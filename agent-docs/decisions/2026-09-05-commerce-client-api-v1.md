# Commerce Client API Hosted Checkout V1 决策

> **2026-09-16 接入职责后继：** [Filter 精简决定](2026-09-16-commerce-ingress-filter-simplification.md)移除 Commerce 的专用 Client Key 校验和 Client/Customer 缓存响应包装。公开 Key 协议和请求幂等由上游中间件处理，公开缓存头由 Gateway 输出；保留现行 Header 参数声明与业务防重。本次不实现或激活中间件。

> **2026-09-16 CreateRefund 后继：** [本入口重构](../../requirements/20260916/commerce-CreateRefund接口重构/technique.md)按 clientRefundId 比较原申请并返回原创建回执；移除 Client 创建退款的请求 Key/Hash/Claim/Lease 和回执快照消费，Header 仅保留接入校验。下游退款身份、金额预占和补偿恢复仍保留；Operations 合同及其他接口不因此改变。

> **2026-09-16 CancelOrder 后继：** [本轮用户纠正](../../requirements/20260916/commerce-CancelOrder接口重构/requirement.md)移除取消用例的请求幂等 Hash、Claim/Lease、恢复阶段及冻结回执；按订单原事实防重，Header 仅保留接入校验，取消响应移除内部 version。本文对应取消 Key 冲突和请求重放语义由该修订取代；Payment 终止权威及其 Order 派生标识继续保留。

> **2026-09-16 响应结构后继：** [已确认修订](../../requirements/20260916/commerce-订单查询合并Checkout/response-shape-amendment.md)将两个订单 GET 的会话字段收进 checkout，增加固定 creationMode，删除订单响应与 fundsAvailability 的 version；不改变内部 CAS 或其他操作的合同。

> **2026-09-16 查询合并：** 用户确认[取消独立 Client Checkout 查询](../../requirements/20260916/commerce-订单查询合并Checkout/technique.md)，两个订单 GET 承接支付链接、准备状态和金额状态。以下当前资源清单已更新；旧首片记录仅作为历史保留。

- Decision status: Confirmed
- Implementation status: See [`CURRENT_IMPLEMENTATION.md`](../CURRENT_IMPLEMENTATION.md)
- Deployment status: Not deployed
- Decision date: 2026-09-05
- Scope: 面向 Client Server 的 Commerce 外部 M2M API；V1 只覆盖 Hosted Checkout、Order、Cancel 与 Refund
- Partially superseded: D1 和第 3 节中“不为外部调用创建 API JAR”的部分由 [`2026-09-05-commerce-client-api-contract-module.md`](./2026-09-05-commerce-client-api-contract-module.md) 取代；单服务、单部署单元和 OpenAPI 公开权威等其余决定继续有效
- Error-contract supersession: D3、D10 中 Body 不含 Request ID 的部分由 [`2026-09-06-system-wide-rfc-9457-problem-details.md`](./2026-09-06-system-wide-rfc-9457-problem-details.md) 取代；Commerce 业务 Code/Status 继续由本文拥有
- First-slice amendment: 2026-09-07 确认先实现 Create/Get Checkout，并以仅限 `local|auto|test` 的确定性 Owner Local Mock 代替尚未发布的跨系统 HTTP 合同；默认/生产失败关闭，且不激活 Gateway、Nginx 或 Checkout 页面
- Supersedes:
  - `requirements-site/content/requirements.ts` 中 COM-01、COM-I01 要求 Client 在 Create Checkout 请求中提交 `industry_code + mcc_code` 的部分；后继由 CCT-I13 在首次命令时点解析 Client 唯一 Primary Classification
  - `requirements-site/content/requirements.ts` 中 COM-I05 以及 `requirements-site/content/global-use-case-sequences.mjs` E2E-13 要求 Client API Cancel 提交 Order Expected Version 和仅以 `client_order_id` 定位的部分；后继外部路径使用 `orderNo`，不接收 Expected Version，Commerce 内部仍使用锁/CAS
  - `requirements-site/content/requirements.ts` 中 COM-I06 向外返回内部 `refund_request_id` / `channel_refund_id` 的部分；后继只返回公开 `refundRequestNo`，不暴露 Channel Refund ID
  - `agent-docs/database/COMMERCE_DATABASE.md` 中 Checkout 只保存 `public_token_hash`、目标模型固定 14 表的部分；后继 Checkout 明文保存大小写敏感 Public Token，并增加一张 Order-owned Funds Availability Projection，目标为 15 表
  - `docs/superpowers/specs/2026-09-05-commerce-modular-code-design.md` 中目标状态固定 14 表的部分；当前模型、SQL、Java 和数据库状态只读 `CURRENT_IMPLEMENTATION.md`
  - `agent-docs/decisions/2026-09-05-client-api-gateway-contract.md` D8、`docs/superpowers/specs/2026-09-05-client-api-gateway-design.md` 第 11 节和对应 Gateway 实施计划中无条件删除 `Location` 的部分；后继允许经 Gateway 严格校验的同源绝对路径式 `Location` 用于 `201 Created`
  - `agent-docs/COMPENSATION_EVENT_GUIDE.md` 与 `docs/superpowers/plans/2026-09-05-compensation-event-six-model-alignment.md` 中“可靠任务 Payload 通常从当前 Owner Root 重建”的部分；只对本决策八种 Webhook 的不可变事件时公开快照允许不超过 4 KiB 的 Canonical Envelope，其他 Compensation Event 规则不变

