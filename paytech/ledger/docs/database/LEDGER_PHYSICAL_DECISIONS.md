# Finance 物理数据库决策

> **2026-09-11 模型切片：** 当前物理模型采用 [OE / Client / Pricing 四系统决定](../../../agent-docs/decisions/2026-09-11-cct-commerce-oe-pricing-model.md)。本页历史正文中的数字 OE、Assignment、独立 Acceptance、Client 多 OE 合同与 Agreement-OE 结算条款由该决定精确取代；本页摘要描述当前模型产物，运行代码、SQL 执行及待迁移状态仅见 [当前实现台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md)。Risk 本期不动。

最后更新：2026-09-05
状态：第一版 PDManer 已确定性生成并通过自动验证；PDManer 4.9.3 应用内人工评审待完成

> **2026-09-05 跨主题对齐：** 本文件仍精确描述当前生成模型，但不再单独拥有 Vault、`compensation_event`、物理 CHECK/FK 或测试基础设施规范。与当前 [文档治理决策](../../../agent-docs/decisions/2026-09-05-document-governance-and-conflict-resolution.md) 冲突的旧字段/流程属于待迁移现状，不得用于新增实现；进度与完成判据见 [当前实现台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md)。

## 1. 交付基线

| 项目             |                                                                当前值 |
|----------------|-------------------------------------------------------------------:|
| 物理表            |                                                                 84 |
| 字段             |                                                              1,109 |
| 唯一索引           |                                                                145 |
| 普通访问索引         |                                                                155 |
| 本库关系           |                                                                160 |
| 其中 1:1 关系      |                                                                 22 |
| Diagram Group  |                                                                  6 |
| 共享模板 SHA-256   | `51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540` |
| 模型文件 SHA-256   | `0ce10fb68b98492017b2ba60bc773d7d2828f4b62c01b45153c757bf8f9c72df` |
| 测试独立结构 SHA-256 | `3ea1d613278f7e4be42860048596d607b134b5a85572cbce8e0684ca99504255` |

模型文件：[finance.pdma.json](./finance.pdma.json)

本次 Trial Balance
与关账门禁收紧的范围、约束和验证记录见 [实施记录](../../../agent-docs/plans/2026-08-27-finance-trial-balance-close-hardening-implementation-plan.md)。

本次 Tax/Fiscal 23 表精简的范围、Rule/Return
生命周期和验证记录见 [实施记录](../../../agent-docs/plans/2026-08-27-finance-tax-fiscal-23-table-simplification-implementation-plan.md)。

退款成功后入账、退款不预占与合同成熟并发语义见 [实施记录](../../../agent-docs/plans/2026-08-27-finance-refund-funding-removal-implementation-plan.md)
；通用 Funding 四表的删除、直接 Account
现金门禁与验证结果见 [删除设计](../../../agent-docs/plans/2026-08-27-finance-funding-module-removal-design.md)
和 [实施记录](../../../agent-docs/plans/2026-08-27-finance-funding-module-removal-implementation-plan.md)。

本次保证金与追偿简化删除五张 Reserve/Recovery 流程表，将最低预存保证金和追偿应收收敛为 Account +
Journal，详见 [实施记录](../../../agent-docs/plans/2026-08-27-finance-security-deposit-account-simplification-implementation-plan.md)。

本次 Channel
两层对账简化删除重复的批次级净现金中间表，以本地冻结交易引用完成渠道交易匹配，并让银行匹配直接引用清算批次，详见 [实施记录](../../../agent-docs/plans/2026-08-28-finance-provider-reconciliation-simplification-implementation-plan.md)。

Channel 三阶段账户把交易形成、最终 Settlement/Remittance 批次完整和银行确认分别落入待清算账户、结算账户与 `BANK_CASH`，并将
Channel Fee
独立计提，详见 [实施计划](../../../agent-docs/plans/2026-08-28-finance-provider-three-stage-accounting-implementation-plan.md)。

Manual Payout 以七张表替代旧自动 Profile/Run/Candidate/Obligation、取消根和 Payment/Attempt 聚合；批准边界、四次
RCO-I09、Execution/Bank Match
与会计路径见 [批准设计](../../../agent-docs/plans/2026-08-28-finance-manual-payout-simplification-design.md)
和 [实施计划](../../../agent-docs/plans/2026-08-28-finance-manual-payout-simplification-implementation-plan.md)。

本基线只授权数据库设计产物，不创建审核后的手工 Schema SQL、DO、Mapper、Repository、Java 服务、数据库实例、测试环境或基础设施。项目不使用 Flyway、Liquibase 或应用启动时 Schema 迁移。

## 2. 六个物理边界

| 图组                | 主表数 | 职责                                                                                                           |
|-------------------|----:|--------------------------------------------------------------------------------------------------------------|
| 账簿、账户与关账          |  28 | Finance Entity、多级科目、账簿、统一账户、规则、Instruction、Journal、期间科目余额、人工调整、试算与逐账簿期间关账                                    |
| 税务与财政事实           |  23 | 多国税务规则、Tax Snapshot、Fiscal Record、Tax Return 和 Tax Payment；Rule 与 Return 各以一张精确版本表承载审批生命周期                   |
| 交易经济与渠道成本         |   9 | PAYMENT/REFUND/DUPLICATE_CAPTURE/DUPLICATE_COMPENSATION/DISPUTE/COST_ADJUSTMENT 的不可变经济结果与预计/实际 Channel Cost |
| Channel 清算、现金与对账 |  15 | Channel 应收/扣款、清算文件、渠道交易匹配、银行流水和银行匹配                                                                         |
| Manual Payout     |   7 | Beneficiary、限制投影、Payout、Approval、Execution、Evidence 与连续 Event；Bank Match 复用现金对账组                             |
| 可靠执行与命令幂等         |   2 | 本地 Compensation Event 和同步命令幂等                                                                                |

每张表恰好属于一个主图组；跨组父表只作为关系阅读锚点显示。每条本库关系只在子表所属图组绘制一次。

## 3. 为什么是 84 张表

表数量不是优化目标，职责和不变量才是。本模型保留不能安全合并的独立事实：

- 原 `t_ledger_account + t_subledger_account` 收敛为一张 `t_account`，当前余额直接并入账户；逐交易余额批次、通用到期策略与重复出款锁定明细由共同成熟时刻、Account
  Journal 与 Payout 直接锁定账户取代；Tax Rule 与 Tax Return 各自收敛为精确版本行；退款可退金额由 Commerce 与 Payment
  各自控制；保证金和追偿不再建 Case/Fund/Move/Application 流程表；Channel 净现金事实直接由清算批次承载；Manual Payout 删除
  Profile/Run/Candidate/Obligation、独立 Request/Cancel 和 Payment/Attempt 根，当前为 84 表；

- Tax Rule、Tax Snapshot、Fiscal Record、Tax Return 分别代表配置、计算证据、法定交易事实和申报事实；
- Transaction Economics 解释交易经济，Accounting Instruction 是记账输入，Journal 是唯一会计事实；
- Header、Line、Movement、Event、Approval 的生命周期、唯一键和不可变性不同；
- Transaction Economics Component 解释权益来源，Account 表达当前状态；Payout/Execution/Tax Payment 状态、付款事件、Bank
  Recon Match 和 Journal 表达付款承诺及审计；
- Channel Settlement 证明渠道清算，Bank Statement/Reconciliation 才证明现金实际到账或扣款；
- Payout 是人工发起并锁定 Owner 应付的义务，Execution 是一次真实银行提交；
- 一个 Accounting Period 可覆盖同一 OE 的多个币种账簿，`t_period_close_book` 必须逐账簿冻结 Journal
  Sequence、最终试算和结转指令，不能把单一 Book 引用塞回关账 Root。

