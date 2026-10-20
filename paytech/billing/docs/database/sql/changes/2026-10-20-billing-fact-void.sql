-- billing：计费事实草稿作废（需求 requirements/20261020/finance-人工调整与计费事实，技术方案 B2 与第 6 节）。审阅后手工执行，应用与测试不执行本文件。
-- 目标库：mor_billing。前提：t_billing_fact 已按 2026-10-19-billing-fact-model.sql 建立。
-- 影响：只改 t_billing_fact.status 列注释（允许值增加 VOIDED），列类型、可空、默认值与索引不变，不改数据；执行后与 billing-schema.sql、billing.pdma.json 一致。
-- 发布顺序：本文件可在新版 billing-service 发布前或后执行；新代码写入 VOIDED 不依赖注释。
-- 回退：将注释改回 '登记状态。允许值：DRAFT（仅 MANUAL）、APPROVED、SUPERSEDED。'（列定义其余部分同下）；若已有 VOIDED 行，回退旧版应用前须先确认旧代码读取到未知状态会失败关闭。
SET NAMES utf8mb4;

ALTER TABLE `t_billing_fact`
  MODIFY COLUMN `status` VARCHAR(16) NOT NULL COMMENT '登记状态：MANUAL 草稿 DRAFT 经批准为 APPROVED 或作废为 VOIDED（终态、不发布事件），APPROVED 被替代后为 SUPERSEDED。允许值：DRAFT（仅 MANUAL）、APPROVED、SUPERSEDED、VOIDED（仅 MANUAL）。';
