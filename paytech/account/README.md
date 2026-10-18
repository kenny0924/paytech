# account

账户服务（V2，第 07 步由 `finance-service` 拆出，第 11b 步分库）：只消费 billing 的经济事实维护 Owner 余额桶，是可用额度的实时权威
（[design/04](../../requirements/20261017/architecture-V2骨架与模块搬迁/design/04-finance-account-ledger-split.md) §3.2、§5–§7）。

- spring.application.name：`account-service`；端口 18114，管理端口 19114。
- 数据库：自有 `mor_account`（账号 `account_app`，只有本库 DML），8 张表：`t_account_balance`、`t_balance_applied_fact`、
  `t_balance_applied_entry`、`t_balance_movement`（仅 Schema）、`t_balance_discrepancy`、`t_consumed_event`、`t_consumed_aggregate_version`、
  `t_compensation_event`。不再连接 `mor_ledger`。
- 物理模型：[account.pdma.json](docs/database/account.pdma.json)；Schema 在 [docs/database/sql](docs/database/sql)；余额语义见 [余额查询接口](docs/balance-query-api.md)。

| 模块 | 职责 |
|---|---|
| paytech-account-api | `BalanceQueryApi`（`/balances/v1/clients`、`/partners`）、`BalanceBookApi`（`/balances/v1/books/{bookId}` 的 consistency / accounts / rebuild）、`BalanceMovedPayload`（仅 Schema）；`OwnerType`、`OwnerRole`、`BalanceBucket`、`StandardAccountCodes` |
| paytech-account-core | `EconomicFactRecordedBalanceHandler`、余额映射规则 `FactBalanceMappingRule`、余额应用 / 查询 / 重建 / 日终核对、ledger 与 billing 出站客户端；包根 `com.paytech.account` |
| paytech-account-app | 启动类、Controller、日终核对 XXL-Job Handler `accountBalanceReconciliation`、DataSource / MyBatis / TypeHandler、paytech-messaging 消费端、paytech-compensation-event、HTTP 客户端、Snowflake |
| paytech-account-test | auto 测试；扮演 billing 的事件发布 Probe、ledger / billing DTO Mock、`init/ddl.sql` |

- 消费：`billing.events` → `account.billing.hash` → `account.billing.p0..p3`（`strictOrder=false`），按 `fact_id + fact_version` 幂等；
  版本 2 事实冲回版本 1 的余额变化后应用（乱序到达时预登记被替代事实）；映射不出合法余额的事实进入 `account.billing.dlq`。
- 账簿身份只经 ledger-api 读取，ledger 不可用时查询与 consistency 返回 503；重建经 billing-api 分页重放已批准事实。

运行：`ACCOUNT_DB_*`、`ACCOUNT_RABBITMQ_*`、`ACCOUNT_LEDGER_SERVICE_URL`、`ACCOUNT_BILLING_SERVICE_URL`、`ACCOUNT_SNOWFLAKE_*`、
`ACCOUNT_COMPENSATION_INSTANCE_ID`；本服务不使用 Redis。已按第 07 步初始化的本机环境执行
`docs/database/sql/changes/2026-10-18-account-database.sql`（或测试 `init/ddl.sql`）建库。
