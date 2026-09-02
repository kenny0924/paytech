# Java 项目结构规范

最后更新：2026-09-02

## 1. 使用范围

当任务涉及以下任一内容时，Agent 必须在修改代码前完整读取本文：

- 创建或调整 Java 服务和 Maven 模块；
- 新增 HTTP API、请求响应、Processor、Domain Service、Repository、Mapper；
- 新增外部系统客户端、MQ 生产者或消费者；
- 集成 Vault、`compensation-event` 或 XXL-Job 异步恢复任务；
- 调整包结构、依赖方向、事务边界或部署单元。

本文定义未来六个核心 Java 服务及内部 Vault 的默认工程形态。`api-gateway`、`operations-console-bff`、`client-console-bff` 和 `partner-console-bff` 的业务边界仍以 `APPLICATION_ARCHITECTURE.md` 为准；若它们不需要完整领域分层，不得为了形式一致强行创建空层。Gateway 首期还必须遵守 `plans/2026-08-29-operations-api-gateway-design.md`，只创建 `api-gateway-web` 和 `api-gateway-test`，不创建空 `*-api`。涉及可靠异步事件或敏感数据时，还必须分别读取 `COMPENSATION_EVENT_GUIDE.md` 和 `VAULT_SERVICE_ARCHITECTURE.md`。

Java、Spring、Maven BOM/Parent、MySQL、RabbitMQ、Redis、Nacos、Gateway 和可观测性版本统一以 [TECHNOLOGY_STACK.md](./TECHNOLOGY_STACK.md) 为准。本机运行方式以 [LOCAL_DEVELOPMENT_ENVIRONMENT.md](./LOCAL_DEVELOPMENT_ENVIRONMENT.md) 为准。

Operations 首期 `api-gateway` 已按批准边界实现；其最终路由、协议、Guard、JWKS 与传输约束以 [`2026-08-30-operations-gateway-hardening-design.md`](../docs/superpowers/specs/2026-08-30-operations-gateway-hardening-design.md) 为准。Platform Support 已按用户明确授权初始化非业务应用基础，边界见第 3.3 节。除此之外，本阶段仍禁止主动为核心服务业务能力、Console BFF/前端或 Vault 创建脚手架；只有用户明确要求开始实现具体 Java 项目或垂直切片时，才能应用本文创建目录或代码。

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
        └── resources/                   # auto/manual 配置、migration 和 fixture
```

模块职责：

| 模块 | 职责 | 发布/运行 |
|---|---|---|
| 父工程 | 聚合模块、统一版本、插件和依赖管理 | `pom`，不运行 |
| `api` | 内部 HTTP 契约：`@HttpExchange` 接口、传输 DTO 和契约枚举 | 可发布 JAR，不运行 |
| `web` | Spring Boot 入口、业务实现、持久化和适配器 | 唯一业务部署单元 |
| `test` | `auto/manual` 测试、测试数据 DSL、所需 Testcontainers 和外部系统 Simulator | 不发布、不部署 |

默认依赖方向：

```text
test ───────→ web ───────→ api

