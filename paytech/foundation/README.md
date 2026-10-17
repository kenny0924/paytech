# PayTech 公共基础 JAR

`paytech/foundation` 是单一 `paytech/` Reactor 中的 Foundation 聚合，包含以下小型、无业务领域所有权的模块：

- `paytech-common-core`：`CodeEnum`/`CodeMessageEnum`、公共异常分类、稳定 HTTP Header 名称、可复用严格 JSON
  字段反序列化器、分页请求/响应、[Public Token](paytech-common-core/PUBLIC_TOKEN.md) 及 [Money](paytech-common-core/MONEY.md)；Money 提供不可变币种/金额、精确同币种运算和严格字符串 JSON；
- `paytech-http-problem`：框架无关的 Code-MoR Problem records，以及 Request ID、UTC 六位小数时间和
  Header/Body 一致性规则；
- `paytech-json-canonicalization`：严格、显式按需引入的 RFC 8785 JSON Object/Number Profile 规范化 JAR；
- `paytech-http-client-support`：同步 `@HttpExchange` 客户端公共异常转换，宿主显式创建，无自动配置；
- [`paytech-test-support`](paytech-test-support/README.md)：测试专用的真实 HTTP 调用器、独立 MVC Mock 服务、入口 Header 工厂、响应 Checker 与统一后置清理，仅以 `test` scope 消费；
- `paytech-mybatis-support`：UTC `Instant` 与稳定 `CodeEnum` Code 的显式 MyBatis TypeHandler；
- `paytech-command-idempotency`：服务本地同步命令的 Claim、Lease、CAS、恢复阶段与稳定响应冻结；
- `paytech-snowflake-id`：显式 Region/Worker 配置和时钟回拨保护；
- `paytech-mvc-web-starter`：Spring MVC 统一异常响应、Request ID/MDC 与请求完成结构化日志；
- `paytech-caffeine-starter`：有容量和过期边界的进程内本地缓存；
- `paytech-redis-starter`：默认的类型化 Redis Cache、Lettuce 普通访问与按需启用的 Redisson 分布式协调；
- `paytech-xxl-job-starter`：与具体业务能力无关的 XXL-Job Executor 自动配置；
- `paytech-compensation-event`：服务本地补偿任务登记、Claim/Lease、重试、人工恢复和 XXL-Job 兜底扫描适配；
- `paytech-messaging-api`：跨服务领域事件信封 `DomainEvent`、发布端口、消费存储端口、`AggregateKey` 与事件目录模型，`-core` 可依赖；
- `paytech-messaging`：RabbitMQ 发布（Publisher Confirm、mandatory）、分区有序消费、`t_consumed_event` 幂等与事件目录，只由 `-app` 依赖；
- `paytech-internal-auth`：Platform 签发的短期 ES256 内部调用 Token（D26）的声明、校验、JWKS 公钥来源、出站 Token 来源与 WebFlux 适配。

父 POM 以真实相对路径 `../build/paytech-parent/pom.xml` 解析；在 `paytech/` 下执行 `./mvnw -B verify` 即按 Reactor 顺序构建 BOM、父 POM 与 Foundation。只构建 Foundation 时执行 `./mvnw -B -f foundation/pom.xml install`（父 POM 需已由 `./mvnw -B install -pl build/paytech-dependencies,build/paytech-parent` 安装到本地仓库）。版本由 `com.paytech:paytech-dependencies` 统一管理。服务只声明实际使用的 JAR，不引入聚合式
`common.jar`。

当前 HTTP Problem/Client/MVC、JSON Canonicalization、Command Idempotency、Caffeine、Redis Starter 和对应 BOM 增量只安装在本机 Maven 缓存中，尚未发布到 Nexus；业务服务正式引入前应先按
“Build 后 Foundation”的顺序发布新的 Snapshot。

完整 Wire Contract 以[系统级 Problem Profile 设计](../../docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md)为准；本 README 只记录 Foundation 实现。当前 Header/JSON 绑定/MVC Problem 聚焦门禁为 29/29，HTTP Problem Profile 为 5/5。2026-09-06 DTO 清理后的 Problem 相关 Reactor 326/326、2026-09-07 Retry-After 目标 Reactor 232/232，以及注入仓库本地 Redis 凭据后的全 Foundation 505/505 是对应历史源码基线；不把已被后继简化删除的请求体异常实现继续描述成当前能力。制品仅安装到本机 Maven 仓库供下游源码验证，未发布 Nexus、未部署，Legacy Reader 清理仍受部署证据门禁。

所有枚举通过 `CodeEnum#getCode()` 提供发布后不可变的编码，不以 `name()` 作为数据库、接口、消息或日志值。
同时带默认消息的错误枚举实现 `CodeMessageEnum`。错误码采用可读语义，例如 `INVALID_REQUEST`、
`BUSINESS_CHECK_FAILED`、`INTERNAL_ERROR` 和 `ID_GENERATION_ERROR`，不使用纯数字流水号。

## JSON Canonicalization

`paytech-json-canonicalization` 是无 Spring 的普通 JAR，只向真实 canonical-bytes 消费者提供
`public static byte[] canonicalizeObject(byte[])`。输入必须是非空、严格 UTF-8、唯一 Root Object；Object 属性递归按
UTF-16 code unit 排序，Array 顺序与 String 内容原样保留，不执行 NFC、trim 或大小写改写。JSON Number 只接受
`0|[1-9][0-9]*` 且值不大于 `9007199254740991`；负数、小数、指数和非 Object Root 失败关闭。Money、Decimal、
64-bit ID 与时间仍由业务 Owner 编码为 String。

固定技术上限为：Document 1,048,576 bytes、100,000 个 JSON token、64 层 Object/Array nesting、String 262,144 个
UTF-16 code unit、Property Name 512 个 UTF-16 code unit、Number 16 个 ASCII digit。模块只拥有严格验证与 RFC 8785
规范 bytes；Projection、Unicode/业务归一化、Array 业务排序、Domain Tag/Hash Schema、SHA-256/HMAC、存储、冲突和
重放语义继续由各 Owner 独占。它不进入 Common Core、现有 Starter、Compensation Event、Client API 或其他非消费者的
传递依赖；消费者必须直接、按需声明：

```xml
<dependency>
    <groupId>com.paytech</groupId>
    <artifactId>paytech-json-canonicalization</artifactId>
</dependency>
```

实现依赖 `io.github.erdtman:java-json-canonicalization:1.1` 与 Jackson Core，并通过 Foundation API 隔离第三方类型。
2026-09-07 实际解析自本机 Maven 代理仓库标记 `maven-public`；上游 POM 声明 Apache License 2.0，实际 JAR SHA-256 为
`ed12a01f28d147898312963a1f704e90290b67a61f34fa3a761f41c134f4e691`。JCS 模块 103/103（核心 76、边界 20、合同 7）及
Foundation 全 Reactor 607/607 均通过；BOM、Foundation Parent 和该 JAR 仅安装到本机 Maven 仓库供 Risk 源码验证，
未发布 Nexus、未部署。完整合同与未来扩展门禁见
[Foundation RFC 8785 JSON Object Profile 设计](../../docs/superpowers/specs/2026-09-07-foundation-rfc-8785-json-canonicalization-design.md)。

## 公共异常类别

