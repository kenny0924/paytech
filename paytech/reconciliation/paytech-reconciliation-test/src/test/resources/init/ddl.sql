-- reconciliation-service Mock 测试环境准备（管理员手工执行；应用与测试从不自动执行）。
-- 测试复用仓库 local-environment 的 MySQL；账号与 application-mock.yml 一致，只授予本库最小 DML 权限（design/10 §4.2）。
-- 已有库和账号不删除、不重置。表结构逐字取自 paytech/reconciliation/docs/database/sql/reconciliation-schema.sql 的全部 18 张表。
CREATE DATABASE IF NOT EXISTS mor_reconciliation DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'reconciliation_app'@'%' IDENTIFIED BY '2ddccce1369869710096b620a2c04a7cbcee8f4e41160f4a';
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_reconciliation.* TO 'reconciliation_app'@'%';
USE mor_reconciliation;
SET NAMES utf8mb4;

CREATE TABLE `t_channel_cost_confirm` (
  `id` BIGINT NOT NULL COMMENT '服务侧生成的渠道实际成本确认 Snowflake 主键。',
  `channel_settle_item_id` BIGINT NOT NULL COMMENT '唯一承载该实际成本并可追溯批次、渠道账户、MID、经营主体和币种的本库清算项目。',
  `actual_cost` DECIMAL(24,8) NOT NULL COMMENT 'Channel 清算确认的非负实际成本金额。',
  `source_hash` CHAR(64) NOT NULL COMMENT 'Channel 原始清算成本项目规范化内容的不可变摘要。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_cost_item` (`channel_settle_item_id`) COMMENT '保证业务唯一性：channel_settle_item_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='以本库渠道清算项目为唯一锚点冻结渠道实际成本及来源内容摘要。';

CREATE TABLE `t_channel_cost_alloc` (
  `id` BIGINT NOT NULL COMMENT '服务侧生成的渠道实际成本分配 Snowflake 主键。',
  `cost_confirm_id` BIGINT NOT NULL COMMENT '该分配所属的本库渠道实际成本确认。',
  `allocation_no` INT NOT NULL COMMENT '同一实际成本确认内稳定且唯一的分配序号。',
  `payment_attempt_id` BIGINT NULL COMMENT '支付类成本对应的 Payment Attempt 稳定业务ID。',
  `channel_refund_id` BIGINT NULL COMMENT '退款类成本对应的 Payment Channel Refund 稳定业务ID。',
  `channel_dispute_notice_id` BIGINT NULL COMMENT '争议类成本对应的 Payment Channel Dispute Notice 稳定业务ID。',
  `dispute_case_id` BIGINT NULL COMMENT '争议类成本对应的 Risk Dispute Case 稳定业务ID。',
  `transaction_economics_id` BIGINT NOT NULL COMMENT '接收实际成本或差额调整的本库交易经济结果。',
  `allocated_amount` DECIMAL(24,8) NOT NULL COMMENT '本行分配到目标业务事实的非负实际成本金额。',
  `rounding_amount` DECIMAL(24,8) NOT NULL COMMENT '为使全部分配精确等于实际成本而显式记录的确定性尾差。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cost_alloc` (`cost_confirm_id`, `allocation_no`) COMMENT '保证业务唯一性：cost_confirm_id、allocation_no',
  KEY `idx_cost_alloc_payment` (`payment_attempt_id`) COMMENT '支持访问路径：payment_attempt_id',
  KEY `idx_cost_alloc_refund` (`channel_refund_id`) COMMENT '支持访问路径：channel_refund_id',
  KEY `idx_cost_alloc_dispute` (`dispute_case_id`) COMMENT '支持访问路径：dispute_case_id',
  KEY `idx_cost_alloc_econ` (`transaction_economics_id`) COMMENT '支持访问路径：transaction_economics_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='把渠道实际成本逐行分配到成功支付、渠道退款或争议及其匹配交易经济结果。';

CREATE TABLE `t_channel_account` (
  `id` BIGINT NOT NULL COMMENT 'Finance 渠道清算账户的 Snowflake 主键。',
  `channel_id` BIGINT NOT NULL COMMENT 'Payment 拥有的外部渠道稳定标识，不建立跨库外键。',
  `mid_id` BIGINT NOT NULL COMMENT 'Payment 拥有的渠道商户号稳定标识，不建立跨库外键。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '该清算账户所属经营主体的外部稳定标识。',
  `currency` CHAR(3) NOT NULL COMMENT 'ISO 4217 三位币种代码。取值来源：ISO 4217 币种代码；非固定枚举。',
  `status` VARCHAR(64) NOT NULL COMMENT '渠道清算账户当前可用状态。允许值：ACTIVE、INACTIVE。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_acct` (`channel_id`, `mid_id`, `entity_no`, `currency`) COMMENT '保证业务唯一性：channel_id、mid_id、entity_no、currency',
  UNIQUE KEY `uk_channel_mid_currency` (`mid_id`, `currency`) COMMENT '保证业务唯一性：mid_id、currency',
  KEY `idx_channel_acct_oe` (`entity_no`, `currency`, `status`) COMMENT '支持访问路径：entity_no、currency、status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='记录单一经营主体、渠道商户号和币种下的渠道清算账户边界，与 Payment 技术接口账户相互独立';

CREATE TABLE `t_channel_open_item` (
  `id` BIGINT NOT NULL COMMENT '逐笔渠道应收的 Snowflake 主键。',
  `channel_account_id` BIGINT NOT NULL COMMENT '该应收归属的 Finance 渠道清算账户。',
  `item_type` VARCHAR(64) NOT NULL COMMENT '区分正常业务支付与真实重复扣款。允许值：PAYMENT、DUPLICATE_CAPTURE。',
  `payment_intent_id` BIGINT NULL COMMENT '正常支付对应的 Payment 支付意图稳定标识。',
  `payment_attempt_id` BIGINT NULL COMMENT '正常支付对应的 Payment 成功尝试稳定标识。',
  `duplicate_capture_item_id` BIGINT NULL COMMENT '重复扣款类型应收对应的本库异常明细。',
  `channel_transaction_ref_id` BIGINT NOT NULL COMMENT 'Payment 冻结并返回的渠道交易引用稳定标识。',
  `reference_type` VARCHAR(64) NOT NULL COMMENT '解释外部交易编号的渠道引用类型。取值来源：Payment 渠道交易引用类型目录；非固定枚举。',
  `external_transaction_id` VARCHAR(255) NOT NULL COMMENT '按引用类型解释且与 Payment 契约等长的渠道原始交易编号。',
  `transaction_economics_id` BIGINT NOT NULL COMMENT '形成该应收的 Finance 不可变交易经济结果。',
  `gross_receivable` DECIMAL(24,8) NOT NULL COMMENT '该渠道应收的原始总金额。',
  `settled_amount` DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '已由完整 Channel Batch 第二阶段 Journal 从 CHANNEL_UNSETTLED_RECEIVABLE 重分类覆盖的累计金额；原始 Transaction Match 单独不推进本字段。',
  `outstanding_amount` DECIMAL(24,8) NOT NULL COMMENT '当前尚未由完整 Channel Batch 第二阶段 Journal 重分类覆盖的金额。',
  `status` VARCHAR(64) NOT NULL COMMENT '渠道应收的批次重分类状态；SETTLED 表示已经由完整 Channel Batch Journal 清出待清算账户，不代表 Bank Statement 已确认到账。允许值：OPEN、PARTIALLY_SETTLED、SETTLED、EXCEPTION。',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT '聚合根条件更新使用的乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_txn_ref` (`channel_account_id`, `channel_transaction_ref_id`) COMMENT '保证业务唯一性：channel_account_id、channel_transaction_ref_id',
  UNIQUE KEY `uk_channel_external` (`channel_account_id`, `reference_type`, `external_transaction_id`) COMMENT '保证业务唯一性：channel_account_id、reference_type、external_transaction_id',
  UNIQUE KEY `uk_open_duplicate` (`duplicate_capture_item_id`) COMMENT '保证业务唯一性：duplicate_capture_item_id',
  KEY `idx_channel_attempt` (`channel_account_id`, `payment_attempt_id`) COMMENT '支持访问路径：channel_account_id、payment_attempt_id',
  KEY `idx_channel_open_econ` (`transaction_economics_id`) COMMENT '支持访问路径：transaction_economics_id',
  KEY `idx_channel_item_status` (`channel_account_id`, `status`) COMMENT '支持访问路径：channel_account_id、status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='记录正常支付或真实重复扣款形成的逐笔渠道待清算应收及其未完成 Channel 交易对账余额；来源 Journal 记入 CHANNEL_UNSETTLED_RECEIVABLE，完整交易对账后的批次净额才聚合重分类到 CHANNEL_SETTLEMENT_RECEIVABLE';

CREATE TABLE `t_channel_debit_item` (
  `id` BIGINT NOT NULL COMMENT '逐笔渠道扣款清算事实的 Snowflake 主键。',
  `channel_account_id` BIGINT NOT NULL COMMENT '该扣款事实归属的 Finance 渠道清算账户。',
  `debit_type` VARCHAR(64) NOT NULL COMMENT '区分 Client 退款、重复扣款补偿退款与争议或拒付扣款。允许值：REFUND、DUPLICATE_COMPENSATION、DISPUTE。',
  `channel_refund_id` BIGINT NULL COMMENT '退款扣款对应的 Payment Channel Refund 稳定标识。',
  `duplicate_capture_item_id` BIGINT NULL COMMENT '重复扣款补偿退款对应的本库重复扣款异常明细。',
  `channel_dispute_notice_id` BIGINT NULL COMMENT '争议扣款对应的 Payment Channel Dispute Notice 稳定标识。',
  `dispute_case_id` BIGINT NULL COMMENT '争议扣款对应的 Risk Dispute Case 稳定标识。',
  `channel_transaction_ref_id` BIGINT NOT NULL COMMENT 'Payment 冻结并返回的退款或争议渠道交易引用标识。',
  `channel_debit_id` VARCHAR(128) NOT NULL COMMENT '渠道返回的稳定退款或争议扣款业务编号。',
  `reference_type` VARCHAR(64) NOT NULL COMMENT '解释外部扣款交易编号的渠道引用类型。取值来源：Payment 渠道扣款交易引用类型目录；非固定枚举。',
  `external_transaction_id` VARCHAR(255) NOT NULL COMMENT '按引用类型解释且与 Payment 契约等长的渠道原始扣款交易编号。',
  `transaction_economics_id` BIGINT NOT NULL COMMENT '形成该退款或争议扣款的 Finance 不可变交易经济结果。',
  `expected_amount` DECIMAL(24,8) NOT NULL COMMENT 'Finance 预期由渠道清算扣减的正金额。',
  `settled_amount` DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '已由完整 Channel Batch 第二阶段 Journal 从 CHANNEL_UNSETTLED_PAYABLE 重分类覆盖的累计扣款金额；原始 Transaction Match 单独不推进本字段。',
  `outstanding_amount` DECIMAL(24,8) NOT NULL COMMENT '尚未由完整 Channel Batch 第二阶段 Journal 重分类覆盖的扣款金额。',
  `status` VARCHAR(64) NOT NULL COMMENT '渠道扣款的批次重分类状态；SETTLED 表示已经由完整 Channel Batch Journal 清出待清算账户，不代表 Bank Statement Debit 已确认。允许值：OPEN、PARTIALLY_SETTLED、SETTLED、EXCEPTION。',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT '聚合根条件更新使用的乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_debit_ref` (`channel_account_id`, `channel_transaction_ref_id`) COMMENT '保证业务唯一性：channel_account_id、channel_transaction_ref_id',
  UNIQUE KEY `uk_channel_debit_id` (`channel_account_id`, `channel_debit_id`) COMMENT '保证业务唯一性：channel_account_id、channel_debit_id',
  UNIQUE KEY `uk_debit_channel_refund` (`channel_refund_id`) COMMENT '保证业务唯一性：channel_refund_id',
  UNIQUE KEY `uk_debit_duplicate` (`duplicate_capture_item_id`) COMMENT '保证业务唯一性：duplicate_capture_item_id',
  UNIQUE KEY `uk_debit_dispute_notice` (`channel_dispute_notice_id`) COMMENT '保证业务唯一性：channel_dispute_notice_id',
  UNIQUE KEY `uk_channel_debit_econ` (`transaction_economics_id`) COMMENT '保证业务唯一性：transaction_economics_id',
  KEY `idx_channel_debit_st` (`channel_account_id`, `debit_type`, `status`) COMMENT '支持访问路径：channel_account_id、debit_type、status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='记录成功 Client 退款、重复扣款补偿退款或争议扣款形成的逐笔渠道待清算应付事实，并冻结准确渠道引用；来源 Journal 记入 CHANNEL_UNSETTLED_PAYABLE，完整交易对账后的批次净额才聚合重分类到 CHANNEL_SETTLEMENT_PAYABLE';

CREATE TABLE `t_duplicate_capture_item` (
  `id` BIGINT NOT NULL COMMENT '重复扣款异常事实的 Snowflake 主键。',
  `duplicate_capture_case_id` BIGINT NOT NULL COMMENT 'Payment 拥有的重复扣款案件稳定标识。',
  `payment_intent_id` BIGINT NOT NULL COMMENT '发生真实重复扣款的 Payment 支付意图标识。',
  `conflicting_attempt_id` BIGINT NOT NULL COMMENT '第二笔真实成功的 Payment 支付尝试标识。',
  `channel_account_id` BIGINT NOT NULL COMMENT '重复扣款发生的 Finance 渠道清算账户。',
  `channel_transaction_ref_id` BIGINT NOT NULL COMMENT '重复扣款对应的 Payment 渠道交易引用标识。',
  `external_transaction_id` VARCHAR(255) NOT NULL COMMENT '重复扣款对应且与 Payment 契约等长的渠道原始交易编号。',
  `amount` DECIMAL(24,8) NOT NULL COMMENT '渠道实际重复扣取且必须大于零的金额。',
  `currency` CHAR(3) NOT NULL COMMENT 'ISO 4217 三位币种代码。取值来源：ISO 4217 币种代码；非固定枚举。',
  `source_system` VARCHAR(64) NOT NULL COMMENT '发布重复扣款事实的权威系统代码。允许值：PAYMENT。',
  `source_event_key` VARCHAR(128) NOT NULL COMMENT 'Payment 重复扣款事实的全局稳定事件键。',
  `source_version` BIGINT NOT NULL COMMENT '来源重复扣款事实的业务版本。',
  `source_payload_hash` CHAR(64) NOT NULL COMMENT '来源事件 Canonical Payload 的 SHA-256 哈希。',
  `remediation_status` VARCHAR(64) NOT NULL COMMENT '重复扣款补偿与对账的当前状态。允许值：OPEN、COMPENSATION_PENDING、COMPENSATED、LOSS_ACCEPTED、RESOLVED。',
  `journal_entry_id` BIGINT NOT NULL COMMENT '确认重复扣款应收与消费者补偿义务的本库会计分录。',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT '聚合根条件更新使用的乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dup_capture_case` (`duplicate_capture_case_id`) COMMENT '保证业务唯一性：duplicate_capture_case_id',
  UNIQUE KEY `uk_dup_capture_attempt` (`conflicting_attempt_id`) COMMENT '保证业务唯一性：conflicting_attempt_id',
  UNIQUE KEY `uk_dup_capture_event` (`source_system`, `source_event_key`) COMMENT '保证业务唯一性：source_system、source_event_key',
  UNIQUE KEY `uk_dup_capture_journal` (`journal_entry_id`) COMMENT '保证业务唯一性：journal_entry_id',
  KEY `idx_dup_capture_acct` (`channel_account_id`, `remediation_status`) COMMENT '支持访问路径：channel_account_id、remediation_status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='记录第二笔真实成功但不属于业务成功的渠道扣款及其财务补偿处置状态';

CREATE TABLE `t_channel_settle_batch` (
  `id` BIGINT NOT NULL COMMENT '渠道清算批次的 Snowflake 主键。',
  `channel_account_id` BIGINT NOT NULL COMMENT '该批次归属的 Finance 渠道清算账户。',
  `channel_batch_ref` VARCHAR(128) NOT NULL COMMENT '渠道提供的原始清算批次编号。',
  `settlement_date` DATE NOT NULL COMMENT 'Channel 最终 Settlement 或 Remittance 明细声明的业务清算日期，不得使用预计日期。',
  `currency` CHAR(3) NOT NULL COMMENT 'ISO 4217 三位币种代码。取值来源：ISO 4217 币种代码；非固定枚举。',
  `gross_amount` DECIMAL(24,8) NOT NULL COMMENT '批次内支付和重复扣款等渠道应付给平台的正向清算总金额。',
  `fee_amount` DECIMAL(24,8) NOT NULL COMMENT '批次内渠道扣收的费用金额。',
  `debit_amount` DECIMAL(24,8) NOT NULL COMMENT '批次内退款、争议和其他渠道从平台扣减的正向绝对金额。',
  `net_amount` DECIMAL(24,8) NOT NULL COMMENT '贷项总额减费用和扣项后的单一净现金腿绝对金额；零净额时为零。',
  `cash_direction` VARCHAR(64) NOT NULL COMMENT '单一净现金腿相对于平台银行账户的方向。允许值：CREDIT、DEBIT、NONE。',
  `file_id` BIGINT NOT NULL COMMENT 'Platform 保存的 Channel 最终 Settlement 或 Remittance 原始文件稳定标识。',
  `file_content_hash` CHAR(64) NOT NULL COMMENT '与 Platform 文件内容哈希逐值一致的 SHA-256 值。',
  `status` VARCHAR(64) NOT NULL COMMENT '不可变批次在接收时确认的校验结果；VALIDATED 单独不能触发账户重分类，交易对账完整且费用、调整、实际成本和阻塞异常门禁全部通过后，才按批次净额幂等创建聚合 Journal。允许值：VALIDATED、REJECTED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_batch` (`channel_account_id`, `channel_batch_ref`) COMMENT '保证业务唯一性：channel_account_id、channel_batch_ref',
  KEY `idx_channel_batch_recon` (`channel_account_id`, `status`, `cash_direction`, `settlement_date`) COMMENT '支持访问路径：channel_account_id、status、cash_direction、settlement_date',
  KEY `idx_channel_batch_file` (`file_id`, `file_content_hash`) COMMENT '支持访问路径：file_id、file_content_hash'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='冻结 Channel 权威最终 Settlement 或 Remittance 批次头、金额和 Platform 原始文件二元组；Preliminary Transaction Report 不能冒充本批次；VALIDATED 只表示导入结构与金额校验通过，单独不能触发账户重分类；只有业务行逐笔交易对账完整、费用与调整处置完整、实际成本确认完整且无阻塞异常，才可按批次净额幂等创建聚合 Journal，把 Channel 待清算应收或应付重分类为 Channel 结算应收或应付并清偿 CHANNEL_FEE_ACCRUAL，不得重复确认 Channel Fee Expense，并使该批次成为银行对账目标';

CREATE TABLE `t_channel_settle_item` (
  `id` BIGINT NOT NULL COMMENT '渠道清算原始行的 Snowflake 主键。',
  `batch_id` BIGINT NOT NULL COMMENT '原始行所属的渠道清算批次。',
  `item_no` INTEGER NOT NULL COMMENT '渠道清算批次内从一开始连续递增的行号。',
  `channel_item_ref` VARCHAR(128) NOT NULL COMMENT '渠道在原始文件中提供的稳定行引用。',
  `item_type` VARCHAR(64) NOT NULL COMMENT '渠道原始行代表的交易、退款、争议或费用类型。允许值：PAYMENT、REFUND、DISPUTE、DUPLICATE_CAPTURE、FEE、ADJUSTMENT。',
  `settlement_direction` VARCHAR(64) NOT NULL COMMENT '该原始行对平台渠道净清算余额的增加或扣减方向。允许值：CREDIT、DEBIT。',
  `reference_type` VARCHAR(64) NOT NULL COMMENT '解释外部交易编号的渠道引用类型。取值来源：渠道清算文件引用类型目录；非固定枚举。',
  `external_transaction_id` VARCHAR(255) NOT NULL COMMENT '渠道清算文件冻结且与 Payment 契约等长的原始交易编号。',
  `gross_amount` DECIMAL(24,8) NOT NULL COMMENT '原始清算行的总金额。',
  `fee_amount` DECIMAL(24,8) NOT NULL COMMENT '原始清算行的渠道费用金额。',
  `net_amount` DECIMAL(24,8) NOT NULL COMMENT '原始清算行的净金额。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settle_item` (`batch_id`, `item_no`) COMMENT '保证业务唯一性：batch_id、item_no',
  UNIQUE KEY `uk_channel_item_ref` (`batch_id`, `channel_item_ref`) COMMENT '保证业务唯一性：batch_id、channel_item_ref',
  KEY `idx_settle_item_ref` (`batch_id`, `reference_type`, `external_transaction_id`) COMMENT '支持访问路径：batch_id、reference_type、external_transaction_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='逐行冻结渠道清算文件中的业务引用和金额，作为后续分配与对账的原始事实';

CREATE TABLE `t_channel_transaction_match` (
  `id` BIGINT NOT NULL COMMENT '渠道交易对账匹配事实的 Snowflake 主键。',
  `settle_item_id` BIGINT NOT NULL COMMENT '被匹配的渠道清算原始行。',
  `target_type` VARCHAR(64) NOT NULL COMMENT '匹配目标为渠道应收或渠道扣款。允许值：OPEN_ITEM、DEBIT_ITEM。',
  `target_key` VARCHAR(128) NOT NULL COMMENT '按目标类型和标识生成的非空 Canonical 键。',
  `open_item_id` BIGINT NULL COMMENT '目标为正常支付或重复扣款应收时引用的本库明细。',
  `debit_item_id` BIGINT NULL COMMENT '目标为退款或争议扣款时引用的本库明细。',
  `allocation_no` INTEGER NOT NULL COMMENT '同一清算行及目标内从一开始递增的匹配分配序号。',
  `gross_amount` DECIMAL(24,8) NOT NULL COMMENT '本次匹配覆盖的总金额。',
  `fee_amount` DECIMAL(24,8) NOT NULL COMMENT '本次匹配确认的渠道费用金额。',
  `net_amount` DECIMAL(24,8) NOT NULL COMMENT '本次匹配覆盖的净金额。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_transaction_match` (`settle_item_id`, `target_type`, `target_key`, `allocation_no`) COMMENT '保证业务唯一性：settle_item_id、target_type、target_key、allocation_no',
  KEY `idx_channel_txn_open` (`open_item_id`) COMMENT '支持访问路径：open_item_id',
  KEY `idx_channel_txn_debit` (`debit_item_id`) COMMENT '支持访问路径：debit_item_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='仅使用 Finance 本地冻结引用完成逐笔 Channel 交易对账，把 PAYMENT、DUPLICATE_CAPTURE、REFUND、DISPUTE 原始行不可变地匹配到应收或扣款明细，正常流程无需在线查询 Payment；本匹配不代表 Channel 银行收付款已经确认，多条匹配可共同支持一个批次级重分类 Journal；FEE、ADJUSTMENT 不创建本表记录，改由 Cost Confirm/Alloc 或 Recon Exception 完整处置';

CREATE TABLE `t_cash_account` (
  `id` BIGINT NOT NULL COMMENT 'Finance 平台外部银行账户的 Snowflake 主键。',
  `entity_type` VARCHAR(64) NOT NULL COMMENT '区分该银行账户所属的 CCT Operating Entity 或 Finance 本地内部财务主体。允许值：OPERATING_ENTITY、FINANCE_ENTITY。',
  `entity_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '按主体类型解释的稳定业务标识；Finance Entity 使用本地主键且不建立多态外键。',
  `bank_code` VARCHAR(64) NOT NULL COMMENT '开户银行的受控机构代码。取值来源：Finance 银行机构目录；非固定枚举。',
  `currency` CHAR(3) NOT NULL COMMENT 'ISO 4217 三位币种代码。取值来源：ISO 4217 币种代码；非固定枚举。',
  `account_vault_token` VARCHAR(128) NOT NULL COMMENT 'Finance 专属且不透明的中央 Vault 值令牌。',
  `account_blind_index` CHAR(64) NOT NULL COMMENT '按财务主体、字段和用途隔离生成的银行账号等值检索摘要。',
  `account_blind_index_key_version` INTEGER NOT NULL COMMENT '生成银行账号盲索引所用密钥版本。',
  `account_last4` CHAR(4) NOT NULL COMMENT '经授权可展示的银行账号末四位。',
  `vault_record_version` BIGINT NOT NULL COMMENT '写入本行时中央 Vault 记录的并发版本。',
  `vault_metadata_hash` CHAR(64) NOT NULL COMMENT '中央 Vault 记录元数据的内容承诺哈希。',
  `status` VARCHAR(64) NOT NULL COMMENT '平台外部银行账户当前受控状态。允许值：PENDING_VERIFICATION、ACTIVE、SUSPENDED、CLOSED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cash_blind_index` (`entity_type`, `entity_id`, `account_blind_index_key_version`, `account_blind_index`) COMMENT '保证业务唯一性：entity_type、entity_id、account_blind_index_key_version、account_blind_index',
  KEY `idx_cash_account_scope` (`entity_type`, `entity_id`, `currency`, `status`) COMMENT '支持访问路径：entity_type、entity_id、currency、status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存单一 Operating Entity 或 Finance Entity 和币种下平台资产银行账户的受控 Vault 引用与状态';

CREATE TABLE `t_cash_account_binding` (
  `id` BIGINT NOT NULL COMMENT '银行账户用途绑定聚合根的 Snowflake 主键。',
  `entity_type` VARCHAR(64) NOT NULL COMMENT '区分该用途绑定所属的 CCT Operating Entity 或 Finance 本地内部财务主体。允许值：OPERATING_ENTITY、FINANCE_ENTITY。',
  `entity_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '按主体类型解释的稳定业务标识；不建立多态外键。',
  `currency` CHAR(3) NOT NULL COMMENT 'ISO 4217 三位币种代码。取值来源：ISO 4217 币种代码；非固定枚举。',
  `account_purpose` VARCHAR(64) NOT NULL COMMENT '允许值：CHANNEL_SETTLEMENT、PAYOUT、TAX。该银行账户绑定允许承接的资金用途；退款通过渠道扣款明细进入 Channel 净清算，不是独立银行用途。允许值：CHANNEL_SETTLEMENT、PAYOUT、TAX。',
  `current_ver_id` BIGINT NULL COMMENT '当前生效的本库不可变银行账户绑定版本。',
  `status` VARCHAR(64) NOT NULL COMMENT '银行账户用途绑定当前状态。允许值：DRAFT、ACTIVE、SUSPENDED、CLOSED。',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT '聚合根条件更新使用的乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cash_binding` (`entity_type`, `entity_id`, `currency`, `account_purpose`) COMMENT '保证业务唯一性：entity_type、entity_id、currency、account_purpose',
  UNIQUE KEY `uk_cash_binding_current` (`current_ver_id`) COMMENT '保证业务唯一性：current_ver_id',
  KEY `idx_cash_binding_status` (`entity_type`, `entity_id`, `status`) COMMENT '支持访问路径：entity_type、entity_id、status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='为 Operating Entity 或 Finance Entity、币种和资金用途维护唯一当前银行账户绑定槽';

CREATE TABLE `t_cash_account_binding_ver` (
  `id` BIGINT NOT NULL COMMENT '不可变银行账户用途绑定版本的 Snowflake 主键。',
  `binding_id` BIGINT NOT NULL COMMENT '该版本所属的稳定银行账户用途绑定聚合根。',
  `binding_ver` BIGINT NOT NULL COMMENT '同一用途绑定内从一开始连续递增的业务版本号。',
  `cash_account_id` BIGINT NOT NULL COMMENT '该版本冻结的平台外部银行账户。',
  `effective_from` DATETIME(6) NOT NULL COMMENT '该绑定版本开始生效的 UTC 微秒时间。',
  `effective_to` DATETIME(6) NULL COMMENT '该绑定版本停止生效的 UTC 微秒时间。',
  `status` VARCHAR(64) NOT NULL COMMENT '不可变绑定版本的审批和生效状态。允许值：DRAFT、APPROVED、ACTIVE、EXPIRED、REVOKED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cash_binding_ver` (`binding_id`, `binding_ver`) COMMENT '保证业务唯一性：binding_id、binding_ver',
  KEY `idx_cash_bind_ver_acct` (`cash_account_id`, `status`) COMMENT '支持访问路径：cash_account_id、status',
  KEY `idx_cash_bind_ver_time` (`binding_id`, `effective_from`, `effective_to`) COMMENT '支持访问路径：binding_id、effective_from、effective_to'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='不可变地冻结某一用途绑定在指定有效期内使用的银行账户，供历史 Payout Execution 与 Bank Recon Match 继续引用；引用的 Cash Account 必须与父 Binding 主体和币种一致';

CREATE TABLE `t_bank_statement` (
  `id` BIGINT NOT NULL COMMENT '银行对账单的 Snowflake 主键。',
  `cash_account_id` BIGINT NOT NULL COMMENT '该对账单归属的平台外部银行账户。',
  `statement_ref` VARCHAR(128) NOT NULL COMMENT '银行提供的稳定对账单编号。',
  `statement_date` DATE NOT NULL COMMENT '银行对账单覆盖的业务日期。',
  `currency` CHAR(3) NOT NULL COMMENT 'ISO 4217 三位币种代码。取值来源：ISO 4217 币种代码；非固定枚举。',
  `opening_balance` DECIMAL(24,8) NOT NULL COMMENT '对账单报告的账户期初余额。',
  `closing_balance` DECIMAL(24,8) NOT NULL COMMENT '对账单报告的账户期末余额。',
  `file_id` BIGINT NOT NULL COMMENT 'Platform 保存的银行原始对账单文件稳定标识。',
  `file_content_hash` CHAR(64) NOT NULL COMMENT '与 Platform 文件内容哈希逐值一致的 SHA-256 值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bank_statement` (`cash_account_id`, `statement_ref`) COMMENT '保证业务唯一性：cash_account_id、statement_ref',
  KEY `idx_bank_stmt_date` (`cash_account_id`, `statement_date`) COMMENT '支持访问路径：cash_account_id、statement_date',
  KEY `idx_bank_stmt_file` (`file_id`, `file_content_hash`) COMMENT '支持访问路径：file_id、file_content_hash'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='冻结指定平台银行账户的对账单头、余额及 Platform 原始文件二元组';

CREATE TABLE `t_bank_statement_item` (
  `id` BIGINT NOT NULL COMMENT '银行流水明细的 Snowflake 主键。',
  `statement_id` BIGINT NOT NULL COMMENT '该流水所属的银行对账单。',
  `cash_account_id` BIGINT NOT NULL COMMENT '冗余冻结且必须与对账单一致的平台银行账户。',
  `line_no` INTEGER NOT NULL COMMENT '同一对账单内从一开始连续递增的行号。',
  `bank_txn_ref` VARCHAR(128) NOT NULL COMMENT '银行提供的账户范围稳定交易编号。',
  `value_date` DATE NOT NULL COMMENT '银行流水资金生效的业务日期。',
  `direction` VARCHAR(64) NOT NULL COMMENT '银行流水相对于平台账户的借贷方向。允许值：CREDIT、DEBIT。',
  `amount` DECIMAL(24,8) NOT NULL COMMENT '银行流水的绝对金额且必须大于零。',
  `description_hash` CHAR(64) NOT NULL COMMENT '银行流水描述经规范化后的最小化 SHA-256 摘要。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bank_line` (`statement_id`, `line_no`) COMMENT '保证业务唯一性：statement_id、line_no',
  UNIQUE KEY `uk_bank_txn` (`cash_account_id`, `bank_txn_ref`) COMMENT '保证业务唯一性：cash_account_id、bank_txn_ref',
  KEY `idx_bank_item_value` (`cash_account_id`, `value_date`, `direction`) COMMENT '支持访问路径：cash_account_id、value_date、direction'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='不可变地记录银行对账单内每笔借贷流水及最小化描述摘要';

CREATE TABLE `t_bank_recon_match` (
  `id` BIGINT NOT NULL COMMENT '银行对账匹配分配的 Snowflake 主键。',
  `match_fingerprint` CHAR(64) NOT NULL COMMENT '目标、银行流水、类型和金额规范化后的唯一 SHA-256 指纹。',
  `match_type` VARCHAR(64) NOT NULL COMMENT '允许值：CHANNEL_CREDIT、CHANNEL_DEBIT、PAYOUT_DEBIT、TAX_PAYMENT_DEBIT。决定本行使用的业务目标字段组合。允许值：CHANNEL_CREDIT、CHANNEL_DEBIT、PAYOUT_DEBIT、TAX_PAYMENT_DEBIT。',
  `channel_batch_id` BIGINT NULL COMMENT '渠道入账或扣款匹配直接对应的本库渠道清算批次；确认前必须重新验证交易对账完整。',
  `payout_execution_id` BIGINT NULL COMMENT 'Payout 借记匹配对应的本库出款执行。',
  `payout_component_type` VARCHAR(64) NULL COMMENT '允许值：BENEFICIARY_TRANSFER、BANK_FEE、COMBINED。Payout 借记匹配对应的受益人转账、银行费用或合并借记组成；非 Payout 匹配必须为空。允许值：BENEFICIARY_TRANSFER、BANK_FEE、COMBINED。',
  `tax_payment_id` BIGINT NULL COMMENT '税款借记匹配对应的本库税款支付。',
  `bank_item_id` BIGINT NOT NULL COMMENT '为业务目标提供资金证据的本库银行流水。',
  `cash_account_binding_ver_id` BIGINT NOT NULL COMMENT '匹配时有效且与银行流水账户一致的本库用途绑定版本；用途由匹配类型严格决定。',
  `matched_amount` DECIMAL(24,8) NOT NULL COMMENT '本次从银行流水分配到业务目标的金额。',
  `status` VARCHAR(64) NOT NULL COMMENT '对账匹配当前确认状态；PROPOSED 和 REJECTED 不产生 Journal，只有 CONFIRMED 按 matched_amount 幂等推进 Channel 结算账户与 BANK_CASH。允许值：PROPOSED、CONFIRMED、REJECTED。',
  `decision_by` BIGINT NULL COMMENT 'Platform 身份系统中确认或拒绝该对账匹配的操作人标识。',
  `decision_time` DATETIME(6) NULL COMMENT '对账匹配被确认或拒绝的 UTC 微秒时间。',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT '聚合根条件更新使用的乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bank_recon_match` (`match_fingerprint`) COMMENT '保证业务唯一性：match_fingerprint',
  UNIQUE KEY `uk_bank_recon_channel` (`channel_batch_id`, `bank_item_id`, `match_type`) COMMENT '保证业务唯一性：channel_batch_id、bank_item_id、match_type',
  UNIQUE KEY `uk_bank_recon_payout` (`payout_execution_id`, `bank_item_id`, `match_type`) COMMENT '保证业务唯一性：payout_execution_id、bank_item_id、match_type',
  UNIQUE KEY `uk_bank_recon_tax` (`tax_payment_id`, `bank_item_id`, `match_type`) COMMENT '保证业务唯一性：tax_payment_id、bank_item_id、match_type',
  KEY `idx_bank_recon_channel` (`channel_batch_id`, `status`) COMMENT '支持访问路径：channel_batch_id、status',
  KEY `idx_bank_recon_payout` (`payout_execution_id`, `status`) COMMENT '支持访问路径：payout_execution_id、status',
  KEY `idx_bank_recon_bank_item` (`bank_item_id`, `status`) COMMENT '支持访问路径：bank_item_id、status',
  KEY `idx_bank_recon_binding_ver` (`cash_account_binding_ver_id`) COMMENT '支持访问路径：cash_account_binding_ver_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='把已完成交易对账并已重分类的 Channel Batch、Payout 借记或税款借记按金额分配到 Bank Statement Item，并冻结匹配时有效的用途绑定版本；Payout 拆分行分别核对受益人转账和银行费用，COMBINED 借记必须以相同 Bank Payment Ref 或权威银行证据绑定同一 Execution，不得因无关借记金额偶然相等而推断；仅 CONFIRMED 分配按 matched_amount 幂等过账，PROPOSED 或 REJECTED 不过账；确认前事务必须锁定 t_bank_statement_item，累计所有已确认分配不得超过流水金额，且同一流水的全部已确认分配必须使用同一兼容资金用途和 Cash Binding；此跨行门禁防止同一流水同时确认为 Channel 与 Payout/Tax，同时允许合法金额分配，nullable 目标唯一键不能替代该门禁；Channel Credit 确认后以 Journal 从 CHANNEL_SETTLEMENT_RECEIVABLE 转入 BANK_CASH，Channel Debit 确认后从 CHANNEL_SETTLEMENT_PAYABLE 减少 BANK_CASH，只有本层银行确认改变银行现金';

CREATE TABLE `t_recon_exception` (
  `id` BIGINT NOT NULL COMMENT '对账异常聚合根的 Snowflake 主键。',
  `exception_type` VARCHAR(64) NOT NULL COMMENT '对账异常的受控业务分类。允许值：UNMATCHED、SHORT_AMOUNT、EXCESS_AMOUNT、FEE_DIFFERENCE、DATE_DIFFERENCE、WRONG_ACCOUNT、WRONG_CURRENCY、LATE_DEBIT。',
  `source_type` VARCHAR(64) NOT NULL COMMENT '发生异常的 Finance 业务资源类型。取值来源：Finance 对账异常来源类型目录；非固定枚举。',
  `source_id` BIGINT NOT NULL COMMENT '发生异常的 Finance 业务资源稳定标识。',
  `entity_type` VARCHAR(64) NOT NULL COMMENT '区分对账异常所属的 CCT Operating Entity 或 Finance 本地内部财务主体。允许值：OPERATING_ENTITY、FINANCE_ENTITY。',
  `entity_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '按主体类型解释且由对账来源父链确定的稳定业务标识；不建立多态外键。',
  `currency` CHAR(3) NOT NULL COMMENT 'ISO 4217 三位币种代码。取值来源：ISO 4217 币种代码；非固定枚举。',
  `difference_amount` DECIMAL(24,8) NOT NULL COMMENT '需要调查和处置的对账差异金额。',
  `applied_resolution_id` BIGINT NULL COMMENT '异常完成处置时唯一指向本库最终已应用的对账异常处置。',
  `status` VARCHAR(64) NOT NULL COMMENT '对账异常当前处置状态。允许值：OPEN、UNDER_REVIEW、RESOLVED、CLOSED。',
  `owner_id` BIGINT NULL COMMENT 'Platform 身份系统中的当前处置负责人标识。',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT '聚合根条件更新使用的乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recon_exception` (`source_type`, `source_id`, `exception_type`) COMMENT '保证业务唯一性：source_type、source_id、exception_type',
  UNIQUE KEY `uk_recon_exception_applied` (`applied_resolution_id`) COMMENT '保证业务唯一性：applied_resolution_id',
  KEY `idx_recon_exception_entity` (`entity_type`, `entity_id`, `currency`, `status`) COMMENT '支持访问路径：entity_type、entity_id、currency、status',
  KEY `idx_recon_exception_owner` (`owner_id`, `status`) COMMENT '支持访问路径：owner_id、status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='记录未匹配、少款、多款、费用、日期和旧 FAILED Payout Execution 迟到扣款等不能自动归入舍入差额的对账异常；LATE_DEBIT 必须先以幂等暂记 Journal 反映 BANK_CASH 减少，主体必须由 Source、Bank 或 Cash 父链确定，不得人工改挂';

CREATE TABLE `t_recon_resolution` (
  `id` BIGINT NOT NULL COMMENT '对账异常处置的 Snowflake 主键。',
  `exception_id` BIGINT NOT NULL COMMENT '该处置所属的本库对账异常。',
  `resolution_no` INTEGER NOT NULL COMMENT '同一异常内从一开始连续递增的处置序号。',
  `resolution_type` VARCHAR(64) NOT NULL COMMENT '对账异常的受控解决方式。允许值：RE_MATCH、ACCOUNTING_ADJUSTMENT、ACCEPT_TIMING_DIFFERENCE、WRITE_OFF。',
  `maker_id` BIGINT NOT NULL COMMENT 'Platform 身份系统中的处置提交人标识。',
  `checker_id` BIGINT NULL COMMENT 'Platform 身份系统中的独立处置复核人标识。',
  `instruction_id` BIGINT NULL COMMENT '处置产生金额变化时创建的本库会计指令。',
  `channel_transaction_match_id` BIGINT NULL COMMENT '交易层重匹配处置实际采用的本库不可变渠道交易对账匹配。',
  `bank_recon_match_id` BIGINT NULL COMMENT '银行层重匹配处置实际采用且已经确认的本库银行对账匹配。',
  `content_hash` CHAR(64) NOT NULL COMMENT '异常、处置类型和处置输入的 Canonical SHA-256 哈希；提交复核后不得改变。',
  `checker_decision` VARCHAR(64) NULL COMMENT '独立复核人对冻结处置内容作出的决定。允许值：APPROVED、REJECTED。',
  `approved_content_hash` CHAR(64) NULL COMMENT '复核人实际审阅且必须等于处置内容哈希的 SHA-256 值。',
  `decision_time` DATETIME(6) NULL COMMENT '独立复核决定形成的 UTC 微秒时间。',
  `status` VARCHAR(64) NOT NULL COMMENT '对账异常处置当前审批和执行状态。允许值：DRAFT、PENDING_CHECK、APPROVED、REJECTED、APPLIED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recon_resolution` (`exception_id`, `resolution_no`) COMMENT '保证业务唯一性：exception_id、resolution_no',
  UNIQUE KEY `uk_recon_resolution_channel_txn` (`channel_transaction_match_id`) COMMENT '保证业务唯一性：channel_transaction_match_id',
  UNIQUE KEY `uk_recon_resolution_bank` (`bank_recon_match_id`) COMMENT '保证业务唯一性：bank_recon_match_id',
  KEY `idx_recon_resolution_st` (`status`, `create_time`) COMMENT '支持访问路径：status、create_time',
  UNIQUE KEY `uk_recon_instruction` (`instruction_id`) COMMENT '保证业务唯一性：instruction_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='记录对账异常的固定双人处置决定；重匹配必须明确选择渠道交易对账或银行现金对账其中一层';

CREATE TABLE `t_compensation_event` (
  `id` BIGINT NOT NULL COMMENT 'Finance 数据库内补偿事件记录的 Snowflake 主键，不作为跨系统事件标识。',
  `event_key` VARCHAR(255) NOT NULL COMMENT '按来源事实和事件类型确定性生成的全局稳定事件标识。',
  `event_type` VARCHAR(128) NOT NULL COMMENT '业务事实事件或本地恢复任务的稳定类型代码。取值来源：Finance 事件契约目录；非固定枚举。',
  `execution_type` VARCHAR(64) NOT NULL COMMENT '事件由 MQ 发布器或本地处理器执行。允许值：MQ_PUBLISH、LOCAL_HANDLER。',
  `source_type` VARCHAR(64) NOT NULL COMMENT '拥有该事件的 Finance 聚合根或不可变业务事实类型。取值来源：Finance 事件来源事实类型目录；非固定枚举。',
  `source_id` BIGINT NOT NULL COMMENT '拥有该事件的 Finance 聚合根或不可变事实标识。',
  `source_version` BIGINT NOT NULL COMMENT '来源聚合根版本或不可变事实固定版本。',
  `schema_version` INTEGER NOT NULL COMMENT '事件 Payload 契约的正整数版本。',
  `payload_json` JSON NOT NULL COMMENT '最大六十四 KiB、不可变且不含敏感明文的 Canonical JSON 事件信封。',
  `payload_hash` CHAR(64) NOT NULL COMMENT 'Canonical Payload 的 SHA-256 哈希，创建后不得修改。',
  `execute_status` VARCHAR(64) NOT NULL COMMENT '补偿事件当前 Claim、重试或人工处置状态。允许值：INIT、PROCESSING、ERROR、FAILED。',
  `execute_count` INTEGER NOT NULL DEFAULT 0 COMMENT '已经开始执行该事件的累计次数。',
  `next_execute_at` DATETIME(6) NOT NULL COMMENT '事件下次允许被 Claim 的 UTC 微秒时间。',
  `lock_owner` VARCHAR(128) NULL COMMENT '当前多实例 Claim Lease 的执行节点稳定标识。',
  `lock_until` DATETIME(6) NULL COMMENT '当前 Claim Lease 到期的 UTC 微秒时间。',
  `last_error_code` VARCHAR(64) NULL COMMENT '最近一次失败的受控且不含敏感信息的错误代码。取值来源：Finance 补偿执行错误代码目录；非固定枚举。',
  `last_error_summary` VARCHAR(1000) NULL COMMENT '最近一次失败的有界脱敏摘要，禁止保存敏感明文。',
  `lock_version` BIGINT NOT NULL DEFAULT 0 COMMENT '仅用于 Claim 和 Lease 条件更新的技术乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '补偿事件与业务事实原子登记的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '补偿事件执行状态最后更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_finance_comp_event` (`event_key`) COMMENT '保证业务唯一性：event_key',
  UNIQUE KEY `uk_finance_comp_source` (`source_type`, `source_id`, `source_version`, `event_type`) COMMENT '保证业务唯一性：source_type、source_id、source_version、event_type',
  KEY `idx_fin_comp_claim` (`execute_status`, `next_execute_at`, `lock_until`) COMMENT '支持访问路径：execute_status、next_execute_at、lock_until',
  KEY `idx_fin_comp_created` (`create_time`) COMMENT '支持访问路径：create_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='与 Finance 业务事实在同一事务登记 MQ 发布或本地处理任务，并通过 Claim、Lease 和乐观锁可靠重试';
