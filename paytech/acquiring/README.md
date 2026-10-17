# Commerce Service

`commerce-service` 是 Commerce 核心服务的独立 Maven Reactor。Checkout 在创建时完成 OE、商业、税务与法律信息初始化，首次提交支付时才创建唯一 Payment Intent。外部 Client 本轮按用户授权使用固定 Mock + TODO；不代表真实 Owner 已接通。
设计见[创建初始化与首次支付](../requirements/20260919/commerce-Checkout准备与Intent创建拆分/technique.md)，实际验证与数据库执行状态见[当前台账](../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-preparation-intent-20260919)。

本轮 Customer 入口已全部收敛在 Commerce，Payment 反向访问/确认/readiness
接口已移除；调用方向及待接合同见 [本轮修订](docs/requirements/20260912-customer-checkout/commerce-entry-amendment.md)。

Customer Checkout 的接口、事务和 Mock 联调方式见 [实施说明](docs/requirements/20260912-customer-checkout/implementation.md)与 [OpenAPI](docs/api/customer-checkout.openapi.json)，实现和验证状态见 [COM-CUSTOMER-01](../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-customer-20260912)。

Customer GET 由 `GetCustomerCheckoutProcessor` 直接按需读取本库对象；法律展示使用三表
`CheckoutLegalView`，不加载定价、风控和税快照。普通类 `CheckoutQueryContext` 持有查询结果，
封装就绪、允许操作和版本比较；消费者回填、原文件链接及 Payment 摘要均在事务外取得。
返回前只重查版本，变化时直接返回冲突，由调用方重新查询；金额复用公共 Money。范围见
[本次重构说明](../requirements/20260916/commerce-GetCustomerCheckout接口重构/technique.md)，
最新验证范围见当前台账。

保存消费者资料使用独立 class Body、内部 Request/Response 和 `SaveConsumerContext`。
`SaveConsumerProcessor` 按表查询并在事务外 Protect；`CustomerCheckoutService.saveConsumerContext`
使用 REQUIRED 事务保存订单版本与消费者资料，以订单、Session、Consumer 的状态和版本保护并发。
保存不再要求 Idempotency-Key，不读写命令表；每次请求按当前状态替换资料，首次保存法律接受后拒绝保存。
公开 Body 字段和路径保持，说明见[本次重构](../requirements/20260917/commerce-SaveConsumer接口重构/technique.md)。

支付提交使用独立普通类 Body、内部 Request/Response 和 `SubmitCustomerPaymentContext`；Processor
按需读取本库对象，直接调用 `CheckoutSubmissionService`，由其 REQUIRED 事务原子保存法律接受与订单版本。
支付不再要求 Idempotency-Key，不计算卡指纹或请求 Hash，不读写命令表；每次生成支付业务编号，提交和查询在事务外调用 Payment。详见[本次重构](../requirements/20260917/commerce-SubmitPayment接口重构/technique.md)。
`SubmitCustomerPaymentProcessor` 使用 Foundation `AbstractLockProcessor`，按原订单 ID 获取 `TRY_LOCK`（立即失败、Watchdog 续租）。逻辑资源 Key 为 `order:{orderId}`，不使用公开 Token；锁前只查询不可变订单身份，锁内按需查询业务状态。创建 Intent 始终在数据库事务外；结果返回后由 `CheckoutSubmissionService.bindPayment` 在同一 REQUIRED 事务插入投影并执行订单 CAS，失败全部回滚，不继续卡支付。取消和资料保存尚使用现有 CAS；锁不取代数据库与 Payment Owner 的唯一性保护。
首建与复用 Intent、事务回滚、同订单锁竞争、不同订单并行、创建超时及取消交错的测试入口见下文。

支付提交查询使用 `CustomerPaymentQueryContext`，会话、订单、支付投影各读取一次，Payment 查询全程无事务。
返回前复用原期限，不重复查库或因订单普通版本变化返回冲突；说明见[GetPayment 重构](../requirements/20260917/commerce-GetPayment接口重构/technique.md)。
查询经 Payment EX-I06（`PaymentIntentApi.getSubmission`，带订单 Client）读取原提交：Payment 404 返回 `PAYMENT_SUBMISSION_NOT_FOUND`，
处理中有交互时为 `REQUIRES_ACTION`（动作 ID 为尝试 ID、无跳转地址），不可用时降级为处理中并继续轮询。

支付成功事件由 `PaymentSucceededListener` 消费：Commerce 声明 `topic_payment_result`（同参数）、Quorum 队列
`cg_acquiring_payment_result`（`payment.succeeded.v1`，投递上限 5）及死信 `dlx_acquiring_payment_result` →
`cg_acquiring_payment_result_dlq`，手动确认。`ApplyPaymentSucceededProcessor` 核对 Client、Intent、OE 与金额后，由
`OrderPaymentService.applySucceeded` 在一个 REQUIRED 事务推进支付投影、订单 PAID、会话 COMPLETED、退款基数和资金投影 PENDING；
同一事件键与 Hash 或同一成功尝试重复投递直接确认，载荷不符、未知订单或重复成功进入死信；已取消/过期订单只记录支付投影并告警。
RabbitMQ 连接取 `COMMERCE_RABBITMQ_*`（测试取 `MOR_RABBITMQ_*`）；Enforcer 只放行 `spring-boot-starter-amqp` 及其 RabbitMQ 客户端。
设计见[支付结果查询与回传](../requirements/20261015/payment-支付结果查询与回传/technique.md)。

## 模块

- `paytech-commerce-customer-api`：Commerce 统一 Customer Checkout 四操作的轻量契约；包括支付提交与原提交查询。
- `paytech-commerce-client-api`：可发布的轻量 Java Owner 入站契约；包含九个 Client API 操作，但不运行服务，
  也不传递 JCS 实现依赖。
- `paytech-commerce-web`：唯一可部署的 Spring MVC 应用；当前实现一条 Checkout、四条 Orders 和四条 Refunds 私有入站路径及其业务、持久化、
  本地 Owner 端口和统一错误映射。
- `paytech-commerce-test`：保存 auto 测试和本地基础设施门禁；不生成可部署 JAR。

业务模块依赖方向为 `test -> web -> client-api`；Web 另外显式按需依赖 Foundation 的
`paytech-redis-starter`、`paytech-compensation-event`、`paytech-http-client-support` 和 CCT `client-contract-api`。这些依赖不进入
Commerce Client API；没有新增无消费者的 `paytech-commerce-api` 模块。

## 当前 Checkout 首片

Spring MVC Controller 直接实现 `ClientCheckoutApi`，不复制 DTO 或重复声明 Mapping：

- `POST /client-gateway-api/v1/checkouts`

应用 Context Path 为 `/commerce`，所以本地直连路径带 `/commerce` 前缀。OpenAPI 中的公共
`/api/v1/...` 路径属于未来 Gateway 映射；当前没有 Target、Route、Nginx Location 或外部可用性声明。

Client API 路径按 [2026-09-12 路径修正](../agent-docs/decisions/2026-09-12-commerce-client-gateway-api-path.md)
统一使用 `/client-gateway-api/v1`；本地创建地址为 `/commerce/client-gateway-api/v1/checkouts`。
Java 契约和 metadata-only 日志配置使用同一前缀语义；当前代码直接替换旧路径，不提供兼容别名。

