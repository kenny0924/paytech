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
- **参数冲突**：某项与 Broker 上同名对象参数不一致（例如手工建的同名经典队列）时，该项声明失败并记 ERROR（含 Broker 原因），依赖它的绑定跳过，其余项照常恢复，最后汇总记一条 ERROR。冲突的分区队列不绑定到哈希交换机，消息改由其余分区承接。失败项不自动重试；修复冲突后需重建连接或重启服务。
- **不覆盖的情形**：连接未断、仅有队列被删除时不会触发重声明，容器会持续重试并记 WARN，直到下次建连或重启。
