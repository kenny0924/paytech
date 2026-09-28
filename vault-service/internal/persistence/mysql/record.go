package mysql

import (
	"context"
	"database/sql"
	"errors"
	"github.com/paytech/vault-service/internal/domain"
	"time"
)

const recordColumns = "record_id,token,data_type,token_mode,value_fingerprint,ciphertext,encryption_nonce,wrapped_dek,kms_key_ref,create_time,update_time"

func (s *Store) FindRecordByToken(ctx context.Context, token string) (domain.Record, bool, error) {
	ctx, cancel := context.WithTimeout(ctx, s.queryTimeout)
	defer cancel()
	return scanRecord(s.db.QueryRowContext(ctx, "SELECT "+recordColumns+" FROM t_vault_record WHERE token=?", token))
}
func (t *transaction) FindRecordByID(ctx context.Context, id int64) (domain.Record, bool, error) {
	return scanRecord(t.tx.QueryRowContext(ctx, "SELECT "+recordColumns+" FROM t_vault_record WHERE record_id=?", id))
}
func (t *transaction) FindRecordByStableIdentityForShare(ctx context.Context, dataType string, fp [32]byte) (domain.Record, bool, error) {
	return scanRecord(t.tx.QueryRowContext(ctx, "SELECT "+recordColumns+" FROM t_vault_record WHERE data_type=? AND value_fingerprint=? FOR SHARE", dataType, fp[:]))
}
func scanRecord(row *sql.Row) (domain.Record, bool, error) {
	var r domain.Record
	var fp, nonce []byte
	err := row.Scan(&r.ID, &r.Token, &r.DataType, &r.TokenMode, &fp, &r.Ciphertext, &nonce, &r.WrappedDEK, &r.KMSKeyRef, &r.CreateTime, &r.UpdateTime)
	if errors.Is(err, sql.ErrNoRows) {
		return domain.Record{}, false, nil
	}
	if err != nil {
		return domain.Record{}, false, classify(err)
	}
	if len(nonce) != 12 || (fp != nil && len(fp) != 32) || (r.TokenMode != domain.Stable && r.TokenMode != domain.Reference) || (r.TokenMode == domain.Stable) != (fp != nil) {
		return domain.Record{}, false, domain.NewError(domain.InternalError)
	}
	copy(r.EncryptionNonce[:], nonce)
	if fp != nil {
		r.ValueFingerprint = new([32]byte)
		copy(r.ValueFingerprint[:], fp)
	}
	return r, true, nil
}
func utc(t time.Time) time.Time { return t.UTC().Truncate(time.Microsecond) }
func (t *transaction) InsertRecord(ctx context.Context, r domain.Record) error {
	var fp any
	if r.ValueFingerprint != nil {
		fp = r.ValueFingerprint[:]
	}
	_, err := t.tx.ExecContext(ctx, "INSERT INTO t_vault_record ("+recordColumns+") VALUES (?,?,?,?,?,?,?,?,?,?,?)", r.ID, r.Token, r.DataType, r.TokenMode, fp, r.Ciphertext, r.EncryptionNonce[:], r.WrappedDEK, r.KMSKeyRef, utc(r.CreateTime), utc(r.UpdateTime))
	return classify(err)
}
