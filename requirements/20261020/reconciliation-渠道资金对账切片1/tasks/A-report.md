# A 底座线交付报告（阶段 4）

> 范围：technique.md §2.9 A 线。未提交、未推送、未部署，未对任何数据库执行写 SQL（只做了只读查询）；未调用 Superpowers skill。只改了 reconciliation、scripts/pdmaner（reconciliation 与 Finance 生成器部分）、arch-rules 表归属，以及本需求目录。

## 1. 已读依据

AGENTS.md；agent-docs/DEVELOPMENT_CONVENTIONS.md（全文）；JAVA_PROJECT_STRUCTURE.md（全文）；examples/WRITE_API_EXAMPLE.md；DATABASE_DESIGN_GUIDE.md；UNIT_TEST_GUIDE.md 与 examples/UNIT_TEST_EXAMPLE.md；COMPENSATION_EVENT_GUIDE.md；TECHNOLOGY_STACK.md §6.7–6.9。本需求 requirement.md、technique.md（§1、§2 全部，含 §2.10）、db/reconciliation/ddl.sql、tasks/T0-report.md。记忆：maven-jdk25、tests-use-enums、processor-template-no-javadoc、test-restclient-503、background-agent 与本地共享测试。参照源码：billing（模型与 billing-model.test.mjs）、account（消费 / 发布 / 补偿 / 测试探针）、channel-connector（Vault、锁、XXL、Nacos、测试 Mock）、ledger 与 acquiring（`@ImportHttpServices`）、paytech-messaging（消费幂等）。

## 2. 必须先处理：库为 15 张表，不是 13 张

paytech-messaging 的消费端（`IdempotentConsumeInterceptor`）要求宿主库有 `t_consumed_event` 与 `t_consumed_aggregate_version`；technique §2.4 要求照 account 消费 `BillingFactRecorded`、按 eventId 去重，所以这两张表是必需的，原 13 张漏算了（billing 的 11 张同样含这两张）。

- 已按 ddl.sql 头注释"不一致以模型为准并回写本文件"处理：模型、Schema、测试 DDL 与 **db/reconciliation/ddl.sql** 都加了这两张表（定义与 billing 逐字相同，放在 `t_compensation_event` 之前），ddl.sql 头部加了一行说明。12 张业务表 + 3 张基础设施表 = 15 张。
- **需要主会话在本机 `mor_reconciliation` 补执行两条 CREATE TABLE**（ddl.sql 中 `t_consumed_event`、`t_consumed_aggregate_version` 两段）。补建前，所有连库测试会在 BeforeEach 失败，提示"表清单与测试 DDL 不一致"。mor-test 重建时用新的 ddl.sql 即可。
- AC-01、requirement §3.1、technique §1.1 / §2.2 中的"13 张"需要主会话改成"12 张业务表 + 补偿 + 消费幂等两表（15 张）"。

## 3. 改动清单

**模型与生成器**
- `paytech/reconciliation/docs/database/reconciliation.pdma.json`：重写为 15 张表，直接维护 JSON（结构照 billing.pdma.json，模板 profile / 数据域 / 命名规则取自 billing，无连接）；11 条库内关系、4 个图组（交易层、银行层、异常与处置、可靠执行与消费幂等）；字段中文显示名逐列填写。
- `docs/database/sql/reconciliation-schema.sql`、`paytech-reconciliation-test/src/test/resources/init/ddl.sql`：由模型渲染，CREATE 语句与 db/reconciliation/ddl.sql 逐字一致（测试新增断言三者一致）。
- `scripts/pdmaner/reconciliation-model.test.mjs`（新）：表序、Schema 渲染与三份 DDL 一致、关键列、34 个索引、关系、图组、标识唯一。
- Finance 生成器摘除 reconciliation：`finance/service-split.mjs`（`FINANCE_SERVICES` = tax、payout；05 与 02 的成本确认 / 分配不再投影；表数清单去掉 reconciliation）、`finance/generate-finance-model.mjs`（标题与注释）、`finance-model.test.mjs`（投影表数再减 17，并断言 reconciliation 表不再投影）、`pdmaner-canonical-paths.test.mjs`（reconciliation 生成器改为 null）。
- `generate-oe-client-schemas.mjs` 的 reconciliation 目标**保留**：它只用 `renderSchema` 渲染与核对 Schema，新模型可直接渲染，`--check` 与 oe-client-model.test.mjs 都通过。

**表归属**
- `paytech/tools/arch-rules/scripts/generate-table-ownership.mjs`：reconciliation 旧 13 表记为 step-removed，新 8 张业务表 + 消费幂等两表记为 step-added（结果 15 张）。另补登记 `t_channel_config_change`（channel-connector）：5f12cf3e 是手改 YAML，生成器没跟上，导致 `--check` 不一致。
- `table-ownership.yaml` 由生成器重写；`TableOwnershipAutoTest` 总数改为 `... - 13 + 10`，新增 `test_reconciliationOwnership`。

