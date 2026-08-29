# Finance 手工出款与共同余额成熟精简设计

日期：2026-08-28
状态：用户已确认；Risk 稳定坐标已收敛为 `assessment_id + evaluation_id`，尚未实施；PDManer、生成器、测试、权威文档和共享入口均未据此修改

## 1. 决策摘要

Finance V1 不再把“系统定时生成结算候选、结算义务、结算付款聚合”拆成多层模型。Client 或 Local Partner 只能针对已经成熟到 `AVAILABLE` 的应付余额主动提交出款申请；Finance 完成固定双人审批后，由 Operations 在外部银行人工打款，保存付款凭证，并以银行 Statement 的最终借记对账完成出款和会计清偿。

本设计明确区分两个容易混淆的概念：

- **余额成熟周期**：保留。交易成功后，Client Principal 与 Local Partner Share 都先进入 `PENDING`；二者在该交易冻结的同一个 Client Agreement-OE `T+N` 时点成熟为 `AVAILABLE`。
- **自动出款周期**：删除。V1 不再由 `Settlement Run` 按周期扫描 `AVAILABLE`、生成 Candidate 或自动创建付款；没有用户主动申请就不产生出款。

法定和会计债务继续由 `t_account + Journal` 表达。`t_payout` 只是清偿既有 Client/Partner Payable 的业务执行根，不再创建一层语义重复的 Settlement Obligation。

## 2. 系统边界

### 2.1 Client & Contract

CCT 继续拥有 Client、Local Partner、Party、Operating Entity、Agreement，以及 Client Agreement-OE 的成熟输入：

- `settlement_business_days`；
- `business_calendar_code`；
- `settlement_timezone`；
- `cutoff_second_of_day`；
- 固定 `FOLLOWING` 工作日滚动语义。

CCT 不保存银行账号或受益人账户。Client Console 可以在 Onboarding 流程中展示受益人账户录入步骤，但该命令经 Gateway 直接路由到 Finance。页面所在流程不改变数据事实所有权。

### 2.2 Finance

Finance 拥有：

- 受益人账户及其审核状态；
- Client/Local Partner 的 `PENDING/AVAILABLE/LOCKED` Account；
- 合同成熟任务与 Journal；
- 出款申请、审批、人工执行和付款证据；
- 银行账户、Statement、Bank Recon Match 与最终付款 Journal；
- Client Security Deposit、Client Recovery Receivable 与 Partner Recovery Receivable Account。

### 2.3 Vault 与 Platform Support

- 受益人姓名和银行账号原值只进入 Vault；Finance 保存自己的 Token 及已证明有消费者的最小元数据。
- Platform Support 保存付款回执、未扣款证明和对账支持文件；Finance 只保存 `file_id + file_content_hash`，不保存文件正文或临时下载地址。

六库之间不建立外键、View、跨库 Repository 或直接读写。

## 3. Client 与 Local Partner 共用成熟时点

### 3.1 到期时间

`PaymentSucceeded` 处理时，Finance 根据该交易冻结的 Client Agreement-OE 成熟条款计算一个不可变时间。`t_transaction_economics.client_available_time` 改名为：

```text
balance_available_time
```

该字段同时控制 Client Principal 与 Local Partner Share，不再暗示只属于 Client。后续 Agreement 修改不得回写历史 Economics。

### 3.2 支付成功入账

支付成功时，以下只展示与 Client/Partner 应付相关的行；它们属于完整平衡 Journal 的组成部分，不是另起一张不平衡 Journal：

```text
Client Principal:
Cr CLIENT_PAYABLE / PENDING

Local Partner Share:
Dr PARTNER_SHARE_EXPENSE
Cr PARTNER_PAYABLE / PENDING
```

Local Partner Share 不再直接进入 `AVAILABLE`。

### 3.3 到期成熟

到达 `balance_available_time` 后，同一个 Economics 级幂等任务在同一事务重算已成功退款、拒付更正和此前已成熟金额，并追加一笔平衡 Journal：

```text
Dr CLIENT_PAYABLE / PENDING
Cr CLIENT_PAYABLE / AVAILABLE

Dr PARTNER_PAYABLE / PENDING
Cr PARTNER_PAYABLE / AVAILABLE
```

