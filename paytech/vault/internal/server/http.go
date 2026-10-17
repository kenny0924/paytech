// Package server 是 Vault 的 HTTP 服务器（Gin）：路由登记、公共头、Trace/指标与 Problem 错误出口。
//
// 接口实现在 service 包；本包只做传输层装配。路由精确匹配：不做尾斜杠/大小写重定向，不反转义路径；
// 不挂 Gin 默认 Logger/Recovery，避免记录请求内容。
package server

import (
	"context"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/internal/service"
)

const (
	protectPath = "/vault/v1/records/protect"
	revealPath  = "/vault/v1/records/reveal"
)

// Observer 为业务请求开启 Span，并在结束时记录有界指标。
type Observer interface {
	Start(ctx context.Context, action biz.Action) (context.Context, func(biz.AuditProjection, int, time.Duration))
}

// Dependencies 是接入层依赖；Metrics 为空时不暴露 /metrics。
type Dependencies struct {
	Records *service.RecordService
	// Admin 为空时不登记 /vault/v1/admin 管理接口。
	Admin *service.AdminService
	// AdminCallers 是允许调用管理接口的服务名（部署配置）。
	AdminCallers []string
	Observer     Observer
	Metrics      http.Handler
	Ready        func(context.Context) error
	Now          func() time.Time
}

// router 持有依赖与 Problem 写出器，路由方法挂在它上面。
type router struct {
	deps     Dependencies
	problems problemWriter
}

// New 构造 Vault HTTP Handler。
func New(deps Dependencies) http.Handler {
	gin.SetMode(gin.ReleaseMode)
	r := &router{deps: deps, problems: problemWriter{now: deps.Now}}

	engine := gin.New()
	engine.RedirectTrailingSlash = false
	engine.RedirectFixedPath = false
	engine.UseRawPath = true
	engine.UnescapePathValues = false
	engine.HandleMethodNotAllowed = true
	engine.Use(r.safetyHeaders)

	records := engine.Group("/vault/v1/records")
	records.POST("/protect", r.businessRoute(biz.ProtectAction, deps.Records.Protect))
	records.POST("/reveal", r.businessRoute(biz.RevealAction, deps.Records.Reveal))
	if deps.Admin != nil {
		r.registerAdmin(engine.Group("/vault/v1/admin"), deps.Admin)
	}

	engine.GET("/health/liveness", r.liveness)
	engine.GET("/health/readiness", r.readiness)
	if deps.Metrics != nil {
		engine.GET("/metrics", r.metrics)
	}

	engine.NoRoute(r.notFound)
	engine.NoMethod(r.methodNotAllowed)
	return engine
}

// safetyHeaders 为所有响应设置 Request ID、禁止缓存与默认 JSON 类型。
func (r *router) safetyHeaders(c *gin.Context) {
	id := requestID(c.Request.Header)
	c.Set(requestIDKey, id)
	header := c.Writer.Header()
	header.Set("Content-Type", "application/json")
	header.Set("Cache-Control", "no-store")
	header.Set("X-Request-Id", id)
	c.Next()
}

// notFound 处理未登记路径，包括任何路径别名。
func (r *router) notFound(c *gin.Context) {
	r.abortWithProblem(c, http.StatusNotFound, biz.InvalidRequest)
}

// methodNotAllowed 对业务路径返回 405 + Allow: POST；技术端点的其他方法按未登记处理。
func (r *router) methodNotAllowed(c *gin.Context) {
	path := c.Request.URL.EscapedPath()
	if path != protectPath && path != revealPath {
		c.Writer.Header().Del("Allow")
		r.notFound(c)
		return
	}
	c.Writer.Header().Set("Allow", http.MethodPost)
	r.abortWithProblem(c, http.StatusMethodNotAllowed, methodNotAllowedCode)
}

// abortWithProblem 写出 Problem 并立即提交状态行，阻止框架追加默认正文。
func (r *router) abortWithProblem(c *gin.Context, status int, code biz.ErrorCode) {
	r.problems.write(c.Writer, c.Request, status, c.GetString(requestIDKey), code)
	c.Writer.WriteHeaderNow()
	c.Abort()
}
