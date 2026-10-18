# 第 12x 步报告 · 消费方测试的分区队列基线

- 分支：`refactor/fix-consumer-queue-baseline`（起点 `refactor-main@d02e0bb1`）。
- 结论：任务卡第 4 条命令 BUILD SUCCESS，用时 4:36。四个模块全部 0 失败、0 错误、0 跳过。
- 没有改生产消费逻辑、事件目录或生产拓扑。没有删除旧 `cg_*` 队列，也没有 up / down / restart 任何容器。

## 1. 根因与证据

分区队列 `<consumer>.payment.p0..p3` 是持久 Quorum 队列。payment-test 真实发布的 `PaymentSucceeded` 会经 Fanout 交换机落到 card 与 acquiring 的分区队列。消费方测试的 Spring 上下文一启动，分区监听容器就开始消费这些残留消息，早于测试基类的 `@BeforeEach purge()`。

开工前的证据：

| 观察点 | 值 |
|---|---|
| `acquiring.payment.p0..p3` 残留消息 | 8 + 6 + 4 + 8 = 26 |
| `card.payment.p0..p3` 残留消息 | 0（已被上次 card 启动消费掉） |
| `mor_card.t_consumed_event` 残留行 | 3（`PaymentSucceeded`，聚合 8000002 / 8100001 / 8100002，版本 3） |
| `mor_card.t_consumed_aggregate_version` 残留行 | 3（同上） |
| 上次 card-test surefire（Code-MoR-refactor 工作区） | 37/38 失败，均为 `专用库非空，不自动删除原有数据 ==> expected: <0> but was: <6>` |

失败中的 6 行正是上述两张消费登记表各 3 行。

## 2. 组件位置与 phase

- 位置：`paytech-messaging` 的 `src/test/java/com/paytech/foundation/messaging/testkit/`，用 `maven-jar-plugin` 的 `test-jar` 目标只打包 `testkit/**`。打出的 `paytech-messaging-1.0.0-SNAPSHOT-tests.jar` 只有两个类，没有本模块的测试、Handler 或配置文件。BOM `paytech-dependencies` 增加 `type=test-jar, scope=test` 的管理条目。
- 不选 `paytech-test-support` 的理由：test-support 被 Gateway、BFF 等禁止 AMQP 的模块使用，放进去会把 spring-rabbit 带给它们。组件还依赖 `MessagingTopology`、`MessagingTopologyDeclarer`、`MessagingProperties` 与 `DomainEventHandlerRegistry`，这些都属于 messaging。生产代码不依赖 test-jar。R8 只检查生产作用域依赖，test 作用域引入不受影响。
- `PartitionQueueBaseline implements SmartLifecycle`：phase 为 `SmartLifecycle.DEFAULT_PHASE - 1`。`PartitionedListenerContainers` 没有覆盖 phase，使用 `DEFAULT_PHASE`（`Integer.MAX_VALUE`），所以基线总是先启动，也早于 `RabbitListenerEndpointRegistry`。
- `start()` 先用生产声明器按相同参数重复声明订阅拓扑（幂等）。队列不存在时由此创建，参数冲突时 Broker 拒绝并向上抛出，使上下文启动失败。随后清空 `p0..pN-1` 与 DLQ，并按订阅记一条 INFO 日志说明清除条数。
- `PartitionQueueBaselineConfiguration`：订阅集合取自宿主实际装配的 `DomainEventHandlerRegistry.producers()`，分区数取 `messaging.consumers.<producer>.partitions`，默认 4。因此清空的正是监听容器将要消费的队列，不会碰其他服务的队列。
- messaging 自身新增 `PartitionQueueBaselineAutoTest`，共 4 例：phase 早于真实容器 Bean；残留消息被清空；拓扑缺失时先声明；同名经典队列冲突时 `start()` 抛 `AmqpException`。messaging 的 `TestApplication` 同样注册该配置。

## 3. 三处接入

| 模块 | 接入点 |
|---|---|
| card-test | `CardTestConfiguration` 增加 `@Import(PartitionQueueBaselineConfiguration.class)` |
| acquiring-test | `AcquiringTestApplication` 的 `@Import` 增加该配置 |
| client-master-test | `ClientMasterTestApplication` 的 `@Import` 增加该配置 |

三个 `-test` 模块的 pom 各加一条 `paytech-messaging` test-jar 依赖。现有 `@BeforeEach/@AfterEach purge()` 全部保留。`CardDataMocker.assertEmpty` 未改。acquiring 的 manual 测试使用独立应用入口，不在本次范围内。

## 4. 残留清理

运行前用 root 清理 `mor_card` 测试库，没有碰其它库：

```bash
docker exec mor-local-mysql-1 sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -e "DELETE FROM mor_card.t_consumed_event; DELETE FROM mor_card.t_consumed_aggregate_version;"'
```

清理前两表各 3 行，清理后各 0 行。Broker 队列没有手工清理，留给基线在测试中证明。

## 5. 验证

环境按任务卡设置：JDK 25、source `.env.local`、导出 `PAYTECH_REDIS_PASSWORD`、unset `OTEL_RESOURCE_ATTRIBUTES`，没有打印密码值。

```bash
cd paytech && ./mvnw -B verify -pl payment/paytech-payment-test,card/paytech-card-test,acquiring/paytech-acquiring-test,client-master/paytech-client-master-test -am -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment
```

| 模块 | tests | failures | errors |
|---|---:|---:|---:|
| paytech-payment-test | 63 | 0 | 0 |
| paytech-card-test | 38 | 0 | 0 |
| paytech-acquiring-test | 803 | 0 | 0 |
| paytech-client-master-test | 58 | 0 | 0 |
| paytech-messaging（上游） | 67 | 0 | 0 |

基线日志证明残留场景确实出现并被覆盖：

| 上下文 | 启动时清除的残留消息 |
|---|---:|
| acquiring / payment | 26（开工前的残留） |
| card / payment | 41（本次 payment-test 先运行时投递） |
| client-master / compliance | 0 |

运行后 `mor_card` 两张消费登记表均为 0 行。Reactor 顺序是 acquiring-test 先于 payment-test，所以 payment-test 本次投递的 40 条消息仍在 `acquiring.payment.p0..p3`。这是预期状态，下次 acquiring 测试启动时由基线清除。

## 6. 对 11b 的接入说明

ledger / account 的 `-test` 模块按第 3 节做两处改动即可：

1. pom 加 `com.paytech:paytech-messaging`，`type=test-jar`、`scope=test`，版本由 BOM 管理。
2. 测试组合根 `@Import(PartitionQueueBaselineConfiguration.class)`。

订阅的发布方与分区数自动取自宿主 Handler 与 `messaging.consumers.*`，例如 `ledger.billing.p0..p3`，无需另写名单。每例 purge 仍由各自的 Mocker 负责。
