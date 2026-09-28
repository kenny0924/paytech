-- 待人工执行：仅适用于仍为旧 11 列且确认 0 行的 t_command_idempotency。
-- 前置、基线和非空处理见 command-idempotency-migration.md。
-- 与已含本次 ALTER 的 execution-simplified-local-empty-migration.sql 二选一，不重复执行。
-- 停写并备份后核对精确 COUNT 与 SHOW CREATE TABLE；非空或基线不同立即停止。
-- DDL 不具有整包原子性；禁止 --force，不创建/删除业务表，不执行回填 DML。
SET NAMES utf8mb4;

ALTER TABLE `t_command_idempotency`
  DROP INDEX `uk_command_idempotency`,
  MODIFY COLUMN `command_idempotency_id` BIGINT NOT NULL COMMENT '一次 Payment 同步写命令幂等执行记录的内部唯一标识。' FIRST,
  MODIFY COLUMN `caller_scope` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '隔离同步命令幂等键命名空间的稳定调用方作用域。取值来源：Payment Caller Scope Catalog；非固定枚举。' AFTER `command_idempotency_id`,
  MODIFY COLUMN `operation_type` VARCHAR(64) NOT NULL COMMENT 'Payment 同步写命令采用的受控业务操作代码。取值来源：Payment Command Operation Catalog；非固定枚举。' AFTER `caller_scope`,
  MODIFY COLUMN `idempotency_key` VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '调用方在命令作用域内提供的大小写敏感幂等键；pay 固定为 submission 加冒号及原提交标识。' AFTER `operation_type`,
  MODIFY COLUMN `request_hash` CHAR(64) NOT NULL COMMENT '幂等命令首次规范化请求的完整性哈希，用于拒绝同键不同请求。' AFTER `idempotency_key`,
  ADD COLUMN `request_hash_version` INT NOT NULL COMMENT 'Payment 拥有的请求身份算法版本；PAY_INSTRUMENT_V1 使用版本 1，未知历史不能默认回填。' AFTER `request_hash`,
  ADD COLUMN `request_snapshot_schema_version` INT NULL COMMENT '可选恢复请求的结构版本；当前 pay 不保存请求快照时为空。' AFTER `request_hash_version`,
  ADD COLUMN `request_snapshot_json` JSON NULL COMMENT '可选最小恢复输入；当前 pay 为空，不得保存工具 Token、卡数据或临时交互凭据。' AFTER `request_snapshot_schema_version`,
  ADD COLUMN `operation_context_time` DATETIME(6) NOT NULL COMMENT '首次 Claim 冻结的 UTC 微秒业务时刻，不随重放更新。' AFTER `request_snapshot_json`,
  ADD COLUMN `recovery_stage` VARCHAR(64) NULL COMMENT 'Payment 定义的命令恢复阶段；pay 在同一本地事务内由 CLAIMED 到 COMPLETED，不承载支付流程步骤。取值来源：Payment Command Recovery Stage Catalog；非固定枚举。' AFTER `operation_context_time`,
  CHANGE COLUMN `command_status` `process_status` VARCHAR(64) NOT NULL COMMENT 'Foundation 命令的处理中、成功或失败技术状态；pay 成功仅表示受理完成，不代表资金成功。允许值：PROCESSING、SUCCEEDED、FAILED。' AFTER `recovery_stage`,
  MODIFY COLUMN `resource_type` VARCHAR(64) NULL COMMENT '幂等命令结果所指 Payment 业务资源的目录类型；无资源时为空。取值来源：Payment Command Resource Type Catalog；非固定枚举。' AFTER `process_status`,
  MODIFY COLUMN `resource_id` BIGINT NULL COMMENT '幂等命令创建或作用到的 Payment 本库业务资源标识；无资源时为空。' AFTER `resource_type`,
  ADD COLUMN `resource_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '可选稳定业务编号；pay 已使用 Attempt 内部标识时为空。' AFTER `resource_id`,
  MODIFY COLUMN `response_code` VARCHAR(64) NULL COMMENT 'Payment 命令的稳定回执代码；pay 使用 PAY_ACCEPTED 表示受理完成，不表示资金成功。取值来源：Payment Command Response Code Catalog；非固定枚举。' AFTER `resource_no`,
  ADD COLUMN `response_http_status` INT NULL COMMENT '命令完成的稳定 HTTP 回执元数据；pay 固定 202，不替代按当前支付状态生成的 HTTP 200 或 202。' AFTER `response_code`,
  ADD COLUMN `response_snapshot_schema_version` INT NULL COMMENT '可选稳定响应正文的结构版本；pay 不冻结响应正文时为空。' AFTER `response_http_status`,
  ADD COLUMN `response_location` VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '可选稳定资源位置；pay 无 Location 合同时为空，不保存临时交互链接。' AFTER `response_snapshot_schema_version`,
  ADD COLUMN `response_body_json` JSON NULL COMMENT '可选稳定响应正文；pay 为空，当前支付状态与 observedAt 在重放时重新投影。' AFTER `response_location`,
  ADD COLUMN `lock_owner` VARCHAR(64) NULL COMMENT '本次命令 Claim 的唯一持有者；pay 使用请求级随机 UUID，完成后由公共组件清空。' AFTER `response_body_json`,
  ADD COLUMN `lock_until` DATETIME(6) NULL COMMENT '命令 Claim Lease 的 UTC 微秒截止时间；pay 仅覆盖本地短事务，完成 CAS 必须仍在租期内，完成后清空。' AFTER `lock_owner`,
  ADD COLUMN `version` BIGINT NOT NULL COMMENT 'Foundation 命令 Claim、Lease 和完成 CAS 使用的技术版本；首次为 1，独立于 Payment 业务根版本。' AFTER `lock_until`,
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。' AFTER `version`,
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。' AFTER `create_time`,
  ADD UNIQUE KEY `uk_command_idempotency` (`caller_scope`, `operation_type`, `idempotency_key`) COMMENT '唯一索引：caller_scope、operation_type、idempotency_key',
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,
  COMMENT = 'Foundation 公共组件持久化的 Payment 本地命令 Claim、Lease、CAS 与结果定位；业务幂等语义由 Payment 拥有。';
