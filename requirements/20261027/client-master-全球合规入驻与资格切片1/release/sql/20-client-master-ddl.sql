-- 2026-10-27 全球合规入驻与资格切片 1（client-master，结构变更）。
-- 需求：requirements/20261027/client-master-全球合规入驻与资格切片1/technique.md 第 2.3 节；字段级设计 design-discuss/client-onboarding/design.md 第 10.1 节
--       （叠加修订：不做阿语，删除 local_* / name_script；规则开关走 t_rule_setting；地址组件 t_address_format_component；
--       通用目录 t_catalog_item；关系类型目录 t_relation_type；重提为新案件串联；不含修订申请与 t_materiality_rule）。
-- 内容：新建 13 张表（t_jurisdiction、t_rule_setting、t_organization_type、t_identifier_type、t_relation_type、
--       t_address_format_component、t_catalog_item、t_attribute_definition、t_party_attribute、t_onboarding_schema、
--       t_onboarding_schema_field、t_application_declaration、t_partner_onboarding_code）；
--       改表 t_party_profile、t_individual_profile、t_organization_profile、t_party_identity_key、t_party_profile_identifier、
--       t_party_address、t_party_relation、t_client、t_client_application、t_application_document。
--       个人证件号不再明文入库：identifier_value / normalized_identifier_value 删除，改为 Vault Token（个人）或公开登记号（机构）。
-- 执行顺序（已有数据的环境，发布决策 P-8 / P-11）：备份 → 本文件 → 2026-10-27-onboarding-global-cleanup.sql（只清在途
--          PENDING Client）→ 2026-10-27-onboarding-country-pack.sql（AE/SA/EG 国家包增量）→
--          2026-10-27-onboarding-global-legacy-backfill.sql（遗留 ACTIVE Client 脱敏与申请新列回填）→ 脱敏核验 →
--          Vault LEGAL_IDENTIFIER_V1 DML → 新版 client-master 代码。空库只需 client-master-schema.sql + client-master-initial-data.sql。
-- 执行前备份整个 mor_client_master；DDL 隐式提交，失败时先核对已完成的语句，禁止整段重跑（CREATE TABLE 已存在会失败，
-- 可跳过已完成语句）。本文件保留全部存量行：第 3 步把存量标识原样做列迁移（UPDATE），个人证件号的脱敏由 legacy-backfill 完成，
-- 两者之间不得放行新版代码；第 7 步新增的申请列对存量行取值：application_type 由列默认值写入 ONBOARDING 后去掉默认值，
-- schema_id 可空（遗留申请没有 Schema），jurisdiction_code 暂为空串、submission_round 暂为 0，由 legacy-backfill 回填。
-- 2026-10-27-onboarding-global-reset.sql（清空全部 Client）已由 P-8 取代并删除。
--
-- 回退（须与 client-master 代码回退同时进行；新代码不再读取被删除的明文列）：
--   DROP TABLE t_partner_onboarding_code, t_application_declaration, t_onboarding_schema_field, t_onboarding_schema,
--     t_party_attribute, t_attribute_definition, t_catalog_item, t_address_format_component, t_relation_type,
--     t_identifier_type, t_organization_type, t_rule_setting, t_jurisdiction;
--   改表回退：按 git 历史中的 client-master-schema.sql 逐列恢复（ADD 旧列 identifier_value / normalized_identifier_value /
--     valid_from / valid_to 并从 public_identifier_value / identifier_key_value / issue_date / expiry_date 回填，
--     DROP 本文件新增列，恢复 uk_party_profile_party 与 uk_party_identity_norm）；Client 入驻数据只能从备份恢复。
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- 1. 新表（定义与 client-master-schema.sql 一致）。
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

