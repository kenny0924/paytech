-- Code-MoR target schema generated from the approved PDManer model.
-- Apply manually to an EMPTY database only; existing environments require a reviewed migration.
-- Execution status: agent-docs/CURRENT_IMPLEMENTATION.md. Verify Java compatibility before rollout.
-- Source: paytech/channel-connector/docs/database/channel-connector.pdma.json
SET NAMES utf8mb4;

CREATE TABLE `t_channel` (
  `channel_id` BIGINT NOT NULL COMMENT '渠道的内部唯一标识；支付渠道的技术账号、MID、成本方案与 Payment 冻结的执行坐标均引用它。',
  `channel_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '平台稳定识别渠道的唯一业务代码，大小写敏感，格式为大写字母开头的大写字母、数字与下划线，2 至 64 字符，创建后不可修改；也是外部 API 管理接口中的接入方编码。取值来源：渠道目录；非固定枚举。',
  `channel_name` VARCHAR(255) NOT NULL COMMENT '供运营配置和审阅展示的渠道正式名称。',
  `channel_type` VARCHAR(16) NOT NULL COMMENT '渠道类型，创建后不可修改；PAYMENT 有绑定 OE 的技术账号、MID、能力与成本，只经支付入口执行；SERVICE 最多一个不绑定 OE 的 ACTIVE 账号保存凭据，只经通用执行入口并按调用授权执行。允许值：PAYMENT、SERVICE。',
  `channel_status` VARCHAR(64) NOT NULL COMMENT '渠道从草稿、激活、暂停到关闭的当前运行状态；激活要求已配置出站地址。允许值：DRAFT、ACTIVE、SUSPENDED、CLOSED。',
  `description` VARCHAR(512) NULL COMMENT '渠道业务用途说明；未填写时为空，禁止填写凭据。',
  `endpoints` JSON NULL COMMENT '本环境出站地址数组 [{endpointCode, baseUrl, sandbox}]，1 至 16 项，未配置时为空；endpointCode 在渠道内唯一，baseUrl 的协议受部署允许列表限制（默认仅 https），不含用户信息、查询串与片段，最长 512 字符；保存时拦截回环、私网、链路本地、保留与组播 IP 字面量及 localhost，执行时解析主机名后再次拦截，部署配置的 trusted-networks 网段放行；sandbox 可选、缺省为 false，只有为 true 的地址允许沙箱验证运行。',
  `version` INT NOT NULL COMMENT '名称、说明或出站地址保存时按版本条件更新并递增的并发版本，初始为 1；不是发布版本。',
  `created_by` BIGINT NULL COMMENT '创建该渠道的 Operations 账号标识；2026-10-22 合表前创建且无接入方记录的渠道为空。',
  `updated_by` BIGINT NULL COMMENT '最后一次修改该渠道的 Operations 账号标识；2026-10-22 合表前创建且未再修改、无接入方记录的渠道为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`channel_id`),
  UNIQUE KEY `uk_channel_code` (`channel_code`) COMMENT '唯一索引：channel_code',
  KEY `idx_channel_type_status` (`channel_type`, `channel_status`) COMMENT '访问索引：channel_type、channel_status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='渠道（支付渠道与服务渠道）的稳定身份、类型、运行状态与本环境出站地址；支付渠道的技术账号、MID 与成本挂在其下。';

CREATE TABLE `t_channel_account` (
  `channel_account_id` BIGINT NOT NULL COMMENT 'Payment 管理的服务商技术认证账户或租户内部唯一标识。',
  `channel_id` BIGINT NOT NULL COMMENT '渠道内部唯一标识（本库 t_channel.channel_id）。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '账号所属 Operating Entity 的不可变 CHAR(5) 编号，来源于 CCT；支付渠道账号必填，且与其当前绑定 MID 的 OE 一致，不作为路由规则；服务渠道账号必须为空（应用校验），每个服务渠道最多一个 ACTIVE 账号保存凭据。',
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
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='归属单个渠道的技术账号：支付渠道账号绑定一个 OE，服务渠道账号不绑定 OE 且每渠道最多一个 ACTIVE；账号参数及凭据只保存 Vault 引用。';

CREATE TABLE `t_channel_mid` (
  `mid_id` BIGINT NOT NULL COMMENT 'Payment 管理且唯一归属于一个 Operating Entity 的服务商商户号标识。',
  `channel_id` BIGINT NOT NULL COMMENT '渠道内部唯一标识（本库 t_channel.channel_id）。',
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

CREATE TABLE `t_channel_cost_plan` (
  `id` BIGINT NOT NULL COMMENT '服务侧生成的渠道成本方案 Snowflake 主键。',
  `channel_id` BIGINT NOT NULL COMMENT '支付渠道（本库 t_channel.channel_id，channel_type 为 PAYMENT）。',
  `plan_code` VARCHAR(64) NOT NULL COMMENT '跨版本稳定识别渠道成本方案的业务代码。取值来源：channel-connector 渠道成本方案代码；非固定枚举。',
  `current_ver_id` BIGINT NULL COMMENT '当前发布的本库渠道成本方案版本。',
  `status` VARCHAR(64) NOT NULL COMMENT '渠道成本方案稳定身份的当前生命周期状态。允许值：DRAFT、ACTIVE、INACTIVE。',
  `version` BIGINT NOT NULL DEFAULT 0 COMMENT '切换当前成本版本和状态时使用的乐观锁版本号。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_cost_plan` (`channel_id`) COMMENT '保证业务唯一性：channel_id',
  UNIQUE KEY `uk_channel_cost_code` (`plan_code`) COMMENT '保证业务唯一性：plan_code',
  UNIQUE KEY `uk_channel_cost_current` (`current_ver_id`) COMMENT '保证业务唯一性：current_ver_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存每个 Payment Channel 的预计渠道成本规则稳定身份和当前版本指针；billing 经命中接口取组件并冻结证据，本库不算金额。';

CREATE TABLE `t_channel_cost_plan_ver` (
  `id` BIGINT NOT NULL COMMENT '服务侧生成的渠道成本方案版本 Snowflake 主键。',
  `plan_id` BIGINT NOT NULL COMMENT '该版本所属的本库渠道成本方案稳定身份。',
  `cost_ver` INT NOT NULL COMMENT '同一渠道成本方案下严格递增的业务版本号。',
  `effective_from` DATETIME(6) NOT NULL COMMENT '该成本版本开始参与预计成本匹配的 UTC 微秒时间。',
  `effective_to` DATETIME(6) NULL COMMENT '该成本版本停止参与预计成本匹配的 UTC 微秒时间，长期有效时为空。',
  `content_hash` CHAR(64) NOT NULL COMMENT '覆盖版本、全部适用范围和成本组件的不可变内容摘要。',
  `status` VARCHAR(64) NOT NULL COMMENT '该成本版本写入时冻结的审批和发布状态。允许值：DRAFT、PENDING_APPROVAL、APPROVED、ACTIVE、REJECTED、INACTIVE。',
  `maker_id` BIGINT NOT NULL COMMENT '创建该成本版本的 Platform Actor 稳定业务ID。',
  `checker_id` BIGINT NULL COMMENT '复核该成本版本的另一 Platform Actor 稳定业务ID。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_cost_ver` (`plan_id`, `cost_ver`) COMMENT '保证业务唯一性：plan_id、cost_ver'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存成本规则发布批次的生效期、内容摘要及双人审批状态，当前版本仅由方案根指针确定；APPROVED 版本到达生效时间后由到期切换 Job 原子切换为当前版本。';

CREATE TABLE `t_channel_cost_scope` (
  `id` BIGINT NOT NULL COMMENT '服务侧生成的渠道成本适用范围 Snowflake 主键。',
  `cost_ver_id` BIGINT NOT NULL COMMENT '该适用范围所属的本库渠道成本方案版本。',
  `scope_no` INT NOT NULL COMMENT '同一成本版本内稳定且唯一的适用范围序号。',
  `mid_id` BIGINT NOT NULL COMMENT 'Payment Channel MID（本库 t_channel_mid.id）；写入时校验存在、归属方案的 Channel 且 entity_no 一致。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '该成本适用范围限定的 Operating Entity 稳定业务ID，须等于 MID 归属的 OE。',
  `channel_agreement_ref` VARCHAR(128) NOT NULL COMMENT '本 MID 与经营主体范围实际采用的 Channel 合同或费率文件稳定引用。',
  `country_code` CHAR(2) NOT NULL COMMENT '该成本适用范围限定的 ISO 3166-1 alpha-2 国家代码。取值来源：ISO 3166-1 alpha-2 国家代码；非固定枚举。',
  `currency` CHAR(3) NOT NULL COMMENT '该成本适用范围限定的 ISO 4217 三位币种代码，也是成本金额的币种。取值来源：ISO 4217 币种代码；非固定枚举。',
  `payment_method_code` VARCHAR(64) NOT NULL COMMENT '该成本适用范围限定的 Payment 支付方式目录代码。取值来源：Payment 支付方式目录；非固定枚举。',
  `action_type` VARCHAR(64) NOT NULL COMMENT '该成本适用范围限定的支付、退款或争议动作类别。取值来源：Payment 动作类型目录；非固定枚举。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_cost_scope_no` (`cost_ver_id`, `scope_no`) COMMENT '保证业务唯一性：cost_ver_id、scope_no',
  UNIQUE KEY `uk_channel_cost_scope` (`cost_ver_id`, `mid_id`, `entity_no`, `country_code`, `currency`, `payment_method_code`, `action_type`) COMMENT '保证业务唯一性：cost_ver_id、mid_id、entity_no、country_code、currency、payment_method_code、action_type',
  KEY `idx_channel_cost_match` (`mid_id`, `entity_no`, `country_code`, `currency`) COMMENT '支持访问路径：mid_id、entity_no、country_code、currency'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='冻结成本版本对 MID、经营主体、国家、币种、支付方式和动作的确定性匹配范围。';

CREATE TABLE `t_channel_cost_component` (
  `id` BIGINT NOT NULL COMMENT '服务侧生成的渠道成本组件 Snowflake 主键。',
  `scope_id` BIGINT NOT NULL COMMENT '该成本组件所属的本库渠道成本适用范围。',
  `component_code` VARCHAR(64) NOT NULL COMMENT '同一适用范围内稳定识别成本组成的业务代码。取值来源：channel-connector 渠道成本组件代码；非固定枚举。',
  `component_type` VARCHAR(64) NOT NULL COMMENT '说明该组件采用的渠道收费语义。允许值：PROCESSING_FEE、CROSS_BORDER_FEE、REFUND_FEE、DISPUTE_FEE。',
  `calculation_base` VARCHAR(64) NOT NULL COMMENT '该成本组件声明的金额计算基数语义。允许值：TRANSACTION_AMOUNT。',
  `rate_value` DECIMAL(18,10) NULL COMMENT '零至一之间的渠道成本十进制费率。',
  `fixed_amount` DECIMAL(24,8) NULL COMMENT '渠道成本规则声明的非负固定收费金额。',
  `currency` CHAR(3) NULL COMMENT '固定金额使用的 ISO 4217 三位币种代码，须等于范围币种。取值来源：ISO 4217 币种代码；非固定枚举。',
  `min_amount` DECIMAL(24,8) NULL COMMENT '该成本组件计算结果允许采用的金额下限。',
  `max_amount` DECIMAL(24,8) NULL COMMENT '该成本组件计算结果允许采用的金额上限。',
  `rounding_mode` VARCHAR(64) NOT NULL COMMENT '把渠道成本原始金额归整到币种精度时采用的固定方式。允许值：HALF_UP。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_cost_component` (`scope_id`, `component_code`) COMMENT '保证业务唯一性：scope_id、component_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='冻结渠道成本适用范围下的百分比、固定费、跨境费、退款费或争议费参数；金额由 billing 按证据计算。';

CREATE TABLE `t_channel_config_change` (
  `id` BIGINT NOT NULL COMMENT '服务侧生成的变更记录 Snowflake 主键。',
  `target_type` VARCHAR(64) NOT NULL COMMENT '被变更对象类型；渠道出站地址变更记为 CHANNEL 与 CONFIGURATION。允许值：CHANNEL、CHANNEL_ACCOUNT、CHANNEL_MID、MID_CAPABILITY、CHANNEL_COST_VERSION、STANDARD_API、CHANNEL_API、CHANNEL_SCRIPT、CHANNEL_IMPLEMENTATION、VERIFY_ACCOUNT、VERIFY_CASE、CHANNEL_CALL_GRANT。',
  `target_id` BIGINT NOT NULL COMMENT '被变更对象在本库的主键。',
  `change_type` VARCHAR(64) NOT NULL COMMENT '变更类型。允许值：CREATE、UPDATE、STATUS、CONFIGURATION、REBIND、CANCEL、DELETE。',
  `from_status` VARCHAR(64) NULL COMMENT '状态变更前的状态；非状态变更为空。取值来源：渠道、账号、MID、能力组、成本版本或调用授权的状态代码；非固定枚举。',
  `to_status` VARCHAR(64) NULL COMMENT '状态变更后的状态；非状态变更为空。取值来源：渠道、账号、MID、能力组、成本版本或调用授权的状态代码；非固定枚举。',
  `operator_id` BIGINT NOT NULL COMMENT '执行变更的 Platform Actor 稳定业务 ID。',
  `reason` VARCHAR(512) NULL COMMENT '操作者填写的非敏感原因；禁止填写凭据。',
  `change_detail` JSON NULL COMMENT '外部 API 配置与渠道出站地址变更的字段摘要：{objectCode, beforeVersion, afterVersion, changes[{field, before, after}]}，字段取值只记名称、编码与 SHA-256 摘要，不含定义原文、参数或凭据值；渠道状态、账号、MID、能力、成本版本与调用授权的变更为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`id`),
  KEY `idx_channel_config_change_target` (`target_type`, `target_id`, `id`) COMMENT '支持访问路径：target_type、target_id、id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='渠道配置、成本版本、外部 API 配置与调用授权的人工变更留痕；与业务变更同事务写入，只增不改。';

CREATE TABLE `t_standard_api` (
  `standard_api_id` BIGINT NOT NULL COMMENT '标准 API 的内部技术标识。',
  `standard_api_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务系统调用渠道执行入口时使用的标准 API 编码，大小写敏感，格式为大写字母开头的字母、数字与下划线。取值来源：channel-connector 标准 API 目录；非固定枚举。',
  `standard_api_name` VARCHAR(128) NOT NULL COMMENT '标准 API 名称，供 Operations 维护页面展示。',
  `description` VARCHAR(512) NULL COMMENT '业务用途说明；未填写时为空。',
  `version` INT NOT NULL COMMENT 'Operations 保存时按版本条件更新的并发版本，初始为 1；不是发布版本。',
  `created_by` BIGINT NOT NULL COMMENT '创建该配置的 Operations 账号标识。',
  `updated_by` BIGINT NOT NULL COMMENT '最后一次修改该配置的 Operations 账号标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`standard_api_id`),
  UNIQUE KEY `uk_standard_api_code` (`standard_api_code`) COMMENT '保证业务唯一性：standard_api_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='渠道对内提供的标准 API 定义，例如 PAY、AUTHORIZE、GEOCODE；请求与响应字段由 t_api_field 维护，服务渠道的调用方授权见 t_channel_call_grant';

CREATE TABLE `t_api_field` (
  `api_field_id` BIGINT NOT NULL COMMENT '字段定义的内部技术标识。',
  `api_id` BIGINT NOT NULL COMMENT '所属 API 标识：方向为 STANDARD_API_* 时指向 t_standard_api.standard_api_id，为 INTEGRATION_API_* 时指向 t_channel_api.channel_api_id；由应用校验。',
  `direction` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '字段所属的 API 类型与请求或响应方向。允许值：STANDARD_API_REQUEST、STANDARD_API_RESPONSE、INTEGRATION_API_REQUEST、INTEGRATION_API_RESPONSE。',
  `field_location` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '字段在 HTTP 报文中的位置；请求允许四种，响应只允许 HEADER、BODY。允许值：HEADER、BODY、QUERY、PATH。',
  `field_path` VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'BODY 字段的完整点分路径，数组元素用 [] 表示，最多 16 段、每段 1 至 64 字符；HEADER、QUERY、PATH 为参数名，HEADER 名称大小写不敏感判重由应用完成。',
  `field_name` VARCHAR(128) NOT NULL COMMENT '字段在 Operations 页面上的中文或业务显示名称。',
  `data_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '字段值的数据类型；金额与精确小数以 STRING 传递。允许值：STRING、BOOLEAN、INTEGER、DECIMAL、OBJECT、ARRAY。',
  `required_flag` BOOLEAN NOT NULL COMMENT '1 表示必填，0 表示非必填；子字段只在父对象存在时校验，响应字段只在执行状态为 SUCCESS 时校验。',
  `sort_no` INT NOT NULL COMMENT '同一 API、同一方向内的显示顺序，从小到大。',
  `description` VARCHAR(512) NULL COMMENT '业务用途说明；未填写时为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`api_field_id`),
  UNIQUE KEY `uk_api_field_path` (`api_id`, `direction`, `field_location`, `field_path`) COMMENT '保证业务唯一性：api_id、direction、field_location、field_path'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='标准 API 与渠道原子 API 共用的请求、响应字段定义；属于所属 API 的配置，随所属 API 整组替换，不单独记录版本与操作人';

CREATE TABLE `t_channel_api` (
  `channel_api_id` BIGINT NOT NULL COMMENT '渠道原子 API 的内部技术标识。',
  `channel_id` BIGINT NOT NULL COMMENT '所属渠道标识（t_channel.channel_id）。',
  `api_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渠道内唯一的原子 API 编码，流程节点按此引用，大小写敏感。取值来源：所属渠道的原子 API 目录；非固定枚举。',
  `api_name` VARCHAR(128) NOT NULL COMMENT 'API 名称，供 Operations 维护页面展示。',
  `description` VARCHAR(512) NULL COMMENT '业务用途说明；未填写时为空。',
  `definition_json` JSON NOT NULL COMMENT '原子 API 执行定义：调用参数、协议固定值、传输、编解码、脚本引用、状态规则与按 SUCCESS/FAIL 分支的输出映射；序列化后最多 65536 UTF-8 字节，禁止保存凭据明文。',
  `version` INT NOT NULL COMMENT 'Operations 保存时按版本条件更新的并发版本，初始为 1；不是发布版本。',
  `created_by` BIGINT NOT NULL COMMENT '创建该配置的 Operations 账号标识。',
  `updated_by` BIGINT NOT NULL COMMENT '最后一次修改该配置的 Operations 账号标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`channel_api_id`),
  UNIQUE KEY `uk_channel_api_code` (`channel_id`, `api_code`) COMMENT '保证业务唯一性：channel_id、api_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='渠道的单次外部协议操作；请求与响应结构由 t_api_field 维护，传输、编解码、脚本、状态规则与输出映射保存在 definition_json';

CREATE TABLE `t_channel_script` (
  `channel_script_id` BIGINT NOT NULL COMMENT '公共脚本的内部技术标识。',
  `channel_id` BIGINT NOT NULL COMMENT '所属渠道标识（t_channel.channel_id）。',
  `script_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渠道内唯一的脚本编码，原子 API 按此引用，大小写敏感。取值来源：所属渠道的公共脚本目录；非固定枚举。',
  `script_name` VARCHAR(128) NOT NULL COMMENT '脚本名称，供 Operations 维护页面展示。',
  `execution_stage` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '脚本运行的 HTTP 处理阶段；REQUEST 读写请求工作对象，RESPONSE 读写响应工作副本。允许值：REQUEST、RESPONSE。',
  `parameter_definition` JSON NOT NULL COMMENT '脚本参数声明数组 [{name, dataType, required}]，最多 32 项，无参数时为空数组。',
  `source_code` TEXT NOT NULL COMMENT '受限 Aviator 脚本源码，最多 32 KiB UTF-8；引擎固定，不另设引擎字段。',
  `description` VARCHAR(512) NULL COMMENT '业务用途说明；未填写时为空。',
  `version` INT NOT NULL COMMENT 'Operations 保存时按版本条件更新的并发版本，初始为 1；不是发布版本。',
  `created_by` BIGINT NOT NULL COMMENT '创建该配置的 Operations 账号标识。',
  `updated_by` BIGINT NOT NULL COMMENT '最后一次修改该配置的 Operations 账号标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`channel_script_id`),
  UNIQUE KEY `uk_channel_script_code` (`channel_id`, `script_code`) COMMENT '保证业务唯一性：channel_id、script_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='渠道内可复用的请求或响应阶段脚本；复杂签名、加密由代码注册函数提供，脚本不读取凭据明文';

CREATE TABLE `t_channel_standard_api` (
  `channel_standard_api_id` BIGINT NOT NULL COMMENT '渠道标准 API 实现的内部技术标识。',
  `channel_id` BIGINT NOT NULL COMMENT '实现该标准 API 的渠道标识（t_channel.channel_id）。',
  `standard_api_id` BIGINT NOT NULL COMMENT '被实现的标准 API 标识。',
  `flow_definition_json` JSON NOT NULL COMMENT '流程定义：总预算、初始化项、入口、原子 API 节点、返回出口与条件连线；无环，序列化后最多 65536 UTF-8 字节。',
  `version` INT NOT NULL COMMENT 'Operations 保存时按版本条件更新的并发版本，初始为 1；不是发布版本。',
  `created_by` BIGINT NOT NULL COMMENT '创建该配置的 Operations 账号标识。',
  `updated_by` BIGINT NOT NULL COMMENT '最后一次修改该配置的 Operations 账号标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`channel_standard_api_id`),
  UNIQUE KEY `uk_channel_standard_api` (`channel_id`, `standard_api_id`) COMMENT '保证业务唯一性：channel_id、standard_api_id',
  KEY `idx_channel_std_api_standard` (`standard_api_id`) COMMENT '支持访问路径：standard_api_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='渠道对某个标准 API 的当前实现；flow_definition_json 保存初始化、原子 API 节点、条件连线与返回出口，不保存流程实例或运行状态';

CREATE TABLE `t_channel_verify_account` (
  `verify_account_id` BIGINT NOT NULL COMMENT '验证账号的内部技术标识。',
  `channel_id` BIGINT NOT NULL COMMENT '所属渠道标识（t_channel.channel_id）。',
  `account_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渠道内唯一的验证账号编码，验证用例按此引用，大小写敏感。取值来源：所属渠道的验证账号目录；非固定枚举。',
  `account_name` VARCHAR(128) NOT NULL COMMENT '账号名称，供 Operations 维护页面展示。',
  `channel_code` VARCHAR(64) NOT NULL COMMENT '账号所属渠道编码，流程初始化时与输入中的账号身份核对。',
  `channel_account_id` VARCHAR(64) NOT NULL COMMENT '流程初始化时与输入中的账号身份核对的渠道账号标识。',
  `entity_no` VARCHAR(64) NOT NULL COMMENT '流程初始化时与输入中的账号身份核对的经营主体编号。',
  `parameters` JSON NOT NULL COMMENT '普通账号参数，字符串键值，最多 64 项、每个值最多 8192 字符。',
  `credentials` JSON NOT NULL COMMENT '沙箱凭据，字符串键值，最多 64 项、每个值最多 8192 字符；执行时包装为受控句柄，只能交给注册函数。',
  `version` INT NOT NULL COMMENT 'Operations 保存时按版本条件更新的并发版本，初始为 1；不是发布版本。',
  `created_by` BIGINT NOT NULL COMMENT '创建该配置的 Operations 账号标识。',
  `updated_by` BIGINT NOT NULL COMMENT '最后一次修改该配置的 Operations 账号标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`verify_account_id`),
  UNIQUE KEY `uk_verify_account_code` (`channel_id`, `account_code`) COMMENT '保证业务唯一性：channel_id、account_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='沙箱验证运行使用的渠道账号材料；按用户决定以明文保存非敏感沙箱材料，不写入 Vault，正式执行不读取';

CREATE TABLE `t_channel_verify_case` (
  `verify_case_id` BIGINT NOT NULL COMMENT '验证用例的内部技术标识。',
  `channel_id` BIGINT NOT NULL COMMENT '所属渠道标识（t_channel.channel_id）。',
  `case_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渠道内唯一的验证用例编码，大小写敏感。取值来源：所属渠道的验证用例目录；非固定枚举。',
  `case_name` VARCHAR(128) NOT NULL COMMENT '用例名称，供 Operations 维护页面展示。',
  `description` VARCHAR(512) NULL COMMENT '业务用途说明；未填写时为空。',
  `verify_account_id` BIGINT NOT NULL COMMENT '运行时使用的验证账号标识。',
  `steps_json` JSON NOT NULL COMMENT '有序步骤数组，1 至 10 步；每步含步骤标识、标准 API 编码、输入模板与期望，序列化后最多 65536 UTF-8 字节。',
  `version` INT NOT NULL COMMENT 'Operations 保存时按版本条件更新的并发版本，初始为 1；不是发布版本。',
  `created_by` BIGINT NOT NULL COMMENT '创建该配置的 Operations 账号标识。',
  `updated_by` BIGINT NOT NULL COMMENT '最后一次修改该配置的 Operations 账号标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`verify_case_id`),
  UNIQUE KEY `uk_verify_case_code` (`channel_id`, `case_code`) COMMENT '保证业务唯一性：channel_id、case_code',
  KEY `idx_verify_case_account` (`verify_account_id`) COMMENT '支持访问路径：verify_account_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='渠道的沙箱验证用例：使用的验证账号与有序步骤，每步包含标准 API、输入模板与期望';

CREATE TABLE `t_channel_verify_run` (
  `verify_run_id` BIGINT NOT NULL COMMENT '验证运行的内部技术标识，以十进制字符串作为对外运行编号。',
  `verify_case_id` BIGINT NOT NULL COMMENT '被运行的验证用例标识；用例删除后保留原值。',
  `channel_id` BIGINT NOT NULL COMMENT '所属渠道标识（t_channel.channel_id）。',
  `run_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '运行结论；FAILED 表示期望不满足或步骤出错，INTERRUPTED 表示执行进程中断后不再向沙箱重放。允许值：RUNNING、PASSED、FAILED、INTERRUPTED。',
  `case_snapshot_json` JSON NOT NULL COMMENT '发起运行时的用例步骤、期望、账号编码与账号版本，不含凭据。',
  `config_versions_json` JSON NULL COMMENT '运行实际加载的标准 API、渠道、原子 API、脚本与实现版本，按编码记录；运行未加载配置时为空。',
  `endpoints_json` JSON NULL COMMENT '运行时渠道出站地址及沙箱标记；运行未加载配置时为空。',
  `failure_summary` VARCHAR(512) NULL COMMENT '未通过时的首个原因；通过或运行中为空。',
  `started_by` BIGINT NOT NULL COMMENT '发起运行的 Operations 账号标识。',
  `start_time` DATETIME(6) NOT NULL COMMENT '发起运行的 UTC 微秒时间。',
  `finish_time` DATETIME(6) NULL COMMENT '运行得出结论的 UTC 微秒时间；运行中为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`verify_run_id`),
  KEY `idx_verify_run_case` (`verify_case_id`, `start_time`) COMMENT '支持访问路径：verify_case_id、start_time',
  KEY `idx_verify_run_channel` (`channel_id`, `start_time`) COMMENT '支持访问路径：channel_id、start_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='一次沙箱验证运行的结论与证据：运行时的用例快照、配置版本与出站地址；记录不删除，用例删除后仍保留';

CREATE TABLE `t_channel_verify_run_step` (
  `verify_run_step_id` BIGINT NOT NULL COMMENT '运行步骤记录的内部技术标识。',
  `verify_run_id` BIGINT NOT NULL COMMENT '所属验证运行标识。',
  `step_no` INT NOT NULL COMMENT '步骤在用例中的顺序，从 1 开始。',
  `step_key` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用例内的步骤标识，供后续步骤模板引用。',
  `standard_api_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '该步骤执行的标准 API 编码。',
  `step_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '步骤结论；SKIPPED 表示前序步骤未通过而未执行。允许值：PASSED、FAILED、SKIPPED。',
  `resolved_input_json` JSON NULL COMMENT '模板渲染后的标准 API 输入；未执行或渲染失败时为空。',
  `result_json` JSON NULL COMMENT '执行结果：已执行时为执行状态与输出，执行前返回问题时为 HTTP 状态与错误码；未执行时为空。',
  `assertions_json` JSON NULL COMMENT '每条期望的实际值与是否满足；未执行时为空。',
  `trace_json` JSON NULL COMMENT '初始化项、节点、连线与出口轨迹及脱敏后的渠道请求与响应原文，单段报文最多保留 65536 字节；未执行时为空。',
  `elapsed_ms` INT NULL COMMENT '步骤执行耗时，单位毫秒；未执行时为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`verify_run_step_id`),
  UNIQUE KEY `uk_verify_run_step` (`verify_run_id`, `step_no`) COMMENT '保证业务唯一性：verify_run_id、step_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='验证运行中一个步骤的实际输入、结果、期望核对与脱敏轨迹；运行结束时一次写入，不再修改';

CREATE TABLE `t_channel_call_grant` (
  `call_grant_id` BIGINT NOT NULL COMMENT '调用授权的内部技术标识。',
  `caller_service` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '调用方服务名（调用方的 spring.application.name），小写字母开头，小写字母、数字或连字符，2 至 64 字符；只校验格式，不维护服务名册。取值来源：内部服务名；非固定枚举。',
  `channel_id` BIGINT NOT NULL COMMENT '被授权调用的服务渠道标识（t_channel.channel_id，channel_type 为 SERVICE）。',
  `standard_api_id` BIGINT NOT NULL COMMENT '被授权调用的标准 API 标识（t_standard_api.standard_api_id），授权时须已被该渠道实现；由应用校验。',
  `grant_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '授权当前是否放行；DISABLED 可再启用。允许值：ACTIVE、DISABLED。',
  `version` INT NOT NULL COMMENT '启用、停用时按版本条件更新的并发版本，初始为 1。',
  `created_by` BIGINT NOT NULL COMMENT '创建该授权的 Operations 账号标识。',
  `updated_by` BIGINT NOT NULL COMMENT '最后一次修改该授权的 Operations 账号标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`call_grant_id`),
  UNIQUE KEY `uk_channel_call_grant` (`caller_service`, `channel_id`, `standard_api_id`) COMMENT '保证业务唯一性：caller_service、channel_id、standard_api_id',
  KEY `idx_channel_call_grant_channel` (`channel_id`, `grant_status`) COMMENT '支持访问路径：channel_id、grant_status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='服务渠道的调用授权：调用方服务 × 渠道 × 标准 API 白名单，只有 ACTIVE 授权放行通用执行入口；授权只停用、不删除，同一组合只有一行，再启用沿用该行并递增版本';
