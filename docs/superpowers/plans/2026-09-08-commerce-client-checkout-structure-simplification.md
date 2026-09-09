# Commerce Client Checkout 结构简化实施计划

> 2026-09-09 后继：本计划以“五阶段恢复不变”为前提的内容已由
> [`2026-09-09-commerce-checkout-recovery-simplification.md`](../../../agent-docs/decisions/2026-09-09-commerce-checkout-recovery-simplification.md)
> 取代，现行 Create Checkout 恢复检查点为 `CLAIMED -> ORDER_COMMITTED -> COMPLETED`。下文旧术语仅保留为历史执行输入，其余结构重构与 HTTP 测试组织决定继续有效。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在不改变 Commerce Client Checkout 外部合同、五阶段恢复、数据库写入集合和错误语义的前提下，拆开当前聚合过多职责的类，统一应用模型，按项目约定使用 Lombok，并让 Checkout 业务测试只从真实 HTTP 入口覆盖生产链。

**Architecture:** 保持 `paytech-commerce-client-api -> paytech-commerce-web -> paytech-commerce-test` 三模块。POST 由具体 `CreateCheckoutProcessor` 编排 `CheckoutCreationIdempotencyService`、`CheckoutCreationService`、四个 Owner Client 和短事务；GET 直接委托 `CheckoutQueryService`。Hasher、Snapshot Codec、Owner Command Converter 和 Prepared Aggregate Assembler 是单实现具体组件，不增加形式接口。Repository 只拥有 MyBatis、CAS、唯一冲突识别和 DataObject 映射。

**Tech Stack:** Java 25、Spring Boot 4.0.8、Spring MVC、MyBatis/MySQL、TransactionTemplate、Foundation RFC 8785/JCS 与 RFC 9457、Lombok、JUnit 5、RestTestClient、JaCoCo。

**Approved design:** [`docs/superpowers/specs/2026-09-08-commerce-client-checkout-structure-simplification-design.md`](../specs/2026-09-08-commerce-client-checkout-structure-simplification-design.md)

**Current status (2026-09-08):** 生产结构、Lombok、真实随机端口 HTTP 测试源码和
JaCoCo 非空 `>= 90%` 门禁已实现并通过编译；本结构阶段门禁为 92/92，后继
[`Commerce Client API 边界简化`](./2026-09-08-commerce-client-api-boundary-simplification.md)
补入 Header/JSON/文档门禁后当前数据库无关集合为 106/106。测试已移除曾经替换四个
生产 Client 的 `@Primary` ScenarioDriver、调用计数器和 CCT Barrier；当前可达场景直接使用生产
确定性 Local Mock。加载仓库本地环境凭据后已确认 MySQL 可连接，但 `mor_commerce` 尚未准备本切片
要求的 Schema；数据库型 HTTP 用例和覆盖率正向结果因此尚未执行。Owner 失败协议场景等待真实合同
和 Loopback Simulator，计划保持实施中。

## Execution rulings

- `commerce-service` 是独立 Git 仓库，现有 Checkout 首片尚未提交且与本重构重叠。为保留这些用户改动，本计划在其当前 `main` 工作树上串行、增量实施；不创建分支或 worktree，不重置文件。
- 用户尚未要求提交，本计划不执行 Git commit。与 Superpowers 模板中的频繁提交建议冲突时，以项目和用户的提交授权规则为准。
- 这是行为保持型重构：先建立/运行 HTTP 特征测试，再逐步改生产结构；不为内部 Helper、Service 或 Repository 新增直接行为测试。
- Java 测试不创建或迁移 Schema。需要本地 MySQL 的 HTTP 测试只检查前置条件并给出人工准备命令。
- 不修改 Java/OpenAPI Wire、数据库 Schema/Mapper SQL、Gateway、Nginx、Checkout Web 或部署状态。

## Task 1: 建立真实 HTTP 行为护栏和测试数据生命周期

**Files:**

