# Commerce Client Webhook 业务事件 — 技术方案

> 状态：第 1—4 节阶段 2 已确认（2026-10-17）；第 5—11 节阶段 3 已确认（2026-10-17，“继续”）。

## 1. 现状

下文 Commerce 路径相对 `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/`。

### 1.1 写事务与并发

- 4 个触发点都不用 `@Transactional` 或 Redis 锁，统一经 `service/transaction/CommerceTransactionExecutor.write()` 执行。它是 `TransactionTemplate`：REQUIRED、READ_COMMITTED、超时 15 秒。
- 并发靠版本 CAS 控制：更新行数不为 1 时抛 `ConcurrentRequestException`。
- CAS 成功后，仓储会同步更新内存对象（状态、时间、`version+1`），所以写完之后事件需要的字段都已在内存里。

### 1.2 四个触发点

| 事件 | 写入位置 | 首次进入的判断 | 冻结对象可用字段 | 缺口 |
|---|---|---|---|---|
| `order.paid` | `OrderPaymentServiceImpl.applySucceeded`（`markPaid` 之后） | 重复消息在写入前由 `isApplied` 返回 DUPLICATE；已关闭订单走 `recordClosedOrderPayment`，不应发事件 | orderNo、clientOrderId、PAID、currency、totalAmount、paidAt、version；`capturedAmount` 作为 paidAmount | `paymentStatus` 不落库（查询时推导），本事件固定为 SUCCEEDED |
| `order.cancelled` | `OrderCancellationServiceImpl.cancel`（仅本地取消，以及 Payment 确认终止后两条路径都经过这里） | 已取消的订单在写入前提前返回；`requestTermination` 是另一个事务，不应发事件 | 订单字段、cancelledAt、version、结账会话状态 | 原因码只写在 SQL 里（固定为 `CLIENT_REQUESTED`），`CommerceOrder` 对象没有这个字段 |
| `refund.created` | `RefundServiceImpl.saveCreateRefundContext`（这里已登记退款提交补偿任务） | 同一 `clientRefundId` 重放在写入前返回；唯一键冲突时整个事务回滚 | `RefundRequest` 的全部安全字段（rfd_ 编号、clientRefundId、requestOrigin、金额、币种、PENDING、原因、version=1、时间），orderNo | 无 |
| `refund.succeeded` / `failed` / `cancelled` | `RefundServiceImpl.complete`（退款提交任务的短事务，行锁 `FOR UPDATE`） | 已是终态时提前返回；只有首次进入终态才执行 `refunds.update` | 状态、`statusReasonCode`、completedAt、version | **生产链路目前到不了这里**：`UnavailablePaymentRefundClient` 总是抛异常，Commerce 还没接入 Payment 退款接口 |

### 1.3 补偿任务（Foundation `paytech-compensation-event`）

- 现有两个 Handler（`RefundSubmissionHandler`、`StoredCardInvalidationHandler`）都用 `CompensationEventRequest(key, type, "-")`，不带载荷。
- 带载荷的规则：
  - `register()` 按 `(event_key, event_type, sub_event_type)` 幂等，同键载荷不同会抛 `EVENT_CONFLICT`；
  - 载荷上限 **4096 字节**；
  - 数字只允许 64 位整数，小数必须用字符串；
  - 字段名不能叫 secret、credential、pan 等。
- `payload_json` 列是 MySQL `JSON` 类型，**不保留键顺序**，取出后必须重新规范化才能计算 Hash。
- Handler 的返回值：
  - `success`；
  - `retryableFailure`：可重试；
  - `terminalFailure`：明确失败，不再重试。抛 `FailException` 也等同明确失败，抛其他运行时异常按“结果未知”重试。
- 默认重试策略：30 秒超时、5 次、退避 1 秒到 5 分钟。生产 `application.yml` 没有补偿或 XXL 配置，用默认值。XXL-Job 扫描只在 `xxl.job.executor.enabled=true` 时启用。

