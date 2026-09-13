-- 仅用于 127.0.0.1:13306/mor_platform_rco16_validation；须单独授权。
-- 调用者必须在同一连接核对四项 ROW_COUNT 后决定 COMMIT 或 ROLLBACK。
START TRANSACTION;
SET time_zone = '+00:00';
UPDATE t_gateway_route SET status='DISABLED', version=version+1, update_time=UTC_TIMESTAMP(6) WHERE route_id IN (900000000000021001,900000000000021002,900000000000021003,900000000000021004,900000000000021005,900000000000021006,900000000000021007,900000000000021008,900000000000021009,900000000000021010,900000000000012003,900000000000012004) AND status='ACTIVE' AND version=2;
SELECT 't_gateway_route',ROW_COUNT() AS changed_rows; -- 必须为 12
UPDATE t_console_resource SET status='DISABLED', version=version+1, update_time=UTC_TIMESTAMP(6) WHERE resource_id IN (900000000000021301,900000000000021302,900000000000021303) AND status='ACTIVE' AND version=2;
SELECT 't_console_resource',ROW_COUNT() AS changed_rows; -- 必须为 3
UPDATE t_role_permission SET status='REVOKED', version=version+1, update_time=UTC_TIMESTAMP(6) WHERE role_permission_id IN (900000000000021201,900000000000021202) AND status='ACTIVE' AND version=2;
SELECT 't_role_permission',ROW_COUNT() AS changed_rows; -- 必须为 2
UPDATE t_role SET version=version+1, update_time=UTC_TIMESTAMP(6) WHERE role_id=900000000000000510 AND version=5;
SELECT 't_role',ROW_COUNT() AS changed_rows; -- 必须为 1
-- 不在本文件自动 COMMIT。