不存在 Local Partner 时只生成 Client 行；Partner Share 为零时不生成零金额行。成熟事件键固定改为 Economics 级共同语义：

```text
PAYABLE_BALANCE_MATURED:<transaction_economics_id>
```

### 3.4 退款、拒付和 Partner 追偿

退款或其他权威冲回发生时，Partner Share 按当时状态处理：

1. 尚未成熟：冲减 `PARTNER_PAYABLE/PENDING`；
2. 已成熟但未锁定：冲减 `PARTNER_PAYABLE/AVAILABLE`；
3. 已进入 `LOCKED`、已经支付或余额不足：不足部分形成资产类 `PARTNER_RECOVERY_RECEIVABLE/NONE` Account；
4. 后续 Partner Share 先在成熟事务中与该 Recovery Receivable 合法净额抵扣，只有剩余部分进入 `PARTNER_PAYABLE/AVAILABLE`。

该追偿只增加 Chart Account/Account/Journal 语义，不新增 Partner Recovery Case、Application、Allocation 或 Move 表。Partner 有未清偿 Recovery Receivable 时不得创建新出款。

Client 继续使用既有 `CLIENT_RECOVERY_RECEIVABLE/NONE` 和 `CLIENT_SECURITY_DEPOSIT/NONE` 规则；Local Partner 不适用 Client Security Deposit。

## 4. 单表受益人账户

删除 `t_beneficiary_account_ver`。一行 `t_beneficiary_account` 就是一个可被历史出款精确引用的受益人账户版本。

### 4.1 所有者

```text
owner_type = CLIENT | LOCAL_PARTNER
owner_id   = 对应 Client ID 或 Local Partner ID
```

Client 出款只能选择同一 Client 的账户，Local Partner 出款只能选择同一 Local Partner 的账户。受益人账户本身不绑定一个 Operating Entity；同一 Owner 可以在多个 OE 的出款中使用同一账户，但每笔 Payout 仍必须只属于一个 Owner、一个 OE 和一种币种，不能跨 OE 合并余额或出款。

### 4.2 不可变规则

- `DRAFT` 阶段允许修改；
- 提交审核后，受益人、银行账号、开户国家、银行、币种和 Vault 元数据冻结；
- 审核采用不同 Maker/Checker，并冻结 `content_hash`；
- `VERIFIED` 才允许被新 Payout 引用；
- 更换银行账号或任何冻结字段时创建新的 `beneficiary_account_id`，旧记录进入 `SUSPENDED` 或 `CLOSED`；
- 已创建的 Payout 永远使用其冻结的 `beneficiary_account_id + beneficiary_content_hash`，不得因当前账户变化而切换收款目的地；
- 每次创建 Execution 以及 Operations 真正向外部银行提交前，都必须确认该账户仍为 `VERIFIED` 且当前 `content_hash` 与 Payout 冻结值一致；不一致时只能中止尚未外部提交的 Execution；
- 已经提交银行或处于 `UNKNOWN` 的执行即使账户后来关闭，也只能按原冻结目的地解决。

建议保留的核心字段包括：Owner、账户业务编号、开户国家、币种、银行代码、Finance 专属 Vault Token、受益人脱敏名称、账号末四位、必要的 Blind Index/Key Version、Vault Record Version/Metadata Hash、`content_hash`、审核人和状态。只有存在等值查重消费者时才保留 Blind Index，不机械补全 Vault Tuple。

## 5. 最终七表模型

### 5.1 `t_beneficiary_account`

受益人账户及其唯一审核生命周期，按上一节定义。

### 5.2 `t_finance_restriction_proj`

继续维护 CCT/Risk 当前出款限制的来源 ID、来源版本、事件键、Payload Hash、Owner/OE/币种 Scope 与当前决定。该表不是 Settlement Obligation，也不冻结资金。

新 Payout 创建、Finance 最终审批、Execution 创建和每次人工银行提交前，都必须重新调用 RCO-I09 并对非成功、技术失败或限制决定 Fail-Closed。Risk 不新增 Eligibility Result Root；Finance 使用现有不可变事实的稳定二元坐标：

```text
assessment_id + evaluation_id
```

