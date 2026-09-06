# Code-MoR 技术栈基线

最后更新：2026-09-05

状态：Confirmed technology baseline。本文只定义产品、版本与技术边界；当前组件的实现、验证和部署状态统一见 [`CURRENT_IMPLEMENTATION.md`](./CURRENT_IMPLEMENTATION.md)。

## 1. 使用范围

当任务涉及 Java 版本、Spring 版本、Maven 依赖管理、数据库、手工 SQL、MQ、Redis、Gateway、配置中心、补偿调度、可观测性或本地运行环境时，必须先读取本文。具体工程分层、可靠事件、测试和本地搭建仍分别以以下文档为准。Operations Gateway 的路由、协议、Guard、JWT/JWKS 校验界限与运行保护继续以 [`2026-08-30-operations-gateway-hardening-design.md`](../docs/superpowers/specs/2026-08-30-operations-gateway-hardening-design.md) 为准；其应用传输和 Client IP 信任来源已由 [`2026-09-02-mvc-web-logging-and-internal-http-simplification-design.md`](../docs/superpowers/specs/2026-09-02-mvc-web-logging-and-internal-http-simplification-design.md) 取代：

- [JAVA_PROJECT_STRUCTURE.md](./JAVA_PROJECT_STRUCTURE.md)
- [COMPENSATION_EVENT_GUIDE.md](./COMPENSATION_EVENT_GUIDE.md)
- [UNIT_TEST_GUIDE.md](./UNIT_TEST_GUIDE.md)
- [LOCAL_DEVELOPMENT_ENVIRONMENT.md](./LOCAL_DEVELOPMENT_ENVIRONMENT.md)

版本基线不改变六个核心系统、Gateway/BFF、Vault、数据库所有权和跨系统交互边界。

## 2. 版本策略

Code-MoR 不追逐刚发布的最新功能线。版本选择遵循以下优先级：

1. 选择 LTS、仍受维护的稳定系列，或与已选托管服务明确对齐的成熟系列；
2. Spring Boot、Spring Cloud 和 Spring Cloud Alibaba 必须使用官方兼容组合；
3. 在稳定系列内采用已包含必要缺陷与安全修复的补丁版本；
4. Java 依赖由统一 BOM 管理，服务不得自行覆盖基础框架版本；
5. 容器镜像固定明确版本，不使用 `latest`；创建实际 Compose 或生产部署前再核对镜像摘要和安全公告；
6. 升级 Spring Boot/Cloud、Nacos、MySQL、RabbitMQ 或 Redis 的主/次版本必须单独评审并完成兼容性测试。

## 3. 已确认技术矩阵