同时没有建立通用 BCR/History、Inbox/Outbox、Workflow、自动银行付款、跨 OE Cash Pool、Retention Projection 或跨库 Current
Projection。

## 4. 关键边界决策

### 4.1 Tax Rule/Return 直接保存精确版本，其余 Root 只保留一个当前权威

`t_tax_rule` 以 `rule_code + rule_ver` 唯一，`t_tax_return` 以 `return_no + return_ver` 唯一；两者每行都是精确业务版本，Maker-Checker
决定直接保存在该行，不再维护 Root、`current_ver_id` 或独立 Approval 表。DRAFT 的 Content Hash 与 Checker Tuple 为空；提交审批时冻结
Hash，离开 DRAFT 后适用范围、申报金额等被审批业务内容和子行禁止修改，审批、发布、文件与回执证据仅按合法生命周期推进；业务内容变化只能追加新版本。Tax
Return 首版的 `amends_return_id` 为空，后续版本必须引用同业务编号下此前已提交版本。

Channel Cost Plan、Accounting Rule Bundle 与 Cash Binding 仍由 Root 的 `current_ver_id` 唯一指向当前版本；除 DRAFT 外的
Root 必须具有当前指针。Beneficiary 是一张带 `version` 与本行 Maker/Checker 生命周期的可变表：DRAFT
可修改，提交后身份字段冻结，任何银行/Vault 身份变化创建新 ID。Payout 不维护 Profile Version 或 Current Execution Pointer；顺序
Execution 使用 `(payout_id,attempt_no)` 唯一，`active_slot=1/NULL` 保证每个 Payout 最多一个活动执行。

### 4.2 Channel 两层对账推进三阶段账户

- `t_channel_open_item` 表示 PAYMENT 或 DUPLICATE_CAPTURE 形成的逐笔待清算应收；来源 Journal 以相同 Gross 记入同
  Channel Account Owner 的 `CHANNEL_UNSETTLED_RECEIVABLE`。同一 PAYMENT Transaction Economics 可按不同 Channel
  Transaction Ref 形成多条 Open Item，以兼容未来多 Capture，其金额合计必须等于交易经济结果 Gross；DUPLICATE_CAPTURE 仍由异常明细
  UK 保证一对一；
- `t_channel_debit_item` 表示 REFUND、DUPLICATE_COMPENSATION 或 DISPUTE 形成的逐笔待清算应付，每个 Transaction Economics
  只能形成一条 Debit Item，来源 Journal 记入 `CHANNEL_UNSETTLED_PAYABLE`；
- DUPLICATE_CAPTURE Transaction Economics 指向原 PAYMENT；DUPLICATE_COMPENSATION 直接指向同一 `duplicate_capture_item_id`
  的 DUPLICATE_CAPTURE，应用事务校验两跳最终回到原 PAYMENT 并禁止循环；
- `t_channel_settle_batch` 必须冻结 Channel 权威最终 Settlement/Remittance 明细，Preliminary Transaction Report
  不能冒充；使用正绝对值记录 Gross、Fee、Debit、Net，并由 `cash_direction=CREDIT/DEBIT/NONE` 表示净现金方向；
- `t_channel_settle_batch.status=VALIDATED` 只说明文件结构、币种和金额恒等式校验通过，不代表逐笔交易已经对平；
- `t_channel_transaction_match` 使用 Finance 已在 Open/Debit Item 冻结的 Channel Transaction Reference，把
  PAYMENT/DUPLICATE_CAPTURE/REFUND/DISPUTE 原始行做多对多匹配，正常流程不在线查询 Payment；FEE/ADJUSTMENT 不创建虚假交易
  Match，代表渠道成本时由 Channel Cost Confirm/Alloc 完整处置，否则打开 Recon Exception；未知或存在歧义的原始行同样必须打开异常，不能猜测归属。单条
  Match 只是证据，不推进 Open/Debit Item 状态，也不形成银行现金；
- 交易对账完整性按最终批次实时派生：批次必须 VALIDATED、业务交易原始行的不可变 Transaction Match 分配完整覆盖、FEE/ADJUSTMENT
  行已由 Cost Confirm/Alloc 或已审批异常处置完整覆盖、实际 Channel Cost Confirmation 完整且不存在阻断性开放异常；不新增第二套批次对账状态。门禁通过后以
  `CHANNEL_BATCH_SETTLEMENT:<batch_id>` 幂等创建唯一聚合 Journal，并在同一事务推进 Open/Debit Item 的
  `settled_amount/outstanding_amount/status` 与 Account 投影；批次聚合 Journal POSTED 后禁止追加改变 Gross/Fee/Debit 的
  Match、Cost 或 Resolution；
- PaymentSucceeded 的 Gross 待清算应收不得被预计 Channel Cost 直接冲减。预计费用记为
  `Dr CHANNEL_FEE_EXPENSE / Cr CHANNEL_FEE_ACCRUAL`；实际差异只调整 Expense 与 Accrual。批次阶段只借记实际
  `CHANNEL_FEE_ACCRUAL`，不得重复确认全额费用；
- 净 CREDIT Batch 使用
  `Dr CHANNEL_UNSETTLED_PAYABLE + Dr CHANNEL_FEE_ACCRUAL + Dr CHANNEL_SETTLEMENT_RECEIVABLE / Cr CHANNEL_UNSETTLED_RECEIVABLE`
  ；净 DEBIT 使用
  `Dr CHANNEL_UNSETTLED_PAYABLE + Dr CHANNEL_FEE_ACCRUAL / Cr CHANNEL_UNSETTLED_RECEIVABLE + Cr CHANNEL_SETTLEMENT_PAYABLE`
  ；NONE 不建立结算账户余额。所有抵销必须在同一 Channel Account/MID/OE/Currency/Batch 内，且合同存在当前可执行抵销权、最终批次证明实际净额结算意图，否则
  V1 Fail-Closed 为不支持的 Gross Multi-Leg；
- `t_bank_recon_match` 的 Channel 目标直接使用 `channel_batch_id` 指向 `t_channel_settle_batch`。PROPOSED/REJECTED 不产生
  Journal；只有 CONFIRMED 以 `CHANNEL_BANK_MATCH:<bank_recon_match_id>` 按 `matched_amount` 幂等推进：Credit 为
  `Dr BANK_CASH / Cr CHANNEL_SETTLEMENT_RECEIVABLE`，Debit 为 `Dr CHANNEL_SETTLEMENT_PAYABLE / Cr BANK_CASH`。累计
  CONFIRMED 不得超过 Batch `net_amount`，NONE Batch 禁止 Channel Bank Match；Bank Match 不再推进 Open/Debit Item；
- Payout Bank Match 使用 `match_type=PAYOUT_DEBIT`，严格引用
  `payout_execution_id + bank_item_id + cash_account_binding_ver_id`，并以
  `payout_component_type=BENEFICIARY_TRANSFER/BANK_FEE/COMBINED` 解释扣款构成；Binding Purpose 必须为 `PAYOUT`，Bank Item
  必须是同 Cash Account、币种和 DEBIT 方向。拆分行分别闭合 `beneficiary_amount` 与 `fee_amount`；COMBINED 必须以相同 Bank
  Payment Ref 或权威银行证据绑定同一 Execution，不能用偶然等额的无关 Debit。完整确认要求 CONFIRMED Match 与已批准差异处置共同闭合
  `cash_required_amount`，Match 可被 `t_payout_event.bank_recon_match_id` 导航；
