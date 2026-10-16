# Finance 期间关账 — 技术方案

状态：阶段 2 已确认（2026-10-16）；阶段 3 评审稿见第 4 节起。

**规模总结（本次方案）：** 只改 Finance；新增 5 个写接口、2 个查询接口；新增表 0（5 张已存在）；修改 1 张表 1 个字段（`period_close.close_hash` 改可空，结构变化）；索引 0；初始化 DML 7 行；页面 0。

## 1. 需求项差异

| 需求项 | 当前 | 缺口 | 处理 |
|---|---|---|---|
| P1 发起关账 | `PeriodStatus` 已含 CLOSING/CLOSED；过账内核以期间共享锁校验 OPEN；试算服务可按当前水位试算 | 5 张关账表无代码；试算只能取“当前水位”，不能按指定水位执行；无前序期间检查；无结转指令生成 | 新建关账持久化、`PeriodCloseService`、Processor；试算服务增加“按指定水位执行”入口（复用聚合 SQL，只换水位来源） |
| P1 损益结转 | 过账内核 `JournalPostingService` 支持任意 VALIDATED 指令；`JournalEntryType.CLOSING` 已有 | 缺按科目汇总本期收入/费用、生成 AUTO 结转指令（D2）、自动创建本期损益账户（D5） | 复用过账内核；结转在关账流程事务内完成 |
| P2/P3 审批与失败 | 人工调整已有 Maker-Checker 模式 | 缺关账审批、驳回、失败回滚期间状态与活动槽释放 | 新建审批/驳回 Processor；状态迁移全部带期望状态与版本 |
| P4 投影重建 | 试算记录 OPEN 异常；账户投影可由 Journal 重算 | 缺异常解决（UNDER_REVIEW → RESOLVED）、解决摘要、投影 CAS 重建 | 新建发起/批准两个写入口；重建只更新投影、不写 Journal |
| P5 查询 | — | 缺关账详情与历史 | 新查询接口 |
| P6 结转规则 DML | `finance-initial-data.sql` 只有币种与科目 | 缺 `close_account_rule` 初始化 | 追加 DML（REVENUE/EXPENSE 叶子科目 → `CURRENT_PERIOD_RESULT` / `RETAINED_EARNINGS`） |
| D4 模型 | PDManer 与 Schema 中 `period_close.close_hash` 为 NOT NULL | 与流程中 RUNNING 状态冲突 | PDManer、`finance-schema.sql`、测试 `init/ddl.sql` 改为可空；本地 `ALTER TABLE t_period_close MODIFY close_hash CHAR(64) NULL`（单独授权） |
| D2/D3 文档 | FINANCE_DATABASE §4.3、§4.7、不变量 43 | 未写系统关账指令例外与“无损益账簿不生成指令” | 在 Finance 自有文档段落中补充例外说明 |

## 2. 影响

| 系统/资产 | 影响 | 改动 |
|---|---|---|
| Finance api/accounting/web/test | 关账、审批、异常处置、查询的全部代码与测试 | 是 |
| Finance PDManer、`finance-schema.sql`、`finance-initial-data.sql` | D4 列改可空；P6 结转规则 DML | 是 |
| 本地 `mor_finance` | 1 条 `ALTER`、结转规则 DML 插入 | 需单独授权 |
| `agent-docs/database/FINANCE_DATABASE.md` | D2、D3、D4 的权威说明 | 是（只改 Finance 文档） |
| 其他系统、Gateway、BFF、requirements-site | 无（`Trial Balance & Period Close` 已是目录内部域） | 否 |

## 3. 风险

| 编号 | 风险 | 处理方向 |
|---|---|---|
| R1 | 关账与过账并发：关账推进 CLOSING 的同时有过账进行 | 关账以 `FOR UPDATE` 锁期间行推进 CLOSING，过账持期间共享锁，两者互斥；推进完成后过账锁后复验期间状态即拒绝 |
| R2 | 关账是长流程（多账簿 × 试算），若放进一个事务会长时间持锁 | 按步骤拆成短事务：推进 CLOSING 与登记流程一个事务；每个账簿的结转 + 最终试算各一个事务；最后冻结 Hash 一个事务。期间处于 CLOSING 即可阻断普通过账，不依赖长事务 |
| R3 | 中途进程崩溃留下 CLOSING 期间与活动流程 | 本期提供“标记失败”只能由下一次发起时检测：若存在超时的 RUNNING 流程则拒绝并提示；自动恢复留作后续（记录为已知限制） |
| R4 | 投影重建与过账并发 | 重建按账户主键升序加锁，使用余额版本 CAS；与过账同一锁顺序 |
| R5 | 自动创建本期损益账户与人工创建冲突 | 依赖账户身份唯一键；冲突时读取已存在账户 |

## 4. 接口设计

内部开发接口；ID、序号、金额为字符串。

**`PeriodCloseApi`（`/accounting-periods/v1`）**

