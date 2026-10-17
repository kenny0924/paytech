-- name: InsertAccessEvent :exec
INSERT INTO t_vault_access_event (access_event_id, request_id, trace_id, caller_service, actor_id, action, data_type, purpose, record_id, outcome, result_code, policy_version, reason_code, ticket_ref, create_time)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

-- name: ListManualEvents :many
SELECT access_event_id, request_id, actor_id, action, data_type, outcome, result_code, reason_code, ticket_ref, create_time
FROM t_vault_access_event
WHERE actor_id IS NOT NULL
  AND (sqlc.arg(actor_id) = '' OR actor_id = sqlc.arg(actor_id))
  AND (sqlc.arg(action) = '' OR action = sqlc.arg(action))
  AND (sqlc.arg(data_type) = '' OR data_type = sqlc.arg(data_type))
  AND create_time >= sqlc.arg(from_time) AND create_time < sqlc.arg(to_time)
ORDER BY create_time DESC, access_event_id DESC
LIMIT ? OFFSET ?;

-- name: CountManualEvents :one
SELECT COUNT(*)
FROM t_vault_access_event
WHERE actor_id IS NOT NULL
  AND (sqlc.arg(actor_id) = '' OR actor_id = sqlc.arg(actor_id))
  AND (sqlc.arg(action) = '' OR action = sqlc.arg(action))
  AND (sqlc.arg(data_type) = '' OR data_type = sqlc.arg(data_type))
  AND create_time >= sqlc.arg(from_time) AND create_time < sqlc.arg(to_time);
