# Commerce Client API Hosted Checkout V1 Implementation Plan

> **2026-09-06 error-contract supersession:** 本计划中要求 Problem Body 不含 `requestId/timestamp`、字段错误含二级 Code 或仅 Header 携带 Request ID 的步骤，已由[全系统 RFC 9457 Problem Details 设计](../specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md)取代。执行 Commerce RFC 9457/OpenAPI/Controller 错误任务前必须先使用后继系统迁移计划重定基线；其他 Commerce 业务语义不变。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 `commerce-service/paytech-commerce-web` 内实现面向 Client Server 的 Hosted Checkout V1 十条外部 API，并闭合公开 Token、命令幂等、Order Funds Availability、Cancel、Refund、RFC 9457 与 Client Webhook 的持久化和恢复语义。

**Architecture:** 保持 Commerce 单服务、单数据库、`web + test` 两模块。Client API 是 Web 内的外部 Adapter；写操作和聚合查询由 Processor 编排，Order/Checkout/Refund 业务与 Repository 按 Owner 包隔离，跨系统只依赖 Owner 发布的 typed `*-api` 合同。Gateway 负责 Credential/Scope/Guard 并只注入可信 Client Context；Commerce 负责资源授权、状态、事务、幂等和不可变 Webhook 快照，Platform Support 负责 Webhook 投递。

**Tech Stack:** 仓库已批准的 Java 25、Spring Boot 4.0.8、Spring MVC、Spring `@HttpExchange`、MyBatis XML、MySQL 8、`TransactionTemplate`、统一 Snowflake/Compensation Event/Foundation 能力、JUnit 5、真实随机 Loopback Stateful Simulator；版本只从现有 Parent/BOM 获取。

**Spec:** [`docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md`](../specs/2026-09-05-commerce-client-api-v1-design.md)

**Partially superseded:** 固定 `web + test` 两模块、禁止外部 Java 契约模块以及把 Client API DTO 创建在 Web 的步骤，已由 [`2026-09-05-commerce-client-api-contract-module-design.md`](../specs/2026-09-05-commerce-client-api-contract-module-design.md) 取代。先执行后继专项计划，再重定基线；不得机械执行本计划中冲突的旧步骤。

## Global Constraints

- 本文件是后续实施计划，不是本轮实施授权。执行前必须由用户明确要求开始编码/建模；不得因计划存在而声明 OpenAPI、15 表、Java、SQL、Gateway、Hosted Checkout 页面或部署已完成。
- 开始和结束都检查根工作区及 `commerce-service` 的 `git status --short`。当前工作区很脏，所有既有修改都视为用户资产；只增量编辑本任务精确文件，不重置、不覆盖、不提交、不建分支、不推送、不部署。
- 不创建 `client-api-service`、`checkout-service`、`paytech-commerce-api` 或 Order/Checkout/Refund Maven 业务 JAR。Reactor 保持 `paytech-commerce-web + paytech-commerce-test`；只有出现真实内部 Java 消费者并另行批准时才建立非空 Commerce API JAR。
- 外部 OpenAPI 唯一位置是 `commerce-service/api/openapi/client-v1.yaml`。公共路径是 `/api/v1/**`；Controller 内部路径位于 `/commerce/external/client-api/v1/**`，不得把部署 Context Path 写成 Client 合同。
- 所有业务 Service 和 Domain Service 使用 `interface + *Impl`；Processor/Controller 保持具体类；Repository 使用 `interface + *RepositoryImpl`。不得为形态检查创建无业务职责的空 Service。
- 生产 SQL 只写 MyBatis XML；事务只使用显式 `TransactionTemplate`，禁止注解 SQL 和 `@Transactional`。DataObject 使用业务名且无 `DO` 后缀，不得跨 Maven 或 HTTP 边界。
- 不使用 Mockito、`@MockBean`、Spy、Testcontainers、Flyway、Liquibase 或应用启动建表/Seed。数据库、Redis、RabbitMQ 只复用手工启动的仓库 `local-environment`；外部 HTTP/MQ 使用真实协议 Stateful Simulator，未知调用默认失败且每测 reset/verify。
- 不在 Commerce 复制 CCT、Risk、Finance、Payment 或 Platform 的 Wire DTO、猜测 URI、直读其他数据库或引入 OpenFeign/Dubbo。缺少 Owner typed 合同时，相应 Task 停止在明确 Gate。
- Client API Response Body 必须走 metadata-only 日志路径，避免把 `checkoutUrl` 主动复制到日志。用户已接受第一方 `/c/{token}` URI Access Log 保留原始 Token 的窄风险；该决定不放宽 Credential、Webhook Secret、Consumer/Vault Token、网络原值、PAN 或 Provider Secret 的日志禁令。
- 行为实现全部按 TDD：先写并运行最小失败证据，再实现最小生产代码，再运行聚焦与相邻回归。不得只写测试而不实际观察 RED，也不得把已有无关失败冒充本任务证据。

