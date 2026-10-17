package data

import (
	"context"
	"database/sql"
	"database/sql/driver"
	"errors"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/internal/data/sqlc"
)

// InTransaction 在专用连接上开启 READ COMMITTED 事务；回滚或提交结果无法确认时隔离该连接。
func (s *Store) InTransaction(ctx context.Context, fn func(biz.TxStore) error) error {
	if fn == nil {
		return biz.NewError(biz.InternalError)
	}
	ctx, cancel := context.WithTimeout(ctx, s.queryTimeout)
	defer cancel()
	conn, err := s.db.Conn(ctx)
	if err != nil {
		return classify(err)
	}
	defer func() { _ = conn.Close() }()
	tx, err := conn.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return classify(err)
	}
	ended := false
	defer func() {
		// 回调 panic 时仍回滚；回滚失败则丢弃连接，不把会话还回连接池。
		if !ended && tx.Rollback() != nil {
			discard(conn)
		}
	}()
	err = fn(&txRepository{tx: tx, queries: sqlc.New(tx)})
	ended = true
	return finish(conn, tx, err)
}

// finish 按回调结果提交或回滚；提交失败时结果无法确认。
func finish(conn *sql.Conn, tx *sql.Tx, cause error) error {
	if cause != nil {
		return rollback(conn, tx, cause)
	}
	if tx.Commit() != nil {
		_ = tx.Rollback()
		discard(conn)
		return biz.ErrTransactionOutcomeUnknown
	}
	return nil
}

// rollback 回滚并返回原因；回滚失败且连接无法隔离时结果无法确认。
func rollback(conn *sql.Conn, tx *sql.Tx, cause error) error {
	if tx.Rollback() == nil {
		return cause
	}
	if !discard(conn) {
		return biz.ErrTransactionOutcomeUnknown
	}
	return biz.ErrTransactionRollbackFailed
}

// discard 经 Raw 返回 ErrBadConn，使连接池丢弃而不是回收该会话。
func discard(conn *sql.Conn) bool {
	err := conn.Raw(func(any) error { return driver.ErrBadConn })
	return errors.Is(err, driver.ErrBadConn) || errors.Is(err, sql.ErrConnDone)
}
