# C 卡报告：Operations BFF 与 Web

工作位置：worktree `/Users/kenny/Desktop/Workspace/Code-MoR-chart2`（detached，基于 `cf39a1ee`，含 commercial 运营控制台改动；该改动在基线提交内，本卡未触碰）。未提交、未建分支、未 stash、未回滚、未 `mvn install`；未改 Platform 代码与 `pageCapabilities.ts`。

## 0. 开工说明（WRITE_API_EXAMPLE 第 1 节）

已读 `agent-docs/DEVELOPMENT_CONVENTIONS.md`（BFF 聚合层、分页、异常、幂等章节）、`agent-docs/JAVA_PROJECT_STRUCTURE.md` 3.3 BFF 例外、`agent-docs/examples/WRITE_API_EXAMPLE.md` 第 1、11 节，technique 2.1—2.3、2.5，A / B / R 报告与 worktree 中 `paytech-ledger-api`、`paytech-account-api` 源码。本卡全部为 BFF 单上游简单转发：Controller 直接委托本地 Client Port（`FinanceSetupClient`、`FinanceLedgerBookClient`、`FinanceSubledgerClient`），没有 Processor / Context，没有本地数据库、事务或事务外步骤；写命令只把会话 Account / Session 填入 `X-Gateway-Actor-Subject` / `X-Gateway-Session-Id` 后调用一次 ledger，业务校验、变更记录与锁全部归 ledger。

## 1. 改动文件

BFF app（`paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/`）：
- `configuration/FinanceClientConfiguration.java`：新增 `ChartOfAccountsCommandApi`、`TrialBalanceApi` 代理 Bean；错误码仍由 `LedgerSetupErrorCode.values()` 带入（已核对 23 个，含新增 8 个）。
- `ext/rest/FinanceSetupController.java`：L1—L5 五条路由；R15 增加 `accountStatus`（`ACTIVE|INACTIVE`）并加入白名单。
- `ext/rest/FinanceLedgerBookController.java`：L6、试算列表、试算详情三条 GET；R10 增加 `accountNo`（≤48）并加入白名单。不接入执行试算。
- `ext/rest/FinanceSubledgerController.java`：R19 增加 `accountNo`（`[A-Z0-9][A-Z0-9-]{0,47}`，与 account 合同一致）并加入白名单。
- `integration/FinanceSetupClient.java`、`impl/FinanceSetupClientImpl.java`：科目新增 / 修改 / 停用 / 启用 / 变更记录；`listChartAccounts` 第三参数。回执核对：新增须带正十进制主键，维护回执科目主键须与请求一致，变更记录在指定科目时每条须属于该科目。
- `integration/FinanceLedgerBookClient.java`、`impl/FinanceLedgerBookClientImpl.java`：`listAccounts` 插入 `accountNo`；科目余额、试算列表、试算详情。回执核对：L6 账簿与期间一致、`lines` 非空引用；试算详情账簿 / 期间 / 序号一致、三个列表非空引用；列表分页自洽。
- `integration/FinanceSubledgerClient.java`、`impl/FinanceSubledgerClientImpl.java`：按位置传 `accountNo`。
- `service/model/FinanceSetupModels.java`：`ChartAccountResponseBody` 加 `accountNo`、`parentChartAccountId`、`level`、`accountStatus`；新增 `CreateChartAccountRequestBody`、`CreateChartAccountResponseBody`、`UpdateChartAccountRequestBody`、`ChartAccountChangeResponseBody`。
- `service/model/FinanceLedgerBookModels.java`：账户与分录行加 `accountNo`、`accountName`；新增 `AccountBalanceLineResponseBody`、`AccountBalanceListResponseBody`、`TrialBalanceRunResponseBody`、`TrialBalanceFindingResponseBody`、`TrialBalanceDetailResponseBody`。
- `service/model/FinanceSubledgerModels.java`：Owner 账户与批次流水加 `accountNo`。

BFF test（`paytech-operations-console-bff-test/src/test/`）：
- `auto/FinanceSetupBrowserAutoTest.java`：边界清单扩到 15 行；只读权限不能执行 L1—L4、可查 L5 / R15；新增 11 个业务码透传；新增 `test_invalidChartMaintenanceParameter`；无效回执补 3 例。
- `auto/FinanceLedgerBookBrowserAutoTest.java`：边界清单扩到 9 行；新增 `test_chartHierarchyFieldsForwarded`、`test_trialBalanceRunNotExposed`；`CHART_ACCOUNT_INACTIVE` 透传；非法参数补 3 例；无效回执补 4 例。
- `auto/FinanceSubledgerBrowserAutoTest.java`：非法账户号本地拒绝。
- `auto/OperationsConsoleBffApplicationAutoTest.java`：精确路由清单加 8 条。
- `test/simulator/PlatformOperationsSimulator.java`：8 个新 Route Code 的 READ / MANAGE 权限。
- `resources/finance-setup-boundary.json`（R15 新字段与 Query、L1—L5）、`finance-ledger-boundary.json`（R10 / R13 新字段与 Query、L6、T1、T2）、`finance-subledger-boundary.json`（R19 / R21 新字段与 Query）。
- `paytech/operations-console-bff/README.md`：补一段现状。

