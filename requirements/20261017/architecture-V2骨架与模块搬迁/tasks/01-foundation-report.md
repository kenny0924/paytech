# 第 01 步报告 · Foundation 搬入、`paytech-messaging`、内部签名 Token

- 分支：`refactor/step-01-foundation`（起点 `refactor-main@5f1fc10`）。实现提交为 `8023ea3`（搬迁与主代码）与 `f7f8d20`（测试、R12 扩展、缺口尾部重投、README）；报告单独提交。
- 门禁反馈修正（追加提交）：
  - `payloadHash` 的规范化计算移出 `paytech-messaging-api`，改在 `paytech-messaging` 完成：发布时计算，消费时校验。
  - 消费记录抽象改名为 `ConsumedEventRepository`，实现为 `MyBatisConsumedEventRepository`。
- 结论：第 5 节 6 项全部执行。1、2、4、5、6 通过；3 中 `verify-java-foundation.mjs` 通过，`verify-local-environment.mjs` 仍失败，失败类别与改动前基线相同，属既有问题。
- RabbitMQ：team-lead 批准后，已在共享 `mor-local-rabbitmq-1` 上在线启用 `rabbitmq_consistent_hash_exchange`，容器未重启（见第 2 节第 2 项）。之后不带任何端口覆盖的 `./mvnw -B clean verify` 在共享 Broker 上全部通过。

## 1. 改动摘要

1. `git mv` `paytech-foundation/` 的 13 个模块、聚合 POM、README、`.run/` 到 `paytech/foundation/`；删除 `mvnw`、`mvnw.cmd`、`.mvn/`、`.gitignore`（与 `paytech/.gitignore` 逐字相同）。`paytech-compensation-event` 67 个文件全部为 R100 纯改名。
2. `paytech/foundation/pom.xml` 的 parent 改为 `../build/paytech-parent/pom.xml`，`<modules>` 追加三个新模块。`paytech/pom.xml` 只取消了 `foundation` 一行的注释。
3. BOM 登记 `paytech-messaging-api`、`paytech-messaging`、`paytech-internal-auth`，以及 `nimbus-jose-jwt` 10.4。
4. 新模块 `paytech-messaging-api`、`paytech-messaging`、`paytech-internal-auth`，公开 API 见第 3 节。
   - `paytech-messaging-api` 的生产依赖只有 `paytech-common-core` 与 `jackson-databind`，不传递 `paytech-json-canonicalization`；`dependency:tree` 已核对。
   - 规范化与 SHA-256 由 `paytech-messaging` 的 `PayloadHasher` 完成：`DomainEventCodec` 编码时计算并写入信封，解码时重新计算并比对。
   - `verify-java-foundation.mjs` 已把 `paytech-messaging-api` 列入"不得声明 json-canonicalization"的高扇出模块清单。
5. MVC Starter 新增三项：
   - `internalauth` 包：`InternalAuthFilter`、`CallerIdentityArgumentResolver`、`InternalAuthWebAutoConfiguration`。
   - `request` 包：`OperationRequestContextParser(Impl)` 与 `OperationRequestContext`。
   - `WebAutoConfiguration` 注册该解析器。
