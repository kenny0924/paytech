-- account 明细账重构：mor_account 变更脚本（阶段 3 设计文件，未执行）
-- 文件性质：执行文件（变更脚本）。目标库：mor_account（本机 local-environment、mor-test）。
-- 前置：按 2026-10-18-account-database.sql 建成的 8 张表；两环境均无业务数据（决定第 11 条、设计 8.4），
--       执行前用下面的 SELECT 核对为 0 行后再执行。t_consumed_event、t_consumed_aggregate_version、t_compensation_event 不动。
-- 执行顺序：本文件 → 重启 account（新镜像）。重跑：不允许（DROP 后再执行会失败在 CREATE）。
-- 回滚：DROP 本文件创建的 6 张表，重新执行 docs/database/sql/account-schema.sql 的旧版本（git 7acbcf4f）。
-- 对应 PDManer：paytech/account/docs/database/account.pdma.json（阶段 4 同步）。
SET NAMES utf8mb4;

-- 执行前核对（期望全部为 0）
SELECT 'pre-check' AS step,
  (SELECT COUNT(*) FROM `t_account_balance`) AS balances,
  (SELECT COUNT(*) FROM `t_balance_applied_fact`) AS applied_facts,
  (SELECT COUNT(*) FROM `t_balance_applied_entry`) AS applied_entries,
  (SELECT COUNT(*) FROM `t_balance_movement`) AS movements,
  (SELECT COUNT(*) FROM `t_balance_discrepancy`) AS discrepancies,
  (SELECT COUNT(*) FROM `t_compensation_event`) AS compensation_events;

DROP TABLE IF EXISTS `t_balance_discrepancy`;
DROP TABLE IF EXISTS `t_balance_applied_entry`;
DROP TABLE IF EXISTS `t_balance_movement`;
DROP TABLE IF EXISTS `t_balance_applied_fact`;
DROP TABLE IF EXISTS `t_account_balance`;

