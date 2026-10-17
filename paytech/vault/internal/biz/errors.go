package biz

import (
	"context"
	"errors"
)

// ErrorCode 是对外稳定的错误码。
type ErrorCode string

const (
	InvalidRequest         ErrorCode = "INVALID_REQUEST"
	ValueTooLarge          ErrorCode = "VALUE_TOO_LARGE"
	UnsupportedDataType    ErrorCode = "UNSUPPORTED_DATA_TYPE"
	TokenModeNotAllowed    ErrorCode = "TOKEN_MODE_NOT_ALLOWED"
	AccessDenied           ErrorCode = "ACCESS_DENIED"
	TokenNotFound          ErrorCode = "TOKEN_NOT_FOUND"
	IdempotencyKeyConflict ErrorCode = "IDEMPOTENCY_KEY_CONFLICT"
	IntegrityCheckFailed   ErrorCode = "INTEGRITY_CHECK_FAILED"
	InternalError          ErrorCode = "INTERNAL_ERROR"
	DependencyUnavailable  ErrorCode = "DEPENDENCY_UNAVAILABLE"
	AuditUnavailable       ErrorCode = "AUDIT_UNAVAILABLE"
	PolicyVersionConflict  ErrorCode = "POLICY_VERSION_CONFLICT"
	PolicyEntryExists      ErrorCode = "POLICY_ENTRY_EXISTS"
	PolicyEntryNotFound    ErrorCode = "POLICY_ENTRY_NOT_FOUND"
	ValueNotJSONObject     ErrorCode = "VALUE_NOT_JSON_OBJECT"
	ValuePatternMismatch   ErrorCode = "VALUE_PATTERN_MISMATCH"
	ValuePatternInvalid    ErrorCode = "VALUE_PATTERN_INVALID"
	SampleValueInvalid     ErrorCode = "SAMPLE_VALUE_INVALID"
	// InternalTokenInvalid 表示内部调用 Token 缺失或不合法（VAULT_INTERNAL_AUTH_MODE 非 OFF 时）。
	InternalTokenInvalid ErrorCode = "INTERNAL_TOKEN_INVALID"
)

// ServiceError 是携带稳定错误码的领域错误，Error() 只返回安全的本地说明。
type ServiceError struct {
	Code ErrorCode
}

// NewError 创建领域错误；未登记的错误码收敛为 INTERNAL_ERROR。
func NewError(code ErrorCode) *ServiceError {
	if _, ok := safeMessages[code]; !ok {
		code = InternalError
	}
	return &ServiceError{Code: code}
}

func (e *ServiceError) Error() string {
	if e == nil {
		return safeMessages[InternalError]
	}
	message, ok := safeMessages[e.Code]
	if !ok {
		return safeMessages[InternalError]
	}
	return message
}

func (e *ServiceError) Is(target error) bool {
	other, ok := target.(*ServiceError)
	return ok && e != nil && other != nil && e.Code == other.Code
}

// CodeOf 取出错误码；非领域错误一律视为 INTERNAL_ERROR。
func CodeOf(err error) ErrorCode {
	var serviceError *ServiceError
	if errors.As(err, &serviceError) && serviceError != nil {
		if _, ok := safeMessages[serviceError.Code]; ok {
			return serviceError.Code
		}
	}
	return InternalError
}

// Sanitize 把任意错误收敛为安全的领域错误；上下文取消或超时视为依赖不可用。
func Sanitize(err error) error {
	if errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded) {
		return NewError(DependencyUnavailable)
	}
	return NewError(CodeOf(err))
}

// OutcomeForCode 把错误码归类为审计结果：调用方可纠正的为 DENIED，其余为 FAILED。
func OutcomeForCode(code ErrorCode) Outcome {
	switch code {
	case InvalidRequest, ValueTooLarge, UnsupportedDataType, TokenModeNotAllowed, AccessDenied, TokenNotFound, IdempotencyKeyConflict,
		PolicyVersionConflict, PolicyEntryExists, PolicyEntryNotFound, ValueNotJSONObject, ValuePatternMismatch, ValuePatternInvalid, SampleValueInvalid,
		InternalTokenInvalid:
		return Denied
	default:
		return Failed
	}
}

var safeMessages = map[ErrorCode]string{
	InvalidRequest:         "请求参数无效",
	ValueTooLarge:          "值超过32KiB限制",
	UnsupportedDataType:    "不支持的数据类型",
	TokenModeNotAllowed:    "Token模式不允许",
	AccessDenied:           "访问被拒绝",
	TokenNotFound:          "Token不存在",
	IdempotencyKeyConflict: "幂等键已被不同请求使用",
	IntegrityCheckFailed:   "完整性校验失败",
	InternalError:          "服务内部错误",
	DependencyUnavailable:  "依赖服务暂不可用",
	AuditUnavailable:       "访问审计不可用",
	PolicyVersionConflict:  "策略已被他人修改，请刷新后重试",
	PolicyEntryExists:      "策略条目已存在",
	PolicyEntryNotFound:    "策略条目不存在",
	ValueNotJSONObject:     "值不是合法的JSON对象",
	ValuePatternMismatch:   "值不符合数据类型的正则",
	ValuePatternInvalid:    "值格式与正则的组合无效或正则无法编译",
	SampleValueInvalid:     "测试样例缺失或不符合值格式",
	InternalTokenInvalid:   "内部调用凭证无效",
}
