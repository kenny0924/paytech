# Commerce Client Checkout Local-Mock Slice Implementation Plan

> 2026-09-09 后继：Create Checkout 的五阶段定义已由
> [`2026-09-09-commerce-checkout-recovery-simplification.md`](../../../agent-docs/decisions/2026-09-09-commerce-checkout-recovery-simplification.md)
> 收敛为 `CLAIMED -> ORDER_COMMITTED -> COMPLETED`。下方 Step 2 的旧阶段文字仅保留为历史实施证据；Cancel、Create Refund 及其他 Wire/业务合同不受影响。

> 2026-09-08 后继：Task 5 至 Task 7 的 Java 类职责划分和测试组织方式已由
> [`2026-09-08-commerce-client-checkout-structure-simplification-design.md`](../specs/2026-09-08-commerce-client-checkout-structure-simplification-design.md)
> 取代。既有 Wire、业务、数据库、幂等、恢复和安全合同继续有效；不得再按本计划为 Processor、Service 或 Repository
> 分别新增行为测试。
> 本计划下方曾勾选的内部调用顺序、调用次数和 Barrier 并发测试不再构成当前合规证据；测试专用
> `@Primary` Owner Client 与动态 ScenarioDriver 已删除。Owner 技术失败和协议错误只能在真实合同发布后，
> 通过生产 HTTP Client 与有状态 Loopback Simulator 恢复覆盖。
> 本文历史步骤中的 `CheckoutWireMapper` 已按同一后继设计更名为 `CheckoutWireConverter`；`Mapper`
> 现只用于持久化边界，不得按旧文件清单恢复该类名。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 `commerce-service` 内完整实现 `ClientCheckoutApi#createCheckout/getCheckout`，以本地显式 Mock `*Client` 暂时代替尚未发布的 CCT、Risk、Finance、Payment Owner 合同，并把结果限定为本地 `integration-ready`。

**Architecture:** 保留 `paytech-commerce-client-api -> paytech-commerce-web -> paytech-commerce-test` 三模块；Controller 直接实现既有 Java 入站契约，Create 由 Processor 编排显式短事务、四个本地 Client Port 与 MyBatis Repository，GET 只读取 Commerce 本地事实。四个 `LocalMock*Client` 只允许在 `local`、`auto` 或 `test` Profile 且 `commerce.integrations.mode=local-mock` 时装配；默认 `disabled` Adapter 失败关闭，避免生产环境误用 Mock。

**Tech Stack:** Java 25、Spring Boot 4.0.8、Spring MVC、MyBatis 4.0.1/MySQL 8.4、`TransactionTemplate`、Foundation Snowflake/MyBatis/JCS/RFC 9457、JUnit 5。

**Specs:** [`docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md`](../specs/2026-09-05-commerce-client-api-v1-design.md)、[`docs/superpowers/specs/2026-09-05-commerce-client-api-contract-module-design.md`](../specs/2026-09-05-commerce-client-api-contract-module-design.md)、[`docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md`](../specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md)

## Global Constraints

