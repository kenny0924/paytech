-- Code-MoR client-master 目标 Schema；与 client-master.pdma.json 手工保持一致（2026-10-22 起不再使用生成器）。
-- Apply manually to an EMPTY database only; existing environments require a reviewed migration.
-- Execution status: agent-docs/CURRENT_IMPLEMENTATION.md. Verify Java compatibility before rollout.
-- Source: paytech/client-master/docs/database/client-master.pdma.json
SET NAMES utf8mb4;

CREATE TABLE `t_party` (
  `party_id` BIGINT NOT NULL COMMENT '自然人或机构法律身份根的内部唯一标识。',
  `party_no` VARCHAR(64) NOT NULL COMMENT '供业务查询与关联使用的唯一法律主体编号。',
  `party_type` VARCHAR(64) NOT NULL COMMENT '区分自然人与机构法律身份的类型代码。允许值：INDIVIDUAL、ORGANIZATION。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`party_id`),
  UNIQUE KEY `uk_party_no` (`party_no`) COMMENT 'Unique index for party on party_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='法律身份根；uk_party_no(party_no)；party_type IN (INDIVIDUAL,ORGANIZATION)，创建后不可改';

CREATE TABLE `t_party_profile` (
  `profile_id` BIGINT NOT NULL COMMENT '单份 Party 资料的稳定标识，不是版本标识。',
  `party_id` BIGINT NOT NULL COMMENT '自然人或机构法律身份根的内部唯一标识。',
  `content_hash` CHAR(64) NULL COMMENT '批准资料内容摘要（规范化资料 JSON 的 SHA-256 小写十六进制），供 Checkout 法律资料消费者核对；未批准为空，批准时写入且不可变。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  `draft_application_id` BIGINT NULL COMMENT '当前可编辑 Client 入驻申请；未批准资料只能由该申请编辑；批准时同一事务清空。',
  `approved_time` DATETIME(6) NULL COMMENT '资料首次批准并永久锁定的 UTC 时间；未批准为空。',
  `jurisdiction_code` VARCHAR(16) NULL COMMENT '资料采集所依据的辖区包代码，入驻创建时写入；Operating Entity 与 Local Partner 的存量资料为空。取值来源：t_jurisdiction；非固定枚举。',
  `current_slot` TINYINT(1) NULL COMMENT '同一 Party 当前资料的稀疏唯一槽位。允许值：1 或 NULL。取值规则：入驻草稿资料与已批准的当前资料为 1；被后续修订取代后为 NULL 并记录 superseded_time（修订申请本期未启用）。',
  `superseded_time` DATETIME(6) NULL COMMENT '资料被修订申请取代、不再是当前资料的 UTC 时间；当前资料为空。',
  PRIMARY KEY (`profile_id`),
  UNIQUE KEY `uk_party_profile_current` (`party_id`, `current_slot`) COMMENT 'Unique index for party_profile on party_id, current_slot',
  KEY `idx_party_profile_80cc2659` (`draft_application_id`) COMMENT 'Access index for party_profile on draft_application_id',
  KEY `idx_party_profile_party` (`party_id`) COMMENT 'Access index for party_profile on party_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='法律主体资料；uk_party_profile_current(party_id,current_slot)；同一 Party 只有一份当前资料（current_slot=1），被取代的历史资料 current_slot 为 NULL 并永久保留；草稿由 draft_application_id 指定申请编辑；批准时同一事务写 approved_time、content_hash 并清空 draft_application_id，之后永久只读；应用校验，不创建 CHECK/FK';

CREATE TABLE `t_individual_profile` (
  `profile_id` BIGINT NOT NULL COMMENT 'Party 资料头及其类型明细的内部唯一标识。',
  `given_name` VARCHAR(128) NULL COMMENT '个人法定姓名中的名字（拉丁字母，与护照一致）。',
  `middle_name` VARCHAR(128) NULL COMMENT '个人法定姓名中的可选中间名，可容纳父名与祖父名。',
  `family_name` VARCHAR(128) NULL COMMENT '个人姓氏，即法定姓名中的姓氏部分。',
  `birth_date` DATE NULL COMMENT '个人法定出生 LocalDate 日期事实，不包含时区或时刻。',
  `nationality_country` CHAR(2) NULL COMMENT '个人国籍的 ISO 3166-1 alpha-2 国家代码。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `residence_country` CHAR(2) NULL COMMENT '个人主要居住地的 ISO 3166-1 alpha-2 国家代码。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  `birth_place` VARCHAR(128) NULL COMMENT '出生地（城市或地区）文本，按辖区 Schema 决定是否必填。',
  `birth_country` CHAR(2) NULL COMMENT '出生国家的 ISO 3166-1 alpha-2 代码。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `gender` VARCHAR(16) NULL COMMENT '筛查使用的性别代码。取值来源：通用目录 GENDER；非固定枚举。',
  `source_of_funds_code` VARCHAR(64) NULL COMMENT '资金来源代码，作为风险评级因子。取值来源：通用目录 SOURCE_OF_FUNDS；非固定枚举。',
  `pep_self_declared` TINYINT(1) NULL COMMENT '本人是否自述为政治公众人物。允许值：0、1、NULL。取值规则：NULL 表示尚未填写。',
  PRIMARY KEY (`profile_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='个人资料；允许部分草稿保存；提交时按申请冻结的采集 Schema 完整校验；批准后只读；职业、税务居民地等辖区特有事实走 t_party_attribute；应用校验，不创建 CHECK/FK';

CREATE TABLE `t_organization_profile` (
  `profile_id` BIGINT NOT NULL COMMENT 'Party 资料头及其类型明细的内部唯一标识。',
  `legal_name` VARCHAR(128) NULL COMMENT '机构在注册司法辖区登记的完整法定名称（拉丁字母）。',
  `trade_name` VARCHAR(128) NULL COMMENT '机构对外经营使用的可选商号或交易名称。',
  `incorporation_country` CHAR(2) NULL COMMENT '机构依法注册成立的 ISO 3166-1 alpha-2 国家代码。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `organization_type` VARCHAR(64) NULL COMMENT '按司法辖区和法律形态分类的受控机构类型代码。取值来源：t_organization_type.type_code；非固定枚举。',
  `incorporation_date` DATE NULL COMMENT '机构依法成立或注册的 LocalDate 日期事实，不包含时区或时刻。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  `registration_authority_code` VARCHAR(64) NULL COMMENT '颁发执照或商业登记的登记机关代码，如 AE_DED_DUBAI、SA_MC、EG_GAFI；AUTHORITY 作用域标识以它为唯一性作用域。取值来源：通用目录 REGISTRATION_AUTHORITY；非固定枚举。',
  `license_expiry_date` DATE NULL COMMENT '营业执照或商业登记到期日，提交时不得早于当日。',
  `listed_company` TINYINT(1) NULL COMMENT '是否为受监管市场上市公司。允许值：0、1、NULL。取值规则：NULL 表示尚未填写。',
  PRIMARY KEY (`profile_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='机构资料；允许部分草稿保存；提交时按申请冻结的采集 Schema 完整校验；批准后只读；经济部门、自贸区代码、税号等辖区特有事实走 t_party_attribute；应用校验，不创建 CHECK/FK';

CREATE TABLE `t_party_identity_key` (
  `identity_key_id` BIGINT NOT NULL COMMENT '跨 Profile 稳定的 Party 法律标识身份键。',
  `party_id` BIGINT NOT NULL COMMENT '自然人或机构法律身份根的内部唯一标识。',
  `identifier_type` VARCHAR(64) NOT NULL COMMENT '注册号、证件号等法律标识的受控类型代码。取值来源：t_identifier_type.identifier_type_code；非固定枚举。',
  `issuing_country` CHAR(2) NULL COMMENT '国家签发标识使用的 ISO 3166-1 alpha-2 国家代码；作用域不是签发国时为空。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `issuer_scope_key` VARCHAR(64) NOT NULL COMMENT '号码唯一性作用域键：GLOBAL、签发国代码或登记机关代码。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  `identifier_key_value` VARCHAR(128) NOT NULL COMMENT '唯一性比较值：VAULT_REQUIRED 类型为规范化原值经 LEGAL_IDENTIFIER_V1 Protect 得到的 STABLE Token（同值同 Token），其他类型为规范化后的公开登记号；本库不保存个人证件原值。',
  PRIMARY KEY (`identity_key_id`),
  UNIQUE KEY `uk_party_identity_token` (`identifier_type`, `issuer_scope_key`, `identifier_key_value`) COMMENT 'Unique index for party_identity_key on identifier_type, issuer_scope_key, identifier_key_value',
  KEY `idx_party_identity_key_party_id` (`party_id`) COMMENT 'Access index for party_identity_key on party_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='法律标识注册主档；uk_party_identity_token(identifier_type,issuer_scope_key,identifier_key_value)；相同类型、作用域和号码只能绑定同一 Party；个人证件以 STABLE Token 比较，不保存原值；应用校验，不创建 CHECK/FK';

CREATE TABLE `t_party_profile_identifier` (
  `profile_identifier_id` BIGINT NOT NULL COMMENT 'Party Profile 与稳定法律标识关联行的内部唯一标识。',
  `profile_id` BIGINT NOT NULL COMMENT 'Party 资料头及其类型明细的内部唯一标识。',
  `identity_key_id` BIGINT NULL COMMENT '跨 Profile 稳定的 Party 法律标识身份键；提交时绑定，草稿可空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  `identifier_type` VARCHAR(64) NULL COMMENT '当前资料填报的法律标识类型；草稿可空。取值来源：t_identifier_type.identifier_type_code；非固定枚举。',
  `issuing_country` CHAR(2) NULL COMMENT '当前资料填报的签发国家代码；按标识类型的作用域方式适用。取值来源：ISO 3166-1 alpha-2，草稿未填可空；非固定枚举。',
  `issuer_scope_key` VARCHAR(64) NULL COMMENT '提交时计算并冻结的唯一性作用域键；草稿为空。',
  `identifier_vault_token` VARCHAR(128) NULL COMMENT 'VAULT_REQUIRED 类型号码规范化后经 LEGAL_IDENTIFIER_V1 Protect 得到的 STABLE Token；非 Vault 类型或草稿未填为空。',
  `public_identifier_value` VARCHAR(128) NULL COMMENT '非 Vault 类型的规范化公开登记号（机构执照号、商业登记号等）；VAULT_REQUIRED 类型恒为空。',
  `identifier_masked_value` VARCHAR(32) NULL COMMENT '展示用脱敏值，只保留末 4 位；草稿未填为空。',
  `identifier_check_result` VARCHAR(64) NULL COMMENT '保存号码时按标识类型执行的格式与校验位检查结论；为空表示通过或尚未填写。允许值：IDENTIFIER_FORMAT_INVALID、IDENTIFIER_CHECKSUM_FAILED、NULL。',
  `issue_date` DATE NULL COMMENT '证件签发日期；可空。',
  `expiry_date` DATE NULL COMMENT '证件到期日（含当日有效）；EXPIRY_REQUIRED 类型提交时必填且不得早于提交日。',
  PRIMARY KEY (`profile_identifier_id`),
  UNIQUE KEY `uk_profile_identity` (`profile_id`, `identity_key_id`) COMMENT 'Unique index for party_profile_identifier on profile_id, identity_key_id',
  KEY `idx_party_profile_ident_8f43e780` (`identity_key_id`) COMMENT 'Access index for party_profile_identifier on identity_key_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='主体资料法律标识；uk_profile_identity(profile_id,identity_key_id)；个人证件只保存 Vault Token 与末 4 位脱敏，机构公开登记号保存规范化明文；仅提交时绑定 identity_key_id；批准资料中的绑定不可改写；应用校验，不创建 CHECK/FK';

CREATE TABLE `t_party_address` (
  `address_id` BIGINT NOT NULL COMMENT 'Party Profile 地址记录的内部唯一标识。',
  `profile_id` BIGINT NOT NULL COMMENT 'Party 资料头及其类型明细的内部唯一标识。',
  `address_type` VARCHAR(64) NOT NULL COMMENT '地址用途或法律属性的受控分类代码。允许值：RESIDENTIAL、REGISTERED。',
  `address_line1` VARCHAR(255) NULL COMMENT '主体地址的主要街道、门牌或建筑信息。',
  `address_line2` VARCHAR(255) NULL COMMENT '主体地址的可选补充单元、楼层或区域信息。',
  `city` VARCHAR(128) NULL COMMENT '主体地址所在城市或同级行政区域名称或目录代码。',
  `state_or_province` VARCHAR(128) NULL COMMENT '主体地址所在的州、省、酋长国或同级行政区域名称或目录代码。',
  `postal_code` VARCHAR(32) NULL COMMENT '主体地址的邮政编码；是否必填由地址格式与 POSTAL_CODE_REQUIRED 规则决定。',
  `country_code` CHAR(2) NULL COMMENT '地址所在国家的精确 ISO 3166-1 alpha-2 代码。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  `address_format_code` VARCHAR(32) NULL COMMENT '提交校验所用的地址格式代码；地址所在国家等于辖区国家时为辖区格式，否则为 GENERIC。取值来源：t_address_format_component；非固定枚举。',
  `building_number` VARCHAR(32) NULL COMMENT '建筑号（如沙特国家地址 4 位建筑号）。',
  `additional_number` VARCHAR(32) NULL COMMENT '附加号（如沙特国家地址 4 位附加号）。',
  `district` VARCHAR(128) NULL COMMENT '区或街区名称。',
  `po_box` VARCHAR(32) NULL COMMENT '邮政信箱号（如阿联酋 PO Box）。',
  PRIMARY KEY (`address_id`),
  UNIQUE KEY `uk_profile_address_type` (`profile_id`, `address_type`) COMMENT 'Unique index for party_address on profile_id, address_type'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='主体地址；uk_profile_address_type(profile_id,address_type)；同一资料同一地址类型最多一条；组件允许部分草稿；提交时按 t_address_format_component 完整校验；批准后只读；应用校验，不创建 CHECK/FK';

CREATE TABLE `t_party_contact` (
  `contact_id` BIGINT NOT NULL COMMENT 'Party Profile 联系方式记录的内部唯一标识。',
  `profile_id` BIGINT NOT NULL COMMENT 'Party 资料头及其类型明细的内部唯一标识。',
  `contact_type` VARCHAR(64) NOT NULL COMMENT '区分邮箱与电话号码的闭合类型。允许值：EMAIL、PHONE。',
  `contact_vault_token` VARCHAR(128) NOT NULL COMMENT 'Vault 联系方式标量原值的逻辑引用，不建立跨库物理外键；EMAIL 对应 EMAIL_ADDRESS_V1，PHONE 对应 PHONE_E164_V1。',
  `contact_masked_value` VARCHAR(255) NOT NULL COMMENT 'Vault 按联系方式类型生成的最小受控展示值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`contact_id`),
  UNIQUE KEY `uk_profile_contact_type` (`profile_id`, `contact_type`) COMMENT 'Unique index for party_contact on profile_id, contact_type'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='主体联系方式；uk_profile_contact_type(profile_id,contact_type)；同一资料同一联系方式类型最多一条；可恢复原值由 Vault 持有，CCT 仅保存 Token 与脱敏展示值；应用校验，不创建 CHECK/FK；Email 使用 EMAIL_ADDRESS_V1 标量 Schema；Phone 使用 PHONE_E164_V1 标量 Schema';

CREATE TABLE `t_party_relation` (
  `relation_id` BIGINT NOT NULL COMMENT '组织与相关 Party 关系的稳定标识，不是关系版本标识。',
  `organization_party_id` BIGINT NOT NULL COMMENT 'Party Relation 中被持有、控制或任职的机构 Party 标识。',
  `related_party_id` BIGINT NOT NULL COMMENT 'Party Relation 中所有者、控制人、董事、签字人或 UBO 的 Party 标识。',
  `relation_type` VARCHAR(64) NOT NULL COMMENT '所有权、控制、任职或 UBO 等法律关系类型代码。取值来源：t_relation_type.relation_type_code；非固定枚举。',
  `ownership_percentage` DECIMAL(18,10) NULL COMMENT '关系类型要求持股比例时填写的百分比，取值范围为 0 至 100；其他关系类型为空。',
  `effective_from` DATETIME(6) NOT NULL COMMENT '业务记录生效区间的 UTC 微秒精度起点，包含该时刻。',
  `effective_to` DATETIME(6) NULL COMMENT '业务记录生效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。',
  `status` VARCHAR(64) NOT NULL COMMENT '当前业务记录在其所属聚合生命周期中的状态代码。允许值：DRAFT、ACTIVE、ENDED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  `client_application_id` BIGINT NULL COMMENT '拥有本次可编辑关系的 Client 申请；Client 新写必填，其他既有来源可空。',
  `ubo_basis` VARCHAR(32) NULL COMMENT '最终受益人的认定依据；仅 ubo_flag 关系类型填写。允许值：OWNERSHIP、CONTROL、SENIOR_MANAGING_OFFICIAL。',
  `job_title` VARCHAR(128) NULL COMMENT '相关人员在机构中的职务名称。',
  `nominee_flag` TINYINT(1) NULL COMMENT '是否为名义（代持）股东或董事。允许值：0、1、NULL。取值规则：NULL 表示未填写。',
  `control_nature` VARCHAR(255) NULL COMMENT '以控制而非持股认定时的控制方式说明。',
  PRIMARY KEY (`relation_id`),
  UNIQUE KEY `uk_party_relation_identity` (`organization_party_id`, `related_party_id`, `relation_type`) COMMENT 'Unique index for party_relation on organization_party_id, related_party_id, relation_type',
  KEY `idx_party_relation_person` (`related_party_id`) COMMENT 'Access index for party_relation on related_party_id',
  KEY `idx_party_relation_application` (`client_application_id`) COMMENT 'Access index for party_relation on client_application_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='主体关系；uk_party_relation_identity(organization_party_id,related_party_id,relation_type)；同一机构、相关主体和关系类型只保留一条稳定关系；关系类型来自 t_relation_type 目录；Client 草稿由 client_application_id 限定修改归属，批准后关系集合锁定；真实关系生效/结束日期保留；应用校验，不创建 CHECK/FK';

CREATE TABLE `t_party_role` (
  `party_role_id` BIGINT NOT NULL COMMENT '法律主体业务角色行的内部唯一标识。',
  `party_id` BIGINT NOT NULL COMMENT '自然人或机构法律身份根的内部唯一标识。',
  `role_code` VARCHAR(64) NOT NULL COMMENT '法律主体承担的业务角色代码。允许值：CLIENT、OPERATING_ENTITY、OE_PROVIDER、REFERRER、SPLIT_PAYEE。',
  `subject_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '角色对应的业务主体标识：CLIENT 为 client_id，OPERATING_ENTITY 为 entity_no，OE_PROVIDER/REFERRER 为 local_partner_id，均为十进制或编号文本。',
  `status` VARCHAR(64) NOT NULL COMMENT '当前业务记录在其所属聚合生命周期中的状态代码。允许值：ACTIVE、ENDED。',
  `active_slot` TINYINT(1) NULL COMMENT '同一角色同一主体同时只有一行有效的稀疏唯一槽位。允许值：1 或 NULL。取值规则：状态为 ACTIVE 时为 1，其他状态为 NULL。',
  `effective_from` DATETIME(6) NOT NULL COMMENT '业务记录生效区间的 UTC 微秒精度起点，包含该时刻。',
  `effective_to` DATETIME(6) NULL COMMENT '业务记录生效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`party_role_id`),
  UNIQUE KEY `uk_party_role_active` (`role_code`, `subject_id`, `active_slot`) COMMENT 'Unique index for party_role on role_code, subject_id, active_slot',
  KEY `idx_party_role_party` (`party_id`, `status`) COMMENT 'Access index for party_role on party_id, status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='法律主体统一角色索引；uk_party_role_active(role_code,subject_id,active_slot)；状态 ACTIVE/ENDED，仅 ACTIVE 的 active_slot=1，ENDED 填写 effective_to；Client 创建、Partner 角色维护时与所属根同一事务写入，是 Client / Operating Entity / Local Partner 根的非审计子行；应用校验，不创建 CHECK/FK';

CREATE TABLE `t_client` (
  `client_id` BIGINT NOT NULL COMMENT '平台 Client 角色的内部唯一标识。',
  `client_no` VARCHAR(64) NOT NULL COMMENT '供外部接口与业务查询使用的唯一 Client 编号。',
  `party_id` BIGINT NOT NULL COMMENT '自然人或机构法律身份根的内部唯一标识。',
  `status` VARCHAR(64) NOT NULL COMMENT '当前业务记录在其所属聚合生命周期中的状态代码。允许值：PENDING、ACTIVE、SUSPENDED、TERMINATED。',
  `activation_time` DATETIME(6) NULL COMMENT '业务角色首次进入可用状态的 UTC 微秒精度时刻。',
  `suspension_time` DATETIME(6) NULL COMMENT '业务角色进入暂停状态的 UTC 微秒精度时刻。',
  `termination_time` DATETIME(6) NULL COMMENT '业务角色终止并停止用于新业务的 UTC 微秒精度时刻。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  `jurisdiction_code` VARCHAR(16) NULL COMMENT 'Client 入驻所在辖区代码，随最近一次入驻申请写入。取值来源：t_jurisdiction；非固定枚举。',
  `responsible_partner_id` BIGINT NULL COMMENT '对该 Client 尽调负责的 PayFac Local Partner；由入驻码解析，平台自营为空。',
  PRIMARY KEY (`client_id`),
  UNIQUE KEY `uk_client_no` (`client_no`) COMMENT 'Unique index for client on client_no',
  UNIQUE KEY `uk_client_party` (`party_id`) COMMENT 'Unique index for client on party_id',
  KEY `idx_client_jurisdiction` (`jurisdiction_code`, `status`) COMMENT 'Access index for client on jurisdiction_code, status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Client 角色根；uk_client_no(client_no)、uk_client_party(party_id)；状态 PENDING/ACTIVE/SUSPENDED/TERMINATED；资格 REVOKED 时系统自动 ACTIVE → SUSPENDED';

CREATE TABLE `t_local_partner` (
  `local_partner_id` BIGINT NOT NULL COMMENT 'Local Partner 角色的内部唯一标识。',
  `partner_no` VARCHAR(64) NOT NULL COMMENT '供业务查询使用的唯一 Local Partner 编号。',
  `party_id` BIGINT NOT NULL COMMENT '自然人或机构法律身份根的内部唯一标识。',
  `status` VARCHAR(64) NOT NULL COMMENT '当前业务记录在其所属聚合生命周期中的状态代码。允许值：PENDING、ACTIVE、SUSPENDED、TERMINATED。',
  `activation_time` DATETIME(6) NULL COMMENT '业务角色首次进入可用状态的 UTC 微秒精度时刻。',
  `termination_time` DATETIME(6) NULL COMMENT '业务角色终止并停止用于新业务的 UTC 微秒精度时刻。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`local_partner_id`),
  UNIQUE KEY `uk_partner_no` (`partner_no`) COMMENT 'Unique index for local_partner on partner_no',
  UNIQUE KEY `uk_partner_party` (`party_id`) COMMENT 'Unique index for local_partner on party_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Local Partner 角色根；uk_partner_no(partner_no)、uk_partner_party(party_id)；Party 必须为 Organization';

CREATE TABLE `t_partner_role` (
  `partner_role_id` BIGINT NOT NULL COMMENT 'Local Partner 商业角色行的内部唯一标识。',
  `local_partner_id` BIGINT NOT NULL COMMENT '承担该角色的 Local Partner 标识。',
  `role_code` VARCHAR(64) NOT NULL COMMENT 'Local Partner 承担的商业角色代码。允许值：OE_PROVIDER、REFERRER。',
  `status` VARCHAR(64) NOT NULL COMMENT '当前业务记录在其所属聚合生命周期中的状态代码。允许值：ACTIVE、ENDED。',
  `active_slot` TINYINT(1) NULL COMMENT '同一 Local Partner 同一角色同时只有一行有效的稀疏唯一槽位。允许值：1 或 NULL。取值规则：状态为 ACTIVE 时为 1，其他状态为 NULL。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`partner_role_id`),
  UNIQUE KEY `uk_partner_role_active` (`local_partner_id`, `role_code`, `active_slot`) COMMENT 'Unique index for partner_role on local_partner_id, role_code, active_slot',
  KEY `idx_partner_role_code` (`role_code`, `status`) COMMENT 'Access index for partner_role on role_code, status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Local Partner 商业角色；uk_partner_role_active(local_partner_id,role_code,active_slot)；role_code IN (OE_PROVIDER,REFERRER)，状态 ACTIVE/ENDED，仅 ACTIVE 的 active_slot=1；LOCAL_PARTNER 根的非审计子行，增减角色推进根 BCR 版本并同步 t_party_role；仍有 ACTIVE 的 PARTNER 来源关系时不得结束 OE_PROVIDER；应用校验，不创建 CHECK/FK';

CREATE TABLE `t_operating_entity` (
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'CCT唯一不可变OE身份，CHAR(5)，创建时经营国家二位代码加001–999，如AE001；编号不参与路由、不复用。',
  `party_id` BIGINT NOT NULL COMMENT '自然人或机构法律身份根的内部唯一标识。',
  `operating_country` CHAR(2) NOT NULL COMMENT 'Operating Entity 开展业务的 ISO 3166-1 alpha-2 国家代码。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `status` VARCHAR(64) NOT NULL COMMENT '当前业务记录在其所属聚合生命周期中的状态代码。允许值：PENDING、ACTIVE、SUSPENDED、CLOSED。',
  `acquiring_qualification` VARCHAR(64) NOT NULL COMMENT 'Operating Entity 经收单行认定的收单资质档位：MERCHANT 只能作为自身商户收单、不能承接 Client；MERCHANT_RESALE_APPROVED 经收单行批准转售、只承接 MOR；PAYFAC 支付服务商资质、可承接 MOR 与 PSP。允许值：MERCHANT、MERCHANT_RESALE_APPROVED、PAYFAC。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`entity_no`),
  UNIQUE KEY `uk_entity_party` (`party_id`) COMMENT 'Unique index for operating_entity on party_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='法律卖方与 MID 持有人角色根；uk_entity_party(party_id)；entity_no 为唯一不可变主键，创建时经营国家二位代码加001–999，编号不参与路由，不复用已关闭编号；同一OE仅一个经营国家，法律Party不得改绑';

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
  UNIQUE KEY `uk_client_business_client` (`client_id`) COMMENT 'Unique index for client_business_profile on client_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='客户业务资料；uk_client_business_client(client_id)；每个 Client 一份业务资料；Client 填写经营说明，运营确认行业及 MCC；批准分类任务和确认人随资料冻结；应用校验，不创建 CHECK/FK';

CREATE TABLE `t_client_business_classification` (
  `classification_id` BIGINT NOT NULL COMMENT '经营分类行的内部唯一标识。',
  `business_profile_id` BIGINT NOT NULL COMMENT 'Client 业务资料版本的内部唯一标识。',
  `industry_code` VARCHAR(64) NOT NULL COMMENT '平台定义的经营行业代码。取值来源：平台行业目录；非固定枚举。',
  `mcc_code` VARCHAR(4) NOT NULL COMMENT '支付收单语义下的商户类别代码。取值来源：平台采用的 MCC 目录，MCC 必须为四位数字；非固定枚举。',
  `primary_slot` TINYINT(1) NULL COMMENT '用于保证同一 Business Profile 最多存在一组主要经营分类。允许值：1 或 NULL。取值规则：值为 1 时表示一组主要经营分类，其他情况为 NULL。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`classification_id`),
  UNIQUE KEY `uk_biz_class_pair` (`business_profile_id`, `industry_code`, `mcc_code`) COMMENT 'Unique index for client_business_classification on business_profile_id, industry_code, mcc_code',
  UNIQUE KEY `uk_biz_class_primary` (`business_profile_id`, `primary_slot`) COMMENT 'Unique index for client_business_classification on business_profile_id, primary_slot'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='客户经营分类：Business Profile 已批准的行业与 MCC 组合；uk_biz_class_pair(business_profile_id,industry_code,mcc_code)、uk_biz_class_primary(business_profile_id,primary_slot)；一个 Business Profile 1:N 拥有分类行，同一组合不得重复，Primary 为 1、其他为 NULL；industry_code 为平台开放行业目录代码，mcc_code 必须为保留前导零的四位数字字符串并存在于批准 MCC 目录；不允许 *，无子行 Canonical Hash';

CREATE TABLE `t_client_application` (
  `application_id` BIGINT NOT NULL COMMENT '业务申请记录的内部唯一标识。',
  `application_no` VARCHAR(64) NOT NULL COMMENT '供业务查询与人工操作使用的唯一申请编号。',
  `client_id` BIGINT NOT NULL COMMENT '平台 Client 角色的内部唯一标识。',
  `requested_by_account_id` BIGINT NOT NULL COMMENT '发起申请的 Platform CUSTOMER Account 外部稳定标识；一般可空，但 ONBOARDING 必填，不建立跨库物理外键。',
  `status` VARCHAR(64) NOT NULL COMMENT '入驻申请状态：DRAFT、SUBMITTED、UNDER_REVIEW、RETURNED、APPROVED、REJECTED、WITHDRAWN。允许值：DRAFT、SUBMITTED、UNDER_REVIEW、RETURNED、APPROVED、REJECTED、WITHDRAWN。',
  `active_slot` TINYINT(1) NULL COMMENT 'DRAFT、SUBMITTED、UNDER_REVIEW、RETURNED 为 1，终态为 NULL；同一 Account 的个人/组织 Client 入驻合计最多一个进行中申请；同一 Client 的原唯一约束保留。允许值：1 或 NULL。取值规则：DRAFT、SUBMITTED、UNDER_REVIEW、RETURNED 为 1；APPROVED、REJECTED、WITHDRAWN 为 NULL，禁止 0。',
  `due_diligence_case_id` BIGINT NULL COMMENT 'Risk & Compliance 尽调 Case 的逻辑引用，不建立跨库物理外键。',
  `qualification_decision_id` BIGINT NULL COMMENT '当前主审核任务产生的不可变资格决定 ID；新 Client 不携带决定版本。',
  `qualification_type` VARCHAR(64) NOT NULL COMMENT '提交时按申请角色和 Party 类型确定并冻结的 Risk 资格类型。允许值：INDIVIDUAL_KYC、ORGANIZATION_KYB。',
  `submitted_time` DATETIME(6) NULL COMMENT '业务申请正式提交并进入审核流程的 UTC 微秒精度时刻。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  `current_submission_id` VARCHAR(64) NULL COMMENT '当前正式提交命令 UUID；同次重试复用，退回重提更换；不是资料版本。',
  `application_type` VARCHAR(32) NOT NULL COMMENT '申请类型；本期只写入 ONBOARDING，PROFILE_AMENDMENT 由后续修订申请启用。允许值：ONBOARDING、PROFILE_AMENDMENT。',
  `jurisdiction_code` VARCHAR(16) NOT NULL COMMENT '申请所适用的辖区包代码，创建时确定。取值来源：t_jurisdiction；非固定枚举。',
  `organization_type_code` VARCHAR(64) NULL COMMENT '组织申请当前选择的组织类型代码；个人申请为空。取值来源：t_organization_type.type_code；非固定枚举。',
  `schema_id` BIGINT NULL COMMENT '申请冻结的采集 Schema 版本；草稿阶段更换组织类型时按新类型重新解析，提交后不可改；2026-10-27 之前的遗留申请没有 Schema，为空。',
  `onboarding_code_id` BIGINT NULL COMMENT '创建申请时使用的 PayFac 入驻码标识；无码为空。',
  `responsible_partner_id` BIGINT NULL COMMENT '由入驻码解析并冻结的责任 PayFac Local Partner；平台自营（无码）为空。',
  `submission_round` INT NOT NULL COMMENT '正式提交轮次：创建为 0，每次提交加一；退回后重提为下一轮。',
  `previous_case_id` BIGINT NULL COMMENT '退回重提时上一轮的 Risk & Compliance 尽调 Case，供新案件串联复用；首轮为空。',
  `review_due_time` DATETIME(6) NULL COMMENT '退回时审核给出的补件截止 UTC 时间；非退回状态为空或保留上次值。',
  PRIMARY KEY (`application_id`),
  UNIQUE KEY `uk_client_application_no` (`application_no`) COMMENT 'Unique index for client_application on application_no',
  UNIQUE KEY `uk_client_application_active` (`client_id`, `active_slot`) COMMENT 'Unique index for client_application on client_id, active_slot',
  UNIQUE KEY `uk_client_app_account_active` (`requested_by_account_id`, `active_slot`) COMMENT 'Unique index for client_application on requested_by_account_id, active_slot',
  UNIQUE KEY `uk_client_app_submission` (`current_submission_id`) COMMENT 'Unique index for client_application on current_submission_id',
  KEY `idx_client_app_requester` (`requested_by_account_id`, `status`) COMMENT 'Access index for client_application on requested_by_account_id, status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='客户申请；uk_client_application_no(application_no)、uk_client_application_active(client_id,active_slot)、uk_client_app_account_active(requested_by_account_id,active_slot)、uk_client_app_submission(current_submission_id)；DRAFT、RETURNED 可编辑并占用 active_slot；SUBMITTED、UNDER_REVIEW 只读并继续占用；APPROVED、REJECTED、WITHDRAWN 终态释放名额；DRAFT/RETURNED 可由 Client 撤回为 WITHDRAWN，审核退回超期由结果事件转为 WITHDRAWN；current_submission_id 隔离每次提交结果，旧审核任务结果不得覆盖当前任务；辖区、Schema、入驻码与责任方创建时冻结；应用校验，不创建 CHECK/FK。';

CREATE TABLE `t_partner_application` (
  `application_id` BIGINT NOT NULL COMMENT '业务申请记录的内部唯一标识。',
  `application_no` VARCHAR(64) NOT NULL COMMENT '供业务查询与人工操作使用的唯一申请编号。',
  `local_partner_id` BIGINT NOT NULL COMMENT '申请创建时同一 CCT 事务创建或复用的 PENDING Local Partner 角色标识；REJECTED/WITHDRAWN 重提继续复用，APPROVED 后才激活。',
  `target_party_profile_id` BIGINT NOT NULL COMMENT '申请创建时同一 CCT 事务创建或复用的 Organization Target Party Profile 标识。',
  `requested_by_account_id` BIGINT NOT NULL COMMENT '发起申请的 Platform CUSTOMER Account 外部稳定标识；Partner Application 必填，不建立跨库物理外键。',
  `status` VARCHAR(64) NOT NULL COMMENT '当前业务记录在其所属聚合生命周期中的状态代码。允许值：DRAFT、SUBMITTED、UNDER_REVIEW、APPROVED、REJECTED、WITHDRAWN。',
  `active_slot` TINYINT(1) NULL COMMENT '同一 Local Partner 的进行中申请稀疏唯一槽位；DRAFT、SUBMITTED、UNDER_REVIEW 占用，终态释放，不表示 ACTIVE 业务状态。允许值：1 或 NULL。取值规则：状态为 DRAFT、SUBMITTED 或 UNDER_REVIEW 时为 1，终态为 NULL。',
  `due_diligence_case_id` BIGINT NULL COMMENT 'Risk & Compliance 尽调 Case 的逻辑引用；APPROVED 必填，不建立跨库物理外键。',
  `qualification_decision_id` BIGINT NULL COMMENT 'Risk & Compliance 资格决定的逻辑引用，与 qualification_decision_version 成组为空或成组非空；APPROVED 必填，不建立跨库物理外键。',
  `qualification_decision_version` BIGINT NULL COMMENT 'Risk & Compliance 决定的外部版本坐标，与决定标识成组出现。',
  `qualification_type` VARCHAR(64) NOT NULL COMMENT '系统固定为 PARTNER_DUE_DILIGENCE，不接受调用方选择，并在申请提交时冻结。允许值：PARTNER_DUE_DILIGENCE。',
  `submitted_time` DATETIME(6) NULL COMMENT '业务申请正式提交并进入审核流程的 UTC 微秒精度时刻。',
  `submission_hash_schema_version` INT NOT NULL COMMENT '解释申请提交包 Canonical Hash 输入对象、排序与编码规则的版本号。',
  `submission_content_hash` CHAR(64) NOT NULL COMMENT '输入遵循 CODE-MOR:CCT:PARTNER_APPLICATION_SUBMISSION:1，使用 UTF-8 RFC 8785/JCS 后的 SHA-256 小写 64 位 Hex。',
  `create_by` BIGINT NOT NULL COMMENT '记录该可变生命周期申请行的 CCT 录入或命令 Actor 标识；不同于 requested_by_account_id，后者仅是外部 Platform CUSTOMER Account 申请人且不证明法律主体或签约资格。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`application_id`),
  UNIQUE KEY `uk_partner_app_no` (`application_no`) COMMENT 'Unique index for partner_application on application_no',
  UNIQUE KEY `uk_partner_app_active` (`local_partner_id`, `active_slot`) COMMENT 'Unique index for partner_application on local_partner_id, active_slot',
  KEY `idx_partner_application_7c869459` (`target_party_profile_id`) COMMENT 'Access index for partner_application on target_party_profile_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Local Partner 入驻申请；uk_partner_app_no(application_no)、uk_partner_app_active(local_partner_id,active_slot)；状态精确为 DRAFT/SUBMITTED/UNDER_REVIEW/APPROVED/REJECTED/WITHDRAWN，前三态 slot=1；qualification_type=PARTNER_DUE_DILIGENCE；创建申请的同一事务创建或复用一个 PENDING Local Partner、Organization Party 和 Target Party Profile，只有 APPROVED 激活 Role，REJECTED/WITHDRAWN 重提复用该 PENDING Role；Risk Decision ID/Version 成组为空或非空，APPROVED 必须具备 Case 与 Decision Tuple，WITHDRAWN 可无 Decision；CHECK ((status IN (''DRAFT'',''SUBMITTED'',''UNDER_REVIEW'') AND active_slot=1) OR (status IN (''APPROVED'',''REJECTED'',''WITHDRAWN'') AND active_slot IS NULL))；CHECK ((qualification_decision_id IS NULL) = (qualification_decision_version IS NULL))；Risk Tuple 固定映射：role_type=LOCAL_PARTNER、role_id=local_partner_id、qualification_type=PARTNER_DUE_DILIGENCE。；requested_by_account_id 是 Platform CUSTOMER Account 的外部稳定引用，不建立跨库物理外键。；due_diligence_case_id、qualification_decision_id 与 qualification_decision_version 是 Risk & Compliance 外部稳定引用，不建立跨库物理外键。；qualification_type 固定由系统写入 PARTNER_DUE_DILIGENCE，不接受调用方输入。；APPROVED 前 due_diligence_case_id、qualification_decision_id、qualification_decision_version、qualification_type、role_type、role_id 必须匹配同一次 Risk 决策；WITHDRAWN 不要求 Decision。；创建申请时必须在同一 CCT 本地事务创建或复用一个 PENDING Local Partner、Organization Party 和 Target Party Profile；只有 APPROVED 激活 Role，REJECTED/WITHDRAWN 重提复用该 PENDING Role。';

CREATE TABLE `t_operating_entity_application` (
  `application_id` BIGINT NOT NULL COMMENT '业务申请记录的内部唯一标识。',
  `application_no` VARCHAR(64) NOT NULL COMMENT '供业务查询与人工操作使用的唯一申请编号。',
  `application_type` VARCHAR(64) NOT NULL COMMENT '区分首次入驻与资料变更申请的闭合业务类型。允许值：ONBOARDING、SCOPE_CHANGE。',
  `base_entity_change_version` BIGINT NOT NULL COMMENT '提交时OE根BCR修订，批准应用前必须一致，防止过时决定覆盖新配置。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '申请创建时冻结的稳定 Operating Entity 标识；ONBOARDING 拒绝或撤回后重提复用同一 PENDING 主体；SCOPE_CHANGE 沿用既有主体且不回退其状态。',
  `party_profile_id` BIGINT NOT NULL COMMENT '本次申请冻结审核的机构 Party Profile 版本标识，必须属于 entity_no 对应的 Party。',
  `source_type` VARCHAR(64) NOT NULL COMMENT '本次 Operating Entity 入驻申请的法律来源类型，提交 Actor 或入口渠道不由该字段表达。允许值：CLIENT、PARTNER。',
  `source_client_id` BIGINT NULL COMMENT 'source_type=CLIENT 时提交自身或经平台审核关联经营主体的 Organization Client 标识，其他来源为空。',
  `source_partner_id` BIGINT NULL COMMENT 'source_type=PARTNER 时提交经营主体的 Local Partner 标识，其他来源为空。',
  `client_source_basis` VARCHAR(64) NULL COMMENT 'source_type=CLIENT 时说明目标 Party 与 Client 是同一法律主体或经材料和资格审核确认的关联主体；其他来源为空。允许值：SAME_PARTY、VERIFIED_ASSOCIATION。',
  `status` VARCHAR(64) NOT NULL COMMENT '当前业务记录在其所属聚合生命周期中的状态代码。允许值：DRAFT、SUBMITTED、UNDER_REVIEW、APPROVED、REJECTED、WITHDRAWN。',
  `active_slot` TINYINT(1) NULL COMMENT '同一 Operating Entity 的进行中申请稀疏唯一槽位；DRAFT、SUBMITTED、UNDER_REVIEW 占用，终态释放，不表示 ACTIVE 业务状态。允许值：1 或 NULL。取值规则：状态为 DRAFT、SUBMITTED 或 UNDER_REVIEW 时为 1，终态为 NULL。',
  `due_diligence_case_id` BIGINT NULL COMMENT 'Risk & Compliance 尽调 Case 的逻辑引用，不建立跨库物理外键。',
  `qualification_decision_id` BIGINT NULL COMMENT 'Risk & Compliance 资格决定的逻辑引用，与 qualification_decision_version 成组为空或成组非空，不建立跨库物理外键。',
  `qualification_decision_version` BIGINT NULL COMMENT 'Risk & Compliance 决定的外部版本坐标，与决定标识成组出现。',
  `qualification_type` VARCHAR(64) NOT NULL COMMENT 'ONBOARDING 固定为 ENTITY_UNDERWRITING，SCOPE_CHANGE 固定为 ENTITY_SCOPE_REVIEW；Risk 新契约接入前禁止激活，不按 Party Type 推导，不接受调用方选择，并在申请提交时冻结。允许值：ENTITY_UNDERWRITING、ENTITY_SCOPE_REVIEW。',
  `submitted_time` DATETIME(6) NULL COMMENT '业务申请正式提交并进入审核流程的 UTC 微秒精度时刻。',
  `submission_hash_schema_version` INT NOT NULL COMMENT '解释申请提交包 Canonical Hash 输入对象、排序与编码规则的版本号。',
  `submission_content_hash` CHAR(64) NOT NULL COMMENT '输入遵循 CODE-MOR:CCT:OPERATING_ENTITY_APPLICATION_SUBMISSION:3，使用 UTF-8 RFC 8785/JCS 后的 SHA-256 小写 64 位 Hex。',
  `approved_source_relation_id` BIGINT NULL COMMENT '仅在申请 APPROVED 时回填且匹配申请来源和经营主体的来源关系标识；ONBOARDING 创建或激活来源关系，SCOPE_CHANGE 沿用现有唯一来源关系。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`application_id`),
  UNIQUE KEY `uk_oe_app_no` (`application_no`) COMMENT 'Unique index for operating_entity_application on application_no',
  UNIQUE KEY `uk_oe_app_active` (`entity_no`, `active_slot`) COMMENT 'Unique index for operating_entity_application on entity_no, active_slot',
  KEY `idx_operating_entity_ap_6d810e59` (`approved_source_relation_id`) COMMENT 'Access index for operating_entity_application on approved_source_relation_id',
  KEY `idx_operating_entity_ap_f2fe417d` (`party_profile_id`) COMMENT 'Access index for operating_entity_application on party_profile_id',
  KEY `idx_operating_entity_ap_5dd3d5f9` (`source_client_id`) COMMENT 'Access index for operating_entity_application on source_client_id',
  KEY `idx_operating_entity_ap_941a9370` (`source_partner_id`) COMMENT 'Access index for operating_entity_application on source_partner_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='OE入驻或完整目标范围变更申请；uk_oe_app_no(application_no)、uk_oe_app_active(entity_no,active_slot)；申请状态/Slot 与 Client Application 相同；entity_no 非空稳定，ONBOARDING 使用 ENTITY_UNDERWRITING，SCOPE_CHANGE 使用 ENTITY_SCOPE_REVIEW（Risk 接入待后续实施）；仅 APPROVED 的 approved_source_relation_id 非空；application_type=ONBOARDING/SCOPE_CHANGE，Scope批准时核对base_entity_change_version；Risk新审核类型尚未接入时禁止自动激活范围；CHECK ((status IN (''DRAFT'',''SUBMITTED'',''UNDER_REVIEW'') AND active_slot=1) OR (status IN (''APPROVED'',''REJECTED'',''WITHDRAWN'') AND active_slot IS NULL))；CHECK ((qualification_decision_id IS NULL) = (qualification_decision_version IS NULL))；CHECK ((status=''APPROVED'' AND approved_source_relation_id IS NOT NULL) OR (status<>''APPROVED'' AND approved_source_relation_id IS NULL))；Risk Tuple 固定映射：role_type=OPERATING_ENTITY、role_id=entity_no、ONBOARDING 的 qualification_type=ENTITY_UNDERWRITING；SCOPE_CHANGE 的 qualification_type=ENTITY_SCOPE_REVIEW。；qualification_decision_id 与 qualification_decision_version 必须成组为空或成组非空。；APPROVED/REJECTED 前 due_diligence_case_id、qualification_decision_id、qualification_decision_version、qualification_type、role_type、role_id 必须匹配同一次 Risk 决策；WITHDRAWN 不要求 Decision。；qualification_type 由系统按申请类型固定为 ENTITY_UNDERWRITING 或 ENTITY_SCOPE_REVIEW，不接受调用方输入。；提交后 source_type、source_client_id、source_partner_id、client_source_basis 与 qualification_type 组成的 Source/Qualification Tuple 冻结且不可修改。；approved_source_relation_id 仅在 APPROVED 时非空，其他状态必须为空。；Source Owner XOR：CLIENT 要求 source_client_id IS NOT NULL AND source_partner_id IS NULL；PARTNER 要求 source_client_id IS NULL AND source_partner_id IS NOT NULL。；CLIENT 来源依据：SAME_PARTY 要求 Client Party=Operating Entity Party；VERIFIED_ASSOCIATION 要求两个 Party 不同，并由已批准申请、冻结材料和匹配的 Risk Qualification Decision 支撑；非 CLIENT 要求 client_source_basis IS NULL。；SCOPE_CHANGE 必须核对 base_entity_change_version 与完整目标 Scope，沿用既有主体和唯一来源关系，不回退主体状态；过时或拒绝的审核不能撤销现有范围。Risk 新 Tuple 未接入时禁止该流程激活范围。';

CREATE TABLE `t_operating_entity_scope` (
  `scope_id` BIGINT NOT NULL COMMENT 'OE准确四要素范围行的稳定标识，由申请创建，批准后供交易引用。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'CCT唯一不可变OE身份，CHAR(5)，创建时经营国家二位代码加001–999，如AE001；编号不参与路由、不复用。',
  `application_id` BIGINT NOT NULL COMMENT '业务申请记录的内部唯一标识。',
  `consumer_country_code` CHAR(2) NOT NULL COMMENT '准确匹配的消费者国家代码。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `currency` CHAR(3) NOT NULL COMMENT '准确匹配的交易币种。取值来源：ISO 4217；非固定枚举。',
  `industry_code` VARCHAR(64) NOT NULL COMMENT '平台定义的经营行业代码。取值来源：平台行业目录；非固定枚举。',
  `mcc_code` VARCHAR(4) NOT NULL COMMENT 'MCC 必须为保留前导零的四位数字字符串，存在于已批准目录。取值来源：平台四位MCC目录；非固定枚举。',
  `status` VARCHAR(64) NOT NULL COMMENT '当前业务记录在其所属聚合生命周期中的状态代码。允许值：DRAFT、ACTIVE、ENDED。',
  `active_slot` TINYINT(1) NULL COMMENT '以 1 或 NULL 实现条件稀疏唯一约束的技术槽位，具体占槽含义由表级规则定义。允许值：1 或 NULL。取值规则：仅ACTIVE为1，DRAFT和ENDED为NULL。',
  `effective_from` DATETIME(6) NULL COMMENT '业务记录生效区间的 UTC 微秒精度起点，包含该时刻。',
  `effective_to` DATETIME(6) NULL COMMENT '业务记录生效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`scope_id`),
  UNIQUE KEY `uk_oe_scope_application` (`application_id`, `consumer_country_code`, `currency`, `industry_code`, `mcc_code`) COMMENT 'Unique index for operating_entity_scope on application_id, consumer_country_code, currency, industry_code, mcc_code',
  UNIQUE KEY `uk_oe_scope_active` (`entity_no`, `consumer_country_code`, `currency`, `industry_code`, `mcc_code`, `active_slot`) COMMENT 'Unique index for operating_entity_scope on entity_no, consumer_country_code, currency, industry_code, mcc_code, active_slot',
  KEY `idx_oe_scope_lookup` (`entity_no`, `consumer_country_code`, `currency`, `industry_code`, `mcc_code`, `status`) COMMENT 'Access index for operating_entity_scope on entity_no, consumer_country_code, currency, industry_code, mcc_code, status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='OE申请目标及批准的准确交易准入组合；uk_oe_scope_application(application_id,consumer_country_code,currency,industry_code,mcc_code)、uk_oe_scope_active(entity_no,consumer_country_code,currency,industry_code,mcc_code,active_slot)；DRAFT/ACTIVE/ENDED；ACTIVE占slot=1，其他NULL；Draft时有效期为空；完整范围批准后原子替换，拒绝或过时审核不撤销原范围；无通配、无独立BCR根';

CREATE TABLE `t_application_document` (
  `document_id` BIGINT NOT NULL COMMENT '业务资料包或合同文档引用记录的内部唯一标识。',
  `client_application_id` BIGINT NULL COMMENT 'Client Application 父标识；与 partner_application_id、operating_entity_application_id 三个父标识恰好一个非空，使用本库 RESTRICT 外键。',
  `partner_application_id` BIGINT NULL COMMENT 'Local Partner Application 父标识；与 client_application_id、operating_entity_application_id 三个父标识恰好一个非空，使用本库 RESTRICT 外键。',
  `operating_entity_application_id` BIGINT NULL COMMENT 'Operating Entity Application 父标识；与 client_application_id、partner_application_id 三个父标识恰好一个非空，使用本库 RESTRICT 外键。',
  `subject_party_id` BIGINT NOT NULL COMMENT '材料所证明的申请主 Party 或 UBO、Director、Control 图节点，使用本库 Party 外键。',
  `requirement_code` VARCHAR(64) NOT NULL COMMENT 'Risk 冻结材料要求目录中的稳定开放编码。取值来源：Risk 冻结材料要求目录；非固定枚举。',
  `document_type` VARCHAR(64) NOT NULL COMMENT '文档用途或法律类别的受控业务代码。取值来源：申请材料类型目录；非固定枚举。',
  `file_address` VARCHAR(255) NOT NULL COMMENT 'Platform 文件服务签发的稳定逻辑地址，格式 /files/v1/{fileId}，与 content_hash 成组冻结；不是下载链接，不含存储实例与桶，只能经文件服务换取临时下载地址；不建立跨库物理外键。',
  `content_hash` CHAR(64) NOT NULL COMMENT 'Platform 文件内容哈希；输入为 Platform File 返回的文件正文 SHA-256，与 file_address 成组冻结。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  `issue_date` DATE NULL COMMENT '材料文件的签发或出具日期，用于程序 max_age_days 时效校验；未提供为空。',
  `expiry_date` DATE NULL COMMENT '材料文件的到期日（含当日有效）；未提供或不到期为空。',
  PRIMARY KEY (`document_id`),
  UNIQUE KEY `uk_app_doc_client` (`client_application_id`, `subject_party_id`, `requirement_code`, `document_type`, `file_address`) COMMENT 'Unique index for application_document on client_application_id, subject_party_id, requirement_code, document_type, file_address',
  UNIQUE KEY `uk_app_doc_partner` (`partner_application_id`, `subject_party_id`, `requirement_code`, `document_type`, `file_address`) COMMENT 'Unique index for application_document on partner_application_id, subject_party_id, requirement_code, document_type, file_address',
  UNIQUE KEY `uk_app_doc_entity` (`operating_entity_application_id`, `subject_party_id`, `requirement_code`, `document_type`, `file_address`) COMMENT 'Unique index for application_document on operating_entity_application_id, subject_party_id, requirement_code, document_type, file_address',
  KEY `idx_application_documen_7785fdda` (`subject_party_id`) COMMENT 'Access index for application_document on subject_party_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='三类申请共享的材料引用；uk_app_doc_client(client_application_id,subject_party_id,requirement_code,document_type,file_address)、uk_app_doc_partner(partner_application_id,subject_party_id,requirement_code,document_type,file_address)、uk_app_doc_entity(operating_entity_application_id,subject_party_id,requirement_code,document_type,file_address)；三个 Parent FK 均可空但满足 (client_application_id IS NOT NULL) + (partner_application_id IS NOT NULL) + (operating_entity_application_id IS NOT NULL) = 1，且均为本库 RESTRICT；subject_party_id -> party 为真实 FK；CHECK ((client_application_id IS NOT NULL) + (partner_application_id IS NOT NULL) + (operating_entity_application_id IS NOT NULL) = 1)；client_application_id、partner_application_id 与 operating_entity_application_id 三个 Parent FK 均为本库 RESTRICT。';

CREATE TABLE `t_jurisdiction` (
  `jurisdiction_code` VARCHAR(16) NOT NULL COMMENT '适用一套采集与核验规则的辖区代码，默认等于 ISO 3166-1 alpha-2 国家码。取值来源：辖区包 DML；非固定枚举。',
  `country_code` CHAR(2) NOT NULL COMMENT '辖区所属国家的 ISO 3166-1 alpha-2 代码。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `label_key` VARCHAR(128) NOT NULL COMMENT '辖区展示名称的界面文案键，命名空间 onboardingSchema；文案由前端语言包提供。',
  `address_format_code` VARCHAR(32) NOT NULL COMMENT '辖区本地地址格式代码，对应 t_address_format_component 的一组地址组件。取值来源：地址格式组件目录；非固定枚举。',
  `status` VARCHAR(16) NOT NULL COMMENT '辖区包状态；只有 ACTIVE 对 Client 可见。允许值：DRAFT、ACTIVE、RETIRED。',
  `change_version` BIGINT NOT NULL COMMENT '辖区包目录的乐观并发版本，DML 修改时加一；Client 不可修改。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`jurisdiction_code`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='辖区包；只保存身份与展示，规则开关在 t_rule_setting；新国家只需 DML，不改代码；目录不进 BCR，按 change_version 与操作日志管理';

CREATE TABLE `t_rule_setting` (
  `rule_setting_id` BIGINT NOT NULL COMMENT '规则开关行的内部唯一标识。',
  `scope_type` VARCHAR(32) NOT NULL COMMENT '规则开关作用的目录层级。允许值：JURISDICTION、ORGANIZATION_TYPE、IDENTIFIER_TYPE。',
  `scope_code` VARCHAR(64) NOT NULL COMMENT '作用对象代码：辖区为辖区码，组织类型为 辖区码:组织类型码，标识类型为标识类型码；* 表示该层级的全局默认。',
  `rule_code` VARCHAR(64) NOT NULL COMMENT '已在代码 RuleCode 注册表登记的规则代码；未登记的代码被忽略并告警。取值来源：RuleCode 注册表；非固定枚举。',
  `rule_value` VARCHAR(64) NOT NULL COMMENT '规则取值的文本形式；数值、0/1 布尔或代码由对应 rule_code 定义解析。',
  `status` VARCHAR(16) NOT NULL COMMENT '规则行状态；只有 ACTIVE 参与解析。允许值：ACTIVE、RETIRED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`rule_setting_id`),
  UNIQUE KEY `uk_rule_setting` (`scope_type`, `scope_code`, `rule_code`) COMMENT 'Unique index for rule_setting on scope_type, scope_code, rule_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='规则开关（键值，一值一行）；uk_rule_setting(scope_type,scope_code,rule_code)；解析顺序为精确 scope_code、再取同层 * 默认、再取代码内置默认；新规则类型只需登记 rule_code 并增加读取它的代码分支，不改表';

CREATE TABLE `t_organization_type` (
  `organization_type_id` BIGINT NOT NULL COMMENT '组织类型目录行的内部唯一标识。',
  `jurisdiction_code` VARCHAR(16) NOT NULL COMMENT '组织类型所属辖区代码。取值来源：t_jurisdiction；非固定枚举。',
  `type_code` VARCHAR(64) NOT NULL COMMENT '辖区内组织法律形态代码，如 LLC、FZ_LLC。取值来源：辖区组织类型目录 DML；非固定枚举。',
  `label_key` VARCHAR(128) NOT NULL COMMENT '组织类型展示名称的界面文案键，命名空间 onboardingSchema。',
  `display_order` INT NOT NULL COMMENT '同一辖区内的展示顺序，数值越小越靠前。',
  `status` VARCHAR(16) NOT NULL COMMENT '目录行状态；只有 ACTIVE 可被新申请选择。允许值：DRAFT、ACTIVE、RETIRED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`organization_type_id`),
  UNIQUE KEY `uk_org_type` (`jurisdiction_code`, `type_code`) COMMENT 'Unique index for organization_type on jurisdiction_code, type_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='组织类型目录；uk_org_type(jurisdiction_code,type_code)；只保存身份与展示，规则在 t_rule_setting(scope_type=ORGANIZATION_TYPE, scope_code=辖区码:类型码)';

CREATE TABLE `t_identifier_type` (
  `identifier_type_id` BIGINT NOT NULL COMMENT '法律标识类型目录行的内部唯一标识。',
  `identifier_type_code` VARCHAR(64) NOT NULL COMMENT '全局唯一的法律标识类型代码，如 SA_NATIONAL_ID、PASSPORT。取值来源：标识类型目录 DML；非固定枚举。',
  `jurisdiction_code` VARCHAR(16) NULL COMMENT '标识类型所属辖区；为空表示全局通用类型（如 PASSPORT、LEI）。取值来源：t_jurisdiction；非固定枚举。',
  `party_type` VARCHAR(16) NOT NULL COMMENT '标识适用的法律主体类型。允许值：INDIVIDUAL、ORGANIZATION。',
  `label_key` VARCHAR(128) NOT NULL COMMENT '标识类型展示名称的界面文案键，命名空间 onboardingSchema。',
  `format_regex` VARCHAR(255) NULL COMMENT '规范化后号码必须完整匹配的正则表达式；为空表示只做通用字符校验。',
  `normalization_rule` VARCHAR(32) NOT NULL COMMENT '号码规范化规则代码，由代码注册表解释。取值来源：规范化规则注册表（STANDARD：NFKC、去空白与常见分隔符、转大写）；非固定枚举。',
  `checksum_rule` VARCHAR(32) NOT NULL COMMENT '号码校验位算法代码，由代码注册表解释；未登记的代码失败关闭。取值来源：校验位算法注册表（NONE、LUHN、EG_NID）；非固定枚举。',
  `issuer_scope_mode` VARCHAR(16) NOT NULL COMMENT '号码唯一性作用域的确定方式：NONE 为全局，COUNTRY 按签发国，AUTHORITY 按组织登记机关。允许值：NONE、COUNTRY、AUTHORITY。',
  `verification_check_code` VARCHAR(64) NULL COMMENT '后续外部核验使用的 channel-connector 标准 API 代码；本期只登记不调用。取值来源：标准 API 目录；非固定枚举。',
  `status` VARCHAR(16) NOT NULL COMMENT '目录行状态；只有 ACTIVE 可被新草稿选择。允许值：DRAFT、ACTIVE、RETIRED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`identifier_type_id`),
  UNIQUE KEY `uk_identifier_type` (`identifier_type_code`) COMMENT 'Unique index for identifier_type on identifier_type_code',
  KEY `idx_identifier_type_scope` (`jurisdiction_code`, `party_type`) COMMENT 'Access index for identifier_type on jurisdiction_code, party_type'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='法律标识类型目录；uk_identifier_type(identifier_type_code)；格式、规范化、校验位与作用域在本表，ACQUIRE_MODE、EXPIRY_REQUIRED、VAULT_REQUIRED 在 t_rule_setting(scope_type=IDENTIFIER_TYPE)';

CREATE TABLE `t_relation_type` (
  `relation_type_id` BIGINT NOT NULL COMMENT '主体关系类型目录行的内部唯一标识。',
  `relation_type_code` VARCHAR(64) NOT NULL COMMENT '全局唯一的组织与相关主体关系类型代码，如 UBO、SIGNATORY。取值来源：关系类型目录 DML；非固定枚举。',
  `jurisdiction_code` VARCHAR(16) NULL COMMENT '关系类型限定的辖区；为空表示全局可用。取值来源：t_jurisdiction；非固定枚举。',
  `rule_role` VARCHAR(32) NOT NULL COMMENT '关系人规则校验识别的规则角色，供签字人、董事、经理与控制人门槛计数。允许值：OWNER、UBO、CONTROL、DIRECTOR、MANAGER、SIGNATORY、OTHER。',
  `ubo_flag` TINYINT(1) NOT NULL COMMENT '是否计入最终受益人门槛与 100% 持股合计。允许值：0、1。',
  `ownership_pct_required` TINYINT(1) NOT NULL COMMENT '该关系类型是否必须填写持股比例。允许值：0、1。',
  `label_key` VARCHAR(128) NOT NULL COMMENT '关系类型展示名称的界面文案键，命名空间 onboardingSchema。',
  `display_order` INT NOT NULL COMMENT '展示顺序，数值越小越靠前。',
  `status` VARCHAR(16) NOT NULL COMMENT '目录行状态；只有 ACTIVE 可被新关系选择。允许值：DRAFT、ACTIVE、RETIRED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`relation_type_id`),
  UNIQUE KEY `uk_relation_type` (`relation_type_code`) COMMENT 'Unique index for relation_type on relation_type_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='主体关系类型目录；uk_relation_type(relation_type_code)；取代原闭合枚举 OWNERSHIP/CONTROL/DIRECTOR/SIGNATORY/UBO（五值作为全局种子）；关系人门槛只按 rule_role 与 ubo_flag 计数';

CREATE TABLE `t_address_format_component` (
  `component_id` BIGINT NOT NULL COMMENT '地址格式组件行的内部唯一标识。',
  `address_format_code` VARCHAR(32) NOT NULL COMMENT '地址格式代码，与 t_jurisdiction.address_format_code 对应；GENERIC 为境外地址通用格式。取值来源：地址格式 DML；非固定枚举。',
  `component` VARCHAR(32) NOT NULL COMMENT '地址组件，对应 t_party_address 的一列及草稿 address 分区字段。允许值：addressLine1、addressLine2、city、stateOrProvince、postalCode、countryCode、buildingNumber、additionalNumber、district、poBox。',
  `required_flag` TINYINT(1) NOT NULL COMMENT '提交时该组件是否必填。允许值：0、1。',
  `catalog_code` VARCHAR(64) NULL COMMENT '组件取值必须属于的通用目录代码，如省、城市；为空表示自由文本。取值来源：t_catalog_item.catalog_code；非固定枚举。',
  `label_key` VARCHAR(128) NOT NULL COMMENT '组件展示名称的界面文案键，命名空间 onboardingSchema。',
  `display_order` INT NOT NULL COMMENT '同一地址格式内的展示顺序，数值越小越靠前。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`component_id`),
  UNIQUE KEY `uk_address_component` (`address_format_code`, `component`) COMMENT 'Unique index for address_format_component on address_format_code, component'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='地址格式组件；uk_address_component(address_format_code,component)；通用地址控件按本表渲染、后端按本表校验，新国家不改地址代码';

CREATE TABLE `t_catalog_item` (
  `catalog_item_id` BIGINT NOT NULL COMMENT '通用目录项的内部唯一标识。',
  `catalog_code` VARCHAR(64) NOT NULL COMMENT '目录代码，如 INDUSTRY、BUSINESS_MODEL、REGISTRATION_AUTHORITY、CITY。取值来源：目录 DML；非固定枚举。',
  `item_code` VARCHAR(64) NOT NULL COMMENT '目录内稳定的取值代码。取值来源：目录 DML；非固定枚举。',
  `jurisdiction_code` VARCHAR(16) NOT NULL COMMENT '目录项适用的辖区代码；* 表示全局通用。取值来源：t_jurisdiction 或 *；非固定枚举。',
  `label_key` VARCHAR(128) NOT NULL COMMENT '目录项展示名称的界面文案键，命名空间 onboardingSchema。',
  `client_types` VARCHAR(64) NULL COMMENT '目录项适用的 Client 类型，逗号分隔；为空表示个人与组织都适用。允许值：INDIVIDUAL、ORGANIZATION 的组合。',
  `display_order` INT NOT NULL COMMENT '同一目录内的展示顺序，数值越小越靠前。',
  `status` VARCHAR(16) NOT NULL COMMENT '目录项状态；只有 ACTIVE 可被选择与校验通过。允许值：DRAFT、ACTIVE、RETIRED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`catalog_item_id`),
  UNIQUE KEY `uk_catalog_item` (`catalog_code`, `jurisdiction_code`, `item_code`) COMMENT 'Unique index for catalog_item on catalog_code, jurisdiction_code, item_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='通用目录项；uk_catalog_item(catalog_code,jurisdiction_code,item_code)；SELECT 控件、属性与地址组件的取值来源；行业与经营模式目录由原 application.yml 迁入，运营分类与行业目录接口同样读取';

CREATE TABLE `t_attribute_definition` (
  `attribute_code` VARCHAR(64) NOT NULL COMMENT '扩展属性代码，全局唯一，如 AE_FREE_ZONE_CODE、EG_TAX_CARD_NUMBER。取值来源：属性定义 DML；非固定枚举。',
  `jurisdiction_code` VARCHAR(16) NULL COMMENT '属性适用的辖区；为空表示全局属性。取值来源：t_jurisdiction；非固定枚举。',
  `party_type` VARCHAR(16) NOT NULL COMMENT '属性适用的法律主体类型。允许值：INDIVIDUAL、ORGANIZATION。',
  `data_type` VARCHAR(16) NOT NULL COMMENT '属性值的类型，决定存储列与草稿校验。允许值：TEXT、DATE、COUNTRY、CODE、DECIMAL。',
  `catalog_code` VARCHAR(64) NULL COMMENT 'CODE 类型属性取值所属的通用目录代码；其他类型为空。取值来源：t_catalog_item.catalog_code；非固定枚举。',
  `sensitivity` VARCHAR(16) NOT NULL COMMENT '属性敏感级别；本期只支持 PUBLIC 与 INTERNAL，VAULT 级属性的保存失败关闭。允许值：PUBLIC、INTERNAL、VAULT。',
  `vault_data_type` VARCHAR(64) NULL COMMENT 'VAULT 级属性使用的 Vault 数据类型；其他级别为空。取值来源：Vault 数据类型目录；非固定枚举。',
  `max_length` INT NULL COMMENT 'TEXT 类型属性允许的最大字符数；为空时取 255。',
  `format_regex` VARCHAR(255) NULL COMMENT 'TEXT 类型属性规范化后必须完整匹配的正则表达式；为空表示不限格式。',
  `label_key` VARCHAR(128) NOT NULL COMMENT '属性展示名称的界面文案键，命名空间 onboardingSchema。',
  `status` VARCHAR(16) NOT NULL COMMENT '定义状态；只有 ACTIVE 可被新草稿写入。允许值：DRAFT、ACTIVE、RETIRED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`attribute_code`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='扩展属性定义；辖区特有事实（自贸区代码、税卡号、职业等）以 DML 增加，不改表；被逻辑使用且两个以上辖区必填时再升为核心列';

CREATE TABLE `t_party_attribute` (
  `attribute_id` BIGINT NOT NULL COMMENT '扩展属性值行的内部唯一标识。',
  `profile_id` BIGINT NOT NULL COMMENT '属性所属 Party 资料的标识。',
  `attribute_code` VARCHAR(64) NOT NULL COMMENT '扩展属性代码。取值来源：t_attribute_definition；非固定枚举。',
  `value_text` VARCHAR(512) NULL COMMENT 'TEXT、COUNTRY、CODE 类型属性的规范化取值；其他类型为空。',
  `value_date` DATE NULL COMMENT 'DATE 类型属性的日期取值；其他类型为空。',
  `value_decimal` DECIMAL(24,8) NULL COMMENT 'DECIMAL 类型属性的精确数值；其他类型为空。',
  `value_vault_token` VARCHAR(128) NULL COMMENT 'VAULT 级属性的 Vault Token；本期没有 VAULT 级属性，恒为空。',
  `masked_value` VARCHAR(64) NULL COMMENT 'VAULT 级属性的脱敏展示值；本期恒为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`attribute_id`),
  UNIQUE KEY `uk_party_attribute` (`profile_id`, `attribute_code`) COMMENT 'Unique index for party_attribute on profile_id, attribute_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Party 扩展属性值；uk_party_attribute(profile_id,attribute_code)；随资料草稿保存，批准后随资料只读；清空属性时删除该行；应用校验，不创建 CHECK/FK';

CREATE TABLE `t_onboarding_schema` (
  `schema_id` BIGINT NOT NULL COMMENT '采集 Schema 版本的内部唯一标识；申请创建时冻结。',
  `jurisdiction_code` VARCHAR(16) NOT NULL COMMENT 'Schema 适用的辖区代码。取值来源：t_jurisdiction；非固定枚举。',
  `party_type` VARCHAR(16) NOT NULL COMMENT 'Schema 适用的申请主主体类型。允许值：INDIVIDUAL、ORGANIZATION。',
  `role_type` VARCHAR(32) NOT NULL COMMENT 'Schema 适用的业务角色；本期只有 Client 入驻。允许值：CLIENT。',
  `organization_type_code` VARCHAR(64) NOT NULL COMMENT '专用组织类型代码；* 表示该辖区该主体类型的默认 Schema。取值来源：t_organization_type.type_code 或 *；非固定枚举。',
  `version_no` INT NOT NULL COMMENT '同一 Schema 键下从 1 递增的版本号。',
  `status` VARCHAR(16) NOT NULL COMMENT 'Schema 版本状态；新申请只使用 ACTIVE，在途申请继续引用已冻结版本。允许值：DRAFT、ACTIVE、RETIRED。',
  `active_slot` TINYINT(1) NULL COMMENT '同一 Schema 键唯一 ACTIVE 版本的稀疏槽位。允许值：1 或 NULL。取值规则：状态为 ACTIVE 时为 1，其他状态为 NULL。',
  `approved_by` BIGINT NULL COMMENT '发布该版本的 Operations Account；DML 预置版本为空。',
  `approved_time` DATETIME(6) NULL COMMENT '版本发布为 ACTIVE 的 UTC 时间；DRAFT 为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`schema_id`),
  UNIQUE KEY `uk_schema_active` (`jurisdiction_code`, `party_type`, `role_type`, `organization_type_code`, `active_slot`) COMMENT 'Unique index for onboarding_schema on jurisdiction_code, party_type, role_type, organization_type_code, active_slot',
  UNIQUE KEY `uk_schema_version` (`jurisdiction_code`, `party_type`, `role_type`, `organization_type_code`, `version_no`) COMMENT 'Unique index for onboarding_schema on jurisdiction_code, party_type, role_type, organization_type_code, version_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='入驻采集 Schema 版本；uk_schema_active、uk_schema_version；按 辖区 × 主体类型 × 角色 × 组织类型 解析，组织类型无专用版本时取 * 默认版本；发布新版本不影响已冻结 schema_id 的在途申请';

CREATE TABLE `t_onboarding_schema_field` (
  `schema_field_id` BIGINT NOT NULL COMMENT '采集 Schema 字段行的内部唯一标识。',
  `schema_id` BIGINT NOT NULL COMMENT '所属采集 Schema 版本标识。',
  `section` VARCHAR(32) NOT NULL COMMENT '前端分区。允许值：BASIC、BUSINESS、PEOPLE、MATERIALS、DECLARATIONS。',
  `display_group` VARCHAR(64) NOT NULL COMMENT '分区内分组代码，如 IDENTITY、REGISTRATION、ADDRESS。取值来源：Schema DML；非固定枚举。',
  `field_key` VARCHAR(128) NOT NULL COMMENT '字段键，等于草稿 values 的分区.字段路径（如 individual.birthPlace、attributes.AE_FREE_ZONE_CODE）或 identifier.<标识类型>、declaration.<声明类型>、address、people。取值来源：Schema DML；非固定枚举。',
  `widget_code` VARCHAR(32) NOT NULL COMMENT '前端控件代码，由前端控件注册表解释。允许值：TEXT、TEXTAREA、DATE、COUNTRY、SELECT、IDENTIFIER、ADDRESS、NAME、PERSON_LIST、DOCUMENT_SLOT、DECLARATION、URL、MONEY、EMAIL、PHONE、BOOLEAN。',
  `catalog_code` VARCHAR(64) NULL COMMENT 'SELECT 控件的取值目录代码；其他控件为空。取值来源：t_catalog_item.catalog_code；非固定枚举。',
  `requirement_level` VARCHAR(16) NOT NULL COMMENT '提交要求级别：REQUIRED 提交前必填，EVENTUALLY 批准前必填，OPTIONAL 可选，HIDDEN 不展示。允许值：REQUIRED、EVENTUALLY、OPTIONAL、HIDDEN。',
  `requirement_group` VARCHAR(64) NULL COMMENT '同组字段至少满足一个的组代码，如个人证件 NID 或 Iqama 或护照；为空表示单独计算。取值来源：Schema DML；非固定枚举。',
  `applies_to_subject` VARCHAR(16) NOT NULL COMMENT '字段适用的受审主体。允许值：PRIMARY、RELATED_PERSON、BOTH。',
  `validation_pattern` VARCHAR(255) NULL COMMENT '字段取值必须完整匹配的正则表达式；为空表示不限格式。',
  `min_length` INT NULL COMMENT '字段最少字符数；为空表示不限。',
  `max_length` INT NULL COMMENT '字段最多字符数；为空时取草稿存储上限。',
  `visible_when` VARCHAR(255) NULL COMMENT '简单可见条件 field_key=value；条件不满足时字段不展示、不校验；为空表示始终可见。',
  `label_key` VARCHAR(128) NOT NULL COMMENT '字段标题的界面文案键，命名空间 onboardingSchema。',
  `help_key` VARCHAR(128) NULL COMMENT '字段帮助说明的界面文案键；为空表示无帮助。',
  `display_order` INT NOT NULL COMMENT '同一分区内的展示顺序，数值越小越靠前。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`schema_field_id`),
  UNIQUE KEY `uk_schema_field` (`schema_id`, `field_key`, `applies_to_subject`) COMMENT 'Unique index for onboarding_schema_field on schema_id, field_key, applies_to_subject'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='采集 Schema 字段，同时是前端渲染模板与后端提交校验的唯一来源；uk_schema_field(schema_id,field_key,applies_to_subject)；发布后不可修改';

CREATE TABLE `t_application_declaration` (
  `declaration_id` BIGINT NOT NULL COMMENT '申请声明记录的内部唯一标识。',
  `client_application_id` BIGINT NOT NULL COMMENT '声明所属的 Client 入驻申请标识。',
  `declaration_type` VARCHAR(64) NOT NULL COMMENT '声明类型，必须是申请冻结 Schema 中的 declaration.<类型> 字段，如 INFORMATION_TRUE、PEP_DECLARATION。取值来源：采集 Schema；非固定枚举。',
  `declared_value` VARCHAR(8) NOT NULL COMMENT '声明取值；PEP 自述可为 FALSE，其他声明须为 TRUE 才算满足。允许值：TRUE、FALSE。',
  `declared_by_account_id` BIGINT NOT NULL COMMENT '作出声明的 Platform CUSTOMER Account 外部稳定标识，不建立跨库物理外键。',
  `declared_time` DATETIME(6) NOT NULL COMMENT '声明作出的 UTC 时间，精确到微秒。',
  `client_ip` VARCHAR(64) NULL COMMENT 'Gateway 传入的可信客户端 IP；未提供时为空。',
  `user_agent` VARCHAR(512) NULL COMMENT '作出声明时的浏览器 User-Agent，超长截断；未提供时为空。',
  `terms_version` VARCHAR(64) NULL COMMENT '声明所针对的条款或告知文本版本；为空表示声明不对应版本化文本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`declaration_id`),
  KEY `idx_app_declaration` (`client_application_id`, `declaration_type`) COMMENT 'Access index for application_declaration on client_application_id, declaration_type'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='申请声明证据；不可变、只追加；同一申请同一类型以最新一行为准；记录账号、时间、IP 与 User-Agent 作为电子声明证据';

CREATE TABLE `t_partner_onboarding_code` (
  `code_id` BIGINT NOT NULL COMMENT '入驻码的内部唯一标识。',
  `code` VARCHAR(32) NOT NULL COMMENT '不可猜测的随机入驻码，格式 XXXX-XXXX，由 PayFac 线下发给商户。',
  `local_partner_id` BIGINT NOT NULL COMMENT '签发入驻码并承担尽调责任的 Local Partner（须具备 OE_PROVIDER 角色）标识。',
  `status` VARCHAR(16) NOT NULL COMMENT '入驻码状态；只有 ACTIVE 且在有效期、未用尽时可用。允许值：ACTIVE、REVOKED、EXPIRED。',
  `effective_from` DATETIME(6) NOT NULL COMMENT '入驻码生效的 UTC 时间，包含该时刻。',
  `effective_to` DATETIME(6) NULL COMMENT '入驻码失效的 UTC 时间，不包含该时刻；为空表示不过期。',
  `max_uses` INT NULL COMMENT '允许创建申请的最大次数；为空表示不限次数。',
  `use_count` INT NOT NULL COMMENT '已被成功用于创建申请的次数；达到 max_uses 时状态转为 EXPIRED。',
  `note` VARCHAR(255) NULL COMMENT '签发说明，仅运营可见。',
  `issued_by` BIGINT NOT NULL COMMENT '签发入驻码的 Operations Account。',
  `revoked_by` BIGINT NULL COMMENT '作废入驻码的 Operations Account；未作废为空。',
  `revoked_time` DATETIME(6) NULL COMMENT '作废的 UTC 时间；未作废为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`code_id`),
  UNIQUE KEY `uk_partner_onboarding_code` (`code`) COMMENT 'Unique index for partner_onboarding_code on code',
  KEY `idx_onboarding_code_partner` (`local_partner_id`, `status`) COMMENT 'Access index for partner_onboarding_code on local_partner_id, status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='PayFac 入驻码；uk_partner_onboarding_code(code)；Client 创建申请时填码解析 responsible_partner_id；码无效只返回 ONBOARDING_CODE_INVALID，不泄露 Partner 信息；ACTIVE 可转 REVOKED 或 EXPIRED，终态不可恢复';

CREATE TABLE `t_client_entity_affiliation` (
  `affiliation_id` BIGINT NOT NULL COMMENT 'Client 获准在准确交易范围内使用 Operating Entity 的关系标识。',
  `client_id` BIGINT NOT NULL COMMENT '平台 Client 角色的内部唯一标识。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'CCT唯一不可变OE身份，CHAR(5)，创建时经营国家二位代码加001–999，如AE001；编号不参与路由、不复用。',
  `priority_no` INT NOT NULL COMMENT '同一业务解析范围内候选记录的确定性顺序，数值越小越优先。',
  `status` VARCHAR(64) NOT NULL COMMENT '当前业务记录在其所属聚合生命周期中的状态代码。允许值：ACTIVE、ENDED。',
  `active_slot` TINYINT(1) NULL COMMENT '以 1 或 NULL 实现条件稀疏唯一约束的技术槽位，具体占槽含义由表级规则定义。允许值：1 或 NULL。取值规则：状态为 ACTIVE 时为 1，其他状态为 NULL。',
  `effective_from` DATETIME(6) NOT NULL COMMENT '业务记录生效区间的 UTC 微秒精度起点，包含该时刻。',
  `effective_to` DATETIME(6) NULL COMMENT '业务记录生效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`affiliation_id`),
  UNIQUE KEY `uk_aff_entity_active` (`client_id`, `entity_no`, `active_slot`) COMMENT 'Unique index for client_entity_affiliation on client_id, entity_no, active_slot',
  UNIQUE KEY `uk_aff_priority_active` (`client_id`, `priority_no`, `active_slot`) COMMENT 'Unique index for client_entity_affiliation on client_id, priority_no, active_slot',
  UNIQUE KEY `uk_aff_entity_time` (`client_id`, `entity_no`, `effective_from`) COMMENT 'Unique index for client_entity_affiliation on client_id, entity_no, effective_from',
  KEY `idx_aff_priority` (`client_id`, `status`, `priority_no`, `effective_from`, `effective_to`) COMMENT 'Access index for client_entity_affiliation on client_id, status, priority_no, effective_from, effective_to',
  KEY `idx_client_entity_affil_a789336f` (`entity_no`) COMMENT 'Access index for client_entity_affiliation on entity_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Client 使用 Operating Entity 的业务资格关系；uk_aff_entity_active(client_id,entity_no,active_slot)、uk_aff_priority_active(client_id,priority_no,active_slot)、uk_aff_entity_time(client_id,entity_no,effective_from)；ACTIVE时slot=1，其余NULL；Client全局正整数优先级，升序逐个完整验证；授权覆盖OE当前和未来已批准且匹配Client分类的范围';

CREATE TABLE `t_client_partner_relation` (
  `relation_id` BIGINT NOT NULL COMMENT 'Client 与 Local Partner 业务关系的内部唯一标识。',
  `client_id` BIGINT NOT NULL COMMENT '平台 Client 角色的内部唯一标识。',
  `local_partner_id` BIGINT NOT NULL COMMENT '与 Client 建立业务关系的 Local Partner 标识。',
  `relation_type` VARCHAR(64) NOT NULL COMMENT 'Client 与 Local Partner 的关系类型。允许值：REFERRAL。',
  `status` VARCHAR(64) NOT NULL COMMENT '当前业务记录在其所属聚合生命周期中的状态代码。允许值：ACTIVE、ENDED。',
  `effective_from` DATETIME(6) NOT NULL COMMENT '业务记录生效区间的 UTC 微秒精度起点，包含该时刻。',
  `effective_to` DATETIME(6) NULL COMMENT '业务记录生效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`relation_id`),
  UNIQUE KEY `uk_client_partner_rel` (`client_id`, `local_partner_id`, `relation_type`, `effective_from`) COMMENT 'Unique index for client_partner_relation on client_id, local_partner_id, relation_type, effective_from',
  KEY `idx_client_partner_rel_partner` (`local_partner_id`) COMMENT 'Access index for client_partner_relation on local_partner_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Client 与 Local Partner 的推荐等业务关系；uk_client_partner_rel(client_id,local_partner_id,relation_type,effective_from)；relation_type=REFERRAL，状态 ACTIVE/ENDED；本期只建表，没有写入方与读取方，与合同 referral_partner_id 的关系由后续需求确定；应用校验，不创建 CHECK/FK';

CREATE TABLE `t_entity_source_relation` (
  `source_relation_id` BIGINT NOT NULL COMMENT 'Operating Entity 来源关系的稳定内部标识。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'CCT唯一不可变OE身份，CHAR(5)，创建时经营国家二位代码加001–999，如AE001；编号不参与路由、不复用。',
  `source_type` VARCHAR(64) NOT NULL COMMENT '该 Operating Entity 来源关系对应的来源主体类型。允许值：CLIENT、PARTNER。',
  `source_client_id` BIGINT NULL COMMENT '提交并经平台审核关联 Operating Entity 的 Organization Client 标识。',
  `source_partner_id` BIGINT NULL COMMENT '提供 Operating Entity 的 Local Partner 标识。',
  `client_source_basis` VARCHAR(64) NULL COMMENT 'Organization Client 提供 Operating Entity 时的来源认定依据；source_type=CLIENT 时为 SAME_PARTY 或 VERIFIED_ASSOCIATION，其他来源为空。允许值：SAME_PARTY、VERIFIED_ASSOCIATION。',
  `status` VARCHAR(64) NOT NULL COMMENT '当前业务记录在其所属聚合生命周期中的状态代码。允许值：ACTIVE、ENDED。',
  `active_source_slot` TINYINT(1) NULL COMMENT '用于保证同一 Operating Entity 同时只有一个有效来源关系。允许值：1 或 NULL。取值规则：状态为 ACTIVE 时为 1，其他状态为 NULL。',
  `effective_from` DATETIME(6) NOT NULL COMMENT '业务记录生效区间的 UTC 微秒精度起点，包含该时刻。',
  `effective_to` DATETIME(6) NULL COMMENT '业务记录生效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`source_relation_id`),
  UNIQUE KEY `uk_source_effective` (`entity_no`, `effective_from`) COMMENT 'Unique index for entity_source_relation on entity_no, effective_from',
  UNIQUE KEY `uk_source_active` (`entity_no`, `active_source_slot`) COMMENT 'Unique index for entity_source_relation on entity_no, active_source_slot',
  KEY `idx_entity_source_relat_6036b10b` (`entity_no`, `status`, `effective_from`, `effective_to`) COMMENT 'Access index for entity_source_relation on entity_no, status, effective_from, effective_to',
  KEY `idx_entity_source_relat_925d4955` (`source_client_id`) COMMENT 'Access index for entity_source_relation on source_client_id',
  KEY `idx_entity_source_relat_48981e34` (`source_partner_id`) COMMENT 'Access index for entity_source_relation on source_partner_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='OE 的唯一来源认定关系；uk_source_effective(entity_no,effective_from)、uk_source_active(entity_no,active_source_slot)；source_type IN (CLIENT,PARTNER)，Owner XOR；状态仅 ACTIVE/ENDED，仅 ACTIVE 的 active_source_slot=1；Client 来源依据仅为 SAME_PARTY/VERIFIED_ASSOCIATION；CHECK ((status=''ACTIVE'' AND active_source_slot=1) OR (status=''ENDED'' AND active_source_slot IS NULL))；CHECK (effective_to IS NULL OR effective_from < effective_to)；Owner XOR：CLIENT 要求 source_client_id IS NOT NULL AND source_partner_id IS NULL；PARTNER 要求 source_client_id IS NULL AND source_partner_id IS NOT NULL。；CLIENT 来源依据：SAME_PARTY 要求 Client Party=Operating Entity Party；VERIFIED_ASSOCIATION 要求两个 Party 不同，并由已批准申请、冻结材料和匹配的 Risk Qualification Decision 支撑；非 CLIENT 要求 client_source_basis IS NULL。；source_type、source_client_id、source_partner_id、client_source_basis 创建后不可修改；来源失效时只允许推进为 ENDED 并填写 effective_to。';

CREATE TABLE `t_consumer_legal_document` (
  `legal_document_id` BIGINT NOT NULL COMMENT '一份独立、可发布且可审批的消费者法律文档内部唯一标识。',
  `document_code` VARCHAR(64) NOT NULL COMMENT '同一消费者法律文档类型下唯一且稳定的业务编号。',
  `document_type` VARCHAR(64) NOT NULL COMMENT '文档用途或法律类别的受控业务代码。允许值：CONSUMER_TERMS、REFUND_POLICY、PRIVACY_NOTICE。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'CCT唯一不可变OE身份，CHAR(5)，创建时经营国家二位代码加001–999，如AE001；编号不参与路由、不复用。',
  `country_code` CHAR(2) NOT NULL COMMENT '消费者法律文档适用的精确 ISO 3166-1 alpha-2 国家代码。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `locale` VARCHAR(35) NOT NULL COMMENT '内容解析和展示使用的受控 IETF BCP 47 Locale 代码。取值来源：IETF BCP 47 Locale Catalog；非固定枚举。',
  `content_file_id` BIGINT NOT NULL COMMENT 'Platform Support 消费者法律文档正文的逻辑引用，与 content_hash 成组冻结，不建立跨库物理外键。',
  `content_hash` CHAR(64) NOT NULL COMMENT '输入为 Platform Support 返回的消费者法律文档 SHA-256，与 content_file_id 成组冻结。',
  `status` VARCHAR(64) NOT NULL COMMENT '该独立消费者法律文档当前所处的发布生命周期状态。允许值：DRAFT、APPROVED、ACTIVE、EXPIRED、WITHDRAWN。',
  `effective_from` DATETIME(6) NOT NULL COMMENT '业务记录生效区间的 UTC 微秒精度起点，包含该时刻。',
  `effective_to` DATETIME(6) NULL COMMENT '业务记录生效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。',
  `approved_time` DATETIME(6) NULL COMMENT '法律事实通过批准的 UTC 微秒精度时刻。',
  `published_time` DATETIME(6) NULL COMMENT '法律事实正式向消费者发布的 UTC 微秒精度时刻。',
  `create_by` BIGINT NOT NULL COMMENT '创建不可变审计快照的正式 Human 或 System Actor 标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`legal_document_id`),
  UNIQUE KEY `uk_legal_doc_code` (`document_code`) COMMENT 'Unique index for consumer_legal_document on document_code',
  UNIQUE KEY `uk_legal_doc_scope_time` (`document_type`, `entity_no`, `country_code`, `locale`, `effective_from`) COMMENT 'Unique index for consumer_legal_document on document_type, entity_no, country_code, locale, effective_from',
  KEY `idx_legal_doc_resolve` (`entity_no`, `country_code`, `locale`, `document_type`, `effective_from`, `effective_to`) COMMENT 'Access index for consumer_legal_document on entity_no, country_code, locale, document_type, effective_from, effective_to'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='消费者法律文档；uk_legal_doc_code(document_code)、uk_legal_doc_scope_time(document_type,entity_no,country_code,locale,effective_from)；类型 CONSUMER_TERMS/REFUND_POLICY/PRIVACY_NOTICE，状态 DRAFT/APPROVED/ACTIVE/EXPIRED/WITHDRAWN；一行是一份独立可发布文档，不使用 Client Override、Root/Version、Active Slot 或业务 Hash；状态时间矩阵：DRAFT 的 approved_time/published_time 均为空；APPROVED 仅 approved_time 非空；ACTIVE、EXPIRED 两者均非空；WITHDRAWN 的 approved_time 非空，未发布撤回时 published_time 可为空。；存在 published_time 时必须满足 approved_time <= published_time AND effective_from <= published_time；已经发布的 EXPIRED/WITHDRAWN 必须填写 effective_to。；按 Operating Entity 稳定顺序锁定同一 OE/Country/Locale/Document Type 的全部历史行并检查半开有效区间冲突；进入 ACTIVE 时防御性复查。；历史 As-Of 候选必须满足 published_time <= t AND effective_from <= t AND (effective_to IS NULL OR t < effective_to)，不得按当前 status=ACTIVE 排除 EXPIRED/WITHDRAWN 行；每个 Scope 缺失或并列均 Fail Closed。；替换在同一事务收窄旧行 effective_to、写 BCR/事件并冻结新行；不得按当前状态过滤历史行，不得回溯收窄到 operation_context_time 之前；APPROVED 后文件、Scope 和 effective_from 不得修改，内容变化创建新 legal_document_id。';

CREATE TABLE `t_seller_disclosure` (
  `disclosure_id` BIGINT NOT NULL COMMENT '一套独立获准公开卖方披露的内部唯一标识。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'CCT唯一不可变OE身份，CHAR(5)，创建时经营国家二位代码加001–999，如AE001；编号不参与路由、不复用。',
  `country_code` CHAR(2) NOT NULL COMMENT 'Seller Disclosure 适用的精确 ISO 3166-1 alpha-2 国家代码。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `locale` VARCHAR(35) NOT NULL COMMENT '内容解析和展示使用的受控 IETF BCP 47 Locale 代码。取值来源：IETF BCP 47 Locale Catalog；非固定枚举。',
  `public_legal_name` VARCHAR(255) NOT NULL COMMENT '批准向 Consumer 展示的 Operating Entity 法定名称。',
  `public_registration_number` VARCHAR(128) NOT NULL COMMENT '批准向 Consumer 展示的 Operating Entity 注册编号。',
  `public_tax_registration_number` VARCHAR(64) NULL COMMENT '按国家披露规则可选展示的卖方税务登记编号。',
  `public_address_text` VARCHAR(512) NOT NULL COMMENT '按司法辖区规则批准向 Consumer 披露的卖方地址文本。',
  `public_support_email` VARCHAR(254) NOT NULL COMMENT '向消费者公开展示的卖方支持邮箱。',
  `public_support_phone` VARCHAR(32) NULL COMMENT '向消费者公开展示的卖方支持电话。',
  `status` VARCHAR(64) NOT NULL COMMENT '该独立法律卖方披露当前所处的发布生命周期状态。允许值：DRAFT、APPROVED、ACTIVE、EXPIRED、WITHDRAWN。',
  `effective_from` DATETIME(6) NOT NULL COMMENT '业务记录生效区间的 UTC 微秒精度起点，包含该时刻。',
  `effective_to` DATETIME(6) NULL COMMENT '业务记录生效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。',
  `approved_time` DATETIME(6) NULL COMMENT '法律事实通过批准的 UTC 微秒精度时刻。',
  `published_time` DATETIME(6) NULL COMMENT '法律事实正式向消费者发布的 UTC 微秒精度时刻。',
  `create_by` BIGINT NOT NULL COMMENT '创建不可变审计快照的正式 Human 或 System Actor 标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`disclosure_id`),
  UNIQUE KEY `uk_seller_disclosure_time` (`entity_no`, `country_code`, `locale`, `effective_from`) COMMENT 'Unique index for seller_disclosure on entity_no, country_code, locale, effective_from',
  KEY `idx_seller_disclosure_resolve` (`entity_no`, `country_code`, `locale`, `effective_from`, `effective_to`) COMMENT 'Access index for seller_disclosure on entity_no, country_code, locale, effective_from, effective_to'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='法律卖方披露；uk_seller_disclosure_time(entity_no,country_code,locale,effective_from)；状态 DRAFT/APPROVED/ACTIVE/EXPIRED/WITHDRAWN；public_tax_registration_number 与 public_support_phone 可空，其余公开展示字段必填；一行是一套获准公开的卖方信息，不使用文件、Client Override、Root/Version、Active Slot 或业务 Hash；状态时间矩阵：DRAFT 的 approved_time/published_time 均为空；APPROVED 仅 approved_time 非空；ACTIVE、EXPIRED 两者均非空；WITHDRAWN 的 approved_time 非空，未发布撤回时 published_time 可为空。；存在 published_time 时必须满足 approved_time <= published_time AND effective_from <= published_time；已经发布的 EXPIRED/WITHDRAWN 必须填写 effective_to。；按 Operating Entity 稳定顺序锁定同一 OE/Country/Locale 的全部历史行并检查半开有效区间冲突；进入 ACTIVE 时防御性复查。；历史 As-Of 候选必须满足 published_time <= t AND effective_from <= t AND (effective_to IS NULL OR t < effective_to)，不得按当前 status=ACTIVE 排除 EXPIRED/WITHDRAWN 行；缺失或并列均 Fail Closed。；替换在同一事务收窄旧行 effective_to、写 BCR/事件并冻结新行；不得回溯收窄到 operation_context_time 之前；APPROVED 后公开字段、Scope 和 effective_from 不得修改，内容变化创建新 disclosure_id。';

CREATE TABLE `t_business_change_record` (
  `history_id` BIGINT NOT NULL COMMENT '一条受审计业务行稳定历史身份的内部唯一标识。',
  `record_type` VARCHAR(64) NOT NULL COMMENT '仅标识已批准接入 BCR 的审计对象类型；不在白名单内的业务表和技术表不得登记。允许值：PARTY、CLIENT、LOCAL_PARTNER、OPERATING_ENTITY、PARTY_PROFILE、CLIENT_BUSINESS_PROFILE、COMMERCIAL_TERM_VERSION、CLIENT_APPLICATION、PARTNER_APPLICATION、OPERATING_ENTITY_APPLICATION、PARTY_RELATION、CLIENT_ENTITY_AFFILIATION、ENTITY_SOURCE_RELATION、AGREEMENT、CONSUMER_LEGAL_DOCUMENT、SELLER_DISCLOSURE、COMMERCIAL_TERM_PLAN、PAYMENT_METHOD_PROFILE。',
  `record_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '被审计业务行在其 record_type 下的稳定业务标识。',
  `aggregate_root_type` VARCHAR(64) NOT NULL COMMENT '该审计对象所属且负责并发串行化的业务聚合根类型；只能使用已批准的 Root 类型。允许值：PARTY、CLIENT、LOCAL_PARTNER、OPERATING_ENTITY、CLIENT_APPLICATION、PARTNER_APPLICATION、OPERATING_ENTITY_APPLICATION、PARTY_RELATION、CLIENT_ENTITY_AFFILIATION、ENTITY_SOURCE_RELATION、AGREEMENT、CONSUMER_LEGAL_DOCUMENT、SELLER_DISCLOSURE、COMMERCIAL_TERM_PLAN、PAYMENT_METHOD_PROFILE。',
  `aggregate_root_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '该变更记录所属业务聚合根的稳定业务标识。',
  `parent_history_id` BIGINT NULL COMMENT '同一聚合内部直接父业务行对应的变更主记录标识。',
  `current_version` BIGINT NOT NULL COMMENT 'Root 记录中是唯一 expected_change_version CAS 令牌；受审计 Child 中只指向最近一次快照采用的 Root 版本，不执行独立 Child CAS。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`history_id`),
  UNIQUE KEY `uk_bcr_record` (`record_type`, `record_id`) COMMENT 'Unique index for business_change_record on record_type, record_id',
  KEY `idx_business_change_rec_6c86a4f4` (`aggregate_root_type`, `aggregate_root_id`, `history_id`) COMMENT 'Access index for business_change_record on aggregate_root_type, aggregate_root_id, history_id',
  KEY `idx_business_change_rec_6d3f5c71` (`parent_history_id`) COMMENT 'Access index for business_change_record on parent_history_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='白名单业务对象的稳定审计身份与当前修订；uk_bcr_record(record_type,record_id)；record_type 与 aggregate_root_type 使用第 7 节闭合白名单；Root 的 Record Tuple 等于 Aggregate Root Tuple 且 Parent 为空，归属不可修改；仅 Root current_version 是 CCT 聚合 CAS 令牌；current_version > 0；Record Type 白名单由 record_type 字段闭合枚举定义；白名单外不创建 BCR。；非审计 Child 使用冻结的结构化事实或所属版本保留历史，完整目录见 BCR_EXCLUDED_BUSINESS_TABLES。；CREATE 原子协议：创建受审计聚合时，必须在同一事务插入结构化业务数据、Root business_change_record(current_version=1)、Root Version 1 CREATE Snapshot、白名单内且本次确需审计的 Child Record/Snapshot 与本地 compensation_event。；Root UPDATE CAS：外部命令必须携带 expected_change_version，固定 next_version=expected_change_version+1；Root business_change_record 必须以 current_version=expected_change_version 条件 CAS 到 next_version，影响行数必须为 1。；受审计 Child 版本指针：只有 Record Type 白名单内且本次发生需留档变化的 Child 才追加最终 Snapshot，并把 current_version 推进到 Root next_version；Root CAS 已串行化同聚合写入，Child current_version 不是独立 CAS Token。；非审计 Child：白名单外 Child 不创建 BCR、不接收 expected_change_version，也不写重复 Snapshot；其 Draft 改动由所属 Root CAS 保护，冻结后历史由结构化 Version 或追加式领域证据保留。；DELETE 顺序：删除白名单内受审计业务行前必须先追加包含稳定身份与删除前安全字段的 DELETE Snapshot，再删除结构化行；Record 永久保留。；System Transition 协议：定时激活、过期、撤销和系统回调必须作为锁内读取 expected_change_version 的 System Command，执行同一 Root CAS/History/Event 协议，不得绕过审计版本。；Multi-Root 原子协议：受影响 Root 必须按 (aggregate_root_type,aggregate_root_id) 固定顺序锁定和 CAS；每个 Root 独立计算 next_version 并写 Root/白名单内 Changed Child Snapshot，所有 Snapshot 使用同一 _meta.triggerId 关联。；整体回滚：任一 Root CAS、业务校验、结构化业务更新、应写 History Snapshot 或 compensation_event 登记失败，整个本地事务必须回滚。；Event 原子与版本：权威业务变化、business_change_record/business_change_version History 与每个 compensation_event 必须在同一本地事务提交；每个 Event.source_version 必须等于它所代表 Root 的 next_version，不得借用其他 Root 版本。';

CREATE TABLE `t_business_change_version` (
  `change_version_id` BIGINT NOT NULL COMMENT '不可变业务行审计快照的内部唯一标识。',
  `history_id` BIGINT NOT NULL COMMENT '该不可变快照所属 business_change_record 的内部标识。',
  `version_no` BIGINT NOT NULL COMMENT '本次变化提交后的聚合根版本坐标，不是连续的行版本号，同一业务行允许跳号。',
  `operation_type` VARCHAR(64) NOT NULL COMMENT '本次不可变审计快照对应的业务行操作类型。允许值：CREATE、UPDATE、DELETE。',
  `snapshot_json` JSON NOT NULL COMMENT '按 Record Type 白名单生成的不可变 JSON 审计视图，含 Schema、Trigger、Reason 与完整性哈希。',
  `snapshot_format` VARCHAR(32) NOT NULL COMMENT '新快照使用ENTITY_NO，历史快照保留LEGACY_NUMERIC；判别不改变原JSON或Hash。允许值：ENTITY_NO、LEGACY_NUMERIC。',
  `create_by` BIGINT NOT NULL COMMENT '创建不可变审计快照的外部 Actor 稳定标识，是逻辑引用且不建立跨库物理外键。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`change_version_id`),
  UNIQUE KEY `uk_bcv_history_ver` (`history_id`, `version_no`) COMMENT 'Unique index for business_change_version on history_id, version_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='不可变行级审计快照；uk_bcv_history_ver(history_id,version_no)；CREATE/UPDATE/DELETE，只 INSERT，Snapshot 白名单与 Canonical Integrity Hash，最大 64 KiB，无 update_time；snapshot_format明确ENTITY_NO/LEGACY_NUMERIC，旧Snapshot及Hash不得重写；version_no > 0；operation_type IN (CREATE,UPDATE,DELETE)；64 KiB';

CREATE TABLE `t_compensation_event` (
  `id` BIGINT NOT NULL COMMENT '补偿执行记录的内部唯一标识。',
  `event_key` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '领域事件或本地恢复动作的全局稳定幂等键。',
  `event_type` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '需要发布或本地恢复执行的领域事件类型代码。取值来源：CCT 唯一 Compensation Handler Catalog；非固定枚举。',
  `sub_event_type` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '同一 Handler 下的业务子类型；无业务子类型时使用短横线占位。取值来源：所属 Handler 的子类型目录，无业务子类型时固定短横线占位；非固定枚举。',
  `payload_json` VARCHAR(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL COMMENT '可空或最大 4 KiB UTF-8 的不可变 JSON Object；禁止 Vault Token、敏感值及可从本库恢复的上下文。',
  `execute_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'INIT' COMMENT '补偿任务当前的可恢复执行状态。允许值：INIT、PROCESSING、ERROR、FAILED。',
  `execute_count` INT NOT NULL DEFAULT 0 COMMENT '补偿任务已经尝试执行的累计次数。',
  `next_execute_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '补偿任务下一次允许被 Claim 的 UTC 微秒精度时刻。',
  `lock_owner` VARCHAR(64) NULL COMMENT '当前 Claim 补偿任务的执行实例标识。',
  `lock_until` DATETIME(6) NULL COMMENT '当前补偿任务执行租约到期的 UTC 微秒精度时刻。',
  `last_error_code` VARCHAR(64) NULL COMMENT '补偿执行最近一次失败的稳定错误分类代码。取值来源：CCT Compensation Error Catalog；非固定枚举。',
  `last_error_summary` VARCHAR(1000) NULL COMMENT '补偿执行最近一次失败的脱敏错误摘要，不保存敏感原文。',
  `lock_version` BIGINT NOT NULL DEFAULT 1 COMMENT '补偿任务 Claim 与完成回写使用的独立技术 CAS 版本，不是业务 Revision 或来源版本。',
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comp_event_identity` (`event_key`, `event_type`, `sub_event_type`) COMMENT 'Unique index for compensation_event on event_key, event_type, sub_event_type',
  KEY `idx_comp_event_ready` (`execute_status`, `next_execute_at`, `id`) COMMENT 'Access index for compensation_event on execute_status, next_execute_at, id',
  KEY `idx_comp_event_lease` (`execute_status`, `lock_until`, `id`) COMMENT 'Access index for compensation_event on execute_status, lock_until, id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='本服务 Handler 的可恢复任务；uk_comp_event_identity(event_key,event_type,sub_event_type)；状态 INIT/PROCESSING/ERROR/FAILED；可选最小 JSON Object 最大 4 KiB UTF-8，禁止敏感值、Vault Token 和已落库上下文；Claim/Lease 使用 lock_version，安全默认值与执行协议服从现行补偿指南；Payload 最小化：payload_json 可空或为最大 4 KiB UTF-8 的不可变 JSON Object；禁止 Vault Token、敏感值及可从本库恢复的上下文。；Lease 状态矩阵：PROCESSING 必须同时具有未过期的 lock_owner 与 lock_until；INIT、ERROR、FAILED 不得保留活动 Lease。；Lease Holder 写回：只有持有当前活动 Lease 的执行者可以完成、删除或回写该次尝试；旧执行者不得覆盖新 Lease Holder 的结果。；登记原子性：权威业务事实、业务 History 与 compensation_event 必须在同一本地事务提交；事务内任一点失败全部回滚。；成功删除：Handler 只有取得确定成功结果后才允许删除 compensation_event；MQ Handler 必须等待 Broker Confirm。；失败保留：达到最大重试次数后必须进入 FAILED、保留记录并告警；人工重开必须记录 Actor、Reason 与 Ticket 后回到 ERROR 等待重新 Claim。；Lease 接管：PROCESSING 的 Lease 超时后允许重新 Claim；Claim 与完成/回写都必须使用 lock_version CAS。';

CREATE TABLE `t_client_stored_card_setting` (
  `client_id` BIGINT NOT NULL COMMENT '平台 Client 的内部唯一标识；每个 Client 至多一行，没有记录视为未开通。',
  `stored_card_enabled` TINYINT(1) NOT NULL COMMENT '是否开通存卡与已存卡支付；1 开通，0 关闭。关闭后已存卡保留但不展示、不可使用。',
  `version` BIGINT NOT NULL COMMENT '可变业务行并发更新使用的单调乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`client_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='按 Client 开通存卡能力（CCT 暂无按 Client 配置落点时的 Commerce 本地配置）；首期由初始化 DML 或内部调用维护，运营页面后续提供。';

CREATE TABLE `t_consumed_event` (
  `event_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件信封 eventId（默认等于 eventKey），按其去重。',
  `event_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '已消费领域事件的类型（PascalCase，登记于 paytech-messaging 事件目录）。',
  `aggregate_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件所属聚合的大写类型代码。',
  `aggregate_id` VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件所属聚合的标识，同时是 RabbitMQ 分区路由键。',
  `aggregate_version` BIGINT NOT NULL COMMENT '本条已消费领域事件携带的聚合版本。',
  `consumed_at` DATETIME(6) NOT NULL COMMENT '消费事务提交的 UTC 微秒精度时间。',
  PRIMARY KEY (`event_id`),
  KEY `idx_consumed_event_aggregate` (`aggregate_type`, `aggregate_id`, `aggregate_version`) COMMENT 'Access index for consumed_event on aggregate_type, aggregate_id, aggregate_version'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='已消费领域事件：paytech-messaging 消费端按事件 ID 去重的标准表（design/08 §4），列、类型、主键与索引与 paytech-messaging 的 messaging-consumed-event.sql 一致；事件与版本合法性由消费拦截器保证。';

CREATE TABLE `t_consumed_aggregate_version` (
  `aggregate_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件所属聚合的大写类型代码。',
  `aggregate_id` VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件所属聚合的标识，同时是 RabbitMQ 分区路由键。',
  `last_version` BIGINT NOT NULL COMMENT '本服务已处理的该聚合最大领域事件版本。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`aggregate_type`, `aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='已消费聚合版本：paytech-messaging 消费端按聚合记录已处理的最大版本，用于判定乱序、重放与缺口（design/08 §4）；列、类型与主键与 messaging-consumed-event.sql 一致。';
