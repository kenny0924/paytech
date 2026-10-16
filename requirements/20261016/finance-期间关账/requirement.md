# Finance 期间关账（含试算异常投影重建）

状态：阶段 1 已确认（2026-10-16）：范围按第 2、3 节，D1—D6 全部按建议执行。

## 1. 背景与目标

FIN-22 与 [Finance 数据库设计](../../../agent-docs/database/FINANCE_DATABASE.md) §4.7、§6.6、全局不变量 15、43：会计期间需按固定步骤关账，冻结每个账簿的最终试算与结转分录，经 Maker-Checker 审批后进入 CLOSED，CLOSED 期间禁止普通过账。当前过账只校验期间 OPEN，但没有任何入口能关闭期间。

使用者：Finance 内部与运营核对人员（内部开发接口，不接 Gateway/授权）。

## 2. 本期范围（建议）

| # | 内容 |
|---|---|
| P1 | 发起关账（CLOSE）：期间 OPEN → CLOSING，占用活动槽，枚举该期间主体的全部账簿并锁定水位，按 7 个固定步骤同步执行：锁定水位、异步与 Journal 完整性检查、余额核对、试算、异常检查、损益结转、最终试算；全部账簿 FINALIZED 后冻结 `close_hash`，进入 PENDING_CHECK |
| P2 | 审批 / 驳回：Checker ≠ Maker 且确认同一 `close_hash`；批准 → 期间 CLOSED、流程 COMPLETED、释放活动槽；驳回 → 流程 REJECTED、期间回到 OPEN、释放活动槽 |
| P3 | 步骤失败：流程 FAILED、期间回到 OPEN、释放活动槽，保留步骤结果供排查 |
| P4 | 试算异常投影重建：对 `GENERAL_LEDGER_MISMATCH`（ACCOUNT）发起投影重建（Maker）→ Checker 批准后从 Journal 重算账户投影并把异常置为 RESOLVED，冻结解决摘要 |
| P5 | 查询：关账流程详情（步骤、账簿证据、审批）与期间的关账历史 |
| P6 | 初始化 DML：为 STANDARD 科目表预置结转规则（每个 REVENUE/EXPENSE 叶子科目 → CURRENT_PERIOD_RESULT / RETAINED_EARNINGS） |

已按上一轮回复采用：余额核对步骤按现有事实执行（无 Channel/银行数据则记录“无核对对象”结果，不静默跳过）；结转规则用初始化 DML 预置，不做维护接口。

## 3. 明确不做（建议）

期间重开（REOPEN，下一批）、年末结转到留存收益（下一批）、其他异常类型的处置（Recon Resolution、Controlled Waiver）、交易经济与 Treasury 相关核对、调度任务、Gateway/授权、页面。

## 4. 需要确认的决定（权威文档与表结构冲突或未定义）

| # | 问题 | 建议 |
|---|---|---|
| D1 | 关账顺序：若允许在前一期间仍 OPEN 时关闭后一期间，前期补记会改变本期期初，已冻结的最终试算失效 | **必须按顺序关账**：同一日历中早于本期间的期间必须全部 CLOSED 才能发起 |
| D2 | 结转分录的来源：文档要求 AUTO 指令必须冻结已批准的规则 Bundle Version 与来源事件，但损益结转不来自规则 Bundle；`origin_type` 只有 AUTO/MANUAL | 结转指令记为 **AUTO**，`source_system=FINANCE`、`source_event_key=PERIOD_CLOSE:{closeId}:{bookId}`、`instruction_type=PERIOD_PROFIT_CLOSE`，规则 Bundle 为空，并在数据库设计文档中把“系统生成的关账/重开指令”写为该规则的显式例外 |
| D3 | 账簿本期没有任何收入/费用时，文档要求每个账簿都有 CLOSING 指令，但零金额分录不允许存在 | 该账簿**不生成结转指令**，`posting_instruction_id` 为空（列可空），文档同步注明 |
| D4 | `period_close.close_hash` 为 NOT NULL，但流程在 RUNNING 时还没有 Hash | **修改模型：`close_hash` 改为可空**，PENDING_CHECK 起必填（与试算 `content_hash` 同一模式）；同步 PDManer、`finance-schema.sql`，并在本地库执行一条 `ALTER`（阶段 3 给出 SQL 后单独授权） |
| D5 | 结转对象：收入/费用账户按核算对象拆成多个账户，本期损益放到哪个账户 | 每个账簿把收入/费用科目下全部账户的本期净额结转到**账簿主体自身**的 `CURRENT_PERIOD_RESULT/NONE` 账户；不存在则在结转事务内按身份矩阵自动创建 |
| D6 | 关账期间其他期间的过账会推进账簿序号 | 最终试算按“锁定水位或本流程结转分录序号”取值，期间外分录因过账日期不同不影响本期数字；D1 保证不会有更早期间的补记 |

## 5. 验收标准（草案）

| 编号 | 标准 |
|---|---|
| AC-1 | 发起关账后期间为 CLOSING，普通过账（含人工调整审批）被拒；同期间第二个关账请求被拒或 409 |
| AC-2 | 7 个步骤按顺序执行并各有结果摘要；任一步骤失败 → 流程 FAILED、期间回到 OPEN、活动槽释放 |
| AC-3 | 损益结转后本期收入/费用科目期末为零，`CURRENT_PERIOD_RESULT` 等于本期利润；结转分录借贷平衡、类型 CLOSING |
| AC-4 | 每个账簿 FINALIZED：最终试算 SUCCEEDED、水位一致；`close_hash` 冻结；PENDING_CHECK |
| AC-5 | 审批：Checker ≠ Maker、Hash 一致 → 期间 CLOSED、流程 COMPLETED；驳回 → OPEN、REJECTED；CLOSED 期间过账被拒 |
| AC-6 | 前序期间未关闭时拒绝发起（D1） |
| AC-7 | 试算存在未解决异常时关账失败；投影重建经双人审批后异常 RESOLVED，重新发起关账成功 |
| AC-8 | 查询返回流程、步骤、账簿证据与审批；`clean verify` 通过 |

## 6. 适用阶段

阶段 1—5；D4 涉及模型与本地 SQL（单独授权）；阶段 4 继续使用多 agent 并行；提交另行确认。
