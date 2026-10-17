package biz

import (
	"context"
	"io"
	"sync"
	"time"
)

// ProtectMaterial 是 Protect 请求的 HMAC 材料：请求哈希用于幂等比对，指纹仅 STABLE 模式存在。
type ProtectMaterial struct {
	RequestHash [32]byte
	Fingerprint *[32]byte
}

// RecordCipherService 负责记录的信封加解密与 HMAC 计算，是唯一接触 KeyService 的组件。
//
// 方法均在事务外调用；明文与中间材料使用后尽力清零。
type RecordCipherService struct {
	keys     KeyService
	ids      IDGenerator
	random   io.Reader
	randomMu sync.Mutex
	clock    func() time.Time
}

// NewRecordCipherService 注入密钥服务、主键生成器、Token 随机源与时钟。
func NewRecordCipherService(keys KeyService, ids IDGenerator, random io.Reader, clock func() time.Time) *RecordCipherService {
	return &RecordCipherService{keys: keys, ids: ids, random: random, clock: clock}
}

// ProtectMaterial 计算请求哈希；STABLE 模式另算值身份指纹。
func (s *RecordCipherService) ProtectMaterial(ctx context.Context, caller string, in ProtectInput) (ProtectMaterial, error) {
	var result ProtectMaterial
	encoded, err := RequestMaterial(caller, in)
	if err != nil {
		return result, err
	}
	defer Wipe(encoded)
	if result.RequestHash, err = s.keys.MAC(ctx, MACProtectRequest, encoded); err != nil {
		return result, err
	}
	if in.TokenMode != Stable {
		return result, nil
	}
	result.Fingerprint, err = s.stableFingerprint(ctx, in)
	return result, err
}

// stableFingerprint 计算 STABLE 值身份 HMAC。
func (s *RecordCipherService) stableFingerprint(ctx context.Context, in ProtectInput) (*[32]byte, error) {
	stable, err := StableMaterial(in.DataType, in.Value)
	if err != nil {
		return nil, err
	}
	defer Wipe(stable)
	fingerprint, err := s.keys.MAC(ctx, MACStableIdentity, stable)
	if err != nil {
		return nil, err
	}
	return &fingerprint, nil
}

// SealRecord 分配主键与 Token，并以绑定记录身份的 AAD 加密值，生成待插入的新记录。
func (s *RecordCipherService) SealRecord(ctx context.Context, in ProtectInput, fingerprint *[32]byte) (Record, error) {
	id, err := NextID(ctx, s.ids)
	if err != nil {
		return Record{}, err
	}
	token, err := s.nextToken(ctx)
	if err != nil {
		return Record{}, err
	}
	aad, err := RecordAAD(id, token, in.DataType, in.TokenMode)
	if err != nil {
		return Record{}, err
	}
	defer Wipe(aad)
	plaintext := append([]byte(nil), in.Value...)
	defer Wipe(plaintext)
	encrypted, err := s.keys.Encrypt(ctx, aad, plaintext)
	if err != nil {
		return Record{}, err
	}
	if len(encrypted.Nonce) != 12 {
		return Record{}, NewError(InternalError)
	}
	now := s.clock().UTC()
	return Record{ID: id, Token: token, DataType: in.DataType, TokenMode: in.TokenMode, ValueFingerprint: fingerprint, Ciphertext: encrypted.Ciphertext, EncryptionNonce: [12]byte(encrypted.Nonce), WrappedDEK: encrypted.WrappedDEK, KMSKeyRef: encrypted.KeyRef, CreateTime: now, UpdateTime: now}, nil
}

// OpenRecord 解密记录；调用方拥有返回的明文并负责清零。
func (s *RecordCipherService) OpenRecord(ctx context.Context, record Record) ([]byte, error) {
	aad, err := RecordAAD(record.ID, record.Token, record.DataType, record.TokenMode)
	if err != nil {
		return nil, err
	}
	defer Wipe(aad)
	return s.keys.Decrypt(ctx, aad, EncryptedValue{Ciphertext: record.Ciphertext, Nonce: record.EncryptionNonce[:], WrappedDEK: record.WrappedDEK, KeyRef: record.KMSKeyRef})
}

// nextToken 串行读取随机源生成 Token。
func (s *RecordCipherService) nextToken(ctx context.Context) (string, error) {
	s.randomMu.Lock()
	defer s.randomMu.Unlock()
	if err := ctx.Err(); err != nil {
		return "", Sanitize(err)
	}
	return NewToken(s.random)
}
