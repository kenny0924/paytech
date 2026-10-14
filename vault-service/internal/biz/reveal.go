package biz

import (
	"context"
)

// RevealUsecase 处理 Reveal：只读查询记录、按记录数据类型授权、解密，并在返回明文前完成成功审计。
//
// 审计失败时不返回明文；每次尝试只审计一次。
type RevealUsecase struct {
	store  Store
	audit  *AccessAuditService
	cipher *RecordCipherService
	policy Authorizer
}

// NewRevealUsecase 注入依赖。
func NewRevealUsecase(store Store, audit *AccessAuditService, cipher *RecordCipherService, policy Authorizer) *RevealUsecase {
	return &RevealUsecase{store: store, audit: audit, cipher: cipher, policy: policy}
}

// Process 执行 Reveal；成功时调用方拥有返回的明文并负责清零。
func (r *RevealUsecase) Process(ctx context.Context, a Attempt, in RevealInput) (RevealResult, error) {
	p := NewProjection(a, RevealAction, "", in.Purpose)
	if err := validateReveal(a, in); err != nil {
		return RevealResult{}, r.audit.RecordFailure(ctx, p, err)
	}
	record, found, err := r.store.FindRecordByToken(ctx, in.Token)
	if err != nil {
		return RevealResult{}, r.audit.RecordFailure(ctx, p, err)
	}
	if !found {
		return RevealResult{}, r.audit.RecordFailure(ctx, p, NewError(TokenNotFound))
	}
	p.RecordID = &record.ID
	p.DataType = &record.DataType
	allowed := r.policy.Authorize(a.Caller, RevealAction, record.DataType, in.Purpose)
	withPolicyVersion(&p, r.policy)
	if !allowed {
		return RevealResult{}, r.audit.RecordFailure(ctx, p, NewError(AccessDenied))
	}
	return r.reveal(ctx, p, record)
}

// reveal 解密并先写成功审计；任一步失败都清零明文。
func (r *RevealUsecase) reveal(ctx context.Context, p AuditProjection, record Record) (RevealResult, error) {
	plaintext, err := r.cipher.OpenRecord(ctx, record)
	if err != nil {
		Wipe(plaintext)
		return RevealResult{}, r.audit.RecordFailure(ctx, p, err)
	}
	if err := r.audit.Record(ctx, Succeeded(p, record.ID)); err != nil {
		Wipe(plaintext)
		return RevealResult{}, err
	}
	return RevealResult{Value: plaintext}, nil
}
