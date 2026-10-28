-- =====================================================================================================================
-- commercial（mor_commercial）· 2026-10-28 开通项前置门禁 · t_agreement_capability 拆分成员资格与执行状态
-- 需求：requirements/20261028/commercial-开通项前置门禁/（technique.md §8）
--
-- 内容：
--   1) t_agreement_capability 新增 execution_status（VARCHAR(32) NULL），status 值域改为 ACTIVE / DISABLED；
--   2) 存量迁移：ENABLED → ACTIVE + ENABLED；SUSPENDED → ACTIVE + SUSPENDED_MANUALLY；DISABLED → DISABLED + NULL；
--   3) 改写 status 列注释与表注释，重建 idx_agreement_capability_lookup 为 (entity_no, capability_code, execution_status)，
--      新增 idx_agreement_capability_execution (execution_status, agreement_id)；
--   4) commercial 首个 MQ 消费者（QualificationChanged）所需的 paytech-messaging 每库一份消费幂等表
--      t_consumed_event / t_consumed_aggregate_version（逐字取自 paytech-messaging schema/messaging-consumed-event.sql，已存在则跳过）。
--
-- 可重跑：ADD 列 / 建索引前检查 information_schema；UPDATE 只改旧值；建表用 IF NOT EXISTS。
-- 部署顺序（technique.md §8.3）：停 commercial → 执行本脚本 → 起新镜像；新旧代码互读对方取值都会 Fail-Closed，二者必须相邻。
-- 回滚：同目录 2026-10-28-capability-execution-status-rollback.sql（停 commercial → 回滚 SQL → 旧镜像）。
-- 执行账号：具备 ALTER / CREATE / INDEX 权限的管理账号；commercial_app 只有 DML 权限。
-- =====================================================================================================================

USE mor_commercial;
SET NAMES utf8mb4;

-- 1) 新增 execution_status（已存在则跳过）
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `t_agreement_capability` ADD COLUMN `execution_status` VARCHAR(32) NULL COMMENT ''执行开关；status=ACTIVE 时必须非空、status=DISABLED 时为空（应用校验）。PENDING_PREREQUISITES 为等待前置，ENABLED 为可执行（Checkout 只认此值），SUSPENDED_BY_PREREQUISITE 为前置失效暂停（门禁可自动恢复），SUSPENDED_MANUALLY 为运营手动暂停（门禁不触碰）。允许值：PENDING_PREREQUISITES、ENABLED、SUSPENDED_BY_PREREQUISITE、SUSPENDED_MANUALLY。'' AFTER `status`',
    'SELECT ''execution_status exists'' AS skipped')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_agreement_capability' AND COLUMN_NAME = 'execution_status');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 存量迁移：只改旧值，重跑不再命中
START TRANSACTION;
UPDATE `t_agreement_capability` SET `execution_status` = 'ENABLED', `status` = 'ACTIVE' WHERE `status` = 'ENABLED';
UPDATE `t_agreement_capability` SET `execution_status` = 'SUSPENDED_MANUALLY', `status` = 'ACTIVE' WHERE `status` = 'SUSPENDED';
UPDATE `t_agreement_capability` SET `execution_status` = NULL WHERE `status` = 'DISABLED' AND `execution_status` IS NOT NULL;
COMMIT;

-- 3) status 列注释与表注释
ALTER TABLE `t_agreement_capability`
  MODIFY COLUMN `status` VARCHAR(32) NOT NULL COMMENT '合同成员资格；ACTIVE 为纳入合同（价格须覆盖），DISABLED 为运营关闭。允许值：ACTIVE、DISABLED。',
  COMMENT = '合同在准确OE下的开通项与保证金条款；uk_agreement_capability(agreement_id,entity_no,capability_code)；require_3ds 为开通项的 3DS 要求；所属产品必须已在合同订阅；capability_code引用时须为ACTIVE目录项；CLIENT合同entity_no等于合同entity_no，PARTNER合同须在合同覆盖OE内；成员资格status为ACTIVE/DISABLED，执行开关execution_status为PENDING_PREREQUISITES/ENABLED/SUSPENDED_BY_PREREQUISITE/SUSPENDED_MANUALLY（DISABLED时为空），有效区间半开；保证金比例与天数成组为空或非空，执行归account/risk；应用校验，不创建 CHECK/FK；所属产品必须已在 agreement_product 订阅；capability_code 引用时目录项须为 ACTIVE。；CLIENT 合同的 entity_no 必须等于 agreement.entity_no；PARTNER 合同的 entity_no 必须在 agreement_operating_entity 内。；CHECK (effective_to IS NULL OR effective_from < effective_to)，解析时要求 execution_status=ENABLED 且 effective_from <= contextAsOfTime < effective_to。；rolling_reserve_rate 与 rolling_reserve_days 成组为空或成组非空；比例取值 0 至 1，保证金执行归 account / risk。';

-- 3b) 重建 idx_agreement_capability_lookup（仍以 status 结尾时才重建）
SET @ddl = (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_agreement_capability` DROP INDEX `idx_agreement_capability_lookup`, ADD KEY `idx_agreement_capability_lookup` (`entity_no`, `capability_code`, `execution_status`) COMMENT ''Access index for agreement_capability on entity_no, capability_code, execution_status''',
    'SELECT ''idx_agreement_capability_lookup already rebuilt'' AS skipped')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_agreement_capability'
      AND INDEX_NAME = 'idx_agreement_capability_lookup' AND SEQ_IN_INDEX = 3 AND COLUMN_NAME = 'status');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3c) 新增 idx_agreement_capability_execution（已存在则跳过）
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `t_agreement_capability` ADD KEY `idx_agreement_capability_execution` (`execution_status`, `agreement_id`) COMMENT ''Access index for agreement_capability on execution_status, agreement_id''',
    'SELECT ''idx_agreement_capability_execution exists'' AS skipped')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_agreement_capability'
      AND INDEX_NAME = 'idx_agreement_capability_execution');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) paytech-messaging 消费幂等表（每库一份）
CREATE TABLE IF NOT EXISTS t_consumed_event (
    event_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件 ID（信封 eventId，默认等于 eventKey）',
    event_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件类型（PascalCase，登记于事件目录）',
    aggregate_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合类型（大写 Code）',
    aggregate_id VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合标识（分区键）',
    aggregate_version BIGINT NOT NULL COMMENT '本事件携带的聚合版本',
    consumed_at DATETIME(6) NOT NULL COMMENT '消费事务提交时间（UTC）',
    PRIMARY KEY (event_id),
    KEY idx_consumed_event_aggregate (aggregate_type, aggregate_id, aggregate_version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_bin COMMENT = '已消费领域事件：按事件 ID 去重';

CREATE TABLE IF NOT EXISTS t_consumed_aggregate_version (
    aggregate_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合类型（大写 Code）',
    aggregate_id VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合标识（分区键）',
    last_version BIGINT NOT NULL COMMENT '本服务已处理的最大聚合版本',
    update_time DATETIME(6) NOT NULL COMMENT '最近推进时间（UTC）',
    PRIMARY KEY (aggregate_type, aggregate_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_bin COMMENT = '已消费聚合版本：判定乱序、重放与缺口';

-- 执行后核对（预期：status 只有 ACTIVE / DISABLED；ACTIVE 行 execution_status 非空；DISABLED 行为空）：
--   SELECT status, execution_status, COUNT(*) FROM t_agreement_capability GROUP BY status, execution_status;
--   SHOW CREATE TABLE t_agreement_capability;
