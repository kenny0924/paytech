package domain

import "errors"

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
)

type ServiceError struct {
	Code ErrorCode
}

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

func CodeOf(err error) ErrorCode {
	var serviceError *ServiceError
	if errors.As(err, &serviceError) && serviceError != nil {
		if _, ok := safeMessages[serviceError.Code]; ok {
			return serviceError.Code
		}
	}
	return InternalError
}

func OutcomeForCode(code ErrorCode) Outcome {
	switch code {
	case InvalidRequest, ValueTooLarge, UnsupportedDataType, TokenModeNotAllowed, AccessDenied, TokenNotFound, IdempotencyKeyConflict:
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
}
