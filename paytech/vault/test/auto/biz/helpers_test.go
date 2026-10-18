package biz_test

import (
	"context"
	"io"
	"time"

	"github.com/paytech/vault-service/internal/biz"
)

// testService 按生产方式组装 Protect/Reveal 用例与审计服务，供用例以入口方法调用。
type testService struct {
	protect *biz.ProtectUsecase
	reveal  *biz.RevealUsecase
	audit   *biz.AccessAuditService
}

func newTestService(store biz.Store, keys biz.KeyService, ids biz.IDGenerator, policy biz.Authorizer, random io.Reader, clock func() time.Time) *testService {
	audit := biz.NewAccessAuditService(store, ids, clock)
	cipher := biz.NewRecordCipherService(keys, ids, random, clock)
	return &testService{
		protect: biz.NewProtectUsecase(store, audit, cipher, policy, ids, clock),
		reveal:  biz.NewRevealUsecase(store, audit, cipher, policy),
		audit:   audit,
	}
}

func (s *testService) Protect(ctx context.Context, a biz.Attempt, in biz.ProtectInput) (biz.ProtectResult, error) {
	return s.protect.Process(ctx, a, in)
}

func (s *testService) Reveal(ctx context.Context, a biz.Attempt, in biz.RevealInput) (biz.RevealResult, error) {
	return s.reveal.Process(ctx, a, in)
}

func (s *testService) AuditEarly(ctx context.Context, p biz.AuditProjection) error {
	return s.audit.Record(ctx, p)
}
