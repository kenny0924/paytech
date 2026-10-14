package main

import (
	"bytes"
	"context"
	"encoding/base64"
	"errors"
	"io"
	"net"
	"net/http"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/paytech/vault-service/internal/bootstrap"
	"github.com/paytech/vault-service/internal/conf"
	"github.com/paytech/vault-service/internal/pkg/observability"
)

func TestProductionRefusesBeforeAssemblyOrListening(t *testing.T) {
	var built atomic.Bool
	out := &bytes.Buffer{}
	values := testConfigValues("127.0.0.1:18107")
	values["VAULT_RUNTIME_MODE"] = "production"
	env := func(key string) string {
		return values[key]
	}
	err := run(context.Background(), env, out, func(context.Context, conf.Config, *observability.Runtime) (*bootstrap.App, error) {
		built.Store(true)
		return nil, errors.New("SECRET-CAUSE")
	})
	if err == nil || built.Load() || strings.Contains(err.Error()+out.String(), "SECRET") {
		t.Fatal("production assembled or disclosed config")
	}
}
func TestServerTimeoutsAndSafePanicDiagnostics(t *testing.T) {
	out := &bytes.Buffer{}
	obs, err := observability.New(context.Background(), observability.Config{Writer: out, DisableExport: true})
	if err != nil {
		t.Fatal(err)
	}
	defer obs.Shutdown(context.Background())
	s := bootstrap.NewHTTPServer("127.0.0.1:0", http.HandlerFunc(func(http.ResponseWriter, *http.Request) { panic("SECRET-PANIC") }), obs)
	if s.ReadHeaderTimeout <= 0 || s.ReadTimeout <= 0 || s.WriteTimeout <= 0 || s.IdleTimeout <= 0 || s.MaxHeaderBytes <= 0 || s.ErrorLog == nil {
		t.Fatal("unbounded or unsafe server")
	}
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan error, 1)
	go func() { done <- serve(ctx, s, listener) }()
	resp, err := http.Get("http://" + listener.Addr().String() + "/SECRET-PATH")
	if err == nil {
		resp.Body.Close()
		t.Fatal("panic should abort")
	}
	cancel()
	if err := <-done; err != nil {
		t.Fatal(err)
	}
	if strings.Contains(out.String(), "SECRET") || strings.Contains(out.String(), "127.0.0.1") || !strings.Contains(out.String(), "http_server_diagnostic") {
		t.Fatal("unsafe server diagnostic")
	}
}
func TestGracefulShutdownLetsInFlightAuditFinish(t *testing.T) {
	entered := make(chan struct{})
	release := make(chan struct{})
	finished := make(chan struct{})
	obs, err := observability.New(context.Background(), observability.Config{Writer: io.Discard, DisableExport: true})
	if err != nil {
		t.Fatal(err)
	}
	defer obs.Shutdown(context.Background())
	s := bootstrap.NewHTTPServer("127.0.0.1:0", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		close(entered)
		<-release
		if r.Context().Err() != nil {
			t.Error("signal canceled in-flight attempt")
		}
		w.WriteHeader(200)
		close(finished)
	}), obs)
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan error, 1)
	go func() { done <- serve(ctx, s, listener) }()
	requestDone := make(chan error, 1)
	go func() {
		resp, err := http.Get("http://" + listener.Addr().String())
		if err == nil {
			resp.Body.Close()
		}
		requestDone <- err
	}()
	<-entered
	cancel()
	select {
	case <-done:
		t.Fatal("shutdown skipped in-flight attempt")
	case <-time.After(20 * time.Millisecond):
	}
	close(release)
	<-finished
	if err := <-requestDone; err != nil {
		t.Fatal(err)
	}
	if err := <-done; err != nil {
		t.Fatal(err)
	}
}

type closeProbe struct{ closed bool }

func (p *closeProbe) Close() error { p.closed = true; return nil }

