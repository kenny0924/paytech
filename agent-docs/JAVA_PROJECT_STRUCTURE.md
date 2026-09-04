# Java 项目结构规范

> **2026-09-04 Operations 当前增量：** [第一版实施记录](./plans/2026-09-04-operations-console-first-slice.md) 更新本页此前“BFF 仅基础 / Console 前端和认证未实现”的状态：独立 React/TypeScript/Vite/Ant Design 前端与 BFF 浏览器适配已增加；Platform 增加显式启用的 Operations 认证/IAM/人员/审计代码和 22 表手工 SQL 投影；Gateway 已有动态 Snapshot，并扩展可信 Context 和四个 BFF 下游根。Vault 按用户要求暂用隔离开发 Mock + TODO，生产 Vault/KMS、手工 Bootstrap/Key 初始化、真实 MySQL/Redis 联调和部署仍需各自前置。下文旧基础阶段的实现范围描述在被本记录更新处仅保留历史意义，架构所有权与手工基础设施规则不变。


最后更新：2026-09-04

## 1. 使用范围

当任务涉及以下任一内容时，Agent 必须在修改代码前完整读取本文：

- 创建或调整 Java 服务和 Maven 模块；
- 新增 HTTP API、请求响应、Processor、Domain Service、Repository、Mapper；
- 新增外部系统客户端、MQ 生产者或消费者；
- 集成 Vault、`compensation-event` 或 XXL-Job 异步恢复任务；
- 调整包结构、依赖方向、事务边界或部署单元。

本文定义未来六个核心 Java 服务及内部 Vault 的默认工程形态。`api-gateway`、`operations-console-bff`、`client-console-bff` 和 `partner-console-bff` 的业务边界仍以 `APPLICATION_ARCHITECTURE.md` 为准；若它们不需要完整领域分层，不得为了形式一致强行创建空层。Gateway 首期还必须遵守 `plans/2026-08-29-operations-api-gateway-design.md`，只创建 `api-gateway-web` 和 `api-gateway-test`，不创建空 `*-api`。涉及可靠异步事件或敏感数据时，还必须分别读取 `COMPENSATION_EVENT_GUIDE.md` 和 `VAULT_SERVICE_ARCHITECTURE.md`。

Java、Spring、Maven BOM/Parent、MySQL、RabbitMQ、Redis、Nacos、Gateway 和可观测性版本统一以 [TECHNOLOGY_STACK.md](./TECHNOLOGY_STACK.md) 为准。本机运行方式以 [LOCAL_DEVELOPMENT_ENVIRONMENT.md](./LOCAL_DEVELOPMENT_ENVIRONMENT.md) 为准。

Operations 首期 `api-gateway` 已按批准边界实现；其路由、协议、Guard 与 JWT/JWKS 校验约束继续以 [`2026-08-30-operations-gateway-hardening-design.md`](../docs/superpowers/specs/2026-08-30-operations-gateway-hardening-design.md) 为准，应用传输和 Client IP 信任来源以 [`2026-09-02-mvc-web-logging-and-internal-http-simplification-design.md`](../docs/superpowers/specs/2026-09-02-mvc-web-logging-and-internal-http-simplification-design.md) 为准。Platform Support 的当前运行切片见第 3.3 节；Operations Console BFF 已按本次明确授权初始化最小应用基础，边界见第 3.4 节。除此之外，本阶段仍禁止主动为核心服务业务能力、其他 Console BFF/前端或 Vault 创建脚手架；只有用户明确要求开始实现具体 Java 项目或垂直切片时，才能应用本文创建目录或代码。

## 2. 参考项目结论

本文参考 `/Users/kenny/Desktop/Workspace/Code-PMX/transaction`。该项目采用一个父 Maven 工程和三个子模块：

```text
transaction/
├── pom.xml
├── transaction-facade-service/  # 对外契约 JAR
├── transaction-web/             # Spring Boot 可部署应用
└── transaction-test/            # 独立自动化测试模块
```

参考项目约有 13 个 Facade Java 文件、117 个 Web Java 文件和 48 个测试 Java 文件。它验证了“独立契约模块 + 可部署 Web 模块 + 测试模块”、极简入站适配、Processor 编排、Domain Service、Repository/Mapper 隔离，以及 Builder/Mocker/Checker 测试模式的可行性。

Code-MoR 继承模块隔离思想，但将 Dubbo `facade-service` 改为 Spring HTTP `*-api`。不复制参考项目的业务模型、公司包名、内部依赖版本、配置中心地址、数据库凭据或历史实现缺陷。

## 3. 标准 Maven 模块

核心服务默认使用以下结构；`<service>` 代表服务根名称，例如未来的 `client-contract`：

```text
<service>-service/
├── pom.xml
├── docs/
│   └── database/
│       └── <service>.pdma.json          # 仅在开始数据库设计时从统一模板创建
├── <service>-api/
│   ├── pom.xml
│   └── src/main/java/<base-package>/api/
├── <service>-web/
│   ├── pom.xml
│   └── src/main/
│       ├── java/<base-package>/
│       └── resources/
└── <service>-test/
    ├── pom.xml
    └── src/test/
        ├── java/<base-package>/
        │   ├── auto/                    # 默认执行的内部闭环测试
        │   ├── manual/                  # 显式 profile 才能运行的人工联调
        │   └── test/                    # Builder/Mocker/Simulator/Checker 等基础设施
        └── resources/                   # auto/manual 配置和 fixture
```

