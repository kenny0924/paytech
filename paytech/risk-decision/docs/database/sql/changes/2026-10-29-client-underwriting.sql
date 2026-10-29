-- REQ-03 risk-decision 承保记录：前滚 DDL + 自动承保规则 v1 初值（requirements/20261029/risk-decision-承保记录/technique.md 第 9 节）。
-- 目标库：Risk Decision（mor_risk_decision）。由开发者审核后用有 DDL 权限的账号手工执行；应用启动不执行。
-- 可重跑：三张表 CREATE TABLE IF NOT EXISTS；规则 v1 与 USD 阈值 INSERT ... ON DUPLICATE KEY UPDATE 主键自赋值，不改已存在行。
-- 初值待风控确认（技术方案 O-1）：USD 月流水 50,000、排除 MCC 清单为空；风控给正式值后以新规则版本 DML 发布，不改 v1。
-- 回滚：同目录 2026-10-29-client-underwriting-rollback.sql（有承保记录时拒绝）。
-- 执行后核对：
--   SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()
--     AND table_name IN ('t_client_underwriting', 't_underwriting_rule_version', 't_underwriting_rule_threshold');  -- 3
--   SELECT rule_version_id, version_no, status, active_slot FROM t_underwriting_rule_version;                     -- 290001 / 1 / ACTIVE / 1
--   SELECT threshold_id, currency, monthly_volume_limit FROM t_underwriting_rule_threshold;                       -- 290101 / USD / 50000
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE IF NOT EXISTS `t_client_underwriting` (
  `underwriting_id` BIGINT NOT NULL COMMENT '承保记录版本的稳定业务标识（每个版本一行）。',
  `client_id` BIGINT NOT NULL COMMENT '被承保 Client 的稳定业务标识（client-master 角色 ID，跨库值引用，不建外键）。',
  `version_no` INT NOT NULL COMMENT '同一 Client 内从 1 递增的承保版本号。',
  `status` VARCHAR(32) NOT NULL COMMENT '承保版本状态。允许值：DRAFT、SUBMITTED、APPROVED、DECLINED、SUPERSEDED。APPROVED 后内容不可变，被新版本批准替代时为 SUPERSEDED。',
  `open_slot` TINYINT(1) NULL COMMENT '进行中占位：DRAFT、SUBMITTED 为 1，其他为 NULL；保证同一 Client 至多一个进行中版本。',
  `effective_slot` TINYINT(1) NULL COMMENT '有效结论占位：当前有效的 APPROVED 为 1，其他为 NULL；保证同一 Client 至多一个有效承保结论。',
  `supersedes_underwriting_id` BIGINT NULL COMMENT '本版本批准后替代的上一有效版本 ID；首个版本或未替代时为空。',
  `decision_source` VARCHAR(32) NULL COMMENT '结论来源。允许值：MANUAL、AUTO_RULE；未决定时为空。',
  `rule_version_id` BIGINT NULL COMMENT 'AUTO_RULE 时使用的自动承保规则版本 ID；人工结论为空。',
  `client_display_name` VARCHAR(255) NOT NULL COMMENT '快照时 Client 的法定名称或个人姓名，仅供列表展示，冻结后不随主档变化。',
  `client_no` VARCHAR(64) NOT NULL COMMENT '快照时 Client 编号，仅供列表展示与搜索。',
  `client_type` VARCHAR(16) NOT NULL COMMENT '快照时 Client 类型。允许值：INDIVIDUAL、ORGANIZATION。',
  `jurisdiction_code` VARCHAR(16) NOT NULL COMMENT '快照时 Client 入驻辖区代码。取值来源：client-master 辖区包；非固定枚举。',
  `crr` VARCHAR(16) NULL COMMENT '快照时尽调客户风险评级。允许值：LOW、MEDIUM、HIGH；存量尽调未评为空，按 MEDIUM 处理。',
  `review_level` VARCHAR(16) NOT NULL COMMENT '决定复核要求的风险级别：crr 非空时等于 crr，为空时为 MEDIUM。允许值：LOW、MEDIUM、HIGH。HIGH 须四眼复核。',
  `dd_tier` VARCHAR(32) NULL COMMENT '快照时尽调档次。允许值：SIMPLIFIED、STANDARD、ENHANCED。',
  `mcc_code` CHAR(4) NULL COMMENT '快照时运营确认的主 MCC，四位数字保留前导零；草稿可空，提交必填。取值来源：Platform MCC 参考数据；非固定枚举。',
  `industry_code` VARCHAR(64) NULL COMMENT '快照时运营确认的主行业代码。取值来源：Platform 行业参考数据；非固定枚举。',
  `network_high_risk_mcc` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '快照时该 MCC 是否被卡组织公布为高风险（Platform t_ref_mcc.network_high_risk）。1 是，0 否。',
  `expected_monthly_volume` DECIMAL(24,8) NULL COMMENT '快照时 Client 申报的预计月交易金额，币种为 volume_currency。',
  `volume_currency` CHAR(3) NULL COMMENT '预计月交易金额与客单价的 ISO 4217 币种。取值来源：ISO 4217；非固定枚举。',
  `expected_ticket` DECIMAL(24,8) NULL COMMENT '快照时 Client 申报的预计单笔金额，币种为 volume_currency。',
  `fulfillment_type` VARCHAR(32) NULL COMMENT '快照时申报的交付方式。允许值：INSTANT、PRE_ORDER、SUBSCRIPTION、SERVICE；存量申请未采集为空。',
  `fulfillment_days` SMALLINT NULL COMMENT '快照时申报的预售交付天数 1—365；仅 PRE_ORDER 有值。',
  `website_url` VARCHAR(2048) NULL COMMENT '快照时申报的网站。',
  `refund_policy_url` VARCHAR(2048) NULL COMMENT '快照时申报的退款政策链接。',
  `expected_payment_methods` VARCHAR(512) NULL COMMENT '快照时申报的期望支付方式（commercial capability_code 规范化逗号串），原样冻结。',
  `source_case_id` BIGINT NULL COMMENT '输入来源：compliance 入驻尽调案件 ID；草稿可空，提交必填。',
  `source_case_version` BIGINT NULL COMMENT '输入来源：读取时的尽调案件版本。',
  `source_application_id` BIGINT NULL COMMENT '输入来源：client-master 入驻申请 ID。',
  `source_draft_revision` BIGINT NULL COMMENT '输入来源：读取时的申请资料版本 draft_revision。',
  `source_classification_confirmed_time` DATETIME(6) NULL COMMENT '输入来源：运营确认 MCC / 行业分类的 UTC 微秒时间；为空表示分类未确认，不能提交。',
  `observation_days` SMALLINT NULL COMMENT '承保设定的观察期天数 1—365；提交必填，默认 30。',
  `observation_transactions` INT NULL COMMENT '承保设定的观察期交易笔数 1—100000；提交必填，默认 100；以先到者为准由 REQ-04 解释。',
  `require_deposit` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否要求最低保证金（Risk 决策事实；下发 compliance 控制与执行归 REQ-18）。1 是，0 否。',
  `deposit_amount` DECIMAL(24,8) NULL COMMENT '要求的最低保证金金额，> 0；不要求时为空。',
  `deposit_currency` CHAR(3) NULL COMMENT '最低保证金 ISO 4217 币种；不要求时为空。取值来源：ISO 4217；非固定枚举。',
  `deposit_blocks_collection` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '保证金未到账前是否建议阻塞收款；只有 require_deposit=1 时可为 1。执行另由 REQ-04 / REQ-18 决定。',
  `suggested_settlement_days` SMALLINT NULL COMMENT '结算周期建议 T+N 的 N（0—90）；未给出为空。',
  `high_risk_mcc` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否属于需卡组织高风险登记的 MCC（默认取 network_high_risk_mcc，可人工改并写理由）；登记事实归 channel-connector。1 是，0 否。',
  `high_risk_mcc_reason` VARCHAR(500) NULL COMMENT 'high_risk_mcc 与默认值不同时的理由。',
  `estimated_exposure` DECIMAL(24,8) NULL COMMENT '估算敞口金额（B10，参考值）：expected_monthly_volume × exposure_risk_days / 30，保留 2 位；不可算为空。',
  `exposure_risk_days` SMALLINT NULL COMMENT '估算敞口使用的风险天数（交付方式 + T+N，T+N 为空按 0）；不可算为空。',
  `limit_note` VARCHAR(500) NULL COMMENT '额度说明（不设逐 Client 额度，见需求 Q-4）。',
  `underwriting_note` VARCHAR(2000) NULL COMMENT '承保说明：交付周期与拒付暴露评估及其他判断。',
  `handover_note` VARCHAR(500) NULL COMMENT '交接给商务的备注。',
  `decline_reason` VARCHAR(500) NULL COMMENT 'DECLINED 时的拒绝原因；其他状态为空。',
  `revision_reason` VARCHAR(500) NULL COMMENT '由修订创建的版本的修订原因；首个版本与其他新建版本为空。',
  `review_comment` VARCHAR(500) NULL COMMENT 'HIGH 复核时复核人填写的意见；可空。',
  `created_by` BIGINT NOT NULL COMMENT '建草稿或触发自动承保的 Operations 账号 ID。',
  `submitted_by` BIGINT NULL COMMENT '提交人 Operations 账号 ID；自动承保为空。',
  `submitted_time` DATETIME(6) NULL COMMENT '提交（输入快照冻结）的 UTC 微秒时间；自动承保为生成时间。',
  `decided_by` BIGINT NULL COMMENT '作出 APPROVED / DECLINED 的 Operations 账号 ID；LOW / MEDIUM 等于提交人，HIGH 为复核人，自动承保为空。',
  `decided_time` DATETIME(6) NULL COMMENT '作出结论的 UTC 微秒时间。',
  `superseded_time` DATETIME(6) NULL COMMENT '被新版本替代的 UTC 微秒时间；未替代为空。',
  `version` INT NOT NULL COMMENT '并发更新版本，初始为 1，每次条件更新加一。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`underwriting_id`),
  UNIQUE KEY `uk_client_underwriting_ver` (`client_id`, `version_no`) COMMENT '保证业务唯一性：client_id、version_no',
  UNIQUE KEY `uk_client_underwriting_open` (`client_id`, `open_slot`) COMMENT '同一 Client 至多一个进行中版本',
  UNIQUE KEY `uk_client_underwriting_effective` (`client_id`, `effective_slot`) COMMENT '同一 Client 至多一个有效结论',
  KEY `idx_client_underwriting_list` (`status`, `update_time`) COMMENT '支持访问路径：status、update_time（列表与待复核）',
  KEY `idx_client_underwriting_decided` (`status`, `decided_time`) COMMENT '支持访问路径：status、decided_time（本月已批准 / 已拒绝统计）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Client 承保记录：每个版本一行，提交时冻结输入快照与来源坐标；APPROVED 不可变，修订产生新版本并替代旧版本；Risk 最低保证金要求的决策事实';

CREATE TABLE IF NOT EXISTS `t_underwriting_rule_version` (
  `rule_version_id` BIGINT NOT NULL COMMENT '自动承保规则版本的稳定业务标识。',
  `version_no` INT NOT NULL COMMENT '从 1 递增的规则版本号。',
  `status` VARCHAR(32) NOT NULL COMMENT '规则版本状态。允许值：DRAFT、ACTIVE、RETIRED；同时至多一个 ACTIVE。',
  `active_slot` TINYINT(1) NULL COMMENT 'ACTIVE 为 1，其他为 NULL。',
  `excluded_mcc_codes` VARCHAR(1024) NULL COMMENT '风控额外排除（不可自动承保）的 MCC，四位数字、升序、英文逗号分隔；为空表示只排除卡组织高风险。',
  `activated_time` DATETIME(6) NULL COMMENT '置为 ACTIVE 的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`rule_version_id`),
  UNIQUE KEY `uk_uw_rule_version_no` (`version_no`) COMMENT '保证业务唯一性：version_no',
  UNIQUE KEY `uk_uw_rule_version_active` (`active_slot`) COMMENT '至多一个 ACTIVE 版本'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动承保规则版本（CRR=LOW、币种月流水阈值、非高风险 MCC）；ACTIVE 后内容不改，新阈值以新版本 DML 发布';

CREATE TABLE IF NOT EXISTS `t_underwriting_rule_threshold` (
  `threshold_id` BIGINT NOT NULL COMMENT '阈值行的稳定业务标识。',
  `rule_version_id` BIGINT NOT NULL COMMENT '所属自动承保规则版本 ID。',
  `currency` CHAR(3) NOT NULL COMMENT '预计月流水币种（ISO 4217）；没有阈值的币种不能自动承保。取值来源：ISO 4217；非固定枚举。',
  `monthly_volume_limit` DECIMAL(24,8) NOT NULL COMMENT '可自动承保的预计月流水上限（含），> 0。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`threshold_id`),
  UNIQUE KEY `uk_uw_rule_threshold` (`rule_version_id`, `currency`) COMMENT '保证业务唯一性：rule_version_id、currency'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动承保规则版本的币种月流水阈值';

START TRANSACTION;

INSERT INTO `t_underwriting_rule_version` (`rule_version_id`, `version_no`, `status`, `active_slot`, `excluded_mcc_codes`, `activated_time`, `create_time`, `update_time`) VALUES
  (290001, 1, 'ACTIVE', 1, NULL, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000')
ON DUPLICATE KEY UPDATE `rule_version_id` = `rule_version_id`;
INSERT INTO `t_underwriting_rule_threshold` (`threshold_id`, `rule_version_id`, `currency`, `monthly_volume_limit`, `create_time`, `update_time`) VALUES
  (290101, 290001, 'USD', 50000.00000000, '2026-10-29 00:00:00.000000', '2026-10-29 00:00:00.000000')
ON DUPLICATE KEY UPDATE `threshold_id` = `threshold_id`;

COMMIT;
