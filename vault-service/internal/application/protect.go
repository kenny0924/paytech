package application

import (
	"context"
	"crypto/subtle"
	"errors"
	"time"

	"github.com/paytech/vault-service/internal/domain"
)

const maxProtectAttempts = 16

var errWinnerPending = errors.New("Vault并发赢家尚不可见")

type protectMaterial struct {
	request     [32]byte
	fingerprint *[32]byte
}
type protectDecision struct {
	token string
	err   error
}

func (s *Service) Protect(ctx context.Context, a Attempt, in domain.ProtectInput) (domain.ProtectResult, error) {
	p := projection(a, domain.ProtectAction, in.DataType, in.Purpose)
	if err := validateProtect(a, in); err != nil {
		return domain.ProtectResult{}, s.auditFailure(ctx, p, err)
	}
	mode, registered := s.policy.DataTypeMode(in.DataType)
	s.consultPolicy(&p)
	if !registered {
		return domain.ProtectResult{}, s.auditFailure(ctx, p, domain.NewError(domain.UnsupportedDataType))
	}
	if mode != in.TokenMode {
		return domain.ProtectResult{}, s.auditFailure(ctx, p, domain.NewError(domain.TokenModeNotAllowed))
	}
	if !s.policy.Authorize(a.Caller, domain.ProtectAction, in.DataType, in.Purpose) {
		return domain.ProtectResult{}, s.auditFailure(ctx, p, domain.NewError(domain.AccessDenied))
	}
	material, err := s.material(ctx, a.Caller, in)
	if err != nil {
		return domain.ProtectResult{}, s.auditFailure(ctx, p, err)
	}
	return s.protectWithRecovery(ctx, a, in, p, material)
}

func (s *Service) material(ctx context.Context, caller string, in domain.ProtectInput) (protectMaterial, error) {
	var result protectMaterial
	encoded, err := domain.RequestMaterial(caller, in)
	if err != nil {
		return result, err
	}
	defer wipe(encoded)
	result.request, err = s.keys.MAC(ctx, ProtectRequest, encoded)
	if err != nil {
		return result, err
	}
	if in.TokenMode != domain.Stable {
		return result, nil
	}
	stable, err := domain.StableMaterial(in.DataType, in.Value)
	if err != nil {
		return result, err
	}
	defer wipe(stable)
	fingerprint, err := s.keys.MAC(ctx, StableIdentity, stable)
	if err != nil {
		return result, err
	}
	result.fingerprint = &fingerprint
	return result, nil
}

func (s *Service) protectWithRecovery(ctx context.Context, a Attempt, in domain.ProtectInput, p domain.AuditProjection, m protectMaterial) (domain.ProtectResult, error) {
	var winner DuplicateKind
	for n := 0; n < maxProtectAttempts; n++ {
		var decision protectDecision
		err := s.store.InTransaction(ctx, func(tx TxStore) error {
			var e error
			decision, e = s.protectTransaction(ctx, tx, a, in, p, m, winner)
			return e
		})
		if err == nil {
			return domain.ProtectResult{Token: decision.token}, decision.err
		}
		// Termination sentinels precede all duplicate, contention and audit handling.
		if errors.Is(err, ErrTransactionOutcomeUnknown) {
			return domain.ProtectResult{}, domain.NewError(domain.AuditUnavailable)
		}
		if errors.Is(err, ErrTransactionRollbackFailed) {
			return domain.ProtectResult{}, s.auditFailure(ctx, p, domain.NewError(domain.DependencyUnavailable))
		}
		if errors.Is(err, errAuditWrite) {
			return domain.ProtectResult{}, domain.NewError(domain.AuditUnavailable)
		}
		retry := errors.Is(err, ErrRetryableContention) || errors.Is(err, errWinnerPending)
		var duplicate *DuplicateError
		if errors.As(err, &duplicate) {
			switch duplicate.Kind {
			case DuplicateProtectRequest, DuplicateStable:
				winner = duplicate.Kind
				retry = true
			case DuplicateToken:
				retry = true
			}
		}
		if !retry {
			return domain.ProtectResult{}, s.auditFailure(ctx, p, err)
		}
		if n+1 < maxProtectAttempts {
			if err := waitForWinner(ctx, n); err != nil {
				return domain.ProtectResult{}, s.auditFailure(ctx, p, err)
			}
		}
	}
	return domain.ProtectResult{}, s.auditFailure(ctx, p, domain.NewError(domain.DependencyUnavailable))
}

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

