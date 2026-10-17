package base

import (
	"bytes"
	"io"
	"net/http"
	"testing"

	"github.com/paytech/vault-service/test/support/builder"
	"github.com/paytech/vault-service/test/support/checker"
)

// Invoke 经真实 HTTP 调用被测 Vault，返回响应 Checker；不补头、不修复输入、不默认断言成功。
func (e *Environment) Invoke(t *testing.T, request builder.Request) *checker.ResponseChecker {
	t.Helper()
	httpRequest, err := http.NewRequest(request.Method, e.URL+request.Path, bytes.NewReader(request.Body))
	if err != nil {
		t.Fatalf("构造请求失败：%v", err)
	}
	httpRequest.Header = request.Header.Clone()
	response, err := e.server.Client().Do(httpRequest)
	if err != nil {
		t.Fatalf("请求 Vault 失败：%v", err)
	}
	defer func() { _ = response.Body.Close() }()
	body, err := io.ReadAll(response.Body)
	if err != nil {
		t.Fatalf("读取响应失败：%v", err)
	}
	return checker.NewResponseChecker(t, request, response.StatusCode, response.Header, body)
}
