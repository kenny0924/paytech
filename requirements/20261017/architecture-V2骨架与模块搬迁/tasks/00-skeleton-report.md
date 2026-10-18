# 第 00 步报告 · `paytech/` 骨架

- 分支：`refactor/step-00-skeleton`（起点 `refactor-main@4ae80ac`）；实现提交 `3ced736`，本报告随后单独提交。
- 结论：第 5 节 6 项验证全部按要求执行；1、2、3、5 通过；4 中 `verify-local-nexus-settings.mjs` 通过，`verify-java-foundation.mjs` 仍失败，但失败输出与改动前基线逐行一致，属既有问题，与本步无关；6 中 `paytech-build/` 已不存在，工作区只剩主会话放入的未跟踪任务卡（见第 2 节第 6 项）。

## 1. 改动摘要

1. `git mv`：`paytech-build/paytech-dependencies`、`paytech-parent` → `paytech/build/`；`paytech-build/README.md` → `paytech/build/README.md`（重写）；`paytech-build/.gitignore` → `paytech/.gitignore`；仓库根 `mvnw`、`mvnw.cmd`、`.mvn/` → `paytech/`。
2. `git rm`：`paytech-build/pom.xml`、`paytech-build/mvnw`、`mvnw.cmd`、`.mvn/`。`paytech-build/` 已消失。
3. `paytech/.mvn/wrapper/maven-wrapper.properties`：3.3.4 / Maven 3.9.16，`distributionSha256Sum=5af3b743…89ce`（取自 commerce-service）。
4. `paytech/pom.xml`：纯聚合 `com.paytech:paytech`，无 parent；列出 build 两模块与 tools 三模块；其余 25 个目录以注释占位。
5. 父 POM：新增属性 `paytech.local-environment.dir`，经 Surefire 与 Failsafe 的 `systemPropertyVariables` 透传。BOM：新增 `archunit` 与 `archunit-junit5`，版本属性 `archunit.version=1.4.2`。
6. `tools/arch-rules`（`com.paytech.arch`）：R1–R12 每条一个规则类，统一入口为 `ArchitectureRules`。
   - 服务级入口 `checkService(service, moduleRoots)`。
   - Reactor 级入口 `checkReactorDependencyGraph(root)`。
   - 依赖图部分由纯 Java DOM 读取 POM，支持继承 Reactor 内父 POM 的 `<dependencies>`。
   - import 部分用 ArchUnit，类按所在模块判定归属，而不是按包名。
   - R10 是文本规则；R12 用 JDK ClassFile API 读取 `eventType()` 常量。
7. `table-ownership.yaml` 由 `tools/arch-rules/scripts/generate-table-ownership.mjs` 从 design/03 §2 生成。
   - 脚本自检 255 张来源表，且 17 个库的表数逐库等于 §4.2，合计 265。
   - 加 `--check` 参数时检查白名单是否过期。
8. `tools/arch-rules-reactor-test`：`ReactorArchitectureAutoTest` 读取真实 `paytech/pom.xml` 全部模块，执行 R1/R2/R3/R6/R8，结果 0 违规。另向真实模型注入违规模块，证明五条规则都会命中。
9. `tools/instance-manifest`（`com.paytech.instance.manifest`）包含以下部分：
   - Schema v1 YAML 与 25 个服务的服务清单。
   - `InstanceManifestValidator` 与 `Impl`：结构由 Schema 驱动，另含 6 条跨字段规则，结果以 `ManifestViolation(path, code, message)` 列表返回。
   - `LocalEnvironmentComposeFragmentGenerator` 与 `Impl`：只返回文本。
   - CLI `validate` 与 `compose`，退出码 0 或 1。
