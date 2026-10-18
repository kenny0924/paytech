# 任务卡 01 · 连接恢复时重声明拓扑

- 分支：`foundation/messaging-redeclare-on-recovery`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-msg1`；起点 `main`。
- 先读：本需求 `requirement.md`；`paytech/foundation/paytech-messaging/README.md`；`core/topology/MessagingTopology*.java`、`consume/PartitionedListenerContainers.java`、`publish/**`、`testkit/PartitionQueueBaseline.java`（12x）；`requirements/20261017/architecture-V2骨架与模块搬迁/design/08-messaging-module.md`；`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`。

## 要求

1. 实现：在 `paytech-messaging` 内注册 Spring AMQP `ConnectionListener`（或等价机制），`onCreate` 时按本服务已装配的发布方 / 订阅方参数调用 `MessagingTopologyDeclarer` 幂等重声明（参数冲突时让声明失败并记 ERROR，不吞）；确认 `PartitionedListenerContainers` 的监听容器在队列重新出现后自动恢复（Spring AMQP 的 `missingQueuesFatal` / 重试参数按需调整并说明）。启动时的首次声明路径不变。避免并发重复声明（多个连接同时恢复时只声明一次或幂等无害）。
2. 测试（`paytech-messaging` 的 `auto`，真实本机 Broker，禁止 `when().thenReturn` / `@MockBean`）：① 删除本服务全部分区队列、DLQ 与交换机后，调用 `CachingConnectionFactory.resetConnection()`（或关闭连接）触发恢复，断言拓扑按原参数全部恢复、发布一条事件能被消费；② 恢复期间发布失败按既有 unroutable 策略处理，不丢消息语义（按 README 现有合同写断言）；③ 参数冲突（先手工建一个同名但不同参数的经典队列）时恢复声明失败并有 ERROR 日志、不影响其他队列。
3. 文档：`paytech-messaging/README.md` 增加"连接恢复"一节；`design/08-messaging-module.md` 补一条实现注记。
4. 验证：`cd paytech && ./mvnw -B verify -pl foundation/paytech-messaging -am`，再 `-pl acquiring/paytech-acquiring-test,card/paytech-card-test,client-master/paytech-client-master-test,ledger/paytech-ledger-test,account/paytech-account-test -am verify`（消费方不回归）。环境：`JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`；`-Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment`；`set -a; source /Users/kenny/Desktop/Workspace/Code-MoR/local-environment/.env.local; set +a; export PAYTECH_REDIS_PASSWORD="$MOR_REDIS_PASSWORD"`；不打印密码；用 `verify` 不用 `test`。
5. 禁止：不改业务服务代码与事件目录；不操作容器（本机 RabbitMQ 刚按固定主机名重建，队列由测试自行声明）；不调用 Superpowers skill；不提交到 main；不推送。提交前缀 `foundation(messaging): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
6. 报告 `tasks/01-report.md`：实现要点、与 Spring AMQP 恢复机制的交互、测试矩阵与计数、待确认。最终回复两行：commit 哈希 + 测试计数。
