-- 第 3 部分（任务卡 F 集成，2026-10-23）：多语言的文件检查策略与写入路由。固定 ID 取块 135 的 1350xx 段（900000000000135021—025）。
-- 1) 检查策略 I18N_BUNDLE_JSON 修订 1（ACTIVE）：只允许 application/json，单文件 1 MiB（1048576），一项 PLATFORM_FORMAT / FORMAT /
--    最低版本 1 / 只接受 FORMAT_VALID 的要求（与检查策略管理新建修订时自动写入的要求相同）。FORMAT 检查对 JSON 复核：
--    UTF-8、无 BOM、不含 NUL、全文恰好是一个 JSON 对象。
-- 2) 检查策略 OPERATIONS_BFF_IMPORT 修订 1（ACTIVE）：允许 text/csv 与 application/json，上限取两者较大值，即 CARD_BIN_CSV 的
--    52428800（50 MiB；多语言导入文件由 Platform 业务另限 2 MiB）；要求同上。
-- 3) 写入路由 platform-support × PUBLIC → 公开桶，检查策略 I18N_BUNDLE_JSON，授权有效期 300 秒（语言包在 Platform 进程内签发后
--    立即写入）。来源系统取文件服务为 Platform 进程内上传记录的来源（FileSourceCodes.PLATFORM_SUPPORT = 'platform-support'，
--    与工作流附件相同；语言包上传由 FileServiceReleaseBundleStore 以该来源调用文件入口）。
--    桶按 (实例代码, 桶名) 查找：mor-test 为文件公开桶发布时经「存储管理」登记并启用的 MINIO / mor-platform-public
--    （对外地址 https://minio.mor-test.localhost:29400/mor-platform-public，匿名 GetObject 已允许）；
--    其他环境执行前把下方两个变量改为该环境已登记且 ACTIVE 的 PUBLIC 桶。桶不存在时 bucket_id 为 NULL，INSERT 因 NOT NULL 失败，
--    整个事务不提交。
-- 4) 写入路由 operations-console-bff × PRIVATE（mor-test route_id 239087906591948800，块 132 发布时在「存储管理」页登记）：
--    只改策略引用，scan_policy_code 由旧值 CARD_BIN_CSV 改为 OPERATIONS_BFF_IMPORT，版本加一；route_id、桶与
--    max_upload_seconds=900 不变。WHERE 带 route_id 与旧值，必须恰好影响 1 行（执行后核对 ROW_COUNT()=1，否则 ROLLBACK）。
--    卡 BIN 库 CSV 上传继续可用（同一 FORMAT 检查：合法 UTF-8、无 NUL，上限 52428800）；多语言 key / 翻译导入的 JSON 由此能通过
--    Platform 检查。owner code-mor-6b 已确认；发布后由主会话安排卡 BIN 导入验收。其他环境的 route_id 不同，按前置 SQL 查出后替换。
-- 前置（在 live mor_platform 只读核对）：
--   SELECT COUNT(*) FROM t_file_scan_policy WHERE policy_code IN ('I18N_BUNDLE_JSON','OPERATIONS_BFF_IMPORT');            -- 0
--   SELECT COUNT(*) FROM t_file_scan_policy WHERE policy_id BETWEEN 900000000000135021 AND 900000000000135025;           -- 0
--   SELECT COUNT(*) FROM t_file_scan_requirement WHERE requirement_id BETWEEN 900000000000135021 AND 900000000000135025; -- 0
--   SELECT COUNT(*) FROM t_file_storage_route WHERE route_id = 900000000000135025
--     OR (source_system = 'platform-support' AND access_level = 'PUBLIC');                                              -- 0
--   SELECT route_id, scan_policy_code, max_upload_seconds, version FROM t_file_storage_route
--     WHERE source_system = 'operations-console-bff' AND access_level = 'PRIVATE';
--     -- mor-test：一行，239087906591948800 / CARD_BIN_CSV / 900；记下 version 供回滚核对
--   SELECT b.bucket_id, b.status, b.access_level FROM t_file_storage_bucket b JOIN t_file_storage_instance i ON i.instance_id = b.instance_id
--     WHERE i.instance_code = 'MINIO' AND b.bucket_name = 'mor-platform-public';                                       -- 一行，ACTIVE、PUBLIC
-- 执行后：Platform 存储快照由定时任务刷新（或在「存储管理」任一写操作后立即刷新）；刷新前新路由与新策略不生效。
-- 可重跑：否。回退：见 dml-rollback.sql 第 3 部分。
-- ---------------------------------------------------------------------------
START TRANSACTION;

SET @i18n_file_time = UTC_TIMESTAMP(6);
SET @i18n_public_instance = 'MINIO';
SET @i18n_public_bucket = 'mor-platform-public';
SET @ops_bff_private_route_id = 239049429963771904;

INSERT INTO t_file_scan_policy (policy_id, policy_code, revision_no, rule_schema_version, allowed_mime_rules, max_size_bytes,
  effective_from, effective_to, status, active_slot, version, create_time, update_time) VALUES
  (900000000000135021, 'I18N_BUNDLE_JSON', 1, 1, '{"mimeTypes":["application/json"]}', 1048576, @i18n_file_time, NULL,
   'ACTIVE', 1, 1, @i18n_file_time, @i18n_file_time),
  (900000000000135023, 'OPERATIONS_BFF_IMPORT', 1, 1, '{"mimeTypes":["text/csv","application/json"]}', 52428800,
   @i18n_file_time, NULL, 'ACTIVE', 1, 1, @i18n_file_time, @i18n_file_time);

INSERT INTO t_file_scan_requirement (requirement_id, policy_id, requirement_no, scanner_code, scan_type, minimum_scanner_version,
  accepted_result_codes, version, create_time, update_time) VALUES
  (900000000000135022, 900000000000135021, 1, 'PLATFORM_FORMAT', 'FORMAT', '1', '["FORMAT_VALID"]', 1, @i18n_file_time,
   @i18n_file_time),
  (900000000000135024, 900000000000135023, 1, 'PLATFORM_FORMAT', 'FORMAT', '1', '["FORMAT_VALID"]', 1, @i18n_file_time,
   @i18n_file_time);

INSERT INTO t_file_storage_route (route_id, source_system, access_level, bucket_id, scan_policy_code, max_upload_seconds, version,
  create_time, update_time) VALUES
  (900000000000135025, 'platform-support', 'PUBLIC',
   (SELECT b.bucket_id FROM t_file_storage_bucket b JOIN t_file_storage_instance i ON i.instance_id = b.instance_id
     WHERE i.instance_code = 'MINIO' AND b.bucket_name = 'mor-platform-public' AND b.access_level = 'PUBLIC'
       AND b.status = 'ACTIVE' AND i.status = 'ACTIVE'),
   'I18N_BUNDLE_JSON', 300, 1, @i18n_file_time, @i18n_file_time);

-- 旧值：scan_policy_code = 'CARD_BIN_CSV'（max_upload_seconds = 900 不改）。
UPDATE t_file_storage_route
   SET scan_policy_code = 'OPERATIONS_BFF_IMPORT', version = version + 1, update_time = @i18n_file_time
 WHERE route_id = @ops_bff_private_route_id AND source_system = 'operations-console-bff' AND access_level = 'PRIVATE'
   AND scan_policy_code = 'CARD_BIN_CSV';
SELECT ROW_COUNT() AS operations_bff_route_updated; -- 必须为 1，否则 ROLLBACK

COMMIT;
