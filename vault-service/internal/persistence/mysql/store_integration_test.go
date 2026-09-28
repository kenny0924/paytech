package mysql

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"os"
	"reflect"
	"testing"
	"time"

	driver "github.com/go-sql-driver/mysql"
	"github.com/paytech/vault-service/internal/application"
	"github.com/paytech/vault-service/internal/domain"
	"github.com/paytech/vault-service/internal/id"
)

var testIDs, _ = id.NewGenerator(31, 31)

func TestIntegrationCollisionCleanupPreservesForeignRecord(t *testing.T) {
	owner := integrationFixture(t)
	var foreign domain.Record
	t.Run("colliding-attempt", func(t *testing.T) {
		contender := integrationFixture(t)
		candidate := contender.record()
		foreign = candidate
		foreign.Token = randomIdentity(t)
		owner.records = append(owner.records, ownedRecord{foreign.ID, foreign.Token})
		if err := owner.store.InTransaction(context.Background(), func(tx application.TxStore) error { return tx.InsertRecord(context.Background(), foreign) }); err != nil {
			t.Fatal(err)
		}
		if err := contender.store.InTransaction(context.Background(), func(tx application.TxStore) error { return tx.InsertRecord(context.Background(), candidate) }); err == nil {
			t.Fatal("collision accepted")
		}
	})
	_, found, err := owner.store.FindRecordByToken(context.Background(), foreign.Token)
	if err != nil || !found {
		t.Fatal("collision cleanup deleted foreign record")
	}
}

type fixture struct {
	store   *Store
	t       *testing.T
	caller  string
	records []ownedRecord
}
type ownedRecord struct {
	id    int64
	token string
}

