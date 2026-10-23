-- 未执行草案：platform-support：多语言管理 dml.sql 的回滚，目标库 mor_platform。只写不执行，执行另行授权。
-- 第 1 部分删除注册块 135 的全部行（子表先删）；第 2 部分删除多语言初始数据；第 3 部分恢复文件检查策略与写入路由。
-- 三部分互相独立，按需单独执行；同时回退时先执行第 3 部分。
-- 第 2 部分前置：先回退 Platform 或确认没有需要保留的文案与发布；若 t_i18n_namespace / t_i18n_key / t_i18n_translation /
-- t_i18n_release 中已有引用这些应用或语言的数据，先人工处置（或按 changes/2026-10-23-localization.sql 的回滚整体删表），
-- 否则删除应用会留下孤儿行。
SET NAMES utf8mb4;

-- 第 1 部分：注册块 135（331 行）
START TRANSACTION;
DELETE FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000135801 AND 900000000000135999;
DELETE FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN 900000000000135701 AND 900000000000135799;
DELETE FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000135501 AND 900000000000135699;
DELETE FROM t_gateway_route WHERE route_id BETWEEN 900000000000135401 AND 900000000000135499;
DELETE FROM t_console_resource WHERE resource_id BETWEEN 900000000000135301 AND 900000000000135399;
DELETE FROM t_role_permission WHERE role_permission_id BETWEEN 900000000000135201 AND 900000000000135299;
DELETE FROM t_permission WHERE permission_id BETWEEN 900000000000135101 AND 900000000000135199;
COMMIT;
-- 执行后重新发布 Gateway Snapshot。

-- 第 2 部分：多语言初始数据（语言 2、应用 4）
START TRANSACTION;
DELETE FROM t_i18n_application WHERE application_id BETWEEN 900000000000135011 AND 900000000000135014;
DELETE FROM t_i18n_locale WHERE locale_id BETWEEN 900000000000135001 AND 900000000000135002;
COMMIT;

-- 第 3 部分：文件检查策略与写入路由（策略 2、要求 2、路由 1，并恢复 operations-console-bff × PRIVATE 的旧策略）
-- 前置：先回退 Platform 或确认不再需要发布语言包；已上传的语言包文件与导入文件保留（文件记录冻结了上传时的策略修订，
-- 删除策略修订前须确认 t_file_upload / t_file_object / t_file_version 中没有引用 900000000000135021 或 900000000000135023，
-- 有引用时只回退路由，策略改为 RETIRED：UPDATE t_file_scan_policy SET status='RETIRED', active_slot=NULL, version=version+1,
-- update_time=UTC_TIMESTAMP(6) WHERE policy_id IN (900000000000135021, 900000000000135023);）。
START TRANSACTION;
-- 恢复 operations-console-bff × PRIVATE（mor-test route_id 239087906591948800）的旧策略 CARD_BIN_CSV；只改策略引用。
UPDATE t_file_storage_route
   SET scan_policy_code = 'CARD_BIN_CSV', version = version + 1, update_time = UTC_TIMESTAMP(6)
 WHERE route_id = 239087906591948800 AND source_system = 'operations-console-bff' AND access_level = 'PRIVATE'
   AND scan_policy_code = 'OPERATIONS_BFF_IMPORT';
SELECT ROW_COUNT() AS operations_bff_route_restored; -- 应为 1
DELETE FROM t_file_storage_route WHERE route_id = 900000000000135025;
DELETE FROM t_file_scan_requirement WHERE requirement_id IN (900000000000135022, 900000000000135024);
DELETE FROM t_file_scan_policy WHERE policy_id IN (900000000000135021, 900000000000135023);
COMMIT;
-- 执行后等待 Platform 存储快照刷新。
