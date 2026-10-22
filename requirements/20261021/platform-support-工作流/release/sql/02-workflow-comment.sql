-- Platform Support 工作流：草稿大小上限调整后的列注释同步（待执行，需授权）。审阅后手工执行。
-- 需求：requirements/20261021/platform-support-工作流（主会话决定：graph_json 不超过 48 KiB，start_form_json 不超过 12 KiB，
--       草稿保存与发布校验同时执行；草稿路由 Body 上限保持 65536）。
-- 目标库：Platform Support（mor_platform）。
-- 前提：已执行 changes/2026-10-22-workflow.sql，且 graph_json 注释仍为“不超过 256 KiB”。预检：
--   SELECT COLUMN_NAME, COLUMN_COMMENT FROM information_schema.COLUMNS
--    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_workflow_definition_version'
--      AND COLUMN_NAME IN ('graph_json', 'start_form_json');
-- 影响：只改两列注释，类型、可空性与数据不变（MODIFY 原样重复列定义）；上限由应用校验，不建数据库约束。
-- 执行后核对：SHOW CREATE TABLE t_workflow_definition_version 与 platform-support-schema.sql、platform-support.pdma.json 一致。
-- 回滚：以原注释重新执行同样的 MODIFY：
--   graph_json：'节点、连线、排他条件、审批、动作引用与通知规则，结构见工作流技术方案 3.1；不超过 256 KiB。'
--   start_form_json：'启动表单字段定义，与表单动作共用结构，见工作流技术方案 3.2；最多 50 个字段。'
ALTER TABLE `t_workflow_definition_version`
  MODIFY COLUMN `graph_json` JSON NOT NULL COMMENT '节点、连线、排他条件、审批、动作引用与通知规则，结构见工作流技术方案 3.1；不超过 48 KiB。',
  MODIFY COLUMN `start_form_json` JSON NOT NULL COMMENT '启动表单字段定义，与表单动作共用结构，见工作流技术方案 3.2；最多 50 个字段，不超过 12 KiB。';