---

## Gate 0: 记录现状并闭合外部前置

**Read-only evidence:**

- `agent-docs/CURRENT_IMPLEMENTATION.md`
- `commerce-service/pom.xml`
- `commerce-service/paytech-commerce-web/pom.xml`
- `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/CommerceModuleBoundaryAutoTest.java`
- `client-contract-service/client-contract-api/src/main/java/**`
- `risk-compliance-service/risk-api/src/main/java/**`
- `finance-service/paytech-finance-api/src/main/java/**`
- `payment-orchestration-service/**`
- `platform-support-service/platform-support-api/src/main/java/**`
- `paytech-foundation/**`

- [ ] 记录根目录和 Commerce 子仓库的 Branch、`git status --short` 与本计划将编辑文件的 SHA-256；不得用临时清理隐藏用户修改。
- [ ] 确认 Commerce 当前仍只有 Web/Test 两模块、一个生产启动类、14 表 / 223 字段生成模型和旧 `public_token_hash`/128 字符幂等字段；若事实已变化，先更新 `CURRENT_IMPLEMENTATION.md` 并重新审计计划，不机械执行旧步骤。
- [ ] 确认 COMP-01 已完成：六系统和 Foundation 使用同一 `event_key + event_type + sub_event_type +` 可选 4 KiB Payload 合同；Commerce 不得在本库局部发明第三套 `t_compensation_event`。
- [ ] 确认 Foundation 已发布经批准的 Client API RFC 9457 输出策略，可由共享 `GlobalExceptionHandler` 选择 `application/problem+json` 且不在 Body 输出 `status/requestId`；未完成时可以继续 OpenAPI/数据库准备，但不得在 Commerce 添加本地 `@RestControllerAdvice`。
- [ ] 确认存在经过项目技术评审的 RFC 8785/JCS 共享实现及固定向量；Commerce 与 Platform 必须使用同一算法。若不存在，先建立独立 Foundation 计划，禁止各服务各写一套近似 JSON 排序器。
- [ ] 确认 CCT-I13 typed 合同已支持服务端按 `clientId + country + currency + contextAsOfTime` 解析唯一 Primary Classification，并返回完整 V5 Bundle；当前只有 `package-info.java` 时此 Gate 未通过。
- [ ] 确认 Risk 发布 Checkout Decision typed 合同，能区分 ALLOW、稳定业务拒绝和技术失败；当前只有 Manual Payout Eligibility 时此 Gate 未通过。
- [ ] 确认 Finance 发布交易税 typed 合同，以及向 Commerce 发布的 `EconomicsCalculated`、`PayableBalanceMatured` typed event。后者必须带相同 Order/Context/Economics/currency/balanceAvailableTime，Maturity 另带 Client matured amount 与 Journal。
- [ ] 确认 Payment 已有可构建 Java Reactor 并发布 PAY-I01、PAY-I03、PAY-I04、PAY-I12 typed 合同与 UNKNOWN/PENDING/终态映射；当前无 POM/Java 模块时此 Gate 未通过。
- [ ] 确认 Platform Support 发布 `ClientWebhookRequested` typed 接收合同，直接映射现有 Source Key/Version/Hash 和单一 `signing_secret_id` Attempt；不要求 `source_type/source_id` 或未设计的双签列。
- [ ] 确认 Gateway Client API 计划至少达到 `integration-ready`，但保持 Commerce Route DISABLED；真实激活仍等待本计划最后 Gate 与 `checkout-web`。
- [ ] 对每个 Owner API JAR 用 `dependency:tree` 和 `jar tf` 核对实际解析制品包含合同类，不能仅凭兄弟仓库源码存在判定 Gate 通过。

