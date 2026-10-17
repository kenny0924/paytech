package server

import (
	"mime"
	"net/http"
	"regexp"
	"strings"

	"github.com/google/uuid"

	"github.com/paytech/vault-service/internal/biz"
)

// requestIDKey 是 Gin 上下文中 Request ID 的键。
const requestIDKey = "vault.requestID"

var requestIDPattern = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$`)

// requestID 采用唯一且合法的 X-Request-Id，否则生成 UUIDv4；替换不构成业务拒绝。
func requestID(h http.Header) string {
	values := h.Values("X-Request-Id")
	if len(values) == 1 && requestIDPattern.MatchString(values[0]) {
		return values[0]
	}
	return uuid.NewString()
}

// caller 返回唯一且合法的 X-Caller-Service，否则为 UNKNOWN。
func caller(h http.Header) string {
	values := h.Values("X-Caller-Service")
	if len(values) != 1 || !biz.IsCaller(values[0]) {
		return biz.UnknownCaller
	}
	return values[0]
}

// validateHeaders 校验业务请求公共头：调用方、Content-Type 与幂等键的数量和格式。
func validateHeaders(h http.Header, action biz.Action) error {
	if caller(h) == biz.UnknownCaller || !jsonContentType(h) {
		return biz.NewError(biz.InvalidRequest)
	}
	keys := h.Values("Idempotency-Key")
	if action == biz.ProtectAction && (len(keys) != 1 || !biz.IsIdempotencyKey(keys[0])) {
		return biz.NewError(biz.InvalidRequest)
	}
	// Reveal 不使用幂等键；若提供，仍不能绕过数量与格式校验。
	if action == biz.RevealAction && len(keys) > 0 && (len(keys) != 1 || !biz.IsIdempotencyKey(keys[0])) {
		return biz.NewError(biz.InvalidRequest)
	}
	return nil
}

// jsonContentType 只接受唯一的 application/json，参数仅允许 charset=utf-8。
func jsonContentType(h http.Header) bool {
	contentTypes := h.Values("Content-Type")
	if len(contentTypes) != 1 {
		return false
	}
	media, params, err := mime.ParseMediaType(contentTypes[0])
	if err != nil || media != "application/json" || len(params) > 1 {
		return false
	}
	for key, value := range params {
		if key != "charset" || !strings.EqualFold(value, "utf-8") {
			return false
		}
	}
	return true
}