10. 24 个 `paytech/<service>/README.md`，不含 partner-console-bff；同时新增 `paytech/scripts/verify-reactor.sh`。
11. 仓库脚本两处改动：
    - `verify-local-nexus-settings.mjs` 的 Wrapper 改为 `paytech/mvnw`。
    - `verify-java-foundation.mjs` 的 `buildRoot` 改为 `paytech/build`，并删除三处 `paytech-build/pom.xml` 引用（requiredFiles、mavenModelFiles、expectedArtifactIds）。

## 2. 第 5 节验证（实际命令与结果）

所有 Maven 命令前均执行 `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`；本地仓库为 `~/.m3/repository`，依赖经 `~/.m2/settings.xml` 的本地 Nexus 下载，ArchUnit 1.4.2 下载成功，`settings.xml` 未改动。

1. `cd paytech && ./mvnw -B clean verify`：BUILD SUCCESS，退出码 0。

   | 模块 | 测试数 | 失败 / 错误 |
   |---|---|---|
   | paytech-arch-rules | 45 | 0 / 0 |
   | paytech-instance-manifest | 16 | 0 / 0 |
   | paytech-arch-rules-reactor-test | 2 | 0 / 0 |
   | 合计 | 63 | 0 / 0 |

   唯一警告是 reactor-test 模块的 "JAR will be empty"。该模块只有测试源码；为它单独配置 jar 插件需要在父 POM 增加插件版本管理，本步未做。
   `paytech/scripts/verify-reactor.sh` 的输出是 `table-ownership.yaml is current (265 physical tables)`，随后打印逐类摘要和 `total tests=63 failures+errors=0`，退出码 0。
2. `cd paytech-foundation && ./mvnw -o -q validate`：退出码 0。第 3 项把新父 POM 安装到本地仓库后又执行了一次，退出码仍为 0。
3. `cd paytech && ./mvnw -B install -pl build/paytech-dependencies,build/paytech-parent`：BUILD SUCCESS。两份 POM 已安装到 `~/.m3/repository/com/paytech/{paytech-dependencies,paytech-parent}/1.0.0-SNAPSHOT/`。
4. 两个仓库脚本：
   - `node scripts/verify-local-nexus-settings.mjs ~/.m2/settings.xml`：输出 "Local Maven Settings routes dependency resolution and deployment through Nexus."，退出码 0，证明经 `paytech/mvnw` 执行成功。不带参数运行时打印用法，退出码 2，这是脚本原有行为。
   - `node scripts/verify-java-foundation.mjs`：退出码 1，共 22 行输出，与改动前在 `4ae80ac` 上的基线 `diff` 完全一致。唯一的独立失败如下，其余 21 行 `static regression …` 是同一失败的连带结果，与 paytech-build 路径无关：

     ```text
     paytech-http-problem production dependencies must contain only com.fasterxml.jackson.core:jackson-annotations
     ```
5. `git log --follow --oneline paytech/build/paytech-parent/pom.xml | tail -1` 的输出是 `fff35d2 chore: consolidate Code-MoR into one repository`，即搬迁前的提交，共 2 条历史。
6. `git status --short`：已跟踪文件全部提交。唯一未跟踪项是主会话放入的 `tasks/00-skeleton.md`，它不属于本步改动，因此未提交。`ls paytech-build` 返回 No such file or directory。

## 3. 新增文件清单

- `paytech/pom.xml`、`paytech/.gitignore`（由搬迁而来）、`paytech/mvnw`、`paytech/mvnw.cmd`、`paytech/.mvn/wrapper/maven-wrapper.properties`、`paytech/scripts/verify-reactor.sh`。
- `paytech/build/README.md`、`paytech/build/paytech-dependencies/pom.xml`、`paytech/build/paytech-parent/pom.xml`（后两者由搬迁而来并有修改）。
- `paytech/<service>/README.md` × 24：acquiring、consumer、payment、channel-connector、card、client-master、commercial、risk-decision、compliance、billing、account、ledger、reconciliation、tax、payout、dispute、fx、data-service、platform-support、workflow-approval、api-gateway、client-console-bff、operations-console-bff、vault。
- `paytech/tools/arch-rules/`：
  - `pom.xml` 与 `scripts/generate-table-ownership.mjs`。
  - `src/main/resources/table-ownership.yaml`。
  - `src/main/java/com/paytech/arch/` 下的 `ArchitectureRules`，以及 `catalog/`、`model/`、`pom/`、`ownership/`、`scope/`、`support/` 和 `rule/`（R1–R12 共 12 个规则类加 3 个接口与记录类型）。
  - 测试：`src/test/java/com/paytech/arch/auto/` 下 16 个 `*AutoTest`；`test/` 下的 `Fixtures` 与 `ViolationChecker`；`fixture/` 下 61 个类 fixture；`src/test/resources/fixtures/` 下的 POM 树、Mapper XML、Java 文本与事件目录 fixture。