模块职责：

| 模块 | 职责 | 发布/运行 |
|---|---|---|
| 父工程 | 聚合模块、统一版本、插件和依赖管理 | `pom`，不运行 |
| `api` | 内部 HTTP 契约：`@HttpExchange` 接口、传输 DTO 和契约枚举 | 可发布 JAR，不运行 |
| `web` | Spring Boot 入口、业务实现、持久化和适配器 | 唯一业务部署单元 |
| `test` | `auto/manual` 测试、测试数据 DSL、本地中间件连接和外部系统 Simulator | 不发布、不部署 |

默认依赖方向：

```text
test ───────→ web ───────→ api

其他服务 ───────────────→ api
```

- `api` 不得依赖 `web` 或 `test`。
- `web` 依赖自己的 `api`，由 HTTP Controller 实现契约接口。
- `test` 可以依赖 `web` 以运行集成测试，但生产模块不得依赖 `test`。
- `auto` 必须从真实本服务入口完成内部闭环；数据库、Redis 和 MQ 复用开发者已启动的仓库 `local-environment`，测试不得创建、停止或重建容器。外部 HTTP/MQ 通过有状态 Simulator 和真实协议交互；完整边界以 `UNIT_TEST_GUIDE.md` 为准。
- 测试不得使用 `when(...).thenReturn(...)`、`@MockBean`、`@MockitoBean` 等方法级行为 Stub 替换生产链路；详细边界以 `UNIT_TEST_GUIDE.md` 为准。
- `manual` 必须由独立 Maven Profile 显式运行，不能进入默认 `mvn test` 和 CI。
- 其他内部 Java 服务只依赖提供方的 `api`，并在自己的外部集成适配器中使用它。
- 父 POM 用 `dependencyManagement` 固定版本；子模块只声明所需依赖。
- 不添加没有实际使用者的模块、依赖或占位类。
- 拥有数据库的项目使用 `DATABASE_DESIGN_GUIDE.md` 和统一 PDManer 模板；无数据库项目不创建空模型。

### 3.1 统一 Build、BOM 与 Parent

所有服务共享独立的构建项目，不在每个服务中复制版本属性：

```text
paytech-build/
├── paytech-dependencies/     # packaging=pom；统一依赖 BOM
└── paytech-parent/           # packaging=pom；统一插件和构建规则
```

- `paytech-dependencies` 导入 Spring Boot 4.0.8、Spring Cloud 2025.1.3 和 Spring Cloud Alibaba 2025.1.0.0 BOM，并管理 MyBatis、Redisson、XXL-Job 等未被上游统一管理的版本。
- `paytech-parent` 导入上述 BOM，固定 Java 25、UTF-8、Compiler、Surefire、Failsafe、JaCoCo、Enforcer 和 Spring Boot Maven Plugin 等规则。
- 每个服务父工程继承 `paytech-parent`；服务子模块只声明实际使用的依赖，不重复声明版本。
- BOM 只提供 `dependencyManagement`，不会自动引入 Starter 或公共 JAR。
- 本机开发的 `1.0.0-SNAPSHOT` 先发布到 Nexus `maven-snapshots`，服务统一通过 `maven-public` 解析；服务不得直接配置 Hosted 仓库 URL。
- 发布地址和认证来自开发者 Maven Settings，POM 只保留与 Settings 一致的 `maven-releases` / `maven-snapshots` 仓库 ID 和 `${nexus.url}`，不得保存凭据。

公共能力按边界拆分为 `paytech-common-core`、`paytech-mvc-web-starter`、`paytech-snowflake-id`、`paytech-caffeine-starter`、`paytech-redis-starter`、`paytech-xxl-job-starter` 和 `paytech-compensation-event`。`paytech-mvc-web-starter` 自动提供一个完整 Request/Response/耗时 INFO 终态日志和共享 `GlobalExceptionHandler`；MVC 服务不得复制本地日志 Filter、异常 Advice 或错误响应模型。Redis Starter 暴露同一实例的 `cacheManager`/`redisCacheManager` 并以 `@Primary` 作为 Spring Cache 默认 Manager；每个 Redis Cache 由宿主声明 `RedisCacheDefinition<T>(cacheName, concreteValueType, ttl)`，未声明名称不动态创建。Caffeine 只注册 `caffeineCacheManager`，仅用于可回源的有界进程内加速；同时使用两者时，Caffeine 方法必须在 Spring Cache 注解显式选择该 Manager，且两边使用不同 Cache Name。两者不是 L1/L2，也不使用 `spring.cache.type` 切换；不需要 Caffeine 的宿主不要引入对应 Starter。不得让任何 Cache 承担权威事实、幂等、锁、跨实例一致性、Vault Reveal、凭据或敏感明文，也不得把跨领域 DTO、业务枚举、Repository、Mapper 或第三方 Provider 模型放入公共 JAR。详细职责见 `TECHNOLOGY_STACK.md`。

公共能力按最小证据提取：天然属于跨服务基础设施，或至少两个真实服务已经共享同一稳定契约时，才新增职责单一的 Foundation JAR。单个服务的实现先保留在本服务，不为未来复用预建带系统名前缀的包装类、空模块、空层或总揽式 `common`；新增本地技术基础设施前先检查现有 Foundation 是否已经拥有该职责。