Web（`operations-console-web/`）：
- `src/finance/finance.ts`：路径（科目维护、变更记录、科目余额、试算列表 / 详情）；类型与解码器（科目层级 / 状态、变更记录、科目余额、试算摘要 / 详情、各账户与流水的 `accountNo` / `accountName`）；错误码落点 15 → 23；科目树组装 / 搜索 / 可展开键、编号分级校验、维护请求体、变更字段对比、一级合计借贷校验；试算页签 URL 状态；`accountNo` 筛选键与校验。
- `src/finance/useFinanceData.ts`：`useChartAccounts` 改为筛选对象并支持 reloadToken；新增 `useChartAdoption`、`useLedgerBooks`、`useBookPeriods`。
- `src/finance/SetupChartsTab.tsx`（改写为科目树）、`src/finance/ChartAccountModals.tsx`（新：新增 / 编辑弹窗、停用 / 启用确认、变更记录抽屉）、`src/finance/TrialBalanceTab.tsx`（新）。
- `src/finance/FinanceModals.tsx`（导出 `CommandModal`；建账户科目选择）、`SetupBooksTab.tsx`、`LedgerDrilldown.tsx`、`JournalEntryDrawer.tsx`、`SubledgerAccounts.tsx`、`SubledgerEntries.tsx`、`SubledgerPostingDrawer.tsx`、`finance.css`。
- `src/pages/FinanceSetup.tsx`（传 `canManage`）、`FinanceLedger.tsx`（「账簿下钻 / 试算平衡」页签）、`FinanceSubledger.tsx`（批次抽屉进入流水带 `accountNo`）。
- `src/finance/finance.test.ts`：同步与新增测试；`README.md` 补一段现状。

## 2. AC 对应

| AC | 本卡实现 |
|---|---|
| AC-01 | Web 编号分级前置校验 `chartAccountNoError`（与 `ChartAccountNoRule` 同规则），BFF Body 格式校验；`CHART_ACCOUNT_NO_INVALID` / `_DUPLICATE` 落 accountNo 字段 |
| AC-04 / AC-05 | 编辑弹窗：已采用（提示条 + 编号 / 上级置灰并说明）或有下级时锁结构；代码、类别、方向、科目类型编辑时只读；`CHART_STRUCTURE_FROZEN`、`CHART_PARENT_INVALID` 落点 |
| AC-06 | 建账户只列启用末级科目（`postingAllowed=true&accountStatus=ACTIVE`）；`CHART_ACCOUNT_INACTIVE` 落建账户 accountCode；停用确认说明影响范围，`CHART_ACCOUNT_IN_USE` 显示在弹窗顶部 |
| AC-07 | 写操作仅 `OPERATIONS_FINANCE_SETUP_MANAGE` 可见（BFF 侧由 Gateway 绑定 MANAGE，测试验证只读账号 403 且不访问 ledger）；变更记录抽屉显示时间、操作人、类型、变更前 → 变更后 |
| AC-08 | 试算平衡页签：L6 实时余额与试算详情按科目树展示末级 + 汇总行，一级科目合计借 = 贷校验 |
| AC-11 | 账簿账户、账本下钻列表与面包屑、明细账账户、流水摘要、批次抽屉、分录详情显示账户号；账簿账户与明细账账户可按账户号精确筛选（BFF 白名单 + 透传） |
| AC-02 / 03 / 09 / 10 | 不属本卡（ledger / account / 部署） |

## 3. 实际执行的命令与结果

| 命令 | 结果 |
|---|---|
| `cd paytech && JAVA_HOME=jdk-25 ./mvnw -o -q -pl operations-console-bff/paytech-operations-console-bff-app -am -DskipTests compile` | 通过（原 3 处编译错误已消除） |
| `cd paytech && ./mvnw -o -pl operations-console-bff/paytech-operations-console-bff-test -am verify -Dtest='com/paytech/operations/console/bff/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false -Dpaytech.local-environment.dir=…/Code-MoR/local-environment` 第一次 | 1592 个测试，1 失败：`OperationsConsoleBffApplicationAutoTest.test_applicationReusesFoundationAndExposesExactOperationsRoutes`（精确路由清单未含新路由），已补清单 |
| 同上第二次 | **1592 个测试，0 失败，0 错误**，BUILD SUCCESS。其中 FinanceSetup 61、FinanceLedgerBook 39、FinanceSubledger 14、FinanceBalance 12 |
| `npm ci`（worktree 内 `operations-console-web`） | 成功 |
| `npx tsc --noEmit` | 通过 |
| `npm test`（worktree） | **1109 个测试，1109 通过，0 失败**；其中 `finance.test.ts` 24 个 |
| `npm run build` | 成功；Vite 提示单个 chunk > 500 kB（`jsx-runtime`，与本卡无关的既有警告） |