Risk 保证 `t_settlement_risk_assessment` 与 `decision_evaluation` 通过 Evaluation ID 形成不可变 1:1，并在 Assessment 冻结 `checkpoint_type/role_type/role_id/operating_entity_id/gross_amount/currency/bank_country/source_fact_type/id/version/input_hash` 以及最终 Decision/Required Amount/Currency/Control Set Hash。Finance 在 Payout 最终审批和每个 Execution 各自冻结当次坐标与 RCO-I09 返回的 `decision/reason_codes/matched_controls/required_amount/required_currency/control_set_hash`，并连同 Owner/OE/币种/Payout 金额 Scope 计算本地 Canonical `eligibility_hash`。

Finance 的 `eligibility_hash` 只是本地快照完整性与幂等证据，不替代 Risk `input_hash/control_set_hash`，也不能让后续执行跳过新的 RCO-I09 Evaluation。查询或重试必须同时校验 Assessment/Evaluation 1:1、Scope 逐值一致以及 Control Set 可重建；任何记录缺失、Hash 不一致或无法长期重取都 Fail-Closed。由 Payout/Execution 行直接冻结这些结果后，不再建立 Candidate/Settlement Requirement Snapshot 子表。

### 5.3 `t_payout`

合并原 `t_payout_request + t_settlement + t_settlement_payment`，一行贯穿主动申请、余额锁定、审批、人工付款和银行对账结果。

核心业务字段至少覆盖：

- `payout_no` 与命令幂等身份；
- `owner_type/owner_id`；
- `operating_entity_id`、币种；
- `beneficiary_account_id + beneficiary_content_hash`；
- `available_account_id + locked_account_id`；
- `requested_amount + fee_mode + fee_amount`；
- `owner_debit_amount + beneficiary_amount + cash_required_amount`；
- 最终审批采用的 `assessment_id + evaluation_id`、完整结果字段与本地 `eligibility_hash`；
- 不可变 `request_hash`、填充一次的 `approval_hash`、状态、并发版本和关键时间；
- 完整银行对账后的 `journal_entry_id + reconciled_time`。

Payout 创建前，系统根据当前受控出款费规则给出 Fee Quote，用户选择费用模式并确认全部金额。V1 不为此新增费率配置表。Payout 一旦创建，Finance Maker 只能核对，不能修改任何金额或费用模式；变化必须取消原 Payout 并创建新申请。

手续费始终由 Client/Local Partner 的余额承担，不存在本设计内的“平台承担手续费”。本设计明确选择“平台只是替 Owner 向银行支付费用”的代付列报政策：合同必须明确该银行费由 Owner 直接承担，Finance 才能只清偿 Owner Payable 而不确认平台手续费收入或费用。若法律合同实际是“银行向平台收费、平台再向 Owner 收费”，则必须重新评审 Gross/Net 列报并设计独立收入/费用分录，不能套用本设计。两个模式定义如下：

| 费用模式 | 用户输入 | 手续费 | AVAILABLE 扣款 | 受益人到账 | 银行现金需求 |
|---|---:|---:|---:|---:|---:|
| `ADDED_ON_TOP`（外扣） | 100 | 2 | 102 | 100 | 102 |
| `DEDUCTED_FROM_PAYOUT`（内扣） | 100 | 2 | 100 | 98 | 100 |

精确公式为：

```text
ADDED_ON_TOP:
  owner_debit_amount = requested_amount + fee_amount
  beneficiary_amount = requested_amount
  cash_required_amount = owner_debit_amount

DEDUCTED_FROM_PAYOUT:
  owner_debit_amount = requested_amount
  beneficiary_amount = requested_amount - fee_amount
  cash_required_amount = owner_debit_amount

Both:
  beneficiary_amount + fee_amount = owner_debit_amount
  cash_required_amount = owner_debit_amount
```

金额必须大于零；内扣还要求 `requested_amount > fee_amount`。未来如平台补贴手续费，应作为新的独立会计政策设计，不能复用这两个模式。

`request_hash` 在创建时覆盖 Owner/OE/币种、受益人 Hash、全部金额、Fee Mode 和 Account 坐标，之后永不改写。最终审批将 `request_hash + Assessment/Evaluation 坐标 + 完整结果字段 + eligibility_hash` 组成 `approval_hash` 并填充一次；审批后权限或限制变化只能阻断执行，不能篡改已批准内容。

### 5.4 `t_payout_approval`

追加保存需要双人控制的 Maker-Checker 结果。`action_type` 区分：

