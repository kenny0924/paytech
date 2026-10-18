package data

import (
	"context"
	"database/sql"
	"errors"
	"time"

	"github.com/paytech/vault-service/internal/biz"
	"github.com/paytech/vault-service/internal/data/sqlc"
)

// txRepository 是绑定单个事务的 biz.TxStore 实现，只委托 sqlc 查询并转换类型。
type txRepository struct {
	tx      *sql.Tx
	queries *sqlc.Queries
}

// FindRecordByToken 事务外按 Token 读取记录。
func (s *Store) FindRecordByToken(ctx context.Context, token string) (biz.Record, bool, error) {
	ctx, cancel := context.WithTimeout(ctx, s.queryTimeout)
	defer cancel()
	return toRecord(s.queries.FindRecordByToken(ctx, token))
}

// FindRecordByID 按主键读取记录。
func (t *txRepository) FindRecordByID(ctx context.Context, id int64) (biz.Record, bool, error) {
	return toRecord(t.queries.FindRecordByID(ctx, id))
}

// FindRecordByStableIdentityForShare 以共享锁按 STABLE 身份读取记录。
func (t *txRepository) FindRecordByStableIdentityForShare(ctx context.Context, dataType string, fingerprint [32]byte) (biz.Record, bool, error) {
	return toRecord(t.queries.FindRecordByStableIdentityForShare(ctx, sqlc.FindRecordByStableIdentityForShareParams{DataType: dataType, ValueFingerprint: fingerprint[:]}))
}

// InsertRecord 新增记录。
func (t *txRepository) InsertRecord(ctx context.Context, r biz.Record) error {
	var fingerprint []byte
	if r.ValueFingerprint != nil {
		fingerprint = r.ValueFingerprint[:]
	}
	return classify(t.queries.InsertRecord(ctx, sqlc.InsertRecordParams{
		RecordID: r.ID, Token: r.Token, DataType: r.DataType, TokenMode: string(r.TokenMode), ValueFingerprint: fingerprint,
		Ciphertext: r.Ciphertext, EncryptionNonce: r.EncryptionNonce[:], WrappedDEK: r.WrappedDEK, KMSKeyRef: r.KMSKeyRef,
		CreateTime: utc(r.CreateTime), UpdateTime: utc(r.UpdateTime),
	}))
}

// FindProtectRequestForUpdate 以排他锁读取请求映射。
func (t *txRepository) FindProtectRequestForUpdate(ctx context.Context, caller, idempotencyKey string) (biz.ProtectRequest, bool, error) {
	row, err := t.queries.FindProtectRequestForUpdate(ctx, sqlc.FindProtectRequestForUpdateParams{CallerService: caller, IdempotencyKey: idempotencyKey})
	if errors.Is(err, sql.ErrNoRows) {
		return biz.ProtectRequest{}, false, nil
	}
	if err != nil {
		return biz.ProtectRequest{}, false, classify(err)
	}
	if len(row.RequestHash) != 32 {
		return biz.ProtectRequest{}, false, biz.NewError(biz.InternalError)
	}
	request := biz.ProtectRequest{ID: row.ProtectRequestID, Caller: row.CallerService, IdempotencyKey: row.IdempotencyKey, RecordID: row.RecordID, CreateTime: row.CreateTime, UpdateTime: row.UpdateTime}
	copy(request.RequestHash[:], row.RequestHash)
	return request, true, nil
}

// InsertProtectRequest 新增请求映射。
func (t *txRepository) InsertProtectRequest(ctx context.Context, p biz.ProtectRequest) error {
	return classify(t.queries.InsertProtectRequest(ctx, sqlc.InsertProtectRequestParams{
		ProtectRequestID: p.ID, CallerService: p.Caller, IdempotencyKey: p.IdempotencyKey, RequestHash: p.RequestHash[:],
		RecordID: p.RecordID, CreateTime: utc(p.CreateTime), UpdateTime: utc(p.UpdateTime),
	}))
}

// InsertAccessEvent 追加访问审计事件。
func (t *txRepository) InsertAccessEvent(ctx context.Context, e biz.AccessEvent) error {
	return classify(t.queries.InsertAccessEvent(ctx, sqlc.InsertAccessEventParams{
		AccessEventID: e.ID, RequestID: e.RequestID, TraceID: nullString(e.TraceID), CallerService: e.Caller, ActorID: nullString(e.ActorID), Action: string(e.Action),
		DataType: nullString(e.DataType), Purpose: nullString(e.Purpose), RecordID: nullInt64(e.RecordID), Outcome: string(e.Outcome),
		ResultCode: e.ResultCode, PolicyVersion: nullString(e.PolicyVersion), ReasonCode: nullString(e.ReasonCode), TicketRef: nullString(e.TicketRef), CreateTime: utc(e.CreateTime),
	}))
}

// toRecord 把查询结果转换为领域记录，并拒绝与表约束不一致的行。
func toRecord(row sqlc.VaultRecord, err error) (biz.Record, bool, error) {
	if errors.Is(err, sql.ErrNoRows) {
		return biz.Record{}, false, nil
	}
	if err != nil {
		return biz.Record{}, false, classify(err)
	}
	mode := biz.TokenMode(row.TokenMode)
	fingerprint := row.ValueFingerprint
	if len(row.EncryptionNonce) != 12 || (fingerprint != nil && len(fingerprint) != 32) || (mode != biz.Stable && mode != biz.Reference) || (mode == biz.Stable) != (fingerprint != nil) {
		return biz.Record{}, false, biz.NewError(biz.InternalError)
	}
	record := biz.Record{ID: row.RecordID, Token: row.Token, DataType: row.DataType, TokenMode: mode, Ciphertext: row.Ciphertext, WrappedDEK: row.WrappedDEK, KMSKeyRef: row.KMSKeyRef, CreateTime: row.CreateTime, UpdateTime: row.UpdateTime}
	copy(record.EncryptionNonce[:], row.EncryptionNonce)
	if fingerprint != nil {
		record.ValueFingerprint = new([32]byte)
		copy(record.ValueFingerprint[:], fingerprint)
	}
	return record, true, nil
}

// utc 统一为 UTC 微秒精度，与 DATETIME(6) 一致。
func utc(t time.Time) time.Time { return t.UTC().Truncate(time.Microsecond) }

func nullString(value *string) sql.NullString {
	if value == nil {
		return sql.NullString{}
	}
	return sql.NullString{String: *value, Valid: true}
}

func nullInt64(value *int64) sql.NullInt64 {
	if value == nil {
		return sql.NullInt64{}
	}
	return sql.NullInt64{Int64: *value, Valid: true}
}
