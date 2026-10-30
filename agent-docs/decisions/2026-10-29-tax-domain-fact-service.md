# tax 税务：领域层事实服务、持有方操作与人工处理边界的决定

- Decision status: Confirmed（2026-10-29 用户确认草稿全文，并同意按第 3 节同步修订被取代文档）
- Implementation status: Not started（`paytech/tax` 仍为空壳，只有 24 张表的 Schema 归属；acquiring 用固定零税 Mock，billing 只记 `TAX_SNAPSHOT` 引用）
- Deployment status: Not applicable
- Decision date: 2026-10-29
- Supersedes / Superseded sections: 见第 3 节；各被取代文档已于 2026-10-29 加注取代说明。
- 用户决定：2026-10-29 在 [design-discuss/tax](../../design-discuss/tax/design.md) 三轮讨论中确认。第一轮：Tax 属领域层，由持有方操作，PSP 不处理消费者销售税。第二轮：首批纳税主体只有 PayFac Partner 的 AE / SA / EG 主体，平台没有自己的纳税主体，只做技术提供；三国注册均未取得；申报缴纳由 Partner 财务处理，系统只是工具；其余六项按建议。第三轮：不确定且没有好方案的点降级为人工处理。
- 详细设计：[职责与设计](../../design-discuss/tax/design.md)（第 10 节人工处理边界、第 12 节需修订文档清单）、[逻辑数据模型](../../design-discuss/tax/database.md)（19 张表）、[流程与人工清单](../../design-discuss/tax/flows.md)、[验收案例](../../design-discuss/tax/cases.md)（48 项）、[地区包与三国通用要求](../../design-discuss/tax/regional-policies.md)、[UML](../../design-discuss/tax/uml.md)。画板 [tax.pen](../../design-discuss/tax/tax.pen) 仍是 2026-10-27 版本。
- 实现、验证与部署状态只见[当前台账](../CURRENT_IMPLEMENTATION.md)。

## 1. 背景

`mor_tax` 的 24 张表来自旧 Finance 单体的 Tax/Fiscal 23 表：辖区、机关、规则、税率、证据规则多组配置表，快照加税行，可变的应纳汇总，没有正式交易与报价的区分。它默认平台为 OE 运营税务，也没有业务模式的概念。

此后三项决定改变了前提。[2026-10-17 目标架构](./2026-10-17-target-architecture-v2.md) D03 / D08 把税务定为所有实例必装的领域层基座，领域层不得调流程层。[2026-10-19 billing 决定](./2026-10-19-billing-fact-engine.md)让税额作为 `SELLER_TAX` 行进入计费事实。[2026-10-23 PayFac 决定](./2026-10-23-platform-tech-provider-and-partner-payfac.md)确定平台是技术提供方，`TAX_RESPONSIBLE` 由业务模式决定，C1 下由 PayFac 的 OE 承担，PSP 下由 Client 承担。

## 2. 决定

### 定位与边界

1. **Tax 是领域层的税务事实服务。** 职责是对一笔已明确交易双方、标的和价格的交易确定税务处理，留下可解释的正式记录，并支撑单据、申报和税款清偿的追溯。不持有交易状态机，不选择卖方或 OE，不计算商业费率，不写 Journal，不维护税务余额表。
2. **服务对象是平台登记的纳税主体。** 首批只有 PayFac Partner 持有的 AE / SA / EG 主体。平台是纯技术提供方，不登记自身为纳税主体；平台向 PayFac 收取技术服务费的税务由平台财务在系统外处理。
3. **持有方操作，系统是工具。** 准入评估、申报冻结、缴款登记、人工登记均由纳税主体的持有方（PayFac Partner）在 Partner 控制台操作，`t_tax_market.managed_by` 首期恒为 `SUBJECT_HOLDER`。法定申报与缴纳由 Partner 财务在税局系统完成，系统回填回执。平台运营只读或协助，不代替提交。授权边界是 `tax_subject_party_id`，不是 OE 来源。
4. **Partner 写操作例外。** 与 AGENTS「Partner transaction access is read-only」冲突，本决定登记例外：Partner 对其持有主体作为 `TAX_RESPONSIBLE` 的税务事实拥有操作权，范围限于准入审批、人工登记、申报冻结与回执回填、缴款登记与证据。不扩展到订单、支付、退款、路由、MID 或账务。

### 业务模式

5. **`NOT_MANAGED` 出口。** `TAX_RESPONSIBLE` 在 `t_tax_market` 没有任何记录时，计算结果为 `NOT_MANAGED`：保存源 Owner 传入的自报税额（`tax_amount_source=DECLARED`），不判断、不登记、不申报，不阻断支付。有记录但 `collection_status=BLOCKED` 才阻断。`NOT_MANAGED` 不是免税，也不是零税率。
6. **按模式的处理。** C1 Partner OE × MoR：完整链路，以 OE 名义计算与开票。C2 Partner OE × PSP 与 C3 Direct Merchant：消费者销售为 `NOT_MANAGED`。PayFac 向 Client 收取的服务费首期不计算，Partner 财务按 billing 费用明细自行处理；`PLATFORM_SERVICE` 供应类型保留给后续。