- `PAYOUT_APPROVAL`；
- `PAYOUT_CANCEL`。

每个 Action 的 Maker 与 Checker 必须不同，`approved_content_hash` 必须等于对应 Payout Approval Hash 或取消计划 Hash。以 `payout_id + action_type + action_no` 区分多次取消申请；一次被拒绝的取消不得阻止后续重新申请，也不能覆盖旧审批事实。

用户在 `PENDING_APPROVAL`、没有 Execution 时主动撤回，不属于财务批准后的取消，不要求额外 Maker-Checker；该动作仍必须追加 Payout Event 和幂等释放 Journal。`APPROVED/ON_HOLD/RETRY_REQUIRED` 的取消必须经过 `PAYOUT_CANCEL` Maker-Checker。

### 5.5 `t_payout_execution`

Operations 每次准备在外部银行人工提交或重试前先创建一行，保存：

- `payout_id + attempt_no`；
- `cash_account_binding_ver_id + cash_account_id`；
- `execution_key + request_hash`；
- 本次提交采用的最新 `assessment_id + evaluation_id`、结果字段与本地 `eligibility_hash`；
- 银行付款引用；
- `CREATED/SUBMITTED/FAILED/UNKNOWN/ABORTED/RECONCILED` 状态；
- `active_slot`、创建、提交、解决时间与并发版本。

同一 Payout 最多一个活动 Execution。`CREATED/SUBMITTED/UNKNOWN` 占用精确 `BANK_CASH` Account 的派生现金容量。`CREATED` 只有在系统确认 Operations 尚未向外部银行提交时才能进入 `ABORTED` 并释放容量；`SUBMITTED/UNKNOWN` 不能 Abort。

`UNKNOWN` 禁止重试。只有银行权威证明没有扣款，Execution 才能进入 `FAILED`，释放 Bank Cash 容量并令 Payout 进入 `RETRY_REQUIRED`；Owner Liability 继续保留在 `LOCKED`。下一次重试必须创建新的 Execution，并重新检查全部门禁和现金容量。

### 5.6 `t_payout_evidence_ref`

一条 Execution 可以引用多份不可变 Platform 文件：

- `PAYMENT_PROOF`；
- `NO_DEBIT_PROOF`；
- `BANK_CONFIRMATION`；
- `RECONCILIATION_SUPPORT`。

每行必须保存 `file_id + file_content_hash`，禁止只存文件 ID、文件正文或 URL。

### 5.7 `t_payout_event`

保留一张出款专用、追加式审计表，统一替代原 `t_settlement_state_change` 与 `t_settlement_payment_event`。它记录：

- Payout/Execution 状态变化；
- `from_status + to_status + reason_code`；
- 操作人、来源类型/来源 ID、幂等事件键和发生时间；
- 可选 Approval、Evidence、Bank Match、Journal 坐标。

`event_scope=PAYOUT|EXECUTION`；`execution_id` 仅在 EXECUTION Scope 必填，并通过 XOR CHECK 防止错挂。每个 Payout 在锁定 Root 后分配严格递增的 `event_no`，唯一键为 `(payout_id, event_no)`；事件同时冻结 `payout_version_after` 和可选 `execution_version_after`，必须与同一事务更新后的 Root/Execution Version 一致。来源事件键另行唯一，保证重放既不跳号也不重复追加。

该表不承担异步投递，也不替代 Payout/Execution 当前状态或 Journal 会计事实。它只解决“谁在何时因什么证据改变了出款”的连续审计问题，并必须与对应业务状态在同一 Finance 本地事务追加。

## 6. 出款状态和资金动作

### 6.1 创建申请并锁定 Owner 余额

Client 或 Local Partner 只能请求本 Owner、单一 OE、单一币种的 `AVAILABLE` 余额。创建事务完成最新限制检查，按 Account ID 升序锁定 AVAILABLE 与 LOCKED Account，校验 Fee 公式，并按 `owner_debit_amount` 追加：

```text
Dr CLIENT_PAYABLE or PARTNER_PAYABLE / AVAILABLE   owner_debit_amount
Cr CLIENT_PAYABLE or PARTNER_PAYABLE / LOCKED      owner_debit_amount
```

