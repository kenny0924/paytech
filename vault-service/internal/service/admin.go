package service

import (
	"context"
	"net/http"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"

	v1 "github.com/paytech/vault-service/api/vault/v1"
	"github.com/paytech/vault-service/internal/biz"
)

// PolicyAdmin 是策略维护与审计查询用例。
type PolicyAdmin interface {
	ListDataTypes(ctx context.Context) ([]biz.DataType, int, error)
	CreateDataType(ctx context.Context, actor biz.PolicyActor, in biz.DataType) (biz.DataType, int, error)
	UpdateDataType(ctx context.Context, actor biz.PolicyActor, in biz.UpdateDataTypeInput) (biz.DataType, int, error)
	ListAccessRules(ctx context.Context, filter biz.AccessRuleFilter) (biz.Page[biz.AccessRule], error)
	CreateAccessRule(ctx context.Context, actor biz.PolicyActor, in biz.AccessRule) (biz.AccessRule, int, error)
	UpdateAccessRule(ctx context.Context, actor biz.PolicyActor, in biz.UpdateAccessRuleInput) (biz.AccessRule, int, error)
	ListPolicyChanges(ctx context.Context, filter biz.PolicyChangeFilter) (biz.Page[biz.PolicyChange], error)
	GetPolicyChange(ctx context.Context, versionNo int) (biz.PolicyChange, error)
	ListManualEvents(ctx context.Context, filter biz.ManualEventFilter) (biz.Page[biz.ManualEvent], error)
}

// ManualProtector 执行运营人工加密。
type ManualProtector interface {
	ProcessManual(ctx context.Context, a biz.Attempt, in biz.ProtectInput) (biz.ProtectResult, error)
}

// AdminService 实现 /vault/v1/admin 下的策略维护、审计查询与人工加/解密接口。
type AdminService struct {
	policy  PolicyAdmin
	protect ManualProtector
	reveal  RevealUsecase
	audit   AccessAuditor
}

// NewAdminService 注入用例；人工解密复用 Reveal 用例（按 Attempt.Manual 走人工授权）。
func NewAdminService(policy PolicyAdmin, protect ManualProtector, reveal RevealUsecase, audit AccessAuditor) *AdminService {
	registerValidations()
	return &AdminService{policy: policy, protect: protect, reveal: reveal, audit: audit}
}

// ListDataTypes 处理 GET /data-types?status=。
func (s *AdminService) ListDataTypes(c *gin.Context, _ biz.PolicyActor) error {
	items, version, err := s.policy.ListDataTypes(c.Request.Context())
	if err != nil {
		return err
	}
	status := c.Query("status")
	body := v1.DataTypeListResponseBody{CurrentPolicyVersion: version, Items: []v1.DataTypeBody{}}
	for _, item := range items {
		if status == "" || string(item.Status) == status {
			body.Items = append(body.Items, dataTypeBody(item))
		}
	}
	WriteJSON(c.Writer, http.StatusOK, body)
	return nil
}

// CreateDataType 处理 POST /data-types。
func (s *AdminService) CreateDataType(c *gin.Context, actor biz.PolicyActor) error {
	var body v1.CreateDataTypeRequestBody
	if err := bindAndValidate(c, &body); err != nil {
		return err
	}
	actor.Reason = *body.Reason
	in := biz.DataType{Code: *body.DataTypeCode, TokenMode: biz.TokenMode(*body.TokenMode), Class: biz.DataClass(*body.DataClass), Description: *body.Description,
		ManualProtect: *body.ManualProtectEnabled, ManualReveal: *body.ManualRevealEnabled}
	created, version, err := s.policy.CreateDataType(c.Request.Context(), actor, in)
	if err != nil {
		return err
	}
	WriteJSON(c.Writer, http.StatusOK, v1.DataTypeResponseBody{DataType: dataTypeBody(created), PolicyVersion: version})
	return nil
}

