# Finance 期间重开与年末结转 — 技术方案

状态：阶段 2 已确认（2026-10-17）；阶段 3 方案、接口与数据设计待确认。

## 1. 需求项差异

| # | 需求 | 现状 | 差异 |
|---|---|---|---|
| P1 | 发起重开 | `t_period_close` 已有 `close_type=REOPEN`、`reopens_close_id`、`reopen_reason_code`、`reopen_request_ref`；`PeriodCloseType.REOPEN`、`PeriodStatus.REOPENING`、`JournalEntryType.REOPENING` 枚举已存在；过账内核已允许 REOPENING 期间过账 REOPENING 分录 | 新增发起重开 Processor：校验最近关闭期间、规划冲回行、冻结摘要；`StartPeriodClose` 只处理 CLOSE |
| P1 | 重开步骤 | `t_close_step.step_type` 允许值只列出 7 个关账步骤（VARCHAR，注释约束） | 新增重开步骤类型（锁定水位、规划冲回、过账冲回），同步模型注释与枚举 |
| P2 | 审批 / 驳回 | `ApprovePeriodCloseProcessor` / `RejectPeriodCloseProcessor` 按 CLOSE 语义：批准复验最终试算并置 CLOSED，驳回回到 OPEN | 按 `close_type` 分派：REOPEN 批准时过账冲回并置 OPEN，驳回回到 CLOSED |
| P2 | 重开计数 | `t_accounting_period.reopen_count` 已存在，默认 0 | 重开批准完成时同一事务 `reopen_count + 1` |
| P2 | 账簿证据 | `t_period_close_book.final_trial_run_id` 可空、`posting_instruction_id` 可空、`status` LOCKED/FINALIZED | REOPEN 的 FINALIZED 定义为“冲回指令已过账”（或该账簿原关账无结转指令），不要求最终试算 |
| P3 | 年末结转 | 结转规则已带 `retained_earnings_account_id`（3021）；`postProfitClose` 只做收入/费用 → 本期损益 | 在财年最后期间的结转指令后追加“本期损益 → 留存收益”行；留存收益账户不存在时自动创建（同本期损益） |
| P4 | 查询 | 关账列表与详情已返回全部流程 | 响应补充 `closeType` 已有；新增返回 `reopensCloseNo`、`reopenReasonCode`、`reopenRequestRef` |

## 2. 影响

- **模块**：只改 `finance-service`；api 新增重开请求体与响应字段；accounting 新增重开 Processor、扩展审批/驳回与结转；web 新增重开路由。
- **既有行为**：CLOSE 流程不变；非年末期间的结转结果不变。年末期间结转指令多出两行，既有测试中若恰好关闭“财年最大期间号”的期间会受影响（阶段 4 核对）。
- **数据库**：见风险 R1，可能需要一条 DDL；`t_close_step.step_type` 注释与 PDManer 允许值更新（不改结构）。
- **其他系统**：无。

## 3. 风险与待定事项（阶段 3 给出建议）

| # | 事项 | 说明 | 初步建议 |
|---|---|---|---|
| R1 | `uk_period_reopen_target(reopens_close_id)` 唯一键 | 一个原关账只能有一条 REOPEN 行；驳回后重试会违反唯一键，与 AC-3 冲突 | **DDL：改为普通索引** `idx_period_reopen_target`；“同一原关账最多一个活动重开”由期间活动槽保证，“最多一次成功重开”由原关账之后不再是最近关闭流程保证。需本地 SQL 授权 |
| R2 | 冲回分录的依据 | 原关账结转指令行冻结在 `t_instruction_line`（`posting_instruction_id`） | 按原指令行逐行反向（借贷互换、金额不变）生成 REOPENING 指令，不重新计算余额 |
| R3 | 审批时过账的锁顺序 | 审批事务需同时更新期间、流程与多账簿过账 | 沿用关账锁：TRY_LOCK `period-close:{periodId}` → 期间行 → 按账簿 ID 升序调用过账内核 |
| R4 | 年末判定 | 日历只有 `year_start_month`，没有“每年期间数” | **期间 `end_date` 的次日是财年起始月 1 日**（按日历 `year_start_month`）即为财年最后期间；不依赖后续期间是否已创建 |
| R5 | 摘要 schema | 关账摘要为 `CODE-MOR:FINANCE:PERIOD_CLOSE:1` | 重开使用独立 schema `CODE-MOR:FINANCE:PERIOD_REOPEN:1`，覆盖原因、原关账 ID 与摘要、账簿范围与冲回行 |