| 范围 | 选择 | 当前基线 | 约束 |
|---|---|---|---|
| Java | Oracle/OpenJDK Java | JDK 25 LTS | 编译、测试和运行统一使用 Java 25；不使用 Java 24 |
| 构建工具 | Apache Maven Wrapper | Maven 3.9.16 | 每个独立 Maven 仓库携带自己的 Wrapper；不采用 Maven 4 RC |
| 本机制品库 | Nexus Repository Community | 3.81.1 | 复用当前电脑已有实例；只承担本地依赖代理和 Snapshot/Release 发布，不代表生产选型 |
| Web/应用框架 | Spring Boot | 4.0.8 | 选择已有多个补丁的 4.0 系列，不追 Spring Boot 4.1 |
| 微服务版本线 | Spring Cloud | 2025.1.3 | 官方基于 Spring Boot 4.0.8 的 Release Train |
| Nacos 集成 | Spring Cloud Alibaba | 2025.1.0.0 | 使用官方 Spring Boot 4.0/Spring Cloud 2025.1 兼容线 |
| 注册/配置客户端 | Nacos Client | 3.1.1 | 由 Spring Cloud Alibaba BOM 管理，不由服务单独指定 |
| 注册/配置服务端 | Nacos Server | 3.1.2 | 暂不进入刚发布的 3.2 系列 |
| Java 同步 Web 栈 | Spring MVC | Spring Boot 管理 | 六个核心 Java 服务与 BFF 默认使用 MVC |
| Vault V1 Runtime | Go Server + Java `@HttpExchange` Client | Go 1.27.1；Java 25 | Go Server 是正式 V1 实现；当前只支持三表 Protect/Reveal，Java Client 供内部服务调用 |
| Gateway Web 栈 | Spring Cloud Gateway Server WebFlux | 5.0.3；Spring Cloud 2025.1.3 线 | Gateway 是 WebFlux 例外，不强行改成 MVC |
| Gateway 资源服务器 | Spring Security OAuth2 Resource Server JWT | Spring Boot 4.0.8 BOM 管理 | Gateway 只验证 Access JWT 和 Channel/Actor 上下文，不实现账号密码登录 |
| 内部同步调用 | Spring HTTP Service Client | Spring Framework 管理 | `@HttpExchange` + `RestClient` + LoadBalancer + Nacos；使用部署网络限制的私有 HTTP，不新增 Dubbo/OpenFeign 或应用证书 |
| 数据访问 | MyBatis Spring Boot Starter | 4.0.1 | 与 Spring Boot 4.0 线对齐；Mapper XML 为默认复杂 SQL 载体 |
| MyBatis Core | MyBatis | 3.5.19 | 由统一 BOM 管理；公共 TypeHandler 位于无 Spring 自动配置的 `paytech-mybatis-support` |
| 数据库 | MySQL LTS | 8.4；本地固定 8.4.11 | 早期允许一个实例承载多数据库，但每系统独立数据库和账号 |
| Schema 演进 | 人工审查并执行的 SQL | 每服务一份当前 DDL + 一份初始化 DML | PDManer 是设计源；应用启动不建表、不迁移、不灌初始数据 |
| MQ | RabbitMQ | 4.2；本地固定 4.2.9 | 对齐 Amazon MQ RabbitMQ 4.2；自建生产前重新选择当时受支持系列 |
| MQ 序列化 | JSON UTF-8 | 统一事件信封 | 禁止 Java 原生序列化；事件契约必须版本化 |
| Redis Server | Redis Open Source Extended | 8.2；本地固定 8.2.1 | 早期单机；不是业务权威数据源 |
| 进程内缓存 | Caffeine | 3.2.4；Spring Boot 4.0.8 BOM 管理 | 只加速可安全回源的数据；与 Redis CacheManager 按方法显式选用，不组成 L1/L2 |
| 通用 Redis 客户端 | Spring Data Redis + Lettuce | Spring Boot BOM 管理 | Redis Starter 默认提供 Spring Cache、JSON 对象访问和字符串原子操作；Gateway 单独使用 Reactive Redis/Lua |
| 分布式协调客户端 | Redisson Community | 4.6.1 | 仅用于锁、可过期信号量等明确协调能力 |
| 补偿调度 | XXL-Job | 3.4.2 | 只唤醒/分片服务内扫描；数据库 Claim/Lease 才是最终互斥边界 |
| Gateway 限流与并发 | Spring Data Redis Reactive + Lettuce Lua | Spring Boot BOM 管理 | Operations 首期用原子多维 Token Bucket 与 ZSET Lease；不使用阻塞客户端、Redisson 或 Foundation Redis Starter |
| 可观测性 API | Actuator + Micrometer Observation | Spring Boot BOM 管理 | 业务代码只依赖 Micrometer/SLF4J 等中立 API |
| 自动采集 | OpenTelemetry Java Agent + OTLP | 创建运行环境时固定补丁版本 | 默认通过 Java Agent 自动采集，不在业务代码绑定后端 SDK |
| 遥测管道 | OpenTelemetry Collector | 创建运行环境时固定镜像版本 | 统一接收和转发 Metrics/Logs/Traces |
| 本地可观测后端 | Prometheus + Loki + Tempo + Grafana + Alertmanager | 创建 Compose 时固定镜像版本 | 按需 Profile 启动，不作为默认开发前置条件 |
| Java 测试 | JUnit 5 + 仓库 `local-environment` | BOM 管理 | 默认 `auto` 经过真实生产链；MySQL/Redis/RabbitMQ 均复用本地固定版本服务并做受限清理 |

OpenTelemetry、Prometheus、Loki、Tempo、Grafana 和 Alertmanager 的产品组合已经确认。它们发布频繁，实际创建 Compose 时固定经过同一次兼容验证的明确补丁版本和镜像摘要，而不是在架构文档中永久冻结一个很快失效的补丁号。

## 4. Java 与虚拟线程

- 使用 Java 25 LTS。Java 24 是非 LTS，不作为生产基线。
- JDK 24 已通过 JEP 491 解决大量 `synchronized` 场景下虚拟线程被固定到平台线程的问题；Java 25 包含该改进。
- V1 不全局启用 `spring.threads.virtual.enabled`。数据库连接池、RabbitMQ Listener、第三方 SDK、限流和 ThreadLocal/MDC 行为必须先完成服务级压测与观测。
- 可以为明确的阻塞 I/O 用例做独立实验，但不得用虚拟线程取消数据库连接数、外部并发和背压上限。

## 5. 统一 Maven BOM 与 Parent

### 5.1 BOM 的含义

BOM（Bill of Materials）是 `packaging=pom` 的版本清单。它只在 `dependencyManagement` 中对齐依赖版本：

- 不包含 Java 代码；
- 不会因为被导入就自动把依赖加入服务；
- 服务仍需在 `dependencies` 中声明自己实际使用的 Starter/JAR；
- 主要作用是防止六个服务各自选择不同的 Spring、MyBatis、Redisson 或测试版本。