- `t_cash_account_binding.account_purpose` 只允许 `CHANNEL_SETTLEMENT/PAYOUT/TAX`；退款通过 Channel Debit Item
  进入渠道净清算，不建立独立 REFUND 银行用途；
- Channel 负向事实先进入 `CHANNEL_UNSETTLED_PAYABLE`，批次完整后进入 `CHANNEL_SETTLEMENT_PAYABLE`，银行 Debit 确认后才减少
  `BANK_CASH`；不把已结清应收写成负资产，也不恢复通用 `CASH_IN_TRANSIT/CASH_OUT_TRANSIT`；
- V1 不支持同一批次独立 Gross Credit 与 Fee Debit 多现金腿；真实渠道需要时再扩展明确的 Cash Leg 模型。

### 4.3 实际 Channel Cost 使用本库事实锚

`t_channel_cost_confirm.channel_settle_item_id` 与原始 `t_channel_settle_item` 真正 1:1。Channel、MID、OE、币种、批次号和项目号通过
Item → Batch → Channel Account 唯一解析，不在 Cost Confirmation 重复保存，也不会因不同 MID 复用外部编号而误冲突。

渠道成本合同引用属于具体 `t_channel_cost_scope`，而不是整版 Plan Version；Scope 通过完整结构化维度复合 UK 去重，不保存可漂移的
`scope_hash`。同一 Payment `mid_id + currency` 只能建立一个 Finance Channel Account，创建时还必须逐值核验 Payment 返回的
Channel/OE，避免全局 MID 被错误分叉。

### 4.4 退款不恢复 Payment Refund Attempt

Payment 已把旧 Refund Attempt 合并到 Channel Refund。Finance 以
`channel_refund_id + channel_transaction_ref_id + channel_debit_id + reference_type + external_transaction_id`
追溯退款扣款，不保存 `channel_refund_attempt_id`。

### 4.5 Ledger Book 使用类型化主体，不增加双 FK

`t_finance_entity` 只保存 Finance 人工维护的 `entity_code + legal_party_id + display_name + status`。`legal_party_id`
唯一映射 CCT Party，状态只允许 `ACTIVE/CLOSED`；它不复制法律资料，也不增加 Version、Hash、审批、Operating Entity 或来源系统字段。

`t_ledger_book` 和 `t_fiscal_calendar` 使用 `entity_type + entity_id`，类型只允许 `OPERATING_ENTITY/FINANCE_ENTITY`
；账簿唯一键为 `uk_ledger_book_entity(entity_type,entity_id,currency)`，日历唯一键为
`uk_fiscal_cal(entity_type,entity_id)`。这里不采用 `operating_entity_id + finance_entity_id` 双可空字段：双字段会制造
XOR、索引空值和所有查询分支，且把类型判定分散到每张表。多态主体不建数据库外键；应用在同一事务按类型校验 CCT 稳定 OE ID 或本地
ACTIVE Finance Entity。

只有真实表示法定主体范围的 `tax_registration`、`cash_account`、`cash_account_binding` 和 `recon_exception`
同步采用该主体二元组。Order、Transaction Economics、Channel Account/Open/Debit 与 Payout 等交易链继续使用明确
`operating_entity_id`；Channel/Refund 子资源从父链继承主体，不机械重复或泛化。每个主体只有一套 Fiscal Calendar；同一主体/币种只有一本
Ledger Book，Journal、Trial Balance 与 Period Close 据此归属独立期间体系。

### 4.6 Manual Payout 的 RCO-I09 冻结边界

Payout 创建、最终 Finance 审批、Execution 创建和外部提交是四个独立 Checkpoint，每次都调用 RCO-I09 获得新的
Assessment/Evaluation，任何较早结果都不能跳过下一次调用。最终审批采用的完整结果冻结在 `t_payout`，每条 Execution
采用的最新完整结果冻结在 `t_payout_execution`，字段均为
`assessment_id,evaluation_id,eligibility_decision,eligibility_reason_codes,eligibility_matched_controls,risk_required_amount,risk_required_currency,control_set_hash,eligibility_hash`
；Risk 自己的不可变 Assessment/Evaluation 保留四次评估的权威历史。`ck_payout_risk_snapshot`、
`ck_payout_execution_risk_snapshot` 只允许完整 ALLOW Tuple，Client/Partner 的 Required Amount 矩阵按当前 Manifest 固化。

调用后必须查询并逐值验证 Risk Assessment 与 Evaluation 是不可变一对一、Scope 与当前 Owner/OE/Currency/Amount 完全一致，且能按
Control Set Hash 重建完整控制集合。任何缺失、陈旧、版本断档、Hash 不可重建或非 ALLOW 都 Fail-Closed；Finance 自己的
Eligibility Hash 只绑定本次冻结输入与结果，不能替代 Risk 权威 Hash。模型不增加 Risk Result Root、Requirement Snapshot 或另一套
Gate 表。

七表物理清单如下；字段与索引顺序均来自当前 Manifest：

