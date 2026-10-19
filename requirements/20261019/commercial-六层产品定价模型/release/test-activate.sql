-- Operations 商务控制台（commercial 六层产品定价模型）：mor-test 激活（任务卡 G 阶段 7 第 6 步，只在 mor-test mor_platform 执行，客户端须 utf8mb4）
-- 前置：已执行初始化 DML 末尾「Operations commercial console」块 95（权限 5、授权 5、菜单 / 按钮 8、路由 24、Header 128、Query 5、限流 144）并 COMMIT；
-- commercial-service、operations-console-bff、operations-console-web 三个容器已换成候选镜像并 healthy。
-- 激活 24 条路由、8 个菜单 / 按钮资源、PLATFORM_ADMIN（900000000000000510）的 5 个商务授权，并递增角色版本使会话重新计算授权与导航。
-- 预期影响行数：24 / 8 / 5 / 1；PLATFORM_ADMIN 版本 31 → 32（2026-10-20 阶段 6 核对值）。
-- 审批第二人：PLATFORM_ADMIN 同时持有 MANAGE 与 APPROVE，commercial 要求批准人 ≠ 提交人。验收不在本文件授予第二账号，
-- 而由验收脚本经运营 IAM 接口（POST /iam/v1/roles、/iam/v1/accounts）建合成审批角色与合成人员，验收后停用人员、清空角色权限（与 roles_smoke 一致）。
START TRANSACTION;
SET @commercial_act_time = UTC_TIMESTAMP(6);
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@commercial_act_time
 WHERE route_id BETWEEN 900000000000095401 AND 900000000000095424 AND status='DISABLED';
SELECT ROW_COUNT() AS routes;
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=@commercial_act_time
 WHERE resource_id BETWEEN 900000000000095301 AND 900000000000095308 AND status='DISABLED';
SELECT ROW_COUNT() AS resources;
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=@commercial_act_time
 WHERE role_permission_id BETWEEN 900000000000095201 AND 900000000000095205 AND status='REVOKED';
SELECT ROW_COUNT() AS grants;
UPDATE t_role SET version=version+1, update_time=@commercial_act_time WHERE role_id=900000000000000510;
SELECT ROW_COUNT() AS role;
COMMIT;
