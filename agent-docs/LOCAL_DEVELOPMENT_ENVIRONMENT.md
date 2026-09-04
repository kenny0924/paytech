# Code-MoR 本地开发环境方案

最后更新：2026-09-04

> **独立 test 部署增量：** 用户于 2026-09-04 授权创建与 local 并行的 Docker test 环境并部署 Operations 前后端，并明确 test 不需要 HTTPS。现已新增 [`test-environment`](../test-environment/README.md)，Compose Project 为 `mor-test`；基础设施沿用本地版本并使用独立端口、网络、数据卷和随机凭据，加入 Platform、BFF、Gateway、前端及 HTTP Nginx。入口 `http://localhost:28080`，无需证书；HTTP test 模式要求显式开关、单独 test Profile 和 localhost Origin。Operations 后续按用户要求改为 JSON Refresh，不读写 Cookie，精确 Origin/JSON/JWT/IAM 校验保持。9 容器健康、真实登录/Bootstrap/改密/IAM 查询/审计/Refresh/退出已验收。22 表/13 Route 目录、测试 Bootstrap 与真实测试签名公钥在新 test 库显式初始化，未改变 local 数据和秘密。Platform 可明确启用 `test` Mock，仍拒绝生产 Profile；不是 Java 启动 Seed 或生产部署。本文其余混合开发与 local 自动测试约束不变。

状态：已确认并完成第一阶段初始化。本工作区已创建 `base` Docker Compose Profile 配置、独立仓库 Maven Wrapper、统一构建项目和八个公共基础 JAR 源码模块；当前电脑已有 Nexus 已接入 Maven，既有构建治理 POM 与五个公共 JAR Snapshot 已发布，新增 Caffeine/Redis Starters、`paytech-mybatis-support` 及对应 BOM 增量已在本机实现、测试并安装到本机 Maven 缓存但尚未发布到 Nexus。`base` Profile 已在当前 Mac 完成首次启动与运行验收，容器按需启停并保留三个持久卷。Operations 首期 API Gateway 已作为独立 Maven 仓库实现并通过构建门禁；Platform Support 已实现 Phase A 手工 SQL/MyBatis 21-table 投影、私有 HTTP PLT-I20 全量 Snapshot Provider、Operations PLT-I22 JWKS Provider 与 Nacos Discovery 注册。Operations Console BFF 本次初始化应用基础，默认端口见第 8 节；其他五个核心服务 Java 模块、Platform 剩余业务能力、BFF 业务能力、其他 Console BFF/前端、调度和外部可观测运行环境仍未创建。

当前传输基线以 [`2026-09-02-mvc-web-logging-and-internal-http-simplification-design.md`](../docs/superpowers/specs/2026-09-02-mvc-web-logging-and-internal-http-simplification-design.md) 为准：Nginx 终止公网 TLS 并覆盖一个 `X-Real-IP`；Gateway 只接受 Nginx 私网流量，解析该值一次供 Guard/访问日志使用，并在转发 BFF 前删除代理 Header；Gateway、BFF、Platform Support、Vault 与核心 Java 服务通过私有 HTTP 通信且不持有 X.509 应用证书。

## 1. 目标

本地环境优先满足：

- 在当前 Apple Silicon Mac 上快速调试单个服务和一条跨服务业务链；
- 基础设施版本可重复、可隔离，不污染或复用开发者现有容器数据；
- Java 服务保留 IDE 断点、热重启和低成本重建体验；
- 不要求日常同时运行十一个 Java Deployable（六个核心服务、API Gateway、三个 Console BFF 和 Vault）和完整可观测栈；
- 自动测试统一复用仓库 `local-environment` 中的 MySQL、Redis 与 RabbitMQ；测试不使用 Testcontainers，也不负责启动、停止或重建中间件。

## 2. 当前电脑实测基线

