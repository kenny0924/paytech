-- mor_payout · 结算出款切片 S1 首建（requirements/20261030/payout-收款账户与余额解释，technique.md 第 7 节）
-- 性质：评审稿（阶段 3），待执行文件。目标库 mor_payout（新建空库；本机 local 与 mor-test 同一文件）。未执行。
-- 前置：库 mor_payout 与账号 payout_app（SELECT, INSERT, UPDATE, DELETE）已由 local-environment init 脚本 / test-environment initialize.py 创建。
-- 内容：t_beneficiary_account（31 列含生成列 active_key，按 2026-10-30 决定第 4 节与阶段 2 S-01 改造；阶段 4 增 review_maker_note、reason_note，
--   已建库用 ddl-20261030-notes.sql 增量补列）、t_beneficiary_account_event（13 列）、t_compensation_event（标准表）。
--   payout.pdma.json 中其余 5 张出款表（t_payout、t_payout_approval、t_payout_execution、t_payout_evidence_ref、t_payout_event）与
--   t_finance_restriction_proj 本期不建，随 S2 改造后首建。无 FOREIGN KEY / CHECK（DATABASE_DESIGN_GUIDE 5.1）。
-- 重复执行：CREATE TABLE 已存在即报错中止；本文件只用于空库。回退：本需求未产生业务数据前可 DROP 三表；产生数据后不回退，保留库。
SET NAMES utf8mb4;
USE `mor_payout`;

