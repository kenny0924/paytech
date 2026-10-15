// Package builder 构造发往 Vault 的 HTTP 请求：template 提供最小合法请求，用例只覆盖与场景有关的部分。
//
// 负例需要的缺失字段、null、原始 JSON 与缺失头都可显式表达，Build 不修复非法输入。
package builder

import (
	"encoding/json"
	"net/http"

	"github.com/paytech/vault-service/test/support/fixture"
)

const (
	protectPath = "/vault/v1/records/protect"
	revealPath  = "/vault/v1/records/reveal"
)

// Request 是构造完成的 HTTP 请求描述。
type Request struct {
	Method string
	Path   string
	Header http.Header
	Body   []byte
}

// RequestID 返回请求携带的 X-Request-Id。
func (r Request) RequestID() string { return r.Header.Get("X-Request-Id") }

// RequestBuilder 是通用请求构造器；字段以 map 保存，可表达缺失与 null。
type RequestBuilder struct {
	request Request
	fields  map[string]any
	raw     []byte
}

// ProtectTemplate 返回 Caller 以 STABLE 数据类型保护一个值的合法请求。
func ProtectTemplate(id *fixture.Identity) *RequestBuilder {
	b := newBuilder(id, protectPath)
	b.request.Header.Set("Idempotency-Key", id.NextIdempotencyKey())
	b.fields = map[string]any{"dataType": id.StableType, "tokenMode": "STABLE", "value": "alice@example.com", "purpose": fixture.Purpose}
	return b
}

// RevealTemplate 返回 Caller 读取指定 Token 的合法请求。
func RevealTemplate(id *fixture.Identity, token string) *RequestBuilder {
	b := newBuilder(id, revealPath)
	b.fields = map[string]any{"token": token, "purpose": fixture.Purpose}
	return b
}

// Admin 返回发往 /vault/v1/admin 的管理请求：管理调用方 + 操作人；body 为 nil 时为无正文请求。
func Admin(id *fixture.Identity, method, path string, body map[string]any) *RequestBuilder {
	b := newBuilder(id, "/vault/v1/admin"+path)
	b.request.Method = method
	b.request.Header.Set("X-Caller-Service", id.AdminCaller)
	b.request.Header.Set("X-Operator-Id", id.Operator)
	b.fields = body
	if body == nil {
		b.request.Header.Del("Content-Type")
	}
	return b
}

// ManualProtect 返回人工加密请求（默认只允许普通敏感级别）。
func ManualProtect(id *fixture.Identity, dataType, value string) *RequestBuilder {
	b := Admin(id, http.MethodPost, "/manual/protect", map[string]any{"dataTypeCode": dataType, "value": value, "reasonCode": "DATA_REPAIR",
		"ticketRef": "OPS-1001", "allowedDataClasses": []string{"STANDARD"}})
	b.request.Header.Set("Idempotency-Key", id.NextIdempotencyKey())
	return b
}

// ManualReveal 返回人工解密请求（默认只允许普通敏感级别）。
func ManualReveal(id *fixture.Identity, token string) *RequestBuilder {
	return Admin(id, http.MethodPost, "/manual/reveal", map[string]any{"token": token, "reasonCode": "CUSTOMER_VERIFICATION",
		"ticketRef": "OPS-1002", "allowedDataClasses": []string{"STANDARD"}})
}

// Get 返回技术端点的 GET 请求。
func Get(id *fixture.Identity, path string) *RequestBuilder {
	b := newBuilder(id, path)
	b.request.Method = http.MethodGet
	b.request.Header.Del("Content-Type")
	return b
}

func newBuilder(id *fixture.Identity, path string) *RequestBuilder {
	header := http.Header{}
	header.Set("Content-Type", "application/json")
	header.Set("X-Caller-Service", id.Caller)
	header.Set("X-Request-Id", id.NextRequestID())
	return &RequestBuilder{request: Request{Method: http.MethodPost, Path: path, Header: header}}
}

// Field 设置请求体字段。
func (b *RequestBuilder) Field(name string, value any) *RequestBuilder {
	b.fields[name] = value
	return b
}

// Null 把字段设为 JSON null。
func (b *RequestBuilder) Null(name string) *RequestBuilder { return b.Field(name, nil) }

// Omit 删除请求体字段。
func (b *RequestBuilder) Omit(name string) *RequestBuilder {
	delete(b.fields, name)
	return b
}

// DataType 设置数据类型。
func (b *RequestBuilder) DataType(value string) *RequestBuilder { return b.Field("dataType", value) }

// TokenMode 设置 Token 模式。
func (b *RequestBuilder) TokenMode(value string) *RequestBuilder { return b.Field("tokenMode", value) }

// Value 设置待保护的值。
func (b *RequestBuilder) Value(value string) *RequestBuilder { return b.Field("value", value) }

// Purpose 设置访问用途。
func (b *RequestBuilder) Purpose(value string) *RequestBuilder { return b.Field("purpose", value) }

// RawBody 直接使用原始请求体，用于畸形 JSON 等负例。
func (b *RequestBuilder) RawBody(raw string) *RequestBuilder {
	b.raw = []byte(raw)
	return b
}

// Header 设置请求头。
func (b *RequestBuilder) Header(name, value string) *RequestBuilder {
	b.request.Header.Set(name, value)
	return b
}

// WithoutHeader 删除请求头。
func (b *RequestBuilder) WithoutHeader(name string) *RequestBuilder {
	b.request.Header.Del(name)
	return b
}

// Caller 设置调用方。
func (b *RequestBuilder) Caller(value string) *RequestBuilder {
	return b.Header("X-Caller-Service", value)
}

// IdempotencyKey 设置幂等键。
func (b *RequestBuilder) IdempotencyKey(value string) *RequestBuilder {
	return b.Header("Idempotency-Key", value)
}

// Build 生成请求；GET 请求无正文。
func (b *RequestBuilder) Build() Request {
	request := b.request
	request.Header = b.request.Header.Clone()
	switch {
	case b.raw != nil:
		request.Body = b.raw
	case b.fields != nil:
		raw, err := json.Marshal(b.fields)
		if err != nil {
			panic(err)
		}
		request.Body = raw
	}
	return request
}
