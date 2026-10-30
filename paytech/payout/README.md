# payout

- spring.application.name：`payout-service`
- 端口：18117（管理端口 19117）
- 数据库：`mor_payout`
- 来源现有工程：`finance-service`

结算出款切片 S1（[requirements/20261030/payout-收款账户与余额解释](../../requirements/20261030/payout-收款账户与余额解释/)）：
收款账户提交、Maker / Checker 核验、冷却、默认、停用 / 恢复、关闭、运营 Reveal、核验队列、字段要求与 Owner 余额解释。

| 模块 | 内容 |
|---|---|
| `paytech-payout-api` | `@HttpExchange` 合同 P1—P12、请求 / 响应体、`api/enums` 受控目录与 `PayoutErrorCode` |
| `paytech-payout-core` | Processor、Domain Service、Repository / Mapper、Vault / account / Platform 出站端口、`BENEFICIARY_NOTIFICATION` 补偿 Handler |
| `paytech-payout-app` | 启动类、Controller、运行与持久化配置、`beneficiary-requirements.yaml`（S3 由 channel-connector 取代后删除） |
| `paytech-payout-test` | 本地 MySQL / Redis 上的 `auto` 入口测试与架构规则 |

`mor_payout` 本期只建 `t_beneficiary_account`、`t_beneficiary_account_event`、`t_compensation_event`；模型中其余出款表随 S2 改造后首建。
模型与 Schema：[payout.pdma.json](docs/database/payout.pdma.json)、[payout-schema.sql](docs/database/sql/payout-schema.sql)。
