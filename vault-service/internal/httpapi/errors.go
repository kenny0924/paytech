package httpapi

import (
	"encoding/json"
	"net/http"
	"time"

	"github.com/paytech/vault-service/internal/domain"
)

type errorResponse struct {
	Type      string           `json:"type"`
	Title     string           `json:"title"`
	Detail    string           `json:"detail"`
	Code      domain.ErrorCode `json:"code"`
	RequestID string           `json:"requestId"`
	Timestamp string           `json:"timestamp"`
}

const problemContentType = "application/problem+json"
const problemTimestampLayout = "2006-01-02T15:04:05.000000Z"
const methodNotAllowedCode domain.ErrorCode = "METHOD_NOT_ALLOWED"
const methodNotAllowedDetail = "请求方法不受支持"

func statusFor(code domain.ErrorCode) int {
	switch code {
	case domain.InvalidRequest, domain.ValueTooLarge, domain.UnsupportedDataType, domain.TokenModeNotAllowed:
		return 400
	case domain.AccessDenied:
		return 403
	case domain.TokenNotFound:
		return 404
	case domain.IdempotencyKeyConflict:
		return 409
	case domain.DependencyUnavailable, domain.AuditUnavailable:
		return 503
	default:
		return 500
	}
}
func (s *server) writeError(w http.ResponseWriter, r *http.Request, status int, id string, code domain.ErrorCode) {
	w.Header().Set("Content-Type", problemContentType)
	w.Header().Set("Cache-Control", "no-store")
	w.Header().Set("X-Request-Id", id)
	if r.Method == http.MethodHead {
		w.WriteHeader(status)
		return
	}
	now := time.Now
	if s.Now != nil {
		now = s.Now
	}
	detail := domain.NewError(code).Error()
	if code == methodNotAllowedCode {
		detail = methodNotAllowedDetail
	}
	response := errorResponse{
		Type:      "about:blank",
		Title:     http.StatusText(status),
		Detail:    detail,
		Code:      code,
		RequestID: id,
		Timestamp: now().UTC().Format(problemTimestampLayout),
	}
	w.WriteHeader(status)
	// There are no dynamic marshalers in response DTOs. Network write failures
	// cannot change a committed application outcome or trigger a second audit.
	_ = json.NewEncoder(w).Encode(response)
}
func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	// There are no dynamic marshalers in response DTOs. Network write failures
	// cannot change a committed application outcome or trigger a second audit.
	_ = json.NewEncoder(w).Encode(value)
}
