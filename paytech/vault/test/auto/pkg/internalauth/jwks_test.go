package internalauth_test

import (
	"context"
	"encoding/json"
	"errors"
	"maps"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/paytech/vault-service/internal/pkg/internalauth"
	"github.com/paytech/vault-service/test/support/mocker/external"
)

// Platform JWKS 公钥缓存：TTL、kid 未命中强制刷新（限速）、失败关闭与公钥过滤，与 Java JwksInternalTokenKeySource 一致。
//
// 被测入口：internalauth.JWKSSource.Key（真实 HTTP 到 PlatformJWKS；时钟为确定性替身）。

// clock 是可前进的确定性时钟。
type clock struct {
	mu  sync.Mutex
	now time.Time
}

func (c *clock) Now() time.Time {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.now
}

func (c *clock) Advance(d time.Duration) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.now = c.now.Add(d)
}

func setUpSource(t *testing.T) (*external.PlatformJWKS, *internalauth.JWKSSource, *clock) {
	t.Helper()
	jwks, err := external.NewPlatformJWKS()
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(jwks.Close)
	c := &clock{now: time.Date(2026, 10, 17, 0, 0, 0, 0, time.UTC)}
	return jwks, internalauth.NewJWKSSource(jwks.URL(), nil, c.Now), c
}

func TestJWKSCachesKeysWithinTTLAndRefreshesAfterExpiry(t *testing.T) {
	jwks, source, c := setUpSource(t)
	kid := jwks.CurrentKeyID()

	requireKey(t, source, kid)
	c.Advance(internalauth.JWKSCacheTTL - time.Second)
	requireKey(t, source, kid)
	assertFetches(t, jwks, 1)

	c.Advance(time.Second)
	requireKey(t, source, kid)
	assertFetches(t, jwks, 2)
}

func TestJWKSExpiredCacheRefreshFailureDropsKeysAndFailsClosed(t *testing.T) {
	jwks, source, c := setUpSource(t)
	kid := jwks.CurrentKeyID()
	requireKey(t, source, kid)

	jwks.Respond(503, nil)
	c.Advance(internalauth.JWKSCacheTTL)
	assertUnavailable(t, source, kid)

	// 过期公钥已丢弃：下一次查找重新读取，恢复后可用。
	jwks.Respond(0, nil)
	requireKey(t, source, kid)
	assertFetches(t, jwks, 3)
}

func TestJWKSUnknownKidForcesRateLimitedRefresh(t *testing.T) {
	jwks, source, c := setUpSource(t)
	requireKey(t, source, jwks.CurrentKeyID())

	rotated, err := jwks.Rotate()
	if err != nil {
		t.Fatal(err)
	}
	requireKey(t, source, rotated)
	assertFetches(t, jwks, 2)

	// 间隔内再次未命中不触发读取，直接视为未知 kid。
	assertUnknown(t, source, "never-published")
	assertFetches(t, jwks, 2)

	c.Advance(internalauth.JWKSForcedRefreshInterval)
	assertUnknown(t, source, "never-published")
	assertFetches(t, jwks, 3)
}

func TestJWKSForcedRefreshFailureKeepsCachedKeys(t *testing.T) {
	jwks, source, _ := setUpSource(t)
	kid := jwks.CurrentKeyID()
	requireKey(t, source, kid)

	jwks.Respond(500, nil)
	assertUnavailable(t, source, "rotated-but-unreachable")
	requireKey(t, source, kid)
}

func TestJWKSRejectsUnusableDocuments(t *testing.T) {
	for name, tc := range map[string]struct {
		status int
		body   string
	}{
		"server error":  {500, `{"keys":[]}`},
		"not found":     {404, `{"keys":[]}`},
		"invalid json":  {200, `{"keys":[`},
		"keys missing":  {200, `{"jwks":[]}`},
		"keys not list": {200, `{"keys":{}}`},
		"oversized":     {200, `{"keys":[],"padding":"` + strings.Repeat("x", 64*1024) + `"}`},
	} {
		t.Run(name, func(t *testing.T) {
			jwks, source, _ := setUpSource(t)
			jwks.Respond(tc.status, []byte(tc.body))
			assertUnavailable(t, source, jwks.CurrentKeyID())
		})
	}
}

func TestJWKSIgnoresKeysThatAreNotP256SigningKeys(t *testing.T) {
	jwks, _, _ := setUpSource(t)
	base := jwks.CurrentJWK()
	// variant 复制当前公钥并改写一个成员（value 为 nil 时删除该成员），以 kid 区分。
	variant := func(kid, member string, value any) map[string]any {
		key := maps.Clone(base)
		key["kid"] = kid
		if value == nil {
			delete(key, member)
		} else {
			key[member] = value
		}
		return key
	}
	keys := []map[string]any{
		base,
		variant("no-use", "use", nil), variant("no-alg", "alg", nil),
		variant("rsa", "kty", "RSA"), variant("p384", "crv", "P-384"), variant("encryption", "use", "enc"),
		variant("rs256", "alg", "RS256"), variant("short-x", "x", "AAAA"), variant("off-curve", "y", base["x"]),
		variant("", "kid", ""),
	}
	document, err := json.Marshal(map[string]any{"keys": keys})
	if err != nil {
		t.Fatal(err)
	}
	jwks.Respond(200, document)
	source := internalauth.NewJWKSSource(jwks.URL(), nil, nil)

	for _, kid := range []string{base["kid"].(string), "no-use", "no-alg"} {
		requireKey(t, source, kid)
	}
	for _, kid := range []string{"rsa", "p384", "encryption", "rs256", "short-x", "off-curve"} {
		assertUnknown(t, source, kid)
	}
}

func requireKey(t *testing.T, source *internalauth.JWKSSource, kid string) {
	t.Helper()
	key, err := source.Key(context.Background(), kid)
	if err != nil || key == nil {
		t.Fatalf("Key(%s) = %v, %v; want key", kid, key, err)
	}
}

func assertUnknown(t *testing.T, source *internalauth.JWKSSource, kid string) {
	t.Helper()
	key, err := source.Key(context.Background(), kid)
	if err != nil || key != nil {
		t.Fatalf("Key(%s) = %v, %v; want unknown", kid, key, err)
	}
}

func assertUnavailable(t *testing.T, source *internalauth.JWKSSource, kid string) {
	t.Helper()
	key, err := source.Key(context.Background(), kid)
	if !errors.Is(err, internalauth.ErrUnavailable) || key != nil {
		t.Fatalf("Key(%s) = %v, %v; want ErrUnavailable", kid, key, err)
	}
}

func assertFetches(t *testing.T, jwks *external.PlatformJWKS, want int) {
	t.Helper()
	if got := jwks.Fetches(); got != want {
		t.Fatalf("JWKS fetches = %d, want %d", got, want)
	}
}
