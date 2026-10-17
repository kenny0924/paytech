# 01 · `paytech/` 骨架与模块约定

依据 D09（修订）、D36（修订）、Q1、Q5。本约定只对 `paytech/` 下的模块生效；旧目录在搬空前继续遵守现行文档。

## 1. 目录与 Reactor

```text
paytech/
├── pom.xml                      # 单一 Reactor 聚合 POM（packaging=pom，只列模块与顺序）
├── build/
│   ├── paytech-dependencies/    # BOM（从 paytech-build 搬入）
│   └── paytech-parent/          # 父 POM（从 paytech-build 搬入）
├── foundation/                  # 13 个现有 Foundation 模块 + 新增 paytech-messaging
├── <service>/                   # 每个逻辑服务一个目录，见第 2 节
└── tools/                       # 迁移执行器、实例清单校验器、ArchUnit 共享规则库
```

- 仓库根不再放业务 `mvnw`；`paytech/mvnw` 为唯一 Wrapper（3.3.4 / Maven 3.9.16，统一固定 `distributionSha256Sum`）。
- `paytech-parent` 对子模块使用**真实相对路径**（`<relativePath>../../build/paytech-parent/pom.xml</relativePath>`），不再用空 `relativePath/` 从本地仓库解析。
- 每个服务目录有自己的聚合 POM，承载该服务的 Enforcer `bannedDependencies` 与 Surefire 设置；全局父 POM 不放服务级禁令。

## 2. 服务模块约定

每个逻辑服务固定四个模块，artifactId 前缀 `paytech-<service>`：

| 模块 | 职责 | 允许依赖 | 禁止 |
|---|---|---|---|
| `-api` | `@HttpExchange` 接口、Request / Response DTO、错误码枚举、事件 Payload 类型（对外发布的事件 Schema 类） | `paytech-common-core`、spring-web、validation | 任何 core、任何其他服务的 api |
| `-core` | 业务实现：`core/dal`、`domain`、`domainservice`、`service/processor`、`integration` 端口接口与 HTTP 实现、MQ 消费者 Handler、补偿 Handler；**不含启动类、不含 Controller** | 本服务 `-api`、Foundation、其他服务的 `-api`（经 HTTP Client）、`paytech-messaging` | 其他服务的 `-core`、`-app`；Spring Boot 启动 |
| `-app` | 唯一可执行：Spring Boot 启动类、Controller（协议适配）、DataSource / 事务管理器 / `@MapperScan` / TypeHandler 注册、HTTP Client 装配、MQ 连接与队列声明、补偿事件与 XXL-Job 配置、`application.yml` | 本服务 `-core`、`-api`、mvc-web-starter 等 | 其他服务的 core |
| `-test` | 全部 `auto` / `manual` 测试、Builder / Mocker / Checker / TestMapper、外部 DTO Mock | 本服务 `-app`、paytech-test-support | 其他服务的 test 工具（需共享的放 test-support） |

与现行工程的差别只有一处：现在的 `-web` 同时承担 core 与 app，拆为两者。Controller 留在 app，Processor 及以下进 core。

**服务间调用唯一方式**：注入对方 `-api` 接口，由 `-app` 用 `HttpServiceClientFactory` 装配 HTTP Client。进程内本地 Bean 绑定只允许出现在 `-test` 的组合测试里，`-app` 中禁止（ArchUnit 规则 R6）。

## 3. 服务清单、坐标与端口

详表见 [03-module-and-table-ownership.md](./03-module-and-table-ownership.md) 第 1 节。约定：

- 目录与 artifactId 用英文服务名（术语表 D31）：`acquiring`、`consumer`、`payment`、`channel-connector`、`card`、`client-master`、`commercial`、`risk-decision`、`compliance`、`billing`、`account`、`ledger`、`reconciliation`、`tax`、`payout`、`dispute`、`fx`、`data-service`、`platform-support`、`workflow-approval`、`api-gateway`、`client-console-bff`、`operations-console-bff`、`vault`（Go，仅 Java Client 进 Reactor）。
- `spring.application.name`：业务服务为 `<service>-service`（如 `ledger-service`、`card-service`，沿用现有 `platform-support-service` 风格，便于 `@ImportHttpServices` 服务发现名统一）；Gateway、BFF、Platform、Vault 保持现名。Nacos dataId、XXL-Job 执行器名、补偿 `instance-id` 前缀、Redis key 前缀、日志 service 字段全部取自它。
- 数据库名 `mor_<service_snake>`（`mor_commerce`→`mor_acquiring`、`mor_cct`→`mor_client_master`、`mor_risk`→`mor_risk_decision`、`mor_finance`→`mor_ledger`；`mor_payment`、`mor_platform`、`mor_vault` 保留；`data-service` 的库在 Doris）；每库一份 `t_compensation_event` 与 `t_consumed_event`。
- 端口：已有服务保留现端口（CCT→client-master 18101、Commerce→acquiring 18102、Payment 18103、Risk→risk-decision 18104、Finance→ledger 18105、Platform 18106、Vault 18107、Gateway 18080、Operations BFF 18201、Client BFF 18202）；新服务 18108 起按 03 文档第 1 节分配（commercial 18108 … workflow-approval 18121，partner-bff 预留 18203）；管理端口 = 业务端口 + 1000。
- 每个 app 独立 Snowflake worker id，由实例清单分配，不再共用一个 `IdGenerator` 服务三个 core。