Create Checkout 当前包含：

- 独立 class Body → `CreateCheckoutConverter` → 内部 `CreateCheckoutRequest/Response`；Processor 复用
  Foundation `AbstractProcessor`，拒绝调用方已有事务，输入关系在 `validate` 校验，主流程依次检查订单号、构建上下文、事务外完成商业/税务/法律初始化、本地保存和响应。
- `CreateCheckoutContext` 直接持有订单、商品、退款仓位、资金投影和包含单个 Token 的 Session；
  `CheckoutCreationService.saveCreateCheckoutContext` 以 REQUIRED 事务一次保存五类基础事实及 Context、Legal、Contract 三份初始化事实，SQL 失败整体回滚。所有外呼始终在事务外。
- Checkout Token 由 Common Core `PublicTokenGenerator.generate(countryCode, clientIdText)` 生成主体，Commerce 拼接 `chk_`；
  公共工具内部使用当前系统 UTC 秒，订单时间与有效期仍由业务管理。每次创建只生成一个 Token，数据库唯一冲突时整笔事务回滚，不切换候选。旧 Token 格式与校验保持，详见[公共 Token 合同](../paytech-foundation/paytech-common-core/PUBLIC_TOKEN.md)。
- 新 `orderNo` 与 `refundRequestNo` 同样直接复用该公共工具，分别拼接 `ord_` 和 `rfd_`；
  三类公开编号均为 4 位前缀加 30 位 Base62 主体，共 34 位。订单混入 Client ID / Client Order ID，
  退款混入 Client ID / Order No / Client Refund ID；每次只生成一次，重复业务申请使用原已保存编号。
  字段名、数据库主键和 Client 归属校验保持，既有编号不重建，历史读取和查询继续接受旧值。
- `clientId + clientOrderId` 永久唯一；顺序重复和并发竞争均返回 `409 CLIENT_ORDER_ID_ALREADY_EXISTS`。
  HTTP `Idempotency-Key` 由 API Gateway 校验与重放（[请求幂等决定](../agent-docs/decisions/2026-09-17-gateway-request-idempotency.md)）；Commerce 不声明、不读取该 Header，也不依赖 `paytech-command-idempotency`。
  创建流程不 Hash、Claim、续租、恢复或冻结响应；相同订单号不会再次创建。
- 创建入口通过请求约束限定 USD，商品 `clientItemId` 在订单内按大小写敏感的原值唯一；不满足时在外部调用和落库前返回 422。金额、行优惠、币种精度与汇总范围同样在外部调用前校验；默认三十分钟，显式期限为五分钟至二十四小时。金额复用 Common Core 的 `Money`，公开字段通过 `toAmountString()` 输出；Commerce 仅保留非负/正数、`DECIMAL(24,8)` 及商品行计算规则。
  商品行与汇总金额由内部 `CreateCheckoutRequest.calculateAmounts()` 纯计算，Processor 触发校验并构建 Context，不额外注入 Calculator。
  订单 INSERT 以期限条件保护写入，慢 CCT 查询不延长期限。创建冻结 OE 和最终金额，返回 `READY/FINAL/OPEN`；当前固定 Mock 返回零税和零舍入，总额等于净额。尚不创建 Payment Intent。
- 新订单在 `t_commerce_order.consumer_country_code` 保存创建国家；查询和支付直接读取该字段。
  不再从 Create Checkout 命令快照回退读取国家；本次已移除独立准备接口、命令表访问和 Handler，不回读旧创建快照。
- Foundation 统一 RFC 9457 错误与 metadata-only 日志；HTTP Body 字段校验和响应格式保持。对外缓存头由 Gateway 统一承担，Commerce 不再用专用 Filter 包装响应。

当前数据结构补充见 [本次增量 SQL](docs/requirements/20260916-create-checkout-business/ddl.sql)，
实际执行和验证状态只见 [当前实施台账](../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-create-checkout-business-20260916)。

## Orders 接口

Client 独立 `getCheckout` 已取消。`GET /client-gateway-api/v1/orders/{orderNo}` 与
`GET /client-gateway-api/v1/orders?clientOrderId=...` 统一返回订单及创建方式 `creationMode`，
收银台信息位于可空的 `checkout`（`status / url / expiresAt`）；`preparationStatus`、`amountStatus` 保留在订单层。
订单根级及 `fundsAvailability` 均不返回内部 `version`。创建响应丢失时，可按原 `clientOrderId`
找回订单及支付链接；链接使用原 Session 的 Origin/Token，到期后返回 null，不新建 Session 或延长期限。
Customer 加载收银台接口保持独立，查询合并不改变它的合同。

本期范围和边界见 [Orders 实施说明](docs/requirements/20260912-orders-api/implementation.md)。
`ClientOrderController` 实现订单查询与取消契约；可退款金额统一由 `ClientRefundController` 实现继承的
`ClientRefundableAmountApi`，原有 Wire 路径和方法不变。

| 方法 | 服务内部路径 | 行为 |
| --- | --- | --- |
| GET | `/client-gateway-api/v1/orders/{orderNo}` | 按公开订单号读取当前本地订单及投影 |
| GET | `/client-gateway-api/v1/orders?clientOrderId=...` | 按 Client 内永久唯一业务号查询同一表示 |
| POST | `/client-gateway-api/v1/orders/{orderNo}/cancel` | 无 Body；请求取消（`Idempotency-Key` 由 Gateway 处理） |
| GET | `/client-gateway-api/v1/orders/{orderNo}/refundable-amount` | 返回退款基数减去预占与成功退款后的剩余额度 |

本地直连仍加 `/commerce` Context Path。字段绑定与 Jakarta Validation、Client 资源归属校验和 RFC 9457 错误仍由服务端处理；
认证和可信身份注入由 Gateway 负责，身份 Header 保留必填、long 类型绑定及 `@Positive` 约束。
Order/Refund 专用请求 Filter 已移除，Commerce 不再按业务路径额外拦截未知 Query、重复单值 Query 或禁用 Body。
这些公开协议限制归接入层；API 参数必填、长度、格式、范围，以及 Processor 的归属和游标业务校验继续执行。
Client `Idempotency-Key` 的格式、单值校验与请求重放由 Gateway 负责；Commerce API 不声明该 Header，过渡期被转发时忽略。
两个订单 GET 使用 `GetOrderConverter → GetOrderProcessor`：Controller 仅适配 HTTP，Processor 逐项读取本库事实、
检查完整性并构建有限响应，不开启查询事务或加锁，不调用 Payment 或 Finance。
对外使用独立 `order/response/GetOrderResponseBody` class 及嵌套 `*Info`，内部使用 Request、Context 和 Response；
HTTP 路径和参数位置不变；Client 按新的嵌套字段读取，Java API 消费者需要随返回类型变化重新编译。
响应及字段合同见[本次修订](../requirements/20260916/commerce-订单查询合并Checkout/response-shape-amendment.md)，
[支付成功示例](../requirements/20260916/commerce-订单查询合并Checkout/order-paid-response.json)可用于接口对照。
`creationMode` 来自订单持久化字段，当前创建入口显式保存 `HOSTED_CHECKOUT`，`DIRECT_PAY` 只预留模式值。
运行新代码前需人工应用[新增列 SQL](../requirements/20260916/commerce-订单查询合并Checkout/db/creation-mode.sql)；执行与验证状态见全局台账。
可退款金额查询由 `GetRefundableAmountProcessor` 依次读取归属当前 Client 的 `CommerceOrder` 和
`OrderRefundPosition`，复用位置对象的金额规则；不加载商品、Session、Payment 或资金投影。
Controller / Converter 仅转换和委托，内部 Request/Response 与独立 `GetRefundableAmountResponseBody` 均为普通 class。
移除旧 `OrderQueryService`、联表 View/SQL、领域 `RefundableAmount` 和 `OrderWireConverter`；
该 GET 不加事务、锁或外部调用，原响应字段及金额公式保持。
范围和未验证项见[可退款金额查询重构](../requirements/20260916/commerce-GetRefundableAmount接口重构/technique.md)。
未准备金额保留 null，金额字符串按币种精度输出。
必需投影缺失、币种或退款金额不一致返回服务错误；不会用虚构零额补齐事实。