6. HTTP Client Support 新增 `InternalTokenExchangeInterceptor`、`InternalPurposeRequestValuesProcessor` 与 `HttpServiceClientOptions.withInternalToken(...)`。两个模块都只以 optional 依赖引用 internal-auth。
7. `CommonErrorCode` 新增 `INTERNAL_TOKEN_INVALID`；`MvcProblemExceptionMapper` 把它固定映射为 401，并只外显通用说明。
8. 09 §2.6：Redis 前置检查文案改为从 `paytech/foundation` 出发、使用 `../../local-environment`；其测试移入 `auto/infrastructure`，从而第一次被执行。09 §2.5 核对后不改代码：13 个模块仍直接位于 `paytech/foundation/` 下。
9. 09 §3.1、§3.11：`build.sh` 第 54–55 行换成任务卡给出的两条单 Reactor 命令；`prepare.py` 文案已改。为不让断言失效，同步改了 `test_deploy_script.py` 与 `test_risk_rule_configuration_authorization_inventory.py` 对这两行的断言（见第 4 节第 7 项）。
10. 09 §4.1：`verify-java-foundation.mjs` 的四处路径与清单已改，三个新模块加入 `requiredFiles`、`modulePoms`、BOM 治理与 `expectedArtifactIds`。同时修正 `paytech-http-problem` 依赖白名单（第 4 节第 8 项）。
11. RabbitMQ 插件：`local-environment` 与 `test-environment` 各新增 `rabbitmq/enabled_plugins`，并以只读方式挂载到 `/etc/rabbitmq/enabled_plugins`。`verify-local-environment.mjs` 增加对该挂载与插件内容的检查。
12. arch-rules 三处改动：
    - `ModuleIdentity` 把 `paytech-internal-auth` 归为 FOUNDATION。
    - R12 增加 `@DomainEventHandler` 检查：Handler 只能在 `-core`，eventType 必须登记在 `foundation/paytech-messaging/catalog/events.yaml`，且 consumers 包含本服务。
    - Reactor 测试改为按嵌套模块计数，覆盖全部 17 个 Foundation 坐标，并用真实坐标验证 R8。
13. Foundation README：路径、命令与相对链接按新位置改写，新增 Messaging 与 Internal Auth 章节。所有 `](../` 链接经脚本核对均能解析。

## 2. 第 5 节验证（实际命令与结果）

所有 Maven 命令前均执行 `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`。worktree 中没有未跟踪的 `local-environment/.env.local`，因此测试锚点经 `-Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment` 指向主工作区；Redis 密码只导出了 `PAYTECH_REDIS_PASSWORD` 一个键，没有打印。

1. **搬迁前基线**：在 `paytech-foundation/` 下执行 `./mvnw -B verify`。
   - 第一次运行时环境中没有 `PAYTECH_REDIS_PASSWORD`，Redis Starter 的 2 个集成测试按设计失败，BUILD FAILURE。
   - 导出该键后第二次运行：BUILD SUCCESS，**722 个测试，0 失败**，各模块数见下表。

