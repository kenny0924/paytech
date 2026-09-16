-- REVIEW CANDIDATE. Do not execute before environment-specific approval.

-- Stop writers; back up; compare actual SHOW CREATE with the baseline;

-- require the complete owner-local reference closure to be empty.

-- No rollback by deleting history. See ../../migration-plan.md.

-- Do not run with mysql --force. DDL auto-commits.

SET NAMES utf8mb4;

ALTER TABLE `t_party_profile`
  DROP INDEX `uk_party_prof_ver`,
  DROP INDEX `uk_party_prof_active`,
  DROP INDEX `idx_party_profile_dfae5819`,
  DROP COLUMN `active_slot`,
  DROP COLUMN `effective_from`,
  DROP COLUMN `effective_to`,
  DROP COLUMN `hash_schema_version`,
  DROP COLUMN `profile_status`,
  DROP COLUMN `version_no`,
  MODIFY COLUMN `profile_id` BIGINT NOT NULL COMMENT '单份 Party 资料的稳定标识，不是版本标识。' FIRST,
  MODIFY COLUMN `party_id` BIGINT NOT NULL COMMENT '自然人或机构法律身份根的内部唯一标识。' AFTER `profile_id`,
  MODIFY COLUMN `content_hash` CHAR(64) NULL COMMENT '批准资料内容摘要，保留现有 Checkout 法律资料消费者；未批准为空，批准后不可变。' AFTER `party_id`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。' AFTER `content_hash`,
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。' AFTER `create_time`,
  ADD COLUMN `draft_application_id` BIGINT NULL COMMENT '当前可编辑 Client 入驻申请；未批准资料只能由该申请编辑；批准后清空。' AFTER `update_time`,
  ADD COLUMN `approved_time` DATETIME(6) NULL COMMENT '资料首次批准并永久锁定的 UTC 时间；未批准为空。' AFTER `draft_application_id`,
  ADD UNIQUE KEY `uk_party_profile_party` (`party_id`) COMMENT 'Unique index for party_profile on party_id',
  ADD KEY `idx_party_profile_80cc2659` (`draft_application_id`) COMMENT 'Access index for party_profile on draft_application_id',
  COMMENT = '法律主体资料；uk_party_profile_party(party_id)；每个 Party 只保留一份资料；草稿由 draft_application_id 指定申请编辑；approved_time 非空后永久只读；应用校验，不创建 CHECK/FK';

ALTER TABLE `t_individual_profile`
  MODIFY COLUMN `profile_id` BIGINT NOT NULL COMMENT 'Party 资料头及其类型明细的内部唯一标识。' FIRST,
  MODIFY COLUMN `given_name` VARCHAR(64) NULL COMMENT '个人法定姓名中的名字。' AFTER `profile_id`,
  MODIFY COLUMN `middle_name` VARCHAR(64) NULL COMMENT '个人法定姓名中的可选中间名。' AFTER `given_name`,
  MODIFY COLUMN `family_name` VARCHAR(64) NULL COMMENT '个人姓氏，即法定姓名中的姓氏部分。' AFTER `middle_name`,
  MODIFY COLUMN `birth_date` DATE NULL COMMENT '个人法定出生 LocalDate 日期事实，不包含时区或时刻。' AFTER `family_name`,
  MODIFY COLUMN `nationality_country` CHAR(2) NULL COMMENT '个人国籍的 ISO 3166-1 alpha-2 国家代码。取值来源：ISO 3166-1 alpha-2；非固定枚举。' AFTER `birth_date`,
  MODIFY COLUMN `residence_country` CHAR(2) NULL COMMENT '个人主要居住地的 ISO 3166-1 alpha-2 国家代码。取值来源：ISO 3166-1 alpha-2；非固定枚举。' AFTER `nationality_country`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。' AFTER `residence_country`,
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。' AFTER `create_time`,
  COMMENT = '个人资料；个人姓名、出生日期和国家允许部分草稿保存；提交时按入驻规则完整校验；批准后只读；应用校验，不创建 CHECK/FK';

