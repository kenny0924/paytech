// Package dal 是测试专用的数据访问（对应 Java TestMapper）：Checker 读取与清理用独立 SQL，不复用被测查询。
package dal

import (
	"context"
	"crypto/rand"
	"database/sql"
	"encoding/binary"
	"errors"
	"strings"
	"time"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/test/support/fixture"
)

// TestMapper 只操作本次运行拥有的数据。
type TestMapper struct {
	db *sql.DB
	id *fixture.Identity
}

// RecordRow 是 t_vault_record 的检查投影，不读取密文。
type RecordRow struct {
	RecordID       int64
	Token          string
	DataType       string
	TokenMode      string
	HasFingerprint bool
}

// ProtectRequestRow 是 t_vault_protect_request 的检查投影。
type ProtectRequestRow struct {
	Caller         string
	IdempotencyKey string
	RecordID       int64
}

// AccessEventRow 是 t_vault_access_event 的检查投影。
type AccessEventRow struct {
	RequestID     string
	Caller        string
	Action        string
	DataType      sql.NullString
	Purpose       sql.NullString
	RecordID      sql.NullInt64
	Outcome       string
	ResultCode    string
	PolicyVersion sql.NullString
	ActorID       sql.NullString
	ReasonCode    sql.NullString
	TicketRef     sql.NullString
}

// NewTestMapper 绑定数据库与本次运行标识。
func NewTestMapper(db *sql.DB, id *fixture.Identity) *TestMapper { return &TestMapper{db: db, id: id} }

// FindRecordByToken 按 Token 读取记录。
func (m *TestMapper) FindRecordByToken(ctx context.Context, token string) (RecordRow, bool, error) {
	var row RecordRow
	err := m.db.QueryRowContext(ctx, `SELECT record_id, token, data_type, token_mode, value_fingerprint IS NOT NULL
		FROM t_vault_record WHERE token = ?`, token).Scan(&row.RecordID, &row.Token, &row.DataType, &row.TokenMode, &row.HasFingerprint)
	if errors.Is(err, sql.ErrNoRows) {
		return RecordRow{}, false, nil
	}
	return row, err == nil, err
}

// CountRecords 统计指定数据类型的记录数。
func (m *TestMapper) CountRecords(ctx context.Context, dataType string) (int, error) {
	return m.count(ctx, `SELECT COUNT(*) FROM t_vault_record WHERE data_type = ?`, dataType)
}

// ListProtectRequests 读取调用方的幂等映射。
func (m *TestMapper) ListProtectRequests(ctx context.Context, caller string) ([]ProtectRequestRow, error) {
	rows, err := m.db.QueryContext(ctx, `SELECT caller_service, idempotency_key, record_id FROM t_vault_protect_request
		WHERE caller_service = ? ORDER BY protect_request_id`, caller)
	if err != nil {
		return nil, err
	}
	defer func() { _ = rows.Close() }()
	var result []ProtectRequestRow
	for rows.Next() {
		var row ProtectRequestRow
		if err := rows.Scan(&row.Caller, &row.IdempotencyKey, &row.RecordID); err != nil {
			return nil, err
		}
		result = append(result, row)
	}
	return result, rows.Err()
}

// ListAccessEvents 读取指定请求 ID 的访问审计。
func (m *TestMapper) ListAccessEvents(ctx context.Context, requestID string) ([]AccessEventRow, error) {
	rows, err := m.db.QueryContext(ctx, `SELECT request_id, caller_service, action, data_type, purpose, record_id, outcome, result_code, policy_version, actor_id, reason_code, ticket_ref
		FROM t_vault_access_event WHERE request_id = ? ORDER BY access_event_id`, requestID)
	if err != nil {
		return nil, err
	}
	defer func() { _ = rows.Close() }()
	var result []AccessEventRow
	for rows.Next() {
		var row AccessEventRow
		if err := rows.Scan(&row.RequestID, &row.Caller, &row.Action, &row.DataType, &row.Purpose, &row.RecordID, &row.Outcome, &row.ResultCode, &row.PolicyVersion, &row.ActorID, &row.ReasonCode, &row.TicketRef); err != nil {
			return nil, err
		}
		result = append(result, row)
	}
	return result, rows.Err()
}

// CountOwned 统计本次运行拥有的记录、幂等映射与审计事件（不含策略数据）。
func (m *TestMapper) CountOwned(ctx context.Context) (records, requests, events int, err error) {
	if records, err = m.count(ctx, `SELECT COUNT(*) FROM t_vault_record WHERE data_type LIKE ?`, m.typeLike()); err != nil {
		return
	}
	if requests, err = m.count(ctx, `SELECT COUNT(*) FROM t_vault_protect_request WHERE caller_service LIKE ?`, m.likePrefix()); err != nil {
		return
	}
	events, err = m.count(ctx, `SELECT COUNT(*) FROM t_vault_access_event WHERE `+ownedEventCondition, m.ownedEventArgs()...)
	return
}

