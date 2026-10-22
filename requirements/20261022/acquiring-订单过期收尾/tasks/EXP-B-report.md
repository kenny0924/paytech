# EXP-B 报告：acquiring 真实 Payment 终止客户端与测试设施

> 执行代理报告，2026-10-22。工作目录 `/Users/kenny/Desktop/Workspace/Code-MoR-expiry`（detached HEAD，基线 `42879401` + EXP-A 未提交改动），未提交、未推送、未执行共享环境 SQL、未部署，未改 payment 生产代码。

## 1. 结论

- Cancel 已改用真实 PAY-I12：有支付投影时直接按投影 Intent 终止，无投影但有接受证据时先按来源查询再终止（AC-07）。
- `FixedPaymentTerminationClient` 已删除。`ALREADY_PAID` 与 `REVERSAL_REQUIRED` 一样返回 409 `REVERSAL_REQUIRED`。
- 门禁：acquiring `auto` 809 例全部通过；全 reactor `test-compile` 通过，118 个模块。
- 无偏离裁决的地方。有一处测试观察方式替换，见第 7 节。

## 2. 已读规范与合同

- `AGENTS.md`（根）。`paytech/acquiring/` 下没有 AGENTS.md，README 未涉及本卡内容。
- `agent-docs/DEVELOPMENT_CONVENTIONS.md`
- `agent-docs/JAVA_PROJECT_STRUCTURE.md`
- `agent-docs/examples/WRITE_API_EXAMPLE.md`
- `agent-docs/UNIT_TEST_GUIDE.md`
- `agent-docs/examples/UNIT_TEST_EXAMPLE.md`
- `agent-docs/decisions/2026-09-17-test-dto-mock-lifecycle.md`
- 任务卡 `tasks/cards.md`（EXP-B、1.1 裁决、第 2 节门禁、第 3 节重叠面）
- `technique.md` 第 1.1、2.1（R2 / R3）、3.2、3.3.1—3.3.3、3.6 节；`requirement.md` AC-07；`tasks/EXP-A-report.md`
- 风格参照：`CheckoutCardPaymentPortImpl`、`PaymentCheckoutClientImpl`、`PaymentSubmissionMocker`、`PaymentTransactionProbe`

本次入口是 `CancelOrderProcessor`（acquiring core，无锁 `AbstractProcessor`），Context 为 `OrderCancellationContext`。本地原子写入未变：先由 `requestTermination` 短事务提交终止意图，再在事务外调用 Payment，确认终止后由 `cancel` 在一个事务内写订单、会话和 Webhook 登记。

## 3. 改动文件

路径相对 `paytech/acquiring/`。

**生产代码（paytech-acquiring-core）**

| 文件 | 说明 |
|---|---|
| `integration/payment/PaymentTerminationClient.java` | 端口合同按 3.3.3 改造，JavaDoc 改述为真实合同 |
| `integration/payment/impl/PaymentTerminationClientImpl.java`（新增） | `@Component`，经 `PaymentIntentApi` 实现 |
| `integration/payment/FixedPaymentTerminationClient.java`（删除） | 固定 Mock 移除 |
| `service/processor/order/CancelOrderProcessor.java` | `terminatePayment` 传投影 Intent 与 `ORDER_CANCELLED`；`ALREADY_PAID` 返回 409 |
| `domain/customer/CustomerCheckoutModels.java` | `IntentRequest` 抽出常量 `COMMERCE_ORDER_SOURCE_TYPE`，创建 Intent 与终止客户端共用 |

**测试代码（paytech-acquiring-test）**

| 文件 | 说明 |
|---|---|
| `test/mocker/external/PaymentTerminationMocker.java` | 由 `@Primary` 端口实现改为 HTTP 侧 Mocker |
| `test/mock/external/MockPaymentIntentApi.java` | `getIntentBySource`、`terminateIntent` 委托 Mocker 返回 DTO、404 或故障 |
| `test/base/MockTestBase.java` | AfterEach 的意外调用检查加入 `PaymentTerminationMocker` |
| `auto/.../order/cancel/CancelOrderTest.java` | 断言改为 HTTP 调用记录，新增场景 |
| `auto/.../order/cancel/ClientWebhookOrderCancelledTest.java` | 改为按 Intent 配置终止决定 |
| `auto/.../customerapi/v1/payment/CheckoutIntentCreationTest.java` | 去掉类级默认终止配置，改为逐例配置来源查询与终止 |