- 本计划只实现 `POST /external/client-api/v1/checkouts` 与 `GET /external/client-api/v1/orders/{orderNo}/checkout`；不实现 Order 聚合、Cancel、Refund、Consumer Checkout 页面、PaymentSucceeded 消费或 Webhook。
- 不新增 Maven 模块，不复制 `CheckoutModels`，不在 Controller 重复 HTTP Mapping；`ClientCheckoutController implements ClientCheckoutApi`。
- 用户已明确批准本地 `*Client` Mock 作为临时例外。Mock 必须按输入确定性计算，不允许每个测试修改某次方法返回值，也不得在非本地 Profile 启用。
- 在 `CommerceIntegrationConfiguration` 保留且只保留一个明确的替换标记：`TODO(COMMERCE-OWNER-CONTRACTS): Replace the local-only CctClient, RiskClient, FinanceClient and PaymentClient adapters with owner-published @HttpExchange contracts before enabling any CLIENT_API route.`
- 默认 `commerce.integrations.mode=disabled`；调用时返回稳定 503，而不是启动后悄悄成功。`local-mock` 仅允许 Active Profile 包含 `local`、`auto` 或 `test`，否则应用启动失败。
- Gateway Route、Nginx Client API 域名和 `checkout-web /c/{token}` 不在本计划内；它们保持未激活。本地 Mock 的成功不能描述为生产可用。
- 所有业务 Service/Domain Service 使用 `interface + *Impl`，Repository 使用 `interface + *RepositoryImpl`；Processor、Controller、Client Adapter 保持具体类。
- 使用显式 `TransactionTemplate`；不使用 `@Transactional`。远程/Mock Client 调用不能发生在数据库事务或行锁内。
- Mapper SQL 只在 XML 中；不使用注解 SQL、Flyway、Liquibase、Testcontainers、Mockito、OpenFeign、Dubbo、WebFlux、`Mono` 或 `Flux`。
- 金额和数量先从 Wire String 解析为 `BigDecimal`；禁止 `double/float`。持久化为 `DECIMAL(24,8)`，外部响应继续使用无指数十进制 String。
- `request_hash_version=1` 的业务 Projection 由 Commerce 构造为 JSON Object；Money/quantity 先规范为 String，64-bit ID 也编码为 String；Foundation JCS 只产生 canonical bytes，SHA-256 由 Commerce 计算。
- 业务错误沿用 Foundation 唯一 RFC 9457 Writer；Commerce 只发布 Error Code 和 `ApiErrorStatusResolver`，不创建本地 `@RestControllerAdvice` 或 Problem DTO。
- 开始和结束均检查根目录及 `commerce-service` 状态。保留当前用户对 `commerce-service/docs/database/COMMERCE_PHYSICAL_DECISIONS.md` 的未提交修改以及根工作区所有无关修改。
- Java/JUnit 不创建或迁移 Schema。只生成经审查的人工 SQL；实际应用到 `mor_commerce` 需要用户另行明确授权。
- 按 TDD 执行每项行为：先写聚焦失败测试并观察预期 RED，再写最小生产实现并观察 GREEN。未获得提交授权，不执行计划中的 Git Commit。

---

## Task 1: 将数据库设计源推进到 Checkout 首片需要的 15 表目标

**Files:**

- Modify: `scripts/pdmaner/generate-commerce-model.mjs`
- Modify: `scripts/pdmaner/commerce-field-metadata.mjs`
- Modify: `scripts/pdmaner/commerce-model.test.mjs`
- Modify: `scripts/pdmaner/commerce-scenario-doc.test.mjs`
- Regenerate: `commerce-service/docs/database/commerce.pdma.json`
- Merge carefully: `commerce-service/docs/database/COMMERCE_PHYSICAL_DECISIONS.md`
- Create: `commerce-service/docs/database/sql/commerce-schema.sql`
- Create: `commerce-service/docs/database/sql/commerce-initial-data.sql`

**Produces:** Checkout 首片可以手工创建并由 MyBatis 映射的权威表结构。

- [x] **Step 1: 先修改独立 Node Oracle，期待新结构并运行 RED**

  精确锁定：新增 `t_order_funds_availability_projection`；Checkout Hash 改为明文 `public_token/public_origin`；Command Idempotency 增加版本化请求/响应快照；Compensation Event 对齐当前最小 `(event_key,event_type,sub_event_type)` 身份。运行：

  ```bash
  node --test scripts/pdmaner/commerce-model.test.mjs scripts/pdmaner/commerce-scenario-doc.test.mjs
  ```

  预期因旧 14 表 Manifest 失败，而不是语法错误。

