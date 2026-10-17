# 09 · 路径耦合修复清单

- 状态：阶段 3 评审稿。只读核对于 2026-10-17 在 `Code-MoR-refactor`（`refactor-main`）完成，未改代码、未运行构建。
- 依据：[analysis/retained-systems.md](../analysis/retained-systems.md) 第 5、6、8 节与五份业务盘点；目标布局以 [01-skeleton-and-conventions.md](./01-skeleton-and-conventions.md) 第 1–3、7 节为准；表归属以 [03-module-and-table-ownership.md](./03-module-and-table-ownership.md) 为准。
- 步骤编号：“需求步”指 [requirement.md 2.2](../requirement.md) 的 1–12；括号内为 [11-dev-plan.md](./11-dev-plan.md) 的短分支编号（00–13）。需求步 2 对应计划 02a–02d，旧根清空与文档链接重写落在计划 13。

## 0. 核对结论与对盘点的修正

| 项 | 盘点结论 | 实际核对 |
|---|---|---|
| `${maven.multiModuleProjectDirectory}` | Platform 6 处；Commerce / Finance / Risk 测试 POM 各 1 处 | **仅 Platform `platform-support-test/pom.xml` 12 处**（第 122–147 行，classfiles 6 + sourcefiles 6）。三个业务测试 POM 用的是 `<xxx.reactor.root>${project.basedir}/..</xxx.reactor.root>`，相对引用，搬迁后仍成立 |
| `failIfNoSpecifiedTests` | “各根为 false、platform-support-test 为 true” | 4 个根为 `false`、4 个 `-test` 模块为 `true`（见 1.5）；CCT、Payment、Risk、Finance、Foundation、Gateway、Vault 未设置 |
| 文档相对链接 | 217 条 / 64 文件 | 按 `](../…/<旧根>` 全仓统计为 **520 条 / 141 文件**（含 requirements/ 与多层 `../`）；另有旧根内部文档指向仓库级目录的**出向链接 351 条 / 32 文件**（盘点未统计） |
| requirements-site 测试 | 与服务目录无耦合 | `requirements-site/tests/site-content.test.mjs:1646,1654` 读取 `../../payment-orchestration-service/docs/development/processor-conventions.md` 与 `AGENTS.md`，**有耦合** |
| 绝对路径 | 26 个文件 | 41 个文件（含 `.json` 证据与 requirements assets 脚本），其中非 Markdown 的脚本 / JSON 19 处 |

目标布局（节选，来自 01 文档）：`paytech/pom.xml`（纯聚合）、`paytech/build/{paytech-dependencies,paytech-parent}`、`paytech/foundation/<module>`、`paytech/<service>/paytech-<service>-{api,core,app,test}`、`paytech/tools/*`、唯一 Wrapper `paytech/mvnw`。Vault 的 Go 根整体搬为 `paytech/vault/`，Java 模块 `paytech/vault/paytech-vault-client`、`paytech/vault/paytech-vault-test` 直接位于其下。仓库级目录 `agent-docs/`、`docs/`、`requirements/`、`local-environment/`、`test-environment/`、`scripts/`、`requirements-site/` **不搬**。

---

## 1. (a) Maven

### 1.1 父 POM `relativePath`

| 文件 | 行 | 现值 | 目标值 | 需求步 |
|---|---|---|---|---|
| `paytech-foundation/pom.xml` | 11 | `<relativePath/>` | `paytech/foundation/pom.xml`：`<relativePath>../build/paytech-parent/pom.xml</relativePath>` | 1 (01) |
| `platform-support-service/pom.xml` | 11 | `<relativePath/>` | `paytech/platform-support/pom.xml`：`../build/paytech-parent/pom.xml` | 2 (02a) |
| `api-gateway/pom.xml` | 9 | `<relativePath/>` | `paytech/api-gateway/pom.xml`：同上 | 2 (02b) |
| `operations-console-bff/pom.xml` | 11 | `<relativePath/>` | `paytech/operations-console-bff/pom.xml`：同上 | 2 (02c) |
| `client-console-bff/pom.xml` | 11 | `<relativePath/>` | `paytech/client-console-bff/pom.xml`：同上 | 2 (02c) |
| `vault-service/pom.xml` | 9 | `<relativePath/>` | `paytech/vault/pom.xml`：同上 | 2 (02d) |
| `commerce-service/pom.xml` | 11 | `<relativePath/>` | `paytech/acquiring/pom.xml` 与 `paytech/consumer/pom.xml`：同上 | 3 (03) |
| `client-contract-service/pom.xml` | 11 | `<relativePath/>` | `paytech/client-master/pom.xml` 与 `paytech/commercial/pom.xml` | 4 (04) |
| `payment-orchestration-service/pom.xml` | 11 | `<relativePath/>` | `paytech/payment/pom.xml`、`paytech/channel-connector/pom.xml`（`paytech/card/pom.xml` 在需求步 12） | 5 (05)、12 |
| `risk-compliance-service/pom.xml` | 10 | `<relativePath/>` | `paytech/risk-decision/pom.xml`、`paytech/compliance/pom.xml` | 6 (06) |
| `finance-service/pom.xml` | 10 | `<relativePath/>` | `paytech/{billing,account,ledger,reconciliation,tax}/pom.xml` 五份 | 7 (07) |
| `paytech-build/paytech-parent/pom.xml` | 11 | `../paytech-dependencies/pom.xml` | 不变（搬入 `paytech/build/` 后相对位置相同） | 1 (00) |
| 各服务子模块 POM（Risk 6、Gateway 2、CCT 3、Finance 6、Ops BFF 2、Commerce 4、Platform 8、Payment 7、Client BFF 2） | 各第 9–11 行 | `../pom.xml` 指向服务聚合 POM | 不变；新建的 `-api/-core/-app/-test` POM 沿用 `../pom.xml` | 各步 |

说明：Maven 3.9 不会从 Reactor 解析空 `relativePath/` 的父 POM，今天靠 `~/.m3` 中已安装的 `paytech-parent` 引导。单 Reactor 后必须写真实相对路径，否则在干净本地仓库首次 `mvn verify` 失败。`paytech/pom.xml` 自身不设父（纯聚合，`packaging=pom`），避免“父 POM 同时是子模块”的解析顺序问题。

### 1.2 `${maven.multiModuleProjectDirectory}`

| 文件 | 行 | 现值 | 目标值 | 需求步 |
|---|---|---|---|---|
| `platform-support-service/platform-support-test/pom.xml` | 122、124、126、128、130、132（classfiles）；136、138、140、142、144、146（sourcefiles） | `${maven.multiModuleProjectDirectory}/platform-support-{api,access-control,audit-security,file-document,delivery,web}/{target/classes,src/main/java}` | `${project.basedir}/../paytech-platform-support-{api,core,app}/target/classes` 与 `/src/main/java`（6 个旧模块合并为 3 个，共 6 个 fileset）；整段 antrun（第 101–160 行，execution `verify-task-nine-coverage`）移入 Profile `coverage-gate`，默认 `verify` 不执行 | 2 (02a) |

原因：单 Reactor 后该变量解析到 `paytech/`（含 `.mvn` 的目录），六个 fileset 全部落空，JaCoCo `check` 以空报告判定失败。该门禁现为既有失败（0.54 / 0.37），不能阻断整体 Reactor。

### 1.3 模块列表

| 现聚合 POM | 现 `<modules>`（行） | 新聚合 POM 与模块 | 需求步 |
|---|---|---|---|
| 仓库根 | 无 `pom.xml` | **新增** `paytech/pom.xml`：`build/paytech-dependencies`、`build/paytech-parent`、`foundation`、`vault`、`platform-support`、`api-gateway`、`operations-console-bff`、`client-console-bff`、`client-master`、`commercial`、`acquiring`、`consumer`、`payment`、`channel-connector`、`card`、`risk-decision`、`compliance`、`billing`、`account`、`ledger`、`reconciliation`、`tax`、`tools/*`；按步骤逐个加入 | 1 (00) 起逐步 |
| `paytech-build/pom.xml` | 27–28：`paytech-dependencies`、`paytech-parent` | 删除该聚合 POM，两模块由 `paytech/pom.xml` 直接列出；`scripts/verify-java-foundation.mjs:618` 对 `paytech-build` artifactId 的期待同步删除（见 4.1） | 1 (00) |
| `paytech-foundation/pom.xml` | 21–33：13 个模块 | `paytech/foundation/pom.xml`：13 个 + `paytech-messaging`（`paytech-messaging-api` 若独立则再加） | 1 (01) |
| `platform-support-service/pom.xml` | 126–133：api、access-control、audit-security、file-document、delivery、integration、web、test | `paytech-platform-support-{api,core,app,test}`；dependencyManagement 第 25–60 行的 8 个内部 artifact 改为 4 个 | 2 (02a) |
| `api-gateway/pom.xml` | 84–85：`api-gateway-web`、`api-gateway-test` | `paytech-api-gateway-{core,app,test}`（Gateway 不发布 api；若按 01 统一四模块则 api 为空壳，建议省略） | 2 (02b) |
| `operations-console-bff/pom.xml` | 22–23 | `paytech-operations-console-bff-{core,app,test}` | 2 (02c) |
| `client-console-bff/pom.xml` | 22–23 | `paytech-client-console-bff-{core,app,test}` | 2 (02c) |
| `vault-service/pom.xml` | 14：`paytech-vault-client`、`vault-test` | `paytech/vault/pom.xml`：不变（Go 服务无 Maven 模块；Java Client 不拆 core/app） | 2 (02d) |
| `commerce-service/pom.xml` | 133–136：customer-api、client-api、web、test | `acquiring`：`paytech-acquiring-{api,core,app,test}`（现 `paytech-commerce-client-api` 内容进 acquiring-api）；`consumer`：`paytech-consumer-{api,core,app,test}`（现 `paytech-commerce-customer-api` 内容按 03 文档归入） | 3 (03) |
| `client-contract-service/pom.xml` | 105–107：api、web、test | `client-master`、`commercial` 各四模块；现 `client-contract-api` 按包拆入两个 api | 4 (04) |
| `payment-orchestration-service/pom.xml` | 19–25：api、execution、routing、channel、card、web、test | `payment`（execution + routing + web 的 Processor → core）、`channel-connector`（channel → core）、`card`（需求步 12 前作为 payment-app 内模块，见 05 文档） | 5 (05)、12 (12) |
| `risk-compliance-service/pom.xml` | 18–23：api、decision、control、compliance、web、test | `risk-decision`（decision + control）、`compliance`；`risk-api` 删除，五个消费方在第 6 步同分支改坐标（07 文档 C6） | 6 (06) |
| `finance-service/pom.xml` | 18–23：api、accounting、treasury、tax、web、test | 五个服务各四模块；`treasury` 空壳并入 `reconciliation` 或删除按 03 文档 | 7 (07) |

