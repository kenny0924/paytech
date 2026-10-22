-- 回滚（数据，mor_platform）：只在 Platform / Card / ops-bff / ops-web / Gateway / channel-connector 都已按回滚文件换回、且确需移除注册行时执行。
-- 一般不执行：休眠注册行与种子对旧镜像无影响（旧 Platform 不读 t_country_region，休眠路由不进快照）。
-- 语句取自各来源文件头的 Rollback / 回退说明，按“子表先删”。不含 COMMIT：apply_sql.sh <备份目录> rollback-platform-data-dry / rollback-platform-data。
-- 前置：在运营「存储管理」先删除 operations-console-bff → CARD_BIN_CSV 写入路由；导入时产生的 t_file_upload / t_file_object 记录保留（文件服务不提供删除）。
START TRANSACTION;
-- 块 133（23 文件头 Rollback）
DELETE FROM t_gateway_guard_rule  WHERE DATABASE()='mor_platform' AND guard_rule_id  BETWEEN 900000000000133001 AND 900000000000133999;
DELETE FROM t_gateway_route_header WHERE DATABASE()='mor_platform' AND route_header_id BETWEEN 900000000000133001 AND 900000000000133999;
DELETE FROM t_gateway_route       WHERE DATABASE()='mor_platform' AND route_id       BETWEEN 900000000000133001 AND 900000000000133999;
DELETE FROM t_gateway_target      WHERE DATABASE()='mor_platform' AND target_id      BETWEEN 900000000000133001 AND 900000000000133999;
-- 块 132（22 文件头 Rollback：900000000000132001—900000000000132899）
DELETE FROM t_gateway_guard_rule        WHERE DATABASE()='mor_platform' AND guard_rule_id        BETWEEN 900000000000132001 AND 900000000000132899;
DELETE FROM t_gateway_route_query_param WHERE DATABASE()='mor_platform' AND route_query_param_id BETWEEN 900000000000132001 AND 900000000000132899;
DELETE FROM t_gateway_route_header      WHERE DATABASE()='mor_platform' AND route_header_id      BETWEEN 900000000000132001 AND 900000000000132899;
DELETE FROM t_gateway_route             WHERE DATABASE()='mor_platform' AND route_id             BETWEEN 900000000000132001 AND 900000000000132899;
DELETE FROM t_console_resource          WHERE DATABASE()='mor_platform' AND resource_id          BETWEEN 900000000000132001 AND 900000000000132899;
DELETE FROM t_role_permission           WHERE DATABASE()='mor_platform' AND role_permission_id   BETWEEN 900000000000132001 AND 900000000000132899;
DELETE FROM t_permission                WHERE DATABASE()='mor_platform' AND permission_id        BETWEEN 900000000000132001 AND 900000000000132899;
-- 扫描策略（20 文件头回退）
DELETE FROM t_file_scan_requirement WHERE DATABASE()='mor_platform' AND requirement_id = 900000000000132902;
DELETE FROM t_file_scan_policy      WHERE DATABASE()='mor_platform' AND policy_id = 900000000000132901;
-- 国家区域种子（15 第 1 段回退）
DELETE FROM t_country_region WHERE DATABASE()='mor_platform' AND id BETWEEN 2001 AND 2249;
SELECT (SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000132001 AND 900000000000133999) AS routes_left_0,
       (SELECT COUNT(*) FROM t_file_storage_route WHERE scan_policy_code = 'CARD_BIN_CSV') AS policy_refs_left_0,
       (SELECT COUNT(*) FROM t_country_region) AS country_left_0;
SELECT IF((SELECT COUNT(*) FROM t_file_storage_route WHERE scan_policy_code = 'CARD_BIN_CSV') = 0, 'refs ok',
          (SELECT 'storage route still references CARD_BIN_CSV' UNION ALL SELECT 'abort')) AS guard;
