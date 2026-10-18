package http_test

import (
	"net/http"
	"strconv"
	"testing"

	"github.com/stretchr/testify/assert"

	v1 "github.com/paytech/vault-service/api/vault/v1"
	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/test/support/base"
	"github.com/paytech/vault-service/test/support/builder"
	"github.com/paytech/vault-service/test/support/checker"
	"github.com/paytech/vault-service/test/support/fixture"
)

// 数据类型值格式：维护（格式、正则、样例与兼容语义）以及服务 Protect、人工加密的统一校验。
//
// 被测入口：/vault/v1/admin/data-types → biz.PolicyAdminUsecase；POST /vault/v1/records/protect、/vault/v1/admin/manual/protect
// → biz.ProtectUsecase（值格式经 pkg/policy 快照生效）。

const phonePattern = `\+[1-9][0-9]{7,14}`

func TestValueFormat_regexTypeValidatesServiceProtectAndReplay(t *testing.T) {
	env := base.Setup(t)
	code := env.ID.TypePrefix() + "FMT_PHONE"
	sample := "+971500000099"

	var created v1.DataTypeResponseBody
	env.Invoke(t, builder.Admin(env.ID, http.MethodPost, "/data-types", dataTypeBody(code, "STABLE", map[string]any{
		"valueFormat": "REGEX", "valuePattern": phonePattern, "sampleValues": []string{sample}})).Build()).NotLeaking(sample).JSON(&created)
	assert.Equal(t, "REGEX", created.DataType.ValueFormat)
	assert.Equal(t, phonePattern, created.DataType.ValuePattern)
	var detail v1.PolicyChangeBody
	env.Invoke(t, builder.Admin(env.ID, http.MethodGet, "/policy-changes/"+strconv.Itoa(created.PolicyVersion), nil).Build()).NotLeaking(sample).JSON(&detail)
	assert.Contains(t, string(detail.AfterSnapshot), `"valueFormat":"REGEX"`)
	grantProtect(t, env, code)

	key := env.ID.NextIdempotencyKey()
	valid := func() *builder.RequestBuilder {
		return builder.ProtectTemplate(env.ID).DataType(code).TokenMode("STABLE").Value("+8613800138000").IdempotencyKey(key)
	}
	eventually(t, func() bool { return env.Invoke(t, valid().Build()).Status == http.StatusOK })

	invalid := builder.ProtectTemplate(env.ID).DataType(code).TokenMode("STABLE").Value("13800138000").Build()
	env.Invoke(t, invalid).Problem(http.StatusBadRequest, biz.ValuePatternMismatch).NotLeaking("13800138000")
	checker.AccessEvent(t, env.Mapper, invalid.RequestID()).Result(biz.ProtectAction, biz.Denied, string(biz.ValuePatternMismatch)).DataType(code)
	checker.Records(t, env.Mapper).Count(code, 1)

	// 改严正则后，原值不再合格：同一幂等键的重放也被拒绝（先校验、后查幂等键）。
	updateDataType(t, env, code, created.DataType.Version, map[string]any{"valueFormat": "REGEX", "valuePattern": `\+971[0-9]{9}`, "sampleValues": []string{sample}})
	eventually(t, func() bool { return env.Invoke(t, valid().Build()).Status == http.StatusBadRequest })
	env.Invoke(t, valid().Build()).Problem(http.StatusBadRequest, biz.ValuePatternMismatch)
	checker.Logs(t, env.Logs.String()).NotLeaking("13800138000", sample)
}

func TestValueFormat_jsonTypeValidatesManualProtect(t *testing.T) {
	env := base.Setup(t)
	code := env.ID.TypePrefix() + "FMT_CONFIG"
	env.Invoke(t, builder.Admin(env.ID, http.MethodPost, "/data-types", dataTypeBody(code, "REFERENCE", map[string]any{
		"valueFormat": "JSON", "manualProtectEnabled": true})).Build()).Success()

	broken := `{"apiKey":"k","apiKey":"k2"}`
	eventually(t, func() bool {
		return env.Invoke(t, builder.ManualProtect(env.ID, code, `{"apiKey":"k"}`).Build()).Status == http.StatusOK
	})
	request := builder.ManualProtect(env.ID, code, broken).Build()
	env.Invoke(t, request).Problem(http.StatusBadRequest, biz.ValueNotJSONObject).NotLeaking(broken)
	checker.AccessEvent(t, env.Mapper, request.RequestID()).Result(biz.ProtectAction, biz.Denied, string(biz.ValueNotJSONObject)).
		Purpose(biz.OperationsManualPurpose).Manual(env.ID.Operator, "DATA_REPAIR", "OPS-1001")
	env.Invoke(t, builder.ManualProtect(env.ID, code, `["apiKey"]`).Build()).Problem(http.StatusBadRequest, biz.ValueNotJSONObject)
	checker.Records(t, env.Mapper).Count(code, 1)
	checker.Logs(t, env.Logs.String()).NotLeaking(broken)
}

