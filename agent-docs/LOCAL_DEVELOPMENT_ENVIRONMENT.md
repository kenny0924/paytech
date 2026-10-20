# Code-MoR 本地开发环境方案

最后更新：2026-09-05

状态：Confirmed local environment topology and operating guide。本页只维护当前 Mac 的资源基线、`mor-local` 拓扑、端口、依赖准备、安全隔离和日常操作。组件实现、验证、`mor-test` 部署及迁移状态统一见 [`CURRENT_IMPLEMENTATION.md`](./CURRENT_IMPLEMENTATION.md) 和相应仓库运行手册，不在此复制。

应用传输边界由 [`2026-09-02-mvc-web-logging-and-internal-http-simplification-design.md`](../docs/superpowers/specs/2026-09-02-mvc-web-logging-and-internal-http-simplification-design.md) 与 [`APPLICATION_ARCHITECTURE.md`](./APPLICATION_ARCHITECTURE.md) 拥有；本地环境只为这些边界分配 Loopback 端口和隔离网络。

## 1. 目标

本地环境优先满足：

- 在当前 Apple Silicon Mac 上快速调试单个服务和一条跨服务业务链；
- 基础设施版本可重复、可隔离，不污染或复用开发者现有容器数据；
- Java 服务保留 IDE 断点、热重启和低成本重建体验；
- 不要求日常同时运行全部后端 Deployable（六个核心服务、API Gateway、三个 Console BFF 和 Vault）和完整可观测栈；
- 自动测试统一复用仓库 `local-environment` 中的 MySQL、Redis 与 RabbitMQ；测试不使用 Testcontainers，也不负责启动、停止或重建中间件。

## 2. 当前电脑实测基线

以下硬件与工具事实最后只读核对于 2026-09-05；执行安装、清理或资源调整前必须重新查询，不能把本页当实时监控。

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
| 数据盘 | 容量约 460 GiB；2026-09-05 根卷查询约 45 GiB 可用，执行大规模构建前重新运行 `df -h /` |
| Docker 数据 | 不在文档冻结易变用量；用 `docker system df` 只读核对，任何清理仍需单独确认 |
| 宿主保留端口 | 既有 Nexus 使用 `18081`、既有 Redis 使用 `6379`；实时容器以 `docker ps` 为准，`mor-local` 不复用二者数据 |

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

`docker system df` 显示“可回收”不代表可以由 Code-MoR 任务自动删除；镜像、停止容器、卷和缓存可能仍属于其他项目，任何清理都需要解析精确归属并单独确认。

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

- `~/.m2/settings.xml` 已链接到该文件，因此唯一 Wrapper `paytech/mvnw` 默认使用它；显式验证时也可以传入 `--settings`。
- Settings 将全部依赖和插件请求通过 `http://127.0.0.1:18081/repository/maven-public`，并为 `maven-snapshots`、`maven-releases` 提供本机发布认证。
- Settings 使用独立的 `${user.home}/.m3/repository` 本地缓存。它包含凭据，文件权限必须保持 `600`，不得提交或把有效内容输出到日志。
- 当前只复用既有本机认证；后续应在 Nexus 中改为最小权限的专用部署账号，现有凭据不得用于生产或 CI。
- Nexus 当前由已有 Docker 容器提供，不加入 Code-MoR Compose。该容器目前把 `18081` 绑定到所有宿主机接口；这是现有环境的安全缺口，应在单独维护窗口改为仅绑定 `127.0.0.1`，本轮不擅自重建或改动该容器。

设置校验与本地 Snapshot 发布命令：

```bash
node scripts/verify-local-nexus-settings.mjs /Users/kenny/Desktop/Workspace/Settings/3-Maven/settings/local_settings.xml
cd paytech
./mvnw clean deploy -pl build/paytech-dependencies,build/paytech-parent
./mvnw clean deploy -f foundation/pom.xml
```

