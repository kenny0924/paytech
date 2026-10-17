package server

import (
	"net/http"
	"regexp"
	"slices"

	"github.com/gin-gonic/gin"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/internal/service"
)

// operatorHeader 携带运营账号 ID，由 Operations BFF 从可信网关上下文填写。
const operatorHeader = "X-Operator-Id"

var operatorPattern = regexp.MustCompile(`^[A-Za-z0-9._-]{1,64}$`)

// policyHandler 是策略维护与审计查询接口；actor 已由路由层校验。
type policyHandler func(c *gin.Context, actor biz.PolicyActor) error

// registerAdmin 登记 /vault/v1/admin 管理接口（只接受部署配置中的管理调用方）。
func (r *router) registerAdmin(admin *gin.RouterGroup, s *service.AdminService) {
	admin.GET("/data-types", r.adminRoute(s.ListDataTypes))
	admin.POST("/data-types", r.adminRoute(s.CreateDataType))
	admin.PUT("/data-types/:dataTypeCode", r.adminRoute(s.UpdateDataType))
	admin.GET("/access-rules", r.adminRoute(s.ListAccessRules))
	admin.POST("/access-rules", r.adminRoute(s.CreateAccessRule))
	admin.PUT("/access-rules/:accessRuleId", r.adminRoute(s.UpdateAccessRule))
	admin.GET("/policy-changes", r.adminRoute(s.ListPolicyChanges))
	admin.GET("/policy-changes/:policyVersionNo", r.adminRoute(s.GetPolicyChange))
	admin.GET("/manual-events", r.adminRoute(s.ListManualEvents))
	admin.POST("/manual/protect", r.businessRoute(biz.ProtectAction, s.ManualProtect, r.adminCaller))
	admin.POST("/manual/reveal", r.businessRoute(biz.RevealAction, s.ManualReveal, r.adminCaller))
}

// adminRoute 包装策略维护接口：校验管理调用方、操作人与写请求的 JSON 类型，并统一写 Problem。
func (r *router) adminRoute(handle policyHandler) gin.HandlerFunc {
	return func(c *gin.Context) {
		requestID := c.GetString(requestIDKey)
		err := r.checkAdmin(c.Request)
		if err == nil {
			err = handle(c, biz.PolicyActor{ActorID: c.Request.Header.Get(operatorHeader), RequestID: requestID})
		}
		if err != nil {
			code := biz.CodeOf(err)
			r.problems.write(c.Writer, c.Request, statusFor(code), requestID, code)
		}
	}
}

// checkAdmin 校验调用方属于管理调用方、操作人唯一且合法；写请求还须为 JSON。
func (r *router) checkAdmin(req *http.Request) error {
	if !slices.Contains(r.deps.AdminCallers, caller(req.Header)) {
		return biz.NewError(biz.AccessDenied)
	}
	if !validOperator(req.Header) {
		return biz.NewError(biz.InvalidRequest)
	}
	if req.Method != http.MethodGet && !jsonContentType(req.Header) {
		return biz.NewError(biz.InvalidRequest)
	}
	return nil
}

// adminCaller 是人工加/解密的附加校验：管理调用方与操作人；通过后把操作人写入尝试与审计。
func (r *router) adminCaller(h http.Header, call *service.Call) error {
	if !slices.Contains(r.deps.AdminCallers, caller(h)) {
		return biz.NewError(biz.AccessDenied)
	}
	if !validOperator(h) {
		return biz.NewError(biz.InvalidRequest)
	}
	call.WithOperator(h.Get(operatorHeader))
	return nil
}

func validOperator(h http.Header) bool {
	values := h.Values(operatorHeader)
	return len(values) == 1 && operatorPattern.MatchString(values[0])
}
