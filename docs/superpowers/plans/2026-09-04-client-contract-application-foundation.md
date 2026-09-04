# Client & Contract Application Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 client-contract-service 初始化为可独立构建、可启动、可验证的三模块 Java 服务骨架，同时不实现任何 Client & Contract 业务能力，也不改动现有数据库设计资产。

**Architecture:** 使用 client-contract-api、client-contract-web、client-contract-test 三模块 Maven Reactor。API 只建立轻量契约边界；Web 只包含 Spring Boot 启动类、共享 MVC Starter 和同监听器 Actuator；Test 通过随机端口真实 MVC Server 验证启动、路由表、生产配置和健康端点。根 POM 通过 Maven Enforcer 冻结基础里程碑的依赖边界。

**Tech Stack:** Java 25、Maven Wrapper 3.9.16、Spring Boot 4.0.8、Spring MVC、PayTech MVC Web Starter、Actuator、Micrometer Prometheus、JUnit 5。

**Spec:** [Client & Contract Application Foundation Design](../specs/2026-09-04-client-contract-application-foundation-design.md)

## Execution Record — 2026-09-04

五个实现任务已完成，最终独立整分支审查通过，无 Critical / Important / Minor 问题。经用户确认，2026-09-04 已将 `codex/client-contract-application-foundation` 快进合并回独立 CCT 仓库的本地 `main`（`a251cd0`）；合并后重新执行 `./mvnw clean verify`，3 个测试全部通过，两个受保护数据库文件校验不变。本次隔离工作树和已合并功能分支已清理，提交完整保留在 main；其他工作树未处理。以下步骤保留为原始执行计划，以本记录为实施状态。

| Task | Commit | 结果 |
|---|---|---|
| 1 — Reactor / Wrapper | `3d91f0e` | 独立审查通过 |
| 2 — Application | `1c23edf` | 独立审查通过 |
| 3 — Runtime / Tests | `2e50474` | 独立审查通过 |
| 4 — Executable JAR | `52e6e62` | 独立审查通过 |
| 5 — README | `a251cd0` | 独立审查通过 |

编排者在 `a251cd0` 上重新执行 `./mvnw clean verify`：全部四个 Reactor 项目 SUCCESS，3 个测试通过、无失败/错误/跳过。Java 25.0.1、Maven 3.9.16；普通与 exec Web JAR 存在，Test JAR 不生成。依赖树和源码/配置检查未发现禁止依赖、业务接口或外部连接配置。exec JAR 实测三个健康端点 UP，Info/Prometheus HTTP 200，随后停止测试进程。

原始工作树两个受保护数据库文件的 SHA-256 均保持不变，原有修改未暂存。本次执行证据暂存于 `/tmp/code-mor-cct-foundation.lWPDzG`。

执行裁定及代价：

- `/.worktrees/` 从 Task 5 提前到隔离工作树准备阶段，在 main 提交 `2f833b3`；代价是一个可逆的独立 ignore 提交。
- 保留生产配置契约断言并配合真实 HTTP 测试；未来有意调整配置时需要同步更新断言。
- 将配置断言长方法拆成短辅助方法；仅测试组织与计划样例不同，预期值不变。
- 仅定向多模块 `-Dtest` 命令添加 `-Dsurefire.failIfNoSpecifiedTests=false`，避免无测试的上游模块提前终止；误写测试名可能静默，因此最终使用未放宽条件的全量测试并核对数量。

## Global Constraints

- 实施前必须阅读并遵守 AGENTS.md、agent-docs/DEVELOPMENT_CONVENTIONS.md、agent-docs/TECHNOLOGY_STACK.md、agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md、agent-docs/APPLICATION_ARCHITECTURE.md、agent-docs/JAVA_PROJECT_STRUCTURE.md、agent-docs/UNIT_TEST_GUIDE.md 和上述 Spec。
- 开始 Task 1 前使用 superpowers:using-git-worktrees 建立隔离工作树，分支名使用 codex/client-contract-application-foundation。所有任务提交都属于 client-contract-service 独立 Git 仓库。
- 原始工作树中的 docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md 和 docs/database/client-contract.pdma.json 是用户未提交的受保护输入。不得编辑、格式化、复制进功能分支、暂存或提交这两个文件。
- 编排者在创建工作树前记录原始工作树 Git 状态，并将两个受保护文件的 SHA-256 写入工作树之外的临时文件；最终验证和任何集成之后都使用同一个临时文件执行校验。
- 不运行 PDManer 生成器，不新增 SQL，不新增 CCT-Ixx 占位契约，不新增 Controller、Processor、Domain Service、Repository、Mapper、HTTP Client、MQ、Scheduler、Security、Nacos、数据库或缓存配置。
- 所有手写文件使用 apply_patch。mvnw 是唯一的机械生成资产：从 platform-support-service 当前工作树复制经验证的 Maven Wrapper 3.3.4 only-script 脚本，并用本计划给定的 SHA-256 校验；不得手工改写脚本。
- 每次暂存都列出明确路径，禁止 git add .；提交前确认 docs/database 下没有暂存内容。
- 测试必须位于 auto 包，使用真实 RANDOM_PORT MVC Server，不使用 Mockito、MockMvc、MockBean、Testcontainers 或固定端口。
- 每项任务结束时保持构建可通过并提交一个聚焦 Commit；RED 结果只记录，不提交失败状态。

