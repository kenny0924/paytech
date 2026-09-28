package mysql

import (
	"context"
	"database/sql"
	driver "github.com/go-sql-driver/mysql"
	"github.com/paytech/vault-service/internal/domain"
	"time"
)

type Options struct {
	ConnectTimeout, QueryTimeout time.Duration
	MaxOpenConns, MaxIdleConns   int
}
type Store struct {
	db           *sql.DB
	queryTimeout time.Duration
}
type quietLogger struct{}

func (quietLogger) Print(...any) {}

// Open owns its pool and validates the manually installed schema, read-only.
func Open(ctx context.Context, dsn string, options Options) (*Store, error) {
	if options == (Options{}) {
		options = Options{5 * time.Second, 30 * time.Second, 10, 5}
	}
	if options.ConnectTimeout <= 0 || options.ConnectTimeout > 30*time.Second || options.QueryTimeout <= 0 || options.QueryTimeout > time.Minute || options.MaxOpenConns < 1 || options.MaxOpenConns > 100 || options.MaxIdleConns < 0 || options.MaxIdleConns > options.MaxOpenConns {
		return nil, domain.NewError(domain.InternalError)
	}
	cfg, err := driverConfig(dsn, options)
	if err != nil {
		return nil, err
	}
	connector, err := driver.NewConnector(cfg)
	if err != nil {
		return nil, domain.NewError(domain.InternalError)
	}
	db := sql.OpenDB(connector)
	db.SetMaxOpenConns(options.MaxOpenConns)
	db.SetMaxIdleConns(options.MaxIdleConns)
	db.SetConnMaxLifetime(5 * time.Minute)
	db.SetConnMaxIdleTime(time.Minute)
	s := &Store{db: db, queryTimeout: options.QueryTimeout}
	if err = s.Ping(ctx); err == nil {
		err = s.verifySchema(ctx)
	}
	if err != nil {
		db.Close()
		return nil, err
	}
	return s, nil
}

func driverConfig(dsn string, options Options) (*driver.Config, error) {
	cfg, err := driver.ParseDSN(dsn)
	if err != nil || cfg.DBName == "" {
		return nil, domain.NewError(domain.InternalError)
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
		return domain.NewError(domain.DependencyUnavailable)
	}
	return nil
}
func (s *Store) Ping(ctx context.Context) error {
	ctx, cancel := context.WithTimeout(ctx, s.queryTimeout)
	defer cancel()
	if err := s.db.PingContext(ctx); err != nil {
		return domain.NewError(domain.DependencyUnavailable)
	}
	return nil
}
