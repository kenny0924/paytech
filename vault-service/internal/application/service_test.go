package application_test

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"maps"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/paytech/vault-service/internal/application"
	"github.com/paytech/vault-service/internal/domain"
	"github.com/paytech/vault-service/internal/integration/kms/localaes"
	"github.com/paytech/vault-service/internal/policy"
)

// These stateful transactions stage all three tables and publish only on commit.
type memoryStore struct {
	mu                      sync.Mutex
	records                 map[int64]domain.Record
	requests                map[string]domain.ProtectRequest
	events                  []domain.AccessEvent
	transactions            int
	faults                  []error
	commitUnknown           bool
	auditFailure            bool
	lookupFailure           error
	hideRequest, hideStable bool
	rollbackFailure         bool
	afterFault              func()
}

func newMemoryStore() *memoryStore {
	return &memoryStore{records: map[int64]domain.Record{}, requests: map[string]domain.ProtectRequest{}}
}
func (m *memoryStore) Ping(context.Context) error { return nil }
func (m *memoryStore) FindRecordByToken(_ context.Context, token string) (domain.Record, bool, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.lookupFailure != nil {
		return domain.Record{}, false, m.lookupFailure
	}
	for _, r := range m.records {
		if r.Token == token {
			return r, true, nil
		}
	}
	return domain.Record{}, false, nil
}
func (m *memoryStore) InTransaction(ctx context.Context, fn func(application.TxStore) error) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.transactions++
	if len(m.faults) > 0 {
		e := m.faults[0]
		m.faults = m.faults[1:]
		if e != nil {
			if m.afterFault != nil {
				m.afterFault()
			}
			return e
		}
	}
	if err := ctx.Err(); err != nil {
		return err
	}
	staged := &memoryTx{records: maps.Clone(m.records), requests: maps.Clone(m.requests), events: append([]domain.AccessEvent(nil), m.events...), owner: m}
	if err := fn(staged); err != nil {
		if m.rollbackFailure {
			m.rollbackFailure = false
			return application.ErrTransactionRollbackFailed
		}
		return err
	}
	m.records, m.requests, m.events = staged.records, staged.requests, staged.events
	if m.commitUnknown {
		m.commitUnknown = false
		return application.ErrTransactionOutcomeUnknown
	}
	return nil
}

type memoryTx struct {
	records  map[int64]domain.Record
	requests map[string]domain.ProtectRequest
	events   []domain.AccessEvent
	owner    *memoryStore
}

func (m *memoryTx) FindProtectRequest(_ context.Context, c, k string, _ bool) (domain.ProtectRequest, bool, error) {
	p, ok := m.requests[c+"/"+k]
	hidden := m.owner.hideRequest
	m.owner.hideRequest = false
	return p, ok && !hidden, nil
}
func (m *memoryTx) FindRecordByID(_ context.Context, id int64) (domain.Record, bool, error) {
	r, ok := m.records[id]
	return r, ok, nil
}
func (m *memoryTx) FindRecordByStableIdentityForShare(_ context.Context, d string, f [32]byte) (domain.Record, bool, error) {
	if m.owner.hideStable {
		m.owner.hideStable = false
		return domain.Record{}, false, nil
	}
	for _, r := range m.records {
		if r.DataType == d && r.ValueFingerprint != nil && *r.ValueFingerprint == f {
			return r, true, nil
		}
	}
	return domain.Record{}, false, nil
}
func (m *memoryTx) InsertRecord(_ context.Context, r domain.Record) error {
	for _, other := range m.records {
		if other.Token == r.Token {
			return &application.DuplicateError{Kind: application.DuplicateToken}
		}
		if r.ValueFingerprint != nil && other.ValueFingerprint != nil && r.DataType == other.DataType && *r.ValueFingerprint == *other.ValueFingerprint {
			return &application.DuplicateError{Kind: application.DuplicateStable}
		}
	}
	m.records[r.ID] = r
	return nil
}
func (m *memoryTx) InsertProtectRequest(_ context.Context, p domain.ProtectRequest) error {
	k := p.Caller + "/" + p.IdempotencyKey
	if _, ok := m.requests[k]; ok {
		return &application.DuplicateError{Kind: application.DuplicateProtectRequest}
	}
	m.requests[k] = p
	return nil
}
func (m *memoryTx) InsertAccessEvent(_ context.Context, e domain.AccessEvent) error {
	if m.owner.auditFailure {
		return errors.New("audit insert failed")
	}
	m.events = append(m.events, e)
	return nil
}