## 4. 端口合同与映射

```java
record Request(long clientId, long orderId, Long paymentIntentId, TerminationReason reason) {}
record Result(long orderId, Status status) {}
enum Status { TERMINATED, PENDING, REVERSAL_REQUIRED, ALREADY_PAID }
enum TerminationReason { ORDER_CANCELLED, ORDER_EXPIRED }  // CodeEnum，Code 与 Payment reason 一致
```

`PaymentTerminationClientImpl.terminate` 的行为：

1. `paymentIntentId` 为空时调用 `getIntentBySource("COMMERCE_ORDER", orderId, clientId)`。响应的来源类型、来源 ID 必须与请求一致，Intent ID 必须为合法正整数，否则抛 `CheckoutUpstreamProtocolException`。
2. 调用 `terminateIntent(intentId, {clientId, reason})`。
3. 两个调用都要求 200 且有响应体，否则抛 `CheckoutUpstreamProtocolException`。
4. 终止响应的 `intentId` 与请求不一致时抛 `CheckoutUpstreamProtocolException`。

决定映射：

| Payment decision | 端口 Status |
|---|---|
| TERMINATED、ALREADY_TERMINATED | TERMINATED |
| PENDING | PENDING |
| REVERSAL_REQUIRED | REVERSAL_REQUIRED |
| ALREADY_PAID | ALREADY_PAID |
| 其他或缺失 | 抛 `CheckoutUpstreamProtocolException` |

错误处理：公共客户端已把 Payment 404 解码为 code 为 `NOT_FOUND` 的 `FailException`，实现不捕获，直接抛给调用方。400 等其他 `FailException` 同样原样抛出，符合 1.1 裁决“只有 404 代表 Intent 不存在”。传输失败和 5xx 由公共客户端映射为 `ServiceUnavailableException`，实现不捕获。

`CancelOrderProcessor` 的其余逻辑不变：`NOT_FOUND` 走本地取消，`ServiceUnavailableException` 保留待定（202）。

## 5. 测试设施

`PaymentTerminationMocker`（`@TestComponent`，实现 `Clear`）：

- 终止按 intentId 配置：`mockDecision`（端口 Status 或决定 Code）、`mockTermination`（任意 DTO，用于错归属负例）、`mockTerminationNotFound`、`mockTerminationFault`。
- 按来源查询按 orderId 配置：`mockSource`、`mockSourceNotFound`。
- 记录 `calls()`（intentId、clientId、reason）与 `sourceQueries()`（sourceType、sourceId、clientId），期望值用 `Termination.of(..., TerminationReason)` 和 `SourceQuery.of(...)` 按枚举构造。
- 未配置的调用和非 `COMMERCE_ORDER` 来源记为意外调用，Mock 返回 500。`MockTestBase` 在清理前调用 `assertNoUnexpectedCalls`，即使业务把 500 当作依赖不可用吞掉，测试也会失败。
- `clear()` 每例清空全部配置与记录。

已做一次反向验证：临时去掉一个用例的终止配置后，该用例失败，并报出“存在未配置的 Payment 终止链路调用”和 Mock HTTP 意外请求。验证后文件已还原。

## 6. 测试场景与结果

**CancelOrderTest 场景变化**

- 保留并改写：有投影终止（断言只终止、不按来源查询、reason 为 `ORDER_CANCELLED`）；接受后无投影 PENDING 重提；Payment 503 Problem 保留待定、重提成功；非 404 错误（400 `BUSINESS_CHECK_FAILED`）不取消。
- 新增：
  - `ALREADY_PAID` 返回 409 `REVERSAL_REQUIRED`，并入 `test_terminationNotCompleted` 参数。
  - 无投影有接受证据，按来源查询 404 时本地取消且不终止（`test_sourceNotFoundCancelsWithoutCreatingIntent`）。
  - 无投影有接受证据，按来源查询命中后终止并取消（`test_sourceFoundTerminatesLocatedIntent`）。
  - 有投影时终止返回 404 也本地取消（`test_terminationNotFoundCancels`）。
  - `ALREADY_TERMINATED` 本地取消（`test_alreadyTerminatedCancels`）。
  - 终止响应错归属或未知决定返回 502（`test_terminationRejectsInvalidResponse`）。