## 1. 背景

Commerce Client API 的调用者是 Client 自己的服务端，不是 Consumer 浏览器。Client Server 创建 Hosted Checkout 后，把返回的 Checkout URL 交给 Consumer；Consumer 随后进入独立 `CHECKOUT` Channel。Client API Credential 不能进入浏览器，Checkout Token 也不能获得 Client API 的查询、Cancel 或 Refund 权限。

参考 Stripe 与 Checkout.com 的外部稳定性做法，本决策分离业务唯一标识、传输幂等键、公开资源标识、业务状态和 Webhook 事件，并保持 Code-MoR 自己的六系统 Owner、Gateway 和支付安全边界。外部参考只作为证据，不拥有本项目语义。

## 2. 已确认决定

### D1. 入口和部署边界

- Client API 不是独立服务，所有 Commerce Client API Controller 都进入现有 `paytech-commerce-web`。
- 外部语言无关 OpenAPI 保存在 Commerce 仓库；不为外部契约创建 `paytech-commerce-api`。
- V1 只提供 Hosted Checkout。未来 DirectPay 使用独立 `/api/v1/payments` 资源，不向 Checkout 请求追加支付执行字段。
- PAN、CVV/CVC、完整磁道、PIN/PIN Block 永不进入 Code-MoR。

### D2. V1 资源与接口

固定接口为：

1. `POST /api/v1/checkouts`
2. `GET /api/v1/orders/{orderNo}`
3. `GET /api/v1/orders?clientOrderId=...`
4. `POST /api/v1/orders/{orderNo}/cancel`
5. `GET /api/v1/orders/{orderNo}/refundable-amount`
6. `POST /api/v1/orders/{orderNo}/refunds`
7. `GET /api/v1/refunds/{refundRequestNo}`
8. `GET /api/v1/refunds?clientRefundId=...`
9. `GET /api/v1/orders/{orderNo}/refunds`

两个带业务 ID Query 的 GET 都是精确单资源查询；V1 不提供无过滤的 Order 或 Refund 全量列表。

### D3. 身份、Scope 与资源授权

- 外部只使用 `Authorization: Bearer <client-api-key>`；Gateway 通过 PLT-I03 认证并注入可信 Client ID。
- Gateway 执行 Route Scope，Commerce 始终按可信 Client ID 校验资源归属；错误 Client 访问与真实不存在统一返回 404。
- Scope 固定为 `checkouts.write`、`orders.read`、`orders.cancel`、`refunds.read`、`refunds.write`。
- Client API 不启用浏览器 CORS，响应固定 `Cache-Control: no-store`；成功响应的 Request ID 只在 `X-Request-Id` Header 中传播，错误响应同时在 RFC 9457 Problem Body 的 `requestId` 字段中返回相同值。

