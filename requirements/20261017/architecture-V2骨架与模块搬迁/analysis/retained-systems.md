# 保留系统盘点（Retained Systems Inventory）

> 范围：`/Users/kenny/Desktop/Workspace/Code-MoR-refactor` 中搬迁但不重切的系统：paytech-build、paytech-foundation、platform-support-service、api-gateway、operations-console-bff、client-console-bff、vault-service（Go + Java Client）。`partner-console-bff` 不存在。
> 方式：只读盘点（2026-10-17，HEAD `4aed55d`）；未修改代码，未运行构建或测试。测试数字来自 `agent-docs/CURRENT_IMPLEMENTATION.md`。

## 结论摘要

- 保留系统之间不存在模块级依赖环，可以组成单一 `paytech/` Reactor。
- 今天唯一的“环”在项目根层面：Platform 依赖 `paytech-payment-api`，Payment 依赖 `platform-support-api` 与 `paytech-vault-client`，靠 `~/.m3` 中的既有 JAR 才能引导；单 Reactor 后自然消除。
- 纯搬迁会破坏的五类东西：Platform 覆盖率门禁中的 `${maven.multiModuleProjectDirectory}`、全部根 POM 的空 `<relativePath/>`、按目录名定位仓库的测试、`../` 耦合的 test-environment 与 Vault 脚本、64 个文档中的 217 个相对链接。
- `paytech-compensation-event` 不含任何 RabbitMQ 代码；MQ 只在 Payment、Risk（发布）与 Commerce、CCT（消费）中；Platform 通过 Enforcer 禁止 `spring-amqp`。
- 内部服务调用身份只有可伪造的明文 `X-Caller-Service` 加配置白名单；人类身份由 Gateway 以 `X-Gateway-*` 头传给 BFF，BFF 不再复核。

## 1. 当前 Maven 结构

仓库根有 `mvnw` 与 `.mvn/wrapper`，但没有 `pom.xml`。每个项目根各自携带 Wrapper：wrapper 3.3.4、Maven 3.9.16、`distributionType=only-script`。只有 `platform-support-service`、`operations-console-bff`、`client-console-bff` 固定了 `distributionSha256Sum`。各根都没有 `maven.config` 或 `jvm.config`。Maven Settings 来自 `~/.m2/settings.xml`（链接到 `/Users/kenny/Desktop/Workspace/Settings/3-Maven/settings/local_settings.xml`），本地仓库为 `~/.m3/repository`。

| 项目根 | 模块 | 父 POM | 可部署主类 |
|---|---|---|---|
| `paytech-build` | `paytech-dependencies`（BOM）、`paytech-parent` | `paytech-parent` → `../paytech-dependencies/pom.xml` | 无 |
| `paytech-foundation` | 13 个：common-core、http-problem、json-canonicalization、http-client-support、test-support、mybatis-support、command-idempotency、snowflake-id、mvc-web-starter、caffeine-starter、redis-starter、xxl-job-starter、compensation-event | `paytech-parent`，`<relativePath/>` | 无 |
| `platform-support-service` | `-api`、`-access-control`、`-audit-security`、`-file-document`、`-delivery`、`-integration`、`-web`、`-test` | 同上 | `platform-support-web/src/main/java/com/paytech/platform/support/PlatformSupportApplication.java` |
| `api-gateway` | `api-gateway-web`、`api-gateway-test` | 同上 | `api-gateway-web/src/main/java/com/paytech/gateway/ApiGatewayApplication.java` |
| `operations-console-bff` | `paytech-operations-console-bff-web`、`-test` | 同上 | `.../operations/console/bff/OperationsConsoleBffApplication.java` |
| `client-console-bff` | `paytech-client-console-bff-web`、`-test` | 同上 | `.../client/console/bff/ClientConsoleBffApplication.java` |
| `vault-service` | `paytech-vault-client`、`vault-test`（artifactId `paytech-vault-test`） | 同上 | Go `cmd/vault-server`；Go module `github.com/paytech/vault-service` |

`paytech-parent`（`paytech-build/paytech-parent/pom.xml`）：

- Java 25；Enforcer 要求 Java `[25,26)`、Maven `[3.9.16,4)`。
- Surefire/Failsafe 包含 `**/auto/**/*Test.java`、排除 `**/manual/**/*Test.java`；Profile `manual-test` 反转。
- 插件版本：compiler 3.14.1、surefire/failsafe 3.5.4、enforcer 3.6.2、jacoco 0.8.13、spring-boot-maven-plugin 4.0.8。
- `spring-boot-maven-plugin` 只在四个 web 模块使用：platform-support-web、api-gateway-web、两个 BFF web。

模块规模（main Java 文件数）：