| 项目 | 当前状态 |
|---|---|
| 设备 | MacBook Pro，Apple M4 Pro，12 核（8 Performance + 4 Efficiency） |
| 内存 | 24 GB |
| 系统 | macOS 15.6.1，`arm64` |
| Docker Desktop | 4.63.0；Engine 29.2.1；Compose 5.0.2 |
| Docker 当前资源上限 | 12 CPU，约 15.84 GiB 内存 |
| JDK | 已安装 Java 25.0.1、24.0.2、21.0.7、17.0.15 和 8 |
| 当前终端默认 Java | Java 8；当前 Maven 也在 Java 8 上运行，必须修正 |
| 当前 Maven | 全局 Maven 3.9.9；仓库统一使用 Maven Wrapper 3.9.16 |
| Node.js | 24.6.0；现有需求网站可继续使用 |
| 数据盘 | 约 460 GiB，总使用约 400 GiB，只剩约 24 GiB |
| Docker 数据 | Images 约 32.42 GB、Containers 约 4.70 GB、Volumes 约 27.29 GB、Build Cache 约 7.90 GB |
| 当前运行容器 | Nexus 占用 `18081`；已有 Redis 7.2 占用 `6379` |

本文不记录设备序列号、硬件 UUID 或其他无关设备标识。

## 3. 方案选择

### 3.1 采用：混合运行

```text
macOS / IDE / Maven
  ├── requirements-site
  ├── API Gateway（需要时）
  ├── 当前业务链涉及的 Java 服务
  ├── OpenTelemetry Java Agent
  └── 已有 Nexus（本机制品库，位于 mor-local 之外）

Docker Desktop / mor-local
  ├── base: MySQL + Nacos + RabbitMQ + Code-MoR Redis
  ├── scheduler: XXL-Job Admin（后续 Profile）
  └── observability: OTel Collector + Prometheus + Loki + Tempo + Grafana + Alertmanager（后续 Profile）
```

选择理由：

- Java 服务原生运行便于断点、热重启和观察 JVM；
- 有状态基础设施容器化可固定版本、网络和卷；
- 24 GB 内存足以运行一条业务链，但不适合日常把所有 Java 服务、基础设施和可观测栈全部常驻；
- 当前磁盘空间紧张，按 Profile 启动能避免默认拉取全部镜像。

### 3.2 未采用：全部容器化

所有 Java 服务也运行在 Compose 中更接近部署形态，但会增加镜像、重建时间和调试成本；在当前 24 GiB 剩余磁盘下不作为默认开发方式。

### 3.3 未采用：全部 Homebrew 原生安装

本机已有 Homebrew Redis，但原生安装会造成多项目版本、端口、数据目录和启动项相互影响。Code-MoR 不复用现有 `redis-single`，也不要求安装原生 MySQL、RabbitMQ 或 Nacos。

## 4. 安全与隔离规则

- Compose Project Name 固定为 `mor-local`，所有 Container、Network 和 Volume 都使用该命名空间。
- 所有宿主机端口只绑定 `127.0.0.1`，不暴露到局域网。
- 现有 `nexus` 只作为 Maven 本机制品库复用，不纳入 `mor-local` Compose，不由本仓库重启、删除、升级或修改数据卷；现有 `redis-single` 和其他项目容器/卷不复用。
- 日常停止使用 `docker compose down`，不附带 `-v`；删除持久卷必须单独确认。
- 禁止使用未经检查的 `docker system prune -a --volumes`。
- 本地密码只放在不提交的 `.env.local` 或开发者 Secret Store；仓库只提交 `.env.example`。
- 所有镜像固定版本；实际创建 Compose 时记录镜像摘要并验证 `linux/arm64` 支持。
- Nacos 3.1.2、MySQL 8.4.11、Redis 8.2.1 和 RabbitMQ 4.2.9 官方镜像支持 Apple Silicon。
- XXL-Job Admin 3.4.2 官方镜像当前只有 `linux/amd64`。本地 `scheduler` Profile 可显式使用模拟运行；长期方案是从官方 3.4.2 源码构建 `arm64` 镜像或直接以 Java 进程运行，不把模拟镜像作为生产依据。

## 5. 磁盘与 Docker Desktop 资源

### 5.1 磁盘门禁

当前数据盘只剩约 24 GiB，完整可观测栈会进一步拉取多个镜像并写入时序数据。实际搭建前必须：

1. 使用 `docker system df` 识别空间来源；
2. 由开发者确认哪些停止容器和未使用镜像可以删除；
3. 不自动删除现有 27 GB Volumes；
4. 启动 `base` 后至少保留 15 GiB 可用磁盘；
5. 空间不足时先不启用 `observability` Profile。

当前显示约 30 GB Images 和 4.7 GB Stopped Containers 可回收，但“可回收”不代表可以由 Code-MoR 任务自动删除；它们可能仍属于其他项目。

### 5.2 建议资源设置