普通业务检查优先使用 `BizCheckFailException("安全的拒绝原因")`，无需为每条检查创建错误枚举或异常类。
只有前端或其他调用方确实需要按某个稳定 Code 执行不同处理时，才保留独立业务错误码。

| 异常 | 公共 Code | MVC 默认状态 | 消息边界 |
|---|---|---|---|
| `BizCheckFailException` | `BUSINESS_CHECK_FAILED` | 400 | 外显调用方可理解的安全拒绝原因 |
| `InvalidParameterException` | `INVALID_REQUEST` | 400 | 外显安全的参数说明 |
| `ConcurrentRequestException` | `CONCURRENT_REQUEST` | 409 | 外显安全的并发冲突说明 |
| `ErrorException` | `INTERNAL_ERROR` | 500 | 只返回默认消息，隐藏实例消息与 Cause |
| `ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE)` | `UPSTREAM_INVALID_RESPONSE` | 502 | 依赖协议异常，只返回默认消息 |
| `ServiceUnavailableException` | `SERVICE_UNAVAILABLE` | 503 | 只返回默认消息，隐藏 Cause |

MVC 框架错误另外使用 `VALIDATION_ERROR`（422）、`NOT_FOUND`（404）、`METHOD_NOT_ALLOWED`（405）和
`UNSUPPORTED_MEDIA_TYPE`（415）。这些是跨应用稳定类别，不要求业务为每个字段或框架异常创建新枚举。

参数和并发异常支持无参、`(String message)`、`(String message, Throwable cause)`；业务检查保留
`(String message)` 和 `(String message, Throwable cause)`。消息不得包含凭据、敏感明文、内部诊断或完整请求。
`ServiceUnavailableException()` 与 `ServiceUnavailableException(cause)` 不要求业务枚举，已有带枚举构造器继续兼容。

`FailException` / `ErrorException` 保留基础分类与既有构造器：前者表示结果明确的拒绝，后者表示系统异常或未知结果。
是否重试由调用方结合幂等、次数上限和退避决定，异常本身不执行重试。`BusinessException` 保留兼容，普通业务检查
直接使用 `BizCheckFailException`，不再创建同义子类。

## 公共分页

`paytech-common-core` 的 `com.paytech.foundation.web.paging` 提供两个轻量 record，不引入 Spring 或数据库；模块因 Money
Codec 依赖 Jackson 3。
`PagingRequestBody.of(page, size)` 只为 null 参数补默认值：page 为 1、size 为 10。显式值必须满足
page 为 1 到 100000、size 为 10、20、50、100 或 200，否则抛出带安全中文说明的 `InvalidParameterException`。
构造器同样校验，服务无需复制范围或条数判断；`offset()` 返回零基偏移量，并使用精确乘法检查溢出。

```java
PagingRequestBody paging = PagingRequestBody.of(page, size);
PagingResponseBody<AccountDto> result = new PagingResponseBody<>(
    items, paging.page(), paging.size(), total);
PagingResponseBody<AccountView> response = result.map(this::toAccountView);
```

`PagingResponseBody<T>` 的 JSON 保持 `{items, page, size, total}`，其中 total 为 long。`map(...)` 仅转换列表元素，
保持顺序和全部分页元数据；空页仍保留原页码与总数。items 为不可变副本，禁止 null 列表或元素。响应不把
上游元数据改写为默认值，消费方仍负责检查自己的远端协议；它只用于分页查询，不包装其他成功响应。

## HTTP Client Support

`paytech-http-client-support` 是依赖 Common Core、`paytech-http-problem`、Spring Web/AOP/Context、Jackson 3 与 SLF4J API 的普通 JAR，不依赖
MVC Starter、Servlet、服务发现或业务 API，也不注册 Bean。宿主按实际 JSON 协议提供消息转换器。

宿主使用 Spring 的 `@ImportHttpServices(group = "service-name", types = OwnerApi.class)` 自动注册接口。
返回以下 Group Configurer Bean 即可复用公共异常处理，无须手工创建 RestClient 或接口代理：

```java
return HttpServiceClientFactory.configureGroup("service-name", HttpServiceClientOptions.strict(), ContractError.values());
```

Spring Boot 提供超时/重定向等 HTTP Service Group 配置，Spring Cloud LoadBalancer 负责按服务名选择实例，
Nacos Discovery 提供实例列表。内部服务 Group 不设置 `base-url`，服务 ID 默认取 Group 名；不要配置为固定实例 Origin。
本 JAR 仍不自动注册 Bean，不依赖 Boot/Cloud/Nacos，也不替业务系统选择依赖或服务名。
既有显式传输宿主仍可使用 `HttpServiceClientFactory.create(restClient, ...)`；两种入口安装相同的单层异常边界。

入口包为 `com.paytech.foundation.httpclient.api`。工厂在 Spring 原生 HTTP 接口代理最前方安装一次异常转换，
业务调用直接使用 typed API，不再给每个方法加 Supplier、try/catch 或同义代理。宿主仍配置发现、
超时和底层客户端的禁止自动重定向；传入的 `RestClient` 不应另装 `defaultStatusHandler`，避免先执行的状态
处理器覆盖公共语义。共享边界不重试、不记录凭据、Body、Header 或异常原文。

- 工厂与 Group 入口均统一安装出站拦截器：每次同步调用读取 MDC `requestId`，校验后写入 `X-Request-Id`。
  与 MVC `RequestIdFilter` 共用 `ApiProblemProfile.REQUEST_ID_MDC_KEY`，不修改 MDC、不缓存请求 ID，也不复制其他 MDC 字段。
  缺失或非法 MDC 值不参与传播；没有当前 MDC 时保留调用者显式传入的 Header。应用无需逐服务重复实现 Request ID 传播。
- 对成功响应要求闭合 JSON 的具体接口，可选择 `withStrictJsonResponses(maximumBytes)`。
  这使用标准 Jackson typed 绑定，拒绝缺失构造字段、null 原始类型、重复/未知字段、隐式标量类型转换和尾随 JSON；
  允许 nullable 引用字段显式 null。公共出站拦截器按 UTF-8 响应体字节上限同时约束成功与错误响应，超限归入 502。
  这是显式接口策略，不改变未选择它的既有 DTO 兼容性；业务日期、资格和原因之间的约束仍由业务适配器解释。
- `3xx` 一律作为 `UPSTREAM_INVALID_RESPONSE`，包括返回 `void` 的命令；底层必须禁止自动跟随重定向。
- 非 `HEAD` 的第一方 `4xx/5xx` 必须是 `application/problem+json`，包含六个非空字符串
  `type,title,detail,code,requestId,timestamp`；`type=about:blank`，title 必须匹配实际标准状态，Request ID
  Header/Body 必须相等，timestamp 必须是 UTC 六位小数。`status/instance/message` 被拒绝；未知顶层扩展可忽略，
  可选 `errors` 必须是非空的精确 `field/message` 数组。
- 合法 `5xx` 在协议校验后转换为依赖失败：504 与读取超时是 `TIMEOUT`，其他 5xx、连接失败和连接超时是
  `UNAVAILABLE`。默认均生成保留原 Cause 的 `ServiceUnavailableException`；确需区分 503/504 时，宿主在
  `strict()` 或 `migrationWithLegacyErrorResponse()` 返回的 Options 上调用 `.withDependencyFailureMapper(...)`，映射为自己的安全异常。
