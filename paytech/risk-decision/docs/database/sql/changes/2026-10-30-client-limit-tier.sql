-- REQ-04 risk-decision 限额档位：前滚 DDL + 注释 + 四张 TIER 名单（requirements/20261029/risk-decision-限额档位/technique.md 第 9 节）。
-- 目标库：Risk Decision（mor_risk_decision）。由开发者审核后用有 DDL 权限的账号手工执行；应用启动不执行。
-- 可重跑：三张表 CREATE TABLE IF NOT EXISTS；五处列注释 MODIFY COLUMN（原类型、原可空）；四张名单 INSERT ... ON DUPLICATE KEY UPDATE 主键自赋值，不改已存在行。
-- 先后：本脚本可先于新镜像执行（新表与名单空跑无影响；现有发布不引用 TIER 名单与 ALLOWED 事件，行为不变）。
-- 回滚：同目录 2026-10-30-client-limit-tier-rollback.sql（有档位数据或 TIER 名单有成员时拒绝）。
-- 执行后核对：
--   SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()
--     AND table_name IN ('t_client_limit_tier', 't_client_limit_tier_change', 't_client_allowed_daily_total');  -- 3
--   SELECT list_id, list_code, list_purpose, supported_subject_type, status FROM t_risk_list
--     WHERE list_code LIKE 'TIER\_%' ORDER BY list_id;                                                         -- 300001—300004 / TIER / CLIENT / ACTIVE
--   SELECT COUNT(*) FROM t_risk_list WHERE list_id BETWEEN 300001 AND 300004 AND list_purpose = 'TIER';        -- 4（编码已被他人占用时为 <4，须人工处理）
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE IF NOT EXISTS `t_client_limit_tier` (
  `client_limit_tier_id` BIGINT NOT NULL COMMENT 'Client 限额档位行的稳定业务标识。',
  `client_id` BIGINT NOT NULL COMMENT 'Client 的稳定业务标识（client-master 角色 ID，跨库值引用）。',
  `tier` VARCHAR(32) NOT NULL COMMENT '当前限额档位。允许值：VERIFICATION、OBSERVATION、STANDARD、RESTRICTED；无行表示无档位（额度为零）。',
  `previous_tier` VARCHAR(32) NULL COMMENT '进入 RESTRICTED 前的档位，用于限制恢复目标；非 RESTRICTED 时为空。',
  `client_display_name` VARCHAR(255) NOT NULL COMMENT '建档时 Client 名称（取承保快照或迁移输入），仅供展示。',
  `client_no` VARCHAR(64) NOT NULL COMMENT '建档时 Client 编号，仅供展示与搜索。',
  `client_type` VARCHAR(16) NOT NULL COMMENT '建档时 Client 类型。允许值：INDIVIDUAL、ORGANIZATION。',
  `jurisdiction_code` VARCHAR(16) NOT NULL COMMENT '建档时 Client 入驻辖区代码。取值来源：client-master 辖区包；非固定枚举。',
  `source_type` VARCHAR(32) NOT NULL COMMENT '当前档位的来源。允许值：UNDERWRITING_INIT、MIGRATION、REQUEST。',
  `source_change_id` BIGINT NOT NULL COMMENT '产生当前档位的变更记录 ID（t_client_limit_tier_change）。',
  `effective_time` DATETIME(6) NOT NULL COMMENT '当前档位生效的 UTC 微秒时间。',
  `observation_started_time` DATETIME(6) NULL COMMENT '观察期开始时间（进入或延长 OBSERVATION 时写入）；非 OBSERVATION 为空。',
  `observation_target_days` SMALLINT NULL COMMENT '观察期目标天数 1—365（从有效承保冻结，无承保默认 30）；非 OBSERVATION 为空。',
  `observation_target_transactions` INT NULL COMMENT '观察期目标放行笔数 1—100000（从有效承保冻结，无承保默认 100）；非 OBSERVATION 为空。',
  `observation_started_by_change_id` BIGINT NULL COMMENT '开始或延长当前观察期的变更记录 ID；非 OBSERVATION 为空。',
  `created_by` BIGINT NULL COMMENT '建档操作人：承保初始化时为承保决定人或触发人，迁移时为执行人。',
  `version` INT NOT NULL COMMENT '并发更新版本，初始为 1，每次档位变化加一。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`client_limit_tier_id`),
  UNIQUE KEY `uk_client_limit_tier_client` (`client_id`) COMMENT '每个 Client 至多一个当前档位',
  KEY `idx_client_limit_tier_list` (`tier`, `update_time`) COMMENT '支持访问路径：tier、update_time（列表）',
  KEY `idx_client_limit_tier_observation` (`tier`, `observation_started_time`) COMMENT '支持访问路径：观察期到期筛选'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Client 当前限额档位（交易放量开关的权威事实）；变化与四张 TIER 名单成员在同一事务维护；历史见 t_client_limit_tier_change';

CREATE TABLE IF NOT EXISTS `t_client_limit_tier_change` (
  `change_id` BIGINT NOT NULL COMMENT '档位变更记录的稳定业务标识。',
  `client_id` BIGINT NOT NULL COMMENT 'Client 的稳定业务标识。',
  `change_type` VARCHAR(32) NOT NULL COMMENT '变更来源。允许值：UNDERWRITING_INIT（承保批准时 Client 尚无档位自动建档）、MIGRATION（存量迁移）、REQUEST（运营申请、四眼审批）。',
  `from_tier` VARCHAR(32) NULL COMMENT '变更前档位；建档时为空。允许值：VERIFICATION、OBSERVATION、STANDARD、RESTRICTED。',
  `to_tier` VARCHAR(32) NOT NULL COMMENT '目标档位。允许值：VERIFICATION、OBSERVATION、STANDARD、RESTRICTED。',
  `status` VARCHAR(32) NOT NULL COMMENT '变更状态。允许值：PENDING、APPROVED、REJECTED、WITHDRAWN；建档与迁移直接为 APPROVED。',
  `pending_slot` TINYINT(1) NULL COMMENT 'PENDING 为 1，其他为 NULL；保证每个 Client 至多一个待审批申请。',
  `approval_level` VARCHAR(16) NULL COMMENT '审批级别：STANDARD（CRR=LOW）或 ELEVATED（MEDIUM、HIGH 或无 CRR）；建档与迁移为空。',
  `crr_snapshot` VARCHAR(16) NULL COMMENT '申请时有效承保快照的 CRR。允许值：LOW、MEDIUM、HIGH；无承保或未评为空（按 ELEVATED）。',
  `underwriting_id` BIGINT NULL COMMENT '建档或申请时的有效承保版本 ID；无承保为空。',
  `reason` VARCHAR(1000) NOT NULL COMMENT '变更理由；建档为"承保批准"，迁移为执行时填写的理由。',
  `go_live_note` VARCHAR(2000) NULL COMMENT '上线复核说明（VERIFICATION → OBSERVATION 必填）。',
  `chk_qualification_status` VARCHAR(32) NULL COMMENT '复核清单：申请时 Client 角色资格状态（APPROVED、其他状态或 UNAVAILABLE）。',
  `chk_qualification_valid_to` DATETIME(6) NULL COMMENT '复核清单：资格有效期。',
  `chk_underwriting_version_no` INT NULL COMMENT '复核清单：有效承保版本号。',
  `chk_agreement_id` BIGINT NULL COMMENT '复核清单：CLIENT 合同 ID。',
  `chk_agreement_status` VARCHAR(32) NULL COMMENT '复核清单：合同状态。',
  `chk_enabled_capabilities` VARCHAR(512) NULL COMMENT '复核清单：执行状态 ENABLED 的支付方式开通项代码，逗号分隔。',
  `chk_pending_capabilities` VARCHAR(512) NULL COMMENT '复核清单：未 ENABLED 的支付方式开通项代码，逗号分隔。',
  `chk_channel_registration` TINYINT(1) NULL COMMENT '复核清单人工项：渠道报备已确认。',
  `chk_integration` TINYINT(1) NULL COMMENT '复核清单人工项：技术接入已确认。',
  `chk_production_verified` TINYINT(1) NULL COMMENT '复核清单人工项：生产交易验证已完成。',
  `chk_open_issues_cleared` TINYINT(1) NULL COMMENT '复核清单人工项：未结事项（筛查待复核、证件到期等）已处理。',
  `observation_target_days` SMALLINT NULL COMMENT '进入或延长观察期时设定的目标天数；其他变更为空。',
  `observation_target_transactions` INT NULL COMMENT '进入或延长观察期时设定的目标放行笔数；其他变更为空。',
  `observation_days_at_request` SMALLINT NULL COMMENT '申请时已过的观察期天数（当前档位为 OBSERVATION 时冻结）；其他为空。',
  `observation_allowed_at_request` INT NULL COMMENT '申请时观察期内已放行笔数（当前档位为 OBSERVATION 时冻结）；其他为空。',
  `requested_by` BIGINT NULL COMMENT '申请人 Operations 账号 ID；承保建档为空，迁移为执行人。',
  `requested_time` DATETIME(6) NOT NULL COMMENT '申请（或建档、迁移）的 UTC 微秒时间。',
  `decided_by` BIGINT NULL COMMENT '审批人 Operations 账号 ID；须不同于 requested_by；建档与迁移为空。',
  `decided_time` DATETIME(6) NULL COMMENT '批准、驳回或撤回的 UTC 微秒时间。',
  `decision_comment` VARCHAR(500) NULL COMMENT '审批意见；驳回必填。',
  `version` INT NOT NULL COMMENT '并发更新版本，初始为 1。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`change_id`),
  UNIQUE KEY `uk_client_tier_change_pending` (`client_id`, `pending_slot`) COMMENT '每个 Client 至多一个待审批申请',
  KEY `idx_client_tier_change_client` (`client_id`, `requested_time`) COMMENT '支持访问路径：client_id、requested_time（历史）',
  KEY `idx_client_tier_change_status` (`status`, `update_time`) COMMENT '支持访问路径：status、update_time（待审批统计）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Client 限额档位的变更记录：承保建档、存量迁移与运营申请（含复核清单快照与四眼审批）；追加为主，只有 PENDING 行可变';

-- 阶段 4 修订（方案 B）：Client 按 UTC 日的放行金额汇总，供日 / 月额度 Feature（DAILY_SUM_AMOUNT）与档位页"已用"。
CREATE TABLE IF NOT EXISTS `t_client_allowed_daily_total` (
  `daily_total_id` BIGINT NOT NULL COMMENT 'Client 日放行汇总行的稳定业务标识。',
  `client_id` BIGINT NOT NULL COMMENT 'Client 的稳定业务标识（CLIENT 累计主体键）。',
  `event_code` VARCHAR(64) NOT NULL COMMENT '汇总的放行事件。允许值：PAYMENT_ATTEMPT_ALLOWED、CHECKOUT_ALLOWED。',
  `currency` CHAR(3) NOT NULL COMMENT '事件币种（ISO 4217）；各币种分别汇总，不换汇。取值来源：ISO 4217；非固定枚举。',
  `event_date` DATE NOT NULL COMMENT '事件发生时间（评估时间）所在的 UTC 自然日。',
  `amount` DECIMAL(24,8) NOT NULL COMMENT '当日该币种放行金额合计。',
  `event_count` INT NOT NULL COMMENT '当日该币种放行事件数。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`daily_total_id`),
  UNIQUE KEY `uk_client_allowed_daily_total` (`client_id`, `event_code`, `currency`, `event_date`) COMMENT '每个 Client、事件、币种、UTC 日一行；也是评估读取路径'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Client 按 UTC 日的放行金额汇总：与放行累计事件同一事务累加，供日 / 月额度 Feature 与档位页"已用"读取；事件表仍是明细事实';

ALTER TABLE `t_velocity_feature_definition`
  MODIFY COLUMN `metric` VARCHAR(64) NOT NULL COMMENT '累计算法的稳定业务代码。允许值：COUNT、SUM_AMOUNT、DISTINCT_COUNT、LAST_AMOUNT、SECONDS_SINCE_LAST、DAILY_SUM_AMOUNT（按 UTC 日汇总的 Client 放行金额，读 t_client_allowed_daily_total）。',
  MODIFY COLUMN `window_seconds` INT NOT NULL COMMENT '窗口秒数的整数值；DAILY_SUM_AMOUNT 时 86400 表示 UTC 当日、2592000 表示近 30 个 UTC 自然日（含当日）。';

ALTER TABLE `t_risk_list`
  MODIFY COLUMN `list_purpose` VARCHAR(64) NOT NULL COMMENT '名单用途的稳定业务代码。允许值：BLOCK、ALLOW、TIER（系统维护的限额档位名单，成员随档位变更同事务维护，运营不能增删成员）。';
ALTER TABLE `t_risk_list_entry`
  MODIFY COLUMN `source_type` VARCHAR(64) NOT NULL COMMENT '成员来源的稳定业务代码。允许值：ACTION、MANUAL、TIER（限额档位投影，原因记 change:{变更记录 ID}）。';
ALTER TABLE `t_velocity_feature_definition`
  MODIFY COLUMN `event_code` VARCHAR(64) NOT NULL COMMENT '事件编码的稳定业务代码。允许值：PAYMENT_ATTEMPT_ASSESSED、PAYMENT_ATTEMPT_REJECTED、PAYMENT_ATTEMPT_ALLOWED、CHECKOUT_ASSESSED、CHECKOUT_REJECTED、CHECKOUT_ALLOWED。';

START TRANSACTION;

INSERT INTO `t_risk_list` (`list_id`, `list_code`, `list_name`, `supported_subject_type`, `matching_mode`, `list_purpose`, `status`, `version`, `create_time`, `update_time`) VALUES
  (300001, 'TIER_VERIFICATION', '限额档位 · 验证', 'CLIENT', 'EXACT', 'TIER', 'ACTIVE', 1, '2026-10-30 00:00:00.000000', '2026-10-30 00:00:00.000000'),
  (300002, 'TIER_OBSERVATION', '限额档位 · 观察', 'CLIENT', 'EXACT', 'TIER', 'ACTIVE', 1, '2026-10-30 00:00:00.000000', '2026-10-30 00:00:00.000000'),
  (300003, 'TIER_STANDARD', '限额档位 · 标准', 'CLIENT', 'EXACT', 'TIER', 'ACTIVE', 1, '2026-10-30 00:00:00.000000', '2026-10-30 00:00:00.000000'),
  (300004, 'TIER_RESTRICTED', '限额档位 · 受限', 'CLIENT', 'EXACT', 'TIER', 'ACTIVE', 1, '2026-10-30 00:00:00.000000', '2026-10-30 00:00:00.000000')
ON DUPLICATE KEY UPDATE `list_id` = `list_id`;

COMMIT;
