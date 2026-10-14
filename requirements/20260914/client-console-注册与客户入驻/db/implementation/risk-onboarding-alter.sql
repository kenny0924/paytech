-- Client 入驻 4b：本机 mor_risk 结构迁移。由 generate-risk-onboarding-alter.py 从正式 Schema 生成，勿手改。
-- 前置：相关表为空（2026-10-13 只读核对）；执行前须重新核对并取得精确授权。DDL 自动提交，不使用 --force。
-- 回滚：DROP TABLE t_dd_case_classification；删除新增列并恢复 uk_dd_case_source 原定义（见执行清单）。
ALTER TABLE `t_due_diligence_case`
  ADD COLUMN `source_submission_id` VARCHAR(64) NULL COMMENT 'Client 每次真实提交的 UUID；旧 APPLICATION 来源适配为准确 legacy 标识；同次重试复用。' AFTER `source_application_id`,
  MODIFY COLUMN `source_submission_version` BIGINT NULL COMMENT '未迁移 APPLICATION 来源的正整数版本；新 Client 使用 source_submission_id，本列为空。',
  MODIFY COLUMN `source_submission_hash` CHAR(64) NULL COMMENT '未迁移 APPLICATION 来源的提交摘要；新 Client 不填本列，以准确提交身份及 Owner 幂等记录校验重放。',
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '案件状态的稳定业务代码。允许值：DRAFT、SUBMITTED、IN_REVIEW、WAITING_EVIDENCE、RETURNED、APPROVED、REJECTED、WITHDRAWN、EXPIRED。',
  ADD COLUMN `public_note` VARCHAR(1000) NULL COMMENT '运营单独确认的安全退回或决定说明，不复制内部审核理由。' AFTER `source_submission_id`,
  ADD COLUMN `resolution_note` VARCHAR(2000) NULL COMMENT '本任务结束的内部审核理由，禁止直接展示给 Client。' AFTER `public_note`,
  ADD COLUMN `return_section` VARCHAR(32) NULL COMMENT '退回定位到单页的资料分区。允许值：BASIC、BUSINESS、PEOPLE、MATERIALS。' AFTER `resolution_note`,
  DROP INDEX `uk_dd_case_source`,
  ADD UNIQUE KEY `uk_dd_case_source` (`source_namespace`, `source_application_id`, `source_submission_id`, `role_type`, `role_id`, `qualification_type`) COMMENT '保证业务唯一性：source_namespace、source_application_id、source_submission_id、role_type、role_id、qualification_type';
ALTER TABLE `t_dd_case_subject`
  ADD COLUMN `source_relation_id` BIGINT NULL COMMENT '来源稳定关系的稳定业务标识。' AFTER `source_profile_id`,
  MODIFY COLUMN `source_profile_version_no` BIGINT NULL COMMENT '旧来源保留真实 Profile 版本；新 Client 使用稳定 Profile ID，本列为空，禁止伪造版本。';
ALTER TABLE `t_qualification_decision`
  MODIFY COLUMN `decision_version` INT NULL COMMENT '旧资格修订合同的真实版本；新 Client 每个 Case 只有一个最终决定，使用 Decision ID，本列为空。';
CREATE TABLE `t_dd_case_classification` (
  `case_classification_id` BIGINT NOT NULL COMMENT '本任务经营分类行的稳定标识。',
  `case_id` BIGINT NOT NULL COMMENT '尽调案件的稳定业务标识。',
  `industry_code` VARCHAR(64) NOT NULL COMMENT '运营独立选择的行业。取值来源：CCT 行业目录；非固定枚举。',
  `mcc_code` VARCHAR(4) NOT NULL COMMENT '运营独立确认的四位 MCC。取值来源：CCT MCC 目录；非固定枚举。',
  `primary_slot` TINYINT(1) NULL COMMENT '主要分类为 1，其他为 NULL；同任务恰好一个主要分类，由应用和唯一索引共同保证。',
  `confirmed_by` BIGINT NOT NULL COMMENT '分类确认人的稳定业务标识。',
  `confirmed_time` DATETIME(6) NOT NULL COMMENT '分类确认时间对应的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`case_classification_id`),
  UNIQUE KEY `uk_case_classification_pair` (`case_id`, `industry_code`, `mcc_code`) COMMENT '保证业务唯一性：case_id、industry_code、mcc_code',
  UNIQUE KEY `uk_case_classification_primary` (`case_id`, `primary_slot`) COMMENT '保证业务唯一性：case_id、primary_slot'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存 CCT 运营确认后传入的当前尽调任务经营分类证据，确认后不可修改';
