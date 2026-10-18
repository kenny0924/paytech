# 第 08 步报告 · 消息拓扑接入：分区队列、发布侧串行、消费者切换新信封

- 分支：`refactor/step-08-messaging-topology`（起点 `refactor-main@c56700bd`）。实现提交 `03adcc92`，本报告单独提交。
- 结论：任务卡第 2 节五项全部完成。第 4 节第 2、3、4 项通过。第 1 项按 team-lead 安排只跑了相关模块：6 个目标模块及其上游一次 `clean verify` 全部通过，全 Reactor 由主会话合入后运行。
- 业务语义未改：两个消费 Handler 仍调用原 Processor，业务幂等键与判定逐字保留；发布 Payload 字段未动。

## 1. 改动摘要

1. **补偿端口**：`paytech-compensation-event` 新增 `api.CompensationExecutionSequencer`，默认 `DIRECT` 在协调线程直接执行，即原并发。
   - Dispatcher 在 Claim 之后经端口执行。端口拒绝时任务按可重试失败释放，错误码 `SEQUENCER_UNAVAILABLE`。
   - 宿主提供一个该类型的 Bean 时自动采用，提供两个则启动失败。
   - 扫描、分片、Claim/Lease、表结构与 Guide 都未改，补偿组件不依赖 messaging，也不引入 AMQP。
2. **messaging**：
   - 新增 `ordering.AggregateKeySequencer`。四段式 `event_key` 取 `aggregateType:aggregateId` 为串行键，其他格式以整个键为串行键。
   - 新增 `MessagingSequencerAutoConfiguration`，开关 `messaging.producer.sequencer.enabled|lanes|queue-capacity`。
   - `AggregateSerialExecutor` 增加 `pendingTaskCount()`。
   - `paytech-compensation-event` 以 optional 依赖引入。
3. **messaging-api**：
   - `@DomainEventHandler` 增加 `transaction()`，取值 `SHARED`（默认）或 `HANDLER_MANAGED`，见第 5 节第 1 项。
   - 新增 `DomainEventRejectedException`：Handler 明确拒绝时，监听器直接把消息送入 DLQ。
4. **payment**：
   - 删除 `PaymentResultRabbitPublisher`、`PaymentMessagingConfiguration` 和 core 端口 `PaymentResultPublisher`。
   - `PaymentSucceededHandler` 只经 `DomainEventPublisher` 发布。Payload 类未改。
   - `application.yml` 与 Nacos 模板设为 `unroutable-policy: FAIL` 并开启 `sequencer.enabled: true`。
5. **compliance**：
   - `ClientReviewResultRabbitPublisher` 改名为 `ClientReviewDecidedEventPublisher`，只发新信封。删除旧 Exchange 声明 `ClientReviewMessagingConfiguration`。
   - 生产配置与测试配置都设为 `FAIL` 并开启串行器。
6. **acquiring**：
   - `PaymentSucceededListener` 改为 `@DomainEventHandler(eventType="PaymentSucceeded", strictOrder=false, transaction=HANDLER_MANAGED)` 的 `DomainEventConsumer`。
   - 转换器改从 `DomainEvent` 读取。业务事件键与载荷 Hash 仍按旧报文口径计算，见第 5 节第 2 项。
   - 删除 `PaymentResultConsumerConfiguration`，即旧 Topic、旧队列、DLX/DLQ 与监听工厂。
   - core 的 AMQP 依赖换成 `paytech-messaging-api`，app 增加 `paytech-messaging`，配置 `messaging.consumer.service: acquiring`、`consumers.payment.partitions: 4`。
7. **client-master**：
   - `ClientReviewResultListener` 同样改造，订阅 compliance。
   - `ClientReviewConfiguration` 删除旧队列、Exchange 与监听工厂 Bean，core 与 app 的依赖调整同 acquiring。
8. **两张消费登记表**：`t_consumed_event` 与 `t_consumed_aggregate_version` 加入 acquiring 与 client-master 的 PDManer 模型、Schema SQL 和测试 DDL。client-master 原先只有第一张表。
   - 两个生成器 `--check` 通过，table-ownership 的共享表增加 `t_consumed_aggregate_version`。
