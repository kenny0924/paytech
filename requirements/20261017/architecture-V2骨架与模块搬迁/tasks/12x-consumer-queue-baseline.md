# 任务卡 12x · 消费方测试的分区队列基线：监听器启动前清空本服务队列（跨模块测试隔离修复）

- 分支：`refactor/fix-consumer-queue-baseline`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-fixq`；起点：`refactor-main@d02e0bb1`（12 已合入）。
- 背景：全 Reactor 顺序运行时，payment-test 真实发布的 `PaymentSucceeded` 落在 card / acquiring 的持久分区队列（`card.payment.p0..p3`、`acquiring.payment.p0..p3`，Quorum、durable）。消费方测试应用一启动，监听器就把这些残留消息消费掉，写入 `t_consumed_event` / `t_consumed_aggregate_version`，并因 Payload 引用的聚合在本库不存在而进入重试。结果：card-test 37/38 失败（`CardDataMocker.assertEmpty` 报"专用库非空"，6 行），acquiring 在 08 合入后的全 Reactor 中大量失败也疑与此有关（当前 broker 上 `acquiring.payment.p*` 还有 26 条残留）。各测试基类现有的 `@BeforeEach purge()` 太晚：监听器在 Spring 上下文启动时已经开始消费。
- 目标：消费方 `auto` 测试在**监听器容器启动之前**清空本服务的全部分区队列与 DLQ，并把这一能力做成 Foundation 可复用件，card / acquiring / client-master 三个消费方测试统一使用；11b 的 ledger / account 测试也将复用。

## 1. 要求

1. 在 `paytech-messaging`（或 `paytech-test-support`，按现有职责选，报告说明理由）提供一个测试侧组件（例如 `PartitionQueueBaseline implements SmartLifecycle`）：phase 低于 Rabbit 监听器容器（`RabbitListenerEndpointRegistry` 为 `Integer.MAX_VALUE`），`start()` 时按 `MessagingTopology` 的命名清空给定 `(consumer, producer)` 的 `p0..p3` 与 DLQ；队列尚不存在时先声明拓扑再清空或安全跳过，但不能吞掉其它异常。只在测试 classpath 可用（test-jar 或 test-support 模块），生产代码不依赖。
2. card / acquiring / client-master 的测试配置注册该组件；保留现有 `@BeforeEach/@AfterEach purge()`（防同模块用例间串扰）。`CardDataMocker.assertEmpty` 的严格空基线语义不放松。
3. 不改生产消费逻辑、不改事件目录、不改 `paytech-messaging` 的生产拓扑；不删 broker 上的旧 `cg_acquiring_payment_result*` 队列（13 步清理）。
4. 验证（按顺序，先制造残留再证明修复）：
   - `cd paytech && ./mvnw -B verify -pl payment/paytech-payment-test,card/paytech-card-test,acquiring/paytech-acquiring-test,client-master/paytech-client-master-test -am -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment` 全绿（payment 先跑会往 card/acquiring 队列里投消息，正是要覆盖的场景）。
   - 测试环境变量：`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`，`set -a; source /Users/kenny/Desktop/Workspace/Code-MoR/local-environment/.env.local; set +a; export PAYTECH_REDIS_PASSWORD="$MOR_REDIS_PASSWORD"`；unset `OTEL_RESOURCE_ATTRIBUTES`。
   - 运行前如 `mor_card` 仍有残留行（`t_consumed_event`、`t_consumed_aggregate_version`），先在报告里记录计数，再用 root 对 **mor_card 测试库**执行 DELETE 清理（这是测试库，允许；命令写进报告）。不要碰其它库。
   - 不跑全 Reactor（主会话负责）。
5. 规范：`AGENTS.md`、`agent-docs/UNIT_TEST_GUIDE.md`（测试不创建 / 停止容器，可以清理自己拥有的队列）、`design/08-messaging-module.md`。禁止 `when().thenReturn` / `@MockBean`；不调用 Superpowers skill；不提交到 `refactor-main` / `main`；不推送。

## 2. 交付物

1. 短分支提交，前缀 `refactor(step-12x): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
2. 报告 `tasks/12x-consumer-queue-baseline-report.md`：根因（含证据：broker 队列深度、surefire 报错）、组件位置与 phase 选择、三处接入、验证输出（四个模块的 tests/failures）、残留清理命令、对 11b 的接入说明。
3. 最终回复两行：commit 哈希 + 四模块测试计数。
