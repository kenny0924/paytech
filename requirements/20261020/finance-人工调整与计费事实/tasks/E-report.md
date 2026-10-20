# 卡 E 交付报告：Operations Web（2026-10-20）

结论：三个新页面（调整 / 期间关账 / 记账规则）、试算平衡「发起试算」按钮、能力清单、错误码映射与测试都已完成。`tsc --noEmit`、`npm test`（1126 / 1126 通过）、`npm run build` 均通过。未提交、未建分支、未 stash，未动 index（见第 5 节第 1 条）。

本卡由前一个 agent 开始、被强制停止后接续完成。接手时已有 `financeWorkflow.ts`、人工调整与计费事实两个页签及其抽屉、`WorkflowParts.tsx`、试算按钮、能力清单与导航登记；缺三个页面文件，`financeWorkflow.ts:435` 有类型错误。

## 1. 依据与开工说明

- 已读：`tasks/00-common.md`、`tasks/E-web.md`、`../technique.md`（第 2—5 节与修订后的 D4）、三个需求的 `requirement.md`、`C-report.md`（第 3、4 节）、`A-report.md`（第 1、8 节）、`B-report.md`。`D-report.md` 当时还不存在，BFF 合同按 team-lead 给的约定执行，并对照了 worktree 里卡 D 已写好的 BFF Controller 路径与 `FinanceBillingFactModels`。
- 响应字段对照的是 worktree 中 ledger / billing API 的 ResponseBody，以及 ledger 的 `CloseStepType` / `CloseStepStatus` / `PeriodCloseStatus` / `PeriodReopenReason` / `AutoInstructionType` / `AmountSource` / `RuleOwnerRole` 枚举。
- WRITE_API_EXAMPLE 第 1 / 11 节：不适用。本卡只改前端，没有 Processor、Context、事务或事务外步骤。

## 2. 改动文件（`Code-MoR-fin3/operations-console-web/`）

| 类型 | 文件 | 内容 |
|---|---|---|
| 新增 | `src/pages/FinanceAdjustments.tsx` | 「调整」页，含「人工调整」「计费事实」两个页签（`?tab=`），按 BUTTON 资源控制制单和复核 |
| 新增 | `src/pages/FinancePeriodClose.tsx` | 「期间关账」页：日历选择、看板、发起关账 / 重开、历史记录与详情入口 |
| 新增 | `src/pages/FinanceAccountingRules.tsx` | 「记账规则」页：规则包列表、版本列表、新建入口 |
| 新增 | `src/finance/PeriodCloseDrawers.tsx` | 重开弹窗、关账历史抽屉、关账详情抽屉（步骤、失败步骤、账簿最终试算链接、结转指令、摘要、审批、复核） |
| 新增 | `src/finance/RuleVersionDrawers.tsx` | 规则版本详情（分组规则行，提交 / 作废 / 审批 / 驳回）、新建版本编辑器（以当前生效版本为模板） |
| 新增 | `src/finance/financeWorkflow.test.ts` | 15 个测试，见第 4 节 |
| 接手时已有，本次修改 | `src/finance/financeWorkflow.ts` | 修 435 行类型错误；新增 `standardChartCode`；`FACT_LINE_INVALID`、`OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN` 默认消息改为与 Owner 枚举逐字一致 |
| 接手时已有，本次修改 | `src/finance/finance.css` | 新增行编辑器、抽屉页脚、复核栏、失败步骤行、「本人」标签及窄屏规则 |
| 接手时已有，本次未改 | `ManualAdjustmentsTab.tsx`、`ManualAdjustmentDrawers.tsx`、`BillingFactsTab.tsx`、`BillingFactDrawers.tsx`、`WorkflowParts.tsx`、`TrialBalanceTab.tsx`、`useFinanceCommand.ts`、`useFinanceData.ts`、`pages/FinanceLedger.tsx`、`navigation/ConsolePage.tsx`、`navigation/pageCapabilities.ts` | 逐个审阅过，与合同一致 |
| 修改 | `src/finance/finance.test.ts` | 财务页面登记改为 7 页及其动作；试算页签原先断言"只读、不发起试算"，改为"按钮受 `canRun` 控制、请求不带 Body" |
| 修改 | `src/configuration/configurationModel.test.ts` | 页面总数 37 → 40 |