### 1.4 HTTP 客户端与依赖

- `PaymentClientConfiguration` 和 `RiskClientConfiguration` 是同一套写法：`@ImportHttpServices` 加 `HttpServiceClientOptions.strict().withStrictJsonResponses(8192)`，地址配在 `spring.http.serviceclient.<group>.base-url`（`lb://...`）。
- Foundation 错误映射（`HttpServiceClientInterceptor`）：

| 上游返回 | Commerce 收到的异常 |
|---|---|
| 5xx 或 I/O 失败 | `ServiceUnavailableException` |
| 4xx，code 为 `BUSINESS_CHECK_FAILED` | `BizCheckFailException` |
| 4xx，code 为 `INVALID_REQUEST` | `InvalidParameterException` |
| 409，code 为 `CONCURRENT_REQUEST` | `ConcurrentRequestException` |
| 404，code 为 `NOT_FOUND` | `FailException` |
| 未知 code 或正文格式不对 | `ErrorException(UPSTREAM_INVALID_RESPONSE)` |

- commerce-web **还没有**依赖 `platform-support-api`；**已经**依赖 `paytech-json-canonicalization`（RFC 8785），但还没用过。
- Commerce 有现成的查询映射可以复用：`GetOrderProcessor`、`GetOrderConverter.money`（金额格式）、`GetRefundProcessor`、`GetRefundConverter`（后者已与设计第 11.3 节一致）。
- 公开编号格式：订单 `ord_` 加令牌，退款 `rfd_` 加令牌。

### 1.5 Platform 受理端

- `WebhookEventApi`：`POST /platform-support/webhooks/v1/events`，带 `X-Caller-Service`，成功返回 204、没有正文。
- 请求体校验：
  - `clientId` 为十进制字符串；
  - `sourceVersion` 为正整数；
  - Platform 会重新计算两个 Hash，不一致就拒绝；
  - Envelope 只能有 5 个顶层键，规范化后不超过 4096 字节；
  - `eventId` 须匹配 `[A-Za-z0-9][A-Za-z0-9_-]{0,127}`。
- 幂等键是 `(租户, eventId, 调用方)`：内容完全一致视为重复受理，内容不同返回 409。
- **两种状态会让 Platform 拒绝**：
  - **客户未开通**：返回 400 `BUSINESS_CHECK_FAILED`。
  - **Platform 未开启 Webhook**：受理 Controller 带 `@ConditionalOnProperty(webhook.enabled)`，没开启时整个接口不存在，返回 404。
- 调用方白名单：
  - Platform 默认配置为空，即拒绝所有调用方；
  - mor-test 已登记 `commerce-service` 的 8 种事件；
  - 本机启动脚本只登记了 `order.paid`。

### 1.6 测试设施

- `TestApplication` 用 `@EnableMockHttpServer`，`application-mock.yml` 把各服务地址指向测试端点。外部 Mock 是实现真实 API 接口的 `@RestController`（如 `MockPaymentIntentApi`、`MockRiskDecisionApi`），由对应的 Mocker 配置返回。
- 补偿 Handler 在事务提交后异步执行，`MockTestBase` 会等调度器空闲；断言经 TestMapper 读 `t_compensation_event`。
- 已有入口测试：`PaymentSucceededListenerTest`、`CancelOrderTest`、`OrderCancellationTransactionTest`、`CreateRefundTest`、`RefundCreationTransactionTest`。`RefundServiceImpl.complete` 没有任何测试。

## 2. 差异与缺口

