package biz

import (
	"context"
	"encoding/json"
	"errors"
	"strings"
	"time"
	"unicode/utf8"
)

const (
	maxPolicyChangeAttempts = 3
	maxDescriptionRunes     = 256
	maxReasonRunes          = 500
	maxPageSize             = 200
)

// PolicyActor 是策略维护的操作人与变更理由。
type PolicyActor struct {
	ActorID   string
	RequestID string
	Reason    string
}

// UpdateDataTypeInput 是数据类型可变字段；编码与 Token 模式不可修改。
type UpdateDataTypeInput struct {
	Code            string
	ExpectedVersion int
	Class           DataClass
	Description     string
	ManualProtect   bool
	ManualReveal    bool
	Status          EntryStatus
}

// UpdateAccessRuleInput 只允许启停规则。
type UpdateAccessRuleInput struct {
	ID              int64
	ExpectedVersion int
	Status          EntryStatus
}

// PolicyAdminUsecase 维护数据类型与访问规则：保存即发布新版本，每次变更写一条变更记录（不需要审批）。
//
// 每次变更在一个本地事务内完成：锁定最新版本号 → 校验与乐观锁更新 → 写变更记录；版本号冲突时整体重试。
type PolicyAdminUsecase struct {
	store Store
	reads PolicyStore
	ids   IDGenerator
	clock func() time.Time
}

// NewPolicyAdminUsecase 注入依赖。
func NewPolicyAdminUsecase(store Store, reads PolicyStore, ids IDGenerator, clock func() time.Time) *PolicyAdminUsecase {
	return &PolicyAdminUsecase{store: store, reads: reads, ids: ids, clock: clock}
}

// ListDataTypes 返回全部数据类型（含停用）与当前最大版本号。
func (u *PolicyAdminUsecase) ListDataTypes(ctx context.Context) ([]DataType, int, error) {
	dataTypes, err := u.reads.ListDataTypes(ctx)
	if err != nil {
		return nil, 0, Sanitize(err)
	}
	version, err := u.reads.MaxPolicyVersion(ctx)
	if err != nil {
		return nil, 0, Sanitize(err)
	}
	return dataTypes, version, nil
}

// CreateDataType 新建数据类型。
func (u *PolicyAdminUsecase) CreateDataType(ctx context.Context, actor PolicyActor, in DataType) (DataType, int, error) {
	in.Status = ActiveStatus
	if err := validateDataType(in); err != nil {
		return DataType{}, 0, err
	}
	var created DataType
	version, err := u.commitChange(ctx, actor, func(tx TxStore, now time.Time) (PolicyChange, error) {
		id, err := NextID(ctx, u.ids)
		if err != nil {
			return PolicyChange{}, err
		}
		if err := tx.InsertDataType(ctx, id, in, now); err != nil {
			return PolicyChange{}, err
		}
		created = in
		created.Version, created.UpdateTime = 1, now
		return newChange("DATA_TYPE_CREATE", "DATA_TYPE", in.Code, nil, dataTypeSnapshot(created))
	})
	return created, version, err
}

// UpdateDataType 修改数据类型的可变字段；版本不匹配返回 POLICY_VERSION_CONFLICT。
func (u *PolicyAdminUsecase) UpdateDataType(ctx context.Context, actor PolicyActor, in UpdateDataTypeInput) (DataType, int, error) {
	var updated DataType
	version, err := u.commitChange(ctx, actor, func(tx TxStore, now time.Time) (PolicyChange, error) {
		current, err := u.lockedDataType(ctx, tx, in.Code, in.ExpectedVersion)
		if err != nil {
			return PolicyChange{}, err
		}
		updated = current
		updated.Class, updated.Description, updated.ManualProtect, updated.ManualReveal, updated.Status = in.Class, in.Description, in.ManualProtect, in.ManualReveal, in.Status
		if err := validateDataType(updated); err != nil {
			return PolicyChange{}, err
		}
		if ok, err := tx.UpdateDataType(ctx, updated, in.ExpectedVersion, now); err != nil || !ok {
			return PolicyChange{}, conflictOr(err)
		}
		updated.Version, updated.UpdateTime = in.ExpectedVersion+1, now
		return newChange("DATA_TYPE_UPDATE", "DATA_TYPE", in.Code, dataTypeSnapshot(current), dataTypeSnapshot(updated))
	})
	return updated, version, err
}

