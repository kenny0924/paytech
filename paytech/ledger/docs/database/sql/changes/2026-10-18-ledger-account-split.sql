-- ledger：t_account 拆为只保存身份的 t_ledger_account，余额迁出到 account（重构第 11b 步，design/04 §3.1）。审阅后手工执行。
-- 需求：requirements/20261017/architecture-V2骨架与模块搬迁/tasks/11b-ledger-account-split.md 第 2 节第 1、12 项。
-- 执行顺序：本文件（mor_ledger）与 paytech/account/docs/database/sql/changes/2026-10-18-account-database.sql（mor_account）
--           在同一发布窗口执行，且早于部署第 11b 步的 ledger-app / account-app；两份文件互不依赖。
-- 前提：目标库为 mor_ledger，且按第 07 步 ledger-schema.sql 建表；本机测试库的 t_account 只有测试残留（测试结束后为空），
--       余额列无需迁移。正式库若已有余额数据，把 debit_total / credit_total / net_balance 迁到 mor_account.t_account_balance
--       不在本文件范围，必须另行设计并评审数据迁移（按 billing 经济事实重放重建，design/04 §4 最后一行）。
-- 影响：
--   1. RENAME TABLE t_account → t_ledger_account；删除 debit_total、credit_total、net_balance、last_journal_sequence、balance_version；
--      id、ledger_book_id、owner_type、create_time 注释与表注释按新模型更新；索引名不变。
--   2. 新建 paytech-messaging 消费端两张标准表 t_consumed_event、t_consumed_aggregate_version（ledger 消费 EconomicFactRecorded）。
--   执行后结构与 ledger-schema.sql 逐列一致；应用与测试不执行本文件。
-- 回退（仅限尚无第 11b 步写入时）：
--   DROP TABLE `t_consumed_aggregate_version`;
--   DROP TABLE `t_consumed_event`;
--   RENAME TABLE `t_ledger_account` TO `t_account`;
--   然后按第 07 步 ledger-schema.sql 中 t_account 的原定义 ADD 五个余额列（DEFAULT 0）并恢复原注释；余额值须由 Journal 重算。
USE mor_ledger;
SET NAMES utf8mb4;

RENAME TABLE `t_account` TO `t_ledger_account`;

ALTER TABLE `t_ledger_account`
  DROP COLUMN `debit_total`,
  DROP COLUMN `credit_total`,
  DROP COLUMN `net_balance`,
  DROP COLUMN `last_journal_sequence`,
  DROP COLUMN `balance_version`,
  MODIFY COLUMN `id` BIGINT NOT NULL COMMENT '服务侧生成的会计科目账户 Snowflake 主键，也是全局账户 ID。',
  MODIFY COLUMN `ledger_book_id` BIGINT NOT NULL COMMENT '该账户严格归属的本库经营主体或财务主体币种账簿。',
  MODIFY COLUMN `owner_type` VARCHAR(64) NOT NULL COMMENT '决定账户按经营主体、内部财务主体、Client、Local Partner、渠道账户、银行账户或税务机关隔离的类型。允许值：OPERATING_ENTITY、FINANCE_ENTITY、CLIENT、LOCAL_PARTNER、CHANNEL_ACCOUNT、CASH_ACCOUNT、TAX_AUTHORITY。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Ledger 数据库中的 UTC 微秒创建时间。',
  COMMENT = '在具体账簿和可过账科目下按核算对象与余额桶建立唯一会计科目账户（只保存身份，不保存余额）；余额由不可变 Journal 聚合得出，Owner 余额桶的实时权威是 account 服务的 t_account_balance（design/04 §3）；CLIENT_PAYABLE 与 PARTNER_PAYABLE 使用 PENDING、AVAILABLE、LOCKED，其中 Partner PENDING 是共享待结算桶；Partner reversal 无法由 PENDING/AVAILABLE/LOCKED liability 吸收时使用资产科目 PARTNER_RECOVERY_RECEIVABLE/NONE；Client Recovery 使用 CLIENT_RECOVERY_RECEIVABLE/NONE，保证金使用 CLIENT_SECURITY_DEPOSIT/NONE；Channel 使用 CHANNEL_UNSETTLED_RECEIVABLE、CHANNEL_SETTLEMENT_RECEIVABLE、CHANNEL_UNSETTLED_PAYABLE、CHANNEL_SETTLEMENT_PAYABLE 四个科目表达交易形成、交易对账完整和银行确认三个阶段，Channel Cost 单独使用 CHANNEL_FEE_ACCRUAL，银行现金使用 BANK_CASH。';

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