Gate 0 未全部通过时，Task 1 可执行；COMP-01 完成后 Task 2 可执行；其他 Task 只能执行到其明确依赖已经满足的位置。

## Task 1: 冻结 OpenAPI 和长期模块边界

**Files:**

- Create: `commerce-service/api/openapi/client-v1.yaml`
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/contract/ClientV1OpenApiContractAutoTest.java`
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/architecture/CommerceArchitectureAutoTest.java`
- Modify: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/CommerceModuleBoundaryAutoTest.java`
- Modify: `commerce-service/paytech-commerce-test/pom.xml`

- [ ] 先写 OpenAPI Contract RED：精确断言十个 Method+Path、五个 Scope、Bearer Security、三个 POST 的 `Idempotency-Key`、GET 不使用该 Header、Query allowlist、Body 大小、成功状态/Location、RFC 9457 Schema 和公开 ID 边界；运行聚焦测试并记录因文件不存在而失败。
- [ ] 创建 OpenAPI 3.1 文档，严格投影 Spec 的请求/响应、Money/quantity 十进制字符串、`expiresAt`、分页 Cursor、状态枚举和 Problem Details；不暴露可信 `X-Gateway-*` Header、内部 ID、Channel Refund、Provider/MID 或 PII。
- [ ] 对 Request Schema 使用显式字段和上限；对 Response 保持向后兼容的可扩展读取语义。契约测试应验证新增可选响应字段不破坏 Client，但不能放宽已禁止的请求字段。
- [ ] 把临时“生产源码只能有一个文件、依赖集合必须完全等于基础骨架”的测试改为长期架构规则；继续禁止 `paytech-commerce-api`、Mockito/Testcontainers/Flyway/Liquibase/OpenFeign/Dubbo/WebFlux、跨模块 DataObject/Mapper/Repository/`*Impl` 与循环依赖。
- [ ] 新增包级门禁：外部 DTO 只能在 `ext.rest.external.clientapi.v1`；Controller 不访问 Mapper；业务模块不依赖另一模块的 Repository/DataObject；Processor 可编排已批准端口；所有 Service/Domain Service/Repository 形态符合项目规范。
- [ ] 运行：

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/commerce-service
./mvnw -pl paytech-commerce-test -am \
  -Dtest=ClientV1OpenApiContractAutoTest,CommerceModuleBoundaryAutoTest,CommerceArchitectureAutoTest test
```

## Task 2: 把 15 表目标落到 PDManer 与人工 SQL

**Files:**

- Modify: `scripts/pdmaner/generate-commerce-model.mjs`
- Modify: `scripts/pdmaner/commerce-field-metadata.mjs`
- Modify: `scripts/pdmaner/commerce-model.test.mjs`
- Modify: `scripts/pdmaner/commerce-scenario-doc.test.mjs`
- Modify: `commerce-service/docs/database/commerce.pdma.json`
- Modify: `commerce-service/docs/database/COMMERCE_PHYSICAL_DECISIONS.md`
- Modify: `agent-docs/database/COMMERCE_DATABASE.md`
- Modify: `agent-docs/database/scenarios/COMMERCE_REAL_WORLD_SCENARIOS.md`
- Create: `commerce-service/docs/database/sql/commerce-schema.sql`
- Create: `commerce-service/docs/database/sql/commerce-initial-data.sql`

