# PDManer 数据库设计规范

最后更新：2026-09-02

> **2026-09-01 current snapshot:** generated evidence is CCT `37/423/57/44/64/7/6` and Platform `63/822/123/99/107/5/7`, plus Platform 31 composite FKs, 85 named CHECK sidecar contracts and exactly 36 `*_hash` fields. Earlier Platform physical snapshots are historical; Commerce, Payment, Finance and Risk figures remain current.

## 1. 使用范围

所有需要设计、创建或演进数据库的项目都必须使用本规范，不限于六个核心 Java 服务。以下任务开始前，Agent 必须完整读取本文：

- 新建或修改 PDManer `*.pdma.json` 模型；
- 设计表、字段、主键、索引、唯一约束或表关系；
- 新增或修改手工 DDL、初始化 DML；
- 修改 DataObject、MyBatis Mapper、Repository 或与数据库结构有关的测试；DataObject 由包名表达持久化职责，类型名不追加 `DO` 后缀；
- 数据库正向生成、反向解析或模型与数据库差异比较。

不持有数据库的 Gateway、BFF、前端和无状态应用不得为了形式一致创建空 PDManer 模型。Vault 虽不是第七个核心业务系统，但拥有独立安全数据库；其当前模型已从本模板派生，必须遵守[批准的 V1 三表设计](../docs/superpowers/specs/2026-09-02-vault-database-design.md)、[物理建模决策](../vault-service/docs/database/VAULT_PHYSICAL_DECISIONS.md)和 `VAULT_SERVICE_ARCHITECTURE.md` 中未被取代的非卡/PCI、业务所有权、内部专用与禁止直读数据库等安全边界。

## 2. 项目模板

项目内统一模板为：

- [templates/pdmaner/template.pdma.json](./templates/pdmaner/template.pdma.json)

模板来源：

- 原始路径：`/Users/kenny/Desktop/Workspace/PDManer/template.pdma.json`
- 原始 PDManer 版本：`4.9.3`
- 原始文件 SHA-256：`0b14315cb76de3f3492426330628152702e3aaa5e3fcbb3f231cfc713a175391`
- 当前项目模板 SHA-256：`51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540`

所有新数据库模型必须从项目内模板派生，不能继续依赖工作区外的绝对路径，也不能直接修改原始模板。

## 3. 模板精简范围

项目模板保留：

- MySQL、Java、JavaMybatis 类型映射；
- MySQL DDL 模板；
- 表、字段、索引、注释、保留字和长度等通用检查规则；
- `DefaultString`、`PrimaryKey`、`Money`、`Int`、`Decimal`、`DateTime`、`currency` 数据域；
- `create_time`、`update_time` 默认审计字段；
- PDManer 打开和编辑模型所需的其他结构及 UI 元数据。

项目模板已删除：

- `PARTNER_ID` 和“商户”术语；
- `VOUCHER_NO` 等交易参考项目遗留域；
- `EXTENSION` 数据域和所有表默认携带的 `extension` 字段；
- Oracle、PostgreSQL、SQL Server、Hive、C#、Golang、Rust 等当前无关目标；
- Hive 表属性；
- Controller、Service、Entity、Mapper 等通用 Java CRUD 代码模板；
- 数据库连接信息。

删除 Java 代码模板是有意决策。PDManer 用于数据库建模和 DDL 辅助，不得用通用 CRUD 模板绕过 `JAVA_PROJECT_STRUCTURE.md` 的 DDD 分层、构造器注入和 Repository/Mapper 规范。

## 4. 模型存放位置

每个拥有数据库的项目维护自己的模型：

```text
<project-root>/
└── docs/
    └── database/
        └── <project-name>.pdma.json
```

当前六套已获准且已生成的 V1 物理模型使用：

```text
client-contract-service/docs/database/client-contract.pdma.json
commerce-service/docs/database/commerce.pdma.json
payment-orchestration-service/docs/database/payment-orchestration.pdma.json
finance-service/docs/database/finance.pdma.json
risk-compliance-service/docs/database/risk-compliance.pdma.json
platform-support-service/docs/database/platform-support.pdma.json
```

六大核心系统之外，独立 Vault 数据库设计项目使用：

```text
vault-service/docs/database/vault.pdma.json
```

Client & Contract 导航：[V1 逻辑模型](./database/CLIENT_CONTRACT_DATABASE.md)、[现实场景与逐表字段映射](./database/scenarios/CLIENT_CONTRACT_REAL_WORLD_SCENARIOS.md)、[实时交易资格与 Retention 延期设计](./plans/2026-08-23-cct-realtime-eligibility-retention-deferral-design.md)、[实施计划](./plans/2026-08-23-cct-realtime-eligibility-retention-deferral-implementation-plan.md)、[物理建模决策](../client-contract-service/docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md) 和 [PDManer 模型](../client-contract-service/docs/database/client-contract.pdma.json)。

Commerce 导航：[V1 逻辑模型](./database/COMMERCE_DATABASE.md)、[现实场景与逐表字段映射](./database/scenarios/COMMERCE_REAL_WORLD_SCENARIOS.md)、[文档投影、审计与取消撤销精简设计（当前）](./plans/2026-08-24-commerce-document-audit-cancel-reversal-design.md)、[隐私与 Fulfillment 移除设计（前序）](./plans/2026-08-24-commerce-privacy-fulfillment-simplification-design.md)、[Order Context 精简设计](./plans/2026-08-24-commerce-order-context-simplification-design.md)、[消费者分析与网络证据设计（前序）](./plans/2026-08-24-commerce-consumer-analytics-evidence-design.md)、[物理建模决策（当前）](../commerce-service/docs/database/COMMERCE_PHYSICAL_DECISIONS.md) 和 [PDManer 模型（当前 14 表、223 字段，已通过自动验证）](../commerce-service/docs/database/commerce.pdma.json)。

Payment Orchestration 导航：[V1 逻辑模型](./database/PAYMENT_ORCHESTRATION_DATABASE.md)、[现实场景与逐表字段映射](./database/scenarios/PAYMENT_REAL_WORLD_SCENARIOS.md)、[Payment Execution 精简设计（当前）](./plans/2026-08-25-payment-execution-simplification-design.md)、[Provider Account 与 MID 五表精简设计（专项参考）](./plans/2026-08-25-payment-provider-account-mid-simplification-design.md)、[平衡型物理模型设计（历史）](./plans/2026-08-25-payment-balanced-physical-model-design.md)、[物理建模决策](../payment-orchestration-service/docs/database/PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md) 和 [PDManer 模型（当前 29 表、373 字段）](../payment-orchestration-service/docs/database/payment-orchestration.pdma.json)。

