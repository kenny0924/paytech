package main

import (
	"context"
	"crypto/rand"
	"errors"
	"io"
	"log/slog"
	"net"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/paytech/vault-service/internal/application"
	"github.com/paytech/vault-service/internal/config"
	"github.com/paytech/vault-service/internal/domain"
	"github.com/paytech/vault-service/internal/httpapi"
	"github.com/paytech/vault-service/internal/id"
	"github.com/paytech/vault-service/internal/integration/kms/localaes"
	"github.com/paytech/vault-service/internal/integration/nacos"
	"github.com/paytech/vault-service/internal/observability"
	"github.com/paytech/vault-service/internal/persistence/mysql"
	"github.com/paytech/vault-service/internal/policy"
)

const shutdownTimeout = 10 * time.Second

type registration interface {
	Registered() bool
	Close(context.Context) error
}
type assembled struct {
	server       *http.Server
	store        io.Closer
	registration registration
}
type assembler func(context.Context, config.Config, *observability.Runtime) (*assembled, error)

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()
	if run(ctx, os.Getenv, os.Stdout, assemble) != nil {
		slog.New(slog.NewJSONHandler(os.Stdout, nil)).Error("vault_server_stopped", "resultCode", "INTERNAL_ERROR")
		os.Exit(1)
	}
}
func run(ctx context.Context, getenv func(string) string, out io.Writer, build assembler) (result error) {
	cfg, err := config.Load(getenv)
	if err != nil {
		return domain.NewError(domain.InternalError)
	}
	startup, cancel := context.WithTimeout(ctx, 15*time.Second)
	defer cancel()
	obs, err := observability.New(startup, observability.Config{Writer: out, DisableExport: getenv("OTEL_TRACES_EXPORTER") == "none" || getenv("OTEL_SDK_DISABLED") == "true"})
	if err != nil {
		return domain.NewError(domain.InternalError)
	}
	defer func() {
		shutdown, cancel := context.WithTimeout(context.Background(), shutdownTimeout)
		defer cancel()
		if obs.Shutdown(shutdown) != nil {
			slog.New(slog.NewJSONHandler(out, nil)).Error("telemetry_shutdown_failed")
		}
	}()
	app, err := build(startup, cfg, obs)
	if err != nil {
		return domain.NewError(domain.InternalError)
	}
	defer func() {
		if err := app.close(); err != nil && result == nil {
			result = err
		}
	}()
	listener, err := net.Listen("tcp", cfg.HTTPAddress)
	if err != nil {
		return domain.NewError(domain.InternalError)
	}
	return serve(ctx, app.server, listener)
}
func assemble(ctx context.Context, cfg config.Config, obs *observability.Runtime) (*assembled, error) {
	file, err := os.Open(cfg.PolicyFile)
	if err != nil {
		return nil, domain.NewError(domain.InternalError)
	}
	permissions, err := policy.Load(file)
	_ = file.Close()
	if err != nil {
		return nil, err
	}
	root := cfg.LocalAESRootKey()
	keys, err := localaes.New(root, cfg.LocalAESKeyID())
	clear(root)
	if err != nil {
		return nil, err
	}
	store, err := mysql.Open(ctx, cfg.DatabaseDSN(), mysql.Options{ConnectTimeout: cfg.DatabaseConnectTimeout, QueryTimeout: cfg.DatabaseQueryTimeout, MaxOpenConns: cfg.DatabaseMaxOpenConns, MaxIdleConns: cfg.DatabaseMaxIdleConns})
	if err != nil {
		return nil, err
	}
	app := &assembled{store: store}
	success := false
	defer func() {
		if !success {
			app.close()
		}
	}()
	ids, err := id.NewGenerator(uint8(cfg.SnowflakeDatacenterID), uint8(cfg.SnowflakeWorkerID))
	if err != nil {
		return nil, domain.NewError(domain.InternalError)
	}
	if keys.Ready(ctx) != nil {
		return nil, domain.NewError(domain.DependencyUnavailable)
	}
	app.registration, err = startRegistration(ctx, cfg.Nacos)
	if err != nil {
		return nil, err
	}
	observedStore, observedKeys := obs.ObserveStore(store), obs.ObserveKeys(keys)
	service := application.NewService(observedStore, observedKeys, ids, permissions, rand.Reader, time.Now)
	handler := httpapi.New(httpapi.Dependencies{Service: service, Observer: obs, Metrics: obs.MetricsHandler(), Ready: readiness(observedStore, observedKeys, app.registration)})
	app.server = newHTTPServer(cfg.HTTPAddress, handler, obs)
	success = true
	return app, nil
}
func startRegistration(ctx context.Context, cfg config.NacosConfig) (registration, error) {
	if !cfg.Enabled {
		return disabledRegistration{}, nil
	}
	reg := nacos.New(cfg)
	if err := reg.Start(ctx); err != nil {
		// Start failure is terminal; its single owner finishes late SDK cleanup.
		return nil, err
	}
	return reg, nil
}

type disabledRegistration struct{}

func (disabledRegistration) Registered() bool            { return true }
func (disabledRegistration) Close(context.Context) error { return nil }
func readiness(db interface{ Ping(context.Context) error }, keys interface{ Ready(context.Context) error }, reg registration) func(context.Context) error {
	return func(ctx context.Context) error {
		if db == nil || keys == nil || db.Ping(ctx) != nil || keys.Ready(ctx) != nil {
			return domain.NewError(domain.DependencyUnavailable)
		}
		if reg != nil && !reg.Registered() {
			return domain.NewError(domain.DependencyUnavailable)
		}
		return nil
	}
}
func newHTTPServer(address string, handler http.Handler, obs *observability.Runtime) *http.Server {
	return &http.Server{Addr: address, Handler: handler, ReadHeaderTimeout: 5 * time.Second, ReadTimeout: 10 * time.Second, WriteTimeout: 45 * time.Second, IdleTimeout: 60 * time.Second, MaxHeaderBytes: 16 * 1024, ErrorLog: obs.ServerErrorLog()}
}
func serve(ctx context.Context, server *http.Server, listener net.Listener) error {
	stopped := make(chan error, 1)
	go func() { stopped <- server.Serve(listener) }()
	select {
	case err := <-stopped:
		if !errors.Is(err, http.ErrServerClosed) {
			return domain.NewError(domain.InternalError)
		}
		return nil
	case <-ctx.Done():
		shutdown, cancel := context.WithTimeout(context.Background(), shutdownTimeout)
		defer cancel()
		if server.Shutdown(shutdown) != nil {
			_ = server.Close()
			<-stopped
			return domain.NewError(domain.InternalError)
		}
		if err := <-stopped; !errors.Is(err, http.ErrServerClosed) {
			return domain.NewError(domain.InternalError)
		}
		return nil
	}
}
func (a *assembled) close() error {
	ctx, cancel := context.WithTimeout(context.Background(), shutdownTimeout)
	defer cancel()
	var failed bool
	if a.registration != nil {
		failed = awaitClose(ctx, func() error { return a.registration.Close(ctx) }) != nil
	}
	if a.store != nil {
		if awaitClose(ctx, a.store.Close) != nil {
			failed = true
		}
	}
	if failed {
		return domain.NewError(domain.InternalError)
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
			err = domain.NewError(domain.InternalError)
		}
		completed <- err
	}()
	select {
	case err := <-completed:
		return err
	case <-ctx.Done():
		return domain.NewError(domain.InternalError)
	}
}