其他服务 ───────────────→ api
```

- `api` 不得依赖 `web` 或 `test`。
- `web` 依赖自己的 `api`，由 HTTP Controller 实现契约接口。
- `test` 可以依赖 `web` 以运行集成测试，但生产模块不得依赖 `test`。
- `auto` 必须从真实本服务入口完成内部闭环；数据库、Redis 和 MQ 原则上由测试按需自动启动，外部 HTTP/MQ 通过有状态 Simulator 和真实协议交互。Operations Gateway 的仓库 Compose Redis 例外以本节 3.2 和 `UNIT_TEST_GUIDE.md` 为准。
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

公共能力按边界拆分为 `paytech-common-core`、`paytech-mvc-web-starter`、`paytech-snowflake-id`、`paytech-caffeine-starter`、`paytech-redis-starter`、`paytech-xxl-job-starter` 和 `paytech-compensation-event`。Redis Starter 暴露同一实例的 `cacheManager`/`redisCacheManager` 并以 `@Primary` 作为 Spring Cache 默认 Manager；每个 Redis Cache 由宿主声明 `RedisCacheDefinition<T>(cacheName, concreteValueType, ttl)`，未声明名称不动态创建。Caffeine 只注册 `caffeineCacheManager`，仅用于可回源的有界进程内加速；同时使用两者时，Caffeine 方法必须在 Spring Cache 注解显式选择该 Manager，且两边使用不同 Cache Name。两者不是 L1/L2，也不使用 `spring.cache.type` 切换；不需要 Caffeine 的宿主不要引入对应 Starter。不得让任何 Cache 承担权威事实、幂等、锁、跨实例一致性、Vault Reveal、凭据或敏感明文，也不得把跨领域 DTO、业务枚举、Repository、Mapper 或第三方 Provider 模型放入公共 JAR。详细职责见 `TECHNOLOGY_STACK.md`。

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
- `api-gateway-test` 以 Compile Scope 依赖 `web`，从真实随机端口 Gateway 入口经过完整生产链，并通过真实协议连接 HTTPS JWKS 和有状态 Operations BFF Simulator。它不引入 Mockito/Testcontainers；Redis 使用唯一已批准例外，只连接已运行的 `127.0.0.1:16379` 仓库 Compose Redis，只接受 `PAYTECH_REDIS_PASSWORD`，每次运行使用唯一 Namespace 并只以 `SCAN` 清理；测试不启动 Docker/Testcontainers，不连接开发者 `6379` Redis。
- 默认 `auto` 包含架构和真实 HTTP E2E 验收。`./mvnw clean verify` 之后还必须独立运行两个 Node 门禁；JaCoCo 对未舍入 Instruction/Branch 同时要求 `>= 90%`，只排除精确的 `ApiGatewayApplication`，并校验新鲜度与编译类一致性。
- `api-gateway-web` 产出普通 JAR 和可执行 `-exec.jar`；`api-gateway-test` 跳过 install/deploy 且不产出可部署 JAR。Application/Management 默认端口分别为 `18080`/`19080`，健康组成与运行命令见 [`api-gateway/README.md`](../api-gateway/README.md)。

### 3.3 Platform Support Phase A 运行切片

Platform Support 当前实际仓库结构是：

```text
platform-support-service/
├── pom.xml
├── platform-support-api/               # PLT-I20 @HttpExchange 与不可变 typed DTO
├── platform-support-web/               # MVC、Flyway/MyBatis、初始化与 Snapshot Provider
├── platform-support-test/              # MySQL、HTTP、mTLS、架构与覆盖率 auto 门禁
├── docs/database/                       # 已批准的完整 PDManer 设计输入
└── README.md
```

当前生产/测试依赖方向严格为：

```text
platform-support-test ────→ platform-support-web ────→ platform-support-api
```

- `platform-support-api` 当前只发布有真实 Gateway 消费者的 PLT-I20 `@HttpExchange` 查询和不可变 DTO；成功调用每次返回完整 typed Snapshot，不使用 ETag/304。
- `platform-support-web` 实现 Spring MVC/Actuator、Phase A Flyway/MyBatis 21-table 投影、确定性 Seed、mTLS Workload 授权和 Snapshot 读取/校验/组装；它不实现 Gateway 运行时消费。
- `platform-support-test` 通过 Testcontainers MySQL、真实随机端口 HTTP/mTLS、生产配置与架构测试验证完整生产链，不使用 Mockito、共享数据库、Redis、MQ 或 Nacos。
- Reactor Enforcer 允许该切片所需的 JDBC/MySQL/Flyway/MyBatis/Security，仍禁止 Nacos、Redis、MQ、XXL-Job、Gateway、Compensation Event 与 Mockito。
- Web 默认监听 `18106`，Actuator 使用同一 Listener；测试模块跳过 install/deploy，Web 同时产出普通 JAR 和可执行 `-exec.jar`。
- 当前切片不实现认证/Tenant IAM 命令、API/Route/Guard 管理、限流运行态、Gateway Snapshot 消费、补偿事件或部署配置。任何此类能力都必须作为获批垂直切片按 TDD 增量加入。
- 详细设计、实施步骤和运行方式分别见[基础设计](../docs/superpowers/specs/2026-09-01-platform-support-application-foundation-design.md)、[实施计划](../docs/superpowers/plans/2026-09-01-platform-support-application-foundation.md)和 [`platform-support-service/README.md`](../platform-support-service/README.md)。

## 4. API 模块与内部 HTTP 契约

推荐结构：

```text
api/
├── internal/
│   ├── *CommandApi.java
│   └── *QueryApi.java
├── request/
│   └── *Request.java
├── response/
│   └── *Response.java
├── model/
│   └── *Info.java
└── enums/
    └── *Status.java
