-- Code-MoR Ledger initialization DML (moved from finance-service finance-initial-data.sql, step 07).
-- Apply manually to mor_ledger after ledger-schema.sql; the application and auto tests never execute this file.
-- Currency rules are not stored here: since 2026-10-20 ledger reads them from platform-support (in-memory snapshot) and
-- t_currency_spec was dropped (sql/changes/2026-10-20-drop-currency-spec-copy.sql).
-- Contents: the standard chart of accounts as a three-level tree (19 + 26 summary + 29 posting accounts),
-- the period-close transfer rules of every REVENUE/EXPENSE leaf account, and the STANDARD_TRANSACTION
-- automatic posting rule bundle version 1, plus (step 11b) the STANDARD_ADJUSTMENT rule bundle version 1 that posts
-- MANUAL_ADJUSTMENT and COST_ADJUSTMENT economic facts by payer / payee role
-- (agent-docs/database/FINANCE_DATABASE.md §4.1.1). Since 2026-10-19 (account subledger / ledger control accounts) the
-- bundles carry no Owner-role (CLIENT, LOCAL_PARTNER) lines: rows 5202, 5208 and 5315-5320 were removed and the Owner
-- control-account lines come from account posting entries (AccountPosted). Fixed IDs are reserved below 1,000,000 and never
-- produced by the Snowflake generator. Re-running fails on primary/unique keys by design.
SET NAMES utf8mb4;

