-- Manual repair for the seven mor-test catalog labels imported as latin1.
-- Only the exact seeded ID/code and known UTF-8-as-latin1 value may change.
-- Correct labels and operator-customized labels are preserved on repeated execution.
-- Role grants, permissions, accounts, signing keys and Bootstrap are unchanged.
SET NAMES utf8mb4;

START TRANSACTION;

UPDATE t_role AS r
JOIN (
    SELECT 900000000000000510 AS id, 'PLATFORM_ADMIN' AS code, '平台管理员' AS label
    UNION ALL
    SELECT 900000000000000511, 'OPERATIONS_VIEWER', '运营只读人员'
) AS expected ON r.role_id = expected.id AND r.role_code = expected.code
SET r.role_name = expected.label,
    r.version = r.version + 1,
    r.update_time = UTC_TIMESTAMP(6)
WHERE r.tenant_scope = 'PLATFORM' AND r.scope_key = 'PLATFORM' AND r.system_role = 1
  AND HEX(r.role_name) = HEX(CONVERT(CONVERT(CAST(expected.label AS BINARY) USING latin1) USING utf8mb4));

SELECT ROW_COUNT() AS repaired_roles;

UPDATE t_console_resource AS r
JOIN (
    SELECT 900000000000000550 AS id, 'OPERATIONS_ACCOUNTS' AS code, '内部人员' AS label
    UNION ALL
    SELECT 900000000000000551, 'OPERATIONS_AUDIT', '审计事件'
    UNION ALL
    SELECT 900000000000000552, 'OPERATIONS_ACCOUNT_CREATE', '创建人员'
    UNION ALL
    SELECT 900000000000000553, 'OPERATIONS_ACCOUNT_DISABLE', '禁用人员'
    UNION ALL
    SELECT 900000000000000554, 'OPERATIONS_ACCOUNT_ASSIGN_ROLES', '分配角色'
) AS expected ON r.resource_id = expected.id AND r.resource_code = expected.code
SET r.display_name = expected.label,
    r.version = r.version + 1,
    r.update_time = UTC_TIMESTAMP(6)
WHERE r.channel = 'OPERATIONS'
  AND HEX(r.display_name) = HEX(CONVERT(CONVERT(CAST(expected.label AS BINARY) USING latin1) USING utf8mb4));

SELECT ROW_COUNT() AS repaired_resources;

COMMIT;