func integrationFixture(t *testing.T) *fixture {
	t.Helper()
	dsn := os.Getenv("VAULT_TEST_DB_DSN")
	if dsn == "" {
		t.Fatal("Set VAULT_TEST_DB_DSN for repository local MySQL and manually apply docs/database/sql/vault-schema.sql first")
	}
	cfg, err := driver.ParseDSN(dsn)
	if err != nil || cfg.Net != "tcp" || cfg.Addr != "127.0.0.1:13306" || cfg.DBName != "mor_vault" || cfg.User != "vault_app" {
		t.Fatal("Integration DSN must use scoped vault_app at loopback 127.0.0.1:13306/mor_vault")
	}
	s, err := Open(context.Background(), dsn, Options{})
	if err != nil {
		t.Fatal("Local Vault MySQL unavailable or schema mismatch; manually apply approved schema")
	}
	f := &fixture{store: s, t: t, caller: "task6-" + randomIdentity(t)}
	t.Cleanup(func() {
		defer s.Close()
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		for _, q := range []string{"DELETE FROM t_vault_access_event WHERE caller_service=?", "DELETE FROM t_vault_protect_request WHERE caller_service=?"} {
			if _, err := s.db.ExecContext(ctx, q, f.caller); err != nil {
				t.Error("owned row cleanup failed")
			}
		}
		for _, record := range f.records {
			if _, err := s.db.ExecContext(ctx, "DELETE FROM t_vault_record WHERE record_id=? AND token=?", record.id, record.token); err != nil {
				t.Error("owned record cleanup failed")
			}
		}
	})
	return f
}
func randomIdentity(t *testing.T) string {
	t.Helper()
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		t.Fatal("random source failed")
	}
	return hex.EncodeToString(b[:])
}
func (f *fixture) next() int64 {
	f.t.Helper()
	n, err := testIDs.Next(context.Background())
	if err != nil {
		f.t.Fatal("ID generation failed")
	}
	return n
}
func (f *fixture) record() domain.Record {
	n := f.next()
	token := randomIdentity(f.t)
	f.records = append(f.records, ownedRecord{n, token})
	return domain.Record{ID: n, Token: token, DataType: "EMAIL_ADDRESS_V1", TokenMode: domain.Reference, Ciphertext: []byte{1, 2, 3}, EncryptionNonce: [12]byte{9}, WrappedDEK: []byte{4, 5}, KMSKeyRef: "local-aes://test", CreateTime: time.Date(2026, 9, 4, 12, 0, 0, 123456789, time.FixedZone("offset", 14400)), UpdateTime: time.Now()}
}
func TestIntegrationRoundTripAtomicityAndNulls(t *testing.T) {
	f := integrationFixture(t)
	ctx := context.Background()
	r := f.record()
	p := domain.ProtectRequest{ID: f.next(), Caller: f.caller, IdempotencyKey: randomIdentity(t), RequestHash: [32]byte{7}, RecordID: r.ID, CreateTime: r.CreateTime, UpdateTime: r.UpdateTime}
	e := domain.AccessEvent{ID: f.next(), RequestID: randomIdentity(t), Caller: f.caller, Action: domain.ProtectAction, Outcome: domain.Success, ResultCode: "SUCCESS", RecordID: &r.ID, CreateTime: r.CreateTime}
	err := f.store.InTransaction(ctx, func(tx application.TxStore) error {
		if err := tx.InsertRecord(ctx, r); err != nil {
			return err
		}
		if err := tx.InsertProtectRequest(ctx, p); err != nil {
			return err
		}
		return tx.InsertAccessEvent(ctx, e)
	})
	if err != nil {
		t.Fatalf("commit: %v", err)
	}
	got, found, err := f.store.FindRecordByToken(ctx, r.Token)
	if err != nil || !found {
		t.Fatal("record missing")
	}
	r.CreateTime = r.CreateTime.UTC().Truncate(time.Microsecond)
	r.UpdateTime = r.UpdateTime.UTC().Truncate(time.Microsecond)
	if !reflect.DeepEqual(got, r) {
		t.Fatal("record mapping or UTC microseconds differ")
	}
	err = f.store.InTransaction(ctx, func(tx application.TxStore) error {
		got, found, err := tx.FindProtectRequest(ctx, p.Caller, p.IdempotencyKey, true)
		if err != nil || !found || got.RequestHash != p.RequestHash || got.RecordID != r.ID {
			t.Error("request mapping differs")
		}
		_, found, err = tx.FindRecordByID(ctx, r.ID)
		if err != nil || !found {
			t.Error("replay lookup failed")
		}
		return nil
	})
	if err != nil {
		t.Fatal(err)
	}
	aborted := f.record()
	stop := errors.New("stop")
	err = f.store.InTransaction(ctx, func(tx application.TxStore) error {
		if err := tx.InsertRecord(ctx, aborted); err != nil {
			return err
		}
		return stop
	})
	if !errors.Is(err, stop) {
		t.Fatal("callback failure lost")
	}
	_, found, err = f.store.FindRecordByToken(ctx, aborted.Token)
	if err != nil || found {
		t.Fatal("rollback leaked a record")
	}
	e.ID = f.next()
	e.RecordID = nil
	e.Outcome = domain.Denied
	e.ResultCode = "INVALID_REQUEST"
	if err = f.store.InTransaction(ctx, func(tx application.TxStore) error { return tx.InsertAccessEvent(ctx, e) }); err != nil {
		t.Fatal(err)
	}
}