| # | 差异 | 影响 |
|---|---|---|
| G1 | **退款终态 3 种事件在生产链路上到不了**：Commerce 还没接 Payment 退款接口（`UnavailablePaymentRefundClient` 总是失败），`complete()` 不会执行 | 测试只能走真实生产链路，不允许 Mockito 替换，所以这 3 种事件也没法用真实链路测试验证。见 D1 |
| G2 | 补偿载荷与 Platform Envelope 的上限都是 4096 字节，载荷比 Envelope 多出元数据 | 本期事件的 Envelope 约 0.6—1.2 KB，没问题；仍要在冻结时检查大小，超限应让业务事务失败，而不是悄悄丢事件 |
| G3 | `payload_json` 不保留键顺序 | Hash 必须基于 RFC 8785 规范化字节计算；冻结时算一次存入载荷，提交前从取出的 Envelope 重新规范化并核对，结果是确定的 |
| G4 | 订单取消原因码不在 Java 对象上 | 本期取消只有客户主动取消一条来源，用常量 `CLIENT_REQUESTED`；以后增加其他取消来源时，需要把原因码补到领域对象上 |
| G5 | `paymentStatus` 不落库 | `order.paid` 固定为 SUCCEEDED，与 GET 订单的推导结果一致 |
| G6 | Platform 未开启 Webhook 时返回 404，Foundation 会把它映射成 `FailException`（明确失败） | 若照搬“4xx 不重试”（Q3），Platform 只要临时关闭 Webhook 就会丢事件。见 D2 |
| G7 | Platform 返回的 204 没有正文，Commerce 客户端用的是严格 JSON 响应模式 | 阶段 3 要确认 `void` 或 `ResponseEntity<Void>` 返回值能正常处理 204 |
| G8 | 新 Handler 会在所有已有的支付成功、取消、退款测试中被触发 | 所有这些测试都需要 Platform 的测试端点与全局意外调用检查。改动落在 `MockTestBase`、`application-mock.yml`（Risk 会话有未提交改动，按约定在其基础上追加） |
| G9 | 白名单：本机 Platform 启动脚本只登记了 `order.paid` | 本机全链路验证前，`run-service.sh` 要补齐 6 种事件（仅本机） |
| G10 | 设计第 13.5 节仍写“提交后发布 MQ”，Commerce 运行时配置也没有 Platform 地址 | 更新合同说明；`application.yml` 新增 `platform-support-service` 服务组 |

## 3. 影响范围

| 系统 | 改动 | 说明 |
|---|---|---|
| Commerce | 4 个触发点登记事件；新增事件组装、补偿 Handler、Platform 客户端配置；pom 加 `platform-support-api`；`application.yml` 加服务组；测试补 Platform Mock 与入口测试 | 不改表、不加表；`t_compensation_event` 已存在 |
| Platform | 不改代码 | 本机启动脚本补白名单（验收环境配置） |
| 文档 | 设计第 13 节传输说明、Commerce README、台账、本需求 | — |
| 数据库 | 不涉及 | — |
| 其他会话 | Risk 会话的未提交改动：commerce-web pom、`application.yml`、`MockTestBase`、`application-mock.yml`。Payment 会话已确认不改 Commerce 源码 | 按约定在其改动上追加，提交时只暂存本需求的行 |

## 4. 需要你决定

| 编号 | 问题 | 建议 |
|---|---|---|
| D1 | 退款终态 3 种事件（G1） | 本期只做 `order.paid`、`order.cancelled`、`refund.created` 3 种。退款终态 3 种等“Commerce 接入 Payment 退款接口（PAY-I04）”另立需求时一并完成，届时在 `complete()` 加一处登记，并用 Payment 退款测试端点走真实链路验证。若坚持本期做，就要把接入 Payment 退款接口一起纳入，范围明显变大 |
| D2 | Platform 返回 404（Webhook 未开启或路由缺失） | 视为“暂不可用”，按可重试处理（与 5xx 相同）；只有 400 业务校验失败（客户未开通、内容非法）和 409（同一事件内容冲突）记为明确失败并告警。重试次数用完后同样留失败记录 |
| D3 | 重试次数 | 默认 5 次、最长退避 5 分钟，总窗口约 10 分钟，Platform 短时重启够用；更长的中断依赖 XXL-Job 扫描兜底（本机与 test 都未启用）。建议本期沿用默认，不新增配置 |

