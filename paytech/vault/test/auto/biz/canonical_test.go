package biz_test

import (
	"encoding/hex"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"

	"github.com/paytech/vault-service/internal/biz"
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
	stable, err := biz.StableMaterial(vectors.StableFingerprint.DataType, []byte(vectors.StableFingerprint.Value))
	if err != nil {
		t.Fatalf("stable material: %v", err)
	}
	assertHex(t, stable, vectors.StableFingerprint.MaterialHex)

	request, err := biz.RequestMaterial(vectors.ProtectRequestHash.CallerService, biz.ProtectInput{
		DataType:  vectors.ProtectRequestHash.DataType,
		TokenMode: biz.TokenMode(vectors.ProtectRequestHash.TokenMode),
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
	aad, err := biz.RecordAAD(recordID, vectors.RecordAAD.Token, vectors.RecordAAD.DataType, biz.TokenMode(vectors.RecordAAD.TokenMode))
	if err != nil {
		t.Fatalf("record AAD: %v", err)
	}
	assertHex(t, aad, vectors.RecordAAD.MaterialHex)
}

func TestCanonicalMaterialRejectsUnencodableInput(t *testing.T) {
	overflow := strings.Repeat("a", 1<<16)
	_, err := biz.StableMaterial(overflow, []byte("value"))
	assertCode(t, err, biz.InvalidRequest)

	_, err = biz.RequestMaterial("caller", biz.ProtectInput{DataType: "DATA", TokenMode: biz.Stable, Value: []byte{0xff}, Purpose: "PURPOSE"})
	assertCode(t, err, biz.InvalidRequest)

	_, err = biz.RecordAAD(0, "token", "DATA", biz.Stable)
	assertCode(t, err, biz.InternalError)
}

func TestServiceErrorsExposeOnlyFixedMessagesAndClasses(t *testing.T) {
	tests := []struct {
		code    biz.ErrorCode
		message string
		outcome biz.Outcome
	}{
		{biz.InvalidRequest, "请求参数无效", biz.Denied},
		{biz.ValueTooLarge, "值超过32KiB限制", biz.Denied},
		{biz.UnsupportedDataType, "不支持的数据类型", biz.Denied},
		{biz.TokenModeNotAllowed, "Token模式不允许", biz.Denied},
		{biz.AccessDenied, "访问被拒绝", biz.Denied},
		{biz.TokenNotFound, "Token不存在", biz.Denied},
		{biz.IdempotencyKeyConflict, "幂等键已被不同请求使用", biz.Denied},
		{biz.IntegrityCheckFailed, "完整性校验失败", biz.Failed},
		{biz.InternalError, "服务内部错误", biz.Failed},
		{biz.DependencyUnavailable, "依赖服务暂不可用", biz.Failed},
		{biz.AuditUnavailable, "访问审计不可用", biz.Failed},
	}
	for _, test := range tests {
		t.Run(string(test.code), func(t *testing.T) {
			err := biz.NewError(test.code)
			if err.Error() != test.message {
				t.Fatalf("message = %q, want %q", err.Error(), test.message)
			}
			if biz.OutcomeForCode(test.code) != test.outcome {
				t.Fatalf("outcome = %s, want %s", biz.OutcomeForCode(test.code), test.outcome)
			}
			if !errors.Is(err, biz.NewError(test.code)) {
				t.Fatal("same error codes must compare with errors.Is")
			}
		})
	}

	if biz.CodeOf(errors.New("untrusted cause")) != biz.InternalError {
		t.Fatal("unrecognized errors must map to INTERNAL_ERROR")
	}
	var typedNil *biz.ServiceError
	if biz.CodeOf(typedNil) != biz.InternalError {
		t.Fatal("typed-nil service errors must map to INTERNAL_ERROR")
	}
}

func loadVectors(t *testing.T) canonicalVectors {
	t.Helper()
	content, err := os.ReadFile(filepath.Join("..", "..", "..", "testdata", "canonical-vectors.json"))
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

func assertCode(t *testing.T, err error, want biz.ErrorCode) {
	t.Helper()
	if biz.CodeOf(err) != want {
		t.Fatalf("code = %s, want %s", biz.CodeOf(err), want)
	}
}