### 5.2 统一构建项目

统一构建项目采用以下结构：

```text
paytech-build/
├── pom.xml                         # 只聚合下面两个模块
├── paytech-dependencies/
│   └── pom.xml                     # 统一 BOM，packaging=pom
└── paytech-parent/
    └── pom.xml                     # 统一父 POM，packaging=pom
```

`paytech-dependencies` 负责：

- 导入 `spring-boot-dependencies:4.0.8`；
- 导入 `spring-cloud-dependencies:2025.1.3`；
- 导入 `spring-cloud-alibaba-dependencies:2025.1.0.0`；
- 管理 MyBatis Core 3.5.19、MyBatis Starter 4.0.1、Redisson 4.6.1、XXL-Job 3.4.2 及其他未被上游 BOM 管理的依赖；
- 管理 JUnit、JaCoCo 等测试依赖，但不引入 Testcontainers 或嵌入式中间件。

`paytech-parent` 负责：

- 导入 `paytech-dependencies`；
- 设置 `maven.compiler.release=25` 和 UTF-8；
- 统一 Compiler、Surefire、Failsafe、JaCoCo、Enforcer、Spring Boot Maven Plugin 等插件与执行规则；
- 保证默认测试只运行 `auto`，`manual` 只能通过显式 Profile 运行；
- 通过 Enforcer 禁止错误 Java/Maven 版本、依赖版本漂移和不允许的依赖。

`paytech-build` 与 `paytech-foundation` 是两个独立 Git 仓库，各自携带 Maven Wrapper。Foundation 的 Parent 使用空 `relativePath`，只从 Maven 仓库解析 `paytech-parent`；发布顺序固定为先 Build、后 Foundation，服务不得依赖同级源码目录才能构建。

服务父工程继承 `paytech-parent`，不在每个服务中重复导入多个上游 BOM。无法继承统一 Parent 的独立消费者可以只导入 `paytech-dependencies`，但必须自行承担插件管理。

### 5.3 本机制品库边界

- 本地 Maven 制品库基线使用 Nexus Repository Community 3.81.1；实际容器、Loopback 地址、Maven Settings、凭据边界和已知宿主绑定缺口只在 [`LOCAL_DEVELOPMENT_ENVIRONMENT.md`](./LOCAL_DEVELOPMENT_ENVIRONMENT.md) 维护。
- Maven 依赖与插件通过 `maven-public` Group 解析；Snapshot/Release 发布使用与开发者 Settings 一致的仓库 ID。POM 只引用仓库 ID 和 `${nexus.url}`，不得保存地址凭据或直接依赖 Hosted 仓库。
- Foundation 制品的本机安装、Nexus 发布和验证状态由 `paytech-foundation/README.md` 与 [`CURRENT_IMPLEMENTATION.md`](./CURRENT_IMPLEMENTATION.md) 记录。新增服务只依赖获准的 `paytech-*` 坐标，不恢复旧 `mor-*` Java 制品依赖。
- 生产制品库、发布权限、Release 不可变策略和 CI/CD 需独立设计；本机配置不得直接复制到生产。

公司名称确定为 PayTech，统一 Maven 坐标前缀使用 `com.paytech`，公共基础包使用 `com.paytech.foundation`。未来各业务服务在此公司级前缀下按系统边界选择基础包名，不复用历史 `com.payplus` 私有坐标。Code-MoR 仍是产品/项目名称；公司级 Maven artifactId 使用 `paytech-*`，既有本地运行资源和数据库继续使用 `mor-*` / `mor_*`，不把 `Mor` / `mor-` 前缀带入新增 Java 类名、Bean 名、配置前缀、日志、指标或错误 Code。

### 5.4 组件专用构建门禁

- API Gateway 是 WebFlux 例外，默认只含 `web + test`，不创建空 `*-api`；它仍继承统一 Java/Maven/覆盖率基线，但反应式依赖禁止清单和独立 Node 门禁由 [`api-gateway/README.md`](../api-gateway/README.md) 维护。
- 核心服务和 BFF 的模块、依赖方向与打包规则由 [`JAVA_PROJECT_STRUCTURE.md`](./JAVA_PROJECT_STRUCTURE.md) 拥有；具体端点、端口、依赖白名单、构建命令和产物只写在组件 README。
- 测试分组、真实协议链路、覆盖率及本地基础设施边界由 [`UNIT_TEST_GUIDE.md`](./UNIT_TEST_GUIDE.md) 拥有。任何组件实现或验证状态只进入 [`CURRENT_IMPLEMENTATION.md`](./CURRENT_IMPLEMENTATION.md) 和仓库证据。

## 6. 公共基础 JAR