type sequenceIDs struct{ value atomic.Int64 }

func (i *sequenceIDs) Next(context.Context) (int64, error) { return i.value.Add(1), nil }

type fixture struct {
	store   *memoryStore
	service *application.Service
	keys    *keyBoundary
}
type keyBoundary struct {
	application.KeyService
	calls          int
	failure        error
	revealed       []byte
	canonical      [][]byte
	aadCopies      [][]byte
	plaintext      []byte
	encryptFailure error
}

func (k *keyBoundary) MAC(c context.Context, p application.MACPurpose, b []byte) ([32]byte, error) {
	k.calls++
	k.canonical = append(k.canonical, b)
	if k.failure != nil {
		return [32]byte{}, k.failure
	}
	return k.KeyService.MAC(c, p, b)
}
func (k *keyBoundary) Encrypt(c context.Context, a, b []byte) (application.EncryptedValue, error) {
	k.calls++
	k.aadCopies = append(k.aadCopies, a)
	k.plaintext = b
	if k.encryptFailure != nil {
		return application.EncryptedValue{}, k.encryptFailure
	}
	return k.KeyService.Encrypt(c, a, b)
}
func (k *keyBoundary) Decrypt(c context.Context, a []byte, e application.EncryptedValue) ([]byte, error) {
	k.calls++
	k.aadCopies = append(k.aadCopies, a)
	if k.failure != nil {
		return nil, k.failure
	}
	b, err := k.KeyService.Decrypt(c, a, e)
	k.revealed = b
	return b, err
}
func testPolicy(t *testing.T, reference, stable, caller string) application.Authorizer {
	t.Helper()
	p, err := policy.Load(bytes.NewBufferString(fmt.Sprintf(`{"policyVersion":"test-v1","dataTypes":{"%s":{"tokenMode":"REFERENCE"},"%s":{"tokenMode":"STABLE"}},"rules":[{"callerService":"%s","action":"PROTECT","dataType":"%s","purposes":["TEST","OTHER"]},{"callerService":"%s","action":"PROTECT","dataType":"%s","purposes":["TEST","OTHER"]},{"callerService":"%s","action":"REVEAL","dataType":"%s","purposes":["TEST"]},{"callerService":"%s","action":"REVEAL","dataType":"%s","purposes":["TEST"]},{"callerService":"other-service","action":"PROTECT","dataType":"%s","purposes":["TEST"]}]}`, reference, stable, caller, reference, caller, stable, caller, reference, caller, stable, stable)))
	if err != nil {
		t.Fatal(err)
	}
	return p
}
func newFixture(t *testing.T) *fixture {
	t.Helper()
	store := newMemoryStore()
	k, err := localaes.New(bytes.Repeat([]byte{7}, 32), "test")
	if err != nil {
		t.Fatal(err)
	}
	keys := &keyBoundary{KeyService: k}
	random := bytes.NewReader(bytes.Repeat([]byte("0123456789abcdefghijklmnopqrstuvwxyz"), 4096))
	return &fixture{store: store, keys: keys, service: application.NewService(store, keys, &sequenceIDs{}, testPolicy(t, "REFERENCE_VALUE", "STABLE_VALUE", "test-service"), random, time.Now)}
}
func attempt(n int) application.Attempt {
	return application.Attempt{RequestID: fmt.Sprintf("request-%d", n), TraceID: "0123456789abcdef0123456789abcdef", Caller: "test-service"}
}
func input(mode domain.TokenMode) domain.ProtectInput {
	d := "REFERENCE_VALUE"
	if mode == domain.Stable {
		d = "STABLE_VALUE"
	}
	return domain.ProtectInput{DataType: d, TokenMode: mode, Value: []byte(" Exact Value "), Purpose: "TEST", IdempotencyKey: "key"}
}
func code(t *testing.T, err error, want domain.ErrorCode) {
	t.Helper()
	if err == nil || domain.CodeOf(err) != want {
		t.Fatalf("error = %v, want %s", err, want)
	}
}
func protect(t *testing.T, f *fixture, a application.Attempt, in domain.ProtectInput) string {
	t.Helper()
	r, e := f.service.Protect(context.Background(), a, in)
	if e != nil || r.Token == "" {
		t.Fatalf("Protect failed: %v", e)
	}
	return r.Token
}
func TestReferenceReplayConflictAndAudit(t *testing.T) {
	f := newFixture(t)
	in := input(domain.Reference)
	original := bytes.Clone(in.Value)
	token := protect(t, f, attempt(1), in)
	if protect(t, f, attempt(2), in) != token {
		t.Fatal("replay changed token")
	}
	in.Purpose = "OTHER"
	r, err := f.service.Protect(context.Background(), attempt(3), in)
	code(t, err, domain.IdempotencyKeyConflict)
	if r.Token != "" {
		t.Fatal("conflict exposed token")
	}
	if len(f.store.records) != 1 || len(f.store.requests) != 1 || len(f.store.events) != 3 {
		t.Fatal("atomic replay/conflict cardinalities differ")
	}
	for i, e := range f.store.events {
		if e.RequestID != fmt.Sprintf("request-%d", i+1) || e.RecordID == nil || e.PolicyVersion == nil || *e.PolicyVersion != "test-v1" || e.TraceID == nil || e.DataType == nil || e.Purpose == nil || e.Action != domain.ProtectAction {
			t.Fatal("incomplete audit projection")
		}
	}
	if e := f.store.events[2]; e.Outcome != domain.Denied || e.ResultCode != "IDEMPOTENCY_KEY_CONFLICT" {
		t.Fatal("conflict audit missing")
	}
	if !bytes.Equal(in.Value, original) {
		t.Fatal("borrowed plaintext was altered")
	}
	in.IdempotencyKey = "new-key"
	if protect(t, f, attempt(4), in) == token {
		t.Fatal("reference identity incorrectly reused")
	}
}
func TestStableIdentityIsCrossCallerExactAndTypeScoped(t *testing.T) {
	f := newFixture(t)
	in := input(domain.Stable)
	token := protect(t, f, attempt(1), in)
	a := attempt(2)
	a.Caller = "other-service"
	in.IdempotencyKey = "other-key"
	if protect(t, f, a, in) != token {
		t.Fatal("stable identity scoped to caller")
	}
	in.Value = []byte("exact value")
	if protect(t, f, attempt(3), in) == token {
		t.Fatal("value was normalized")
	}
	if len(f.store.records) != 2 || len(f.store.events) != 3 {
		t.Fatal("stable cardinalities differ")
	}
}
func TestPolicyAndValidationBeforeKeys(t *testing.T) {
	for _, tc := range []struct {
		name    string
		change  func(*domain.ProtectInput, *application.Attempt)
		want    domain.ErrorCode
		version bool
	}{
		{"unregistered", func(i *domain.ProtectInput, _ *application.Attempt) { i.DataType = "UNKNOWN" }, domain.UnsupportedDataType, true},
		{"mode", func(i *domain.ProtectInput, _ *application.Attempt) { i.TokenMode = domain.Stable }, domain.TokenModeNotAllowed, true},
		{"denied", func(_ *domain.ProtectInput, a *application.Attempt) { a.Caller = "denied-service" }, domain.AccessDenied, true},
		{"invalid-utf8", func(i *domain.ProtectInput, _ *application.Attempt) { i.Value = []byte{255} }, domain.InvalidRequest, false},
		{"too-large", func(i *domain.ProtectInput, _ *application.Attempt) { i.Value = bytes.Repeat([]byte("a"), 32769) }, domain.ValueTooLarge, false},
		{"invalid-purpose", func(i *domain.ProtectInput, _ *application.Attempt) { i.Purpose = "bad purpose" }, domain.InvalidRequest, false},
		{"invalid-caller", func(_ *domain.ProtectInput, a *application.Attempt) { a.Caller = "BAD CALLER" }, domain.InvalidRequest, false},
		{"invalid-mode", func(i *domain.ProtectInput, _ *application.Attempt) { i.TokenMode = "INVALID" }, domain.InvalidRequest, false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			f := newFixture(t)
			i, a := input(domain.Reference), attempt(1)
			tc.change(&i, &a)
			r, err := f.service.Protect(context.Background(), a, i)
			code(t, err, tc.want)
			if r.Token != "" || f.keys.calls != 0 || len(f.store.events) != 1 {
				t.Fatal("validation accessed keys or omitted audit")
			}
			e := f.store.events[0]
			if (e.PolicyVersion != nil) != tc.version || e.ResultCode != string(tc.want) || e.Outcome != domain.Denied {
				t.Fatal("denial audit differs")
			}
		})
	}
}