### 1.4 Enforcer `bannedDependencies`

现状（均在各根聚合 POM，Foundation、Vault、`paytech-parent` 无）：

| 现根 POM | 禁令要点（行） | 目标位置与改动 | 需求步 |
|---|---|---|---|
| `api-gateway/pom.xml` | 第 35–80 行：Servlet/MVC 栈、Cloud Gateway starter、`paytech-redis-starter`、`paytech-caffeine-starter`、`paytech-mvc-web-starter`、Redisson、Testcontainers、Mockito、nacos-config；对业务制品 `com.paytech:platform-support-web`、`client-contract-*`、`commerce-*`、`payment-orchestration-*`、`risk-compliance-*`、`finance-*` | `paytech/api-gateway/pom.xml` 原样搬入；**业务制品模式改为真实 artifactId**。现模式中只有 `client-contract-*` 与 `platform-support-web` 命中（实际 artifactId 为 `paytech-commerce-*`、`paytech-payment-*`、`risk-*`、`paytech-finance-*`）。推荐统一写法：`<exclude>com.paytech:*-core</exclude>`、`<exclude>com.paytech:*-app</exclude>`，并以 `<includes>` 放行自身 `paytech-api-gateway-core`；这样所有服务的 core / app 对 Gateway 一律禁止，api 一律允许，无需逐服务列举 | 2 (02b) |
| `operations-console-bff/pom.xml`、`client-console-bff/pom.xml` | 第 67–/54– 行：JDBC/JPA/MyBatis/MySQL/Hikari、Flyway/Liquibase、nacos-config、spring-data-redis、Redisson、AMQP/RabbitMQ、XXL、`paytech-mybatis-support`、caffeine/redis-starter、`paytech-compensation-event`、`paytech-xxl-job-starter`、Security（放行 crypto）、WebFlux、Cloud Gateway、OpenFeign、Dubbo、Mockito、Testcontainers | 各自新聚合 POM 原样搬入；追加 `com.paytech:*-core` / `*-app` 禁令并放行自身 core | 2 (02c) |
| `platform-support-service/pom.xml` | 第 83–125 行：nacos-config、reactive redis、`spring-boot-starter-amqp`、`org.springframework.amqp:*`、`com.xuxueli:*`（放行 xxl-job-core、xxl-tool）、`paytech-caffeine-starter`、Flyway/Liquibase、Security（放行 crypto）、Cloud Gateway、Mockito、Testcontainers | 搬入 `paytech/platform-support/pom.xml`；**删除两条 AMQP 禁令**（C5，`paytech-messaging` 需要） | 2 (02a) |
| `client-contract-service/pom.xml` | 第 40–104 行：JDBC starter、Flyway/Liquibase、Testcontainers、nacos-config、Redis 全家（`<includes>` 再放行 `paytech-redis-starter`、redisson、data-redis、lettuce）、caffeine、AMQP（includes 放行）、XXL（includes 放行 compensation-event、xxl-job-starter、xxl-job-core、xxl-tool）、Security/OAuth2（放行 crypto）、Cloud Gateway、Mockito | 复制到 `client-master` 与 `commercial` 两份；`commercial` 无 MQ 消费者，可去掉 AMQP 放行 | 4 (04) |
| `commerce-service/pom.xml` | 第 68–132 行：JDBC starter、H2、Flyway/Liquibase、Testcontainers、nacos-config、Redis（includes 放行）、caffeine、AMQP（includes 放行 amqp/rabbit/amqp-client）、**`com.paytech:paytech-vault-client`**、Security/OAuth2、Cloud Gateway、OpenFeign、Dubbo、WebFlux、Mockito | 复制到 `acquiring` 与 `consumer`；`paytech-vault-client` 禁令两边保留（消费者 Token 仍是新模型，见 technique.md 1.5） | 3 (03) |
| `payment-orchestration-service/pom.xml` | 第 67–94 行：Flyway/Liquibase、Mockito、Testcontainers、Dubbo、OpenFeign | 复制到 `payment`、`channel-connector`（需求步 12 再复制到 `card`） | 5 (05)、12 |
| `risk-compliance-service/pom.xml` | 第 58–：Flyway/Liquibase、nacos-config、WebFlux、Cloud Gateway、OpenFeign、Dubbo、Mockito、Testcontainers | 复制到 `risk-decision`、`compliance`；`RiskModuleBoundaryAutoTest` 中对禁令清单的断言同步拆分（见 2.8） | 6 (06) |
| `finance-service/pom.xml` | 第 58–：Flyway/Liquibase、`com.alibaba.cloud:*`、`com.alibaba.nacos:*`、AMQP、RabbitMQ、XXL、`paytech-compensation-event`、`paytech-xxl-job-starter`、WebFlux、Cloud Gateway、OpenFeign、Dubbo、Mockito、Testcontainers | 复制到五个服务；`account` 与 `ledger` 在需求步 11 需要 `paytech-messaging` 与补偿事件，届时放开 AMQP / compensation-event 禁令（04 文档） | 7 (07)、11 |

规则：禁令只放服务聚合 POM，不进 `paytech-parent`（01 文档第 1 节）。

### 1.5 Surefire `failIfNoSpecifiedTests`

| 文件 | 行 | 现值 | 目标 | 需求步 |
|---|---|---|---|---|
| `operations-console-bff/pom.xml` | 62 | `false` | `paytech/operations-console-bff/pom.xml` 保留 | 2 (02c) |
| `operations-console-bff/paytech-operations-console-bff-test/pom.xml` | 56 | `true` | `paytech-operations-console-bff-test` 保留 | 2 (02c) |
| `client-console-bff/pom.xml` / `…-bff-test/pom.xml` | 49 / 51 | `false` / `true` | 同上 | 2 (02c) |
| `platform-support-service/pom.xml` / `platform-support-test/pom.xml` | 78 / 181 | `false` / `true` | 同上 | 2 (02a) |
| `commerce-service/pom.xml` / `paytech-commerce-test/pom.xml` | 63 / 99 | `false` / `true` | `acquiring`、`consumer` 两边各复制一对 | 3 (03) |
| 其余根（CCT、Payment、Risk、Finance、Foundation、Gateway、Vault） | — | 未设置（继承 `paytech-parent` 默认） | 不设置 | — |

同时保留的测试路径属性：`risk-compliance-service/risk-test/pom.xml:57`、`finance-service/paytech-finance-test/pom.xml:94`、`commerce-service/paytech-commerce-test/pom.xml:101` 的 `<xxx.reactor.root>${project.basedir}/..`：`-test` 模块仍直接位于服务目录下，值不变；`commerce.reactor.root` 无 Java 读取方，拆分时删除。

### 1.6 其他 Maven 项

| 文件 | 行 | 现值 | 目标 | 需求步 |
|---|---|---|---|---|
| `client-console-bff/pom.xml` | 28–29 | `client-contract-api`、`risk-api` 硬编码 `1.0.0-SNAPSHOT` | `${project.version}`；需求步 6 后改为 `paytech-risk-decision-api` / `paytech-compliance-api`，需求步 4 后改为 `paytech-client-master-api` | 2 (02c)、4、6 |
| `operations-console-bff/pom.xml` | 30–45 | `risk-api`、`platform-support-api`、`client-contract-api`、`paytech-vault-client` 硬编码版本 | 同上 | 2 (02c) |
| 12 个根的 `mvnw`、`.mvn/`（Platform、CCT、Commerce、Risk、Finance 无 `mvnw.cmd`）与仓库根 `mvnw`、`.mvn/` | — | 13 份 Wrapper | 只保留 `paytech/mvnw`、`paytech/mvnw.cmd`、`paytech/.mvn/`（固定 `distributionSha256Sum`）；删除其余 13 份；依赖 Wrapper 位置的脚本见 2.10、3.2、4.1、4.2 | 1 (00) 建立，各步删除 |
| `vault-service/pom.xml` | 11–14 | artifactId `vault-service`，模块 `paytech-vault-client`、`vault-test` | `paytech/vault/pom.xml`，artifactId `paytech-vault`；模块不变 | 2 (02d) |

---

## 2. (b) 按目录名定位仓库或相邻目录的测试

