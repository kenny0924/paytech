package server

import (
	"context"
	"errors"
	"mime"
	"net/http"
	"regexp"
	"strings"

	"github.com/google/uuid"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/internal/pkg/internalauth"
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

const (
	callerHeader        = "X-Caller-Service"
	authorizationHeader = "Authorization"
	bearerPrefix        = "bearer "
)

// TokenVerifier 校验内部调用 Token（由 internalauth.Verifier 实现）。
type TokenVerifier interface {
	Verify(ctx context.Context, compact string) (internalauth.Identity, error)
}

// InternalAuth 是入站调用方身份的来源配置；零值等同 OFF。
type InternalAuth struct {
	Mode     internalauth.Mode
	Verifier TokenVerifier
}

// callerOf 按模式确定调用方（D26），与 Java InternalCallerAuthenticator 的判定一致：
//
//   - OFF（默认）：只读 X-Caller-Service，行为与引入 Token 前相同；
//   - DUAL：合法 Token 的 sub 优先；无 Token、Token 不合法或公钥不可用时回退 X-Caller-Service；
//   - REQUIRED：只认 Token；缺失或不合法 → INTERNAL_TOKEN_INVALID（401），公钥不可用 → DEPENDENCY_UNAVAILABLE（503）。
//
// 拒绝时调用方记为 UNKNOWN。访问规则的 Caller 维度不变，只是取值来源不同。
func (a InternalAuth) callerOf(req *http.Request) (string, error) {
	if a.Verifier == nil || (a.Mode != internalauth.Dual && a.Mode != internalauth.Required) {
		return caller(req.Header), nil
	}
	identity, err := a.verify(req)
	if err == nil {
		return identity.Caller, nil
	}
	if a.Mode == internalauth.Dual {
		return caller(req.Header), nil
	}
	if errors.Is(err, internalauth.ErrUnavailable) {
		return biz.UnknownCaller, biz.NewError(biz.DependencyUnavailable)
	}
	return biz.UnknownCaller, biz.NewError(biz.InternalTokenInvalid)
}

// verify 取出唯一的 Bearer Token 并校验；缺失视为不合法。
func (a InternalAuth) verify(req *http.Request) (internalauth.Identity, error) {
	token, ok := bearerToken(req.Header)
	if !ok {
		return internalauth.Identity{}, internalauth.ErrInvalid
	}
	return a.Verifier.Verify(req.Context(), token)
}

// bearerToken 只接受唯一的 Authorization 头，方案名不区分大小写。
func bearerToken(h http.Header) (string, bool) {
	values := h.Values(authorizationHeader)
	if len(values) != 1 || len(values[0]) <= len(bearerPrefix) || !strings.EqualFold(values[0][:len(bearerPrefix)], bearerPrefix) {
		return "", false
	}
	token := strings.TrimSpace(values[0][len(bearerPrefix):])
	return token, token != ""
}

// caller 返回唯一且合法的 X-Caller-Service，否则为 UNKNOWN。
func caller(h http.Header) string {
	values := h.Values(callerHeader)
	if len(values) != 1 || !biz.IsCaller(values[0]) {
		return biz.UnknownCaller
	}
	return values[0]
}

// validateHeaders 校验业务请求公共头：调用方、Content-Type 与幂等键的数量和格式。
func validateHeaders(h http.Header, callerID string, action biz.Action) error {
	if callerID == biz.UnknownCaller || !jsonContentType(h) {
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