**持久化（core）**
- 删除旧 `ChannelAccount` DataObject / Mapper / XML / Repository 与 `ReferenceDataStatus`。
- 12 张业务表各一套：`core/dal/dataobject/*`（无 DO 后缀，闭合枚举列直接用 api 枚举，字段 JavaDoc 来自模型）、`core/dal/mapper/*Mapper` + `resources/mapper/reconciliation/*Mapper.xml`、`domainservice/repository/*Repository` + `impl/*RepositoryImpl`。每表都有：插入（唯一键冲突转 409 `ConcurrentRequestException`）、按主键 / 业务键读取、API 列表所需的分页与计数，以及按 `version`（必要时加原状态）的条件更新——Mapper 返回行数，Repository 行数不为 1 抛 409，成功后才同步传入对象的字段、版本与更新时间。t_recon_exception 的 `changeStatus` 自动维护 `active_slot`（OPEN / UNDER_REVIEW 为 1，结案为 NULL）。
- `core/configuration/ReconciliationEventProperties`（`reconciliation.event.source-service / instance-id`，四类事件信封身份）。
- app `ReconciliationPersistenceConfiguration`：29 个持久化枚举逐一注册 `CodeEnumTypeHandler`。

**构建与运行配置**
- 父 pom 放开 AMQP、RabbitMQ、XXL、compensation-event、Nacos 的禁用；core 加 billing-api、channel-connector-api、vault-client、compensation-event、messaging-api、redis-starter、snowflake 等；app 加 messaging、amqp、compensation-event、xxl-job-starter、redis-starter、http-client-support、restclient、loadbalancer、nacos discovery / config；test 加 messaging test-jar。
- app `ChannelConnectorClientConfiguration`：`@ImportHttpServices(group = "channel-connector-service", types = ChannelMidApi.class)` + `HttpServiceClientFactory.configureGroup`，基址 `lb://channel-connector-service`。Vault 走 vault-client 自动配置（`vault.client.*`，caller `reconciliation-service`，服务发现 `vault-service`）。
- `application.yml`：Nacos 发现（nacos Profile 时导入配置）、RabbitMQ（发布确认）、Redis + `redis.coordination.enabled`、`messaging.consumer/producer.service: reconciliation`、`compensation.*`、`paytech.instance-id`、`vault.client.*`、`web.logging.metadata-only-path-prefixes: /reconciliation/v1/cash-accounts`（I-05）。新建 `paytech/reconciliation/config/nacos/reconciliation-service.yaml`。
- `messaging.consumers.billing.partitions: 4` **暂时注释**：paytech-messaging 启动时要求每个配置的发布方都有本服务 Handler 订阅，Handler 未装配时会使启动失败。B 线加 Handler 时在 application.yml、nacos 模板、application-mock.yml 三处同时启用。
- `ReconciliationModuleBoundaryTest` 依赖白名单与运行时类检查同步更新（Nacos、RabbitTemplate、Redisson、XXL 必须存在，Mockito、Flyway、命令幂等组件必须不存在）。补偿目录用 T0 的 `compensation-event-catalog.yaml`，未改。

**合同调整（决定 I-02）**
- `ChannelBatchReconciledPayload`：删 `platformTxnIds`，加 `Integer platformTxnCount`（注释写明按 `GET /platform-txns?statementId=` 取明细）。
- `PlatformTxnApi.listPlatformTxns` 增加可选 `statementId` 查询参数（I-02 引用了它，T0 合同里没有）。**B 线实现 Controller 时需要支持它。**

