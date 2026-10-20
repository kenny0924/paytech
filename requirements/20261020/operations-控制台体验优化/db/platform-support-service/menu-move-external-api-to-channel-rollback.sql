-- 回滚 menu-move-external-api-to-channel.sql：恢复「外部 API」目录（ACTIVE、排序 40）及其两个子菜单（标准 API 10、接入方 20）。
SET NAMES utf8mb4;
START TRANSACTION;

SET @opsux_menu_rollback_time = UTC_TIMESTAMP(6);

UPDATE t_console_resource SET status = 'ACTIVE', version = version + 1, update_time = @opsux_menu_rollback_time
 WHERE resource_id = 900000000000071301 AND resource_code = 'OPERATIONS_EXTERNAL_API' AND status = 'DISABLED';

UPDATE t_console_resource SET parent_resource_id = 900000000000071301, sort_order = 10, version = version + 1, update_time = @opsux_menu_rollback_time
 WHERE resource_id = 900000000000071302 AND resource_code = 'OPERATIONS_EXTERNAL_STANDARD_APIS' AND parent_resource_id = 900000000000092301;

UPDATE t_console_resource SET parent_resource_id = 900000000000071301, sort_order = 20, version = version + 1, update_time = @opsux_menu_rollback_time
 WHERE resource_id = 900000000000071303 AND resource_code = 'OPERATIONS_EXTERNAL_INTEGRATIONS' AND parent_resource_id = 900000000000092301;

COMMIT;
