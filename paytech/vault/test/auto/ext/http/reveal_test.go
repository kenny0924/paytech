package http_test

import (
	"net/http"
	"testing"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/test/support/base"
	"github.com/paytech/vault-service/test/support/builder"
	"github.com/paytech/vault-service/test/support/checker"
	"github.com/paytech/vault-service/test/support/fixture"
	"github.com/paytech/vault-service/test/support/mocker/local"
)

// Reveal 入口：读取原值、Token 不存在、授权拒绝、请求校验。
//
// 被测入口：POST /vault/v1/records/reveal → biz.RevealUsecase.Process。

// revealCase 是本文件用例共用的准备（对应测试类级 @BeforeEach）。
type revealCase struct {
	env     *base.Environment
	records *local.RecordMocker
}

func setUpReveal(t *testing.T) revealCase {
	env := base.Setup(t)
	return revealCase{env: env, records: local.NewRecordMocker(t, env)}
}

func TestReveal_returnsProtectedValueAndAuditsFirst(t *testing.T) {
	for _, mode := range []biz.TokenMode{biz.Reference, biz.Stable} {
		t.Run(string(mode), func(t *testing.T) {
			c := setUpReveal(t)
			dataType := c.env.ID.ReferenceType
			if mode == biz.Stable {
				dataType = c.env.ID.StableType
			}
			value := "合成备注<&>\n😀-canary"
			token, recordID := c.records.MockRecord(t, dataType, mode, value)
			request := builder.RevealTemplate(c.env.ID, token).WithoutHeader("Idempotency-Key").Build()

			c.env.Invoke(t, request).Success().RevealedValue(value)

			checker.AccessEvent(t, c.env.Mapper, request.RequestID()).
				Result(biz.RevealAction, biz.Success, "SUCCESS").Caller(c.env.ID.Caller).
				DataType(dataType).Purpose(fixture.Purpose).Record(recordID).PolicyVersion(checker.AnyPolicyVersion)
			checker.Logs(t, c.env.Logs.String()).NotLeaking(value, token)
		})
	}
}

func TestReveal_unknownTokenIsNotFound(t *testing.T) {
	c := setUpReveal(t)
	request := builder.RevealTemplate(c.env.ID, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA").Build()

	c.env.Invoke(t, request).Problem(http.StatusNotFound, biz.TokenNotFound)

	checker.AccessEvent(t, c.env.Mapper, request.RequestID()).
		Result(biz.RevealAction, biz.Denied, string(biz.TokenNotFound)).DataType("").Purpose(fixture.Purpose).Record(0).PolicyVersion("")
}

func TestReveal_unauthorizedAccessIsDeniedWithoutValue(t *testing.T) {
	cases := []struct {
		name   string
		change func(*fixture.Identity, *builder.RequestBuilder)
	}{
		{"调用方无授权", func(id *fixture.Identity, b *builder.RequestBuilder) { b.Caller(id.OtherCaller) }},
		{"仅有 Protect 授权", func(id *fixture.Identity, b *builder.RequestBuilder) { b.Caller(id.SecondCaller) }},
		{"用途无授权", func(_ *fixture.Identity, b *builder.RequestBuilder) { b.Purpose(fixture.OtherPurpose) }},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			c := setUpReveal(t)
			token, recordID := c.records.MockRecord(t, c.env.ID.StableType, biz.Stable, "bob@example.com")
			b := builder.RevealTemplate(c.env.ID, token)
			tc.change(c.env.ID, b)
			request := b.Build()

			c.env.Invoke(t, request).Problem(http.StatusForbidden, biz.AccessDenied).NotLeaking("bob@example.com")

			checker.AccessEvent(t, c.env.Mapper, request.RequestID()).
				Result(biz.RevealAction, biz.Denied, string(biz.AccessDenied)).
				DataType(c.env.ID.StableType).Record(recordID).PolicyVersion(checker.AnyPolicyVersion)
			checker.Logs(t, c.env.Logs.String()).NotLeaking("bob@example.com")
		})
	}
}

func TestReveal_invalidTokenFormatIsRejected(t *testing.T) {
	c := setUpReveal(t)
	request := builder.RevealTemplate(c.env.ID, "not-a-token").Build()

	c.env.Invoke(t, request).Problem(http.StatusBadRequest, biz.InvalidRequest)

	checker.AccessEvent(t, c.env.Mapper, request.RequestID()).
		Result(biz.RevealAction, biz.Denied, string(biz.InvalidRequest)).Purpose(fixture.Purpose).Record(0)
}
