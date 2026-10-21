-- 需求：requirements/20261020/reconciliation-渠道资金对账切片1（阶段 3 评审稿，2026-10-20）
-- 目标库：mor_reconciliation
-- 性质：待执行文件（整库重建）。只能在空库执行：执行前先核对旧 18 张表全部为 0 行并备份，
--       DROP DATABASE / CREATE DATABASE 后执行本文件；不写逐表 ALTER（technique.md 2.1 D-12）。
-- 一致性：阶段 4 由 paytech/reconciliation/docs/database/reconciliation.pdma.json 生成的
--       reconciliation-schema.sql 与测试 DDL 必须与本文件的 CREATE 语句逐字一致；不一致以模型为准并回写本文件。
-- 回退：从执行前备份恢复旧 18 张表（库内无业务数据）。
-- 授权：本文件不携带执行授权；本机、mor-test 分别单独授权。
-- 阶段 4 A 线回写（2026-10-20）：补 paytech-messaging 消费幂等两表 t_consumed_event、t_consumed_aggregate_version（与 billing 同定义），
--       BillingFactRecorded 消费（technique §2.4，照 account 按 eventId 去重）依赖它们；库内共 15 张表（12 张业务表 + 3 张基础设施表）。
SET NAMES utf8mb4;

CREATE TABLE `t_clearing_account` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake 主键。',
  `clearing_account_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '运营可读的清算账户编码，渠道账单文件用它指明所属清算账户；创建后不可改。',
  `channel_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渠道编码，挂入的 MID 必须属于该渠道。取值来源：channel-connector 渠道目录；非固定枚举。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '与渠道签约的经营主体编号，挂入的 MID 必须属于该主体。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '清算币种；渠道账单币种必须与之相同。取值来源：ISO 4217 币种代码；非固定枚举。',
  `settlement_cash_account_id` BIGINT NULL COMMENT '渠道净额打入或扣出的本库银行账户；为空时账单对平后不能生成预期银行流水，对平被拦下。',
  `unmatched_wait_days` INTEGER NOT NULL DEFAULT 3 COMMENT '账单行未命中平台流水时的等待天数，期满转为 CHANNEL_LINE_UNMATCHED 异常；非负。',
  `cost_tolerance_amount` DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '单笔平台流水实际渠道费与预估之差的容差，超过开 COST_DEVIATION 异常；非负。',
  `cash_grace_days` INTEGER NOT NULL DEFAULT 2 COMMENT '预期银行流水超过预期日多少天仍未到账视为逾期（超期检测在切片 2 使用）；非负。',
  `status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '停用后不再接收新的平台流水和渠道账单。允许值：ACTIVE、INACTIVE。',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '状态、配置与所含 MID 变更的乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '最后更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_clearing_account_code` (`clearing_account_code`) COMMENT '保证业务唯一性：clearing_account_code',
  KEY `idx_clearing_account_scope` (`entity_no`, `currency`, `status`) COMMENT '支持访问路径：entity_no、currency、status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='渠道清算账户：一个经营主体在一个渠道、一个币种下与渠道结算的边界，平台流水与渠道账单都挂在它下面；与渠道技术账号无关。';

CREATE TABLE `t_clearing_account_mid` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake 主键。',
  `clearing_account_id` BIGINT NOT NULL COMMENT '所属清算账户。',
  `mid_id` BIGINT NOT NULL COMMENT 'channel-connector 的 MID 主键，不建跨库外键。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '从清算账户冗余的币种，用于保证同一 MID + 币种只归属一个清算账户。取值来源：ISO 4217 币种代码；非固定枚举。',
  `create_time` DATETIME(6) NOT NULL COMMENT '挂入的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_clearing_account_mid` (`mid_id`, `currency`) COMMENT '保证业务唯一性：mid_id、currency',
  KEY `idx_clearing_mid_account` (`clearing_account_id`) COMMENT '支持访问路径：clearing_account_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='清算账户所含的 MID；billing 按 MID + 币种解析清算账户。摘除 MID 即删除本行，已生成的平台流水仍属原清算账户。';

