package httpapi

import (
	"crypto/rand"
	"fmt"
	"mime"
	"net/http"
	"regexp"
	"strings"

	"github.com/paytech/vault-service/internal/domain"
)

var (
	callerPattern         = regexp.MustCompile(`^[a-z][a-z0-9-]{0,127}$`)
	requestIDPattern      = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$`)
	idempotencyKeyPattern = regexp.MustCompile(`^[A-Za-z0-9._~-]+$`)
	codePattern           = regexp.MustCompile(`^[A-Z][A-Z0-9_]{0,63}$`)
	tokenPattern          = regexp.MustCompile(`^[A-Za-z0-9_-]{43}$`)
)

func requestID(h http.Header) string {
	values := h.Values("X-Request-Id")
	if len(values) == 1 && requestIDPattern.MatchString(values[0]) {
		return values[0]
	}
	var raw [16]byte
	_, _ = rand.Read(raw[:])
	raw[6] = (raw[6] & 0x0f) | 0x40
	raw[8] = (raw[8] & 0x3f) | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", raw[:4], raw[4:6], raw[6:8], raw[8:10], raw[10:])
}
func validIdempotencyKey(value string) bool {
	return len(value) <= 128 && idempotencyKeyPattern.MatchString(value)
}
func caller(h http.Header) string {
	values := h.Values("X-Caller-Service")
	if len(values) != 1 || !callerPattern.MatchString(values[0]) {
		return "UNKNOWN"
	}
	return values[0]
}
func validateHeaders(h http.Header, action domain.Action) error {
	if caller(h) == "UNKNOWN" {
		return domain.NewError(domain.InvalidRequest)
	}
	contentTypes := h.Values("Content-Type")
	if len(contentTypes) != 1 {
		return domain.NewError(domain.InvalidRequest)
	}
	media, params, err := mime.ParseMediaType(contentTypes[0])
	if err != nil || media != "application/json" || len(params) > 1 {
		return domain.NewError(domain.InvalidRequest)
	}
	for key, value := range params {
		if key != "charset" || !strings.EqualFold(value, "utf-8") {
			return domain.NewError(domain.InvalidRequest)
		}
	}
	keys := h.Values("Idempotency-Key")
	if action == domain.ProtectAction && (len(keys) != 1 || !validIdempotencyKey(keys[0])) {
		return domain.NewError(domain.InvalidRequest)
	}
	// Reveal does not consume an idempotency key; if supplied, it still cannot
	// bypass safety-header multiplicity or format validation.
	if action == domain.RevealAction && len(keys) > 0 && (len(keys) != 1 || !validIdempotencyKey(keys[0])) {
		return domain.NewError(domain.InvalidRequest)
	}
	return nil
}
