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
