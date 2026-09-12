# Commerce Client API 契约模块决策

- Decision status: Confirmed
- Implementation status: Implemented and locally verified
- Deployment status: Not applicable
- Decision date: 2026-09-05
- Scope: Commerce Hosted Checkout V1 的非运行 Java Owner 入站契约模块
- Path amendment: 服务入站前缀按 [2026-09-12 用户修正](./2026-09-12-commerce-client-gateway-api-path.md) 改为 `/client-gateway-api/v1`；公开 `/api/v1` 合同保持。
- Supersedes:
  - [`2026-09-05-commerce-client-api-v1.md`](./2026-09-05-commerce-client-api-v1.md) D1 与第 3 节中“只为外部调用不创建 API JAR”的部分；不取代“不创建独立 Client API 服务”及其他业务决定
  - [`2026-09-05-commerce-client-api-v1-design.md`](../../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md) 第 2.1、2.2 节及第 17 节结论 2 中“外部 DTO 全部留在 Web、外部 OpenAPI 不进入 Java API JAR”的部分
  - [`2026-09-05-commerce-client-api-v1-implementation.md`](../../docs/superpowers/plans/2026-09-05-commerce-client-api-v1-implementation.md) 中固定 `web + test` 两模块、禁止外部契约模块及把外部 DTO 创建在 Web 的部分；该旧计划在新模块专项计划形成前不得机械继续执行这些步骤
  - [`2026-09-05-commerce-modular-code-design.md`](../../docs/superpowers/specs/2026-09-05-commerce-modular-code-design.md) 中当前 Commerce Maven 多模块结构固定为 Parent、Web、Test 的部分

## 1. 背景

Commerce Hosted Checkout V1 已有语言无关 OpenAPI，但 Java Web 实现尚未开始。用户确认新增一个独立、非运行的 Java 契约模块，用编译期接口和传输模型约束未来 Controller，同时继续让外部 Client 以 OpenAPI 为唯一公开协议权威。

本决定只改变 Java 契约的落位，不改变业务状态、数据 Owner、Gateway 权限、部署单元、数据库或跨系统前置条件。

## 2. 已确认决定

### D1. 新增非运行契约模块

新增 Maven 模块：

```text
commerce-service/paytech-commerce-client-api
```

制品坐标为 `com.paytech:paytech-commerce-client-api`。它是 Commerce Owner 的外部入站 HTTP 契约 JAR，不是独立服务、Gateway Target 进程、外部 Java SDK 或未来内部 `paytech-commerce-api`。

Commerce 仍只有一个 Spring Boot 部署单元 `paytech-commerce-web`、一个数据库和一个服务发现身份 `commerce-service`。

### D2. OpenAPI 继续拥有公开 Wire Contract

公开 `/api/v1/**` 的 Method、Path、Scope、认证、Body、状态码、Header 和 Schema 继续以：

```text
commerce-service/api/openapi/client-v1.yaml
```

为唯一权威。Java 模块是面向服务端实现的编译期投影，不要求 Client 依赖，也不能自行改变公开 Wire。OpenAPI 与 Java 任一变化必须在同一变更中同步，并由漂移测试阻止不一致。

### D3. 三个能力接口

接口按业务能力拆为：

- `ClientCheckoutApi`：创建 Checkout、查询 Checkout；
- `ClientOrderApi`：按 `orderNo` 查询、按 `clientOrderId` 精确查询、取消 Order；
- `ClientRefundApi`：查询可退款金额、创建退款、列出 Order 退款、按两种公开标识查询退款。

接口使用 Spring `@HttpExchange`、`@GetExchange` 和 `@PostExchange`，声明 Gateway 到 Commerce 的私网 Owner 路径 `/client-gateway-api/v1/**`。未来 Web Controller 实现接口且不重复声明 Mapping。

### D4. 可信身份与响应语义必须进入签名

每个方法显式接收 `X-Request-Id`、`X-Gateway-Client-Id` 和 `X-Gateway-Credential-Id`。三个 POST 额外接收 `Idempotency-Key`。接口不接收 `Authorization`、Credential Secret、Tenant、Scope、Route Code、Channel、Actor、Realm 或 Session。

