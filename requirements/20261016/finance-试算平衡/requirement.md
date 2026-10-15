# Finance 试算平衡与期间余额（+ import 清理）

状态：阶段 1 已确认（2026-10-16）：第 5 节三个问题均按建议执行。

## 1. 背景与目标

[Finance 数据库设计](../../../agent-docs/database/FINANCE_DATABASE.md) §4.7 规定：试算平衡按账簿、会计期间与 Journal Sequence 水位执行，冻结可过账叶子科目的期初、本期借贷与期末余额，以及完整性异常；它是期间关账（固定步骤 4、7）的前置，也是 `ledger_period_balance` 投影的来源。本需求只做试算与期间余额，不做关账流程。

使用者：Finance 内部与运营核对人员（内部开发接口，不接 Gateway/授权）。

## 2. 本期范围

| # | 内容 | 表 |
|---|---|---|
| T1 | 执行试算：对一个账簿 + 期间，在一致水位下汇总每个可过账叶子科目的期初、本期借贷、期末（按科目正常方向的有符号余额），计算期末借贷合计并校验相等；检测总账完整性异常；冻结 `content_hash` | `trial_balance_run`、`trial_balance_line`、`trial_balance_exception` |
| T2 | 成功试算刷新期间余额投影：按（期间, 账簿, 科目）写入；只允许更新水位覆盖旧水位 | `ledger_period_balance` |
| T3 | 查询：某账簿某期间的试算列表；单次试算详情（行与异常） | — |
| C | `JournalPostingServiceImpl` 的通配符 import 改回逐个导入 | — |

建议口径（待确认，见第 5 节）：

- **触发方式：** 内部 HTTP 手工触发，同步执行；不建调度任务。
- **一致水位：** 试算事务先以共享锁读取账簿序号行确定 `as_of_sequence`，等在途过账提交后再读 Journal，避免漏读已取号未提交的分录；试算期间该账簿的普通过账会短暂等待。
- **期初/本期/期末：** 期初 = 过账日期早于期间开始的全部分录；本期 = 过账日期落在期间内的分录；期末 = 期初 + 本期；都只计入 `journal_sequence ≤ as_of_sequence`。期初为零且本期无发生额的科目不落行。
- **异常范围：** 本期只检测 `GENERAL_LEDGER_MISMATCH`——账户余额投影与 Journal 重算不一致（引用 ACCOUNT），以及期末借贷合计不相等（引用 CHART_ACCOUNT）。`COUNTERPARTY_MISMATCH`、`CHANNEL_SETTLEMENT_MISMATCH`、`UNRESOLVED_RECONCILIATION` 依赖交易经济与 Treasury，暂不检测。有任何异常的试算为 FAILED。
- **异常处置：** 本期只记录 OPEN 异常；Maker-Checker 解决（含投影重建）随期间关账需求实现。
- **状态：** 同步执行，在一个事务内直接写入终态 SUCCEEDED/FAILED（不落中间 CREATED/RUNNING），终态冻结 `content_hash`。

## 3. 明确不做

期间关账/重开与关账步骤、异常解决与投影重建、损益结转、交易经济与 Treasury 相关核对、调度任务、Gateway/授权、Operations 页面；不改造账户流水查询（期间余额截断留作后续优化）。

## 4. 验收标准

| 编号 | 标准 |
|---|---|
| AC-1 | 试算行按科目汇总该账簿全部账户：期初、本期借贷、期末正确，期末 = 期初 + 本期净额（按正常方向），符号不取绝对值 |
| AC-2 | `total_debit/total_credit` 按期末有符号余额拆分实际借贷方向计算，且成功时相等；不使用本期发生额 |
| AC-3 | 只计入水位以内的分录；跨期间分录分别进入期初或不计入；期初为零且无发生额的科目不落行 |
| AC-4 | 账户投影被篡改时试算 FAILED 并记录 `GENERAL_LEDGER_MISMATCH`（ACCOUNT，差额正确）；失败试算不刷新期间余额 |
| AC-5 | 成功试算写入/刷新 `ledger_period_balance`；同期间再次试算以新水位覆盖，旧水位不覆盖新水位 |
| AC-6 | `run_no` 在账簿 + 期间内从 1 连续递增；并发触发同一账簿期间时只有一个成功、另一个 409；终态 `content_hash` 为 64 位且同内容可重放 |
| AC-7 | 试算进行时并发过账不丢失、不被漏计（双连接测试：试算结果水位与分录一致） |
| AC-8 | 账簿/期间不存在、期间不属于账簿主体、期间为 FUTURE 时拒绝 |
| AC-9 | 查询返回试算列表与详情；`clean verify` 通过；通配符 import 已清理 |

## 5. 待确认问题

1. 期间状态限制：建议 OPEN、CLOSING、CLOSED 均可试算（关账后也能复核），只拒绝 FUTURE。
2. 异常范围与处置按第 2 节建议：本期只检测总账类异常、只记录不处置。
3. 同步执行直接写终态，不落 CREATED/RUNNING。

## 6. 适用阶段

阶段 1—5；表已在本地 `mor_finance`（84 表），预计无 SQL 变更；提交另行确认。