- [ ] 先更新模型/场景测试，使其精确期待 15 表、新 Funds 一对一关系、Checkout 明文 Token+Origin、完整 Command Idempotency 恢复字段及 binary collation；运行 Node 测试并确认旧 14 表生成器 RED。
- [ ] 将 `t_checkout_session.public_token_hash` 替换为 `public_token CHAR(34) ASCII ascii_bin` 与 `public_origin VARCHAR(255) ASCII ascii_bin`，把唯一索引改为 `uk_checkout_public_token(public_token)`。
- [ ] 新增精确名称 `t_order_funds_availability_projection`，包含 Spec 第 9 节字段、五个唯一索引及 Order 1:1 逻辑关系。Event Key/Hash 使用 ASCII binary；不增加基于本机时钟的成熟扫描索引。
- [ ] 扩展 `t_command_idempotency`：255 字符大小写敏感 Key、`request_hash_version`、Request Snapshot Version/JSON、`resource_no`、Response HTTP/Schema/Location/JSON；锁定 Process Status、每操作 Recovery Stage、16 KiB Request/4 KiB Response 上限及可空矩阵。
- [ ] 按已统一 COMP-01 结构更新 Commerce `t_compensation_event`，并明确八种 Client Webhook 的 Canonical Envelope 是已批准 4 KiB 窄例外；禁止 Vault/Consumer Token、Checkout Token、网络原值或 Secret。
- [ ] 重新生成 `commerce.pdma.json`，再用独立 Oracle 测试验证表、字段、索引、关系、注释和统计；不得手改生成结果掩盖 Manifest 漂移。
- [ ] 创建完整手工 Schema SQL 和单一初始化 DML。SQL 与 PDManer 字段/类型/默认值/主键/唯一索引/非空/完整中英文注释一致，不创建 `CHECK` 或 `FOREIGN KEY`，不引入 Flyway/Liquibase。
- [ ] 在 PDManer 4.9.3 人工打开检查 Diagram、注释、类型、binary collation 和长表名兼容；人工门禁没有完成时只记录“自动模型通过”。
- [ ] 运行：

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
node scripts/pdmaner/generate-commerce-model.mjs --check
node --test \
  scripts/pdmaner/commerce-model.test.mjs \
  scripts/pdmaner/commerce-scenario-doc.test.mjs
```

- [ ] 只有用户另行授权后，人工审阅并应用 SQL 到仓库自有本地 `mor_commerce`；Java/JUnit 不执行 DDL/DML。通过 `SHOW CREATE TABLE` 与 `information_schema.columns/statistics` 核对实际库，不把 SQL 文件存在当作数据库已迁移。

## Task 3: 建立持久化基础与真实本地测试链

**Files:**

- Modify: `commerce-service/pom.xml`
- Modify: `commerce-service/paytech-commerce-web/pom.xml`
- Modify: `commerce-service/paytech-commerce-test/pom.xml`
- Modify: `commerce-service/paytech-commerce-web/src/main/resources/application.yml`
- Modify: `commerce-service/paytech-commerce-test/src/test/resources/application-auto.yml`
- Modify: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/CommerceConfigurationAutoTest.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/configuration/PersistenceConfiguration.java`
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/repository/CommerceDatabaseSchemaAutoTest.java`
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/test/infrastructure/CommerceDatabaseIsolationListener.java`
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/test/CommerceTestMapper.java`

- [ ] 先把配置/模块测试改为期待真实 DataSource、MyBatis、MySQL Driver、`TransactionTemplate` 和统一 Snowflake/Compensation Event；保留所有仍有效禁令，运行并观察旧基础 POM RED。
- [ ] 只增加已批准生产依赖及 test-scoped 测试支撑，不删除对 Mockito、Testcontainers、Migration 工具和未批准集成的 Enforcer 禁令。
- [ ] 本地 auto 固定复用 `127.0.0.1:13306` 的 `mor_commerce / commerce_app`，凭据来自受控测试配置且不得回显。缺服务或缺表时快速失败并打印明确的人工准备说明；测试不启动/停止容器。
- [ ] 建立按随机测试业务命名空间隔离的数据准备和清理；只能删除本次测试创建的精确行，不 truncate 共享表、不重建 Schema。
- [ ] 运行 `CommerceConfigurationAutoTest,CommerceDatabaseSchemaAutoTest`，再运行既有启动/健康测试，确认基础运维边界未回退。

## Task 4: 实现 Token、金额、公开 ID 与命令幂等基础

**Representative production files:**

- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domain/checkout/CheckoutPublicToken.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/checkout/CheckoutPublicTokenGenerator.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/checkout/impl/CheckoutPublicTokenGeneratorImpl.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domain/order/MoneyValue.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/idempotency/CommandIdempotencyService.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/idempotency/impl/CommandIdempotencyServiceImpl.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/repository/idempotency/CommandIdempotencyRepository.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/repository/idempotency/impl/CommandIdempotencyRepositoryImpl.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/core/dal/dataobject/idempotency/CommandIdempotency.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/core/dal/mapper/idempotency/CommandIdempotencyMapper.java`
- Create: `commerce-service/paytech-commerce-web/src/main/resources/mapper/idempotency/CommandIdempotencyMapper.xml`
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/domain/checkout/CheckoutPublicTokenAutoTest.java`
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/domain/order/MoneyValueAutoTest.java`
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/domainservice/CommandIdempotencyAutoTest.java`

- [ ] Token 测试先固定长度、Prefix、Base62 大小写、CRC、唯一冲突三次上限、DB binary lookup 和 Spec 固定向量；运行确认实现不存在。
- [ ] 实现标准 KSUID 20-byte 编码、CSPRNG Payload、country/clientId XOR 与 CRC-16/CCITT-FALSE。不要 Hash/HMAC/Vault/KMS，不从 Token 解码业务授权；事务提交后恢复只读取数据库 winner。
- [ ] Money/quantity 测试覆盖纯十进制字符串、无指数、币种 exponent、`DECIMAL(24,8)` 边界、规范等价值、逐行金额恒等式及溢出；Java 全程 `BigDecimal`，禁止 `double/float`。
- [ ] 幂等测试先覆盖 `Client + operation + key`、Credential 轮换、Hash V1、字段缺失差异、同 Key 并发 Lease、稳定结果重放、暂态不封存、Create Checkout/Refund pre-root Snapshot 和 Cancel 无 Body 恢复。
- [ ] 使用共享 RFC 8785 实现计算版本化语义 Hash；BIGINT 用字符串，Decimal 用无指数规范字符串。旧记录必须按自身 Version 比较，算法升级只增加版本。
- [ ] Repository 以唯一索引和 CAS/Lease 决定 winner；不要先查后插竞态。所有 Mapper SQL 位于 XML，锁与更新只覆盖精确 Command/Order/Refund 资源。
- [ ] 运行：

```bash
./mvnw -pl paytech-commerce-test -am \
  -Dtest=CheckoutPublicTokenAutoTest,MoneyValueAutoTest,CommandIdempotencyAutoTest test
