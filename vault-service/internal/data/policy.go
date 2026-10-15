package data

import (
	"context"
	"database/sql"
	"errors"
	"time"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/internal/data/sqlc"
)

// LoadPolicy 在 REPEATABLE READ 只读事务中一致性读取 ACTIVE 数据类型、ACTIVE 规则与最大版本号。
func (s *Store) LoadPolicy(ctx context.Context) (biz.PolicyState, error) {
	ctx, cancel := context.WithTimeout(ctx, s.queryTimeout)
	defer cancel()
	tx, err := s.db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelRepeatableRead, ReadOnly: true})
	if err != nil {
		return biz.PolicyState{}, classify(err)
	}
	defer func() { _ = tx.Rollback() }()
	queries := sqlc.New(tx)
	version, err := queries.MaxPolicyVersion(ctx)
	if err != nil {
		return biz.PolicyState{}, classify(err)
	}
	types, err := queries.ListDataTypes(ctx)
	if err != nil {
		return biz.PolicyState{}, classify(err)
	}
	rules, err := queries.ListActiveAccessRules(ctx)
	if err != nil {
		return biz.PolicyState{}, classify(err)
	}
	state := biz.PolicyState{Version: int(version)}
	for _, row := range types {
		if row.Status == string(biz.ActiveStatus) {
			state.DataTypes = append(state.DataTypes, toDataType(row))
		}
	}
	for _, row := range rules {
		state.Rules = append(state.Rules, toAccessRule(row))
	}
	return state, nil
}

// ListDataTypes 返回全部数据类型（含停用），按编码排序。
func (s *Store) ListDataTypes(ctx context.Context) ([]biz.DataType, error) {
	ctx, cancel := context.WithTimeout(ctx, s.queryTimeout)
	defer cancel()
	rows, err := s.queries.ListDataTypes(ctx)
	if err != nil {
		return nil, classify(err)
	}
	result := make([]biz.DataType, 0, len(rows))
	for _, row := range rows {
		result = append(result, toDataType(row))
	}
	return result, nil
}

// ListAccessRules 分页查询访问规则。
func (s *Store) ListAccessRules(ctx context.Context, f biz.AccessRuleFilter) (biz.Page[biz.AccessRule], error) {
	ctx, cancel := context.WithTimeout(ctx, s.queryTimeout)
	defer cancel()
	rows, err := s.queries.ListAccessRules(ctx, sqlc.ListAccessRulesParams{DataTypeCode: f.DataTypeCode, CallerService: f.Caller, Action: f.Action, Status: f.Status, Limit: int32(f.Size), Offset: offset(f.Page, f.Size)})
	if err != nil {
		return biz.Page[biz.AccessRule]{}, classify(err)
	}
	total, err := s.queries.CountAccessRules(ctx, sqlc.CountAccessRulesParams{DataTypeCode: f.DataTypeCode, CallerService: f.Caller, Action: f.Action, Status: f.Status})
	if err != nil {
		return biz.Page[biz.AccessRule]{}, classify(err)
	}
	page := biz.Page[biz.AccessRule]{Items: make([]biz.AccessRule, 0, len(rows)), Page: f.Page, Size: f.Size, Total: total}
	for _, row := range rows {
		page.Items = append(page.Items, toAccessRule(row))
	}
	return page, nil
}

// ListPolicyChanges 分页查询变更记录（版本倒序）。
func (s *Store) ListPolicyChanges(ctx context.Context, f biz.PolicyChangeFilter) (biz.Page[biz.PolicyChange], error) {
	ctx, cancel := context.WithTimeout(ctx, s.queryTimeout)
	defer cancel()
	rows, err := s.queries.ListPolicyChanges(ctx, sqlc.ListPolicyChangesParams{TargetType: f.TargetType, TargetKey: f.TargetKey, Limit: int32(f.Size), Offset: offset(f.Page, f.Size)})
	if err != nil {
		return biz.Page[biz.PolicyChange]{}, classify(err)
	}
	total, err := s.queries.CountPolicyChanges(ctx, sqlc.CountPolicyChangesParams{TargetType: f.TargetType, TargetKey: f.TargetKey})
	if err != nil {
		return biz.Page[biz.PolicyChange]{}, classify(err)
	}
	page := biz.Page[biz.PolicyChange]{Items: make([]biz.PolicyChange, 0, len(rows)), Page: f.Page, Size: f.Size, Total: total}
	for _, row := range rows {
		page.Items = append(page.Items, toPolicyChange(row))
	}
	return page, nil
}

