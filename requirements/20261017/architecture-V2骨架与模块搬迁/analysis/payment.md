# Payment 服务边界盘点（只读）

- 盘点对象：`/Users/kenny/Desktop/Workspace/Code-MoR-refactor/payment-orchestration-service`（下文记为 `P/`）
- 盘点日期：2026-10-17
- 方式：只读，未修改任何文件
- 目标拆分：payment（支付执行）、channel-connector（渠道连接）、card（卡域），各自独立数据库

## 结论摘要

- 没有跨核心的 SQL。
- 有两个事务同时写 payment 与 card 的表。
- Channel 模块没有任何写入。
- Execution 到 Channel 已经通过端口隔离。
- 风险最高的边界是渠道执行读卡的回调重入。

## 1. Maven 模块布局与现有业务模块到三核心的映射

父 POM 为 `P/pom.xml`，共 7 个模块。只有一个 Spring Boot 进程：`paytech-payment-web` 的 `PaymentApplication`，端口 18103，context path `/payment`。全部模块共用一个 DataSource（`mor_payment`）和一个 Redis。

| 模块 | 源文件数（main / test） | 对其他 payment 模块的生产依赖 | 目标核心 |
|---|---|---|---|
| paytech-payment-api | 19 / 0 | 无 | 拆分：execution 与 instrument API 归 payment；card API 归 card |
| paytech-payment-execution | 92 / 0 | **card**、**routing** | payment |
| paytech-payment-routing | 5 / 0 | 无 | payment（路由决策） |
| paytech-payment-channel | 45 / 0 | 无（依赖 platform-support-api、paytech-vault-client） | channel-connector |
| paytech-payment-card | 38 / 0 | 无（依赖 paytech-vault-client、paytech-redis-starter） | card |
| paytech-payment-web | 21 / 0 | api、execution、card、channel | 组合根，需要拆分 |
| paytech-payment-test | 0 / 27 | api、execution、web、channel | 需要拆分 |

Execution 对 Channel 没有 Maven 依赖。两者由 Web 中的 `P/paytech-payment-web/src/main/java/com/paytech/payment/integration/channel/PaymentChannelClientImpl.java` 桥接。

`P/paytech-payment-web/src/main/java/com/paytech/payment/configuration/PaymentConfiguration.java` 用 `@MapperScan` 把 execution、channel、card 三个 Mapper 包扫描到同一个 DataSource。

`P/paytech-payment-test/src/test/java/com/paytech/payment/auto/architecture/ChannelModuleBoundaryTest.java` 强制了三条规则：

- Channel 的公开类型使用白名单。
- 五张 Channel 表只能出现在 Channel 模块内。
- POM 依赖图不能有环。
- **Card 和 Execution 的表没有同等的门禁。**

依据的决定是 `agent-docs/decisions/2026-09-13-payment-module-scaffolding.md`。该决定在 2026-09-15 增补了 Execution→Routing。POM 中已有 Execution→Card，但盘点中没有找到批准该依赖的决定。

## 2. 表清单

来源是 `P/docs/database/sql/payment-orchestration-schema.sql`，共 31 张表。测试 DDL（`P/paytech-payment-test/src/test/resources/init/ddl.sql`）只包含有代码的 15 张表。