> 阶段 2 已确认（2026-10-17，“按建议”）：D1 本期只做 3 种事件；D2 404 可重试；D3 沿用默认重试。以下第 5—11 节阶段 3 已确认（“继续”）。

## 5. 总体方案

```text
业务事务（CommerceTransactionExecutor.write，已有）
  ├─ 原业务写入（markPaid / cancel / insert refund）
  └─ ClientWebhookEventService.register(event)        ← 新增，同一事务
        组装 data.object 与 Envelope → RFC 8785 规范化 → 两个 SHA-256
        → 大小检查 → CompensationEventRegistrar.register(CLIENT_WEBHOOK_REQUESTED, 载荷)
提交后（Foundation 唤醒，事务外）
  ClientWebhookSubmissionHandler.handle(task)          ← 新增
        读载荷 → 重新规范化并核对 Hash → WebhookEventApi.submitEvent(commerce-service, body)
        204 → success；暂不可用 → retryable；明确拒绝 → terminal + WARN
```

- 不改表、不加表；事件载荷存在已有的 `t_compensation_event.payload_json`（合同第 13.5 节明确允许事件快照这一用途）。
- 3 个触发点都只在“首次进入”分支里登记（重复消息、已取消、重放创建都在写入前返回，天然不会登记）。

## 6. 事件定义

### 6.1 公共规则

| 项 | 规则 |
|---|---|
| `eventId` | `evt_` + 资源公开编号 + `_` + 事件短名：`evt_<orderNo>_paid`、`evt_<orderNo>_cancelled`、`evt_<refundRequestNo>_created`（如 `evt_ord_3Iu73V…_paid`，约 45 字符）。每种事件对同一资源最多发生一次，所以不加版本；订单号、退款号在 Client 内唯一，事件 ID 因此在 Client 内永久唯一，重放不变 |
| `eventType` / `schemaVersion` | 公开类型；`"1"` |
| `createdAt` | 业务事务里的当前时刻（与本次写入的 `update_time` 同一值），秒精度 UTC `Z` |
| `sourceVersion` | `data.object.version`（订单版本或退款版本） |
| 金额 | 字符串，沿用 `GetOrderConverter.money` 的格式（与 GET 一致） |
| 时间字段 | UTC `Z` 字符串 |
| 大小 | 规范化后的 Envelope 与整个补偿载荷都必须 ≤ 4096 字节，超出时抛 `ErrorException`，业务事务整体回滚（本期事件不会接近上限，防止以后加字段时悄悄丢事件） |

### 6.2 `order.paid`

触发：`OrderPaymentServiceImpl.applySucceeded`，在 `markPaid` 之后（不含 `recordClosedOrderPayment`）。

```json
{"orderNo":"ord_…","clientOrderId":"…","orderStatus":"PAID","paymentStatus":"SUCCEEDED","currency":"USD",
 "amounts":{"totalAmount":"42.90","paidAmount":"42.90"},"paidAt":"2026-10-17T10:05:14Z","version":5}
```

`paidAmount` 取本次 `capturedAmount`；`version` 为 `markPaid` 后的订单版本。

### 6.3 `order.cancelled`

触发：`OrderCancellationServiceImpl.cancel`，在 `commerceOrderRepository.cancel` 与会话取消之后（覆盖“仅本地取消”和“Payment 确认终止后取消”两条路径；`requestTermination` 不登记）。

```json
{"orderNo":"ord_…","clientOrderId":"…","orderStatus":"CANCELLED","checkoutStatus":"CANCELLED",
 "reasonCode":"CLIENT_REQUESTED","cancelledAt":"2026-10-17T10:06:00Z","version":4}
```

