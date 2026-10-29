-- 2026-10-29 client-master：业务信息补采（DDL）。需求见 requirements/20261028/client-master-业务信息补采/（technique 第 9.1、9.2 节）。
-- 内容：
--   t_client_business_profile 新增 4 列（置于 business_description 之后，全部可空、无默认值、无索引）：
--     expected_payment_methods、refund_policy_url、fulfillment_type、fulfillment_days；
--   只改注释（类型、可空与默认不变）：t_onboarding_schema_field.widget_code（允许值加 MULTI_SELECT）、
--     t_onboarding_schema_field.catalog_code（补 COMMERCIAL_PAYMENT_METHOD 外部目录）、t_catalog_item.catalog_code（不得为 COMMERCIAL_ 开头）。
-- 执行顺序：本文件 → client-master 新镜像 → 两个 Web → 语言包 → 2026-10-29-business-info-collection.sql（Schema v3 DML）。
--   旧镜像不读写新列（新列全可空），本文件可先于新镜像执行；ADD COLUMN 与只改注释的 MODIFY COLUMN 在 MySQL 8 均即时完成。
-- 可重复执行：4 列已全部存在时跳过 ADD COLUMN（只存在部分列时 SIGNAL 停止，须人工核对）；注释 MODIFY 重跑结果相同。
-- 回退：changes/2026-10-29-business-info-collection-ddl-rollback.sql（须先回退 v3 DML；删除 4 列会丢失已采集的三项）。
SET NAMES utf8mb4;

-- ===== 1. 预检（只读）=====
SET @cm_bizinfo_ddl_columns = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_client_business_profile'
      AND COLUMN_NAME IN ('expected_payment_methods', 'refund_policy_url', 'fulfillment_type', 'fulfillment_days'));
SELECT @cm_bizinfo_ddl_columns AS present_columns_expect_0_or_4;

DROP PROCEDURE IF EXISTS `cm_bizinfo_ddl_assert`;
DELIMITER //
CREATE PROCEDURE `cm_bizinfo_ddl_assert`()
BEGIN
  IF @cm_bizinfo_ddl_columns NOT IN (0, 4) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: t_client_business_profile has only part of the 4 business-info columns; inspect before continuing';
  END IF;
END //
DELIMITER ;
CALL `cm_bizinfo_ddl_assert`();
DROP PROCEDURE IF EXISTS `cm_bizinfo_ddl_assert`;

-- ===== 2. 新增 4 列（已存在时跳过）=====
SET @cm_bizinfo_ddl = IF(@cm_bizinfo_ddl_columns = 0,
  'ALTER TABLE `t_client_business_profile`
     ADD COLUMN `expected_payment_methods` VARCHAR(512) NULL COMMENT ''Client 期望开通的支付方式：commercial 产品能力目录 capability_kind=PAYMENT_METHOD 的 capability_code，大写、去重、按代码升序、英文逗号分隔（如 APPLE_PAY,CARD），最多 20 项；草稿可空，提交按 Schema 要求级别校验且每项须为 ACTIVE；仅作审核与商务参考，不代表已开通。取值来源：commercial t_product_capability；非固定枚举。'' AFTER `business_description`,
     ADD COLUMN `refund_policy_url` VARCHAR(2048) NULL COMMENT ''Client 对外公示的退款政策页面 https 网址；草稿可空，机构申请批准前必填、个人申请可选（按 Schema 要求级别）；不校验页面内容。'' AFTER `expected_payment_methods`,
     ADD COLUMN `fulfillment_type` VARCHAR(32) NULL COMMENT ''Client 向消费者交付商品或服务的方式；草稿可空，提交必填。取值来源：t_catalog_item 目录 FULFILLMENT_TYPE（INSTANT、PRE_ORDER、SUBSCRIPTION、SERVICE）；非固定枚举。'' AFTER `refund_policy_url`,
     ADD COLUMN `fulfillment_days` SMALLINT NULL COMMENT ''预售从付款到交付的预计天数，1—365；只在 fulfillment_type=PRE_ORDER 时有值，改为其他交付方式时清空。'' AFTER `fulfillment_type`',
  'DO 0');
PREPARE `cm_bizinfo_stmt` FROM @cm_bizinfo_ddl;
EXECUTE `cm_bizinfo_stmt`;
DEALLOCATE PREPARE `cm_bizinfo_stmt`;

-- ===== 3. 注释（类型、可空与默认不变）=====
ALTER TABLE `t_onboarding_schema_field`
  MODIFY COLUMN `widget_code` VARCHAR(32) NOT NULL COMMENT '前端控件代码，由前端控件注册表解释。允许值：TEXT、TEXTAREA、DATE、COUNTRY、SELECT、MULTI_SELECT、IDENTIFIER、ADDRESS、NAME、PERSON_LIST、DOCUMENT_SLOT、DECLARATION、URL、MONEY、EMAIL、PHONE、BOOLEAN。',
  MODIFY COLUMN `catalog_code` VARCHAR(64) NULL COMMENT 'SELECT 与 MULTI_SELECT 控件的取值目录代码；REF_ 开头时为 Platform 参考数据（如 REF_CURRENCY）；COMMERCIAL_PAYMENT_METHOD 表示 commercial 产品能力目录的支付方式（外部目录，不在 t_catalog_item）；其他控件为空。取值来源：t_catalog_item.catalog_code、REF_ 保留码或 COMMERCIAL_PAYMENT_METHOD；非固定枚举。';
ALTER TABLE `t_catalog_item`
  MODIFY COLUMN `catalog_code` VARCHAR(64) NOT NULL COMMENT '目录代码，如 BUSINESS_MODEL、REGISTRATION_AUTHORITY、CITY；不得为 REF_ 开头的保留码或 COMMERCIAL_ 开头的外部目录码，也不得承载国家、币种、行政区或行业等 Platform 参考数据。取值来源：目录 DML；非固定枚举。';

-- ===== 4. 核验（只读）：4 列与 3 处注释 =====
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, ORDINAL_POSITION FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_client_business_profile'
   AND COLUMN_NAME IN ('business_description', 'expected_payment_methods', 'refund_policy_url', 'fulfillment_type', 'fulfillment_days')
 ORDER BY ORDINAL_POSITION;
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_COMMENT FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND ((TABLE_NAME = 't_onboarding_schema_field' AND COLUMN_NAME IN ('widget_code', 'catalog_code'))
    OR (TABLE_NAME = 't_catalog_item' AND COLUMN_NAME = 'catalog_code'));
