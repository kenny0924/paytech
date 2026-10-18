-- account-service Mock 测试环境准备（管理员手工执行；应用与测试从不自动执行）。
-- 测试复用仓库 local-environment 的 MySQL；账号与 application-mock.yml 一致，只授予本库最小 DML 权限（design/10 §4.2）。
-- 已有库和账号不删除、不重置。表结构逐字取自 paytech/account/docs/database/sql/account-schema.sql 的全部 8 张表；本库没有保留行。
-- 第 11b 步起 account 自有 mor_account；第 07 步临时授予 account_app 的 mor_ledger 只读权限不在本文件撤销，撤销草案见
-- paytech/account/docs/database/sql/changes/2026-10-18-account-database.sql。
CREATE DATABASE IF NOT EXISTS mor_account DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'account_app'@'%' IDENTIFIED BY 'bc991f08e64ba1551ce6e2f8e610defc65e511dfbaf1e37c';
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_account.* TO 'account_app'@'%';
USE mor_account;
SET NAMES utf8mb4;

CREATE TABLE `t_account_balance` (
  `id` BIGINT NOT NULL COMMENT '账户服务生成的余额账户 Snowflake 主键；与 ledger 账户身份按 (账簿主体, 科目代码, Owner, 余额桶) 对应，不复用 ledger 账户 ID。',
  `book_entity_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '余额所属 ledger 账簿的主体类型。允许值：OPERATING_ENTITY、FINANCE_ENTITY。',
  `book_entity_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '余额所属 ledger 账簿的主体标识：OE 为 entityNo，Finance Entity 为 ledger 本地主键十进制字符串；来源 Owner 为 ledger，不建立跨库外键。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '余额所属账簿的大写 ISO 4217 币种。',
  `account_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '余额对应的 ledger 标准科目代码（如 CLIENT_PAYABLE）；科目名称与属性以 ledger 为准，本表不复制。',
  `owner_type` VARCHAR(64) NOT NULL COMMENT '余额隔离的核算对象类型。允许值：CLIENT、LOCAL_PARTNER、CHANNEL_ACCOUNT、CASH_ACCOUNT。',
  `owner_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '按 owner_type 解释的规范十进制稳定标识；来源 Owner 为 CCT 或 reconciliation，不建立跨库外键。',
  `balance_bucket` VARCHAR(64) NOT NULL COMMENT '应付款余额桶；独立科目使用 NONE。允许值：PENDING、AVAILABLE、LOCKED、NONE。',
  `normal_side` VARCHAR(16) NOT NULL COMMENT '科目正常余额方向，由映射规则冻结，用于计算净余额。允许值：DEBIT、CREDIT。',
  `debit_total` DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '已应用经济事实累计的借方金额。',
  `credit_total` DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '已应用经济事实累计的贷方金额。',
  `net_balance` DECIMAL(24,8) NOT NULL DEFAULT 0 COMMENT '按正常方向计算、允许为负的当前净余额；是 Owner 可用额度的实时权威。',
  `balance_version` BIGINT NOT NULL DEFAULT 0 COMMENT '余额条件更新使用的乐观锁版本，每次写入加一。',
  `last_fact_id` BIGINT NULL COMMENT '最近一次改变本余额的 billing 经济事实 ID；尚未发生变化时为空。',
  `last_fact_version` BIGINT NULL COMMENT '最近一次改变本余额的经济事实版本；尚未发生变化时为空。',
  `status` VARCHAR(16) NOT NULL COMMENT '余额账户状态。允许值：ACTIVE。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '记录最近一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account_balance_identity` (`book_entity_type`, `book_entity_id`, `currency`, `account_code`, `owner_type`, `owner_id`, `balance_bucket`) COMMENT '保证业务唯一性：book_entity_type、book_entity_id、currency、account_code、owner_type、owner_id、balance_bucket',
  KEY `idx_account_balance_owner` (`owner_type`, `owner_id`, `book_entity_id`, `currency`) COMMENT '支持访问路径：owner_type、owner_id、book_entity_id、currency'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Owner 余额桶的实时权威（design/04 §3.2）：只由 EconomicFactRecorded 消费与按 billing 经济事实重建维护，余额版本 CAS 保护并发写入；与 ledger 分录的一致性由日终核对检查。';

CREATE TABLE `t_balance_applied_fact` (
  `id` BIGINT NOT NULL COMMENT '账户服务生成的 Snowflake 主键。',
  `fact_id` BIGINT NOT NULL COMMENT 'billing 经济事实 ID（t_economic_fact.id）；来源 Owner 为 billing，不建立跨库外键。',
  `fact_version` BIGINT NOT NULL COMMENT '经济事实版本；被替代事实先于自身事件到达时按替代事实版本减一登记。',
  `fact_type` VARCHAR(64) NULL COMMENT '经济事实类型代码；仅由替代关系预登记、自身事件尚未到达时为空。取值来源：billing EconomicsType；非固定枚举。',
  `supersedes_fact_id` BIGINT NULL COMMENT '本事实替代的前一经济事实；首次登记为空。',
  `superseded_by_fact_id` BIGINT NULL COMMENT '替代本事实的后一经济事实；未被替代时为空。',
  `entity_no` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '经济事实头的 Operating Entity 编号，用于按 OE 账簿统计已消费事实。',
  `finance_entity_id` BIGINT NULL COMMENT '经济事实头的平台服务费 Finance Entity；无合作方为空，用于按 Finance Entity 账簿统计已消费事实。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '经济事实币种。',
  `apply_status` VARCHAR(32) NOT NULL COMMENT '事实对余额的影响状态：APPLIED 已应用；REVERSED 已应用后被替代事实冲回；PENDING_SUPERSEDED 已被替代但自身事件尚未到达；SKIPPED 自身事件到达时已被替代、未应用。允许值：APPLIED、REVERSED、PENDING_SUPERSEDED、SKIPPED。',
  `lines_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '已应用余额变化的规范文本 SHA-256 小写十六进制；仅预登记、尚未处理时为空。',
  `applied_time` DATETIME(6) NULL COMMENT '事实被本服务处理的 UTC 微秒时间；仅预登记时为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '记录最近一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_balance_applied_fact` (`fact_id`, `fact_version`) COMMENT '保证业务唯一性：fact_id、fact_version',
  KEY `idx_applied_fact_oe_book` (`entity_no`, `currency`, `fact_id`) COMMENT '支持访问路径：entity_no、currency、fact_id',
  KEY `idx_applied_fact_fe_book` (`finance_entity_id`, `currency`, `fact_id`) COMMENT '支持访问路径：finance_entity_id、currency、fact_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='已处理的经济事实（design/04 §3.2）：按 fact_id + fact_version 幂等，并记录版本替代关系，使替代事实可冲回被替代事实的余额变化。';

CREATE TABLE `t_balance_applied_entry` (
  `id` BIGINT NOT NULL COMMENT '账户服务生成的 Snowflake 主键。',
  `applied_fact_id` BIGINT NOT NULL COMMENT '所属已处理事实（t_balance_applied_fact.id）。',
  `fact_id` BIGINT NOT NULL COMMENT 'billing 经济事实 ID，冗余保存便于按事实冲回。',
  `line_no` INTEGER NOT NULL COMMENT '产生该变化的经济事实明细行号；交易事实的总额（GROSS_AMOUNT）取自事实头，行号为 0。',
  `balance_id` BIGINT NOT NULL COMMENT '被改变的余额账户（t_account_balance.id）。',
  `entry_side` VARCHAR(16) NOT NULL COMMENT '对余额账户的借贷方向。允许值：DEBIT、CREDIT。',
  `amount` DECIMAL(24,8) NOT NULL COMMENT '已按币种精度归整的正金额。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  KEY `idx_applied_entry_fact` (`applied_fact_id`) COMMENT '支持访问路径：applied_fact_id',
  KEY `idx_applied_entry_balance` (`balance_id`) COMMENT '支持访问路径：balance_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='不可变的已应用余额变化：一条经济事实映射到余额账户的逐行借贷，供替代事实冲回与重建核对。';

CREATE TABLE `t_balance_movement` (
  `id` BIGINT NOT NULL COMMENT '账户服务生成的 Snowflake 主键。',
  `book_entity_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '余额所属 ledger 账簿的主体类型。允许值：OPERATING_ENTITY、FINANCE_ENTITY。',
  `book_entity_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '余额所属 ledger 账簿的主体标识。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '移动金额的币种。',
  `movement_sequence` BIGINT NOT NULL COMMENT '同一账簿主体与币种内连续递增的移动序号，即 BalanceMoved 的 aggregateVersion。',
  `account_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '移动所在的标准科目代码（CLIENT_PAYABLE 或 PARTNER_PAYABLE）。',
  `owner_type` VARCHAR(64) NOT NULL COMMENT '核算对象类型。允许值：CLIENT、LOCAL_PARTNER。',
  `owner_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '核算对象规范十进制标识。',
  `from_bucket` VARCHAR(64) NOT NULL COMMENT '转出余额桶。允许值：PENDING、AVAILABLE、LOCKED。',
  `to_bucket` VARCHAR(64) NOT NULL COMMENT '转入余额桶。允许值：PENDING、AVAILABLE、LOCKED。',
  `amount` DECIMAL(24,8) NOT NULL COMMENT '已按币种精度归整的正移动金额。',
  `source_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发起移动的业务来源类型（如出款锁定、成熟释放）。取值来源：account 移动来源目录；非固定枚举。',
  `source_ref` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源业务的稳定引用，同一来源只移动一次。',
  `movement_status` VARCHAR(16) NOT NULL COMMENT '移动命令状态。允许值：APPLIED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '记录最近一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_balance_movement_source` (`source_type`, `source_ref`) COMMENT '保证业务唯一性：source_type、source_ref',
  UNIQUE KEY `uk_balance_movement_sequence` (`book_entity_type`, `book_entity_id`, `currency`, `movement_sequence`) COMMENT '保证业务唯一性：book_entity_type、book_entity_id、currency、movement_sequence'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='账户侧余额桶移动命令记录（AVAILABLE → LOCKED 等），移动后发布 BalanceMoved 供 ledger 补记分录（design/04 §5）；阶段 0 只建表，无生产调用方。';

CREATE TABLE `t_balance_discrepancy` (
  `id` BIGINT NOT NULL COMMENT '账户服务生成的 Snowflake 主键。',
  `recon_date` DATE NOT NULL COMMENT '核对任务执行的 UTC 日期。',
  `ledger_book_id` BIGINT NOT NULL COMMENT '被核对的 ledger 账簿主键；来源 Owner 为 ledger，不建立跨库外键。',
  `book_entity_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账簿主体类型。允许值：OPERATING_ENTITY、FINANCE_ENTITY。',
  `book_entity_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账簿主体标识。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账簿币种。',
  `account_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '存在差异的标准科目代码。',
  `owner_type` VARCHAR(64) NOT NULL COMMENT '核算对象类型。允许值：CLIENT、LOCAL_PARTNER、CHANNEL_ACCOUNT、CASH_ACCOUNT。',
  `owner_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '核算对象标识。',
  `balance_bucket` VARCHAR(64) NOT NULL COMMENT '余额桶。允许值：PENDING、AVAILABLE、LOCKED、NONE。',
  `account_net_balance` DECIMAL(24,8) NULL COMMENT '账户服务余额的净额；账户服务没有该余额时为空。',
  `ledger_net_balance` DECIMAL(24,8) NULL COMMENT 'ledger 按分录聚合的净额；ledger 没有该账户时为空。',
  `difference_amount` DECIMAL(24,8) NOT NULL COMMENT '两侧净额差的绝对值，缺失一侧按零计。',
  `status` VARCHAR(16) NOT NULL COMMENT '差异处置状态；阶段 0 不接工单，只记录。允许值：OPEN。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '记录最近一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_balance_discrepancy` (`recon_date`, `ledger_book_id`, `account_code`, `owner_type`, `owner_id`, `balance_bucket`) COMMENT '保证业务唯一性：recon_date、ledger_book_id、account_code、owner_type、owner_id、balance_bucket',
  KEY `idx_balance_discrepancy_status` (`status`, `recon_date`) COMMENT '支持访问路径：status、recon_date'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='日终核对发现的账户余额与 ledger 分录聚合余额差异（design/04 §7）；裁决依据是 billing 经济事实，阶段 0 不接工单。';

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
