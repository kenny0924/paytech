package domain_test

import (
	"encoding/hex"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"

	"github.com/paytech/vault-service/internal/domain"
)

type canonicalVectors struct {
	StableFingerprint struct {
		DataType    string `json:"dataType"`
		Value       string `json:"value"`
		MaterialHex string `json:"materialHex"`
	} `json:"stableFingerprint"`
	ProtectRequestHash struct {
		CallerService string `json:"callerService"`
		DataType      string `json:"dataType"`
		TokenMode     string `json:"tokenMode"`
		Value         string `json:"value"`
		Purpose       string `json:"purpose"`
		MaterialHex   string `json:"materialHex"`
	} `json:"protectRequestHash"`
	RecordAAD struct {
		RecordID    string `json:"recordId"`
		Token       string `json:"token"`
		DataType    string `json:"dataType"`
		TokenMode   string `json:"tokenMode"`
		MaterialHex string `json:"materialHex"`
	} `json:"recordAad"`
}

func TestCanonicalVectors(t *testing.T) {
	vectors := loadVectors(t)
	stable, err := domain.StableMaterial(vectors.StableFingerprint.DataType, []byte(vectors.StableFingerprint.Value))
	if err != nil {
		t.Fatalf("stable material: %v", err)
	}
	assertHex(t, stable, vectors.StableFingerprint.MaterialHex)

	request, err := domain.RequestMaterial(vectors.ProtectRequestHash.CallerService, domain.ProtectInput{
		DataType:  vectors.ProtectRequestHash.DataType,
		TokenMode: domain.TokenMode(vectors.ProtectRequestHash.TokenMode),
		Value:     []byte(vectors.ProtectRequestHash.Value),
		Purpose:   vectors.ProtectRequestHash.Purpose,
	})
	if err != nil {
		t.Fatalf("request material: %v", err)
	}
	assertHex(t, request, vectors.ProtectRequestHash.MaterialHex)

	recordID, err := strconv.ParseInt(vectors.RecordAAD.RecordID, 10, 64)
	if err != nil || recordID <= 0 {
		t.Fatal("record AAD fixture requires a positive base-10 int64 recordId")
	}
	aad, err := domain.RecordAAD(recordID, vectors.RecordAAD.Token, vectors.RecordAAD.DataType, domain.TokenMode(vectors.RecordAAD.TokenMode))
	if err != nil {
		t.Fatalf("record AAD: %v", err)
	}
	assertHex(t, aad, vectors.RecordAAD.MaterialHex)
}

func TestCanonicalMaterialRejectsUnencodableInput(t *testing.T) {
	overflow := strings.Repeat("a", 1<<16)
	_, err := domain.StableMaterial(overflow, []byte("value"))
	assertCode(t, err, domain.InvalidRequest)

	_, err = domain.RequestMaterial("caller", domain.ProtectInput{DataType: "DATA", TokenMode: domain.Stable, Value: []byte{0xff}, Purpose: "PURPOSE"})
	assertCode(t, err, domain.InvalidRequest)

	_, err = domain.RecordAAD(0, "token", "DATA", domain.Stable)
	assertCode(t, err, domain.InternalError)
}

func TestServiceErrorsExposeOnlyFixedMessagesAndClasses(t *testing.T) {
	tests := []struct {
		code    domain.ErrorCode
		message string
		outcome domain.Outcome
	}{
		{domain.InvalidRequest, "请求参数无效", domain.Denied},
		{domain.ValueTooLarge, "值超过32KiB限制", domain.Denied},
		{domain.UnsupportedDataType, "不支持的数据类型", domain.Denied},
		{domain.TokenModeNotAllowed, "Token模式不允许", domain.Denied},
		{domain.AccessDenied, "访问被拒绝", domain.Denied},
		{domain.TokenNotFound, "Token不存在", domain.Denied},
		{domain.IdempotencyKeyConflict, "幂等键已被不同请求使用", domain.Denied},
		{domain.IntegrityCheckFailed, "完整性校验失败", domain.Failed},
		{domain.InternalError, "服务内部错误", domain.Failed},
		{domain.DependencyUnavailable, "依赖服务暂不可用", domain.Failed},
		{domain.AuditUnavailable, "访问审计不可用", domain.Failed},
	}
	for _, test := range tests {
		t.Run(string(test.code), func(t *testing.T) {
			err := domain.NewError(test.code)
			if err.Error() != test.message {
				t.Fatalf("message = %q, want %q", err.Error(), test.message)
			}
			if domain.OutcomeForCode(test.code) != test.outcome {
				t.Fatalf("outcome = %s, want %s", domain.OutcomeForCode(test.code), test.outcome)
			}
			if !errors.Is(err, domain.NewError(test.code)) {
				t.Fatal("same error codes must compare with errors.Is")
			}
		})
	}

	if domain.CodeOf(errors.New("untrusted cause")) != domain.InternalError {
		t.Fatal("unrecognized errors must map to INTERNAL_ERROR")
	}
	var typedNil *domain.ServiceError
	if domain.CodeOf(typedNil) != domain.InternalError {
		t.Fatal("typed-nil service errors must map to INTERNAL_ERROR")
	}
}

func loadVectors(t *testing.T) canonicalVectors {
	t.Helper()
	content, err := os.ReadFile(filepath.Join("..", "..", "testdata", "canonical-vectors.json"))
	if err != nil {
		t.Fatalf("read vectors: %v", err)
	}
	var vectors canonicalVectors
	if err := json.Unmarshal(content, &vectors); err != nil {
		t.Fatalf("decode vectors: %v", err)
	}
	return vectors
}

func assertHex(t *testing.T, actual []byte, expected string) {
	t.Helper()
	if hex.EncodeToString(actual) != expected {
		t.Fatalf("material = %x, want %s", actual, expected)
	}
}

func assertCode(t *testing.T, err error, want domain.ErrorCode) {
	t.Helper()
	if domain.CodeOf(err) != want {
		t.Fatalf("code = %s, want %s", domain.CodeOf(err), want)
	}
}