- 合法 `4xx` 的三个公共业务类别保留安全本地默认消息；显式注册的特殊错误码也只使用枚举默认消息。未知码、
  缺字段、ID/时间/title 不一致或无法解码的错误体，以及正常 DTO 的 JSON/媒体类型解码失败均归入安全的 `502`。
- `HEAD` 错误只校验 4xx/5xx 状态、Problem 媒体类型和一个合法 Request ID Header，不读取或要求 Body。
- `HttpServiceClientOptions.strict()` 是默认值。Legacy Reader 只在已知第一方 Target 显式选择
  `migrationWithLegacyErrorResponse()` 时接受 `application/json` 的精确 `{code,message,requestId,timestamp}`；它只转换入站错误，
  不重新输出旧格式。全部已部署 Producer/Consumer 切换并验证前保留，之后单独删除；不根据内容猜测协议。
- 特殊错误注册按 Code 防重，不能占用任何 `CommonErrorCode`。Foundation 不依赖业务枚举，不按前缀猜测错误。
- 同步 HTTP 方法除 `void` / `Void` 外要求非空返回值；默认辅助方法不受此检查。业务 DTO 字段仍由宿主校验。
  有意 nullable、异步、流式或第三方非 Foundation 错误契约接口不适用此工厂，应使用其自身协议适配器。
- 未预期运行时异常保持原样向上传播；系统响应只公开公共默认消息，Cause 不外显。调用失败不代表命令未执行，
  重试必须由具备幂等契约的业务能力决定。

模块测试通过随机 Loopback 端口的真实 HTTP 资源 Simulator 检查错误转换及请求次数，不依赖数据库或外部环境。

## MyBatis Support

持有数据库的服务按需引入普通 JAR，不需要 Spring 自动配置：

```xml
<dependency>
    <groupId>com.paytech</groupId>
    <artifactId>paytech-mybatis-support</artifactId>
</dependency>
```

`InstantTypeHandler` 将 MySQL `DATETIME(6)` 明确按 UTC 映射为 `Instant`。内部 DataObject 的闭合字符串枚举使用
实现 `CodeEnum` 的 Java 枚举，并为每个类型显式注册 `CodeEnumTypeHandler`；未知数据库 Code 会抛出
`SQLException` 并使当前读取失败，不会返回 `null`。外部 API/MQ DTO 仍使用字符串 Code，不能直接暴露 Java
枚举。该模块不扫描业务包、不替换 MyBatis 全局默认枚举处理器，也不持有业务枚举。

## Command Idempotency

`paytech-command-idempotency` 是按需启用的服务本地组件，不是中央幂等服务。宿主直接声明依赖并开启配置：

```xml
<dependency>
    <groupId>com.paytech</groupId>
    <artifactId>paytech-command-idempotency</artifactId>
</dependency>
```

```yaml
command-idempotency:
  enabled: true
```

组件注册自己的 `t_command_idempotency` MyBatis Mapper、Repository 和 `CommandIdempotencyService`，提供唯一 winner
Claim、Lease 续租/到期接管、Version CAS、业务阶段推进以及稳定成功/失败响应冻结。所有写方法要求调用方已经处于本地
可写事务；组件不会创建事务、执行远端调用、包装业务 Callback 或自动重试。

业务服务继续拥有 Caller Scope、Operation Type、请求 JCS/Hash、Snapshot/Response Schema、恢复阶段、资源坐标、错误码、
事务编排和重放策略。Operation Type 与 Recovery Stage 以稳定 String Code 进入公共 API，Foundation 不引用业务枚举或 DTO。
每个服务使用自己的数据库和本地表；完整边界见
[Foundation 服务本地命令幂等能力决策](../../agent-docs/decisions/2026-09-09-foundation-command-idempotency.md)。当前只批准
Commerce 迁移，其他服务不会因为引入 BOM 或 Foundation Reactor 自动接入。

当前 JDK 25 聚焦 `clean verify` 中该模块 11/11 通过，覆盖显式启用/禁用、Mapper XML 装载、调用方写事务要求、
Claim winner、Lease 返回值、CAS 冲突和未知持久化状态失败关闭。测试不创建数据库、不使用 H2、Testcontainers 或自动
Schema 初始化；真实 SQL 行为随获准业务消费者复用其服务本地数据库验证。制品只安装到本机 Maven 仓库，未发布或部署。

## MVC Web Starter

Starter 自动配置 Request ID/MDC、统一 Code-MoR Problem 响应和 `HttpRequestResponseLoggingFilter`。除 `HEAD`
外，错误响应固定为 `application/problem+json`，Body 为六个必填成员及按需出现的 `errors`；`HEAD` 保留状态、
Problem 媒体类型、Request ID 和批准的标准 Header，但不创建时间或传输 Body。单个且符合
`[A-Za-z0-9][A-Za-z0-9._-]{0,63}` 的入站 Request ID 会被保留；缺失、重复或非法时生成新值。非 `HEAD` 响应
Header/Body 必须相等，`HEAD` 只写 Header；timestamp 由 UTC Clock 一次生成并固定为六位小数。标准 `Date` 由真实 HTTP
容器提供，不增加 `X-Timestamp`。

`GlobalExceptionHandler` 和更内层的 `ProblemExceptionFilter` 共用唯一
`MvcProblemExceptionMapper -> ProblemResponseWriter` 路径。后者捕获 DispatcherServlet 之前仍未提交的应用异常，
但会重新抛出已提交响应和已识别的客户端断开，不追加第二个 Body。Writer 在写入前重置未提交响应，只保留映射器
批准的 `Allow`、`Accept`、`Accept-Patch` 等标准 Header；宿主不创建本地 Advice 或同义错误 DTO。

标准 Jackson/MVC 请求体解析或类型错误统一返回 `400 INVALID_REQUEST`；成功绑定后的 Jakarta Bean Validation
约束错误返回 `422 VALIDATION_ERROR`，并只投影安全的 `field/message`。未知属性是否允许由各 Owner API 合同决定，
Foundation 不维护业务 DTO 字段集合，也不提供 API 专用异常层级。需要表达“字段可缺失，但显式 JSON `null` 非法”时，
契约模块可复用 Common Core 的 `RejectExplicitNullDeserializer`；严格可选时间字段可复用
`StrictOptionalInstantDeserializer`。这两个组件只负责 Jackson 绑定差异，不改变统一 MVC Problem 分类。

确有协议需要的暂态业务异常可实现无 Spring 依赖的 `RetryAfterProvider`，返回 1 至 86400 秒的整数等待时间。
Mapper 只为该异常增加一个标准 `Retry-After` Header；零、负数、超上限或 Provider 求值失败都安全地省略该 Header，
不会改变原 Problem 状态、Code 或 Body。此扩展点不允许业务注入任意 Header。

