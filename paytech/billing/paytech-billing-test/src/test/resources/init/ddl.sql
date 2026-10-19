-- billing-service Mock 测试环境准备（管理员手工执行；应用与测试从不自动执行）。
-- 测试复用仓库 local-environment 的 MySQL；账号与 application-mock.yml 一致，只授予本库最小 DML 权限（design/10 §4.2）。
-- 已有库和账号不删除、不重置。表结构逐字取自 paytech/billing/docs/database/sql/billing-schema.sql 的全部 11 张表。
-- 保留行逐字取自 paytech/billing/docs/database/sql/billing-initial-data.sql（测试只删除币种 ID ≥ 1,000,000 与非 DEFAULT 日历的行）。
-- 由旧 9 表结构重建时，先在 mor_billing 依次执行需求 requirements/20261019/billing-计费事实模型重构与渠道成本迁出 的 db/billing/ddl.sql
-- 与 dml.sql（DROP 旧 7 表、CREATE 新 9 表、插入日历），或删库后执行本文件。
CREATE DATABASE IF NOT EXISTS mor_billing DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'billing_app'@'%' IDENTIFIED BY '92f01bf0c17cbbdc07914d46880c949dfc3de5f359d133a2';
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_billing.* TO 'billing_app'@'%';
USE mor_billing;
SET NAMES utf8mb4;