### D4. 标识与金额

- `orderNo` 和 `refundRequestNo` 是大小写敏感、不可解析的公开平台标识；内部 Snowflake ID、Payment ID、Provider/MID 引用不外露。
- `clientOrderId` 在一个 Client 内永久唯一，但不是传输幂等键。
- Client 创建退款时 `clientRefundId` 必填且在一个 Client 内唯一；Operations 退款保持为空。
- 金额和数量使用 JSON 十进制字符串，Java 使用 `BigDecimal`，数据库金额继续使用 `DECIMAL(24,8)`；外部边界按币种小数位校验，不使用 JSON 浮点数或科学计数法。

### D5. Create Checkout 输入

- Client 只提交 `clientOrderId`、币种、Consumer 国家、可选 `expiresAt` 和商品行。
- 不提交 locale、industry/MCC、Client/OE、Provider、MID、支付方式、消费者 PII、税额或总额。
- V1 locale 固定 `en-US`。
- CCT-I13 必须在首次冻结的 `operationContextTime` 解析唯一 Primary Classification，并返回精确 industry/MCC；不存在、未就绪或歧义统一为 `422 CLIENT_CONFIGURATION_NOT_READY`。
- `expiresAt` 缺失时冻结为首次 `operationContextTime + 30m`；显式值必须位于 `+5m` 至 `+24h`，使用 UTC RFC 3339，重试不重新计算。
- `201 Created` 只在 Checkout URL 已可用时返回，并携带可验证的相对 `Location`。

### D6. Checkout Public Token

- Token 固定为 `chk_` + 27 字符 KSUID Core + 3 字符 Base62 CRC16 校验码，总长 34，大小写敏感。
- 2026-09-16 后继生成规则：Foundation 静态 `PublicTokenGenerator.generate(String... mixingValues)` 内部取当前系统 UTC 秒，返回无前缀主体；Commerce 传国家码与 Client ID 文本并拼接 `chk_`。16 字节 CSPRNG Payload 与有边界编码的混入值摘要前 16 字节 XOR，不建立可解码绑定或安全语义。旧混入方式生成的 Token 继续可读，已存值不重建；完整边界见[公共 Token 技术说明](../../requirements/20260916/foundation-PublicToken公共生成/technique.md)。
- 校验码只用于发现录入/传输错误；存在性、Session/Order 关系、状态和 `expiresAt` 才决定 Token 是否可用。
- 数据库以 ASCII binary collation 明文保存并建立唯一索引；不保存 Hash、不使用 Vault/HMAC/KMS。
- Token 只授权 Hosted Checkout 的最小页面动作，不能调用 Client API、Cancel、Refund 或读取受限数据。

### D7. 命令幂等

- 所有 POST 必须携带 `Idempotency-Key`，GET 不使用；Gateway 只原样转发，不缓存、不生成、不解释也不自动重试。
- Commerce 以 `Client + operation + key` Claim，保存语义请求 Hash、首次操作时点、Lease、恢复阶段、稳定资源和结果。
- 目标幂等 Key 上限为 255；Hash、Request Snapshot 和 Response Snapshot 都显式版本化。Create Checkout 与 Create Refund 在 Root 建立前保存受控 Request Snapshot；稳定创建结果同时冻结 HTTP 状态、相对 Location 和不超过 4 KiB 的受控创建回执 JSON，以便重放不读取后来可变状态或当前公共域名配置。含 Checkout URL 的回执具有访问能力，只能用于原命令恢复，不能复用为普通日志、MQ/Webhook 或通用审计 Payload。
- 同 Key 同语义请求重放或恢复；同 Key 不同请求返回 `409 IDEMPOTENCY_KEY_REUSED`。
- 新 Key 复用已有 `clientOrderId` 始终返回 `409 CLIENT_ORDER_ID_ALREADY_EXISTS`。
- 新 Key 复用已有 `clientRefundId` 时，相同不可变退款内容返回原始 `201` 回执，不同内容返回 `409 CLIENT_REFUND_ID_CONFLICT`。
- Cancel 对 Order 状态天然收敛；外部不接收 Expected Version，内部锁/CAS 不变。
- 通用 Claim/Lease/CAS 与 `t_command_idempotency` Mapper/Repository 由按需引入的 Foundation
  `paytech-command-idempotency` 实现；Commerce 仍拥有上述请求 Hash/Snapshot、Operation、恢复阶段、资源、响应、错误、
  重放策略、本地表和事务编排。完整代码归属边界见
  [`2026-09-09-foundation-command-idempotency.md`](./2026-09-09-foundation-command-idempotency.md)。

