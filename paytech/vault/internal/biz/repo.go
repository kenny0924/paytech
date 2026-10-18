// 仓储端口：由 biz 定义、data 实现（依赖倒置）。
//
// 仓储只做数据访问，不承载授权、加解密或审计判定。所有写入都在 Store.InTransaction
// 提供的同一本地事务内完成；事务回调内不得调用 Nacos、远程 KMS 等网络依赖。
package biz

import (
	"context"
	"errors"
)

// Store 是事务入口与少量事务外只读查询。
type Store interface {
	// InTransaction 在 READ COMMITTED 本地事务内执行回调；回调返回错误即回滚。
	InTransaction(ctx context.Context, fn func(TxStore) error) error

	// FindRecordByToken 事务外按 Token 读取记录（Reveal 只读路径）。
	FindRecordByToken(ctx context.Context, token string) (Record, bool, error)

	// Ping 检查数据库连通性，供就绪探针使用。
	Ping(ctx context.Context) error
}

// TxStore 是绑定到单个事务的仓储集合，一张表一个仓储接口。
type TxStore interface {
	RecordTxRepository
	ProtectRequestTxRepository
	AccessEventTxRepository
	PolicyTxRepository
}

// RecordTxRepository 访问 t_vault_record。
type RecordTxRepository interface {
	// FindRecordByID 按主键读取记录。
	FindRecordByID(ctx context.Context, id int64) (Record, bool, error)

	// FindRecordByStableIdentityForShare 以共享锁按 STABLE 身份读取记录。
	FindRecordByStableIdentityForShare(ctx context.Context, dataType string, fingerprint [32]byte) (Record, bool, error)

	// InsertRecord 新增记录；唯一约束冲突返回 *DuplicateError。
	InsertRecord(ctx context.Context, record Record) error
}

// ProtectRequestTxRepository 访问 t_vault_protect_request。
type ProtectRequestTxRepository interface {
	// FindProtectRequestForUpdate 以排他锁按调用方与幂等键读取请求映射。
	FindProtectRequestForUpdate(ctx context.Context, caller, idempotencyKey string) (ProtectRequest, bool, error)

	// InsertProtectRequest 新增请求映射；唯一约束冲突返回 *DuplicateError。
	InsertProtectRequest(ctx context.Context, request ProtectRequest) error
}

// AccessEventTxRepository 访问 t_vault_access_event（只追加）。
type AccessEventTxRepository interface {
	// InsertAccessEvent 追加一条访问审计事件。
	InsertAccessEvent(ctx context.Context, event AccessEvent) error
}

// DuplicateKind 是可被业务恢复的唯一索引名。
type DuplicateKind string

const (
	DuplicateToken DuplicateKind = "uk_vault_record_token"

	DuplicateStable DuplicateKind = "uk_vault_record_stable"

	DuplicateProtectRequest DuplicateKind = "uk_vault_protect_request"

	DuplicateDataType DuplicateKind = "uk_vault_data_type_code"

	DuplicateAccessRule DuplicateKind = "uk_vault_access_rule"

	DuplicatePolicyVersion DuplicateKind = "uk_vault_policy_version"
)

// DuplicateError 表示命中已知唯一索引，Kind 指明索引。
type DuplicateError struct{ Kind DuplicateKind }

func (*DuplicateError) Error() string { return "Vault持久化唯一约束冲突" }

var (
	// ErrRetryableContention 表示死锁或锁等待超时，整个事务可重试。
	ErrRetryableContention = errors.New("Vault事务竞争，请重试")

	// ErrTransactionOutcomeUnknown 表示提交结果无法确认，调用方不得重试或再次审计。
	ErrTransactionOutcomeUnknown = errors.New("Vault事务结果无法确认")

	// ErrTransactionRollbackFailed 表示回滚失败且连接已隔离，本次尝试终止。
	ErrTransactionRollbackFailed = errors.New("Vault事务回滚失败，连接已隔离")
)
