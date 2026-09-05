# Finance 模块化代码设计

- Decision status: Confirmed
- Implementation status: In progress
- Deployment status: Not deployed
- Decision date: 2026-09-04
- Superseded sections: 第 4 节原业务模块绝对零依赖限制已由 [`2026-09-05-business-module-collaboration-policy.md`](../decisions/2026-09-05-business-module-collaboration-policy.md) 取代

本文记录 Finance 的模块、表归属与实施门禁。当前实现与验证只以 [`CURRENT_IMPLEMENTATION.md`](../CURRENT_IMPLEMENTATION.md) 和 [`finance-service/README.md`](../../finance-service/README.md) 为准；本文不授权或代表完整 Java、DDL、数据库或 PDManer 已实施。

## 1. 基线与范围

本设计以 Finance 当前业务与数据库权威资产为依据。生成模型统计、实现、验证和部署状态只在 `CURRENT_IMPLEMENTATION.md` 维护；下方表归属用于代码模块映射，不从旧 Settlement 或历史 Funding 流程恢复设计。

权威来源：主工作区的 `agent-docs/database/FINANCE_DATABASE.md`、Finance 现实场景、`FINANCE_PHYSICAL_DECISIONS.md`、`DEVELOPMENT_CONVENTIONS.md`、`JAVA_PROJECT_STRUCTURE.md` 与 `COMPENSATION_EVENT_GUIDE.md`。本文不替代这些资产中的金额公式、状态机、来源与会计控制。

## 2. 工程结构

一个 Finance 数据库、一个运行进程、一个事务管理器。业务模块为内部 JAR，不是独立服务。

```text
finance-service/
├── pom.xml
├── docs/database/
├── paytech-finance-api/
├── paytech-finance-accounting/
├── paytech-finance-treasury/
├── paytech-finance-tax/
├── paytech-finance-web/
└── paytech-finance-test/
```

以上六个 Maven 模块已经按统一 Artifact 命名初始化；这只表示工程骨架和当前余额查询切片存在，不代表完整 Finance 服务已经实现。

- Accounting：经济计算、账户、记账、余额与关账。
- Treasury：Provider 清算、银行资料及对账、手工出款。
- Tax：税务计算、Fiscal 单据、申报及缴税业务记录。
- Web：唯一启动入口；`ext/rest`、`ext/mq`、`ext/job` 适配协议，`service/processor` 编排用例及事务。
- API：外部 HTTP 契约，不是共享数据库对象库。
- Test：真实业务链路、并发及架构边界测试。

不新增 Application、Common、Balance、Funding 工程。按开发闭环逐步创建，不预建占位目录、接口和抽象。

## 3. 表归属

以下名称省略统一 `t_` 前缀。每张表只有一个持久化归属，关系均按应用层逻辑关系理解；不得据此新增物理 `FOREIGN KEY` 或 `CHECK`，也不得开放跨模块 SQL。既有模型或物理库的迁移状态只见当前状态台账。

### 3.1 Accounting：37 表

- 基础 9 表：`currency_spec`、`chart_of_accounts`、`chart_account`、`finance_entity`、`ledger_book`、`account`、`fiscal_calendar`、`accounting_period`、`book_sequence`。
- 经济结果 3 表：`transaction_economics`、`transaction_economic_component`、`transaction_economic_comp_link`。
- Provider 成本 6 表：`provider_cost_plan`、`provider_cost_plan_ver`、`provider_cost_scope`、`provider_cost_component`、`provider_cost_confirm`、`provider_cost_alloc`。
- 记账及调整 10 表：`accounting_rule_bundle`、`accounting_rule_bundle_ver`、`accounting_rule_line`、`accounting_instruction`、`instruction_line`、`journal_entry`、`journal_line`、`manual_adjustment`、`manual_adjustment_line`、`adjustment_approval`。
- 期间及关账 9 表：`ledger_period_balance`、`trial_balance_run`、`trial_balance_line`、`trial_balance_exception`、`period_close`、`period_close_book`、`close_step`、`close_approval`、`close_account_rule`。

T+N、保证金、追偿使用 Accounting 现有 Account/Journal，不恢复已删除工作流。Provider 实际成本证据可由 Treasury 提供，但经济确认及分配写入由 Accounting 完成。

### 3.2 Treasury：22 表

- Provider 7 表：`provider_account`、`provider_open_item`、`provider_debit_item`、`duplicate_capture_item`、`provider_settle_batch`、`provider_settle_item`、`provider_transaction_match`。
- 银行及对账 8 表：`cash_account`、`cash_account_binding`、`cash_account_binding_ver`、`bank_statement`、`bank_statement_item`、`bank_recon_match`、`recon_exception`、`recon_resolution`。
- 手工出款 7 表：`beneficiary_account`、`finance_restriction_proj`、`payout`、`payout_approval`、`payout_execution`、`payout_evidence_ref`、`payout_event`。