- [x] **Step 2: 修改生产 Manifest 与字段元数据**

  `t_order_funds_availability_projection` 固定包含 19 列和 5 个唯一键；状态为 `NOT_APPLICABLE|PENDING|AVAILABLE|FULLY_OFFSET`。`t_checkout_session` 使用大小写敏感 `public_token CHAR(34) ASCII ascii_bin`、`public_origin VARCHAR(255) ASCII ascii_bin` 和 `uk_checkout_public_token(public_token)`。`t_command_idempotency.idempotency_key` 扩为 255，并增加 `request_hash_version`、最多 16 KiB UTF-8 的请求快照、`resource_no`、HTTP/Location 和最多 4 KiB UTF-8 的 Response 快照字段；含 Checkout URL 的回执按访问能力数据限制为原命令恢复/重放，禁止普通日志、MQ、Webhook 和通用审计。`process_status` 与三个操作的无重复 `recovery_stage` 联集都使用权威闭合值。

- [x] **Step 3: 对齐 Compensation Event 当前最小表形**

  使用 `id,event_key,event_type,sub_event_type,payload_json,execute_status,execute_count,next_execute_at,lock_owner,lock_until,last_error_code,last_error_summary,lock_version,create_time,update_time`；唯一键按 `(event_key,event_type,sub_event_type)`，两条访问索引精确为 `(execute_status,next_execute_at,id)` 与 `(execute_status,lock_until,id)`。冻结 Foundation 安全默认值：`INIT`、`0`、`CURRENT_TIMESTAMP(6)`、`lock_version=1` 及创建/更新时间的 `CURRENT_TIMESTAMP(6)`。本切片不引入 Handler 或事件发布代码。

- [x] **Step 4: 重新生成 PDManer 并验证确定性**

  ```bash
  node scripts/pdmaner/generate-commerce-model.mjs
  node scripts/pdmaner/generate-commerce-model.mjs --check
  node --test scripts/pdmaner/commerce-model.test.mjs scripts/pdmaner/commerce-scenario-doc.test.mjs
  ```

- [x] **Step 5: 生成完整人工 Schema SQL 和单一初始化 DML**

  DDL 必须覆盖全部 15 表，并逐表精确匹配 PDManer 的列清单/顺序、类型、可空性、默认值、主键、唯一/访问索引、ASCII binary 字段及完整表/列注释，且拒绝额外列或索引；不得包含 `CHECK`、`FOREIGN KEY`、Flyway 元数据或 Seed 业务数据。初始化 DML 文件明确当前无业务初始化行。

- [x] **Step 6: 静态核对 SQL 与模型**

  增加 Node 断言确保 15 个物理表各出现一次、模型总计精确为 15 表/246 字段/24 个非主键唯一索引/12 个访问索引/15 条本库关系/4 个 Diagram Group、9 个首片表完整、无 CHECK/FK、无旧 `public_token_hash`，再重跑本 Task 全部 Node 门禁。

## Task 2: 建立 Commerce 持久化与运行配置边界

**Files:**

- Modify: `commerce-service/pom.xml`
- Modify: `commerce-service/paytech-commerce-web/pom.xml`
- Modify: `commerce-service/paytech-commerce-test/pom.xml`
- Modify: `commerce-service/paytech-commerce-web/src/main/resources/application.yml`
- Modify: `commerce-service/paytech-commerce-test/src/test/resources/application-auto.yml`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/configuration/PersistenceConfiguration.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/service/transaction/CommerceTransactionExecutor.java`
- Modify: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/CommerceConfigurationAutoTest.java`
- Modify: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/CommerceModuleBoundaryAutoTest.java`
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/repository/CommerceDatabaseSchemaAutoTest.java`

**Produces:** `TransactionTemplate`、MyBatis 与 MySQL 可被业务实现使用，且 local/auto 配置失败关闭。

- [x] **Step 1: 修改配置/模块测试并观察 RED**

  新测试要求 Web 直接依赖 `paytech-mybatis-support`、`paytech-snowflake-id`、`paytech-json-canonicalization`、MyBatis Starter、MySQL Driver 和 Validation Starter；同时继续禁止 Redis/MQ/Compensation/Vault/OpenFeign/Dubbo/WebFlux/Mockito/Testcontainers/Migration 工具。

