package localaes

import (
	"bytes"
	"context"
	"crypto/aes"
	"crypto/cipher"
	"encoding/hex"
	"errors"
	"io"
	"strconv"
	"sync"
	"testing"

	"github.com/paytech/vault-service/internal/biz"
)

// TEST-ONLY-NOT-A-SECRET: SHA-256 of that literal; never a runtime default.
func testRoot() []byte {
	return decode("10d560af63c1aa45dd71f4da45556cadbb9b5b7c1a283b9e6321c7a728d799f3")
}
func decode(s string) []byte { b, _ := hex.DecodeString(s); return b }
func provider(t *testing.T) *Provider {
	t.Helper()
	p, e := New(testRoot(), "test-key")
	if e != nil {
		t.Fatal(e)
	}
	return p
}

func TestKnownAnswers(t *testing.T) {
	p := provider(t)
	// Independently calculated with Python HMAC Extract/Expand, nil salt and one block.
	for _, v := range []struct {
		purpose  biz.MACPurpose
		key, mac string
	}{
		{biz.MACStableIdentity, "76c82a980e1e8fb7f09ef7d54cee7c0e476a3ce8b978804069be638f797ac900", "2ddf1b7f6faa483b26a64db2991014f3183af72c0d9cc75c87b9b29cc711b0d2"},
		{biz.MACProtectRequest, "32b0b0cac6cb3e6acdd0adf841fe24b07973f1f7d1776e3fbe50834af0c758e0", "aed93174b79b93b2ddbeb525049523baef1793901477f20836e4bf358ca08090"},
	} {
		k, e := p.derive(string(v.purpose))
		if e != nil || !bytes.Equal(k, decode(v.key)) {
			t.Fatal("HKDF mismatch")
		}
		m, e := p.MAC(context.Background(), v.purpose, []byte("test material"))
		if e != nil || !bytes.Equal(m[:], decode(v.mac)) {
			t.Fatal("MAC mismatch")
		}
	}
	k, e := p.derive("local-wrap-v1")
	if e != nil || !bytes.Equal(k, decode("689e46afb335dd35976c4b3e6469bb59109821d2a0d2684c6e51eecb6c8b1cb0")) {
		t.Fatal("wrap HKDF mismatch")
	}
	if m, e := p.MAC(context.Background(), "local-wrap-v1", nil); e == nil || m != [32]byte{} {
		t.Fatal("unsupported purpose accepted")
	}
}

func TestEnvelopeAndRoundTrip(t *testing.T) {
	p := provider(t)
	entropy := make([]byte, 112)
	for i := range entropy {
		entropy[i] = byte(i)
	}
	p.random = bytes.NewReader(entropy)
	aad, plain := []byte("record aad"), []byte("test value")
	first, e := p.Encrypt(context.Background(), aad, plain)
	if e != nil {
		t.Fatal(e)
	}
	if len(first.WrappedDEK) != 61 || first.WrappedDEK[0] != 1 || first.KeyRef != "local-aes://test-key" || !bytes.Equal(first.Nonce, entropy[32:44]) || !bytes.Equal(first.WrappedDEK[1:13], entropy[44:56]) {
		t.Fatal("wrong envelope layout")
	}
	// Independent decryption proves exact DEK, domain and nonce allocation, not merely symmetric mistakes.
	block, _ := aes.NewCipher(decode("689e46afb335dd35976c4b3e6469bb59109821d2a0d2684c6e51eecb6c8b1cb0"))
	gcm, _ := cipher.NewGCM(block)
	dek, e := gcm.Open(nil, first.WrappedDEK[1:13], first.WrappedDEK[13:], append([]byte("vault-local-wrap-v1"), aad...))
	if e != nil || !bytes.Equal(dek, entropy[:32]) {
		t.Fatal("DEK wrap mismatch")
	}
	block, _ = aes.NewCipher(dek)
	gcm, _ = cipher.NewGCM(block)
	actual, e := gcm.Open(nil, first.Nonce, first.Ciphertext, aad)
	if e != nil || !bytes.Equal(actual, plain) {
		t.Fatal("data encryption mismatch")
	}
	second, e := p.Encrypt(context.Background(), aad, plain)
	if e != nil || bytes.Equal(first.Ciphertext, second.Ciphertext) || bytes.Equal(first.WrappedDEK, second.WrappedDEK) || bytes.Equal(first.Nonce, second.Nonce) {
		t.Fatal("fresh randomness missing")
	}
	for _, v := range []biz.EncryptedValue{first, second} {
		actual, e := p.Decrypt(context.Background(), aad, v)
		if e != nil || !bytes.Equal(actual, plain) {
			t.Fatal("round trip failed")
		}
	}
}

