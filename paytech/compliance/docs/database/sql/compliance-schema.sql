-- Code-MoR Compliance schema: verbatim slice of the approved Risk & Compliance schema by table ownership (design/03 §2.2).
-- 只适用于人工审核后的空 mor_compliance 数据库；不得作为应用启动迁移或已有数据库升级脚本。
-- Source: paytech/compliance/docs/database/compliance.pdma.json

CREATE TABLE `t_dd_program` (
  `program_id` BIGINT NOT NULL COMMENT '尽调程序的稳定业务标识。',
  `program_code` VARCHAR(64) NOT NULL COMMENT '程序编码的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `qualification_type` VARCHAR(64) NOT NULL COMMENT '资格类型的稳定业务代码。允许值：INDIVIDUAL_KYC、ORGANIZATION_KYB、PARTNER_DUE_DILIGENCE、ENTITY_UNDERWRITING、RELATED_PERSON_KYC。',
  `status` VARCHAR(64) NOT NULL COMMENT '程序状态的稳定业务代码。允许值：DRAFT、ACTIVE、RETIRED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`program_id`),
  UNIQUE KEY `uk_dd_program_code` (`program_code`) COMMENT '保证业务唯一性：program_code'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存角色资格尽调程序的稳定身份和适用资格类型';

CREATE TABLE `t_dd_program_version` (
  `program_version_id` BIGINT NOT NULL COMMENT '尽调程序版本的稳定业务标识。',
  `program_id` BIGINT NOT NULL COMMENT '尽调程序的稳定业务标识。',
  `version_no` INT NOT NULL COMMENT '版本号的整数值。',
  `review_frequency_code` VARCHAR(64) NULL COMMENT '复审频率的稳定业务代码。取值来源：Risk 持续复审频率目录；非固定枚举。',
  `status` VARCHAR(64) NOT NULL COMMENT '版本状态的稳定业务代码。允许值：DRAFT、ACTIVE、RETIRED。',
  `active_slot` BOOLEAN NULL COMMENT 'ACTIVE 时为 1，其他状态必须为 NULL；禁止写入 0。',
  `content_hash` CHAR(64) NOT NULL COMMENT '按 CODE-MOR:RISK:DD_PROGRAM_VERSION:1 Canonical Schema 对复审配置及材料、检查和筛查模板有序重算的 SHA-256 小写十六进制值。',
  `approved_by` BIGINT NULL COMMENT '审核人的稳定业务标识。',
  `approved_time` DATETIME(6) NULL COMMENT '审核时间对应的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`program_version_id`),
  UNIQUE KEY `uk_dd_prog_ver` (`program_id`, `version_no`) COMMENT '保证业务唯一性：program_id、version_no',
  UNIQUE KEY `uk_dd_prog_active` (`program_id`, `active_slot`) COMMENT '保证业务唯一性：program_id、active_slot'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存经审核冻结并可被案件精确引用的尽调程序版本';

CREATE TABLE `t_dd_program_requirement` (
  `program_requirement_id` BIGINT NOT NULL COMMENT '程序材料模板的稳定业务标识。',
  `program_version_id` BIGINT NOT NULL COMMENT '程序版本的稳定业务标识。',
  `requirement_code` VARCHAR(64) NOT NULL COMMENT '材料要求编码的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `document_type` VARCHAR(64) NOT NULL COMMENT '文件类型的稳定业务代码。取值来源：Platform 文件类型目录；非固定枚举。',
  `required_flag` BOOLEAN NOT NULL COMMENT '是否必需，1 表示是，0 表示否。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`program_requirement_id`),
  UNIQUE KEY `uk_dd_prog_req` (`program_version_id`, `requirement_code`) COMMENT '保证业务唯一性：program_version_id、requirement_code'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定义一个尽调程序版本要求收集的材料';

CREATE TABLE `t_dd_program_check` (
  `program_check_id` BIGINT NOT NULL COMMENT '程序检查模板的稳定业务标识。',
  `program_version_id` BIGINT NOT NULL COMMENT '程序版本的稳定业务标识。',
  `check_code` VARCHAR(64) NOT NULL COMMENT '检查编码的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `check_type` VARCHAR(64) NOT NULL COMMENT '检查类型的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `required_flag` BOOLEAN NOT NULL COMMENT '是否必需，1 表示是，0 表示否。',
  `validity_days` INT NULL COMMENT '提供方检查结果的最长有效天数；非空时必须为正，valid_until 不得晚于 performed_time 加该天数。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`program_check_id`),
  UNIQUE KEY `uk_dd_prog_check` (`program_version_id`, `check_code`) COMMENT '保证业务唯一性：program_version_id、check_code'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定义一个尽调程序版本要求执行的结构化身份或业务检查';

CREATE TABLE `t_dd_program_screening` (
  `program_screening_id` BIGINT NOT NULL COMMENT '程序筛查模板的稳定业务标识。',
  `program_version_id` BIGINT NOT NULL COMMENT '程序版本的稳定业务标识。',
  `subject_role` VARCHAR(64) NOT NULL COMMENT '主体角色的稳定业务代码。允许值：PRIMARY、UBO、DIRECTOR、SIGNATORY。',
  `screening_type` VARCHAR(64) NOT NULL COMMENT '筛查类型的稳定业务代码。允许值：SANCTIONS、PEP、ADVERSE_MEDIA。',
  `required_flag` BOOLEAN NOT NULL COMMENT '是否必需，1 表示是，0 表示否。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`program_screening_id`),
  UNIQUE KEY `uk_dd_prog_screen` (`program_version_id`, `subject_role`, `screening_type`) COMMENT '保证业务唯一性：program_version_id、subject_role、screening_type'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定义一个尽调程序版本要求执行的制裁、PEP 或负面信息筛查';

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
  `source_submission_id` VARCHAR(64) NULL COMMENT 'Client 每次真实提交的 UUID；旧 APPLICATION 来源适配为准确 legacy 标识；同次重试复用。',
  `public_note` VARCHAR(1000) NULL COMMENT '运营单独确认的安全退回或决定说明，不复制内部审核理由。',
  `resolution_note` VARCHAR(2000) NULL COMMENT '本任务结束的内部审核理由，禁止直接展示给 Client。',
  `return_section` VARCHAR(32) NULL COMMENT '退回定位到单页的资料分区。允许值：BASIC、BUSINESS、PEOPLE、MATERIALS。',
  `source_submission_version` BIGINT NULL COMMENT '未迁移 APPLICATION 来源的正整数版本；新 Client 使用 source_submission_id，本列为空。',
  `source_submission_hash` CHAR(64) NULL COMMENT '未迁移 APPLICATION 来源的提交摘要；新 Client 不填本列，以准确提交身份及 Owner 幂等记录校验重放。',
  `status` VARCHAR(64) NOT NULL COMMENT '案件状态的稳定业务代码。允许值：DRAFT、SUBMITTED、IN_REVIEW、WAITING_EVIDENCE、RETURNED、APPROVED、REJECTED、WITHDRAWN、EXPIRED。',
  `active_slot` BOOLEAN NULL COMMENT '进行中状态时为 1，终态必须为 NULL；禁止写入 0。',
  `submitted_time` DATETIME(6) NULL COMMENT '提交时间对应的 UTC 微秒时间。',
  `decision_time` DATETIME(6) NULL COMMENT '决定时间对应的 UTC 微秒时间。',
  `version` BIGINT NOT NULL COMMENT '案件并发版本的长整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`case_id`),
  UNIQUE KEY `uk_dd_case_no` (`case_no`) COMMENT '保证业务唯一性：case_no',
  UNIQUE KEY `uk_dd_case_source` (`source_namespace`, `source_application_id`, `source_submission_id`, `role_type`, `role_id`, `qualification_type`) COMMENT '保证业务唯一性：source_namespace、source_application_id、source_submission_id、role_type、role_id、qualification_type',
  UNIQUE KEY `uk_dd_case_active` (`role_type`, `role_id`, `qualification_type`, `active_slot`) COMMENT '保证业务唯一性：role_type、role_id、qualification_type、active_slot',
  UNIQUE KEY `uk_dd_case_review` (`role_type`, `role_id`, `qualification_type`, `scheduled_review_time`) COMMENT '保证业务唯一性：role_type、role_id、qualification_type、scheduled_review_time',
  KEY `idx_dd_case_prog` (`dd_program_version_id`) COMMENT '支持访问路径：dd_program_version_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存一次角色化 KYC、KYB、合作伙伴尽调或经营主体承保案件';

CREATE TABLE `t_dd_case_subject` (
  `case_subject_id` BIGINT NOT NULL COMMENT '案件主体范围的稳定业务标识。',
  `case_id` BIGINT NOT NULL COMMENT '尽调案件的稳定业务标识。',
  `party_id` BIGINT NOT NULL COMMENT 'Party 主体的稳定业务标识。',
  `subject_role` VARCHAR(64) NOT NULL COMMENT '主体角色的稳定业务代码。允许值：PRIMARY、UBO、DIRECTOR、SIGNATORY。',
  `primary_slot` BOOLEAN NULL COMMENT 'PRIMARY 主体时为 1，关联主体必须为 NULL；禁止写入 0。',
  `source_profile_id` BIGINT NOT NULL COMMENT '来源档案的稳定业务标识。',
  `source_relation_id` BIGINT NULL COMMENT '来源稳定关系的稳定业务标识。',
  `source_profile_version_no` BIGINT NULL COMMENT '旧来源保留真实 Profile 版本；新 Client 使用稳定 Profile ID，本列为空，禁止伪造版本。',
  `source_relation_version_id` BIGINT NULL COMMENT '关联主体对应的 CCT Party Relation Version 稳定标识；PRIMARY 必须为 NULL。',
  `source_relation_version_no` BIGINT NULL COMMENT '关联主体对应的 CCT Party Relation Version version_no 正整数坐标；PRIMARY 必须为 NULL。',
  `ownership_percentage` DECIMAL(18,10) NULL COMMENT 'UBO 来源关系在 CCT 中冻结的持股百分比，取值大于 0 且不超过 100；其他主体角色必须为 NULL。',
  `required_flag` BOOLEAN NOT NULL COMMENT '是否必需，1 表示是，0 表示否。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`case_subject_id`),
  UNIQUE KEY `uk_dd_case_subject` (`case_id`, `party_id`, `subject_role`) COMMENT '保证业务唯一性：case_id、party_id、subject_role',
  UNIQUE KEY `uk_dd_case_primary` (`case_id`, `primary_slot`) COMMENT '保证业务唯一性：case_id、primary_slot',
  UNIQUE KEY `uk_dd_subject_relation` (`case_id`, `source_relation_version_id`) COMMENT '保证业务唯一性：case_id、source_relation_version_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冻结尽调案件覆盖的 Party、角色、来源 Profile 坐标及关联主体关系事实';

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

CREATE TABLE `t_dd_case_dependency` (
  `dependency_id` BIGINT NOT NULL COMMENT '案件依赖的稳定业务标识。',
  `parent_case_id` BIGINT NOT NULL COMMENT '父案件的稳定业务标识。',
  `child_case_id` BIGINT NOT NULL COMMENT '子案件的稳定业务标识。',
  `dependency_type` VARCHAR(64) NOT NULL COMMENT '依赖类型的稳定业务代码。允许值：UBO_KYC、DIRECTOR_KYC、SIGNATORY_KYC。',
  `required_flag` BOOLEAN NOT NULL COMMENT '是否必需，1 表示是，0 表示否。',
  `accepted_decision_id` BIGINT NULL COMMENT '已接受决定的稳定业务标识。',
  `version` BIGINT NOT NULL COMMENT '依赖并发版本的长整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`dependency_id`),
  UNIQUE KEY `uk_dd_case_dep` (`parent_case_id`, `child_case_id`, `dependency_type`) COMMENT '保证业务唯一性：parent_case_id、child_case_id、dependency_type',
  KEY `idx_dd_dep_child` (`child_case_id`) COMMENT '支持访问路径：child_case_id',
  KEY `idx_dd_dep_decision` (`accepted_decision_id`) COMMENT '支持访问路径：accepted_decision_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存组织案件对关联自然人 KYC 案件的明确依赖';

CREATE TABLE `t_dd_party_snapshot` (
  `snapshot_id` BIGINT NOT NULL COMMENT '尽调主体快照的稳定业务标识。',
  `case_subject_id` BIGINT NOT NULL COMMENT '案件主体的稳定业务标识。',
  `schema_version` INT NOT NULL COMMENT 'Risk 在 Protect 前校验的 RISK_DD_PARTY_SNAPSHOT_V1 Payload Schema 正整数版本。',
  `source_content_hash` CHAR(64) NOT NULL COMMENT '按快照 Schema 校验并排序后的规范化来源 Payload SHA-256 小写十六进制值。',
  `snapshot_vault_token` VARCHAR(128) NOT NULL COMMENT '用于案件审核授权 Reveal 的 RISK_DD_PARTY_SNAPSHOT_V1 Vault Token；仅允许 COMPLIANCE_REVIEW Purpose。',
  `captured_time` DATETIME(6) NOT NULL COMMENT '来源快照采集完成的 UTC 微秒时间，可早于本库不可变引用的创建时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`snapshot_id`),
  UNIQUE KEY `uk_dd_party_snap` (`case_subject_id`) COMMENT '保证业务唯一性：case_subject_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存案件时点 Party 与 Profile 的受控不可变合规快照引用';

CREATE TABLE `t_dd_requirement` (
  `requirement_id` BIGINT NOT NULL COMMENT '案件材料要求的稳定业务标识。',
  `case_id` BIGINT NOT NULL COMMENT '尽调案件的稳定业务标识。',
  `program_requirement_id` BIGINT NOT NULL COMMENT '程序材料模板的稳定业务标识。',
  `status` VARCHAR(64) NOT NULL COMMENT '要求状态的稳定业务代码。允许值：PENDING、RECEIVED、ACCEPTED、REJECTED、WAIVED、EXPIRED。',
  `due_time` DATETIME(6) NULL COMMENT '截止时间对应的 UTC 微秒时间。',
  `version` BIGINT NOT NULL COMMENT '要求并发版本的长整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`requirement_id`),
  UNIQUE KEY `uk_dd_req_case` (`case_id`, `program_requirement_id`) COMMENT '保证业务唯一性：case_id、program_requirement_id',
  KEY `idx_dd_req_template` (`program_requirement_id`) COMMENT '支持访问路径：program_requirement_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存由尽调程序实例化到具体案件的材料要求和办理状态';

CREATE TABLE `t_dd_evidence_ref` (
  `evidence_id` BIGINT NOT NULL COMMENT '尽调证据引用的稳定业务标识。',
  `requirement_id` BIGINT NOT NULL COMMENT '材料要求的稳定业务标识。',
  `case_subject_id` BIGINT NOT NULL COMMENT '案件主体的稳定业务标识。',
  `file_id` BIGINT NOT NULL COMMENT 'Platform 文件的稳定业务标识。',
  `content_hash` CHAR(64) NOT NULL COMMENT '文件内容哈希的 SHA-256 小写十六进制值。',
  `status` VARCHAR(64) NOT NULL COMMENT '证据状态的稳定业务代码。允许值：RECEIVED、ACCEPTED、REJECTED、SUPERSEDED。',
  `version` BIGINT NOT NULL COMMENT '证据并发版本的长整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`evidence_id`),
  UNIQUE KEY `uk_dd_evidence_req_file` (`requirement_id`, `file_id`) COMMENT '保证业务唯一性：requirement_id、file_id',
  KEY `idx_dd_evidence_subject` (`case_subject_id`) COMMENT '支持访问路径：case_subject_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存案件主体在材料要求下提交的 Platform 文件引用及审核状态';

CREATE TABLE `t_dd_check` (
  `check_id` BIGINT NOT NULL COMMENT '尽调检查结果的稳定业务标识。',
  `case_subject_id` BIGINT NOT NULL COMMENT '案件主体的稳定业务标识。',
  `program_check_id` BIGINT NOT NULL COMMENT '程序检查模板的稳定业务标识。',
  `provider_code` VARCHAR(64) NOT NULL COMMENT '检查提供方的稳定业务代码。取值来源：Risk 检查提供方目录；非固定枚举。',
  `request_ref` VARCHAR(128) NOT NULL COMMENT '提供方请求引用的业务值。',
  `result_code` VARCHAR(64) NOT NULL COMMENT '检查结果代码的稳定业务代码。取值来源：Risk 检查结果目录；非固定枚举。',
  `result_vault_token` VARCHAR(128) NULL COMMENT '仅在审核需要恢复提供方明细时保存 RISK_DD_CHECK_RESULT_V1 Vault Token；只允许 COMPLIANCE_REVIEW Purpose。',
  `performed_time` DATETIME(6) NOT NULL COMMENT '执行时间对应的 UTC 微秒时间。',
  `valid_until` DATETIME(6) NULL COMMENT '有效截止时间对应的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`check_id`),
  UNIQUE KEY `uk_dd_check_request` (`provider_code`, `request_ref`) COMMENT '保证业务唯一性：provider_code、request_ref',
  KEY `idx_dd_check_subject` (`case_subject_id`) COMMENT '支持访问路径：case_subject_id',
  KEY `idx_dd_check_program` (`program_check_id`) COMMENT '支持访问路径：program_check_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存一次身份、注册、地址或业务检查的不可变提供方结果引用';

CREATE TABLE `t_screening_run` (
  `screening_run_id` BIGINT NOT NULL COMMENT '筛查执行的稳定业务标识。',
  `case_subject_id` BIGINT NOT NULL COMMENT '案件主体的稳定业务标识。',
  `program_screening_id` BIGINT NOT NULL COMMENT '程序筛查模板的稳定业务标识。',
  `provider_code` VARCHAR(64) NOT NULL COMMENT '筛查提供方的稳定业务代码。取值来源：Risk 筛查提供方目录；非固定枚举。',
  `request_ref` VARCHAR(128) NOT NULL COMMENT '提供方请求引用的业务值。',
  `status` VARCHAR(64) NOT NULL COMMENT '筛查状态的稳定业务代码。允许值：STARTED、SUCCEEDED、FAILED、TIMED_OUT。',
  `started_time` DATETIME(6) NOT NULL COMMENT '开始时间对应的 UTC 微秒时间。',
  `completed_time` DATETIME(6) NULL COMMENT '完成时间对应的 UTC 微秒时间。',
  `version` BIGINT NOT NULL COMMENT '筛查执行并发版本的长整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`screening_run_id`),
  UNIQUE KEY `uk_screen_request` (`provider_code`, `request_ref`) COMMENT '保证业务唯一性：provider_code、request_ref',
  KEY `idx_screen_subject` (`case_subject_id`) COMMENT '支持访问路径：case_subject_id',
  KEY `idx_screen_program` (`program_screening_id`) COMMENT '支持访问路径：program_screening_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存制裁、PEP 或负面信息筛查的一次提供方执行生命周期';

CREATE TABLE `t_screening_hit` (
  `hit_id` BIGINT NOT NULL COMMENT '筛查命中的稳定业务标识。',
  `screening_run_id` BIGINT NOT NULL COMMENT '筛查执行的稳定业务标识。',
  `external_hit_ref` VARCHAR(128) NOT NULL COMMENT '外部命中引用的业务值。',
  `match_type` VARCHAR(64) NOT NULL COMMENT '匹配类型的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `match_score` DECIMAL(18,10) NOT NULL COMMENT '匹配分数，取值范围和业务口径由所属契约限定。',
  `status` VARCHAR(64) NOT NULL COMMENT '命中状态的稳定业务代码。允许值：OPEN、RESOLVED。',
  `name_vault_token` VARCHAR(128) NULL COMMENT '仅在命中审核需要恢复姓名时保存 RISK_SCREENING_HIT_NAME_V1 Vault Token；只允许 COMPLIANCE_REVIEW Purpose。',
  `version` BIGINT NOT NULL COMMENT '命中并发版本的长整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`hit_id`),
  UNIQUE KEY `uk_screen_hit_ref` (`screening_run_id`, `external_hit_ref`) COMMENT '保证业务唯一性：screening_run_id、external_hit_ref'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存一次筛查返回的外部命中及其当前处置状态';

CREATE TABLE `t_screening_resolution` (
  `resolution_id` BIGINT NOT NULL COMMENT '筛查命中处置的稳定业务标识。',
  `hit_id` BIGINT NOT NULL COMMENT '筛查命中的稳定业务标识。',
  `resolution_version` INT NOT NULL COMMENT '处置版本的整数值。',
  `resolution` VARCHAR(64) NOT NULL COMMENT '处置结论的稳定业务代码。允许值：CONFIRMED_MATCH、FALSE_POSITIVE、INSUFFICIENT_EVIDENCE。',
  `reason_code` VARCHAR(64) NOT NULL COMMENT '处置原因的稳定业务代码。取值来源：Risk 筛查处置原因目录；非固定枚举。',
  `reviewer_id` BIGINT NOT NULL COMMENT '审核人的稳定业务标识。',
  `resolved_time` DATETIME(6) NOT NULL COMMENT '处置时间对应的 UTC 微秒时间。',
  `evidence_file_id` BIGINT NULL COMMENT '证据文件的稳定业务标识。',
  `evidence_content_hash` CHAR(64) NULL COMMENT '证据内容哈希的 SHA-256 小写十六进制值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`resolution_id`),
  UNIQUE KEY `uk_hit_resolution` (`hit_id`, `resolution_version`) COMMENT '保证业务唯一性：hit_id、resolution_version'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存筛查命中每一次不可变人工处置和可选证据文件';

CREATE TABLE `t_dd_case_state_change` (
  `change_id` BIGINT NOT NULL COMMENT '尽调案件状态变更的稳定业务标识。',
  `case_id` BIGINT NOT NULL COMMENT '尽调案件的稳定业务标识。',
  `from_status` VARCHAR(64) NULL COMMENT '原状态的稳定业务代码。允许值：DRAFT、SUBMITTED、IN_REVIEW、WAITING_EVIDENCE、APPROVED、REJECTED、WITHDRAWN、EXPIRED。',
  `to_status` VARCHAR(64) NOT NULL COMMENT '新状态的稳定业务代码。允许值：DRAFT、SUBMITTED、IN_REVIEW、WAITING_EVIDENCE、APPROVED、REJECTED、WITHDRAWN、EXPIRED。',
  `action_type` VARCHAR(64) NOT NULL COMMENT '迁移动作的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `reason_code` VARCHAR(64) NULL COMMENT '迁移原因的稳定业务代码。取值来源：Risk 案件状态原因目录；非固定枚举。',
  `trigger_type` VARCHAR(64) NOT NULL COMMENT '触发类型的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `trigger_id` VARCHAR(128) NOT NULL COMMENT '触发标识的业务值。',
  `actor_id` BIGINT NULL COMMENT '操作人的稳定业务标识。',
  `aggregate_version` BIGINT NOT NULL COMMENT '聚合版本的长整数值。',
  `occurred_time` DATETIME(6) NOT NULL COMMENT '发生时间对应的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`change_id`),
  UNIQUE KEY `uk_dd_case_change` (`case_id`, `aggregate_version`) COMMENT '保证业务唯一性：case_id、aggregate_version'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='追加保存尽调案件每次状态迁移、触发来源和聚合版本';

CREATE TABLE `t_qualification_decision` (
  `decision_id` BIGINT NOT NULL COMMENT '资格决定的稳定业务标识。',
  `case_id` BIGINT NULL COMMENT '尽调案件的稳定业务标识；运营手工登记或撤销（decision_source=MANUAL）的决定不关联案件，为空。',
  `decision_version` INT NULL COMMENT '旧资格修订合同的真实版本；新 Client 每个 Case 只有一个最终决定，使用 Decision ID，本列为空。',
  `role_type` VARCHAR(64) NOT NULL COMMENT '角色类型的稳定业务代码。允许值：CLIENT、LOCAL_PARTNER、OPERATING_ENTITY、RELATED_PERSON。',
  `role_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '角色的稳定业务标识：OPERATING_ENTITY 存 entity_no（两位大写字母加三位数字，如 AE001），CLIENT、LOCAL_PARTNER、RELATED_PERSON 存十进制 ID 文本。',
  `qualification_type` VARCHAR(64) NOT NULL COMMENT '资格类型的稳定业务代码。允许值：INDIVIDUAL_KYC、ORGANIZATION_KYB、PARTNER_DUE_DILIGENCE、ENTITY_UNDERWRITING、RELATED_PERSON_KYC。',
  `decision_evaluation_id` BIGINT NULL COMMENT '规则评估的稳定业务标识。',
  `decision_source` VARCHAR(64) NOT NULL COMMENT '决定来源的稳定业务代码。允许值：AUTOMATED、REVIEWER、OVERRIDE、MANUAL。',
  `recommended_decision` VARCHAR(64) NULL COMMENT '建议结论的稳定业务代码。允许值：APPROVE、REVIEW、REJECT。',
  `outcome` VARCHAR(64) NOT NULL COMMENT '资格结论的稳定业务代码。允许值：APPROVED、REJECTED、REVOKED、EXPIRED。',
  `override_reason_code` VARCHAR(64) NULL COMMENT '覆盖原因的稳定业务代码。取值来源：Risk 资格覆盖原因目录；非固定枚举。',
  `basis_note` VARCHAR(1000) NULL COMMENT '运营手工登记或撤销资格的依据说明，最多 1000 字符；仅 MANUAL 决定填写，其他来源为空。',
  `valid_from` DATETIME(6) NOT NULL COMMENT '有效开始时间对应的 UTC 微秒时间。',
  `valid_to` DATETIME(6) NULL COMMENT '有效结束时间对应的 UTC 微秒时间。',
  `decided_by` BIGINT NULL COMMENT '决定人的稳定业务标识。',
  `decided_time` DATETIME(6) NOT NULL COMMENT '决定时间对应的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`decision_id`),
  UNIQUE KEY `uk_qual_decision_ver` (`case_id`, `decision_version`) COMMENT '保证业务唯一性：case_id、decision_version',
  KEY `idx_qual_dec_eval` (`decision_evaluation_id`) COMMENT '支持访问路径：decision_evaluation_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存一个尽调案件形成的不可变角色资格决定版本';

CREATE TABLE `t_role_qualification` (
  `qualification_id` BIGINT NOT NULL COMMENT '角色资格投影的稳定业务标识。',
  `role_type` VARCHAR(64) NOT NULL COMMENT '角色类型的稳定业务代码。允许值：CLIENT、LOCAL_PARTNER、OPERATING_ENTITY、RELATED_PERSON。',
  `role_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '角色的稳定业务标识：OPERATING_ENTITY 存 entity_no（两位大写字母加三位数字，如 AE001），CLIENT、LOCAL_PARTNER、RELATED_PERSON 存十进制 ID 文本。',
  `qualification_type` VARCHAR(64) NOT NULL COMMENT '资格类型的稳定业务代码。允许值：INDIVIDUAL_KYC、ORGANIZATION_KYB、PARTNER_DUE_DILIGENCE、ENTITY_UNDERWRITING、RELATED_PERSON_KYC。',
  `current_decision_id` BIGINT NOT NULL COMMENT '当前资格决定的稳定业务标识。',
  `status` VARCHAR(64) NOT NULL COMMENT '资格状态的稳定业务代码。允许值：APPROVED、REJECTED、REVOKED、EXPIRED。',
  `valid_from` DATETIME(6) NOT NULL COMMENT '有效开始时间对应的 UTC 微秒时间。',
  `valid_to` DATETIME(6) NULL COMMENT '有效结束时间对应的 UTC 微秒时间。',
  `review_frequency_code` VARCHAR(64) NULL COMMENT '复审频率的稳定业务代码。取值来源：Risk 持续复审频率目录；非固定枚举。',
  `next_review_time` DATETIME(6) NULL COMMENT '下次复审时间对应的 UTC 微秒时间。',
  `review_status` VARCHAR(64) NOT NULL COMMENT '复审状态的稳定业务代码。允许值：NOT_REQUIRED、ACTIVE、PAUSED、COMPLETED。',
  `version` BIGINT NOT NULL COMMENT '资格并发版本的长整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`qualification_id`),
  UNIQUE KEY `uk_role_qualification` (`role_type`, `role_id`, `qualification_type`) COMMENT '保证业务唯一性：role_type、role_id、qualification_type',
  UNIQUE KEY `uk_role_qual_dec` (`current_decision_id`) COMMENT '保证业务唯一性：current_decision_id',
  KEY `idx_role_qual_review` (`review_status`, `next_review_time`) COMMENT '支持访问路径：review_status、next_review_time'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存每个角色和资格类型当前生效决定、有效期及持续复审调度';

CREATE TABLE `t_qualification_state_change` (
  `change_id` BIGINT NOT NULL COMMENT '资格状态变更的稳定业务标识。',
  `qualification_id` BIGINT NOT NULL COMMENT '角色资格的稳定业务标识。',
  `from_status` VARCHAR(64) NULL COMMENT '原状态的稳定业务代码。允许值：APPROVED、REJECTED、REVOKED、EXPIRED。',
  `to_status` VARCHAR(64) NOT NULL COMMENT '新状态的稳定业务代码。允许值：APPROVED、REJECTED、REVOKED、EXPIRED。',
  `decision_id` BIGINT NOT NULL COMMENT '资格决定的稳定业务标识。',
  `reason_code` VARCHAR(64) NULL COMMENT '变更原因的稳定业务代码。取值来源：Risk 资格状态原因目录；非固定枚举。',
  `trigger_type` VARCHAR(64) NOT NULL COMMENT '触发类型的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `trigger_id` VARCHAR(128) NOT NULL COMMENT '触发标识的业务值。',
  `actor_id` BIGINT NULL COMMENT '操作人的稳定业务标识。',
  `aggregate_version` BIGINT NOT NULL COMMENT '聚合版本的长整数值。',
  `occurred_time` DATETIME(6) NOT NULL COMMENT '发生时间对应的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`change_id`),
  UNIQUE KEY `uk_qual_state_ver` (`qualification_id`, `aggregate_version`) COMMENT '保证业务唯一性：qualification_id、aggregate_version',
  KEY `idx_qual_state_dec` (`decision_id`) COMMENT '支持访问路径：decision_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='追加保存角色资格投影的状态迁移、决定引用和聚合版本';

CREATE TABLE `t_settlement_control` (
  `control_id` BIGINT NOT NULL COMMENT '结算资格控制的稳定业务标识。',
  `control_no` VARCHAR(64) NOT NULL COMMENT '结算控制编号的业务值。',
  `role_type` VARCHAR(64) NOT NULL COMMENT '角色类型的稳定业务代码。允许值：CLIENT、LOCAL_PARTNER。',
  `role_id` BIGINT NOT NULL COMMENT '角色的稳定业务标识。',
  `control_type` VARCHAR(64) NOT NULL COMMENT '控制类型的稳定业务代码。允许值：SETTLEMENT_BLOCK、MIN_SECURITY_DEPOSIT。',
  `reason_code` VARCHAR(64) NOT NULL COMMENT '控制原因的稳定业务代码。取值来源：Risk 结算资格控制原因目录；非固定枚举。',
  `source_action_effect_id` BIGINT NOT NULL COMMENT '来源 Action Effect的稳定业务标识。',
  `status` VARCHAR(64) NOT NULL COMMENT '控制状态的稳定业务代码。允许值：ACTIVE、RELEASED、EXPIRED。',
  `effective_from` DATETIME(6) NOT NULL COMMENT '生效时间对应的 UTC 微秒时间。',
  `effective_to` DATETIME(6) NULL COMMENT '失效时间对应的 UTC 微秒时间。',
  `required_amount` DECIMAL(24,8) NULL COMMENT '最低保证金金额，精度为 24 位、小数位为 8 位。',
  `required_currency` CHAR(3) NULL COMMENT '最低保证金使用的 ISO 4217 alpha-3 币种。取值来源：ISO 4217 alpha-3；非固定枚举。',
  `release_condition_code` VARCHAR(64) NULL COMMENT '释放条件代码的稳定业务代码。取值来源：Risk 结算资格控制释放条件目录；非固定枚举。',
  `control_instance_key` VARCHAR(160) NOT NULL COMMENT '控制实例键的业务值。',
  `content_hash` CHAR(64) NOT NULL COMMENT '控制内容哈希的 SHA-256 小写十六进制值。',
  `active_slot` BOOLEAN NULL COMMENT '活动控制占位，1 表示是，0 表示否，NULL 表示不适用。',
  `version` INT NOT NULL COMMENT '控制并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`control_id`),
  UNIQUE KEY `uk_settle_control_no` (`control_no`) COMMENT '保证业务唯一性：control_no',
  UNIQUE KEY `uk_settle_control_effect` (`source_action_effect_id`) COMMENT '保证业务唯一性：source_action_effect_id',
  UNIQUE KEY `uk_settle_control_active` (`role_type`, `role_id`, `control_instance_key`, `active_slot`) COMMENT '保证业务唯一性：role_type、role_id、control_instance_key、active_slot',
  KEY `idx_settle_control_role` (`role_type`, `role_id`, `active_slot`, `control_id`) COMMENT '支持访问路径：role_type、role_id、active_slot、control_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存由真实 Action Effect 创建的 Client 或 Local Partner 长期结算资格控制';

CREATE TABLE `t_settlement_control_scope` (
  `scope_id` BIGINT NOT NULL COMMENT '结算资格控制范围的稳定业务标识。',
  `control_id` BIGINT NOT NULL COMMENT '结算资格控制的稳定业务标识。',
  `operating_entity_id` BIGINT NULL COMMENT '经营主体的稳定业务标识。',
  `currency` CHAR(3) NULL COMMENT 'ISO 4217 alpha-3 适用币种；NULL 表示全部币种。取值来源：ISO 4217 alpha-3；非固定枚举。',
  `bank_country` CHAR(2) NULL COMMENT 'Finance Beneficiary Account Version 的 ISO 3166-1 alpha-2 开户银行国家；NULL 表示全部国家。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `scope_hash` CHAR(64) NOT NULL COMMENT '范围哈希的 SHA-256 小写十六进制值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`scope_id`),
  UNIQUE KEY `uk_settle_control_scope` (`control_id`, `scope_hash`) COMMENT '保证业务唯一性：control_id、scope_hash'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存结算资格控制适用的经营主体、币种和收款银行国家 OR 组合';

CREATE TABLE `t_settle_control_state_change` (
  `change_id` BIGINT NOT NULL COMMENT '结算资格控制状态变更的稳定业务标识。',
  `control_id` BIGINT NOT NULL COMMENT '结算资格控制的稳定业务标识。',
  `from_status` VARCHAR(64) NULL COMMENT '原状态的稳定业务代码。允许值：ACTIVE、RELEASED、EXPIRED。',
  `to_status` VARCHAR(64) NOT NULL COMMENT '新状态的稳定业务代码。允许值：ACTIVE、RELEASED、EXPIRED。',
  `reason_code` VARCHAR(64) NULL COMMENT '变更原因的稳定业务代码。取值来源：Risk 结算资格控制状态原因目录；非固定枚举。',
  `trigger_type` VARCHAR(64) NOT NULL COMMENT '触发类型的稳定业务代码。允许值：ACTION_EFFECT、OPERATION_COMMAND、SYSTEM_TIMER。',
  `trigger_id` VARCHAR(128) NOT NULL COMMENT '触发标识的业务值。',
  `actor_id` BIGINT NULL COMMENT '操作人的稳定业务标识。',
  `aggregate_version` INT NOT NULL COMMENT '聚合版本的整数值。',
  `occurred_time` DATETIME(6) NOT NULL COMMENT '发生时间对应的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`change_id`),
  UNIQUE KEY `uk_settle_control_state` (`control_id`, `aggregate_version`) COMMENT '保证业务唯一性：control_id、aggregate_version'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='追加保存结算资格控制的创建、准确释放和过期迁移及连续聚合版本';

CREATE TABLE `t_compensation_event` (
  `id` BIGINT NOT NULL COMMENT '补偿事件的稳定业务标识。',
  `event_key` VARCHAR(128) NOT NULL COMMENT '事件键的业务值。',
  `event_type` VARCHAR(64) NOT NULL COMMENT '事件类型的稳定业务代码。取值来源：Risk & Compliance 应用全局唯一 Handler 目录；非固定枚举。',
  `sub_event_type` VARCHAR(64) NOT NULL COMMENT '事件子类型的稳定业务代码。取值来源：对应 Handler 的 Risk 业务子类型目录；非固定枚举。',
  `payload_json` VARCHAR(4096) NULL COMMENT '可选且不超过 4 KiB UTF-8 的最小非敏感 JSON Object；默认从本库权威事实重建。',
  `execute_status` VARCHAR(64) NOT NULL DEFAULT 'INIT' COMMENT '执行状态的稳定业务代码。允许值：INIT、PROCESSING、ERROR、FAILED。',
  `execute_count` INT NOT NULL DEFAULT 0 COMMENT '执行次数的整数值。',
  `next_execute_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '下次执行时间对应的 UTC 微秒时间。',
  `lock_owner` VARCHAR(64) NULL COMMENT '租约持有者的业务值。',
  `lock_until` DATETIME(6) NULL COMMENT '租约截止时间对应的 UTC 微秒时间。',
  `last_error_code` VARCHAR(64) NULL COMMENT '最近错误代码的稳定业务代码。取值来源：Risk 补偿执行错误目录；非固定枚举。',
  `last_error_summary` VARCHAR(1000) NULL COMMENT '最近脱敏错误摘要的业务值。',
  `lock_version` BIGINT NOT NULL DEFAULT 1 COMMENT 'Claim 与 Lease 条件更新使用的正长整数版本，登记初始值固定为 1。',
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间对应的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间对应的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comp_event_identity` (`event_key`, `event_type`, `sub_event_type`) COMMENT '保证业务唯一性：event_key、event_type、sub_event_type',
  KEY `idx_comp_event_ready` (`execute_status`, `next_execute_at`, `id`) COMMENT '支持访问路径：execute_status、next_execute_at、id',
  KEY `idx_comp_event_lease` (`execute_status`, `lock_until`, `id`) COMMENT '支持访问路径：execute_status、lock_until、id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存与业务事务原子登记的 MQ 发布或本地可恢复动作及 Claim Lease';