| # | 方法与路径 | 请求 | 成功响应 |
|---|---|---|---|
| C1 | `POST /{periodId}/closes` | `makerId` | 201 `PeriodCloseResponseBody`：同步执行全部步骤，结果为 `PENDING_CHECK` 或 `FAILED`（失败时返回失败步骤） |
| C2 | `POST /{periodId}/closes/{closeNo}/approve` | `checkerId, approvedCloseHash` | 200 `PeriodCloseResponseBody`（COMPLETED） |
| C3 | `POST /{periodId}/closes/{closeNo}/reject` | `checkerId, approvedCloseHash` | 200 `PeriodCloseResponseBody`（REJECTED） |
| C4 | `GET /{periodId}/closes?page=&size=` | — | 分页，按 `closeNo` 降序 |
| C5 | `GET /{periodId}/closes/{closeNo}` | — | `PeriodCloseDetailResponseBody`：头 + `steps[]` + `books[]` + `approvals[]` |

`PeriodCloseResponseBody`：`periodCloseId, periodId, closeNo, closeType, status, makerId, checkerId, closeHash, failedStepType, createTime`。
`steps[stepNo, stepType, status, resultHash, completedTime]`；`books[ledgerBookId, asOfSequence, finalTrialRunId, postingInstructionId, status]`；`approvals[approvalRole, actorId, decision, approvedCloseHash, decisionTime]`。

**`TrialExceptionApi`（`/trial-balance-exceptions/v1`）**

| # | 方法与路径 | 请求 | 成功响应 |
|---|---|---|---|
| E1 | `POST /{exceptionId}/projection-rebuild` | `makerId` | 200：异常 OPEN → UNDER_REVIEW，`resolutionType=PROJECTION_REBUILD` |
| E2 | `POST /{exceptionId}/projection-rebuild/approve` | `checkerId` | 200：重建账户投影，异常 RESOLVED，返回重建前后摘要与 `resolutionContentHash` |

只允许 `GENERAL_LEDGER_MISMATCH` + `ACCOUNT`；Checker ≠ Maker；同一异常已处理时拒绝。错误统一 400 `BUSINESS_CHECK_FAILED`，锁冲突 409。

## 5. 关账执行（C1）

`StartPeriodCloseProcessor`（`AbstractLockProcessor`，TRY_LOCK `period-close:{periodId}`），按短事务推进：

| 步骤 | 事务内容 | 失败条件 |
|---|---|---|
| 准备（事务前） | 期间存在且 OPEN；同日历中 `end_date` 早于本期开始的期间全部 CLOSED（D1）；枚举主体与日历相同的全部账簿 | 不满足即 400，不创建流程 |
| 1 LOCK_SEQUENCE | 期间行 `FOR UPDATE` 复验 OPEN；`close_no = max + 1`；插入 `period_close`（CLOSE、RUNNING、`active_slot=1`、`close_hash` 空）；期间 OPEN → CLOSING；每个账簿序号行 `FOR SHARE` 取水位，插入 `period_close_book`（LOCKED）；插入 7 个步骤（PENDING），本步骤 SUCCEEDED | 活动槽冲突 → 409 |
| 2 CHECK_ASYNC_AND_JOURNAL | 本期账簿内存在未 POSTED 的指令、本期分录借贷不平或无行、`t_compensation_event` 存在 INIT/PROCESSING/ERROR 记录 | 任一存在 |
| 3 RECONCILE_ACCOUNT_BALANCES | 按现有事实执行：统计 CHANNEL_ACCOUNT / CASH_ACCOUNT 账户；均为零余额或不存在 → SUCCEEDED 并在结果中记录“无核对对象” | 存在非零余额的渠道或银行账户（Treasury 尚未上线，无法核对，Fail-Closed） |
| 4 GENERATE_TRIAL_BALANCE | 每个账簿按锁定水位执行试算（试算服务新增“按指定水位执行”） | — |
| 5 RESOLVE_EXCEPTIONS | 检查步骤 4 的试算 | 任一试算 FAILED 或仍有 OPEN/UNDER_REVIEW 异常 |
| 6 POST_PROFIT_CLOSE | 每个账簿一个事务：汇总本期（过账日在期间内、序号 ≤ 水位）REVENUE/EXPENSE 科目下每个账户的净额；非零则生成 AUTO 指令（D2）把其冲为零，差额记入账簿主体的 `CURRENT_PERIOD_RESULT/NONE` 账户（不存在则创建，D5）；以 CLOSING 分录、过账日期 = 期间结束日过账；水位推进到该分录序号；无收入费用的账簿不生成指令（D3） | — |
| 7 FINAL_TRIAL_BALANCE | 每个账簿按最终水位试算，必须 SUCCEEDED；账簿 FINALIZED 并冻结 `final_trial_run_id` | 试算 FAILED |
| 冻结 | 计算 `close_hash`（`CODE-MOR:FINANCE:PERIOD_CLOSE:1`：流程头、按账簿 ID 排序的账簿证据、按步骤号排序的步骤结果摘要、最终试算 `content_hash`、结转指令 ID）；流程 PENDING_CHECK；写入 Maker 审批记录 | — |

