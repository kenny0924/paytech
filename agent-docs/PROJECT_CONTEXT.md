# Code-MoR 项目上下文

最后更新：2026-09-05

状态：Confirmed project context。本文只维护稳定的项目目标、业务边界和术语；当前实现、验证与部署见 [`CURRENT_IMPLEMENTATION.md`](./CURRENT_IMPLEMENTATION.md)，主题权威见 [`DOCUMENT_GOVERNANCE.md`](./DOCUMENT_GOVERNANCE.md)。

## 1. 项目目标

Code-MoR 是 PayTech 的 Merchant of Record 平台。Client 将 Consumer 引导到 Hosted Checkout，平台在已分配的 Operating Entity 下选择支付渠道，保存交易及合规证据，并完成多参与方计价、复式记账、对账、退款、争议和主动出款。

当前 MVP 聚焦 USD、一次性支付和 Hosted Checkout，同时保留多币种金额模型。项目不是通用电商、银行核心、支付卡数据仓库或自动化税务/资金运营平台。

## 2. 核心参与方与术语

- **Client**：平台客户，类型只有 `INDIVIDUAL` 和 `ORGANIZATION`；同一角色不再称为 Merchant。
- **Operating Entity**：与支付 Provider 签约并拥有 MID 的法律卖方主体。
- **Finance Entity**：Finance 内部的会计主体，可映射平台法律 Party，但不自动拥有 MID，也不替代 CCT Party 权威。
- **Local Partner**：提供主体资料并按独立规则获得分成的外部合作方。
- **Consumer**：在 Hosted Checkout 完成支付的最终买方。
- **Provider / Acquirer**：NI、Geidea 或其他外部支付服务提供方。

## 3. 六个核心系统

| 系统 | 拥有的核心事实 | 明确不拥有 |
|---|---|---|
| Client & Contract | Party、Client/Partner/OE、合同、商业规则、实体分配和交易时点资格 | Order、Payment、Ledger、Risk 决策 |
| Commerce | Order、唯一不可变 Order Context、Consumer Context、网络证据、接受记录和退款意图 | MID 路由、支付渠道状态、会计分录 |
| Payment Orchestration | Intent、Attempt、Provider/MID 路由、渠道 Action、回调、支付侧退款与 Void | Client 合同、Finance Ledger |
| Risk & Compliance | 风险/合规评估、KYC/KYB 支持决定、Case、争议和限制 | 支付执行、资金出款和账务事实 |
| Finance | 交易经济结果、复式账、账户余额、税务/财政事实、对账、保证金、追偿和 Manual Payout | Provider 交易状态、Client 主档和 Risk 决策 |
| Platform Support | 人类 Account、Realm/Channel、Session、Tenant IAM、公共控制面、文件、通知、Webhook 和平台审计 | 核心业务编排、财务事实和 Gateway 运行计数器 |

每个系统拥有独立数据库。禁止跨库读写、跨库外键或通过共享 DataObject 绕过 Owner。跨系统同步命令/查询使用窄 HTTP 契约，业务事实和内部回调使用 MQ。

## 4. 入口与技术支撑

- API Gateway 负责入口、基础认证、限流、路由、Request ID 和访问日志，不做业务编排或核心资源授权。
- BFF 负责页面级聚合、授权查询组合和展示模型；它无状态，不拥有核心事实，也不编排跨系统业务事务。
- Vault 是内部敏感值保护服务，不是第七个核心系统，不拥有 Consumer、Order 或其他业务语义。
- 每个有状态服务使用本地 `compensation_event` 处理提交后的可靠异步工作；XXL-Job 只是兜底唤醒/分片机制，不建立中央补偿库或通用 Inbox/Outbox。
- Hosted Checkout、Operations Console、Client Console、Partner Console、Client API、Provider Callback 和 Consumer Service Portal 是逻辑入口，不自动一一对应 Java 服务。

入口应用、BFF、Gateway 和部署边界的完整规则由 [`APPLICATION_ARCHITECTURE.md`](./APPLICATION_ARCHITECTURE.md) 拥有。

## 5. 固定业务边界