## 3. 实现要点

- **能力清单**：`pageCapabilities.ts` 的 7 个财务组件（含 LEDGER 新增的 `OPERATIONS_FINANCE_LEDGER_MANAGE`）与 Platform `console-resource-capabilities.json` 完全一致，由测试读取 JSON 后 `deepEqual` 保证。
- **权限显示**：写按钮只在有对应 BUTTON 资源时显示。审批 / 驳回还要求单据处于待复核状态、且制单人不是当前账号；制单人本人看到的是"你是制单人，不能复核…"的说明。规则版本的提交 / 作废只对制单人本人的草稿显示；计费事实作废需要 MANAGE；新建规则版本在已有草稿或待审批版本时隐藏，并显示原因标签。
- **无请求体命令**：计费事实批准 / 作废、发起关账、发起试算、规则版本提交 / 审批 / 驳回 / 作废都以 `body=undefined` 调用，不发送正文（含 `{}`），并有测试检查。
- **错误码**：`ledgerWorkflowErrorTargets` 覆盖 `LedgerErrorCode` 全部 15 个 Code，`billingErrorTargets` 覆盖 `BillingFactErrorCode` 全部 4 个，测试直接解析 Java 枚举源码比对 Code 与消息。字段类错误显示在对应表单项上（期间、分录行、生效时间、替代事实），其余显示在抽屉顶部，带 Code。`BUSINESS_CHECK_FAILED` 走通用通知（带 Request ID）；409 显示冲突横幅；403 刷新授权。
- **关账耗时**：发起时按钮进入 loading，页面顶部显示"关账执行中"提示；命令是单飞的，其他关账 / 重开按钮全部禁用。返回 `FAILED` 时用警告提示，并打开详情查看失败步骤。
- **最终试算链接**：关账详情的账簿只有 `finalTrialRunId`，试算页签按 `runNo` 定位。页面读取该账簿本期的试算记录（第 1 页，50 条），按 ID 找到对应序号后链接到 `/finance/ledger?tab=trial&…&tbRun=`，并显示异常数。没有最终试算时，链接到最近一次试算或试算列表。异常明细在试算记录抽屉中只读查看。
- **规则行科目**：取 `STANDARD` 科目表（与 ledger `CreateRuleVersionProcessor` 一致）。可选项排除停用、不可记账和控制科目。模板里已不可选的科目仍显示，标注"（不可用）"且不能选择，便于看出需要替换。

## 4. 验证（实际执行，目录 `Code-MoR-fin3/operations-console-web`）

| 命令 | 结果 |
|---|---|
| `npx tsc --noEmit -p .` | 0 错误 |
| `npm test` | 1126 个测试，1126 通过，0 失败（其中新增 `financeWorkflow.test.ts` 15 个） |
| `npm run build` | 成功；有 jsx-runtime chunk > 500 kB 的提示，与本次改动无关 |

`financeWorkflow.test.ts` 覆盖：BFF 路径；无请求体命令与幂等键复用；能力清单与 Platform JSON 一致；两个错误码枚举逐字一致，以及错误落点；全部新解码器（合法与非法输入）；`canReview`、`ruleVersionActions`、`billingFactActions`、`hasAction`、`hasVersionInProgress`；`closablePeriodId`、`reopenablePeriodId`、`openPeriods`；`amountError`、`draftTotals`（借贷合计 / 差额 / 不完整 / 零额）；`manualAdjustmentBody`、`adjustableAccountOptions`、`ruleAccountOptions`；`billingFilters`、`factLineError`、`resolvePartyId`、`billingFactBody`（请求体不含操作人、`sourceSystem`、`sourceReference`）；`templateRuleLines`、`groupRuleLines`、`ruleLinesError`、`ruleVersionBody`（行号重排、UTC）；跳转链接；三个新页面的权限接线与空状态（检查源码）。

**没有运行：** 浏览器检查与真实 BFF 联调。BFF（卡 D）还在进行，本机也没有启动 BFF / Gateway。

## 5. 偏差与注意事项

