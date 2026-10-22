-- 发布副本：取自同一文件的“第 2 步”段，去掉行首注释符后可执行（mor_client_master）。
-- 只在新 CCT 稳定运行、验收通过并另行授权后执行。执行前：
--   1. 再备份 t_application_document；
--   2. 补一次回填（旧 CCT 在第 1 步之后、换新镜像之前写入的行 file_address 为空）：
--      UPDATE `t_application_document` SET `file_address` = CONCAT('/files/v1/', `file_id`) WHERE `file_address` IS NULL;
--   3. 前置核对：SELECT COUNT(*) FROM `t_application_document` WHERE `file_address` IS NULL;  -- 必须为 0
-- 执行后不能再回滚到旧 CCT（旧代码读写 file_id）；回到第 1 步形态的语句见 01 文件头“第 2 步回退”。
SET time_zone = '+00:00';

UPDATE `t_application_document` SET `file_address` = CONCAT('/files/v1/', `file_id`) WHERE `file_address` IS NULL;
SELECT COUNT(*) AS missing_address_must_be_0 FROM `t_application_document` WHERE `file_address` IS NULL;

ALTER TABLE `t_application_document`
  DROP INDEX `uk_app_doc_client`,
  DROP INDEX `uk_app_doc_partner`,
  DROP INDEX `uk_app_doc_entity`,
  DROP COLUMN `file_id`,
  MODIFY COLUMN `file_address` VARCHAR(255) NOT NULL COMMENT 'Platform 文件服务签发的稳定逻辑地址，格式 /files/v1/{fileId}，与 content_hash 成组冻结；不是下载链接，不含存储实例与桶，只能经文件服务换取临时下载地址；不建立跨库物理外键。',
  MODIFY COLUMN `content_hash` CHAR(64) NOT NULL COMMENT 'Platform 文件内容哈希；输入为 Platform File 返回的文件正文 SHA-256，与 file_address 成组冻结。',
  RENAME INDEX `uk_app_doc_client_addr` TO `uk_app_doc_client`,
  RENAME INDEX `uk_app_doc_partner_addr` TO `uk_app_doc_partner`,
  RENAME INDEX `uk_app_doc_entity_addr` TO `uk_app_doc_entity`,
  COMMENT = '三类申请共享的材料引用；uk_app_doc_client(client_application_id,subject_party_id,requirement_code,document_type,file_address)、uk_app_doc_partner(partner_application_id,subject_party_id,requirement_code,document_type,file_address)、uk_app_doc_entity(operating_entity_application_id,subject_party_id,requirement_code,document_type,file_address)；三个 Parent FK 均可空但满足 (client_application_id IS NOT NULL) + (partner_application_id IS NOT NULL) + (operating_entity_application_id IS NOT NULL) = 1，且均为本库 RESTRICT；subject_party_id -> party 为真实 FK；CHECK ((client_application_id IS NOT NULL) + (partner_application_id IS NOT NULL) + (operating_entity_application_id IS NOT NULL) = 1)；client_application_id、partner_application_id 与 operating_entity_application_id 三个 Parent FK 均为本库 RESTRICT。';
