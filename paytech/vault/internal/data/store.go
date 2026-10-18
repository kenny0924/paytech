package data

import (
	"context"
	"database/sql"
	"time"

	driver "github.com/go-sql-driver/mysql"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/internal/data/sqlc"
)

// DefaultOptions 是连接池参数的唯一定义；它们不从环境变量读取。
var DefaultOptions = Options{ConnectTimeout: 5 * time.Second, QueryTimeout: 30 * time.Second, MaxOpenConns: 10, MaxIdleConns: 5}

// Options 是连接池与超时参数；Open 校验其范围。
type Options struct {
	ConnectTimeout, QueryTimeout time.Duration
	MaxOpenConns, MaxIdleConns   int
}

// Store 是 biz.Store 的 MySQL 实现，拥有连接池。
type Store struct {
	db           *sql.DB
	queries      *sqlc.Queries
	queryTimeout time.Duration
}
type quietLogger struct{}

func (quietLogger) Print(...any) {}

// Open 建立连接池、确认连通并检查三张表已手工建好（只读，不执行 DDL）。
func Open(ctx context.Context, dsn string, options Options) (*Store, error) {
	if options.ConnectTimeout <= 0 || options.ConnectTimeout > 30*time.Second || options.QueryTimeout <= 0 || options.QueryTimeout > time.Minute || options.MaxOpenConns < 1 || options.MaxOpenConns > 100 || options.MaxIdleConns < 0 || options.MaxIdleConns > options.MaxOpenConns {
		return nil, biz.NewError(biz.InternalError)
	}
	cfg, err := driverConfig(dsn, options)
	if err != nil {
		return nil, err
	}
	connector, err := driver.NewConnector(cfg)
	if err != nil {
		return nil, biz.NewError(biz.InternalError)
	}
	db := sql.OpenDB(connector)
	db.SetMaxOpenConns(options.MaxOpenConns)
	db.SetMaxIdleConns(options.MaxIdleConns)
	db.SetConnMaxLifetime(5 * time.Minute)
	db.SetConnMaxIdleTime(time.Minute)
	s := &Store{db: db, queries: sqlc.New(db), queryTimeout: options.QueryTimeout}
	if err = s.Ping(ctx); err == nil {
		err = s.verifyTables(ctx)
	}
	if err != nil {
		_ = db.Close()
		return nil, err
	}
	return s, nil
}

func driverConfig(dsn string, options Options) (*driver.Config, error) {
	cfg, err := driver.ParseDSN(dsn)
	if err != nil || cfg.DBName == "" {
		return nil, biz.NewError(biz.InternalError)
	}
	cfg.ParseTime = true
	cfg.Loc = time.UTC
	cfg.Timeout = options.ConnectTimeout
	cfg.ReadTimeout = options.QueryTimeout
	cfg.WriteTimeout = options.QueryTimeout
	cfg.Logger = quietLogger{}
	if cfg.Params == nil {
		cfg.Params = map[string]string{}
	}
	cfg.Params["time_zone"] = "'+00:00'"
	return cfg, nil
}
func (s *Store) Close() error {
	if err := s.db.Close(); err != nil {
		return biz.NewError(biz.DependencyUnavailable)
	}
	return nil
}
func (s *Store) Ping(ctx context.Context) error {
	ctx, cancel := context.WithTimeout(ctx, s.queryTimeout)
	defer cancel()
	if err := s.db.PingContext(ctx); err != nil {
		return biz.NewError(biz.DependencyUnavailable)
	}
	return nil
}
