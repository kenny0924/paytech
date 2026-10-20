# 任务卡 B 报告：billing（D1 / B1 / B2）

日期：2026-10-20　工作区：`/Users/kenny/Desktop/Workspace/Code-MoR-fin3`（未提交、未建分支）

## 开工说明（WRITE_API_EXAMPLE 第 1 节）

已读 `DEVELOPMENT_CONVENTIONS.md`、`JAVA_PROJECT_STRUCTURE.md`、`examples/WRITE_API_EXAMPLE.md`、`UNIT_TEST_GUIDE.md`、`examples/UNIT_TEST_EXAMPLE.md`、`DATABASE_DESIGN_GUIDE.md`（worktree 版本），以及本需求 `requirement.md`、`technique.md`（D1、B1、B2、第 6 节）。

- 作废入口由 billing 模块 `VoidBillingFactProcessor` 处理（`AbstractLockProcessor`，与批准共用 `BillingLocks.billingFact(factId)` 的 LOCK）；Context 沿用 `BillingFactContext`（事实头、引用、明细行）。
- 原请求分支：事实已是 `VOIDED` 直接返回原事实；新请求只允许 `DRAFT`。
- 本地原子写入：一条条件更新 `status='DRAFT' AND fact_version=0 → VOIDED`，行数≠1 为 409；不登记补偿、无外部调用、无事务外步骤。
- 登记 / 批准只把操作人来源从 Body 改为 Header，Processor、事务与发布流程不变。
- 管理列表是单表只读查询，不建 Processor、不开事务，Controller 委托 `BillingFactQueryService`。

## 最终 API 签名

```java
// BillingFactCommandApi  @HttpExchange("/billing/v1/facts")
@PostExchange
ResponseEntity<BillingFactResponseBody> submitBillingFact(
    @RequestHeader("X-Gateway-Actor-Subject") @Pattern(regexp = "[1-9][0-9]{0,18}") String operatorId,
    @RequestHeader("X-Gateway-Session-Id") String sessionId,
    @Valid @RequestBody SubmitBillingFactRequestBody body);            // body 已删除 makerId

@PostExchange("/{factId}/approve")
BillingFactResponseBody approveBillingFact(
    @RequestHeader("X-Gateway-Actor-Subject") @Pattern(regexp = "[1-9][0-9]{0,18}") String operatorId,
    @RequestHeader("X-Gateway-Session-Id") String sessionId,
    @PathVariable("factId") @Pattern(regexp = "[1-9][0-9]{0,18}") String factId);   // 无请求体

@PostExchange("/{factId}/void")
BillingFactResponseBody voidBillingFact(
    @RequestHeader("X-Gateway-Actor-Subject") @Pattern(regexp = "[1-9][0-9]{0,18}") String operatorId,
    @RequestHeader("X-Gateway-Session-Id") String sessionId,
    @PathVariable("factId") @Pattern(regexp = "[1-9][0-9]{0,18}") String factId);   // 无请求体

// BillingFactQueryApi 新增
@GetExchange("/search")
PagingResponseBody<BillingFactSummaryResponseBody> searchBillingFacts(
    @RequestParam("entityNo") @Pattern(regexp = "[A-Z]{2}(00[1-9]|0[1-9][0-9]|[1-9][0-9]{2})") String entityNo,
    @RequestParam("currency") @Pattern(regexp = "[A-Z]{3}") String currency,
    @RequestParam(value = "clientId", required = false) @Pattern(regexp = "[1-9][0-9]{0,18}") String clientId,
    @RequestParam(value = "status", required = false) @Pattern(regexp = "DRAFT|APPROVED|SUPERSEDED|VOIDED") String status,
    @RequestParam(value = "factType", required = false) @Pattern(regexp = "[A-Z][A-Z_]{0,63}") String factType,
    @RequestParam(value = "createdFrom", required = false) Instant createdFrom,   // 含
    @RequestParam(value = "createdTo", required = false) Instant createdTo,       // 不含，须晚于 createdFrom
    @RequestParam(value = "page", required = false) Integer page,
    @RequestParam(value = "size", required = false) Integer size);
```

