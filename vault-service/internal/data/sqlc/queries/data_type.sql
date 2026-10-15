-- name: ListDataTypes :many
SELECT data_type_id, data_type_code, token_mode, data_class, description, manual_protect_enabled, manual_reveal_enabled, status, version, create_time, update_time
FROM t_vault_data_type
ORDER BY data_type_code;

-- name: FindDataTypeForUpdate :one
SELECT data_type_id, data_type_code, token_mode, data_class, description, manual_protect_enabled, manual_reveal_enabled, status, version, create_time, update_time
FROM t_vault_data_type
WHERE data_type_code = ?
FOR UPDATE;

-- name: InsertDataType :exec
INSERT INTO t_vault_data_type (data_type_id, data_type_code, token_mode, data_class, description, manual_protect_enabled, manual_reveal_enabled, status, version, create_time, update_time)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

-- name: UpdateDataType :execrows
UPDATE t_vault_data_type
SET data_class = ?, description = ?, manual_protect_enabled = ?, manual_reveal_enabled = ?, status = ?, version = version + 1, update_time = ?
WHERE data_type_code = ? AND version = ?;
