// Package dal 是测试专用的数据访问（对应 Java TestMapper）：Checker 读取与清理用独立 SQL，不复用被测查询。
package dal

import (
	"context"
	"database/sql"
	"errors"
	"strings"
	"time"

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
	rows, err := m.db.QueryContext(ctx, `SELECT request_id, caller_service, action, data_type, purpose, record_id, outcome, result_code, policy_version
		FROM t_vault_access_event WHERE request_id = ? ORDER BY access_event_id`, requestID)
	if err != nil {
		return nil, err
	}
	defer func() { _ = rows.Close() }()
	var result []AccessEventRow
	for rows.Next() {
		var row AccessEventRow
		if err := rows.Scan(&row.RequestID, &row.Caller, &row.Action, &row.DataType, &row.Purpose, &row.RecordID, &row.Outcome, &row.ResultCode, &row.PolicyVersion); err != nil {
			return nil, err
		}
		result = append(result, row)
	}
	return result, rows.Err()
}

// CountOwned 统计本次运行拥有的记录、幂等映射与审计事件。
func (m *TestMapper) CountOwned(ctx context.Context) (records, requests, events int, err error) {
	if records, err = m.count(ctx, `SELECT COUNT(*) FROM t_vault_record WHERE data_type IN (?, ?)`, m.dataTypeArgs()...); err != nil {
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
	OR record_id IN (SELECT record_id FROM t_vault_record WHERE data_type IN (?, ?))`

// DeleteOwned 在一个事务内按外键顺序删除本次运行的数据：审计 → 幂等映射 → 记录。
func (m *TestMapper) DeleteOwned(ctx context.Context) error {
	ctx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()
	tx, err := m.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer func() { _ = tx.Rollback() }()
	statements := []struct {
		query string
		args  []any
	}{
		{`DELETE FROM t_vault_access_event WHERE ` + ownedEventCondition, m.ownedEventArgs()},
		{`DELETE FROM t_vault_protect_request WHERE caller_service LIKE ?
			OR record_id IN (SELECT record_id FROM (SELECT record_id FROM t_vault_record WHERE data_type IN (?, ?)) owned)`,
			append([]any{m.likePrefix()}, m.dataTypeArgs()...)},
		{`DELETE FROM t_vault_record WHERE data_type IN (?, ?)`, m.dataTypeArgs()},
	}
	for _, statement := range statements {
		if _, err := tx.ExecContext(ctx, statement.query, statement.args...); err != nil {
			return err
		}
	}
	return tx.Commit()
}

func (m *TestMapper) count(ctx context.Context, query string, args ...any) (int, error) {
	var n int
	err := m.db.QueryRowContext(ctx, query, args...).Scan(&n)
	return n, err
}

func (m *TestMapper) likePrefix() string {
	return strings.ReplaceAll(m.id.Prefix(), "_", `\_`) + "%"
}

func (m *TestMapper) dataTypeArgs() []any {
	types := m.id.DataTypes()
	return []any{types[0], types[1]}
}

func (m *TestMapper) ownedEventArgs() []any {
	return append([]any{m.likePrefix(), m.likePrefix()}, m.dataTypeArgs()...)
}
