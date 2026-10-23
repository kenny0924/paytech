-- 多语言管理 · 回滚块 135 第 1 部分：删除注册 331 行（子表先删），mor_platform。
-- 来源：../../db/dml-rollback.sql 第 9—19 行。通常只停用（21-deactivate-block135.sql）即可；彻底移除时执行本文件，之后等 Gateway Snapshot 刷新。
-- 本文件不含 COMMIT：apply_sql.sh <step>-dry 执行后断开即回滚；<step> 在末尾追加 COMMIT。guard 不符时子查询多行报错，mysql 非零退出，
-- 未提交事务随断开回滚。语句正文逐字取自需求目录 db/ 的对应段（只去掉 START TRANSACTION / COMMIT），生成脚本另行核对。
SET NAMES utf8mb4;
SET @target_db = 'mor_platform';
SELECT IF(DATABASE() = @target_db, 'database ok', (SELECT 'wrong database' UNION ALL SELECT 'abort')) AS db_guard;
START TRANSACTION;
-- 第 1 部分：注册块 135（331 行）
DELETE FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000135801 AND 900000000000135999;
DELETE FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000135701 AND 900000000000135799;
DELETE FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000135501 AND 900000000000135699;
DELETE FROM t_gateway_route WHERE route_id BETWEEN 900000000000135401 AND 900000000000135499;
DELETE FROM t_console_resource WHERE resource_id BETWEEN 900000000000135301 AND 900000000000135399;
DELETE FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000135201 AND 900000000000135299;
DELETE FROM t_permission WHERE permission_id BETWEEN 900000000000135101 AND 900000000000135199;
-- 执行后重新发布 Gateway Snapshot。
SELECT IF((SELECT COUNT(*) FROM t_permission WHERE permission_id BETWEEN 900000000000135101 AND 900000000000135199) = 0
  AND (SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000135401 AND 900000000000135499) = 0
  AND (SELECT COUNT(*) FROM t_console_resource WHERE resource_id BETWEEN 900000000000135301 AND 900000000000135399) = 0,
  'rollback registration ok', (SELECT 'mismatch' UNION ALL SELECT 'abort')) AS guard;
