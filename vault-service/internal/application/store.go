package application

import (
	"context"
	"errors"
	"github.com/paytech/vault-service/internal/domain"
)

type Store interface {
	InTransaction(context.Context, func(TxStore) error) error
	FindRecordByToken(context.Context, string) (domain.Record, bool, error)
	Ping(context.Context) error
}
type TxStore interface {
	FindProtectRequest(context.Context, string, string, bool) (domain.ProtectRequest, bool, error)
	FindRecordByID(context.Context, int64) (domain.Record, bool, error)
	FindRecordByStableIdentityForShare(context.Context, string, [32]byte) (domain.Record, bool, error)
	InsertRecord(context.Context, domain.Record) error
	InsertProtectRequest(context.Context, domain.ProtectRequest) error
	InsertAccessEvent(context.Context, domain.AccessEvent) error
}
type DuplicateKind string

const (
	DuplicateToken          DuplicateKind = "uk_vault_record_token"
	DuplicateStable         DuplicateKind = "uk_vault_record_stable"
	DuplicateProtectRequest DuplicateKind = "uk_vault_protect_request"
)

type DuplicateError struct{ Kind DuplicateKind }

func (*DuplicateError) Error() string { return "Vault持久化唯一约束冲突" }

var (
	ErrRetryableContention       = errors.New("Vault事务竞争，请重试")
	ErrTransactionOutcomeUnknown = errors.New("Vault事务结果无法确认")
	ErrTransactionRollbackFailed = errors.New("Vault事务回滚失败，连接已隔离")
)
