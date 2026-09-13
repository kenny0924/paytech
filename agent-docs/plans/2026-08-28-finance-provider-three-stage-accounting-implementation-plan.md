# Finance Provider 三阶段账户实施计划

> 2026-09-13 命名后继：[支付渠道 Channel 决定](../decisions/2026-09-13-payment-channel-terminology.md)取代本文支付领域 Provider 命名；业务边界与历史证据保持。现行实体、字段及自有回调代码使用 Channel；外部固定协议与原报文不改写。

> 状态：实施完成并通过当前隔离工作区自动验证；PDManer 4.9.3 应用内人工检查与主工作区六系统总门禁待集成线程完成。  
> 日期：2026-08-28  
> 范围：Finance PDManer-only 模型、权威文档、现实场景、requirements-site 与共享入口；不创建 Java、Migration、物理数据库或部署资产，不提交 Git。

## 1. 目标

把 Provider 资金生命周期从含义模糊的“应收 / 在途”命名，收敛为三个普通会计账户阶段，并让交易对账与银行对账各自只推进自己负责的阶段：

1. 权威交易成功后，形成 Provider 待清算应收；
2. Provider 批次的逐笔交易对账完整后，按批次净额转成 Provider 结算应收；
3. Bank Statement 确认入账后，结算应收转入平台银行现金。

退款、争议等 Provider 应付方向使用对称的“待清算应付 → 结算应付 → 银行现金”路径。实现不新增表，继续复用统一 `t_account`、不可变 Journal、Provider Transaction Match 和 Bank Recon Match。

## 2. 已确认的账户代码

| 旧代码 | 新代码 | 会计含义 |
| --- | --- | --- |
| `PROVIDER_RECEIVABLE` | `PROVIDER_UNSETTLED_RECEIVABLE` | 交易已经形成，但尚未完成 Provider 交易对账的待清算应收 |
| `CASH_IN_TRANSIT` | `PROVIDER_SETTLEMENT_RECEIVABLE` | Provider 批次已经完整交易对账，等待 Bank Statement 确认的结算应收 |
| `PROVIDER_SETTLEMENT_PAYABLE` | `PROVIDER_UNSETTLED_PAYABLE` | 退款、争议等已经形成，但尚未完成 Provider 交易对账的待清算应付 |
| `CASH_OUT_TRANSIT` | `PROVIDER_SETTLEMENT_PAYABLE` | Provider 批次已经完整交易对账，等待银行扣款确认的结算应付 |
| `BANK_CASH` | `BANK_CASH` | 平台在 `t_cash_account` 对应银行账户中的账面现金 |

所有 Provider 阶段账户继续使用：

- `owner_type=PROVIDER_ACCOUNT`；
- `owner_id=t_provider_account.id`；
- `balance_bucket=NONE`；
- Ledger Book 与 Currency 必须与来源交易和批次一致。

`BANK_CASH` 继续使用 `owner_type=CASH_ACCOUNT`、`owner_id=t_cash_account.id`、`balance_bucket=NONE`，应用通过唯一 Account Identity 解析；`t_cash_account` 不保存也不需要虚构 `account_id` 外键。

## 3. 正向资金路径

假设 Provider 应收毛额为 105，预计 Provider Cost 为 2，净收款为 103：

1. 交易成功时，权威业务事实产生毛额 `PROVIDER_UNSETTLED_RECEIVABLE` 105。预计 Provider Cost 单独计提：  
   `Dr PROVIDER_FEE_EXPENSE 2 / Cr PROVIDER_FEE_ACCRUAL 2`，不能直接冲减待清算应收。
2. Provider Batch 必须是 Provider 权威最终 Settlement/Remittance 明细，而非 Preliminary Transaction Report；其全部业务行已经唯一 Transaction Match，费用和调整已经进入 Cost Confirm / Allocation 或 Recon Exception，实际成本确认完整，且没有阻塞异常。满足门禁后，创建一次聚合 Journal：  
   `Dr PROVIDER_SETTLEMENT_RECEIVABLE 103 / Dr PROVIDER_FEE_ACCRUAL 2 / Cr PROVIDER_UNSETTLED_RECEIVABLE 105`。
3. Bank Statement Credit 与该 Provider Batch 完成 Bank Recon Match 后：  
   `Dr BANK_CASH 103 / Cr PROVIDER_SETTLEMENT_RECEIVABLE 103`。

`VALIDATED` 只证明批次文件结构、币种及金额公式有效，单独不能触发第二阶段 Journal。Provider Cost 已在交易阶段计提时，批次 Journal 只清偿 `PROVIDER_FEE_ACCRUAL`，不得再次确认全额费用；实际与预计差异只调整 `PROVIDER_FEE_EXPENSE ↔ PROVIDER_FEE_ACCRUAL`，且只能入账一次，四个 Provider 阶段账户不承载费用差额。

## 4. 负向资金路径

