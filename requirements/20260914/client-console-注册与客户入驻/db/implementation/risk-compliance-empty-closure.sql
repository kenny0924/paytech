-- REVIEW CANDIDATE. Do not execute before environment-specific approval.

-- Stop writers; back up; compare actual SHOW CREATE with the baseline;

-- require the complete owner-local reference closure to be empty.

-- No rollback by deleting history. See ../../migration-plan.md.

-- Do not run with mysql --force. DDL auto-commits.

SET NAMES utf8mb4;

ALTER TABLE `t_due_diligence_case`
  DROP INDEX `uk_dd_case_no`,
  DROP INDEX `uk_dd_case_source`,
  DROP INDEX `uk_dd_case_active`,
  DROP INDEX `uk_dd_case_review`,
  DROP INDEX `idx_dd_case_prog`,
  MODIFY COLUMN `case_id` BIGINT NOT NULL COMMENT '尽调案件的稳定业务标识。' FIRST,
  MODIFY COLUMN `case_no` VARCHAR(64) NOT NULL COMMENT '案件编号的业务值。' AFTER `case_id`,
  MODIFY COLUMN `role_type` VARCHAR(64) NOT NULL COMMENT '角色类型的稳定业务代码。允许值：CLIENT、LOCAL_PARTNER、OPERATING_ENTITY、RELATED_PERSON。' AFTER `case_no`,
  MODIFY COLUMN `role_id` BIGINT NOT NULL COMMENT '角色的稳定业务标识。' AFTER `role_type`,
  MODIFY COLUMN `qualification_type` VARCHAR(64) NOT NULL COMMENT '资格类型的稳定业务代码。允许值：INDIVIDUAL_KYC、ORGANIZATION_KYB、PARTNER_DUE_DILIGENCE、ENTITY_UNDERWRITING、RELATED_PERSON_KYC。' AFTER `role_id`,
  MODIFY COLUMN `dd_program_version_id` BIGINT NOT NULL COMMENT '尽调程序版本的稳定业务标识。' AFTER `qualification_type`,
  MODIFY COLUMN `case_origin` VARCHAR(64) NOT NULL COMMENT '案件来源类型的稳定业务代码。允许值：APPLICATION、CONTINUOUS_REVIEW、MANUAL。' AFTER `dd_program_version_id`,
  MODIFY COLUMN `scheduled_review_time` DATETIME(6) NULL COMMENT '计划复审时间对应的 UTC 微秒时间。' AFTER `case_origin`,
  MODIFY COLUMN `source_namespace` VARCHAR(64) NOT NULL COMMENT '区分 CCT Client Application、CCT Operating Entity Application、CCT Local Partner Application 与 Risk 本地来源的稳定命名空间。允许值：CCT_CLIENT_APPLICATION、CCT_OPERATING_ENTITY_APPLICATION、CCT_LOCAL_PARTNER_APPLICATION、RISK_COMPLIANCE。' AFTER `scheduled_review_time`,
  MODIFY COLUMN `source_application_id` BIGINT NULL COMMENT '来源申请的稳定业务标识。' AFTER `source_namespace`,
  ADD COLUMN `source_submission_id` VARCHAR(64) NULL COMMENT 'Client 每次真实提交的 UUID；旧 APPLICATION 来源适配为准确 legacy 标识；同次重试复用。' AFTER `source_application_id`,
  ADD COLUMN `public_note` VARCHAR(1000) NULL COMMENT '运营单独确认的安全退回或决定说明，不复制内部审核理由。' AFTER `source_submission_id`,
  ADD COLUMN `resolution_note` VARCHAR(2000) NULL COMMENT '本任务结束的内部审核理由，禁止直接展示给 Client。' AFTER `public_note`,
  ADD COLUMN `return_section` VARCHAR(32) NULL COMMENT '退回定位到单页的资料分区。允许值：BASIC、BUSINESS、PEOPLE、MATERIALS。' AFTER `resolution_note`,
  MODIFY COLUMN `source_submission_version` BIGINT NULL COMMENT '未迁移 APPLICATION 来源的正整数版本；新 Client 使用 source_submission_id，本列为空。' AFTER `return_section`,
  MODIFY COLUMN `source_submission_hash` CHAR(64) NULL COMMENT '未迁移 APPLICATION 来源的提交摘要；新 Client 不填本列，以准确提交身份及 Owner 幂等记录校验重放。' AFTER `source_submission_version`,
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '案件状态的稳定业务代码。允许值：DRAFT、SUBMITTED、IN_REVIEW、WAITING_EVIDENCE、RETURNED、APPROVED、REJECTED、WITHDRAWN、EXPIRED。' AFTER `source_submission_hash`,
  MODIFY COLUMN `active_slot` BOOLEAN NULL COMMENT '进行中状态时为 1，终态必须为 NULL；禁止写入 0。' AFTER `status`,
  MODIFY COLUMN `submitted_time` DATETIME(6) NULL COMMENT '提交时间对应的 UTC 微秒时间。' AFTER `active_slot`,
  MODIFY COLUMN `decision_time` DATETIME(6) NULL COMMENT '决定时间对应的 UTC 微秒时间。' AFTER `submitted_time`,
  MODIFY COLUMN `version` BIGINT NOT NULL COMMENT '案件并发版本的长整数值。' AFTER `decision_time`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。' AFTER `version`,
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。' AFTER `create_time`,
  ADD UNIQUE KEY `uk_dd_case_no` (`case_no`) COMMENT '保证业务唯一性：case_no',
  ADD UNIQUE KEY `uk_dd_case_source` (`source_namespace`, `source_application_id`, `source_submission_id`, `role_type`, `role_id`, `qualification_type`) COMMENT '保证业务唯一性：source_namespace、source_application_id、source_submission_id、role_type、role_id、qualification_type',
  ADD UNIQUE KEY `uk_dd_case_active` (`role_type`, `role_id`, `qualification_type`, `active_slot`) COMMENT '保证业务唯一性：role_type、role_id、qualification_type、active_slot',
  ADD UNIQUE KEY `uk_dd_case_review` (`role_type`, `role_id`, `qualification_type`, `scheduled_review_time`) COMMENT '保证业务唯一性：role_type、role_id、qualification_type、scheduled_review_time',
  ADD KEY `idx_dd_case_prog` (`dd_program_version_id`) COMMENT '支持访问路径：dd_program_version_id',
  COMMENT = '保存一次角色化 KYC、KYB、合作伙伴尽调或经营主体承保案件';

