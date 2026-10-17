# Finance 支付成功入账 — 跨系统依赖清单

状态：2026-10-16 Finance 会话整理，供用户分派各系统 Owner 评审；本文件不构成任何系统的已批准合同，也不授权修改其他系统。

Finance 创建一条 PAYMENT `transaction_economics` 必须一次性冻结下列事实（表定义见 `finance-service/docs/database/sql/finance-schema.sql` `t_transaction_economics` / `t_transaction_economic_component`），设计禁止缺失时使用当前、默认或零费率（[FINANCE_DATABASE.md](../../../agent-docs/database/FINANCE_DATABASE.md) §4.3，[2026-09-11 四系统决定](../../../agent-docs/decisions/2026-09-11-cct-commerce-oe-pricing-model.md)）。

| # | Owner | 需要的能力 | 现状 | Finance 需要的最小内容 |
|---|---|---|---|---|
| X1 | Payment | `PaymentSucceeded` 升级 schemaVersion（或另行合同） | v1 已发布：clientId、orderId、paymentIntentId、paymentAttemptId、entityNo、amount/currency/capturedAmount、paymentSucceededTime、paymentMethodCode、channelId/channelAccountId/midId、channelTransactionRef | `orderContextId`；定价所需的支付事实（2026-09-14 删除 `t_payment_pricing_fact` 后的替代：成功 Action、标准化支付方式/Profile 特征）；渠道交易引用的稳定 ID 与引用类型（供 Channel Open Item `channel_transaction_ref_id/reference_type/external_transaction_id`） |
| X2 | Commerce | 订单上下文查询（COM-I04 或新合同） | 未实现 | `order_id + order_context_id`、`commercial_context_time`、Client/OE、`industry_code + mcc_code`、`consumer_country_code`、Client Agreement、Term Versions；Partner 来源时 Local Partner、Partner Agreement-OE、`platform_service_party_id`；金额币种供逐值交叉校验 |
| X3 | CCT | 价格解析 `POST /commercial-prices/v1/resolutions`（[阶段 3 合同](../../20260911/client-contract-OE与Client模型梳理/stage3-contracts.md)） | 模型已定，接口未实现 | 按固定 Term Version + 支付事实返回准确 Client Price（Component/Band、T+N、日历、时区、Cutoff）、Partner Share、Platform Service Charge 及签约 Party |
| X4 | Finance（自有） | 税务快照 | 未实现 | `tax_snapshot_id` 非空；Seller 实收税金额 |
| X5 | Finance（自有） | 渠道成本配置与渠道清算账户维护 | 渠道成本配置：已实现（[2026-10-17 渠道成本配置需求](../../20261017/finance-渠道成本配置/requirement.md)）；渠道清算账户维护：待 X6 | 按 Channel/MID 唯一命中成本规则；`t_channel_account` 按 MID + 币种唯一 |
| X6 | Payment（跨系统） | MID 权威元组查询接口 | 未实现 | 按 MID 返回 Channel、Channel Account、OE、币种；Finance 创建 `t_channel_account` 时按数据库设计全局不变量 49 核对 |

完成顺序建议：X1、X2、X3 的合同先评审定稿 → Finance 消费批次（MQ Listener、交易经济创建、费率计算、补偿事件迁移、Channel Open Item、出站事件）在同一本地事务内调用本需求的 AUTO 过账引擎。
