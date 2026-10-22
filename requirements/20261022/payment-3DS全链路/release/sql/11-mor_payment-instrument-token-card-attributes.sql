-- 发布副本（3DS 全链路 + 卡 BIN 库合并发布，阶段 6）。目标库：mor_payment。来源：Code-MoR/requirements/20261022/card-BIN库与卡属性/db/payment/ddl.sql（SHA-256 eb3436f2fc1718b2，BIN 需求 db）。
-- 以下为来源文件全文逐字副本，未改动业务语句。
-- 未执行草案（2026-10-22 阶段 3 评审稿）：card：BIN 库与卡属性，目标库 mor_payment（payment-service）。
-- 性质：需求级增量 DDL；t_payment_instrument_token 追加 8 个可空列，存交易工具创建时冻结的卡属性快照。
-- 同步：PDManer payment.pdma.json（scripts/pdmaner/generate-payment-model.mjs、payment-execution-field-overrides.mjs，
--       校验 payment-model.test.mjs）、payment-schema.sql、PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md、测试库 DDL。
-- 顺序：须在新 Payment 镜像之前执行（旧镜像不读这些列，兼容）。可重跑：否。
-- 回退：ALTER TABLE t_payment_instrument_token DROP COLUMN bin_match_length, DROP COLUMN bin_library_version_no,
--       DROP COLUMN issuer_region, DROP COLUMN issuer_country, DROP COLUMN card_product, DROP COLUMN card_level,
--       DROP COLUMN funding_type, DROP COLUMN card_brand;（须先回退 Payment 镜像；已冻结的快照随之丢失）
SET NAMES utf8mb4;

ALTER TABLE `t_payment_instrument_token`
  ADD COLUMN `card_brand` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Card 创建卡工具时按 BIN 库冻结的卡组织；未识别或非卡工具为 NULL。用于 Risk 输入、路由 schemeCode 与 PaymentSucceeded。允许值：VISA、MASTERCARD、AMEX、UNIONPAY、MADA、DISCOVER、JCB、DINERS。' AFTER `card_fingerprint`,
  ADD COLUMN `funding_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '冻结的借贷记类型；未识别、数据未提供或非卡工具为 NULL。允许值：CREDIT、DEBIT、PREPAID、CHARGE。' AFTER `card_brand`,
  ADD COLUMN `card_level` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '冻结的卡等级；未识别、数据未提供或非卡工具为 NULL。取值来源：Card BIN 库；非固定枚举。' AFTER `funding_type`,
  ADD COLUMN `card_product` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '冻结的卡产品类别；未识别、数据未提供或非卡工具为 NULL。允许值：CONSUMER、COMMERCIAL。' AFTER `card_level`,
  ADD COLUMN `issuer_country` CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '冻结的发卡国 ISO 3166-1 alpha-2；未识别或非卡工具为 NULL。取值来源：ISO 3166-1 alpha-2；非固定枚举。' AFTER `card_product`,
  ADD COLUMN `issuer_region` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '冻结的发卡区域；未识别或非卡工具为 NULL。取值来源：Platform 国家区域映射 region_code；非固定枚举。' AFTER `issuer_country`,
  ADD COLUMN `bin_library_version_no` INT NULL COMMENT '查询时的 BIN 库生效版本号；没有生效版本或非卡工具为 NULL。识别与否都记录，用于追溯。' AFTER `issuer_region`,
  ADD COLUMN `bin_match_length` TINYINT NULL COMMENT '命中号段的前缀位数（6、7、8）；未识别或非卡工具为 NULL，NULL 与 bin_library_version_no 非空同时出现表示查询过但未识别。' AFTER `bin_library_version_no`;
