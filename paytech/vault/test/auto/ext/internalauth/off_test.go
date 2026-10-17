package internalauth_test

import (
	"net/http"
	"testing"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/test/support/builder"
	"github.com/paytech/vault-service/test/support/checker"
)

// OFF（默认）：Authorization 被忽略，调用方只取 X-Caller-Service，行为与引入 Token 前一致。
//
// 被测入口：POST /vault/v1/records/protect（VAULT_INTERNAL_AUTH_MODE=OFF）。

func TestOff_tokenIsIgnoredAndHeaderDecidesCaller(t *testing.T) {
	env := setUp(t, offEnv)
	request := builder.ProtectTemplate(env.ID).Caller(env.ID.OtherCaller).Bearer(token(env.ID.Caller)).Build()

	env.Invoke(t, request).Problem(http.StatusForbidden, biz.AccessDenied)

	checker.AccessEvent(t, env.Mapper, request.RequestID()).Result(biz.ProtectAction, biz.Denied, string(biz.AccessDenied)).
		Caller(env.ID.OtherCaller)
}

func TestOff_tokenWithoutCallerHeaderIsInvalidRequest(t *testing.T) {
	env := setUp(t, offEnv)
	request := builder.ProtectTemplate(env.ID).WithoutHeader("X-Caller-Service").Bearer(token(env.ID.Caller)).Build()

	env.Invoke(t, request).Problem(http.StatusBadRequest, biz.InvalidRequest)

	checker.AccessEvent(t, env.Mapper, request.RequestID()).Result(biz.ProtectAction, biz.Denied, string(biz.InvalidRequest)).
		Caller(biz.UnknownCaller)
}

func TestOff_malformedAuthorizationDoesNotAffectHeaderCaller(t *testing.T) {
	env := setUp(t, offEnv)
	request := builder.ProtectTemplate(env.ID).Header("Authorization", "Bearer not-a-jws").Build()

	env.Invoke(t, request).Success()

	checker.AccessEvent(t, env.Mapper, request.RequestID()).Result(biz.ProtectAction, biz.Success, "SUCCESS").Caller(env.ID.Caller)
}