`BillingFactSummaryResponseBody`：`factId`、`factType`、`sourceReference`、`clientId`、`status`、`grossAmount`（按币种精度补齐）、`makerId`、`checkerId`、`createTime`、`approvedTime`。

响应语义：
- 操作人 Header 缺失或不符合 `[1-9][0-9]{0,18}`、会话 Header 缺失：400 `INVALID_REQUEST`。
- 作废：200 返回 `VOIDED` 事实；已作废时返回原事实（任一操作人）；事实不存在、`APPROVED` / `SUPERSEDED`：400 `BUSINESS_CHECK_FAILED`（"只能作废草稿计费事实"）。
- 批准已作废事实：400（"计费事实已作废，不能批准"）；已作废事实不能作为被替代事实（沿用"被替代事实必须 APPROVED"）。
- 检索：按 `id` 降序，`LIMIT/OFFSET` + 同条件 `COUNT`；状态 / 类型不在目录、条数不在 10/20/50/100/200、时间区间颠倒：400 `INVALID_REQUEST`。
- 三个命令成功后 Controller 记一条日志 `action / actor / session / factId`。

## 改动文件

`paytech/billing/` 下：

- API：`BillingFactCommandApi`、`BillingFactQueryApi`、`enums/BillingFactStatus`（新增 `VOIDED`）、`request/SubmitBillingFactRequestBody`（删 `makerId`）、删除 `request/ApproveBillingFactRequestBody`、新增 `response/BillingFactSummaryResponseBody`。
- App：`ext/rest/BillingFactController`、`ext/rest/converter/BillingFactConverter`。
- Core：新增 `service/processor/VoidBillingFactProcessor`、`service/processor/request/VoidBillingFactRequest`、`domain/BillingFactSearchCriteria`；修改 `ApproveBillingFactProcessor`（拒绝 VOIDED）、`ApproveBillingFactRequest` / `SubmitBillingFactRequest`（注释）、`BillingLocks`（注释）、`BillingFactService(Impl)`（`voidDraft`）、`BillingFactQueryService(Impl)`（`searchFacts`）、`BillingFactRepository(Impl)`（`voidDraft`、`findSearchPage`、`countSearch`）、`BillingFactMapper` + XML（`voidDraft`、`selectSearchPage`、`countSearch`，筛选从 `entity_no + currency` 开始，命中 `idx_fact_book_oe`）。
- Schema：`docs/database/billing.pdma.json`、`docs/database/sql/billing-schema.sql`、`paytech-billing-test/src/test/resources/init/ddl.sql`（`t_billing_fact.status` 注释加 `VOIDED`）；新增 `docs/database/sql/changes/2026-10-20-billing-fact-void.sql`（只改注释，含前提 / 影响 / 回退）。
- 文档：`README.md` 接口表。
- 测试：`AbstractBillingFactApiTest`（Header 常量、`operatorHeaders`、`voidFact`）、`MockTestBase`（带 Header 的 `post`）、`BillingFactRequestBuilder`（编制人改为 Header 值）、`BillingTestMapper`（读 `update_time`）、`SubmitBillingFactTest`、`ApproveBillingFactTest`；新增 `BillingFactSearchTest`（4 例）、`VoidBillingFactTest`（5 例）。

## 对应 AC

| AC | 实现与验证 |
|---|---|
| AC-04 | `searchBillingFacts` 含 DRAFT / APPROVED / SUPERSEDED / VOIDED；`BillingFactSearchTest.test_filtersIncludeDrafts`（状态、Client、类型、其他 OE 排除、降序）、`test_pagination`（11 条两页、总数、越界空页）、`test_createdTimeRange`、`test_invalidParameter` |
| AC-06（billing 部分） | 作废后不入账、不能再批准：`VoidBillingFactTest.test_normalFlow`（无补偿任务、无事件）、`test_voidedCannotBeApproved`、`test_nonDraftRejected`、`test_repeatedVoidReturnsOriginal` |
| AC-07（billing 部分） | 请求体不再接受 `makerId` / `checkerId`，操作人只来自 Header；缺失 / 畸形 Header 400：`SubmitBillingFactTest.test_invalidParameter`、`ApproveBillingFactTest.test_checkerMustDifferFromMaker`、`VoidBillingFactTest.test_invalidParameter`；Maker ≠ Checker 规则不变 |