// FindPolicyChange 按版本号读取变更详情。
func (s *Store) FindPolicyChange(ctx context.Context, versionNo int) (biz.PolicyChange, bool, error) {
	ctx, cancel := context.WithTimeout(ctx, s.queryTimeout)
	defer cancel()
	row, err := s.queries.FindPolicyChange(ctx, int32(versionNo))
	if errors.Is(err, sql.ErrNoRows) {
		return biz.PolicyChange{}, false, nil
	}
	if err != nil {
		return biz.PolicyChange{}, false, classify(err)
	}
	return toPolicyChange(row), true, nil
}

// MaxPolicyVersion 返回当前最大版本号；无变更记录时为 0。
func (s *Store) MaxPolicyVersion(ctx context.Context) (int, error) {
	ctx, cancel := context.WithTimeout(ctx, s.queryTimeout)
	defer cancel()
	version, err := s.queries.MaxPolicyVersion(ctx)
	return int(version), classify(err)
}

// ListManualEvents 分页查询人工操作审计（actor_id 非空）。
func (s *Store) ListManualEvents(ctx context.Context, f biz.ManualEventFilter) (biz.Page[biz.ManualEvent], error) {
	ctx, cancel := context.WithTimeout(ctx, s.queryTimeout)
	defer cancel()
	// 空串参与 “= '' OR 列 = 参数” 的过滤，必须以非空的 NullString 传入，否则比较结果为 NULL。
	actor, dataType := sql.NullString{String: f.ActorID, Valid: true}, sql.NullString{String: f.DataTypeCode, Valid: true}
	rows, err := s.queries.ListManualEvents(ctx, sqlc.ListManualEventsParams{ActorID: actor, Action: f.Action, DataType: dataType, FromTime: utc(f.From), ToTime: utc(f.To), Limit: int32(f.Size), Offset: offset(f.Page, f.Size)})
	if err != nil {
		return biz.Page[biz.ManualEvent]{}, classify(err)
	}
	total, err := s.queries.CountManualEvents(ctx, sqlc.CountManualEventsParams{ActorID: actor, Action: f.Action, DataType: dataType, FromTime: utc(f.From), ToTime: utc(f.To)})
	if err != nil {
		return biz.Page[biz.ManualEvent]{}, classify(err)
	}
	page := biz.Page[biz.ManualEvent]{Items: make([]biz.ManualEvent, 0, len(rows)), Page: f.Page, Size: f.Size, Total: total}
	for _, row := range rows {
		page.Items = append(page.Items, biz.ManualEvent{ID: row.AccessEventID, RequestID: row.RequestID, ActorID: row.ActorID.String, Action: biz.Action(row.Action),
			DataType: row.DataType.String, Outcome: biz.Outcome(row.Outcome), ResultCode: row.ResultCode, ReasonCode: row.ReasonCode.String, TicketRef: row.TicketRef.String, CreateTime: row.CreateTime})
	}
	return page, nil
}

// FindDataTypeForUpdate 以排他锁读取数据类型。
func (t *txRepository) FindDataTypeForUpdate(ctx context.Context, code string) (biz.DataType, bool, error) {
	row, err := t.queries.FindDataTypeForUpdate(ctx, code)
	if errors.Is(err, sql.ErrNoRows) {
		return biz.DataType{}, false, nil
	}
	if err != nil {
		return biz.DataType{}, false, classify(err)
	}
	return toDataType(row), true, nil
}

// InsertDataType 新增数据类型，版本从 1 开始。
func (t *txRepository) InsertDataType(ctx context.Context, id int64, d biz.DataType, now time.Time) error {
	return classify(t.queries.InsertDataType(ctx, sqlc.InsertDataTypeParams{DataTypeID: id, DataTypeCode: d.Code, TokenMode: string(d.TokenMode), DataClass: string(d.Class),
		Description: d.Description, ManualProtectEnabled: d.ManualProtect, ManualRevealEnabled: d.ManualReveal, Status: string(d.Status), Version: 1, CreateTime: utc(now), UpdateTime: utc(now)}))
}

// UpdateDataType 按乐观锁更新可变字段。
func (t *txRepository) UpdateDataType(ctx context.Context, d biz.DataType, expectedVersion int, now time.Time) (bool, error) {
	affected, err := t.queries.UpdateDataType(ctx, sqlc.UpdateDataTypeParams{DataClass: string(d.Class), Description: d.Description, ManualProtectEnabled: d.ManualProtect,
		ManualRevealEnabled: d.ManualReveal, Status: string(d.Status), UpdateTime: utc(now), DataTypeCode: d.Code, Version: int32(expectedVersion)})
	return affected == 1, classify(err)
}

