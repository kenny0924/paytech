# 任务卡 00 · `paytech/` 骨架

- 分支：`refactor/step-00-skeleton`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-step00`；起点：`refactor-main@4ae80ac`。
- 执行者：阶段 4 执行 agent。主会话负责门禁与合入 `refactor-main`。
- 对应计划：[11-dev-plan.md](../design/11-dev-plan.md) 第 00 步。

## 1. 输入文档（先完整读取）

- [design/01-skeleton-and-conventions.md](../design/01-skeleton-and-conventions.md) 全文（目录与 Reactor、模块约定、坐标与端口、配置命名空间、实例清单 Schema v1、Enforcer 与 Surefire）。
- [design/02-archunit-rules.md](../design/02-archunit-rules.md) 全文（R1–R12 与实现要点）。
- [design/03-module-and-table-ownership.md](../design/03-module-and-table-ownership.md) 第 1 节（坐标总表、端口汇总）与第 4.2 节（目标库清单，用于表归属白名单文件的库名）。
- [design/09-path-coupling-fixes.md](../design/09-path-coupling-fixes.md) 第 0 节、1.1、1.3、1.4（Wrapper 行）、4.1（`verify-local-nexus-settings.mjs`、`verify-java-foundation.mjs`）。
- [design/10-test-baseline-and-cleanup.md](../design/10-test-baseline-and-cleanup.md) 第 4.4 节（`paytech.local-environment.dir`）。
- 现有 `paytech-build/paytech-dependencies/pom.xml`、`paytech-build/paytech-parent/pom.xml`、`paytech-build/README.md`、仓库根 `.mvn/wrapper/maven-wrapper.properties`、`commerce-service/.mvn/wrapper/maven-wrapper.properties`（含 `distributionSha256Sum`）。
- 仓库规范：`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/UNIT_TEST_GUIDE.md`（本步新增的 Java 工具模块按 V2 草案组织，但命名、枚举空行、`interface + Impl`、测试 `auto/` 目录等现行规则仍适用）。

## 2. 搬迁清单（`git mv`，保留历史）

| 源 | 目标 |
|---|---|
| `paytech-build/paytech-dependencies/` | `paytech/build/paytech-dependencies/` |
| `paytech-build/paytech-parent/` | `paytech/build/paytech-parent/` |
| `paytech-build/README.md` | `paytech/build/README.md`（内容改写为新位置与用法） |
| 仓库根 `mvnw`、`mvnw.cmd`、`.mvn/` | `paytech/mvnw`、`paytech/mvnw.cmd`、`paytech/.mvn/` |

删除（`git rm`）：`paytech-build/pom.xml`（聚合 POM 取消，两模块由 `paytech/pom.xml` 直接列出）、`paytech-build/mvnw`、`paytech-build/mvnw.cmd`、`paytech-build/.mvn/`、`paytech-build/.gitignore`（内容合并到 `paytech/.gitignore`）。搬完后 `paytech-build/` 必须为空目录并消失。

不动：`paytech-foundation/` 与 12 个服务根目录及其各自 `mvnw`（第 01、02 步处理）。

## 3. 新建清单

### 3.1 `paytech/pom.xml`

纯聚合 POM：`com.paytech:paytech:1.0.0-SNAPSHOT`，`packaging=pom`，**不设 `<parent>`**。`<modules>` 本步只列：`build/paytech-dependencies`、`build/paytech-parent`、`tools/arch-rules`、`tools/instance-manifest`。其余服务目录以注释占位，按 01 §1 顺序列出，后续步骤逐个取消注释。加 `paytech/.gitignore`（`target/`、IDE 文件）。

### 3.2 `paytech/build/paytech-parent/pom.xml` 调整

- `<parent>` 的 `relativePath` 保持 `../paytech-dependencies/pom.xml`。
- 新增属性 `paytech.local-environment.dir`，默认 `${maven.multiModuleProjectDirectory}/../local-environment`；在 Surefire 与 Failsafe 的 `systemPropertyVariables` 中透传同名系统属性（10 §4.4）。
- `paytech-dependencies` BOM 的 `dependencyManagement` 新增 `com.tngtech.archunit:archunit-junit5`（1.4.x 最新稳定版，发布满两周）与 `archunit`，版本以属性 `archunit.version` 管理。
- 其余规则不变（Java 25、Maven `[3.9.16,4)`、Surefire `auto/` 包含规则、`manual` Profile）。

### 3.3 `paytech/mvnw`

`maven-wrapper.properties` 固定 `wrapperVersion=3.3.4`、Maven 3.9.16、`distributionSha256Sum` 取 `commerce-service/.mvn/wrapper/maven-wrapper.properties` 的现值。

### 3.4 `paytech/tools/arch-rules`（`com.paytech:paytech-arch-rules`，普通 JAR）

- 包根 `com.paytech.arch`。父 POM `paytech-parent`（`relativePath` `../../build/paytech-parent/pom.xml`）。
- 以 ArchUnit 提供 R1–R12 的可复用规则（02 文档）；每条规则一个类或一个静态工厂方法，入参为服务名与 Maven 模块根路径集合，不依赖 Spring。R4、R11 的表白名单来自 `src/main/resources/table-ownership.yaml`（按 03 §2 / §4.2 生成：`service → database → [tables]`，本步只需填入 03 文档中的归属结果；允许用脚本从 03 文档 Markdown 表生成，脚本放 `tools/arch-rules/scripts/`）。R1、R3、R6、R8 的 Maven 依赖图部分实现为读取 Reactor `pom.xml` 树的纯 Java 解析器（不调用 Maven）。R10 为文本规则（枚举常量间一个空行）。
- `src/test/java/**/auto/**`：对每条规则各有通过与违规的 fixture（fixture 类放 `src/test/java/com/paytech/arch/fixture/**`，POM 树 fixture 放 `src/test/resources/`）。
- `paytech/tools/arch-rules-reactor-test`：`-test` 性质模块，一个 `ReactorArchitectureAutoTest` 读取 `paytech/pom.xml` 当前列出的全部模块并执行 R1、R2、R3、R6、R8 的依赖图部分；本步 Reactor 内无服务模块，测试需真实执行且通过（不是空断言）。

### 3.5 `paytech/tools/instance-manifest`（`com.paytech:paytech-instance-manifest`，普通 JAR）

- 包根 `com.paytech.instance.manifest`。
- `src/main/resources/schema/instance-manifest-v1.yaml`：按 01 §5 的字段定义 Schema（字段、类型、必填、枚举 `operationMode ∈ {SELF, MANAGED}`、`businessModes ⊆ {MOR, PSP}`、`services.<name>` 的 `port / snowflakeWorkerId / db`、`versions.schema` 每服务整数）。
- 校验器：`InstanceManifestValidator`（`interface + Impl`）读取 YAML（Jackson YAML，版本由 Spring Boot BOM 管理），校验 Schema、服务名必须属于 03 §1.2 的服务清单、端口与 worker id 不重复、`modules` 只能出现 01 §5 列出的可选模块、brands 的 `legalPartyRef` 非空。错误用结构化 `ManifestViolation(path, code, message)` 列表返回，不抛 `RuntimeException` 作为控制流。
- 生成器：`LocalEnvironmentComposeFragmentGenerator`（`interface + Impl`）按清单输出 `local-environment` 的 compose 片段（每个 `services.<name>` 一个 service 块：镜像占位 `paytech/<name>-service:${version}`、端口映射 `port` 与 `port+1000`、环境变量 `SPRING_APPLICATION_NAME`、`PAYTECH_SNOWFLAKE_WORKER_ID`、`PAYTECH_DB_NAME`）。只生成文本，不写入 `local-environment/`。
- CLI：`com.paytech.instance.manifest.cli.InstanceManifestCli`（`validate <file>` / `compose <file> <out>`），`main` 退出码 0 / 1。
- `src/test/resources/manifests/`：一份合法样例（`main.yaml`，与 01 §5 示例一致、端口按 03 §1.2）与至少 5 份非法样例；`auto/` 测试覆盖校验全部规则与生成器输出的快照比对。

### 3.6 空服务目录骨架

为 03 §1.2 的每个逻辑服务目录（`acquiring` … `vault`，含 `partner-console-bff` 不建）创建 `paytech/<service>/README.md`，内容固定四行：服务名、`spring.application.name`、端口、数据库、来源现有工程。不创建 POM、不创建源码目录。

### 3.7 脚本

- `paytech/scripts/verify-reactor.sh`：`cd paytech && ./mvnw -q -B verify`，再运行 `arch-rules-reactor-test`（已包含在 verify 内则只做一次），输出摘要；用 `set -euo pipefail`。
- `scripts/verify-local-nexus-settings.mjs` 第 15–16 行：Wrapper 路径改为 `paytech/mvnw`（09 §4.1）。
- `scripts/verify-java-foundation.mjs`：**只改与 `paytech-build` 相关的部分**，使脚本在本步之后仍可运行：`buildRoot = join(repositoryRoot, 'paytech', 'build')`；`requiredFiles` 与 `expectedArtifactIds` 删除 `paytech-build/pom.xml` 项；Foundation 相关路径本步不动（第 01 步处理）。运行一次确认通过或只剩与本步无关的既有失败并在报告中列出。

## 4. 代码改动清单

本步无业务代码。改动只限第 2、3 节所列文件，以及 `paytech/build/README.md`。

## 5. 测试与验证要求

1. `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home && cd paytech && ./mvnw -B verify` 全绿；Maven 本地仓库为 `~/.m3/repository`，镜像为 `~/.m2/settings.xml` 中的本地 Nexus（`127.0.0.1:18081`）。ArchUnit 不在本地仓库，需经 Nexus 下载；若 Nexus 不可达，先 `curl` 确认，再查 `agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md` 的 Nexus 启动方式启动它；**不得修改 `~/.m2/settings.xml`**；仍不可用则在报告中标注阻塞并停止。
2. `cd paytech-foundation && ./mvnw -o -q validate` 仍成功（父 POM 仍从 `~/.m3` 解析，证明旧目录未被破坏）。
3. `cd paytech && ./mvnw -B install -pl build/paytech-dependencies,build/paytech-parent` 成功（后续步骤旧目录继续从本地仓库引导）。
4. `node scripts/verify-local-nexus-settings.mjs` 与 `node scripts/verify-java-foundation.mjs` 运行结果记录在报告中。
5. `git log --follow --oneline paytech/build/paytech-parent/pom.xml | tail -1` 能追溯到搬迁前的提交。
6. `git status` 干净，`paytech-build/` 不存在。

## 6. 禁止事项

- 不改任何业务代码、不动 `paytech-foundation/` 与 12 个服务目录、不改 `local-environment/`、`test-environment/`、`agent-docs/`。
- 不执行任何 SQL，不启动数据库、Redis、MQ。
- 不提交到 `refactor-main`、`main`，不推送，不创建 PR。
- 不调用任何 Superpowers skill。
- 不使用 `when(...).thenReturn(...)`、`@MockBean` 等行为桩；本步测试全为纯 Java。
- 文件新增与编辑无需询问；遇到设计文档未覆盖的决定，按 01 文档精神自行选择并在报告「待确认」中列出。

## 7. 交付物

1. 短分支 `refactor/step-00-skeleton` 上的提交（可多个，信息前缀 `refactor(step-00): `，末尾一行 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`）。
2. 报告文件 `/Users/kenny/Desktop/Workspace/Code-MoR-step00/requirements/20261017/architecture-V2骨架与模块搬迁/tasks/00-skeleton-report.md`（随分支提交），内容：改动摘要 ≤ 30 行；第 5 节每项的实际命令与结果（含测试数）；新增文件清单；待确认项；与设计文档的偏差。
3. 最终回复只给报告路径与一句话结论；完整内容以报告文件为准。
