-- mor-test mor_commercial 增量 DDL（执行文件草案，阶段 6 评审后执行）。前提：mor-test 当前为 14 张旧表且全空（2026-10-19 核对）。
-- 第 1 段 = design-discuss/commercial/local-mor_commercial-round2.sql；第 2 段 = paytech/commercial/docs/database/sql/local/2026-10-19-round3-local.sql；第 3 段 = 目录初始化 DML docs/database/sql/commercial-initial-data.sql。
-- 回退：按两段脚本注释逆序执行；不含 DROP 库表。

-- 仅本机 local-environment 的测试库 mor_commercial；不含 DROP 库表；对应 paytech-commercial-test/src/test/resources/init/ddl.sql 2026-10-19 第二轮形状。
USE mor_commercial;
RENAME TABLE t_payment_method_profile TO t_pricing_profile, t_payment_method_profile_cond TO t_pricing_profile_cond;

ALTER TABLE t_pricing_profile
  RENAME COLUMN payment_method_profile_id TO pricing_profile_id,
  RENAME COLUMN payment_method_profile_code TO pricing_profile_code,
  MODIFY COLUMN payment_method_code VARCHAR(64) NULL COMMENT 'profile_kind=PAYMENT_METHOD 时必填并精确绑定的 Payment Method 稳定代码，其他种类为空；由 Payment 目录拥有，CCT 不建立 Payment Method 主表。取值来源：Payment 拥有的 Payment Method 稳定代码目录；非固定枚举。',
  ADD COLUMN profile_kind VARCHAR(32) NOT NULL DEFAULT 'PAYMENT_METHOD' COMMENT '定价画像描述的定价对象种类；ACTIVE 后冻结，决定 payment_method_code 是否必填及可用条件维度。允许值：PAYMENT_METHOD、PAYOUT_RAIL、FX_PAIR、DISPUTE、PERIODIC。' AFTER profile_name,
  DROP INDEX uk_payment_method_profile_code,
  DROP INDEX idx_pm_profile_lookup,
  ADD UNIQUE KEY uk_pricing_profile_code (pricing_profile_code),
  ADD KEY idx_pricing_profile_lookup (status, payment_method_code, pricing_profile_code);
ALTER TABLE t_pricing_profile ALTER COLUMN profile_kind DROP DEFAULT;

ALTER TABLE t_pricing_profile_cond
  RENAME COLUMN payment_method_profile_id TO pricing_profile_id,
  DROP INDEX uk_pm_profile_cond_value,
  DROP INDEX idx_pm_cond_lookup,
  ADD UNIQUE KEY uk_pricing_profile_cond_value (pricing_profile_id, dimension_code, condition_value),
  ADD KEY idx_pricing_cond_lookup (dimension_code, condition_value, pricing_profile_id);

ALTER TABLE t_commercial_term_price
  RENAME COLUMN payment_method_profile_code TO pricing_profile_code,
  DROP INDEX uk_term_price_profile,
  DROP INDEX idx_term_price_profile,
  ADD UNIQUE KEY uk_term_price_profile (term_version_id, entity_no, pricing_profile_code),
  ADD KEY idx_term_price_profile (pricing_profile_code, term_version_id);

ALTER TABLE t_agreement
  ADD COLUMN business_mode VARCHAR(16) NULL COMMENT 'agreement_type=CLIENT 时必填的合同业务模式，PARTNER 时为空；创建后不可修改。允许值：MOR、PSP。' AFTER agreement_type,
  ADD COLUMN fund_mode VARCHAR(32) NULL COMMENT 'agreement_type=CLIENT 时必填的资金模式，PARTNER 时为空；MOR 只允许 SETTLE_THROUGH_PLATFORM，FEES_ONLY 只允许 OE 来源 CLIENT，本期只接受 SETTLE_THROUGH_PLATFORM。允许值：SETTLE_THROUGH_PLATFORM、FEES_ONLY。' AFTER business_mode,
  ADD COLUMN pricing_authority VARCHAR(16) NULL COMMENT 'agreement_type=CLIENT 时必填的定价权归属；PARTNER 表示价目由推荐伙伴提出、平台运营录入审批，Partner 门户只读；本期只接受 PLATFORM。允许值：PLATFORM、PARTNER。' AFTER fund_mode,
  ADD COLUMN referral_partner_id BIGINT NULL COMMENT '推荐该合同的 Local Partner，client-master 跨库引用只存值；签署时冻结，pricing_authority=PARTNER 时必须非空。' AFTER pricing_authority,
  ADD KEY idx_agreement_referral (referral_partner_id);