CREATE TABLE `t_platform_txn` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake 主键。',
  `clearing_account_id` BIGINT NOT NULL COMMENT '所属清算账户，取自 billing 事实引用 CLEARING_ACCOUNT。',
  `direction` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渠道欠平台为应收，平台欠渠道为应付。允许值：RECEIVABLE、PAYABLE。',
  `txn_source` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源 billing 事实类型；切片 1 只产生 PAYMENT、REFUND。允许值：PAYMENT、DUPLICATE_CAPTURE、REFUND、DUPLICATE_COMPENSATION、CHARGEBACK、CHARGEBACK_REVERSAL。',
  `billing_fact_id` BIGINT NOT NULL COMMENT '来源 billing 事实 ID，消费幂等键。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事实所属经营主体。',
  `source_ref_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源业务对象类型。允许值：PAYMENT_ATTEMPT、CHANNEL_REFUND、DUPLICATE_ITEM、DISPUTE_NOTICE。',
  `source_ref_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源业务对象 ID（十进制字符串），不建跨库外键。',
  `channel_transaction_ref_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Payment 渠道交易引用的内部 ID，取自事实引用 CHANNEL_TRANSACTION_REF。',
  `reference_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渠道外部交易号的类型，与渠道账单行的 reference_type 对应。取值来源：Payment 渠道交易引用类型目录；非固定枚举。',
  `external_transaction_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渠道外部交易号，匹配主键之一。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '币种。取值来源：ISO 4217 币种代码；非固定枚举。',
  `expected_amount` DECIMAL(24,8) NOT NULL COMMENT '预期由渠道清算的金额，取事实 grossAmount；正数。',
  `expected_fee` DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT 'billing 预估的渠道费，取事实 CHANNEL_COST 行合计；无则为 0。',
  `matched_amount` DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '已被渠道账单行覆盖的金额，不超过 expected_amount。',
  `actual_fee` DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '已匹配账单行确认的实际渠道费累计。',
  `settled_amount` DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '已随账单对平登记或经归类处置结清的金额，不超过 matched_amount 与归类金额之和。',
  `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '逐笔对账进度。允许值：OPEN、PARTIALLY_MATCHED、MATCHED、SETTLED、EXCEPTION。',
  `business_time` DATETIME(6) NOT NULL COMMENT '来源事实的业务发生 UTC 时间，用于账龄与查询。',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '进度与状态更新的乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '登记的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '最后更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_platform_txn_fact` (`billing_fact_id`) COMMENT '保证业务唯一性：billing_fact_id',
  UNIQUE KEY `uk_platform_txn_external` (`clearing_account_id`, `reference_type`, `external_transaction_id`, `direction`) COMMENT '保证业务唯一性：clearing_account_id、reference_type、external_transaction_id、direction',
  KEY `idx_platform_txn_progress` (`clearing_account_id`, `status`, `business_time`) COMMENT '支持访问路径：clearing_account_id、status、business_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='平台流水（内部推送）：由 billing 事实生成的逐笔渠道待清算应收或应付，等待渠道账单行逐笔覆盖；只记进度，不记余额。';

