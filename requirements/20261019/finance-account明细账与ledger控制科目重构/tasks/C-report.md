# C 线 · Operations BFF 与 Web 财务页面 · 交付报告（2026-10-19）

结论：BFF 新增明细账三个只读转发端点（R19–R21），R10–R13、R17–R18 按新合同适配；Web 新增「明细账查询」页面，账本、基础资料、分录抽屉与余额页按画板 04 / 05 / 07 / 08 / 09 / 11 / 13 / 14 / 15 修改。BFF reactor `verify`（含 `-am`，未排除任何测试）通过，Web `test` 与 `build` 通过。没有改动卡外路径。

## 1. 已读规范与设计

- `AGENTS.md`「接口开发必读与自查」
- `agent-docs/DEVELOPMENT_CONVENTIONS.md`（第 2–5 节，含 BFF 聚合层职责、公共分页、异常与枚举）
- `agent-docs/JAVA_PROJECT_STRUCTURE.md`（3.3 BFF 工程例外及 BFF 相关条目）
- `agent-docs/examples/WRITE_API_EXAMPLE.md` 第 11 节
- `agent-docs/UNIT_TEST_GUIDE.md`（目录与 BFF / 外部 DTO Mock 相关节）、`agent-docs/examples/UNIT_TEST_EXAMPLE.md`
- 需求：`tasks/00-common.md`、`tasks/T0-report.md`、`tasks/C-console.md`、`technique.md` 2.2 / 2.4、`assets/stage3/pencil/*.png`（9 张）
- 合同源码：account-api `OwnerAccountQueryApi`、`AccountPostingApi`、`response/OwnerAccount*`、`AccountPostingResponseBody`、`enums/{OwnerType,BalanceBucket,AccountEntityType,OwnerAccountType,BalanceQueryErrorCode,AccountPostingErrorCode,BalanceMovementErrorCode}`；ledger-api `LedgerOwnerType`、`AccountJournalLineResponseBody`、`JournalEntryDetailResponseBody`、`LedgerBookQueryApi`
- 未改模型 / SQL，未读 `DATABASE_DESIGN_GUIDE.md`；不涉及补偿事件，未读 `COMPENSATION_EVENT_GUIDE.md`。

## 2. 改动清单

### 2.1 BFF（`paytech/operations-console-bff`）