然后创建 `t_payout(status=PENDING_APPROVAL)` 和事件。锁款发生在申请创建时，防止同一余额被多个申请重复使用。Journal 幂等来源键固定为：

```text
PAYOUT_LOCK:<payout_id>
```

### 6.2 审批、拒绝与撤回

Finance Maker/Checker 再次检查 Owner、OE、币种、受益人账户、全部冻结金额、最新 Eligibility 和账户门禁：

- Client：`CLIENT_RECOVERY_RECEIVABLE=0`，且 `CLIENT_SECURITY_DEPOSIT >= Risk required_amount`；
- Local Partner：`PARTNER_RECOVERY_RECEIVABLE=0`；
- 两者：当前出款限制必须为 `ALLOW`。

审批通过时填充一次 `approval_hash`、Assessment/Evaluation 坐标、完整结果字段和本地 `eligibility_hash`，进入 `APPROVED`。拒绝或合法撤回在同一事务按 `owner_debit_amount` 追加 `LOCKED -> AVAILABLE` Journal。任何终止释放都只能发生一次，幂等来源键固定为：

```text
PAYOUT_RELEASE:<payout_id>
```

### 6.3 先占用银行现金，再由 Operations 外部提交

禁止 Operations 先打款、Finance 后补 Execution。正确顺序是：

1. 系统在 Finance 本地事务锁定实际付款 `BANK_CASH` Account，重验最新限制、Eligibility、受益人 `VERIFIED + content_hash` 和 Owner Account 门禁；
2. 计算现金容量，足够时创建 `t_payout_execution(status=CREATED)`、稳定 `execution_key` 和事件，从此占用银行现金容量；
3. Operations 以该 `execution_key` 为内部操作依据，在外部银行人工提交；
4. 提交成功后登记银行付款引用、回执 Hash，Execution 进入 `SUBMITTED`，Payout 进入 `PAYING`；结果不明确则进入 `UNKNOWN/PAYMENT_UNKNOWN`；
5. 如果尚未外部提交且需要放弃，Execution 进入 `ABORTED` 并释放 Bank Cash 容量；Payout 保持原 `APPROVED` 或 `RETRY_REQUIRED` 状态。

现金容量计算为：

```text
available_cash
= bank_cash_account.net_balance
- 同一 cash_account_id 下 CREATED/SUBMITTED/UNKNOWN Execution 所属 Payout.cash_required_amount
- 同一 cash_account_id 下现有活动 Tax Payment.amount
```

普通 PDF、截图或银行“已受理”回执只能证明已提交，不能产生最终付款 Journal。

### 6.4 未扣款、未知与重试

- `CREATED -> ABORTED`：仅限确定尚未向银行提交，释放 Bank Cash 容量，不释放 Owner LOCKED Liability；
- `SUBMITTED/UNKNOWN -> FAILED`：仅限权威未扣款证据，释放 Bank Cash 容量，Payout 进入 `RETRY_REQUIRED`，Owner Liability 仍为 LOCKED；
- `UNKNOWN`：继续占用 Bank Cash，禁止重试、取消、替代付款或人工强制完成；
- 外部银行立即明确拒绝：仍按实际发生顺序追加 `CREATED -> SUBMITTED -> FAILED` 两个事件；不得把已经向银行提交的尝试伪装成从未提交的 ABORTED；
- 重试：创建新 Execution，并重新检查现金、受益人、限制、Eligibility、保证金和 Recovery；如果最新门禁阻断，Payout 保持 `RETRY_REQUIRED`，追加阻断事件，不进入 `ON_HOLD`，恢复后再重新检查。

### 6.5 银行 Statement 对账和最终付款

现有 `t_bank_recon_match`：

- 将 `settlement_payment_attempt_id` 改为 `payout_execution_id`；
- 将 `SETTLEMENT_DEBIT` 改为 `PAYOUT_DEBIT`；
- 继续冻结 Cash Binding Version、Bank Statement Item、匹配金额、决定人和决定时间；
- 允许同一 Execution 对应一条合并 Debit，或对应“受益人转账 + 银行手续费”等多条 Statement Item；每条 Statement Item 仍不得被重复确认到多个资金用途；
- Payout Match 必须标记 `BENEFICIARY_TRANSFER/BANK_FEE/COMBINED` 组成。拆分行分别核对 `beneficiary_amount` 与 `fee_amount`；合并行必须以相同 Bank Payment Ref 或权威银行证据绑定该 Execution，不能仅因两笔无关 Debit 的总和碰巧相等就确认。

