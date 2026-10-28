-- =====================================================================================================================
-- commercial（mor_commercial）· 2026-10-28 开通项前置门禁 · 回滚 2026-10-28-capability-execution-status.sql
-- 需求：requirements/20261028/commercial-开通项前置门禁/（technique.md §8.3）
--
-- 顺序：停 commercial → 执行本脚本 → 起旧镜像。
-- 反向映射：ACTIVE + ENABLED → ENABLED；ACTIVE + SUSPENDED_BY_PREREQUISITE / SUSPENDED_MANUALLY → SUSPENDED；
--           ACTIVE + PENDING_PREREQUISITES → SUSPENDED（回滚后不能凭空开通）；DISABLED → DISABLED。
-- 不删除 t_consumed_event / t_consumed_aggregate_version：旧代码不读写它们，保留消费去重记录以便再次前滚。
-- 可重跑：UPDATE 只改新值；索引与列按 information_schema 判断。
-- =====================================================================================================================

USE mor_commercial;
SET NAMES utf8mb4;

-- 1) 反向迁移（execution_status 列存在时；列已删除说明回滚已完成，跳过）
SET @has_execution_status = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_agreement_capability' AND COLUMN_NAME = 'execution_status');
SET @dml = IF(@has_execution_status > 0,
    'UPDATE `t_agreement_capability` SET `status` = CASE WHEN `execution_status` = ''ENABLED'' THEN ''ENABLED'' ELSE ''SUSPENDED'' END WHERE `status` = ''ACTIVE''',
    'SELECT ''execution_status absent'' AS skipped');
PREPARE stmt FROM @dml;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 恢复 status 列注释与表注释
ALTER TABLE `t_agreement_capability`
  MODIFY COLUMN `status` VARCHAR(32) NOT NULL COMMENT '开通项状态；SUSPENDED 为风控或合规临时停用且可恢复，DISABLED 为运营关闭。允许值：ENABLED、SUSPENDED、DISABLED。',
  COMMENT = '合同在准确OE下的开通项与保证金条款；uk_agreement_capability(agreement_id,entity_no,capability_code)；require_3ds 为开通项的 3DS 要求；所属产品必须已在合同订阅；capability_code引用时须为ACTIVE目录项；CLIENT合同entity_no等于合同entity_no，PARTNER合同须在合同覆盖OE内；状态ENABLED/SUSPENDED/DISABLED，有效区间半开；保证金比例与天数成组为空或非空，执行归account/risk；应用校验，不创建 CHECK/FK；所属产品必须已在 agreement_product 订阅；capability_code 引用时目录项须为 ACTIVE。；CLIENT 合同的 entity_no 必须等于 agreement.entity_no；PARTNER 合同的 entity_no 必须在 agreement_operating_entity 内。；CHECK (effective_to IS NULL OR effective_from < effective_to)，解析时要求 status=ENABLED 且 effective_from <= contextAsOfTime < effective_to。；rolling_reserve_rate 与 rolling_reserve_days 成组为空或成组非空；比例取值 0 至 1，保证金执行归 account / risk。';

-- 3) 删除 idx_agreement_capability_execution（存在时）
SET @ddl = (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_agreement_capability` DROP INDEX `idx_agreement_capability_execution`',
    'SELECT ''idx_agreement_capability_execution absent'' AS skipped')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_agreement_capability'
      AND INDEX_NAME = 'idx_agreement_capability_execution');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) 恢复 idx_agreement_capability_lookup 为 (entity_no, capability_code, status)（仍以 execution_status 结尾时）
SET @ddl = (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_agreement_capability` DROP INDEX `idx_agreement_capability_lookup`, ADD KEY `idx_agreement_capability_lookup` (`entity_no`, `capability_code`, `status`) COMMENT ''Access index for agreement_capability on entity_no, capability_code, status''',
    'SELECT ''idx_agreement_capability_lookup already restored'' AS skipped')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_agreement_capability'
      AND INDEX_NAME = 'idx_agreement_capability_lookup' AND SEQ_IN_INDEX = 3 AND COLUMN_NAME = 'execution_status');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5) 删除 execution_status（存在时）
SET @ddl = (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_agreement_capability` DROP COLUMN `execution_status`',
    'SELECT ''execution_status absent'' AS skipped')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_agreement_capability' AND COLUMN_NAME = 'execution_status');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 执行后核对（预期：status 只有 ENABLED / SUSPENDED / DISABLED，execution_status 列不存在）：
--   SELECT status, COUNT(*) FROM t_agreement_capability GROUP BY status;
--   SHOW CREATE TABLE t_agreement_capability;