Cancel 按订单业务事实处理，不再消费请求幂等组件，也不声明 Idempotency-Key；内部 Request
仅有 clientId/orderNo；已取消直接返回原取消时间，不再次查询支付或写入。没有受理支付且无 Intent 的订单可一次事务取消；有接受事实但创建响应未知时，先按同一 Order 取得唯一 Intent 再终止，只有明确 TERMINATED 才关闭 Order 和实际存在的 Session。
已支付返回 `409 REVERSAL_REQUIRED`，待定返回 202；取消响应不再输出内部 version。

`CancelOrderProcessor` 使用 Foundation `AbstractProcessor`，先归属查询、原结果判断，再验证新请求并构造 Context。
Processor 按需从单表 Repository 读取 CommerceOrder、可空 CheckoutSession、OrderPaymentProjection，不使用 OrderRecord 或整单联查。
Hosted Checkout 缺少 Session 仍报数据错误；Direct Pay 无会话时正常取消，若实际关联会话则一并关闭。
Context 持有这三个本库对象和已查询的支付受理事实。Service 负责 REQUIRED 短事务；SQL 同时检查 Order/已有 Session 版本、允许状态及
Payment 绑定/投影版本和未成功条件。事实已变化则回滚并返回并发冲突，不在 Processor 内重新加载、接管租约或自动重试。
取消专用 Hash、Claim/Lease、命令 ID、检查点和响应/失败快照已移除；共享命令表与其他消费者保留。
取消时间来自 Order.cancelled_at；Direct Pay 无 Session 的 200 响应省略 checkoutStatus。运行新代码前还需人工应用
[订单取消时间 SQL](../requirements/20260916/commerce-CancelOrder接口重构/db/cancelled-at.sql)，本次未执行。
Java 契约使用独立 `CancelOrderResponseBody` class，旧 `OrderModels` 引用需迁移；重构范围见
[本次说明](../requirements/20260916/commerce-CancelOrder接口重构/technique.md)，验证状态见全局台账。

Payment 终止端口本轮按用户授权由 `FixedPaymentTerminationClient` 暂固定返回 TERMINATED，并保留 TODO。真实接入后无法取得权威结果时返回 202；客户端再次取消沿原订单继续。GET 不推进取消，Payment HTTP、终止事件和后台处理仍待接入。该固定 Mock 不证明实际支付已经终止。

## Refunds 接口

本期范围、恢复边界和验证记录见 [Refunds 实施说明](docs/requirements/20260912-refunds-api/implementation.md)。
`ClientRefundController` 实现已有 `ClientRefundApi`，不新增公开路由契约。

| 方法 | 服务内部路径 | 行为 |
| --- | --- | --- |
| POST | `/client-gateway-api/v1/orders/{orderNo}/refunds` | 以 clientRefundId 防重受理部分或全额退款，返回原创建 201 回执 |
| GET | `/client-gateway-api/v1/orders/{orderNo}/refunds` | 按创建时间、退款号倒序游标分页，默认 20，最多 100 |
| GET | `/client-gateway-api/v1/refunds/{refundRequestNo}` | 查询当前退款状态 |
| GET | `/client-gateway-api/v1/refunds?clientRefundId=...` | 按 Client 内永久唯一退款业务号查询 |

金额继承 Order 币种；创建必须具有一致的已支付事实及退款基数。
`CreateRefundProcessor` 继承 Foundation `AbstractProcessor`，使用内部 Request/Response；Controller
只转换协议。创建依次单表查询 CommerceOrder、原 RefundRequest；原申请内容一致直接返回原 201 回执，
不再查支付或当前余额、不再登记任务。只有新申请才查询 OrderPaymentProjection、OrderRefundPosition。
Context 和参与的数据库对象均使用普通 class，纯事实判断归对象；创建不依赖 Checkout Session。

`RefundService.saveCreateRefundContext` 自行使用 REQUIRED 事务，原子插入退款、预占额度和登记补偿任务。
唯一索引保护 Client 业务退款号，条件 SQL 保护预检的订单、支付投影版本和可退余额；SQL 成功后直接同步
原数据库对象。相同业务号改订单、金额或原因返回 409；金额版本竞争整体回滚，不自动重试事务。
`Idempotency-Key` 由 Gateway 校验与重放，Commerce 不声明该 Header；不进入 Request，不再读写 CREATE_REFUND 命令或保存回执快照。
原退款创建字段直接生成回执。GET 只读本库，并按可信 Client 隔离，不调用 Payment。
本次范围与未验证状态见 [创建退款接口重构](../requirements/20260916/commerce-CreateRefund接口重构/technique.md)。

订单退款列表由 `ListOrderRefundsProcessor` 编排：先按可信 Client / orderNo 只查订单主键；有游标时单表读取
锚点并核对订单和创建时间；再单表读取退款，按 `create_time DESC, refund_request_no DESC` 多取一条判断后续页。
查询不使用显式事务、行锁或 `OrderRecord`，不依赖 Session，也不加载支付和资金投影。
Controller / Converter 仅转换内部 Request/Response 与普通类 `ListOrderRefundsResponseBody`，列表项为 `RefundInfo`。
保留原路径、分页默认值、JSON 字段及旧/新退款号游标，空页返回 `items=[]`、`nextCursor=null`；
每页观察当前退款状态，不冻结跨页状态快照。旧 `RefundService.list` 和列表 Page/Body 容器已移除。
范围与验证状态见[退款列表重构](../requirements/20260916/commerce-ListOrderRefunds接口重构/technique.md)。

按公开退款号查询由 `GetRefundProcessor` 编排：先按可信 Client / refundRequestNo 读取 `RefundRequest`，
再按同一 Client / orderId 只取公开订单号。该入口不再通过 `RefundService.get` 或联表 View，
不创建显式事务、不要求 Checkout Session，也不加载支付或资金投影。Controller / Converter 只转换
内部 Request/Response 和普通类 `GetRefundResponseBody`；HTTP 路径、字段、显式 null 和错误语义保持。
范围及后续验证见[退款详情重构](../requirements/20260916/commerce-GetRefund接口重构/technique.md)。