两个创建接口使用 `ResponseEntity<...>` 保留 `201 Created + Location`。Cancel 使用 `ResponseEntity<CancelOrderResult>` 表达确定性 `200` 与未决 `202 + Retry-After`，不得退化为 `Object`、通用 Map 或一个与 OpenAPI 不一致的合并 Body。

### D5. 传输模型和兼容性

传输模型按 `CheckoutModels`、`OrderModels`、`RefundModels` 和窄化共享模型组织，使用 `public record`。完整顶层请求和响应分别使用 `*RequestBody` 与 `*ResponseBody`。

公开状态、原因和来源字段仍以 `String` 序列化。已知 Code 可在 `api.v1.enums` 中以实现 `CodeEnum` 的枚举发布，但 DTO 不使用封闭 Java enum 字段；未知未来 Code 必须能被读取并进入调用边界的 Fail-Closed 处理。

日期时间使用 `Instant`，金额和数量使用普通十进制 `String`，不得使用 `double`、`float` 或 JSON number。

### D6. 使用 Spring MVC，不使用响应式类型

本模块只使用 Spring MVC 所需的 `spring-web` 契约注解。不得引入：

- `spring-webflux`；
- `reactor-core` 或其他 `io.projectreactor` 制品；
- `Mono`、`Flux` 或响应式 Controller 签名。

“加入 Maven 多模块构建”只表示父 POM 按依赖顺序构建 `client-api -> web -> test`，与 Project Reactor 响应式编程无关。

### D7. 本切片不创建运行时占位实现

模块专项切片只创建真实接口、DTO、Code 和合同测试。缺少业务实现和 Gate 0 前置时不创建空 Controller、空 Processor、假 Owner Client、临时错误 Advice 或固定返回值。

Foundation RFC 9457/JCS/COMP-01、CCT/Risk/Finance/Payment/Platform typed contracts、Gateway/Nginx 和 Hosted Checkout 页面门禁不因本决定解除。

### D8. Wire 修正随模块投影同步完成

在公开 Route 尚未激活的前提下，同步闭合三个已发现的合同缺口：

1. `DecimalAmount` 与 `PositiveDecimalAmount` 的最大整数位从 24 位收紧为 16 位，使总精度真正符合 `DECIMAL(24,8)`；
2. `RefundResponse.clientRefundId` 保持 required-but-nullable，但非空值复用完整 `ClientReference` 限制；
3. Cancel 的 `202` 响应在 OpenAPI 中显式声明 `Retry-After`。

这些修正不得被描述为已部署兼容迁移；当前 Client API 尚未激活。

## 3. 明确不做

- 不创建新的可执行 Client API 服务、端口、数据库、Nacos Service Name 或部署资源；
- 不创建 Order、Checkout、Refund 业务 Maven JAR；
- 不把本模块作为内部服务调用 API，未来私网 `paytech-commerce-api` 必须使用不同路径和 DTO；
- 不从 OpenAPI 生成并提交一套并行 Java 源码；V1 采用手写接口与自动漂移测试；
- 不在本模块定义 Controller、Service、Processor、Repository、Mapper、DataObject、异常 Advice、HTTP Client Factory 或业务实现；
- 不引入 DirectPay、Payment Method、Provider/MID 选择、Consumer PII 或自由 Metadata。

## 4. 权威与后继

- 完整模块设计由 [`2026-09-05-commerce-client-api-contract-module-design.md`](../../docs/superpowers/specs/2026-09-05-commerce-client-api-contract-module-design.md) 持有。
- 公开 Hosted Checkout V1 业务与 Wire 语义继续由原 Commerce Client API V1 决策和设计拥有，只有本决定列出的模块落位与三个 Wire 缺口被取代。
- 实施、验证和部署状态只由 [`CURRENT_IMPLEMENTATION.md`](../CURRENT_IMPLEMENTATION.md) 记录；设计确认不等于 Java 模块已经存在。
