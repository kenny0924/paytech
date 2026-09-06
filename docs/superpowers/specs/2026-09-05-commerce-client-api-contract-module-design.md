# Commerce Client API Java 契约模块设计

- Decision status: Confirmed
- Written spec review: Completed (2026-09-05)
- Implementation status: Implemented and locally verified
- Deployment status: Not applicable
- Decision date: 2026-09-05
- Decision record: [`2026-09-05-commerce-client-api-contract-module.md`](../../../agent-docs/decisions/2026-09-05-commerce-client-api-contract-module.md)
- Public Wire authority: [`commerce-service/api/openapi/client-v1.yaml`](../../../commerce-service/api/openapi/client-v1.yaml)
- Scope: Hosted Checkout V1 的非运行 Java Owner 入站契约模块

本文定义 `paytech-commerce-client-api` 的 Maven 边界、Java 包、十条接口投影、DTO、兼容性、依赖和验证方式。它不实现 Controller、业务处理、数据库、跨系统调用、Gateway Route 或部署。

## 1. 目标与边界

### 1.1 目标

新增一个可编译、可发布但不可运行的 Java 契约 JAR，使未来 `paytech-commerce-web` Controller 通过实现接口获得编译期约束，并由自动测试持续验证 Java 投影与公开 OpenAPI 一致。

本模块服务于 Commerce Owner 的服务端实现，不是要求外部 Client 使用的 Java SDK。外部 Client 仍可使用任意语言，并只依赖公开 OpenAPI 和 HTTP 文档。

### 1.2 不变边界

```text
Client Server
  -> public /api/v1/**
  -> Nginx / API Gateway
  -> private /commerce/external/client-api/v1/**
  -> paytech-commerce-web Controller
  -> Commerce business implementation
```

- Gateway 继续负责 Credential、Scope、Guard、路由和可信上下文；
- Commerce 继续负责资源归属、状态、事务、幂等和业务结果；
- `paytech-commerce-web` 继续是唯一 Commerce Spring Boot 部署单元；
- 外部路径和私网 Owner 路径保持分离；
- 本模块不改变 Gate 0 或任何跨系统 Owner 合同。

## 2. Maven 多模块结构

目标结构：

```text
commerce-service/
├── pom.xml
├── api/openapi/client-v1.yaml
├── paytech-commerce-client-api/
│   ├── pom.xml
│   └── src/main/java/com/paytech/commerce/client/api/v1/
├── paytech-commerce-web/
└── paytech-commerce-test/
```

依赖方向：

```text
paytech-commerce-test
        |
        v
paytech-commerce-web
        |
        v
paytech-commerce-client-api
```

父 POM 的 `<modules>` 按 `paytech-commerce-client-api`、`paytech-commerce-web`、`paytech-commerce-test` 排列；Maven 会依据真实依赖图构建。这里不使用 Project Reactor 响应式编程。

### 2.1 制品

- Group ID：`com.paytech`
- Artifact ID：`paytech-commerce-client-api`
- Packaging：`jar`
- 版本：继承 `paytech-commerce-service` 的 `${project.version}`
- 发布性质：可发布的编译期契约 JAR
- 运行性质：无 Main Class、无 Spring Boot Application、无独立配置或端口

### 2.2 最小依赖

允许的直接依赖只有：

- `com.paytech:paytech-common-core`：`CodeEnum`；
- `org.springframework:spring-web`：`@HttpExchange`、方法注解、MVC 参数注解和 `ResponseEntity`；
- `jakarta.validation:jakarta.validation-api`：传输边界约束；
- `com.fasterxml.jackson.core:jackson-annotations`：明确 nullable/optional JSON 字段和 Cancel sealed result 的序列化意图。

版本全部来自既有 Parent/BOM，子模块不声明版本。

明确禁止 `spring-boot-*`、`spring-webflux`、`io.projectreactor:*`、数据库、MyBatis、Redis、MQ、Nacos、HTTP Client 自动配置和其他业务系统 API。

## 3. Java 包与文件职责

