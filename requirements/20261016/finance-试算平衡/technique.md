# Finance 试算平衡与期间余额 — 技术方案

状态：阶段 2 已确认（2026-10-16）；阶段 3 评审稿见第 4 节起。

**规模总结（本次方案）：** 只改 Finance；新增 1 个写接口、2 个查询接口；新增表 0（4 张已存在）、改表 0、索引 0、DML 0；页面 0。

## 1. 需求项差异

| 需求项 | 当前 | 缺口 | 处理 |
|---|---|---|---|
| T1 执行试算 | 4 张表已在本地 `mor_finance`（完整 84 表 Schema），无 DataObject/Mapper/代码 | DataObject、Mapper、Repository、试算 Domain Service、写 Processor、HTTP 入口全部缺失 | 新建；锁与水位见风险 R1 |
| T1 异常检测 | 账本查询已能从 Journal 重算；`account` 投影已存在 | 缺“投影 vs Journal 重算”与“期末借贷不平”检测 | 在试算事务内用聚合 SQL 一次算出，按差额落 `GENERAL_LEDGER_MISMATCH` |
| T1 content_hash | 人工调整已用 RFC 8785 + SHA-256（`ManualAdjustmentContentHash`） | 缺试算 Hash 输入定义 | 新增 `CODE-MOR:FINANCE:TRIAL_BALANCE:1`，覆盖 Scope、水位、全部行与异常发现事实（不含解决字段） |
| T2 期间余额 | 无代码 | 缺按水位条件的 upsert | `INSERT ... ON DUPLICATE KEY UPDATE ... WHERE` 新水位才覆盖（以条件表达式实现） |
| T3 查询 | 账本查询已有分页范式 | 缺试算列表/详情 | 复用 `LedgerBookQueryApi` 同前缀新增查询方法或新建 `TrialBalanceApi`（阶段 3 定） |
| C 清理 | `JournalPostingServiceImpl` 使用 `core.dal.dataobject.*` 等通配符 import（IDE 带入，已在 `c542498`） | 不符合显式 import 习惯 | 改回逐个导入，逻辑不变 |

## 2. 影响

只改 Finance：`paytech-finance-api`（新接口与 DTO）、`paytech-finance-accounting`（4 表持久化、试算服务、Processor）、`paytech-finance-web`（Controller/Converter）、`paytech-finance-test`（入口测试与并发测试）。无表结构变更、无 DML；其他系统、Gateway、BFF、requirements-site 不变（`Trial Balance & Period Close` 已是目录内部域）。

## 3. 风险

| 编号 | 风险 | 处理方向 |
|---|---|---|
| R1 | 漏读在途分录：过账事务已取得序号但未提交时读取水位，会漏计该分录 | 试算事务先 `SELECT ... FOR SHARE` 账簿序号行（与过账的 `FOR UPDATE` 互斥），取得后再做一致性读；锁顺序与过账一致（期间共享 → 序号行），不会死锁。代价：试算期间该账簿过账等待，试算需保持短事务 |
| R2 | 汇总全量 Journal 的性能 | 当前数据量小；聚合 SQL 按 `account_id`/`ledger_book_id` 走既有索引；后续可用上一期间的期间余额作为期初起点（不在本期） |
| R3 | 并发触发同一账簿期间 | Redis 资源锁 `trial-balance:{book}:{period}`（TRY_LOCK）+ `uk_trial_run` 唯一键兜底，冲突 409 |
| R4 | 期间余额覆盖顺序 | 条件 upsert 只接受更高 `as_of_sequence`；同水位同 Run 幂等 |
| R5 | `content_hash` 输入量大（行多） | 行按科目代码排序；Foundation RFC 8785 单文档上限 1 MiB，当前科目 29 个远低于上限，记为已知限制 |

## 4. 接口设计

新建 `TrialBalanceApi`（`@HttpExchange("/ledger-books/v1")`），Web `TrialBalanceController` 实现。内部开发接口；ID、序号、金额为字符串，金额按账簿币种精度补齐。

| # | 方法与路径 | 说明 | 成功响应 |
|---|---|---|---|
| T1 | `POST /ledger-books/v1/{ledgerBookId}/periods/{periodId}/trial-balances` | 无 Body；同步执行并写终态 | 201 `TrialBalanceRunResponseBody` |
| T3a | `GET /ledger-books/v1/{ledgerBookId}/periods/{periodId}/trial-balances?page=&size=` | 按 `runNo` 降序分页 | `PagingResponseBody<TrialBalanceRunResponseBody>` |
| T3b | `GET /ledger-books/v1/{ledgerBookId}/periods/{periodId}/trial-balances/{runNo}` | 单次详情 | `TrialBalanceDetailResponseBody` |

- `TrialBalanceRunResponseBody`：`trialBalanceRunId, ledgerBookId, periodId, runNo, asOfSequence, status(SUCCEEDED/FAILED), totalDebit, totalCredit, contentHash, lineCount, exceptionCount, createTime`
- `TrialBalanceDetailResponseBody`：上述字段 + `lines[chartAccountId, accountCode, accountName, accountClass, normalSide, openingBalance, periodDebit, periodCredit, closingBalance]`（按科目代码排序）+ `exceptions[exceptionType, referenceType, referenceId, differenceAmount, status]`
- 错误：账簿/期间不存在、期间不属于账簿主体、期间为 FUTURE、`runNo` 不存在 → 400；同一账簿期间并发试算 → 409。

## 5. 执行设计

