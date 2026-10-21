# reconciliation

对账服务：只认定渠道资金事实，记账经 billing。交易层回答「渠道账单每一行对应平台哪一笔预期应收或应付」，银行层回答「应到的钱是否真的进了平台银行账户」，结论以事件交给 billing 生成事实，再由 account、ledger 入账。**本服务不写分录、不改余额。**

- 依据：[2026-10-20 对账决定](../../agent-docs/decisions/2026-10-20-reconciliation-facts-via-billing.md)、[设计稿](../../design-discuss/reconciliation/design.md)、[切片 1 需求与技术方案](../../requirements/20261020/reconciliation-渠道资金对账切片1/requirement.md)。
- 实现、验证与部署状态只见台账 [CURRENT_IMPLEMENTATION.md](../../agent-docs/CURRENT_IMPLEMENTATION.md)，本文不记录。
- spring.application.name：`reconciliation-service`；端口 18115，管理端口 19115。

## 边界

| 拥有 | 不拥有（Owner） |
|---|---|
| 清算账户及其 MID、平台流水、渠道账单与账单行、交易匹配与实际渠道费、平台银行账户与用途、银行对账单与流水、预期银行流水、银行匹配、对账异常与处置 | 渠道 / 技术账号 / MID 主数据（channel-connector）、金额计算与事实（billing）、分录与余额（ledger / account）、银行账号明文（Vault）、出款与缴税是否完成（payout / tax） |

- 平台流水只来自 billing 事实（`BillingFactRecorded`），不消费 Payment 事件、不回查 Payment。
- 看不准的场景不写专门逻辑：开异常，由 Maker 提交、Checker 复核（不得同人）三种处置之一：`RE_MATCH`、`ACCEPT_TIMING_DIFFERENCE`、`CLASSIFY`（带归类码）；批准时在同一本地事务内应用。
- 事务内只写本库；调 channel-connector、Vault 在事务前；对外事件经本地 `t_compensation_event` 在提交后发布。

## 模块

| 模块 | 职责 |
|---|---|
| `paytech-reconciliation-api` | `/reconciliation/v1` 内部 HTTP 合同（`@HttpExchange`）、请求 / 响应、`enums`（Code 枚举与 `ReconciliationErrorCode`）、`event`（四类事件载荷）、操作人头常量 `ReconciliationHeaders` |
| `paytech-reconciliation-core` | Processor、Domain Service、Repository、Mapper 与 DataObject；`BillingFactRecordedPlatformTxnHandler`（MQ 消费）、补偿 Handler（事件发布、匹配、匹配建议） |
| `paytech-reconciliation-app` | 启动类、Controller 与转换、DataSource / MyBatis、Snowflake、Redis 锁、RabbitMQ、`paytech-compensation-event`、XXL-Job 执行器与 `ChannelLineWaitExpiryJobHandler` |
| `paytech-reconciliation-test` | auto 测试、测试 DDL `src/test/resources/init/ddl.sql`、channel-connector 与 Vault 的 DTO Mock 端点、MQ 测试发布与消费 |

## 数据库

`mor_reconciliation`，15 张表：13 张业务表 + 2 张公共消息消费去重表。

| 表 | 用途 |
|---|---|
| `t_clearing_account` | 清算账户：渠道、经营主体、币种，配置 `unmatched_wait_days`、`cost_tolerance_amount`、`cash_grace_days`、到账银行账户 |
| `t_clearing_account_mid` | 清算账户下的 MID，`uk(mid_id, currency)` |
| `t_platform_txn` | 平台流水：PAYMENT（应收）、REFUND（应付），`uk(billing_fact_id)`，冻结渠道交易引用 |
| `t_channel_statement` | 渠道账单头，`revision` 支持更正重传，状态含 `RECEIVING`、`VOIDED` |
| `t_channel_statement_line` | 账单行：匹配的平台流水、实际费用、`raw_attributes` |
| `t_cash_account` | 平台银行账户：Vault Token 与末四位、用途、复核状态 |
| `t_bank_statement` | 银行对账单：期间、期初 / 期末余额 |
| `t_bank_statement_line` | 银行流水，`uk(cash_account_id, bank_transaction_ref)` |
| `t_expected_bank_movement` | 预期银行流水（切片 1 只有 `CHANNEL_SETTLEMENT`） |
| `t_bank_match` | 银行匹配：建议、确认、拒绝，多对多 |
| `t_recon_exception` | 对账异常 |
| `t_recon_resolution` | 处置（Maker / Checker / 内容 Hash） |
| `t_compensation_event` | 本地补偿事件 |
| `t_consumed_event`、`t_consumed_aggregate_version` | 公共消息库消费去重的机制表（与 billing、account 相同），不是业务表 |

