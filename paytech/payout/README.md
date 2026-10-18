# payout

- spring.application.name：`payout-service`
- 端口：18117
- 数据库：`mor_payout`
- 来源现有工程：`finance-service`

第 07 步只放 Schema 归属：Manual Payout 7 表与 `t_compensation_event` 的 [payout.pdma.json](docs/database/payout.pdma.json) 与
[payout-schema.sql](docs/database/sql/payout-schema.sql)（由 Finance 逻辑模型切分），不建代码模块。
