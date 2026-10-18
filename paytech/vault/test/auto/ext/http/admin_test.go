package http_test

import (
	"net/http"
	"strconv"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	v1 "github.com/paytech/vault-service/api/vault/v1"
	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/test/support/base"
	"github.com/paytech/vault-service/test/support/builder"
	"github.com/paytech/vault-service/test/support/fixture"
)

// 策略维护入口：数据类型与访问规则的新建、修改、版本冲突，以及热加载生效。
//
// 被测入口：/vault/v1/admin/data-types、/access-rules、/policy-changes → biz.PolicyAdminUsecase；生效经 pkg/policy.Refresher。

func TestAdmin_newDataTypeAndRuleTakeEffectThenDisable(t *testing.T) {
	env := base.Setup(t)
	code := env.ID.TypePrefix() + "NEW_NOTE"

	var created v1.DataTypeResponseBody
	env.Invoke(t, builder.Admin(env.ID, http.MethodPost, "/data-types", map[string]any{"dataTypeCode": code, "tokenMode": "REFERENCE", "dataClass": "STANDARD",
		"description": "新增备注", "manualProtectEnabled": false, "manualRevealEnabled": false, "reason": "接入新字段"}).Build()).JSON(&created)
	assert.Equal(t, code, created.DataType.DataTypeCode)
	assert.Equal(t, 1, created.DataType.Version)

	var rule v1.AccessRuleResponseBody
	env.Invoke(t, builder.Admin(env.ID, http.MethodPost, "/access-rules", map[string]any{"callerService": env.ID.Caller, "action": "PROTECT", "dataTypeCode": code,
		"purposeCode": fixture.Purpose, "reason": "开通服务加密"}).Build()).JSON(&rule)
	assert.Greater(t, rule.PolicyVersion, created.PolicyVersion)

	protect := func() *builder.RequestBuilder {
		return builder.ProtectTemplate(env.ID).DataType(code).TokenMode("REFERENCE").Value("note-after-admin")
	}
	eventually(t, func() bool { return env.Invoke(t, protect().Build()).Status == http.StatusOK })

	var disabled v1.DataTypeResponseBody
	env.Invoke(t, builder.Admin(env.ID, http.MethodPut, "/data-types/"+code, map[string]any{"expectedVersion": 1, "dataClass": "STANDARD", "description": "新增备注",
		"manualProtectEnabled": false, "manualRevealEnabled": false, "status": "DISABLED", "reason": "下线"}).Build()).JSON(&disabled)
	assert.Equal(t, "DISABLED", disabled.DataType.Status)
	assert.Equal(t, 2, disabled.DataType.Version)
	eventually(t, func() bool { return env.Invoke(t, protect().Build()).Status == http.StatusBadRequest })
	env.Invoke(t, protect().Build()).Problem(http.StatusBadRequest, biz.UnsupportedDataType)

	var changes v1.PolicyChangePageResponseBody
	env.Invoke(t, builder.Admin(env.ID, http.MethodGet, "/policy-changes?targetType=DATA_TYPE&targetKey="+code, nil).Build()).JSON(&changes)
	require.Len(t, changes.Items, 2)
	assert.Equal(t, "DATA_TYPE_UPDATE", changes.Items[0].ChangeType)
	assert.Equal(t, env.ID.Operator, changes.Items[0].ActorID)
	assert.Equal(t, "下线", changes.Items[0].Reason)

	var detail v1.PolicyChangeBody
	env.Invoke(t, builder.Admin(env.ID, http.MethodGet, "/policy-changes/"+strconv.Itoa(changes.Items[0].PolicyVersionNo), nil).Build()).JSON(&detail)
	assert.JSONEq(t, `{"dataTypeCode":"`+code+`","tokenMode":"REFERENCE","dataClass":"STANDARD","description":"新增备注","manualProtectEnabled":false,"manualRevealEnabled":false,"status":"ACTIVE","valueFormat":"TEXT"}`, string(detail.BeforeSnapshot))
	assert.Contains(t, string(detail.AfterSnapshot), `"status":"DISABLED"`)
}

