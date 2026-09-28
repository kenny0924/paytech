package nacos

import (
	"bytes"
	"context"
	"encoding/base64"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"sync"
	"testing"
	"testing/synctest"
	"time"

	"github.com/nacos-group/nacos-sdk-go/v2/common/logger"
	"github.com/nacos-group/nacos-sdk-go/v2/model"
	"github.com/nacos-group/nacos-sdk-go/v2/vo"
	"github.com/paytech/vault-service/internal/config"
)

// Simulator models the SDK's cached instance set and uncertain remote effects.
// A blocked operation stays owned until released; cancellation cannot interrupt it.
type namingSimulator struct {
	mu              sync.Mutex
	instance        *model.Instance
	available       bool
	queryFailed     bool
	events          []string
	registrations   []vo.RegisterInstanceParam
	deregistrations []vo.DeregisterInstanceParam
	queries         []vo.SelectInstancesParam
	block           string
	release         chan struct{}
	active, maximum int
}

func (s *namingSimulator) enter(operation string) func() {
	s.mu.Lock()
	s.active++
	if s.active > s.maximum {
		s.maximum = s.active
	}
	s.events = append(s.events, operation)
	block := s.block == operation
	s.mu.Unlock()
	if block {
		<-s.release
	}
	return func() { s.mu.Lock(); s.active--; s.mu.Unlock() }
}
func (s *namingSimulator) RegisterInstance(p vo.RegisterInstanceParam) (bool, error) {
	defer s.enter("register")()
	s.mu.Lock()
	defer s.mu.Unlock()
	s.registrations = append(s.registrations, p)
	s.instance = &model.Instance{Ip: p.Ip, Port: p.Port, Healthy: p.Healthy, Enable: p.Enable, Ephemeral: p.Ephemeral, ClusterName: p.ClusterName}
	if !s.available {
		return false, errors.New("SECRET-SDK-REGISTER")
	}
	return true, nil
}
func (s *namingSimulator) DeregisterInstance(p vo.DeregisterInstanceParam) (bool, error) {
	defer s.enter("deregister")()
	s.mu.Lock()
	defer s.mu.Unlock()
	s.deregistrations = append(s.deregistrations, p)
	s.instance = nil
	if !s.available {
		return false, errors.New("SECRET-SDK-DEREGISTER")
	}
	return true, nil
}
func (s *namingSimulator) SelectHealthyInstances(p vo.SelectInstancesParam) ([]model.Instance, error) {
	defer s.enter("select")()
	s.mu.Lock()
	defer s.mu.Unlock()
	s.queries = append(s.queries, p)
	if s.queryFailed {
		return nil, errors.New("SECRET-SDK-QUERY")
	}
	if s.instance == nil {
		return nil, nil
	}
	return []model.Instance{*s.instance}, nil
}
func (s *namingSimulator) CloseClient() { defer s.enter("close")() }
func (s *namingSimulator) SelectInstances(p vo.SelectInstancesParam) ([]model.Instance, error) {
	return s.SelectHealthyInstances(p)
}

