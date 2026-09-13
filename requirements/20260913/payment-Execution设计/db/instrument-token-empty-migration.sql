-- 已确认交易级 Token 合同的空旧表迁移工件；尚未执行，不得由应用或测试自动执行。
-- 合同来源：../instrument-token-amendment.md 第 4、7 节。
-- 仅适用于人工确认所有相关 Execution 业务、命令和补偿表均为空，
-- 且 t_payment_instrument_token 不存在的 Payment 服务本地库。
-- 必须先同步并逐项核对正式 PDManer、生成器及完整 Schema，停止写入，
-- 完成备份和恢复验证，再取得指定数据库的本次执行授权。
-- 存在旧 Attempt、Instrument、幂等或未决任务时禁止运行；不得猜值回填。
-- MySQL DDL 非整包原子；失败立即停止，核对已执行语句，禁止盲目重跑。
-- 不含连接信息、USE、IF EXISTS、DML、物理 FK/CHECK 或 Card 候选表迁移。

CREATE TABLE `t_payment_instrument_token` (
  `payment_instrument_token_id` BIGINT NOT NULL COMMENT '本次交易级支付工具绑定的 Snowflake 内部主键，供 Attempt 和清理任务定位。',
  `payment_instrument_token` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '安全随机生成的不透明内部支付工具定位符，原准备命令重放同值；仅持有此值不能获得用卡或扣款权限。',
  `client_id` BIGINT NOT NULL COMMENT '从可信 Payment Intent 核对的所属 CCT Client 稳定标识，创建后不可修改。',
  `payment_intent_id` BIGINT NOT NULL COMMENT '本次工具固定关联的 Payment Intent 内部主键，创建后不可修改。',
  `source_submission_id` BIGINT NOT NULL COMMENT 'Commerce 已提交的原支付命令全局稳定标识；当前仅接受 Commerce 命令命名空间，不是 Payment Attempt 标识。',
  `payment_method_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本次工具绑定的精确支付方式，创建后不可修改。取值来源：Payment Method Catalog；非固定枚举。',
  `instrument_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工具 Owner 的受控类型，本期只实现卡工具。允许值：CARD。',
  `instrument_ref` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工具 Owner 返回的稳定内部记录引用，创建后不可修改；不包含原始卡数据、CVV、渠道工具 Token 或可直接使用的临时材料。',
  `expires_at` DATETIME(6) NOT NULL COMMENT '首次用卡资格的 UTC 微秒精度截止时间，创建时按原支付和材料期限冻结；重试不延长，不表示在途资金失效。',
  `revoked_at` DATETIME(6) NULL COMMENT '主动撤销新用卡资格的 UTC 微秒精度时间；NULL 表示未主动撤销，仍须检查期限和原执行事实；不能据此撤销渠道资金。',
  `create_time` DATETIME(6) NOT NULL COMMENT '应用写入的工具绑定创建 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '应用写入的工具绑定最近有效更新 UTC 微秒精度时间。',
  PRIMARY KEY (`payment_instrument_token_id`),
  UNIQUE KEY `uk_pay_token_value` (`payment_instrument_token`) COMMENT '唯一索引：payment_instrument_token',
  UNIQUE KEY `uk_pay_token_scope` (`client_id`, `payment_intent_id`, `source_submission_id`) COMMENT '唯一索引：client_id、payment_intent_id、source_submission_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Execution 拥有的交易级支付工具 Token 与不可变使用范围，不存卡数据或会话状态。';

ALTER TABLE `t_payment_attempt`
  ADD COLUMN `payment_instrument_token_id` BIGINT NULL COMMENT '本次尝试关联的交易级工具 Token 内部主键；新统一 pay 必填并校验相同 Client、Intent、来源提交和支付方式，历史未迁移行为空；不存 Token 字符串。' AFTER `authentication_requirement`,
  ADD UNIQUE KEY `uk_pay_attempt_token` (`payment_instrument_token_id`) COMMENT '唯一索引：payment_instrument_token_id',
  DROP COLUMN `card_reference`;

DROP TABLE `t_payment_instrument`;
