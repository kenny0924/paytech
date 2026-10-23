-- =============================================================================
-- Platform Support 文件公开桶与固定访问地址 增量 DDL
-- 需求：requirements/20261022/platform-support-文件公开桶/requirement.md、technique.md 第 4.1、4.2、4.4 节、tasks/common.md
-- 同步副本：paytech/platform-support/docs/database/sql/changes/2026-10-22-file-public-bucket.sql（两份内容一致）
--
-- 状态：人工评审后在目标库 mor_platform 执行；应用与测试不自动执行。
-- 执行账号：需要 CREATE / ALTER 权限（本机 platform_app 持有 mor_platform ALL PRIVILEGES）。
--
-- 前置条件：
--   - 目标库已是 main 73a2460d（文件多存储并存第二段）之后的结构：t_file_upload 无 owner_* 列、t_file_owner_ref 不存在；
--   - 本文件只新增表与列、放宽 3 个唯一键并加 2 个索引；新列都有默认值且默认值即存量取值，执行前后旧镜像都能运行
--     （旧镜像的写入都落在默认值上，变宽后的唯一键对其仍然成立）。
--
-- 执行前备份（在 shell 执行，备份文件留存到新链路稳定后再删）：
--   mysqldump --single-transaction --no-tablespaces -h <host> -P <port> -u <user> -p mor_platform \
--     t_file_object t_file_upload t_file_scan_result t_file_storage_bucket t_file_storage_route \
--     > mor_platform-file-public-bucket-backup-$(date +%Y%m%d%H%M%S).sql
--
-- 执行顺序：
--   1. 执行前核验（记录行数）；
--   2. 新建 t_file_version；
--   3. t_file_object、t_file_upload、t_file_storage_bucket、t_file_storage_route、t_file_scan_result 加列、改唯一键、加索引与注释；
--   4. 执行后核验；
--   5. 执行同目录 dml.sql 回填 v1 版本行（可重跑）。
--
-- 回退（仅在需要回滚到本需求之前的 Platform 镜像时执行；前提：不存在 PUBLIC 桶或路由、不存在 version_no > 1 的版本、
-- 不存在 replacement_no > 0 的上传；否则先人工处置这些数据）：
--   ALTER TABLE `t_file_scan_result` DROP INDEX `uk_file_scan_attempt`,
--     ADD UNIQUE KEY `uk_file_scan_attempt` (`file_id`, `scan_requirement_id`, `attempt_no`) COMMENT '保证业务唯一性：file_id、scan_requirement_id、attempt_no',
--     DROP COLUMN `file_version_no`;
--   ALTER TABLE `t_file_storage_route` DROP INDEX `uk_file_storage_route_source`,
--     ADD UNIQUE KEY `uk_file_storage_route_source` (`source_system`) COMMENT '保证业务唯一性：source_system',
--     DROP COLUMN `access_level`;
--   ALTER TABLE `t_file_storage_bucket` DROP COLUMN `public_base_url`, DROP COLUMN `access_level`;
--   ALTER TABLE `t_file_upload` DROP INDEX `idx_file_upload_replaces`, DROP INDEX `uk_upload_storage_target`,
--     ADD UNIQUE KEY `uk_upload_storage_target` (`storage_provider`, `storage_container_ref`, `storage_object_ref`) COMMENT '同一存储对象上传目标只归属一条 Upload，防止不同申请共享可写目标',
--     DROP COLUMN `base_object_version`, DROP COLUMN `replacement_no`, DROP COLUMN `replaces_file_id`, DROP COLUMN `access_level`;
--   ALTER TABLE `t_file_object` DROP INDEX `idx_file_create_time`, DROP COLUMN `current_version_no`, DROP COLUMN `access_level`;
--   DROP TABLE `t_file_version`;
--   （列注释与表注释的回退以备份文件中的 SHOW CREATE TABLE 为准，不影响旧镜像运行。）
-- =============================================================================

-- 1. 执行前核验 -----------------------------------------------------------------