CREATE TABLE `t_channel_statement` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake 主键。',
  `clearing_account_id` BIGINT NOT NULL COMMENT '所属清算账户。',
  `channel_batch_ref` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渠道结算批次号。',
  `revision` INTEGER NOT NULL COMMENT '同一批次号的导入版本，从 1 起；同一批次号只允许一个未作废版本。',
  `settlement_date` DATE NOT NULL COMMENT '渠道结算日。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账单币种，必须等于清算账户币种。取值来源：ISO 4217 币种代码；非固定枚举。',
  `gross_amount` DECIMAL(24,8) NOT NULL COMMENT '账单头总额（带符号：贷记减借记，不含费用行）。',
  `fee_amount` DECIMAL(24,8) NOT NULL COMMENT '账单头费用合计，非负。',
  `net_amount` DECIMAL(24,8) NOT NULL COMMENT '净额绝对值，方向见 cash_direction。',
  `cash_direction` VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '净额相对平台的方向；NONE 表示净额为 0。允许值：CREDIT、DEBIT、NONE。',
  `declared_line_count` INTEGER NOT NULL COMMENT '开始导入时声明的明细行数。',
  `imported_line_count` INTEGER NOT NULL DEFAULT 0 COMMENT '已接收的明细行数。',
  `file_name` VARCHAR(255) NOT NULL COMMENT '运营上传的原文件名，仅用于展示。',
  `file_content_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '浏览器对原文件计算的 SHA-256；不保存原文件。',
  `reject_reason` VARCHAR(1000) NULL COMMENT '整单被拒的汇总原因；逐行原因只在完成导入的响应中返回。',
  `status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账单状态。允许值：RECEIVING、REJECTED、MATCHING、VOIDED、RECONCILED、CASH_CONFIRMED。',
  `cash_confirmed_amount` DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '净额中已经银行确认或归类结清的金额。',
  `reconciled_time` DATETIME(6) NULL COMMENT '对平的 UTC 时间；未对平为空。',
  `maker_id` BIGINT NOT NULL COMMENT '上传人（Platform 账户 ID）。',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '状态与进度更新的乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '开始导入的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '最后更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_statement_rev` (`clearing_account_id`, `channel_batch_ref`, `revision`) COMMENT '保证业务唯一性：clearing_account_id、channel_batch_ref、revision',
  KEY `idx_channel_statement_scope` (`clearing_account_id`, `status`, `settlement_date`) COMMENT '支持访问路径：clearing_account_id、status、settlement_date'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='渠道账单（外部账单）：一个渠道结算批次的一个导入版本；头内容导入后不可变，只推进状态与到账进度。';

CREATE TABLE `t_channel_statement_line` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake 主键。',
  `statement_id` BIGINT NOT NULL COMMENT '所属渠道账单。',
  `clearing_account_id` BIGINT NOT NULL COMMENT '从账单冗余的清算账户，用于跨账单查重与匹配。',
  `line_no` INTEGER NOT NULL COMMENT '文件内行号，从 1 起连续。',
  `line_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '行类型。允许值：PAYMENT、REFUND、CHARGEBACK、FEE、ADJUSTMENT。',
  `direction` VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '相对平台的资金方向。允许值：CREDIT、DEBIT。',
  `amount` DECIMAL(24,8) NOT NULL COMMENT '行金额，非负。',
  `fee_amount` DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '行上直接扣的渠道费，非负。',
  `reference_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '外部交易号类型；ADJUSTMENT 行与不带交易号的 FEE 行为空。取值来源：Payment 渠道交易引用类型目录；非固定枚举。',
  `external_transaction_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '渠道外部交易号；为空规则同 reference_type。',
  `transaction_date` DATE NULL COMMENT '渠道记录的交易日期。',
  `raw_attributes` JSON NULL COMMENT '标准列以外的渠道特有列，原样保存为 JSON Object，最大 4 KiB；不参与匹配。',
  `platform_txn_id` BIGINT NULL COMMENT '匹配到的平台流水；一行最多对一笔。',
  `match_method` VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '匹配方式；未匹配为空。允许值：AUTO、MANUAL。',
  `matched_time` DATETIME(6) NULL COMMENT '匹配的 UTC 时间。',
  `actual_fee` DECIMAL(24,8) NULL COMMENT '确认的实际渠道费（本行 fee_amount 加同交易号 FEE 行）；FEE 行本身记在交易行上，FEE 行此列为空。',
  `resolution_id` BIGINT NULL COMMENT '已归类调整行指向的已应用处置；随账单对平事件带出。',
  `match_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '匹配状态；归类或并入交易行的 FEE 行也记 MATCHED。允许值：UNMATCHED、MATCHED、EXCEPTION。',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '匹配结果更新的乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '接收的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '最后更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_line_no` (`statement_id`, `line_no`) COMMENT '保证业务唯一性：statement_id、line_no',
  KEY `idx_channel_line_ref` (`clearing_account_id`, `reference_type`, `external_transaction_id`) COMMENT '支持访问路径：clearing_account_id、reference_type、external_transaction_id（匹配与跨账单查重）',
  KEY `idx_channel_line_txn` (`platform_txn_id`) COMMENT '支持访问路径：platform_txn_id',
  KEY `idx_channel_line_status` (`clearing_account_id`, `match_status`, `create_time`) COMMENT '支持访问路径：clearing_account_id、match_status、create_time（等待期满任务）'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='渠道账单行：文件内容不可变；匹配到的平台流水、实际渠道费和归类处置记在行上。';