func TestListenerFailureClosesInitializedDependencies(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	values := testConfigValues(listener.Addr().String())
	db := &closeProbe{}
	out := &bytes.Buffer{}
	built := false
	err = run(context.Background(), func(k string) string { return values[k] }, out, func(ctx context.Context, cfg conf.Config, obs *observability.Runtime) (*bootstrap.App, error) {
		built = true
		if _, ok := ctx.Deadline(); !ok {
			t.Fatal("unbounded initialization")
		}
		return &bootstrap.App{Store: db, Server: bootstrap.NewHTTPServer(cfg.HTTPAddress, http.NotFoundHandler(), obs)}, nil
	})
	if err == nil || !built || !db.closed || strings.Contains(err.Error()+out.String(), "CANARY") {
		t.Fatal("listener failure leaked or lost cleanup")
	}
}

type blockingCloser struct {
	calls     atomic.Int32
	entered   chan struct{}
	release   chan struct{}
	completed chan struct{}
}

func (p *blockingCloser) Close() error {
	p.calls.Add(1)
	close(p.entered)
	<-p.release
	close(p.completed)
	return errors.New("BLOCKED-CLOSE-SECRET")
}

type deadlineRegistration struct{ calls atomic.Int32 }

func (p *deadlineRegistration) Close(ctx context.Context) error {
	p.calls.Add(1)
	<-ctx.Done()
	return errors.New("DEREGISTER-TIMEOUT-SECRET")
}
func TestRunCleanupBoundsBlockingCloseWithRemainingDeadline(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	values := testConfigValues(listener.Addr().String())
	db := &blockingCloser{entered: make(chan struct{}), release: make(chan struct{}), completed: make(chan struct{})}
	reg := &deadlineRegistration{}
	out := &bytes.Buffer{}
	done := make(chan error, 1)
	runReturned := false
	go func() {
		done <- run(context.Background(), func(k string) string { return values[k] }, out, func(context.Context, conf.Config, *observability.Runtime) (*bootstrap.App, error) {
			return &bootstrap.App{Store: db, Registration: reg, Server: &http.Server{}}, nil
		})
	}()
	// Always release the simulated underlying Close so even RED cannot leak a
	// goroutine. A caller timeout must not pretend that it canceled this method.
	defer func() {
		close(db.release)
		select {
		case <-db.completed:
		case <-time.After(time.Second):
			t.Error("underlying Close did not finish after release")
		}
		if !runReturned {
			select {
			case <-done:
			case <-time.After(time.Second):
				t.Error("run did not finish after releasing Close")
			}
		}
	}()
	select {
	case err := <-done:
		runReturned = true
		if err == nil || strings.Contains(err.Error()+out.String(), "SECRET") {
			t.Fatal("unsafe cleanup result")
		}
	case <-time.After(bootstrap.ShutdownTimeout + 2*time.Second):
		t.Error("run blocked past the shared cleanup deadline; later cleanup cannot execute")
	}
	select {
	case <-db.entered:
	case <-time.After(time.Second):
		t.Fatal("database Close was not attempted after registration exhausted budget")
	}
	if db.calls.Load() != 1 || reg.calls.Load() != 1 {
		t.Fatal("cleanup was retried")
	}
	select {
	case <-db.completed:
		t.Fatal("deadline falsely completed underlying database Close")
	default:
	}
}

func testConfigValues(address string) map[string]string {
	return map[string]string{"VAULT_RUNTIME_MODE": "test", "VAULT_HTTP_ADDRESS": address, "VAULT_DB_DSN": "TEST-DSN-CANARY", "VAULT_POLICY_FILE": "unused", "VAULT_LOCAL_AES_ROOT_KEY": base64.StdEncoding.EncodeToString(bytes.Repeat([]byte{7}, 32)), "VAULT_LOCAL_AES_KEY_ID": "test", "VAULT_SNOWFLAKE_DATACENTER_ID": "31", "VAULT_SNOWFLAKE_WORKER_ID": "29", "VAULT_NACOS_ENABLED": "false", "OTEL_TRACES_EXPORTER": "none"}
}
