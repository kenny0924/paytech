package internalauth

// Mode 是入站内部调用身份的校验模式（VAULT_INTERNAL_AUTH_MODE），与 Java internal-auth.mode 同义。
type Mode string

const (
	// Off 不校验 Token，只读取 X-Caller-Service；默认值，行为与引入前一致。
	Off Mode = "OFF"
	// Dual 合法 Token 优先；无 Token、Token 不合法或公钥不可用时回退 X-Caller-Service（迁移期）。
	Dual Mode = "DUAL"
	// Required 必须携带合法 Token：缺失或不合法 → 401，公钥不可用 → 503；不再读取 X-Caller-Service。
	Required Mode = "REQUIRED"
)

// ParseMode 解析模式 Code；未知值返回 false。
func ParseMode(value string) (Mode, bool) {
	switch Mode(value) {
	case Off, Dual, Required:
		return Mode(value), true
	default:
		return "", false
	}
}