下游影响核对：account 只经 `BillingFactRecorded` 与 `listBillingFacts`（SQL 限 `APPROVED/SUPERSEDED`）取事实；ledger 只读详情与 `getBillingFactProgress`（同样限 `APPROVED/SUPERSEDED`）；发布 Handler 只处理 `recorded()`（APPROVED/SUPERSEDED）。VOIDED 从不登记补偿、从不发布，对 account / ledger 消费与读取无影响。

## 执行的命令与结果

1. 编译：`./mvnw -o -q -pl billing/paytech-billing-app -am -DskipTests package` — 成功。
2. 测试库：用 root 从 worktree `paytech-billing-test/src/test/resources/init/ddl.sql`（库名替换为 `mor_billing_fin3`，副本在 scratchpad）建 `mor_billing_fin3`（11 张表）并授予 `billing_app` 该库 DML；随后对该库执行 `changes/2026-10-20-billing-fact-void.sql` 验证语法 — 成功，列注释与 Schema 一致。vhost `/mor-fin3` 已存在且已授权，未改动。
3. 测试：`BILLING_TEST_DB_URL=jdbc:mysql://127.0.0.1:13306/mor_billing_fin3?... MOR_RABBITMQ_VHOST=/mor-fin3 REDIS_KEY_PREFIX=billing-fin3 ./mvnw -o -pl billing/paytech-billing-test -am verify -Dpaytech.local-environment.dir=.../local-environment -Dredis.key-prefix=billing-fin3 -Dtest='com/paytech/billing/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false` — **80 个测试，0 失败，0 错误**（Submit 14、Approve 6、Query 8、Search 4、Void 5、Handler 5、Repository 4、Application 12、ModuleBoundary 4、Architecture 1、Invariants 7、ChannelCost 10）。核对过：`mor_billing_fin3.t_billing_fact` 更新时间为本次运行，`mor_billing` 未被写入。

未运行：ledger / account 测试（不在本卡范围）；`npm --prefix requirements-site test`（本卡未改 requirements-site）。

## 偏离设计与需注意事项

- **ledger 测试编译受影响（需卡 A 处理）**：`paytech/ledger/paytech-ledger-test/.../mock/external/MockBillingFactApi.java` 实现 `BillingFactQueryApi`，新增 `searchBillingFacts` 后需补一个记为意外调用的 override。已把完整代码片段发给 fin3-a-ledger；卡 A 回复已补上该 override，并在同一 reactor（-am）基于本卡 billing-api 跑完 ledger 全套测试：311 个，0 失败（卡 A 执行，本卡未复跑）。
- 作废人不落库：technique 规定不加字段，作废人只在 Controller 命令日志中留痕；`checker_id` 保持为空，没有借用它表示作废人。
- 作废不限定操作人：requirement Q3 写"只能由制单人或复核人操作"，technique B2 没有这条限制，而草稿还没有复核人，billing 也无法判断谁有复核权限。本卡按 technique 实现：任一已认证操作人均可作废，由 Operations 路由（MANAGE 权限，D2）控制。若要只允许制单人作废，需另行确认。
- 已作废事实的来源引用仍然占用：同一 `sourceSystem + sourceReference`、同样内容再次提交，会返回那条已作废的原事实（200）。BFF 每次登记都生成新的 `OPS-` + UUID，所以不受影响。
- `X-Gateway-Session-Id` 只校验必须存在、并记入日志，不校验格式（与 ledger 基础资料接口一致）。

## 未完成项

无（本卡范围内）。BFF 转发、Platform 路由与 Header / 查询参数白名单（`search` 的 9 个查询参数）属于卡 C / D。

## 追加（2026-10-20）：BillingFactErrorCode（technique D4 修订）