| 表与可变性                             | 实际字段                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | 索引、关系、CHECK/门禁                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
|-----------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `t_beneficiary_account`，可变 Root   | `id,owner_type,owner_id,account_code,beneficiary_vault_token,beneficiary_name_masked,bank_country,currency,bank_account_blind_index,bank_account_blind_key_ver,account_last4,vault_record_version,vault_metadata_hash,bank_code,content_hash,maker_id,checker_id,checker_decision,decision_time,status,version,create_time,update_time`                                                                                                                                                                                                                                  | UK `uk_beneficiary_acct(owner_type,owner_id,account_code)`；IDX `idx_beneficiary_owner(owner_type,owner_id,status)`、`idx_beneficiary_blind(bank_account_blind_key_ver,bank_account_blind_index)`；无本库关系；`ck_beneficiary_lifecycle`；Maker≠Checker，DRAFT 后身份冻结，新 Payout/Execution/提交逐值复验 Owner/Currency/VERIFIED/Content Hash                                                                                                                                                                                                                                                                                                                    |
| `t_finance_restriction_proj`，可变投影 | `id,source_system,source_restriction_id,source_version,source_event_key,source_payload_hash,restriction_type,owner_type,owner_id,client_id,operating_entity_id,currency_scope_key,decision,reason_set_hash,effective_from,effective_to,version,create_time,update_time`                                                                                                                                                                                                                                                                                                  | UK `uk_fin_restrict_source(source_system,source_restriction_id)`、`uk_fin_restrict_event(source_system,source_event_key)`；IDX `idx_fin_restrict_scope(owner_type,owner_id,operating_entity_id,currency_scope_key,decision)`、`idx_fin_restrict_client(client_id,decision)`；无关系/CHECK；来源 Version CAS 且同键异 Hash 冲突，只表达当前限制，不替代实时 RCO-I09                                                                                                                                                                                                                                                                                                         |
| `t_payout`，可变 Root                | `id,payout_no,request_key,owner_type,owner_id,operating_entity_id,currency,beneficiary_account_id,beneficiary_content_hash,available_account_id,locked_account_id,requested_amount,fee_mode,fee_amount,owner_debit_amount,beneficiary_amount,cash_required_amount,assessment_id,evaluation_id,eligibility_decision,eligibility_reason_codes,eligibility_matched_controls,risk_required_amount,risk_required_currency,control_set_hash,eligibility_hash,request_hash,approval_hash,approved_time,journal_entry_id,reconciled_time,status,version,create_time,update_time` | UK `uk_payout_no(payout_no)`、`uk_payout_request(owner_type,owner_id,request_key)`、`uk_payout_journal(journal_entry_id)`；IDX `idx_payout_owner(owner_type,owner_id,currency,status)`、`idx_payout_oe(operating_entity_id,currency,status)`、`idx_payout_beneficiary(beneficiary_account_id,status)`、`idx_payout_available(available_account_id,status)`、`idx_payout_locked(locked_account_id,status)`；N:1 Beneficiary/AVAILABLE Account/LOCKED Account，1:1 最终 Journal；`ck_payout_amounts`、`ck_payout_risk_snapshot`、`ck_payout_approval_snapshot`、`ck_payout_reconciled`；跨行复验 Owner/OE/Currency、科目/Bucket、受益人 Hash、余额锁定、Risk Scope 与最终 Match 闭合 |
| `t_payout_approval`，不可变追加事实       | `id,payout_id,action_type,action_no,approval_role,actor_id,decision,requested_from_status,reason_code,evidence_ref_id,approved_content_hash,decision_time,create_time`                                                                                                                                                                                                                                                                                                                                                                                                   | UK `uk_payout_approval(payout_id,action_type,action_no,approval_role)`；IDX `idx_payout_approval_evidence(evidence_ref_id)`、`idx_payout_approval_actor(actor_id,decision_time)`；N:1 Payout、可选 Evidence；`ck_payout_approval_action`；每个动作固定 Maker/Checker 不同，取消 Hash 覆盖 Root/Action/原状态/原因/Evidence/LOCKED Account/Owner Debit                                                                                                                                                                                                                                                                                                                  |
| `t_payout_execution`，可变执行 Root    | `id,payout_id,attempt_no,cash_account_binding_ver_id,cash_account_id,execution_key,request_hash,assessment_id,evaluation_id,eligibility_decision,eligibility_reason_codes,eligibility_matched_controls,risk_required_amount,risk_required_currency,control_set_hash,eligibility_hash,bank_payment_ref,status,failure_code,active_slot,submitted_time,resolved_time,version,create_time,update_time`                                                                                                                                                                      | UK `uk_payout_execution(payout_id,attempt_no)`、`uk_payout_execution_key(execution_key)`、`uk_payout_bank_ref(cash_account_id,bank_payment_ref)`、`uk_payout_active_execution(payout_id,active_slot)`；IDX `idx_payout_execution_binding(cash_account_binding_ver_id)`、`idx_payout_execution_cash(cash_account_id,status)`、`idx_payout_execution_status(status,create_time)`；N:1 Payout/Binding Version/Cash Account；`ck_payout_execution_active`、`ck_payout_execution_state`、`ck_payout_execution_risk_snapshot`；Attempt 连续、最多一个活动执行、PAYOUT 用途与 Owner/OE/Currency 一致、CREATED 占用现金、仅权威 FAILED 可重试                                                |
| `t_payout_evidence_ref`，不可变追加事实   | `id,payout_execution_id,file_id,file_content_hash,evidence_type,create_time`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             | UK `uk_payout_evidence(payout_execution_id,file_id,evidence_type)`；IDX `idx_payout_evidence_file(file_id,file_content_hash)`；N:1 Execution；无额外 CHECK；Evidence Type 仅 PAYMENT_PROOF/NO_DEBIT_PROOF/BANK_CONFIRMATION/RECONCILIATION_SUPPORT，File ID/Hash 成对且与 Platform 逐值一致                                                                                                                                                                                                                                                                                                                                                                   |
| `t_payout_event`，不可变连续事实          | `id,payout_id,execution_id,event_scope,event_no,from_status,to_status,reason_code,actor_id,source_type,source_id,source_event_key,approval_id,evidence_ref_id,bank_recon_match_id,journal_entry_id,payout_version_after,execution_version_after,occurred_time,create_time`                                                                                                                                                                                                                                                                                               | UK `uk_payout_event_no(payout_id,event_no)`、`uk_payout_event_source(source_type,source_event_key)`；IDX `idx_payout_event_execution(execution_id,event_no)`、`idx_payout_event_approval(approval_id)`、`idx_payout_event_evidence(evidence_ref_id)`、`idx_payout_event_bank_match(bank_recon_match_id)`、`idx_payout_event_journal(journal_entry_id)`、`idx_payout_event_actor(actor_id,occurred_time)`；N:1 Payout 与可选 Execution/Approval/Evidence/Bank Match/Journal；`ck_payout_event_scope`；Root 锁内连续分配 Event No，Version After 等于同事务结果，来源重放不跳号/重复                                                                                                |

关系方向为 Beneficiary/AVAILABLE Account/LOCKED Account → Payout → Approval/Execution/Event，Execution → Evidence/Bank
Recon Match，Cash Binding Version/Cash Account → Execution，Bank Statement Item → Bank Recon Match → Event，Journal Entry →
Payout/Event。Account 表示钱的待成熟、可用和已锁定位置；Payout 表示出款理由、受益人和金额；Execution 表示 Operations
的每次银行指令；Bank Match 证明银行是否真实扣款；只有 Journal 改变账簿余额。

### 4.7 重大对账先审批、后执行

`t_recon_resolution` 在非 APPLIED 状态禁止出现任何执行引用；APPLIED 后按类型精确绑定一组证据：

- RE_MATCH → `channel_transaction_match_id` 与 `bank_recon_match_id` 严格二选一：前者表示用新追加的不可变交易匹配解决未知/歧义清算行，后者必须引用已
  CONFIRMED 的银行匹配；
- ACCOUNTING_ADJUSTMENT / WRITE_OFF → 本库 `instruction_id`；
- ACCEPT_TIMING_DIFFERENCE → 不产生执行引用。

Maker、Checker、决定和批准 Hash 仍受固定双人控制，避免“先退回资金、后补审批”。

### 4.8 保证金与追偿只使用 Account + Journal

保证金是 `CLIENT_SECURITY_DEPOSIT/NONE` 的 Client 负债 Account，追偿是 `CLIENT_RECOVERY_RECEIVABLE/NONE` 的 Client 资产
Account；二者都按 Ledger Book、Client 和币种隔离。预存、返还、允许的争议消耗、未来净额抵扣与人工回款全部追加平衡 Journal，不再建
Reserve Case、Restricted Fund、Movement、Recovery Case 或 Application。Journal 的 Source Tuple 提供每次变动的幂等与追溯，
`t_account` 只保存可重建的当前余额投影。

### 4.9 Platform 文件与 Vault

文件引用统一为 ID/Hash 二元组，覆盖 Tax Return、Manual Adjustment、Channel Settlement Batch、Bank Statement、Payout Approval
取消证据和 Payout Execution Evidence。业务 Content Hash 或 Source Hash 不能代替 Platform 文件内容 Hash。

当前 Finance 模型中的 Buyer Tax ID `Prepare/CopyForBinding/ConfirmReference`、Binding、Blind Index、Masked Value、Record Version、
Metadata Hash 与 Retire 依赖不属于 Vault V1。不得按这些字段假装当前 Vault 已支持，也不得让 Commerce 来源 Token 进入 Finance
表、MQ Payload 或普通日志。FIN-I17 的 Buyer Tax ID 分支在完成 [Vault 消费者模型对齐计划](../../../docs/superpowers/plans/2026-09-05-vault-consumer-model-alignment.md)、
同步批准的 API/模型迁移及安全测试前必须 Fail Closed；没有真实消费者证明时不补回宽泛 Vault V2。

### 4.10 Partner OE 与平台 Finance Entity 使用业务明确的双账分录