func (s *Service) protectTransaction(ctx context.Context, tx TxStore, a Attempt, in domain.ProtectInput, p domain.AuditProjection, m protectMaterial, winner DuplicateKind) (protectDecision, error) {
	request, found, err := tx.FindProtectRequest(ctx, a.Caller, in.IdempotencyKey, true)
	if err != nil {
		return protectDecision{}, err
	}
	if found {
		return s.replay(ctx, tx, p, request, m.request)
	}
	if winner == DuplicateProtectRequest {
		return protectDecision{}, errWinnerPending
	}
	record, err := s.findOrCreateRecord(ctx, tx, in, m.fingerprint, winner == DuplicateStable)
	if err != nil {
		return protectDecision{}, err
	}
	id, err := s.nextID(ctx)
	if err != nil {
		return protectDecision{}, err
	}
	now := s.clock().UTC()
	request = domain.ProtectRequest{ID: id, Caller: a.Caller, IdempotencyKey: in.IdempotencyKey, RequestHash: m.request, RecordID: record.ID, CreateTime: now, UpdateTime: now}
	if err := tx.InsertProtectRequest(ctx, request); err != nil {
		return protectDecision{}, err
	}
	if err := s.appendEvent(ctx, tx, successProjection(p, record.ID)); err != nil {
		return protectDecision{}, err
	}
	return protectDecision{token: record.Token}, nil
}

func (s *Service) replay(ctx context.Context, tx TxStore, p domain.AuditProjection, request domain.ProtectRequest, hash [32]byte) (protectDecision, error) {
	record, found, err := tx.FindRecordByID(ctx, request.RecordID)
	if err != nil {
		return protectDecision{}, err
	}
	if !found {
		return protectDecision{}, domain.NewError(domain.IntegrityCheckFailed)
	}
	p = successProjection(p, record.ID)
	decision := protectDecision{token: record.Token}
	if subtle.ConstantTimeCompare(request.RequestHash[:], hash[:]) != 1 {
		decision = protectDecision{err: domain.NewError(domain.IdempotencyKeyConflict)}
		p.Outcome = domain.Denied
		p.ResultCode = string(domain.IdempotencyKeyConflict)
	}
	// Business denial is held outside the callback so its audit can commit.
	return decision, s.appendEvent(ctx, tx, p)
}

func (s *Service) findOrCreateRecord(ctx context.Context, tx TxStore, in domain.ProtectInput, fingerprint *[32]byte, requireWinner bool) (domain.Record, error) {
	if fingerprint != nil {
		record, found, err := tx.FindRecordByStableIdentityForShare(ctx, in.DataType, *fingerprint)
		if err != nil {
			return domain.Record{}, err
		}
		if found {
			return record, nil
		}
	}
	if requireWinner {
		return domain.Record{}, errWinnerPending
	}
	record, err := s.newRecord(ctx, in, fingerprint)
	if err != nil {
		return domain.Record{}, err
	}
	if err = tx.InsertRecord(ctx, record); err != nil {
		return domain.Record{}, err
	}
	return record, nil
}

func (s *Service) newRecord(ctx context.Context, in domain.ProtectInput, fingerprint *[32]byte) (domain.Record, error) {
	id, err := s.nextID(ctx)
	if err != nil {
		return domain.Record{}, err
	}
	token, err := s.nextToken(ctx)
	if err != nil {
		return domain.Record{}, err
	}
	aad, err := domain.RecordAAD(id, token, in.DataType, in.TokenMode)
	if err != nil {
		return domain.Record{}, err
	}
	defer wipe(aad)
	plaintext := append([]byte(nil), in.Value...)
	defer wipe(plaintext)
	encrypted, err := s.keys.Encrypt(ctx, aad, plaintext)
	if err != nil {
		return domain.Record{}, err
	}
	if len(encrypted.Nonce) != 12 {
		return domain.Record{}, domain.NewError(domain.InternalError)
	}
	now := s.clock().UTC()
	return domain.Record{ID: id, Token: token, DataType: in.DataType, TokenMode: in.TokenMode, ValueFingerprint: fingerprint, Ciphertext: encrypted.Ciphertext, EncryptionNonce: [12]byte(encrypted.Nonce), WrappedDEK: encrypted.WrappedDEK, KMSKeyRef: encrypted.KeyRef, CreateTime: now, UpdateTime: now}, nil
}
