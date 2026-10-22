# EXP-A 报告：Payment 终止原因与按来源查询

> 执行代理报告，2026-10-22。工作目录 `/Users/kenny/Desktop/Workspace/Code-MoR-expiry`（detached HEAD，基线 `42879401`），未提交、未推送、未执行共享环境 SQL、未部署。

## 1. 结论

- 终止接口按原因写 Intent 终态：`ORDER_CANCELLED`、`OPERATING_ENTITY_CLOSED` 写 `CANCELLED`，`ORDER_EXPIRED` 写 `EXPIRED`；已终态重复终止返回 `ALREADY_TERMINATED` 且不改写（AC-11）。
- 新增 `GET /payment-execution/v1/intents/by-source/{sourceType}/{sourceId}?clientId=`，不存在与跨 Client 均 404 `NOT_FOUND`（AC-02 的定位前提）。
- 门禁：payment 模块 `auto` 78 例全部通过；全 reactor `test-compile` 通过。
- 有两处与任务卡测试描述不同，均按现有合同处理，需主会话确认，见第 6 节。

## 2. 已读规范与合同

- `AGENTS.md`（根）与 `paytech/payment/AGENTS.md`
- `agent-docs/DEVELOPMENT_CONVENTIONS.md`
- `agent-docs/JAVA_PROJECT_STRUCTURE.md`
- `agent-docs/examples/WRITE_API_EXAMPLE.md`
- `agent-docs/UNIT_TEST_GUIDE.md`
- `agent-docs/examples/UNIT_TEST_EXAMPLE.md`
- `agent-docs/decisions/2026-09-17-test-dto-mock-lifecycle.md`
- `paytech/payment/docs/development/processor-conventions.md`
- 任务卡 `tasks/cards.md`（EXP-A、第 2 节门禁）、`technique.md` 第 1.2、3.3.1、3.3.2、3.4、3.6 节、`requirement.md` AC-02 / AC-11

## 3. 改动文件

路径相对 `paytech/payment/`，另有一处 acquiring 测试文件。

**新增**

| 文件 | 说明 |
|---|---|
| `paytech-payment-core/.../execution/core/enums/TerminationReason.java` | 三值枚举，`targetStatus()` 返回对应 `IntentStatus` |
| `paytech-payment-core/.../execution/service/processor/GetIntentBySourceProcessor.java` | `AbstractProcessor`，只读、无锁、无事务 |
| `paytech-payment-core/.../execution/service/processor/request/GetIntentBySourceRequest.java` | sourceType、sourceId、clientId |
| `paytech-payment-test/.../auto/execution/TerminateIntentTest.java` | 11 例，HTTP 入口 |
| `paytech-payment-test/.../auto/execution/GetIntentBySourceTest.java` | 4 例，HTTP 入口 |

**修改**

