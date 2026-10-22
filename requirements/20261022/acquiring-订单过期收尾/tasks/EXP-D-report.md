# EXP-D 报告：acquiring 订单过期扫描 XXL 任务与配置

> 执行代理报告，2026-10-22。工作目录 `/Users/kenny/Desktop/Workspace/Code-MoR-expiry`（detached HEAD，基线 `42879401` + EXP-A、B、C 未提交改动）。本卡未提交、未推送、未建分支，未对共享环境执行 SQL，未部署，未改 payment 代码，未登记 XXL 任务。无 DDL。

## 1. 结论

- `orderExpiryScan` Handler、`OrderExpiryProperties` 与 `application.yml` 配置已实现。PENDING 告警阈值已从常量改为配置，经请求逐单传入。
- `ExpireOrderTest` 与 `ClientWebhookOrderExpiredTest` 的入口已改为 Handler。EXP-C 的全部场景保留，另新增 7 例。
- 门禁：acquiring `auto` 837 例全部通过（EXP-C 的 830 例 + 新增 7 例）。全 reactor `test-compile` 通过，118 个模块。
- 有一处需要主会话知悉的行为选择：本轮有订单收尾失败时，Handler 把 XXL 任务标记为失败（沿用 ledger 样板），见第 7 节第 2 项。

## 2. 已读规范与合同

- `agent-docs/DEVELOPMENT_CONVENTIONS.md`（全文，含第 4 节枚举与日志、第 5 节、第 9 节配置）
- `agent-docs/JAVA_PROJECT_STRUCTURE.md`（第 5 节 Web 模块分层、`ext/job` 与 `configuration` 位置、第 5.2 节 Processor）
- `agent-docs/UNIT_TEST_GUIDE.md`（全文）
- `agent-docs/examples/UNIT_TEST_EXAMPLE.md`（组件与两级生命周期部分）
- `agent-docs/examples/WRITE_API_EXAMPLE.md` 第 11 节
- `agent-docs/COMPENSATION_EVENT_GUIDE.md` 第 6 节（XXL 与多实例规则）
- `agent-docs/TECHNOLOGY_STACK.md` 第 6.8 节 `paytech-xxl-job-starter` 与版本表中的 XXL-Job 行
- 任务卡 `tasks/cards.md`（EXP-D 行、1.1 裁决与运行提示、第 2 节门禁）
- `technique.md` 第 2.4 节 D4、3.3.5、3.5、3.6 测试表 `ExpireOrderTest` 行
- `requirement.md` B5、AC-01、AC-09、AC-10
- `tasks/EXP-C-report.md`
- 样板代码：card `CardInstrumentPurgeJobHandler` 与 `CardInstrumentPurgeProperties`；ledger `AccountingRuleVersionActivationJobHandler` 与 `AccountingRuleVersionActivationJobTest`；card `CardInstrumentHttpTest`（日志捕获方式）；`xxl-job-core` 3.4.2 源码中的 `XxlJobHelper` 与 `XxlJobContext`
- acquiring 现状：`paytech-acquiring-app` 的 pom 与 `application.yml`、`CommerceRuntimeConfiguration`（Clock Bean）、测试侧 `MockTestBase`、`AcquiringTestApplication`、`AdjustableUtcClock`、`application-mock.yml`

实施前的说明：入口是 app 模块的 `OrderExpiryJobHandler`，委托 core 的 `ScanExpiredOrdersProcessor`。本卡不新增本地写入，也不新增事务。原子写入与事务外的 Payment 调用仍按 EXP-C 的两段处理。XXL 执行器由 `paytech-compensation-event` 经 `paytech-xxl-job-starter` 传递引入，app 的 pom 未改。

## 3. 改动文件

路径相对 `paytech/acquiring/`。

**生产代码**