ALTER TABLE `t_dd_case_subject`
  DROP INDEX `uk_dd_case_subject`,
  DROP INDEX `uk_dd_case_primary`,
  DROP INDEX `uk_dd_subject_relation`,
  MODIFY COLUMN `case_subject_id` BIGINT NOT NULL COMMENT '案件主体范围的稳定业务标识。' FIRST,
  MODIFY COLUMN `case_id` BIGINT NOT NULL COMMENT '尽调案件的稳定业务标识。' AFTER `case_subject_id`,
  MODIFY COLUMN `party_id` BIGINT NOT NULL COMMENT 'Party 主体的稳定业务标识。' AFTER `case_id`,
  MODIFY COLUMN `subject_role` VARCHAR(64) NOT NULL COMMENT '主体角色的稳定业务代码。允许值：PRIMARY、UBO、DIRECTOR、SIGNATORY。' AFTER `party_id`,
  MODIFY COLUMN `primary_slot` BOOLEAN NULL COMMENT 'PRIMARY 主体时为 1，关联主体必须为 NULL；禁止写入 0。' AFTER `subject_role`,
  MODIFY COLUMN `source_profile_id` BIGINT NOT NULL COMMENT '来源档案的稳定业务标识。' AFTER `primary_slot`,
  ADD COLUMN `source_relation_id` BIGINT NULL COMMENT '来源稳定关系的稳定业务标识。' AFTER `source_profile_id`,
  MODIFY COLUMN `source_profile_version_no` BIGINT NULL COMMENT '旧来源保留真实 Profile 版本；新 Client 使用稳定 Profile ID，本列为空，禁止伪造版本。' AFTER `source_relation_id`,
  MODIFY COLUMN `source_relation_version_id` BIGINT NULL COMMENT '关联主体对应的 CCT Party Relation Version 稳定标识；PRIMARY 必须为 NULL。' AFTER `source_profile_version_no`,
  MODIFY COLUMN `source_relation_version_no` BIGINT NULL COMMENT '关联主体对应的 CCT Party Relation Version version_no 正整数坐标；PRIMARY 必须为 NULL。' AFTER `source_relation_version_id`,
  MODIFY COLUMN `ownership_percentage` DECIMAL(18,10) NULL COMMENT 'UBO 来源关系在 CCT 中冻结的持股百分比，取值大于 0 且不超过 100；其他主体角色必须为 NULL。' AFTER `source_relation_version_no`,
  MODIFY COLUMN `required_flag` BOOLEAN NOT NULL COMMENT '是否必需，1 表示是，0 表示否。' AFTER `ownership_percentage`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。' AFTER `required_flag`,
  ADD UNIQUE KEY `uk_dd_case_subject` (`case_id`, `party_id`, `subject_role`) COMMENT '保证业务唯一性：case_id、party_id、subject_role',
  ADD UNIQUE KEY `uk_dd_case_primary` (`case_id`, `primary_slot`) COMMENT '保证业务唯一性：case_id、primary_slot',
  ADD UNIQUE KEY `uk_dd_subject_relation` (`case_id`, `source_relation_version_id`) COMMENT '保证业务唯一性：case_id、source_relation_version_id',
  COMMENT = '冻结尽调案件覆盖的 Party、角色、来源 Profile 坐标及关联主体关系事实';

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