// ListAccessRules 分页查询访问规则。
func (u *PolicyAdminUsecase) ListAccessRules(ctx context.Context, filter AccessRuleFilter) (Page[AccessRule], error) {
	if err := normalizePage(&filter.Page, &filter.Size); err != nil {
		return Page[AccessRule]{}, err
	}
	page, err := u.reads.ListAccessRules(ctx, filter)
	return page, sanitizeOrNil(err)
}

// CreateAccessRule 新建服务访问规则；数据类型须存在且 ACTIVE，用途不得使用人工操作保留值。
func (u *PolicyAdminUsecase) CreateAccessRule(ctx context.Context, actor PolicyActor, in AccessRule) (AccessRule, int, error) {
	in.Status = ActiveStatus
	if !IsCaller(in.Caller) || (in.Action != ProtectAction && in.Action != RevealAction) || !IsCode(in.DataTypeCode) || !IsCode(in.Purpose) || in.Purpose == OperationsManualPurpose {
		return AccessRule{}, 0, NewError(InvalidRequest)
	}
	var created AccessRule
	version, err := u.commitChange(ctx, actor, func(tx TxStore, now time.Time) (PolicyChange, error) {
		if err := requireActiveDataType(ctx, tx, in.DataTypeCode); err != nil {
			return PolicyChange{}, err
		}
		id, err := NextID(ctx, u.ids)
		if err != nil {
			return PolicyChange{}, err
		}
		created = in
		created.ID, created.Version, created.UpdateTime = id, 1, now
		if err := tx.InsertAccessRule(ctx, created, now); err != nil {
			return PolicyChange{}, err
		}
		return newChange("RULE_CREATE", "ACCESS_RULE", created.Key(), nil, ruleSnapshot(created))
	})
	return created, version, err
}

// UpdateAccessRule 启用或停用访问规则。
func (u *PolicyAdminUsecase) UpdateAccessRule(ctx context.Context, actor PolicyActor, in UpdateAccessRuleInput) (AccessRule, int, error) {
	if in.Status != ActiveStatus && in.Status != DisabledStatus {
		return AccessRule{}, 0, NewError(InvalidRequest)
	}
	var updated AccessRule
	version, err := u.commitChange(ctx, actor, func(tx TxStore, now time.Time) (PolicyChange, error) {
		current, found, err := tx.FindAccessRuleForUpdate(ctx, in.ID)
		if err != nil {
			return PolicyChange{}, err
		}
		if !found {
			return PolicyChange{}, NewError(PolicyEntryNotFound)
		}
		if current.Version != in.ExpectedVersion {
			return PolicyChange{}, NewError(PolicyVersionConflict)
		}
		if ok, err := tx.UpdateAccessRuleStatus(ctx, in.ID, in.Status, in.ExpectedVersion, now); err != nil || !ok {
			return PolicyChange{}, conflictOr(err)
		}
		updated = current
		updated.Status, updated.Version, updated.UpdateTime = in.Status, in.ExpectedVersion+1, now
		return newChange("RULE_UPDATE", "ACCESS_RULE", updated.Key(), ruleSnapshot(current), ruleSnapshot(updated))
	})
	return updated, version, err
}

// ListPolicyChanges 分页查询策略变更记录（按版本倒序）。
func (u *PolicyAdminUsecase) ListPolicyChanges(ctx context.Context, filter PolicyChangeFilter) (Page[PolicyChange], error) {
	if err := normalizePage(&filter.Page, &filter.Size); err != nil {
		return Page[PolicyChange]{}, err
	}
	page, err := u.reads.ListPolicyChanges(ctx, filter)
	return page, sanitizeOrNil(err)
}