- **失败处理：** 步骤 FAILED（带结果摘要），流程 FAILED、`active_slot` 置空，期间 CLOSING → OPEN；已过账的结转分录保留（重新关账时这些科目本期净额已为零，不会重复结转）。
- **过账内核调整：** `JournalPostingService` 期间校验改为：普通分录要求 OPEN；`CLOSING/REOPENING` 分录允许期间处于 CLOSING/REOPENING。人工调整等普通过账在 CLOSING/CLOSED 期间继续被拒。
- **审批（C2）：** 期间行 `FOR UPDATE`；流程 PENDING_CHECK、Checker ≠ Maker、Hash 一致；复验全部账簿 FINALIZED、最终试算 SUCCEEDED 且水位一致；写 Checker 记录；流程 COMPLETED、`active_slot` 置空；期间 CLOSING → CLOSED。
- **驳回（C3）：** 写 Checker 驳回记录；流程 REJECTED、`active_slot` 置空；期间 CLOSING → OPEN。
- **锁顺序：** Redis 关账锁 → 期间行（排他）→ 各账簿序号行（共享，按账簿 ID 升序）；与过账（期间共享 → 序号排他 → 账户升序）不形成环。

## 6. 投影重建（E1/E2）

- E1：异常 OPEN、类型与引用合法 → UNDER_REVIEW，记录 `resolution_type`、`resolution_maker_id`。
- E2：TRY_LOCK `trial-exception:{exceptionId}`；Checker ≠ Maker；一个事务内按账户行 `FOR UPDATE` 读取投影，从该账户全部分录重算借贷累计、净额与最后序号，以 `balance_version` CAS 写回；`resolution_ref_id = 账户 ID`；`resolution_content_hash` 覆盖异常、账户、重建前后四个值、Maker、Checker（`CODE-MOR:FINANCE:PROJECTION_REBUILD:1`）；异常 RESOLVED，`resolution_checker_id`、`resolution_time` 冻结。不写 Journal、不改序号。

## 7. 数据库与文档变更

| 项 | 内容 |
|---|---|
| 本地增量 DDL | [db/ddl.sql](./db/ddl.sql)：`t_period_close.close_hash` 改为可空 |
| 初始化 DML | [db/dml.sql](./db/dml.sql)：`t_close_account_rule` 7 行（ID 4001—4007）；同时追加到 `finance-initial-data.sql` |
| 模型 | PDManer 与 `finance-schema.sql` 中 `close_hash` 改为可空 |
| 文档 | `FINANCE_DATABASE.md`：§4.3 注明系统关账/重开指令为 AUTO 且无规则 Bundle（D2）；§4.7 与不变量 43 注明无收入费用的账簿不生成结转指令（D3）；`close_hash` 在 PENDING_CHECK 前为空（D4） |

## 8. 测试与验收映射

| AC | 用例 |
|---|---|
| AC-1 | 发起后期间 CLOSING；同期间并发发起 409；CLOSING 期间审批人工调整被拒 |
| AC-2 | 7 个步骤结果摘要齐全；分别制造步骤 2（未过账指令）、步骤 3（银行账户非零余额）、步骤 5（投影篡改）失败 → FAILED、期间 OPEN、活动槽释放 |
| AC-3 | 收入 300、费用 120 → 结转后两科目期末为零，`CURRENT_PERIOD_RESULT` 为 180；结转分录为 CLOSING、借贷平衡、过账日为期间结束日 |
| AC-4 | 账簿 FINALIZED、最终试算 SUCCEEDED、水位等于结转分录序号；无收入费用的账簿无结转指令；`close_hash` 64 位 |
| AC-5 | 批准 → CLOSED、COMPLETED；驳回 → OPEN、REJECTED；Maker 自审与 Hash 不符被拒；CLOSED 期间人工调整被拒 |
| AC-6 | 前序期间 OPEN 时发起被拒 |
| AC-7 | 投影篡改导致关账 FAILED → 发起重建 → Checker 批准 → 异常 RESOLVED、投影恢复 → 重新关账成功 |
| AC-8 | 关账列表与详情；`clean verify` |

## 9. 阶段 4 多 agent 分工

| Agent | 范围 |
|---|---|
| A 关账生产代码 | 5 张关账表持久化、`PeriodCloseService`、C1—C5、过账内核期间校验调整、试算“按指定水位执行”、PDManer/Schema/初始化 DML/文档修改 |
| C 异常处置生产代码 | `TrialExceptionApi` E1/E2、投影重建（只新增 Account 重建相关 Mapper/Repository 方法，不改 A 的文件） |
| B 测试 | 第 8 节全部测试、TestMapper 与清理清单扩展 |
| 主会话 | 合并、编译、契约核对、`clean verify`、自查 |

## 10. 研发实现说明（阶段 4）

- 与契约一致。实现细节：请求体字段校验失败为 422 `VALIDATION_ERROR`；步骤执行中出现意外异常时先把流程置 FAILED、期间回到 OPEN 再原样抛出；`TrialBalanceService.runAt` 对最后序号超过水位的账户跳过投影核对（其投影已包含水位后的分录）；投影重建摘要输入按 HALF_UP 收敛到币种精度（只影响摘要，不改库内值）；投影重建的发起使用 `AbstractProcessor` + 条件更新，批准使用 Redis 锁。
