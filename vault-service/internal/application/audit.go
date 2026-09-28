package application

import (
	"context"
	"errors"
	"time"

	"github.com/paytech/vault-service/internal/domain"
)

// A failed audit is terminal; its transaction must never recursively audit itself.
var errAuditWrite = errors.New("Vault访问审计写入失败")

func projection(a Attempt, action domain.Action, dataType, purpose string) domain.AuditProjection {
	p := domain.AuditProjection{RequestID: a.RequestID, Caller: a.Caller, Action: action}
	if !callerPattern.MatchString(a.Caller) {
		p.Caller = "UNKNOWN"
	}
	if tracePattern.MatchString(a.TraceID) && a.TraceID != "00000000000000000000000000000000" {
		p.TraceID = &a.TraceID
	}
	if codePattern.MatchString(dataType) {
		p.DataType = &dataType
	}
	if codePattern.MatchString(purpose) {
		p.Purpose = &purpose
	}
	return p
}

func (s *Service) consultPolicy(p *domain.AuditProjection) {
	version := s.policy.Version()
	p.PolicyVersion = &version
}

func (s *Service) appendEvent(ctx context.Context, tx TxStore, p domain.AuditProjection) error {
	id, err := s.nextID(ctx)
	if err != nil {
		return errAuditWrite
	}
	event := domain.AccessEvent{ID: id, RequestID: p.RequestID, TraceID: p.TraceID, Caller: p.Caller, Action: p.Action, DataType: p.DataType, Purpose: p.Purpose, RecordID: p.RecordID, Outcome: p.Outcome, ResultCode: p.ResultCode, PolicyVersion: p.PolicyVersion, CreateTime: s.clock().UTC()}
	if tx.InsertAccessEvent(ctx, event) != nil {
		return errAuditWrite
	}
	return nil
}

// AuditEarly accepts a safe projection from the HTTP validation boundary.
// It does not consult policy and makes exactly one independent transaction attempt.
func (s *Service) AuditEarly(ctx context.Context, p domain.AuditProjection) error {
	// Client cancellation does not establish database unavailability. Preserve
	// context values, but bound the one independent audit attempt to two seconds.
	auditContext, cancel := context.WithTimeout(context.WithoutCancel(ctx), 2*time.Second)
	defer cancel()
	if err := s.store.InTransaction(auditContext, func(tx TxStore) error { return s.appendEvent(auditContext, tx, p) }); err != nil {
		return domain.NewError(domain.AuditUnavailable)
	}
	return nil
}

func (s *Service) auditFailure(ctx context.Context, p domain.AuditProjection, err error) error {
	result := safeError(err)
	p.ResultCode = string(domain.CodeOf(result))
	p.Outcome = domain.OutcomeForCode(domain.CodeOf(result))
	if auditErr := s.AuditEarly(ctx, p); auditErr != nil {
		return auditErr
	}
	return result
}

func successProjection(p domain.AuditProjection, id int64) domain.AuditProjection {
	p.RecordID = &id
	p.Outcome = domain.Success
	p.ResultCode = "SUCCESS"
	return p
}