CREATE TABLE `t_cash_account` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake 主键。',
  `entity_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账户所属主体类型。允许值：OPERATING_ENTITY、FINANCE_ENTITY。',
  `entity_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '按主体类型解释：经营主体为 entityNo，财务主体为 ledger 主键十进制字符串；不建跨库外键。',
  `account_purpose` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '资金用途；同主体 + 币种 + 用途只允许一个 ACTIVE；创建后不可改。允许值：CHANNEL_SETTLEMENT、PAYOUT、TAX。',
  `bank_code` VARCHAR(64) NOT NULL COMMENT '开户银行代码。取值来源：运营录入的银行机构代码；非固定枚举。',
  `account_name` VARCHAR(128) NOT NULL COMMENT '账户显示名称。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账户币种。取值来源：ISO 4217 币种代码；非固定枚举。',
  `account_vault_token` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '银行账号的 Vault 令牌（数据类型 CASH_ACCOUNT_NUMBER_V1，REFERENCE 模式）；本库不存明文。',
  `account_last4` CHAR(4) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '银行账号末四位，用于展示与核对对账单。',
  `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账户状态；新建待复核为 PENDING_VERIFICATION。允许值：PENDING_VERIFICATION、ACTIVE、SUSPENDED、CLOSED。',
  `pending_action` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '待复核的变更；为空表示没有待复核的关闭申请（新建待复核由 status 表示）。允许值：CLOSE。',
  `content_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '最近一次提交复核内容的 SHA-256，复核人确认的必须与之相同。',
  `maker_id` BIGINT NOT NULL COMMENT '最近一次提交（新建或关闭）的 Platform 账户 ID。',
  `checker_id` BIGINT NULL COMMENT '最近一次复核人 Platform 账户 ID，必须与 maker_id 不同；未复核为空。',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '状态与复核更新的乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '最后更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  KEY `idx_cash_account_scope` (`entity_type`, `entity_id`, `currency`, `account_purpose`, `status`) COMMENT '支持访问路径：entity_type、entity_id、currency、account_purpose、status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='平台银行账户：账号经 Vault 保护，含资金用途；新建与关闭都经复核。换用途即关闭旧户、开新户。';

CREATE TABLE `t_bank_statement` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake 主键。',
  `cash_account_id` BIGINT NOT NULL COMMENT '所属银行账户。',
  `statement_ref` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '银行对账单编号。',
  `period_start` DATE NOT NULL COMMENT '对账单期间开始日（含）。',
  `period_end` DATE NOT NULL COMMENT '对账单期间结束日（含）。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '币种，必须等于银行账户币种。取值来源：ISO 4217 币种代码；非固定枚举。',
  `opening_balance` DECIMAL(24,8) NOT NULL COMMENT '银行报告的期初余额（带符号）。',
  `closing_balance` DECIMAL(24,8) NOT NULL COMMENT '银行报告的期末余额（带符号），须等于期初加流水。',
  `declared_line_count` INTEGER NOT NULL COMMENT '开始导入时声明的流水行数。',
  `imported_line_count` INTEGER NOT NULL DEFAULT 0 COMMENT '已接收的流水行数。',
  `duplicate_line_count` INTEGER NOT NULL DEFAULT 0 COMMENT '因银行交易号已存在而跳过的行数。',
  `file_name` VARCHAR(255) NOT NULL COMMENT '原文件名，仅用于展示。',
  `file_content_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '浏览器对原文件计算的 SHA-256；不保存原文件。',
  `reject_reason` VARCHAR(1000) NULL COMMENT '整单被拒的汇总原因。',
  `status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '导入状态。允许值：RECEIVING、IMPORTED、REJECTED。',
  `maker_id` BIGINT NOT NULL COMMENT '上传人（Platform 账户 ID）。',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '导入进度更新的乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '开始导入的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '最后更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bank_statement_ref` (`cash_account_id`, `statement_ref`) COMMENT '保证业务唯一性：cash_account_id、statement_ref',
  KEY `idx_bank_statement_period` (`cash_account_id`, `status`, `period_end`) COMMENT '支持访问路径：cash_account_id、status、period_end（连续性检查）'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='银行对账单头：期间与银行报告的期初期末余额，导入时校验期初加流水等于期末。';

