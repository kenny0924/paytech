package server

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"regexp"
	"strings"
	"sync"
	"testing"
	"time"

	"go.opentelemetry.io/otel/sdk/trace/tracetest"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/internal/pkg/observability"
	"github.com/paytech/vault-service/internal/service"
)

const goodBody = `{"dataType":"EMAIL_ADDRESS_V1","tokenMode":"STABLE","value":"alice@example.com","purpose":"CHECKOUT_CONTACT"}`
const goodToken = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

var problemTimestampPattern = regexp.MustCompile(`^[0-9]{4}-[0-9]{2}-[0-9]{2}T([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]\.[0-9]{6}Z$`)

// The application boundary owns durable outcomes; this simulator models that
// ownership so a handler's double dispatch or second audit is observable.
type applicationSimulator struct {
	mu                    sync.Mutex
	calls, early          int
	attempt               biz.Attempt
	projection            biz.AuditProjection
	input                 biz.ProtectInput
	value                 []byte
	returned              []byte
	failure, auditFailure error
}

func (s *applicationSimulator) Protect(_ context.Context, a biz.Attempt, in biz.ProtectInput) (biz.ProtectResult, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.calls++
	s.attempt = a
	s.input = in
	s.value = bytes.Clone(in.Value)
	return biz.ProtectResult{Token: goodToken}, s.failure
}
func (s *applicationSimulator) Reveal(_ context.Context, a biz.Attempt, in biz.RevealInput) (biz.RevealResult, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.calls++
	s.attempt = a
	s.returned = []byte("明文<&\"\n�😀")
	return biz.RevealResult{Value: s.returned}, s.failure
}
func (s *applicationSimulator) AuditEarly(_ context.Context, p biz.AuditProjection) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.early++
	s.projection = p
	return s.auditFailure
}
func fixture(t *testing.T, s *applicationSimulator, ready func(context.Context) error) *httptest.Server {
	return fixtureAt(t, s, ready, nil)
}
func fixtureAt(t *testing.T, s *applicationSimulator, ready func(context.Context) error, now func() time.Time) *httptest.Server {
	t.Helper()
	server := httptest.NewServer(New(Dependencies{Records: service.NewRecordService(protectAdapter{s}, revealAdapter{s}, auditAdapter{s}), Ready: ready, Now: now}))
	t.Cleanup(server.Close)
	return server
}
func send(t *testing.T, server *httptest.Server, method, path, body string, modify func(http.Header)) (*http.Response, []byte) {
	t.Helper()
	req, err := http.NewRequest(method, server.URL+path, strings.NewReader(body))
	if err != nil {
		t.Fatal(err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Caller-Service", "commerce-service")
	req.Header.Set("Idempotency-Key", "key-1")
	req.Header.Set("X-Request-Id", "attempt-1")
	if modify != nil {
		modify(req.Header)
	}
	resp, err := server.Client().Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		t.Fatal(err)
	}
	if resp.Header.Get("Cache-Control") != "no-store" || len(resp.Header.Values("X-Request-Id")) != 1 {
		t.Fatalf("missing safety headers: %v", resp.Header)
	}
	if _, err := http.ParseTime(resp.Header.Get("Date")); err != nil || resp.Header.Get("X-Timestamp") != "" {
		t.Fatalf("invalid standard response time headers: %v", resp.Header)
	}
	wantContentType := "application/json"
	if resp.StatusCode >= 400 {
		wantContentType = "application/problem+json"
	}
	if path != "/metrics" && resp.Header.Get("Content-Type") != wantContentType {
		t.Fatalf("content type: %v", resp.Header)
	}
	return resp, raw
}
func errorCode(t *testing.T, raw []byte, id string, status int) string {
	t.Helper()
	var doc map[string]any
	if json.Unmarshal(raw, &doc) != nil || len(doc) != 6 {
		t.Fatalf("unsafe error shape: %s", raw)
	}
	for _, field := range []string{"type", "title", "detail", "code", "requestId", "timestamp"} {
		if _, ok := doc[field]; !ok {
			t.Fatalf("missing Problem field %s: %s", field, raw)
		}
	}
	if doc["type"] != "about:blank" || doc["title"] != http.StatusText(status) {
		t.Fatalf("wrong Problem identity: %s", raw)
	}
	detail, detailOK := doc["detail"].(string)
	code, codeOK := doc["code"].(string)
	timestamp, timestampOK := doc["timestamp"].(string)
	if !detailOK || detail == "" || !codeOK || code == "" || doc["requestId"] != id {
		t.Fatalf("invalid Problem values: %s", raw)
	}
	wantDetail := biz.NewError(biz.ErrorCode(code)).Error()
	if code == "METHOD_NOT_ALLOWED" {
		wantDetail = "请求方法不受支持"
	}
	if detail != wantDetail {
		t.Fatalf("Problem detail is not the safe local message: %s", raw)
	}
	if !timestampOK || !problemTimestampPattern.MatchString(timestamp) {
		t.Fatalf("timestamp is not fixed-microsecond UTC: %s", raw)
	}
	if _, err := time.Parse(time.RFC3339Nano, timestamp); err != nil {
		t.Fatalf("timestamp is not RFC3339: %s", raw)
	}
	return code
}

func TestProblemResponseUsesExactProfileAndOneClockRead(t *testing.T) {
	fixed := time.Date(2026, time.September, 6, 10, 20, 30, 123456789, time.FixedZone("test-offset", 7*60*60))
	clockReads := 0
	sim := &applicationSimulator{failure: biz.NewError(biz.AccessDenied)}
	server := fixtureAt(t, sim, nil, func() time.Time {
		clockReads++
		return fixed
	})
	resp, raw := send(t, server, http.MethodPost, protectPath, goodBody, func(header http.Header) {
		header.Set("X-Request-Id", "CaseSensitive._-9")
	})
	want := "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"detail\":\"访问被拒绝\",\"code\":\"ACCESS_DENIED\",\"requestId\":\"CaseSensitive._-9\",\"timestamp\":\"2026-09-06T03:20:30.123456Z\"}\n"
	if resp.StatusCode != http.StatusForbidden || string(raw) != want || clockReads != 1 {
		t.Fatalf("unexpected fixed Problem: status=%d clockReads=%d body=%s", resp.StatusCode, clockReads, raw)
	}
	for _, forbidden := range []string{"\"message\"", "\"status\"", "\"instance\"", "\"errors\"", "SECRET", goodBody} {
		if bytes.Contains(raw, []byte(forbidden)) {
			t.Fatalf("forbidden Problem member or canary %q: %s", forbidden, raw)
		}
	}
}
func TestStrictJSONBeforeApplicationAndOneEarlyAudit(t *testing.T) {
	cases := map[string]string{
		"unknown":           strings.Replace(goodBody, `"value":`, `"unknown":"x","value":`, 1),
		"wrong-case":        strings.Replace(goodBody, `"value"`, `"Value"`, 1),
		"case-alias":        strings.Replace(goodBody, `"value":`, `"Value":"x","value":`, 1),
		"duplicate":         strings.Replace(goodBody, `"value":`, `"value":"x","value":`, 1),
		"escaped-duplicate": strings.Replace(goodBody, `"value":`, `"v\u0061lue":"x","value":`, 1),
		"missing":           strings.Replace(goodBody, `"value":"alice@example.com",`, "", 1),
		"null":              strings.Replace(goodBody, `"alice@example.com"`, `null`, 1),
		"number":            strings.Replace(goodBody, `"alice@example.com"`, `123`, 1),
		"array-field":       strings.Replace(goodBody, `"alice@example.com"`, `[]`, 1),
		"array-root":        "[" + goodBody + "]", "null-root": "null", "trailing": goodBody + ` {}`, "garbage": goodBody + ` x`, "incomplete": goodBody[:20],
		"invalid-utf8":   strings.Replace(goodBody, "alice@example.com", string([]byte{0xff}), 1),
		"high-surrogate": strings.Replace(goodBody, "alice@example.com", `\ud800`, 1),
		"low-surrogate":  strings.Replace(goodBody, "alice@example.com", `\udfff`, 1),
		"bad-pair":       strings.Replace(goodBody, "alice@example.com", `\ud800\u0041`, 1),
		"malformed-code": strings.Replace(goodBody, "CHECKOUT_CONTACT", "bad", 1),
		"long-code":      strings.Replace(goodBody, "CHECKOUT_CONTACT", strings.Repeat("A", 65), 1),
		"bad-mode":       strings.Replace(goodBody, "STABLE", "stable", 1),
	}
	for name, body := range cases {
		t.Run(name, func(t *testing.T) {
			sim := &applicationSimulator{}
			server := fixture(t, sim, nil)
			resp, raw := send(t, server, "POST", protectPath, body, nil)
			if resp.StatusCode != 400 || errorCode(t, raw, resp.Header.Get("X-Request-Id"), resp.StatusCode) != "INVALID_REQUEST" {
				t.Fatalf("unexpected error: %d %s", resp.StatusCode, raw)
			}
			if sim.calls != 0 || sim.early != 1 || sim.projection.Action != biz.ProtectAction || sim.projection.ResultCode != "INVALID_REQUEST" {
				t.Fatalf("wrong attempt ownership: %+v", sim.projection)
			}
		})
	}
}
func TestUTF8ValueAndRawBodyBounds(t *testing.T) {
	for _, tc := range []struct {
		name, value string
		status      int
		code        string
	}{
		{"empty", "", 200, ""}, {"exact-ascii", strings.Repeat("a", 32768), 200, ""},
		{"exact-multibyte", strings.Repeat("é", 16384), 200, ""}, {"multibyte-over", strings.Repeat("é", 16384) + "a", 400, "VALUE_TOO_LARGE"},
		{"escaped-boundary", strings.Repeat(`\u0061`, 32768), 200, ""}, {"surrogate-pair", `\ud83d\ude00`, 200, ""}, {"replacement", "�", 200, ""},
		{"escaped-backslash", `\\ud800`, 200, ""},
	} {
		t.Run(tc.name, func(t *testing.T) {
			sim := &applicationSimulator{}
			server := fixture(t, sim, nil)
			body := strings.Replace(goodBody, "alice@example.com", tc.value, 1)
			resp, raw := send(t, server, "POST", protectPath, body, nil)
			if resp.StatusCode != tc.status {
				t.Fatalf("%d %s", resp.StatusCode, raw)
			}
			if tc.code != "" {
				if errorCode(t, raw, "attempt-1", resp.StatusCode) != tc.code || sim.early != 1 || sim.calls != 0 {
					t.Fatal("wrong limit audit")
				}
			} else if sim.calls != 1 || sim.early != 0 {
				t.Fatal("wrong successful ownership")
			}
		})
	}
	sim := &applicationSimulator{}
	server := fixture(t, sim, nil)
	padded := goodBody + strings.Repeat(" ", 256*1024-len(goodBody))
	resp, _ := send(t, server, "POST", protectPath, padded, nil)
	if resp.StatusCode != 200 {
		t.Fatal("exact raw limit rejected")
	}
	resp, raw := send(t, server, "POST", protectPath, padded+" ", nil)
	if resp.StatusCode != 400 || errorCode(t, raw, "attempt-1", resp.StatusCode) != "INVALID_REQUEST" || sim.early != 1 {
		t.Fatal("raw limit not enforced")
	}
}
func TestHeaderMultiplicityAndValidationOnWire(t *testing.T) {
	cases := map[string]func(http.Header){
		"missing-caller":         func(h http.Header) { h.Del("X-Caller-Service") },
		"duplicate-caller":       func(h http.Header) { h.Add("x-caller-service", "commerce-service") },
		"joined-caller":          func(h http.Header) { h.Set("X-Caller-Service", "commerce-service,other") },
		"invalid-caller":         func(h http.Header) { h.Set("X-Caller-Service", "COMMERCE") },
		"long-caller":            func(h http.Header) { h.Set("X-Caller-Service", strings.Repeat("a", 129)) },
		"missing-key":            func(h http.Header) { h.Del("Idempotency-Key") },
		"duplicate-key":          func(h http.Header) { h.Add("Idempotency-Key", "key-1") },
		"joined-key":             func(h http.Header) { h.Set("Idempotency-Key", "key-1,key-2") },
		"invalid-key":            func(h http.Header) { h.Set("Idempotency-Key", "key/1") },
		"long-key":               func(h http.Header) { h.Set("Idempotency-Key", strings.Repeat("k", 129)) },
		"duplicate-content-type": func(h http.Header) { h.Add("Content-Type", "application/json") },
		"missing-content-type":   func(h http.Header) { h.Del("Content-Type") },
		"wrong-content-type":     func(h http.Header) { h.Set("Content-Type", "text/plain") },
		"bad-charset":            func(h http.Header) { h.Set("Content-Type", "application/json; charset=iso-8859-1") },
	}
	for name, change := range cases {
		t.Run(name, func(t *testing.T) {
			sim := &applicationSimulator{}
			server := fixture(t, sim, nil)
			resp, raw := send(t, server, "POST", protectPath, goodBody, change)
			if resp.StatusCode != 400 || errorCode(t, raw, resp.Header.Get("X-Request-Id"), resp.StatusCode) != "INVALID_REQUEST" || sim.calls != 0 || sim.early != 1 {
				t.Fatal("invalid header accepted or unaudited")
			}
			if strings.Contains(name, "caller") && sim.projection.Caller != "UNKNOWN" {
				t.Fatal("invalid caller leaked")
			}
		})
	}
}
func TestRequestIDPreservationAndReplacementEchoAndTrace(t *testing.T) {
	cases := []struct {
		name     string
		values   []string
		preserve bool
	}{
		{name: "single-alphanumeric", values: []string{"a"}, preserve: true},
		{name: "case-sensitive-safe-symbols", values: []string{"Valid._-9"}, preserve: true},
		{name: "trailing-safe-symbols", values: []string{"A._-"}, preserve: true},
		{name: "maximum-length", values: []string{strings.Repeat("r", 64)}, preserve: true},
		{name: "missing"},
		{name: "empty", values: []string{""}},
		{name: "leading-dot", values: []string{".leading"}},
		{name: "leading-underscore", values: []string{"_leading"}},
		{name: "leading-hyphen", values: []string{"-leading"}},
		{name: "legacy-tilde", values: []string{"valid~legacy"}},
		{name: "colon", values: []string{"invalid:id"}},
		{name: "space", values: []string{"contains space"}},
		{name: "too-long", values: []string{strings.Repeat("a", 65)}},
		{name: "joined", values: []string{"attempt-1,attempt-2"}},
		{name: "repeated-different", values: []string{"attempt-1", "attempt-2"}},
		{name: "repeated-same", values: []string{"attempt-1", "attempt-1"}},
	}
	generatedPattern := regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$`)
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			sim := &applicationSimulator{failure: biz.NewError(biz.AccessDenied)}
			server := fixture(t, sim, nil)
			resp, raw := send(t, server, "POST", protectPath, goodBody, func(h http.Header) {
				h.Del("X-Request-Id")
				for _, value := range tc.values {
					h.Add("X-Request-Id", value)
				}
				h.Set("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
			})
			if resp.StatusCode != http.StatusForbidden || errorCode(t, raw, resp.Header.Get("X-Request-Id"), resp.StatusCode) != "ACCESS_DENIED" {
				t.Fatal(resp.StatusCode)
			}
			id := resp.Header.Get("X-Request-Id")
			if tc.preserve {
				if id != tc.values[0] {
					t.Fatal("valid ID replaced")
				}
			} else if !generatedPattern.MatchString(id) {
				t.Fatalf("replacement is not a valid request ID: %q", id)
			} else {
				for _, supplied := range tc.values {
					if id == supplied {
						t.Fatal("invalid or repeated request ID was preserved")
					}
				}
			}
			if sim.attempt.RequestID != id || sim.attempt.TraceID != "4bf92f3577b34da6a3ce929d0e0e4736" {
				t.Fatal("lost correlation")
			}
			if sim.calls != 1 || sim.early != 0 {
				t.Fatal("request ID replacement became a business rejection")
			}
		})
	}
}

func TestNonASCIIRequestIDIsReplacedWithoutBusinessRejection(t *testing.T) {
	sim := &applicationSimulator{failure: biz.NewError(biz.AccessDenied)}
	request := httptest.NewRequest(http.MethodPost, protectPath, strings.NewReader(goodBody))
	request.Header.Set("Content-Type", "application/json")
	request.Header.Set("X-Caller-Service", "commerce-service")
	request.Header.Set("Idempotency-Key", "key-1")
	request.Header.Set("X-Request-Id", "请求")
	recorder := httptest.NewRecorder()
	New(Dependencies{Records: service.NewRecordService(protectAdapter{sim}, revealAdapter{sim}, auditAdapter{sim})}).ServeHTTP(recorder, request)
	id := recorder.Header().Get("X-Request-Id")
	if recorder.Code != http.StatusForbidden || !regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$`).MatchString(id) || errorCode(t, recorder.Body.Bytes(), id, recorder.Code) != "ACCESS_DENIED" || sim.calls != 1 || sim.early != 0 {
		t.Fatalf("non-ASCII request ID was not safely replaced: status=%d id=%q body=%s", recorder.Code, id, recorder.Body.Bytes())
	}
}