1. **共享 index 里已有暂存项**：`git status` 显示前一个 agent 遗留的部分 Web 文件已在 index 中（`A ` / `M `）。本卡没有执行 `git add` / `reset`，也没有改动 index；提交时请用私有 index，只取本需求文件。
2. **试算异常只读**：关账详情没有逐条列出异常，只显示异常数并链接到试算记录抽屉（抽屉里已有异常表）。这样不在两处重复同一张表。
3. **人工调整原因代码**：ledger 没有固定目录，页面下拉沿用前一个 agent 定的 5 个值（会计更正、科目重分类、计提 / 摊销、审计调整、其他）。外部单据号写在分录行说明中（需求 Q4）。如果 ledger 有约定的原因代码，需要对齐。
4. **重开原因**用 ledger `PeriodReopenReason` 的三个值；**规则行余额桶**用 NONE / PENDING / AVAILABLE / LOCKED，由 ledger 账户身份矩阵兜底。
5. **规则版本作废**按 ledger 规则只给制单人本人显示（technique L3），并且需要 MANAGE；**计费事实作废**按 B 的实现，任何有 MANAGE 的账号都可以作废（B-report 偏离第 2 条）。
6. **发起试算**：FUTURE 期间按钮禁用（ledger 不支持）。CLOSED 期间按钮仍可点，是否拒绝由 ledger 决定。

## 6. 浏览器验收用页面状态清单

**调整 · 人工调整**（`/finance/adjustments?tab=manual`）
1. 未选账簿：空状态"选择账簿后查看人工调整"。
2. 列表：期间 / 状态 / 制单人筛选，"只看我制单的"，分页与总数；无数据时显示"没有符合条件的人工调整"。
3. 无 MANAGE：没有「新建人工调整」按钮，说明栏写"当前账号没有制单权限"。无 APPROVE：说明栏写"没有复核权限"。
4. 制单抽屉：期间下拉只有 OPEN 期间（没有时显示提示）；账户下拉为"账户号 · 科目编号 名称"，不含控制科目和停用科目；页脚实时显示借贷合计和平衡状态，不平衡或行不完整时「提交复核」禁用；少于 2 行时不能删除。
5. 服务端拒绝：`ADJUSTMENT_PERIOD_NOT_OPEN` 显示在期间字段；`ADJUSTMENT_UNBALANCED`、`ADJUSTMENT_ACCOUNT_INVALID`、`OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN` 显示在分录行；字段清单 `lines[i].amount` 落到对应行。
6. 详情抽屉：状态、内容摘要（可复制）、分录行与借贷合计、审批记录；已过账时显示"序号 N ↗"，跳到账本分录。
7. 复核：非本人且有 APPROVE 时显示「驳回」「审批过账」（二次确认，回传内容摘要）；本人时显示说明、不显示按钮；内容已变化时顶部显示 `REVIEW_CONTENT_CHANGED`。

**调整 · 计费事实**（`/finance/adjustments?tab=billing`）
8. 未查询：空状态"填写经营主体与币种后查询计费事实"。entityNo 格式错误时字段报错，时间区间颠倒时显示警告横幅。
9. 列表能看到草稿 / 已批准 / 已替代 / 已作废。
10. 登记抽屉（需 MANAGE）：头部字段；绑定头部的角色（Client、Local Partner、财务主体、经营主体）的 ID 只读显示头部值；收方非 Owner 时目标余额桶禁用；每行错误显示在"金额组件"下；`FACT_LINE_INVALID`、`FACT_SUPERSEDE_INVALID` 就地提示。
11. 详情：草稿且非本人、有 APPROVE 时显示「批准入账」；有 MANAGE 时显示「作废草稿」；本人时显示说明；"入账事件"显示已发布 / 待发布 / 不发布。

**期间关账**（`/finance/period-close`）
12. 未选日历：空状态。选中后看板按期间升序，显示状态、重开次数、最近记录（点击打开详情）、发起人和时间。
13. 「发起关账」只出现在最早的 OPEN 期间（之前全部已关账），「重开」只出现在最近已关账期间，都需要 MANAGE。没有 MANAGE 时说明栏有提示。
14. 发起关账：二次确认后，顶部显示"关账执行中…"，按钮 loading，其他操作禁用。成功后打开详情（待复核）；失败时警告提示，详情顶部红色横幅显示失败步骤（`RESOLVE_EXCEPTIONS` 有专门文案），失败行标红。
15. 重开弹窗：原因下拉（3 个值）和申请单号必填；`PERIOD_REOPEN_NOT_ALLOWED` 显示在弹窗顶部。
16. 详情：步骤表、各账簿（最终试算"序号 N ↗"链到试算平衡页签、异常数、结转指令、状态）、关账摘要、审批记录；非本人且有 APPROVE 时可「驳回」「审批通过」（回传关账摘要）；本人时显示说明。
17. 「全部记录」：历史抽屉分页，点击进入详情。

