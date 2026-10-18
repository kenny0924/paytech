# 08 · `paytech-messaging`：事件信封、分区有序与消费幂等（D24、D34、S9、C5）

现状见 [analysis/retained-systems.md](../analysis/retained-systems.md) 第 3 节：`paytech-compensation-event` 不含 RabbitMQ，`COMPENSATION_EVENT_GUIDE.md` 第 4 节禁止在其中内置路由与信封；Payment 已手工构造信封；只有 Payment 与 Risk 发布、Commerce 与 CCT 消费。

## 1. 模块划分

- `paytech-messaging-api`（core 可依赖）：`DomainEvent` 信封类型、`DomainEventPublisher` 端口接口、`ConsumedEventRepository` 接口、`AggregateKey` 工具、`DomainEventConsumer` 与 `@DomainEventHandler(eventType, strictOrder)`（Handler 在 `-core`，故注解必须在 api）。
- `paytech-messaging`（只有 app 依赖）：RabbitMQ 发布实现（Publisher Confirm、mandatory、Returned 检查）、交换机与队列声明器、单活消费者容器工厂、`t_consumed_event` 的 MyBatis Mapper 与幂等拦截器、Micrometer 指标。
- 与 compensation-event 的分工：**发布仍然发生在补偿事件 Handler 内**（提交后、可重试）；`paytech-messaging` 只提供"把一个信封可靠送到 Broker"和"消费端有序与幂等"，不碰补偿表。治理文档第 4 节无需修改。

## 2. 信封（Schema v1）

```json
{
  "eventId": "<默认 = eventKey，确定性；补偿重试不变>",
  "eventType": "EconomicFactRecorded",
  "schemaVersion": 1,
  "aggregateType": "LEDGER_BOOK",
  "aggregateId": "123456",
  "aggregateVersion": 8871,
  "occurredAt": "2026-10-17T10:00:00.123456Z",
  "sourceService": "ledger",
  "instanceId": "main",
  "correlationId": "<Request ID>",
  "payloadHash": "sha256:...",
  "payload": { }
}
```

- `aggregateId` 是分区键；`aggregateVersion` 单调递增（账簿用 `journal_sequence`，Attempt 用 Attempt 版本，Order 用 Order 版本），消费者据此判乱序与重放（D24）。
- Payment 现有字段映射：`eventKey`→保留在 Payload 之外作为 AMQP `messageId`；`sourceType/sourceId/sourceVersion`→`aggregateType/aggregateId/aggregateVersion`。
- AMQP 属性：`messageId = eventId`、`type = eventType`、`correlationId`、Persistent、`headers.x-aggregate-id`。
- 信封大小上限 64 KiB；Payload 不含 PII、Token、卡数据（现行规则）。
- `payloadHash` 由 `paytech-messaging` 发布端在 publish 时按 RFC 8785 规范字节计算，消费端拦截器校验；`paytech-messaging-api` 不依赖 `paytech-json-canonicalization`（治理：该 JAR 只由真实消费者引入，不得传递到各 `-core`）。
- 内部 Token 校验失败的 Problem 用公共错误码 `INTERNAL_TOKEN_INVALID`（401），`type` 保持 `about:blank`，与现行 Problem Profile 一致。

## 3. 拓扑（D34）

- **实现修订（第 01 步，2026-10-17）**：一致性哈希交换机只把一条消息路由到一个绑定队列，若多个订阅服务都直接绑在发布方的哈希交换机上，一条事件只会被其中一个服务收到。因此发布方交换机 `<producer>.events` 为 **Durable Fanout**；每个订阅服务声明自己的 `<consumer>.<producer>.hash`（`x-consistent-hash`，需在两个 compose 的 RabbitMQ 启用 `rabbitmq_consistent_hash_exchange` 插件）以交换机到交换机绑定接到发布方，再把分区队列以相同权重绑到它。路由键 = `aggregateId`。
- 每个消费服务对每个感兴趣的发布方声明 **N 个分区队列** `<consumer>.<producer>.p0..pN-1`（Quorum，`x-single-active-consumer=true`，`delivery-limit=5`，DLX `<consumer>.<producer>.dlx` → `.dlq`）。N 默认 4，由 `messaging.consumers.<producer>.partitions` 配置。
- 同一 `aggregateId` 恒落同一队列，单活消费者保证该队列串行消费 → 同聚合有序；不同聚合并行。
- 发布侧顺序：补偿事件 Handler 并发执行，可能让同聚合的两个事件乱序发出。规则：**事件的 `event_key` 包含 `aggregateId`**，`CompensationEventDispatcher` 按 `event_key` 的聚合前缀串行（messaging 提供 `AggregateSerialExecutor`，Dispatcher 以 `aggregateId` 哈希到固定线程）；再加消费侧版本判定兜底。
- **实现注记（2026-10-18，连接恢复）**：启动声明成功的发布与订阅拓扑登记到 `MessagingTopologyRecovery`（Spring AMQP `ConnectionListener`），每次新建 Broker 连接时按原参数逐项幂等重声明；单项参数冲突记 ERROR 并跳过依赖它的绑定，其余项照常恢复。分区监听容器改为 `missingQueuesFatal=false`，队列暂缺时持续重试。详见 `paytech-messaging/README.md`「连接恢复」与需求 `requirements/20261018/foundation-messaging-连接恢复重声明拓扑/`。
- **实现注记（2026-10-18，任务卡 02）**：重声明失败项与依赖它的绑定按 `messaging.recovery.*` 有界指数退避重试（默认 5s 起、上限 5m、不限次数），每次失败记 WARN，同一发布方或订阅连续失败达到阈值（默认 5）只记一次 ERROR。连接保持时：分区容器的消费者取消或缺失队列事件触发该订阅重声明；发布被 `NOT_FOUND` 拒绝时，发布方重声明 `<producer>.events` 并按事件目录补回订阅方哈希交换机的绑定，当次发布仍抛出由补偿重试。只删订阅方哈希交换机或 DLX/DLQ 仍无事件可触发，未引入定时轮询。
- 现有 `topic_payment_result` / `topic_risk_client_review` 与消费队列保留一个版本：发布方双写（旧 Topic + 新一致性哈希交换机）直到消费方切换完成，然后删除旧 Topic。

