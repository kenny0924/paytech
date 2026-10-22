-- ONLY mor-local MySQL at 127.0.0.1:13306; payment test database mor_payment (design/10 §4.2, §5.1).
-- REVIEW AND APPLY MANUALLY with the root account to an EMPTY mor_payment; applications and tests never execute this file.
-- mor_payment / payment_app come from local-environment/mysql/init/01-create-databases-and-users.sh (password MOR_PAYMENT_DB_PASSWORD
-- in .env.local); the GRANT below is the minimum the tests need and does not revoke an existing broader local grant.
CREATE DATABASE IF NOT EXISTS mor_payment CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_payment.* TO 'payment_app'@'%';
USE mor_payment;

-- 13 tables: 10 payment tables (design/03 §2.3), the per-database t_compensation_event and, since the 2026-10-22 3DS
-- requirement (payment consumes ChannelPaymentNotified), the paytech-messaging t_consumed_event / t_consumed_aggregate_version;
-- copied verbatim from paytech/payment/docs/database/sql/payment-schema.sql. An existing local mor_payment applies
-- requirements/20261022/payment-3DS全链路/db/payment/ddl.sql instead. The four card tables moved to mor_card in step 12 (design/05);
-- an existing local mor_payment keeps its card tables untouched (no DROP) and only needs the four new
-- t_payment_instrument_token columns, see requirements/20261017/architecture-V2骨架与模块搬迁/tasks/12-card-isolation-report.md.
SET NAMES utf8mb4;