R1—R5 在阶段 3 按上表建议采用，细化见下文。

## 4. 接口设计（`PeriodCloseApi` 扩展，前缀 `/accounting-periods/v1`）

| # | 方法与路径 | 请求体 | 结果 |
|---|---|---|---|
| E1 | `POST /{periodId}/reopens` | `makerId, reasonCode, requestRef` | 201 `PeriodCloseResponseBody`（`closeType=REOPEN`，PENDING_CHECK，`closeHash` 已冻结） |
| E2 | `POST /{periodId}/closes/{closeNo}/approve`（既有） | `checkerId, approvedCloseHash` | 按流程 `close_type` 分派；REOPEN → COMPLETED，期间 OPEN |
| E3 | `POST /{periodId}/closes/{closeNo}/reject`（既有） | 同上 | REOPEN → REJECTED，期间 CLOSED |
| E4 | `GET /{periodId}/closes[/{closeNo}]`（既有） | — | 响应新增 `reopensCloseNo`、`reopenReasonCode`、`reopenRequestRef`（CLOSE 流程为空） |

- `reasonCode` 目录（Java 枚举 `PeriodReopenReason`）：`ACCOUNTING_CORRECTION`（会计更正）、`LATE_TRANSACTION`（晚到交易补记）、`AUDIT_ADJUSTMENT`（审计调整）。`requestRef` 非空，≤128。原因代码不在目录内属于参数错误，按开发规范使用 `InvalidParameterException` → 400 `INVALID_REQUEST`（阶段 4 合并时确认）。
- 关账流程编号 `close_no` 在同一期间内 CLOSE/REOPEN 共用一个递增序列。

## 5. 发起重开（E1，`StartPeriodReopenProcessor`）

TRY_LOCK `period-close:{periodId}`（与关账同一把锁）；一个事务内完成：
1. 期间行 `FOR UPDATE`，须为 CLOSED；同一日历中 `start_date` 晚于它的期间不得为 CLOSED/CLOSING/REOPENING（D1）；无活动流程。
2. 原关账：该期间 `close_no` 最大的 `close_type=CLOSE` 且 COMPLETED 的流程，写入 `reopens_close_id`。
3. 创建 REOPEN 流程（CREATED → RUNNING）、Maker 审批记录、3 个步骤：
   - `LOCK_SEQUENCE`：对原关账的每个账簿锁定当前最大 Journal Sequence，写 `period_close_book`（LOCKED）。SUCCEEDED。
   - `PLAN_REOPEN_REVERSAL`：读取原关账每个账簿的结转指令行（`posting_instruction_id` 为空的账簿无冲回行），逐行借贷互换、金额不变，作为该账簿冲回计划。SUCCEEDED，`result_hash` 覆盖全部冲回行。
   - `POST_REOPEN_REVERSAL`：PENDING（审批时执行）。
4. 冻结 `close_hash`（schema `CODE-MOR:FINANCE:PERIOD_REOPEN:1`，RFC 8785，覆盖：期间、流程编号、原关账 ID 与原 `close_hash`、原因、申请编号、Maker、按账簿 ID 排序的 `as_of_sequence` 与冲回行〔账户 ID、方向、金额〕、步骤 1—2 结果摘要）；流程 PENDING_CHECK；期间 CLOSED → REOPENING。

## 6. 审批与驳回（E2/E3 的 REOPEN 分支）