### 3.2 已实现的 API Gateway 工程边界

Operations 首期 Gateway 的实际仓库结构是：

```text
api-gateway/
├── pom.xml                              # 聚合、统一版本和继承式 Enforcer
├── api-gateway-web/                    # 唯一可运行的 Reactive Gateway 模块
├── api-gateway-test/                   # auto、Simulator、架构/E2E 与覆盖率证据
├── scripts/
│   ├── verify-gateway-boundaries.mjs
│   └── verify-jacoco.mjs
└── README.md                            # 本地前置、启动、验证、Guard 状态与 Access Log HMAC 手册
```

该项目没有 `api-gateway-api` 或其他空 `*-api` 模块，也不套用核心服务的 Domain/Repository/Mapper 分层。生产/测试依赖方向只是：

```text
api-gateway-test ────→ api-gateway-web
```

- `api-gateway-web` 使用 Spring Cloud Gateway Server WebFlux 5.0.3、OAuth2 Resource Server JWT、Spring Data Redis Reactive/Lettuce Lua、Actuator 和 Micrometer；它负责入口、Access JWT 与 Channel/Actor 校验、受信上下文、Guard、路由、请求 ID、结构化访问日志和健康/指标，不处理账号密码登录、页面聚合或业务编排。
- 公共面只包含 `POST /api/operations/auth/v1/login`、`POST /api/operations/auth/v1/refresh` 和 `POST /api/operations/auth/v1/logout` 三条静态精确路由。Catalog 以 `HttpMethod + rawPath` 和空 Query 匹配，并为每条路由显式指定唯一 Operations BFF 路径 `/auth/v1/login`、`/auth/v1/refresh` 或 `/auth/v1/logout`；没有通配或前缀改写。未匹配 Operations API 在 Gateway 本地返回错误，不转发给 Simulator/未来 BFF。Operations BFF、Platform Support 认证代码和前端不属于本实现。
- 生产模块禁止 MVC/Servlet、Foundation Redis/Caffeine/MVC Starter、Redisson、Nacos Client、Testcontainers、Mockito、Platform/core Client、Redis Template（包括 Reactive Template）/阻塞 Connection、手工 Reactor Blocking/Subscription 和应用自有动态 Route Repository。路由只来自三条编译时 Catalog 定义，Discovery Locator、Redis Route Repository、Retry 和原始 Path Metric Tag 均显式关闭。
- `api-gateway-test` 以 Compile Scope 依赖 `web`，从真实随机端口私有 HTTP Gateway 入口经过完整生产链，并通过真实内部 HTTP 连接 JWKS 和有状态 Operations BFF Simulator；测试默认携带 Nginx 会覆盖的单值合法 `X-Real-IP`。它不引入 Mockito/Testcontainers；Redis 只连接已运行的 `127.0.0.1:16379` 仓库 Compose Redis，只接受 `PAYTECH_REDIS_PASSWORD`，每次运行使用唯一 Namespace 并只以 `SCAN` 清理；测试不启动 Docker/Testcontainers，不连接开发者 `6379` Redis。
- 默认 `auto` 包含架构和真实 HTTP E2E 验收。`./mvnw clean verify` 之后还必须独立运行两个 Node 门禁；JaCoCo 对未舍入 Instruction/Branch 同时要求 `>= 90%`，只排除精确的 `ApiGatewayApplication`，并校验新鲜度与编译类一致性。
- `api-gateway-web` 产出普通 JAR 和可执行 `-exec.jar`；`api-gateway-test` 跳过 install/deploy 且不产出可部署 JAR。Application/Management 默认端口分别为 `18080`/`19080`，健康组成与运行命令见 [`api-gateway/README.md`](../api-gateway/README.md)。

### 3.3 Platform Support 当前运行切片

Platform Support 当前实际仓库结构是：

```text
platform-support-service/
├── pom.xml
├── platform-support-api/               # PLT-I20/PLT-I22 @HttpExchange 与不可变 typed DTO
├── platform-support-web/               # MVC、MyBatis、Nacos Discovery、Snapshot/JWKS Provider
├── platform-support-test/              # MySQL、HTTP、架构与覆盖率 auto 门禁
├── docs/database/                       # 已批准的完整 PDManer 设计输入
└── README.md
```

当前生产/测试依赖方向严格为：

```text
platform-support-test ────→ platform-support-web ────→ platform-support-api
```