**账本查询 · 试算平衡**（`/finance/ledger?tab=trial`）
18. 有 `OPERATIONS_FINANCE_LEDGER_MANAGE` 且选好账簿和期间时显示「发起试算」；FUTURE 期间禁用；成功后切到"试算记录"并打开新记录抽屉；无权限时不显示按钮。

**记账规则**（`/finance/accounting-rules`）
19. 规则包列表显示中文名（`STANDARD_TRANSACTION` 交易记账规则、`STANDARD_ADJUSTMENT` 调整记账规则，未知代码显示代码本身）、当前版本与生效时间；未选规则包时有空状态。
20. 版本列表按版本号降序，当前版本带"当前"标签；有草稿或待审批版本时不显示「新建版本」，改为金色提示。
21. 版本详情：规则行按指令类型分组，科目显示"编号 名称"；制单人本人的草稿可「提交审批」「作废草稿」；非本人且有 APPROVE 时，待审批版本可「审批通过」「驳回」；审批后根据结果提示"立即生效"或"将于 … 生效"。
22. 新建版本：模板为当前生效版本的规则行；可增删改行；生效时间默认当前分钟；页面预检行不完整、组内行代码重复、缺少借方或贷方；`RULE_EFFECTIVE_FROM_INVALID` 显示在生效时间字段，`RULE_VERSION_IN_PROGRESS` / `RULE_LINE_INVALID` 显示在顶部。

## 7. 未完成项

- 浏览器验收与真实链路联调：等卡 D 完成，并在本机 / mor-test 起服务后按第 6 节执行。
- 如果 `D-report.md` 最终的字段或路径与本卡采用的约定不同，需要再核对一次（目前与 worktree 中 BFF Controller 一致）。

## 8. 追加：对齐 D-report 定稿（2026-10-20）

逐表核对了 D-report 第 2 节的 26 条路径、请求 / 响应字段和第 3 节错误码，Web 已有实现与之一致：路径、无 Body 命令、Body 字段（不含 makerId / checkerId，计费事实不含 sourceSystem / sourceReference）、计费事实明细 `lineNo` 为字符串、详情中多出的 pricing / links 等字段解码时忽略。按 team-lead 的两条要求追加以下改动：

1. **计费事实登记防重复提交**（`finance/BillingFactDrawers.tsx`）：BFF 每次登记都会生成新的 sourceReference。现有保护是命令单飞（`busy` ref），提交中表单禁用、取消按钮禁用、抽屉不能关闭，成功后关闭抽屉（再打开时为空白表单）。本次在此基础上把「提交复核」按钮在提交中显式置为 `disabled`，并在类注释写明原因。失败后用原样内容重试会复用同一个 Idempotency-Key，由 Gateway 回放首次结果。
2. **同 Code 不同来源的消息**：`api/session.ts` 的 `ApiError` 新增只读字段 `detail`，保存 Problem detail 原文。原有的 `message` 规则没有变化。`finance/finance.ts` 的 `financeErrorTarget` 改为优先显示 `detail`，没有 detail 时才用本表文案。这样 `SELF_REVIEW_FORBIDDEN` 在 ledger 和 billing 下各自显示 Owner 的默认消息。基础资料等已有映射也会改为显示上游 detail，对已登记 Code 来说内容就是默认消息。
3. **测试**（`finance/financeWorkflow.test.ts`）新增 2 例：同一个 Code 按 detail 显示 ledger / billing 各自的消息、detail 缺失时回退到本表文案；登记抽屉的防重复接线。

重跑结果：`npx tsc --noEmit -p .` 0 错误；`npm test` 1128 例，1128 通过，0 失败；`npm run build` 成功。浏览器检查和真实链路仍未执行。
