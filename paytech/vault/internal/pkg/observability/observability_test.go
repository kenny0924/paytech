package observability

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/sdk/trace/tracetest"

	"github.com/paytech/vault-service/internal/biz"
)

type lockedBuffer struct {
	sync.Mutex
	data bytes.Buffer
}

func (b *lockedBuffer) Write(p []byte) (int, error) {
	b.Lock()
	defer b.Unlock()
	return b.data.Write(p)
}
func (b *lockedBuffer) String() string { b.Lock(); defer b.Unlock(); return b.data.String() }
func telemetry(t *testing.T, exporter sdktrace.SpanExporter) (*Runtime, *lockedBuffer) {
	t.Helper()
	out := &lockedBuffer{}
	r, err := New(context.Background(), Config{Writer: out, DisableExport: true, Exporter: exporter})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if r.Shutdown(context.Background()) != nil {
			t.Error("shutdown")
		}
	})
	return r, out
}
func TestWhitelistedAccessTelemetryAndPrivateMetrics(t *testing.T) {
	t.Setenv("OTEL_RESOURCE_ATTRIBUTES", "")
	t.Setenv("OTEL_SERVICE_NAME", "ENV-SERVICE-CANARY")
	exporter := tracetest.NewInMemoryExporter()
	r, out := telemetry(t, exporter)
	data, purpose := "EMAIL_ADDRESS_V1", "CHECKOUT_CONTACT"
	ctx, finish := r.Start(context.Background(), biz.ProtectAction)
	finish(biz.AuditProjection{RequestID: "attempt-1", Caller: "commerce-service", Action: biz.ProtectAction, DataType: &data, Purpose: &purpose, Outcome: biz.Success, ResultCode: "SUCCESS"}, 200, time.Millisecond)
	spans := exporter.GetSpans()
	if len(spans) != 1 || spans[0].Name != "vault.protect" || !spans[0].SpanContext.IsValid() {
		t.Fatalf("missing controlled span: %+v", spans)
	}
	allowed := map[string]bool{"requestId": true, "traceId": true, "spanId": true, "caller": true, "action": true, "dataType": true, "purpose": true, "outcome": true, "resultCode": true, "httpStatus": true, "durationMs": true}
	for _, a := range spans[0].Attributes {
		if !allowed[string(a.Key)] {
			t.Fatalf("unapproved attribute: %s", a.Key)
		}
	}
	if !strings.Contains(out.String(), `"caller":"commerce-service"`) || !strings.Contains(out.String(), `"resultCode":"SUCCESS"`) {
		t.Fatal("missing safe access log")
	}
	if len(spans[0].Resource.Attributes()) != 1 || spans[0].Resource.Attributes()[0] != attribute.String("service.name", "vault-service") {
		t.Fatal("resource environment captured")
	}
	req := httptest.NewRequest("GET", "http://local/metrics", nil)
	rec := httptest.NewRecorder()
	r.MetricsHandler().ServeHTTP(rec, req)
	for _, wanted := range []string{"vault_requests_total{action=\"PROTECT\"", "vault_request_duration_seconds", "go_goroutines", "process_start_time_seconds"} {
		if !strings.Contains(rec.Body.String(), wanted) {
			t.Fatalf("missing %s", wanted)
		}
	}
	if strings.Contains(rec.Body.String(), "commerce-service") || strings.Contains(rec.Body.String(), "attempt-1") {
		t.Fatal("high cardinality metric")
	}
	_ = ctx
}
func TestResourceEnvironmentFailsBeforeCapture(t *testing.T) {
	t.Setenv("OTEL_RESOURCE_ATTRIBUTES", "secret=ENV-RESOURCE-CANARY")
	exporter := tracetest.NewInMemoryExporter()
	out := &bytes.Buffer{}
	runtime, err := New(context.Background(), Config{Writer: out, DisableExport: true, Exporter: exporter})
	if runtime != nil || err == nil || strings.Contains(err.Error()+out.String(), "CANARY") || len(exporter.GetSpans()) != 0 {
		t.Fatal("arbitrary environment metadata reached telemetry")
	}
}
func TestSDKPanicPropagatesWithoutRecordingPayload(t *testing.T) {
	exporter := tracetest.NewInMemoryExporter()
	r, out := telemetry(t, exporter)
	var recovered any
	func() {
		defer func() { recovered = recover() }()
		_, span := r.tracer.Start(context.Background(), "vault.protect")
		defer span.End()
		panic("PANIC-TRACE-CANARY")
	}()
	if recovered != "PANIC-TRACE-CANARY" {
		t.Fatal("panic behavior changed")
	}
	spans := exporter.GetSpans()
	if len(spans) != 1 || len(spans[0].Events) != 0 {
		t.Fatal("panic automatically recorded")
	}
	raw, _ := json.Marshal(spans)
	if strings.Contains(string(raw)+out.String(), "CANARY") {
		t.Fatal("panic payload captured")
	}
}