- `platform-support-api` 当前发布有真实 Gateway 消费者的 PLT-I20 与 Operations-only PLT-I22 `@HttpExchange` 查询和不可变 DTO；成功调用分别返回完整 typed Snapshot 与完整有效 public JWKS，不使用 ETag/304。接口 DTO 的枚举语义使用 `String` Code，不暴露 Java Enum。
- `platform-support-web` 实现 Spring MVC/Actuator、Phase A MyBatis 21-table 投影、Snapshot/JWKS 读取校验组装和 Nacos Discovery 注册；它通过私有 HTTP 暴露 PLT-I20、PLT-I22 与 Actuator，不包含 Spring Security、X.509/mTLS、应用证书或本地异常处理器，并复用 Foundation 的日志与全局错误边界。Schema 与初始数据由 `docs/database/sql` 下两份手工 SQL 维护，应用启动不执行 SQL。
- `platform-support-test` 通过仓库 `local-environment` MySQL、真实随机端口 HTTP 和少量领域场景验证完整生产链，不使用 Mockito 或 Testcontainers。
- Reactor Enforcer 允许该切片所需的 JDBC/MySQL/MyBatis、Nacos Discovery 及其精确的 Crypto 工具依赖，禁止 Nacos Config、其余 Spring Security、Flyway、Testcontainers、Redis、MQ、XXL-Job、Gateway、Compensation Event 与 Mockito。
- Web 默认监听 `18106`，Actuator 使用同一 Listener；测试模块跳过 install/deploy，Web 同时产出普通 JAR 和可执行 `-exec.jar`。
- 当前切片不实现登录/Token 签发或其他认证/Tenant IAM 命令、CUSTOMER JWKS、API/Route/Guard 管理、限流运行态、Gateway Snapshot/JWKS 消费、补偿事件、Nacos Config 或部署自动化。任何此类能力都必须作为获批垂直切片按 TDD 增量加入。
- 详细设计、实施步骤和运行方式分别见[基础设计](../docs/superpowers/specs/2026-09-01-platform-support-application-foundation-design.md)、[实施计划](../docs/superpowers/plans/2026-09-01-platform-support-application-foundation.md)和 [`platform-support-service/README.md`](../platform-support-service/README.md)。

### 3.4 Operations Console BFF 应用基础

```text
operations-console-bff/
├── pom.xml                              # paytech-operations-console-bff，继承 paytech-parent
├── paytech-operations-console-bff-web/   # 唯一可部署 Spring MVC 应用
└── paytech-operations-console-bff-test/  # 随机端口、关闭 Nacos 的 auto 测试
```

- 根目录保留架构名称 `operations-console-bff`，新增 Maven Artifact 和模块目录使用 `paytech-*`；基础包为 `com.paytech.operations.console.bff`，启动类为 `OperationsConsoleBffApplication`。依赖方向只有 `test → web`，当前没有真实共享契约，不创建 `*-api`、空 Controller/Processor/领域层或未来客户端。
- Web 使用 Foundation MVC Starter、Spring Boot MVC、Actuator/Prometheus 和 Nacos Discovery；复用 Foundation 的 Request ID、日志与统一异常处理，不复制本地 Filter、Advice 或错误体。没有业务 API、登录/IAM、数据库、Redis、MQ 和前端。
- `spring.application.name=operations-console-bff` 与当前 Gateway Approved Target 一致；默认监听 `127.0.0.1:18201`，不配置 Servlet context path，管理端点复用同一 Listener。Nacos 默认 `127.0.0.1:8848` / `mor-local`，部署时 Namespace 必须与 Gateway 一致；`auto` 明确关闭发现并使用随机端口。
- Web 同时产出普通 JAR 和 `-exec.jar`，测试模块跳过 install/deploy。应用基础不代表认证和业务授权可用；新增认证端点前必须落实专项禁止凭据/Token Body 日志的边界，不能用重复服务本地 Filter 绕过。运行说明见 [`operations-console-bff/README.md`](../operations-console-bff/README.md)。

## 4. API 模块与内部 HTTP 契约

推荐结构：

```text
api/
├── <capability>/
│   ├── *CommandApi.java
│   ├── *QueryApi.java
│   └── *Models.java        # public record *RequestBody / *ResponseBody 及相关嵌套模型
└── enums/
    └── *Status.java
```

规则：

- 内部同步接口使用 Spring `@HttpExchange`，具体方法使用 `@GetExchange`、`@PostExchange` 等明确 HTTP 注解。
- API 按业务能力拆分，例如 `ClientEligibilityQueryApi`、`AgreementCommandApi`；禁止创建包含所有接口的巨型 `ClientContractApi`。
- `*Api` 只声明 HTTP 方法与协议注解；请求、响应及相关嵌套模型按业务能力组织到独立 `*Models` 容器，不直接定义在 API 接口中。容器使用 `public final` 与私有构造器，传输对象使用 `public record`，保留 `*RequestBody` / `*ResponseBody` 后缀及字段校验。模型仍属于提供方的 `*-api` 模块，BFF 自己的页面模型留在 BFF。
- 接口方法使用业务语言命名，并通过 JavaDoc 说明参数、结果、幂等和主要错误语义。
- 请求、响应和模型是 HTTP 传输契约，不暴露数据库映射对象、Repository 类型、内部聚合或第三方 SDK 类型。MQ 事件契约不因复用 DTO 而混入 HTTP API。
- 公开 HTTP 与发布的内部 `*-api` 顶层请求 DTO 使用 `*RequestBody`，顶层响应 DTO 使用 `*ResponseBody`。GET 查询 DTO 同样使用 `*RequestBody`，但命名不改变既有 Query/Path 传参，不要求 GET 携带 Body 或增加 `@RequestBody`。
- 嵌套业务模型及内部领域、应用层对象不强制该后缀，不为统一命名添加同义包装。新增和调整 HTTP DTO 时统一遵循规则；已发布 Java 类型重命名时同步更新提供方和调用方。详细规则以 `DEVELOPMENT_CONVENTIONS.md` 第 2 节为准。
- 对外发布且调用方需要理解的稳定 Code 枚举定义统一放在 `api/enums` 并实现 `CodeEnum`；提供方 Web 直接复用，不在领域包复制同义枚举。
- API DTO 字段仍使用 `String` 承载稳定枚举 Code，不声明 Java enum 字段；`api/enums` 是 Code 目录而不是 JSON 字段类型。仅服务内部使用的枚举放在 Web 的 `domain/enums`，边界转换使用 `getCode()`，消费方显式校验未知 Code。
- 契约变更先判断兼容性。新增可选字段优先，删除、改名或改变语义必须明确版本策略。
- 有界的内部同步查询默认直接返回完整 typed DTO。没有真实负载指标和已批准的兼容契约时，不预先增加 ETag、`If-None-Match`、`304`、Canonical Body Hash 或其他 HTTP 缓存协议。
- `api` 模块保持轻量，只引入 HTTP 契约、序列化和校验所需依赖；不引入数据库、Spring Boot 应用启动器、服务发现配置或业务实现。
- 不在 `api` 模块放通用工具、Repository、Mapper、领域服务、HTTP 客户端配置或异常堆栈实现。