- [x] **Step 2: 精确调整 Maven 依赖和 Enforcer**

  只从现有基础骨架禁令中移除本切片真正需要的 MyBatis/JDBC/Hikari/MySQL、Foundation MyBatis/Snowflake 项；新增上述直接依赖，不给 Client API JAR 增加运行依赖。

- [x] **Step 3: 实现 MyBatis 配置和事务执行器**

  `PersistenceConfiguration` 使用 `@MapperScan("com.paytech.commerce.core.dal.mapper")`，关闭 generated keys，注册 Foundation `InstantTypeHandler` 与每个实际持久化枚举的 `CodeEnumTypeHandler`。`CommerceTransactionExecutor` 提供 `write(Supplier<T>)` 和 `read(Supplier<T>)`，分别使用短 `READ_COMMITTED` 写事务和 `REPEATABLE_READ/readOnly` 查询事务。

- [x] **Step 4: 配置生产与 auto 边界**

  `application.yml` 默认 `commerce.integrations.mode=disabled`，数据库连接只接受环境覆盖，`spring.sql.init.mode=never`；`application-auto.yml` 只连接 `127.0.0.1:13306/mor_commerce`，密码从 `MOR_COMMERCE_DB_PASSWORD`/专用测试变量注入，禁用 Nacos 和非本切片异步组件。

- [x] **Step 5: 运行配置门禁**

  ```bash
  cd commerce-service
  ./mvnw -pl paytech-commerce-test -am -Dtest=CommerceConfigurationAutoTest,CommerceModuleBoundaryAutoTest,CommerceDatabaseSchemaAutoTest test
  ```

  如果 Schema 未人工应用，`CommerceDatabaseSchemaAutoTest` 必须快速失败并给出精确准备命令；不得自动建表或降级到内存数据库。

## Task 3: 实现 Money、公开 Token、公开 ID 与请求 Hash

**Files:**

- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domain/order/MoneyValue.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domain/checkout/CheckoutPublicToken.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/checkout/CheckoutPublicTokenGenerator.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/checkout/impl/CheckoutPublicTokenGeneratorImpl.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/port/CommerceIdentifierGenerator.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/integration/id/FoundationCommerceIdentifierGenerator.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/idempotency/CheckoutRequestHasher.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/idempotency/impl/CheckoutRequestHasherImpl.java`
- Test: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/domain/order/MoneyValueAutoTest.java`
- Test: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/domain/checkout/CheckoutPublicTokenAutoTest.java`
- Test: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/domainservice/idempotency/CheckoutRequestHasherAutoTest.java`

**Produces:** `MoneyValue`、`CheckoutPublicTokenGenerator.generate(Instant,String,long)`、公开 ID 生成端口和 Hash V1。

- [x] **Step 1: Money 测试 RED/GREEN**

  覆盖纯十进制、无符号/指数、`DECIMAL(24,8)` 边界、USD exponent、`quantity*unitAmount-discount`、金额溢出和规范字符串；再实现最小 `MoneyValue`。

- [x] **Step 2: Token 固定向量 RED/GREEN**

  覆盖 `chk_ + 27 KSUID + 3 Base62 CRC16`、大小写、CRC-16/CCITT-FALSE、country/clientId XOR、固定时间/随机源向量和非法格式；实现接口与实现类，不 Hash、不 HMAC、不解码授权。

- [x] **Step 3: Hash V1 RED/GREEN**

  投影必须包含 Operation、Client 可控字段、Item 原顺序和 `expiresAt` 缺失标记；排除 Request ID、Credential ID 和 Idempotency Key。先用 Jackson 构造只包含 String/Boolean/Array/Object 的业务 JSON，再调用 `Rfc8785JsonCanonicalizer.canonicalizeObject(byte[])`，最后计算小写 SHA-256。

- [x] **Step 4: 运行聚焦测试**

  ```bash
  cd commerce-service
  ./mvnw -pl paytech-commerce-test -am -Dtest=MoneyValueAutoTest,CheckoutPublicTokenAutoTest,CheckoutRequestHasherAutoTest test
  ```

