-- card-service 初始化数据（mor_card）。人工审阅后以 root 执行；应用与测试不执行本文件。
-- 前置：card-schema.sql（或 requirements/20261022/card-BIN库与卡属性/db/card/ddl.sql 增量）已执行，t_card_bin_library_version 为空。
--
-- 1. BIN 库种子版本（2026-10-22，requirements/20261022/card-BIN库与卡属性 db/card/dml.sql）：1 个 ACTIVE 版本（version_no=1）、
--    17 个号段、1 条激活记录。号段为合成数据与公开测试卡号段，issuer_name 一律为合成值，不代表真实发卡行；真实 BIN 数据到位后经运营控制台
--    导入新版本并激活，本种子版本随之变为 RETIRED。
--    ID：固定 ID 低于 Snowflake 范围（版本 1001，号段 1101 起，激活 1201），与 t_currency_spec 种子同一做法。
--    区域：按 Platform 国家区域映射手工推导（US→NORTH_AMERICA、GB→EUROPE、AE/SA→GCC、JP/CN→APAC）。
--    可重跑：否（主键冲突即失败）。回退：DELETE FROM t_card_bin_library_activation WHERE activation_id=1201;
--    DELETE FROM t_card_bin_range WHERE version_id=1001; DELETE FROM t_card_bin_library_version WHERE version_id=1001;
SET NAMES utf8mb4;
START TRANSACTION;
SET @card_bin_seed_time = UTC_TIMESTAMP(6);

INSERT INTO t_card_bin_library_version (version_id, version_no, status, active_slot, file_address, file_content_hash, file_name,
  total_rows, processed_rows, error_rows, failure_reason, created_by, version, create_time, update_time) VALUES
  (1001, 1, 'ACTIVE', 1, NULL, NULL, NULL, 17, 17, 0, NULL, NULL, 1, @card_bin_seed_time, @card_bin_seed_time);

INSERT INTO t_card_bin_range (range_id, version_id, bin_prefix, prefix_length, card_brand, funding_type, card_level, card_product,
  issuer_country, issuer_region, issuer_name, create_time, update_time) VALUES
  (1101, 1001, '411111',   6, 'VISA',       'CREDIT',  'CLASSIC',  'CONSUMER',   'US', 'NORTH_AMERICA', 'SYNTHETIC TEST ISSUER US',  @card_bin_seed_time, @card_bin_seed_time),
  (1102, 1001, '424242',   6, 'VISA',       'CREDIT',  'CLASSIC',  'CONSUMER',   'US', 'NORTH_AMERICA', 'SYNTHETIC TEST ISSUER US',  @card_bin_seed_time, @card_bin_seed_time),
  (1103, 1001, '401288',   6, 'VISA',       'CREDIT',  'GOLD',     'CONSUMER',   'US', 'NORTH_AMERICA', 'SYNTHETIC TEST ISSUER US',  @card_bin_seed_time, @card_bin_seed_time),
  (1104, 1001, '40128888', 8, 'VISA',       'DEBIT',   'CLASSIC',  'CONSUMER',   'GB', 'EUROPE',        'SYNTHETIC TEST ISSUER GB',  @card_bin_seed_time, @card_bin_seed_time),
  (1105, 1001, '400000',   6, 'VISA',       'DEBIT',   'CLASSIC',  'CONSUMER',   'AE', 'GCC',           'SYNTHETIC TEST ISSUER AE',  @card_bin_seed_time, @card_bin_seed_time),
  (1106, 1001, '555555',   6, 'MASTERCARD', 'CREDIT',  'WORLD',    'CONSUMER',   'US', 'NORTH_AMERICA', 'SYNTHETIC TEST ISSUER US',  @card_bin_seed_time, @card_bin_seed_time),
  (1107, 1001, '545454',   6, 'MASTERCARD', 'CREDIT',  'PLATINUM', 'CONSUMER',   'US', 'NORTH_AMERICA', 'SYNTHETIC TEST ISSUER US',  @card_bin_seed_time, @card_bin_seed_time),
  (1108, 1001, '222300',   6, 'MASTERCARD', 'CREDIT',  'WORLD',    'COMMERCIAL', 'US', 'NORTH_AMERICA', 'SYNTHETIC TEST ISSUER US',  @card_bin_seed_time, @card_bin_seed_time),
  (1109, 1001, '520082',   6, 'MASTERCARD', 'DEBIT',   NULL,       'CONSUMER',   'US', 'NORTH_AMERICA', 'SYNTHETIC TEST ISSUER US',  @card_bin_seed_time, @card_bin_seed_time),
  (1110, 1001, '543603',   6, 'MASTERCARD', 'DEBIT',   'CLASSIC',  'CONSUMER',   'AE', 'GCC',           'SYNTHETIC TEST ISSUER AE',  @card_bin_seed_time, @card_bin_seed_time),
  (1111, 1001, '378282',   6, 'AMEX',       'CHARGE',  NULL,       'CONSUMER',   'US', 'NORTH_AMERICA', 'SYNTHETIC TEST ISSUER US',  @card_bin_seed_time, @card_bin_seed_time),
  (1112, 1001, '371449',   6, 'AMEX',       'CREDIT',  NULL,       'CONSUMER',   'US', 'NORTH_AMERICA', 'SYNTHETIC TEST ISSUER US',  @card_bin_seed_time, @card_bin_seed_time),
  (1113, 1001, '601111',   6, 'DISCOVER',   'CREDIT',  NULL,       'CONSUMER',   'US', 'NORTH_AMERICA', 'SYNTHETIC TEST ISSUER US',  @card_bin_seed_time, @card_bin_seed_time),
  (1114, 1001, '353011',   6, 'JCB',        'CREDIT',  NULL,       'CONSUMER',   'JP', 'APAC',          'SYNTHETIC TEST ISSUER JP',  @card_bin_seed_time, @card_bin_seed_time),
  (1115, 1001, '620000',   6, 'UNIONPAY',   'DEBIT',   NULL,       'CONSUMER',   'CN', 'APAC',          'SYNTHETIC TEST ISSUER CN',  @card_bin_seed_time, @card_bin_seed_time),
  (1116, 1001, '305693',   6, 'DINERS',     'CREDIT',  NULL,       'CONSUMER',   'US', 'NORTH_AMERICA', 'SYNTHETIC TEST ISSUER US',  @card_bin_seed_time, @card_bin_seed_time),
  (1117, 1001, '588845',   6, 'MADA',       'DEBIT',   NULL,       'CONSUMER',   'SA', 'GCC',           'SYNTHETIC TEST ISSUER SA',  @card_bin_seed_time, @card_bin_seed_time);

INSERT INTO t_card_bin_library_activation (activation_id, version_id, previous_version_id, activation_type, operator_account_id,
  create_time, update_time) VALUES
  (1201, 1001, NULL, 'ACTIVATE', NULL, @card_bin_seed_time, @card_bin_seed_time);

COMMIT;