CREATE TABLE `t_beneficiary_account` (
  `id` BIGINT NOT NULL COMMENT '收款账户聚合根的 Snowflake 主键；一行即一个可被历史出款精确引用的账户版本，冻结字段变化必须新建账户。',
  `owner_type` VARCHAR(32) NOT NULL COMMENT '收款账户所属 Owner 类型。允许值：CLIENT、LOCAL_PARTNER（PAYEE 为代发预留，本期不开放）。',
  `owner_id` BIGINT NOT NULL COMMENT 'Client 或 Local Partner 的 client-master 稳定标识；不建立跨库关系。',
  `account_code` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Owner 范围内可读编号，格式 BA- 加 4 位以上递增序号（锁 Owner 后取最大值加一）；只用于展示与沟通。',
  `rail_type` VARCHAR(16) NOT NULL COMMENT '收款方式，决定字段要求。允许值：LOCAL、SWIFT。',
  `bank_country` CHAR(2) NOT NULL COMMENT '开户银行所在国家的 ISO 3166-1 alpha-2 代码。取值来源：Platform 参考数据 t_ref_country；非固定枚举。',
  `currency` CHAR(3) NOT NULL COMMENT '收款币种的 ISO 4217 三位代码。取值来源：Platform 参考数据 t_ref_currency；非固定枚举。',
  `bank_name` VARCHAR(128) NOT NULL COMMENT '开户银行显示名称，非敏感。',
  `bank_code` VARCHAR(32) NULL COMMENT '本地银行代码或 SWIFT BIC，非敏感；只用 IBAN 的国家为空。',
  `beneficiary_vault_token` VARCHAR(128) NOT NULL COMMENT 'PAYOUT_BENEFICIARY_V1 的 REFERENCE Token，值为 JSON（户名、账号或 IBAN、银行代码、SWIFT BIC、账户类型）；只在运营核验与出款执行前 Reveal。',
  `bank_account_token` VARCHAR(128) NOT NULL COMMENT 'PAYOUT_BANK_ACCOUNT_KEY_V1 的 STABLE Token，值为规范化账号（去空白、转大写）；同值同 Token，用于同 Owner 查重与跨 Owner 共用提示。',
  `holder_name_masked` VARCHAR(255) NOT NULL COMMENT '户名脱敏值，可用于页面与通知展示。',
  `account_last4` CHAR(4) NOT NULL COMMENT '账号或 IBAN 末四位。',
  `content_hash` CHAR(64) NOT NULL COMMENT '提交时冻结的全部账户字段 Canonical SHA-256；复核人审阅的哈希必须与之一致。',
  `submitted_by_type` VARCHAR(16) NOT NULL COMMENT '提交人类型。允许值：CLIENT_MEMBER、OPERATIONS。',
  `submitted_by` BIGINT NOT NULL COMMENT '提交人的 Platform 账号标识。',
  `review_maker_id` BIGINT NULL COMMENT '核验意见人（Maker）的 Platform 账号标识；未出具意见时为空。',
  `review_maker_decision` VARCHAR(16) NULL COMMENT 'Maker 核验意见。允许值：APPROVE、REJECT。',
  `review_maker_note` VARCHAR(500) NULL COMMENT 'Maker 核验意见的内部备注（可选，最多 500 字符），只给运营查看；恢复重新核验时清空。',
  `checker_id` BIGINT NULL COMMENT '复核人（Checker）的 Platform 账号标识，必须不同于 Maker 与运营代提交人。',
  `checker_decision` VARCHAR(16) NULL COMMENT '复核决定。允许值：APPROVED、REJECTED。',
  `decision_time` DATETIME(6) NULL COMMENT '复核决定的 UTC 微秒时间。',
  `reason_code` VARCHAR(64) NULL COMMENT '最近一次拒绝、停用或关闭的原因代码。取值来源：payout-api BeneficiaryRejectReason / BeneficiarySuspendReason / BeneficiaryCloseReason；非固定枚举。',
  `reason_note` VARCHAR(500) NULL COMMENT '拒绝时运营给 Owner 的说明（可选，最多 500 字符），随拒绝通知的 reasonText 发送；停用、关闭、恢复时清空。',
  `cooling_until` DATETIME(6) NULL COMMENT '冷却结束的 UTC 微秒时间 = decision_time + 配置冷却小时；早于此时刻的 VERIFIED 账户不可用于出款。',
  `is_default` TINYINT NOT NULL DEFAULT 0 COMMENT '是否为同 Owner、同币种的默认收款账户，1 是 0 否；至多一个由应用在 Owner 锁内保证。',
  `status` VARCHAR(16) NOT NULL COMMENT '账户状态。允许值：PENDING_REVIEW、VERIFIED、REJECTED、SUSPENDED、CLOSED。',
  `active_key` BIGINT AS (IF(`status` IN ('REJECTED', 'CLOSED'), `id`, 0)) STORED NOT NULL COMMENT '生成列：未终结账户为 0、终结账户为自身主键，使同 Owner 同账号只能有一个未终结账户。',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT '聚合根条件更新使用的乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '记录最近一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_beneficiary_acct` (`owner_type`, `owner_id`, `account_code`) COMMENT '保证业务唯一性：owner_type、owner_id、account_code',
  UNIQUE KEY `uk_beneficiary_owner_token` (`owner_type`, `owner_id`, `bank_account_token`, `active_key`) COMMENT '保证业务唯一性：同 Owner 同账号只能有一个未终结账户',
  KEY `idx_beneficiary_owner` (`owner_type`, `owner_id`, `status`) COMMENT '支持访问路径：owner_type、owner_id、status',
  KEY `idx_beneficiary_token` (`bank_account_token`) COMMENT '支持访问路径：跨 Owner 共用账号提示',
  KEY `idx_beneficiary_review` (`status`, `review_maker_id`, `create_time`) COMMENT '支持访问路径：核验队列'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Owner 收款账户（2026-10-30 payout 决定 P11）：原值只在 Vault；提交即冻结，Maker-Checker 核验后冷却可用。';

CREATE TABLE `t_beneficiary_account_event` (
  `id` BIGINT NOT NULL COMMENT '收款账户事件的 Snowflake 主键。',
  `beneficiary_account_id` BIGINT NOT NULL COMMENT '所属本库收款账户。',
  `event_no` INT NOT NULL COMMENT '同一账户内从 1 开始严格连续的事件序号，在账户行锁内分配。',
  `from_status` VARCHAR(16) NULL COMMENT '变更前状态；首条事件为空。允许值：PENDING_REVIEW、VERIFIED、REJECTED、SUSPENDED、CLOSED。',
  `to_status` VARCHAR(16) NOT NULL COMMENT '变更后状态；不改状态的动作（设默认、Reveal 等）与 from_status 相同。',
  `action` VARCHAR(32) NOT NULL COMMENT '触发动作。允许值：SUBMIT、MAKER_REVIEW、CHECKER_DECIDE、SET_DEFAULT、UNSET_DEFAULT、SUSPEND、RESUME、CLOSE、REVEAL。',
  `reason_code` VARCHAR(64) NULL COMMENT '动作原因代码；取值来源同 t_beneficiary_account.reason_code。',
  `actor_type` VARCHAR(16) NOT NULL COMMENT '操作人类型。允许值：CLIENT_MEMBER、OPERATIONS、SYSTEM。',
  `actor_id` BIGINT NULL COMMENT '操作人的 Platform 账号标识；SYSTEM 为空。',
  `source_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '触发命令的稳定来源键（Gateway 请求 ID 或命令业务键），用于审计关联。',
  `account_version_after` BIGINT NOT NULL COMMENT '同一事务完成后账户聚合根的版本。',
  `occurred_time` DATETIME(6) NOT NULL COMMENT '事件发生的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_beneficiary_event_no` (`beneficiary_account_id`, `event_no`) COMMENT '保证业务唯一性：beneficiary_account_id、event_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='收款账户不可变事件链：每次状态变化与敏感读取与账户在同一本地事务追加。';

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
