-- 发布副本：取自 paytech/client-master/docs/database/sql/changes/2026-10-22-application-document-file-address.sql
--   文件头与“第 1 步”段逐字照抄（mor_client_master，不带 USE，库名由 scripts/apply_sql.sh 传入）；第 2 步见 02 文件。
-- 阶段 7 第 4 步执行：在旧 CCT 运行时即可执行（旧代码只写 file_id，新列可空、新唯一键允许空值）。
-- 执行前备份本表（scripts/backup.sh 已含）；DDL 隐式提交，失败时先核对已完成的语句，禁止整段重跑。
-- 回滚：见下文文件头“第 1 步回退”。
-- 2026-10-22 文件多存储并存第二段（S2-B）：t_application_document 由 file_id 改存 Platform 文件地址 file_address。
-- 需求：requirements/20261022/platform-support-文件多存储并存/segment2.md 第 4.2、5 节。
-- 地址格式 /files/v1/{fileId}，由 Platform FileAddress 签发与解析；content_hash 继续冻结，含义不变。
--
-- 分两步执行，中间换上新代码：
--   第 1 步：加列可空 → 回填 → file_id 放宽为可空 → 新增三个以 file_address 替代 file_id 的唯一键（*_addr），旧唯一键暂留。
--            第 1 步之后旧代码（只写 file_id）与新代码（只写 file_address）都能运行。
--   第 2 步：新代码全部上线、确认没有 file_address 为空的行之后执行：删旧唯一键与 file_id，file_address 改 NOT NULL，新唯一键改回原名。
-- 执行前备份本表；DDL 隐式提交，失败时先核对已完成的语句，禁止整段重跑。
--
-- 回退：
--   第 1 步回退（仍在旧代码或已回滚到旧代码时）：
--     UPDATE `t_application_document` SET `file_id` = CAST(SUBSTRING(`file_address`, 11) AS UNSIGNED) WHERE `file_id` IS NULL;
--     ALTER TABLE `t_application_document`
--       DROP INDEX `uk_app_doc_client_addr`, DROP INDEX `uk_app_doc_partner_addr`, DROP INDEX `uk_app_doc_entity_addr`,
--       MODIFY COLUMN `file_id` BIGINT NOT NULL COMMENT 'Platform File 的逻辑引用，与 content_hash 成组冻结，不建立跨库物理外键。',
--       DROP COLUMN `file_address`;
--   第 2 步回退（回到第 1 步之后的形态）：
--     ALTER TABLE `t_application_document`
--       ADD COLUMN `file_id` BIGINT NULL COMMENT '已由 file_address 取代；迁移第 2 步删除，新代码不读写。' AFTER `document_type`,
--       MODIFY COLUMN `file_address` VARCHAR(255) NULL COMMENT '<同第 1 步>',
--       RENAME INDEX `uk_app_doc_client` TO `uk_app_doc_client_addr`,
--       RENAME INDEX `uk_app_doc_partner` TO `uk_app_doc_partner_addr`,
--       RENAME INDEX `uk_app_doc_entity` TO `uk_app_doc_entity_addr`;
--     UPDATE `t_application_document` SET `file_id` = CAST(SUBSTRING(`file_address`, 11) AS UNSIGNED);
--     ALTER TABLE `t_application_document`
--       ADD UNIQUE KEY `uk_app_doc_client` (`client_application_id`, `subject_party_id`, `requirement_code`, `document_type`, `file_id`),
--       ADD UNIQUE KEY `uk_app_doc_partner` (`partner_application_id`, `subject_party_id`, `requirement_code`, `document_type`, `file_id`),
--       ADD UNIQUE KEY `uk_app_doc_entity` (`operating_entity_application_id`, `subject_party_id`, `requirement_code`, `document_type`, `file_id`);
SET time_zone = '+00:00';

-- =====================================================================================================================
-- 第 1 步：加列、回填、放宽、新增唯一键（可在旧代码运行时执行）
-- =====================================================================================================================

ALTER TABLE `t_application_document`
  ADD COLUMN `file_address` VARCHAR(255) NULL COMMENT 'Platform 文件服务签发的稳定逻辑地址，格式 /files/v1/{fileId}，与 content_hash 成组冻结；不是下载链接，不含存储实例与桶，只能经文件服务换取临时下载地址；不建立跨库物理外键。' AFTER `document_type`;

UPDATE `t_application_document` SET `file_address` = CONCAT('/files/v1/', `file_id`) WHERE `file_address` IS NULL;

ALTER TABLE `t_application_document`
  MODIFY COLUMN `file_id` BIGINT NULL COMMENT '已由 file_address 取代；迁移第 2 步删除，新代码不读写。',
  ADD UNIQUE KEY `uk_app_doc_client_addr` (`client_application_id`, `subject_party_id`, `requirement_code`, `document_type`, `file_address`) COMMENT 'Unique index for application_document on client_application_id, subject_party_id, requirement_code, document_type, file_address',
  ADD UNIQUE KEY `uk_app_doc_partner_addr` (`partner_application_id`, `subject_party_id`, `requirement_code`, `document_type`, `file_address`) COMMENT 'Unique index for application_document on partner_application_id, subject_party_id, requirement_code, document_type, file_address',
  ADD UNIQUE KEY `uk_app_doc_entity_addr` (`operating_entity_application_id`, `subject_party_id`, `requirement_code`, `document_type`, `file_address`) COMMENT 'Unique index for application_document on operating_entity_application_id, subject_party_id, requirement_code, document_type, file_address';

-- 第 1 步核对（期望：missing_address = 0，mismatched = 0，total 与执行前行数一致）：
-- SELECT COUNT(*) AS total,
--        SUM(`file_address` IS NULL) AS missing_address,
--        SUM(`file_id` IS NOT NULL AND `file_address` <> CONCAT('/files/v1/', `file_id`)) AS mismatched
--   FROM `t_application_document`;