9. **事件目录**：`PaymentSucceeded` 与 `ClientReviewDecided` 的 aggregateType、versionField、strictOrder 已与实现核对，无需修改，只更新注释。`PaymentSucceeded` 的 card 消费方登记已在，由第 12 步接入。
10. **compose**：`test-environment/compose.applications.yaml` 只加环境变量，服务拆分不变。
    - risk-service：`MESSAGING_PRODUCER_SERVICE=compliance`、`..._SEQUENCER_ENABLED=true`、`MESSAGING_PUBLISHER_UNROUTABLEPOLICY=FAIL`。
    - commerce-service：`MESSAGING_CONSUMER_SERVICE=acquiring`、`MESSAGING_CONSUMERS_PAYMENT_PARTITIONS=4`。
    - client-contract-service：`MESSAGING_CONSUMER_SERVICE=client-master`、`MESSAGING_CONSUMERS_COMPLIANCE_PARTITIONS=4`。
11. **测试**：

    | 模块 | 改动 |
    |---|---|
    | compensation-event | +2 例：经编排器执行、拒绝后可重试 |
    | messaging | +8 例：自管事务、拒绝直入 DLQ、Handler 自管失败、串行器 3 例、发布侧串行与 FAIL 补偿重试的集成测试 2 例 |
    | payment | 去掉旧 Topic 断言；+1 例核对 FAIL 与串行器装配 |
    | acquiring | 测试改为经真实 `RabbitDomainEventPublisher` 以 payment 身份发布到 `payment.events`；+2 例（重放跳过、旧版本跳过）；各例断言 `t_consumed_event` 行数 |
    | client-master | 测试改造同 acquiring；+1 例重放跳过 |
    | compliance | 只断言新信封；原旧报文 Checker 的 Payload 断言移入信封 Checker |

## 2. 第 4 节验证（实际命令与结果）

所有命令前执行 `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`。从主工作区 `.env.local` 导出 `MOR_RABBITMQ_USER/PASSWORD/VHOST` 与 `PAYTECH_REDIS_PASSWORD`，未打印。没有 up / down / restart 任何容器。

### 2.1 Maven（第 1 项：按安排只跑相关模块）

```bash
cd paytech && ./mvnw -B clean verify -pl payment/paytech-payment-test,acquiring/paytech-acquiring-test,client-master/paytech-client-master-test,compliance/paytech-compliance-test,foundation/paytech-messaging,foundation/paytech-compensation-event -am -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment
```

结果为 BUILD SUCCESS，用时 5:48，全部模块 0 失败、0 错误、0 跳过。

| 模块 | 本步 | 上一步报告 | 差 |
|---|---:|---:|---|
| paytech-compensation-event | 33 | 31（第 01 步） | +2 |
| paytech-messaging | 63 | 55（第 05、06 步） | +8 |
| paytech-messaging-api | 37 | 37 | 0 |
| paytech-payment-test | 73 | 72 | +1 |
| paytech-acquiring-test | 803 | 801 | +2（Jacoco 覆盖率门禁通过） |
| paytech-client-master-test | 58 | 57 | +1 |
| paytech-compliance-test | 374 | 374 | 0 |
| paytech-arch-rules | 57 | 57 | 0 |
| 上游 Foundation 其余模块与 vault-client | 与第 01 步相同 | — | 0 |

- 四个消费方与发布方的测试数都不少于上一步报告。
- 全 Reactor `./mvnw -B verify` 未在本步运行，由主会话合入后执行。
- 第一次单独运行 messaging 时，`PartitionedConsumeAutoTest` 首例耗时约 16 分钟，期间没有任何日志输出。之后的 clean verify 中同一类耗时 9.0 s。原因未查明，可能是主机休眠；这次运行仍然通过，没有失败。

### 2.2 Broker（第 2 项）

测试后在共享 vhost `/mor-local` 上以只读方式执行 `rabbitmqctl list_exchanges / list_queues / list_bindings`，摘要如下：

```text
payment.events                 fanout             durable  → 绑定 acquiring.payment.hash（exchange）
compliance.events              fanout             durable  → 绑定 client-master.compliance.hash（exchange）
acquiring.payment.hash         x-consistent-hash  durable  → p0..p3（权重 1）
client-master.compliance.hash  x-consistent-hash  durable  → p0..p3（权重 1）
acquiring.payment.p0..p3       quorum  x-single-active-consumer=true  x-delivery-limit=5  DLX acquiring.payment.dlx → acquiring.payment.dlq
client-master.compliance.p0..p3 quorum x-single-active-consumer=true  x-delivery-limit=5  DLX client-master.compliance.dlx → client-master.compliance.dlq
以 mt 开头的交换机：0；以 mt、payment_test_ 或 compliance_test_ 开头的队列：0
```

