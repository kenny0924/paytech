# Finance 通用 Funding 模块移除设计

> **后续专项取代范围（2026-08-27）：** 本文关于通用 Funding 删除、Settlement/Tax Payment 直接 Account 与 `BANK_CASH` 行锁的决定继续有效；任何 Reserve Case、Restricted Fund、Recovery Case/Application 或独立准备金/追偿余额流程，均由 [Finance 保证金 Account 精简实施记录](./2026-08-27-finance-security-deposit-account-simplification-implementation-plan.md) 取代。当前保证金与追偿只使用统一 Account + Journal。

日期：2026-08-27  
状态：已实施、同步主工作区并通过自动验证；PDManer 4.9.3 应用内人工检查未完成

## 1. 决策

Finance V1 删除以下四张通用 Funding 表：

- `t_funding_position`；
- `t_funding_commitment`；
- `t_funding_allocation`；
- `t_funding_move`。

四表均不是法定会计事实。`OWNER_PAYABLE`、`TAX_PAYABLE` 与 `BANK_CASH` Position 重复 `t_account` 的 Journal 可重建余额；Commitment、Allocation 与 Move 又重复 Settlement、Tax Payment、付款事件、Recon Match 和 Journal 已经拥有的金额、状态与审计证据。

本次只删除通用抽象，不删除结算、缴税、银行现金并发与未知结果门禁。Settlement 和 Tax Payment 自身分别成为其业务承诺根，Journal 继续是唯一不可变会计事实。

## 2. Settlement 直接模型

`t_settlement` 删除 `funding_commitment_id`，增加：

- `locked_account_id`：直接引用本次结算占用的 Client 或 Local Partner `LOCKED` Account；
- `cash_required_amount`：冻结本次实际需要从银行账户扣除的总金额，`t_settlement_payment.amount` 必须与其相等。

Promotion 在一个本地事务内按 Account ID 升序锁定 AVAILABLE 与 LOCKED Account，重验候选或出款请求、余额与 `balance_version`，写入 `AVAILABLE -> LOCKED` Journal、更新 Account 投影并创建 Settlement。`gross_amount` 是本次锁定的业务负债金额，不再创建 Liability Allocation。

`t_settlement_payment_attempt` 删除 `bank_cash_allocation_id`。Attempt 继续直接冻结 `cash_account_binding_ver_id`、`cash_account_id`、`submission_key`、`request_hash` 与银行引用。Payment 可以先以 `status=CREATED/current_attempt_id=NULL` 存在，但此时尚未选择银行账户，因此不构成银行现金承诺。首个 Attempt 必须在锁定精确 BANK_CASH Account 的同一事务内创建并原子写入 `current_attempt_id`；此后活动金额按当前 Attempt 的 `cash_account_id` 归属。Attempt `CREATED/SUBMITTED/PROCESSING/BANK_ACCEPTED/UNKNOWN/FAILED/RECONCILED` 分别映射 Payment `CREATED/SUBMITTED/PROCESSING/PROCESSING/UNKNOWN/RETRY_REQUIRED/RECONCILED`，所以 BANK_ACCEPTED 仍不是付款完成。

Settlement 付款只有完整银行 Debit Recon 才能在同一事务写付款 Journal 并进入 PAID。UNKNOWN、BANK_ACCEPTED、部分借记或未决 Recon 均不得重试或取消；批准取消或审批拒绝的释放路径必须写 `LOCKED -> AVAILABLE` 反向 Journal，不能只改 Settlement 状态。

## 3. Tax Payment 直接模型

`t_tax_payment` 删除：

- `funding_commitment_id`；
- `tax_payable_allocation_id`；
- `bank_cash_allocation_id`。

并增加：

- `tax_payable_account_id`：直接引用精确主体、税务机关和币种的 `TAX_PAYABLE/NONE` Account；
- `active_slot`：CREATED、SUBMITTED、PROCESSING、BANK_ACCEPTED、UNKNOWN 时固定为 `1`，FAILED、RECONCILED 时固定为 `NULL`；同一 Tax Return 最多一个活动付款。

创建 Tax Payment 时按 Account ID 顺序锁定精确 Tax Return、Tax Payable Account 与 Bank Cash Account。当前 Return 下全部非 FAILED Payment 金额加新金额不得超过已批准申报应付额；同时必须以 `tax_payable_account.net_balance - 同一 tax_payable_account_id 下全部既有活动 Tax Payment.amount` 计算该应付税账户的剩余可承诺额，并要求新金额不超过该结果。这个聚合跨 Tax Return，防止两个 Return 共用同一 TAX_PAYABLE Account 时分别通过、合计超额。UNKNOWN 保持 `active_slot=1`；FAILED 必须与权威未扣款证据 Event 同事务并在锁定 Tax Payable 与 Bank Cash Account 后释放活动槽；替代执行使用新的 `payment_no` 与 Execution Key。只有完整银行 Debit Recon 才在锁定两类 Account 的同一事务生成 `Dr TAX_PAYABLE / Cr BANK_CASH` Journal 并进入 RECONCILED。