CCT Partner Agreement 的唯一 `PLATFORM_SIGNATORY` 是平台签约法律 Party，但不保证它同时持有 MID 或是 Operating
Entity。Partner 来源的 Commerce Order Context 因此冻结 `platform_service_charge_scope_id + platform_service_party_id`
；Finance 以 Party 唯一解析本地 ACTIVE `t_finance_entity`，并在 `t_transaction_economics.finance_entity_id` 与
`PLATFORM_SERVICE_CHARGE` Component 冻结结果。非 Partner 来源不生成服务费，
`local_partner_id + partner_agreement_oe_id + finance_entity_id` 必须三项全空。

平台服务费必须来自 CCT 明确合同 Component 的受控 Calculation Base、Rate、Fixed Amount、上下限和币种，不以 Client Fee、Partner
Share、Channel Cost 或残差倒推。一个 Component 生成两个各自绑定 `ledger_book_id` 的 Accounting Instruction：Partner OE 使用
`PLATFORM_SERVICE_CHARGE_PARTNER`，借服务费费用、贷对平台应付；Platform Finance Entity 使用
`PLATFORM_SERVICE_CHARGE_PLATFORM`，借对 Partner OE 应收、贷平台服务收入。两条 Journal 在同一 Finance 本地事务内按 Book
ID、Account ID 升序锁定并各自平衡，任一失败全部回滚；同 Event Key/Version/Payload 重试返回既有结果，不同 Payload 或主体冲突。

V1 仍不恢复通用 `t_intercompany_transfer`、Cross-Entity Cash Pool 或任意跨主体调拨。四个服务费科目表达已确认的合同双边义务，付款时双方账簿分别结清应付/应收；Refund
沿 Parent Economics 与 Component Link 封顶冲回，两本账分别执行 Trial、Close 和 Profit Carry Forward。平台利润只在 Platform
Book 结转，gross/net 收入列报仍由合同和 principal/agent 会计政策决定。

### 4.11 科目表保留稳定 Root，不保留闭包表

`t_chart_of_accounts` 仍是 Ledger Book 引用的稳定科目目录 Root，但 V1 不对整套科目表维护 Version、有效期或乐观锁。科目层级只由
`t_chart_account.parent_account_id` 表达；汇总查询使用递归 CTE 或应用层遍历。Chart 尚未被账簿采用时，新增或移动科目须在同一事务校验同
Chart、父节点存在和无环；一旦被任一 Ledger Book 采用，既有科目的代码、父级、类别、正常方向和 `posting_allowed`
全部冻结，只允许追加新科目。具体核算账户的停用通过 `t_account.account_status` 完成，避免历史分组随当前树变化，也不再维护容易与父子关系漂移的
`t_account_tree_path`。

### 4.12 统一记账账户，不维护双份余额真相

`t_account` 以 `ledger_book_id + chart_account_id + owner_type + owner_id + balance_bucket`
唯一标识可过账账户，在一套身份中同时表达科目落账位置和核算对象明细。账簿继续隔离 OE/Finance Entity 主体与币种；账户不重复保存这些字段。
`OPERATING_ENTITY`、`FINANCE_ENTITY`、`CHANNEL_ACCOUNT`、`CASH_ACCOUNT`、`TAX_AUTHORITY` 仅使用 `NONE`。行内
`ck_account_owner_bucket` 允许 Client 和 Local Partner 出现 `PENDING/AVAILABLE/LOCKED/NONE`，但它只做 Owner/Bucket
结构放行，不能证明任意 Local Partner/NONE 合法：同一账户创建事务必须锁定并校验 Chart Account，`CLIENT_PAYABLE` 与
`PARTNER_PAYABLE` 只允许 `PENDING/AVAILABLE/LOCKED`；`CLIENT_SECURITY_DEPOSIT`、`CLIENT_RECOVERY_RECEIVABLE` 和
`PARTNER_RECOVERY_RECEIVABLE` 才使用 `NONE`。其中 Partner Recovery 是资产 Account，Partner Payable 是负债 Account，不得互换或以负
Partner Payable 长期承载追偿。

Journal、Instruction 和人工调整明细只引用 `account_id`；Header 的 Ledger Book 与账户所属 Book 必须一致。每个账户在创建时从
`t_chart_account` 复制并冻结 `normal_side=DEBIT/CREDIT`，但所有账户仍可接受借贷双向过账；`normal_side` 只决定余额公式，不能写成
`BOTH`。`normal_side=DEBIT` 时 `net_balance=debit_total-credit_total`，`normal_side=CREDIT` 时
`net_balance=credit_total-debit_total`。`net_balance` 允许为负，负数表示反向余额；当前余额方向和金额由该有符号值动态推导，不持久化第二套方向字段。

`t_account` 直接保存 `debit_total/credit_total/net_balance/last_journal_sequence/balance_version`
，这是每个统一账户唯一的当前余额投影。前四项可由 Journal 重算；重建事务以原 `balance_version` 做 CAS 并单调加一，不能从
Journal 推导或重置该并发令牌。身份与 `normal_side` 不可修改。短暂冲销、退款和调整可以产生反向余额；真实且持续的反向债权债务必须进入成对账户，例如
`CLIENT_RECOVERY_RECEIVABLE`、`CHANNEL_UNSETTLED_PAYABLE` 或 `CHANNEL_SETTLEMENT_PAYABLE`
，不长期用负应付款/应收款代替另一类资产或负债。Channel 四个阶段账户和 `CHANNEL_FEE_ACCRUAL` 均使用
`owner_type=CHANNEL_ACCOUNT`、`owner_id=t_channel_account.id`、`balance_bucket=NONE`；`BANK_CASH` 使用
`owner_type=CASH_ACCOUNT`、`owner_id=t_cash_account.id`，`t_cash_account` 不保存反向 `account_id`。试算和平账按
`ledger_book_id + chart_account_id` 汇总全部 Owner/Bucket 账户，冻结到 `t_ledger_period_balance` 和
`t_trial_balance_line`；Opening/Closing Balance 按科目正常方向保留有符号值，不取绝对值。Run `total_debit/total_credit`
只把可过账叶子科目期末有符号余额拆成实际借方/贷方后合计，本期发生额继续由 Line `period_debit/period_credit` 表达。
`t_ledger_period_balance` 额外冻结 `as_of_sequence`，更新事务只允许较新水位覆盖，相同 Run/水位幂等，旧水位拒绝，从而保留会计科目级报表又消除双份余额漂移。

### 4.13 自动记账只配置确定性规则行

`t_accounting_rule_bundle` 保存稳定规则集身份和当前版本指针，`t_accounting_rule_bundle_ver` 只保存业务版本号、生效区间、状态以及
`maker_id/checker_id/approved_time`。每个版本下的 `t_accounting_rule_line` 用
`instruction_type + line_no/line_code + amount_source_code + owner_role + account_code + balance_bucket + entry_side + required_flag`
表达一条确定性映射；同一个 `amount_source_code` 可以生成多条借贷规则行。

Client Fee、Partner Share、Platform Service Charge、Tax 与 Channel Cost 金额已经在 Transaction Economics Component
中计算并冻结，Accounting Rule 不重复计算费率，只把受控金额来源解析到统一账户、余额桶和借贷方向。预计 Channel Cost 借记
`CHANNEL_FEE_EXPENSE`、贷记 `CHANNEL_FEE_ACCRUAL`；实际成本大于预计时使用 `CHANNEL_COST_INCREASE` 并借记 Expense、贷记
Accrual，实际成本小于预计时使用 `CHANNEL_COST_DECREASE` 并借记 Accrual、贷记 Expense；实际等于预计时不创建
COST_ADJUSTMENT。Channel Cost Rule 不得引用四个 Channel 阶段账户，Batch/Bank Rule 不得再次借记 Channel Fee Expense。
`required_flag=1` 时来源缺失即拒绝生成指令，为 `0` 时可跳过该行；来源存在但金额为零不算缺失，固定解释器必须对该
`amount_source_code` 的全部规则行整体跳过，不创建零金额 Instruction Line。V1 不保存通用 Condition、优先级、规则
Hash、SQL、任意脚本或外部规则文件；AUTO Instruction 还必须冻结唯一 `ledger_book_id`、Bundle Version ID、Transaction Economics
ID 与 Input Hash，并让 Instruction Line 回指实际采用的 Rule Line，从而可解释和确定性重放。Rule Line 各字段的解释语义就是 V1
稳定协议，代码升级必须向后兼容旧行；未来若出现不兼容语义，应新增显式字段或模型版本并迁移，不能用隐藏的代码分支改写旧规则含义。