```text
com.paytech.commerce.client.api.v1
├── common/
│   ├── ClientApiHeaders.java
│   ├── ClientApiPaths.java
│   ├── ClientApiConstraints.java
│   └── ClientApiModels.java
├── checkout/
│   ├── ClientCheckoutApi.java
│   └── CheckoutModels.java
├── order/
│   ├── ClientOrderApi.java
│   └── OrderModels.java
├── refund/
│   ├── ClientRefundApi.java
│   └── RefundModels.java
├── enums/
│   ├── CancellationReason.java
│   ├── CancellationStatus.java
│   ├── CheckoutStatus.java
│   ├── FundsAvailabilityStatus.java
│   ├── OrderStatus.java
│   ├── PaymentStatus.java
│   ├── RefundReason.java
│   ├── RefundRequestOrigin.java
│   └── RefundStatus.java
└── package-info.java
```

- `ClientApiHeaders` 只定义四个协议 Header 常量：三个可信 Gateway Header 和 `Idempotency-Key`；
- `ClientApiPaths` 只定义 `/external/client-api/v1` 私网 Base Path；公开 `/api/v1` 不进入 Java接口常量；
- `ClientApiConstraints` 集中定义 Header、公开标识、币种、国家和十进制字符串的编译期正则常量，接口与 DTO 不复制同义表达式；
- `ClientApiModels` 只保存多个能力真实共享的 `Amounts`，不演化成通用 `common.jar`；
- 三个 `*Api` 只声明 HTTP 方法，不内嵌 DTO；
- 三个 `*Models` 是 `public final` 容器，私有构造器，内部模型使用 `public record`；
- 枚举只发布当前已知 Code 并实现 `CodeEnum`，DTO 字段类型保持 `String`。

## 4. HTTP 路径和可信上下文

### 4.1 两层路径

| 层级 | Base Path | Owner |
|---|---|---|
| 公开 Client Wire | `/api/v1` | OpenAPI + Gateway Route Catalog |
| Commerce 私网接口 | `/external/client-api/v1` | Java `@HttpExchange` 接口 |
| 应用实际路径 | `/commerce/external/client-api/v1` | Context Path + 私网接口 |

接口类级注解使用：

```java
@HttpExchange(value = ClientApiPaths.V1, accept = MediaType.APPLICATION_JSON_VALUE)
```

公共 Bearer Credential 不进入方法签名；Gateway 完成认证后不向 Commerce 转发它。

### 4.2 每个方法的必需可信 Header

每个方法重复声明以下三个参数，避免通用 Map 或隐式全局变量扩大可信面：

```java
@RequestHeader("X-Request-Id") String requestId
@RequestHeader("X-Gateway-Client-Id") String clientId
@RequestHeader("X-Gateway-Credential-Id") String credentialId
```

约束为：

- `requestId`：1–64 字符，首字符为 ASCII 字母或数字，其余仅允许 ASCII 字母、数字、`.`、`_`、`-`；
- `clientId` 与 `credentialId`：正十进制、最多 19 位；
- Header 必须是单值；重复 Header 的拒绝由 Web 可信上下文边界测试保证，不能依赖普通字符串参数静默合并。

三个 POST 另声明：

```java
@RequestHeader("Idempotency-Key") String idempotencyKey
```

其长度为 1–255，首字符为字母或数字，其余只允许字母、数字、`.`、`_`、`:`、`-`，大小写敏感。

## 5. 三个接口与十个操作

下表省略每个方法都重复的三个可信 Header；`IK` 表示额外的 `Idempotency-Key`。

| Java 接口 / 方法 | 私网 Method + Path | 额外输入 | Java 正常返回 |
|---|---|---|---|
| `ClientCheckoutApi.createCheckout` | `POST /external/client-api/v1/checkouts` | `IK`、`@Valid CreateCheckoutRequestBody` | `ResponseEntity<CreateCheckoutResponseBody>`；只能为 `201 + Location` |
| `ClientCheckoutApi.getCheckout` | `GET /external/client-api/v1/orders/{orderNo}/checkout` | `orderNo` | `CheckoutResponseBody`；200 |
| `ClientOrderApi.getOrder` | `GET /external/client-api/v1/orders/{orderNo}` | `orderNo` | `OrderResponseBody`；200 |
| `ClientOrderApi.getOrderByClientOrderId` | `GET /external/client-api/v1/orders?clientOrderId=` | `clientOrderId` | `OrderResponseBody`；200 |
| `ClientOrderApi.cancelOrder` | `POST /external/client-api/v1/orders/{orderNo}/cancel` | `orderNo`、`IK`；无 Body | `ResponseEntity<CancelOrderResult>`；200 或 202 |
| `ClientRefundApi.getRefundableAmount` | `GET /external/client-api/v1/orders/{orderNo}/refundable-amount` | `orderNo` | `RefundableAmountResponseBody`；200 |
| `ClientRefundApi.createRefund` | `POST /external/client-api/v1/orders/{orderNo}/refunds` | `orderNo`、`IK`、`@Valid CreateRefundRequestBody` | `ResponseEntity<CreateRefundResponseBody>`；只能为 `201 + Location` |
| `ClientRefundApi.listOrderRefunds` | `GET /external/client-api/v1/orders/{orderNo}/refunds` | `orderNo`、可选 `limit`/`cursor` | `RefundListResponseBody`；200 |
| `ClientRefundApi.getRefund` | `GET /external/client-api/v1/refunds/{refundRequestNo}` | `refundRequestNo` | `RefundResponseBody`；200 |
| `ClientRefundApi.getRefundByClientRefundId` | `GET /external/client-api/v1/refunds?clientRefundId=` | `clientRefundId` | `RefundResponseBody`；200 |

