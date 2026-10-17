-- 仅补齐 127.0.0.1:13306/mor_commerce 缺失表，来源 docs/database/sql/commerce-schema.sql。
-- 不修改现有表、字段或数据；请人工审核后执行。
USE `mor_commerce`;

CREATE TABLE `t_order_legal` (
  `order_legal_id` BIGINT NOT NULL COMMENT '订单不可变上下文对应法律呈现记录的内部唯一标识。',
  `order_context_id` BIGINT NOT NULL COMMENT '订单创建时冻结外部商业、风控和税务证据引用的内部唯一标识。',
  `client_agreement_id` BIGINT NOT NULL COMMENT 'CCT准确Client/OE合同，替代新流程的Client Agreement-OE关联。',
  `partner_agreement_id` BIGINT NULL COMMENT 'PARTNER来源时固定的CCT合同，仍允许该合同覆盖多个OE。',
  `partner_agreement_operating_entity_id` BIGINT NULL COMMENT '存在 Local Partner 时，CCT 中唯一定位 Partner 合同与订单 Operating Entity 关联记录的稳定标识。',
  `consumer_terms_document_id` BIGINT NOT NULL COMMENT '订单法律上下文冻结的消费者条款业务文档标识。',
  `consumer_terms_file_id` BIGINT NOT NULL COMMENT '订单法律上下文冻结的消费者条款不可变文件标识。',
  `consumer_terms_content_hash` CHAR(64) NOT NULL COMMENT '订单法律上下文冻结的消费者条款文件内容哈希。',
  `refund_policy_document_id` BIGINT NOT NULL COMMENT '订单法律上下文冻结的退款政策业务文档标识。',
  `refund_policy_file_id` BIGINT NOT NULL COMMENT '订单法律上下文冻结的退款政策不可变文件标识。',
  `refund_policy_content_hash` CHAR(64) NOT NULL COMMENT '订单法律上下文冻结的退款政策文件内容哈希。',
  `privacy_notice_document_id` BIGINT NOT NULL COMMENT '订单法律上下文冻结的隐私声明业务文档标识。',
  `privacy_notice_file_id` BIGINT NOT NULL COMMENT '订单法律上下文冻结的隐私声明不可变文件标识。',
  `privacy_notice_content_hash` CHAR(64) NOT NULL COMMENT '订单法律上下文冻结的隐私声明文件内容哈希。',
  `seller_disclosure_id` BIGINT NOT NULL COMMENT '按订单 consumer_country_code 与 locale 精确命中并冻结的 CCT 卖方公开披露标识，禁止隐式 Locale 回退。',
  `public_legal_name` VARCHAR(255) NOT NULL COMMENT '订单法律卖方对消费者展示的完整法律名称。',
  `public_registration_number` VARCHAR(128) NOT NULL COMMENT '订单法律卖方对消费者展示的机构注册编号。',
  `public_tax_registration_number` VARCHAR(64) NULL COMMENT '订单法律卖方对消费者展示的可选税务登记编号。',
  `public_address_text` VARCHAR(512) NOT NULL COMMENT '订单法律卖方对消费者展示的注册地址或营业地址。',
  `public_support_email` VARCHAR(254) NOT NULL COMMENT '订单法律卖方对消费者展示的客户支持邮箱。',
  `public_support_phone` VARCHAR(32) NULL COMMENT '订单法律卖方对消费者展示的可选客户支持电话。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  PRIMARY KEY (`order_legal_id`),
  UNIQUE KEY `uk_order_legal` (`order_context_id`) COMMENT '唯一索引：order_context_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='固定Client/OE合同、可选Partner合同关联和三份独立法律文件；Client不再新写Agreement-OE关联，PARTNER成组引用保持。';

CREATE TABLE `t_consumer_context` (
  `consumer_context_id` BIGINT NOT NULL COMMENT '订单当前消费者受保护上下文的内部唯一标识。',
  `order_id` BIGINT NOT NULL COMMENT 'Commerce 订单聚合的内部唯一标识。',
  `consumer_reference` VARCHAR(128) NULL COMMENT 'Client 命名空间内可选的非 PII、不透明消费者业务引用；当前 Customer 无此输入时为空，不生成假值；不得提交 Email、Phone、证件号等个人原值。',
  `email_vault_token` VARCHAR(128) NOT NULL COMMENT '指向 Vault 中消费者 Email 原值的稳定不可枚举 Token。',
  `phone_vault_token` VARCHAR(128) NULL COMMENT '指向 Vault 中消费者 Phone 原值的可选稳定不可枚举 Token。',
  `consumer_profile_vault_token` VARCHAR(128) NOT NULL COMMENT '指向 Vault 中姓名与完整账单地址等消费者资料的稳定不可枚举 Token。',
  `email_domain` VARCHAR(255) NOT NULL COMMENT '消费者邮箱规范化后的域名分析维度，不包含邮箱本地部分。',
  `phone_country_code` VARCHAR(8) NULL COMMENT '消费者电话号码规范化后的国家或地区呼叫代码分析维度。',
  `billing_country_code` CHAR(2) NOT NULL COMMENT '消费者账单地址规范化后的 ISO 3166-1 alpha-2 国家代码。',
  `billing_region_code` VARCHAR(64) NULL COMMENT '消费者账单地址规范化后的州、省或行政区分析代码。',
  `billing_city` VARCHAR(128) NULL COMMENT '消费者账单地址规范化后的城市分析维度，不包含完整街道地址。',
  `billing_postal_prefix` VARCHAR(16) NULL COMMENT '消费者账单邮编按批准粒度截取的分析前缀，不保存完整邮编。',
  `version` BIGINT NOT NULL COMMENT '可变业务行并发更新使用的单调乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`consumer_context_id`),
  UNIQUE KEY `uk_consumer_context_order` (`order_id`) COMMENT '唯一索引：order_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='订单当前消费者上下文；consumer_reference 是 Client 命名空间内的非 PII、不透明业务引用，不得提交 Email、Phone、证件号等个人原值；Email、电话、姓名和完整账单地址仅以分用途 Vault Token 保存，Commerce 保留受控派生分析维度。';

CREATE TABLE `t_checkout_network_evidence` (
  `network_evidence_id` BIGINT NOT NULL COMMENT '一条不可变 Checkout 网络与设备证据的内部唯一标识。',
  `order_id` BIGINT NOT NULL COMMENT 'Commerce 订单聚合的内部唯一标识。',
  `checkout_session_id` BIGINT NOT NULL COMMENT 'Hosted Checkout 会话的内部唯一标识。',
  `evidence_scene` VARCHAR(64) NOT NULL COMMENT '网络证据产生于打开 Checkout、提交消费者资料、接受法律条款或开始支付的闭合场景。允许值：CHECKOUT_OPEN、CONSUMER_CONTEXT_SUBMIT、LEGAL_ACCEPTANCE、PAYMENT_START。',
  `request_id` VARCHAR(128) NOT NULL COMMENT 'Checkout 一次网络证据采集请求的稳定去重与追踪标识。',
  `client_ip_address` VARCHAR(45) NOT NULL COMMENT '可信入口解析并保存的完整客户端 IPv4 或 IPv6 地址。',
  `user_agent` VARCHAR(1024) NOT NULL COMMENT '可信入口采集的完整 User-Agent 原文，用于保留该次 Checkout 的网络证据。',
  `trusted_proxy_chain` VARCHAR(2048) NULL COMMENT '可信入口解析得到的可选代理链原文，仅记录已验证代理转发路径。',
  `ip_country_code` CHAR(2) NULL COMMENT '网络情报解析得到的 ISO 3166-1 alpha-2 国家代码。',
  `ip_region_code` VARCHAR(64) NULL COMMENT '网络情报解析得到的州、省或行政区分析代码。',
  `ip_city` VARCHAR(128) NULL COMMENT '网络情报解析得到的城市分析维度。',
  `ip_asn` BIGINT NULL COMMENT '网络情报解析得到的自治系统编号分析维度。',
  `network_type` VARCHAR(64) NULL COMMENT '网络情报目录返回的接入网络分类代码。取值来源：Network Type Catalog；非固定枚举。',
  `proxy_flag` TINYINT(1) NULL COMMENT '网络情报判断当前 IP 是否使用代理；1 表示是，0 表示否，NULL 表示未评估。',
  `vpn_flag` TINYINT(1) NULL COMMENT '网络情报判断当前 IP 是否使用 VPN；1 表示是，0 表示否，NULL 表示未评估。',
  `tor_flag` TINYINT(1) NULL COMMENT '网络情报判断当前 IP 是否属于 Tor 网络；1 表示是，0 表示否，NULL 表示未评估。',
  `hosting_flag` TINYINT(1) NULL COMMENT '网络情报判断当前 IP 是否属于托管网络；1 表示是，0 表示否，NULL 表示未评估。',
  `browser_family` VARCHAR(64) NULL COMMENT '由 User-Agent 派生且不包含完整原文的浏览器产品族。',
  `browser_major_version` VARCHAR(32) NULL COMMENT '由 User-Agent 派生的浏览器主版本分析维度。',
  `os_family` VARCHAR(64) NULL COMMENT '由 User-Agent 派生且不包含完整原文的操作系统产品族。',
  `os_major_version` VARCHAR(32) NULL COMMENT '由 User-Agent 派生的操作系统主版本分析维度。',
  `device_type` VARCHAR(64) NOT NULL COMMENT '由客户端特征派生的桌面端、移动端、平板、机器人、其他或未知闭合类型。允许值：DESKTOP、MOBILE、TABLET、BOT、OTHER、UNKNOWN。',
  `occurred_at` DATETIME(6) NOT NULL COMMENT '网络证据或预占变化事实发生的业务时刻。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  PRIMARY KEY (`network_evidence_id`),
  UNIQUE KEY `uk_network_evidence_request` (`checkout_session_id`, `request_id`, `evidence_scene`) COMMENT '唯一索引：checkout_session_id、request_id、evidence_scene',
  KEY `idx_network_order_time` (`order_id`, `occurred_at`) COMMENT '访问索引：order_id、occurred_at',
  KEY `idx_network_client_ip` (`client_ip_address`, `occurred_at`) COMMENT '访问索引：client_ip_address、occurred_at'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='不可变 Checkout 网络与设备证据；Commerce 直接保存可信解析的完整客户端 IP、完整 User-Agent 和可选可信代理链，以及批准的网络和设备派生维度；不保存 Vault Token、盲索引、密钥版本或设备标识。';

CREATE TABLE `t_consumer_acceptance` (
  `consumer_acceptance_id` BIGINT NOT NULL COMMENT '一条不可变消费者法律接受证据的内部唯一标识。',
  `consumer_contract_id` BIGINT NOT NULL COMMENT '订单呈现给消费者的不可变合同文件记录标识。',
  `network_evidence_id` BIGINT NOT NULL COMMENT '一条不可变 Checkout 网络与设备证据的内部唯一标识。',
  `acceptance_type` VARCHAR(64) NOT NULL COMMENT '本条证据对应的消费者条款、退款政策或隐私声明类型。允许值：CONSUMER_TERMS、REFUND_POLICY、PRIVACY_NOTICE。',
  `accepted_at` DATETIME(6) NOT NULL COMMENT '消费者完成该项法律接受动作的业务时刻。',
  `command_idempotency_id` BIGINT NULL COMMENT '仅历史请求幂等记录引用；新支付提交不写入，由合同和接受类型唯一键保护业务事实。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  PRIMARY KEY (`consumer_acceptance_id`),
  UNIQUE KEY `uk_consumer_acceptance` (`consumer_contract_id`, `acceptance_type`) COMMENT '唯一索引：consumer_contract_id、acceptance_type',
  KEY `idx_acceptance_network` (`network_evidence_id`) COMMENT '访问索引：network_evidence_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='消费者对特定法律文档类型的不可变接受证据；不维护 Acceptance Summary，Payment Readiness 依据三种必需接受类型的不可变记录实时判定。';