BFF 测试不需要 MySQL / Redis / RabbitMQ（Owner 与 Platform 均为 Loopback Simulator），所以未设置 `_chart` 库或 vhost；`-Dtest` 限定在 BFF 包，避免 `-am` 连带运行 Foundation 等上游模块测试。

## 4. 偏离设计之处

- BFF 试算详情模型 `TrialBalanceDetailResponseBody` 继承 `TrialBalanceRunResponseBody`（ledger-api 中两者是独立类、字段重复）；JSON 字段集合与 ledger 一致，严格转换测试通过。
- 「已采用提示条」的账簿数由 Web 读取全部账簿（R08，每页 200）后按 `chartCode` 计数，BFF 不新增聚合接口；ledger 仍以 `CHART_STRUCTURE_FROZEN` 兜底。
- 试算平衡的期间选择按账簿主体查唯一财务日历（R03）再读全部期间（R05），复用既有接口。
- 末级行「进入账户流水下钻」实现为进入账本下钻的账户级并按科目代码筛选（`?book=&accountCode=`）：同一科目在账簿下可能有多个账户（不同核算对象），无法唯一定位到某个账户的流水。
- 科目树默认全部展开、默认隐藏已停用科目（客户端过滤，不另发 `accountStatus` 请求）；`accountStatus` 筛选用于建账户弹窗。

## 5. 未完成 / 未运行

- 没有浏览器实测（阶段 5 验收）；没有运行 `npm --prefix requirements-site test`（本卡未改该目录）。
- 没有对真实 ledger / account 联调：BFF 只经 Simulator 验证；Web 只经单元测试与类型检查。
- Platform 注册块 91 由 D 卡负责；路由 Route Code、路径与 Query 白名单已与 `db/platform-support/registration-dml.sql` 逐条核对一致。

## 6. 浏览器验收要看的 UI 状态

1. 账务基础资料 → 科目表与币种：科目树按编号排序，汇总行加粗、可展开；「全部展开 / 全部折叠」；搜索 `partner` 只留命中路径；勾选「显示已停用」后出现灰色停用行与计数变化。
2. 科目表已被账簿采用时显示蓝色提示条「本科目表已被 N 个账簿采用…」；未采用时不显示。
3. 有维护权限：右上「新增科目」；操作菜单「编辑 / 新增下级（仅启用的汇总科目）/ 停用或启用 / 变更记录」。只读账号：无新增按钮，菜单按钮为「记录 ▾」只含「变更记录」。
4. 新增弹窗：选上级后编号自动带出上级前缀、类别锁定；不选上级时编号须 4 位；编号写错（如 5 位、类别首位不符）就地报错；服务端 `CHART_ACCOUNT_NO_DUPLICATE` / `CHART_ACCOUNT_CODE_DUPLICATE` / `CHART_PARENT_INVALID` 落到对应字段。
5. 编辑弹窗：代码、类别、方向、科目类型只读；已采用或有下级时编号与上级置灰并显示原因；只改名称可保存。
6. 停用确认文案「停用后不能新建账户、不能被新规则引用，已有账户不受影响」；被引用时弹窗顶部显示 `CHART_ACCOUNT_IN_USE` 中文原因；启用时上级已停用显示 `CHART_PARENT_INVALID`。
7. 变更记录抽屉：时间倒序，类型标签，只列有变化的字段「编号 220104 → 220105」。
8. 账簿与账户：账户列表首列「账户号」，筛选区「账户号」精确搜索；新建账户弹窗科目为「编号 名称（方向）」、只有启用末级科目。
9. 账本查询 → 账簿下钻：账户列表账户号列与筛选、面包屑以账户号开头、流水摘要显示账户号；分录详情抽屉「账户号 / 科目」列。
10. 账本查询 → 试算平衡：未选账簿 / 期间时空状态；选后「科目余额（实时）」树、表尾一级合计与「借 = 贷」绿色标签；点末级行跳到账簿下钻并按科目筛选；切「试算记录」列表，点行打开详情抽屉（摘要、科目树含汇总行、异常表）；页面没有「发起试算」按钮。
11. 明细账查询：筛选区「账户号」（自动转大写，格式错就地提示）；账户列表首列账户号；流水摘要与批次抽屉显示账户号；从批次抽屉进入流水时面包屑 / 摘要带账户号。
