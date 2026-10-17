# 任务卡 01 · Foundation 搬入、`paytech-messaging`、内部签名 Token

- 分支：`refactor/step-01-foundation`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-step01`；起点：`refactor-main@36c0506`（第 00 步已合入）。
- 对应计划：[11-dev-plan.md](../design/11-dev-plan.md) 第 01 步；决定 D24、D26、D34；设计 [08-messaging-module.md](../design/08-messaging-module.md)。

## 1. 输入文档（先完整读取）

- [design/01-skeleton-and-conventions.md](../design/01-skeleton-and-conventions.md) 全文；[design/02-archunit-rules.md](../design/02-archunit-rules.md) R8、R12；[design/08-messaging-module.md](../design/08-messaging-module.md) 全文。
- [design/09-path-coupling-fixes.md](../design/09-path-coupling-fixes.md) 第 1.1（Foundation 行）、1.3（Foundation 行）、2.5、2.6、3.1、3.11、4.1 全部；[design/10-test-baseline-and-cleanup.md](../design/10-test-baseline-and-cleanup.md) 第 4.4 节。
- [analysis/retained-systems.md](../analysis/retained-systems.md) 第 1–4 节（Foundation 模块现状、compensation-event、内部调用方身份）。
- `agent-docs/COMPENSATION_EVENT_GUIDE.md` 第 4 节（compensation-event 不得内置 RabbitMQ；本步遵守，不改该库）；`agent-docs/decisions/2026-10-17-target-architecture-v2.md` D24、D26、D34。
- 现有代码：`paytech-foundation/` 13 个模块（重点 `paytech-compensation-event` 的 Dispatcher 与 `event_key`、`paytech-http-client-support` 的 `HttpServiceExchangeInterceptor`、`paytech-mvc-web-starter` 的过滤器与 Problem 错误写出、`paytech-redis-starter` 的两个测试）；`payment-orchestration-service` 中 `PaymentResultRabbitPublisher`（现有手工信封，作为映射参考，不改它）；`api-gateway/api-gateway-web/.../security/ReactiveJwksProvider.java` 与 `configuration/GatewayJwksConfiguration.java`（Platform JWKS 读取方式参考）；`risk-compliance-service/risk-web/.../service/request/RiskOperationRequestContextParser(Impl).java`。
- 第 00 步产物：`paytech/pom.xml`、`paytech/build/*`、`paytech/tools/arch-rules`、`scripts/verify-java-foundation.mjs` 本步前的状态。
- 仓库规范：`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/TECHNOLOGY_STACK.md`。

## 2. 搬迁清单（`git mv`）

| 源 | 目标 |
|---|---|
| `paytech-foundation/pom.xml` | `paytech/foundation/pom.xml`（`<parent>` 改 `<relativePath>../build/paytech-parent/pom.xml</relativePath>`；`<modules>` 13 个 + 新增） |
| `paytech-foundation/<13 个模块>/` | `paytech/foundation/<同名>/`（目录名不变；artifactId 不变） |
| `paytech-foundation/README.md` | `paytech/foundation/README.md`（路径与命令改写） |

删除：`paytech-foundation/mvnw`、`mvnw.cmd`、`.mvn/`；搬完后 `paytech-foundation/` 消失。`paytech/pom.xml` 取消 `foundation` 注释。模块内源码**不改包名**（01 §8 允许保留一个版本）。

## 3. 新建与改动清单

### 3.1 `paytech-messaging-api`（`paytech/foundation/paytech-messaging-api`，core 可依赖）

`com.paytech.foundation.messaging.api`：`DomainEvent`（08 §2 信封全部字段，不可变，Builder；`payload` 为 `JsonNode`；构造时计算 `payloadHash = sha256:<hex>`，使用现有 `paytech-json-canonicalization` 得到规范字节）、`DomainEventPublisher` 端口接口（`publish(DomainEvent)`，同步，失败抛 `DomainEventPublishException`）、`ConsumedEventStore` 接口（`recordIfAbsent`、`lastVersion`、`advance`）、`AggregateKey`（`aggregateType + aggregateId` 与 `eventKey` 的生成 / 解析：`eventKey = <aggregateType>:<aggregateId>:<eventType>:<aggregateVersion>`，供补偿事件 `event_key` 使用）、`EventCatalogEntry` 模型与 `EventCatalog` 读取接口。依赖只允许 `paytech-common-core`、`paytech-json-canonicalization`、Jackson。

### 3.2 `paytech-messaging`（`paytech/foundation/paytech-messaging`，只有 app 依赖）

`com.paytech.foundation.messaging`：

- 发布：`RabbitDomainEventPublisher`（`DomainEventPublisher` 实现）：交换机 `<sourceService>.events`，路由键 `aggregateId`，AMQP 属性按 08 §2（`messageId=eventId`、`type`、`correlationId`、Persistent、`x-aggregate-id` 头），Publisher Confirm + mandatory，Returned 或 Nack 抛 `DomainEventPublishException`；信封序列化后 > 64 KiB 拒绝。
- 拓扑声明器 `MessagingTopologyDeclarer`：按配置 `messaging.producer.service`、`messaging.consumers.<producer>.partitions`（默认 4）声明 `x-consistent-hash` 交换机、`<consumer>.<producer>.p<i>` Quorum 分区队列（`x-single-active-consumer=true`、`delivery-limit=5`、DLX / DLQ）并以相同权重绑定；缺插件时启动失败并提示启用 `rabbitmq_consistent_hash_exchange`。
- 消费：`PartitionedListenerContainerFactory`（每个分区队列一个容器、并发 1）、`IdempotentConsumeInterceptor` 实现 08 §4 的 1–4 步（`strictOrder` 由 Handler 注解 `@DomainEventHandler(eventType, strictOrder)` 声明），`t_consumed_event` 与 `t_consumed_aggregate_version` 的 MyBatis Mapper（XML，表 DDL 放 `src/main/resources/schema/messaging-consumed-event.sql` 供各服务 `init/ddl.sql` 逐字复制；本模块自身测试 DDL 在 `src/test/resources`），`JdbcConsumedEventStore`。业务 Handler 在事务内被调用，两张表与业务写同一事务（依赖 app 的事务管理器）。
- `AggregateSerialExecutor`：按 `aggregateId` 哈希到固定线程的串行执行器（08 §3），供 compensation-event Dispatcher 在第 08 步接入；本步只提供类与测试，不改 compensation-event。
- 指标：Micrometer `messaging.published`、`messaging.consumed{outcome=handled|replayed|stale|gap|dead}`。
- 事件目录：`catalog/events.yaml` 按 08 §5 登记六条（`PaymentSucceeded` v2、`ClientReviewDecided`、`EconomicFactRecorded`、`BalanceMoved`、`SettlementRestrictionChanged`、`PaymentSucceeded` 的 card 消费作为 consumers 项），`YamlEventCatalog` 读取并校验（eventType 唯一、producer / consumers 为 01 §3 服务名、strictOrder 布尔）。
- 自动配置 `MessagingAutoConfiguration`（`spring.factories` / `AutoConfiguration.imports`），前缀 `messaging.*`。

### 3.3 内部签名 Token（D26，Foundation 部分）

新增模块 `paytech/foundation/paytech-internal-auth`（`com.paytech.foundation.internalauth`），依赖 `com.nimbusds:nimbus-jose-jwt`（Spring Boot BOM 管理版本）、`paytech-common-core`、spring-web（optional）、spring-webflux（optional）：

- `InternalTokenClaims`：`iss`（固定 `platform-support-service`）、`sub` = 调用方 `spring.application.name`、`aud` = 目标服务名、`inst` = 实例 ID、`purpose`（大写 Code，如 `CHANNEL_CARD_MATERIAL_REVEAL`、`GENERAL`）、`jti`、`iat`、`exp`（≤ 300 s）。签名 ES256，JWS compact。
- `InternalTokenVerifier`（`interface + Impl`）：公钥来源 `InternalTokenKeySource` 接口，默认实现 `JwksInternalTokenKeySource`（配置 `internal-auth.jwks-uri`，缓存 + 过期刷新 + `kid` 未命中强制刷新一次），校验签名、`iss`、`aud == 本服务名`、`inst == paytech.instance-id`、`exp`、`nbf/iat` 偏差 ≤ 60 s；返回 `CallerIdentity(caller, purpose, jti)`。
- `InternalTokenSupplier` 端口（`token(targetService, purpose)`），实现 `PlatformInternalTokenSupplier`：调 Platform `POST /platform/v1/internal-tokens`（请求 `{targetService, purpose}`，响应 `{token, expiresAt}`；Platform 端点在第 02a 步实现，本步只定义 `-api` 风格的合同类 `InternalTokenIssueRequest/Response` 放在本模块 `contract` 包，后续 Platform `-api` 引用），按 `(target, purpose)` 缓存到过期前 60 s；获取失败抛 `InternalTokenUnavailableException`，调用方按失败关闭处理。
- `StaticKeyInternalTokenSupport`：仅 `mock` / 测试用——本地 ES256 密钥对（配置 `internal-auth.static-key.private-jwk` / `public-jwk`）实现 `InternalTokenSupplier` 与 `InternalTokenKeySource`；自动配置在 `internal-auth.mode != OFF` 且 `internal-auth.static-key.*` 存在时启用，并在启动日志 WARN 标注"仅限本地"。生产 Profile 不得配置它（Properties 校验：`spring.profiles.active` 含 `prod` 时拒绝）。
- 出站：`paytech-http-client-support` 新增 `InternalTokenExchangeInterceptor`，对经 `HttpServiceClientFactory` 创建的客户端按目标服务名注入 `Authorization: Bearer <token>`；目标服务名来自现有服务发现名解析；`purpose` 默认 `GENERAL`，可按接口方法以注解 `@InternalPurpose("...")` 覆盖。
- 入站：`paytech-mvc-web-starter` 新增 `InternalAuthFilter`，配置 `internal-auth.mode = OFF | DUAL | REQUIRED`（默认 `OFF`，保证本步后旧目录与新目录行为不变）：`REQUIRED` 无 / 坏 Token → 401 RFC 9457 Problem（用现有错误写出器，`type` 用现有内部错误 Profile 的新增项 `internal-token-invalid`）；`DUAL` 接受合法 Token，否则回退读取 `X-Caller-Service`（记 WARN 一次 / 调用方）；通过后把 `CallerIdentity` 放入请求属性与 `CallerIdentityHolder`（供 Controller 用 `@CallerIdentityArgument` 解析）；`internal-auth.exempt-path-prefixes` 默认含 actuator 与 `/public/`。
- WebFlux：同模块 `webflux` 包提供 `InternalTokenExchangeFilterFunction`（WebClient 出站）与 `InternalAuthWebFilter`（入站，同三种模式），供第 02b 步 Gateway 使用；本步只实现与测试，不接 Gateway。
- Vault Java Client（`paytech-vault-client`，在 `vault-service/` 下，本步不搬）**不动**；Go 服务端在第 02d 步。

### 3.4 `OperationRequestContextParser` 下沉

把 `RiskOperationRequestContextParser(Impl)` 以通用名 `OperationRequestContextParser(Impl)`、`OperationRequestContext` 放入 `paytech-mvc-web-starter`（包 `com.paytech.foundation.web.request`），去掉 Risk 命名与 Risk 特定错误码（错误码改为 Foundation 的参数校验 Problem），行为与现有测试等价；**不删 Risk 的副本**（第 06 步切换）。

### 3.5 路径与脚本

- 09 §2.6：`RedisLocalEnvironmentPrecondition` 提示文案改为相对 `paytech/foundation` 的路径；09 §2.5 不改代码（确认 13 个模块仍直接位于 `paytech/foundation/` 下）。
- 09 §3.1：`test-environment/scripts/build.sh` 第 54–55 行替换为单 Reactor 命令：先 `(cd "$workspace_dir/paytech" && ./mvnw -DskipTests -B install -pl build/paytech-dependencies,build/paytech-parent)`，再 `(cd "$workspace_dir/paytech" && ./mvnw -DskipTests -B -f foundation/pom.xml install)`；其余行不动（第 02 步处理）。
- 09 §3.11：`test-environment/scripts/prepare.py` 第 39 行文案。
- 09 §4.1：`scripts/verify-java-foundation.mjs` 全部四处按目标值修改，并把 `paytech-messaging-api`、`paytech-messaging`、`paytech-internal-auth` 加入 `requiredFiles` 与 `expectedArtifactIds`；运行通过。
- `local-environment/compose.yaml` 的 rabbitmq：挂载 `./rabbitmq/enabled_plugins`（内容启用 `rabbitmq_management`、`rabbitmq_consistent_hash_exchange`）；`scripts/verify-local-environment.mjs` 若校验卷 / 挂载则同步；`test-environment` 的 RabbitMQ 同样处理（只改插件文件与挂载，不改其他）。
- `paytech/tools/arch-rules`：R8（`-core` 只依赖 `paytech-messaging-api`）与 R12（Handler eventType 必须在目录中）此时有了真实目标，确认规则实现对新模块可执行；`arch-rules-reactor-test` 对 Foundation 模块的依赖图运行并通过。

## 4. 测试要求

- 现有 Foundation 测试全部迁移后通过；基线数以搬迁前 `cd paytech-foundation && ./mvnw -B verify` 的 Surefire 汇总为准（先在搬迁前跑一次并记录）。
- `paytech-messaging`：按 08 §7 用本地 RabbitMQ（`local-environment` base Profile，凭据从 `.env.local` 经 `paytech.local-environment.dir` 读取；不可用时失败并给出启动说明）：同键有序、异键并行、重放跳过、版本回退跳过、缺口等待后 DLQ、Publisher Confirm 失败抛错、64 KiB 上限、目录校验。`t_consumed_event` 两表在 `paytech_foundation_test` 库中由测试 DDL 创建并在每个测试后清理。
- `paytech-internal-auth`：签发 / 校验往返、`aud` 不匹配、过期、`inst` 不匹配、`kid` 轮换刷新、JWKS 不可用失败关闭、`OFF / DUAL / REQUIRED` 三种模式的入站行为（MockMvc）、WebFlux 入站与出站（WebTestClient）、`StaticKey` 在 `prod` Profile 被拒绝。JWKS 用测试内嵌 HTTP 端点（现有 MockHttpServer 模式），不 Mock 方法。
- `OperationRequestContextParser`：复制 Risk 现有解析测试用例到 Foundation。
- 全部测试在 `auto/` 目录；禁止 `when(...).thenReturn`、`@MockBean`。

## 5. 验证清单（写入报告）

1. 搬迁前：`cd paytech-foundation && JAVA_HOME=… ./mvnw -B verify` 的测试数。
2. 搬迁后：`cd paytech && ./mvnw -B verify` 全绿，测试数 ≥ 基线 + 新增。
3. `node scripts/verify-java-foundation.mjs`、`node scripts/verify-local-environment.mjs` 通过。
4. `cd paytech && ./mvnw -B install -f foundation/pom.xml -DskipTests` 成功后，任选一个旧服务（如 `commerce-service`）`./mvnw -o -q -DskipTests validate` 仍成功（旧目录继续从 `~/.m3` 引导）。
5. `git log --follow` 抽查 `paytech/foundation/paytech-compensation-event/pom.xml` 与一个 Java 文件可追溯。
6. `paytech-foundation/` 不存在；`git status` 干净。

## 6. 禁止事项

- 不改 `paytech-compensation-event` 的任何生产代码（Dispatcher 接入 `AggregateSerialExecutor` 在第 08 步）；不改 `COMPENSATION_EVENT_GUIDE.md`。
- 不动 12 个服务目录（含 `vault-service/`），除第 3.5 节列出的 `test-environment` 两处与 `local-environment` RabbitMQ 插件。
- `internal-auth.mode` 默认必须为 `OFF`；不在任何现有 `application.yml` 中开启。
- 不改 Foundation 现有模块的包名与公开 API（新增除外）。
- 不执行业务 SQL；不调用 Superpowers skill；不提交到 `refactor-main` / `main`；不推送。

## 7. 交付物

1. 短分支提交，前缀 `refactor(step-01): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
2. 报告 `tasks/01-foundation-report.md`（随分支提交）：改动摘要 ≤ 30 行、第 5 节逐项命令与结果、新模块的公开 API 清单、与 08 文档 / D26 的偏差、待确认项（如 `paytech-internal-auth` 作为独立模块、Token 有效期、`DUAL` 模式保留期限）。
3. 最终回复两行：报告路径 + 一句话结论。