- 共同校验沿用关账：PENDING_CHECK、Checker ≠ Maker、`approvedCloseHash` 等于 `close_hash`，TRY_LOCK 同一把锁。
- **批准**（一个事务）：期间须 REOPENING；重新按 §5 第 3 步规则计算冲回计划并与冻结摘要比对，不一致拒绝；按账簿 ID 升序为有冲回行的账簿生成 AUTO 指令（`source_system=FINANCE`、`source_event_key=PERIOD_REOPEN:{closeId}:{bookId}`、`instruction_type=PERIOD_PROFIT_REOPEN`、规则 Bundle 为空、`input_hash` 覆盖冲回行）并以 `JournalEntryType.REOPENING`、过账日 = 期间 `end_date` 调用过账内核；`period_close_book` 写 `posting_instruction_id` 并 FINALIZED（无冲回行的账簿直接 FINALIZED）；步骤 3 SUCCEEDED；Checker 审批记录；流程 COMPLETED、活动槽置空；期间 REOPENING → OPEN 且 `reopen_count + 1`。
- **驳回**（一个事务）：Checker 记录（REJECT）；步骤 3 SKIPPED；流程 REJECTED、活动槽置空；期间 REOPENING → CLOSED；不生成指令或分录。
- 关账 CLOSE 的批准/驳回逻辑保持不变。

## 7. 年末结转（P3，扩展 `StartPeriodCloseProcessor.postProfitClose`）

- **判定**：日历 `year_start_month`；期间 `end_date + 1 天` 为该月 1 日，即财年最后期间（R4）。
- **生成**：对每个账簿，先按现有规则生成收入/费用 → 本期损益行；若为年末期间，再计算该账簿主体 `CURRENT_PERIOD_RESULT/NONE` 账户的“截至锁定水位的 Journal 余额 + 本次结转行对它的净影响”，非零时追加两行：结余为贷方（盈利）则 `Dr CURRENT_PERIOD_RESULT / Cr RETAINED_EARNINGS`，借方（亏损）则相反。留存收益科目取结转规则 `retained_earnings_account_id`，账户 `RETAINED_EARNINGS/NONE` 不存在时自动创建。
- 账簿只要有任一行就生成结转指令（原“无收入费用不生成”规则调整为“无任何结转行不生成”），所以年末即使本期无收入费用，也会结转此前月份累计的本期损益。
- 重开年末期间时，冲回计划逐行反向原指令，自动包含年末结转行。

## 8. 数据库与文档变更

- **DDL**（[db/ddl.sql](./db/ddl.sql)，需本地 SQL 授权）：`t_period_close` 唯一键 `uk_period_reopen_target` 改为普通索引 `idx_period_reopen_target`；`t_close_step.step_type` 允许值注释追加 `PLAN_REOPEN_REVERSAL`、`POST_REOPEN_REVERSAL`。同步 PDManer 与 `finance-schema.sql`、测试 `init/ddl.sql`。
- **DML**：无。
- **文档**：`FINANCE_DATABASE.md` 的 `period_close` 行改为 `idx_period_reopen_target` 并说明理由；关账补充约定后追加“重开与年末结转（2026-10-17 决定 D1—D5）”一段。

## 9. 测试与验收映射

| AC | 用例（HTTP 入口） |
|---|---|
| AC-1 | 发起重开成功（REOPENING、PENDING_CHECK、摘要冻结、引用原关账、步骤 1—2 成功、步骤 3 PENDING）；非 CLOSED、存在更晚已关期间、已有活动流程均被拒；原因代码非法 400 |
| AC-2 | 批准：收入/费用恢复到关账前余额、本期损益冲回、期间 OPEN、`reopen_count=1`、冲回分录类型 REOPENING、过账日为期末 |
| AC-3 | 驳回：期间 CLOSED、无新 Journal；再次发起重开成功（验证 R1） |
| AC-4 | 重开后人工调整可过账；再次关账得到新 `closeNo` 并按当时余额结转；原关账详情不变 |
| AC-5 | 年末期间（构造 12 月期间、`year_start_month=1`）关账后本期损益 0、留存收益 = 全年利润（含此前月份累计）；非年末期间无留存收益行；重开年末期间一并冲回 |
| AC-6 | Maker 自审、摘要不一致、重复批准被拒；`clean verify` |

## 10. 阶段 4 多 agent 分工（建议）

- A：重开（E1、E2/E3 分支、查询字段、枚举、DDL 同步的模型与文档）。
- C：年末结转（`postProfitClose` 扩展与判定）。
- B：测试。
- 主会话：合并核对、`clean verify`。A 与 C 都会改关账相关类，约定 C 只改 `postProfitClose` 链路，A 不碰该链路。