`limit` 缺失时由接口默认到 20，范围 1–100；`cursor` 最长 512。两个按 Client ID 查询的 GET 都是精确单资源查询，不是列表。

## 6. 传输模型

### 6.1 共享模型

`ClientApiModels.Amounts`：

- `merchandiseAmount`
- `discountAmount`
- `netAmount`
- `taxAmount`
- `roundingAmount`
- `totalAmount`
- 可选 `paidAmount`

所有字段都是十进制字符串；前六项必填且不可为 null，`paidAmount` 缺失时不输出 JSON `null`。

### 6.2 CheckoutModels

`CreateCheckoutRequestBody`：

- `clientOrderId`
- `currency`
- `consumerCountryCode`
- 可选 `expiresAt: Instant`
- `items: List<CheckoutItemRequest>`

`CheckoutItemRequest`：

- `clientItemId`
- `name`
- 可选 `description`
- `productTypeCode`
- `taxCategoryCode`
- `quantity`
- `unitAmount`
- 可选 `discountAmount`

`CreateCheckoutResponseBody`：

- `orderNo`
- `clientOrderId`
- `checkoutUrl`
- `currency`
- `amounts`
- `expiresAt: Instant`

`CheckoutResponseBody`：

- `orderNo`
- `clientOrderId`
- `checkoutStatus: String`
- required-but-nullable `checkoutUrl`
- `expiresAt: Instant`
- `updatedAt: Instant`
- `version: int`

### 6.3 OrderModels

`OrderResponseBody` 与 OpenAPI 保持以下字段：

- `orderNo`、`clientOrderId`
- `orderStatus`、`checkoutStatus`、`paymentStatus`
- `currency`、`consumerCountryCode`
- `items: List<OrderItem>`
- `amounts: Amounts`
- 可选且 nullable 的 `lastPaymentAttempt`
- `refundSummary`
- `fundsAvailability`
- `createdAt`、`expiresAt`
- 可选且 nullable 的 `paidAt`
- `updatedAt`
- `version`

嵌套模型精确为：

- `OrderItem(clientItemId, name, description, productTypeCode, taxCategoryCode, quantity, unitAmount, discountAmount)`；
- `LastPaymentAttempt(status)`；
- `RefundSummary(processingAmount, succeededAmount, refundableAmount)`；
- `FundsAvailability(status, availableAmount, scheduledAt, availableAt, asOf, updatedAt, version)`，前三个金额/时间值按 OpenAPI 保持 required-but-nullable。

Cancel 使用：

```text
sealed interface CancelOrderResult
  |- CancelOrderResponseBody
  |    orderNo, orderStatus, checkoutStatus, cancelledAt, version
  `- CancellationPendingResponseBody
       orderNo, cancellationStatus, reasonCode