// UpdateDataType 处理 PUT /data-types/{dataTypeCode}。
func (s *AdminService) UpdateDataType(c *gin.Context, actor biz.PolicyActor) error {
	var body v1.UpdateDataTypeRequestBody
	if err := bindAndValidate(c, &body); err != nil {
		return err
	}
	actor.Reason = *body.Reason
	in := biz.UpdateDataTypeInput{Code: c.Param("dataTypeCode"), ExpectedVersion: *body.ExpectedVersion, Class: biz.DataClass(*body.DataClass), Description: *body.Description,
		ManualProtect: *body.ManualProtectEnabled, ManualReveal: *body.ManualRevealEnabled, Status: biz.EntryStatus(*body.Status)}
	if !biz.IsCode(in.Code) {
		return biz.NewError(biz.InvalidRequest)
	}
	updated, version, err := s.policy.UpdateDataType(c.Request.Context(), actor, in)
	if err != nil {
		return err
	}
	WriteJSON(c.Writer, http.StatusOK, v1.DataTypeResponseBody{DataType: dataTypeBody(updated), PolicyVersion: version})
	return nil
}

// ListAccessRules 处理 GET /access-rules。
func (s *AdminService) ListAccessRules(c *gin.Context, _ biz.PolicyActor) error {
	page, size, err := pageQuery(c)
	if err != nil {
		return err
	}
	filter := biz.AccessRuleFilter{DataTypeCode: c.Query("dataTypeCode"), Caller: c.Query("callerService"), Action: c.Query("action"), Status: c.Query("status"), Page: page, Size: size}
	result, err := s.policy.ListAccessRules(c.Request.Context(), filter)
	if err != nil {
		return err
	}
	body := v1.AccessRulePageResponseBody{Items: make([]v1.AccessRuleBody, 0, len(result.Items)), Page: result.Page, Size: result.Size, Total: result.Total}
	for _, rule := range result.Items {
		body.Items = append(body.Items, accessRuleBody(rule))
	}
	WriteJSON(c.Writer, http.StatusOK, body)
	return nil
}

// CreateAccessRule 处理 POST /access-rules。
func (s *AdminService) CreateAccessRule(c *gin.Context, actor biz.PolicyActor) error {
	var body v1.CreateAccessRuleRequestBody
	if err := bindAndValidate(c, &body); err != nil {
		return err
	}
	actor.Reason = *body.Reason
	in := biz.AccessRule{Caller: *body.CallerService, Action: biz.Action(*body.Action), DataTypeCode: *body.DataTypeCode, Purpose: *body.PurposeCode}
	created, version, err := s.policy.CreateAccessRule(c.Request.Context(), actor, in)
	if err != nil {
		return err
	}
	WriteJSON(c.Writer, http.StatusOK, v1.AccessRuleResponseBody{AccessRule: accessRuleBody(created), PolicyVersion: version})
	return nil
}

// UpdateAccessRule 处理 PUT /access-rules/{accessRuleId}。
func (s *AdminService) UpdateAccessRule(c *gin.Context, actor biz.PolicyActor) error {
	var body v1.UpdateAccessRuleRequestBody
	if err := bindAndValidate(c, &body); err != nil {
		return err
	}
	id, err := strconv.ParseInt(c.Param("accessRuleId"), 10, 64)
	if err != nil || id <= 0 {
		return biz.NewError(biz.InvalidRequest)
	}
	actor.Reason = *body.Reason
	updated, version, err := s.policy.UpdateAccessRule(c.Request.Context(), actor, biz.UpdateAccessRuleInput{ID: id, ExpectedVersion: *body.ExpectedVersion, Status: biz.EntryStatus(*body.Status)})
	if err != nil {
		return err
	}
	WriteJSON(c.Writer, http.StatusOK, v1.AccessRuleResponseBody{AccessRule: accessRuleBody(updated), PolicyVersion: version})
	return nil
}

// ListPolicyChanges 处理 GET /policy-changes。
func (s *AdminService) ListPolicyChanges(c *gin.Context, _ biz.PolicyActor) error {
	page, size, err := pageQuery(c)
	if err != nil {
		return err
	}
	result, err := s.policy.ListPolicyChanges(c.Request.Context(), biz.PolicyChangeFilter{TargetType: c.Query("targetType"), TargetKey: c.Query("targetKey"), Page: page, Size: size})
	if err != nil {
		return err
	}
	body := v1.PolicyChangePageResponseBody{Items: make([]v1.PolicyChangeBody, 0, len(result.Items)), Page: result.Page, Size: result.Size, Total: result.Total}
	for _, change := range result.Items {
		body.Items = append(body.Items, policyChangeBody(change, false))
	}
	WriteJSON(c.Writer, http.StatusOK, body)
	return nil
}

