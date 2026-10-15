# 支付结果查询与回传需求

> 状态：阶段 1 已确认（2026-10-15，问题按建议）；技术方案见 [technique.md](./technique.md)。确认记录见 [delivery.md](./delivery.md)。

## 1. 背景与来源

来源：[Customer 与存卡支付](../commerce-Customer与存卡支付/delivery.md) 登记的后续需求 F-01、F-02（用户 2026-10-15 同意先做）。

Commerce 结账已经通过 Payment pay 提交卡支付，但：

1. **结账查不到真实结果（F-01）：** Commerce `GET /customer-gateway-api/v1/checkouts/{token}/payment-submissions/{submissionId}` 经 `CheckoutCardPaymentPort.query` 固定返回“处理中”（`CheckoutCardPaymentPortImpl.query`，`TODO by CLAUDE`），因为 Payment 没有按来源提交查询的接口。
2. **订单不会变成已支付（F-02）：** Payment 没有任何结果事件或 MQ 发布；Commerce 没有消费者，也没有代码把订单置为 PAID（只读 `PAID`/`paid_at`）。

### 现有合同（未实现）

| 合同 | 出处 | 要点 |
|---|---|---|
| EX-I06 按来源提交查询 | [Execution api-contract](../../20260913/payment-Execution设计/api-contract.md) §44、§87 | `GET /payment-execution/v1/intents/{intentId}/submissions/{sourceSubmissionId}`，返回 SubmissionView（状态、尝试、金额、可选动作与失败码等）；只有工具没有 Attempt 时 NOT_FOUND |
| PaymentSucceeded | `requirements-site/content/requirements.ts`（PAY-I01、PAY-06）；`global-use-case-sequences.mjs` | SALE 成功或全额 Capture 时发布；订阅方 Commerce、Finance；AUTHORIZE 成功不发布 |
| Commerce 消费 | `global-use-case-sequences.mjs` | 同一本地事务幂等更新 Payment Projection、Order→PAID、Session→COMPLETED、建立可退基准、Funds Availability→PENDING |
| MQ 规范 | [TECHNOLOGY_STACK.md](../../../agent-docs/TECHNOLOGY_STACK.md) §343–375、[补偿事件指南](../../../agent-docs/COMPENSATION_EVENT_GUIDE.md) | `topic_{source}_{subject}` 交换机、`cg_{consumer}_{subject}` 队列、`payment.succeeded.v1` 路由键；Publisher Confirm、手动 Ack、Quorum、DLQ；信封 `eventKey/eventType/source*/occurredAt/schemaVersion/payloadHash/payload`；本地 `compensation_event` 与业务同事务登记、提交后发布 |

### 现状约束

- Payment 未接入 `paytech-compensation-event` 与 RabbitMQ（Schema 有 `t_compensation_event` 表，无代码）。
- Commerce `pom.xml` 用 Enforcer 禁止 AMQP 依赖；已有 `paytech-compensation-event` 与 `t_compensation_event`。
- Commerce `t_order_payment_projection` 已按事件消费设计（事件键、Payload Hash、尝试状态、成功尝试、已捕获金额、版本），目前只有插入。
- 3DS 跳转地址不落库（Payment 只在 pay 响应中返回）。
- 仓库唯一的 MQ 先例：Risk 发布 `topic_risk_client_review`、CCT 消费。

## 2. 目标与范围（草案）

### 2.1 F-01 按来源提交查询

1. Payment 实现 EX-I06：按 Intent 与来源提交返回原 Attempt 的状态、尝试 ID、金额、失败码；处理中且有 3DS 交互时返回交互类型与期限（不含跳转地址，见 Q-01）。
2. Commerce `CheckoutCardPaymentPort.query` 改为调用 EX-I06；Payment 返回不存在时 Commerce 返回 `PAYMENT_SUBMISSION_NOT_FOUND`，不再伪装为处理中。

### 2.2 F-02 支付成功回传