| 模块 | Java 文件 | 包根 |
|---|---|---|
| platform-support-api | 292 | `com.paytech.platform.support.api` |
| platform-support-access-control | 522 | `...support.accesscontrol` |
| platform-support-audit-security | 22 | `...support.auditsecurity` |
| platform-support-file-document | 34 | `...support.filedocument` |
| platform-support-delivery | 175 | `...support.delivery` |
| platform-support-integration | 235 | `...support.integration` |
| platform-support-web | 161 | `...support.{configuration,core,integration,ext,service}` |
| api-gateway-web | 128 | `com.paytech.gateway.*` |
| paytech-operations-console-bff-web | 238 | `com.paytech.operations.console.bff` |
| paytech-client-console-bff-web | 31 | `com.paytech.client.console.bff` |
| paytech-vault-client | 38 | `com.paytech.vault.client.*` |

注意：`platform-support-web` 含包 `com.paytech.platform.support.integration`，与 `platform-support-integration` 模块同包（Split Package）。只有当 api/core/app 重切把包映射到模块时才会成为问题。

`@HttpExchange` 合同位置：

- Platform：`platform-support-service/platform-support-api/src/main/java/com/paytech/platform/support/api/**`，36 个接口，包括 `gatewayconfiguration/GatewaySnapshotQueryApi`、`authentication/{Operations,Customer}JwksQueryApi`、`authorization/{Operations,Client}AuthorizationApi`、`webhook/WebhookEventApi`、`notification/NotificationCommandApi`、`apicredential/ApiCredentialValidationApi`、`externalapi/*` 等。
- Vault：`vault-service/paytech-vault-client/src/main/java/com/paytech/vault/client/internal/VaultHttpService.java`、`VaultAdminHttpService.java`。
- Gateway 与两个 BFF 不发布 API 模块。

## 2. 跨项目依赖

全部 groupId 为 `com.paytech`。

| 消费模块 | 依赖的 com.paytech 制品 |
|---|---|
| api-gateway-web | paytech-common-core、paytech-http-problem、platform-support-api |
| paytech-operations-console-bff-web | risk-api、platform-support-api、client-contract-api、paytech-vault-client、paytech-mvc-web-starter、paytech-http-client-support |
| paytech-operations-console-bff-test | risk-api（test）、paytech-operations-console-bff-web |
| paytech-client-console-bff-web | client-contract-api、risk-api、platform-support-api、paytech-mvc-web-starter、paytech-http-client-support |
| platform-support-api | paytech-common-core |
| platform-support-access-control | platform-support-api、-audit-security、common-core、redis-starter、snowflake-id、paytech-vault-client |
| platform-support-audit-security | common-core、snowflake-id |
| platform-support-file-document | platform-support-api、common-core |
| platform-support-delivery | platform-support-api、common-core、compensation-event、paytech-vault-client、json-canonicalization |
| platform-support-integration | platform-support-api、-access-control、-audit-security、common-core、http-problem、redis-starter、snowflake-id、paytech-vault-client、compensation-event、**paytech-payment-api** |
| platform-support-web | -delivery、-integration、-file-document、-api、-access-control、-audit-security、compensation-event、**client-contract-api**、http-client-support、redis-starter、snowflake-id、mvc-web-starter、mybatis-support |
| platform-support-test | paytech-test-support（test）、platform-support-web |
| paytech-vault-client | common-core、http-problem |
| paytech-vault-test | paytech-vault-client（test） |

Foundation 内部：common-core 是根；http-problem、mybatis-support、snowflake-id、xxl-job-starter、caffeine-starter、test-support 依赖 common-core；http-client-support 与 mvc-web-starter 依赖 common-core + http-problem；command-idempotency 依赖 common-core + mybatis-support；compensation-event 依赖 common-core + mybatis-support + snowflake-id + xxl-job-starter；redis-starter 依赖 common-core（caffeine-starter 仅 test）。

业务服务对保留制品的使用：

- Commerce web → platform-support-api；Commerce 根 POM 禁止 paytech-vault-client。
- CCT web → platform-support-api、paytech-vault-client、compensation-event、json-canonicalization。
- Payment channel → paytech-vault-client、platform-support-api；Payment card → paytech-vault-client；Payment web → platform-support-api；Payment execution → compensation-event。
- Risk compliance/decision/web → compensation-event；Finance accounting → json-canonicalization。
- 根 POM 之外没有消费者使用 paytech-command-idempotency。

环检测：

- 模块级无环。所有业务 `*-api`（client-contract-api、risk-api、paytech-payment-api、paytech-finance-api、paytech-commerce-client-api、paytech-commerce-customer-api）只依赖 common-core；platform-support-api 只依赖 common-core；paytech-vault-client 只依赖 common-core + http-problem。
- 项目根级存在互相依赖：Platform → paytech-payment-api，Payment → platform-support-api / paytech-vault-client；CCT ↔ Platform 同理（Platform-web → client-contract-api，CCT-web → platform-support-api）。今天依赖本地仓库中已安装的 Snapshot 引导；单 Reactor 后按模块拓扑排序即可。