Finance 导航：[V1 逻辑模型](./database/FINANCE_DATABASE.md)、[现实场景与逐表字段映射](./database/scenarios/FINANCE_REAL_WORLD_SCENARIOS.md)、[交易经济与会计规则精简计划](./plans/2026-08-26-finance-transaction-economics-accounting-rule-simplification-implementation-plan.md)、[Account Balance 合同成熟精简实施计划](./plans/2026-08-27-finance-account-balance-contract-maturity-simplification-implementation-plan.md)、[退款 Funding 移除实施记录](./plans/2026-08-27-finance-refund-funding-removal-implementation-plan.md)、[通用 Funding 模块删除设计](./plans/2026-08-27-finance-funding-module-removal-design.md)、[通用 Funding 模块删除实施记录](./plans/2026-08-27-finance-funding-module-removal-implementation-plan.md)、[保证金 Account 精简实施记录](./plans/2026-08-27-finance-security-deposit-account-simplification-implementation-plan.md) + [Provider 对账精简实施计划](./plans/2026-08-28-finance-provider-reconciliation-simplification-implementation-plan.md) + [Provider 三阶段账户实施计划](./plans/2026-08-28-finance-provider-three-stage-accounting-implementation-plan.md) + [手工出款精简设计](./plans/2026-08-28-finance-manual-payout-simplification-design.md) + [手工出款精简实施计划](./plans/2026-08-28-finance-manual-payout-simplification-implementation-plan.md)、[统一账户模型设计（Funding 部分已替代）](./plans/2026-08-26-finance-unified-account-model-design.md)、[Account Balance 合并计划（Funding 部分已替代）](./plans/2026-08-26-finance-account-balance-merge-implementation-plan.md)、[统一账户实施记录](./plans/2026-08-26-finance-unified-account-model-implementation-plan.md)、[Finance Entity 双账簿设计](./plans/2026-08-26-finance-entity-dual-ledger-design.md)、[Finance Entity 双账簿实施记录（Funding 部分已替代）](./plans/2026-08-26-finance-entity-dual-ledger-implementation-plan.md)、[Trial Balance 与关账门禁收紧实施记录](./plans/2026-08-27-finance-trial-balance-close-hardening-implementation-plan.md)、[Tax/Fiscal 23 表精简实施记录](./plans/2026-08-27-finance-tax-fiscal-23-table-simplification-implementation-plan.md)、[物理建模决策](../finance-service/docs/database/FINANCE_PHYSICAL_DECISIONS.md) 和 [PDManer 模型（当前 84 表、1,100 字段）](../finance-service/docs/database/finance.pdma.json)。Finance 自动验证已通过，PDManer 4.9.3 应用内检查仍是人工门禁；2026-08-25 平衡型计划只作为历史基线。

Risk & Compliance 导航：[V1 逻辑模型](./database/RISK_COMPLIANCE_DATABASE.md)、[现实场景与逐表字段映射](./database/scenarios/RISK_COMPLIANCE_REAL_WORLD_SCENARIOS.md)、[物理建模决策](../risk-compliance-service/docs/database/RISK_COMPLIANCE_PHYSICAL_DECISIONS.md)、[Limit 7 表平衡精简设计](./plans/2026-08-28-risk-limit-balanced-simplification-design.md)、[Limit 平衡精简实施计划](./plans/2026-08-28-risk-limit-balanced-simplification-implementation-plan.md)、[Decision Engine 19/8 平衡精简计划](./plans/2026-08-27-risk-decision-engine-balanced-simplification-implementation-plan.md) + [Dispute 平衡优化设计](./plans/2026-08-28-risk-dispute-balanced-optimization-design.md) + [Dispute 平衡优化实施计划](./plans/2026-08-28-risk-dispute-balanced-optimization-implementation-plan.md)、[DD 19 表精简计划](./plans/2026-08-27-risk-dd-simplification-implementation-plan.md)、[首版 PDManer 历史记录](./plans/2026-08-26-risk-compliance-pdmaner-implementation-plan.md) 和 [PDManer 模型（当前 71 表、865 字段）](../risk-compliance-service/docs/database/risk-compliance.pdma.json)。Risk 自动验证已通过，PDManer 4.9.3 应用内检查仍是人工门禁。

Platform Support 导航：[统一人工认证设计（认证权威）](../docs/superpowers/specs/2026-08-30-platform-unified-human-authentication-design.md)、[57 表数据库精简设计（未受影响领域前序）](../docs/superpowers/specs/2026-09-01-platform-support-database-simplification-design.md)、[57 表实施记录](./plans/2026-09-01-platform-support-database-simplification-implementation-plan.md)、[统一公共 API 动态控制面设计](../docs/superpowers/specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md)、[公共控制面 PDManer 实施计划](../docs/superpowers/plans/2026-09-01-platform-public-api-control-plane-pdmaner.md)、[V1 逻辑/物理基线](./database/PLATFORM_SUPPORT_DATABASE.md)、[现实场景与逐表字段映射](./database/scenarios/PLATFORM_SUPPORT_REAL_WORLD_SCENARIOS.md)、[物理建模决策](../platform-support-service/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md) 和 [PDManer 模型（当前 63 表、822 字段）](../platform-support-service/docs/database/platform-support.pdma.json)。早期 Operations-only、平衡型和已取代物理记录只保留历史价值；自动验证已通过，PDManer 4.9.3 应用内检查仍是人工门禁。

Vault 导航：[批准的 V1 三表设计](../docs/superpowers/specs/2026-09-02-vault-database-design.md)、[物理建模决策](../vault-service/docs/database/VAULT_PHYSICAL_DECISIONS.md)和 [PDManer 模型](../vault-service/docs/database/vault.pdma.json)。当前精确为 3 表、30 字段、3 个非主键唯一索引、4 个访问索引、2 条本库 `n:1` 关系、0 条 1:1 关系和 1 个 `vault_core` Diagram Group，自动验证已通过，PDManer 4.9.3 应用内人工检查仍待完成。`VAULT_SERVICE_ARCHITECTURE.md` 中强制 Binding、mTLS/Workload Identity、广泛 API/生命周期、18 表拓扑、复杂幂等和 Platform Audit 部分在被当前设计取代处仅为历史。当前 V1 只接受部署网络限制的普通内部 HTTP、必需但可伪造的 `X-Caller-Service` 与静态 Allowlist 作为 MVP 信任假设，不构成强认证且不持有 X.509 应用证书；没有 Java、手工 DDL/DML、物理数据库、KMS 集成/运行时认证或部署实现。

Platform Support 当前不使用以 `_ver` 结尾的物理表名。可编辑配置默认采用带 `revision_no` 的单表 Revision；只有 Document Version、Webhook Endpoint Revision 和 Webhook Signing Secret 具有必须独立保留的历史身份。其 `*_hash` 字段必须属于当前 36 字段精确 Allowlist，并能指出实际完整性、防重、冲突检测或安全消费者。公共 API 控制面只允许 `t_gateway_target`、`t_gateway_route`、`t_gateway_route_header`、`t_gateway_route_query_param`、Route-owned `t_gateway_guard_rule` 和 `t_console_resource` 六张当前配置表；不得恢复旧 Gateway Policy/Binding/Selector/Version/Release/Snapshot/Ack/Tenant Override、通用技术配置或持久化 Integration Readiness。

