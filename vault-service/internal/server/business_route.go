package server

import (
	"context"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
	"go.opentelemetry.io/otel/propagation"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/internal/service"
)

// businessHandler 是 service 层的接口方法；返回的错误由 businessRoute 统一映射为 Problem。
type businessHandler func(c *gin.Context, call *service.Call) error

// businessRoute 把业务接口包装为路由：提取 Trace、开启观测、校验公共头、统一写错误并记录指标（≈ 拦截器 + @ControllerAdvice）。
//
// 公共头校验失败在此独立审计一次；进入用例后的审计由 biz 负责，这里不再重复审计。
func (r *router) businessRoute(action biz.Action, handle businessHandler) gin.HandlerFunc {
	return func(c *gin.Context) {
		ctx, finish := r.observe(c.Request, action)
		c.Request = c.Request.WithContext(ctx)
		call := service.NewCall(ctx, c.GetString(requestIDKey), caller(c.Request.Header), action)
		start, status := time.Now(), http.StatusInternalServerError
		defer func() { finish(call.Projection, status, time.Since(start)) }()

		err := validateHeaders(c.Request.Header, action)
		if err == nil {
			err = handle(c, call)
		} else {
			err = r.deps.Records.AuditEarly(ctx, call, err)
		}
		status = r.complete(c, call, err)
	}
}

// observe 只采用单个 traceparent 作为父上下文，不读取 tracestate、Baggage 或其他头。
func (r *router) observe(req *http.Request, action biz.Action) (context.Context, func(biz.AuditProjection, int, time.Duration)) {
	carrier := propagation.MapCarrier{}
	if values := req.Header.Values("Traceparent"); len(values) == 1 {
		carrier.Set("traceparent", values[0])
	}
	ctx := propagation.TraceContext{}.Extract(req.Context(), carrier)
	if r.deps.Observer == nil {
		return ctx, func(biz.AuditProjection, int, time.Duration) {}
	}
	return r.deps.Observer.Start(ctx, action)
}

// complete 记录结果到投影并写出错误响应，返回 HTTP 状态码；成功响应已由 service 写出。
func (r *router) complete(c *gin.Context, call *service.Call, err error) int {
	if err == nil {
		call.Projection.ResultCode = "SUCCESS"
		call.Projection.Outcome = biz.Success
		return http.StatusOK
	}
	code := biz.CodeOf(err)
	status := statusFor(code)
	call.Projection.ResultCode = string(code)
	call.Projection.Outcome = biz.OutcomeForCode(code)
	r.problems.write(c.Writer, c.Request, status, call.Attempt.RequestID, code)
	return status
}
