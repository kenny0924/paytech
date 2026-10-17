// Package base 提供自动测试的公共生命周期。
//
//   - Run：在 TestMain 中按正式装配（bootstrap.Assemble）启动一次 Vault，相当于 Java 的测试应用上下文；
//   - Setup：每个用例开头调用，检查空基线并登记全局清理，相当于 MockTestBase 的 @BeforeEach/@AfterEach；
//   - Start / Stop：同一测试包需要多套配置（如内部 Token 的三种模式）时显式启动多个环境，用 (*Environment).Setup 进入用例。
package base

import (
	"bytes"
	"context"
	"crypto/rand"
	"database/sql"
	"encoding/base64"
	"fmt"
	"maps"
	"net/http/httptest"
	"os"
	"sync"
	"testing"
	"time"

	_ "github.com/go-sql-driver/mysql"

	"github.com/paytech/vault-service/internal/bootstrap"
	"github.com/paytech/vault-service/internal/conf"
	"github.com/paytech/vault-service/internal/pkg/observability"
	"github.com/paytech/vault-service/test/support/dal"
	"github.com/paytech/vault-service/test/support/fixture"
)

// setupInstruction 是本机依赖缺失时的准备说明；测试失败而不是跳过。
const setupInstruction = "设置 VAULT_TEST_DB_DSN 指向仓库 local-environment 的 mor_vault（vault_app，parseTime=true&loc=UTC），并先手工执行 docs/database/sql/vault-schema.sql；见 paytech/vault/README.md"

// Environment 是一个测试包共享的运行环境：被测 Vault、测试数据库连接与本次运行标识。
type Environment struct {
	ID     *fixture.Identity
	Mapper *dal.TestMapper
	Logs   *SyncBuffer
	URL    string

	rootKey []byte
	dsn     string
	server  *httptest.Server
	app     *bootstrap.App
	obs     *observability.Runtime
	db      *sql.DB
	dir     string

	mu       sync.Mutex
	unusable bool
}

var current *Environment

// Run 在 TestMain 中调用：启动环境、运行全部用例、关闭环境，返回进程退出码。
func Run(m *testing.M) int {
	env, err := start(nil)
	if err != nil {
		fmt.Fprintln(os.Stderr, "Vault 自动测试环境启动失败："+err.Error()+"。"+setupInstruction)
		return 1
	}
	current = env
	code := m.Run()
	if err := env.stop(); err != nil {
		fmt.Fprintln(os.Stderr, "Vault 自动测试环境关闭失败："+err.Error())
		return 1
	}
	return code
}

// Options 是在完整测试配置之上的覆盖项。同一包内的多个环境必须各自覆盖 VAULT_SNOWFLAKE_WORKER_ID，坐标见 mockConfig。
type Options struct {
	Overrides map[string]string
}

// Start 按完整测试配置加覆盖项启动一个独立环境（独占的运行标识、根密钥与 Vault 实例）；调用方负责 Stop。
func Start(options Options) (*Environment, error) {
	return start(options.Overrides)
}

// Stop 停止环境并删除本次运行的数据。
func (e *Environment) Stop() error { return e.stop() }

// start 生成本次运行的密钥，把初始策略写入数据库后按正式装配启动 Vault，并以 httptest 承载同一 Handler。
func start(overrides map[string]string) (*Environment, error) {
	dsn := os.Getenv("VAULT_TEST_DB_DSN")
	if dsn == "" {
		return nil, fmt.Errorf("缺少 VAULT_TEST_DB_DSN")
	}
	env := &Environment{ID: fixture.NewIdentity(), Logs: &SyncBuffer{}, dsn: dsn, rootKey: make([]byte, 32)}
	if _, err := rand.Read(env.rootKey); err != nil {
		return nil, err
	}
	var err error
	if env.dir, err = os.MkdirTemp("", "vault-auto-"); err != nil {
		return nil, err
	}
	if err = env.openDatabase(); err != nil {
		return nil, err
	}
	if _, err = env.Mapper.ResetOwned(context.Background(), env.ID.SeedDataTypes(), env.ID.SeedRules()); err != nil {
		_ = env.stop()
		return nil, fmt.Errorf("写入初始策略失败（请先执行策略表 DDL）")
	}
	if err = env.assemble(overrides); err != nil {
		_ = env.stop()
		return nil, err
	}
	return env, nil
}

// openDatabase 打开测试专用连接（TestMapper 使用）。
func (e *Environment) openDatabase() error {
	var err error
	if e.db, err = sql.Open("mysql", e.dsn); err != nil {
		return err
	}
	e.Mapper = dal.NewTestMapper(e.db, e.ID)
	return nil
}

// assemble 加载完整测试配置（含覆盖项）并调用正式装配。
func (e *Environment) assemble(overrides map[string]string) error {
	values := mockConfig(e.dsn, e.ID.AdminCaller, base64.StdEncoding.EncodeToString(e.rootKey))
	maps.Copy(values, overrides)
	cfg, err := conf.Load(func(key string) string { return values[key] })
	if err != nil {
		return fmt.Errorf("测试配置无效")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	if e.obs, err = observability.New(ctx, observability.Config{Writer: e.Logs, DisableExport: true}); err != nil {
		return err
	}
	if e.app, err = bootstrap.Assemble(ctx, cfg, e.obs); err != nil {
		return fmt.Errorf("装配失败（数据库或 Schema 不可用）")
	}
	e.server = httptest.NewServer(e.app.Server.Handler)
	e.URL = e.server.URL
	return nil
}

// stop 先停 HTTP，再清理本次运行残留并释放资源。
func (e *Environment) stop() error {
	if e.server != nil {
		e.server.Close()
	}
	var failures []error
	if e.Mapper != nil {
		failures = append(failures, e.Mapper.DeleteOwned(context.Background()))
	}
	if e.db != nil {
		failures = append(failures, e.db.Close())
	}
	if e.app != nil {
		failures = append(failures, e.app.Close())
	}
	if e.obs != nil {
		failures = append(failures, e.obs.Shutdown(context.Background()))
	}
	clear(e.rootKey)
	failures = append(failures, os.RemoveAll(e.dir))
	for _, err := range failures {
		if err != nil {
			return err
		}
	}
	return nil
}

// RootKey 返回本次运行 LOCAL AES 根密钥的副本，供本地 Mocker 生成可被 Vault 解密的记录。
func (e *Environment) RootKey() []byte { return append([]byte(nil), e.rootKey...) }

// DSN 返回测试数据库连接串，供本地 Mocker 打开仓储。
func (e *Environment) DSN() string { return e.dsn }

// SyncBuffer 是并发安全的日志缓冲，用于检查日志不含敏感值。
type SyncBuffer struct {
	mu  sync.Mutex
	buf bytes.Buffer
}

// Write 追加日志。
func (b *SyncBuffer) Write(p []byte) (int, error) {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.buf.Write(p)
}

// String 返回当前日志。
func (b *SyncBuffer) String() string {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.buf.String()
}

// Reset 清空日志。
func (b *SyncBuffer) Reset() {
	b.mu.Lock()
	defer b.mu.Unlock()
	b.buf.Reset()
}