func TestValueFormat_maintenanceRulesAndCompatibility(t *testing.T) {
	env := base.Setup(t)
	code := env.ID.TypePrefix() + "FMT_RULES"
	var created v1.DataTypeResponseBody
	env.Invoke(t, builder.Admin(env.ID, http.MethodPost, "/data-types", dataTypeBody(code, "REFERENCE", nil)).Build()).JSON(&created)
	assert.Equal(t, "TEXT", created.DataType.ValueFormat, "未指定值格式时为 TEXT")
	assert.Empty(t, created.DataType.ValuePattern)

	version := created.DataType.Version
	cases := []struct {
		name   string
		fields map[string]any
		code   biz.ErrorCode
	}{
		{"切换到 REGEX 缺样例", map[string]any{"valueFormat": "REGEX", "valuePattern": phonePattern}, biz.SampleValueInvalid},
		{"样例不符合正则", map[string]any{"valueFormat": "REGEX", "valuePattern": phonePattern, "sampleValues": []string{"+971500000001", "abc"}}, biz.SampleValueInvalid},
		{"正则无法编译", map[string]any{"valueFormat": "REGEX", "valuePattern": "(", "sampleValues": []string{"("}}, biz.ValuePatternInvalid},
		{"REGEX 缺正则", map[string]any{"valueFormat": "REGEX", "sampleValues": []string{"x"}}, biz.ValuePatternInvalid},
		{"TEXT 带正则", map[string]any{"valueFormat": "TEXT", "valuePattern": phonePattern}, biz.ValuePatternInvalid},
		{"未知值格式", map[string]any{"valueFormat": "XML"}, biz.InvalidRequest},
		{"样例超过 5 个", map[string]any{"valueFormat": "TEXT", "sampleValues": []string{"1", "2", "3", "4", "5", "6"}}, biz.SampleValueInvalid},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			env.Invoke(t, builder.Admin(env.ID, http.MethodPut, "/data-types/"+code, updateBody(version, tc.fields)).Build()).Problem(http.StatusBadRequest, tc.code)
		})
	}

	// 切换到 REGEX 并带合格样例 → 成功；之后不带 valueFormat 的旧请求保持格式与正则不变。
	switched := updateDataType(t, env, code, version, map[string]any{"valueFormat": "REGEX", "valuePattern": phonePattern, "sampleValues": []string{"+971500000001"}})
	legacy := updateDataType(t, env, code, switched.DataType.Version, map[string]any{"description": "旧版调用方修改说明"})
	assert.Equal(t, "REGEX", legacy.DataType.ValueFormat)
	assert.Equal(t, phonePattern, legacy.DataType.ValuePattern)
	// Java 客户端对未设置的字段发送显式 null：等同于未传。
	nulls := updateDataType(t, env, code, legacy.DataType.Version, map[string]any{"valueFormat": nil, "valuePattern": nil, "sampleValues": nil})
	assert.Equal(t, "REGEX", nulls.DataType.ValueFormat)
	assert.Equal(t, phonePattern, nulls.DataType.ValuePattern)
	// 正则不变时修改其他字段不需要样例；改回 JSON 时正则清空。
	unchanged := updateDataType(t, env, code, nulls.DataType.Version, map[string]any{"valueFormat": "REGEX", "valuePattern": phonePattern})
	json := updateDataType(t, env, code, unchanged.DataType.Version, map[string]any{"valueFormat": "JSON"})
	assert.Equal(t, "JSON", json.DataType.ValueFormat)
	assert.Empty(t, json.DataType.ValuePattern)

	var detail v1.PolicyChangeBody
	env.Invoke(t, builder.Admin(env.ID, http.MethodGet, "/policy-changes/"+strconv.Itoa(switched.PolicyVersion), nil).Build()).NotLeaking("+971500000001").JSON(&detail)
	assert.Contains(t, string(detail.BeforeSnapshot), `"valueFormat":"TEXT"`)
	assert.Contains(t, string(detail.AfterSnapshot), `"valuePattern":"\\+[1-9][0-9]{7,14}"`)
	checker.Logs(t, env.Logs.String()).NotLeaking("+971500000001")
}

// dataTypeBody 返回新建数据类型请求，extra 覆盖默认字段。
func dataTypeBody(code, tokenMode string, extra map[string]any) map[string]any {
	body := map[string]any{"dataTypeCode": code, "tokenMode": tokenMode, "dataClass": "STANDARD", "description": "值格式测试",
		"manualProtectEnabled": false, "manualRevealEnabled": false, "reason": "值格式测试"}
	for k, v := range extra {
		body[k] = v
	}
	return body
}

// updateBody 返回修改数据类型请求，extra 覆盖默认字段。
func updateBody(version int, extra map[string]any) map[string]any {
	body := map[string]any{"expectedVersion": version, "dataClass": "STANDARD", "description": "值格式测试", "manualProtectEnabled": false,
		"manualRevealEnabled": false, "status": "ACTIVE", "reason": "调整值格式"}
	for k, v := range extra {
		body[k] = v
	}
	return body
}

// updateDataType 修改数据类型并返回结果。
func updateDataType(t *testing.T, env *base.Environment, code string, version int, extra map[string]any) v1.DataTypeResponseBody {
	t.Helper()
	var updated v1.DataTypeResponseBody
	env.Invoke(t, builder.Admin(env.ID, http.MethodPut, "/data-types/"+code, updateBody(version, extra)).Build()).JSON(&updated)
	return updated
}

// grantProtect 为测试调用方开通该类型的 Protect。
func grantProtect(t *testing.T, env *base.Environment, code string) {
	t.Helper()
	env.Invoke(t, builder.Admin(env.ID, http.MethodPost, "/access-rules", map[string]any{"callerService": env.ID.Caller, "action": "PROTECT",
		"dataTypeCode": code, "purposeCode": fixture.Purpose, "reason": "开通服务加密"}).Build()).Success()
}