版本与 Enforcer 注意点：

- BFF POM 对其他服务 API 硬编码 `1.0.0-SNAPSHOT`；Platform `-integration` 用 `${project.version}`。单 Reactor 中应统一由 BOM 或 `${project.version}` 管理。
- `api-gateway/pom.xml` 的 bannedDependencies 禁止 `com.paytech:client-contract-*`、`commerce-*`、`payment-orchestration-*`、`risk-compliance-*`、`finance-*` 与 `platform-support-web`。实际 artifactId 是 `paytech-commerce-*`、`paytech-payment-*`、`risk-*`、`paytech-finance-*`，所以只有 `client-contract-*` 与 `platform-support-web` 实际生效。
- Platform 根 POM 禁止 nacos-config、reactive redis、`spring-boot-starter-amqp`、`org.springframework.amqp:*`、`com.xuxueli:*`（放行 xxl-job-core、xxl-tool）、caffeine-starter、Flyway/Liquibase、Spring Security（放行 crypto）、Spring Cloud Gateway、Mockito、Testcontainers。
- 两个 BFF 根 POM 禁止 JDBC/JPA/MyBatis/MySQL/Hikari、Redis/Redisson、AMQP/RabbitMQ、XXL、mybatis-support、caffeine/redis-starter、compensation-event、xxl-job-starter、Security、WebFlux、Gateway、OpenFeign、Dubbo、Mockito、Testcontainers。
- Gateway 禁止全部 Servlet/MVC 栈、Spring Cloud Gateway starter、redis/caffeine/mvc-web-starter、Redisson、Testcontainers、Mockito、nacos-config。
- 这些规则都在各项目根聚合 POM，搬迁时必须挂到新的服务聚合 POM，不能放到全局 parent。

## 3. compensation-event 库

位置：`paytech-foundation/paytech-compensation-event`，包 `com.paytech.foundation.compensation`。自动配置：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中的 `core/autoconfigure/CompensationEventAutoConfiguration` 与 `CompensationXxlJobAutoConfiguration`。依赖 mybatis-spring-boot-starter、xxl-job-core、jackson、micrometer；不含 RabbitMQ。

### 3.1 公共 API（`api/`）

- `CompensationEventHandler`：`eventType()`、默认 `policy()`（`CompensationExecutionPolicy.defaults()`：timeout 30s、最多 5 次、退避 1s→5min、倍数 2.0、抖动 0.2）、`handle(CompensationEvent)` 返回 `CompensationHandlerResult`（SUCCESS / RETRYABLE_FAILURE / TERMINAL_FAILURE，错误码 `[A-Z][A-Z0-9_-]{0,63}`）。
- Handler 以 Spring Bean 收集，由 `core/execution/CompensationHandlerRegistry.java` 索引：每个 eventType 只能有一个 Handler；Handler timeout 必须短于 Lease；注册未配置的 eventType 会失败（`CONFIGURATION_INVALID`）。
- `CompensationEventRegistrar.register(CompensationEventRequest)`：请求为 `(eventKey ≤128, eventType ≤64, subEventType ≤64, JsonNode payload 可选对象)`。实现 `service/CompensationEventRegistrationService.java`：
  - 经 `core/transaction/CompensationTransactionBoundary.executeMandatory` 要求已有 Spring 事务，且 MyBatis DataSource 与 `DataSourceTransactionManager` 必须是同一 Bean。
  - 按身份 `(event_key, event_type, sub_event_type)` 去重；重复但 Payload 不同 → `FailException(EVENT_CONFLICT)`；返回 `RegisteredCompensationEvent(eventId, duplicate)`。
  - ID 由 snowflake `IdGenerator` 生成。
- 提交后唤醒：`core/execution/CompensationAfterCommitWakeup.java` 注册 `TransactionSynchronization.afterCommit` → `CompensationEventWakeup.wakeUp(eventId)` → `CompensationEventDispatcher`（`Semaphore` 控制 Handler 并发）。唤醒失败只记日志，由扫描兜底。
- 兜底扫描：`ext/job/CompensationEventXxlJobHandler.java`，`@XxlJob("compensationEventScan")`，Job 参数为可选逗号分隔 eventType；分片 SQL 在 `src/main/resources/META-INF/mybatis/CompensationEventMapper.xml` 第 170 行 `MOD(id, #{shardCount}) = #{shardIndex}`；Claim 使用 `FOR UPDATE` 与 `lock_version`。
- 人工重开：`CompensationEventRecoveryService`（`service/CompensationEventRecoveryServiceImpl.java`），只在宿主提供 `CompensationRecoveryAuditRecorder` 时暴露。
- 配置：`core/autoconfigure/CompensationEventProperties.java`，前缀 `compensation`（`ignoreUnknownFields=false`）：`enabled`（默认 true）、`instance-id`、`lease-duration`（2min）、`scanner-batch-size`（100）、`coordinator.{core-size 10, maximum-size 20, queue-capacity 80, keep-alive 60s}`、`handler.maximum-concurrency`（20）。
- 观测：`core/observability/MicrometerCompensationEventObserver.java`。