Provider/Cash Account 是业务资料；其账面余额只存在 Accounting 的 Account。Treasury 不保存第二份权威余额。

### 3.3 Tax：23 表

- 基础规则 11 表：`tax_jurisdiction`、`tax_authority`、`tax_registration`、`tax_category`、`tax_category_mapping`、`tax_treatment`、`tax_rule`、`place_of_supply_rule`、`tax_rate`、`tax_evidence_rule`、`tax_exchange_rate`。
- 计算 3 表：`tax_snapshot`、`tax_evidence`、`tax_line`。
- Fiscal 3 表：`fiscal_number_sequence`、`fiscal_record`、`fiscal_record_line`。
- 申报缴税 6 表：`tax_period`、`tax_liability_summary`、`tax_return`、`tax_return_line`、`tax_payment`、`tax_payment_event`。

Tax Payment 不强行映射为 Client/Partner Payout。Tax 拥有税款义务及支付记录，Treasury 提供银行匹配，Accounting 负责税款会计处理。

### 3.4 服务级技术表：2 表

`compensation_event`、`command_idempotency` 由 Web 服务级基础设施统一接入；补偿持久化复用 Foundation，命令幂等经专用内部能力访问。Processor 不直接调用 Mapper，业务模块不反向依赖 Web，也不各建一张补偿表。

## 4. 依赖与公开能力

`paytech-finance-web` 依赖 API 及三个业务模块，`paytech-finance-test` 依赖 Web。Accounting、Treasury 和 Tax 默认不建立直接 Maven 依赖；只有稳定、可复用的业务能力满足非跨模块用例编排、长期单向和无环门禁时，才允许一个业务模块建立单向依赖。获准依赖只能指向 Owner 发布的窄 Service/Domain Service 接口及 Domain / Application Command / Result。任一条件不满足，或形成双向/循环依赖时，由 `paytech-finance-web` Processor 编排。`paytech-finance-api` 只承载对外 HTTP 契约，不用于共享内部业务对象或消除循环依赖。

业务模块内沿用 `domain`、`domainservice/service`、`domainservice/repository`、`core/dal` 和按需 `integration`。所有业务 Service 和 Domain Service 使用 `interface + *Impl`；获准的跨模块调用方只依赖 Owner 发布的 Service/Domain Service 接口及 Domain / Application Command / Result，不依赖 `*Impl`、未发布的内部 Domain Service、DataObject、Repository、Mapper、表或通用 CRUD。禁止跨模块 SQL、JOIN、视图和存储过程绕过归属。

- Accounting 公开经济计算、余额查询、受控过账、资金锁定/释放、成熟、试算与关账能力。
- Treasury 公开报表登记、匹配、受益人校验、出款申请/审批/执行及凭证能力。
- Tax 公开税额快照、单据、税负汇总、申报与缴税能力。

接口按业务动作设计，不开放任意 `updateBalance` 或 `updateStatus`。上述能力不要求一能力一 Service 类。

## 5. 事务协议

复杂用例由 `paytech-finance-web` Processor 使用一个本地 TransactionTemplate。涉及多 Owner 写入、整体幂等/失败/补偿语义或双向能力需求时必须提升到该层；模块写能力参与当前事务，禁止隐式独立提交、REQUIRES_NEW 或线程切换拆散原子范围。必要写能力检查事务存在。简单查询不强制增加空 Processor。

必须保护的原子范围：

| 用例 | 原子内容 |
|---|---|
| 过账 | 指令状态、Journal、序列、Account 投影及必要补偿登记 |
| 支付/退款事实入账 | 对应经济结果、适用 Provider 明细、Component Link、指令及 Journal |
| 平台服务费 | OE/FE 两本账分别平衡且同事务提交 |
| 成熟 | 来源幂等、适用追偿抵扣、Client/Partner 共同成熟 Journal |
| 申请出款 | 申请、AVAILABLE→LOCKED、状态事件及命令幂等 |
| 创建 Execution | 执行记录、锁内现金容量校验及相应状态事件 |
| 最终银行匹配 | 匹配确认、业务状态、Journal 和余额更新 |
| 取消出款 | 取消条件、状态、LOCKED→AVAILABLE 及事件 |
| 缴税清算 | 银行匹配、税款状态及 Journal |

完整文件导入、整段关账不放进一个长事务。外部 HTTP、上传及人工银行动作不属于可回滚范围。Risk 四检查点维持当前合同，取得结果后必须在本地事务内复验来源版本、Scope、限制与当前账户；不能复用旧检查点代替新评估。

