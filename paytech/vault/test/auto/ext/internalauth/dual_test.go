package internalauth_test

import (
	"net/http"
	"testing"
	"time"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/test/support/builder"
	"github.com/paytech/vault-service/test/support/checker"
)

// DUAL（迁移期）：合法 Token 的 sub 优先；无 Token 或 Token 不合法时回退 X-Caller-Service。
//
// 被测入口：POST /vault/v1/records/protect（VAULT_INTERNAL_AUTH_MODE=DUAL）。

func TestDual_validTokenWinsOverCallerHeader(t *testing.T) {
	env := setUp(t, dualEnv)
	request := builder.ProtectTemplate(env.ID).Caller(env.ID.OtherCaller).Bearer(token(env.ID.Caller)).Build()

	env.Invoke(t, request).Success()

	checker.AccessEvent(t, env.Mapper, request.RequestID()).Result(biz.ProtectAction, biz.Success, "SUCCESS").Caller(env.ID.Caller)
}

func TestDual_missingTokenFallsBackToCallerHeader(t *testing.T) {
	env := setUp(t, dualEnv)
	request := builder.ProtectTemplate(env.ID).Build()

	env.Invoke(t, request).Success()

	checker.AccessEvent(t, env.Mapper, request.RequestID()).Result(biz.ProtectAction, biz.Success, "SUCCESS").Caller(env.ID.Caller)
}

func TestDual_invalidTokenFallsBackToCallerHeader(t *testing.T) {
	env := setUp(t, dualEnv)
	expired := platform.Sign(builder.InternalTokenTemplate(env.ID.Caller).IssuedAt(-10*time.Minute, 5*time.Minute))
	request := builder.ProtectTemplate(env.ID).Caller(env.ID.OtherCaller).Bearer(expired).Build()

	// 过期 Token 的 sub 不被采用：调用方回退为 OtherCaller，按其（无）授权拒绝。
	env.Invoke(t, request).Problem(http.StatusForbidden, biz.AccessDenied)

	checker.AccessEvent(t, env.Mapper, request.RequestID()).Result(biz.ProtectAction, biz.Denied, string(biz.AccessDenied)).
		Caller(env.ID.OtherCaller)
}

func TestDual_neitherTokenNorHeaderIsInvalidRequest(t *testing.T) {
	env := setUp(t, dualEnv)
	request := builder.ProtectTemplate(env.ID).WithoutHeader("X-Caller-Service").Build()

	env.Invoke(t, request).Problem(http.StatusBadRequest, biz.InvalidRequest)

	checker.AccessEvent(t, env.Mapper, request.RequestID()).Result(biz.ProtectAction, biz.Denied, string(biz.InvalidRequest)).
		Caller(biz.UnknownCaller)
}

// 本环境唯一触发 kid 未命中的用例：Platform 轮换密钥后，Vault 强制刷新 JWKS 并接受新 kid。
func TestDual_rotatedSigningKeyIsAcceptedAfterForcedRefresh(t *testing.T) {
	env := setUp(t, dualEnv)
	env.Invoke(t, builder.ProtectTemplate(env.ID).Bearer(token(env.ID.Caller)).Build()).Success()
	if _, err := platform.Rotate(); err != nil {
		t.Fatal(err)
	}
	request := builder.ProtectTemplate(env.ID).Caller(env.ID.OtherCaller).Bearer(token(env.ID.Caller)).Build()

	env.Invoke(t, request).Success()

	checker.AccessEvent(t, env.Mapper, request.RequestID()).Result(biz.ProtectAction, biz.Success, "SUCCESS").Caller(env.ID.Caller)
}
