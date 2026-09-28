package application

import (
	"context"

	"github.com/paytech/vault-service/internal/domain"
)

func (s *Service) Reveal(ctx context.Context, a Attempt, in domain.RevealInput) (domain.RevealResult, error) {
	p := projection(a, domain.RevealAction, "", in.Purpose)
	if !callerPattern.MatchString(a.Caller) || !codePattern.MatchString(in.Purpose) || !tokenPattern.MatchString(in.Token) {
		return domain.RevealResult{}, s.auditFailure(ctx, p, domain.NewError(domain.InvalidRequest))
	}
	record, found, err := s.store.FindRecordByToken(ctx, in.Token)
	if err != nil {
		return domain.RevealResult{}, s.auditFailure(ctx, p, err)
	}
	if !found {
		return domain.RevealResult{}, s.auditFailure(ctx, p, domain.NewError(domain.TokenNotFound))
	}
	p.RecordID = &record.ID
	p.DataType = &record.DataType
	allowed := s.policy.Authorize(a.Caller, domain.RevealAction, record.DataType, in.Purpose)
	s.consultPolicy(&p)
	if !allowed {
		return domain.RevealResult{}, s.auditFailure(ctx, p, domain.NewError(domain.AccessDenied))
	}
	plaintext, err := s.decryptRecord(ctx, record)
	if err != nil {
		wipe(plaintext)
		return domain.RevealResult{}, s.auditFailure(ctx, p, err)
	}
	if err := s.AuditEarly(ctx, successProjection(p, record.ID)); err != nil {
		wipe(plaintext)
		return domain.RevealResult{}, err
	}
	return domain.RevealResult{Value: plaintext}, nil
}

func (s *Service) decryptRecord(ctx context.Context, record domain.Record) ([]byte, error) {
	aad, err := domain.RecordAAD(record.ID, record.Token, record.DataType, record.TokenMode)
	if err != nil {
		return nil, err
	}
	defer wipe(aad)
	return s.keys.Decrypt(ctx, aad, EncryptedValue{Ciphertext: record.Ciphertext, Nonce: record.EncryptionNonce[:], WrappedDEK: record.WrappedDEK, KeyRef: record.KMSKeyRef})
}
