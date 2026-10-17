package server

import (
	"context"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/internal/service"
)

// readinessTimeout 限定就绪探针等待依赖的时长。
const readinessTimeout = 2 * time.Second

// liveness 只表示进程存活，不检查依赖。
func (r *router) liveness(c *gin.Context) {
	service.WriteJSON(c.Writer, http.StatusOK, map[string]string{"status": "UP"})
}

// readiness 检查数据库、密钥与注册状态；依赖细节不出现在响应中。
func (r *router) readiness(c *gin.Context) {
	ctx, cancel := context.WithTimeout(c.Request.Context(), readinessTimeout)
	defer cancel()
	if r.deps.Ready == nil || r.deps.Ready(ctx) != nil {
		r.abortWithProblem(c, http.StatusServiceUnavailable, biz.DependencyUnavailable)
		return
	}
	service.WriteJSON(c.Writer, http.StatusOK, map[string]string{"status": "UP"})
}

// metrics 暴露 Prometheus 指标，内容类型由指标 Handler 决定。
func (r *router) metrics(c *gin.Context) {
	c.Writer.Header().Del("Content-Type")
	r.deps.Metrics.ServeHTTP(c.Writer, c.Request)
}