正常路径只有 `CONFIRMED` Debit Match 累计恰好等于 `t_payout.cash_required_amount` 时，才在同一事务锁定 Payout、Execution、LOCKED Liability、BANK_CASH 与匹配集合，追加：

```text
Dr CLIENT_PAYABLE or PARTNER_PAYABLE / LOCKED   owner_debit_amount
Cr BANK_CASH                                    cash_required_amount
```

由于两个模式都满足 `owner_debit_amount = cash_required_amount`，Journal 始终平衡。标准路径不记 `PAYOUT_FEE_EXPENSE` 或平台手续费收入：银行费已由 Owner Payable 一并清偿。随后 Execution 进入 `RECONCILED`、Payout 进入 `PAID`，付款 Journal 幂等来源键固定为：

```text
PAYOUT_PAID:<payout_id>
```

V1 正常路径要求 Fee Quote 与银行实际费精确一致。如果银行实际扣款或手续费与冻结金额不一致，不允许回写 Payout，先创建 Recon Exception，Payout 保持非终态：

- 实际少扣：付款 Journal 只按真实 Debit 清偿同额 LOCKED Liability，再经批准追加 `LOCKED -> AVAILABLE` 差额释放 Journal。只有“已确认 Debit + 已批准释放差额 = owner_debit_amount”，且受益人实付等于冻结值时，才能进入 `PAID`；
- 实际多扣：只把冻结金额分配给 Payout，超额 Debit 单独进入 Recon Exception；在本设计的 Owner 承担政策下，经批准记入 Owner Recovery Receivable，或在获得追加授权且余额充足时扣除 Owner AVAILABLE。Payout 只有在超额也完成独立会计处置后才能进入 `PAID`；
- 受益人转账金额本身不符：不得用手续费差额规则掩盖，必须保持异常并人工纠正。

这些差额都通过现有 Recon Exception 与 Journal 处理，不新增 Payout Adjustment 表；差额 Journal 的幂等来源键使用 `PAYOUT_FEE_DIFF:<recon_exception_id>`。PROPOSED、REJECTED、未解决部分/超额匹配、回执上传或 BANK_ACCEPTED 均不能完成付款。

### 6.6 取消和迟到扣款

- 用户可在 `PENDING_APPROVAL` 且没有 Execution 时直接撤回；
- `APPROVED/ON_HOLD/RETRY_REQUIRED` 只有在不存在任何可能扣款的 Execution，且经过 `PAYOUT_CANCEL` Maker-Checker 时才能取消；
- `CREATED` 必须先按未提交证明进入 `ABORTED`，再批准取消；
- `SUBMITTED/UNKNOWN`、部分银行借记或存在未决 Bank Match 时禁止取消；
- 旧 FAILED Execution 后续出现迟到 Debit 时，沿用现有 `LATE_DEBIT` 暂记 Journal 与 Recon Exception，不能把旧执行静默改成普通成功。

## 7. 状态机

`t_payout.status`：

```text
PENDING_APPROVAL
  -> APPROVED
  -> REJECTED
  -> CANCELLED       requester withdrawal, no Execution

APPROVED
  -> ON_HOLD
  -> PAYING          only when Execution is externally submitted
  -> CANCELLED       maker-checker, no possible debit

ON_HOLD
  -> APPROVED
  -> CANCELLED       maker-checker, no possible debit

PAYING
  -> PAYMENT_UNKNOWN
  -> RETRY_REQUIRED  only after authoritative no-debit result
  -> PAID            only after complete confirmed bank debit match

PAYMENT_UNKNOWN
  -> RETRY_REQUIRED  only after authoritative no-debit result
  -> PAID            only after complete confirmed bank debit match

RETRY_REQUIRED
  -> PAYING          only when a new Execution is externally submitted
  -> CANCELLED       maker-checker, all attempts proven no-debit
```

`t_payout_execution.status`：

```text
CREATED
  -> SUBMITTED
  -> ABORTED         only when no external submission occurred

SUBMITTED
  -> UNKNOWN
  -> FAILED          only with authoritative no-debit evidence
  -> RECONCILED      only with complete confirmed bank debit match

UNKNOWN
  -> FAILED          only with authoritative no-debit evidence
  -> RECONCILED      only with complete confirmed bank debit match
```