`HttpRequestResponseLoggingFilter` 在每次请求结束时记录一条
名为 `HTTP request completed` 的 INFO 事件，结构化字段包括 Request ID、Method、原始 URI/Query、完整请求体、
响应状态、完整响应体和毫秒耗时；它不读取或记录 Header 与 Cookie，也不进行字段脱敏或截断。
`ServiceUnavailableException` 使用错误码的安全默认消息返回 HTTP 503，其他 `ErrorException` 默认返回 HTTP 500。
`BizCheckFailException` 和 `InvalidParameterException` 默认返回 400，公共 `CONCURRENT_REQUEST` 固定返回 409。
成功绑定后的请求 Body 字段约束错误返回 422 `VALIDATION_ERROR`；仅在存在可安全投影的字段时携带非空 `errors`，每项只含
`field/message`。Header、Path、Query、混合验证以及 JSON 语法、结构和类型错误返回 400 且不含 `errors`。未映射资源、Method 和媒体类型分别返回 404/405/415，
其余标准 4xx/5xx 保留真实状态/title 并使用安全的通用 Code/detail。

需要按公开错误码区分浏览器状态的 MVC 宿主可提供一个 `ApiErrorStatusResolver` Bean。共享 Mapper/Writer
仍负责错误体、Request ID 和时间戳；Resolver 只声明状态，不引入服务本地 Advice 或异常子类。`FailException` 只接受
4xx 映射，`ErrorException` 只接受 5xx；空结果或跨类别状态回退默认 400/500。公共 `CONCURRENT_REQUEST` 的 409、
`UPSTREAM_INVALID_RESPONSE` 的 502、`ServiceUnavailableException` 的 503、框架映射和未知异常的 500 不受扩展影响。Resolver 供确有独立前端
处理契约的稳定错误码使用，不要求宿主维护所有业务拒绝的状态目录。错误体始终由共享处理器生成。

认证等禁止记录内容的接口必须通过共享配置划定边界，不创建服务本地重复 Filter：

```yaml
web:
  logging:
    metadata-only-path-prefixes:
      - /auth
      - /internal/auth
```

前缀是**不含 Servlet Context Path** 的应用内绝对路径，按完整路径段匹配自身及所有子路径，包括未映射和错误请求。
例如 `/auth` 同时保护 `/auth` 与 `/auth/login`，不匹配 `/authentication-status`；配置 `/` 保护全部请求。
默认列表为空，普通端点继续记录完整内容。前缀必须是固定、规范路径，不能包含通配符、编码、分号、点路径段、重复
斜线、尾斜线（根路径除外）、Query 或 Fragment；非法配置直接启动失败。

受保护请求在创建缓存 Wrapper 前分流，原始请求和响应直接交给下游；Filter 不获取、补读、缓存或记录 Body，也不记录
Query、Header、Cookie 或原始敏感路径后缀。每个请求仍记录一条 INFO 完成事件，仅含 `requestId`、`method`、安全
`uri`、`status`、`elapsedMs`；安全 URI 为 `/auth/**` 一类配置前缀。路径包含编码、分号、点路径段、重复斜线、反斜线、
其他非规范字符或无法可靠剥离 Context Path 时，保守使用同一保护模式并记为固定 `/[unresolved]`，不尝试解码后输出。
共享请求属性让错误处理延续保护，未知异常只记录安全 Request ID，不记录异常消息或堆栈。该边界不替代宿主业务代码
和第三方组件自身的敏感日志约束，也不改变请求处理结果。

## Caffeine Starter

服务通过统一 BOM 引入 Starter，不声明版本：

```xml
<dependency>
    <groupId>com.paytech</groupId>
    <artifactId>paytech-caffeine-starter</artifactId>
</dependency>
```

仅引入 Caffeine Starter 时，它是唯一的 CacheManager，可以直接使用 Spring Cache 注解。同时引入 Redis Starter
时，Redis 是默认 CacheManager；本地缓存必须显式指定 `caffeineCacheManager`。对于保证返回值非空、并发读取
同一 Key 的加载型缓存，可使用 `sync = true`，避免同一实例内重复回源：

```java
import static com.paytech.foundation.caffeine.api.CaffeineCacheConstants.CACHE_MANAGER_BEAN_NAME;

@Cacheable(cacheNames = "payment-method", key = "#code",
    cacheManager = CACHE_MANAGER_BEAN_NAME, sync = true)
public PaymentMethod findPaymentMethod(String code) {
    return paymentMethodRepository.findByCode(code);
}
```

默认配置已经限制每个缓存最多 10,000 条、写入 10 分钟后过期、拒绝缓存 `null` 并记录命中统计。业务服务只需在
确有不同容量或时效要求时覆盖：

```yaml
caffeine:
  maximum-size: 20000
  expire-after-write: 5m
  cache-names:
    - payment-method
    - provider-capability
```

`cache-names` 为空时，第一次使用某个名称会按上述统一边界创建缓存；配置后则作为白名单，未知名称不会被动态
创建。`maximum-size` 必须大于零，`expire-after-write` 至少为 1 毫秒，因此 Starter 不会创建无容量边界或永不
过期的缓存。如宿主提供名为 `caffeineCacheManager` 的 Bean，Starter 会按名称退让；不需要本地缓存的服务不要
引入该 Starter。

默认拒绝缓存 `null`。可能返回空值的方法必须去掉 `sync = true`，并使用
`@Cacheable(..., unless = "#result == null")` 明确跳过缓存，因为 Spring Cache 不允许同时使用 `sync` 和
`unless`。Starter 固定开启命中统计并拒绝空值，不再暴露两套容易产生跨服务差异的开关；确有特殊语义的服务应
提供自己的 `CacheManager`，自动配置会退让。

生产服务应配置固定的 `cache-names` 白名单，Cache 名称只能是代码级能力名称，不得拼入 Client ID、Order ID、
租户、Token 或其他动态/敏感值。白名单还能让 Actuator/Micrometer 在启动时发现缓存并绑定指标；动态创建的缓存
虽然会记录 Caffeine Statistics，但不保证被启动期的指标绑定自动发现。

Caffeine 保存的是当前 JVM 内的对象，不进行 JSON 或 JDK 序列化，也不会在多个服务实例间同步。它只适合可从
权威数据源重新查询的加速数据，不得用作业务事实、幂等记录、锁或跨实例一致性依据，也不得缓存 Vault Reveal
结果、凭据或其他敏感明文。容量淘汰和到期后都必须允许安全回源。

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
# 在 paytech/ 下执行
./mvnw -pl foundation/paytech-caffeine-starter -am test
```

## Redis Starter

服务通过统一 BOM 引入 Starter，不声明版本：

```xml
<dependency>
    <groupId>com.paytech</groupId>
    <artifactId>paytech-redis-starter</artifactId>
</dependency>
```

### 默认的类型化 Redis Cache

Redis Starter 提供同一实例的 `cacheManager`/`redisCacheManager` Bean，并将其作为 Spring Cache 默认实现。每个
Redis Cache 必须由宿主服务声明唯一名称、具体 DTO 类型和 TTL：

```java
@Configuration
public class PaymentCacheConfiguration {