| 文件 | 说明 |
|---|---|
| `paytech-acquiring-app/.../configuration/OrderExpiryProperties.java`（新增） | `@ConfigurationProperties("acquiring.order-expiry")`、`@Validated`、`@Component`，样式同 card |
| `paytech-acquiring-app/.../ext/job/OrderExpiryJobHandler.java`（新增） | `@XxlJob("orderExpiryScan")`，提供 `scan()` 与 `runOnce(shardIndex, shardTotal)` |
| `paytech-acquiring-app/src/main/resources/application.yml` | `acquiring.order-expiry` 三项，写默认值并加注释，不引用环境变量 |
| `paytech-acquiring-core/.../processor/order/request/ScanExpiredOrdersRequest.java` | 新增 `@NotNull Duration pendingAlertAfter`，位于 `now` 之后 |
| `paytech-acquiring-core/.../processor/order/request/ExpireOrderRequest.java` | 新增 `@NotNull Duration pendingAlertAfter` |
| `paytech-acquiring-core/.../processor/order/ScanExpiredOrdersProcessor.java` | 逐单构造请求时传入阈值 |
| `paytech-acquiring-core/.../processor/order/ExpireOrderProcessor.java` | 删除 `PENDING_ALERT_AFTER` 常量；阈值经 `terminatePayment`、`applyDecision` 参数传到 PENDING 分支 |

**测试代码**

| 文件 | 说明 |
|---|---|
| `paytech-acquiring-test/.../test/mocker/local/OrderExpiryConfigMocker.java`（新增） | 用例级覆盖批大小、页数与告警阈值；实现 `Clear`，每例结束恢复 `application-mock.yml` 绑定的值 |
| `paytech-acquiring-test/src/test/resources/application-mock.yml` | 补 `acquiring.order-expiry` 三项及说明 |
| `paytech-acquiring-test/.../auto/ext/job/ExpireOrderTest.java` | 入口改为 Handler，新增 7 例 |
| `paytech-acquiring-test/.../auto/ext/job/ClientWebhookOrderExpiredTest.java` | 入口改为 `runOnce(0, 1)`，场景不变 |

## 4. 配置项与默认值

| 键 | 默认值 | 约束 | 含义 |
|---|---|---|---|
| `acquiring.order-expiry.batch-size` | 200 | 1—1000 | 每页读取的到期订单数 |
| `acquiring.order-expiry.max-batches` | 50 | 1—1000 | 单次执行最多页数，剩余订单留待下一轮 |
| `acquiring.order-expiry.pending-alert-after` | `24h` | 非空 | 终止意图写入后仍 PENDING 的告警阈值（B5 / D4） |

Java 类中的默认值与 YAML 相同，所以删掉 YAML 配置也能启动。Handler 每次执行都读取配置对象，没有缓存。

**阈值注入方式选 a：随请求由 Handler 传入。** core 模块因此不依赖 app 的配置类。`ExpireOrderProcessor` 也不持有可变配置。直接调用收尾 Processor 时，阈值必须显式给出，由 `@NotNull` 校验。

## 5. Handler 行为

- **分片来源。** `scan()` 先读 `XxlJobHelper.getShardTotal()`。值大于 0 时，分片取 XXL 上下文的 `shardIndex` 和 `shardTotal`。没有 XXL 上下文时，xxl-job 3.4.2 两项都返回 -1，此时按 `0 / 1` 全量扫描。
- **扫描时刻。** `runOnce` 取进程 `Clock`，截断到微秒，与 acquiring 其他写入保持一致。批大小、页数和阈值取自配置。
- **执行日志。** 每次执行写一行汇总，格式如下：

  ```
  order expiry scan: scanned N, expired N, pending N, already paid N, reversal required N, skipped N, retry N, failed N
  ```

  日志只有条数和异常类型，不含订单号、Intent、Token 或 URL。按订单的 WARN 和 INFO 仍由 EXP-C 的 Processor 记录，只含 orderId 和 intentId。
- **失败标记。** 本轮 `failed > 0` 时，用同一条汇总调用 `handleFail`；否则调用 `log`。扫描抛出的运行时异常，例如分片非法时的 `InvalidParameterException`，统一转为 `handleFail("order expiry scan failed: <异常类名>")`，不向 XXL 抛出。

