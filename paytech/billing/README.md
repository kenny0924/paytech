# billing

计费服务（V2，第 07 步由 `finance-service` 拆出），唯一经济事实来源（D37）与渠道成本配置。

- spring.application.name：`billing-service`；端口 18113，管理端口 19113。
- 数据库：`mor_billing`（9 张表：经济事实三表、渠道成本四表、`t_currency_spec` 只读副本、`t_compensation_event`）。
- 物理模型：[billing.pdma.json](docs/database/billing.pdma.json)，Schema 与初始化 DML 在 [docs/database/sql](docs/database/sql)。

| 模块 | 职责 |
|---|---|
| paytech-billing-api | `/channel-cost-plans/v1` 合同；`AmountSource`、`EconomicsType`、`AutoInstructionType`；经济事实载荷 `EconomicFactPayload` / `EconomicFactLinePayload` |
| paytech-billing-core | 渠道成本方案与估算、经济事实只读访问、登记不变量 `EconomicFactInvariants`；包根 `com.paytech.billing` |
| paytech-billing-app | 启动类、Controller、DataSource / MyBatis / TypeHandler、Snowflake、Redis 锁（`BillingLocks`） |
| paytech-billing-test | auto 测试、清理器、`init/ddl.sql` |

- 经济事实三表更名为 `t_economic_fact` / `t_economic_fact_line` / `t_economic_fact_line_link`；`fact_type` 增加 `MANUAL_ADJUSTMENT`，
  新增 `supersedes_fact_id`、`status`。本步只有读代码，登记接口与 `EconomicFactRecorded` 事件在第 11a 步实现。
- `t_currency_spec` 的 Owner 是 platform-support（D41）；本库副本只读，种子行与 Platform 逐字一致，状态值类型取自 `paytech-platform-support-api`。
- 运行：`BILLING_DB_*`、`BILLING_REDIS_*`、`BILLING_SNOWFLAKE_*`；测试库账号 `billing_app`，Redis 前缀 `billing-mock`。