公共能力拆分为小而稳定的 JAR，禁止形成包含领域 DTO、业务枚举和所有工具类的巨型 `common.jar`：

```text
paytech-foundation/
├── paytech-common-core/
├── paytech-http-client-support/
├── paytech-mybatis-support/
├── paytech-mvc-web-starter/
├── paytech-snowflake-id/
├── paytech-caffeine-starter/
├── paytech-redis-starter/
├── paytech-xxl-job-starter/
└── paytech-compensation-event/
```

九个 Foundation 模块的当前状态见状态台账。依赖方向固定为 `paytech-compensation-event -> paytech-mybatis-support -> paytech-common-core` 和 `paytech-compensation-event -> paytech-xxl-job-starter -> xxl-job-core`；`paytech-http-client-support` 依赖 Common Core 与 Spring HTTP 能力但不自动发现业务接口。公共 MyBatis 模块不依赖 Spring 或业务枚举，通用 XXL-Job Starter 不得反向引用补偿能力。`paytech-caffeine-starter` 提供有界进程内缓存，`paytech-redis-starter` 独立提供默认 Redis CacheManager、Lettuce 普通访问和 Redisson 协调客户端；二者都不依赖补偿或业务模块。

### 6.1 `paytech-common-core`

- 定义项目自有的业务异常基类、`BizCheckFailException`、稳定 `ErrorCode` 契约和与 Web 无关的最小公共值类型；
- 不依赖 Spring MVC、数据库、MQ、Redis 或某个业务服务；
- 不依赖或复制 Code-PMX/PayPlus 私有公共包。

### 6.2 `paytech-http-client-support`

- 为同步 `@HttpExchange` Client 提供统一网络、HTTP 状态、共享错误体和解码异常转换；
- 由宿主配置显式创建具体接口代理，不扫描业务包、不自动重试、不安装业务 AOP；
- 只依赖 Common Core 与必要 Spring HTTP 能力，不依赖某个业务服务、数据库或 MVC Server Starter。

### 6.3 `paytech-mybatis-support`

- 提供 MySQL `DATETIME(6)` 与 UTC `Instant` 的显式转换，以及实现 `CodeEnum` 的内部 DataObject 枚举映射；
- 每个业务枚举由宿主 MyBatis 配置显式注册，不扫描业务包、不替换全局默认枚举 Handler；
- 未知持久化 Code 失败关闭；外部 API/MQ DTO 继续使用字符串 Code；
- 只依赖 `paytech-common-core` 与 MyBatis Core，不依赖 Spring、数据库 Driver 或业务模块。

### 6.4 `paytech-mvc-web-starter`

- 自动提供一个完整 Request/Response/耗时的 INFO 终态日志，以及一个共享 `GlobalExceptionHandler`；MVC 服务不得重复实现本地日志 Filter 或 `@RestControllerAdvice`；
- 提供统一异常转换、参数校验错误、错误响应和 Request ID/MDC；当前已批准的完整 Body 日志不做脱敏、截断、采样，也不记录 Header/Cookie，后续如需改变必须独立设计；
- 完整 Body 日志不覆盖更窄专项安全文档的禁日志规则；Vault 明确禁止 Token、敏感明文和完整 Payload 进入普通日志，在专门日志边界获批前不得直接引入该 Starter，也不得以本地重复 Filter 绕开；
- 除 `HEAD` 的无 Body 语义及无法进入第一方错误 Writer 的 pre-application 边缘失败外，目标错误响应统一使用 RFC 9457-based Code-MoR Problem Profile 和 `application/problem+json`，必填 `type/title/detail/code/requestId/timestamp`，请求 Body 字段错误可附 `errors[{field,message}]`，Body 不复制 `status`；当前 `type=about:blank`、业务细分由应用扩展 `code` 承担，完整合同、边缘例外和消费者优先迁移状态分别见[全系统设计](../docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md)与当前状态台账；
- 不向外返回堆栈、SQL、内部类名、数据库键或敏感值；
- 外部 API 与内部 API 可以有不同业务 Code/Status 映射，但共享同一个 Problem Wire Shape、关联 ID 和错误时间合同。

### 6.5 `paytech-snowflake-id`

- 封装 Snowflake ID 接口、实现、时钟和节点参数校验；
- 本地单节点固定使用明确的 Region/Worker，例如 `0/0`，禁止随机碰撞式分配；
- 多实例环境必须由部署配置保证 `(regionId, workerId)` 唯一，未配置或重复时启动失败；
- 检测到超过容忍阈值的时钟回拨时失败关闭，不静默产生可能重复的 ID；
- 业务代码依赖 ID 接口，不直接创建静态 Snowflake 单例。

### 6.6 `paytech-caffeine-starter`

