-- Operations 接入支付风控配置与执行发布：本机前置能力开放（阶段 5，只在本机 mor_platform_e2e 执行）
-- 背景：本机 mor_platform_e2e 只开放了第一期规则配置；决策流、统一变量、拒绝原因、规则集历史/引用/删除
--       的注册早已存在但为 DISABLED / REVOKED（各自需求在其他验证库验收后关闭）。支付规则端到端验收依赖这些能力。
-- 不新增任何行，只改既有注册的状态；不开放主体标签。mor-test 已按各自需求开放，不执行本文件。
-- 预期影响行数：路由 28 / 资源 13 / 授权 8 / 角色 1。
START TRANSACTION;
SET @prc_pre_time=UTC_TIMESTAMP(6);
-- 路由：决策流 10、统一变量 9、规则集历史与引用 2、拒绝原因 6、规则集删除 1
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@prc_pre_time
 WHERE status='DISABLED' AND (route_id BETWEEN 900000000000021001 AND 900000000000021010
    OR route_id BETWEEN 900000000000031001 AND 900000000000031009
    OR route_id BETWEEN 900000000000012001 AND 900000000000012008
    OR route_id=900000000000001033);
-- 资源：决策流 3、统一变量 3、规则集历史/引用按钮 2、拒绝原因菜单与 3 个按钮 4、规则集删除按钮 1
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@prc_pre_time
 WHERE status='DISABLED' AND (resource_id BETWEEN 900000000000021301 AND 900000000000021303
    OR resource_id BETWEEN 900000000000031301 AND 900000000000031303
    OR resource_id IN (900000000000012301,900000000000012302,900000000000012303,900000000000012305,900000000000012306,900000000000012307)
    OR resource_id=900000000000000571);
-- PLATFORM_ADMIN 授权：决策流保存/删除、变量管理/删除、拒绝原因新建/修改/状态、规则集删除
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@prc_pre_time
 WHERE status='REVOKED' AND role_permission_id IN (900000000000021201,900000000000021202,900000000000031201,900000000000031202,
    900000000000012201,900000000000012202,900000000000012203,900000000000000587);
UPDATE t_role SET version=version+1, update_time=@prc_pre_time
 WHERE role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM' AND system_role=1 AND status='ACTIVE';
-- 核对影响行数后 COMMIT；否则 ROLLBACK。
-- 撤销：同一组 ID 反向置 DISABLED / REVOKED（WHERE status='ACTIVE'），version+1，并再次递增 PLATFORM_ADMIN 角色 version。