```

`CancellationPendingResponseBody` 只用于 HTTP 202，且 `ResponseEntity` 必须携带合法 `Retry-After`；`CancelOrderResponseBody` 只用于 HTTP 200。

### 6.4 RefundModels

`CreateRefundRequestBody`：

- `clientRefundId`
- `amount`
- `reasonCode`
- 可选 `reasonSummary`

`RefundableAmountResponseBody`：

- `orderNo`
- `currency`
- `refundableAmount`
- `asOf: Instant`
- `version`

`CreateRefundResponseBody`：

- `refundRequestNo`
- `clientRefundId`
- `orderNo`
- `amount`
- `currency`
- `createdAt: Instant`

`RefundResponseBody`：

- `refundRequestNo`
- required-but-nullable `clientRefundId`
- `requestOrigin`
- `orderNo`
- `amount`
- `currency`
- `refundStatus`
- `reasonCode`
- required-but-nullable `statusReasonCode`
- `createdAt`
- required-but-nullable `completedAt`
- `updatedAt`
- `version`

`RefundListResponseBody`：

- `items: List<RefundResponseBody>`
- required-but-nullable `nextCursor`

## 7. 校验与 JSON 规则

### 7.1 字符串与集合

- `ClientReference`：1–128 个可打印 ASCII 字符，正则 `^[!-~]+$`；
- `orderNo`、`refundRequestNo`：1–64，大小写敏感；
- currency：`^[A-Z]{3}$`；
- country：`^[A-Z]{2}$`；
- Checkout items：1–100；
- name：1–255；description：最多 1024；
- product/tax code：1–64；
- refund reason summary：最多 512；
- Refund 创建 reasonCode 只接受 `REQUESTED_BY_CUSTOMER | DUPLICATE | FRAUDULENT | OTHER`。

### 7.2 十进制

普通非负十进制：

```regex
^(?:0|[1-9][0-9]{0,15})(?:\.[0-9]{1,8})?$
```

正十进制在同一格式上额外要求至少一位非零数字。两者都禁止符号、指数、前导零和超过 16 位整数/8 位小数，确保最多 24 位有效数字，与 `DECIMAL(24,8)` 一致。

币种 exponent、逐行金额恒等式、总额计算、`expiresAt` 相对窗口和资源归属不是 DTO 格式校验，留给 Web 业务边界。

### 7.3 optional 与 nullable

- optional 且非 nullable 的 JSON 字段缺失时映射为 Java `null`，序列化时必须省略；显式 JSON `null` 必须由 Web 反序列化边界拒绝；
- required-but-nullable 字段即使值为 null 也必须保留 JSON key；
- OpenAPI `RefundResponse.clientRefundId` 使用 `ClientReference | null`，非空值不能绕过长度和字符约束；
- Java annotations 和 ObjectMapper 合同测试必须证明上述区别，不依赖环境默认值。

请求对象继续拒绝未知字段；响应对象允许未来增加未知字段。Java DTO 不把 `x-known-values` 变成拒绝未知值的封闭反序列化类型。

## 8. 成功、错误与 Header

### 8.1 成功

- Create Checkout：201，Body 为冻结的 `CreateCheckoutResponseBody`，`Location` 是 `/api/v1/orders/{orderNo}` 形式的安全相对路径；
- Create Refund：201，Body 为冻结的 `CreateRefundResponseBody`，`Location` 是 `/api/v1/refunds/{refundRequestNo}`；
- Cancel definitive：200 + `CancelOrderResponseBody`；
- Cancel pending：202 + `CancellationPendingResponseBody` + `Retry-After`；
- 其余查询：200 + 对应 Body。

Gateway 重建 `X-Request-Id`、`Cache-Control: no-store` 和 `Pragma: no-cache`；这些通用响应 Header 不要求每个 Java方法返回重复模型。

### 8.2 错误

Java 方法只声明正常 typed 返回。4xx/5xx 继续由共享 Foundation Client API RFC 9457 策略输出：

```json
{
  "type": "...",
  "title": "...",
  "detail": "...",
  "code": "...",
  "errors": []
}
```

`errors` 可选；Body 不含 `status`、`requestId` 或 `timestamp`。本模块不创建 `Problem` Java DTO、不定义异常 Advice，也不绕过尚未完成的 Foundation Gate。

## 9. Web 实现边界

本专项切片不创建 Controller。后续运行时任务满足 Gate 后：

- `ClientCheckoutController implements ClientCheckoutApi`；
- `ClientOrderController implements ClientOrderApi`；
- `ClientRefundController implements ClientRefundApi`；
- Controller 只标注 `@RestController`，不重复声明 HTTP Mapping；
- Controller 把传输 DTO 和可信 Header 转换为本地 Application/Domain 输入；
- DTO 不进入 Repository/Mapper/DataObject，Domain 对象也不反向泄露到契约模块；
- Controller 不直接访问 Mapper，不持有业务事务，不编排跨系统恢复。

## 10. 版本和演进

- Java package 与私网路径都包含 `v1`；
- 新增公开可选字段可以保留 V1，但必须同步 OpenAPI、Java模型和测试；
- 删除字段、改变含义、改变必填性或已有状态语义进入 V2；
- 已知状态 Code 可以增加，DTO 的 String 字段必须继续接收未知值；
- 未来内部 Commerce Java API 使用独立 `paytech-commerce-api`、私网能力路径和内部 DTO，不依赖或复用本模块；
- 如果未来真的提供外部 Java SDK，应由 OpenAPI 派生单独 SDK 设计，不能把 Owner 入站 JAR冒充 SDK。

## 11. TDD 与验收

实现先写失败测试并实际观察 RED，再创建生产模块。测试统一位于 `paytech-commerce-test`，不在契约模块建立空测试模块。

### 11.1 Maven 与纯度

- 父 POM 精确包含 `client-api + web + test`；
- `web` 依赖 `client-api`，`client-api` 不依赖 `web/test`；
- `dependency:tree` 证明没有 `spring-webflux`、`io.projectreactor:*`、Boot Starter、数据库或其他 Owner API；
- API JAR 中不存在 Main、`@SpringBootApplication`、Controller、Service、Processor、Repository、Mapper、DataObject、配置文件或 Mapper XML。

### 11.2 接口反射合同

- 精确存在三个接口和十个 operation；
- Method、私网 Path、Header、Path/Query/Body 参数及 Bean Validation 注解与本设计一致；
- 三个 POST 都且只有它们接收 `Idempotency-Key`；
- 两个创建接口保留 `ResponseEntity` 和 Location 语义；
- Cancel 返回 sealed result，并对 200/202 Body 与 Retry-After 做测试；
- 所有方法都不接受 Authorization/Tenant/Scope/Channel 等禁止字段。

### 11.3 DTO 和 OpenAPI 漂移

- Java record 字段名、类型、required/optional/nullable、长度、正则、集合大小和已知 Code 与 OpenAPI 双向一致；
- JSON round-trip 覆盖全部顶层模型、required-null key、optional-null omission、未知状态字符串及多余请求字段拒绝；
- 合成破坏样本证明漂移测试能拒绝新增未声明 Java 字段、错误 Path、缺失 Header、封闭 enum 和放宽金额精度；
- OpenAPI 同步增加 Cancel 202 Retry-After，并修正 Decimal 与 nullable Client Reference。

### 11.4 完整验证

```bash
cd commerce-service
./mvnw clean verify
./mvnw dependency:tree
git diff --check

