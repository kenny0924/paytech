-- name: MaxPolicyVersion :one
SELECT CAST(COALESCE(MAX(policy_version_no), 0) AS SIGNED) AS max_version
FROM t_vault_policy_change;

-- name: LockLatestPolicyVersion :many
SELECT policy_version_no
FROM t_vault_policy_change
ORDER BY policy_version_no DESC
LIMIT 1
FOR UPDATE;

-- name: InsertPolicyChange :exec
INSERT INTO t_vault_policy_change (policy_change_id, policy_version_no, change_type, target_type, target_key, before_snapshot, after_snapshot, reason, actor_id, request_id, create_time)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

-- name: ListPolicyChanges :many
SELECT policy_change_id, policy_version_no, change_type, target_type, target_key, before_snapshot, after_snapshot, reason, actor_id, request_id, create_time
FROM t_vault_policy_change
WHERE (sqlc.arg(target_type) = '' OR target_type = sqlc.arg(target_type))
  AND (sqlc.arg(target_key) = '' OR target_key = sqlc.arg(target_key))
ORDER BY policy_version_no DESC
LIMIT ? OFFSET ?;

-- name: CountPolicyChanges :one
SELECT COUNT(*)
FROM t_vault_policy_change
WHERE (sqlc.arg(target_type) = '' OR target_type = sqlc.arg(target_type))
  AND (sqlc.arg(target_key) = '' OR target_key = sqlc.arg(target_key));

-- name: FindPolicyChange :one
SELECT policy_change_id, policy_version_no, change_type, target_type, target_key, before_snapshot, after_snapshot, reason, actor_id, request_id, create_time
FROM t_vault_policy_change
WHERE policy_version_no = ?;
