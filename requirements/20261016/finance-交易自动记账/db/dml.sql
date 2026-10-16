-- Finance 交易自动记账：预置 STANDARD_TRANSACTION 规则 Bundle 第 1 版（ACTIVE）。
-- 执行前核对：t_accounting_rule_bundle / _ver / _line 均为 0 行；执行后分别为 1 / 1 / 12 行。
-- 回滚：DELETE FROM t_accounting_rule_line WHERE id BETWEEN 5201 AND 5212;
--       DELETE FROM t_accounting_rule_bundle_ver WHERE id = 5101; DELETE FROM t_accounting_rule_bundle WHERE id = 5001;
SET NAMES utf8mb4;

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