func TestIdempotencyKeyRetainsIndependentLegacyGrammar(t *testing.T) {
	sim := &applicationSimulator{}
	server := fixture(t, sim, nil)
	resp, _ := send(t, server, http.MethodPost, protectPath, goodBody, func(header http.Header) {
		header.Set("Idempotency-Key", "~legacy.key_-1")
	})
	if resp.StatusCode != http.StatusOK || sim.input.IdempotencyKey != "~legacy.key_-1" || sim.calls != 1 || sim.early != 0 {
		t.Fatal("Request ID migration changed Idempotency-Key grammar")
	}
}
func TestKnownBusinessRoutesRejectNonPostMethods(t *testing.T) {
	for _, path := range []string{protectPath, revealPath} {
		for _, method := range []string{http.MethodGet, http.MethodPut, http.MethodPatch, http.MethodDelete, http.MethodOptions, "CUSTOM"} {
			t.Run(method+" "+path, func(t *testing.T) {
				sim := &applicationSimulator{}
				server := fixture(t, sim, nil)
				resp, raw := send(t, server, method, path, goodBody, nil)
				if resp.StatusCode != http.StatusMethodNotAllowed || errorCode(t, raw, resp.Header.Get("X-Request-Id"), resp.StatusCode) != "METHOD_NOT_ALLOWED" {
					t.Fatalf("known route method response: status=%d body=%s", resp.StatusCode, raw)
				}
				if len(resp.Header.Values("Allow")) != 1 || resp.Header.Get("Allow") != http.MethodPost {
					t.Fatalf("known route method Allow header: %v", resp.Header)
				}
				if sim.calls != 0 || sim.early != 0 {
					t.Fatal("method rejection entered the business or audit boundary")
				}
				for _, forbidden := range []string{"SECRET", goodBody} {
					if bytes.Contains(raw, []byte(forbidden)) {
						t.Fatalf("method rejection leaked %q: %s", forbidden, raw)
					}
				}
			})
		}
	}
}

