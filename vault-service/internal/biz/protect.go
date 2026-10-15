package biz

import (
	"context"
	"crypto/subtle"
	"errors"
	"time"
)

// maxProtectAttempts 是并发竞争下整个事务的最大尝试次数。
const maxProtectAttempts = 16

// errWinnerPending 表示并发赢家已提交唯一键但本事务尚不可见，需等待后重试。
var errWinnerPending = errors.New("Vault并发赢家尚不可见")

// protectDecision 是事务内的判定结果；业务拒绝放在 err 中，使拒绝审计能随事务提交。
type protectDecision struct {
	token string
	err   error
}

// ProtectUsecase 处理 Protect：按幂等键创建或复用记录并返回 Token。
//
// 流程：校验与策略 → 值格式校验 → 事务外计算 HMAC → 本地事务（锁幂等键 → 复用或新建记录 → 写请求映射与成功审计）。
// 并发唯一冲突与锁竞争在上限内整体重试；提交结果未知、回滚失败与审计失败为终态，不重试也不重复审计。
type ProtectUsecase struct {
	store  Store
	audit  *AccessAuditService
	cipher *RecordCipherService
	policy Authorizer
	ids    IDGenerator
	clock  func() time.Time
}

// NewProtectUsecase 注入依赖。
func NewProtectUsecase(store Store, audit *AccessAuditService, cipher *RecordCipherService, policy Authorizer, ids IDGenerator, clock func() time.Time) *ProtectUsecase {
	return &ProtectUsecase{store: store, audit: audit, cipher: cipher, policy: policy, ids: ids, clock: clock}
}

// Process 执行 Protect。
func (p *ProtectUsecase) Process(ctx context.Context, a Attempt, in ProtectInput) (ProtectResult, error) {
	proj := NewProjection(a, ProtectAction, in.DataType, in.Purpose)
	if err := validateProtect(a, in); err != nil {
		return ProtectResult{}, p.audit.RecordFailure(ctx, proj, err)
	}
	if err := p.authorize(a, in, &proj); err != nil {
		return ProtectResult{}, p.audit.RecordFailure(ctx, proj, err)
	}
	// 先校验值格式再计算 HMAC 和查幂等键：不符合当前约束的值（含幂等重放）一律拒绝，不生成记录。
	if err := p.policy.CheckValue(in.DataType, in.Value); err != nil {
		return ProtectResult{}, p.audit.RecordFailure(ctx, proj, err)
	}
	material, err := p.cipher.ProtectMaterial(ctx, a.Caller, in)
	if err != nil {
		return ProtectResult{}, p.audit.RecordFailure(ctx, proj, err)
	}
	return p.commit(ctx, a, in, proj, material)
}

// authorize 校验数据类型登记、Token 模式与调用方授权，并记录策略版本。
func (p *ProtectUsecase) authorize(a Attempt, in ProtectInput, proj *AuditProjection) error {
	if a.Manual != nil {
		return p.authorizeManual(a, in, proj)
	}
	mode, registered := p.policy.DataTypeMode(in.DataType)
	withPolicyVersion(proj, p.policy)
	if !registered {
		return NewError(UnsupportedDataType)
	}
	if mode != in.TokenMode {
		return NewError(TokenModeNotAllowed)
	}
	if !p.policy.Authorize(a.Caller, ProtectAction, in.DataType, in.Purpose) {
		return NewError(AccessDenied)
	}
	return nil
}

// ProcessManual 执行运营人工加密：Token 模式取自数据类型，用途固定为 OPERATIONS_MANUAL。
func (p *ProtectUsecase) ProcessManual(ctx context.Context, a Attempt, in ProtectInput) (ProtectResult, error) {
	if a.Manual == nil {
		return ProtectResult{}, NewError(InternalError)
	}
	in.Purpose = OperationsManualPurpose
	if dataType, ok := p.policy.DataType(in.DataType); ok {
		in.TokenMode = dataType.TokenMode
	}
	return p.Process(ctx, a, in)
}

