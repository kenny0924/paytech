# 阶段 3：方案、接口与数据设计

> 状态：已确认（2026-10-18，含补充的只读"科目表与币种"标签页）。阶段 2 结论见 [analysis.md](./analysis.md)，范围与验收见 [requirement.md](./requirement.md)。本文使用"本次方案"口径，所有 SQL 为待执行文件。

**规模（本次方案）：** 涉及 5 个目录（ledger、account、Operations BFF、Operations Web、Platform 注册数据）；ledger 新增 1 个只读接口类 6 个端点、修改 6 个命令端点签名（加 2 个 Header）、新增 1 个错误码枚举（15 个 Code）；account 新增 1 个错误码枚举（1 个 Code）、修改 1 处抛错；BFF 转发 18 个接口（与 Owner 同一业务 Method + Path 不重复计数）；新增 0 张表、0 个字段、0 个索引；注册 DML 226 行（权限 2、角色授权 2、Console Resource 5、Gateway 路由 18、路由 Header 60、Query 参数 31、限流 108）；业务 DML 0 行；新增 3 个功能页面（账务基础资料为四标签页，第四页只读）。

## 1. 业务与架构

```text
Browser ─ Gateway /api/operations/finance/** ─ Operations BFF /finance/**（统一授权拦截、填操作人 Header、简单转发）
                                                   ├─ ledger-service  固定 base-url  /finance-entities/v1 … /ledger-books/v1 …
                                                   └─ account-service 固定 base-url  /balances/v1/clients|partners/{id}
```

| 职责 | 落点 |
|---|---|
| 账务基础资料与账本事实、业务校验、错误码 | ledger（Owner） |
| Owner 余额投影 | account（Owner） |
| 入口授权（PLT-I02）、协议防护、把会话账号转成 `X-Gateway-Actor-Subject` / `X-Gateway-Session-Id`、分页回显校验、错误码到状态映射 | Operations BFF |
| 页面、表单校验、错误码到字段的映射、金额按币种精度展示 | Operations Web |
| 权限、Console Resource、Gateway 路由与限流数据 | Platform 初始化 SQL |

设计要点：

