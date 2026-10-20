-- Code-MoR target schema generated from the approved PDManer model.
-- Apply manually to an EMPTY database only; existing environments require a reviewed migration.
-- Execution status: agent-docs/CURRENT_IMPLEMENTATION.md. Verify Java compatibility before rollout.
-- Source: paytech/account/docs/database/account.pdma.json
SET NAMES utf8mb4;

CREATE TABLE `t_owner_account` (
  `id` BIGINT NOT NULL COMMENT '账户服务生成的 Owner 账户 Snowflake 主键；ledger 分录行经 t_account_entry 下钻，不复用 ledger 账户 ID。',
  `account_no` VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '财务人员可读账户号：{主体}-{币种}-{科目编号}-S{序号}，S 表示明细账账户，序号按主体、币种与科目代码从 1 递增、至少 6 位；主体段 OE 为 entityNo、Finance Entity 为 FE 加主体主键；创建时生成后不再改变，只用于显示与搜索，不进入事件载荷、不作为跨系统关联键。',
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
  UNIQUE KEY `uk_owner_account_no` (`account_no`) COMMENT '保证业务唯一性：account_no',
  KEY `idx_owner_account_owner` (`owner_type`, `owner_id`, `entity_id`, `currency`) COMMENT '支持访问路径：owner_type、owner_id、entity_id、currency',
  KEY `idx_owner_account_code` (`entity_type`, `entity_id`, `currency`, `account_code`) COMMENT '支持访问路径：entity_type、entity_id、currency、account_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='明细账 Owner 账户（2026-10-18 决定第 2 条）：Client / Local Partner / 银行现金在每个主体 + 币种 + 科目代码 + 余额桶下的唯一账户主数据与实时余额；余额只由本库流水维护并可从流水重算。';

CREATE TABLE `t_owner_account_no_sequence` (
  `id` BIGINT NOT NULL COMMENT '账户号序列的服务侧雪花主键。',
  `entity_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账户所属法律主体类型。允许值：OPERATING_ENTITY、FINANCE_ENTITY。',
  `entity_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账户所属主体标识：OE 为 CCT entityNo，Finance Entity 为 ledger 主数据主键十进制字符串。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账户币种，ISO 4217 大写三位代码。',
  `account_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账户类型目录中的科目代码。',
  `last_value` BIGINT NOT NULL COMMENT '同主体同币种同科目最近一次分配给账户号的序号；在开户本地事务中以 INSERT … ON DUPLICATE KEY UPDATE 原子递增，已分配序号不复用，允许空洞。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 account 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_owner_account_no_seq` (`entity_type`, `entity_id`, `currency`, `account_code`) COMMENT '保证业务唯一性：entity_type、entity_id、currency、account_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='按主体、币种与科目代码为明细账账户分配可读账户号序号。';

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

CREATE TABLE `t_consumed_event` (
  `event_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件信封 eventId（默认等于 eventKey），按其去重。',
  `event_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '已消费领域事件的类型（PascalCase，登记于 paytech-messaging 事件目录）。',
  `aggregate_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件所属聚合的大写类型代码。',
  `aggregate_id` VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件所属聚合的标识，同时是 RabbitMQ 分区路由键。',
  `aggregate_version` BIGINT NOT NULL COMMENT '本条已消费领域事件携带的聚合版本。',
  `consumed_at` DATETIME(6) NOT NULL COMMENT '消费事务提交的 UTC 微秒精度时间。',
  PRIMARY KEY (`event_id`),
  KEY `idx_consumed_event_aggregate` (`aggregate_type`, `aggregate_id`, `aggregate_version`) COMMENT '访问索引：aggregate_type、aggregate_id、aggregate_version'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='已消费领域事件：paytech-messaging 消费端按事件 ID 去重的标准表（design/08 §4），列、类型、主键与索引与 paytech-messaging 的 messaging-consumed-event.sql 一致；事件与版本合法性由消费拦截器保证。';

CREATE TABLE `t_consumed_aggregate_version` (
  `aggregate_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件所属聚合的大写类型代码。',
  `aggregate_id` VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件所属聚合的标识，同时是 RabbitMQ 分区路由键。',
  `last_version` BIGINT NOT NULL COMMENT '本服务已处理的该聚合最大领域事件版本。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`aggregate_type`, `aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='已消费聚合版本：paytech-messaging 消费端按聚合记录已处理的最大版本，用于判定乱序、重放与缺口（design/08 §4）；列、类型与主键与 messaging-consumed-event.sql 一致。';

CREATE TABLE `t_compensation_event` (
  `id` BIGINT NOT NULL COMMENT '本库补偿事件记录的 Snowflake 主键，不作为跨系统事件标识。',
  `event_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Handler 读取本服务权威事实使用的稳定业务键，最长 128 个字符；去首尾空白、拒绝控制字符，大小写敏感。',
  `event_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '全应用唯一注册的 Handler 类型，最长 64 个字符，大小写敏感。取值来源：本服务 compensation-event-catalog.yaml；非固定枚举。',
  `sub_event_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '同一 Handler 下的受控业务子类型；无业务子类型时为占位值 -；与 event_key、event_type 共同组成唯一身份。取值来源：本服务补偿子类型目录；非固定枚举。',
  `payload_json` VARCHAR(4096) NULL COMMENT '可选最小 JSON Object，通常为空；最多 4 KiB，不得保存可从本库恢复的上下文、敏感明文或凭据。',
  `execute_status` VARCHAR(16) NOT NULL DEFAULT 'INIT' COMMENT '本地补偿任务执行状态；成功后删除记录。允许值：INIT、PROCESSING、ERROR、FAILED。',
  `execute_count` INTEGER NOT NULL DEFAULT 0 COMMENT '已开始的 Handler 尝试次数，非负，Claim 时增加。',
  `next_execute_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '任务下次允许 Claim 的 UTC 微秒时间，由 Handler 退避策略更新。',
  `lock_owner` VARCHAR(64) NULL COMMENT '当前持有数据库 Claim Lease 的服务实例标识；未被认领时为空。',
  `lock_until` DATETIME(6) NULL COMMENT 'Claim Lease 到期并可被其他实例接管的 UTC 微秒时间；无活动 Lease 时为空。',
  `last_error_code` VARCHAR(64) NULL COMMENT '最近一次执行失败的稳定错误分类代码；尚无错误时为空。取值来源：本服务补偿执行错误代码目录；非固定枚举。',
  `last_error_summary` VARCHAR(1000) NULL COMMENT '最近一次执行失败的脱敏摘要，最长 1000 字符，禁止保存敏感明文。',
  `lock_version` BIGINT NOT NULL DEFAULT 1 COMMENT 'Claim 和 Lease 使用的正乐观锁版本；登记初始值固定为 1，仅当前 Lease 持有者可回写执行结果。',
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '补偿记录与业务事实原子登记的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '补偿记录最近更新的 UTC 微秒时间，由写入口维护。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comp_event_identity` (`event_key`, `event_type`, `sub_event_type`) COMMENT '保证业务唯一性：event_key、event_type、sub_event_type',
  KEY `idx_comp_event_ready` (`execute_status`, `next_execute_at`, `id`) COMMENT '支持访问路径：execute_status、next_execute_at、id',
  KEY `idx_comp_event_lease` (`execute_status`, `lock_until`, `id`) COMMENT '支持访问路径：execute_status、lock_until、id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='与本库业务事实在同一事务登记的本地可靠执行记录（paytech-compensation-event 现行模型，COMPENSATION_EVENT_GUIDE 第 3 节）；成功后删除，FAILED 保留并可受控重开';
