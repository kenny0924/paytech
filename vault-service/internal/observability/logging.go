package observability

import (
	"context"
	"log"
	"log/slog"
	"regexp"
	"time"

	"github.com/go-logr/logr"
	"github.com/paytech/vault-service/internal/domain"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/trace"
)

var (
	safeID     = regexp.MustCompile(`^[A-Za-z0-9._~-]{1,64}$`)
	safeCaller = regexp.MustCompile(`^[a-z][a-z0-9-]{0,127}$`)
	safeCode   = regexp.MustCompile(`^[A-Z][A-Z0-9_]{0,63}$`)
)

func safeProjection(p domain.AuditProjection) domain.AuditProjection {
	if !safeID.MatchString(p.RequestID) {
		p.RequestID = ""
	}
	if !safeCaller.MatchString(p.Caller) {
		p.Caller = "UNKNOWN"
	}
	p.Action = domain.Action(actionLabel(p.Action))
	if p.DataType != nil && !safeCode.MatchString(*p.DataType) {
		p.DataType = nil
	}
	if p.Purpose != nil && !safeCode.MatchString(*p.Purpose) {
		p.Purpose = nil
	}
	p.ResultCode = resultLabel(p.ResultCode)
	p.Outcome = domain.OutcomeForCode(domain.ErrorCode(p.ResultCode))
	if p.ResultCode == "SUCCESS" {
		p.Outcome = domain.Success
	}
	return p
}
func statusLabel(status int) int {
	switch status {
	case 200, 400, 403, 404, 409, 500, 503:
		return status
	default:
		return 500
	}
}
func actionLabel(action domain.Action) string {
	switch action {
	case domain.ProtectAction, domain.RevealAction:
		return string(action)
	default:
		return "UNKNOWN"
	}
}
func resultLabel(code string) string {
	if code == "SUCCESS" {
		return code
	}
	return string(domain.NewError(domain.ErrorCode(code)).Code)
}

func (r *Runtime) access(ctx context.Context, p domain.AuditProjection, status int, duration time.Duration) []attribute.KeyValue {
	attrs := []attribute.KeyValue{attribute.String("action", string(p.Action)), attribute.String("caller", p.Caller), attribute.String("outcome", string(p.Outcome)), attribute.String("resultCode", p.ResultCode), attribute.Int("httpStatus", status), attribute.Float64("durationMs", float64(duration)/float64(time.Millisecond))}
	if p.RequestID != "" {
		attrs = append(attrs, attribute.String("requestId", p.RequestID))
	}
	if p.DataType != nil {
		attrs = append(attrs, attribute.String("dataType", *p.DataType))
	}
	if p.Purpose != nil {
		attrs = append(attrs, attribute.String("purpose", *p.Purpose))
	}
	if sc := trace.SpanContextFromContext(ctx); sc.IsValid() {
		attrs = append(attrs, attribute.String("traceId", sc.TraceID().String()), attribute.String("spanId", sc.SpanID().String()))
	}
	args := make([]any, 0, len(attrs))
	for _, a := range attrs {
		args = append(args, slog.Any(string(a.Key), a.Value.AsInterface()))
	}
	level := slog.LevelInfo
	if p.ResultCode == string(domain.AuditUnavailable) {
		level = slog.LevelError
	}
	r.logger.Log(ctx, level, "vault_access", args...)
	return attrs
}

// Diagnostic messages can contain credentials, endpoints, request URLs and
// panic stacks. Do not format or inspect them: emit one fixed event instead.
type diagnosticWriter struct{ logger *slog.Logger }

func (w diagnosticWriter) Write(p []byte) (int, error) {
	w.logger.Error("http_server_diagnostic")
	return len(p), nil
}
func (r *Runtime) ServerErrorLog() *log.Logger { return log.New(diagnosticWriter{r.logger}, "", 0) }

type diagnosticSink struct{ logger *slog.Logger }

func (d diagnosticSink) Init(logr.RuntimeInfo)          {}
func (d diagnosticSink) Enabled(int) bool               { return true }
func (d diagnosticSink) Info(int, string, ...any)       { d.logger.Warn("telemetry_diagnostic") }
func (d diagnosticSink) Error(error, string, ...any)    { d.logger.Error("telemetry_diagnostic") }
func (d diagnosticSink) WithValues(...any) logr.LogSink { return d }
func (d diagnosticSink) WithName(string) logr.LogSink   { return d }