六套书面逻辑模型均已确认。CCT 为 37 表、423 字段、57 个唯一索引、44 个访问索引、64 条本库关系、7 条 1:1 关系和 6 个 Diagram Groups。Commerce 当前为 14 表、223 字段、20 个唯一索引、11 个访问索引、14 条本库关系和 4 个 Diagram Groups，精确包含 7 张 Version 表、6 张 immutable evidence 表且不保存通用 BCR；当前 Order Context 额外冻结 Platform Service Charge Scope ID 与 Platform Service Party ID，但不保存 Finance Entity、服务费金额或会计状态。Commerce 使用稳定 Email/Phone Value Token、只含姓名/账单地址的 Profile Token，并直接保存受限 Network Evidence 原值，无 Consumer/Network Blind Index；Refund 仅保留 Order Refund Position 与 Refund Request，不复制 Finance/Payment 状态或事件元数据；消费者文档状态、版本、文件和更正关系归 Platform Support。Payment 当前为 29 表、373 字段、41 个唯一索引、44 个访问索引、63 条本库关系和 7 个 Diagram Groups；Payment Method、可选单表 Instrument、通用 Customer Action 与 Channel Refund 去冗余遵循当前 Payment Execution 精简设计，首次 Provider 提交前实时检查采用 Fail-Closed，但不建立独立 Gate 持久化表。Finance 是第四套已授权、已生成且已通过自动验证的物理设计，当前快照为 84 表、1,100 字段、145 个唯一索引、155 个访问索引、160 条本库关系、22 条 1:1 关系和 6 个 Diagram Groups；Tax/Fiscal 主图精简为 23 表，Finance Entity 为手工维护的平台法律主体映射，Partner 来源服务费在 Partner OE 与平台 Finance Entity 两本账分别形成平衡 Journal。退款额度由 Commerce 与 Payment 各自控制，Finance 只在 `RefundSucceeded` 后入账；未决退款不阻断合同成熟。通用 Funding 四表及 Reserve/Recovery 工作流五表已经删除。预存保证金和追偿统一使用 `t_account` + Journal；Manual Payout 的四个检查点实时校验对应 Client/Local Partner 的 Recovery/Deposit 门禁；Payout Execution 与 Tax Payment 通过同一 `BANK_CASH` Account 行锁，分别以 `active_payout_cash` 与 `active_tax_cash` 控制现金容量。Risk & Compliance 是第五套已授权、已生成且已通过自动验证的物理设计，当前快照为 71 表、865 字段、106 个唯一索引、74 个访问索引、111 条本库关系、9 条 1:1 关系、8 个 Diagram Groups 和 83 个 CHECK 约束，其中 Due Diligence 为 19 表、Limit 为 7 表。Platform Support 是第六套已授权、已生成且已通过自动验证的物理设计，当前快照为 63 表、822 字段、123 个唯一索引、99 个访问索引、107 条本库关系、5 条 1:1 关系和 7 个 Diagram Groups，并冻结 31 条复合外键、85 条 CHECK 契约与恰好 36 个 `*_hash` 字段。六套物理模型均已授权、生成并通过自动验证。

一个部署服务默认只有一个数据库模型。只有存在已批准的独立物理数据库且生命周期确实不同，才能拆成多个模型。

Finance's current 84-table Manual Payout boundary uses shared `balance_available_time`, verified Beneficiary Accounts, `owner_debit_amount` and `active_payout_cash`; it has no Settlement Candidate/Run/Payment model. Risk's current 71-table authority includes the [2026-08-29 four-checkpoint alignment record](./plans/2026-08-29-risk-manual-payout-eligibility-checkpoint-alignment-implementation-plan.md): each distinct Payout checkpoint creates a new Assessment/Evaluation pair, while same-command replay reuses its prior pair and every scope is exact on Owner/OE/currency/Beneficiary Account country/`owner_debit_amount`.

## 5. 设计与执行来源

数据库相关工件职责如下：

| 工件 | 职责 |
|---|---|
| PDManer `*.pdma.json` | 表、字段、关系、索引、注释和物理模型的设计来源 |
| 当前 Schema DDL + 初始化 DML | 经人工审查和执行的当前完整结构与初始数据；应用启动不得自动执行 |
| DataObject/Mapper/Repository | 生产代码中的持久化映射与访问抽象；DataObject 类型使用业务实体名，不加 `DO` 后缀 |
| Repository/auto 测试 | 在仓库本地 MySQL 上验证 SQL、约束和映射 |

PDManer 模型不能替代可执行 SQL，SQL 也不能替代设计模型。一次结构变更必须同步更新：

```text
业务状态和所有权设计
  → PDManer 模型
    → PDManer 模型检查
      → 更新当前 Schema DDL / 初始化 DML
        → DataObject / Mapper / Repository
          → Repository 与服务级 auto 测试
```

当前 Client & Contract、Commerce、Payment Orchestration、Finance、Risk & Compliance 与 Platform Support 六套授权均只覆盖从冻结逻辑设计到 PDManer 物理设计；Finance 的评审入口包括[逻辑模型](./database/FINANCE_DATABASE.md)、[现实场景](./database/scenarios/FINANCE_REAL_WORLD_SCENARIOS.md)、[退款 Funding 移除实施记录](./plans/2026-08-27-finance-refund-funding-removal-implementation-plan.md)、[通用 Funding 模块删除设计](./plans/2026-08-27-finance-funding-module-removal-design.md)、[通用 Funding 模块删除实施记录](./plans/2026-08-27-finance-funding-module-removal-implementation-plan.md)、[保证金 Account 精简实施记录](./plans/2026-08-27-finance-security-deposit-account-simplification-implementation-plan.md) + [Provider 对账精简实施计划](./plans/2026-08-28-finance-provider-reconciliation-simplification-implementation-plan.md) + [Provider 三阶段账户实施计划](./plans/2026-08-28-finance-provider-three-stage-accounting-implementation-plan.md) + [手工出款精简设计](./plans/2026-08-28-finance-manual-payout-simplification-design.md) + [手工出款精简实施计划](./plans/2026-08-28-finance-manual-payout-simplification-implementation-plan.md)、[平衡型物理模型设计（历史）](./plans/2026-08-25-finance-balanced-physical-model-design.md)、[实施计划](./plans/2026-08-25-finance-pdmaner-implementation-plan.md)、[物理建模决策](../finance-service/docs/database/FINANCE_PHYSICAL_DECISIONS.md)与[生成模型](../finance-service/docs/database/finance.pdma.json)；Risk 的评审入口包括[逻辑模型](./database/RISK_COMPLIANCE_DATABASE.md)、[现实场景](./database/scenarios/RISK_COMPLIANCE_REAL_WORLD_SCENARIOS.md)、[Limit 7 表平衡精简设计](./plans/2026-08-28-risk-limit-balanced-simplification-design.md)、[Limit 平衡精简实施计划](./plans/2026-08-28-risk-limit-balanced-simplification-implementation-plan.md)、[Decision Engine 19/8 平衡精简计划](./plans/2026-08-27-risk-decision-engine-balanced-simplification-implementation-plan.md) + [Dispute 平衡优化设计](./plans/2026-08-28-risk-dispute-balanced-optimization-design.md) + [Dispute 平衡优化实施计划](./plans/2026-08-28-risk-dispute-balanced-optimization-implementation-plan.md)、[DD 19 表精简计划](./plans/2026-08-27-risk-dd-simplification-implementation-plan.md)、[物理建模决策](../risk-compliance-service/docs/database/RISK_COMPLIANCE_PHYSICAL_DECISIONS.md)与[生成模型](../risk-compliance-service/docs/database/risk-compliance.pdma.json)。从手工 DDL/DML 开始的步骤属于后续门禁，必须再次获得明确授权；本轮同样未授权 Java 实现、物理数据库或运行环境。