## 4. 配置命名空间

- 服务自身配置前缀 `<service>.*`（如 `ledger.identifier.worker-id`），不再出现 `decision.identifier.*` 被 compliance 读取这类跨服务配置。
- Foundation 组件保持自己的前缀（`compensation.*`、`messaging.*`、`vault.*`、`web.logging.*`）。
- 外部服务地址一律经服务发现名，不写 URL；测试用 `simple` discovery 指向 MockHttpServer。
- 本地环境定位：父 POM 属性 `paytech.local-environment.dir`（默认 `${maven.multiModuleProjectDirectory}/../local-environment`）作为测试读取 `.env.local` 的唯一锚点，替代各测试按目录名或 `../../` 定位（design/10 §4.4）。

## 5. 实例清单（Instance Manifest）Schema v1

文件 `paytech/tools/instance-manifest/schema/instance-manifest-v1.yaml`，由 `tools/instance-manifest` 校验器在构建期校验；部署描述（Compose / Helm values）由它生成。

```yaml
instance:
  id: main                      # 实例 ID，全局唯一
  operator: PayTech             # 运营方
  operationMode: SELF | MANAGED # 自运营 / 代运营
  businessModes: [MOR, PSP]
modules:                        # 可选模块及版本；基座不列
  card: {enabled: true, version: 2.3.0}
  payout: {enabled: true, version: 2.3.0}
  fx: {enabled: false}
  dispute: {enabled: true, version: 2.3.0}
  analytics: {enabled: false}
brands:
  - code: main
    domains: [checkout.example.com]
    legalPartyRef: PARTY-xxxx   # 引用客户主档 Party，不存法律名称
channels: [NI, PAYERMAX, PAYBY, ALIPAY_PLUS]
services:                       # 每个 app 的运行参数（阶段 0 只用到这些）
  ledger: {port: 18111, snowflakeWorkerId: 11, db: mor_ledger}
  account: {port: 18112, snowflakeWorkerId: 12, db: mor_account}
versions:
  platform: 2.3.0
  schema: {ledger: 7, account: 1}
```

阶段 0 只实现 Schema、校验器与"按清单生成 local-environment compose 片段"；运维平面不在范围。

## 6. 数据源、事务与 MyBatis

- 每个 app 一个 DataSource、一个 `DataSourceTransactionManager`、一个 `SqlSessionTemplate`、一个 `@MapperScan`（只扫本服务 core 的 mapper 包）、一个 TypeHandler 注册表。
- `TransactionTemplate` 按类型注入即可（单数据源），不再需要 `@Qualifier`。
- 补偿事件库表与 Mapper 随 app 配置，`CompensationTransactionBoundary` 要求的"同一 DataSource 与事务管理器"自然满足。

## 7. Enforcer 与 Surefire

- 各服务聚合 POM 保留现有 `bannedDependencies`；Gateway 与两个 BFF 对业务制品的禁令改为通配 `com.paytech:*-core`、`com.paytech:*-app`（加自身 include），替代逐服务列举，避免改名后悄悄失效。
- BFF 继续禁止 JDBC / AMQP / XXL / compensation-event；Platform 解除对 `org.springframework.amqp:*` 与 `spring-boot-starter-amqp` 的禁止（C5），其余禁令保留。
- `failIfNoSpecifiedTests` 在各服务聚合 POM 保留现值；`platform-support-test` 为 `true`。
- Platform 覆盖率门禁改为只在 `-Pcoverage-gate` 下执行，默认 `verify` 不跑，已知失败不阻断 Reactor（既有问题另行修复）。
- `${maven.multiModuleProjectDirectory}` 的用法全部改为 `${project.basedir}/..` 相对引用。

## 8. 同包与命名清理

- `platform-support-web` 与 `platform-support-integration` 的同包 `com.paytech.platform.support.integration`：web 侧的包改为 `...support.app.integration`。
- 包根统一 `com.paytech.<service_dotted>`（如 `com.paytech.risk.decision`、`com.paytech.ledger`）；搬迁时允许保留旧包名一个版本以减少 diff，但 ArchUnit 以模块而非包名判边界。

## 9. V2 规范草案的生效范围（Q4）

本目录 `design/` 的约定自阶段 4 起对 `paytech/` 下模块生效；现行 `DEVELOPMENT_CONVENTIONS.md`、`JAVA_PROJECT_STRUCTURE.md`、`DATABASE_DESIGN_GUIDE.md`、`COMPENSATION_EVENT_GUIDE.md` 继续约束旧目录。全部搬迁完成后，以本目录内容为底稿替换现行文档，另行登记需求。