## 6. 统一现金容量门禁

应用层组合 Accounting 的 BANK_CASH 余额/锁、Treasury 活动 Payout Execution 占用、Tax 活动 Tax Payment 占用。

可承诺现金仅派生：银行账面余额减两类活动占用。不新增 Funding 表或现金余额副本。UNKNOWN 保留占用；释放必须符合权威未扣款或最终清算契约。

所有新增、释放、转移占用及相关银行 Journal 遵守同一账户锁协议。锁后聚合不能使用旧快照：实现时明确隔离级别、读取方式与写者序列化协议，用双连接并发测试证明，不仅依赖一条 FOR UPDATE。

真实外部扣款不得因容量不足而漏记；如实入账后阻断后续新承诺并进入异常处理。现金容量不是银行真实扣款事实的准入条件。

## 7. 锁与关账：实施前必过门禁

不能把“先锁 BANK_CASH”或“Account ID 升序”当作完整全局锁顺序。现有文档分别规定业务根、Instruction、Book/Period、Sequence 和 Account 局部顺序，需合并成无反向取锁的用例矩阵。

实现前列明每个写用例的资源集合、锁次序、版本复验和失败行为，覆盖双账簿、重试换银行账户、退款/成熟、出款/缴税、普通过账/关账。准备阶段解析范围；锁后发现范围变化则退出重试或拒绝，不反向临时补锁。不在锁内做远程调用。本文不声称该矩阵已经完成或通过并发验证。

关账由应用层协调，Accounting 拥有流程事实，Treasury 提供对账控制结果，服务级基础设施提供相关待处理/冲突检查。先 CLOSING，分阶段检查和结转，最后短事务复验水位、控制证据、Final Trial 与审批后 CLOSED。需界定哪些变化使检查失效及如何检测，不能仅检查 Journal 水位，也不能因无关技术任务而永久阻断所有期间。

## 8. 两处冲突的处置方向

### 8.1 补偿契约

最新 Foundation 使用 event_key/event_type/sub_event_type，Payload 为可选最小参数；Finance 旧模型仍含执行类型、Source/Schema/Hash 等字段。实施前单独对齐 manifest、policy、模型、文档与测试，不能只改 Java。

任务成功可删除，因此永久幂等、来源版本及事件证据保留在业务事实中。逐个出站事件验证可按稳定身份从不可变事实重建同一内容；不能读取可变根的最新值重建旧事件。任何缺少重建证据的事件先补齐明确契约，不用新通用 Outbox 掩盖问题。

### 8.2 手工缴税登记

当前文档同时规定“记录已在外部完成的操作”和“创建时必须通过事前资金门禁”，两者不可原样组合。

2026-09-05 已确认保留手工模式，并区分事前容量检查与事后事实登记：前者控制本系统新增承诺，后者保留经过验证的真实外部证据，不因资金不足丢弃；不满足正常归属、金额、活动槽条件的事实进入银行对账异常，不能强行创建违反现有约束的 Tax Payment 或直接结清税负。

这不是自动缴税授权。现有 `bank_statement_item + recon_exception` 能保留异常事实，但尚不能无损完成“无前置 Tax Payment 的真实扣款”从暂记到具体 Tax Return/Tax Payable 结清的完整链路；精确缺口和实施顺序见 [Finance 手工缴税对账计划](../../docs/superpowers/plans/2026-09-05-finance-manual-tax-reconciliation.md)。本轮不虚构模型能力。

## 9. 开发顺序及验收

第一批只做账务内核闭环：基础初始化、合法指令过账、查询、幂等、余额和期间控制。随后接经济事实、成熟及对账，再完整手工出款；交易必需税务能力随交易闭环接入，不能默认零税或把工程分期理解为上线豁免。

实施前完成：

1. 84 表归属无遗漏、无重复。
2. 锁矩阵及隔离级别协议；现金容量和关账证据失效规则。
3. 补偿与缴税冲突的权威资产增量及专项测试。
4. Maven 依赖图无环；架构测试阻止未获准的单向依赖，以及跨模块 `*Impl`、DataObject、Repository、Mapper、SQL 越权。
5. 并发超额、重复事件、同键异内容、事务中途失败、UNKNOWN、迟到 Debit、跨期间、双账簿单边失败反例。

Java 测试执行遵循实施时最新规范：当前为仓库 local-environment 的隔离测试数据和真实协议 Simulator，不恢复旧文档中的 Testcontainers 或 Flyway。数据库 SQL 需另行授权并手工审核执行。目录划分不是运行正确性的验证；本轮没有运行 Java 或资金并发测试。
