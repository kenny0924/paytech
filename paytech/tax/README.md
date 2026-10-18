# tax

税务服务（V2，第 07 步承接 `finance-service` 空壳 tax）：税额、财政单据、申报与缴税。本期只有 Schema 归属，没有业务代码。

- spring.application.name：`tax-service`；端口 18116，管理端口 19116。
- 数据库：`mor_tax`（24 张表）；引用 Finance Entity、现金账户、分录等只存 ID。
- 物理模型：[tax.pdma.json](docs/database/tax.pdma.json)，Schema 在 [docs/database/sql](docs/database/sql)。
- 模块：`-api` / `-core` 只有 `package-info`；`-app` 只有启动类、JDBC 健康检查与 Snowflake；`-test` 为应用启动、模块边界与架构测试。
