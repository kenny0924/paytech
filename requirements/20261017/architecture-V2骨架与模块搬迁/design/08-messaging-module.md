# 08 · `paytech-messaging`：事件信封、分区有序与消费幂等（D24、D34、S9、C5）

现状见 [analysis/retained-systems.md](../analysis/retained-systems.md) 第 3 节：`paytech-compensation-event` 不含 RabbitMQ，`COMPENSATION_EVENT_GUIDE.md` 第 4 节禁止在其中内置路由与信封；Payment 已手工构造信封；只有 Payment 与 Risk 发布、Commerce 与 CCT 消费。

## 1. 模块划分

- `paytech-messaging-api`（core 可依赖）：`DomainEvent` 信封类型、`DomainEventPublisher` 端口接口、`ConsumedEventStore` 接口、`AggregateKey` 工具。
- `paytech-messaging`（只有 app 依赖）：RabbitMQ 发布实现（Publisher Confirm、mandatory、Returned 检查）、交换机与队列声明器、单活消费者容器工厂、`t_consumed_event` 的 MyBatis Mapper 与幂等拦截器、Micrometer 指标。
- 与 compensation-event 的分工：**发布仍然发生在补偿事件 Handler 内**（提交后、可重试）；`paytech-messaging` 只提供"把一个信封可靠送到 Broker"和"消费端有序与幂等"，不碰补偿表。治理文档第 4 节无需修改。

## 2. 信封（Schema v1）

```json
{
  "eventId": "01J...（ULID）",
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

## 3. 拓扑（D34）

- 每个发布服务一个交换机 `<service>.events`，类型 `x-consistent-hash`（需在两个 compose 的 RabbitMQ 启用 `rabbitmq_consistent_hash_exchange` 插件）；路由键 = `aggregateId`（插件对路由键做哈希）。
- 每个消费服务对每个感兴趣的发布方声明 **N 个分区队列** `<consumer>.<producer>.p0..pN-1`（Quorum，`x-single-active-consumer=true`，`delivery-limit=5`，DLX `<consumer>.<producer>.dlx` → `.dlq`），以相同权重绑定到发布方交换机。N 默认 4，由 `messaging.partitions.<producer>` 配置。
- 同一 `aggregateId` 恒落同一队列，单活消费者保证该队列串行消费 → 同聚合有序；不同聚合并行。
- 发布侧顺序：补偿事件 Handler 并发执行，可能让同聚合的两个事件乱序发出。规则：**事件的 `event_key` 包含 `aggregateId`**，`CompensationEventDispatcher` 按 `event_key` 的聚合前缀串行（messaging 提供 `AggregateSerialExecutor`，Dispatcher 以 `aggregateId` 哈希到固定线程）；再加消费侧版本判定兜底。
- 现有 `topic_payment_result` / `topic_risk_client_review` 与消费队列保留一个版本：发布方双写（旧 Topic + 新一致性哈希交换机）直到消费方切换完成，然后删除旧 Topic。

## 4. 消费幂等与顺序（`t_consumed_event`）

每库标准表：`event_id`（唯一）、`event_type`、`aggregate_type`、`aggregate_id`、`aggregate_version`、`consumed_at`；另有 `t_consumed_aggregate_version`：`(aggregate_type, aggregate_id)` → `last_version`。

消费拦截器：

1. `event_id` 已存在 → 确认消息，跳过（重放）。
2. `aggregateVersion` ≤ `last_version` → 确认并跳过（旧事件）。
3. `aggregateVersion` > `last_version + 1` 且 Handler 声明 `strictOrder=true`（如账户消费过账）→ 拒绝并重新投递（等待缺口补齐），超过 `delivery-limit` 进 DLQ 并告警；`strictOrder=false`（如 Webhook 投递）→ 直接处理。
4. Handler 在本地事务内处理业务并写两张表，同事务提交；消费者按 Handler 幂等键再做业务层幂等（现行规则）。

## 5. 事件目录 v1

`paytech/foundation/paytech-messaging/catalog/events.yaml`：每个事件一条 `{eventType, schemaVersion, producer, aggregateType, aggregateVersionField, consumers[], strictOrder, payloadSchemaRef}`。构建期校验：`-core` 中发布的事件类型与注册的 Handler 必须在目录中（ArchUnit R12）。阶段 0 登记现有 `PaymentSucceeded`（v2，扩字段）、`ClientReviewDecided`、新增 `EconomicFactRecorded`（billing → ledger、account，C1）、`BalanceMoved`（仅 Schema）、`PaymentSucceeded` 的 card 消费、`SettlementRestrictionChanged`（compliance → payout、account，C3；阶段 0 仅 Schema 与发布端）。

## 6. Platform 的 AMQP 禁令（C5）

Platform 的 Webhook 投递按 D07 要直接消费 Owner 事件，必须接入 RabbitMQ。阶段 0 只解除 `platform-support` 聚合 POM 对 `org.springframework.amqp:*` 与 `spring-boot-starter-amqp` 的禁止并引入 `paytech-messaging`，不实现消费者；BFF 与 Gateway 的 AMQP 禁止保持。

## 7. 测试

- messaging 自身 `auto` 测试用本地 RabbitMQ：同键有序、异键并行、重放跳过、版本回退跳过、缺口等待与 DLQ、Publisher Confirm 失败抛错由补偿重试、信封大小上限。
- 各服务消费测试以真实本地 Broker 加 `Probe` 观察（现有模式）。