- Client 或 Local Partner 不为单笔交易选择 Operating Entity 或 MID。CCT 冻结 Order 的 Operating Entity，Payment 在该主体下为每个 Attempt 选择 MID。
- `client_order_id` 在一个 Client 内永久唯一，不是传输幂等键；重试复用原幂等键，不自动创建替代 Order。
- 退款由 Commerce 和 Payment 分别锁定各自可退位置；Finance 只消费 `RefundSucceeded`，不预留退款现金，也不使用保证金支付退款。
- Cancel 只关闭未支付 Order；Void 只释放成功但未 Capture 的 Authorization；Reversal 由 Commerce 发起并由 Payment 在锁内选择 Void 或完整 Refund 链。
- Partner 交易访问只读；Partner 不能操作 Order、Payment、Refund、Routing、MID 或 Ledger。
- Manual Payout 金额和费用由系统计算，MVP 使用固定双人审批、预创建 Execution、人工银行提交、付款证据和完整确认借记后完成。
- Platform Support 只拥有六张公共控制面当前配置表：Target、Route、Header、Query Parameter、Guard Rule 和 Console Resource；不拥有 Gateway 运行限流状态、数据库 Snapshot、Release、Ack、Tenant Override 或通用技术配置。
- `DisputeLost/DisputeOutcomeCorrected` 只有在 `outcome=LOST + principal_bearer=CLIENT + security_deposit_use_allowed=1` 时才允许 Finance 使用 Client Security Deposit，剩余短缺进入 Client Recovery Receivable。
- Industry/MCC 资格由配置决定，不硬编码某个行业；Offering 对 MVP 可选，订单创建时冻结所需交易上下文。

更窄的业务不变量由 [`database/README.md`](./database/README.md) 路由的系统文档和当前专项决策拥有。

## 6. 安全与隐私

- PAN、CVV/CVC、完整磁道和 PIN/PIN Block 不得进入 Code-MoR 服务、数据库、事件、日志、文件或 Vault。卡片采集与 Tokenization 留在 Provider 托管页面。
- Vault 只保护已批准的可恢复非卡敏感值。完整 Token 或明文不得进入外部 API、普通日志、MQ 或 BCR。
- Commerce 的网络证据留在 Commerce 并执行最小访问、审计和禁止外发；它不进入 Vault。
- MVP 不实现通用自动 Retention、Legal Hold、归档、匿名化或销毁。在真实受监管数据上线前必须另行完成法律/合规设计和验证。

Vault 当前契约与仍待迁移的消费者缺口见 [`CURRENT_IMPLEMENTATION.md`](./CURRENT_IMPLEMENTATION.md) 和文档治理中的 Vault 主题 Owner，不能从早期广义架构文档推断 V1 能力。

## 7. 工程约束

- Java、Spring、Maven、MySQL、MQ、Redis、Nacos、Gateway 和可观测性选型由 [`TECHNOLOGY_STACK.md`](./TECHNOLOGY_STACK.md) 拥有。
- Java 分层和跨模块依赖由 [`JAVA_PROJECT_STRUCTURE.md`](./JAVA_PROJECT_STRUCTURE.md) 拥有；DataObject 可在 Owner 业务 JAR 内使用，但不能跨 Maven 模块或 HTTP 边界。
- 业务 Schema 使用评审后的手工 SQL。应用和自动测试不得创建、迁移或 Seed 业务 Schema，也不使用 Flyway、Liquibase 或 Testcontainers。
- 当前工作区可能包含其他任务的未提交修改；修改时保留它们。未经明确请求，不提交、建分支、部署或配置外部资源。

## 8. 后续专项设计

以下问题保持显式未决，不允许在实现中自行补全：

- 各事件的 SLA、重试/告警参数和人工恢复流程；
- 国家/地区税务、披露和发票细则；
- 自动 Provider Settlement、自动缴税和跨境出款；
- 订阅/周期支付；
- 生产云厂商、拓扑、交付流水线、KMS、强 Workload Identity 和合规运营；
- Vault 现有 Protect/Reveal 以外由真实消费者证明需要的窄 V2 能力。