按 Client 业务退款号查询由 `GetRefundByClientRefundIdProcessor` 编排，复用单表
`findByClientRefundId`，限定可信 Client 和 CLIENT 来源，再只查询关联订单的公开编号。
两个详情入口共用 `GetRefundResponse`、`GetRefundResponseBody` 和 `GetRefundConverter` 的响应转换。
旧 `RefundService.get`、只读事务、领域 `RefundModels.View` 及旧 `RefundResponseBody` 已移除；
查询金额直接调用公共 Money，不做文本往返解析或额外异常包装。
范围及验证状态见[业务退款号查询重构](../requirements/20260916/commerce-GetRefundByClientRefundId接口重构/technique.md)。

`RefundSubmissionHandler` 使用 Foundation 提交后调度及 Claim/Lease；任务只保存退款号，不携带原因原文或
支付凭据。每次提交/查询复用同一 Payment 幂等身份。未决或不可用保持 PROCESSING 和预占，确定成功消费
预占，确定失败/取消释放预占。已完成退款再次执行不会重复处理金额，Order 仍保持 PAID。

Payment 退款端口目前由 `UnavailablePaymentRefundClient` 报告尚未接入，保留原预占和恢复任务。
合成 Payment 状态与退款结果只存在于测试源码，由测试配置显式装配，不进入任何业务 JAR。
当前端口模拟仅验证 Commerce 的本地编排；正式 Owner 契约接入后须使用真实 HTTP Client 连接外部 Simulator，
不能把现有测试声称为 Payment HTTP 契约或端到端退款验证。隔离规则和本次验证见
[测试代码隔离说明](docs/requirements/20260912-refunds-api/test-isolation.md)。
TODO(Payment)：正式 PAY-I04、结果查询及 MQ 事实。

## Client Webhook 业务事件

`order.paid`（`OrderPaymentServiceImpl.applySucceeded`）、`order.cancelled`（`OrderCancellationServiceImpl.cancel`）、
`refund.created`（`RefundServiceImpl.saveCreateRefundContext`）在业务变更的同一事务里经
`ClientWebhookSubmissionHandler.buildEvent` 冻结 Envelope 与两个 RFC 8785 Hash，登记 `CLIENT_WEBHOOK_REQUESTED`
补偿任务；提交后 Handler 核对 Hash，经 `WebhookEventApi` 调 Platform 内部受理接口，204 才完成。
5xx、网络错误、404 可重试；400/409/422 记为明确失败并输出 WARN。普通日志只记事件 ID 与类型。

| 配置 | 默认 |
|---|---|
| `paytech.http.client.groups.platform-support-service.base-url` | `lb://platform-support-service/platform-support` |
| 连接 / 读取超时 | 2 秒 / 5 秒 |

Platform 需开启 `webhook.enabled`，并在 `webhook.http.callers.commerce-service` 中允许上述三种事件。
退款终态与 `order.expired`、`order.funds_available` 未实现。范围与验证见
[Commerce Webhook 业务事件](../requirements/20261016/commerce-Webhook业务事件/delivery.md)。

Foundation 扫描入口已可恢复到期重试与过期租约，本期未启用外部 XXL-Job 调度。任务达到默认重试上限时
保留失败任务和资金预占，不能将技术失败解释为退款失败；部署前需完成调度、告警与人工恢复接入。
本期没有新增 Gateway、Nginx、其他服务或数据库结构变更。

`ClientApiIdempotencyKeyFilter`、`ClientApiNoStoreResponseFilter`、`ClientOrderRequestFilter`、
`ClientRefundRequestFilter` 和 `ClientApiTrustedContextFilter` 已按[职责决定](../agent-docs/decisions/2026-09-16-commerce-ingress-filter-simplification.md)移除。
缓存 Filter 原先也覆盖 Customer Checkout；Client 和 Customer 直连 Commerce 均不再保证由该 Filter 输出
`Cache-Control: no-store` / `Pragma: no-cache`。公开合同仍要求这些响应头，由 Gateway 统一承担；
Gateway 实现及配置未在本轮修改或验证。Client 身份 Filter 的 Header 单值检查及 `clientId/credentialId` MDC 填充一并移除；
Client 资源归属校验、现有 Request ID 日志链路及 Customer 自有请求处理保留，没有新增替代 Filter。
实际交付和后续验证见[接入 Filter 精简](../requirements/20260916/commerce-接入Filter精简/delivery.md)。

## 数据库

现行设计源为 [COMMERCE_PHYSICAL_DECISIONS.md](docs/database/COMMERCE_PHYSICAL_DECISIONS.md) 和
[commerce.pdma.json](docs/database/commerce.pdma.json)。完整目标定义只有
[commerce-schema.sql](docs/database/sql/commerce-schema.sql)，初始化数据只有
[commerce-initial-data.sql](docs/database/sql/commerce-initial-data.sql)。生成统计及实际本地库状态在当前状态台账维护。

基础创建兼容旧库的人工增量脚本位于
[本需求 ddl.sql](../requirements/20260911/client-contract-OE与Client模型梳理/db/commerce/ddl.sql)。它保留旧数字身份、
Scope、JSON、Hash、原金额列，只增加格式/准备/终止/OE 编号列并放宽新创建所需的可空约束。
不得用完整目标 CREATE 脚本覆盖旧库，也不能将这个局部兼容迁移当成完整支付准备 Schema 迁移。

当前创建写入还需要 [creation_mode 增量](../requirements/20260916/commerce-订单查询合并Checkout/db/creation-mode.sql)
和 [consumer_country_code 增量](docs/requirements/20260916-create-checkout-business/ddl.sql)。执行前核对现有列，
单独授权并备份表结构，不能重复执行。

Java 应用固定 `spring.sql.init.mode=never`。启动和测试均不执行 DDL 或基础设施初始化 DML；
逐例业务 Fixture 由 Mocker 插入和清理，创建与恢复测试要求先人工准备已批准结构。
旧 `t_command_idempotency` 仅保留历史数据；当前准备与支付不再消费，不为业务接入新增请求重放。

## Owner Client 模式

CCT 通过 Nacos 服务发现与 Spring Cloud LoadBalancer 调用，服务名为 `client-contract-service`。
`@ImportHttpServices` 自动注册 Owner API 代理，Commerce 直接注入；不配置 CCT 实例地址，也不自行创建 RestClient 或代理。
注册中心使用项目统一的 `NACOS_SERVER_ADDR` / `NACOS_NAMESPACE`。HTTP Service Group 不设置 `base-url`，
Spring Cloud 以 Group 名作为服务 ID。连接/读取超时为 2/5 秒，LoadBalancer 与 HTTP 传输都禁止自动重试和重定向。
Foundation Group Configurer 统一完成 Request ID 传播、Problem 解码及可选的严格 JSON 绑定/响应大小限制。
应用级禁重试传输集中在 `HttpClientConfiguration`。发现结果为空或连接失败返回依赖不可用。

旧预检链（`CctPrecheckClient` → `CheckoutEligibilityApi.precheck`）已删除：CCT 在合并单仓时已移除该接口，创建主链也早已不再调用（[Customer 与存卡支付](../requirements/20261015/commerce-Customer与存卡支付/delivery.md)阶段 4 记录）。当前由 `CheckoutCommercialPort` 返回完整 OE/商业/法律事实；新依赖暂固定 Mock，真实解析接入时继续复用上述 HTTP 与服务发现公共组件。创建初始化不使用请求幂等组件。

