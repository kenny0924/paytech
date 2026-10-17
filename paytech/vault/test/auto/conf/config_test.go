package conf_test

import (
	"encoding/base64"
	"fmt"
	"strings"
	"testing"
	"time"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/internal/conf"
)

func TestLoadParsesApprovedEnvironment(t *testing.T) {
	env := validEnvironment("local")
	got, err := conf.Load(env.Get)
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}
	if len(got.AdminCallers) != 1 || got.AdminCallers[0] != "operations-console-bff" || got.PolicyRefreshInterval != 10*time.Second {
		t.Fatalf("Load() admin/refresh defaults = %v %v", got.AdminCallers, got.PolicyRefreshInterval)
	}
	if got.HTTPAddress != "127.0.0.1:18107" || got.Nacos.Group != "DEFAULT_GROUP" {
		t.Fatalf("Load() defaults = %#v", got)
	}
	if got.Nacos.Enabled {
		t.Fatal("Load() enabled Nacos unexpectedly")
	}
	if got.SnowflakeDatacenterID != 0 || got.SnowflakeWorkerID != 31 {
		t.Fatalf("Load() Snowflake IDs = %d, %d", got.SnowflakeDatacenterID, got.SnowflakeWorkerID)
	}
	if got.DatabaseDSN() != "vault:password@tcp(127.0.0.1:3306)/vault" {
		t.Fatal("Load() did not retain DSN")
	}
	if got.LocalAESKeyID() != "local-key-1" || len(got.LocalAESRootKey()) != 32 {
		t.Fatal("Load() did not retain local AES configuration")
	}
	if strings.Contains(got.String(), "password") || strings.Contains(got.String(), "local-key-1") {
		t.Fatalf("Config.String() exposed a secret: %s", got.String())
	}
}

func TestLoadRejectsInvalidRequiredConfiguration(t *testing.T) {
	cases := []struct {
		name   string
		mutate func(environment)
	}{
		{"missing DSN", func(env environment) { delete(env, "VAULT_DB_DSN") }},
		{"empty admin callers", func(env environment) { env["VAULT_ADMIN_CALLERS"] = "," }},
		{"invalid admin caller", func(env environment) { env["VAULT_ADMIN_CALLERS"] = "operations-console-bff,BAD" }},
		{"refresh interval too short", func(env environment) { env["VAULT_POLICY_REFRESH_INTERVAL"] = "10ms" }},
		{"refresh interval too long", func(env environment) { env["VAULT_POLICY_REFRESH_INTERVAL"] = "6m" }},
		{"invalid refresh interval", func(env environment) { env["VAULT_POLICY_REFRESH_INTERVAL"] = "soon" }},
		{"missing root key", func(env environment) { delete(env, "VAULT_LOCAL_AES_ROOT_KEY") }},
		{"invalid base64 root key", func(env environment) { env["VAULT_LOCAL_AES_ROOT_KEY"] = "***" }},
		{"short root key", func(env environment) {
			env["VAULT_LOCAL_AES_ROOT_KEY"] = base64.StdEncoding.EncodeToString(make([]byte, 31))
		}},
		{"invalid key identifier", func(env environment) { env["VAULT_LOCAL_AES_KEY_ID"] = "not/a/key" }},
		{"invalid runtime mode", func(env environment) { env["VAULT_RUNTIME_MODE"] = "staging" }},
		{"invalid HTTP address", func(env environment) { env["VAULT_HTTP_ADDRESS"] = "example.com:bad" }},
		{"datacenter out of range", func(env environment) { env["VAULT_SNOWFLAKE_DATACENTER_ID"] = "32" }},
		{"datacenter missing", func(env environment) { delete(env, "VAULT_SNOWFLAKE_DATACENTER_ID") }},
		{"worker missing", func(env environment) { delete(env, "VAULT_SNOWFLAKE_WORKER_ID") }},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			env := validEnvironment("test")
			tc.mutate(env)
			_, err := conf.Load(env.Get)
			assertInternalError(t, err)
		})
	}
}

func TestLoadRejectsProductionLocalAES(t *testing.T) {
	env := validEnvironment("production")
	_, err := conf.Load(env.Get)
	assertInternalError(t, err)
}

func TestLoadValidatesHTTPAddressBoundaries(t *testing.T) {
	for _, tc := range []struct {
		address string
		valid   bool
	}{
		{"127.0.0.1:1", true},
		{"vault.internal:65535", true},
		{"[::1]:18107", true},
		{":18107", false},
		{"127.0.0.1", false},
		{"127.0.0.1:", false},
		{"127.0.0.1:0", false},
		{"127.0.0.1:65536", false},
		{" vault.internal:18107", false},
		{"vault.internal :18107", false},
	} {
		t.Run(tc.address, func(t *testing.T) {
			env := validEnvironment("test")
			env["VAULT_HTTP_ADDRESS"] = tc.address
			_, err := conf.Load(env.Get)
			if !tc.valid {
				assertInternalError(t, err)
				return
			}
			if err != nil {
				t.Fatal("Load() rejected a valid HTTP address")
			}
		})
	}
}