func TestSDKConstructionUsesPrivatePathsCredentialsAndNoopLogger(t *testing.T) {
	t.Setenv("ALIBABA_CLOUD_ACCESS_KEY_ID", "")
	t.Setenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET", "")
	values := map[string]string{
		"VAULT_RUNTIME_MODE": "test", "VAULT_DB_DSN": "synthetic", "VAULT_POLICY_FILE": "synthetic.json",
		"VAULT_LOCAL_AES_ROOT_KEY": base64.StdEncoding.EncodeToString(make([]byte, 32)), "VAULT_LOCAL_AES_KEY_ID": "test-key",
		"VAULT_SNOWFLAKE_DATACENTER_ID": "0", "VAULT_SNOWFLAKE_WORKER_ID": "1", "VAULT_NACOS_ENABLED": "true",
		"VAULT_NACOS_SERVER_ADDRESS": "127.0.0.1:8848", "VAULT_NACOS_NAMESPACE": "mor-local", "VAULT_NACOS_GROUP": "vault-group",
		"VAULT_NACOS_INSTANCE_IP": "127.0.0.2", "VAULT_NACOS_INSTANCE_PORT": "18108", "VAULT_NACOS_USERNAME": "synthetic-user", "VAULT_NACOS_PASSWORD": "synthetic-password",
	}
	cfg, err := config.Load(func(k string) string { return values[k] })
	if err != nil {
		t.Fatal(err)
	}
	root := t.TempDir()
	s := &namingSimulator{available: true}
	client, err := newClient(cfg.Nacos, root, func(p vo.NacosClientParam) (sdkNamingClient, error) {
		c := p.ClientConfig
		if c.NamespaceId != "mor-local" || c.Username != "synthetic-user" || c.Password != "synthetic-password" {
			t.Fatal("SDK credentials/namespace not propagated")
		}
		if c.CacheDir != filepath.Join(root, "cache") || c.LogDir != filepath.Join(root, "log") || !c.NotLoadCacheAtStart || !c.UpdateCacheWhenEmpty || c.AppendToStdout || c.TimeoutMs != 5000 {
			t.Fatal("unsafe SDK runtime options")
		}
		if !c.TLSCfg.Appointed || c.TLSCfg.Enable || len(p.ServerConfigs) != 1 || p.ServerConfigs[0].IpAddr != "127.0.0.1" || p.ServerConfigs[0].Port != 8848 || p.ServerConfigs[0].Scheme != "http" {
			t.Fatal("private HTTP server config changed")
		}
		if _, ok := logger.GetLogger().(noopLogger); !ok {
			t.Fatal("safe logger was not installed before construction")
		}
		return s, nil
	})
	if err != nil {
		t.Fatal(err)
	}
	_, err = client.SelectHealthyInstances(vo.SelectInstancesParam{ServiceName: "vault-service", GroupName: "vault-group"})
	if err != nil || !s.queries[0].HealthyOnly {
		t.Fatal("SDK adapter did not force healthy-only selection")
	}
}

func TestSDKRejectsAmbientCloudCredentialsBeforeConstruction(t *testing.T) {
	for _, fields := range [][]string{{"ALIBABA_CLOUD_ACCESS_KEY_ID"}, {"ALIBABA_CLOUD_ACCESS_KEY_SECRET"}, {"ALIBABA_CLOUD_ACCESS_KEY_ID", "ALIBABA_CLOUD_ACCESS_KEY_SECRET"}} {
		t.Run(strings.Join(fields, "+"), func(t *testing.T) {
			t.Setenv("ALIBABA_CLOUD_ACCESS_KEY_ID", "")
			t.Setenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET", "")
			for _, field := range fields {
				t.Setenv(field, "SYNTHETIC-AMBIENT-SECRET-CANARY")
			}
			logs := &capturedSDKLogger{}
			logger.SetLogger(logs)
			t.Cleanup(func() { logger.SetLogger(noopLogger{}) })
			constructed := false
			client, err := newClient(config.NacosConfig{Enabled: true, ServerAddress: "127.0.0.1:8848"}, t.TempDir(), func(vo.NacosClientParam) (sdkNamingClient, error) {
				constructed = true
				return &namingSimulator{available: true}, nil
			})
			if constructed || client != nil || err == nil {
				t.Fatal("ambient credentials reached SDK construction")
			}
			if err.Error() != "依赖服务暂不可用" || strings.Contains(err.Error()+logs.String(), "SYNTHETIC-AMBIENT") {
				t.Fatal("ambient credential diagnostics were not fixed and safe")
			}
			if logger.GetLogger() != logs {
				t.Fatal("ambient credential rejection changed SDK logger")
			}
			for _, field := range fields {
				if os.Getenv(field) != "SYNTHETIC-AMBIENT-SECRET-CANARY" {
					t.Fatal("ambient environment was mutated")
				}
			}
		})
	}
}