### 3.2 表结构（测试 DDL：`src/test/resources/schema/compensation-event.sql`）

`t_compensation_event`：`id BIGINT PK`、`event_key VARCHAR(128)`、`event_type VARCHAR(64)`、`sub_event_type VARCHAR(64)`、`payload_json VARCHAR(4096)`、`execute_status`（默认 INIT）、`execute_count`、`next_execute_at DATETIME(6)`、`lock_owner`、`lock_until`、`last_error_code`、`last_error_summary`、`lock_version`、`create_time`、`update_time`；`UNIQUE uk_comp_event_identity(event_key, event_type, sub_event_type)`、`idx_comp_event_ready(execute_status, next_execute_at, id)`、`idx_comp_event_lease(execute_status, lock_until, id)`。

`t_compensation_recovery_audit` 为可选表（启用人工重开时由宿主创建）。

成功执行后事件行被删除（见 `agent-docs/COMPENSATION_EVENT_GUIDE.md` §2 序列图），因此该表不是持久事件日志。

### 3.3 当前 RabbitMQ 使用（仅业务服务；保留系统没有）

| 发布方 | Exchange（Durable Topic） | Routing Key | 消费方队列 |
|---|---|---|---|
| Payment `payment-orchestration-service/paytech-payment-web/src/main/java/com/paytech/payment/integration/mq/PaymentResultRabbitPublisher.java`（Exchange 声明 `configuration/PaymentMessagingConfiguration.java`） | `topic_payment_result` | `payment.succeeded.v1`（eventType `PaymentSucceeded`） | Commerce `cg_commerce_payment_result`；DLX `dlx_commerce_payment_result` → `cg_commerce_payment_result_dlq` |
| Risk `risk-compliance-service/risk-web/src/main/java/com/paytech/risk/ext/mq/ClientReviewResultRabbitPublisher.java`（声明 `configuration/ClientReviewMessagingConfiguration.java`） | `topic_risk_client_review` | `client_review.decided.v1`（eventType `ClientReviewDecided`） | CCT `cg_cct_client_review`；DLX `dlx_cct_client_review` → `cg_cct_client_review_dlq` |

- 消费方配置：`commerce-service/paytech-commerce-web/.../configuration/PaymentResultConsumerConfiguration.java` 与 `client-contract-service/client-contract-web/.../configuration/ClientReviewConfiguration.java`：Quorum Queue、`deliveryLimit=5`、DLX/DLQ、手动 Ack、拒绝不重新入队、Prefetch 10；消费方幂等声明发布方 Topic Exchange 以便绑定。
- 发布在补偿 Handler 内执行（Payment：`paytech-payment-execution/.../service/handler/PaymentSucceededHandler.java`）：`rabbitTemplate.send` + `CorrelationData` 等待 Publisher Confirm，并检查 Returned。
- Payment 已手工构造信封：`eventKey`、`eventType`、`sourceSystem`、`sourceType`、`sourceId`、`sourceVersion`、`occurredAt`、`schemaVersion=1`、`payloadHash`（sha256）、`payload`；AMQP `messageId=eventKey`、`type=eventType`、Persistent。
- 当前补偿 Handler 清单（13 个）：CCT 3 个（ClientClassification、ClientReviewRegistration、ClientTenantProjection）、Commerce 3 个（ClientWebhookSubmission、RefundSubmission、StoredCardInvalidation）、Payment 1 个（PaymentSucceeded）、Platform 4 个（`platform-support-delivery/.../ext/eventhandler/NotificationDeliveryHandler.java`、`WebhookDeliveryHandler.java`、`platform-support-integration/.../ext/eventhandler/IntegrationVerifyRunHandler.java`、`platform-support-web/.../service/handler/FileScanHandler.java`）、Risk 2 个（ClientReviewResult、ShadowRun）。

### 3.4 增加事件信封与一致性哈希路由需要的改动