// ownedEventCondition 识别本次运行的审计：请求 ID 前缀、调用方前缀或指向本次运行记录。
const ownedEventCondition = `request_id LIKE ? OR caller_service LIKE ?
	OR record_id IN (SELECT record_id FROM t_vault_record WHERE data_type LIKE ?)`

// ResetOwned 在一个事务内删除本次运行的全部数据（审计 → 幂等映射 → 记录 → 策略与变更记录），
// 再按 seed 重新写入初始数据类型与规则；返回本次是否删除了策略变更记录（即用例改过策略）。
func (m *TestMapper) ResetOwned(ctx context.Context, types []biz.DataType, rules []biz.AccessRule) (bool, error) {
	ctx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()
	tx, err := m.db.BeginTx(ctx, nil)
	if err != nil {
		return false, err
	}
	defer func() { _ = tx.Rollback() }()
	changed, err := m.deleteOwned(ctx, tx)
	if err != nil {
		return false, err
	}
	if err := insertSeed(ctx, tx, types, rules); err != nil {
		return false, err
	}
	return changed, tx.Commit()
}

// DeleteOwned 删除本次运行的全部数据，包括初始策略；用于测试包结束。
func (m *TestMapper) DeleteOwned(ctx context.Context) error {
	_, err := m.ResetOwned(ctx, nil, nil)
	return err
}

func (m *TestMapper) deleteOwned(ctx context.Context, tx *sql.Tx) (bool, error) {
	changes, err := tx.ExecContext(ctx, `DELETE FROM t_vault_policy_change WHERE actor_id LIKE ? OR target_key LIKE ?`, m.likePrefix(), m.typeLike())
	if err != nil {
		return false, err
	}
	statements := []struct {
		query string
		args  []any
	}{
		{`DELETE FROM t_vault_access_event WHERE ` + ownedEventCondition, m.ownedEventArgs()},
		{`DELETE FROM t_vault_protect_request WHERE caller_service LIKE ?
			OR record_id IN (SELECT record_id FROM (SELECT record_id FROM t_vault_record WHERE data_type LIKE ?) owned)`, []any{m.likePrefix(), m.typeLike()}},
		{`DELETE FROM t_vault_record WHERE data_type LIKE ?`, []any{m.typeLike()}},
		{`DELETE FROM t_vault_access_rule WHERE caller_service LIKE ? OR data_type_code LIKE ?`, []any{m.likePrefix(), m.typeLike()}},
		{`DELETE FROM t_vault_data_type WHERE data_type_code LIKE ?`, []any{m.typeLike()}},
	}
	for _, statement := range statements {
		if _, err := tx.ExecContext(ctx, statement.query, statement.args...); err != nil {
			return false, err
		}
	}
	affected, err := changes.RowsAffected()
	return affected > 0, err
}

// insertSeed 写入初始数据类型与规则；不写变更记录（与环境初始化 DML 一致，热加载按内容生效）。
func insertSeed(ctx context.Context, tx *sql.Tx, types []biz.DataType, rules []biz.AccessRule) error {
	for _, d := range types {
		if _, err := tx.ExecContext(ctx, `INSERT INTO t_vault_data_type (data_type_id, data_type_code, token_mode, data_class, description, manual_protect_enabled,
			manual_reveal_enabled, status, version, create_time, update_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))`,
			randomID(), d.Code, d.TokenMode, d.Class, d.Description, d.ManualProtect, d.ManualReveal, d.Status); err != nil {
			return err
		}
	}
	for _, r := range rules {
		if _, err := tx.ExecContext(ctx, `INSERT INTO t_vault_access_rule (access_rule_id, caller_service, action, data_type_code, purpose_code, status, version,
			create_time, update_time) VALUES (?, ?, ?, ?, ?, ?, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))`,
			randomID(), r.Caller, r.Action, r.DataTypeCode, r.Purpose, r.Status); err != nil {
			return err
		}
	}
	return nil
}

// randomID 生成测试数据主键（2^40 以上的随机正数，与 Snowflake 主键区间不同）。
func randomID() int64 {
	var raw [8]byte
	if _, err := rand.Read(raw[:]); err != nil {
		panic(err)
	}
	return int64(binary.BigEndian.Uint64(raw[:])>>1) | 1<<40
}

func (m *TestMapper) count(ctx context.Context, query string, args ...any) (int, error) {
	var n int
	err := m.db.QueryRowContext(ctx, query, args...).Scan(&n)
	return n, err
}

func (m *TestMapper) likePrefix() string {
	return strings.ReplaceAll(m.id.Prefix(), "_", `\_`) + "%"
}

func (m *TestMapper) typeLike() string {
	return strings.ReplaceAll(m.id.TypePrefix(), "_", `\_`) + "%"
}

func (m *TestMapper) ownedEventArgs() []any {
	return []any{m.likePrefix(), m.likePrefix(), m.typeLike()}
}
