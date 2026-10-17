# reconciliation

对账服务（V2，第 07 步承接 `finance-service` 空壳 treasury 的对账职责）：Finance 渠道清算账户、渠道结算与银行对账、现金账户三表（D42）。

- spring.application.name：`reconciliation-service`；端口 18115，管理端口 19115。
- 数据库：`mor_reconciliation`（18 张表）；本期只有 `t_channel_account` 的只读代码，其余为 Schema 归属。
- 物理模型：[reconciliation.pdma.json](docs/database/reconciliation.pdma.json)，Schema 在 [docs/database/sql](docs/database/sql)。
- 模块：`-api`（暂无合同）、`-core`（`ChannelAccount` 只读访问）、`-app`（启动类与健康检查）、`-test`（应用启动、模块边界与架构测试）。
- ledger 交易自动记账不再读本库：渠道清算账户主键由 billing 在经济事实头中给出（design/04 §6）。
