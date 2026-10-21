-- commercial 开通与定价模型调整（一期）：mor-test 激活（阶段 7，只在 mor-test mor_platform 执行，platform_app，客户端须 utf8mb4）
-- 前置：已执行 registration-dml.sql 的块 124 并 COMMIT；已执行首切片块 95 / 第二切片块 93 并已激活（20261019、20261020
-- commercial 各需求 release/test-activate.sql）；commercial-service、operations-console-bff、operations-console-web 已换成候选镜像并 healthy
-- （旧 BFF 没有这些端点，先开路由会得到 404）。
-- 激活 17 条路由（目录维护 5、维度清单 1、标准价目 9、合同版本从模板补齐 1、价格协议签署登记 1）、5 个资源（菜单 标准价目，按钮 目录维护、
-- 标准价目维护、标准价目复核、登记签署）与 PLATFORM_ADMIN 的 3 个新授权（COMMERCIAL_CATALOG_MANAGE / COMMERCIAL_TEMPLATE_MANAGE /
-- COMMERCIAL_TEMPLATE_APPROVE）；COMMERCIAL_CONTRACT_READ / PRICING_READ / PRICING_MANAGE 授权属于首切片，已存在不重复。
-- 递增一次 PLATFORM_ADMIN 角色版本使会话重新计算菜单与按钮。
-- 预期影响行数：17 / 5 / 3 / 1；第 0 段期望 DISABLED 路由 17、DISABLED 资源 5、REVOKED 授权 3；admin_version 以发布时实测为准（执行后 +1）。
-- 试跑：先单独执行第 0 段核对；计数不符时停止，不进入事务。

-- 0. 执行前核对
SELECT
 (SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000124401 AND 900000000000124417 AND status='DISABLED') AS routes_exp17,
 (SELECT COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000124301 AND 900000000000124305 AND status='DISABLED') AS resources_exp5,
 (SELECT COUNT(*) FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000124201 AND 900000000000124203 AND status='REVOKED') AS grants_exp3,
 (SELECT COUNT(*) FROM t_permission WHERE permission_id BETWEEN 900000000000124101 AND 900000000000124103 AND status='ACTIVE') AS permissions_exp3,
 (SELECT version FROM t_role WHERE role_id=900000000000000510) AS admin_version;

START TRANSACTION;
SET @commercial_template_act_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@commercial_template_act_time
 WHERE route_id BETWEEN 900000000000124401 AND 900000000000124417 AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@commercial_template_act_time
 WHERE resource_id BETWEEN 900000000000124301 AND 900000000000124305 AND status='DISABLED';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@commercial_template_act_time
 WHERE role_permission_id BETWEEN 900000000000124201 AND 900000000000124203 AND status='REVOKED';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@commercial_template_act_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
-- 回滚：test-deactivate.sql。