## Task 4: 定义四个本地 Client Port 和受限 Mock Adapter

**Files:**

- Create under: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/integration/cct/`
- Create under: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/integration/risk/`
- Create under: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/integration/finance/`
- Create under: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/integration/payment/`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/configuration/CommerceIntegrationConfiguration.java`
- Test: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/integration/LocalCheckoutClientsAutoTest.java`
- Test: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/integration/LocalMockProfileBoundaryAutoTest.java`

**Produces:** `CctClient.resolve(...)`、`RiskClient.assess(...)`、`FinanceClient.calculateTax(...)`、`PaymentClient.createIntent(...)` 和确定性本地实现。

- [x] **Step 1: 先写 Client 行为与 Profile 防误用测试并观察 RED**

  测试要求相同输入得到相同外部引用/Hash/税额/Intent，不同 Order 得到不同引用；无临时可变 Response；非 `local|auto|test` Profile 启用 `local-mock` 时启动失败；`disabled` 调用产生稳定依赖不可用异常。

- [x] **Step 2: 定义本地 Commerce-owned Port 模型**

  CCT 结果提供当前 Order Context/Legal 所需的 Assignment、OE、分类、Scope 和 Legal 字段；Risk 结果提供 `assessmentId + ALLOW`；Finance 结果提供 Tax Snapshot、逐 Item 税额和 rounding；Payment 结果提供 Intent ID、`REQUIRES_PAYMENT`、source version/time。模型不得进入 Client API JAR。

- [x] **Step 3: 实现确定性 Local Mock**

  引用 ID 从 `namespace + orderNo` 的 SHA-256 取正 63-bit 非零值；Hash 使用小写 SHA-256；Finance V1 Local Mock 对所有 Item 返回零税和零 rounding；Risk 固定 ALLOW；Payment 固定 `REQUIRES_PAYMENT/version=1`。Mock 只能计算，不访问其他数据库或网络。

- [x] **Step 4: 实现装配和唯一明确 TODO**

  `CommerceIntegrationConfiguration` 默认装配四个 Disabled Client；`local-mock` 模式先校验 Active Profile，再装配 Local Mock，并包含 Global Constraints 中的精确 TODO。不得在 Processor 中出现 Profile 或 Mock 分支。

- [x] **Step 5: 运行聚焦测试**

  ```bash
  cd commerce-service
  ./mvnw -pl paytech-commerce-test -am -Dtest=LocalCheckoutClientsAutoTest,LocalMockProfileBoundaryAutoTest test
  ```

## Task 5: 实现九表持久化和命令幂等恢复

**Files:**

- Create under: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/core/dal/dataobject/{order,checkout,idempotency}/`
- Create under: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/core/dal/mapper/{order,checkout,idempotency}/`
- Create under: `commerce-service/paytech-commerce-web/src/main/resources/mapper/{order,checkout,idempotency}/`
- Create under: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/repository/{order,checkout,idempotency}/`
- Test: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/repository/checkout/CheckoutRepositoryAutoTest.java`
- Test: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/repository/idempotency/CommandIdempotencyRepositoryAutoTest.java`

**Produces:** 原子创建 Order/Context/Legal/Items/Refund/Funds/Session、绑定 Payment Projection、Checkout 查询和可恢复命令记录。

- [ ] **Step 1: Repository 测试 RED**

  使用真实本地 MySQL 覆盖：九表原子插入/回滚、`client_id+client_order_id` 永久唯一、Token binary 唯一、同 Client/Operation/Key 唯一、`SELECT ... FOR UPDATE` Claim、Lease/CAS、stage 单调推进、Response Snapshot 重放和跨 Client 查询为空。

- [x] **Step 2: 实现 DataObject、Mapper 接口与 XML**

  所有 ID 在应用预生成；所有 Instant 使用 Foundation Handler；INSERT 列表显式完整；锁查询使用 `FOR UPDATE`；条件更新同时校验 `version/process_status/recovery_stage`；所有 affected-row 异常失败关闭。

