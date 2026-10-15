# 支付结果查询与回传技术方案

> 状态：阶段 2+3 已确认（2026-10-15，第 1 版）。依据 [需求](./requirement.md)；确认记录见 [delivery.md](./delivery.md)。

**规模：** Payment 新增 1 个查询接口、1 个 MQ 事件发布（补偿事件 + RabbitMQ），测试库补建 `t_compensation_event`；Commerce 查询改接真实接口、新增 1 个 MQ 消费者与 5 条条件更新 SQL，不改表；Platform、Risk、CCT 不改。

## 1. 现状差异

| 需求项 | 现状 | 目标 | 缺口 |
|---|---|---|---|
| AC-01 EX-I06 | 无接口；`t_payment_attempt` 有 `source_submission_id` 唯一键，无失败码列；失败码与 3DS 交互在 `t_payment_action` | 按 Intent + 来源提交返回 SubmissionView | 新建 Processor 与 API |
| AC-02 Commerce 查询 | `CheckoutCardPaymentPortImpl.query` 固定返回处理中（`TODO by CLAUDE`） | 调 EX-I06，404 → `PAYMENT_SUBMISSION_NOT_FOUND` | 改实现 |
| AC-03 事件发布 | Payment 无补偿事件代码、无 RabbitMQ；Schema 有 `t_compensation_event`，测试库没有 | 成功事务登记，提交后发布并等 Confirm | 接入组件、建表 |
| AC-04/05/06 Commerce 消费 | 无消费者；Enforcer 禁止 AMQP；投影只有插入；无置 PAID 的 SQL | 消费并在一个事务推进五项事实，幂等 | 新建消费者与 SQL |

## 2. Payment

### 2.1 EX-I06 按来源提交查询

`GET /payment-execution/v1/intents/{intentId}/submissions/{sourceSubmissionId}?clientId=`（`PaymentIntentApi` 新增方法），`GetSubmissionProcessor`：

1. 读 Intent，校验 Client；不存在或跨 Client → 404。
2. 按 `source_submission_id` 读 Attempt；不存在或不属于该 Intent → 404（只有工具没有 Attempt 也是 404）。
3. 读 Attempt 的最后一个 Action（新增 `PaymentActionRepository.findLatestByAttempt`）取失败码与交互。
4. 响应：`sourceSubmissionId`、`intentId`、`attemptId`、`paymentMethodCode`、`status`（Attempt 状态）、`capturedAmount`、`interaction{type, expiresAt}`（仅处理中且有未过期交互时）、`failureCode`（失败时取最后动作的渠道码）、`observedAt`。**不返回 3DS 跳转地址**（Q-01）。

### 2.2 PaymentSucceeded 发布

- **登记：** `PayActionActor.doInTransaction` 在 `attemptSucceed` 之后、同一成功事务内 `CompensationEventRegistrar.register(PaymentSucceededHandler.buildEvent(attemptId))`；`event_type=PAYMENT_SUCCEEDED`，`event_key=attemptId`，无 Payload。
- **Handler（execution 模块）：** 不在事务中执行；按 `attemptId` 回查 Attempt、Intent、成功动作与渠道交易引用重建事件；不合法 → `terminalFailure`；发布异常 → `retryableFailure`。策略参照 Risk（超时 20 秒、最多 12 次、退避 5 秒至 30 分钟）。
- **发布端口：** execution 定义 `PaymentResultPublisher` 接口，web 实现 `PaymentResultRabbitPublisher`：
  - Exchange `topic_payment_result`（Durable Topic，发布方声明）；Routing Key `payment.succeeded.v1`；Event Type `PaymentSucceeded`。
  - `eventKey = PAYMENT:PAYMENT_ATTEMPT:{attemptId}:{attemptVersion}:PaymentSucceeded`；`messageId = eventKey`；Persistent、UTF-8 JSON。
  - Publisher Confirm（correlated）+ Mandatory；10 秒内未 ack 或被 Return 视为失败。
- **信封：** `eventKey, eventType, sourceSystem="payment", sourceType="PAYMENT_ATTEMPT", sourceId=attemptId, sourceVersion, occurredAt, schemaVersion=1, payloadHash, payload`；`payloadHash = "sha256:" + hex(SHA-256(payload JSON 字节))`（沿用 Risk 写法，字段顺序固定）。
- **Payload（Q-03 最小集合，ID 与金额均为字符串）：** `clientId, orderId（Intent 的 sourceId）, sourceType, paymentIntentId, paymentAttemptId, sourceSubmissionId, entityNo, amount, currency, capturedAmount, paymentSucceededTime, paymentMethodCode, channelId, channelAccountId, midId, channelTransactionRef`。不含卡数据、`consumerReference`、IP、User-Agent。
- **依赖与配置：** execution 加 `paytech-compensation-event`；web 加 `spring-boot-starter-amqp`；`spring.rabbitmq.*`（`PAYMENT_RABBITMQ_*`，vhost 默认 `/mor-local`，回退值为空）、`publisher-confirm-type: correlated`、`publisher-returns: true`、`template.mandatory: true`、`compensation.enabled: true`；XXL-Job 兜底唤醒沿用组件默认，测试关闭。

### 2.3 数据库

- Payment 业务表不改；测试 `init/ddl.sql` 补入 Schema 中已有的 `t_compensation_event`。
- 本机测试库 `mor_payment_channel_test` 执行建表（需授权）；`db/payment/ddl.sql` 为该表的增量执行文件。