func TestCorruptionReturnsNoPlaintext(t *testing.T) {
	p := provider(t)
	aad := []byte("record aad")
	original, e := p.Encrypt(context.Background(), aad, []byte("test value"))
	if e != nil {
		t.Fatal(e)
	}
	cases := map[string]func(*biz.EncryptedValue){
		"key ID":     func(v *biz.EncryptedValue) { v.KeyRef = "local-aes://other" },
		"version":    func(v *biz.EncryptedValue) { v.WrappedDEK[0] = 2 },
		"wrap nonce": func(v *biz.EncryptedValue) { v.WrappedDEK[1] ^= 1 },
		"wrap tag":   func(v *biz.EncryptedValue) { v.WrappedDEK[60] ^= 1 },
		"ciphertext": func(v *biz.EncryptedValue) { v.Ciphertext[0] ^= 1 },
		"nonce":      func(v *biz.EncryptedValue) { v.Nonce[0] ^= 1 },
	}
	for _, n := range []int{0, 1, 12, 13, 60, 62} {
		cases["envelope length "+strconv.Itoa(n)] = func(v *biz.EncryptedValue) { v.WrappedDEK = make([]byte, n) }
	}
	for _, n := range []int{0, 11, 13} {
		cases["nonce length "+strconv.Itoa(n)] = func(v *biz.EncryptedValue) { v.Nonce = make([]byte, n) }
	}
	cases["short ciphertext"] = func(v *biz.EncryptedValue) { v.Ciphertext = make([]byte, 15) }
	for name, mutate := range cases {
		t.Run(name, func(t *testing.T) {
			v := original
			v.WrappedDEK = bytes.Clone(v.WrappedDEK)
			v.Ciphertext = bytes.Clone(v.Ciphertext)
			v.Nonce = bytes.Clone(v.Nonce)
			mutate(&v)
			actual, e := p.Decrypt(context.Background(), aad, v)
			if actual != nil || biz.CodeOf(e) != biz.IntegrityCheckFailed {
				t.Fatal("corruption did not fail closed")
			}
		})
	}
	actual, e := p.Decrypt(context.Background(), []byte("wrong aad"), original)
	if actual != nil || e == nil {
		t.Fatal("wrong AAD accepted")
	}
	root := testRoot()
	root[0] ^= 1
	other, _ := New(root, "test-key")
	actual, e = other.Decrypt(context.Background(), aad, original)
	if actual != nil || e == nil {
		t.Fatal("wrong root accepted")
	}
}

type failingReader struct{}

type cancelingReader struct{ cancel context.CancelFunc }

func (r cancelingReader) Read(dst []byte) (int, error) {
	clear(dst)
	r.cancel()
	return len(dst), nil
}

func TestCancellationDuringEntropyReturnsNoEnvelope(t *testing.T) {
	p := provider(t)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	p.random = cancelingReader{cancel: cancel}
	v, err := p.Encrypt(ctx, nil, []byte("test"))
	if !errors.Is(err, context.Canceled) || v.Ciphertext != nil || v.WrappedDEK != nil || v.Nonce != nil || v.KeyRef != "" {
		t.Fatal("cancellation during entropy did not fail closed")
	}
}

func (failingReader) Read([]byte) (int, error) { return 0, errors.New("sensitive entropy failure") }
func TestEntropyAndCancellation(t *testing.T) {
	for _, n := range []int{0, 31, 32, 43, 44, 55} {
		p := provider(t)
		p.random = io.MultiReader(bytes.NewReader(make([]byte, n)), failingReader{})
		v, e := p.Encrypt(context.Background(), nil, []byte("test"))
		if e == nil || e.Error() == "sensitive entropy failure" || v.Ciphertext != nil || v.WrappedDEK != nil || v.Nonce != nil || v.KeyRef != "" {
			t.Fatal("entropy failure exposed output")
		}
	}
	p := provider(t)
	encrypted, e := p.Encrypt(context.Background(), nil, []byte("test"))
	if e != nil {
		t.Fatal(e)
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if v, e := p.Encrypt(ctx, nil, nil); !errors.Is(e, context.Canceled) || v.Ciphertext != nil {
		t.Fatal("canceled encryption")
	}
	if v, e := p.Decrypt(ctx, nil, encrypted); !errors.Is(e, context.Canceled) || v != nil {
		t.Fatal("canceled decryption")
	}
	if v, e := p.MAC(ctx, biz.MACStableIdentity, nil); !errors.Is(e, context.Canceled) || v != [32]byte{} {
		t.Fatal("canceled MAC")
	}
	if e := p.Ready(ctx); !errors.Is(e, context.Canceled) {
		t.Fatal("canceled readiness")
	}
	if e := p.Ready(context.Background()); e != nil {
		t.Fatal(e)
	}
}

func TestConstructorAndConcurrency(t *testing.T) {
	for _, root := range [][]byte{nil, make([]byte, 31), make([]byte, 33)} {
		if _, e := New(root, "test-key"); e == nil {
			t.Fatal("invalid root accepted")
		}
	}
	if _, e := New(testRoot(), ""); e == nil {
		t.Fatal("empty ID accepted")
	}
	root := testRoot()
	p, e := New(root, "test-key")
	if e != nil {
		t.Fatal(e)
	}
	clear(root)
	var wg sync.WaitGroup
	for range 32 {
		wg.Go(func() {
			v, e := p.Encrypt(context.Background(), []byte("aad"), []byte("value"))
			if e != nil {
				t.Error(e)
				return
			}
			actual, e := p.Decrypt(context.Background(), []byte("aad"), v)
			if e != nil || string(actual) != "value" {
				t.Error("concurrent round trip")
			}
			m, e := p.MAC(context.Background(), biz.MACStableIdentity, []byte("test material"))
			if e != nil || !bytes.Equal(m[:], decode("2ddf1b7f6faa483b26a64db2991014f3183af72c0d9cc75c87b9b29cc711b0d2")) {
				t.Error("root alias or MAC race")
			}
		})
	}
	wg.Wait()
}
