package mysql

import (
	"context"
	"database/sql"
	"database/sql/driver"
	"errors"
	"github.com/paytech/vault-service/internal/application"
	"github.com/paytech/vault-service/internal/domain"
)

type transaction struct{ tx *sql.Tx }

func (s *Store) InTransaction(ctx context.Context, fn func(application.TxStore) error) error {
	if fn == nil {
		return domain.NewError(domain.InternalError)
	}
	ctx, cancel := context.WithTimeout(ctx, s.queryTimeout)
	defer cancel()
	conn, err := s.db.Conn(ctx)
	if err != nil {
		return classify(err)
	}
	defer conn.Close()
	tx, err := conn.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	if err != nil {
		return classify(err)
	}
	ended := false
	defer func() {
		if !ended {
			if tx.Rollback() != nil {
				discard(conn)
			}
		}
	}()
	if err = fn(&transaction{tx: tx}); err != nil {
		rollbackErr := tx.Rollback()
		ended = true
		if rollbackErr == nil {
			return err
		}
		if !discard(conn) {
			return application.ErrTransactionOutcomeUnknown
		}
		return application.ErrTransactionRollbackFailed
	}
	if err = tx.Commit(); err != nil {
		tx.Rollback()
		ended = true
		discard(conn)
		return application.ErrTransactionOutcomeUnknown
	}
	ended = true
	return nil
}

// Returning ErrBadConn through Raw discards rather than returns the session.
func discard(conn *sql.Conn) bool {
	err := conn.Raw(func(any) error { return driver.ErrBadConn })
	return errors.Is(err, driver.ErrBadConn) || errors.Is(err, sql.ErrConnDone)
}