| # | 文件 | 行 | 现值 | 目标值或处理方式 | 需求步 |
|---|---|---|---|---|---|
| 2.1 | `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/test/configuration/LocalMockEnvironmentInitializer.java` | 89–91 | 向上查找同时含 `local-environment/.env.example` 与 `platform-support-service/pom.xml` 的目录为仓库根 | 标记改为 `local-environment/.env.example` + `paytech/pom.xml`（仓库根不变，服务目录改名）；`OnboardingFileTestBase.java:74` 与 `LocalMockEnvironmentInitializerTest.java:49,118` 的合成工作区同步改标记 | 2 (02a) |
| 2.2 | `platform-support-service/platform-support-test/src/test/resources/application-mock.yml` | 236、246 | `${mor.local-environment:${user.dir}/../../local-environment}/minio/certs/public.crt` | 回退路径改 `${user.dir}/../../../local-environment`（测试模块深度由 2 变 3）；主路径由 2.1 注入，不变 | 2 (02a) |
| 2.3 | `operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/RiskClientConfigurationAutoTest.java` | 531–537 | 向上查找名为 `paytech-operations-console-bff-web` 的目录 | 改为 `paytech-operations-console-bff-app`（读取的 `application.yml` 随 app）；建议改为 `Path.of("").resolve("../paytech-operations-console-bff-app")` 并断言存在，不再向上遍历 | 2 (02c) |
| 2.4 | `…/bff/auto/OperationsConsoleBffApplicationAutoTest.java` | 341–347 | 同 2.3 | 同 2.3 | 2 (02c) |
| 2.5 | `paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/auto/architecture/ResourceLockArchitectureAutoTest.java` | 68、141 | `moduleRoot()`=`user.dir`，`getParent()` 视为 Foundation 根并 `Files.walk` | 不改代码；前提是 13 个模块仍直接位于 `paytech/foundation/` 下（01 文档）。若去掉 `foundation/` 一层，遍历范围会扩大到整个 `paytech/` | 1 (01) 核对 |
| 2.6 | `paytech-foundation/paytech-redis-starter/src/test/java/com/paytech/foundation/redis/test/infrastructure/RedisLocalEnvironmentPrecondition.java` | 16–19 | 提示文本 `From paytech-foundation run: … ../local-environment/.env.local … -f ../local-environment/compose.yaml` | 改为 `From paytech/foundation run: … ../../local-environment/…`；`RedisLocalEnvironmentPreconditionAutoTest.java:12,14` 断言该文本，同步改并把该类移入 `auto/`（既有未执行问题） | 1 (01) |
| 2.7 | `finance-service/paytech-finance-test/src/test/java/com/paytech/finance/auto/FinanceModuleBoundaryTest.java` | 27–28、43–63、86 | 读 `finance.reactor.root`，断言模块集合 `paytech-finance-{api,accounting,treasury,tax,web,test}` 与各 POM 依赖 | 路径属性不变；按五个服务各写一份（或下沉为 `tools/arch-rules` 的 POM 依赖规则）断言 `paytech-<svc>-{api,core,app,test}`；RabbitTemplate 不在 classpath 的断言在需求步 11 对 `account`、`ledger` 取消 | 7 (07)、11 |
| 2.8 | `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/RiskModuleBoundaryAutoTest.java` | 48–50、63、84–99、122–125、287 | 读 `risk.reactor.root`，断言模块集合 `risk-{api,decision,control,compliance,web,test}`、依赖集合、Enforcer 清单、只有 web 可执行 | 拆为 `risk-decision` 与 `compliance` 各一份（07 文档第 6 节）；`RiskDatabasePrecondition.java:31–33` 的 SQL 路径提示改为两库各自的 `docs/database/sql/*.sql` | 6 (06) |
| 2.9 | `payment-orchestration-service/paytech-payment-test/src/test/java/com/paytech/payment/auto/architecture/ChannelModuleBoundaryTest.java` | 98–105、109–114 | 向上查找含 `paytech-payment-channel/pom.xml` 的目录，列举同级 `paytech-payment-*` 模块扫描 Owner 表 | 锚点改为 `paytech/payment/pom.xml`（`Path.of("").resolve("..")`），模块列举改 `paytech-payment-{api,core,app}`；对 Channel 表的归属规则迁入 `tools/arch-rules` R4，由 `channel-connector` 自己持有 | 5 (05) |
| 2.10 | `vault-service/vault-test/src/test/java/com/paytech/vault/test/auto/VaultEndToEndTest.java` | 27 | `new ProcessBuilder("../mvnw", "--version")` | `../../mvnw`（`paytech/vault/paytech-vault-test` → `paytech/mvnw`） | 2 (02d) |
| 2.11 | 同上 | 100 | `new ProcessBuilder("../scripts/verify.sh")` | 不变（`scripts/` 随 Go 根留在 `paytech/vault/scripts/`）；`verify.sh` 内部的 Wrapper 检查见 3.2 | 2 (02d) |
| 2.12 | `vault-service/paytech-vault-client/src/test/java/com/paytech/vault/client/auto/VaultClientHttpTest.java` | 35 | `Path.of("../testdata/wire/" + name + ".json")` | 不变（`testdata/` 随 Go 根，Client 模块直接位于 `paytech/vault/` 下） | 2 (02d) |
| 2.13 | `vault-service/test/auto/contract/openapi_test.go:19,26,41,53`、`admin_openapi_test.go:7`、`vault-service/internal/data/schema_contract_test.go:21` | — | `../../../api/openapi/*.yaml`、`../../docs/database/sql/vault-schema.sql` | 不变（Go 根内部相对路径） | — |
| 2.14 | `payment-orchestration-service/paytech-payment-test/src/test/resources/application-mock.yml` | 11–12 | `spring.config.import: optional:file:../../local-environment/.env.local`、`../local-environment/.env.local` | 增加 `optional:file:../../../local-environment/.env.local[.properties]`（模块深度 3）；`channel-connector-test`、`card-test` 复制同样配置 | 5 (05)、12 |
| 2.15 | `api-gateway/api-gateway-test/src/test/java/com/paytech/gateway/test/redis/RepositoryRedisEnvironment.java` | 19 | 提示 `cd local-environment` | 不变（相对仓库根） | — |
| 2.16 | `requirements-site/tests/site-content.test.mjs` | 1646、1654 | `new URL("../../payment-orchestration-service/docs/development/processor-conventions.md")`、`../../payment-orchestration-service/AGENTS.md` | `../../paytech/payment/docs/development/processor-conventions.md`、`../../paytech/payment/AGENTS.md`；需求步 5 当步修改，否则 `npm --prefix requirements-site test` 失败 | 5 (05) |
| 2.17 | 仅提示文本（不影响执行）：`commerce-service/paytech-commerce-test/.../CheckoutDataMocker.java:71`、`platform-support-service/.../WebhookFixture.java:73`、`finance-service/paytech-finance-test/src/test/resources/init/ddl.sql:3,5`、`commerce-service/paytech-commerce-test/src/test/resources/init/ddl.sql:4`、`vault-service/test/support/base/environment.go:30`、`vault-service/internal/data/schema.go:14` | — | 引用 `<旧根>/docs/database/sql/*.sql` | 随各步改为新服务路径，不阻断 | 各步 |

---

## 3. (c) 构建与部署脚本

| # | 文件 | 行 | 现值 | 目标值或处理方式 | 需求步 |
|---|---|---|---|---|---|
| 3.1 | `test-environment/scripts/build.sh` | 54 | `(cd "$workspace_dir/paytech-build" && ./mvnw -DskipTests clean install)` | 删除；由单 Reactor 一次构建 | 1 (01) |
| | | 55 | `(cd "$workspace_dir/paytech-foundation" && ./mvnw -pl paytech-mvc-web-starter,… -am … install)` | 删除（同上） | 1 (01) |
| | | 56–57 | `cd risk-compliance-service && ./mvnw -pl risk-web -am … install`；`cp risk-compliance-service/risk-web/target/risk-web-1.0.0-SNAPSHOT-exec.jar artifacts/risk-service.jar` | `(cd "$workspace_dir/paytech" && ./mvnw -pl risk-decision/paytech-risk-decision-app,compliance/paytech-compliance-app,platform-support/paytech-platform-support-app,api-gateway/paytech-api-gateway-app,operations-console-bff/paytech-operations-console-bff-app -am -DskipTests clean package)`；`cp paytech/risk-decision/paytech-risk-decision-app/target/paytech-risk-decision-app-1.0.0-SNAPSHOT-exec.jar artifacts/risk-decision.jar`、`…compliance….jar`。compose 服务 `risk-service`（`compose.applications.yaml:72–74`）需拆为两个服务，这是 D09 的部署面变化，不在路径修复范围，此处只登记 | 6 (06) |
| | | 59–61 | `cd platform-support-service / api-gateway / operations-console-bff && ./mvnw -pl <web> -am …` | 并入上一条单次 Reactor 命令 | 2 (02a–c) |
| | | 63–65 | `cp platform-support-service/platform-support-web/target/platform-support-web-1.0.0-SNAPSHOT-exec.jar`、`api-gateway/api-gateway-web/target/api-gateway-web-…-exec.jar`、`operations-console-bff/paytech-operations-console-bff-web/target/…-exec.jar` | `paytech/platform-support/paytech-platform-support-app/target/paytech-platform-support-app-1.0.0-SNAPSHOT-exec.jar` → `artifacts/platform-support-service.jar`（compose `JAR_FILE` 名不变）；Gateway、BFF 同理 | 2 |
| | | 62 | `cd "$workspace_dir/operations-console-web" && npm ci && npm run build` | 不变（前端不搬） | — |
| | | 73 | `compose.sh build nacos risk-service platform-support-service operations-console-bff api-gateway operations-console-web` | 随 compose 服务名调整 | 6 |
| 3.2 | `test-environment/scripts/test_deploy_script.py` | 766–790、815–825 | 断言 `build.sh` 含 `(cd "$workspace_dir/paytech-build"`、`paytech-foundation`、`risk-compliance-service`、`platform-support-service`、`api-gateway`、`operations-console-bff` 片段与顺序，及 `risk-web/target/risk-web-1.0.0-SNAPSHOT-exec.jar` | 与 3.1 同一提交改断言为新片段（单 Reactor 命令、新 jar 路径）；否则 `python3 -m unittest test_deploy_script` 失败 | 与 3.1 同步 |
| 3.3 | `test-environment/scripts/build-vault.sh` | 35 | `cd "$workspace_dir/vault-service"` | `cd "$workspace_dir/paytech/vault"` | 2 (02d) |
| 3.4 | `local-environment/vault/vault.sh` | 45 | `(cd "$workspace/vault-service" && "$go_bin" build … ./cmd/vault-server)` | `cd "$workspace/paytech/vault"`；第 46 行 `test-environment/vault/probe.go` 不变 | 2 (02d) |
| 3.5 | `vault-service/scripts/verify.sh` | 118、142 | `root/'mvnw'`、`root/'.mvn/wrapper/maven-wrapper.properties'`、`[str(root/'mvnw'), …]`（root = 脚本上一级 = Vault 根） | 改为 `root.parent / 'mvnw'`、`root.parent / '.mvn/wrapper/maven-wrapper.properties'`（`paytech/mvnw`）；`VaultEndToEndTest.java:100` 调用它，一并回归 | 2 (02d) |
| 3.6 | `test-environment/scripts/initialize.py` | 21 | `RISK_SQL_DIR = WORKSPACE_ROOT / "risk-compliance-service/docs/database/sql"`（22–23 行派生 schema / initial-data） | `paytech/risk-decision/docs/database/sql` 与 `paytech/compliance/docs/database/sql` 两套；`mor_risk` 单库初始化改为两库，属部署面变化，登记到 10 文档 | 6 (06) |
| | | 24 | `PLATFORM_CATALOG = … 'platform-support-service/docs/database/sql/platform-support-initial-data.sql'` | `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql` | 2 (02a) |
| | | 124 | `sql_dir = ROOT.parent / "platform-support-service/docs/database/sql"` | `ROOT.parent / "paytech/platform-support/docs/database/sql"` | 2 (02a) |
| 3.7 | `test-environment/scripts/vault.py` | 86 | `ROOT.parent / "vault-service/docs/database/sql/vault-schema.sql"` | `ROOT.parent / "paytech/vault/docs/database/sql/vault-schema.sql"` | 2 (02d) |
| 3.8 | `test-environment/scripts/test_risk_database_initialization.py` | 124–125、132–133 | `WORKSPACE / "risk-compliance-service/docs/database/sql/risk-compliance-{initial-data,schema}.sql"` | 两库各自路径（同 3.6） | 6 (06) |
| 3.9 | `test-environment/scripts/test_risk_rule_configuration_catalog.py` | 21 | `WORKSPACE / 'platform-support-service/docs/database/sql/platform-support-initial-data.sql'` | `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql` | 2 (02a) |
| 3.10 | `test-environment/scripts/test_nginx_gateway_problem_boundary.py` | 11–13 | `WORKSPACE / "api-gateway/api-gateway-web/src/main/resources/application.yml"` | `paytech/api-gateway/paytech-api-gateway-app/src/main/resources/application.yml` | 2 (02b) |
| 3.11 | `test-environment/scripts/prepare.py` | 39 | 提示 `Install paytech-build/Foundation/Platform dependencies first` | 文案改 `paytech/` Reactor | 1 (01) |
| 3.12 | `test-environment/scripts/test_risk_rule_configuration_authorization_inventory.py` | 739–740、758、776 | 断言 `docs/superpowers/plans/2026-09-07-risk-operations-local-integration.md` 文本含 `paytech-build && ./mvnw clean verify`、`/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/nginx/nginx.conf` 等 | 它读的是历史计划文档文本，不触文件系统；文档不改则测试不受搬迁影响。若按第 6 节重写该文档，断言需同步 | 13 时决定 |
| 3.13 | 不受影响（核对确认）：`test-environment/compose.applications.yaml` 的 `context: ./artifacts`、`dockerfile: ../java/Dockerfile`、`JAR_FILE: <service>.jar`；`local-environment/compose.yaml:191–192` 的 `context: ./vault/bin/image`、`dockerfile: ../../../../test-environment/vault/Dockerfile`（相对 context，指向仓库级 `test-environment/`）；两个 `compose.scheduler.yaml` 挂载 `../local-environment/mysql/init`；`test-environment/deploy.sh`、`compose.sh`、`java/Dockerfile`、`vault/Dockerfile`、`nginx.conf`；Nacos dataId 与 XXL 执行器名按 `spring.application.name` | — | — | — |

