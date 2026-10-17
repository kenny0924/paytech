package http_test

import (
	"net/http"
	"testing"

	"github.com/stretchr/testify/assert"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/test/support/base"
	"github.com/paytech/vault-service/test/support/builder"
	"github.com/paytech/vault-service/test/support/checker"
)

// 技术端点：存活、就绪（真实数据库与密钥）、指标与未登记路径。
//
// 被测入口：GET /health/liveness、GET /health/readiness、GET /metrics（internal/server 路由）。

func TestHealth_livenessAndReadinessAreUp(t *testing.T) {
	env := base.Setup(t)
	for _, path := range []string{"/health/liveness", "/health/readiness"} {
		response := env.Invoke(t, builder.Get(env.ID, path).Build()).Success()
		assert.JSONEq(t, `{"status":"UP"}`, string(response.Body), path)
	}
}

func TestHealth_metricsCountBusinessRequests(t *testing.T) {
	env := base.Setup(t)
	env.Invoke(t, builder.ProtectTemplate(env.ID).Build()).Success()

	response := env.Invoke(t, builder.Get(env.ID, "/metrics").Build())

	assert.Equal(t, http.StatusOK, response.Status)
	response.BodyContains("vault_requests_total").NotLeaking(env.ID.Caller, "alice@example.com")
}

func TestHealth_unknownPathsAreNotFoundWithoutAudit(t *testing.T) {
	for _, path := range []string{"/missing", "/vault/v1/records/protect/", "/vault/v1/records%2Fprotect"} {
		t.Run(path, func(t *testing.T) {
			env := base.Setup(t)
			request := builder.ProtectTemplate(env.ID).Build()
			request.Path = path

			env.Invoke(t, request).Problem(http.StatusNotFound, biz.InvalidRequest).Allow("")

			checker.NoAccessEvent(t, env.Mapper, request.RequestID())
		})
	}
}