Docker Desktop 建议从当前约 16 GiB 上限调整为：

| 模式 | CPU | Memory | Swap | 说明 |
|---|---:|---:|---:|---|
| 日常 `base` | 8 | 10 GB | 2 GB | 同时保留 IDE、浏览器和少量 Java 服务空间 |
| 临时完整观测 | 8 | 12 GB | 2 GB | 使用完即关闭 `observability` |

建议容器内存预算：

| 组件 | 初始上限 |
|---|---:|
| MySQL | 1.5 GB |
| Nacos | 1 GB |
| RabbitMQ | 768 MB |
| Redis | 256 MB |
| XXL-Job Admin | 512 MB |
| OpenTelemetry Collector | 256 MB |
| Prometheus | 512 MB |
| Loki | 512 MB |
| Tempo | 512 MB |
| Grafana | 512 MB |
| Alertmanager | 128 MB |

本地 Java 服务默认从 `-Xms128m -Xmx512m` 起步，Finance/Risk 等确有需要时单独提高。不要为了模拟生产而一次启动所有 Deployable。

## 6. JDK 与 Maven 准备

当前机器已经安装 JDK 25，但 Shell 和 Maven 实际使用 Java 8。启动任何 Spring Boot 4 工程前，当前终端应执行：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
```

校验结果必须同时满足：

- `java -version` 显示 Java 25；
- `mvn -version` 的 Runtime 也显示 Java 25；
- IDE Project SDK、Maven Runner JRE 和 Run Configuration 都指向同一个 JDK 25。

Java 工程统一使用各自仓库提交的 `./mvnw`，避免依赖 Code-MoR 根目录或开发者全局 Maven 版本。是否把 JDK 选择写入个人 `.zshrc` 由开发者决定，项目任务不自动修改全局 Shell 配置。

### 6.1 本机 Maven Settings 与 Nexus

当前电脑统一使用：

```text
/Users/kenny/Desktop/Workspace/Settings/3-Maven/settings/local_settings.xml
```

- `~/.m2/settings.xml` 已链接到该文件，因此仓库根目录的 `./mvnw` 默认使用它；显式验证时也可以传入 `--settings`。
- Settings 将全部依赖和插件请求通过 `http://127.0.0.1:18081/repository/maven-public`，并为 `maven-snapshots`、`maven-releases` 提供本机发布认证。
- Settings 使用独立的 `${user.home}/.m3/repository` 本地缓存。它包含凭据，文件权限必须保持 `600`，不得提交或把有效内容输出到日志。
- 当前只复用既有本机认证；后续应在 Nexus 中改为最小权限的专用部署账号，现有凭据不得用于生产或 CI。
- Nexus 当前由已有 Docker 容器提供，不加入 Code-MoR Compose。该容器目前把 `18081` 绑定到所有宿主机接口；这是现有环境的安全缺口，应在单独维护窗口改为仅绑定 `127.0.0.1`，本轮不擅自重建或改动该容器。

设置校验与本地 Snapshot 发布命令：

```bash
node scripts/verify-local-nexus-settings.mjs /Users/kenny/Desktop/Workspace/Settings/3-Maven/settings/local_settings.xml
cd paytech-build
./mvnw clean deploy
cd ../paytech-foundation
./mvnw clean deploy
```

`paytech-build` 必须先于首次 `paytech-foundation` 发布，以便 Nexus 中先存在 BOM 和 Parent。日常消费者只从 `maven-public` 读取，不直接指向 Hosted 仓库。

## 7. Compose Profile

第一阶段本地基础设施已经放在专用目录：

```text
local-environment/
├── compose.yaml
├── .env.example
├── README.md
└── mysql/init/
```

当前目录只实现 `base` Profile；`scheduler`、`observability` 及其配置目录仍在后续按需创建。实际命令和安全边界见仓库根目录下的 [`local-environment/README.md`](../local-environment/README.md)。

### 7.1 `base` Profile

日常最小依赖：

- MySQL 8.4.11；
- Nacos Server 3.1.2 Standalone，使用 MySQL 持久化；
- RabbitMQ 4.2.9 Management；
- Redis 8.2.1，Code-MoR 独立实例。

### 7.2 `scheduler` Profile

- XXL-Job Admin 3.4.2；
- 只在验证 `compensation_event` 兜底扫描、分片和 Handler 注册时启动；
- 正常请求的 `afterCommit` Dispatcher 不依赖 XXL-Job Admin 才能执行。

