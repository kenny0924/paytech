package contract

import (
	"context"
	"fmt"
	"net/http"
	"path/filepath"
	"strconv"
	"strings"
	"testing"

	"github.com/getkin/kin-openapi/openapi3"
)

const requestIDPattern = `^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$`
const problemTimestampPattern = `^[0-9]{4}-[0-9]{2}-[0-9]{2}T([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]\.[0-9]{6}Z$`

func TestOpenAPIExposesOnlyProtectAndReveal(t *testing.T) {
	document := loadDocument(t, "../../api/openapi/v1.yaml")
	requireOperation(t, document, "/vault/v1/records/protect", "post")
	requireOperation(t, document, "/vault/v1/records/reveal", "post")
	requireOperationCount(t, document, 2)
}

func TestOpenAPIProtectContractRejectsUnexpectedFieldsAndOversizedValues(t *testing.T) {
	document := loadDocument(t, "../../api/openapi/v1.yaml")
	operation := requireOperation(t, document, "/vault/v1/records/protect", "post")

	requireHeader(t, operation, "X-Caller-Service", true)
	requireHeader(t, operation, "Idempotency-Key", true)
	requireHeader(t, operation, "X-Request-Id", false)
	requireJSONRequestSchema(t, operation, "ProtectRequest")
	requireRawBodyByteLimit(t, operation, 262144)
	requireObjectSchema(t, document, "ProtectRequest", []string{"dataType", "tokenMode", "value", "purpose"})
	requireUTF8ByteLimit(t, document, "ProtectRequest", "value", 32768)
	requireEnum(t, document, "ProtectRequest", "tokenMode", "REFERENCE", "STABLE")
	requireResponses(t, operation, "200", "400", "403", "405", "409", "500", "503")
}

func TestOpenAPIRevealContractHasRequiredHeadersAndStableResponses(t *testing.T) {
	document := loadDocument(t, "../../api/openapi/v1.yaml")
	operation := requireOperation(t, document, "/vault/v1/records/reveal", "post")

	requireHeader(t, operation, "X-Caller-Service", true)
	requireHeader(t, operation, "X-Request-Id", false)
	requireJSONRequestSchema(t, operation, "RevealRequest")
	requireRawBodyByteLimit(t, operation, 262144)
	requireObjectSchema(t, document, "RevealRequest", []string{"token", "purpose"})
	requireResponses(t, operation, "200", "400", "403", "404", "405", "500", "503")
}

func TestOpenAPIResponsesUseSuccessJSONAndErrorProblemJSON(t *testing.T) {
	document := loadDocument(t, "../../api/openapi/v1.yaml")

	for _, path := range []string{"/vault/v1/records/protect", "/vault/v1/records/reveal"} {
		operation := requireOperation(t, document, path, "post")
		for status, response := range operation.Responses.Map() {
			if response.Value.Headers["X-Request-Id"] == nil {
				t.Fatalf("%s %s response %s must expose X-Request-Id", "POST", path, status)
			}
			if response.Value.Headers["Cache-Control"] == nil {
				t.Fatalf("%s %s response %s must expose Cache-Control", "POST", path, status)
			}
			if response.Value.Headers["Cache-Control"].Value.Schema.Value.Enum[0] != "no-store" {
				t.Fatalf("%s %s response %s Cache-Control must be no-store", "POST", path, status)
			}
			contentType := "application/problem+json"
			if status == "200" {
				contentType = "application/json"
			}
			if len(response.Value.Content) != 1 || response.Value.Content[contentType] == nil {
				t.Fatalf("%s %s response %s must use only %s", "POST", path, status, contentType)
			}
			if status == "405" {
				allow := response.Value.Headers["Allow"]
				if allow == nil || allow.Value == nil || !allow.Value.Required || len(allow.Value.Schema.Value.Enum) != 1 || allow.Value.Schema.Value.Enum[0] != http.MethodPost {
					t.Fatalf("%s %s response 405 must require Allow: POST", "POST", path)
				}
			}
		}
	}

	requireObjectSchema(t, document, "ProtectResponse", []string{"token"})
	requireObjectSchema(t, document, "RevealResponse", []string{"value"})
	requireProblemSchema(t, document)
	requireRequestIDProfile(t, document)
	requireErrorResponseMappings(t, document)
}