- 使用 Spring Cache + Caffeine 3.2.4 提供进程内缓存，版本由 Spring Boot 4.0.8 BOM 管理；
- 每个 Cache 默认最多 10,000 条、写入 10 分钟后过期、拒绝 `null` 并开启 Statistics；容量和 TTL 必须保持有界；
- 生产服务应显式配置固定 `caffeine.cache-names` 白名单，Cache Name 不得拼接 Client、Order、Token 或其他动态/敏感值；
- 只缓存可从权威数据源安全回源的加速数据，不保存业务权威事实、幂等、锁、跨实例一致性状态、Vault Reveal、凭据或敏感明文；淘汰、到期和实例重启必须不改变业务正确性；
- Starter 只注册名为 `caffeineCacheManager` 的 Manager；同时引入 Redis Starter 时，使用 Caffeine 的 `@Cacheable`、`@CachePut`、`@CacheEvict` 和 `@Caching` 方法必须显式指定 `cacheManager = "caffeineCacheManager"`；
- Caffeine 与 Redis 是按方法选择的两个独立 Manager，不是自动串联的 L1/L2 Cache；两边必须使用不同 Cache Name，不能期待任一 Manager 自动回源到另一 Manager；
- 当前设计不使用 `spring.cache.type` 切换 Manager。不需要进程内缓存的宿主不要引入 Caffeine Starter；只有宿主自己注册同名 `caffeineCacheManager` 时该 Bean 才退让。

### 6.7 `paytech-redis-starter`

- 通过 Spring Boot 标准 `spring.data.redis.*` 配置和 Spring Data Redis 提供 Lettuce 普通访问；对象缓存优先注入 `RedisJsonTemplate`，字符串、计数器和其他 Redis 原生命令使用 `StringRedisTemplate`；
- 同一 `RedisCacheManager` 实例以 `cacheManager` 和 `redisCacheManager` 两个 Bean Name 暴露，并标记为 `@Primary`；引入 Redis Starter 后，未显式指定 Manager 的 Spring Cache 注解默认使用 Redis；
- 每个 Redis Cache 必须由宿主服务注册一个 `RedisCacheDefinition<T>(cacheName, concreteValueType, ttl)`；Cache Name 使用固定小写 kebab-case，值类型必须是可实例化的具体类型，TTL 不短于 1 毫秒，未声明的 Cache 不得动态创建；
- Redis Cache 使用普通 Jackson 3 JSON，不写 Java 类元数据或 JDK 序列化内容；物理 Key 固定为 `{service-prefix}:cache:{cache-name}::{key}`，其中服务前缀遵守统一 Redis Namespace 规则；
- `RedisJsonTemplate` 保存不含 Java 类名的普通 UTF-8 JSON，读取时显式传入 `Class<T>` 或 `TypeReference<T>`，实现 `CodeEnum` 的枚举使用稳定 `code` 而非 `name()`；为支持滚动升级，新增未知字段可忽略，其他不兼容变更必须升级 Key 版本；每次写入必须显式提供至少 1 毫秒的 TTL；默认不创建使用 JDK 序列化的通用 `RedisTemplate<Object, Object>`；
- 通过 `redis.coordination.enabled=true` 按需创建只用于锁、可过期信号量等协调能力的 `RedissonClient`；
- 直接依赖 Redisson Core，不引入 `redisson-spring-boot-starter`，不得注册 `RedissonConnectionFactory` 或替换 Lettuce；
- Redisson 复用同一套地址、数据库、凭据、超时和 SSL 配置，强制显式配置连接/命令超时与有界线程/连接池；`RedisJsonTemplate`、Redisson 与 `StringRedisTemplate` 共用服务级 Key 前缀，默认取 `spring.application.name`，特殊场景可通过 `redis.key-prefix` 统一覆盖；
- Namespace 只作用于顶层 Key，不改写 Hash field、Value 或 Channel；业务代码不得通过通用 `RedisTemplate<Object, Object>`、原始连接、回调、Lua 硬编码 Key 或 Redisson 全库命令绕过隔离，跨 Key `SORT BY/GET` 失败关闭；Namespace 是默认防误用约定，生产权限隔离仍由独立凭据、ACL 或实例承担；
- 当前已确认部署边界为 Redis 单机模式；Sentinel、Cluster 和 Master/Replica 在获得设计与真实测试前失败关闭；
- 不提供通用 JSON Repository、Java 类型元数据序列化、Java 原生序列化、默认 TTL、Redis 业务权威状态或补偿 Claim；业务 Repository 负责具体 Key、结构版本、TTL、容量与失败语义。

### 6.8 `paytech-xxl-job-starter`

- 提供与具体业务能力无关的 XXL-Job Executor 配置、参数校验和生命周期接入；
- 可以被其他本地调度能力独立使用，不依赖补偿 API、Mapper、数据库或 Handler；
- 不决定补偿扫描参数、事件类型或 Claim/Lease 语义。