| 表 | 是否有代码 | 建议归属 | 理由 |
|---|---|---|---|
| t_payment_channel | 有 | channel-connector | 渠道身份 |
| t_channel_account | 有 | channel-connector | 技术账号；`entity_no` 来自 CCT；含 `account_vault_token` |
| t_channel_mid | 有 | channel-connector | MID 绑定渠道、账号和 OE |
| t_mid_capability | 有 | channel-connector | 能力作用域 |
| t_mid_capability_detail | 有 | channel-connector | 类型化能力值 |
| t_routing_policy | 仅 Schema | channel-connector 或待定 | 候选策略；09-13 模块方案把它划给 Routing |
| t_routing_policy_version | 仅 Schema | 同上 | 不可变候选集 |
| t_routing_policy_mid | 仅 Schema | 同上 | 持有 `mid_id` |
| t_routing_decision | 仅 Schema | payment | 以 `payment_attempt_id` 为键；`selected_channel_id`、`selected_mid_id`、`selected_channel_account_id` 指向 channel-connector |
| t_route_candidate_eval | 仅 Schema | payment | 冻结证据，含健康度列（`health_status`、`circuit_status`、`health_score`、`technical_success_rate`、`timeout_rate`、`p95_latency_ms`、`sample_count`、`health_observed_at`） |
| t_payment_intent | 有 | payment | |
| t_payment_attempt | 有 | payment | 冻结 `channel_id`、`channel_account_id`、`mid_id`（引用 channel-connector） |
| t_payment_instrument_token | 有 | payment | `instrument_ref` 是以字符串保存的卡工具 ID |
| t_payment_action | 有 | payment | |
| t_channel_transaction_ref | 有 | payment，但有争议 | 由 payment 写入，唯一键却是 `(channel_id, mid_id, reference_type, external_transaction_id)`；未来回调需要按它反查 |
| t_duplicate_payment_case | 仅 Schema | payment | |
| t_payment_refund_position | 仅 Schema | payment | |
| t_channel_refund | 仅 Schema | payment | 名称带 `channel_` 前缀，但它是退款动作的根 |
| t_payment_card | 有 | card | 含 `dek_vault_token`、`encrypted_pan` |
| t_payment_card_credential | 有 | card | |
| t_payment_card_instrument | 有 | card | 含 `client_id`、`customer_id` |
| t_payment_stored_card | 有 | card | |
| t_channel_interaction | 仅 Schema | channel-connector 或待定 | 以 `payment_action_id` 或 `channel_dispute_action_id` 为键 |
| t_channel_callback | 仅 Schema | channel-connector | 验签与去重 |
| t_channel_callback_item | 仅 Schema | channel-connector 或待定 | `target_type`、`target_id` 指向 payment 的记录 |
| t_channel_event_exception | 仅 Schema | channel-connector | |
| t_channel_dispute_notice | 仅 Schema | 待定 | `original_channel_txn_ref_id` 指向 payment 的表 |
| t_channel_dispute_event | 仅 Schema | 待定 | |
| t_channel_dispute_action | 仅 Schema | 待定 | 引用 Risk 的 case、request 和 package；冻结了 channel、MID 和 account |
| t_command_idempotency | 仅 Schema，无代码引用 | 删除或待定 | 旧模型；2026-09-17 决定禁止新使用 |
| t_compensation_event | 有 | 每个核心各一张 | 目前只使用 `PAYMENT_SUCCEEDED` |

**文档漂移：**

- `P/docs/database/PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md` 的 Manifest 仍记 27 张表。四张卡表是后来加入的。
- 模块方案 `requirements/20260913/payment-业务模块划分/technique.md` 列出的 `t_mid_health_state`、`t_payment_submission`、`t_card_access_event` 不在当前 Schema 中。
- **不存在健康度快照表**，也没有维护健康数据的代码。

## 3. 跨核心 SQL

没有。每个 Mapper XML 只访问本模块的表。仅有的联表都在 payment 内部：

- `P/paytech-payment-execution/src/main/resources/mapper/PaymentAttemptMapper.xml` 的 `insertAttempt` 是 INSERT…SELECT，从工具 Token 联表 Intent。
- `P/paytech-payment-execution/src/main/resources/mapper/PaymentInstrumentTokenMapper.xml` 的 `insertToken` 是 INSERT…SELECT，从 Intent 取数，并带对 Attempt 的 NOT EXISTS 检查。

跨核心关联只以 ID 列的形式存在，见第 2 节。

## 4. 写入多个核心的事务

### 4.1 申请卡工具（payment + card）

位置：`P/paytech-payment-execution/src/main/java/com/paytech/payment/execution/service/processor/ApplyCardInstrumentProcessor.java` 的 `saveContext`。

