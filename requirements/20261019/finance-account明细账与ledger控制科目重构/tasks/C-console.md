# 任务卡 C · Operations BFF 与 Web 财务页面适配 + 明细账查询

先读 `00-common.md`、`T0-report.md`。设计：`technique.md` 2.2「Operations BFF」、2.4 页面；画板导出 `REQ/assets/stage3/pencil/*.png`（04 / 05 / 07 / 08 / 09 / 11 修改，13 / 14 / 15 新增）；前序实现与约定：`requirements/20261018/operations-console-财务基础资料与账本余额查询/technique.md`、`delivery.md` 阶段 4 结果。

## 允许改动的路径

`paytech/operations-console-bff/**`、`operations-console-web/src/**`、`operations-console-web/README.md`（不改 `docs/design.pen`）。

## 1. BFF（`paytech/operations-console-bff`）

1. `FinanceLedgerBookController` / `FinanceLedgerBookModels`：`ownerType` 正则与 `@Pattern` 改 `LedgerOwnerType` 五值（引用枚举生成正则，不写字面量）；分录行与详情模型透传 `originType`（含 ACCOUNT_POSTING）、`accountPostingId`、`accountPostingSequence`、行 `accountEntryId`。
2. `FinanceBalanceClientImpl` / 错误码代理：`BalanceQueryErrorCode.OWNER_ACCOUNT_NOT_FOUND`。
3. 新增 `FinanceSubledgerController`（`/finance/owner-accounts/v1`、`/finance/owner-accounts/v1/{accountId}/entries`、`/finance/account-postings/v1/{postingId}`，`FINANCE_ACCOUNTING_READ`，Query 白名单按 technique 2.2 R19–R21）、`FinanceSubledgerClient` Port + Impl（account 代理工厂增加 `OwnerAccountQueryApi`、`AccountPostingApi`，错误码 `AccountPostingErrorCode`）、模型与 `FinanceContractConverter` 转换、精确路由集合 +3、`OperationsConsoleBffApplicationAutoTest` 路由计数、Platform Simulator 路由权限、`application.yml` / README 增量。
4. 测试：`finance-ledger-boundary.json` / `finance-setup-boundary.json` 夹具改五值 Owner 与控制账户；`FinanceLedgerBookBrowserAutoTest` 补 ACCOUNT_POSTING 分录透传；`FinanceBalanceBrowserAutoTest` 改新错误码；新增 `FinanceSubledgerBrowserAutoTest`（转发、错误透传、分页回显、Query 白名单、403）与 `OperationsAccountUnavailableAutoTest` 补新路由；`AccountOwnerSimulator` 增加新接口 DTO。

## 2. Web（`operations-console-web`）

1. `src/finance/finance.ts`：`ownerTypes` 改五值；`bucketedAccountCodes` 删除（账户表单余额桶固定 NONE 且禁用）；`originTypes` 加 `ACCOUNT_POSTING`；`JournalEntryLine` 加 `accountEntryId`，分录详情加 `accountPostingId` / `accountPostingSequence`；`balanceErrorTargets` 改 `OWNER_ACCOUNT_NOT_FOUND → ownerId`；新增明细账类型（`OwnerAccount`、`AccountEntry`、`AccountPosting`）、路径、解码器、筛选与 URL 状态。
2. `FinanceModals.tsx` 新建账户：Owner 类型五值、余额桶固定 NONE、提示文案按画板 05；`SetupBooksTab.tsx` / `LedgerDrilldown.tsx` 列表与筛选按画板 04 / 07；`JournalEntryDrawer.tsx` 按画板 09：来源区显示「ACCOUNT_POSTING · account」与「account 批次」行（批次 ID · 序号），控制科目行显示「流水 …{后 4 位} ↗」链接到明细账流水（路由带 `postingId` / `accountId` 需先经 R21 取批次再定位，或链接到 R20 列表并高亮 `entryId`：按实现简单者选，报告说明）；`FinanceBalances.tsx` 错误挂 Owner ID 字段与空状态文案按画板 11。
3. 新页面 `pages/FinanceSubledger.tsx` + `src/finance/Subledger*.tsx`：主体类型 / 主体标识 / 币种必填 + Owner 类型 / Owner ID / 科目 / 桶筛选 → 账户列表（画板 13）→ 账户流水（画板 14：批次序号、入账时间、来源类型标签、行号、方向、金额、冲回关系、事实类型、来源两行）→ 入账批次抽屉（画板 15）；面包屑逐级返回保留筛选；加载 / 空状态（未查询、无账户、无流水）/ 错误反馈复用现有组件；金额右对齐按币种精度。
4. `navigation/pageCapabilities.ts` +1（能力键 `OPERATIONS_FINANCE_SUBLEDGER`，路径 `/finance/subledger`，父 `OPERATIONS_FINANCE`，沿用 READ 权限）、`ConsolePage.tsx` +1、`configurationModel.test.ts` 页面计数 33 → 34；`finance.test.ts` 按新规则改写并补明细账解码 / 筛选 / 错误映射用例。

## 3. 验证

`./mvnw -B -o verify -pl operations-console-bff/paytech-operations-console-bff-test -am` 全部通过；`npm --prefix operations-console-web test` 与 `npm --prefix operations-console-web run build` 通过。跨线一致性自查：Web 路径常量 = BFF 路由 = technique 2.2 路由表；Web 发送的查询键在白名单内；错误码表覆盖 account 新 Code。报告 `REQ/tasks/C-report.md`（含页面与画板逐一对照）。
