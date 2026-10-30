-- payout 收款账户与余额解释（S1）· 注册块 147 · 停用（回滚第一步，激活的逆操作）。重复执行为空操作。
-- 块号 147：任务卡原写 146，但 146 已被 tax（requirements/20261029/tax-结账算税与事件登记）占用、145 = REQ-04、144 = 联合查询，本稿取 147；阶段 6 只读核对现网最大块号后替换（ID 区间 900000000000147001—900000000000147999）。
-- 先回退 client-console-web / operations-console-web（入口）再执行；执行后 Gateway 快照自动刷新（routeCount -19）。
-- 通知模板不在此停用：payout 补偿任务可能仍在发送，停用模板会使其受理失败；需要停发时由部署移除 notification.http.callers.payout-service。
-- 注意：Platform 新版本仍按 ClientTeamPermissions 矩阵给此后首次 ACTIVE 的工作区授予 ACTIVE；彻底撤回须同时回退 Platform 版本。
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET time_zone = '+00:00';

-- 前置：本块已写入（8 个权限、19 条路由齐全），否则标量子查询返回两行 → ERROR 1242，事务未开始、不做任何改动。
SET @b147_block_guard = (SELECT 1 UNION ALL SELECT 1 FROM DUAL
  WHERE (SELECT COUNT(*) FROM t_permission WHERE permission_id BETWEEN 900000000000147101 AND 900000000000147108) <> 8
     OR (SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000147401 AND 900000000000147419) <> 19);
SET @b147_deactivate_time = UTC_TIMESTAMP(6);

START TRANSACTION;
UPDATE t_gateway_route SET status = 'DISABLED', version = version + 1, update_time = @b147_deactivate_time
WHERE route_id BETWEEN 900000000000147401 AND 900000000000147419 AND status = 'ACTIVE';
UPDATE t_console_resource SET status = 'DISABLED', version = version + 1, update_time = @b147_deactivate_time
WHERE resource_id BETWEEN 900000000000147301 AND 900000000000147307 AND status = 'ACTIVE';
UPDATE t_role_permission SET status = 'REVOKED', version = version + 1, update_time = @b147_deactivate_time
WHERE role_permission_id BETWEEN 900000000000147201 AND 900000000000147205 AND status = 'ACTIVE';
UPDATE t_role_permission rp JOIN t_role r ON r.role_id = rp.role_id
SET rp.status = 'REVOKED', rp.version = rp.version + 1, rp.update_time = @b147_deactivate_time
WHERE rp.permission_id IN (900000000000147101, 900000000000147102, 900000000000147103) AND rp.status = 'ACTIVE' AND r.tenant_scope = 'TENANT' AND r.system_role = 1;
UPDATE t_role SET version = version + 1, update_time = @b147_deactivate_time WHERE role_id = 900000000000000510;
COMMIT;
