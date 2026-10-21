# D 控制台线交付报告（阶段 4）

> 范围：technique §2.9 D（BFF、七个页面、Platform 块 123 注册与激活脚本、Vault 策略）。未提交、未推送、未部署、未对任何库执行 SQL（只做了本机 `mor_platform` / `mor_vault` 的只读 SELECT 核对 ID 占用）；未调用 Superpowers skill；共享文件只做局部增改。

## 1. 已读依据

AGENTS.md；agent-docs：APPLICATION_ARCHITECTURE.md（§3 BFF、§4 Operations Console）、DEVELOPMENT_CONVENTIONS.md（全文）、JAVA_PROJECT_STRUCTURE.md（§3.3、§4、§5.5）、UNIT_TEST_GUIDE.md（§2、§5、§8–§13）、DATABASE_DESIGN_GUIDE.md（§5、§13–§15）；本需求 requirement.md（AC-14、AC-V1–V6）、technique.md（§1.2、§2.1 D-01、§2.3、§2.5、§2.6、§2.7、§2.10 I-05）、tasks/T0-report.md、E1-report.md；design-discuss/reconciliation/design.md §4 F3；reconciliation-api 全部接口与 DTO。先例：财务人工调整 BFF（FinanceClientConfiguration / FinanceRequestConfiguration / FinanceManualAdjustment* / FinanceContractConverter / FinanceBrowserAutoTestBase）、渠道成本（ChannelConnectorClientConfiguration、ChannelCostVersionComparisonProcessor）、Web 调整页与 finance 目录、navigation；注册先例 `20261020/finance-人工调整与计费事实/db/platform-support/*`、`20261020/channel-connector-…/db/vault/dml.sql` 与 `platform-support-initial-data.sql` 块 92 / 120–122。

## 2. 文件

**BFF（paytech/operations-console-bff）**
- 新增：`configuration/ReconciliationClientConfiguration`；`integration/Reconciliation{Clearing,Cash,Statement,Bank,Exception}Client` + `impl/*Impl`；`ext/rest/Reconciliation{Clearing,Cash,Statement,Bank,Exception}Controller`；`service/model/Reconciliation{Clearing,Cash,Statement,Bank,Exception}Models`（与 reconciliation-api DTO 逐字段对应，由脚本从 api 源码生成后手加两处聚合字段）；`service/processor/ReconClearingAccountDetailProcessor`、`ReconExceptionDetailProcessor` 及其 request。
- 修改：两个 pom（依赖 `paytech-reconciliation-api`）；`application.yml`（`operations.reconciliation-base-url`，默认 `http://127.0.0.1:18115`；metadata-only 前缀 `/finance/reconciliation/v1/cash-accounts`，落实 I-05）；`FinanceRequestConfiguration`（对账模型纳入严格请求边界，两个追加行请求体 65536，其余 8192）；`FinanceBillingFactModels.BillingFactRefResponseBody` 加 `externalReferenceType`、`externalTransactionId`（见第 5 节）；README。
- 测试：新增 `ReconciliationBrowserAutoTest`（90 例）、`ReconciliationOwnerSimulator`、`finance-reconciliation-boundary.json`（38 行）；修改 `FinanceBrowserAutoTestBase`（重置 / 校验 reconciliation Simulator）、`OperationsBffTestConfiguration`、`OperationsBffHttpTestBase`、`PlatformOperationsSimulator`（对账路由 → 权限 123101/102/103）、`OperationsConsoleBffApplicationAutoTest`（路由清单 + metadata 前缀）、`OperationsHttpBodyNamingAutoTest`、`finance-adjustment-boundary.json`（引用补两键）。

**Web（operations-console-web）**
- 新增 `src/reconciliation/`：`reconModel.ts`（路径、类型、解码、标签、F3 归类码表、十进制运算、人工匹配合计、CSV 解析、UTF-8 / SHA-256、分段）、`reconModel.test.ts`（13 例）、`useReconCommand.ts`、`useReconData.ts`、`ReconParts.tsx`、`ImportDrawer.tsx`、`recon.css`；`src/pages/Recon{ClearingAccounts,CashAccounts,Statements,PlatformTxns,Bank,Exceptions,Summary}.tsx`。
- 修改：`navigation/pageCapabilities.ts`（7 页，按钮挂在对账异常页）、`navigation/ConsolePage.tsx`（懒加载）、`configuration/configurationModel.test.ts`（页数 48 → 55）、`finance/finance.test.ts`（财务七页断言排除 RECON_）。没有改 finance.css 等其他会话在改的文件。