func loadDocument(t *testing.T, relativePath string) *openapi3.T {
	t.Helper()
	document, err := openapi3.NewLoader().LoadFromFile(filepath.Clean(relativePath))
	if err != nil {
		t.Fatalf("load OpenAPI document: %v", err)
	}
	if err := document.Validate(context.Background()); err != nil {
		t.Fatalf("validate OpenAPI document: %v", err)
	}
	return document
}

func requireOperation(t *testing.T, document *openapi3.T, path, method string) *openapi3.Operation {
	t.Helper()
	pathItem := document.Paths.Find(path)
	if pathItem == nil {
		t.Fatalf("missing path %s", path)
	}
	operation := pathItem.GetOperation(strings.ToUpper(method))
	if operation == nil {
		t.Fatalf("missing %s operation for %s", method, path)
	}
	return operation
}

func requireOperationCount(t *testing.T, document *openapi3.T, expected int) {
	t.Helper()
	actual := 0
	for _, pathItem := range document.Paths.Map() {
		for _, method := range []string{"get", "put", "post", "delete", "options", "head", "patch", "trace"} {
			if pathItem.GetOperation(strings.ToUpper(method)) != nil {
				actual++
			}
		}
	}
	if actual != expected {
		t.Fatalf("operation count = %d, want %d", actual, expected)
	}
}

func requireHeader(t *testing.T, operation *openapi3.Operation, header string, required bool) {
	t.Helper()
	for _, parameter := range operation.Parameters {
		if parameter.Value.In == openapi3.ParameterInHeader && parameter.Value.Name == header {
			if parameter.Value.Required != required {
				t.Fatalf("header %s required = %t, want %t", header, parameter.Value.Required, required)
			}
			return
		}
	}
	t.Fatalf("header %s is missing", header)
}

func requireJSONRequestSchema(t *testing.T, operation *openapi3.Operation, schemaName string) {
	t.Helper()
	if operation.RequestBody == nil || operation.RequestBody.Value.Required == false {
		t.Fatal("required request body is missing")
	}
	mediaType := operation.RequestBody.Value.Content["application/json"]
	if mediaType == nil || mediaType.Schema == nil || mediaType.Schema.Ref != "#/components/schemas/"+schemaName {
		t.Fatalf("request body must be application/json %s", schemaName)
	}
}

func requireObjectSchema(t *testing.T, document *openapi3.T, schemaName string, required []string) {
	t.Helper()
	schema := document.Components.Schemas[schemaName]
	if schema == nil || schema.Value == nil || schema.Value.Type == nil || !schema.Value.Type.Is("object") || schema.Value.AdditionalProperties.Has == nil || *schema.Value.AdditionalProperties.Has {
		t.Fatalf("%s must be an object with additionalProperties: false", schemaName)
	}
	for _, property := range required {
		if !contains(schema.Value.Required, property) || schema.Value.Properties[property] == nil {
			t.Fatalf("%s must require property %s", schemaName, property)
		}
	}
}