    @Bean
    RedisCacheDefinition<PaymentMethodCache> paymentMethodCacheDefinition() {
        return RedisCacheDefinition.of(
            "payment-method",
            PaymentMethodCache.class,
            Duration.ofMinutes(30)
        );
    }
}
```

不指定 `cacheManager` 的注解默认使用 Redis：

```java
@Cacheable(cacheNames = "payment-method", key = "#code")
public PaymentMethodCache findPaymentMethod(String code) {
    return paymentMethodRepository.findCacheByCode(code);
}
```

一个 Cache 只能存放一种具体类型。类型不能是 `Object`、接口、抽象类、Primitive、数组、`Collection`、`Map` 或
`Optional`；集合结果应包装为一个明确的 Cache DTO。缓存名称必须使用小写 kebab-case，重复名称、未注册名称、
空类型或小于 1 毫秒的 TTL 都会明确失败。返回值类型不匹配时使用稳定错误码
`REDIS_CACHE_VALUE_TYPE_MISMATCH` 失败，不会把错误类型的数据写入 Redis。

Redis Cache 使用 Jackson 3 普通 UTF-8 JSON，不保存 Java 包名、类名或其他类型元数据，也不使用 JDK 序列化；
实现 `CodeEnum` 的枚举继续保存稳定 `code`。物理 Key 统一为
`{服务前缀}:cache:{cache-name}::{key}`，例如 `payment-service:cache:payment-method::visa`。未知 Cache 不会动态
创建。CacheManager 开启事务感知和统计；整个 Cache 清理使用 SCAN 批处理，不使用阻塞式 KEYS。

默认 Redis CacheManager 使用 non-locking writer。事务感知只把普通 `put`、`evict`、`clear` 延迟到事务提交；
`putIfAbsent` 以及 `@Cacheable(sync = true)` 的加载写入不会获得这个延迟语义，因此事务方法不要依赖它避免回滚后
留下缓存。Redis 上的 `sync = true` 也不等于跨实例 single-flight；需要单实例热点合并时使用显式的
`caffeineCacheManager`，需要跨实例业务互斥时使用具有明确 Lease 的 Redisson 锁或数据库并发控制。

同时使用两种缓存时：

```java
import static com.paytech.foundation.caffeine.api.CaffeineCacheConstants.CACHE_MANAGER_BEAN_NAME;

// 默认 Redis
@Cacheable(cacheNames = "payment-method", key = "#code")
public PaymentMethodCache findShared(String code) {
    return loadFromDatabase(code);
}

// 显式 Caffeine，本方法只使用本机缓存，不会自动再写 Redis
@Cacheable(cacheNames = "provider-capability", key = "#providerCode",
    cacheManager = CACHE_MANAGER_BEAN_NAME, sync = true)
public ProviderCapabilityCache findLocal(String providerCode) {
    return loadCapability(providerCode);
}
```

这是“按方法选择 CacheManager”，不是自动 L1 Caffeine + L2 Redis。现阶段不引入双层缓存的更新广播、失效同步和
一致性复杂度。Caffeine 和 Redis 应使用不同的 Cache 名称；Caffeine 对应的 `@CachePut`、`@CacheEvict` 和
`@Caching` 也必须显式写 `cacheManager = CACHE_MANAGER_BEAN_NAME`，否则更新或删除会误操作默认 Redis Cache。
外部服务从 `CaffeineCacheConstants` 静态导入该编译期常量，不需要重复硬编码 Bean 名。

Redis Cache 同样拒绝缓存 `null`。可能返回空值的方法使用 `unless = "#result == null"` 明确跳过缓存，不要同时
使用与 `unless` 不兼容的 `sync = true`。Cache DTO 优先使用无泛型参数的 `record` 或 `final` 类；Starter 会拒绝
泛型 Class、非静态成员类和无法安全恢复明确类型的容器，并拒绝把注册类型的子类写入同一个 Cache。

### RedisJsonTemplate 与 StringRedisTemplate

不适合方法级 `@Cacheable`、需要在 Repository 中显式控制 Key/TTL 的业务对象缓存使用 `RedisJsonTemplate`；它
要求读取时显式传入 `Class<T>` 或 `TypeReference<T>`。计数器、位图、集合及明确需要字符串原生命令的场景继续
注入 `StringRedisTemplate`。两者底层都使用 Spring Boot 管理的 Lettuce `RedisConnectionFactory`，并共享同一个
服务级 Namespace。

Starter 不创建含 Java 类型信息的通用 `RedisTemplate<String, Object>`，也不使用 JDK 序列化。默认名为
`redisTemplate` 的 Bean 与 `stringRedisTemplate` 是同一个带 Namespace 的字符串模板，避免 Spring Boot 额外创建
一个使用 JDK 序列化的模板。`RedisJsonTemplate` 不是业务 Repository；每项业务能力仍应在自己的 Repository 中
封装 Key，并显式定义 TTL、容量上限和 Redis 不可用时的处理语义。

```java
redisJsonTemplate.set("cache:order:" + orderId, orderCache, Duration.ofMinutes(10));
OrderCache cached = redisJsonTemplate.get("cache:order:" + orderId, OrderCache.class);

List<OrderCache> orders = redisJsonTemplate.get("cache:orders:" + clientId,
    new TypeReference<List<OrderCache>>() {
    });
```

Redis 中只会保存类似 `{"orderId":"1001","status":"PROCESSING"}` 的 JSON，不包含 `@class`、Java 包名或类名。
类名或包名重构不会改变缓存协议；结构不兼容时由业务升级 Key 版本，例如从 `cache:order:v1:*` 切换到
`cache:order:v2:*`。为支持滚动升级，读取会忽略新增的未知字段；删除、改名或改变字段语义仍必须升级 Key 版本。
JSON 不提供隐式默认 TTL，`set` 和 `setIfAbsent` 每次都必须传入至少 1 毫秒的 TTL；写入 `null` 会明确失败。
实现 `CodeEnum` 的枚举按稳定 `code` 序列化和反序列化，不使用可能被重构的枚举 `name()`。

Redisson 只用于锁、可过期信号量等协调能力，默认关闭。启用时复用 `spring.data.redis.*` 的地址、数据库、
凭据、超时和 SSL 配置，不再维护第二份连接密钥：

```yaml
spring:
  application:
    name: payment-service
  data:
    redis:
      host: 127.0.0.1
      port: 16379
      password: ${REDIS_PASSWORD}
      connect-timeout: 2s
      timeout: 2s

redis:
  coordination:
    enabled: true
    resource-lock:
      wait-time: 500ms
      lease-time: 30s
    threads: 4
    netty-threads: 4
    connection-minimum-idle-size: 1
    connection-pool-size: 16
    subscription-connection-minimum-idle-size: 1
    subscription-connection-pool-size: 8