Broker 上仍有旧拓扑，代码已不再声明它们，删除属部署动作，见第 4 节：

| 名称 | 类型 | 消息数 | 消费者 |
|---|---|---:|---:|
| `cg_commerce_payment_result` | 队列 | 159 | 0 |
| `cg_acquiring_payment_result` | 队列 | 31 | 0 |
| `cg_client_master_client_review` | 队列 | 9 | 0 |
| `cg_cct_client_review` | 队列 | 0 | 1（另一会话的旧 CCT 进程） |
| `cg_cct_client_review_dlq` | 队列 | 108 | 0 |
| 各自的 `*_dlq` | 队列 | — | — |
| `topic_payment_result`、`topic_risk_client_review`、`dlx_*` 四个 | 交换机 | — | — |

### 2.3 旧名 grep（第 3 项）

```bash
grep -rn --exclude-dir=target -e topic_payment_result -e cg_commerce_payment_result -e cg_cct_client_review paytech/
```

结果只剩文档与注释，没有代码引用：

- `PaymentSucceededPayload` 的类注释。按 team-lead 要求，本步不动该文件，由第 12 步一并修改。
- 测试与发布类中的"旧 Topic 第 08 步停发"说明，以及事件目录注释。
- `cg_acquiring_payment_result`、`cg_client_master_client_review`、`topic_risk_client_review` 同样只出现在文档与注释中。

### 2.4 R12 与其他检查（第 4 项）

- **R12 零违规**：acquiring 与 client-master 的 `ArchitectureAutoTest`（R1–R12）均通过，两个 Handler 位于 `-core`，事件目录的 consumers 包含本服务。messaging 启动时的 `DomainEventHandlerRegistry` 核对也通过，否则应用上下文无法启动。
- `node tools/arch-rules/scripts/generate-table-ownership.mjs --check`：输出 `current (265 physical tables + 56 retained)`。
- `node scripts/verify-java-foundation.mjs`：通过。
- PDManer：`generate-acquiring-model.mjs --check` 为 up to date（16 表），`generate-client-master-model.mjs --check` 为 up to date。
- PDManer 测试（acquiring / client-contract / consumer / client-compensation / canonical-paths）：122 项，120 通过，2 失败。这 2 项是 client-contract 场景文档测试"现实场景 Manifest 覆盖当前全部物理表…"与"现实场景枚举表与当前闭合枚举目录完全一致"，改动前即失败，输出相同。
- `cd test-environment/scripts && python3 -m unittest test_deploy_script`：OK。

### 2.5 本机测试库（root，运行中的 `mor-local-mysql-1`）

- 执行前用 `information_schema` 查询：`mor_acquiring` 两张表都没有，`mor_client_master` 只有 `t_consumed_event`。
- 2026-10-18T01:21:09Z 执行：

  ```bash
  docker exec -i -e MYSQL_PWD mor-local-mysql-1 mysql -uroot --default-character-set=utf8mb4 < <脚本>
  ```

  - 密码取自 `.env.local` 的 `MOR_MYSQL_ROOT_PASSWORD`，未出现在命令文本与输出中。
  - 脚本内容为 `USE` 加 CREATE TABLE，逐字取自本步的两份测试 DDL：
    - `mor_acquiring`：`t_consumed_event`、`t_consumed_aggregate_version`。
    - `mor_client_master`：`t_consumed_aggregate_version`。
  - 脚本存于 scratchpad：`/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/d1bc4b55-586b-45ae-abbc-d503d0fde214/scratchpad/step08/`。
- 执行后复查，两库各有这两张表。只执行了 CREATE，没有业务库 SQL，也没有 DROP 或 ALTER。

## 3. 测试设计说明