### 7.3 `observability` Profile

- OpenTelemetry Collector；
- Prometheus；
- Loki；
- Tempo；
- Grafana；
- Alertmanager。

该 Profile 用于多服务链路、消息重试和性能分析，不作为单服务日常启动条件。数据使用短保留期和有上限的本地卷。

### 7.4 延期 Profile

Vault 本地开发需要的 KMS、Secrets Manager 和 Object Storage 模拟器在开始实现 Vault 时单独设计。当前不为了未来可能使用 AWS 而预先加入 LocalStack、MinIO 或 SoftHSM。

## 8. 端口规划

### 8.1 基础设施

| 组件 | 容器端口 | 宿主机端口 | 说明 |
|---|---:|---:|---|
| MySQL | 3306 | 13306 | 避免与本机或其他项目 MySQL 冲突 |
| Redis | 6379 | 16379 | 不复用当前 `6379` 的 `redis-single` |
| RabbitMQ AMQP | 5672 | 5672 | 仅绑定 `127.0.0.1` |
| RabbitMQ Management | 15672 | 15672 | 本地管理界面 |
| Nacos Client HTTP | 8848 | 8848 | 本地配置和注册入口 |
| Nacos Client gRPC | 9848 | 9848 | 本机原生 Java Client 使用 |
| Nacos 3 Console | 8080 | 18088 | Nacos 3 独立控制台入口 |
| XXL-Job Admin | 8080 | 18090 | 避开现有 Nexus `18081` |
| OTLP gRPC | 4317 | 4317 | Java Agent -> Collector |
| OTLP HTTP | 4318 | 4318 | Java Agent -> Collector |
| Grafana | 3000 | 13000 | `3000` 保留给 requirements-site |
| Prometheus | 9090 | 19090 | 本地查询 |
| Loki | 3100 | 13100 | 本地日志后端 |
| Tempo | 3200 | 13200 | 本地 Trace 后端 |
| Alertmanager | 9093 | 19093 | 本地告警界面 |

### 8.2 Java 服务

| Deployable | 本地端口 |
|---|---:|
| API Gateway Application | 18080 |
| API Gateway Management | 19080 |
| Client & Contract | 18101 |
| Commerce | 18102 |
| Payment Orchestration | 18103 |
| Risk & Compliance | 18104 |
| Finance | 18105 |
| Platform Support | 18106 |
| Vault | 18107 |
| Operations Console BFF（Application 与 Management） | 18201 |
| Client / Partner Console BFF | 未分配；项目创建时分别确定，不复用一个 Business Portal BFF |

服务在 Nacos 中按稳定 `spring.application.name` 注册。端口只用于本机调试，不成为跨环境契约。

### 8.3 当前 Platform Support 运行切片

Platform Support 的自动门禁使用 `base` Profile 中的本地 MySQL。首次运行或 SQL 变化后，先按 [`platform-support-service/README.md`](../platform-support-service/README.md) 手工应用 Schema 与初始化 DML，再执行：

```bash
cd platform-support-service
./mvnw clean verify
```

实际启动可执行 JAR 前提供 Platform 专属 MySQL 配置和可达的 Nacos Discovery 地址；应用不读取 Bootstrap Secret，也不执行建表、Schema 变更或初始化 DML。PLT-I20、Operations PLT-I22、`health`、`liveness`、`readiness`、`info` 和 `prometheus` 共用 `18106` 私有 HTTP Listener，Readiness 检查当前 Snapshot 能否从数据库完整构造。应用注册名固定为 `platform-support-service`，不导入 Nacos Config；它不包含 Spring Security、X.509/mTLS、证书配置或本地异常处理器。完整变量、手工 SQL 和接口边界以 [`platform-support-service/README.md`](../platform-support-service/README.md) 为准。

### 8.4 当前 Operations Console 前端与 BFF

[`operations-console-bff`](../operations-console-bff/README.md) 默认通过私有 HTTP 监听 `127.0.0.1:18201`，没有 Servlet context path；`health`、Liveness、Readiness、`info` 和 `prometheus` 复用应用 Listener。绑定到其他地址需要显式运行配置与部署网络约束，不配置应用 TLS 或证书。