| 文件 | 说明 |
|---|---|
| `paytech-payment-api/.../execution/PaymentIntentApi.java` | 新增 `getIntentBySource`，校验与 `getIntent` 一致 |
| `paytech-payment-api/.../execution/request/TerminateIntentRequestBody.java` | `reason` 的 `@Pattern` 扩为三值，JavaDoc 同步 |
| `paytech-payment-app/.../ext/rest/converter/TerminateIntentConverter.java` | Body `reason` 转 `TerminationReason` |
| `paytech-payment-app/.../ext/rest/converter/GetIntentConverter.java` | 新增 `convertBySourceRequest`，响应复用 `convertResponse` |
| `paytech-payment-app/.../ext/rest/execution/PaymentIntentController.java` | 新增 `getIntentBySource` 委托 |
| `paytech-payment-core/.../service/processor/request/TerminateIntentRequest.java` | 新增 `reason` |
| `paytech-payment-core/.../domain/TerminateIntentContext.java` | 新增 `reason`；决定逻辑不变 |
| `paytech-payment-core/.../service/processor/TerminateIntentProcessor.java` | 构造 Context 时传入原因 |
| `paytech-payment-core/.../service/impl/PaymentExecutionServiceImpl.java` | 按 `reason.targetStatus()` 调仓储 |
| `paytech-payment-core/.../domainservice/repository/PaymentIntentRepository.java` 与 `impl/PaymentIntentRepositoryImpl.java` | `terminateIntent(intent, IntentStatus targetStatus)`，成功后同步内存对象为实际终态 |
| `paytech-payment-core/.../core/dal/mapper/PaymentIntentMapper.java` 与 `resources/mapper/PaymentIntentMapper.xml` | `SET intent_status = #{status}`（`CodeEnumTypeHandler`），WHERE 不变 |
| `paytech-payment-core/.../service/processor/GetIntentProcessor.java` | 响应组装改调 `GetIntentResponse.of` |
| `paytech-payment-core/.../service/processor/response/GetIntentResponse.java` | 新增静态 `of(PaymentIntent)`，两个查询共用 |
| `paytech-payment-test/.../test/dal/PaymentTestMapper.java` | `intent()` 多返回 `version`；新增夹具更新 `authorizeAwaitingCapture` |
| `paytech-payment-test/.../test/mocker/PaymentDataMocker.java` | 新增夹具方法 `authorizedAwaitingCapture` |
| `paytech/acquiring/paytech-acquiring-test/.../mock/external/MockPaymentIntentApi.java` | 新增 `getIntentBySource`，任何调用抛 `AssertionError`，与该类现有 `getIntent` 同样式；EXP-B 改为 DTO |

## 4. 接口与数据变更

- **终止**：路径、请求字段、响应字段和 `decision` 五值不变。`reason` 新增允许值 `ORDER_EXPIRED`。响应 `status` 为本次实际终态。`ALREADY_TERMINATED` 判断未改，已 `CANCELLED` 或 `EXPIRED` 都算。
- **按来源查询**：`sourceType` 校验 `[A-Z][A-Z0-9_]{0,63}`；`sourceId`、`clientId` 校验同 `getIntent`。响应复用 `GetIntentResponseBody`。实现复用已有 `PaymentIntentRepository.findIntentBySource`，唯一性由 `uk_payment_intent_source` 保证。
- **DDL / DML**：无。Mapper 只改 `terminateIntent` 的 SET 子句。
- **锁与事务**：终止仍用 Intent 资源锁 TRY_LOCK、单 Service 单事务；按来源查询不加锁、不开事务。

## 5. 测试命令与结果