典型接口：

```java
@HttpExchange(url = "/clients/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface ClientEligibilityQueryApi {

    /**
     * 查询 Client 交易资格
     *
     * @param clientId Client ID
     * @return 交易资格及不可交易原因
     */
    @GetExchange("/{clientId}/eligibility")
    ClientEligibilityResponseBody getEligibility(@PathVariable String clientId);
}
```

### 4.1 提供方使用方式

以下 Domain Service 与 Processor 规则适用于核心服务。`*-bff` 的职责是前端查询聚合和展示模型组装：简单转发可由 Controller 直接委托一个本地 Client Port，无需补空 Domain Service；真实多接口查询与页面组装由 BFF 的 `service` / Processor 编排，再通过 Client Port 访问上游，不下沉到核心服务。Controller 仍只做协议适配。

`*-web` 依赖自己的 `*-api`。位于 `ext/rest` 的 Controller 只做 HTTP 协议适配；符合简单用例规则时可直接委托一个 Domain Service，否则委托 `service` 中的 Processor：

```java
@RestController
@AllArgsConstructor
public class ClientEligibilityController implements ClientEligibilityQueryApi {
    private final ClientEligibilityService clientEligibilityService;

    @Override
    public ClientEligibilityResponseBody getEligibility(String clientId) {
        return clientEligibilityService.getEligibility(clientId);
    }
}
```

上例仅适用于方法少于 10 条有效可执行语句、只调用一个 Domain Service，并且没有业务判断、多步骤编排、写事务、幂等、补偿、跨系统协调或可复用应用编排的简单用例。空行、注释、花括号和格式换行不计入语句数；任一条件不满足时都必须使用 Processor。Controller 不包含事务、Repository/Mapper/SDK 调用或跨系统编排。

`@HttpExchange` 接口是内部 HTTP Method、Path、Header 和 DTO 签名的唯一来源。Controller 必须直接实现该接口，不得再次声明等价的 `@RequestMapping`、`@GetMapping` 或 `@PostMapping`；这些 MVC 注解只用于没有共享 Java API 契约的服务端入口。应用配置的 `context-path` 表达系统边界，接口注解只表达能力内路径，最终地址统一为 `{context-path}/{capability-path}`，不为每个服务机械增加 `/internal` 前缀。

HTTP 状态是调用方判断结果的第一依据：`2xx` 返回接口定义的正常 typed DTO，`4xx/5xx` 返回 `paytech-mvc-web-starter` 的统一直接错误体，至少包含 `code`、`message`、`requestId` 和 UTC `timestamp`。Controller 不为成功响应增加通用包装层，也不复制服务本地错误响应类型。

未预期的运行时异常直接向上传播，由共享 `GlobalExceptionHandler` 映射为 `500`。Controller、Processor 和 Domain Service 不使用宽泛 `catch (RuntimeException/Exception)` 只做重复日志、同义包装或统一改成 `503`；只有当前层能恢复、必须释放资源/更新异步状态，或承担明确不同的外部协议契约时才捕获，并把原始异常保留为 Cause。

### 4.2 调用方使用方式

调用方依赖提供方发布的 `*-api` JAR，通过 Spring HTTP Service Client 创建接口代理：

```text
调用方 Domain Service
  → 本地 *Client 端口
    → integration 中的 *ClientImpl
      → 注入的 @HttpExchange API 代理
        → RestClient/WebClient
          → Spring Cloud LoadBalancer
            → Nacos 中的服务实例
```

- 调用方不重复定义远程 HTTP DTO，也不重新声明 URL。
- 远程 DTO 只停留在 integration/infrastructure 边界；转换为调用方自己的领域对象后再进入领域逻辑。
- 内部共享错误协议的 HTTP 代理由配置层通过 Foundation `HttpServiceClientFactory` 创建并注册为单例 Bean，业务 Client 直接注入调用。公共异常转换只作用于这些显式创建的代理；业务类不重复包装 HTTP 状态、网络与错误体处理，特殊恢复或业务转换仍由拥有该语义的层负责。
- `@HttpExchange` 本身只定义 HTTP 契约。Nacos 负责服务发现，Spring Cloud LoadBalancer 负责实例选择，底层 `RestClient` 或 `WebClient` 通过部署网络限制的私有 HTTP 发送请求；Java 服务不持有 X.509 应用证书。
- 超时、统一错误映射、追踪和熔断在调用方基础配置或适配器中处理；命令请求不得在缺少幂等保障时自动重试。
- 内部服务调用不经过 API Gateway。