- 治理冲突：`agent-docs/COMPENSATION_EVENT_GUIDE.md` §4（第 119 行）明确 JAR 不内置 RabbitMQ Route、`RabbitTemplate`、Canonical Envelope 或 Publisher Connection。需要新决定，或新增独立 Foundation 模块（例如 messaging/outbox-publisher），不应直接塞进 compensation-event。
- 信封字段：event id（可用 eventKey 或 snowflake 行 id）、aggregate key、aggregate version、schema version。Guide 第 139 行规定领域版本来自业务合同而非 `t_compensation_event`，因此 aggregate version 要么放进 Payload，要么给表加列（需改 PDManer、各服务 Schema 与 Mapper XML）。
- `payload_json VARCHAR(4096)` 可能限制信封体积。
- 一致性哈希：两个 compose 的 RabbitMQ 需启用 `rabbitmq_consistent_hash_exchange` 插件；发布方按 aggregate key 设 Routing Key 或 Header；消费方声明 N 个分区队列并设置 `x-single-active-consumer`；Quorum Queue 支持 SAC。
- 顺序性：当前 Dispatcher 并发执行、扫描按 `MOD(id)` 分片，不保证同一聚合的发布顺序。单活消费者只能保证消费侧顺序，发布侧仍需按 aggregate key 串行或由消费侧按版本丢弃旧事件。
- Platform 若要发布 MQ，必须先修改 `platform-support-service/pom.xml` 中对 `org.springframework.amqp:*` 与 `spring-boot-starter-amqp` 的禁止规则；BFF 禁止 AMQP 应保持。

## 4. 内部调用方身份

### 4.1 服务间：`X-Caller-Service`（明文，可在私网伪造，文档标注“过渡信任”）

- Platform Webhook：常量 `WebhookEventApi.CALLER_SERVICE_HEADER = "X-Caller-Service"`（`platform-support-api/.../api/webhook/WebhookEventApi.java`）；白名单 `platform-support-web/.../configuration/WebhookCallerProperties.java`，配置键 `webhook.http.callers`（服务名 → 可提交事件类型）；在 `ext/rest/webhook/WebhookEventController.java` 调用 `callers.authorize(...)`，调用方作为来源系统保存。
- Platform Notification：`NotificationCommandApi` 同一请求头；白名单 `NotificationCallerProperties.java`，配置键 `notification.http.callers`（服务名 → 普通邮件模板）；`ext/rest/notification/NotificationController.java`。
- 两个白名单在 `platform-support-web/src/main/resources/application.yml` 第 192、213 行默认 `callers: {}`。
- Vault：Java Client 在 `paytech-vault-client/.../internal/VaultRequestHeaders.java` 设置 `X-Caller-Service`，值来自 `autoconfigure/VaultClientProperties.callerService`；Platform 配置 `vault.caller-service: platform-support-service`（application.yml 第 40 行）。Go 服务端 `vault-service/internal/server/headers.go` 读取唯一且合法的值，否则为 `UNKNOWN`；访问规则按 `Caller|Action|DataTypeCode|Purpose` 匹配（`internal/biz/policy.go`），管理接口见 `vault-service/api/openapi/admin-v1.yaml`。
- 业务服务：Risk `risk-api/.../compliance/ClientReviewRegistrationApi.java` 接收该头；CCT `client-contract-web/.../configuration/CheckoutHttpBoundaryConfiguration.java` 拒绝重复的该头；CCT 多个 onboarding API 也声明它。
- Foundation：`paytech-http-client-support/.../core/transport/HttpServiceExchangeInterceptor.java` 只传播 `X-Request-Id`，没有统一的调用方身份拦截器。

### 4.2 Gateway → BFF → Platform：人类身份头

- `api-gateway-web/src/main/java/com/paytech/gateway/web/GatewayProtocol.java` 定义并下发：`X-Gateway-Actor-Subject`、`X-Gateway-Session-Id`、`X-Gateway-Actor-Type`、`X-Gateway-Channel`、`X-Gateway-Realm`、`X-Gateway-Context-Type`、`X-Gateway-Tenant-Id`、`X-Gateway-Tenant-Type`、`X-Gateway-Tenant-Member-Id`、`X-Gateway-Route-Code`、`X-Entry-Channel`、`X-Request-Id`；另有 `ApiHeaderNames.GATEWAY_CLIENT_ID`、`GATEWAY_CREDENTIAL_ID`（`paytech-common-core/.../common/http/ApiHeaderNames.java`）。
- JWT 校验在 Gateway：`security/ReactiveJwksProvider.java`、`OperationsSecurityConfiguration.java`、`ClientSecurityConfiguration.java`（从 Platform JWKS API 读取公钥）。
- BFF 直接信任这些头，不复核：`operations-console-bff/.../ext/rest/OperatorContextArgumentResolver.java`、`client-console-bff/.../service/processor/ClientSessionProcessor.java`。
- BFF 把 Actor-Subject 与 Session-Id 作为头转给 Platform，例如 `OperationsAuthorizationApi.authorize(@RequestHeader("X-Gateway-Actor-Subject"), @RequestHeader("X-Gateway-Session-Id"), ...)`；约 171 处引用。
- PLT-I03 是 Gateway 调用的外部 Client API Credential 校验（`ApiCredentialValidationApi`），不用于内部服务间认证。

