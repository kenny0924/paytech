// Package checker 只读断言：HTTP 响应、数据库记录与访问审计。预期值来自用例约定，不从实际结果推导。
package checker

import (
	"encoding/json"
	"net/http"
	"regexp"
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	v1 "github.com/paytech/vault-service/api/vault/v1"
	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/test/support/builder"
)

var problemTimestamp = regexp.MustCompile(`^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{6}Z$`)

// ResponseChecker 检查一次 HTTP 响应。
type ResponseChecker struct {
	t       *testing.T
	request builder.Request
	Status  int
	Header  http.Header
	Body    []byte
}

// NewResponseChecker 由 Invoke 创建。
func NewResponseChecker(t *testing.T, request builder.Request, status int, header http.Header, body []byte) *ResponseChecker {
	return &ResponseChecker{t: t, request: request, Status: status, Header: header, Body: body}
}

// Success 断言 200 JSON 成功响应与安全头，并回显请求 ID。
func (c *ResponseChecker) Success() *ResponseChecker {
	c.t.Helper()
	require.Equal(c.t, http.StatusOK, c.Status, "响应：%s", c.Body)
	assert.Equal(c.t, "application/json", c.Header.Get("Content-Type"))
	c.safetyHeaders()
	return c
}

// Problem 断言 RFC 9457-based Problem：状态、错误码、固定六个成员、请求 ID 与微秒 UTC 时间。
func (c *ResponseChecker) Problem(status int, code biz.ErrorCode) *ResponseChecker {
	c.t.Helper()
	require.Equal(c.t, status, c.Status, "响应：%s", c.Body)
	assert.Equal(c.t, "application/problem+json", c.Header.Get("Content-Type"))
	c.safetyHeaders()
	if c.request.Method == http.MethodHead {
		assert.Empty(c.t, c.Body)
		return c
	}
	var problem map[string]any
	require.NoError(c.t, json.Unmarshal(c.Body, &problem), "Problem 不是 JSON：%s", c.Body)
	assert.Len(c.t, problem, 6, "Problem 成员：%s", c.Body)
	assert.Equal(c.t, "about:blank", problem["type"])
	assert.Equal(c.t, http.StatusText(status), problem["title"])
	assert.Equal(c.t, string(code), problem["code"])
	assert.Equal(c.t, c.Header.Get("X-Request-Id"), problem["requestId"])
	assert.Regexp(c.t, problemTimestamp, problem["timestamp"])
	return c
}

// Allow 断言 Allow 头。
func (c *ResponseChecker) Allow(want string) *ResponseChecker {
	c.t.Helper()
	assert.Equal(c.t, want, c.Header.Get("Allow"))
	return c
}

// Token 读取 Protect 成功响应中的 Token，并断言响应只有该字段。
func (c *ResponseChecker) Token() string {
	c.t.Helper()
	var body v1.ProtectResponseBody
	c.decodeExact(&body, 1)
	require.Regexp(c.t, `^[A-Za-z0-9_-]{43}$`, body.Token)
	return body.Token
}

// RevealedValue 断言 Reveal 成功响应返回的原值。
func (c *ResponseChecker) RevealedValue(want string) *ResponseChecker {
	c.t.Helper()
	var body v1.RevealResponseBody
	c.decodeExact(&body, 1)
	assert.Equal(c.t, want, body.Value)
	return c
}

// BodyContains 断言响应正文包含片段（用于非 JSON 的指标端点）。
func (c *ResponseChecker) BodyContains(fragment string) *ResponseChecker {
	c.t.Helper()
	assert.Contains(c.t, string(c.Body), fragment)
	return c
}

// NotLeaking 断言响应头与正文都不含给定敏感值。
func (c *ResponseChecker) NotLeaking(values ...string) *ResponseChecker {
	c.t.Helper()
	for _, value := range values {
		assert.NotContains(c.t, string(c.Body), value, "响应正文泄漏敏感值")
		for name, headerValues := range c.Header {
			assert.NotContains(c.t, strings.Join(headerValues, ","), value, "响应头 %s 泄漏敏感值", name)
		}
	}
	return c
}

// RequestID 返回响应回显的请求 ID。
func (c *ResponseChecker) RequestID() string { return c.Header.Get("X-Request-Id") }

// safetyHeaders 断言禁止缓存且回显本次请求 ID。
func (c *ResponseChecker) safetyHeaders() {
	assert.Equal(c.t, "no-store", c.Header.Get("Cache-Control"))
	assert.Equal(c.t, c.request.RequestID(), c.Header.Get("X-Request-Id"))
}

// decodeExact 解码正文并检查成员数量，拒绝多余字段。
func (c *ResponseChecker) decodeExact(target any, members int) {
	c.t.Helper()
	var raw map[string]any
	require.NoError(c.t, json.Unmarshal(c.Body, &raw), "响应不是 JSON：%s", c.Body)
	require.Len(c.t, raw, members, "响应成员：%s", c.Body)
	require.NoError(c.t, json.Unmarshal(c.Body, target))
}
