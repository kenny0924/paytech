-- ONLY mor-local MySQL at 127.0.0.1:13306; dedicated, EMPTY Channel test database.
-- REVIEW AND APPLY MANUALLY; applications and tests never execute this file.
CREATE DATABASE IF NOT EXISTS mor_payment_channel_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'payment_channel_test'@'%' IDENTIFIED BY 'local-channel-test-only';
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_payment_channel_test.* TO 'payment_channel_test'@'%';
USE mor_payment_channel_test;

-- Code-MoR target schema generated from the approved PDManer model.
-- Apply manually to an EMPTY database only; existing environments require a reviewed migration.
-- Execution status: agent-docs/CURRENT_IMPLEMENTATION.md. Verify Java compatibility before rollout.
-- Source: payment-orchestration-service/docs/database/payment-orchestration.pdma.json
SET NAMES utf8mb4;

CREATE TABLE `t_payment_channel` (
  `channel_id` BIGINT NOT NULL COMMENT 'Payment 管理的支付服务商内部唯一标识。',
  `channel_code` VARCHAR(64) NOT NULL COMMENT '平台稳定识别支付服务商的唯一业务代码。取值来源：Channel Catalog；非固定枚举。',
  `channel_name` VARCHAR(255) NOT NULL COMMENT '供运营配置和审阅展示的支付服务商正式名称。',
  `channel_status` VARCHAR(64) NOT NULL COMMENT '支付服务商从草稿、激活、暂停到关闭的当前运行状态。允许值：DRAFT、ACTIVE、SUSPENDED、CLOSED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`channel_id`),
  UNIQUE KEY `uk_payment_channel_code` (`channel_code`) COMMENT '唯一索引：channel_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='支付渠道的稳定身份和当前运行状态。';

CREATE TABLE `t_channel_account` (
  `channel_account_id` BIGINT NOT NULL COMMENT 'Payment 管理的服务商技术认证账户或租户内部唯一标识。',
  `channel_id` BIGINT NOT NULL COMMENT 'Payment 管理的支付服务商内部唯一标识。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账号所属 Operating Entity 的不可变 CHAR(5) 编号，来源于 CCT；与其当前绑定 MID 的 OE 一致，不作为路由规则。',
  `account_name` VARCHAR(128) NOT NULL COMMENT '运营维护的账号展示名称，不作为唯一技术身份。',
  `account_vault_token` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '引用 CHANNEL_ACCOUNT_CONFIG_V1 的 REFERENCE Token，含账号业务参数及渠道凭据；仅执行前以 CHANNEL_EXECUTION 用途读取；DRAFT 可空，其他状态执行时必须存在；不得进入日志或事件。',
  `account_status` VARCHAR(64) NOT NULL COMMENT '技术认证账户或租户从草稿、激活、暂停到关闭的当前状态。允许值：DRAFT、ACTIVE、SUSPENDED、CLOSED。',
  `remark` VARCHAR(512) NULL COMMENT '运营维护的非敏感补充说明；无备注时为空，禁止填写凭据或个人敏感值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`channel_account_id`),
  UNIQUE KEY `uk_channel_account_owner` (`channel_account_id`, `channel_id`) COMMENT '唯一索引：channel_account_id、channel_id',
  KEY `idx_channel_account_status` (`channel_id`, `account_status`) COMMENT '访问索引：channel_id、account_status',
  KEY `idx_channel_account_entity` (`entity_no`, `account_status`) COMMENT '访问索引：entity_no、account_status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='归属单个 Channel 与 OE 的技术账号；账号参数及凭据只保存 Vault 引用。';

CREATE TABLE `t_channel_mid` (
  `mid_id` BIGINT NOT NULL COMMENT 'Payment 管理且唯一归属于一个 Operating Entity 的服务商商户号标识。',
  `channel_id` BIGINT NOT NULL COMMENT 'Payment 管理的支付服务商内部唯一标识。',
  `channel_account_id` BIGINT NOT NULL COMMENT 'Payment 管理的服务商技术认证账户或租户内部唯一标识。',
  `external_mid_code` VARCHAR(128) NOT NULL COMMENT '服务商侧识别该 MID 的稳定代码，不保存凭据或 Secret。取值来源：Channel Adapter Catalog；非固定枚举。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'CCT唯一不可变OE编号，创建时经营国家二位代码加001–999，如AE001；不是数字代理ID，编号不参与路由。',
  `mid_status` VARCHAR(64) NOT NULL COMMENT '服务商商户号从草稿、激活、暂停到关闭的当前状态。允许值：DRAFT、ACTIVE、SUSPENDED、CLOSED。',
  `activated_at` DATETIME(6) NULL COMMENT '商户号首次进入可承接生产交易状态的业务时刻。',
  `closed_at` DATETIME(6) NULL COMMENT '商户号永久进入关闭状态的业务时刻；未关闭时为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`mid_id`),
  UNIQUE KEY `uk_channel_mid` (`channel_id`, `external_mid_code`) COMMENT '唯一索引：channel_id、external_mid_code',
  KEY `idx_channel_mid_account` (`channel_account_id`, `mid_status`) COMMENT '访问索引：channel_account_id、mid_status',
  KEY `idx_channel_mid_entity` (`entity_no`, `mid_status`) COMMENT '访问索引：entity_no、mid_status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='服务商商户号与唯一 Operating Entity 的稳定归属及当前 Channel Account 绑定；归属创建后不得改写。';

CREATE TABLE `t_mid_capability` (
  `mid_capability_id` BIGINT NOT NULL COMMENT '一个 MID 在完整交易条件组合下的能力配置组标识；明细按能力代码分别解析。',
  `mid_id` BIGINT NOT NULL COMMENT 'Payment 管理且唯一归属于一个 Operating Entity 的服务商商户号标识。',
  `payment_method_code` VARCHAR(64) NOT NULL COMMENT '支付尝试冻结本次使用的精确支付方式目录值；商户号 Capability 与 Health State 使用精确值，Routing Policy 可使用精确值或控制通配符 *。取值来源：Payment Method Catalog；非固定枚举。',
  `scheme_code` VARCHAR(64) NOT NULL COMMENT '支付体系或网络代码，可为精确代码或 *；请求未提供体系时仅匹配 *。取值来源：Scheme Catalog；非固定枚举。',
  `country_code` CHAR(2) NOT NULL COMMENT '匹配 Commerce 冻结到 Payment Intent 的消费者国家，可为精确两位代码或 *。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `currency` CHAR(3) NOT NULL COMMENT '金额事实采用 ISO 4217 三位币种；配置 Scope 允许控制通配符 *，交易事实必须使用精确币种。取值来源：ISO 4217；非固定枚举。',
  `mcc_code` VARCHAR(4) NOT NULL COMMENT '渠道批准用于该 MID 的精确四位业务 MCC，不允许 *，不代表默认收单 MCC。取值来源：CCT/Commerce frozen classification；非固定枚举。',
  `capability_status` VARCHAR(64) NOT NULL COMMENT '整组能力条件的启停状态；DISABLED 不参与匹配，不等价于任一 BOOLEAN 能力的 false。允许值：ENABLED、DISABLED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`mid_capability_id`),
  UNIQUE KEY `uk_mid_capability_scope` (`mid_id`, `payment_method_code`, `scheme_code`, `country_code`, `currency`, `mcc_code`) COMMENT '唯一索引：mid_id、payment_method_code、scheme_code、country_code、currency、mcc_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='商户号按支付方式、支付体系、消费者国家、币种和精确 MCC 定义的能力条件组。';

CREATE TABLE `t_mid_capability_detail` (
  `mid_capability_detail_id` BIGINT NOT NULL COMMENT '能力条件组内一项类型化能力值的唯一标识。',
  `mid_capability_id` BIGINT NOT NULL COMMENT '一条商户号支付方式、卡组织、地域与动作能力配置的内部唯一标识。',
  `capability_code` VARCHAR(128) NOT NULL COMMENT 'MID 固定五维 Scope 下受控的静态路由能力代码。取值来源：MID Capability Catalog；非固定枚举。',
  `value_type` VARCHAR(64) NOT NULL COMMENT 'MID 静态能力值采用 BOOLEAN、DECIMAL、INTEGER 或 CODE 的闭合类型。允许值：BOOLEAN、DECIMAL、CODE。',
  `boolean_value` TINYINT(1) NULL COMMENT 'BOOLEAN 类型的能力值：1 明确支持，0 明确不支持；其他类型必须为 NULL。',
  `decimal_value` DECIMAL(24,8) NULL COMMENT 'DECIMAL 类型的精确能力值；金额能力使用交易币种主单位；其他类型必须为 NULL。',
  `code_value` VARCHAR(128) NULL COMMENT 'CODE 类型的能力目录值；其他类型必须为 NULL；允许值由该能力的注册定义限定。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`mid_capability_detail_id`),
  UNIQUE KEY `uk_mid_cap_detail_code` (`mid_capability_id`, `capability_code`) COMMENT '唯一索引：mid_capability_id、capability_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='能力条件组下的类型化能力值；按能力代码唯一，值类型与受控目录一致。';

-- 执行链路五表（2026-10-14 PayerMax 卡直付对接，经用户授权本机执行；原文取自正式 Schema）。
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
  `interaction_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '原 Action 的消费者交互类型，无交互时为空。允许值：REDIRECT、THREE_DS_CHALLENGE、SDK、APP_SWITCH、QR_CODE。本期只写 THREE_DS_CHALLENGE。',
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
