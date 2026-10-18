package biz

import "regexp"

var (
	callerPattern         = regexp.MustCompile(`^[a-z][a-z0-9-]{0,127}$`)
	codePattern           = regexp.MustCompile(`^[A-Z][A-Z0-9_]{0,63}$`)
	tokenPattern          = regexp.MustCompile(`^[A-Za-z0-9_-]{43}$`)
	idempotencyKeyPattern = regexp.MustCompile(`^[A-Za-z0-9._~-]{1,128}$`)
	traceIDPattern        = regexp.MustCompile(`^[0-9a-f]{32}$`)
)

// UnknownCaller 是调用方标识缺失或不合法时写入审计的占位值。
const UnknownCaller = "UNKNOWN"

// IsCaller 判断调用服务名格式：小写字母开头，最长 128。
func IsCaller(value string) bool { return callerPattern.MatchString(value) }

// IsCode 判断数据类型、用途等代码格式：大写字母开头，最长 64。
func IsCode(value string) bool { return codePattern.MatchString(value) }

// IsToken 判断 Vault Token 格式：43 位 base64url。
func IsToken(value string) bool { return tokenPattern.MatchString(value) }

// IsIdempotencyKey 判断幂等键格式：1～128 位 URL 安全字符。
func IsIdempotencyKey(value string) bool { return idempotencyKeyPattern.MatchString(value) }

// IsTraceID 判断 W3C Trace ID：32 位小写十六进制且不全为 0。
func IsTraceID(value string) bool {
	return traceIDPattern.MatchString(value) && value != "00000000000000000000000000000000"
}
