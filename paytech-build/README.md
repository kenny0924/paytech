# PayTech 统一构建

该目录是独立 Git 仓库，只提供 Maven 构建治理，不包含业务代码。仓库自带 Maven Wrapper，可以脱离 Code-MoR 根目录构建和发布。

- `paytech-dependencies`：统一导入 Spring Boot、Spring Cloud 和 Spring Cloud Alibaba BOM，并管理未被上游 BOM 覆盖的依赖版本。
- `paytech-parent`：统一 Java 25、编译/测试插件、默认 `auto` 测试范围和 Maven Enforcer 规则。

业务工程应继承 `com.paytech:paytech-parent`。确实不能继承统一 Parent 的工程，才单独在 `dependencyManagement` 中导入 `com.paytech:paytech-dependencies`。

只读构建验证命令（不会发布制品）：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw clean verify
```

本机 Nexus 发布（`deploy` 会写入 Nexus，必须显式执行）：

```bash
./mvnw --settings /Users/kenny/Desktop/Workspace/Settings/3-Maven/settings/local_settings.xml clean deploy
```

Snapshot 发布到 `maven-snapshots`，依赖消费者统一从 `maven-public` 读取。认证和 `nexus.url` 来自开发者 Maven Settings，不得写入 POM 或提交到仓库。