func TestAdmin_conflictsAndValidation(t *testing.T) {
	env := base.Setup(t)
	update := map[string]any{"expectedVersion": 9, "dataClass": "STANDARD", "description": "x", "manualProtectEnabled": false, "manualRevealEnabled": false, "status": "ACTIVE", "reason": "r"}
	cases := []struct {
		name   string
		req    *builder.RequestBuilder
		status int
		code   biz.ErrorCode
	}{
		{"过期版本", builder.Admin(env.ID, http.MethodPut, "/data-types/"+env.ID.StableType, update), http.StatusConflict, biz.PolicyVersionConflict},
		{"编码已存在", builder.Admin(env.ID, http.MethodPost, "/data-types", map[string]any{"dataTypeCode": env.ID.StableType, "tokenMode": "STABLE", "dataClass": "STANDARD",
			"description": "dup", "manualProtectEnabled": false, "manualRevealEnabled": false, "reason": "r"}), http.StatusConflict, biz.PolicyEntryExists},
		{"修改 Token 模式（未知字段）", builder.Admin(env.ID, http.MethodPut, "/data-types/"+env.ID.StableType, withField(update, "tokenMode", "REFERENCE")), http.StatusBadRequest, biz.InvalidRequest},
		{"缺少理由", builder.Admin(env.ID, http.MethodPost, "/access-rules", map[string]any{"callerService": env.ID.Caller, "action": "REVEAL",
			"dataTypeCode": env.ID.StableType, "purposeCode": "OTHER_PURPOSE"}), http.StatusBadRequest, biz.InvalidRequest},
		{"规则引用不存在的类型", builder.Admin(env.ID, http.MethodPost, "/access-rules", map[string]any{"callerService": env.ID.Caller, "action": "REVEAL",
			"dataTypeCode": env.ID.UnregisteredType, "purposeCode": "OTHER_PURPOSE", "reason": "r"}), http.StatusNotFound, biz.PolicyEntryNotFound},
		{"人工用途为保留值", builder.Admin(env.ID, http.MethodPost, "/access-rules", map[string]any{"callerService": env.ID.Caller, "action": "REVEAL",
			"dataTypeCode": env.ID.StableType, "purposeCode": biz.OperationsManualPurpose, "reason": "r"}), http.StatusBadRequest, biz.InvalidRequest},
		{"规则已存在", builder.Admin(env.ID, http.MethodPost, "/access-rules", map[string]any{"callerService": env.ID.Caller, "action": "PROTECT",
			"dataTypeCode": env.ID.StableType, "purposeCode": fixture.Purpose, "reason": "r"}), http.StatusConflict, biz.PolicyEntryExists},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			env.Invoke(t, tc.req.Build()).Problem(tc.status, tc.code)
		})
	}
}

func TestAdmin_onlyConfiguredAdminCallerWithOperator(t *testing.T) {
	env := base.Setup(t)
	env.Invoke(t, builder.Admin(env.ID, http.MethodGet, "/data-types", nil).Caller(env.ID.Caller).Build()).Problem(http.StatusForbidden, biz.AccessDenied)
	env.Invoke(t, builder.Admin(env.ID, http.MethodGet, "/data-types", nil).WithoutHeader("X-Operator-Id").Build()).Problem(http.StatusBadRequest, biz.InvalidRequest)
}

func TestAdmin_listsDataTypesAndRules(t *testing.T) {
	env := base.Setup(t)
	var types v1.DataTypeListResponseBody
	env.Invoke(t, builder.Admin(env.ID, http.MethodGet, "/data-types", nil).Build()).JSON(&types)
	found := map[string]v1.DataTypeBody{}
	for _, item := range types.Items {
		found[item.DataTypeCode] = item
	}
	assert.Equal(t, "SECRET", found[env.ID.SecretType].DataClass)
	assert.True(t, found[env.ID.ManualType].ManualRevealEnabled)

	var rules v1.AccessRulePageResponseBody
	env.Invoke(t, builder.Admin(env.ID, http.MethodGet, "/access-rules?dataTypeCode="+env.ID.StableType+"&page=1&size=2", nil).Build()).JSON(&rules)
	assert.Equal(t, int64(3), rules.Total)
	assert.Len(t, rules.Items, 2)

	// 分页与 Java PagingRequestBody 一致：默认 10 条，最大 200 条。
	var defaults v1.AccessRulePageResponseBody
	env.Invoke(t, builder.Admin(env.ID, http.MethodGet, "/access-rules?dataTypeCode="+env.ID.StableType, nil).Build()).JSON(&defaults)
	assert.Equal(t, 1, defaults.Page)
	assert.Equal(t, 10, defaults.Size)
	env.Invoke(t, builder.Admin(env.ID, http.MethodGet, "/access-rules?size=200", nil).Build()).JSON(&defaults)
	env.Invoke(t, builder.Admin(env.ID, http.MethodGet, "/access-rules?size=201", nil).Build()).Problem(http.StatusBadRequest, biz.InvalidRequest)
}

// eventually 在 2 秒内轮询条件，覆盖策略热加载的刷新间隔。
func eventually(t *testing.T, condition func() bool) {
	t.Helper()
	deadline := time.Now().Add(2 * time.Second)
	for !condition() {
		if time.Now().After(deadline) {
			t.Fatal("策略变更未在 2 秒内生效")
		}
		time.Sleep(50 * time.Millisecond)
	}
}

func withField(body map[string]any, key string, value any) map[string]any {
	copied := map[string]any{key: value}
	for k, v := range body {
		copied[k] = v
	}
	return copied
}