## 4. Settlement 与 Tax Payment 的共同 Bank Cash 门禁

不保存另一份 Bank Cash Position。以 `owner_type=CASH_ACCOUNT`、`owner_id=t_cash_account.id`、科目为 `BANK_CASH`、`balance_bucket=NONE` 的 `t_account` 行作为共同并发互斥点。

所有 Settlement Attempt 创建或重试、Settlement 取消、Tax Payment 创建或失败、两类完整对账，以及改变该 Bank Cash Account 的 Journal，都必须在同一本地事务锁定对应 Account。锁内计算：

```text
可承诺现金
= Bank Cash Account.net_balance
- 当前银行账户上的活动 Settlement Payment 金额
- 当前银行账户上的活动 Tax Payment 金额
```

活动 Settlement Payment 状态为 `CREATED/SUBMITTED/PROCESSING/UNKNOWN/RETRY_REQUIRED`，但必须具有非空 `current_attempt_id`，并按该当前 Attempt 的 `cash_account_id` 归入银行账户；没有当前 Attempt 的 CREATED Payment 不进入任何银行账户的活动金额。即使 Attempt 已权威 FAILED，Settlement Payment 在 RETRY_REQUIRED 时仍保留原银行账户承诺，直到原子重试换账户或受控取消。

活动 Tax Payment 状态为 `CREATED/SUBMITTED/PROCESSING/BANK_ACCEPTED/UNKNOWN`。FAILED 不再占用 Bank Cash，但 Ledger Tax Payable 不变；RECONCILED 已由 Journal 减少 Bank Cash，因此不再计入活动金额。

跨账户重试必须按 Account ID 升序同时锁旧、新 Bank Cash Account，排除自身旧承诺后再增加新承诺，任一校验失败则全部回滚。未创建当前 Attempt 的 Settlement 取消不锁不存在的 BANK_CASH Account，只锁 Settlement 与 LOCKED/AVAILABLE Account；存在当前 Attempt 或处于 RETRY_REQUIRED 时，取消还必须锁该 Attempt 的精确 BANK_CASH Account。该门禁是由 Account 与业务状态实时派生的并发控制，不新增 Availability、Balance Lot、Funding 或 Treasury 表。

Provider Credit/Debit 完整匹配、Settlement/Tax Debit 完整匹配以及任何会改变 BANK_CASH 的对账调整 Journal，也必须在过账前按 Account ID 升序锁定对应 BANK_CASH Account。外部已经发生的银行 Debit 是事实，即使会令可承诺现金变成负数也不得因“容量不足”而拒绝入账；Journal 更新余额后，后续 Settlement/Tax 承诺必须立即看到该新余额并失败关闭。

## 5. 审计与幂等

- Settlement 使用 Settlement State Change、Settlement Payment Event、Recon Match、Accounting Instruction 与 Journal 形成完整证据链；
- Tax Payment 使用 Tax Payment Event、Recon Match、Accounting Instruction 与 Journal 形成完整证据链；
- Settlement 继续使用 `cash_account_id + submission_key + request_hash` 幂等；
- Tax Payment 继续使用 `cash_account_id + execution_key + request_hash` 幂等；
- UNKNOWN 不追加虚假 Journal，旧 FAILED Attempt 的迟到 Debit 进入 `LATE_DEBIT` Recon Exception；
- 旧 FAILED Settlement Attempt 或 FAILED Tax Payment 后续出现真实 Debit 时，先锁定原 BANK_CASH Account，以 `LATE_DEBIT:<bank_item_id>` 幂等创建 `Dr RECON_SUSPENSE_ASSET / Cr BANK_CASH` 暂记 Journal，再创建 `LATE_DEBIT` Recon Exception；旧执行不得改回普通成功，任何新 Attempt/替代 Tax Payment 的现金承诺保持不变，后续经审批的 Recon Resolution 只重分类暂记资产；
- Account Projection 漂移只能通过受控重建修复，不能追加假 Journal。

## 6. 预期模型基线

纯删除四表及五个外部 Funding 字段后的基线为 103 表、1,281 字段、171 个唯一索引、165 个访问索引、176 条关系、29 条 1:1 关系和 8 个 Diagram Groups。

加入四个直接替代字段、一个 Tax 活动槽唯一索引、四个必要访问索引与两条 Account 关系后，目标基线为：

- 103 表；
- 1,285 字段；
- 172 个唯一索引；
- 169 个访问索引；
- 178 条本库关系；
- 29 条 1:1 关系；
- 8 个 Diagram Groups。

Tax/Fiscal Diagram 继续保持 23 张主表；原“余额、准备金与资金承诺”Diagram 改为“余额、准备金与追偿”，从 10 张表减少为 6 张表。

## 7. 边界

本轮同步修改 Finance PDManer 生成器、独立 Oracle、自动测试、权威数据库文档、现实场景、requirements-site 与共享统计。退款 Funding 的负向防回归断言继续保留。

本轮不创建 Java、Migration、物理数据库、部署环境或基础设施，不提交 Git。PDManer 4.9.3 应用内人工兼容检查仍是未完成门禁。