Platform Support 的评审入口包括[57 表数据库精简设计（未受影响领域前序）](../docs/superpowers/specs/2026-09-01-platform-support-database-simplification-design.md)、[57 表实施记录](./plans/2026-09-01-platform-support-database-simplification-implementation-plan.md)、[统一公共 API 动态控制面设计](../docs/superpowers/specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md)、[公共控制面 PDManer 实施计划](../docs/superpowers/plans/2026-09-01-platform-public-api-control-plane-pdmaner.md)、[逻辑/物理基线](./database/PLATFORM_SUPPORT_DATABASE.md)、[现实场景](./database/scenarios/PLATFORM_SUPPORT_REAL_WORLD_SCENARIOS.md)、[物理建模决策](../platform-support-service/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md)与[生成模型](../platform-support-service/docs/database/platform-support.pdma.json)。

Vault 的评审入口是[批准的 V1 三表设计](../docs/superpowers/specs/2026-09-02-vault-database-design.md)、[物理建模决策](../vault-service/docs/database/VAULT_PHYSICAL_DECISIONS.md)与[生成模型](../vault-service/docs/database/vault.pdma.json)。该授权同样只到 PDManer 物理设计；Java、Migration、物理数据库、KMS 集成、运行时认证和部署均未实现。

禁止只修改线上数据库、只修改 SQL、只修改 Java 字段或从数据库反向覆盖模型后不审查差异。

## 6. 数据库所有权

- 每个核心系统只设计和访问自己的数据库。
- 禁止跨数据库查询、写入、共享表、视图依赖和物理外键。
- 对其他系统聚合只保存稳定业务 ID，并在字段注释中写清来源系统和语义。
- PDManer 图中的物理关系只能连接当前数据库内的表。
- 跨系统一致性通过 HTTP 契约、业务事件、幂等和补偿实现，不能通过数据库关系实现。
- BFF、Gateway 和入口应用不拥有核心业务事实；没有自身持久化需求时不创建数据库。

## 7. 第一性原理与精简门禁

数据库模型首先服务于当前已经确认的业务事实、约束和查询，不以“以后可能用到”为理由提前堆叠表、字段或基础设施能力。表数量少不是目标，能够用最小且明确的结构完整表达业务才是目标。

每张表和每个非通用审计字段在设计评审时都必须回答：

1. 它保存的具体业务事实是什么，事实所有者是谁；
2. 哪个真实写入流程产生它，哪个查询、约束、计算或审计流程使用它；
3. 为什么不能放在现有结构化表中，拆表带来了什么独立的基数、生命周期、权限或约束；
4. 删除该表或字段会破坏哪个已经确认的场景；
5. 能否用一个带示例数据的实际场景说明从写入、当前查询到历史追溯的完整链路。

无法具体回答时，应删除、合并或推迟建模。表名、DDD 术语、审计口号或“未来扩展性”本身不构成保留理由。

### 7.1 业务结构优先，安全引用不能代替数据模型

- 业务所有者数据库必须显式定义当前业务需要理解、校验、查询或展示的字段。不得用一个 Vault Token、文件引用、无 Schema JSON 或 `extension` 字段代替尚未设计的数据结构。
- Vault 只托管已分类、已注册 Data Type 的敏感原值，不拥有字段的业务含义。业务表保存 Token 时，必须从字段名和注释看出它引用什么值、使用哪个 Data Type、由哪个业务字段持有以及允许怎样使用；当前 V1 没有 Vault Resource Binding。
- 结构化敏感 Field Set 也必须在 Vault Data Type Catalog 明确列出字段、类型、格式、Normalization、Mask Policy 和 Schema Version；禁止“整个对象加密后丢进 Vault，但没有任何地方说明对象结构”。
- 如果字段按当前业务分类允许本地保存，应直接使用结构化列，并继续执行授权、日志、留存和 Legal Hold 控制；“不进 Vault”不等于“公开数据”。
- CCT Party V1 是当前示例：Email、Phone 使用 `EMAIL_ADDRESS_V1`、`PHONE_E164_V1` 标量 Vault Token；姓名、日期、法律标识和地址结构化落 CCT；登录密码不进入 CCT、Vault、事件或日志。Platform 自有认证域保存全局 Account 的不可逆 Password verifier；Client/Partner Account 不等同于 CCT Party、Signatory 或法律主体，Consumer authentication 仍不在本设计范围。该示例不能推导为银行账户、税号、KYC/KYB 或其他系统字段自动不敏感，各系统必须独立分类。

### 7.2 复杂机制必须有当前需求证据

| 机制 | 允许使用的条件 | 不允许的理由 |
|---|---|---|
| 独立子表 | 子对象具有独立基数、生命周期、权限、查询或数据库约束 | 仅为了把几个始终一起读写的字段“看起来更 DDD”而拆表 |
| `current_*_id` | 存在高频当前版本查询，且指针需要与版本激活原子切换 | 已能通过受约束的 Active Slot 唯一定位，却再保存重复指针 |
| `active_slot` | 业务确实要求同一 Scope 最多一条活动记录，并需要数据库唯一约束防并发双活 | 只为了表示状态，或没有完整的 `status ↔ slot` 条件约束 |
| 乐观锁 `version` | 可变记录存在并发命令或乱序更新，需要 Expected Version/CAS | 追加不可变事实、普通引用表或没有并发写入的静态行机械带版本 |
| 业务 Version 表 | 业务必须冻结不可变快照、支持 As-Of 引用或保留有效期版本 | 只是想记录谁改过状态；这种审计优先使用统一行级变更历史 |
| `hash_schema_version + content_hash` | 下游会冻结、校验或重放内容，且 Canonical 输入、排序、编码和版本边界已经定义 | 只写一个无法重算、没有消费者的“完整性 Hash”字段 |
| 专项 History/Fact 表 | 记录本身是领域证据，具有独立查询、状态机或法定语义 | 每个主表仅为字段/状态变化复制一张结构相同的 History 表 |
| Blind Index、Alias、密钥轮换表 | 当前存在经批准的等值检索/唯一约束与轮换协议 | 仅为未来可能轮换提前把 Vault 内部密钥、Alias 或索引模型复制进业务库 |

已经追加不可变的独立 Agreement、Acceptance、Decision、Ledger、外部来源事实等继续使用专项表；统一行级历史不能吞并这些领域事实。跨系统交易上下文使用 Agreement/Association 稳定 ID，商业计算规则另用 Pricing/Share/Platform Service Charge Version/Scope；CCT 一份 Agreement 本身就是一份独立法律事实，没有 Agreement Version 表。相反，普通字段或状态变化不应各自建立一套重复历史结构。

### 7.3 场景化审查与可删除性

- 复杂聚合或关系图必须至少提供一个实际 Showcase，说明示例数据分别落在哪些表、如何查询当前值、如何发生版本切换以及如何追溯。只有 ER 图而无法讲通场景，视为模型尚未完成。
- 评审表清单时按业务职责分类，而不是把总表数当作设计质量。每类都要说明为什么存在，基础设施表与业务事实表必须分开统计。
- 引入新抽象前先检查能否删除旧表或合并重复职责。一次重构如果只新增抽象、不减少任何重复或明确复杂度，必须重新说明收益。
- 为未来保留能力时只保留可兼容的边界，例如稳定 ID、版本化接口或明确扩展点；不得提前实现尚无用例的工作流、Alias、Key Rotation、泛化规则引擎或自由 JSON 容器。

