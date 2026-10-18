# PayTech 统一构建

`paytech/build/` 只提供 Maven 构建治理，不包含业务代码。两个模块由单一 Reactor `paytech/pom.xml` 直接列出，使用唯一 Wrapper `paytech/mvnw`（Wrapper 3.3.4 / Maven 3.9.16，固定 `distributionSha256Sum`）。

- `paytech-dependencies`：统一导入 Spring Boot、Spring Cloud 和 Spring Cloud Alibaba BOM，并管理未被上游 BOM 覆盖的依赖版本（含 ArchUnit `archunit` / `archunit-junit5`，属性 `archunit.version`）。
- `paytech-parent`：统一 Java 25、编译/测试插件、默认 `auto` 测试范围、`manual-test` Profile 和 Maven Enforcer 规则；属性 `paytech.local-environment.dir`（默认 `${maven.multiModuleProjectDirectory}/../local-environment`）经 Surefire / Failsafe 透传为同名系统属性，是测试定位 `local-environment/.env.local` 的唯一锚点。

`paytech/` 下的模块继承 `com.paytech:paytech-parent`，并写真实相对路径，例如 `paytech/<service>/<module>/pom.xml` 使用 `<relativePath>../../build/paytech-parent/pom.xml</relativePath>`。尚未搬入 `paytech/` 的旧工程继续以空 `relativePath` 从本地仓库解析父 POM，因此每次修改父 POM 或 BOM 后需要先安装这两个模块。

只读构建验证（不会发布制品）：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
cd paytech
./mvnw -B verify                  # 全部 Reactor 模块
scripts/verify-reactor.sh         # 同上，并核对表归属白名单与输出摘要
```

供旧工程引导父 POM（写入本地仓库）：

```bash
cd paytech
./mvnw -B install -pl build/paytech-dependencies,build/paytech-parent
```

本机 Nexus 发布（`deploy` 会写入 Nexus，必须显式执行）：

```bash
cd paytech
./mvnw --settings /Users/kenny/Desktop/Workspace/Settings/3-Maven/settings/local_settings.xml -pl build/paytech-dependencies,build/paytech-parent clean deploy
```

Snapshot 发布到 `maven-snapshots`，依赖消费者统一从 `maven-public` 读取。认证和 `nexus.url` 来自开发者 Maven Settings，不得写入 POM 或提交到仓库。
