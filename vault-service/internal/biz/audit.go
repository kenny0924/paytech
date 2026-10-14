package biz

import (
	"context"
	"errors"
	"time"
)

// independentAuditTimeout 限定独立审计事务的时长；客户端断开不代表数据库不可用。
const independentAuditTimeout = 2 * time.Second

// ErrAuditWrite 表示审计写入失败；该失败是终态，所在事务不得再递归审计自身。
var ErrAuditWrite = errors.New("Vault访问审计写入失败")

// AccessAuditService 追加 Vault 访问审计事件。
//
// 成功的写操作在业务事务内追加（Append）；校验失败、拒绝和依赖失败用独立短事务记录一次（Record/RecordFailure）。
type AccessAuditService struct {
	store Store
	ids   IDGenerator
	clock func() time.Time
}

// NewAccessAuditService 注入事务入口、主键生成器与时钟。
func NewAccessAuditService(store Store, ids IDGenerator, clock func() time.Time) *AccessAuditService {
	return &AccessAuditService{store: store, ids: ids, clock: clock}
}

// NewProjection 从本次尝试构造审计投影；只保留格式合法的字段，非法值不入审计。
func NewProjection(a Attempt, action Action, dataType, purpose string) AuditProjection {
	p := AuditProjection{RequestID: a.RequestID, Caller: a.Caller, Action: action}
	if !IsCaller(a.Caller) {
		p.Caller = UnknownCaller
	}
	if IsTraceID(a.TraceID) {
		p.TraceID = &a.TraceID
	}
	p.SetFields(dataType, purpose)
	return p
}

// SetFields 记录格式合法的数据类型与用途；非法或空值不入审计。
func (p *AuditProjection) SetFields(dataType, purpose string) {
	if IsCode(dataType) {
		p.DataType = &dataType
	}
	if IsCode(purpose) {
		p.Purpose = &purpose
	}
}

// Succeeded 把投影标记为对指定记录的成功访问。
func Succeeded(p AuditProjection, recordID int64) AuditProjection {
	p.RecordID = &recordID
	p.Outcome = Success
	p.ResultCode = "SUCCESS"
	return p
}

// Append 在调用方事务内追加一条审计事件；失败统一返回 ErrAuditWrite。
func (s *AccessAuditService) Append(ctx context.Context, tx TxStore, p AuditProjection) error {
	id, err := NextID(ctx, s.ids)
	if err != nil {
		return ErrAuditWrite
	}
	event := AccessEvent{ID: id, RequestID: p.RequestID, TraceID: p.TraceID, Caller: p.Caller, Action: p.Action, DataType: p.DataType, Purpose: p.Purpose, RecordID: p.RecordID, Outcome: p.Outcome, ResultCode: p.ResultCode, PolicyVersion: p.PolicyVersion, CreateTime: s.clock().UTC()}
	if tx.InsertAccessEvent(ctx, event) != nil {
		return ErrAuditWrite
	}
	return nil
}

// Record 用独立事务记录一次审计，只尝试一次；不查询策略。失败返回 AUDIT_UNAVAILABLE。
func (s *AccessAuditService) Record(ctx context.Context, p AuditProjection) error {
	auditContext, cancel := context.WithTimeout(context.WithoutCancel(ctx), independentAuditTimeout)
	defer cancel()
	if err := s.store.InTransaction(auditContext, func(tx TxStore) error { return s.Append(auditContext, tx, p) }); err != nil {
		return NewError(AuditUnavailable)
	}
	return nil
}

// RecordFailure 记录失败或拒绝结果并返回安全错误；审计本身失败时以 AUDIT_UNAVAILABLE 覆盖原错误。
func (s *AccessAuditService) RecordFailure(ctx context.Context, p AuditProjection, err error) error {
	result := Sanitize(err)
	p.ResultCode = string(CodeOf(result))
	p.Outcome = OutcomeForCode(CodeOf(result))
	if auditErr := s.Record(ctx, p); auditErr != nil {
		return auditErr
	}
	return result
}