### 协作方式

7. **报价同步，登记走事件。** acquiring 在支付准备时同步调用 CalculateTax，冻结不可变 calculation 的 ID 与金额。Tax 消费 acquiring 的 `OrderPaymentSucceeded` / `RefundSucceeded` 建立正式事实，按 `source_system + source_fact_type + source_fact_id + tax_scope_key` 幂等；事件内容与冻结计算冲突时进死信。Tax 不提供对外的正式登记写命令，也不接受其他领域服务代为登记。
8. **billing 不等 Tax。** billing 消费同一事件，直接按携带的冻结结果生成 `SELLER_TAX` 行并引用 calculation_id，不读 Tax、不等待登记。两者都派生自同一份不可变 calculation，Tax 登记失败只影响申报底稿。`SELLER_TAX` 按税组件逐行生成：付方为渠道，收方为 `TAX_AUTHORITY` 且收方 ID = 纳税主体 Party（已有索引），辖区与注册写入行的 `source_evidence` JSON（2026-10-29 首个需求阶段 2 S-09 修订：原定三个可索引证据列取消，因无查询消费者，子分类账核对按账本 + 币种进行）。`NOT_MANAGED` 不生成 `SELLER_TAX` 行。
9. **两阶段报价。** Client 建单时按 Client 声明的国家与税类先报价；消费者在 Hosted Checkout 提交资料后重算并绑定新的 `source_revision`。支付只能基于最新的 `READY` 计算。
10. **账务一致性用子分类账模式。** ledger 每账本每币种一个 `TAX_PAYABLE` 控制科目，不按注册或辖区拆科目；Tax 的申报底稿是其子分类账，期末按账本加币种汇总与控制科目核对，纳入关账检查。
11. **资金事件。** `TaxPaymentSubmitted` 与新增的 `TaxPaymentFailed` 供 reconciliation 建立或取消预期银行流水，后者与 payout 的 `PayoutExecutionFailed` 对称。`TaxPaymentSettled` 携带缴款币种与负债币种两组金额，不同时 billing 增加 `TAX_FX_DIFFERENCE` 行。新增 `TaxRefundReceived` 对应 billing 新事实类型 `TAX_REFUND_RECEIVED`。抵税额跨期转用没有现金流，不向 billing 发布事件。

### 人工处理边界

12. **三条不能降级的线。** 结账时算税必须自动；逐笔销售与退款的不可变事实必须由事件自动登记；人工决定必须记录操作者、时间、文件地址与摘要，且不可改。
13. **首期降级为人工的事项。** 采购进项（Partner 财务按 Client 发票登记 `MANUAL_INPUT`）、B2B 买方税号核验、B2B 税票（外部开具后回填，`issuance_mode=EXTERNAL`）、电子发票报送、多种税务处理的总金额退款分摊、汇差与税局错配、退税与抵税、服务费税、申报与缴款。首期自动化只有结账算税、事件登记、不含买方个人资料的 B2C 简化票据、底稿与申报版本生成。
14. **人工事实的约束。** 人工登记的事实以 `origin=MANUAL` 进入同一模型，经 Platform workflow 的 Maker-Checker 审批；按 D13，审批通过后执行时重新校验发票号唯一、RCM_INPUT 不超过 RCM_OUTPUT、期间未关闭。Partner 控制台提供"待人工处理"清单，清单非空时对应期间不能关闭。

### 模型

15. **19 张表的公共模型加有版本的地区规则包。** 准入、注册、规则包、不可变计算（头 / 行 / 组件 / 证据）、正式交易、申报底稿、单据与序列、期间、申报版本与栏目、缴款与分配与事件、抵税转用。地区差异放进经 Schema 校验的规则包，不按国家建表、不建万能 DSL 或脚本执行器。逐表字段与约束以 [database.md](../../design-discuss/tax/database.md) 为准。
16. **退款分摊。** 只带总金额的退款，原交易各行处理相同时按剩余可退 gross 比例分摊，尾差给剩余额最大的行；处理不同时拒绝并要求行级金额。退款基数用 `refund_basis_amount`，`quantity` 只表示真实数量。

### 修订（2026-10-29，首个需求阶段 1）

17. **首期由平台运营代操作。** 用户决定 Partner 控制台本期不做，税务页面只做在 Operations 控制台。因此首期 `t_tax_market.managed_by` 取 `PLATFORM_OPERATIONS`：准入、注册、人工登记、申报冻结与回执回填、缴款登记由平台运营在 Operations 控制台代 PayFac Partner 执行，按 Maker-Checker 留痕。法定申报与缴纳仍由 Partner 财务在税局系统完成，平台只录入其结果与证据。第 3 条"由持有方在 Partner 控制台操作"与第 4 条 Partner 写操作例外保留为后续目标，Partner 控制台交付时把对应主体切换为 `SUBJECT_HOLDER`。

## 3. 取代范围

2026-10-29 确认生效，下表各文档已加注取代说明。`paytech/tax` 现有 Schema 与代码在实现切片重建前仍是代码依据。