退款成功、允许入账的争议损失等权威事实先形成 `PROVIDER_UNSETTLED_PAYABLE`。批次完整交易对账后，聚合 Journal 把净负向金额转入 `PROVIDER_SETTLEMENT_PAYABLE`；Bank Statement Debit 匹配后再清偿该应付并减少 `BANK_CASH`。

示例：同一批次含待清算应付 70、待清算应收 30，净扣款 40：

1. 批次完整交易对账（本例无额外 Fee；存在已计提 Fee 时同时借记 `PROVIDER_FEE_ACCRUAL`）：  
   `Dr PROVIDER_UNSETTLED_PAYABLE 70 / Cr PROVIDER_UNSETTLED_RECEIVABLE 30 / Cr PROVIDER_SETTLEMENT_PAYABLE 40`；
2. Bank Statement Debit：  
   `Dr PROVIDER_SETTLEMENT_PAYABLE 40 / Cr BANK_CASH 40`。

## 5. 对账与状态边界

- Provider Settle Batch/Item 必须来自权威最终 Settlement/Remittance 明细；Preliminary Transaction Report 只能辅助调查，不能冒充批次重分类依据。Provider Open Item / Debit Item 是 Finance 本地逐笔会计导航事实。
- `t_provider_transaction_match` 只完成交易层逐笔匹配，不直接代表银行到账。
- 多条 Transaction Match 可以共同支持一个 Provider Batch 级 Journal；不得为每条银行汇总款伪造逐笔银行流水。
- Open / Debit Item 的结算金额与状态表达交易对账推进；银行是否到账只由 `t_bank_recon_match` 和 Bank Statement Item 表达。
- `t_bank_recon_match.provider_batch_id` 直接把批次净额与银行流水连接，银行层不恢复 `Provider Cash Item`。
- 批次重放必须校验同一 Provider Batch 的冻结内容和既有 Journal，不覆盖批次或重复过账。
- 所有不确定、金额不守恒、匹配歧义或门禁不完整情况 Fail-Closed，保留原阶段余额并产生 Recon Exception。

## 6. 模型与文档修改

### Task 1：先建立失败测试

- [x] Finance 模型测试要求新四个账户代码及新 Trial Exception 术语，并禁止活动语义中的旧四个代码。
- [x] Finance 场景测试要求完整正向、负向三阶段 Journal 和批次完整性门禁。
- [x] requirements 测试要求 FIN-11、FIN-12 与 E2E-08 明确区分交易对账和银行确认。
- [x] 运行定向测试，记录它们因现有旧语义而失败。

### Task 2：更新可执行 Finance 模型契约

- [x] 更新 Account / Provider Recon 字段注释，冻结三阶段账户 owner 和触发边界。
- [x] 将 Trial Balance Exception 的 `CASH_IN_TRANSIT_MISMATCH` 收敛为 `PROVIDER_SETTLEMENT_MISMATCH`。
- [x] 不增加表、字段、索引或关系；除非验证发现真实结构缺口，否则保持 Finance 统计不变。
- [x] 重新生成 `finance.pdma.json` 并更新双哈希。

### Task 3：更新 Finance 权威内容

- [x] 更新 `FINANCE_DATABASE.md` 的账户、Journal、不变量与 Provider Recon 关系图。
- [x] 更新现实场景的科目代码、Journal 样例、试算异常和对账流程。
- [x] 更新 `FINANCE_PHYSICAL_DECISIONS.md` 的三阶段物理约束与模型哈希。
- [x] 对仍被引用的历史计划增加明确 supersession 说明，不伪造历史执行记录。

### Task 4：更新 requirements 与共享入口

- [x] 更新 FIN-08、FIN-10、FIN-11、FIN-12、E2E-03、E2E-05、E2E-08 及对应自动测试。
- [x] 在六份共享入口增加本计划链接和三阶段摘要。
- [x] 保留其他系统的当前统计、链接与并行改动。

### Task 5：验证与交接

- [x] Finance 生成器 `--check`。
- [x] Finance 模型、场景与 requirements 定向测试。
- [x] 当前隔离 worktree 可用的 CCT、Commerce、Payment 与 Finance PDManer 全量测试；Risk/Platform 资产不在该 worktree，由主工作区集成线程复跑六系统总门禁。
- [x] requirements build、全量测试与 ESLint。
- [x] 旧活动术语、冲突标记、Finance 链接与 `git diff --check` 扫描。
- [x] 独立只读审查账户分录、模型关系和共享语义。
- [x] 回传最终 changed files、统计、哈希、门禁结果及 Risk 解冻短语。

## 7. 完成标准

完成后，一个不懂会计的业务人员也应能从模型和场景中得到同一答案：交易成功只是“Provider 还欠平台多少钱”；交易对账完整后变成“Provider 已确认本批次应该结多少钱”；只有银行流水确认后，才成为平台银行账户里的现金。负向扣款严格对称，任何阶段都由不可变 Journal 推进，`t_account` 只是这些 Journal 可重建的当前余额投影。