ALTER TABLE `t_organization_profile`
  MODIFY COLUMN `profile_id` BIGINT NOT NULL COMMENT 'Party 资料头及其类型明细的内部唯一标识。' FIRST,
  MODIFY COLUMN `legal_name` VARCHAR(64) NULL COMMENT '机构在注册司法辖区登记的完整法定名称。' AFTER `profile_id`,
  MODIFY COLUMN `trade_name` VARCHAR(64) NULL COMMENT '机构对外经营使用的可选商号或交易名称。' AFTER `legal_name`,
  MODIFY COLUMN `incorporation_country` CHAR(2) NULL COMMENT '机构依法注册成立的 ISO 3166-1 alpha-2 国家代码。取值来源：ISO 3166-1 alpha-2；非固定枚举。' AFTER `trade_name`,
  MODIFY COLUMN `organization_type` VARCHAR(64) NULL COMMENT '按司法辖区和法律形态分类的受控机构类型代码。取值来源：司法辖区机构类型目录；非固定枚举。' AFTER `incorporation_country`,
  MODIFY COLUMN `incorporation_date` DATE NULL COMMENT '机构依法成立或注册的 LocalDate 日期事实，不包含时区或时刻。' AFTER `organization_type`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。' AFTER `incorporation_date`,
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。' AFTER `create_time`,
  COMMENT = '机构资料；机构名称、注册国家、机构类型和日期允许部分草稿保存；提交时按入驻规则完整校验；批准后只读；应用校验，不创建 CHECK/FK';

ALTER TABLE `t_party_address`
  DROP INDEX `uk_profile_address_type`,
  MODIFY COLUMN `address_id` BIGINT NOT NULL COMMENT 'Party Profile 地址记录的内部唯一标识。' FIRST,
  MODIFY COLUMN `profile_id` BIGINT NOT NULL COMMENT 'Party 资料头及其类型明细的内部唯一标识。' AFTER `address_id`,
  MODIFY COLUMN `address_type` VARCHAR(64) NOT NULL COMMENT '地址用途或法律属性的受控分类代码。取值来源：地址用途目录；非固定枚举。' AFTER `profile_id`,
  MODIFY COLUMN `address_line1` VARCHAR(255) NULL COMMENT '主体地址的主要街道、门牌或建筑信息。' AFTER `address_type`,
  MODIFY COLUMN `address_line2` VARCHAR(255) NULL COMMENT '主体地址的可选补充单元、楼层或区域信息。' AFTER `address_line1`,
  MODIFY COLUMN `city` VARCHAR(128) NULL COMMENT '主体地址所在城市或同级行政区域名称。' AFTER `address_line2`,
  MODIFY COLUMN `state_or_province` VARCHAR(128) NULL COMMENT '主体地址所在的可选州、省或同级行政区域。' AFTER `city`,
  MODIFY COLUMN `postal_code` VARCHAR(32) NULL COMMENT '主体地址的可选邮政编码。' AFTER `state_or_province`,
  MODIFY COLUMN `country_code` CHAR(2) NULL COMMENT '地址所在国家的精确 ISO 3166-1 alpha-2 代码。取值来源：ISO 3166-1 alpha-2；非固定枚举。' AFTER `postal_code`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。' AFTER `country_code`,
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。' AFTER `create_time`,
  ADD UNIQUE KEY `uk_profile_address_type` (`profile_id`, `address_type`) COMMENT 'Unique index for party_address on profile_id, address_type',
  COMMENT = '主体地址；uk_profile_address_type(profile_id,address_type)；同一资料同一地址类型最多一条；地址组成字段允许部分草稿；提交时完整校验；批准后只读；应用校验，不创建 CHECK/FK';

ALTER TABLE `t_party_contact`
  DROP INDEX `uk_profile_contact_type`,
  DROP COLUMN `vault_metadata_hash`,
  DROP COLUMN `vault_record_version`,
  MODIFY COLUMN `contact_id` BIGINT NOT NULL COMMENT 'Party Profile 联系方式记录的内部唯一标识。' FIRST,
  MODIFY COLUMN `profile_id` BIGINT NOT NULL COMMENT 'Party 资料头及其类型明细的内部唯一标识。' AFTER `contact_id`,
  MODIFY COLUMN `contact_type` VARCHAR(64) NOT NULL COMMENT '区分邮箱与电话号码的闭合类型。允许值：EMAIL、PHONE。' AFTER `profile_id`,
  MODIFY COLUMN `contact_vault_token` VARCHAR(128) NOT NULL COMMENT 'Vault 联系方式标量原值的逻辑引用，不建立跨库物理外键；EMAIL 对应 EMAIL_ADDRESS_V1，PHONE 对应 PHONE_E164_V1。' AFTER `contact_type`,
  MODIFY COLUMN `contact_masked_value` VARCHAR(255) NOT NULL COMMENT 'Vault 按联系方式类型生成的最小受控展示值。' AFTER `contact_vault_token`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。' AFTER `contact_masked_value`,
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。' AFTER `create_time`,
  ADD UNIQUE KEY `uk_profile_contact_type` (`profile_id`, `contact_type`) COMMENT 'Unique index for party_contact on profile_id, contact_type',
  COMMENT = '主体联系方式；uk_profile_contact_type(profile_id,contact_type)；同一资料同一联系方式类型最多一条；可恢复原值由 Vault 持有，CCT 仅保存 Token 与脱敏展示值；应用校验，不创建 CHECK/FK；Email 使用 EMAIL_ADDRESS_V1 标量 Schema；Phone 使用 PHONE_E164_V1 标量 Schema';

