# 任务卡 E：Operations Web

先读 `00-common.md`、`../technique.md`（第 2—5 节，页面描述）、三个需求的 `requirement.md`（验收与页面验收）、`C-report.md`（组件 key、路由、actions）、`D-report.md`（BFF 路径与字段，唯一来源）。范围：`operations-console-web/**`。沿用现有财务页面的组件与样式（`src/finance/*`、antd），不引入新库。

1. **导航与能力**：`src/navigation/pageCapabilities.ts`、`menu.ts` 等登记三个新页面（`OPERATIONS_FINANCE_ADJUSTMENTS` `/finance/adjustments`、`OPERATIONS_FINANCE_PERIOD_CLOSE` `/finance/period-close`、`OPERATIONS_FINANCE_ACCOUNTING_RULES` `/finance/accounting-rules`，各自 MANAGE / APPROVE actions），LEDGER 页面 actions 加 `OPERATIONS_FINANCE_LEDGER_MANAGE`；与 C 的能力清单逐项一致。
2. **调整页**（两个页签）：
   - 人工调整：账簿选择 → 列表（期间、状态、制单人筛选，分页）→ 制单抽屉（期间只列开放期间；原因；分录行：账户按"账户号 + 科目编号 名称"选择，排除控制科目与停用科目，借贷合计实时显示，不平衡禁止提交）→ 详情抽屉（分录行、借贷合计、内容哈希、审批记录、分录跳转到账本；有 APPROVE 权限且非本人时显示审批 / 驳回，回传哈希）。
   - 计费事实：主体（entityNo）+ 币种 + 筛选（Client、状态、类型、时间）→ 列表 → 登记抽屉（MANUAL_ADJUSTMENT：Client、明细行：金额组件 / 付方角色与 ID / 收方角色与 ID / 金额 / 方向 / 目标余额桶；可选工单号、替代事实）→ 详情（明细行、状态；APPROVE 权限非本人可批准，MANAGE 可作废草稿）。
3. **期间关账页**：财务日历选择 → 看板（期间、状态、最近关账 / 重开记录）→ 操作：「发起关账」只对最早的开放期间显示；「重开」只对最近已关账期间显示（原因下拉 + 申请单号）→ 详情抽屉（步骤列表与结果、失败步骤与原因、各账簿最终试算链接到试算平衡页签、结转指令、关账哈希、审批记录、复核按钮回传哈希；试算异常只读）。关账发起耗时较长时显示进行中状态，不重复提交。
4. **试算平衡页签**（现有）：加「发起试算」按钮（需 `OPERATIONS_FINANCE_LEDGER_MANAGE`），成功后刷新试算记录并打开新记录。
5. **记账规则页**：规则包列表（名称由代码映射：`STANDARD_TRANSACTION` 交易记账规则、`STANDARD_ADJUSTMENT` 调整记账规则；未知代码显示代码本身）→ 版本列表（状态、生效时间）→ 版本详情（按指令类型分组的规则行，科目显示编号 + 名称）→ 新建（以当前生效版本为模板复制行，行编辑器增删改，生效时间）→ 提交（制单人）/ 审批 / 驳回（APPROVE、非本人）/ 作废草稿（制单人）。
6. 所有写操作按权限显示；业务拒绝显示上游中文 detail；空状态与无权限状态有提示。
7. 测试：`finance.test.ts` 或新测试文件覆盖新解码器、路径、权限显示规则与关键纯函数（借贷合计、可操作期间判断、模板复制）；`npm test`、`npm run build` 通过（worktree 内 `npm ci`）。
8. 报告 `E-report.md`：文件清单、页面状态清单（供浏览器验收）、偏差。