字段名与取消接口响应（`CancelOrderResponseBody`）对齐；没有结账会话（Direct Pay）时省略 `checkoutStatus`。`reasonCode` 本期为常量（G4）。

### 6.4 `refund.created`

触发：`RefundServiceImpl.saveCreateRefundContext`，在登记退款提交任务之后。对象与第 11.3 节 GET Refund 表示一致：

```json
{"refundRequestNo":"rfd_…","clientRefundId":"refund-0001","requestOrigin":"CLIENT","orderNo":"ord_…",
 "amount":"10.00","currency":"USD","refundStatus":"PENDING","reasonCode":"REQUESTED_BY_CUSTOMER",
 "statusReasonCode":null,"createdAt":"…","completedAt":null,"updatedAt":"…","version":1}
```

复用 `GetRefundConverter` 的字段映射，确保与 GET 完全相同。

### 6.5 补偿任务

| 列 | 值 |
|---|---|
| `event_key` | `eventId` |
| `event_type` | `CLIENT_WEBHOOK_REQUESTED` |
| `sub_event_type` | 公开事件类型 |
| `payload_json` | `{"clientId":"<十进制>","canonicalizationVersion":"RFC8785","source":{"system":"commerce-service","eventKey","eventType","sourceVersion","payloadSchemaVersion":"1","sourcePayloadHash","payloadHash"},"envelope":{…}}` |

Hash 在登记时计算并写入载荷；提交前 Handler 从取出的 Envelope 重新规范化计算，与载荷中的值不一致就记为明确失败（`WEBHOOK_PAYLOAD_CORRUPTED`），不发给 Platform。

## 7. 提交到 Platform

| 项 | 设计 |
|---|---|
| 客户端 | 新增 `configuration/PlatformClientConfiguration`：`@ImportHttpServices(group="platform-support-service", types=WebhookEventApi.class)`，与 Payment / Risk 同一写法；`application.yml` 加 `spring.http.serviceclient.platform-support-service.base-url: lb://platform-support-service/platform-support` |
| 依赖 | commerce-web pom 加 `platform-support-api`（只用 `WebhookEventApi` 与请求体） |
| 调用 | `submitEvent("commerce-service", body)`，`body` 由载荷映射：`clientId`、`sourceVersion`、两个 Hash、`envelope` |
| 204 | 接口返回 `ResponseEntity<Void>`；阶段 4 先用 auto 测试确认严格 JSON 模式能正常处理无正文的 204（G7），不行再调整该客户端组选项 |

### 结果映射（D2、Q3）

| Platform 结果 | Commerce 收到 | 处理 |
|---|---|---|
| 204（含幂等重复受理） | 正常返回 | `success` |
| 5xx、超时、断连 | `ServiceUnavailableException` | `retryableFailure` |
| 404（Webhook 未开启或路由缺失） | `FailException(NOT_FOUND)` | `retryableFailure`（D2） |
| 未知错误码或正文不合规 | `ErrorException` | `retryableFailure` |
| 400 客户未开通 / 校验失败 | `BizCheckFailException` / `InvalidParameterException` | `terminalFailure` + WARN |
| 409 同一事件内容冲突 | `ConcurrentRequestException` | `terminalFailure` + WARN |
| 重试次数用完 | — | Foundation 记为失败（与明确失败一样可在任务表查到） |

日志只记 `eventId`、事件类型、`clientId`、错误码，不记载荷。

## 8. 代码结构（Commerce）