func requireProblemSchema(t *testing.T, document *openapi3.T) {
	t.Helper()
	required := []string{"type", "title", "detail", "code", "requestId", "timestamp"}
	requireObjectSchema(t, document, "ErrorResponse", required)
	schema := document.Components.Schemas["ErrorResponse"].Value
	if len(schema.Required) != len(required) || len(schema.Properties) != len(required) {
		t.Fatalf("ErrorResponse must contain exactly the six Problem members: required=%v properties=%v", schema.Required, schema.Properties)
	}
	for _, forbidden := range []string{"message", "status", "instance", "errors"} {
		if schema.Properties[forbidden] != nil {
			t.Fatalf("ErrorResponse contains forbidden or unsupported member %s", forbidden)
		}
	}
	typeSchema := schema.Properties["type"].Value
	if typeSchema.Const != "about:blank" || typeSchema.Format != "uri-reference" {
		t.Fatalf("Problem type constraint = const %v format %q", typeSchema.Const, typeSchema.Format)
	}
	titleSchema := schema.Properties["title"].Value
	wantTitles := []any{"Bad Request", "Forbidden", "Not Found", "Method Not Allowed", "Conflict", "Internal Server Error", "Service Unavailable"}
	if fmt.Sprint(titleSchema.Enum) != fmt.Sprint(wantTitles) {
		t.Fatalf("Problem title enum = %v, want %v", titleSchema.Enum, wantTitles)
	}
	detailSchema := schema.Properties["detail"].Value
	if detailSchema.Type == nil || !detailSchema.Type.Is("string") || detailSchema.MinLength != 1 {
		t.Fatal("Problem detail must be a non-empty string")
	}
	codeSchema := schema.Properties["code"].Value
	if codeSchema.Pattern != `^[A-Z][A-Z0-9_]{0,63}$` || len(codeSchema.Enum) != 12 {
		t.Fatalf("Problem code constraints = pattern %q enum %v", codeSchema.Pattern, codeSchema.Enum)
	}
	requestIDSchema := schema.Properties["requestId"].Value
	if requestIDSchema.MinLength != 1 || requestIDSchema.MaxLength == nil || *requestIDSchema.MaxLength != 64 || requestIDSchema.Pattern != requestIDPattern {
		t.Fatalf("Problem requestId constraints = min %d max %v pattern %q", requestIDSchema.MinLength, requestIDSchema.MaxLength, requestIDSchema.Pattern)
	}
	timestampSchema := schema.Properties["timestamp"].Value
	if timestampSchema.Format != "date-time" || timestampSchema.Pattern != problemTimestampPattern {
		t.Fatalf("Problem timestamp constraints = format %q pattern %q", timestampSchema.Format, timestampSchema.Pattern)
	}
}

func requireRequestIDProfile(t *testing.T, document *openapi3.T) {
	t.Helper()
	parameter := document.Components.Parameters["XRequestId"].Value
	if parameter.Required || parameter.Schema.Value.Pattern != requestIDPattern || !strings.Contains(parameter.Description, "replaced") {
		t.Fatalf("X-Request-Id parameter does not describe preservation/replacement: %+v", parameter)
	}
	header := document.Components.Headers["XRequestId"].Value
	if !header.Required || header.Schema.Value.Pattern != requestIDPattern || !strings.Contains(header.Description, "effective") {
		t.Fatalf("X-Request-Id response header is not the effective unified ID: %+v", header)
	}
}

func requireUTF8ByteLimit(t *testing.T, document *openapi3.T, schemaName, property string, expected int64) {
	t.Helper()
	schema := document.Components.Schemas[schemaName].Value.Properties[property].Value
	actual, ok := extensionInt64(schema.Extensions["x-max-utf8-bytes"])
	if !ok || actual != expected {
		t.Fatalf("%s.%s x-max-utf8-bytes = %v, want %d", schemaName, property, schema.Extensions["x-max-utf8-bytes"], expected)
	}
}

func requireRawBodyByteLimit(t *testing.T, operation *openapi3.Operation, expected int64) {
	t.Helper()
	actual, ok := extensionInt64(operation.Extensions["x-max-raw-body-bytes"])
	if !ok || actual != expected {
		t.Fatalf("x-max-raw-body-bytes = %v, want %d", operation.Extensions["x-max-raw-body-bytes"], expected)
	}
}

func extensionInt64(value any) (int64, bool) {
	switch typed := value.(type) {
	case float64:
		return int64(typed), true
	case int64:
		return typed, true
	case int:
		return int64(typed), true
	default:
		return 0, false
	}
}

