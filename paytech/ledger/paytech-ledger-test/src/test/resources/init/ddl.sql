-- ledger-service Mock 测试环境准备（管理员手工执行；应用与测试从不自动执行）。
-- 测试复用仓库 local-environment 的 MySQL；账号与 application-mock.yml 一致，只授予本库最小 DML 权限（design/10 §4.2）。
-- 已有库和账号不删除、不重置。表结构逐字取自 paytech/ledger/docs/database/sql/ledger-schema.sql 的全部 31 张表。
-- 保留行逐字取自 paytech/ledger/docs/database/sql/ledger-initial-data.sql（测试只删除 ID ≥ 1,000,000 的行）。
CREATE DATABASE IF NOT EXISTS mor_ledger DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'ledger_app'@'%' IDENTIFIED BY '05a148d75d6913440f13b66dd4f793519923433100eb1d0a';
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_ledger.* TO 'ledger_app'@'%';
USE mor_ledger;
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

CREATE TABLE `t_ledger_account` (
  `id` BIGINT NOT NULL COMMENT '服务侧生成的会计科目账户 Snowflake 主键，也是全局账户 ID。',
  `ledger_book_id` BIGINT NOT NULL COMMENT '该账户严格归属的本库经营主体或财务主体币种账簿。',
  `chart_account_id` BIGINT NOT NULL COMMENT '该账户使用且必须允许过账的本库会计科目定义。',
  `owner_type` VARCHAR(64) NOT NULL COMMENT '决定账户按经营主体、内部财务主体、渠道账户、银行账户或税务机关隔离的类型；Client / Local Partner 不再在总账开户，其科目以账簿主体为 Owner 的控制账户表达。允许值：OPERATING_ENTITY、FINANCE_ENTITY、CHANNEL_ACCOUNT、CASH_ACCOUNT、TAX_AUTHORITY。',
  `owner_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '按owner_type解释的稳定字符串；OE为entityNo，其余数字身份保存规范十进制字符串；本库对象使用本地主键，外部对象不建立跨库外键。',
  `balance_bucket` VARCHAR(64) NOT NULL COMMENT '总账账户不再按余额桶分账户，一律 NONE；Owner 余额桶由 account 明细账维护。允许值：NONE。',
  `normal_side` VARCHAR(64) NOT NULL COMMENT '创建账户时从会计科目复制并冻结的正常余额方向；账户仍允许借贷双向过账。允许值：DEBIT、CREDIT。',
  `account_status` VARCHAR(64) NOT NULL COMMENT '该具体核算账户当前是否允许新的会计分录过账。允许值：ACTIVE、INACTIVE、CLOSED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Ledger 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account_identity` (`ledger_book_id`, `chart_account_id`, `owner_type`, `owner_id`, `balance_bucket`) COMMENT '保证业务唯一性：ledger_book_id、chart_account_id、owner_type、owner_id、balance_bucket',
  KEY `idx_account_owner` (`owner_type`, `owner_id`, `ledger_book_id`, `balance_bucket`) COMMENT '支持访问路径：owner_type、owner_id、ledger_book_id、balance_bucket',
  KEY `idx_account_chart_status` (`chart_account_id`, `ledger_book_id`, `account_status`) COMMENT '支持访问路径：chart_account_id、ledger_book_id、account_status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='在具体账簿和可过账科目下按核算对象建立唯一会计科目账户（只保存身份，不保存余额）；余额由不可变 Journal 聚合得出。Owner 科目（CLIENT_PAYABLE、CLIENT_SECURITY_DEPOSIT、CLIENT_RECOVERY_RECEIVABLE、PARTNER_PAYABLE、PARTNER_RECOVERY_RECEIVABLE）每账簿每科目一个控制账户，Owner 取账簿主体、桶 NONE，明细在 account 的 t_owner_account（2026-10-18 决定第 3 条）；Channel 使用 CHANNEL_UNSETTLED_RECEIVABLE、CHANNEL_SETTLEMENT_RECEIVABLE、CHANNEL_UNSETTLED_PAYABLE、CHANNEL_SETTLEMENT_PAYABLE 与 CHANNEL_FEE_ACCRUAL，银行存款使用 BANK_CASH / CASH_ACCOUNT，税务机关使用 TAX_PAYABLE。';

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

CREATE TABLE `t_accounting_rule_bundle` (
  `id` BIGINT NOT NULL COMMENT '自动记账规则集的服务侧雪花主键。',
  `bundle_code` VARCHAR(64) NOT NULL COMMENT '自动记账规则集的稳定业务代码。取值来源：Finance 自动记账规则集代码目录；非固定枚举。',
  `current_ver_id` BIGINT NULL COMMENT '当前生效的自动记账规则版本主键。',
  `status` VARCHAR(64) NOT NULL COMMENT '自动记账规则集当前生命周期状态。允许值：DRAFT、ACTIVE、INACTIVE。',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT '规则集条件更新使用的乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_acct_rule_bundle` (`bundle_code`) COMMENT '保证业务唯一性：bundle_code',
  UNIQUE KEY `uk_acct_bundle_current` (`current_ver_id`) COMMENT '保证业务唯一性：current_ver_id',
  KEY `idx_acct_bundle_status` (`status`) COMMENT '支持访问路径：status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='维护自动记账规则集的稳定身份和当前生效版本指针，不保存可执行表达式';

CREATE TABLE `t_accounting_rule_bundle_ver` (
  `id` BIGINT NOT NULL COMMENT '自动记账规则版本的服务侧雪花主键。',
  `bundle_id` BIGINT NOT NULL COMMENT '所属自动记账规则集主键。',
  `bundle_ver` BIGINT NOT NULL COMMENT '同一规则集内严格递增的业务版本号。',
  `effective_from` DATETIME(6) NOT NULL COMMENT '该规则版本开始适用的 UTC 微秒时间。',
  `effective_to` DATETIME(6) NULL COMMENT '该规则版本停止适用的 UTC 微秒时间，空值表示尚未失效。',
  `status` VARCHAR(64) NOT NULL COMMENT '自动记账规则版本的审批和生效状态。允许值：DRAFT、PENDING_APPROVAL、APPROVED、ACTIVE、REJECTED、RETIRED。',
  `maker_id` BIGINT NOT NULL COMMENT '创建并提交该规则版本的平台用户主键。',
  `checker_id` BIGINT NULL COMMENT '审批该规则版本的平台用户主键，必须与编制人不同。',
  `approved_time` DATETIME(6) NULL COMMENT '复核人批准该规则版本的 UTC 微秒时间；未批准或已驳回时为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_acct_rule_ver` (`bundle_id`, `bundle_ver`) COMMENT '保证业务唯一性：bundle_id、bundle_ver',
  KEY `idx_acct_ver_status` (`status`, `effective_from`) COMMENT '支持访问路径：status、effective_from'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存一版自动记账规则的生效区间、发布状态和固定双人审批结果；提交审批时冻结完整有序规则行集，所有追加、提交、审批与当前指针切换事务统一先锁定 Bundle 再锁定 Version';

CREATE TABLE `t_accounting_rule_line` (
  `id` BIGINT NOT NULL COMMENT '自动记账规则行的服务侧雪花主键。',
  `bundle_ver_id` BIGINT NOT NULL COMMENT '所属自动记账规则版本主键。',
  `instruction_type` VARCHAR(64) NOT NULL COMMENT '该规则行适用的会计指令业务类型。取值来源：Finance 会计指令类型目录；非固定枚举。',
  `line_no` INTEGER NOT NULL COMMENT '同一规则版本和指令类型内稳定且有序的规则行序号。',
  `line_code` VARCHAR(64) NOT NULL COMMENT '同一规则版本和指令类型内稳定识别该会计含义的代码。取值来源：Finance 自动记账规则行代码目录；非固定枚举。',
  `amount_source_code` VARCHAR(64) NOT NULL COMMENT '指向交易经济组件或受控业务金额的稳定代码；同一来源允许生成多条借贷规则行；渠道成本差额分别使用 CHANNEL_COST_INCREASE 和 CHANNEL_COST_DECREASE。取值来源：Finance 自动记账金额来源目录；非固定枚举。',
  `owner_role` VARCHAR(64) NOT NULL COMMENT '由固定解释器解析统一账户核算对象类型和稳定ID的业务参与方角色。取值来源：Finance 记账账户所有者角色目录；非固定枚举。',
  `account_code` VARCHAR(64) NOT NULL COMMENT '在适用账簿科目表中必须唯一解析的叶子科目代码。取值来源：Finance 会计科目代码目录；非固定枚举。',
  `balance_bucket` VARCHAR(64) NOT NULL COMMENT '统一账户使用的余额桶；不区分运营状态的科目固定使用 NONE。允许值：PENDING、AVAILABLE、LOCKED、NONE。',
  `entry_side` VARCHAR(64) NOT NULL COMMENT '该规则行生成分录行的借方或贷方方向。允许值：DEBIT、CREDIT。',
  `required_flag` BOOLEAN NOT NULL COMMENT '为 1 时金额来源缺失即拒绝生成指令，为 0 时来源缺失可跳过该规则行；来源存在但金额为零时不视为缺失，对同一金额来源的规则行整体跳过且不生成零金额指令行。1 表示是，0 表示否。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_acct_rule_line` (`bundle_ver_id`, `instruction_type`, `line_no`) COMMENT '保证业务唯一性：bundle_ver_id、instruction_type、line_no',
  UNIQUE KEY `uk_acct_rule_line_code` (`bundle_ver_id`, `instruction_type`, `line_code`) COMMENT '保证业务唯一性：bundle_ver_id、instruction_type、line_code',
  KEY `idx_acct_rule_amount` (`bundle_ver_id`, `instruction_type`, `amount_source_code`) COMMENT '支持访问路径：bundle_ver_id、instruction_type、amount_source_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='把一个已计算金额来源确定性映射为借贷方向、科目、核算对象角色和余额桶，不承担金额计算；仅所属版本为 DRAFT 时可追加，版本离开 DRAFT 后禁止新增且规则行自身不可更新或删除';

CREATE TABLE `t_accounting_instruction` (
  `id` BIGINT NOT NULL COMMENT '会计记账指令的服务侧雪花主键。',
  `ledger_book_id` BIGINT NOT NULL COMMENT '该指令唯一适用的本库 Ledger Book；全部指令行账户和最终 Journal Entry 必须逐值属于同一账簿。',
  `instruction_type` VARCHAR(64) NOT NULL COMMENT '该指令所表达的业务会计动作类型。取值来源：Finance 会计指令类型目录；非固定枚举。',
  `origin_type` VARCHAR(64) NOT NULL COMMENT '区分规则自动生成、account 入账批次生成和人工调整生成的指令。允许值：AUTO、ACCOUNT_POSTING、MANUAL。',
  `source_system` VARCHAR(64) NULL COMMENT '自动指令所消费权威事件的来源系统（ACCOUNT_POSTING 指令为 account），人工指令为空。取值来源：Code-MoR 系统代码目录；非固定枚举。',
  `source_event_key` VARCHAR(128) NULL COMMENT '自动指令所消费权威事件的稳定事件键（ACCOUNT_POSTING 指令为 account 入账批次 ID），人工指令为空。',
  `source_version` BIGINT NULL COMMENT '自动指令所消费权威事件或事实的版本（ACCOUNT_POSTING 指令为 posting_sequence），人工指令为空。',
  `source_payload_hash` CHAR(64) NULL COMMENT '自动指令来源规范载荷的 SHA-256 小写十六进制摘要，人工指令为空。',
  `economic_fact_id` BIGINT NULL COMMENT '自动交易类指令对应的 billing 经济事实主键，仅存 ID；原列名 transaction_economics_id。',
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
  KEY `idx_instr_economics` (`economic_fact_id`) COMMENT '支持访问路径：economic_fact_id',
  KEY `idx_instr_rule_ver` (`accounting_rule_bundle_ver_id`) COMMENT '支持访问路径：accounting_rule_bundle_ver_id',
  KEY `idx_instr_status` (`status`, `create_time`) COMMENT '支持访问路径：status、create_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='维护一个自动业务事实或已批准人工调整在单一账簿产生的平衡记账指令组及其验证、入账状态；同一服务费以 PARTNER 与 PLATFORM 两类指令分别绑定两本账';

CREATE TABLE `t_instruction_line` (
  `id` BIGINT NOT NULL COMMENT '记账指令明细的服务侧雪花主键。',
  `instruction_id` BIGINT NOT NULL COMMENT '所属会计记账指令主键。',
  `line_no` INTEGER NOT NULL COMMENT '同一记账指令内稳定且有序的明细序号。',
  `accounting_rule_line_id` BIGINT NULL COMMENT '自动指令行采用的自动记账规则行主键。',
  `manual_adjustment_line_id` BIGINT NULL COMMENT '人工指令行一一对应的已批准人工调整明细主键。',
  `account_entry_id` BIGINT NULL COMMENT 'ACCOUNT_POSTING 指令中 Owner 控制科目行对应的 account 流水（mor_account.t_account_entry.id），用于下钻；规则行与人工行为空；与 accounting_rule_line_id、manual_adjustment_line_id 三选一。',
  `account_id` BIGINT NOT NULL COMMENT '该指令行准备写入且已冻结账簿、科目、核算对象和余额桶的本库账户。',
  `entry_side` VARCHAR(64) NOT NULL COMMENT '指令行的借方或贷方方向。允许值：DEBIT、CREDIT。',
  `amount` DECIMAL(24,8) NOT NULL COMMENT '按币种精度舍入后且必须大于零的记账金额。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_instr_line` (`instruction_id`, `line_no`) COMMENT '保证业务唯一性：instruction_id、line_no',
  UNIQUE KEY `uk_instr_adj_line` (`manual_adjustment_line_id`) COMMENT '保证业务唯一性：manual_adjustment_line_id',
  KEY `idx_instr_line_rule` (`accounting_rule_line_id`) COMMENT '支持访问路径：accounting_rule_line_id',
  KEY `idx_instr_line_account` (`account_id`) COMMENT '支持访问路径：account_id',
  KEY `idx_instr_line_entry` (`account_entry_id`) COMMENT '支持访问路径：account_entry_id'
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

CREATE TABLE `t_ledger_period_balance` (
  `id` BIGINT NOT NULL COMMENT '账簿期间科目余额的服务侧雪花主键。',
  `period_id` BIGINT NOT NULL COMMENT '本余额行所属的 Finance 会计期间主键。',
  `ledger_book_id` BIGINT NOT NULL COMMENT '本余额行严格归属的会计主体币种账簿。',
  `chart_account_id` BIGINT NOT NULL COMMENT '本余额行汇总的本库可过账会计科目定义。',
  `opening_balance` DECIMAL(24,8) NOT NULL COMMENT '会计期间开始时按科目正常方向计算的余额。',
  `period_debit` DECIMAL(24,8) NOT NULL COMMENT '会计期间内累计借方发生金额。',
  `period_credit` DECIMAL(24,8) NOT NULL COMMENT '会计期间内累计贷方发生金额。',
  `closing_balance` DECIMAL(24,8) NOT NULL COMMENT '截止试算序号按科目正常方向计算的期末余额。',
  `as_of_sequence` BIGINT NOT NULL COMMENT '本余额行已投影到的 Journal Sequence 水位；只允许较新水位覆盖，相同 Run 和水位幂等，旧水位不得覆盖较新投影。',
  `trial_balance_run_id` BIGINT NOT NULL COMMENT '产生或最终冻结本余额行的试算平衡任务主键。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ledger_period_bal` (`period_id`, `ledger_book_id`, `chart_account_id`) COMMENT '保证业务唯一性：period_id、ledger_book_id、chart_account_id',
  KEY `idx_period_bal_book_chart` (`ledger_book_id`, `chart_account_id`, `period_id`) COMMENT '支持访问路径：ledger_book_id、chart_account_id、period_id',
  KEY `idx_period_bal_chart` (`chart_account_id`, `ledger_book_id`, `period_id`) COMMENT '支持访问路径：chart_account_id、ledger_book_id、period_id',
  KEY `idx_period_bal_trial` (`trial_balance_run_id`) COMMENT '支持访问路径：trial_balance_run_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存 Trial Balance 与关账流程按账簿和科目汇总冻结的期初、本期发生额和期末余额';

CREATE TABLE `t_trial_balance_run` (
  `id` BIGINT NOT NULL COMMENT '试算平衡运行的 Snowflake 主键。',
  `ledger_book_id` BIGINT NOT NULL COMMENT '执行试算平衡的本库经营主体币种账簿。',
  `period_id` BIGINT NOT NULL COMMENT '执行试算平衡的本库会计期间。',
  `run_no` INTEGER NOT NULL COMMENT '同一账簿和期间内从一开始连续递增的试算序号。',
  `as_of_sequence` BIGINT NOT NULL COMMENT '本次试算纳入的最大 Journal Sequence。',
  `status` VARCHAR(64) NOT NULL COMMENT '试算平衡运行当前执行结果。允许值：CREATED、RUNNING、SUCCEEDED、FAILED。',
  `total_debit` DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '可过账叶子科目的期末余额按科目正常方向和有符号余额拆分后处于实际借方的金额合计，不表示本期借方发生额。',
  `total_credit` DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '可过账叶子科目的期末余额按科目正常方向和有符号余额拆分后处于实际贷方的金额合计，不表示本期贷方发生额。',
  `content_hash` CHAR(64) NULL COMMENT '运行终态冻结 Scope、水位、试算行和异常发现事实的 Canonical SHA-256 哈希，不包含随后可变的异常解决字段；运行态必须为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trial_run` (`ledger_book_id`, `period_id`, `run_no`) COMMENT '保证业务唯一性：ledger_book_id、period_id、run_no',
  KEY `idx_trial_run_period` (`period_id`, `status`) COMMENT '支持访问路径：period_id、status',
  KEY `idx_trial_run_status` (`status`, `create_time`) COMMENT '支持访问路径：status、create_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='按账簿、会计期间和 Journal Sequence 水位执行可重放试算平衡，并冻结可过账叶子科目期末余额按实际借贷方向拆分的合计';

CREATE TABLE `t_trial_balance_line` (
  `id` BIGINT NOT NULL COMMENT '试算平衡明细行的 Snowflake 主键。',
  `run_id` BIGINT NOT NULL COMMENT '该明细所属的本库试算平衡运行。',
  `chart_account_id` BIGINT NOT NULL COMMENT '被试算且由运行账簿范围唯一限定的本库会计科目定义。',
  `opening_balance` DECIMAL(24,8) NOT NULL COMMENT '按科目正常方向表达的期间期初余额。',
  `period_debit` DECIMAL(24,8) NOT NULL COMMENT '期间内该科目的借方累计发生金额。',
  `period_credit` DECIMAL(24,8) NOT NULL COMMENT '期间内该科目的贷方累计发生金额。',
  `closing_balance` DECIMAL(24,8) NOT NULL COMMENT '按科目正常方向表达的期间期末余额。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trial_line` (`run_id`, `chart_account_id`) COMMENT '保证业务唯一性：run_id、chart_account_id',
  KEY `idx_trial_line_account` (`chart_account_id`) COMMENT '支持访问路径：chart_account_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='不可变地冻结运行账簿内每个会计科目汇总后的期初余额、本期借贷发生额和期末余额';

CREATE TABLE `t_trial_balance_exception` (
  `id` BIGINT NOT NULL COMMENT '试算平衡异常的 Snowflake 主键。',
  `run_id` BIGINT NOT NULL COMMENT '发现该异常的本库试算平衡运行。',
  `exception_type` VARCHAR(64) NOT NULL COMMENT '试算平衡完整性异常的受控分类；COUNTERPARTY_MISMATCH 还覆盖同一 Economics、Component 和币种下 PLATFORM_SERVICE_PAYABLE 与 PLATFORM_SERVICE_RECEIVABLE 不相等；CHANNEL_SETTLEMENT_MISMATCH 覆盖 Channel 待清算、结算应收应付与已确认 Bank Statement 之间的阶段差异。允许值：COUNTERPARTY_MISMATCH、CHANNEL_SETTLEMENT_MISMATCH、GENERAL_LEDGER_MISMATCH、UNRESOLVED_RECONCILIATION。',
  `reference_type` VARCHAR(64) NOT NULL COMMENT '明确异常所引用的统一账户、会计科目、银行账户或对账异常记录类型。允许值：ACCOUNT、CHART_ACCOUNT、CASH_ACCOUNT、RECON_EXCEPTION。',
  `reference_id` BIGINT NOT NULL COMMENT '按异常引用类型解释且用于定位被核对对象的稳定主键。',
  `difference_amount` DECIMAL(24,8) NOT NULL COMMENT '核对结果之间的绝对差异金额。',
  `status` VARCHAR(64) NOT NULL COMMENT '试算平衡异常当前处置状态。允许值：OPEN、UNDER_REVIEW、RESOLVED、WAIVED。',
  `resolution_type` VARCHAR(64) NULL COMMENT '终态异常采用的受控解决证据类型。允许值：RECON_RESOLUTION、ACCOUNTING_INSTRUCTION、CONTROLLED_WAIVER。',
  `resolution_ref_id` BIGINT NULL COMMENT '批准的对账处置、会计指令、投影重建命令结果或 Platform 控制豁免的稳定标识。',
  `resolution_content_hash` CHAR(64) NULL COMMENT '异常、解决类型、证据标识和决定的 Canonical SHA-256 哈希。',
  `resolution_maker_id` BIGINT NULL COMMENT 'Platform 身份系统中提交该异常解决方案的操作人标识。',
  `resolution_checker_id` BIGINT NULL COMMENT 'Platform 身份系统中独立批准该异常解决方案的操作人标识。',
  `resolution_time` DATETIME(6) NULL COMMENT '双人控制完成并使异常进入终态的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trial_exception` (`run_id`, `exception_type`, `reference_type`, `reference_id`) COMMENT '保证业务唯一性：run_id、exception_type、reference_type、reference_id',
  KEY `idx_trial_exception_st` (`run_id`, `status`) COMMENT '支持访问路径：run_id、status',
  KEY `idx_trial_exception_ref` (`reference_type`, `reference_id`, `status`) COMMENT '支持访问路径：reference_type、reference_id、status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='记录账户、往来、在途、对账或科目汇总之间阻断关账的不一致及其解决引用，并核对平台服务费双账簿应付与应收';

CREATE TABLE `t_period_close` (
  `id` BIGINT NOT NULL COMMENT '期间关账或重开聚合根的 Snowflake 主键。',
  `period_id` BIGINT NOT NULL COMMENT '被关账或重开的本库会计期间。',
  `close_no` INTEGER NOT NULL COMMENT '同一会计期间内从一开始连续递增的流程序号。',
  `close_type` VARCHAR(64) NOT NULL COMMENT '区分期间关账与受控重开。允许值：CLOSE、REOPEN。',
  `reopens_close_id` BIGINT NULL COMMENT '重开流程唯一引用同一期间已完成的原关账流程；普通关账为空。',
  `reopen_reason_code` VARCHAR(64) NULL COMMENT '重开会计期间采用的受控业务原因；普通关账为空。取值来源：Finance 期间重开原因目录；非固定枚举。',
  `reopen_request_ref` VARCHAR(128) NULL COMMENT '批准发起本次重开的 Platform 工单或操作申请稳定引用；普通关账为空。',
  `status` VARCHAR(64) NOT NULL COMMENT '期间关账或重开流程当前状态。允许值：CREATED、RUNNING、PENDING_CHECK、APPROVED、REJECTED、COMPLETED、FAILED。',
  `active_slot` TINYINT NULL COMMENT '流程非终态时固定为 1，终态后必须固定为 NULL。',
  `maker_id` BIGINT NOT NULL COMMENT 'Platform 身份系统中的关账或重开提交人标识。',
  `checker_id` BIGINT NULL COMMENT 'Platform 身份系统中的独立关账或重开复核人标识。',
  `close_hash` CHAR(64) NULL COMMENT '流程进入 PENDING_CHECK 时冻结的流程头、全部账簿范围、步骤、试算、异常解决和结转指令的 Canonical SHA-256 哈希；CREATED/RUNNING/FAILED 时为空。',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT '关账或重开聚合根条件更新使用的乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_period_close` (`period_id`, `close_no`) COMMENT '保证业务唯一性：period_id、close_no',
  UNIQUE KEY `uk_period_close_active` (`period_id`, `active_slot`) COMMENT '保证业务唯一性：period_id、active_slot',
  KEY `idx_period_reopen_target` (`reopens_close_id`) COMMENT '支持访问路径：reopens_close_id',
  KEY `idx_period_close_status` (`status`, `create_time`) COMMENT '支持访问路径：status、create_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='控制会计期间关账或重开流程、活动槽、试算水位和固定双人审批内容';

CREATE TABLE `t_period_close_book` (
  `id` BIGINT NOT NULL COMMENT '关账账簿范围记录的 Snowflake 主键。',
  `period_close_id` BIGINT NOT NULL COMMENT '该账簿范围所属的本库关账或重开流程。',
  `ledger_book_id` BIGINT NOT NULL COMMENT '本流程必须独立关账或重开的本库经营主体币种账簿。',
  `as_of_sequence` BIGINT NOT NULL COMMENT '本流程针对该账簿锁定的最大 Journal Sequence。',
  `final_trial_run_id` BIGINT NULL COMMENT '该账簿在最终复核步骤采用且必须成功平衡的试算运行。',
  `posting_instruction_id` BIGINT NULL COMMENT '该账簿由本流程生成的 CLOSING 或 REOPENING 会计指令。',
  `status` VARCHAR(64) NOT NULL COMMENT '该账簿水位是否已经完成最终试算和结转证据冻结。允许值：LOCKED、FINALIZED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_period_close_book` (`period_close_id`, `ledger_book_id`) COMMENT '保证业务唯一性：period_close_id、ledger_book_id',
  UNIQUE KEY `uk_close_book_trial` (`final_trial_run_id`) COMMENT '保证业务唯一性：final_trial_run_id',
  UNIQUE KEY `uk_close_book_instr` (`posting_instruction_id`) COMMENT '保证业务唯一性：posting_instruction_id',
  KEY `idx_close_book_ledger` (`ledger_book_id`, `status`) COMMENT '支持访问路径：ledger_book_id、status',
  KEY `idx_close_book_status` (`period_close_id`, `status`) COMMENT '支持访问路径：period_close_id、status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='逐账簿冻结关账或重开的 Journal Sequence 水位、最终试算和平衡结转指令；FINALIZED 必须在同一事务重验同一账簿、期间和水位的成功 Final Trial、无开放异常、对应 Journal、同水位期间余额及七个关账步骤全部成功';

CREATE TABLE `t_close_step` (
  `id` BIGINT NOT NULL COMMENT '关账执行步骤的 Snowflake 主键。',
  `period_close_id` BIGINT NOT NULL COMMENT '该步骤所属的本库关账或重开流程。',
  `step_no` INTEGER NOT NULL COMMENT '同一流程内按固定定义连续递增的步骤序号。',
  `step_type` VARCHAR(64) NOT NULL COMMENT '关账或重开流程中的固定执行步骤；CHECK_ACCOUNT_POSTING_PROGRESS 在一个步骤内做 billing → account、account → ledger 两段进度检查（决定第 9 条）。允许值：LOCK_SEQUENCE、CHECK_ASYNC_AND_JOURNAL、CHECK_ACCOUNT_POSTING_PROGRESS、GENERATE_TRIAL_BALANCE、RESOLVE_EXCEPTIONS、POST_PROFIT_CLOSE、FINAL_TRIAL_BALANCE、PLAN_REOPEN_REVERSAL、POST_REOPEN_REVERSAL。',
  `status` VARCHAR(64) NOT NULL COMMENT '关账步骤当前执行状态。允许值：PENDING、RUNNING、SUCCEEDED、FAILED、SKIPPED。',
  `result_hash` CHAR(64) NULL COMMENT '本步骤输入和确定性结果的 Canonical SHA-256 哈希。',
  `completed_time` DATETIME(6) NULL COMMENT '步骤进入成功、失败或跳过终态的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_close_step` (`period_close_id`, `step_no`) COMMENT '保证业务唯一性：period_close_id、step_no',
  UNIQUE KEY `uk_close_step_type` (`period_close_id`, `step_type`) COMMENT '保证业务唯一性：period_close_id、step_type',
  KEY `idx_close_step_status` (`period_close_id`, `status`) COMMENT '支持访问路径：period_close_id、status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='按固定顺序记录数据完整性、对账、试算、损益结转和最终复核步骤及结果';

CREATE TABLE `t_close_approval` (
  `id` BIGINT NOT NULL COMMENT '关账或重开审批记录的 Snowflake 主键。',
  `period_close_id` BIGINT NOT NULL COMMENT '被审批的本库关账或重开流程。',
  `approval_role` VARCHAR(64) NOT NULL COMMENT '固定双人控制中的提交或复核角色。允许值：MAKER、CHECKER。',
  `actor_id` BIGINT NOT NULL COMMENT 'Platform 身份系统中的审批操作人标识。',
  `decision` VARCHAR(64) NOT NULL COMMENT '对关账或重开流程作出的批准或拒绝决定。允许值：APPROVED、REJECTED。',
  `approved_close_hash` CHAR(64) NOT NULL COMMENT '审批人实际审阅且必须等于流程关账内容哈希的值。',
  `decision_time` DATETIME(6) NOT NULL COMMENT '审批决定发生的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_close_approval` (`period_close_id`, `approval_role`) COMMENT '保证业务唯一性：period_close_id、approval_role'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='追加记录关账或重开流程固定 Maker-Checker 审批及实际批准的关账内容哈希';

CREATE TABLE `t_close_account_rule` (
  `id` BIGINT NOT NULL COMMENT '关账科目映射规则的 Snowflake 主键。',
  `chart_id` BIGINT NOT NULL COMMENT '该关账映射适用的本库稳定会计科目表。',
  `source_account_id` BIGINT NOT NULL COMMENT '需要在关账时结转的 Revenue 或 Expense 科目定义。',
  `current_result_account_id` BIGINT NOT NULL COMMENT '接收当期收入或费用结转的 Equity 科目定义。',
  `retained_earnings_account_id` BIGINT NOT NULL COMMENT '接收累计期间结果结转的 Equity 科目定义。',
  `effective_from` DATETIME(6) NOT NULL COMMENT '该关账科目映射开始生效的 UTC 微秒时间。',
  `effective_to` DATETIME(6) NULL COMMENT '该关账科目映射停止生效的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_close_rule` (`chart_id`, `source_account_id`, `effective_from`) COMMENT '保证业务唯一性：chart_id、source_account_id、effective_from',
  KEY `idx_close_source_account` (`source_account_id`) COMMENT '支持访问路径：source_account_id',
  KEY `idx_close_current_result` (`current_result_account_id`) COMMENT '支持访问路径：current_result_account_id',
  KEY `idx_close_retained` (`retained_earnings_account_id`) COMMENT '支持访问路径：retained_earnings_account_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='不可变地定义 Revenue 或 Expense 科目向本期损益及留存收益科目的有效期映射';

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

INSERT INTO `t_currency_spec` (`id`, `currency`, `minor_unit`, `rounding_mode`, `status`, `create_time`, `update_time`)
VALUES (1001, 'USD', 2, 'HALF_UP', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO `t_chart_of_accounts` (`id`, `chart_code`, `chart_name`, `status`, `create_time`, `update_time`)
VALUES (2001, 'STANDARD', 'Code-MoR 标准会计科目表', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO `t_chart_account`
  (`id`, `chart_id`, `parent_account_id`, `account_code`, `account_name`, `account_class`, `normal_side`, `posting_allowed`, `create_time`, `update_time`)
VALUES
  (3001, 2001, NULL, 'CHANNEL_UNSETTLED_RECEIVABLE',  '渠道待清算应收',       'ASSET',     'DEBIT',  1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3002, 2001, NULL, 'CHANNEL_SETTLEMENT_RECEIVABLE', '渠道结算应收',         'ASSET',     'DEBIT',  1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3003, 2001, NULL, 'BANK_CASH',                     '银行现金',             'ASSET',     'DEBIT',  1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3004, 2001, NULL, 'CLIENT_RECOVERY_RECEIVABLE',    'Client 追偿应收',      'ASSET',     'DEBIT',  1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3005, 2001, NULL, 'PARTNER_RECOVERY_RECEIVABLE',   'Local Partner 追偿应收', 'ASSET',   'DEBIT',  1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3006, 2001, NULL, 'TAX_RECOVERABLE',               '可抵扣进项税',         'ASSET',     'DEBIT',  1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3007, 2001, NULL, 'RECON_SUSPENSE_ASSET',          '对账悬挂资产',         'ASSET',     'DEBIT',  1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3008, 2001, NULL, 'PLATFORM_SERVICE_RECEIVABLE',   '平台服务费应收',       'ASSET',     'DEBIT',  1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3009, 2001, NULL, 'CLIENT_PAYABLE',                'Client 应付款',        'LIABILITY', 'CREDIT', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3010, 2001, NULL, 'PARTNER_PAYABLE',               'Local Partner 应付款', 'LIABILITY', 'CREDIT', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3011, 2001, NULL, 'CLIENT_SECURITY_DEPOSIT',       'Client 保证金',        'LIABILITY', 'CREDIT', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3012, 2001, NULL, 'TAX_PAYABLE',                   '应交税费',             'LIABILITY', 'CREDIT', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3013, 2001, NULL, 'CHANNEL_FEE_ACCRUAL',           '渠道费用预提',         'LIABILITY', 'CREDIT', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3014, 2001, NULL, 'CHANNEL_UNSETTLED_PAYABLE',     '渠道待清算应付',       'LIABILITY', 'CREDIT', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3015, 2001, NULL, 'CHANNEL_SETTLEMENT_PAYABLE',    '渠道结算应付',         'LIABILITY', 'CREDIT', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3016, 2001, NULL, 'UNIDENTIFIED_CASH',             '待识别资金',           'LIABILITY', 'CREDIT', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3017, 2001, NULL, 'CONSUMER_COMPENSATION_PAYABLE', 'Consumer 补偿应付',    'LIABILITY', 'CREDIT', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3018, 2001, NULL, 'PLATFORM_SERVICE_PAYABLE',      '平台服务费应付',       'LIABILITY', 'CREDIT', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3019, 2001, NULL, 'CAPITAL',                       '实收资本',             'EQUITY',    'CREDIT', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3020, 2001, NULL, 'CURRENT_PERIOD_RESULT',         '本期损益',             'EQUITY',    'CREDIT', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3021, 2001, NULL, 'RETAINED_EARNINGS',             '留存收益',             'EQUITY',    'CREDIT', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3022, 2001, NULL, 'OWNER_DISTRIBUTION',            '所有者分配',           'EQUITY',    'DEBIT',  1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3023, 2001, NULL, 'CLIENT_FEE_REVENUE',            'Client 手续费收入',    'REVENUE',   'CREDIT', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3024, 2001, NULL, 'PLATFORM_SERVICE_REVENUE',      '平台服务费收入',       'REVENUE',   'CREDIT', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3025, 2001, NULL, 'PLATFORM_SERVICE_FEE_EXPENSE',  '平台服务费费用',       'EXPENSE',   'DEBIT',  1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3026, 2001, NULL, 'PARTNER_SHARE_EXPENSE',         'Local Partner 分润费用', 'EXPENSE', 'DEBIT',  1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3027, 2001, NULL, 'CHANNEL_FEE_EXPENSE',           '渠道成本',             'EXPENSE',   'DEBIT',  1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3028, 2001, NULL, 'ROUNDING_ADJUSTMENT',           '舍入尾差',             'EXPENSE',   'DEBIT',  1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3029, 2001, NULL, 'RECON_ADJUSTMENT_EXPENSE',      '对账调整损失',         'EXPENSE',   'DEBIT',  1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

-- Period close: every REVENUE/EXPENSE leaf of STANDARD -> CURRENT_PERIOD_RESULT (3020) / RETAINED_EARNINGS (3021).
INSERT INTO `t_close_account_rule`
  (`id`, `chart_id`, `source_account_id`, `current_result_account_id`, `retained_earnings_account_id`, `effective_from`, `effective_to`, `create_time`)
VALUES
  (4001, 2001, 3023, 3020, 3021, '2000-01-01 00:00:00.000000', NULL, UTC_TIMESTAMP(6)),
  (4002, 2001, 3024, 3020, 3021, '2000-01-01 00:00:00.000000', NULL, UTC_TIMESTAMP(6)),
  (4003, 2001, 3025, 3020, 3021, '2000-01-01 00:00:00.000000', NULL, UTC_TIMESTAMP(6)),
  (4004, 2001, 3026, 3020, 3021, '2000-01-01 00:00:00.000000', NULL, UTC_TIMESTAMP(6)),
  (4005, 2001, 3027, 3020, 3021, '2000-01-01 00:00:00.000000', NULL, UTC_TIMESTAMP(6)),
  (4006, 2001, 3028, 3020, 3021, '2000-01-01 00:00:00.000000', NULL, UTC_TIMESTAMP(6)),
  (4007, 2001, 3029, 3020, 3021, '2000-01-01 00:00:00.000000', NULL, UTC_TIMESTAMP(6));

-- Automatic posting: STANDARD_TRANSACTION bundle version 1 (ACTIVE), see requirements/20261016/finance-交易自动记账/technique.md §7.
INSERT INTO `t_accounting_rule_bundle` (`id`, `bundle_code`, `current_ver_id`, `status`, `version`, `create_time`, `update_time`)
VALUES (5001, 'STANDARD_TRANSACTION', 5101, 'ACTIVE', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO `t_accounting_rule_bundle_ver`
  (`id`, `bundle_id`, `bundle_ver`, `effective_from`, `effective_to`, `status`, `maker_id`, `checker_id`, `approved_time`, `create_time`, `update_time`)
VALUES (5101, 5001, 1, '2000-01-01 00:00:00.000000', NULL, 'ACTIVE', 1, 2, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO `t_accounting_rule_line`
  (`id`, `bundle_ver_id`, `instruction_type`, `line_no`, `line_code`, `amount_source_code`, `owner_role`, `account_code`, `balance_bucket`, `entry_side`, `required_flag`, `create_time`)
VALUES
  (5201, 5101, 'PAYMENT_SUCCEEDED',                1, 'GROSS_RECEIVABLE',         'GROSS_AMOUNT',            'CHANNEL_ACCOUNT',         'CHANNEL_UNSETTLED_RECEIVABLE', 'NONE',    'DEBIT',  1, UTC_TIMESTAMP(6)),
  (5203, 5101, 'PAYMENT_SUCCEEDED',                3, 'CLIENT_FEE_REVENUE',       'CLIENT_FEE',              'BOOK_ENTITY',             'CLIENT_FEE_REVENUE',           'NONE',    'CREDIT', 1, UTC_TIMESTAMP(6)),
  (5204, 5101, 'PAYMENT_SUCCEEDED',                4, 'SELLER_TAX_PAYABLE',       'SELLER_TAX',              'TAX_AUTHORITY',           'TAX_PAYABLE',                  'NONE',    'CREDIT', 0, UTC_TIMESTAMP(6)),
  (5205, 5101, 'PAYMENT_SUCCEEDED',                5, 'CHANNEL_COST_EXPENSE',     'CHANNEL_COST',            'BOOK_ENTITY',             'CHANNEL_FEE_EXPENSE',          'NONE',    'DEBIT',  0, UTC_TIMESTAMP(6)),
  (5206, 5101, 'PAYMENT_SUCCEEDED',                6, 'CHANNEL_COST_ACCRUAL',     'CHANNEL_COST',            'CHANNEL_ACCOUNT',         'CHANNEL_FEE_ACCRUAL',          'NONE',    'CREDIT', 0, UTC_TIMESTAMP(6)),
  (5207, 5101, 'PAYMENT_SUCCEEDED',                7, 'PARTNER_SHARE_EXPENSE',    'PARTNER_SHARE',           'BOOK_ENTITY',             'PARTNER_SHARE_EXPENSE',        'NONE',    'DEBIT',  0, UTC_TIMESTAMP(6)),
  (5209, 5101, 'PLATFORM_SERVICE_CHARGE_PARTNER',  1, 'SERVICE_FEE_EXPENSE',      'PLATFORM_SERVICE_CHARGE', 'BOOK_ENTITY',             'PLATFORM_SERVICE_FEE_EXPENSE', 'NONE',    'DEBIT',  0, UTC_TIMESTAMP(6)),
  (5210, 5101, 'PLATFORM_SERVICE_CHARGE_PARTNER',  2, 'SERVICE_PAYABLE',          'PLATFORM_SERVICE_CHARGE', 'PLATFORM_FINANCE_ENTITY', 'PLATFORM_SERVICE_PAYABLE',     'NONE',    'CREDIT', 0, UTC_TIMESTAMP(6)),
  (5211, 5101, 'PLATFORM_SERVICE_CHARGE_PLATFORM', 1, 'SERVICE_RECEIVABLE',       'PLATFORM_SERVICE_CHARGE', 'TRANSACTION_OE',          'PLATFORM_SERVICE_RECEIVABLE',  'NONE',    'DEBIT',  0, UTC_TIMESTAMP(6)),
  (5212, 5101, 'PLATFORM_SERVICE_CHARGE_PLATFORM', 2, 'SERVICE_REVENUE',          'PLATFORM_SERVICE_CHARGE', 'BOOK_ENTITY',             'PLATFORM_SERVICE_REVENUE',     'NONE',    'CREDIT', 0, UTC_TIMESTAMP(6));

-- Economic-fact adjustments: STANDARD_ADJUSTMENT bundle version 1 (ACTIVE), refactor step 11b (design/04 §4 row 3).
-- MANUAL_ADJUSTMENT facts post as ECONOMIC_ADJUSTMENT, COST_ADJUSTMENT facts as CHANNEL_COST_ADJUSTMENT, both in the fact's OE book.
-- Each fact line posts twice: the payer side as DEBIT and the payee side as CREDIT (swapped when direction = REVERSAL);
-- a CLIENT or LOCAL_PARTNER side is not rule-driven: it is the account posting entry booked to the book's control account.
-- A rule line matches when amount_source_code = the line's fee type, owner_role = the party role's owner role
-- (CHANNEL -> CHANNEL_ACCOUNT, OPERATING_ENTITY -> TRANSACTION_OE, FINANCE_ENTITY -> PLATFORM_FINANCE_ENTITY,
-- TAX_AUTHORITY) and entry_side = the side; required_flag is not used for adjustments. Any unmatched combination rejects the fact.
INSERT INTO `t_accounting_rule_bundle` (`id`, `bundle_code`, `current_ver_id`, `status`, `version`, `create_time`, `update_time`)
VALUES (5002, 'STANDARD_ADJUSTMENT', 5102, 'ACTIVE', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO `t_accounting_rule_bundle_ver`
  (`id`, `bundle_id`, `bundle_ver`, `effective_from`, `effective_to`, `status`, `maker_id`, `checker_id`, `approved_time`, `create_time`, `update_time`)
VALUES (5102, 5002, 1, '2000-01-01 00:00:00.000000', NULL, 'ACTIVE', 1, 2, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO `t_accounting_rule_line`
  (`id`, `bundle_ver_id`, `instruction_type`, `line_no`, `line_code`, `amount_source_code`, `owner_role`, `account_code`, `balance_bucket`, `entry_side`, `required_flag`, `create_time`)
VALUES
  (5301, 5102, 'ECONOMIC_ADJUSTMENT', 1, 'OE_CLIENT_PRINCIPAL_DR', 'CLIENT_PRINCIPAL', 'TRANSACTION_OE', 'RECON_ADJUSTMENT_EXPENSE', 'NONE', 'DEBIT', 0, UTC_TIMESTAMP(6)),
  (5302, 5102, 'ECONOMIC_ADJUSTMENT', 2, 'OE_CLIENT_PRINCIPAL_CR', 'CLIENT_PRINCIPAL', 'TRANSACTION_OE', 'RECON_ADJUSTMENT_EXPENSE', 'NONE', 'CREDIT', 0, UTC_TIMESTAMP(6)),
  (5303, 5102, 'ECONOMIC_ADJUSTMENT', 3, 'OE_CLIENT_FEE_DR', 'CLIENT_FEE', 'TRANSACTION_OE', 'CLIENT_FEE_REVENUE', 'NONE', 'DEBIT', 0, UTC_TIMESTAMP(6)),
  (5304, 5102, 'ECONOMIC_ADJUSTMENT', 4, 'OE_CLIENT_FEE_CR', 'CLIENT_FEE', 'TRANSACTION_OE', 'CLIENT_FEE_REVENUE', 'NONE', 'CREDIT', 0, UTC_TIMESTAMP(6)),
  (5305, 5102, 'ECONOMIC_ADJUSTMENT', 5, 'OE_PARTNER_SHARE_DR', 'PARTNER_SHARE', 'TRANSACTION_OE', 'PARTNER_SHARE_EXPENSE', 'NONE', 'DEBIT', 0, UTC_TIMESTAMP(6)),
  (5306, 5102, 'ECONOMIC_ADJUSTMENT', 6, 'OE_PARTNER_SHARE_CR', 'PARTNER_SHARE', 'TRANSACTION_OE', 'PARTNER_SHARE_EXPENSE', 'NONE', 'CREDIT', 0, UTC_TIMESTAMP(6)),
  (5307, 5102, 'ECONOMIC_ADJUSTMENT', 7, 'OE_GROSS_AMOUNT_DR', 'GROSS_AMOUNT', 'TRANSACTION_OE', 'RECON_ADJUSTMENT_EXPENSE', 'NONE', 'DEBIT', 0, UTC_TIMESTAMP(6)),
  (5308, 5102, 'ECONOMIC_ADJUSTMENT', 8, 'OE_GROSS_AMOUNT_CR', 'GROSS_AMOUNT', 'TRANSACTION_OE', 'RECON_ADJUSTMENT_EXPENSE', 'NONE', 'CREDIT', 0, UTC_TIMESTAMP(6)),
  (5309, 5102, 'ECONOMIC_ADJUSTMENT', 9, 'OE_CHANNEL_COST_DR', 'CHANNEL_COST', 'TRANSACTION_OE', 'CHANNEL_FEE_EXPENSE', 'NONE', 'DEBIT', 0, UTC_TIMESTAMP(6)),
  (5310, 5102, 'ECONOMIC_ADJUSTMENT', 10, 'OE_CHANNEL_COST_CR', 'CHANNEL_COST', 'TRANSACTION_OE', 'CHANNEL_FEE_EXPENSE', 'NONE', 'CREDIT', 0, UTC_TIMESTAMP(6)),
  (5311, 5102, 'ECONOMIC_ADJUSTMENT', 11, 'OE_SELLER_TAX_DR', 'SELLER_TAX', 'TRANSACTION_OE', 'RECON_ADJUSTMENT_EXPENSE', 'NONE', 'DEBIT', 0, UTC_TIMESTAMP(6)),
  (5312, 5102, 'ECONOMIC_ADJUSTMENT', 12, 'OE_SELLER_TAX_CR', 'SELLER_TAX', 'TRANSACTION_OE', 'RECON_ADJUSTMENT_EXPENSE', 'NONE', 'CREDIT', 0, UTC_TIMESTAMP(6)),
  (5313, 5102, 'ECONOMIC_ADJUSTMENT', 13, 'OE_SERVICE_CHARGE_DR', 'PLATFORM_SERVICE_CHARGE', 'TRANSACTION_OE', 'PLATFORM_SERVICE_FEE_EXPENSE', 'NONE', 'DEBIT', 0, UTC_TIMESTAMP(6)),
  (5314, 5102, 'ECONOMIC_ADJUSTMENT', 14, 'OE_SERVICE_CHARGE_CR', 'PLATFORM_SERVICE_CHARGE', 'TRANSACTION_OE', 'PLATFORM_SERVICE_FEE_EXPENSE', 'NONE', 'CREDIT', 0, UTC_TIMESTAMP(6)),
  (5321, 5102, 'ECONOMIC_ADJUSTMENT', 21, 'CHANNEL_GROSS_DR', 'GROSS_AMOUNT', 'CHANNEL_ACCOUNT', 'CHANNEL_UNSETTLED_RECEIVABLE', 'NONE', 'DEBIT', 0, UTC_TIMESTAMP(6)),
  (5322, 5102, 'ECONOMIC_ADJUSTMENT', 22, 'CHANNEL_GROSS_CR', 'GROSS_AMOUNT', 'CHANNEL_ACCOUNT', 'CHANNEL_UNSETTLED_RECEIVABLE', 'NONE', 'CREDIT', 0, UTC_TIMESTAMP(6)),
  (5323, 5102, 'ECONOMIC_ADJUSTMENT', 23, 'CHANNEL_COST_DR', 'CHANNEL_COST', 'CHANNEL_ACCOUNT', 'CHANNEL_FEE_ACCRUAL', 'NONE', 'DEBIT', 0, UTC_TIMESTAMP(6)),
  (5324, 5102, 'ECONOMIC_ADJUSTMENT', 24, 'CHANNEL_COST_CR', 'CHANNEL_COST', 'CHANNEL_ACCOUNT', 'CHANNEL_FEE_ACCRUAL', 'NONE', 'CREDIT', 0, UTC_TIMESTAMP(6)),
  (5325, 5102, 'ECONOMIC_ADJUSTMENT', 25, 'FE_SERVICE_CHARGE_DR', 'PLATFORM_SERVICE_CHARGE', 'PLATFORM_FINANCE_ENTITY', 'PLATFORM_SERVICE_PAYABLE', 'NONE', 'DEBIT', 0, UTC_TIMESTAMP(6)),
  (5326, 5102, 'ECONOMIC_ADJUSTMENT', 26, 'FE_SERVICE_CHARGE_CR', 'PLATFORM_SERVICE_CHARGE', 'PLATFORM_FINANCE_ENTITY', 'PLATFORM_SERVICE_PAYABLE', 'NONE', 'CREDIT', 0, UTC_TIMESTAMP(6)),
  (5327, 5102, 'ECONOMIC_ADJUSTMENT', 27, 'TAX_SELLER_TAX_DR', 'SELLER_TAX', 'TAX_AUTHORITY', 'TAX_PAYABLE', 'NONE', 'DEBIT', 0, UTC_TIMESTAMP(6)),
  (5328, 5102, 'ECONOMIC_ADJUSTMENT', 28, 'TAX_SELLER_TAX_CR', 'SELLER_TAX', 'TAX_AUTHORITY', 'TAX_PAYABLE', 'NONE', 'CREDIT', 0, UTC_TIMESTAMP(6)),
  (5329, 5102, 'CHANNEL_COST_ADJUSTMENT', 1, 'CHANNEL_COST_DR', 'CHANNEL_COST', 'CHANNEL_ACCOUNT', 'CHANNEL_FEE_ACCRUAL', 'NONE', 'DEBIT', 0, UTC_TIMESTAMP(6)),
  (5330, 5102, 'CHANNEL_COST_ADJUSTMENT', 2, 'CHANNEL_COST_CR', 'CHANNEL_COST', 'CHANNEL_ACCOUNT', 'CHANNEL_FEE_ACCRUAL', 'NONE', 'CREDIT', 0, UTC_TIMESTAMP(6)),
  (5331, 5102, 'CHANNEL_COST_ADJUSTMENT', 3, 'OE_CHANNEL_COST_DR', 'CHANNEL_COST', 'TRANSACTION_OE', 'CHANNEL_FEE_EXPENSE', 'NONE', 'DEBIT', 0, UTC_TIMESTAMP(6)),
  (5332, 5102, 'CHANNEL_COST_ADJUSTMENT', 4, 'OE_CHANNEL_COST_CR', 'CHANNEL_COST', 'TRANSACTION_OE', 'CHANNEL_FEE_EXPENSE', 'NONE', 'CREDIT', 0, UTC_TIMESTAMP(6));