INSERT INTO `t_chart_of_accounts` (`id`, `chart_code`, `chart_name`, `status`, `create_time`, `update_time`)
VALUES (2001, 'STANDARD', 'Code-MoR 标准会计科目表', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

-- STANDARD chart tree (2026-10-20 three levels, ledger 会计科目分级维护): 19 level-1 summary accounts 3101-3119 (4 digits),
-- 26 level-2 summary accounts 3201-3226 (6 digits), both posting_allowed = 0, and the 29 level-3 posting leaf accounts
-- 3001-3029 (8 digits); IDs and account codes of the leaves are unchanged. Level 2 and 3 inherit class and side from level 1.
INSERT INTO `t_chart_account`
  (`id`, `chart_id`, `parent_account_id`, `account_no`, `account_code`, `account_name`, `account_class`, `normal_side`, `posting_allowed`, `account_status`, `create_time`, `update_time`)
VALUES
  (3101, 2001, NULL, '1001',    'CASH_AND_BANK',                '现金及银行存款',         'ASSET',     'DEBIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3102, 2001, NULL, '1101',    'CHANNEL_RECEIVABLES',          '渠道应收款',             'ASSET',     'DEBIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3103, 2001, NULL, '1131',    'OTHER_RECEIVABLES',            '其他应收款',             'ASSET',     'DEBIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3104, 2001, NULL, '1171',    'TAX_RECEIVABLES',              '应收税费',               'ASSET',     'DEBIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3105, 2001, NULL, '1901',    'SUSPENSE_ASSETS',              '待处理资产',             'ASSET',     'DEBIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3106, 2001, NULL, '2201',    'CUSTOMER_FUNDS_PAYABLE',       '应付客户款项',           'LIABILITY', 'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3107, 2001, NULL, '2211',    'CUSTOMER_DEPOSITS',            '客户保证金',             'LIABILITY', 'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3108, 2001, NULL, '2221',    'TAXES_PAYABLE',                '应交税费',               'LIABILITY', 'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3109, 2001, NULL, '2231',    'CHANNEL_PAYABLES',             '渠道应付款',             'LIABILITY', 'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3110, 2001, NULL, '2241',    'ACCRUED_EXPENSES',             '预提费用',               'LIABILITY', 'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3111, 2001, NULL, '2251',    'OTHER_PAYABLES',               '其他应付款',             'LIABILITY', 'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3112, 2001, NULL, '2901',    'SUSPENSE_LIABILITIES',         '待处理负债',             'LIABILITY', 'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3113, 2001, NULL, '3001',    'SHARE_CAPITAL',                '实收资本',               'EQUITY',    'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3114, 2001, NULL, '3101',    'RETAINED_EARNINGS_TOTAL',      '留存收益',               'EQUITY',    'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3115, 2001, NULL, '3103',    'PROFIT_FOR_PERIOD',            '本期利润',               'EQUITY',    'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3116, 2001, NULL, '3201',    'DISTRIBUTIONS',                '所有者分配',             'EQUITY',    'DEBIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3117, 2001, NULL, '4001',    'OPERATING_REVENUE',            '营业收入',               'REVENUE',   'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3118, 2001, NULL, '5001',    'COST_OF_SERVICES',             '营业成本',               'EXPENSE',   'DEBIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3119, 2001, NULL, '5101',    'OTHER_LOSSES',                 '其他损失',               'EXPENSE',   'DEBIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3201, 2001, 3101, '100101',  'BANK_DEPOSITS',                '银行存款',               'ASSET',     'DEBIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3202, 2001, 3102, '110101',  'CHANNEL_CLEARING_RECEIVABLES', '渠道清算应收',           'ASSET',     'DEBIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3203, 2001, 3103, '113101',  'RECOVERY_RECEIVABLES',         '追偿应收',               'ASSET',     'DEBIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3204, 2001, 3103, '113102',  'INTERCOMPANY_RECEIVABLES',     '关联方应收',             'ASSET',     'DEBIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3205, 2001, 3104, '117101',  'INPUT_TAX',                    '进项税额',               'ASSET',     'DEBIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3206, 2001, 3105, '190101',  'RECON_SUSPENSE',               '对账待处理',             'ASSET',     'DEBIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3207, 2001, 3106, '220101',  'CLIENT_FUNDS_PAYABLE',         '应付 Client 款项',       'LIABILITY', 'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3208, 2001, 3106, '220102',  'PARTNER_FUNDS_PAYABLE',        '应付合作方款项',         'LIABILITY', 'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3209, 2001, 3106, '220103',  'CONSUMER_FUNDS_PAYABLE',       '应付 Consumer 款项',     'LIABILITY', 'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3210, 2001, 3107, '221101',  'CLIENT_DEPOSITS',              'Client 保证金',          'LIABILITY', 'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3211, 2001, 3108, '222101',  'OUTPUT_TAX',                   '销项税额',               'LIABILITY', 'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3212, 2001, 3109, '223101',  'CHANNEL_CLEARING_PAYABLES',    '渠道清算应付',           'LIABILITY', 'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3213, 2001, 3110, '224101',  'CHANNEL_FEE_ACCRUALS',         '渠道费用预提',           'LIABILITY', 'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3214, 2001, 3111, '225101',  'INTERCOMPANY_PAYABLES',        '关联方应付',             'LIABILITY', 'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3215, 2001, 3112, '290101',  'UNIDENTIFIED_RECEIPTS',        '待识别款项',             'LIABILITY', 'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3216, 2001, 3113, '300101',  'ORDINARY_CAPITAL',             '普通股本',               'EQUITY',    'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3217, 2001, 3114, '310101',  'UNDISTRIBUTED_PROFIT',         '未分配利润',             'EQUITY',    'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3218, 2001, 3115, '310301',  'PERIOD_RESULT',                '本期损益',               'EQUITY',    'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3219, 2001, 3116, '320101',  'OWNER_DISTRIBUTIONS',          '股东分配',               'EQUITY',    'DEBIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3220, 2001, 3117, '400101',  'TRANSACTION_SERVICE_REVENUE',  '交易服务收入',           'REVENUE',   'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3221, 2001, 3117, '400102',  'PLATFORM_SERVICE_INCOME',      '平台服务收入',           'REVENUE',   'CREDIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3222, 2001, 3118, '500101',  'CHANNEL_COSTS',                '渠道成本',               'EXPENSE',   'DEBIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3223, 2001, 3118, '500102',  'PARTNER_COSTS',                '合作方分成',             'EXPENSE',   'DEBIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3224, 2001, 3118, '500103',  'PLATFORM_SERVICE_COSTS',       '平台服务费用',           'EXPENSE',   'DEBIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3225, 2001, 3119, '510101',  'ROUNDING_DIFFERENCES',         '舍入差异',               'EXPENSE',   'DEBIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3226, 2001, 3119, '510102',  'RECONCILIATION_LOSSES',        '对账差异损失',           'EXPENSE',   'DEBIT', 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3003, 2001, 3201, '10010101', 'BANK_CASH',                    '银行现金',               'ASSET',     'DEBIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3001, 2001, 3202, '11010101', 'CHANNEL_UNSETTLED_RECEIVABLE', '渠道待清算应收',         'ASSET',     'DEBIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3002, 2001, 3202, '11010102', 'CHANNEL_SETTLEMENT_RECEIVABLE','渠道结算应收',           'ASSET',     'DEBIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3004, 2001, 3203, '11310101', 'CLIENT_RECOVERY_RECEIVABLE',   'Client 追偿应收',        'ASSET',     'DEBIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3005, 2001, 3203, '11310102', 'PARTNER_RECOVERY_RECEIVABLE',  'Local Partner 追偿应收', 'ASSET',     'DEBIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3008, 2001, 3204, '11310201', 'PLATFORM_SERVICE_RECEIVABLE',  '平台服务费应收',         'ASSET',     'DEBIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3006, 2001, 3205, '11710101', 'TAX_RECOVERABLE',              '可抵扣进项税',           'ASSET',     'DEBIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3007, 2001, 3206, '19010101', 'RECON_SUSPENSE_ASSET',         '对账悬挂资产',           'ASSET',     'DEBIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3009, 2001, 3207, '22010101', 'CLIENT_PAYABLE',               'Client 应付款',          'LIABILITY', 'CREDIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3010, 2001, 3208, '22010201', 'PARTNER_PAYABLE',              'Local Partner 应付款',   'LIABILITY', 'CREDIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3017, 2001, 3209, '22010301', 'CONSUMER_COMPENSATION_PAYABLE','Consumer 补偿应付',      'LIABILITY', 'CREDIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3011, 2001, 3210, '22110101', 'CLIENT_SECURITY_DEPOSIT',      'Client 保证金',          'LIABILITY', 'CREDIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3012, 2001, 3211, '22210101', 'TAX_PAYABLE',                  '应交税费',               'LIABILITY', 'CREDIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3014, 2001, 3212, '22310101', 'CHANNEL_UNSETTLED_PAYABLE',    '渠道待清算应付',         'LIABILITY', 'CREDIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3015, 2001, 3212, '22310102', 'CHANNEL_SETTLEMENT_PAYABLE',   '渠道结算应付',           'LIABILITY', 'CREDIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3013, 2001, 3213, '22410101', 'CHANNEL_FEE_ACCRUAL',          '渠道费用预提',           'LIABILITY', 'CREDIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3018, 2001, 3214, '22510101', 'PLATFORM_SERVICE_PAYABLE',     '平台服务费应付',         'LIABILITY', 'CREDIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3016, 2001, 3215, '29010101', 'UNIDENTIFIED_CASH',            '待识别资金',             'LIABILITY', 'CREDIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3019, 2001, 3216, '30010101', 'CAPITAL',                      '实收资本',               'EQUITY',    'CREDIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3021, 2001, 3217, '31010101', 'RETAINED_EARNINGS',            '留存收益',               'EQUITY',    'CREDIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3020, 2001, 3218, '31030101', 'CURRENT_PERIOD_RESULT',        '本期损益',               'EQUITY',    'CREDIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3022, 2001, 3219, '32010101', 'OWNER_DISTRIBUTION',           '所有者分配',             'EQUITY',    'DEBIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3023, 2001, 3220, '40010101', 'CLIENT_FEE_REVENUE',           'Client 手续费收入',      'REVENUE',   'CREDIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3024, 2001, 3221, '40010201', 'PLATFORM_SERVICE_REVENUE',     '平台服务费收入',         'REVENUE',   'CREDIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3027, 2001, 3222, '50010101', 'CHANNEL_FEE_EXPENSE',          '渠道成本',               'EXPENSE',   'DEBIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3026, 2001, 3223, '50010201', 'PARTNER_SHARE_EXPENSE',        'Local Partner 分润费用', 'EXPENSE',   'DEBIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3025, 2001, 3224, '50010301', 'PLATFORM_SERVICE_FEE_EXPENSE', '平台服务费费用',         'EXPENSE',   'DEBIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3028, 2001, 3225, '51010101', 'ROUNDING_ADJUSTMENT',          '舍入尾差',               'EXPENSE',   'DEBIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (3029, 2001, 3226, '51010201', 'RECON_ADJUSTMENT_EXPENSE',     '对账调整损失',           'EXPENSE',   'DEBIT', 1, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

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