// GetPolicyChange 处理 GET /policy-changes/{policyVersionNo}。
func (s *AdminService) GetPolicyChange(c *gin.Context, _ biz.PolicyActor) error {
	versionNo, err := strconv.Atoi(c.Param("policyVersionNo"))
	if err != nil || versionNo <= 0 {
		return biz.NewError(biz.InvalidRequest)
	}
	change, err := s.policy.GetPolicyChange(c.Request.Context(), versionNo)
	if err != nil {
		return err
	}
	WriteJSON(c.Writer, http.StatusOK, policyChangeBody(change, true))
	return nil
}

// ListManualEvents 处理 GET /manual-events。
func (s *AdminService) ListManualEvents(c *gin.Context, _ biz.PolicyActor) error {
	page, size, err := pageQuery(c)
	if err != nil {
		return err
	}
	filter := biz.ManualEventFilter{ActorID: c.Query("actorId"), Action: c.Query("action"), DataTypeCode: c.Query("dataTypeCode"), Page: page, Size: size}
	if filter.From, err = timeQuery(c, "from"); err != nil {
		return err
	}
	if filter.To, err = timeQuery(c, "to"); err != nil {
		return err
	}
	result, err := s.policy.ListManualEvents(c.Request.Context(), filter)
	if err != nil {
		return err
	}
	body := v1.ManualEventPageResponseBody{Items: make([]v1.ManualEventBody, 0, len(result.Items)), Page: result.Page, Size: result.Size, Total: result.Total}
	for _, e := range result.Items {
		body.Items = append(body.Items, v1.ManualEventBody{RequestID: e.RequestID, ActorID: e.ActorID, Action: string(e.Action), DataTypeCode: e.DataType,
			ReasonCode: e.ReasonCode, TicketRef: e.TicketRef, Outcome: string(e.Outcome), ResultCode: e.ResultCode, CreateTime: e.CreateTime})
	}
	WriteJSON(c.Writer, http.StatusOK, body)
	return nil
}

// ManualProtect 处理 POST /manual/protect：用途固定为 OPERATIONS_MANUAL，Token 模式取自数据类型。
func (s *AdminService) ManualProtect(c *gin.Context, call *Call) error {
	ctx := c.Request.Context()
	var body v1.ManualProtectRequestBody
	if err := bindStrictJSON(c, &body); err != nil {
		return s.auditEarly(ctx, call, err)
	}
	call.Projection.SetFields(text(body.DataTypeCode), biz.OperationsManualPurpose)
	if validate(&body) != nil {
		return s.auditEarly(ctx, call, biz.NewError(biz.InvalidRequest))
	}
	call.withManual(*body.ReasonCode, *body.TicketRef, body.AllowedDataClasses)
	if len(*body.Value) > biz.MaxValueBytes {
		return s.auditEarly(ctx, call, biz.NewError(biz.ValueTooLarge))
	}
	value := []byte(*body.Value)
	defer clear(value)
	in := biz.ProtectInput{DataType: *body.DataTypeCode, Value: value, IdempotencyKey: c.GetHeader("Idempotency-Key")}
	result, err := s.protect.ProcessManual(ctx, call.Attempt, in)
	if err != nil {
		return err
	}
	WriteJSON(c.Writer, http.StatusOK, v1.ProtectResponseBody{Token: result.Token})
	return nil
}

// ManualReveal 处理 POST /manual/reveal；无论成败都清零本层持有的明文。
func (s *AdminService) ManualReveal(c *gin.Context, call *Call) error {
	ctx := c.Request.Context()
	var body v1.ManualRevealRequestBody
	if err := bindStrictJSON(c, &body); err != nil {
		return s.auditEarly(ctx, call, err)
	}
	call.Projection.SetFields("", biz.OperationsManualPurpose)
	if validate(&body) != nil {
		return s.auditEarly(ctx, call, biz.NewError(biz.InvalidRequest))
	}
	call.withManual(*body.ReasonCode, *body.TicketRef, body.AllowedDataClasses)
	result, err := s.reveal.Process(ctx, call.Attempt, biz.RevealInput{Token: *body.Token, Purpose: biz.OperationsManualPurpose})
	defer clear(result.Value)
	if err != nil {
		return err
	}
	WriteJSON(c.Writer, http.StatusOK, v1.ManualRevealResponseBody{DataTypeCode: result.DataType, Value: string(result.Value)})
	return nil
}

