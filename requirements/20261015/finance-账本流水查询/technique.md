# Finance 账本流水与账户查询 — 技术方案

状态：阶段 2 已确认（2026-10-15）；阶段 3 评审稿见第 4 节起。

**规模总结（本次方案）：** 只改 Finance；新增 4 个只读接口；新增表 0、改表 0、索引 0、DML 0；页面 0。

## 1. 需求项差异

| 需求项 | 当前行为及证据 | 目标 | 缺口 | 处理 |
|---|---|---|---|---|
| Q1 账簿列表 | `LedgerBookMapper` 只有按主键、按主体唯一读取 | 按过滤条件分页 | 缺分页/计数 SQL | 扩展 Mapper XML 与 Repository |
| Q2 账户列表 | `AccountMapper` 只有按主键、批量和 Owner 余额读取 | 按科目/核算对象/余额桶分页，返回科目代码 | 缺分页 SQL；科目代码在 `t_chart_account` | 新增账户与科目组合查询（`*View`） |
| Q3 账户流水 | 无 | 按序号分页，带累计余额，可按日期过滤 | 缺查询；累计余额需对全量历史计算 | 一条 SQL：子查询内对账户全部分录行按 `journal_sequence` 用窗口函数累计，再外层按日期过滤与 `LIMIT/OFFSET`；来源字段关联指令与人工调整 |
| Q4 分录详情 | 测试侧可读，生产无接口 | 分录头 + 全部行（含科目、核算对象）+ 来源 | 缺查询 | 新增分录详情组合查询 |
| AC-6 错误 | — | 不存在/不归属拒绝 | — | 沿用 `BizCheckFailException`（400）；Query 参数格式错误为 400，分页值非法为 `INVALID_REQUEST` |

## 2. 影响

| 系统 | 影响 | 是否改动 |
|---|---|---|
| Finance `paytech-finance-api` | `LedgerBookCommandApi` 同路径下新增查询方法，或新增 `LedgerQueryApi`（阶段 3 定） | 是 |
| Finance `paytech-finance-accounting` | 新增查询 Service、View、Mapper SQL；表归属不变（全部是 Accounting 自有表） | 是 |
| Finance `paytech-finance-web` / `-test` | Controller、Converter；新增入口测试 | 是 |
| Finance 数据库 | 无结构变化；现有索引 `idx_journal_line_account`、`uk_journal_seq`、`idx_account_owner`、`idx_ledger_book_*` 可支撑 | 否 |
| 其他系统、Gateway、BFF、requirements-site | 无（FIN-I05 已在目录中，本期只是内部实现） | 否 |

## 3. 风险

| 编号 | 风险 | 处理 |
|---|---|---|
| R1 | 累计余额对账户全量历史计算，高频账户（如 Channel 应收）历史很长时变慢 | 本期可接受（当前只有人工调整）；后续可按 `ledger_period_balance` 期初余额截断，留作已知限制 |
| R2 | 分页的 COUNT 与数据查询是两条语句，可能观察到相邻提交 | 展示类查询按规范不开事务；Journal 只追加，最多造成 `total` 与页内容相差新提交的行，不影响已有行的累计余额 |
| R3 | 分录行与账户的科目、核算对象需要跨表组合 | 全部是 Accounting 自有表，同模块 JOIN 合规 |

## 4. 接口设计

新建 `LedgerBookQueryApi`（`@HttpExchange("/ledger-books/v1")`，与已有 `LedgerBookCommandApi` 同前缀、按命令/查询拆分），由 Web `LedgerBookQueryController` 实现。全部为 Finance 内部开发接口；ID 与金额均为字符串，金额按账簿币种精度补齐。

| # | 方法与路径 | 参数 | 成功响应 |
|---|---|---|---|
| Q1 | `GET /ledger-books/v1` | `entityType?`（OPERATING_ENTITY/FINANCE_ENTITY）、`entityId?`、`currency?`、`page?`、`size?` | `PagingResponseBody<LedgerBookResponseBody>`：`ledgerBookId, entityType, entityId, currency, chartCode, bookStatus`；按主键升序 |
| Q2 | `GET /ledger-books/v1/{ledgerBookId}/accounts` | `accountCode?`、`ownerType?`、`ownerId?`、`balanceBucket?`、`page?`、`size?` | `PagingResponseBody<AccountResponseBody>`：`accountId, accountCode, ownerType, ownerId, balanceBucket, normalSide, accountStatus, debitTotal, creditTotal, netBalance, lastJournalSequence`；按科目代码、核算对象类型/标识、余额桶、主键排序 |
| Q3 | `GET /ledger-books/v1/{ledgerBookId}/accounts/{accountId}/journal-lines` | `postingDateFrom?`、`postingDateTo?`（ISO 日期，闭区间）、`page?`、`size?` | `PagingResponseBody<AccountJournalLineResponseBody>`：`journalEntryId, journalSequence, postingDate, entryType, lineNo, entrySide, amount, balanceAfter, instructionType, originType, sourceSystem, sourceEventKey, adjustmentNo`；按分录序号、行号升序 |
| Q4 | `GET /ledger-books/v1/{ledgerBookId}/journal-entries/{journalEntryId}` | — | `JournalEntryDetailResponseBody`：分录头（序号、期间、过账日期、类型、冲回原分录）+ 来源（指令 ID/类型/来源、人工调整 ID/单号）+ `lines[lineNo, accountId, accountCode, ownerType, ownerId, balanceBucket, entrySide, amount]` |