`paytech/build` 的 BOM 与 Parent 必须先于首次 Foundation（`paytech/foundation`）发布，以便 Nexus 中先存在 BOM 和 Parent。日常消费者只从 `maven-public` 读取，不直接指向 Hosted 仓库。

## 7. Compose Profile

第一阶段本地基础设施已经放在专用目录：

```text
local-environment/
├── compose.yaml
├── .env.example
├── README.md
└── mysql/init/
```

当前目录已实现 `base`、`storage`、`mail`、`scheduler`、`vault` 与 `analytics`（独立文件 `compose.analytics.yaml`）Profile；`observability` 及其配置目录仍在后续按需创建。实际命令和安全边界见仓库根目录下的 [`local-environment/README.md`](../local-environment/README.md)。

### 7.1 `base` Profile

日常最小依赖：

- MySQL 8.4.11；
- Nacos Server 3.1.2 Standalone，使用 MySQL 持久化；
- RabbitMQ 4.2.9 Management；
- Redis 8.2.1，Code-MoR 独立实例。

环境事实登记（2026-10-17/18，V2 搬迁第 01、09 步）：

- RabbitMQ 以只读方式挂载 `local-environment/rabbitmq/enabled_plugins` 到 `/etc/rabbitmq/enabled_plugins`，启用 `rabbitmq_management`、`rabbitmq_prometheus` 与 `rabbitmq_consistent_hash_exchange`。`paytech-messaging` 的分区订阅依赖一致性哈希插件，缺插件时应用启动失败并提示。已运行的容器经 `rabbitmq-plugins enable` 在线启用，重建后由该挂载保持。
- MySQL 启动参数增加 `--binlog-format=ROW`、`--binlog-row-image=FULL`、`--gtid-mode=ON`、`--enforce-gtid-consistency=ON`，供 `analytics` Profile 的 Flink CDC 读取；GTID 参数在 MySQL 容器下次重建后生效。初始化脚本另建 CDC 账号 `cdc_reader`：全局只有 `REPLICATION SLAVE, REPLICATION CLIENT`，`SELECT` 只授予白名单库。

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

### 7.4 `vault` Profile（2026-10-14 获准）

- 只含 Vault Server 一个容器（`mor-local/vault-service:development`，`FROM scratch`、非 root、只读根文件系统、去除全部 capability、内存上限 256MB），复用测试环境的 Dockerfile 与就绪探针；
- 连接 `mysql:3306/mor_vault` 与 `nacos:8848`，端口只发布到 `127.0.0.1:18107`，在 Nacos `mor-local` 注册 `127.0.0.1:18107`，供宿主机上的 Java 服务发现；
- LOCAL AES 根密钥、密钥引用与 Nacos 账号来自本机全链路材料，由 `local-environment/vault/vault.sh build` 写入不提交的 `vault/private/vault.env`（0600）；访问策略只从 `mor_vault` 策略表读取（[dml-local.sql](../requirements/20261014/operations-console-Vault数据类型与手动加解密/db/vault/dml-local.sql) 初始化），`vault/policy.local.json` 仅作转写来源、不再读取；管理调用方 `VAULT_ADMIN_CALLERS=operations-console-bff`；
- 与宿主机进程方式（`run-service.sh vault`）互斥，同一时间只运行一种；不是生产 KMS，也不改变 Vault 的生产缺口。

### 7.5 `analytics` Profile（2026-10-17，V2 搬迁第 09 步）

