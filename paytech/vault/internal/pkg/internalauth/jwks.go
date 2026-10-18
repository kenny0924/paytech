package internalauth

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"sync"
	"time"
)

const (
	// JWKSCacheTTL 是公钥缓存有效期（与 Java internal-auth.jwks-cache-ttl 默认值一致）。
	JWKSCacheTTL = 5 * time.Minute
	// JWKSTimeout 是读取 JWKS 的连接与读取总超时。
	JWKSTimeout = 2 * time.Second
	// JWKSForcedRefreshInterval 是两次因 kid 未命中触发刷新的最小间隔，避免伪造 kid 放大对 Platform 的请求。
	JWKSForcedRefreshInterval = 10 * time.Second

	maxJWKSBytes   = 64 * 1024
	p256CoordBytes = 32
)

// JWKSSource 从 Platform JWKS 端点读取 ES256 公钥。
//
// 按 TTL 缓存；过期后下一次查找先刷新，刷新失败时丢弃过期公钥并失败关闭。kid 未命中时强制刷新一次以接收密钥轮换，
// 强制刷新失败时保留现有缓存。只保留 P-256、用途为签名（或未声明）且算法为 ES256（或未声明）的公钥。
// 查找在互斥锁内完成，刷新期间的并发请求排队等待，与 Java JwksInternalTokenKeySource 一致。
type JWKSSource struct {
	url    string
	client *http.Client
	now    func() time.Time

	mu         sync.Mutex
	keys       map[string]*ecdsa.PublicKey
	fetchedAt  time.Time
	lastForced time.Time
}

// NewJWKSSource 创建公钥来源；client 为空时使用带 JWKSTimeout 的默认客户端，now 为空时使用系统时钟。
func NewJWKSSource(url string, client *http.Client, now func() time.Time) *JWKSSource {
	if client == nil {
		client = &http.Client{Timeout: JWKSTimeout}
	}
	if now == nil {
		now = time.Now
	}
	return &JWKSSource{url: url, client: client, now: now}
}

// Key 先查缓存；缓存过期或 kid 未命中时刷新。未知 kid 返回 (nil, nil)。
func (s *JWKSSource) Key(ctx context.Context, keyID string) (*ecdsa.PublicKey, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	now := s.now()
	if s.fetchedAt.IsZero() || !now.Before(s.fetchedAt.Add(JWKSCacheTTL)) {
		if err := s.refresh(ctx, now); err != nil {
			s.keys, s.fetchedAt = nil, time.Time{}
			return nil, err
		}
	}
	if key := s.keys[keyID]; key != nil || !s.forcedRefreshAllowed(now) {
		return key, nil
	}
	s.lastForced = now
	if err := s.refresh(ctx, now); err != nil {
		return nil, err
	}
	return s.keys[keyID], nil
}

func (s *JWKSSource) forcedRefreshAllowed(now time.Time) bool {
	return s.lastForced.IsZero() || !now.Before(s.lastForced.Add(JWKSForcedRefreshInterval))
}

// refresh 读取并替换缓存；失败时不修改缓存并返回 ErrUnavailable。
func (s *JWKSSource) refresh(ctx context.Context, now time.Time) error {
	document, err := s.load(ctx)
	if err != nil {
		return ErrUnavailable
	}
	loaded := make(map[string]*ecdsa.PublicKey, len(document.Keys))
	for _, jwk := range document.Keys {
		if key, ok := jwk.publicKey(); ok {
			loaded[jwk.Kid] = key
		}
	}
	s.keys, s.fetchedAt = loaded, now
	return nil
}

// jwkSet 是 JWKS 文档；未知成员忽略。
type jwkSet struct {
	Keys []jwk `json:"keys"`
}

// jwk 是一把公钥的受支持成员。
type jwk struct {
	Kty string `json:"kty"`
	Crv string `json:"crv"`
	Kid string `json:"kid"`
	Use string `json:"use"`
	Alg string `json:"alg"`
	X   string `json:"x"`
	Y   string `json:"y"`
}

var errJWKS = errors.New("jwks document unavailable")

// load 读取 JWKS：只接受 200 与不超过 64 KiB 的 JSON 对象。
func (s *JWKSSource) load(ctx context.Context) (jwkSet, error) {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, s.url, nil)
	if err != nil {
		return jwkSet{}, errJWKS
	}
	request.Header.Set("Accept", "application/json")
	response, err := s.client.Do(request)
	if err != nil {
		return jwkSet{}, errJWKS
	}
	defer func() { _ = response.Body.Close() }() // 只读响应，关闭失败不影响已解析的公钥。
	if response.StatusCode != http.StatusOK {
		return jwkSet{}, errJWKS
	}
	body, err := io.ReadAll(io.LimitReader(response.Body, maxJWKSBytes+1))
	if err != nil || len(body) > maxJWKSBytes {
		return jwkSet{}, errJWKS
	}
	var document jwkSet
	if json.Unmarshal(body, &document) != nil || document.Keys == nil {
		return jwkSet{}, errJWKS
	}
	return document, nil
}

// publicKey 只接受带 kid 的 P-256 签名公钥，坐标必须是曲线上的点。
func (k jwk) publicKey() (*ecdsa.PublicKey, bool) {
	if k.Kty != "EC" || k.Crv != "P-256" || !keyIDPattern.MatchString(k.Kid) ||
		(k.Use != "" && k.Use != "sig") || (k.Alg != "" && k.Alg != "ES256") {
		return nil, false
	}
	x, errX := segmentEncoding.DecodeString(k.X)
	y, errY := segmentEncoding.DecodeString(k.Y)
	if errX != nil || errY != nil || len(x) != p256CoordBytes || len(y) != p256CoordBytes {
		return nil, false
	}
	uncompressed := append(append([]byte{4}, x...), y...)
	key, err := ecdsa.ParseUncompressedPublicKey(elliptic.P256(), uncompressed)
	if err != nil {
		return nil, false
	}
	return key, true
}
