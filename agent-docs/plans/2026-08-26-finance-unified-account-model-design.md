# Finance 统一账户模型设计

最后更新：2026-08-28
状态：统一账户决策最初落入历史 121/103 表阶段，当前 Finance 为 98 表；本文出现的 `RESERVED`、`CLIENT_RESTRICTED_FUNDS` 与 Reserve 工作流均为历史方案，已被[保证金 Account 与 Journal 追偿简化实施记录](./2026-08-27-finance-security-deposit-account-simplification-implementation-plan.md)替代；所有 generic Funding Position/Commitment/Allocation/Move 内容已被[通用 Funding 模块删除设计](./2026-08-27-finance-funding-module-removal-design.md)与[实施记录](./2026-08-27-finance-funding-module-removal-implementation-plan.md)替代。当前只保留不受影响的统一 Account、Journal 与科目汇总决策；自动记账规则的后续精简以[交易经济与会计规则精简实施计划](./2026-08-26-finance-transaction-economics-accounting-rule-simplification-implementation-plan.md)为准；当前结构以 Finance 权威数据库文档、物理决策和生成模型为准，PDManer 4.9.3 应用内人工评审待完成

## 目标

在保留 `t_ledger_book` 作为 `Book Entity（Operating Entity 或 Finance Entity）+ Currency` 会计边界的前提下，把原有 `t_ledger_account + t_subledger_account` 收敛为单一可过账账户 `t_account`，让 Journal Line 只引用一个账户，并把法定试算平衡与 Client、Partner 等明细余额分层处理。

## 核心结构

```text
t_chart_of_accounts -> t_chart_account
                              |
t_ledger_book ------------> t_account（身份 + 当前余额列）
                              ^
t_journal_entry -> t_journal_line

t_trial_balance_run -> t_trial_balance_line -> t_chart_account
```

- `t_ledger_book` 继续承载 Book Entity 类型与 ID、币种、Chart、状态、Journal Sequence 分配范围和逐账簿关账边界；实际取号状态由其唯一的 `t_book_sequence` 行维护。
- `t_account` 是唯一可过账账户实例，身份由 Book、Chart Account、Owner 和 Balance Bucket 共同确定。
- 总账控制余额不再保存成第二份账户数据，而是按 `ledger_book_id + chart_account_id` 汇总所有 `t_account` 余额列。
- Client、Partner、Provider、银行和税务机关的身份与当前余额只在同一 `t_account` 行保存一份。
- Trial Balance 和期间余额保持科目级，不为每个 Client 或 Partner 生成试算行。

## `t_account`

字段固定为：

```text
id
ledger_book_id
chart_account_id
owner_type
owner_id
balance_bucket
normal_side
account_status
debit_total
credit_total
net_balance
last_journal_sequence
balance_version
create_time
update_time
```

`entity_type`、`entity_id` 和 `currency` 由 `ledger_book_id` 推导，不重复存储；账户身份字段创建后不可修改。`normal_side` 在创建账户时从 `t_chart_account.normal_side` 复制并冻结，只允许 `DEBIT/CREDIT`，不能写成 `BOTH`，也不代表账户只能接受一侧分录。账户不使用混合身份与余额语义的通用 `version`：账户状态变更携带期望旧状态，余额投影并发控制由 `t_account.balance_version + last_journal_sequence` 承担。五个余额投影字段创建时全部初始化为零，`balance_version` 只代表余额列更新次数，不代表账户身份版本。

唯一身份：

```text
uk_account_identity(
  ledger_book_id,
  chart_account_id,
  owner_type,
  owner_id,
  balance_bucket
)
```

访问索引分别从 Owner 和 Chart Account 开始；Book + Chart 查询直接使用唯一索引左前缀，避免重复索引。

Owner 规则：

| Owner Type | Owner ID | Balance Bucket | 主要科目 |
|---|---|---|---|
| `OPERATING_ENTITY` | Book 的 OE ID | `NONE` | OE 账簿的收入、费用、权益和内部调节科目 |
| `FINANCE_ENTITY` | 本库 `t_finance_entity.id` | `NONE` | 平台财务主体账簿的收入、费用、权益和内部调节科目 |
| `CLIENT` | CCT Client ID | `PENDING/AVAILABLE/LOCKED/NONE` | Client Payable、Security Deposit、Recovery Receivable |
| `LOCAL_PARTNER` | CCT Local Partner ID | `AVAILABLE/LOCKED` | Partner Payable |
| `PROVIDER_ACCOUNT` | 本库 `t_provider_account.id` | `NONE` | Provider Receivable/Payable 和渠道在途 |
| `CASH_ACCOUNT` | 本库 `t_cash_account.id` | `NONE` | Bank Cash |
| `TAX_AUTHORITY` | 本库 `t_tax_authority.id` | `NONE` | Tax Payable |

V1 不保留没有独立法律账簿事实的 `PLATFORM` Owner。外部 Owner ID 不建立跨库外键；多态 Owner ID 由受控解析器和应用事务校验。Book Entity 的类型仅允许 `OPERATING_ENTITY/FINANCE_ENTITY`。

## 科目与余额桶

- `CLIENT_PAYABLE_PENDING/AVAILABLE/LOCKED` 合并为 `CLIENT_PAYABLE`。
- `PARTNER_PAYABLE_AVAILABLE/LOCKED` 合并为 `PARTNER_PAYABLE`。
- 生命周期由 `balance_bucket` 表达；Pending 到 Available 等移动仍产生两条平衡 Journal Line，但借贷落在相同 Chart Account 的不同 `t_account`。
- `CLIENT_SECURITY_DEPOSIT` 是独立 Client 负债科目并使用 `CLIENT + NONE`；不占用普通应付款余额桶，也不计入平台收入。
- `CLIENT_RECOVERY_RECEIVABLE` 保持独立资产科目并使用 `CLIENT + NONE`。

