-- 2026-10-29 client-master：业务信息补采——回退 changes/2026-10-29-business-info-collection-ddl.sql。
-- 需求：requirements/20261028/client-master-业务信息补采/technique.md 第 9.2 节。
-- 前提：已执行 changes/2026-10-29-business-info-collection-rollback.sql（v3 Schema 已删除），且 client-master 已回退到不读写新列的镜像。
-- 效果：删除 t_client_business_profile 的 4 列（数据丢失：已采集的期望支付方式、退款政策链接、交付方式与天数不可恢复，执行前按需导出）；
--   3 处列注释恢复原文。
-- 停止条件（cm_bizinfo_ddl_rb_assert SIGNAL，尚未修改）：仍有 v3 Schema（ID 段 9281200000900000—9281200000999999）。
-- 幂等：列已不存在时跳过 DROP；注释恢复重跑结果相同。
SET NAMES utf8mb4;

-- ===== 1. 预检（只读）=====
SET @cm_bizinfo_ddl_rb_v3 = (SELECT COUNT(*) FROM `t_onboarding_schema` WHERE `schema_id` BETWEEN 9281200000900000 AND 9281200000999999);
SET @cm_bizinfo_ddl_rb_columns = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_client_business_profile'
      AND COLUMN_NAME IN ('expected_payment_methods', 'refund_policy_url', 'fulfillment_type', 'fulfillment_days'));
SELECT @cm_bizinfo_ddl_rb_v3 AS v3_schemas_expect_0, @cm_bizinfo_ddl_rb_columns AS present_columns;
-- 将丢失的已采集资料行数（列已删除时不查询）
SET @cm_bizinfo_ddl_rb_count = IF(@cm_bizinfo_ddl_rb_columns = 4,
  'SELECT COUNT(*) AS rows_with_collected_values FROM `t_client_business_profile` WHERE `expected_payment_methods` IS NOT NULL OR `refund_policy_url` IS NOT NULL OR `fulfillment_type` IS NOT NULL OR `fulfillment_days` IS NOT NULL',
  'SELECT 0 AS rows_with_collected_values');
PREPARE `cm_bizinfo_rb_count_stmt` FROM @cm_bizinfo_ddl_rb_count;
EXECUTE `cm_bizinfo_rb_count_stmt`;
DEALLOCATE PREPARE `cm_bizinfo_rb_count_stmt`;

DROP PROCEDURE IF EXISTS `cm_bizinfo_ddl_rb_assert`;
DELIMITER //
CREATE PROCEDURE `cm_bizinfo_ddl_rb_assert`()
BEGIN
  IF @cm_bizinfo_ddl_rb_v3 <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: v3 onboarding schemas still exist; run 2026-10-29-business-info-collection-rollback.sql first';
  END IF;
  IF @cm_bizinfo_ddl_rb_columns NOT IN (0, 4) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: t_client_business_profile has only part of the 4 business-info columns; inspect before continuing';
  END IF;
END //
DELIMITER ;
CALL `cm_bizinfo_ddl_rb_assert`();
DROP PROCEDURE IF EXISTS `cm_bizinfo_ddl_rb_assert`;

-- ===== 2. 删除 4 列（不存在时跳过）=====
SET @cm_bizinfo_ddl_rb = IF(@cm_bizinfo_ddl_rb_columns = 4,
  'ALTER TABLE `t_client_business_profile` DROP COLUMN `fulfillment_days`, DROP COLUMN `fulfillment_type`, DROP COLUMN `refund_policy_url`, DROP COLUMN `expected_payment_methods`',
  'DO 0');
PREPARE `cm_bizinfo_rb_stmt` FROM @cm_bizinfo_ddl_rb;
EXECUTE `cm_bizinfo_rb_stmt`;
DEALLOCATE PREPARE `cm_bizinfo_rb_stmt`;

-- ===== 3. 注释恢复原文 =====
ALTER TABLE `t_onboarding_schema_field`
  MODIFY COLUMN `widget_code` VARCHAR(32) NOT NULL COMMENT '前端控件代码，由前端控件注册表解释。允许值：TEXT、TEXTAREA、DATE、COUNTRY、SELECT、IDENTIFIER、ADDRESS、NAME、PERSON_LIST、DOCUMENT_SLOT、DECLARATION、URL、MONEY、EMAIL、PHONE、BOOLEAN。',
  MODIFY COLUMN `catalog_code` VARCHAR(64) NULL COMMENT 'SELECT 控件的取值目录代码；REF_ 开头时为 Platform 参考数据（如 REF_CURRENCY）；其他控件为空。取值来源：t_catalog_item.catalog_code 或 REF_ 保留码；非固定枚举。';
ALTER TABLE `t_catalog_item`
  MODIFY COLUMN `catalog_code` VARCHAR(64) NOT NULL COMMENT '目录代码，如 BUSINESS_MODEL、REGISTRATION_AUTHORITY、CITY；不得为 REF_ 开头的保留码，也不得承载国家、币种、行政区或行业等 Platform 参考数据。取值来源：目录 DML；非固定枚举。';

-- ===== 4. 核验（只读）：4 列不存在 =====
SELECT COUNT(*) AS business_info_columns_expect_0 FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_client_business_profile'
   AND COLUMN_NAME IN ('expected_payment_methods', 'refund_policy_url', 'fulfillment_type', 'fulfillment_days');