### 6.9 `paytech-compensation-event`

- 封装每服务本地 `compensation_event`、事务提交后调度、Claim/Lease、重试、Handler 注册和 XXL-Job 兜底扫描适配；
- 主动依赖 `paytech-xxl-job-starter`，并由本模块自己持有和注册补偿扫描 Handler；
- 不部署中央 Worker，不拥有跨服务数据库；
- 详细行为必须遵守 [COMPENSATION_EVENT_GUIDE.md](./COMPENSATION_EVENT_GUIDE.md)。

## 7. Spring Web 与内部调用

- 六个核心 Java 服务、`operations-console-bff`、`client-console-bff` 和 `partner-console-bff` 默认使用 Spring MVC；Vault V1 Server 使用 Go，Java 调用方通过发布的 `@HttpExchange` Client 访问。
- API Gateway 使用 Spring Cloud Gateway Server WebFlux；Operations 首期的 JWT、路由、限流和并发 Filter 保持端到端 Reactive。
- Gateway 验证 Access JWT 和 Channel/Actor 上下文，不处理账号密码登录，也不执行 PLT-I02 或核心资源授权。它消费 Platform 的全量不可变 Snapshot，同时保留 Approved Target Registry、预编译安全实现和绝对安全上限；当前 Route 数量与环境 rollout 见状态台账和 Gateway README。
- 未匹配的 API 由 Gateway 本地拒绝，不转发到下游；不启用 Discovery Locator、通配外部 Route 或由 Redis 直接充当配置权威。
- 内部同步接口由提供方 `*-api` JAR 提供 `@HttpExchange` 契约，调用方通过本地 `*Client` 适配器隔离传输 DTO。
- MVC 服务默认使用 `RestClient`；确有流式或 Reactive 边界时才使用 `WebClient`。
- Nacos 负责服务发现，Spring Cloud LoadBalancer 负责实例选择；服务间调用不得绕行外部 Gateway。
- 不新增 Dubbo 或 OpenFeign 契约。

## 8. Nacos 配置与注册中心

- Nacos 同时承担服务注册发现和非敏感运行配置管理。
- Spring Cloud Alibaba 2025.1 不再使用 `bootstrap.yml`；统一在 `application.yml` 中通过 `spring.config.import` 导入 Nacos 配置。
- 使用独立环境 Namespace；本地为 `mor-local`。环境隔离不用 Data ID 名称前缀模拟。
- 共享配置与服务配置分开，例如 `mor-common.yaml` 和 `{spring.application.name}.yaml`。
- 数据库密码、RabbitMQ/Redis 密码、KMS/Secrets Manager 凭据、证书和 Token 不存入 Nacos；Nacos 只保存 Secret Reference 或非敏感参数。
- Nacos 不成为业务事实、幂等状态、锁或业务 Feature 数据库。

## 9. MySQL、MyBatis 与手工 SQL

### 9.1 部署边界

早期允许使用一个云 MySQL 实例承载多个逻辑数据库，但必须同时满足：

- 六个核心系统、Vault、Nacos 和 XXL-Job 使用各自数据库；
- 每个服务使用独立数据库账号，只拥有自己的数据库权限；
- 禁止跨数据库查询、写入、外键、视图、触发器和事务；
- 备份、恢复演练、容量和性能可以按共享实例管理，但数据所有权不能因此合并；
- Vault 在处理真实敏感数据前重新评估独立实例和更严格网络/审计边界。

### 9.2 手工 SQL 的作用

数据库结构由人工审查、人工执行的普通 SQL 管理：

- PDManer 继续作为物理设计源；
- 每个服务保留一份反映当前完整结构的 Schema DDL；
- 每个服务所有必要初始数据集中在一份 DML，禁止用 Java Seed 对象图重建；
- SQL 变更必须在评审记录中说明执行顺序、影响与回滚方式，应用不得自动执行；
- 一旦数据库实现获批，PDManer、DDL/DML、DataObject/Mapper/Repository 和本地 MySQL 测试必须在同一变更中保持一致；DataObject 类型使用业务实体名，不加 `DO` 后缀。

具体哪些服务已具备手工 Schema/DML、物理数据库或 Java 映射只由 [`CURRENT_IMPLEMENTATION.md`](./CURRENT_IMPLEMENTATION.md) 和组件 README 记录。本地 Compose 初始化脚本只准备相互隔离的数据库、最小账号以及中间件自身所需技术表，不代表业务 Schema 已创建。

## 10. RabbitMQ 基线

RabbitMQ 是 MVP 唯一业务 MQ，不再并行评估 RocketMQ。