| 被取代的内容 | 取代方式 |
|---|---|
| [`FINANCE_DATABASE.md`](../database/FINANCE_DATABASE.md) §4.2 税务部分、§3.5 FIN-I17 / FIN-I22 Tax Snapshot 查询、§6.7 税务期间与申报、§6.8 Tax Payment 中的 Tax/Fiscal 23 表模型、快照语义与应纳汇总 | 由本决定第 7、15 条及 design-discuss/tax 的 19 张表取代；§6.3 共享 `BANK_CASH` 现金并发门禁的原则保留，执行方按 account / ledger V3 为 account |
| [Finance Tax/Fiscal 23 表精简实施记录](../plans/2026-08-27-finance-tax-fiscal-23-table-simplification-implementation-plan.md) | 历史记录，不再作为 Tax 模型依据 |
| `paytech/tax/docs/database/tax.pdma.json` 与 `tax-schema.sql` 的 24 张表 | 随实现切片重建为 19 张；未执行前现有 Schema 仍是代码依据；既有零税 Mock 不得作为合法税务事实导入 |
| [billing 决定](./2026-10-19-billing-fact-engine.md)第 10 条与需求 B 的 S11（`TAX_SNAPSHOT` 引用、"有快照读不到则失败等待"） | 按第 8 条改为引用 calculation_id、直接使用事件携带的冻结结果、逐组件出行；事实类型增加 `TAX_REFUND_RECEIVED`，组件增加 `TAX_FX_DIFFERENCE` |
| [account / ledger V3 决定](./2026-10-18-account-ledger-subledger-control-account.md)对税务科目未作规定的部分 | 按第 10 条补每账本每币种一个 `TAX_PAYABLE` 控制科目与子分类账核对 |
| [PayFac 决定](./2026-10-23-platform-tech-provider-and-partner-payfac.md) 9.3 U8 | 补充：纳税主体 = `TAX_RESPONSIBLE` 且已在 Tax 登记，未登记时 `NOT_MANAGED`；平台首期不是纳税主体 |
| [counterparty 决定](./2026-10-19-counterparty-repositioning.md)第 4 条"Customer 档案不是交易证据"未覆盖税务证据 | 增加按交易准备身份生成的不可变税务身份快照（首期）；受控渲染与 B2B 税号采集字段推到第二期 |
| 根 `AGENTS.md`「Partner transaction access is read-only」 | 增加第 4 条的税务操作例外，写法同 2026-10-27 入驻决定的 PayFac 例外 |
| [reconciliation 决定](./2026-10-20-reconciliation-facts-via-billing.md)切片 3 的 tax 事件清单 | 增加 `TaxPaymentFailed` |
| acquiring `OrderPaymentSucceeded` / `RefundSucceeded` 载荷中的 `financeTaxSnapshotId` | 替换为 calculation_id、冻结税额与 `tax_scope_key` |

## 4. 已知代价

- Partner 财务首期工作量大：进项、B2B 核验与开票、报送、申报缴款都是人工。B2B 量增长是第二期自动化的触发条件。
- 人工进项、外部票据与系统事实的一致性只能靠期末核对与控制科目比对，不能靠事务保证。
- billing、account / ledger、acquiring、counterparty、Platform workflow、Partner 控制台都要改，跨系统协调成本高。
- Partner 获得写操作，Partner 门户的权限模型需要按 `tax_subject_party_id` 扩展。
- `NOT_MANAGED` 意味着 PSP 下平台不对 Client 自报税额的正确性负责，Client 报错税不会被系统发现。

## 5. 不变的边界

- 事务公理：报价时的授权事实与核验结果在事务外取得，事务内只写本库；对外事件经补偿事件表发出。
- 原始个人资料只经 counterparty；Tax 只持有快照引用与批准的派生维度，不存地址、税号、UIN 或完整 IP。
- 外部调用（税号核验、电子发票、申报协议）经 channel-connector 的 SERVICE 出口。
- 国家、行政区、币种引用 Platform 参考数据，不另建全局目录；税类、税务制度、处理原因由 Tax 管理。
- 文件只存 `FileAddress`；无跨库外键；PDManer 为设计源、人工 SQL 规则不变。
- 本决定不构成税务意见。地区包上线前须由 Partner 的税务顾问复核，三国适用条件见 [regional-policies.md](../../design-discuss/tax/regional-policies.md) 第 6 节。

## 6. 后续

- 第 3 节的文档修订与 `AGENTS.md` 路由已于 2026-10-29 完成。
- 首个需求已登记：[结账算税与事件登记](../../requirements/20261029/tax-结账算税与事件登记/requirement.md)（阶段 1 评审稿）；退款与人工登记通道按其待确认 Q1、Q3 拆到后续需求。
- 待 Partner 确认：SA / EG 走非居民注册还是设本地主体；Client 与 OE 之间的合同性质。待 Partner 税务顾问核实：SA 非居民税务代表与担保、SA 非居民电子发票适用性。
- 待同步：`tax.pen` 画板与 UML 预览图重渲染。
