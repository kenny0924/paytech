-- name: ListActiveAccessRules :many
SELECT access_rule_id, caller_service, action, data_type_code, purpose_code, status, version, create_time, update_time
FROM t_vault_access_rule
WHERE status = 'ACTIVE';

-- name: ListAccessRules :many
SELECT access_rule_id, caller_service, action, data_type_code, purpose_code, status, version, create_time, update_time
FROM t_vault_access_rule
WHERE (sqlc.arg(data_type_code) = '' OR data_type_code = sqlc.arg(data_type_code))
  AND (sqlc.arg(caller_service) = '' OR caller_service = sqlc.arg(caller_service))
  AND (sqlc.arg(action) = '' OR action = sqlc.arg(action))
  AND (sqlc.arg(status) = '' OR status = sqlc.arg(status))
ORDER BY data_type_code, caller_service, action, purpose_code
LIMIT ? OFFSET ?;

-- name: CountAccessRules :one
SELECT COUNT(*)
FROM t_vault_access_rule
WHERE (sqlc.arg(data_type_code) = '' OR data_type_code = sqlc.arg(data_type_code))
  AND (sqlc.arg(caller_service) = '' OR caller_service = sqlc.arg(caller_service))
  AND (sqlc.arg(action) = '' OR action = sqlc.arg(action))
  AND (sqlc.arg(status) = '' OR status = sqlc.arg(status));

-- name: FindAccessRuleForUpdate :one
SELECT access_rule_id, caller_service, action, data_type_code, purpose_code, status, version, create_time, update_time
FROM t_vault_access_rule
WHERE access_rule_id = ?
FOR UPDATE;

-- name: InsertAccessRule :exec
INSERT INTO t_vault_access_rule (access_rule_id, caller_service, action, data_type_code, purpose_code, status, version, create_time, update_time)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);

-- name: UpdateAccessRuleStatus :execrows
UPDATE t_vault_access_rule
SET status = ?, version = version + 1, update_time = ?
WHERE access_rule_id = ? AND version = ?;