应用注册名固定为 `operations-console-bff`，匹配当前 Gateway Approved Target。Nacos Discovery 默认地址为 `127.0.0.1:8848`、Namespace 为 `mor-local`；可通过 `NACOS_SERVER_ADDR` / `NACOS_NAMESPACE` 调整，实际运行时必须与 Gateway 使用同一 Namespace。认证、菜单、人员与审计接口已经添加，但可用性还取决于 Platform 的显式认证配置、手工 SQL 与签名密钥前置。

BFF 本身没有数据库、Redis 或 RabbitMQ，生产调用通过 Nacos/LoadBalancer 到 Platform；默认 `auto` 使用随机端口及真实 HTTP 协议 Simulator、关闭 Nacos，不要求本地中间件。四个业务前缀已经配置 Foundation metadata-only 日志。

`operations-console-web` 的 Vite 预览绑定 `127.0.0.1:18301`；默认真实认证从 `https://operations-console.local.test` 等配置 Origin 通过 Nginx 进入。单独 `mor-test` 按用户明确要求使用 `http://localhost:28080`，配置约束见 test 运行手册；Vite 预览不会自动启用这一模式。HTTP 与默认 HTTPS 均使用 JSON Access/Refresh 双 Token。最新决定改用当前标签页 sessionStorage 保存两 Token 及绝对过期时刻，模块启动时恢复，覆盖此前仅内存/刷新需登录的限制；不使用 localStorage、Cookie、Web Locks 或 BroadcastChannel，不缓存密码或权限。存储遵循浏览器 page session 生命周期，复制/恢复标签页的行为由浏览器决定，应用不主动跨标签页共享 Token；恢复增量的验证与部署状态见 test 运行手册。

本轮已部署独立 `mor-test` Docker 环境，并在新 test 库显式应用 22 表 Schema、13 条 Route 目录及测试 Bootstrap/公钥初始化；真实登录、IAM 查询、Refresh 与退出链路已验收。初始化不由 Java 启动执行，普通重启不重建 Bootstrap。Platform 的 Vault Mock 和本地签名只允许显式开发/测试配置；具体边界、命令和测试结果见[首版实施记录](./plans/2026-09-04-operations-console-first-slice.md)及各仓 README。

## 9. 本地 MySQL 多数据库方案

一个 MySQL 容器承载以下独立数据库：

```text
mor_cct
mor_commerce
mor_payment
mor_risk
mor_finance
mor_platform
mor_vault
nacos_config
xxl_job
```

权限规则：

- 每个业务服务一个独立用户，只拥有对应 `mor_*` 数据库；
- Nacos 和 XXL-Job 使用独立技术账号；
- 服务账号没有 `SUPER`、`FILE`、跨数据库授权或创建其他用户权限；
- 禁止跨数据库外键、视图、查询、触发器和事务；
- 当前 `base` Compose Profile 初始化只创建数据库、账号、授权和中间件自身要求的技术表；
- 业务/Vault Schema 与初始化数据只能由开发者或部署人员按各服务 README 手工执行审核过的 SQL，Java 应用与测试不得自动创建。

## 10. Nacos 本地约定

- 模式：Standalone；持久化到 `nacos_config`，不使用嵌入式临时数据作为长期本地基线。
- Namespace：`mor-local`。
- 共享 Data ID：`mor-common.yaml`。
- 服务 Data ID：`{spring.application.name}.yaml`。
- Spring Boot 只保留连接 Nacos 所需的最小本地引导配置，并通过 `spring.config.import` 导入配置；不使用 `bootstrap.yml`。
- 密码、Token、证书和真实云凭据不进入 Nacos。
- `auto` 测试关闭 Nacos，通过测试动态配置连接 Simulator；需要的 MySQL/Redis/RabbitMQ 直接使用仓库本地服务。

## 11. RabbitMQ 本地约定

- VHost：`/mor-local`；使用独立本地账号，不使用 `guest/guest` 作为项目默认凭据。
- Topic Exchange：`topic_{source_system}_{business_subject}`。
- Consumer Queue：`cg_{consumer_system}_{consumer_subject}`。
- DLQ：`dlq_{consumer_system}_{consumer_subject}`；通过明确 DLX/Binding 路由。
- Queue、Exchange 和 Binding 由应用声明或受版本控制的拓扑初始化声明，禁止依赖开发者在管理界面手工点击。
- 本地也启用 Publisher Confirm、Persistent Message、Manual Ack 和 Delivery Limit，以验证真实可靠性语义。
- 单节点 Quorum Queue 只提供接口与行为验证，不提供节点级容灾。