type capturedSDKLogger struct {
	noopLogger
	bytes.Buffer
}

func (l *capturedSDKLogger) Info(args ...interface{})  { fmt.Fprint(&l.Buffer, args...) }
func (l *capturedSDKLogger) Warn(args ...interface{})  { fmt.Fprint(&l.Buffer, args...) }
func (l *capturedSDKLogger) Error(args ...interface{}) { fmt.Fprint(&l.Buffer, args...) }
func (l *capturedSDKLogger) Debug(args ...interface{}) { fmt.Fprint(&l.Buffer, args...) }
func (l *capturedSDKLogger) Infof(format string, args ...interface{}) {
	fmt.Fprintf(&l.Buffer, format, args...)
}
func (l *capturedSDKLogger) Warnf(format string, args ...interface{}) {
	fmt.Fprintf(&l.Buffer, format, args...)
}
func (l *capturedSDKLogger) Errorf(format string, args ...interface{}) {
	fmt.Fprintf(&l.Buffer, format, args...)
}
func (l *capturedSDKLogger) Debugf(format string, args ...interface{}) {
	fmt.Fprintf(&l.Buffer, format, args...)
}

func testRegistrar(s *namingSimulator) (*Registrar, *string, *int) {
	dir, constructions := "", 0
	r := newRegistrar(config.NacosConfig{Enabled: true, Namespace: "mor-local", Group: "vault-group", InstanceIP: "127.0.0.2", InstancePort: 18108}, func(_ config.NacosConfig, root string) (NamingClient, error) {
		s.mu.Lock()
		dir = root
		constructions++
		s.mu.Unlock()
		defer s.enter("construct")()
		return s, nil
	})
	return r, &dir, &constructions
}

func (s *namingSimulator) registrationCount() int {
	s.mu.Lock()
	defer s.mu.Unlock()
	return len(s.registrations)
}

func assertCleaned(t *testing.T, r *Registrar, s *namingSimulator, dir string, want []string) {
	t.Helper()
	if r.Registered() {
		t.Fatal("closed registrar is ready")
	}
	if !reflect.DeepEqual(s.events, want) || s.maximum != 1 {
		t.Fatalf("ordered lifecycle = %v; maximum active = %d", s.events, s.maximum)
	}
	if _, err := os.Stat(dir); !errors.Is(err, os.ErrNotExist) {
		t.Fatal("owned runtime directory remains")
	}
}

func TestRegistrationIdentityAndStartupContextIsNotRuntimeLifetime(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		s := &namingSimulator{available: true}
		r, dir, count := testRegistrar(s)
		startup, cancel := context.WithTimeout(context.Background(), 15*time.Second)
		if err := r.Start(startup); err != nil {
			t.Fatal(err)
		}
		cancel()
		time.Sleep(16 * time.Second)
		synctest.Wait()
		if !r.Registered() {
			t.Fatal("finite startup context terminated registration")
		}
		if err := r.Start(context.Background()); err != nil {
			t.Fatal(err)
		}
		if *count != 1 {
			t.Fatal("client reconstructed")
		}
		p := s.registrations[0]
		if p.ServiceName != "vault-service" || p.GroupName != "vault-group" || p.Ip != "127.0.0.2" || p.Port != 18108 || p.Weight != 1 || !p.Enable || !p.Healthy || !p.Ephemeral || len(p.Metadata) != 0 {
			t.Fatal("registration identity or safe metadata changed")
		}
		q := s.queries[0]
		if q.ServiceName != "vault-service" || q.GroupName != "vault-group" || !q.HealthyOnly {
			t.Fatal("query identity changed")
		}
		if err := r.Close(context.Background()); err != nil {
			t.Fatal(err)
		}
		if err := r.Close(context.Background()); err != nil {
			t.Fatal(err)
		}
		d := s.deregistrations[0]
		if d.ServiceName != p.ServiceName || d.GroupName != p.GroupName || d.Ip != p.Ip || d.Port != p.Port || d.Cluster != p.ClusterName || d.Ephemeral != p.Ephemeral {
			t.Fatal("deregister identity differs")
		}
		assertCleaned(t, r, s, *dir, []string{"construct", "register", "select", "select", "select", "deregister", "close"})
	})
}

