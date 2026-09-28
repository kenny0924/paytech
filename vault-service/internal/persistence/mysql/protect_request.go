package mysql

import (
	"context"
	"database/sql"
	"errors"
	"github.com/paytech/vault-service/internal/domain"
)

func (t *transaction) FindProtectRequest(ctx context.Context, caller, key string, lock bool) (domain.ProtectRequest, bool, error) {
	query := "SELECT protect_request_id,caller_service,idempotency_key,request_hash,record_id,create_time,update_time FROM t_vault_protect_request WHERE caller_service=? AND idempotency_key=?"
	if lock {
		query += " FOR UPDATE"
	}
	var p domain.ProtectRequest
	var hash []byte
	err := t.tx.QueryRowContext(ctx, query, caller, key).Scan(&p.ID, &p.Caller, &p.IdempotencyKey, &hash, &p.RecordID, &p.CreateTime, &p.UpdateTime)
	if errors.Is(err, sql.ErrNoRows) {
		return domain.ProtectRequest{}, false, nil
	}
	if err != nil {
		return domain.ProtectRequest{}, false, classify(err)
	}
	if len(hash) != 32 {
		return domain.ProtectRequest{}, false, domain.NewError(domain.InternalError)
	}
	copy(p.RequestHash[:], hash)
	return p, true, nil
}
func (t *transaction) InsertProtectRequest(ctx context.Context, p domain.ProtectRequest) error {
	_, err := t.tx.ExecContext(ctx, "INSERT INTO t_vault_protect_request (protect_request_id,caller_service,idempotency_key,request_hash,record_id,create_time,update_time) VALUES (?,?,?,?,?,?,?)", p.ID, p.Caller, p.IdempotencyKey, p.RequestHash[:], p.RecordID, utc(p.CreateTime), utc(p.UpdateTime))
	return classify(err)
}
