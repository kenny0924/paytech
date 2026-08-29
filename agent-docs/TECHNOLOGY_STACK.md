# Code-MoR 技术栈基线

最后更新：2026-08-29

状态：已确认的 Java、中间件和工程治理基线。基础 Maven 构建、五个公共 JAR 与本地 `base` Compose Profile 配置已经初始化，构建治理 POM 和公共 JAR 的 `1.0.0-SNAPSHOT` 已发布到本机 Nexus；`base` Profile 已完成本机烟测并关闭，业务服务、Migration、业务表、后续运行环境和生产基础设施仍未授权创建。

## 1. 使用范围

当任务涉及 Java 版本、Spring 版本、Maven 依赖管理、数据库、Migration、MQ、Redis、Gateway、配置中心、补偿调度、可观测性或本地运行环境时，必须先读取本文。具体工程分层、可靠事件、测试和本地搭建仍分别以以下文档为准：

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
| 同步 Web 栈 | Spring MVC | Spring Boot 管理 | 六个核心服务、BFF 和 Vault 默认使用 MVC |
| Gateway Web 栈 | Spring Cloud Gateway Server WebFlux | Spring Cloud 管理 | Gateway 是 WebFlux 例外，不强行改成 MVC |
| 内部同步调用 | Spring HTTP Service Client | Spring Framework 管理 | `@HttpExchange` + `RestClient` + LoadBalancer + Nacos；不新增 Dubbo/OpenFeign |
| 数据访问 | MyBatis Spring Boot Starter | 4.0.1 | 与 Spring Boot 4.0 线对齐；Mapper XML 为默认复杂 SQL 载体 |
| 数据库 | MySQL LTS | 8.4；本地固定 8.4.11 | 早期允许一个实例承载多数据库，但每系统独立数据库和账号 |
| Schema 演进 | Flyway | Spring Boot BOM 管理 | 数据库实现获批后启用；PDManer 是设计源，Migration 是可执行历史 |
| MQ | RabbitMQ | 4.2；本地固定 4.2.9 | 对齐 Amazon MQ RabbitMQ 4.2；自建生产前重新选择当时受支持系列 |
| MQ 序列化 | JSON UTF-8 | 统一事件信封 | 禁止 Java 原生序列化；事件契约必须版本化 |
| Redis Server | Redis Open Source Extended | 8.2；本地固定 8.2.1 | 早期单机；不是业务权威数据源 |
| 通用 Redis 客户端 | Spring Data Redis + Lettuce | Spring Boot BOM 管理 | 普通 Key/Value、缓存、Reactive Redis 和 Gateway 限流 |
| 分布式协调客户端 | Redisson Community | 4.6.1 | 仅用于锁、可过期信号量等明确协调能力 |
| 补偿调度 | XXL-Job | 3.4.2 | 只唤醒/分片服务内扫描；数据库 Claim/Lease 才是最终互斥边界 |
| Gateway 限流 | Spring Cloud Gateway `RedisRateLimiter` | Spring Cloud 管理 | Token Bucket；并发限制使用 Redisson 可过期信号量 |
| 可观测性 API | Actuator + Micrometer Observation | Spring Boot BOM 管理 | 业务代码只依赖 Micrometer/SLF4J 等中立 API |
| 自动采集 | OpenTelemetry Java Agent + OTLP | 创建运行环境时固定补丁版本 | 默认通过 Java Agent 自动采集，不在业务代码绑定后端 SDK |
| 遥测管道 | OpenTelemetry Collector | 创建运行环境时固定镜像版本 | 统一接收和转发 Metrics/Logs/Traces |
| 本地可观测后端 | Prometheus + Loki + Tempo + Grafana + Alertmanager | 创建 Compose 时固定镜像版本 | 按需 Profile 启动，不作为默认开发前置条件 |
| Java 测试 | JUnit 5 + Testcontainers | BOM 管理 | 默认 `auto` 内部闭环；不连接共享本地或真实业务环境 |

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

统一构建项目已经按以下结构初始化：

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
- 管理 MyBatis Starter 4.0.1、Redisson 4.6.1、XXL-Job 3.4.2 及其他未被上游 BOM 管理的依赖；
- 管理 Testcontainers 等测试 BOM，但不把测试依赖带入生产模块。

`paytech-parent` 负责：