CREATE TABLE `t_routing_decision` (
  `routing_decision_id` BIGINT NOT NULL COMMENT '一次支付尝试唯一不可变路由结论的内部标识。',
  `payment_attempt_id` BIGINT NOT NULL COMMENT '同一支付意图内一次顺序支付尝试的内部唯一标识。',
  `routing_policy_id` BIGINT NULL COMMENT '按固定交易作用域匹配的稳定路由策略根标识。',
  `routing_policy_version_id` BIGINT NULL COMMENT '路由策略不可变候选集合发布版本的内部唯一标识。',
  `selected_channel_id` BIGINT NULL COMMENT '路由最终选中的支付服务商标识；拒绝或冲突时为空。',
  `selected_mid_id` BIGINT NULL COMMENT '路由最终选中的服务商商户号标识；拒绝或冲突时为空。',
  `selected_channel_account_id` BIGINT NULL COMMENT '路由最终选中并要求首个资金动作逐值一致的 Channel Account 标识；未选中时为空。',
  `decision_status` VARCHAR(64) NOT NULL COMMENT '一次支付尝试最终得到选中、拒绝或配置冲突的闭合结论。允许值：SELECTED、REJECTED、CONFIGURATION_CONFLICT。',
  `decision_reason_code` VARCHAR(64) NULL COMMENT '路由选中、拒绝或配置冲突采用的稳定原因代码；无需原因时为空。取值来源：Reason Catalog；非固定枚举。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  PRIMARY KEY (`routing_decision_id`),
  UNIQUE KEY `uk_routing_decision_attempt` (`payment_attempt_id`) COMMENT '唯一索引：payment_attempt_id',
  KEY `idx_route_dec_policy` (`routing_policy_id`) COMMENT '访问索引：routing_policy_id',
  KEY `idx_route_dec_pol_ver` (`routing_policy_version_id`) COMMENT '访问索引：routing_policy_version_id',
  KEY `idx_route_dec_channel` (`selected_channel_id`) COMMENT '访问索引：selected_channel_id',
  KEY `idx_route_dec_mid` (`selected_mid_id`) COMMENT '访问索引：selected_mid_id',
  KEY `idx_route_dec_account` (`selected_channel_account_id`) COMMENT '访问索引：selected_channel_account_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='一次支付尝试的不可变最终路由结论，冻结服务商、Channel Account 和商户号。';

CREATE TABLE `t_route_candidate_eval` (
  `route_candidate_eval_id` BIGINT NOT NULL COMMENT '一次路由决策中一条不可变候选商户号评估的内部唯一标识。',
  `routing_decision_id` BIGINT NOT NULL COMMENT '一次支付尝试唯一不可变路由结论的内部标识。',
  `channel_id` BIGINT NOT NULL COMMENT 'Payment 管理的支付服务商内部唯一标识。',
  `mid_id` BIGINT NOT NULL COMMENT 'Payment 管理且唯一归属于一个 Operating Entity 的服务商商户号标识。',
  `channel_account_id` BIGINT NULL COMMENT 'Payment 管理的服务商技术认证账户或租户内部唯一标识。',
  `eligible_flag` TINYINT(1) NOT NULL COMMENT '1 表示该商户号满足本次路由条件，0 表示被排除。',
  `candidate_priority` INT NOT NULL COMMENT '路由策略中候选商户号的优先顺序，数值越小优先级越高。',
  `weight_value` DECIMAL(18,10) NOT NULL COMMENT '同一候选优先级内执行稳定加权选择的正数权重。',
  `exclusion_reason_code` VARCHAR(64) NULL COMMENT '该商户号未进入最终可选集合的主要稳定原因代码；可用时为空。取值来源：Reason Catalog；非固定枚举。',
  `health_status` VARCHAR(64) NULL COMMENT '路由评估时读取或冻结的商户号健康结论。允许值：HEALTHY、DEGRADED、UNHEALTHY、UNKNOWN。',
  `circuit_status` VARCHAR(64) NULL COMMENT '商户号支付方式当前处于关闭、打开或半开探测的熔断状态。允许值：CLOSED、OPEN、HALF_OPEN。',
  `health_score` DECIMAL(5,2) NULL COMMENT '当前窗口技术健康度的零到一百分评分。',
  `technical_success_rate` DECIMAL(9,8) NULL COMMENT '当前健康窗口技术成功样本占全部样本的比例。',
  `timeout_rate` DECIMAL(9,8) NULL COMMENT '当前健康窗口超时样本占全部样本的比例。',
  `p95_latency_ms` INT NULL COMMENT '当前健康窗口服务商交互延迟的第九十五百分位毫秒数。',
  `sample_count` INT NULL COMMENT '当前健康窗口纳入成功、服务商错误和超时聚合的总样本数。',
  `health_observed_at` DATETIME(6) NULL COMMENT '路由候选评估冻结所读取健康状态的观察时间；未形成快照时为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  PRIMARY KEY (`route_candidate_eval_id`),
  UNIQUE KEY `uk_route_candidate_eval` (`routing_decision_id`, `mid_id`) COMMENT '唯一索引：routing_decision_id、mid_id',
  KEY `idx_route_eval_channel` (`channel_id`) COMMENT '访问索引：channel_id',
  KEY `idx_route_eval_mid` (`mid_id`) COMMENT '访问索引：mid_id',
  KEY `idx_route_eval_account` (`channel_account_id`) COMMENT '访问索引：channel_account_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='一次路由决策中全部候选的不可变资格评估证据。';

CREATE TABLE `t_payment_intent` (
  `payment_intent_id` BIGINT NOT NULL COMMENT '一个来源订单唯一支付目标的内部稳定标识。',
  `source_type` VARCHAR(64) NOT NULL COMMENT '支付意图来源或补偿事件来源业务事实采用的受控目录类型。取值来源：Payment Source Type Catalog；非固定枚举。',
  `source_id` BIGINT NOT NULL COMMENT '支付意图或补偿事件所引用来源事实的稳定业务标识。',
  `client_id` BIGINT NOT NULL COMMENT '来源订单所属平台 Client 的稳定业务标识。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'CCT唯一不可变OE编号，创建时经营国家二位代码加001–999，如AE001；不是数字代理ID，编号不参与路由。',
  `client_pricing_version_id` BIGINT NOT NULL COMMENT '支付准备时由CCT固定的Client条款版本，后续准确Price只能从此版本解析。',
  `partner_share_version_id` BIGINT NULL COMMENT 'PARTNER来源时固定的CCT分成版本，与平台服务费版本成组非空。',
  `platform_charge_version_id` BIGINT NULL COMMENT 'PARTNER来源时固定的CCT服务费版本，与分成版本属于同一Partner合同。',
  `industry_code` VARCHAR(64) NOT NULL COMMENT 'Commerce 从 Client & Contract 冻结并传入的平台行业目录代码。取值来源：CCT/Commerce frozen classification；非固定枚举。',
  `mcc_code` VARCHAR(4) NOT NULL COMMENT '精确值为 Commerce 从 Client & Contract 冻结的四位 Merchant Category Code；配置 Scope 允许控制通配符 *。取值来源：CCT/Commerce frozen classification；非固定枚举。',
  `consumer_country_code` CHAR(2) NOT NULL COMMENT 'Commerce 创建 Intent 时传入并冻结的消费者国家，用于支付定价；后续不得覆盖。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `amount` DECIMAL(24,8) NOT NULL COMMENT '当前支付目标或渠道资金动作采用的确定金额。',
  `currency` CHAR(3) NOT NULL COMMENT '金额事实采用 ISO 4217 三位币种；配置 Scope 允许控制通配符 *，交易事实必须使用精确币种。取值来源：ISO 4217；非固定枚举。',
  `intent_status` VARCHAR(64) NOT NULL COMMENT '一个来源订单支付目标当前的闭合业务生命周期状态。允许值：OPEN、SUCCEEDED、CANCELLED、EXPIRED。',
  `active_attempt_id` BIGINT NULL COMMENT '当前仍占用支付意图且可能继续推进或等待权威结果的支付尝试标识。',
  `successful_attempt_id` BIGINT NULL COMMENT '支付意图首次形成权威成功结果的支付尝试标识。',
  `expires_at` DATETIME(6) NOT NULL COMMENT '支付意图或消费者后续动作允许继续处理的最晚业务时刻；消费者动作未提供期限时为空。',
  `version` BIGINT NOT NULL COMMENT '可变 Payment 业务根并发命令与乱序结果条件更新使用的单调版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`payment_intent_id`),
  UNIQUE KEY `uk_payment_intent_source` (`source_type`, `source_id`) COMMENT '唯一索引：source_type、source_id',
  KEY `idx_payment_intent_expiry` (`intent_status`, `expires_at`, `payment_intent_id`) COMMENT '访问索引：intent_status、expires_at、payment_intent_id',
  UNIQUE KEY `uk_payment_intent_active` (`active_attempt_id`) COMMENT '唯一索引：active_attempt_id',
  UNIQUE KEY `uk_payment_intent_success` (`successful_attempt_id`) COMMENT '唯一索引：successful_attempt_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='从已提交Commerce Context取得固定entityNo、金额及Term Version，所有Attempt只能在同一OE下执行；新写去Assignment。Risk未接入新身份时不得伪造资格或跨OE降级。';

CREATE TABLE `t_payment_attempt` (
  `payment_attempt_id` BIGINT NOT NULL COMMENT '同一支付意图内一次顺序支付尝试的内部唯一标识。',
  `payment_intent_id` BIGINT NOT NULL COMMENT '一个来源订单唯一支付目标的内部稳定标识。',
  `attempt_no` INT NOT NULL COMMENT '同一支付意图内从 1 开始单调递增的尝试序号。',
  `source_submission_id` BIGINT NULL DEFAULT NULL COMMENT 'Commerce 原支付提交命令标识；新执行必填且全局唯一绑定一个 Attempt，历史无可靠身份时为空，不得猜测回填。',
  `payment_method_code` VARCHAR(64) NOT NULL COMMENT '支付尝试冻结本次使用的精确支付方式目录值；商户号 Capability 与 Health State 使用精确值，Routing Policy 可使用精确值或控制通配符 *。取值来源：Payment Method Catalog；非固定枚举。',
  `capture_mode` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '本次支付尝试冻结的捕获模式；新执行必填且不可修改。允许值：AUTOMATIC、MANUAL。历史未核对时为空。',
  `payment_instrument_token_id` BIGINT NULL COMMENT '本次尝试关联的交易级工具 Token 内部主键；新统一 pay 必填并校验相同 Client、Intent、来源提交和支付方式，历史未迁移行为空；不存 Token 字符串。',
  `channel_id` BIGINT NULL COMMENT 'Routing 成功后冻结的支付渠道标识；路由前为空，三项渠道坐标同事务绑定后不可修改，后续动作和退款沿原 Attempt 使用。',
  `channel_account_id` BIGINT NULL COMMENT 'Routing 成功后冻结的渠道技术账户标识；路由前为空，三项渠道坐标同事务绑定后不可修改，后续动作和退款沿原 Attempt 使用。',
  `mid_id` BIGINT NULL COMMENT 'Routing 成功后冻结的渠道商户号标识；路由前为空，三项渠道坐标同事务绑定后不可修改，后续动作和退款沿原 Attempt 使用。',
  `attempt_status` VARCHAR(64) NOT NULL COMMENT '一次支付尝试从创建、处理到权威终态的当前状态。允许值：PROCESSING、SUCCEEDED、FAILED、CANCELLED。',
  `active_action_id` BIGINT NULL COMMENT '原尝试当前唯一未终结业务动作；PROCESSING、UNKNOWN 占位，终态须核对仍指向原动作才清空；等待手动 Capture 时为空，但 Intent 继续占用该 Attempt。',
  `authorized_amount` DECIMAL(24,8) NOT NULL COMMENT '该支付尝试已获得服务商权威授权的累计金额。',
  `captured_amount` DECIMAL(24,8) NOT NULL COMMENT '该支付尝试已经确认成功捕获的累计金额。',
  `version` BIGINT NOT NULL COMMENT '可变 Payment 业务根并发命令与乱序结果条件更新使用的单调版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`payment_attempt_id`),
  UNIQUE KEY `uk_payment_attempt_no` (`payment_intent_id`, `attempt_no`) COMMENT '唯一索引：payment_intent_id、attempt_no',
  UNIQUE KEY `uk_pay_attempt_submission` (`source_submission_id`) COMMENT '唯一索引：source_submission_id',
  UNIQUE KEY `uk_pay_attempt_token` (`payment_instrument_token_id`) COMMENT '唯一索引：payment_instrument_token_id',
  KEY `idx_pay_attempt_channel` (`channel_id`) COMMENT '访问索引：channel_id',
  KEY `idx_pay_attempt_account` (`channel_account_id`) COMMENT '访问索引：channel_account_id',
  KEY `idx_pay_attempt_mid` (`mid_id`) COMMENT '访问索引：mid_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='同一支付意图内的一次支付尝试，冻结方式、捕获模式、工具及选定渠道，持有唯一活动动作。';

CREATE TABLE `t_payment_instrument_token` (
  `payment_instrument_token_id` BIGINT NOT NULL COMMENT '本次交易级支付工具绑定的 Snowflake 内部主键，供 Attempt 和清理任务定位。',
  `payment_instrument_token` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '安全随机生成的不透明内部支付工具定位符，原准备命令重放同值；仅持有此值不能获得用卡或扣款权限。',
  `client_id` BIGINT NOT NULL COMMENT '从可信 Payment Intent 核对的所属 CCT Client 稳定标识，创建后不可修改。',
  `payment_intent_id` BIGINT NOT NULL COMMENT '本次工具固定关联的 Payment Intent 内部主键，创建后不可修改。',
  `source_submission_id` BIGINT NOT NULL COMMENT 'Commerce 已提交的原支付命令全局稳定标识；当前仅接受 Commerce 命令命名空间，不是 Payment Attempt 标识。',
  `payment_method_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本次工具绑定的精确支付方式，创建后不可修改。取值来源：Payment Method Catalog；非固定枚举。',
  `instrument_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工具 Owner 的受控类型，本期只实现卡工具。允许值：CARD。',
  `instrument_ref` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工具 Owner 返回的稳定内部记录引用，创建后不可修改；不包含原始卡数据、CVV、渠道工具 Token 或可直接使用的临时材料。',
  `customer_id` BIGINT NULL COMMENT '本次支付关联的 Commerce Customer 标识，随申请请求传入；访客支付为 NULL。随 PaymentSucceeded v2 交给 Card 生成已存卡。',
  `consent_to_save` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '消费者是否同意本次支付成功后保存卡；1 表示同意，仅手输卡且有 customer_id 时可为 1。随 PaymentSucceeded v2 交给 Card。',
  `card_bin` VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Card 创建卡工具时返回的卡 BIN（6 或 8 位），只用作支付风控输入；非卡工具为 NULL。',
  `card_fingerprint` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Card 创建卡工具时返回的跨交易稳定卡指纹（受限值，不能反推卡号），只经内部同步请求传给 Risk，不进日志或 MQ；非卡工具为 NULL。',
  `card_brand` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Card 创建卡工具时按 BIN 库冻结的卡组织；未识别或非卡工具为 NULL。用于 Risk 输入、路由 schemeCode 与 PaymentSucceeded。允许值：VISA、MASTERCARD、AMEX、UNIONPAY、MADA、DISCOVER、JCB、DINERS。',
  `funding_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '冻结的借贷记类型；未识别、数据未提供或非卡工具为 NULL。允许值：CREDIT、DEBIT、PREPAID、CHARGE。',
  `card_level` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '冻结的卡等级；未识别、数据未提供或非卡工具为 NULL。取值来源：Card BIN 库；非固定枚举。',
  `card_product` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '冻结的卡产品类别；未识别、数据未提供或非卡工具为 NULL。允许值：CONSUMER、COMMERCIAL。',
  `issuer_country` CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '冻结的发卡国 ISO 3166-1 alpha-2；未识别或非卡工具为 NULL。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `issuer_region` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '冻结的发卡区域；未识别或非卡工具为 NULL。取值来源：Platform 国家区域映射 region_code；非固定枚举。',
  `bin_library_version_no` INT NULL COMMENT '查询时的 BIN 库生效版本号；没有生效版本或非卡工具为 NULL。识别与否都记录，用于追溯。',
  `bin_match_length` TINYINT NULL COMMENT '命中号段的前缀位数（6、7、8）；未识别或非卡工具为 NULL，NULL 与 bin_library_version_no 非空同时出现表示查询过但未识别。',
  `expires_at` DATETIME(6) NOT NULL COMMENT '首次用卡资格的 UTC 微秒精度截止时间，创建时按原支付和材料期限冻结；重试不延长，不表示在途资金失效。',
  `revoked_at` DATETIME(6) NULL COMMENT '主动撤销新用卡资格的 UTC 微秒精度时间；NULL 表示未主动撤销，仍须检查期限和原执行事实；不能据此撤销渠道资金。',
  `create_time` DATETIME(6) NOT NULL COMMENT '应用写入的工具绑定创建 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '应用写入的工具绑定最近有效更新 UTC 微秒精度时间。',
  PRIMARY KEY (`payment_instrument_token_id`),
  UNIQUE KEY `uk_pay_token_value` (`payment_instrument_token`) COMMENT '唯一索引：payment_instrument_token',
  UNIQUE KEY `uk_pay_token_scope` (`client_id`, `payment_intent_id`, `source_submission_id`) COMMENT '唯一索引：client_id、payment_intent_id、source_submission_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Execution 拥有的交易级支付工具 Token 与不可变使用范围，不存卡数据或会话状态。';

CREATE TABLE `t_payment_action` (
  `payment_action_id` BIGINT NOT NULL COMMENT '一次内部风控或渠道业务动作的内部唯一标识。',
  `payment_attempt_id` BIGINT NULL COMMENT '同一支付意图内一次顺序支付尝试的内部唯一标识。',
  `channel_refund_id` BIGINT NULL COMMENT 'Payment 为一个 Commerce 退款请求创建的唯一渠道退款标识。',
  `duplicate_payment_case_id` BIGINT NULL COMMENT '第二笔真实渠道成功处置根的内部唯一标识。',
  `action_no` INT NOT NULL COMMENT '同一支付尝试、渠道退款或重复支付处置内单调递增的动作序号。',
  `action_type` VARCHAR(64) NOT NULL COMMENT '原支付业务步骤类型；风控和认证不形成资金事实。允许值：PAY_RISK、AUTHENTICATE、AUTHORIZE、CAPTURE、PAY、VOID、REFUND。无 QUERY 或 REVERSAL 业务动作。',
  `action_status` VARCHAR(64) NOT NULL COMMENT '支付业务动作的当前状态；创建即进入 PROCESSING，表示已进入处理流程，不代表已提交渠道。允许值：PROCESSING、UNKNOWN、SUCCEEDED、FAILED。',
  `target_channel_txn_ref_id` BIGINT NULL COMMENT '原成功资金交易引用；CAPTURE/VOID 指 AUTHORIZE，REFUND 指 PAY/CAPTURE；风控、认证与首次资金动作为空。',
  `amount` DECIMAL(24,8) NOT NULL COMMENT '原动作冻结的正金额；PAY_RISK 与 AUTHENTICATE 仅作为输入校验，不产生资金事实；精度上限为 24 位且小数最多 8 位。',
  `currency` CHAR(3) NOT NULL COMMENT '金额事实采用 ISO 4217 三位币种；配置 Scope 允许控制通配符 *，交易事实必须使用精确币种。取值来源：ISO 4217；非固定枚举。',
  `channel_idempotency_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Payment 为原渠道业务动作分配的全局唯一幂等键；PAY_RISK 为空，渠道动作必填；恢复不换键，不依赖可变渠道配置拼接新身份。',
  `transaction_assessment_id` BIGINT NULL COMMENT 'Risk 统一评估返回的评估 ID（evaluationId）；仅 PAY_RISK 取得结论后填写，已知后不可替换；渠道动作为空。',
  `risk_result` VARCHAR(64) NULL COMMENT '原 PAY_RISK 的可信评估结论；渠道动作及未知评估为空。允许值：ALLOW、REVIEW、DENY、FAILED、TIMED_OUT。评估完成不等于允许支付。',
  `authentication_requirement` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '仅原 PAY_RISK 获得可信 ALLOW 后冻结的认证要求；其他动作及未放行结果为空。允许值：NOT_REQUIRED、THREE_DS_REQUIRED、CHALLENGE_REQUIRED。',
  `interaction_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '原 Action 的消费者交互类型，无交互时为空。允许值：REDIRECT、THREE_DS_CHALLENGE、SDK、APP_SWITCH、QR_CODE。本期按渠道返回写 THREE_DS_CHALLENGE 或 REDIRECT；完成后保留历史值，可据此判断是否发生过挑战。',
  `interaction_expires_at` DATETIME(6) NULL DEFAULT NULL COMMENT '原消费者交互的 UTC 微秒截止时间；PROCESSING 且需要交互时必填，完成后保留历史值，不等于认证证明有效期。',
  `channel_response_code` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '渠道适配器返回的安全响应代码；仅渠道动作可填，风控结果使用 risk_result，本地异常或未取得响应时为空。取值来源：Channel Adapter Catalog；非固定枚举。',
  `channel_response_msg` VARCHAR(512) NULL DEFAULT NULL COMMENT '渠道适配器清洗后的安全响应说明，最长512字符；不得保存原始报文、卡数据、个人信息、凭据或临时交互参数；不是状态机判断依据。',
  `channel_result_time` DATETIME(6) NULL COMMENT '渠道动作获得当前服务商结果的业务时刻；尚无结果时为空。',
  `version` BIGINT NOT NULL COMMENT '可变 Payment 业务根并发命令与乱序结果条件更新使用的单调版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`payment_action_id`),
  UNIQUE KEY `uk_payment_action_attempt_no` (`payment_attempt_id`, `action_no`) COMMENT '唯一索引：payment_attempt_id、action_no',
  UNIQUE KEY `uk_payment_action_refund_no` (`channel_refund_id`, `action_no`) COMMENT '唯一索引：channel_refund_id、action_no',
  UNIQUE KEY `uk_pay_action_dup_case_no` (`duplicate_payment_case_id`, `action_no`) COMMENT '唯一索引：duplicate_payment_case_id、action_no',
  UNIQUE KEY `uk_payment_action_idempotency` (`channel_idempotency_key`) COMMENT '唯一索引：channel_idempotency_key',
  KEY `idx_pay_action_target_ref` (`target_channel_txn_ref_id`) COMMENT '访问索引：target_channel_txn_ref_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='一次风控或渠道业务步骤；复用原动作恢复，通过所属 Attempt 取得冻结渠道。';

CREATE TABLE `t_channel_transaction_ref` (
  `channel_transaction_ref_id` BIGINT NOT NULL COMMENT '一条不可变服务商外部资金交易引用的内部唯一标识。',
  `payment_action_id` BIGINT NOT NULL COMMENT '一次内部风控或渠道业务动作的内部唯一标识。',
  `channel_id` BIGINT NOT NULL COMMENT 'Payment 管理的支付服务商内部唯一标识。',
  `mid_id` BIGINT NOT NULL COMMENT 'Payment 管理且唯一归属于一个 Operating Entity 的服务商商户号标识。',
  `reference_type` VARCHAR(64) NOT NULL COMMENT '服务商交易引用对应授权、捕获、扣款、撤销或退款的闭合类型。允许值：AUTHORIZE、CAPTURE、PAY、VOID、REFUND。',
  `external_transaction_id` VARCHAR(255) NOT NULL COMMENT '服务商命名空间内标识一笔授权、捕获、扣款、撤销或退款的交易编号。',
  `original_channel_txn_ref_id` BIGINT NULL COMMENT '退款或争议所指向的原始成功服务商交易引用标识。',
  `channel_event_time` DATETIME(6) NULL COMMENT '服务商声明渠道交易、回调项或争议事件发生的业务时刻；未提供时为空。',
  `amount` DECIMAL(24,8) NOT NULL COMMENT '当前支付目标或渠道资金动作采用的确定金额。',
  `currency` CHAR(3) NOT NULL COMMENT '金额事实采用 ISO 4217 三位币种；配置 Scope 允许控制通配符 *，交易事实必须使用精确币种。取值来源：ISO 4217；非固定枚举。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  PRIMARY KEY (`channel_transaction_ref_id`),
  UNIQUE KEY `uk_channel_transaction_ref` (`channel_id`, `mid_id`, `reference_type`, `external_transaction_id`) COMMENT '唯一索引：channel_id、mid_id、reference_type、external_transaction_id',
  KEY `idx_txn_ref_action` (`payment_action_id`) COMMENT '访问索引：payment_action_id',
  KEY `idx_txn_ref_mid` (`mid_id`) COMMENT '访问索引：mid_id',
  KEY `idx_txn_ref_original` (`original_channel_txn_ref_id`) COMMENT '访问索引：original_channel_txn_ref_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='授权、捕获、扣款、撤销或退款的不可变服务商外部交易引用。';

CREATE TABLE `t_duplicate_payment_case` (
  `duplicate_payment_case_id` BIGINT NOT NULL COMMENT '第二笔真实渠道成功处置根的内部唯一标识。',
  `payment_intent_id` BIGINT NOT NULL COMMENT '一个来源订单唯一支付目标的内部稳定标识。',
  `canonical_attempt_id` BIGINT NOT NULL COMMENT '支付意图首次权威成功对应的支付尝试标识。',
  `canonical_action_id` BIGINT NOT NULL COMMENT '支付意图首次权威成功对应的渠道动作标识。',
  `canonical_transaction_ref_id` BIGINT NOT NULL COMMENT '支付意图首次权威成功对应的服务商交易引用标识。',
  `duplicate_attempt_id` BIGINT NOT NULL COMMENT '第二笔真实渠道成功对应并进入冲突终态的支付尝试标识。',
  `duplicate_action_id` BIGINT NOT NULL COMMENT '第二笔真实渠道成功对应且需要补偿处置的渠道动作标识。',
  `duplicate_transaction_ref_id` BIGINT NOT NULL COMMENT '第二笔真实渠道成功对应的服务商交易引用标识。',
  `duplicate_amount` DECIMAL(24,8) NOT NULL COMMENT '第二笔真实渠道成功形成且需要退回的确定金额。',
  `currency` CHAR(3) NOT NULL COMMENT '金额事实采用 ISO 4217 三位币种；配置 Scope 允许控制通配符 *，交易事实必须使用精确币种。取值来源：ISO 4217；非固定枚举。',
  `case_status` VARCHAR(64) NOT NULL COMMENT '第二笔真实渠道成功从发现、补偿到人工处置的当前状态。允许值：OPEN、COMPENSATING、COMPENSATION_UNKNOWN、COMPENSATED、MANUAL_REVIEW。',
  `compensation_action_id` BIGINT NULL COMMENT '为处置第二笔真实渠道成功而创建的 REFUND 动作标识；尚未创建时为空。',
  `version` BIGINT NOT NULL COMMENT '可变 Payment 业务根并发命令与乱序结果条件更新使用的单调版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`duplicate_payment_case_id`),
  UNIQUE KEY `uk_dup_case_duplicate_action` (`duplicate_action_id`) COMMENT '唯一索引：duplicate_action_id',
  KEY `idx_dup_case_intent` (`payment_intent_id`) COMMENT '访问索引：payment_intent_id',
  KEY `idx_dup_case_canonical_attempt` (`canonical_attempt_id`) COMMENT '访问索引：canonical_attempt_id',
  KEY `idx_dup_case_duplicate_attempt` (`duplicate_attempt_id`) COMMENT '访问索引：duplicate_attempt_id',
  KEY `idx_dup_case_canonical_action` (`canonical_action_id`) COMMENT '访问索引：canonical_action_id',
  KEY `idx_dup_case_canonical_ref` (`canonical_transaction_ref_id`) COMMENT '访问索引：canonical_transaction_ref_id',
  KEY `idx_dup_case_duplicate_ref` (`duplicate_transaction_ref_id`) COMMENT '访问索引：duplicate_transaction_ref_id',
  KEY `idx_dup_case_comp_action` (`compensation_action_id`) COMMENT '访问索引：compensation_action_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='第二笔真实渠道成功的处置根，不能替换支付意图的首笔成功指针。';

CREATE TABLE `t_payment_refund_position` (
  `payment_refund_position_id` BIGINT NOT NULL COMMENT 'Payment 对原成功交易并发控制预占与已退款金额的位置标识。',
  `payment_intent_id` BIGINT NOT NULL COMMENT '一个来源订单唯一支付目标的内部稳定标识。',
  `successful_attempt_id` BIGINT NOT NULL COMMENT '支付意图首次形成权威成功结果的支付尝试标识。',
  `original_channel_txn_ref_id` BIGINT NOT NULL COMMENT '退款或争议所指向的原始成功服务商交易引用标识。',
  `original_amount` DECIMAL(24,8) NOT NULL COMMENT '成功支付对应且可用于渠道退款的原始金额上限。',
  `reserved_amount` DECIMAL(24,8) NOT NULL COMMENT '已创建但尚未成功或释放的渠道退款占用金额。',
  `refunded_amount` DECIMAL(24,8) NOT NULL COMMENT '原成功支付已经确认通过渠道退回消费者的累计金额。',
  `currency` CHAR(3) NOT NULL COMMENT '金额事实采用 ISO 4217 三位币种；配置 Scope 允许控制通配符 *，交易事实必须使用精确币种。取值来源：ISO 4217；非固定枚举。',
  `version` BIGINT NOT NULL COMMENT '可变 Payment 业务根并发命令与乱序结果条件更新使用的单调版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`payment_refund_position_id`),
  UNIQUE KEY `uk_payment_refund_position` (`payment_intent_id`, `original_channel_txn_ref_id`) COMMENT '唯一索引：payment_intent_id、original_channel_txn_ref_id',
  KEY `idx_refund_pos_attempt` (`successful_attempt_id`) COMMENT '访问索引：successful_attempt_id',
  KEY `idx_refund_pos_orig_ref` (`original_channel_txn_ref_id`) COMMENT '访问索引：original_channel_txn_ref_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Payment 侧渠道可退款金额位置，预占、消费和释放由乐观锁原子保护。';

CREATE TABLE `t_channel_refund` (
  `channel_refund_id` BIGINT NOT NULL COMMENT 'Payment 为一个 Commerce 退款请求创建的唯一渠道退款标识。',
  `commerce_refund_request_id` BIGINT NOT NULL COMMENT 'Commerce 拥有且在 Payment 中唯一对应渠道退款的业务请求标识。',
  `payment_refund_position_id` BIGINT NOT NULL COMMENT 'Payment 对原成功交易并发控制预占与已退款金额的位置标识。',
  `refund_amount` DECIMAL(24,8) NOT NULL COMMENT '本次渠道退款要求服务商退还给消费者的确定金额。',
  `currency` CHAR(3) NOT NULL COMMENT '金额事实采用 ISO 4217 三位币种；配置 Scope 允许控制通配符 *，交易事实必须使用精确币种。取值来源：ISO 4217；非固定枚举。',
  `refund_status` VARCHAR(64) NOT NULL COMMENT '渠道退款从创建、处理、未知到权威终态的当前状态。允许值：CREATED、PROCESSING、UNKNOWN、SUCCEEDED、FAILED、CANCELLED。',
  `version` BIGINT NOT NULL COMMENT '可变 Payment 业务根并发命令与乱序结果条件更新使用的单调版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`channel_refund_id`),
  UNIQUE KEY `uk_channel_refund_commerce_req` (`commerce_refund_request_id`) COMMENT '唯一索引：commerce_refund_request_id',
  KEY `idx_channel_refund_pos` (`payment_refund_position_id`) COMMENT '访问索引：payment_refund_position_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='一个 Commerce Refund Request 的唯一渠道退款根，UNKNOWN 继续占用退款位置。';

CREATE TABLE `t_compensation_event` (
  `id` BIGINT NOT NULL COMMENT '本地补偿事件记录的内部唯一标识。',
  `event_key` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'Handler 读取本服务权威事实使用的稳定业务键，最长 128 个 Java 字符；去首尾空白、拒绝控制字符，大小写敏感。',
  `event_type` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '全应用唯一注册的 Handler 类型，最长 64 个 Java 字符；去首尾空白、拒绝控制字符，大小写敏感。取值来源：Payment Event/Handler Catalog；非固定枚举。',
  `payload_json` VARCHAR(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL COMMENT '可选最小 JSON Object，通常为空；最多 4 KiB UTF-8 字节由框架校验，不得保存可从本库恢复的上下文、卡数据、Vault Token、凭据或临时交互指令。',
  `execute_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'INIT' COMMENT '本地补偿任务执行状态。允许值：INIT、PROCESSING、ERROR、FAILED。成功后删除记录，FAILED 不改变业务资金状态。',
  `execute_count` INT NOT NULL DEFAULT 0 COMMENT '已开始的 Handler 尝试次数，非负，Claim 时增加；达到上限后不得继续产生副作用。',
  `next_execute_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '任务下次允许 Claim 的 UTC 微秒时间，由 Handler 退避策略更新。',
  `lock_owner` VARCHAR(64) NULL COMMENT '当前持有本地补偿事件数据库 Claim Lease 的服务实例标识；未被认领时为空。',
  `lock_until` DATETIME(6) NULL COMMENT '本地补偿事件 Claim Lease 到期并可被其他实例接管的时刻；无活动 Lease 时为空。',
  `last_error_code` VARCHAR(64) NULL COMMENT '补偿事件最近一次执行失败的稳定错误分类代码；尚无错误时为空。取值来源：Payment Error Catalog；非固定枚举。',
  `last_error_summary` VARCHAR(1000) NULL COMMENT '最近一次执行失败的脱敏摘要，最长 1000 字符；无错误时为空，不包含卡数据、凭据或其他敏感原值。',
  `lock_version` BIGINT NOT NULL DEFAULT 1 COMMENT 'Claim/Lease 使用的正乐观锁版本；登记初始值固定为 1，仅当前 Lease 持有者可回写执行结果。',
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '本地补偿记录创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '本地补偿记录最近更新的 UTC 微秒时间，由写入口维护。',
  `sub_event_type` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '同一 Handler 下的受控业务子类型，最长 64 个 Java 字符；与 event_key、event_type 共同组成稳定唯一身份。取值来源：Payment Compensation Sub Event Catalog；非固定枚举。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comp_event_identity` (`event_key`, `event_type`, `sub_event_type`) COMMENT '唯一索引：event_key、event_type、sub_event_type',
  KEY `idx_comp_event_ready` (`execute_status`, `next_execute_at`, `id`) COMMENT '访问索引：execute_status、next_execute_at、id',
  KEY `idx_comp_event_lease` (`execute_status`, `lock_until`, `id`) COMMENT '访问索引：execute_status、lock_until、id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='业务变化同事务登记的本地可靠执行记录；成功后删除，FAILED 保留并可受控重开。';

CREATE TABLE `t_consumed_event` (
  `event_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件信封 eventId（默认等于 eventKey），按其去重。',
  `event_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '已消费领域事件的类型（PascalCase，登记于 paytech-messaging 事件目录）。',
  `aggregate_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件所属聚合的大写类型代码。',
  `aggregate_id` VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件所属聚合的标识，同时是 RabbitMQ 分区路由键。',
  `aggregate_version` BIGINT NOT NULL COMMENT '本条已消费领域事件携带的聚合版本。',
  `consumed_at` DATETIME(6) NOT NULL COMMENT '消费事务提交的 UTC 微秒精度时间。',
  PRIMARY KEY (`event_id`),
  KEY `idx_consumed_event_aggregate` (`aggregate_type`, `aggregate_id`, `aggregate_version`) COMMENT '访问索引：aggregate_type、aggregate_id、aggregate_version'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='已消费领域事件：paytech-messaging 消费端按事件 ID 去重的标准表（design/08 §4），列、类型、主键与索引与 paytech-messaging 的 messaging-consumed-event.sql 一致；事件与版本合法性由消费拦截器保证。';

CREATE TABLE `t_consumed_aggregate_version` (
  `aggregate_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件所属聚合的大写类型代码。',
  `aggregate_id` VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件所属聚合的标识，同时是 RabbitMQ 分区路由键。',
  `last_version` BIGINT NOT NULL COMMENT '本服务已处理的该聚合最大领域事件版本。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`aggregate_type`, `aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='已消费聚合版本：paytech-messaging 消费端按聚合记录已处理的最大版本，用于判定乱序、重放与缺口（design/08 §4）；列、类型与主键与 messaging-consumed-event.sql 一致。';
