-- Local manual acceptance only: desktop-linux / mor-local-mysql-1 / 127.0.0.1:13306 / mor_platform_config_validation.
-- Explicit local acceptance authorization required. Never run against test/production.
-- 8 routes + 3 resources; no role/permission grants. Temporary fixture state, version remains 1.
-- One interactive connection. Confirm 8/3 below before START; confirm ROW_COUNT after each UPDATE.
-- Any mismatch/error => ROLLBACK. COMMIT only after all checks. Do not use --force.
SELECT DATABASE() target_database;
SELECT COUNT(*) expected_8 FROM t_gateway_route WHERE route_id BETWEEN 900000000000061001 AND 900000000000061008 AND status='ACTIVE' AND version=1;
SELECT COUNT(*) expected_3 FROM t_console_resource WHERE resource_id BETWEEN 900000000000061301 AND 900000000000061303 AND status='ACTIVE' AND version=1;
START TRANSACTION;
UPDATE t_gateway_route SET status='DISABLED' WHERE route_id BETWEEN 900000000000061001 AND 900000000000061008 AND status='ACTIVE' AND version=1;
SELECT ROW_COUNT() affected_routes_expected_8;
UPDATE t_console_resource SET status='DISABLED' WHERE resource_id BETWEEN 900000000000061301 AND 900000000000061303 AND status='ACTIVE' AND version=1;
SELECT ROW_COUNT() affected_resources_expected_3;
SELECT status,version,COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000061001 AND 900000000000061008 GROUP BY status,version;
SELECT status,version,COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000061301 AND 900000000000061303 GROUP BY status,version;
-- Caller verifies and COMMITs or ROLLBACKs; no implicit commit in this file.