// authorizeManual 人工加密授权：数据类型 ACTIVE、开启人工加密、数据级别在本次路由允许范围内。
func (p *ProtectUsecase) authorizeManual(a Attempt, in ProtectInput, proj *AuditProjection) error {
	dataType, ok := p.policy.DataType(in.DataType)
	withPolicyVersion(proj, p.policy)
	if !ok {
		return NewError(UnsupportedDataType)
	}
	if !dataType.ManualProtect || !a.Manual.Allows(dataType.Class) {
		return NewError(AccessDenied)
	}
	return nil
}

// commit 在上限内执行本地事务，并按错误类别决定终止、重试或审计失败。
func (p *ProtectUsecase) commit(ctx context.Context, a Attempt, in ProtectInput, proj AuditProjection, m ProtectMaterial) (ProtectResult, error) {
	var winner DuplicateKind
	for n := 0; n < maxProtectAttempts; n++ {
		candidate, err := p.sealCandidate(ctx, in, m, winner)
		if err != nil {
			return ProtectResult{}, p.audit.RecordFailure(ctx, proj, err)
		}
		decision, err := p.runTransaction(ctx, a, in, proj, m, winner, candidate)
		if err == nil {
			return ProtectResult{Token: decision.token}, decision.err
		}
		if terminal, ok := p.terminalError(ctx, proj, err); ok {
			return ProtectResult{}, terminal
		}
		retry, next := retryable(err, winner)
		if !retry {
			return ProtectResult{}, p.audit.RecordFailure(ctx, proj, err)
		}
		winner = next
		if n+1 < maxProtectAttempts {
			if err := waitForWinner(ctx, n); err != nil {
				return ProtectResult{}, p.audit.RecordFailure(ctx, proj, err)
			}
		}
	}
	return ProtectResult{}, p.audit.RecordFailure(ctx, proj, NewError(DependencyUnavailable))
}

// sealCandidate 在事务外预先加密候选新记录，使事务内只操作数据库；已知并发赢家时只复用赢家，不需要候选。
//
// 复用已有记录或重放时候选被丢弃，代价是一次本地加密。
func (p *ProtectUsecase) sealCandidate(ctx context.Context, in ProtectInput, m ProtectMaterial, winner DuplicateKind) (*Record, error) {
	if winner != "" {
		return nil, nil
	}
	record, err := p.cipher.SealRecord(ctx, in, m.Fingerprint)
	if err != nil {
		return nil, err
	}
	return &record, nil
}

// runTransaction 执行一次本地事务；winner 为已知的并发赢家类型，candidate 为 nil 表示只能复用赢家。
func (p *ProtectUsecase) runTransaction(ctx context.Context, a Attempt, in ProtectInput, proj AuditProjection, m ProtectMaterial, winner DuplicateKind, candidate *Record) (protectDecision, error) {
	var decision protectDecision
	err := p.store.InTransaction(ctx, func(tx TxStore) error {
		var e error
		decision, e = p.protectInTransaction(ctx, tx, a, in, proj, m, winner, candidate)
		return e
	})
	return decision, err
}

// terminalError 识别终态错误：这些错误先于唯一冲突、竞争与审计处理，且不再重试。
func (p *ProtectUsecase) terminalError(ctx context.Context, proj AuditProjection, err error) (error, bool) {
	switch {
	case errors.Is(err, ErrTransactionOutcomeUnknown):
		return NewError(AuditUnavailable), true
	case errors.Is(err, ErrTransactionRollbackFailed):
		return p.audit.RecordFailure(ctx, proj, NewError(DependencyUnavailable)), true
	case errors.Is(err, ErrAuditWrite):
		return NewError(AuditUnavailable), true
	}
	return nil, false
}

// retryable 判断能否重试；幂等键或 STABLE 身份冲突时记住赢家类型，重试只复用赢家而不再新建。
func retryable(err error, winner DuplicateKind) (bool, DuplicateKind) {
	var duplicate *DuplicateError
	if errors.As(err, &duplicate) {
		switch duplicate.Kind {
		case DuplicateProtectRequest, DuplicateStable:
			return true, duplicate.Kind
		case DuplicateToken:
			return true, winner
		}
	}
	return errors.Is(err, ErrRetryableContention) || errors.Is(err, errWinnerPending), winner
}

