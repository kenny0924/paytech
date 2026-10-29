-- =====================================================================================================================
-- Platform Support（mor_platform）· 回滚 2026-10-29 workflow 按钮发起与数据验证
-- 需求：requirements/20261028/platform-support-workflow按钮发起与数据验证/（technique.md §9.3）
-- 回滚对象：changes/2026-10-29-workflow-data-query.sql
--
-- 前提：新代码已停止（XXL 任务 workflowDataQueryRecheck 已停用、Platform 已回到旧镜像之前的停机窗口）。
--   旧代码不会再检查 DATA_QUERY 动作：执行前先人工处理仍 PENDING 的 DATA_QUERY 动作（按需终止所在实例）；
--   检查状态随列删除而丢失，active_slot 的防重保护随之撤销。
-- 可重跑：DROP 前检查 information_schema；MODIFY 与注释同值重复执行无副作用。
-- 执行账号：具备 ALTER / INDEX 权限的管理账号。由开发者审核后手工执行，应用不会执行本文件。
-- =====================================================================================================================

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- 0) 仍待检查的 DATA_QUERY 动作（应已人工处理；有行时确认后再继续）
SELECT `action_run_id`, `instance_id`, `action_key` FROM `t_workflow_action_run`
WHERE `action_type` = 'DATA_QUERY' AND `status` = 'PENDING';

-- 1) 实例：删唯一索引与槽位列，business_key 恢复表默认排序规则与原注释
SET @ddl = (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_workflow_instance` DROP INDEX `uk_workflow_inst_active`',
    'SELECT ''uk_workflow_inst_active absent'' AS skipped')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_workflow_instance' AND INDEX_NAME = 'uk_workflow_inst_active');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_workflow_instance` DROP COLUMN `active_slot`',
    'SELECT ''active_slot absent'' AS skipped')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_workflow_instance' AND COLUMN_NAME = 'active_slot');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `t_workflow_instance`
  MODIFY COLUMN `business_key` VARCHAR(128) NULL COMMENT '可选的业务关联键（如 CHANNEL:GEIDEA-SA），只用于检索和展示，工作流不解释其含义；未填写时为空。';

-- 2) 动作运行：删扫描索引与 5 个检查列，action_type 注释恢复
SET @ddl = (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_workflow_action_run` DROP INDEX `idx_workflow_action_check`',
    'SELECT ''idx_workflow_action_check absent'' AS skipped')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_workflow_action_run' AND INDEX_NAME = 'idx_workflow_action_check');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_workflow_action_run` DROP COLUMN `last_check_time`, DROP COLUMN `check_result`, DROP COLUMN `check_detail`, DROP COLUMN `check_failure_count`, DROP COLUMN `last_check_success_time`',
    'SELECT ''check columns absent'' AS skipped')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_workflow_action_run' AND COLUMN_NAME = 'last_check_time');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `t_workflow_action_run`
  MODIFY COLUMN `action_type` VARCHAR(32) NOT NULL COMMENT '动作的提交与验证方式，取自版本快照。允许值：FORM、FILE_UPLOAD、PAGE_TASK、MANUAL_CONFIRM。';

-- 3) 动作模板 action_type 注释恢复
ALTER TABLE `t_workflow_action_template`
  MODIFY COLUMN `action_type` VARCHAR(32) NOT NULL COMMENT '动作的提交与验证方式；DATA_QUERY 本期只预留，不能被发布的流程引用。允许值：FORM、FILE_UPLOAD、PAGE_TASK、MANUAL_CONFIRM、DATA_QUERY。';