`PAID/REJECTED/CANCELLED` 是 Payout 终态；`FAILED/ABORTED/RECONCILED` 是 Execution 终态。每次状态变化都必须由 `t_payout_event` 连续记录，并由 Approval、Evidence、Bank Recon Match 或 Journal 提供可追溯依据。

## 8. 删除与替换清单

从当前 20 张 Settlement 表中删除：

- `t_beneficiary_account_ver`；
- `t_settlement_profile`；
- `t_settlement_profile_ver`；
- `t_profile_review`；
- `t_payout_request`；
- `t_settlement_run`；
- `t_settlement_candidate`；
- `t_candidate_requirement_snapshot`；
- `t_settlement`；
- `t_settlement_req_snapshot`；
- `t_settlement_approval`；
- `t_settlement_cancel`；
- `t_settlement_cancel_approval`；
- `t_settlement_state_change`；
- `t_settlement_payment`；
- `t_settlement_payment_attempt`；
- `t_settlement_payment_event`；
- `t_settlement_evidence_ref`。

保留并改造：

- `t_beneficiary_account`；
- `t_finance_restriction_proj`。

新增：

- `t_payout`；
- `t_payout_approval`；
- `t_payout_execution`；
- `t_payout_evidence_ref`；
- `t_payout_event`。

Settlement 图组因此从 20 张表收敛为 7 张，Finance 总表数预计从 97 张降到 84 张。这里保留一张统一 Payout Event，是为了避免删除原两张历史表后失去状态原因、证据和操作人的连续审计链；它不是恢复通用 Event 模型。字段、索引、关系和 1:1 数量必须由实现后的 Generator/Oracle 实算，不在设计阶段手工承诺。

## 9. 不变量

1. Journal 是唯一不可变会计事实；Account 是可重建余额投影。
2. T+N 成熟保留，且 Client Principal 与 Local Partner Share 使用同一交易冻结时点。
3. 自动 Settlement Run/Candidate 删除；V1 只有主动出款。
4. 受益人账户录入可以属于 Onboarding 页面，但表和 Vault Purpose 归 Finance。
5. 一行受益人账户就是一个冻结版本；审核后不覆盖银行资料。
6. Client 与 Local Partner 都只能从 `AVAILABLE` 申请，并在创建时按 `owner_debit_amount` 转为 `LOCKED`。
7. 外扣锁 `requested + fee`、到账 `requested`；内扣锁 `requested`、到账 `requested - fee`。
8. 两种模式都由 Owner 余额承担，且 `owner_debit_amount = cash_required_amount`。
9. 每笔 Payout 只属于一个 Owner、OE、币种、受益人账户和 LOCKED Account。
10. Client/Partner、不同 OE、不同币种的余额和出款禁止合并。
11. Local Partner Recovery Receivable 非零时禁止 Partner 出款；未来 Share 先抵扣追偿。
12. Client 保证金与追偿继续按现有 Account 门禁；Partner 不复用 Client 保证金。
13. Operations 外部提交前必须先创建 CREATED Execution 并占用 BANK_CASH 容量。
14. 上传回执不等于 `PAID`；只有完整 CONFIRMED Bank Debit Match 可完成付款。
15. UNKNOWN 不释放锁定 Liability 或 Bank Cash 容量，不允许重试、取消或替代。
16. FAILED 释放 Bank Cash 容量但不释放 Owner LOCKED Liability；重试重新检查现金。
17. 受益人账户后来关闭不得改写已提交 Execution 的目的地。
18. 实际银行费与冻结金额不符只能进入 Recon Exception，不能修改 Payout。
19. 银行费代付列报只有在合同明确由 Owner 直接承担时适用。
20. Assessment/Evaluation 及 Finance 本地 Eligibility Hash 必须绑定完整 Payout Scope，禁止跨 Owner/OE/币种/金额复用。
21. Payout Event 在每个 Payout 内严格连续，不得缺号、重号或与 Root Version 不一致。
22. 所有文件引用保存 `file_id + file_content_hash`，所有 Vault 字段遵守最小消费者原则。
23. 不恢复 Funding、Availability、Balance Lot、Reserve/Recovery Workflow 或 Provider Cash Item。