- 一个 TransactionTemplate 内同时执行：
  - `CardService.applyInstrument`：插入 `t_payment_card_instrument`。
  - `PaymentExecutionService.saveApplyCardInstrumentContext`：按条件插入 `t_payment_instrument_token`，条件依赖 Intent 状态。
- 事务之前，`PrepareCardInstrumentProcessor` 在事务外执行：
  - 以自动提交插入 `t_payment_card` 和 `t_payment_card_credential`，并处理 DuplicateKey。
  - 调用 Vault 保护 DEK。
  - 把 CVV 写入 Redis，键为 `card:cvv:<instrumentId>`。

### 4.2 支付成功（payment + card）

位置：`P/paytech-payment-execution/src/main/java/com/paytech/payment/execution/core/action/flow/ActionFlow.java` 的 `advanceAction`，结合 `P/paytech-payment-execution/src/main/java/com/paytech/payment/execution/core/action/PayActionActor.java` 的 `doInTransaction`。

一个事务内有六处写入：

- `t_payment_action` 保存结果。
- 插入 `t_channel_transaction_ref`。
- 更新 `t_payment_attempt`（`actionSuccess` 或 `payCompleted`）。
- 更新 `t_payment_intent`（`attemptSucceed`）。
- `CardService.paymentSucceeded`：更新凭证的 `verified_time`，插入或更新 `t_payment_stored_card`。
- 插入 `t_compensation_event`。

### 4.3 Channel 表没有写入

- `QueryMidCandidatesProcessor` 和 `AbstractChannelExecutionProcessor.readChecked` 只使用只读的 REPEATABLE_READ 事务。
- `PaymentExecutionService.savePayContext` 只写 Attempt 和 Intent。
- 没有持久化路由决策，也没有健康度更新。

## 5. 目标核心之间的 Java 调用

- **Execution → Card，进程内调用，中间没有端口：**
  - `CardService`（`applyInstrument`、`riskAttributes`、`paymentSucceeded`）。
  - `PrepareCardInstrumentProcessor`、`CheckCardInstrumentProcessor`、`ClaimChannelCardMaterialProcessor`。
  - 领域类型 `CardInstrumentContext`、`CardInput`、`ChannelCardMaterial`、`CardRiskAttributes`。
- **Execution → Routing：**
  - 使用 `RouteService` 以及 `RouteContext`、`RouteResult`、`RouteCandidate`。
  - `P/paytech-payment-routing/src/main/java/com/paytech/payment/routing/service/impl/RouteServiceImpl.java` 是纯函数：选第一个支持所需动作且金额在上下限内的候选，没有健康度逻辑。
- **Execution → Channel，已有端口：**
  - 端口是 `execution/integration` 中的 `PaymentChannelClient` 接口。
  - Web 的 `PaymentChannelClientImpl` 实现它，调用 `QueryMidCandidatesProcessor` 和 `ExecuteChannelOperationProcessor`，并按代码映射枚举。
- **Card → payment，方向反转：**
  - `ChannelCardMaterialController` 提供 card 路径 `/payment-card/v1/channel-card-materials/reveal`。
  - 它调用 `P/paytech-payment-execution/src/main/java/com/paytech/payment/execution/service/processor/RevealChannelCardMaterialProcessor.java`。
  - 该 Processor 读取 Token、Attempt、Action，要求当前动作是处于 PROCESSING 的 PAY 或 AUTHORIZE。
  - 之后再调用 Card 的 `ClaimChannelCardMaterialProcessor`。
- **Web → Card，直接调用：** `StoredCardController` 直接调用 `StoredCardService`。
- **Web → Execution 静态方法：** `PaymentResultRabbitPublisher` 使用 `PayActionActor.amountText`。
- **其他已有端口：**
  - Execution 有 `PaymentRiskClient` 和 `PaymentResultPublisher`，都在 Web 中实现。
  - Card 有 `CardKeyClient` 和 `CardSecurityCodeClient` 接口。
  - Channel 的 `PlatformStandardApiClient` 和 `ChannelAccountConfigReader` 是具体类，没有接口。