// waitForWinner 线性退避等待并发赢家可见，最长 20ms。
func waitForWinner(ctx context.Context, attempt int) error {
	timer := time.NewTimer(time.Duration(min(attempt+1, 10)) * 2 * time.Millisecond)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-timer.C:
		return nil
	}
}

// protectInTransaction 是事务内逻辑：已有幂等映射走重放，否则复用或插入候选记录并写映射与成功审计。
func (p *ProtectUsecase) protectInTransaction(ctx context.Context, tx TxStore, a Attempt, in ProtectInput, proj AuditProjection, m ProtectMaterial, winner DuplicateKind, candidate *Record) (protectDecision, error) {
	request, found, err := tx.FindProtectRequestForUpdate(ctx, a.Caller, in.IdempotencyKey)
	if err != nil {
		return protectDecision{}, err
	}
	if found {
		return p.replay(ctx, tx, proj, request, m.RequestHash)
	}
	if winner == DuplicateProtectRequest {
		return protectDecision{}, errWinnerPending
	}
	record, err := p.findOrCreateRecord(ctx, tx, in, m.Fingerprint, candidate)
	if err != nil {
		return protectDecision{}, err
	}
	if err := p.insertProtectRequest(ctx, tx, a, in, m.RequestHash, record.ID); err != nil {
		return protectDecision{}, err
	}
	if err := p.audit.Append(ctx, tx, Succeeded(proj, record.ID)); err != nil {
		return protectDecision{}, err
	}
	return protectDecision{token: record.Token}, nil
}

// insertProtectRequest 写入幂等键到记录的映射。
func (p *ProtectUsecase) insertProtectRequest(ctx context.Context, tx TxStore, a Attempt, in ProtectInput, hash [32]byte, recordID int64) error {
	id, err := NextID(ctx, p.ids)
	if err != nil {
		return err
	}
	now := p.clock().UTC()
	return tx.InsertProtectRequest(ctx, ProtectRequest{ID: id, Caller: a.Caller, IdempotencyKey: in.IdempotencyKey, RequestHash: hash, RecordID: recordID, CreateTime: now, UpdateTime: now})
}

// replay 处理同一幂等键的重放：请求哈希一致返回原 Token，不一致为幂等冲突；两者都随事务写审计。
func (p *ProtectUsecase) replay(ctx context.Context, tx TxStore, proj AuditProjection, request ProtectRequest, hash [32]byte) (protectDecision, error) {
	record, found, err := tx.FindRecordByID(ctx, request.RecordID)
	if err != nil {
		return protectDecision{}, err
	}
	if !found {
		return protectDecision{}, NewError(IntegrityCheckFailed)
	}
	proj = Succeeded(proj, record.ID)
	decision := protectDecision{token: record.Token}
	if subtle.ConstantTimeCompare(request.RequestHash[:], hash[:]) != 1 {
		decision = protectDecision{err: NewError(IdempotencyKeyConflict)}
		proj.Outcome = Denied
		proj.ResultCode = string(IdempotencyKeyConflict)
	}
	return decision, p.audit.Append(ctx, tx, proj)
}

// findOrCreateRecord STABLE 模式先按身份复用记录；没有候选（已知并发赢家）时只等待复用，不再新建。
func (p *ProtectUsecase) findOrCreateRecord(ctx context.Context, tx TxStore, in ProtectInput, fingerprint *[32]byte, candidate *Record) (Record, error) {
	if fingerprint != nil {
		record, found, err := tx.FindRecordByStableIdentityForShare(ctx, in.DataType, *fingerprint)
		if err != nil {
			return Record{}, err
		}
		if found {
			return record, nil
		}
	}
	if candidate == nil {
		return Record{}, errWinnerPending
	}
	if err := tx.InsertRecord(ctx, *candidate); err != nil {
		return Record{}, err
	}
	return *candidate, nil
}