## 12. Redis 本地约定

- Code-MoR 使用自己的 Redis 8.2.1 容器和 Volume，宿主机端口 `16379`。
- Key 使用 `{service}:{capability}:...` Namespace；Redis Starter 为 `RedisJsonTemplate`、`StringRedisTemplate` 与 Redisson 统一把 `{service}` 默认解析为 `spring.application.name`，服务可用 `redis.key-prefix` 显式覆盖。业务对象缓存优先使用不含 Java 类型信息的普通 JSON；字符串与计数器使用 `StringRedisTemplate`。新增 Key 不使用 `mor` 技术前缀，每个能力必须定义 TTL。
- Gateway Rate Limiter 与各服务 Key 进一步使用独立前缀，禁止 `KEYS *` 作为运行逻辑。
- Operations Gateway 不使用 Redis Starter、`StringRedisTemplate` 或 Redisson；它通过 Spring Data Redis Reactive + Lettuce Lua 实现 Rate Token Bucket 和 Concurrency ZSET Lease。
- 所有 Redis 自动测试连接 `127.0.0.1:16379` 的 `mor-local` Compose Redis，密码从 `.env.local` 对应的 `PAYTECH_REDIS_PASSWORD` 环境注入；不引入 Testcontainers，不连接 `6379`。自动化不得读取、输出或改写 `.env.local`。
- 每次 Gateway 测试运行生成唯一 Key Namespace，Setup/Teardown 只用 `SCAN` 删除本次已知前缀，禁止 `KEYS *`、`FLUSHDB` 或停止共享 Compose Redis。Redis 不可用用例连接未监听的 Loopback 端口，不影响其他本地进程。
- 本地密码来自 `.env.local`；应用日志不得输出完整连接串或密码。
- 现有宿主机 `6379` Redis 不参与 Code-MoR 开发、测试或故障演练。
- Gateway 的 Redis 启动前置、不回显密码的注入方式、Guard 状态约束、Access Log HMAC 配置与完整验证命令以 [`api-gateway/README.md`](../api-gateway/README.md) 为唯一运行手册；最终 Gateway 约束见 [`2026-08-30-operations-gateway-hardening-design.md`](../docs/superpowers/specs/2026-08-30-operations-gateway-hardening-design.md)，本文不复制密钥或大段命令。

## 13. 日常启动顺序

使用当前 Compose 时，推荐按以下阶段运行：

1. 检查 JDK/Maven、磁盘空间和端口占用；
2. 启动 `base`，等待 MySQL、RabbitMQ、Redis 和 Nacos Health Check 全部通过；
3. 在 Nacos 创建/核对 `mor-local` Namespace 和最小非敏感配置；
4. 只启动当前业务链需要的 Java 服务，例如 Checkout 链的 Gateway、Commerce、Payment、Risk 与 Simulator；
5. 调试补偿扫描时再启动 `scheduler`；
6. 调试跨服务 Trace、Metrics 或 Logs 时再启动 `observability`；
7. 停止时先退出 Java 服务，再执行 `docker compose down`，保留数据卷。

只检查 Platform Support 当前自动门禁时至少启动 `base` 中的 MySQL，并确认 `mor_platform` 已手工应用当前两份 SQL；测试 Profile 会关闭 Nacos Discovery，无需启动未被当前测试使用的其他 Java 服务。

不要把“本地全绿”建立在所有十一个 Deployable（六个核心服务、API Gateway、三个 Console BFF 和 Vault）必须同时常驻的前提上；当前仍只按需启动已经实现且属于当前业务链的应用，未创建的核心服务、其他 Console BFF 和 Vault 不因此获得实现授权，已初始化 Operations BFF 也不因此获得业务能力授权。

## 14. 健康检查与验证

基础设施至少验证：

