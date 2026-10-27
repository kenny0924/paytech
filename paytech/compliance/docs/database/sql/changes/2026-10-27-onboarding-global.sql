-- 2026-10-27 全球合规入驻与资格 · 切片 1（requirements/20261027/client-master-全球合规入驻与资格切片1/technique.md §2.3）
-- 目标库：mor_compliance（已执行 2026-10-24 两个增量的库）。由开发者审核后用具 DDL 权限的账号手工执行；应用不会执行本文件。
--
-- 原则（发布决策 P-8）：保留存量行，不 DROP 表。执行后结构与 compliance-schema.sql 逐列、逐索引、逐注释一致。
--   每张变更表分三步：① ADD 新列（NOT NULL 新列带临时默认值，使旧行合法）；② 回填旧行；
--   ③ 按目标顺序 MODIFY 全部列为最终定义（去掉临时默认值）、DROP 旧列、调整索引与表注释。
-- 旧行回填口径：
--   t_dd_program / _version：旧通用程序（CLIENT_INDIVIDUAL_GENERIC 等）按 qualification_type 回填 party_type / role_type，tier=STANDARD，
--     程序与版本一律置 RETIRED（版本 active_slot=NULL），不再被解析；保留以便已批准案件回读冻结的程序版本。
--   t_dd_program_requirement：旧 document_type 迁入 t_dd_program_requirement_doc_type（行 ID 复用 program_requirement_id），
--     subject_role=PRIMARY，waivable_flag=0。
--   t_due_diligence_case：case_type=ONBOARDING，jurisdiction_code=LEGACY（旧案件无辖区，与 client-master P-11 的 LEGACY 口径一致），
--     tier=STANDARD，round_no=1，其余新列为空。
--   t_dd_evidence_ref：file_address='/files/v1/' || file_id；document_type 取所属程序材料槽的类型（取不到为 LEGACY_DOCUMENT）。
--   t_dd_check / t_screening_run / t_screening_hit：旧代码从未写入（预期 0 行）；如有行，case_id 由主体回填，其余取保守值。
--   资格两表只 ADD 可空列；t_qualification_state_change、结算控制三表与 t_compensation_event 不变。
-- 删除的列：t_dd_program_version.review_frequency_code / content_hash、t_dd_program_requirement.document_type、t_dd_evidence_ref.file_id、
--   t_dd_check.result_code / result_vault_token / performed_time、t_screening_hit.name_vault_token、
--   t_screening_resolution.evidence_file_id / evidence_content_hash（后四类旧代码未写入）。
--
-- 执行顺序：client-master 先上线（兼容 ClientReviewDecided v1/v2）→ compliance 服务停止 → 备份 → 本脚本
--   → 初始化 DML（sql/compliance-initial-data.sql，先 SET @approved_by）→ 清理（changes/2026-10-27-onboarding-global-cleanup.sql）→ compliance 新镜像。
-- 本脚本不可重复执行（第二次 ADD COLUMN 会报列已存在）；中途失败时恢复备份后重来。
-- 回滚：恢复执行前的 mysqldump 备份（删除的列与 RETIRED 前的程序状态无法逆向恢复）。

