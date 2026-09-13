-- 仅用于 127.0.0.1:13306/mor_platform_rco16_validation；用户已于本次回复“都同意”确认重验及补充只读路由。
-- 调用者必须在同一连接核对五项 ROW_COUNT 后决定 COMMIT 或 ROLLBACK。
START TRANSACTION;
SET time_zone = '+00:00';
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=UTC_TIMESTAMP(6) WHERE route_id IN (900000000000021001,900000000000021002,900000000000021003,900000000000021004,900000000000021005,900000000000021006,900000000000021007,900000000000021008,900000000000021009,900000000000021010,900000000000012003,900000000000012004) AND status='DISABLED' AND version=3;
SELECT 't_gateway_route',ROW_COUNT() AS changed_rows; -- 必须为 12
UPDATE t_gateway_route SET status='ACTIVE',version=version+1,update_time=UTC_TIMESTAMP(6) WHERE route_id=900000000000012001 AND route_code='operations-risk-rule-set-versions' AND status='DISABLED' AND version=1;
SELECT 'rule_history_route',ROW_COUNT() AS changed_rows; -- 必须为 1；全部五项核对后才在同一连接 COMMIT，否则 ROLLBACK。
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=UTC_TIMESTAMP(6) WHERE resource_id IN (900000000000021301,900000000000021302,900000000000021303) AND status='DISABLED' AND version=3;
SELECT 't_console_resource',ROW_COUNT() AS changed_rows; -- 必须为 3
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=UTC_TIMESTAMP(6) WHERE role_permission_id IN (900000000000021201,900000000000021202) AND status='REVOKED' AND version=3;
SELECT 't_role_permission',ROW_COUNT() AS changed_rows; -- 必须为 2
UPDATE t_role SET version=version+1, update_time=UTC_TIMESTAMP(6) WHERE role_id=900000000000000510 AND version=6;
SELECT 't_role',ROW_COUNT() AS changed_rows; -- 必须为 1
-- 不在本文件自动 COMMIT。