| 类型 | 文件 | 内容 |
|---|---|---|
| 新增 | `ext/rest/FinanceSubledgerController` | R19 `GET /finance/owner-accounts/v1`、R20 `GET /finance/owner-accounts/v1/{accountId}/entries`、R21 `GET /finance/account-postings/v1/{postingId}`；Query 白名单按 technique 2.2，`PagingRequestBody.of` 补默认分页；简单转发，无 Processor |
| 新增 | `integration/FinanceSubledgerClient` + `impl/FinanceSubledgerClientImpl` | 本地只读 Port；分页回执自洽校验，批次主键一致且 `entries` 不为空引用，否则 502 |
| 新增 | `service/model/FinanceSubledgerModels` | 页面模型与 account 合同一一对应（Money、Instant）；三个 Query 正则常量 `ENTITY_TYPE_PATTERN`、`OWNER_TYPE_PATTERN`、`BALANCE_BUCKET_PATTERN` |
| 修改 | `configuration/FinanceClientConfiguration` | account 工厂增加 `OwnerAccountQueryApi`、`AccountPostingApi` 代理；错误码登记 `BalanceQueryErrorCode` + `AccountPostingErrorCode` + `BalanceMovementErrorCode.ACCOUNT_NOT_FOUND`（R20 账户不存在）；类注释去掉旧 `LEDGER_BOOK_NOT_FOUND` 冲突说明 |
| 修改 | `service/model/FinanceLedgerBookModels` | 新增 `LEDGER_OWNER_TYPE_PATTERN`（五值）；新建账户 `ownerType` 引用它、余额桶注释改为一律 NONE；流水行与详情新增 `accountPostingId`、`accountPostingSequence`，详情行新增 `accountEntryId`，`originType` 注释加 ACCOUNT_POSTING |
| 修改 | `ext/rest/FinanceLedgerBookController` | R10 `ownerType` 引用 `LEDGER_OWNER_TYPE_PATTERN` |
| 修改 | `integration/impl/FinanceBalanceClientImpl` | 类注释改为 `OWNER_ACCOUNT_NOT_FOUND` |
| 修改 | `README.md` | 错误码登记说明；明细账三个端点、Route Code、白名单与 `/finance` 端点总数 21 |
| 新增（测试） | `auto/FinanceSubledgerBrowserAutoTest`、`resources/finance-subledger-boundary.json` | 13 例：转发 ×3、授权拒绝 ×3、分页默认值与回显、`ACCOUNT_NOT_FOUND` / `POSTING_NOT_FOUND` 原样 400、正则与 account 枚举一致、Query 白名单与格式本地拒绝（8 种）、上游无效响应 502（3 种）、超时 504 |
| 修改（测试） | `auto/FinanceLedgerBookBrowserAutoTest`、`resources/finance-ledger-boundary.json` | 夹具改控制账户（OPERATING_ENTITY AE001 / NONE）、CHANNEL_ACCOUNT 新建、ACCOUNT_POSTING 流水与分录；新增 ACCOUNT_POSTING 字段透传、`LEDGER_OWNER_TYPE_PATTERN` 与 `LedgerOwnerType` 一致、CLIENT / LOCAL_PARTNER 在 Query（400）与 Body（422）被本地拒绝 |
| 修改（测试） | `auto/FinanceBalanceBrowserAutoTest` | `test_noDataFound` 改 `OWNER_ACCOUNT_NOT_FOUND`，去掉与 ledger 同名 Code 的对比 |
| 修改（测试） | `auto/OperationsAccountUnavailableAutoTest` | 新增明细账三路由连接拒绝 503 参数化用例 |
| 修改（测试） | `auto/OperationsConsoleBffApplicationAutoTest` | 精确路由集合 +3 |
| 修改（测试） | `auto/OperationsHttpBodyNamingAutoTest` | 命名检查加入 `FinanceSubledgerController`、`OwnerAccountQueryApi`、`AccountPostingApi` |
| 修改（测试） | `test/simulator/PlatformOperationsSimulator` | `FINANCE_ROUTE_PERMISSIONS` +3（均为 READ） |
| 修改（测试） | `test/simulator/AccountOwnerSimulator`、`resources/finance-setup-boundary.json` | Simulator 注释写明承载明细账 DTO（通用实现，无代码变化）；科目名示例改「Client 应付（控制科目）」 |

`application.yml` 未改：新接口复用 `operations.account-base-url`，没有新配置项。

### 2.2 Web（`operations-console-web`）

