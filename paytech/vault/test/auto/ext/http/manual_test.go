package http_test

import (
	"net/http"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	v1 "github.com/paytech/vault-service/api/vault/v1"
	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/test/support/base"
	"github.com/paytech/vault-service/test/support/builder"
	"github.com/paytech/vault-service/test/support/checker"
	"github.com/paytech/vault-service/test/support/mocker/local"
)

// 人工加/解密入口：数据类型开关、数据级别、操作人审计与幂等重放。
//
// 被测入口：POST /vault/v1/admin/manual/protect|reveal → biz.ProtectUsecase.ProcessManual、biz.RevealUsecase.Process（人工分支）。

var bothClasses = []string{"STANDARD", "SECRET"}

func TestManual_protectThenRevealStandardWithOperatorAudit(t *testing.T) {
	env := base.Setup(t)
	value := "+971500000001"
	key := env.ID.NextIdempotencyKey()
	protect := builder.ManualProtect(env.ID, env.ID.ManualType, value).IdempotencyKey(key).Build()

	token := env.Invoke(t, protect).Success().Token()
	replayed := env.Invoke(t, builder.ManualProtect(env.ID, env.ID.ManualType, value).IdempotencyKey(key).Build()).Success().Token()
	assert.Equal(t, token, replayed)
	checker.AccessEvent(t, env.Mapper, protect.RequestID()).
		Result(biz.ProtectAction, biz.Success, "SUCCESS").Caller(env.ID.AdminCaller).DataType(env.ID.ManualType).
		Purpose(biz.OperationsManualPurpose).Manual(env.ID.Operator, "DATA_REPAIR", "OPS-1001")

	reveal := builder.ManualReveal(env.ID, token).Build()
	var revealed v1.ManualRevealResponseBody
	env.Invoke(t, reveal).JSON(&revealed)
	assert.Equal(t, v1.ManualRevealResponseBody{DataTypeCode: env.ID.ManualType, Value: value}, revealed)
	checker.AccessEvent(t, env.Mapper, reveal.RequestID()).
		Result(biz.RevealAction, biz.Success, "SUCCESS").DataType(env.ID.ManualType).
		Purpose(biz.OperationsManualPurpose).Manual(env.ID.Operator, "CUSTOMER_VERIFICATION", "OPS-1002")

	var events v1.ManualEventPageResponseBody
	env.Invoke(t, builder.Admin(env.ID, http.MethodGet, "/manual-events?actorId="+env.ID.Operator, nil).Build()).NotLeaking(value, token).JSON(&events)
	require.GreaterOrEqual(t, len(events.Items), 2)
	assert.Equal(t, reveal.RequestID(), events.Items[0].RequestID)
	assert.Equal(t, "OPS-1002", events.Items[0].TicketRef)
	checker.Logs(t, env.Logs.String()).NotLeaking(value, token)
}

func TestManual_secretClassRequiresSecretRoute(t *testing.T) {
	env := base.Setup(t)
	value := "sk_test_synthetic"
	env.Invoke(t, builder.ManualProtect(env.ID, env.ID.SecretType, value).Build()).Problem(http.StatusForbidden, biz.AccessDenied).NotLeaking(value)

	token := env.Invoke(t, builder.ManualProtect(env.ID, env.ID.SecretType, value).Field("allowedDataClasses", bothClasses).Build()).Success().Token()

	env.Invoke(t, builder.ManualReveal(env.ID, token).Build()).Problem(http.StatusForbidden, biz.AccessDenied).NotLeaking(value)
	var revealed v1.ManualRevealResponseBody
	env.Invoke(t, builder.ManualReveal(env.ID, token).Field("allowedDataClasses", bothClasses).Build()).JSON(&revealed)
	assert.Equal(t, value, revealed.Value)
}

func TestManual_switchOffTypeIsDenied(t *testing.T) {
	env := base.Setup(t)
	records := local.NewRecordMocker(t, env)
	token, recordID := records.MockRecord(t, env.ID.StableType, biz.Stable, "carol@example.com")

	protect := builder.ManualProtect(env.ID, env.ID.StableType, "carol@example.com").Build()
	env.Invoke(t, protect).Problem(http.StatusForbidden, biz.AccessDenied)
	checker.AccessEvent(t, env.Mapper, protect.RequestID()).Result(biz.ProtectAction, biz.Denied, string(biz.AccessDenied)).
		Manual(env.ID.Operator, "DATA_REPAIR", "OPS-1001")

	reveal := builder.ManualReveal(env.ID, token).Build()
	env.Invoke(t, reveal).Problem(http.StatusForbidden, biz.AccessDenied).NotLeaking("carol@example.com")
	checker.AccessEvent(t, env.Mapper, reveal.RequestID()).Result(biz.RevealAction, biz.Denied, string(biz.AccessDenied)).
		Record(recordID).Manual(env.ID.Operator, "CUSTOMER_VERIFICATION", "OPS-1002")
}

func TestManual_invalidRequestIsRejected(t *testing.T) {
	env := base.Setup(t)
	cases := []struct {
		name   string
		req    *builder.RequestBuilder
		status int
		code   biz.ErrorCode
	}{
		{"非管理调用方", builder.ManualProtect(env.ID, env.ID.ManualType, "v").Caller(env.ID.Caller), http.StatusForbidden, biz.AccessDenied},
		{"缺少操作人", builder.ManualProtect(env.ID, env.ID.ManualType, "v").WithoutHeader("X-Operator-Id"), http.StatusBadRequest, biz.InvalidRequest},
		{"缺少工单号", builder.ManualProtect(env.ID, env.ID.ManualType, "v").Omit("ticketRef"), http.StatusBadRequest, biz.InvalidRequest},
		{"工单号含非法字符", builder.ManualProtect(env.ID, env.ID.ManualType, "v").Field("ticketRef", "OPS 1"), http.StatusBadRequest, biz.InvalidRequest},
		{"未知理由码", builder.ManualProtect(env.ID, env.ID.ManualType, "v").Field("reasonCode", "CURIOSITY"), http.StatusBadRequest, biz.InvalidRequest},
		{"未知数据级别", builder.ManualProtect(env.ID, env.ID.ManualType, "v").Field("allowedDataClasses", []string{"TOP"}), http.StatusBadRequest, biz.InvalidRequest},
		{"缺少幂等键", builder.ManualProtect(env.ID, env.ID.ManualType, "v").WithoutHeader("Idempotency-Key"), http.StatusBadRequest, biz.InvalidRequest},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			env.Invoke(t, tc.req.Build()).Problem(tc.status, tc.code)
		})
	}
}