```

规则：

- 内部同步接口使用 Spring `@HttpExchange`，具体方法使用 `@GetExchange`、`@PostExchange` 等明确 HTTP 注解。
- API 按业务能力拆分，例如 `ClientEligibilityQueryApi`、`AgreementCommandApi`；禁止创建包含所有接口的巨型 `ClientContractApi`。
- 接口方法使用业务语言命名，并通过 JavaDoc 说明参数、结果、幂等和主要错误语义。
- 请求、响应和模型是 HTTP 传输契约，不暴露数据库映射对象、Repository 类型、内部聚合或第三方 SDK 类型。MQ 事件契约不因复用 DTO 而混入 HTTP API。
- API DTO 使用 `String` 承载稳定枚举 Code，不声明 Java enum 字段；内部枚举实现 `CodeEnum`，边界转换必须使用 `getCode()`，消费方显式校验未知 Code。
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
    ClientEligibilityResponse getEligibility(@PathVariable String clientId);
}
```

### 4.1 提供方使用方式

`*-web` 依赖自己的 `*-api`，位于 `ext/rest` 的 Controller 只做 HTTP 协议适配并委托 `service` 中的 Processor：

```java
@RestController
@AllArgsConstructor
public class ClientEligibilityController implements ClientEligibilityQueryApi {
    private final QueryClientEligibilityProcessor queryClientEligibilityProcessor;

    @Override
    public ClientEligibilityResponse getEligibility(String clientId) {
        return queryClientEligibilityProcessor.process(clientId);
    }
}
```

Controller 不包含业务判断、事务、Repository 调用或跨系统编排。

`@HttpExchange` 接口是内部 HTTP Method、Path、Header 和 DTO 签名的唯一来源。Controller 必须直接实现该接口，不得再次声明等价的 `@RequestMapping`、`@GetMapping` 或 `@PostMapping`；这些 MVC 注解只用于没有共享 Java API 契约的服务端入口。应用配置的 `context-path` 表达系统边界，接口注解只表达能力内路径，最终地址统一为 `{context-path}/{capability-path}`，不为每个服务机械增加 `/internal` 前缀。

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
- `@HttpExchange` 本身只定义 HTTP 契约。Nacos 负责服务发现，Spring Cloud LoadBalancer 负责实例选择，底层 `RestClient` 或 `WebClient` 负责发送请求。
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
│   └── processor/                   # 用例编排、校验、事务边界
├── integration/
│   ├── *Client.java                 # 出站端口
│   └── impl/                        # Provider/RPC/HTTP 适配实现
├── domain/                          # 聚合、值对象和纯领域模型
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

`ext` 和 `service` 必须保持平级。外部机制触发本服务工作时，入口适配器按协议放入 `ext/<protocol>`，只完成协议解析、入口校验、调用上下文转换和服务委托；内部用例编排留在 `service`。`ext/job`、`ext/dubbo`、`ext/rest` 是当前标准目录，MQ Listener 在存在实际消费能力时使用 `ext/mq`。出站调用不属于外部触发入口，统一放在平级 `integration` 包。

`ext/dubbo` 只规定已有或迁移期兼容入口的包位置，不授权新项目引入 Dubbo。Code-MoR 内部同步服务调用仍使用轻量 `*-api` 与 Spring `@HttpExchange`，新项目不得新增 Dubbo 或 OpenFeign 契约。

### 5.1 HTTP 入站适配器

`ext/rest` 中的 Controller 实现 `*-api` 中按能力拆分的 `@HttpExchange` 接口，并委托一个明确的 Processor。统一参数校验、错误响应、请求 ID 和鉴权上下文由 HTTP 基础设施处理，Controller 不复制业务规则。

