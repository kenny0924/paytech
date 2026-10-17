// Package external 是外部依赖的测试替身：只配置 DTO 并记录调用，不替换被测生产链路。
package external

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"sync"

	"github.com/paytech/vault-service/test/support/builder"
)

// PlatformJWKS 模拟 Platform 的 JWKS 端点（真实 HTTP），并以对应私钥签发测试 Token（ES256）。
//
// 状态：已发布公钥、当前签名公钥、一个从不发布的公钥、可选的原始响应与状态码；Fetches 记录被读取次数。
// Reset 恢复初始状态，供每个用例结束时调用。
type PlatformJWKS struct {
	server *httptest.Server

	mu          sync.Mutex
	initial     signingKey
	unpublished signingKey
	published   []signingKey
	current     signingKey
	raw         []byte
	status      int
	fetches     int
	sequence    int
}

type signingKey struct {
	kid     string
	private *ecdsa.PrivateKey
}

// NewPlatformJWKS 生成初始密钥并在随机回环端口启动 JWKS 端点。
func NewPlatformJWKS() (*PlatformJWKS, error) {
	p := &PlatformJWKS{}
	var err error
	if p.initial, err = p.newKey(); err != nil {
		return nil, err
	}
	if p.unpublished, err = p.newKey(); err != nil {
		return nil, err
	}
	p.published, p.current = []signingKey{p.initial}, p.initial
	p.server = httptest.NewServer(http.HandlerFunc(p.serve))
	return p, nil
}

// URL 是 JWKS 地址（VAULT_INTERNAL_AUTH_JWKS_URL）。
func (p *PlatformJWKS) URL() string { return p.server.URL + "/platform/v1/internal-auth/jwks" }

// Close 停止端点。
func (p *PlatformJWKS) Close() { p.server.Close() }

// Fetches 返回 JWKS 被读取的次数。
func (p *PlatformJWKS) Fetches() int {
	p.mu.Lock()
	defer p.mu.Unlock()
	return p.fetches
}

// Respond 让端点返回指定状态码与原始正文（nil 表示按已发布公钥生成）。
func (p *PlatformJWKS) Respond(status int, raw []byte) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.status, p.raw = status, raw
}

// Rotate 新增并发布一把公钥，之后的 Sign 使用它；返回新 kid。
func (p *PlatformJWKS) Rotate() (string, error) {
	key, err := p.newKey()
	if err != nil {
		return "", err
	}
	p.mu.Lock()
	defer p.mu.Unlock()
	p.published = append(p.published, key)
	p.current = key
	return key.kid, nil
}

// Reset 恢复初始状态：只发布初始公钥、正常响应、读取计数清零。
func (p *PlatformJWKS) Reset() {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.published, p.current = []signingKey{p.initial}, p.initial
	p.raw, p.status, p.fetches = nil, 0, 0
}

// CurrentKeyID 返回当前签名公钥的 kid。
func (p *PlatformJWKS) CurrentKeyID() string {
	p.mu.Lock()
	defer p.mu.Unlock()
	return p.current.kid
}

// CurrentJWK 返回当前签名公钥的 JWK 成员，供用例拼装自定义 JWKS 文档。
func (p *PlatformJWKS) CurrentJWK() map[string]any {
	p.mu.Lock()
	defer p.mu.Unlock()
	return publicJWK(p.current)
}

// Sign 用当前公钥签名；头中缺少 kid 时填写当前 kid。
func (p *PlatformJWKS) Sign(token *builder.InternalToken) string {
	p.mu.Lock()
	key := p.current
	p.mu.Unlock()
	return sign(key, token)
}

// SignUnpublished 用从不发布的公钥签名（kid 未知）。
func (p *PlatformJWKS) SignUnpublished(token *builder.InternalToken) string {
	return sign(p.unpublished, token)
}

func (p *PlatformJWKS) serve(w http.ResponseWriter, _ *http.Request) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.fetches++
	body := p.raw
	if body == nil {
		keys := make([]map[string]any, 0, len(p.published))
		for _, key := range p.published {
			keys = append(keys, publicJWK(key))
		}
		body, _ = json.Marshal(map[string]any{"keys": keys}) // 固定结构，编码不会失败。
	}
	status := p.status
	if status == 0 {
		status = http.StatusOK
	}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_, _ = w.Write(body) // 测试端点：客户端断开不影响断言。
}

func (p *PlatformJWKS) newKey() (signingKey, error) {
	private, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return signingKey{}, err
	}
	p.mu.Lock()
	defer p.mu.Unlock()
	p.sequence++
	return signingKey{kid: fmt.Sprintf("auto-key-%d", p.sequence), private: private}, nil
}

func publicJWK(key signingKey) map[string]any {
	encoded, err := key.private.PublicKey.Bytes()
	if err != nil {
		panic(err)
	}
	// 未压缩点：0x04 || X(32) || Y(32)。
	return map[string]any{"kty": "EC", "crv": "P-256", "use": "sig", "alg": "ES256", "kid": key.kid,
		"x": encode(encoded[1:33]), "y": encode(encoded[33:65])}
}

func sign(key signingKey, token *builder.InternalToken) string {
	header := token.Header()
	if _, ok := header["kid"]; !ok {
		header["kid"] = key.kid
	}
	signingInput := encodeJSON(header) + "." + encodeJSON(token.Claims())
	digest := sha256.Sum256([]byte(signingInput))
	r, s, err := ecdsa.Sign(rand.Reader, key.private, digest[:])
	if err != nil {
		panic(err)
	}
	signature := make([]byte, 64)
	r.FillBytes(signature[:32])
	s.FillBytes(signature[32:])
	return signingInput + "." + encode(signature)
}

func encodeJSON(value map[string]any) string {
	raw, err := json.Marshal(value)
	if err != nil {
		panic(err)
	}
	return encode(raw)
}

func encode(raw []byte) string { return base64.RawURLEncoding.EncodeToString(raw) }
