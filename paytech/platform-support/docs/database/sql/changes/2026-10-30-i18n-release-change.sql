-- =====================================================================================================================
-- Platform Support（mor_platform）· 2026-10-30 多语言发布变更记录
-- 需求：requirements/20261030/platform-support-多语言发布变更记录/（technique.md 第 4、5 节）
--
-- 内容：
--   1) 新表 t_i18n_release_change：每次发布相对上一发布号的文案变化（首个发布为相对空内容的全量基线）。
--   2) t_i18n_release 新增 change_count：该发布登记的变化条数；NULL 表示尚未生成变更明细（历史发布待补录）。
--
-- 兼容：纯新增；旧 Platform 不读写新表与新列，照常运行。
-- 部署顺序：本脚本 → 一次性补录脚本（为全部已有发布生成明细并写 change_count）→ Platform 新镜像。
-- 可重跑：建表用 IF NOT EXISTS；加列前检查 information_schema。
-- 回滚：同目录 2026-10-30-i18n-release-change-rollback.sql（新 Platform 停止后执行）。
-- 执行账号：具备 CREATE / ALTER 权限的管理账号；由开发者审核后手工执行，应用不会执行本文件。
-- =====================================================================================================================

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- 1) 发布文案变化
CREATE TABLE IF NOT EXISTS `t_i18n_release_change` (
  `change_id` BIGINT NOT NULL COMMENT '服务侧生成的文案变化 Snowflake 主键。',
  `application_id` BIGINT NOT NULL COMMENT '所属应用，取值为 t_i18n_application.application_id。',
  `release_no` INT NOT NULL COMMENT '发生变化的发布号，取值为同一应用的 t_i18n_release.release_no；变化相对该应用发布号减一的发布，发布号 1 相对空内容。',
  `locale_code` VARCHAR(35) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '文案语言，取值为 t_i18n_locale.locale_code；en 为英文基准。',
  `key_code` VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '完整 key，发布时的 t_i18n_key.key_code。',
  `change_type` VARCHAR(16) NOT NULL COMMENT '变化类型；ADDED 上一发布没有该文案，CHANGED 文字不同，REMOVED 本发布不再包含该文案。允许值：ADDED、CHANGED、REMOVED。',
  `text` TEXT NULL COMMENT '本发布中的文案（ICU MessageFormat）；change_type 为 REMOVED 时为 NULL。改前文字取同一应用、语言、key 在更小发布号中的最后一条变化。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间；创建后不可变，没有更新时间。',
  PRIMARY KEY (`change_id`),
  UNIQUE KEY `uk_i18n_release_change` (`application_id`, `locale_code`, `key_code`, `release_no`) COMMENT '保证业务唯一性：一次发布中每个语言、key 最多一条变化；同时支持按发布号还原版本内容',
  KEY `idx_i18n_change_release` (`application_id`, `release_no`) COMMENT '支持访问路径：按应用与发布号列出一次发布的变化'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存每次发布相对上一发布号的文案变化（新增、修改、移除），只记变化不记全量；按发布号顺序累积即可还原任意发布的完整文案，用于发布预检、发布摘要与版本差异，不读语言包文件。创建后不修改。';

-- 2) t_i18n_release.change_count（放在 key_count 之后）
SET @has_column := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_i18n_release' AND column_name = 'change_count');
SET @ddl := IF(@has_column = 0,
  'ALTER TABLE `t_i18n_release` ADD COLUMN `change_count` INT NULL COMMENT ''该发布在 t_i18n_release_change 中登记的变化条数（可为 0）；NULL 表示尚未生成变更明细，此时不能用明细还原该发布及其后的发布。'' AFTER `key_count`',
  'SELECT ''change_count exists'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