### D8. 状态与资金可用性

- Order 状态保持 `CREATED | OPEN | PAID | CANCELLED | EXPIRED`，不增加 `SETTLED`。
- Payment、Checkout、Refund 各自使用独立外部状态；Payment 内部 `UNKNOWN` 对 Client 映射为 `PROCESSING`。
- 新建 Order-owned `t_order_funds_availability_projection`，状态为 `NOT_APPLICABLE | PENDING | AVAILABLE | FULLY_OFFSET`。该精确业务名是数据库默认 32 字符建议的窄例外，仍受 MySQL 64 字符硬上限约束。
- `PaymentSucceeded` 进入 `PENDING`；Finance `EconomicsCalculated` 提供 `scheduledAt`；只有 `PayableBalanceMatured` 能进入 `AVAILABLE` 或 `FULLY_OFFSET`。
- `AVAILABLE` 表示该 Order 的正金额已经成熟进入 Client 可用余额，并返回该次 `availableAmount`；它不是当前账户余额，后续退款和 Payout 不回退该状态。

### D9. Cancel 与 Refund

- Cancel 成功返回 200；支付仍在收敛返回 202 `PAYMENT_IN_PROGRESS`；发现成功 Authorization/Capture/Sale 或 Order 已 PAID 返回 409 `REVERSAL_REQUIRED`；Order 已 EXPIRED 或处于其他不可取消终态返回 409 `ORDER_NOT_CANCELLABLE`。Cancel 不静默执行 Void 或 Refund。
- 可退款金额查询扣除本地处理中预占，但只作建议；创建退款时必须重新锁定并校验。
- 退款请求不提交 currency，继承 Order；本地 Refund Request 与额度预占提交后即可返回原始 `201` 回执，下游继续恢复。
- Refund 外部状态固定 `PENDING | PROCESSING | SUCCEEDED | FAILED | CANCELLED`。

### D10. 错误合同

- Owner 和 Gateway 产生的 Client API 4xx/5xx 都使用 RFC 9457 `application/problem+json`。
- Body 必填 `type=about:blank`、标准状态 `title`、安全 `detail`、顶层 `code`、`requestId` 和 UTC 六位小数 `timestamp`；字段错误可增加仅含 `field/message` 的 `errors`。Body 不复制 HTTP `status`，Header 与 Body Request ID 必须相同。
- HTTP 状态是传输状态唯一权威；调用方按 HTTP 状态和顶层 `code` 分支。
- `IDEMPOTENCY_REQUEST_IN_PROGRESS`（409）和 `CHECKOUT_CREATION_IN_PROGRESS`（503）固定返回 `Retry-After: 3`；该值只在响应 Header，不进入 Problem Body。

### D11. Webhook

