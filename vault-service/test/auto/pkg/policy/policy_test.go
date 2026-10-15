package policy_test

import (
	"context"
	"errors"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/internal/pkg/policy"
)

func state(version int) biz.PolicyState {
	return biz.PolicyState{
		Version: version,
		DataTypes: []biz.DataType{
			{Code: "EMAIL", TokenMode: biz.Stable, Class: biz.StandardClass, ManualReveal: true, Status: biz.ActiveStatus},
			{Code: "OLD", TokenMode: biz.Reference, Class: biz.StandardClass, Status: biz.DisabledStatus},
		},
		Rules: []biz.AccessRule{
			{Caller: "commerce-service", Action: biz.ProtectAction, DataTypeCode: "EMAIL", Purpose: "CHECKOUT", Status: biz.ActiveStatus},
			{Caller: "commerce-service", Action: biz.RevealAction, DataTypeCode: "EMAIL", Purpose: "CHECKOUT", Status: biz.DisabledStatus},
			{Caller: "commerce-service", Action: biz.ProtectAction, DataTypeCode: "OLD", Purpose: "CHECKOUT", Status: biz.ActiveStatus},
		},
	}
}

func TestBuildKeepsOnlyActiveTypesAndRules(t *testing.T) {
	s := policy.Build(state(7))
	if s.Version() != "db-7" {
		t.Fatalf("version label = %q", s.Version())
	}
	if mode, ok := s.DataTypeMode("EMAIL"); !ok || mode != biz.Stable {
		t.Fatal("active data type missing")
	}
	if _, ok := s.DataType("OLD"); ok {
		t.Fatal("disabled data type must not be visible")
	}
	if !s.Authorize("commerce-service", biz.ProtectAction, "EMAIL", "CHECKOUT") {
		t.Fatal("active rule not authorized")
	}
	for _, denied := range []struct {
		action   biz.Action
		dataType string
	}{{biz.RevealAction, "EMAIL"}, {biz.ProtectAction, "OLD"}} {
		if s.Authorize("commerce-service", denied.action, denied.dataType, "CHECKOUT") {
			t.Fatalf("disabled rule or rule of disabled type authorized: %v", denied)
		}
	}
	if s.Authorize("other-service", biz.ProtectAction, "EMAIL", "CHECKOUT") || s.Authorize("commerce-service", biz.ProtectAction, "EMAIL", "OTHER") {
		t.Fatal("unregistered caller or purpose authorized")
	}
}

func TestEmptyPolicyDeniesEverything(t *testing.T) {
	s := policy.Build(biz.PolicyState{})
	if s.Version() != "db-0" || s.Authorize("commerce-service", biz.ProtectAction, "EMAIL", "CHECKOUT") {
		t.Fatal("empty policy must deny")
	}
}

type loader struct {
	mu    sync.Mutex
	state biz.PolicyState
	err   error
	calls atomic.Int32
}

func (l *loader) LoadPolicy(context.Context) (biz.PolicyState, error) {
	l.calls.Add(1)
	l.mu.Lock()
	defer l.mu.Unlock()
	return l.state, l.err
}

func (l *loader) set(state biz.PolicyState, err error) {
	l.mu.Lock()
	defer l.mu.Unlock()
	l.state, l.err = state, err
}

func TestRefresherStartFailsWithoutInitialPolicy(t *testing.T) {
	r := policy.NewRefresher(&loader{err: errors.New("SECRET-DB")}, time.Hour, nil)
	if err := r.Start(context.Background()); err == nil || biz.CodeOf(err) != biz.DependencyUnavailable {
		t.Fatalf("start must fail closed with a safe error: %v", err)
	}
}

func TestRefresherPicksUpChangesAndKeepsLastSnapshotOnFailure(t *testing.T) {
	l := &loader{state: state(1)}
	var failures atomic.Int32
	r := policy.NewRefresher(l, 20*time.Millisecond, func() { failures.Add(1) })
	if err := r.Start(context.Background()); err != nil {
		t.Fatal(err)
	}
	defer r.Close()
	if r.Version() != "db-1" || !r.Authorize("commerce-service", biz.ProtectAction, "EMAIL", "CHECKOUT") {
		t.Fatal("initial snapshot not active")
	}

	next := state(2)
	next.Rules = nil
	l.set(next, nil)
	waitFor(t, func() bool { return r.Version() == "db-2" })
	if r.Authorize("commerce-service", biz.ProtectAction, "EMAIL", "CHECKOUT") {
		t.Fatal("removed rule still authorized after refresh")
	}

	l.set(biz.PolicyState{}, errors.New("SECRET-DB"))
	waitFor(t, func() bool { return failures.Load() > 0 })
	if r.Version() != "db-2" {
		t.Fatal("failed refresh replaced the last good snapshot")
	}
}

func TestRefresherCloseStopsBackgroundRefresh(t *testing.T) {
	l := &loader{state: state(1)}
	r := policy.NewRefresher(l, 10*time.Millisecond, nil)
	if err := r.Start(context.Background()); err != nil {
		t.Fatal(err)
	}
	r.Close()
	after := l.calls.Load()
	time.Sleep(50 * time.Millisecond)
	if l.calls.Load() != after {
		t.Fatal("refresh continued after Close")
	}
}

func waitFor(t *testing.T, condition func() bool) {
	t.Helper()
	deadline := time.Now().Add(2 * time.Second)
	for !condition() {
		if time.Now().After(deadline) {
			t.Fatal("condition not reached within 2s")
		}
		time.Sleep(5 * time.Millisecond)
	}
}

func TestBuildCompilesValueRulesAndFailsClosedOnBrokenPattern(t *testing.T) {
	s := policy.Build(biz.PolicyState{Version: 3, DataTypes: []biz.DataType{
		{Code: "PHONE", TokenMode: biz.Stable, Status: biz.ActiveStatus, ValueFormat: biz.RegexFormat, ValuePattern: `\+[1-9][0-9]{7,14}`},
		{Code: "CONFIG", TokenMode: biz.Reference, Status: biz.ActiveStatus, ValueFormat: biz.JSONFormat},
		{Code: "LEGACY", TokenMode: biz.Reference, Status: biz.ActiveStatus},
		{Code: "BROKEN", TokenMode: biz.Reference, Status: biz.ActiveStatus, ValueFormat: biz.RegexFormat, ValuePattern: "("},
		{Code: "OFF", TokenMode: biz.Reference, Status: biz.DisabledStatus, ValueFormat: biz.TextFormat},
	}})
	checks := []struct {
		dataType string
		value    string
		code     biz.ErrorCode
	}{
		{"PHONE", "+8613800138000", ""},
		{"PHONE", "13800138000", biz.ValuePatternMismatch},
		{"CONFIG", `{"k":"v"}`, ""},
		{"CONFIG", `[1]`, biz.ValueNotJSONObject},
		{"LEGACY", "anything", ""},
		{"BROKEN", "(", biz.InternalError},
		{"OFF", "x", biz.UnsupportedDataType},
		{"MISSING", "x", biz.UnsupportedDataType},
	}
	for _, c := range checks {
		err := s.CheckValue(c.dataType, []byte(c.value))
		if c.code == "" && err != nil || c.code != "" && biz.CodeOf(err) != c.code {
			t.Fatalf("CheckValue(%s, %q) = %v, want %q", c.dataType, c.value, err, c.code)
		}
	}
}
