package internalauth_test

import (
	"context"
	"encoding/base64"
	"errors"
	"strings"
	"testing"
	"time"

	"github.com/paytech/vault-service/internal/pkg/internalauth"
	"github.com/paytech/vault-service/test/support/builder"
	"github.com/paytech/vault-service/test/support/mocker/external"
)

// 内部调用 Token 校验（D26）：结构、ES256 签名、声明与时间窗口，与 Java InternalTokenVerifierImpl 一致。
//
// 被测入口：internalauth.Verifier.Verify（公钥经真实 HTTP 从 PlatformJWKS 读取）。

const caller = "commerce-service"

func setUpVerifier(t *testing.T) (*external.PlatformJWKS, *internalauth.Verifier) {
	t.Helper()
	jwks, err := external.NewPlatformJWKS()
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(jwks.Close)
	source := internalauth.NewJWKSSource(jwks.URL(), nil, nil)
	return jwks, internalauth.NewVerifier(source, "vault-service", builder.TestInstanceID, nil)
}

func TestVerifierAcceptsValidTokenAndReturnsCaller(t *testing.T) {
	jwks, verifier := setUpVerifier(t)
	for name, token := range map[string]*builder.InternalToken{
		"string audience":       builder.InternalTokenTemplate(caller),
		"single audience array": builder.InternalTokenTemplate(caller).Claim("aud", []string{"vault-service"}),
		"iat within skew":       builder.InternalTokenTemplate(caller).IssuedAt(30*time.Second, 300*time.Second),
		"nbf within skew":       builder.InternalTokenTemplate(caller).Claim("nbf", time.Now().Add(30*time.Second).Unix()),
	} {
		t.Run(name, func(t *testing.T) {
			identity, err := verifier.Verify(context.Background(), jwks.Sign(token.Claim("purpose", "CARD_ACCESS")))
			if err != nil {
				t.Fatalf("Verify() error = %v", err)
			}
			if identity.Caller != caller || identity.Purpose != "CARD_ACCESS" || identity.TokenID == "" {
				t.Fatalf("identity = %#v", identity)
			}
		})
	}
}

func TestVerifierRejectsInvalidClaims(t *testing.T) {
	jwks, verifier := setUpVerifier(t)
	now := time.Now()
	cases := map[string]*builder.InternalToken{
		"issuer mismatch":     builder.InternalTokenTemplate(caller).Claim("iss", "commerce-service"),
		"issuer missing":      builder.InternalTokenTemplate(caller).WithoutClaim("iss"),
		"audience mismatch":   builder.InternalTokenTemplate(caller).Claim("aud", "payment-service"),
		"two audiences":       builder.InternalTokenTemplate(caller).Claim("aud", []string{"vault-service", "payment-service"}),
		"audience missing":    builder.InternalTokenTemplate(caller).WithoutClaim("aud"),
		"instance mismatch":   builder.InternalTokenTemplate(caller).Claim("inst", "other-instance"),
		"instance not string": builder.InternalTokenTemplate(caller).Claim("inst", 7),
		"subject malformed":   builder.InternalTokenTemplate("Commerce_Service"),
		"subject missing":     builder.InternalTokenTemplate(caller).WithoutClaim("sub"),
		"purpose lowercase":   builder.InternalTokenTemplate(caller).Claim("purpose", "general"),
		"purpose missing":     builder.InternalTokenTemplate(caller).WithoutClaim("purpose"),
		"jti missing":         builder.InternalTokenTemplate(caller).WithoutClaim("jti"),
		"iat missing":         builder.InternalTokenTemplate(caller).WithoutClaim("iat"),
		"exp missing":         builder.InternalTokenTemplate(caller).WithoutClaim("exp"),
		"exp fractional":      builder.InternalTokenTemplate(caller).Claim("exp", float64(now.Unix())+100.5),
		"exp as string":       builder.InternalTokenTemplate(caller).Claim("exp", "9999999999"),
		"lifetime over 300s":  builder.InternalTokenTemplate(caller).IssuedAt(0, 301*time.Second),
		"exp before iat":      builder.InternalTokenTemplate(caller).Claim("exp", now.Unix()-1).Claim("iat", now.Unix()),
		"expired":             builder.InternalTokenTemplate(caller).IssuedAt(-301*time.Second, 300*time.Second),
		"issued in future":    builder.InternalTokenTemplate(caller).IssuedAt(61*time.Second, 200*time.Second),
		"not yet valid":       builder.InternalTokenTemplate(caller).Claim("nbf", now.Add(120*time.Second).Unix()),
		"nbf as string":       builder.InternalTokenTemplate(caller).Claim("nbf", "0"),
	}
	for name, token := range cases {
		t.Run(name, func(t *testing.T) {
			assertInvalid(t, verifier, jwks.Sign(token))
		})
	}
}

