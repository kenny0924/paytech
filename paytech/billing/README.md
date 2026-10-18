# billing

计费服务（V2，第 07 步由 `finance-service` 拆出），唯一经济事实来源（D37）与渠道成本配置。

- spring.application.name：`billing-service`；端口 18113，管理端口 19113。
- 数据库：`mor_billing`（9 张表：经济事实三表、渠道成本四表、`t_currency_spec` 只读副本、`t_compensation_event`）。
- 物理模型：[billing.pdma.json](docs/database/billing.pdma.json)，Schema 与初始化 DML 在 [docs/database/sql](docs/database/sql)。

| 模块 | 职责 |
|---|---|
| paytech-billing-api | `/channel-cost-plans/v1` 与 `/billing/v1/economic-facts`（`EconomicFactCommandApi`）合同；`AmountSource`、`EconomicsType`、`EconomicFactStatus`、`FactPartyRole`、`FactLineDirection`、`AutoInstructionType`；经济事实载荷 `EconomicFactPayload` / `EconomicFactLinePayload`（同时是 `EconomicFactRecorded` v1 Payload） |
| paytech-billing-core | 渠道成本方案与估算；经济事实 Maker-Checker 登记（`SubmitEconomicFactProcessor`、`ApproveEconomicFactProcessor`）、登记不变量 `EconomicFactInvariants`、发布 Handler `EconomicFactRecordedHandler`；包根 `com.paytech.billing` |
| paytech-billing-app | 启动类、Controller、DataSource / MyBatis / TypeHandler、Snowflake、Redis 锁（`BillingLocks`）、RabbitMQ 与 `paytech-messaging` 发布端、`paytech-compensation-event` |
| paytech-billing-test | auto 测试、清理器、`init/ddl.sql` |

- 经济事实（第 11a 步，design/04 §2）：Maker `POST /billing/v1/economic-facts` 提交 `MANUAL_ADJUSTMENT` / `COST_ADJUSTMENT`
  为 `DRAFT`，Checker（≠ Maker）`POST .../{factId}/approve` 后 `APPROVED` 并定稿 `fact_version`，替代事实同事务置 `SUPERSEDED`，
  同事务登记补偿事件 `ECONOMIC_FACT_RECORDED`；提交后由 Handler 经 `DomainEventPublisher` 发布 `EconomicFactRecorded` 到 `billing.events`
  并写 `published_at`。`GET .../{factId}`、按账簿主体游标读取 `GET ...?bookEntityType=&bookEntityId=&currency=&fromFactId=&limit=`、
  发布进度 `GET .../pending`。表结构增量见 [changes/2026-10-18-economic-fact-maker-checker.sql](docs/database/sql/changes/2026-10-18-economic-fact-maker-checker.sql)。
- `messaging.publisher.unroutable-policy: ACK_AND_WARN`：第 08 步接入 ledger / account 订阅者后改回 `FAIL`。
- `t_currency_spec` 的 Owner 是 platform-support（D41）；本库副本只读，种子行与 Platform 逐字一致，状态值类型取自 `paytech-platform-support-api`。
- 运行：`BILLING_DB_*`、`BILLING_REDIS_*`、`BILLING_RABBITMQ_*`、`BILLING_SNOWFLAKE_*`、`PAYTECH_INSTANCE_ID`；测试库账号 `billing_app`，Redis 前缀 `billing-mock`，RabbitMQ 凭据取自 `local-environment/.env.local`。
