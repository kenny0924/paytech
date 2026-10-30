-- =====================================================================================================================
-- Platform Support（mor_platform）· 2026-10-29 联合查询 · 回滚
-- 先回退 Platform（新代码读写 joint_json 与 t_query_key_type）与注册块 144（2026-10-29-joint-query-registration-144-rollback.sql），
-- 再执行本文件。会丢失已填的联合查询配置（joint_json）与全部键类型，执行前确认无需保留。重复执行为空操作。
-- =====================================================================================================================
SET NAMES utf8mb4;

SET @ddl = (SELECT IF(COUNT(*) = 1,
    'ALTER TABLE `t_query_template_version` DROP COLUMN `joint_json`',
    'SELECT ''joint_json absent'' AS skipped')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_query_template_version' AND COLUMN_NAME = 'joint_json');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DROP TABLE IF EXISTS `t_query_key_type`;
