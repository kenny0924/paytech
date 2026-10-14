package biz_test

import (
	"bytes"
	"context"
	"crypto/rand"
	"database/sql"
	"encoding/hex"
	"fmt"
	"os"
	"strings"
	"sync"
	"testing"
	"time"

	driver "github.com/go-sql-driver/mysql"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/internal/data"
	"github.com/paytech/vault-service/internal/pkg/localaes"
	"github.com/paytech/vault-service/internal/pkg/snowflake"
)

// One package-wide generator; no other application test process may own 31/30.
var integrationIDs, _ = snowflake.NewGenerator(31, 30)

type integrationFixture struct {
	db                        *sql.DB
	store                     *data.Store
	service                   *testService
	caller, reference, stable string
}

func newIntegrationFixture(t *testing.T) *integrationFixture {
	t.Helper()
	dsn := os.Getenv("VAULT_TEST_DB_DSN")
	if dsn == "" {
		t.Fatal("Set VAULT_TEST_DB_DSN for repository local MySQL and manually apply docs/database/sql/vault-schema.sql first")
	}
	cfg, err := driver.ParseDSN(dsn)
	if err != nil || cfg.Net != "tcp" || cfg.Addr != "127.0.0.1:13306" || cfg.DBName != "mor_vault" || cfg.User != "vault_app" {
		t.Fatal("Integration DSN must use scoped vault_app at loopback 127.0.0.1:13306/mor_vault")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	store, err := data.Open(ctx, dsn, data.DefaultOptions)
	if err != nil {
		t.Fatal("Local Vault MySQL unavailable or schema mismatch; manually apply approved schema")
	}
	db, err := sql.Open("mysql", dsn)
	if err != nil {
		store.Close()
		t.Fatal("Local Vault MySQL unavailable")
	}
	var raw [16]byte
	if _, err = rand.Read(raw[:]); err != nil {
		db.Close()
		store.Close()
		t.Fatal("test ownership entropy unavailable")
	}
	marker := hex.EncodeToString(raw[:])
	upper := strings.ToUpper(marker)
	f := &integrationFixture{db: db, store: store, caller: "task7-" + marker, reference: "TASK7_R_" + upper, stable: "TASK7_S_" + upper}
	key, err := localaes.New(bytes.Repeat([]byte{23}, 32), "task7-test")
	if err != nil {
		t.Fatal(err)
	}
	// bytes.Reader is intentionally not concurrency-safe; Service must serialize
	// the borrowed reader even when different database transactions overlap.
	entropy := make([]byte, 32*4096)
	if _, err := rand.Read(entropy); err != nil {
		t.Fatal("test token entropy unavailable")
	}
	f.service = newTestService(store, key, integrationIDs, testPolicy(t, f.reference, f.stable, f.caller), bytes.NewReader(entropy), time.Now)
	t.Cleanup(func() { defer db.Close(); defer store.Close(); f.cleanup(t) })
	return f
}
func (f *integrationFixture) cleanup(t *testing.T) {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	// Immutable per-run caller/type markers establish ownership independently of IDs.
	for _, query := range []string{"DELETE FROM t_vault_access_event WHERE caller_service=?", "DELETE FROM t_vault_protect_request WHERE caller_service=?"} {
		if _, err := f.db.ExecContext(ctx, query, f.caller); err != nil {
			t.Error("owned row cleanup failed")
		}
	}
	rows, err := f.db.QueryContext(ctx, "SELECT record_id,data_type FROM t_vault_record WHERE data_type IN (?,?)", f.reference, f.stable)
	if err != nil {
		t.Error("owned record lookup failed")
		return
	}
	type owner struct {
		id       int64
		dataType string
	}
	var records []owner
	for rows.Next() {
		var o owner
		if rows.Scan(&o.id, &o.dataType) != nil {
			t.Error("owned record scan failed")
			break
		}
		records = append(records, o)
	}
	if rows.Err() != nil {
		t.Error("owned record iteration failed")
	}
	rows.Close()
	for _, o := range records {
		if _, err := f.db.ExecContext(ctx, "DELETE FROM t_vault_record WHERE record_id=? AND data_type=?", o.id, o.dataType); err != nil {
			t.Error("owned record cleanup failed")
		}
	}
}
func (f *integrationFixture) attempt(n int) biz.Attempt {
	a := attempt(n)
	a.Caller = f.caller
	return a
}
func (f *integrationFixture) input(mode biz.TokenMode) biz.ProtectInput {
	i := input(mode)
	i.DataType = f.reference
	if mode == biz.Stable {
		i.DataType = f.stable
	}
	return i
}
func (f *integrationFixture) count(t *testing.T, query string, args ...any) int {
	t.Helper()
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	var n int
	if f.db.QueryRowContext(ctx, query, args...).Scan(&n) != nil {
		t.Fatal("owned-row assertion query failed")
	}
	return n
}
func (f *integrationFixture) assertCounts(t *testing.T, records, requests, events int) {
	t.Helper()
	if n := f.count(t, "SELECT COUNT(*) FROM t_vault_record WHERE data_type IN (?,?)", f.reference, f.stable); n != records {
		t.Fatalf("records=%d want=%d (orphan or convergence failure)", n, records)
	}
	if n := f.count(t, "SELECT COUNT(*) FROM t_vault_protect_request WHERE caller_service=?", f.caller); n != requests {
		t.Fatalf("requests=%d want=%d", n, requests)
	}
	if n := f.count(t, "SELECT COUNT(*) FROM t_vault_access_event WHERE caller_service=?", f.caller); n != events {
		t.Fatalf("events=%d want=%d", n, events)
	}
}
func TestIntegrationConcurrentProtectConvergence(t *testing.T) {
	for _, mode := range []biz.TokenMode{biz.Reference, biz.Stable} {
		t.Run(string(mode), func(t *testing.T) {
			f := newIntegrationFixture(t)
			ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
			defer cancel()
			start := make(chan struct{})
			var wg sync.WaitGroup
			results := make([]biz.ProtectResult, 32)
			failures := make([]error, 32)
			for n := range 32 {
				wg.Add(1)
				go func(n int) {
					defer wg.Done()
					<-start
					i := f.input(mode)
					if mode == biz.Stable {
						i.IdempotencyKey = fmt.Sprintf("key-%d", n)
					}
					results[n], failures[n] = f.service.Protect(ctx, f.attempt(n), i)
				}(n)
			}
			close(start)
			wg.Wait()
			for n, e := range failures {
				if e != nil {
					t.Fatalf("attempt %d failed: %v", n, e)
				}
				if results[n].Token == "" || results[n].Token != results[0].Token {
					t.Fatal("concurrent attempts did not converge")
				}
			}
			requests := 1
			if mode == biz.Stable {
				requests = 32
			}
			f.assertCounts(t, 1, requests, 32)
			for n := range 32 {
				if f.count(t, "SELECT COUNT(*) FROM t_vault_access_event WHERE caller_service=? AND request_id=? AND action='PROTECT' AND outcome='SUCCESS' AND result_code='SUCCESS' AND policy_version='test-v1' AND record_id IS NOT NULL AND data_type=? AND purpose='TEST'", f.caller, fmt.Sprintf("request-%d", n), f.input(mode).DataType) != 1 {
					t.Fatal("attempt did not produce exactly one complete success audit")
				}
			}
			conflict := f.input(mode)
			if mode == biz.Stable {
				conflict.IdempotencyKey = "key-0"
			}
			conflict.Value = []byte("different")
			r, e := f.service.Protect(ctx, f.attempt(33), conflict)
			code(t, e, biz.IdempotencyKeyConflict)
			if r.Token != "" {
				t.Fatal("conflict exposed token")
			}
			f.assertCounts(t, 1, requests, 33)
			if f.count(t, "SELECT COUNT(*) FROM t_vault_access_event WHERE caller_service=? AND request_id='request-33' AND outcome='DENIED' AND result_code='IDEMPOTENCY_KEY_CONFLICT'", f.caller) != 1 {
				t.Fatal("conflict audit absent")
			}
		})
	}
}

// The real database transaction is retained; only its audit insertion is faulted.
type auditRejectingStore struct{ biz.Store }
type auditRejectingTx struct{ biz.TxStore }

func (a auditRejectingStore) InTransaction(ctx context.Context, fn func(biz.TxStore) error) error {
	return a.Store.InTransaction(ctx, func(tx biz.TxStore) error { return fn(auditRejectingTx{tx}) })
}
func (a auditRejectingTx) InsertAccessEvent(context.Context, biz.AccessEvent) error {
	return biz.NewError(biz.InternalError)
}

func TestIntegrationAuditFailureRollsBackProtectAndSuppressesReveal(t *testing.T) {
	f := newIntegrationFixture(t)
	ctx := context.Background()
	i := f.input(biz.Reference)
	first, e := f.service.Protect(ctx, f.attempt(1), i)
	if e != nil {
		t.Fatal(e)
	}
	key, e := localaes.New(bytes.Repeat([]byte{23}, 32), "task7-test")
	if e != nil {
		t.Fatal(e)
	}
	failing := newTestService(auditRejectingStore{f.store}, key, integrationIDs, testPolicy(t, f.reference, f.stable, f.caller), rand.Reader, time.Now)
	i.IdempotencyKey = "rolled-back"
	r, e := failing.Protect(ctx, f.attempt(2), i)
	code(t, e, biz.AuditUnavailable)
	if r.Token != "" {
		t.Fatal("unaudited token escaped")
	}
	revealed, e := failing.Reveal(ctx, f.attempt(3), biz.RevealInput{Token: first.Token, Purpose: "TEST"})
	code(t, e, biz.AuditUnavailable)
	if len(revealed.Value) != 0 {
		t.Fatal("unaudited plaintext escaped")
	}
	f.assertCounts(t, 1, 1, 1)
	revealed, e = f.service.Reveal(ctx, f.attempt(4), biz.RevealInput{Token: first.Token, Purpose: "TEST"})
	if e != nil || string(revealed.Value) != " Exact Value " {
		t.Fatal("rollback damaged committed record")
	}
	f.assertCounts(t, 1, 1, 2)
}