## Customer 与已存卡

按 [Customer 与存卡支付](../requirements/20261015/commerce-Customer与存卡支付/technique.md) §2.1–2.2：`t_customer` 以（`client_id`, `active_reference`）唯一保证同一引用同时最多一个有效 Customer，公开编号 `cus_` 前缀。Client API `/client-gateway-api/v1/customers` 提供创建（已存在返回 200）、按编号或引用查询、删除，以及 `/customers/{customerNo}/stored-cards` 列出与删除已存卡（经 Payment `StoredCardApi`，事务外调用）。删除 Customer 在同一事务登记 `STORED_CARD_INVALIDATION` 补偿任务，提交后调用 Payment 失效其已存卡。建单可带 `consumerReference`，在保存订单前关联或新建 Customer，订单记 `customer_id`；访客订单不建 Customer。引用只允许 `[A-Za-z0-9._:-]`，7～15 位纯数字视为电话号码拒绝（422）。

结账存卡（§2.2、§2.5）：`t_client_stored_card_setting` 按 Client 开通（无记录为未开通）。`GET /customer-gateway-api/v1/checkouts/{token}` 在订单有有效 Customer 且已开通时返回 `cardSaving.available=true` 与已存卡；`POST .../payments` 接受手输卡（可带 `saveCard`）或 `storedCard{storedCardToken,cvv}`，无资格时 422 `STORED_CARD_NOT_AVAILABLE`。卡支付经 `CheckoutCardPaymentPortImpl` 调用 Payment pay（幂等键 `submission:<id>`、自动捕获、回跳本页面）；结账“查询支付提交”仍为占位，待 Payment 提供按来源提交查询。

## 构建与本地运行

前置条件：Java 25、仓库 Maven Wrapper，以及能解析本项目 Foundation Snapshot 的 Maven Settings/Nexus。

运行配置按职责绑定到 `@ConfigurationProperties`，由 `CommerceRuntimeConfiguration` 注册并装配：

| 配置键 | 配置类 | 约束与生效时机 |
|---|---|---|
| `commerce.checkout.public-origin` | `CommerceCheckoutProperties` | 必填的 HTTPS Origin；复用 `CheckoutPublicOrigin` 校验，修改需重启，仅影响新建 Session |
| `commerce.identifier.region-id` | `CommerceIdentifierProperties` | 必填，0～31；复用 Foundation Snowflake 范围校验，修改需重启 |
| `commerce.identifier.worker-id` | `CommerceIdentifierProperties` | 必填，0～31；同一区域每实例唯一，修改需重启 |

配置键及环境变量 `COMMERCE_CHECKOUT_PUBLIC_ORIGIN`、`COMMERCE_SNOWFLAKE_REGION_ID`、
`COMMERCE_SNOWFLAKE_WORKER_ID` 保持不变，由 `application.yml` 读取，无默认值。
ID 生成器保持进程级单例，业务和补偿共用；本次配置归集不增加 Nacos Config 加载或动态刷新。

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw clean verify
```

### 测试启动边界

CreateCheckout 与 GetOrder 按[现行测试规范](../agent-docs/UNIT_TEST_GUIDE.md)使用 `MockTestBase`：
`TestApplication` 引用正式 `CommerceApplication`，复用其生产组件扫描，以随机端口启动；
测试组合根只声明辅助组件扫描、测试 Mapper 和必要的 HTTP 配置，不逐个列举生产 Bean。
新创建流程由 `CheckoutCommercialMocker` 配置商业 DTO，首次支付由 `CheckoutIntentMocker`/`CheckoutCardMocker` 配置结果；它们只替换本轮暂缓的外部端口，不替换内部业务或数据库。以下独立 CCT HTTP Mock 基础设施仍保留，但新创建主链不再调用基础预检。
`MockCheckoutEligibilityApi` 直接实现正式 CCT API；Foundation `MockHttpServer` 使用独立 MVC
上下文承载它，路径与参数绑定继承接口。测试服务发现将 `client-contract-service` 指向随机地址，
生产 API 代理与 LoadBalancer 保持原样。
测试配置通过 `@EnableMockHttpServer` 扫描指定包的 `@TestComponent`，自动装配 MockHttpServer、
CctCheckoutMocker 与 Mock API；MockTestBase 直接注入，公共组件在客户端装配前注册随机端点，
测试上下文关闭时释放服务，无需逐个手写 Bean 工厂。
本地 CheckoutDataMocker、CheckoutCreationChecker、可控时钟与事务观察器也通过 TestComponent 扫描注入；
测试配置只声明装配范围；CctTransactionProbe 实现 HTTP Service Group 配置接口并自动挂载事务观察拦截器。
Builder 只构造数据，Mocker 插入前置事实，Checker 查询真实数据库；两级 `BeforeEach` 每例重建基线，
Commerce 基类 `AfterEach` 等待异步结束并检查意外调用，随后 Foundation `TestBase`
通过注入的 `List<Clear>` 清理全部自有数据、响应配置和调用记录；不在 BeforeEach 清理。

接口测试通过 Foundation [`paytech-test-support`](../paytech-foundation/paytech-test-support/README.md) 的
`HttpTestInvoker` 和 `TestRequestHeaders` 发出请求；`CreateCheckoutResponseChecker`、`GetOrderResponseChecker` 继承公共
`AbstractHttpResponseChecker`，复用协议断言，保留本地业务断言。组件只在测试 POM 以 `test` scope 消费，
数据 Mocker、CCT Mocker、可控时钟和事务观察器实现 `Clear` 并由测试配置注册为 Bean，
新增清理对象无需修改基类清单。应用启动、具体清理实现、数据库 Checker 和异步停止仍由 Commerce 管理。

当前 `auto` 包含 CreateCheckout、GetOrder、CreateRefund、ListOrderRefunds 的 HTTP 场景类及对应的本地事务测试。
其他旧 auto 测试已删除，后续接口测试按现行规范重建。旧 `test.repository` Mapper、Fixture 和配置
已无实际测试消费者并清理；CreateCheckout 使用 `test.dal.CheckoutCreationTestMapper`，GetOrder 使用
`test.dal.OrderQueryTestMapper` 直接准备查询事实。GetOrder 不配置外部成功响应，显式断言 CCT 零调用；
每次请求前后比较订单及关联表的全列快照，验证查询没有修改版本、状态、金额或链接。
查询 Fixture 的订单 ID 统一登记到现有 CheckoutDataMocker，支付投影和冻结上下文一并在全局 AfterEach 清理。
`ManualTestBase` 直接引用正式启动类，不加载旧 Repository 夹具或自动测试的 CCT Mock。
Mock 和 Manual 基类均通过现有 `@AutoConfigureRestTestClient` 启用 Foundation 自动装配，
直接注入 `HttpTestInvoker` / `RestTestClient`，复用宿主传输配置与随机端口；不再维护 `HttpTestClientConfiguration`。
原 Checkout 专用启动类和配置类已合并到 `TestApplication`；启动修复与验证边界见
[测试配置收敛台账](../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-test-configuration-20260917)。
旧准备 Service/Handler 和命令表访问已移除；历史行不改写，未准备旧订单和旧任务须在其他环境启用前单独处理。

- [`application-mock.yml`](paytech-commerce-test/src/test/resources/application-mock.yml) 同时是 Commerce 配置字段清单和 Mock 运行配置：列全自有配置、已接入 Foundation 组件配置及本服务使用的框架配置，包含可选字段、代码默认值和已关闭能力的附属字段；中间件、框架与公共配置在上方，系统自定义配置在下方；公共配置不逐项添加说明，自定义字段保留必要的用途、单位和启用条件注释。配置字段新增、修改或删除时同步维护。`MockTestBase` 自动启用 `mock` Profile，无需设置生产环境变量；CCT 随机端口由测试注入 `commerce.test.cct.base-url`，YAML 将其绑定到 SimpleDiscovery，DTO 响应由每个用例配置。
- `application-auto.yml` 是尚未清理的旧配置资源，当前 auto 测试均使用 `application-mock.yml`。`auto` 测试目录与 `mock` Spring Profile 职责不同，不需要在 IDE 手动切换 Profile。
- Mock 数据库固定为仓库 `local-environment` 的 `127.0.0.1:13306/mor_commerce`，默认凭据与其本地初始化值一致；仅自定义本地密码时使用 `COMMERCE_TEST_DB_PASSWORD` 覆盖。MySQL 和已批准 Schema 仍须事先准备，测试不自动建表或迁移。
- 当前 Mock 的完整初始化入口为 [`src/test/resources/init/ddl.sql`](paytech-commerce-test/src/test/resources/init/ddl.sql)：使用 root 在本地 MySQL 手工执行，创建 `mor_commerce`、`commerce_app`，授予本库 `SELECT/INSERT/UPDATE/DELETE` 权限，再创建测试依赖的 14 张表，已包含 `creation_mode`、`consumer_country_code`。账号及默认密码与 Mock 配置一致；文件含重建数据库语句，只能用于获准重建的本地测试库，已有用户不重置自定义密码。表定义与正式 Schema 一致；已有开发库只能执行缺失表或对应需求的增量 SQL。后续 Mock 增加表依赖时同步此文件，场景数据仍由每例 Mocker 准备。
- `application-manual.yml` 使用同一本地 MySQL 与测试凭据边界，默认关闭 Nacos；无发现实例时依赖失败关闭。人工联调可显式启用 Nacos Discovery 并指定注册中心；
- 支付订单锁引入 Foundation Redis Starter，默认启动需要 `MOR_REDIS_HOST/PORT/PASSWORD`（本地 `127.0.0.1:16379`）。自动测试使用仓库手工启动的 `mor-local-redis-1`；`application-mock.yml` 已配置该隔离本地实例的默认密码，IDE / Maven 直接运行无需额外注入。自定义本地密码时仍可通过 `COMMERCE_TEST_REDIS_PASSWORD` 或 `MOR_REDIS_PASSWORD` 覆盖。测试 Context 使用独立随机 Redis Namespace，锁在 finally 释放，不清理其他业务 Key、不自动启动容器。Redis 不可用时启动/获取锁失败关闭，不降级为无锁。
- Commerce 使用 Nacos Discovery，自动测试显式关闭注册和发现；RabbitMQ 仍无运行时消费者，父 POM 继续禁止 AMQP 及非 Foundation Redis 客户端依赖。

默认自动测试使用 `./mvnw clean test`；只运行 `manual` 包中的人工测试使用
`./mvnw -Pmanual-test test`。创建接口可单独执行：

```bash
./mvnw -Pcheckout-create-tests clean test
./mvnw -Pcheckout-create-tests '-Dtest=CreateCheckout*Test' test
./mvnw -Pcheckout-create-tests -Dtest=CreateCheckoutSuccessTest#test_createCheckout test
```

两个订单查询入口可以一起执行（不启用创建接口专用 Profile）：

```bash
./mvnw '-Dtest=GetOrder*Test' -Dsurefire.failIfNoSpecifiedTests=false test
```

按 Client 业务订单号查询也可单独执行：

```bash
./mvnw -Dtest=GetOrderByClientOrderIdTest -Dsurefire.failIfNoSpecifiedTests=false test
```

取消订单接口及其事务边界可以一起执行：

```bash
./mvnw '-Dtest=CancelOrderTest,OrderCancellationTransactionTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