ALTER TABLE `t_qualification_decision`
  DROP INDEX `uk_qual_decision_ver`,
  DROP INDEX `idx_qual_dec_eval`,
  MODIFY COLUMN `decision_id` BIGINT NOT NULL COMMENT '资格决定的稳定业务标识。' FIRST,
  MODIFY COLUMN `case_id` BIGINT NOT NULL COMMENT '尽调案件的稳定业务标识。' AFTER `decision_id`,
  MODIFY COLUMN `decision_version` INT NULL COMMENT '旧资格修订合同的真实版本；新 Client 每个 Case 只有一个最终决定，使用 Decision ID，本列为空。' AFTER `case_id`,
  MODIFY COLUMN `role_type` VARCHAR(64) NOT NULL COMMENT '角色类型的稳定业务代码。允许值：CLIENT、LOCAL_PARTNER、OPERATING_ENTITY、RELATED_PERSON。' AFTER `decision_version`,
  MODIFY COLUMN `role_id` BIGINT NOT NULL COMMENT '角色的稳定业务标识。' AFTER `role_type`,
  MODIFY COLUMN `qualification_type` VARCHAR(64) NOT NULL COMMENT '资格类型的稳定业务代码。允许值：INDIVIDUAL_KYC、ORGANIZATION_KYB、PARTNER_DUE_DILIGENCE、ENTITY_UNDERWRITING、RELATED_PERSON_KYC。' AFTER `role_id`,
  MODIFY COLUMN `decision_evaluation_id` BIGINT NULL COMMENT '规则评估的稳定业务标识。' AFTER `qualification_type`,
  MODIFY COLUMN `decision_source` VARCHAR(64) NOT NULL COMMENT '决定来源的稳定业务代码。允许值：AUTOMATED、REVIEWER、OVERRIDE。' AFTER `decision_evaluation_id`,
  MODIFY COLUMN `recommended_decision` VARCHAR(64) NULL COMMENT '建议结论的稳定业务代码。允许值：APPROVE、REVIEW、REJECT。' AFTER `decision_source`,
  MODIFY COLUMN `outcome` VARCHAR(64) NOT NULL COMMENT '资格结论的稳定业务代码。允许值：APPROVED、REJECTED、REVOKED、EXPIRED。' AFTER `recommended_decision`,
  MODIFY COLUMN `override_reason_code` VARCHAR(64) NULL COMMENT '覆盖原因的稳定业务代码。取值来源：Risk 资格覆盖原因目录；非固定枚举。' AFTER `outcome`,
  MODIFY COLUMN `valid_from` DATETIME(6) NOT NULL COMMENT '有效开始时间对应的 UTC 微秒时间。' AFTER `override_reason_code`,
  MODIFY COLUMN `valid_to` DATETIME(6) NULL COMMENT '有效结束时间对应的 UTC 微秒时间。' AFTER `valid_from`,
  MODIFY COLUMN `decided_by` BIGINT NULL COMMENT '决定人的稳定业务标识。' AFTER `valid_to`,
  MODIFY COLUMN `decided_time` DATETIME(6) NOT NULL COMMENT '决定时间对应的 UTC 微秒时间。' AFTER `decided_by`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。' AFTER `decided_time`,
  ADD UNIQUE KEY `uk_qual_decision_ver` (`case_id`, `decision_version`) COMMENT '保证业务唯一性：case_id、decision_version',
  ADD KEY `idx_qual_dec_eval` (`decision_evaluation_id`) COMMENT '支持访问路径：decision_evaluation_id',
  COMMENT = '保存一个尽调案件形成的不可变角色资格决定版本';