func TestExactRoutingHasNoRedirectAliasesOrBusinessAudits(t *testing.T) {
	for _, tc := range []struct{ method, path string }{
		{"POST", protectPath + "/"},
		{"POST", revealPath + "/"},
		{"POST", "/vault/v1//records/protect"},
		{"POST", "/vault/v1/../v1/records/protect"},
		{"POST", "/vault/v1/records%2Fprotect"},
		{"POST", "/vault/v1/records/%70rotect"},
		{"POST", "/vault/v1/records%2Freveal"},
		{"POST", "/vault/v1/records:protect"},
		{"POST", "/vault/v1/records:reveal"},
		{"POST", "/internal/v1/vault/records:protect"},
		{"POST", "/internal/v1/vault/records:reveal"},
		{"POST", "/internal/v1/vault/records/protect"},
		{"POST", "/internal/v1/vault/records/reveal"},
		{"POST", "/missing"},
		{"GET", "/missing"},
	} {
		sim := &applicationSimulator{}
		server := fixture(t, sim, nil)
		resp, raw := send(t, server, tc.method, tc.path, goodBody, nil)
		if resp.StatusCode != 404 || errorCode(t, raw, resp.Header.Get("X-Request-Id"), resp.StatusCode) != "INVALID_REQUEST" || resp.Header.Get("Allow") != "" || sim.calls != 0 || sim.early != 0 {
			t.Fatalf("route alias %s %s status %d", tc.method, tc.path, resp.StatusCode)
		}
	}
}
func TestRevealStringAndOwnedPlaintextCleared(t *testing.T) {
	sim := &applicationSimulator{}
	server := fixture(t, sim, nil)
	resp, raw := send(t, server, "POST", revealPath, `{"token":"`+goodToken+`","purpose":"CHECKOUT_CONTACT"}`, func(h http.Header) { h.Del("Idempotency-Key") })
	var doc map[string]string
	if json.Unmarshal(raw, &doc) != nil || resp.StatusCode != 200 || doc["value"] != "明文<&\"\n�😀" || len(doc) != 1 {
		t.Fatalf("not string value: %s", raw)
	}
	if !bytes.Equal(sim.returned, make([]byte, len(sim.returned))) || sim.early != 0 {
		t.Fatal("plaintext retained or second audit")
	}
}