// GetPolicyChange 返回指定版本的变更详情。
func (u *PolicyAdminUsecase) GetPolicyChange(ctx context.Context, versionNo int) (PolicyChange, error) {
	change, found, err := u.reads.FindPolicyChange(ctx, versionNo)
	if err != nil {
		return PolicyChange{}, Sanitize(err)
	}
	if !found {
		return PolicyChange{}, NewError(PolicyEntryNotFound)
	}
	return change, nil
}

// ListManualEvents 分页查询人工操作审计；时间区间默认最近 30 天。
func (u *PolicyAdminUsecase) ListManualEvents(ctx context.Context, filter ManualEventFilter) (Page[ManualEvent], error) {
	if err := normalizePage(&filter.Page, &filter.Size); err != nil {
		return Page[ManualEvent]{}, err
	}
	if filter.To.IsZero() {
		filter.To = u.clock().UTC().Add(time.Second)
	}
	if filter.From.IsZero() {
		filter.From = filter.To.AddDate(0, 0, -30)
	}
	if !filter.From.Before(filter.To) {
		return Page[ManualEvent]{}, NewError(InvalidRequest)
	}
	page, err := u.reads.ListManualEvents(ctx, filter)
	return page, sanitizeOrNil(err)
}

// commitChange 在本地事务中执行变更并写入新版本的变更记录；版本号冲突或锁竞争时整体重试。
func (u *PolicyAdminUsecase) commitChange(ctx context.Context, actor PolicyActor, mutate func(tx TxStore, now time.Time) (PolicyChange, error)) (int, error) {
	if err := validateActor(actor); err != nil {
		return 0, err
	}
	for n := 0; n < maxPolicyChangeAttempts; n++ {
		version, err := u.commitOnce(ctx, actor, mutate)
		var duplicate *DuplicateError
		if errors.As(err, &duplicate) && duplicate.Kind == DuplicatePolicyVersion || errors.Is(err, ErrRetryableContention) {
			continue
		}
		return version, policyError(err)
	}
	return 0, NewError(DependencyUnavailable)
}

// commitOnce 执行一次事务：锁定最新版本 → 变更 → 写变更记录。
func (u *PolicyAdminUsecase) commitOnce(ctx context.Context, actor PolicyActor, mutate func(tx TxStore, now time.Time) (PolicyChange, error)) (int, error) {
	var version int
	err := u.store.InTransaction(ctx, func(tx TxStore) error {
		latest, err := tx.LockLatestPolicyVersion(ctx)
		if err != nil {
			return err
		}
		now := u.clock().UTC()
		change, err := mutate(tx, now)
		if err != nil {
			return err
		}
		id, err := NextID(ctx, u.ids)
		if err != nil {
			return err
		}
		change.VersionNo, change.Reason, change.ActorID, change.RequestID, change.CreateTime = latest+1, actor.Reason, actor.ActorID, actor.RequestID, now
		version = change.VersionNo
		return tx.InsertPolicyChange(ctx, id, change)
	})
	return version, err
}

// lockedDataType 锁定数据类型并校验乐观锁版本。
func (u *PolicyAdminUsecase) lockedDataType(ctx context.Context, tx TxStore, code string, expectedVersion int) (DataType, error) {
	current, found, err := tx.FindDataTypeForUpdate(ctx, code)
	if err != nil {
		return DataType{}, err
	}
	if !found {
		return DataType{}, NewError(PolicyEntryNotFound)
	}
	if current.Version != expectedVersion {
		return DataType{}, NewError(PolicyVersionConflict)
	}
	return current, nil
}

// requireActiveDataType 规则引用的数据类型须存在且 ACTIVE。
func requireActiveDataType(ctx context.Context, tx TxStore, code string) error {
	dataType, found, err := tx.FindDataTypeForUpdate(ctx, code)
	if err != nil {
		return err
	}
	if !found {
		return NewError(PolicyEntryNotFound)
	}
	if dataType.Status != ActiveStatus {
		return NewError(InvalidRequest)
	}
	return nil
}

