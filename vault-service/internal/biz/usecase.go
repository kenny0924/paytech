package biz

import (
	"unicode/utf8"
)

// MaxValueBytes 是受保护值的 UTF-8 字节上限（32KiB）。
const MaxValueBytes = 32 * 1024

// Authorizer 是静态 Allowlist 策略。
type Authorizer interface {
	// DataTypeMode 返回数据类型登记的 Token 模式；未登记返回 false。
	DataTypeMode(dataType string) (TokenMode, bool)

	// Authorize 判断调用方能否以指定用途对数据类型执行动作。
	Authorize(caller string, action Action, dataType, purpose string) bool

	// Version 返回生效策略版本，写入审计。
	Version() string
}

// validateProtect 校验 Protect 输入格式；值超限单独返回 VALUE_TOO_LARGE。
func validateProtect(a Attempt, in ProtectInput) error {
	if !IsCaller(a.Caller) || !IsCode(in.DataType) || !IsCode(in.Purpose) || !IsIdempotencyKey(in.IdempotencyKey) || !utf8.Valid(in.Value) {
		return NewError(InvalidRequest)
	}
	if in.TokenMode != Reference && in.TokenMode != Stable {
		return NewError(InvalidRequest)
	}
	if len(in.Value) > MaxValueBytes {
		return NewError(ValueTooLarge)
	}
	return nil
}

// validateReveal 校验 Reveal 输入格式。
func validateReveal(a Attempt, in RevealInput) error {
	if !IsCaller(a.Caller) || !IsCode(in.Purpose) || !IsToken(in.Token) {
		return NewError(InvalidRequest)
	}
	return nil
}

// withPolicyVersion 在投影上记录本次判定所用的策略版本。
func withPolicyVersion(p *AuditProjection, policy Authorizer) {
	version := policy.Version()
	p.PolicyVersion = &version
}
