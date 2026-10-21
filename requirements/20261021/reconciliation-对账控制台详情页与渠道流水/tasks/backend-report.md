# 后端线交付报告（阶段 4—5）

> 范围：technique §2.1、§2.2、§2.4。未提交、未推送、未部署，没有对任何数据库执行写 SQL（只做了 ID 占用的只读 SELECT），没有调用 Superpowers，没有改 operations-console-web。共享文件（ops-bff 测试与 Simulator、`platform-support-initial-data.sql`、`console-resource-capabilities.json`、两个 README）只做了局部追加，商务会话与其他会话的内容未动。
>
> **Platform 块号由 125 改为 126：** 货币主数据会话正在 mor-test 注册、激活块 125，按主会话 2026-10-21 的通知，本线全部改用块 126（`900000000000126nxx`）。本线从未写入过块 125 的任何行。

## 1. 已读依据

AGENTS.md；DEVELOPMENT_CONVENTIONS.md、JAVA_PROJECT_STRUCTURE.md、UNIT_TEST_GUIDE.md（按任务要求）；本需求 requirement.md、technique.md；前序切片 1 的 A、D 报告与块 123 注册 / 激活脚本；记忆中的 JDK 25 构建、测试用枚举不用字面值、Processor 模板方法不写 JavaDoc 三条。查询沿用现有写法：Controller → 查询 Service（domainservice）→ Repository，不新建 Processor。

## 2. 文件

**reconciliation**
- api：
  - `enums/ReconciliationErrorCode` 新增 `BANK_STATEMENT_NOT_FOUND`、`BANK_STATEMENT_LINE_NOT_FOUND`。
  - 新增 `statement/ChannelStatementLineApi`。
  - `bank/BankStatementApi` 加两个方法。
  - 新增响应体 `statement/response/ChannelStatementLineSearchItemResponseBody`、`bank/response/BankStatementLineDetailResponseBody`。
- core：
  - 新增 `domain/ChannelLineSearch`（查询条件；未指定账单状态时的默认排除口径集中在这里）。
  - 新增 `domain/view/ChannelStatementLineView`、`domain/view/BankStatementLineDetailView`。
  - `ChannelStatementLineMapper` + XML：`selectSearchPage`、`countSearch`；`ReconExceptionMapper` + XML：`selectActiveBySource`。
  - 对应 Repository 与 Impl。
  - `ChannelStatementQueryService.searchLines`。
  - `BankReconciliationQueryService.readBankStatement`、`readBankStatementLine`。
- app：
  - 新增 `ext/rest/ChannelStatementLineController`；`BankStatementController` 加两个方法。
  - 两个转换器各加一个方法。
  - 新增 `configuration/ReconciliationApiErrorConfiguration`（`ApiErrorStatusResolver`：两个 NOT_FOUND → 404）。
- test：
  - 新增 `auto/ext/http/ReconciliationDetailQueryTest`（6 例）。
  - `PersistenceMappingTest` 加 `test_channelLineSearchAndActiveBySource`。
- `README.md` 更新入站合同。

**operations-console-bff**
- `ReconciliationClientConfiguration` 增加 `ChannelStatementLineApi` 代理。
- `ReconciliationStatementClient/Impl.searchChannelStatementLines`：核对分页回执；带清算账户条件时，每个元素必须属于该清算账户。
- `ReconciliationBankClient/Impl.getBankStatement`、`getBankStatementLine`：核对流水主键；对账单必须是流水所属的那张；每条匹配属于本流水、带对应的预期流水；集合不能缺失。
- `ReconciliationStatementController`、`ReconciliationBankController` 加 3 个入口。查询参数白名单为 `clearingAccountId, statementStatus, matchStatus, lineType, referenceType, externalTransactionId, transactionDateFrom, transactionDateTo, page, size`；两个详情不接受查询参数。
- `ReconciliationStatementModels`、`ReconciliationBankModels` 增加镜像模型。
- `OperationsErrorStatusResolver` 增加 `ReconciliationErrorCode` 分支：两个 NOT_FOUND → 404，`CLEARING_ACCOUNT_NOT_RESOLVED` 保持默认。
- 测试：
  - `finance-reconciliation-boundary.json` 增加 R39–R41（38 → 41 行，只追加，原有行未动）。
  - `PlatformOperationsSimulator` 增加 3 条路由码到 `RECON_READ_PERMISSION_ID` 的对应。
  - `OperationsConsoleBffApplicationAutoTest` 路由清单 +3。
  - `ReconciliationBrowserAutoTest`：路由数 41；新增 `test_noDataFound`（两个 404 原样到达浏览器）、`test_detailInvalidUpstreamResponse`；`test_invalidParameter` 增加未知 Query 与非法 referenceType 两项。