背景：Foundation HTTP Client 不向调用方转发 `BUSINESS_CHECK_FAILED` 的 detail，控制台需要已登记的错误码才能显示具体提示。

新增 `paytech-billing-api` `api/enums/BillingFactErrorCode`（`CodeMessageEnum`，HTTP 400，消费者为 Operations BFF / Web）：

| Code | 默认消息 | 抛出位置 |
|---|---|---|
| `SELF_REVIEW_FORBIDDEN` | 制单人不能复核自己提交的计费事实 | `ApproveBillingFactProcessor.buildApproveContext`（复核人 = 编制人） |
| `FACT_NOT_DRAFT` | 只能批准或作废草稿计费事实 | 批准已作废事实、批准已由其他复核人批准的事实（`getOriginalApproval`）、作废 APPROVED / SUPERSEDED（`VoidBillingFactProcessor.checkDraft`） |
| `FACT_LINE_INVALID` | 明细行不合法：金额组件、收付方、目标余额桶或金额精度不符合规则 | `BillingFactInvariants` 的全部行级规则（人工调整组件、目标余额桶用于非 Owner 收方、成本调整组件 / 方向 / 收付方 / 目标余额桶、金额非正、付方等于收方、收付方与头部不一致、合作方组件缺少二元组、平台服务费收方不是 Finance Entity）；`SubmitBillingFactProcessor.checkAmountScales`（金额超过币种精度） |
| `FACT_SUPERSEDE_INVALID` | 被替代的计费事实不存在或不可替代 | `SubmitBillingFactProcessor.checkSupersededFact`；`ApproveBillingFactProcessor.buildApproveContext`（被替代事实已不是 APPROVED） |

全部以 `new FailException(code)` 抛出（与 ledger `LedgerSetupErrorCode` 一致），Problem 中 `detail` 为默认消息，不再带行号等上下文。

保持不变的拒绝：
- 公共 `BUSINESS_CHECK_FAILED`：事实不存在、来源引用内容不同、头部规则（缺少 Client、人工调整带来源事实、成本调整缺少来源事实或 CLEARING_ACCOUNT、合作方二元组不完整）、币种未启用、来源支付事实不合格。
- `INVALID_REQUEST`：代码不在目录（Processor `validate` 中的 `BillingCodes.parse`）、引用类型不被事实类型允许、Header 与参数格式。卡上写的 "processors" 里的目录检查，本卡理解为业务规则检查；代码解析错误属于参数错误，页面下拉也不会产生，因此没有改。
- 409：条件更新未命中。

偏离：金额超过币种精度也归入 `FACT_LINE_INVALID`。这是 Processor 里唯一的行级业务检查，但默认消息没有提到金额精度。若希望它保持 `BUSINESS_CHECK_FAILED`，改回一行即可。

其他改动：`BillingFactInvariants` 删除两个只在消息中使用的参数（`checkCostLine` 的 fact、`checkPartyIdentity` 的 line）；`BillingFactCommandApi` 的 `@return` 列出各错误码；README 增加错误码说明。

测试：`BillingResponseChecker.assertFailCode(code)` 断言 400、Problem `code` 与默认消息 detail。改用错误码断言的有 `SubmitBillingFactTest` 13 处、`ApproveBillingFactTest` 4 处、`VoidBillingFactTest` 4 处；`BillingFactInvariantsTest` 中 11 处行级断言改为 `assertLineInvalid`，断言 `FailException` 且 Code 为 `FACT_LINE_INVALID`。

复跑：与上文相同的命令（`mor_billing_fin3`、`/mor-fin3`、`billing-fin3`），**80 个测试，0 失败，0 错误**。

### 决定（team-lead，2026-10-20）

- 金额超过币种精度保留在 `FACT_LINE_INVALID`，默认消息改为"明细行不合法：金额组件、收付方、目标余额桶或金额精度不符合规则"（上表已更新）；上一节"偏离"一项按此关闭。
- 接受 detail 只返回默认消息、不再带行号。
- 测试按 `code.getMessage()` 断言，没有写死消息字面量。复跑同一命令：**80 个测试，0 失败，0 错误**。