1. Payment 在支付成功事务内登记补偿事件，提交后发布 `PaymentSucceeded`（字段按 PAY-I01 的最小集合，见 Q-03）。
2. Commerce 消费 `PaymentSucceeded`：同一本地事务幂等更新支付投影、Order→PAID（`paid_at`）、Session→COMPLETED、建立可退基准、Funds Availability→PENDING。
3. 重复投递、乱序、未知订单的处理：按事件键与 Payload Hash 幂等；与本地事实冲突时拒绝并进入死信，不覆盖。

## 3. 明确不做（草案）

| 项 | 说明 |
|---|---|
| Finance 消费 `PaymentSucceeded` | Finance 侧另行 |
| `PaymentAttemptFailed/Unknown`、`PaymentIntentTerminated` 等其他事件 | 见 Q-02 |
| 渠道异步通知、主动查询恢复 UNKNOWN | Payment 恢复能力另行 |
| `order.paid` Webhook 推送给 Client | Platform Webhook 另行 |
| 手动捕获（AUTHORIZE/Capture） | Payment 尚未实现 |
| mor-test 部署 | 待定 |

## 4. 验收标准（草案）

| 编号 | 验收标准 |
|---|---|
| AC-01 | Payment EX-I06：成功、失败、处理中、3DS 待操作四种 Attempt 均返回正确状态；只有工具没有 Attempt、或提交不属于该 Intent/Client 时返回 404 |
| AC-02 | Commerce 结账查询返回 Payment 的真实状态；Payment 404 时返回 `PAYMENT_SUBMISSION_NOT_FOUND`；Payment 不可用时保持既有降级行为 |
| AC-03 | 卡支付成功后，Payment 在同一事务登记发布任务，提交后经 RabbitMQ 发布 `PaymentSucceeded`（Publisher Confirm 成功才完成任务）；支付失败不发布 |
| AC-04 | Commerce 消费后订单为 PAID、`paid_at` 为支付成功时间、Session 为 COMPLETED、可退基准等于已捕获金额、资金可用为 PENDING，且在同一本地事务 |
| AC-05 | 同一事件重复投递只生效一次；同一事件键但 Payload 不同、订单不存在或金额币种不符时不改订单并进入死信 |
| AC-06 | 订单已取消或已过期后收到成功事件：按 Q-04 处理，不静默丢弃 |
| AC-07 | 事件与日志不含卡数据、`consumerReference`、IP、User-Agent |
| AC-08 | 两边 auto 测试使用本机 RabbitMQ（`/mor-local`），测试队列与清理隔离 |

## 5. 问题与决定（2026-10-15 按建议确认）

| 编号 | 问题 | 建议 |
|---|---|---|
| Q-01 | 3DS 跳转地址不落库，EX-I06 是否需要返回它（原合同写“可即时取得”） | 不返回地址，只返回交互类型与期限；地址只在 pay 响应中给一次，页面需自行保存。若要支持刷新后继续 3DS，需要另行决定是否加密暂存地址 |
| Q-02 | 是否同时发布失败类事件（`PaymentAttemptFailed` 等）让 Commerce 投影反映失败 | 本期只发 `PaymentSucceeded`；失败状态由 F-01 查询得到 |
| Q-03 | 事件载荷用 PAY-I01 全字段还是最小集合 | 本期最小集合：订单/Intent/Attempt、Client、金额币种、成功时间、OE、渠道三坐标、渠道交易引用；其余字段随 Finance 消费时补充（Schema 版本递增） |
| Q-04 | 订单已取消或过期后收到支付成功（资金已收） | Commerce 不改订单终态，记录异常并进入人工处理（死信 + 日志告警）；退款或冲正另行 |
| Q-05 | Commerce 解除 AMQP 依赖禁令 | 解除（仅 `spring-boot-starter-amqp`），并按 Risk→CCT 先例实现消费者 |
| Q-06 | 本需求由谁实现 | 本会话同时改 Payment 与 Commerce（沿用上一需求的做法），需你确认授权 |
| Q-07 | 阶段安排 | 1 → 2+3（合并）→ 4（Payment 与 Commerce 分段）→ 5；6–7 不适用；提交另行确认 |
