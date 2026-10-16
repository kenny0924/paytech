# Commerce Client API Hosted Checkout V1 设计

> **2026-09-16 接入职责后继：** [已确认 Filter 精简](../../../agent-docs/decisions/2026-09-16-commerce-ingress-filter-simplification.md)删除 Commerce 专用 Key、缓存、Order/Refund Query 和可信身份五个 Filter。下文公开 Header 约束保持，Key 处理归上游中间件，缓存头、认证和可信身份注入归 Gateway；Commerce 保留参数绑定与字段约束、资源归属校验和业务防重，不再重复实现这些专用拦截。中间件具体方案与启用状态另行管理。

> **2026-09-16 CreateRefund 后继：** [本入口重构](../../../requirements/20260916/commerce-CreateRefund接口重构/technique.md)取代 Client 创建退款的 Key/Hash/Claim/Lease 与响应快照合同；clientRefundId 同内容返回原创建回执，改内容冲突。Header 仅保留接入格式校验，预占与任务仍原子提交，提交后使用同一 Payment 退款身份；其余操作按各自修订执行。

> **2026-09-16 CancelOrder 后继：** [已确认纠正](../../../requirements/20260916/commerce-CancelOrder接口重构/technique.md)取代本文取消接口的请求 Key/Hash/Claim/Lease、恢复阶段和响应快照合同；Header 只做接入校验，业务按订单原事实防重，取消响应移除内部 version。Payment 终止权威与确定性下游终止标识不变，其他操作不在本次范围。

- Decision status: Confirmed
- Written spec review: Completed
- Decision date: 2026-09-05
- Implementation, verification and deployment: see [`CURRENT_IMPLEMENTATION.md`](../../../agent-docs/CURRENT_IMPLEMENTATION.md)
- Decision record: [`2026-09-05-commerce-client-api-v1.md`](../../../agent-docs/decisions/2026-09-05-commerce-client-api-v1.md)
- Scope: Commerce-owned Client Server M2M API for Hosted Checkout, Order, Cancel and Refund
- Path amendment: 第 3.1、4 节服务前缀及下游路径已同步 [2026-09-12 用户修正](../../../agent-docs/decisions/2026-09-12-commerce-client-gateway-api-path.md)；Route 表表达后续接入目标，本次没有修改 Gateway 或注册配置。
- Partially superseded sections: 第 2.1、2.2 节及第 17 节结论 2 中禁止外部 Java 契约模块、DTO 只落 Web 的部分由 [`2026-09-05-commerce-client-api-contract-module-design.md`](./2026-09-05-commerce-client-api-contract-module-design.md) 取代；其余 Wire 与业务语义继续有效
- Error-contract supersession: 第 3.4 节 Request ID 通用格式及第 12 节 Problem `type/requestId/timestamp/errors` 形状由 [`2026-09-06-system-wide-rfc-9457-problem-details-design.md`](./2026-09-06-system-wide-rfc-9457-problem-details-design.md) 取代；本设计的 Commerce 业务 Code/Status 映射继续有效
- Recovery-stage supersession: 第 5.5 节中 Create Checkout 的五阶段定义由 [`2026-09-09-commerce-checkout-recovery-simplification.md`](../../../agent-docs/decisions/2026-09-09-commerce-checkout-recovery-simplification.md) 取代，现行恢复检查点为 `CLAIMED -> ORDER_COMMITTED -> COMPLETED`；Cancel 与 Create Refund 不变，下文旧阶段文字仅保留为历史设计证据
- Implementation-ownership alignment: 第 7 节业务合同继续由 Commerce 拥有；通用 `t_command_idempotency` Mapper/Repository、Claim/Lease/CAS 和写事务参与边界由 [`2026-09-09-foundation-command-idempotency.md`](../../../agent-docs/decisions/2026-09-09-foundation-command-idempotency.md) 实现。该决定不把幂等转移给 Gateway，也不改变 Hash、Snapshot、阶段、响应、业务事务或数据库所有权

本文冻结 Commerce 首个 Client API 的外部 Wire Contract、公开状态、命令幂等、Checkout Token、资金可用性投影、Webhook Payload、Route Manifest、模块落位和跨系统恢复语义。它不代表 Java、OpenAPI、PDManer、SQL、Gateway、Platform Webhook、Nginx 或部署已经完成。

## 1. 目标、边界与设计依据

### 1.1 调用者和业务边界

本 API 的调用者是 Client 自己的服务端，不是 Consumer：

```text
Client Server
  -> Client API domain / Nginx
  -> API Gateway (CLIENT_API Credential + Scope)
  -> commerce-service / paytech-commerce-web
  -> CCT, Risk, Finance and Payment private APIs

Client Server
  -> gives checkoutUrl to Consumer
  -> Hosted Checkout domain / CHECKOUT Channel

Commerce business fact
  -> Platform Support Webhook Event/Delivery
  -> Client Webhook Endpoint
```

因此：

- Client API Credential 永不进入浏览器或 Hosted Checkout；
- Checkout Token 不能调用 Client API，也不能 Cancel、Refund 或读取 Client 受限数据；
- Consumer 的接受、上下文、网络证据和支付动作继续走 `CHECKOUT` Channel，不复用本 API；
- Gateway 只做入口认证、Scope、Guard、路由和可信上下文，Commerce 拥有资源授权、状态机、事务与幂等；
- Client API 是逻辑协议面，不创建 `client-api-service` 或第七个核心系统。

### 1.2 V1 范围

V1 包含：

- 创建一个 Hosted Checkout；
- 查询 Checkout 和 Order 当前视图；
- 严格取消未支付 Order；
- 查询、创建和跟踪全额或部分 Refund；
- 查询该 Order 的资金是否已经成熟进入 Client 可用余额；
- 接收 Order/Refund 业务结果 Webhook。

V1 不包含：

- DirectPay 或 `/payments` 创建；
- Client 选择 Payment Method、Provider、Provider Account、MID 或 Operating Entity；
- PAN、CVV/CVC、完整磁道、PIN/PIN Block；
- 任意 Metadata、自由扩展字段、Consumer PII、每单成功/取消 URL；
- Client 发起 Reversal、Void、Dispute 或 Payout；
- 无过滤的 Order/Refund 全量列表。

未来 DirectPay 必须使用独立 `/api/v1/payments` 资源和独立设计，不能向已经发布的 Checkout Body 填入支付执行字段。

### 1.3 外部参考的采用方式

本设计采用成熟支付 API 中已经被实际验证的模式：

- Stripe 把 `Idempotency-Key` 与业务资源 ID 分离，并在相同 Key 参数不一致时拒绝；本项目进一步把恢复阶段和首次业务时点留在 Commerce；
- Stripe 的 Webhook 明确可能乱序、重复，重试时产生新的签名时间；本项目使用稳定 Event ID、资源版本和 GET 最新状态；
- Checkout.com 的 Server-to-server API 使用 Bearer Credential，Webhook 要求签名、快速 2xx 确认并允许 Retry/Replay；本项目仍由 Platform Support 持有 Endpoint/Secret/Delivery；
- RFC 9457 允许 Problem Details 扩展成员，也允许省略可由 HTTP 状态行得到的 `status`。

参考资料：