- 业务事件固定为 `order.paid`、`order.cancelled`、`order.expired`、`order.funds_available`、`refund.created`、`refund.succeeded`、`refund.failed`、`refund.cancelled`。
- Payload 使用稳定 `eventId`、`eventType`、`schemaVersion`、`createdAt` 和不可变 `data.object` 快照；`eventId` 在同一 Client 的全部 Webhook 事件中永久唯一，不得跨 Type/Schema/来源资源复用；不含 Checkout Token/URL、PII、网络原值或内部 Payment/Provider/Risk 数据。
- Platform Support 拥有 Endpoint、Subscription、Secret、Delivery、Retry 和 Replay；Commerce 在业务事务中以统一 `compensation_event` 冻结不超过 4 KiB 的 Canonical Envelope，并以稳定 source tuple 可靠提交，不能在重试时从当前 Root 重建旧事件。
- Delivery 为 at-least-once，可能重复和乱序；Retry/Replay 保持相同 Event ID/Body，每次投递重新生成签名时间。
- Platform 先按 Tenant + Source System + Source Event Key 串行检查；同 Event ID 的 Type、Schema、Source Version 或任一 Payload Hash 不同都 Fail-Closed，不能利用现有复合唯一键插入第二条公开事件。
- `Code-MoR-Signature` 使用 `t=<unix-seconds>,v1=<hex-hmac>`，签名内容为 `timestamp + "." + rawBody` 的 HMAC-SHA256，默认容忍五分钟时钟偏差。V1 Sender 每个 Attempt 冻结并使用一个 ACTIVE Secret、发送一个 `v1`；Parser 接受多个 `v1` 作为前向兼容，不提前引入 Platform 双签字段或宽限状态。

### D12. 首个本地 Checkout 实施切片

- 首片只实现 `POST /checkouts` 与 `GET /orders/{orderNo}/checkout` 的 Commerce 私网 Controller；其余八个 V1 操作、Webhook、Hosted Checkout 页面和公共 Route 保持未实现。
- CCT、Risk、Finance、Payment 各由 Commerce 定义一个最小 `*Client` 应用端口；在真实 Owner `@HttpExchange` 合同发布前，可提供确定性 `LocalMock*Client`，但只能在 Active Profile 包含 `local`、`auto` 或 `test` 且显式选择 `local-mock` 时装配。默认模式为 `disabled`，调用稳定返回依赖不可用，非本地 Profile 误启 Mock 必须启动失败。
- Local Mock 只证明本地编排和恢复合同，不得复制 Owner wire DTO、手写未来 URI、冒充真实跨系统集成或作为生产降级。真实 Owner 合同替换点只保留一条明确 TODO。
- Risk Local Mock 的 `DENY` 收敛为 `422 CHECKOUT_NOT_ELIGIBLE`；暂态结果保持命令可恢复，不封存为永久失败。
- 该切片不授权创建或启用 Gateway Target/Route、Nginx Location、`checkout-web` 或对 Consumer 可达的 `/c/{token}`。本地 `201` 中的 Checkout URL 只验证冻结合同，不能据此宣称端到端可用。

## 3. 明确不做

- 不创建独立 Client API 服务、Checkout/Order/Refund Maven 业务 JAR 或只为外部调用创建的 API JAR。
- 不在 V1 创建 DirectPay、Payment Method 选择、自动 Reversal、客户端可选 OE/MID 或自由 Metadata。
- 不把 Funds Availability 混进 OrderStatus，也不把订单入账金额解释为当前 Client 余额。
- 不让 Gateway 拥有业务幂等、资源归属或跨系统编排。
- 不因本决策已确认而宣称 Java、OpenAPI、PDManer、SQL、Platform Webhook、Gateway Route、Nginx 或部署已经完成。

## 4. 权威与后继

- 完整 Wire、状态、幂等、Token、数据投影、流程和 Route Manifest 由 [`2026-09-05-commerce-client-api-v1-design.md`](../../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md) 持有。
- Commerce 实施顺序与验证由 [`2026-09-05-commerce-client-api-v1-implementation.md`](../../docs/superpowers/plans/2026-09-05-commerce-client-api-v1-implementation.md) 持有。
- 首个 Create/Get Checkout 本地切片按 [`2026-09-07-commerce-client-checkout-local-mock-slice.md`](../../docs/superpowers/plans/2026-09-07-commerce-client-checkout-local-mock-slice.md) 执行；它不扩展 D12 的授权范围。
- Gateway 通用认证与可信上下文仍由现有 Client API Gateway 决策/设计持有；本决策只取代上文列出的 Location、精确 Route 和 RFC 9457 交叉点。
- 当前实现、模型统计、验证、部署与未完成前置只记录在 [`CURRENT_IMPLEMENTATION.md`](../CURRENT_IMPLEMENTATION.md)。
