package contract

import (
	"testing"
)

const adminDocument = "../../../api/openapi/admin-v1.yaml"

// 管理接口文档与 internal/server/admin.go 登记的 11 个路由一一对应；写请求均为严格对象并要求操作人头。
func TestAdminOpenAPIMatchesRegisteredRoutes(t *testing.T) {
	document := loadDocument(t, adminDocument)
	operations := map[string]string{
		"/vault/v1/admin/data-types":                       "get",
		"/vault/v1/admin/data-types/{dataTypeCode}":        "put",
		"/vault/v1/admin/access-rules":                     "get",
		"/vault/v1/admin/access-rules/{accessRuleId}":      "put",
		"/vault/v1/admin/policy-changes":                   "get",
		"/vault/v1/admin/policy-changes/{policyVersionNo}": "get",
		"/vault/v1/admin/manual-events":                    "get",
		"/vault/v1/admin/manual/protect":                   "post",
		"/vault/v1/admin/manual/reveal":                    "post",
	}
	for path, method := range operations {
		operation := requireOperation(t, document, path, method)
		requireHeader(t, operation, "X-Caller-Service", true)
		requireHeader(t, operation, "X-Operator-Id", true)
		requireHeader(t, operation, "X-Request-Id", false)
	}
	requireOperation(t, document, "/vault/v1/admin/data-types", "post")
	requireOperation(t, document, "/vault/v1/admin/access-rules", "post")
	requireOperationCount(t, document, 11)
}

func TestAdminOpenAPIRequestBodiesAreStrict(t *testing.T) {
	document := loadDocument(t, adminDocument)
	requireObjectSchema(t, document, "CreateDataTypeRequest", []string{"dataTypeCode", "tokenMode", "dataClass", "description", "manualProtectEnabled", "manualRevealEnabled", "reason"})
	requireObjectSchema(t, document, "UpdateDataTypeRequest", []string{"expectedVersion", "dataClass", "description", "manualProtectEnabled", "manualRevealEnabled", "status", "reason"})
	requireObjectSchema(t, document, "CreateAccessRuleRequest", []string{"callerService", "action", "dataTypeCode", "purposeCode", "reason"})
	requireObjectSchema(t, document, "UpdateAccessRuleRequest", []string{"expectedVersion", "status", "reason"})
	requireObjectSchema(t, document, "ManualProtectRequest", []string{"dataTypeCode", "value", "reasonCode", "ticketRef", "allowedDataClasses"})
	requireObjectSchema(t, document, "ManualRevealRequest", []string{"token", "reasonCode", "ticketRef", "allowedDataClasses"})
	requireObjectSchema(t, document, "ManualEvent", []string{"requestId", "actorId", "action", "dataTypeCode", "reasonCode", "ticketRef", "outcome", "resultCode", "createTime"})
	requireUTF8ByteLimit(t, document, "ManualProtectRequest", "value", 32768)
	requireEnum(t, document, "UpdateDataTypeRequest", "status", "ACTIVE", "DISABLED")

	update := document.Components.Schemas["UpdateDataTypeRequest"].Value
	if update.Properties["tokenMode"] != nil || update.Properties["dataTypeCode"] != nil {
		t.Fatal("UpdateDataTypeRequest must not allow changing the code or token mode")
	}
	event := document.Components.Schemas["ManualEvent"].Value
	for _, forbidden := range []string{"token", "value", "recordId"} {
		if event.Properties[forbidden] != nil {
			t.Fatalf("ManualEvent must not expose %s", forbidden)
		}
	}
	manual := requireOperation(t, document, "/vault/v1/admin/manual/protect", "post")
	requireHeader(t, manual, "Idempotency-Key", true)
	requireResponses(t, manual, "200", "400", "403", "409", "500", "503")
}