## Exact File Map

实施完成后只允许出现以下基础文件变更：

- Modify: client-contract-service/.gitignore
- Create: client-contract-service/pom.xml
- Create: client-contract-service/mvnw
- Create: client-contract-service/.mvn/wrapper/maven-wrapper.properties
- Create: client-contract-service/README.md
- Create: client-contract-service/client-contract-api/pom.xml
- Create: client-contract-service/client-contract-api/src/main/java/com/paytech/client/contract/api/package-info.java
- Create: client-contract-service/client-contract-web/pom.xml
- Create: client-contract-service/client-contract-web/src/main/java/com/paytech/client/contract/ClientContractApplication.java
- Create: client-contract-service/client-contract-web/src/main/resources/application.yml
- Create: client-contract-service/client-contract-test/pom.xml
- Create: client-contract-service/client-contract-test/src/test/java/com/paytech/client/contract/auto/ClientContractApplicationAutoTest.java
- Create: client-contract-service/client-contract-test/src/test/java/com/paytech/client/contract/auto/ClientContractConfigurationAutoTest.java
- Create: client-contract-service/client-contract-test/src/test/java/com/paytech/client/contract/auto/ClientContractHealthAutoTest.java
- Create: client-contract-service/client-contract-test/src/test/resources/application-auto.yml

最终生产接口边界：

- 唯一生产 Java 类型是 com.paytech.client.contract.ClientContractApplication。
- API 模块没有 Java interface、DTO 或 enum，仅有 package-info.java。
- 唯一可访问的 HTTP 运维端点是同一应用监听器上的 health、info、prometheus；没有业务 Handler，也没有精确根路径 / Handler。

## Execution Preflight

- [ ] 在原始目录记录当前分支、HEAD 和全部已有修改：

~~~bash
git -C /Users/kenny/Desktop/Workspace/Code-MoR/client-contract-service \
  status --short --branch
git -C /Users/kenny/Desktop/Workspace/Code-MoR/client-contract-service \
  rev-parse HEAD
~~~
- [ ] 使用下列命令在 /tmp 下创建本次专用状态目录、保存原始状态与 SHA-256，并在执行日志中保留最后输出的实际目录路径。该路径由编排者持有，不交给会丢失上下文的独立任务：

~~~bash
CCT_FOUNDATION_STATE_DIR="$(mktemp -d /tmp/code-mor-cct-foundation.XXXXXX)"
git -C /Users/kenny/Desktop/Workspace/Code-MoR/client-contract-service status --short \
  > "$CCT_FOUNDATION_STATE_DIR/status.before"
shasum -a 256 \
  /Users/kenny/Desktop/Workspace/Code-MoR/client-contract-service/docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md \
  /Users/kenny/Desktop/Workspace/Code-MoR/client-contract-service/docs/database/client-contract.pdma.json \
  > "$CCT_FOUNDATION_STATE_DIR/protected.sha256"
printf '%s\n' "$CCT_FOUNDATION_STATE_DIR"
~~~

- [ ] 调用 superpowers:using-git-worktrees 创建隔离工作树；确认其基线是执行时 client-contract-service 的 main HEAD，且 git status --short 为空。
- [ ] 在隔离工作树中运行 java -version；预期 Java 25。若不是 Java 25，停止实施并按 LOCAL_DEVELOPMENT_ENVIRONMENT.md 修正当前 Shell，而不是修改项目版本。

---

### Task 1: 建立三模块 Reactor 与依赖边界

**Files:**

- Create: pom.xml
- Create: mvnw
- Create: .mvn/wrapper/maven-wrapper.properties
- Create: client-contract-api/pom.xml
- Create: client-contract-api/src/main/java/com/paytech/client/contract/api/package-info.java
- Create: client-contract-web/pom.xml
- Create: client-contract-test/pom.xml

**Interfaces:**

- Produces: com.paytech:client-contract-service:1.0.0-SNAPSHOT Reactor 以及同版本的 api、web、test 三个子模块。
- Produces: test -> web -> api 单向依赖；API 不发布业务 Java 契约。
- Produces: 后续任务可编译和测试的空 Web 应用模块；本任务不创建启动类或业务 Handler。