2. **搬迁后**：在 `paytech/` 下执行 `./mvnw -B clean verify -Dpaytech.local-environment.dir=… -Dpaytech.test.rabbitmq.port=25682`。结果为 首轮（修正前）BUILD SUCCESS，957 个测试，0 失败。

   门禁修正后在共享 Broker（默认 5672，不带端口覆盖）上复测：

   ```bash
   cd paytech && ./mvnw -B clean verify -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment
   ```

   结果：BUILD SUCCESS，退出码 0，**960 个测试，0 失败 / 0 错误**，用时 56 s；运行后共享 vhost 中以 `mt` 开头的交换机与队列均为 0。下表为这次复测的数字。

   | 模块 | 基线 | 搬迁后 | 差异说明 |
   |---|---|---|---|
   | common-core | 130 | 130 | |
   | http-problem | 9 | 9 | |
   | json-canonicalization | 103 | 103 | |
   | http-client-support | 111 | 117 | +5 出站 Token；+1 为现有 `@EnumSource(CommonErrorCode.class)` 覆盖到新错误码 |
   | test-support | 53 | 53 | |
   | mybatis-support | 6 | 6 | |
   | command-idempotency | 11 | 11 | |
   | snowflake-id | 5 | 5 | |
   | mvc-web-starter | 125 | 160 | +6 入站三模式，+29 解析器 |
   | caffeine-starter | 11 | 11 | |
   | redis-starter | 121 | 122 | +1 为移入 `auto/` 后首次执行的前置检查测试 |
   | xxl-job-starter | 6 | 6 | |
   | compensation-event | 31 | 31 | |
   | internal-auth（新） | — | 38 | |
   | messaging-api（新） | — | 37 | 哈希向量与 Profile 用例已移到 messaging |
   | messaging（新） | — | 54 | 其中 Broker + MySQL 场景 16 例；含 `PayloadHasher` 向量与 Profile 边界、编码时预置哈希不符 |
   | Foundation 合计 | 722 | 893 | 基线 722 加新增 171，满足"不少于基线加新增" |
   | tools（arch-rules / instance-manifest / reactor-test） | 63 | 67 | +3 R12 领域事件，+1 Reactor R8 |

   **RabbitMQ 的执行过程：**
   - 起初共享 Broker 缺少一致性哈希插件。我没有 up/down/restart 共享容器，先向 team-lead 申请在线启用。等待批复期间，用临时私有容器 `step01-rabbitmq-verify`（`rabbitmq:4.2.9-management`，`127.0.0.1:25682`，挂载本步的 `enabled_plugins`，账号同 `.env.local`）加 `-Dpaytech.test.rabbitmq.port=25682` 跑完了一轮，同样 957/0。该容器及其匿名卷已删除。
   - 对照：插件启用前连接共享 Broker，应用启动失败并给出提示，即任务卡要求的"缺插件时启动失败并提示"：

     ```text
     声明一致性哈希交换机失败: …hash；请确认 RabbitMQ 已启用 rabbitmq_consistent_hash_exchange 插件
     ```

     其底层原因是 `PRECONDITION_FAILED - unknown exchange type 'x-consistent-hash'`。
   - team-lead 批准后在线启用，容器状态保持 `Up 3 weeks (healthy)`，未重启：

     ```bash
     docker exec mor-local-rabbitmq-1 rabbitmq-plugins enable rabbitmq_consistent_hash_exchange
     ```

     启用前 `rabbitmq-plugins list -e`：

     ```text
     [E*] rabbitmq_management       4.2.9
     [e*] rabbitmq_management_agent 4.2.9
     [E*] rabbitmq_prometheus       4.2.9
     [e*] rabbitmq_web_dispatch     4.2.9
     ```

     启用后：

     ```text
     [E*] rabbitmq_consistent_hash_exchange 4.2.9
     [E*] rabbitmq_management               4.2.9
     [e*] rabbitmq_management_agent         4.2.9
     [E*] rabbitmq_prometheus               4.2.9
     [e*] rabbitmq_web_dispatch             4.2.9
     ```

   - 启用后（门禁修正前）不带端口覆盖复测：957 个测试、0 失败，messaging 50/50。门禁修正后的复测：960 个测试、0 失败，messaging 54/54。
   - 持久化：在线启用写入的是容器内的 `/etc/rabbitmq/enabled_plugins`，容器重建后会丢失。本步的 `local-environment/rabbitmq/enabled_plugins` 以只读方式挂载到同一路径，内容为 `[rabbitmq_management,rabbitmq_prometheus,rabbitmq_consistent_hash_exchange].`，与当前已启用集合一致，所以下次按 compose 重建时插件仍然生效。test-environment 同样处理。
   - MySQL：messaging 只在 `paytech_foundation_test` 中重建并清理自己的 `t_consumed_event`、`t_consumed_aggregate_version`、`t_test_handled_event`。