`PLATFORM_SERVICE_CHARGE` 使用四条确定性 Rule Line：Partner 指令的
`Dr PLATFORM_SERVICE_FEE_EXPENSE / Cr PLATFORM_SERVICE_PAYABLE`，Platform 指令的
`Dr PLATFORM_SERVICE_RECEIVABLE / Cr PLATFORM_SERVICE_REVENUE`。规则只读取已计算 Component Amount；Owner Role 分别解析
Partner OE 和 Platform Finance Entity，不参与合同费率计算。Instruction 的 Book、全部 Instruction Line Account、Journal Entry
和 Accounting Period 必须逐值属于同一本账。

因为规则版本不再依赖 Content Hash，行集冻结必须靠更直接的生命周期约束：只有 `status=DRAFT` 的 Version 可在受控事务追加
Rule Line；追加、提交 `PENDING_APPROVAL`、审批和 Current Pointer 切换统一先锁定 Bundle Root、再锁定
Version，禁止反向取锁；提交时校验完整有序行集，离开 DRAFT 后禁止再插入。Rule Line 自创建起不可更新或删除，发现错误必须放弃该
Version 并创建新版本；Checker 审批的就是提交时被冻结的同一行集。该跨表约束未来由应用事务和仓库 `local-environment`
真实 MySQL 并发测试保证。

### 4.14 账簿序号使用同事务行锁

每个 `t_ledger_book` 恰有一条 `t_book_sequence`，并在创建 Book 时同步初始化正整数 `next_value`。Journal Posting
在同一本地事务内先按统一顺序校验并锁定 Book/Period，再以 `SELECT ... FOR UPDATE` 锁定 Sequence 行，分配原 `next_value`
并加一，随后写 Journal Entry/Line、账户余额投影和本地 Compensation Event。`t_book_sequence` 不保存 `version`
，因为行锁已经是唯一取号并发协议。

任一步失败时 Sequence 与 Journal 整体回滚；已经提交的 Journal 序号严格递增且不可复用，回滚事务中未提交的候选序号允许再次取得。
`uk_book_seq(ledger_book_id)` 保证一 Book 一行，`uk_journal_seq(ledger_book_id,journal_sequence)` 提供最终重复保护；Sequence
行锁本身不能替代期间关账校验和 Posting/Close 的统一锁顺序。

## 5. Version、Hash、Active Slot 和时间

业务 `version` 只用于有真实并发写入者的 Root 或资金投影；不可变 Line、Transaction
Economics、Snapshot、Movement、Event、Approval 不机械增加 Version。`t_book_sequence` 通过同事务行锁串行取号，不使用 Version；
`t_compensation_event.lock_version` 只用于 Claim/Lease，`t_command_idempotency` 不使用 Version。

Hash 只用于请求幂等、事件/文件/Vault 元数据完整性、不可变计算输出、审批承诺和确定性集合绑定。同库可由稳定 ID 精确取得的 Hash
不重复保存。`t_trial_balance_run.content_hash` 在 CREATED/RUNNING 时为空，进入 SUCCEEDED/FAILED 时才冻结 Scope、水位、Trial
Line 与异常发现事实；它不包含之后可变的异常解决字段。失败 Run 不转成功，解决 Hash 由 Exception 单独冻结，修复后追加新 Run。

活动唯一性只保留三处：

- `t_tax_payment.active_slot`；
- `t_payout_execution.active_slot`；
- `t_period_close.active_slot`。

取值固定为 `1/NULL`。UTC 时间使用 `DATETIME(6)` 并映射 Java/MyBatis `Instant`；无时区业务日期使用 `DATE` 并映射
`LocalDate`。

## 6. 会计、资金和审批不变量

### 6.1 Journal 与关账

- Journal Entry/Line 只追加，每个 Entry 在一个 Ledger Book、Period 和 Currency 内借贷相等；
- 每个 Book 通过唯一 `t_book_sequence` 行在 Journal Posting 事务内锁定取号，`next_value>0`；已提交 Journal
  序号不可复用，事务回滚同时回滚候选取号；
- Posting 按 Account ID 升序锁定账户，并在获得锁后再次校验 `account_status=ACTIVE`；Posting 与
  Close/停用账户流程必须采用固定锁顺序，避免先读后改竞态；
- `REVERSAL` 只允许对整条原 Entry 做统一账户、金额和方向逐行严格相反的全额技术冲销；部分退款与非完整镜像调整使用新的
  `STANDARD/ADJUSTMENT` Entry；
- `t_account` 余额列与 Ledger Period Balance 是可重建投影；
- Projection Rebuild 只允许 `GENERAL_LEDGER_MISMATCH`，按 Book/Sequence 从 Journal 重建投影并通过 CAS 提交，不追加
  Accounting Instruction/Journal；Journal 本身错误必须追加更正 Journal；
- `CONTROLLED_WAIVER` 不得处理 Journal 不平衡、Journal/Line 缺失、跨 Book/Period/Sequence、总账硬错误或 Platform Service
  Charge 单边/金额不等；
- Partner 来源服务费在关账控制中按同一 Economics、Component 和币种核对 Partner OE `PLATFORM_SERVICE_PAYABLE` 与 Platform
  Finance Entity `PLATFORM_SERVICE_RECEIVABLE`；
- 每个 Close/Reopen 必须覆盖期间全部适用 Book；每个 Book 的 FINALIZED 更新在同一事务校验同 Book/Period/Sequence 的成功
  Final Trial、无 OPEN/UNDER_REVIEW 或硬错误 Waiver、CLOSING/REOPENING Instruction 及其 Journal、同水位 Ledger Period
  Balance；
- 七种固定 Close Step 必须各一条且全部 SUCCEEDED，OPEN/UNDER_REVIEW Trial Exception 阻断完成；
- Ledger Book、Period、Chart Account、统一 Account 与 Currency 的组合必须逐层一致。

### 6.2 Account、保证金、追偿与付款现金

Client 与 Local Partner 的 Payable Account 都只使用 PENDING、AVAILABLE、LOCKED；`CLIENT_RECOVERY_RECEIVABLE` 与
`PARTNER_RECOVERY_RECEIVABLE` 是独立资产 Account，使用 NONE。PAYMENT Economics 冻结成功时刻和双方共用的成熟时刻，并由 CHECK
保证 `balance_available_time >= payment_succeeded_time`：成功当地日是工作日且不晚于合同 Cutoff 时该日为 T，否则先按
FOLLOWING 滚到下一工作日作为 T，再增加 Agreement-OE 的 N 个工作日，最终取结果日当地 Cutoff 转 UTC。Channel 实际结算不阻断该成熟时刻。