CREATE TABLE `t_bank_statement_line` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake 主键。',
  `statement_id` BIGINT NOT NULL COMMENT '所属银行对账单。',
  `cash_account_id` BIGINT NOT NULL COMMENT '从对账单冗余的银行账户，用于跨对账单去重与匹配。',
  `line_no` INTEGER NOT NULL COMMENT '文件内行号。',
  `bank_transaction_ref` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '银行交易号。',
  `value_date` DATE NOT NULL COMMENT '起息日。',
  `direction` VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '相对平台账户的方向。允许值：CREDIT、DEBIT。',
  `amount` DECIMAL(24,8) NOT NULL COMMENT '流水金额，正数。',
  `counterparty_name` VARCHAR(255) NULL COMMENT '银行给出的对方名称。',
  `reference_text` VARCHAR(500) NULL COMMENT '附言或参考号，仅用于人工识别。',
  `matched_amount` DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '已确认匹配按方向带符号的合计（与本流水同向为正），不超过 amount。',
  `match_status` VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '匹配状态；归类结清也记 MATCHED；SUSPENDED 切片 2 起使用。允许值：UNMATCHED、PARTIALLY_MATCHED、MATCHED、EXCEPTION、SUSPENDED。',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '匹配进度更新的乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '接收的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '最后更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bank_line_ref` (`cash_account_id`, `bank_transaction_ref`) COMMENT '保证业务唯一性：cash_account_id、bank_transaction_ref',
  KEY `idx_bank_line_statement` (`statement_id`, `line_no`) COMMENT '支持访问路径：statement_id、line_no',
  KEY `idx_bank_line_open` (`cash_account_id`, `match_status`, `value_date`) COMMENT '支持访问路径：cash_account_id、match_status、value_date（匹配建议）'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='银行流水：内容不可变，只推进匹配进度；同一银行账户的银行交易号只入库一次。';

CREATE TABLE `t_expected_bank_movement` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake 主键。',
  `movement_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '预期来源；切片 1 只产生 CHANNEL_SETTLEMENT。允许值：CHANNEL_SETTLEMENT、PAYOUT、TAX_PAYMENT。',
  `source_id` BIGINT NOT NULL COMMENT '来源 ID：渠道账单 ID / 出款执行 ID / 缴税 ID。',
  `cash_account_id` BIGINT NOT NULL COMMENT '预期进出的银行账户，生成时冻结。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属经营主体。',
  `direction` VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '预期方向。允许值：CREDIT、DEBIT。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '币种。取值来源：ISO 4217 币种代码；非固定枚举。',
  `expected_amount` DECIMAL(24,8) NOT NULL COMMENT '预期金额，正数。',
  `expected_date` DATE NOT NULL COMMENT '预期到账或扣款日；渠道取结算日。',
  `bank_payment_ref` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '出款、缴税的银行付款参考号；渠道为空。',
  `confirmed_amount` DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '已确认银行匹配的金额合计。',
  `classified_amount` DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '经归类处置结清的差额合计（带符号：不足为正，超出为负）。',
  `status` VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '确认进度。允许值：OPEN、PARTIALLY_CONFIRMED、CONFIRMED、CANCELLED、EXCEPTION。',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '进度更新的乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '生成的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '最后更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_expected_movement_source` (`movement_type`, `source_id`) COMMENT '保证业务唯一性：movement_type、source_id',
  KEY `idx_expected_movement_open` (`cash_account_id`, `status`, `expected_date`) COMMENT '支持访问路径：cash_account_id、status、expected_date'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='预期银行流水：银行层要核对的目标（渠道账单净额、出款执行、缴税），由银行匹配逐笔确认。';

