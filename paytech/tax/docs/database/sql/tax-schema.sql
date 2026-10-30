-- Code-MoR target schema generated from the approved PDManer model.
-- Apply manually to an EMPTY database only; existing environments require a reviewed migration.
-- Execution status: agent-docs/CURRENT_IMPLEMENTATION.md. Verify Java compatibility before rollout.
-- Source: paytech/tax/docs/database/tax.pdma.json
SET NAMES utf8mb4;

CREATE TABLE `t_tax_category` (
  `id` BIGINT NOT NULL COMMENT '税类 Snowflake 主键；初始化数据使用 1–999 的固定值。',
  `category_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定税类代码，例如 SAAS_AUTOMATED；订单行 tax_category_code 的取值来源。取值来源：本表；非固定枚举。',
  `name` VARCHAR(128) NOT NULL COMMENT '税类名称（业务定义，不含税率）。',
  `description` VARCHAR(512) NULL COMMENT '税类边界：哪些产品属于本类、哪些不属于；可空。',
  `status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '税类状态；停用后历史计算仍可读取，新计算不再接受。允许值：ACTIVE、RETIRED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '记录最近一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tax_category` (`category_code`) COMMENT '保证业务唯一性：category_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Tax 产品税类目录：订单行按税类匹配地区包规则；不放税率，不复用 MCC 或平台产品目录。未知或停用的税类使计算 BLOCKED(UNKNOWN_TAX_CATEGORY)。';

CREATE TABLE `t_tax_market` (
  `id` BIGINT NOT NULL COMMENT '准入记录 Snowflake 主键；初始化数据使用 1–999 的固定值。',
  `subject_party_id` BIGINT NOT NULL COMMENT '平台登记的纳税主体 Party ID（CCT Party，稳定不复用）；计算请求 taxSubjectPartyId 按它判断是否由平台计税。',
  `jurisdiction_code` CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '准入地区 ISO 3166-1 alpha-2 国家代码。取值来源：Platform t_ref_country；非固定枚举。',
  `tax_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '税种代码。取值来源：地区包税种目录；非固定枚举（本期 VAT）。',
  `scheme_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '适用制度代码，例如 STANDARD、NON_RESIDENT、SVRR。取值来源：地区包制度目录；非固定枚举。',
  `assessment_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '同一责任评估的业务编号；修订沿用编号、递增 revision。',
  `revision` INT NOT NULL COMMENT '评估修订号，从 1 开始；同一评估取有效期内最大修订。',
  `valid_from` DATE NOT NULL COMMENT '批准判断的适用开始日期（含），按税点日期比较。',
  `valid_to` DATE NULL COMMENT '批准判断的适用结束日期（不含）；NULL 表示没有预定结束。',
  `obligation` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '评估结论：REGISTRATION_REQUIRED 按地区包计税（必须指向有效注册）；NO_COLLECTION_REQUIRED 获准不收，组件 NOT_COLLECTED、税 0；CUSTOMER_ACCOUNTED 与 REVIEW_REQUIRED 本期按未启用阻断。允许值：REGISTRATION_REQUIRED、NO_COLLECTION_REQUIRED、CUSTOMER_ACCOUNTED、REVIEW_REQUIRED。',
  `collection_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '是否允许按本准入收税；只有 ENABLED 可以 READY。允许值：BLOCKED、ENABLED、SUSPENDED。',
  `managed_by` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '谁操作该主体在该地区的准入、申报与缴款；本期只读展示。允许值：SUBJECT_HOLDER、PLATFORM_OPERATIONS。',
  `registration_id` BIGINT NULL COMMENT '本库 t_tax_registration.id；REGISTRATION_REQUIRED 时必须指向有效注册，其余义务可空。',
  `policy_id` BIGINT NULL COMMENT '本库 t_tax_policy.id：本准入计税依据的地区包版本；REGISTRATION_REQUIRED 时必填。',
  `approved_by` VARCHAR(64) NULL COMMENT '批准该法律判断的操作者标识；初始化数据为 SEED。',
  `approved_at` DATETIME(6) NULL COMMENT '批准的 UTC 微秒时间；未批准为空，未批准的准入不参与计算。',
  `review_due_at` DATE NULL COMMENT '到期复核日期；可空，本期只展示。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '记录最近一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tax_market` (`assessment_no`, `revision`) COMMENT '保证业务唯一性：assessment_no、revision',
  KEY `idx_tax_market_subject` (`subject_party_id`, `jurisdiction_code`, `tax_type`) COMMENT '支持访问路径：subject_party_id、jurisdiction_code、tax_type'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='一个纳税主体进入一个地区的责任评估与准入批准；按 assessment_no + revision 版本化，批准后内容不改。主体在本表没有任何记录 → 计算 NOT_MANAGED；有记录但供应地无有效准入或 collection_status 不是 ENABLED → BLOCKED(MARKET_NOT_ENABLED)。';

CREATE TABLE `t_tax_registration` (
  `id` BIGINT NOT NULL COMMENT '注册 Snowflake 主键；初始化数据使用 1–999 的固定值。',
  `subject_party_id` BIGINT NOT NULL COMMENT '注册归属的纳税主体 Party ID（CCT Party，稳定不复用）。',
  `jurisdiction_code` CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '注册地区 ISO 3166-1 alpha-2 国家代码。取值来源：Platform t_ref_country；非固定枚举。',
  `authority_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '税局代码，例如 AE_FTA、SA_ZATCA、EG_ETA；不是银行收款账户。取值来源：地区包税局目录；非固定枚举。',
  `tax_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '税种代码。取值来源：地区包税种目录；非固定枚举（本期 VAT）。',
  `scheme_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '注册制度代码，例如 STANDARD、NON_RESIDENT、SVRR。取值来源：地区包制度目录；非固定枚举。',
  `registration_no_ref` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '受控注册号引用（来源注册业务身份的一部分）；不复制个人税号原值。',
  `public_registration_no` VARCHAR(64) NOT NULL COMMENT '票据上展示的主体公开注册号（TRN / VAT No.），属于主体公开信息、非个人资料。',
  `registered_name` VARCHAR(255) NOT NULL COMMENT '注册登记的主体法定名称，简化税票的卖方名称取它；属于主体公开信息、非个人资料。',
  `effective_from` DATE NOT NULL COMMENT '注册实际生效日期（含）。',
  `effective_to` DATE NULL COMMENT '注册失效日期（不含）；NULL 表示没有预定结束。',
  `status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '注册状态；只有 ACTIVE 且在有效期内的注册可用于计税与开票。允许值：PENDING、ACTIVE、SUSPENDED、DEREGISTERED。',
  `reporting_currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '法定申报币种 ISO 4217；与交易币种不同时底稿需要法定汇率。',
  `legal_timezone` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '法定日期解释所用 IANA 时区，例如 Asia/Riyadh；税点日期与期间按它取本地日期。',
  `filing_frequency` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '法定申报周期；期间按它以日历月或季度自动生成。允许值：MONTHLY、QUARTERLY。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '记录最近一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tax_registration` (`subject_party_id`, `authority_code`, `registration_no_ref`, `tax_type`, `scheme_code`) COMMENT '保证业务唯一性：subject_party_id、authority_code、registration_no_ref、tax_type、scheme_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='纳税主体的一份法定税务注册与申报制度；有自己的有效期。决定申报币种、法定日期时区与申报周期，期间按它自动生成。';

CREATE TABLE `t_tax_policy` (
  `id` BIGINT NOT NULL COMMENT '地区包 Snowflake 主键；初始化数据使用 1–999 的固定值。',
  `package_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区包代码，例如 DEMO_SA_SAAS；同代码按 revision 版本化。',
  `revision` INT NOT NULL COMMENT '地区包版本号，从 1 开始。',
  `jurisdiction_code` CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规则适用地区 ISO 3166-1 alpha-2 国家代码；必须等于引用它的准入地区。',
  `tax_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '税种代码。取值来源：地区包税种目录；非固定枚举（本期 VAT）。',
  `scheme_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '适用制度代码。取值来源：地区包制度目录；非固定枚举。',
  `effective_from` DATE NOT NULL COMMENT '法定适用开始日期（含），按税点日期比较。',
  `effective_to` DATE NULL COMMENT '法定适用结束日期（不含）；NULL 表示没有预定结束。',
  `schema_version` INT NOT NULL COMMENT 'policy_json 的结构版本；本期只支持 1。',
  `status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区包状态；只有 APPROVED 参与计算，RETIRED 不影响历史解释。允许值：DRAFT、APPROVED、RETIRED。',
  `policy_json` JSON NOT NULL COMMENT '固定 Schema（版本 1）的规则：schemaVersion、applicability、placeOfSupply、taxPoint、treatments（EQ / IN 合取条件、互斥）、unmatchedBehavior、calculation、documents、reporting；未知区块或字段使计算 BLOCKED(POLICY_INVALID)。',
  `source_refs_json` JSON NOT NULL COMMENT '官方依据列表 JSON 数组（标题与地址）；不是任意附件袋。',
  `approved_by` VARCHAR(64) NULL COMMENT '复核批准者标识；初始化数据为 SEED。',
  `approved_at` DATETIME(6) NULL COMMENT '批准的 UTC 微秒时间；DRAFT 为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '记录最近一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tax_policy` (`package_code`, `revision`) COMMENT '保证业务唯一性：package_code、revision',
  KEY `idx_tax_policy_scope` (`jurisdiction_code`, `tax_type`, `scheme_code`, `status`) COMMENT '支持访问路径：jurisdiction_code、tax_type、scheme_code、status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='一起生效和审查的地区规则包版本：policy_json 为固定 Schema 的条件、税率、税点、取整、单据与申报规则；APPROVED 后内容不可修改，解释历史计算所依据的规则。';

CREATE TABLE `t_tax_calculation` (
  `id` BIGINT NOT NULL COMMENT '计算 Snowflake 主键；对外 calculationId。',
  `source_system` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '计算请求来源系统，例如 acquiring。取值来源：Code-MoR 系统目录；非固定枚举。',
  `source_object_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源对象类型。允许值：ORDER。',
  `source_object_id` BIGINT NOT NULL COMMENT '来源对象 ID（acquiring 订单主键，稳定不复用）。',
  `source_object_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源对象公开编号（acquiring 订单号），供运营按订单号查询。',
  `source_revision` INT NOT NULL COMMENT '来源对象的报价准备版本，建单为 1、每次重算递增；最大者为当前报价。',
  `tax_scope_key` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '同一源对象内的税务范围键。允许值：SALE。',
  `calculation_stage` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'PROVISIONAL 按 Client 声明国家暂估、不可支付；FINAL 按消费者账单国家计算。允许值：PROVISIONAL、FINAL。',
  `supply_kind` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '供应类型。允许值：SALE。',
  `business_mode` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '商业模式代码（commercial BusinessMode），只冻结为证据，不直接决定税负。取值来源：commercial BusinessMode；非固定枚举。',
  `seller_party_id` BIGINT NULL COMMENT '卖方法律主体 Party ID（CCT Party）；可空。',
  `buyer_client_id` BIGINT NOT NULL COMMENT '买方所属 Client ID（CCT Client，稳定不复用）。',
  `tax_subject_party_id` BIGINT NULL COMMENT '税务责任方 Party ID（商业关系快照 TAX_RESPONSIBLE）；NOT_MANAGED 时仍冻结以便追溯；调用方无法确定责任方（CLIENT 来源 OE 没有商业关系快照）时为空，结果恒为 NOT_MANAGED。',
  `acquiring_entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '收单 Operating Entity 编号（CCT entity_no，不复用）。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '全部金额的 ISO 4217 币种。',
  `price_mode` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '行价格口径：EXCLUSIVE 未税价，INCLUSIVE 含税价。允许值：EXCLUSIVE、INCLUSIVE。',
  `tax_point_date` DATE NOT NULL COMMENT '调用方给出的候选税点日期，用于选择有效准入、地区包与税率。',
  `tax_point_basis` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '税点依据，取自地区包 taxPoint.basis；未匹配地区包时为 PAYMENT。允许值：PAYMENT。',
  `status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '计算结果：READY 已算出；BLOCKED 平台纳税主体但规则、准入或证据缺失，不可支付；NOT_MANAGED 不是平台纳税主体，税额为自报值。允许值：READY、BLOCKED、NOT_MANAGED。',
  `tax_amount_source` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'COMPUTED 按规则计算；DECLARED 源 Owner 自报（NOT_MANAGED）。允许值：COMPUTED、DECLARED。',
  `block_reasons_json` JSON NULL COMMENT 'BLOCKED 的原因 JSON 数组 [{code, path}]，code 为 TaxBlockReason；不含个人原值；其他状态为空。',
  `net_amount` DECIMAL(24,8) NULL COMMENT '行净额合计；INCLUSIVE 且 BLOCKED 时无法反算为空。',
  `charged_tax_amount` DECIMAL(24,8) NULL COMMENT '收取税额合计；BLOCKED 为空，NOT_MANAGED 为自报值。',
  `gross_amount` DECIMAL(24,8) NULL COMMENT '应付总额 = 净额 + 税额 + 尾差；EXCLUSIVE 且 BLOCKED 时为空。',
  `rounding_amount` DECIMAL(24,8) NULL COMMENT '头级尾差；逐行取整、尾差记在行上，本期恒为 0；BLOCKED 为空。',
  `expires_at` DATETIME(6) NULL COMMENT '报价有效期 UTC：只有 FINAL 且 READY 非空（计算时间 + 地区包 quoteValidityHours，缺省 24 小时）。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tax_calculation_revision` (`source_system`, `source_object_type`, `source_object_id`, `source_revision`, `tax_scope_key`) COMMENT '保证业务唯一性：source_system、source_object_type、source_object_id、source_revision、tax_scope_key',
  KEY `idx_tax_calculation_no` (`source_object_no`) COMMENT '支持访问路径：source_object_no',
  KEY `idx_tax_calculation_subject` (`tax_subject_party_id`, `create_time`) COMMENT '支持访问路径：tax_subject_party_id、create_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='一次不可变的税务计算：源对象准备版本、主体、币种、证据阶段与结果汇总；创建后不修改。同一源对象 + 准备版本 + 税务范围唯一，重复请求内容相同返回原计算、不同拒绝。';

CREATE TABLE `t_tax_calculation_line` (
  `id` BIGINT NOT NULL COMMENT '计算行 Snowflake 主键。',
  `calculation_id` BIGINT NOT NULL COMMENT '本库 t_tax_calculation.id。',
  `source_line_ref` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '源商品行引用（acquiring clientItemId），计算内唯一，原样返回。',
  `line_no` INT NOT NULL COMMENT '计算内行号，从 1 开始。',
  `category_code` VARCHAR(64) NOT NULL COMMENT '行的税类代码（请求原值，Client 自由填写、最长 64 字符，未知时保留以便解释阻断）。取值来源：t_tax_category；非固定枚举。',
  `description` VARCHAR(255) NULL COMMENT '非敏感商品说明（调用方提供，供运营查询展示）；可空，不含消费者资料。',
  `quantity` DECIMAL(24,8) NOT NULL COMMENT '真实数量，正数。',
  `unit_price` DECIMAL(24,8) NOT NULL COMMENT '单价，按计算 price_mode 口径。',
  `discount_amount` DECIMAL(24,8) NOT NULL COMMENT '整行折扣（不乘数量），与价格同一口径。',
  `net_amount` DECIMAL(24,8) NULL COMMENT '行净额；INCLUSIVE 时为反算结果，无法计算（BLOCKED、NOT_MANAGED 的含税行）时为空。',
  `charged_tax_amount` DECIMAL(24,8) NULL COMMENT '行收取税额；BLOCKED 与 NOT_MANAGED 为空。',
  `gross_amount` DECIMAL(24,8) NULL COMMENT '行含税金额 = 净额 + 税额；EXCLUSIVE 的 BLOCKED 与 NOT_MANAGED 行为空。',
  `rounding_delta` DECIMAL(24,8) NOT NULL COMMENT '本行尾差 = 取整后税额 − 未取整税额（八位小数）；无组件时为 0。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tax_calculation_line` (`calculation_id`, `line_no`) COMMENT '保证业务唯一性：calculation_id、line_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='一次计算的商品行：数量、单价、折扣与逐行税额；逐行取整，尾差记在行上。追加写、不可变。';

CREATE TABLE `t_tax_calculation_component` (
  `id` BIGINT NOT NULL COMMENT '组件 Snowflake 主键。',
  `calculation_id` BIGINT NOT NULL COMMENT '本库 t_tax_calculation.id（冗余，便于整单读取）。',
  `line_id` BIGINT NOT NULL COMMENT '本库 t_tax_calculation_line.id。',
  `component_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '税组件代码，例如 VAT。取值来源：地区包规则 result.componentCode；非固定枚举。',
  `jurisdiction_code` CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '征税辖区 ISO 3166-1 alpha-2 国家代码。',
  `tax_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '税种代码（来自地区包）。取值来源：地区包税种目录；非固定枚举。',
  `treatment` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '税务处理。允许值：TAXABLE、ZERO_RATED、EXEMPT、OUT_OF_SCOPE、REVERSE_CHARGE、NOT_COLLECTED。',
  `reason_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '地区包给出的处理原因码，例如 SA_REMOTE_B2C_STANDARD。取值来源：地区包；非固定枚举。',
  `liable_role` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '法定承担角色。允许值：SELLER、BUYER。',
  `market_id` BIGINT NOT NULL COMMENT '本库 t_tax_market.id：本组件依据的准入记录。',
  `registration_id` BIGINT NULL COMMENT '本库 t_tax_registration.id；NOT_COLLECTED 等无须注册时可空。',
  `policy_id` BIGINT NULL COMMENT '本库 t_tax_policy.id；准入无地区包（NOT_COLLECTED）时可空。',
  `rule_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '命中的地区包规则代码；NOT_COLLECTED 为空。',
  `tax_base` DECIMAL(24,8) NOT NULL COMMENT '本组件税基（行净额）。',
  `rate` DECIMAL(18,10) NULL COMMENT '税率小数，例如 0.1500000000；无适用税率时为空。',
  `charged_tax_amount` DECIMAL(24,8) NOT NULL COMMENT '本组件收取的税额，按取整规则取整。',
  `calculation_method` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本次使用的算法。允许值：PERCENTAGE_EXCLUSIVE、PERCENTAGE_INCLUSIVE、NONE。',
  `rounding_method` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '法定取整方法。允许值：HALF_UP、HALF_EVEN、DOWN、UP。',
  `rounding_scale` INT NOT NULL COMMENT '取整小数位，不超过币种 minor unit。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tax_calculation_component` (`line_id`, `jurisdiction_code`, `component_code`) COMMENT '保证业务唯一性：line_id、jurisdiction_code、component_code',
  KEY `idx_tax_component_calc` (`calculation_id`) COMMENT '支持访问路径：calculation_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='一行上的一个税组件：辖区、处理类型、依据（准入、注册、地区包、规则）、税基、税率与税额；无适用税率时 rate 为空而不是 0。追加写、不可变。';

CREATE TABLE `t_tax_evidence` (
  `id` BIGINT NOT NULL COMMENT '证据 Snowflake 主键。',
  `calculation_id` BIGINT NOT NULL COMMENT '本库 t_tax_calculation.id。',
  `evidence_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '证据类型：DECLARED_COUNTRY 为 Client 声明的计税国家，BILLING_COUNTRY 为消费者账单国家。允许值：DECLARED_COUNTRY、BILLING_COUNTRY。',
  `source_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '证据提供方系统，例如 acquiring。取值来源：Code-MoR 系统目录；非固定枚举。',
  `country_code` CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ISO 3166-1 alpha-2 国家代码。',
  `subdivision_code` VARCHAR(6) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'ISO 3166-2 行政区代码；可空，本期不参与计算。',
  `validation_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '核验结果；本期国家证据均为 NOT_CHECKED。允许值：VALID、INVALID、UNKNOWN、NOT_CHECKED。',
  `result_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '核验结果码；本期为空。',
  `checked_at` DATETIME(6) NULL COMMENT '核验 UTC 微秒时间；NOT_CHECKED 为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  KEY `idx_tax_evidence_calc` (`calculation_id`) COMMENT '支持访问路径：calculation_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='一次计算冻结的地点证据：只存批准的派生维度（国家、行政区），不存完整地址或 IP。追加写、不可变。';

CREATE TABLE `t_tax_transaction` (
  `id` BIGINT NOT NULL COMMENT '交易 Snowflake 主键。',
  `calculation_id` BIGINT NOT NULL COMMENT '本库 t_tax_calculation.id；一次计算最多成为一个正式事实。',
  `source_system` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源系统，例如 acquiring。取值来源：Code-MoR 系统目录；非固定枚举。',
  `source_fact_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源事实类型。允许值：ORDER_PAYMENT_SUCCEEDED。',
  `source_fact_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源事实 ID：订单支付成功为 acquiring 订单 ID 的十进制字符串。',
  `tax_scope_key` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '税务范围键，等于计算的 tax_scope_key。允许值：SALE。',
  `origin` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'AUTO 由事件消费建立；MANUAL 人工登记（本期不用）。允许值：AUTO、MANUAL。',
  `transaction_kind` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '交易类型；本期只有 ORIGINAL。允许值：ORIGINAL、REFUND、ADJUSTMENT、MANUAL_INPUT。',
  `tax_subject_party_id` BIGINT NOT NULL COMMENT '纳税主体 Party ID，等于计算的 tax_subject_party_id。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '交易币种 ISO 4217，等于计算币种。',
  `occurred_at` DATETIME(6) NOT NULL COMMENT '经济事件发生 UTC 时间（支付成功时间）。',
  `tax_point_date` DATE NOT NULL COMMENT '法定税点日期：发生时间在注册法定时区的本地日期。',
  `tax_point_basis` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '税点依据。允许值：PAYMENT。',
  `registered_at` DATETIME(6) NOT NULL COMMENT '本系统登记 UTC 微秒时间。',
  `reporting_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '底稿完整性：READY 每个应税组件都有 OUTPUT 底稿；PENDING 底稿未齐。允许值：PENDING、READY。',
  `reporting_block_reason` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'PENDING 的原因；READY 为空。允许值：FX_RATE_MISSING。',
  `source_payload_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源事件 Payload 的 RFC 8785 规范 bytes 的 SHA-256 小写十六进制；同源身份再来时比对，不同进死信。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '记录最近一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tax_transaction_source` (`source_system`, `source_fact_type`, `source_fact_id`, `tax_scope_key`) COMMENT '保证业务唯一性：source_system、source_fact_type、source_fact_id、tax_scope_key',
  UNIQUE KEY `uk_tax_transaction_calc` (`calculation_id`) COMMENT '保证业务唯一性：calculation_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='把一次计算确认成正式税务事实：由消费 acquiring OrderPaymentSucceeded 建立，源事实身份唯一；同身份载荷 Hash 不同进死信。已确认交易不取消、不删除，只有底稿状态可更新。';

CREATE TABLE `t_tax_reporting_entry` (
  `id` BIGINT NOT NULL COMMENT '底稿 Snowflake 主键。',
  `transaction_id` BIGINT NOT NULL COMMENT '本库 t_tax_transaction.id。',
  `component_id` BIGINT NOT NULL COMMENT '本库 t_tax_calculation_component.id。',
  `registration_id` BIGINT NOT NULL COMMENT '本库 t_tax_registration.id。',
  `period_id` BIGINT NOT NULL COMMENT '本库 t_tax_period.id：按税点日期归属的期间。',
  `entry_role` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '申报角色；本期只有 OUTPUT。允许值：OUTPUT、INPUT、RCM_OUTPUT、RCM_INPUT、DISCLOSURE。',
  `reporting_rule_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '申报规则代码（地区包 reporting.reportingRuleCode）。取值来源：地区包；非固定枚举。',
  `reporting_policy_id` BIGINT NOT NULL COMMENT '本库 t_tax_policy.id：采用的申报规则包版本。',
  `source_currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '交易币种 ISO 4217。',
  `source_tax_base` DECIMAL(24,8) NOT NULL COMMENT '原币税基。',
  `source_tax_amount` DECIMAL(24,8) NOT NULL COMMENT '原币税额。',
  `reporting_currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '申报币种 ISO 4217（注册的申报币种）。',
  `fx_rate` DECIMAL(24,12) NOT NULL COMMENT '原币 → 申报币汇率；同币种为 1。',
  `fx_rate_date` DATE NOT NULL COMMENT '法定汇率日期；同币种为税点日期。',
  `fx_source_ref` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '汇率来源证据；同币种为 SAME_CURRENCY。',
  `reporting_tax_base` DECIMAL(24,8) NOT NULL COMMENT '按申报币种精度取整后的税基。',
  `reporting_tax_amount` DECIMAL(24,8) NOT NULL COMMENT '按申报币种精度取整后的税额。',
  `tax_point_date` DATE NOT NULL COMMENT '底稿税点日期，决定期间归属。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tax_reporting_entry` (`transaction_id`, `component_id`, `registration_id`, `entry_role`, `reporting_rule_code`) COMMENT '保证业务唯一性：transaction_id、component_id、registration_id、entry_role、reporting_rule_code',
  KEY `idx_tax_entry_period` (`period_id`) COMMENT '支持访问路径：period_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='正式交易到法定注册与期间的申报底稿：原币与申报币金额、汇率与来源；本期只有 OUTPUT。跨币种缺法定汇率时不建本行、交易标 PENDING。追加写、不可变。';

CREATE TABLE `t_tax_period` (
  `id` BIGINT NOT NULL COMMENT '期间 Snowflake 主键。',
  `registration_id` BIGINT NOT NULL COMMENT '本库 t_tax_registration.id。',
  `period_start` DATE NOT NULL COMMENT '期间开始日期（含）。',
  `period_end` DATE NOT NULL COMMENT '期间结束日期（含）。',
  `filing_due_date` DATE NOT NULL COMMENT '申报截止日期，按地区包 reporting.dueDateRule 计算。',
  `payment_due_date` DATE NOT NULL COMMENT '缴款截止日期；本期与申报截止日相同。',
  `reporting_currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '期间申报币种 ISO 4217，等于注册申报币种。',
  `status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '期间状态；本期自动生成为 OPEN、只读。允许值：OPEN、PREPARING、FILED、CLOSED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '记录最近一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tax_period` (`registration_id`, `period_start`, `period_end`) COMMENT '保证业务唯一性：registration_id、period_start、period_end'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='注册下稳定的法定申报期间；首次需要时按注册申报周期以法定时区的日历月或季度自动生成，截止日按地区包规则计算。不存可变余额。';

CREATE TABLE `t_tax_document` (
  `id` BIGINT NOT NULL COMMENT '单据 Snowflake 主键。',
  `transaction_id` BIGINT NOT NULL COMMENT '本库 t_tax_transaction.id。',
  `document_kind` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '单据类型；本期只有 RECEIPT。允许值：TAX_INVOICE、RECEIPT、CREDIT_NOTE、DEBIT_NOTE。',
  `document_scope_key` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '同一交易内的单据范围键。允许值：SALE。',
  `issuance_mode` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SYSTEM 本系统分号并生成文件；EXTERNAL 外部开具回填（本期不用）。允许值：SYSTEM、EXTERNAL。',
  `issuer_party_id` BIGINT NOT NULL COMMENT '开票主体 Party ID（纳税主体）。',
  `registration_id` BIGINT NOT NULL COMMENT '本库 t_tax_registration.id：票面税号所属注册。',
  `series_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '号码序列代码，年度编入代码，例如 SA-RCPT-2026。',
  `document_number` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '单据号码，按序列分配；同主体 + 注册 + 序列唯一。',
  `issued_at` DATETIME(6) NOT NULL COMMENT '分号（票面日期）的 UTC 微秒时间。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '单据币种 ISO 4217，等于交易币种。',
  `policy_id` BIGINT NOT NULL COMMENT '本库 t_tax_policy.id：法定内容与模板依据。',
  `template_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '单据模板代码（地区包 documents.receipt.templateCode）。',
  `template_revision` INT NOT NULL COMMENT '单据模板版本。',
  `content_schema_version` INT NOT NULL COMMENT 'document_content_json 的结构版本；本期为 1。',
  `document_content_json` JSON NOT NULL COMMENT '固定 Schema 的票面快照：卖方名称与公开税号、号码、日期、行（税类说明、数量、净额、税率、税额）、合计、币种、法定文字键；不含消费者任何资料。',
  `issue_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'RESERVED 已分号、文件未就绪；ISSUED 文件已存入 file 服务；VOID 号码占用但未发行。允许值：RESERVED、ISSUED、VOID。',
  `submission_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '税局报送状态；本期为 NOT_REQUIRED。允许值：NOT_REQUIRED、PENDING、ACCEPTED、REJECTED、UNKNOWN。',
  `submission_source` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'AUTO 系统报送，MANUAL 人工报送回填；NOT_REQUIRED 为空。允许值：AUTO、MANUAL。',
  `document_file_address` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Platform 文件地址 /files/v1/{fileId}；ISSUED 时非空。',
  `document_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '文件内容 SHA-256 小写十六进制；ISSUED 时非空。',
  `void_reason` VARCHAR(256) NULL COMMENT 'VOID 的原因；其他状态为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '记录最近一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tax_document` (`transaction_id`, `document_kind`, `document_scope_key`) COMMENT '保证业务唯一性：transaction_id、document_kind、document_scope_key',
  UNIQUE KEY `uk_tax_document_number` (`issuer_party_id`, `registration_id`, `series_code`, `document_number`) COMMENT '保证业务唯一性：issuer_party_id、registration_id、series_code、document_number'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='正式交易的法定单据：本期只有系统开具的 B2C 简化税票（RECEIPT），内容不含消费者个人资料。号码在 RESERVED 时占用、失败不回收；文件存 file 服务，只保存 /files/v1/{fileId} 地址与摘要。';

CREATE TABLE `t_tax_document_sequence` (
  `id` BIGINT NOT NULL COMMENT '序列 Snowflake 主键；初始化数据使用 1–999 的固定值。',
  `issuer_party_id` BIGINT NOT NULL COMMENT '开票主体 Party ID。',
  `registration_id` BIGINT NOT NULL COMMENT '本库 t_tax_registration.id；两份注册不共用序列。',
  `series_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '序列代码 = 地区包 documents.receipt.seriesPrefix + "-" + 年份，例如 SA-RCPT-2026。',
  `document_kind` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '序列所开单据类型。允许值：TAX_INVOICE、RECEIPT、CREDIT_NOTE、DEBIT_NOTE。',
  `next_number` BIGINT NOT NULL COMMENT '下一个可分配的序号，从 1 开始；分配后递增，失败不回收。',
  `number_format` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '号码格式：{series} 为序列代码，{number:0N} 为补零到 N 位的序号，例如 {series}-{number:06}。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '记录最近一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tax_document_sequence` (`issuer_party_id`, `registration_id`, `series_code`) COMMENT '保证业务唯一性：issuer_party_id、registration_id、series_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='开票主体在一份注册下的单据号码序列：锁定本行、在同一短事务中递增并创建 RESERVED 单据；年度编入 series_code，首次使用时自动建立。';

CREATE TABLE `t_consumed_event` (
  `event_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件信封 eventId（默认等于 eventKey），按其去重。',
  `event_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '已消费领域事件的类型（PascalCase，登记于 paytech-messaging 事件目录）。',
  `aggregate_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件所属聚合的大写类型代码。',
  `aggregate_id` VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件所属聚合的标识，同时是 RabbitMQ 分区路由键。',
  `aggregate_version` BIGINT NOT NULL COMMENT '本条已消费领域事件携带的聚合版本。',
  `consumed_at` DATETIME(6) NOT NULL COMMENT '消费事务提交的 UTC 微秒精度时间。',
  PRIMARY KEY (`event_id`),
  KEY `idx_consumed_event_aggregate` (`aggregate_type`, `aggregate_id`, `aggregate_version`) COMMENT '访问索引：aggregate_type、aggregate_id、aggregate_version'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='已消费领域事件：paytech-messaging 消费端按事件 ID 去重的标准表（design/08 §4），列、类型、主键与索引与 paytech-messaging 的 messaging-consumed-event.sql 一致；事件与版本合法性由消费拦截器保证。';

CREATE TABLE `t_consumed_aggregate_version` (
  `aggregate_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件所属聚合的大写类型代码。',
  `aggregate_id` VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件所属聚合的标识，同时是 RabbitMQ 分区路由键。',
  `last_version` BIGINT NOT NULL COMMENT '本服务已处理的该聚合最大领域事件版本。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`aggregate_type`, `aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='已消费聚合版本：paytech-messaging 消费端按聚合记录已处理的最大版本，用于判定乱序、重放与缺口（design/08 §4）；列、类型与主键与 messaging-consumed-event.sql 一致。';

CREATE TABLE `t_compensation_event` (
  `id` BIGINT NOT NULL COMMENT '本库补偿事件记录的 Snowflake 主键，不作为跨系统事件标识。',
  `event_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Handler 读取本服务权威事实使用的稳定业务键，最长 128 个字符；去首尾空白、拒绝控制字符，大小写敏感。',
  `event_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '全应用唯一注册的 Handler 类型，最长 64 个字符，大小写敏感。取值来源：本服务 compensation-event-catalog.yaml；非固定枚举。',
  `sub_event_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '同一 Handler 下的受控业务子类型；无业务子类型时为占位值 -；与 event_key、event_type 共同组成唯一身份。取值来源：本服务补偿子类型目录；非固定枚举。',
  `payload_json` VARCHAR(4096) NULL COMMENT '可选最小 JSON Object，通常为空；最多 4 KiB，不得保存可从本库恢复的上下文、敏感明文或凭据。',
  `execute_status` VARCHAR(16) NOT NULL DEFAULT 'INIT' COMMENT '本地补偿任务执行状态；成功后删除记录。允许值：INIT、PROCESSING、ERROR、FAILED。',
  `execute_count` INTEGER NOT NULL DEFAULT 0 COMMENT '已开始的 Handler 尝试次数，非负，Claim 时增加。',
  `next_execute_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '任务下次允许 Claim 的 UTC 微秒时间，由 Handler 退避策略更新。',
  `lock_owner` VARCHAR(64) NULL COMMENT '当前持有数据库 Claim Lease 的服务实例标识；未被认领时为空。',
  `lock_until` DATETIME(6) NULL COMMENT 'Claim Lease 到期并可被其他实例接管的 UTC 微秒时间；无活动 Lease 时为空。',
  `last_error_code` VARCHAR(64) NULL COMMENT '最近一次执行失败的稳定错误分类代码；尚无错误时为空。取值来源：本服务补偿执行错误代码目录；非固定枚举。',
  `last_error_summary` VARCHAR(1000) NULL COMMENT '最近一次执行失败的脱敏摘要，最长 1000 字符，禁止保存敏感明文。',
  `lock_version` BIGINT NOT NULL DEFAULT 1 COMMENT 'Claim 和 Lease 使用的正乐观锁版本；登记初始值固定为 1，仅当前 Lease 持有者可回写执行结果。',
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '补偿记录与业务事实原子登记的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '补偿记录最近更新的 UTC 微秒时间，由写入口维护。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comp_event_identity` (`event_key`, `event_type`, `sub_event_type`) COMMENT '保证业务唯一性：event_key、event_type、sub_event_type',
  KEY `idx_comp_event_ready` (`execute_status`, `next_execute_at`, `id`) COMMENT '支持访问路径：execute_status、next_execute_at、id',
  KEY `idx_comp_event_lease` (`execute_status`, `lock_until`, `id`) COMMENT '支持访问路径：execute_status、lock_until、id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='与本库业务事实在同一事务登记的本地可靠执行记录（paytech-compensation-event 现行模型，COMPENSATION_EVENT_GUIDE 第 3 节）；成功后删除，FAILED 保留并可受控重开';
