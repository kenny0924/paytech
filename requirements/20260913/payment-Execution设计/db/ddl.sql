-- Payment Execution 阶段 4：已确认模型的空旧表增量方案，未执行。
-- 目标：后续明确指定的 Payment 服务本地库；本文件不含连接、USE 或自动执行入口。
-- 仅适用于模型已同步、写入已停止、涉及业务数据和旧补偿任务经核验均为空的环境。
-- 有历史数据或 UNKNOWN 时禁止执行，必须另做迁移。完整前置、备份和恢复限制见 ../data-design.md 第 7 节。
-- 正式 PDManer 与完整 Schema 已同步；实际目标表存在性与数据前置尚未核验，禁止据此直接执行。
-- 不可重复执行；DDL 不是整包事务，任一步失败必须停止核对，禁止直接重跑。
-- 业务/注册 DML 均为 0；不创建 CHECK、FOREIGN KEY、触发器或自动种子。

CREATE TABLE `t_payment_intent_binding` (
  `payment_intent_binding_id` BIGINT NOT NULL COMMENT '来源订单与支付意图创建资格绑定的本库 Snowflake 唯一标识。',
  `source_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '支付来源的受控命名空间；本期只允许 COMMERCE_ORDER，不接收任意调用方自定义来源。取值来源：Payment Source Type Catalog；非固定枚举。',
  `source_id` BIGINT NOT NULL COMMENT '来源命名空间中的稳定业务标识；COMMERCE_ORDER 时为 Commerce Order ID。',
  `client_id` BIGINT NOT NULL COMMENT '来源订单所属 Client 标识；创建、关闭和查询必须核对同一 Client 权限。',
  `payment_intent_id` BIGINT NULL COMMENT '已创建的唯一支付意图标识；关闭先于创建时为空，非空后不得改变。',
  `closed_at` DATETIME(6) NULL COMMENT '关闭新建意图、新 Attempt 和首次 AUTHORIZE/SALE 发送资格的 UTC 微秒时间；开放时为空，关闭不可撤销，不表示既有资金已经取消。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间，由应用写入。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间，由应用写入。',
  PRIMARY KEY (`payment_intent_binding_id`),
  UNIQUE KEY `uk_pay_binding_source` (`source_type`, `source_id`) COMMENT '唯一来源订单创建资格；包括尚未创建 Intent 的关闭记录。',
  UNIQUE KEY `uk_pay_binding_intent` (`payment_intent_id`) COMMENT '同一非空 Intent 仅有一个来源绑定。'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Execution 来源绑定与单向关闭事实；关闭不代表资金终止，不承载工作流状态。';

ALTER TABLE `t_payment_attempt`
  ADD COLUMN `source_submission_id` BIGINT NULL DEFAULT NULL COMMENT 'Commerce 原支付提交命令标识；新执行必填且全局唯一绑定一个 Attempt，历史无可靠身份时为空，不得猜测回填。',
  ADD COLUMN `payment_goal` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '本次支付尝试冻结的业务目标；新执行必填且不可修改。允许值：PAY_NOW、AUTHORIZE_ONLY。历史未确定时为空。',
  ADD COLUMN `authentication_requirement` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '内部 Risk 允许本次支付后冻结的认证要求。允许值：NOT_REQUIRED、THREE_DS_REQUIRED、CHALLENGE_REQUIRED。尚无确定允许结果时为空。',
  ADD COLUMN `card_reference` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT 'Card Owner 提供且受 Client/Intent 作用域授权的稳定非 bearer 用卡引用；新 Card 执行必填，不包含 PAN、CVV 或临时使用材料；其他或历史不适用时为空。',
  ADD UNIQUE KEY `uk_pay_attempt_submission` (`source_submission_id`) COMMENT '同一 Commerce 支付提交身份最多绑定一个 Attempt。';

ALTER TABLE `t_payment_action`
  ADD COLUMN `target_payment_action_id` BIGINT NULL DEFAULT NULL COMMENT 'QUERY 查询的原 Payment Action 标识；QUERY 必填并与原动作同父级、同渠道，其他动作为空。',
  ADD COLUMN `authentication_action_id` BIGINT NULL DEFAULT NULL COMMENT '需要认证的 AUTHORIZE/SALE 绑定同 Attempt 的成功 AUTHENTICATE 标识；要求金额、币种、Card 和渠道坐标一致，其他动作为空。',
  ADD COLUMN `channel_operation_ref` VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '渠道返回的稳定非 bearer 操作或认证上下文引用；未取得时为空；不得保存临时 URL、SDK Token、Client Secret 或持有即可继续支付的参数。',
  ADD COLUMN `interaction_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '原 Action 的消费者交互类型，无交互时为空。允许值：REDIRECT、THREE_DS_CHALLENGE、SDK、APP_SWITCH、QR_CODE。本期只写 THREE_DS_CHALLENGE。',
  ADD COLUMN `interaction_expires_at` DATETIME(6) NULL DEFAULT NULL COMMENT '原消费者交互可继续等待的 UTC 微秒截止时间；REQUIRES_ACTION 时必填，完成后保留历史值，不等于认证证明有效期。',
  ADD COLUMN `authentication_outcome` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT 'AUTHENTICATE 获得的可信标准化认证结论。允许值：AUTHENTICATED、ATTEMPTED、FAILED、UNAVAILABLE。无确定结论或非认证动作时为空。',
  ADD COLUMN `challenge_performed` TINYINT NULL DEFAULT NULL COMMENT '可信证据确认是否实际执行挑战：1 是、0 否；未知或非认证动作时为空，不能由浏览器回跳推断。',
  ADD COLUMN `authentication_valid_until` DATETIME(6) NULL DEFAULT NULL COMMENT '成功 AUTHENTICATE 证明可供原资金动作使用的 UTC 微秒截止时间；成功认证必填，其他情形为空。',
  ADD COLUMN `status_reason_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '原 Action 失败、未知或受控等待的稳定安全原因代码；无原因时为空；取值来源为 Execution Reason Catalog，不保存远程原始报文或敏感值。取值来源：Reason Catalog；非固定枚举。',
  MODIFY COLUMN `action_type` VARCHAR(64) NOT NULL COMMENT '原支付语义操作类型；认证无资金效应，查询只收敛原动作。允许值：AUTHENTICATE、AUTHORIZE、CAPTURE、SALE、VOID、REFUND、QUERY。无 REVERSAL 动作。',
  MODIFY COLUMN `target_channel_txn_ref_id` BIGINT NULL COMMENT '原成功资金交易引用；CAPTURE/VOID 指 AUTHORIZE，REFUND 指 SALE/CAPTURE，QUERY 在已有成功引用时可附带但仍以 target_payment_action_id 定位；初始支付及认证时为空。',
  MODIFY COLUMN `amount` DECIMAL(24,8) NOT NULL COMMENT '当前动作绑定的确定正金额；AUTHENTICATE/QUERY 保存被认证或查询操作的金额用于一致性校验，不因此形成资金事实；本期精度上限为 24 位且小数最多 8 位。',
  MODIFY COLUMN `channel_idempotency_key` VARCHAR(128) NOT NULL COMMENT '一次语义 Action 在原渠道上的稳定平台幂等键，重试不得改变；字段存在不代表渠道保证幂等，未知结果重发必须另经渠道幂等能力与期限校验。',
  ADD KEY `idx_pay_action_target_action` (`target_payment_action_id`) COMMENT '按被查询的原 Action 定位查询记录。',
  ADD KEY `idx_pay_action_authentication` (`authentication_action_id`) COMMENT '按原认证定位其绑定的资金动作。',
  ADD KEY `idx_pay_action_operation` (`channel_account_id`, `channel_operation_ref`) COMMENT '按渠道账户及稳定操作引用定位原 Action；不假设引用全局唯一。';

-- Web 本地补偿技术表：旧合同对齐 Foundation 当前最小模型。
-- sub_event_type 不给伪造默认值；旧表存在任何任务时本节不得执行。
ALTER TABLE `t_compensation_event`
  DROP INDEX `uk_compensation_event_key`,
  DROP INDEX `uk_compensation_source`,
  DROP INDEX `idx_compensation_claim`,
  DROP COLUMN `execution_type`,
  DROP COLUMN `source_type`,
  DROP COLUMN `source_id`,
  DROP COLUMN `source_version`,
  DROP COLUMN `schema_version`,
  DROP COLUMN `payload_hash`,
  ADD COLUMN `sub_event_type` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '同一 Handler 下的受控业务子类型，最长 64 个 Java 字符；与 event_key、event_type 共同组成稳定唯一身份。取值来源：Payment Compensation Sub Event Catalog；非固定枚举。',
  MODIFY COLUMN `event_key` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'Handler 读取本服务权威事实使用的稳定业务键，最长 128 个 Java 字符；去首尾空白、拒绝控制字符，大小写敏感。',
  MODIFY COLUMN `event_type` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '全应用唯一注册的 Handler 类型，最长 64 个 Java 字符；去首尾空白、拒绝控制字符，大小写敏感。取值来源：Payment Event/Handler Catalog；非固定枚举。',
  MODIFY COLUMN `payload_json` VARCHAR(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL COMMENT '可选最小 JSON Object，通常为空；最多 4 KiB UTF-8 字节由框架校验，不得保存可从本库恢复的上下文、卡数据、Vault Token、凭据或临时交互指令。',
  MODIFY COLUMN `execute_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'INIT' COMMENT '本地补偿任务执行状态。允许值：INIT、PROCESSING、ERROR、FAILED。成功后删除记录，FAILED 不改变业务资金状态。',
  MODIFY COLUMN `execute_count` INT NOT NULL DEFAULT 0 COMMENT '已开始的 Handler 尝试次数，非负，Claim 时增加；达到上限后不得继续产生副作用。',
  MODIFY COLUMN `next_execute_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '任务下次允许 Claim 的 UTC 微秒时间，由 Handler 退避策略更新。',
  MODIFY COLUMN `last_error_summary` VARCHAR(1000) NULL COMMENT '最近一次执行失败的脱敏摘要，最长 1000 字符；无错误时为空，不包含卡数据、凭据或其他敏感原值。',
  MODIFY COLUMN `lock_version` BIGINT NOT NULL DEFAULT 1 COMMENT 'Claim/Lease 使用的正乐观锁版本；登记初始值固定为 1，仅当前 Lease 持有者可回写执行结果。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '本地补偿记录创建的 UTC 微秒时间。',
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '本地补偿记录最近更新的 UTC 微秒时间，由写入口维护。',
  ADD UNIQUE KEY `uk_comp_event_identity` (`event_key`, `event_type`, `sub_event_type`) COMMENT '同一稳定业务键、Handler 和业务子类型唯一登记。',
  ADD KEY `idx_comp_event_ready` (`execute_status`, `next_execute_at`, `id`) COMMENT '按状态与到期时间稳定扫描待执行任务。',
  ADD KEY `idx_comp_event_lease` (`execute_status`, `lock_until`, `id`) COMMENT '按状态与 Lease 到期时间扫描可接管任务。';

-- 破坏性设计语句：只有空表且新 Action 承接方案已核验时才允许后续人工执行。
DROP TABLE `t_payment_customer_action`;