### 5.2 Processor

`service/processor` 中的 Processor 负责一个用例的参数校验和业务流程编排：

```text
validate request
  → 调用领域服务
  → 组合本系统内的业务步骤
  → 填充 response
```

- 默认事务边界位于 Processor。
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
- 业务异常使用 `BizCheckFailException`，中文消息包含实体标识、当前状态和操作上下文；
- 状态转换必须校验旧状态，防止静默覆盖并发更新。

### 5.4 Repository 与 Mapper

Repository 使用领域语言屏蔽 MyBatis 细节：

```text
Domain Service → Repository interface → RepositoryImpl → Mapper → 本系统数据库
```

- Repository 不包含跨系统业务规则。
- Mapper 只表达本系统数据库操作。
- 每个核心系统只能访问自己的数据库，禁止跨库查询、跨库写入或共享表。
- 更新状态时携带期望旧状态或版本号；更新行数不为预期值时抛出包含上下文的异常。
- `dataobject` 中的数据库映射对象直接使用业务实体名，不追加 `DO` 后缀，也不得作为公共接口对象返回。
- 表、字段、索引、migration 与 DataObject/Mapper 的设计必须和项目 PDManer 模型同步；详细规则见 `DATABASE_DESIGN_GUIDE.md`。

### 5.5 外部集成

外部 Provider、RPC、HTTP 和第三方 SDK 必须封装在平级 `integration` 包，不能放入表示入站触发的 `ext`：

- 业务层依赖本地 `*Client` 或端口接口，不直接依赖其他服务的 HTTP API 或第三方 SDK。
- `*ClientImpl` 可以依赖其他服务的 `*-api` JAR，但必须把远程 DTO 转换为本地领域语义。
- 实现层负责超时、错误映射、脱敏日志和外部状态转换。
- 外部原始状态先转换为本系统统一语义，再进入领域逻辑。
- 不把外部响应对象穿透到领域层。
- Vault 作为内部 HTTP 集成放在同一边界：本地端口方法必须显式表达 `purpose` 和最小字段集合，适配器携带 mTLS/Workload Identity、Reason 与业务资源上下文。Reveal 结果不得写数据库、缓存、事件或日志；业务库只持久化 Token、版本、元数据 Hash、必要 Blind Index、同前缀 Blind Index Key Version 和 Masked Value。

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
- [ ] API DTO 的枚举语义使用 `String code`，内部枚举实现 `CodeEnum`，错误枚举按需实现 `CodeMessageEnum`。
- [ ] Controller 实现 `@HttpExchange` API、没有重复 MVC Mapping，并仅委托 Processor。
- [ ] 外部触发入口位于与 `service` 平级的 `ext/job`、`ext/dubbo`、`ext/rest` 或实际协议子包，且没有承载业务逻辑。
- [ ] 调用方通过本地 `*Client` 适配器隔离远程 API DTO，没有让远程 DTO进入领域层。
- [ ] 未新增 Dubbo 或 OpenFeign 契约，内部调用未绕行 Gateway。
- [ ] Processor 未直接调用 Repository、Mapper 或第三方 SDK。
- [ ] Processor 未依赖 HTTP 类型，也没有为单一实现创建空泛的抽象基类或公共上下文。
- [ ] Domain Service 与 Repository 使用业务语言命名。
- [ ] 只访问本系统数据库。
- [ ] DataObject 类型名没有 `DO` 后缀；数据库结构已使用项目统一 PDManer 模板设计，并与 migration、DataObject、Mapper 和 Repository 保持一致。
- [ ] 事务覆盖本地原子操作，事件在提交后可靠发布。
- [ ] 状态更新校验旧状态/版本并处理并发冲突。
- [ ] 异常信息包含必要业务上下文。
- [ ] 没有为尚未批准的技术方案创建占位基础设施。
- [ ] 已按 `UNIT_TEST_GUIDE.md` 补充适当层级的测试。
- [ ] 新增出站依赖具有可自动启动的测试基础设施或有状态 Simulator，未通过方法级 Stub 绕过真实适配器。
