-- =====================================================================================================================
-- Platform Support（mor_platform）· 2026-10-29 workflow 按钮发起与数据验证
-- 需求：requirements/20261028/platform-support-workflow按钮发起与数据验证/（technique.md §9）
--
-- 内容：
--   1) t_workflow_instance：business_key 改为 utf8mb4_bin 并改注释；新增 active_slot（status 之后）；
--      回填 RUNNING 且 business_key 非空的实例 active_slot = 1；新增唯一索引 uk_workflow_inst_active。
--   2) t_workflow_action_run：action_type 注释补 DATA_QUERY；submitted_time 之后新增 5 个检查列；新增扫描索引
--      idx_workflow_action_check。
--   3) t_workflow_action_template：action_type 注释去掉“预留”。
--
-- 预检（第 0 步）必须返回 0 行；有行时停止执行并人工处理重复的 RUNNING 实例（终止其一）后再继续。
-- 可重跑：ADD 列 / 加索引前检查 information_schema；MODIFY 与注释同值重复执行无副作用；回填只改 active_slot 为空的行。
-- 部署顺序（technique.md §9.3）：本脚本 → Platform 新镜像；新列可空或有默认值，旧代码照常读写。
-- 回滚：同目录 2026-10-29-workflow-data-query-rollback.sql（新代码停止后执行）。
-- 执行账号：具备 ALTER / INDEX 权限的管理账号；platform_app 只有 DML 权限。由开发者审核后手工执行，应用不会执行本文件。
-- =====================================================================================================================

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- 0) 预检：同一定义下按原文（区分大小写）重复的 RUNNING business_key（必须返回 0 行）
SELECT `definition_id`, BINARY `business_key` AS `business_key`, COUNT(*) AS `running`
FROM `t_workflow_instance`
WHERE `status` = 'RUNNING' AND `business_key` IS NOT NULL
GROUP BY `definition_id`, BINARY `business_key` HAVING COUNT(*) > 1;

-- 1a) business_key 改排序规则与注释（同值重复执行无副作用）
ALTER TABLE `t_workflow_instance`
  MODIFY COLUMN `business_key` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '可选的业务关联键（如 CLIENT:880000000101），按原文精确匹配；给出时同一流程定义下只允许一个 RUNNING 实例（见 active_slot），重复发起返回已有实例；未填写时为空。';

-- 1b) 新增 active_slot（已存在则跳过）
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `t_workflow_instance` ADD COLUMN `active_slot` TINYINT NULL COMMENT ''业务关联键防重槽位：实例 RUNNING 且 business_key 非空时为 1，完成或终止时置空；与 definition_id、business_key 组成唯一索引。'' AFTER `status`',
    'SELECT ''active_slot exists'' AS skipped')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_workflow_instance' AND COLUMN_NAME = 'active_slot');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 回填槽位：只改尚未回填的行
START TRANSACTION;
UPDATE `t_workflow_instance` SET `active_slot` = 1
WHERE `status` = 'RUNNING' AND `business_key` IS NOT NULL AND `active_slot` IS NULL;
COMMIT;

-- 3) 唯一索引（已存在则跳过）
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `t_workflow_instance` ADD UNIQUE KEY `uk_workflow_inst_active` (`definition_id`, `business_key`, `active_slot`) COMMENT ''保证同一流程定义下同一业务关联键只有一个 RUNNING 实例''',
    'SELECT ''uk_workflow_inst_active exists'' AS skipped')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_workflow_instance' AND INDEX_NAME = 'uk_workflow_inst_active');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4a) 动作运行 action_type 注释
ALTER TABLE `t_workflow_action_run`
  MODIFY COLUMN `action_type` VARCHAR(32) NOT NULL COMMENT '动作的提交与验证方式，取自版本快照。允许值：FORM、FILE_UPLOAD、PAGE_TASK、MANUAL_CONFIRM、DATA_QUERY。';

-- 4b) 5 个检查列（以 last_check_time 判断是否已执行，五列同一语句加入）
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `t_workflow_action_run`
       ADD COLUMN `last_check_time` DATETIME(6) NULL COMMENT ''DATA_QUERY 最近一次检查结果落库的 UTC 微秒时间；其他类型与尚未检查时为空。'' AFTER `submitted_time`,
       ADD COLUMN `check_result` VARCHAR(32) NULL COMMENT ''DATA_QUERY 最近一次检查结果：SATISFIED 已满足（动作随之完成）；UNSATISFIED 查询成功但未满足；FAILED 查询失败、超时或返回形状不符。允许值：SATISFIED、UNSATISFIED、FAILED。'' AFTER `last_check_time`,
       ADD COLUMN `check_detail` VARCHAR(512) NULL COMMENT ''UNSATISFIED 时为查询返回的原因码（逗号分隔，可空）；FAILED 时为失败码；SATISFIED 时为空。'' AFTER `check_result`,
       ADD COLUMN `check_failure_count` INT NOT NULL DEFAULT 0 COMMENT ''DATA_QUERY 连续检查失败次数；查询成功执行（满足或未满足）时清零。'' AFTER `check_detail`,
       ADD COLUMN `last_check_success_time` DATETIME(6) NULL COMMENT ''DATA_QUERY 最近一次查询成功执行的 UTC 微秒时间（满足或未满足）；从未成功为空。'' AFTER `check_failure_count`',
    'SELECT ''check columns exist'' AS skipped')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_workflow_action_run' AND COLUMN_NAME = 'last_check_time');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4c) 扫描索引（已存在则跳过）
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `t_workflow_action_run` ADD KEY `idx_workflow_action_check` (`action_type`, `status`, `last_check_time`) COMMENT ''支持访问路径：定时重查扫描待检查的 DATA_QUERY 动作''',
    'SELECT ''idx_workflow_action_check exists'' AS skipped')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_workflow_action_run' AND INDEX_NAME = 'idx_workflow_action_check');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5) 动作模板 action_type 注释
ALTER TABLE `t_workflow_action_template`
  MODIFY COLUMN `action_type` VARCHAR(32) NOT NULL COMMENT '动作的提交与验证方式；DATA_QUERY 由 data-query 查询结果自动完成。允许值：FORM、FILE_UPLOAD、PAGE_TASK、MANUAL_CONFIRM、DATA_QUERY。';

-- 6) 执行后核对
SELECT 'after' AS `phase`, COUNT(*) AS `running_with_key`, SUM(`active_slot` = 1) AS `slotted`
FROM `t_workflow_instance` WHERE `status` = 'RUNNING' AND `business_key` IS NOT NULL;
