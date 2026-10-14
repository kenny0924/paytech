package http_test

import (
	"net/http"
	"strings"
	"testing"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/test/support/base"
	"github.com/paytech/vault-service/test/support/builder"
	"github.com/paytech/vault-service/test/support/checker"
	"github.com/paytech/vault-service/test/support/fixture"
)

// Protect 入口：成功、幂等重放与冲突、策略拒绝、请求校验。
//
// 被测入口：POST /vault/v1/records/protect → biz.ProtectUsecase.Process。

func TestProtect_referenceValueStoredAndAudited(t *testing.T) {
	env := base.Setup(t)
	request := builder.ProtectTemplate(env.ID).DataType(env.ID.ReferenceType).TokenMode("REFERENCE").Value("note-canary-001").Build()

	token := env.Invoke(t, request).Success().NotLeaking("note-canary-001").Token()

	record := checker.Records(t, env.Mapper).Stored(token, env.ID.ReferenceType, biz.Reference)
	checker.Records(t, env.Mapper).ProtectRequests(env.ID.Caller, record.RecordID)
	checker.AccessEvent(t, env.Mapper, request.RequestID()).
		Result(biz.ProtectAction, biz.Success, "SUCCESS").Caller(env.ID.Caller).
		DataType(env.ID.ReferenceType).Purpose(fixture.Purpose).Record(record.RecordID).PolicyVersion(env.ID.PolicyVersion())
	checker.Logs(t, env.Logs.String()).NotLeaking("note-canary-001", token)
}

func TestProtect_stableValueReusedAcrossCallers(t *testing.T) {
	env := base.Setup(t)
	first := builder.ProtectTemplate(env.ID).Value("alice@example.com").Build()
	second := builder.ProtectTemplate(env.ID).Caller(env.ID.SecondCaller).Value("alice@example.com").Build()

	token := env.Invoke(t, first).Success().Token()
	reused := env.Invoke(t, second).Success().Token()

	if reused != token {
		t.Fatal("同一 STABLE 值应返回同一 Token")
	}
	record := checker.Records(t, env.Mapper).Count(env.ID.StableType, 1).Stored(token, env.ID.StableType, biz.Stable)
	checker.Records(t, env.Mapper).ProtectRequests(env.ID.Caller, record.RecordID).ProtectRequests(env.ID.SecondCaller, record.RecordID)
	checker.AccessEvent(t, env.Mapper, second.RequestID()).Result(biz.ProtectAction, biz.Success, "SUCCESS").Caller(env.ID.SecondCaller).Record(record.RecordID)
}

func TestProtect_replaySameKeyReturnsOriginalToken(t *testing.T) {
	env := base.Setup(t)
	key := env.ID.NextIdempotencyKey()
	first := builder.ProtectTemplate(env.ID).IdempotencyKey(key).Build()
	replay := builder.ProtectTemplate(env.ID).IdempotencyKey(key).Build()

	token := env.Invoke(t, first).Success().Token()
	replayed := env.Invoke(t, replay).Success().Token()

	if replayed != token {
		t.Fatal("同一幂等键与同一请求应返回原 Token")
	}
	record := checker.Records(t, env.Mapper).Count(env.ID.StableType, 1).Stored(token, env.ID.StableType, biz.Stable)
	checker.Records(t, env.Mapper).ProtectRequests(env.ID.Caller, record.RecordID)
	checker.AccessEvent(t, env.Mapper, replay.RequestID()).Result(biz.ProtectAction, biz.Success, "SUCCESS").Record(record.RecordID)
}

func TestProtect_sameKeyDifferentValueConflicts(t *testing.T) {
	env := base.Setup(t)
	key := env.ID.NextIdempotencyKey()
	original := builder.ProtectTemplate(env.ID).IdempotencyKey(key).Build()
	changed := builder.ProtectTemplate(env.ID).IdempotencyKey(key).Value("mallory@example.com").Build()

	token := env.Invoke(t, original).Success().Token()
	env.Invoke(t, changed).Problem(http.StatusConflict, biz.IdempotencyKeyConflict).NotLeaking(token, "mallory@example.com")

	record := checker.Records(t, env.Mapper).Count(env.ID.StableType, 1).Stored(token, env.ID.StableType, biz.Stable)
	checker.Records(t, env.Mapper).ProtectRequests(env.ID.Caller, record.RecordID)
	checker.AccessEvent(t, env.Mapper, changed.RequestID()).
		Result(biz.ProtectAction, biz.Denied, string(biz.IdempotencyKeyConflict)).Record(record.RecordID)
}