// FindAccessRuleForUpdate 以排他锁读取访问规则。
func (t *txRepository) FindAccessRuleForUpdate(ctx context.Context, id int64) (biz.AccessRule, bool, error) {
	row, err := t.queries.FindAccessRuleForUpdate(ctx, id)
	if errors.Is(err, sql.ErrNoRows) {
		return biz.AccessRule{}, false, nil
	}
	if err != nil {
		return biz.AccessRule{}, false, classify(err)
	}
	return toAccessRule(row), true, nil
}

// InsertAccessRule 新增访问规则，版本从 1 开始。
func (t *txRepository) InsertAccessRule(ctx context.Context, r biz.AccessRule, now time.Time) error {
	return classify(t.queries.InsertAccessRule(ctx, sqlc.InsertAccessRuleParams{AccessRuleID: r.ID, CallerService: r.Caller, Action: string(r.Action), DataTypeCode: r.DataTypeCode,
		PurposeCode: r.Purpose, Status: string(r.Status), Version: 1, CreateTime: utc(now), UpdateTime: utc(now)}))
}

// UpdateAccessRuleStatus 按乐观锁更新规则状态。
func (t *txRepository) UpdateAccessRuleStatus(ctx context.Context, id int64, status biz.EntryStatus, expectedVersion int, now time.Time) (bool, error) {
	affected, err := t.queries.UpdateAccessRuleStatus(ctx, sqlc.UpdateAccessRuleStatusParams{Status: string(status), UpdateTime: utc(now), AccessRuleID: id, Version: int32(expectedVersion)})
	return affected == 1, classify(err)
}

// LockLatestPolicyVersion 锁定最新的变更记录并返回其版本号；无记录时为 0。
func (t *txRepository) LockLatestPolicyVersion(ctx context.Context) (int, error) {
	versions, err := t.queries.LockLatestPolicyVersion(ctx)
	if err != nil {
		return 0, classify(err)
	}
	if len(versions) == 0 {
		return 0, nil
	}
	return int(versions[0]), nil
}

// InsertPolicyChange 写入变更记录；版本号重复返回 DuplicateError。
func (t *txRepository) InsertPolicyChange(ctx context.Context, id int64, c biz.PolicyChange) error {
	return classify(t.queries.InsertPolicyChange(ctx, sqlc.InsertPolicyChangeParams{PolicyChangeID: id, PolicyVersionNo: int32(c.VersionNo), ChangeType: c.ChangeType, TargetType: c.TargetType,
		TargetKey: c.TargetKey, BeforeSnapshot: c.Before, AfterSnapshot: c.After, Reason: c.Reason, ActorID: c.ActorID, RequestID: c.RequestID, CreateTime: utc(c.CreateTime)}))
}

func toDataType(row sqlc.VaultDataType) biz.DataType {
	return biz.DataType{Code: row.DataTypeCode, TokenMode: biz.TokenMode(row.TokenMode), Class: biz.DataClass(row.DataClass), Description: row.Description,
		ManualProtect: row.ManualProtectEnabled, ManualReveal: row.ManualRevealEnabled, Status: biz.EntryStatus(row.Status), Version: int(row.Version), UpdateTime: row.UpdateTime}
}

func toAccessRule(row sqlc.VaultAccessRule) biz.AccessRule {
	return biz.AccessRule{ID: row.AccessRuleID, Caller: row.CallerService, Action: biz.Action(row.Action), DataTypeCode: row.DataTypeCode, Purpose: row.PurposeCode,
		Status: biz.EntryStatus(row.Status), Version: int(row.Version), UpdateTime: row.UpdateTime}
}

func toPolicyChange(row sqlc.VaultPolicyChange) biz.PolicyChange {
	return biz.PolicyChange{VersionNo: int(row.PolicyVersionNo), ChangeType: row.ChangeType, TargetType: row.TargetType, TargetKey: row.TargetKey, Before: row.BeforeSnapshot,
		After: row.AfterSnapshot, Reason: row.Reason, ActorID: row.ActorID, RequestID: row.RequestID, CreateTime: row.CreateTime}
}

// offset 由页码与每页条数计算偏移。
func offset(page, size int) int32 { return int32((page - 1) * size) }