- Java 侧使用 Spring AMQP，保持 Exchange、Queue、Binding、Publisher Confirm、Ack 和 Retry 语义显式；当前不增加 Spring Cloud Stream。
- Topic 映射为 Durable Topic Exchange：`topic_{source_system}_{business_subject}`。
- Consumer Group 映射为 Durable Queue：`cg_{consumer_system}_{consumer_subject}`。
- 名称使用小写 `snake_case`；环境通过 RabbitMQ VHost 隔离，本地 VHost 为 `/mor-local`。
- Routing Key 使用稳定的小写点分格式，例如 `payment.succeeded.v1`。
- 生产持久消费队列默认使用 Quorum Queue；本地单节点只验证语义，不声称具备高可用。
- 消息设置 Persistent，发布端必须等待 Publisher Confirm；消费端使用 Manual Ack，本地事务提交后才 Ack。
- 每个消费队列配置 DLX/DLQ 和显式 Delivery Limit；禁止无界立即重试。
- 同一个 Consumer Group 的实例竞争消费同一 Queue；不同消费者语义使用不同 Queue 和独立幂等事实。

### 10.1 JSON 事件信封

统一使用 UTF-8 JSON，至少包含：

```json
{
  "eventKey": "PAYMENT:PAYMENT_INTENT:10001:3:PaymentSucceeded",
  "eventType": "PaymentSucceeded",
  "sourceSystem": "payment",
  "sourceType": "PAYMENT_INTENT",
  "sourceId": "10001",
  "sourceVersion": 3,
  "occurredAt": "2026-08-28T10:15:30.123456Z",
  "schemaVersion": 1,
  "payloadHash": "sha256:...",
  "payload": {}
}
```

- 可能超出 JavaScript 安全整数范围的 ID 必须编码为十进制字符串；
- 时间使用 UTC ISO-8601；金额使用最小货币单位或明确的十进制字符串并携带币种；
- Payload 必须最小化，不放敏感明文、Vault Token、Secret、大文件或 Provider 原始报文；
- `schemaVersion` 的兼容演进必须先定义消费者策略。

## 11. 补偿执行参数

统一初始运行参数：

| 参数 | 默认值 |
|---|---:|
| Coordinator Core Size | 10 |
| Coordinator Maximum Size | 20 |
| Coordinator Queue Capacity | 80 |
| Coordinator Keep Alive | 60 秒 |
| Handler Maximum Concurrency | 20 |
| 单次扫描批次 | 100 |
| Rejection Policy | `AbortPolicy`，上层捕获后保留为可扫描状态并产生指标 |

以上参数均有代码默认值，`instance-id` 默认取 `spring.application.name`；宿主通常不需要增加补偿配置，容量评估后才选择性覆盖。每类事件的超时、最大次数和退避由 Handler 的 `CompensationExecutionPolicy` 定义，告警阈值和人工恢复 SLA 仍由宿主确定。

## 12. Redis 与 Gateway

- Redis 早期使用单机部署。开发和测试统一复用仓库专用 `mor-local` Compose Redis，使用运行级唯一 Namespace 和受限清理，不使用 Testcontainers。
- 普通同步/Reactive Redis 操作使用 Spring Data Redis + Lettuce；非 Gateway 服务需要锁、可过期信号量等明确协调能力时可按 `paytech-redis-starter` 约束使用 Redisson。
- Operations Gateway 不依赖 `paytech-redis-starter`，不创建 Blocking/Reactive Redis Template、阻塞连接或 Redisson Client，只使用 `ReactiveRedisConnectionFactory` 和 Reactive Lua 执行。
- Gateway Rate Limit 使用原子 Lua Token Bucket；一次请求的 Route/IP/Subject 等适用维度全量通过后才统一扣减。
- Gateway Concurrency 使用 Lua + ZSET Expiring Lease；Acquire 全维度原子成功或失败，Reactor 的成功、错误和取消路径都释放 Lease，进程故障由 TTL 恢复容量。
- Guard 为静态的每路由 Profile：先 Rate Limit，再立即 Acquire Concurrency Lease，不等待、不排队；后续 Concurrency、JWT、Body 或下游拒绝不退还已消费的 Rate Token，所有已获 Lease 在成功、错误和取消时释放。Redis 采用 `guard:v3` 和服务器时间；Client IP Key 使用 Nginx 覆盖写入且由 Gateway 只解析一次的单值合法 `X-Real-IP`，Subject Key 使用已验证 JWT 的准确 `sub`，Guard 不再配置 HMAC。Rate Hash 只含 `t/lr/rr`，Concurrency Metadata 只含 `hw/l`，不保存 Policy Revision，也不执行滚动状态迁移。Access Log 从同一可信 IP 上下文生成独立 HMAC 假名值，已消费的代理 Header 在转发 BFF 前删除。
- 其他服务使用 Redis 暂不需要额外架构审批，但必须定义 Key Namespace、TTL、容量、失败语义和自动测试。
- Redis 不存放账务余额、支付状态、补偿 Claim、不可丢失幂等事实或其他权威业务状态。资金和状态并发仍使用 MySQL 行锁/CAS/唯一约束。