## 6. 测试命令与结果

在 `paytech/` 下执行。四项变量从主工作区 `local-environment/.env.local` 只读导出，未打印。

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
set -a; eval "$(grep -E '^(MOR_RABBITMQ_USER|MOR_RABBITMQ_PASSWORD|MOR_RABBITMQ_VHOST|MOR_REDIS_PASSWORD)=' \
  /Users/kenny/Desktop/Workspace/Code-MoR/local-environment/.env.local)"; set +a
./mvnw -o -pl acquiring/paytech-acquiring-test -am test -Dtest='com.paytech.acquiring.auto.**' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment
./mvnw -o test-compile
```

| 步骤 | 结果 |
|---|---|
| 两个过期测试类 | `ExpireOrderTest` 24 例、`ClientWebhookOrderExpiredTest` 4 例，全部通过，首轮即通过 |
| acquiring 全部 auto | 837 通过 / 0 失败 / 0 错误 / 0 跳过，BUILD SUCCESS |
| 全 reactor `test-compile` | BUILD SUCCESS，118 个模块 |

**应用启动配置。** acquiring 没有单独的启动类测试。每个 `MockTestBase` 用例都用正式启动类 `AcquiringApplication` 启动，同时加载 app 的 `application.yml` 和 `application-mock.yml`。837 例全部通过，说明新的 YAML 块能解析，`@Validated` 绑定也成功。

**ExpireOrderTest 新增场景**

| 用例 | 覆盖 |
|---|---|
| `test_shardMismatchNotProcessed` | `shardTotal=2` 时，不同余分片扫描 0 条、订单不变、不调用 Payment；所属分片随后收尾（AC-01、S2） |
| `test_pendingBeyondThresholdWarns` | 默认 24 小时阈值：首轮和恰好 24 小时时不记 WARN；超过 1 分钟记 1 条，再过 1 分钟累计 2 条，即每轮一条。订单保持 OPEN，终止意图保留首次时刻，未登记事件（AC-10、D4） |
| `test_pendingAlertThresholdConfigurable` | 阈值配置为 1 小时，61 分钟后告警一次，证明阈值来自配置 |
| `test_xxlEntryUsesShardContext` | 测试侧设置 `XxlJobContext(shardIndex, 2)` 后调用 `scan()`：不命中分片时不处理，命中分片时收尾，`handleCode=200` |
| `test_xxlEntryWithoutContextScansAll` | 无 XXL 上下文时直接调用 `scan()`，按 0 / 1 收尾并提交 Webhook |
| `test_xxlEntryInvalidShardFails` | 上下文为 `2 / 2`：不抛出，`handleCode=500`，消息含 `InvalidParameterException`，订单不变、不调用 Payment |
| `test_xxlEntryOrderFailureMarksJobFailed` | 一单 Payment 400、另一单正常：后者收尾，`handleCode=500`，消息为带条数的汇总 |

**保留的 EXP-C 场景。** 以下场景的入口都改为 `runOnce(0, 1)`，断言不变：未到期（`test_notDueOrPaidSkipped`）、Payment 503 下轮重试（`test_paymentUnavailableRetries`）、重复扫描（AC-09）、先取消后扫描、锁竞争、单单失败不阻断。`test_pagingStopsAtMaxBatches` 改用 `OrderExpiryConfigMocker` 设置每页 1 条：先设最多 1 页，再设 5 页，所以批大小和页数也走配置。只有模拟另一实例重复处理的用例，仍直接调用 `ExpireOrderProcessor`。

**测试手段说明**

- **WARN 捕获。** 用 Spring Boot `OutputCaptureExtension` 读取控制台输出，card 的 `CardInstrumentHttpTest` 已有同样用法。计数时同时匹配 ` WARN `、告警文案和 `orderId=<本单>,`。
- **XXL 上下文。** `XxlJobContext` 在测试线程上设置，执行完立即清除，属于测试侧做法，生产代码没有测试分支。ledger 测试同样直接调用 Handler 方法，在无上下文时 `XxlJobHelper.log` 和 `handleFail` 返回 false，不会抛出。
- **未使用的替身。** 未使用 Mockito Stub、`@MockBean` 或 Spy。

## 7. 偏离与需要知悉的选择

1. **配置类注册方式。** 按卡面指定的 card 样式，`OrderExpiryProperties` 标注了 `@Component`。acquiring 现有的 Properties 是在配置类上用 `@EnableConfigurationProperties` 注册的，但没有合适的宿主配置类，所以没有为此新增一个。
2. **有订单失败时任务标记失败。** technique 3.3.5 只写了“异常 handleFail”。我沿用 ledger 样板：本轮 `failed > 0` 时也调用 `handleFail`，让 Payment 拒绝、数据不一致等情况在 XXL 中可见。PENDING、ALREADY_PAID、REVERSAL_REQUIRED、RETRY、SKIPPED 都不算失败。如果希望只有扫描整体异常才标记失败，改一处即可。
3. **汇总日志不用枚举 `name()`。** 开发规范要求日志不依赖 `name()`，而 `ExpireOrderResponse` 没有 Code。所以汇总里的各结果标签是逐项写出的。
4. **测试中修改生产配置对象。** `OrderExpiryConfigMocker` 直接调用配置对象的 setter，用来验证 Handler 每次执行时读取配置，每例由公共清理恢复。这是测试侧对 Spring 单例状态的控制，方式与 `AdjustableUtcClock` 相同。

## 8. WRITE_API_EXAMPLE 第 11 节自查

| 检查项 | 结果 |
|---|---|
| 合同与校验触发点 | 无 HTTP 入口。配置类由 `@Validated` 在启动绑定时校验。两个内部 Request 的 `@NotNull pendingAlertAfter` 由 `validateBasic` 触发，分片关系由扫描 Processor 的 `validate` 校验，非法分片有测试 |
| 入站适配只做适配 | `OrderExpiryJobHandler` 位于 app 的 `ext/job`，只读取 XXL 分片、配置和时钟，然后委托 `ScanExpiredOrdersProcessor`，不含业务规则或持久化 |
| Processor / Context / Service / Repository | 本卡未改其结构，阈值随请求传入，Context 的 `isPendingBeyond(Duration)` 由调用方给阈值，符合“时间等输入由调用方提供” |
| 事务与外部调用 | 本卡不新增事务；Handler 与扫描 Processor 不开事务 |
| 锁 | 不变，仍为收尾 Processor 的订单 TRY_LOCK |
| 原请求比较、Gateway 幂等、View | 不适用：扫描任务没有命令身份或 HTTP 请求，也没有查询结果模型 |
| 配置集中、无多余抽象 | 三个配置项都有消费者，集中在一个 Properties 类；没有生产 Mock 或测试分支 |
| 实际验证 | 见第 6 节 |

## 9. 未完成项与提示

- 卡内工作已全部完成，未进入 EXP-E。
- 执行器启用需要部署配置。acquiring 生产 `application.yml` 没有 `xxl.job` 块，executor 默认不启用。启用开关 `xxl.job.executor.enabled` 和 admin 地址需由 mor-test 的部署环境提供，阶段 6 请核对 acquiring 容器是否已经设置这些值。本卡按要求没有改动。
- 扫描查询的生产执行计划仍未验证，沿用 EXP-C 报告中的提示。

## 10. 给阶段 6 的 XXL 登记信息

| 项 | 值 |
|---|---|
| 执行器 | `acquiring-service` |
| JobHandler | `orderExpiryScan` |
| Cron | `0 * * * * ?` |
| 路由策略 | 分片广播（SHARDING_BROADCAST） |
| 阻塞处理策略 | 丢弃后续调度（DISCARD_LATER） |
| 任务参数 | 无 |
| 失败判定 | 本轮有订单收尾失败或扫描异常时任务结果为失败，详情看执行日志的汇总行 |