- `README.md` 加一段说明。

**platform-support**
- `docs/database/sql/platform-support-initial-data.sql` 末尾、商务块 124 之后追加完整的块 126，以 COMMIT 结束。
- `console-resource-capabilities.json` 在 `OPERATIONS_FINANCE_RECON_PLATFORM_TXNS` 之后加 `OPERATIONS_FINANCE_RECON_CHANNEL_LINES`：`displayName` 渠道流水，`frontendRoute` `/finance/reconciliation/channel-lines`，`actions` 为 `[]`。

**需求目录**
- `db/platform-support/registration-dml.sql`：阅读副本。
- `activation-local.sql`、`test-activate.sql`、`test-deactivate.sql`：预期影响行数均为 3 / 1 / 1（路由 / 资源 / 角色版本）。

## 3. 接口合同（Web 线按这些字段对接；BFF 路径为 `/finance/reconciliation/v1` 加下列路径）

**`GET /channel-statement-lines`**
- Query：
  - `clearingAccountId`
  - `statementStatus`：不传时排除 VOIDED、REJECTED、RECEIVING 账单的行；传了只按该值过滤，可以传这三种状态。
  - `matchStatus`、`lineType`
  - `referenceType`、`externalTransactionId`：都是精确匹配，可以单独使用。
  - `transactionDateFrom`、`transactionDateTo`：两端都含。
  - `page`、`size`：只允许 10/20/50/100，200 返回 400 INVALID_REQUEST。
- 排序：`transactionDate` 降序，没有交易日期的行排最后，同日按 `lineId` 降序。
- 返回 `{items, page, size, total}`，item 字段：
  `lineId, statementId, channelBatchRef, revision, statementStatus, settlementDate, clearingAccountId, clearingAccountCode, currency, lineNo, lineType, direction, amount, feeAmount, referenceType, externalTransactionId, transactionDate, platformTxnId, matchMethod, matchedTime, actualFee, resolutionId, matchStatus`

**`GET /bank-statements/{bankStatementId}`**
- 返回字段与列表项相同：
  `bankStatementId, cashAccountId, statementRef, periodStart, periodEnd, currency, openingBalance, closingBalance, declaredLineCount, importedLineCount, duplicateLineCount, fileName, fileContentHash, rejectReason, status, makerId, createTime, updateTime`
- 不存在时 **404**，`code=BANK_STATEMENT_NOT_FOUND`。