3. **Node 脚本**：
   - `node scripts/verify-java-foundation.mjs --self-test` 与 `node scripts/verify-java-foundation.mjs` 均退出码 0，输出 "Java foundation structure and effective Maven model verified; JCS 1.1, sixteen modules, …"。改动前它仅因 http-problem 白名单而失败。
   - `node scripts/verify-local-environment.mjs` 退出码 1。改动前在干净基线上运行同样退出码 1，失败类别相同：
     - `.env.example` 缺少 MinIO 变量，导致 compose 插值失败。具体报出哪个变量随键顺序变化。
     - `environment example declaration inventory drifted`。
     - 4 条 Gateway 合同文档引用缺失。
   - 由于 compose 模型在插值阶段就失败，新增的 RabbitMQ 挂载检查在该脚本里走不到。我另用 `docker compose -f <compose> config --no-interpolate` 核对了两个 compose 的 rabbitmq 卷：均为只读 bind，从 `./rabbitmq/enabled_plugins` 挂到 `/etc/rabbitmq/enabled_plugins`。
   - 附带回归：`python3 -m unittest test_deploy_script` 31/31 通过。`test_risk_rule_configuration_authorization_inventory` 改动前后均为相同的 3 个失败，属既有问题：compose 计数、Dockerfile 计数、pinned manifest 中的 README。

4. **旧服务引导**：
   - 在 `paytech/` 下执行 `./mvnw -B install -f foundation/pom.xml -DskipTests`：BUILD SUCCESS。
   - 在 `commerce-service/` 下执行 `./mvnw -o -q -DskipTests validate`：退出码 0。另执行 `./mvnw -o -q -DskipTests compile`，退出码也是 0。
   - 补充运行期证据：用 commerce-web 的真实 runtime classpath 运行一个单文件检查。该 classpath 不含 internal-auth 与 nimbus。检查中创建了 `HttpServiceClientFactory` 客户端并加载 `WebAutoConfiguration`，输出 `internal-auth absent / client created: true / mvc starter loaded`。由此可见 optional 依赖不会影响未引入 internal-auth 的服务。

5. **历史可追溯**：
   - `git log --follow --oneline paytech/foundation/paytech-compensation-event/pom.xml` 输出 `8023ea3`、`fff35d2`。
   - 对 `…/core/execution/CompensationEventDispatcher.java` 执行同样命令，也是这 2 条，可追溯到搬迁前。

6. **目录与工作区**：`ls paytech-foundation` 返回 No such file or directory；提交后 `git status --short` 为空。

## 3. 新模块公开 API

**`paytech-messaging-api`**（`com.paytech.foundation.messaging.api`；依赖仅 common-core、jackson-databind）：

- `DomainEvent` 与 `DomainEvent.Builder`：
  - 字段为 08 §2 的全部字段，`payload()` 返回副本。
  - `payloadHash` 可空：`-core` 构造时一般不带，`Builder.payloadHash(...)` 只校验 `sha256:<64 hex>` 格式。
  - `eventKey()`；未指定 `eventId` 时默认取 eventKey。
- `DomainEventPublisher.publish(DomainEvent)`；失败时抛 `DomainEventPublishException`，它是 `ErrorException` 的子类。
- `ConsumedEventRepository`（数据访问抽象，按开发约定以 Repository 命名）：`recordIfAbsent`、`lastVersion(AggregateKey)`、`advance(AggregateKey, long)`。
- `AggregateKey`：
  - 构造与生成：`of`、`eventKey(eventType, version)`。
  - 解析：`fromEventKey`、`parseEventKey`，后者返回 `ParsedEventKey`。
  - 格式常量：`AGGREGATE_TYPE`、`AGGREGATE_ID`、`EVENT_TYPE`。
- `DomainEventConsumer.consume(DomainEvent)` 与注解 `@DomainEventHandler(eventType, strictOrder)`。它们放在 api 而非实现模块，因为 Handler 位于 `-core`，而 R8 规定 `-core` 只能依赖 api。
- `EventCatalog`（`find`、`entries`）与 `EventCatalogEntry`。

**`paytech-messaging`**（`com.paytech.foundation.messaging`，只由 `-app` 依赖）：

- 发布、拓扑与消费：
  - 发布：`publish.RabbitDomainEventPublisher`。
  - 拓扑：`core.topology.MessagingTopology`（命名与常量）、`MessagingTopologyDeclarer`。
  - 消费：`consume.PartitionedListenerContainerFactory`、`PartitionedListenerContainers`、`IdempotentConsumeInterceptor`、`DomainEventHandlerRegistry`、`DomainEventMessageListener`。