- 物理模型：[reconciliation.pdma.json](docs/database/reconciliation.pdma.json)，直接维护（已脱离 Finance 生成器），门禁 `node --test scripts/pdmaner/reconciliation-model.test.mjs`。
- Schema：[docs/database/sql/reconciliation-schema.sql](docs/database/sql/reconciliation-schema.sql)，与测试 DDL 逐字一致；由开发者人工执行，应用和测试都不建表。

## 入站合同

**内部 HTTP**（前缀 `/reconciliation/v1`，运营写命令带 `X-Gateway-Actor-Subject`、`X-Gateway-Session-Id`；Operations 经 BFF `/finance/reconciliation/v1/**` 转发，Gateway 按权限点 `FINANCE_RECONCILIATION_READ` / `_OPERATE` / `_APPROVE` 拦截，提交人 ≠ 复核人由本服务校验）：

| 资源 | 接口 |
|---|---|
| 清算账户 | `POST /clearing-accounts`；`PUT /{id}/settings`；`POST /{id}/mids`、`DELETE /{id}/mids/{midId}`（挂 MID 前向 channel-connector 核对存在、`ACTIVE`、渠道与经营主体一致、能力组有该币种）；`POST /{id}/status`；`GET /clearing-accounts`、`/{id}`、`/unassigned-mids` |
| 清算账户解析（内部，billing） | `POST /clearing-accounts/resolutions`：MID + 币种 → 清算账户与经营主体；命中不到返回 `CLEARING_ACCOUNT_NOT_RESOLVED` |
| 银行账户 | `POST /cash-accounts`（明文账号先经 Vault Protect，请求日志只记元数据）；`POST /{id}/closure`；`POST /{id}/approval`；`GET /cash-accounts`、`/{id}` |
| 当前银行账户（内部，payout / tax） | `GET /cash-accounts/current?entityType&entityId&currency&purpose`：只返回 ID 与末四位 |
| 渠道账单 | `POST /channel-statements` → `/{id}/lines` → `/{id}/completion`（分段导入）；`POST /{id}/void`；`GET /channel-statements`、`/{id}`、`/{id}/lines`；`GET /platform-txns` |
| 银行对账 | `POST /bank-statements` → `/{id}/lines` → `/{id}/completion`（期初 + 流水 ≠ 期末、起息日不在期间内、交易号与已导入流水或本文件重复时整单拒绝；被拒的可用同一编号重传；接不上上一张开 `BANK_STATEMENT_GAP`）；`GET /bank-statements`、`/{id}/lines`、`/bank-statement-lines`；`GET /expected-bank-movements`；`GET /bank-matches`；`POST /bank-matches`（手工多选匹配）、`/{id}/confirmation`、`/{id}/rejection` |
| 异常与处置 | `POST /recon-exceptions`（人工开异常）；`GET /recon-exceptions`、`/{id}`；`POST /recon-exceptions/{id}/resolutions`；`POST /recon-resolutions/{id}/approval` |
| 汇总 | `GET /summary?entityNo&currency` |

上传文件格式：[标准渠道账单 CSV](docs/formats/channel-statement-csv.md)、[标准银行对账单 CSV](docs/formats/bank-statement-csv.md)（含 [S1 示例](docs/formats/examples)）。浏览器解析后分段发送 JSON，每段 ≤ 100 行、≤ 64 KiB，每张 ≤ 20,000 行；服务不保存原文件。

**MQ：** 消费 `billing.events` 的 `BillingFactRecorded`（`reconciliation.billing.hash` → 分区队列 `reconciliation.billing.p0..p3`）。只处理已批准、无 `supersedesFactId` 的 `PAYMENT`、`REFUND` 事实，按 `billing_fact_id` 幂等，其他类型直接确认；缺清算账户或渠道交易引用、清算账户停用时抛可重试异常，重投后进 `reconciliation.billing.dlq` 留存，补建后由运维重放。

## 出站

| 对象 | 用途 |
|---|---|
| channel-connector `ChannelMidApi`（`lb://channel-connector-service`） | 挂 MID 核对、未归属 MID 列表 |
| Vault（`vault-service`） | 银行账号 Protect：数据类型 `CASH_ACCOUNT_NUMBER_V1`，Token 模式 `REFERENCE`，用途 `RECON_CASH_ACCOUNT` |

**事件**（`reconciliation.events`，`strictOrder: false`，聚合版本固定 1，目前 `consumers: []`，billing 接入后补；无订阅方时 `unroutable-policy: FAIL`，补偿任务保留重试）：

