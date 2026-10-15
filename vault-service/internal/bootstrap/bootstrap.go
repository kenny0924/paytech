// Package bootstrap 是 Vault 的组合根：按配置组装策略、密钥、数据库（data）、用例（biz）、接口（service）与 HTTP 服务器（server）。
//
// cmd/vault-server 与 test 自动测试共用这一份装配，测试不另建平行的装配清单。
package bootstrap

import (
	"context"
	"crypto/rand"
	"io"
	"net/http"
	"time"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/internal/conf"
	"github.com/paytech/vault-service/internal/data"
	"github.com/paytech/vault-service/internal/pkg/localaes"
	"github.com/paytech/vault-service/internal/pkg/nacos"
	"github.com/paytech/vault-service/internal/pkg/observability"
	"github.com/paytech/vault-service/internal/pkg/policy"
	"github.com/paytech/vault-service/internal/pkg/snowflake"
	"github.com/paytech/vault-service/internal/server"
	"github.com/paytech/vault-service/internal/service"
)

// ShutdownTimeout 是停机时 HTTP 关闭与资源清理各自的时间预算。
const ShutdownTimeout = 10 * time.Second

// Registration 是服务注册的生命周期：停机时注销。
type Registration interface {
	Close(ctx context.Context) error
}

// App 是组装完成的进程资源。
type App struct {
	Server       *http.Server
	Store        io.Closer
	Registration Registration
	// Policy 是策略定时刷新器；停机时先于数据库关闭。
	Policy interface{ Close() }
}

// Assemble 按依赖顺序组装进程：密钥 → 数据库 → 策略 → ID → 注册 → HTTP；任一步失败都释放已建资源。
func Assemble(ctx context.Context, cfg conf.Config, obs *observability.Runtime) (*App, error) {
	keys, err := openKeys(cfg)
	if err != nil {
		return nil, err
	}
	store, err := data.Open(ctx, cfg.DatabaseDSN(), data.DefaultOptions)
	if err != nil {
		return nil, err
	}
	app := &App{Store: store}
	success := false
	defer func() {
		if !success {
			// 组装失败的原始错误优先返回；清理只尽力而为。
			_ = app.Close()
		}
	}()
	refresher := policy.NewRefresher(store, cfg.PolicyRefreshInterval, obs.PolicyRefreshFailed)
	if err := refresher.Start(ctx); err != nil {
		// 启动时必须读到一次策略（库可为空），否则拒绝启动。
		return nil, err
	}
	app.Policy = refresher
	ids, err := snowflake.NewGenerator(uint8(cfg.SnowflakeDatacenterID), uint8(cfg.SnowflakeWorkerID))
	if err != nil {
		return nil, biz.NewError(biz.InternalError)
	}
	if keys.Ready(ctx) != nil {
		return nil, biz.NewError(biz.DependencyUnavailable)
	}
	if app.Registration, err = startRegistration(ctx, cfg.Nacos); err != nil {
		return nil, err
	}
	app.Server = NewHTTPServer(cfg.HTTPAddress, newHandler(obs, store, keys, ids, refresher, cfg.AdminCallers), obs)
	success = true
	return app, nil
}

// openKeys 用配置中的根密钥创建 LOCAL AES 密钥服务，并清零本地副本。
func openKeys(cfg conf.Config) (*localaes.Provider, error) {
	root := cfg.LocalAESRootKey()
	defer clear(root)
	return localaes.New(root, cfg.LocalAESKeyID())
}

// newHandler 按 data → biz → service → server 组装 HTTP Handler；存储与密钥经观测包装。
func newHandler(obs *observability.Runtime, store *data.Store, keys *localaes.Provider, ids *snowflake.Generator, permissions biz.Authorizer, adminCallers []string) http.Handler {
	observedStore, observedKeys := obs.ObserveStore(store), obs.ObserveKeys(keys)
	audit := biz.NewAccessAuditService(observedStore, ids, time.Now)
	cipher := biz.NewRecordCipherService(observedKeys, ids, rand.Reader, time.Now)
	protect := biz.NewProtectUsecase(observedStore, audit, cipher, permissions, ids, time.Now)
	reveal := biz.NewRevealUsecase(observedStore, audit, cipher, permissions)
	admin := biz.NewPolicyAdminUsecase(observedStore, store, ids, time.Now)
	return server.New(server.Dependencies{
		Records:      service.NewRecordService(protect, reveal, audit),
		Admin:        service.NewAdminService(admin, protect, reveal, audit),
		AdminCallers: adminCallers,
		Observer:     obs,
		Metrics:      obs.MetricsHandler(),
		Ready:        readiness(observedStore, observedKeys),
	})
}

// startRegistration 启用 Nacos 时注册实例，未启用时返回空实现。
func startRegistration(ctx context.Context, cfg conf.NacosConfig) (Registration, error) {
	if !cfg.Enabled {
		return disabledRegistration{}, nil
	}
	return nacos.Register(ctx, cfg)
}

// disabledRegistration 表示未启用服务注册。
type disabledRegistration struct{}

func (disabledRegistration) Close(context.Context) error { return nil }

// readiness 要求数据库与密钥可用；注册状态由 SDK 维持，不纳入就绪判断。
func readiness(db interface{ Ping(context.Context) error }, keys interface{ Ready(context.Context) error }) func(context.Context) error {
	return func(ctx context.Context) error {
		if db == nil || keys == nil || db.Ping(ctx) != nil || keys.Ready(ctx) != nil {
			return biz.NewError(biz.DependencyUnavailable)
		}
		return nil
	}
}

// NewHTTPServer 创建带读写超时与安全诊断日志的 HTTP Server。
func NewHTTPServer(address string, handler http.Handler, obs *observability.Runtime) *http.Server {
	return &http.Server{Addr: address, Handler: handler, ReadHeaderTimeout: 5 * time.Second, ReadTimeout: 10 * time.Second, WriteTimeout: 45 * time.Second, IdleTimeout: 60 * time.Second, MaxHeaderBytes: 16 * 1024, ErrorLog: obs.ServerErrorLog()}
}

// Close 依次注销实例并关闭数据库，共享 ShutdownTimeout 预算；每项只尝试一次。
func (a *App) Close() error {
	ctx, cancel := context.WithTimeout(context.Background(), ShutdownTimeout)
	defer cancel()
	var failed bool
	if a.Policy != nil {
		a.Policy.Close()
	}
	if a.Registration != nil {
		failed = awaitClose(ctx, func() error { return a.Registration.Close(ctx) }) != nil
	}
	if a.Store != nil {
		if awaitClose(ctx, a.Store.Close) != nil {
			failed = true
		}
	}
	if failed {
		return biz.NewError(biz.InternalError)
	}
	return nil
}

// Each cleanup is invoked once, even if an earlier step exhausted the shared
// budget. Timeout bounds only the caller's wait: io.Closer (including sql.DB)
// has no cancellation contract and may finish later. Buffered completion lets
// that single invocation finish without waiting for a departed caller.
func awaitClose(ctx context.Context, closeResource func() error) error {
	completed := make(chan error, 1)
	go func() {
		err := closeResource()
		if err != nil {
			err = biz.NewError(biz.InternalError)
		}
		completed <- err
	}()
	select {
	case err := <-completed:
		return err
	case <-ctx.Done():
		return biz.NewError(biz.InternalError)
	}
}
