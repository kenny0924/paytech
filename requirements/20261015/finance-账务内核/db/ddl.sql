-- 需求：Finance Accounting 账务内核（第一批）
-- 性质：本地执行脚本（第一步）+ 完整 Schema 引用。已于 2026-10-15 经用户授权在本地 mor_finance 执行，见 delivery.md。
-- 目标库：本地 mor-local-mysql-1 / 127.0.0.1:13306 / mor_finance（不涉及 mor-test 或生产）。
-- 前置：旧余额切片 3 表全部 0 行（2026-10-15 只读核对）；执行前再次确认，否则停止。
--   SELECT (SELECT COUNT(*) FROM t_ledger_book) + (SELECT COUNT(*) FROM t_chart_account) + (SELECT COUNT(*) FROM t_account);
-- 备份：mysqldump --no-data 保存旧 3 表结构到 scratchpad；无数据可备份。
-- 顺序：
--   1. 本文件：删除旧切片 3 表（结构与新 Schema 不兼容，entity_id/owner_id 为 BIGINT）。
--   2. finance-service/docs/database/sql/finance-schema.sql：创建完整 84 表（唯一执行来源，不复制）。
--   3. finance-service/docs/database/sql/finance-initial-data.sql：初始化 USD 与标准科目表。
-- 回滚：DROP 第 2 步创建的 84 表，按 git 历史中的 finance-balance-query-schema.sql（本需求已删除该文件）重建旧 3 表（均无数据）。
-- 不允许重跑：第 2、3 步对已存在对象会失败，需先按回滚步骤恢复空库。

DROP TABLE `t_account`;
DROP TABLE `t_chart_account`;
DROP TABLE `t_ledger_book`;