## 6. 共享的 DataObject 与枚举

没有 DataObject 跨模块传递。共享知识以字符串代码传递：

- **动作与操作：** Execution 的 `ActionType`（PAY_RISK、AUTHENTICATE、AUTHORIZE、PAY、CAPTURE、VOID、REFUND）对应 Channel 的 `ChannelOperation`（PAY、AUTHORIZE、AUTHENTICATE、CAPTURE、VOID、REFUND、QUERY）。Web 按代码映射。
- **结果枚举：** Execution 的 `ChannelExecutionOutcome` 对应 Channel 的 `ChannelOperationOutcome`（SUCCESS、FAIL、ERROR、NOT_SENT）。
- **能力代码：**
  - ACTION.PAY、ACTION.AUTHORIZE、AMOUNT.MIN、AMOUNT.MAX、SECURITY.3DS_MODE 等代码配置在 `P/paytech-payment-web/src/main/resources/application.yml` 的 `payment.channel.capability-definitions`。
  - `AbstractChannelExecutionProcessor` 和 Web 的 `PaymentChannelClientImpl.candidate` **两处**都在解析它们。
- **捕获模式：** `RouteServiceImpl` 比较字符串字面量 "MANUAL"，而 `CaptureMode` 枚举属于 Execution。
- **工具关联：** `InstrumentType.CARD` 加上 `instrument_ref`（卡工具 ID）是 payment 与 card 之间唯一的关联。
- **其他 Execution 枚举：** `PaymentErrorCode` 属于 Execution。
- **共享基础设施 Bean：**
  - Snowflake `IdGenerator`。
  - `Clock`。
  - 单一 DataSource 与 TransactionTemplate。
  - `P/paytech-payment-execution/src/main/java/com/paytech/payment/execution/core/constant/Locks.java` 中的 Redis 资源锁键 `intent:<id>` 和 `intent-source:<type>:<id>`。

## 7. 外部合同

### 7.1 入站 HTTP

context path 为 `/payment`，端口 18103。

| API | 接口 | 调用方 | 归属核心 |
|---|---|---|---|
| `PaymentIntentApi` `/payment-execution/v1` | POST `/intents`；GET `/intents/{intentId}`；GET `/intents/{intentId}/submissions/{sourceSubmissionId}`（EX-I06）；POST `/intents/{intentId}/terminate`；POST `/intents/{intentId}/pay` | Commerce | payment |
| `PaymentInstrumentApi` `/payment-instrument/v1` | POST `/card-tokens`（ApplyCardInstrument，接收 PAN 与 CVV 原值） | 按注释推断为 Commerce | payment + card |
| `StoredCardApi` `/payment-card/v1` | GET `/stored-cards`；DELETE `/stored-cards/{storedCardToken}`；POST `/customers/{customerId}/stored-cards/invalidation` | Commerce | card |
| `ChannelCardMaterialApi` `/payment-card/v1` | POST `/channel-card-materials/reveal`，Header `X-Caller-Service` 只接受 platform-support-service | Platform | card，但准入校验在 payment |

API 接口定义位于 `P/paytech-payment-api/src/main/java/com/paytech/payment/api/` 下。

`P/paytech-payment-web/src/main/java/com/paytech/payment/ext/rest/converter/PayConverter.java` 要求 Idempotency-Key 等于 `submission:<sourceSubmissionId>`。真正的防重是 `t_payment_attempt.source_submission_id` 唯一键。

没有入站回调接口。

### 7.2 出站 HTTP

所有客户端在 `P/paytech-payment-web/src/main/java/com/paytech/payment/configuration/PaymentOutboundConfiguration.java` 装配，经服务发现调用，不重试。