- 定义在 `local-environment/compose.analytics.yaml`，需与 `compose.yaml` 一起以 `-f compose.yaml -f compose.analytics.yaml --profile analytics` 使用；
- 服务：`doris-fe`、`doris-be`（Doris 3.0.8 单节点）、一次性 `doris-init`（root 密码、注册 BE、建库 `mor_data_service`、只读账号 `data_service_app`）、`flink-jobmanager`、`flink-taskmanager`（Flink 1.20.5 + Flink CDC 3.7.0，本地镜像 `mor-local/flink-cdc:1.20.5-cdc3.7.0`）与一次性 `flink-cdc-submit`；
- 端口只绑定 `127.0.0.1`，见 8.1 节；卷 `mor-local-doris-fe-meta`、`mor-local-doris-be-storage`；
- 实测空闲内存约 2.7 GiB（上限合计 4.75 GiB），镜像使 Docker 镜像存储增长约 14 GB；不作为日常启动条件，`mor-test` 未接入。运行手册见 [`local-environment/README.md`](../local-environment/README.md) 与 [`paytech/data-service/README.md`](../paytech/data-service/README.md)。

### 7.6 可选安全基础设施 Profile

`mor-local` 不默认提供生产等价的 KMS、Secrets Manager 或 Object Storage。若某项已批准的 Vault/文件能力需要本地模拟器，应先定义威胁边界、持久卷、端口、测试用途和与生产差异，再显式增加独立 Profile；不得为了未来可能使用某个云厂商预装 LocalStack、MinIO 或 SoftHSM。Vault 当前实现和生产缺口见状态台账。

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
| MinIO S3 API（`storage` Profile，HTTPS） | 9000 | 19400 | Platform 入驻文件本机存储；自签名证书，仅绑定 `127.0.0.1` |
| MinIO Console（`storage` Profile） | 9001 | 19401 | 本机存储管理界面 |
| Doris FE MySQL 协议（`analytics` Profile） | 9030 | 19030 | 仅绑定 `127.0.0.1`；`data_service_app` 只读查询 |
| Doris FE HTTP（`analytics` Profile） | 8030 | 18030 | 仅绑定 `127.0.0.1` |
| Flink Web UI（`analytics` Profile） | 8081 | 18091 | 仅绑定 `127.0.0.1`；避开 Nexus `18081` |

### 8.2 后端应用

V2 服务端口（2026-10-17/18 搬迁后，取自各 `paytech/<service>/paytech-<service>-app/src/main/resources/application.yml` 与 [服务目录](../paytech/tools/instance-manifest/src/main/resources/catalog/services.yaml)）。管理端口为业务端口 + 1000，Actuator 只在管理端口暴露；“—”表示沿用业务端口：

| 服务（`spring.application.name`） | 业务端口 | 管理端口 | Context Path | 来源工程 |
|---|---:|---:|---|---|
| API Gateway（`api-gateway`） | 18080 | 19080 | — | api-gateway |
| client-master（`client-master-service`） | 18101 | 19101 | — | client-contract-service |
| acquiring（`acquiring-service`） | 18102 | 19102 | `/commerce` | commerce-service |
| payment（`payment-service`） | 18103 | 19103 | `/payment` | payment-orchestration-service |
| risk-decision（`risk-decision-service`） | 18104 | 19104 | — | risk-compliance-service |
| ledger（`ledger-service`） | 18105 | 19105 | — | finance-service |
| Platform Support（`platform-support-service`） | 18106 | — | `/platform-support` | platform-support-service |
| Vault（`vault-service`，Go） | 18107 | — | — | vault-service |
| commercial（`commercial-service`） | 18108 | 19108 | — | client-contract-service |
| counterparty（`counterparty-service`） | 18109 | 19109 | — | commerce-service |
| channel-connector（`channel-connector-service`） | 18110 | 19110 | — | payment-orchestration-service |
| card（`card-service`） | 18111 | 19111 | — | payment-orchestration-service |
| compliance（`compliance-service`） | 18112 | 19112 | — | risk-compliance-service |
| billing（`billing-service`） | 18113 | 19113 | — | finance-service |
| account（`account-service`） | 18114 | 19114 | — | finance-service |
| reconciliation（`reconciliation-service`） | 18115 | 19115 | — | finance-service |
| tax（`tax-service`） | 18116 | 19116 | — | finance-service |
| payout（`payout-service`，未建工程） | 18117 | — | — | 预留 |
| dispute（`dispute-service`，只有模型） | 18118 | — | — | 预留 |
| fx（`fx-service`，未建工程） | 18119 | — | — | 预留 |
| data-service（`data-service`） | 18120 | 19120 | — | 新建 |
| workflow-approval（`workflow-approval-service`，未建工程） | 18121 | — | — | 预留 |
| Operations Console BFF（`operations-console-bff`） | 18201 | 19201 | — | operations-console-bff |
| Client Console BFF（`client-console-bff`） | 18202 | 19202 | — | client-console-bff |
| Partner Console BFF（`partner-console-bff`，未建工程） | 18203 | — | — | 预留 |