### 4.3 用 Platform 签发内部 Token 替换时的范围

- 需替换或兼容的入口：Platform Webhook/Notification 两个 Controller 与两个 Properties；Vault Go `headers.go` 与访问规则中的 Caller 维度；Risk、CCT 接收该头的 API；所有调用方客户端（Vault Client 以及各服务构造 `X-Caller-Service` 的位置）。
- 最自然的注入点是 `paytech-http-client-support` 的出站拦截器与 `paytech-mvc-web-starter` 的入站校验；Gateway（WebFlux）需单独实现，因为它禁止 mvc-web-starter。
- 是否同时签名 `X-Gateway-*` 人类上下文，需要单独决定；目前 BFF 与 Platform 只靠网络隔离信任。

## 5. 配置与环境

- `test-environment/compose.applications.yaml`：Java 服务均以 `context: ./artifacts`、`dockerfile: ../java/Dockerfile`、`JAR_FILE: <service>.jar` 构建；Vault 用 `context: ./artifacts/vault`、`dockerfile: ../../vault/Dockerfile`。Compose 本身与源码目录解耦。
- `test-environment/scripts/build.sh` 与源码目录强耦合：
  - `cd $workspace/paytech-build && ./mvnw -DskipTests clean install`
  - `cd $workspace/paytech-foundation && ./mvnw -pl paytech-mvc-web-starter,paytech-http-client-support,paytech-mybatis-support,paytech-redis-starter,paytech-snowflake-id,paytech-json-canonicalization -am ... install`
  - `risk-compliance-service -pl risk-web`、`platform-support-service -pl platform-support-web`、`api-gateway -pl api-gateway-web`、`operations-console-bff -pl paytech-operations-console-bff-web`
  - 固定复制 `platform-support-service/platform-support-web/target/platform-support-web-1.0.0-SNAPSHOT-exec.jar`、`api-gateway/api-gateway-web/target/api-gateway-web-1.0.0-SNAPSHOT-exec.jar`、`operations-console-bff/paytech-operations-console-bff-web/target/paytech-operations-console-bff-web-1.0.0-SNAPSHOT-exec.jar`、`risk-compliance-service/risk-web/target/risk-web-1.0.0-SNAPSHOT-exec.jar`
  - 还执行 `operations-console-web` 的 `npm ci && npm run build`。
- `test-environment/scripts/build-vault.sh` 与 `local-environment/vault/vault.sh`：`cd $workspace/vault-service && go build ./cmd/vault-server`，并编译 `test-environment/vault/probe.go`。
- `local-environment/compose.yaml` 的 vault-service：`context: ./vault/bin/image`、`dockerfile: ../../../../test-environment/vault/Dockerfile`。
- SQL 路径耦合：`test-environment/scripts/initialize.py` 读 `platform-support-service/docs/database/sql/platform-support-initial-data.sql` 与 `risk-compliance-service/docs/database/sql`；`scripts/vault.py` 读 `vault-service/docs/database/sql/vault-schema.sql`；`test_risk_database_initialization.py`、`test_risk_rule_configuration_catalog.py` 读固定 SQL；`test_nginx_gateway_problem_boundary.py` 读 `api-gateway/api-gateway-web/src/main/resources/application.yml`。
- `test-environment/scripts/prepare.py` 只检查 `~/.m3` 中 crypto JAR，并提示“先安装 paytech-build/Foundation/Platform”。
- `test-environment/deploy.sh` 只按 compose 服务名与镜像名工作，不引用源码目录。
- 两个 scheduler compose 都挂载 `local-environment/mysql/init`（test-environment 经 `../local-environment/mysql/init`）。
- 不受目录搬迁影响：Nacos dataId 与 XXL-Job 执行器按 `spring.application.name`；nginx 代理到 `$gateway` / `$frontend` 变量。

| 服务 | spring.application.name | 端口 | 管理端口 |
|---|---|---|---|
| Platform | platform-support-service | 18106 | 同端口 |
| Gateway | api-gateway | 18080 | 19080 |
| Operations BFF | operations-console-bff | 18201 | 19201 |
| Client BFF | client-console-bff | 18202 | 19202 |

## 6. 构建、CI 与脚本

