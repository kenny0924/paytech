# paytech-messaging

RabbitMQ 领域事件发布、分区有序消费与消费幂等的 `-app` 侧实现。合同与拓扑以 [design/08](../../../requirements/20261017/architecture-V2骨架与模块搬迁/design/08-messaging-module.md) 为准；本文只记录接入与运行行为。

## 接入

- `-app` 引入本模块即由 `MessagingAutoConfiguration` 自动装配；`messaging.producer.service` 启用发布端，`messaging.consumer.service` 启用消费端。
- 发布端要求宿主连接开启 `spring.rabbitmq.publisher-confirm-type=correlated` 与 `spring.rabbitmq.publisher-returns=true`。
- 未路由（mandatory 退回）按 `messaging.publisher.unroutable-policy` 处理：默认 `FAIL` 抛出 `DomainEventPublishException`，由补偿任务重试；`ACK_AND_WARN` 只用于订阅方尚未接入的过渡期。
- 消费方 `-test` 模块以 test-jar 引入 `PartitionQueueBaselineConfiguration`，见 `agent-docs/UNIT_TEST_GUIDE.md` 第 7.3 节。

## 连接恢复

Broker 重建、节点更换或长时间断连后，服务无需重启即可恢复发布与消费。

- **启动声明不变**：发布方交换机与订阅分区拓扑仍在 Bean 创建时声明，失败使启动失败。声明成功后才登记到 `MessagingTopologyRecovery`。
- **每次新建连接即重声明**：`MessagingTopologyRecovery` 是注册在宿主 `ConnectionFactory` 上的 Spring AMQP `ConnectionListener`。`CachingConnectionFactory` 先设置新连接、再在创建线程上回调 `onCreate`，回调按登记的服务名与分区数重声明 `<producer>.events`、`<consumer>.<producer>.hash`、DLX/DLQ、`p0..pN-1` 及其绑定，参数与启动时相同。声明幂等；多个连接同时创建时串行执行，重复声明无害。
- **监听容器自动恢复**：断连后容器按 Spring AMQP 默认间隔重启消费者，重启时建连会先触发重声明，再被动声明队列并恢复消费。分区容器设置 `missingQueuesFatal=false`，队列暂时缺失时持续重试，不会在 3 次失败后永久停止。
- **恢复前的发布**：拓扑恢复前发布失败按既有合同处理。发布方交换机缺失时 Broker 拒绝，订阅侧缺失时按未路由策略处理。默认均抛出 `DomainEventPublishException`，补偿任务保留并重试，不丢失事件；同一 `eventId` 重发后由消费幂等去重。
- **参数冲突**：某项与 Broker 上同名对象参数不一致（例如手工建的同名经典队列）时，该项声明失败并记 WARN（含 Broker 原因），依赖它的绑定暂缓，其余项照常恢复。冲突的分区队列不绑定到哈希交换机，消息改由其余分区承接。

### 重试与队列被删

- **失败项重试**：失败项与暂缓的绑定按 `messaging.recovery.*` 有界指数退避重试，只重试这些项，不限次数，直到成功或应用停止。默认 5 秒起、每次翻倍、上限 5 分钟。每次失败记 WARN；同一单元（一个发布方或一个订阅）连续失败达到 `error-threshold`（默认 5）时记一次 ERROR，之后不再重复。全部补齐后记 INFO 并补上暂缓的绑定，计数归零。重试线程 `messaging-topology-retry` 是守护线程，应用关闭时停止。修复冲突后无需重建连接或重启服务。
- **分区队列被删**：RabbitMQ 删除队列时会取消其消费者。分区容器的事件发布器收到消费者取消或被动声明找不到队列的事件后，在容器线程上重声明该订阅；容器随后的被动声明即可看到队列并恢复消费。正在等待重试的项不在这条路径上重复尝试，避免每 5 秒刷 WARN。
- **发布方交换机被删**：发布被 Broker 以 `NOT_FOUND - no exchange '<producer>.events'` 拒绝时，在发布线程上同步重声明交换机，并按事件目录补回到各订阅方 `<consumer>.<producer>.hash` 的绑定。删除交换机会同时删除这些绑定，而订阅方收不到任何信号，因此由发布方补回；订阅方哈希交换机不存在时跳过。当次发布仍按 Broker 拒绝抛出 `DomainEventPublishException`，两种未路由策略相同，由补偿任务重试；下一次发布即可路由。
- **哈希环会变化**：分区队列被删再重建后，一致性哈希交换机按新的绑定重新分布路由键，同一聚合可能落到其他分区。严格有序事件由消费侧版本判定兜底。
- **仍不覆盖的情形**：连接保持时只删除订阅方的一致性哈希交换机或 DLX/DLQ，队列和消费者都不受影响，发布方也可能仍路由成功，没有事件可触发重声明，需等下次建连或重启。未引入定时全量轮询。