## 8. 命名与注释

项目派生模型遵守以下规则；共享模板中的旧索引前缀校验为兼容性基线，派生模型必须覆盖为本节规则，不能据此继续生成 `ix_`：

- 表、字段和索引物理名称使用小写 `snake_case`；所有物理表 `defKey` 必须以 `t_` 开头，例如逻辑表 `party` 对应物理表 `t_party`；
- 名称只使用英文、数字和下划线，不能包含空格或数据库保留字；
- 表、字段和索引名称默认不超过 32 个字符；
- PDManer 表 `defName` 使用简洁、唯一、可独立理解的中文业务名称，物理 `defKey` 继续使用英文 `snake_case`；不得直接把整段表用途机械复制为表名；
- 每张表和每个字段必须有清晰中文注释；字段说明直接描述字段自身的业务含义，不得在每个字段前重复表名或表用途前缀；
- 字段说明不得使用 `Profile ...`、`... for <table>` 等没有新增语义的机械前缀，也不能只把英文列名逐词翻译成中文；脱离所在表查看时仍应能理解字段含义；
- 闭合枚举必须在字段说明中完整列出稳定允许值，格式统一为 `允许值：A、B、C。`；开放目录代码必须写 `取值来源：<权威标准或目录>；非固定枚举。`，不得虚构闭合值域；
- Boolean 与 nullable Slot 必须写清 `0/1/NULL` 的准确业务含义和驱动状态，例如什么状态占用 `1`、什么状态必须为 `NULL`；
- 唯一索引以 `uk_` 开头，普通索引以 `idx_` 开头；禁止新建或重新生成 `ix_` 普通索引；
- Java 属性使用 `lowerCamelCase`，通过 Mapper 显式映射到数据库字段；
- 名称必须表达业务语义，禁止 `data`、`info1`、`ext1` 等含糊命名。

中文显示名和说明用于设计审阅，不增加 `t_` 等技术前缀；物理表名和字段名始终使用英文。架构说明与逻辑 Catalog 可继续用不带 `t_` 的领域名称，但 PDManer `defKey`、Schema DDL、DataObject/Mapper 映射和数据库测试必须使用带 `t_` 的物理表名。项目术语以 `PROJECT_CONTEXT.md` 为准，不能重新引入 Merchant 作为 Client 的同义词。

## 9. 默认字段和主键

新实体默认带有：

```text
create_time
update_time
```

- 两个字段必须非空，含义和更新时机写入字段注释。
- 不使用模板原始校验中的 `created_time/updated_time`；项目模板已统一为 `create_time/update_time`。
- 不给所有表添加通用 `extension`、`remark`、`deleted` 或其他万能字段；只有明确业务需要时才建模。
- 状态并发更新需要 `version` 或期望旧状态条件时，应在具体模型中显式设计。

六大核心系统已批准统一使用服务侧生成的 Snowflake `BIGINT` 主键，Java 类型为 `Long`：

- 领域实体主键不得使用 `AUTO_INCREMENT`；
- Snowflake ID 只作为内部技术标识，不向外承诺连续、有序或可枚举；
- 对外业务编号必须单独建模并建立唯一约束；
- worker/region 配置由部署环境管理，生成器必须处理时钟回拨并防止重复；
- 测试必须使用与生产相同的 ID 生成接口，通过测试配置提供隔离 worker，不用数据库自增替代。

项目模板仍保留原模板的通用 `INT/BIGINT` 与自增检查能力，便于非核心项目按批准方案使用。六大核心系统派生模型时必须关闭实体主键的自增属性，并以 [database/README.md](./database/README.md) 和对应系统专项文档为准。

### 9.1 服务本地通用行级变更历史（可选）

当系统专项设计明确采用通用审计模式时，可在本服务数据库建立 `business_change_record` 与 `business_change_version`。BCR 必须按明确的 Record Type 白名单启用，不得默认覆盖本库全部业务表。只有已确认需要审计、追责或历史留档的可变业务对象，以及被专项设计选中的审计 Child 才接入；当前业务值、索引、唯一约束、外键和状态机仍以结构化业务表为权威来源。该模式是业务变更历史，不是通用 Inbox/Outbox、中央审计库或 Event Sourcing 存储。

变更主表至少保存业务行类型/ID、聚合根类型/ID、同聚合父历史 ID、当前版本以及 `create_time/update_time`；版本明细保存明细 ID、主历史 ID、`version_no`、操作类型、`snapshot_json`、`create_by` 和 `create_time`。明细创建后不得修改，因此它明确例外于默认字段规则，不建立 `update_time`。

落模时必须同时满足：

- 系统专项文档必须列出完整 Record Type 白名单、可作为聚合根的类型、Child 到 Root 的固定映射以及明确排除项。新增表默认不接入 BCR；只有能说明具体审计查询、追责要求或历史恢复用途，并说明为何现有结构化业务版本或领域证据不足，才允许扩大白名单。
- 自身已经是一行一份且创建后完全不可变的 Acceptance/Decision/Ledger/Fact 等领域证据不重复写 BCR；由冻结结构化 Version 完整保留的附属明细，以及只在 Draft 内编辑、冻结后不可变且没有 Draft 审计要求的 Child，也不单独写 BCR。具有 Draft/审批/激活等可变生命周期且确有审计要求的 Version 头可以由系统专项白名单接入。`business_change_record`、`business_change_version`、`compensation_event`、`command_idempotency` 等技术表不得自审计。
- `(record_type,record_id)` 和 `(history_id,version_no)` 分别唯一，并为 `(aggregate_root_type,aggregate_root_id)` 和 `parent_history_id` 建立查询路径；父记录必须属于同一聚合。业务行身份、聚合归属和父子关系创建后不可覆盖。
- `version_no` 是聚合根版本，允许受审计 Child 跳号，不再使用第二个行内版本或依赖时间分组。任何聚合内写命令必须使 Root 的并发版本原子递增一次；Root 与白名单内实际发生需留档变化的记录写最终快照，并更新对应 `current_version`。白名单外 Child 不创建 BCR，也不为了形式完整写重复快照。系统专项设计可明确收敛 CAS Token，但不得把该例外静默推广到其他系统。
- `snapshot_json` 使用按 `record_type` 受控的 `_meta + data` Schema。`_meta` 至少含非空 `schemaVersion/triggerType/triggerId/reasonCode/integrityAlgorithm/integrityHash`；Integrity Hash 输入包含服务与 Schema 版本化 Domain Tag、记录/聚合/父级 Tuple、明细结构化字段以及排除 Hash 自身后的 `_meta + data`，统一使用 UTF-8 RFC 8785/JCS。每个采用该模式的服务必须冻结精确输入对象属性名与层级；`BIGINT` 编码为十进制字符串，`DECIMAL` 使用去无意义尾零且无指数的十进制字符串，时间使用六位微秒 UTC 字符串；禁止把 Snowflake ID 写为可能超过 IEEE-754 安全范围的 JSON Number。`create_by` 是操作者，不替代 Command、Event 或 System Run Trigger；系统变更使用非空正式 System Actor。
- `data` 只保存白名单化的业务行审计视图，不得使用拦截器或反射直接序列化 DataObject。严禁复制敏感原值、完整 Vault Token、Blind Index、密文、Secret、卡数据、文件正文以及创建/更新时间、乐观锁、Active Slot、Lease、重试计数等纯技术字段。敏感业务值只允许记录经分类批准的 Masked Value、Metadata Hash、Record Version 或稳定“已变更”标记。
- 业务更改、聚合版本、最终行快照、主记录指针和相关 `compensation_event` 必须在同一本地事务中提交。一次业务操作对同一行的多次 SQL 变化在 Repository 聚合入口收敛为一条最终快照。
- 删除父业务行时，必须在同一聚合版本为全部仍存活且已经接入 BCR 的后代追加 `DELETE`；按版本还原时只重建白名单覆盖的审计视图，忽略没有目标版本快照或最新操作为 `DELETE` 的记录，不能把它描述为完整业务聚合恢复。
- 创建后完全不可变的 History、Acceptance、Decision、Ledger/Journal 和其他领域证据表不重复写入通用历史。结构化 Version 的冻结明细不重复写 BCR；若其版本头仍有 Draft、审批、激活或替换等可变生命周期，只有系统专项白名单明确要求时才审计该头记录。现有专项状态历史保持合法，只有系统专项设计明确迁移时才替换；同一生命周期不并行维护两个权威历史。
- 历史明细的普通路径只允许追加。MVP 数据默认保留，不建设通用自动 Retention/Legal Hold、归档、匿名化或销毁任务；接入真实受监管生产数据前必须完成专项设计和法律/合规评审，届时不得绕过受控流程直接删除历史。