- [ ] 创建根 pom.xml，内容必须为：

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.paytech</groupId>
        <artifactId>paytech-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath/>
    </parent>

    <artifactId>client-contract-service</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>Client &amp; Contract Service</name>
    <description>Client &amp; Contract service reactor.</description>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.paytech</groupId>
                <artifactId>client-contract-api</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.paytech</groupId>
                <artifactId>client-contract-web</artifactId>
                <version>${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-enforcer-plugin</artifactId>
                <executions>
                    <execution>
                        <id>enforce-client-contract-foundation-scope</id>
                        <phase>validate</phase>
                        <goals>
                            <goal>enforce</goal>
                        </goals>
                        <configuration>
                            <rules>
                                <bannedDependencies>
                                    <searchTransitive>true</searchTransitive>
                                    <excludes>
                                        <exclude>org.mybatis:*</exclude>
                                        <exclude>org.mybatis.spring.boot:*</exclude>
                                        <exclude>org.springframework:spring-jdbc</exclude>
                                        <exclude>org.springframework.boot:spring-boot-starter-jdbc</exclude>
                                        <exclude>org.springframework.boot:spring-boot-starter-data-jdbc</exclude>
                                        <exclude>com.zaxxer:HikariCP</exclude>
                                        <exclude>com.mysql:*</exclude>
                                        <exclude>org.flywaydb:*</exclude>
                                        <exclude>org.liquibase:*</exclude>
                                        <exclude>org.testcontainers:*</exclude>
                                        <exclude>com.alibaba.nacos:*</exclude>
                                        <exclude>com.alibaba.cloud:*</exclude>
                                        <exclude>org.springframework.boot:spring-boot-starter-data-redis</exclude>
                                        <exclude>org.springframework.boot:spring-boot-starter-data-redis-reactive</exclude>
                                        <exclude>io.lettuce:*</exclude>
                                        <exclude>redis.clients:*</exclude>
                                        <exclude>org.redisson:*</exclude>
                                        <exclude>com.github.ben-manes.caffeine:*</exclude>
                                        <exclude>com.paytech:paytech-caffeine-starter</exclude>
                                        <exclude>org.springframework.boot:spring-boot-starter-amqp</exclude>
                                        <exclude>org.springframework.amqp:*</exclude>
                                        <exclude>com.rabbitmq:*</exclude>
                                        <exclude>com.xuxueli:*</exclude>
                                        <exclude>com.paytech:paytech-redis-starter</exclude>
                                        <exclude>com.paytech:paytech-compensation-event</exclude>
                                        <exclude>com.paytech:paytech-xxl-job-starter</exclude>
                                        <exclude>org.springframework.boot:spring-boot-starter-security</exclude>
                                        <exclude>org.springframework.boot:spring-boot-starter-oauth2-*</exclude>
                                        <exclude>org.springframework.security:*</exclude>
                                        <exclude>org.springframework.cloud:spring-cloud-starter-gateway*</exclude>
                                        <exclude>org.mockito:*</exclude>
                                    </excludes>
                                </bannedDependencies>
                            </rules>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>

    <modules>
        <module>client-contract-api</module>
        <module>client-contract-web</module>
        <module>client-contract-test</module>
    </modules>
</project>
~~~

- [ ] 机械复用已批准的 Maven Wrapper 脚本并验证来源：

~~~bash
cp /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service/mvnw ./mvnw
chmod 0755 ./mvnw
shasum -a 256 ./mvnw
~~~

预期 mvnw SHA-256 为 cae96cef89ebea3531221f4ae17c23cf8edf67d00eae8306d4186ae1bbed4d02。

- [ ] 创建 .mvn/wrapper/maven-wrapper.properties：

~~~properties
wrapperVersion=3.3.4
distributionType=only-script
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip
distributionSha256Sum=5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce
~~~

- [ ] 创建 client-contract-api/pom.xml：

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.paytech</groupId>
        <artifactId>client-contract-service</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>client-contract-api</artifactId>
    <name>Client &amp; Contract API</name>
    <description>Internal HTTP contracts published by Client &amp; Contract.</description>
</project>
~~~

- [ ] 创建 client-contract-api/src/main/java/com/paytech/client/contract/api/package-info.java：

~~~java
/**
 * Client & Contract 内部 HTTP 契约边界。
 *
 * <p>仅在具体纵向业务切片获批后添加业务接口。</p>
 */
package com.paytech.client.contract.api;
~~~

- [ ] 创建尚不包含可执行 JAR 插件的 client-contract-web/pom.xml：

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.paytech</groupId>
        <artifactId>client-contract-service</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>client-contract-web</artifactId>
    <name>Client &amp; Contract Web</name>
    <description>Deployable Client &amp; Contract Spring MVC application.</description>

    <dependencies>
        <dependency>
            <groupId>com.paytech</groupId>
            <artifactId>client-contract-api</artifactId>
        </dependency>
        <dependency>
            <groupId>com.paytech</groupId>
            <artifactId>paytech-mvc-web-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
    </dependencies>
</project>
~~~

- [ ] 创建 client-contract-test/pom.xml：

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.paytech</groupId>
        <artifactId>client-contract-service</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>client-contract-test</artifactId>
    <name>Client &amp; Contract Test</name>
    <description>Isolated Client &amp; Contract automated tests.</description>

    <properties>
        <maven.install.skip>true</maven.install.skip>
        <maven.deploy.skip>true</maven.deploy.skip>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.paytech</groupId>
            <artifactId>client-contract-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
            <exclusions>
                <exclusion>
                    <groupId>org.mockito</groupId>
                    <artifactId>mockito-core</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>org.mockito</groupId>
                    <artifactId>mockito-junit-jupiter</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <skipIfEmpty>true</skipIfEmpty>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

- [ ] 验证工具链、Reactor 模型和空源码编译：

~~~bash
./mvnw --version
./mvnw validate -DskipTests
./mvnw test
~~~

预期：Maven 3.9.16；根项目与 api、web、test 三个模块全部 SUCCESS；没有测试执行，也没有禁止依赖。

- [ ] 运行 git diff --check；仅暂存 Reactor 文件：

~~~bash
git add \
  pom.xml \
  mvnw \
  .mvn/wrapper/maven-wrapper.properties \
  client-contract-api \
  client-contract-web/pom.xml \
  client-contract-test/pom.xml
git diff --cached --name-only
git diff --cached --name-only -- docs/database
~~~

预期：最后一条命令没有输出。

- [ ] 提交：

~~~bash
git commit -m "build: initialize Client Contract reactor"
~~~

---

### Task 2: 用缺失启动类完成应用边界 RED-GREEN

**Files:**

- Create: client-contract-test/src/test/resources/application-auto.yml
- Test: client-contract-test/src/test/java/com/paytech/client/contract/auto/ClientContractApplicationAutoTest.java
- Create after RED: client-contract-web/src/main/java/com/paytech/client/contract/ClientContractApplication.java
- Create after RED: client-contract-web/src/main/resources/application.yml

**Interfaces:**

- Consumes: Task 1 的 client-contract-web 与 client-contract-test 模块。
- Produces: com.paytech.client.contract.ClientContractApplication 唯一生产类型。
- Produces: 可在随机端口启动、且没有精确 / 业务 Handler 的真实 MVC 应用。

- [ ] 创建 client-contract-test/src/test/resources/application-auto.yml：

~~~yaml
spring:
  main:
    banner-mode: "off"
~~~

- [ ] 先创建 client-contract-test/src/test/java/com/paytech/client/contract/auto/ClientContractApplicationAutoTest.java：

~~~java
package com.paytech.client.contract.auto;

import com.paytech.client.contract.ClientContractApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Client & Contract 应用基础边界测试。
 */
@ActiveProfiles("auto")
@SpringBootTest(
    classes = ClientContractApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.port=0"
)
class ClientContractApplicationAutoTest {

    @LocalServerPort
    private int serverPort;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    void test_applicationStartsWithoutPlaceholderBusinessRoute() {
        boolean hasPlaceholderRootRoute = requestMappingHandlerMapping.getHandlerMethods()
            .keySet()
            .stream()
            .flatMap(mapping -> mapping.getPatternValues().stream())
            .anyMatch("/"::equals);

        assertTrue(serverPort > 0, "应用应通过随机端口真实启动");
        assertFalse(hasPlaceholderRootRoute, "基础骨架不应添加占位业务接口");
    }
}
~~~

- [ ] 运行第一次 RED：

~~~bash
./mvnw -pl client-contract-test -am -Dtest=ClientContractApplicationAutoTest test
~~~

预期：testCompile 失败，明确指出 com.paytech.client.contract.ClientContractApplication 不存在。若失败原因是 Java、Maven、Nexus或其他环境问题，先修复环境并重跑，不能把错误环境记录成 RED。

- [ ] RED 确认后创建 client-contract-web/src/main/java/com/paytech/client/contract/ClientContractApplication.java：

~~~java
package com.paytech.client.contract;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Client & Contract 服务启动入口。
 */
@SpringBootApplication
public class ClientContractApplication {

    /**
     * 启动 Client & Contract 服务。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ClientContractApplication.class, args);
    }
}
~~~

- [ ] 创建最小的 client-contract-web/src/main/resources/application.yml；Actuator 的完整边界留给 Task 3 的第二次 RED-GREEN：

~~~yaml
server:
  port: 18101

spring:
  application:
    name: client-contract-service
~~~

- [ ] 重跑目标测试：

~~~bash
./mvnw -pl client-contract-test -am -Dtest=ClientContractApplicationAutoTest test
~~~

预期：ClientContractApplicationAutoTest 通过，真实 Server 使用随机正端口，MVC Route Table 不含精确 / Handler。

- [ ] 运行 ./mvnw -pl client-contract-test -am test 和 git diff --check。
- [ ] 仅暂存本任务列出的新文件并确认不包含 docs/database：

~~~bash
git add \
  client-contract-web/src/main/java/com/paytech/client/contract/ClientContractApplication.java \
  client-contract-web/src/main/resources/application.yml \
  client-contract-test/src/test/java/com/paytech/client/contract/auto/ClientContractApplicationAutoTest.java \
  client-contract-test/src/test/resources/application-auto.yml
git diff --cached --name-only
git diff --cached --name-only -- docs/database
~~~

预期：最后一条命令没有输出。

- [ ] 提交：

~~~bash
git commit -m "feat: add Client Contract application"
~~~

---

### Task 3: 用配置与健康测试冻结无外部依赖的运行边界

**Files:**

- Test: client-contract-test/src/test/java/com/paytech/client/contract/auto/ClientContractConfigurationAutoTest.java
- Test: client-contract-test/src/test/java/com/paytech/client/contract/auto/ClientContractHealthAutoTest.java
- Modify: client-contract-web/src/main/resources/application.yml

**Interfaces:**

- Consumes: Task 2 的 ClientContractApplication 与 client-contract-test 随机端口测试边界。
- Produces: 生产身份 client-contract-service 与默认端口 18101。
- Produces: 同一监听器上的 /actuator/health、/actuator/health/liveness 和 /actuator/health/readiness。
- Produces: 隐藏组件详情的健康响应；liveness/readiness 分组各只包含批准的 State contributor。

- [ ] 创建 ClientContractConfigurationAutoTest.java：

~~~java
package com.paytech.client.contract.auto;

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * Client & Contract 生产配置契约测试。
 */
class ClientContractConfigurationAutoTest {

    @Test
    void test_productionConfigurationUsesApprovedFoundationBoundary() throws IOException {
        StandardEnvironment environment = loadProductionConfiguration();

        assertApplicationIdentity(environment);
        assertManagementEndpointBoundary(environment);
        assertHealthBoundary(environment);
        assertExternalInfrastructureIsAbsent(environment);
    }

    private void assertApplicationIdentity(StandardEnvironment environment) {
        Assertions.assertEquals(
            "client-contract-service",
            environment.getRequiredProperty("spring.application.name")
        );
        Assertions.assertEquals(
            18101,
            environment.getRequiredProperty("server.port", Integer.class)
        );
        Assertions.assertFalse(
            environment.getRequiredProperty("spring.jmx.enabled", Boolean.class)
        );
    }

    private void assertManagementEndpointBoundary(StandardEnvironment environment) {
        Assertions.assertFalse(environment.getRequiredProperty(
            "management.endpoints.enabled-by-default",
            Boolean.class
        ));
        Assertions.assertFalse(environment.getRequiredProperty(
            "management.endpoints.web.discovery.enabled",
            Boolean.class
        ));
        Assertions.assertEquals(
            "health,info,prometheus",
            environment.getRequiredProperty("management.endpoints.web.exposure.include")
        );
        Assertions.assertEquals(
            "*",
            environment.getRequiredProperty("management.endpoints.jmx.exposure.exclude")
        );
        Assertions.assertEquals(
            "read-only",
            environment.getRequiredProperty("management.endpoint.info.access")
        );
        Assertions.assertEquals(
            "read-only",
            environment.getRequiredProperty("management.endpoint.prometheus.access")
        );
        Assertions.assertNull(environment.getProperty("management.server.port"));
    }

    private void assertHealthBoundary(StandardEnvironment environment) {
        Assertions.assertEquals(
            "read-only",
            environment.getRequiredProperty("management.endpoint.health.access")
        );
        Assertions.assertEquals(
            "never",
            environment.getRequiredProperty("management.endpoint.health.show-details")
        );
        Assertions.assertTrue(environment.getRequiredProperty(
            "management.endpoint.health.probes.enabled",
            Boolean.class
        ));
        Assertions.assertFalse(environment.getRequiredProperty(
            "management.endpoint.health.probes.add-additional-paths",
            Boolean.class
        ));
        Assertions.assertTrue(environment.getRequiredProperty(
            "management.endpoint.health.validate-group-membership",
            Boolean.class
        ));
        Assertions.assertEquals(
            "livenessState",
            environment.getRequiredProperty("management.endpoint.health.group.liveness.include")
        );
        Assertions.assertEquals(
            "readinessState",
            environment.getRequiredProperty("management.endpoint.health.group.readiness.include")
        );
        Assertions.assertTrue(environment.getRequiredProperty(
            "management.health.livenessstate.enabled",
            Boolean.class
        ));
        Assertions.assertTrue(environment.getRequiredProperty(
            "management.health.readinessstate.enabled",
            Boolean.class
        ));
    }

    private void assertExternalInfrastructureIsAbsent(StandardEnvironment environment) {
        Assertions.assertNull(environment.getProperty("spring.config.import"));
        Assertions.assertNull(environment.getProperty("spring.cloud.nacos.discovery.server-addr"));
        Assertions.assertNull(environment.getProperty("spring.cloud.nacos.config.server-addr"));
        Assertions.assertNull(environment.getProperty("spring.datasource.url"));
        Assertions.assertNull(environment.getProperty("spring.data.redis.host"));
        Assertions.assertNull(environment.getProperty("spring.rabbitmq.host"));
        Assertions.assertNull(environment.getProperty("xxl.job.admin.addresses"));
        Assertions.assertNull(environment.getProperty(
            "spring.security.oauth2.resourceserver.jwt.issuer-uri"
        ));
    }

    private StandardEnvironment loadProductionConfiguration() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
            .load("client-contract-production", new ClassPathResource("application.yml"))
            .forEach(environment.getPropertySources()::addLast);
        return environment;
    }
}
~~~

- [ ] 创建 ClientContractHealthAutoTest.java：

~~~java
package com.paytech.client.contract.auto;

import com.paytech.client.contract.ClientContractApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Client & Contract 健康端点测试。
 */
@ActiveProfiles("auto")
@AutoConfigureRestTestClient
@SpringBootTest(
    classes = ClientContractApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.port=0"
)
class ClientContractHealthAutoTest {

    @Autowired
    private RestTestClient restTestClient;

    @Test
    void test_healthEndpointsReportUpWithoutDetails() {
        assertUpWithoutDetails("/actuator/health");
        assertUpWithoutDetails("/actuator/health/liveness");
        assertUpWithoutDetails("/actuator/health/readiness");
    }

    private void assertUpWithoutDetails(String path) {
        restTestClient.get()
            .uri(path)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .value(body -> {
                Assertions.assertTrue(
                    body.contains("\"status\":\"UP\""),
                    "健康端点未返回 UP: " + path + ", body=" + body
                );
                Assertions.assertFalse(
                    body.contains("\"components\""),
                    "健康端点不应披露组件详情: " + path + ", body=" + body
                );
            });
    }
}
~~~

- [ ] 在仍为最小 application.yml 的状态下运行第二次 RED：

~~~bash
./mvnw -pl client-contract-test -am \
  -Dtest=ClientContractConfigurationAutoTest,ClientContractHealthAutoTest test
~~~

预期：配置测试先因缺少 spring.jmx.enabled 等批准属性而失败；健康测试的 liveness/readiness 也可能返回 404。至少必须观察到配置契约的预期失败，不能接受编译或环境错误。

- [ ] 用以下完整内容替换 client-contract-web/src/main/resources/application.yml：

~~~yaml
server:
  port: 18101

spring:
  application:
    name: client-contract-service
  jmx:
    enabled: false

management:
  endpoints:
    enabled-by-default: false
    web:
      discovery:
        enabled: false
      exposure:
        include: health,info,prometheus
    jmx:
      exposure:
        exclude: "*"
  endpoint:
    health:
      access: read-only
      show-details: never
      probes:
        enabled: true
        add-additional-paths: false
      validate-group-membership: true
      group:
        liveness:
          include: livenessState
        readiness:
          include: readinessState
    info:
      access: read-only
    prometheus:
      access: read-only
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true
~~~

- [ ] 重跑 Task 3 两个目标测试；预期全部通过。
- [ ] 运行 ./mvnw -pl client-contract-test -am test；预期三个 auto 测试全部通过，且启动不需要任何外部基础设施。
- [ ] 运行 git diff --check；仅暂存 application.yml 和两个新测试：

~~~bash
git add \
  client-contract-web/src/main/resources/application.yml \
  client-contract-test/src/test/java/com/paytech/client/contract/auto/ClientContractConfigurationAutoTest.java \
  client-contract-test/src/test/java/com/paytech/client/contract/auto/ClientContractHealthAutoTest.java
git diff --cached --name-only
git diff --cached --name-only -- docs/database
~~~

预期：最后一条命令没有输出。

- [ ] 提交：

~~~bash
git commit -m "test: lock Client Contract runtime boundary"
~~~

---

### Task 4: 用失败的制品检查驱动可执行 JAR 打包

**Files:**

- Modify: client-contract-web/pom.xml

**Interfaces:**

- Consumes: Task 1 的 client-contract-web 模块与 Task 2 的 ClientContractApplication。
- Produces: Web 模块的普通依赖 JAR 与带 exec classifier 的可执行 JAR。
- Preserves: Test 模块跳过 install/deploy 且不产生可部署 JAR。

- [ ] 在尚未配置 Spring Boot repackage 时运行：

~~~bash
./mvnw -pl client-contract-web -am clean package -DskipTests
test -f client-contract-web/target/client-contract-web-1.0.0-SNAPSHOT-exec.jar
~~~

预期：Maven package 成功，但 test -f 以退出码 1 失败，证明可执行制品契约尚未满足。

- [ ] 在 client-contract-web/pom.xml 的 dependencies 结束标签之后、project 结束标签之前加入：

~~~xml
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                        <configuration>
                            <classifier>exec</classifier>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
~~~

- [ ] 重跑：

~~~bash
./mvnw -pl client-contract-web -am clean package -DskipTests
test -f client-contract-web/target/client-contract-web-1.0.0-SNAPSHOT.jar
test -f client-contract-web/target/client-contract-web-1.0.0-SNAPSHOT-exec.jar
~~~

预期：普通 JAR 和 exec JAR 均存在。

- [ ] 运行 git diff --check；仅暂存 client-contract-web/pom.xml：

~~~bash
git add client-contract-web/pom.xml
git diff --cached --name-only
git diff --cached --name-only -- docs/database
~~~

预期：最后一条命令没有输出。

- [ ] 提交：

~~~bash
git commit -m "build: package Client Contract executable jar"
~~~

---

### Task 5: 补齐开发文档并执行全量完成门禁

**Files:**

- Modify: .gitignore
- Create: README.md
- Verify: all files from the Exact File Map

**Interfaces:**

- Consumes: Task 1 至 Task 4 的三模块 Reactor、运行配置、测试与制品契约。
- Produces: 覆盖本地构建、运行、健康检查、模块职责和明确非目标的 README。
- Produces: .gitignore 的工作树元数据规则，同时保留既有数据库设计与 PDManer backup 策略。

- [ ] 先运行以下文档契约检查；预期因 README.md 尚不存在以及 .worktrees 规则缺失而失败：

~~~bash
test -f README.md
rg -n '^/\.worktrees/$' .gitignore
~~~

- [ ] 在 .gitignore 的本地环境区加入一行，不改动现有 PDManer backup 规则：

~~~gitignore
/.worktrees/
~~~

- [ ] 创建 README.md：

~~~~markdown
# Client & Contract Service

client-contract-service 是 Client & Contract 核心服务的独立 Maven Reactor。当前里程碑只提供可运行的 Spring MVC 应用基础、共享 MVC 日志与异常边界，以及同监听器的 Actuator 健康、信息和 Prometheus 端点；尚未暴露业务 API。

现有数据库设计资产仍以 [CLIENT_CONTRACT_PHYSICAL_DECISIONS.md](docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md) 和 [client-contract.pdma.json](docs/database/client-contract.pdma.json) 为准。它们是受保护的设计输入；本基础工程不会生成、迁移或连接业务数据库，也不得在普通工程搭建中重新生成这两个文件。

## 模块

- client-contract-api：轻量内部 HTTP 契约边界；当前没有业务接口、DTO 或枚举。
- client-contract-web：可部署的 Spring MVC 应用及运维端点。
- client-contract-test：通过随机端口真实 Server 验证生产应用的 auto 测试；跳过 install 和 deploy。

依赖方向固定为 test -> web -> api，生产模块不得依赖 test。

## 前置条件

- Java 25。
- 仓库内 Maven Wrapper；它下载并校验 Maven 3.9.16。
- 开发者 Maven Settings/Nexus 能解析 com.paytech:paytech-parent:1.0.0-SNAPSHOT 和 paytech-mvc-web-starter。

本基础工程不需要 MySQL、Nacos、Redis、RabbitMQ、XXL-Job、Docker、其他 Java 服务、凭据或 Secret。

## 构建与验证

~~~bash
./mvnw clean verify
~~~

Web 模块同时生成普通依赖 JAR 和可执行的 client-contract-web-1.0.0-SNAPSHOT-exec.jar。Test 模块只保存测试证据，不生成可部署 JAR。

## 本地运行

~~~bash
java -jar client-contract-web/target/client-contract-web-1.0.0-SNAPSHOT-exec.jar
~~~

默认应用端口为 18101。启动后可检查：

~~~bash
curl --fail --silent http://127.0.0.1:18101/actuator/health
curl --fail --silent http://127.0.0.1:18101/actuator/health/liveness
curl --fail --silent http://127.0.0.1:18101/actuator/health/readiness
~~~

Actuator discovery 已关闭，健康详情不会对外披露，管理端点与应用共用监听器。当前没有 Gateway 公共路由。

## 当前非目标

本基础工程不实现 CCT-I01 至 CCT-I16、Party、Client、Local Partner、Operating Entity、Application、Assignment、Agreement、Consumer Legal、Commercial Term 或其他业务能力；也不包含 SQL、持久化、Nacos、消息、任务调度、Security、跨系统客户端、BFF、前端或部署自动化。

后续每个 CCT 业务能力都必须通过单独批准的纵向切片设计，在既有 api -> web -> test 边界内按需增加最小实现。
~~~~

- [ ] 重跑文档契约检查，并逐项确认 README 包含 Java 25、Maven 3.9.16、./mvnw clean verify、18101、三个健康 URL、无业务 API、无外部基础设施和数据库设计资产保护说明。

- [ ] 执行新鲜环境与全 Reactor 验证：

~~~bash
java -version
./mvnw --version
./mvnw clean verify
~~~

预期：Java 25、Apache Maven 3.9.16；三个模块及根 Reactor 全部 SUCCESS；三个 auto 测试通过。

- [ ] 验证制品边界：

~~~bash
test -f client-contract-web/target/client-contract-web-1.0.0-SNAPSHOT.jar
test -f client-contract-web/target/client-contract-web-1.0.0-SNAPSHOT-exec.jar
test ! -e client-contract-test/target/client-contract-test-1.0.0-SNAPSHOT.jar
~~~

- [ ] 输出 Reactor 的 test-scope 依赖树：

~~~bash
./mvnw -pl client-contract-test -am dependency:tree \
  -Dscope=test -DoutputFile=target/dependency-tree.txt
~~~

- [ ] 确认 client-contract-web 的依赖树包含 client-contract-api，client-contract-test 的依赖树包含 client-contract-web；反向依赖不存在。

~~~bash
rg -n 'com\.paytech:client-contract-api' \
  client-contract-web/target/dependency-tree.txt
rg -n 'com\.paytech:client-contract-web' \
  client-contract-test/target/dependency-tree.txt
~~~

- [ ] 搜索所有禁止家族：

~~~bash
rg -ni \
  'mybatis|spring-jdbc|mysql|flyway|liquibase|nacos|redis|redisson|caffeine|rabbit|amqp|xxl|compensation-event|spring-security|oauth2|gateway|testcontainers|mockito' \
  client-contract-*/target/dependency-tree.txt
~~~

预期：无输出且 rg 退出码为 1。根 POM Enforcer 是自动失败门禁，依赖树检查是可读证据。

- [ ] 验证生产源码边界：

~~~bash
find client-contract-api/src/main client-contract-web/src/main \
  -type f -name '*.java' -print | sort
rg -n \
  '@RestController|@Controller|@Service|@Repository|@Mapper|@HttpExchange|@Scheduled|@EnableDiscoveryClient|@Transactional|TODO by Codex' \
  client-contract-api/src/main client-contract-web/src/main
~~~

预期：find 只列出 package-info.java 与 ClientContractApplication.java；rg 无输出且退出码为 1。

- [ ] 验证生产配置没有外部连接、凭据或 Secret：

~~~bash
rg -ni \
  'jdbc:|password|secret|token|spring\.config\.import|datasource|redis|rabbitmq|nacos|xxl\.job' \
  client-contract-web/src/main/resources client-contract-test/src/test/resources
~~~

预期：无输出且 rg 退出码为 1。
- [ ] 运行 git diff --check。
- [ ] 仅暂存 .gitignore 和 README.md，并确认没有 docs/database 路径：

~~~bash
git add .gitignore README.md
git diff --cached --name-only
git diff --cached --name-only -- docs/database
~~~

预期：最后一条命令没有输出。

- [ ] 提交：

~~~bash
git commit -m "docs: document Client Contract foundation"
~~~

- [ ] 提交后再次运行 ./mvnw clean verify、git diff --check 和 git status --short；预期构建成功且功能工作树干净。
- [ ] 在原始 client-contract-service 工作树使用 Execution Preflight 中编排者保留的 CCT_FOUNDATION_STATE_DIR 执行：

~~~bash
shasum -a 256 -c "$CCT_FOUNDATION_STATE_DIR/protected.sha256"
git -C /Users/kenny/Desktop/Workspace/Code-MoR/client-contract-service \
  status --short
git -C /Users/kenny/Desktop/Workspace/Code-MoR/client-contract-service \
  diff --cached --name-only -- docs/database
~~~

预期：两个受保护文件均为 OK；原始 Git 状态中用户原有的两个 M 仍然存在；最后一条命令没有输出。
- [ ] 运行 git log -5 --oneline，预期按最新到最旧看到五个聚焦提交：
  - docs: document Client Contract foundation
  - build: package Client Contract executable jar
  - test: lock Client Contract runtime boundary
  - feat: add Client Contract application
  - build: initialize Client Contract reactor

## Final Review Gate

- [ ] 对照 Spec 第 2 至 11 节逐条核对，确认所有完成标准都有命令或测试证据。
- [ ] 扫描本计划要求创建的源码、POM、YAML 和 README，确认没有省略号实现、TODO、TBD、占位 DTO/接口、未解释的未来配置或伪业务端点。
- [ ] 确认 Maven 坐标、模块名、包名、服务名、端口、健康组和制品名在所有文件中完全一致。
- [ ] 确认没有配置 JaCoCo 的包级排除；本基础里程碑没有业务代码，当前也不额外启用覆盖率排除。未来启用覆盖率门禁时只允许精确排除 ClientContractApplication。
- [ ] 使用 superpowers:verification-before-completion 检查新鲜证据；实现完成后使用 superpowers:requesting-code-review 做一次独立审查。
- [ ] 审查通过后使用 superpowers:finishing-a-development-branch 处理功能分支集成选择；任何合并或集成后再次校验原始工作树的 protected.sha256。