## Journal 与规则解析

- `t_accounting_rule_line` 保存可跨 Book 复用的 `account_code`，不能保存具体 `account_id`；它只映射已经计算好的金额来源、Owner Role、余额桶与借贷方向，不承担费率或分润计算。
- `owner_role` 由固定代码解释器解析 Owner Type 和 Owner ID；删除重复的 `subledger_owner_type`。
- `balance_bucket` 必填，不适用时使用 `NONE`。
- `t_instruction_line`、`t_journal_line`、`t_manual_adjustment_line` 只保存 `account_id`，不再保存 Ledger/Subledger 双账户。
- 行级 `ledger_book_id` 和 `currency` 删除；Account、Journal/Adjustment Header 与 Book 的不可变关系是唯一来源。
- 每条 Journal Entry 仍严格属于一个 Book，所有 Line Account 必须属于 Header Book，且借贷合计相等。

## 余额、试算和平账

- `debit_total/credit_total/net_balance/last_journal_sequence/balance_version` 直接并入 `t_account`，删除没有独立生命周期的 1:1 `t_account_balance`。
- 所有账户都允许 Journal Line 从借方或贷方进入；`normal_side=DEBIT` 时 `net_balance=debit_total-credit_total`，`normal_side=CREDIT` 时 `net_balance=credit_total-debit_total`。
- `net_balance` 是有符号余额：正数表示正常余额方向，负数表示反向余额。当前余额方向和绝对金额由 `normal_side + net_balance` 实时推导，不额外保存 `current_side`、`balance_direction` 或 `BOTH`。
- 负数允许表达短暂冲销、退款和调整，但真实且持续的反向债权债务使用成对科目/账户表达，例如 Client 欠款进入 `CLIENT_RECOVERY_RECEIVABLE`，Provider 净扣款进入 `PROVIDER_SETTLEMENT_PAYABLE`，不长期把应付款或应收款维护成负数。
- Journal Posting 只按 `balance_version + last_journal_sequence` 条件更新余额投影；账户按 ID 升序锁定，并在锁后重新确认 `account_status=ACTIVE`。全量重建从 Journal 重算 `debit_total/credit_total/net_balance/last_journal_sequence`，再以当前 `balance_version` 做 CAS 并单调加一；`balance_version` 是并发令牌，不能由 Journal 推导或重置。账户身份、Owner、Bucket、`normal_side` 和状态均不改变。
- 删除 `t_subledger_balance`，消除总账余额与子账余额两份可漂移投影。
- `t_account_period_balance` 重命名为 `t_ledger_period_balance`，按 `period_id + ledger_book_id + chart_account_id` 冻结科目期间余额；Opening/Closing Balance 都按科目正常方向保留有符号值，不取绝对值。
- `t_trial_balance_line` 使用 `chart_account_id`；所属 Book 由 `t_trial_balance_run.ledger_book_id` 固定。
- 删除 `SUBLEDGER_MISMATCH`；`RECONCILE_SUBLEDGERS` 改为 `RECONCILE_ACCOUNT_BALANCES`。
- Trial Exception 使用 `reference_type + reference_id` 表达 Account、Chart Account、Cash Account 或 Recon Exception 等类型化引用。
- `t_close_account_rule` 继续引用 `t_chart_account`，无需修改。

## Journal Sequence 分配

- 每个 `t_ledger_book` 创建时同步创建且只创建一条 `t_book_sequence`，`next_value` 必须为正整数。
- Journal Posting 在同一本地事务中使用 `SELECT ... FOR UPDATE` 锁定该行，读取并分配原 `next_value`，随后将其加一，再写 Journal Entry/Line、账户余额投影和本地 Compensation Event。
- `t_book_sequence` 不使用 `version`：行锁本身就是取号并发控制，额外乐观锁只会形成两套并发语义。
- 事务任一步失败时取号更新与 Journal 一并回滚；因此“不可复用”针对已经提交的 Journal 序号，回滚事务中尚未提交的候选序号仍可再次分配。
- `uk_journal_seq(ledger_book_id,journal_sequence)` 是最终安全约束；取号前还必须按统一锁顺序校验 Book/Period 状态，避免与关账流程竞争。

## 当前 Account 边界

Finance 不保留 generic Funding 表。Settlement 与 Tax Payment 直接引用各自的 Liability/Cash Account，并在创建、重试、失败、取消与完整对账路径锁定同一 `BANK_CASH` Account 来串行化现金容量；`UNKNOWN` 状态继续占用相应容量。

Client 保证金和追偿也不创建独立工作流：保证金使用 `CLIENT_SECURITY_DEPOSIT/NONE` Account，追偿使用 `CLIENT_RECOVERY_RECEIVABLE/NONE` Account，所有收取、使用、返还、形成、抵扣和回款都由不可变平衡 Journal 表达。

## 明确不做

- 不把 `t_ledger_book` 与 `t_account` 合并。
- 不采用 `account_kind=LEDGER/SUBLEDGER + parent_account_id` 的物理合表方案。
- 不新增独立 GL 当前余额表、Client 汇总余额表或跨 OE 净额表。
- 不创建 Java、Migration、数据库实例或基础设施。
- 不修改共享 PDManer 模板，不提交 Git Commit。
