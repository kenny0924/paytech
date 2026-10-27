-- 参考数据目录：mor_platform 回退（只在需要换回 2026-10-27 之前的 Platform 镜像时执行，需授权）。
-- 对应变更：changes/2026-10-27-reference-data.sql。目标库：mor_platform / mor_platform_e2e。
-- 前提：新 Platform 已停止；执行后立即换回旧 Platform 镜像（旧镜像只认 t_currency_spec / t_country_region）。
-- 影响：删除 t_ref_country_subdivision、t_ref_industry、t_ref_mcc 及其数据；删除币种与国家的新增列（数字码、名称、符号、logo、
--   区号、法定货币、国家状态与版本），并删除本变更以固定 ID 1002—1004 插入的 AED / SAR / EGP。
--   运营用 Snowflake ID 新建的币种保留；本变更把已存在的 AED / SAR / EGP 改为 ACTIVE 的不还原（以执行前备份为准人工处置）。
--   客户端已经保存的 AED / SAR / EGP 业务引用不在本库，回退前由发布负责人确认。
-- 执行：mysql 客户端整文件执行；预检不满足时 SIGNAL 中止，此时尚未改动。
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `ref_data_rollback_assert`;
DELIMITER //
CREATE PROCEDURE `ref_data_rollback_assert`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 't_ref_currency')
     OR NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 't_ref_country') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: t_ref_currency / t_ref_country missing; nothing to roll back';
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE()
              AND table_name IN ('t_currency_spec', 't_country_region')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'STOP: t_currency_spec / t_country_region already exist';
  END IF;
END //
DELIMITER ;
CALL `ref_data_rollback_assert`();

DROP TABLE IF EXISTS `t_ref_mcc`, `t_ref_industry`, `t_ref_country_subdivision`;

DELETE FROM `t_ref_currency` WHERE `id` IN (1002, 1003, 1004) AND `currency_code` IN ('AED', 'SAR', 'EGP');

ALTER TABLE `t_ref_country`
  DROP INDEX `uk_ref_country_code`,
  ADD UNIQUE KEY `uk_country_region_country` (`country_code`) COMMENT '保证业务唯一性：country_code',
  DROP COLUMN `alpha3_code`, DROP COLUMN `numeric_code`, DROP COLUMN `name`, DROP COLUMN `calling_code`,
  DROP COLUMN `default_currency_code`, DROP COLUMN `logo_file_address`, DROP COLUMN `status`, DROP COLUMN `version`,
  COMMENT = '保存全平台使用的国家到地理区域映射；Platform 唯一持有，Card 导入 BIN 库时经内部 HTTP 读取并推导发卡区域。本期只经初始化数据维护，不提供维护页面。';
RENAME TABLE `t_ref_country` TO `t_country_region`;

ALTER TABLE `t_ref_currency`
  DROP INDEX `uk_ref_currency_code`,
  DROP COLUMN `numeric_code`, DROP COLUMN `name`, DROP COLUMN `symbol`, DROP COLUMN `logo_file_address`,
  RENAME COLUMN `currency_code` TO `currency`,
  COMMENT = '保存全平台使用的 ISO 4217 币种精度、舍入方式和启停状态；Platform 唯一持有，ledger、billing 经内部 HTTP 读取并在进程内缓存快照。';
ALTER TABLE `t_ref_currency`
  ADD UNIQUE KEY `uk_currency` (`currency`) COMMENT '保证业务唯一性：currency';
RENAME TABLE `t_ref_currency` TO `t_currency_spec`;

DROP PROCEDURE IF EXISTS `ref_data_rollback_assert`;
-- 执行后核对：SHOW CREATE TABLE t_currency_spec; SHOW CREATE TABLE t_country_region; 与 2026-10-22 之后、本变更之前的 schema 一致。