-- 2. Party 资料：当前槽位、辖区与取代时间；唯一键由每 Party 一行改为每 Party 一份当前资料。
ALTER TABLE `t_party_profile`
  ADD COLUMN `jurisdiction_code` VARCHAR(16) NULL COMMENT '资料采集所依据的辖区包代码，入驻创建时写入；Operating Entity 与 Local Partner 的存量资料为空。取值来源：t_jurisdiction；非固定枚举。',
  ADD COLUMN `current_slot` TINYINT(1) NULL COMMENT '同一 Party 当前资料的稀疏唯一槽位。允许值：1 或 NULL。取值规则：入驻草稿资料与已批准的当前资料为 1；被后续修订取代后为 NULL 并记录 superseded_time（修订申请本期未启用）。',
  ADD COLUMN `superseded_time` DATETIME(6) NULL COMMENT '资料被修订申请取代、不再是当前资料的 UTC 时间；当前资料为空。',
  MODIFY COLUMN `content_hash` CHAR(64) NULL COMMENT '批准资料内容摘要（规范化资料 JSON 的 SHA-256 小写十六进制），供 Checkout 法律资料消费者核对；未批准为空，批准时写入且不可变。',
  MODIFY COLUMN `draft_application_id` BIGINT NULL COMMENT '当前可编辑 Client 入驻申请；未批准资料只能由该申请编辑；批准时同一事务清空。',
  COMMENT='法律主体资料；uk_party_profile_current(party_id,current_slot)；同一 Party 只有一份当前资料（current_slot=1），被取代的历史资料 current_slot 为 NULL 并永久保留；草稿由 draft_application_id 指定申请编辑；批准时同一事务写 approved_time、content_hash 并清空 draft_application_id，之后永久只读；应用校验，不创建 CHECK/FK';
UPDATE `t_party_profile` SET `current_slot` = 1;
ALTER TABLE `t_party_profile`
  DROP INDEX `uk_party_profile_party`,
  ADD UNIQUE KEY `uk_party_profile_current` (`party_id`, `current_slot`) COMMENT 'Unique index for party_profile on party_id, current_slot',
  ADD KEY `idx_party_profile_party` (`party_id`) COMMENT 'Access index for party_profile on party_id';

-- 3. 法律标识：明文号码改为 Vault Token（个人）或公开登记号（机构）；OE / Local Partner 存量为机构公开登记号，原样迁移。
ALTER TABLE `t_party_identity_key`
  ADD COLUMN `identifier_key_value` VARCHAR(128) NULL COMMENT '唯一性比较值：VAULT_REQUIRED 类型为规范化原值经 LEGAL_IDENTIFIER_V1 Protect 得到的 STABLE Token（同值同 Token），其他类型为规范化后的公开登记号；本库不保存个人证件原值。';
UPDATE `t_party_identity_key` SET `identifier_key_value` = `normalized_identifier_value`;
ALTER TABLE `t_party_identity_key`
  DROP INDEX `uk_party_identity_norm`,
  DROP COLUMN `normalized_identifier_value`,
  MODIFY COLUMN `identifier_key_value` VARCHAR(128) NOT NULL COMMENT '唯一性比较值：VAULT_REQUIRED 类型为规范化原值经 LEGAL_IDENTIFIER_V1 Protect 得到的 STABLE Token（同值同 Token），其他类型为规范化后的公开登记号；本库不保存个人证件原值。',
  MODIFY COLUMN `identifier_type` VARCHAR(64) NOT NULL COMMENT '注册号、证件号等法律标识的受控类型代码。取值来源：t_identifier_type.identifier_type_code；非固定枚举。',
  MODIFY COLUMN `issuing_country` CHAR(2) NULL COMMENT '国家签发标识使用的 ISO 3166-1 alpha-2 国家代码；作用域不是签发国时为空。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  MODIFY COLUMN `issuer_scope_key` VARCHAR(64) NOT NULL COMMENT '号码唯一性作用域键：GLOBAL、签发国代码或登记机关代码。',
  ADD UNIQUE KEY `uk_party_identity_token` (`identifier_type`, `issuer_scope_key`, `identifier_key_value`) COMMENT 'Unique index for party_identity_key on identifier_type, issuer_scope_key, identifier_key_value',
  COMMENT='法律标识注册主档；uk_party_identity_token(identifier_type,issuer_scope_key,identifier_key_value)；相同类型、作用域和号码只能绑定同一 Party；个人证件以 STABLE Token 比较，不保存原值；应用校验，不创建 CHECK/FK';