func TestProtectPreservesExactInputWithoutNormalization(t *testing.T) {
	value := "  Alice@EXAMPLE.com\r\n é\u0301😀�  "
	body, _ := json.Marshal(map[string]string{"dataType": "EMAIL_ADDRESS_V1", "tokenMode": "STABLE", "value": value, "purpose": "CHECKOUT_CONTACT"})
	sim := &applicationSimulator{}
	server := fixture(t, sim, nil)
	resp, raw := send(t, server, "POST", protectPath, string(body), nil)
	if resp.StatusCode != 200 || string(sim.value) != value || sim.input.DataType != "EMAIL_ADDRESS_V1" || sim.input.TokenMode != biz.Stable || sim.input.Purpose != "CHECKOUT_CONTACT" || sim.input.IdempotencyKey != "key-1" {
		t.Fatal("input mutated")
	}
	if string(raw) != "{\"token\":\""+goodToken+"\"}\n" || !bytes.Equal(sim.input.Value, make([]byte, len(sim.input.Value))) {
		t.Fatal("wrong response or owned input retained")
	}
}
func TestRevealFailureClearsOwnedPlaintextAndNeverSecondAudits(t *testing.T) {
	sim := &applicationSimulator{failure: biz.NewError(biz.AuditUnavailable)}
	server := fixture(t, sim, nil)
	resp, raw := send(t, server, "POST", revealPath, `{"token":"`+goodToken+`","purpose":"CHECKOUT_CONTACT"}`, nil)
	if resp.StatusCode != 503 || errorCode(t, raw, "attempt-1", resp.StatusCode) != "AUDIT_UNAVAILABLE" || bytes.Contains(raw, []byte("明文")) || sim.calls != 1 || sim.early != 0 || !bytes.Equal(sim.returned, make([]byte, len(sim.returned))) {
		t.Fatal("failed Reveal leaked plaintext or repeated audit")
	}
}
func TestRevealStrictFieldsBeforeApplication(t *testing.T) {
	for _, body := range []string{`{"Token":"` + goodToken + `","purpose":"CHECKOUT_CONTACT"}`, `{"token":"bad","purpose":"CHECKOUT_CONTACT"}`, `{"token":"` + goodToken + `","purpose":null}`, `{"token":"` + goodToken + `","purpose":"CHECKOUT_CONTACT","value":"unexpected"}`, `{"token":"` + goodToken + `","purpose":"CHECKOUT_CONTACT","purp\u006fse":"CHECKOUT_CONTACT"}`} {
		sim := &applicationSimulator{}
		server := fixture(t, sim, nil)
		resp, raw := send(t, server, "POST", revealPath, body, nil)
		if resp.StatusCode != 400 || errorCode(t, raw, "attempt-1", resp.StatusCode) != "INVALID_REQUEST" || sim.calls != 0 || sim.early != 1 || sim.projection.Action != biz.RevealAction {
			t.Fatal("invalid Reveal accepted or missing early audit")
		}
	}
}
func TestSafeErrorMappingNeverSecondAudits(t *testing.T) {
	for _, tc := range []struct {
		code   biz.ErrorCode
		status int
	}{
		{biz.InvalidRequest, 400}, {biz.ValueTooLarge, 400}, {biz.UnsupportedDataType, 400}, {biz.TokenModeNotAllowed, 400}, {biz.AccessDenied, 403}, {biz.TokenNotFound, 404}, {biz.IdempotencyKeyConflict, 409}, {biz.IntegrityCheckFailed, 500}, {biz.InternalError, 500}, {biz.DependencyUnavailable, 503}, {biz.AuditUnavailable, 503},
	} {
		t.Run(string(tc.code), func(t *testing.T) {
			sim := &applicationSimulator{failure: biz.NewError(tc.code)}
			server := fixture(t, sim, nil)
			resp, raw := send(t, server, "POST", protectPath, goodBody, nil)
			if resp.StatusCode != tc.status || errorCode(t, raw, "attempt-1", resp.StatusCode) != string(tc.code) || sim.calls != 1 || sim.early != 0 {
				t.Fatal("wrong application error boundary")
			}
		})
	}
	sim := &applicationSimulator{auditFailure: errors.New("SECRET-CAUSE")}
	server := fixture(t, sim, nil)
	resp, raw := send(t, server, "POST", protectPath, "{", nil)
	if resp.StatusCode != 503 || errorCode(t, raw, "attempt-1", resp.StatusCode) != "AUDIT_UNAVAILABLE" || strings.Contains(string(raw), "SECRET") || sim.early != 1 {
		t.Fatal("unsafe early audit failure")
	}
}
func TestHealthReadinessAndNoBusinessAttempts(t *testing.T) {
	sim := &applicationSimulator{}
	server := fixture(t, sim, func(ctx context.Context) error {
		if _, ok := ctx.Deadline(); !ok {
			t.Error("unbounded health")
		}
		return errors.New("SECRET-DEPENDENCY")
	})
	resp, _ := send(t, server, "GET", "/health/liveness", "", nil)
	if resp.StatusCode != 200 {
		t.Fatal("liveness tied to dependency")
	}
	resp, raw := send(t, server, "GET", "/health/readiness", "", nil)
	if resp.StatusCode != 503 || errorCode(t, raw, "attempt-1", resp.StatusCode) != "DEPENDENCY_UNAVAILABLE" || strings.Contains(string(raw), "SECRET") {
		t.Fatal("readiness bypass")
	}
	if sim.early != 0 || sim.calls != 0 {
		t.Fatal("fake health attempt")
	}
}