- **"同 Attempt 两条事件按版本顺序发布"不在 payment-test 中做。**
  - 原因：Payment 的补偿事件身份是 `(event_key=尝试 ID, PAYMENT_SUCCEEDED, "-")`，一个尝试只能有一条 PaymentSucceeded 任务。Handler 又按当前版本重建事件，经真实入口无法构造同一尝试的两个版本。
  - 替代：该场景在 `paytech-messaging` 的 `SequencedPublishAutoTest` 中用真实 Dispatcher、`AggregateKeySequencer`、发布端与 Broker 覆盖。同聚合 v1 放慢 800 ms 后，v2 仍排在 v1 之后到达，另一聚合不被阻塞。
  - payment-test 只核对装配：FAIL 策略、串行器 Bean 被 Dispatcher 采用、尝试 ID 键的串行归并。
- **"订阅者不存在时按 FAIL 抛错由补偿重试"同样在 `SequencedPublishAutoTest` 中覆盖。**
  - 原因：共享 Broker 上 `payment.events` 与 `compliance.events` 已有 acquiring 与 client-master 的持久绑定，服务测试无法稳定构造"无订阅方"。
  - 过程：无绑定时补偿任务转为 `ERROR` / `TEST_UNPUBLISHED`；绑定观察队列后经扫描重试成功，任务被删除。
  - "订阅者存在时发布成功"由 payment 与 compliance 现有的发布测试在 FAIL 下通过来证明。
- **acquiring 的清理**：测试库可能被其他会话共享，所以 acquiring 只删除本例发布过的尝试对应的消费登记行。client-master 沿用全库清理器，表集合增加 `t_consumed_aggregate_version`。

## 4. 部署侧待办（本步不执行）

1. **删除旧队列与交换机。** 先确认旧进程已停，并按需排空或导出队列中的消息，再执行：

   ```bash
   rabbitmqadmin -V /mor-test delete queue name=cg_commerce_payment_result
   rabbitmqadmin -V /mor-test delete queue name=cg_commerce_payment_result_dlq
   rabbitmqadmin -V /mor-test delete queue name=cg_acquiring_payment_result
   rabbitmqadmin -V /mor-test delete queue name=cg_acquiring_payment_result_dlq
   rabbitmqadmin -V /mor-test delete queue name=cg_cct_client_review
   rabbitmqadmin -V /mor-test delete queue name=cg_cct_client_review_dlq
   rabbitmqadmin -V /mor-test delete queue name=cg_client_master_client_review
   rabbitmqadmin -V /mor-test delete queue name=cg_client_master_client_review_dlq
   rabbitmqadmin -V /mor-test delete exchange name=dlx_commerce_payment_result
   rabbitmqadmin -V /mor-test delete exchange name=dlx_acquiring_payment_result
   rabbitmqadmin -V /mor-test delete exchange name=dlx_cct_client_review
   rabbitmqadmin -V /mor-test delete exchange name=dlx_client_master_client_review
   rabbitmqadmin -V /mor-test delete exchange name=topic_payment_result
   rabbitmqadmin -V /mor-test delete exchange name=topic_risk_client_review
   ```

   本机 `/mor-local` 也残留同名拓扑，把 `-V` 换成 `/mor-local` 即可。`cg_cct_client_review` 当前有 1 个消费者，是另一会话运行的旧 CCT 进程。

2. **切换时序。** 旧队列中未消费的消息不会进入新分区队列。
   - 发布方先停发旧 Topic 并完成新信封订阅。
   - 旧消费进程排空旧队列后再下线，或把残留消息人工转换后重放。
   - acquiring 侧的业务键沿用旧口径，同一事件经新旧两条路径各到达一次时，第二次会被识别为重复。
3. **compose。** test-environment 的 messaging 变量已加入，见第 1 节第 10 项。
   - test-environment 的 MySQL 需对 `mor_commerce`（acquiring 目前仍用该库名）与 `mor_cct` 执行两张消费登记表的 CREATE。这两个库名沿用旧服务拆分，第 03、04 步的待办里已有这项。
   - RabbitMQ 一致性哈希插件需要按第 01 步的挂载重建容器后才生效。
4. **Nacos 与部署配置。** payment 与 compliance 若在 Nacos 中覆盖了 `messaging.publisher.unroutable-policy`，需要改为 `FAIL` 并开启 `messaging.producer.sequencer.enabled`；payment 的 Nacos 模板已改。

## 5. 偏差与待确认