`CheckoutIntentCreationTest` 保留原语义：创建超时后取消改为按来源命中并终止；创建中途取消改为按来源 404；终止断连保留 202 后重提成功。

**命令**（在 `paytech/` 下执行；从主工作区 `local-environment/.env.local` 只读导出 `MOR_RABBITMQ_USER`、`MOR_RABBITMQ_PASSWORD`、`MOR_RABBITMQ_VHOST`、`MOR_REDIS_PASSWORD` 四项，未打印）：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
./mvnw -o -pl acquiring/paytech-acquiring-test -am test -Dtest='com.paytech.acquiring.auto.**' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment
./mvnw -o test-compile
```

| 步骤 | 结果 |
|---|---|
| 三个改造类 | CancelOrderTest 52、ClientWebhookOrderCancelledTest 4、CheckoutIntentCreationTest 42，全部通过 |
| acquiring 全部 auto | 809 通过 / 0 失败 / 0 错误 / 0 跳过，BUILD SUCCESS |
| 全 reactor `test-compile` | BUILD SUCCESS，118 个模块 |

运行中有两次失败，均已解决，不是既有缺陷：

1. 首轮 Spring 上下文加载失败：RabbitMQ `ACCESS_REFUSED`。acquiring 的 `application-mock.yml` 从环境变量读 Rabbit 凭据，`-Dpaytech.local-environment.dir` 不负责这部分。导出上述四项后正常。
2. 两例返回 502：测试构造的 Problem `title` 不是标准原因短语，被严格解码器拒绝。改用 `HttpStatus.getReasonPhrase()` 后通过。

未使用 Mockito Stub 或 `@MockBean`。

## 7. WRITE_API_EXAMPLE 第 11 节自查

| 检查项 | 结果 |
|---|---|
| API 与 Body 合同 | 不适用。未改 HTTP 合同，只消费 EXP-A 发布的 Payment API |
| Controller / Converter 仅适配；Processor 位置 | 符合。Controller 未动；`CancelOrderProcessor` 主流程阶段不变 |
| Processor 只查本模块 Repository | 符合。终止客户端是 integration 端口，在事务外调用 |
| Context | 符合。`OrderCancellationContext` 未增字段，Intent ID 取自已持有的投影 |
| Service 不调外部 | 符合。`OrderCancellationService` 未改 |
| 本地原子写入与事务位置 | 符合。`requestTermination` 先提交，Payment 调用在事务外，测试经 `PaymentTransactionProbe` 在调用方线程断言无活动事务 |
| Repository 与对象同步 | 不适用。未改仓储与 Mapper |
| 锁 | 不适用。Cancel 保持无锁（technique 第 3.2 节第 2 点） |
| 原请求比较 | 保持。已取消订单重提直接返回原事实，测试断言不再外呼 |
| Gateway 请求幂等 | 不涉及。未新增 Idempotency-Key 或命令幂等组件 |
| View | 不适用 |
| 无生产 Mock、无多余抽象 | 符合。固定 Mock 已删除；新增只有一个实现类和一个常量 |
| 实际验证 | 见第 6 节 |

## 8. 偏离与说明

1. **事务外观察方式。** 原端口 Mocker 在调用线程记录事务状态（`assertOutsideTransaction`）。改为 HTTP 后 Mock 线程无法观察调用方事务，所以 `CancelOrderTest` 改用已有的 `PaymentTransactionProbe`，它挂在真实 Payment 客户端上记录调用方线程的事务状态。
2. **`ALREADY_TERMINATED` 在测试中用字符串常量。** payment-api 没有发布决定枚举，端口也不区分该值，所以在 `PaymentTerminationMocker.ALREADY_TERMINATED` 集中定义一次。其他决定都用端口 `Status` 枚举。
3. **按来源查询多校验了来源归属。** 响应的 sourceType、sourceId 与请求不符时抛协议错误，与 `CheckoutCardPaymentPortImpl.queried` 的归属校验同样处理。
4. **Rabbit 凭据。** 工作目录运行 acquiring 测试需要导出上述环境变量，`-Dpaytech.local-environment.dir` 不够。EXP-C、EXP-D 运行时同样需要。

## 9. 未完成项

- 卡内工作全部完成，未进入 EXP-C。
- `PaymentSucceededMessageConverter` 里仍有一处 `"COMMERCE_ORDER"` 字面量，不在本卡范围，未改。
