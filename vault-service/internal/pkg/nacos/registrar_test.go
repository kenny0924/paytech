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

	"github.com/paytech/vault-service/internal/conf"
)

// Simulator models the SDK's cached instance set and uncertain remote effects.
// A blocked operation stays owned until released; cancellation cannot interrupt it.
type namingSimulator struct {
	mu              sync.Mutex
	instance        *model.Instance
	available       bool
	events          []string
	registrations   []vo.RegisterInstanceParam
	deregistrations []vo.DeregisterInstanceParam
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
func (s *namingSimulator) CloseClient() { defer s.enter("close")() }

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
	cfg, err := conf.Load(func(k string) string { return values[k] })
	if err != nil {
		t.Fatal(err)
	}
	root := t.TempDir()
	s := &namingSimulator{available: true}
	_, err = newClient(cfg.Nacos, root, func(p vo.NacosClientParam) (NamingClient, error) {
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
			client, err := newClient(conf.NacosConfig{Enabled: true, ServerAddress: "127.0.0.1:8848"}, t.TempDir(), func(vo.NacosClientParam) (NamingClient, error) {
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

func testConfig() conf.NacosConfig {
	return conf.NacosConfig{Enabled: true, Namespace: "mor-local", Group: "vault-group", InstanceIP: "127.0.0.2", InstancePort: 18108}
}

// constructWith 返回记录私有目录的构造函数。
func constructWith(s *namingSimulator, dir *string) func(conf.NacosConfig, string) (NamingClient, error) {
	return func(_ conf.NacosConfig, root string) (NamingClient, error) {
		*dir = root
		return s, nil
	}
}

func assertRemoved(t *testing.T, dir string) {
	t.Helper()
	if _, err := os.Stat(dir); !errors.Is(err, os.ErrNotExist) {
		t.Fatal("私有目录未删除")
	}
}

func TestRegisterThenCloseUsesSameIdentityAndCleans(t *testing.T) {
	s, dir := &namingSimulator{available: true}, ""
	r, err := register(context.Background(), testConfig(), constructWith(s, &dir))
	if err != nil {
		t.Fatal(err)
	}
	if err := r.Close(context.Background()); err != nil {
		t.Fatal(err)
	}
	want := vo.RegisterInstanceParam{ServiceName: "vault-service", GroupName: "vault-group", ClusterName: "DEFAULT", Ip: "127.0.0.2", Port: 18108, Weight: 1, Enable: true, Healthy: true, Ephemeral: true}
	if !reflect.DeepEqual(s.registrations, []vo.RegisterInstanceParam{want}) {
		t.Fatalf("注册参数：%+v", s.registrations)
	}
	d := s.deregistrations[0]
	if len(s.deregistrations) != 1 || d.ServiceName != want.ServiceName || d.GroupName != want.GroupName || d.Cluster != want.ClusterName || d.Ip != want.Ip || d.Port != want.Port || !d.Ephemeral {
		t.Fatalf("注销身份与注册不一致：%+v", s.deregistrations)
	}
	if !reflect.DeepEqual(s.events, []string{"register", "deregister", "close"}) {
		t.Fatalf("生命周期顺序：%v", s.events)
	}
	assertRemoved(t, dir)
}

func TestRegisterFailureDeregistersAttemptedIdentityAndSanitizes(t *testing.T) {
	s, dir := &namingSimulator{available: false}, ""
	r, err := register(context.Background(), testConfig(), constructWith(s, &dir))
	if r != nil || err == nil || strings.Contains(err.Error(), "SECRET") {
		t.Fatalf("注册失败未安全返回：%v", err)
	}
	if !reflect.DeepEqual(s.events, []string{"register", "deregister", "close"}) {
		t.Fatalf("失败清理顺序：%v", s.events)
	}
	assertRemoved(t, dir)
}

func TestConstructorFailureCleansOwnedDirectory(t *testing.T) {
	dir := ""
	r, err := register(context.Background(), testConfig(), func(_ conf.NacosConfig, root string) (NamingClient, error) {
		dir = root
		return nil, errors.New("SECRET-CONSTRUCT")
	})
	if r != nil || err == nil || strings.Contains(err.Error(), "SECRET") {
		t.Fatalf("构造失败未安全返回：%v", err)
	}
	assertRemoved(t, dir)
}

func TestStartupTimeoutReturnsAndLateRegistrationIsDeregistered(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		s, dir := &namingSimulator{available: true, block: "register", release: make(chan struct{})}, ""
		ctx, cancel := context.WithTimeout(context.Background(), time.Second)
		defer cancel()
		r, err := register(ctx, testConfig(), constructWith(s, &dir))
		if r != nil || err == nil {
			t.Fatal("启动超时应失败")
		}
		close(s.release)
		synctest.Wait()
		if !reflect.DeepEqual(s.events, []string{"register", "deregister", "close"}) {
			t.Fatalf("迟到的注册未被注销：%v", s.events)
		}
		assertRemoved(t, dir)
	})
}

func TestCloseIsBoundedByContext(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		s, dir := &namingSimulator{available: true}, ""
		r, err := register(context.Background(), testConfig(), constructWith(s, &dir))
		if err != nil {
			t.Fatal(err)
		}
		s.mu.Lock()
		s.block, s.release = "deregister", make(chan struct{})
		s.mu.Unlock()
		ctx, cancel := context.WithTimeout(context.Background(), time.Second)
		defer cancel()
		if err := r.Close(ctx); err == nil {
			t.Fatal("阻塞的注销应在期限到达时返回错误")
		}
		close(s.release)
		synctest.Wait()
		assertRemoved(t, dir)
	})
}
