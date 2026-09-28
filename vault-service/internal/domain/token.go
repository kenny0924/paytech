package domain

import (
	"encoding/base64"
	"io"
)

func NewToken(random io.Reader) (string, error) {
	raw := make([]byte, 32)
	if _, err := io.ReadFull(random, raw); err != nil {
		return "", NewError(InternalError)
	}
	return base64.RawURLEncoding.EncodeToString(raw), nil
}