- 导入 `paytech-dependencies`；
- 设置 `maven.compiler.release=25` 和 UTF-8；
- 统一 Compiler、Surefire、Failsafe、JaCoCo、Enforcer、Spring Boot Maven Plugin 等插件与执行规则；
- 保证默认测试只运行 `auto`，`manual` 只能通过显式 Profile 运行；
- 通过 Enforcer 禁止错误 Java/Maven 版本、依赖版本漂移和不允许的依赖。

`paytech-build` 与 `paytech-foundation` 是两个独立 Git 仓库，各自携带 Maven Wrapper。Foundation 的 Parent 使用空 `relativePath`，只从 Maven 仓库解析 `paytech-parent`；发布顺序固定为先 Build、后 Foundation，服务不得依赖同级源码目录才能构建。

服务父工程继承 `paytech-parent`，不在每个服务中重复导入多个上游 BOM。无法继承统一 Parent 的独立消费者可以只导入 `paytech-dependencies`，但必须自行承担插件管理。

### 5.3 本机 Nexus

- 复用当前电脑已有的 Nexus 3.81.1，地址为 `http://127.0.0.1:18081`；它位于 `mor-local` Compose 之外，不由本仓库创建、重启、删除或管理数据卷。
- Maven 依赖与插件统一通过 `maven-public` Group 解析，`mirrorOf=*` 防止构建绕过本机代理；Snapshot 发布到 `maven-snapshots`，Release 发布到 `maven-releases`。
- 本机使用开发者指定的 `/Users/kenny/Desktop/Workspace/Settings/3-Maven/settings/local_settings.xml`，并由 `~/.m2/settings.xml` 引用。该文件包含本机凭据，权限必须为 `600`，不得复制到仓库、日志或文档。
- POM 的 `distributionManagement` 只引用仓库 ID 和 `${nexus.url}`；地址与凭据由 Maven Settings 注入，服务 POM 不硬编码账号或密码。
- 当前 `com.paytech` 下的 `paytech-dependencies`、`paytech-parent`、`paytech-build`、`paytech-foundation`、`paytech-common-core`、`paytech-snowflake-id`、`paytech-mvc-web-starter`、`paytech-xxl-job-starter` 和 `paytech-compensation-event` 已发布 `1.0.0-SNAPSHOT`。旧 `mor-*` Snapshot 暂时保留，不作为新服务依赖。
- 生产制品库、发布权限模型、Release 不可变策略和 CI/CD 发布流程仍需单独设计；本机配置不直接复制到生产。

公司名称确定为 PayTech，统一 Maven 坐标前缀使用 `com.paytech`，公共基础包使用 `com.paytech.foundation`。未来各业务服务在此公司级前缀下按系统边界选择基础包名，不复用历史 `com.payplus` 私有坐标。Code-MoR 仍是产品/项目名称；公司级 Maven artifactId 使用 `paytech-*`，既有本地运行资源和数据库继续使用 `mor-*` / `mor_*`，不把 `Mor` / `mor-` 前缀带入新增 Java 类名、Bean 名、配置前缀、日志、指标或错误 Code。

## 6. 公共基础 JAR

公共能力拆分为小而稳定的 JAR，禁止形成包含领域 DTO、业务枚举和所有工具类的巨型 `common.jar`：

```text
paytech-foundation/
├── paytech-common-core/
├── paytech-mvc-web-starter/
├── paytech-snowflake-id/
├── paytech-xxl-job-starter/
└── paytech-compensation-event/
```

五个 Foundation 模块均已实现。依赖方向固定为 `paytech-compensation-event -> paytech-xxl-job-starter -> xxl-job-core`；通用 XXL-Job Starter 不得反向引用补偿能力。

### 6.1 `paytech-common-core`

- 定义项目自有的业务异常基类、`BizCheckFailException`、稳定 `ErrorCode` 契约和与 Web 无关的最小公共值类型；
- 不依赖 Spring MVC、数据库、MQ、Redis 或某个业务服务；
- 不依赖或复制 Code-PMX/PayPlus 私有公共包。

### 6.2 `paytech-mvc-web-starter`

- 提供统一异常转换、参数校验错误、错误响应、Request ID/MDC 和必要的日志脱敏；
- 默认错误响应至少包含稳定 `code`、可读 `message`、`requestId` 和 UTC `timestamp`；
- 不向外返回堆栈、SQL、内部类名、数据库键或敏感值；
- 外部 API 与内部 API 可以有不同错误映射，但共享稳定错误编码和关联 ID。