- Replace: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/ext/http/clientapi/v1/checkout/ClientCheckoutHttpAdapterAutoTest.java`
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/ext/http/clientapi/v1/checkout/ClientCheckoutCreateHttpAutoTest.java`
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/ext/http/clientapi/v1/checkout/ClientCheckoutIdempotencyHttpAutoTest.java`
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/ext/http/clientapi/v1/checkout/ClientCheckoutQueryAndErrorHttpAutoTest.java`
- Create or modify shared HTTP support under: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/test/checkout/`
- Modify: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/test/repository/CommerceRepositoryTestMapper.java`
- Modify: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/test/repository/CommerceRepositoryTestFixture.java`

- [x] Convert the manual MockMvc/controller harness into `@SpringBootTest(webEnvironment = RANDOM_PORT)` tests using `RestTestClient` and the real Controller/Processor/Service/Repository/Mapper chain plus production deterministic Local Mock Clients.
- [x] Build valid HTTP requests only through the public path and required trusted headers; assert HTTP status/body/headers plus narrowly scoped persisted facts.
- [x] Add deterministic lookup and child-first cleanup by test-owned `clientOrderId`/idempotency caller scope rather than generated Snowflake IDs.
- [x] Characterize reachable behavior before structural edits: successful create, GET, default and explicit expiry, same-key replay, same-key/different-request conflict, permanent `clientOrderId` uniqueness, Client isolation, validation/RFC 9457, and expired URL hiding.
- [x] Run the focused HTTP tests and retain the baseline result. If local MySQL/password is unavailable, record that environmental blocker and continue with compile/static checks; do not apply SQL automatically.

## Task 2: Add Lombok only for pure assignment constructors

**Files:**

- Modify: `commerce-service/paytech-commerce-web/pom.xml`
- Modify production classes with pure dependency-assignment constructors under `paytech-commerce-web/src/main/java/com/paytech/commerce/`

- [x] Add `org.projectlombok:lombok` with `provided` scope and explicitly register it in `maven-compiler-plugin.annotationProcessorPaths`, leaving version ownership to the BOM.
- [x] Order injected fields by the project convention, add `@AllArgsConstructor`, and remove only constructors whose entire behavior is field assignment/null checking.
- [x] Keep explicit constructors that configure resources, initialize SecureRandom, call superclasses, validate values, copy collections, or derive state.
- [x] Compile `paytech-commerce-web` and rerun HTTP characterization tests when infrastructure is available.

## Task 3: Converge Checkout application/domain models and request hashing

**Files:**

- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domain/checkout/CheckoutCreationRequest.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domain/checkout/CheckoutCreationReceipt.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domain/checkout/CheckoutQueryResult.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domain/checkout/PreparedCheckout.java`
- Rename and modify: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/ext/rest/external/clientapi/v1/checkout/CheckoutWireMapper.java` to `CheckoutWireConverter.java`
- Modify: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/service/processor/checkout/CreateCheckoutProcessor.java`
- Replace interface/implementation pair with concrete: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/idempotency/CheckoutRequestHasher.java`
- Delete after migration: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/idempotency/impl/CheckoutRequestHasherImpl.java`

- [x] Map the Wire body once into immutable `CheckoutCreationRequest`; pass the idempotency key separately as Processor command metadata.
- [x] Make the concrete hasher project that one model into the unchanged version-1 semantic JSON Object, use Foundation canonical bytes, and calculate the identical SHA-256 value.
- [x] Move create receipt, query result, and prepared projection out of oversized Service interfaces while retaining all compact-constructor invariants.
- [x] Make Repository and Services share one `PreparedCheckout`; retain `PreparedCheckoutRecord` only as the MyBatis DataObject.
- [ ] Compile and rerun the HTTP tests to confirm hashes, responses, and persisted results are unchanged.

## Task 4: Extract idempotency serialization and narrow the business Service

**Files:**

- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/idempotency/CheckoutCreationIdempotencyService.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/idempotency/impl/CheckoutCreationIdempotencyServiceImpl.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/idempotency/CheckoutIdempotencySnapshotCodec.java`
- Delete after migration: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/idempotency/CommandIdempotencyService.java`
- Delete after migration: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/idempotency/impl/CommandIdempotencyServiceImpl.java`
- Modify: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/service/processor/checkout/CreateCheckoutProcessor.java`

- [x] Move request snapshot and success receipt encode/decode, canonical-byte generation, size bounds, and closed projection checks into the concrete codec.
- [x] Keep claim, lease, caller-scope/hash validation, legal stage transitions, stable failure completion, success completion, and replay in the renamed Service interface plus Impl.
- [x] 在尚未发布或写入目标 Schema 的 V1 首次落地前，将内部 Request Snapshot 定稿为 `{h,p}` 闭合文档：保留全部恢复字段并增加 Payload canonical SHA-256 完整性校验；外部语义请求 Hash 与 Response Snapshot 保持不变。
- [x] Reduce the implementation to short orchestration/state methods; do not add a workflow framework or stage-handler hierarchy.
- [x] Fail closed on incompatible process-status/stage/lease/response combinations, command-row versus snapshot coordinates, and frozen success receipts that do not match the actual committed Root/Public Token.
- [ ] Rerun idempotency HTTP characterization after each extraction.

## Task 5: Split creation and query rules; extract pure collaborators

**Files:**

- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/checkout/CheckoutCreationService.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/checkout/impl/CheckoutCreationServiceImpl.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/checkout/CheckoutQueryService.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/checkout/impl/CheckoutQueryServiceImpl.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/checkout/CheckoutOwnerCommandConverter.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/checkout/PreparedCheckoutAssembler.java`
- Delete after migration: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/checkout/ClientCheckoutService.java`
- Delete after migration: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/checkout/impl/ClientCheckoutServiceImpl.java`
- Modify: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/ext/rest/external/clientapi/v1/checkout/ClientCheckoutController.java`
- Modify: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/service/processor/checkout/CreateCheckoutProcessor.java`

- [x] Move all four Owner request conversions into `CheckoutOwnerCommandConverter`, without Client calls, persistence, or transaction ownership.
- [x] Move item amount calculation, Finance result validation, and seven-table aggregate construction into `PreparedCheckoutAssembler`.
- [x] Keep creation persistence/recovery/payment binding/expiry/receipt rules in `CheckoutCreationService + Impl` and GET ownership/expiry projection in `CheckoutQueryService + Impl`.
- [x] Point the Controller GET path only at the Query Service and the Processor only at creation/idempotency services.
- [x] Keep business Services as `interface + *Impl`; keep Converter and Assembler concrete.
- [ ] Run focused HTTP tests after each responsibility move.

## Task 6: Simplify Processor and Repository control flow

**Files:**

- Modify: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/service/processor/checkout/CreateCheckoutProcessor.java`
- Modify: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/repository/checkout/CheckoutRepository.java`
- Modify: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/repository/checkout/CheckoutRepositoryImpl.java`
- Modify only when mapping signatures require it: Checkout Mapper Java interfaces/XML under `commerce-service/paytech-commerce-web/src/main/`

- [x] Make `create` read as the five approved stages in order; keep each stage method single-purpose and generally no longer than 30 lines.
- [x] Keep Owner calls outside transactions and only the existing prepared/final writes inside short `CommerceTransactionExecutor` scopes.
- [x] Keep next-stage decisions in Processor/idempotency Service; Repository accepts explicit expected stage/version and performs CAS without deciding workflow.
- [x] Remove duplicate model conversions and group low-level mapping helpers without changing SQL, insert order, unique conflict handling, CAS, or fail-closed checks.
- [ ] Run the complete real HTTP Checkout suite.

## Task 7: Remove superseded internal behavior tests and enforce aggregate coverage

**Files:**