**Platform / Vault / 需求目录**
- `platform-support-initial-data.sql` 末尾追加块 123；`console-resource-capabilities.json` 加同样 7 个组件。
- `local-environment/vault/policy.local.json`、`test-environment/vault/policy.json`：数据类型 `CASH_ACCOUNT_NUMBER_V1`（REFERENCE）+ `reconciliation-service` PROTECT `RECON_CASH_ACCOUNT`。
- 需求目录：`db/platform-support/{registration-dml.sql, activation-local.sql, test-activate.sql, test-deactivate.sql}`、`db/vault/dml.sql`（数据类型 + 规则 + 变更记录，ID 9100000000000730）。

## 3. 路由与注册（块 123，BFF 路径 = `/finance/reconciliation/v1` + reconciliation 路径；Gateway 外部路径再加 `/api/operations`）

| ID 尾号 | Route Code（前缀 `operations-finance-recon-`） | 方法与路径 | 权限 | Body 上限 |
|---|---|---|---|---|
| 401 | clearing-account-list | GET /clearing-accounts | READ | 0 |
| 402 | clearing-account-unassigned-mids | GET /clearing-accounts/unassigned-mids | READ | 0 |
| 403 | clearing-account-detail | GET /clearing-accounts/{id}（BFF 聚合） | READ | 0 |
| 404–408 | clearing-account-create / -settings / -mid-add / -mid-remove / -status | POST / PUT …/settings / POST …/mids / DELETE …/mids/{midId} / POST …/status | OPERATE | 8192（DELETE 0） |
| 409–410 | cash-account-list / -detail | GET /cash-accounts、/{id} | READ | 0 |
| 411–412 | cash-account-create / -closure | POST /cash-accounts、/{id}/closure | OPERATE | 8192 / 0 |
| 413 | cash-account-approval | POST /cash-accounts/{id}/approval | APPROVE | 8192 |
| 414–416 | channel-statement-list / -detail / -lines | GET /channel-statements、/{id}、/{id}/lines | READ | 0 |
| 417–419 | channel-statement-start / -lines-append / -complete | POST /channel-statements、/{id}/lines、/{id}/completion | OPERATE | 65536 |
| 420 | channel-statement-void | POST /channel-statements/{id}/void | OPERATE | 0 |
| 421 | platform-txn-list | GET /platform-txns | READ | 0 |
| 422–424 | bank-statement-list / -lines / bank-statement-line-list | GET /bank-statements、/{id}/lines、/bank-statement-lines | READ | 0 |
| 425–427 | bank-statement-start / -lines-append / -complete | POST /bank-statements、/{id}/lines、/{id}/completion | OPERATE | 65536 |
| 428–429 | expected-bank-movement-list / bank-match-list | GET /expected-bank-movements、/bank-matches | READ | 0 |
| 430–432 | bank-match-confirm / -reject / -create | POST /bank-matches/{id}/confirmation、/rejection、/bank-matches | OPERATE | 0 / 0 / 8192 |
| 433–434 | exception-list / -detail | GET /recon-exceptions、/{id}（BFF 聚合） | READ | 0 |
| 435–436 | exception-open / resolution-submit | POST /recon-exceptions、/{id}/resolutions | OPERATE | 8192 |
| 437 | resolution-approval | POST /recon-resolutions/{id}/approval | APPROVE | 8192 |
| 438 | summary | GET /summary | READ | 0 |

- 权限 123101 `FINANCE_RECONCILIATION_READ`、123102 `_OPERATE`、123103 `_APPROVE`；PLATFORM_ADMIN 授权 123201–203（REVOKED）。资源 123301 分组「对账」（财务菜单下，sort 80）、123302–308 七页、123309 / 123310 按钮 `OPERATIONS_FINANCE_RECON_OPERATE` / `_APPROVE`（挂在对账异常页；`hasAction` 全局判断，七页都可用）。全部 DISABLED。
- 共 512 行：Header 160（1235xx–1236xx）、Query 70（1230xx）、限流 228（1237xx–1239xx，每路由 6 行；两个追加行路由按主体 300 次 / 分钟，共享 600，2 万行 = 200 段不会被限流）。
- 本机 `mor_platform` 块 123 七张表 0 行、无同名权限 / 资源 / 路由；mor-test 未查（阶段 6 前查）。

## 4. 验证

