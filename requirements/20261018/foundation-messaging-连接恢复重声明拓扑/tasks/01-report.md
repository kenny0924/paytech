# 任务卡 01 报告 · 连接恢复时重声明拓扑

- 分支：`foundation/messaging-redeclare-on-recovery`；实际 worktree：`/Users/kenny/Desktop/Workspace/Code-MoR/Code-MoR-msg1`（嵌在主仓库内，与任务卡路径不同，由主会话处理）。
- 本需求目录位于 `local-environment/requirements/20261018/`，主会话将在合入后移到根 `requirements/`。

## 实现要点

- 新增 `core/topology/MessagingTopologyRecovery`（Spring AMQP `ConnectionListener`）。自动配置创建它并 `addConnectionListener` 到宿主 `ConnectionFactory`。
- 发布端 Bean 在 `declareProducer` 成功后 `registerProducer`；消费端容器 Bean 在每个 `declareSubscription` 成功后 `registerSubscription`。启动首次声明路径与失败语义不变。
- `onCreate` 按登记的服务名与分区数重声明全部拓扑；用 `ReentrantLock` 串行，多个连接同时创建时重复声明幂等无害；未登记任何拓扑时直接返回。
- `MessagingTopologyDeclarer` 新增 `redeclareProducer` / `redeclareSubscription`：与启动路径共用同一组拓扑对象构造（参数一致），逐项声明。单项 `AmqpException` 记 ERROR（含 Broker 原因）并计数，依赖失败项的绑定跳过（冲突分区队列不绑到哈希交换机），其余项继续；`MessagingTopologyRecovery` 有失败时再汇总记一条 ERROR，全部成功记 INFO。不向连接回调抛出，避免一项冲突让整个连接不可用。
- `PartitionedListenerContainerFactory`：`missingQueuesFatal` 由 `true` 改为 `false`。原因：Spring AMQP 4.0.5 中被动声明 3 次失败（间隔 5 秒）后抛 `QueuesNotAvailableException`，`true` 时容器永久停止，只能重启服务；`false` 时按 5 秒间隔无限重启消费者，下一次建连或重声明后自动恢复。启动时拓扑已在容器启动前声明，声明失败仍使启动失败。其余重试参数保持默认。

## 与 Spring AMQP 恢复机制的交互（已核对 4.0.5 字节码）

- `CachingConnectionFactory.createConnection` 在 `ReentrantLock` 内先设置 `connection.target`，再回调 `onCreate`。回调里的声明经 `RabbitTemplate` 重入同一把锁并复用新连接，与 Spring 自带 `RabbitAdmin` 的恢复方式相同。其他线程在回调完成前阻塞在锁上，因此监听容器重启时的被动声明总能看到已恢复的队列。
- `addConnectionListener` 在连接已存在时会立即回调一次；此时登记表为空或只含已声明项，结果幂等。
- 断连后容器消费者重启时会自行建连，从而触发重声明。新增测试中有一例不靠调用方，仅靠容器线程完成恢复。
- Boot 自带的 `amqpAdmin` 只声明上下文中的 Declarable Bean，本模块拓扑不是 Bean，所以原先断连后不会补回，这就是本次的问题根因。

## 测试矩阵（`MessagingTopologyRecoveryAutoTest`，真实本机 Broker，无 Stub）

| 用例 | 场景 | 断言 |
|---|---|---|
| `test_normalFlow_lostTopologyRedeclaredOnReconnect` | 删除本次运行全部交换机、分区队列、DLQ 后 `resetConnection()` 再建连 | 删除后发布被 Broker 拒绝并抛 `DomainEventPublishException`；经 Management API 核对恢复后的类型、durable、`x-single-active-consumer`、`x-delivery-limit=5`、DLX 参数与全部绑定；重发同一事件被消费一次；无 ERROR |
| `test_normalFlow_listenerContainersReconnectAndRedeclare` | 同上，但只 `resetConnection()`，不由测试建连 | 容器线程建连并触发重声明，拓扑按原参数恢复，事件被消费；无 ERROR |
| `test_businessRuleViolation_unroutableBeforeRecoveryRetriedAfterReconnect` | 只删订阅侧拓扑，发布方交换机仍在 | 按默认 `FAIL` 策略抛"未路由"，confirmed 与 unroutable 计数均为 0；恢复后补偿式重发同一 `eventId` 被消费一次 |
| `test_edgeCase_conflictingQueueFailsAloneWithErrorLog` | 先建同名无参数经典队列 `p1`，再重建连接 | `p1` 仍为 classic 且未绑定；其余拓扑按原参数恢复；恰好一条带异常的 ERROR 指向 `p1`，另有一条汇总 ERROR"失败 1 项"；8 个聚合的事件全部经其余分区消费 |

先以空实现运行（红）：3 例全部失败于拓扑缺失；实现后全部通过。新增测试侧组件：`BrokerTopologyProbe`（Management API 只读）、`TopologyErrorLogProbe`（拓扑包 ERROR 观察，每例清空）、`BrokerTestSupport` 的删除与冲突队列辅助方法。每例 `@AfterEach` 删除冲突队列并经启动路径恢复拓扑，之后才运行全局清理。

## 验证

| 命令 | 结果 |
|---|---|
| `./mvnw -B verify -pl foundation/paytech-messaging -am` | BUILD SUCCESS；paytech-messaging 71 例（含新增 4 例）0 失败 |
| `./mvnw -B verify -pl acquiring/paytech-acquiring-test,card/paytech-card-test,client-master/paytech-client-master-test,ledger/paytech-ledger-test,account/paytech-account-test -am` | BUILD SUCCESS；acquiring-test 804、card-test 38、client-master-test 63、ledger-test 204、account-test 40，全部 0 失败 |

全 Reactor 未运行（按指示一次一条 Maven、不跑全 Reactor）；mor-test 阶段 2 验证由主会话执行。

## 待确认

1. 恢复声明的失败项不自动重试：冲突修复后需再次建连或重启服务。是否需要定时重试或指标（如 `messaging.topology.redeclare{outcome=failed}`）？本次未加。
2. 连接未断、只有队列被删除时不会触发重声明，容器会每 5 秒重试并记 WARN。此情形不在本需求范围。
3. 冲突分区队列不绑定到哈希交换机，消息由其余分区承接，该期间聚合到分区的映射会变化。也可以选择绑定该队列，但那样消息会进入参数错误的队列；本次选择不绑定，请确认。
4. 模块原无 README，本次新建 `paytech-messaging/README.md`（接入要点与"连接恢复"一节）。