func TestIntegrationDuplicateClassification(t *testing.T) {
	f := integrationFixture(t)
	ctx := context.Background()
	r := f.record()
	fp := [32]byte{}
	if _, err := rand.Read(fp[:]); err != nil {
		t.Fatal("random failed")
	}
	r.TokenMode = domain.Stable
	r.ValueFingerprint = &fp
	if err := f.store.InTransaction(ctx, func(tx application.TxStore) error { return tx.InsertRecord(ctx, r) }); err != nil {
		t.Fatal(err)
	}
	for _, kind := range []application.DuplicateKind{application.DuplicateToken, application.DuplicateStable} {
		other := f.record()
		if kind == application.DuplicateToken {
			other.Token = r.Token
		} else {
			other.TokenMode = domain.Stable
			other.ValueFingerprint = &fp
		}
		err := f.store.InTransaction(ctx, func(tx application.TxStore) error { return tx.InsertRecord(ctx, other) })
		var duplicate *application.DuplicateError
		if !errors.As(err, &duplicate) || duplicate.Kind != kind {
			t.Fatalf("duplicate classification: %v", err)
		}
	}
	if err := f.store.InTransaction(ctx, func(tx application.TxStore) error {
		got, found, err := tx.FindRecordByStableIdentityForShare(ctx, r.DataType, fp)
		if err != nil || !found || got.ID != r.ID {
			t.Error("stable winner missing")
		}
		return err
	}); err != nil {
		t.Fatal(err)
	}
	p := domain.ProtectRequest{ID: f.next(), Caller: f.caller, IdempotencyKey: randomIdentity(t), RecordID: r.ID, CreateTime: time.Now(), UpdateTime: time.Now()}
	if err := f.store.InTransaction(ctx, func(tx application.TxStore) error { return tx.InsertProtectRequest(ctx, p) }); err != nil {
		t.Fatal(err)
	}
	p.ID = f.next()
	err := f.store.InTransaction(ctx, func(tx application.TxStore) error { return tx.InsertProtectRequest(ctx, p) })
	var duplicate *application.DuplicateError
	if !errors.As(err, &duplicate) || duplicate.Kind != application.DuplicateProtectRequest {
		t.Fatalf("request duplicate: %v", err)
	}
}

func TestIntegrationShareLockTimeoutRollsBackWholeTransaction(t *testing.T) {
	f := integrationFixture(t)
	ctx := context.Background()
	r := f.record()
	fp := [32]byte{}
	rand.Read(fp[:])
	r.TokenMode = domain.Stable
	r.ValueFingerprint = &fp
	if err := f.store.InTransaction(ctx, func(tx application.TxStore) error { return tx.InsertRecord(ctx, r) }); err != nil {
		t.Fatal(err)
	}
	locked := make(chan struct{})
	release := make(chan struct{})
	done := make(chan error, 1)
	go func() {
		done <- f.store.InTransaction(ctx, func(tx application.TxStore) error {
			_, found, err := tx.FindRecordByStableIdentityForShare(ctx, r.DataType, fp)
			if err != nil {
				return err
			}
			if !found {
				return errors.New("winner absent")
			}
			close(locked)
			<-release
			return nil
		})
	}()
	select {
	case <-locked:
	case err := <-done:
		t.Fatalf("lock failed: %v", err)
	case <-time.After(5 * time.Second):
		t.Fatal("lock setup timeout")
	}
	defer func() {
		close(release)
		if err := <-done; err != nil {
			t.Error(err)
		}
	}()
	aborted := f.record()
	err := f.store.InTransaction(ctx, func(tx application.TxStore) error {
		raw := tx.(*transaction).tx
		if _, err := raw.ExecContext(ctx, "SET SESSION innodb_lock_wait_timeout=1"); err != nil {
			return classify(err)
		}
		if err := tx.InsertRecord(ctx, aborted); err != nil {
			return err
		}
		_, err := raw.ExecContext(ctx, "UPDATE t_vault_record SET update_time=? WHERE record_id=?", time.Now().UTC(), r.ID)
		return classify(err)
	})
	if !errors.Is(err, application.ErrRetryableContention) {
		t.Fatalf("FOR SHARE did not block conflicting write: %v", err)
	}
	_, found, err := f.store.FindRecordByToken(ctx, aborted.Token)
	if err != nil || found {
		t.Fatal("1205 retained earlier statement")
	}
}