ALTER TABLE `t_party_profile_identifier`
  DROP INDEX `uk_profile_identity`,
  DROP INDEX `idx_party_profile_ident_8f43e780`,
  MODIFY COLUMN `profile_identifier_id` BIGINT NOT NULL COMMENT 'Party Profile 与稳定法律标识关联行的内部唯一标识。' FIRST,
  MODIFY COLUMN `profile_id` BIGINT NOT NULL COMMENT 'Party 资料头及其类型明细的内部唯一标识。' AFTER `profile_identifier_id`,
  MODIFY COLUMN `identity_key_id` BIGINT NULL COMMENT '跨 Profile 稳定的 Party 法律标识身份键。' AFTER `profile_id`,
  MODIFY COLUMN `valid_from` DATETIME(6) NULL COMMENT '法律标识值有效区间的 UTC 微秒精度起点，包含该时刻。' AFTER `identity_key_id`,
  MODIFY COLUMN `valid_to` DATETIME(6) NULL COMMENT '法律标识值有效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。' AFTER `valid_from`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。' AFTER `valid_to`,
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。' AFTER `create_time`,
  ADD COLUMN `identifier_type` VARCHAR(64) NULL COMMENT '当前资料填报的身份标识类型；草稿可空。取值来源：CCT 身份标识类型目录；非固定枚举。' AFTER `update_time`,
  ADD COLUMN `issuing_country` CHAR(2) NULL COMMENT '当前资料填报的签发国家代码；按标识类型适用。取值来源：ISO 3166-1 alpha-2，草稿未填可空；非固定枚举。' AFTER `identifier_type`,
  ADD COLUMN `issuer_scope_key` VARCHAR(64) NULL COMMENT '当前资料填报的受控签发机构作用域代码；未选可空。' AFTER `issuing_country`,
  ADD COLUMN `identifier_value` VARCHAR(128) NULL COMMENT '当前资料填报的原始身份号码；不输出普通日志；草稿可空。' AFTER `issuer_scope_key`,
  ADD UNIQUE KEY `uk_profile_identity` (`profile_id`, `identity_key_id`) COMMENT 'Unique index for party_profile_identifier on profile_id, identity_key_id',
  ADD KEY `idx_party_profile_ident_8f43e780` (`identity_key_id`) COMMENT 'Access index for party_profile_identifier on identity_key_id',
  COMMENT = '主体资料法律标识关联；uk_profile_identity(profile_id,identity_key_id)；草稿保存候选标识值；仅完成身份核对后绑定 identity_key_id；批准资料中的绑定不可改写；应用校验，不创建 CHECK/FK';

