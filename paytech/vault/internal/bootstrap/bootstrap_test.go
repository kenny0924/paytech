package bootstrap

import (
	"context"
	"errors"
	"strings"
	"testing"

	"github.com/paytech/vault-service/internal/conf"
)

func TestRegistrationAssemblyDisabledAndCanceledStartup(t *testing.T) {
	t.Setenv("ALIBABA_CLOUD_ACCESS_KEY_ID", "SYNTHETIC-DISABLED-ID-CANARY")
	t.Setenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET", "SYNTHETIC-DISABLED-SECRET-CANARY")
	if reg, err := startRegistration(context.Background(), conf.NacosConfig{}); err != nil || reg == nil || reg.Close(context.Background()) != nil {
		t.Fatal("disabled Nacos has side effects")
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if _, err := startRegistration(ctx, conf.NacosConfig{Enabled: true}); err == nil {
		t.Fatal("canceled initial registration reported healthy")
	}
}

type readinessProbe struct {
	err   error
	calls int
}

func (p *readinessProbe) Ping(context.Context) error  { p.calls++; return p.err }
func (p *readinessProbe) Ready(context.Context) error { p.calls++; return p.err }
func (p *readinessProbe) Close(context.Context) error { return nil }
func TestReadinessRequiresDatabaseAndKeys(t *testing.T) {
	db, key := &readinessProbe{}, &readinessProbe{}
	ready := readiness(db, key)
	if ready(context.Background()) != nil {
		t.Fatal("initialized dependencies not ready")
	}
	for _, p := range []*readinessProbe{db, key} {
		p.err = errors.New("SECRET-CAUSE")
		err := ready(context.Background())
		if err == nil || strings.Contains(err.Error(), "SECRET") {
			t.Fatal("unhealthy readiness or cause leaked")
		}
		p.err = nil
	}
}
func TestCleanupReportsDeregisterFailureAndStillClosesDatabase(t *testing.T) {
	db, reg := &closeProbe{}, &registrationProbe{}
	app := &App{Store: db, Registration: reg}
	err := app.Close()
	if err == nil || strings.Contains(err.Error(), "SECRET") || !db.closed || !reg.closed {
		t.Fatal("failed deregistration hidden or pool left open")
	}
}

type registrationProbe struct{ closed bool }

func (p *registrationProbe) Close(ctx context.Context) error {
	p.closed = true
	if _, ok := ctx.Deadline(); !ok {
		return errors.New("unbounded close")
	}
	return errors.New("DEREGISTER-SECRET")
}

type closeProbe struct{ closed bool }

func (p *closeProbe) Close() error { p.closed = true; return nil }