- **Platform：** platform-support-api 的 `StandardApiCommandApi`，服务名 platform-support-service，由 `PlatformStandardApiClient` 使用。归属 channel-connector。
- **Risk：** risk-api 的 `RiskDecisionApi`，服务名 risk-service。`PayRiskCheckActionActor` 经 `PaymentRiskClientImpl` 调用。归属 payment。
- **Vault（卡）：** `CardKeyClientImpl` 对数据类型 PAYMENT_CARD_DEK 做 protect（用途 CARD_STORAGE）和 reveal（用途 CARD_USE）。归属 card。
- **Vault（渠道）：** `ChannelAccountConfigReader` 以用途 CHANNEL_EXECUTION 做 reveal。归属 channel-connector。它只被 `GetChannelExecutionConfigProcessor` 使用，而该 Processor **没有生产调用方**，只有测试在用。
- **Commerce：** 没有出站客户端。

### 7.3 Redis

- 卡 CVV 暂存：`card:cvv:<instrumentId>`，归属 card。
- 资源锁：`intent:<id>`、`intent-source:<type>:<id>`，归属 payment。

### 7.4 MQ、Job 与补偿

- 唯一的生产者是 `P/paytech-payment-web/src/main/java/com/paytech/payment/integration/mq/PaymentResultRabbitPublisher.java`：
  - Exchange `topic_payment_result`，Routing Key `payment.succeeded.v1`，事件类型 PaymentSucceeded。
  - 事件键 `PAYMENT:PAYMENT_ATTEMPT:<id>:<version>:PaymentSucceeded`。
  - Payload 包含 `channelId`、`channelAccountId`、`midId`、`channelTransactionRef`。
  - 归属 payment。
- **没有消费者**，服务代码中没有 `@XxlJob`，也没有 `@Scheduled`。XXL-Job 兜底来自补偿组件的默认配置。
- 唯一的补偿 Handler 是 `P/paytech-payment-execution/src/main/java/com/paytech/payment/execution/service/handler/PaymentSucceededHandler.java`（`PAYMENT_SUCCEEDED`）。它只从 payment 的表重建事件。

## 8. 测试

共 12 个 auto 测试类，grep 统计约 84 个 `@Test` 和 12 个 `@ParameterizedTest`。

- **基类层次：** Foundation `TestBase` → `MockTestBase`（SpringBootTest 启动 `PaymentApplication`，随机端口，profile `mock`）→ `AbstractPayCardTest`。
- **测试基础设施：**
  - 一个数据库 `mor_payment_channel_test`。
  - 真实的 Redis 和 RabbitMQ，由 `PaymentResultProbe` 观察消息。
  - 一个 `MockHttpServer` 经 simple discovery 同时承载 Vault、Platform 和 Risk。
- **清理与检查：**
  - 全局 BeforeEach 断言数据库和 Vault 为空。
  - AfterEach 等待补偿任务结束，并检查意外调用。
  - `VaultTransactionObserver.assertOutsideTransactions` 断言 Vault 调用不发生在事务内。
  - `Clear` 实现（`ChannelDataMocker`、`VaultMocker`、`PlatformMocker`、`RiskMocker`、`VaultTransactionObserver`）加上 `ChannelTestMapper` 的 DELETE 语句清理**全部 15 张表**。`ChannelTestMapper` 虽以 Channel 命名，实际清理所有表。
- **跨核心测试：** `PayCardChannelTest`、`PayRiskEvaluationTest`、`PaymentSucceededPublishTest`、`GetSubmissionTest`、`CardStorageTest`、`StoredCardTest`。
  - 它们经真实 HTTP 入口依次新建 Intent、申请卡工具、提交 pay。
  - `PlatformMocker` 在 pay 期间回调 Payment 的读卡接口。
- **单核心测试：**
  - Card：`CardCipherTest`、`PaymentCardPropertiesTest`。
  - Channel：`QueryMidCandidatesTest`、`GetChannelExecutionConfigTest`、`MidCapabilityResolutionTest`。
  - 架构：`ChannelModuleBoundaryTest`。