共同到期以 `PAYABLE_BALANCE_MATURED:<economics_id>` 幂等执行。`partner_gross_matured` 是本次从 Partner PENDING 消灭的成熟总额，
`recovery_netted` 是其中贷记并冲减 `PARTNER_RECOVERY_RECEIVABLE/NONE` 的金额，
`partner_net_available = partner_gross_matured - recovery_netted` 是实际进入 Partner AVAILABLE 的金额，且
`0 <= recovery_netted <= partner_gross_matured`。同一个 Journal 完整记为：
`Dr CLIENT_PAYABLE/PENDING client_matured_amount / Cr CLIENT_PAYABLE/AVAILABLE client_matured_amount`，Partner 场景再记
`Dr PARTNER_PAYABLE/PENDING partner_gross_matured / Cr PARTNER_RECOVERY_RECEIVABLE/NONE recovery_netted / Cr PARTNER_PAYABLE/AVAILABLE partner_net_available`
。零金额行分别省略；非零 Partner 借方始终等于两条贷方之和。每个金额在锁内按原 Component、已关联成功负向事实、此前成熟 Journal
与当前 Partner Recovery 重算。成熟、RefundSucceeded 与 Partner Recovery Netting 采用相同稳定顺序锁定原 Economics、Component
Link 和相关 PENDING/AVAILABLE/Recovery Account；未清 Recovery 阻断新 Partner Payout。

Finance 不审批或预留退款资金。Commerce 与 Payment 分别维护订单退款额度和渠道退款额度；Finance 只消费 `RefundSucceeded`
，幂等创建 REFUND Economics、Component Link、Channel Debit Item 与 Journal。Client Principal 按 PENDING、AVAILABLE
顺序冲减；LOCKED 不自动拆锁，已锁定、已结算或余额不足的部分形成 `CLIENT_RECOVERY_RECEIVABLE`。Component Link 的 REVERSAL
累计封顶继续保护会计入账完整性，但不构成 Channel 提交前门禁。

Client 的预存保证金收款记为 `Dr BANK_CASH / Cr CLIENT_SECURITY_DEPOSIT`；等价余额只存在 `CLIENT_SECURITY_DEPOSIT/NONE`
Account，不占用应付款 Bucket。Payout 创建、最终 Finance 审批、Execution 创建和外部提交四次都使用新的 RCO-I09
Assessment/Evaluation；最终审批结果冻结到 Payout，每条 Execution 采用的最新结果冻结到该 Execution，Risk 保留每次权威评估。同事务锁定适用
AVAILABLE/LOCKED、Client Security Deposit/Recovery 或 Partner Recovery Account，逐值重验 Risk Scope、最新限制与当前余额。任一缺失、非
ALLOW、Client Recovery 非零、Client 保证金不足或 Partner Recovery 非零都停止出款。

Payout 创建以 `PAYOUT_LOCK:<payout_id>` 执行 `Dr Owner Payable/AVAILABLE / Cr Owner Payable/LOCKED`；PENDING_APPROVAL 且无
Execution 的申请人撤回，或 APPROVED/ON_HOLD/RETRY_REQUIRED 的 `PAYOUT_CANCEL` 双人审批通过，在确认无可能扣款的
Execution、UNKNOWN、未决 Match 或实际扣款后，以 `PAYOUT_RELEASE:<payout_id>` 反向释放；完整付款对账以
`PAYOUT_PAID:<payout_id>` 执行 `Dr Owner Payable/LOCKED / Cr BANK_CASH`。`ADDED_ON_TOP` 的 Owner Debit/Cash Required=
`requested+fee`、Beneficiary=`requested`；`DEDUCTED_FROM_PAYOUT` 的 Owner Debit/Cash Required=`requested`、Beneficiary=
`requested-fee`。正常费用由 Owner 承担，不形成平台收入/费用。`payout.journal_entry_id` 仅指正常/最终 Journal；不足/超额、Recovery
和迟到 Debit 从 Event/Recon 链导航至 `PAYOUT_FEE_DIFF:<recon_exception_id>` 或 `LATE_DEBIT:<bank_item_id>` 的平衡
Journal。

实际少扣时，最终清算只按真实 Debit 记 `Dr LOCKED / Cr BANK_CASH`，差额以 `Dr LOCKED / Cr AVAILABLE` 释放；实际多扣时，冻结金额由正常
Payout 清算，超额经批准记 `Dr CLIENT_RECOVERY_RECEIVABLE 或 PARTNER_RECOVERY_RECEIVABLE / Cr BANK_CASH`，或在追加授权与
AVAILABLE 足额时记 `Dr AVAILABLE / Cr BANK_CASH`。迟到 Debit 记 `Dr RECON_SUSPENSE_ASSET / Cr BANK_CASH`。这三类差异/迟到分录不进入
`payout.journal_entry_id`，而由 Payout Event 和 Recon Exception/Resolution 的现有 Accounting Instruction/Journal 坐标追溯。

Tax Registration 和 Tax Payment 逻辑不受合同成熟简化影响。Tax Payment 直接冻结 TAX_PAYABLE Account，并由 `active_slot` 阻断
UNKNOWN 期间的替代付款。V1 不自动执行缴税：正常路径先创建 `CREATED` Tax Payment 并通过 Tax Payable、BANK_CASH 容量和活动槽门禁，
Operations 才可在银行或税务门户形成外部承诺。已出现在 Bank Statement 的真实 Debit 不因容量不足而丢弃；没有合法既有 Tax Payment 时保留
Bank Item 并打开 Recon Exception，不补造正常 Payment，也不在完成匹配/受控解决前结清 Tax Payable。当前事后登记闭环缺口见
[Finance 手工缴税对账计划](../../../docs/superpowers/plans/2026-09-05-finance-manual-tax-reconciliation.md)。

Payout Execution 创建/重试、Payout 取消、Tax Payment 创建/失败、Channel/Payout/Tax 完整 Recon 和每一条改变银行现金的
Journal，均按 Account ID 升序在同一本地事务锁定对应 `BANK_CASH/NONE` Account。锁内只派生、不存储
`available_cash = bank_cash_account.net_balance - active_payout_cash - active_tax_cash`；活动 Payout Execution 是
`CREATED/SUBMITTED/UNKNOWN`，CREATED 就占用 `cash_required_amount`；活动 Tax Payment 是
`CREATED/SUBMITTED/PROCESSING/BANK_ACCEPTED/UNKNOWN`。`UNKNOWN 不释放`；只有权威未扣款证据允许 FAILED 并释放活动槽，此后才可创建连续
Attempt No 的新 Execution。若重试更换已批准且同 OE/Currency/PAYOUT 用途的 Cash Binding Version，必须同时锁定旧、新
BANK_CASH Account，排除自身旧承诺后再加入新承诺，任一检查失败整体回滚。

Tax Payable 也使用直接 Account 并发门禁：创建、FAILED 释放活动槽和完整 Recon 必须锁定 `tax_payable_account_id`；新付款金额不得超过
`tax_payable_account.net_balance - 同一账户全部既有活动 Tax Payment.amount`，该聚合跨 Tax Return。Tax Payment Event 的
Source Ref/Evidence Hash 成对为空或非空，只有 FAILED 强制要求权威未扣款证据；超时、丢响应形成的 UNKNOWN 可以没有证据，但必须保持活动槽与
Bank Cash 承诺。

Channel Credit/Debit 及其他外部银行事实不做“容量足够才入账”的错误判断：确认 Match 时先锁 BANK_CASH 并如实生成
Journal，随后新承诺读取新余额。旧 FAILED Payout Execution 或 FAILED Tax Payment 的迟到 Debit 使用
`LATE_DEBIT:<bank_item_id>` 幂等键，在原 BANK_CASH 锁内先记 `Dr RECON_SUSPENSE_ASSET / Cr BANK_CASH`，再打开 `LATE_DEBIT`
Recon Exception；旧执行保持 FAILED，新 Execution/替代付款的现金承诺不释放。