- 编解码、存储与工具：
  - 编解码：`core.codec.DomainEventCodec`、`PayloadHasher`（RFC 8785 + SHA-256，本模块是 `paytech-json-canonicalization` 的真实消费者）、`InvalidDomainEventException`。
  - 持久化：`persistence.mapper.ConsumedEventMapper`（XML 在 `META-INF/mybatis/messaging/`）、`persistence.repository.MyBatisConsumedEventRepository`。
  - 串行执行：`ordering.AggregateSerialExecutor`。
  - 目录：`catalog.YamlEventCatalog`。
  - 指标：`core.observability.MessagingMetrics`（`messaging.published`、`messaging.consumed{outcome}`）、`ConsumeOutcome`。
- 自动配置 `MessagingAutoConfiguration`：
  - 配置键：`messaging.enabled`、`messaging.producer.service`、`messaging.producer.confirm-timeout`、`messaging.consumer.service`、`messaging.consumer.retry-backoff`、`messaging.consumers.<producer>.partitions`。
  - 附带资源：DDL 在 `src/main/resources/schema/messaging-consumed-event.sql`，目录在 `catalog/events.yaml`（含 5 条登记）。

**`paytech-internal-auth`**（`com.paytech.foundation.internalauth`）：

- `api`：
  - 声明与身份：`InternalTokenClaims`、`CallerIdentity`。
  - 端口：`InternalTokenVerifier`、`InternalTokenSupplier`、`InternalTokenKeySource`。
  - 异常：`InternalTokenInvalidException`（401）、`InternalTokenUnavailableException`（503）。
  - 模式与上下文：`InternalAuthMode`、`CallerIdentityHolder`。
  - 注解：`@CallerIdentityArgument`、`@InternalPurpose`。
- `contract`：`InternalTokenIssueApi`（`POST /platform/v1/internal-tokens`）、`InternalTokenIssueRequest`、`InternalTokenIssueResponse`。
- `core`：
  - 签发与校验：`token.InternalTokenSigner`、`token.InternalTokenVerifierImpl`。
  - 公钥与 Token 来源：`key.JwksInternalTokenKeySource`、`supplier.PlatformInternalTokenSupplier`、`supplier.StaticKeyInternalTokenSupport`。
  - 入站判定：`inbound.InternalCallerAuthenticator`，Servlet 与 WebFlux 共用。
  - 自动配置：`autoconfigure.InternalAuthAutoConfiguration` 与 `InternalAuthProperties`，对应配置键 `internal-auth.*`、`paytech.instance-id`、`spring.application.name`。
- `webflux`：`InternalAuthWebFilter`、`InternalTokenExchangeFilterFunction`。

## 4. 与任务卡、08 文档及 D26 的偏差

1. **一致性哈希交换机的位置（08 §3）**：
   - 一致性哈希交换机只把一条消息路由到一个绑定队列。若所有订阅服务都把分区队列绑在发布方的 `x-consistent-hash` 交换机上，`EconomicFactRecorded` 只会被 ledger 与 account 中的一个收到。
   - 实现改为：发布方 `<producer>.events` 用 Durable Fanout；每个订阅服务声明自己的 `<consumer>.<producer>.hash`（`x-consistent-hash`），以交换机到交换机的绑定接到发布方，再把 `p0..pN-1` 以相同权重绑定到它。
   - 路由键仍为 `aggregateId`；队列名、Quorum、SAC、`delivery-limit=5`、DLX/DLQ 均按原设计。
