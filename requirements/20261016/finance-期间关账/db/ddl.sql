-- 需求：Finance 期间关账（D4）
-- 性质：本地增量执行脚本。设计批准不代表已执行；执行需用户单独授权。
-- 目标库：本地 mor-local-mysql-1 / 127.0.0.1:13306 / mor_finance（不涉及 mor-test 或生产）。
-- 前置：t_period_close 为 0 行（关账功能尚未实现，执行前确认）。
-- 同步：PDManer finance.pdma.json 与 finance-schema.sql 中 period_close.close_hash 同步改为可空（阶段 4 修改文件）。
-- 回滚：t_period_close 仍为 0 行时，执行 MODIFY close_hash CHAR(64) NOT NULL 恢复原定义。
ALTER TABLE `t_period_close`
  MODIFY `close_hash` CHAR(64) NULL COMMENT '流程进入 PENDING_CHECK 时冻结的流程头、全部账簿范围、步骤、试算、异常解决和结转指令的 Canonical SHA-256 哈希；CREATED/RUNNING/FAILED 时为空。';
