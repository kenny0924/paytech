# counterparty：consumer 服务改名 技术方案

> 状态：阶段 2–3 合并设计，已实施（2026-10-20）。改名规则的完整表见 [任务卡 01](./tasks/01-rename.md) 第 1 节；本文记录最终落地口径、数据库变更与恢复方式。

## 1. 改名映射（已落地）

| 对象 | 旧 | 新 |
|---|---|---|
| 目录 / Maven | `paytech/consumer`、`paytech-consumer-{api,core,app,test}` | `paytech/counterparty`、`paytech-counterparty-{api,core,app,test}` |
| Java 包 | `com.paytech.consumer` | `com.paytech.counterparty` |
| 服务身份类 | `ConsumerApplication`、`ConsumerCustomerApi`、`ConsumerErrorCode`、`Consumer*Configuration`、`ConsumerTransactionExecutor`、`ConsumerUpstreamProtocolException`、测试基类 | `Counterparty*` |
| 业务概念（不改） | `Customer*`、`consumerReference` / `ConsumerReferencePolicy`、错误码字符串、Client API 路径 | 不变 |
| 已存卡 client | `integration/payment/PaymentStoredCardClient(Impl)` | `integration/card/StoredCardClient(Impl)` |
| 应用名 / 发现名 / 内部前缀 | `consumer-service`、`/consumer/v1` | `counterparty-service`、`/counterparty/v1`（端口 18109 / 19109 不变，不保留别名） |
| 配置 / 环境变量 | `consumer.*`、`MOR_CONSUMER_DB_*`、`CONSUMER_SNOWFLAKE_*`、`CONSUMER_INTERNAL_AUTH_MODE` | `counterparty.*`、`MOR_COUNTERPARTY_DB_*`、`COUNTERPARTY_SNOWFLAKE_*`、`COUNTERPARTY_INTERNAL_AUTH_MODE` |
| 库 / 账号 | `mor_consumer` / `consumer_app` | `mor_counterparty` / `counterparty_app`（表结构不变） |
| PDManer | `consumer.pdma.json`、`consumer-schema.sql`、`consumer-initial-data.sql`、`CONSUMER_PHYSICAL_DECISIONS.md`、`generate-consumer-model.mjs`、`consumer-*.test.mjs` | `counterparty.*` 同名替换；`commerce-model-core.mjs`、`commerce-service-model-contract.mjs`、`pdmaner-canonical-paths.test.mjs`、`oe-client-*` 同步 |
| 调用方 | acquiring `ConsumerClientConfiguration`、`integration/consumer/ConsumerCustomerClient(Impl)`、测试 `MockConsumerApi` / `ConsumerMocker`；card `CardApiConstants.CONSUMER_SERVICE` | `CounterpartyClientConfiguration`、`integration/counterparty/CounterpartyCustomerClient(Impl)`、`MockCounterpartyApi` / `CounterpartyMocker`；`COUNTERPARTY_SERVICE` |
| 工具与环境 | arch-rules 表归属 / 服务目录 / 已知偏差、instance-manifest、`local-environment`、`test-environment`（compose、构建、初始化、prepare、探针测试）、`scripts/verify-local-environment.mjs`、`scripts/moved-links-map.json` | 同步改名；arch-rules 生成脚本以 `renamedServices` / `renamedDatabases` 记录 design 03 原名到新名的映射 |
| 现行验收脚本 | `requirements/20261017/…/verification/local-e2e/e2e_http.py` 私网地址表 `consumer` | `counterparty`（该脚本仍被各会话用于 mor-test 验收；同目录其他历史文件不改） |

不改：acquiring 中表示"按订单的消费者"的 `t_consumer_context`、`ConsumerContext`、`SaveConsumer*`、`ConsumerProtectionPort`；历史需求目录（20261017、20261018）与决定记录原文。

## 2. 数据库变更

表结构不变，只换库名与账号；先建新库复制数据，旧库保留。

| 环境 | 文件 | 执行方式 |
|---|---|---|
| 本机 local-environment | [db/local/](./db/local/) `01-create.sql` → `02-schema-and-copy.sql` → `03-verify.sql`；回滚 `99-rollback.sql` | root；口令键 `MOR_COUNTERPARTY_DB_PASSWORD` 已追加到 `local-environment/.env.local` |
| mor-test | [db/mor-test/](./db/mor-test/) 同结构 | root 经 `docker exec mor-test-mysql-1`；`01-create.sql` 中的口令占位由 `test-environment/.env.test` 新键替换后经标准输入传入；执行时 consumer-service 已停、counterparty-service 未启动 |

恢复：停 counterparty-service → 执行 `99-rollback.sql`（删新库与新账号）→ 用旧镜像重新启动 consumer-service；`mor_consumer` 与 `consumer_app` 未被修改。若新库已产生数据，先导出再评估回灌。

## 3. XXL-Job

新增执行器组 `counterparty-service`（mor-test 为 group 15）与任务 `compensationEventScan`（job 18，CRON `*/30 * * * * ?`，SHARDING_BROADCAST，与原 consumer 任务参数一致）；原 consumer 任务（job 8）停用，执行器组 8 保留以便回退。

## 4. 验证口径

为避免清空其他会话正在使用的本机测试库，没有跑全 Reactor 测试：全 Reactor `test-compile` 通过；受影响模块测试全量运行（counterparty、acquiring、card、arch-rules、instance-manifest），foundation 中与本改动无关、依赖本机环境变量的消息 / Redis 集成测试不在范围内。结果见 [delivery.md](./delivery.md)。
