-- name: InsertAccessEvent :exec
INSERT INTO t_vault_access_event (access_event_id, request_id, trace_id, caller_service, action, data_type, purpose, record_id, outcome, result_code, policy_version, create_time)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