| 类型 | 文件 | 内容 |
|---|---|---|
| 修改 | `src/finance/finance.ts` | `ownerTypes` 改五值；删除 `bucketedAccountCodes`、`usesPayableBuckets`、`bucketOptions`，`accountBody` 余额桶固定 NONE；`originTypes` 加 ACCOUNT_POSTING，流水 / 详情 / 行解码 `accountPostingId`、`accountPostingSequence`、`accountEntryId`（ACCOUNT_POSTING 必须带批次，其他来源不得带）；`balanceErrorTargets` 改 `OWNER_ACCOUNT_NOT_FOUND → ownerId` 并加 `ownerAccountNotFoundMessage`；新增明细账路径、类型（`OwnerAccount`、`AccountEntry`、`AccountPosting`）、解码器、`subledgerErrorTargets`、查询条件与 URL 状态（`readSubledgerQuery`、`subledgerLevelSearch`、`subledgerQuerySearch`、`subledgerPostingLink`）、`journalLineSourceMeta`、`entrySourceLabel`、`postingTotals` |
| 新增 | `src/pages/FinanceSubledger.tsx` | 页面编排：条件表单 › 账户列表 › 流水，批次抽屉；面包屑逐级返回 |
| 新增 | `src/finance/SubledgerAccounts.tsx` | 条件表单（主体三项必填一行 + 四个筛选一行）与 Owner 账户列表 |
| 新增 | `src/finance/SubledgerEntries.tsx` | 账户摘要与明细流水表 |
| 新增 | `src/finance/SubledgerPostingDrawer.tsx` | 入账批次抽屉：批次头、来源事实或桶移动、流水行、合计，高亮跳转来的流水，行内「账户流水」进入所属账户 |
| 修改 | `src/finance/FinanceModals.tsx` | 新建账户：Owner 类型五值、余额桶固定 NONE 且禁用（保留字段承接 `ACCOUNT_ALREADY_EXISTS`），提示文案按画板 05 |
| 修改 | `src/finance/JournalEntryDrawer.tsx` | 来源事件键标注「account 批次 ID」；新增「account 批次」行；控制科目行「流水 …后 4 位 ↗」 |
| 修改 | `src/finance/LedgerDrilldown.tsx`、`SetupBooksTab.tsx` | 来源第二行「批次 …后 4 位 · 序号 n」；说明文按画板 04 / 07 / 08 |
| 修改 | `src/pages/FinanceBalances.tsx` | 无账户错误挂 Owner ID，文案带 Owner 类型与范围；空状态文案按画板 11 |
| 修改 | `src/finance/finance.css` | 主体三项一行布局、抽屉流水链接与高亮行 |
| 修改 | `src/navigation/pageCapabilities.ts`、`ConsolePage.tsx` | +1 `OPERATIONS_FINANCE_SUBLEDGER → /finance/subledger`（排在余额查询之后，无动作） |
| 修改（测试） | `src/configuration/configurationModel.test.ts` | 页面计数 33 → 34 |
| 修改（测试） | `src/finance/finance.test.ts` | 按新规则改写，新增明细账解码、查询条件、URL 状态、页面接线 4 组用例 |
| 修改 | `README.md` | 新增「财务明细账查询与控制科目（2026-10-19）」一节 |

## 3. 接口前后对照

| 路由 | 之前 | 之后 |
|---|---|---|
| R10 `GET /finance/ledger-books/v1/{id}/accounts` | `ownerType` 七值（含 CLIENT、LOCAL_PARTNER） | 五值，正则常量与 `LedgerOwnerType` 由测试逐值核对 |
| R11 `POST .../accounts` | Body `ownerType` 七值，余额桶四值 | `ownerType` 五值；余额桶正则未改（ledger 以 `ACCOUNT_IDENTITY_INVALID` 拒绝非 NONE），Web 固定提交 NONE |
| R12 / R13 | 无批次字段 | 透传 `accountPostingId`、`accountPostingSequence`，R13 行透传 `accountEntryId` |
| R17 / R18 | 无账簿 400 `LEDGER_BOOK_NOT_FOUND` | 无账户 400 `OWNER_ACCOUNT_NOT_FOUND` |
| R19 新增 | — | `GET /finance/owner-accounts/v1`，Route `operations-finance-owner-accounts`，READ；Query `entityType*, entityId*, currency*, ownerType, ownerId, accountCode, balanceBucket, page, size` |
| R20 新增 | — | `GET /finance/owner-accounts/v1/{accountId}/entries`，Route `operations-finance-owner-account-entries`，READ；Query `page, size`；400 `ACCOUNT_NOT_FOUND` |
| R21 新增 | — | `GET /finance/account-postings/v1/{postingId}`，Route `operations-finance-account-posting`，READ；无 Query；400 `POSTING_NOT_FOUND` |

