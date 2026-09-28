package mysql

import (
	"context"
	"database/sql"
	"database/sql/driver"
	"errors"
	"io"
	"sync"
	"testing"
	"time"

	mysqlDriver "github.com/go-sql-driver/mysql"
	"github.com/paytech/vault-service/internal/application"
)

// The simulator models durable commit followed by response loss and a live
// session after failed rollback; neither can be forced safely on shared MySQL.
type terminationDriver struct {
	mu                                               sync.Mutex
	commitErr, rollbackErr                           error
	connections, closed, commits, rollbacks, durable int
}
type terminationConn struct {
	owner   *terminationDriver
	pending int
}

func (d *terminationDriver) Connect(context.Context) (driver.Conn, error) {
	d.mu.Lock()
	defer d.mu.Unlock()
	d.connections++
	return &terminationConn{owner: d}, nil
}
func (d *terminationDriver) Driver() driver.Driver             { return d }
func (d *terminationDriver) Open(string) (driver.Conn, error)  { return d.Connect(context.Background()) }
func (c *terminationConn) Prepare(string) (driver.Stmt, error) { return nil, errors.New("unsupported") }
func (c *terminationConn) Close() error {
	c.owner.mu.Lock()
	defer c.owner.mu.Unlock()
	c.owner.closed++
	c.pending = 0
	return nil
}
func (c *terminationConn) Begin() (driver.Tx, error) { return c, nil }
func (c *terminationConn) BeginTx(_ context.Context, o driver.TxOptions) (driver.Tx, error) {
	if o.Isolation != driver.IsolationLevel(sql.LevelReadCommitted) {
		return nil, errors.New("wrong isolation")
	}
	return c, nil
}
func (c *terminationConn) ExecContext(context.Context, string, []driver.NamedValue) (driver.Result, error) {
	c.pending++
	return driver.RowsAffected(1), nil
}
func (c *terminationConn) Commit() error {
	d := c.owner
	d.mu.Lock()
	defer d.mu.Unlock()
	d.commits++
	d.durable += c.pending
	c.pending = 0
	return d.commitErr
}
func (c *terminationConn) Rollback() error {
	d := c.owner
	d.mu.Lock()
	defer d.mu.Unlock()
	d.rollbacks++
	if d.rollbackErr == nil {
		c.pending = 0
	}
	return d.rollbackErr
}
func simulatedStore(t *testing.T, d *terminationDriver) *Store {
	db := sql.OpenDB(d)
	db.SetMaxOpenConns(1)
	db.SetMaxIdleConns(1)
	t.Cleanup(func() { db.Close() })
	return &Store{db: db, queryTimeout: time.Second}
}

func TestCommitResponseLossNeverReturnsRetryableClassification(t *testing.T) {
	for _, cause := range []error{io.ErrUnexpectedEOF, application.ErrRetryableContention, &application.DuplicateError{Kind: application.DuplicateToken}, context.Canceled} {
		d := &terminationDriver{commitErr: cause}
		s := simulatedStore(t, d)
		err := s.InTransaction(context.Background(), func(tx application.TxStore) error {
			_, e := tx.(*transaction).tx.ExecContext(context.Background(), "INSERT synthetic audit")
			return e
		})
		if err != application.ErrTransactionOutcomeUnknown {
			t.Fatalf("unsafe commit result: %v", err)
		}
		if d.durable != 1 || d.commits != 1 || d.closed != 1 {
			t.Fatal("durable commit or quarantine contract violated")
		}
	}
}
func TestFailedRollbackSuppressesClassificationAndDiscardsSession(t *testing.T) {
	for _, cause := range []error{application.ErrRetryableContention, &application.DuplicateError{Kind: application.DuplicateToken}, &application.DuplicateError{Kind: application.DuplicateStable}, &application.DuplicateError{Kind: application.DuplicateProtectRequest}} {
		for _, rollbackErr := range []error{nil, io.ErrUnexpectedEOF, sql.ErrTxDone} {
			d := &terminationDriver{rollbackErr: rollbackErr}
			s := simulatedStore(t, d)
			err := s.InTransaction(context.Background(), func(tx application.TxStore) error {
				tx.(*transaction).tx.ExecContext(context.Background(), "INSERT synthetic audit")
				return cause
			})
			want := cause
			if rollbackErr != nil {
				want = application.ErrTransactionRollbackFailed
			}
			if err != want || d.commits != 0 || d.durable != 0 {
				t.Fatal("unsafe rollback result")
			}
			if rollbackErr != nil && d.closed != 1 {
				t.Fatal("failed session returned to pool")
			}
			d.rollbackErr = nil
			if err := s.InTransaction(context.Background(), func(application.TxStore) error { return nil }); err != nil {
				t.Fatal(err)
			}
			if rollbackErr != nil && d.connections != 2 {
				t.Fatal("failed session reused")
			}
		}
	}
}
func TestPanicAndCancellationQuarantine(t *testing.T) {
	d := &terminationDriver{rollbackErr: io.ErrUnexpectedEOF}
	s := simulatedStore(t, d)
	func() {
		defer func() {
			if recover() == nil {
				t.Error("panic swallowed")
			}
		}()
		s.InTransaction(context.Background(), func(application.TxStore) error { panic("test panic") })
	}()
	if d.closed != 1 || d.commits != 0 {
		t.Fatal("panic did not quarantine")
	}
	ctx, cancel := context.WithCancel(context.Background())
	err := s.InTransaction(ctx, func(application.TxStore) error { cancel(); return application.ErrRetryableContention })
	if err != application.ErrTransactionRollbackFailed && err != application.ErrTransactionOutcomeUnknown {
		t.Fatalf("cancellation exposed retry: %v", err)
	}
	if d.closed != 2 {
		t.Fatal("cancelled session not quarantined")
	}
}
func TestDuplicateParserRejectsUnknownAndEmbeddedCanary(t *testing.T) {
	for _, message := range []string{"Duplicate entry 'canary uk_vault_record_token' for key 'PRIMARY'", "Duplicate entry 'canary' for key 'uk_vault_record_token_suffix'", "Duplicate entry 'canary' for key 'uk_vault_record_token' trailing"} {
		err := classify(&mysqlDriver.MySQLError{Number: 1062, Message: message})
		var dup *application.DuplicateError
		if errors.As(err, &dup) || err.Error() == message {
			t.Fatal("unsafe duplicate parsing")
		}
	}
	for _, number := range []uint16{1205, 1213} {
		if classify(&mysqlDriver.MySQLError{Number: number, Message: "canary"}) != application.ErrRetryableContention {
			t.Fatal("contention classification missing")
		}
	}
}