func TestProtect_policyRejectionsAreAuditedWithoutStoring(t *testing.T) {
	cases := []struct {
		name     string
		change   func(*fixture.Identity, *builder.RequestBuilder)
		status   int
		code     biz.ErrorCode
		dataType func(*fixture.Identity) string
	}{
		{"未登记数据类型", func(id *fixture.Identity, b *builder.RequestBuilder) { b.DataType(id.UnregisteredType) }, http.StatusBadRequest, biz.UnsupportedDataType, func(id *fixture.Identity) string { return id.UnregisteredType }},
		{"Token 模式不符", func(_ *fixture.Identity, b *builder.RequestBuilder) { b.TokenMode("REFERENCE") }, http.StatusBadRequest, biz.TokenModeNotAllowed, stableType},
		{"调用方无授权", func(id *fixture.Identity, b *builder.RequestBuilder) { b.Caller(id.OtherCaller) }, http.StatusForbidden, biz.AccessDenied, stableType},
		{"用途无授权", func(_ *fixture.Identity, b *builder.RequestBuilder) { b.Purpose(fixture.OtherPurpose) }, http.StatusForbidden, biz.AccessDenied, stableType},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			env := base.Setup(t)
			b := builder.ProtectTemplate(env.ID)
			tc.change(env.ID, b)
			request := b.Build()

			env.Invoke(t, request).Problem(tc.status, tc.code).NotLeaking("alice@example.com")

			checker.Records(t, env.Mapper).Count(env.ID.StableType, 0).Count(env.ID.ReferenceType, 0).ProtectRequests(env.ID.Caller)
			checker.AccessEvent(t, env.Mapper, request.RequestID()).
				Result(biz.ProtectAction, biz.Denied, string(tc.code)).DataType(tc.dataType(env.ID)).Record(0).PolicyVersion(env.ID.PolicyVersion())
		})
	}
}

func TestProtect_invalidRequestsAreAuditedBeforePolicy(t *testing.T) {
	cases := []struct {
		name   string
		change func(*builder.RequestBuilder)
		code   biz.ErrorCode
		caller func(*fixture.Identity) string
	}{
		{"缺少调用方", func(b *builder.RequestBuilder) { b.WithoutHeader("X-Caller-Service") }, biz.InvalidRequest, func(*fixture.Identity) string { return biz.UnknownCaller }},
		{"缺少幂等键", func(b *builder.RequestBuilder) { b.WithoutHeader("Idempotency-Key") }, biz.InvalidRequest, caller},
		{"非 JSON 类型", func(b *builder.RequestBuilder) { b.Header("Content-Type", "text/plain") }, biz.InvalidRequest, caller},
		{"重复字段", func(b *builder.RequestBuilder) {
			b.RawBody(`{"dataType":"X","tokenMode":"STABLE","value":"a","value":"b","purpose":"CHECKOUT_CONTACT"}`)
		}, biz.InvalidRequest, caller},
		{"缺少值", func(b *builder.RequestBuilder) { b.Omit("value") }, biz.InvalidRequest, caller},
		{"值为 null", func(b *builder.RequestBuilder) { b.Null("value") }, biz.InvalidRequest, caller},
		{"值超过 32KiB", func(b *builder.RequestBuilder) { b.Value(strings.Repeat("a", 32*1024+1)) }, biz.ValueTooLarge, caller},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			env := base.Setup(t)
			b := builder.ProtectTemplate(env.ID)
			tc.change(b)
			request := b.Build()

			env.Invoke(t, request).Problem(http.StatusBadRequest, tc.code)

			checker.Records(t, env.Mapper).Count(env.ID.StableType, 0).ProtectRequests(env.ID.Caller)
			checker.AccessEvent(t, env.Mapper, request.RequestID()).
				Result(biz.ProtectAction, biz.Denied, string(tc.code)).Caller(tc.caller(env.ID)).Record(0).PolicyVersion("")
		})
	}
}

func TestProtect_rejectsNonPostWithoutAudit(t *testing.T) {
	env := base.Setup(t)
	request := builder.Get(env.ID, "/vault/v1/records/protect").Build()

	env.Invoke(t, request).Problem(http.StatusMethodNotAllowed, "METHOD_NOT_ALLOWED").Allow(http.MethodPost)

	checker.NoAccessEvent(t, env.Mapper, request.RequestID())
}

func stableType(id *fixture.Identity) string { return id.StableType }

func caller(id *fixture.Identity) string { return id.Caller }