```

## Task 5: 接入 Owner typed contracts 与 Stateful Simulators

**Production packages:**

- Create under: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/integration/cct/`
- Create under: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/integration/risk/`
- Create under: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/integration/finance/`
- Create under: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/integration/payment/`
- Create under: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/integration/platform/`
- Create test infrastructure under: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/test/simulator/`
- Create tests under: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/integration/`

- [ ] 为每个真实生产 Adapter 先写 HTTP/MQ RED：成功、业务拒绝、超时、连接失败、非法字段/枚举、超大响应、UNKNOWN、重复与 unexpected call。
- [ ] 生产 Adapter 只消费 Owner API JAR 的 Command/Result/Event；在 Commerce 边界显式转换到本地 Domain，不把远程 DTO传入 Repository 或外部 Response。
- [ ] Simulator 使用随机 Loopback 端口、真实序列化/HTTP 或 Broker 协议、可编程状态和调用 Journal；每测 reset/verify，未声明调用立即失败。不得用 Simulator 代替缺失的生产 typed contract。
- [ ] 固定下游子幂等键由 Commerce Command/Resource/Operation Slot 派生；重试不得生成新业务资源或新首次时点。
- [ ] 运行 `CctClientAutoTest,RiskClientAutoTest,FinanceClientAutoTest,PaymentClientAutoTest,PlatformWebhookClientAutoTest`；兄弟 Reactor 制品不由 `-am` 自动构建，执行前必须确认已安装/发布正确版本。

## Task 6: 实现 Create Hosted Checkout 纵向切片

**Representative production files:**

- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/ext/rest/external/clientapi/v1/checkout/ClientCheckoutController.java`
- Create request/response DTOs under: `.../ext/rest/external/clientapi/v1/checkout/`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/service/processor/checkout/CreateCheckoutProcessor.java`
- Create Order/Checkout Domain and Service interfaces/implementations under: `.../domain/{order,checkout}/` and `.../domainservice/service/{order,checkout}/`
- Create aggregate Repositories/implementations under: `.../domainservice/repository/{order,checkout}/`
- Create DataObjects/Mappers/XML under: `.../core/dal/{dataobject,mapper}/{order,checkout}/` and `.../resources/mapper/{order,checkout}/`
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/ext/http/clientapi/ClientCheckoutHttpAutoTest.java`

- [ ] 先写从真实 HTTP 到真实本地数据库和 Stateful Simulators 的 RED：可信 Client Header、Scope 已由 Gateway 处理、请求字段上限、默认/显式 expiry、Primary Classification、Risk/Tax、Payment Intent、崩溃恢复和 `201` 门禁。
- [ ] Controller 只解析 Header/Body、取得可信 Client、转换 DTO、调用 Processor 和映射结果；缺失/重复/非法 `X-Gateway-*` 失败关闭，Body/Path 不能覆盖 Client。
- [ ] Processor 按 Spec 六阶段执行：短事务 Claim；事务外 CCT/Risk/Finance；本地 Order/Context/Legal/Items/Refund Position/Funds/Session 事务；提交后 PAY-I01；绑定 Payment Projection 并打开 Order/Session；最后冻结原始 201 回执。
- [ ] 远程调用期间不持有数据库行锁。稳定业务拒绝可冻结 FAILED Problem；技术未知保持 PROCESSING 并以同一 Snapshot/时点/资源/子 Key 恢复。
- [ ] `clientOrderId` 唯一索引决定永久 winner；新 Key 命中既有值固定 409 `CLIENT_ORDER_ID_ALREADY_EXISTS`，不得返回旧 Order 当作新命令成功。
- [ ] 只有 Payment Intent 与 Checkout URL 可用才返回 201；响应冻结相对 Location 和原 `checkoutUrl`。如果 CHECKOUT Channel 未激活，生产 Route 仍保持 DISABLED，不通过硬编码死链接绕过。
- [ ] 运行 `ClientCheckoutHttpAutoTest` 及 Repository/Idempotency 聚焦测试。

## Task 7: 实现 Checkout/Order 查询与 Funds Projection

**Representative production files:**

- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/ext/rest/external/clientapi/v1/order/ClientOrderController.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/service/processor/checkout/CheckoutQueryProcessor.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/service/processor/order/OrderAggregateQueryProcessor.java`
- Create Funds DataObject/Mapper/Repository/Domain Service in the Order owner packages
- Create listeners/handlers under: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/ext/mq/{payment,finance}/`
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/ext/http/clientapi/ClientOrderHttpAutoTest.java`
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/ext/eventhandler/PaymentEventHandlerAutoTest.java`
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/ext/eventhandler/FinanceEventHandlerAutoTest.java`

- [ ] 先写 GET RED：orderNo/clientOrderId 精确查询、Checkout URL 过期置 null、Order 本地聚合、状态映射、no-store、跨 Client 与不存在统一 404；GET 不调用远程 Payment/Finance。
- [ ] Order 聚合查询只读取本地 Order、Payment Projection、Refund Position/Request 和 Funds Projection；简单单表读取可直接走业务 Service，不为形式创建空 Processor。
- [ ] 先写事件 RED：PaymentSucceeded 原子更新 Order/Session/Payment Projection/Refund Base/Funds PENDING；Finance 两事件的重复、乱序、跨 Order、同版本异 Hash、未来版本、Journal/Economics 唯一冲突与最终不回退。
- [ ] 初始化 Funds=`NOT_APPLICABLE` 且 `as_of=order.create_time`。Commerce 不看本机时钟宣布成熟；只有 Finance Maturity 产生 `AVAILABLE (>0)` 或 `FULLY_OFFSET (=0)`。
- [ ] Event handler 在本地事务提交后才 Ack；Commerce 尚未应用匹配 PaymentSucceeded 时 Finance 事件保持可重试且不 Ack，不能独立把 Order 置 PAID。
- [ ] 运行三个聚焦测试并验证 AVAILABLE 金额是本 Order 成熟金额，不是 Client 当前余额。

## Task 8: 实现 Cancel

**Files:**