---

## 4. (d) Node 工具

### 4.1 仓库级脚本

| 文件 | 行 | 现值 | 目标值 | 需求步 |
|---|---|---|---|---|
| `scripts/verify-java-foundation.mjs` | 16–18 | `buildRoot = join(repositoryRoot, 'paytech-build')`、`foundationRoot = join(repositoryRoot, 'paytech-foundation')`、`wrapper = join(foundationRoot, 'mvnw')` | `paytech/build`、`paytech/foundation`、`paytech/mvnw` | 1 (01) |
| | 23–30 | `requiredFiles`：`paytech-build/pom.xml`、`paytech-build/paytech-dependencies/pom.xml`、`paytech-build/paytech-parent/pom.xml`、`paytech-foundation/pom.xml`、各模块 `pom.xml` | 删除 `paytech-build/pom.xml`（聚合 POM 取消）；其余换前缀；加入 `paytech-messaging/pom.xml` | 1 |
| | 618–633 | `expectedArtifactIds` 含 `[paytech-build/pom.xml, 'paytech-build']` 与 13 个 Foundation 模块 | 删除 `paytech-build` 项；加 `paytech-messaging` | 1 |
| | 1390–1396 | `spawnSync(wrapper, ['-f', join(foundationRoot, 'pom.xml'), 'help:effective-pom', …])` | `-f paytech/foundation/pom.xml`，Wrapper 为 `paytech/mvnw` | 1 |
| `scripts/verify-local-nexus-settings.mjs` | 15–16 | `repositoryRoot = resolve(scriptDirectory, '..')`；`wrapper = join(repositoryRoot, 'mvnw')` | `join(repositoryRoot, 'paytech', 'mvnw')` | 1 (00) |
| `scripts/verify-local-environment.mjs` | 59、67 | 只引用 `agent-docs/plans/…` 与 `local-environment` | 不受影响 | — |
| `requirements-site/tests/site-content.test.mjs` | 1646、1654 | 见 2.16 | 见 2.16 | 5 |

### 4.2 服务内脚本

| 文件 | 行 | 现值 | 目标值 | 需求步 |
|---|---|---|---|---|
| `api-gateway/scripts/verify-gateway-boundaries.mjs` | 6–11 | `repositoryRoot = <脚本目录>/..`；`api-gateway-web/src/main/java`、`api-gateway-web/src/main/resources/application.yml`、`api-gateway-test/src/test`、`README.md` | 脚本随服务搬到 `paytech/api-gateway/scripts/`，根仍为服务目录；模块名改 `paytech-api-gateway-app/src/main/resources/application.yml`、生产源码根改为 `paytech-api-gateway-core/src/main/java` 与 `paytech-api-gateway-app/src/main/java` 两处、`paytech-api-gateway-test/src/test` | 2 (02b) |
| `api-gateway/scripts/verify-jacoco.mjs` | 7–15 | `api-gateway-test/target/site/jacoco-aggregate`、`api-gateway-test/target/jacoco.exec`、`api-gateway-web/target/classes`（及 `/com/paytech/gateway`）、`api-gateway-test/target/test-classes`、`api-gateway-test/target/surefire-reports` | `paytech-api-gateway-test/target/…`；classes 目录改为 core 与 app 两处并合并统计 | 2 (02b) |
| `api-gateway/scripts/class-file.mjs`、`class-file.test.mjs` | — | 无路径常量 | 随目录搬迁 | 2 (02b) |
| `vault-service/scripts/verify.sh` | 118、142 | 见 3.5 | 见 3.5 | 2 (02d) |

### 4.3 `scripts/pdmaner/**`（97 个 `.mjs`，`scripts/` 不搬，所有 `workspaceRoot = ../..`、`../../..` 保持）

生成器（写入目标）：

| 文件 | 行 | 现值 | 目标值（见第 5 节拆分） | 需求步 |
|---|---|---|---|---|
| `scripts/pdmaner/generate-client-contract-model.mjs` | 38 | `client-contract-service/docs/database/client-contract.pdma.json` | 拆为 `generate-client-master-model.mjs` → `paytech/client-master/docs/database/client-master.pdma.json`、`generate-commercial-model.mjs` → `paytech/commercial/docs/database/commercial.pdma.json`；第 34 行读取的目录 `agent-docs/database/CLIENT_CONTRACT_DATABASE.md` 路径不变，但需按两服务标注表归属 | 4 (04) |
| `scripts/pdmaner/generate-commerce-model.mjs` | 21 | `commerce-service/docs/database/commerce.pdma.json` | `generate-acquiring-model.mjs` → `paytech/acquiring/docs/database/acquiring.pdma.json`；`generate-consumer-model.mjs` → `paytech/consumer/docs/database/consumer.pdma.json`（表定义内联于脚本，按 `defKey` 分组拆分） | 3 (03) |
| `scripts/pdmaner/generate-payment-model.mjs` | 24 | `payment-orchestration-service/docs/database/payment-orchestration.pdma.json` | `generate-payment-model.mjs` → `paytech/payment/docs/database/payment.pdma.json`；`generate-channel-connector-model.mjs` → `paytech/channel-connector/docs/database/channel-connector.pdma.json`；`generate-card-model.mjs` → `paytech/card/docs/database/card.pdma.json`（需求步 12 前卡表仍随 payment 库，模型可先拆、库后拆，由 05 文档定） | 5 (05)、12 |
| `scripts/pdmaner/risk/generate-risk-compliance-model.mjs` | 29 | `risk-compliance-service/docs/database/risk-compliance.pdma.json` | `risk/generate-risk-decision-model.mjs` → `paytech/risk-decision/docs/database/risk-decision.pdma.json`；`risk/generate-compliance-model.mjs` → `paytech/compliance/docs/database/compliance.pdma.json`；争议 7 表 → `dispute.pdma.json`（归属目录由 03 文档定） | 6 (06) |
| `scripts/pdmaner/risk/generate-risk-compliance-schema.mjs` | 6–7、107 | 模型与 `sql/risk-compliance-schema.sql` 路径、SQL 头 `-- Source: risk-compliance-service/docs/database/risk-compliance.pdma.json` | 按两（三）个模型分别输出 `paytech/<svc>/docs/database/sql/<svc>-schema.sql`，`-- Source:` 同步 | 6 |
| `scripts/pdmaner/finance/generate-finance-model.mjs` | 28 | `finance-service/docs/database/finance.pdma.json` | 五个生成器（或一个生成器按 Manifest 分组输出五份）→ `paytech/{billing,account,ledger,reconciliation,tax}/docs/database/<svc>.pdma.json` | 7 (07) |
| `scripts/pdmaner/platform-support/generate-platform-support-model.mjs` | 24 | `platform-support-service/docs/database/platform-support.pdma.json` | `paytech/platform-support/docs/database/platform-support.pdma.json` | 2 (02a) |
| `scripts/pdmaner/vault/generate-vault-model.mjs` | 30 | `vault-service/docs/database/vault.pdma.json` | `paytech/vault/docs/database/vault.pdma.json` | 2 (02d) |
| `scripts/pdmaner/generate-oe-client-schemas.mjs` | 8–11、51 | 仓库表 `[client-contract-service→client-contract]、[commerce-service→commerce]、[payment-orchestration-service→payment-orchestration]、[finance-service→finance]`，写 `<repo>/docs/database/sql/<name>-schema.sql` | 列表改为新服务目录与短名（`paytech/client-master→client-master`、`paytech/commercial→commercial`、`paytech/acquiring`、`paytech/consumer`、`paytech/payment`、`paytech/channel-connector`、`paytech/card`、五个 Finance 服务） | 3–7 |

测试（读取路径）：

