package httpapi

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"strconv"
	"unicode/utf8"

	"github.com/paytech/vault-service/internal/domain"
)

const maxBodyBytes = 256 * 1024

type protectDocument struct {
	DataType  *string `json:"dataType"`
	TokenMode *string `json:"tokenMode"`
	Value     *string `json:"value"`
	Purpose   *string `json:"purpose"`
}
type revealDocument struct {
	Token   *string `json:"token"`
	Purpose *string `json:"purpose"`
}

func decode(w http.ResponseWriter, r *http.Request, target any, fields ...string) error {
	r.Body = http.MaxBytesReader(w, r.Body, maxBodyBytes)
	raw, err := io.ReadAll(r.Body)
	defer clear(raw)
	if err != nil || !utf8.Valid(raw) || !validSurrogates(raw) || !strictObject(raw, fields) {
		return domain.NewError(domain.InvalidRequest)
	}
	decoder := json.NewDecoder(bytes.NewReader(raw))
	decoder.DisallowUnknownFields()
	if decoder.Decode(target) != nil || decoder.Decode(&struct{}{}) != io.EOF {
		return domain.NewError(domain.InvalidRequest)
	}
	return nil
}

// Token-level validation closes encoding/json's case-insensitive struct lookup,
// escaped duplicate keys, null strings, nested values and trailing documents.
func strictObject(raw []byte, fields []string) bool {
	allowed := make(map[string]bool, len(fields))
	for _, key := range fields {
		allowed[key] = true
	}
	decoder := json.NewDecoder(bytes.NewReader(raw))
	token, err := decoder.Token()
	if err != nil || token != json.Delim('{') {
		return false
	}
	seen := make(map[string]bool, len(fields))
	for decoder.More() {
		token, err = decoder.Token()
		key, ok := token.(string)
		if err != nil || !ok || !allowed[key] || seen[key] {
			return false
		}
		seen[key] = true
		token, err = decoder.Token()
		if _, ok = token.(string); err != nil || !ok {
			return false
		}
	}
	token, err = decoder.Token()
	if err != nil || token != json.Delim('}') {
		return false
	}
	_, err = decoder.Token()
	return err == io.EOF
}

// The standard decoder replaces lone UTF-16 surrogates. Validate escapes on
// original bytes first; an escaped backslash is not a Unicode escape.
func validSurrogates(raw []byte) bool {
	for i := 0; i < len(raw); i++ {
		if raw[i] != '\\' {
			continue
		}
		i++
		if i >= len(raw) {
			return false
		}
		if raw[i] != 'u' {
			continue
		}
		value, ok := unicodeEscape(raw, i)
		if !ok {
			return false
		}
		i += 4
		if value >= 0xdc00 && value <= 0xdfff {
			return false
		}
		if value < 0xd800 || value > 0xdbff {
			continue
		}
		if i+2 >= len(raw) || raw[i+1] != '\\' || raw[i+2] != 'u' {
			return false
		}
		low, ok := unicodeEscape(raw, i+2)
		if !ok || low < 0xdc00 || low > 0xdfff {
			return false
		}
		i += 6
	}
	return true
}
func unicodeEscape(raw []byte, u int) (uint64, bool) {
	if u+4 >= len(raw) {
		return 0, false
	}
	value, err := strconv.ParseUint(string(raw[u+1:u+5]), 16, 16)
	return value, err == nil
}
func text(value *string) string {
	if value == nil {
		return ""
	}
	return *value
}