跨线一致性：Web `financePaths` 三个新路径 = BFF `@GetMapping` = technique 2.2 路由表 = D 卡登记的 Gateway 路径（去掉 `/api/operations`）。Web 对 R19 只发送白名单内的键，R20 只发送 `page`、`size`，R21 不带 Query。Web 错误码表覆盖 account 新 Code `OWNER_ACCOUNT_NOT_FOUND`、`ACCOUNT_NOT_FOUND`、`POSTING_NOT_FOUND`；`STREAM_NOT_FOUND` 只属于按流重建，控制台不调用。

## 4. 页面与画板对照

| 画板 | 实现 | 差异 |
|---|---|---|
| 04 账簿与账户 | 账户列表组件共用；说明文改控制科目口径；科目副标题取科目表名称 | 「（控制科目）」字样来自 ledger 科目表名称，不在前端拼接 |
| 05 新建账户错误 | Owner 类型五值，余额桶固定 NONE 且禁用，`ACCOUNT_ALREADY_EXISTS` 仍挂余额桶字段；提示文案按画板 | 无 |
| 07 账本 · 账户列表 | 核算对象类型筛选默认「全部」（五值）；说明文按画板 | 无 |
| 08 账本 · 账户流水 | 来源列「ACCOUNT_POSTING · account」+「批次 …2018 · 序号 18」；说明文追加 ACCOUNT_POSTING 来源说明 | 无 |
| 09 分录抽屉 | 来源、来源事件键「（account 批次 ID）」、新增「account 批次」行；控制科目行「流水 …3001 ↗」 | 「account 批次」行显示批次 ID、序号、账簿与来自 account 的行数；分片号不在 ledger 合同中，未显示 |
| 11 余额未找到 | 错误挂 Owner ID 并高亮：「该 Local Partner 在 SG002 / USD 下没有账户（OWNER_ACCOUNT_NOT_FOUND）」；空状态文案同步 | 无 |
| 13 明细账 · 账户列表 | 导航新增「明细账查询」；主体三项必填一行、四个筛选一行；表格八列；BANK_CASH 等六个科目带中文名 | BANK_CASH 副标题不含「可承诺」 |
| 14 明细账 · 账户流水 | 面包屑、摘要、表：批次序号（打开批次）、入账时间、来源类型标签、行号、方向、金额、冲回、事实类型、来源 | 见第 6 节第 2、3 项：无入账时间筛选，来源第二行不显示事件键 |
| 15 入账批次抽屉 | 批次头（含发布时间）、来源事实（ID、类型、版本、替代事实、应用状态、服务费财务主体）或桶移动、流水行与合计 | 合计标签显示「n 行流水」 |

空状态：未查询（「选择主体类型并填写主体标识与币种后查询 Owner 账户」）、无账户、无流水、SKIPPED 批次无流水均复用现有 `Empty` 组件；加载与错误复用 `LoadFailure` 与错误通知。

分录行跳转方式：选「先经 R21 取批次再定位」。链接只带 `posting` 与 `entry`，打开明细账页的批次抽屉并高亮该流水；抽屉行内「账户流水」用批次的主体、币种与该行账户进入流水。这样不需要 ledger 合同提供 account 账户主键，也不需要按 `entryId` 翻页查找。

## 5. 写接口示例第 11 节自查

本卡只有只读转发与页面，没有写接口、Processor、Context、Service、Repository、事务或锁。

