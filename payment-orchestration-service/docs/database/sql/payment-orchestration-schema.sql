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

CREATE TABLE `t_routing_policy` (
  `routing_policy_id` BIGINT NOT NULL COMMENT '按固定交易作用域匹配的稳定路由策略根标识。',
  `scope_type` VARCHAR(64) NOT NULL COMMENT '路由策略适用 Client、Operating Entity 或全局层级的闭合类型。允许值：CLIENT、OPERATING_ENTITY、GLOBAL。',
  `scope_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '按 scope_type 解释的稳定字符串；CLIENT 为规范十进制ID，OPERATING_ENTITY 为五位entityNo，GLOBAL 固定字符串0，不允许跨类型强制转换。',
  `country_code` CHAR(2) NOT NULL COMMENT '支付能力或路由条件采用的国家维度；精确值遵循 ISO 3166-1 alpha-2，配置 Scope 允许控制通配符 *。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `currency` CHAR(3) NOT NULL COMMENT '金额事实采用 ISO 4217 三位币种；配置 Scope 允许控制通配符 *，交易事实必须使用精确币种。取值来源：ISO 4217；非固定枚举。',
  `payment_method_code` VARCHAR(64) NOT NULL COMMENT '支付尝试冻结本次使用的精确支付方式目录值；商户号 Capability 与 Health State 使用精确值，Routing Policy 可使用精确值或控制通配符 *。取值来源：Payment Method Catalog；非固定枚举。',
  `industry_code` VARCHAR(64) NOT NULL COMMENT 'Commerce 从 Client & Contract 冻结并传入的平台行业目录代码。取值来源：CCT/Commerce frozen classification；非固定枚举。',
  `mcc_code` VARCHAR(4) NOT NULL COMMENT '精确值为 Commerce 从 Client & Contract 冻结的四位 Merchant Category Code；配置 Scope 允许控制通配符 *。取值来源：CCT/Commerce frozen classification；非固定枚举。',
  `match_priority` INT NOT NULL COMMENT '同一作用域匹配层级内用于稳定排序的优先级，数值越小优先。',
  `policy_status` VARCHAR(64) NOT NULL COMMENT '路由策略当前可参与解析、暂停或永久关闭的状态。允许值：ACTIVE、SUSPENDED、CLOSED。',
  `current_version_id` BIGINT NULL COMMENT '稳定配置根当前唯一允许用于生产解析的已发布版本标识；尚未首次发布或未启用时可为空。',
  `version` BIGINT NOT NULL COMMENT '可变 Payment 业务根并发命令与乱序结果条件更新使用的单调版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`routing_policy_id`),
  UNIQUE KEY `uk_routing_policy_scope` (`scope_type`, `scope_id`, `country_code`, `currency`, `payment_method_code`, `industry_code`, `mcc_code`, `match_priority`) COMMENT '唯一索引：scope_type、scope_id、country_code、currency、payment_method_code、industry_code、mcc_code、match_priority',
  UNIQUE KEY `uk_route_policy_cur_ver` (`current_version_id`) COMMENT '唯一索引：current_version_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='按固定交易作用域匹配的路由策略根，当前发布版本只由 current_version_id 指针确定。';

CREATE TABLE `t_routing_policy_version` (
  `routing_policy_version_id` BIGINT NOT NULL COMMENT '路由策略不可变候选集合发布版本的内部唯一标识。',
  `routing_policy_id` BIGINT NOT NULL COMMENT '按固定交易作用域匹配的稳定路由策略根标识。',
  `version_no` INT NOT NULL COMMENT '同一路由策略根内从 1 开始递增的业务发布版本序号。',
  `version_status` VARCHAR(64) NOT NULL COMMENT '不可变配置版本从草稿、激活或验证到退役的当前状态。允许值：DRAFT、ACTIVE、RETIRED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  PRIMARY KEY (`routing_policy_version_id`),
  UNIQUE KEY `uk_routing_policy_version` (`routing_policy_id`, `version_no`) COMMENT '唯一索引：routing_policy_id、version_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='不可变路由候选集发布版本。';

CREATE TABLE `t_routing_policy_mid` (
  `routing_policy_mid_id` BIGINT NOT NULL COMMENT '一个路由策略版本内候选商户号配置的内部唯一标识。',
  `routing_policy_version_id` BIGINT NOT NULL COMMENT '路由策略不可变候选集合发布版本的内部唯一标识。',
  `mid_id` BIGINT NOT NULL COMMENT 'Payment 管理且唯一归属于一个 Operating Entity 的服务商商户号标识。',
  `candidate_priority` INT NOT NULL COMMENT '路由策略中候选商户号的优先顺序，数值越小优先级越高。',
  `weight_value` DECIMAL(18,10) NOT NULL COMMENT '同一候选优先级内执行稳定加权选择的正数权重。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  PRIMARY KEY (`routing_policy_mid_id`),
  UNIQUE KEY `uk_routing_policy_mid` (`routing_policy_version_id`, `mid_id`) COMMENT '唯一索引：routing_policy_version_id、mid_id',
  KEY `idx_route_policy_mid_mid` (`mid_id`) COMMENT '访问索引：mid_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='路由策略版本中的候选商户号、优先级和权重。';

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
  `expires_at` DATETIME(6) NOT NULL COMMENT '首次用卡资格的 UTC 微秒精度截止时间，创建时按原支付和材料期限冻结；重试不延长，不表示在途资金失效。',
  `revoked_at` DATETIME(6) NULL COMMENT '主动撤销新用卡资格的 UTC 微秒精度时间；NULL 表示未主动撤销，仍须检查期限和原执行事实；不能据此撤销渠道资金。',
  `create_time` DATETIME(6) NOT NULL COMMENT '应用写入的工具绑定创建 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '应用写入的工具绑定最近有效更新 UTC 微秒精度时间。',
  PRIMARY KEY (`payment_instrument_token_id`),
  UNIQUE KEY `uk_pay_token_value` (`payment_instrument_token`) COMMENT '唯一索引：payment_instrument_token',
  UNIQUE KEY `uk_pay_token_scope` (`client_id`, `payment_intent_id`, `source_submission_id`) COMMENT '唯一索引：client_id、payment_intent_id、source_submission_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Execution 拥有的交易级支付工具 Token 与不可变使用范围，不存卡数据或会话状态。';

CREATE TABLE `t_payment_card` (
  `payment_card_id` BIGINT NOT NULL COMMENT 'Card 卡的 Snowflake 内部主键，创建后不可修改。',
  `card_fingerprint` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '以配置指纹密钥对卡号做 HMAC-SHA256 的小写十六进制；同一卡号恒定，用于按卡号定位与 Risk 评估，不能反推卡号。',
  `card_bin` VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '卡号不少于 16 位时为前 8 位，否则为前 6 位；创建后不可修改。',
  `pan_last4` CHAR(4) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '卡号后 4 位；创建后不可修改。',
  `encrypted_pan` VARBINARY(128) NOT NULL COMMENT '以本卡 DEK 做 AES-256-GCM 加密的卡号：IV(12) ‖ 密文 ‖ Tag(16)，AAD 绑定 payment_card_id。',
  `dek_vault_token` CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本卡数据加密密钥（DEK）在 Vault 数据类型 PAYMENT_CARD_DEK 下的 REFERENCE Token；DEK 明文不落库。',
  `create_time` DATETIME(6) NOT NULL COMMENT '应用写入的卡创建 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '应用写入的卡最近有效更新 UTC 微秒精度时间。',
  PRIMARY KEY (`payment_card_id`),
  UNIQUE KEY `uk_payment_card_fingerprint` (`card_fingerprint`) COMMENT '唯一索引：card_fingerprint'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Card 拥有的卡，每个卡号一条（按卡指纹唯一），每张卡一把由 Vault 托管的 DEK；只加密保存卡号，不存 CVV、磁道、PIN 或 DEK 明文。';

CREATE TABLE `t_payment_card_credential` (
  `payment_card_credential_id` BIGINT NOT NULL COMMENT '卡凭证的 Snowflake 内部主键，创建后不可修改。',
  `payment_card_id` BIGINT NOT NULL COMMENT '所属卡，创建后不可修改。',
  `credential_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '以配置指纹密钥对卡号、有效期与规范化持卡人做 HMAC-SHA256 的小写十六进制；同一张卡下唯一。',
  `encrypted_details` VARBINARY(1024) NOT NULL COMMENT '以所属卡 DEK 做 AES-256-GCM 加密的有效期与持卡人原值：IV(12) ‖ 密文 ‖ Tag(16)，AAD 绑定 payment_card_credential_id。',
  `create_time` DATETIME(6) NOT NULL COMMENT '应用写入的卡凭证创建 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '应用写入的卡凭证最近有效更新 UTC 微秒精度时间。',
  PRIMARY KEY (`payment_card_credential_id`),
  UNIQUE KEY `uk_payment_card_credential` (`payment_card_id`, `credential_digest`) COMMENT '唯一索引：payment_card_id、credential_digest'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='同一张卡下每组有效期与规范化持卡人一条；有效期与持卡人原值以所属卡的 DEK 加密保存。';

CREATE TABLE `t_payment_card_instrument` (
  `payment_card_instrument_id` BIGINT NOT NULL COMMENT '卡交易工具的 Snowflake 内部主键；其十进制字符串即支付工具 Token 的 instrument_ref。',
  `payment_card_credential_id` BIGINT NOT NULL COMMENT '本次使用的卡凭证，创建后不可修改。',
  `client_id` BIGINT NOT NULL COMMENT '所属 CCT Client 稳定标识，取自已核对的 Payment Intent，创建后不可修改。',
  `create_time` DATETIME(6) NOT NULL COMMENT '应用写入的卡交易工具创建 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '应用写入的卡交易工具最近有效更新 UTC 微秒精度时间。',
  PRIMARY KEY (`payment_card_instrument_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='每个支付工具一条，记录本次使用的卡凭证与所属 Client；主键十进制字符串即支付工具 Token 的 instrument_ref。';

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

CREATE TABLE `t_channel_interaction` (
  `channel_interaction_id` BIGINT NOT NULL COMMENT '一次真实服务商 HTTP 交互记录的内部唯一标识。',
  `payment_action_id` BIGINT NULL COMMENT '一次内部风控或渠道业务动作的内部唯一标识。',
  `channel_dispute_action_id` BIGINT NULL COMMENT '一次按 Risk 指令提交举证或查询服务商争议的动作标识。',
  `interaction_no` INT NOT NULL COMMENT '同一渠道动作内从 1 开始递增的真实 HTTP 交互次数。',
  `interaction_status` VARCHAR(64) NOT NULL COMMENT '一次服务商 HTTP 交互从已开始到响应、网络错误或超时的当前状态。允许值：STARTED、RESPONSE_RECEIVED、NETWORK_ERROR、TIMED_OUT。',
  `started_at` DATETIME(6) NOT NULL COMMENT '向服务商实际发起 HTTP 交互前记录的业务时刻。',
  `finished_at` DATETIME(6) NULL COMMENT '服务商 HTTP 交互收到响应、发生网络错误或超时的业务时刻。',
  `request_file_id` BIGINT NULL COMMENT 'Platform Support 保存的受控加密服务商请求文件标识；未留存时为空。',
  `request_content_hash` CHAR(64) NULL COMMENT '通过安全扫描后留存服务商请求文件的 SHA-256 完整性哈希；未留存时为空。',
  `response_file_id` BIGINT NULL COMMENT 'Platform Support 保存的受控加密服务商响应文件标识；未留存时为空。',
  `response_content_hash` CHAR(64) NULL COMMENT '通过安全扫描后留存服务商响应文件的 SHA-256 完整性哈希；未留存时为空。',
  `http_status` INT NULL COMMENT '服务商 HTTP 响应状态码；无有效 HTTP 响应时为空。',
  `channel_result_code` VARCHAR(128) NULL COMMENT '服务商适配器从响应中提取的原始业务结果代码；无响应时为空。取值来源：Channel Adapter Catalog；非固定枚举。',
  `latency_ms` INT NULL COMMENT '服务商 HTTP 交互从开始到结束的总耗时毫秒数；尚未结束时为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`channel_interaction_id`),
  UNIQUE KEY `uk_channel_interaction_action` (`payment_action_id`, `interaction_no`) COMMENT '唯一索引：payment_action_id、interaction_no',
  UNIQUE KEY `uk_channel_interact_dispute_act` (`channel_dispute_action_id`, `interaction_no`) COMMENT '唯一索引：channel_dispute_action_id、interaction_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='一次真实服务商 HTTP 交互的受控通信记录，可选文件引用必须通过安全扫描。';

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

CREATE TABLE `t_channel_callback` (
  `channel_callback_id` BIGINT NOT NULL COMMENT '一次服务商回调信封的内部唯一标识。',
  `channel_id` BIGINT NOT NULL COMMENT 'Payment 管理的支付服务商内部唯一标识。',
  `channel_account_id` BIGINT NOT NULL COMMENT 'Payment 管理的服务商技术认证账户或租户内部唯一标识。',
  `external_event_id` VARCHAR(255) NULL COMMENT '同一 Channel Account 内由服务商提供的回调事件唯一编号；未提供时为空并使用账户内载荷指纹去重。',
  `payload_fingerprint` CHAR(64) NULL COMMENT '服务商未提供外部事件编号时，对同一 Channel Account 的规范化回调信封生成的去重指纹。',
  `signature_status` VARCHAR(64) NOT NULL COMMENT '服务商回调签名已验证、被拒绝或无法确定的当前结论。允许值：VERIFIED、REJECTED、UNKNOWN。',
  `callback_status` VARCHAR(64) NOT NULL COMMENT '服务商回调信封从接收、处理到归档或异常的当前状态。允许值：RECEIVED、PROCESSING、PROCESSED、REJECTED、EXCEPTION。',
  `received_at` DATETIME(6) NOT NULL COMMENT 'Payment 首次收到服务商回调或争议通知的业务时刻。',
  `file_id` BIGINT NULL COMMENT 'Platform Support 保存的受控加密回调原文文件标识；未留存原文时为空。',
  `content_hash` CHAR(64) NULL COMMENT '通过安全扫描后留存文件内容的 SHA-256 完整性哈希；没有受控文件时为空。',
  `version` BIGINT NOT NULL COMMENT '可变 Payment 业务根并发命令与乱序结果条件更新使用的单调版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`channel_callback_id`),
  UNIQUE KEY `uk_channel_callback_dedup` (`channel_id`, `channel_account_id`, `external_event_id`) COMMENT '唯一索引：channel_id、channel_account_id、external_event_id',
  UNIQUE KEY `uk_channel_callback_fingerprint` (`channel_id`, `channel_account_id`, `payload_fingerprint`) COMMENT '唯一索引：channel_id、channel_account_id、payload_fingerprint',
  KEY `idx_callback_account` (`channel_account_id`) COMMENT '访问索引：channel_account_id',
  KEY `idx_callback_pending` (`callback_status`, `received_at`, `channel_callback_id`) COMMENT '访问索引：callback_status、received_at、channel_callback_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='服务商回调信封的 Channel Account 上下文、验签、去重和批次处理状态。';

CREATE TABLE `t_channel_callback_item` (
  `channel_callback_item_id` BIGINT NOT NULL COMMENT '批量服务商回调中一条不可变项目的内部唯一标识。',
  `channel_callback_id` BIGINT NOT NULL COMMENT '一次服务商回调信封的内部唯一标识。',
  `item_no` INT NOT NULL COMMENT '同一批量回调信封内从 1 开始递增的项目序号。',
  `event_type` VARCHAR(64) NOT NULL COMMENT '当前记录表达的服务商事件、业务事件或本地恢复任务类型。取值来源：Channel Adapter Catalog；非固定枚举。',
  `external_transaction_id` VARCHAR(255) NULL COMMENT '服务商命名空间内标识一笔授权、捕获、扣款、撤销或退款的交易编号。',
  `target_type` VARCHAR(64) NOT NULL COMMENT '回调项映射到渠道动作、退款或争议对象的闭合类型。允许值：PAYMENT_ACTION、CHANNEL_REFUND、CHANNEL_DISPUTE_NOTICE、CHANNEL_DISPUTE_ACTION、UNKNOWN。',
  `target_id` BIGINT NULL COMMENT '回调项映射到的 Payment 本库业务对象标识；未匹配时为空。',
  `normalized_status` VARCHAR(64) NULL COMMENT '服务商适配器把回调原始结果映射出的标准状态代码；无法归一化时为空。取值来源：Channel Adapter Catalog；非固定枚举。',
  `channel_event_time` DATETIME(6) NULL COMMENT '服务商声明渠道交易、回调项或争议事件发生的业务时刻；未提供时为空。',
  `mapping_status` VARCHAR(64) NOT NULL COMMENT '回调项被映射、无法匹配、匹配歧义或无需处理的闭合结论。允许值：MAPPED、UNMATCHED、AMBIGUOUS、IGNORED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  PRIMARY KEY (`channel_callback_item_id`),
  UNIQUE KEY `uk_channel_callback_item` (`channel_callback_id`, `item_no`) COMMENT '唯一索引：channel_callback_id、item_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='批量服务商回调中逐项归一化和目标映射的不可变证据。';

CREATE TABLE `t_channel_event_exception` (
  `channel_event_exception_id` BIGINT NOT NULL COMMENT '一个服务商回调签名、映射、时序、状态或安全异常处置记录标识。',
  `channel_callback_id` BIGINT NOT NULL COMMENT '一次服务商回调信封的内部唯一标识。',
  `channel_callback_item_id` BIGINT NULL COMMENT '批量服务商回调中一条不可变项目的内部唯一标识。',
  `exception_type` VARCHAR(64) NOT NULL COMMENT '服务商回调在签名、匹配、时序、状态或载荷安全方面的异常分类。允许值：SIGNATURE、MATCHING、OUT_OF_ORDER、STATE_CONFLICT、PAYLOAD_SECURITY。',
  `exception_status` VARCHAR(64) NOT NULL COMMENT '服务商事件异常从发现、调查到解决或忽略的当前状态。允许值：OPEN、INVESTIGATING、RESOLVED、IGNORED。',
  `reason_code` VARCHAR(64) NULL COMMENT '当前状态流转、异常、健康或争议事实采用的稳定原因代码；无需原因时为空。取值来源：Reason Catalog；非固定枚举。',
  `resolved_by` VARCHAR(128) NULL COMMENT '完成服务商事件异常处置的人员或系统标识；未解决时为空。',
  `resolved_at` DATETIME(6) NULL COMMENT '服务商事件异常进入解决或忽略终态的业务时刻；未解决时为空。',
  `version` BIGINT NOT NULL COMMENT '可变 Payment 业务根并发命令与乱序结果条件更新使用的单调版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`channel_event_exception_id`),
  KEY `idx_event_exc_callback` (`channel_callback_id`) COMMENT '访问索引：channel_callback_id',
  KEY `idx_event_exc_item` (`channel_callback_item_id`) COMMENT '访问索引：channel_callback_item_id',
  KEY `idx_event_exc_open` (`exception_status`, `create_time`, `channel_event_exception_id`) COMMENT '访问索引：exception_status、create_time、channel_event_exception_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='签名、匹配、乱序或非资金状态冲突的独立处置记录。';

CREATE TABLE `t_channel_dispute_notice` (
  `channel_dispute_notice_id` BIGINT NOT NULL COMMENT '一个服务商争议身份、金额与当前渠道状态根的内部唯一标识。',
  `channel_id` BIGINT NOT NULL COMMENT 'Payment 管理的支付服务商内部唯一标识。',
  `mid_id` BIGINT NOT NULL COMMENT 'Payment 管理且唯一归属于一个 Operating Entity 的服务商商户号标识。',
  `original_channel_txn_ref_id` BIGINT NOT NULL COMMENT '退款或争议所指向的原始成功服务商交易引用标识。',
  `external_dispute_id` VARCHAR(255) NOT NULL COMMENT '服务商命名空间内稳定且不可复用的争议业务编号。',
  `dispute_amount` DECIMAL(24,8) NOT NULL COMMENT '服务商通知并由 Payment 冻结的争议资金金额。',
  `currency` CHAR(3) NOT NULL COMMENT '金额事实采用 ISO 4217 三位币种；配置 Scope 允许控制通配符 *，交易事实必须使用精确币种。取值来源：ISO 4217；非固定枚举。',
  `received_at` DATETIME(6) NOT NULL COMMENT 'Payment 首次收到服务商回调或争议通知的业务时刻。',
  `channel_deadline_at` DATETIME(6) NULL COMMENT '服务商要求完成争议举证提交的最晚业务时刻；未给出时为空。',
  `current_channel_status` VARCHAR(64) NOT NULL COMMENT '服务商争议在渠道侧的当前权威生命周期状态。允许值：RECEIVED、EVIDENCE_REQUIRED、SUBMITTED、UNDER_REVIEW、WON、LOST、CLOSED。',
  `latest_event_id` BIGINT NULL COMMENT '当前争议状态所对应的最新不可变服务商争议事件标识；尚无事件时为空。',
  `latest_event_no` INT NOT NULL COMMENT '争议通知已经原子应用的最大事件序号。',
  `version` BIGINT NOT NULL COMMENT '可变 Payment 业务根并发命令与乱序结果条件更新使用的单调版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`channel_dispute_notice_id`),
  UNIQUE KEY `uk_channel_dispute_external` (`channel_id`, `external_dispute_id`) COMMENT '唯一索引：channel_id、external_dispute_id',
  KEY `idx_dispute_notice_mid` (`mid_id`) COMMENT '访问索引：mid_id',
  KEY `idx_dispute_notice_orig_ref` (`original_channel_txn_ref_id`) COMMENT '访问索引：original_channel_txn_ref_id',
  UNIQUE KEY `uk_dispute_notice_latest` (`latest_event_id`) COMMENT '唯一索引：latest_event_id',
  KEY `idx_dispute_status_due` (`current_channel_status`, `channel_deadline_at`) COMMENT '访问索引：current_channel_status、channel_deadline_at'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='服务商争议身份、金额事实和当前渠道状态根。';

CREATE TABLE `t_channel_dispute_event` (
  `channel_dispute_event_id` BIGINT NOT NULL COMMENT '一条不可变服务商争议事件的内部唯一标识。',
  `channel_dispute_notice_id` BIGINT NOT NULL COMMENT '一个服务商争议身份、金额与当前渠道状态根的内部唯一标识。',
  `event_no` INT NOT NULL COMMENT '同一服务商争议内从 1 开始单调递增的不可变事件序号。',
  `event_type` VARCHAR(64) NOT NULL COMMENT '当前记录表达的服务商事件、业务事件或本地恢复任务类型。允许值：NOTICE_RECEIVED、STATUS_CHANGED、OUTCOME_RECEIVED、DEADLINE_UPDATED。',
  `from_status` VARCHAR(64) NULL COMMENT '状态变更发生前的聚合或服务商争议状态；对象创建时可为空。取值来源：Channel Dispute Status Catalog；非固定枚举。',
  `to_status` VARCHAR(64) NULL COMMENT '状态变更完成后形成的聚合或服务商争议状态。取值来源：Channel Dispute Status Catalog；非固定枚举。',
  `channel_event_time` DATETIME(6) NULL COMMENT '服务商声明渠道交易、回调项或争议事件发生的业务时刻；未提供时为空。',
  `reason_code` VARCHAR(64) NULL COMMENT '当前状态流转、异常、健康或争议事实采用的稳定原因代码；无需原因时为空。取值来源：Reason Catalog；非固定枚举。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  PRIMARY KEY (`channel_dispute_event_id`),
  UNIQUE KEY `uk_channel_dispute_event` (`channel_dispute_notice_id`, `event_no`) COMMENT '唯一索引：channel_dispute_notice_id、event_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='服务商争议的不可变事件流。';

CREATE TABLE `t_channel_dispute_action` (
  `channel_dispute_action_id` BIGINT NOT NULL COMMENT '一次按 Risk 指令提交举证或查询服务商争议的动作标识。',
  `channel_dispute_notice_id` BIGINT NOT NULL COMMENT '一个服务商争议身份、金额与当前渠道状态根的内部唯一标识。',
  `dispute_case_id` BIGINT NOT NULL COMMENT 'Risk & Compliance 拥有并指示本次渠道争议动作的案件标识。',
  `dispute_submission_request_id` BIGINT NULL COMMENT 'Risk & Compliance 下达的精确争议提交请求标识；查询动作可为空。',
  `dispute_submission_request_version` BIGINT NULL COMMENT '本次动作冻结使用的 Risk 争议提交请求版本；查询动作可为空。',
  `dispute_evidence_package_id` BIGINT NULL COMMENT 'Risk & Compliance 为本次提交冻结的证据包标识；查询动作可为空。',
  `dispute_evidence_package_version` BIGINT NULL COMMENT '本次提交严格使用的 Risk 证据包版本；查询动作可为空。',
  `action_type` VARCHAR(64) NOT NULL COMMENT '本次向服务商执行的资金、查询或争议处理动作类型。允许值：SUBMIT_EVIDENCE、QUERY。',
  `action_status` VARCHAR(64) NOT NULL COMMENT '渠道动作从创建、提交到权威结果收敛的当前状态。允许值：CREATED、SUBMITTED、UNKNOWN、SUCCEEDED、FAILED。',
  `channel_id` BIGINT NOT NULL COMMENT 'Payment 管理的支付服务商内部唯一标识。',
  `mid_id` BIGINT NOT NULL COMMENT 'Payment 管理且唯一归属于一个 Operating Entity 的服务商商户号标识。',
  `channel_account_id` BIGINT NOT NULL COMMENT 'Payment 管理的服务商技术认证账户或租户内部唯一标识。',
  `channel_idempotency_key` VARCHAR(128) NOT NULL COMMENT '同一渠道动作网络重发时保持不变并由服务商识别的幂等键。',
  `channel_result_time` DATETIME(6) NULL COMMENT '渠道动作获得当前服务商结果的业务时刻；尚无结果时为空。',
  `version` BIGINT NOT NULL COMMENT '可变 Payment 业务根并发命令与乱序结果条件更新使用的单调版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`channel_dispute_action_id`),
  UNIQUE KEY `uk_dispute_action_idempotency` (`channel_id`, `channel_idempotency_key`) COMMENT '唯一索引：channel_id、channel_idempotency_key',
  KEY `idx_dispute_action_notice` (`channel_dispute_notice_id`) COMMENT '访问索引：channel_dispute_notice_id',
  KEY `idx_dispute_action_mid` (`mid_id`) COMMENT '访问索引：mid_id',
  KEY `idx_dispute_action_account` (`channel_account_id`) COMMENT '访问索引：channel_account_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='按 Risk 精确请求提交举证或查询并冻结 Channel Account 的服务商动作。';

CREATE TABLE `t_command_idempotency` (
  `command_idempotency_id` BIGINT NOT NULL COMMENT '一次 Payment 同步写命令幂等执行记录的内部唯一标识。',
  `caller_scope` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '隔离同步命令幂等键命名空间的稳定调用方作用域。取值来源：Payment Caller Scope Catalog；非固定枚举。',
  `operation_type` VARCHAR(64) NOT NULL COMMENT 'Payment 同步写命令采用的受控业务操作代码。取值来源：Payment Command Operation Catalog；非固定枚举。',
  `idempotency_key` VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '调用方在命令作用域内提供的大小写敏感幂等键；pay 固定为 submission 加冒号及原提交标识。',
  `request_hash` CHAR(64) NOT NULL COMMENT '幂等命令首次规范化请求的完整性哈希，用于拒绝同键不同请求。',
  `request_hash_version` INT NOT NULL COMMENT 'Payment 拥有的请求身份算法版本；PAY_INSTRUMENT_V1 使用版本 1，未知历史不能默认回填。',
  `request_snapshot_schema_version` INT NULL COMMENT '可选恢复请求的结构版本；当前 pay 不保存请求快照时为空。',
  `request_snapshot_json` JSON NULL COMMENT '可选最小恢复输入；当前 pay 为空，不得保存工具 Token、卡数据或临时交互凭据。',
  `operation_context_time` DATETIME(6) NOT NULL COMMENT '首次 Claim 冻结的 UTC 微秒业务时刻，不随重放更新。',
  `recovery_stage` VARCHAR(64) NULL COMMENT 'Payment 定义的命令恢复阶段；pay 在同一本地事务内由 CLAIMED 到 COMPLETED，不承载支付流程步骤。取值来源：Payment Command Recovery Stage Catalog；非固定枚举。',
  `process_status` VARCHAR(64) NOT NULL COMMENT 'Foundation 命令的处理中、成功或失败技术状态；pay 成功仅表示受理完成，不代表资金成功。允许值：PROCESSING、SUCCEEDED、FAILED。',
  `resource_type` VARCHAR(64) NULL COMMENT '幂等命令结果所指 Payment 业务资源的目录类型；无资源时为空。取值来源：Payment Command Resource Type Catalog；非固定枚举。',
  `resource_id` BIGINT NULL COMMENT '幂等命令创建或作用到的 Payment 本库业务资源标识；无资源时为空。',
  `resource_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '可选稳定业务编号；pay 已使用 Attempt 内部标识时为空。',
  `response_code` VARCHAR(64) NULL COMMENT 'Payment 命令的稳定回执代码；pay 使用 PAY_ACCEPTED 表示受理完成，不表示资金成功。取值来源：Payment Command Response Code Catalog；非固定枚举。',
  `response_http_status` INT NULL COMMENT '命令完成的稳定 HTTP 回执元数据；pay 固定 202，不替代按当前支付状态生成的 HTTP 200 或 202。',
  `response_snapshot_schema_version` INT NULL COMMENT '可选稳定响应正文的结构版本；pay 不冻结响应正文时为空。',
  `response_location` VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '可选稳定资源位置；pay 无 Location 合同时为空，不保存临时交互链接。',
  `response_body_json` JSON NULL COMMENT '可选稳定响应正文；pay 为空，当前支付状态与 observedAt 在重放时重新投影。',
  `lock_owner` VARCHAR(64) NULL COMMENT '本次命令 Claim 的唯一持有者；pay 使用请求级随机 UUID，完成后由公共组件清空。',
  `lock_until` DATETIME(6) NULL COMMENT '命令 Claim Lease 的 UTC 微秒截止时间；pay 仅覆盖本地短事务，完成 CAS 必须仍在租期内，完成后清空。',
  `version` BIGINT NOT NULL COMMENT 'Foundation 命令 Claim、Lease 和完成 CAS 使用的技术版本；首次为 1，独立于 Payment 业务根版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`command_idempotency_id`),
  UNIQUE KEY `uk_command_idempotency` (`caller_scope`, `operation_type`, `idempotency_key`) COMMENT '唯一索引：caller_scope、operation_type、idempotency_key'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Foundation 公共组件持久化的 Payment 本地命令 Claim、Lease、CAS 与结果定位；业务幂等语义由 Payment 拥有。';

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
