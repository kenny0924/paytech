-- payout 收款账户与余额解释（S1）· 注册块 147 · 激活（payout-service、client-console-bff / web、operations-console-bff / web 新版本之后执行）。
-- 块号 147：任务卡原写 146，但 146 已被 tax（requirements/20261029/tax-结账算税与事件登记）占用、145 = REQ-04、144 = 联合查询，本稿取 147；阶段 6 只读核对现网最大块号后替换（ID 区间 900000000000147001—900000000000147999）。
-- 平台管理员授权、Client 系统角色授权 REVOKED → ACTIVE；补齐休眠写入之后才开通的工作区（旧版本投影时尚无本矩阵）的授权（直接 ACTIVE）；
-- 菜单 / 按钮与 19 条路由 DISABLED → ACTIVE；管理员角色 version + 1（让会话权限缓存刷新，同块 143）。
-- 重复执行：状态更新为空操作，补齐语句按 NOT EXISTS 跳过，角色 version 再 + 1（无副作用）。执行后 Gateway 快照自动刷新（routeCount +19）。
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET time_zone = '+00:00';

-- 前置：本块已写入（8 个权限、19 条路由齐全），否则标量子查询返回两行 → ERROR 1242，事务未开始、不做任何改动。
SET @b147_block_guard = (SELECT 1 UNION ALL SELECT 1 FROM DUAL
  WHERE (SELECT COUNT(*) FROM t_permission WHERE permission_id BETWEEN 900000000000147101 AND 900000000000147108) <> 8
     OR (SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000147401 AND 900000000000147419) <> 19);
SET @b147_activate_time = UTC_TIMESTAMP(6);

START TRANSACTION;
UPDATE t_role_permission SET status = 'ACTIVE', version = version + 1, update_time = @b147_activate_time
WHERE role_permission_id BETWEEN 900000000000147201 AND 900000000000147205 AND status = 'REVOKED';
UPDATE t_role_permission rp JOIN t_role r ON r.role_id = rp.role_id
SET rp.status = 'ACTIVE', rp.version = rp.version + 1, rp.update_time = @b147_activate_time
WHERE rp.permission_id IN (900000000000147101, 900000000000147102, 900000000000147103) AND rp.status = 'REVOKED' AND r.tenant_scope = 'TENANT' AND r.system_role = 1;
SET @b147_activate_rp_base = (SELECT COALESCE(MAX(role_permission_id), 9099999999999999999)
  FROM t_role_permission WHERE role_permission_id >= 9100000000000000000);

INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time)
SELECT @b147_activate_rp_base + ROW_NUMBER() OVER (ORDER BY r.role_id, m.permission_id), r.role_id, m.permission_id,
  'ACTIVE', 1, @b147_activate_time, @b147_activate_time
FROM t_role r
JOIN t_access_tenant t ON t.tenant_id = r.tenant_id
JOIN (
  SELECT 'TENANT_OWNER' role_code, 900000000000147101 permission_id
  UNION ALL SELECT 'TENANT_OWNER', 900000000000147102
  UNION ALL SELECT 'TENANT_OWNER', 900000000000147103
  UNION ALL SELECT 'TENANT_ADMIN', 900000000000147101
  UNION ALL SELECT 'TENANT_ADMIN', 900000000000147102
  UNION ALL SELECT 'TENANT_ADMIN', 900000000000147103
  UNION ALL SELECT 'TENANT_DEVELOPER', 900000000000147101
  UNION ALL SELECT 'TENANT_DEVELOPER', 900000000000147103
  UNION ALL SELECT 'TENANT_VIEWER', 900000000000147101
  UNION ALL SELECT 'TENANT_VIEWER', 900000000000147103
) m ON m.role_code = r.role_code
JOIN t_permission p ON p.permission_id = m.permission_id AND p.status = 'ACTIVE'
WHERE r.tenant_scope = 'TENANT' AND r.system_role = 1 AND r.scope_key = CAST(t.tenant_id AS CHAR)
  AND t.tenant_type = 'CLIENT' AND t.owner_status <> 'PENDING'
  AND NOT EXISTS (SELECT 1 FROM t_role_permission x WHERE x.role_id = r.role_id AND x.permission_id = m.permission_id);

UPDATE t_console_resource SET status = 'ACTIVE', version = version + 1, update_time = @b147_activate_time
WHERE resource_id BETWEEN 900000000000147301 AND 900000000000147307 AND status = 'DISABLED';
UPDATE t_gateway_route SET status = 'ACTIVE', version = version + 1, update_time = @b147_activate_time
WHERE route_id BETWEEN 900000000000147401 AND 900000000000147419 AND status = 'DISABLED';
UPDATE t_role SET version = version + 1, update_time = @b147_activate_time WHERE role_id = 900000000000000510;
COMMIT;

SELECT role_permission_id, status, version FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000147201 AND 900000000000147205 ORDER BY role_permission_id;
SELECT status, COUNT(*) AS `client_grants` FROM t_role_permission WHERE permission_id IN (900000000000147101, 900000000000147102, 900000000000147103) GROUP BY status;
SELECT resource_id, resource_code, status, version FROM t_console_resource WHERE resource_id BETWEEN 900000000000147301 AND 900000000000147307 ORDER BY resource_id;
SELECT route_id, route_code, status, version FROM t_gateway_route WHERE route_id BETWEEN 900000000000147401 AND 900000000000147419 ORDER BY route_id;