## 10. 验证范围

实施时必须至少覆盖：

- Client 与 Local Partner 同时 PENDING、同一 T+N 成熟；
- 无 Partner、零 Partner Share 和重复成熟幂等；
- 成熟前退款、成熟后退款、LOCKED/PAID 后 Partner Recovery；
- Partner Recovery 后未来 Share 抵扣和出款阻断；
- 单表 Beneficiary DRAFT 修改、审核冻结、换账户新 ID、历史 Payout 防漂移；
- Execution 创建和外部提交前的 Beneficiary VERIFIED/Hash 重验；
- Client/Partner Owner 混用、错 OE、错币种、错账户全部拒绝；
- 外扣 100+2 锁 102、实收 100、银行需求 102；
- 内扣 100+2 锁 100、实收 98、银行需求 100；
- 内扣手续费大于等于申请金额、公式不守恒全部拒绝；
- 并发双 Payout 不得超额锁定 AVAILABLE；
- Maker/Checker 不同人和 Approval Hash 一致；
- 同一 Payout 多次取消申请以 Action No 隔离，拒绝记录不得被覆盖；
- Finance Maker 不能修改已冻结金额或 Fee Mode；
- CREATED Execution 先占用现金、未提交可 ABORT、已提交不可 ABORT；
- 银行立即拒绝仍产生 CREATED→SUBMITTED→FAILED 连续事件；
- 回执上传不进入 PAID；
- UNKNOWN 禁止 Retry/Cancel，权威未扣款后才可 Retry；
- FAILED 释放 Bank Cash 但保留 Owner LOCKED，Retry 重新检查容量；
- Bank Cash 容量与 Tax Payment 共同锁定；
- 单条合并 Debit 与多条“转账 + 手续费”Statement Item 均可完成同一 Execution；
- 部分/超额/错方向/错 Cash Binding/重复 Statement Item Bank Match；
- 实际少收费用按真实 Debit + 差额释放闭合，实际多收费用按超额独立处置闭合，均不回写 Payout；
- 完整 Debit Match 原子生成平衡 Journal、更新 Account、Execution、Payout 和 Event；
- 直接撤回、审批拒绝、批准后取消都只生成一次 `PAYOUT_RELEASE` Journal；
- 迟到 Debit 暂记与 Recon Exception；
- Payout Event 的状态、原因、操作人、证据/审批/Journal 坐标连续可追溯；
- Payout Event 的 Scope/XOR、连续 Event No、来源幂等键和 Root/Execution Version 一致；
- Assessment/Evaluation 跨 Owner/OE/币种/金额 Scope 误用、1:1 断裂、Control Set 无法重建全部拒绝；
- 所有命令重放不生成第二张 Payout、Execution、Match、Event 或 Journal。

## 11. 实施边界

后续实施需要同步：Finance Manifest/Policy/Generator/Oracle/Tests、PDManer 模型、Finance 权威逻辑/现实场景/物理文档、requirements-site 需求与全局出款序列，以及六份共享统计入口。外围契约还必须原位同步：

- `t_account` Owner/Chart/Bucket 约束允许 Local Partner 的 `PENDING/AVAILABLE/LOCKED`；
- Chart/Accounting Rule 支持 `PARTNER_RECOVERY_RECEIVABLE`；
- Economics 与成熟任务支持 Partner PENDING；
- Cash Binding 用途由 `SETTLEMENT` 正名为 `PAYOUT`；
- Bank Recon 的外键、索引、CHECK 和类型从 Settlement Attempt 改为 Payout Execution；
- Journal 来源键采用 `PAYOUT_LOCK/PAYOUT_RELEASE/PAYOUT_PAID`。

Risk 已确认以不可变 `assessment_id + evaluation_id` 提供稳定来源坐标，并由 Assessment 冻结完整 Scope；Finance 不要求 Risk 新增 Eligibility Result Root/Version/Hash。Risk 最终生成统计、文档和门禁完成前，共享 requirements、全局序列和六份共享入口继续冻结；Finance 专属资产可先实施。

本设计不授权 Java、Migration、物理数据库、部署环境或基础设施，不提交 Git。PDManer 4.9.3 应用内人工兼容检查继续作为实施后的未完成人工门禁。