func TestIntegrationAuditProjectionAndConstraints(t *testing.T) {
	f := integrationFixture(t)
	ctx := context.Background()
	r := f.record()
	trace, dataType, purpose, version := "0123456789abcdef0123456789abcdef", r.DataType, "CHECKOUT", "policy-v1"
	e := domain.AccessEvent{ID: f.next(), RequestID: randomIdentity(t), TraceID: &trace, Caller: f.caller, Action: domain.RevealAction, DataType: &dataType, Purpose: &purpose, RecordID: &r.ID, Outcome: domain.Success, ResultCode: "SUCCESS", PolicyVersion: &version, CreateTime: r.CreateTime}
	if err := f.store.InTransaction(ctx, func(tx application.TxStore) error {
		if err := tx.InsertRecord(ctx, r); err != nil {
			return err
		}
		return tx.InsertAccessEvent(ctx, e)
	}); err != nil {
		t.Fatal(err)
	}
	var got domain.AccessEvent
	err := f.store.db.QueryRowContext(ctx, "SELECT access_event_id,request_id,trace_id,caller_service,action,data_type,purpose,record_id,outcome,result_code,policy_version,create_time FROM t_vault_access_event WHERE access_event_id=?", e.ID).Scan(&got.ID, &got.RequestID, &got.TraceID, &got.Caller, &got.Action, &got.DataType, &got.Purpose, &got.RecordID, &got.Outcome, &got.ResultCode, &got.PolicyVersion, &got.CreateTime)
	e.CreateTime = e.CreateTime.UTC().Truncate(time.Microsecond)
	if err != nil || !reflect.DeepEqual(got, e) {
		t.Fatal("audit projection differs")
	}
	for _, scenario := range []string{"mode", "fingerprint", "action", "outcome", "foreign-key", "primary-key"} {
		t.Run(scenario, func(t *testing.T) {
			candidate := f.record()
			event := e
			event.ID = f.next()
			err := f.store.InTransaction(ctx, func(tx application.TxStore) error {
				switch scenario {
				case "mode":
					candidate.TokenMode = "INVALID"
					return tx.InsertRecord(ctx, candidate)
				case "fingerprint":
					candidate.TokenMode = domain.Stable
					return tx.InsertRecord(ctx, candidate)
				case "action":
					event.Action = "INVALID"
				case "outcome":
					event.Outcome = "INVALID"
				case "foreign-key":
					missing := candidate.ID
					event.RecordID = &missing
				case "primary-key":
					return tx.InsertRecord(ctx, r)
				}
				return tx.InsertAccessEvent(ctx, event)
			})
			var duplicate *application.DuplicateError
			if err == nil || errors.As(err, &duplicate) || domain.CodeOf(err) != domain.InternalError {
				t.Fatal("constraint failure incorrectly classified")
			}
		})
	}
}

func TestIntegrationMissingRecordsAndCancelledDependency(t *testing.T) {
	f := integrationFixture(t)
	ctx := context.Background()
	if err := f.store.InTransaction(ctx, func(tx application.TxStore) error {
		_, found, err := tx.FindProtectRequest(ctx, f.caller, randomIdentity(t), false)
		if err != nil || found {
			t.Error("missing request not represented")
		}
		_, found, err = tx.FindRecordByID(ctx, f.next())
		if err != nil || found {
			t.Error("missing record not represented")
		}
		return err
	}); err != nil {
		t.Fatal(err)
	}
	cancelled, cancel := context.WithCancel(ctx)
	cancel()
	if err := f.store.Ping(cancelled); domain.CodeOf(err) != domain.DependencyUnavailable {
		t.Fatal("cancelled ping not sanitized")
	}
	if err := f.store.InTransaction(cancelled, func(application.TxStore) error { t.Error("cancelled callback invoked"); return nil }); domain.CodeOf(err) != domain.DependencyUnavailable {
		t.Fatal("cancelled transaction not sanitized")
	}
	if err := f.store.InTransaction(ctx, nil); domain.CodeOf(err) != domain.InternalError {
		t.Fatal("nil callback accepted")
	}
	if _, _, err := f.store.FindRecordByToken(cancelled, randomIdentity(t)); domain.CodeOf(err) != domain.DependencyUnavailable {
		t.Fatal("cancelled read not sanitized")
	}
}