-- 仅本机 local-environment 的测试库 mor_commercial（已按 2026-10-19 第二轮脚本 design-discuss/commercial/local-mor_commercial-round2.sql 变更）；
-- 第三轮：新增六张产品/开通表，t_commercial_term_version / t_commercial_term_price / t_commercial_term_component 加列，
-- 并同步 t_commercial_term_plan 与上述三表的列注释、唯一键与表注释；执行后本机库与 docs/database/sql/commercial-schema.sql 及
-- paytech-commercial-test/src/test/resources/init/ddl.sql 一致。全部定义逐字取自 commercial-schema.sql。
-- 前提：四张被修改的条款表为空（auto 测试每例清理全部表）；新增 NOT NULL 列不带默认值。不含 DROP 库表，不含初始化 DML。
-- 回退：DROP 六张新表；按第二轮形状删除新增列并恢复原唯一键 / 索引（见 git 历史中第二轮 init/ddl.sql）。
-- 只写不执行：由主会话审阅后人工执行。
USE mor_commercial;
SET NAMES utf8mb4;

CREATE TABLE `t_agreement_product` (
  `agreement_product_id` BIGINT NOT NULL COMMENT '合同订阅产品行的内部唯一标识。',
  `agreement_id` BIGINT NOT NULL COMMENT '商业合同稳定身份的内部唯一标识。',
  `product_code` VARCHAR(64) NOT NULL COMMENT '合同订阅的产品代码；产品 business_mode_scope 必须包含合同 business_mode，基座产品不写行。允许值：ACQUIRING、PAYOUT、FX、DISPUTE、TAX_INVOICE、DATA_ANALYTICS、PLATFORM。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`agreement_product_id`),
  UNIQUE KEY `uk_agreement_product` (`agreement_id`, `product_code`) COMMENT 'Unique index for agreement_product on agreement_id, product_code',
  KEY `idx_agreement_product_code` (`product_code`) COMMENT 'Access index for agreement_product on product_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='合同订阅的可选产品模块；uk_agreement_product(agreement_id,product_code)；产品的business_mode_scope必须包含合同business_mode；base_product=1的基座产品无需订阅、不写行；应用校验，不创建 CHECK/FK；business_mode_scope=BOTH 兼容 MOR 与 PSP 合同；产品 RETIRED 后禁止新订阅，历史订阅可读。';

CREATE TABLE `t_agreement_capability` (
  `agreement_capability_id` BIGINT NOT NULL COMMENT '合同在准确 OE 下开通项行的内部唯一标识。',
  `agreement_id` BIGINT NOT NULL COMMENT '商业合同稳定身份的内部唯一标识。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '开通项适用的准确 OE；CLIENT 合同须等于合同 entity_no，PARTNER 合同须在合同覆盖 OE 内。',
  `capability_code` VARCHAR(64) NOT NULL COMMENT '开通的可开通项代码；引用时目录项须为 ACTIVE。取值来源：t_product_capability.capability_code 可开通项目录；非固定枚举。',
  `status` VARCHAR(32) NOT NULL COMMENT '开通项状态；SUSPENDED 为风控或合规临时停用且可恢复，DISABLED 为运营关闭。允许值：ENABLED、SUSPENDED、DISABLED。',
  `effective_from` DATETIME(6) NOT NULL COMMENT '开通生效区间的 UTC 微秒精度起点，包含该时刻。',
  `effective_to` DATETIME(6) NULL COMMENT '开通生效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。',
  `rolling_reserve_rate` DECIMAL(18,10) NULL COMMENT '滚动保证金按交易金额计提的小数比例，取值范围为 0 至 1；无保证金时为空。',
  `rolling_reserve_days` INT NULL COMMENT '滚动保证金的持有天数；与比例成组为空或非空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`agreement_capability_id`),
  UNIQUE KEY `uk_agreement_capability` (`agreement_id`, `entity_no`, `capability_code`) COMMENT 'Unique index for agreement_capability on agreement_id, entity_no, capability_code',
  KEY `idx_agreement_capability_lookup` (`entity_no`, `capability_code`, `status`) COMMENT 'Access index for agreement_capability on entity_no, capability_code, status',
  KEY `idx_agreement_capability_code` (`capability_code`) COMMENT 'Access index for agreement_capability on capability_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='合同在准确OE下的开通项与保证金条款；uk_agreement_capability(agreement_id,entity_no,capability_code)；所属产品必须已在合同订阅；capability_code引用时须为ACTIVE目录项；CLIENT合同entity_no等于合同entity_no，PARTNER合同须在合同覆盖OE内；状态ENABLED/SUSPENDED/DISABLED，有效区间半开；保证金比例与天数成组为空或非空，执行归account/risk；应用校验，不创建 CHECK/FK；所属产品必须已在 agreement_product 订阅；capability_code 引用时目录项须为 ACTIVE。；CLIENT 合同的 entity_no 必须等于 agreement.entity_no；PARTNER 合同的 entity_no 必须在 agreement_operating_entity 内。；CHECK (effective_to IS NULL OR effective_from < effective_to)，解析时要求 status=ENABLED 且 effective_from <= contextAsOfTime < effective_to。；rolling_reserve_rate 与 rolling_reserve_days 成组为空或成组非空；比例取值 0 至 1，保证金执行归 account / risk。';

CREATE TABLE `t_agreement_capability_limit` (
  `agreement_capability_limit_id` BIGINT NOT NULL COMMENT '开通项约束行的内部唯一标识。',
  `agreement_capability_id` BIGINT NOT NULL COMMENT '合同在准确 OE 下开通项行的内部唯一标识。',
  `limit_code` VARCHAR(64) NOT NULL COMMENT '开通项约束的类别代码；同码多行按 OR，异码按 AND。允许值：CURRENCY、CONSUMER_COUNTRY、REQUIRE_3DS。',
  `limit_value` VARCHAR(128) NOT NULL COMMENT '约束的规范化值：ISO 4217 币种、ISO 3166-1 alpha-2 国家或固定值 1。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`agreement_capability_limit_id`),
  UNIQUE KEY `uk_agreement_capability_limit` (`agreement_capability_id`, `limit_code`, `limit_value`) COMMENT 'Unique index for agreement_capability_limit on agreement_capability_id, limit_code, limit_value'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='开通项约束，一值一行；uk_agreement_capability_limit(agreement_capability_id,limit_code,limit_value)；同码多行按OR、异码按AND；金额限额归风控决策，不在此表；随开通项冻结，不单独审计；CURRENCY 用 ISO 4217，CONSUMER_COUNTRY 用 ISO 3166-1 alpha-2，REQUIRE_3DS 固定为 1。';

CREATE TABLE `t_product` (
  `product_id` BIGINT NOT NULL COMMENT '产品目录行的内部唯一标识。',
  `product_code` VARCHAR(64) NOT NULL COMMENT '可售产品模块的稳定代码，创建后不可修改。允许值：ACQUIRING、PAYOUT、FX、DISPUTE、TAX_INVOICE、DATA_ANALYTICS、PLATFORM。',
  `product_name` VARCHAR(128) NOT NULL COMMENT '供 Operations 识别产品的展示名称。',
  `business_mode_scope` VARCHAR(16) NOT NULL COMMENT '该产品允许被哪种合同业务模式订阅。允许值：MOR_ONLY、PSP_ONLY、BOTH。',
  `base_product` TINYINT(1) NOT NULL COMMENT '1 表示基座产品，所有实例必装且合同无需订阅；0 表示可选模块；不允许 NULL。',
  `status` VARCHAR(64) NOT NULL COMMENT '产品目录行的生命周期状态；RETIRED 阻止新合同订阅，历史订阅继续可读。允许值：DRAFT、ACTIVE、RETIRED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`product_id`),
  UNIQUE KEY `uk_product_code` (`product_code`) COMMENT 'Unique index for product on product_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='L0产品，即可售模块；uk_product_code(product_code)；product_code创建后不可改；状态DRAFT/ACTIVE/RETIRED，RETIRED阻止新合同订阅、历史订阅可读；随产品发版维护，不接入BCR；base_product 只允许 0 或 1，基座产品对所有合同默认生效。';

CREATE TABLE `t_product_capability` (
  `product_capability_id` BIGINT NOT NULL COMMENT '可开通项目录行的内部唯一标识。',
  `product_code` VARCHAR(64) NOT NULL COMMENT '可开通项所属产品的稳定代码。允许值：ACQUIRING、PAYOUT、FX、DISPUTE、TAX_INVOICE、DATA_ANALYTICS、PLATFORM。',
  `capability_code` VARCHAR(64) NOT NULL COMMENT '全局唯一且稳定的可开通项代码，如 CARD_VISA、PAYOUT_LOCAL。取值来源：产品发版维护的能力目录；非固定枚举。',
  `capability_name` VARCHAR(128) NOT NULL COMMENT '供 Operations 识别可开通项的展示名称。',
  `capability_kind` VARCHAR(32) NOT NULL COMMENT '可开通项所属的能力种类，决定其是否绑定 Payment Method。允许值：PAYMENT_METHOD、PAYOUT_RAIL、FX、DISPUTE、PERIODIC_SERVICE。',
  `payment_method_code` VARCHAR(64) NULL COMMENT 'capability_kind=PAYMENT_METHOD 时必填的 Payment Method 稳定代码，其他种类为空。取值来源：Payment 拥有的 Payment Method 稳定代码目录；非固定枚举。',
  `dimension_code` VARCHAR(64) NULL COMMENT '把开通项细分到画像维度的单一条件维度，与 dimension_value 成组为空或非空。允许值：CARD_BRAND、FUNDING_TYPE、CARD_LEVEL、CARD_PRODUCT、ISSUER_REGION、ISSUER_COUNTRY、THREE_DS_RESULT、CURRENCY、CONSUMER_COUNTRY、TRANSACTION_TYPE、PAYOUT_RAIL、DESTINATION_COUNTRY、CURRENCY_PAIR、DISPUTE_STAGE。',
  `dimension_value` VARCHAR(128) NULL COMMENT '开通项细分维度的单个规范化值，如 AMEX。',
  `status` VARCHAR(64) NOT NULL COMMENT '可开通项目录行的生命周期状态；合同引用时须为 ACTIVE。允许值：DRAFT、ACTIVE、RETIRED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`product_capability_id`),
  UNIQUE KEY `uk_product_capability_code` (`capability_code`) COMMENT 'Unique index for product_capability on capability_code',
  KEY `idx_product_capability_product` (`product_code`, `status`) COMMENT 'Access index for product_capability on product_code, status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='L1可开通项目录；uk_product_capability_code(capability_code)；capability_code全局唯一稳定；PAYMENT_METHOD种类payment_method_code必填，其他种类为空；dimension_code与dimension_value成组为空或非空；随产品发版维护，不接入BCR；capability_kind=PAYMENT_METHOD 要求 payment_method_code IS NOT NULL，其他种类要求 payment_method_code IS NULL。；dimension_code 与 dimension_value 成组为空或成组非空。';

CREATE TABLE `t_product_fee_event` (
  `product_fee_event_id` BIGINT NOT NULL COMMENT '计费事件目录行的内部唯一标识。',
  `product_code` VARCHAR(64) NOT NULL COMMENT '计费事件所属产品的稳定代码。允许值：ACQUIRING、PAYOUT、FX、DISPUTE、TAX_INVOICE、DATA_ANALYTICS、PLATFORM。',
  `fee_event_code` VARCHAR(64) NOT NULL COMMENT '触发收费的业务动作代码，是组件 trigger_code 的值域。取值来源：产品发版维护的计费事件目录；非固定枚举。',
  `term_type` VARCHAR(64) NOT NULL COMMENT '该计费事件可在哪类商业条款下收费；一值一行，本表登记即为方案 term_type 值域。取值来源：本表登记；非固定枚举。',
  `billing_fact_type` VARCHAR(64) NOT NULL COMMENT '该计费事件对应的 billing 事实类型代码。取值来源：billing 事实类型目录；非固定枚举。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`product_fee_event_id`),
  UNIQUE KEY `uk_product_fee_event` (`fee_event_code`, `term_type`) COMMENT 'Unique index for product_fee_event on fee_event_code, term_type',
  KEY `idx_product_fee_event_product` (`product_code`) COMMENT 'Access index for product_fee_event on product_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='L2计费事件目录；uk_product_fee_event(fee_event_code,term_type)；fee_event_code为组件trigger_code值域，term_type为方案term_type值域，一个事件可在哪类条款下收费一值一行；随产品发版维护，不接入BCR；fee_event_code 是 commercial_term_component.trigger_code 的值域，term_type 是 commercial_term_plan.term_type 的值域；均为值引用，不建关系。';

ALTER TABLE `t_commercial_term_plan`
  MODIFY COLUMN `term_type` VARCHAR(64) NOT NULL COMMENT '方案所属的商业条款类型；必须在计费事件目录登记，CLIENT_PRICING、PARTNER_SHARE、PLATFORM_SERVICE_CHARGE 继续有效；PARTNER_BUY_RATE 为 Partner 合同下按画像 × 币种的买入价，结构同 CLIENT_PRICING。取值来源：t_product_fee_event.term_type 登记目录，示例 CLIENT_PRICING、PARTNER_SHARE、PLATFORM_SERVICE_CHARGE、PARTNER_BUY_RATE；非固定枚举。',
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '当前业务记录在其所属聚合生命周期中的状态代码。允许值：DRAFT、ACTIVE、RETIRED。',
  COMMENT='Client Pricing、Partner Share 或 Platform Service Charge 的稳定方案根；uk_comm_term_code(term_type,plan_code)、uk_comm_term_agreement(agreement_id,term_type)、uk_comm_term_partner(local_partner_id,term_type)；CLIENT_PRICING时agreement_id/entity_no/client_id非空且由同一CLIENT合同派生，local_partner_id为空；Partner两类时仅local_partner_id非空，agreement_id/entity_no/client_id为空；不限制Client全局只有一个Plan；Owner XOR：term_type=CLIENT_PRICING 要求 client_id IS NOT NULL AND local_partner_id IS NULL；term_type IN (PARTNER_SHARE,PLATFORM_SERVICE_CHARGE) 要求 client_id IS NULL AND local_partner_id IS NOT NULL。；term_type、client_id 与 local_partner_id 创建后不可修改；MVP 每份 Client Agreement 一个 Client Pricing Plan，每个 Local Partner 可分别拥有一个 Partner Share Plan 和一个 PLATFORM_SERVICE_CHARGE Plan，通过 local_partner_id + term_type 复合唯一。；CLIENT_PRICING 的 agreement_id/entity_no/client_id 必须与同一 Client Agreement 一致，local_partner_id 为空；Partner 两类只填 local_partner_id，Version 冻结其 Partner Agreement。；PARTNER_BUY_RATE 属于 Partner 合同，Owner 规则同 PARTNER_SHARE；结构同 CLIENT_PRICING，按画像 × 币种定价。';

ALTER TABLE `t_commercial_term_version`
  ADD COLUMN `pricing_model` VARCHAR(32) NOT NULL COMMENT '该版本采用的定价模型；本期应用只接受 BLENDED。允许值：BLENDED、INTERCHANGE_PLUS_PLUS。' AFTER `rounding_mode`,
  ADD COLUMN `fee_tax_treatment` VARCHAR(16) NOT NULL COMMENT '该版本费用金额是否已包含税额。允许值：INCLUSIVE、EXCLUSIVE。' AFTER `pricing_model`,
  COMMENT='商业条款版本；uk_comm_term_ver(term_plan_id,version_no)、uk_comm_term_active(term_plan_id,active_slot)；仅 ACTIVE slot=1；Client Pricing 引用同 Client 的 CLIENT Agreement，Partner Share 与 Platform Service Charge 引用同 Local Partner 的 PARTNER Agreement；同一交易命中的两类 Partner Term 必须属于同一 Agreement；Agreement 覆盖全部 Price 的准确 OE，计划有效区间不得超出合同；APPROVED 后 Owner、Agreement、Version No、effective_from、舍入方式、Price、Component 与 Band 冻结；CHECK ((status=''ACTIVE'' AND active_slot=1) OR (status<>''ACTIVE'' AND active_slot IS NULL))；CHECK (effective_to IS NULL OR effective_from < effective_to)；同一 Root 已批准或生效的有效区间不得重叠。；CLIENT_PRICING 必须引用同一 Client 的 CLIENT Agreement；PARTNER_SHARE 与 PLATFORM_SERVICE_CHARGE 必须引用同一 Local Partner 的 PARTNER Agreement，同一交易命中的两类 Partner Term 必须属于同一 Agreement。；Agreement 必须覆盖全部 Price 的准确 Operating Entity，Version 有效区间不得超出 Agreement；APPROVED 后 Version、Price、Component 与 Band 业务字段不可修改。；本期 pricing_model 只接受 BLENDED；激活前 Version 下每个开通项至少命中一条 Price。';

ALTER TABLE `t_commercial_term_price`
  MODIFY COLUMN `term_price_id` BIGINT NOT NULL COMMENT 'Commercial Term Version 下准确 OE、币种与定价画像价格绑定的内部唯一标识。',
  ADD COLUMN `currency` CHAR(3) NOT NULL COMMENT '该价格适用的交易币种，即费用币种；同一画像按币种各一条 Price，不做通配，组件金额同币种。取值来源：ISO 4217；非固定枚举。' AFTER `entity_no`,
  MODIFY COLUMN `priority_no` INT NOT NULL COMMENT '同一 Commercial Term Version、准确 OE 与币种下、相同具体度候选的确定性顺序，数值越小越优先。',
  DROP INDEX `uk_term_price_profile`,
  DROP INDEX `uk_term_price_priority`,
  DROP INDEX `idx_term_price_oe`,
  ADD UNIQUE KEY `uk_term_price_profile` (`term_version_id`, `entity_no`, `currency`, `pricing_profile_code`) COMMENT 'Unique index for commercial_term_price on term_version_id, entity_no, currency, pricing_profile_code',
  ADD UNIQUE KEY `uk_term_price_priority` (`term_version_id`, `entity_no`, `currency`, `priority_no`) COMMENT 'Unique index for commercial_term_price on term_version_id, entity_no, currency, priority_no',
  ADD KEY `idx_term_price_oe` (`entity_no`, `currency`, `term_version_id`) COMMENT 'Access index for commercial_term_price on entity_no, currency, term_version_id',
  COMMENT='合同版本、准确 OE、交易币种与定价画像的价格绑定；uk_term_price_profile(term_version_id,entity_no,currency,pricing_profile_code)、uk_term_price_priority(term_version_id,entity_no,currency,priority_no)；currency 为 ISO 4217 交易币种即费用币种，同一画像按币种各一条 Price，不做通配；Profile Code 引用 ACTIVE Profile；OE 必须被 Agreement 覆盖；先按不同 Dimension 数量选择最高具体度，再按最小 Priority 唯一解析，零命中或仍有歧义均 Fail Closed；CLIENT_PRICING四项结算输入全非空，初期UTC/86399，N按工作日；Partner两类四项全空；费用和成熟输入必须取自同一命中Price，批准后不可修改；每条 Price 属于准确 term_version_id + entity_no，并以 pricing_profile_code 关联一个 ACTIVE 定价画像；Profile 可跨 Client、Version 与 OE 复用。；Operating Entity 必须被 Version 引用的 Agreement 覆盖；不允许 NULL、* 或全 OE 通配，旧全 Agreement-OE Scope 迁移时展开为准确 OE Price。；匹配先选择不同 dimension_code 数量最多的 Profile，再以最小 priority_no 选择；零命中、事实缺失或仍有歧义均 Fail Closed。；PLATFORM_SERVICE_CHARGE 使用受控 Calculation Base Catalog；不得用 Client Fee、Partner Share、Channel Cost 或其残差倒算平台服务费。；CLIENT 的四个结算字段全部非空；PARTNER 的四个结算字段全部为空；初期 settlement_timezone=UTC、cutoff_second_of_day=86399。；CHECK (settlement_business_days IS NULL OR settlement_business_days>=0)；CHECK (cutoff_second_of_day IS NULL OR cutoff_second_of_day BETWEEN 0 AND 86399)；UTC 截止时刻精确至秒的起点；23:59:59.000001 已超过 86399 秒截止，使用冻结的工作日日历和实际成功时刻计算。；费用币种 = 交易币种，同一画像按币种各一条 Price，不做通配；命中顺序为币种精确 → 画像具体度 → priority_no。；组件 currency 非空时必须等于本 Price 的 currency；激活前每个开通项 × 每个 CURRENCY 约束币种至少命中一条 Price。';

ALTER TABLE `t_commercial_term_component`
  MODIFY COLUMN `term_price_id` BIGINT NOT NULL COMMENT 'Commercial Term Version 下准确 OE、币种与定价画像价格绑定的内部唯一标识。',
  MODIFY COLUMN `trigger_code` VARCHAR(64) NOT NULL COMMENT '触发该组件计算的计费事件代码，必须在计费事件目录登记。取值来源：t_product_fee_event.fee_event_code 登记目录；非固定枚举。',
  MODIFY COLUMN `calculation_base` VARCHAR(64) NOT NULL COMMENT '组件计算基数；BUY_RATE_DIFFERENCE = Client 实收费用 − 同场景 PARTNER_BUY_RATE 命中金额，负值取 0，仅 PARTNER_SHARE 组件可用。允许值：GROSS_AMOUNT、NET_AMOUNT、ACTUAL_CLIENT_FEE、FIXED_PER_EVENT、BUY_RATE_DIFFERENCE。',
  ADD COLUMN `refund_behavior` VARCHAR(32) NOT NULL COMMENT '原交易退款时该组件费用的处理方式；非支付类事件的组件填 KEEP。允许值：RETURN_ALL、RETURN_PRORATA、KEEP。' AFTER `calculation_base`,
  ADD COLUMN `band_basis` VARCHAR(32) NULL COMMENT '仅 TIERED 组件非空；TRANSACTION_AMOUNT 按单笔金额匹配档位，PERIOD_VOLUME 按周期累计交易量匹配且本期拒绝激活。允许值：TRANSACTION_AMOUNT、PERIOD_VOLUME。' AFTER `max_amount`,
  MODIFY COLUMN `band_mode` VARCHAR(64) NULL COMMENT '仅 TIERED 组件非空；WHOLE_BASE 将命中档位参数用于完整基数，PROGRESSIVE 对各档位区间分别计算，RETROACTIVE 按周期累计命中档位回溯整期且只允许与 PERIOD_VOLUME 组合。允许值：WHOLE_BASE、PROGRESSIVE、RETROACTIVE。',
  COMMENT='Client Fee、Partner Share 或 Platform Service Charge 计算组件；uk_comm_term_component(term_price_id,component_code)、uk_comm_term_component_no(term_price_id,component_no)；类型 PERCENTAGE/FIXED/TIERED；多行 Component 按 component_no 稳定计算并相加；TIERED 的头部 rate/fixed_amount 为空、band_basis=TRANSACTION_AMOUNT/PERIOD_VOLUME、band_mode=WHOLE_BASE/PROGRESSIVE/RETROACTIVE 且至少一档，RETROACTIVE 只配 PERIOD_VOLUME，其他类型不得有 Band；PERCENTAGE、FIXED、TIERED 必须满足批准的 rate、fixed_amount、currency、min_amount、max_amount 与 band_mode 矩阵。；多个 Component 按 component_no 稳定计算并相加；PERCENTAGE/FIXED 不得拥有 Band，TIERED 头部 rate/fixed_amount 为空、band_mode 非空且至少拥有一档。；CLIENT_PRICING 使用闭合计算基数；PARTNER_SHARE 使用 calculation_base=ACTUAL_CLIENT_FEE 或 BUY_RATE_DIFFERENCE；PLATFORM_SERVICE_CHARGE 使用闭合计算基数，现实场景可使用 GROSS_AMOUNT。；BUY_RATE_DIFFERENCE = Client 实收费用 − 同场景 PARTNER_BUY_RATE 命中金额，负值取 0；仅 PARTNER_SHARE 组件可用。；TIERED 要求 band_basis 非空，其他类型 band_basis 为空；RETROACTIVE 只允许与 PERIOD_VOLUME 组合，本期 band_basis=PERIOD_VOLUME 拒绝激活。；trigger_code 必须在 product_fee_event 登记；非支付类事件的组件 refund_behavior=KEEP。；所属 Version 进入 APPROVED 后结构化 Component 与 Band 不可修改；调整条款必须创建新 Version。';