**`GET /bank-statement-lines/{bankStatementLineId}`**
- 返回 `{line, statement, matches, openExceptions}`：
  - `line`：`bankStatementLineId, bankStatementId, cashAccountId, lineNo, bankTransactionRef, valueDate, direction, amount, currency, counterpartyName, referenceText, matchedAmount, matchStatus, version`
  - `statement`：同上面的对账单头。
  - `matches[]`：每项 `{match, expectedMovement}`。
    - `match`：`bankMatchId, bankStatementLineId, expectedMovementId, matchedAmount, matchMethod, payoutComponentType, status, resolutionId, operatorId, decisionTime, createTime`
    - `expectedMovement`：`expectedMovementId, movementType, sourceId, cashAccountId, entityNo, direction, currency, expectedAmount, expectedDate, bankPaymentRef, confirmedAmount, classifiedAmount, status, version, createTime, updateTime`
    - 包含 PROPOSED、CONFIRMED、REJECTED 三种状态，按 `bankMatchId` 升序。
    - `movementType=CHANNEL_SETTLEMENT` 时，`sourceId` 就是渠道账单 ID，页面可直接跳转。
  - `openExceptions[]`：字段与 `ReconExceptionResponseBody` 相同（`exceptionId, exceptionType, reconLayer, blocking, sourceType, sourceId, statementId, clearingAccountId, cashAccountId, entityType, entityId, currency, differenceAmount, detail, status, openedBy, openedTime, ageDays, closedTime, version`），只含 OPEN、UNDER_REVIEW，按 ID 升序。
- 不存在时 **404**，`code=BANK_STATEMENT_LINE_NOT_FOUND`。

**既有详情接口的「不存在」（按主会话决定不改合同，仍为 400）**

这些接口的 Problem 都是 `status=400`、`code=BUSINESS_CHECK_FAILED`，`detail` 以固定中文前缀开头，后接 `: <ID>`。Web 可以按 `code=BUSINESS_CHECK_FAILED` 且 `detail` 以下列前缀开头，判定为「未找到」：

| 详情 | BFF 路径 | detail 前缀 |
|---|---|---|
| 清算账户 | `GET /clearing-accounts/{id}`（BFF 聚合，原样透传 Owner detail） | `清算账户不存在: ` |
| 银行账户 | `GET /cash-accounts/{id}` | `银行账户不存在: ` |
| 渠道账单 | `GET /channel-statements/{id}`（`/lines` 同） | `渠道账单不存在: ` |
| 对账异常 | `GET /recon-exceptions/{id}`（BFF 聚合，原样透传 Owner detail） | `对账异常不存在: ` |
| 银行对账单流水列表 | `GET /bank-statements/{id}/lines` | `银行对账单不存在: ` |

这几个都没有专用错误码。如果以后要统一改成 404 + 专用错误码，需要另行变更合同。

## 4. Platform 块 126

| ID | 内容 |
|---|---|
| 900000000000126301 | 菜单 `OPERATIONS_FINANCE_RECON_CHANNEL_LINES` 渠道流水：父资源 123301（对账），排序 45（平台流水 40 与银行对账 50 之间），权限 123101，DISABLED |
| 900000000000126401 | `operations-finance-recon-channel-line-search` GET `/api/operations/finance/reconciliation/v1/channel-statement-lines` |
| 900000000000126402 | `operations-finance-recon-bank-statement-detail` GET `…/bank-statements/{bankStatementId}` |
| 900000000000126403 | `operations-finance-recon-bank-statement-line-detail` GET `…/bank-statement-lines/{bankStatementLineId}` |
| 126501–126506 | Header：每条路由 `authorization` CORS_REQUEST_ALLOWED + `origin` FORWARD_REQUEST，同块 123 的 GET 路由 |
| 126001–126010 | 查询参数白名单：只给 401 配 10 个参数 |
| 126701–126718 | 限流：每条路由 6 行，同块 123 的 GET 路由（共享 300/分钟、并发 50；IP 30/分钟、并发 3；主体 30/分钟、并发 2） |

- 3 条路由都是 HUMAN_PERMISSION、权限 123101 `FINANCE_RECONCILIATION_READ`、Body 上限 0、全部 DISABLED。共 38 行。没有新增权限，也没有新增角色授权。
- **占用核对（2026-10-21，只读 SELECT）：** 块 126 的 ID 段以及 3 个路由码、1 个资源码，在本文件、本机 `mor_platform_v2e2e`、本机 `mor_platform`、mor-test `mor_platform` 中均为 0 行。
- **父项与权限：** 父菜单 123301 和权限 123101 在 `mor_platform_v2e2e` 和 mor-test 中存在。本机 `mor_platform` 没有注册块 123，要在这个库激活，必须先执行块 123。