CREATE TABLE `t_currency_spec` (
  `id` BIGINT NOT NULL COMMENT '服务侧生成的币种规则 Snowflake 主键。',
  `currency` CHAR(3) NOT NULL COMMENT 'ISO 4217 三位币种代码。取值来源：ISO 4217 币种代码；非固定枚举。',
  `minor_unit` TINYINT NOT NULL COMMENT '记账与结算金额允许保留的小数位数，取值范围为零至八。',
  `rounding_mode` VARCHAR(64) NOT NULL COMMENT '金额从八位计算精度归整到币种精度时采用的固定方式。允许值：HALF_UP。',
  `status` VARCHAR(64) NOT NULL COMMENT '币种规则当前是否允许被新业务使用。允许值：DRAFT、ACTIVE、INACTIVE。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_currency` (`currency`) COMMENT '保证业务唯一性：currency'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='只读副本，Owner platform-support（D41）：保存 Finance 使用的 ISO 4217 币种精度、舍入方式和启停状态；ledger 与 billing 各持一份，不在本服务写入。';

CREATE TABLE `t_billing_fact` (
  `id` BIGINT NOT NULL COMMENT '计费事实 Snowflake 主键；BillingFactRecorded 的聚合 ID。',
  `fact_type` VARCHAR(64) NOT NULL COMMENT '事实类型。允许值：PAYMENT、REFUND、DUPLICATE_CAPTURE、DUPLICATE_COMPENSATION、DISPUTE、COST_ADJUSTMENT、CHANNEL_SETTLEMENT、PAYOUT_SETTLED、TAX_PAID、MANUAL_ADJUSTMENT、FX_SELL、FX_BUY。',
  `origin_type` VARCHAR(16) NOT NULL COMMENT '来源方式：AUTO 由事件或系统命令生成、登记即 APPROVED；MANUAL 人工登记、DRAFT 经 Checker 批准。允许值：AUTO、MANUAL。',
  `source_system` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源系统代码。取值来源：Code-MoR 系统目录；非固定枚举。',
  `source_event_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源事件键或命令业务键（支付尝试事件键、出款单号、清算明细 ID、缴税单号、工单号），与 source_system 组成幂等键。',
  `source_version` BIGINT NOT NULL COMMENT '来源业务事实的版本；命令类与人工登记固定为 1。',
  `source_payload_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源载荷或命令请求的 RFC 8785 SHA-256 小写十六进制；同键再来时比对，不同则拒绝。',
  `parent_fact_id` BIGINT NULL COMMENT '派生来源：退款、争议、重复扣款及其补偿、成本调整指向原支付事实；其余为空。',
  `supersedes_fact_id` BIGINT NULL COMMENT '更正链：本事实替代的前一事实（同 Client、同 OE、同币种）；首次登记为空。',
  `client_id` BIGINT NULL COMMENT 'CCT Client 稳定业务 ID；资金类事实（CHANNEL_SETTLEMENT、PAYOUT_SETTLED、TAX_PAID）为空。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Operating Entity 编号，决定 account 入账流与 ledger 账簿。',
  `local_partner_id` BIGINT NULL COMMENT 'Local Partner 稳定业务 ID；无合作方为空，与 finance_entity_id 同有同无。',
  `finance_entity_id` BIGINT NULL COMMENT '平台服务费收取方 Finance Entity（ledger 主数据主键）；无合作方为空。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本事实全部金额的大写 ISO 4217 币种；一笔外汇兑换按币种拆为 FX_SELL 与 FX_BUY 两条事实。',
  `gross_amount` DECIMAL(24,8) NOT NULL COMMENT '业务事实非负总额：支付为毛额，退款为退款额，资金类为银行确认额，人工调整为明细行之和。',
  `business_time` DATETIME(6) NOT NULL COMMENT '业务发生的 UTC 微秒时间：支付成功、退款成功、争议结论、银行确认、兑换成交、人工批准；ledger 过账日期统一取它。',
  `balance_available_time` DATETIME(6) NULL COMMENT '仅 PAYMENT：按命中价格的 T+N、日历、时区、截止秒算出的应付到期转可用 UTC 时间；account 定时任务读取。',
  `status` VARCHAR(16) NOT NULL COMMENT '登记状态。允许值：DRAFT（仅 MANUAL）、APPROVED、SUPERSEDED。',
  `fact_version` BIGINT NOT NULL DEFAULT 0 COMMENT '批准时定稿：首次 1，替代事实为前版本 + 1，DRAFT 为 0；事件聚合版本。',
  `maker_id` BIGINT NULL COMMENT 'MANUAL 事实的提交人；AUTO 为空。',
  `checker_id` BIGINT NULL COMMENT 'MANUAL 事实的批准人，须不同于提交人；AUTO 为空。',
  `approved_time` DATETIME(6) NULL COMMENT '批准（AUTO 为登记）的 UTC 微秒时间，即事件 occurredAt；DRAFT 为空。',
  `published_at` DATETIME(6) NULL COMMENT 'BillingFactRecorded 首次收到 Broker 确认的 UTC 微秒时间；未发布为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '记录最近一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fact_source` (`source_system`, `source_event_key`) COMMENT '保证业务唯一性：source_system、source_event_key',
  KEY `idx_fact_book_oe` (`entity_no`, `currency`, `id`) COMMENT '支持访问路径：entity_no、currency、id',
  KEY `idx_fact_book_fe` (`finance_entity_id`, `currency`, `id`) COMMENT '支持访问路径：finance_entity_id、currency、id',
  KEY `idx_fact_client` (`client_id`, `entity_no`, `currency`) COMMENT '支持访问路径：client_id、entity_no、currency',
  KEY `idx_fact_supersedes` (`supersedes_fact_id`) COMMENT '支持访问路径：supersedes_fact_id',
  KEY `idx_fact_parent` (`parent_fact_id`) COMMENT '支持访问路径：parent_fact_id',
  KEY `idx_fact_maturity` (`fact_type`, `balance_available_time`) COMMENT '支持访问路径：fact_type、balance_available_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='计费事实头：任何会改变 Client、合作伙伴、平台或银行现金余额的金额在此落为不可变、带版本的事实，account 记明细账、ledger 记总账都只消费它；内容登记后不改，只推进状态、版本与发布时间。';

CREATE TABLE `t_billing_fact_ref` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake 主键。',
  `fact_id` BIGINT NOT NULL COMMENT '所属计费事实（t_billing_fact.id）。',
  `ref_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '引用的外部业务对象类型。允许值：ORDER、ORDER_CONTEXT、PAYMENT_INTENT、PAYMENT_ATTEMPT、PAYMENT_CHANNEL_ACCOUNT、MID、REFUND_REQUEST、CHANNEL_REFUND、DUPLICATE_ITEM、DISPUTE_NOTICE、DISPUTE_CASE、TAX_SNAPSHOT、CLEARING_ACCOUNT、SETTLEMENT_BATCH、SETTLEMENT_ITEM、CASH_ACCOUNT、PAYOUT、TAX_PAYMENT、TICKET、FX_DEAL、FX_QUOTE。',
  `ref_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '按 ref_type 解释的稳定标识：数字 ID 存规范十进制字符串，OE 类存 entityNo；来源 Owner 各异，不建立跨库外键。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fact_ref` (`fact_id`, `ref_type`) COMMENT '保证业务唯一性：fact_id、ref_type',
  KEY `idx_fact_ref_lookup` (`ref_type`, `ref_id`) COMMENT '支持访问路径：ref_type、ref_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='计费事实引用的外部业务对象，一行一个；每种事实类型必填的引用集合由应用层目录约束，新增事实类型只增加 ref_type 代码。';

CREATE TABLE `t_billing_fact_pricing` (
  `fact_id` BIGINT NOT NULL COMMENT '对应的 PAYMENT 计费事实（t_billing_fact.id）；与头 1:1，只有 PAYMENT 有行。',
  `client_agreement_id` BIGINT NOT NULL COMMENT '收单订单上下文冻结的 commercial Client / OE 合同 ID。',
  `partner_agreement_oe_id` BIGINT NULL COMMENT '冻结的合作伙伴协议与 OE 关联 ID；无合作方为空。',
  `commercial_context_time` DATETIME(6) NOT NULL COMMENT '收单冻结并用于命中合同版本的 UTC 微秒时间。',
  `consumer_country_code` CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '消费者国家 ISO 3166-1 alpha-2，价格命中维度。',
  `payment_method` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Payment 支付方式目录代码，价格命中维度。',
  `industry_code` VARCHAR(64) NULL COMMENT '行业分类代码；非固定枚举。',
  `mcc_code` CHAR(4) NULL COMMENT '保留前导零的四位 MCC。',
  `card_network` VARCHAR(32) NULL COMMENT 'Payment 给出的卡组织代码，价格命中维度；非卡支付为空。',
  `funding_type` VARCHAR(32) NULL COMMENT '资金类型（借记 / 贷记 / 预付）代码；非卡支付为空。',
  `card_level` VARCHAR(32) NULL COMMENT '卡等级代码；非卡支付为空。',
  `authentication_class` VARCHAR(32) NULL COMMENT '3DS 认证结果类别代码；未认证为空。',
  `commercial_price_id` BIGINT NOT NULL COMMENT 'commercial 命中的 Price（GET /commercial-prices/v1/{priceId}）；费用与到期输入都来自这一行。',
  `settlement_business_days` INT NOT NULL COMMENT '命中价格对应开通项的非负工作日数 N。',
  `business_calendar_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '命中价格引用的工作日日历代码（t_business_calendar.calendar_code 只读副本）。',
  `settlement_timezone` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '结算时区（IANA），初期 UTC。',
  `cutoff_second_of_day` INT NOT NULL COMMENT '当日截止秒，初期 86399；超过截止滚至下一工作日。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  PRIMARY KEY (`fact_id`),
  KEY `idx_pricing_price` (`commercial_price_id`) COMMENT '支持访问路径：commercial_price_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='PAYMENT 事实的定价输入与结算输入：命中价格用到的合同坐标、订单维度、支付事实，以及同一条命中价格给出的 T+N 参数；反向事实沿 parent_fact_id 回看。';

CREATE TABLE `t_billing_fact_line` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake 主键。',
  `fact_id` BIGINT NOT NULL COMMENT '所属计费事实（t_billing_fact.id）。',
  `line_no` INT NOT NULL COMMENT '事实内从 1 起连续的行号。',
  `component_type` VARCHAR(64) NOT NULL COMMENT '金额组成类型。允许值：CLIENT_PRINCIPAL、CLIENT_FEE、SELLER_TAX、PARTNER_SHARE、PLATFORM_SERVICE_CHARGE、CHANNEL_COST、CHANNEL_COST_SETTLED、DUPLICATE_AMOUNT、PAYOUT_FEE、PAYOUT_AMOUNT、SETTLEMENT_AMOUNT、TAX_PAYMENT_AMOUNT、FX_SOLD_AMOUNT、FX_BOUGHT_AMOUNT、FX_FEE。成本调整用 CHANNEL_COST + ADJUSTMENT，增提或冲减由付方 / 收方方向表达。',
  `payer_role` VARCHAR(32) NOT NULL COMMENT '付方角色。允许值：CLIENT、LOCAL_PARTNER、CHANNEL、OPERATING_ENTITY、FINANCE_ENTITY、TAX_AUTHORITY、CASH_ACCOUNT、FX_ACCOUNT、CONSUMER（仅重复扣款两类事实）。',
  `payer_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '按 payer_role 解释的稳定标识；OE 为 entityNo，其余为规范十进制字符串。',
  `payee_role` VARCHAR(32) NOT NULL COMMENT '收方角色，允许值同 payer_role；付方与收方不得相同。',
  `payee_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '按 payee_role 解释的稳定标识。',
  `basis_amount` DECIMAL(24,8) NOT NULL COMMENT '本行费率或规则实际使用的非负基数；资金类与人工行等于金额本身。',
  `rate_value` DECIMAL(18,10) NULL COMMENT '实际使用的十进制费率；固定金额或非规则行为空。',
  `raw_amount` DECIMAL(24,8) NOT NULL COMMENT '按八位小数计算、尚未按币种精度舍入的金额。',
  `posted_amount` DECIMAL(24,8) NOT NULL COMMENT '按币种精度 HALF_UP 归整后的正金额，是 account 与 ledger 使用的数。',
  `effect_direction` VARCHAR(16) NOT NULL COMMENT '本行相对原事实的作用。允许值：CHARGE、REVERSAL、ADJUSTMENT。',
  `target_bucket` VARCHAR(16) NULL COMMENT '仅 MANUAL_ADJUSTMENT：收方 Owner 账户的目标余额桶（决定 2026-10-19 第 14 条）。允许值：PENDING、AVAILABLE；为空按 AVAILABLE；其余事实为空。account 映射随后续切片启用。',
  `source_rule_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '提供计算参数的规则族；资金类与人工行为空。允许值：COMMERCIAL_PRICE、PARTNER_SHARE、PLATFORM_SERVICE_CHARGE、TAX_RULE、CHANNEL_COST、FX_RATE。',
  `source_rule_version_id` BIGINT NULL COMMENT '规则族中实际使用的不可变版本 ID；规则行必填。',
  `source_price_id` BIGINT NULL COMMENT 'COMMERCIAL_PRICE 族命中的 Price ID；其他族为空。',
  `source_rule_component_id` BIGINT NULL COMMENT '规则中命中的组件 / 阶梯 / 范围的稳定 ID；无则为空。',
  `source_evidence` JSON NULL COMMENT '计算证据：{schemaVersion, profileCode, scopeId, componentCode, triggerCode, calculationBase, calculationMethod, rate, fixedAmount, currency, minAmount, maxAmount, contentHash, roundingMode, bands:[{bandId, bandNo, lowerBound, upperBound, rate, fixedAmount, basisAmount, rawAmount}], taxLineId, fxQuoteId, fxRate, fxSpread}；最大 64 KiB；规则行必填，资金类与人工行为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fact_line` (`fact_id`, `line_no`) COMMENT '保证业务唯一性：fact_id、line_no',
  KEY `idx_fact_line_payee` (`payee_role`, `payee_id`, `component_type`) COMMENT '支持访问路径：payee_role、payee_id、component_type',
  KEY `idx_fact_line_rule` (`source_rule_type`, `source_rule_version_id`) COMMENT '支持访问路径：source_rule_type、source_rule_version_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='计费事实明细行：每行一个付方 → 收方的金额及其规则来源证据；account 按付方 / 收方与组件类型推导借贷，ledger 按组件类型套规则包。';

CREATE TABLE `t_billing_fact_line_link` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake 主键。',
  `source_line_id` BIGINT NOT NULL COMMENT '被冲回或被调整的原明细行（t_billing_fact_line.id）。',
  `target_line_id` BIGINT NOT NULL COMMENT '产生冲回或调整的新明细行（t_billing_fact_line.id）。',
  `link_type` VARCHAR(16) NOT NULL COMMENT '链接类型。允许值：REVERSAL、ADJUSTMENT。',
  `linked_amount` DECIMAL(24,8) NOT NULL COMMENT '本链接承担的正金额；同一原行的 REVERSAL 累计不得超过原行 posted_amount。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fact_line_link` (`source_line_id`, `target_line_id`, `link_type`) COMMENT '保证业务唯一性：source_line_id、target_line_id、link_type',
  KEY `idx_fact_line_link_target` (`target_line_id`) COMMENT '支持访问路径：target_line_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='退款、争议、重复扣款、成本调整等反向或调整行与原行之间的金额链接，用于封顶与追溯。';

CREATE TABLE `t_business_calendar` (
  `calendar_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工作日日历代码，commercial Price 的 business_calendar_code 引用它。',
  `display_name` VARCHAR(128) NOT NULL COMMENT '显示名。',
  `timezone` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '日历所在时区（IANA）。',
  `status` VARCHAR(16) NOT NULL COMMENT '允许值：ACTIVE、INACTIVE。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '记录最近一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`calendar_code`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='工作日日历（平台参考数据的只读副本，同币种表模式）：默认周一至周五为工作日，例外日在 t_business_calendar_day 登记。';

CREATE TABLE `t_business_calendar_day` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake 主键。',
  `calendar_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属日历。',
  `calendar_date` DATE NOT NULL COMMENT '例外日期（日历时区）。',
  `day_type` VARCHAR(16) NOT NULL COMMENT '例外类型。允许值：WORKING（周末调休上班）、NON_WORKING（节假日）。',
  `note` VARCHAR(128) NULL COMMENT '说明。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_calendar_day` (`calendar_code`, `calendar_date`) COMMENT '保证业务唯一性：calendar_code、calendar_date'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='工作日日历的例外日；T+N 计算只向后数 WORKING 日，覆盖不到目标日期时失败等待，不猜。';

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

INSERT INTO `t_currency_spec` (`id`, `currency`, `minor_unit`, `rounding_mode`, `status`, `create_time`, `update_time`)
VALUES (1001, 'USD', 2, 'HALF_UP', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO `t_business_calendar` (`calendar_code`, `display_name`, `timezone`, `status`, `create_time`, `update_time`)
VALUES ('DEFAULT', '默认工作日日历（周一至周五）', 'UTC', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