func TestVerifierRejectsUnsupportedHeaders(t *testing.T) {
	jwks, verifier := setUpVerifier(t)
	cases := map[string]*builder.InternalToken{
		"alg none":      builder.InternalTokenTemplate(caller).HeaderParam("alg", "none"),
		"alg HS256":     builder.InternalTokenTemplate(caller).HeaderParam("alg", "HS256"),
		"kid malformed": builder.InternalTokenTemplate(caller).HeaderParam("kid", "bad kid"),
		"kid empty":     builder.InternalTokenTemplate(caller).HeaderParam("kid", ""),
		"crit present":  builder.InternalTokenTemplate(caller).HeaderParam("crit", []string{"exp"}),
		"unknown kid":   builder.InternalTokenTemplate(caller).HeaderParam("kid", "never-published"),
	}
	for name, token := range cases {
		t.Run(name, func(t *testing.T) {
			assertInvalid(t, verifier, jwks.Sign(token))
		})
	}
	t.Run("signed by unpublished key", func(t *testing.T) {
		assertInvalid(t, verifier, jwks.SignUnpublished(builder.InternalTokenTemplate(caller)))
	})
}

func TestVerifierRejectsMalformedCompactSerialization(t *testing.T) {
	jwks, verifier := setUpVerifier(t)
	valid := jwks.Sign(builder.InternalTokenTemplate(caller))
	segments := strings.Split(valid, ".")
	forged := base64.RawURLEncoding.EncodeToString([]byte(`{"iss":"platform-support-service","sub":"vault-admin"}`))
	signature, _ := base64.RawURLEncoding.DecodeString(segments[2])
	cases := map[string]string{
		"empty":                "",
		"two segments":         segments[0] + "." + segments[1],
		"four segments":        valid + ".extra",
		"empty signature":      segments[0] + "." + segments[1] + ".",
		"tampered payload":     segments[0] + "." + forged + "." + segments[2],
		"short signature":      segments[0] + "." + segments[1] + "." + base64.RawURLEncoding.EncodeToString(signature[:63]),
		"padded signature":     valid + "=",
		"non-base64 header":    "%%%." + segments[1] + "." + segments[2],
		"header not object":    base64.RawURLEncoding.EncodeToString([]byte(`"ES256"`)) + "." + segments[1] + "." + segments[2],
		"trailing header data": base64.RawURLEncoding.EncodeToString([]byte(`{"alg":"ES256"} {}`)) + "." + segments[1] + "." + segments[2],
		"too long":             valid + strings.Repeat("A", 4096),
	}
	for name, token := range cases {
		t.Run(name, func(t *testing.T) {
			assertInvalid(t, verifier, token)
		})
	}
}

func TestVerifierReportsUnavailableKeysSeparately(t *testing.T) {
	jwks, verifier := setUpVerifier(t)
	jwks.Respond(503, []byte(`{"error":"down"}`))

	_, err := verifier.Verify(context.Background(), jwks.Sign(builder.InternalTokenTemplate(caller)))

	if !errors.Is(err, internalauth.ErrUnavailable) {
		t.Fatalf("Verify() error = %v, want ErrUnavailable", err)
	}
}

func assertInvalid(t *testing.T, verifier *internalauth.Verifier, token string) {
	t.Helper()
	identity, err := verifier.Verify(context.Background(), token)
	if !errors.Is(err, internalauth.ErrInvalid) {
		t.Fatalf("Verify() = %#v, %v; want ErrInvalid", identity, err)
	}
}