#### 9.1.1 CCT 专项 CAS 例外

Client & Contract 已批准把白名单内聚合根对应的 `business_change_record.current_version` 同时作为审计版本和唯一普通聚合 CAS Token，结构化业务 Root 不再机械保存第二个 `version`。CCT 外部命令携带 `expected_change_version`，Root BCR 以条件更新推进到 `next_version`；白名单内发生需留档变化的 Child 追加该 Root Version 的最终快照并更新版本指针，但 Child `current_version` 不是独立 CAS Token。白名单外 Child 只受 Root CAS 和自身结构化约束保护，不创建 BCR。多 Root 命令按 `(aggregate_root_type,aggregate_root_id)` 确定顺序锁定/CAS，每个 Root 独立拥有下一版本、History 与 Event `source_version`，共同 `trigger_id` 只做关联，任一冲突回滚整个本地事务。

该例外严格限定于 CCT 已批准的当前 37 表设计，不改变 Commerce、Payment Orchestration、Risk & Compliance、Finance 与 Platform Support 五套模型的并发策略；其他系统仍按自身专项文档决定是否保存结构化 Root `version`。CCT 只在 `compensation_event` 上保留独立技术 `lock_version`；`version_no/source_version/vault_record_version/hash_schema_version/schema_version` 继续保持各自业务或协议语义。

## 10. 类型与业务约束

- 金额使用 `Money` 数据域作为起点，当前模板映射为 `DECIMAL(24,8)`；禁止使用 `FLOAT` 或 `DOUBLE` 保存金额。
- 每个金额事实必须能确定 ISO 4217 币种，通常使用同一记录中的 `currency` 字段或不可歧义的父级事实。
- 百分比、汇率和费率统一以 `DECIMAL(18,10)` 作为建模起点；最终精度仍需根据字段业务范围收窄。
- 六大核心系统按 ISO 4217 minor unit 使用 `HALF_UP` 完成最终金额舍入；可解释尾差进入 Finance 的 `ROUNDING_ADJUSTMENT`，未知差异进入对账异常。
- 枚举和状态保存稳定业务编码，不保存 Java 枚举 ordinal。
- 瞬时时间字段统一使用 `DATETIME(6)` 保存 UTC，Java/MyBatis 映射为 `Instant`，不能依赖数据库会话的隐式时区；对外 API 使用带 offset 的 ISO 8601。仅无时区、无时刻语义的 date-only 事实使用 `DATE` 并映射为 Java/MyBatis `LocalDate`。CCT 当前只批准 `individual_profile.birth_date` 与 `organization_profile.incorporation_date` 使用派生 `localDate` 映射；该映射由 CCT 生成模型增加，共享 PDManer 模板保持字节级不变。
- 有效期统一使用半开区间 `[effective_from, effective_to)`，`effective_to IS NULL` 表示没有预定结束时间。
- 大文本、二进制和 JSON 字段必须有真实使用场景、大小边界和查询策略，不能作为逃避字段建模的容器。
- 先按业务系统、字段和使用场景完成数据分类，再决定结构化本地存储、Vault Token、不可逆 Hash/HMAC、Secrets Manager/KMS、对象存储或禁止采集；不得看到个人或财务字段就机械套用同一种存储方式。
- 已明确为可恢复非卡敏感值且完成业务 Payload Schema 审批的字段由中央 Vault 保存；事实拥有系统定义并校验业务 Schema、Canonical Bytes 与派生维度，Vault V1 只执行通用 Data Type/Token Mode/Purpose/大小/安全策略。业务模型必须标记数据分类、Purpose、Reveal 边界和留存要求，并保存必需 `*_vault_token`；当前 Vault V1 不返回 Blind Index/Key Version、Masked Value、Record Version 或 Metadata Hash，任何未来增加都需新的 Vault API/模型批准。不得同时保存明文、本地密文或密钥。
- 已有业务模型中的多个 Token 必须继续使用语义明确的字段前缀；不能让一组通用元数据模糊代表多个值。历史设计中完整 Token/Version/Metadata Hash 或 Blind Index Tuple 不代表当前 Vault V1 已提供对应运行能力，后续实现必须先重新对齐三表 API 权威。
- 当前 Vault V1 不建立 Reference Binding，也没有 Bind、Confirm、Copy 或 pre-bind API。Protect 在 Vault 单一本地事务中写入 Record、成功请求映射和访问事件后直接返回 Token；业务系统按自身获批模型保存 Token。此前 `PENDING_AFTER_COMMIT`、`VAULT_BIND_REFERENCE`、`SYNCHRONOUS_PRE_BIND`、Owner Confirm 和孤儿治理属于被[当前三表设计](../docs/superpowers/specs/2026-09-02-vault-database-design.md)取代的历史方案，不是 V1 强制默认值。
- Current V1 uses deployment-restricted private HTTP without application certificates, a required `X-Caller-Service` header and a static `caller_service + action + data_type + purpose` allowlist. This accepted MVP trust assumption prevents only accidental misuse: the Header is spoofable and is not mTLS, Workload Identity or other strong service authentication. Checkout Network Evidence still does not enter Vault.
- PAN、CVV/CVC、完整磁道数据和 PIN/PIN Block 不进入 Code-MoR 数据库或 Vault；Payment 只保存 Provider Token 与获准的卡片描述字段。Provider/API Credential 和私钥只保存 Secrets Manager/KMS 引用，大文件只保存 Platform `file_id + content_hash`。

数据域只是可复用的类型起点。具体字段仍需检查长度、精度、非空、默认值和业务语义，不能因为选择了数据域就跳过评审。

## 11. 索引、约束与关系