### 4.3 OpenAPI 与外部接口

- 内部全 Java 服务以手写 `@HttpExchange` 契约和共享 `*-api` JAR 为默认，使用 springdoc/构建检查输出或校验 OpenAPI 文档。
- Gateway 暴露的 Client API、Checkout API、Provider Callback 和未来第三方 API 必须保持语言无关的 HTTP/OpenAPI 契约，不要求外部消费者依赖 Java JAR。
- 内部 API 与外部 API 使用不同路径、权限和契约包，不能因为 DTO 相似而直接暴露内部接口。
- 新项目不新增 Dubbo 或 OpenFeign 契约；历史系统迁移必须另行设计兼容期。

## 5. Web 模块分层

推荐包结构：

```text
<base-package>/
├── Application.java
├── configuration/                  # 本服务运行配置
├── ext/
│   ├── job/                         # XXL-Job 等外部调度入口
│   ├── dubbo/                       # 仅既有或迁移期 Dubbo Provider 入口
│   ├── rest/                        # Controller 与 REST 入站适配器
│   └── mq/                          # 仅存在真实 MQ 入站能力时创建
├── service/
│   └── processor/                   # 按需创建：复杂用例编排、校验、事务边界
├── integration/
│   ├── *Client.java                 # 出站端口
│   └── impl/                        # Provider/RPC/HTTP 适配实现
├── domain/                          # 聚合、值对象和纯领域模型
│   └── enums/                       # 仅服务内部使用的业务/持久化枚举
├── domainservice/
│   ├── service/                     # 领域服务接口及 impl
│   ├── repository/                  # 领域仓储接口及 impl
│   ├── process/                     # 领域流程/策略（确有需要时）
│   └── convert/                     # 明确边界之间的转换
└── core/
    ├── common/                      # 本服务共享类型
    ├── constant/                    # 稳定常量
    └── dal/
        ├── dataobject/              # 数据库映射对象（类型名不加 DO 后缀）
        ├── mapper/                  # MyBatis Mapper
        └── configuration/           # 数据访问配置
```

目录代表职责，不代表必须创建。没有对应能力时不要创建空目录。

`ext` 和 `service` 必须保持平级。外部机制触发本服务工作时，入口适配器按协议放入 `ext/<protocol>`，只完成协议解析、入口校验、调用上下文转换和服务委托；需要编排的内部用例留在 `service`。`ext/job`、`ext/dubbo`、`ext/rest` 是当前标准目录，MQ Listener 在存在实际消费能力时使用 `ext/mq`。出站调用不属于外部触发入口，统一放在平级 `integration` 包。

`ext/dubbo` 只规定已有或迁移期兼容入口的包位置，不授权新项目引入 Dubbo。Code-MoR 内部同步服务调用仍使用轻量 `*-api` 与 Spring `@HttpExchange`，新项目不得新增 Dubbo 或 OpenFeign 契约。

### 5.1 HTTP 入站适配器

`ext/rest` 中的 Controller 实现 `*-api` 中按能力拆分的 `@HttpExchange` 接口。符合下节简单用例规则时直接委托一个 Domain Service，否则委托一个明确的 Processor。统一参数校验、错误响应、请求 ID 和鉴权上下文由 HTTP 基础设施处理，Controller 不复制业务规则。

### 5.2 Processor（按需）

`service/processor` 中的 Processor 负责需要独立应用层的参数校验和业务流程编排，不是每个入口都必须创建的转发类：

```text
validate request
  → 调用领域服务
  → 组合本系统内的业务步骤
  → 填充 response
```

- Controller 方法少于 10 条有效可执行语句，且只委托一个 Domain Service、没有业务判断、多步骤编排、写事务、幂等、补偿、跨系统协调或可复用应用编排时，可以省略 Processor。空行、注释、花括号和格式换行不计入语句数，不能通过调整格式规避边界。
- 任一条件不满足时，无论物理代码行数多少都必须使用 Processor。
- 复杂用例的默认事务边界位于 Processor；省略 Processor 的简单一致性读由 Domain Service 持有只读 `TransactionTemplate`。Controller 不持有事务边界。
- Processor 只能调用 Domain Service 或明确的应用端口，不得直接调用 Repository、Mapper 或外部 SDK。
- Processor 的请求和结果使用应用语义，不依赖 `ResponseEntity`、HTTP Header、Servlet 或客户端代理类型；协议参数在 Controller/Web 基础设施完成解析和转换。
- 一个命令只由一个核心系统拥有。BFF 和 Gateway 不承担该命令的业务编排。
- 方法使用卫语句，嵌套不超过三层，单个方法原则上不超过 30 行。
- 不为单个 Processor 创建抽象基类。只有至少两个真实 Processor 共享稳定模板流程和上下文字段时才提取 `Abstract*Processor` 或公共请求上下文。

参考 `transaction` 的 `AbstractProcessor` 同时注入了 Repository；这是参考项目现状，不是 Code-MoR 允许的依赖方式。

### 5.3 Domain Service

Domain Service 承载业务规则和跨聚合协调：

