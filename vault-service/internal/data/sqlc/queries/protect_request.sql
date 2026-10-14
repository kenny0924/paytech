-- name: FindProtectRequestForUpdate :one
SELECT protect_request_id, caller_service, idempotency_key, request_hash, record_id, create_time, update_time
FROM t_vault_protect_request
WHERE caller_service = ? AND idempotency_key = ?
FOR UPDATE;

-- name: InsertProtectRequest :exec
INSERT INTO t_vault_protect_request (protect_request_id, caller_service, idempotency_key, request_hash, record_id, create_time, update_time)
VALUES (?, ?, ?, ?, ?, ?, ?);