- [x] **Step 3: 实现 Repository interface + Impl**

  `CommandIdempotencyRepository.claim(...)` 使用 `INSERT ... ON DUPLICATE KEY UPDATE command_idempotency_id=command_idempotency_id` 后同事务锁定 winner；`CheckoutRepository.createPreparedAggregate(...)` 一次写入八个业务事实；`confirmPaymentIntent(...)` 原子创建 Payment Projection 并推进 Order/Session；查询必须带 trusted `client_id`。

- [ ] **Step 4: 运行聚焦测试**

  ```bash
  cd commerce-service
  ./mvnw -pl paytech-commerce-test -am -Dtest=CheckoutRepositoryAutoTest,CommandIdempotencyRepositoryAutoTest test
  ```

## Task 6: 实现 Create/Get 业务 Service 和 Processor

**Files:**

- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/service/processor/checkout/CreateCheckoutProcessor.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/checkout/ClientCheckoutService.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/checkout/impl/ClientCheckoutServiceImpl.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/idempotency/CommandIdempotencyService.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/domainservice/service/idempotency/impl/CommandIdempotencyServiceImpl.java`
- Test: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/service/checkout/CreateCheckoutProcessorAutoTest.java`
- Test: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/service/checkout/CheckoutQueryServiceAutoTest.java`

**Produces:** 可由 HTTP Adapter 调用的 Create command/result 和 Checkout query/result。

- [x] **Step 1: 写 Create 正常路径和失败路径 RED**

  历史目标曾包含默认 `+30m`、显式 `+5m..+24h`、Item/金额恒等式、Client ID、四个 Client 调用顺序、Client 调用期间无活动 DB 事务、201 只在 Payment Intent 确认后可构造；其中内部调用级断言已被后继 HTTP-only 设计取代，不再作为当前证据。

- [x] **Step 2: 实现五阶段恢复**

  阶段固定为 `CLAIMED -> AUTHORITIES_RESOLVED -> ORDER_COMMITTED -> PAYMENT_INTENT_CONFIRMED -> COMPLETED`。首次 Claim 冻结 `operationContextTime/effectiveExpiresAt/orderId/orderNo/request snapshot/hash`；同 Key 恢复复用原值，绝不重新生成 Token、Order 或 Payment 子 Key。

- [x] **Step 3: 实现幂等矩阵 RED/GREEN**

  同 Key 同请求成功重放原始 `201/Location/Body`；同 Key 异请求返回 409 `IDEMPOTENCY_KEY_REUSED`；新 Key 复用 `clientOrderId` 返回 409 `CLIENT_ORDER_ID_ALREADY_EXISTS`；正在处理返回 409 `IDEMPOTENCY_REQUEST_IN_PROGRESS` 或 503 `CHECKOUT_CREATION_IN_PROGRESS`，并带受控 Retry-After。旧的“不重复调用 Client”计数断言已被移除，待真实 Owner 协议 Simulator 后再从协议边界验证。

- [x] **Step 4: 实现 GET 查询**

  只读取本地 Order/Session；相同 Client 返回状态与冻结 URL；`now >= expiresAt` 时 URL 为 null；错误 Client 与不存在资源统一 404；GET 不调用四个 Client。

- [x] **Step 5: 运行 Processor/Service 测试**

  ```bash
  cd commerce-service
  ./mvnw -pl paytech-commerce-test -am -Dtest=CreateCheckoutProcessorAutoTest,CheckoutQueryServiceAutoTest test
  ```

## Task 7: 实现 ClientCheckoutController 和 RFC 9457 外部边界

**Files:**

- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/ext/rest/external/clientapi/v1/checkout/ClientCheckoutController.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/ext/rest/external/clientapi/v1/checkout/CheckoutWireMapper.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/ext/rest/external/clientapi/v1/error/CommerceClientApiErrorCode.java`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/configuration/CommerceApiErrorConfiguration.java`
- Test: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/ext/http/clientapi/v1/checkout/ClientCheckoutHttpAutoTest.java`
- Test: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/ext/http/clientapi/v1/checkout/ClientCheckoutTrustedContextHttpAutoTest.java`

**Produces:** 既有 Java/API 契约的真实 Spring MVC 实现。

- [ ] **Step 1: 写真实 HTTP RED**

  从随机端口调用实际 `/commerce/external/client-api/v1/checkouts` 和查询路径，断言三个 trusted Header、Idempotency-Key、201、相对 `Location`、Body、数据库事实和本地 Client 结果。

- [x] **Step 2: 实现 Controller 和 Wire Mapper**

  Controller 只加 `@RestController` 并实现 `ClientCheckoutApi`，不重复 Mapping；解析正 long Client/Credential ID、转换 DTO、调用 Processor/Service、映射成功结果。Credential 只用于可信上下文审计，不进入幂等 scope。

- [x] **Step 3: 闭合可信边界和错误映射**

  缺失/重复/非法 trusted Header 失败关闭；跨 Client 与不存在统一 404。`ApiErrorStatusResolver` 映射 409/422/503/504，Body 固定 `type/title/detail/code/requestId/timestamp/errors?`，无 `status/instance/message`，Header 与 Body Request ID 相同。

- [x] **Step 4: 配置日志与缓存边界**

  Client API 路径使用 metadata-only 日志，响应带 `Cache-Control: no-store`，不记录 Checkout URL/Token 或请求快照。确定性 Local Mock 不维护调用 Journal 或计数器。

- [ ] **Step 5: 运行全部 Checkout HTTP 测试**

  ```bash
  cd commerce-service
  ./mvnw -pl paytech-commerce-test -am -Dtest='*ClientCheckout*AutoTest' test
  ```

## Task 8: 完整验证与状态台账

**Files:**

- Modify after evidence: `commerce-service/README.md`
- Modify after evidence: `agent-docs/CURRENT_IMPLEMENTATION.md`
- Verify only: `requirements-site/content/requirements.ts`
- Verify only: `requirements-site/content/global-use-case-sequences.mjs`

- [ ] **Step 1: 运行 Commerce 全量门禁**

  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 25)
  export PATH="$JAVA_HOME/bin:$PATH"
  cd commerce-service
  ./mvnw clean verify
  ./mvnw dependency:tree
  ```

