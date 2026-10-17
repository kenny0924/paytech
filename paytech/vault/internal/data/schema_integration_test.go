package data

import (
	"context"
	"database/sql"
	"database/sql/driver"
	"errors"
	"io"
	"testing"
	"time"
)

func TestIntegrationSchemaTablesPresent(t *testing.T) {
	f := integrationFixture(t)
	if err := f.store.verifyTables(context.Background()); err != nil {
		t.Fatal("已手工建好的三张表未被识别")
	}
}

// 用模拟驱动返回缺表结果，不改动共享库来验证启动失败。
func TestMissingTableFailsStartup(t *testing.T) {
	for name, tables := range map[string][]string{
		"缺一张表": {"t_vault_access_event", "t_vault_record"},
		"无表":   nil,
	} {
		t.Run(name, func(t *testing.T) {
			db := sql.OpenDB(metadataConnector{map[string][]string{tablesQuery: tables}})
			defer func() { _ = db.Close() }()
			s := &Store{db: db, queryTimeout: time.Second}
			if err := s.verifyTables(context.Background()); err == nil {
				t.Fatal("缺表时应拒绝启动")
			}
		})
	}
}

type metadataConnector struct{ rows map[string][]string }

func (c metadataConnector) Connect(context.Context) (driver.Conn, error) {
	return metadataConn(c), nil
}
func (c metadataConnector) Driver() driver.Driver            { return c }
func (c metadataConnector) Open(string) (driver.Conn, error) { return c.Connect(context.Background()) }

type metadataConn struct{ rows map[string][]string }

func (c metadataConn) Prepare(string) (driver.Stmt, error) { return nil, errors.New("unsupported") }
func (c metadataConn) Close() error                        { return nil }
func (c metadataConn) Begin() (driver.Tx, error)           { return nil, errors.New("unsupported") }
func (c metadataConn) QueryContext(_ context.Context, q string, _ []driver.NamedValue) (driver.Rows, error) {
	rows, ok := c.rows[q]
	if !ok {
		return nil, errors.New("unexpected query")
	}
	return &metadataRows{rows: rows}, nil
}

type metadataRows struct {
	rows  []string
	index int
}

func (r *metadataRows) Columns() []string { return []string{"metadata"} }
func (r *metadataRows) Close() error      { return nil }
func (r *metadataRows) Next(dest []driver.Value) error {
	if r.index >= len(r.rows) {
		return io.EOF
	}
	dest[0] = r.rows[r.index]
	r.index++
	return nil
}