CREATE TABLE `t_bank_match` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake 主键。',
  `bank_statement_line_id` BIGINT NOT NULL COMMENT '银行流水。',
  `expected_movement_id` BIGINT NOT NULL COMMENT '预期银行流水，必须与流水同一银行账户。',
  `matched_amount` DECIMAL(24,8) NOT NULL COMMENT '本次分配的金额，正数；对流水的带符号方向由预期流水方向与流水方向是否相同决定。',
  `match_method` VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'AUTO 只表示系统建议，确认均为人工。允许值：AUTO、MANUAL。',
  `payout_component_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '出款流水的组成部分，仅 PAYOUT 使用（切片 3）；其他为空。允许值：BENEFICIARY_TRANSFER、BANK_FEE、COMBINED。',
  `status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '匹配状态；只有 CONFIRMED 产生下游事件。允许值：PROPOSED、CONFIRMED、REJECTED。',
  `resolution_id` BIGINT NULL COMMENT '由 RE_MATCH 处置建立时指向该处置；其他为空。',
  `operator_id` BIGINT NULL COMMENT '确认或拒绝的 Platform 账户 ID；建议未处理时为空。',
  `decision_time` DATETIME(6) NULL COMMENT '确认或拒绝的 UTC 时间。',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '状态更新的乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '建立的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '最后更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  KEY `idx_bank_match_line` (`bank_statement_line_id`, `status`) COMMENT '支持访问路径：bank_statement_line_id、status',
  KEY `idx_bank_match_expected` (`expected_movement_id`, `status`) COMMENT '支持访问路径：expected_movement_id、status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='银行匹配：银行流水与预期银行流水的多对多分配；确认时锁流水，同一流水已确认的带符号合计不超过流水金额。';

CREATE TABLE `t_recon_exception` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake 主键。',
  `exception_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '异常类型。允许值：CHANNEL_LINE_UNMATCHED、AMBIGUOUS_REFERENCE、AMOUNT_MISMATCH、DUPLICATE_SETTLEMENT、UNEXPLAINED_ADJUSTMENT、COST_DEVIATION、PLATFORM_ITEM_UNSETTLED、BANK_LINE_UNMATCHED、BANK_AMOUNT_MISMATCH、BANK_MOVEMENT_OVERDUE、BANK_STATEMENT_GAP、LATE_DEBIT。',
  `recon_layer` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属对账层。允许值：TRANSACTION、BANK。',
  `blocking` TINYINT NOT NULL COMMENT '1 表示阻塞所属渠道账单对平，0 表示不阻塞（PLATFORM_ITEM_UNSETTLED、BANK_MOVEMENT_OVERDUE、BANK_STATEMENT_GAP 及银行层异常为 0）。',
  `source_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '异常挂靠的本库对象类型，决定可用处置与归类码。允许值：CHANNEL_STATEMENT、CHANNEL_STATEMENT_LINE、PLATFORM_TXN、BANK_STATEMENT、BANK_STATEMENT_LINE、EXPECTED_BANK_MOVEMENT。',
  `source_id` BIGINT NOT NULL COMMENT '挂靠对象的本库 ID。',
  `statement_id` BIGINT NULL COMMENT '交易层异常所属的渠道账单，用于对平门禁查询；无关时为空。',
  `clearing_account_id` BIGINT NULL COMMENT '由来源父链确定的清算账户；银行层异常为空。',
  `cash_account_id` BIGINT NULL COMMENT '由来源父链确定的银行账户；交易层异常为空。',
  `entity_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '由来源父链确定的主体类型，不得人工改挂。允许值：OPERATING_ENTITY、FINANCE_ENTITY。',
  `entity_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '由来源父链确定的主体标识：经营主体为 entityNo，财务主体为 ledger 主键十进制字符串。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '币种。取值来源：ISO 4217 币种代码；非固定枚举。',
  `difference_amount` DECIMAL(24,8) NOT NULL COMMENT '系统算出的待处置差异（带符号）；归类处置金额必须与之相等。',
  `detail` VARCHAR(1000) NULL COMMENT '系统生成的说明，如命中的多条平台流水 ID、重复的账单行。',
  `status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '异常状态。允许值：OPEN、UNDER_REVIEW、RESOLVED、AUTO_CLOSED。',
  `active_slot` TINYINT NULL COMMENT 'OPEN、UNDER_REVIEW 时为 1，RESOLVED、AUTO_CLOSED 时为 NULL；保证同一对象同一类型只有一个未结异常。',
  `opened_by` BIGINT NULL COMMENT '人工开异常的 Platform 账户 ID；系统开的为空。',
  `opened_time` DATETIME(6) NOT NULL COMMENT '开立的 UTC 时间，用于账龄。',
  `closed_time` DATETIME(6) NULL COMMENT '结案的 UTC 时间。',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '状态更新的乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '最后更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recon_exception_active` (`source_type`, `source_id`, `exception_type`, `active_slot`) COMMENT '保证业务唯一性：source_type、source_id、exception_type、active_slot',
  KEY `idx_recon_exception_age` (`status`, `opened_time`) COMMENT '支持访问路径：status、opened_time',
  KEY `idx_recon_exception_statement` (`statement_id`, `status`) COMMENT '支持访问路径：statement_id、status（对平门禁）',
  KEY `idx_recon_exception_clearing` (`clearing_account_id`, `status`) COMMENT '支持访问路径：clearing_account_id、status',
  KEY `idx_recon_exception_cash` (`cash_account_id`, `status`) COMMENT '支持访问路径：cash_account_id、status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='对账异常：对不上的项，挂在本库某个对象上，经人工处置结案；只显示账龄，不做分派。';