## 3. Commerce

### 3.1 查询切换（F-01）

`CheckoutCardPaymentPortImpl.query` 调 EX-I06：
- 200 → 映射为 `PaymentResult`：`SUCCEEDED/FAILED/CANCELLED` 直接映射；`PROCESSING` 有交互 → `REQUIRES_ACTION`（动作 ID 用尝试 ID、无跳转地址），否则 `PROCESSING`；失败码透传。
- 404 → 返回 null，由 `GetCustomerPaymentProcessor` 按既有约定返回 `PAYMENT_SUBMISSION_NOT_FOUND`（若其校验要求 3DS 有跳转地址，调整为允许查询结果无地址）。
- 不可用 → 保持既有降级。

### 3.2 PaymentSucceeded 消费（F-02）

- **依赖：** 解除 `pom.xml` Enforcer 对 `spring-boot-starter-amqp` 的禁止（Q-05，仍禁止其他 AMQP 直连依赖），web 加该依赖。
- **拓扑（消费方声明）：** 再次声明 `topic_payment_result`；主队列 `cg_commerce_payment_result`（Quorum、`deliveryLimit=5`、DLX `dlx_commerce_payment_result` → `cg_commerce_payment_result_dlq`）绑定 `payment.succeeded.v1`；监听工厂 Manual Ack、`defaultRequeueRejected=false`、Prefetch 10。
- **Listener → Converter → Processor：**
  - Converter 校验 eventType、sourceSystem、schemaVersion、payloadHash、`sourceId == payload.paymentAttemptId`、ID 与金额格式；不合法抛 IllegalArgumentException → reject 进 DLQ。
  - `ApplyPaymentSucceededProcessor`：按 `orderId` 读订单与投影，核对 Client、Intent、金额币种；
    - 投影已记录同一 `eventKey` 且 Hash 相同 → 重复，直接 Ack；同 eventKey Hash 不同 → 进 DLQ。
    - 订单已 PAID 且成功尝试相同 → 重复；成功尝试不同 → 进 DLQ（重复成功需人工）。
    - 订单已取消或过期（Q-04）→ 只更新支付投影（记录资金已收），订单与会话不改，记 WARN 日志（不含受限字段）后 Ack；不进 DLQ 以免被误当成坏消息，人工处理依据为投影与日志。
    - 正常：`OrderPaymentService.applySucceeded`（REQUIRED 事务，条件 SQL + 版本）一次完成：投影（意图 SUCCEEDED、最后尝试 SUCCEEDED、成功尝试、已捕获金额、事件键与 Hash、来源版本）、订单 `OPEN→PAID` 与 `paid_at`、会话 `→COMPLETED` 与 `completed_at`、退款仓位 `refundable_base_amount=已捕获金额`、资金可用 `NOT_APPLICABLE→PENDING`。
  - 并发冲突（版本条件失败）→ Nack 重新入队；未知订单或投影不存在 → 进 DLQ。
- **Ack：** Processor 返回（事务已提交）后 `basicAck`。

### 3.3 不做

- 订单 `paid` Webhook 快照与推送、Finance 消费、失败类事件（Q-02）。

## 4. 测试计划

| 系统 | 入口 | 覆盖 |
|---|---|---|
| Payment | HTTP EX-I06 | 成功、失败（失败码）、处理中、3DS 待操作（无地址）、只有工具 404、跨 Client 404、跨 Intent 404 |
| Payment | HTTP pay → 本机 RabbitMQ 临时队列 | 成功后发布一次、信封与 Payload 正确、Hash 可重算；失败不发布；成功事务回滚不留补偿行；补偿行发布后删除 |
| Commerce | HTTP 结账查询（Payment HTTP Mock） | 状态映射、404 → `PAYMENT_SUBMISSION_NOT_FOUND`、不可用降级 |
| Commerce | 测试扮演 Payment 发到真实交换机 | 正常推进五项事实；重复投递一次生效；Hash 被篡改、未知订单、金额不符进 DLQ；已取消订单只更新投影 |

测试队列：Payment 发布端用每例唯一的临时排他队列（参照 Risk 探针）；Commerce 消费端每例前清空生产队列与 DLQ（参照 CCT）。

## 5. 阶段 4 拆分与授权

1. **P：** Payment EX-I06 + 事件发布；测试库建 `t_compensation_event`（需授权）。
2. **C：** Commerce 查询切换 + 消费者；Commerce 本机库无表结构变更。
3. 本地依赖：Payment `paytech-payment-api` 重新 `install` 供 Commerce 编译。

## 6. 实现偏离（阶段 4 记录）

- Payment `PaymentSucceededHandler` 回查权威表，不满足“尝试成功、Intent 成功尝试指向它、最后动作为成功 PAY、恰好一条渠道交易引用”任一条件即终态失败、不发布；Handler 直接读 Repository（同 Risk 先例）。
- Payment Publisher 实现放在 web `integration/mq`，execution 只持有端口。
- Commerce 查询的渠道失败码不符合公开格式 `[A-Z][A-Z0-9_]{0,63}` 时统一返回 `CHANNEL_DECLINED`。
- Commerce 投影不更新 `intent_source_version`（事件不含 Intent 版本）；只接受全额捕获。
- 证据与测试映射见 [delivery.md](./delivery.md)。