**错误：** 账簿不存在、账户/分录不属于该账簿 → 400 `BUSINESS_CHECK_FAILED`；路径/Query 格式错误 → 400；`page/size` 非法（size 只允许 10/20/50/100/200）→ 400 `INVALID_REQUEST`（公共 `PagingRequestBody.of`）；`postingDateFrom` 晚于 `postingDateTo` → 400 `INVALID_REQUEST`。

## 5. 分层与查询

- **简单查询不建 Processor：** Controller 解析参数后直接委托 Accounting 的 `LedgerQueryService`（interface + Impl），由它校验账簿/账户归属、读取币种精度并返回 `*View`；Converter 转为 ResponseBody。
- **查询条件对象：** 参数超过三个的查询使用 `LedgerBookQuery`、`AccountQuery` 两个领域查询对象；流水查询参数为账簿、账户、日期区间与分页，直接传参。
- **View：** `AccountView`（账户 + 科目代码）、`AccountJournalLineView`、`JournalEntryDetailView`（含行）；均为多表组合，放 `domain/view`，不跨模块。
- **Repository/Mapper：** 只扩展已有 `LedgerBookRepository`、`AccountRepository`、`JournalLineRepository`、`JournalEntryRepository` 与对应 XML；SQL 只读本模块自有表。

**Q3 累计余额 SQL（一条语句）：**

```sql
SELECT * FROM (
  SELECT e.id journal_entry_id, e.journal_sequence, e.posting_date, e.entry_type, l.line_no, l.entry_side, l.amount,
         SUM(CASE WHEN l.entry_side = a.normal_side THEN l.amount ELSE -l.amount END)
           OVER (ORDER BY e.journal_sequence, l.line_no) balance_after,
         i.instruction_type, i.origin_type, i.source_system, i.source_event_key, m.adjustment_no
  FROM t_journal_line l
  JOIN t_journal_entry e ON e.id = l.journal_entry_id
  JOIN t_account a ON a.id = l.account_id
  JOIN t_accounting_instruction i ON i.id = e.instruction_id
  LEFT JOIN t_manual_adjustment m ON m.id = i.manual_adjustment_id
  WHERE l.account_id = #{accountId}
) t
WHERE (#{from} IS NULL OR posting_date >= #{from}) AND (#{to} IS NULL OR posting_date <= #{to})
ORDER BY journal_sequence, line_no
LIMIT #{size} OFFSET #{offset}
```

窗口在过滤之前对全量历史计算，因此日期过滤与翻页都不改变累计起点（AC-3、AC-4）；计数使用同一子查询的过滤条件。

## 6. 代码落点

| 模块 | 新增 / 修改 |
|---|---|
| `paytech-finance-api` | `LedgerBookQueryApi`；`response/` 下 4 个 ResponseBody（`JournalEntryDetailResponseBody` 含嵌套 `Line`） |
| `paytech-finance-accounting` | `LedgerQueryService` + Impl；`domain/LedgerBookQuery`、`domain/AccountQuery`；3 个 View；4 个 Repository/Mapper 扩展 |
| `paytech-finance-web` | `LedgerBookQueryController`、`LedgerQueryConverter` |
| `paytech-finance-test` | 4 个入口测试类；必要的 Checker 读取方法 |
| 文档 | README、`CURRENT_IMPLEMENTATION.md` Finance 段落（阶段 5） |

## 7. 测试与验收映射

测试复用 `MockTestBase` 与人工调整 HTTP 流程形成真实 Journal（不直接插分录），期间准备一个 2020-01 的 OPEN 历史期间（过账日期收敛到 2020-01-31）和当前期间，以得到不同过账日期。

| AC | 用例 |
|---|---|
| AC-1 | 三本账簿按主体/币种过滤、`size=10` 翻页 `total` 准确；非法 `size=7` → 400 |
| AC-2 | 账户按科目、核算对象、余额桶过滤；余额等于投影；其他账簿账户不出现 |
| AC-3 | 12 笔调整后按 `size=10` 两页读取，`balanceAfter` 连续，末行等于 `netBalance`；贷方正常账户借记后为负 |
| AC-4 | 只取 2020-01-31 的行时 `balanceAfter` 与全量结果中对应行一致；`from > to` → 400 |
| AC-5 | 分录详情借贷相等，`adjustmentNo` 等于提交返回的单号，行内科目与核算对象正确 |
| AC-6 | 账簿不存在、账户属于其他账簿、分录属于其他账簿 → 400 |
| AC-7 | `clean verify` 全部通过 |

## 8. 研发实现偏差（阶段 4）

| 项 | 设计稿 | 实际 | 原因 |
|---|---|---|---|
| Q3 日期参数类型 | `LocalDate` Query 参数 | 字符串参数 + `@Pattern("[0-9]{4}-[0-9]{2}-[0-9]{2}")`，由 Web Converter 用 `LocalDate.parse` 精确解析；格式错误 400，格式对但日期不存在（如 02-30）400 `INVALID_REQUEST` | `@DateTimeFormat` 需要 spring-context，轻量 `*-api` 模块不为此新增依赖；仓库内也没有既有日期 Query 先例 |
| 分页参数传递 | 查询对象内含 `PagingRequestBody` | Repository 从查询对象取出 `size/offset` 单独传给 Mapper | `PagingRequestBody` 是 record，`offset()` 不是 JavaBean 属性，MyBatis OGNL 无法读取 |
| 测试辅助 | — | “提交并批准”上移到 `AbstractManualAdjustmentApiTest.posted`，余额测试改用它；`FinanceResponseChecker` 新增 `assertPage` | 查询测试与余额测试共用 |