### 6.3 `paytech-snowflake-id`

- 封装 Snowflake ID 接口、实现、时钟和节点参数校验；
- 本地单节点固定使用明确的 Region/Worker，例如 `0/0`，禁止随机碰撞式分配；
- 多实例环境必须由部署配置保证 `(regionId, workerId)` 唯一，未配置或重复时启动失败；
- 检测到超过容忍阈值的时钟回拨时失败关闭，不静默产生可能重复的 ID；
- 业务代码依赖 ID 接口，不直接创建静态 Snowflake 单例。

### 6.4 `paytech-xxl-job-starter`

- 提供与具体业务能力无关的 XXL-Job Executor 配置、参数校验和生命周期接入；
- 可以被其他本地调度能力独立使用，不依赖补偿 API、Mapper、数据库或 Handler；
- 不决定补偿扫描参数、事件类型或 Claim/Lease 语义。

### 6.5 `paytech-compensation-event`

- 封装每服务本地 `compensation_event`、事务提交后调度、Claim/Lease、重试、Handler 注册和 XXL-Job 兜底扫描适配；
- 主动依赖 `paytech-xxl-job-starter`，并由本模块自己持有和注册补偿扫描 Handler；
- 不部署中央 Worker，不拥有跨服务数据库；
- 详细行为必须遵守 [COMPENSATION_EVENT_GUIDE.md](./COMPENSATION_EVENT_GUIDE.md)。

## 7. Spring Web 与内部调用

- 六个核心服务、Business Portal BFF、Operations BFF 和 Vault 默认使用 Spring MVC。
- API Gateway 使用 Spring Cloud Gateway Server WebFlux，因为其过滤链和 Redis Rate Limiter 基于 Reactive 模型。
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

## 9. MySQL、MyBatis 与 Migration

### 9.1 部署边界

早期允许使用一个云 MySQL 实例承载多个逻辑数据库，但必须同时满足：

- 六个核心系统、Vault、Nacos 和 XXL-Job 使用各自数据库；
- 每个服务使用独立数据库账号，只拥有自己的数据库权限；
- 禁止跨数据库查询、写入、外键、视图、触发器和事务；
- 备份、恢复演练、容量和性能可以按共享实例管理，但数据所有权不能因此合并；
- Vault 在处理真实敏感数据前重新评估独立实例和更严格网络/审计边界。

### 9.2 Flyway 的作用

Flyway 保存数据库从空库演进到当前版本的可执行、可审计历史：

- PDManer 继续作为物理设计源；
- 版本化 Migration 负责创建和演进真实数据库；
- 每个服务数据库拥有自己的 `flyway_schema_history`；
- 已应用 Migration 不得重写，修正必须创建新版本；
- 一旦数据库实现获批，PDManer、Migration、DO/Mapper/Repository 和 Testcontainers 测试必须在同一变更中保持一致。

业务数据库实现仍停留在已批准的 PDManer 设计阶段，不创建 `V1__initial_schema.sql` 或业务表。本地 Compose 初始化脚本只为未来启动准备相互隔离的空数据库和最小账号，并包含 Nacos 自身所需的技术表。

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
| Core Pool Size | 10 |
| Maximum Pool Size | 20 |
| Queue Capacity | 500 |
| Keep Alive | 60 秒 |
| 单次扫描批次 | 100 |
| Rejection Policy | `AbortPolicy`，上层捕获后保留为可扫描状态并产生指标 |

这些参数通过 Nacos 外部配置，可按服务和事件类型覆盖。每类事件的超时、最大次数、退避、告警阈值和人工恢复 SLA 尚未统一，必须在实现具体 Handler 时确定。

## 12. Redis 与 Gateway

- Redis 早期使用单机部署；本地和测试都使用独立实例或容器，不复用开发者已有 Redis 数据。
- 普通同步/Reactive Redis 操作使用 Spring Data Redis + Lettuce。
- Redisson 用于明确的分布式锁、可过期信号量等协调能力，不替代全部 Redis 客户端。
- Gateway 请求速率限制使用 Spring Cloud Gateway `RequestRateLimiter` + `RedisRateLimiter` Token Bucket。
- Gateway 并发限制使用 Redisson `RPermitExpirableSemaphore` 一类具有租约/过期语义的信号量，不使用无法表达并发配额的普通锁。
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

- Gateway 的具体认证、Token、Session 和授权上下文方案；
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