| 文件 | 行 | 现值 | 目标值 | 需求步 |
|---|---|---|---|---|
| `scripts/pdmaner/pdmaner-canonical-paths.test.mjs` | 10–16 | 7 条合同 `[<旧根>, <短名>.pdma.json, <生成器>]`，并断言生成器源码含 `<旧根>/docs/database/<短名>.pdma.json` | 改为 Java 持库服务清单（见 5.2 表），每条 `["paytech/<svc>", "<svc>.pdma.json", "scripts/pdmaner/…"]`；第 21 行 `path.join(workspaceRoot, service, "docs/database")` 逻辑不变 | 各步递增，13 收口 |
| `scripts/pdmaner/platform-support-model.test.mjs` | 43–44 | `platform-support-service/docs/database/platform-support.pdma.json`、`PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md` | `paytech/platform-support/docs/database/…` | 2 |
| | 58–59 | `agent-docs/plans/2026-08-29-operations-api-gateway-design.md`、`docs/superpowers/plans/…` | 不变 | — |
| `scripts/pdmaner/platform-support-scenario-doc.test.mjs` | 11 | `platform-support-service/docs/database/platform-support.pdma.json` | `paytech/platform-support/…` | 2 |
| | 24–29 | 场景表中的服务名 `operations-console-bff`、`client-console-bff`、`commerce-service`、`payment-orchestration-service`（Platform Target 服务码，非路径） | 不属于路径耦合；Target 服务码随 `spring.application.name` 改名（`acquiring`、`payment`）时另行处理 | 3、5 |
| `scripts/pdmaner/platform-support-schema.test.mjs` | 5 | `new URL('../../platform-support-service/', import.meta.url)`（7、9、115、170 行派生 `docs/database/sql/…`） | `../../paytech/platform-support/` | 2 |
| | 145 | `../../requirements/20260914/platform-support-外部API配置与执行/db/platform-support-service/integration-create.sql` | 不变（requirements 内路径，目录名是历史记录） | — |
| `scripts/pdmaner/platform-file-upload-model.test.mjs` | 6 | `platform-support-service/docs/database/platform-support.pdma.json` | `paytech/platform-support/…` | 2 |
| `scripts/pdmaner/vault-model.test.mjs` | 14、17 | `vault-service/docs/database/vault.pdma.json`、`VAULT_PHYSICAL_DECISIONS.md` | `paytech/vault/…` | 2 |
| `scripts/pdmaner/client-contract-model.test.mjs` | 25、37 | `client-contract-service/docs/database/client-contract.pdma.json`、`CLIENT_CONTRACT_PHYSICAL_DECISIONS.md` | 拆为 client-master / commercial 两份测试 | 4 |
| `scripts/pdmaner/client-contract-scenario-doc.test.mjs` | 12、305 | 同上 | 同上 | 4 |
| `scripts/pdmaner/client-compensation-model.test.mjs` | 6 | `client-contract-service/docs/database/client-contract.pdma.json` | `paytech/client-master/…`（补偿 Handler 全在 client-master） | 4 |
| `scripts/pdmaner/client-onboarding-model.test.mjs` | 7–8 | CCT 模型 + `risk-compliance-service/docs/database/risk-compliance.pdma.json` | `paytech/client-master/…` + `paytech/compliance/…` | 4、6 |
| `scripts/pdmaner/oe-client-model.test.mjs` | 51、56 | 四个旧根名；`path.join(root, repository, "docs/database/sql", name-schema.sql)` | 与 `generate-oe-client-schemas.mjs` 同一清单 | 3–7 |
| `scripts/pdmaner/commerce-model.test.mjs` | 12、15、23、27 | `commerce-service/docs/database/{commerce.pdma.json, COMMERCE_PHYSICAL_DECISIONS.md, sql/commerce-schema.sql, sql/commerce-initial-data.sql}` | acquiring / consumer 两份 | 3 |
| `scripts/pdmaner/commerce-scenario-doc.test.mjs` | 9、13 | 同上 | 同上 | 3 |
| `scripts/pdmaner/payment-model.test.mjs` | 12–13 | `payment-orchestration-service/docs/database/{payment-orchestration.pdma.json, PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md}` | payment / channel-connector / card 三份 | 5 |
| `scripts/pdmaner/payment-scenario-doc.test.mjs` | 8 | 同模型路径 | 同上 | 5 |
| `scripts/pdmaner/payment-channel-query.test.mjs` | 20、22 | 模型路径；`../../payment-orchestration-service/docs/database/sql/payment-orchestration-schema.sql` | `paytech/channel-connector/docs/database/…` | 5 |
| `scripts/pdmaner/payment-execution-simplification.test.mjs` | 6、39 | 同上 | `paytech/payment/docs/database/…` | 5 |
| `scripts/pdmaner/risk-compliance-model.test.mjs` | 46–47 | `risk-compliance-service/docs/database/{risk-compliance.pdma.json, RISK_COMPLIANCE_PHYSICAL_DECISIONS.md}` | risk-decision / compliance 两份 | 6 |
| `scripts/pdmaner/risk-compliance-scenario-doc.test.mjs` | 11、14 | 同上 | 同上 | 6 |
| `scripts/pdmaner/risk-compliance-schema.test.mjs` | 8（10 派生） | `new URL("../../risk-compliance-service/")` | 两服务各一份 | 6 |
| `scripts/pdmaner/risk-compliance-initial-data.test.mjs` | 6（7、405 派生） | 同上；`docs/database/sql/2026-09-11-risk-rule-v1-configuration.sql` | 规则配置 DML 归 `paytech/risk-decision/docs/database/sql/` | 6 |
| `scripts/pdmaner/risk-variable-model.test.mjs` | 6 | `risk-compliance-service/docs/database/risk-compliance.pdma.json` | `paytech/risk-decision/…` | 6 |
| `scripts/pdmaner/risk-decision-engine-contract.test.mjs` | 30、34 | 模型路径；`risk-compliance-service/risk-test/src/test/resources/fixtures/risk-decision-v1-golden-vectors.json` | `paytech/risk-decision/docs/database/risk-decision.pdma.json`；`paytech/risk-decision/paytech-risk-decision-test/src/test/resources/fixtures/…` | 6 |
| `scripts/pdmaner/risk-dispute-balanced-optimization.test.mjs`、`risk-limit-balanced-simplification.test.mjs`、`risk-settlement-eligibility-balanced-simplification.test.mjs` | 10 / 12 / 14 | `risk-compliance-service/docs/database/risk-compliance.pdma.json` | 争议 → `dispute.pdma.json`；限额、结算控制 → `paytech/risk-decision/…`（C3） | 6 |
| `scripts/pdmaner/finance-model.test.mjs` | 40–41 | `finance-service/docs/database/{finance.pdma.json, FINANCE_PHYSICAL_DECISIONS.md}` | 五服务各一份（或参数化） | 7 |
| `scripts/pdmaner/finance-scenario-doc.test.mjs` | 20 | `finance-service/docs/database/finance.pdma.json` | 同上 | 7 |
| `scripts/pdmaner/finance-manual-payout-simplification.test.mjs` | — | 经 `finance/manifest.mjs` 读 Manifest，不读模型路径 | 随 Manifest 06 归 payout | 7 |
| 无路径常量、随目录搬迁即可：`*-field-metadata.mjs`、`*-field-overrides.mjs`、`payment-channel-identity.mjs`、`client-*-field-storage.mjs`、`risk/*canonicalizer*.mjs`、`risk/*validator*.mjs`、`*/schema-dsl.mjs`、`*/model-policy.mjs`、`*/manifest.mjs`、`*-test-oracle/*` | — | — | Manifest 重新分组时 `manifest.mjs` 的 `import` 列表改动 | — |

---

## 5. (e) PDManer 规范模型路径规则

### 5.1 规则映射

现行规则（`AGENTS.md:137`、`DATABASE_DESIGN_GUIDE.md`）：每个持库项目恰有一个规范模型 `<project-root>/docs/database/<service-short-name>.pdma.json`。V2 下 `<project-root>` = `paytech/<service>/`，`<service-short-name>` = 服务目录名（本身已是小写 kebab-case）。每个逻辑服务独立库 ⇒ **一服务一模型**，现有 7 个模型拆为 17 个（Java 持库服务）+ 1 个 Vault。`PHYSICAL_DECISIONS.md`、`sql/<svc>-schema.sql`、`sql/<svc>-initial-data.sql` 随模型按表归属拆分；`agent-docs/database/*_DATABASE.md` 目录文档不在本期改写范围，由 03 文档登记表归属即可。

### 5.2 模型与 Manifest 分配

| 现模型 | 新模型（`paytech/<svc>/docs/database/<svc>.pdma.json`） | 来源分配 |
|---|---|---|
| `platform-support-service/docs/database/platform-support.pdma.json` | `platform-support.pdma.json` | `scripts/pdmaner/platform-support/manifests/01–09` 全部，仅改输出路径 |
| `vault-service/docs/database/vault.pdma.json` | `vault.pdma.json` | `scripts/pdmaner/vault/` 全部，仅改路径 |
| `client-contract-service/docs/database/client-contract.pdma.json`（内联 + `agent-docs/database/CLIENT_CONTRACT_DATABASE.md` 目录） | `client-master.pdma.json`（21 表）、`commercial.pdma.json`（11 表 + `t_consumer_legal_document`、`t_seller_disclosure`，C4） | 表清单见 analysis/cct.md 第 1 节；BCR、补偿表两边各一份 |
| `commerce-service/docs/database/commerce.pdma.json`（内联 `defKey` 分组：order_context、checkout_consumer、payment_refund_position、reliability…） | `acquiring.pdma.json`（transaction 7 + checkout 5 + command 1 + 补偿）、`consumer.pdma.json`（`t_customer`、`t_client_stored_card_setting`、补偿） | analysis/commerce.md 第 2 节；`t_command_idempotency` 删除（C7） |
| `payment-orchestration-service/docs/database/payment-orchestration.pdma.json`（内联） | `payment.pdma.json`、`channel-connector.pdma.json`、`card.pdma.json` | analysis/payment.md 表归属 + 03 文档 S7 结论；`card` 模型需求步 12 前即可存在，库到 12 步分离 |
| `risk-compliance-service/docs/database/risk-compliance.pdma.json` | `risk-decision.pdma.json` ← Manifest `02-decision-engine`、`03-evaluation`、`04-risk-data`、`05-limits`、`06-settlement`（C3）、`09-decision-flow`、`10-variables`、`08-reliability`（补偿）；`compliance.pdma.json` ← `01-due-diligence`、`08-reliability`（补偿）；`dispute.pdma.json` ← `07-dispute` | 07 文档第 7 节 |
| `finance-service/docs/database/finance.pdma.json` | `ledger.pdma.json` ← `01-accounting-foundation`（除 `account`）、`03-accounting-journal`、`07-close`；`account.pdma.json` ← `01` 的 `account` + 需求步 11 新增余额表；`billing.pdma.json` ← `02` 的 `transaction_economics*`、`channel_cost_plan*`、`channel_cost_scope/component`；`reconciliation.pdma.json` ← `05-channel-cash-recon` 全部 + `02` 的 `channel_cost_confirm/alloc`；`tax.pdma.json` ← `02` 的 `tax_*`、`place_of_supply_rule`、`fiscal_*`；`payout`（模块）← `06-payout`；`08-reliability` 各库一份；`currency_spec`、`finance_entity` 共享引用数据按 03 文档 | 03、04 文档 |