- MySQL 可登录、九个数据库存在、服务账号不能访问其他业务数据库；
- Redis 只在 `16379` 可访问，项目 Key 与现有 `6379` 实例完全隔离；
- RabbitMQ VHost、账号、Exchange/Queue/DLQ 和 Confirm/Ack 行为正确；
- Nacos Server 可用，Client 能通过 `8848/9848` 注册、发现并导入配置，控制台通过 `18088` 访问；
- XXL-Job 只能唤醒本服务 Handler，不能跨库读取 `compensation_event`；
- OTel Collector 可接收 OTLP，Grafana 能分别查询 Metrics、Logs 和 Traces；
- Maven Settings 校验通过，`maven-public` 能从空缓存解析已发布的 `com.paytech` Snapshot，两个 Hosted 仓库按版本类型接收发布；
- 所有 Code-MoR Compose 宿主机监听只绑定 `127.0.0.1`；现有 Nexus 的绑定缺口按第 6.1 节单独治理。

Java 服务至少验证：

- `java -version` 与 Maven Runtime 均为 Java 25；
- Actuator Readiness 在依赖未就绪时失败，在核心依赖可用时恢复；
- Nacos 注册名、端口和 Metadata 正确；
- 内部 `@HttpExchange` 调用经过 Nacos + LoadBalancer，不经过外部 Gateway；
- RabbitMQ 发布等待 Confirm，消费者提交本地事务后 Manual Ack；
- 日志含 Request/Trace ID，且没有密码、Token 或敏感明文。
- Nginx 终止公网 TLS、覆盖一个单值合法 `X-Real-IP`，并且只有 Nginx 私网可到达 API Gateway；Gateway 从该值构造唯一可信 Client IP 上下文供 Guard 和 Access Log 共用，转发 BFF 前删除 `Forwarded`、逐项列举的 X-Forwarded 系列 Header 与 `X-Real-IP`。
- API Gateway Application 以私有 HTTP 监听 `18080`，Management 只在 `19080` 暴露 `health` / `info` / `prometheus`；Liveness 只含 `livenessState`，Readiness 只含 `readinessState`、`gatewayBootstrap` 和短超时 `gatewayRedis`，不把 Operations BFF 或 JWKS 可用性纳入就绪门禁。Gateway 到 BFF 与 JWKS 同样使用内部 HTTP，不加载应用证书或 Trust Anchor。
- Platform Support 以私有 HTTP 监听 `18106` 并注册到 Nacos；Liveness 只反映进程状态，Readiness 要求当前 Gateway Snapshot 能从手工初始化的数据库完整构造，健康详情不对外披露。Operations PLT-I22 从同库读取 JWKS，但不加入 Gateway 的就绪门禁。

## 15. 自动测试与本地共享环境的边界

`auto` 与 `manual` 都复用本文的仓库本地中间件，但不得连接真实业务环境：

- 测试自身不调用 Docker API，不启动、不停止、不重建容器；缺少依赖时快速失败并指向本文件；
- MySQL 固定连接 `127.0.0.1:13306`，每个服务只使用自己的账号和数据库，DDL/DML 由开发者手工执行；
- Redis 固定连接 `127.0.0.1:16379`，每次运行使用唯一 Namespace，并仅以 `SCAN` 清理本次前缀；
- RabbitMQ 使用 `/mor-local` 和测试专属 Queue/Binding 名称；Nacos 在 `auto` 中关闭；
- Platform Support 从真实随机端口 MVC/HTTP 入口经过 Controller、Domain Service、Repository、Mapper 到本地 MySQL；简单查询不强制增加 Processor，也不使用 Mockito 或 Testcontainers；
- 内部服务/Provider 仍通过有状态 Simulator 提供真实 HTTP/MQ 协议，不要求同时启动全部业务服务。

详细规则见 [UNIT_TEST_GUIDE.md](./UNIT_TEST_GUIDE.md)。

## 16. 当前不执行的动作

`base` Profile 的首次本机烟测已经单独获准并完成：四个容器均通过健康与隔离检查，随后已关闭并保留持久卷。以下动作仍未执行，也不会被这次烟测自动授权：

- 清理现有 Docker Images、Containers、Volumes 或 Build Cache；
- 修改 Docker Desktop 全局资源设置；
- 修改个人 `.zshrc`、IDE 全局配置或系统默认 JDK；
- 创建尚未授权的核心业务服务 Java 模块、Console BFF/前端或业务表；Operations Gateway、Platform 当前已列明的 Phase A/PLT-I20/Operations PLT-I22 切片与 Operations BFF 应用基础不在本条禁止范围内；
- 创建 `scheduler`、`observability`、Vault 模拟依赖等后续 Compose Profile；
- 创建/发布云资源、CI/CD 或生产部署。

这些动作需要后续明确的实施请求。