func TestInitialFailureDeregistersAttemptedIdentityAndSanitizes(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		s := &namingSimulator{}
		r, dir, _ := testRegistrar(s)
		err := r.Start(context.Background())
		if err == nil || strings.Contains(err.Error(), "SECRET") {
			t.Fatal("initial registration did not fail safely")
		}
		err = r.Close(context.Background())
		if err == nil || strings.Contains(err.Error(), "SECRET") {
			t.Fatal("deregister failure not reported safely")
		}
		assertCleaned(t, r, s, *dir, []string{"construct", "register", "deregister", "close"})
	})
}

func TestObservedLossRecoversWithCappedBackoff(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		s := &namingSimulator{available: true}
		r, _, _ := testRegistrar(s)
		if err := r.Start(context.Background()); err != nil {
			t.Fatal(err)
		}
		s.mu.Lock()
		s.instance = nil
		s.available = false
		s.mu.Unlock()
		time.Sleep(5 * time.Second)
		synctest.Wait()
		if r.Registered() {
			t.Fatal("SDK-observed loss remained ready")
		}
		for i, delay := range []time.Duration{1, 2, 4, 8, 16, 30, 30} {
			time.Sleep(delay*time.Second - time.Nanosecond)
			synctest.Wait()
			if s.registrationCount() != i+1 {
				t.Fatal("retry admitted early")
			}
			time.Sleep(time.Nanosecond)
			synctest.Wait()
			if s.registrationCount() != i+2 || r.Registered() {
				t.Fatal("bounded recovery state incorrect")
			}
		}
		s.mu.Lock()
		s.available = true
		s.mu.Unlock()
		time.Sleep(30 * time.Second)
		synctest.Wait()
		if !r.Registered() {
			t.Fatal("successful recovery not ready")
		}
		if err := r.Close(context.Background()); err != nil {
			t.Fatal(err)
		}
	})
}

func TestBlockedCallsStaySingleOwnedAndLateResultsOnlyCleanup(t *testing.T) {
	for _, operation := range []string{"construct", "register", "select", "deregister", "close"} {
		t.Run(operation, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				s := &namingSimulator{available: true, block: operation, release: make(chan struct{})}
				r, dir, count := testRegistrar(s)
				ctx, cancel := context.WithTimeout(context.Background(), 20*time.Millisecond)
				defer cancel()
				if operation == "construct" || operation == "register" {
					if err := r.Start(ctx); err == nil {
						t.Fatal("blocked startup succeeded")
					}
				} else {
					if err := r.Start(context.Background()); err != nil {
						t.Fatal(err)
					}
					if operation == "select" {
						time.Sleep(5 * time.Second)
						synctest.Wait()
					}
				}
				closeCtx, stop := context.WithTimeout(context.Background(), 20*time.Millisecond)
				defer stop()
				if err := r.Close(closeCtx); err == nil {
					t.Fatal("blocked close succeeded")
				}
				if r.Registered() {
					t.Fatal("canceled registrar is ready")
				}
				if err := r.Start(context.Background()); err == nil {
					t.Fatal("terminal registrar restarted")
				}
				s.mu.Lock()
				constructions, maximum, root := *count, s.maximum, *dir
				s.mu.Unlock()
				if constructions != 1 || maximum != 1 {
					t.Fatal("overlapping replacement client/call")
				}
				if _, err := os.Stat(root); err != nil {
					t.Fatal("directory deleted while SDK blocked")
				}
				close(s.release)
				if err := r.Close(context.Background()); err != nil {
					t.Fatal(err)
				}
				want := []string{"construct", "register", "deregister", "close"}
				if operation == "construct" {
					want = []string{"construct", "close"}
				}
				if operation == "select" {
					want = []string{"construct", "register", "select", "deregister", "close"}
				}
				assertCleaned(t, r, s, *dir, want)
			})
		})
	}
}