1. **`HANDLER_MANAGED` 事务模式，偏离 08 §4 第 4 步的"同事务"写法。**
   - 原因：两个消费方的 Processor 继承 `AbstractProcessor`，它在已有事务时直接抛 `processor不能处于事务中调用`。若放进拦截器事务，就要绕过 Processor 改调 Service，业务路径不再等价。
   - 做法：拦截器先用短事务判定重放、旧版本与缺口（插入后回滚），再在事务外调用 Handler，Handler 成功后另开短事务登记并推进版本。
   - 崩溃窗口：在 Handler 提交后、登记前中断时，消息会重投，由原有业务幂等返回重复结果，这与切换前只靠业务幂等的现状一致。
   - 默认仍为 `SHARED`。请确认这一扩展，以及是否在 08 设计文档中补写。
2. **acquiring 的业务幂等键沿用旧报文口径。**
   - 事件键为 `PAYMENT:PAYMENT_ATTEMPT:{id}:{ver}:PaymentSucceeded`，载荷 Hash 为 payload 字节的 SHA-256（不是信封的 RFC 8785 `payloadHash`）。
   - 目的是与切换前已落地的投影保持一致，使新旧路径重复到达时识别为重复，而不是"载荷不一致"。
   - 投影列 `last_attempt_event_key` 的语义因此仍是旧格式。是否在后续切片迁到信封 `eventId`，需要确认。
3. **业务拒绝改为抛 `DomainEventRejectedException` 并直接进入 DLQ**，与旧监听器的 basicReject 等价，不消耗 5 次重投。
   - 并发冲突与其他运行时异常仍按退避重投，上限 5 次后进入 DLQ。旧实现是立即 requeue，新实现在两次重投之间退避等待（默认 1s × 次数）。
4. **测试 DDL 取自服务 Schema 切片，不是逐字复制 messaging SQL。**
   - 原因：acquiring 的模型契约测试要求测试 DDL 是服务 Schema 的逐字切片。
   - 两者的列、类型、ascii_bin、NOT NULL、主键与索引相同，只有注释文字和表级默认排序规则（`utf8mb4_0900_ai_ci`）不同；所有列都显式指定了字符集，实际行为一致。client-master 同样处理，原先逐字复制的 `t_consumed_event` 也换成了服务 Schema 的版本。
5. **client-master 不配置 `messaging.consumers.compliance.partitions`，取默认 4。**
   - 原因：onboarding 开关关闭时 Handler 不注册，而 messaging 启动校验会拒绝配置了未订阅的发布方。
   - test-environment 开关是开启的，因此用环境变量设为 4。是否把该校验放宽为"未订阅时忽略"，需要确认。
6. **串行器默认值。** lanes 为 4、每线程队列 256；队列中的任务持有 Lease，payment 的 Lease 为组件默认值。
   - 串行器开启后，补偿任务的最大并行聚合数等于 lanes。payment 与 compliance 目前都只有一个补偿 Handler，影响仅限发布任务。取值需要确认。
7. **messaging 以 optional 方式依赖 compensation-event**，实现任务卡要求的 `AggregateKeySequencer implements CompensationExecutionSequencer`。
   - 方向只有 messaging 到补偿组件，补偿组件仍不依赖 messaging。
   - messaging 的测试类路径因此带上补偿组件，`application-mock.yaml` 默认 `compensation.enabled: false`，只有串行测试的独立上下文开启它。
8. **compliance 发布类改名**为 `ClientReviewDecidedEventPublisher`，因为它不再直接使用 RabbitMQ；ComplianceArchitectureTest 未引用旧类名，无需改动。
9. **PDManer**：
   - acquiring 的 `COMMERCE_REAL_WORLD_SCENARIOS.md`（`agent-docs` 下）仍写 15 张表，场景测试已改为自动追加两张 messaging 表。
   - client-master 的两张新表不使用 Snowflake BIGINT 主键，命名规则与审计列检查对它们放行。
   - 这些放行与说明需要数据模型 Owner 确认。
10. **与第 12 步的冲突面**：`PaymentSucceededHandler`（删除旧发布调用与字段）、`PaymentSucceededEnvelopeChecker`（新增 `PAYLOAD_FIELDS` 常量）、`PaymentResultProbe` 与 `events.yaml` 注释都可能与第 12 步重叠，合并时以第 12 步的 Payload v2 为准。另外，acquiring 转换器仍只接受 `schemaVersion=1`，第 12 步升 v2 时需要同时放行 v2。
