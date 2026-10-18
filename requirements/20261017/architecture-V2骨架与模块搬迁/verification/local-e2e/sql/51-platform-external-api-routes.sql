-- 阶段 5 本机全链路（任务卡 14）：在 mor_platform_v2e2e 登记并启用外部 API 配置管理与渠道沙箱验证的运营路由。
-- 评审后由主会话以 root 执行（在 apply-e2e.sh platform 之后），用 mysql --default-character-set=utf8mb4，不用 --force。
-- 原因：mor_platform_e2e（本库的复制来源）与 platform-support-initial-data.sql 都没有 /api/operations/external-api-admin/** 路由；
--   旧 PayerMax harness 用的是测试装配 Gateway 的动态路由。channel_setup.py 要经真实运营入口保存 PayerMax 配置，必须先有这些路由。
-- 内容（逐字复用已评审文件，不复制其 INSERT）：
--   1. requirements/20260914/platform-support-外部API配置与执行/db/platform-support-service/registration-dml.sql
--      （权限 2、PLATFORM_ADMIN 授权 2 REVOKED、菜单资源 5 DISABLED、路由 900000000000071001—71030 DISABLED 及子项）
--   2. requirements/20261014/platform-support-渠道沙箱验证/db/registration-dml.sql（路由 900000000000071031—71044 DISABLED 及子项）
--   3. 启用：与 requirements/20260914/platform-support-外部API配置与执行/db/platform-support-service/local-e2e/activate.sql 相同的语句，
--      库名守卫改为 mor_platform_v2e2e，路由范围扩到 71044。
-- 两个注册文件都不含 COMMIT；本文件在每个 SOURCE 之后核对计数再 COMMIT。SOURCE 路径相对仓库根：请在本 worktree 根目录执行
--   cd /Users/kenny/Desktop/Workspace/Code-MoR-step14 && mysql -h127.0.0.1 -P13306 -uroot --default-character-set=utf8mb4 \
--     < requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/sql/51-platform-external-api-routes.sql
-- 执行前核对（期望 0 / 0 / 1 / 1）：
--   SELECT COUNT(*) FROM mor_platform_v2e2e.t_gateway_route WHERE route_id BETWEEN 900000000000071001 AND 900000000000071044;
--   SELECT COUNT(*) FROM mor_platform_v2e2e.t_permission WHERE permission_code IN ('EXTERNAL_API_READ','EXTERNAL_API_MANAGE');
--   SELECT COUNT(*) FROM mor_platform_v2e2e.t_gateway_target WHERE target_service_code='operations-console-bff' AND status='ACTIVE';
--   SELECT COUNT(*) FROM mor_platform_v2e2e.t_role WHERE role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM' AND system_role=1 AND status='ACTIVE';
-- 操作者：@plt_ext_operator 取本库最早创建的 ACTIVE 运营真人账号（即 bootstrap 交接后的 E2E 管理员）；为空时注册文件的 INSERT 会因
--   created_by NOT NULL 失败，整个事务回滚。
-- 预期最后一行：routes_44=44 resources_5=5 grants_2=2 role_1=1。启用后运营管理员需重新登录才拿到新权限。
-- 回滚：requirements/20260914/platform-support-外部API配置与执行/db/platform-support-service/local-e2e/deactivate.sql 的语句（库名改为本库）后，
--   按两个注册文件头部的回滚说明删除；或直接 DROP DATABASE mor_platform_v2e2e 重新执行 apply-e2e.sh platform。
USE mor_platform_v2e2e;
SET @plt_ext_operator=(SELECT account_id FROM t_user_account WHERE realm='OPERATIONS' AND account_type='HUMAN' AND status='ACTIVE'
  ORDER BY create_time, account_id LIMIT 1);

SOURCE requirements/20260914/platform-support-外部API配置与执行/db/platform-support-service/registration-dml.sql
SELECT COUNT(*) AS registered_routes_30 FROM t_gateway_route WHERE route_id BETWEEN 900000000000071001 AND 900000000000071030;
COMMIT;

SOURCE requirements/20261014/platform-support-渠道沙箱验证/db/registration-dml.sql
SELECT COUNT(*) AS registered_routes_14 FROM t_gateway_route WHERE route_id BETWEEN 900000000000071031 AND 900000000000071044;
COMMIT;

START TRANSACTION;
SET @plt_ext_activation_time=UTC_TIMESTAMP(6);
SELECT role_id,version INTO @plt_ext_admin,@plt_ext_admin_version FROM t_role
 WHERE role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM' AND system_role=1 AND status='ACTIVE' FOR UPDATE;
UPDATE t_gateway_route SET status='ACTIVE',version=version+1,update_time=@plt_ext_activation_time
 WHERE DATABASE()='mor_platform_v2e2e' AND route_id BETWEEN 900000000000071001 AND 900000000000071044
   AND channel='OPERATIONS' AND status='DISABLED' AND version=1;
SET @plt_ext_routes=ROW_COUNT();
UPDATE t_console_resource SET status='ACTIVE',version=version+1,update_time=@plt_ext_activation_time
 WHERE DATABASE()='mor_platform_v2e2e' AND resource_id BETWEEN 900000000000071301 AND 900000000000071305
   AND channel='OPERATIONS' AND status='DISABLED' AND version=1;
SET @plt_ext_resources=ROW_COUNT();
UPDATE t_role_permission SET status='ACTIVE',version=version+1,update_time=@plt_ext_activation_time
 WHERE DATABASE()='mor_platform_v2e2e' AND role_permission_id IN (900000000000071201,900000000000071202)
   AND role_id=@plt_ext_admin AND status='REVOKED' AND version=1;
SET @plt_ext_grants=ROW_COUNT();
UPDATE t_role SET version=version+1,update_time=@plt_ext_activation_time
 WHERE DATABASE()='mor_platform_v2e2e' AND role_id=@plt_ext_admin AND version=@plt_ext_admin_version;
SET @plt_ext_role=ROW_COUNT();
SELECT @plt_ext_routes routes_44,@plt_ext_resources resources_5,@plt_ext_grants grants_2,@plt_ext_role role_1;
-- 批处理执行：计数不符时请 ROLLBACK 而非 COMMIT（交互执行时在此停下核对）。
COMMIT;