## 4. 消费幂等与顺序（`t_consumed_event`）

每库标准表：`event_id`（唯一）、`event_type`、`aggregate_type`、`aggregate_id`、`aggregate_version`、`consumed_at`；另有 `t_consumed_aggregate_version`：`(aggregate_type, aggregate_id)` → `last_version`。

消费拦截器：

1. `event_id` 已存在 → 确认消息，跳过（重放）。
2. `aggregateVersion` ≤ `last_version` → 确认并跳过（旧事件）。
3. `aggregateVersion` > `last_version + 1` 且 Handler 声明 `strictOrder=true` → **退避后把消息重新发布到本分区队列尾部**（Quorum 队列对 Nack 重入队的消息会优先重投，原样 Nack 等不到排在后面的缺失版本），Broker 确认后再 Ack 原消息；尝试次数记在头 `x-paytech-gap-attempts`，第 5 次仍有缺口则拒绝进入 DLQ 并告警；`strictOrder=false`（如 Webhook 投递）→ 直接处理。聚合首次出现、尚无版本记录时以该事件为基线直接处理。**`strictOrder=true` 的事件要求发布方按聚合使用连续的事件序号**（如 `journal_sequence`），用可能跳号的实体版本会造成永久缺口。
4. Handler 在本地事务内处理业务并写两张表，同事务提交（`transaction=SHARED`，默认）；消费者按 Handler 幂等键再做业务层幂等（现行规则）。**实现修订（第 08 步）**：当 Handler 复用继承 `AbstractProcessor` 的既有 Processor（它拒绝在已有事务中被调用）时，用 `transaction=HANDLER_MANAGED`：拦截器先以短事务判定重放 / 旧版本 / 缺口，再在事务外调用 Handler（Processor 自管事务），成功后另开短事务登记 `event_id` 并推进版本；Handler 提交后、登记前崩溃的窗口由业务幂等兜底，与切换前只靠业务幂等的现状一致。业务明确拒绝抛 `DomainEventRejectedException` 直接入 DLQ；运行时异常退避重投，5 次后入 DLQ。

## 5. 事件目录 v1

`paytech/foundation/paytech-messaging/catalog/events.yaml`：每个事件一条 `{eventType, schemaVersion, producer, aggregateType, aggregateVersionField, consumers[], strictOrder, payloadSchemaRef}`。构建期校验：`-core` 中发布的事件类型与注册的 Handler 必须在目录中（ArchUnit R12）。阶段 0 登记现有 `PaymentSucceeded`（v2，扩字段）、`ClientReviewDecided`、新增 `EconomicFactRecorded`（billing → ledger、account，C1）、`BalanceMoved`（仅 Schema）、`PaymentSucceeded` 的 card 消费、`SettlementRestrictionChanged`（compliance → payout、account，C3；阶段 0 仅 Schema 与发布端）。

## 6. Platform 的 AMQP 禁令（C5）

Platform 的 Webhook 投递按 D07 要直接消费 Owner 事件，必须接入 RabbitMQ。阶段 0 只解除 `platform-support` 聚合 POM 对 `org.springframework.amqp:*` 与 `spring-boot-starter-amqp` 的禁止并引入 `paytech-messaging`，不实现消费者；BFF 与 Gateway 的 AMQP 禁止保持。

## 7. 测试

- messaging 自身 `auto` 测试用本地 RabbitMQ：同键有序、异键并行、重放跳过、版本回退跳过、缺口等待与 DLQ、Publisher Confirm 失败抛错由补偿重试、信封大小上限。
- 各服务消费测试以真实本地 Broker 加 `Probe` 观察（现有模式）。
