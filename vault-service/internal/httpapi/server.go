package httpapi

import (
	"context"
	"net/http"
	"time"

	"github.com/paytech/vault-service/internal/application"
	"github.com/paytech/vault-service/internal/domain"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/trace"
)

type Application interface {
	Protect(context.Context, application.Attempt, domain.ProtectInput) (domain.ProtectResult, error)
	Reveal(context.Context, application.Attempt, domain.RevealInput) (domain.RevealResult, error)
	AuditEarly(context.Context, domain.AuditProjection) error
}
type Observer interface {
	Start(context.Context, domain.Action) (context.Context, func(domain.AuditProjection, int, time.Duration))
}
type Dependencies struct {
	Service  Application
	Observer Observer
	Metrics  http.Handler
	Ready    func(context.Context) error
	Now      func() time.Time
}
type server struct{ Dependencies }

func New(deps Dependencies) http.Handler { return &server{deps} }

func (s *server) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	id := requestID(r.Header)
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Cache-Control", "no-store")
	w.Header().Set("X-Request-Id", id)
	path := r.URL.EscapedPath()
	action := domain.Action("")
	switch path {
	case "/vault/v1/records/protect":
		action = domain.ProtectAction
	case "/vault/v1/records/reveal":
		action = domain.RevealAction
	}
	if action != "" && r.Method != http.MethodPost {
		w.Header().Set("Allow", http.MethodPost)
		s.writeError(w, r, http.StatusMethodNotAllowed, id, methodNotAllowedCode)
		return
	}
	if action == "" {
		s.technical(w, r, path, id)
		return
	}
	// Traceparent only: never capture tracestate, Baggage, URL or other headers.
	carrier := propagation.MapCarrier{}
	if values := r.Header.Values("Traceparent"); len(values) == 1 {
		carrier.Set("traceparent", values[0])
	}
	ctx := propagation.TraceContext{}.Extract(r.Context(), carrier)
	start := time.Now()
	finish := func(domain.AuditProjection, int, time.Duration) {}
	if s.Observer != nil {
		ctx, finish = s.Observer.Start(ctx, action)
	}
	attempt := application.Attempt{RequestID: id, Caller: caller(r.Header), Action: action}
	if sc := trace.SpanContextFromContext(ctx); sc.IsValid() {
		attempt.TraceID = sc.TraceID().String()
	}
	p := domain.AuditProjection{RequestID: id, Caller: attempt.Caller, Action: action}
	if attempt.TraceID != "" {
		p.TraceID = &attempt.TraceID
	}
	status := http.StatusInternalServerError
	defer func() { finish(p, status, time.Since(start)) }()
	err := validateHeaders(r.Header, action)
	if err == nil {
		err = s.business(w, r.WithContext(ctx), attempt, &p)
	} else {
		err = s.early(ctx, &p, err)
	}
	if err != nil {
		code := domain.CodeOf(err)
		status = statusFor(code)
		p.ResultCode = string(code)
		p.Outcome = domain.OutcomeForCode(code)
		s.writeError(w, r, status, id, code)
		return
	}
	status = 200
	p.ResultCode = "SUCCESS"
	p.Outcome = domain.Success
}

func (s *server) technical(w http.ResponseWriter, r *http.Request, path, id string) {
	if r.Method == http.MethodGet {
		switch path {
		case "/health/liveness":
			writeJSON(w, 200, map[string]string{"status": "UP"})
			return
		case "/health/readiness":
			ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
			defer cancel()
			if s.Ready == nil || s.Ready(ctx) != nil {
				s.writeError(w, r, 503, id, domain.DependencyUnavailable)
				return
			}
			writeJSON(w, 200, map[string]string{"status": "UP"})
			return
		case "/metrics":
			if s.Metrics != nil {
				w.Header().Del("Content-Type")
				s.Metrics.ServeHTTP(w, r)
				return
			}
		}
	}
	s.writeError(w, r, 404, id, domain.InvalidRequest)
}