退款创建接口及其原子写入测试可以单独执行：

```bash
./mvnw '-Dtest=CreateRefund*Test,RefundCreationTransactionTest' -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw '-Dtest=CreateRefundTest#test_acceptRefundAndDispatchAfterCommit' -Dsurefire.failIfNoSpecifiedTests=false test
```

退款列表查询可单独执行，无需创建退款或配置外部成功响应：

```bash
./mvnw -Dtest=ListOrderRefundsTest -Dsurefire.failIfNoSpecifiedTests=false test
```

按公开退款号查询可单独执行：

```bash
./mvnw -Dtest=GetRefundTest -Dsurefire.failIfNoSpecifiedTests=false test
```

按 Client 业务退款号查询可单独执行：

```bash
./mvnw -Dtest=GetRefundByClientRefundIdTest -Dsurefire.failIfNoSpecifiedTests=false test
```

可退款金额查询可单独执行：

```bash
./mvnw -Dtest=GetRefundableAmountTest -Dsurefire.failIfNoSpecifiedTests=false test
```

`checkout-create-tests` 只编译和运行创建 Checkout 测试及其辅助类，显式 Profile 不代替全模块 `verify`。
共享实例中的测试仅清理本次分配的两个
Client 命名空间及明确预留的 Order ID，不执行整库/整表清空。JUnit 在同一测试 JVM 内串行运行。

完整 `clean verify` 包含真实本地 MySQL 门禁，需要先按仓库 `local-environment` 规范人工准备 `mor_commerce`、应用
需求增量 SQL，并提供测试数据库密码。缺少前置条件时测试应快速失败并给出准备说明。

本地启动需要配置 Checkout Origin、Snowflake 坐标、数据库密码与 Nacos 注册中心（CCT 必须以相同 Namespace 中的 `client-contract-service` 注册）：

```bash
export COMMERCE_CHECKOUT_PUBLIC_ORIGIN=https://checkout.local.test
export COMMERCE_SNOWFLAKE_REGION_ID=0
export COMMERCE_SNOWFLAKE_WORKER_ID=0
export MOR_COMMERCE_DB_PASSWORD='<local password>'
export SPRING_PROFILES_ACTIVE=local
export NACOS_SERVER_ADDR=127.0.0.1:8848
export NACOS_NAMESPACE=public
java -jar paytech-commerce-web/target/paytech-commerce-web-1.0.0-SNAPSHOT-exec.jar
```

应用默认监听 `127.0.0.1:18102`。Actuator 仍可从
`http://127.0.0.1:18102/commerce/actuator/health` 检查，但本地健康不代表公共路由、真实 Owner 集成或部署已完成。

## 验证入口

当前源码对应的开发检查、完整门禁与数据库迁移状态只在
[当前状态台账](../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-precheck-20260911)维护。
创建接口在 `auto/ext/http/clientapi/v1/checkout/create` 下按场景组织，均经过真实 HTTP、数据库与 CCT DTO Mock：