func TestProtectRejectsDisallowedIdempotencyKeysBeforeKeys(t *testing.T) {
	for _, key := range []string{"/", ",", ":", "!", "@", "=", "+", "\\", "a b", "", strings.Repeat("a", 129)} {
		t.Run(fmt.Sprintf("%q", key), func(t *testing.T) {
			f := newFixture(t)
			in := input(domain.Reference)
			in.IdempotencyKey = key
			result, err := f.service.Protect(context.Background(), attempt(1), in)
			code(t, err, domain.InvalidRequest)
			if result.Token != "" || f.keys.calls != 0 || len(f.store.records) != 0 || len(f.store.requests) != 0 || len(f.store.events) != 1 {
				t.Fatal("invalid key accessed key material, wrote business state or omitted audit")
			}
			event := f.store.events[0]
			if event.ResultCode != "INVALID_REQUEST" || event.Outcome != domain.Denied || event.RequestID != "request-1" || event.PolicyVersion != nil {
				t.Fatal("invalid key audit projection differs")
			}
		})
	}
}

func TestProtectAcceptsIdempotencyKeyBoundaries(t *testing.T) {
	for _, key := range []string{"a", "AZaz09._~-", strings.Repeat("a", 128)} {
		t.Run(fmt.Sprintf("length-%d", len(key)), func(t *testing.T) {
			f := newFixture(t)
			in := input(domain.Reference)
			in.IdempotencyKey = key
			token := protect(t, f, attempt(1), in)
			if protect(t, f, attempt(2), in) != token || len(f.store.requests) != 1 || len(f.store.events) != 2 {
				t.Fatal("valid key did not retain replay semantics")
			}
			if f.store.events[1].RequestID != "request-2" || f.store.events[1].Outcome != domain.Success {
				t.Fatal("valid key changed per-attempt audit")
			}
		})
	}
}
func TestRevealAuditBeforePlaintextAndUnknownPolicyVersion(t *testing.T) {
	f := newFixture(t)
	token := protect(t, f, attempt(1), input(domain.Reference))
	q := domain.RevealInput{Token: token, Purpose: "TEST"}
	r, err := f.service.Reveal(context.Background(), attempt(2), q)
	if err != nil || string(r.Value) != " Exact Value " {
		t.Fatal("reveal failed")
	}
	before := f.keys.calls
	a := attempt(3)
	a.Caller = "denied-service"
	r, err = f.service.Reveal(context.Background(), a, q)
	code(t, err, domain.AccessDenied)
	if len(r.Value) != 0 || f.keys.calls != before {
		t.Fatal("denied reveal accessed keys")
	}
	q.Token = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
	_, err = f.service.Reveal(context.Background(), attempt(4), q)
	code(t, err, domain.TokenNotFound)
	if e := f.store.events[3]; e.PolicyVersion != nil || e.RecordID != nil || e.DataType != nil {
		t.Fatal("unknown token claimed policy consultation")
	}
	f.store.auditFailure = true
	q.Token = token
	r, err = f.service.Reveal(context.Background(), attempt(5), q)
	code(t, err, domain.AuditUnavailable)
	if len(r.Value) != 0 || !bytes.Equal(f.keys.revealed, make([]byte, len(f.keys.revealed))) || len(f.store.events) != 4 {
		t.Fatal("audit failure released plaintext or appended audit")
	}
}
func TestAmbiguousCommitNeverRetriesOrReaudits(t *testing.T) {
	for _, kind := range []string{"new", "replay", "conflict", "reveal"} {
		t.Run(kind, func(t *testing.T) {
			f := newFixture(t)
			in := input(domain.Reference)
			var token string
			if kind != "new" {
				token = protect(t, f, attempt(1), in)
			}
			if kind == "conflict" {
				in.Value = []byte("different")
			}
			before, events := f.store.transactions, len(f.store.events)
			f.store.commitUnknown = true
			if kind == "reveal" {
				r, e := f.service.Reveal(context.Background(), attempt(2), domain.RevealInput{Token: token, Purpose: "TEST"})
				code(t, e, domain.AuditUnavailable)
				if len(r.Value) != 0 {
					t.Fatal("plaintext escaped")
				}
			} else {
				r, e := f.service.Protect(context.Background(), attempt(2), in)
				code(t, e, domain.AuditUnavailable)
				if r.Token != "" {
					t.Fatal("token escaped")
				}
			}
			if f.store.transactions != before+1 || len(f.store.events) != events+1 {
				t.Fatal("ambiguous commit retried or reaudited")
			}
		})
	}
}
func TestRollbackFailureIsTerminalAndAuditedOnce(t *testing.T) {
	for _, failAudit := range []bool{false, true} {
		t.Run(fmt.Sprint(failAudit), func(t *testing.T) {
			f := newFixture(t)
			f.store.faults = []error{application.ErrTransactionRollbackFailed}
			f.store.auditFailure = failAudit
			r, e := f.service.Protect(context.Background(), attempt(1), input(domain.Reference))
			want := domain.DependencyUnavailable
			if failAudit {
				want = domain.AuditUnavailable
			}
			code(t, e, want)
			if r.Token != "" || f.store.transactions != 2 || len(f.store.records) != 0 {
				t.Fatal("unsafe retry after rollback failure")
			}
		})
	}
}
func TestBoundedContentionAndDuplicateRecovery(t *testing.T) {
	for _, failure := range []error{application.ErrRetryableContention, &application.DuplicateError{Kind: application.DuplicateToken}} {
		f := newFixture(t)
		f.store.faults = []error{failure, failure}
		protect(t, f, attempt(1), input(domain.Reference))
		if len(f.store.events) != 1 || len(f.store.records) != 1 {
			t.Fatal("rolled back work leaked")
		}
	}
	for _, kind := range []application.DuplicateKind{application.DuplicateProtectRequest, application.DuplicateStable} {
		t.Run(string(kind), func(t *testing.T) {
			f := newFixture(t)
			in := input(domain.Stable)
			token := protect(t, f, attempt(1), in)
			f.store.faults = []error{&application.DuplicateError{Kind: kind}}
			if kind == application.DuplicateStable {
				in.IdempotencyKey = "second"
			}
			if protect(t, f, attempt(2), in) != token {
				t.Fatal("winner not reused")
			}
			if len(f.store.records) != 1 || len(f.store.events) != 2 {
				t.Fatal("duplicate recovery cardinality")
			}
		})
	}
	for _, kind := range []application.DuplicateKind{application.DuplicateProtectRequest, application.DuplicateStable} {
		f := newFixture(t)
		f.store.faults = []error{&application.DuplicateError{Kind: kind}}
		r, e := f.service.Protect(context.Background(), attempt(1), input(domain.Stable))
		code(t, e, domain.DependencyUnavailable)
		if r.Token != "" || len(f.store.records) != 0 || len(f.store.events) != 1 || f.store.transactions > 20 {
			t.Fatal("missing winner created new work or unbounded retry")
		}
	}
	f := newFixture(t)
	for range 40 {
		f.store.faults = append(f.store.faults, application.ErrRetryableContention)
	}
	_, e := f.service.Protect(context.Background(), attempt(1), input(domain.Reference))
	code(t, e, domain.AuditUnavailable)
	if f.store.transactions > 20 {
		t.Fatal("contention unbounded")
	}
}
func TestFailureAuditOverridesOriginalAndDoesNotRecurse(t *testing.T) {
	for _, failure := range []error{domain.NewError(domain.IntegrityCheckFailed), domain.NewError(domain.DependencyUnavailable), errors.New("unsafe private detail")} {
		f := newFixture(t)
		token := protect(t, f, attempt(1), input(domain.Reference))
		f.keys.failure = failure
		r, e := f.service.Reveal(context.Background(), attempt(2), domain.RevealInput{Token: token, Purpose: "TEST"})
		code(t, e, domain.CodeOf(failure))
		if len(r.Value) != 0 || len(f.store.events) != 2 {
			t.Fatal("failure was not audited")
		}
		f.store.auditFailure = true
		before := f.store.transactions
		_, e = f.service.Reveal(context.Background(), attempt(3), domain.RevealInput{Token: token, Purpose: "TEST"})
		code(t, e, domain.AuditUnavailable)
		if f.store.transactions != before+1 {
			t.Fatal("recursive failure audit")
		}
	}
	f := newFixture(t)
	f.store.auditFailure = true
	r, e := f.service.Protect(context.Background(), attempt(1), input(domain.Reference))
	code(t, e, domain.AuditUnavailable)
	if r.Token != "" || len(f.store.records) != 0 || len(f.store.requests) != 0 || f.store.transactions != 1 {
		t.Fatal("failed atomic audit left business state or recursed")
	}
}
func TestAuditEarlyAndCancelledRetry(t *testing.T) {
	f := newFixture(t)
	e := f.service.AuditEarly(context.Background(), domain.AuditProjection{RequestID: "early", Caller: "UNKNOWN", Action: domain.ProtectAction, Outcome: domain.Denied, ResultCode: "INVALID_REQUEST"})
	if e != nil || len(f.store.events) != 1 || f.store.events[0].PolicyVersion != nil {
		t.Fatal("early audit incorrect")
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	before := time.Now()
	r, e := f.service.Protect(ctx, attempt(1), input(domain.Reference))
	code(t, e, domain.DependencyUnavailable)
	if r.Token != "" || time.Since(before) > time.Second || len(f.store.events) != 2 || f.store.events[1].ResultCode != "DEPENDENCY_UNAVAILABLE" {
		t.Fatal("cancel ignored")
	}
}

func TestRolledBackDuplicateLosersReuseCommittedWinner(t *testing.T) {
	for _, mode := range []domain.TokenMode{domain.Reference, domain.Stable} {
		t.Run(string(mode), func(t *testing.T) {
			f := newFixture(t)
			in := input(mode)
			token := protect(t, f, attempt(1), in)
			if mode == domain.Reference {
				f.store.hideRequest = true
			} else {
				in.IdempotencyKey = "second"
				f.store.hideStable = true
			}
			before := f.store.transactions
			if protect(t, f, attempt(2), in) != token {
				t.Fatal("duplicate winner changed")
			}
			if len(f.store.records) != 1 || len(f.store.events) != 2 || f.store.transactions != before+2 {
				t.Fatal("losing transaction leaked records or audit")
			}
		})
	}
}

func TestDuplicateRollbackFailureDoesNotEnterWinnerRecovery(t *testing.T) {
	f := newFixture(t)
	in := input(domain.Reference)
	protect(t, f, attempt(1), in)
	f.store.hideRequest = true
	f.store.rollbackFailure = true
	before := f.store.transactions
	r, e := f.service.Protect(context.Background(), attempt(2), in)
	code(t, e, domain.DependencyUnavailable)
	if r.Token != "" || f.store.transactions != before+2 || len(f.store.records) != 1 || len(f.store.events) != 2 {
		t.Fatal("rollback failure retried business work")
	}
	if event := f.store.events[1]; event.Outcome != domain.Failed || event.ResultCode != "DEPENDENCY_UNAVAILABLE" {
		t.Fatal("rollback failure audit differs")
	}
}

func TestOwnedSensitiveCopiesAreCleared(t *testing.T) {
	f := newFixture(t)
	in := input(domain.Stable)
	protect(t, f, attempt(1), in)
	for _, copy := range f.keys.canonical {
		if !bytes.Equal(copy, make([]byte, len(copy))) {
			t.Fatal("canonical plaintext copy retained")
		}
	}
	if !bytes.Equal(f.keys.plaintext, make([]byte, len(f.keys.plaintext))) || string(in.Value) != " Exact Value " {
		t.Fatal("owned plaintext retained or borrowed input cleared")
	}
	for _, aad := range f.keys.aadCopies {
		if !bytes.Equal(aad, make([]byte, len(aad))) {
			t.Fatal("owned canonical AAD retained")
		}
	}
}

func TestCorruptCiphertextAndLookupFailureAreAudited(t *testing.T) {
	f := newFixture(t)
	token := protect(t, f, attempt(1), input(domain.Reference))
	for id, r := range f.store.records {
		r.Ciphertext = bytes.Clone(r.Ciphertext)
		r.Ciphertext[0] ^= 1
		f.store.records[id] = r
	}
	r, e := f.service.Reveal(context.Background(), attempt(2), domain.RevealInput{Token: token, Purpose: "TEST"})
	code(t, e, domain.IntegrityCheckFailed)
	event := f.store.events[1]
	if len(r.Value) != 0 || event.RecordID == nil || event.PolicyVersion == nil || event.Outcome != domain.Failed || event.ResultCode != "INTEGRITY_CHECK_FAILED" {
		t.Fatal("integrity failure projection differs")
	}
	f.store.lookupFailure = domain.NewError(domain.DependencyUnavailable)
	_, e = f.service.Reveal(context.Background(), attempt(3), domain.RevealInput{Token: token, Purpose: "TEST"})
	code(t, e, domain.DependencyUnavailable)
	event = f.store.events[2]
	if event.RecordID != nil || event.PolicyVersion != nil || event.ResultCode != "DEPENDENCY_UNAVAILABLE" {
		t.Fatal("failed lookup claimed metadata/policy")
	}
	f.store.auditFailure = true
	_, e = f.service.Reveal(context.Background(), attempt(4), domain.RevealInput{Token: token, Purpose: "TEST"})
	code(t, e, domain.AuditUnavailable)
}

func TestSameStableValueDifferentDataTypeDoesNotConverge(t *testing.T) {
	f := newFixture(t)
	in := input(domain.Stable)
	token := protect(t, f, attempt(1), in)
	p, err := policy.Load(bytes.NewBufferString(`{"policyVersion":"test-v1","dataTypes":{"SECOND_STABLE":{"tokenMode":"STABLE"}},"rules":[{"callerService":"test-service","action":"PROTECT","dataType":"SECOND_STABLE","purposes":["TEST"]}]}`))
	if err != nil {
		t.Fatal(err)
	}
	ids := &sequenceIDs{}
	ids.value.Store(1000)
	s := application.NewService(f.store, f.keys, ids, p, bytes.NewReader(bytes.Repeat([]byte{99}, 128)), time.Now)
	in.DataType = "SECOND_STABLE"
	in.IdempotencyKey = "second"
	r, e := s.Protect(context.Background(), attempt(2), in)
	if e != nil || r.Token == "" || r.Token == token || len(f.store.records) != 2 {
		t.Fatal("stable identity ignored data type")
	}
}

func TestRetryUsesContextAwareBackoff(t *testing.T) {
	f := newFixture(t)
	f.store.faults = append(f.store.faults, application.ErrRetryableContention)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	f.store.afterFault = cancel
	// Cancellation after rolled-back contention ends business work, not its independent audit.
	start := time.Now()
	r, e := f.service.Protect(ctx, attempt(1), input(domain.Reference))
	code(t, e, domain.DependencyUnavailable)
	if r.Token != "" || time.Since(start) > time.Second || f.store.transactions != 2 || len(f.store.events) != 1 {
		t.Fatal("cancelled retry did not terminate")
	}
}

type failedEntropy struct{}

func (failedEntropy) Read([]byte) (int, error) { return 0, io.ErrUnexpectedEOF }

type failingIDs struct {
	sequenceIDs
	calls, failAt int
}

func (i *failingIDs) Next(ctx context.Context) (int64, error) {
	i.calls++
	if i.calls == i.failAt {
		return 0, errors.New("clock failure")
	}
	return i.sequenceIDs.Next(ctx)
}

func TestIDAndEntropyFailuresLeaveNoBusinessRows(t *testing.T) {
	for _, kind := range []string{"record-id", "event-id", "entropy"} {
		t.Run(kind, func(t *testing.T) {
			f := newFixture(t)
			var ids application.IDGenerator = &sequenceIDs{}
			var entropy io.Reader = bytes.NewReader(bytes.Repeat([]byte{81}, 128))
			switch kind {
			case "record-id":
				ids = &failingIDs{failAt: 1}
			case "event-id":
				ids = &failingIDs{failAt: 3}
			case "entropy":
				entropy = failedEntropy{}
			}
			s := application.NewService(f.store, f.keys, ids, testPolicy(t, "REFERENCE_VALUE", "STABLE_VALUE", "test-service"), entropy, time.Now)
			r, e := s.Protect(context.Background(), attempt(1), input(domain.Reference))
			want := domain.InternalError
			events := 1
			if kind == "event-id" {
				want = domain.AuditUnavailable
				events = 0
			}
			code(t, e, want)
			if r.Token != "" || len(f.store.records) != 0 || len(f.store.requests) != 0 || len(f.store.events) != events {
				t.Fatal("generator failure left business rows or incorrect audit")
			}
		})
	}
}

func TestEncryptFailureAuditsAndClearsOwnedInput(t *testing.T) {
	f := newFixture(t)
	f.keys.encryptFailure = domain.NewError(domain.DependencyUnavailable)
	in := input(domain.Reference)
	r, e := f.service.Protect(context.Background(), attempt(1), in)
	code(t, e, domain.DependencyUnavailable)
	if r.Token != "" || len(f.store.records) != 0 || len(f.store.events) != 1 || string(in.Value) != " Exact Value " || !bytes.Equal(f.keys.plaintext, make([]byte, len(f.keys.plaintext))) {
		t.Fatal("encryption failure leaked state or retained copied plaintext")
	}
}

func TestInvalidRevealFormatDoesNotReadPolicyOrKeys(t *testing.T) {
	f := newFixture(t)
	r, e := f.service.Reveal(context.Background(), attempt(1), domain.RevealInput{Token: "invalid", Purpose: "TEST"})
	code(t, e, domain.InvalidRequest)
	if len(r.Value) != 0 || f.keys.calls != 0 || len(f.store.events) != 1 || f.store.events[0].PolicyVersion != nil || f.store.events[0].RecordID != nil {
		t.Fatal("invalid reveal crossed metadata/policy boundary")
	}
}

type auditContextKey struct{}
type boundedAuditStore struct {
	application.Store
	t *testing.T
}

func (s boundedAuditStore) InTransaction(ctx context.Context, fn func(application.TxStore) error) error {
	deadline, ok := ctx.Deadline()
	if !ok || time.Until(deadline) <= 0 || time.Until(deadline) > 2*time.Second || ctx.Err() != nil || ctx.Value(auditContextKey{}) != "retained" {
		s.t.Error("audit context is not independently bounded or lost trace context")
	}
	return s.Store.InTransaction(ctx, fn)
}
func TestCancelledEarlyAuditRetainsContextWithinIndependentBound(t *testing.T) {
	f := newFixture(t)
	s := application.NewService(boundedAuditStore{f.store, t}, f.keys, &sequenceIDs{}, testPolicy(t, "REFERENCE_VALUE", "STABLE_VALUE", "test-service"), failedEntropy{}, time.Now)
	ctx, cancel := context.WithCancel(context.WithValue(context.Background(), auditContextKey{}, "retained"))
	cancel()
	err := s.AuditEarly(ctx, domain.AuditProjection{RequestID: "cancelled", Caller: "UNKNOWN", Action: domain.ProtectAction, Outcome: domain.Denied, ResultCode: "INVALID_REQUEST"})
	if err != nil || len(f.store.events) != 1 {
		t.Fatal("cancelled early attempt was not independently audited")
	}
}
