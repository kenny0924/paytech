-- 回滚 30-ops-platform-tax-local.sql（只用于 mor_platform_v2e2e），先子后父。
SET NAMES utf8mb4;
SET @tax_id = 900000000001449000;
START TRANSACTION;
DELETE FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN @tax_id + 701 AND @tax_id + 712;
DELETE FROM t_gateway_route_header WHERE route_header_id BETWEEN @tax_id + 501 AND @tax_id + 508;
DELETE FROM t_gateway_route WHERE route_id BETWEEN @tax_id + 401 AND @tax_id + 402;
DELETE FROM t_console_resource WHERE resource_id = @tax_id + 301;
DELETE FROM t_role_permission WHERE role_permission_id = @tax_id + 201;
DELETE FROM t_permission WHERE permission_id = @tax_id + 101;
UPDATE t_role SET version = version + 1, update_time = UTC_TIMESTAMP(6) WHERE role_id = 900000000000000510;
COMMIT;