ALTER TABLE `t_party_identity_key`
  DROP INDEX `uk_party_identity_norm`,
  DROP INDEX `idx_party_identity_key_party_id`,
  DROP COLUMN `identifier_value`,
  MODIFY COLUMN `identity_key_id` BIGINT NOT NULL COMMENT '跨 Profile 稳定的 Party 法律标识身份键。' FIRST,
  MODIFY COLUMN `party_id` BIGINT NOT NULL COMMENT '自然人或机构法律身份根的内部唯一标识。' AFTER `identity_key_id`,
  MODIFY COLUMN `identifier_type` VARCHAR(64) NOT NULL COMMENT '注册号、证件号等法律标识的受控类型代码。取值来源：法律标识类型目录；非固定枚举。' AFTER `party_id`,
  MODIFY COLUMN `issuing_country` CHAR(2) NULL COMMENT '国家签发标识使用的 ISO 3166-1 alpha-2 国家代码；issuer_scope_key=GLOBAL 时为空。取值来源：ISO 3166-1 alpha-2，全局标识为空；非固定枚举。' AFTER `identifier_type`,
  MODIFY COLUMN `issuer_scope_key` VARCHAR(64) NOT NULL COMMENT '法律标识签发国家或 GLOBAL Sentinel 形成的稳定作用域键。' AFTER `issuing_country`,
  MODIFY COLUMN `normalized_identifier_value` VARCHAR(128) NOT NULL COMMENT '按法律标识类型和签发作用域批准规则确定性规范化后的值，用于本库查重和唯一约束。' AFTER `issuer_scope_key`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。' AFTER `normalized_identifier_value`,
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。' AFTER `create_time`,
  ADD UNIQUE KEY `uk_party_identity_norm` (`identifier_type`, `issuer_scope_key`, `normalized_identifier_value`) COMMENT 'Unique index for party_identity_key on identifier_type, issuer_scope_key, normalized_identifier_value',
  ADD KEY `idx_party_identity_key_party_id` (`party_id`) COMMENT 'Access index for party_identity_key on party_id',
  COMMENT = '法律标识注册主档；uk_party_identity_norm(identifier_type,issuer_scope_key,normalized_identifier_value)；已完成规范化校验的永久法律身份键；相同类型、签发域和规范化值只能绑定同一 Party；应用校验，不创建 CHECK/FK';

ALTER TABLE `t_party_relation`
  DROP INDEX `uk_party_rel_ver`,
  DROP INDEX `uk_party_rel_active`,
  DROP INDEX `idx_party_relation_3aff6e6a`,
  DROP INDEX `idx_party_relation_4fd1d42c`,
  DROP COLUMN `active_slot`,
  DROP COLUMN `content_hash`,
  DROP COLUMN `hash_schema_version`,
  DROP COLUMN `version_no`,
  CHANGE COLUMN `relation_version_id` `relation_id` BIGINT NOT NULL COMMENT '组织与相关 Party 关系的稳定标识，不是关系版本标识。' FIRST,
  MODIFY COLUMN `organization_party_id` BIGINT NOT NULL COMMENT 'Party Relation 中被持有、控制或任职的机构 Party 标识。' AFTER `relation_id`,
  MODIFY COLUMN `related_party_id` BIGINT NOT NULL COMMENT 'Party Relation 中所有者、控制人、董事、签字人或 UBO 的 Party 标识。' AFTER `organization_party_id`,
  MODIFY COLUMN `relation_type` VARCHAR(64) NOT NULL COMMENT '所有权、控制、任职或 UBO 等法律关系类型代码。允许值：OWNERSHIP、CONTROL、DIRECTOR、SIGNATORY、UBO。' AFTER `related_party_id`,
  MODIFY COLUMN `ownership_percentage` DECIMAL(18,10) NULL COMMENT '法律关系类型具备持股语义时填写的百分比，取值范围为 0 至 100；其他关系类型为空。' AFTER `relation_type`,
  MODIFY COLUMN `effective_from` DATETIME(6) NOT NULL COMMENT '业务记录生效区间的 UTC 微秒精度起点，包含该时刻。' AFTER `ownership_percentage`,
  MODIFY COLUMN `effective_to` DATETIME(6) NULL COMMENT '业务记录生效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。' AFTER `effective_from`,
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '当前业务记录在其所属聚合生命周期中的状态代码。允许值：DRAFT、ACTIVE、ENDED。' AFTER `effective_to`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。' AFTER `status`,
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。' AFTER `create_time`,
  ADD COLUMN `client_application_id` BIGINT NULL COMMENT '拥有本次可编辑关系的 Client 申请；Client 新写必填，其他既有来源可空。' AFTER `update_time`,
  ADD UNIQUE KEY `uk_party_relation_identity` (`organization_party_id`, `related_party_id`, `relation_type`) COMMENT 'Unique index for party_relation on organization_party_id, related_party_id, relation_type',
  ADD KEY `idx_party_relation_person` (`related_party_id`) COMMENT 'Access index for party_relation on related_party_id',
  ADD KEY `idx_party_relation_application` (`client_application_id`) COMMENT 'Access index for party_relation on client_application_id',
  COMMENT = '主体关系；uk_party_relation_identity(organization_party_id,related_party_id,relation_type)；同一机构、相关主体和关系类型只保留一条稳定关系；Client 草稿由 client_application_id 限定修改归属；应用校验，不创建 CHECK/FK；稳定关系标识；Client 未批准资料只允许所属可编辑申请维护，批准后关系集合锁定。；真实关系生效/结束日期保留；非 Client 历史引用不得自动迁移或归并。';