`pdmaner-canonical-paths.test.mjs` 的合同清单改为：`platform-support`、`vault`、`client-master`、`commercial`、`acquiring`、`consumer`、`payment`、`channel-connector`、`card`、`risk-decision`、`compliance`、`billing`、`account`、`ledger`、`reconciliation`、`tax`（`dispute`、`payout` 以模块库形式登记时同样加入）。Gateway 与两个 BFF 无库，不进清单。

---

## 6. (f) 文档相对链接

### 6.1 统计（命令见第 8 节）

- **入向**（仓库级文档指向 12 个旧根）：`](../…/<旧根>/` 共 **520 条 / 141 文件**；按目标根：payment 130、platform 112、risk 64、commerce 64、gateway 43、operations-bff 31、cct 25、vault 19、foundation 18、finance 13、client-bff 1。按深度：`../` 77、`../../` 61、`../../../` 363、`../../../../` 12、五层 3、六层 4。其中位于 `agent-docs/`、`docs/`、`requirements/`、`AGENTS.md`、`README.md` 的 509 条 / 137 文件；其余在 `test-environment/README.md`（1）与旧根内部互链（10 条 / 3 文件，见下）。
- **出向**（旧根内部文档指向仓库级目录 `agent-docs|docs|requirements|local-environment|test-environment|scripts|AGENTS.md|README.md`）：**351 条 / 32 文件**。最多的：`commerce-service/README.md` 42、`platform-support-service/README.md` 35、`payment-orchestration-service/docs/development/processor-conventions.md` 29、`risk-compliance-service/docs/database/RISK_COMPLIANCE_PHYSICAL_DECISIONS.md` 27、`client-contract-service/docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md` 20、`operations-console-bff/README.md` 17、`payment-orchestration-service/README.md` 16、`payment-orchestration-service/docs/database/PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md` 16、`finance-service/docs/database/FINANCE_PHYSICAL_DECISIONS.md` 16、`platform-support-service/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md` 14、`commerce-service/docs/database/COMMERCE_PHYSICAL_DECISIONS.md` 14、`api-gateway/README.md` 12、`risk-compliance-service/README.md` 11、`commerce-service/docs/requirements/20260912-customer-checkout/implementation.md` 11、`client-contract-service/README.md` 11（其余 17 文件各 ≤ 8）。
- **旧根互链**：`paytech-foundation/README.md:529` → payment；`commerce-service/README.md:76,316` → foundation；`payment-orchestration-service/docs/development/command-idempotency-differences.md:23,27,88,137` → foundation、commerce（7 条）。
- 文档中的 `cd <旧根>` 命令 25 处 / 10 文件（agent-docs、requirements、test-environment、local-environment README），同样需要重写。

入向链接前 40 文件（完整 141 文件清单见附录 A）：`agent-docs/CURRENT_IMPLEMENTATION.md` 38、`agent-docs/plans/2026-09-10-risk-rule-editor-v2-assessments/gateway.md` 21、`requirements/20260913/platform-support-菜单权限与API配置管理/technique.md` 18、`requirements/20260914/platform-support-租户访问与成员权限查询/technique.md` 17、`requirements/20260912/operations-console-统一风控变量管理与条件编排/technique.md` 17、`requirements/20260911/client-contract-OE与Client模型梳理/technique.md` 16、`agent-docs/database/README.md` 16、`requirements/20260913/payment-Execution设计/instrument-token-development-checks.md` 15、`requirements/20260913/payment-Execution设计/design.md` 14、`agent-docs/examples/WRITE_API_EXAMPLE.md` 14、`agent-docs/plans/2026-09-10-risk-rule-editor-v2-assessments/platform-support.md` 13、`requirements/20260914/client-console-注册与客户入驻/technique.md` 12、`requirements/20260913/platform-support-ClientAPI接入管理/development.md` 12、`agent-docs/README.md` 12、`agent-docs/plans/2026-09-10-risk-rule-editor-v2-assessments/operations-bff.md` 12、`requirements/20260913/platform-support-ClientAPI接入管理/technique.md` 11、`requirements/20260911/operations-console-风控规则集历史与字典管理/gateway-development.md` 8、`requirements/20260911/client-contract-OE与Client模型梳理/model-implementation.md` 8、`requirements/20260913/payment-Execution设计/technique.md` 7、`requirements/20260912/commerce-CustomerCheckout接口设计/implementation-design.md` 7、`payment-orchestration-service/docs/development/command-idempotency-differences.md` 7、`agent-docs/plans/2026-09-11-risk-rule-v1-configuration-contract-draft.md` 7、`requirements/20260911/operations-console-风控规则集列表优化/technique.md` 6、`requirements/20260913/payment-Execution设计/{delivery,create-intent-amendment,apply-card-instrument-amendment}.md` 各 5、`requirements/20260912/payment-自建CardVault/technique.md` 5、`requirements/20260912/operations-console-决策流配置/table-relationship-review.md` 5、`requirements/20260911/operations-console-风控规则集历史与字典管理/gateway-dynamic-routing-assessment.md` 5、`agent-docs/VAULT_SERVICE_ARCHITECTURE.md` 5、`agent-docs/plans/2026-09-10-risk-rule-editor-v2-assessments/risk.md` 5、`agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md` 5、`requirements/20260921/payment-渠道账号与MID能力查询/technique.md` 4、`requirements/20260914/client-console-注册与客户入驻/migration-plan.md` 4、`requirements/20260913/payment-Execution设计/instrument-token-amendment.md` 4、`agent-docs/DATABASE_DESIGN_GUIDE.md` 4。

### 6.2 机械重写规则

两条规则，一个映射表，一次性在需求步“旧根搬空”（计划 13）执行；在此之前各步只修改会导致**测试或脚本失败**的链接（2.16、4.x），文档链接允许暂时失效，`delivery.md` 登记。

1. **入向**：对每个 `](` 后的相对路径，先归一化出 `(../)*` 前缀与其后的路径 `P`；若 `P` 以 `<旧根>/` 开头，查映射表取最长匹配前缀 `old → new`，输出 `(../)*paytech/<new>`。`(../)*` 层数不变（仓库级目录不动）。
2. **出向**：对 12 个旧根内部的 Markdown，若链接目标归一化后落在仓库级目录（`agent-docs|docs|requirements|local-environment|test-environment|scripts|requirements-site|AGENTS.md|README.md`），在 `(../)*` 前缀上**追加一个 `../`**（`paytech/` 多一层；模块改名不改深度：`<旧根>/<旧模块>/…` → `paytech/<svc>/paytech-<svc>-<x>/…`）。旧根互链同时套用规则 1。
3. **映射表**（最长前缀优先；业务服务按 03 文档的模块→服务归属表补全模块级条目）：

| 旧前缀 | 新前缀 |
|---|---|
| `paytech-build/paytech-dependencies/` / `paytech-build/paytech-parent/` | `build/paytech-dependencies/` / `build/paytech-parent/` |
| `paytech-foundation/` | `foundation/` |
| `platform-support-service/platform-support-web/src/main/java/…/{configuration,ext/rest}/` | `platform-support/paytech-platform-support-app/…` |
| `platform-support-service/platform-support-{api}/` | `platform-support/paytech-platform-support-api/` |
| `platform-support-service/platform-support-{access-control,audit-security,file-document,delivery,integration,web}/` | `platform-support/paytech-platform-support-core/`（web 中 Controller / 装配类除外，见上一行） |
| `platform-support-service/platform-support-test/` | `platform-support/paytech-platform-support-test/` |
| `platform-support-service/` | `platform-support/` |
| `api-gateway/api-gateway-web/` → `api-gateway/paytech-api-gateway-{core,app}/`；`api-gateway/api-gateway-test/` → `…-test/`；`api-gateway/` | `api-gateway/` |
| `operations-console-bff/paytech-operations-console-bff-web/` → `operations-console-bff/paytech-operations-console-bff-{core,app}/`；`…-test/` 同名；根 | `operations-console-bff/` |
| `client-console-bff/…` | 同上模式 |
| `vault-service/` | `vault/`（内部结构不变） |
| `commerce-service/paytech-commerce-client-api/` → `acquiring/paytech-acquiring-api/`；`paytech-commerce-customer-api/`、`paytech-commerce-web/…/customer/` → `consumer/…`；`paytech-commerce-web/` → `acquiring/paytech-acquiring-{core,app}/`；`paytech-commerce-test/` → 按测试归属；`commerce-service/docs/database/` → `acquiring/docs/database/`（consumer 表另列） | 03 文档 |
| `client-contract-service/` → `client-master/` 或 `commercial/`（按包：`agreement`、`pricing`、`commercial` → commercial，其余 → client-master） | 03、06 文档 |
| `payment-orchestration-service/paytech-payment-channel/` → `channel-connector/paytech-channel-connector-core/`；`paytech-payment-card/` → `card/paytech-card-core/`；`paytech-payment-{api,execution,routing,web}/` → `payment/paytech-payment-{api,core,core,app}/`；`docs/` → `payment/docs/` | 03、05 文档 |
| `risk-compliance-service/risk-compliance/` → `compliance/paytech-compliance-core/`；`risk-{decision,control}/` → `risk-decision/paytech-risk-decision-core/`；`risk-api/…/compliance/` → `compliance/paytech-compliance-api/`，其余 `risk-api/` → `risk-decision/paytech-risk-decision-api/`；`risk-web/`、`risk-test/` 按归属 | 07 文档 |
| `finance-service/paytech-finance-accounting/` → `ledger/paytech-ledger-core/`（account 包 → `account/…`）；`paytech-finance-tax/` → `tax/…`；`paytech-finance-treasury/` → `reconciliation/…`；`docs/database/` → 按表 | 03、04 文档 |

4. **实现**：新增 `scripts/rewrite-moved-links.mjs`（输入映射表 JSON，`--check` 只报告、`--write` 改写）与 `scripts/verify-markdown-links.mjs`（解析全仓 `*.md` 相对链接，去掉 `#` 锚点后用 `fs.existsSync` 校验，失败列出 `文件:行:链接`）。后者在计划 13 合入前必须为 0 失败，并加入 `npm --prefix requirements-site test` 之前的本地检查序列（不改 requirements-site 自身测试）。
5. **不改写**：`requirements/**` 下的 `db/<旧根>/…` 目录名、`assets/` 证据与 `docs/superpowers/plans/*.md` 历史计划里的命令文本（它们是过程记录，目录名作为历史标识保留）；只改 Markdown 链接目标使其可解析。`agent-docs/` 现行正文的链接目标**允许**改写（纯路径修正，不是权威正文语义修订；requirement.md 2.3 第 4 条针对的是规范语义）。