| 层 | 新增 / 修改 | 职责 |
|---|---|---|
| domain | `domain/webhook/ClientWebhookEvent`（事件类型、资源编号、clientId、版本、createdAt、object） | 待冻结事件的领域模型 |
| domain | `domain/enums/ClientWebhookEventType`（ORDER_PAID、ORDER_CANCELLED、REFUND_CREATED，含公开值与短名） | 事件类型 |
| domain service | `domainservice/service/webhook/ClientWebhookEventService` + `impl/ClientWebhookEventServiceImpl` | 由订单 / 退款对象组装事件、规范化、Hash、大小检查、登记补偿任务；必须在调用方事务内调用 |
| domain service（修改） | `OrderPaymentServiceImpl`、`OrderCancellationServiceImpl`、`RefundServiceImpl` | 在首次进入的写入之后各加一行 `register` |
| ext/job | `ClientWebhookSubmissionHandler`（`CompensationEventHandler`） | 读载荷、核对 Hash、调用 Platform、映射结果 |
| configuration | `PlatformClientConfiguration` | Platform HTTP 客户端组 |

`OrderPaymentServiceImpl` 等通过 `ObjectProvider` 取登记器（与 `RefundServiceImpl` 现有写法一致，避免 Handler Registry 启动时循环构造）。

## 9. 测试方案

| 入口 | 测试类（新增） | 场景 |
|---|---|---|
| MQ `PaymentSucceededListener` | `ClientWebhookOrderPaidTest` | 首次支付成功 → Platform 测试端点收到 1 次，字段与 GET 一致、Hash 可复算；同一消息重复投递不再提交；已关闭订单补记支付不提交 |
| HTTP 取消订单 | `ClientWebhookOrderCancelledTest` | 本地取消与 Payment 确认终止后取消都提交一次；重复取消不再提交 |
| HTTP 创建退款 | `ClientWebhookRefundCreatedTest` | 创建提交一次；同一 `clientRefundId` 重放不再提交；对象与 GET Refund 一致 |
| 补偿 Handler | `ClientWebhookSubmissionTest` | 503 → 重试后成功；404 → 重试；400、409 → 明确失败不重试；支付成功后订单再被修改，补交内容仍为冻结值；载荷被篡改 → `WEBHOOK_PAYLOAD_CORRUPTED` |

测试设施：
- 新增 `test/mock/external/MockWebhookEventApi`（实现 `WebhookEventApi` 的测试端点）与 `PlatformWebhookMocker`（配置返回状态、记录请求）。
- `application-mock.yml` 给 `platform-support-service` 指向测试端点；`MockTestBase` 全局 AfterEach 增加该 Mocker 的意外调用检查（在 Risk 会话改动上追加）。
- 已有的支付成功、取消、退款测试在默认配置下返回 204；它们不新增断言，只保证不被新任务打断。

## 10. 本机全链路验证（阶段 5）

- 本机 Platform 启动脚本把 `commerce-service` 白名单补齐 3 种事件（仅本机）。
- Commerce 从当前工作区打包，连本机 Platform；用本机测试接收端作为 Webhook 端点（沿用 `developer_flow.py` 的接收端写法）。
- 触发：
  - `order.cancelled`：经 Client API 创建结账并取消。
  - `order.paid`：向本机 RabbitMQ 发布一条合规的 PaymentSucceeded 消息，或在 Payment 会话本机联调完成后走真实支付。
  - `refund.created`：对已支付订单发起退款。退款提交到 Payment 会失败，因为 Commerce 还没接 Payment 退款接口；这不影响 `refund.created`。
- 若本机 Commerce 依赖（CCT、Risk、Payment）不全，导致无法建单，阶段 5 记为限制，只保留 auto 测试结论，并说明原因。

## 11. 文档与配置

| 项 | 改动 |
|---|---|
| [Commerce Client API V1 设计](../../../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md) 第 13.5 节 | “提交后发布 `ClientWebhookRequested`”改为“补偿任务事务外调用 Platform 内部受理接口”，链接 2026-10-15 决定；注明本期实现的 3 种事件 |
| `commerce-service/README.md` | Platform 依赖与配置项 |
| `CURRENT_IMPLEMENTATION.md` | 新增本需求条目 |
| 本机 `run-service.sh` | Platform 白名单补齐 |
| 数据库 | 不涉及（无 `db/` 目录） |