- 主键、业务唯一键和幂等键必须分别建模，不能用普通索引代替唯一约束。
- 跨系统文件引用统一使用 Platform Support 返回的 `file_id + content_hash`；业务用途由独立的 purpose/type 字段表达，不用 `file_ref`、`content_ref` 或任意外部 URL 代替稳定 ID。
- 每个存在异步发布或本地恢复任务的服务使用本库 `compensation_event`，与业务变化在同一事务登记；至少保存稳定 `event_key`、来源类型/ID/版本、事件类型、`schema_version`、不可变非敏感 Payload、`payload_hash`、执行状态、次数、下次执行时间、Claim Lease 与乐观锁版本，并以 `(source_type, source_id, source_version, event_type)` 保证同一事实只登记一次。成功记录允许删除，失败记录保留并告警；完整约束见 `COMPENSATION_EVENT_GUIDE.md`。当前 Vault V1 没有异步发布/恢复任务，也不建立 `compensation_event`。
- HTTP/内部命令幂等键通常使用非空 `caller_scope + operation_type + idempotency_key`；同键不同 `request_hash` 必须拒绝，不能只用可能跨调用方碰撞的裸 `caller_id`。当前 Vault V1 Protect 是明确例外：唯一命名空间精确为 `(caller_service,idempotency_key)`，`request_hash` 覆盖 Caller、Data Type、Token Mode、Canonical Value Bytes 与 Purpose，且表中不保存 `operation_type`。
- “同一 Scope 仅一条活动记录”使用规范化的非空 Scope Key/Hash 与 nullable `active_slot` 建立联合唯一约束：活动记录的 slot 固定为非空值，终态记录置空以保留多条历史。业务 Scope 的组成列不得直接以 `NULL` 充当通配符后依赖联合唯一约束拦截冲突；`NULL` 只允许用于这个明确设计的终态 slot。
- 可检索非卡敏感标识必须保存 opaque Token。当前 Vault V1 不提供 Blind Index、Masked Value、Record Version、Metadata Hash、Binding 或跨接收方复制能力；只有未来新的批准设计证明真实等值查询、展示、元数据并发/同步、跨系统校验或内容承诺消费者后，才可引入对应结构。V1 `STABLE` 模式仅按 `data_type + canonical value bytes` 通过服务端 Keyed HMAC 指纹在同一部署内收敛 Token；调用方拥有业务规范化。Token 和不可逆派生维度仍按可关联假名化或准标识数据治理，必须最小授权、访问审计、禁止任意导出并纳入未来 Retention/删除专项设计。业务库不得缓存 Reveal 明文或 Vault 密文；不可检索 Secret 只保存 Secrets Manager/KMS 引用。
- 业务变更历史必须保存不可变触发来源、原因和聚合版本。领域专项表可使用 `trigger_type + trigger_id + reason_code + aggregate_version` 结构化列；经批准的通用行级历史可把等价元数据保存到受控 `snapshot_json._meta`。`actor_id/create_by` 只表示操作者，不能替代 Command、Event 或系统任务来源；使用行快照的通用模式时，可通过比较相邻版本获得 `from_status/to_status`，不强制额外建立这两个物理列。
- 索引应由真实查询、排序和并发更新路径驱动，不为每个字段机械建索引。
- 联合索引字段顺序必须对应主要访问路径，并记录设计理由。
- 状态转换更新应携带版本或期望旧状态，并验证影响行数。
- 当前数据库内的强一致关系可以使用外键，但必须评估写入顺序、删除策略和迁移成本。
- 跨系统 ID 绝不能建立物理外键。
- Finance 的账务事实采用追加/补偿方式，不把历史分录设计为可覆盖记录。

## 12. 数据库连接与安全

- `*.pdma.json` 中不得保存真实数据库地址、用户名、密码、Token、SSH 配置或生产库标识。
- 项目模板的 `dbConn`、`dbConns` 和默认连接保持为空。
- 正向同步、反向解析和连接数据库属于外部状态操作，只有用户明确要求且目标环境经过确认后才能执行。
- 日常设计优先通过模型和生成的差异 SQL 评审，不让 PDManer 自动修改共享环境。
- 生成 SQL 中包含删除表、删除字段、数据重写或不可逆类型变更时，按破坏性操作处理，必须单独评审迁移和回滚方案。

## 13. 模型变更流程

一次数据库变更按以下顺序执行：

1. 确认业务事实属于当前项目，并先完成相关状态和生命周期设计。
2. 从当前项目模型修改实体、字段、关系、索引和注释。
3. 运行 PDManer 模型检查，解决所有失败项并评估警告。
4. 导出或比较 MySQL DDL，人工审查删除、默认值、非空、锁表和数据回填风险。
5. 更新一份当前完整 Schema DDL；如初始数据受影响，同步更新该服务唯一的初始化 DML，并记录人工执行与回滚顺序。
6. 同步更新 DataObject、Mapper XML/接口、Repository 和转换逻辑；DataObject 类型名不得追加 `DO` 后缀。
7. 使用 `UNIT_TEST_GUIDE.md` 规定的仓库本地 MySQL 验证 SQL 和完整链路。
8. 检查 PDManer、手工 DDL/DML、Java 映射和测试结果一致后才可声明完成。

## 14. Agent 操作规则

- 修改任何数据库相关工件前，先读取 `PROJECT_CONTEXT.md`、本文和涉及 Java 时的 `JAVA_PROJECT_STRUCTURE.md`。
- 新项目只能从项目内模板派生，不从零创建不兼容的 PDManer JSON。
- 编辑 `*.pdma.json` 时保留 PDManer ID 和引用完整性；不要通过无差别格式化重排整个已有业务模型。
- 不手工构造未知 PDManer 内部字段。不能确认格式时，优先在 PDManer 中完成结构编辑，再审查 JSON 差异。
- 不自动恢复已从项目模板删除的数据域、数据库目标或代码模板。
- 外部原始模板更新时只比较和选择性合并适用变化，不能覆盖项目模板或已有服务模型。

## 15. 验证清单

