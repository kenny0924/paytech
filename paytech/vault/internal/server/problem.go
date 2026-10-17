package server

import (
	"encoding/json"
	"net/http"
	"time"

	"github.com/paytech/vault-service/internal/biz"
)

const (
	problemContentType     = "application/problem+json"
	problemTimestampLayout = "2006-01-02T15:04:05.000000Z"

	methodNotAllowedDetail = "请求方法不受支持"
)

// methodNotAllowedCode 只用于接入层 405，不属于领域错误码。
const (
	methodNotAllowedCode biz.ErrorCode = "METHOD_NOT_ALLOWED"
)

// problemBody 是 RFC 9457-based Code-MoR Problem，固定六个成员。
type problemBody struct {
	Type      string        `json:"type"`
	Title     string        `json:"title"`
	Detail    string        `json:"detail"`
	Code      biz.ErrorCode `json:"code"`
	RequestID string        `json:"requestId"`
	Timestamp string        `json:"timestamp"`
}

// statusFor 把错误码映射为 HTTP 状态码。
func statusFor(code biz.ErrorCode) int {
	switch code {
	case biz.InvalidRequest, biz.ValueTooLarge, biz.UnsupportedDataType, biz.TokenModeNotAllowed,
		biz.ValueNotJSONObject, biz.ValuePatternMismatch, biz.ValuePatternInvalid, biz.SampleValueInvalid:
		return http.StatusBadRequest
	case biz.AccessDenied:
		return http.StatusForbidden
	case biz.TokenNotFound, biz.PolicyEntryNotFound:
		return http.StatusNotFound
	case biz.IdempotencyKeyConflict, biz.PolicyVersionConflict, biz.PolicyEntryExists:
		return http.StatusConflict
	case biz.DependencyUnavailable, biz.AuditUnavailable:
		return http.StatusServiceUnavailable
	default:
		return http.StatusInternalServerError
	}
}

// problemWriter 写出 Problem 响应；now 为空时使用系统时钟。
type problemWriter struct{ now func() time.Time }

// write 写出 Problem；HEAD 请求只写状态与头，不读时钟、不写正文。detail 只用本地安全说明。
func (p problemWriter) write(w http.ResponseWriter, r *http.Request, status int, requestID string, code biz.ErrorCode) {
	header := w.Header()
	header.Set("Content-Type", problemContentType)
	header.Set("Cache-Control", "no-store")
	header.Set("X-Request-Id", requestID)
	if r.Method == http.MethodHead {
		w.WriteHeader(status)
		return
	}
	detail := biz.NewError(code).Error()
	if code == methodNotAllowedCode {
		detail = methodNotAllowedDetail
	}
	body := problemBody{Type: "about:blank", Title: http.StatusText(status), Detail: detail, Code: code, RequestID: requestID, Timestamp: p.clock().UTC().Format(problemTimestampLayout)}
	w.WriteHeader(status)
	// 响应体没有动态序列化器；网络写失败不能改变已提交的业务结果，也不触发二次审计。
	_ = json.NewEncoder(w).Encode(body)
}

func (p problemWriter) clock() time.Time {
	if p.now == nil {
		return time.Now()
	}
	return p.now()
}