**测试底座（test 模块，无 Mockito）**
- `configuration/ReconciliationTestConfiguration`：测试 Mapper 扫描 + `@EnableMockHttpServer`（扫描 test 下 mock、mocker、probe、clear 包）。
- 外部 DTO Mock：`mock/external/MockChannelMidController`（实现 `ChannelMidApi`，写命令记为意外调用）+ `mocker/external/ChannelConnectorMocker`（按 MID 配置列表摘要与详情，列表按 entityNo / status / channelId / 外部 MID 前缀过滤分页，详情或列表可注入 HTTP 故障）；`MockVaultController` + `VaultMocker`（按数据类型 + 用途预设 Token，同幂等键重放返回同一 Token，只记原值 SHA-256；Reveal 一律意外）。
- MQ：`probe/BillingEventPublisher`（扮演 billing 发 `BillingFactRecorded` 到 billing.events，引用带 externalReferenceType / externalTransactionId；分区队列不存在时 purge 跳过）；`probe/ReconciliationEventProbe`（每例临时队列绑定 reconciliation.events，`await(n)`、`assertNone()`、`payload(event, XxxPayload.class)`）。
- 本地数据：`mocker/local/ReconciliationDataMocker`（用生产 Mapper 插入并立即提交）；Builder：`ClearingAccountBuilder`（含 `mid(...)`）、`CashAccountBuilder`、`PlatformTxnBuilder.payment/refund`、`ChannelStatementBuilder.matching/line`、`BankStatementBuilder.imported/line`、`ExpectedBankMovementBuilder.channelSettlement`、`ReconExceptionBuilder.onChannelLine`、`ChannelMidBuilder`（DTO）、`BillingFactBuilder.payment/refund/of`（`channelCost`、`withoutRef`、`supersedes` 负例）；`support/ReconciliationTestConstants`（AE001、USD、NI、PAY / REFUND、Maker / Checker、测试主键序列）。
- `clear/ReconciliationDataCleaner`（先核对 15 张表齐全，缺表给出建表提示；再断言全空；AfterEach 按子表到父表删除）、`clear/RedisNamespaceCleaner`（前缀 reconciliation-mock）、`dal/ReconciliationTestMapper`。
- `base/MockTestBase`：BeforeEach 清分区队列、核对库与两个 Mock 空基线、绑定观察队列；AfterEach 等 Mock HTTP 空闲、分区队列消费完、补偿表清空（超时标记环境不可复用），检查意外调用，再由 TestBase 执行全部 Clear。提供 `consumed(fact)`、`postAsMaker / postAsChecker`（带 Gateway 操作人头）、`post / get`、`management`。
- 自检用例：`auto/repository/PersistenceMappingTest`（12 张表的 SQL、枚举 / 时间映射、唯一键冲突与条件更新）、`auto/integration/OutboundClientWiringTest`（ChannelMidApi 列表 / 详情 / 503、Vault Protect）、`auto/integration/MessagingWiringTest`（生产 DomainEventPublisher → 观察队列，载荷含 platformTxnCount）。

## 4. B / C 线怎样在此之上开发

- **写入口**：core `service/processor/XxxProcessor extends AbstractLockProcessor<XxxRequest, XxxResponse>`，`lockCommand` 返回 `LockCommand.tryLock(...)`，Key 按 D-11 锁实际资源（如 `channel-statement:{id}`、`bank-statement-line:{id}`、`recon-exception:{id}`、`clearing-account:{id}`；确认匹配先流水后预期、ID 升序，用 `tryLocks`）；无锁入口用 `AbstractProcessor`。Processor 只查 Repository，写入交给 `domainservice/service/XxxService` + `impl`（`TransactionTemplate`）。
- **出站**：core 新建 `integration/ChannelMidClient` + `impl/ChannelMidClientImpl`，注入 app 已注册的 `ChannelMidApi` Bean，在事务前调用；Vault 直接注入 `VaultClient`（数据类型 `CASH_ACCOUNT_NUMBER_V1`、`TokenMode.REFERENCE`、用途 `RECON_CASH_ACCOUNT`）。注意：**MID 详情没有 channelCode，只有 channelId**；核对渠道要用列表摘要（按 entityNo + status 过滤）或另查渠道。
- **补偿 Handler**：core `service/handler/XxxHandler implements CompensationEventHandler`，`EVENT_TYPE` 用目录里的字符串常量（R12 从字节码读取），纯静态 `buildEvent(...)`，事件键 `AggregateKey.of(Payload.AGGREGATE_TYPE, id).eventKey(Payload.EVENT_TYPE, Payload.AGGREGATE_VERSION)`；业务事务内用 `CompensationEventRegistrar.register(XxxHandler.buildEvent(...))` 登记；发布型 Handler 用 `DomainEventPublisher` + `ReconciliationEventProperties` 组信封（参照 account `AccountPostedHandler`）。六个类型已在 `compensation-event-catalog.yaml`。
- **MQ 消费**：core `ext/mq/BillingFactRecordedPlatformTxnHandler`，`@DomainEventHandler(eventType = "BillingFactRecorded", strictOrder = false)`（参照 account `BillingFactRecordedBalanceHandler`），然后启用三处 `messaging.consumers.billing.partitions: 4`。
- **Job**：app `ext/job/ChannelLineWaitExpiryJobHandler`（`@XxlJob("channelLineWaitExpiry")`）委托 core Processor；测试直接调用 Handler Bean。
- **Controller**：app `ext/rest/*Controller implements XxxApi`，转换放 `ext/rest/converter`。
- **Repository**：现有方法不够时在对应 Mapper / XML / Repository 上扩展，并在 `PersistenceMappingTest` 补一例；条件更新沿用"Mapper 返回行数、Repository 校验后同步对象"。
- **测试**：继承 `MockTestBase`；前置数据 `data.clearingAccount(ClearingAccountBuilder.template().build())` 等；外部 `channelConnector.mockMid(mid.summary(), mid.detail())`、`vault.expectProtect(...)`（Token 须 43 位 Base64URL）；消费 `consumed(BillingFactBuilder.payment(caId, "TXN-1", "100.00").channelCost("0.45").build())`；事件 `events.await(n)` / `events.assertNone()`；运营命令 `postAsMaker` / `postAsChecker`。

