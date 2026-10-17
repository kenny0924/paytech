// Package internalauth 校验 Platform 签发的内部调用 Token（D26），与 Java paytech-internal-auth 的声明格式一致。
//
// Token 为 ES256 JWS Compact：iss 固定 platform-support-service，sub 为调用方服务名，aud 为唯一目标服务名，
// inst 为实例 ID，purpose 为大写 Code，jti 唯一，exp - iat 不超过 300 秒。公钥来自 Platform JWKS。
// 只用 Go 标准库实现：Vault 现有依赖中没有 JOSE 库，不为此引入新依赖。
package internalauth

import (
	"context"
	"crypto/ecdsa"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"math/big"
	"regexp"
	"strings"
	"time"
)

const (
	// Issuer 是唯一签发方。
	Issuer = "platform-support-service"
	// MaxLifetime 是 exp - iat 的上限。
	MaxLifetime = 300 * time.Second
	// ClockSkew 是 iat、nbf 允许超前当前时间的最大偏差；exp 严格判定。
	ClockSkew = 60 * time.Second

	maxTokenLength = 4096
	es256SigLength = 64
)

var (
	// ErrInvalid 表示 Token 缺失、格式、签名或声明不合法；调用方应返回 401。
	ErrInvalid = errors.New("internal token invalid")
	// ErrUnavailable 表示校验公钥暂不可得；调用方失败关闭（503），不得放行。
	ErrUnavailable = errors.New("internal token keys unavailable")

	keyIDPattern       = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$`)
	serviceNamePattern = regexp.MustCompile(`^[a-z][a-z0-9-]{0,63}$`)
	instanceIDPattern  = regexp.MustCompile(`^[a-z0-9][a-z0-9-]{0,63}$`)
	purposePattern     = regexp.MustCompile(`^[A-Z][A-Z0-9_]{0,63}$`)
	jwtIDPattern       = regexp.MustCompile(`^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$`)

	segmentEncoding = base64.RawURLEncoding.Strict()
)

// IsInstanceID 判断实例 ID 格式（与 Token inst 声明相同）。
func IsInstanceID(value string) bool { return instanceIDPattern.MatchString(value) }

// KeySource 按 kid 查找 ES256 公钥；未知 kid 返回 (nil, nil)，来源不可用返回 ErrUnavailable。
type KeySource interface {
	Key(ctx context.Context, keyID string) (*ecdsa.PublicKey, error)
}

// Identity 是已校验 Token 的调用方身份。
type Identity struct {
	Caller  string
	Purpose string
	TokenID string
}

// Verifier 校验结构、签名、声明与时间；aud 必须等于本服务名，inst 必须等于本实例 ID。
type Verifier struct {
	keys     KeySource
	audience string
	instance string
	now      func() time.Time
}

// NewVerifier 创建校验器；now 为空时使用系统时钟。
func NewVerifier(keys KeySource, audience, instance string, now func() time.Time) *Verifier {
	if now == nil {
		now = time.Now
	}
	return &Verifier{keys: keys, audience: audience, instance: instance, now: now}
}

// header 是受支持的 JOSE 头；crit 出现即拒绝（不支持任何扩展参数）。
type header struct {
	Alg  string          `json:"alg"`
	Kid  string          `json:"kid"`
	Crit json.RawMessage `json:"crit"`
}

// claims 是 Token 声明；数字时间只接受整数秒。
type claims struct {
	Iss     string          `json:"iss"`
	Sub     string          `json:"sub"`
	Aud     json.RawMessage `json:"aud"`
	Inst    string          `json:"inst"`
	Purpose string          `json:"purpose"`
	Jti     string          `json:"jti"`
	Iat     *json.Number    `json:"iat"`
	Exp     *json.Number    `json:"exp"`
	Nbf     *json.Number    `json:"nbf"`
}

// Verify 依次校验结构、签名、声明与时间，返回调用方身份；失败返回 ErrInvalid 或 ErrUnavailable。
func (v *Verifier) Verify(ctx context.Context, compact string) (Identity, error) {
	segments, err := split(compact)
	if err != nil {
		return Identity{}, err
	}
	key, err := v.keyFor(ctx, segments[0])
	if err != nil {
		return Identity{}, err
	}
	if err := verifySignature(key, segments); err != nil {
		return Identity{}, err
	}
	var c claims
	if err := decodeSegment(segments[1], &c); err != nil {
		return Identity{}, err
	}
	if err := v.checkClaims(c); err != nil {
		return Identity{}, err
	}
	return Identity{Caller: c.Sub, Purpose: c.Purpose, TokenID: c.Jti}, nil
}

// split 拆出三段非空的 JWS Compact 片段，并限制总长度。
func split(compact string) ([]string, error) {
	if compact == "" || len(compact) > maxTokenLength {
		return nil, ErrInvalid
	}
	segments := strings.Split(compact, ".")
	if len(segments) != 3 || segments[0] == "" || segments[1] == "" || segments[2] == "" {
		return nil, ErrInvalid
	}
	return segments, nil
}

// keyFor 只接受 ES256、合法 kid 且无 crit 的头，并取得对应公钥。
func (v *Verifier) keyFor(ctx context.Context, encodedHeader string) (*ecdsa.PublicKey, error) {
	var h header
	if err := decodeSegment(encodedHeader, &h); err != nil {
		return nil, err
	}
	if h.Alg != "ES256" || h.Crit != nil || !keyIDPattern.MatchString(h.Kid) {
		return nil, ErrInvalid
	}
	key, err := v.keys.Key(ctx, h.Kid)
	if err != nil {
		return nil, ErrUnavailable
	}
	if key == nil {
		return nil, ErrInvalid
	}
	return key, nil
}

// verifySignature 校验 ES256 签名：R||S 各 32 字节，摘要为 SHA-256(header.payload)。
func verifySignature(key *ecdsa.PublicKey, segments []string) error {
	signature, err := segmentEncoding.DecodeString(segments[2])
	if err != nil || len(signature) != es256SigLength {
		return ErrInvalid
	}
	digest := sha256.Sum256([]byte(segments[0] + "." + segments[1]))
	r := new(big.Int).SetBytes(signature[:es256SigLength/2])
	s := new(big.Int).SetBytes(signature[es256SigLength/2:])
	if !ecdsa.Verify(key, digest[:], r, s) {
		return ErrInvalid
	}
	return nil
}

// decodeSegment 严格 base64url（无填充）解码并解析唯一 JSON 对象。
func decodeSegment(segment string, target any) error {
	raw, err := segmentEncoding.DecodeString(segment)
	if err != nil {
		return ErrInvalid
	}
	decoder := json.NewDecoder(strings.NewReader(string(raw)))
	decoder.UseNumber()
	if decoder.Decode(target) != nil || decoder.More() {
		return ErrInvalid
	}
	return nil
}

// checkClaims 校验签发方、受众、实例与格式，再校验时间窗口。
func (v *Verifier) checkClaims(c claims) error {
	if c.Iss != Issuer || !singleAudience(c.Aud, v.audience) || c.Inst != v.instance {
		return ErrInvalid
	}
	if !serviceNamePattern.MatchString(c.Sub) || !instanceIDPattern.MatchString(c.Inst) ||
		!purposePattern.MatchString(c.Purpose) || !jwtIDPattern.MatchString(c.Jti) {
		return ErrInvalid
	}
	return v.checkTime(c)
}

// checkTime：0 < exp - iat ≤ 300s；当前时间早于 exp；iat、nbf 最多超前 60 秒。
func (v *Verifier) checkTime(c claims) error {
	issuedAt, okIat := seconds(c.Iat)
	expiresAt, okExp := seconds(c.Exp)
	if !okIat || !okExp {
		return ErrInvalid
	}
	lifetime := expiresAt.Sub(issuedAt)
	if lifetime <= 0 || lifetime > MaxLifetime {
		return ErrInvalid
	}
	now := v.now()
	latest := now.Add(ClockSkew)
	if !now.Before(expiresAt) || issuedAt.After(latest) {
		return ErrInvalid
	}
	if c.Nbf != nil {
		notBefore, ok := seconds(c.Nbf)
		if !ok || notBefore.After(latest) {
			return ErrInvalid
		}
	}
	return nil
}

// singleAudience 接受字符串或只含一个字符串的数组，且等于期望受众。
func singleAudience(raw json.RawMessage, expected string) bool {
	var single string
	if json.Unmarshal(raw, &single) == nil {
		return single == expected
	}
	var list []string
	if json.Unmarshal(raw, &list) != nil || len(list) != 1 {
		return false
	}
	return list[0] == expected
}

// seconds 把整数秒 NumericDate 转为时间；缺失、小数或越界视为不合法。
func seconds(value *json.Number) (time.Time, bool) {
	if value == nil {
		return time.Time{}, false
	}
	parsed, err := value.Int64()
	if err != nil || parsed <= 0 {
		return time.Time{}, false
	}
	return time.Unix(parsed, 0), true
}
