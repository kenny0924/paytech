-- 多语言管理 · 回滚块 135 第 3 部分（已有文件引用策略时）：恢复 ops-bff 私有路由、删除公开路由 135025、两条策略置 RETIRED（保留行），mor_platform。
-- 来源：../../db/dml-rollback.sql 第 3 部分文件头的 RETIRED 写法。
-- 本文件不含 COMMIT：apply_sql.sh <step>-dry 执行后断开即回滚；<step> 在末尾追加 COMMIT。guard 不符时子查询多行报错，mysql 非零退出，
-- 未提交事务随断开回滚。语句正文逐字取自需求目录 db/ 的对应段（只去掉 START TRANSACTION / COMMIT），生成脚本另行核对。
SET NAMES utf8mb4;
SET @target_db = 'mor_platform';
SELECT IF(DATABASE() = @target_db, 'database ok', (SELECT 'wrong database' UNION ALL SELECT 'abort')) AS db_guard;
START TRANSACTION;
UPDATE t_file_storage_route
   SET scan_policy_code = 'CARD_BIN_CSV', version = version + 1, update_time = UTC_TIMESTAMP(6)
 WHERE route_id = 239087906591948800 AND source_system = 'operations-console-bff' AND access_level = 'PRIVATE'
   AND scan_policy_code = 'OPERATIONS_BFF_IMPORT';
SET @ops_bff_route_restored = ROW_COUNT();
DELETE FROM t_file_storage_route WHERE route_id = 900000000000135025;
SET @public_route_deleted = ROW_COUNT();
UPDATE t_file_scan_policy SET status='RETIRED', active_slot=NULL, version=version+1, update_time=UTC_TIMESTAMP(6)
 WHERE policy_id IN (900000000000135021, 900000000000135023) AND status = 'ACTIVE';
SET @policies_retired = ROW_COUNT();
SELECT @ops_bff_route_restored AS route_restored_1, @public_route_deleted AS public_route_deleted_1, @policies_retired AS policies_retired_2;
SELECT IF(@ops_bff_route_restored = 1
  AND @public_route_deleted = 1
  AND @policies_retired = 2,
  'retire 1/1/2 ok', (SELECT 'mismatch' UNION ALL SELECT 'abort')) AS guard;
