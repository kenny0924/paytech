-- 2026-10-19 ledger 控制科目重构（account 明细账 / ledger 控制科目）：mor_ledger 变更脚本
-- 由需求目录 db/ledger/ddl.sql（第一段：结构与数据清理）与 db/ledger/dml.sql（第二段：规则行删除）合并；两段按顺序执行，内容与需求目录逐字一致。
-- 需求目录：requirements/20261019/finance-account明细账与ledger控制科目重构/。完整建库文件见 ../ledger-schema.sql 与 ../ledger-initial-data.sql。

-- ===================== 第一段：ddl.sql =====================
-- ledger 控制科目重构：mor_ledger 结构变更脚本（阶段 3 设计文件，未执行）
-- 文件性质：执行文件（变更脚本）。目标库：mor_ledger（本机 local-environment、mor-test）。
-- 前置：31 张表（07 建库 + 2026-10-18-ledger-account-split.sql + 2026-10-18-standard-adjustment-rule-bundle.sql）。
-- 执行顺序：本文件 → dml.sql → 重启 ledger（新镜像）。重跑：ALTER 会因列已存在失败，不允许重跑。
-- 回滚：ALTER TABLE t_instruction_line DROP KEY idx_instr_line_entry, DROP COLUMN account_entry_id；注释回退按 git 7acbcf4f 的 ledger-schema.sql；
--       清理掉的 Owner 账户与分录只有测试数据，不恢复。
SET NAMES utf8mb4;

-- 1. 分录行指回 account 流水（跨库引用只存 ID）
ALTER TABLE `t_instruction_line`
  ADD COLUMN `account_entry_id` BIGINT NULL COMMENT 'ACCOUNT_POSTING 指令中 Owner 控制科目行对应的 account 流水（mor_account.t_account_entry.id），用于下钻；规则行与人工行为空；与 accounting_rule_line_id、manual_adjustment_line_id 三选一。' AFTER `manual_adjustment_line_id`,
  ADD KEY `idx_instr_line_entry` (`account_entry_id`) COMMENT '支持访问路径：account_entry_id';

-- 2. 允许值与注释（结构不变）
ALTER TABLE `t_ledger_account`
  MODIFY COLUMN `owner_type` VARCHAR(64) NOT NULL COMMENT '决定账户按经营主体、内部财务主体、渠道账户、银行账户或税务机关隔离的类型；Client / Local Partner 不再在总账开户，其科目以账簿主体为 Owner 的控制账户表达。允许值：OPERATING_ENTITY、FINANCE_ENTITY、CHANNEL_ACCOUNT、CASH_ACCOUNT、TAX_AUTHORITY。',
  MODIFY COLUMN `balance_bucket` VARCHAR(64) NOT NULL COMMENT '总账账户不再按余额桶分账户，一律 NONE；Owner 余额桶由 account 明细账维护。允许值：NONE。',
  COMMENT = '在具体账簿和可过账科目下按核算对象建立唯一会计科目账户（只保存身份，不保存余额）；余额由不可变 Journal 聚合得出。Owner 科目（CLIENT_PAYABLE、CLIENT_SECURITY_DEPOSIT、CLIENT_RECOVERY_RECEIVABLE、PARTNER_PAYABLE、PARTNER_RECOVERY_RECEIVABLE）每账簿每科目一个控制账户，Owner 取账簿主体、桶 NONE，明细在 account 的 t_owner_account（2026-10-18 决定第 3 条）；Channel 使用 CHANNEL_UNSETTLED_RECEIVABLE、CHANNEL_SETTLEMENT_RECEIVABLE、CHANNEL_UNSETTLED_PAYABLE、CHANNEL_SETTLEMENT_PAYABLE 与 CHANNEL_FEE_ACCRUAL，银行存款使用 BANK_CASH / CASH_ACCOUNT，税务机关使用 TAX_PAYABLE。';

ALTER TABLE `t_accounting_instruction`
  MODIFY COLUMN `origin_type` VARCHAR(64) NOT NULL COMMENT '区分规则自动生成、account 入账批次生成和人工调整生成的指令。允许值：AUTO、ACCOUNT_POSTING、MANUAL。',
  MODIFY COLUMN `source_system` VARCHAR(64) NULL COMMENT '自动指令所消费权威事件的来源系统（ACCOUNT_POSTING 指令为 account），人工指令为空。取值来源：Code-MoR 系统代码目录；非固定枚举。',
  MODIFY COLUMN `source_event_key` VARCHAR(128) NULL COMMENT '自动指令所消费权威事件的稳定事件键（ACCOUNT_POSTING 指令为 account 入账批次 ID），人工指令为空。',
  MODIFY COLUMN `source_version` BIGINT NULL COMMENT '自动指令所消费权威事件或事实的版本（ACCOUNT_POSTING 指令为 posting_sequence），人工指令为空。';

ALTER TABLE `t_close_step`
  MODIFY COLUMN `step_type` VARCHAR(64) NOT NULL COMMENT '关账或重开流程中的固定执行步骤；CHECK_ACCOUNT_POSTING_PROGRESS 在一个步骤内做 billing → account、account → ledger 两段进度检查（决定第 9 条）。允许值：LOCK_SEQUENCE、CHECK_ASYNC_AND_JOURNAL、CHECK_ACCOUNT_POSTING_PROGRESS、GENERATE_TRIAL_BALANCE、RESOLVE_EXCEPTIONS、POST_PROFIT_CLOSE、FINAL_TRIAL_BALANCE、PLAN_REOPEN_REVERSAL、POST_REOPEN_REVERSAL。';

