# Payment 与 Commerce 的调用方向修订

确认日期：2026-09-14。用户明确要求 Payment 系统不允许调用 Commerce。此决定适用于 Payment 全部模块，包括 Web 集成适配、资格检查和恢复任务；取代此前 Commerce 出站 Mock 端口的设计。实际代码、测试和数据库状态仅见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

## 调用和事实边界

```text
Commerce ── 创建 Intent / 准备工具 / pay / 后续命令 ──▶ Payment
Commerce ◀──────────────── 支付事实 MQ ───────────── Payment
```

Commerce 完成订单、支付准备和法律接受校验后，向 Payment 传入所需事实。Payment 不再定义 `ExecutionPorts.Commerce`，不调用 `verifyCreation`、`verifySubmission` 或 `consumerCountry`；也不能通过其他适配器间接回查 Commerce。

Payment 仍校验请求格式、Client/来源/Intent/Token 归属、不可变事实、幂等、金额、到期、来源关闭和自身资金状态。Risk、CCT 资格、Routing、Card、Channel 的真实接入要求保留；Eligibility 不能承接 Commerce 回查。订单取消/关闭由 Commerce 主动调用既有关闭和终止命令，未决资金继续按原动作恢复。没有可用的 Risk/Channel 实现时不能默认放行收款。

## 消费者国家的传入与冻结

EX-I01 `CreateIntentRequestBody` 增加必填 `consumerCountryCode`，来源为 Commerce 冻结的消费者国家，格式为大写两字母 ISO 3166-1 alpha-2 code。Payment 执行格式校验，不猜测、默认或从渠道推断国家。

| 保存位置 | 写入 | 使用 |
|---|---|---|
| `t_payment_intent.consumer_country_code CHAR(2) NOT NULL` | 创建 Intent 时保存；无默认值，无新增索引 | 首次定价与恢复时的本地权威输入；重复创建时必须一致 |
| 既有 `t_payment_pricing_fact.consumer_country_code` | 支付成功且适用定价维度齐全时，从 Intent 冻结值生成 | 下游读取完整定价事实 |

渠道只提供其拥有的卡及认证事实；即使返回不同消费者国家，Payment 仍使用 Intent 的冻结值。其他定价字段缺失时保留 PENDING，不撤销已经确认的资金成功；恢复无需 Commerce 在线。

示例：Commerce 创建 Intent 时传 `AE`，SALE 渠道返回 `US` 或不返回国家，Payment 的定价事实仍为 `AE`。同幂等键或新键试图把原订单国家改为 `US` 均返回冲突。

## 命令兼容和 SQL

新增国家参与创建命令 Hash，沿用原字段顺序后追加 `consumerCountryCode`。创建操作升级为 `CREATE_INTENT_V2`，不重新解释旧 V1 Hash；HTTP 路径仍为本期未对外激活的 `/payment-execution/v1/intents`。其余命令操作和 Hash 保持原合同，Client 命名空间继续按[内部信任修订](./trusted-internal-amendment.md)。

本次为本地研发合同收敛，不提供非空历史环境的自动升级。存在旧 Intent、V1 命令或在途任务时须另行设计历史读取/排空和迁移，不能把历史国家填成默认值，也不能直接切换 V2 规避旧命令。Commerce 真实适配仍属 TODO-EX-COM，由 Commerce 调用 Payment 的方向实现。

- 正式 PDManer 与完整 Schema 为字段权威；[空库创建](./db/instrument-token-create-empty-schema.sql)包含新列。
- 旧本地空表库先审阅既有[工具 Token 迁移](./db/instrument-token-empty-migration.sql)，再审阅[Intent 国家空表迁移](./db/payment-intent-consumer-country-empty-migration.sql)。完全空库创建与增量升级是互斥路径。
- 增量前须停止写入、确认相关业务/命令/任务为空、备份并验证恢复，再取得目标库执行授权。应用、测试和本次代码修改均不执行 SQL。

## 验证范围

架构检查禁止 Payment 模块依赖 Commerce API 或恢复上述出站端口；合同测试覆盖国家必填与格式。真实 HTTP/本地数据库测试覆盖无 Commerce 适配器创建和重放、同键/换键改国家拒绝、渠道国家不覆盖及晚到定价恢复。测试是否已实际运行，以台账和验证证据为准。