- **API 与模型：** 页面模型为 class + 中文字段注释；Query 用 `@Pattern` 与白名单 `requireQueryKeys` 在入口校验，枚举正则常量由 auto 测试逐值对照 api 枚举。
- **Controller / Client：** Controller 只做协议适配，直接委托一个本地 Client Port（BFF 简单转发，按规范不建空 Processor）；Client 每入口只调用一次 account，不重试。
- **Processor / Context / Service / 事务 / 锁 / 原请求比较 / 业务防重：** 不适用（只读转发，BFF 无数据库）。
- **View / DataObject 边界：** BFF 只用 account-api 的 ResponseBody 经严格转换得到本地页面模型，没有 DataObject。
- **无多余抽象：** `FinanceRequestConfiguration` 的模型容器集合未加 `FinanceSubledgerModels`，因为三个端点都没有请求体；`application.yml` 无新配置。
- **测试：** 真实 BFF 入站 → Platform Simulator（PLT-I02）→ account Loopback Simulator，无 Mockito / MockBean；Web 用 Node 测试覆盖解码、条件、URL 状态与页面接线。未做浏览器手工走查（需要真实 account / ledger 与 Gateway 路由，见第 7 节）。

## 6. 偏离设计与说明

1. **枚举正则不能直接由枚举生成：** Bean Validation 注解需要编译期常量，所以正则写成常量，并用 `test_ownerTypePatternMatchesLedgerContract`、`test_queryPatternsMatchAccountContract` 以枚举 `getCode()` 拼接后逐值断言相等。
2. **画板 14 的入账时间筛选未实现：** R20 白名单只有 `page, size`（technique 2.2），account `listOwnerAccountEntries` 也没有时间参数。
3. **画板 14 的「事实类型」列已于追加轮实现（见第 9 节）；来源事件键仍未显示：** `OwnerAccountEntryResponseBody` 没有事件键；来源列显示「事实 ID · v版本」与「事实行 n」，桶移动显示移动来源类型与批次缩略主键。
4. **画板 15 的「发布时间」已于追加轮实现（见第 9 节）：** 未发布时显示「尚未发布」。
5. **BFF 对上游多余字段的检测：** account 代理按 account-api DTO 反序列化，上游多出的 JSON 字段在到达 BFF 转换前已被丢弃，因此「上游多字段 → 502」只能检测 api DTO 与 BFF 模型之间的漂移，测试中未写该场景。