- [x] **Step 2: 运行模型和 Requirements 门禁**

  ```bash
  cd ..
  node scripts/pdmaner/generate-commerce-model.mjs --check
  node --test scripts/pdmaner/commerce-model.test.mjs scripts/pdmaner/commerce-scenario-doc.test.mjs
  npm --prefix requirements-site test
  npm --prefix requirements-site run lint
  ```

- [x] **Step 3: 运行静态安全检查**

  ```bash
  git diff --check
  git -C commerce-service diff --check
  rg -n 'TODO\(COMMERCE-OWNER-CONTRACTS\)' commerce-service/paytech-commerce-web/src/main/java
  rg -n '@Transactional|@MockBean|@MockitoBean|org\.mockito|Mono<|Flux<' commerce-service/paytech-commerce-web commerce-service/paytech-commerce-test
  ```

  TODO 必须恰好出现一次；禁止模式扫描应无匹配。

- [x] **Step 4: 更新实际状态而非目标状态**

  只有相应门禁真实通过后才记录：两条 Owner Route 已实现、本地 Mock 模式、已生成 Schema、物理数据库是否实际应用、测试数量和未完成的 Owner 合同/Gateway/Checkout Web。不得将本地 Mock 表述为外部可用或部署完成。

- [x] **Step 5: 最终核对用户资产**

  对比开场 Git 状态；确认没有覆盖 `COMMERCE_PHYSICAL_DECISIONS.md` 的既有用户修改、根工作区 Risk/test-environment 改动或其他仓库内容。未经新授权不提交、不推送、不部署、不应用数据库 SQL。
