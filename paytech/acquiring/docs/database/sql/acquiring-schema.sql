-- Code-MoR target schema generated from the approved PDManer model.
-- Apply manually to an EMPTY database only; existing environments require a reviewed migration.
-- Execution status: agent-docs/CURRENT_IMPLEMENTATION.md. Verify Java compatibility before rollout.
-- Source: paytech/acquiring/docs/database/acquiring.pdma.json
SET NAMES utf8mb4;

CREATE TABLE `t_commerce_order` (
  `order_id` BIGINT NOT NULL COMMENT 'Commerce 订单聚合的内部唯一标识。',
  `order_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '供接口响应、业务查询与人工操作使用的唯一订单编号。',
  `client_id` BIGINT NOT NULL COMMENT '订单或退款请求所属平台 Client 的内部唯一标识。',
  `client_order_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Client 在自身命名空间内提供且不可复用的稳定订单业务标识；不承担接口请求幂等职责。',
  `customer_id` BIGINT NULL COMMENT '建单时按 consumerReference 关联的 Customer；访客订单为 NULL，创建后不可修改。',
  `creation_mode` VARCHAR(32) NOT NULL DEFAULT 'HOSTED_CHECKOUT' COMMENT '服务端根据创建入口固定的订单接入方式，创建后不可修改；HOSTED_CHECKOUT为托管收银台，DIRECT_PAY为预留直接支付接入；已有订单及默认值为HOSTED_CHECKOUT。允许值：HOSTED_CHECKOUT、DIRECT_PAY。',
  `consumer_country_code` CHAR(2) NULL COMMENT '基础创建时固定的消费者 ISO 3166-1 alpha-2 国家代码；NULL 仅用于历史订单读取旧创建快照。',
  `termination_requested_at` DATETIME(6) NULL COMMENT '取消或到期首次请求的UTC时间，串行阻止新副作用；不表示远端未知Intent已终止。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'CCT唯一不可变OE编号，创建时经营国家二位代码加001–999，如AE001；不是数字代理ID，编号不参与路由。',
  `entity_source_type` VARCHAR(64) NULL COMMENT '订单经营主体来源于 Client 或 Local Partner 的闭合类型；平台自有 OE 经平台 PayFac Partner 以 PARTNER 来源提供。允许值：CLIENT、PARTNER。',
  `local_partner_id` BIGINT NULL COMMENT '当经营主体来源为 PARTNER 时对应的 Local Partner 标识。',
  `industry_code` VARCHAR(64) NULL COMMENT '订单上下文采用的平台行业目录代码。',
  `mcc_code` VARCHAR(4) NULL COMMENT '订单上下文采用的四位 Merchant Category Code。',
  `merchandise_amount` DECIMAL(24,8) NOT NULL COMMENT '订单创建时冻结的优惠前商品服务汇总金额。',
  `discount_amount` DECIMAL(24,8) NOT NULL COMMENT '订单创建时冻结的全部商品服务优惠汇总金额。',
  `net_amount` DECIMAL(24,8) NOT NULL COMMENT '订单创建时冻结的商品服务金额扣除优惠后的净金额。',
  `tax_amount` DECIMAL(24,8) NULL COMMENT '订单创建时冻结的消费者实收税额汇总金额。',
  `rounding_amount` DECIMAL(24,8) NULL COMMENT '订单金额计算为保持币种精度而冻结的舍入差额。',
  `total_amount` DECIMAL(24,8) NULL COMMENT '消费者最终应付的订单冻结总金额。',
  `currency` CHAR(3) NOT NULL COMMENT '金额采用的 ISO 4217 三位币种代码。',
  `order_status` VARCHAR(64) NOT NULL COMMENT '订单聚合当前的闭合业务生命周期状态。允许值：CREATED、OPEN、PAID、CANCELLED、EXPIRED。',
  `status_reason_code` VARCHAR(64) NULL COMMENT '当前终态原因采用的稳定业务代码，适用于取消、失败、过期等需要说明的结果；没有终态原因时为空。取值来源：Commerce Order Status Reason Catalog；非固定枚举。',
  `expires_at` DATETIME(6) NOT NULL COMMENT '订单允许继续 Checkout 或支付处理的业务截止时刻。',
  `paid_at` DATETIME(6) NULL COMMENT '订单确认支付成功并进入 PAID 的业务时刻。',
  `cancelled_at` DATETIME(6) NULL COMMENT '本订单或 Checkout 会话进入取消终态的 UTC 业务时刻，重复取消不改写。',
  `version` BIGINT NOT NULL COMMENT '可变业务行并发更新使用的单调乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `uk_order_no` (`order_no`) COMMENT '唯一索引：order_no',
  UNIQUE KEY `uk_order_client_order` (`client_id`, `client_order_id`) COMMENT '唯一索引：client_id、client_order_id',
  KEY `idx_order_client_time` (`client_id`, `create_time`, `order_id`) COMMENT '访问索引：client_id、create_time、order_id',
  KEY `idx_order_partner_time` (`local_partner_id`, `create_time`, `order_id`) COMMENT '访问索引：local_partner_id、create_time、order_id',
  KEY `idx_order_expiry` (`order_status`, `expires_at`, `order_id`) COMMENT '访问索引：order_status、expires_at、order_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='基础创建时固定商品净额/币种/期限；支付准备选择提交后固定OE，Context建立时一次补齐税额和最终金额；不延长到期或跨OE恢复。';

CREATE TABLE `t_order_context` (
  `order_context_id` BIGINT NOT NULL COMMENT '订单创建时冻结外部商业、风控和税务证据引用的内部唯一标识。',
  `order_id` BIGINT NOT NULL COMMENT 'Commerce 订单聚合的内部唯一标识。',
  `commercial_context_time` DATETIME(6) NOT NULL COMMENT 'CCT 解析经营主体和商业条款时使用并由订单上下文冻结的业务有效时点。',
  `affiliation_id` BIGINT NOT NULL COMMENT 'CCT批准的Client对整个OE授权关系，支付准备时冻结。',
  `entity_scope_id` BIGINT NOT NULL COMMENT 'CCT已批准的准确国家/币种/行业/MCC范围事实。',
  `entity_source_relation_id` BIGINT NOT NULL COMMENT 'CCT唯一有效OE来源关系，固定后不得换来源。',
  `client_pricing_version_id` BIGINT NOT NULL COMMENT '支付准备时由CCT固定的Client条款版本，后续准确Price只能从此版本解析。',
  `partner_share_version_id` BIGINT NULL COMMENT 'PARTNER来源时固定的CCT分成版本，与平台服务费版本成组非空。',
  `platform_charge_version_id` BIGINT NULL COMMENT 'PARTNER来源时固定的CCT服务费版本，与分成版本属于同一Partner合同。',
  `platform_service_party_id` BIGINT NULL COMMENT '支付准备从 Partner Agreement 唯一 PLATFORM_SIGNATORY 解析并冻结的法律 Party 稳定标识；Finance 后续映射本地 Finance Entity。',
  `risk_checkout_assessment_id` BIGINT NOT NULL COMMENT 'Risk & Compliance 返回且由订单上下文冻结的不可变评估标识。',
  `finance_tax_snapshot_id` BIGINT NOT NULL COMMENT 'Finance 对订单税务计算结果的不可变快照标识。',
  `finance_tax_snapshot_hash` CHAR(64) NOT NULL COMMENT 'Finance 税务快照 calculation_hash 的精确副本，按 CODE-MOR:FINANCE:TAX_CALCULATION:1 对 Header、全部 Evidence 和 Tax Line 生成 SHA-256 小写 64 位十六进制，用于校验按标识返回的不可变税务结果。',
  `consumer_country_code` CHAR(2) NOT NULL COMMENT '订单创建时冻结的消费者 ISO 3166-1 alpha-2 国家代码。',
  `locale` VARCHAR(35) NOT NULL COMMENT '文档或消费者体验采用的 IETF BCP 47 语言区域代码。',
  `fund_flow` VARCHAR(32) NULL COMMENT 'commercial 商业关系快照的资金流向；固定 Mock 解析时为 NULL。允许值：THROUGH_CUSTODIAN、DIRECT_TO_CLIENT。',
  `effective_relationship_model_version` INT NOT NULL COMMENT '订单冻结的商业关系生效模型版本：0 表示快照只记录、下游沿用旧字段，1 表示下游按快照生效；固定 Mock 解析及 2026-10-24 之前的存量订单为 0。',
  `snapshot_model_version` INT NULL COMMENT '记录的商业关系快照按哪一版推导规则产生；固定 Mock 解析时为 NULL。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  PRIMARY KEY (`order_context_id`),
  UNIQUE KEY `uk_order_context` (`order_id`) COMMENT '唯一索引：order_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='支付准备时一次建立的不可变上下文；外部事实只保存稳定引用；保存CCT批准Scope/Affiliation/Source及固定Term Version，准确Price由完整成功支付事实解析；三份接受证据及原始Context不得覆盖；PARTNER的分成版本/服务费版本/平台Party三项成组非空且Order的local_partner_id非空，非 PARTNER三项成组为空且local_partner_id为空；commercial 商业关系快照逐角色冻结在 t_order_relationship_binding，资金流向与两个模型版本记在本表，同一事务写入。';

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

CREATE TABLE `t_order_item` (
  `order_item_id` BIGINT NOT NULL COMMENT '订单内一条商品或服务明细的内部唯一标识。',
  `order_id` BIGINT NOT NULL COMMENT 'Commerce 订单聚合的内部唯一标识。',
  `client_item_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Client 在订单请求中提供且在订单内唯一的商品项标识。',
  `item_name` VARCHAR(255) NOT NULL COMMENT '订单商品或服务项目在创建时冻结的展示名称。',
  `item_description` VARCHAR(1024) NULL COMMENT '订单商品或服务项目在创建时冻结的可选详细说明。',
  `product_type_code` VARCHAR(64) NOT NULL COMMENT '订单商品服务所属产品类型目录代码。取值来源：Product Type Catalog；非固定枚举。',
  `tax_category_code` VARCHAR(64) NOT NULL COMMENT '订单商品服务在税务目录中的分类代码。取值来源：Tax Category Catalog；非固定枚举。',
  `quantity` DECIMAL(24,8) NOT NULL COMMENT '订单商品服务项目采用的冻结计量数量。',
  `unit_amount` DECIMAL(24,8) NOT NULL COMMENT '订单商品服务项目在创建时冻结的单位金额。',
  `line_gross_amount` DECIMAL(24,8) NOT NULL COMMENT '优惠和税务处理前的订单商品服务行金额。',
  `line_discount_amount` DECIMAL(24,8) NOT NULL COMMENT '订单商品服务行的冻结优惠金额。',
  `line_net_amount` DECIMAL(24,8) NOT NULL COMMENT '行毛金额扣除优惠后的冻结净金额。',
  `currency` CHAR(3) NOT NULL COMMENT '金额采用的 ISO 4217 三位币种代码。',
  `sort_no` INT NOT NULL COMMENT '保持订单商品服务项目原始呈现顺序的稳定序号。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  PRIMARY KEY (`order_item_id`),
  UNIQUE KEY `uk_order_item` (`order_id`, `client_item_id`) COMMENT '唯一索引：order_id、client_item_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='创建时固定不可变商品/优惠/净额；新流程最终逐行税额和总额由Finance不可变Tax Snapshot按client_item_id读取，不先写假税额再更新。';

CREATE TABLE `t_order_relationship_binding` (
  `binding_id` BIGINT NOT NULL COMMENT '订单商业关系中一个角色绑定的内部唯一标识。',
  `order_id` BIGINT NOT NULL COMMENT 'Commerce 订单聚合的内部唯一标识。',
  `role_code` VARCHAR(64) NOT NULL COMMENT 'commercial 商业关系快照中的角色，每个订单每个角色至多一行。允许值：CLIENT、SELLER、ACQUIRING_OE、OE_PROVIDER、FUNDS_CUSTODIAN、TAX_RESPONSIBLE、PLATFORM_FEE_PAYER、ACQUIRER_SUB_MERCHANT、REFERRER、SPLIT_PAYEE、PLATFORM。',
  `subject_type` VARCHAR(64) NOT NULL COMMENT '角色绑定的主体类型。允许值：CLIENT、OPERATING_ENTITY、LOCAL_PARTNER、PLATFORM。',
  `subject_id` VARCHAR(64) NULL COMMENT '主体标识：CLIENT 为 clientId，OPERATING_ENTITY 为 entityNo，LOCAL_PARTNER 为 localPartnerId；主体类型为 PLATFORM 时为 NULL。',
  `party_id` BIGINT NULL COMMENT '主体对应的 client-master 法律 Party 稳定标识；commercial 未提供时为 NULL，不建立跨库外键。',
  `snapshot_model_version` INT NOT NULL COMMENT '本行所属商业关系快照的推导规则版本，与订单上下文 snapshot_model_version 相同。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  PRIMARY KEY (`binding_id`),
  UNIQUE KEY `uk_order_relationship_role` (`order_id`, `role_code`) COMMENT '唯一索引：order_id、role_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='订单创建时冻结 commercial 返回的商业关系快照，每个角色一行，记录主体类型、主体标识与法律 Party；与订单上下文同一事务写入，之后不可变；固定 Mock 解析不写本表。';

CREATE TABLE `t_order_payment_projection` (
  `order_payment_projection_id` BIGINT NOT NULL COMMENT '订单唯一支付状态投影的内部标识。',
  `order_id` BIGINT NOT NULL COMMENT 'Commerce 订单聚合的内部唯一标识。',
  `payment_intent_id` BIGINT NOT NULL COMMENT 'Payment Orchestration 为订单创建的稳定支付意图标识。',
  `intent_status` VARCHAR(64) NOT NULL COMMENT 'Commerce 消费到的支付意图当前闭合状态。允许值：REQUIRES_PAYMENT、PROCESSING、REQUIRES_ACTION、SUCCEEDED、FAILED、EXPIRED、CANCELLED。',
  `intent_source_version` BIGINT NOT NULL COMMENT '支付意图投影已经消费的来源聚合版本。',
  `last_intent_event_key` VARCHAR(128) NULL COMMENT '支付投影最近处理的支付意图事件幂等键。',
  `last_intent_payload_hash` CHAR(64) NULL COMMENT '最近支付意图事件规范化载荷的完整性哈希。',
  `active_attempt_id` BIGINT NULL COMMENT '支付投影中当前仍可能继续推进的支付尝试标识。',
  `last_attempt_id` BIGINT NULL COMMENT '支付投影最近观察到的支付尝试标识。',
  `last_attempt_no` INT NULL COMMENT '订单支付意图下最近一次尝试的业务序号。',
  `last_attempt_status` VARCHAR(64) NULL COMMENT '支付投影最近观察到的支付尝试闭合状态。允许值：CREATED、RISK_CHECKING、RISK_REJECTED、RISK_FAILED、LIMIT_REJECTED、ROUTING、PROCESSING、REQUIRES_ACTION、UNKNOWN、SUCCEEDED、FAILED、SUCCESS_CONFLICT。',
  `attempt_source_version` BIGINT NULL COMMENT '支付尝试投影已经消费的来源聚合版本。',
  `last_attempt_event_key` VARCHAR(128) NULL COMMENT '支付投影最近处理的支付尝试事件幂等键。',
  `last_attempt_payload_hash` CHAR(64) NULL COMMENT '最近支付尝试事件规范化载荷的完整性哈希。',
  `successful_attempt_id` BIGINT NULL COMMENT '最终形成订单成功支付结果的支付尝试标识。',
  `captured_amount` DECIMAL(24,8) NOT NULL COMMENT '订单支付投影确认成功捕获的累计金额。',
  `currency` CHAR(3) NOT NULL COMMENT '金额采用的 ISO 4217 三位币种代码。',
  `last_event_time` DATETIME(6) NOT NULL COMMENT '支付或退款投影最近一次成功应用来源事实的业务时刻。',
  `version` BIGINT NOT NULL COMMENT '可变业务行并发更新使用的单调乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`order_payment_projection_id`),
  UNIQUE KEY `uk_order_payment` (`order_id`) COMMENT '唯一索引：order_id',
  UNIQUE KEY `uk_payment_intent` (`payment_intent_id`) COMMENT '唯一索引：payment_intent_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Payment 订单支付事实的本地可重建投影；只按来源版本和事件幂等键推进，不拥有 Payment 聚合。';

CREATE TABLE `t_order_funds_availability_projection` (
  `order_funds_availability_projection_id` BIGINT NOT NULL COMMENT '一条 Order-owned 资金可用性投影的内部唯一标识。',
  `order_id` BIGINT NOT NULL COMMENT 'Commerce 订单聚合的内部唯一标识。',
  `finance_economics_id` BIGINT NULL COMMENT 'Finance PAYMENT Economics 的稳定引用，仅供本库资金可用性投影导航。',
  `maturity_journal_id` BIGINT NULL COMMENT 'Finance 完成资金成熟时对应的稳定 Journal 标识，仅供本库投影导航。',
  `funds_availability_status` VARCHAR(32) NOT NULL COMMENT '订单资金是否已成熟进入 Client 可用余额的闭合投影状态。允许值：NOT_APPLICABLE、PENDING、AVAILABLE、FULLY_OFFSET。',
  `available_amount` DECIMAL(24,8) NULL COMMENT 'Finance 确认本订单已经成熟进入 Client 可用余额的金额，未成熟时为空。',
  `currency` CHAR(3) NOT NULL COMMENT '金额采用的 ISO 4217 三位币种代码。',
  `scheduled_at` DATETIME(6) NULL COMMENT 'Finance 冻结的本订单余额可用时刻，尚未完成经济计算时为空。',
  `available_at` DATETIME(6) NULL COMMENT 'Finance 权威确认本订单资金成熟进入 Client 可用余额的时刻。',
  `economics_event_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Finance EconomicsCalculated 事实的大小写敏感稳定来源事件键。',
  `economics_source_version` BIGINT NULL COMMENT 'Finance EconomicsCalculated 事实的来源版本。',
  `economics_source_payload_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Finance EconomicsCalculated 规范来源载荷的 SHA-256 小写十六进制完整性哈希。',
  `maturity_event_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Finance PayableBalanceMatured 事实的大小写敏感稳定来源事件键。',
  `maturity_source_version` BIGINT NULL COMMENT 'Finance PayableBalanceMatured 事实的来源版本。',
  `maturity_source_payload_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Finance PayableBalanceMatured 规范来源载荷的 SHA-256 小写十六进制完整性哈希。',
  `as_of` DATETIME(6) NOT NULL COMMENT '本投影已应用来源事件 occurredAt 的最大值，迟到事件不得使其回退。',
  `version` BIGINT NOT NULL COMMENT '可变业务行并发更新使用的单调乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`order_funds_availability_projection_id`),
  UNIQUE KEY `uk_order_funds_availability` (`order_id`) COMMENT '唯一索引：order_id',
  UNIQUE KEY `uk_order_funds_economics` (`finance_economics_id`) COMMENT '唯一索引：finance_economics_id',
  UNIQUE KEY `uk_order_funds_econ_event` (`economics_event_key`) COMMENT '唯一索引：economics_event_key',
  UNIQUE KEY `uk_order_funds_maturity_event` (`maturity_event_key`) COMMENT '唯一索引：maturity_event_key',
  UNIQUE KEY `uk_order_funds_maturity_journal` (`maturity_journal_id`) COMMENT '唯一索引：maturity_journal_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Order-owned 资金可用性投影；只由 Payment 与 Finance 权威事件推进，Commerce 不按本机时钟宣布成熟，最终状态不因 Refund、Payout 或当前余额变化回退。';

CREATE TABLE `t_order_refund_position` (
  `order_refund_position_id` BIGINT NOT NULL COMMENT '订单唯一可退款金额位置的内部标识。',
  `order_id` BIGINT NOT NULL COMMENT 'Commerce 订单聚合的内部唯一标识。',
  `refundable_base_amount` DECIMAL(24,8) NOT NULL COMMENT '订单成功支付后允许退款计算的冻结金额基准。',
  `reserved_amount` DECIMAL(24,8) NOT NULL COMMENT '尚未完成或释放的退款请求占用金额。',
  `refunded_amount` DECIMAL(24,8) NOT NULL COMMENT '订单已经确认成功退款的累计金额。',
  `currency` CHAR(3) NOT NULL COMMENT '金额采用的 ISO 4217 三位币种代码。',
  `version` BIGINT NOT NULL COMMENT '可变业务行并发更新使用的单调乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`order_refund_position_id`),
  UNIQUE KEY `uk_order_refund_position` (`order_id`) COMMENT '唯一索引：order_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='订单可退款金额的强一致本地位置；始终满足 reserved_amount + refunded_amount <= refundable_base_amount，预占、消费和释放必须通过乐观锁原子更新。';

CREATE TABLE `t_checkout_session` (
  `checkout_session_id` BIGINT NOT NULL COMMENT 'Hosted Checkout 会话的内部唯一标识。',
  `order_id` BIGINT NOT NULL COMMENT 'Commerce 订单聚合的内部唯一标识。',
  `public_token` CHAR(34) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '大小写敏感的 Hosted Checkout 最小能力 Token，仅用于受限 Checkout 页面动作。',
  `public_origin` VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '服务端 allowlist 选定并由会话冻结的无路径 HTTPS Origin，用于组合 Checkout URL。',
  `session_status` VARCHAR(64) NOT NULL COMMENT 'Hosted Checkout 会话当前的闭合生命周期状态。允许值：PREPARING、OPEN、PAYMENT_PROCESSING、COMPLETED、CANCELLED、EXPIRED。',
  `opened_at` DATETIME(6) NULL COMMENT 'Hosted Checkout 会话首次进入可交互状态的时刻。',
  `completed_at` DATETIME(6) NULL COMMENT 'Checkout 会话或退款请求进入完成终态的业务时刻。',
  `cancelled_at` DATETIME(6) NULL COMMENT '本订单或 Checkout 会话进入取消终态的 UTC 业务时刻，重复取消不改写。',
  `version` BIGINT NOT NULL COMMENT '可变业务行并发更新使用的单调乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`checkout_session_id`),
  UNIQUE KEY `uk_checkout_order` (`order_id`) COMMENT '唯一索引：order_id',
  UNIQUE KEY `uk_checkout_public_token` (`public_token`) COMMENT '唯一索引：public_token'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Hosted Checkout 重定向会话；公开 Token 以大小写敏感明文和冻结 Public Origin 保存，状态变更使用乐观锁，网络事实写入独立不可变证据表。';

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

CREATE TABLE `t_consumer_contract` (
  `consumer_contract_id` BIGINT NOT NULL COMMENT '订单呈现给消费者的不可变合同文件记录标识。',
  `order_id` BIGINT NOT NULL COMMENT 'Commerce 订单聚合的内部唯一标识。',
  `order_legal_id` BIGINT NOT NULL COMMENT '订单不可变上下文对应法律呈现记录的内部唯一标识。',
  `rendered_file_id` BIGINT NOT NULL COMMENT 'Platform Support 保存的不可变消费者合同文件标识。',
  `rendered_content_hash` CHAR(64) NOT NULL COMMENT '呈现给消费者的合同文件内容完整性哈希。',
  `locale` VARCHAR(35) NOT NULL COMMENT '文档或消费者体验采用的 IETF BCP 47 语言区域代码。',
  `presented_at` DATETIME(6) NOT NULL COMMENT '不可变消费者合同文件完成呈现的业务时刻。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  PRIMARY KEY (`consumer_contract_id`),
  UNIQUE KEY `uk_consumer_contract` (`order_id`) COMMENT '唯一索引：order_id',
  KEY `idx_contract_legal` (`order_legal_id`) COMMENT '访问索引：order_legal_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='呈现给消费者的不可变合同文件事实；文件标识、内容哈希、Locale 与呈现时间共同封存。';

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

CREATE TABLE `t_refund_request` (
  `refund_request_id` BIGINT NOT NULL COMMENT 'Commerce 退款请求聚合的内部唯一标识。',
  `refund_request_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '供接口响应、查询与人工操作使用的唯一退款编号。',
  `order_id` BIGINT NOT NULL COMMENT 'Commerce 订单聚合的内部唯一标识。',
  `client_id` BIGINT NOT NULL COMMENT '订单或退款请求所属平台 Client 的内部唯一标识。',
  `request_origin` VARCHAR(64) NOT NULL COMMENT '区分 Client 与内部 Operations 发起退款的闭合来源。允许值：CLIENT、OPERATIONS。',
  `client_refund_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Client 在自身命名空间内提供且不可复用的稳定退款业务标识；Operations 发起时为空。',
  `refund_amount` DECIMAL(24,8) NOT NULL COMMENT '本退款请求要求原支付渠道退还给消费者的确定金额。',
  `currency` CHAR(3) NOT NULL COMMENT '金额采用的 ISO 4217 三位币种代码。',
  `reason_code` VARCHAR(64) NOT NULL COMMENT '退款领域事实采用的受控原因代码。取值来源：Commerce Refund Reason Catalog；非固定枚举。',
  `reason_summary` VARCHAR(512) NULL COMMENT '不含敏感信息的退款原因简要说明。',
  `refund_status` VARCHAR(64) NOT NULL COMMENT '退款请求当前的闭合业务生命周期状态。允许值：PENDING、PROCESSING、SUCCEEDED、FAILED、CANCELLED。',
  `status_reason_code` VARCHAR(64) NULL COMMENT '当前终态原因采用的稳定业务代码，适用于取消、失败、过期等需要说明的结果；没有终态原因时为空。取值来源：Refund Status Reason Catalog；非固定枚举。',
  `channel_refund_id` BIGINT NULL COMMENT '支付系统负责执行退款的稳定渠道退款标识。',
  `completed_at` DATETIME(6) NULL COMMENT 'Checkout 会话或退款请求进入完成终态的业务时刻。',
  `version` BIGINT NOT NULL COMMENT '可变业务行并发更新使用的单调乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`refund_request_id`),
  UNIQUE KEY `uk_refund_request_no` (`refund_request_no`) COMMENT '唯一索引：refund_request_no',
  UNIQUE KEY `uk_refund_client_ref` (`client_id`, `client_refund_id`) COMMENT '唯一索引：client_id、client_refund_id',
  UNIQUE KEY `uk_refund_channel` (`channel_refund_id`) COMMENT '唯一索引：channel_refund_id',
  KEY `idx_refund_order` (`order_id`, `create_time`, `refund_request_id`) COMMENT '访问索引：order_id、create_time、refund_request_id',
  KEY `idx_refund_client_time` (`client_id`, `create_time`, `refund_request_id`) COMMENT '访问索引：client_id、create_time、refund_request_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Commerce 拥有的退款聚合；只保存退款业务状态、订单金额位置所需金额以及 Payment Channel Refund 的稳定标识，不复制下游状态、事件版本、事件键、载荷哈希或渠道执行明细。';

CREATE TABLE `t_compensation_event` (
  `id` BIGINT NOT NULL COMMENT '本地补偿事件记录的内部唯一标识。',
  `event_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '领域事件或本地恢复动作的全局稳定幂等键。',
  `event_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本地补偿 Handler 的全应用唯一类型。取值来源：Commerce Event/Handler Catalog；非固定枚举。',
  `sub_event_type` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '同一补偿 Handler 下区分具体业务子动作的大小写敏感类型。',
  `payload_json` JSON NULL COMMENT '补偿事件执行所需的最小非敏感结构化载荷，不超过 4 KiB UTF-8 JSON Object，且不得包含 Vault Token。',
  `execute_status` VARCHAR(64) NOT NULL DEFAULT 'INIT' COMMENT '本地补偿事件当前的可恢复执行状态。允许值：INIT、PROCESSING、ERROR、FAILED。',
  `execute_count` INT NOT NULL DEFAULT 0 COMMENT '补偿事件已经尝试执行的累计次数。',
  `next_execute_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '补偿事件下一次具备执行资格的 UTC 微秒精度时间。',
  `lock_owner` VARCHAR(64) NULL COMMENT '当前持有数据库 Claim Lease 的服务实例标识。',
  `lock_until` DATETIME(6) NULL COMMENT '数据库 Claim Lease 到期并可被其他实例重新认领的时刻。',
  `last_error_code` VARCHAR(64) NULL COMMENT '补偿事件最近一次执行失败的稳定错误分类代码。',
  `last_error_summary` TEXT NULL COMMENT '补偿事件最近一次执行失败且不含敏感信息的摘要。',
  `lock_version` BIGINT NOT NULL DEFAULT 1 COMMENT '补偿事件 Claim Lease 并发更新使用的乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_compensation_event` (`event_key`, `event_type`, `sub_event_type`) COMMENT '唯一索引：event_key、event_type、sub_event_type',
  KEY `idx_compensation_ready` (`execute_status`, `next_execute_at`, `id`) COMMENT '访问索引：execute_status、next_execute_at、id',
  KEY `idx_compensation_lease` (`execute_status`, `lock_until`, `id`) COMMENT '访问索引：execute_status、lock_until、id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='业务变更同事务写入的本地可靠执行记录；身份固定为 event_key、event_type、sub_event_type，PROCESSING 必须同时持有 lock_owner 且 lock_until 晚于当前时间，XXL-Job 仅负责唤醒和分片扫描。';

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