- Modify: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/ext/rest/external/clientapi/v1/order/ClientOrderController.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/service/processor/order/CancelOrderProcessor.java`
- Create/modify Order Service/Repository and Payment Adapter files actually used
- Create Cancel HTTP scenarios in: `.../auto/ext/http/clientapi/ClientOrderHttpAutoTest.java`

- [ ] 先写 `POST /orders/{orderNo}/cancel` RED：必需 Key、无 Body/Expected Version、200 CANCELLED、202 PAYMENT_IN_PROGRESS + Retry-After、已支付 409 REVERSAL_REQUIRED、已过期/其他不可取消终态 409 ORDER_NOT_CANCELLABLE、同 Key 恢复、不同 Key 自然收敛和并发。
- [ ] Processor 在事务外调用 PAY-I12，只有确定 `TERMINATED` 后才在本地短事务以 Order 锁/CAS 同时关闭 Order/Session并冻结事件；PENDING 不修改终态。
- [ ] 明确断言 Cancel 永不创建 Void、Refund 或 Reversal；已授权/捕获/SALE 只返回 `REVERSAL_REQUIRED`，EXPIRED 等不可取消终态只返回 `ORDER_NOT_CANCELLABLE`。
- [ ] 运行 Cancel 聚焦测试及 Payment Simulator unexpected-call 检查。

## Task 9: 实现 Refund 创建与查询

**Representative production files:**

- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/ext/rest/external/clientapi/v1/refund/ClientRefundController.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/service/processor/refund/CreateRefundProcessor.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/service/processor/refund/RefundQueryProcessor.java`
- Create Refund Domain/Service/Repository/DataObject/Mapper/XML under the Refund owner packages
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/ext/http/clientapi/ClientRefundHttpAutoTest.java`
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/domainservice/refund/RefundReservationAutoTest.java`

- [ ] 先写 RED 覆盖可退款建议查询、锁内重新校验、并发预占守恒、Create 201、相同/冲突 `clientRefundId`、同 Key恢复、PAY-I04 明确拒绝/UNKNOWN/终态和五态映射。
- [ ] 一个本地事务锁 Order Refund Position、预占、创建 Refund Request、更新 Command Root 并冻结 `refund.created`；事务提交后才调用 PAY-I04，绝不在持有 Position 行锁时远程调用。
- [ ] Refund 已本地提交即返回原 201；下游绑定与 UNKNOWN 由同一子 Key 恢复。成功消费 reserved 并增加 refunded；失败/取消释放 reserved；始终保持 `reserved + refunded <= base`。
- [ ] 实现按 `refundRequestNo`、`clientRefundId` 的精确查询和按 `orderNo` 的 `(create_time DESC, refund_request_no DESC)` Cursor 分页；Cursor 大小写敏感、URL-safe、版本化并校验 Client 归属。
- [ ] 外部永不返回 `channel_refund_id`、内部 Snowflake、Provider 原因或 Payment UNKNOWN；UNKNOWN 只映射 PROCESSING。
- [ ] 运行 Refund HTTP、Reservation 和 Repository 测试。

## Task 10: 冻结并提交 Client Webhook 事件

**Files:**

- Create Webhook snapshot/domain service interface+impl under: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/webhook/`
- Create Platform adapter/handler under: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/integration/platform/`
- Modify the exact Order/Funds/Refund transactions that create the eight events
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/ext/eventhandler/ClientWebhookRequestedAutoTest.java`

- [ ] 先写 RED 覆盖八种 Event Type、同一 Client 全事件永久唯一且不可跨 Type/Schema/资源复用的 `eventId`、RFC 8785 Object/Envelope Hash、4 KiB 上限、重放不回源、敏感字段缺失、Platform duplicate/conflict 和 FULLY_OFFSET 不发送 funds_available。
- [ ] 在业务状态变更的同一本地事务建立公开快照与统一 Compensation Event；`event_key=public eventId`，Source Version 对应 Order/Refund/Funds Version，Payload 只含 Spec 允许的受控字段。
- [ ] Platform 请求把公开 `eventId` 映射 `source_event_key`；按实际 Source Event Key 串行协调并先以 Tenant + Source System + Source Event Key 查重，再比较现有 Type/Schema/Source Version/Source Payload Hash/Payload Hash，任一不同都 Fail-Closed；不传 `source_type/source_id`，不要求 Platform 增加双签列。
- [ ] V1 Platform 每 Attempt 只用一个 ACTIVE Secret；Commerce 不拥有签名、Endpoint、Subscription、Delivery、Retry 或 Replay。
- [ ] 运行 Webhook 测试并确认 Retry/Replay 使用同一 Event ID/Body，新的 Attempt 只更新时间签名。

## Task 11: 闭合可信边界、RFC 9457 和日志

**Files:**

- Create external error mapping DTO/converters only as allowed by the shared Foundation strategy under: `.../ext/rest/external/clientapi/v1/error/`
- Modify: `commerce-service/paytech-commerce-web/src/main/resources/application.yml`
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/ext/http/clientapi/ClientApiTrustedContextAutoTest.java`
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/ext/http/clientapi/ClientApiErrorContractAutoTest.java`
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/ext/http/clientapi/ClientApiLoggingSafetyAutoTest.java`