公共前缀（在 `paytech/` 下执行；凭据只经 Surefire 锚点读取主工作区 `local-environment/.env.local`，未打印）：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
ANCHOR=-Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment
```

| 步骤 | 命令 | 结果 |
|---|---|---|
| 基线（生产代码未改） | `./mvnw -o -pl payment/paytech-payment-test -am test -Dtest=TerminateIntentTest -Dsurefire.failIfNoSpecifiedTests=false $ANCHOR` | 8 通过 / 0 失败 |
| 新增两类 | 同上，`-Dtest='TerminateIntentTest,GetIntentBySourceTest'` | 15 通过 / 0 失败 |
| payment 全部 auto | 同上，`-Dtest='com.paytech.payment.auto.**'` | 78 通过 / 0 失败 / 0 跳过 |
| 全 reactor 编译 | `./mvnw -o test-compile` | BUILD SUCCESS，118 个模块 |

payment 全量明细：StoredCardTest 8、CardStorageTest 14、ArchitectureAutoTest 1、GetSubmissionTest 7、TerminateIntentTest 11、GetIntentBySourceTest 4、PayRiskEvaluationTest 20、PayCardChannelTest 8、PaymentSucceededPublishTest 5。

基线首轮有 2 例失败，均为我的预期写错，不是既有缺陷：Intent 初始版本是 1 而非 0；Body 字段非法由公共 Problem 映射为 422 而非 400。修正断言后基线 8 例全过，再改生产代码。

**TerminateIntentTest 场景**：无尝试三种原因各一例（CANCELLED、CANCELLED、EXPIRED，版本加一）；已取消再以到期原因终止、已过期再以取消原因终止（均 ALREADY_TERMINATED，行内容与版本不变）；活动尝试结果未知 → PENDING；已授权未捕获 → REVERSAL_REQUIRED；已收款 → ALREADY_PAID；不存在 → 404；跨 Client → 400；非法原因 → 422。

**GetIntentBySourceTest 场景**：命中；来源无 Intent 404；跨 Client 404；小写 sourceType → 400 `INVALID_REQUEST`。

**REVERSAL_REQUIRED 夹具**：AUTHORIZE 动作尚未接入渠道（`AuthorizeActionActor.execute` 抛 `ErrorException`），无法经 HTTP 形成已授权状态。测试先经真实 pay 得到结果未知的处理中尝试，再由 TestMapper 把该尝试改为 `authorized_amount = 金额`、`active_action_id = NULL`。这与生产 `authorizeCompleted` 成功后的落库事实一致，只在测试侧。

未使用 Mockito Stub 或 `@MockBean`。

## 6. 偏离任务卡之处，需主会话确认

1. **跨 Client 终止仍返回 400 `BUSINESS_CHECK_FAILED`，不是卡上写的 404。** 现状 `TerminateIntentProcessor.buildContext` 对归属不符抛 `BizCheckFailException`。技术方案 3.3.2 没有列出这项合同变更，任务卡又要求基线在未改代码时跑通，所以我保留现状并按 400 断言。另一个原因是 EXP-B 设计把终止的 404 映射为“本地取消”。若跨 Client 也回 404，acquiring 侧的归属错配会被当成没有 Intent 而本地取消。如需改为 404，只需改该处异常并调整一条断言。
2. **非法 reason 返回 422 `VALIDATION_ERROR`，不是卡上写的 400。** 这是 Foundation 公共 Problem 对 Body 字段错误的统一映射，payment 不应单独改。Path / Query 参数错误返回 400 `INVALID_REQUEST`，按来源查询的非法 sourceType 即如此。
3. **响应组装的复用方式。** 方案写“复用 `GetIntentProcessor.processResponse`”。新 Processor 调用另一个 API Processor 的辅助方法违反开发规范第 5.2 节，所以把组装移到 `GetIntentResponse.of(PaymentIntent)`，两个 Processor 共用，没有复制代码。
4. **acquiring 测试文件的最小改动。** 新增接口方法会让 `MockPaymentIntentApi` 编译失败。为保住 reactor `test-compile`，加了一个拒绝调用的实现；EXP-B 应替换为 DTO 行为。

## 7. WRITE_API_EXAMPLE 第 11 节自查

| 检查项 | 结果 |
|---|---|
| API 与 Body 最小合同、class、注释、Validator | 符合。`reason` 用 `@Pattern` 扩值；新接口三个参数注解校验，JavaDoc 写明 200 / 404 语义 |
| Controller / Converter 仅适配；Processor 在所属模块 | 符合。Controller 只做转换与委托；两个 Processor 在 `paytech-payment-core` 的 `service/processor` |
| Processor 查本模块 Repository，不调 Mapper、不逐表写 | 符合。终止仍由 Service 写；按来源查询只读 Repository |
| Context 持有本模块 DataObject，无同义 ID | 符合。`TerminateIntentContext` 只新增 `reason` 枚举，终态由枚举派生，不另存 |
| Service 不重复查询、不调外部 | 符合。Service 只按 `reason.targetStatus()` 调仓储 |
| 本地原子写与事务位置 | 符合。单事务内单表条件更新；事务内无外部调用 |
| Repository 条件 SQL、行数检查、成功后同步原对象 | 符合。WHERE 不变，行数不为 1 抛 `ConcurrentRequestException`，成功后写入实际终态、版本加一 |
| 锁 | 符合。终止沿用 Intent TRY_LOCK；按来源查询无锁 |
| 原请求比较、终态重放 | 不适用原提交比较；已终态重复终止按当前事实返回 ALREADY_TERMINATED，已测 |
| Gateway 请求幂等 | 不涉及。未新增 Idempotency-Key 或命令幂等组件 |
| View | 不适用。单表查询，复用内部 Response |
| 无生产 Mock、伪造成功、无消费者字段或多余抽象 | 符合。新增类型均有当前消费者；acquiring 的 Mock 改动只在测试侧 |
| 实际验证 | 见第 5 节。并发竞争下的终止 CAS 冲突未新增测试，WHERE 条件和行数检查未改动 |

## 8. 未完成与提示

- 卡内工作已全部完成。未进入 EXP-B 及之后的卡。
- 第 6 节第 1 项需要主会话决定是否改为 404。
- 路由注意（未验证）：`/intents/by-source/{sourceType}/{sourceId}` 与 `/intents/{intentId}/submissions/{sourceSubmissionId}` 段数相同。只有 sourceType 恰为小写 `submissions` 时两者都能匹配，而该值本身不合法。正常调用不受影响，我没有为此编写测试。
- 工作目录的 `local-environment/.env.local` 不存在。测试通过 `-Dpaytech.local-environment.dir` 指向主工作区，只读取，未复制。

## 9. 新基线返工（main `22ede435`，含 3DS 合入 `de4f7ba4`）

**结论：** 新基线上 payment `auto` 111 例全部通过。只改了测试，生产代码未动。

**原因（已按源码核实）：** 3DS 合入后，PAY 结果事务把尝试保存为 PROCESSING 或 UNKNOWN 时，会在同一事务登记 `PAY_RESULT_QUERY`（`PayActionActor` 第 194 行 `events.register(PayResultQueryHandler.buildEvent(...))`）。提交后补偿任务立即执行一次。`PayResultQueryHandler` 调用 `ConfirmPayResultProcessor`，后者持有同一个 Intent 资源锁。`TerminateIntentProcessor` 对该锁使用 TRY_LOCK，所以测试在 pay 返回后马上终止，会与首轮查单竞争，得到 409 `CONCURRENT_REQUEST`。`test_authorizedReversalRequired` 和 `test_activeAttemptPending` 都先经结果未知的 pay 构造夹具，两者有同样的竞争，只是前者这次碰上了。

**修复（只改测试侧）：** `TerminateIntentTest` 改为继承 3DS 合入的 `AbstractPayResultTest`。两个用例改用其 `unknownPay()`：先提交结果未知的 pay，再有上限地等待该尝试的 `PAY_RESULT_QUERY` 进入 ERROR，即首轮已执行完毕、等待退避重试。之后才读取基线、修改授权夹具并终止。这与 `RefreshSubmissionTest` 等 3DS 测试避开同一竞争的方式一致。没有加 sleep，没有删除补偿任务，生产代码未改。

**验证：**

| 运行 | 结果 |
|---|---|
| payment `auto` 全量，前台执行，命令同第 5 节 | 111 通过 / 0 失败 / 0 跳过，BUILD SUCCESS |
| `TerminateIntentTest` 单独再跑两次 | 两次均为 11 通过 |

全量明细：StoredCardTest 8、CardStorageTest 22、PayResultQueryHandlerTest 4、RefreshSubmissionTest 11、ChannelPaymentNotifiedListenerTest 4、ArchitectureAutoTest 1、GetSubmissionTest 7、TerminateIntentTest 11、GetIntentBySourceTest 4、PayRiskEvaluationTest 21、PayCardChannelTest 12、PaymentSucceededPublishTest 6。

**中途的环境干扰：** 23:04 前后有一轮 `TerminateIntentTest` 出现多例“专用库非空”。当时 `Code-MoR-billing-b` 会话正在对同一个 `mor_payment` 运行 payment 测试，残留行数在用例之间变化（7、5、6），这是共享库并发占用，与本次改动无关。该会话结束后，在前台重跑的全量未再出现库非空。我没有清理数据库。
