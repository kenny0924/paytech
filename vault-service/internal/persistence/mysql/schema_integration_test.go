package mysql

import (
	"context"
	"database/sql"
	"database/sql/driver"
	"errors"
	"io"
	"strings"
	"testing"
)

func TestIntegrationSchemaExactContract(t *testing.T) {
	f := integrationFixture(t)
	if err := f.store.verifySchema(context.Background()); err != nil {
		t.Fatal("approved physical schema rejected")
	}
}

// Capture genuine MySQL metadata, then alter only simulator responses. Never
// mutate shared schema merely to demonstrate startup fails closed on drift.
func TestIntegrationSchemaDriftFailsClosed(t *testing.T) {
	f := integrationFixture(t)
	snapshot := map[string][]string{}
	for _, check := range schemaChecks {
		rows, err := f.store.db.QueryContext(context.Background(), check.query)
		if err != nil {
			t.Fatal("metadata unavailable")
		}
		for rows.Next() {
			var row string
			if err := rows.Scan(&row); err != nil {
				t.Fatal("metadata scan failed")
			}
			snapshot[check.query] = append(snapshot[check.query], row)
		}
		if rows.Err() != nil {
			t.Fatal("metadata stream failed")
		}
		rows.Close()
	}
	for _, kind := range []string{"fourth-table", "column-type", "index-order", "foreign-key", "check-expression", "check-literal", "check-disabled", "binary-collation"} {
		t.Run(kind, func(t *testing.T) {
			changed := map[string][]string{}
			for q, rows := range snapshot {
				changed[q] = append([]string(nil), rows...)
			}
			switch kind {
			case "check-literal":
				q := schemaChecks[4].query
				changed[q][0] = strings.Replace(changed[q][0], "PROTECT", "PROTECT_utf8mb4", 1)
			case "fourth-table":
				q := schemaChecks[0].query
				changed[q] = append(changed[q], "t_vault_unapproved|InnoDB|utf8mb4_0900_ai_ci|BASE TABLE")
			case "column-type":
				q := schemaChecks[1].query
				changed[q][0] = strings.ReplaceAll(changed[q][0], "bigint", "int")
			case "index-order":
				q := schemaChecks[2].query
				changed[q][0], changed[q][1] = changed[q][1], changed[q][0]
			case "foreign-key":
				q := schemaChecks[3].query
				changed[q] = changed[q][:1]
			case "check-expression":
				q := schemaChecks[4].query
				changed[q][0] = "t_vault_access_event|ck_vault_access_action|YES|(1 = 1)"
			case "check-disabled":
				q := schemaChecks[4].query
				changed[q][0] = strings.Replace(changed[q][0], "|YES|", "|NO|", 1)
			case "binary-collation":
				q := schemaChecks[1].query
				changed[q][3] = strings.ReplaceAll(changed[q][3], "ascii_bin", "ascii_general_ci")
			}
			db := sql.OpenDB(metadataConnector{changed})
			defer db.Close()
			s := &Store{db: db, queryTimeout: f.store.queryTimeout}
			if err := s.verifySchema(context.Background()); err == nil {
				t.Fatal("schema drift accepted")
			}
		})
	}
}

type metadataConnector struct{ rows map[string][]string }

func (c metadataConnector) Connect(context.Context) (driver.Conn, error) {
	return metadataConn{c.rows}, nil
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