SELECT COUNT(*) AS file_rows FROM t_file_object;
SELECT COUNT(*) AS upload_rows FROM t_file_upload;
SELECT COUNT(*) AS scan_result_rows FROM t_file_scan_result;
SELECT COUNT(*) AS bucket_rows FROM t_file_storage_bucket;
SELECT COUNT(*) AS route_rows FROM t_file_storage_route;

-- 2. 新建文件版本表 ---------------------------------------------------------------

CREATE TABLE `t_file_version` (
  `version_id` BIGINT NOT NULL COMMENT '文件版本主键；回填的 v1 使用 file_id 的值，新版本使用服务端 Snowflake ID。',
  `file_id` BIGINT NOT NULL COMMENT '所属文件 ID。',
  `version_no` INT NOT NULL COMMENT '版本号；首次上传为 1，每次替换完成加 1，创建后不变。',
  `upload_id` BIGINT NULL COMMENT '产生该版本的上传 ID；非上传来源的文件为空。',
  `object_version` VARCHAR(128) NOT NULL COMMENT '该版本在存储中的 versionId；读取与下载固定到该版本，不读取 latest。',
  `content_hash` CHAR(64) NOT NULL COMMENT '该版本实际字节的 SHA-256 小写十六进制值。',
  `size_bytes` BIGINT NOT NULL COMMENT '该版本实际字节数。',
  `mime_type` VARCHAR(255) NOT NULL COMMENT '服务端按文件头识别的该版本媒体类型。取值来源：IANA Media Types 与 Platform 允许列表；非固定枚举。',
  `storage_encryption_key_ref` VARCHAR(255) NOT NULL COMMENT '该版本经存储验证的服务端加密保护引用。',
  `scan_policy_id` BIGINT NOT NULL COMMENT '该版本在上传开始时冻结的扫描策略修订 ID。',
  `scan_status` VARCHAR(64) NOT NULL COMMENT '该版本的扫描状态。允许值：PENDING_SCAN、SCANNING、AVAILABLE、QUARANTINED、REJECTED。',
  `version_status` VARCHAR(16) NOT NULL COMMENT '版本状态；CANDIDATE 为检查中，CURRENT 为当前对外提供（每个文件最多一个），SUPERSEDED 为已被更新版本取代，REJECTED 为检查不通过。允许值：CANDIDATE、CURRENT、SUPERSEDED、REJECTED。',
  `available_time` DATETIME(6) NULL COMMENT '该版本检查通过的 UTC 微秒时间；未通过为空。',
  `version` INT NOT NULL COMMENT '乐观并发版本，新建为 1，每次成功修改递增 1。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_file_version_no` (`file_id`, `version_no`) COMMENT '保证业务唯一性：file_id、version_no（同一文件的版本号不重复）',
  UNIQUE KEY `uk_file_version_upload` (`upload_id`) COMMENT '保证业务唯一性：upload_id（一次上传最多产生一个版本）',
  KEY `idx_file_version_scan` (`scan_status`, `create_time`) COMMENT '支持访问路径：scan_status、create_time（检查中版本排查）',
  KEY `idx_file_version_scan_policy_id` (`scan_policy_id`) COMMENT '支持访问路径：scan_policy_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存文件的每一次内容版本：存储 versionId、摘要、大小、类型、冻结策略、扫描状态与版本状态；文件地址不变，当前版本镜像在文件表';

-- 3. 现有表增量 -------------------------------------------------------------------

ALTER TABLE `t_file_object`
  ADD COLUMN `access_level` VARCHAR(16) NOT NULL DEFAULT 'PRIVATE' COMMENT '访问级别，上传时由调用方声明并复制到文件，之后不变；PUBLIC 文件位于公开桶，检查通过后提供长期公开地址。允许值：PRIVATE、PUBLIC。' AFTER `reason_code`,
  ADD COLUMN `current_version_no` INT NOT NULL DEFAULT 1 COMMENT '当前对外提供的版本号，对应 t_file_version.version_no；替换内容检查通过后递增。' AFTER `storage_object_ref`,
  MODIFY COLUMN `storage_object_ref` VARCHAR(255) NOT NULL COMMENT '存储对象引用；各版本共用同一对象名，由桶的版本控制区分版本。',
  MODIFY COLUMN `object_version` VARCHAR(128) NOT NULL COMMENT '当前版本在存储中的 versionId（镜像 t_file_version）；下载签发固定到该版本。',
  MODIFY COLUMN `content_hash` CHAR(64) NOT NULL COMMENT '当前版本内容的 SHA-256 小写十六进制值（镜像 t_file_version）。',
  MODIFY COLUMN `size_bytes` BIGINT NOT NULL COMMENT '当前版本字节数（镜像 t_file_version）。',
  MODIFY COLUMN `mime_type` VARCHAR(255) NOT NULL COMMENT '当前版本内容的规范媒体类型（镜像 t_file_version）。取值来源：IANA Media Types 与 Platform 允许列表；非固定枚举。',
  MODIFY COLUMN `storage_encryption_key_ref` VARCHAR(255) NOT NULL COMMENT '当前版本的存储加密密钥引用（镜像 t_file_version）。',
  MODIFY COLUMN `scan_policy_id` BIGINT NOT NULL COMMENT '当前版本冻结扫描策略修订的稳定业务标识（镜像 t_file_version）。',
  MODIFY COLUMN `scan_status` VARCHAR(64) NOT NULL COMMENT '当前版本的扫描状态（镜像 t_file_version；首次上传检查中即为 v1 的状态）。允许值：PENDING_SCAN、SCANNING、AVAILABLE、QUARANTINED、REJECTED。',
  MODIFY COLUMN `available_time` DATETIME(6) NULL COMMENT '当前版本检查通过的 UTC 微秒时间（镜像 t_file_version）。',
  ADD KEY `idx_file_create_time` (`create_time`) COMMENT '支持访问路径：create_time（文件记录按时间范围倒序分页）',
  COMMENT = '保存文件身份（来源、原因、访问级别、存储实例、桶与对象名）与当前版本镜像；地址按 file_id 不变，各版本见 t_file_version';

ALTER TABLE `t_file_upload`
  ADD COLUMN `access_level` VARCHAR(16) NOT NULL DEFAULT 'PRIVATE' COMMENT '访问级别；开始上传时由调用方声明（缺省 PRIVATE）并按 (来源系统, 访问级别) 选择写入路由，替换上传沿用原文件的值，创建后不变。允许值：PRIVATE、PUBLIC。' AFTER `reason_code`,
  ADD COLUMN `replaces_file_id` BIGINT NULL COMMENT '被替换内容的文件 ID；首次上传为空。' AFTER `storage_object_ref`,
  ADD COLUMN `replacement_no` INT NOT NULL DEFAULT 0 COMMENT '替换序号；首次上传为 0，同一文件的第 n 次替换为 n。' AFTER `replaces_file_id`,
  ADD COLUMN `base_object_version` VARCHAR(128) NULL COMMENT '开始替换时存储中该对象最新版本的 versionId；完成时要求比它新的版本恰好 1 个。首次上传为空。' AFTER `replacement_no`,
  DROP INDEX `uk_upload_storage_target`,
  ADD UNIQUE KEY `uk_upload_storage_target` (`storage_provider`, `storage_container_ref`, `storage_object_ref`, `replacement_no`) COMMENT '同一存储对象的同一替换序号只归属一条 Upload，防止不同申请共享可写目标',
  ADD KEY `idx_file_upload_replaces` (`replaces_file_id`, `status`) COMMENT '支持访问路径：replaces_file_id、status（判断文件是否有未结束的替换）',
  COMMENT = '保存受控上传（首次上传或替换）的来源系统与原因、访问级别、租户与上传人、展示名、原存储目标、替换序号与冻结扫描策略；不记录业务归属，上传授权由存储适配器签发，不持久化临时 URL、签名或额外上传秘密';

ALTER TABLE `t_file_storage_bucket`
  ADD COLUMN `access_level` VARCHAR(16) NOT NULL DEFAULT 'PRIVATE' COMMENT '访问级别，登记后不可修改；写入路由只能指向同级别的桶。允许值：PRIVATE、PUBLIC。' AFTER `bucket_name`,
  ADD COLUMN `public_base_url` VARCHAR(255) NULL COMMENT '对外基础地址（CDN 或桶公开地址）；PUBLIC 必填，https:// 开头、不带查询串与片段、不以 / 结尾，只在 DRAFT 时可修改；PRIVATE 为空。公开地址 = 基础地址 + / + 对象名。' AFTER `access_level`,
  MODIFY COLUMN `last_check_result` VARCHAR(64) NULL COMMENT '最近一次启用检查结果；从未检查或修改基础地址后为空。允许值：OK、NOT_FOUND、VERSIONING_DISABLED、ENCRYPTION_DISABLED、ACCESS_DENIED、UNAVAILABLE、PUBLIC_ACCESS_ALLOWED、PUBLIC_READ_FAILED。';

ALTER TABLE `t_file_storage_route`
  ADD COLUMN `access_level` VARCHAR(16) NOT NULL DEFAULT 'PRIVATE' COMMENT '访问级别；与来源系统共同构成路由键，只能指向同级别的桶，解析时不跨级别回落；创建后不可修改。允许值：PRIVATE、PUBLIC。' AFTER `source_system`,
  MODIFY COLUMN `source_system` VARCHAR(64) NOT NULL COMMENT '来源系统，即内部调用 Token 的服务名（[a-z][a-z0-9-]{0,63}）；* 表示默认路由，同级别没有精确路由的来源使用该级别的默认路由。取值来源：内部调用身份的服务名；非固定枚举。',
  DROP INDEX `uk_file_storage_route_source`,
  ADD UNIQUE KEY `uk_file_storage_route_source` (`source_system`, `access_level`) COMMENT '保证业务唯一性：source_system、access_level',
  COMMENT = '保存按 (来源系统, 访问级别) 选择新上传存储桶、扫描策略与上传期限的写入路由；改路由只影响之后的新上传';

ALTER TABLE `t_file_scan_result`
  ADD COLUMN `file_version_no` INT NOT NULL DEFAULT 1 COMMENT '被检查的文件版本号，对应 t_file_version.version_no。' AFTER `file_id`,
  DROP INDEX `uk_file_scan_attempt`,
  ADD UNIQUE KEY `uk_file_scan_attempt` (`file_id`, `file_version_no`, `scan_requirement_id`, `attempt_no`) COMMENT '保证业务唯一性：file_id、file_version_no、scan_requirement_id、attempt_no',
  COMMENT = '追加保存文件各版本对其冻结策略修订中每项扫描要求的运行版本与判定';

-- 4. 执行后核验 -------------------------------------------------------------------

SELECT table_name, column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 't_file_object' AND column_name IN ('access_level', 'current_version_no'))
    OR (table_name = 't_file_upload' AND column_name IN ('access_level', 'replaces_file_id', 'replacement_no', 'base_object_version'))
    OR (table_name = 't_file_storage_bucket' AND column_name IN ('access_level', 'public_base_url'))
    OR (table_name = 't_file_storage_route' AND column_name = 'access_level')
    OR (table_name = 't_file_scan_result' AND column_name = 'file_version_no'))
ORDER BY table_name, ordinal_position;

SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns_in_index
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND index_name IN ('uk_file_version_no', 'uk_file_version_upload', 'idx_file_version_scan', 'idx_file_version_scan_policy_id', 'idx_file_create_time',
    'uk_upload_storage_target', 'idx_file_upload_replaces', 'uk_file_storage_route_source', 'uk_file_scan_attempt')
GROUP BY table_name, index_name
ORDER BY table_name, index_name;