```

Starter 默认把 `spring.application.name` 作为 `RedisJsonTemplate`、`StringRedisTemplate` 和 Redisson 共用的服务级
Key 前缀。
例如应用名为 `payment-service`：

- `redisTemplate.opsForValue().set("cache:order:1", value)` 的物理 Key 是
  `payment-service:cache:order:1`；
- `redissonClient.getLock("refund:" + refundId)` 的物理 Key 是
  `payment-service:refund:{refundId}`。

业务代码只传入 `{capability}:...` 形式的逻辑 Key，不再重复拼接服务名。如有测试隔离或 Namespace 迁移需求，
可通过 `redis.key-prefix` 为两种客户端统一显式覆盖。应用名和覆盖值都必须使用小写 kebab-case，覆盖值还可以使用
冒号分段，且最长 255 个字符；两者都缺失时应用启动失败。

前缀只应用于 Redis 顶层 Key，不应用于 Hash field、Value 或发布订阅 Channel。单 Key、多 Key、`keys(pattern)`
和 `scan(options)` 都会自动限定在当前服务 Namespace；`randomKey()` 与跨 Key 的 `SORT BY/GET` 无法保证隔离，
因此明确禁用。Lua 脚本使用的全部 Key 必须通过 `KEYS` 参数传入，不得在脚本或 `ARGV` 中硬编码 Key。业务代码
不得使用通用 `RedisTemplate<Object, Object>`、原始 `RedisConnection` 或 `RedisCallback` 绕过 Namespace，也不得
使用 Redisson `RKeys`、`flushdb`、`flushall`、`swapdb` 等全库能力。

这里的 Namespace 是共享 Redis 中的默认防误用约定，不是恶意代码隔离或权限边界；生产服务间的强隔离仍需由
独立凭据、Redis ACL 或独立实例/集群承担。

启用协调能力时 `spring.data.redis.connect-timeout` 与 `spring.data.redis.timeout` 必须显式配置，不沿用客户端
隐式默认值。业务方传给 `getLock(...)` 的名称应以能力开头并描述具体资源。锁调用必须显式给出等待时间和
Lease，并只由持有锁的线程释放：

```java
RLock lock = redissonClient.getLock("refund:" + refundId);
boolean acquired = lock.tryLock(100, 5_000, TimeUnit.MILLISECONDS);
try {
    if (!acquired) {
        return;
    }
    processRefund(refundId);
} finally {
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

#### Processor 资源锁模板

新增写接口按[公共编码规范](../../agent-docs/DEVELOPMENT_CONVENTIONS.md#52-用例编排与职责划分)复用 [AbstractProcessor](paytech-common-core/src/main/java/com/paytech/foundation/processor/AbstractProcessor.java)
和 [AbstractLockProcessor](paytech-redis-starter/src/main/java/com/paytech/foundation/redis/core/processor/AbstractLockProcessor.java)。
`process` 先拒绝调用方已有活动事务，再执行基础校验；有锁模板通过 `lockCommand` 取得参数，获取全部资源锁后调用
`processWithinLock`，处理完成后释放。业务本地事务在锁内按阶段开启并结束；不在已有事务中调用另一个 Processor。

默认 `validateBasic` 检查请求非空，并执行内部 Request 的 Bean Validation 默认分组及 `@Valid` 嵌套约束；
失败使用公共 `InvalidParameterException`，具体业务的输入关系留在 `validate`。父类通过方法注入复用宿主
Spring 管理的 `Validator`；业务子类无需声明校验器依赖，非 Spring 创建的实例在调用前须显式 `setValidator`。
Common Core 只依赖标准 Validation API，运行实现由宿主的 validation starter 提供，不自行创建 ValidatorFactory。
特殊认证合同仍可通过已有的 `validateBasic` 重写保留错误语义。完整决定见
[公共校验决定](../../agent-docs/decisions/2026-09-17-processor-basic-validation.md)。

`LockCommand.lock(key)` 持续等待并允许中断；`tryLock(key)` 立即尝试，未获取就返回公共并发冲突。
需要限定等待时间时使用 `tryLock(key, waitTime, leaseTime)`；指定租约也可使用 `lock(key, leaseTime)` 或
`tryLock(key, leaseTime)`。所有时间单位为毫秒，等待时间非负；租约为正数或 -1，-1 使用 Redisson Watchdog
自动续租，省略租约时默认 -1。等待中断恢复线程中断标记并保留 Cause；未获取锁不会执行业务或释放锁。
创建/获取锁时的 `RedisException` 由模板统一转换：包装 `InterruptedException` 时恢复中断标记并抛出
`ConcurrentRequestException`，其余 Redis 异常抛出 `ServiceUnavailableException`，均保留原 Cause。
异常转换只包围锁的创建与获取；`lockCommand` 和 `processWithinLock` 的业务异常原样传播，
消费者无需为锁异常重写 `processRequest`。确有锁前资源解析时仍可保留该扩展。
处理结束只释放当前线程仍持有的锁，失去持有权或释放失败记录固定告警，不覆盖业务结果。

模板为逻辑 Key 添加 `lock:` 前缀，并沿用 Redisson 的服务 Namespace。它直接使用 `LockCommand` 参数，
不读取下述 `@ResourceLock` 的等待/租约配置；同一入口不能叠加两套锁。Payment 的选择与接口约束见
[写接口规范](../../payment-orchestration-service/docs/development/processor-conventions.md#6-processor-模板与锁)。
该默认来自 [2026-09-16 合并决定](../../agent-docs/decisions/2026-09-16-payment-based-coding-conventions.md)。既有消费者不因规范修改自动迁移；迁移时须核对同资源 Key、持锁范围及切换顺序。

多个资源可由同一 `lockCommand` 返回 `LockCommand.tryLocks(keys, waitTime, leaseTime)`，后两项使用 `Duration`。
`keys` 是已经包含资源前缀、不含服务 Namespace 的完整逻辑 Key；此工厂不再添加 `lock:`，以便保留迁移前的互斥域。
它在访问 Redis 前校验、去重并按 ASCII 顺序排列全部 Key，使用普通 `RLock` 和一个共享等待预算；
等待不能为负，租约必须显式且不少于 1 毫秒，不使用 Watchdog。全部锁取得后才调用 `processWithinLock`，
任何后续资源获取失败或业务结束都会逆序释放已取得的资源；异常处理复用模板公共逻辑。
模板不读取服务配置；迁移消费者可显式传入原配置，并继续负责启动校验。注解的代理/执行链门禁和指标不会自动迁入模板。
原单资源 `lock/tryLock` 工厂仍接受毫秒参数、添加 `lock:` 并使用 SpinLock；其他消费者不自动迁移。

#### 声明式资源锁

尚未迁移的既有单资源或多资源用例继续使用 Starter 的声明式 `@ResourceLock`，其合同如下；不要在业务模块重复封装
Redisson，也不将这些注解规则套在上述模板上。空 `key` 表示固定资源，逻辑 Key 精确等于 `prefix`；动态 Key 只接受完整的 `#param` 或
`#param.field`，可以 Repeatable 声明多个资源：

```java
@ResourceLock(prefix = "gateway-configuration:catalog")
@ResourceLock(prefix = "operations:account", key = "#command.accountId")
public void changeAccount(ChangeAccountCommand command) {
    transactionTemplate.executeWithoutResult(status -> accountDomainService.change(command));
}
```

Annotation 只能位于启动期可预测、非 lazy singleton 且 Spring 代理可达的具体实现类 `public` 同步
Processor/协调方法；类和方法不能是 `final`，override 必须在具体方法完整复写所有祖先锁声明，不能放在
Controller、Repository、Mapper、private helper、同类自调用、`@Transactional` 或 `@Async` 方法上。
一个用例需要的全部资源必须在同一入口一次声明，不得通过调用另一个带锁 Bean、方法引用或嵌套代理逐步拼装锁集合。
JDK 代理下每个锁方法必须由代理接口真实暴露。`Future`、`CompletionStage`、`Callable`/`Runnable`、JDK 函数式
接口、`Iterable`/`Iterator`/`Spliterator`/`Enumeration`、Java `Stream/BaseStream`、Reactive Streams、Java `Flow`、
Kotlin Flow 和 Kotlin suspend 等延迟执行入口不支持持锁。应用启动会同时扫描
Bean Definition 与已注册 singleton，并校验实际代理使用的正是 Foundation 固定 Advisor/执行链；未经
BeanPostProcessor 代理的手工 singleton、声明锁的 FactoryBean、非 singleton FactoryBean product，以及隐藏实现类的
lazy/scoped Bean 都会 Fail-Closed，不能绕过元数据冻结。任何适用于锁方法的其他 Advisor 都不得复用或另建
`ResourceLockMethodInterceptor`，否则在 Readiness 前拒绝启动。
锁必须包住业务方法内部完整的 `TransactionTemplate`：先求值全部 Key、取得全部 Redis 锁，再开启本地事务；
提交或回滚完成后才逆序释放。启动门禁会验证实际代理包含适用的 Resource Lock Advisor 且位于适用事务
Advisor 外层；若调用进入 Interceptor 时已有活动事务，会在求值动态 Key 和访问 Redis 前失败。

动态值只支持 `String`、`byte/short/int/long` 及包装类型和 `UUID`。字符串必须为 1–256 个字符，首字符为
ASCII 字母或数字，其余字符限 ASCII 字母、数字、`.`、`_`、`:`、`-`。不得把邮箱、手机号、姓名、政府标识、
Token、Cookie、Secret、Vault 值、完整请求体或自由文本作为锁 Key；非法值在连接 Redis 前以不回显原值的参数
错误拒绝。多锁按完整逻辑 Key 的 ASCII 顺序去重获取，所有锁共享一次服务级 `wait-time` 总预算，并使用显式
`lease-time`，不启用无限 Watchdog。
由于 Redisson 的显式 Lease 以毫秒传递，`lease-time` 最少为 1 毫秒；不足 1 毫秒会在启动时拒绝，
避免被向下取整为立即过期。`prefix` 不得再次包含当前服务 Namespace。使用 `@ResourceLock` 时必须使用 Foundation
根据同一套 Spring Data Redis 配置创建并登记来源的唯一 `RedissonClient`；宿主自定义或替换客户端即使
`NameMapper` 看起来相同也会拒绝启动。唯一性检查包含 singleton、prototype 和 scoped 候选；验证完成后执行器
冻结该客户端对象身份，不在请求期间从可变 Bean 集合重新选择。启动期和每次运行期 Key 都会验证 `NameMapper` 精确添加一次当前
`redis.key-prefix`/应用名 Namespace，且可以正确反向映射。

竞争或中断映射为并发请求错误（HTTP 409），中断会恢复线程中断位并保留 Cause；Redis 连接、认证、超时或协议
错误 Fail-Closed 为服务不可用（HTTP 503）并保留 Cause。业务返回值和 Throwable 原样传播；释放只在当前线程
仍为 Owner 时调用普通 `unlock`，释放与观测故障不会覆盖已经形成的提交、回滚或业务结果，日志和指标只记录
固定 Prefix/状态，不记录动态 Key。Lease 到期导致释放前已丢失 Owner 时不强制解锁，但会以低基数
`ownership-lost` 状态记录固定 Prefix，便于发现 Lease 设置不足。

Redis Lock 只降低跨实例热点冲突，不是 fencing，也不替代数据库最终保护。每个消费者仍须分别记录并验证
“业务不变量—冲突事务—Redis 实际资源—数据库 CAS/条件 DML/唯一索引/必要局部行锁”；Lease 到期重叠时，
数据库保护必须仍给出合法串行结果或明确并发冲突。

当前 Redisson 自动配置只支持已确认的 Redis 单机模式；若配置 Sentinel、Cluster 或 Master/Replica，将在启动时
明确失败，不会静默退化。Redis 自动测试复用仓库 `local-environment` 的 `127.0.0.1:16379`，密码只从测试进程的
`PAYTECH_REDIS_PASSWORD` 环境变量读取；每次 JVM 使用不可预测 Namespace，并只用 `SCAN` 定向清理自己的 Key。
测试不读取 `.env.local`、不启动/停止容器、不使用 `KEYS *`/`FLUSHDB`，也不连接本机 `6379`。Redisson 启动连接
失败使用 `REDIS_CONNECTION_UNAVAILABLE` 的 `ErrorException` 表示结果未知；运行期
Lettuce/Redisson 异常不被 Starter 吞掉或自动降级，具体业务能力必须定义 Fail-Open 或 Fail-Closed 语义。

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
# 在 paytech/foundation 下执行
set -a
source ../../local-environment/.env.local
set +a
export PAYTECH_REDIS_PASSWORD="${PAYTECH_REDIS_PASSWORD:-$MOR_REDIS_PASSWORD}"
docker compose --env-file ../../local-environment/.env.local -f ../../local-environment/compose.yaml --profile base ps redis
../mvnw -pl paytech-redis-starter -am test
```

## Compensation Event

补偿能力嵌入每个业务服务，不是中央补偿服务。业务事实与 `t_compensation_event` 必须使用同一个
DataSource、同一个事务管理器并在同一本地事务中提交；提交后立即唤醒，XXL-Job 只负责兜底扫描。

### 简化后的执行模型

- 每个 `event_type` 只对应一个 `CompensationEventHandler`；
- 不区分本地执行和 MQ 执行类型。需要发布 RabbitMQ 消息时，业务服务实现一个只负责发布该消息的
  Handler；
- Handler 的正常输入按 `event_key + event_type + sub_event_type` 排列，并使用 `event_key` 和
  `sub_event_type` 查询服务自己的业务表；
- `payload_json` 可为空，只允许保存少量、确实没有落库来源的补充数据，不得承载完整业务上下文；
- 没有 `source_version`、`schema_version`、Canonical Envelope 或 Payload Hash；`event_key` 是业务查找键，
  不要求单列全局唯一；
- 唯一约束是 `(event_key, event_type, sub_event_type)`。同一行成功删除前，重复登记会复用原记录；
- 成功后删除补偿行。异常按 Handler Policy 重试，耗尽次数后进入 `FAILED`，由带审计的恢复接口重开。

一个 Handler 只做一件事：

```java
@Component
@AllArgsConstructor
public class PublishPaymentSucceededHandler implements CompensationEventHandler {

    private final PaymentSucceededFactRepository paymentSucceededFactRepository;
    private final RabbitTemplate rabbitTemplate;

    @Override
    public String eventType() {
        return "PublishPaymentSucceeded";
    }

    @Override
    public CompensationHandlerResult handle(CompensationEvent event) {
        PaymentSucceededFact fact = paymentSucceededFactRepository.findById(event.eventKey());
        rabbitTemplate.convertAndSend("topic_payment_payment", "payment.succeeded", fact.toMessage());
        return CompensationHandlerResult.success();
    }
}
```

这里发布所需的数据来自 Payment 数据库中的不可变事实行，而不是从 `payload_json` 还原业务对象，也不是
从可变聚合的“最新状态”临时重建历史消息。Rabbit Publisher Confirm、Exchange/Queue/Binding 和消息幂等性
由宿主服务的 RabbitMQ 配置与 Handler 测试负责。

### 事务内登记

```java
public void completePayment(String paymentId) {
    transactionTemplate.executeWithoutResult(status -> {
        paymentRepository.markSucceeded(paymentId);
        PaymentSucceededFact fact = paymentSucceededFactRepository.save(paymentId);
        compensationEventRegistrar.register(new CompensationEventRequest(
            String.valueOf(fact.getId()),
            "PublishPaymentSucceeded",
            "PAYMENT_SUCCEEDED_FACT"
        ));
    });
}
```

这个请求不需要 Payload。构造参数的固定顺序是 `eventKey, eventType, subEventType`。人工补偿时只需知道
业务查找键、Handler 类型和业务子类型，即可插入一条
`INIT` 记录或通过受控管理入口重新登记。

业务代码和 Foundation 内部统一使用 `TransactionTemplate`，不使用 `@Transactional`。登记操作采用
`MANDATORY` 传播级别加入调用方的业务事务；Claim 和每次执行结果落库使用独立的 `REQUIRES_NEW`
事务；人工重开使用 `REQUIRED` 事务并把状态修改与审计记录一起提交。

`registerWakeupAfterCommit(...)` 必须在上述真实事务内调用。它不会立即执行任务，只向 Spring 注册
`afterCommit` 回调；事务提交后才唤醒执行器，回滚则不唤醒。提交后进程即使在唤醒前退出，Scanner 仍会从
数据库恢复事件。

宿主 Migration 应为状态、次数、下次执行时间、锁版本和创建/更新时间设置安全默认值，使受控人工操作
只需提供 `id + event_key + event_type + sub_event_type`；状态列还必须限制为
`INIT/PROCESSING/ERROR/FAILED`。

只有数据没有任何持久化来源时才传第四个参数：

```java
new CompensationEventRequest(noticeId, "SendTransientNotice", "NOTICE", minimalPayload);
```

可选 Payload 必须是 JSON Object，默认最多 4 KiB；禁止凭据、卡数据、Token 等敏感字段。

### 宿主表字段

业务服务在自己的 PDManer 模型和经评审、人工执行的版本化 Schema SQL 中创建表，Foundation JAR 不自动建表。最小字段为：

```text
id
event_key
event_type
sub_event_type
payload_json              nullable
execute_status
execute_count
next_execute_at
lock_owner                nullable
lock_until                nullable
last_error_code           nullable
last_error_summary        nullable
lock_version              initial 1
create_time
update_time
```

必需索引：

```text
UNIQUE (event_key, event_type, sub_event_type)
INDEX  (execute_status, next_execute_at, id)
INDEX  (execute_status, lock_until, id)
```

这三个身份列使用 binary collation，与 Java 的大小写敏感比较保持一致。表中不定义 `CHECK CONSTRAINT`；
状态、非负次数、正锁版本、Lease 字段组合以及 Payload 格式由 Foundation 代码和开发约定保证。人工插入也必须
遵循这些约定。`payload_json` 为空或为有效 JSON Object，Java 登记入口限制为最多 4 KiB UTF-8。

### 配置

所有扫描、实例标识与线程池参数都有安全默认值。`instance-id` 默认取 `spring.application.name`，因此业务服务通常无需额外配置补偿参数：

```yaml
spring:
  application:
    name: payment-service
```

需要区分同一应用的特殊运行单元时，仍可显式设置 `compensation.instance-id` 覆盖应用名。默认 Lease 为 2 分钟、扫描批次为 100。协调线程池和 Handler 隔离线程池也可以按服务容量选择性覆盖：

```yaml
compensation:
  coordinator:
    core-size: 10
    maximum-size: 20
    queue-capacity: 80
    keep-alive: 60s
  handler:
    maximum-concurrency: 20
```

未配置以上可选项时直接使用示例中的默认值。默认队列容量 80 与扫描批次 100、最大协调线程数 20 配合，使一个完整批次可以被接收并允许线程池扩展到上限。每个 Handler 可覆盖 `policy()`；Handler timeout
必须短于 Lease。`compensationCoordinatorExecutor` 只负责 Claim、等待结果和状态转换；
`compensationHandlerExecutor` 使用 `SynchronousQueue` 执行实际 Handler，不缓存已 Claim 但尚未开始的 Handler。

旧版 Snapshot 的 `compensation.pool.*` 已拆分为 `compensation.coordinator.*` 和
`compensation.handler.maximum-concurrency`。旧键不会被静默忽略，而是使应用启动失败；升级宿主必须先完成配置迁移。

### XXL-Job 兜底扫描

`paytech-xxl-job-starter` 是通用 Executor Starter，不依赖也不感知补偿能力。`paytech-compensation-event` 主动依赖该
Starter，并由补偿模块自己提供 `CompensationEventXxlJobHandler`。业务服务引入 `paytech-compensation-event` 即可获得
统一的 XXL-Job `3.4.2` 兜底接入；只需要执行其他定时任务的服务也可以独立引入 Starter，而不会被传递引入补偿
数据库、Mapper 或执行链。正常的事务提交后唤醒仍不经过 XXL-Job Admin。

```yaml
xxl:
  job:
    admin:
      addresses: http://127.0.0.1:18090
      access-token: ${MOR_XXL_JOB_ACCESS_TOKEN}
      timeout: 3
    executor:
      enabled: true
      app-name: payment-service
      # Admin 在 Docker、服务在 macOS 原生进程时使用该注册地址。
      address: http://host.docker.internal:19999
      port: 19999
      log-path: ./logs/xxl-job/payment-service
      log-retention-days: 30
```

每个服务必须分配不同的 Executor 端口。`enabled` 默认为 `false`；开启时 Starter 强制校验 Admin 地址、
至少 32 字符的 Access Token、App Name、端口和日志保留期。配置中的 `executor.ip/address` 只影响注册地址，
XXL-Job 3.4.2 的内嵌 Netty 服务器仍监听所有网卡，因此本机需要防火墙，生产环境必须使用网络白名单或容器
网络隔离。

在 Admin 中为每个服务分别创建 Executor Group 和 Job：

- JobHandler 固定为 `compensationEventScan`；
- 路由策略使用 `SHARDING_BROADCAST`，阻塞策略使用 `SERIAL_EXECUTION`；
- Job 参数为空表示扫描所有事件类型，也可传英文逗号分隔的 `event_type` 白名单；
- XXL 失败重试次数设为 `0`，记录自身的业务重试仍由补偿 Policy 控制；
- 只允许创建 `BEAN` Job，不使用 GLUE Groovy/Shell 等在线代码功能。

每次 Job 只提交 `scanner-batch-size` 上限内的候选项。Executor 线程池背压导致的拒绝会保留数据库记录并把
本次 Job 标记失败；下一次扫描继续处理。异常原文、SQL、Payload 和 Event Key 不会返回 Admin，只返回异常
类型和候选/提交/拒绝数量。

### 测试

测试统一使用一个 `TestApplication` 和一个 `MockTestBase`。`application-mock.yaml` 直接连接本机
`paytech_foundation_test`，显式关闭 Nacos，不依赖配置中心。测试 Handler 根据 `event_key` 查询真实测试表；
同时覆盖无 Payload 的人工插入记录被 Scanner 执行的场景。

本地配置默认使用：

```text
MySQL  127.0.0.1:13306/paytech_foundation_test
Rabbit 127.0.0.1:5672（由具体业务 Handler 测试按需使用）
Redis  127.0.0.1:16379
Nacos  不参与 mock 测试
```

测试配置支持 `PAYTECH_TEST_DB_HOST/PORT/NAME/USER/PASSWORD` 环境变量覆盖。

### 编译

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
# 在 paytech/ 下执行
./mvnw -pl foundation/paytech-compensation-event -am -DskipTests test-compile
```
