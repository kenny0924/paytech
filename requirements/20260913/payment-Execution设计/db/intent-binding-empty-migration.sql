-- 待人工执行：删除已经完成其他 Execution 精简的环境中的空 Binding 表。
-- 仅在确认目标库、停写、备份并检查 COUNT(*) = 0 后使用。
-- 任何非空记录（尤其 closed_at 非空或 payment_intent_id 为空）均需单独处置，禁止直接删除。
-- 未建表的环境跳过本文件；不得与 execution-simplified-local-empty-migration.sql 叠加执行。
-- 本文件没有 IF EXISTS，不允许 mysql --force。失败后保持停写，从原表备份恢复，不靠回退应用恢复表。
DROP TABLE `t_payment_intent_binding`;