- [ ] 先写可信 Header RED：缺失、重复、非法、直接私网绕过、Body/Path Client 覆盖和跨 Client 资源探测全部失败关闭；真实不存在与错误归属保持相同外部 404。
- [ ] 先写 RFC 9457 RED：Owner 4xx/5xx 使用 `application/problem+json`，Body 仅 `type/title/detail/code/errors?`，无 `status/requestId/timestamp`，X-Request-Id 只在 Header；稳定 Code 与 HTTP 映射符合 Spec。
- [ ] 使用 Gate 0 的共享 Foundation 扩展，不在 Commerce 建第二个全局 Advice。Gateway 本地产生的错误需由 Gateway 独立测试保证同一外部合同。
- [ ] 将 `/commerce/external/client-api/v1/**` 配置为 metadata-only 日志；Canary 验证 create/query response 中的 checkoutUrl、Authorization/Credential、Webhook Secret、Consumer/Vault Token、网络原值、PAN 字段和内部 ID 不出现在日志/异常/Metric/Simulator Journal。
- [ ] 固定 `Cache-Control: no-store`、禁用 CORS。Hosted Checkout `/c/{token}` 可按已确认取舍进入第一方 URI Access Log，但页面必须使用 `Referrer-Policy: no-referrer`、受限 CSP 且不请求第三方资源。
- [ ] 运行三个边界测试以及全部 Client API HTTP 测试。

## Task 12: 完整验证、状态合并和激活 Gate

**Files:**

- Modify after evidence only: `commerce-service/README.md`
- Modify after evidence only: `agent-docs/CURRENT_IMPLEMENTATION.md`
- Verify: `requirements-site/content/requirements.ts`
- Verify: `requirements-site/content/global-use-case-sequences.mjs`

- [ ] 运行所有聚焦测试后执行完整 Commerce 门禁：

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/commerce-service
./mvnw clean verify
./mvnw dependency:tree
```

- [ ] 执行模型与需求门禁：

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
node scripts/pdmaner/generate-commerce-model.mjs --check
node --test \
  scripts/pdmaner/commerce-model.test.mjs \
  scripts/pdmaner/commerce-scenario-doc.test.mjs
npm --prefix requirements-site test
npm --prefix requirements-site run lint
```

- [ ] 启动新可执行 JAR，以随机或批准本地端口验证 Actuator 与十条内部 Adapter 路径；测试 Credential/数据只使用隔离的本地合成值并在结束后按精确身份清理。不要访问或修改 `mor-test`/生产。
- [ ] 执行 `git diff --check`、`git -C commerce-service diff --check`，重新比较开场状态和受保护文件 Hash；区分本任务失败与既有失败。
- [ ] 只根据实际证据更新 README/`CURRENT_IMPLEMENTATION.md`：分别标记 OpenAPI、Java、PDManer、SQL 文件、物理本地库、测试和部署，不把其中一项成功外推到其他项。
- [ ] 在 Commerce 仅完成时最多声明 `integration-ready`。真实 Client API Route 继续 DISABLED，直到 Platform PLT-I03/Scope、Gateway/Nginx、Platform Webhook 和所有 Owner 合同完成。
- [ ] 启用 `POST /api/v1/checkouts` 前，`checkout-web` 与 CHECKOUT Channel 的 `public_origin + /c/{token}` 必须真实可达，完成 no-store/no-referrer/CSP/无第三方泄漏浏览器 Smoke；否则 201 会返回死链接，禁止激活。
- [ ] 外部激活按 Gateway 计划以未分发测试 Credential、单 Route canary、逐实例 Snapshot Revision/Fingerprint、回滚顺序和端到端 smoke 执行；部署和 DML 都必须获得独立明确授权。

## Completion Evidence

完成本计划时最终交付必须同时给出：

- 十条 OpenAPI 与实际 Controller 的一致性证据；
- 15 表生成统计、PDManer 人工检查与实际本地 Schema 核验是否分别完成；
- Token 固定向量、幂等崩溃恢复、Funds 乱序、Refund 守恒、Webhook不变快照和 RFC 9457 的测试结果；
- Commerce、Requirements、Gateway/Platform/Owner 前置各自的真实状态；
- 未完成的激活、DML、部署或合规事项；
- 明确说明没有提交、推送或部署，除非用户另行授权且确实执行。