ALTER TABLE `t_party_profile_identifier`
  ADD COLUMN `identifier_vault_token` VARCHAR(128) NULL COMMENT 'VAULT_REQUIRED 类型号码规范化后经 LEGAL_IDENTIFIER_V1 Protect 得到的 STABLE Token；非 Vault 类型或草稿未填为空。',
  ADD COLUMN `public_identifier_value` VARCHAR(128) NULL COMMENT '非 Vault 类型的规范化公开登记号（机构执照号、商业登记号等）；VAULT_REQUIRED 类型恒为空。',
  ADD COLUMN `identifier_masked_value` VARCHAR(32) NULL COMMENT '展示用脱敏值，只保留末 4 位；草稿未填为空。',
  ADD COLUMN `identifier_check_result` VARCHAR(64) NULL COMMENT '保存号码时按标识类型执行的格式与校验位检查结论；为空表示通过或尚未填写。允许值：IDENTIFIER_FORMAT_INVALID、IDENTIFIER_CHECKSUM_FAILED、NULL。',
  ADD COLUMN `issue_date` DATE NULL COMMENT '证件签发日期；可空。',
  ADD COLUMN `expiry_date` DATE NULL COMMENT '证件到期日（含当日有效）；EXPIRY_REQUIRED 类型提交时必填且不得早于提交日。';
UPDATE `t_party_profile_identifier` SET `public_identifier_value` = `identifier_value`,
  `identifier_masked_value` = IF(`identifier_value` IS NULL, NULL, CONCAT('****', RIGHT(`identifier_value`, 4))),
  `issue_date` = DATE(`valid_from`), `expiry_date` = DATE(`valid_to`);
ALTER TABLE `t_party_profile_identifier`
  DROP COLUMN `identifier_value`,
  DROP COLUMN `valid_from`,
  DROP COLUMN `valid_to`,
  MODIFY COLUMN `identity_key_id` BIGINT NULL COMMENT '跨 Profile 稳定的 Party 法律标识身份键；提交时绑定，草稿可空。',
  MODIFY COLUMN `identifier_type` VARCHAR(64) NULL COMMENT '当前资料填报的法律标识类型；草稿可空。取值来源：t_identifier_type.identifier_type_code；非固定枚举。',
  MODIFY COLUMN `issuing_country` CHAR(2) NULL COMMENT '当前资料填报的签发国家代码；按标识类型的作用域方式适用。取值来源：ISO 3166-1 alpha-2，草稿未填可空；非固定枚举。',
  MODIFY COLUMN `issuer_scope_key` VARCHAR(64) NULL COMMENT '提交时计算并冻结的唯一性作用域键；草稿为空。',
  COMMENT='主体资料法律标识；uk_profile_identity(profile_id,identity_key_id)；个人证件只保存 Vault Token 与末 4 位脱敏，机构公开登记号保存规范化明文；仅提交时绑定 identity_key_id；批准资料中的绑定不可改写；应用校验，不创建 CHECK/FK';

-- 4. 个人与机构资料：名称列 128（S-18），新增核心列。
ALTER TABLE `t_individual_profile`
  MODIFY COLUMN `given_name` VARCHAR(128) NULL COMMENT '个人法定姓名中的名字（拉丁字母，与护照一致）。',
  MODIFY COLUMN `middle_name` VARCHAR(128) NULL COMMENT '个人法定姓名中的可选中间名，可容纳父名与祖父名。',
  MODIFY COLUMN `family_name` VARCHAR(128) NULL COMMENT '个人姓氏，即法定姓名中的姓氏部分。',
  ADD COLUMN `birth_place` VARCHAR(128) NULL COMMENT '出生地（城市或地区）文本，按辖区 Schema 决定是否必填。',
  ADD COLUMN `birth_country` CHAR(2) NULL COMMENT '出生国家的 ISO 3166-1 alpha-2 代码。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  ADD COLUMN `gender` VARCHAR(16) NULL COMMENT '筛查使用的性别代码。取值来源：通用目录 GENDER；非固定枚举。',
  ADD COLUMN `source_of_funds_code` VARCHAR(64) NULL COMMENT '资金来源代码，作为风险评级因子。取值来源：通用目录 SOURCE_OF_FUNDS；非固定枚举。',
  ADD COLUMN `pep_self_declared` TINYINT(1) NULL COMMENT '本人是否自述为政治公众人物。允许值：0、1、NULL。取值规则：NULL 表示尚未填写。',
  COMMENT='个人资料；允许部分草稿保存；提交时按申请冻结的采集 Schema 完整校验；批准后只读；职业、税务居民地等辖区特有事实走 t_party_attribute；应用校验，不创建 CHECK/FK';