func TestLoadRequiresCompleteNacosConfigurationWhenEnabled(t *testing.T) {
	env := validEnvironment("local")
	env["VAULT_NACOS_ENABLED"] = "true"
	env["VAULT_NACOS_SERVER_ADDRESS"] = "127.0.0.1:8848"
	env["VAULT_NACOS_NAMESPACE"] = "mor-local"
	env["VAULT_NACOS_GROUP"] = "vault-group"
	env["VAULT_NACOS_INSTANCE_IP"] = "127.0.0.2"
	env["VAULT_NACOS_INSTANCE_PORT"] = "18108"
	env["VAULT_NACOS_USERNAME"] = "nacos"
	env["VAULT_NACOS_PASSWORD"] = "secret"
	got, err := conf.Load(env.Get)
	if err != nil || !got.Nacos.Enabled || got.Nacos.Group != "vault-group" || got.Nacos.InstanceIP != "127.0.0.2" || got.Nacos.InstancePort != 18108 {
		t.Fatalf("Load() Nacos configuration was invalid: %v", err)
	}
	for _, name := range []string{
		"VAULT_NACOS_SERVER_ADDRESS",
		"VAULT_NACOS_NAMESPACE",
		"VAULT_NACOS_INSTANCE_IP",
		"VAULT_NACOS_INSTANCE_PORT",
		"VAULT_NACOS_USERNAME",
		"VAULT_NACOS_PASSWORD",
	} {
		t.Run("missing "+name, func(t *testing.T) {
			incomplete := cloneEnvironment(env)
			delete(incomplete, name)
			_, err := conf.Load(incomplete.Get)
			assertInternalError(t, err)
		})
	}
}

func TestConfigFormattingAlwaysRedactsSecrets(t *testing.T) {
	env := validEnvironment("local")
	env["VAULT_NACOS_ENABLED"] = "true"
	env["VAULT_NACOS_SERVER_ADDRESS"] = "127.0.0.1:8848"
	env["VAULT_NACOS_NAMESPACE"] = "mor-local"
	env["VAULT_NACOS_INSTANCE_IP"] = "127.0.0.2"
	env["VAULT_NACOS_INSTANCE_PORT"] = "18108"
	env["VAULT_NACOS_USERNAME"] = "synthetic-username"
	env["VAULT_NACOS_PASSWORD"] = "synthetic-password"
	loaded, err := conf.Load(env.Get)
	if err != nil {
		t.Fatal("Load() rejected valid synthetic configuration")
	}
	for _, verb := range []string{"%v", "%+v", "%#v"} {
		if fmt.Sprintf(verb, loaded) != "vault runtime configuration [REDACTED]" {
			t.Fatal("Config formatting redaction failed")
		}
		if fmt.Sprintf(verb, loaded.Nacos) != "Nacos configuration [REDACTED]" {
			t.Fatal("NacosConfig formatting redaction failed")
		}
	}
}

func TestNacosIdentifiersRejectCachePathComponents(t *testing.T) {
	for _, field := range []string{"VAULT_NACOS_NAMESPACE", "VAULT_NACOS_GROUP"} {
		for _, value := range []string{"../outside", "mor/local", `/tmp/mor`, `vault\group`, ".", "..", "mor@@local", "vault group", " leading", "trailing ", "line\nfeed", strings.Repeat("a", 65)} {
			t.Run(field+"/"+value, func(t *testing.T) {
				env := nacosEnvironment()
				env[field] = value
				_, err := conf.Load(env.Get)
				assertInternalError(t, err)
			})
		}
		for _, value := range []string{"mor-local", "DEFAULT_GROUP", "e525eafa-f7d7-4029-83d9-008937f9d468", "mor.local_1-2", strings.Repeat("a", 64)} {
			env := nacosEnvironment()
			env[field] = value
			if _, err := conf.Load(env.Get); err != nil {
				t.Fatal("valid Nacos identifier rejected")
			}
		}
	}
}

func nacosEnvironment() environment {
	env := validEnvironment("test")
	env["VAULT_NACOS_ENABLED"] = "true"
	env["VAULT_NACOS_SERVER_ADDRESS"] = "127.0.0.1:8848"
	env["VAULT_NACOS_NAMESPACE"] = "mor-local"
	env["VAULT_NACOS_INSTANCE_IP"] = "127.0.0.2"
	env["VAULT_NACOS_INSTANCE_PORT"] = "18108"
	env["VAULT_NACOS_USERNAME"] = "synthetic-username"
	env["VAULT_NACOS_PASSWORD"] = "synthetic-password"
	return env
}

func TestLocalAESRootKeyReturnsDefensiveCopy(t *testing.T) {
	loaded, err := conf.Load(validEnvironment("local").Get)
	if err != nil {
		t.Fatal("Load() rejected valid configuration")
	}
	first := loaded.LocalAESRootKey()
	first[0] = 1
	if loaded.LocalAESRootKey()[0] != 0 {
		t.Fatal("LocalAESRootKey() exposed mutable configuration state")
	}
}

type environment map[string]string

func (e environment) Get(name string) string { return e[name] }

func cloneEnvironment(source environment) environment {
	clone := make(environment, len(source))
	for name, value := range source {
		clone[name] = value
	}
	return clone
}

func validEnvironment(runtimeMode string) environment {
	return environment{
		"VAULT_RUNTIME_MODE":            runtimeMode,
		"VAULT_DB_DSN":                  "vault:password@tcp(127.0.0.1:3306)/vault",
		"VAULT_LOCAL_AES_ROOT_KEY":      base64.StdEncoding.EncodeToString(make([]byte, 32)),
		"VAULT_LOCAL_AES_KEY_ID":        "local-key-1",
		"VAULT_SNOWFLAKE_DATACENTER_ID": "0",
		"VAULT_SNOWFLAKE_WORKER_ID":     "31",
		"VAULT_NACOS_ENABLED":           "false",
	}
}

func assertInternalError(t *testing.T, err error) {
	t.Helper()
	if err == nil || biz.CodeOf(err) != biz.InternalError {
		t.Fatalf("error code = %s, want %s; error = %v", biz.CodeOf(err), biz.InternalError, err)
	}
}