func TestUnhealthyMismatchedOrFailedCachedViewDropsReadiness(t *testing.T) {
	for _, scenario := range []string{"unhealthy", "disabled", "different IP", "different port", "different cluster", "persistent", "query error"} {
		t.Run(scenario, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				s := &namingSimulator{available: true}
				r, _, _ := testRegistrar(s)
				if err := r.Start(context.Background()); err != nil {
					t.Fatal(err)
				}
				s.mu.Lock()
				switch scenario {
				case "unhealthy":
					s.instance.Healthy = false
				case "disabled":
					s.instance.Enable = false
				case "different IP":
					s.instance.Ip = "127.0.0.3"
				case "different port":
					s.instance.Port = 18109
				case "different cluster":
					s.instance.ClusterName = "OTHER"
				case "persistent":
					s.instance.Ephemeral = false
				case "query error":
					s.queryFailed = true
				}
				s.mu.Unlock()
				time.Sleep(5 * time.Second)
				synctest.Wait()
				if r.Registered() {
					t.Fatal("unusable observed identity kept readiness")
				}
				if err := r.Close(context.Background()); err != nil {
					t.Fatal(err)
				}
				// Closing during the retry delay must admit no further registration.
				time.Sleep(time.Minute)
				synctest.Wait()
				if len(s.registrations) != 1 {
					t.Fatal("retry admitted after cancellation")
				}
			})
		})
	}
}

func TestBlockedRuntimeProbeTimeoutIsTerminalWithoutConcurrentCleanup(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		s := &namingSimulator{available: true, block: "select", release: make(chan struct{})}
		r, dir, _ := testRegistrar(s)
		if err := r.Start(context.Background()); err != nil {
			t.Fatal(err)
		}
		time.Sleep(15 * time.Second)
		synctest.Wait()
		if r.Registered() {
			t.Fatal("timed-out SDK call left readiness true")
		}
		if !reflect.DeepEqual(s.events, []string{"construct", "register", "select"}) {
			t.Fatal("cleanup/replacement overlapped blocked SDK call")
		}
		close(s.release)
		if err := r.Close(context.Background()); err != nil {
			t.Fatal(err)
		}
		assertCleaned(t, r, s, *dir, []string{"construct", "register", "select", "deregister", "close"})
	})
}

func TestConstructorFailureCleansOnlyOwnedDirectoryAndCannotRetry(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		root, calls := "", 0
		r := newRegistrar(config.NacosConfig{}, func(_ config.NacosConfig, dir string) (NamingClient, error) {
			root = dir
			calls++
			info, err := os.Stat(root)
			if err != nil || info.Mode().Perm() != 0700 {
				t.Fatal("runtime directory is not private")
			}
			return nil, errors.New("SECRET-PARTIAL-CONSTRUCTION")
		})
		if err := r.Start(context.Background()); err == nil || strings.Contains(err.Error(), "SECRET") {
			t.Fatal("construction failure not sanitized")
		}
		if err := r.Close(context.Background()); err != nil {
			t.Fatal(err)
		}
		if _, err := os.Stat(root); !errors.Is(err, os.ErrNotExist) {
			t.Fatal("failed construction directory remains")
		}
		if err := r.Start(context.Background()); err == nil || calls != 1 {
			t.Fatal("failed SDK client reconstructed")
		}
	})
}

func TestCloseBeforeStartNeverConstructs(t *testing.T) {
	r := newRegistrar(config.NacosConfig{}, func(config.NacosConfig, string) (NamingClient, error) {
		t.Fatal("constructed after terminal close")
		return nil, nil
	})
	if err := r.Close(context.Background()); err != nil {
		t.Fatal(err)
	}
	if err := r.Start(context.Background()); err == nil || r.Registered() {
		t.Fatal("closed registrar restarted")
	}
}