// validateDataType 校验数据类型字段。
func validateDataType(d DataType) error {
	description := strings.TrimSpace(d.Description)
	if !IsCode(d.Code) || (d.TokenMode != Reference && d.TokenMode != Stable) || (d.Class != StandardClass && d.Class != SecretClass) ||
		(d.Status != ActiveStatus && d.Status != DisabledStatus) || description == "" || !utf8.ValidString(d.Description) || utf8.RuneCountInString(d.Description) > maxDescriptionRunes {
		return NewError(InvalidRequest)
	}
	return nil
}

// validateActor 校验操作人、请求 ID 与变更理由。
func validateActor(actor PolicyActor) error {
	reason := strings.TrimSpace(actor.Reason)
	if !actorPattern.MatchString(actor.ActorID) || actor.RequestID == "" || reason == "" || !utf8.ValidString(actor.Reason) || utf8.RuneCountInString(actor.Reason) > maxReasonRunes {
		return NewError(InvalidRequest)
	}
	return nil
}

// normalizePage 补默认分页并校验范围：页码从 1 开始，每页 1～200，默认 10（与 Java PagingRequestBody 一致，BFF 原样转发）。
func normalizePage(page, size *int) error {
	if *page == 0 {
		*page = 1
	}
	if *size == 0 {
		*size = 10
	}
	if *page < 1 || *page > 100000 || *size < 1 || *size > maxPageSize {
		return NewError(InvalidRequest)
	}
	return nil
}

// policyError 把唯一冲突映射为 POLICY_ENTRY_EXISTS，事务终态映射为依赖不可用，其余收敛为安全错误。
func policyError(err error) error {
	if err == nil {
		return nil
	}
	var duplicate *DuplicateError
	if errors.As(err, &duplicate) && (duplicate.Kind == DuplicateDataType || duplicate.Kind == DuplicateAccessRule) {
		return NewError(PolicyEntryExists)
	}
	if errors.Is(err, ErrTransactionOutcomeUnknown) || errors.Is(err, ErrTransactionRollbackFailed) {
		return NewError(DependencyUnavailable)
	}
	return Sanitize(err)
}

// conflictOr 条件更新未命中视为版本冲突；有错误则返回原错误。
func conflictOr(err error) error {
	if err != nil {
		return err
	}
	return NewError(PolicyVersionConflict)
}

func sanitizeOrNil(err error) error {
	if err == nil {
		return nil
	}
	return Sanitize(err)
}

// newChange 构造变更记录，快照为对象元数据 JSON。
func newChange(changeType, targetType, targetKey string, before, after any) (PolicyChange, error) {
	change := PolicyChange{ChangeType: changeType, TargetType: targetType, TargetKey: targetKey}
	var err error
	if before != nil {
		if change.Before, err = json.Marshal(before); err != nil {
			return PolicyChange{}, NewError(InternalError)
		}
	}
	if change.After, err = json.Marshal(after); err != nil {
		return PolicyChange{}, NewError(InternalError)
	}
	return change, nil
}

type dataTypeView struct {
	DataTypeCode         string `json:"dataTypeCode"`
	TokenMode            string `json:"tokenMode"`
	DataClass            string `json:"dataClass"`
	Description          string `json:"description"`
	ManualProtectEnabled bool   `json:"manualProtectEnabled"`
	ManualRevealEnabled  bool   `json:"manualRevealEnabled"`
	Status               string `json:"status"`
}

type ruleView struct {
	CallerService string `json:"callerService"`
	Action        string `json:"action"`
	DataTypeCode  string `json:"dataTypeCode"`
	PurposeCode   string `json:"purposeCode"`
	Status        string `json:"status"`
}

func dataTypeSnapshot(d DataType) dataTypeView {
	return dataTypeView{d.Code, string(d.TokenMode), string(d.Class), d.Description, d.ManualProtect, d.ManualReveal, string(d.Status)}
}

func ruleSnapshot(r AccessRule) ruleView {
	return ruleView{r.Caller, string(r.Action), r.DataTypeCode, r.Purpose, string(r.Status)}
}