ALTER TABLE `t_trial_balance_exception`
  MODIFY COLUMN `resolution_type` VARCHAR(64) NULL COMMENT '终态异常采用的受控解决证据类型。允许值：RECON_RESOLUTION、ACCOUNTING_INSTRUCTION、CONTROLLED_WAIVER。';

-- 3. 清理 Client / Local Partner 账户及引用它们的测试数据（设计 8.4）。执行前先看计数。
SELECT 'owner-accounts' AS step, COUNT(*) AS accounts FROM `t_ledger_account` WHERE `owner_type` IN ('CLIENT', 'LOCAL_PARTNER');
SELECT 'instructions' AS step, COUNT(DISTINCT il.`instruction_id`) AS instructions
  FROM `t_instruction_line` il JOIN `t_ledger_account` a ON a.`id` = il.`account_id`
  WHERE a.`owner_type` IN ('CLIENT', 'LOCAL_PARTNER');

CREATE TEMPORARY TABLE `tmp_owner_instruction` AS
  SELECT DISTINCT il.`instruction_id` FROM `t_instruction_line` il
  JOIN `t_ledger_account` a ON a.`id` = il.`account_id`
  WHERE a.`owner_type` IN ('CLIENT', 'LOCAL_PARTNER');
CREATE TEMPORARY TABLE `tmp_owner_entry` AS
  SELECT je.`id` FROM `t_journal_entry` je WHERE je.`instruction_id` IN (SELECT `instruction_id` FROM `tmp_owner_instruction`);

DELETE FROM `t_journal_line` WHERE `journal_entry_id` IN (SELECT `id` FROM `tmp_owner_entry`);
DELETE FROM `t_journal_entry` WHERE `id` IN (SELECT `id` FROM `tmp_owner_entry`);
DELETE FROM `t_instruction_line` WHERE `instruction_id` IN (SELECT `instruction_id` FROM `tmp_owner_instruction`);
DELETE FROM `t_accounting_instruction` WHERE `id` IN (SELECT `instruction_id` FROM `tmp_owner_instruction`);
-- t_trial_balance_line 与 t_ledger_period_balance 按 chart_account_id 聚合、不按账户，不在此删除；被删分录影响的试算 / 期间余额
-- 只有测试数据，重跑试算即重建。
DELETE FROM `t_ledger_account` WHERE `owner_type` IN ('CLIENT', 'LOCAL_PARTNER');
DROP TEMPORARY TABLE `tmp_owner_entry`;
DROP TEMPORARY TABLE `tmp_owner_instruction`;

-- 账簿序号不回收：被删除分录留下的 journal_sequence 缺口只存在于测试数据，关账前试算按现有分录聚合。
-- 执行后核对（期望 31 张表，t_instruction_line 含 account_entry_id）
SELECT COUNT(*) AS table_count FROM information_schema.tables WHERE table_schema = DATABASE();
SELECT COUNT(*) AS entry_column FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_instruction_line' AND column_name = 'account_entry_id';

-- ===================== 第二段：dml.sql =====================
-- ledger 控制科目重构：mor_ledger 初始化数据变更（阶段 3 设计文件，未执行）
-- 文件性质：执行文件（增量 DML）。目标库：mor_ledger。在 ddl.sql 之后执行；可重跑（DELETE 幂等）。
-- 内容：规则包删除 Owner 角色（CLIENT、LOCAL_PARTNER）规则行；Owner 科目行由 account 入账批次的流水生成（技术方案 2.1）。
-- 对应完整初始化文件：paytech/ledger/docs/database/sql/ledger-initial-data.sql（阶段 4 同步删除同样的 8 行，保留行数 86 → 78）。
-- 回滚：按 git 7acbcf4f 的 ledger-initial-data.sql 重新插入 id 5202、5208、5315–5320。
SET NAMES utf8mb4;

START TRANSACTION;

-- STANDARD_TRANSACTION / PAYMENT_SUCCEEDED：Client 本金应付、Partner 分成应付
DELETE FROM `t_accounting_rule_line` WHERE `id` IN (5202, 5208) AND `owner_role` IN ('CLIENT', 'LOCAL_PARTNER');

-- STANDARD_ADJUSTMENT / ECONOMIC_ADJUSTMENT：Client 本金 / 手续费借贷、Partner 分成借贷
DELETE FROM `t_accounting_rule_line` WHERE `id` BETWEEN 5315 AND 5320 AND `owner_role` IN ('CLIENT', 'LOCAL_PARTNER');

-- 核对：规则包内不再有 Owner 角色行（期望 0）
SELECT COUNT(*) AS owner_rule_lines FROM `t_accounting_rule_line` WHERE `owner_role` IN ('CLIENT', 'LOCAL_PARTNER');
-- 核对：规则行总数（期望 36 = STANDARD_TRANSACTION 10 + STANDARD_ADJUSTMENT 26）
SELECT COUNT(*) AS rule_lines FROM `t_accounting_rule_line` WHERE `bundle_ver_id` IN (5101, 5102);

COMMIT;