## 5. 验证（JDK 25，`paytech/` 下）

| 命令 | 结果 |
|---|---|
| `-pl reconciliation/paytech-reconciliation-{api,core,app} install -DskipTests` | 成功 |
| `-pl reconciliation/paytech-reconciliation-test verify` | **100/100 通过**：新增 ReconciliationDetailQueryTest 6 例、PersistenceMappingTest 6 例（+1），以及 ArchitectureAutoTest、ModuleBoundaryTest。测试的 AfterEach 已清空库、Redis 前缀与临时队列 |
| `-f operations-console-bff/pom.xml -pl …-test test -Dtest=ReconciliationBrowserAutoTest,OperationsConsoleBffApplicationAutoTest,OperationsHttpBodyNamingAutoTest` | **152/152 通过**（ReconciliationBrowserAutoTest 99 例） |
| `-pl tools/arch-rules test` | 63/63 通过 |
| `-f platform-support/pom.xml -pl paytech-platform-support-access-control -am compile`，以及能力清单唯一性检查 | 通过：48 个组件、64 个动作，无重复；新键排在 PLATFORM_TXNS 之后 |
| `operations-console-web` 的 `financeWorkflow.test.ts`（只运行，没有修改 Web 文件） | 10/10 通过。Web 线已经加了同一个 componentKey，「能力清单与 Platform JSON 逐项一致」通过 |
| `node --test scripts/pdmaner/platform-support-schema.test.mjs` | 9 例中 4 例失败，均为既有问题：表数 55、凭据 Scope 索引、Client API 127 行比对、Notification 增量。原因是其他会话在工作区改了 Platform 模型和 Schema（D 报告时同样有 4 例失败），与块 126 无关 |

**构建环境说明：** ops-bff 依赖的工作区源码比 `~/.m3` 新（其他会话把 externalapi 移到了 channel-connector-api，商务模板也有改动），所以先把 channel-connector-api、commercial-api、platform-support-api 按当前源码 install 到 `~/.m3`（-DskipTests，没跑这些模块的测试），再构建 ops-bff app。

**没有跑的：** ops-bff 全模块（1900+ 例；其中商务会话正在修改的测试不属于本线）；platform-support 的连库测试模块（共享本机库，且该模块有其他会话的大量未提交改动）；全仓 reactor。

## 6. 偏差与风险

1. **块号：** 125 → 126（见开头），各脚本的头注释也写明了这一点。
2. **既有接口的「不存在」仍返回 400**（按主会话决定），只有两个新的按 ID 读取接口返回 404；Web 的映射见第 3 节。
3. **索引：** 只按外部交易号查、或不带清算账户条件时，没有以 `external_transaction_id` / `transaction_date` 开头的索引，会扫描账单行表并 filesort；带 `clearingAccountId` 时可以用现有索引的前缀。本期不改表，数据量上去后需要另立变更加索引。
4. **渠道流水每页上限 100**（技术方案要求）；其他列表仍允许 200。
5. **渠道流水不返回 `rawAttributes`**（列表视图不需要）；需要时从账单详情页的行接口读取。
6. **激活脚本要递增角色版本：** 块 126 没有授权变化，但新菜单需要会话重新计算导航，所以激活脚本仍递增 PLATFORM_ADMIN（900000000000000510）的角色版本，与块 123 的做法一致。

## 7. 后续（阶段 6—7，另行授权）

- mor-test 需要替换的镜像：reconciliation、operations-console-bff、operations-console-web，以及 platform-support（能力清单在 access-control JAR 中）。
- SQL：先执行块 126（registration-dml.sql 的内容，以 `platform-support-initial-data.sql` 为准），再执行 `test-activate.sql`。执行前再查一次块 126 未占用。