搬迁前的 Client & Contract、Commerce、Payment Orchestration、Risk & Compliance、Finance 单体分别占用 18101–18105，`main` 分支并行期间仍按旧端口运行；同一台电脑上不得同时以默认端口启动新旧两套。各服务的启动参数、健康组与依赖见对应 `paytech/<service>/README.md`。

服务在 Nacos 中按稳定 `spring.application.name` 注册。端口只用于本机调试，不成为跨环境契约。

### 8.3 组件运行手册

本页只分配端口和本地依赖，不复制应用端点、健康组、认证模式、Token 存储、已应用表数或验收结果。启动或验证具体组件时读取其仓库 README：

- [API Gateway](../paytech/api-gateway/README.md)
- [Platform Support](../paytech/platform-support/README.md)
- [Operations Console BFF](../paytech/operations-console-bff/README.md)
- [Operations Console Web](../operations-console-web/README.md)
- [独立 `mor-test` 环境](../test-environment/README.md)

跨组件当前实现、验证和部署范围由 [`CURRENT_IMPLEMENTATION.md`](./CURRENT_IMPLEMENTATION.md) 汇总。组件手册与本页端口冲突时，先确认是本机覆盖还是文档漂移，不得静默启动到未分配端口或改变安全协议。

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

环境事实登记（V2 搬迁，design/10 §5.1）：每个 V2 服务一个库与账号，账号只授本库权限，密码变量为 `MOR_<SERVICE>_DB_PASSWORD`。

| 服务 | 数据库 | 账号 | 替代的旧库 |
|---|---|---|---|
| client-master | `mor_client_master` | `client_master_app` | `mor_cct` |
| commercial | `mor_commercial` | `commercial_app` | `mor_cct` |
| acquiring | `mor_acquiring` | `acquiring_app` | `mor_commerce` |
| counterparty | `mor_counterparty` | `counterparty_app` | `mor_commerce` |
| payment | `mor_payment`（沿用） | `payment_app` | — |
| channel-connector | `mor_channel_connector` | `channel_connector_app` | `mor_payment` 渠道表 |
| card | `mor_card` | `card_app` | `mor_payment` 卡 4 表 |
| risk-decision | `mor_risk_decision` | `risk_decision_app` | `mor_risk` |
| compliance | `mor_compliance` | `compliance_app` | `mor_risk` |
| ledger | `mor_ledger` | `ledger_app` | `mor_finance` |
| billing | `mor_billing` | `billing_app` | `mor_finance` |
| account | `mor_account` | `account_app` | `mor_finance` |
| reconciliation | `mor_reconciliation` | `reconciliation_app` | `mor_finance` |
| tax | `mor_tax` | `tax_app` | `mor_finance` |

- 旧库 `mor_cct`、`mor_commerce`、`mor_risk`、`mor_finance` 及其账号在 `refactor-main` 与 `main` 并行期间保留，初始化脚本只追加不删除。
- `mor_platform`、`mor_vault`、`nacos_config`、`xxl_job`、`paytech_foundation_test` 不变；`mor_dispute`、`mor_payout`、`mor_fx`、`mor_workflow_approval` 尚不创建。
- 搬迁后新增的库在初始化脚本中按“密码变量存在才创建”的可选规则创建，因为 `compose.yaml` 的 `mysql.environment` 尚未透传这些变量；各库是否已在本机建出、建了哪些表，以 [`CURRENT_IMPLEMENTATION.md`](./CURRENT_IMPLEMENTATION.md) 为准。
- 各 `paytech/<service>/paytech-<service>-test/src/test/resources/init/ddl.sql` 自带建库、建账号（最小权限 `SELECT, INSERT, UPDATE, DELETE`）与全部建表语句，由开发者以 root 人工执行。

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

