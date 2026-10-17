-- Code-MoR Ledger initialization DML (moved from finance-service finance-initial-data.sql, step 07).
-- Apply manually to mor_ledger after ledger-schema.sql; the application and auto tests never execute this file.
-- t_currency_spec is a read-only copy owned by platform-support (D41); its USD row is copied verbatim from
-- paytech/platform-support/docs/database/sql/platform-support-initial-data.sql.
-- Contents: USD currency rule, the standard chart of accounts and its 29 core posting accounts,
-- the period-close transfer rules of every REVENUE/EXPENSE leaf account, and the STANDARD_TRANSACTION
-- automatic posting rule bundle version 1
-- (agent-docs/database/FINANCE_DATABASE.md §4.1.1). Fixed IDs are reserved below 1,000,000 and never
-- produced by the Snowflake generator. Re-running fails on primary/unique keys by design.
SET NAMES utf8mb4;

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
  (5202, 5101, 'PAYMENT_SUCCEEDED',                2, 'CLIENT_PRINCIPAL_PAYABLE', 'CLIENT_PRINCIPAL',        'CLIENT',                  'CLIENT_PAYABLE',               'PENDING', 'CREDIT', 1, UTC_TIMESTAMP(6)),
  (5203, 5101, 'PAYMENT_SUCCEEDED',                3, 'CLIENT_FEE_REVENUE',       'CLIENT_FEE',              'BOOK_ENTITY',             'CLIENT_FEE_REVENUE',           'NONE',    'CREDIT', 1, UTC_TIMESTAMP(6)),
  (5204, 5101, 'PAYMENT_SUCCEEDED',                4, 'SELLER_TAX_PAYABLE',       'SELLER_TAX',              'TAX_AUTHORITY',           'TAX_PAYABLE',                  'NONE',    'CREDIT', 0, UTC_TIMESTAMP(6)),
  (5205, 5101, 'PAYMENT_SUCCEEDED',                5, 'CHANNEL_COST_EXPENSE',     'CHANNEL_COST',            'BOOK_ENTITY',             'CHANNEL_FEE_EXPENSE',          'NONE',    'DEBIT',  0, UTC_TIMESTAMP(6)),
  (5206, 5101, 'PAYMENT_SUCCEEDED',                6, 'CHANNEL_COST_ACCRUAL',     'CHANNEL_COST',            'CHANNEL_ACCOUNT',         'CHANNEL_FEE_ACCRUAL',          'NONE',    'CREDIT', 0, UTC_TIMESTAMP(6)),
  (5207, 5101, 'PAYMENT_SUCCEEDED',                7, 'PARTNER_SHARE_EXPENSE',    'PARTNER_SHARE',           'BOOK_ENTITY',             'PARTNER_SHARE_EXPENSE',        'NONE',    'DEBIT',  0, UTC_TIMESTAMP(6)),
  (5208, 5101, 'PAYMENT_SUCCEEDED',                8, 'PARTNER_SHARE_PAYABLE',    'PARTNER_SHARE',           'LOCAL_PARTNER',           'PARTNER_PAYABLE',              'PENDING', 'CREDIT', 0, UTC_TIMESTAMP(6)),
  (5209, 5101, 'PLATFORM_SERVICE_CHARGE_PARTNER',  1, 'SERVICE_FEE_EXPENSE',      'PLATFORM_SERVICE_CHARGE', 'BOOK_ENTITY',             'PLATFORM_SERVICE_FEE_EXPENSE', 'NONE',    'DEBIT',  0, UTC_TIMESTAMP(6)),
  (5210, 5101, 'PLATFORM_SERVICE_CHARGE_PARTNER',  2, 'SERVICE_PAYABLE',          'PLATFORM_SERVICE_CHARGE', 'PLATFORM_FINANCE_ENTITY', 'PLATFORM_SERVICE_PAYABLE',     'NONE',    'CREDIT', 0, UTC_TIMESTAMP(6)),
  (5211, 5101, 'PLATFORM_SERVICE_CHARGE_PLATFORM', 1, 'SERVICE_RECEIVABLE',       'PLATFORM_SERVICE_CHARGE', 'TRANSACTION_OE',          'PLATFORM_SERVICE_RECEIVABLE',  'NONE',    'DEBIT',  0, UTC_TIMESTAMP(6)),
  (5212, 5101, 'PLATFORM_SERVICE_CHARGE_PLATFORM', 2, 'SERVICE_REVENUE',          'PLATFORM_SERVICE_CHARGE', 'BOOK_ENTITY',             'PLATFORM_SERVICE_REVENUE',     'NONE',    'CREDIT', 0, UTC_TIMESTAMP(6));