- `paytech/tools/arch-rules-reactor-test/`：`pom.xml` 与 `src/test/java/com/paytech/arch/reactor/auto/ReactorArchitectureAutoTest.java`。
- `paytech/tools/instance-manifest/`：
  - `pom.xml`、`src/main/resources/schema/instance-manifest-v1.yaml`、`src/main/resources/catalog/services.yaml`。
  - `src/main/java/com/paytech/instance/manifest/` 下的校验器、模型、`catalog/`、`schema/`、`compose/`、`cli/`。
  - 测试：`src/test/resources/manifests/` 下的 `main.yaml` 与 9 份非法样例；`snapshots/main-compose-fragment.yaml`；`auto/` 下 3 个测试类；`test/` 下 2 个辅助类。

## 4. 待确认

1. **ArchUnit 版本**：采用 1.4.2，发布于 2026-04-18，满足 1.4.x 最新且发布满两周。02 文档写的是 1.3.x，以任务卡为准。
2. **Reactor 模块**：`paytech/pom.xml` 额外列出了 `tools/arch-rules-reactor-test`。不列出它，`verify` 就不会执行 Reactor 测试。
3. **注释占位顺序**：按 09 §1.3 排列，并在 tax 之后补上 payout、dispute、fx、data-service、workflow-approval，这 5 个来自 03 §1.2。
4. **`paytech/pom.xml` 的 `distributionManagement`**：从被删除的 paytech-build 聚合 POM 原样搬入，保证聚合 POM 自身也能执行 `deploy`。它不是构建规则。
5. **依赖图口径**：所有依赖图规则只检查非 test 范围的依赖，因为 test 依赖不进入制品。
6. **R2 允许清单**：允许 `paytech-common-core`、`spring-web`、`jakarta.validation-api`，以及 `provided` 范围的 Lombok。`jackson-annotations` 等其他依赖一律判违规。
7. **R3 分层**：02 文档的领域层清单写有 `fx-rate`，但 03 中没有这个服务。实现上 fx 按 02 的流程层清单处理，`fx-rate` 不登记。
8. **R4 白名单口径**：
   - 01 §3 与 08 要求每库一张 `t_consumed_event`，但 03 没有列出，因此以 `sharedInfrastructureTables` 形式对所有有库服务放行。
   - 白名单使用当前表名，例如 `t_account`、`t_transaction_economics`。更名后的 `t_ledger_account`、`t_economic_fact` 等由对应步骤重新生成白名单时加入。
9. **R5 的注解范围**：02 原文可读作"@RabbitListener 之外"。实现按 01 §2 理解为 core 允许 MQ 消费注解，禁止以下注解：
   - `@SpringBootApplication`、`@SpringBootConfiguration`、`@EnableAutoConfiguration`。
   - `@Controller`（含 `@RestController`）、`@ControllerAdvice`（含 `@RestControllerAdvice`）。
   - 类或方法上的 `@RequestMapping` 及其派生注解。
10. **R7 的识别口径**：
    - "直接注入"指非 static 字段与构造器参数。
    - 事务类指类或方法带 `@Transactional`（Spring 或 Jakarta），或持有、调用 `TransactionOperations`。
    - HTTP 接口指归属任一服务 api 或 client 模块的接口，或带 `@HttpExchange` 元注解的接口。
