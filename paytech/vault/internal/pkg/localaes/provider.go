// Package localaes 仅供配置边界批准的单实例 local/test 模式使用，不是生产 KMS。
package localaes

import (
	"context"
	"crypto/aes"
	"crypto/cipher"
	"crypto/hkdf"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"io"
	"sync"

	"github.com/paytech/vault-service/internal/biz"
)

const wrapLabel = "local-wrap-v1"

type Provider struct {
	root      [32]byte
	keyRef    string
	random    io.Reader
	entropyMu sync.Mutex
}

var _ biz.KeyService = (*Provider)(nil)

// New 复制已验证的进程 Secret；调用方仍负责清零自己的原始副本。
// runtimeMode 与 Key ID 格式由 config 验证，不提供默认密钥或轮换行为。
func New(root []byte, keyID string) (*Provider, error) {
	if len(root) != 32 || keyID == "" {
		return nil, biz.NewError(biz.InternalError)
	}
	p := &Provider{keyRef: "local-aes://" + keyID, random: rand.Reader}
	copy(p.root[:], root)
	return p, nil
}

func (p *Provider) Ready(ctx context.Context) error { return ctx.Err() }

func (p *Provider) derive(label string) ([]byte, error) {
	key, err := hkdf.Key(sha256.New, p.root[:], nil, label, 32)
	if err != nil {
		return nil, biz.NewError(biz.InternalError)
	}
	return key, nil
}

func (p *Provider) MAC(ctx context.Context, purpose biz.MACPurpose, material []byte) ([32]byte, error) {
	if err := ctx.Err(); err != nil {
		return [32]byte{}, err
	}
	if purpose != biz.MACStableIdentity && purpose != biz.MACProtectRequest {
		return [32]byte{}, biz.NewError(biz.InternalError)
	}
	key, err := p.derive(string(purpose))
	if err != nil {
		return [32]byte{}, err
	}
	defer wipe(key)
	mac := hmac.New(sha256.New, key)
	mac.Write(material)
	var result [32]byte
	mac.Sum(result[:0])
	if err := ctx.Err(); err != nil {
		wipe(result[:])
		return [32]byte{}, err
	}
	return result, nil
}

func (p *Provider) Encrypt(ctx context.Context, aad, plaintext []byte) (biz.EncryptedValue, error) {
	if err := ctx.Err(); err != nil {
		return biz.EncryptedValue{}, err
	}
	// 分别分配 DEK、数据 Nonce 和 Wrap Nonce，每次加密消耗 56 字节新熵。
	entropy := make([]byte, 56)
	defer wipe(entropy)
	if err := p.readEntropy(ctx, entropy); err != nil {
		return biz.EncryptedValue{}, err
	}
	data, err := newGCM(entropy[:32])
	if err != nil {
		return biz.EncryptedValue{}, err
	}
	wrapped, err := p.wrap(aad, entropy[:32], entropy[44:])
	if err != nil {
		return biz.EncryptedValue{}, err
	}
	nonce := append([]byte(nil), entropy[32:44]...)
	encrypted := biz.EncryptedValue{Ciphertext: data.Seal(nil, nonce, plaintext, aad), Nonce: nonce, WrappedDEK: wrapped, KeyRef: p.keyRef}
	if err := ctx.Err(); err != nil {
		return biz.EncryptedValue{}, err
	}
	return encrypted, nil
}

func (p *Provider) readEntropy(ctx context.Context, dst []byte) error {
	p.entropyMu.Lock()
	defer p.entropyMu.Unlock()
	if err := ctx.Err(); err != nil {
		return err
	}
	if _, err := io.ReadFull(p.random, dst); err != nil {
		return biz.NewError(biz.InternalError)
	}
	return ctx.Err()
}

func (p *Provider) wrap(aad, dek, nonce []byte) ([]byte, error) {
	key, err := p.derive(wrapLabel)
	if err != nil {
		return nil, err
	}
	defer wipe(key)
	gcm, err := newGCM(key)
	if err != nil {
		return nil, err
	}
	envelope := make([]byte, 13, 61)
	envelope[0] = 1
	copy(envelope[1:], nonce)
	return gcm.Seal(envelope, nonce, dek, wrapAAD(aad)), nil
}

func (p *Provider) Decrypt(ctx context.Context, aad []byte, encrypted biz.EncryptedValue) ([]byte, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	if encrypted.KeyRef != p.keyRef || len(encrypted.Nonce) != 12 || len(encrypted.WrappedDEK) != 61 || encrypted.WrappedDEK[0] != 1 || len(encrypted.Ciphertext) < 16 {
		return nil, biz.NewError(biz.IntegrityCheckFailed)
	}
	dek, err := p.unwrap(aad, encrypted.WrappedDEK)
	if err != nil {
		return nil, err
	}
	defer wipe(dek)
	gcm, err := newGCM(dek)
	if err != nil {
		return nil, err
	}
	plaintext, err := gcm.Open(nil, encrypted.Nonce, encrypted.Ciphertext, aad)
	if err != nil {
		wipe(plaintext)
		return nil, biz.NewError(biz.IntegrityCheckFailed)
	}
	if err := ctx.Err(); err != nil {
		wipe(plaintext)
		return nil, err
	}
	return plaintext, nil
}

func (p *Provider) unwrap(aad, envelope []byte) ([]byte, error) {
	key, err := p.derive(wrapLabel)
	if err != nil {
		return nil, err
	}
	defer wipe(key)
	gcm, err := newGCM(key)
	if err != nil {
		return nil, err
	}
	dek, err := gcm.Open(nil, envelope[1:13], envelope[13:], wrapAAD(aad))
	if err != nil {
		wipe(dek)
		return nil, biz.NewError(biz.IntegrityCheckFailed)
	}
	return dek, nil
}

func newGCM(key []byte) (cipher.AEAD, error) {
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, biz.NewError(biz.InternalError)
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, biz.NewError(biz.InternalError)
	}
	return gcm, nil
}

func wrapAAD(aad []byte) []byte { return append([]byte("vault-local-wrap-v1"), aad...) }

// wipe 是尽力擦除；Go 的 GC、密码库内部副本和密钥调度不提供可证明的内存销毁。
//
//go:noinline
func wipe(value []byte) { clear(value) }