2. **缺口等待（08 §4 第 3 步）**：
   - 实测表明，Quorum Queue 对 Nack 重新入队的消息会优先重投。缺失版本排在同一队列后面，原样重投只会让新版本连续失败 5 次后进入 DLQ，等不到缺口补齐；该结论来自本步新增的"缺口补齐"用例在旧实现下超时。
   - 实现改为：退避后把消息重新发布到本分区队列尾部，等待 Broker 确认后再 Ack 原消息。尝试次数记在 `x-paytech-gap-attempts`，第 5 次仍有缺口则拒绝进入 DLQ。
   - Handler 异常仍走 Nack 重新入队。"缺口补齐后处理"与"缺口等待后 DLQ"两个场景都已覆盖。
3. **`eventId`**：08 示例写的是 ULID。ULID 在补偿重试时每次都会变化，会让消费端无法去重，因此默认 `eventId = eventKey`（确定性，最长 126），同时允许显式指定。
4. **`@DomainEventHandler` 放在 api 模块**：任务卡把它列在 `paytech-messaging` 下。Handler 位于 `-core`，而 `-core` 只能依赖 api（R8），所以注解与 `DomainEventConsumer` 必须放在 api。
5. **Problem `type`**：任务卡要求 `type` 使用 `internal-token-invalid`。现行 Problem Profile 固定 `type=about:blank`，`ApiProblemProfile.validate` 也强制这一点，业务区分靠 `code`。因此改为在 Foundation 公共错误码中新增 `INTERNAL_TOKEN_INVALID`，`type` 保持 `about:blank`。JWKS 不可用时返回 503 `SERVICE_UNAVAILABLE`，而不是 401。
6. **依赖与版本**：
   - Spring Boot 4.0.8 的 BOM 并不管理 `nimbus-jose-jwt`，它只经 Spring Security 7.0.7 传递进来，版本 10.4。为此在 paytech BOM 中固定为 10.4。
   - internal-auth 额外依赖 `spring-boot-autoconfigure`（自动配置所需）与 `paytech-http-problem`（WebFlux 写出 Problem 所需），这两项不在任务卡列出的依赖中。
   - 任务卡原要求 messaging-api 依赖 `paytech-json-canonicalization`。门禁指出这违反 RFC 8785 决定：会让所有 `-core` 传递获得该 JAR。现已把哈希计算移到 `paytech-messaging`，messaging-api 不再依赖它。
7. **test-environment 改动范围**：任务卡只列 `build.sh` 与 `prepare.py` 两处。按 09 §3.2 "与 3.1 同一提交改断言"，另改了 `test_deploy_script.py` 与 `test_risk_rule_configuration_authorization_inventory.py` 中锁定旧命令的断言，否则 `build.sh` 一改它们就会失败。
8. **`verify-java-foundation.mjs` 的既有失败**：脚本要求 `paytech-http-problem` 的生产依赖只有 `jackson-annotations`，但该模块一直依赖 `paytech-common-core`（`ApiProblemProfile` 引用 `ApiHeaderNames`）。白名单已改为两者都允许，否则无法满足"运行通过"。
9. **RabbitMQ 插件文件**：任务卡要求启用 `rabbitmq_management` 与 `rabbitmq_consistent_hash_exchange`。镜像原本还默认启用 `rabbitmq_prometheus`，为不丢失现有能力，一并保留。
10. **命名**：
    - 按门禁反馈，任务卡中的 `ConsumedEventStore` / `JdbcConsumedEventStore` 已改为 `ConsumedEventRepository` / `MyBatisConsumedEventRepository`，与开发约定 §3 的 `*Repository` 一致。
    - 合同类 `InternalTokenIssueRequest/Response` 仍按任务卡命名，没有使用 `*RequestBody/*ResponseBody`。
11. **Risk 解析器测试**：Risk 侧没有直接测试 `RiskOperationRequestContextParser` 的用例，只在 HTTP 入口测试中间接覆盖。Foundation 按原实现逐条行为编写了 29 个解析用例，并删去了原实现中正则已排除、不可能命中的 `value <= 0` 分支。
12. **出站目标服务名**：负载均衡拦截器会先把 URI 改写为实例地址，所以不能从请求 URI 读取服务名。实际取法如下：
    - `configureGroup` 取 HTTP Service Group 名。
    - `create()` 必须显式传入目标服务名；`withInternalToken(supplier)` 配合 `create()` 使用会立即报错。
    - Purpose 经 `HttpRequestValues.Processor` 写入请求属性。

