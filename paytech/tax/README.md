# tax

税务服务（2026-10-29 tax 领域层事实服务，需求 tax-结账算税与事件登记）：结账算税（CalculateTax）、消费 acquiring
OrderPaymentSucceeded 登记正式交易与申报底稿、按需生成申报期间、开具 B2C 简化税票；运营按订单号只读查询。

- spring.application.name：`tax-service`；端口 18116，管理端口 19116；无 context-path，内部接口 `/tax/v1/calculations`、`/tax/v1/orders/{orderNo}`。
- 数据库：`mor_tax`（16 张表：13 张业务表 + `t_compensation_event`、`t_consumed_event`、`t_consumed_aggregate_version`）；跨服务引用只存 ID。
- 物理模型：[tax.pdma.json](docs/database/tax.pdma.json)（脱离 Finance 生成器，直接维护），Schema、初始化数据与变更脚本在 [docs/database/sql](docs/database/sql)。
- MQ：消费 `acquiring.events` 的 OrderPaymentSucceeded（`tax.acquiring.p0..p3`）；本期不发布领域事件。
- 补偿任务：`TAX_DOCUMENT_ISSUE`（[compensation-event-catalog.yaml](compensation-event-catalog.yaml)），经 Platform 内部文件接口上传税票（PRIVATE，reasonCode `TAX_RECEIPT`）。
- 模块：`-api` 内部 HTTP 合同与 Code 枚举；`-core` dal / domain / domainservice / processor / MQ 与补偿 Handler / integration；`-app` 启动类、
  Controller、MyBatis、Platform 出站与错误码状态；`-test` 服务级入口测试（HTTP、MQ、补偿 Handler、纯规则）与模块边界、架构测试。
