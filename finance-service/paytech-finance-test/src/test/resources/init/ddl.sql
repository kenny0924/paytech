-- Finance Mock 测试环境准备（管理员手工执行；应用与测试从不自动执行）。
-- 测试复用仓库 local-environment 的 mor_finance；账号与 application-mock.yml 一致，只授予本库权限。
-- 已有库和账号不删除、不重置。表结构与 finance-service/docs/database/sql/finance-schema.sql 保持一致，
-- 此处只列出 Accounting 测试实际读写的 16 张表；完整环境仍执行 finance-schema.sql 全量 84 表。
-- 初始化数据：执行 finance-service/docs/database/sql/finance-initial-data.sql（测试依赖 USD 与 STANDARD 科目表）。
CREATE DATABASE IF NOT EXISTS mor_finance DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'finance_app'@'%' IDENTIFIED BY '1f22a89fd7472c0d0b00813339c33ac0e4c06a4bf8e8bcc4';
GRANT ALL PRIVILEGES ON mor_finance.* TO 'finance_app'@'%';
USE mor_finance;
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
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存 Finance 使用的 ISO 4217 币种精度、舍入方式和启停状态。';

CREATE TABLE `t_chart_of_accounts` (
  `id` BIGINT NOT NULL COMMENT '服务侧生成的会计科目表 Snowflake 主键。',
  `chart_code` VARCHAR(64) NOT NULL COMMENT '科目表稳定且唯一的业务代码。取值来源：Finance 会计科目表代码；非固定枚举。',
  `chart_name` VARCHAR(255) NOT NULL COMMENT '供财务人员识别该科目目录的中文名称。',
  `status` VARCHAR(64) NOT NULL COMMENT '科目表当前是否允许新账簿采用。允许值：DRAFT、ACTIVE、INACTIVE。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chart_code` (`chart_code`) COMMENT '保证业务唯一性：chart_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存可供账簿采用的稳定多级会计科目目录。';

CREATE TABLE `t_chart_account` (
  `id` BIGINT NOT NULL COMMENT '服务侧生成的会计科目 Snowflake 主键。',
  `chart_id` BIGINT NOT NULL COMMENT '该科目所属的本库会计科目表。',
  `parent_account_id` BIGINT NULL COMMENT '该科目的直接上级科目，顶级科目为空；科目表被任一账簿采用后既有父子关系不可修改。',
  `account_code` VARCHAR(64) NOT NULL COMMENT '在同一会计科目表内唯一且激活后不可改变的科目代码。取值来源：Finance 会计科目代码；非固定枚举。',
  `account_name` VARCHAR(255) NOT NULL COMMENT '财务报表和记账界面使用的科目名称。',
  `account_class` VARCHAR(64) NOT NULL COMMENT '会计恒等式中的五类科目分类。允许值：ASSET、LIABILITY、EQUITY、REVENUE、EXPENSE。',
  `normal_side` VARCHAR(64) NOT NULL COMMENT '该科目余额增加时采用的借贷方向。允许值：DEBIT、CREDIT。',
  `posting_allowed` BOOLEAN NOT NULL COMMENT '是否允许 Journal Line 直接记入该科目的结构属性；汇总父科目必须为否，科目表被账簿采用后不可修改。1 表示是，0 表示否。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chart_acct` (`chart_id`, `account_code`) COMMENT '保证业务唯一性：chart_id、account_code',
  KEY `idx_chart_acct_parent` (`parent_account_id`) COMMENT '支持访问路径：parent_account_id',
  KEY `idx_chart_acct_class` (`chart_id`, `account_class`) COMMENT '支持访问路径：chart_id、account_class'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='定义稳定会计科目表中的五类多级科目及其正常余额方向和可过账属性。';

CREATE TABLE `t_finance_entity` (
  `id` BIGINT NOT NULL COMMENT '服务侧生成的内部财务主体 Snowflake 主键。',
  `entity_code` VARCHAR(64) NOT NULL COMMENT 'Finance 内部人工分配且永久唯一的财务主体代码。取值来源：Finance Entity Code；非固定枚举。',
  `legal_party_id` BIGINT NOT NULL COMMENT 'CCT Party 的稳定业务ID；由 Partner Agreement 唯一 PLATFORM_SIGNATORY 提供，不建立跨库外键。',
  `display_name` VARCHAR(256) NOT NULL COMMENT '供 Finance 内部操作和审计识别该财务主体的显示名称，不替代 CCT 法律资料。',
  `status` VARCHAR(64) NOT NULL COMMENT '控制该财务主体能否用于创建新账簿和新交易；关闭后不得恢复激活。允许值：ACTIVE、CLOSED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fin_entity_code` (`entity_code`) COMMENT '保证业务唯一性：entity_code',
  UNIQUE KEY `uk_fin_entity_party` (`legal_party_id`) COMMENT '保证业务唯一性：legal_party_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存 Finance 受控人工维护的内部财务主体，并以唯一法律 Party 稳定标识映射 CCT 中的平台签约法律主体；不复制法律资料。';

CREATE TABLE `t_ledger_book` (
  `id` BIGINT NOT NULL COMMENT '服务侧生成的总账账簿 Snowflake 主键。',
  `entity_type` VARCHAR(64) NOT NULL COMMENT '区分由 CCT Operating Entity 或 Finance 本地内部财务主体拥有的账簿。允许值：OPERATING_ENTITY、FINANCE_ENTITY。',
  `entity_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '按主体类型解释的稳定业务ID；Operating Entity 来自 CCT，Finance Entity 使用本地主键，不建立多态外键。',
  `currency` CHAR(3) NOT NULL COMMENT '该账簿唯一使用的 ISO 4217 三位币种代码。取值来源：ISO 4217 币种代码；非固定枚举。',
  `chart_id` BIGINT NOT NULL COMMENT '该账簿采用的本库稳定会计科目表。',
  `book_status` VARCHAR(64) NOT NULL COMMENT '账簿是否允许接收新的会计分录。允许值：OPEN、SUSPENDED、CLOSED。',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT '更新账簿状态时进行条件更新的乐观锁版本号。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ledger_book_entity` (`entity_type`, `entity_id`, `currency`) COMMENT '保证业务唯一性：entity_type、entity_id、currency',
  KEY `idx_ledger_book_chart` (`chart_id`) COMMENT '支持访问路径：chart_id',
  KEY `idx_ledger_book_status` (`book_status`) COMMENT '支持访问路径：book_status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='为单一 Operating Entity 或 Finance Entity 和单一币种建立独立会计账簿；主体类型与标识创建后不可修改。';

CREATE TABLE `t_account` (
  `id` BIGINT NOT NULL COMMENT '服务侧生成的统一记账账户 Snowflake 主键。',
  `ledger_book_id` BIGINT NOT NULL COMMENT '该账户严格归属的本库经营主体币种账簿。',
  `chart_account_id` BIGINT NOT NULL COMMENT '该账户使用且必须允许过账的本库会计科目定义。',
  `owner_type` VARCHAR(64) NOT NULL COMMENT '决定账户余额按经营主体、内部财务主体、Client、Local Partner、渠道账户、银行账户或税务机关隔离的类型。允许值：OPERATING_ENTITY、FINANCE_ENTITY、CLIENT、LOCAL_PARTNER、CHANNEL_ACCOUNT、CASH_ACCOUNT、TAX_AUTHORITY。',
  `owner_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '按owner_type解释的稳定字符串；OE为entityNo，其余数字身份保存规范十进制字符串；本库对象使用本地主键，外部对象不建立跨库外键。',
  `balance_bucket` VARCHAR(64) NOT NULL COMMENT 'PARTNER_PAYABLE 使用 PENDING、AVAILABLE、LOCKED，其中 Partner 的 PENDING 是共享待结算桶；PARTNER_RECOVERY_RECEIVABLE 使用 NONE；CLIENT_PAYABLE 使用 PENDING、AVAILABLE、LOCKED，CLIENT_RECOVERY_RECEIVABLE/NONE、CLIENT_SECURITY_DEPOSIT/NONE 以及 Channel 三阶段账户均使用独立会计科目并采用 NONE，不复用应付款余额桶。允许值：PENDING、AVAILABLE、LOCKED、NONE。',
  `normal_side` VARCHAR(64) NOT NULL COMMENT '创建账户时从会计科目复制并冻结的正常余额方向；账户仍允许借贷双向过账。允许值：DEBIT、CREDIT。',
  `account_status` VARCHAR(64) NOT NULL COMMENT '该具体核算账户当前是否允许新的会计分录过账。允许值：ACTIVE、INACTIVE、CLOSED。',
  `debit_total` DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '截至末次分录序号已计入该账户的累计借方金额。',
  `credit_total` DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '截至末次分录序号已计入该账户的累计贷方金额。',
  `net_balance` DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '按照本账户正常余额方向计算且可由不可变分录重建的有符号余额；正数表示正常方向，负数表示反向余额。',
  `last_journal_sequence` BIGINT NOT NULL DEFAULT 0 COMMENT '已经应用到账户余额投影的本账簿最后分录序号。',
  `balance_version` BIGINT NOT NULL DEFAULT 0 COMMENT '仅用于账户余额投影顺序条件更新的乐观锁版本，不表示账户身份版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account_identity` (`ledger_book_id`, `chart_account_id`, `owner_type`, `owner_id`, `balance_bucket`) COMMENT '保证业务唯一性：ledger_book_id、chart_account_id、owner_type、owner_id、balance_bucket',
  KEY `idx_account_owner` (`owner_type`, `owner_id`, `ledger_book_id`, `balance_bucket`) COMMENT '支持访问路径：owner_type、owner_id、ledger_book_id、balance_bucket',
  KEY `idx_account_chart_status` (`chart_account_id`, `ledger_book_id`, `account_status`) COMMENT '支持访问路径：chart_account_id、ledger_book_id、account_status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='在具体账簿和可过账科目下按核算对象与余额桶建立唯一记账账户，并保存可由不可变 Journal 重建的当前余额投影；CLIENT_PAYABLE 与 PARTNER_PAYABLE 使用 PENDING、AVAILABLE、LOCKED，其中 Partner PENDING 是共享待结算桶；Partner reversal 无法由 PENDING/AVAILABLE/LOCKED liability 吸收时使用资产科目 PARTNER_RECOVERY_RECEIVABLE/NONE；Client Recovery 使用 CLIENT_RECOVERY_RECEIVABLE/NONE，保证金使用 CLIENT_SECURITY_DEPOSIT/NONE；Channel 使用 CHANNEL_UNSETTLED_RECEIVABLE、CHANNEL_SETTLEMENT_RECEIVABLE、CHANNEL_UNSETTLED_PAYABLE、CHANNEL_SETTLEMENT_PAYABLE 四个科目表达交易形成、交易对账完整和银行确认三个阶段，Channel Cost 单独使用 CHANNEL_FEE_ACCRUAL，银行现金使用 BANK_CASH。';

CREATE TABLE `t_fiscal_calendar` (
  `id` BIGINT NOT NULL COMMENT '服务侧生成的财务日历 Snowflake 主键。',
  `entity_type` VARCHAR(64) NOT NULL COMMENT '区分使用该财务日历的 CCT Operating Entity 或 Finance 本地内部财务主体。允许值：OPERATING_ENTITY、FINANCE_ENTITY。',
  `entity_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '按主体类型解释的稳定业务ID；Finance Entity 使用本地主键且不建立多态外键。',
  `calendar_code` VARCHAR(64) NOT NULL COMMENT 'V1 中该经营主体唯一法定财务日历的稳定展示和接口代码。取值来源：Finance 财务日历代码；非固定枚举。',
  `timezone` VARCHAR(64) NOT NULL COMMENT '用于确定财务日期边界的 IANA 时区代码。取值来源：IANA 时区数据库；非固定枚举。',
  `year_start_month` TINYINT NOT NULL COMMENT '财年开始月份，取值范围为一至十二。',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT '更新财务日历规则时进行条件更新的乐观锁版本号。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fiscal_cal` (`entity_type`, `entity_id`) COMMENT '保证业务唯一性：entity_type、entity_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存 Operating Entity 或 Finance Entity 使用的财年起始月份和 IANA 时区规则；主体类型与标识创建后不可修改。';

CREATE TABLE `t_accounting_period` (
  `id` BIGINT NOT NULL COMMENT '服务侧生成的会计期间 Snowflake 主键。',
  `calendar_id` BIGINT NOT NULL COMMENT '该会计期间所属的本库财务日历。',
  `fiscal_year` INT NOT NULL COMMENT '该期间所属的四位财务年度。',
  `period_no` SMALLINT NOT NULL COMMENT '同一财年中严格递增的会计期间序号。',
  `start_date` DATE NOT NULL COMMENT '该会计期间包含的首个业务日期。',
  `end_date` DATE NOT NULL COMMENT '该会计期间包含的最后一个业务日期。',
  `status` VARCHAR(64) NOT NULL COMMENT '控制该期间是否允许新会计分录过账。允许值：FUTURE、OPEN、CLOSING、CLOSED、REOPENING。',
  `reopen_count` INT NOT NULL DEFAULT 0 COMMENT '该期间完成受控重开的累计次数。',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT '更新期间状态时进行条件更新的乐观锁版本号。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account_period` (`calendar_id`, `fiscal_year`, `period_no`) COMMENT '保证业务唯一性：calendar_id、fiscal_year、period_no',
  KEY `idx_period_date` (`calendar_id`, `start_date`, `end_date`) COMMENT '支持访问路径：calendar_id、start_date、end_date',
  KEY `idx_period_status` (`calendar_id`, `status`) COMMENT '支持访问路径：calendar_id、status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存财务日历下可过账、关闭或重开的会计期间。';

CREATE TABLE `t_book_sequence` (
  `id` BIGINT NOT NULL COMMENT '服务侧生成的账簿分录序列 Snowflake 主键。',
  `ledger_book_id` BIGINT NOT NULL COMMENT '使用该分录序列的本库总账账簿。',
  `next_value` BIGINT NOT NULL COMMENT '下一次在 Journal 过账本地事务中通过 SELECT FOR UPDATE 锁定本行后可分配的正整数序号。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_book_seq` (`ledger_book_id`) COMMENT '保证业务唯一性：ledger_book_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='为每个总账账簿在 Journal 过账本地事务中通过行锁分配严格递增的 Journal 序号；已提交 Journal 的序号不可复用。';

CREATE TABLE `t_accounting_instruction` (
  `id` BIGINT NOT NULL COMMENT '会计记账指令的服务侧雪花主键。',
  `ledger_book_id` BIGINT NOT NULL COMMENT '该指令唯一适用的本库 Ledger Book；全部指令行账户和最终 Journal Entry 必须逐值属于同一账簿。',
  `instruction_type` VARCHAR(64) NOT NULL COMMENT '该指令所表达的业务会计动作类型。取值来源：Finance 会计指令类型目录；非固定枚举。',
  `origin_type` VARCHAR(64) NOT NULL COMMENT '区分规则自动生成和人工调整生成的指令。允许值：AUTO、MANUAL。',
  `source_system` VARCHAR(64) NULL COMMENT '自动指令所消费权威事件的来源系统，人工指令为空。取值来源：Code-MoR 系统代码目录；非固定枚举。',
  `source_event_key` VARCHAR(128) NULL COMMENT '自动指令所消费权威事件的稳定事件键，人工指令为空。',
  `source_version` BIGINT NULL COMMENT '自动指令所消费权威事件或事实的版本，人工指令为空。',
  `source_payload_hash` CHAR(64) NULL COMMENT '自动指令来源规范载荷的 SHA-256 小写十六进制摘要，人工指令为空。',
  `transaction_economics_id` BIGINT NULL COMMENT '自动交易类指令对应的本库不可变交易经济结果主键。',
  `manual_adjustment_id` BIGINT NULL COMMENT '人工指令对应且已经固定双人审批的人工调整主键。',
  `accounting_rule_bundle_ver_id` BIGINT NULL COMMENT '自动指令冻结使用的自动记账规则版本主键。',
  `input_hash` CHAR(64) NULL COMMENT '自动指令确定性输入或人工调整冻结内容的 SHA-256 小写十六进制摘要。',
  `status` VARCHAR(64) NOT NULL COMMENT '记账指令当前验证和入账状态。允许值：RECEIVED、VALIDATING、VALIDATED、POSTED、REJECTED、CONFLICT。',
  `maker_id` BIGINT NULL COMMENT '人工指令对应的调整编制人主键，自动指令为空。',
  `checker_id` BIGINT NULL COMMENT '人工指令对应的调整复核人主键，自动指令为空且必须与编制人不同。',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT '记账指令状态条件更新使用的乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_instr_source` (`source_system`, `source_event_key`, `instruction_type`) COMMENT '保证业务唯一性：source_system、source_event_key、instruction_type',
  UNIQUE KEY `uk_instr_manual` (`manual_adjustment_id`) COMMENT '保证业务唯一性：manual_adjustment_id',
  KEY `idx_instr_book_status` (`ledger_book_id`, `status`, `create_time`) COMMENT '支持访问路径：ledger_book_id、status、create_time',
  KEY `idx_instr_economics` (`transaction_economics_id`) COMMENT '支持访问路径：transaction_economics_id',
  KEY `idx_instr_rule_ver` (`accounting_rule_bundle_ver_id`) COMMENT '支持访问路径：accounting_rule_bundle_ver_id',
  KEY `idx_instr_status` (`status`, `create_time`) COMMENT '支持访问路径：status、create_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='维护一个自动业务事实或已批准人工调整在单一账簿产生的平衡记账指令组及其验证、入账状态；同一服务费以 PARTNER 与 PLATFORM 两类指令分别绑定两本账';

CREATE TABLE `t_instruction_line` (
  `id` BIGINT NOT NULL COMMENT '记账指令明细的服务侧雪花主键。',
  `instruction_id` BIGINT NOT NULL COMMENT '所属会计记账指令主键。',
  `line_no` INTEGER NOT NULL COMMENT '同一记账指令内稳定且有序的明细序号。',
  `accounting_rule_line_id` BIGINT NULL COMMENT '自动指令行采用的自动记账规则行主键。',
  `manual_adjustment_line_id` BIGINT NULL COMMENT '人工指令行一一对应的已批准人工调整明细主键。',
  `account_id` BIGINT NOT NULL COMMENT '该指令行准备写入且已冻结账簿、科目、核算对象和余额桶的本库账户。',
  `entry_side` VARCHAR(64) NOT NULL COMMENT '指令行的借方或贷方方向。允许值：DEBIT、CREDIT。',
  `amount` DECIMAL(24,8) NOT NULL COMMENT '按币种精度舍入后且必须大于零的记账金额。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_instr_line` (`instruction_id`, `line_no`) COMMENT '保证业务唯一性：instruction_id、line_no',
  UNIQUE KEY `uk_instr_adj_line` (`manual_adjustment_line_id`) COMMENT '保证业务唯一性：manual_adjustment_line_id',
  KEY `idx_instr_line_rule` (`accounting_rule_line_id`) COMMENT '支持访问路径：accounting_rule_line_id',
  KEY `idx_instr_line_account` (`account_id`) COMMENT '支持访问路径：account_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='追加保存已解析到唯一统一记账账户的借贷指令行；全部账户必须属于 Instruction Header 指定的同一 Ledger Book';

CREATE TABLE `t_journal_entry` (
  `id` BIGINT NOT NULL COMMENT '会计分录的服务侧雪花主键。',
  `ledger_book_id` BIGINT NOT NULL COMMENT '该不可变分录所属的账簿主键。',
  `accounting_period_id` BIGINT NOT NULL COMMENT '分录过账日期所属且过账时必须开放的会计期间主键。',
  `instruction_id` BIGINT NOT NULL COMMENT '产生该会计分录的已验证记账指令主键。',
  `journal_sequence` BIGINT NOT NULL COMMENT '在所属账簿内原子取得且严格递增的分录序号。',
  `posting_date` DATE NOT NULL COMMENT '该会计分录所属的业务过账日期。',
  `entry_type` VARCHAR(64) NOT NULL COMMENT '区分标准、冲回、调整和关账类分录的受控类型。允许值：STANDARD、REVERSAL、ADJUSTMENT、CLOSING、REOPENING。',
  `reversal_of_entry_id` BIGINT NULL COMMENT '冲回分录严格反向引用的原不可变会计分录主键。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_journal_seq` (`ledger_book_id`, `journal_sequence`) COMMENT '保证业务唯一性：ledger_book_id、journal_sequence',
  UNIQUE KEY `uk_journal_instr` (`instruction_id`, `ledger_book_id`) COMMENT '保证业务唯一性：instruction_id、ledger_book_id',
  UNIQUE KEY `uk_journal_reversal` (`reversal_of_entry_id`) COMMENT '保证业务唯一性：reversal_of_entry_id',
  KEY `idx_journal_period` (`accounting_period_id`, `posting_date`) COMMENT '支持访问路径：accounting_period_id、posting_date'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='追加保存一条记账指令在单一账簿和开放期间内形成的不可变会计分录头';

CREATE TABLE `t_journal_line` (
  `id` BIGINT NOT NULL COMMENT '会计分录明细的服务侧雪花主键。',
  `journal_entry_id` BIGINT NOT NULL COMMENT '所属不可变会计分录主键。',
  `line_no` INTEGER NOT NULL COMMENT '同一会计分录内稳定且有序的明细序号。',
  `instruction_line_id` BIGINT NOT NULL COMMENT '生成该分录明细的记账指令明细主键。',
  `account_id` BIGINT NOT NULL COMMENT '该分录明细实际记入且必须属于分录头账簿的本库统一账户。',
  `entry_side` VARCHAR(64) NOT NULL COMMENT '该分录明细的借方或贷方方向。允许值：DEBIT、CREDIT。',
  `amount` DECIMAL(24,8) NOT NULL COMMENT '按币种精度舍入且必须大于零的不可变分录金额。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_journal_line` (`journal_entry_id`, `line_no`) COMMENT '保证业务唯一性：journal_entry_id、line_no',
  UNIQUE KEY `uk_journal_instruction_line` (`instruction_line_id`) COMMENT '保证业务唯一性：instruction_line_id',
  KEY `idx_journal_line_account` (`account_id`) COMMENT '支持访问路径：account_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='追加保存会计分录中不可变的借贷行，并通过统一账户保持账簿、科目、核算对象和余额桶可追溯';

CREATE TABLE `t_manual_adjustment` (
  `id` BIGINT NOT NULL COMMENT '人工会计调整申请的服务侧雪花主键。',
  `adjustment_no` VARCHAR(128) NOT NULL COMMENT '人工会计调整申请的稳定业务单号。',
  `ledger_book_id` BIGINT NOT NULL COMMENT '人工调整唯一适用的账簿主键。',
  `accounting_period_id` BIGINT NOT NULL COMMENT '人工调整唯一适用且提交时必须开放的会计期间主键。',
  `currency` CHAR(3) NOT NULL COMMENT '人工调整全部明细共同使用的 ISO 4217 三字母币种。取值来源：ISO 4217；非固定枚举。',
  `reason_code` VARCHAR(64) NOT NULL COMMENT '人工会计调整使用的受控业务原因代码。取值来源：Finance 人工会计调整原因目录；非固定枚举。',
  `file_id` BIGINT NULL COMMENT 'Platform 文件服务保存的受控调整证据文件主键。',
  `file_content_hash` CHAR(64) NULL COMMENT 'Platform 文件服务返回且与证据文件主键成组保存的 SHA-256 小写十六进制摘要。',
  `total_debit` DECIMAL(24,8) NOT NULL COMMENT '提交时必须大于零并等于贷方合计的借方总金额。',
  `total_credit` DECIMAL(24,8) NOT NULL COMMENT '提交时必须大于零并等于借方合计的贷方总金额。',
  `content_hash` CHAR(64) NOT NULL COMMENT '覆盖申请头和有序明细的规范化 SHA-256 小写十六进制摘要。',
  `submitted_time` DATETIME(6) NULL COMMENT '人工调整进入不可修改提交态的 UTC 微秒时间。',
  `status` VARCHAR(64) NOT NULL COMMENT '人工会计调整申请的当前生命周期状态。允许值：DRAFT、SUBMITTED、APPROVED、REJECTED、POSTED、CANCELLED。',
  `maker_id` BIGINT NOT NULL COMMENT '创建并提交人工调整的平台用户主键。',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT '人工调整条件更新使用的乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_manual_adj` (`adjustment_no`) COMMENT '保证业务唯一性：adjustment_no',
  KEY `idx_manual_adj_book` (`ledger_book_id`, `status`) COMMENT '支持访问路径：ledger_book_id、status',
  KEY `idx_manual_adj_period` (`accounting_period_id`, `status`) COMMENT '支持访问路径：accounting_period_id、status',
  KEY `idx_manual_adj_file` (`file_id`) COMMENT '支持访问路径：file_id',
  KEY `idx_manual_adj_status` (`status`, `submitted_time`) COMMENT '支持访问路径：status、submitted_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='维护人工会计调整申请头、平衡金额、受控文件证据和提交审批状态';

CREATE TABLE `t_manual_adjustment_line` (
  `id` BIGINT NOT NULL COMMENT '人工会计调整明细的服务侧雪花主键。',
  `adjustment_id` BIGINT NOT NULL COMMENT '所属人工会计调整申请主键。',
  `line_no` INTEGER NOT NULL COMMENT '同一人工调整内稳定且有序的明细序号。',
  `account_id` BIGINT NOT NULL COMMENT '人工调整实际使用且必须属于调整头账簿的本库统一账户。',
  `entry_side` VARCHAR(64) NOT NULL COMMENT '人工调整明细的借方或贷方方向。允许值：DEBIT、CREDIT。',
  `amount` DECIMAL(24,8) NOT NULL COMMENT '按币种精度舍入且必须大于零的调整金额。',
  `line_description` VARCHAR(512) NOT NULL COMMENT '不含敏感值的人工调整业务原因说明。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_manual_adj_line` (`adjustment_id`, `line_no`) COMMENT '保证业务唯一性：adjustment_id、line_no',
  KEY `idx_manual_line_account` (`account_id`) COMMENT '支持访问路径：account_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存人工会计调整中冻结的借贷明细，提交后只读并由人工记账指令逐行承接';

CREATE TABLE `t_adjustment_approval` (
  `id` BIGINT NOT NULL COMMENT '人工会计调整审批记录的服务侧雪花主键。',
  `adjustment_id` BIGINT NOT NULL COMMENT '被审批的人工会计调整申请主键。',
  `approval_role` VARCHAR(64) NOT NULL COMMENT '本条审批记录承担的编制或复核角色。允许值：MAKER、CHECKER。',
  `actor_id` BIGINT NOT NULL COMMENT '执行该审批动作的平台用户主键。',
  `decision` VARCHAR(64) NOT NULL COMMENT '审批人对冻结调整内容作出的决定。允许值：APPROVED、REJECTED。',
  `approved_content_hash` CHAR(64) NOT NULL COMMENT '审批时确认且必须等于人工调整内容摘要的 SHA-256 小写十六进制值。',
  `decision_time` DATETIME(6) NOT NULL COMMENT '审批决定形成的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_adj_approval` (`adjustment_id`, `approval_role`) COMMENT '保证业务唯一性：adjustment_id、approval_role',
  KEY `idx_adj_approval_actor` (`actor_id`, `decision_time`) COMMENT '支持访问路径：actor_id、decision_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='追加保存人工会计调整固定双人审批的角色、决定和被批准内容摘要';