CREATE TABLE `t_recon_resolution` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake 主键。',
  `exception_id` BIGINT NOT NULL COMMENT '所属对账异常。',
  `resolution_no` INTEGER NOT NULL COMMENT '同一异常内从 1 起递增的处置序号。',
  `resolution_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '处置方式。允许值：RE_MATCH、ACCEPT_TIMING_DIFFERENCE、CLASSIFY。',
  `classification_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '归类码，仅 CLASSIFY 非空，可用范围由异常来源对象决定。允许值：CHANNEL_FEE_OTHER、CHANNEL_RESERVE_HOLD、CHANNEL_RESERVE_RELEASE、BANK_FEE、BANK_INTEREST、INTERNAL_TRANSFER、UNALLOCATED_RECEIPT、PAYOUT_RETURN_PENDING、RECON_DIFFERENCE。',
  `amount` DECIMAL(24,8) NULL COMMENT '归类金额（带符号），仅 CLASSIFY 非空且等于异常差异金额。',
  `rematch_target_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '重新匹配的目标类型，仅 RE_MATCH 非空。允许值：PLATFORM_TXN、EXPECTED_BANK_MOVEMENT。',
  `rematch_target_id` BIGINT NULL COMMENT '重新匹配的目标 ID，仅 RE_MATCH 非空。',
  `rematch_amount` DECIMAL(24,8) NULL COMMENT '银行层重新匹配的分配金额，仅银行层 RE_MATCH 非空。',
  `note` VARCHAR(1000) NOT NULL COMMENT '提交人填写的处置说明。',
  `content_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '异常 ID、处置类型与全部输入的 SHA-256；提交后不变。',
  `maker_id` BIGINT NOT NULL COMMENT '提交人 Platform 账户 ID。',
  `checker_id` BIGINT NULL COMMENT '复核人 Platform 账户 ID，必须与提交人不同。',
  `checker_decision` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '复核决定。允许值：APPROVED、REJECTED。',
  `approved_content_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '复核人确认的内容 Hash，必须等于 content_hash。',
  `decision_time` DATETIME(6) NULL COMMENT '复核的 UTC 时间。',
  `status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '处置状态；批准即在同一事务应用为 APPLIED。允许值：PENDING_CHECK、REJECTED、APPLIED。',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '复核更新的乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '提交的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '最后更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recon_resolution_no` (`exception_id`, `resolution_no`) COMMENT '保证业务唯一性：exception_id、resolution_no',
  KEY `idx_recon_resolution_status` (`status`, `create_time`) COMMENT '支持访问路径：status、create_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='异常处置：Maker 提交、Checker 复核，批准即应用；归类处置按来源对象经 billing 入账。';

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