// AuditEarly 为进入人工用例前的失败独立审计一次（供路由层的调用方校验使用）。
func (s *AdminService) AuditEarly(ctx context.Context, call *Call, err error) error {
	return s.auditEarly(ctx, call, err)
}

func (s *AdminService) auditEarly(ctx context.Context, call *Call, err error) error {
	call.Projection.ResultCode = string(biz.CodeOf(err))
	call.Projection.Outcome = biz.OutcomeForCode(biz.CodeOf(err))
	if s.audit.Record(ctx, call.Projection) != nil {
		return biz.NewError(biz.AuditUnavailable)
	}
	return err
}

// WithOperator 记录人工操作的操作人（来自可信请求头），写入尝试与审计投影。
func (call *Call) WithOperator(actorID string) {
	call.Attempt.Manual = &biz.ManualContext{ActorID: actorID}
	if actorID != "" {
		call.Projection.ActorID = &actorID
	}
}

// withManual 补充理由码、工单号与允许的数据级别。
func (call *Call) withManual(reasonCode, ticketRef string, classes []string) {
	if call.Attempt.Manual == nil {
		call.Attempt.Manual = &biz.ManualContext{}
	}
	call.Attempt.Manual.ReasonCode, call.Attempt.Manual.TicketRef = reasonCode, ticketRef
	call.Attempt.Manual.AllowedClasses = call.Attempt.Manual.AllowedClasses[:0]
	for _, class := range classes {
		call.Attempt.Manual.AllowedClasses = append(call.Attempt.Manual.AllowedClasses, biz.DataClass(class))
	}
	call.Projection.ReasonCode, call.Projection.TicketRef = &reasonCode, &ticketRef
}

// bindAndValidate 严格解码请求体并按 binding 标签校验。
func bindAndValidate(c *gin.Context, body any) error {
	if err := bindStrictJSON(c, body); err != nil {
		return err
	}
	if validate(body) != nil {
		return biz.NewError(biz.InvalidRequest)
	}
	return nil
}

// pageQuery 读取 page/size 查询参数；缺省交由用例补默认值。
func pageQuery(c *gin.Context) (int, int, error) {
	page, err := optionalInt(c.Query("page"))
	if err != nil {
		return 0, 0, err
	}
	size, err := optionalInt(c.Query("size"))
	return page, size, err
}

func optionalInt(value string) (int, error) {
	if value == "" {
		return 0, nil
	}
	n, err := strconv.Atoi(value)
	if err != nil {
		return 0, biz.NewError(biz.InvalidRequest)
	}
	return n, nil
}

// timeQuery 读取 RFC 3339 时间参数；缺省为零值。
func timeQuery(c *gin.Context, name string) (time.Time, error) {
	value := c.Query(name)
	if value == "" {
		return time.Time{}, nil
	}
	t, err := time.Parse(time.RFC3339Nano, value)
	if err != nil {
		return time.Time{}, biz.NewError(biz.InvalidRequest)
	}
	return t.UTC(), nil
}

func dataTypeBody(d biz.DataType) v1.DataTypeBody {
	return v1.DataTypeBody{DataTypeCode: d.Code, TokenMode: string(d.TokenMode), DataClass: string(d.Class), Description: d.Description, ManualProtectEnabled: d.ManualProtect,
		ManualRevealEnabled: d.ManualReveal, Status: string(d.Status), Version: d.Version, UpdateTime: d.UpdateTime.UTC()}
}

func accessRuleBody(r biz.AccessRule) v1.AccessRuleBody {
	return v1.AccessRuleBody{AccessRuleID: strconv.FormatInt(r.ID, 10), CallerService: r.Caller, Action: string(r.Action), DataTypeCode: r.DataTypeCode, PurposeCode: r.Purpose,
		Status: string(r.Status), Version: r.Version, UpdateTime: r.UpdateTime.UTC()}
}

func policyChangeBody(c biz.PolicyChange, detail bool) v1.PolicyChangeBody {
	body := v1.PolicyChangeBody{PolicyVersionNo: c.VersionNo, ChangeType: c.ChangeType, TargetType: c.TargetType, TargetKey: c.TargetKey, ActorID: c.ActorID, Reason: c.Reason, CreateTime: c.CreateTime.UTC()}
	if detail {
		body.BeforeSnapshot, body.AfterSnapshot = c.Before, c.After
	}
	return body
}
