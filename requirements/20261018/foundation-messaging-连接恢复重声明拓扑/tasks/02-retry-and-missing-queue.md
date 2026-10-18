# 任务卡 02 · 重声明失败自动重试；连接不断而队列被删时的恢复

- 分支：`foundation/messaging-recovery-retry`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-msg2`；起点 `main`（含 fecae671 的 `MessagingTopologyRecovery`）。
- 背景：任务卡 01 报告待确认第 1、3 项——① 重声明失败项（如同名经典队列参数冲突）不自动重试，修复冲突后需重建连接或重启服务；② 连接保持时队列被删除（运维误删、策略过期），没有任何事件触发重声明，监听容器只会在被动声明失败后反复重试。
- 先读：`requirement.md`、`tasks/01-report.md`、`paytech/foundation/paytech-messaging/README.md`（连接恢复一节）、`core/topology/MessagingTopologyRecovery.java`、`MessagingTopologyDeclarer.java`、`consume/PartitionedListenerContainerFactory.java`、`PartitionedListenerContainers.java`、`agent-docs/UNIT_TEST_GUIDE.md`。

## 要求

1. **失败重试**：`MessagingTopologyRecovery` 对单项声明失败做有界指数退避重试（例如 5s 起、上限 5 分钟、不限次数直到成功或应用停止；参数由 `messaging.recovery.*` 配置并带默认值与注释），重试只针对失败项；每次失败记 WARN，连续失败超过阈值记 ERROR 一次（不刷屏）；成功后记 INFO 并补绑定。重试线程为守护线程，应用关闭时停止。
2. **队列被删的检测与恢复**：订阅方监听容器收到 Broker 的 consumer cancel（RabbitMQ 删除队列时会取消其消费者；Spring AMQP 以 `ConsumerCancelledException` / 容器重启表现）或被动声明失败时，触发对该订阅的重声明（复用 01 的声明路径），容器随后自动恢复消费；发布方侧对 `<producer>.events` 交换机缺失（`NOT_FOUND` 通道异常）同样触发重声明并按既有 unroutable 策略处理当次发布。不引入定时全量轮询，除非证明事件驱动不可覆盖某场景（报告说明）。
3. **测试**（`paytech-messaging` `auto`，真实本机 Broker；禁止 `when().thenReturn` / `@MockBean`）：① 先制造同名经典队列冲突 → 恢复失败 → 删除冲突队列 → 不重建连接，等待重试成功，拓扑恢复、事件可消费；② 连接保持，删除某个分区队列 → 消费者被取消 → 自动重声明并恢复消费，发到该分区的事件最终被消费一次；③ 连接保持，删除发布方交换机 → 下一次发布触发重声明，按策略处理当次发布，再次发布成功。
4. 文档：README 连接恢复一节补"重试与队列被删"；design/08 补注记。
5. 验证：`cd paytech && ./mvnw -B verify -pl foundation/paytech-messaging -am`，再五个消费方模块 `verify`（acquiring、card、client-master、ledger、account 的 -test）。环境变量与密码规则同任务卡 01；用 `verify` 不用 `test`；一次一条 Maven；不操作容器。
6. 禁止：不改业务服务代码与事件目录；不调用 Superpowers skill；不提交到 main；不推送。提交前缀 `foundation(messaging): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。报告 `tasks/02-report.md`。最终回复两行：commit 哈希 + 测试计数。
