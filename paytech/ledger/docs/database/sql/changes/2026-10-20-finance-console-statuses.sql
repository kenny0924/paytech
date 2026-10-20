-- 2026-10-20 财务控制台三项（人工调整与计费事实 / 期间关账与试算 / 记账规则包）：mor_ledger 变更脚本
-- 需求目录：requirements/20261020/finance-人工调整与计费事实/（共用技术方案 technique.md 第 5、6 节，L3 作废规则草稿）。
-- 文件性质：执行文件（变更脚本）。目标库：mor_ledger（本机 local-environment、mor-test）。
-- 内容：只改列注释，自动记账规则版本状态允许值增加 VOIDED（草稿作废）；结构、索引与数据不变。
-- 前置：已执行 2026-10-19-ledger-chart-hierarchy.sql。执行顺序：本文件 → 重启 ledger（新镜像）；本文件可重复执行。
-- 回滚：把注释中的「、VOIDED」去掉后执行同一语句；回滚前须确认表内没有 status = 'VOIDED' 的行（旧镜像无法解析该状态）。
SET NAMES utf8mb4;

ALTER TABLE `t_accounting_rule_bundle_ver`
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '自动记账规则版本的审批和生效状态。允许值：DRAFT、PENDING_APPROVAL、APPROVED、ACTIVE、REJECTED、RETIRED、VOIDED。';