- [Stripe idempotent requests](https://docs.stripe.com/api/idempotent_requests)
- [Stripe Webhooks](https://docs.stripe.com/webhooks)
- [Checkout.com API authentication](https://api-reference.checkout.com/)
- [Checkout.com Webhook acknowledgment](https://support.checkout.com/hc/en-us/articles/29686471128722-How-to-acknowledge-my-server-received-a-webhook)
- [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html)
- [RFC 8785 JSON Canonicalization Scheme](https://www.rfc-editor.org/rfc/rfc8785.html)

这些资料只提供外部工程证据，不改变 Code-MoR 的六系统事实 Owner、状态机或安全边界。

## 2. 部署、模块和代码落位

### 2.1 不拆新服务或业务 JAR

本 API 进入当前 Commerce 模块化单体：

- 一个 `commerce-service`；
- 一个 Commerce 数据库；
- 一个 `paytech-commerce-web` 部署单元；
- Order、Checkout、Refund 仍是 Web 内逻辑业务包；
- 不为外部 OpenAPI 创建 `paytech-commerce-api`；该 JAR 只在真实内部 Java HTTP 消费者出现时创建。

语言无关契约固定保存为：

```text
commerce-service/api/openapi/client-v1.yaml
```

### 2.2 Java 包

外部适配器放在：

```text
com.paytech.commerce.ext.rest.external.clientapi.v1
├── checkout
├── order
├── refund
└── error
```

建议具体类：

- `ClientCheckoutController`
- `ClientOrderController`
- `ClientRefundController`
- 各能力独立的 `*RequestBody` / `*ResponseBody`，不跨入 Domain，不与内部 HTTP DTO 复用
- `CreateCheckoutProcessor`
- `CheckoutQueryProcessor`
- `OrderAggregateQueryProcessor`
- `CancelOrderProcessor`
- `CreateRefundProcessor`
- `RefundQueryProcessor`

业务 Service 和 Domain Service 均使用 `interface + *Impl`；Repository 使用 `interface + *RepositoryImpl`；Processor 和 Controller 保持具体类。跨 Order/Checkout/Refund 的写事务和聚合查询由 Processor 编排，Controller 不直接拼 Repository。

### 2.3 数据访问 Owner

目标 15 表仍全部属于 `paytech-commerce-web`。逻辑 Owner 为：

| Owner | 表 |
|---|---|
| Order | `t_commerce_order`、`t_order_context`、`t_order_legal`、`t_order_item`、`t_order_payment_projection`、`t_order_funds_availability_projection` |
| Checkout | `t_checkout_session`、`t_consumer_context`、`t_consumer_contract`、`t_checkout_network_evidence`、`t_consumer_acceptance` |
| Refund | `t_order_refund_position`、`t_refund_request` |
| Web 服务级可靠性 | `t_command_idempotency`、`t_compensation_event` |

计数为 `6 + 5 + 2 + 2 = 15`。当前生成资产、人工 Schema、Java、物理数据库、验证与部署进度只由 `CURRENT_IMPLEMENTATION.md` 声明，不能从本目标设计推断。

## 3. 通用 HTTP 合同

### 3.1 Base、媒体类型和版本

- 公共路径以 `/api/v1` 开始；服务私网 Controller 路径以 `/client-gateway-api/v1` 开始，应用 Context Path 保持 `/commerce`。
- 成功 Body 使用 `application/json`；错误 Body 使用 `application/problem+json`。
- 公共响应由 Gateway 重建 `X-Request-Id`、`Cache-Control: no-store` 和 `Pragma: no-cache`；Commerce 私网 Client API Adapter 不再通过专用 Filter 重复强制缓存头，直连服务不具有该公网缓存头保证。
- Client API 不启用 CORS，不把 API Key 放入 Cookie，不提供浏览器 Preflight 旁路。
- `POST` 接受 `Content-Type: application/json`；所有接口可接受 `Accept: application/json, application/problem+json`。

V1 内允许增加可选字段。Client 必须忽略未知 JSON 字段；公开状态、原因码和 Event Type 按可扩展字符串读取，未知值不能被解释为成功。删除字段、改变含义、改变必填性或改变已有状态语义属于破坏性变化，进入 `/api/v2` 或新的 Webhook `schemaVersion`。

### 3.2 认证和可信身份

外部请求使用：

```http
Authorization: Bearer <client-api-key>
```

Gateway 通过 PLT-I03 建立 typed Client API identity，只向 Commerce 转发：

```text
X-Request-Id
X-Gateway-Client-Id
X-Gateway-Credential-Id
```

Commerce 只信任私网 Gateway 注入的 Client ID，不从 Path、Query 或 Body 接受 Client/Tenant/Credential/Scope。Credential ID 只用于安全审计和诊断，不参与业务幂等命名空间；Credential 轮换不能改变幂等结果或资源归属。

Gateway 负责清理外部伪造身份 Header 并注入可信身份。Commerce API 保留两个身份 Header 的必填、long 类型绑定及 `@Positive` 约束，资源查询仍按 Client ID 限定；不再由 `ClientApiTrustedContextFilter` 重复检查 Header 单值或填充 `clientId/credentialId` MDC。Request ID 链路保持，删除 Filter 不代表另一个组件已接管这两个日志字段。

### 3.3 Scope

| Scope | 接口 |
|---|---|
| `checkouts.write` | `POST /api/v1/checkouts` |
| `orders.read` | 两个 Order GET 与 Checkout GET |
| `orders.cancel` | Cancel POST |
| `refunds.read` | 可退款金额、Refund 单资源查询和 Order Refund 列表 |
| `refunds.write` | 创建 Refund |

V1 不提供通配 Scope。Credential 可以组合多个明确 Scope。Webhook 配置属于 Platform Support；未来需要外部管理接口时单独使用 `webhooks.read/write`，不加入 Commerce Controller。

### 3.4 Request ID、幂等 Header 和标识

- `X-Request-Id` 遵循 Gateway 已批准的单值、1 至 64 字符规则，只作当前 HTTP 尝试关联。
- 每个 POST 必须带一个 `Idempotency-Key`；Header 名大小写不敏感，值大小写敏感。
- Key 长度 1 至 255，只允许首字符字母或数字，其余为字母、数字、`.`、`_`、`:`、`-`；缺失为 `IDEMPOTENCY_KEY_REQUIRED`，非法为 `INVALID_IDEMPOTENCY_KEY`。
- 上一条是公开接入协议，由上游中间件承担；Commerce 保留 Header 参数声明，不保留专用 Key Filter。框架缺参错误与接入层专有错误不混为同一实现保证；本次未启用请求幂等中间件。
- `orderNo`、`refundRequestNo`、`clientOrderId`、`clientRefundId` 和 Cursor 都大小写敏感。
- `clientOrderId` / `clientRefundId` 最长 128 个可打印 ASCII 字符，不允许控制字符或空白；Query 使用时必须正确 URL encode。
- `orderNo` / `refundRequestNo` 最长 64，Client 必须把它们当作不可解析的 opaque string。

目标数据库中 `order_no`、`refund_request_no`、`client_order_id`、`client_refund_id`、`client_item_id`、`idempotency_key`、`public_token` 和 `response_location` 都使用 `CHAR/VARCHAR ... CHARACTER SET ascii COLLATE ascii_bin`，使唯一索引与 Wire 的大小写敏感语义一致；当前生成模型尚未具备这些逐字段 Collation，是数据库实施差异。普通名称/描述仍使用项目默认 Unicode 字符集。

### 3.5 金额、数量、币种和时间

金额和数量都是 JSON 字符串：

```json
{
  "quantity": "2",
  "unitAmount": "19.95",
  "totalAmount": "42.90"
}
```

规则：

- 禁止 JSON number、正负号、科学计数法、`NaN`、`Infinity`、分组符号和前后空白；
- Java 使用 `BigDecimal`，金额持久化使用 `DECIMAL(24,8)`；
- 金额非负，Refund amount 和 quantity 必须大于零；
- 金额在规范化后不得超过 ISO 4217 币种 exponent，响应按该 exponent 固定输出；
- quantity 最多 8 位小数，总精度不超过 24；
- 同值的 `"10"`、`"10.0"`、`"10.00"` 在请求 Hash 中等价；
- V1 运行配置只启用 USD，但校验器、模型和 OpenAPI 不把金额实现成整数 cents；
- 国家使用 ISO 3166-1 alpha-2 大写，币种使用 ISO 4217 alpha-3 大写；
- 所有外部时刻使用 UTC RFC 3339，例如 `2026-09-05T10:30:00Z`；数据库使用 UTC `DATETIME(6)` / Java `Instant`。

## 4. 冻结 Route Manifest

真实 Owner Target 固定为：

```text
target_service_code   = commerce-client-api
discovery_service_name = commerce-service
approved_path_root    = /commerce/client-gateway-api/v1
channel               = CLIENT_API
security_profile      = CLIENT_API_CREDENTIAL
authorization_mode    = API_SCOPE
request_cookie_mode   = DROP
response_cookie_mode  = NONE
body_receive_timeout  = 5000 ms
response_timeout      = 10000 ms
```

精确 Route：

| Route code | Method | External path | Downstream path | Scope | Query allowlist | Body bytes |
|---|---|---|---|---|---|---:|
| `client-api-commerce-checkout-create` | POST | `/api/v1/checkouts` | `/commerce/client-gateway-api/v1/checkouts` | `checkouts.write` | none | 16384 |
| `client-api-commerce-checkout-read` | GET | `/api/v1/orders/{orderNo}/checkout` | `/commerce/client-gateway-api/v1/orders/{orderNo}/checkout` | `orders.read` | none | 0 |
| `client-api-commerce-order-read` | GET | `/api/v1/orders/{orderNo}` | `/commerce/client-gateway-api/v1/orders/{orderNo}` | `orders.read` | none | 0 |
| `client-api-commerce-order-read-client-id` | GET | `/api/v1/orders` | `/commerce/client-gateway-api/v1/orders` | `orders.read` | `clientOrderId` | 0 |
| `client-api-commerce-order-cancel` | POST | `/api/v1/orders/{orderNo}/cancel` | `/commerce/client-gateway-api/v1/orders/{orderNo}/cancel` | `orders.cancel` | none | 0 |
| `client-api-commerce-refundable-read` | GET | `/api/v1/orders/{orderNo}/refundable-amount` | `/commerce/client-gateway-api/v1/orders/{orderNo}/refundable-amount` | `refunds.read` | none | 0 |
| `client-api-commerce-refund-create` | POST | `/api/v1/orders/{orderNo}/refunds` | `/commerce/client-gateway-api/v1/orders/{orderNo}/refunds` | `refunds.write` | none | 4096 |
| `client-api-commerce-refund-read` | GET | `/api/v1/refunds/{refundRequestNo}` | `/commerce/client-gateway-api/v1/refunds/{refundRequestNo}` | `refunds.read` | none | 0 |
| `client-api-commerce-refund-read-client-id` | GET | `/api/v1/refunds` | `/commerce/client-gateway-api/v1/refunds` | `refunds.read` | `clientRefundId` | 0 |
| `client-api-commerce-order-refunds-list` | GET | `/api/v1/orders/{orderNo}/refunds` | `/commerce/client-gateway-api/v1/orders/{orderNo}/refunds` | `refunds.read` | `limit,cursor` | 0 |

POST Header allowlist 为 `Accept`、`Content-Type`、`Idempotency-Key`；GET 为 `Accept`。`X-Request-Id` 和两个可信身份 Header 由 Gateway 固定管线处理，不进入可配置 allowlist。公开入口要求 Query 名称精确匹配，未知 Query、重复单值 Query、GET Body 或 Cancel Body 返回 `400 INVALID_REQUEST`；这些协议限制由上游接入层承担。按 [2026-09-16 决定](../../../agent-docs/decisions/2026-09-16-commerce-ingress-filter-simplification.md)，Commerce 已不再用 Order/Refund 专用 Filter 重复拦截，服务直连不能套用这些公开入口错误保证；业务字段及资源归属仍由 Commerce 校验。

创建成功的 `Location` 只允许：

- 响应状态为 `201`；
- 恰好一个 ASCII absolute-path reference；
- 以 `/api/v1/` 开始；
- 不包含 scheme、authority、反斜线、控制字符、Query 或 Fragment。

Gateway 校验后保留该 Header；非法 `Location` 把 Owner 响应视为上游合同错误，而不是向 Client 透传或静默改写。其他 Owner Header 继续遵循 Client API Gateway 的固定响应策略。

## 5. 创建 Hosted Checkout

### 5.1 请求

```http
POST /api/v1/checkouts
Authorization: Bearer <client-api-key>
Idempotency-Key: 7a67ff64-4d82-4dc0-a138-b5169a3bf838
Content-Type: application/json
```

```json
{
  "clientOrderId": "checkout-20260905-0001",
  "currency": "USD",
  "consumerCountryCode": "US",
  "expiresAt": "2026-09-05T10:30:00Z",
  "items": [
    {
      "clientItemId": "line-1",
      "name": "Premium subscription",
      "description": "One-time purchase",
      "productTypeCode": "DIGITAL_SERVICE",
      "taxCategoryCode": "STANDARD",
      "quantity": "2",
      "unitAmount": "19.95",
      "discountAmount": "2.00"
    },
    {
      "clientItemId": "line-2",
      "name": "Setup service",
      "productTypeCode": "SERVICE",
      "taxCategoryCode": "STANDARD",
      "quantity": "1",
      "unitAmount": "5.00"
    }
  ]
}
```

字段合同：

| 字段 | 必填 | 规则 |
|---|---|---|
| `clientOrderId` | 是 | Client 内永久唯一，最长 128 |
| `currency` | 是 | ISO 4217；V1 只启用 USD |
| `consumerCountryCode` | 是 | ISO alpha-2 大写 |
| `expiresAt` | 否 | 首次操作时点后 5 分钟至 24 小时；缺失默认 30 分钟 |
| `items` | 是 | 1 至 100 行；原始顺序被冻结 |
| `clientItemId` | 是 | Order 内唯一，最长 128 |
| `name` | 是 | 非空，最长 255 |
| `description` | 否 | 最长 1024，不得包含敏感信息 |
| `productTypeCode` | 是 | Commerce Product Type Catalog，最长 64 |
| `taxCategoryCode` | 是 | Finance Tax Category Catalog，最长 64 |
| `quantity` | 是 | 正十进制字符串，精度不超过 24、小数位不超过 8 |
| `unitAmount` | 是 | 非负 Money string |
| `discountAmount` | 否 | 整行优惠总额，缺失为零；不得超过该行毛额 |

`unitAmount` 是税前单位金额。Commerce 按原始行序冻结：

```text
lineGrossAmount = quantity * unitAmount
lineNetAmount   = lineGrossAmount - lineDiscountAmount
merchandiseAmount = sum(lineGrossAmount)
discountAmount    = sum(lineDiscountAmount)
netAmount         = sum(lineNetAmount)
totalAmount       = netAmount + taxAmount + roundingAmount
```

每次运算使用不丢精度的 `BigDecimal`；只有 Finance 批准的税务计算与币种舍入步骤生成 `taxAmount` / `roundingAmount`。Client 不提交任何汇总金额，避免两个金额权威。

### 5.2 不接受的字段

V1 明确不接受：

- `clientId`、`tenantId`、`credentialId`；
- `locale`，固定使用 `en-US`；
- `industryCode`、`mccCode`、`classificationId`；
- `operatingEntityId`、`providerId`、`providerAccountId`、`midId`；
- `paymentMethod`、支付工具或卡数据；
- Consumer 姓名、Email、Phone、地址、IP 或 User-Agent；
- `taxAmount`、`totalAmount` 等 Client 计算汇总；
- `successUrl`、`cancelUrl`、`returnUrl`；
- 任意 `metadata`。

Consumer 完成或失败后先进入 Code-MoR 统一结果页。未来若有真实业务需要，可在同一版本增加一个经过 Client 级 allowlist 校验的可选 `returnUrl`；本设计不提前创建该字段或数据库列。

### 5.3 Primary Classification 与依赖失败

Commerce 使用首次冻结的 `operationContextTime` 调用 CCT-I13。CCT 必须服务端选择 Client 唯一有效的 Primary Business Classification，并在同一个 V5 Bundle 返回精确 `industryCode` 和 `mccCode`。Commerce 不从任意列表取第一项，也不自行选择或回退默认分类。

以下情况返回 `422 CLIENT_CONFIGURATION_NOT_READY`，且不创建可支付 Checkout：

- 没有有效 Primary Classification；
- 同一时点存在多个 Primary；
- Primary 没有唯一有效 Industry/MCC；
- CCT Bundle、Operating Entity、Agreement、Legal、Integration Readiness 或 Routing Coverage 未就绪。

CCT/Risk/Finance 的技术超时或不可用使用 503/504，不伪装成业务未配置。已进入幂等 Claim 后，稳定业务失败可以重放；技术暂态保持可恢复。

### 5.4 `expiresAt`

首次 Claim 冻结 `operationContextTime`：

```text
effectiveExpiresAt = request.expiresAt ?? operationContextTime + 30 minutes
```

显式值必须满足：

```text
operationContextTime + 5 minutes
  <= effectiveExpiresAt
  <= operationContextTime + 24 hours
```

请求省略和显式提供是不同的语义请求；同一 Key 重试必须保持原始“省略/提供”形状。省略时永远复用首次计算值，不能因为恢复发生在稍后而延长 Checkout。

### 5.5 创建流程和恢复边界

创建流程分成可恢复阶段，远程调用不持有 Commerce 行锁：

1. 短事务 Claim `Client + CREATE_CHECKOUT + Idempotency-Key`，保存规范请求 Hash、`operationContextTime`、预分配 `orderId/orderNo`，并保存版本化、已验证、规范化且不超过本 Route 16 KiB 上限的 `request_snapshot_json`；该快照保留 `expiresAt` 是否由 Client 显式提供，并冻结最终有效值，因此 Root 建立前也可由后台恢复。
2. 在事务外以同一商业时点调用 CCT-I13、Risk Checkout Decision 和 Finance Tax Calculation；稳定 DENY/配置错误记录稳定失败，技术失败保留恢复状态。
3. 一个 Commerce 本地事务创建 Order、Order Context、Legal、Items、Refund Position、Funds Availability=`NOT_APPLICABLE`、Checkout Session=`PREPARING` 及需要的可靠执行记录。
4. 提交后以预分配 Order 和确定性下游幂等键调用 PAY-I01 创建唯一 Payment Intent。
5. Payment 确认 Intent 可用后，一个本地事务绑定 Payment Projection，并以 CAS 把 Order/Checkout 打开。
6. 只有此时返回原始 `201` 创建回执；如果下游结果未知，返回 `503 CHECKOUT_CREATION_IN_PROGRESS`，Client 必须以同一 Key 重试。

崩溃恢复永远复用同一 Order、业务时点、`expiresAt`、Context、Token 和 PAY-I01 子幂等键。已经创建的 Order 不删除、不替换，也不自动生成新 `clientOrderId`。

`process_status` 固定为 `PROCESSING | SUCCEEDED | FAILED`。Create Checkout 的 `recovery_stage` 固定为 `CLAIMED | AUTHORITIES_RESOLVED | ORDER_COMMITTED | PAYMENT_INTENT_CONFIRMED | COMPLETED`；Cancel 为 `CLAIMED | PAYMENT_PENDING | COMPLETED`；Create Refund 为 `CLAIMED | REFUND_COMMITTED | PAYMENT_REFUND_BOUND | COMPLETED`。稳定业务失败进入 `FAILED` 并冻结错误回执，技术暂态保持 `PROCESSING`；Worker 只按持久化 Snapshot/Stage/Root 推进，不依赖原 HTTP 进程内对象。

### 5.6 成功响应

```http
HTTP/1.1 201 Created
Location: /api/v1/orders/ord_20260905_A7M4Q9
Content-Type: application/json
X-Request-Id: req_01K4...
Cache-Control: no-store
```

```json
{
  "orderNo": "ord_20260905_A7M4Q9",
  "clientOrderId": "checkout-20260905-0001",
  "checkoutUrl": "https://checkout.code-mor.com/c/chk_0ujsswThIGTUYm2K8FjOOfXtY1K5TI",
  "currency": "USD",
  "amounts": {
    "merchandiseAmount": "44.90",
    "discountAmount": "2.00",
    "netAmount": "42.90",
    "taxAmount": "0.00",
    "roundingAmount": "0.00",
    "totalAmount": "42.90"
  },
  "expiresAt": "2026-09-05T10:30:00Z"
}
```

这是不可变创建回执，不包含 Order/Checkout/Payment 当前状态。相同 Key 的成功重放始终返回同一 `201`、`Location` 和语义相同的 Body，即使 Order 后来已支付、取消或过期；最新状态只能通过 GET 获取。`t_command_idempotency` 在成功事务冻结 `response_http_status`、相对 `response_location` 和不超过 4 KiB 的受控 `response_body_json`，因此重放不从后来可变的 Order 状态或当前公共域名配置重建回执；JSON 字节顺序本身不属于合同。该受控快照包含具有 Hosted Checkout 访问能力的 URL，不得把它误标为非敏感通用日志或事件材料。

## 6. Checkout Public Token

### 6.1 Wire 格式

```text
chk_<27-character KSUID core><3-character checksum>
```

固定总长度：

```text
4 prefix + 27 KSUID + 3 checksum = 34 characters
```

Base62 alphabet 精确为：

```text
0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz
```

整个 Token 大小写敏感；Java 使用 `String.equals`，Nginx、Gateway、浏览器和数据库均不得 lowercase 或使用大小写不敏感比较。

### 6.2 生成算法

2026-09-16 后继：生成收敛为 Foundation `PublicTokenGenerator.generate(String... mixingValues)`，内部取当前系统 UTC 秒，
返回 30 位无前缀主体。Commerce 传入大写国家码及 Client ID 十进制文本后拼接 `chk_`；16 字节随机 Payload 与有边界编码的
混入值 SHA-256 前 16 字节 XOR。详见[公共 Token 合同](../../../paytech-foundation/paytech-common-core/PUBLIC_TOKEN.md)。
Wire、CRC、持久化及授权规则不变。Token 生成时间不再由业务创建时间传入，也不参与订单有效期判断。

以下逐字节 country/clientId 混入步骤保留为历史生成算法，仅用于解释第 16.4 节旧固定向量；新生成以以上后继为准：

1. 按标准 KSUID 生成 20 bytes：4-byte KSUID seconds + 16-byte CSPRNG payload。
2. `consumerCountryCode` 先规范为两个大写 ASCII bytes。
3. 正值 `clientId` 转为 8-byte big-endian two's-complement representation。
4. 只修改随机 Payload：

```text
payload[0] ^= country[0]
payload[1] ^= country[1]
payload[2] ^= clientIdBytes[0]
...
payload[9] ^= clientIdBytes[7]
```

5. 保持 4-byte timestamp 不变，把修改后的完整 20 bytes 按上述 alphabet 编码为固定 27 字符 KSUID Core。
6. 对同一 20 bytes 计算 CRC-16/CCITT-FALSE：`poly=0x1021`、`init=0xFFFF`、`refin=false`、`refout=false`、`xorout=0x0000`。
7. 把无符号 CRC 值按同一 Base62 alphabet 编码，左侧以 `0` 补成固定 3 字符。
8. 拼接 `chk_ + core + checksum`。

XOR 只是轻量混入，不可从 Token 解出 country/clientId，也不承担认证、完整性或防伪语义。安全不可猜测性只来自 KSUID 的 128-bit CSPRNG payload；CRC 只发现拼写、截断或大小写错误。

2026-09-16 用户后续确认只生成一次：创建流程在事务前为 Session 生成一个 Token；插入 `uk_checkout_public_token` 发生唯一冲突时整笔创建事务回滚，不切换候选或自动重新生成。事务提交后只使用数据库中已保存的 Token；历史快照中的候选字段仅为旧数据读取保留。

### 6.3 数据库

`t_checkout_session` 使用：

```sql
public_token CHAR(34)
  CHARACTER SET ascii
  COLLATE ascii_bin
  NOT NULL
```

唯一索引固定为：

```text
uk_checkout_public_token(public_token)
```

同一行另冻结 `public_origin VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin`。它必须来自服务端 allowlist，生产值为无尾随斜线、无 Path/Query/Fragment 的 HTTPS Origin；`checkoutUrl = public_origin + "/c/" + public_token`。Public Origin 在 Session 生命周期内不可改，环境迁移必须保持旧 Origin 至所有未过期 Session 失效。

删除 `public_token_hash` 和旧 `uk_checkout_token`。不增加 Token Hash、HMAC Key Version、Vault Token、KMS Ref 或 Secret 生命周期。用户已明确接受该 Hosted Checkout Token 出现在第一方 URI Access Log 的风险；因此本 V1 不要求对 `/c/{token}` 路径做摘要化，但日志仍受最小访问与 Retention 约束，代码不得把 Token 主动复制到异常、Metric Label、MQ、Webhook、BCR、Client API Response Body 日志或无关审计 Payload。这里是经确认的窄风险取舍，不代表 Token 没有访问能力，也不放宽 Credential、Webhook Secret、Consumer/Vault Token 或网络原值的日志禁令。

### 6.4 校验和授权

接收 Hosted Checkout Token 时按顺序执行：

1. 长度、`chk_` Prefix、Base62 字符和 CRC 校验；
2. 以大小写敏感唯一索引查询存在性；
3. 校验 Checkout Session 与 Order 的一对一关系；
4. 校验当前 Session/Order 状态和 `expiresAt`；
5. 只开放 Hosted Checkout 当前允许的最小动作。

CRC 合法不表示 Token 由平台签发或当前有效。Token 到期或业务终态后不得发起新支付；在 `expiresAt` 前的终态页面可以只读显示统一结果，到期后 Client 查询不再返回 `checkoutUrl`。

## 7. 命令幂等

### 7.1 Owner 和作用域

幂等由 Commerce `t_command_idempotency` 拥有，不由 Gateway 拥有。唯一作用域：

```text
caller_scope = CLIENT:<trustedClientId>
operation_type = CREATE_CHECKOUT | CANCEL_ORDER | CREATE_REFUND
idempotency_key = exact case-sensitive header value
```

Gateway 只转发 Header，零自动重试、零响应缓存。Credential 轮换不改变 `caller_scope`。

`t_command_idempotency.idempotency_key` 固定为 `VARCHAR(255)`，与外部上限一致。该表另保存 `request_hash_version`、`request_snapshot_schema_version`、可空 `request_snapshot_json`、公开 `resource_no`、HTTP/业务响应字段和 Response Snapshot Schema Version。Create Checkout 与 Create Refund 在 Root 尚未建立时都必须保存已验证的受控 Request Snapshot；Cancel 无 Body，只需冻结 Operation 与 `orderNo`，`request_snapshot_json` 可空。稳定结果除业务 `response_code` 外冻结 HTTP 状态、相对 Location 与最多 4 KiB 的受控创建回执 JSON；暂态结果不封存为最终响应。Checkout 创建回执包含 Public Token URL，因此只能用于原命令恢复，不得复用为 MQ/Webhook 或通用审计 Payload。

### 7.2 语义请求 Hash

Hash 输入包括 Operation、Path 资源标识和全部 Client 可控语义字段，排除 Authorization、Credential、Request ID 和 Idempotency Key 本身。`request_hash_version=1` 固定为：先完成 JSON/Schema/Money 解析，建立保留字段存在性的语义对象，以 RFC 8785 生成 UTF-8 Canonical JSON，再保存其 SHA-256 小写十六进制值。读取旧记录时必须按该记录自己的 Hash Version 重建和比较，未来算法升级不得用新算法误判旧 Key：

- JSON 字段顺序和空白无关；
- Money/quantity 使用规范十进制，`"10"` 与 `"10.00"` 等价；
- Item 数组顺序有业务意义，不能排序；
- 可选字段缺失与显式提供不同；
- Create Checkout 的省略 `expiresAt` 在 Hash 中保持缺失标记，冻结的默认值另存于恢复上下文；
- Cancel V1 无 Body，Hash 仍包含目标 `orderNo`；
- Refund Hash 包含 `orderNo`、`clientRefundId`、amount、reasonCode 和可空 reasonSummary。

认证、Header 语法、JSON 解析和 Schema 校验在 Claim 前失败，不消耗 Key。通过这些检查后产生的稳定 409/422 可以保存并重放；依赖超时、网络未知和仍在处理的状态不保存为永久失败。

### 7.3 精确结果矩阵

| 场景 | Create Checkout | Cancel Order | Create Refund |
|---|---|---|---|
| 同 Key、同请求、已成功 | 原始 201 回执 | 已收敛的 200 或稳定 409 | 原始 201 回执 |
| 同 Key、同请求、暂态 | 恢复原命令；未完成仍 503 | 202 可升级为 200/409 | Refund 已落库即原始 201，后台恢复 |
| 同 Key、不同请求 | 409 `IDEMPOTENCY_KEY_REUSED` | 同左，换 Path 也冲突 | 同左 |
| 新 Key、已有相同业务 ID | 409 `CLIENT_ORDER_ID_ALREADY_EXISTS` | 按 Order 当前状态收敛 | 相同不可变内容返回已有 201 |
| 新 Key、业务 ID 但内容不同 | 同上，不比较历史请求猜等价 | V1 无 Body，不适用 | 409 `CLIENT_REFUND_ID_CONFLICT` |
| 下游超时/未知 | 503 `CHECKOUT_CREATION_IN_PROGRESS` | 202 `PAYMENT_IN_PROGRESS` | 已本地提交时 201，继续后台恢复 |

同 Key 同 Hash 遇到未过期 Lease 时不接管、不重复调用下游：

- 已有 OPEN Checkout：返回原始 201；
- Checkout 仍 PREPARING：503 `CHECKOUT_CREATION_IN_PROGRESS`；
- Cancel 正等待 Payment：202 `PAYMENT_IN_PROGRESS`；
- Refund Request 已存在：返回原始 201；
- 尚无足够持久化事实构造安全响应：409 `IDEMPOTENCY_REQUEST_IN_PROGRESS` 并带 `Retry-After`。

Lease 到期后的 Worker 必须从 `recoveryStage + resourceId/resourceNo + requestSnapshot +` 业务 Root 当前事实接管，不能只依赖旧进程内状态。Create Checkout/Refund 在 Root 建立前使用各自版本化 Request Snapshot；Root 建立后以 Root 和 Snapshot 交叉校验。Cancel 只使用已冻结 `orderNo` 和当前 Root。所有下游调用使用从本命令和资源派生的稳定子幂等键。

## 8. Checkout 与 Order 查询

### 8.1 公开状态

Order：

```text
CREATED | OPEN | PAID | CANCELLED | EXPIRED
```

Checkout：

```text
PREPARING | OPEN | PAYMENT_PROCESSING | COMPLETED | CANCELLED | EXPIRED
```

Payment 聚合：

```text
NOT_STARTED | PROCESSING | SUCCEEDED | FAILED | CANCELLED | EXPIRED
```

Payment 内部 `PROCESSING`、`REQUIRES_ACTION`、`AUTHORIZED` 和 `UNKNOWN` 对外均不能假装终态，其中 `UNKNOWN` 固定映射为 `PROCESSING`。`FAILED` 只表示当前最近尝试失败，Order 仍可能保持 OPEN 并允许 Consumer 在到期前重新尝试。

OrderStatus 不增加 `SETTLED`。资金进入可用余额由独立 `fundsAvailability` 表达。

### 8.2 查询 Checkout

```http
GET /api/v1/orders/ord_20260905_A7M4Q9/checkout
```

```json
{
  "orderNo": "ord_20260905_A7M4Q9",
  "clientOrderId": "checkout-20260905-0001",
  "checkoutStatus": "OPEN",
  "checkoutUrl": "https://checkout.code-mor.com/c/chk_0ujsswThIGTUYm2K8FjOOfXtY1K5TI",
  "expiresAt": "2026-09-05T10:30:00Z",
  "updatedAt": "2026-09-05T10:00:02Z",
  "version": 2
}
```

在 `expiresAt` 前，PAID/CANCELLED 等终态仍由已冻结 `public_origin + public_token` 返回同一 URL，用于 Consumer 查看只读统一结果页；达到 `expiresAt` 后 `checkoutUrl` 固定为 `null`，任何状态都不能再发起支付。

### 8.3 查询 Order

以下入口返回相同单资源表示：

```http
GET /api/v1/orders/{orderNo}
GET /api/v1/orders?clientOrderId={clientOrderId}
```

第二个入口必须恰好出现一个非空 `clientOrderId`；缺失、重复或出现其他 Query 返回 400，而不是退化成列表。

```json
{
  "orderNo": "ord_20260905_A7M4Q9",
  "clientOrderId": "checkout-20260905-0001",
  "orderStatus": "PAID",
  "checkoutStatus": "COMPLETED",
  "paymentStatus": "SUCCEEDED",
  "currency": "USD",
  "consumerCountryCode": "US",
  "items": [
    {
      "clientItemId": "line-1",
      "name": "Premium subscription",
      "description": "One-time purchase",
      "productTypeCode": "DIGITAL_SERVICE",
      "taxCategoryCode": "STANDARD",
      "quantity": "2",
      "unitAmount": "19.95",
      "discountAmount": "2.00"
    }
  ],
  "amounts": {
    "merchandiseAmount": "44.90",
    "discountAmount": "2.00",
    "netAmount": "42.90",
    "taxAmount": "0.00",
    "roundingAmount": "0.00",
    "totalAmount": "42.90",
    "paidAmount": "42.90"
  },
  "lastPaymentAttempt": {
    "status": "SUCCEEDED"
  },
  "refundSummary": {
    "processingAmount": "0.00",
    "succeededAmount": "10.00",
    "refundableAmount": "32.90"
  },
  "fundsAvailability": {
    "status": "PENDING",
    "availableAmount": null,
    "scheduledAt": "2026-09-06T00:00:00Z",
    "availableAt": null,
    "asOf": "2026-09-05T10:05:15Z",
    "updatedAt": "2026-09-05T10:05:16Z",
    "version": 2
  },
  "createdAt": "2026-09-05T10:00:00Z",
  "expiresAt": "2026-09-05T10:30:00Z",
  "paidAt": "2026-09-05T10:05:14Z",
  "updatedAt": "2026-09-05T10:05:16Z",
  "version": 5
}
```

没有 Payment Attempt 时 `lastPaymentAttempt=null`。V1 只返回本地 Payment Projection 已拥有的聚合状态，不返回 Payment Method、Card Brand/Last4、Attempt 时间、Instrument Ref、Provider、Account、MID、Action、Transaction Ref、Risk 结果、Consumer Token 或网络证据；以后只有 Payment 合同和 Commerce 投影都先扩展并证明 Client 消费者时，才可增加这些可选字段。

Order GET 从 Commerce 本地 Order、Payment Projection、Refund Position/Request 和 Funds Availability Projection 组合，不同步扇出 Payment 或 Finance。`asOf/updatedAt` 明确投影可能有短暂延迟；Cancel/Refund 等命令仍在使用点访问实时权威并加锁，不能信任查询快照执行写入。

## 9. Funds Availability Projection

### 9.1 结构

新增 `t_order_funds_availability_projection`。这是已确认业务术语的窄命名例外：表名与主键名超过数据库指南的默认 32 字符建议，但仍低于 MySQL 64 字符硬上限；实施测试必须锁定该精确名称，不能再缩写出第二套术语：

| 字段 | 类型 | 语义 |
|---|---|---|
| `order_funds_availability_projection_id` | BIGINT | 内部唯一 ID |
| `order_id` | BIGINT | 一对一 Order；`uk_order_funds_availability(order_id)` |
| `finance_economics_id` | BIGINT nullable | Finance PAYMENT Economics 稳定引用；非外部字段 |
| `maturity_journal_id` | BIGINT nullable | 完成成熟的 Finance Journal 稳定引用；非外部字段 |
| `funds_availability_status` | VARCHAR(32) | 四态状态 |
| `available_amount` | DECIMAL(24,8) nullable | Finance 最终 `client_matured_amount`；未成熟为空 |
| `currency` | CHAR(3) | 必须与 Order 一致 |
| `scheduled_at` | DATETIME(6) nullable | Finance 冻结的 `balance_available_time` |
| `available_at` | DATETIME(6) nullable | 权威成熟时刻；最终态非空 |
| `economics_event_key` | VARCHAR(128) nullable, ASCII binary | `EconomicsCalculated` 稳定来源事件 Key |
| `economics_source_version` | BIGINT nullable | Economics 来源版本 |
| `economics_source_payload_hash` | CHAR(64) nullable, ASCII binary | Economics Canonical Payload SHA-256 小写十六进制 |
| `maturity_event_key` | VARCHAR(128) nullable, ASCII binary | `PayableBalanceMatured` 稳定来源事件 Key |
| `maturity_source_version` | BIGINT nullable | Maturity 来源版本 |
| `maturity_source_payload_hash` | CHAR(64) nullable, ASCII binary | Maturity Canonical Payload SHA-256 小写十六进制 |
| `as_of` | DATETIME(6) | 已应用来源事件 `occurredAt` 的最大值；不得因迟到事件回退 |
| `version` | BIGINT | 本投影 CAS/乱序保护版本 |
| `create_time` | DATETIME(6) | UTC 创建时间 |
| `update_time` | DATETIME(6) | UTC 更新时间 |

除主键外保留：

- `uk_order_funds_availability(order_id)`；
- `uk_order_funds_economics(finance_economics_id)`；
- `uk_order_funds_econ_event(economics_event_key)`；
- `uk_order_funds_maturity_event(maturity_event_key)`；
- `uk_order_funds_maturity_journal(maturity_journal_id)`。

可空唯一键只在对应 Finance 事实已知时生效，用于阻止同一个 Economics、Journal 或 Event Key 被投到另一个 Order。不增加基于时钟的扫描索引，因为 Commerce 不自行宣布成熟。

### 9.2 状态机

```text
Order created          -> NOT_APPLICABLE + asOf=order.createTime
PaymentSucceeded       -> PENDING
EconomicsCalculated v1 -> PENDING + scheduledAt/economicsId
PayableBalanceMatured v1 -> AVAILABLE      when client_matured_amount > 0
PayableBalanceMatured v1 -> FULLY_OFFSET   when client_matured_amount = 0
```

规则：

- 不允许负 `available_amount`；
- `AVAILABLE` 必须是正金额，`FULLY_OFFSET` 必须是零；
- 最终态不因后续 Refund、Payout、Recovery 或当前账户余额变化而回退；
- `EconomicsCalculated` 的 source tuple 固定为 `source_type=TRANSACTION_ECONOMICS`、`source_id=finance_economics_id`、`source_version=1`；`PayableBalanceMatured` 固定为 `source_type=PAYMENT_ECONOMICS_MATURITY`、`source_id=finance_economics_id`、`source_version=1`，并携带唯一 `maturity_journal_id`。两者都必须包含同一 `order_id + order_context_id + finance_economics_id + currency + balance_available_time`；Maturity 再包含 `client_matured_amount` 和 Journal；
- Commerce 尚未应用匹配的 `PaymentSucceeded`，或 Order/Context/币种不一致时，Finance 事件保持可重试且不 Ack；它不能独立把 Order 宣布为 PAID。Order 已支付后再按各自独立的 Event Key/Source Tuple/Payload Hash 应用；
- `EconomicsCalculated` 晚于 `PayableBalanceMatured` 到达时只核对同一 Economics/时间，不回退状态；
- 同 Event Key + 同 Source Tuple + 同 Payload Hash 重放为 no-op；同 Key 任一 Tuple/Hash 不同、不同 Key 指向同一 Source Tuple、同 Key 跨 Order、Source Version 不是 1、Economics/Journal 唯一引用冲突，均 Fail-Closed 并告警；V1 没有可静默跳过的“低版本”或自动接受的未来高版本；
- 只有 Finance 业务事实可以填写 `scheduledAt/availableAt/availableAmount`，Commerce 不看本机时钟猜成熟；
- `scheduledAt` 和最终 `availableAt` 都等于 Finance 冻结的 `balance_available_time`；`asOf=max(asOf,event.occurredAt)`，本地 `updatedAt` 只表示投影应用时间；
- `AVAILABLE` 对外输出正 `availableAmount`，`FULLY_OFFSET` 输出 `"0.00"`，前两态输出 `null`。

## 10. 取消未支付 Order

### 10.1 请求

```http
POST /api/v1/orders/ord_20260905_A7M4Q9/cancel
Idempotency-Key: 417407a1-b950-4b6f-9577-b42da332e800
```

无 Body、无 `expectedVersion`、无 reason。Commerce 按可信 Client ID + `orderNo` 定位后，在内部读取 Order/Payment 版本并使用 Order 级锁、Payment 权威锁和本地 CAS。

### 10.2 结果

Payment 确认安全终止后：

```http
HTTP/1.1 200 OK
```

```json
{
  "orderNo": "ord_20260905_A7M4Q9",
  "orderStatus": "CANCELLED",
  "checkoutStatus": "CANCELLED",
  "cancelledAt": "2026-09-05T10:08:00Z",
  "version": 4
}
```

Payment 仍有 submitted/UNKNOWN 结果待 Query：

```http
HTTP/1.1 202 Accepted
Retry-After: 3
```

```json
{
  "orderNo": "ord_20260905_A7M4Q9",
  "cancellationStatus": "PENDING",
  "reasonCode": "PAYMENT_IN_PROGRESS"
}
```

发现成功 Authorization/Capture/Sale 或 Order 已经 PAID 时返回 409 `REVERSAL_REQUIRED`。Client API 不暴露 Void/Reversal 命令，Cancel 也不能静默升级。重复 Cancel 使用同一确定性 PAY-I12 子幂等键；已经 CANCELLED 返回相同 200 表示；已经 EXPIRED 或其他不可取消终态返回 409 `ORDER_NOT_CANCELLABLE`，不得改写状态。

## 11. Refund

### 11.1 查询可退款金额

```http
GET /api/v1/orders/ord_20260905_A7M4Q9/refundable-amount
```

```json
{
  "orderNo": "ord_20260905_A7M4Q9",
  "currency": "USD",
  "refundableAmount": "32.90",
  "asOf": "2026-09-05T11:00:00Z",
  "version": 6
}
```

金额等于本地 `refundable_base_amount - reserved_amount - refunded_amount`，已扣除处理中 Refund 的本地预占。它只是带时点的查询结果；创建 Refund 必须再次锁定 Position 并校验。

### 11.2 创建 Refund

```http
POST /api/v1/orders/ord_20260905_A7M4Q9/refunds
Idempotency-Key: 9a3f8966-6899-4056-89ed-ce44ac8a5a28
Content-Type: application/json
```

```json
{
  "clientRefundId": "refund-20260905-0001",
  "amount": "10.00",
  "reasonCode": "REQUESTED_BY_CUSTOMER",
  "reasonSummary": "Customer requested a partial refund."
}
```

规则：

- `clientRefundId` 必填、Client 内唯一、最长 128；
- `amount` 必填、大于零，不提交 currency，始终继承 Order；
- `reasonCode` 必填，初始值为 `REQUESTED_BY_CUSTOMER | DUPLICATE | FRAUDULENT | OTHER`；
- `reasonSummary` 可选、最长 512，不得包含 PAN、Credential、Consumer PII 或 Provider Secret；
- 请求事务锁定 Order Refund Position，以 CAS 原子增加 `reserved_amount` 并创建 `PENDING` Refund Request；
- 本地提交后直接调用 PAY-I04；未知结果保持 PROCESSING 和预占，不通知 Finance，也不释放额度。

本地 Refund 已经创建和预占后即可返回：

```http
HTTP/1.1 201 Created
Location: /api/v1/refunds/rfd_20260905_G3P8C2
```

```json
{
  "refundRequestNo": "rfd_20260905_G3P8C2",
  "clientRefundId": "refund-20260905-0001",
  "orderNo": "ord_20260905_A7M4Q9",
  "amount": "10.00",
  "currency": "USD",
  "createdAt": "2026-09-05T10:45:00Z"
}
```

这是不可变创建回执，不包含当前 Refund Status，并与 Checkout 创建回执一样冻结在命令幂等记录中供原 Key 重放。PAY-I04 后续成功消费预占，失败/取消释放预占；Payment 内部 UNKNOWN 始终映射为 Commerce `PROCESSING`。

### 11.3 查询 Refund

```http
GET /api/v1/refunds/{refundRequestNo}
GET /api/v1/refunds?clientRefundId={clientRefundId}
```

两者返回相同单资源表示：

```json
{
  "refundRequestNo": "rfd_20260905_G3P8C2",
  "clientRefundId": "refund-20260905-0001",
  "requestOrigin": "CLIENT",
  "orderNo": "ord_20260905_A7M4Q9",
  "amount": "10.00",
  "currency": "USD",
  "refundStatus": "SUCCEEDED",
  "reasonCode": "REQUESTED_BY_CUSTOMER",
  "statusReasonCode": null,
  "createdAt": "2026-09-05T10:45:00Z",
  "completedAt": "2026-09-05T10:45:08Z",
  "updatedAt": "2026-09-05T10:45:08Z",
  "version": 3
}
```

Refund Status：

```text
PENDING | PROCESSING | SUCCEEDED | FAILED | CANCELLED
```

Operations 发起的 Refund 返回 `clientRefundId=null`、`requestOrigin=OPERATIONS`。Operations reason summary、内部 Snowflake ID、`channelRefundId`、Provider 引用和 Payment UNKNOWN 不外露。

### 11.4 Order Refund 列表

```http
GET /api/v1/orders/{orderNo}/refunds?limit=20&cursor=<opaque-cursor>
```

```json
{
  "items": [
    {
      "refundRequestNo": "rfd_20260905_G3P8C2",
      "clientRefundId": "refund-20260905-0001",
      "requestOrigin": "CLIENT",
      "orderNo": "ord_20260905_A7M4Q9",
      "amount": "10.00",
      "currency": "USD",
      "refundStatus": "SUCCEEDED",
      "reasonCode": "REQUESTED_BY_CUSTOMER",
      "statusReasonCode": null,
      "createdAt": "2026-09-05T10:45:00Z",
      "completedAt": "2026-09-05T10:45:08Z",
      "updatedAt": "2026-09-05T10:45:08Z",
      "version": 3
    }
  ],
  "nextCursor": null
}
```

- 默认 `limit=20`，最小 1、最大 100；
- 稳定排序为 `create_time DESC, refund_request_no DESC`，目标访问索引为 `idx_refund_order(order_id,create_time,refund_request_no)`；
- Cursor 是大小写敏感、URL-safe、版本化 opaque value，内部只携带上一页最后一行的公开 `createdAt + refundRequestNo`；
- Cursor 不携带内部 ID、Client ID、过滤条件或 PII；Commerce 每次仍从可信 Client ID 和 Path Order 重新建立授权范围；
- 非法/版本未知/与当前 Order 不匹配返回 `400 INVALID_CURSOR`。

## 12. RFC 9457 错误合同

示例：

```http
HTTP/1.1 422 Unprocessable Content
Content-Type: application/problem+json
X-Request-Id: req_01K4...
Cache-Control: no-store
```

```json
{
  "type": "about:blank",
  "title": "Unprocessable Content",
  "detail": "One or more request fields are invalid.",
  "code": "VALIDATION_ERROR",
  "requestId": "req_01K4...",
  "timestamp": "2026-09-05T10:20:30.123456Z",
  "errors": [
    {
      "field": "items[0].unitAmount",
      "message": "The amount must be a non-negative decimal string."
    }
  ]
}
```

`status` 不出现在 Body；RFC 9457 将其定义为可选，HTTP 状态行已经是唯一传输权威。`requestId` 与响应 Header 完全相同；`timestamp` 是当前错误生成时刻。`detail/message` 仅供人阅读，程序只依赖 HTTP 状态和稳定顶层 `code`；`errors[]` 只用于请求 Body 字段校验且不再包含第二层错误码。

稳定映射：

| HTTP | Code |
|---:|---|
| 400 | `INVALID_REQUEST`、`IDEMPOTENCY_KEY_REQUIRED`、`INVALID_IDEMPOTENCY_KEY`、`INVALID_CURSOR` |
| 401 | `UNAUTHENTICATED` |
| 403 | `FORBIDDEN`、`INSUFFICIENT_SCOPE` |
| 404 | `NOT_FOUND`、`ORDER_NOT_FOUND`、`REFUND_NOT_FOUND` |
| 408 | `REQUEST_TIMEOUT` |
| 409 | `IDEMPOTENCY_KEY_REUSED`、`IDEMPOTENCY_REQUEST_IN_PROGRESS`、`CLIENT_ORDER_ID_ALREADY_EXISTS`、`CLIENT_REFUND_ID_CONFLICT`、`REVERSAL_REQUIRED`、`ORDER_NOT_CANCELLABLE` |
| 413 | `REQUEST_TOO_LARGE` |
| 422 | `VALIDATION_ERROR`、`CLIENT_CONFIGURATION_NOT_READY`、`CHECKOUT_NOT_ELIGIBLE`、`REFUND_AMOUNT_EXCEEDS_REFUNDABLE` |
| 429 | `RATE_LIMIT_EXCEEDED` |
| 431 | `REQUEST_HEADERS_TOO_LARGE` |
| 502 | `UPSTREAM_PROTOCOL_ERROR` |
| 503 | `DEPENDENCY_UNAVAILABLE`、`CHECKOUT_CREATION_IN_PROGRESS` |
| 504 | `DEPENDENCY_TIMEOUT` |

错误 Client 访问其他 Client 的 `orderNo` / `refundRequestNo` 与真实不存在使用相同 404、相同对外 detail，不泄露资源存在性。内部异常、SQL、类名、依赖 URI、Credential、Token 和 Provider 原始错误不得进入 Problem Body。

`IDEMPOTENCY_REQUEST_IN_PROGRESS`（409）与 `CHECKOUT_CREATION_IN_PROGRESS`（503）固定携带 `Retry-After: 3`；其他业务错误不因共享 Writer 获得该 Header。`Retry-After` 只在 HTTP Header，不复制到 Problem Body。

## 13. Client Webhook

> **2026-10-15：** 13.5 的传输方式（改为 Platform 内部 HTTP 同步受理）与 13.6 的单签名限制（轮换 1 小时宽限内双签）已由 [Platform Webhook 投递决定](../../../agent-docs/decisions/2026-10-15-platform-webhook-delivery.md) 取代；事件内容与摘要合同不变，本节正文由 Commerce 生产端需求同步。

### 13.1 事件目录

V1 只发送业务结果：

```text
order.paid
order.cancelled
order.expired
order.funds_available
refund.created
refund.succeeded
refund.failed
refund.cancelled
```

不发送 `checkout.completed`、`payment.failed`、Payment Attempt、3DS、Provider Callback 或 Risk 过程事件。Client 不应被迫理解 Code-MoR 内部支付尝试状态机。

### 13.2 Envelope 与 Order 示例

```json
{
  "eventId": "evt_3Bf8K2Qh6N9uA1",
  "eventType": "order.paid",
  "schemaVersion": "1",
  "createdAt": "2026-09-05T10:05:15Z",
  "data": {
    "object": {
      "orderNo": "ord_20260905_A7M4Q9",
      "clientOrderId": "checkout-20260905-0001",
      "orderStatus": "PAID",
      "paymentStatus": "SUCCEEDED",
      "currency": "USD",
      "amounts": {
        "totalAmount": "42.90",
        "paidAmount": "42.90"
      },
      "paidAt": "2026-09-05T10:05:14Z",
      "version": 5
    }
  }
}
```

`eventId` 是大小写敏感的 opaque public ID，在同一 Client 的全部 Webhook 事件命名空间内永久唯一；同一值不得跨 Event Type、Schema Version 或来源资源复用。`createdAt` 是该不可变事件建立时刻，不是每次 Delivery 时间。`data.object.version` 是该对象来源 Root/Projection 的版本：Order 事件比较 Order Version，Refund 事件比较 Refund Version，Funds 事件比较 Funds Projection Version；不同资源族的 Version 不互相比较。

Order Cancel/Expired Payload 使用相同公开 ID、对应 Order/Checkout 状态、结构化 `reasonCode`、业务终态时刻和 Order Version。它们不复制完整 Items 或 Payment Attempt。

### 13.3 Funds 事件

只有最终 `AVAILABLE` 且 `availableAmount > 0` 才发送：

```json
{
  "eventId": "evt_3Bg1M7Qw8R2pC4",
  "eventType": "order.funds_available",
  "schemaVersion": "1",
  "createdAt": "2026-09-06T00:00:02Z",
  "data": {
    "object": {
      "orderNo": "ord_20260905_A7M4Q9",
      "clientOrderId": "checkout-20260905-0001",
      "currency": "USD",
      "fundsAvailability": {
        "status": "AVAILABLE",
        "availableAmount": "38.27",
        "availableAt": "2026-09-06T00:00:00Z"
      },
      "version": 3
    }
  }
}
```

`FULLY_OFFSET` 没有资金进入 AVAILABLE，因此 V1 不发送 `order.funds_available`；Client 可从 Order GET 得到 `FULLY_OFFSET + 0.00`。未来若出现明确的零金额结算通知消费者，应增加新的 Event Type，不能让 `funds_available` 同时表示“没有资金可用”。

### 13.4 Refund 事件

Refund 事件的 `data.object` 与第 11.3 节安全 Refund 表示一致，包括公开 Refund/Order ID、可空 `clientRefundId`、`requestOrigin`、金额币种、Status、Reason、时刻和 Refund Version。`refund.created` 在本地 Request 与预占提交后建立；终态事件只来自 Payment 权威收敛。

不发送 Operations 私有备注、`channelRefundId`、Provider 原始原因、Payment UNKNOWN、Consumer PII 或内部 ID。

### 13.5 Commerce 与 Platform 的 Owner 边界

Commerce 在业务状态变更的同一本地事务冻结：

- 稳定 Event ID；
- Client ID 路由键；
- Event Type、Schema Version、Created At；
- 精确公开 `data.object` 快照；
- Source Resource Type/ID/Version 和 Payload Hash；
- 用于向 Platform 发布的本地可靠执行记录。

同一事务写入 Foundation `compensation_event`：`event_key` 使用稳定公开 Event ID，`event_type=CLIENT_WEBHOOK_REQUESTED`，`sub_event_type` 使用上文八种公开 Event Type。`payload_json` 是不超过 4 KiB 的最小恢复对象，精确包含 `clientId`、`canonicalizationVersion=RFC8785`、`source{system,eventKey,eventType,sourceVersion,payloadSchemaVersion,sourcePayloadHash,payloadHash}` 与公开 `envelope`；所有 64-bit 内部 ID 在 JSON 中编码为十进制字符串。该 Payload 不含 Endpoint/Secret/Checkout Token/PII/网络原值。这是“事件时不可变公开快照无法由后来 Current Root 重建”的明确消费者，因此只在本节范围内取代统一 Compensation Event 通常从权威表重建 Payload 的规则；不得扩展为通用业务事件仓库。

公开 `data.object` 和完整 Envelope 分别按 RFC 8785 JSON Canonicalization Scheme 序列化为 UTF-8 bytes；`sourcePayloadHash=SHA-256(canonicalObjectBytes)`，`payloadHash=SHA-256(canonicalEnvelopeBytes)`，均为小写十六进制。Commerce 到 Platform 以及 Platform 到 Client 都使用这组逻辑对象和 Canonical bytes；MySQL JSON 的属性顺序不参与签名或 Hash。所有标识和可能超过 I-JSON 安全整数范围的值使用十进制字符串，JSON Number 只用于受限的小整数 Schema/Object Version 并不得超过 `2^53-1`。任务保留到 Platform 明确幂等接收后才完成，恢复只重放原 Event ID/Canonical Envelope，绝不从后来可变 Root 重建。

提交后 Commerce 发布 `ClientWebhookRequested`。公开 `eventId` 精确映射 Platform `source_event_key`；Platform 自己的 `t_webhook_event.event_id` 仍是内部 Snowflake，绝不作为公开 Event ID。Platform 按 Client ID 唯一解析 CLIENT Tenant；接收方必须先按 `(tenant_id,source_system,source_event_key)` 串行检查现有事件：已有记录的 `event_type`、`payload_schema_version`、`source_version`、`source_payload_hash` 和 `payload_hash` 全部相同才返回原 Event，任一不同都 Fail-Closed，不能把同一公开 Event ID 作为另一 Type/Schema/资源的新事件插入。现有物理唯一键仍为 `(tenant_id,source_system,source_event_key,event_type,payload_schema_version)`；实施必须按实际 Source Event Key 进行并发协调和本地事务保护，不能锁整个 Tenant。Platform 选择 ACTIVE Subscription/Endpoint Revision，保存 exact Event/Payload，并拥有后续 Delivery、Attempt、Secret、Retry、Replay 和审计。Platform 不需要新增 `source_type/source_id` 字段，不回调 Commerce 重建旧 Payload，也不改写业务字段。

当前六库 `compensation_event` 仍存在 COMP-01 迁移；Commerce 实现不得局部发明第三种可靠表结构。实施前必须使用当时已经统一的 Foundation 合同，并只在上述最小、无敏感数据的恢复 Payload 中保存快照。

### 13.6 签名

```http
Content-Type: application/json
Code-MoR-Signature: t=1788602402,v1=<lowercase-hex-hmac>
```

Platform 把已冻结 Envelope 重新执行 RFC 8785 并发送得到的 UTF-8 bytes；签名输入是该次 HTTP 请求未经 Client 解析或重新序列化的原始 Body bytes：

```text
signedPayload = ASCII(unixTimestampSeconds) + "." + rawRequestBody
signature = HMAC-SHA256(endpointSigningSecret, signedPayload)
```

Client 验证顺序：

1. 读取唯一 `Code-MoR-Signature` Header 并解析一个 `t` 和一个或多个 `v1`；
2. 拒绝相对本机时钟偏差超过 5 分钟的 `t`；
3. 以原始 Body 计算 HMAC，对每个 `v1` 做常量时间比较，任一匹配即通过；
4. 验签成功后才解析 JSON；
5. 以 `eventId` 持久化去重；
6. 只在同资源族 Version 更新时应用，版本跳跃或不确定时 GET 最新状态。

V1 每个 Attempt 只使用一个 ACTIVE Secret，并把现有 `t_webhook_attempt.signing_secret_id` 冻结下来，Header 精确发送一个 `v1`。Client Parser 接受一个或多个 `v1` 是前向兼容语法，不代表当前 Sender 已实现双签；Client 在受控轮换期可同时持有旧、新 Secret，并用二者依次验证收到的单一签名。Retry/Replay 建立新的 Attempt，并选择该时点唯一 ACTIVE Secret；REVOKED Secret 立即不可用于新 Attempt。未来若需要同一 Header 双签，必须另行冻结宽限状态、生命周期、唯一约束、Attempt 关系和测试，不能仅增加第二个列。

Retry/Replay 使用相同 `eventId`、`createdAt` 和 Body，但每个 Delivery Attempt 使用当前尝试时间重新计算 Header 签名。旧事件的 `createdAt` 不参与五分钟容忍窗口。

### 13.7 确认、重复和乱序

- Client 在持久化收到的 Event 后尽快返回任意 2xx；
- 非 2xx、网络失败或 Delivery timeout 进入 Platform Retry；
- 3xx 不跟随，避免 Endpoint 验证后被重定向到未批准地址；
- Retry 和人工 Replay 都保持相同 Event ID；
- Event 可能重复、延迟和乱序，Platform 不承诺跨资源或跨 Event Type 的全局顺序；
- 未知 Event Type 在验签和安全持久化后应返回 2xx 并忽略，避免永久重试；
- 任何 Webhook 只作变更通知，GET 是当前资源状态的恢复权威。

## 14. 业务流程和并发不变量

### 14.1 PaymentSucceeded 与到期

唯一 PaymentSucceeded 在同一个 Commerce 本地事务：

- 幂等更新 Payment Projection；
- 把 Order 置为 PAID、Checkout 置为 COMPLETED；
- 只允许 Refund Position `refundable_base_amount` 从零建立一次；
- 把 Funds Availability 从 NOT_APPLICABLE 置为 PENDING；
- 冻结 `order.paid` Webhook 快照和可靠事件。

重复事件同版本同内容幂等；成功金额、Order Context、Assignment、Client/OE、币种或来源版本冲突时 Fail-Closed。Payment 内部 UNKNOWN 不能让 Commerce 宣布 PAID、EXPIRED 或可安全 Cancel。

到达 `expiresAt` 只阻止新的支付动作。若仍有 submitted/UNKNOWN Provider Action，Commerce 等待 Payment Query 权威收敛；最终只能收敛为 PAID 或在明确无成功支付后 EXPIRED，不能按本机时钟覆盖未知资金结果。

### 14.2 Cancel

Cancel Processor 在事务外调用 PAY-I12，在确定 `TERMINATED` 后才以本地 CAS 同时关闭 Order/Checkout 并冻结 `order.cancelled`。PENDING 不修改终态；REVERSAL_REQUIRED 不创建 Refund、Void 或 Reversal。

### 14.3 Refund

创建时一个本地事务锁定 Order Refund Position、预占额度、创建 Refund Request 和 `refund.created` 快照。PAY-I04 在提交后执行；绑定 Channel Refund 或进入 PROCESSING 使用另一短事务。RefundSucceeded 在一个本地事务消费 reserved、增加 refunded、置 SUCCEEDED 并冻结 Webhook；FAILED/CANCELLED 同事务释放 reserved。远程调用永不位于持有 Refund Position 行锁的长事务内。

## 15. 安全与数据最小化

- PAN、CVV/CVC、完整磁道、PIN/PIN Block 在 OpenAPI Schema、Controller DTO、日志测试和 Simulator 中都必须被显式拒绝或证明不存在；
- 外部响应/Webhook 不返回 Consumer Email/Phone/Profile Token、Network Evidence、Client IP、User-Agent、Vault Token 或派生风险字段；
- 不记录 Authorization、Client API Credential、Webhook Secret、签名原文或 Provider Secret；
- Checkout Token 不属于 Vault Secret，可明文保存并出现在自身 URL；它仍只作为最小 Hosted Checkout Capability，不进入 Client API 认证；
- Query Value、Body、Token 和业务 ID 不作为 Metric Label；
- 所有资源查询先固定可信 Client ID，再定位资源；不得先查询全局资源再返回权限差异；
- API 响应、错误、Webhook 和日志不得泄露内部 Snowflake ID、数据库键、SQL、Provider/MID 或依赖拓扑。

## 16. 实施与激活门禁

### 16.1 Commerce 编码前置

以下合同缺失时，真实 Owner Adapter、公共 Route 激活和端到端可用性保持未实现：

1. CCT-I13 发布服务端解析唯一 Primary Classification 的 typed 合同；
2. Payment 发布 PAY-I01、PAY-I03、PAY-I04、PAY-I12 真实 typed HTTP 合同和状态映射；
3. Finance 确认 `EconomicsCalculated`、`PayableBalanceMatured` 向 Commerce 提供 Order、Economics、共享成熟时间、Client matured amount、币种和来源版本；
4. 六库 COMP-01 完成或执行时已有一个统一可用的 Foundation Compensation Event 合同；
5. Platform 接收 `ClientWebhookRequested` 的 typed/event schema，并按现有单一 `signing_secret_id` 闭合 Event/Delivery/Attempt 合同；
6. Gateway Client API 通用前置按其独立计划达到 integration-ready。
7. Foundation MVC 提供经批准的 Client API RFC 9457 输出策略；Commerce 不得用本地 `@RestControllerAdvice` 或本地 Problem 类型绕开共享 `GlobalExceptionHandler`。旧四字段 `{code,message,requestId,timestamp}` 错误体不能用于本外部协议。

2026-09-07 已批准一个窄的本地首片例外：先实现 Create/Get Checkout，由 Commerce 定义 CCT、Risk、Finance、Payment 最小 `*Client` 应用端口并提供确定性 Local Mock。Mock 只有在显式 `commerce.integrations.mode=local-mock` 且 Active Profile 包含 `local`、`auto` 或 `test` 时才可装配；默认 `disabled` 调用失败关闭，非本地 Profile 误启 Mock 必须启动失败。此例外只验证本地编排、幂等、恢复和 HTTP 合同，不是 Owner wire 合同、生产降级或真实集成；替换点只保留一条明确 TODO。Risk `DENY` 返回稳定 `422 CHECKOUT_NOT_ELIGIBLE`，两种处理中错误按第 12 节返回受控 `Retry-After`。真实 Risk 接入后的结论与错误映射见 [结账点运行期评估](../../../requirements/20261016/risk-结账点运行期评估/design.md) 第 2.2 节（`REVIEW` 同样返回 422，结果未知重试一次后 503）。

本地首片不创建或激活 Gateway Target/Route、Nginx Location、`checkout-web` 或 Consumer `/c/{token}` 页面。它可以返回并冻结合同形式的 Checkout URL，但在页面与公共链路就绪前不得称为可访问或端到端可用。除上述例外外，不得在 Commerce 复制其他 Owner 的 wire DTO、手写未批准 URI、直读其他数据库或用临时匿名/静态旁路代替这些合同。

### 16.2 数据库实施完整性

数据库实施必须在同一获批切片同步：

- `COMMERCE_DATABASE.md`、现实场景和物理决策；
- PDManer Generator/Manifest/Field Metadata/Scenario/Structure Tests；
- `commerce.pdma.json` 及人工 PDManer 4.9.3 检查；
- 手工 Schema SQL 和单一初始化 DML；
- DataObject、Mapper、Repository 与本地 MySQL auto；
- `public_token` 的 `ascii/ascii_bin`、长度和唯一索引验证；
- `t_command_idempotency.idempotency_key VARCHAR(255)`、版本化 Hash/Request Snapshot、公开 Resource No，以及稳定响应状态、相对 Location、最多 4 KiB 受控创建回执 JSON 的恢复验证；
- 15 表目标统计与 `CURRENT_IMPLEMENTATION.md` 的实际状态。

Java 应用不得自动建表、迁移或 Seed；不使用 Flyway、Liquibase 或 Testcontainers。

### 16.3 自动测试

至少覆盖：

- OpenAPI 10 条精确 Route、Scope、Header、Query、成功/错误 Schema 和未知字段兼容；
- Trusted Client Context 缺失/重复、跨 Client 统一 404、no CORS/no-store；
- Money/quantity 边界、币种 exponent、金额等式、Item 顺序和税费快照；
- expiresAt 默认/边界/恢复不漂移；
- Token 固定向量、大小写、CRC 错误、DB binary collation、存在/状态/到期授权；
- 幂等同键重放、异请求冲突、Lease 并发、崩溃恢复、Client ID 与 Credential 轮换；
- CCT/Risk/Finance/Payment 成功、业务拒绝、超时、非法响应、重复与未知结果；
- Cancel 的 200/202/409 以及绝不静默 Reversal；
- Refund 并发预占、相同/different `clientRefundId`、PAY-I04 UNKNOWN/终态和额度守恒；
- Payment/Finance 事件重复、乱序、冲突和 Funds Projection 不回退；
- Webhook Payload 快照、Event ID 稳定、敏感字段缺失和 Platform 请求幂等；
- RFC 9457 无 Body `status/requestId`，Gateway/Owner 错误一致；
- 日志、异常、Metric Tag 和 Simulator Journal 不含 Credential、Secret、PAN/Vault Token/网络原值；Client API Response Body 日志不得复制 Checkout URL，已确认允许的原始 Token 只限第一方 `/c/{token}` URI Access Log。

外部 HTTP/MQ 依赖使用真实 Loopback Stateful Simulator，默认拒绝未声明调用并在每测后 reset/verify。数据库、Redis 和 RabbitMQ 只复用手工启动的仓库 `local-environment`；不可用时测试快速失败并给出准备说明。

### 16.4 Token 固定向量

历史生成向量保留为 Core 编码、CRC 与旧 Token 读取兼容性依据；新生成接口使用系统时间和随机源，不要求从相同混入值复现此 Token：

```text
timestamp          = 2026-09-05T10:00:00Z
unix seconds       = 1788602400
KSUID epoch offset = 388602400
country            = US
clientId           = 40001
initial payload    = 000102030405060708090a0b0c0d0e0f
mixed raw bytes    = 17299a20555202030405060794480a0b0c0d0e0f
KSUID core         = 3Iu73V3AfDlneKkX3g6YoNctElj
CRC16              = 63207
Base62 checksum    = GRT
token              = chk_3Iu73V3AfDlneKkX3g6YoNctEljGRT
```

### 16.5 真实 Route 激活

本设计冻结 Manifest，但不授权激活。Commerce Adapter、OpenAPI、数据库、依赖、Gateway/Platform/Nginx 和端到端测试全部完成后，仍按 Client API Gateway 设计以 DISABLED Target/Route 准备、逐实例 Snapshot 收敛、未分发测试 Credential smoke 和失败回滚。

Create Checkout Route 启用前，独立 `CHECKOUT` Channel 的 `public_origin + /c/{token}` 必须已经可达并通过真实浏览器 Smoke；响应至少使用 `Cache-Control: no-store`、`Referrer-Policy: no-referrer`、限制第三方来源的 CSP，且页面不得向第三方请求或 Referrer 泄露 Token。第一方 URI Access Log 的原值保留是第 6.3 节已确认的窄风险取舍。`checkout-web`、CHECKOUT Gateway Route 或 Public Origin 任一尚未就绪时，不得启用 `POST /api/v1/checkouts`，也不得以返回死链接的 `201` 冒充可用。

未达到全部门禁前最多称为 designed 或 integration-ready，不得称为可用或已部署。

## 17. 已确认结论

1. Client API 服务 Client Server，不服务 Consumer；Hosted Checkout 是另一入口 Channel。
2. Commerce 不拆新服务，不拆 Order/Checkout/Refund Maven JAR；外部 OpenAPI 不进入内部 API JAR。
3. V1 冻结十条 Hosted Checkout/Order/Cancel/Refund API，DirectPay 以后独立设计。
4. 金额使用十进制字符串，业务使用 BigDecimal/DECIMAL，不使用 JSON 浮点。
5. `clientOrderId`、`clientRefundId` 和 `Idempotency-Key` 是三个不同层次的标识。
6. Checkout Token 是大小写敏感、可排序、不可猜测、明文持久化的 34 字符值；CRC 只作结构校验。
7. Order 不增加 SETTLED；资金成熟由 Finance 权威驱动的独立四态投影表达。
8. Cancel 不接收外部 Expected Version，不静默升级 Reversal；内部锁/CAS 继续存在。
9. RFC 9457 Body 不复制 HTTP Status 或 Request ID。
10. Webhook 是稳定不可变快照、at-least-once、签名、可重复和可乱序；Platform 拥有投递，Commerce 拥有业务内容。
