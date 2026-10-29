-- =====================================================================================================================
-- Platform Support（mor_platform）· 回滚 2026-10-30 多语言发布变更记录
-- 需求：requirements/20261030/platform-support-多语言发布变更记录/（technique.md 第 5.3、7 节）
-- 回滚对象：changes/2026-10-30-i18n-release-change.sql
--
-- 前提：新 Platform 已停止，执行后换回旧镜像（旧镜像不读写新表与新列，预检 / 发布 / 差异恢复为读语言包文件）。
-- 影响：删除全部发布变更明细与 change_count；语言包文件与发布登记不受影响。重新上线新镜像前须重新执行变更脚本与补录脚本。
-- 可重跑：删表用 IF EXISTS；删列前检查 information_schema。
-- 执行账号：具备 DROP / ALTER 权限的管理账号；由开发者审核后手工执行，应用不会执行本文件。
-- =====================================================================================================================

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- 1) 删除发布文案变化
DROP TABLE IF EXISTS `t_i18n_release_change`;

-- 2) 删除 t_i18n_release.change_count
SET @has_column := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_i18n_release' AND column_name = 'change_count');
SET @ddl := IF(@has_column > 0,
  'ALTER TABLE `t_i18n_release` DROP COLUMN `change_count`',
  'SELECT ''change_count absent'' AS skipped');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
