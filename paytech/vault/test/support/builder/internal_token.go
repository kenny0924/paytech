package builder

import (
	"crypto/rand"
	"encoding/hex"
	"maps"
	"time"
)

// TestInstanceID 是自动测试中 Vault 的实例 ID（VAULT_INSTANCE_ID），Token 的 inst 默认取它。
const TestInstanceID = "auto-test"

// InternalToken 描述一个内部调用 Token 的 JOSE 头与声明（D26）。
//
// template 给出合法 Token：iss=platform-support-service、aud=vault-service、inst=TestInstanceID、purpose=GENERAL、
// 有效期 300 秒；用例只覆盖与场景有关的成员。签名由 mocker/external.PlatformJWKS 完成，Build 不修复非法输入。
type InternalToken struct {
	header map[string]any
	claims map[string]any
}

// InternalTokenTemplate 返回以 caller 为 sub 的合法 Token 描述；kid 缺省时由签名方填写当前公钥。
func InternalTokenTemplate(caller string) *InternalToken {
	now := time.Now().Unix()
	return &InternalToken{
		header: map[string]any{"alg": "ES256", "typ": "JWT"},
		claims: map[string]any{
			"iss": "platform-support-service", "sub": caller, "aud": "vault-service", "inst": TestInstanceID,
			"purpose": "GENERAL", "jti": randomJWTID(), "iat": now, "exp": now + 300,
		},
	}
}

// Claim 设置声明；value 为 nil 时写入 JSON null。
func (b *InternalToken) Claim(name string, value any) *InternalToken {
	b.claims[name] = value
	return b
}

// WithoutClaim 删除声明。
func (b *InternalToken) WithoutClaim(name string) *InternalToken {
	delete(b.claims, name)
	return b
}

// HeaderParam 设置 JOSE 头成员。
func (b *InternalToken) HeaderParam(name string, value any) *InternalToken {
	b.header[name] = value
	return b
}

// IssuedAt 以相对当前时间的偏移设置 iat 与 exp（秒精度）。
func (b *InternalToken) IssuedAt(issuedOffset, lifetime time.Duration) *InternalToken {
	issued := time.Now().Add(issuedOffset).Unix()
	b.claims["iat"] = issued
	b.claims["exp"] = issued + int64(lifetime/time.Second)
	return b
}

// Header 返回 JOSE 头副本。
func (b *InternalToken) Header() map[string]any { return maps.Clone(b.header) }

// Claims 返回声明副本。
func (b *InternalToken) Claims() map[string]any { return maps.Clone(b.claims) }

// Bearer 设置 Authorization: Bearer <token>。
func (b *RequestBuilder) Bearer(token string) *RequestBuilder {
	return b.Header("Authorization", "Bearer "+token)
}

func randomJWTID() string {
	var raw [8]byte
	if _, err := rand.Read(raw[:]); err != nil {
		panic(err)
	}
	return "jti-" + hex.EncodeToString(raw[:])
}
