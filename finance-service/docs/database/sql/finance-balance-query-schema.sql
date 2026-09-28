-- Historical balance-query runtime slice; retained for existing Java/local database compatibility.
-- Current complete target schema: finance-schema.sql. Do not mix the two before a reviewed migration.
-- Finance 余额查询首个运行切片。人工审查并执行；应用和测试均不自动建表。
-- 字段、索引和注释来自当前 Finance PDManer。关系由应用代码保证，不创建 CHECK/FOREIGN KEY。
CREATE TABLE IF NOT EXISTS t_ledger_book (
  id BIGINT NOT NULL COMMENT '服务侧生成的总账账簿 Snowflake 主键',
  entity_type VARCHAR(64) NOT NULL COMMENT '账簿主体类型：OPERATING_ENTITY、FINANCE_ENTITY',
  entity_id BIGINT NOT NULL COMMENT '按主体类型解释的稳定业务ID',
  currency CHAR(3) NOT NULL COMMENT '账簿唯一使用的 ISO 4217 三位币种代码',
  chart_id BIGINT NOT NULL COMMENT '账簿采用的本库稳定会计科目表',
  book_status VARCHAR(64) NOT NULL COMMENT '账簿状态：OPEN、SUSPENDED、CLOSED',
  version BIGINT NOT NULL COMMENT '账簿状态乐观锁版本号',
  create_time DATETIME(6) NOT NULL COMMENT 'UTC 微秒创建时间',
  update_time DATETIME(6) NOT NULL COMMENT 'UTC 微秒更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ledger_book_entity (entity_type, entity_id, currency),
  KEY idx_ledger_book_chart (chart_id),
  KEY idx_ledger_book_status (book_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='总账账簿';

CREATE TABLE IF NOT EXISTS t_chart_account (
  id BIGINT NOT NULL COMMENT '服务侧生成的会计科目 Snowflake 主键',
  chart_id BIGINT NOT NULL COMMENT '该科目所属的本库会计科目表',
  parent_account_id BIGINT NULL COMMENT '直接上级科目，顶级科目为空',
  account_code VARCHAR(64) NOT NULL COMMENT '同一科目表内唯一的稳定会计科目代码',
  account_name VARCHAR(255) NOT NULL COMMENT '财务报表和记账界面使用的科目名称',
  account_class VARCHAR(64) NOT NULL COMMENT '科目分类：ASSET、LIABILITY、EQUITY、REVENUE、EXPENSE',
  normal_side VARCHAR(64) NOT NULL COMMENT '正常余额方向：DEBIT、CREDIT',
  posting_allowed BOOLEAN NOT NULL COMMENT '是否允许 Journal Line 直接记入该科目',
  create_time DATETIME(6) NOT NULL COMMENT 'UTC 微秒创建时间',
  update_time DATETIME(6) NOT NULL COMMENT 'UTC 微秒更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_chart_acct (chart_id, account_code),
  KEY idx_chart_acct_parent (parent_account_id),
  KEY idx_chart_acct_class (chart_id, account_class)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会计科目';

CREATE TABLE IF NOT EXISTS t_account (
  id BIGINT NOT NULL COMMENT '服务侧生成的统一记账账户 Snowflake 主键',
  ledger_book_id BIGINT NOT NULL COMMENT '账户严格归属的本库主体币种账簿',
  chart_account_id BIGINT NOT NULL COMMENT '账户使用且必须允许过账的会计科目',
  owner_type VARCHAR(64) NOT NULL COMMENT '核算对象类型',
  owner_id BIGINT NOT NULL COMMENT '按核算对象类型解释的稳定业务ID',
  balance_bucket VARCHAR(64) NOT NULL COMMENT '余额桶：PENDING、AVAILABLE、LOCKED、NONE',
  normal_side VARCHAR(64) NOT NULL COMMENT '从会计科目复制并冻结的正常余额方向',
  account_status VARCHAR(64) NOT NULL COMMENT '账户状态：ACTIVE、INACTIVE、CLOSED',
  debit_total DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '累计借方金额',
  credit_total DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '累计贷方金额',
  net_balance DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '按正常余额方向计算的有符号当前净余额',
  last_journal_sequence BIGINT NOT NULL DEFAULT 0 COMMENT '已应用到账户余额投影的最后分录序号',
  balance_version BIGINT NOT NULL DEFAULT 0 COMMENT '账户余额投影顺序条件更新的乐观锁版本',
  create_time DATETIME(6) NOT NULL COMMENT 'UTC 微秒创建时间',
  update_time DATETIME(6) NOT NULL COMMENT 'UTC 微秒更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_account_identity (ledger_book_id, chart_account_id, owner_type, owner_id, balance_bucket),
  KEY idx_account_owner (owner_type, owner_id, ledger_book_id, balance_bucket),
  KEY idx_account_chart_status (chart_account_id, ledger_book_id, account_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一记账账户';