CCT 37 表、Commerce 14 表、Payment 29 表、Finance 84 表、Risk 71 表与 Platform Support 63 表 PDManer-only 阶段的生成器和模型自动项均已通过。Finance 的完整评审入口为[逻辑模型](./database/FINANCE_DATABASE.md)、[现实场景](./database/scenarios/FINANCE_REAL_WORLD_SCENARIOS.md)、[交易经济与会计规则精简计划](./plans/2026-08-26-finance-transaction-economics-accounting-rule-simplification-implementation-plan.md)、[Account Balance 合同成熟精简实施计划](./plans/2026-08-27-finance-account-balance-contract-maturity-simplification-implementation-plan.md)、[退款 Funding 移除实施记录](./plans/2026-08-27-finance-refund-funding-removal-implementation-plan.md)、[通用 Funding 模块删除设计](./plans/2026-08-27-finance-funding-module-removal-design.md)、[通用 Funding 模块删除实施记录](./plans/2026-08-27-finance-funding-module-removal-implementation-plan.md)、[保证金 Account 精简实施记录](./plans/2026-08-27-finance-security-deposit-account-simplification-implementation-plan.md) + [Provider 对账精简实施计划](./plans/2026-08-28-finance-provider-reconciliation-simplification-implementation-plan.md) + [Provider 三阶段账户实施计划](./plans/2026-08-28-finance-provider-three-stage-accounting-implementation-plan.md) + [手工出款精简设计](./plans/2026-08-28-finance-manual-payout-simplification-design.md) + [手工出款精简实施计划](./plans/2026-08-28-finance-manual-payout-simplification-implementation-plan.md)、[统一账户模型设计（Funding 部分已替代）](./plans/2026-08-26-finance-unified-account-model-design.md)、[Account Balance 合并计划（Funding 部分已替代）](./plans/2026-08-26-finance-account-balance-merge-implementation-plan.md)、[统一账户实施记录](./plans/2026-08-26-finance-unified-account-model-implementation-plan.md)、[Finance Entity 双账簿设计](./plans/2026-08-26-finance-entity-dual-ledger-design.md)、[Finance Entity 双账簿实施记录（Funding 部分已替代）](./plans/2026-08-26-finance-entity-dual-ledger-implementation-plan.md)、[Tax/Fiscal 23 表精简实施记录](./plans/2026-08-27-finance-tax-fiscal-23-table-simplification-implementation-plan.md)、[物理建模决策](../finance-service/docs/database/FINANCE_PHYSICAL_DECISIONS.md)与[生成模型](../finance-service/docs/database/finance.pdma.json)。Risk 的完整评审入口为[逻辑模型](./database/RISK_COMPLIANCE_DATABASE.md)、[现实场景](./database/scenarios/RISK_COMPLIANCE_REAL_WORLD_SCENARIOS.md)、[Limit 7 表平衡精简设计](./plans/2026-08-28-risk-limit-balanced-simplification-design.md)、[Limit 平衡精简实施计划](./plans/2026-08-28-risk-limit-balanced-simplification-implementation-plan.md)、[Decision Engine 19/8 平衡精简计划](./plans/2026-08-27-risk-decision-engine-balanced-simplification-implementation-plan.md) + [Dispute 平衡优化设计](./plans/2026-08-28-risk-dispute-balanced-optimization-design.md) + [Dispute 平衡优化实施计划](./plans/2026-08-28-risk-dispute-balanced-optimization-implementation-plan.md)、[DD 19 表精简计划](./plans/2026-08-27-risk-dd-simplification-implementation-plan.md)、[物理建模决策](../risk-compliance-service/docs/database/RISK_COMPLIANCE_PHYSICAL_DECISIONS.md)与[生成模型](../risk-compliance-service/docs/database/risk-compliance.pdma.json)。六套模型的 PDManer 4.9.3 应用内检查仍待人工执行。

Platform Support 的 57 表[数据库精简设计](../docs/superpowers/specs/2026-09-01-platform-support-database-simplification-design.md)与[实施记录](./plans/2026-09-01-platform-support-database-simplification-implementation-plan.md)是未受公共控制面影响领域的前序基线；当前完整验证入口还包括[统一公共 API 动态控制面设计](../docs/superpowers/specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md)、[公共控制面 PDManer 实施计划](../docs/superpowers/plans/2026-09-01-platform-public-api-control-plane-pdmaner.md)、[逻辑/物理基线](./database/PLATFORM_SUPPORT_DATABASE.md)、[现实场景](./database/scenarios/PLATFORM_SUPPORT_REAL_WORLD_SCENARIOS.md)、[物理建模决策](../platform-support-service/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md)、[生成模型](../platform-support-service/docs/database/platform-support.pdma.json)及对应的 Manifest/Oracle 自动门禁。当前生成事实为 63 表、822 字段、123 个唯一索引、99 个访问索引、107 条本库关系、5 条 1:1、7 个 Diagram Groups、31 条复合 FK、85 条具名 CHECK sidecar 契约和恰好 36 个 `*_hash` 字段。

Vault 的三表设计也已通过独立 Oracle 和确定性再生成门禁：3 表、30 字段、3 个非主键唯一索引、4 个访问索引、2 条本库 `n:1` 关系、0 条 1:1 关系和 1 个 `vault_core` Diagram Group。其 PDManer 4.9.3 应用内检查仍待人工执行，且没有 Java、Migration、物理数据库、KMS 集成/运行时认证或部署实现。

- [ ] 模型由项目内 PDManer 模板派生，文件可被 PDManer 4.9.3 或兼容版本打开。
- [ ] JSON 语法有效，内部 ID、数据域、基础类型和关系引用完整。
- [ ] 模型中没有 `PARTNER_ID`、Merchant 等错误术语或参考项目遗留域。
- [ ] 模型中没有真实数据库连接和凭据。
- [ ] 表和字段属于当前项目，没有跨库物理关系。
- [ ] 异步事实与恢复任务使用本地 `compensation_event`，没有通用 Inbox/Outbox 或中央补偿表；消费幂等约束落在本地业务事实或投影。
- [ ] 字段已先按所属系统和使用场景完成数据分类；可恢复非卡敏感值由事实拥有系统校验业务 Payload Schema，业务库保存必需 Vault Token。当前 Vault V1 不增加 Binding/Bind/Copy、Blind Index/Key Version、Masked Value、Record Version 或 Metadata Hash；`REFERENCE`/`STABLE` 与 Protect 请求幂等遵守三表设计。V1 默认保留、不自动销毁，真实受监管生产数据前完成 Retention/Legal Hold、共享 Stable Token 引用和受控销毁专项设计与评审；卡数据、密码、Secret、私钥和大文件遵守各自边界。
- [ ] 表、字段、索引和约束具有明确业务含义及注释。
- [ ] 所有物理表使用 `t_`，唯一索引使用 `uk_`，普通索引使用 `idx_`；中文显示名和逻辑 Catalog 未被技术前缀污染。
- [ ] 每张新增表和关键字段能对应具体写入、查询、约束或审计场景；无法说明删除影响的结构已删除或推迟。
- [ ] 复杂聚合已通过实际 Showcase 说明写入、当前查询、版本切换和历史追溯，未仅凭 ER 图或未来扩展性证明合理性。
- [ ] 业务字段结构明确；Vault Token、文件引用和 JSON 均有受控 Schema，没有成为逃避业务字段设计的黑盒。
- [ ] `current_*_id`、Active Slot、乐观锁、Version 表和 Content Hash 均满足第 7 节的使用条件，没有机械重复。
- [ ] 闭合枚举列出全部允许值，开放代码说明权威来源，Boolean/Slot 说明完整状态矩阵。
- [ ] 金额、币种、状态、时间、精度和并发字段符合业务要求。
- [ ] 若采用通用行级变更历史，已明确 Record Type/Root 白名单、Child 映射与排除项；结构化业务表仍为权威来源，聚合版本/父子归属/指针与唯一约束完整，`snapshot_json` 通过 Schema/白名单/Hash 校验且不含敏感、Vault 或纯技术字段，明细只含 `create_time` 并且追加后不可变。
- [ ] PDManer 模型、手工 Schema/DML SQL、DataObject/Mapper/Repository 已同步，且 DataObject 类型名没有 `DO` 后缀。
- [ ] Repository 和服务级 `auto` 使用真实测试数据库验证通过。
- [ ] 破坏性 DDL/数据变更 SQL 有明确的数据迁移、兼容和回滚设计。