ALTER TABLE `t_client_business_profile`
  DROP INDEX `uk_client_biz_prof_ver`,
  DROP INDEX `uk_client_biz_prof_active`,
  DROP INDEX `idx_client_business_pro_554980bb`,
  DROP COLUMN `active_slot`,
  DROP COLUMN `effective_from`,
  DROP COLUMN `effective_to`,
  DROP COLUMN `hash_schema_version`,
  DROP COLUMN `status`,
  DROP COLUMN `version_no`,
  MODIFY COLUMN `business_profile_id` BIGINT NOT NULL COMMENT 'Client 单份经营资料稳定标识，不是版本标识。' FIRST,
  MODIFY COLUMN `client_id` BIGINT NOT NULL COMMENT '平台 Client 角色的内部唯一标识。' AFTER `business_profile_id`,
  MODIFY COLUMN `business_model_code` VARCHAR(64) NULL COMMENT 'Client 声明商业模式的受控业务代码。取值来源：Client Business Model Catalog；非固定枚举。' AFTER `client_id`,
  MODIFY COLUMN `website_url` VARCHAR(2048) NULL COMMENT 'Client 对外经营网站的受控 URL；无网站时可为空。' AFTER `business_model_code`,
  MODIFY COLUMN `expected_volume` DECIMAL(24,8) NULL COMMENT '预计月交易金额，使用 currency 指定币种；提交时大于零。' AFTER `website_url`,
  MODIFY COLUMN `expected_ticket` DECIMAL(24,8) NULL COMMENT 'Client 声明的预期单笔交易金额。' AFTER `expected_volume`,
  MODIFY COLUMN `currency` CHAR(3) NULL COMMENT '预期交易量与客单价采用的精确 ISO 4217 三位币种代码。取值来源：ISO 4217；非固定枚举。' AFTER `expected_ticket`,
  MODIFY COLUMN `content_hash` CHAR(64) NULL COMMENT '已批准单份经营资料内容摘要；未批准为空，不用于草稿版本冲突控制。' AFTER `currency`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。' AFTER `content_hash`,
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。' AFTER `create_time`,
  ADD COLUMN `business_description` VARCHAR(2000) NULL COMMENT '客户实际商品、服务、客户群体及销售方式说明；草稿可空，提交必填。' AFTER `update_time`,
  ADD COLUMN `classification_case_id` BIGINT NULL COMMENT '本组运营分类所属 Risk 主审核任务；外部引用，不建物理外键。' AFTER `business_description`,
  ADD COLUMN `classification_confirmed_by` BIGINT NULL COMMENT '确认本任务分类的 Operations Account；与确认时间成组。' AFTER `classification_case_id`,
  ADD COLUMN `classification_confirmed_time` DATETIME(6) NULL COMMENT '本任务分类确认的 UTC 时间；为空时仅为可编辑草稿。' AFTER `classification_confirmed_by`,
  ADD UNIQUE KEY `uk_client_business_client` (`client_id`) COMMENT 'Unique index for client_business_profile on client_id',
  COMMENT = '客户业务资料；uk_client_business_client(client_id)；每个 Client 一份业务资料；Client 填写经营说明，运营确认行业及 MCC；批准分类任务和确认人随资料冻结；应用校验，不创建 CHECK/FK';