环境事实登记（V2 搬迁第 01、08、12x 步，[design/08](../requirements/20261017/architecture-V2骨架与模块搬迁/design/08-messaging-module.md)）：`paytech-messaging` 在 `/mor-local` 上声明的领域事件拓扑如下。上面的 `topic_*` / `cg_*` 命名描述 `main` 分支既有拓扑，V2 命名约定的正文修订另行处理。

| 对象 | 名称 | 属性 |
|---|---|---|
| 发布交换机 | `<producer>.events`，如 `payment.events`、`compliance.events`、`billing.events` | Durable Fanout |
| 订阅哈希交换机 | `<consumer>.<producer>.hash`，如 `acquiring.payment.hash` | `x-consistent-hash`，以交换机到交换机绑定接到发布交换机，路由键为 aggregateId |
| 分区队列 | `<consumer>.<producer>.p0` … `p<N-1>`（默认 N=4，`messaging.consumers.<producer>.partitions`） | Quorum、Single Active Consumer、`x-delivery-limit=5` |
| 死信 | `<consumer>.<producer>.dlx` → `<consumer>.<producer>.dlq` | — |

- 第 08 步起 payment 与 compliance 只发布新信封，代码不再声明 `topic_payment_result`、`topic_risk_client_review` 与 `cg_commerce_payment_result*`、`cg_acquiring_payment_result*`、`cg_cct_client_review*`、`cg_client_master_client_review*`；本机 Broker 上残留的这些对象需人工删除，命令与时机见 [`CURRENT_IMPLEMENTATION.md`](./CURRENT_IMPLEMENTATION.md)。
- 分区队列是持久队列，其他服务测试发布的事件会留在其中。消费方 `-test` 模块以 test 作用域引入 `paytech-messaging` 的 test-jar，并导入 `PartitionQueueBaselineConfiguration`，在监听容器启动前清空本服务订阅的分区队列与 DLQ。

## 12. Redis 本地约定

