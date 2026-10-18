package internalauth_test

import (
	"net/http"
	"strings"
	"testing"
	"time"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/test/support/builder"
	"github.com/paytech/vault-service/test/support/checker"
)

// REQUIRED：只认合法 Token；缺失或不合法 → 401 INTERNAL_TOKEN_INVALID，公钥不可用 → 503；X-Caller-Service 被忽略。
// 访问规则的 Caller 维度不变：Token 的 sub 仍须有对应授权。
//
// 被测入口：POST /vault/v1/records/protect、POST /vault/v1/records/reveal、GET /vault/v1/admin/data-types
// （VAULT_INTERNAL_AUTH_MODE=REQUIRED）。

func TestRequired_validTokenAuthorizesProtectAndRevealWithoutCallerHeader(t *testing.T) {
	env := setUp(t, requiredEnv)
	protect := builder.ProtectTemplate(env.ID).WithoutHeader("X-Caller-Service").Bearer(token(env.ID.Caller)).Build()
	issued := env.Invoke(t, protect).Success().Token()
	reveal := builder.RevealTemplate(env.ID, issued).Caller(env.ID.OtherCaller).Bearer(token(env.ID.Caller)).Build()

	env.Invoke(t, reveal).Success().RevealedValue("alice@example.com")

	checker.AccessEvent(t, env.Mapper, protect.RequestID()).Result(biz.ProtectAction, biz.Success, "SUCCESS").Caller(env.ID.Caller)
	checker.AccessEvent(t, env.Mapper, reveal.RequestID()).Result(biz.RevealAction, biz.Success, "SUCCESS").Caller(env.ID.Caller)
}

func TestRequired_tokenSubjectWithoutAccessRuleIsDenied(t *testing.T) {
	env := setUp(t, requiredEnv)
	request := builder.ProtectTemplate(env.ID).Bearer(token(env.ID.OtherCaller)).Build()

	env.Invoke(t, request).Problem(http.StatusForbidden, biz.AccessDenied)

	checker.AccessEvent(t, env.Mapper, request.RequestID()).Result(biz.ProtectAction, biz.Denied, string(biz.AccessDenied)).
		Caller(env.ID.OtherCaller)
}

func TestRequired_missingOrInvalidTokenIsUnauthorizedAndAudited(t *testing.T) {
	env := setUp(t, requiredEnv)
	caller := env.ID.Caller
	cases := map[string]func(*builder.RequestBuilder) *builder.RequestBuilder{
		"header only": func(b *builder.RequestBuilder) *builder.RequestBuilder { return b },
		"basic scheme": func(b *builder.RequestBuilder) *builder.RequestBuilder {
			return b.Header("Authorization", "Basic "+token(caller))
		},
		"empty bearer": func(b *builder.RequestBuilder) *builder.RequestBuilder { return b.Header("Authorization", "Bearer ") },
		"not a JWS":    func(b *builder.RequestBuilder) *builder.RequestBuilder { return b.Bearer("not-a-jws") },
		"expired": func(b *builder.RequestBuilder) *builder.RequestBuilder {
			return b.Bearer(platform.Sign(builder.InternalTokenTemplate(caller).IssuedAt(-10*time.Minute, 5*time.Minute)))
		},
		"other audience": func(b *builder.RequestBuilder) *builder.RequestBuilder {
			return b.Bearer(platform.Sign(builder.InternalTokenTemplate(caller).Claim("aud", "payment-service")))
		},
		"other instance": func(b *builder.RequestBuilder) *builder.RequestBuilder {
			return b.Bearer(platform.Sign(builder.InternalTokenTemplate(caller).Claim("inst", "other-instance")))
		},
		"other issuer": func(b *builder.RequestBuilder) *builder.RequestBuilder {
			return b.Bearer(platform.Sign(builder.InternalTokenTemplate(caller).Claim("iss", caller)))
		},
		// 本环境唯一触发 kid 未命中的用例。
		"unpublished key": func(b *builder.RequestBuilder) *builder.RequestBuilder {
			return b.Bearer(platform.SignUnpublished(builder.InternalTokenTemplate(caller)))
		},
	}
	for name, modify := range cases {
		t.Run(name, func(t *testing.T) {
			request := modify(builder.ProtectTemplate(env.ID)).Build()

			env.Invoke(t, request).Problem(http.StatusUnauthorized, biz.InternalTokenInvalid).NotLeaking(caller)

			checker.AccessEvent(t, env.Mapper, request.RequestID()).
				Result(biz.ProtectAction, biz.Denied, string(biz.InternalTokenInvalid)).Caller(biz.UnknownCaller)
		})
	}
}

func TestRequired_duplicateAuthorizationHeadersAreUnauthorized(t *testing.T) {
	env := setUp(t, requiredEnv)
	request := builder.RevealTemplate(env.ID, strings.Repeat("A", 43)).Bearer(token(env.ID.Caller)).Build()
	request.Header.Add("Authorization", "Bearer "+token(env.ID.Caller))

	env.Invoke(t, request).Problem(http.StatusUnauthorized, biz.InternalTokenInvalid)

	checker.AccessEvent(t, env.Mapper, request.RequestID()).
		Result(biz.RevealAction, biz.Denied, string(biz.InternalTokenInvalid)).Caller(biz.UnknownCaller)
}

func TestRequired_adminRoutesUseTokenSubjectAsAdminCaller(t *testing.T) {
	env := setUp(t, requiredEnv)

	env.Invoke(t, builder.Admin(env.ID, http.MethodGet, "/data-types", nil).WithoutHeader("X-Caller-Service").
		Bearer(token(env.ID.AdminCaller)).Build()).Success()
	env.Invoke(t, builder.Admin(env.ID, http.MethodGet, "/data-types", nil).Build()).
		Problem(http.StatusUnauthorized, biz.InternalTokenInvalid)
	env.Invoke(t, builder.Admin(env.ID, http.MethodGet, "/data-types", nil).Bearer(token(env.ID.Caller)).Build()).
		Problem(http.StatusForbidden, biz.AccessDenied)
}

func TestRequired_unavailableJWKSFailsClosed(t *testing.T) {
	env := setUp(t, unavailableEnv)
	request := builder.ProtectTemplate(env.ID).Bearer(token(env.ID.Caller)).Build()

	env.Invoke(t, request).Problem(http.StatusServiceUnavailable, biz.DependencyUnavailable)

	checker.AccessEvent(t, env.Mapper, request.RequestID()).
		Result(biz.ProtectAction, biz.Failed, string(biz.DependencyUnavailable)).Caller(biz.UnknownCaller)
	checker.Records(t, env.Mapper).Count(env.ID.StableType, 0)
}

func TestRequired_healthEndpointsNeedNoToken(t *testing.T) {
	env := setUp(t, unavailableEnv)
	for _, path := range []string{"/health/liveness", "/health/readiness"} {
		env.Invoke(t, builder.Get(env.ID, path).WithoutHeader("X-Caller-Service").Build()).Success()
	}
}
