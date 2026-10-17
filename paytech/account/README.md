# account

账户服务（V2，第 07 步由 `finance-service` 拆出）：Client 与 Local Partner 分类余额查询（`/balances/v1`）。

- spring.application.name：`account-service`；端口 18114，管理端口 19114。
- 数据库：本步**共连 `mor_ledger` 且只读**（账号 `account_app` 只授 SELECT），只读 `t_account`、`t_chart_account`、`t_ledger_book`，
  在 arch-rules `table-ownership.yaml` 的 `transitionalSharedReads` 登记；`mor_account` 与余额表在第 11b 步新建。
- 物理模型：[account.pdma.json](docs/database/account.pdma.json)（目标库当前只有 `t_compensation_event`）；余额语义见 [余额查询接口](docs/balance-query-api.md)。

| 模块 | 职责 |
|---|---|
| paytech-account-api | `BalanceQueryApi` 与响应；`OwnerType`、`OwnerRole`、`BalanceBucket`、`StandardAccountCodes`；过渡位置的 `AccountIdentityRule`（第 11b 步收回） |
| paytech-account-core | `BalanceQueryServiceImpl` 与只读 `BalanceAccountMapper`；包根 `com.paytech.account` |
| paytech-account-app | 启动类、Controller、DataSource / MyBatis / TypeHandler、Snowflake |
| paytech-account-test | auto 测试；夹具经测试专用的 ledger 账号写共库，`init/ddl.sql` 只建 `account_app` 账号 |

运行：`ACCOUNT_DB_*`、`ACCOUNT_SNOWFLAKE_*`；本服务不使用 Redis。