| 事件 | 聚合 | 何时 | billing 侧事实 |
|---|---|---|---|
| `ActualChannelCostConfirmed` | `PLATFORM_TXN` | 账单对平时，平台流水实际费用 ≠ 预估 | `COST_ADJUSTMENT` |
| `ChannelBatchReconciled` | `CHANNEL_STATEMENT` | 账单对平（含已归类调整行、`platformTxnCount`） | `CHANNEL_BATCH_RECONCILED` |
| `ChannelSettlementConfirmed` | `BANK_MATCH` | 渠道预期银行流水的一条匹配确认 | `CHANNEL_SETTLEMENT` |
| `ReconAdjustmentApproved` | `RECON_RESOLUTION` | 平台流水 / 预期银行流水 / 银行流水的 `CLASSIFY` 批准 | `RECON_ADJUSTMENT` |

载荷类在 `paytech-reconciliation-api` 的 `event` 包；事件目录 `foundation/paytech-messaging/catalog/events.yaml`；补偿事件目录 [compensation-event-catalog.yaml](compensation-event-catalog.yaml)（四个发布类 + 本地任务 `CHANNEL_STATEMENT_MATCH`、`BANK_MATCH_SUGGEST`）。

## 定时任务（XXL-Job）

| Handler | 建议周期 | 作用 |
|---|---|---|
| `compensationEventScan` | 每 30 秒，分片广播 | 补偿事件兜底扫描（`paytech-compensation-event`） |
| `channelLineWaitExpiry` | 每 10 分钟 | `UNMATCHED` 且导入时间 + `unmatched_wait_days` 已过的账单行开 `CHANNEL_LINE_UNMATCHED` 异常；已有开放异常跳过 |

## 配置

`application.yml` 与 Nacos 模板 [config/nacos/reconciliation-service.yaml](config/nacos/reconciliation-service.yaml)（`nacos` Profile 导入 `mor-common.yaml` 与本文件）。

| 键 | 说明 |
|---|---|
| `spring.datasource.*` | `mor_reconciliation`，账号 `reconciliation_app`，UTC |
| `spring.rabbitmq.*` | 发布确认 `correlated`、`publisher-returns`、`mandatory` |
| `spring.data.redis.*` | `AbstractLockProcessor` 资源锁（`redis.coordination.enabled: true`） |
| `spring.http.serviceclient.channel-connector-service.*` | `lb://channel-connector-service`，连接 2s、读取 5s，不跟随重定向；客户端工厂 `jdk`（不自动重试） |
| `spring.cloud.nacos.discovery.*` | 注册本服务并发现 channel-connector、Vault |
| `messaging.consumer.service` / `messaging.producer.service` | `reconciliation` |
| `messaging.consumers.billing.partitions` | 4 |
| `messaging.publisher.unroutable-policy` | `FAIL` |
| `compensation.enabled` / `compensation.instance-id` | `true` / 多实例时带实例后缀 |
| `vault.client.caller-service` / `service-id` | `reconciliation-service` / `vault-service` |
| `web.logging.metadata-only-path-prefixes` | `/reconciliation/v1/cash-accounts`（银行账号明文不进日志） |
| `reconciliation.event.source-service` / `instance-id` | 事件信封身份 |
| `reconciliation.identifier.region-id` / `worker-id` | Snowflake 坐标，必填 |

## 本机运行与测试

- 环境变量：`RECONCILIATION_DB_URL` / `_USERNAME` / `_PASSWORD`、`RECONCILIATION_RABBITMQ_HOST` / `_PORT` / `_USERNAME` / `_PASSWORD` / `_VHOST`、`RECONCILIATION_REDIS_HOST` / `_PORT` / `_PASSWORD`、`RECONCILIATION_SNOWFLAKE_REGION_ID` / `_WORKER_ID`、`RECONCILIATION_COMPENSATION_INSTANCE_ID`、`PAYTECH_INSTANCE_ID`、`NACOS_SERVER_ADDR` / `NACOS_NAMESPACE`；XXL-Job 调度中心与执行器参数沿用组件默认，由部署注入。
- 依赖仓库自带的 `local-environment`（MySQL 13306、Redis 16379、RabbitMQ 5672 `/mor-local`），需手工启动；`mor_reconciliation` 结构按 Schema SQL 手工创建。Vault 本机策略见 `local-environment/vault/policy.local.json`。
- 测试：`paytech-reconciliation-test`（JDK 25，`./mvnw -B -o -pl reconciliation/paytech-reconciliation-test -am test`）；测试库变量 `RECONCILIATION_TEST_DB_*`，Redis 前缀 `reconciliation-mock`，RabbitMQ 凭据取自 `local-environment/.env.local`。channel-connector 与 Vault 由测试内的 DTO Mock HTTP 端点提供，不连真实服务。共享本机库时不跑全仓 reactor。