cd ..
npm --prefix requirements-site test
npm --prefix requirements-site run lint
git diff --check
```

设计文档和契约模块通过只证明 Java合同可编译且与 OpenAPI 对齐，不证明 Controller、数据库、Gateway、Hosted Checkout 页面、端到端调用或部署可用。

## 12. 实施范围与文档治理

专项实施计划只允许修改：

- Commerce 父/Web/Test POM；
- 新的 `paytech-commerce-client-api` 源码；
- OpenAPI 的三项精确修正；
- Commerce 合同、模块边界和架构测试；
- 本决策、当前设计、旧设计/计划的窄 supersede 标记；
- 有实际验证证据后的 Commerce README 与 `CURRENT_IMPLEMENTATION.md`。

现有 `docs/database/COMMERCE_PHYSICAL_DECISIONS.md` 修改属于用户资产，本切片不触碰。数据库、业务 Controller、Foundation 前置和其他系统合同不在本切片内。

## 13. 已确认结论

1. 新增 `com.paytech:paytech-commerce-client-api`，它是非运行的 Owner 入站契约 JAR。
2. OpenAPI 仍是公开 `/api/v1/**` 唯一权威，Java 接口使用私网 `/external/client-api/v1/**`。
3. 使用三个能力接口和三个 Models 容器，不创建一个巨型接口或十个机械接口。
4. 每个方法显式接收三个可信 Gateway Header；三个 POST 额外接收幂等 Key。
5. Java 使用普通 DTO/`ResponseEntity`，不使用 WebFlux、Project Reactor、`Mono` 或 `Flux`。
6. 公开 Code 字段使用 String，已知 enum 仅作为 Code 目录。
7. 本切片只建立真实契约和测试，不创建空运行时实现，也不解除 Gate 0。
8. 金额精度、nullable Client Reference 和 Cancel Retry-After 与 Java投影同步修正。
