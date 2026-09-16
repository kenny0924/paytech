-- REVIEW ONLY. Target table definitions; NOT a migration.
-- Do not execute against an existing database. Missing unchanged tables are intentional.

CREATE TABLE `t_due_diligence_case` (
  `case_id` BIGINT NOT NULL COMMENT '尽调案件的稳定业务标识。',
  `case_no` VARCHAR(64) NOT NULL COMMENT '案件编号的业务值。',
  `role_type` VARCHAR(64) NOT NULL COMMENT '角色类型的稳定业务代码。允许值：CLIENT、LOCAL_PARTNER、OPERATING_ENTITY、RELATED_PERSON。',
  `role_id` BIGINT NOT NULL COMMENT '角色的稳定业务标识。',
  `qualification_type` VARCHAR(64) NOT NULL COMMENT '资格类型的稳定业务代码。允许值：INDIVIDUAL_KYC、ORGANIZATION_KYB、PARTNER_DUE_DILIGENCE、ENTITY_UNDERWRITING、RELATED_PERSON_KYC。',
  `dd_program_version_id` BIGINT NOT NULL COMMENT '尽调程序版本的稳定业务标识。',
  `case_origin` VARCHAR(64) NOT NULL COMMENT '案件来源类型的稳定业务代码。允许值：APPLICATION、CONTINUOUS_REVIEW、MANUAL。',
  `scheduled_review_time` DATETIME(6) NULL COMMENT '计划复审时间对应的 UTC 微秒时间。',
  `source_namespace` VARCHAR(64) NOT NULL COMMENT '区分 CCT Client Application、CCT Operating Entity Application、CCT Local Partner Application 与 Risk 本地来源的稳定命名空间。允许值：CCT_CLIENT_APPLICATION、CCT_OPERATING_ENTITY_APPLICATION、CCT_LOCAL_PARTNER_APPLICATION、RISK_COMPLIANCE。',
  `source_application_id` BIGINT NULL COMMENT '来源申请的稳定业务标识。',
  `source_submission_version` BIGINT NULL COMMENT '仅 APPLICATION 保存的 CCT Application Submission 正整数版本；其他 Case Origin 必须为 NULL。',
  `source_submission_hash` CHAR(64) NULL COMMENT '仅 APPLICATION 保存的 CCT Application Submission Canonical Hash；用于同来源键重试的同 Hash 幂等和异 Hash 冲突。',
  `status` VARCHAR(64) NOT NULL COMMENT '尽调案件状态沿用既有处理状态；新 Client 退回时以 RETURNED 终止本次任务，不形成最终拒绝决定。',
  `active_slot` BOOLEAN NULL COMMENT '进行中状态时为 1，终态必须为 NULL；禁止写入 0。',
  `submitted_time` DATETIME(6) NULL COMMENT '提交时间对应的 UTC 微秒时间。',
  `decision_time` DATETIME(6) NULL COMMENT '决定时间对应的 UTC 微秒时间。',
  `version` BIGINT NOT NULL COMMENT '案件并发版本的长整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  `source_submission_id` VARCHAR(64) NULL COMMENT 'Client 新入驻为提交命令 UUID；既有 APPLICATION 来源使用 legacy:application_id 稳定迁移键；定时复审为空。',
  `public_note` VARCHAR(1000) NULL COMMENT '运营确认的客户可见审核说明；最多 1000 字符，不自动复制内部理由。',
  `resolution_note` VARCHAR(2000) NULL COMMENT '本任务终结的内部审核依据说明；只允许授权 Operations 查询。',
  `return_section` VARCHAR(32) NULL COMMENT '退回定位分区：BASIC、BUSINESS、PEOPLE、MATERIALS；非退回为空。',
  PRIMARY KEY (`case_id`),
  UNIQUE KEY `uk_dd_case_no` (`case_no`) COMMENT '保证唯一性：case_no',
  UNIQUE KEY `uk_dd_case_submission` (`source_namespace`,`source_application_id`,`source_submission_id`,`role_type`,`role_id`,`qualification_type`) COMMENT '保证唯一性：source_namespace,source_application_id,source_submission_id,role_type,role_id,qualification_type',
  UNIQUE KEY `uk_dd_case_active` (`role_type`,`role_id`,`qualification_type`,`active_slot`) COMMENT '保证唯一性：role_type,role_id,qualification_type,active_slot',
  UNIQUE KEY `uk_dd_case_review` (`role_type`,`role_id`,`qualification_type`,`scheduled_review_time`) COMMENT '保证唯一性：role_type,role_id,qualification_type,scheduled_review_time',
  KEY `idx_dd_case_prog` (`dd_program_version_id`) COMMENT '支持查询：dd_program_version_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Client 入驻简化目标：尽调案件；仅供评审，非已部署结构。';

CREATE TABLE `t_dd_case_subject` (
  `case_subject_id` BIGINT NOT NULL COMMENT '案件主体范围的稳定业务标识。',
  `case_id` BIGINT NOT NULL COMMENT '尽调案件的稳定业务标识。',
  `party_id` BIGINT NOT NULL COMMENT 'Party 主体的稳定业务标识。',
  `subject_role` VARCHAR(64) NOT NULL COMMENT '主体角色的稳定业务代码。允许值：PRIMARY、UBO、DIRECTOR、SIGNATORY。',
  `primary_slot` BOOLEAN NULL COMMENT 'PRIMARY 主体时为 1，关联主体必须为 NULL；禁止写入 0。',
  `source_profile_id` BIGINT NOT NULL COMMENT '来源档案的稳定业务标识。',
  `source_profile_version_no` BIGINT NULL COMMENT '案件创建时冻结的 CCT Party Profile version_no 正整数坐标。',
  `source_relation_version_id` BIGINT NULL COMMENT '关联主体对应的 CCT Party Relation Version 稳定标识；PRIMARY 必须为 NULL。',
  `source_relation_version_no` BIGINT NULL COMMENT '关联主体对应的 CCT Party Relation Version version_no 正整数坐标；PRIMARY 必须为 NULL。',
  `ownership_percentage` DECIMAL(18,10) NULL COMMENT 'UBO 来源关系在 CCT 中冻结的持股百分比，取值大于 0 且不超过 100；其他主体角色必须为 NULL。',
  `required_flag` BOOLEAN NOT NULL COMMENT '是否必需，1 表示是，0 表示否。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `source_relation_id` BIGINT NULL COMMENT '新 Client 入驻的稳定 CCT Party Relation ID；旧版本关系来源为空。',
  PRIMARY KEY (`case_subject_id`),
  UNIQUE KEY `uk_dd_case_subject` (`case_id`,`party_id`,`subject_role`) COMMENT '保证唯一性：case_id,party_id,subject_role',
  UNIQUE KEY `uk_dd_case_primary` (`case_id`,`primary_slot`) COMMENT '保证唯一性：case_id,primary_slot',
  UNIQUE KEY `uk_dd_subject_relation` (`case_id`,`source_relation_version_id`) COMMENT '保证唯一性：case_id,source_relation_version_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Client 入驻简化目标：尽调案件主体；仅供评审，非已部署结构。';

CREATE TABLE `t_dd_case_classification` (
  `case_classification_id` BIGINT NOT NULL COMMENT '本任务批准经营分类的稳定行标识。',
  `case_id` BIGINT NOT NULL COMMENT '拥有该分类审核事实的 Risk 主任务 ID。',
  `industry_code` VARCHAR(64) NOT NULL COMMENT 'CCT 运营确认的行业代码。',
  `mcc_code` VARCHAR(4) NOT NULL COMMENT 'CCT 运营确认的四位 MCC 字符串。',
  `primary_slot` TINYINT(1) NULL COMMENT '主要分类为 1，其他为空；同一任务恰好一个主要分类。',
  `confirmed_by` BIGINT NOT NULL COMMENT '确认分类的 Operations Account 外部稳定 ID。',
  `confirmed_time` DATETIME(6) NOT NULL COMMENT '分类确认的 UTC 时间；任务内不可变。',
  `create_time` DATETIME(6) NOT NULL COMMENT '本行创建 UTC 时间。',
  PRIMARY KEY (`case_classification_id`),
  UNIQUE KEY `uk_case_classification_pair` (`case_id`,`industry_code`,`mcc_code`) COMMENT '保证唯一性：case_id,industry_code,mcc_code',
  UNIQUE KEY `uk_case_classification_primary` (`case_id`,`primary_slot`) COMMENT '保证唯一性：case_id,primary_slot'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Client 入驻简化目标：审核任务经营分类事实；仅供评审，非已部署结构。';

CREATE TABLE `t_qualification_decision` (
  `decision_id` BIGINT NOT NULL COMMENT '资格决定的稳定业务标识。',
  `case_id` BIGINT NOT NULL COMMENT '尽调案件的稳定业务标识。',
  `decision_version` INT NULL COMMENT '既有资格修订合同的决定版本；新 Client 入驻每任务仅一项终态决定，使用决定 ID，版本为空；由 Case 锁防止第二项决定。',
  `role_type` VARCHAR(64) NOT NULL COMMENT '角色类型的稳定业务代码。允许值：CLIENT、LOCAL_PARTNER、OPERATING_ENTITY、RELATED_PERSON。',
  `role_id` BIGINT NOT NULL COMMENT '角色的稳定业务标识。',
  `qualification_type` VARCHAR(64) NOT NULL COMMENT '资格类型的稳定业务代码。允许值：INDIVIDUAL_KYC、ORGANIZATION_KYB、PARTNER_DUE_DILIGENCE、ENTITY_UNDERWRITING、RELATED_PERSON_KYC。',
  `decision_evaluation_id` BIGINT NULL COMMENT '规则评估的稳定业务标识。',
  `decision_source` VARCHAR(64) NOT NULL COMMENT '决定来源的稳定业务代码。允许值：AUTOMATED、REVIEWER、OVERRIDE。',
  `recommended_decision` VARCHAR(64) NULL COMMENT '建议结论的稳定业务代码。允许值：APPROVE、REVIEW、REJECT。',
  `outcome` VARCHAR(64) NOT NULL COMMENT '资格结论的稳定业务代码。允许值：APPROVED、REJECTED、REVOKED、EXPIRED。',
  `override_reason_code` VARCHAR(64) NULL COMMENT '覆盖原因的稳定业务代码。取值来源：Risk 资格覆盖原因目录；非固定枚举。',
  `valid_from` DATETIME(6) NOT NULL COMMENT '有效开始时间对应的 UTC 微秒时间。',
  `valid_to` DATETIME(6) NULL COMMENT '有效结束时间对应的 UTC 微秒时间。',
  `decided_by` BIGINT NULL COMMENT '决定人的稳定业务标识。',
  `decided_time` DATETIME(6) NOT NULL COMMENT '决定时间对应的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`decision_id`),
  UNIQUE KEY `uk_qual_decision_ver` (`case_id`,`decision_version`) COMMENT '保证唯一性：case_id,decision_version',
  KEY `idx_qual_dec_eval` (`decision_evaluation_id`) COMMENT '支持查询：decision_evaluation_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Client 入驻简化目标：资格决定；仅供评审，非已部署结构。';
