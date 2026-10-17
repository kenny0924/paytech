package biz_test

import (
	"bytes"
	"errors"
	"io"
	"regexp"
	"testing"

	"github.com/paytech/vault-service/internal/biz"
)

func TestNewTokenProducesOpaqueBase64URLToken(t *testing.T) {
	raw := append(bytes.Repeat([]byte{0x01}, 32), bytes.Repeat([]byte{0x02}, 32)...)
	tokenOne, err := biz.NewToken(bytes.NewReader(raw))
	if err != nil {
		t.Fatalf("first token: %v", err)
	}
	tokenTwo, err := biz.NewToken(bytes.NewReader(raw[32:]))
	if err != nil {
		t.Fatalf("second token: %v", err)
	}
	if len(tokenOne) != 43 || !regexp.MustCompile(`^[A-Za-z0-9_-]{43}$`).MatchString(tokenOne) {
		t.Fatalf("token %q is not a 43-character Base64URL token", tokenOne)
	}
	if tokenOne == tokenTwo {
		t.Fatal("separate random inputs must produce different tokens")
	}
}

func TestNewTokenMapsRandomFailureToSafeInternalError(t *testing.T) {
	token, err := biz.NewToken(errorReader{})
	if token != "" {
		t.Fatalf("token = %q, want empty", token)
	}
	assertCode(t, err, biz.InternalError)
	if errors.Is(err, io.ErrUnexpectedEOF) || err.Error() != "服务内部错误" {
		t.Fatalf("random failure must not expose its cause: %v", err)
	}
}

type errorReader struct{}

func (errorReader) Read([]byte) (int, error) { return 0, io.ErrUnexpectedEOF }
