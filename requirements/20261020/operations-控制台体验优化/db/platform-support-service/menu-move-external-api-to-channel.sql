-- Operations 控制台体验优化：把「外部 API」下的「接入方」「标准 API」移到「渠道」目录，停用空的「外部 API」目录。
-- 只改 mor_platform.t_console_resource 菜单层级与排序；权限（EXTERNAL_API_READ/MANAGE）、路由 /external-api/*、Gateway Route 不变。
-- 前置：已执行 20260914 外部 API registration-dml（71301-71303）与 20261020 渠道控制台 registration-dml（92301）。
-- 每条 UPDATE 带旧值条件，期望各影响 1 行；任一不为 1 行则 ROLLBACK 并核对现状。
-- 回滚：menu-move-external-api-to-channel-rollback.sql。
SET NAMES utf8mb4;
START TRANSACTION;

SET @opsux_menu_time = UTC_TIMESTAMP(6);

UPDATE t_console_resource SET parent_resource_id = 900000000000092301, sort_order = 40, version = version + 1, update_time = @opsux_menu_time
 WHERE resource_id = 900000000000071303 AND resource_code = 'OPERATIONS_EXTERNAL_INTEGRATIONS' AND parent_resource_id = 900000000000071301;

UPDATE t_console_resource SET parent_resource_id = 900000000000092301, sort_order = 50, version = version + 1, update_time = @opsux_menu_time
 WHERE resource_id = 900000000000071302 AND resource_code = 'OPERATIONS_EXTERNAL_STANDARD_APIS' AND parent_resource_id = 900000000000071301;

UPDATE t_console_resource SET status = 'DISABLED', version = version + 1, update_time = @opsux_menu_time
 WHERE resource_id = 900000000000071301 AND resource_code = 'OPERATIONS_EXTERNAL_API' AND status = 'ACTIVE';

SELECT resource_id, resource_code, parent_resource_id, sort_order, status, version FROM t_console_resource
 WHERE resource_id IN (900000000000071301, 900000000000071302, 900000000000071303);

COMMIT;