1. **BFF 固定地址下游。** ledger/account 不注册 Nacos（`pom.xml` enforcer 禁 `com.alibaba.nacos:*`）。BFF 新增 `FinanceClientConfiguration`：两个 `RestClient`，`baseUrl` 取属性 `operations.ledger-base-url`（默认 `http://127.0.0.1:18105`）、`operations.account-base-url`（默认 `http://127.0.0.1:18114`），`JdkClientHttpRequestFactory` + `Redirect.NEVER`，连接 2s / 读取 4s，`requestIdInterceptor` 传 `X-Request-Id`，`InternalCallTokenOptions.apply(options, tokens, "ledger-service" | "account-service")`，`HttpServiceClientOptions.strict().withRequestValidationFailures()`，统一 `withDependencyFailureMapper`（TIMEOUT→504、UNAVAILABLE→503）。不加 LoadBalancer、不加 Discovery 拦截器、不重试。`auto` 测试直接把两个属性指到 Simulator。README"代码分层"补"固定地址下游"一节。
2. **操作人 Header。** ledger 六个命令方法新增前两个参数 `@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId`、`@RequestHeader("X-Gateway-Session-Id") String sessionId`（必填，缺失由 Spring 返回 400 INVALID_REQUEST）。Controller 在命令成功后记一条 INFO：`财务基础资料命令 action=<ACTION> actor=<id> session=<id> resourceId=<id>`。不落库（Q-03 已决）。
3. **业务错误可见。** Foundation `HttpServiceClientInterceptor` 对公共 `BUSINESS_CHECK_FAILED` 只保留默认消息、不外显远端 `detail`，BFF 因此拿不到中文原因。按 [DEVELOPMENT_CONVENTIONS 第 4 节](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md#4-异常与枚举)"调用方确有区别处理的契约时才定义专有 `CodeMessageEnum`"：本次前端要把不同拒绝放到不同表单字段下，构成区别处理。ledger-api 新增 `LedgerSetupErrorCode`、account-api 新增 `BalanceQueryErrorCode`（第 2.4 节），Owner 以 `new FailException(code)` 抛出（400，Problem `code` 为枚举 Code、`detail` 为枚举默认消息），BFF 在 `HttpServiceClientFactory.create(...)` 显式注册两个枚举并原样返回 400，Web 按 Code 映射字段与文案。只有本期页面需要区分的拒绝进枚举；其余仍用公共 `BizCheckFailException`。
4. **路径与权限。** BFF 浏览器路径 = `/finance` + Owner 路径；Gateway 外部路径 = `/api/operations` + BFF 路径。权限两级：`FINANCE_ACCOUNTING_READ`（全部 GET）、`FINANCE_ACCOUNTING_MANAGE`（全部 POST）。
5. **不记元数据日志。** 财务数据不含个人原值，`/finance` 不加入 `web.logging.metadata-only-path-prefixes`。
6. **事务与并发。** 本次不新增写事务；ledger 既有命令保持原 Processor（`AbstractProcessor` / 期间创建 `AbstractLockProcessor`）。只读接口无锁无事务。无幂等变更（Gateway `idempotency_mode=NONE`，与 Webhook 块一致）。

## 2. 接口

### 2.1 ledger 新增 `LedgerSetupQueryApi`（`com.paytech.ledger.api`，`@HttpExchange(accept = "application/json")`，方法写完整路径）

| # | Method + Path | 查询参数 | 响应 | 说明 |
|---|---|---|---|---|
| L1 | `GET /finance-entities/v1` | `status`（可选 `ACTIVE\|CLOSED`）、`page`、`size` | `PagingResponseBody<FinanceEntityResponseBody>` | 按主键升序 |
| L2 | `GET /fiscal-calendars/v1` | `entityType`（可选 `OPERATING_ENTITY\|FINANCE_ENTITY`）、`entityId`（可选）、`page`、`size` | `PagingResponseBody<FiscalCalendarResponseBody>` | 按主键升序 |
| L3 | `GET /fiscal-calendars/v1/{fiscalCalendarId}/periods` | `fiscalYear`（可选）、`page`、`size` | `PagingResponseBody<AccountingPeriodResponseBody>` | 按 `fiscalYear, periodNo` 升序；日历不存在 400 `FISCAL_CALENDAR_NOT_FOUND` |
| L4 | `GET /chart-of-accounts/v1` | 无 | `ChartOfAccountsListResponseBody{items}` | 全部科目表，按 `chartCode` |
| L5 | `GET /chart-of-accounts/v1/{chartCode}/accounts` | `postingAllowed`（可选 `true\|false`） | `ChartAccountListResponseBody{items}`（复用现有） | 按 `accountCode`；科目表不存在 400 `CHART_NOT_ACTIVE` |
| L6 | `GET /currencies/v1` | `status`（可选 `DRAFT\|ACTIVE\|INACTIVE`） | `CurrencyListResponseBody{items}` | 按 `currency` |

响应体字段（`response` 包，普通 class + Lombok，ID 与枚举均为 `String`，时间为 `Instant`）：

| 类 | 字段 |
|---|---|
| `FinanceEntityResponseBody` | `financeEntityId`、`entityCode`、`legalPartyId`、`displayName`、`status`、`createTime` |
| `FiscalCalendarResponseBody` | `fiscalCalendarId`、`entityType`、`entityId`、`calendarCode`、`timezone`、`yearStartMonth`、`createTime` |
| `AccountingPeriodResponseBody` | `accountingPeriodId`、`fiscalCalendarId`、`fiscalYear`、`periodNo`、`startDate`、`endDate`、`status`、`reopenCount` |
| `ChartOfAccountsResponseBody` / `ChartOfAccountsListResponseBody` | `chartId`、`chartCode`、`chartName`、`status` / `items` |
| `CurrencyResponseBody` / `CurrencyListResponseBody` | `currency`、`minorUnit`、`roundingMode`、`status` / `items` |

实现形态沿用现有查询链：`LedgerSetupQueryController implements LedgerSetupQueryApi` → `LedgerQueryService` 新增 6 个方法 → Repository 新增 `findPage/countPage`（财务主体、日历、期间）与 `findAll`（科目表、币种）、`findByChart(chartId, postingAllowed)`（科目） → Mapper XML。查询对象放 `domain`（`FinanceEntityQuery`、`FiscalCalendarQuery`、`AccountingPeriodQuery`，含 `PagingRequestBody`）。不改 `LedgerBookQueryApi`、`LedgerReadApi`，account 的测试 Mock 不受影响。

### 2.2 ledger 修改：六个命令端点加操作人 Header

| 接口 | 方法 | 变更 |
|---|---|---|
| `FinanceEntityCommandApi` | `createFinanceEntity` | 前置 `@RequestHeader("X-Gateway-Actor-Subject") String actorAccountId, @RequestHeader("X-Gateway-Session-Id") String sessionId` |
| `FiscalCalendarCommandApi` | `createFiscalCalendar`、`createAccountingPeriod` | 同上 |
| `AccountingPeriodCommandApi` | `openAccountingPeriod` | 同上 |
| `LedgerBookCommandApi` | `createLedgerBook`、`createAccount` | 同上 |

兼容：不兼容签名变更；现有调用方仅 ledger-test（`HttpTestInvoker`，补 `TestRequestHeaders` 两个 Header）与本机 `-exec.jar` 冒烟脚本。请求体、路径、状态码、幂等语义不变。

### 2.3 ledger / account 保持不变并被 BFF 转发

`LedgerBookQueryApi` 四个端点；`BalanceQueryApi` 两个端点。

### 2.4 错误码（`*-api/api/enums`，实现 `CodeMessageEnum`，常量间空一行）

`LedgerSetupErrorCode`（ledger 在对应 Processor / Repository 以 `new FailException(code)` 抛出，替换当前同场景的 `BizCheckFailException(message)`；DuplicateKey 转换处按唯一键归类）：

| Code | 默认消息 | 抛出点 | Web 落点 |
|---|---|---|---|
| `FINANCE_ENTITY_ALREADY_EXISTS` | 财务主体代码或法律 Party 已被使用 | `FinanceEntityRepositoryImpl` DuplicateKey | 新建主体表单顶部 |
| `FINANCE_ENTITY_UNAVAILABLE` | 财务主体不存在或已关闭 | `CreateLedgerBookProcessor`、`CreateFiscalCalendar*`（FINANCE_ENTITY 主体） | `entityId` |
| `FISCAL_CALENDAR_ALREADY_EXISTS` | 该主体已有财务日历 | 日历 DuplicateKey | `entityId` |
| `FISCAL_CALENDAR_NOT_FOUND` | 财务日历不存在 | `CreateAccountingPeriodProcessor`、L3 | 页面提示 |
| `ACCOUNTING_PERIOD_OVERLAP` | 期间日期与同日历其他期间重叠 | `CreateAccountingPeriodProcessor` countOverlapping | `startDate` |
| `ACCOUNTING_PERIOD_NO_DUPLICATE` | 同一财年期号已存在 | 期间 DuplicateKey | `periodNo` |
| `ACCOUNTING_PERIOD_NOT_FOUND` | 会计期间不存在 | `OpenAccountingPeriodProcessor` | 页面提示 |
| `ACCOUNTING_PERIOD_NOT_FUTURE` | 只有 FUTURE 期间可以开放 | `OpenAccountingPeriodProcessor` | 页面提示 |
| `LEDGER_BOOK_ALREADY_EXISTS` | 同主体同币种已有账簿 | `LedgerBookRepositoryImpl` DuplicateKey | 新建账簿表单顶部 |
| `CURRENCY_NOT_ACTIVE` | 币种未启用 | `CreateLedgerBookProcessor` | `currency` |
| `CHART_NOT_ACTIVE` | 科目表不存在或未启用 | `CreateLedgerBookProcessor`、L5 | `chartCode` |
| `LEDGER_BOOK_NOT_FOUND` | 账簿不存在 | `CreateAccountProcessor`、`LedgerBookQueryApi` 现有"账簿不存在" | 页面提示 |
| `CHART_ACCOUNT_NOT_POSTABLE` | 科目不存在或不可过账 | `CreateAccountProcessor` | `accountCode` |
| `ACCOUNT_IDENTITY_INVALID` | 核算对象或余额桶不符合账户身份矩阵 | `CreateAccountProcessor` 矩阵校验 | `ownerType` |

`ACCOUNT_ALREADY_EXISTS`（账户已存在，账户 DuplicateKey，表单顶部）为第 15 个 Code。`InvalidParameterException` 场景（期间起止倒置、期号非正）保持公共 `INVALID_REQUEST`，BFF 启用 `withRequestValidationFailures` 透传 `errors[]` 字段清单。

`BalanceQueryErrorCode`（account）：`LEDGER_BOOK_NOT_FOUND`，"未找到该经营主体与币种的账簿"，`QueryClientBalanceProcessor` / `QueryPartnerBalanceProcessor` 替换现有 `BizCheckFailException`；Web 落在 `entityNo` 字段。

### 2.5 BFF 转发路由（18 个；BFF 路径 = 浏览器路径；Gateway 外部路径前缀 `/api/operations`）

| # | Method | BFF 路径 | 下游 | 权限 | 路由 Code | Query 白名单 |
|---|---|---|---|---|---|---|
| R01 | GET | `/finance/finance-entities/v1` | ledger L1 | READ | `operations-finance-entity-list` | status,page,size |
| R02 | POST | `/finance/finance-entities/v1` | ledger 创建主体 | MANAGE | `operations-finance-entity-create` | — |
| R03 | GET | `/finance/fiscal-calendars/v1` | ledger L2 | READ | `operations-finance-calendar-list` | entityType,entityId,page,size |
| R04 | POST | `/finance/fiscal-calendars/v1` | ledger 创建日历 | MANAGE | `operations-finance-calendar-create` | — |
| R05 | GET | `/finance/fiscal-calendars/v1/{fiscalCalendarId}/periods` | ledger L3 | READ | `operations-finance-period-list` | fiscalYear,page,size |
| R06 | POST | `/finance/fiscal-calendars/v1/{fiscalCalendarId}/periods` | ledger 创建期间 | MANAGE | `operations-finance-period-create` | — |
| R07 | POST | `/finance/accounting-periods/v1/{accountingPeriodId}/open` | ledger 开放期间 | MANAGE | `operations-finance-period-open` | —（无 Body） |
| R08 | GET | `/finance/ledger-books/v1` | ledger 账簿分页 | READ | `operations-finance-book-list` | entityType,entityId,currency,page,size |
| R09 | POST | `/finance/ledger-books/v1` | ledger 创建账簿 | MANAGE | `operations-finance-book-create` | — |
| R10 | GET | `/finance/ledger-books/v1/{ledgerBookId}/accounts` | ledger 账户分页 | READ | `operations-finance-account-list` | accountCode,ownerType,ownerId,balanceBucket,page,size |
| R11 | POST | `/finance/ledger-books/v1/{ledgerBookId}/accounts` | ledger 创建账户 | MANAGE | `operations-finance-account-create` | — |
| R12 | GET | `/finance/ledger-books/v1/{ledgerBookId}/accounts/{accountId}/journal-lines` | ledger 流水 | READ | `operations-finance-journal-line-list` | postingDateFrom,postingDateTo,page,size |
| R13 | GET | `/finance/ledger-books/v1/{ledgerBookId}/journal-entries/{journalEntryId}` | ledger 分录详情 | READ | `operations-finance-journal-entry-detail` | — |
| R14 | GET | `/finance/chart-of-accounts/v1` | ledger L4 | READ | `operations-finance-chart-list` | — |
| R15 | GET | `/finance/chart-of-accounts/v1/{chartCode}/accounts` | ledger L5 | READ | `operations-finance-chart-account-list` | postingAllowed |
| R16 | GET | `/finance/currencies/v1` | ledger L6 | READ | `operations-finance-currency-list` | status |
| R17 | GET | `/finance/balances/v1/clients/{clientId}` | account | READ | `operations-finance-client-balance` | entityNo,currency |
| R18 | GET | `/finance/balances/v1/partners/{partnerId}` | account | READ | `operations-finance-partner-balance` | entityNo,currency |

BFF 实现：

- Controller 三个：`FinanceSetupController`（R01—R07、R14—R16）、`FinanceLedgerBookController`（R08—R13）、`FinanceBalanceController`（R17—R18）；路径变量 `@Pattern(ID)`，`chartCode` `[A-Z][A-Z0-9_]{0,63}`，`clientId/partnerId` 正十进制，分页 `requireQueryKeys` + `PagingRequestBody.of`。
- Client Port：`FinanceSetupClient`、`FinanceLedgerBookClient`、`FinanceBalanceClient`（`integration`）与 impl（`integration/impl`），impl 注入 Owner API 代理，以 `context.accountId()`、`context.sessionId()` 传入命令 Header；分页响应用 `requirePage` 回显校验；本地模型 `FinanceSetupModels`、`FinanceLedgerBookModels`、`FinanceBalanceModels`（RequestBody / ResponseBody 命名满足 `OperationsHttpBodyNamingAutoTest`）。
- 请求体防护：新增 `FinanceRequestConfiguration`（路径 `/finance/**`）：严格 JSON（拒重复键、未知属性、标量强转），只读 `Finance*Models` 内 `*RequestBody`，写请求体上限 8192，GET 与 R07 拒绝 Body。
- 错误：`HttpServiceClientFactory.create(..., LedgerSetupErrorCode.values(), BalanceQueryErrorCode.values())`；两枚举默认 400，无需改 `OperationsErrorStatusResolver`（若 Foundation 默认不是 400 再增量加映射）。
- 共享文件增量：`pom.xml`（`paytech-ledger-api`、`paytech-account-api`）、`application.yml`（两个 base-url 属性，带注释说明固定地址原因）、`OperationsConsoleBffApplicationAutoTest`（精确路由 +18）、`OperationsHttpBodyNamingAutoTest`（Controller / API 列表）、`OperationsBffHttpTestBase`（两个属性指向 Simulator）、`OperationsBffTestConfiguration`（新增 `LEDGER`、`ACCOUNT` Simulator Bean）、`PlatformOperationsSimulator`（`operations-finance-*` → 权限 `900000000000097101/102`）。
- 请求响应示例（R02）：

```http
POST /api/operations/finance/finance-entities/v1
Content-Type: application/json
{"entityCode":"PLT-AE","legalPartyId":"880000000000012001","displayName":"平台阿联酋财务主体"}

201 {"financeEntityId":"7312345678901234567"}
400 {"type":"about:blank","title":"Bad Request","detail":"财务主体代码或法律 Party 已被使用","code":"FINANCE_ENTITY_ALREADY_EXISTS","requestId":"…","timestamp":"…"}
```

### 2.6 Web 消费映射

| 页面 | 调用 |
|---|---|
| 账务基础资料 / 财务主体 | R01、R02 |
| 账务基础资料 / 日历与期间 | R03、R04、R05、R06、R07、R01（FINANCE_ENTITY 主体下拉） |
| 账务基础资料 / 账簿与账户 | R08、R09、R10、R11、R14、R15、R16、R01 |
| 账务基础资料 / 科目表与币种（只读） | R14、R15、R16 |
| 账本查询 | R08、R10、R12、R13、R16（金额精度） |
| 余额查询 | R17、R18 |

## 3. 数据库与注册数据

- **业务 Schema DDL：** 0。**业务 DML：** 0。
- **注册 DML：** `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql` 末尾追加两个自包含块（`START TRANSACTION … COMMIT`，头部注释写需求路径、行数、回滚 ID 范围），同时在本需求目录 `db/platform-support-service/registration-dml.sql` 保留同内容副本作为阅读索引（执行来源唯一为初始化 SQL 块）。全部行 `DISABLED` / `REVOKED` 休眠；激活 SQL（本机 `activation-local.sql`、mor-test 另列）阶段 5 / 7 单独授权。

ID 块（`9000000000000NNXYY`，NN=97 基础资料与账本，NN=98 余额）：

| 表 | 块 97 | 块 98 | 行数 |
|---|---|---|---|
| `t_permission` | `…097101` `FINANCE_ACCOUNTING_READ`（`FINANCE_ACCOUNTING`/`READ`/`LOW`）、`…097102` `FINANCE_ACCOUNTING_MANAGE`（`MANAGE`/`HIGH`） | — | 2 |
| `t_role_permission` | `…097201/202` → 管理员角色 `900000000000000510`，`REVOKED` | — | 2 |
| `t_console_resource` | `…097301` 目录 `OPERATIONS_FINANCE`"财务"（parent NULL，sort 50，permission READ）；`…097302` `OPERATIONS_FINANCE_SETUP`"账务基础资料" `/finance/setup` sort 10；`…097303` `OPERATIONS_FINANCE_LEDGER`"账本查询" `/finance/ledger` sort 20；`…097304` `OPERATIONS_FINANCE_BALANCES`"余额查询" `/finance/balances` sort 30；`…097305` BUTTON `OPERATIONS_FINANCE_SETUP_MANAGE`"维护"（parent 097302，permission MANAGE） | — | 5 |
| `t_gateway_route` | `…097401—097416` = R01—R16 | `…098401—098402` = R17—R18 | 18 |
| `t_gateway_route_header` | GET 2 行（authorization/CORS、origin/FORWARD），POST 6 行（+content-type、idempotency-key 各两用途）：10×2 + 6×6 = 56 | 2×2 = 4 | 60 |
| `t_gateway_route_query_param` | 按 2.5 白名单：3+4+3+5+6+4+1+1 = 27 | 2+2 = 4 | 31 |
| `t_gateway_guard_rule` | 每路由 6 行（SHARED_ROUTE 300/60s + 并发 50；CLIENT_IP 30 + 3；PRINCIPAL 10 + 2）：16×6 = 96 | 2×6 = 12 | 108 |

路由字段：`target_id` = BFF Target `900000000000000001`，`security_profile_code='OPERATIONS_JWT'`，`authorization_mode='HUMAN_PERMISSION'`，GET `body_limit_bytes=0`，POST 8192，`body_receive_timeout_millis=5000`，`response_timeout_millis=10000`，`idempotency_mode='NONE'`，`status='DISABLED'`。

- **Platform 代码：** `console-resource-capabilities.json` 追加三页（与 Web `pageCapabilities` 同形）；测试基线 `OperationsCatalog.java` 追加两条 Permission（`viewer=false`）。
- **mor-test：** `compose.applications.yaml` `operations-console-bff` 环境变量追加 `OPERATIONS_LEDGER_BASE_URL=http://ledger-service:18105`、`OPERATIONS_ACCOUNT_BASE_URL=http://account-service:18114`（阶段 6 清单）。

## 4. 页面设计（结构稿）

Pencil：`operations-console-web/docs/operations-console.pen` 新增 12 个画板（`财务 · 01—12`，位于 Webhook 画板下方，y 59400 起），导出见 `assets/stage3/pencil/`。三页复用 Webhook 管理 / 通知管理已验收的页面骨架（筛选表单 + 表格 + 对话框表单 + 确认弹窗 + 内联说明），无新组件形态。

| 画板 | 内容 | 对应验收 |
|---|---|---|
| 01 账务基础资料｜财务主体 | 三标签页、状态筛选、主体列表（启用 / 已关闭标签）、分页、说明 | AC-01、AC-05、AC-11 |
| 02 账务基础资料｜日历与期间 | 日历筛选与列表（选中高亮）、所选日历的期间列表（FUTURE 行才有「开放」）、财年筛选、新建日历 / 新建期间按钮 | AC-02、AC-03 |
| 03 开放期间确认 | 二次确认弹窗，显示日历、期间、当前状态与不可逆说明 | AC-03 |
| 04 账务基础资料｜账簿与账户 | 账簿筛选与列表、所选账簿的账户列表（科目 + 名称、核算对象、余额桶、方向、状态、借贷合计、净余额，反向余额红色） | AC-04、AC-05、AC-12 |
| 05 新建账户（字段错误） | 科目下拉（仅可过账）、核算对象类型 / 标识、余额桶；`ACCOUNT_ALREADY_EXISTS` 落在余额桶字段、标识格式错误落在标识字段 | AC-02、AC-04 |
| 06 新建账簿 | 主体类型 / entityNo、币种与科目表下拉（仅启用项）、提示 | AC-04 |
| 07 账本查询｜账户列表 | 面包屑「账簿 › AE001 / USD」、账户筛选、只读账户列表（无新建） | AC-05、AC-13 |
| 08 账本查询｜账户流水 | 三级面包屑、账户摘要、过账日期范围、流水表（序号链接、类型 / 方向标签、金额与行后余额右对齐、来源 AUTO / MANUAL 两行） | AC-06、AC-12、AC-13 |
| 09 分录详情抽屉 | 分录头、指令来源、分录行表、借贷合计与「借贷平衡」标签 | AC-06 |
| 10 余额查询（结果） | Owner 类型切换、Owner ID / entityNo / 币种表单、五张余额卡片（Partner 时无保证金卡）、「Available 不等于可出款资格」提示 | AC-07 |
| 11 余额查询（账簿不存在） | `LEDGER_BOOK_NOT_FOUND` 落在 entityNo 字段、空状态 | AC-07、AC-11 |
| 12 账务基础资料｜科目表与币种（只读） | 第四个标签页：科目表与币种在左、科目列表在右，无新建按钮 | AC-04 |

### 4.1 账务基础资料 `/finance/setup`（四个标签页，URL `?tab=entities|calendars|books|charts`）

| 标签 | 列表 | 操作（仅 MANAGE 可见） | 表单与校验 |
|---|---|---|---|
| 财务主体 | 代码、显示名称、法律 Party ID、状态标签、创建时间；筛选状态；分页 | 新建主体 | `entityCode`（1—64）、`legalPartyId`（正十进制）、`displayName`（1—256）；错误：`FINANCE_ENTITY_ALREADY_EXISTS` 表单顶部 |
| 日历与期间 | 上半：日历列表（主体类型、主体、代码、时区、财年起始月），筛选主体类型 + 主体标识；下半：选中日历的期间列表（财年、期号、起止、状态、重开次数），筛选财年 | 新建日历；新建期间；期间行"开放"（仅 FUTURE，二次确认"开放后可过账，不能回到 FUTURE"） | 日历：`entityType` 下拉、`entityId`（OE 为 `[A-Z]{2}[0-9]{3}` 输入框；FE 为 R01 下拉）、`calendarCode`、`timezone`（IANA 文本，前端用 `Intl.supportedValuesOf('timeZone')` 做下拉）、`yearStartMonth` 1—12。期间：`fiscalYear`、`periodNo`、`startDate ≤ endDate`。错误落点见 2.4 |
| 账簿与账户 | 上半：账簿列表（主体类型、主体、币种、科目表、状态），筛选三项；下半：选中账簿的账户列表（科目代码 + 名称、核算对象、余额桶、方向、状态、借方合计、贷方合计、净余额），筛选科目 / 核算对象 / 余额桶 | 新建账簿；新建账户 |
| 科目表与币种（只读） | 左栏：科目表列表（代码、名称、状态，默认选中第一个）与币种列表（代码、小数位、舍入、状态）；右栏：所选科目表的科目列表（代码、名称、类别、方向、可过账、上级科目），"只看可过账"开关 | 无（MANAGE 也不显示新建） | 账簿：`entityType`、`entityId`（同上）、`currency`（R16 ACTIVE 下拉）、`chartCode`（R14 ACTIVE 下拉）。账户：`accountCode`（R15 `postingAllowed=true` 下拉，显示名称与方向）、`ownerType` 下拉、`ownerId`（OE 为 entityNo 规则，其余正十进制）、`balanceBucket`（`CLIENT_PAYABLE`/`PARTNER_PAYABLE` 科目时 PENDING/AVAILABLE/LOCKED，其余锁定 NONE） |

空状态：无主体 / 无日历 / 未选日历 / 未选账簿 / 无账户。加载失败内联 Alert + 重试；403 触发授权刷新。

### 4.2 账本查询 `/finance/ledger`（URL `?book=&account=&entry=`）

1. 账簿列表（同 4.1 账簿列，无新建）→ 点击行进入账户列表。
2. 账户列表（同 4.1 账户列）→ 点击行进入流水。
3. 流水：顶部账户摘要（科目、核算对象、余额桶、方向、当前净余额），过账日期范围筛选（默认不限），列：分录序号、过账日期、类型、行号、方向、金额、行后余额、指令类型、来源（AUTO 显示 `sourceSystem/sourceEventKey`，MANUAL 显示调整单号）。页脚固定提示"行后余额按全量历史累计，筛选不改变起点"。
4. 分录详情抽屉：头（分录主键、序号、日期、类型、冲回原分录、指令主键 / 类型 / 来源、人工调整主键 / 单号）+ 行表（行号、科目、核算对象、余额桶、方向、金额），底部显示借贷合计。

面包屑：账簿 › 账户 › 流水，可回退并保留筛选（存于 URL）。

### 4.3 余额查询 `/finance/balances`

表单：Owner 类型（Client / Local Partner 单选）、Owner ID（正十进制）、OE `entityNo`、币种（R16 ACTIVE 下拉）→ 查询。结果卡片：PENDING / AVAILABLE / LOCKED 三格；Client 另有保证金、追偿应收；Partner 只有追偿应收。卡片下固定说明"Available 不等于可出款资格"。`LEDGER_BOOK_NOT_FOUND` 落在 `entityNo` 字段下。未查询时为空状态。

### 4.4 通用

- 金额右对齐，按 `minorUnit` 补零（R16 缓存），负数红色并加"反向"标签；状态统一标签色；日期 ISO，`Instant` 按本地时区。
- 按钮权限：`resources.some(r => r.resourceType==='BUTTON' && r.resourceCode==='OPERATIONS_FINANCE_SETUP_MANAGE')`。
- 文件：`src/pages/FinanceSetup.tsx`、`FinanceLedger.tsx`、`FinanceBalances.tsx`；`src/finance/`：`finance.ts`（路径常量、类型、解码器、错误码→字段文案表、金额格式）、`useFinanceCommand.ts`、`SetupEntitiesTab.tsx`、`SetupCalendarsTab.tsx`、`SetupBooksTab.tsx`、`SetupChartsTab.tsx`、`FinanceModals.tsx`、`LedgerDrilldown.tsx`、`JournalEntryDrawer.tsx`、`finance.css`、`finance.test.ts`；共享：`pageCapabilities.ts`（+3）、`ConsolePage.tsx`（+3）、`configurationModel.test.ts`（30→33）。

## 5. 研发与验证计划（阶段 4，Opus 5.5 多 agent）

### 5.1 任务拆分与顺序

| 步骤 | 任务 | 执行者 | 产物 | 依赖 |
|---|---|---|---|---|
| T0 | **合同先行**：ledger-api `LedgerSetupQueryApi` + 5 个响应体 + `LedgerSetupErrorCode`、六个命令接口加 Header；account-api `BalanceQueryErrorCode`；`./mvnw -pl ledger/paytech-ledger-api,account/paytech-account-api install -DskipTests` | Agent A（先做，其余等待） | 两个 api 模块编译安装 | — |
| T1 | ledger 实现：查询链、错误码替换、命令 Header 与日志、ledger-test 新增用例与补 Header | Agent A（续） | 代码 + `ledger-test` 通过 | T0 |
| T2 | account：`BalanceQueryErrorCode` 抛出替换 + 测试断言 | Agent A（续，小） | `account-test` 通过 | T0 |
| T3 | BFF：客户端配置、Port/impl、三 Controller、请求体防护、Simulator 与测试、共享文件增量、README 节 | Agent B | `operations-console-bff-test` 通过 | T0 |
| T4 | Web：三页与 `src/finance/`、共享文件增量、测试 | Agent C | `npm test`、`npm run build` 通过 | 合同（可并行，按本文 2.5/2.6 开发，不等 T0 安装） |
| T5 | Platform：初始化 SQL 两块、JSON 三项、`OperationsCatalog` 两条；需求目录 `db/platform-support-service/registration-dml.sql` 副本 | Agent D | SQL 只写不执行；Platform 相关测试通过 | — |
| T6 | 主会话合并核对：对照 2.1—2.5 合同逐项核对、共享文件冲突检查（`git status` 与 Processor 迁移会话）、全量跑四条线测试、整理阶段 4 报告与验收映射 | 主会话（当前模型） | 阶段 4 报告 | T1—T5 |

Agent 边界：A 只改 `paytech/ledger`、`paytech/account`；B 只改 `paytech/operations-console-bff`；C 只改 `operations-console-web`；D 只改 `paytech/platform-support/docs/database/sql`、`console-resource-capabilities.json`、`OperationsCatalog.java` 与需求目录 `db/`。任何人不碰 account 的 `README.md` / `account.pdma.json`（其他会话在改）。每个 agent 开工前读 `agent-docs/DEVELOPMENT_CONVENTIONS.md`、`JAVA_PROJECT_STRUCTURE.md`、`UNIT_TEST_GUIDE.md`（Web 读 `operations-console-web/README.md`），遵守 Mock 禁令与 BeforeEach/AfterEach 规则。

### 5.2 验收标准映射与测试入口

| AC | 测试入口 |
|---|---|
| AC-01 | BFF Browser 测试：无权限路由 403；Web 测试：无 BUTTON 资源时不渲染新建 / 开放按钮 |
| AC-02 | ledger-test：各错误码用例（重复主体、Party 重复、重复日历、期间重叠、重复期号、重复账簿、币种 / 科目表未启用、科目不可过账、身份矩阵、重复账户）；BFF 边界 JSON：400 透传 Code；Web 测试：Code→字段映射 |
| AC-03 | ledger-test：FUTURE→OPEN、非 FUTURE 400、重复开放幂等；BFF R07 拒绝 Body |
| AC-04 | ledger-test：L4/L5/L6 过滤；Web：下拉数据源来自 R14/R15/R16 |
| AC-05 | ledger 既有 16 例；BFF：`size=7` 400、分页回显校验 |
| AC-06 | ledger 既有；Web：流水表与详情抽屉解码测试 |
| AC-07 | account-test：`LEDGER_BOOK_NOT_FOUND`；BFF：R17/R18 转发与 Partner 响应无保证金字段 |
| AC-08 | ledger-test：缺 Header 400；BFF：Simulator 记录到的 Header 等于会话账号、不含 Authorization / Cookie |
| AC-09 | 人工审阅 SQL 块行数与 ID 范围；`OperationsCatalog` 基线测试 |
| AC-10 | 四条线各自测试命令（下表） |
| AC-11—13 | 阶段 5 本机浏览器验收截图（`assets/stage5/`） |

| 线 | 命令（`paytech/` 下，JDK 25） |
|---|---|
| ledger | `./mvnw -B verify -pl ledger/paytech-ledger-test -am` |
| account | `./mvnw -B verify -pl account/paytech-account-test -am` |
| BFF | `./mvnw -B verify -pl operations-console-bff/paytech-operations-console-bff-test -am` |
| Platform | `./mvnw -B verify -pl platform-support/paytech-platform-support-test -am -Dtest='OperationsCatalog*,*ConsoleResource*'`（全量留阶段 5） |
| Web | `npm --prefix operations-console-web test && npm --prefix operations-console-web run build` |

真实环境依赖：ledger / account 测试需本机 `local-environment` MySQL（`mor_ledger`、`mor_account`）、Redis、RabbitMQ；BFF 与 Web 测试不需要外部依赖。

### 5.3 阶段 5 预告（不在本阶段执行）

本机 `mor_platform_e2e` 执行两块注册 SQL + 激活 SQL（单独授权）、重启本机 BFF 与 ledger/account（命令 Header 变更）、`local_acceptance.py` 接口验收与浏览器截图；mor-test 发布清单含 BFF 环境变量两项。

## 6. 遗留与边界

- CCT OE / Party 选择器、持久化审计、修改 / 停用基础资料、余额重建：不在本期（阶段 1 已决）。
- `console-resource-capabilities.json` 与 Web 历史不同步（缺 9 项）：另行登记，本期只追加三项。
- BFF 全局错误 `detail` 投影策略仍是 Webhook 需求遗留的待决项；本期用专有错误码绕开，不改 Foundation。

## 7. 实现偏差（阶段 4）

| 线 | 偏差 | 原因 / 处理 |
|---|---|---|
| ledger | 错误 `detail` 只用枚举默认消息，不再附 ID 上下文 | 与 2.4"detail 为枚举默认消息"一致；排障靠日志中的 resourceId |
| ledger | `LEDGER_BOOK_NOT_FOUND` 只用于 `LedgerBookQueryApi`，account 调用的 `LedgerReadApi` 保持公共 `BUSINESS_CHECK_FAILED` | account 的客户端未注册该枚举，未知 Code 会被判为上游无效响应（502） |
| ledger | `ACCOUNT_IDENTITY_INVALID` 只在 `CreateAccountProcessor` 抛出；过账与规则版本流程复用的矩阵校验保持原公共错误 | 避免改动非本期流程的错误合同 |
| ledger | 期间重叠检查仍在 `AccountingPeriodServiceImpl` 的锁内事务里改抛新 Code，未移到 Processor | 不改既有并发边界 |
| ledger | `CurrencySpecRepository.findAll(status)` 在 SQL 过滤；L5 只校验科目表存在，不校验状态 | 与 2.1 的"科目表不存在"口径一致 |
| ledger | 操作人 INFO 日志未被测试断言，仅人工在运行日志确认 | 日志不是合同 |
| account | 无 | — |
| BFF | ledger 与 account 各自一个代理工厂、各注册自己的枚举，而非一个 `create(...)` 注册两枚举 | 两枚举都有 `LEDGER_BOOK_NOT_FOUND`，Foundation 对同一工厂内重复 Code 抛异常；测试确认 account 的消息到达浏览器 |
| BFF | 创建接口用 `@ResponseStatus(CREATED)` 而非 `ResponseEntity` | 命名测试要求返回类型以 `ResponseBody` 结尾；四个返回 `ResponseEntity` 的 Owner CommandApi 因此不进命名测试清单 |
| BFF | 超时（504）用例放在 Setup / Balance Browser 测试（Simulator 挂起），不可用测试类只覆盖连接拒绝（503） | 一个 Spring 上下文只有一个固定地址 |
| BFF | `requireQueryKeys` 复用 `RiskListController` 的包内方法 | 不复制同义代码 |
| BFF | 本地账户模型命名加 `Ledger` 前缀（`LedgerAccountResponseBody` 等），JSON 形状不变 | 与 BFF 内既有"账号"模型区分 |
| BFF | 顶层 `operations-console-bff/pom.xml` 增加两个 api 的 dependencyManagement 版本 | 该模块的 api 版本统一在聚合 pom 管理 |
| Web | `ACCOUNT_ALREADY_EXISTS` 落在余额桶字段（画板 05），2.4 表写"表单顶部" | 以画板为准；改回为一行改动 |
| Web | 科目表标签页无"上级科目"列 | 复用的 `ChartAccountResponseBody` 无父科目字段（画板 12 该列阶段 5 按实际验收） |
| Web | 余额"查询范围"不显示账簿 ID；分录详情的会计期间只显示 ID | 两个响应体不含对应字段 |
| Web | 账本 URL 同时保存各级筛选与分页；粘贴链接可加载但只显示 ID，币种未知前金额不补零 | 显示对象放在 history state |
| Web | AC-10 的页面门控以源码扫描测试代替 DOM 测试 | 项目无浏览器测试框架 |
| Web | POST 发送 `Idempotency-Key`（与 Webhook 页一致），路由 Header 白名单已含；BFF 不转发 | 幂等模式为 NONE，无行为影响 |
| Web | 新增 `FinanceParts.tsx`、`useFinanceData.ts` 两个计划外文件 | 公共件与数据 Hook 拆分 |
| Web（阶段 5 修复） | 11 个 antd Form 加唯一 `name`，字段 id 变为 `<form>_<field>` | 弹窗与筛选表单重复 DOM id |
| Platform | R07（期间开放，无 Body）`body_limit_bytes=0` 而非 8192 | 与 Webhook 无 Body 路由先例一致 |
| Platform | 两条 `t_permission` 为 ACTIVE | 与通知 / Webhook 权限块先例一致；休眠由 REVOKED 授权与 DISABLED 菜单 / 路由保证 |
| Platform（阶段 5 后） | R08 / R10 / R12（账簿、账户、流水列表）的 AUTHENTICATED_PRINCIPAL 速率 30 / 分钟，其余 15 条路由仍 10 / 分钟 | 阶段 5 观察人工筛选、翻页一分钟内可达 10 次；2026-10-19 用户确认提到 30。初始化 SQL 与阅读副本各改 3 行，本机 `mor_platform_v2e2e` 已 UPDATE（3 / 3，路由版本 +1），经 Gateway 连发 14 次无 429 |
