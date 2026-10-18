// Package service 是 Vault HTTP 接口的实现（≈ @RestController）：解析与校验请求、转换为 biz 输入、调用用例、写成功响应。
//
// 路由登记、公共头、Trace 与错误出口在 server 包；业务规则在 biz 包。本包不查库、不开事务。
package service

import (
	"context"
	"net/http"

	"github.com/gin-gonic/gin"
	"go.opentelemetry.io/otel/trace"

	v1 "github.com/paytech/vault-service/api/vault/v1"
	"github.com/paytech/vault-service/internal/biz"
)

// ProtectUsecase 执行 Protect 用例。
type ProtectUsecase interface {
	Process(ctx context.Context, a biz.Attempt, in biz.ProtectInput) (biz.ProtectResult, error)
}

// RevealUsecase 执行 Reveal 用例；成功时调用方拥有返回的明文。
type RevealUsecase interface {
	Process(ctx context.Context, a biz.Attempt, in biz.RevealInput) (biz.RevealResult, error)
}

// AccessAuditor 为接入层校验失败独立记录一次审计。
type AccessAuditor interface {
	Record(ctx context.Context, p biz.AuditProjection) error
}

// RecordService 实现 /vault/v1/records 下的 Protect 与 Reveal 接口。
type RecordService struct {
	protect ProtectUsecase
	reveal  RevealUsecase
	audit   AccessAuditor
}

// NewRecordService 注入用例与审计；同时登记请求体校验规则。
func NewRecordService(protect ProtectUsecase, reveal RevealUsecase, audit AccessAuditor) *RecordService {
	registerValidations()
	return &RecordService{protect: protect, reveal: reveal, audit: audit}
}

// Call 是一次业务尝试：尝试标识与随处理逐步补全的审计投影。
type Call struct {
	Attempt    biz.Attempt
	Projection biz.AuditProjection
}

// NewCall 由接入层构造尝试与初始审计投影；Trace ID 取自上下文中的合法 Span。
func NewCall(ctx context.Context, requestID, caller string, action biz.Action) *Call {
	attempt := biz.Attempt{RequestID: requestID, Caller: caller, Action: action}
	if sc := trace.SpanContextFromContext(ctx); sc.IsValid() {
		attempt.TraceID = sc.TraceID().String()
	}
	return &Call{Attempt: attempt, Projection: biz.NewProjection(attempt, action, "", "")}
}

// AuditEarly 为进入用例前的校验失败独立审计一次；审计失败以 AUDIT_UNAVAILABLE 覆盖原错误。
func (s *RecordService) AuditEarly(ctx context.Context, call *Call, err error) error {
	call.Projection.ResultCode = string(biz.CodeOf(err))
	call.Projection.Outcome = biz.OutcomeForCode(biz.CodeOf(err))
	if s.audit.Record(ctx, call.Projection) != nil {
		return biz.NewError(biz.AuditUnavailable)
	}
	return err
}

// Protect 处理 POST /vault/v1/records/protect。
func (s *RecordService) Protect(c *gin.Context, call *Call) error {
	ctx := c.Request.Context()
	var body v1.ProtectRequestBody
	if err := bindStrictJSON(c, &body); err != nil {
		return s.AuditEarly(ctx, call, err)
	}
	call.Projection.SetFields(text(body.DataType), text(body.Purpose))
	if validate(&body) != nil {
		return s.AuditEarly(ctx, call, biz.NewError(biz.InvalidRequest))
	}
	if len(*body.Value) > biz.MaxValueBytes {
		return s.AuditEarly(ctx, call, biz.NewError(biz.ValueTooLarge))
	}
	value := []byte(*body.Value)
	defer clear(value)
	input := biz.ProtectInput{DataType: *body.DataType, TokenMode: biz.TokenMode(*body.TokenMode), Value: value, Purpose: *body.Purpose, IdempotencyKey: c.GetHeader("Idempotency-Key")}
	result, err := s.protect.Process(ctx, call.Attempt, input)
	if err != nil {
		return err
	}
	WriteJSON(c.Writer, http.StatusOK, v1.ProtectResponseBody{Token: result.Token})
	return nil
}

// Reveal 处理 POST /vault/v1/records/reveal；无论成败都清零本层持有的明文。
func (s *RecordService) Reveal(c *gin.Context, call *Call) error {
	ctx := c.Request.Context()
	var body v1.RevealRequestBody
	if err := bindStrictJSON(c, &body); err != nil {
		return s.AuditEarly(ctx, call, err)
	}
	call.Projection.SetFields("", text(body.Purpose))
	if validate(&body) != nil {
		return s.AuditEarly(ctx, call, biz.NewError(biz.InvalidRequest))
	}
	result, err := s.reveal.Process(ctx, call.Attempt, biz.RevealInput{Token: *body.Token, Purpose: *body.Purpose})
	defer clear(result.Value)
	if err != nil {
		return err
	}
	// []byte 会被 encoding/json 编码为 base64，合同要求 JSON 字符串。
	WriteJSON(c.Writer, http.StatusOK, v1.RevealResponseBody{Value: string(result.Value)})
	return nil
}