ALTER TABLE `t_organization_profile`
  MODIFY COLUMN `legal_name` VARCHAR(128) NULL COMMENT '机构在注册司法辖区登记的完整法定名称（拉丁字母）。',
  MODIFY COLUMN `trade_name` VARCHAR(128) NULL COMMENT '机构对外经营使用的可选商号或交易名称。',
  MODIFY COLUMN `organization_type` VARCHAR(64) NULL COMMENT '按司法辖区和法律形态分类的受控机构类型代码。取值来源：t_organization_type.type_code；非固定枚举。',
  ADD COLUMN `registration_authority_code` VARCHAR(64) NULL COMMENT '颁发执照或商业登记的登记机关代码，如 AE_DED_DUBAI、SA_MC、EG_GAFI；AUTHORITY 作用域标识以它为唯一性作用域。取值来源：通用目录 REGISTRATION_AUTHORITY；非固定枚举。',
  ADD COLUMN `license_expiry_date` DATE NULL COMMENT '营业执照或商业登记到期日，提交时不得早于当日。',
  ADD COLUMN `listed_company` TINYINT(1) NULL COMMENT '是否为受监管市场上市公司。允许值：0、1、NULL。取值规则：NULL 表示尚未填写。',
  COMMENT='机构资料；允许部分草稿保存；提交时按申请冻结的采集 Schema 完整校验；批准后只读；经济部门、自贸区代码、税号等辖区特有事实走 t_party_attribute；应用校验，不创建 CHECK/FK';

-- 5. 地址组件。
ALTER TABLE `t_party_address`
  MODIFY COLUMN `address_type` VARCHAR(64) NOT NULL COMMENT '地址用途或法律属性的受控分类代码。允许值：RESIDENTIAL、REGISTERED。',
  MODIFY COLUMN `city` VARCHAR(128) NULL COMMENT '主体地址所在城市或同级行政区域名称或目录代码。',
  MODIFY COLUMN `state_or_province` VARCHAR(128) NULL COMMENT '主体地址所在的州、省、酋长国或同级行政区域名称或目录代码。',
  MODIFY COLUMN `postal_code` VARCHAR(32) NULL COMMENT '主体地址的邮政编码；是否必填由地址格式与 POSTAL_CODE_REQUIRED 规则决定。',
  ADD COLUMN `address_format_code` VARCHAR(32) NULL COMMENT '提交校验所用的地址格式代码；地址所在国家等于辖区国家时为辖区格式，否则为 GENERIC。取值来源：t_address_format_component；非固定枚举。',
  ADD COLUMN `building_number` VARCHAR(32) NULL COMMENT '建筑号（如沙特国家地址 4 位建筑号）。',
  ADD COLUMN `additional_number` VARCHAR(32) NULL COMMENT '附加号（如沙特国家地址 4 位附加号）。',
  ADD COLUMN `district` VARCHAR(128) NULL COMMENT '区或街区名称。',
  ADD COLUMN `po_box` VARCHAR(32) NULL COMMENT '邮政信箱号（如阿联酋 PO Box）。',
  COMMENT='主体地址；uk_profile_address_type(profile_id,address_type)；同一资料同一地址类型最多一条；组件允许部分草稿；提交时按 t_address_format_component 完整校验；批准后只读；应用校验，不创建 CHECK/FK';

-- 6. 关系：类型改引用目录，新增 UBO 依据、职务、代持与控制说明。
ALTER TABLE `t_party_relation`
  MODIFY COLUMN `relation_type` VARCHAR(64) NOT NULL COMMENT '所有权、控制、任职或 UBO 等法律关系类型代码。取值来源：t_relation_type.relation_type_code；非固定枚举。',
  MODIFY COLUMN `ownership_percentage` DECIMAL(18,10) NULL COMMENT '关系类型要求持股比例时填写的百分比，取值范围为 0 至 100；其他关系类型为空。',
  ADD COLUMN `ubo_basis` VARCHAR(32) NULL COMMENT '最终受益人的认定依据；仅 ubo_flag 关系类型填写。允许值：OWNERSHIP、CONTROL、SENIOR_MANAGING_OFFICIAL。',
  ADD COLUMN `job_title` VARCHAR(128) NULL COMMENT '相关人员在机构中的职务名称。',
  ADD COLUMN `nominee_flag` TINYINT(1) NULL COMMENT '是否为名义（代持）股东或董事。允许值：0、1、NULL。取值规则：NULL 表示未填写。',
  ADD COLUMN `control_nature` VARCHAR(255) NULL COMMENT '以控制而非持股认定时的控制方式说明。',
  COMMENT='主体关系；uk_party_relation_identity(organization_party_id,related_party_id,relation_type)；同一机构、相关主体和关系类型只保留一条稳定关系；关系类型来自 t_relation_type 目录；Client 草稿由 client_application_id 限定修改归属，批准后关系集合锁定；真实关系生效/结束日期保留；应用校验，不创建 CHECK/FK';