| 测试类 | 范围 |
|---|---|
| `CreateCheckoutSuccessTest` | 创建成功、金额计算、合法边界与扩展字段 |
| `CreateCheckoutDuplicateTest` | 业务订单号防重、Client 隔离与并发竞争 |
| `CreateCheckoutValidationTest` | 请求绑定、字段/金额/期限校验及提前拒绝 |
| `CreateCheckoutInitializationTest` | 商业初始化拒绝、不完整法律结果、依赖故障重试和调用期间过期 |

`AbstractCreateCheckoutApiTest` 只提供公共调用与场景辅助，每个具体类保留自己的 BeforeEach，统一复用 MockTestBase 的全局初始化和清理。原子写入与传播边界使用
`CheckoutCreationTransactionTest`。其余旧 auto 测试及其包内专用合同辅助类已删除，后续接口测试按授权范围重建。
清理和验证范围见[auto 测试清理台账](../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-auto-tests-pruned-20260917)。

订单号查询接口在 `auto/ext/http/clientapi/v1/order/get` 下组织，每个类均通过 `@see` 引用
`ClientOrderApi#getOrder(long, long, String)`：

| 测试类 | 范围 |
|---|---|
| `GetOrderSuccessTest` | Checkout / Direct Pay、完整响应、金额精度、冻结国家、到期边界和更新时间 |
| `GetOrderValidationTest` | Client 隔离、未知订单、标识精确匹配、身份与路径参数边界 |
| `GetOrderProjectionTest` | Intent / Attempt 状态映射、UNKNOWN 优先级、退款余额和资金成熟事实 |
| `GetOrderConsistencyTest` | 必需事实缺失、币种/金额冲突、支付事实不完整和损坏链接 |

`AbstractGetOrderApiTest` 只复用调用与只读断言；Fixture 使用独立 Builder，每例由 Mocker 在短事务中
直接插入本地数据后再发出 HTTP 请求，不调用创建或支付接口准备数据。GetOrderResponseChecker 反序列化为
正式 `GetOrderResponseBody`，业务断言通过 DTO getter 读取；响应结构通过 DTO 回序列化后与原始 JSON 比较，
不维护字段名清单或使用字符串路径读取业务值。既有 `init/ddl.sql` 已覆盖所需表，
测试不自动执行 DDL。实际运行结果及覆盖率边界见[GetOrder 测试台账](../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-get-order-tests-20260917)。
未修改 90% 覆盖率门槛；完整业务与覆盖率验收要求兼容迁移就绪。

按 Client 业务订单号查询使用一个 `order/getbyclientorderid/GetOrderByClientOrderIdTest`，
`@see` 指向 `ClientOrderApi#getOrderByClientOrderId(long, long, String)`。
复用相同的 Builder、数据库 Mocker、只读快照和响应 Checker，覆盖 Checkout / Direct Pay、支付及退款视图、
Client 隔离、大小写、Query 编码、编号与身份边界、到期重复查询及投影缺失。
Query 使用已自动装配的 `RestTestClient` URI 变量编码，保留 `+`、`&`、`%` 等合法编号字符；
不新增调用器、配置或生产代码。每次查询检查数据库不变、CCT 预检及商业初始化零调用，清理由公共 AfterEach 完成。
实际结果见[按 Client 订单号查询测试台账](../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-get-order-by-client-id-tests-20260921)。

取消接口使用一个 `order/cancel/CancelOrderTest`，`@see` 指向 `ClientOrderApi#cancelOrder`；
`OrderCancellationTransactionTest` 单独验证 Service 的 REQUIRED 传播及 Order / Session / Payment 投影的 CAS 回滚。
覆盖 Checkout / Direct Pay、本地取消、按订单身份终止、重复取消、Intent 不存在及终止超时后重提、
创建响应晚于取消时阻止支付、成功支付拒绝取消、Client 隔离及 HTTP 参数边界。
前置事实复用订单 Builder / Mocker，补充取消时刻与法律接受的 TestMapper；响应使用正式 `CancelOrderResponseBody`。
每次调用检查金额、法律接受、网络证据和退款事实未被改动，沿用两级 BeforeEach 及全局 AfterEach 清理。

取消不调用创建接口或补写 Intent 关联；Payment 明确返回业务 `NOT_FOUND` 时完成本地取消，
超时与 `PENDING` 保持待定，其他错误不按不存在处理。
`PaymentTerminationMocker` 在测试侧按订单配置端口 DTO / 异常，记录调用参数及事务状态，并参与全局 Clear。
终止 Client 当前仍为已获准的固定 `TERMINATED` 实现；真实 Payment 按来源订单取消的合同和 HTTP 适配仍待接入，
现有 Payment 仅按 Intent ID 终止。端口用例不代表真实 HTTP 或远端并发已验证。
实际运行与覆盖率边界见[取消接口当前台账](../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-cancel-without-create-20260921)。

退款创建按实际复杂度拆成两个 HTTP 场景类，另有独立的原子写入测试：

| 测试类 | 范围 |
|---|---|
| `CreateRefundTest` | Checkout / Direct Pay、金额精度、真实异步提交、业务防重、Client 隔离和并发预占 |
| `CreateRefundValidationTest` | HTTP 绑定、字段与精度校验、订单归属、可退额度和不一致的持久化事实 |
| `RefundCreationTransactionTest` | Service REQUIRED 传播、退款/预占/任务一起回滚、过期 Context 的 CAS 拒绝 |

退款响应通过正式 `CreateRefundResponseBody` getter 断言，响应结构复用 DTO 序列化定义。
订单事实复用 OrderQueryDataMocker，原退款由 RefundCreationDataMocker / TestMapper 直接准备，
不调用支付或退款接口完成 Arrange。每次 HTTP 调用前后核对订单、支付、Session、商品、商业上下文
和资金投影不变，并分别检查退款申请、金额位置和补偿任务。原退款重提返回原始 201 回执，不再预占或派发任务。

测试保留真实补偿登记、提交后唤醒和 Handler。PaymentRefundClient 尚未接入真实 HTTP 合同，
生产适配器明确返回不可用，本轮验证本地受理后保留 PROCESSING 退款、预占及 ERROR 恢复任务；
不替换内部 Client，也不声称覆盖 Payment 协议、超时或渠道退款成功结算。
全局 AfterEach 先等待 Coordinator 与 Handler 退出，再清理自有订单对应的退款任务和申请；
无法停止时拒绝清理并禁止复用环境。

所需 `t_refund_request`、`t_compensation_event` 的完整定义集中在测试 `init/ddl.sql`。
已有本地库只摘取缺失两表的 CREATE TABLE 手工执行，不能执行整个重建脚本。
实际验证、覆盖率和本地 DDL 执行记录见[退款创建测试台账](../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-create-refund-tests-20260919)。