```text
RunTrialBalanceProcessor（AbstractLockProcessor，TRY_LOCK `trial-balance:{ledgerBookId}:{periodId}`）
  事务前：账簿存在；期间存在、非 FUTURE、属于账簿主体日历；读取币种精度
  TrialBalanceService.run（一个 TransactionTemplate）
    1. book_sequence FOR SHARE → as_of_sequence = next_value - 1
    2. 聚合 SQL：按科目汇总 seq ≤ 水位 且 posting_date ≤ 期间结束 的分录
         opening = Σ(过账日 < 期间开始，同正常方向 +、反向 −)
         period_debit / period_credit = Σ(过账日在期间内，按借/贷)
         closing = opening + (正常方向 DEBIT ? 借 − 贷 : 贷 − 借)
    3. 投影核对 SQL：账簿内每个账户的 debit_total/credit_total/net_balance 与 seq ≤ 水位的全量分录重算比较
    4. run_no = 同账簿期间最大 run_no + 1（唯一键兜底）
    5. 期末有符号余额拆分借贷方向得 total_debit/total_credit
    6. 异常：投影不一致 → GENERAL_LEDGER_MISMATCH / ACCOUNT / 账户 ID / 差额（借、贷、净三项绝对差的最大值）
            借贷合计不等 → GENERAL_LEDGER_MISMATCH / CHART_ACCOUNT / 账簿采用的科目表 ID / |借 − 贷|
    7. 状态：无异常且借贷相等 → SUCCEEDED，否则 FAILED；计算 content_hash；插入 Run、Line、Exception（OPEN）
    8. 仅 SUCCEEDED：条件 upsert ledger_period_balance（新水位 ≥ 旧水位才覆盖，as_of_sequence 最后更新）
```

- **锁顺序**：Redis 试算锁 → 账簿序号行共享锁；与过账（期间共享 → 序号行排他）不形成环；试算不锁期间，期间状态检查在事务前完成（关账流程另行设计锁）。
- **content_hash**：`CODE-MOR:FINANCE:TRIAL_BALANCE:1`，RFC 8785 + SHA-256；输入为 `ledgerBookId, periodId, runNo, asOfSequence, status, totalDebit, totalCredit`，按 `chartAccountId` 升序的全部行（四个金额），按（类型、引用类型、引用 ID）排序的异常发现事实（不含状态与解决字段）；ID 与金额均为字符串，金额按币种精度。
- **借贷合计不等的引用**：表结构只允许 ACCOUNT/CHART_ACCOUNT/CASH_ACCOUNT/RECON_EXCEPTION，而合计不等不对应单个科目，本期以 CHART_ACCOUNT + 科目表 ID 记录。过账内核保证每张分录平衡，该情况只会在数据库被直接篡改时出现。

## 6. 代码落点

| 模块 | 新增 / 修改 |
|---|---|
| api | `TrialBalanceApi`；`TrialBalanceRunResponseBody`、`TrialBalanceDetailResponseBody` |
| accounting | 4 个 DataObject、Mapper（XML）、Repository；枚举 `TrialBalanceStatus`、`TrialExceptionType`、`TrialReferenceType`、`TrialExceptionStatus`；`TrialBalanceService` + Impl；`RunTrialBalanceProcessor`；`TrialBalanceContentHash`；View；`AccountingLocks.trialBalance`；`JournalPostingServiceImpl` import 清理 |
| web | `TrialBalanceController`、`TrialBalanceConverter`；`FinancePersistenceConfiguration` 注册新枚举 |
| test | `RunTrialBalanceTest`、`TrialBalanceConcurrencyTest`、`TrialBalanceQueryTest`、`TrialBalanceContentHashTest`；`AccountingTestMapper` 增加试算表清理与读取 |

## 7. 测试与验收映射

| AC | 用例 |
|---|---|
| AC-1/2 | 历史期间 + 当前期间各若干调整；行的期初/本期/期末与手算一致；含反向余额科目时借贷合计按符号拆分且相等 |
| AC-3 | 未来期间分录、水位外分录不计入；无发生额科目不落行 |
| AC-4 | TestMapper 篡改账户 `net_balance` → FAILED、异常差额正确、`ledger_period_balance` 无写入 |
| AC-5 | 两次成功试算：第二次水位更高覆盖；手工写入更高水位后再试算不被旧水位覆盖 |
| AC-6 | 连续 `runNo`；预占 Redis 锁 → 409；Hash 固定向量（独立计算）与 64 位格式 |
| AC-7 | 双连接：一个事务持有序号行排他锁模拟在途过账，试算请求等待；在途提交后试算水位包含该分录 |
| AC-8 | 账簿/期间不存在、期间属于其他主体、FUTURE → 400 |
| AC-9 | 列表降序分页、详情行与异常；`clean verify` |

## 8. 阶段 4 多 agent 分工

| Agent | 范围 | 依赖 |
|---|---|---|
| A 生产代码 | accounting/api/web 全部生产代码与 C 清理，按本节契约命名 | 无 |
| B 测试 | `paytech-finance-test` 中第 6 节列出的测试与 TestMapper 扩展，按第 4 节 JSON 字段与路径编写 | 与 A 并行，只依赖本契约 |
| 主会话 | 合并后编译、修复契约偏差、`clean verify`、规范自查 | A、B 完成后 |

## 9. 研发实现说明（阶段 4）

- 实现与契约一致，无偏差。实现细节：试算执行结果直接复用 `TrialBalanceRunView` 作为 Processor 返回，未另建同义 Response；`TrialBalanceService` 向 Processor 提供 `getBook/getPeriod` 校验读取，执行与查询共用。
- 期间余额条件 upsert 使用 MySQL 8.0.19+ 的 `INSERT ... AS n` 行别名（本地 MySQL 8.4），`as_of_sequence` 以 `GREATEST` 最后更新。
- 多 agent 执行：生产代码与测试由两个并行 agent 按第 4–7 节契约完成，主会话合并后一次通过。
