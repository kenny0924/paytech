# 任务卡 C：Operations BFF 与 Web

先读 `00-common.md`。在 A、B 两卡完成后开始（依赖其 `*-api` 合同）。范围：`paytech/operations-console-bff/**`、`operations-console-web/**`。

## BFF

- 新增 8 条转发（technique 2.3：L1—L6，以及 `TrialBalanceApi` 列表、详情；**不**接入试算运行）。路径为 `/finance/...` 加 ledger 原路径，参照 `FinanceSetupController` / `FinanceLedgerBookController` 的写法与 `requireQueryKeys` 白名单；写接口转发 `X-Gateway-Actor-Subject` / `X-Gateway-Session-Id`。
- BFF 模型（`FinanceSetupModels`、`FinanceLedgerBookModels`、`FinanceSubledgerModels` 等）加 M1—M6 新字段与新响应模型（转换器开启了 `FAIL_ON_UNKNOWN_PROPERTIES`，字段必须齐全）；既有列表白名单加 `accountStatus`（科目列表）、`accountNo`（账簿账户、明细账账户）。
- `FinanceClientConfiguration` 注册新错误码（随 `LedgerSetupErrorCode.values()` 自动带入则核对即可）。
- 测试：对应 `*BrowserAutoTest` 与边界 JSON（`finance-setup-boundary.json`、`finance-ledger-boundary.json`、`finance-subledger-boundary.json`）补新路由；Simulator 补新上游响应；`./mvnw -o -pl paytech/operations-console-bff/paytech-operations-console-bff-test -am verify`（如需 Redis / DB，按 00 卡隔离）。

## Web（`operations-console-web/src/finance/`）

按 technique 2.5：
- `SetupChartsTab.tsx` 改为科目树（编号排序、展开 / 折叠、搜索、显示已停用开关、已采用提示条）；新增 / 编辑弹窗、停用 / 启用确认、变更记录抽屉；写操作受 `OPERATIONS_FINANCE_SETUP_MANAGE` 控制。
- 账本查询页新增「试算平衡」页签：账簿 + 期间选择；视图切换「科目余额（实时）」（L6）/「试算记录」（列表 → 详情含 `summaryLines` 树与异常）；**无「发起试算」按钮**。
- 账户号：`SetupBooksTab` 账户列表、`LedgerDrilldown`（列表与面包屑）、`SubledgerAccounts` 与流水 / 批次抽屉、`JournalEntryDrawer` 显示 `accountNo`；两处账户列表加"账户号"精确搜索。建账户弹窗科目选择改为"编号 名称"、只列启用末级科目。
- `finance.ts` 路径、解码器（新字段）、错误码落点（15 → 23）；`finance.test.ts` 同步并补测试。
- 运行：`npm --prefix operations-console-web test` 与 `npm --prefix operations-console-web run build`（如 node_modules 缺失，在 worktree 内 `npm ci`，不改主工作区）。

## 不做

不改 Platform 代码与 `pageCapabilities.ts` 的能力集合（不新增按钮能力）。

## 来自 B 卡的合同变化

- `OwnerAccountQueryApi.listOwnerAccounts` 在 `balanceBucket` 与 `page` 之间新增可选参数 `accountNo`；BFF `FinanceSubledgerClientImpl` 按位置调用，需要同步传入。
- `OwnerAccountResponseBody` 新增 `accountNo`；`AccountPostingResponseBody.Entry` 新增 `accountNo`（同一个 Entry 也用于 `GET /account-postings/v1` 列表），BFF 对应模型要加字段。

## 来自 A 卡的合同（ledger-api 已定稿）

- `LedgerSetupQueryApi.listChartAccounts(chartCode, postingAllowed, accountStatus)`：新增第 3 个参数（ACTIVE / INACTIVE）。
- `LedgerSetupQueryApi.listChartAccountChanges(chartCode, chartAccountId, page, size)` → `PagingResponseBody<ChartAccountChangeResponseBody>`（L5）。
- `LedgerBookQueryApi.listAccounts(ledgerBookId, accountNo, accountCode, ownerType, ownerId, balanceBucket, page, size)`：`accountNo` 插在第 2 位。
- 新 `ChartOfAccountsCommandApi`（`/chart-of-accounts/v1`）：`createChartAccount(actor, session, chartCode, CreateChartAccountRequestBody)` → 201 `{chartAccountId}`；`updateChartAccount` / `disableChartAccount` / `enableChartAccount(actor, session, chartCode, chartAccountId[, UpdateChartAccountRequestBody])` → `ChartAccountResponseBody`。
- `TrialBalanceApi.listAccountBalances(ledgerBookId, periodId)` → `AccountBalanceListResponseBody{ledgerBookId, periodId, currency, lines[AccountBalanceLine]}`（L6）；`TrialBalanceDetailResponseBody.lines` 改为 `List<AccountBalanceLine>`，另有 `summaryLines`。
- 新字段：`ChartAccountResponseBody` 加 `accountNo`、`parentChartAccountId`、`level`、`accountStatus`；`AccountResponseBody` 加 `accountNo`、`accountName`；`JournalEntryDetailResponseBody.Line` 加 `accountNo`、`accountName`。
- `LedgerSetupErrorCode` 新增 8 个 Code，与 technique 2.3 一致。
- 以 worktree 中 `paytech/ledger/paytech-ledger-api` 的实际源码为准。