订单退款列表使用一个 `ListOrderRefundsTest`，按实际复杂度保持在单类内，不新增专用测试基类。
覆盖空列表、Client/Operations 来源、退款各状态、金额精度、默认页大小和边界、同一微秒内退款号倒序、
游标编码及归属、翻页期间插入和状态更新、归属隔离、参数及损坏事实拒绝。
`ListOrderRefundsResponseChecker` 使用正式 `ListOrderRefundsResponseBody / RefundInfo` getter 断言业务字段，
响应结构复用 DTO 序列化定义，不再手写字段名及可空字段清单。每次 HTTP 查询都通过 `RefundQueryChecker` 比较本例两个
Client 的订单、关联事实、退款及补偿任务全列快照，检查查询没有写入；CCT 必须零调用。
前置数据使用现有订单与退款 Mocker 的 Builder 直接准备，待执行任务也只作为数据库事实插入，不调用
被测创建命令或主动唤醒任务。复用既有 DDL 和全局清理；没有新增表、配置或外部 Mock。
实际执行及覆盖率见[退款列表测试台账](../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-list-order-refunds-tests-20260919)。

CreateCheckout、GetOrder、CreateRefund、ListOrderRefunds 的响应 Checker 均使用正式 ResponseBody。
DTO 回序列化比较只检查响应结构，业务期望值仍由用例独立指定；验证记录见
[响应 Checker 调整台账](../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-response-dto-checkers-20260919)。

公开退款号查询使用一个 `GetRefundTest`，类级 `@see` 指向 `ClientRefundApi#getRefund`。
复用正式应用、两级 BeforeEach、全局 AfterEach、订单与退款 Mocker；通过真实 HTTP 查询本地数据库。
覆盖全部退款状态、Operations 来源、金额和时间精度、重复查询当前状态、Client 隔离、关联订单缺失或归属错误、
标识与身份参数边界及损坏事实。响应通过正式 `GetRefundResponseBody` getter 断言，业务期望独立准备。
只读 Checker 也纳入没有关联订单的退款，避免缺失订单场景遗漏退款及任务变化；不推进补偿任务或配置外部成功 Mock。
存量金额精度和币种错误按系统错误返回；GetRefundProcessor 只转换公共 Money 的参数异常并保留 Cause，
仍复用 Money 校验，测试保持 500 的期望。
具体结果见[退款详情测试台账](../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-get-refund-tests-20260919)。

按 Client 业务退款号查询使用一个 `refund/getbyclientrefundid/GetRefundByClientRefundIdTest`，
类级 `@see` 指向 `ClientRefundApi#getRefundByClientRefundId`。复用相同的退款 Builder、Mocker、
`GetRefundResponseChecker` 和只读数据库 Checker，不新增启动配置或响应模型。
覆盖同编号跨 Client 隔离、同 Client 多订单/退款定位、Operations 来源排除、关联订单缺失或越权、
大小写、Query 保留字符、长度和身份边界，以及退款状态、金额、时刻和版本。
每次 GET 比较数据库全列快照并检查相关外部端口零调用；前置准备与清理沿用两级 BeforeEach 和全局 AfterEach。
本库存量金额精度或币种错误与公开退款号查询一致，返回系统错误，不归为 Client 参数错误。
实际结果见[按 Client 退款号查询测试台账](../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-get-refund-by-client-id-tests-20260921)。

可退款金额查询使用一个 `refund/refundableamount/GetRefundableAmountTest`，`@see` 指向
`ClientRefundableAmountApi#getRefundableAmount(long, long, String)`。
复用订单 Builder / Mocker 和退款只读 Checker，覆盖基数扣减、零额、币种精度、Checkout / Direct Pay、
Client 隔离、参数边界、位置损坏，以及重复查询返回最新位置。`asOf/version` 与独立准备的位置记录比较，
金额期望由用例明确指定；响应通过正式 `GetRefundableAmountResponseBody` getter 检查，不复制 JSON 字段清单。
每次 GET 都比较两个测试 Client 的数据库全列快照，检查查询不预占、不推进退款任务、不重新准备或支付。
沿用两级 BeforeEach、全局 AfterEach 和既有 DDL，无新增配置或外部 Mock。
实际运行与覆盖范围见[可退款金额测试台账](../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-get-refundable-amount-tests-20260921)。

消费者资料保存使用一个 `customerapi/v1/checkout/consumer/SaveConsumerTest`，类级 `@see` 指向
`CustomerCheckoutApi#saveConsumer(String, SaveConsumerRequestBody)`；另用 `SaveConsumerTransactionTest`
验证 Service 独立提交、REQUIRED 传播、消费者 CAS / 唯一约束失败时的两表回滚。
HTTP 场景覆盖首次保存、完整替换及可选字段清空、规范化、参数和状态边界、保护失败重试、
保护期间订单变化与两个请求同时保存。响应结构与值直接比较正式 `SaveConsumerResponseBody`，
独立 Checker 检查消费者引用、版本、时刻和关联事实，保存不能变更 OE / 税务或发起支付。

复用正式应用、真实本地 MySQL、现有订单 Fixture 及全局 AfterEach 清理。
`ConsumerProtectionMocker` 自动装配为测试组件，按完整消费者 DTO 配置响应并记录事务状态；
未配置、意外 Reveal 或事务内调用均使测试失败。当前 Protect 尚无真实 HTTP Client，
本轮仅覆盖端口后的 Commerce 行为，不能作为 Vault HTTP / 加密 / 稳定 Token 协议的验证。
未新增生产 Mock、配置或 DDL。定向运行：

```bash
env -u COMMERCE_TEST_REDIS_PASSWORD -u MOR_REDIS_PASSWORD \
  -u SPRING_DATA_REDIS_PASSWORD -u SPRING_APPLICATION_JSON \
  JAVA_HOME="$(/usr/libexec/java_home -v 25)" \
  ./mvnw -pl paytech-commerce-test -am test \
  '-Dtest=SaveConsumerTest,SaveConsumerTransactionTest,CheckoutIntentCreationTest' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  jacoco:report-aggregate@report-commerce-checkout-coverage
```

实际执行范围见[消费者保存测试台账](../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-save-consumer-tests-20260921)。

## 尚未实现

- 真实商业/风险/税务/文件/Payment Owner 接入与晚到支付事实定价；
- 除 CCT 基础预检外的真实 Owner 适配与跨服务完整联调；
- 退款终态与过期、资金可用 Webhook，Funds Availability / Payment 事件消费，以及补偿任务的部署调度与人工恢复接入；
- Gateway/Platform/Nginx Client API 激活、`checkout-web`、Consumer `/c/{token}` 页面；
- 真实 Owner 联调、跨系统端到端验收、发布和部署。

## 创建初始化 / 首次 Intent 回归

`CheckoutIntentCreationTest` 经真实 Customer HTTP 入口覆盖已初始化无 Intent、首次创建、后续复用、创建/卡提交超时、取消重提及与创建交错。独立 POST `/preparation` 已删除，GET 不返回 PREPARE 操作；回归验证旧路径返回 404。所有 Fixture 由 TestMapper 插入，公共 AfterEach 清理接受、证据、合同、法律、消费者及订单；Mock 调用控制仅留在 test。

```sh
JAVA_HOME="$(/usr/libexec/java_home -v 25)" ./mvnw -Dtest='CreateCheckout*Test,CheckoutCreationTransactionTest,CheckoutIntentCreationTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

仅补齐缺失四表可审核执行 [local-test-tables.sql](docs/requirements/20260919-checkout-preparation/local-test-tables.sql)；其他旧库继续按已有需求的增量 SQL 人工迁移，不能直接执行完整初始化脚本。