11. **R8 范围**：服务的非 app 模块不得以非 test 范围依赖 `paytech-messaging`；core 与 api 类不得 import 它的类。
12. **R11 范围**：`paytech-card-test` 不在豁免之列，严格按 02 原文只豁免 card-core 和 card-app。Mapper 同时扫描 `src/main/resources` 与 `src/test/resources`。卡域表取白名单中 card 服务的 4 张表，并以 `t_payment_card*`、`t_payment_stored_card` 兜底匹配。
13. **R12 设计**：02 写的是"本服务的事件目录文件"，08 定义的则是 MQ 事件目录 `foundation/paytech-messaging/catalog/events.yaml`。补偿事件类型（如 `STORED_CARD_INVALIDATION`）与 MQ 事件不是同一集合。实现如下：
    - 目录文件定为 `paytech/<service>/compensation-event-catalog.yaml`，格式为 `eventTypes: [...]`。
    - `eventType()` 必须返回字面量或编译期常量，返回枚举 `getCode()` 时报告为无法核对。
    - `-test` 模块中的测试 Handler 不受检查。
    - 若要求改用 08 的全局目录，只需改规则读取的位置。
14. **Vault 角色**：`paytech-vault-client` 归类为 `CLIENT` 角色，对调用方等价于 api，但不受 R2 的 api 依赖清单约束。
15. **Schema 文件位置**：采用任务卡的 `src/main/resources/schema/instance-manifest-v1.yaml`，与 01 §5 写的 `tools/instance-manifest/schema/…` 不同。
16. **清单校验细则**：
    - `snowflakeWorkerId` 范围 0–31，因为 `SnowflakeIdGenerator` 的 worker 位只有 5 位。
    - `port` 范围 1024–64535，保证 port+1000 合法；业务端口与管理端口一并查重。
    - `db` 必须等于服务清单中的库名，无库服务不得声明。
    - `channels` 是开放目录，只校验大写格式，不做枚举。
    - `versions.schema` 只能登记已部署的服务；品牌 code 唯一；启用的模块必须带 version。
    - 服务名清单包含预留的 partner-console-bff。
17. **Compose 镜像与写入**：镜像占位 `${version}` 取 `versions.platform`。CLI 拒绝把片段写入任何名为 `local-environment` 的目录，落实"不写入 local-environment/"。
18. **服务 README 的"四行"**：理解为标题（服务名）加四行，分别是应用名、端口、数据库、来源工程。

## 5. 与设计文档的偏差及影响

- **共享本地仓库**：第 3 项把带 `paytech.local-environment.dir` 的父 POM 安装进了共享的 `~/.m3`。其他会话的旧工程从此解析到新父 POM，只多一个系统属性，没有其他行为变化。
- **以下位置仍引用 `paytech-build`**，均在本步禁改范围内，未处理：
  - `test-environment/scripts/build.sh:54` 执行 `cd paytech-build && ./mvnw … install`，**本步合入后会失败**。同目录的 `test_deploy_script.py:772` 与 `test_risk_rule_configuration_authorization_inventory.py:739-740` 断言了该文本。
  - `test-environment/scripts/prepare.py:39`、`vault-service/scripts/verify.sh:37`、`paytech-foundation/README.md:21` 只是提示文案。
  - 按 09 文档，这些属于第 01 / 02 步及 test-environment 相关步骤。
- **仓库根 Wrapper**：根目录的 `mvnw` 已移走。仍在仓库根执行 `./mvnw` 的人工习惯或文档需要改为 `paytech/mvnw`；本步只按 09 §4.1 修改了 `verify-local-nexus-settings.mjs`。
- **SLF4J 提示**：ArchUnit 在测试中打印 "No SLF4J providers were found"，属提示信息，不影响结果；本步未引入日志实现依赖。