- `scripts/verify-java-foundation.mjs`：硬编码 `paytech-build`、`paytech-dependencies`、`paytech-parent` 以及每个 `paytech-foundation/<module>/pom.xml`，并检查 BOM 版本治理。
- `scripts/verify-local-nexus-settings.mjs`：使用仓库根 `mvnw`。
- `scripts/verify-local-environment.mjs`：检查 `local-environment` 与若干 agent-docs 路径。
- `api-gateway/scripts/verify-gateway-boundaries.mjs`、`verify-jacoco.mjs`：以脚本目录的上一级为根，期望 `api-gateway-web/src/main/java`、`api-gateway-web/src/main/resources/application.yml`、`api-gateway-test/src/test`、`api-gateway-test/target/site/jacoco-aggregate`、`api-gateway-test/target/jacoco.exec`、`api-gateway-web/target/classes`、`README.md`。
- `scripts/pdmaner/*`：约 30 个生成器与测试硬编码 `<service>/docs/database/<name>.pdma.json`、`*_PHYSICAL_DECISIONS.md`、`sql/*-schema.sql`；`pdmaner-canonical-paths.test.mjs` 检查六个核心服务加 vault-service；`platform-support-schema.test.mjs` 引用 `platform-support-service/integration-create.sql`；`platform-support-model.test.mjs` 与 `platform-support-scenario-doc.test.mjs` 引用 api-gateway、两个 BFF、commerce、payment 目录。
- `vault-service/scripts/verify.sh`：从脚本上一级运行，检查 `root/mvnw` 与 `root/.mvn/wrapper/maven-wrapper.properties`，并以 `-Dmaven.repo.local` 调用 Maven。
- `requirements-site` 测试（`npm run build && node --test tests/*.test.mjs`）：只读 `requirements-site/content/*` 与 `../../agent-docs/**`，与服务目录无耦合。
- Maven 内无 node/exec 绑定；Gateway JaCoCo 用 `report-aggregate`（`includeCurrentProject=true`）。
- 文档路径：`agent-docs`、`docs`、`requirements`、`AGENTS.md`、`README.md` 中对保留目录的路径提及 4,495 处，其中相对 Markdown 链接 217 个、分布在 64 个文件。
- 绝对路径：26 个文件硬编码 `/Users/kenny/Desktop/Workspace/Code-MoR/`（主要在 `docs/superpowers`）；代码类文件有 `test-environment/scripts/test_risk_rule_configuration_authorization_inventory.py` 与 `requirements/20260912/operations-console-统一风控变量管理与条件编排/assets/stage5/harness/web.mjs`。在 refactor 副本中它们仍指向旧工作区。

## 7. 测试基线

auto/manual 机制：`paytech-parent` 的 `surefire.includes=**/auto/**/*Test.java`、`surefire.excludes=**/manual/**/*Test.java`；`-Pmanual-test` 反转。各根另设 `failIfNoSpecifiedTests=false`，`platform-support-test` 为 `true`。

| 项目 | 测试类 | `auto/` | `manual/` | 文档记录的最近数字（CURRENT_IMPLEMENTATION.md） |
|---|---|---|---|---|
| paytech-build | 0 | 0 | 0 | — |
| paytech-foundation | 68 | 67 | 0 | 全 Reactor `clean verify` 607/607；另有 505/505、410/410（Redis Starter 120/120） |
| platform-support-service | 80 | 79 | 1 | 736/736（最新）；另有 647/647、617/617、671 例中 3 例错误来自其他会话改动 |
| api-gateway | 74 | 72 | 0 | 1277/1277；另有 1261/1261 |
| operations-console-bff | 27 | 27 | 0 | 1386/1386；另有 1378/1378 |
| client-console-bff | 6 | 6 | 0 | 57/57；另有 76/76、47/47、35/35 |
| vault-service | 8 Java + 29 Go `_test.go` | 7 | 1 | Vault Java Client staged tree 34/34 |

不在 `auto/` 目录、因此今天从不执行的测试类：

- `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/configuration/PlatformSnapshotControlClientAutoTest.java`
- `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/routing/snapshot/GatewaySnapshotCodeParserAutoTest.java`
- `paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/test/infrastructure/RedisLocalEnvironmentPreconditionAutoTest.java`

测试基类：

- Platform：`platform-support-test/src/test/java/com/paytech/platform/support/test/base/MockTestBase.java`，以及 `AuthenticationEmailTestBase`、`NotificationManagementTestBase`、`FileStorageAdapterTestBase`、`ApiCredentialValidationTestBase`、`WebhookTestBase`、`OnboardingFileTestBase`、`OperationsApiTestBase`、`NotificationTestBase`、`ClientTeamTestBase`、`ExternalApiTestBase`、`ManualTestBase`。
- Gateway：`api-gateway-test/.../gateway/test/GatewayAutoTestBase.java`、`ClientApiGatewayAutoTestBase.java`、`RedisUnavailableGatewayAutoTestBase.java`。
- Operations BFF：`.../bff/test/base/OperationsBffHttpTestBase.java`。
- compensation-event：`.../compensation/test/base/MockTestBase.java`。
- Foundation 共享：`paytech-test-support` 提供 `TestBase`、`MockHttpServer`、`EnableMockHttpServer`、`MockHttpFault`、`HttpTestInvoker`、`AbstractHttpResponseChecker`、`TestRequestHeaders`、`Clear`。