## 5. 验证（JDK 25，`paytech/` 下 `./mvnw -B -o`）

| 命令 | 结果 |
|---|---|
| `-pl reconciliation/paytech-reconciliation-{api,core,app} -am install -DskipTests`，之后对三个模块 `clean install -DskipTests` | 成功 |
| `-pl reconciliation/paytech-reconciliation-test -am test -Dtest=ArchitectureAutoTest,ReconciliationModuleBoundaryTest` | 5/5 通过（R1–R12 零违规） |
| `-pl reconciliation/paytech-reconciliation-test clean verify`（**临时**把清理组件与 TestMapper 的表清单去掉两张消费幂等表，因本机库还缺这两张；跑完已恢复原文件） | 25/25 通过：PersistenceMappingTest 4、ReconciliationApplicationTest 12、ArchitectureAutoTest 1、OutboundClientWiringTest 3、MessagingWiringTest 1、ModuleBoundary 4；跑完后库、Redis 前缀与临时队列均已清空 |
| 同上但不改清单（当前提交态） | 应用上下文正常启动；首例在 BeforeEach 报"表清单与测试 DDL 不一致…按 init/ddl.sql 手工建表"，后续用例因环境被标记不可复用而失败。**主会话补建两表后应全部通过** |
| `-pl tools/arch-rules test -Dtest=TableOwnershipAutoTest,MapperTableOwnershipRuleAutoTest` | 10/10 通过；另 `install -DskipTests`，使 reconciliation-test 读到新表归属 |
| `node scripts/pdmaner/generate-oe-client-schemas.mjs --check`、`node paytech/tools/arch-rules/scripts/generate-table-ownership.mjs --check` | 通过 |
| `node --test scripts/pdmaner/{reconciliation-model,finance-model,oe-client-model,pdmaner-canonical-paths,billing-model}.test.mjs` | 54 项，52 通过，2 项为既有失败（见下） |

**既有失败（与本线无关，未修）**：finance-model.test.mjs 的 "artifact is deterministic…" 与 "service artifacts are exact per-database projections…"。原因是 cf4a429c 直接改了各服务模型的 `name` / `describe`，生成器没跟上。用 HEAD 版本的 scripts 与模型在临时目录复现：同样两项失败，HEAD 时先报 reconciliation 投影不一致，跳过 reconciliation 后报 tax 不一致；现在 reconciliation 已脱离生成器，只剩 tax（payout 同理）。

**没跑的**：全仓 reactor（共享本机库）；`npm --prefix requirements-site test`（本线没改站点）。

## 6. 偏差与待确认

1. **15 张表**（第 2 节）：需主会话补建本机两表，并修订 AC-01 与 technique 中的表数。
2. **四类事件 `unroutable-policy: FAIL`**：D-08 让四个事件暂无订阅方，生产与 mor-test 上发布会一直未路由，补偿任务重试到上限后转 FAILED 留存，订阅方接入后再人工重开补发；不会丢。如果希望不报警、接受丢弃，可改成 `ACK_AND_WARN`（paytech-messaging README 说这只用于订阅方未接入的过渡期）。请主会话定。测试里有观察队列绑定，不受影响。
3. **`messaging.consumers.billing.partitions` 暂注释**，B 线加 Handler 时启用（第 3 节）。
4. **I-02 带出一处 api 改动**：`PlatformTxnApi.listPlatformTxns` 新增 `statementId`，B 线 Controller 与 BFF（D 线）需同步。
5. Repository 的唯一键冲突统一转 409；AC 要求 400 的业务拒绝（如 MID + 币种重复归属、同一账单号已有未作废版本）由 Processor 先按业务键查询后抛 `BizCheckFailException`，409 只兜底并发。
6. 渠道交易引用类型取 Payment 表注释中的 `PAY`、`REFUND`；payment-api 没有发布对应枚举，测试常量用字面值。
7. Vault 数据类型 `CASH_ACCOUNT_NUMBER_V1` 与 reconciliation 的 PROTECT 规则（本机 policy、mor-test policy、mor_vault DML）不在本线，属 D 线。
8. 生成的 Java 按 120 列做了参数换行；生成脚本只在 scratchpad，没有提交到仓库（模型以后直接维护 JSON，由模型测试守住）。
9. 构建时 `-am install` 把当前工作区的 foundation、billing-api、channel-connector-api、vault-client 与 arch-rules 装进了 `~/.m3`；内容就是工作区源码。