测试代码位于 `P/paytech-payment-test/src/test/java/com/paytech/payment/`。

## 9. 跨边界逻辑热点（按风险从高到低）

1. **渠道执行读卡的回调重入。**
   - pay 持有 Redis Intent 锁，调用 Platform，Platform 再回调 `/payment-card/v1/channel-card-materials/reveal`。
   - 准入校验基于 payment 的表：Token、Attempt，以及当前动作为 PROCESSING 的 PAY 或 AUTHORIZE。
   - 随后 Card 从 Vault 取回 DEK，并对 Redis 中的 CVV 执行 `getAndDelete`，所以 CVV 只能领取一次。
   - 拆分后，Card 需要同步回调 payment 校验，或者由 payment 签发有范围的授权。读卡失败或重试后 CVV 无法恢复。
2. **支付成功事务包含卡表写入。**
   - 涉及 `PayActionActor.doInTransaction` 调用 `CardService.paymentSucceeded`。
   - 拆分后需要通过补偿事件或 MQ 通知 Card，已存卡创建和凭证验证会变为最终一致。
3. **申请卡工具的原子性。**
   - 卡工具和 Token 一起提交，且 Token 的插入依赖 Intent 状态。
   - 分库后可能出现孤立的卡工具或孤立的 Token。
   - 卡和凭证行在此之前已提交，孤立的 Vault DEK 已是现有设计接受的残留。
4. **路由冻结与渠道重新校验。**
   - 候选在事务前读取，选第一个合格候选，坐标冻结到 Attempt。
   - `ExecuteChannelOperationProcessor` 随后重新校验归属、ACTIVE 状态、MID 当前绑定账号、能力和金额上下限，任何不一致都返回 NOT_SENT。
   - 能力代码在两处解析。
   - 没有持久化路由决策或候选证据，健康度逻辑不存在。
5. **渠道结果映射位于 payment。**
   - `PayActionActor` 和 `PayOutputs` 解释 Platform 输出键：`paymentStatus`（SUCCEEDED、PENDING、FAILED）、`redirectUrl`、`channelTransactionRef`、`failureCode`。
   - `PlatformStandardApiClient` 决定哪些 Problem 代码代表 NOT_SENT。
   - 目标设计把报文映射划给 channel-connector，这部分逻辑需要迁移。
6. **`t_channel_transaction_ref` 位于边界上。** 它由 payment 写入，却以 channel-connector 的 ID 为键。未来 channel-connector 中的回调条目和争议通知都要按它反查 payment 的动作。
7. **风控输入需要卡数据。** `PayRiskCheckActionActor` 经 `CardService.riskAttributes` 在进程内读取 BIN 和卡指纹。拆分后会变成风控评估前的一次同步 card 调用。
8. **PaymentSucceeded 的 Payload 暴露连接器 ID。** `channelId`、`channelAccountId`、`midId` 将归另一个核心所有，消费方合同依赖这些 ID。
9. **共享的组合根。** 拆分时需要分开以下内容：
   - 单一 DataSource 与事务管理器。
   - 每个进程的 Snowflake worker ID 分配。
   - 共用的补偿表。
   - `@MapperScan`。
   - Vault 调用方身份：card 和 channel 都使用 caller-service `payment-orchestration-service`。
   - 唯一的测试数据库。
10. **OE 一致性校验。** Channel 校验账号和 MID 的 `entity_no` 等于 Intent 的 `entity_no`。payment 必须在每条命令中继续携带 OE，Channel 绝不重新选择三元组。

## 不确定项

- `AuthorizeActionActor` 和 `AuthenticateActionActor` 尚未实现，执行时抛出异常，结果记为 UNKNOWN。
- 恢复和回调均未实现。
- `P/docs/development/processor-conventions.md` 引用了不存在的 `PaymentExecutionApi.java`，当前类名是 `PaymentIntentApi`。
- `PaymentInstrumentApi` 的调用方只根据代码注释推断。
