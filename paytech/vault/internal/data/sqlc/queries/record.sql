-- name: FindRecordByToken :one
SELECT record_id, token, data_type, token_mode, value_fingerprint, ciphertext, encryption_nonce, wrapped_dek, kms_key_ref, create_time, update_time
FROM t_vault_record
WHERE token = ?;

-- name: FindRecordByID :one
SELECT record_id, token, data_type, token_mode, value_fingerprint, ciphertext, encryption_nonce, wrapped_dek, kms_key_ref, create_time, update_time
FROM t_vault_record
WHERE record_id = ?;

-- name: FindRecordByStableIdentityForShare :one
SELECT record_id, token, data_type, token_mode, value_fingerprint, ciphertext, encryption_nonce, wrapped_dek, kms_key_ref, create_time, update_time
FROM t_vault_record
WHERE data_type = ? AND value_fingerprint = ?
FOR SHARE;

-- name: InsertRecord :exec
INSERT INTO t_vault_record (record_id, token, data_type, token_mode, value_fingerprint, ciphertext, encryption_nonce, wrapped_dek, kms_key_ref, create_time, update_time)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
