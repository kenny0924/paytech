package observability

import (
	"context"
	"io"
	"log/slog"
	"os"
	"time"

	"github.com/go-logr/logr"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/codes"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/trace"

	"github.com/paytech/vault-service/internal/biz"
)

type Config struct {
	Writer        io.Writer
	DisableExport bool
	// Exporter is an explicit in-process test boundary, not remote auto-detection.
	Exporter sdktrace.SpanExporter
}
type Runtime struct {
	logger   *slog.Logger
	provider *sdktrace.TracerProvider
	tracer   trace.Tracer
	metrics  *metrics
}

func New(ctx context.Context, cfg Config) (*Runtime, error) {
	// SDK 1.46 WithResource unconditionally merges this environment input.
	// Reject it before SDK/exporter creation rather than capturing then filtering.
	if os.Getenv("OTEL_RESOURCE_ATTRIBUTES") != "" {
		return nil, biz.NewError(biz.InternalError)
	}
	if cfg.Writer == nil {
		cfg.Writer = os.Stdout
	}
	r := &Runtime{logger: slog.New(slog.NewJSONHandler(cfg.Writer, nil)), metrics: newMetrics()}
	otel.SetErrorHandler(otel.ErrorHandlerFunc(func(error) { r.logger.Error("telemetry_diagnostic") }))
	otel.SetLogger(logr.New(diagnosticSink{r.logger}))
	// No default resource, process/host detectors, environment resource attrs,
	// request instrumentation or global auto SDK: capture only the fixed identity.
	opts := []sdktrace.TracerProviderOption{sdktrace.WithResource(resource.NewSchemaless(attribute.String("service.name", "vault-service"))), sdktrace.WithSampler(sdktrace.ParentBased(sdktrace.AlwaysSample())), sdktrace.WithoutPanicRecording()}
	if cfg.Exporter != nil {
		opts = append(opts, sdktrace.WithSyncer(cfg.Exporter))
	} else if !cfg.DisableExport {
		exporter, err := otlptracehttp.New(ctx, otlptracehttp.WithTimeout(5*time.Second))
		if err != nil {
			return nil, biz.NewError(biz.InternalError)
		}
		opts = append(opts, sdktrace.WithBatcher(exporter, sdktrace.WithExportTimeout(5*time.Second)))
	}
	r.provider = sdktrace.NewTracerProvider(opts...)
	r.tracer = r.provider.Tracer("vault-service")
	return r, nil
}
func (r *Runtime) Shutdown(ctx context.Context) error {
	if r.provider.Shutdown(ctx) != nil {
		return biz.NewError(biz.InternalError)
	}
	return nil
}
func (r *Runtime) Start(ctx context.Context, action biz.Action) (context.Context, func(biz.AuditProjection, int, time.Duration)) {
	name := "vault.request"
	switch action {
	case biz.ProtectAction:
		name = "vault.protect"
	case biz.RevealAction:
		name = "vault.reveal"
	}
	ctx, span := r.tracer.Start(ctx, name, trace.WithSpanKind(trace.SpanKindServer))
	return ctx, func(p biz.AuditProjection, status int, duration time.Duration) {
		p = safeProjection(p)
		status = statusLabel(status)
		span.SetAttributes(r.access(ctx, p, status, duration)...)
		if status >= 400 {
			span.SetStatus(codes.Error, "")
		}
		r.metrics.request(p, status, duration)
		span.End()
	}
}
func (r *Runtime) dependency(ctx context.Context, name string) (context.Context, func(error)) {
	start := time.Now()
	ctx, span := r.tracer.Start(ctx, name, trace.WithSpanKind(trace.SpanKindInternal))
	return ctx, func(err error) {
		result := "SUCCESS"
		outcome := biz.Success
		if err != nil {
			result = string(biz.CodeOf(err))
			outcome = biz.OutcomeForCode(biz.ErrorCode(result))
			span.SetStatus(codes.Error, "")
		}
		span.SetAttributes(attribute.String("resultCode", result), attribute.String("outcome", string(outcome)), attribute.Float64("durationMs", float64(time.Since(start))/float64(time.Millisecond)))
		span.End()
	}
}
