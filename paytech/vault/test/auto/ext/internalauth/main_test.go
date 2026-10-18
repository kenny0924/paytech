package internalauth_test

import (
	"fmt"
	"os"
	"testing"

	"github.com/paytech/vault-service/internal/pkg/internalauth"
	"github.com/paytech/vault-service/test/support/base"
	"github.com/paytech/vault-service/test/support/builder"
	"github.com/paytech/vault-service/test/support/mocker/external"
)

// 内部调用 Token 三种模式（D26）的 HTTP 入口测试：每种模式一套按正式装配启动的 Vault，共用本机 mor_vault。
//
// Snowflake 坐标 31/22～25 专属本包（见 base.mockConfig）。JWKS 强制刷新每 10 秒最多一次，
// 因此每个环境中只有一个用例触发 kid 未命中（DUAL：密钥轮换；REQUIRED：未发布公钥），本包不支持 -count>1 连续运行。

var (
	// platform 是 DUAL / REQUIRED 环境读取的 JWKS；offPlatform 只配置给 OFF 环境，必须从未被读取。
	platform, offPlatform *external.PlatformJWKS

	offEnv, dualEnv, requiredEnv, unavailableEnv *base.Environment
)

// unreachableJWKS 是不监听的回环端口：REQUIRED 下公钥不可用必须失败关闭。
const unreachableJWKS = "http://127.0.0.1:1/platform/v1/internal-auth/jwks"

func TestMain(m *testing.M) { os.Exit(run(m)) }

func run(m *testing.M) int {
	var err error
	if platform, err = external.NewPlatformJWKS(); err != nil {
		return fail(err)
	}
	defer platform.Close()
	if offPlatform, err = external.NewPlatformJWKS(); err != nil {
		return fail(err)
	}
	defer offPlatform.Close()
	environments := []struct {
		target **base.Environment
		mode   internalauth.Mode
		jwks   string
		worker string
	}{
		{&offEnv, internalauth.Off, offPlatform.URL(), "25"},
		{&dualEnv, internalauth.Dual, platform.URL(), "24"},
		{&requiredEnv, internalauth.Required, platform.URL(), "23"},
		{&unavailableEnv, internalauth.Required, unreachableJWKS, "22"},
	}
	for _, e := range environments {
		if *e.target, err = base.Start(base.Options{Overrides: tokenConfig(e.mode, e.jwks, e.worker)}); err != nil {
			stopAll(offEnv, dualEnv, requiredEnv, unavailableEnv)
			return fail(err)
		}
	}
	code := m.Run()
	if !stopAll(offEnv, dualEnv, requiredEnv, unavailableEnv) && code == 0 {
		code = 1
	}
	return code
}

func tokenConfig(mode internalauth.Mode, jwks, worker string) map[string]string {
	return map[string]string{
		"VAULT_INTERNAL_AUTH_MODE":     string(mode),
		"VAULT_INTERNAL_AUTH_JWKS_URL": jwks,
		"VAULT_INSTANCE_ID":            builder.TestInstanceID,
		"VAULT_SNOWFLAKE_WORKER_ID":    worker,
	}
}

func stopAll(environments ...*base.Environment) bool {
	ok := true
	for _, env := range environments {
		if env == nil {
			continue
		}
		if err := env.Stop(); err != nil {
			fmt.Fprintln(os.Stderr, "Vault 自动测试环境关闭失败："+err.Error())
			ok = false
		}
	}
	return ok
}

func fail(err error) int {
	fmt.Fprintln(os.Stderr, "Vault 内部 Token 测试环境启动失败："+err.Error()+
		"。设置 VAULT_TEST_DB_DSN 指向仓库 local-environment 的 mor_vault；见 paytech/vault/README.md")
	return 1
}

// setUp 是本包的类级准备：全局 Setup 之后登记 JWKS 状态复原，并在结束时检查 OFF 环境从未读取 JWKS。
func setUp(t *testing.T, env *base.Environment) *base.Environment {
	t.Helper()
	env = env.Setup(t)
	t.Cleanup(func() {
		if fetches := offPlatform.Fetches(); fetches != 0 {
			t.Errorf("OFF 环境不应读取 JWKS，实际读取 %d 次", fetches)
		}
		platform.Reset()
	})
	return env
}

// token 返回以 caller 为 sub 的合法 Token。
func token(caller string) string { return platform.Sign(builder.InternalTokenTemplate(caller)) }