-- 7. Client 与申请：辖区、责任方、Schema 冻结、入驻码、提交轮次与退回期限（存量行保留，见文件头）。
ALTER TABLE `t_client`
  ADD COLUMN `jurisdiction_code` VARCHAR(16) NULL COMMENT 'Client 入驻所在辖区代码，随最近一次入驻申请写入。取值来源：t_jurisdiction；非固定枚举。',
  ADD COLUMN `responsible_partner_id` BIGINT NULL COMMENT '对该 Client 尽调负责的 PayFac Local Partner；由入驻码解析，平台自营为空。',
  ADD KEY `idx_client_jurisdiction` (`jurisdiction_code`, `status`) COMMENT 'Access index for client on jurisdiction_code, status',
  COMMENT='Client 角色根；uk_client_no(client_no)、uk_client_party(party_id)；状态 PENDING/ACTIVE/SUSPENDED/TERMINATED；资格 REVOKED 时系统自动 ACTIVE → SUSPENDED';

ALTER TABLE `t_client_application`
  ADD COLUMN `application_type` VARCHAR(32) NOT NULL DEFAULT 'ONBOARDING' COMMENT '申请类型；本期只写入 ONBOARDING，PROFILE_AMENDMENT 由后续修订申请启用。允许值：ONBOARDING、PROFILE_AMENDMENT。',
  ADD COLUMN `jurisdiction_code` VARCHAR(16) NOT NULL COMMENT '申请所适用的辖区包代码，创建时确定。取值来源：t_jurisdiction；非固定枚举。',
  ADD COLUMN `organization_type_code` VARCHAR(64) NULL COMMENT '组织申请当前选择的组织类型代码；个人申请为空。取值来源：t_organization_type.type_code；非固定枚举。',
  ADD COLUMN `schema_id` BIGINT NULL COMMENT '申请冻结的采集 Schema 版本；草稿阶段更换组织类型时按新类型重新解析，提交后不可改；2026-10-27 之前的遗留申请没有 Schema，为空。',
  ADD COLUMN `onboarding_code_id` BIGINT NULL COMMENT '创建申请时使用的 PayFac 入驻码标识；无码为空。',
  ADD COLUMN `responsible_partner_id` BIGINT NULL COMMENT '由入驻码解析并冻结的责任 PayFac Local Partner；平台自营（无码）为空。',
  ADD COLUMN `submission_round` INT NOT NULL COMMENT '正式提交轮次：创建为 0，每次提交加一；退回后重提为下一轮。',
  ADD COLUMN `previous_case_id` BIGINT NULL COMMENT '退回重提时上一轮的 Risk & Compliance 尽调 Case，供新案件串联复用；首轮为空。',
  ADD COLUMN `review_due_time` DATETIME(6) NULL COMMENT '退回时审核给出的补件截止 UTC 时间；非退回状态为空或保留上次值。',
  COMMENT='客户申请；uk_client_application_no(application_no)、uk_client_application_active(client_id,active_slot)、uk_client_app_account_active(requested_by_account_id,active_slot)、uk_client_app_submission(current_submission_id)；DRAFT、RETURNED 可编辑并占用 active_slot；SUBMITTED、UNDER_REVIEW 只读并继续占用；APPROVED、REJECTED、WITHDRAWN 终态释放名额；DRAFT/RETURNED 可由 Client 撤回为 WITHDRAWN，审核退回超期由结果事件转为 WITHDRAWN；current_submission_id 隔离每次提交结果，旧审核任务结果不得覆盖当前任务；辖区、Schema、入驻码与责任方创建时冻结；应用校验，不创建 CHECK/FK。';

ALTER TABLE `t_client_application` ALTER COLUMN `application_type` DROP DEFAULT;

-- 8. 申请材料：签发与到期日（程序时效校验）。
ALTER TABLE `t_application_document`
  ADD COLUMN `issue_date` DATE NULL COMMENT '材料文件的签发或出具日期，用于程序 max_age_days 时效校验；未提供为空。' AFTER `update_time`,
  ADD COLUMN `expiry_date` DATE NULL COMMENT '材料文件的到期日（含当日有效）；未提供或不到期为空。' AFTER `issue_date`;