## 13. 可观测性

```text
Java Service
  -> Actuator / Micrometer Observation / SLF4J
  -> OpenTelemetry Java Agent
  -> OTLP
  -> OpenTelemetry Collector
  -> Prometheus / Loki / Tempo
  -> Grafana / Alertmanager
```

- Metrics：Prometheus；Logs：结构化日志进入 Loki；Traces：Tempo；统一查询和 Dashboard：Grafana；告警路由：Alertmanager。
- 每个请求和消息携带 Request/Trace/Correlation ID；MQ Producer/Consumer Span 保留 `eventKey` 等非敏感关联属性。
- 日志、Trace、Metric Label 禁止携带敏感明文、完整 Vault Token、Blind Index、Secret 或无界高基数字段。
- 业务指标通过 Micrometer 定义，不直接依赖 Prometheus/Grafana SDK。
- 本地可观测环境按需启动；自动测试不依赖共享 Collector 或共享监控后端。
- API Gateway 默认在 `18080` 提供 Application Listener，在独立 `19080` 管理端口只暴露 `health`、`info` 和 `prometheus`。Liveness 只包含 `livenessState`；Readiness 精确包含 `readinessState`、`gatewayBootstrap` 和短超时 `gatewayRedis`，不调用 Operations BFF 或 JWKS。
- Gateway Access Log 是结构化、有界、HMAC 假名化的终态日志，每个请求终态精确记录一次；原始 Path、IP、Subject、Token、Cookie 和其他请求原文不进入日志或无界 Metric Tag。Gateway Request/Guard/JWKS 指标只使用稳定低基数维度。
- 公网 HTTPS 仅在 Nginx 终止；Nginx 覆盖一个 `X-Real-IP` 并通过私有网络访问 Gateway。Gateway Application Listener、BFF、Platform Support、Vault、核心 Java 服务与 JWKS 使用私有 HTTP，均不持有 X.509 应用证书；Gateway 只允许来自 Nginx 私网的流量。

## 14. 云厂商边界

AWS 是当前可能选项，但尚未最终确定。代码保持云中立，外部能力通过 Integration/Port 隔离。若使用 AWS，优先映射为：

- MySQL：Amazon RDS for MySQL；
- Redis：Amazon ElastiCache；
- RabbitMQ：Amazon MQ for RabbitMQ 4.2；
- 对象存储：Amazon S3；
- 密钥与 Secret：AWS KMS + AWS Secrets Manager。

AWS SDK 只能出现在外部集成适配器，不进入 Domain 或 Processor。生产网络、可用区、备份、容灾、部署和交付方案仍未批准。

## 15. 明确延期的技术决策

以下内容没有因为技术栈确认而自动获批：

- Client、Partner、Consumer 和机器入口的 Gateway 认证、Token、Session 和授权上下文方案；Operations 首期已由 `plans/2026-08-29-operations-api-gateway-design.md` 固定；
- 云厂商最终选择与生产部署拓扑；
- Kubernetes/ECS、CI/CD、生产制品库、凭据治理和发布流程；
- 每个业务服务的具体基础包名和业务契约；
- 每类补偿事件的超时、退避、最大次数、告警和人工恢复 SLA；
- Vault 本地 KMS/Object Storage 模拟器与生产密钥生命周期；
- 自动化 Retention、Legal Hold、归档和销毁基础设施。

## 16. 官方依据

- [Oracle Java SE Support Roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)
- [JEP 491: Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491)
- [Spring Boot 4.0.8](https://spring.io/blog/2026/08/20/spring-boot-4-0-8-available-now/)
- [Spring Cloud 2025.1.3](https://spring.io/blog/2026/08/20/spring-cloud-2025-1-3-has-been-released/)
- [Spring Cloud Alibaba 版本说明](https://sca.aliyun.com/docs/2025.x/overview/version-explain/)
- [Nacos Release History](https://nacos.io/en/download/release-history/)
- [MySQL LTS Release Model](https://dev.mysql.com/doc/refman/8.4/en/mysql-releases.html)
- [RabbitMQ Release Information](https://www.rabbitmq.com/release-information)
- [Amazon MQ RabbitMQ 4](https://docs.aws.amazon.com/amazon-mq/latest/developer-guide/rabbitmq-4.html)
- [Redis Version Management](https://redis.io/docs/latest/operate/oss_and_stack/install/version-mgmt/)
- [Redisson Spring Integration](https://redisson.pro/docs/integration-with-spring/)
- [OpenTelemetry Java](https://opentelemetry.io/docs/languages/java/)