func TestHeadProblemWritesNoBodyAndDoesNotConstructTimestamp(t *testing.T) {
	clockReads := 0
	now := func() time.Time {
		clockReads++
		return time.Date(2026, time.September, 6, 10, 20, 30, 123456000, time.UTC)
	}
	handler := New(Dependencies{Now: now})
	problems := problemWriter{now: now}
	for _, tc := range []struct {
		name   string
		status int
		code   biz.ErrorCode
	}{
		{name: "business-conflict", status: http.StatusConflict, code: biz.IdempotencyKeyConflict},
		{name: "technical-unavailable", status: http.StatusServiceUnavailable, code: biz.DependencyUnavailable},
	} {
		t.Run(tc.name, func(t *testing.T) {
			recorder := httptest.NewRecorder()
			request := httptest.NewRequest(http.MethodHead, protectPath, nil)
			problems.write(recorder, request, tc.status, "head-request", tc.code)
			if recorder.Code != tc.status || recorder.Body.Len() != 0 {
				t.Fatalf("HEAD status/body = %d/%q", recorder.Code, recorder.Body.String())
			}
			if recorder.Header().Get("Content-Type") != "application/problem+json" || recorder.Header().Get("Cache-Control") != "no-store" || len(recorder.Header().Values("X-Request-Id")) != 1 || recorder.Header().Get("X-Request-Id") != "head-request" {
				t.Fatalf("HEAD Problem headers: %v", recorder.Header())
			}
		})
	}
	if clockReads != 0 {
		t.Fatalf("HEAD constructed %d timestamps", clockReads)
	}
	for _, path := range []string{protectPath, revealPath} {
		recorder := httptest.NewRecorder()
		request := httptest.NewRequest(http.MethodHead, path, nil)
		request.Header.Set("X-Request-Id", "head-known")
		handler.ServeHTTP(recorder, request)
		if recorder.Code != http.StatusMethodNotAllowed || recorder.Body.Len() != 0 {
			t.Fatalf("known-route HEAD status/body = %d/%q", recorder.Code, recorder.Body.String())
		}
		if recorder.Header().Get("Content-Type") != problemContentType || recorder.Header().Get("Cache-Control") != "no-store" || len(recorder.Header().Values("X-Request-Id")) != 1 || recorder.Header().Get("X-Request-Id") != "head-known" || len(recorder.Header().Values("Allow")) != 1 || recorder.Header().Get("Allow") != http.MethodPost {
			t.Fatalf("known-route HEAD headers: %v", recorder.Header())
		}
	}
	recorder := httptest.NewRecorder()
	request := httptest.NewRequest(http.MethodHead, "/missing", nil)
	request.Header.Set("X-Request-Id", "head-technical")
	handler.ServeHTTP(recorder, request)
	if recorder.Code != http.StatusNotFound || recorder.Body.Len() != 0 || recorder.Header().Get("X-Request-Id") != "head-technical" || recorder.Header().Get("Allow") != "" || clockReads != 0 {
		t.Fatalf("raw HEAD technical response: status=%d headers=%v body=%q clockReads=%d", recorder.Code, recorder.Header(), recorder.Body.String(), clockReads)
	}
}

