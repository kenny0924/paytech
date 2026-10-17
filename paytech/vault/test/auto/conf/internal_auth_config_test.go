package conf_test

import (
	"testing"

	"github.com/paytech/vault-service/internal/conf"
	"github.com/paytech/vault-service/internal/pkg/internalauth"
)

// 内部 Token 校验配置（D26）：VAULT_INTERNAL_AUTH_MODE 默认 OFF；DUAL / REQUIRED 要求 JWKS 地址与实例 ID。
//
// 被测入口：conf.Load。

func TestLoadDefaultsInternalAuthToOffWithoutReadingItsOtherKeys(t *testing.T) {
	env := validEnvironment("test")
	// OFF 下不读取、不校验 JWKS 地址与实例 ID；非法值也不影响启动。
	env["VAULT_INTERNAL_AUTH_JWKS_URL"] = "not a url"
	env["VAULT_INSTANCE_ID"] = "NOT VALID"

	got, err := conf.Load(env.Get)

	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}
	if got.InternalAuth != (conf.InternalAuthConfig{Mode: internalauth.Off}) {
		t.Fatalf("InternalAuth = %#v, want OFF only", got.InternalAuth)
	}
}

func TestLoadAcceptsTokenModesWithJWKSAndInstance(t *testing.T) {
	for _, mode := range []internalauth.Mode{internalauth.Off, internalauth.Dual, internalauth.Required} {
		t.Run(string(mode), func(t *testing.T) {
			env := validEnvironment("test")
			env["VAULT_INTERNAL_AUTH_MODE"] = string(mode)
			env["VAULT_INTERNAL_AUTH_JWKS_URL"] = "http://platform-support-service:18106/platform/v1/internal-auth/jwks"
			env["VAULT_INSTANCE_ID"] = "main"

			got, err := conf.Load(env.Get)

			if err != nil {
				t.Fatalf("Load() error = %v", err)
			}
			if got.InternalAuth.Mode != mode {
				t.Fatalf("Mode = %s, want %s", got.InternalAuth.Mode, mode)
			}
			if mode != internalauth.Off && (got.InternalAuth.JWKSURL != env["VAULT_INTERNAL_AUTH_JWKS_URL"] || got.InternalAuth.InstanceID != "main") {
				t.Fatalf("InternalAuth = %#v", got.InternalAuth)
			}
		})
	}
}

func TestLoadRejectsInvalidInternalAuthConfiguration(t *testing.T) {
	cases := []struct {
		name   string
		mutate func(environment)
	}{
		{"unknown mode", func(env environment) { env["VAULT_INTERNAL_AUTH_MODE"] = "ON" }},
		{"lowercase mode", func(env environment) { env["VAULT_INTERNAL_AUTH_MODE"] = "required" }},
		{"missing JWKS URL", func(env environment) { delete(env, "VAULT_INTERNAL_AUTH_JWKS_URL") }},
		{"relative JWKS URL", func(env environment) { env["VAULT_INTERNAL_AUTH_JWKS_URL"] = "/jwks" }},
		{"non-HTTP JWKS URL", func(env environment) { env["VAULT_INTERNAL_AUTH_JWKS_URL"] = "file:///etc/jwks.json" }},
		{"JWKS URL with user info", func(env environment) { env["VAULT_INTERNAL_AUTH_JWKS_URL"] = "http://user:pass@platform/jwks" }},
		{"JWKS URL with fragment", func(env environment) { env["VAULT_INTERNAL_AUTH_JWKS_URL"] = "http://platform/jwks#keys" }},
		{"missing instance ID", func(env environment) { delete(env, "VAULT_INSTANCE_ID") }},
		{"invalid instance ID", func(env environment) { env["VAULT_INSTANCE_ID"] = "Main_1" }},
	}
	for _, mode := range []internalauth.Mode{internalauth.Dual, internalauth.Required} {
		for _, tc := range cases {
			t.Run(string(mode)+"/"+tc.name, func(t *testing.T) {
				env := validEnvironment("test")
				env["VAULT_INTERNAL_AUTH_MODE"] = string(mode)
				env["VAULT_INTERNAL_AUTH_JWKS_URL"] = "https://platform.internal/jwks"
				env["VAULT_INSTANCE_ID"] = "main"
				tc.mutate(env)

				_, err := conf.Load(env.Get)

				assertInternalError(t, err)
			})
		}
	}
}