覆盖率门禁：`platform-support-test/pom.xml` 的 antrun `verify-task-nine-coverage` 以 `jacoco:check failonviolation="true"` 要求 INSTRUCTION 与 BRANCH ≥ 0.90，是已知既有失败（最近记录 0.54 / 0.37）。单 Reactor 的 `mvn verify` 会在 Platform 处失败，除非限定门禁范围或使用 `-fae`。Gateway 另有 `verify-jacoco.mjs` 脚本门禁。

## 8. 纯目录或模块搬迁的十大风险

1. **Platform 覆盖率 fileset 失效。** `platform-support-test/pom.xml` 中 6 处 `${maven.multiModuleProjectDirectory}/platform-support-*/target/classes` 会解析到新的 `.mvn` 根，得到空报告或失败。`commerce-service/paytech-commerce-test`、`finance-service/paytech-finance-test`、`risk-compliance-service/risk-test` 的 POM 各有 1 处同类写法。
2. **父 POM 从仓库而非 Reactor 解析。** 所有根都对 `paytech-parent` 使用 `<relativePath/>`。Maven 3.9 不会从 Reactor 解析该父 POM，所以 `paytech/` 需要真实相对路径，或必须先单独安装 build POM。
3. **Platform 测试的仓库根标记失效。** `platform-support-test/.../test/configuration/LocalMockEnvironmentInitializer.java` 第 89–91 行以 `local-environment/.env.example` 加 `platform-support-service/pom.xml` 识别仓库根；失效后无法加载本地 MySQL/Redis/MinIO 凭据（`mor.local-environment`），`OnboardingFileTestBase` 也依赖它。
4. **其他测试依赖相邻目录布局。** Operations BFF 的 `RiskClientConfigurationAutoTest.java` 与 `OperationsConsoleBffApplicationAutoTest.java` 向上查找名为 `paytech-operations-console-bff-web` 的目录；`paytech-redis-starter/.../auto/architecture/ResourceLockArchitectureAutoTest.java` 扫描 `moduleRoot().getParent()`；Redis 前置检查的提示文本使用 `../local-environment`；Gateway `RepositoryRedisEnvironment` 的说明使用 `cd local-environment`。
5. **Vault Java 测试绑定 Go 模块根。** `vault-service/vault-test/.../auto/VaultEndToEndTest.java` 运行 `../mvnw` 与 `../scripts/verify.sh`；`paytech-vault-client/.../auto/VaultClientHttpTest.java` 读取 `../testdata/wire/`。Java Client 与 Go 根、Wrapper 必须保持相对位置，或同步修改。
6. **test-environment 构建脚本失效。** `build.sh` 的逐根 `cd ... ./mvnw -pl` 与固定 `target/*-exec.jar` 复制路径都会失效；`build-vault.sh` 与 `vault.sh` 进入 `vault-service`。
7. **Dockerfile 引用与 SQL 读取失效。** 本地 compose 使用 `../../../../test-environment/vault/Dockerfile`；`initialize.py`、`vault.py` 与 Risk 初始化测试读取固定的 `docs/database/sql` 路径。
8. **Node 工具失效。** `verify-java-foundation.mjs`、约 30 个 PDManer 生成器与测试、Gateway 边界与 JaCoCo 脚本都按当前目录名推导路径。
9. **Enforcer 禁止规则与 Surefire 设置必须随模块迁移。** 各根的 `bannedDependencies` 必须重新挂到新聚合 POM；改名后的 artifactId 会悄悄绕过 Gateway 的模式禁止（目前已部分无效）；`failIfNoSpecifiedTests` 在各根为 `false`、在 `platform-support-test` 为 `true`，也需逐一保留。
10. **文档链接与 PDManer 规范路径失效。** 64 个文件中的 217 个相对链接会断；仓库规则 `<project-root>/docs/database/<service-short-name>.pdma.json` 固定了每个规范模型的位置，`pdmaner-canonical-paths.test.mjs` 会检查。

其他较小事项：

- 三个未放在 `auto/` 的测试类（第 7 节）在任何布局下都不运行，属于既有问题。
- `platform-support-web` 与 `platform-support-integration` 同包 `com.paytech.platform.support.integration`。
- BFF 硬编码的 `1.0.0-SNAPSHOT` 应改由 BOM 管理。
- refactor 副本中硬编码的旧工作区绝对路径（第 6 节）。