-- ===== 1. 新表 =====
CREATE TABLE `t_dd_program_requirement_doc_type` (
  `requirement_doc_type_id` BIGINT NOT NULL COMMENT '材料槽允许类型行的稳定业务标识。',
  `program_requirement_id` BIGINT NOT NULL COMMENT '程序材料模板的稳定业务标识。',
  `document_type` VARCHAR(64) NOT NULL COMMENT '允许的材料类型稳定业务代码，例如 TRADE_LICENSE、PASSPORT。取值来源：client-master 材料类型目录；非固定枚举。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`requirement_doc_type_id`),
  UNIQUE KEY `uk_dd_prog_req_doc_type` (`program_requirement_id`, `document_type`) COMMENT '保证业务唯一性：program_requirement_id、document_type'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定义一个程序材料槽允许提交的材料类型，一类型一行';
CREATE TABLE `t_screening_hit_list` (
  `hit_list_id` BIGINT NOT NULL COMMENT '命中名单类型行的稳定业务标识。',
  `hit_id` BIGINT NOT NULL COMMENT '筛查命中的稳定业务标识。',
  `list_type` VARCHAR(32) NOT NULL COMMENT '命中所在名单类型的稳定业务代码，例如 SANCTIONS、PEP、ADVERSE_MEDIA；非固定枚举。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`hit_list_id`),
  UNIQUE KEY `uk_screen_hit_list` (`hit_id`, `list_type`) COMMENT '保证业务唯一性：hit_id、list_type'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存一条筛查命中所在的名单类型，一值一行';
CREATE TABLE `t_dd_risk_factor` (
  `factor_id` BIGINT NOT NULL COMMENT '风险因子的稳定业务标识。',
  `case_id` BIGINT NOT NULL COMMENT '尽调主案件的稳定业务标识。',
  `factor_code` VARCHAR(64) NOT NULL COMMENT '因子代码的稳定业务代码。允许值：COUNTRY_RISK、NATIONALITY_RISK、PEP、INDUSTRY_RISK、VOLUME、ENTITY_STRUCTURE、ADVERSE_MEDIA、PRIOR_REJECTION、MANUAL。',
  `factor_value` VARCHAR(128) NOT NULL COMMENT '因子取值或依据文本。',
  `score` DECIMAL(10,4) NULL COMMENT '自动评分得分；本期人工评级为空。',
  `source` VARCHAR(16) NOT NULL COMMENT '因子来源的稳定业务代码。允许值：AUTO、REVIEWER；本期只写 REVIEWER。',
  `recorded_by` BIGINT NULL COMMENT '记录人的 Platform OPERATIONS Account ID；AUTO 为空。',
  `recorded_time` DATETIME(6) NOT NULL COMMENT '记录时间对应的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`factor_id`),
  UNIQUE KEY `uk_dd_risk_factor` (`case_id`, `factor_code`, `factor_value`) COMMENT '保证业务唯一性：case_id、factor_code、factor_value'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存案件客户风险评级的依据因子；随评级替换，决定时随案件冻结';
CREATE TABLE `t_dd_return_error` (
  `return_error_id` BIGINT NOT NULL COMMENT '退回需求级错误的稳定业务标识。',
  `case_id` BIGINT NOT NULL COMMENT '被退回主案件的稳定业务标识。',
  `sequence_no` INT NOT NULL COMMENT '同一案件内的错误序号，从 1 起按退回请求顺序。',
  `requirement_key` VARCHAR(128) NOT NULL COMMENT '与 client-master 需求清单一致的需求键，例如 document.REGISTRATION。',
  `subject_party_id` BIGINT NULL COMMENT '需求所属 Party ID（client-master t_party.party_id）；与主体无关为空。',
  `error_code` VARCHAR(64) NOT NULL COMMENT '错误码的稳定业务代码（设计 8.3 错误码族），例如 DOCUMENT_EXPIRED；非固定枚举。',
  `reason` VARCHAR(1000) NULL COMMENT '审核员给 Client 的说明，客户可见。',
  `remediation_code` VARCHAR(64) NOT NULL COMMENT '补救动作的稳定业务代码，例如 UPLOAD_DOCUMENT、PROVIDE_FIELD；非固定枚举。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`return_error_id`),
  UNIQUE KEY `uk_dd_return_error` (`case_id`, `sequence_no`) COMMENT '保证业务唯一性：case_id、sequence_no'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存一次退回登记的需求级错误，按主案件存储；Client 据此定位并补件';

-- ===== t_dd_program =====
ALTER TABLE `t_dd_program`
  ADD COLUMN `jurisdiction_code` VARCHAR(16) NULL,
  ADD COLUMN `party_type` VARCHAR(32) NOT NULL DEFAULT 'ORGANIZATION',
  ADD COLUMN `role_type` VARCHAR(64) NOT NULL DEFAULT 'CLIENT',
  ADD COLUMN `tier` VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
  ADD COLUMN `owner_partner_id` BIGINT NULL;
UPDATE `t_dd_program`
   SET `party_type` = CASE `qualification_type` WHEN 'INDIVIDUAL_KYC' THEN 'INDIVIDUAL' WHEN 'RELATED_PERSON_KYC' THEN 'INDIVIDUAL' ELSE 'ORGANIZATION' END,
       `role_type` = CASE `qualification_type` WHEN 'RELATED_PERSON_KYC' THEN 'RELATED_PERSON' WHEN 'PARTNER_DUE_DILIGENCE' THEN 'LOCAL_PARTNER'
                     WHEN 'ENTITY_UNDERWRITING' THEN 'OPERATING_ENTITY' ELSE 'CLIENT' END,
       `tier` = 'STANDARD', `jurisdiction_code` = NULL, `owner_partner_id` = NULL,
       `status` = 'RETIRED', `update_time` = UTC_TIMESTAMP(6);
ALTER TABLE `t_dd_program`
  MODIFY COLUMN `program_id` BIGINT NOT NULL COMMENT '尽调程序的稳定业务标识。' FIRST,
  MODIFY COLUMN `program_code` VARCHAR(64) NOT NULL COMMENT '程序编码的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。' AFTER `program_id`,
  MODIFY COLUMN `qualification_type` VARCHAR(64) NOT NULL COMMENT '资格类型的稳定业务代码。允许值：INDIVIDUAL_KYC、ORGANIZATION_KYB、PARTNER_DUE_DILIGENCE、ENTITY_UNDERWRITING、RELATED_PERSON_KYC。' AFTER `program_code`,
  MODIFY COLUMN `jurisdiction_code` VARCHAR(16) NULL COMMENT '适用辖区代码，引用 client-master 辖区目录 t_jurisdiction.jurisdiction_code（创建后不变，例如 SA、AE-DIFC）；NULL 表示全局默认程序。解析时辖区精确匹配优先于全局程序。' AFTER `qualification_type`,
  MODIFY COLUMN `party_type` VARCHAR(32) NOT NULL COMMENT '受审主体类型的稳定业务代码。允许值：ORGANIZATION、INDIVIDUAL。' AFTER `jurisdiction_code`,
  MODIFY COLUMN `role_type` VARCHAR(64) NOT NULL COMMENT '受审角色的稳定业务代码。允许值：CLIENT、RELATED_PERSON、LOCAL_PARTNER、OPERATING_ENTITY。' AFTER `party_type`,
  MODIFY COLUMN `tier` VARCHAR(32) NOT NULL COMMENT '尽调档次的稳定业务代码。允许值：SIMPLIFIED、STANDARD、ENHANCED。' AFTER `role_type`,
  MODIFY COLUMN `owner_partner_id` BIGINT NULL COMMENT '定制该程序的 PayFac Local Partner ID（client-master t_local_partner 的稳定 ID）；NULL 表示平台标准程序。解析时 Owner 定制程序优先于平台程序。' AFTER `tier`,
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '程序状态的稳定业务代码。允许值：DRAFT、ACTIVE、RETIRED。' AFTER `owner_partner_id`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。' AFTER `status`,
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。' AFTER `create_time`,
  ADD KEY `idx_dd_program_scope` (`party_type`, `role_type`, `tier`, `jurisdiction_code`) COMMENT '支持访问路径：party_type、role_type、tier、jurisdiction_code',
  COMMENT = '保存角色资格尽调程序的稳定身份、适用辖区、主体类型、角色、档次与 Owner';

-- ===== t_dd_program_version =====
ALTER TABLE `t_dd_program_version`
  ADD COLUMN `review_interval_months` INT NULL,
  ADD COLUMN `four_eyes_required` BOOLEAN NOT NULL DEFAULT 1;
UPDATE `t_dd_program_version`
   SET `status` = 'RETIRED', `active_slot` = NULL, `four_eyes_required` = 1, `review_interval_months` = NULL, `update_time` = UTC_TIMESTAMP(6);
ALTER TABLE `t_dd_program_version`
  DROP COLUMN `review_frequency_code`,
  DROP COLUMN `content_hash`,
  MODIFY COLUMN `program_version_id` BIGINT NOT NULL COMMENT '尽调程序版本的稳定业务标识。' FIRST,
  MODIFY COLUMN `program_id` BIGINT NOT NULL COMMENT '尽调程序的稳定业务标识。' AFTER `program_version_id`,
  MODIFY COLUMN `version_no` INT NOT NULL COMMENT '版本号的整数值，同一程序从 1 递增。' AFTER `program_id`,
  MODIFY COLUMN `review_interval_months` INT NULL COMMENT '批准后的定期复审间隔月数，正整数；NULL 表示不排定期复审。' AFTER `version_no`,
  MODIFY COLUMN `four_eyes_required` BOOLEAN NOT NULL COMMENT '批准或拒绝是否要求推荐人与决定人不同，1 表示是，0 表示否（为 0 时决定记 four_eyes_waived=1）。' AFTER `review_interval_months`,
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '版本状态的稳定业务代码。允许值：DRAFT、ACTIVE、RETIRED。' AFTER `four_eyes_required`,
  MODIFY COLUMN `active_slot` BOOLEAN NULL COMMENT 'ACTIVE 时为 1，其他状态必须为 NULL；禁止写入 0。' AFTER `status`,
  MODIFY COLUMN `approved_by` BIGINT NULL COMMENT '批准该版本的 Platform OPERATIONS Account ID；未批准为空，未批准版本不能被解析。' AFTER `active_slot`,
  MODIFY COLUMN `approved_time` DATETIME(6) NULL COMMENT '批准时间对应的 UTC 微秒时间；晚于当前时间的版本不能被解析。' AFTER `approved_by`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。' AFTER `approved_time`,
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。' AFTER `create_time`,
  COMMENT = '保存经审核冻结并可被案件精确引用的尽调程序版本';

-- ===== t_dd_program_requirement =====
ALTER TABLE `t_dd_program_requirement`
  ADD COLUMN `subject_role` VARCHAR(64) NOT NULL DEFAULT 'PRIMARY',
  ADD COLUMN `max_age_days` INT NULL,
  ADD COLUMN `waivable_flag` BOOLEAN NOT NULL DEFAULT 0;
INSERT INTO `t_dd_program_requirement_doc_type` (`requirement_doc_type_id`, `program_requirement_id`, `document_type`, `create_time`)
SELECT `program_requirement_id`, `program_requirement_id`, `document_type`, `create_time` FROM `t_dd_program_requirement`;
UPDATE `t_dd_program_requirement` SET `subject_role` = 'PRIMARY', `waivable_flag` = 0;
ALTER TABLE `t_dd_program_requirement`
  DROP COLUMN `document_type`,
  MODIFY COLUMN `program_requirement_id` BIGINT NOT NULL COMMENT '程序材料模板的稳定业务标识。' FIRST,
  MODIFY COLUMN `program_version_id` BIGINT NOT NULL COMMENT '程序版本的稳定业务标识。' AFTER `program_requirement_id`,
  MODIFY COLUMN `requirement_code` VARCHAR(64) NOT NULL COMMENT '材料要求编码的稳定业务代码（材料槽），例如 REGISTRATION、IDENTITY。取值来源：Risk & Compliance 受控代码目录；非固定枚举。' AFTER `program_version_id`,
  MODIFY COLUMN `subject_role` VARCHAR(64) NOT NULL COMMENT '材料归属的案件主体角色。允许值：PRIMARY（受审主体本身）、UBO、DIRECTOR、SIGNATORY、ANY_RELATED；本期程序只使用 PRIMARY。' AFTER `requirement_code`,
  MODIFY COLUMN `required_flag` BOOLEAN NOT NULL COMMENT '是否必需，1 表示是，0 表示否。' AFTER `subject_role`,
  MODIFY COLUMN `max_age_days` INT NULL COMMENT '材料签发日期距提交的最长天数，正整数；NULL 表示不限。' AFTER `required_flag`,
  MODIFY COLUMN `waivable_flag` BOOLEAN NOT NULL COMMENT '审核员是否可豁免该要求，1 表示是，0 表示否。' AFTER `max_age_days`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。' AFTER `waivable_flag`,
  COMMENT = '定义一个尽调程序版本要求收集的材料槽、归属主体角色、时效与可豁免性';

-- ===== t_dd_program_check =====
ALTER TABLE `t_dd_program_check`
  ADD COLUMN `check_mode` VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
  ADD COLUMN `subject_role` VARCHAR(64) NOT NULL DEFAULT 'PRIMARY',
  ADD COLUMN `standard_api_code` VARCHAR(64) NULL,
  ADD COLUMN `blocking_flag` BOOLEAN NOT NULL DEFAULT 1;
ALTER TABLE `t_dd_program_check`
  MODIFY COLUMN `program_check_id` BIGINT NOT NULL COMMENT '程序检查模板的稳定业务标识。' FIRST,
  MODIFY COLUMN `program_version_id` BIGINT NOT NULL COMMENT '程序版本的稳定业务标识。' AFTER `program_check_id`,
  MODIFY COLUMN `check_code` VARCHAR(64) NOT NULL COMMENT '检查编码的稳定业务代码，例如 WEBSITE_REVIEW、TERMINATED_MERCHANT_LOOKUP、MANUAL_SCREENING。取值来源：Risk & Compliance 受控代码目录；非固定枚举。' AFTER `program_version_id`,
  MODIFY COLUMN `check_type` VARCHAR(64) NOT NULL COMMENT '检查类型的稳定业务代码，例如 BUSINESS、LIST_LOOKUP、SCREENING。取值来源：Risk & Compliance 受控代码目录；非固定枚举。' AFTER `check_code`,
  MODIFY COLUMN `check_mode` VARCHAR(16) NOT NULL COMMENT '执行方式的稳定业务代码。允许值：MANUAL（审核员录入结论）、AUTOMATED（经 channel-connector 标准 API 执行，本期未启用）。' AFTER `check_type`,
  MODIFY COLUMN `subject_role` VARCHAR(64) NOT NULL COMMENT '检查对象的案件主体角色。允许值：PRIMARY、UBO、DIRECTOR、SIGNATORY、ANY_RELATED；本期程序只使用 PRIMARY。' AFTER `check_mode`,
  MODIFY COLUMN `standard_api_code` VARCHAR(64) NULL COMMENT 'AUTOMATED 检查使用的 channel-connector 标准 API 编码；MANUAL 为空。' AFTER `subject_role`,
  MODIFY COLUMN `required_flag` BOOLEAN NOT NULL COMMENT '是否必需，1 表示是，0 表示否。' AFTER `standard_api_code`,
  MODIFY COLUMN `blocking_flag` BOOLEAN NOT NULL COMMENT '结论非 PASS 时是否阻断批准，1 表示是，0 表示否。' AFTER `required_flag`,
  MODIFY COLUMN `validity_days` INT NULL COMMENT '检查结论的有效天数，正整数；valid_until = 录入时间加该天数；NULL 表示结论不设有效期且重提时不复用。' AFTER `blocking_flag`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。' AFTER `validity_days`,
  COMMENT = '定义一个尽调程序版本要求执行的人工或自动检查';

-- ===== t_dd_program_screening =====
ALTER TABLE `t_dd_program_screening`
  ADD COLUMN `standard_api_code` VARCHAR(64) NOT NULL DEFAULT 'SCREENING_SEARCH',
  ADD COLUMN `monitoring_required` BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE `t_dd_program_screening`
  MODIFY COLUMN `program_screening_id` BIGINT NOT NULL COMMENT '程序筛查模板的稳定业务标识。' FIRST,
  MODIFY COLUMN `program_version_id` BIGINT NOT NULL COMMENT '程序版本的稳定业务标识。' AFTER `program_screening_id`,
  MODIFY COLUMN `subject_role` VARCHAR(64) NOT NULL COMMENT '被筛查的案件主体角色。允许值：PRIMARY、UBO、DIRECTOR、SIGNATORY；本期程序只使用 PRIMARY。' AFTER `program_version_id`,
  MODIFY COLUMN `screening_type` VARCHAR(64) NOT NULL COMMENT '筛查类型的稳定业务代码。允许值：SANCTIONS、PEP、ADVERSE_MEDIA。' AFTER `subject_role`,
  MODIFY COLUMN `standard_api_code` VARCHAR(64) NOT NULL COMMENT '执行该筛查的 channel-connector 标准 API 编码，本期为 SCREENING_SEARCH。' AFTER `screening_type`,
  MODIFY COLUMN `required_flag` BOOLEAN NOT NULL COMMENT '是否必需，1 表示批准前必须有一次成功执行，0 表示否。' AFTER `standard_api_code`,
  MODIFY COLUMN `monitoring_required` BOOLEAN NOT NULL COMMENT '批准后是否需要持续监控，1 表示是，0 表示否；本期只登记不执行。' AFTER `required_flag`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。' AFTER `monitoring_required`,
  COMMENT = '定义一个尽调程序版本要求执行的制裁、PEP 或负面信息筛查';

-- ===== t_due_diligence_case =====
ALTER TABLE `t_due_diligence_case`
  ADD COLUMN `case_type` VARCHAR(32) NOT NULL DEFAULT 'ONBOARDING',
  ADD COLUMN `jurisdiction_code` VARCHAR(16) NOT NULL DEFAULT 'LEGACY',
  ADD COLUMN `tier` VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
  ADD COLUMN `round_no` INT NOT NULL DEFAULT 1,
  ADD COLUMN `previous_case_id` BIGINT NULL,
  ADD COLUMN `responsible_partner_id` BIGINT NULL,
  ADD COLUMN `risk_rating` VARCHAR(16) NULL,
  ADD COLUMN `risk_rating_note` VARCHAR(1000) NULL,
  ADD COLUMN `risk_rated_by` BIGINT NULL,
  ADD COLUMN `risk_rated_time` DATETIME(6) NULL,
  ADD COLUMN `recommendation` VARCHAR(16) NULL,
  ADD COLUMN `recommendation_note` VARCHAR(2000) NULL,
  ADD COLUMN `recommended_by` BIGINT NULL,
  ADD COLUMN `recommended_time` DATETIME(6) NULL,
  ADD COLUMN `review_due_time` DATETIME(6) NULL,
  ADD COLUMN `operated_by` VARCHAR(16) NULL,
  ADD COLUMN `on_behalf_of_partner_id` BIGINT NULL;
ALTER TABLE `t_due_diligence_case`
  MODIFY COLUMN `case_id` BIGINT NOT NULL COMMENT '尽调案件的稳定业务标识。' FIRST,
  MODIFY COLUMN `case_no` VARCHAR(64) NOT NULL COMMENT '案件编号的业务值。' AFTER `case_id`,
  MODIFY COLUMN `case_type` VARCHAR(32) NOT NULL COMMENT '案件类型的稳定业务代码。允许值：ONBOARDING、PROFILE_AMENDMENT、PERIODIC_REVIEW、TRIGGERED_REVIEW；本期只写 ONBOARDING。' AFTER `case_no`,
  MODIFY COLUMN `role_type` VARCHAR(64) NOT NULL COMMENT '角色类型的稳定业务代码。允许值：CLIENT、LOCAL_PARTNER、OPERATING_ENTITY、RELATED_PERSON。' AFTER `case_type`,
  MODIFY COLUMN `role_id` BIGINT NOT NULL COMMENT '角色的稳定业务标识：CLIENT 为 Client ID，RELATED_PERSON 为关联人员 Party ID。' AFTER `role_type`,
  MODIFY COLUMN `qualification_type` VARCHAR(64) NOT NULL COMMENT '资格类型的稳定业务代码。允许值：INDIVIDUAL_KYC、ORGANIZATION_KYB、PARTNER_DUE_DILIGENCE、ENTITY_UNDERWRITING、RELATED_PERSON_KYC。' AFTER `role_id`,
  MODIFY COLUMN `dd_program_version_id` BIGINT NOT NULL COMMENT '立案时冻结的尽调程序版本稳定业务标识；版本发布不影响在途案件。' AFTER `qualification_type`,
  MODIFY COLUMN `jurisdiction_code` VARCHAR(16) NOT NULL COMMENT '立案时冻结的辖区代码（client-master t_jurisdiction.jurisdiction_code）。' AFTER `dd_program_version_id`,
  MODIFY COLUMN `tier` VARCHAR(32) NOT NULL COMMENT '立案时冻结的尽调档次。允许值：SIMPLIFIED、STANDARD、ENHANCED。' AFTER `jurisdiction_code`,
  MODIFY COLUMN `case_origin` VARCHAR(64) NOT NULL COMMENT '案件来源类型的稳定业务代码。允许值：APPLICATION、CONTINUOUS_REVIEW、MANUAL。' AFTER `tier`,
  MODIFY COLUMN `scheduled_review_time` DATETIME(6) NULL COMMENT '计划复审时间对应的 UTC 微秒时间。' AFTER `case_origin`,
  MODIFY COLUMN `source_namespace` VARCHAR(64) NOT NULL COMMENT '区分 client-master Client Application、Operating Entity Application、Local Partner Application 与本地来源的稳定命名空间。允许值：CCT_CLIENT_APPLICATION、CCT_OPERATING_ENTITY_APPLICATION、CCT_LOCAL_PARTNER_APPLICATION、RISK_COMPLIANCE。' AFTER `scheduled_review_time`,
  MODIFY COLUMN `source_application_id` BIGINT NULL COMMENT '来源申请的稳定业务标识（client-master t_client_application.application_id）。' AFTER `source_namespace`,
  MODIFY COLUMN `source_submission_id` VARCHAR(64) NULL COMMENT 'Client 每次真实提交的 UUID；同次重试复用，退回重提更换。' AFTER `source_application_id`,
  MODIFY COLUMN `round_no` INT NOT NULL COMMENT '同一申请的提交轮次，首次为 1，每次退回重提加 1。' AFTER `source_submission_id`,
  MODIFY COLUMN `previous_case_id` BIGINT NULL COMMENT '退回重提时上一轮被退回的同级案件 ID（主案件指上一主案件，子案件指上一轮同一关联人员的子案件）；首次提交为空。' AFTER `round_no`,
  MODIFY COLUMN `responsible_partner_id` BIGINT NULL COMMENT '对收单行承担尽调责任的 PayFac Local Partner ID（由入驻码解析，client-master t_local_partner 的稳定 ID）；平台自营为空。' AFTER `previous_case_id`,
  MODIFY COLUMN `risk_rating` VARCHAR(16) NULL COMMENT '客户风险评级的稳定业务代码。允许值：LOW、MEDIUM、HIGH；未评为空，未评不能批准。' AFTER `responsible_partner_id`,
  MODIFY COLUMN `risk_rating_note` VARCHAR(1000) NULL COMMENT '评级人填写的评级依据说明，内部可见。' AFTER `risk_rating`,
  MODIFY COLUMN `risk_rated_by` BIGINT NULL COMMENT '最近一次评级的 Platform OPERATIONS Account ID。' AFTER `risk_rating_note`,
  MODIFY COLUMN `risk_rated_time` DATETIME(6) NULL COMMENT '最近一次评级对应的 UTC 微秒时间。' AFTER `risk_rated_by`,
  MODIFY COLUMN `recommendation` VARCHAR(16) NULL COMMENT '推荐结论的稳定业务代码。允许值：APPROVE、REJECT；改评级会清空推荐。' AFTER `risk_rated_time`,
  MODIFY COLUMN `recommendation_note` VARCHAR(2000) NULL COMMENT '推荐人填写的推荐理由，内部可见。' AFTER `recommendation`,
  MODIFY COLUMN `recommended_by` BIGINT NULL COMMENT '推荐人的 Platform OPERATIONS Account ID；四眼时决定人必须与其不同。' AFTER `recommendation_note`,
  MODIFY COLUMN `recommended_time` DATETIME(6) NULL COMMENT '推荐时间对应的 UTC 微秒时间。' AFTER `recommended_by`,
  MODIFY COLUMN `public_note` VARCHAR(1000) NULL COMMENT '运营单独确认的安全退回或决定说明，不复制内部审核理由。' AFTER `recommended_time`,
  MODIFY COLUMN `resolution_note` VARCHAR(2000) NULL COMMENT '本案件结束的内部审核理由，禁止直接展示给 Client。' AFTER `public_note`,
  MODIFY COLUMN `return_section` VARCHAR(32) NULL COMMENT '兼容字段：退回定位到单页的资料分区。允许值：BASIC、BUSINESS、PEOPLE、MATERIALS；需求级错误见 t_dd_return_error。' AFTER `resolution_note`,
  MODIFY COLUMN `review_due_time` DATETIME(6) NULL COMMENT '退回后 Client 必须重提的 UTC 微秒期限；超过且未重提时系统把 RETURNED 案件置为 EXPIRED。' AFTER `return_section`,
  MODIFY COLUMN `source_submission_version` BIGINT NULL COMMENT '未迁移 APPLICATION 来源的正整数版本；新 Client 使用 source_submission_id，本列为空。' AFTER `review_due_time`,
  MODIFY COLUMN `source_submission_hash` CHAR(64) NULL COMMENT '未迁移 APPLICATION 来源的提交摘要；新 Client 不填本列。' AFTER `source_submission_version`,
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '案件状态的稳定业务代码。允许值：DRAFT、SUBMITTED、IN_REVIEW、WAITING_EVIDENCE、PENDING_DEPENDENCY、RETURNED、APPROVED、REJECTED、WITHDRAWN、EXPIRED。' AFTER `source_submission_hash`,
  MODIFY COLUMN `active_slot` BOOLEAN NULL COMMENT 'SUBMITTED、IN_REVIEW、WAITING_EVIDENCE、PENDING_DEPENDENCY 时为 1；RETURNED 与终态必须为 NULL；禁止写入 0。' AFTER `status`,
  MODIFY COLUMN `operated_by` VARCHAR(16) NULL COMMENT '操作方的稳定业务代码。允许值：PLATFORM（平台运营，含代 PayFac 操作）、PARTNER（PayFac 自有合规人员，本期不开放）；系统动作为空。' AFTER `active_slot`,
  MODIFY COLUMN `on_behalf_of_partner_id` BIGINT NULL COMMENT '平台运营代 PayFac 操作时的责任 PayFac Local Partner ID（client-master t_local_partner 的稳定 ID）；非代操作为空。' AFTER `operated_by`,
  MODIFY COLUMN `submitted_time` DATETIME(6) NULL COMMENT '提交时间对应的 UTC 微秒时间。' AFTER `on_behalf_of_partner_id`,
  MODIFY COLUMN `decision_time` DATETIME(6) NULL COMMENT '退回、决定、撤回或过期对应的 UTC 微秒时间。' AFTER `submitted_time`,
  MODIFY COLUMN `version` BIGINT NOT NULL COMMENT '案件并发版本的长整数值。' AFTER `decision_time`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。' AFTER `version`,
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。' AFTER `create_time`,
  ADD KEY `idx_dd_case_previous` (`previous_case_id`) COMMENT '支持访问路径：previous_case_id',
  ADD KEY `idx_dd_case_due` (`status`, `review_due_time`) COMMENT '支持访问路径：status、review_due_time',
  ADD KEY `idx_dd_case_partner` (`responsible_partner_id`, `submitted_time`) COMMENT '支持访问路径：responsible_partner_id、submitted_time',
  COMMENT = '保存一次角色化 KYC、KYB 尽调案件及其冻结的辖区、档次、责任方、评级与推荐';

-- ===== t_dd_case_subject =====
ALTER TABLE `t_dd_case_subject`
  ADD COLUMN `ubo_basis` VARCHAR(64) NULL,
  ADD COLUMN `job_title` VARCHAR(128) NULL;
ALTER TABLE `t_dd_case_subject`
  MODIFY COLUMN `case_subject_id` BIGINT NOT NULL COMMENT '案件主体范围的稳定业务标识。' FIRST,
  MODIFY COLUMN `case_id` BIGINT NOT NULL COMMENT '尽调案件的稳定业务标识。' AFTER `case_subject_id`,
  MODIFY COLUMN `party_id` BIGINT NOT NULL COMMENT 'Party 主体的稳定业务标识（client-master t_party.party_id）。' AFTER `case_id`,
  MODIFY COLUMN `subject_role` VARCHAR(64) NOT NULL COMMENT '主体角色的稳定业务代码。允许值：PRIMARY、UBO、DIRECTOR、SIGNATORY。' AFTER `party_id`,
  MODIFY COLUMN `primary_slot` BOOLEAN NULL COMMENT 'PRIMARY 主体时为 1，关联主体必须为 NULL；禁止写入 0。' AFTER `subject_role`,
  MODIFY COLUMN `source_profile_id` BIGINT NOT NULL COMMENT '来源档案的稳定业务标识（client-master t_party_profile.profile_id）。' AFTER `primary_slot`,
  MODIFY COLUMN `source_relation_id` BIGINT NULL COMMENT '来源稳定关系的稳定业务标识。' AFTER `source_profile_id`,
  MODIFY COLUMN `source_profile_version_no` BIGINT NULL COMMENT '旧来源保留真实 Profile 版本；新 Client 使用稳定 Profile ID，本列为空，禁止伪造版本。' AFTER `source_relation_id`,
  MODIFY COLUMN `source_relation_version_id` BIGINT NULL COMMENT '关联主体对应的关系版本稳定标识；PRIMARY 必须为 NULL。' AFTER `source_profile_version_no`,
  MODIFY COLUMN `source_relation_version_no` BIGINT NULL COMMENT '关联主体对应的关系版本号正整数坐标；PRIMARY 必须为 NULL。' AFTER `source_relation_version_id`,
  MODIFY COLUMN `ownership_percentage` DECIMAL(18,10) NULL COMMENT 'UBO 来源关系冻结的持股百分比，取值大于 0 且不超过 100；其他主体角色必须为 NULL。' AFTER `source_relation_version_no`,
  MODIFY COLUMN `ubo_basis` VARCHAR(64) NULL COMMENT '关联人员 UBO 认定依据代码（OWNERSHIP、CONTROL、SENIOR_MANAGING_OFFICIAL 等，client-master 目录）；非 UBO 或主体本身为空。' AFTER `ownership_percentage`,
  MODIFY COLUMN `job_title` VARCHAR(128) NULL COMMENT '关联人员在组织中的职务名称；主体本身为空。' AFTER `ubo_basis`,
  MODIFY COLUMN `required_flag` BOOLEAN NOT NULL COMMENT '是否必需，1 表示是，0 表示否。' AFTER `job_title`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。' AFTER `required_flag`,
  COMMENT = '冻结尽调案件覆盖的 Party、角色、来源 Profile 坐标及关联主体关系事实';

-- ===== t_dd_case_dependency =====
ALTER TABLE `t_dd_case_dependency`
  MODIFY COLUMN `dependency_id` BIGINT NOT NULL COMMENT '案件依赖的稳定业务标识。' FIRST,
  MODIFY COLUMN `parent_case_id` BIGINT NOT NULL COMMENT '父案件的稳定业务标识。' AFTER `dependency_id`,
  MODIFY COLUMN `child_case_id` BIGINT NOT NULL COMMENT '子案件的稳定业务标识。' AFTER `parent_case_id`,
  MODIFY COLUMN `dependency_type` VARCHAR(64) NOT NULL COMMENT '依赖类型的稳定业务代码。允许值：UBO_KYC、DIRECTOR_KYC、SIGNATORY_KYC、RELATED_PERSON_KYC（关系类型目录中的其他关系）。' AFTER `child_case_id`,
  MODIFY COLUMN `required_flag` BOOLEAN NOT NULL COMMENT '是否必需，1 表示是，0 表示否。' AFTER `dependency_type`,
  MODIFY COLUMN `accepted_decision_id` BIGINT NULL COMMENT '已接受决定的稳定业务标识。' AFTER `required_flag`,
  MODIFY COLUMN `version` BIGINT NOT NULL COMMENT '依赖并发版本的长整数值。' AFTER `accepted_decision_id`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。' AFTER `version`,
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。' AFTER `create_time`,
  COMMENT = '保存组织案件对关联自然人 KYC 案件的明确依赖';

-- ===== t_dd_requirement =====
ALTER TABLE `t_dd_requirement`
  ADD COLUMN `waived_by` BIGINT NULL,
  ADD COLUMN `waived_reason_code` VARCHAR(64) NULL,
  ADD COLUMN `waived_note` VARCHAR(1000) NULL,
  ADD COLUMN `waived_time` DATETIME(6) NULL,
  ADD COLUMN `reused_from_case_id` BIGINT NULL;
ALTER TABLE `t_dd_requirement`
  MODIFY COLUMN `requirement_id` BIGINT NOT NULL COMMENT '案件材料要求的稳定业务标识。' FIRST,
  MODIFY COLUMN `case_id` BIGINT NOT NULL COMMENT '尽调案件的稳定业务标识。' AFTER `requirement_id`,
  MODIFY COLUMN `program_requirement_id` BIGINT NOT NULL COMMENT '程序材料模板的稳定业务标识。' AFTER `case_id`,
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '要求状态的稳定业务代码。允许值：PENDING、RECEIVED、ACCEPTED、REJECTED、WAIVED、EXPIRED。' AFTER `program_requirement_id`,
  MODIFY COLUMN `due_time` DATETIME(6) NULL COMMENT '截止时间对应的 UTC 微秒时间。' AFTER `status`,
  MODIFY COLUMN `waived_by` BIGINT NULL COMMENT '豁免该要求的 Platform OPERATIONS Account ID；未豁免为空。' AFTER `due_time`,
  MODIFY COLUMN `waived_reason_code` VARCHAR(64) NULL COMMENT '豁免原因的稳定业务代码。取值来源：Compliance 豁免原因目录；非固定枚举。' AFTER `waived_by`,
  MODIFY COLUMN `waived_note` VARCHAR(1000) NULL COMMENT '豁免说明，内部可见。' AFTER `waived_reason_code`,
  MODIFY COLUMN `waived_time` DATETIME(6) NULL COMMENT '豁免时间对应的 UTC 微秒时间。' AFTER `waived_note`,
  MODIFY COLUMN `reused_from_case_id` BIGINT NULL COMMENT '退回重提时从上一轮案件复用已接受材料的来源案件 ID；非复用为空。' AFTER `waived_time`,
  MODIFY COLUMN `version` BIGINT NOT NULL COMMENT '要求并发版本的长整数值。' AFTER `reused_from_case_id`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。' AFTER `version`,
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。' AFTER `create_time`,
  COMMENT = '保存由尽调程序实例化到具体案件的材料要求、办理状态、豁免与复用来源';

-- ===== t_dd_evidence_ref =====
ALTER TABLE `t_dd_evidence_ref`
  ADD COLUMN `file_address` VARCHAR(64) NOT NULL DEFAULT '',
  ADD COLUMN `document_type` VARCHAR(64) NOT NULL DEFAULT '',
  ADD COLUMN `issue_date` DATE NULL,
  ADD COLUMN `expiry_date` DATE NULL,
  ADD COLUMN `reject_reason_code` VARCHAR(64) NULL,
  ADD COLUMN `review_note` VARCHAR(1000) NULL,
  ADD COLUMN `reviewer_id` BIGINT NULL,
  ADD COLUMN `reviewed_time` DATETIME(6) NULL,
  ADD COLUMN `reused_from_case_id` BIGINT NULL;
UPDATE `t_dd_evidence_ref` e
   SET e.`file_address` = CONCAT('/files/v1/', e.`file_id`),
       e.`document_type` = COALESCE((SELECT MIN(d.`document_type`) FROM `t_dd_requirement` r
                                       JOIN `t_dd_program_requirement_doc_type` d ON d.`program_requirement_id` = r.`program_requirement_id`
                                      WHERE r.`requirement_id` = e.`requirement_id`), 'LEGACY_DOCUMENT');
ALTER TABLE `t_dd_evidence_ref`
  DROP INDEX `uk_dd_evidence_req_file`,
  DROP INDEX `idx_dd_evidence_subject`,
  DROP COLUMN `file_id`,
  MODIFY COLUMN `evidence_id` BIGINT NOT NULL COMMENT '尽调证据引用的稳定业务标识。' FIRST,
  MODIFY COLUMN `requirement_id` BIGINT NOT NULL COMMENT '材料要求的稳定业务标识。' AFTER `evidence_id`,
  MODIFY COLUMN `case_subject_id` BIGINT NOT NULL COMMENT '案件主体的稳定业务标识。' AFTER `requirement_id`,
  MODIFY COLUMN `file_address` VARCHAR(64) NOT NULL COMMENT 'Platform 文件服务签发的稳定逻辑地址 /files/v1/{fileId}（FileAddress），不是下载链接。' AFTER `case_subject_id`,
  MODIFY COLUMN `content_hash` CHAR(64) NOT NULL COMMENT '文件内容哈希的 SHA-256 小写十六进制值。' AFTER `file_address`,
  MODIFY COLUMN `document_type` VARCHAR(64) NOT NULL COMMENT '提交的材料类型稳定业务代码，必须属于材料槽允许类型。' AFTER `content_hash`,
  MODIFY COLUMN `issue_date` DATE NULL COMMENT 'Client 填报的材料签发日期；未提供为空。' AFTER `document_type`,
  MODIFY COLUMN `expiry_date` DATE NULL COMMENT 'Client 填报的材料到期日期；未提供为空。' AFTER `issue_date`,
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '证据状态的稳定业务代码。允许值：RECEIVED、ACCEPTED、REJECTED、SUPERSEDED。' AFTER `expiry_date`,
  MODIFY COLUMN `reject_reason_code` VARCHAR(64) NULL COMMENT '拒绝原因的稳定业务代码。允许值：DOCUMENT_EXPIRED、DOCUMENT_TOO_OLD、DOCUMENT_UNREADABLE、DOCUMENT_MISMATCH_NAME、DOCUMENT_MISMATCH_ADDRESS、DOCUMENT_TYPE_INVALID、DOCUMENT_FRAUDULENT、OTHER；非 REJECTED 为空。' AFTER `status`,
  MODIFY COLUMN `review_note` VARCHAR(1000) NULL COMMENT '核验人填写的内部备注。' AFTER `reject_reason_code`,
  MODIFY COLUMN `reviewer_id` BIGINT NULL COMMENT '核验人的 Platform OPERATIONS Account ID；未核验为空。' AFTER `review_note`,
  MODIFY COLUMN `reviewed_time` DATETIME(6) NULL COMMENT '核验时间对应的 UTC 微秒时间。' AFTER `reviewer_id`,
  MODIFY COLUMN `reused_from_case_id` BIGINT NULL COMMENT '从上一轮案件复用的来源案件 ID；非复用为空。' AFTER `reviewed_time`,
  MODIFY COLUMN `version` BIGINT NOT NULL COMMENT '证据并发版本的长整数值。' AFTER `reused_from_case_id`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。' AFTER `version`,
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。' AFTER `create_time`,
  ADD UNIQUE KEY `uk_dd_evidence_req_file` (`requirement_id`, `file_address`) COMMENT '保证业务唯一性：requirement_id、file_address',
  ADD KEY `idx_dd_evidence_subject` (`case_subject_id`) COMMENT '支持访问路径：case_subject_id',
  COMMENT = '保存案件主体在材料要求下提交的文件服务地址、材料属性及核验结果';

-- ===== t_dd_check =====
ALTER TABLE `t_dd_check`
  ADD COLUMN `case_id` BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN `check_mode` VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
  ADD COLUMN `status` VARCHAR(16) NOT NULL DEFAULT 'COMPLETED',
  ADD COLUMN `outcome` VARCHAR(16) NULL,
  ADD COLUMN `reason_code` VARCHAR(64) NULL,
  ADD COLUMN `evidence_ref` VARCHAR(128) NULL,
  ADD COLUMN `note` VARCHAR(1000) NULL,
  ADD COLUMN `reviewer_id` BIGINT NULL,
  ADD COLUMN `reviewed_time` DATETIME(6) NULL,
  ADD COLUMN `reused_from_case_id` BIGINT NULL,
  ADD COLUMN `operated_by` VARCHAR(16) NULL,
  ADD COLUMN `on_behalf_of_partner_id` BIGINT NULL,
  ADD COLUMN `version` BIGINT NOT NULL DEFAULT 1,
  ADD COLUMN `update_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
UPDATE `t_dd_check` c JOIN `t_dd_case_subject` s ON s.`case_subject_id` = c.`case_subject_id`
   SET c.`case_id` = s.`case_id`, c.`check_mode` = 'MANUAL', c.`status` = 'COMPLETED',
       c.`outcome` = CASE c.`result_code` WHEN 'PASS' THEN 'PASS' WHEN 'FAIL' THEN 'FAIL' ELSE 'REVIEW' END,
       c.`reason_code` = c.`result_code`, c.`reviewed_time` = c.`performed_time`, c.`version` = 1, c.`update_time` = c.`create_time`;
ALTER TABLE `t_dd_check`
  DROP INDEX `idx_dd_check_subject`,
  DROP INDEX `idx_dd_check_program`,
  DROP COLUMN `result_code`,
  DROP COLUMN `result_vault_token`,
  DROP COLUMN `performed_time`,
  MODIFY COLUMN `check_id` BIGINT NOT NULL COMMENT '尽调检查的稳定业务标识。' FIRST,
  MODIFY COLUMN `case_id` BIGINT NOT NULL COMMENT '尽调案件的稳定业务标识。' AFTER `check_id`,
  MODIFY COLUMN `case_subject_id` BIGINT NOT NULL COMMENT '案件主体的稳定业务标识。' AFTER `case_id`,
  MODIFY COLUMN `program_check_id` BIGINT NOT NULL COMMENT '程序检查模板的稳定业务标识。' AFTER `case_subject_id`,
  MODIFY COLUMN `check_mode` VARCHAR(16) NOT NULL COMMENT '执行方式的稳定业务代码。允许值：MANUAL、AUTOMATED。' AFTER `program_check_id`,
  MODIFY COLUMN `provider_code` VARCHAR(64) NOT NULL COMMENT '检查提供方的稳定业务代码；MANUAL 固定为 MANUAL，AUTOMATED 为 channel-connector 服务渠道编码。' AFTER `check_mode`,
  MODIFY COLUMN `request_ref` VARCHAR(128) NOT NULL COMMENT '提供方请求引用；MANUAL 为检查 ID 文本。' AFTER `provider_code`,
  MODIFY COLUMN `status` VARCHAR(16) NOT NULL COMMENT '检查状态的稳定业务代码。允许值：REQUESTED、RUNNING、COMPLETED、FAILED、UNKNOWN。' AFTER `request_ref`,
  MODIFY COLUMN `outcome` VARCHAR(16) NULL COMMENT '检查结论的稳定业务代码。允许值：PASS、FAIL、REVIEW、NOT_EXECUTED；未出结论为空。' AFTER `status`,
  MODIFY COLUMN `reason_code` VARCHAR(64) NULL COMMENT '结论原因的稳定业务代码。取值来源：Compliance 检查原因目录；非固定枚举。' AFTER `outcome`,
  MODIFY COLUMN `evidence_ref` VARCHAR(128) NULL COMMENT '人工检查录入的名单版本或查询编号；可空。' AFTER `reason_code`,
  MODIFY COLUMN `note` VARCHAR(1000) NULL COMMENT '录入人填写的内部备注。' AFTER `evidence_ref`,
  MODIFY COLUMN `reviewer_id` BIGINT NULL COMMENT '录入结论的 Platform OPERATIONS Account ID。' AFTER `note`,
  MODIFY COLUMN `reviewed_time` DATETIME(6) NULL COMMENT '录入结论对应的 UTC 微秒时间。' AFTER `reviewer_id`,
  MODIFY COLUMN `valid_until` DATETIME(6) NULL COMMENT '结论有效截止的 UTC 微秒时间；模板未设有效天数为空。' AFTER `reviewed_time`,
  MODIFY COLUMN `reused_from_case_id` BIGINT NULL COMMENT '从上一轮案件复用 PASS 结论的来源案件 ID；非复用为空。' AFTER `valid_until`,
  MODIFY COLUMN `operated_by` VARCHAR(16) NULL COMMENT '操作方的稳定业务代码。允许值：PLATFORM（平台运营，含代 PayFac 操作）、PARTNER（PayFac 自有合规人员，本期不开放）；系统动作为空。' AFTER `reused_from_case_id`,
  MODIFY COLUMN `on_behalf_of_partner_id` BIGINT NULL COMMENT '平台运营代 PayFac 操作时的责任 PayFac Local Partner ID（client-master t_local_partner 的稳定 ID）；非代操作为空。' AFTER `operated_by`,
  MODIFY COLUMN `version` BIGINT NOT NULL COMMENT '检查并发版本的长整数值。' AFTER `on_behalf_of_partner_id`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。' AFTER `version`,
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。' AFTER `create_time`,
  ADD KEY `idx_dd_check_case` (`case_id`) COMMENT '支持访问路径：case_id',
  ADD KEY `idx_dd_check_program` (`program_check_id`) COMMENT '支持访问路径：program_check_id',
  COMMENT = '保存案件主体的一项人工或自动检查的状态与结论';

-- ===== t_screening_run =====
ALTER TABLE `t_screening_run`
  ADD COLUMN `case_id` BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN `trigger_source` VARCHAR(16) NOT NULL DEFAULT 'CASE',
  ADD COLUMN `trigger_ref` VARCHAR(64) NOT NULL DEFAULT '-',
  ADD COLUMN `execution_ref` VARCHAR(128) NULL;
UPDATE `t_screening_run` r JOIN `t_dd_case_subject` s ON s.`case_subject_id` = r.`case_subject_id`
   SET r.`case_id` = s.`case_id`, r.`trigger_source` = 'CASE', r.`trigger_ref` = '-';
ALTER TABLE `t_screening_run`
  DROP INDEX `idx_screen_subject`,
  DROP INDEX `idx_screen_program`,
  MODIFY COLUMN `screening_run_id` BIGINT NOT NULL COMMENT '筛查执行的稳定业务标识。' FIRST,
  MODIFY COLUMN `case_id` BIGINT NOT NULL COMMENT '尽调案件的稳定业务标识。' AFTER `screening_run_id`,
  MODIFY COLUMN `case_subject_id` BIGINT NOT NULL COMMENT '案件主体的稳定业务标识。' AFTER `case_id`,
  MODIFY COLUMN `program_screening_id` BIGINT NOT NULL COMMENT '程序筛查模板的稳定业务标识；一次执行只对应一个筛查类型。' AFTER `case_subject_id`,
  MODIFY COLUMN `provider_code` VARCHAR(64) NOT NULL COMMENT '执行筛查的 channel-connector 服务渠道编码（例如 S_SCREENING_MOCK）。' AFTER `program_screening_id`,
  MODIFY COLUMN `request_ref` VARCHAR(128) NOT NULL COMMENT '发给提供方的请求引用（clientRef），全局唯一。' AFTER `provider_code`,
  MODIFY COLUMN `trigger_source` VARCHAR(16) NOT NULL COMMENT '触发来源的稳定业务代码。允许值：CASE（立案）、RERUN（审核员重跑）。' AFTER `request_ref`,
  MODIFY COLUMN `trigger_ref` VARCHAR(64) NOT NULL COMMENT '触发本次执行的补偿事件子类型：立案为 -，重跑为重跑请求 ID。' AFTER `trigger_source`,
  MODIFY COLUMN `execution_ref` VARCHAR(128) NULL COMMENT '提供方返回的执行引用（runRef）；未返回为空。' AFTER `trigger_ref`,
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '筛查状态的稳定业务代码。允许值：STARTED、SUCCEEDED、FAILED、TIMED_OUT（结果未知）。' AFTER `execution_ref`,
  MODIFY COLUMN `started_time` DATETIME(6) NOT NULL COMMENT '开始时间对应的 UTC 微秒时间。' AFTER `status`,
  MODIFY COLUMN `completed_time` DATETIME(6) NULL COMMENT '完成时间对应的 UTC 微秒时间。' AFTER `started_time`,
  MODIFY COLUMN `version` BIGINT NOT NULL COMMENT '筛查执行并发版本的长整数值。' AFTER `completed_time`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。' AFTER `version`,
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。' AFTER `create_time`,
  ADD KEY `idx_screen_case` (`case_id`) COMMENT '支持访问路径：case_id',
  ADD KEY `idx_screen_subject` (`case_subject_id`, `program_screening_id`) COMMENT '支持访问路径：case_subject_id、program_screening_id',
  COMMENT = '保存一个案件主体一种筛查类型的一次服务渠道执行生命周期';

-- ===== t_screening_hit =====
ALTER TABLE `t_screening_hit`
  ADD COLUMN `entity_type` VARCHAR(16) NOT NULL DEFAULT 'OTHER',
  ADD COLUMN `risk_level` VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE `t_screening_hit`
  DROP COLUMN `name_vault_token`,
  MODIFY COLUMN `hit_id` BIGINT NOT NULL COMMENT '筛查命中的稳定业务标识。' FIRST,
  MODIFY COLUMN `screening_run_id` BIGINT NOT NULL COMMENT '筛查执行的稳定业务标识。' AFTER `hit_id`,
  MODIFY COLUMN `external_hit_ref` VARCHAR(128) NOT NULL COMMENT '提供方命中引用；审核员凭此在提供方查看原文。' AFTER `screening_run_id`,
  MODIFY COLUMN `entity_type` VARCHAR(16) NOT NULL COMMENT '命中实体类型的稳定业务代码。允许值：PERSON、ORGANIZATION、VESSEL、OTHER。' AFTER `external_hit_ref`,
  MODIFY COLUMN `match_type` VARCHAR(64) NOT NULL COMMENT '匹配方式的稳定业务代码（提供方返回的首个匹配方式，例如 NAME_EXACT、NAME_FUZZY）；非固定枚举。' AFTER `entity_type`,
  MODIFY COLUMN `match_score` DECIMAL(18,10) NOT NULL COMMENT '匹配分数，0 到 100。' AFTER `match_type`,
  MODIFY COLUMN `risk_level` VARCHAR(16) NOT NULL COMMENT '命中风险等级的稳定业务代码。允许值：LOW、MEDIUM、HIGH、UNKNOWN。' AFTER `match_score`,
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '命中状态的稳定业务代码。允许值：OPEN、RESOLVED、SUPERSEDED（同主体同类型新执行成功后取代）。' AFTER `risk_level`,
  MODIFY COLUMN `version` BIGINT NOT NULL COMMENT '命中并发版本的长整数值。' AFTER `status`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。' AFTER `version`,
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。' AFTER `create_time`,
  COMMENT = '保存一次筛查返回的外部命中及其当前处置状态；不保存命中实体姓名';

-- ===== t_screening_resolution =====
ALTER TABLE `t_screening_resolution`
  ADD COLUMN `note` VARCHAR(1000) NULL,
  ADD COLUMN `operated_by` VARCHAR(16) NULL,
  ADD COLUMN `on_behalf_of_partner_id` BIGINT NULL;
ALTER TABLE `t_screening_resolution`
  DROP COLUMN `evidence_file_id`,
  DROP COLUMN `evidence_content_hash`,
  MODIFY COLUMN `resolution_id` BIGINT NOT NULL COMMENT '筛查命中处置的稳定业务标识。' FIRST,
  MODIFY COLUMN `hit_id` BIGINT NOT NULL COMMENT '筛查命中的稳定业务标识。' AFTER `resolution_id`,
  MODIFY COLUMN `resolution_version` INT NOT NULL COMMENT '同一命中的处置版本，从 1 递增。' AFTER `hit_id`,
  MODIFY COLUMN `resolution` VARCHAR(64) NOT NULL COMMENT '处置结论的稳定业务代码。允许值：NO_MATCH、FALSE_POSITIVE、POTENTIAL_MATCH、TRUE_POSITIVE_ACCEPT、TRUE_POSITIVE_REJECT、INSUFFICIENT_EVIDENCE；后两类以外的前四类之一关闭命中（POTENTIAL_MATCH 与 INSUFFICIENT_EVIDENCE 不关闭）。' AFTER `resolution_version`,
  MODIFY COLUMN `reason_code` VARCHAR(64) NOT NULL COMMENT '处置原因的稳定业务代码。取值来源：Compliance 筛查处置原因目录；非固定枚举。' AFTER `resolution`,
  MODIFY COLUMN `note` VARCHAR(1000) NULL COMMENT '处置人填写的内部备注。' AFTER `reason_code`,
  MODIFY COLUMN `reviewer_id` BIGINT NOT NULL COMMENT '处置人的 Platform OPERATIONS Account ID。' AFTER `note`,
  MODIFY COLUMN `operated_by` VARCHAR(16) NULL COMMENT '操作方的稳定业务代码。允许值：PLATFORM（平台运营，含代 PayFac 操作）、PARTNER（PayFac 自有合规人员，本期不开放）；系统动作为空。' AFTER `reviewer_id`,
  MODIFY COLUMN `on_behalf_of_partner_id` BIGINT NULL COMMENT '平台运营代 PayFac 操作时的责任 PayFac Local Partner ID（client-master t_local_partner 的稳定 ID）；非代操作为空。' AFTER `operated_by`,
  MODIFY COLUMN `resolved_time` DATETIME(6) NOT NULL COMMENT '处置时间对应的 UTC 微秒时间。' AFTER `on_behalf_of_partner_id`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。' AFTER `resolved_time`,
  COMMENT = '保存筛查命中每一次不可变人工处置';

-- ===== t_dd_case_state_change =====
ALTER TABLE `t_dd_case_state_change`
  ADD COLUMN `operated_by` VARCHAR(16) NULL,
  ADD COLUMN `on_behalf_of_partner_id` BIGINT NULL;
ALTER TABLE `t_dd_case_state_change`
  MODIFY COLUMN `change_id` BIGINT NOT NULL COMMENT '尽调案件状态变更的稳定业务标识。' FIRST,
  MODIFY COLUMN `case_id` BIGINT NOT NULL COMMENT '尽调案件的稳定业务标识。' AFTER `change_id`,
  MODIFY COLUMN `from_status` VARCHAR(64) NULL COMMENT '原状态的稳定业务代码；立案为空。允许值：DRAFT、SUBMITTED、IN_REVIEW、WAITING_EVIDENCE、PENDING_DEPENDENCY、RETURNED、APPROVED、REJECTED、WITHDRAWN、EXPIRED。' AFTER `case_id`,
  MODIFY COLUMN `to_status` VARCHAR(64) NOT NULL COMMENT '新状态的稳定业务代码。允许值：DRAFT、SUBMITTED、IN_REVIEW、WAITING_EVIDENCE、PENDING_DEPENDENCY、RETURNED、APPROVED、REJECTED、WITHDRAWN、EXPIRED。' AFTER `from_status`,
  MODIFY COLUMN `action_type` VARCHAR(64) NOT NULL COMMENT '动作的稳定业务代码，例如 REGISTER、START_REVIEW、RETURN、APPROVE、REJECT、WITHDRAW、WITHDRAW_WITH_PARENT、EXPIRE。取值来源：Compliance 受控代码目录；非固定枚举。' AFTER `to_status`,
  MODIFY COLUMN `reason_code` VARCHAR(64) NULL COMMENT '迁移原因的稳定业务代码。取值来源：Compliance 案件状态原因目录；非固定枚举。' AFTER `action_type`,
  MODIFY COLUMN `trigger_type` VARCHAR(64) NOT NULL COMMENT '触发类型的稳定业务代码，例如 CLIENT_SUBMISSION、OPERATIONS_COMMAND、CLIENT_WITHDRAWAL、SYSTEM_TIMER。非固定枚举。' AFTER `reason_code`,
  MODIFY COLUMN `trigger_id` VARCHAR(128) NOT NULL COMMENT '触发标识的业务值（提交编号、操作人或定时扫描标识）。' AFTER `trigger_type`,
  MODIFY COLUMN `actor_id` BIGINT NULL COMMENT '操作人的稳定业务标识；系统动作为空。' AFTER `trigger_id`,
  MODIFY COLUMN `operated_by` VARCHAR(16) NULL COMMENT '操作方的稳定业务代码。允许值：PLATFORM（平台运营，含代 PayFac 操作）、PARTNER（PayFac 自有合规人员，本期不开放）；系统动作为空。' AFTER `actor_id`,
  MODIFY COLUMN `on_behalf_of_partner_id` BIGINT NULL COMMENT '平台运营代 PayFac 操作时的责任 PayFac Local Partner ID（client-master t_local_partner 的稳定 ID）；非代操作为空。' AFTER `operated_by`,
  MODIFY COLUMN `aggregate_version` BIGINT NOT NULL COMMENT '迁移后案件版本的长整数值。' AFTER `on_behalf_of_partner_id`,
  MODIFY COLUMN `occurred_time` DATETIME(6) NOT NULL COMMENT '发生时间对应的 UTC 微秒时间。' AFTER `aggregate_version`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。' AFTER `occurred_time`,
  COMMENT = '追加保存尽调案件每次状态迁移或审核动作、触发来源、操作方和聚合版本';

-- ===== t_qualification_decision =====
ALTER TABLE `t_qualification_decision`
  ADD COLUMN `tier` VARCHAR(32) NULL,
  ADD COLUMN `risk_rating` VARCHAR(16) NULL,
  ADD COLUMN `jurisdiction_code` VARCHAR(16) NULL,
  ADD COLUMN `responsible_partner_id` BIGINT NULL,
  ADD COLUMN `recommended_by` BIGINT NULL,
  ADD COLUMN `recommended_time` DATETIME(6) NULL,
  ADD COLUMN `four_eyes_waived` BOOLEAN NULL,
  ADD COLUMN `operated_by` VARCHAR(16) NULL,
  ADD COLUMN `on_behalf_of_partner_id` BIGINT NULL;
ALTER TABLE `t_qualification_decision`
  MODIFY COLUMN `decision_id` BIGINT NOT NULL COMMENT '资格决定的稳定业务标识。' FIRST,
  MODIFY COLUMN `case_id` BIGINT NULL COMMENT '尽调案件的稳定业务标识；运营手工登记或撤销（decision_source=MANUAL）的决定不关联案件，为空。' AFTER `decision_id`,
  MODIFY COLUMN `decision_version` INT NULL COMMENT '旧资格修订合同的真实版本；新 Client 每个 Case 只有一个最终决定，使用 Decision ID，本列为空。' AFTER `case_id`,
  MODIFY COLUMN `role_type` VARCHAR(64) NOT NULL COMMENT '角色类型的稳定业务代码。允许值：CLIENT、LOCAL_PARTNER、OPERATING_ENTITY、RELATED_PERSON。' AFTER `decision_version`,
  MODIFY COLUMN `role_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '角色的稳定业务标识：OPERATING_ENTITY 存 entity_no（两位大写字母加三位数字，如 AE001），CLIENT、LOCAL_PARTNER、RELATED_PERSON 存十进制 ID 文本。' AFTER `role_type`,
  MODIFY COLUMN `qualification_type` VARCHAR(64) NOT NULL COMMENT '资格类型的稳定业务代码。允许值：INDIVIDUAL_KYC、ORGANIZATION_KYB、PARTNER_DUE_DILIGENCE、ENTITY_UNDERWRITING、RELATED_PERSON_KYC。' AFTER `role_id`,
  MODIFY COLUMN `decision_evaluation_id` BIGINT NULL COMMENT '规则评估的稳定业务标识。' AFTER `qualification_type`,
  MODIFY COLUMN `decision_source` VARCHAR(64) NOT NULL COMMENT '决定来源的稳定业务代码。允许值：AUTOMATED、REVIEWER、OVERRIDE、MANUAL。' AFTER `decision_evaluation_id`,
  MODIFY COLUMN `recommended_decision` VARCHAR(64) NULL COMMENT '建议结论的稳定业务代码。允许值：APPROVE、REVIEW、REJECT。' AFTER `decision_source`,
  MODIFY COLUMN `outcome` VARCHAR(64) NOT NULL COMMENT '资格结论的稳定业务代码。允许值：APPROVED、REJECTED、REVOKED、EXPIRED。' AFTER `recommended_decision`,
  MODIFY COLUMN `override_reason_code` VARCHAR(64) NULL COMMENT '覆盖原因的稳定业务代码。取值来源：Risk 资格覆盖原因目录；非固定枚举。' AFTER `outcome`,
  MODIFY COLUMN `basis_note` VARCHAR(1000) NULL COMMENT '运营手工登记或撤销资格的依据说明，最多 1000 字符；仅 MANUAL 决定填写，其他来源为空。' AFTER `override_reason_code`,
  MODIFY COLUMN `tier` VARCHAR(32) NULL COMMENT '决定时冻结的尽调档次。允许值：SIMPLIFIED、STANDARD、ENHANCED；MANUAL 决定为空。' AFTER `basis_note`,
  MODIFY COLUMN `risk_rating` VARCHAR(16) NULL COMMENT '决定时冻结的客户风险评级。允许值：LOW、MEDIUM、HIGH；MANUAL 决定或未评级为空。' AFTER `tier`,
  MODIFY COLUMN `jurisdiction_code` VARCHAR(16) NULL COMMENT '决定时冻结的辖区代码（client-master t_jurisdiction.jurisdiction_code）；MANUAL 决定为空。' AFTER `risk_rating`,
  MODIFY COLUMN `responsible_partner_id` BIGINT NULL COMMENT '决定时冻结的责任 PayFac Local Partner ID；平台自营或 MANUAL 决定为空。' AFTER `jurisdiction_code`,
  MODIFY COLUMN `recommended_by` BIGINT NULL COMMENT '推荐人的 Platform OPERATIONS Account ID；四眼时必须与 decided_by 不同；MANUAL 决定为空。' AFTER `responsible_partner_id`,
  MODIFY COLUMN `recommended_time` DATETIME(6) NULL COMMENT '推荐时间对应的 UTC 微秒时间。' AFTER `recommended_by`,
  MODIFY COLUMN `four_eyes_waived` BOOLEAN NULL COMMENT '程序版本不要求四眼时为 1，要求四眼时为 0；MANUAL 决定为空。' AFTER `recommended_time`,
  MODIFY COLUMN `operated_by` VARCHAR(16) NULL COMMENT '操作方的稳定业务代码。允许值：PLATFORM、PARTNER；历史行为空。' AFTER `four_eyes_waived`,
  MODIFY COLUMN `on_behalf_of_partner_id` BIGINT NULL COMMENT '平台运营代 PayFac 决定时的责任 PayFac Local Partner ID；非代操作为空。' AFTER `operated_by`,
  MODIFY COLUMN `valid_from` DATETIME(6) NOT NULL COMMENT '有效开始时间对应的 UTC 微秒时间。' AFTER `on_behalf_of_partner_id`,
  MODIFY COLUMN `valid_to` DATETIME(6) NULL COMMENT '有效结束时间对应的 UTC 微秒时间。' AFTER `valid_from`,
  MODIFY COLUMN `decided_by` BIGINT NULL COMMENT '决定人的稳定业务标识。' AFTER `valid_to`,
  MODIFY COLUMN `decided_time` DATETIME(6) NOT NULL COMMENT '决定时间对应的 UTC 微秒时间。' AFTER `decided_by`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。' AFTER `decided_time`,
  COMMENT = '保存一个尽调案件形成的不可变角色资格决定版本';

-- ===== t_role_qualification =====
ALTER TABLE `t_role_qualification`
  ADD COLUMN `tier` VARCHAR(32) NULL,
  ADD COLUMN `risk_rating` VARCHAR(16) NULL,
  ADD COLUMN `jurisdiction_code` VARCHAR(16) NULL,
  ADD COLUMN `responsible_partner_id` BIGINT NULL,
  ADD COLUMN `review_interval_months` INT NULL,
  ADD COLUMN `last_review_time` DATETIME(6) NULL;
ALTER TABLE `t_role_qualification`
  MODIFY COLUMN `qualification_id` BIGINT NOT NULL COMMENT '角色资格投影的稳定业务标识。' FIRST,
  MODIFY COLUMN `role_type` VARCHAR(64) NOT NULL COMMENT '角色类型的稳定业务代码。允许值：CLIENT、LOCAL_PARTNER、OPERATING_ENTITY、RELATED_PERSON。' AFTER `qualification_id`,
  MODIFY COLUMN `role_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '角色的稳定业务标识：OPERATING_ENTITY 存 entity_no（两位大写字母加三位数字，如 AE001），CLIENT、LOCAL_PARTNER、RELATED_PERSON 存十进制 ID 文本。' AFTER `role_type`,
  MODIFY COLUMN `qualification_type` VARCHAR(64) NOT NULL COMMENT '资格类型的稳定业务代码。允许值：INDIVIDUAL_KYC、ORGANIZATION_KYB、PARTNER_DUE_DILIGENCE、ENTITY_UNDERWRITING、RELATED_PERSON_KYC。' AFTER `role_id`,
  MODIFY COLUMN `current_decision_id` BIGINT NOT NULL COMMENT '当前资格决定的稳定业务标识。' AFTER `qualification_type`,
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '资格状态的稳定业务代码。允许值：APPROVED、REJECTED、REVOKED、EXPIRED。' AFTER `current_decision_id`,
  MODIFY COLUMN `valid_from` DATETIME(6) NOT NULL COMMENT '有效开始时间对应的 UTC 微秒时间。' AFTER `status`,
  MODIFY COLUMN `valid_to` DATETIME(6) NULL COMMENT '有效结束时间对应的 UTC 微秒时间。' AFTER `valid_from`,
  MODIFY COLUMN `review_frequency_code` VARCHAR(64) NULL COMMENT '复审频率的稳定业务代码。取值来源：Risk 持续复审频率目录；非固定枚举。' AFTER `valid_to`,
  MODIFY COLUMN `next_review_time` DATETIME(6) NULL COMMENT '下次复审时间对应的 UTC 微秒时间。' AFTER `review_frequency_code`,
  MODIFY COLUMN `review_status` VARCHAR(64) NOT NULL COMMENT '复审状态的稳定业务代码。允许值：NOT_REQUIRED、ACTIVE、PAUSED、COMPLETED。' AFTER `next_review_time`,
  MODIFY COLUMN `tier` VARCHAR(32) NULL COMMENT '当前决定冻结的尽调档次；MANUAL 决定为空。' AFTER `review_status`,
  MODIFY COLUMN `risk_rating` VARCHAR(16) NULL COMMENT '当前决定冻结的客户风险评级 LOW、MEDIUM、HIGH；MANUAL 决定为空。' AFTER `tier`,
  MODIFY COLUMN `jurisdiction_code` VARCHAR(16) NULL COMMENT '当前决定冻结的辖区代码；MANUAL 决定为空。' AFTER `risk_rating`,
  MODIFY COLUMN `responsible_partner_id` BIGINT NULL COMMENT '当前决定冻结的责任 PayFac Local Partner ID；平台自营或 MANUAL 决定为空。' AFTER `jurisdiction_code`,
  MODIFY COLUMN `review_interval_months` INT NULL COMMENT '当前决定适用的复审间隔月数；不复审为空。' AFTER `responsible_partner_id`,
  MODIFY COLUMN `last_review_time` DATETIME(6) NULL COMMENT '最近一次案件决定对应的 UTC 微秒时间；MANUAL 决定为空。' AFTER `review_interval_months`,
  MODIFY COLUMN `version` BIGINT NOT NULL COMMENT '资格并发版本的长整数值。' AFTER `last_review_time`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。' AFTER `version`,
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。' AFTER `create_time`,
  COMMENT = '保存每个角色和资格类型当前生效决定、有效期及持续复审调度';
