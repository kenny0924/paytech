# 任务卡 05 · Payment → payment + channel-connector（卡域暂留 payment 进程与库）

- 分支：`refactor/step-05-payment-channel`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-step05`；起点：`refactor-main@c3f4a92`（00–02d 步与 09、10 已合入）。
- 对应计划：[11-dev-plan.md](../design/11-dev-plan.md) 第 05 步；决定 D06（渠道连接只拥有支付渠道）、D33、D34；S7（表归属已定）。卡域独立（S3 / 05 文档）在第 12 步，本步**不做**读卡票据与两段创建。
- 并行：与 04、07 同时进行。

## 1. 输入文档（先完整读取）

- [design/01-skeleton-and-conventions.md](../design/01-skeleton-and-conventions.md)、[design/02-archunit-rules.md](../design/02-archunit-rules.md)、[design/03-module-and-table-ownership.md](../design/03-module-and-table-ownership.md) 第 1.2 节 payment / channel-connector / card 三行与第 2 节 Payment 31 表。
- [design/05-payment-card-isolation.md](../design/05-payment-card-isolation.md)（只读以了解第 12 步目标，本步不实现）。
- [design/08-messaging-module.md](../design/08-messaging-module.md)（`PaymentSucceeded` 改信封、双写旧 Topic）；[design/09-path-coupling-fixes.md](../design/09-path-coupling-fixes.md) 第 1.1、1.3、1.4（Payment 行）、2.9、2.14、2.16、4.3（payment 生成器与四个测试）；[design/10-test-baseline-and-cleanup.md](../design/10-test-baseline-and-cleanup.md) 第 1 节 Payment 行、2.3、3.1（payment / channel-connector / card 行）、3.2、4.1–4.3、5.1、6。
- [analysis/payment.md](../analysis/payment.md) 全文（第 5 节端口、第 6 节共享代码、第 9 节 10 个热点）。
- 第 00、01、02a 步报告；`paytech/foundation/paytech-messaging*` 的 README 与事件目录 `catalog/events.yaml`。
- 现有 `payment-orchestration-service/`：`AGENTS.md`、`docs/development/processor-conventions.md`、`PaymentChannelClientImpl`、`AbstractChannelExecutionProcessor`、`ExecuteChannelOperationProcessor`、`QueryMidCandidatesProcessor`、`PayActionActor`、`PayOutputs`、`PlatformStandardApiClient`、`PaymentResultRabbitPublisher`、`PaymentSucceededHandler`、`ChannelModuleBoundaryTest`、`application.yml` 的 `payment.channel.capability-definitions`。
- 仓库规范：`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/COMPENSATION_EVENT_GUIDE.md`。

## 2. 模块映射

| 现 | 目标 |
|---|---|
| `paytech-payment-api`（execution、instrument、card 合同全部） | `paytech/payment/paytech-payment-api`（本步不拆 card 合同，第 12 步拆） |
| `paytech-payment-execution` + `paytech-payment-routing` + web 中 Processor / 装配以外的 payment 代码 | `paytech/payment/paytech-payment-core`（routing 作为 core 内的包 `routing`，不再独立模块） |
| `paytech-payment-card` | `paytech/payment/paytech-payment-card`（**保留为 payment 服务内的独立模块**，artifactId 不变语义：卡域代码与卡表仍在 `mor_payment`，第 12 步整体迁出；只允许 `paytech-payment-core` 与 `-app` 依赖它） |
| `paytech-payment-channel` + web 中 `PaymentChannelClientImpl` 的渠道侧 + `PayActionActor` / `PayOutputs` 的渠道结果映射 + `PlatformStandardApiClient` + `ChannelAccountConfigReader` | `paytech/channel-connector/paytech-channel-connector-core`；Controller、启动类、装配 → `paytech-channel-connector-app`；合同 → `paytech-channel-connector-api` |
| `paytech-payment-web` 的 Controller、启动类、配置、`PaymentResultRabbitPublisher`、`PaymentRiskClientImpl` | `paytech-payment-app` |
| `paytech-payment-test` | 按 10 §2.3 拆：`paytech-payment-test`（6 类 48 例改写 + 卡域 2 类 7 例纯 Java 暂留 payment）、`paytech-channel-connector-test`（4 类 41 例） |
| `docs/`、`AGENTS.md`、`README.md`、`api/openapi` | 随 payment；PDManer 拆 `payment.pdma.json`、`channel-connector.pdma.json`（card 表仍在 payment 模型，第 12 步拆） |

删除 `payment-orchestration-service/mvnw*`；搬完后旧根消失。`paytech/pom.xml` 只取消 `payment`、`channel-connector` 两行注释。应用名 `payment-service`（18103 / 19103，现 `payment-orchestration-service`，调用方 Commerce、Platform 的发现名在各自步骤改，本步在报告列出）、`channel-connector-service`（18110 / 19110）；库 `mor_payment`（沿用，含卡表）、`mor_channel_connector`。

## 3. 代码与逻辑改动清单

1. **channel-connector-api**：`ChannelCandidateQueryApi`（`POST /channel-connector/v1/mid-candidates`，入参现 `QueryMidCandidatesProcessor` 的请求：entityNo、currency、amount、requiredActions、captureMode 等；出参候选列表含 `channelId`、`channelAccountId`、`midId`、能力集合、金额上下限）、`ChannelOperationApi`（`POST /channel-connector/v1/operations`：入参冻结三元组 + Intent 的 `entityNo` + 操作 + 金额 + 工具句柄 + 回调所需字段；出参 **已映射** 的 `ChannelOperationResult{outcome: SUCCESS|FAIL|ERROR|NOT_SENT, paymentStatus: SUCCEEDED|PENDING|FAILED|null, redirectUrl, channelTransactionRef, failureCode, rawOutputKeys(调试用、不含敏感值)}`）、`ChannelExecutionConfigApi`（现 `GetChannelExecutionConfigProcessor`，无生产调用方，保留）。**OE 一致性校验**（analysis 热点 10）留在 channel-connector：账号与 MID 的 `entity_no` 必须等于请求的 `entityNo`，不一致返回 NOT_SENT。
2. **渠道结果映射迁入 channel-connector**（D06，热点 5）：`PayOutputs` 对 Platform 输出键（`paymentStatus`、`redirectUrl`、`channelTransactionRef`、`failureCode`）的解释与 `PlatformStandardApiClient` 的"哪些 Problem 代码代表 NOT_SENT"全部搬到 channel-connector-core；payment 的 `PayActionActor` 只消费 `ChannelOperationResult`。能力代码（`ACTION.PAY` 等）只在 channel-connector 一处解析（热点 4、6），`payment.channel.capability-definitions` 配置随之迁移；`RouteServiceImpl` 比较的字面量 `"MANUAL"` 改为 `CaptureMode` 枚举。
3. **payment 的 `PaymentChannelClient` 端口**：生产实现改为 HTTP 客户端（`channel-connector-api`，发现名 `channel-connector-service`），现 `PaymentChannelClientImpl` 的进程内桥接删除；渠道调用仍在事务外、Redis Intent 锁内（现状）。
4. **读卡回调**：`ChannelCardMaterialController`（`/payment-card/v1/channel-card-materials/reveal`）与 `RevealChannelCardMaterialProcessor` 原样留在 payment-app / core（第 12 步改票据）。
5. **`PaymentSucceeded` 改信封并双写**（08 §3、D34）：`PaymentSucceededHandler` 在补偿 Handler 内经 `paytech-messaging` 的 `DomainEventPublisher` 发布 `DomainEvent`（`eventType=PaymentSucceeded`、`schemaVersion=1`、`aggregateType=PAYMENT_ATTEMPT`、`aggregateId=attemptId`、`aggregateVersion=attempt version`、`sourceService=payment`），Payload 字段与现有一致（第 12 步再扩 v2）；**同时继续向旧 `topic_payment_result` 发布现有报文**（Commerce 消费者未切换）；`event_key` 已含聚合键，确认格式满足 `AggregateKey.eventKey`。事件目录 `catalog/events.yaml` 中 `PaymentSucceeded` 的 aggregateType / versionField 按实现核对修正。
6. **装配拆分**（热点 9）：两个 app 各自 DataSource / 事务管理器 / `@MapperScan`（payment 扫 execution + card 的 mapper 包；channel-connector 只扫 channel）/ TypeHandler / Snowflake worker / 补偿配置 / Redis（payment：Intent 锁与 CVV 键；channel-connector：无 Redis 则不引入）；Vault 调用方身份：payment（card）`vault.caller-service=payment-service`，channel-connector `channel-connector-service`，并在报告中列出 Vault 访问规则需要同步的 Caller 名（Vault 侧配置不在本步改，由主会话登记）。
7. **Enforcer**（09 §1.4）：Payment 禁令复制到两个聚合 POM，模块 parent = 服务聚合 POM；payment 聚合 POM 额外禁止除 `paytech-payment-core` / `-app` 之外任何模块依赖 `paytech-payment-card`（R11 过渡）。
8. **路径修复**：09 §2.9（`ChannelModuleBoundaryTest` 改名 `ChannelConnectorArchitectureTest` 并改锚点与模块列举；Channel 表归属规则改为 arch-rules R4）、2.14（`application-mock.yml` 的 `.env.local` 导入改为 `${paytech.local-environment.dir}`）、2.16（`requirements-site/tests/site-content.test.mjs` 两处路径改 `paytech/payment/...`，并运行 `npm --prefix requirements-site test`——worktree 没有 `node_modules` 时先 `npm --prefix requirements-site ci`）、4.3（`generate-payment-model.mjs` 拆两份 + 四个 pdmaner 测试 + `generate-oe-client-schemas.mjs` / `oe-client-model.test.mjs` 的 Payment 条目）。
9. **文档**：`processor-conventions.md` 中不存在的 `PaymentExecutionApi.java` 引用改为 `PaymentIntentApi`（既有漂移，顺手修）；`PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md` 随 payment 并在 Manifest 中按两服务标注表归属（不改表结构）。

## 4. 测试要求（10 §2.3）

- 基线：搬迁前 `cd payment-orchestration-service && ./mvnw -B verify` 实际数（源码核实 96）。
- channel-connector：`QueryMidCandidatesTest`、`GetChannelExecutionConfigTest`、`MidCapabilityResolutionTest`、`ChannelConnectorArchitectureTest` 整类搬入；新增"OE 不一致返回 NOT_SENT"、"Platform 输出映射（SUCCEEDED / PENDING / FAILED / Problem→NOT_SENT）"用例 ≥ 6 例（映射逻辑从 payment 迁来必须有自己的测试）；`ArchitectureAutoTest`。
- payment：6 类 48 例保持入口不变；渠道候选与执行改为 `ChannelConnectorMocker`（channel-connector-api DTO，MockHttpServer 以 `channel-connector-service` 承载）；`PlatformMocker.revealCard` 回调目标仍是 payment（第 12 步改）；`PaymentSucceededPublishTest` 增加"新信封已发布到 `payment.events`"与"旧 Topic 仍收到"两条断言（真实本地 Broker + Probe）；卡域纯 Java 2 类 7 例随 payment；`ArchitectureAutoTest`。
- 清理器：`PaymentDataMocker`（11 表 + 卡 4 表暂含）与 `ChannelDataMocker`（6 表）按 10 §3.1；`ChannelTestMapper` 删去非本库表方法；`init/ddl.sql` 两份按 10 §4.2（payment 的测试库从自建 `mor_payment_channel_test` 改为 local-environment 的 `mor_payment` + 新增 `mor_channel_connector`；`ChannelDataMocker.assertEmpty` 的库名断言随之改）。
- 本地库：新增 `mor_channel_connector` / `channel_connector_app`（init 脚本、`.env.example`、运行中 MySQL 手工建库建账号并应用测试 DDL，命令写进报告）。
- 禁止 `when(...).thenReturn`、`@MockBean`。

## 5. 验证清单

1. 前后测试数；`cd paytech && ./mvnw -B verify -pl payment/paytech-payment-test,channel-connector/paytech-channel-connector-test -am` 全绿；全 Reactor 全绿。
2. 两个 app 以 `mock` Profile 启动 health UP。
3. `npm --prefix requirements-site test` 通过；`node --test scripts/pdmaner/payment-*.test.mjs`（改名后）通过。
4. `git log --follow` 抽查 `paytech-payment-core` 的 `PayActionActor.java` 与 `paytech-channel-connector-core` 的一个 Java 文件；旧根不存在。
5. R4 零违规（卡表仍归 payment 库，白名单按 03 文档 card 行需在 `table-ownership.yaml` 临时标注"第 12 步前位于 mor_payment"，由 00 步脚本参数或 03 文档注记实现，报告说明）。

## 6. 禁止事项

- 不改支付状态机、Intent / Attempt / Action 语义、幂等键、锁；不实现 AUTHORIZE / CAPTURE / 回调；不做卡域票据与两段创建（第 12 步）。
- 不改 Commerce、Platform、Risk 目录；不执行业务库 SQL（测试库除外，须记录）。
- 不调用 Superpowers skill；不提交到 `refactor-main` / `main`；不推送。

## 7. 交付物

1. 短分支提交，前缀 `refactor(step-05): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
2. 报告 `tasks/05-payment-channel-connector-report.md`：改动摘要 ≤ 30 行、第 5 节逐项、channel-connector-api 合同清单、调用方需改的发现名清单（Commerce / Platform → `payment-service`）、Vault Caller 名变更清单、与设计偏差、待确认项。
3. 最终回复两行。