| 命令 | 结果 |
|---|---|
| `./mvnw -B -o -f operations-console-bff/pom.xml -pl paytech-operations-console-bff-test test`（JDK 25） | 首轮 1939 例，只有 `FinanceBillingFactBrowserAutoTest` 5 例失败（第 5 节第 1 条，已修复后该类 27 例通过）；修复后整模块复跑：1939 例全部通过，BUILD SUCCESS |
| `ReconciliationBrowserAutoTest`、`OperationsConsoleBffApplicationAutoTest`、`OperationsHttpBodyNamingAutoTest` | 143 例全部通过 |
| `npm test`（operations-console-web，node --test） | 1170 例全部通过（含 reconModel 13 例、financeWorkflow 能力清单与 Platform JSON 一致） |
| `tsc --noEmit`；`vite build --outDir <scratch>` | 通过；七页各自独立 chunk |
| `./mvnw -o -f platform-support/pom.xml -pl paytech-platform-support-access-control -am compile` + 能力清单唯一性检查（46 组件 / 60 动作无重复） | 通过 |
| `node --test scripts/pdmaner/platform-support-schema.test.mjs` | 11 例 4 失败，均为既有问题（Schema 表数、Client API 127 行比对等，与块 123 无关；E1 报告已列 Platform Support 既有失败） |

BFF 测试覆盖：38 条路由的方法、路径、Query、Body 与操作人 Header 原样转发（不转发 Authorization / Cookie / Idempotency-Key）；授权拒绝不访问 Owner；只有 OPERATE 不能复核、只有 APPROVE 不能提交、只有 READ 不能写（PLT-I02 按 Route Code 判定，BFF 不另判，AC-14）；清算账户详情补 MID 外部编码与状态、无 MID 不调 channel-connector、MID 读取失败整页 503；异常详情补银行 / 清算账户标识；业务拒绝 detail 原样到达浏览器（AC-V6）；本地参数与 Body 拒绝；100 行追加 > 8192 字节正常、> 65536 字节 413；银行账号明文不出现在日志（I-05）；上游回执主键不符、列表元素不属于请求对象、多选匹配条数不符、缺主键 → 502。

Web 测试覆盖：路径；能力清单；CSV 引号 / 逗号 / CRLF；S1 账单解析与 rawAttributes；结构错误逐行原因；银行对账单负期初、末四位不符、零金额；严格 UTF-8 与 BOM、SHA-256；分段 ≤ 100 行且 ≤ 60 KB；十进制带符号合计与比较；人工多选匹配（同向正、反向负、剩余、超出不可提交、超过预期剩余、合计为负不可提交）；F3 归类码交集并与 `ReconClassification` 枚举一致；解码主键核对。

## 5. 偏差与遗留

1. **billing 合同变更波及 BFF（已修）：** E2 线给 billing-api `BillingFactRefResponseBody` 加了两列，BFF 严格转换因此对计费事实页返回 502。已在 BFF 模型补两键并更新先例夹具；Web 计费事实解码本来就宽松，未改。
2. **跟进 A 线合同：** `PlatformTxnApi.listPlatformTxns` 已加 `statementId`（I-02），BFF、注册 Query 白名单与测试同步。
3. **CSV 文件格式由本线先定（E 线写 `docs/formats/` 须一致）：** 两种文件都是「第 1、2 行以 `#HEADER` 开头的头部列名行与取值行 → 第 3 行明细列名 → 明细」；列名按 technique §2.5，渠道账单其他列进 `rawAttributes`（空值不带）。浏览器只做结构与格式校验，恒等式、余额连续性、重复流水由 reconciliation 完成导入时校验并返回逐行原因。
4. **异常详情的「来源对象摘要」reconciliation 已经返回**，BFF 不再逐类回读，只补所属清算账户编码 / 渠道或银行账户名称 / 末四位。
5. **完成导入路由 Body 上限按任务写 65536**（该请求实际无 Body，BFF 收到 Body 即 400）；其余无 Body 命令为 0。
6. **菜单可见性：** 分组挂在「财务」下，父菜单绑定 `FINANCE_ACCOUNTING_READ`，只有对账权限的角色还需该读权限才能看到财务菜单树（与渠道成本先例相同的父菜单约束）。
7. **未做（部署线 / 阶段 6–7）：** mor-test `compose.applications.yaml` 与本机运行脚本给 Operations BFF 加 `OPERATIONS_RECONCILIATION_BASE_URL`；mor-test 块 123 占用核对；Vault DML 与注册 / 激活 SQL 执行。
8. **构建环境：** 全仓 reactor 因 A 线进行中的 `reconciliation-core/pom.xml`（vault-client 缺版本）无法读取 POM，本线用 `-f operations-console-bff/pom.xml` 等子聚合构建；为此把 channel-connector、ledger、account、billing、commercial、platform-support、risk-decision、compliance、client-master 的 api 模块按当前源码 install 到 `~/.m3`（未跑这些模块的测试）。
9. 浏览器联调（AC-V1–V6 实际页面走查）需 reconciliation 服务实现后在阶段 5 进行；本线未启动任何服务。