## 5. 待确认项

1. **test-environment 的 RabbitMQ**：mor-test 的 Broker 未在线启用插件，本步只改了插件文件与挂载。容器按新挂载重建后才会生效，需要在 test-environment 发布时由用户执行。
2. **`paytech-internal-auth` 作为独立模块**：MVC Starter 与 HTTP Client Support 只以 optional 方式引用它，未引入的服务不受影响。是否改为合入现有模块，需要确认。
3. **Token 有效期**：上限 300 s，Supplier 在过期前 60 s 刷新，`iat`/`nbf` 允许 60 s 偏差，`exp` 严格判定。JWKS 缓存 5 min，kid 未命中时的强制刷新至少间隔 10 s。这些取值需要确认。
4. **`DUAL` 模式保留期限及 Platform 签发接口的调用方认证**：Platform 如何认证"申请 Token 的调用方"本身，属于第 02a 步的设计。
5. **事件目录中的推断取值**：08 未给出各事件的 `aggregateType`、`aggregateVersionField`、`strictOrder`，以下为本步推断，需要业务 Owner 确认：
   - `PaymentSucceeded`：`PAYMENT_ATTEMPT` / `attemptVersion`，consumers 为 acquiring 与 card。
   - `ClientReviewDecided`：`CLIENT_REVIEW_CASE` / `caseVersion`，consumers 为 client-master。
   - `EconomicFactRecorded`：`ECONOMIC_FACT` / `factVersion`，strictOrder=false，依据 04 §5。
   - `BalanceMoved`：`ACCOUNT_BOOK` / `movementSequence`，strictOrder=true。
   - `SettlementRestrictionChanged`：`SETTLEMENT_CONTROL` / `controlVersion`，strictOrder=true。
   - 所有 `payloadSchemaRef` 目前只是占位路径。
6. **聚合首次出现与版本连续性**：
   - 聚合首次出现、尚无版本记录时，以该事件为基线直接处理。
   - `strictOrder=true` 的事件要求发布方按聚合使用连续的事件序号（例如 `journal_sequence`）。若直接用 Attempt 版本这类可能跳号的值，就会产生永久缺口。
7. **R12 的两个目录**：补偿 Handler 仍读服务目录下的 `compensation-event-catalog.yaml`（第 00 步口径），领域事件 Handler 读 `foundation/paytech-messaging/catalog/events.yaml`。是否合并为一个目录，需要确认。
8. **JavaDoc 口径**：Properties 访问器与私有辅助方法沿用 Foundation 现有写法，没有逐个添加 JavaDoc。

## 6. 其他说明

- **共享本地仓库**：多次 `install` 把搬迁后的 Foundation（含三个新模块）与新 BOM 安装进了共享的 `~/.m3`，其他会话的旧工程会解析到这些新快照。经核对，commerce 编译不受影响，且未引入 internal-auth 时运行期行为不变。
- **未改动的范围**：
  - `paytech-compensation-event` 生产代码、`COMPENSATION_EVENT_GUIDE.md`、12 个服务目录（含 `vault-service/`）、任何 `application.yml` 均未改动。
  - `internal-auth.mode` 默认 `OFF`，没有在任何地方开启。
  - 未执行业务 SQL；未调用 Superpowers skill；没有使用 `when(...).thenReturn`、`@MockBean` 等行为桩。
- **仓库其他文档中的旧链接**：`AGENTS.md`、`agent-docs/**`、`TECHNOLOGY_STACK.md` §6 仍有指向 `paytech-foundation/` 的链接与"十三个模块"描述。按 09 §6，文档链接统一重写落在第 13 步，本步未改。