- Code-MoR 使用自己的 Redis 8.2.1 容器和 Volume，宿主机端口 `16379`。
- Key 使用 `{service}:{capability}:...` Namespace；Redis Starter 为 `RedisJsonTemplate`、`StringRedisTemplate` 与 Redisson 统一把 `{service}` 默认解析为 `spring.application.name`，服务可用 `redis.key-prefix` 显式覆盖。业务对象缓存优先使用不含 Java 类型信息的普通 JSON；字符串与计数器使用 `StringRedisTemplate`。新增 Key 不使用 `mor` 技术前缀，每个能力必须定义 TTL。
- Gateway Rate Limiter 与各服务 Key 进一步使用独立前缀，禁止 `KEYS *` 作为运行逻辑。
- Operations Gateway 不使用 Redis Starter、`StringRedisTemplate` 或 Redisson；它通过 Spring Data Redis Reactive + Lettuce Lua 实现 Rate Token Bucket 和 Concurrency ZSET Lease。
- 所有 Redis 自动测试连接 `127.0.0.1:16379` 的 `mor-local` Compose Redis；固定测试配置由 Spring 加载 `application-auto.yml`，隔离本地测试账号凭据遵守 `UNIT_TEST_GUIDE.md` 第 7.5 节，并允许 `PAYTECH_REDIS_PASSWORD` 环境覆盖。已有实例使用自定义密码时，由开发者安全注入相同值，不自动修改共享服务密码。不引入 Testcontainers，不连接 `6379`；除下述明确授权的范围外，自动化不得读取 `.env.local`，所有场景均不得输出或改写该文件。见 [2026-09-08 决策](./decisions/2026-09-08-middleware-auto-test-configuration.md)。
- **Platform Support Mock 测试的授权例外（2026-09-17）：** 用户明确授权读取 `.env.local` 用于本地测试连接，并要求 Mock 启动无需另配密码。仅该服务测试侧的 `mock` Profile 可自动读取 `MOR_PLATFORM_DB_PASSWORD` 和 `PAYTECH_REDIS_PASSWORD`，不导入其他字段、不执行 Shell、不输出或持久化值；显式覆盖优先。此例外不适用于正式应用、manual Profile 或其他服务。入口见 [测试模块说明](../paytech/platform-support/paytech-platform-support-test/README.md)，实际验证见[当前台账](CURRENT_IMPLEMENTATION.md#platform-api-credential-validation-tests-20260917)。
- 每次 Gateway 测试运行生成唯一 Key Namespace，Setup/Teardown 只用 `SCAN` 删除本次已知前缀，禁止 `KEYS *`、`FLUSHDB` 或停止共享 Compose Redis。Redis 不可用用例连接未监听的 Loopback 端口，不影响其他本地进程。
- 本地密码来自 `.env.local`；应用日志不得输出完整连接串或密码。
- 现有宿主机 `6379` Redis 不参与 Code-MoR 开发、测试或故障演练。
- Gateway 的 Redis 启动前置、不回显密码的注入方式、Guard 状态约束、Access Log HMAC 配置与完整验证命令以 [`api-gateway/README.md`](../paytech/api-gateway/README.md) 为唯一运行手册；最终 Gateway 约束见 [`2026-08-30-operations-gateway-hardening-design.md`](../docs/superpowers/specs/2026-08-30-operations-gateway-hardening-design.md)，本文不复制密钥或大段命令。

## 13. 日常启动顺序

使用当前 Compose 时，推荐按以下阶段运行：

1. 检查 JDK/Maven、磁盘空间和端口占用；
2. 启动 `base`，等待 MySQL、RabbitMQ、Redis 和 Nacos Health Check 全部通过；
3. 在 Nacos 创建/核对 `mor-local` Namespace 和最小非敏感配置；
4. 只启动当前业务链需要的 Java 服务，例如 Checkout 链的 Gateway、Commerce、Payment、Risk 与 Simulator；
5. 调试补偿扫描时再启动 `scheduler`；
6. 调试跨服务 Trace、Metrics 或 Logs 时再启动 `observability`；
7. 停止时先退出 Java 服务，再执行 `docker compose down`，保留数据卷。

运行组件测试前，只启动该组件 README 明确列出的本地依赖，并确认所需 Schema/DML 已由操作员手工应用。不要把“本地全绿”建立在全部后端 Deployable 同时常驻的前提上；本地环境存在端口或数据库不代表对应应用已经实现、获准或部署。

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
- 每个组件的精确 Listener、Health Group、依赖检查和验证命令以该组件 README 为准；本页只检查其端口仍位于规划范围、只绑定批准接口且没有与其他本地进程冲突。

## 15. 自动测试与本地共享环境的边界

`auto` 与 `manual` 都复用本文的仓库本地中间件，但不得连接真实业务环境：

- 测试自身不调用 Docker API，不启动、不停止、不重建容器；缺少依赖时快速失败并指向本文件；
- MySQL 固定连接 `127.0.0.1:13306`，每个服务只使用自己的账号和数据库，Schema DDL 与基础设施初始化 DML 由开发者手工执行；每例业务 Fixture 由测试 Mocker 准备。全局清理仅允许作用于测试专用数据库/Schema，不能擦除共享开发数据；
- Redis 固定连接 `127.0.0.1:16379`，每次运行使用唯一 Namespace，并仅以 `SCAN` 清理本次前缀；
- RabbitMQ 使用 `/mor-local` 和测试专属 Queue/Binding 名称；Nacos 在 `auto` 中关闭；
- 测试定位 `local-environment/` 的唯一锚点是系统属性 `paytech.local-environment.dir`：`paytech-parent` 默认取 `${maven.multiModuleProjectDirectory}/../local-environment`，并经 Surefire / Failsafe 透传。在 git worktree 中运行时，worktree 内没有未跟踪的 `.env.local`，需显式传入 `-Dpaytech.local-environment.dir=<主工作区>/local-environment`；IDE 运行测试同样以该系统属性覆盖。
- 有数据库的业务服务从真实随机端口入口经过其生产 Controller、可选 Processor、Domain Service、Repository/Mapper 到本地 MySQL；简单查询不强制增加 Processor，也不使用 Mockito 或 Testcontainers；
- 外部 HTTP 通过独立测试服务返回按请求配置的 DTO，保留本服务真实 Client 与协议；不强制完整外部状态机，不要求同时启动全部业务服务。MQ 使用真实本地 Broker 与测试消费者。两级 BeforeEach、全局 AfterEach 和同一环境串行规则见现行测试规范。

详细规则见 [UNIT_TEST_GUIDE.md](./UNIT_TEST_GUIDE.md)。

### 15.1 `.env.example` 变量登记（V2 搬迁）

搬迁各步向 `local-environment/.env.example` 增加的变量，值都是仓库隔离的本地示例，复制到 `.env.local` 后必须替换：

| 变量 | 用途 | 引入步骤 |
|---|---|---|
| `MOR_CLIENT_MASTER_DB_PASSWORD`、`MOR_COMMERCIAL_DB_PASSWORD` | client-master、commercial 库账号 | 04 |
| `MOR_ACQUIRING_DB_PASSWORD`、`MOR_COUNTERPARTY_DB_PASSWORD` | acquiring、counterparty 库账号 | 03 |
| `MOR_CHANNEL_CONNECTOR_DB_PASSWORD` | channel-connector 库账号 | 05 |
| `MOR_RISK_DECISION_DB_PASSWORD`、`MOR_COMPLIANCE_DB_PASSWORD` | risk-decision、compliance 库账号 | 06 |
| `MOR_LEDGER_DB_PASSWORD`、`MOR_BILLING_DB_PASSWORD`、`MOR_ACCOUNT_DB_PASSWORD`、`MOR_RECONCILIATION_DB_PASSWORD`、`MOR_TAX_DB_PASSWORD` | Finance 五个服务库账号 | 07 |
| `MOR_CARD_DB_PASSWORD` | card 库账号 | 12 |
| `MOR_CDC_DB_PASSWORD` | CDC 账号 `cdc_reader` | 09 |
| `MOR_DORIS_ROOT_PASSWORD`、`MOR_DATA_SERVICE_DORIS_PASSWORD` | `analytics` Profile 的 Doris root 与 `data_service_app` | 09 |
| `PAYTECH_REDIS_PASSWORD` | Redis 密码（取代 `MOR_REDIS_PASSWORD`，Compose 插值与测试都读该键） | 既有，搬迁中统一使用 |

`.env.local` 若仍只有旧键 `MOR_REDIS_PASSWORD`，Compose 组合命令需临时导出 `PAYTECH_REDIS_PASSWORD`。测试库账号密码以 `.env.local` 为准，未设置时取各 `application-mock.yml` 的仓库默认值。

## 16. 本地环境不自动授权的动作

读取或使用本地环境不授权以下额外变更：

- 清理现有 Docker Images、Containers、Volumes、Build Cache 或其他项目资源；
- 修改 Docker Desktop 全局资源、个人 Shell/IDE 配置或系统默认 JDK；
- 创建未获准的业务应用、业务表或空模块；
- 新增或启动尚未配置的 `scheduler`、`observability`、Vault 模拟器等 Profile；
- 创建/发布云资源、CI/CD 或生产部署。

这些动作需要对应任务的明确授权；已经执行到何处只从状态台账和操作证据判断。
