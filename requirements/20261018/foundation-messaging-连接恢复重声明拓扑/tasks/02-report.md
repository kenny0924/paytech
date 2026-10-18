# 任务卡 02 报告 · 重声明失败自动重试；连接不断而队列被删时的恢复

- 分支：`foundation/messaging-recovery-retry`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-msg2`；起点 main@398c5289。
- 已读：`requirement.md`、`tasks/01-report.md`、本任务卡、`paytech-messaging/README.md`、`MessagingTopologyRecovery`、`MessagingTopologyDeclarer`、`PartitionedListenerContainerFactory`、`PartitionedListenerContainers`、`MessagingAutoConfiguration`、`RabbitDomainEventPublisher`、`agent-docs/UNIT_TEST_GUIDE.md`、design/08；Spring AMQP 4.0.5 的 `BlockingQueueConsumer`、`SimpleMessageListenerContainer` 字节码。

## 实现要点

- **声明计划**：`MessagingTopologyDeclarer` 的恢复路径改为生成带依赖关系的声明项（新增包内 `TopologyDeclaration`），每个绑定依赖其两端；启动路径 `declare*` 不变。构造参数由 `AmqpAdmin` 改为 `RabbitAdmin`，用于被动检查交换机是否存在。
- **按单元管理与重试**：`MessagingTopologyRecovery` 以单元管理拓扑（每个发布方一个、每个订阅一个），每项完成即记录是否待重试。失败项记 WARN（含 Broker 原因），依赖它的绑定暂缓。待重试项按新增 `TopologyRetryPolicy` 有界指数退避，在守护线程 `messaging-topology-retry` 上只重试这些项；`destroy()` 停止线程。同一单元连续失败达到阈值记一次 ERROR，补齐后记 INFO 并归零。
- **三种执行方式**：新建连接时全部项（含待重试项）同步重声明，保持 01 的顺序保证；拓扑缺失触发时跳过待重试项，避免容器每 5 秒触发时刷 WARN；退避重试只声明待重试项。
- **不加跨声明的锁**：01 的 `ReentrantLock` 已去掉。新建连接的回调持有 `CachingConnectionFactory` 的锁，另一线程若持本类锁再取连接会死锁。声明本身幂等，状态变更用短同步块。
- **分区队列被删**：分区容器设置事件发布器 `subscriptionWatcher`。收到 `MissingQueueEvent`（每次被动声明失败时在容器线程上发布，早于其 5 秒重试等待），或 `ListenerContainerConsumerFailedEvent` 且原因为 `ConsumerCancelledException` 时，在容器线程上重声明该订阅。同一单元同时只执行一次。
- **发布方交换机被删**：Nack 原因含 `NOT_FOUND - no exchange '<producer>.events'` 时，发布器在当前线程重声明发布方单元。该单元包含交换机，以及按事件目录到各订阅方 `<consumer>.<producer>.hash` 的绑定，订阅方交换机不存在时跳过。当次发布仍抛出 `DomainEventPublishException`。
- **配置**：`messaging.recovery.initial-interval`（5s）、`max-interval`（5m）、`multiplier`（2.0）、`error-threshold`（5），已带注释并写入 messaging 测试 `application-mock.yaml`。
- **兼容**：`RabbitDomainEventPublisher` 原有构造器保留，消费方测试中手工创建的发布器行为不变。新增构造器多一个可为 null 的恢复组件参数。

## 测试矩阵（`MessagingTopologyRecoveryAutoTest`，真实本机 Broker，无 Stub）

| 用例 | 场景 | 断言 |
|---|---|---|
| `test_normalFlow_lostTopologyRedeclaredOnReconnect` | 01 用例，先停容器再删全部拓扑，重建连接 | 恢复参数与绑定；无 WARN/ERROR；同一事件被消费一次 |
| `test_normalFlow_listenerContainersReconnectAndRedeclare` | 01 用例，容器运行中，只重置连接 | 拓扑恢复、事件被消费；无 ERROR |
| `test_businessRuleViolation_unroutableBeforeRecoveryRetriedAfterReconnect` | 01 用例，先停容器再删订阅侧拓扑 | 按 FAIL 抛"未路由"；重连后重发被消费一次 |
| `test_edgeCase_conflictingQueueRetriedUntilResolved`（替换 01 的冲突用例） | 同名经典队列 `p1` 冲突，重建连接触发恢复失败；之后删除冲突队列，不重建连接 | `p1` 为 classic 且未绑定，其余恢复；每次失败一条带异常的 WARN 指向 `p1`；达到阈值恰好一条 ERROR，此后继续失败也不再增加；删除冲突后由重试补回 Quorum `p1` 与绑定，新事件经 `p1` 消费 |
| `test_edgeCase_partitionQueueDeletedWhileConnected` | 连接保持，删除曾承接聚合 A 的 `p2` | 自动恢复参数与绑定；A@2 被消费一次；重建的 `p2` 承接新事件；无 WARN/ERROR |
| `test_businessRuleViolation_producerExchangeDeletedWhileConnected` | 连接保持，删除 `<producer>.events` | 当次发布抛"交换机缺失"且不计成功；随即交换机与到订阅方哈希交换机的绑定已恢复；再次发布被消费一次 |

先在原实现上运行（红）：三个新用例失败，分别失败于未补回分区队列、无重试且失败记 ERROR、交换机未重声明。实现后全部通过。三个 01 用例改为先停监听容器，因为删除队列现在会经容器事件立刻恢复，会与"重连前拓扑缺失"的断言竞争。测试侧新增或调整：`TopologyLogProbe`（原 `TopologyErrorLogProbe`，改为分别记录 WARN 与 ERROR）、`HandledEventProbe.thread(...)`（按线程名判断经哪个分区消费）、`BrokerTestSupport.deleteExchange`。

## 验证

| 命令 | 结果 |
|---|---|
| `./mvnw -B verify -pl foundation/paytech-messaging -am` | BUILD SUCCESS；paytech-messaging 73 例 0 失败（71 − 1 替换 + 3 新增） |
| `./mvnw -B verify -pl acquiring/paytech-acquiring-test,card/paytech-card-test,client-master/paytech-client-master-test,ledger/paytech-ledger-test,account/paytech-account-test -am` | BUILD SUCCESS；acquiring-test 804、card-test 38、client-master-test 63、ledger-test 204、account-test 40，全部 0 失败 |

全 Reactor 与 mor-test 阶段 2 未运行，留给主会话。

## 发现与待确认

1. **哈希环随队列重建变化**：实测删除并重建 `p2` 后，原落在 `p2` 的聚合 A 的下一版本落到了 `p3`。重建期间同一聚合可能分布在两个分区，严格有序事件靠消费侧版本缺口判定兜底。测试按此只断言消费一次。
2. **发布方补回订阅方绑定**：删除 `<producer>.events` 会连带删除订阅方接在其上的绑定，订阅方没有任何事件可感知。本次由发布方按事件目录补回，订阅方哈希交换机不存在时跳过。这让发布方声明了订阅方拥有的绑定，参数与订阅方自己声明的完全相同，请确认可接受。
3. **交换机缺失时两种策略都抛出**：任务卡写"按既有 unroutable 策略处理当次发布"。现有合同与 `RabbitDomainEventPublisherAutoTest.test_edgeCase_unroutablePolicies` 都要求 Broker 拒绝（Nack）在 `ACK_AND_WARN` 下仍然失败，本次保留该行为：当次发布抛出，由补偿重试。若希望 `ACK_AND_WARN` 下把交换机缺失视为未路由并成功，需要改合同。
4. **仍不覆盖的情形**：连接保持时只删订阅方哈希交换机或 DLX/DLQ，没有消费者取消，发布方也可能仍能路由到其他订阅方，没有事件可触发。重试中的绑定若其已就绪的依赖被再次删除，也要等下次建连或缺失触发。按任务卡未引入定时全量轮询。
5. **ERROR 语义变化**：01 中单项失败立即记 ERROR，现在改为每次失败记 WARN，连续失败到阈值才记一次 ERROR，告警规则如按 ERROR 配置需相应调整。未加指标。
6. 消费方模块的 `application-mock.yml` 本就只列出部分 `messaging.*` 键，本次未改业务模块，未补 `messaging.recovery.*`。

**主会话合入决定（2026-10-18）**：第 1–6 项全部接受——哈希环变化由消费侧版本缺口兜底；发布方按目录补回订阅方绑定（参数一致）；交换机缺失时两种策略均抛出、交补偿重试；不引入定时轮询；ERROR 语义改为阈值告警（告警规则按 WARN/ERROR 阈值调整）；业务模块 `application-mock.yml` 不补 `messaging.recovery.*`（走默认值）。