### 6.3 固定双人控制

Accounting Rule、Tax Rule/Return、Manual Adjustment、Channel Cost、Beneficiary、Payout 普通审批与取消动作、重大 Recon
Resolution 和 Period Close/Reopen 必须由不同 Maker/Checker 完成。Accounting Rule Version、Tax Rule/Return 精确版本行与
Beneficiary 生命周期直接保存 Maker、Checker 和决定时间；Payout 使用不可变 `t_payout_approval`，普通审批固定 `action_no=1`
，取消为递增正 Action No。使用 Approval 子表的对象必须恰有 MAKER/CHECKER 两个角色，Actor 不同，决定与 Root 状态一致，批准
Hash 等于相同冻结输入的 Hash。这些跨行条件由应用事务和仓库 `local-environment` 真实 MySQL 并发测试保证。

## 7. Sidecar 逻辑约束与事务边界

PDManer 4.9.3 没有针对所有数据库目标的统一可移植约束节点。名称、逻辑表达式和绑定表固定在
`scripts/pdmaner/finance/model-policy.mjs`，独立 Oracle 会逐项比对；这些 Sidecar 契约供模型、Owner 应用校验和测试追踪，不生成物理 `CHECK`。它覆盖金额范围、XOR、状态—金额矩阵、文件/Vault
Tuple、Maker-Checker、Tax/Payout Execution 活动槽、Channel 净额、Tax/Fx Rate、版本根指针、自引用禁止、Recon 目标、Transaction
Economics 类型、父子引用以及 Component 规则来源矩阵、Payout/Tax Payment 对账终态、Close/Reopen 和 Compensation Lease。Manual
Payout 的表级 Sidecar 契约精确为 `ck_beneficiary_lifecycle`、`ck_payout_amounts`、`ck_payout_risk_snapshot`、
`ck_payout_approval_snapshot`、`ck_payout_reconciled`、`ck_payout_approval_action`、`ck_payout_execution_active`、
`ck_payout_execution_state`、`ck_payout_execution_risk_snapshot` 和 `ck_payout_event_scope`。Component Link 的 Target
分配守恒、方向兼容和 REVERSAL Source 累计封顶，以及 Rule Version 行集冻结都属于跨行约束，分别通过按稳定顺序锁定 Source
Component、锁定 Rule Version 的应用事务和仓库 `local-environment` 真实 MySQL 并发测试实现；ADJUSTMENT 是实际成本差额，允许高于原预计成本，不错误套用退款式
Source 封顶。Accounting Rule 不再拥有通用 Condition；规则行只允许受控目录中的指令类型、金额来源、Owner Role、科目、余额桶和借贷方向。

以下跨行或跨表条件必须在单一数据库事务中通过实际资源锁、完整 Tuple 查询、唯一索引和 CAS 校验，并由仓库自有 `local-environment` 真实 MySQL 自动测试证明：

- Chart Account 父子必须属于同一 Chart、不得形成长环，父科目必须 `posting_allowed=false`；Chart 被账簿采用后既有结构字段不可修改；
- Journal 借贷平衡及严格全额冲销；
- Account、Instruction/Journal Line 的 Book、Chart、Owner、Bucket、账簿主体与 Currency 逐值一致；行内 Owner/Bucket Sidecar 表达式
  只定义逻辑形状，账户创建事务必须锁定 Chart Account 并验证 `PARTNER_PAYABLE -> PENDING/AVAILABLE/LOCKED`、
  `PARTNER_RECOVERY_RECEIVABLE -> NONE` 等科目—Bucket 矩阵，拒绝其他 Local Partner/NONE 组合；
- Account Journal、Payout 直接锁定账户、Client/Partner 共同成熟与 Recovery Netting 并发守恒；
- Tax Header/Line 的单一报告币种、Registration 报告币种、汇率与申报汇总闭环；
- Channel Batch/Item/Transaction Match 的 Gross/Fee/Debit/Net 守恒、`R-P-F` 与单一 Settlement 方向一致、Batch Journal 与
  Open/Debit Item 状态原子推进，以及 Bank Match 确认前对批次完整性和累计金额的锁内重算；
- Bank Statement/Item 的 Cash Account 一致，Binding Version 指向同一 `entity_type/entity_id`、Currency、Purpose 和
  Account，且 Bank Item 与业务目标累计匹配不超限；
- Partner 来源的 CCT Platform Service Party 唯一映射本地 ACTIVE Finance Entity；双 Instruction/Journal 分别绑定 OE/FE
  Book，按稳定锁顺序同事务提交，并在重试时完整幂等；
- Payout 创建/最终审批/Execution 创建/外部提交四次 RCO-I09 的新评估、Assessment/Evaluation 一对一与 Scope/Hash
  重建；Beneficiary Hash、Account Bucket、现金容量、Attempt 连续性、取消释放和最终 Bank Match 闭合的锁内重验；
- Approval 两角色、批准 Hash 与 Root 状态一致；
- Period Close 的全 Book 覆盖、七步骤、Trial/Instruction/Journal 对齐。

## 8. 索引、关系和安全

- 表名以 `t_` 开头，唯一索引以 `uk_` 开头，普通索引以 `idx_` 开头；表、字段和索引物理名不超过 32 字符；
- 主键是服务侧 Snowflake `BIGINT`，禁止自增；1:1 关系必须由主键或单字段 UK 保证；
- 同一 `(mid_id,currency)` 只能映射一个 Channel Account，`duplicate_capture_item` 与确认其异常的 Journal Entry 为 1:1；
- 只建立 Finance 本库关系；CCT、Commerce、Payment、Risk、Platform、Vault 和 Actor 标识不建 FK；
- 本库关系只表达逻辑 `RESTRICT` 语义，不创建物理 FK；Owner 应用在同一本地事务中查询引用并拒绝级联删除 Journal、Transaction Economics、Snapshot、Fiscal、Channel/Bank 原始事实和审批证据；
- PAN、CVV/CVC、完整磁道、PIN/PIN Block 永不进入 Finance、Vault、事件、文件或日志；
- Channel/API/Webhook Secret、银行 Credential 和私钥由 Secrets Manager/KMS 管理，不进入模型；
- 当前目标 `compensation_event` 以 `(event_key,event_type,sub_event_type)` 唯一；可选 `payload_json` 最大 4 KiB UTF-8，只保存无法从本库权威事实恢复的最小非敏感 JSON Object；
  禁止 Vault Token。生成模型仍保留的旧 Source/Schema/Hash/64 KiB 字段按 COMP-01 一次性迁移，迁移完成前不得宣称物理模型已符合目标规范。

## 9. Review 门禁

自动门禁覆盖 JSON、确定性生成、精确总量、中文元数据、闭合枚举/开放目录、32 字符命名、索引前缀、内部逻辑关系、真 1:
1、不可变表、Version/Hash/Active 白名单、Sidecar 独立 Oracle、场景覆盖和敏感禁存。自动验证不能替代以下人工步骤：

1. 使用 PDManer 4.9.3 打开 `finance.pdma.json`；
2. 检查六个关系图的可读性、连线端口和循环引用；
3. 执行应用内规范检查；
4. 按六个子域 Review 表、字段、枚举、索引和关系；
5. 用户确认后，才允许另行规划经人工评审并手工执行的完整 Schema SQL 和 Java 实现；同时补齐仓库自有 `local-environment` 真实 MySQL 测试。禁止 Flyway、Liquibase、Testcontainers 或应用启动时 Schema/初始化数据迁移。