func requireEnum(t *testing.T, document *openapi3.T, schemaName, property string, expected ...string) {
	t.Helper()
	actual := document.Components.Schemas[schemaName].Value.Properties[property].Value.Enum
	if fmt.Sprint(actual) != fmt.Sprint(expected) {
		t.Fatalf("%s.%s enum = %v, want %v", schemaName, property, actual, expected)
	}
}

func requireResponses(t *testing.T, operation *openapi3.Operation, statuses ...string) {
	t.Helper()
	if len(operation.Responses.Map()) != len(statuses) {
		t.Fatalf("response count = %d, want exact statuses %v", len(operation.Responses.Map()), statuses)
	}
	for _, status := range statuses {
		if operation.Responses.Value(status) == nil {
			t.Fatalf("missing %s response", status)
		}
	}
}

func requireErrorResponseMappings(t *testing.T, document *openapi3.T) {
	t.Helper()
	want := map[string]map[string]string{
		"400": {
			"INVALID_REQUEST":        "请求参数无效",
			"VALUE_TOO_LARGE":        "值超过32KiB限制",
			"UNSUPPORTED_DATA_TYPE":  "不支持的数据类型",
			"TOKEN_MODE_NOT_ALLOWED": "Token模式不允许",
		},
		"403": {"ACCESS_DENIED": "访问被拒绝"},
		"404": {"TOKEN_NOT_FOUND": "Token不存在"},
		"405": {"METHOD_NOT_ALLOWED": "请求方法不受支持"},
		"409": {"IDEMPOTENCY_KEY_CONFLICT": "幂等键已被不同请求使用"},
		"500": {
			"INTEGRITY_CHECK_FAILED": "完整性校验失败",
			"INTERNAL_ERROR":         "服务内部错误",
		},
		"503": {
			"DEPENDENCY_UNAVAILABLE": "依赖服务暂不可用",
			"AUDIT_UNAVAILABLE":      "访问审计不可用",
		},
	}
	for _, path := range []string{"/vault/v1/records/protect", "/vault/v1/records/reveal"} {
		operation := requireOperation(t, document, path, "post")
		for status, codes := range want {
			response := operation.Responses.Value(status)
			if response == nil {
				if (status == "404" && path == "/vault/v1/records/protect") ||
					(status == "409" && path == "/vault/v1/records/reveal") {
					continue
				}
				t.Fatalf("%s %s is missing", path, status)
			}
			requireErrorVariants(t, response, cloneErrorCodes(codes), status)
		}
	}
}

func cloneErrorCodes(source map[string]string) map[string]string {
	copy := make(map[string]string, len(source))
	for code, detail := range source {
		copy[code] = detail
	}
	return copy
}

func requireErrorVariants(t *testing.T, response *openapi3.ResponseRef, want map[string]string, status string) {
	t.Helper()
	schema := response.Value.Content["application/problem+json"].Schema.Value
	variants := schema.OneOf
	if len(variants) == 0 {
		variants = openapi3.SchemaRefs{{Value: schema}}
	}
	if len(variants) != len(want) {
		t.Fatalf("error variants = %d, want %d", len(variants), len(want))
	}
	for _, variant := range variants {
		if len(variant.Value.AllOf) != 2 {
			t.Fatalf("error variant %s must constrain the shared ErrorResponse", variant.Ref)
		}
		properties := variant.Value.AllOf[1].Value.Properties
		code, codeOK := properties["code"].Value.Const.(string)
		detail, detailOK := properties["detail"].Value.Const.(string)
		title, titleOK := properties["title"].Value.Const.(string)
		statusCode, statusError := strconv.Atoi(status)
		if !codeOK || !detailOK || !titleOK || statusError != nil || title != http.StatusText(statusCode) || want[code] != detail {
			t.Fatalf("unexpected error variant status=%s title=%q code=%q detail=%q", status, title, code, detail)
		}
		delete(want, code)
	}
	if len(want) != 0 {
		t.Fatalf("missing error variants: %v", want)
	}
}

func contains(values []string, expected string) bool {
	for _, value := range values {
		if value == expected {
			return true
		}
	}
	return false
}
