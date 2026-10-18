# 任务卡 08 · 消息拓扑接入：分区队列、发布侧串行、消费者切换新信封

- 分支：`refactor/step-08-messaging-topology`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-step08`；起点：`refactor-main@84fe5d7e`（00–07、09、10 已合入）。
- 对应计划：[11-dev-plan.md](../design/11-dev-plan.md) 第 08 步；设计 [08-messaging-module.md](../design/08-messaging-module.md)（已按第 01 步实现修订：Fanout + 订阅方哈希交换机、缺口尾部重投）；决定 D24、D34。
- 串行执行（涉及 payment、compliance 两个发布方与 acquiring、client-master、card 预留三个消费方）。

## 1. 输入文档（先完整读取）

- [design/08-messaging-module.md](../design/08-messaging-module.md) 全文；`paytech/foundation/paytech-messaging/README.md`、`catalog/events.yaml`、`AggregateSerialExecutor`、`PartitionedListenerContainerFactory`、`IdempotentConsumeInterceptor`。
- `agent-docs/COMPENSATION_EVENT_GUIDE.md`（第 4 节：补偿库不内置 RabbitMQ；本步只在 Dispatcher 接入一个"按聚合键串行"的执行器端口，不引入 AMQP）。
- 第 01、05、06、03、04 步报告（发布方双写现状、消费方位置、`event_key` 格式）。
- 现有代码：`paytech/foundation/paytech-compensation-event` 的 `CompensationEventDispatcher`（并发执行与 `MOD(id)` 分片）、payment 的 `PaymentSucceededHandler` 与双写发布器、compliance 的 `ClientReviewResultRabbitPublisher`、acquiring 的 `PaymentSucceededListener`（旧 Topic `topic_payment_result` / 队列 `cg_commerce_payment_result`）、client-master 的 `ClientReviewResultListener`（旧队列 `cg_cct_client_review`）。
- 仓库规范：`AGENTS.md`、`agent-docs/UNIT_TEST_GUIDE.md`。

## 2. 改动清单

1. **发布侧串行（08 §3）**：`paytech-compensation-event` 新增可选端口 `CompensationExecutionSequencer`（接口在 compensation-event，默认实现 = 现状并发；**不依赖 messaging**）；`paytech-messaging` 提供实现 `AggregateKeySequencer`（用 `AggregateSerialExecutor` 按 `event_key` 的聚合前缀哈希到固定线程）；payment-app 与 compliance-app 装配该实现。Dispatcher 的扫描分片不变。补偿库的 Guide 不改（只是新增一个端口，无 Broker 依赖）。
2. **消费方切换到新信封**：
   - acquiring：`PaymentSucceededListener` 改为 `@DomainEventHandler(eventType="PaymentSucceeded", strictOrder=false)` 的 `DomainEventConsumer`，经 `PartitionedListenerContainerFactory` 消费 `acquiring.payment.p0..p3`（订阅方哈希交换机 `acquiring.payment.hash` 绑到 `payment.events`）；业务幂等（按 `paymentAttemptId` / 订单投影版本）保持现状；`t_consumed_event`、`t_consumed_aggregate_version` 两表加入 acquiring 的 Schema SQL、PDManer、测试 DDL（DDL 逐字取 `paytech-messaging/src/main/resources/schema/messaging-consumed-event.sql`）。
   - client-master：`ClientReviewResultListener` 同样切到 `ClientReviewDecided` 新信封（`client-master.compliance.p0..p3`）。
   - 切换完成后**删除旧 Topic 双写**：payment 不再发 `topic_payment_result`，compliance 不再发旧 Topic；旧队列 `cg_commerce_payment_result`、`cg_cct_client_review` 及其 DLX 的声明从代码删除（Broker 中既有队列的删除属部署动作，报告列出 `rabbitmqadmin delete queue` 命令，不执行）。
   - card 对 `PaymentSucceeded` 的消费留第 12 步；`EconomicFactRecorded`、`SettlementRestrictionChanged`、`BalanceMoved` 的消费留 11b / 后续切片，本步只核对目录登记。
3. **compose**：`local-environment` 与 `test-environment` 的 RabbitMQ 插件挂载已在第 01 步完成；本步核对 `test-environment` 的 compose 对应用服务的 `messaging.*` 环境变量（`messaging.producer.service`、`messaging.consumer.service`、分区数）写入 `compose.applications.yaml` 的环境段（只加变量，不改服务拆分）。
4. **退回策略收口**：第 05 步为避免无订阅者时 mandatory 退回导致补偿任务 FAILED，在 `paytech-messaging` 加了 `messaging.publisher.unroutable-policy = FAIL | ACK_AND_WARN`，payment-app 临时配置 `ACK_AND_WARN`。本步接入订阅者后，把 payment-app（以及 compliance-app 若同样设置）改回 `FAIL`，并新增一例测试证明订阅者存在时发布成功、不存在时按 FAIL 抛错由补偿重试。
5. **事件目录**：`catalog/events.yaml` 中 `PaymentSucceeded`（consumers acquiring、card）、`ClientReviewDecided`（consumers client-master）的 `aggregateType / aggregateVersionField / strictOrder` 以第 05、06 步实现为准核对；R12 对两个消费方的 Handler 登记校验必须通过。

## 3. 测试要求

- payment：`PaymentSucceededPublishTest` 去掉"旧 Topic 仍收到"断言，保留"新信封已发布"；新增"同 Attempt 两条事件按版本顺序发布（串行器生效）"用例（Probe 观察 `payment.events` 的到达顺序）。
- acquiring：`PaymentResult*` 测试改为向 `payment.events`（Fanout）发布新信封触发消费；新增"重放跳过"、"旧版本跳过"两例；`t_consumed_event` 行数断言；清理器加两表。
- client-master：`ClientReviewResultListenerTest` 同样改造；新增重放跳过一例。
- compliance：发布测试改为只断言新信封。
- 全 Reactor `verify` 全绿；本地 Broker 上测试拓扑在测试后清理（现有模式）。
- 禁止 `when(...).thenReturn`、`@MockBean`。

## 4. 验证清单

1. 全 Reactor `cd paytech && ./mvnw -B verify` 全绿，各消费方测试数 ≥ 上一步报告。
2. 本地 Broker 管理 API 核对：测试后无 `mt*` 残留；`payment.events`、`compliance.events` 为 fanout，`acquiring.payment.hash`、`client-master.compliance.hash` 为 `x-consistent-hash`，各 4 个 Quorum 分区队列 SAC 开启（把 `rabbitmqctl list_exchanges / list_queues` 输出摘要写进报告）。
3. `grep -r "topic_payment_result\|cg_commerce_payment_result\|cg_cct_client_review" paytech/` 只剩文档或为零。
4. R12 零违规。

## 5. 禁止事项

- 不改补偿事件表结构与 Guide；不在 compensation-event 引入 AMQP。
- 不改支付、入驻审核任何业务语义；消费 Handler 的业务幂等逻辑保持。
- 不执行业务库 SQL（测试库 DDL 除外，须记录）；不 up/down/restart 容器。
- 不调用 Superpowers skill；不提交到 `refactor-main` / `main`；不推送。

## 6. 交付物

1. 短分支提交，前缀 `refactor(step-08): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
2. 报告 `tasks/08-messaging-topology-report.md`：改动摘要 ≤ 30 行、第 4 节逐项、Broker 拓扑摘要、部署侧待办（旧队列删除、compose 变量）、待确认项。
3. 最终回复两行。