---

## 7. (g) 硬编码绝对路径 `/Users/kenny/Desktop/Workspace/Code-MoR`

41 个文件、约 400 处；在 `Code-MoR-refactor` 副本中全部指向旧工作区。

| 类别 | 文件（处数） | 处理 | 需求步 |
|---|---|---|---|
| 可执行脚本（requirements 阶段资产，历史 harness） | `requirements/20260912/operations-console-统一风控变量管理与条件编排/assets/stage5/harness/web.mjs:5`（`dist='…/operations-console-web/dist'`）、`…/start.py:3`；`requirements/20260911/operations-console-风控规则集历史与字典管理/assets/stage5/harness-snapshot/start_runtime.py:4`、`assets/stage6/preparation-scripts/{preflight,build_images,final_checks}.py:3`、`assets/stage7/deploy.py:12`、`assets/stage7/create_demo_data.py:10` | 不改写：这些是已交付阶段的证据脚本，不在任何自动化中执行；在 `delivery.md` 登记“历史资产，指向旧工作区”。若日后需要复跑，改为 `Path(__file__).resolve().parents[N]` | 13（登记） |
| 自动化测试断言 | `test-environment/scripts/test_risk_rule_configuration_authorization_inventory.py:776` 断言计划文档文本含 `nginx_probe_config='/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/nginx/nginx.conf'` | 它核对的是 `docs/superpowers/plans/2026-09-07-risk-operations-local-integration.md` 的文本（该文档 119 处绝对路径），不触文件系统；文档不改则不受影响。若第 6 节重写该文档链接，不触及这段 bash 文本 | — |
| JSON 证据 | `requirements/20260911/operations-console-风控规则集列表优化/assets/stage6-preflight.json:20,160,183`、`requirements/20260913/platform-support-菜单权限与API配置管理/assets/stage6/release-manifest.json:3`、`requirements/20260914/client-console-注册与客户入驻/assets/stage4/cct-*-verification-20260915.json`（4 处）、`requirements/20260911/…/assets/stage7/deployment.json:90` | 不改写（验收证据） | — |
| 历史计划 / 评审文档 | `docs/superpowers/plans/*.md` 16 个文件（119、35、23、23、19、19、16、15、12、11、9、8、8、6、4、2、2、2、1 处）、`agent-docs/plans/2026-08-27-finance-funding-module-removal-implementation-plan.md`（2）、`vault-service/docs/database/reviews/2026-09-04-vault-pdmaner-4.9.3-review.md`（1） | 不改写命令文本；仅第 6 节的 Markdown 链接目标重写 | 13 |
| 本需求自身 | `requirements/20261017/architecture-V2骨架与模块搬迁/analysis/{retained-systems(2),risk,payment,finance,commerce,cct}.md` 开头的工作区说明 | 保留（说明盘点副本位置） | — |
| 代码与配置 | **零处**（Java、YAML、POM、Shell、compose 均无） | — | — |

---

## 8. 验证方法

每条命令在仓库根执行，期望结果写在后面。阶段 4 各步在合并前跑与该步相关的条目；计划 13 全部跑一遍。

```bash
# (a) 没有空 relativePath、没有 multiModuleProjectDirectory、Wrapper 只剩一份
grep -rn --include=pom.xml '<relativePath/>' paytech | wc -l                    # 0
grep -rn --include=pom.xml 'multiModuleProjectDirectory' paytech | wc -l        # 0
find . -name mvnw -not -path './node_modules/*' -not -path './paytech/mvnw'     # 无输出
# 干净本地仓库下父 POM 可从 Reactor 解析（不依赖 ~/.m3 中已安装的 paytech-parent）
(cd paytech && ./mvnw -q -Dmaven.repo.local="$(mktemp -d)" -o help:effective-pom -N >/dev/null && echo PARENT_OK) 2>&1 | tail -1
# Gateway / BFF 禁令真正生效：人为加一条对任一 *-core 的依赖后 enforcer 必须失败
(cd paytech && ./mvnw -q -pl api-gateway/paytech-api-gateway-app enforcer:enforce)   # 0 失败
# 覆盖率门禁不再阻断默认 verify，仅在 Profile 下执行
(cd paytech && ./mvnw -pl platform-support/paytech-platform-support-test -am -DskipTests verify)  # 成功
(cd paytech && ./mvnw -pl platform-support/paytech-platform-support-test -am -Pcoverage-gate verify) # 允许失败（既有问题）

# (b) 定位仓库的测试
(cd paytech && ./mvnw -pl platform-support/paytech-platform-support-test -am -Dtest='LocalMockEnvironmentInitializerTest,OnboardingFile*' test)
(cd paytech && ./mvnw -pl operations-console-bff/paytech-operations-console-bff-test -am -Dtest='RiskClientConfigurationAutoTest,OperationsConsoleBffApplicationAutoTest' test)
(cd paytech && ./mvnw -pl foundation/paytech-redis-starter -Dtest='ResourceLockArchitectureAutoTest,RedisLocalEnvironmentPreconditionAutoTest' test)
(cd paytech && ./mvnw -pl vault/paytech-vault-test -am -Dtest=VaultEndToEndTest test)
(cd paytech && ./mvnw -pl payment/paytech-payment-test -am -Dtest='*ModuleBoundary*' test)   # 以及 risk-decision / compliance / 五个 Finance 服务的边界测试
npm --prefix requirements-site test                                             # site-content.test.mjs 读到 paytech/payment 文档

# (c) 脚本
bash -n test-environment/scripts/build.sh test-environment/scripts/build-vault.sh local-environment/vault/vault.sh paytech/vault/scripts/verify.sh
python3 -m unittest discover -s test-environment/scripts -p 'test_deploy_script.py'
python3 -m unittest discover -s test-environment/scripts -p 'test_nginx_gateway_problem_boundary.py'
python3 -m unittest discover -s test-environment/scripts -p 'test_risk_*.py'
python3 - <<'PY'
import pathlib,re
root=pathlib.Path('.')
bad=[l for f in ['test-environment/scripts/initialize.py','test-environment/scripts/vault.py','test-environment/scripts/test_risk_database_initialization.py','test-environment/scripts/test_risk_rule_configuration_catalog.py','test-environment/scripts/test_nginx_gateway_problem_boundary.py'] for l in open(f) if re.search(r'"(risk-compliance-service|platform-support-service|vault-service|api-gateway)/',l)]
print('OLD_PATHS',len(bad))   # 0
PY
grep -rn -E '\$workspace(_dir)?/(paytech-build|paytech-foundation|risk-compliance-service|platform-support-service|api-gateway|operations-console-bff|vault-service)' test-environment local-environment | wc -l   # 0

# (d) Node 工具
node scripts/verify-java-foundation.mjs --self-test && node scripts/verify-java-foundation.mjs
node scripts/verify-local-nexus-settings.mjs "$HOME/.m2/settings.xml"
node paytech/api-gateway/scripts/verify-gateway-boundaries.mjs
(cd paytech && ./mvnw -pl api-gateway/paytech-api-gateway-test -am verify) && node paytech/api-gateway/scripts/verify-jacoco.mjs
node --test scripts/pdmaner/*.test.mjs                                          # 全部通过
grep -rn -E '(client-contract-service|commerce-service|payment-orchestration-service|risk-compliance-service|finance-service|platform-support-service|vault-service)/docs/database' scripts | wc -l   # 0

# (e) PDManer 规范路径
node --test scripts/pdmaner/pdmaner-canonical-paths.test.mjs
for s in platform-support vault client-master commercial acquiring consumer payment channel-connector card risk-decision compliance billing account ledger reconciliation tax; do
  ls paytech/$s/docs/database/$s.pdma.json >/dev/null || echo "MISSING $s"; done   # 无输出
find paytech -name '*.pdma.json' | wc -l                                        # 等于清单条目数；旧根下 find . -path ./paytech -prune -o -name '*.pdma.json' -print 仅剩 agent-docs/templates

# (f) 文档链接
node scripts/verify-markdown-links.mjs                                          # 0 broken
grep -rhoE --include='*.md' "\]\((\.\./)+(paytech-build|paytech-foundation|platform-support-service|api-gateway|operations-console-bff|client-console-bff|vault-service|client-contract-service|commerce-service|payment-orchestration-service|risk-compliance-service|finance-service)[/)#]" . | grep -v node_modules | wc -l   # 0
grep -rhoE --include='*.md' '\bcd (paytech-build|paytech-foundation|platform-support-service|api-gateway|operations-console-bff|client-console-bff|vault-service|client-contract-service|commerce-service|payment-orchestration-service|risk-compliance-service|finance-service)\b' agent-docs requirements docs test-environment local-environment README.md AGENTS.md | wc -l   # 0

# (g) 绝对路径：代码与配置仍为零（文档与历史资产不计）
grep -rlI 'Users/kenny/Desktop/Workspace/Code-MoR' --include='*.java' --include='*.xml' --include='*.yml' --include='*.yaml' --include='*.sh' --include='*.mjs' --include='*.py' . | grep -v -E 'node_modules|requirements/.*/assets/|test_risk_rule_configuration_authorization_inventory.py' | wc -l   # 0

# 验收 6：旧根搬空且历史可追溯
for d in paytech-build paytech-foundation platform-support-service api-gateway operations-console-bff client-console-bff vault-service client-contract-service commerce-service payment-orchestration-service risk-compliance-service finance-service; do [ -e $d ] && echo "REMAINS $d"; done   # 无输出
git log --follow --oneline -- paytech/platform-support/paytech-platform-support-test/src/test/java/com/paytech/platform/support/test/configuration/LocalMockEnvironmentInitializer.java | tail -1   # 显示搬迁前的首次提交
```

## 附录 A · 入向链接完整文件清单（`文件:链接数`，生成命令见 8 (f)）