## 7. 测试

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
# 按 00-common 加载 local-environment/.env.local 中的 Redis 密码
cd paytech && ./mvnw -B -o verify -pl operations-console-bff/paytech-operations-console-bff-test -am
npm --prefix operations-console-web test
npm --prefix operations-console-web run build
```

| 模块 | 结果 |
|---|---|
| paytech-operations-console-bff-test | 1496 通过 |
| 其中 `FinanceSubledgerBrowserAutoTest` | 13 通过（新增） |
| 其中 `FinanceLedgerBookBrowserAutoTest` | 30 通过（+4） |
| 其中 `FinanceBalanceBrowserAutoTest` / `FinanceSetupBrowserAutoTest` | 12 / 35 通过 |
| 其中 `OperationsAccountUnavailableAutoTest` | 4 通过（+3） |
| 其中 `OperationsConsoleBffApplicationAutoTest` / `OperationsHttpBodyNamingAutoTest` | 8 / 27 通过 |
| `-am` Foundation 等上游模块 | 全部通过，本次未排除 `PartitionQueueBaselineAutoTest` 也通过 |
| operations-console-web `test` | 1086 通过 |
| operations-console-web `build` | `tsc --noEmit` 与 `vite build` 通过；仅有原有的单 chunk 大于 500 kB 提示 |

第一次运行有 3 例失败，均为测试本身写错并已修正：新建账户 Body 字段校验返回 422 而非 400；上游多字段场景不成立（第 6 节第 5 项）。

## 8. 未完成项与待主会话确认

- 未做浏览器实际走查：本地没有按新合同运行的 account / ledger 与 Gateway 路由（A / B / D 线并行中）。建议 A、B、D 合入后，在联调环境按画板 13–15 走一遍。
- Gateway Route 与 Console Resource 的注册（`operations-finance-owner-accounts` 等三条路由及 `OPERATIONS_FINANCE_SUBLEDGER`）属于 D 线；BFF 与 Web 中的 Route Code、路径、权限与 D 卡一致。
- 第 6 节第 2–4 项是否需要 account 合同补字段或补查询参数，由主会话决定；本卡按现有合同实现。

## 9. 追加轮：事实类型与发布时间（2026-10-19）

A 线在 `OwnerAccountEntryResponseBody` 增加 `factType`（桶移动为空），在 `AccountPostingResponseBody` 增加 `publishedTime`（未发布为空），本卡同步透传与展示。入账时间筛选仍不实现。

| 类型 | 文件 | 内容 |
|---|---|---|
| 修改 | BFF `service/model/FinanceSubledgerModels` | 流水模型加 `factType`，批次模型在 `postedTime` 后加 `publishedTime`（Instant） |
| 修改（测试） | BFF `resources/finance-subledger-boundary.json` | R20 两条事实流水 `factType = PAYMENT`、桶移动流水为 null；R21 `publishedTime = 2026-10-18T10:21:08.500Z`。account Simulator 是通用回放实现，DTO 即该清单 |
| 修改（测试） | BFF `auto/FinanceSubledgerBrowserAutoTest` | 新增 `test_factTypeAndPublishedTimeForwarded`：事实类型原样透传、桶移动为 null，发布时间原样透传，未发布批次 `publishedTime = null` 时 200 且为 null |
| 修改 | Web `src/finance/finance.ts` | `AccountEntry.factType`、`AccountPosting.publishedTime`；事实流水 / 批次必须带事实类型，桶移动不得带；发布时间可空、非空时须为合法时间 |
| 修改 | Web `src/finance/SubledgerEntries.tsx` | 画板 14「事实类型」列（桶移动显示 —） |
| 修改 | Web `src/finance/SubledgerPostingDrawer.tsx` | 画板 15 批次头「发布时间」，未发布显示「尚未发布」 |
| 修改（测试） | Web `src/finance/finance.test.ts` | 事实类型与发布时间的解码、缺失视为 null、互斥规则与页面接线断言 |

验证：

| 命令 | 结果 |
|---|---|
| `./mvnw -B -o verify -pl operations-console-bff/paytech-operations-console-bff-test -am` | BUILD SUCCESS；bff-test 1497 通过（`FinanceSubledgerBrowserAutoTest` 14） |
| `npm --prefix operations-console-web test` | 1086 通过 |
| `npm --prefix operations-console-web run build` | 通过 |

## 10. 阶段 5 缺陷 C-1 修复：从批次抽屉进入流水时摘要缺方向与净余额（2026-10-19）

问题：经批次抽屉「账户流水」（含总账分录行「流水 …↗」链路）进入流水页时，下钻状态只有科目、Owner 与余额桶，画板 14 摘要缺「方向」与「当前净余额」。

修复（只改 Web）：

| 类型 | 文件 | 内容 |
|---|---|---|
| 修改 | `src/finance/finance.ts` | `ownerAccountLookupPath(scope, account)`：账户缺净余额且已知科目、Owner 类型、Owner ID、余额桶时，生成 R19 精确筛选路径（主体 + 币种 + 四项筛选，page 1 / size 10）；列表下钻（已有净余额）或直接粘贴 URL（只有主键）返回 null，不请求。`decodeOwnerAccountLookup`：必须恰好一行、总数 1 且账户主键一致，否则按无效响应处理 |
| 修改 | `src/pages/FinanceSubledger.tsx` | `useAccountSummary`：仅在上述路径非空时调用 R19，补齐后用完整账户渲染摘要；失败时保留已有字段，流水列表照常加载。从账户列表下钻的路径不变 |
| 修改（测试） | `src/finance/finance.test.ts` | 新增用例：路径与 Query 键（均在 R19 白名单内）、列表下钻 / 只有主键 / 无主体时不请求、恰好一行与同一账户校验、页面接线 |

验证：`npm --prefix operations-console-web test` 1087 通过；`npm --prefix operations-console-web run build` 通过。未改 BFF 与其他路径。