type failingExporter struct{}

func (failingExporter) ExportSpans(context.Context, []sdktrace.ReadOnlySpan) error {
	return errors.New("EXPORTER-SECRET-ENDPOINT-CREDENTIAL")
}
func (failingExporter) Shutdown(context.Context) error { return errors.New("SHUTDOWN-SECRET") }
func TestExporterAndHTTPDiagnosticSinksDiscardRawContent(t *testing.T) {
	r, out := telemetry(t, tracetest.NewInMemoryExporter())
	otel.Handle(errors.New("EXPORTER-SECRET-ENDPOINT-CREDENTIAL"))
	r.ServerErrorLog().Print("PANIC-CANARY remote=127.0.0.1 stack=STACK-CANARY")
	server := httptest.NewUnstartedServer(http.HandlerFunc(func(http.ResponseWriter, *http.Request) { panic("PANIC-PAYLOAD-CANARY") }))
	server.Config.ErrorLog = r.ServerErrorLog()
	server.Start()
	defer server.Close()
	resp, err := server.Client().Get(server.URL + "/SECRET-URL")
	if err == nil {
		resp.Body.Close()
		t.Fatal("panic should abort response")
	}
	logs := out.String()
	for _, canary := range []string{"EXPORTER-SECRET", "PANIC-CANARY", "STACK-CANARY", "PANIC-PAYLOAD", "127.0.0.1", "SECRET-URL"} {
		if strings.Contains(logs, canary) {
			t.Fatalf("diagnostic leakage: %s", logs)
		}
	}
	if !strings.Contains(logs, "telemetry_diagnostic") || !strings.Contains(logs, "http_server_diagnostic") {
		t.Fatal("lost safe diagnostics")
	}
}
func TestExporterErrorsUseSafeBoundary(t *testing.T) {
	out := &lockedBuffer{}
	r, err := New(context.Background(), Config{Writer: out, DisableExport: true, Exporter: failingExporter{}})
	if err != nil {
		t.Fatal(err)
	}
	_, finish := r.Start(context.Background(), biz.RevealAction)
	finish(biz.AuditProjection{Action: biz.RevealAction, Outcome: biz.Failed, ResultCode: "AUDIT_UNAVAILABLE"}, 503, time.Millisecond)
	if err := r.Shutdown(context.Background()); err == nil || strings.Contains(err.Error(), "SECRET") {
		t.Fatal("unsafe shutdown error")
	}
	if strings.Contains(out.String(), "SECRET") || !strings.Contains(out.String(), "telemetry_diagnostic") {
		t.Fatal("unsafe exporter diagnostics")
	}
}

type failingStore struct{}

func (failingStore) Ping(context.Context) error { return errors.New("DSN-CANARY") }
func (failingStore) FindRecordByToken(context.Context, string) (biz.Record, bool, error) {
	return biz.Record{}, false, errors.New("TOKEN-CAUSE-CANARY")
}
func (failingStore) InTransaction(ctx context.Context, fn func(biz.TxStore) error) error {
	return fn(failingTx{})
}

type failingTx struct{ biz.TxStore }

func (failingTx) InsertRecord(context.Context, biz.Record) error {
	return &biz.DuplicateError{Kind: biz.DuplicateStable}
}
func (failingTx) InsertProtectRequest(context.Context, biz.ProtectRequest) error {
	return &biz.DuplicateError{Kind: biz.DuplicateProtectRequest}
}

type failingKeys struct{ biz.KeyService }

