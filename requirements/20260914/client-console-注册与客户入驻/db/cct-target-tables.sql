-- REVIEW ONLY. Target table definitions; NOT a migration.
-- Do not execute against an existing database. Missing unchanged tables are intentional.

CREATE TABLE `t_party_profile` (
  `profile_id` BIGINT NOT NULL COMMENT '单份 Party 资料的稳定标识，不是版本标识。',
  `party_id` BIGINT NOT NULL COMMENT '自然人或机构法律身份根的内部唯一标识。',
  `content_hash` CHAR(64) NULL COMMENT '批准资料内容摘要，保留现有 Checkout 法律资料消费者；未批准为空，批准后不可变。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  `draft_application_id` BIGINT NULL COMMENT '当前可编辑 Client 入驻申请；未批准资料只能由该申请编辑；批准后清空。',
  `approved_time` DATETIME(6) NULL COMMENT '资料首次批准并永久锁定的 UTC 时间；未批准为空。',
  PRIMARY KEY (`profile_id`),
  UNIQUE KEY `uk_party_profile_party` (`party_id`) COMMENT '保证唯一性：party_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Client 入驻简化目标：法律主体资料版本；仅供评审，非已部署结构。';

CREATE TABLE `t_individual_profile` (
  `profile_id` BIGINT NOT NULL COMMENT 'Party 资料头及其类型明细的内部唯一标识。',
  `given_name` VARCHAR(64) NULL COMMENT '个人法定姓名中的名字。',
  `middle_name` VARCHAR(64) NULL COMMENT '个人法定姓名中的可选中间名。',
  `family_name` VARCHAR(64) NULL COMMENT '个人姓氏，即法定姓名中的姓氏部分。',
  `birth_date` DATE NULL COMMENT '个人法定出生 LocalDate 日期事实，不包含时区或时刻。',
  `nationality_country` CHAR(2) NULL COMMENT '个人国籍的 ISO 3166-1 alpha-2 国家代码。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `residence_country` CHAR(2) NULL COMMENT '个人主要居住地的 ISO 3166-1 alpha-2 国家代码。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`profile_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Client 入驻简化目标：个人资料版本；仅供评审，非已部署结构。';

CREATE TABLE `t_organization_profile` (
  `profile_id` BIGINT NOT NULL COMMENT 'Party 资料头及其类型明细的内部唯一标识。',
  `legal_name` VARCHAR(64) NULL COMMENT '机构在注册司法辖区登记的完整法定名称。',
  `trade_name` VARCHAR(64) NULL COMMENT '机构对外经营使用的可选商号或交易名称。',
  `incorporation_country` CHAR(2) NULL COMMENT '机构依法注册成立的 ISO 3166-1 alpha-2 国家代码。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `organization_type` VARCHAR(64) NULL COMMENT '按司法辖区和法律形态分类的受控机构类型代码。取值来源：司法辖区机构类型目录；非固定枚举。',
  `incorporation_date` DATE NULL COMMENT '机构依法成立或注册的 LocalDate 日期事实，不包含时区或时刻。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`profile_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Client 入驻简化目标：机构资料版本；仅供评审，非已部署结构。';

CREATE TABLE `t_party_address` (
  `address_id` BIGINT NOT NULL COMMENT 'Party Profile 地址记录的内部唯一标识。',
  `profile_id` BIGINT NOT NULL COMMENT 'Party 资料头及其类型明细的内部唯一标识。',
  `address_type` VARCHAR(64) NOT NULL COMMENT '地址用途或法律属性的受控分类代码。取值来源：地址用途目录；非固定枚举。',
  `address_line1` VARCHAR(255) NULL COMMENT '主体地址的主要街道、门牌或建筑信息。',
  `address_line2` VARCHAR(255) NULL COMMENT '主体地址的可选补充单元、楼层或区域信息。',
  `city` VARCHAR(128) NULL COMMENT '主体地址所在城市或同级行政区域名称。',
  `state_or_province` VARCHAR(128) NULL COMMENT '主体地址所在的可选州、省或同级行政区域。',
  `postal_code` VARCHAR(32) NULL COMMENT '主体地址的可选邮政编码。',
  `country_code` CHAR(2) NULL COMMENT '地址所在国家的精确 ISO 3166-1 alpha-2 代码。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`address_id`),
  UNIQUE KEY `uk_profile_address_type` (`profile_id`,`address_type`) COMMENT '保证唯一性：profile_id,address_type'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Client 入驻简化目标：主体地址版本；仅供评审，非已部署结构。';

CREATE TABLE `t_party_contact` (
  `contact_id` BIGINT NOT NULL COMMENT 'Party Profile 联系方式记录的内部唯一标识。',
  `profile_id` BIGINT NOT NULL COMMENT 'Party 资料头及其类型明细的内部唯一标识。',
  `contact_type` VARCHAR(64) NOT NULL COMMENT '区分邮箱与电话号码的闭合类型。允许值：EMAIL、PHONE。',
  `contact_vault_token` VARCHAR(128) NOT NULL COMMENT 'Vault 联系方式标量原值的逻辑引用，不建立跨库物理外键；EMAIL 对应 EMAIL_ADDRESS_V1，PHONE 对应 PHONE_E164_V1。',
  `contact_masked_value` VARCHAR(255) NOT NULL COMMENT 'Vault 按联系方式类型生成的最小受控展示值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`contact_id`),
  UNIQUE KEY `uk_profile_contact_type` (`profile_id`,`contact_type`) COMMENT '保证唯一性：profile_id,contact_type'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Client 入驻简化目标：主体联系方式版本；仅供评审，非已部署结构。';

CREATE TABLE `t_party_profile_identifier` (
  `profile_identifier_id` BIGINT NOT NULL COMMENT 'Party Profile 与稳定法律标识关联行的内部唯一标识。',
  `profile_id` BIGINT NOT NULL COMMENT 'Party 资料头及其类型明细的内部唯一标识。',
  `identity_key_id` BIGINT NULL COMMENT '跨 Profile 稳定的 Party 法律标识身份键。',
  `valid_from` DATETIME(6) NULL COMMENT '法律标识值有效区间的 UTC 微秒精度起点，包含该时刻。',
  `valid_to` DATETIME(6) NULL COMMENT '法律标识值有效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  `identifier_type` VARCHAR(64) NULL COMMENT '当前资料填报的身份标识类型；草稿可空。',
  `issuing_country` CHAR(2) NULL COMMENT '当前资料填报的签发国家代码；按标识类型适用。',
  `issuer_scope_key` VARCHAR(64) NULL COMMENT '当前资料填报的受控签发机构作用域代码；未选可空。',
  `identifier_value` VARCHAR(128) NULL COMMENT '当前资料填报的原始身份号码；不输出普通日志；草稿可空。',
  PRIMARY KEY (`profile_identifier_id`),
  UNIQUE KEY `uk_profile_identity` (`profile_id`,`identity_key_id`) COMMENT '保证唯一性：profile_id,identity_key_id',
  KEY `idx_party_profile_ident_8f43e780` (`identity_key_id`) COMMENT '支持查询：identity_key_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Client 入驻简化目标：主体资料法律标识关联；仅供评审，非已部署结构。';

CREATE TABLE `t_party_identity_key` (
  `identity_key_id` BIGINT NOT NULL COMMENT '跨 Profile 稳定的 Party 法律标识身份键。',
  `party_id` BIGINT NOT NULL COMMENT '自然人或机构法律身份根的内部唯一标识。',
  `identifier_type` VARCHAR(64) NOT NULL COMMENT '注册号、证件号等法律标识的受控类型代码。取值来源：法律标识类型目录；非固定枚举。',
  `issuing_country` CHAR(2) NULL COMMENT '国家签发标识使用的 ISO 3166-1 alpha-2 国家代码；issuer_scope_key=GLOBAL 时为空。取值来源：ISO 3166-1 alpha-2，全局标识为空；非固定枚举。',
  `issuer_scope_key` VARCHAR(64) NOT NULL COMMENT '法律标识签发国家或 GLOBAL Sentinel 形成的稳定作用域键。',
  `normalized_identifier_value` VARCHAR(128) NOT NULL COMMENT '按法律标识类型和签发作用域批准规则确定性规范化后的值，用于本库查重和唯一约束。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`identity_key_id`),
  UNIQUE KEY `uk_party_identity_norm` (`identifier_type`,`issuer_scope_key`,`normalized_identifier_value`) COMMENT '保证唯一性：identifier_type,issuer_scope_key,normalized_identifier_value',
  KEY `idx_party_identity_key_party_id` (`party_id`) COMMENT '支持查询：party_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Client 入驻简化目标：法律标识注册主档；仅供评审，非已部署结构。';

CREATE TABLE `t_party_relation` (
  `relation_id` BIGINT NOT NULL COMMENT '组织与相关 Party 关系的稳定标识，不是关系版本标识。',
  `organization_party_id` BIGINT NOT NULL COMMENT 'Party Relation 中被持有、控制或任职的机构 Party 标识。',
  `related_party_id` BIGINT NOT NULL COMMENT 'Party Relation 中所有者、控制人、董事、签字人或 UBO 的 Party 标识。',
  `relation_type` VARCHAR(64) NOT NULL COMMENT '所有权、控制、任职或 UBO 等法律关系类型代码。允许值：OWNERSHIP、CONTROL、DIRECTOR、SIGNATORY、UBO。',
  `ownership_percentage` DECIMAL(18,10) NULL COMMENT '法律关系类型具备持股语义时填写的百分比，取值范围为 0 至 100；其他关系类型为空。',
  `effective_from` DATETIME(6) NOT NULL COMMENT '业务记录生效区间的 UTC 微秒精度起点，包含该时刻。',
  `effective_to` DATETIME(6) NULL COMMENT '业务记录生效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。',
  `status` VARCHAR(64) NOT NULL COMMENT '当前业务记录在其所属聚合生命周期中的状态代码。允许值：DRAFT、ACTIVE、ENDED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  `client_application_id` BIGINT NULL COMMENT '拥有本次可编辑关系的 Client 申请；Client 新写必填，其他既有来源可空。',
  PRIMARY KEY (`relation_id`),
  UNIQUE KEY `uk_party_relation_identity` (`organization_party_id`,`related_party_id`,`relation_type`) COMMENT '保证唯一性：organization_party_id,related_party_id,relation_type',
  KEY `idx_party_relation_person` (`related_party_id`) COMMENT '支持查询：related_party_id',
  KEY `idx_party_relation_application` (`client_application_id`) COMMENT '支持查询：client_application_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Client 入驻简化目标：主体关系版本；仅供评审，非已部署结构。';

CREATE TABLE `t_client_business_profile` (
  `business_profile_id` BIGINT NOT NULL COMMENT 'Client 单份经营资料稳定标识，不是版本标识。',
  `client_id` BIGINT NOT NULL COMMENT '平台 Client 角色的内部唯一标识。',
  `business_model_code` VARCHAR(64) NULL COMMENT 'Client 声明商业模式的受控业务代码。取值来源：Client Business Model Catalog；非固定枚举。',
  `website_url` VARCHAR(2048) NULL COMMENT 'Client 对外经营网站的受控 URL；无网站时可为空。',
  `expected_volume` DECIMAL(24,8) NULL COMMENT '预计月交易金额，使用 currency 指定币种；提交时大于零。',
  `expected_ticket` DECIMAL(24,8) NULL COMMENT 'Client 声明的预期单笔交易金额。',
  `currency` CHAR(3) NULL COMMENT '预期交易量与客单价采用的精确 ISO 4217 三位币种代码。取值来源：ISO 4217；非固定枚举。',
  `content_hash` CHAR(64) NULL COMMENT '已批准单份经营资料内容摘要；未批准为空，不用于草稿版本冲突控制。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  `business_description` VARCHAR(2000) NULL COMMENT '客户实际商品、服务、客户群体及销售方式说明；草稿可空，提交必填。',
  `classification_case_id` BIGINT NULL COMMENT '本组运营分类所属 Risk 主审核任务；外部引用，不建物理外键。',
  `classification_confirmed_by` BIGINT NULL COMMENT '确认本任务分类的 Operations Account；与确认时间成组。',
  `classification_confirmed_time` DATETIME(6) NULL COMMENT '本任务分类确认的 UTC 时间；为空时仅为可编辑草稿。',
  PRIMARY KEY (`business_profile_id`),
  UNIQUE KEY `uk_client_business_profile_client` (`client_id`) COMMENT '保证唯一性：client_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Client 入驻简化目标：客户业务资料版本；仅供评审，非已部署结构。';

CREATE TABLE `t_client_application` (
  `application_id` BIGINT NOT NULL COMMENT '业务申请记录的内部唯一标识。',
  `application_no` VARCHAR(64) NOT NULL COMMENT '供业务查询与人工操作使用的唯一申请编号。',
  `client_id` BIGINT NOT NULL COMMENT '平台 Client 角色的内部唯一标识。',
  `requested_by_account_id` BIGINT NOT NULL COMMENT '发起申请的 Platform CUSTOMER Account 外部稳定标识；一般可空，但 ONBOARDING 必填，不建立跨库物理外键。',
  `status` VARCHAR(64) NOT NULL COMMENT '入驻申请状态：DRAFT、SUBMITTED、UNDER_REVIEW、RETURNED、APPROVED、REJECTED、WITHDRAWN。',
  `active_slot` TINYINT(1) NULL COMMENT 'DRAFT、SUBMITTED、UNDER_REVIEW、RETURNED 为 1，终态为 NULL；同一 Account 的个人/组织 Client 入驻合计最多一个进行中申请；同一 Client 的原唯一约束保留。',
  `due_diligence_case_id` BIGINT NULL COMMENT 'Risk & Compliance 尽调 Case 的逻辑引用，不建立跨库物理外键。',
  `qualification_decision_id` BIGINT NULL COMMENT '当前主审核任务产生的不可变资格决定 ID；新 Client 不携带决定版本。',
  `qualification_type` VARCHAR(64) NOT NULL COMMENT '提交时按申请角色和 Party 类型确定并冻结的 Risk 资格类型。允许值：INDIVIDUAL_KYC、ORGANIZATION_KYB。',
  `submitted_time` DATETIME(6) NULL COMMENT '业务申请正式提交并进入审核流程的 UTC 微秒精度时刻。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  `current_submission_id` VARCHAR(64) NULL COMMENT '当前正式提交命令 UUID；同次重试复用，退回重提更换；不是资料版本。',
  PRIMARY KEY (`application_id`),
  UNIQUE KEY `uk_client_application_no` (`application_no`) COMMENT '保证唯一性：application_no',
  UNIQUE KEY `uk_client_application_active` (`client_id`,`active_slot`) COMMENT '保证唯一性：client_id,active_slot',
  UNIQUE KEY `uk_client_application_account_active` (`requested_by_account_id`,`active_slot`) COMMENT '保证唯一性：requested_by_account_id,active_slot',
  UNIQUE KEY `uk_client_application_submission` (`current_submission_id`) COMMENT '保证唯一性：current_submission_id',
  KEY `idx_client_application_requester` (`requested_by_account_id`,`status`) COMMENT '支持查询：requested_by_account_id,status'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Client 入驻简化目标：客户申请；仅供评审，非已部署结构。';
