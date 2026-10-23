-- 多语言管理 · 回滚块 135 第 3 部分：恢复 ops-bff 私有路由 CARD_BIN_CSV，删除公开路由 135025 与两条策略（含要求），mor_platform。
-- 来源：../../db/dml-rollback.sql 第 27—42 行（回显改为变量，同 12）。
-- 前提：没有任何文件 / 上传 / 版本 / 检查结果引用这两条策略（guard 判定 @policy_references = 0，否则整体回滚）；有引用时
-- 改用 90b-retire-file-policies.sql（恢复路由、策略置 RETIRED 保留行）。先跑 dry 看计数。
-- 本文件不含 COMMIT：apply_sql.sh <step>-dry 执行后断开即回滚；<step> 在末尾追加 COMMIT。guard 不符时子查询多行报错，mysql 非零退出，
-- 未提交事务随断开回滚。语句正文逐字取自需求目录 db/ 的对应段（只去掉 START TRANSACTION / COMMIT），生成脚本另行核对。
SET NAMES utf8mb4;
SET @target_db = 'mor_platform';
SELECT IF(DATABASE() = @target_db, 'database ok', (SELECT 'wrong database' UNION ALL SELECT 'abort')) AS db_guard;
START TRANSACTION;
SET @policy_references = (SELECT COUNT(*) FROM t_file_object WHERE scan_policy_id IN (900000000000135021, 900000000000135023)) + (SELECT COUNT(*) FROM t_file_upload WHERE scan_policy_id IN (900000000000135021, 900000000000135023)) + (SELECT COUNT(*) FROM t_file_version WHERE scan_policy_id IN (900000000000135021, 900000000000135023)) + (SELECT COUNT(*) FROM t_file_scan_result WHERE scan_policy_id IN (900000000000135021, 900000000000135023));
SELECT @policy_references AS file_rows_referencing_policies_must_be_0;
-- 第 3 部分：文件检查策略与写入路由（策略 2、要求 2、路由 1，并恢复 operations-console-bff × PRIVATE 的旧策略）
-- 前置：先回退 Platform 或确认不再需要发布语言包；已上传的语言包文件与导入文件保留（文件记录冻结了上传时的策略修订，
-- 删除策略修订前须确认 t_file_upload / t_file_object / t_file_version 中没有引用 900000000000135021 或 900000000000135023，
-- 有引用时只回退路由，策略改为 RETIRED：UPDATE t_file_scan_policy SET status='RETIRED', active_slot=NULL, version=version+1,
-- update_time=UTC_TIMESTAMP(6) WHERE policy_id IN (900000000000135021, 900000000000135023);）。
-- 恢复 operations-console-bff × PRIVATE（mor-test route_id 239087906591948800）的旧策略 CARD_BIN_CSV；只改策略引用。
UPDATE t_file_storage_route
   SET scan_policy_code = 'CARD_BIN_CSV', version = version + 1, update_time = UTC_TIMESTAMP(6)
 WHERE route_id = 239087906591948800 AND source_system = 'operations-console-bff' AND access_level = 'PRIVATE'
   AND scan_policy_code = 'OPERATIONS_BFF_IMPORT';
SET @ops_bff_route_restored = ROW_COUNT();
SELECT @ops_bff_route_restored AS operations_bff_route_restored; -- 应为 1
DELETE FROM t_file_storage_route WHERE route_id = 900000000000135025;
DELETE FROM t_file_scan_requirement WHERE requirement_id IN (900000000000135022, 900000000000135024);
DELETE FROM t_file_scan_policy WHERE policy_id IN (900000000000135021, 900000000000135023);
SELECT IF(@policy_references = 0
  AND @ops_bff_route_restored = 1
  AND (SELECT COUNT(*) FROM t_file_storage_route WHERE route_id = 239087906591948800 AND scan_policy_code = 'CARD_BIN_CSV') = 1
  AND (SELECT COUNT(*) FROM t_file_storage_route WHERE route_id = 900000000000135025) = 0
  AND (SELECT COUNT(*) FROM t_file_scan_policy WHERE policy_id IN (900000000000135021, 900000000000135023)) = 0,
  'rollback part 3 ok', (SELECT 'mismatch' UNION ALL SELECT 'abort')) AS guard;
