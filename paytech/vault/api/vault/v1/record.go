// Package api 定义 Vault HTTP 合同的请求与响应体，字段与 api/openapi/v1.yaml 一致。
//
// 请求字段使用指针区分“缺失/null”与空串；binding 标签由接入层在严格 JSON 解析之后统一校验。
package v1

// ProtectRequestBody 是 POST /vault/v1/records/protect 的请求体。
type ProtectRequestBody struct {
	// DataType 是策略登记的数据类型代码。
	DataType *string `json:"dataType" binding:"required,vault_code"`

	// TokenMode 是 Token 模式：REFERENCE 或 STABLE，须与数据类型登记一致。
	TokenMode *string `json:"tokenMode" binding:"required,oneof=REFERENCE STABLE"`

	// Value 是待保护的原值，UTF-8 最长 32KiB；允许空串。
	Value *string `json:"value" binding:"required"`

	// Purpose 是访问用途代码。
	Purpose *string `json:"purpose" binding:"required,vault_code"`
}

// ProtectResponseBody 是 Protect 成功响应。
type ProtectResponseBody struct {
	// Token 是不透明 Vault Token。
	Token string `json:"token"`
}

// RevealRequestBody 是 POST /vault/v1/records/reveal 的请求体。
type RevealRequestBody struct {
	// Token 是 Protect 返回的 Vault Token。
	Token *string `json:"token" binding:"required,vault_token"`

	// Purpose 是访问用途代码。
	Purpose *string `json:"purpose" binding:"required,vault_code"`
}

// RevealResponseBody 是 Reveal 成功响应；Value 以 JSON 字符串返回原值。
type RevealResponseBody struct {
	Value string `json:"value"`
}
