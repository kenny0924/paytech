# Commerce 数据库设计规范

> **2026-09-19 Checkout 时序修订：** [创建初始化与首次支付](../../requirements/20260919/commerce-Checkout准备与Intent创建拆分/technique.md)删除独立准备阶段：创建 Checkout 时取得 OE、商业/税务/法律信息，与 Order/Session 原子保存；首次提交支付才创建唯一 Intent，后续复用。该修订取代下文独立准备命令/任务、准备时创建 Intent、Intent 绑定后才 READY 的新写时序；不重写历史数据、旧命令或 Scope。实现、数据库执行及迁移限制只见[当前台账](../CURRENT_IMPLEMENTATION.md#commerce-preparation-intent-20260919)。

> **2026-09-17 SubmitPayment 列约束修订：** [支付去幂等](../../requirements/20260917/commerce-SubmitPayment接口重构/technique.md)使 `t_consumer_acceptance.command_idempotency_id` 仅保留历史用途，新写入不读写该列。现行目标改为可空，不新增替代命令或支付提交表；增量来源为[人工 SQL](../../requirements/20260917/commerce-SubmitPayment接口重构/db/ddl.sql)。PDManer、基线及数据库执行状态见[当前台账](../CURRENT_IMPLEMENTATION.md#commerce-submit-payment-20260917)。

> **2026-09-16 CancelOrder 请求机制修订：** [本轮修订](../../requirements/20260916/commerce-CancelOrder接口重构/technique.md)取消新读写不使用命令表、Hash/租约或冻结回执，仅使用订单取消事实与数据库条件更新；共享命令表及历史记录保留。新增 t_commerce_order.cancelled_at 保存原订单取消时间；仅实际有关联 Session 时同事务关闭会话，HOSTED_CHECKOUT 缺会话仍报错，DIRECT_PAY 可以无会话。支付权威和禁止取消成功支付的约束不变；增量 SQL 尚未执行。

> **2026-09-16 创建方式与响应修订：** [已确认修订](../../requirements/20260916/commerce-订单查询合并Checkout/response-shape-amendment.md)增加订单不可变 creation_mode，当前 HOSTED_CHECKOUT、预留 DIRECT_PAY；历史和默认值为 HOSTED_CHECKOUT。订单查询将会话收进 checkout，移除对外版本，内部版本/CAS 保留。Direct Pay 业务入口仍另行设计。

> **2026-09-16 CreateCheckout 修订：** [业务重构决定](../../requirements/20260916/commerce-CreateCheckout业务重构/technique.md)移除 CreateCheckout 的请求幂等执行。新订单国家保存在 `t_commerce_order.consumer_country_code`，旧命令快照只保留历史读取；本文其他操作的命令合同不变。


> **2026-09-11 模型更新：** 本页的数字 OE、Assignment、Client 多 OE 合同及旧 Scope 冻结描述，仅适用于迁移前记录与接口；现行目标由 [四系统模型决定](../decisions/2026-09-11-cct-commerce-oe-pricing-model.md) 精确取代。Risk 本期不改，Java 与数据库尚未据此迁移，历史订单与 Hash 不重写。

最后更新：2026-09-05

状态：V1 权威逻辑目标已确认；本期模型采用 2026-09-11 四系统调整；生成统计、验证、Java 与数据库迁移状态统一见 [当前实现台账](../CURRENT_IMPLEMENTATION.md)。

> **2026-09-05 跨主题对齐：** 本文拥有 Commerce 业务事实，不单独拥有 Vault、`compensation_event`、物理 CHECK/FK 或测试基础设施规范。[Commerce Client API Hosted Checkout V1](../../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md) 已取代旧 Token Hash、Client 提交行业/MCC、外部 Cancel Expected Version 和固定 14 表目标；当前生成模型与其他横切差异按 [当前实现台账](../CURRENT_IMPLEMENTATION.md) 迁移。新增实现必须遵循 [文档治理路由](../DOCUMENT_GOVERNANCE.md)。

## 1. 文档目的与实施边界

本文定义 Commerce System 的事实所有权、15 表目标结构、字段语义、状态机、关系、并发与跨系统契约，是 Commerce PDManer 设计资产的权威输入。现实示例见 [Commerce 现实场景](./scenarios/COMMERCE_REAL_WORLD_SCENARIOS.md)，Client Server 外部合同见 [Commerce Client API Hosted Checkout V1](../../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md)，订单边界见 [Commerce 订单上下文精简设计](../plans/2026-08-24-commerce-order-context-simplification-design.md)，消费者、网络证据与非履约边界见 [Commerce 隐私与履约精简设计](../plans/2026-08-24-commerce-privacy-fulfillment-simplification-design.md)，文档/审计精简及取消撤销语义见 [Commerce 文档投影、审计与取消撤销精简设计](../plans/2026-08-24-commerce-document-audit-cancel-reversal-design.md)。

本次已同步逻辑设计、PDManer/生成器与人工 Schema DDL/初始化 DML。DataObject/Mapper/Repository、Java 服务、物理数据库、Nacos 配置和运行环境仍须在各自实施切片中完成并验证；人工 Schema SQL 未获授权应用。

## 2. 系统边界

Commerce 拥有 Order 身份/生命周期、每 Order 一个不可变 Order Context、Hosted Checkout Session、当前 Consumer Context、不可变 Consumer Contract/Acceptance/Checkout Network Evidence、订单级退款意图和位置、Order Funds Availability 本地投影、命令幂等与本地可靠执行记录。

Commerce 不拥有 Client/Operating Entity/Agreement/Commercial Term 主档，Payment Intent/Attempt/Channel/MID，Risk 决策与 Dispute Case，Finance 税务/账本/结算，Consumer Document 状态/版本/文件/更正关系，Webhook 配置与投递，文件正文，或 Vault 原始值。跨系统引用按具体消费者保留必要的稳定业务 ID、版本/Hash 或交易时点，不建立跨库外键；Refund Request 只保留 Payment `channel_refund_id` 这一稳定下游业务 ID，不投影 Payment Refund 的状态与来源元数据，也不建立 Finance 退款资金预留引用。Receipt 与 Credit Note 由 Platform Support 持久化，Finance 保留 Fiscal Fact；Commerce 只可靠发布生成所需的最小来源事实。

Commerce 不建设主动履约域。支付成功后的 Order 保持 `PAID`；退款、争议与财务追偿各自保持权威状态。Chargeback 后 RFI/举证由 Risk 的 Dispute Case、Evidence Package/Reference 和 Submission Request 承接。

## 3. 全局物理规则

- PDManer、Schema DDL、数据变更 SQL 和 Mapper 使用 `t_<logical_table_name>` 小写物理名；唯一索引 `uk_`，访问索引 `idx_`。
- 主键为服务侧 Snowflake `BIGINT`；瞬时时间 UTC `DATETIME(6)`；金额 `DECIMAL(24,8)`、费率 `DECIMAL(18,10)`，金额绑定 ISO 4217 `CHAR(3)`。
- 可变 Current 行有 `version BIGINT NOT NULL`，使用 Expected Version/CAS；`compensation_event` 仅有技术 `lock_version`；不可变事实没有乐观锁。
- 可变行有 `create_time + update_time`，创建后不可变行仅有 `create_time`；业务发生时间使用专用字段。
- 业务变更和所需 `compensation_event` 在同一本地事务提交；MQ Ack 只能发生在提交后。JSON 仅用于 `command_idempotency` 的受控请求/响应恢复快照和 `compensation_event.payload_json`；Commerce V1 不保存通用 JSON 变更历史，法律接受、网络证据和冻结上下文由专项结构化表保留。

## 4. 精确 15 表目标目录

| 分组 | 逻辑表 | 物理表 | 基数与职责 |
|---|---|---|---|
| Order | `commerce_order` | `t_commerce_order` | 每 Order 一行；身份、冻结主体/分类/金额、状态、到期与 CAS |
| Order Context | `order_context` | `t_order_context` | 每 Order 一行；最小 CCT/Risk/Finance 不可变引用 |
| Order Context | `order_legal` | `t_order_legal` | 每 Context 一行；Agreement、三份 Consumer Legal 与 Seller Disclosure |
| Order | `order_item` | `t_order_item` | 每 Order 多行；商品/服务和逐行金额 |
| Payment View | `order_payment_projection` | `t_order_payment_projection` | Intent 和当前/最近 Attempt Current View |
| Funds View | `order_funds_availability_projection` | `t_order_funds_availability_projection` | 每 Order 一行；Finance 驱动的本次 Order 资金成熟投影；精确名称是默认 32 字符建议的窄例外，仍低于 MySQL 64 字符硬上限 |
| Refund Position | `order_refund_position` | `t_order_refund_position` | 每 Order 一行；可退金额并发位置 |
| Checkout | `checkout_session` | `t_checkout_session` | 每 Order 一行；大小写敏感明文 Public Token 与会话状态 |
| Consumer | `consumer_context` | `t_consumer_context` | 每 Order 至多一行；Consumer 引用、稳定 Token、联系/账单派生维度 |
| Consumer Legal | `consumer_contract` | `t_consumer_contract` | 每 Order 至多一行；不可变合同呈现证据 |
| Network Evidence | `checkout_network_evidence` | `t_checkout_network_evidence` | 每可信 Checkout 请求一行；直接网络证据与解析维度 |
| Consumer Legal | `consumer_acceptance` | `t_consumer_acceptance` | 每 Contract 三行；不可变逐内容接受证据 |
| Refund | `refund_request` | `t_refund_request` | 每退款一行；唯一对外退款状态 |
| Reliability | `command_idempotency` | `t_command_idempotency` | 每命令键一行；幂等与恢复 |
| Reliability | `compensation_event` | `t_compensation_event` | 每待执行任务一行；可靠 MQ/本地 Handler |

## 5. 核心事实与约束

### 5.1 Order、Context、Checkout 与退款

`commerce_order` 冻结 `client_id + client_order_id`、Operating Entity/来源、Industry/MCC、金额币种和 `expires_at`。`uk_order_client_order(client_id,client_order_id)` 永久阻止同一 Client 重用业务标识；它不是传输幂等键。`order_status IN (CREATED,OPEN,PAID,CANCELLED,EXPIRED)`，仅支付成功写 `paid_at`，不含 `fulfilled_at` 或履约状态。可空 `status_reason_code` 来自 Commerce Order Status Reason Catalog，结构化说明当前取消、过期或其他需要解释的终态；它不保存历史，非终态通常为空。

Commerce Order Status Reason Catalog 现行取值（2026-10-22）：

| `status_reason_code` | 对应终态 | 写入时机 |
|---|---|---|
| `CLIENT_REQUESTED` | `CANCELLED` | Client API Cancel 在 Payment 确认 Intent 已安全终止（或 Intent 不存在）后，与订单 `cancelled_at`、Session `CANCELLED` 同一本地事务写入 |
| `EXPIRY_REACHED` | `EXPIRED` | 到期扫描在 Payment 确认终止（TERMINATED / ALREADY_TERMINATED，或 Intent 不存在）后，与 Session `EXPIRED`、支付投影 `EXPIRED`（各自存在时）和 `order.expired` 登记同一本地事务写入；Payment 仍 PENDING、已授权或已支付时不写，订单保持 OPEN。过期终态时刻取 `expires_at`，不另设 `expired_at` 列 |

新增取值须先登记本目录；`order.cancelled` / `order.expired` 的 `reasonCode` 与该列一致。依据：[订单与会话过期收尾](../../requirements/20261022/acquiring-订单过期收尾/technique.md)。

`order_context` 每 Order 一行且不可变，保存 `commercial_context_time`、Assignment、CCT V5 `cct_commercial_context_hash`、Client Pricing Scope、可选 Partner Share Scope、可选 Platform Service Charge Scope、可选 Platform Service Party、Risk Assessment、Finance Tax Snapshot/Hash、Consumer Country 和 Locale。`platform_service_charge_scope_id` 是 CCT-I13 V5 返回的 `PLATFORM_SERVICE_CHARGE` Scope 稳定标识，`platform_service_party_id` 是同一 Partner Agreement 唯一 `PLATFORM_SIGNATORY` 的法律 Party 稳定标识；二者只用于跨系统事实导航，不建立跨库或本库关系。它不保存 Commerce 自建 Snapshot Schema/Hash，不复制 Order 分类/金额、Tax Line 或 Term Component。`order_legal` 与 `order_item` 在创建事务一并插入并冻结。

CreateOrder 必须原样冻结 CCT-I13 V5 的条件元组：PARTNER 来源时，`commerce_order.local_partner_id`、`order_context.partner_share_scope_id`、`order_context.platform_service_charge_scope_id`、`order_context.platform_service_party_id` 四项成组非空；非 PARTNER 来源四项成组为空。任一缺项、额外值或多义 `PLATFORM_SIGNATORY` 都失败关闭，不允许 Commerce 猜测平台主体或费率。

`checkout_session` 是每 Order 唯一 Hosted Checkout Session。公开 Token 固定为大小写敏感的 34 字符 `chk_ + 27-char KSUID Core + 3-char Base62 CRC16`，以 `public_token CHAR(34) CHARACTER SET ascii COLLATE ascii_bin` 明文保存，并由 `uk_checkout_public_token(public_token)` 唯一约束；不再保存 `public_token_hash`。同一行冻结服务端 allowlist 选择的 `public_origin VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin`；它是无尾随斜线、无 Path/Query/Fragment 的 HTTPS Origin，Checkout URL 始终由该 Origin 与 Public Token 组合，配置迁移不得改写尚未过期的 Session。CRC 只作格式校验，存在性、Order/Session 关系、状态与 `expires_at` 才决定能力是否有效。Payment Intent/Attempt Current View 只用于 Commerce 读取；Attempt `UNKNOWN` 保持 Active，迟到事件不能回退新 Attempt 或唯一成功事实。临时 Channel/MID 短缺可在同一 Operating Entity 下重试；OE CLOSED 后阻止新 Attempt，待活动/UNKNOWN Attempt 收敛，Client 若需再次付款必须以新的 `client_order_id` 创建新订单，不自动替换、不跨 OE 重试。

`t_order_funds_availability_projection` 与 Order 一对一，状态固定为 `NOT_APPLICABLE`、`PENDING`、`AVAILABLE`、`FULLY_OFFSET`。Order 创建时为 `NOT_APPLICABLE`，`as_of=commerce_order.create_time`；唯一 PaymentSucceeded 后为 `PENDING`；Finance `EconomicsCalculated` 冻结 `finance_economics_id + balance_available_time`，只有 `PayableBalanceMatured` 能写入最终 `client_matured_amount` 和成熟时刻并转为 `AVAILABLE (>0)` 或 `FULLY_OFFSET (=0)`。Economics 与 Maturity 分别保存独立 Event Key、Source Version 和 Payload Hash，重复同内容幂等、同版本冲突失败关闭；在本地尚未应用匹配 PaymentSucceeded 时 Finance 事件保持可重试且不 Ack。`economics_event_key`、`maturity_event_key` 和统一 `compensation_event.event_key` 均使用 ASCII binary collation；两个 Payload Hash 固定为小写十六进制 ASCII binary 值，避免默认大小写不敏感排序规则改变事件身份。Commerce 不按本机时钟宣布成熟，最终状态也不因后来 Refund、Payout 或当前余额变化回退。该金额表示本 Order 已进入 Client 可用余额的金额，不是 Client 当前账户余额。

`t_order_refund_position` 与 Order 一对一，在创建 Order 的同一本地事务中初始化：`refundable_base_amount=0`、`reserved_amount=0`、`refunded_amount=0`，`currency=commerce_order.currency`。唯一成功的 `PaymentSucceeded` 只允许把 `refundable_base_amount` 从零建立为实际捕获金额一次；重复事件必须幂等，冲突金额必须失败关闭。创建 Refund Request 时以 Expected Version/CAS 在同一本地事务增加 `reserved_amount`，并始终满足 `reserved_amount + refunded_amount <= refundable_base_amount`。

`t_refund_request` 精确包含以下字段，不保存 Finance Funding 引用、Payment 来源版本、Event Key、Payload Hash、事件时间或 Current Projection：

| 字段 | 语义与约束 |
|---|---|
| `refund_request_id` | 服务侧 Snowflake 主键 |
| `refund_request_no` | 平台退款编号；`uk_refund_request_no(refund_request_no)` |
| `order_id` / `client_id` | 本地 Order 归属与 Client 冗余校验 |
| `request_origin` | `CLIENT` 或 `OPERATIONS` |
| `client_refund_id` | Client 退款业务标识；CLIENT 发起时 client_refund_id 必填；OPERATIONS 发起时为空；`uk_refund_client_ref(client_id,client_refund_id)` |
| `refund_amount` / `currency` | 本次申请的退款金额与 Order 币种 |
| `reason_code` / `reason_summary` | 结构化原因与受控补充说明 |
| `refund_status` | `PENDING`、`PROCESSING`、`SUCCEEDED`、`FAILED` 或 `CANCELLED` |
| `status_reason_code` | 失败、取消或其他终态原因；成功时为空 |
| `channel_refund_id` | 可空且唯一的 Payment Channel Refund 稳定 ID；`uk_refund_channel(channel_refund_id)` |
| `completed_at` | `SUCCEEDED`、`FAILED` 或 `CANCELLED` 的权威终态时间；非终态为空 |
| `version` | Request 条件更新的业务 CAS |
| `create_time` / `update_time` | 本地创建与最后更新时间 |

`client_refund_id` 是 Client 退款业务幂等标识，不是传输命令幂等键；CLIENT 在同一 Client 下重用该值只定位同一退款，相同标识携带不同 Order、金额或币种时按结构化字段比较并冲突失败，不依赖 Request Hash 猜测等价。OPERATIONS 不伪造 Client 标识，`client_refund_id` 固定为空，使用 `t_command_idempotency` 的命令键完成重试幂等。`channel_refund_id` 是可空且唯一的稳定 Payment 引用：Payment 尚未创建渠道退款时为空，填入后不承载下游状态。

`t_command_idempotency` 的唯一作用域为 `caller_scope + operation_type + idempotency_key`；Client API 的 `caller_scope=CLIENT:<client_id>`，Credential 轮换不改变命名空间。`caller_scope`、`idempotency_key`、`resource_no` 和 `response_location` 都使用 ASCII binary collation，`idempotency_key` 目标长度为 `VARCHAR(255)`。目标恢复字段固定为：

| 字段 | 目标语义 |
|---|---|
| `request_hash` / `request_hash_version` | RFC 8785 规范语义对象的 SHA-256 小写十六进制值及算法版本；旧记录永远按自身版本比较 |
| `request_snapshot_schema_version` / `request_snapshot_json` | Create Checkout/Create Refund 在 Root 建立前可恢复的已验证语义快照；最多 16 KiB；Cancel 两列为空 |
| `operation_context_time` | 首次 Claim 的业务时点；默认 `expires_at` 和所有恢复调用复用该值 |
| `recovery_stage` / `process_status` | 每操作固定阶段；状态只允许 `PROCESSING`、`SUCCEEDED`、`FAILED` |
| `resource_type` / `resource_id` / `resource_no` | 预分配或已存在的稳定业务资源；公开编号大小写敏感 |
| `response_code` / `response_http_status` | 稳定业务 Code 与 HTTP 状态；暂态结果为空 |
| `response_snapshot_schema_version` / `response_location` / `response_body_json` | 稳定回执版本、可空相对 Location 和最多 4 KiB 的受控 JSON；暂态结果为空 |

Create Checkout、Cancel 和 Create Refund 在 Claim 时冻结请求 Hash、首次 `operation_context_time`、恢复阶段和稳定资源；Create Checkout/Refund 同时冻结各自 Request Snapshot。Create Checkout 的 Snapshot 保留 Client 是否显式提供 `expiresAt` 以及最终有效值；Create Refund 的 Snapshot 保留 Order、Client Refund ID、金额和原因。稳定创建结果冻结上表 Response 字段，使同 Key 重放不依赖后来可变状态或当前公共域名配置。Checkout 创建回执包含 Public Token URL，因此只供原命令恢复，不进入普通 Response Body 日志、MQ/Webhook 或通用审计 Payload。技术暂态和未决结果不得封存为永久失败。

Recovery Stage 固定为：Create Checkout=`CLAIMED | ORDER_COMMITTED | COMPLETED`；Cancel=`CLAIMED | PAYMENT_PENDING | COMPLETED`；Create Refund=`CLAIMED | REFUND_COMMITTED | PAYMENT_REFUND_BOUND | COMPLETED`。Create Checkout 的成功和稳定业务失败都进入 `COMPLETED`，由 `process_status=SUCCEEDED | FAILED` 区分结果；依赖超时、网络未知和仍在处理的 Payment 保持 `PROCESSING`。

Refund Request 创建后为 `PENDING`，本地退款额度已预占；Commerce 本地预占成功后直接调用 PAY-I04，Payment 以自身 Refund Position CAS 创建 Channel Refund，Commerce 获得 `channel_refund_id` 后进入 `PROCESSING`。Payment Refund=UNKNOWN 只保留在 Payment 本地，Commerce Request 保持 PROCESSING、本地退款额度继续预占。Payment Refund=SUCCEEDED 同时通知 Commerce 与 Finance：Commerce 同事务消费本地退款额度并将 Request 置为 SUCCEEDED，即 `reserved_amount` 减少、`refunded_amount` 等额增加并写入 `completed_at`；Finance 独立按成功事实记账。Payment Refund=FAILED/CANCELLED 只通知 Commerce，Commerce 同事务释放本地退款额度并进入对应终态，即 `reserved_amount` 等额减少并写入 `status_reason_code + completed_at`。

退款成功后 Commerce 可靠发布最小生成来源事实。Platform Support 创建新的 Credit Note 并引用原 Receipt，绝不覆盖原文档；Commerce 不保存 Consumer Document Current Projection。

### 5.2 Consumer 单轨模型

`consumer_context` 的精确事实为 `consumer_reference`、`email_vault_token`、可选 `phone_vault_token`、`consumer_profile_vault_token`、`email_domain`、`phone_country_code`、`billing_country_code`、`billing_region_code`、`billing_city`、`billing_postal_prefix` 和版本/审计时间。

- `consumer_reference` 是 Client 命名空间内的非 PII、不透明业务引用；禁止复用 Email、Phone、证件号或其他个人原值。
- 同一规范化 Email/Phone 在平台内部同字段作用域返回稳定 Token，分别兼具受控关联与授权 Reveal，Vault 内部密钥轮换不得改变业务 Token。
- `consumer_profile_vault_token` 只保护姓名和完整账单地址，不能重复包含 Email/Phone。
- 不保存 Consumer Blind Index、Key Version、Masked Value、旧 Composite Token、Vault Record Version 或 Metadata Hash；Token 不得进入外部响应、普通日志、MQ 或通用审计 JSON。
- 当前 Vault V1 仅提供 Protect/Reveal，没有 Binding 或 Retire。Commerce 命令恢复复用首次 Protect 后已保存的稳定 Token；引用替换、解除或销毁能力不在 V1 范围，若出现真实消费者必须先批准窄 V2 设计。

### 5.3 Network Evidence 与 Acceptance

`checkout_network_evidence` 保存 `order_id + checkout_session_id + evidence_scene + request_id`、`client_ip_address`、`user_agent`、可选 `trusted_proxy_chain`，以及 Geo/ASN/Network/Proxy/VPN/Tor/Hosting/Browser/OS/device_type。`occurred_at` 只取可信 Gateway/Commerce 服务端 UTC 时钟。`uk_network_evidence_request(checkout_session_id,request_id,evidence_scene)` 防止重复写入；`idx_network_order_time` 和 `idx_network_client_ip(client_ip_address,occurred_at)` 是受控取证/频次路径。

网络证据直接保存完整客户端 IP、完整 User-Agent 和可信代理链。它不保存 Network Vault Token、Blind Index、Key Version 或 `device_identifier`，插入后不可更新；原值和派生维度是受限数据，禁止复制到通用审计 JSON、MQ、普通日志或普通业务页面。

Consumer Contract 1:N Acceptance。`uk_consumer_acceptance(consumer_contract_id,acceptance_type)` 使每个 Contract 的 `CONSUMER_TERMS`、`REFUND_POLICY`、`PRIVACY_NOTICE` 三类记录各至多一条；每条必须关联同 Order、同 Session 的 `LEGAL_ACCEPTANCE` Evidence，二者同一本地事务插入且创建后不可变。COM-I10 每次实时校验归属、可支付状态、到期和三类精确齐全，返回 Contract ID、三个显式 Acceptance ID、`acceptance_completed_at=max(accepted_at)`、Order Version 和 Expires At；不维护 Acceptance Summary、集合 Hash 或完成指针。

### 5.4 事件、审计与跨系统边界

Commerce V1 不保存 `business_change_record/business_change_version`。结构化 `version` 只服务当前业务行 CAS，不代表历史版本；Order Context/Legal/Item、Consumer Contract、Network Evidence 和 Acceptance 本身就是冻结或追加式事实。若未来出现明确的监管审计或历史还原需求，应针对具体聚合重新评估，不得默认恢复通用 JSON BCR。

订单取消同事务登记 `OrderCancelled`；支付成功、退款成功等文档来源事实通过 `compensation_event` 在业务提交后可靠发布。面向 Client 的八种 Webhook 业务结果也在状态变更事务中冻结稳定公开 Event ID、Source Version/Hash 和不超过 4 KiB 的受控 Canonical Envelope；Event ID 在同一 Client 全部 Webhook 事件中永久唯一，不得跨 Type/Schema/来源资源复用。统一可靠记录使用 `event_type=CLIENT_WEBHOOK_REQUESTED` 和具体公开 Event Type 作为 `sub_event_type`，Platform 明确幂等接收后才完成本地任务，恢复不从后来可变 Root 重建旧 Payload。这是统一 Compensation Event 通常回源重建规则的已批准窄例外，不把 `compensation_event` 变成通用业务事件仓库。公开 Event ID 映射 Platform `source_event_key`，不映射 Platform 内部 BIGINT `event_id`；Platform 先按 Tenant + Source System + Source Event Key 串行检查现有事件，并使用现有 Type/Schema/Source Version/Hash 字段校验冲突，任一不同都 Fail-Closed。Platform Support 对 Endpoint、Subscription、Secret、Delivery、Retry/Replay、文档生成、版本和更正幂等负责，Finance 对 Fiscal Fact 负责；Commerce 不建立 Platform Delivery 投影。

Commerce 通过内部 HTTP 契约与 CCT、Risk、Finance、Payment 交互，不读其他数据库。Commerce 仅保存实际采用的 Risk `ALLOW` Assessment ID；Risk 独占 Case、Decision、RFI、Evidence Package/Reference 和 Submission Request。Chargeback 后 Risk 进入 `EVIDENCE_REQUIRED` 并发布 `DisputeEvidenceRequested`；Commerce 不增加履约表、接口、事件或场景行来伪造举证。

COM-I04 返回订单已经冻结的 `platform_service_charge_scope_id`、`platform_service_party_id` 以及 CCT-I13 `V5 commercial_context_hash`，供 Finance 精确读取原 Scope 并把法律 Party 映射为本地 Finance Entity。Commerce 不解析或返回 `finance_entity_id`，不复制 Commercial Term Component、服务费金额或任何会计状态。Risk 的最小 Order Preparation Context 如需审计关联可以携带同一 V5 Hash，但不得消费 Finance Entity ID、平台服务费金额或会计映射。

### 5.5 Cancel、Void 与 Reversal

- `CANCEL` 是未支付关单：没有成功 Authorization/Capture/Sale，且没有已提交或 `UNKNOWN` 渠道结果。Commerce 调用 Payment 终止 Intent，只有得到确定终止结果后才将 Order/Session 更新为 `CANCELLED`。Cancel 不会静默升级为 Reversal。
- `VOID` 是 Payment 对成功 Authorization 的内部 Action。精确条件为 `AUTHORIZE Action=SUCCEEDED` 且 `captured_amount=0`；单独授权成功不是 `PaymentSucceeded`。Void 成功后业务撤销场景下 Intent/Order/Session=`CANCELLED`，订单原因通常为 `AUTHORIZATION_VOIDED`，不创建 Refund Request 或 Finance 事实。
- `REVERSAL` 是 Commerce 接收的统一业务命令，不是 Payment Action 或新表。Payment 在锁/CAS 内原子判断：授权未捕获时执行 Void，SALE/全额 Capture 后返回 `REFUND_REQUIRED` 并由 Commerce 创建标准全额 Refund Request。Capture 提交中或 UNKNOWN 时返回 `PENDING`；部分 Capture 的自动 Reversal 在 V1 返回 `PARTIAL_CAPTURE_REVERSAL_NOT_SUPPORTED`。
- Captured Reversal 完成退款后 Order 仍为 `PAID`，退款经济结果由 Refund Request 与 Refund Position 表达，不增加 `REVERSED` 状态。

## 6. 关系图

```mermaid
erDiagram
    commerce_order ||--|| order_context : freezes
    order_context ||--|| order_legal : locks
    commerce_order ||--|{ order_item : contains
    commerce_order ||--|| checkout_session : opens
    commerce_order ||--o| consumer_context : identifies
    commerce_order ||--o| consumer_contract : presents
    order_legal ||--o| consumer_contract : renders
    commerce_order ||--o{ checkout_network_evidence : observes
    checkout_session ||--o{ checkout_network_evidence : captures
    consumer_contract ||--o{ consumer_acceptance : accepts
    checkout_network_evidence ||--o{ consumer_acceptance : proves
    commerce_order ||--o| order_payment_projection : projects
    commerce_order ||--|| order_funds_availability_projection : matures
    commerce_order ||--|| order_refund_position : controls
    commerce_order ||--o{ refund_request : receives
```

## 7. 状态机与实施门禁

```text
Order: CREATED -> OPEN -> PAID
       CREATED -> CANCELLED
       OPEN -> CANCELLED | EXPIRED

Checkout Session: PREPARING -> OPEN -> PAYMENT_PROCESSING -> COMPLETED
                  PREPARING -> CANCELLED
                  OPEN -> CANCELLED | EXPIRED

Refund: PENDING -> PROCESSING -> SUCCEEDED | FAILED | CANCELLED
        PENDING -> FAILED | CANCELLED

Funds Availability: NOT_APPLICABLE -> PENDING -> AVAILABLE
                                      PENDING -> FULLY_OFFSET
```

`PROCESSING` 同时覆盖 Payment 内部的 CREATED、PROCESSING 与 UNKNOWN；Commerce 不复制 Payment 的 `UNKNOWN` 状态。Refund `FAILED` 表示 Payment/Channel 权威确认无法完成，Refund `CANCELLED` 只表示 Channel Action 提交前的受控主动取消；二者都不可复活。Order Cancel 仅在 Payment 已确认没有成功 Authorization/Capture/Sale 且没有未知渠道结果时完成；授权成功必须使用 Reversal，未 Capture 走 Void，已全额 Capture 走 Refund。PAN、CVV/CVC、完整磁道、PIN/PIN Block、Vault 密文和 Secrets Manager 凭据不得进入模型。MVP 默认保留业务/技术数据，不实现自动 Retention、Legal Hold、归档、匿名化或销毁。当前生成模型为 15 表 / 246 字段 / 24 个非主键唯一索引 / 12 个访问索引 / 15 条本库关系 / 4 个 Diagram Group；PDManer 4.9.3 人工检查仍是兼容门禁，SQL 尚未应用，Java 与部署状态只读当前状态台账。

本文件不授权手工数据库 SQL、Java 模块、DataObject/Mapper/Repository、物理数据库或运行环境。