```text
agent-docs/CURRENT_IMPLEMENTATION.md:38
agent-docs/plans/2026-09-10-risk-rule-editor-v2-assessments/gateway.md:21
requirements/20260913/platform-support-菜单权限与API配置管理/technique.md:18
requirements/20260914/platform-support-租户访问与成员权限查询/technique.md:17
requirements/20260912/operations-console-统一风控变量管理与条件编排/technique.md:17
requirements/20260911/client-contract-OE与Client模型梳理/technique.md:16
agent-docs/database/README.md:16
requirements/20260913/payment-Execution设计/instrument-token-development-checks.md:15
requirements/20260913/payment-Execution设计/design.md:14
agent-docs/examples/WRITE_API_EXAMPLE.md:14
agent-docs/plans/2026-09-10-risk-rule-editor-v2-assessments/platform-support.md:13
requirements/20260914/client-console-注册与客户入驻/technique.md:12
requirements/20260913/platform-support-ClientAPI接入管理/development.md:12
agent-docs/README.md:12
agent-docs/plans/2026-09-10-risk-rule-editor-v2-assessments/operations-bff.md:12
requirements/20260913/platform-support-ClientAPI接入管理/technique.md:11
requirements/20260911/operations-console-风控规则集历史与字典管理/gateway-development.md:8
requirements/20260911/client-contract-OE与Client模型梳理/model-implementation.md:8
requirements/20260913/payment-Execution设计/technique.md:7
requirements/20260912/commerce-CustomerCheckout接口设计/implementation-design.md:7
payment-orchestration-service/docs/development/command-idempotency-differences.md:7
agent-docs/plans/2026-09-11-risk-rule-v1-configuration-contract-draft.md:7
requirements/20260911/operations-console-风控规则集列表优化/technique.md:6
requirements/20260913/payment-Execution设计/delivery.md:5
requirements/20260913/payment-Execution设计/create-intent-amendment.md:5
requirements/20260913/payment-Execution设计/apply-card-instrument-amendment.md:5
requirements/20260912/payment-自建CardVault/technique.md:5
requirements/20260912/operations-console-决策流配置/table-relationship-review.md:5
requirements/20260911/operations-console-风控规则集历史与字典管理/gateway-dynamic-routing-assessment.md:5
agent-docs/VAULT_SERVICE_ARCHITECTURE.md:5
agent-docs/plans/2026-09-10-risk-rule-editor-v2-assessments/risk.md:5
agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md:5
requirements/20260921/payment-渠道账号与MID能力查询/technique.md:4
requirements/20260914/client-console-注册与客户入驻/migration-plan.md:4
requirements/20260913/payment-Execution设计/instrument-token-amendment.md:4
agent-docs/DATABASE_DESIGN_GUIDE.md:4
requirements/20260921/payment-渠道账号与MID能力查询/implementation-contract.md:3
requirements/20260917/commerce-CreateCheckout测试重建/technique.md:3
requirements/20260914/platform-support-外部API配置与执行/gap-analysis.md:3
requirements/20260914/client-console-注册与客户入驻/registration-design.md:3
requirements/20260913/payment-支付渠道命名统一/technique.md:3
requirements/20260911/operations-console-风控规则改动/technique.md:3
requirements/20260911/operations-console-风控规则集历史与字典管理/requirement.md:3
requirements/20260911/operations-console-风控规则集历史与字典管理/pending-registration-permissions.md:3
requirements/20260911/operations-console-风控规则集列表优化/delivery.md:3
agent-docs/TECHNOLOGY_STACK.md:3
requirements/20261014/payment-PayerMax卡直付对接/delivery.md:2
requirements/20260916/commerce-CreateCheckout业务重构/technique.md:2
requirements/20260914/platform-support-租户访问与成员权限查询/data-design.md:2
requirements/20260914/client-console-注册与客户入驻/cct-write-dependencies.md:2
requirements/20260913/platform-support-菜单权限与API配置管理/db/README.md:2
requirements/20260913/platform-support-菜单权限与API配置管理/db/platform-support-service/local-validation/README.md:2
requirements/20260913/payment-Execution设计/verification-plan.md:2
requirements/20260913/payment-Execution设计/requirement.md:2
requirements/20260913/payment-Execution设计/db/execution-simplified-migration.md:2
requirements/20260913/payment-Execution设计/db/command-idempotency-migration.md:2
requirements/20260913/payment-业务模块划分/technique.md:2
requirements/20260912/payment-自建CardVault/data-design.md:2
requirements/20260912/operations-console-决策流配置/technique.md:2
requirements/20260912/operations-console-决策流节点处理结果/technique.md:2
requirements/20260912/operations-console-统一风控变量管理与条件编排/stage5.md:2
requirements/20260912/operations-console-决策流配置/development-checks.md:2
requirements/20260912/commerce-CustomerCheckout接口设计/delivery.md:2
requirements/20260911/operations-console-风控规则集历史与字典管理/db/platform-support-service/local-validation/README.md:2
requirements/20260911/client-contract-OE与Client模型梳理/requirement.md:2
commerce-service/README.md:2
agent-docs/JAVA_PROJECT_STRUCTURE.md:2
agent-docs/DOCUMENT_GOVERNANCE.md:2
agent-docs/database/scenarios/CLIENT_CONTRACT_REAL_WORLD_SCENARIOS.md:2
agent-docs/database/RISK_COMPLIANCE_DATABASE.md:2
agent-docs/database/FINANCE_DATABASE.md:2
agent-docs/database/CLIENT_CONTRACT_DATABASE.md:2
test-environment/README.md:1
requirements/20261017/gateway-请求幂等IdempotencyKey/release/release-20261017.md:1
requirements/20261015/operations-console-通知管理/release/release.md:1
requirements/20261014/risk-支付反欺诈一期/technique.md:1
requirements/20261014/platform-support-渠道沙箱验证/release/release.md:1
requirements/20261014/operations-console-Vault数据类型与手动加解密/delivery.md:1
requirements/20260921/payment-渠道账号与MID能力查询/requirement.md:1
requirements/20260920/platform-support-Delivery领域规划/technique.md:1
requirements/20260919/commerce-Checkout准备与Intent创建拆分/technique.md:1
requirements/20260919/commerce-Checkout准备与Intent创建拆分/delivery.md:1
requirements/20260917/commerce-GetPayment接口重构/technique.md:1
requirements/20260916/foundation-PublicToken公共生成/technique.md:1
requirements/20260916/commerce-CreateRefund接口重构/technique.md:1
requirements/20260916/commerce-CancelOrder接口重构/requirement.md:1
requirements/20260914/platform-support-外部API配置与执行/registry-contract.md:1
requirements/20260914/platform-support-外部API配置与执行/dev-plan-step2.md:1
requirements/20260914/platform-support-外部API配置与执行/design.md:1
requirements/20260914/client-console-注册与客户入驻/release/release.md:1
requirements/20260914/client-console-注册与客户入驻/platform-tenant-projection-contract.md:1
requirements/20260914/client-console-注册与客户入驻/development-progress.md:1
requirements/20260914/client-console-注册与客户入驻/db/implementation/platform-auth-local-execution.md:1
requirements/20260914/client-console-注册与客户入驻/db/implementation/cct-local-execution.md:1
requirements/20260913/platform-support-ClientAPI接入管理/release.md:1
requirements/20260913/platform-support-ClientAPI接入管理/db/README.md:1
requirements/20260913/platform-support-ClientAPI接入管理/data-design.md:1
requirements/20260913/payment-Execution设计/terminate-intent-amendment.md:1
requirements/20260913/payment-Execution设计/internal-risk-action-amendment.md:1
requirements/20260913/payment-Execution设计/execution-state-channel-recovery-amendment.md:1
requirements/20260913/payment-Execution设计/execution-field-simplification-amendment.md:1
requirements/20260913/payment-Execution设计/development-checks.md:1
requirements/20260913/payment-Execution设计/db/card-pay-action-migration.md:1
requirements/20260913/payment-Execution设计/api-contract.md:1
requirements/20260913/payment-业务模块划分/requirement.md:1
requirements/20260912/payment-自建CardVault/requirement.md:1
requirements/20260912/payment-自建CardVault/api-contract.md:1
requirements/20260912/operations-console-决策流配置/simplification-development-checks.md:1
requirements/20260912/operations-console-决策流配置/db/platform-support-service/README.md:1
requirements/20260912/commerce-CustomerCheckout接口设计/technique.md:1
requirements/20260912/commerce-CustomerCheckout接口设计/interface-impact.md:1
requirements/20260911/operations-console-风控规则改动/delivery.md:1
requirements/20260911/client-contract-OE与Client模型梳理/cct-query-implementation.md:1
paytech-foundation/README.md:1
docs/superpowers/specs/2026-09-05-foundation-resource-lock-design.md:1
docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md:1
docs/superpowers/specs/2026-09-05-commerce-client-api-contract-module-design.md:1
docs/superpowers/specs/2026-09-04-risk-modular-code-design.md:1
docs/superpowers/specs/2026-09-01-platform-support-application-foundation-design.md:1
docs/superpowers/specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md:1
agent-docs/UNIT_TEST_GUIDE.md:1
agent-docs/UNIT_TEST_GUIDE_BEFORE_20260917.md:1
agent-docs/plans/2026-09-04-platform-schema-code-conventions-implementation.md:1
agent-docs/plans/2026-09-04-finance-modular-code-design.md:1
agent-docs/plans/2026-09-01-platform-support-database-simplification-implementation-plan.md:1
agent-docs/plans/2026-08-29-platform-support-operations-authentication-requirements.md:1
agent-docs/plans/2026-08-27-platform-support-pdmaner-implementation-plan.md:1
agent-docs/plans/2026-08-27-platform-support-balanced-physical-model-design.md:1
agent-docs/plans/2026-08-25-payment-provider-account-mid-simplification-implementation-plan.md:1
agent-docs/plans/2026-08-25-payment-provider-account-mid-simplification-design.md:1
agent-docs/plans/2026-08-25-payment-pdmaner-implementation-plan.md:1
agent-docs/plans/2026-08-25-payment-balanced-physical-model-design.md:1
agent-docs/plans/2026-08-21-cct-model-slimming-implementation-plan.md:1
agent-docs/plans/2026-08-21-cct-model-slimming-design.md:1
agent-docs/examples/UNIT_TEST_EXAMPLE.md:1
agent-docs/decisions/2026-09-16-payment-based-coding-conventions.md:1
agent-docs/decisions/2026-09-13-payment-execution-code-orchestration.md:1
agent-docs/decisions/2026-09-09-foundation-command-idempotency.md:1
agent-docs/decisions/2026-09-05-platform-controller-best-effort-audit.md:1
agent-docs/database/scenarios/PLATFORM_SUPPORT_REAL_WORLD_SCENARIOS.md:1
agent-docs/database/PAYMENT_ORCHESTRATION_DATABASE.md:1
```
