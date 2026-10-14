// 密钥服务端口：由 biz 定义，local/test 实现位于 internal/pkg/localaes。

package biz

import "context"

// MACPurpose 区分 HMAC 用途，不同用途派生不同子密钥。
type MACPurpose string

const (
	MACStableIdentity MACPurpose = "stable-identity-v1"

	MACProtectRequest MACPurpose = "protect-request-hash-v1"
)

// EncryptedValue 是信封加密结果。
type EncryptedValue struct {
	Ciphertext []byte
	Nonce      []byte
	WrappedDEK []byte
	KeyRef     string
}

// KeyService 只承载密码学边界，不拥有业务授权或明文审计。
// 调用方拥有返回的明文，必须在使用后尽力清零；任何失败都不得返回明文。
type KeyService interface {
	Encrypt(ctx context.Context, aad, plaintext []byte) (EncryptedValue, error)
	Decrypt(ctx context.Context, aad []byte, encrypted EncryptedValue) ([]byte, error)
	MAC(ctx context.Context, purpose MACPurpose, material []byte) ([32]byte, error)
	Ready(ctx context.Context) error
}