CREATE TABLE `t_owner_account` (
  `id` BIGINT NOT NULL COMMENT '账户服务生成的 Owner 账户 Snowflake 主键；ledger 分录行经 t_account_entry 下钻，不复用 ledger 账户 ID。',
  `entity_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账户所属法律主体类型。允许值：OPERATING_ENTITY、FINANCE_ENTITY。',
  `entity_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账户所属主体标识：OE 为 CCT entityNo，Finance Entity 为 ledger 主数据主键十进制字符串；不建立跨库外键。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账户的大写 ISO 4217 币种。',
  `account_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Owner 账户类型代码，等于 ledger 控制科目的科目代码。取值来源：account-api OwnerAccountType（CLIENT_PAYABLE、CLIENT_SECURITY_DEPOSIT、CLIENT_RECOVERY_RECEIVABLE、PARTNER_PAYABLE、PARTNER_RECOVERY_RECEIVABLE、BANK_CASH）；非固定枚举。',
  `owner_type` VARCHAR(64) NOT NULL COMMENT '核算对象类型。允许值：CLIENT、LOCAL_PARTNER、CASH_ACCOUNT。',
  `owner_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '按 owner_type 解释的规范十进制稳定标识：Client / Local Partner 为 CCT 主键，CASH_ACCOUNT 为 reconciliation 银行账户主键；不建立跨库外键。',
  `balance_bucket` VARCHAR(64) NOT NULL COMMENT '余额桶：应付款与银行现金按桶分账户，独立科目使用 NONE。允许值：PENDING、AVAILABLE、LOCKED、NONE。',
  `normal_side` VARCHAR(16) NOT NULL COMMENT '正常余额方向，开户时由 Owner 账户类型目录冻结。允许值：DEBIT、CREDIT。',
  `debit_total` DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '已入账流水累计借方金额。',
  `credit_total` DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '已入账流水累计贷方金额。',
  `net_balance` DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '按正常方向计算、允许为负的当前净余额；是 Owner 可用额度与桶移动的实时权威。',
  `balance_version` BIGINT NOT NULL DEFAULT 0 COMMENT '余额条件更新使用的乐观锁版本，每次写入加一；重建也只递增。',
  `account_status` VARCHAR(16) NOT NULL COMMENT '账户状态，只约束桶移动等命令，不拒绝经济事实入账。允许值：ACTIVE、FROZEN、CLOSED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '记录最近一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_owner_account_identity` (`entity_type`, `entity_id`, `currency`, `account_code`, `owner_type`, `owner_id`, `balance_bucket`) COMMENT '保证业务唯一性：entity_type、entity_id、currency、account_code、owner_type、owner_id、balance_bucket',
  KEY `idx_owner_account_owner` (`owner_type`, `owner_id`, `entity_id`, `currency`) COMMENT '支持访问路径：owner_type、owner_id、entity_id、currency',
  KEY `idx_owner_account_code` (`entity_type`, `entity_id`, `currency`, `account_code`) COMMENT '支持访问路径：entity_type、entity_id、currency、account_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='明细账 Owner 账户（2026-10-18 决定第 2 条）：Client / Local Partner / 银行现金在每个主体 + 币种 + 科目代码 + 余额桶下的唯一账户主数据与实时余额；余额只由本库流水维护并可从流水重算。';

CREATE TABLE `t_account_posting_stream` (
  `id` BIGINT NOT NULL COMMENT '账户服务生成的 Snowflake 主键。',
  `entity_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '入账流所属主体类型。允许值：OPERATING_ENTITY、FINANCE_ENTITY。',
  `entity_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '入账流所属主体标识，解释同 t_owner_account.entity_id。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '入账流币种。',
  `shard_no` INTEGER NOT NULL DEFAULT 0 COMMENT '预留分片号；当前固定 0，同一主体 + 币种内全部入账串行分配序号（设计 8.1）。',
  `last_sequence` BIGINT NOT NULL DEFAULT 0 COMMENT '本流已分配的最大入账序号；分配时行锁后加一，等于 AccountPosted 的最新 aggregateVersion。',
  `last_posted_time` DATETIME(6) NULL COMMENT '本流最近一次入账的 UTC 微秒时间；尚未入账时为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '记录最近一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_posting_stream` (`entity_type`, `entity_id`, `currency`, `shard_no`) COMMENT '保证业务唯一性：entity_type、entity_id、currency、shard_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='入账流序号行：每个主体 + 币种 + 分片一行，入账与桶移动在本行锁内分配连续 posting_sequence；也是进度查询与 ledger 追平的起点。';

CREATE TABLE `t_account_posting` (
  `id` BIGINT NOT NULL COMMENT '账户服务生成的入账批次 Snowflake 主键，也是 AccountPosted 的 postingId 与 ledger 指令的 source_event_key。',
  `entity_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '批次所属主体类型。允许值：OPERATING_ENTITY、FINANCE_ENTITY。',
  `entity_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '批次所属主体标识。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '批次币种。',
  `shard_no` INTEGER NOT NULL DEFAULT 0 COMMENT '分片号，当前固定 0；与事件聚合 ID {OE|FE}.{entity_id}.{currency}.{shard_no} 对应。',
  `posting_sequence` BIGINT NOT NULL COMMENT '同主体 + 币种 + 分片内连续递增的入账序号，即 AccountPosted 的 aggregateVersion 与 ledger 的消费进度。',
  `source_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '批次来源。允许值：ECONOMIC_FACT（一条经济事实，含替代冲回与 0 流水批次）、BALANCE_MOVEMENT（一次桶移动）。',
  `fact_id` BIGINT NULL COMMENT '事实批次对应的 billing 经济事实 ID；桶移动批次为空。',
  `fact_version` BIGINT NULL COMMENT '事实批次对应的经济事实版本；桶移动批次为空。',
  `movement_id` BIGINT NULL COMMENT '桶移动批次对应的 t_balance_movement.id；事实批次为空。',
  `entry_count` INTEGER NOT NULL DEFAULT 0 COMMENT '本批次流水行数；不影响 Owner 的事实为 0。',
  `posted_time` DATETIME(6) NOT NULL COMMENT '入账事务提交前记录的 UTC 微秒时间。',
  `published_time` DATETIME(6) NULL COMMENT 'AccountPosted 收到 Broker 确认的 UTC 微秒时间；尚未发布为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_posting_sequence` (`entity_type`, `entity_id`, `currency`, `shard_no`, `posting_sequence`) COMMENT '保证业务唯一性：entity_type、entity_id、currency、shard_no、posting_sequence',
  UNIQUE KEY `uk_posting_fact` (`source_type`, `fact_id`, `fact_version`) COMMENT '保证业务唯一性：source_type、fact_id、fact_version',
  UNIQUE KEY `uk_posting_movement` (`movement_id`) COMMENT '保证业务唯一性：movement_id',
  KEY `idx_posting_unpublished` (`published_time`, `posted_time`) COMMENT '支持访问路径：published_time、posted_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='账户入账批次（设计第 4 节）：一次入账一条，带主体 + 币种 + 分片内连续序号；经本地补偿事件可靠发布为 AccountPosted，ledger 按序消费并记控制科目。';

CREATE TABLE `t_account_entry` (
  `id` BIGINT NOT NULL COMMENT '账户服务生成的流水 Snowflake 主键；ledger 控制科目行以 account_entry_id 指回本行。',
  `posting_id` BIGINT NOT NULL COMMENT '所属入账批次（t_account_posting.id）。',
  `line_no` INTEGER NOT NULL COMMENT '批次内从 1 起连续的行号。',
  `account_id` BIGINT NOT NULL COMMENT '被改变的 Owner 账户（t_owner_account.id）。',
  `entry_side` VARCHAR(16) NOT NULL COMMENT '对账户的借贷方向。允许值：DEBIT、CREDIT。',
  `amount` DECIMAL(24,8) NOT NULL COMMENT '已按币种精度归整的正金额。',
  `fact_line_no` INTEGER NULL COMMENT '产生本行的经济事实明细行号；事实头总额为 0；桶移动为空。',
  `reverses_entry_id` BIGINT NULL COMMENT '替代冲回时指向被冲回的原流水（t_account_entry.id）；普通行为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account_entry_line` (`posting_id`, `line_no`) COMMENT '保证业务唯一性：posting_id、line_no',
  UNIQUE KEY `uk_account_entry_reverses` (`reverses_entry_id`) COMMENT '保证业务唯一性：reverses_entry_id',
  KEY `idx_account_entry_account` (`account_id`, `id`) COMMENT '支持访问路径：account_id、id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='不可变账户流水明细：事实入账的逐行借贷与桶移动的转出 / 转入两行，是余额的唯一来源（设计 4.5）。';

CREATE TABLE `t_balance_applied_fact` (
  `id` BIGINT NOT NULL COMMENT '账户服务生成的 Snowflake 主键。',
  `fact_id` BIGINT NOT NULL COMMENT 'billing 经济事实 ID（t_economic_fact.id）；不建立跨库外键。',
  `fact_version` BIGINT NOT NULL COMMENT '经济事实版本；被替代事实先于自身事件到达时按替代事实版本减一登记。',
  `fact_type` VARCHAR(64) NULL COMMENT '经济事实类型代码；仅由替代关系预登记、自身事件尚未到达时为空。取值来源：billing EconomicsType；非固定枚举。',
  `supersedes_fact_id` BIGINT NULL COMMENT '本事实替代的前一经济事实；首次登记为空。',
  `superseded_by_fact_id` BIGINT NULL COMMENT '替代本事实的后一经济事实；未被替代时为空。',
  `entity_no` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '经济事实头的 Operating Entity 编号，决定入账流。',
  `finance_entity_id` BIGINT NULL COMMENT '经济事实头的平台服务费 Finance Entity；无合作方为空，用于 Finance Entity 账簿关账时列出相关 OE 流。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '经济事实币种。',
  `apply_status` VARCHAR(32) NOT NULL COMMENT '事实对余额的影响状态。允许值：APPLIED（已入账）、REVERSED（已入账后被替代事实冲回）、PENDING_SUPERSEDED（已被替代但自身事件尚未到达）、SKIPPED（自身事件到达时已被替代，生成 0 流水批次）。',
  `posting_id` BIGINT NULL COMMENT '自身事件到达并入账生成的批次（t_account_posting.id）；仅预登记时为空。',
  `balance_available_time` DATETIME(6) NULL COMMENT '事件载荷携带的应付余额到期转可用 UTC 时间；billing 未给出为空。',
  `maturity_status` VARCHAR(32) NOT NULL DEFAULT 'NOT_APPLICABLE' COMMENT '到期转可用状态。允许值：NOT_APPLICABLE（无到期时间或无 PENDING 流水）、PENDING（等待到期）、MATURED（已全部移动）。',
  `matured_time` DATETIME(6) NULL COMMENT '到期转可用完成的 UTC 微秒时间。',
  `applied_time` DATETIME(6) NULL COMMENT '事实被本服务处理的 UTC 微秒时间；仅预登记时为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '记录最近一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_balance_applied_fact` (`fact_id`, `fact_version`) COMMENT '保证业务唯一性：fact_id、fact_version',
  KEY `idx_applied_fact_oe` (`entity_no`, `currency`, `fact_id`) COMMENT '支持访问路径：entity_no、currency、fact_id',
  KEY `idx_applied_fact_fe` (`finance_entity_id`, `currency`, `fact_id`) COMMENT '支持访问路径：finance_entity_id、currency、fact_id',
  KEY `idx_applied_fact_maturity` (`maturity_status`, `balance_available_time`) COMMENT '支持访问路径：maturity_status、balance_available_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='已处理的经济事实：按 fact_id + fact_version 幂等，记录版本替代关系、对应入账批次与到期转可用进度。';

CREATE TABLE `t_balance_movement` (
  `id` BIGINT NOT NULL COMMENT '账户服务生成的 Snowflake 主键。',
  `entity_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账户所属主体类型。允许值：OPERATING_ENTITY、FINANCE_ENTITY。',
  `entity_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账户所属主体标识。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '移动金额的币种。',
  `account_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '移动所在的 Owner 账户类型代码（CLIENT_PAYABLE、PARTNER_PAYABLE 或 BANK_CASH）。',
  `owner_type` VARCHAR(64) NOT NULL COMMENT '核算对象类型。允许值：CLIENT、LOCAL_PARTNER、CASH_ACCOUNT。',
  `owner_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '核算对象规范十进制标识。',
  `from_bucket` VARCHAR(64) NOT NULL COMMENT '转出余额桶。允许值：PENDING、AVAILABLE、LOCKED。',
  `to_bucket` VARCHAR(64) NOT NULL COMMENT '转入余额桶。允许值：PENDING、AVAILABLE、LOCKED。',
  `amount` DECIMAL(24,8) NOT NULL COMMENT '已按币种精度归整的正移动金额。',
  `source_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发起移动的来源类型。取值来源：account-api MovementSourceType（FACT_MATURITY、PAYOUT_LOCK、PAYOUT_RELEASE、TAX_PAYMENT_LOCK、TAX_PAYMENT_RELEASE）；非固定枚举。',
  `source_ref` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源业务的稳定引用（出款执行 ID、缴税付款 ID、事实 ID:版本:账户 ID），同一来源只移动一次。',
  `posting_id` BIGINT NOT NULL COMMENT '本次移动生成的入账批次（t_account_posting.id）。',
  `operator_id` BIGINT NULL COMMENT '经命令接口发起时的操作人；内部任务为空。',
  `movement_status` VARCHAR(16) NOT NULL COMMENT '移动命令状态。允许值：APPLIED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '记录最近一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_balance_movement_source` (`source_type`, `source_ref`) COMMENT '保证业务唯一性：source_type、source_ref',
  UNIQUE KEY `uk_balance_movement_posting` (`posting_id`) COMMENT '保证业务唯一性：posting_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='余额桶移动命令记录（成熟释放、出款 / 缴税占用与释放、银行现金额度）：按来源幂等，借贷进 t_account_entry，不产生总账分录（决定第 4 条）。';

-- 执行后核对（期望 9 张表）
SELECT COUNT(*) AS table_count FROM information_schema.tables WHERE table_schema = DATABASE();