- 接口按业务能力命名，例如 `findTransactionReadyClient`；
- 实现依赖 Repository 和出站端口，不依赖 HTTP Controller 或 Processor；
- 结果明确、不可重试的业务拒绝使用 `FailException` 并传入本服务 `CodeMessageEnum`；中文消息包含实体标识、当前状态和操作上下文；
- 状态转换必须校验旧状态，防止静默覆盖并发更新。

### 5.4 Repository 与 Mapper

Repository 使用领域语言屏蔽 MyBatis 细节：

```text
Domain Service → Repository interface → RepositoryImpl → Mapper → 本系统数据库
```

- Repository 不包含跨系统业务规则。
- Mapper 只表达本系统数据库操作。
- 生产 SQL 只写在资源目录的 `*Mapper.xml`，接口保留方法签名和参数绑定；禁止 SQL 注解及 `*Provider` 注解。测试源码中的 TestMapper 可保留注解 SQL，具体边界见 [DEVELOPMENT_CONVENTIONS.md](./DEVELOPMENT_CONVENTIONS.md) 的“MyBatis SQL 存放规则”。
- 每个核心系统只能访问自己的数据库，禁止跨库查询、跨库写入或共享表。
- 更新状态时携带期望旧状态或版本号；更新行数不为预期值时抛出包含上下文的异常。
- `dataobject` 中的数据库映射对象直接使用业务实体名，不追加 `DO` 后缀，也不得作为公共接口对象返回。
- 表、字段、索引、手工 DDL/DML 与 DataObject/Mapper 的设计必须和项目 PDManer 模型同步；详细规则见 `DATABASE_DESIGN_GUIDE.md`。

### 5.5 外部集成

外部 Provider、RPC、HTTP 和第三方 SDK 必须封装在平级 `integration` 包，不能放入表示入站触发的 `ext`：

- 业务层依赖本地 `*Client` 或端口接口，不直接依赖其他服务的 HTTP API 或第三方 SDK。
- `*ClientImpl` 可以依赖其他服务的 `*-api` JAR，但必须把远程 DTO 转换为本地领域语义。
- 实现层负责超时、错误映射、脱敏日志和外部状态转换。
- 外部原始状态先转换为本系统统一语义，再进入领域逻辑。
- 不把外部响应对象穿透到领域层。
- Vault 作为内部 HTTP 集成放在同一边界：本地端口方法必须显式表达 `purpose` 和最小字段集合，适配器通过部署网络限制的私有 HTTP 携带当前 V1 必需但可伪造的 `X-Caller-Service`、Reason 与业务资源上下文，并接受静态 Allowlist 只能防止误用而不能认证调用方的限制。Reveal 结果不得写数据库、缓存、事件或日志；业务库只持久化 Token、版本、元数据 Hash、必要 Blind Index、同前缀 Blind Index Key Version 和 Masked Value。

### 5.6 MQ 与业务事件

- 存在真实 MQ 入站能力时，`ext/mq` 负责验签/反序列化之后的事件接收、幂等入口和调用领域能力。
- 业务状态、状态历史和 `compensation_event` 在 Processor 本地事务内原子提交；MQ 发布只能由事务提交后的执行器完成，不得出现数据库回滚但消息已发送。
- 正常路径通过 `afterCommit` 唤醒本地 Dispatcher；XXL-Job 只做兜底扫描、分片或串行隔离，数据库 Claim/Lease/Version 仍是多实例互斥边界。
- 发布重试复用相同 `event_key`、来源版本、Schema、Payload 和 Hash。Broker Confirm 后成功记录允许删除；失败达到上限必须保留、告警并支持带 Actor/Reason/Ticket 的人工重开。
- MVP 不建立通用 Inbox/Outbox。消费者在本地业务事务中用业务唯一键、`source_event_key`、Payload Hash、来源版本条件更新和状态机支持重复、延迟和乱序消息，并在事务提交后 Ack。
- 事件 Payload 不得包含敏感明文或 Vault 内部密文，只能携带必要业务字段、`file_id + content_hash`、必要 Masked Value，或由 Vault `CopyForBinding` 为准确接收系统/资源新建的接收方专属 Token Tuple；不得传播来源系统自己的 Token。
- Finance 的账务事实不可变；更正使用补偿事实和追加分录。

完整的表结构、执行状态、并发和测试要求见 `COMPENSATION_EVENT_GUIDE.md`。

## 6. 依赖方向

允许的主路径：

```text
简单用例：
ext/* 入站适配器
  → 一个 Domain Service / Domain
    → Repository interface / Integration port
      → RepositoryImpl / IntegrationImpl
        → Mapper / 外部系统

复杂用例：
ext/* 入站适配器
  → Processor
    → Domain Service / Domain
      → Repository interface / Integration port
        → RepositoryImpl / IntegrationImpl
          → Mapper / 外部系统
```

禁止：

- HTTP Controller 直接访问 Repository 或 Mapper；
- Processor 直接访问 Mapper 或另一个系统数据库；
- Repository 调用 Domain Service；
- Domain Service 依赖入站协议对象；
- BFF、Gateway 或入口应用拥有核心业务事实；
- 为复用方便让两个服务共享数据库或可变数据库映射对象。

## 7. Spring 与代码风格

