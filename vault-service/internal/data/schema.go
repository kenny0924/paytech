package data

import (
	"context"

	"github.com/paytech/vault-service/internal/biz"
)

// tablesQuery 列出已手工建好的 Vault 三张表。information_schema 不在 sqlc 的 Schema 内，故手写。
const tablesQuery = `SELECT TABLE_NAME FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('t_vault_access_event', 't_vault_protect_request', 't_vault_record')`

// requiredTables 是启动前必须存在的表数量；表结构以 docs/database/sql/vault-schema.sql 为准，由 sqlc 生成代码对齐。
const requiredTables = 3

// verifyTables 启动时确认三张表存在，缺失即启动失败；不执行任何 DDL。
func (s *Store) verifyTables(ctx context.Context) error {
	ctx, cancel := context.WithTimeout(ctx, s.queryTimeout)
	defer cancel()
	rows, err := s.db.QueryContext(ctx, tablesQuery)
	if err != nil {
		return classify(err)
	}
	defer func() { _ = rows.Close() }()
	found := 0
	for rows.Next() {
		found++
	}
	if err := rows.Err(); err != nil {
		return classify(err)
	}
	if found != requiredTables {
		return biz.NewError(biz.InternalError)
	}
	return nil
}