- Delete Checkout Processor/Service/Repository/Hasher/Integration behavior tests under `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/` only after equivalent HTTP behavior exists.
- Retain and adjust API/OpenAPI, architecture, schema, configuration, source-boundary, and model-generation checks that do not substitute for business behavior.
- Modify: `commerce-service/pom.xml`
- Modify: `commerce-service/paytech-commerce-test/pom.xml`
- Create only if Maven needs an aggregate-result verifier: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/test/coverage/` or a narrowly scoped build helper.

- [x] Attach JaCoCo agent to the real HTTP tests and generate an aggregate report that includes `paytech-commerce-web` production classes.
- [x] Add a `verify`-phase gate over the aggregate result for Hosted Checkout production packages/classes with line coverage at least 90%; reject an empty/zero-class input so the test module cannot pass falsely.
- [x] Do not exclude Controller/filter, Processor, Service, Repository, integration adapters, or business-error paths merely to raise the percentage.
- [ ] Delete direct internal behavior tests only after their scenarios are represented through HTTP and the gate remains green; delete speculative cancel/refund assertions not owned by this slice rather than migrating them.
- [ ] Run focused tests, then the full Commerce Maven `verify` and report the exact coverage result.
- [x] Remove the non-compliant test-only `@Primary` Owner Clients, dynamic ScenarioDriver, call-count assertions and CCT Barrier; do not restore Owner failure scenarios until production HTTP contracts and stateful protocol Simulators exist.

## Task 8: Verification and documentation truth

**Files:**

- Modify: `docs/superpowers/specs/2026-09-08-commerce-client-checkout-structure-simplification-design.md`
- Modify: `agent-docs/CURRENT_IMPLEMENTATION.md`

- [ ] Run `mvn -f commerce-service/pom.xml clean verify` with the required local Commerce MySQL credentials when available.
- [x] Run `node --test scripts/pdmaner/commerce-model.test.mjs scripts/pdmaner/commerce-scenario-doc.test.mjs` and `node scripts/pdmaner/generate-commerce-model.mjs --check`.
- [x] Run `npm --prefix requirements-site test` from the workspace root.
- [x] Search for obsolete class names, direct Checkout Service/Repository test calls, `Mono`/`Flux`, Mockito, and accidental Lombok misuse.
- [x] Review both root and `commerce-service` diffs, distinguishing pre-existing/user changes from this refactor.
- [x] Update the design status and `CURRENT_IMPLEMENTATION.md` only to the exact locally implemented and verified state; record the unprepared Schema and deferred Owner protocol scenarios without claiming completion.

**Verification record (2026-09-08):** 模型/Schema Oracle 24/24、生成器 `--check`、
Requirements 135/135 与 lint 均通过；本结构阶段 Commerce 数据库无关 `clean test` 为 92/92，
后继 Client API 边界简化后的当前集合为 106/106。单个真实
`RANDOM_PORT` Create 用例已完成应用启动；加载仓库本地环境凭据后数据库连接成功，但 fixture
确认 `mor_commerce` 未准备本切片所需 Schema，并按规则快速失败。使用不完整执行数据单独运行覆盖率门禁时，
门禁识别非空 Hosted Checkout 生产类并以最新不完整执行数据的 line ratio `0.08 < 0.90` 正确失败；这只证明门禁不会假绿，
不构成覆盖率验收。完整 MySQL HTTP 与 `clean verify` 因此前置保持未勾选。

## Completion conditions

- External Checkout HTTP/API/OpenAPI, RFC 9457, amount/expiry, five stages, seven-table writes, statuses/version and recovery behavior are unchanged; the not-yet-published internal Request Snapshot V1 is finalized with its integrity envelope before first deployment.
- Processor, idempotency, creation, query, serialization, conversion, assembly, and persistence responsibilities match the approved design.
- Pure assignment dependency constructors use Lombok; special constructors remain explicit.
- Checkout business behavior tests enter through real random-port HTTP and no longer directly invoke internal Services/Processors/Repositories.
- The aggregate JaCoCo gate covers non-empty Hosted Checkout production code at line coverage `>= 90%`.
- No Schema is applied, no public route is enabled, no deployment is performed, and no Git commit is created without a later explicit request.