- 六个核心服务、BFF 和 Vault 默认使用 Spring MVC；API Gateway 使用 Spring Cloud Gateway Server WebFlux，不为了统一形式改成 MVC。
- MVC 服务的内部同步 HTTP 调用默认使用 `RestClient`；只有真实 Reactive/Streaming 边界才使用 `WebClient`。
- 默认不全局启用虚拟线程；必须先验证连接池、Listener、第三方 SDK、MDC 和背压行为。
- 优先构造器注入并使用 `final` 字段；纯字段赋值构造器使用 Lombok `@AllArgsConstructor`，包含校验、防御性复制、派生对象创建或资源配置的构造器保留显式实现；禁止新增字段注入。
- Service 使用 `@Service`，Repository 实现使用 `@Repository`，无更精确语义的组件才使用 `@Component`。
- 数据类可使用 Lombok `@Data`，但领域对象应谨慎暴露无约束 setter。
- 接口、公共类型和关键业务方法写清晰 JavaDoc。
- 常量和枚举使用业务语义名称，不使用 `getData`、`handle`、`process2` 等模糊名称。
- 金额使用整数最小货币单位或项目批准的 Money 类型，并显式携带 ISO 4217 币种；禁止浮点金额。
- 时间、时区、ID 和序列生成由基础能力统一提供，不在业务代码中散落静态随机实现。
- 先实现当前获批用例需要的最小类和配置；不要用一行代理、空抽象或未来扩展点代替清晰的直接调用。只有实际复杂度达到第 5.2 节门槛时才增加 Processor 或应用层抽象。

## 8. 资源目录

典型资源：

```text
src/main/resources/
├── application.yml
├── logback.xml
└── META-INF/
    ├── mybatis-config.xml
    └── sqlmap/
        └── *Mapper.xml
```

- 配置文件不得写入真实密码、Token 或内部环境默认凭据。
- 环境差异通过配置中心或环境变量注入。
- Spring Cloud Alibaba 2025.1 使用 `application.yml` 的 `spring.config.import` 导入 Nacos 配置，不创建 `bootstrap.yml`。
- MyBatis XML 的 namespace、方法名、参数名和结果映射必须与 Mapper 一致。
- 不复制参考项目的 Spring Cloud Config、Dubbo、MySQL 或插件版本；所有版本和客户端边界必须遵守 `TECHNOLOGY_STACK.md`。

## 9. 新增功能检查清单

Agent 完成 Java 功能前逐项检查：

- [ ] 业务事实属于当前系统，而不是其他五个核心系统。
- [ ] 公共契约和内部实现没有混在同一模块。
- [ ] `*-api` 只包含轻量 HTTP 契约，没有业务实现或持久化依赖。
- [ ] API DTO 的枚举语义使用 `String code`；对外 Code 枚举定义位于 `*-api/api/enums`，内部枚举位于 `*-web/domain/enums`，均实现 `CodeEnum`，错误枚举按需实现 `CodeMessageEnum`，且没有复制同义 Code 目录。
- [ ] HTTP DTO 按业务能力放入独立 `*Models`，API 接口不内嵌 DTO；顶层类型使用 `*RequestBody` / `*ResponseBody`，GET 参数位置保持，嵌套模型和内部领域对象不机械追加后缀。
- [ ] Controller 实现 `@HttpExchange` API、没有重复 MVC Mapping；简单用例只委托一个 Domain Service，其他用例委托 Processor。
- [ ] 外部触发入口位于与 `service` 平级的 `ext/job`、`ext/dubbo`、`ext/rest` 或实际协议子包，且没有承载业务逻辑。
- [ ] 调用方通过本地 `*Client` 适配器隔离远程 API DTO，没有让远程 DTO进入领域层。
- [ ] 未新增 Dubbo 或 OpenFeign 契约，内部调用未绕行 Gateway。
- [ ] 未创建仅做一行转发的 Processor；省略 Processor 时满足少于 10 条有效语句及全部简单用例条件，且 Controller 没有持有事务或访问 Repository、Mapper、SDK。
- [ ] 实际存在的 Processor 未直接调用 Repository、Mapper 或第三方 SDK。
- [ ] 实际存在的 Processor 未依赖 HTTP 类型，也没有为单一实现创建空泛的抽象基类或公共上下文。
- [ ] Domain Service 与 Repository 使用业务语言命名。
- [ ] 只访问本系统数据库。
- [ ] DataObject 类型名没有 `DO` 后缀；数据库结构已使用项目统一 PDManer 模板设计，并与手工 DDL/DML、DataObject、Mapper 和 Repository 保持一致。
- [ ] 事务覆盖本地原子操作，事件在提交后可靠发布。
- [ ] 状态更新校验旧状态/版本并处理并发冲突。
- [ ] 异常信息包含必要业务上下文。
- [ ] 未预期异常交给共享全局处理器；局部 `catch` 具有实际恢复、清理、异步状态或协议转换职责，并保留原始 Cause。
- [ ] 没有为尚未批准的技术方案创建占位基础设施。
- [ ] 没有一行代理、空层或猜测性公共类；新增 Foundation 能力已有跨服务复用证据且职责单一。
- [ ] 已按 `UNIT_TEST_GUIDE.md` 补充适当层级的测试。
- [ ] 新增出站依赖使用已启动的仓库 `local-environment` 或有状态 Simulator，未通过方法级 Stub 绕过真实适配器。
