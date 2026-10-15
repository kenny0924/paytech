package biz

import (
	"context"
	"encoding/json"
	"fmt"
	"time"
)

// DataClass 是数据级别：普通敏感数据与密钥分开授权人工操作。
type DataClass string

const (
	StandardClass DataClass = "STANDARD"

	SecretClass DataClass = "SECRET"
)

// EntryStatus 是数据类型与访问规则的状态；只停用不删除。
type EntryStatus string

const (
	ActiveStatus EntryStatus = "ACTIVE"

	DisabledStatus EntryStatus = "DISABLED"
)

// OperationsManualPurpose 是运营人工加/解密在审计中记录的固定用途。
const OperationsManualPurpose = "OPERATIONS_MANUAL"

// ManualReasonCodes 是人工操作允许的理由码。
var ManualReasonCodes = map[string]bool{
	"CUSTOMER_VERIFICATION":  true,
	"DATA_REPAIR":            true,
	"CHANNEL_CONFIGURATION":  true,
	"INCIDENT_INVESTIGATION": true,
	"OTHER":                  true,
}

// DataType 是数据类型目录条目。编码与 Token 模式创建后不可修改。
type DataType struct {
	Code          string
	TokenMode     TokenMode
	Class         DataClass
	Description   string
	ManualProtect bool
	ManualReveal  bool
	Status        EntryStatus
	Version       int
	UpdateTime    time.Time
}

// AccessRule 是服务访问规则：调用服务 × 动作 × 数据类型 × 用途。
type AccessRule struct {
	ID           int64
	Caller       string
	Action       Action
	DataTypeCode string
	Purpose      string
	Status       EntryStatus
	Version      int
	UpdateTime   time.Time
}

// Key 返回规则四元组标识，用作变更记录的对象键。
func (r AccessRule) Key() string {
	return fmt.Sprintf("%s|%s|%s|%s", r.Caller, r.Action, r.DataTypeCode, r.Purpose)
}

// PolicyChange 是一次策略变更，即一个策略版本。快照只含元数据，不含受保护值。
type PolicyChange struct {
	VersionNo  int
	ChangeType string
	TargetType string
	TargetKey  string
	Before     json.RawMessage
	After      json.RawMessage
	Reason     string
	ActorID    string
	RequestID  string
	CreateTime time.Time
}

// PolicyState 是从数据库一致性读取的全部 ACTIVE 策略与最大版本号。
type PolicyState struct {
	Version   int
	DataTypes []DataType
	Rules     []AccessRule
}

// ManualContext 是运营人工操作的附加上下文，由接入层从可信请求头与路由构造。
type ManualContext struct {
	ActorID        string
	ReasonCode     string
	TicketRef      string
	AllowedClasses []DataClass
}

// Allows 判断数据级别是否在本次路由允许的范围内。
func (m *ManualContext) Allows(class DataClass) bool {
	for _, allowed := range m.AllowedClasses {
		if allowed == class {
			return true
		}
	}
	return false
}

// AccessRuleFilter 是访问规则分页查询条件；空串表示不过滤。
type AccessRuleFilter struct {
	DataTypeCode, Caller, Action, Status string
	Page, Size                           int
}

// PolicyChangeFilter 是策略变更分页查询条件。
type PolicyChangeFilter struct {
	TargetType, TargetKey string
	Page, Size            int
}

// ManualEventFilter 是人工操作审计分页查询条件；时间为左闭右开区间。
type ManualEventFilter struct {
	ActorID, Action, DataTypeCode string
	From, To                      time.Time
	Page, Size                    int
}

// ManualEvent 是人工操作审计的展示投影，不含 Token 与原值。
type ManualEvent struct {
	ID         int64
	RequestID  string
	ActorID    string
	Action     Action
	DataType   string
	Outcome    Outcome
	ResultCode string
	ReasonCode string
	TicketRef  string
	CreateTime time.Time
}

// Page 是分页结果。
type Page[T any] struct {
	Items      []T
	Page, Size int
	Total      int64
}

// PolicyStore 是策略与人工审计的事务外只读查询，由 data 实现。
type PolicyStore interface {
	// LoadPolicy 在一致性只读事务中读取全部 ACTIVE 数据类型、ACTIVE 规则与最大版本号。
	LoadPolicy(ctx context.Context) (PolicyState, error)

	ListDataTypes(ctx context.Context) ([]DataType, error)
	ListAccessRules(ctx context.Context, filter AccessRuleFilter) (Page[AccessRule], error)
	ListPolicyChanges(ctx context.Context, filter PolicyChangeFilter) (Page[PolicyChange], error)
	FindPolicyChange(ctx context.Context, versionNo int) (PolicyChange, bool, error)
	MaxPolicyVersion(ctx context.Context) (int, error)
	ListManualEvents(ctx context.Context, filter ManualEventFilter) (Page[ManualEvent], error)
}

// PolicyTxRepository 是策略维护的事务内仓储。
type PolicyTxRepository interface {
	FindDataTypeForUpdate(ctx context.Context, code string) (DataType, bool, error)
	InsertDataType(ctx context.Context, id int64, dataType DataType, now time.Time) error
	// UpdateDataType 按乐观锁更新可变字段；版本不匹配返回 false。
	UpdateDataType(ctx context.Context, dataType DataType, expectedVersion int, now time.Time) (bool, error)

	FindAccessRuleForUpdate(ctx context.Context, id int64) (AccessRule, bool, error)
	InsertAccessRule(ctx context.Context, rule AccessRule, now time.Time) error
	// UpdateAccessRuleStatus 按乐观锁更新状态；版本不匹配返回 false。
	UpdateAccessRuleStatus(ctx context.Context, id int64, status EntryStatus, expectedVersion int, now time.Time) (bool, error)

	// LockLatestPolicyVersion 锁定并返回当前最大版本号，用于分配下一个版本。
	LockLatestPolicyVersion(ctx context.Context) (int, error)
	InsertPolicyChange(ctx context.Context, id int64, change PolicyChange) error
}

// PolicyVersionLabel 是审计中记录的策略版本标识。
func PolicyVersionLabel(version int) string { return fmt.Sprintf("db-%d", version) }
