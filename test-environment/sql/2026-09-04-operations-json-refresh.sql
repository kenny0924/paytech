-- 仅用于 mor-test 的 Platform Support 数据库，前置为人工目录已存在下面 13 个 Operations Route。
-- 这是人工审阅后显式执行的增量 DML，不得挂入 MySQL init 或由应用启动自动执行。
-- 不创建/覆盖账号、Bootstrap、Key、权限或 Guard；其他 Channel 和 Route 保持原样。
-- Route Header 属于 Route 聚合：Cookie 模式或 CSRF Header 有变化时，版本在同一事务内只推进一次。
-- 重复执行没有新增变化时不会推进版本，UPDATE 和 DELETE 均为幂等操作。

START TRANSACTION;

UPDATE t_gateway_route AS r
SET r.request_cookie_mode = 'DROP',
    r.response_cookie_mode = 'NONE',
    r.version = r.version + 1,
    r.update_time = UTC_TIMESTAMP(6)
WHERE r.channel = 'OPERATIONS'
  AND r.route_code IN (
      'operations-auth-login',
      'operations-auth-refresh',
      'operations-auth-logout',
      'operations-auth-context',
      'operations-auth-password',
      'operations-bootstrap-administrator',
      'operations-navigation',
      'operations-account-list',
      'operations-account-create',
      'operations-account-disable',
      'operations-account-roles',
      'operations-role-list',
      'operations-audit-list'
  )
  AND (
      r.request_cookie_mode <> 'DROP'
      OR r.response_cookie_mode <> 'NONE'
      OR EXISTS (
          SELECT 1
          FROM t_gateway_route_header AS h
          WHERE h.route_id = r.route_id
            AND LOWER(h.header_name) = 'x-csrf-token'
            AND h.header_use IN ('CORS_REQUEST_ALLOWED', 'FORWARD_REQUEST')
      )
  );

DELETE h
FROM t_gateway_route_header AS h
INNER JOIN t_gateway_route AS r ON r.route_id = h.route_id
WHERE r.channel = 'OPERATIONS'
  AND r.route_code IN (
      'operations-auth-login',
      'operations-auth-refresh',
      'operations-auth-logout',
      'operations-auth-context',
      'operations-auth-password',
      'operations-bootstrap-administrator',
      'operations-navigation',
      'operations-account-list',
      'operations-account-create',
      'operations-account-disable',
      'operations-account-roles',
      'operations-role-list',
      'operations-audit-list'
  )
  AND LOWER(h.header_name) = 'x-csrf-token'
  AND h.header_use IN ('CORS_REQUEST_ALLOWED', 'FORWARD_REQUEST');

COMMIT;
