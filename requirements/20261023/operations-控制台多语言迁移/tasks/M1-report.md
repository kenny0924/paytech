# M1 报告：finance、recon 文案迁移

执行日期 2026-10-23 · 工作目录 `/Users/kenny/Desktop/Workspace/Code-MoR-opsi18n-m1`（在 F0 成果上继续，未提交、未推送）。未调用 Superpowers skill，未派生子代理；主工作区只写本报告。

## 1. 迁移文件

全部在 `operations-console-web/` 下：

- **finance/**（27 个源码 + 2 个测试）：`finance.ts`、`financeWorkflow.ts`、`FinanceParts.tsx`、`WorkflowParts.tsx`、`useFinanceCommand.ts`、`useFinanceData.ts`、`FinanceModals.tsx`、`ChartAccountModals.tsx`、`Setup{Entities,Calendars,Books,Charts}Tab.tsx`、`LedgerDrilldown.tsx`、`JournalEntryDrawer.tsx`、`TrialBalanceTab.tsx`、`Subledger{Accounts,Entries,PostingDrawer}.tsx`、`ManualAdjustment{sTab,Drawers}.tsx`、`BillingFact{sTab,Drawers}.tsx`、`PeriodCloseDrawers.tsx`、`RuleVersionDrawers.tsx`；`finance.test.ts`、`financeWorkflow.test.ts`。
- **reconciliation/**（含 `detail/` 6 个详情页）：`reconModel.ts`、`reconRoutes.ts`、`ReconParts.tsx`、`ReconDetail.tsx`、`ClearingSettings.tsx`、`ImportDrawer.tsx`、`useReconCommand.ts`、`useReconData.ts`、`useReconDetail.ts`、`detail/{BankLine,BankStatement,CashAccount,ChannelStatement,ClearingAccount,Exception}Detail.tsx`；`reconModel.test.ts`、`reconRoutes.test.ts`。
- **pages/**：`Finance{AccountingRules,Adjustments,Balances,Ledger,PeriodClose,Setup,Subledger}.tsx`、`Recon{Bank,CashAccounts,ChannelLines,ClearingAccounts,Exceptions,PlatformTxns,Statements,Summary}.tsx`。
- **目录 JSON**：`src/i18n/en/finance.json`、`src/i18n/en/recon.json`、`i18n/zh-CN/finance.json`、`i18n/zh-CN/recon.json`。
- **守卫**：`src/i18nSourceGuard.test.ts` 的 `MIGRATED_DIRS` 加入 `finance/`、`reconciliation/` 与上面 15 个页面文件。

改动范围外文件：无（`useFinanceCommand` / `FinanceParts` 被 recon 复用，都在本卡范围内）。

## 2. 主要做法

- **枚举**：finance 的 `*Labels` 映射全部删掉，显示名改为 `finance.enum.<类型>.<值>`，组件内 `enumLabel(t, 'enum.side', v)`；需要下拉顺序的保留代码数组（`adjustmentReasonCodes`、`factTypes`、`instructionTypes`、`subledgerAccountCodes`、`financeEntityStatuses` 等）。`StatusTag` / `WorkflowTag` 的 `labels` 属性改为 `enumType`。
- recon 的 22 组映射改为 `ReconEnum { type, codes }`（`directionEnum` 等），配 `reconLabel(t, kind, code)` / `reconOptions(t, kind)`；`ReconTag` 的 `labels` 改为 `kind`；渠道账单行三态与五态共用 `recon.enum.lineMatchStatus`；原「关闭待复核」临时映射改为 `closePendingEnum`。
- **错误码落点表**：`{ field, message }` 改为 `{ field, messageKey }`（`FinanceErrorRule`），`financeErrorTarget` 调用时 `t(messageKey)`，后缀改为 ICU `error.withCode`（en「{message} ({code})」）。Problem `detail` 仍原样优先显示。测试改为：zh-CN 种子与 Java `LedgerErrorCode` / `BillingFactErrorCode` 默认消息逐字一致、每个 messageKey 在英文基准中存在。
- **模型函数**（校验、CSV 逐行原因、余额卡片、变更字段名、期间名等）用模块级 `translator('finance' | 'recon')`，调用时取文案；`gateItems`、规则版本动作文案等顶层常量只存 key。
- 页面 h1 与菜单同名的全部用 `nav.resource.<CODE>`（账务基础资料、账本查询、余额查询、明细账查询、调整、期间关账、记账规则与 8 个对账页）；详情页面包屑「财务 / 对账」也取 `nav.resource.*`。
- 「当前账号没有…权限。」这类原来前置空格拼接的句子保留拼接空格，句子本身一个 key。
- `reconRoutes.ts` 解析服务端数据的两处中文（来源摘要「第 N 行」、Owner 默认消息「不存在: 」后缀）改为 `\u` 转义并注明是数据。
- 测试断言改为英文或断言源码引用 key；未删除测试、未放宽断言（科目编号类别错误由 `match` 改为完整 `equal`，新增 0 / 1 位小数的复数断言）。

## 3. key 数与自检

| 项 | 结果 |
|---|---|
| key 数 | finance 834、recon 541（en / zh-CN 同一组 key） |
| `npm test` | 1475 个，全部通过 |
| `npx tsc --noEmit` / `npx vite build` | 通过 / 通过（只有既有的大 chunk 提示） |
| `node scripts/i18n-export-source.mjs` | 成功：21 个命名空间、2109 key（finance 834、recon 541） |
| 守卫 | 本卡范围内源码汉字 0，已纳入 `MIGRATED_DIRS` 严格检查；全局剩余 220 / 354 个文件、约 8519 处 |
| zh-CN 种子 | 脚本核对：每个 zh 值去掉 `{参数}` 后的片段都能在迁移前源码中找到；16 个合并拼接句（如「明细行 · {currency}」「{count} 个启用科目」「执行失败：{step}」）按原渲染结果写成整句 |

## 4. 拿不准的英文用词（请主会话定）

1. **驳回 / 已驳回**：按术语表译为 Return / Returned（复核驳回、期间关账驳回、规则版本驳回、对账处置驳回）；而银行账户复核、匹配建议里的「拒绝 / 已拒绝」用 Reject / Rejected。若希望复核动作统一用 Reject，需改约 18 个 key。
2. **科目**：`GL Account`（术语表）；科目表 `Chart of Accounts`，汇总 / 末级 `Summary / Leaf`，余额方向 `Normal Balance`。
3. **明细账账户科目六条**（CLIENT_PAYABLE 等）：`Client Payable`、`Client Security Deposit`、`Client Recovery Receivable`、`Partner Payable`、`Partner Recovery Receivable`、`Bank Cash`。
4. **余额卡片**：`PENDING Maturing Payable`、`AVAILABLE Available Payable`、`LOCKED Locked Payable`（原文「待成熟应付 / 可用应付 / 已锁定应付」）。
5. **期间名**：`FY2026 P3`（原「2026 年第 3 期」）；期间状态 FUTURE「未开始」= `Not Started`。
6. **桶移动** `Bucket Movement`、**事实** `Fact`、**冲回** `Reversal`、**分录水位** `Journal Sequence`、**锁定水位** `Lock Sequence`、**损益结转** `Close Profit and Loss`。
7. **对账**：已对平 `Reconciled`、对平门禁 `Reconciliation Gate`、暂记 `Suspense`、在途 `In-Transit`、到账 `Cash Received / Cash Confirmed`、移动状态 OPEN「待到账」= `Awaiting Cash`、尾号 `ending 1234`、异常「待处置」= `Pending Resolution`、处置 `Resolution`（术语表）、开异常 `Open Exception`。
8. **人员角色列**：对账「提交人」译 `Submitted By`，财务「制单人 / 复核人」译 `Maker / Checker`（术语表）。
9. 详情页「未找到该{对象}」：对象名用小写英文（`bank account not found` 句首小写），如需句首大写可改为整句 key。

## 5. 与指南不一致之处

- 无实质偏离。补充两点约定供 F9 参考：模块私有枚举在 recon 用 `ReconEnum` 描述对象（type + codes）代替逐处 `enumLabel` 前缀字符串，组件统一经 `reconLabel` / `reconOptions` 取值；错误码表的文案 key 存在表里（`messageKey`），一致性测试看不到这些动态 key，已在模块测试中逐个 `t.has` 断言。