func TestActualServerTelemetryNeverCapturesUntrustedWireFields(t *testing.T) {
	var logs bytes.Buffer
	exporter := tracetest.NewInMemoryExporter()
	runtime, err := observability.New(context.Background(), observability.Config{Writer: &logs, DisableExport: true, Exporter: exporter})
	if err != nil {
		t.Fatal(err)
	}
	defer runtime.Shutdown(context.Background())
	sim := &applicationSimulator{failure: errors.New("CAUSE-SECRET-CANARY")}
	server := httptest.NewUnstartedServer(New(Dependencies{Records: service.NewRecordService(protectAdapter{sim}, revealAdapter{sim}, auditAdapter{sim}), Observer: runtime, Metrics: runtime.MetricsHandler(), Ready: func(context.Context) error { return nil }}))
	server.Config.ErrorLog = runtime.ServerErrorLog()
	server.Start()
	defer server.Close()
	body := strings.Replace(goodBody, "alice@example.com", "BODY-VALUE-CANARY", 1)
	resp, raw := send(t, server, "POST", protectPath+"?query=QUERY-CANARY", body, func(h http.Header) {
		h.Set("User-Agent", "UA-CANARY")
		h.Set("Authorization", "CREDENTIAL-CANARY")
		h.Set("Idempotency-Key", "IDEMPOTENCY-CANARY")
		h.Set("Baggage", "secret=BAGGAGE-CANARY")
		h.Set("Tracestate", "vendor=TRACESTATE-CANARY")
		h.Set("Traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
	})
	if resp.StatusCode != 500 || errorCode(t, raw, "attempt-1", resp.StatusCode) != "INTERNAL_ERROR" {
		t.Fatal("unsafe failure response")
	}
	_, _ = send(t, server, "POST", "/PATH-CANARY?x=QUERY-CANARY", body, nil)
	spans := exporter.GetSpans()
	if len(spans) != 1 || spans[0].Parent.TraceID().String() != "4bf92f3577b34da6a3ce929d0e0e4736" || spans[0].Parent.TraceState().Len() != 0 {
		t.Fatal("trace context boundary incorrect")
	}
	encoded, _ := json.Marshal(spans)
	rec := httptest.NewRecorder()
	runtime.MetricsHandler().ServeHTTP(rec, httptest.NewRequest("GET", "/metrics", nil))
	all := logs.String() + string(encoded) + rec.Body.String() + string(raw)
	if strings.Contains(all, "CANARY") || strings.Contains(all, "url.path") || strings.Contains(all, "user_agent") || strings.Contains(all, "127.0.0.1") {
		t.Fatal("wire content captured by observability")
	}
	if !strings.Contains(all, "audit_unavailable") || !strings.Contains(all, "vault_requests_total") {
		t.Fatal("missing bounded metrics")
	}
}

type countingBody struct {
	reader io.Reader
	read   int
}

func (c *countingBody) Read(p []byte) (int, error) {
	n, err := c.reader.Read(p)
	c.read += n
	return n, err
}
func (c *countingBody) Close() error { return nil }
func TestRawBodyIsStoppedBeforeFullReadOrDecode(t *testing.T) {
	body := &countingBody{reader: strings.NewReader(goodBody + strings.Repeat(" ", 1024*1024))}
	req := httptest.NewRequest("POST", protectPath, body)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Caller-Service", "commerce-service")
	req.Header.Set("Idempotency-Key", "key")
	sim := &applicationSimulator{}
	rec := httptest.NewRecorder()
	New(Dependencies{Records: service.NewRecordService(protectAdapter{sim}, revealAdapter{sim}, auditAdapter{sim})}).ServeHTTP(rec, req)
	if body.read > 256*1024+1 || rec.Code != 400 || sim.calls != 0 || sim.early != 1 {
		t.Fatal("unbounded body consumption")
	}
}

// 适配器把模拟器的三个方法分别暴露为接入层端口。
type protectAdapter struct{ *applicationSimulator }
type revealAdapter struct{ *applicationSimulator }
type auditAdapter struct{ *applicationSimulator }

func (a protectAdapter) Process(ctx context.Context, at biz.Attempt, in biz.ProtectInput) (biz.ProtectResult, error) {
	return a.Protect(ctx, at, in)
}
func (a revealAdapter) Process(ctx context.Context, at biz.Attempt, in biz.RevealInput) (biz.RevealResult, error) {
	return a.Reveal(ctx, at, in)
}
func (a auditAdapter) Record(ctx context.Context, p biz.AuditProjection) error {
	return a.AuditEarly(ctx, p)
}

func TestTechnicalEndpointsRejectOtherMethodsAsNotFound(t *testing.T) {
	for _, tc := range []struct{ method, path string }{
		{http.MethodPost, "/health/liveness"},
		{http.MethodPut, "/health/readiness"},
		{http.MethodDelete, "/metrics"},
		{http.MethodHead, "/health/liveness"},
	} {
		t.Run(tc.method+" "+tc.path, func(t *testing.T) {
			sim := &applicationSimulator{}
			recorder := httptest.NewRecorder()
			request := httptest.NewRequest(tc.method, tc.path, nil)
			request.Header.Set("X-Request-Id", "technical-method")
			New(Dependencies{Records: service.NewRecordService(protectAdapter{sim}, revealAdapter{sim}, auditAdapter{sim}), Metrics: http.NotFoundHandler()}).ServeHTTP(recorder, request)
			if recorder.Code != http.StatusNotFound || recorder.Header().Get("Allow") != "" || recorder.Header().Get("X-Request-Id") != "technical-method" {
				t.Fatalf("technical method response: status=%d headers=%v", recorder.Code, recorder.Header())
			}
			if tc.method != http.MethodHead && errorCode(t, recorder.Body.Bytes(), "technical-method", recorder.Code) != "INVALID_REQUEST" {
				t.Fatal("technical method Problem code")
			}
			if tc.method == http.MethodHead && recorder.Body.Len() != 0 {
				t.Fatalf("HEAD wrote body: %q", recorder.Body.String())
			}
			if sim.calls != 0 || sim.early != 0 {
				t.Fatal("technical method entered the business or audit boundary")
			}
		})
	}
}

func TestMetricsRouteServesOnlyWhenConfigured(t *testing.T) {
	metrics := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "text/plain; version=0.0.4")
		_, _ = w.Write([]byte("vault_requests_total 1\n"))
	})
	server := httptest.NewServer(New(Dependencies{Metrics: metrics}))
	t.Cleanup(server.Close)
	resp, raw := send(t, server, http.MethodGet, "/metrics", "", nil)
	if resp.StatusCode != http.StatusOK || resp.Header.Get("Content-Type") != "text/plain; version=0.0.4" || string(raw) != "vault_requests_total 1\n" {
		t.Fatalf("metrics route: status=%d headers=%v body=%q", resp.StatusCode, resp.Header, raw)
	}

	unconfigured := httptest.NewServer(New(Dependencies{}))
	t.Cleanup(unconfigured.Close)
	resp, raw = send(t, unconfigured, http.MethodGet, "/metrics", "", nil)
	if resp.StatusCode != http.StatusNotFound || errorCode(t, raw, resp.Header.Get("X-Request-Id"), resp.StatusCode) != "INVALID_REQUEST" {
		t.Fatalf("unconfigured metrics: status=%d body=%s", resp.StatusCode, raw)
	}
}