ALTER TABLE `t_client_application`
  DROP INDEX `uk_client_app_no`,
  DROP INDEX `uk_client_app_active`,
  DROP INDEX `idx_client_application_7de4b1c7`,
  DROP INDEX `idx_client_application_2c0403e4`,
  DROP INDEX `idx_client_application_053f1a4b`,
  DROP INDEX `idx_client_application_ba6c56cb`,
  DROP COLUMN `application_type`,
  DROP COLUMN `base_business_profile_id`,
  DROP COLUMN `base_party_profile_id`,
  DROP COLUMN `qualification_decision_version`,
  DROP COLUMN `submission_content_hash`,
  DROP COLUMN `submission_hash_schema_version`,
  DROP COLUMN `target_business_profile_id`,
  DROP COLUMN `target_party_profile_id`,
  MODIFY COLUMN `application_id` BIGINT NOT NULL COMMENT '业务申请记录的内部唯一标识。' FIRST,
  MODIFY COLUMN `application_no` VARCHAR(64) NOT NULL COMMENT '供业务查询与人工操作使用的唯一申请编号。' AFTER `application_id`,
  MODIFY COLUMN `client_id` BIGINT NOT NULL COMMENT '平台 Client 角色的内部唯一标识。' AFTER `application_no`,
  MODIFY COLUMN `requested_by_account_id` BIGINT NOT NULL COMMENT '发起申请的 Platform CUSTOMER Account 外部稳定标识；一般可空，但 ONBOARDING 必填，不建立跨库物理外键。' AFTER `client_id`,
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '入驻申请状态：DRAFT、SUBMITTED、UNDER_REVIEW、RETURNED、APPROVED、REJECTED、WITHDRAWN。允许值：DRAFT、SUBMITTED、UNDER_REVIEW、RETURNED、APPROVED、REJECTED、WITHDRAWN。' AFTER `requested_by_account_id`,
  MODIFY COLUMN `active_slot` TINYINT(1) NULL COMMENT 'DRAFT、SUBMITTED、UNDER_REVIEW、RETURNED 为 1，终态为 NULL；同一 Account 的个人/组织 Client 入驻合计最多一个进行中申请；同一 Client 的原唯一约束保留。允许值：1 或 NULL。取值规则：DRAFT、SUBMITTED、UNDER_REVIEW、RETURNED 为 1；APPROVED、REJECTED、WITHDRAWN 为 NULL，禁止 0。' AFTER `status`,
  MODIFY COLUMN `due_diligence_case_id` BIGINT NULL COMMENT 'Risk & Compliance 尽调 Case 的逻辑引用，不建立跨库物理外键。' AFTER `active_slot`,
  MODIFY COLUMN `qualification_decision_id` BIGINT NULL COMMENT '当前主审核任务产生的不可变资格决定 ID；新 Client 不携带决定版本。' AFTER `due_diligence_case_id`,
  MODIFY COLUMN `qualification_type` VARCHAR(64) NOT NULL COMMENT '提交时按申请角色和 Party 类型确定并冻结的 Risk 资格类型。允许值：INDIVIDUAL_KYC、ORGANIZATION_KYB。' AFTER `qualification_decision_id`,
  MODIFY COLUMN `submitted_time` DATETIME(6) NULL COMMENT '业务申请正式提交并进入审核流程的 UTC 微秒精度时刻。' AFTER `qualification_type`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。' AFTER `submitted_time`,
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。' AFTER `create_time`,
  ADD COLUMN `current_submission_id` VARCHAR(64) NULL COMMENT '当前正式提交命令 UUID；同次重试复用，退回重提更换；不是资料版本。' AFTER `update_time`,
  ADD UNIQUE KEY `uk_client_application_no` (`application_no`) COMMENT 'Unique index for client_application on application_no',
  ADD UNIQUE KEY `uk_client_application_active` (`client_id`, `active_slot`) COMMENT 'Unique index for client_application on client_id, active_slot',
  ADD UNIQUE KEY `uk_client_app_account_active` (`requested_by_account_id`, `active_slot`) COMMENT 'Unique index for client_application on requested_by_account_id, active_slot',
  ADD UNIQUE KEY `uk_client_app_submission` (`current_submission_id`) COMMENT 'Unique index for client_application on current_submission_id',
  ADD KEY `idx_client_app_requester` (`requested_by_account_id`, `status`) COMMENT 'Access index for client_application on requested_by_account_id, status',
  COMMENT = '客户申请；uk_client_application_no(application_no)、uk_client_application_active(client_id,active_slot)、uk_client_app_account_active(requested_by_account_id,active_slot)、uk_client_app_submission(current_submission_id)；DRAFT、RETURNED 可编辑并占用 active_slot；SUBMITTED、UNDER_REVIEW 只读并继续占用；APPROVED、REJECTED 终态释放名额；current_submission_id 隔离每次提交结果；应用校验，不创建 CHECK/FK；同账号个人/组织合计一份进行中申请；RETURNED 占用名额，终态释放。；DRAFT/RETURNED 可编辑；批准后永久只读，最终拒绝不复活。；提交和重提使用 current_submission_id；旧审核任务结果不得覆盖当前任务。；资格类型由主 Party 类型推导，最终决定校验准确 Case/Decision/Role Tuple。';