func (failingKeys) Encrypt(context.Context, []byte, []byte) (biz.EncryptedValue, error) {
	return biz.EncryptedValue{}, errors.New("KEY-CAUSE-CANARY")
}
func (failingKeys) Decrypt(context.Context, []byte, biz.EncryptedValue) ([]byte, error) {
	return nil, errors.New("DECRYPT-CAUSE-CANARY")
}
func (failingKeys) MAC(context.Context, biz.MACPurpose, []byte) ([32]byte, error) {
	return [32]byte{}, errors.New("MAC-CAUSE-CANARY")
}
func TestStoreAndKeyObservationDoNotCaptureSensitiveInputs(t *testing.T) {
	exporter := tracetest.NewInMemoryExporter()
	r, out := telemetry(t, exporter)
	store := r.ObserveStore(failingStore{})
	keys := r.ObserveKeys(failingKeys{})
	_ = store.Ping(context.Background())
	_, _, _ = store.FindRecordByToken(context.Background(), "TOKEN-CANARY")
	_ = store.InTransaction(context.Background(), func(tx biz.TxStore) error {
		var fingerprint, requestHash [32]byte
		copy(fingerprint[:], "FINGERPRINT-CANARY")
		copy(requestHash[:], "REQUEST-HASH-CANARY")
		_ = tx.InsertRecord(context.Background(), biz.Record{Token: "TOKEN-CANARY", Ciphertext: []byte("CIPHERTEXT-CANARY"), ValueFingerprint: &fingerprint})
		return tx.InsertProtectRequest(context.Background(), biz.ProtectRequest{IdempotencyKey: "IDEMPOTENCY-CANARY", RequestHash: requestHash})
	})
	_, _ = keys.Encrypt(context.Background(), []byte("AAD-CANARY"), []byte("PLAINTEXT-CANARY"))
	_, _ = keys.Decrypt(context.Background(), []byte("AAD-CANARY"), biz.EncryptedValue{Ciphertext: []byte("CIPHERTEXT-CANARY"), Nonce: []byte("NONCE-CANARY"), WrappedDEK: []byte("WRAPPED-DEK-CANARY"), KeyRef: "KEY-REF-CANARY"})
	_, _ = keys.MAC(context.Background(), biz.MACProtectRequest, []byte("HASH-MATERIAL-CANARY"))
	raw, _ := json.Marshal(exporter.GetSpans())
	rec := httptest.NewRecorder()
	r.MetricsHandler().ServeHTTP(rec, httptest.NewRequest("GET", "/metrics", nil))
	all := string(raw) + out.String() + rec.Body.String()
	if strings.Contains(all, "CANARY") {
		t.Fatal("adapter observation leaked secret")
	}
	for _, wanted := range []string{"vault_database_duration_seconds", "vault_key_duration_seconds", "vault_duplicate_races_total{action=\"STABLE\"} 1", "vault_duplicate_races_total{action=\"IDEMPOTENCY\"} 1"} {
		if !strings.Contains(all, wanted) {
			t.Fatalf("missing observation %s", wanted)
		}
	}
	if len(exporter.GetSpans()) < 5 {
		t.Fatal("missing dependency spans")
	}
}
func TestLogFieldsRejectUnvalidatedProjection(t *testing.T) {
	exporter := tracetest.NewInMemoryExporter()
	r, out := telemetry(t, exporter)
	bad := "sensitive/body"
	_, finish := r.Start(context.Background(), biz.Action(bad))
	finish(biz.AuditProjection{RequestID: bad, Caller: bad, Action: biz.Action(bad), DataType: &bad, Purpose: &bad, Outcome: biz.Outcome(bad), ResultCode: bad}, 987, time.Millisecond)
	raw, _ := json.Marshal(exporter.GetSpans())
	if strings.Contains(string(raw)+out.String(), bad) {
		t.Fatal("unvalidated fields captured")
	}
	var entry map[string]any
	decoder := json.NewDecoder(strings.NewReader(out.String()))
	for decoder.Decode(&entry) == nil {
		if entry["msg"] == "vault_access" {
			break
		}
	}
	if entry["action"] != "UNKNOWN" || entry["resultCode"] != "INTERNAL_ERROR" {
		t.Fatal("unbounded labels")
	}
}

var _ io.Writer = (*lockedBuffer)(nil)
