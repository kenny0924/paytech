# Commerce Application Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `commerce-service` 当前 `main` 工作区建立可独立构建、启动和验证的 Commerce Java 基础工程，同时保持所有 Commerce 业务能力、数据库实现和外部基础设施未实施。

**Architecture:** 本里程碑创建 `com.paytech:paytech-commerce-service:1.0.0-SNAPSHOT` 父 Reactor，以及非空的 `paytech-commerce-web` 和 `paytech-commerce-test` 两个模块。Web 是唯一可执行 Spring MVC 应用；Test 通过真实随机端口验证启动与运维端点，并通过 POM/Classpath/Source 自动门禁锁定当前窄范围。因为没有真实内部 HTTP 消费者，本轮不创建 `paytech-commerce-api`。

**Tech Stack:** Java 25、Maven Wrapper 3.3.4 / Maven 3.9.16、`com.paytech:paytech-parent:1.0.0-SNAPSHOT`、Spring Boot 4.0.8、Spring MVC、PayTech MVC Foundation Starter、Actuator、Micrometer Prometheus、JUnit 5、Spring `RestTestClient`。

**Spec:** [`docs/superpowers/specs/2026-09-05-commerce-modular-code-design.md`](../specs/2026-09-05-commerce-modular-code-design.md)

## Global Constraints

- 直接在 `commerce-service` 当前 `main` 工作区增量实施；本计划不创建分支或 worktree，不提交、不推送、不合并、不部署。
- 保留用户已有修改。开始和结束都检查 `git status --short`；不得编辑 `.gitignore`、`docs/database/COMMERCE_PHYSICAL_DECISIONS.md`、`docs/database/commerce.pdma.json` 或 PDManer 备份文件。
- 开始时记录上述三个受保护文件的 SHA-256，完成时必须逐字节一致；如果期间状态或哈希变化，停止集成并报告，不覆盖变化。
- Maven 坐标固定为父工程 `com.paytech:paytech-commerce-service:1.0.0-SNAPSHOT`，子模块固定为 `paytech-commerce-web` 和 `paytech-commerce-test`。
- Java 基础包在本实施计划中冻结为 `com.paytech.commerce`；唯一启动类为 `com.paytech.commerce.CommerceApplication`。
- 运行身份固定为 `spring.application.name=commerce-service`，本地地址为 `127.0.0.1`，端口为 `18102`，Context Path 为 `/commerce`。
- 本轮不创建 `paytech-commerce-api`、空包、占位 Controller、业务 Service、Domain Service、Processor、Repository、Mapper、DataObject、Integration、Listener、Job 或业务配置。
- 本轮不增加 SQL、Datasource、MyBatis、MySQL Driver、Migration、Nacos、Redis、RabbitMQ、Compensation Event、XXL-Job、Security、Gateway、外部 HTTP Client、Vault Client、Testcontainers 或 Mockito。
- Web 只依赖 `paytech-mvc-web-starter`、`spring-boot-starter-webmvc`、`spring-boot-starter-actuator` 和 `micrometer-registry-prometheus`；Test 只依赖 Web 与 test-scoped `spring-boot-starter-webmvc-test`，并排除 Mockito。
- 只有 Web 使用 Spring Boot `repackage`，classifier 固定为 `exec`；Test 跳过 install/deploy 且不产生可部署 JAR。
- 默认 Maven 生命周期只运行 `auto`；所有测试走真实生产启动链，不使用方法级 Stub、固定测试端口、外部基础设施或 `Thread.sleep`。
- Actuator 与应用共用监听器，默认禁用所有端点和 discovery，只读暴露 `health,info,prometheus`；健康详情为 `never`，liveness/readiness 分别只含 `livenessState`/`readinessState`。
- 本计划完成后只更新 Commerce README 与 `agent-docs/CURRENT_IMPLEMENTATION.md` 的当前事实；不把基础工程描述成业务 API、数据库或部署完成。

## File Map

| File | Responsibility |
|---|---|
| `commerce-service/pom.xml` | 聚合两个模块、管理 Web 版本、配置定向测试行为，并在最终状态禁止未授权依赖 |
| `commerce-service/mvnw` | 仓库固定的 Maven Wrapper 启动脚本 |
| `commerce-service/.mvn/wrapper/maven-wrapper.properties` | 固定 Wrapper 3.3.4、Maven 3.9.16 和分发包校验和 |
| `commerce-service/paytech-commerce-web/pom.xml` | 声明唯一运行模块的最小 MVC/Actuator/Prometheus 依赖与 `exec` 制品 |
| `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/CommerceApplication.java` | 唯一 Spring Boot 启动入口 |
| `commerce-service/paytech-commerce-web/src/main/resources/application.yml` | 冻结服务身份、监听边界和最小 Actuator 配置 |
| `commerce-service/paytech-commerce-test/pom.xml` | 隔离 auto 测试、禁止发布测试制品、排除 Mockito |
| `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/CommerceApplicationAutoTest.java` | 验证真实 Server 启动且没有占位根路由 |
| `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/CommerceConfigurationAutoTest.java` | 直接验证生产 YAML 的身份、Actuator 与无外部基础设施边界 |
| `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/CommerceHealthAutoTest.java` | 通过真实 HTTP 验证健康、Info、Prometheus 和端点集合 |
| `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/CommerceModuleBoundaryAutoTest.java` | 验证 Reactor、依赖、制品、禁令、Classpath、源码清单和无 API 模块 |
| `commerce-service/paytech-commerce-test/src/test/resources/application-auto.yml` | 仅关闭测试 Banner，不覆盖生产边界 |
| `commerce-service/README.md` | 记录当前能力、构建运行方法、设计资产保护和非目标 |
| `agent-docs/CURRENT_IMPLEMENTATION.md` | 在完整验证成功后更新 Commerce 当前实现台账 |

---

### Task 1: Establish the protected two-module Maven reactor

**Files:**
- Create: `commerce-service/mvnw`
- Create: `commerce-service/.mvn/wrapper/maven-wrapper.properties`
- Create: `commerce-service/pom.xml`
- Create: `commerce-service/paytech-commerce-web/pom.xml`
- Create: `commerce-service/paytech-commerce-test/pom.xml`
- Verify unchanged: `commerce-service/.gitignore`
- Verify unchanged: `commerce-service/docs/database/COMMERCE_PHYSICAL_DECISIONS.md`
- Verify unchanged: `commerce-service/docs/database/commerce.pdma.json`

**Interfaces:**
- Consumes: `com.paytech:paytech-parent:1.0.0-SNAPSHOT` and the repository/Nexus configuration already used by sibling services.
- Produces: a reactor containing exactly `paytech-commerce-web` then `paytech-commerce-test`; targeted tests can be selected without failing production modules that contain no tests.

- [ ] **Step 1: Capture branch, dirty state and protected-file hashes**

Run from the workspace root:

```bash
git -C commerce-service branch --show-current
git -C commerce-service status --short
commerce_foundation_evidence_file=/private/tmp/code-mor-commerce-foundation-protected.before.sha256
test ! -e "$commerce_foundation_evidence_file"
shasum -a 256 \
  commerce-service/.gitignore \
  commerce-service/docs/database/COMMERCE_PHYSICAL_DECISIONS.md \
  commerce-service/docs/database/commerce.pdma.json \
  | tee "$commerce_foundation_evidence_file"
```

Expected: branch is `main`; the existing `M docs/database/COMMERCE_PHYSICAL_DECISIONS.md` remains visible. The explicit temporary evidence file did not already exist and now contains all three hashes. If it already exists, stop rather than overwriting ambiguous evidence.

- [ ] **Step 2: Install the approved Wrapper mechanically and verify exact bytes**

The Wrapper is a mechanical copy of the current approved sibling-service wrapper; all semantic files in later steps use `apply_patch`.

```bash
install -m 755 client-contract-service/mvnw commerce-service/mvnw
mkdir -p commerce-service/.mvn/wrapper
install -m 644 \
  client-contract-service/.mvn/wrapper/maven-wrapper.properties \
  commerce-service/.mvn/wrapper/maven-wrapper.properties
shasum -a 256 commerce-service/mvnw
sed -n '1,20p' commerce-service/.mvn/wrapper/maven-wrapper.properties
```

Expected script SHA-256:

```text
cae96cef89ebea3531221f4ae17c23cf8edf67d00eae8306d4186ae1bbed4d02
```

Expected properties:

```properties
wrapperVersion=3.3.4
distributionType=only-script
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip
distributionSha256Sum=5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce
```

- [ ] **Step 3: Create the initial reactor POM**

Create `commerce-service/pom.xml` with this exact initial content. The dependency-enforcement execution is intentionally added under test in Task 4.

```xml
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

    <artifactId>paytech-commerce-service</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>Commerce Service</name>
    <description>Commerce service reactor.</description>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.paytech</groupId>
                <artifactId>paytech-commerce-web</artifactId>
                <version>${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <failIfNoSpecifiedTests>false</failIfNoSpecifiedTests>
                </configuration>
            </plugin>
        </plugins>
    </build>

    <modules>
        <module>paytech-commerce-web</module>
        <module>paytech-commerce-test</module>
    </modules>
</project>
```

- [ ] **Step 4: Create the Web POM**

Create `commerce-service/paytech-commerce-web/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.paytech</groupId>
        <artifactId>paytech-commerce-service</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>paytech-commerce-web</artifactId>
    <name>Commerce Web</name>
    <description>Deployable Commerce Spring MVC application.</description>

    <dependencies>
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
</project>
```

- [ ] **Step 5: Create the isolated Test POM**

Create `commerce-service/paytech-commerce-test/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.paytech</groupId>
        <artifactId>paytech-commerce-service</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>paytech-commerce-test</artifactId>
    <name>Commerce Test</name>
    <description>Isolated Commerce automated tests.</description>

    <properties>
        <maven.install.skip>true</maven.install.skip>
        <maven.deploy.skip>true</maven.deploy.skip>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.paytech</groupId>
            <artifactId>paytech-commerce-web</artifactId>
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
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <failIfNoSpecifiedTests>true</failIfNoSpecifiedTests>
                    <systemPropertyVariables>
                        <commerce.reactor.root>${project.basedir}/..</commerce.reactor.root>
                    </systemPropertyVariables>
                </configuration>
            </plugin>
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
```

- [ ] **Step 6: Verify the reactor can resolve under Java 25**

```bash
cd commerce-service
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"
java -version
./mvnw -version
./mvnw validate -DskipTests
```

Expected: Java is `[25,26)`, Maven is `3.9.16`, and the reactor build order is parent → Web → Test. No `paytech-commerce-api` module appears.

---

### Task 2: Add the only production Java class through a failing startup test

**Files:**
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/CommerceApplicationAutoTest.java`
- Create: `commerce-service/paytech-commerce-test/src/test/resources/application-auto.yml`
- Create: `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/CommerceApplication.java`

**Interfaces:**
- Consumes: the Web and Test modules from Task 1.
- Produces: `public class CommerceApplication` with `public static void main(String[] args)` and a real random-port Spring MVC startup path.

- [ ] **Step 1: Write the failing application-boundary test**

Create `CommerceApplicationAutoTest.java`:

```java
package com.paytech.commerce.auto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.paytech.commerce.CommerceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Commerce 应用基础边界测试。
 */
@ActiveProfiles("auto")
@SpringBootTest(
    classes = CommerceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.port=0"
)
class CommerceApplicationAutoTest {

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
```

Create `application-auto.yml`:

```yaml
spring:
  main:
    banner-mode: "off"
```

- [ ] **Step 2: Run the test and verify the intended RED**

```bash
./mvnw -pl paytech-commerce-test -am -Dtest=CommerceApplicationAutoTest test
```

Expected: test compilation fails because `com.paytech.commerce.CommerceApplication` does not exist. Dependency resolution or Java/Maven-version failures are environment failures and do not count as the intended RED.

- [ ] **Step 3: Add the minimal application entry point**

Create `CommerceApplication.java`:

```java
package com.paytech.commerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Commerce 服务启动入口。
 */
@SpringBootApplication
public class CommerceApplication {

    /**
     * 启动 Commerce 服务。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(CommerceApplication.class, args);
    }
}
```

- [ ] **Step 4: Run the same test and verify GREEN**

```bash
./mvnw -pl paytech-commerce-test -am -Dtest=CommerceApplicationAutoTest test
```

Expected: exactly the selected application test passes, the Server uses a random port, and no root business route exists.

---

### Task 3: Freeze runtime identity and management behavior under tests

**Files:**
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/CommerceConfigurationAutoTest.java`
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/CommerceHealthAutoTest.java`
- Create: `commerce-service/paytech-commerce-web/src/main/resources/application.yml`

**Interfaces:**
- Consumes: `CommerceApplication` from Task 2 and the Web module's Actuator/Prometheus dependencies.
- Produces: fixed local identity `commerce-service`, `127.0.0.1:18102`, `/commerce`, and private management endpoints `/commerce/actuator/{health,health/liveness,health/readiness,info,prometheus}`.

- [ ] **Step 1: Write the failing production-configuration contract test**

Create `CommerceConfigurationAutoTest.java`:

```java
package com.paytech.commerce.auto;

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * Commerce 生产配置契约测试。
 */
class CommerceConfigurationAutoTest {

    @Test
    void test_productionConfigurationUsesApprovedFoundationBoundary() throws IOException {
        String originalServerPort = System.getProperty("server.port");
        System.setProperty("server.port", "19999");

        try {
            StandardEnvironment environment = loadProductionConfiguration();

            assertApplicationIdentity(environment);
            assertManagementEndpointBoundary(environment);
            assertHealthBoundary(environment);
            assertExternalInfrastructureIsAbsent(environment);
        } finally {
            if (originalServerPort == null) {
                System.clearProperty("server.port");
            } else {
                System.setProperty("server.port", originalServerPort);
            }
        }
    }

    private void assertApplicationIdentity(StandardEnvironment environment) {
        Assertions.assertEquals("127.0.0.1", environment.getRequiredProperty("server.address"));
        Assertions.assertEquals(18102, environment.getRequiredProperty("server.port", Integer.class));
        Assertions.assertEquals("/commerce", environment.getRequiredProperty("server.servlet.context-path"));
        Assertions.assertEquals("commerce-service", environment.getRequiredProperty("spring.application.name"));
        Assertions.assertFalse(environment.getRequiredProperty("spring.jmx.enabled", Boolean.class));
    }

    private void assertManagementEndpointBoundary(StandardEnvironment environment) {
        Assertions.assertFalse(environment.getRequiredProperty(
            "management.endpoints.enabled-by-default", Boolean.class));
        Assertions.assertFalse(environment.getRequiredProperty(
            "management.endpoints.web.discovery.enabled", Boolean.class));
        Assertions.assertEquals("health,info,prometheus", environment.getRequiredProperty(
            "management.endpoints.web.exposure.include"));
        Assertions.assertEquals("*", environment.getRequiredProperty(
            "management.endpoints.jmx.exposure.exclude"));
        Assertions.assertEquals("read-only", environment.getRequiredProperty(
            "management.endpoint.health.access"));
        Assertions.assertEquals("read-only", environment.getRequiredProperty(
            "management.endpoint.info.access"));
        Assertions.assertEquals("read-only", environment.getRequiredProperty(
            "management.endpoint.prometheus.access"));
        Assertions.assertNull(environment.getProperty("management.server.port"));
    }

    private void assertHealthBoundary(StandardEnvironment environment) {
        Assertions.assertEquals("never", environment.getRequiredProperty(
            "management.endpoint.health.show-details"));
        Assertions.assertTrue(environment.getRequiredProperty(
            "management.endpoint.health.probes.enabled", Boolean.class));
        Assertions.assertFalse(environment.getRequiredProperty(
            "management.endpoint.health.probes.add-additional-paths", Boolean.class));
        Assertions.assertTrue(environment.getRequiredProperty(
            "management.endpoint.health.validate-group-membership", Boolean.class));
        Assertions.assertEquals("livenessState", environment.getRequiredProperty(
            "management.endpoint.health.group.liveness.include"));
        Assertions.assertEquals("readinessState", environment.getRequiredProperty(
            "management.endpoint.health.group.readiness.include"));
        Assertions.assertTrue(environment.getRequiredProperty(
            "management.health.livenessstate.enabled", Boolean.class));
        Assertions.assertTrue(environment.getRequiredProperty(
            "management.health.readinessstate.enabled", Boolean.class));
    }

    private void assertExternalInfrastructureIsAbsent(StandardEnvironment environment) {
        Assertions.assertNull(environment.getProperty("spring.config.import"));
        Assertions.assertNull(environment.getProperty("spring.cloud.nacos.discovery.server-addr"));
        Assertions.assertNull(environment.getProperty("spring.cloud.nacos.config.server-addr"));
        Assertions.assertNull(environment.getProperty("spring.datasource.url"));
        Assertions.assertNull(environment.getProperty("spring.data.redis.host"));
        Assertions.assertNull(environment.getProperty("spring.rabbitmq.host"));
        Assertions.assertNull(environment.getProperty("xxl.job.admin.addresses"));
        Assertions.assertNull(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"));
        Assertions.assertNull(environment.getProperty("web.logging.metadata-only-path-prefixes"));
    }

    private StandardEnvironment loadProductionConfiguration() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        new YamlPropertySourceLoader()
            .load("commerce-production", new ClassPathResource("application.yml"))
            .forEach(environment.getPropertySources()::addLast);
        return environment;
    }
}
```

- [ ] **Step 2: Write the failing real-HTTP management test**

Create `CommerceHealthAutoTest.java`:

```java
package com.paytech.commerce.auto;

import java.util.Set;
import java.util.stream.Collectors;

import com.paytech.commerce.CommerceApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Commerce 私有运维端点测试。
 */
@ActiveProfiles("auto")
@AutoConfigureRestTestClient
@SpringBootTest(
    classes = CommerceApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.port=0"
)
class CommerceHealthAutoTest {

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private WebEndpointsSupplier endpoints;

    @Test
    void test_healthEndpointsReportUpWithoutDetails() {
        assertUpWithoutDetails("/actuator/health");
        assertUpWithoutDetails("/actuator/health/liveness");
        assertUpWithoutDetails("/actuator/health/readiness");
    }

    @Test
    void test_onlyApprovedManagementEndpointsAreRegistered() {
        Set<String> endpointIds = endpoints.getEndpoints().stream()
            .map(endpoint -> endpoint.getEndpointId().toString())
            .collect(Collectors.toUnmodifiableSet());

        Assertions.assertEquals(Set.of("health", "info", "prometheus"), endpointIds);
    }

    @Test
    void test_infoDoesNotExposeEnvironment() {
        restTestClient.get()
            .uri("/actuator/info")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .value(body -> Assertions.assertEquals("{}", body));
    }

    @Test
    void test_prometheusIsAvailable() {
        restTestClient.get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .value(body -> Assertions.assertTrue(body.contains("jvm_memory_used_bytes")));
    }

    private void assertUpWithoutDetails(String path) {
        restTestClient.get()
            .uri(path)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .value(body -> {
                Assertions.assertTrue(body.contains("\"status\":\"UP\""),
                    "健康端点未返回 UP: " + path + ", body=" + body);
                Assertions.assertFalse(body.contains("\"components\""),
                    "健康端点不应披露组件: " + path + ", body=" + body);
                Assertions.assertFalse(body.contains("\"details\""),
                    "健康端点不应披露详情: " + path + ", body=" + body);
            });
    }
}
```

- [ ] **Step 3: Run both tests and verify the intended RED**

```bash
./mvnw -pl paytech-commerce-test -am \
  -Dtest=CommerceConfigurationAutoTest,CommerceHealthAutoTest test
```

Expected: the configuration test cannot load the absent production `application.yml`, and the management URLs do not satisfy the `/commerce` contract. A passing result means an unplanned resource has entered the classpath and must be investigated before proceeding.

- [ ] **Step 4: Add the minimal production configuration**

Create `commerce-service/paytech-commerce-web/src/main/resources/application.yml`:

```yaml
server:
  address: 127.0.0.1
  port: 18102
  servlet:
    context-path: /commerce

spring:
  application:
    name: commerce-service
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
```

- [ ] **Step 5: Run the same tests and verify GREEN**

```bash
./mvnw -pl paytech-commerce-test -am \
  -Dtest=CommerceConfigurationAutoTest,CommerceHealthAutoTest test
```

Expected: five selected test methods pass—one configuration contract and four management behaviors—and no external infrastructure is contacted.

---

### Task 4: Turn the foundation scope into an executable module boundary

**Files:**
- Create: `commerce-service/paytech-commerce-test/src/test/java/com/paytech/commerce/auto/CommerceModuleBoundaryAutoTest.java`
- Modify: `commerce-service/pom.xml`

**Interfaces:**
- Consumes: the complete foundation file set from Tasks 1–3.
- Produces: an automated contract for exact modules, direct dependencies, executable/test artifact boundaries, required dependency bans, absent infrastructure classes, and the one-file production source scope.

- [ ] **Step 1: Write the failing module-boundary test**

Create `CommerceModuleBoundaryAutoTest.java`:

```java
package com.paytech.commerce.auto;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 在默认 Maven 测试中保护 Commerce 基础模块和依赖边界。
 */
class CommerceModuleBoundaryAutoTest {

    private static final Set<String> WEB_DEPENDENCIES = Set.of(
        "com.paytech:paytech-mvc-web-starter",
        "org.springframework.boot:spring-boot-starter-webmvc",
        "org.springframework.boot:spring-boot-starter-actuator",
        "io.micrometer:micrometer-registry-prometheus"
    );

    private static final Set<String> TEST_DEPENDENCIES = Set.of(
        "com.paytech:paytech-commerce-web",
        "org.springframework.boot:spring-boot-starter-webmvc-test"
    );

    private static final Set<String> REQUIRED_BANS = Set.of(
        "org.mybatis:*",
        "org.mybatis.spring.boot:*",
        "org.springframework:spring-jdbc",
        "org.springframework.boot:spring-boot-starter-jdbc",
        "org.springframework.boot:spring-boot-starter-data-jdbc",
        "com.zaxxer:HikariCP",
        "com.mysql:*",
        "org.flywaydb:*",
        "org.liquibase:*",
        "org.testcontainers:*",
        "com.alibaba.cloud:*",
        "com.alibaba.nacos:*",
        "org.springframework.boot:spring-boot-starter-data-redis*",
        "org.springframework.data:spring-data-redis",
        "io.lettuce:*",
        "redis.clients:*",
        "org.redisson:*",
        "com.github.ben-manes.caffeine:*",
        "com.paytech:paytech-caffeine-starter",
        "com.paytech:paytech-redis-starter",
        "org.springframework.boot:spring-boot-starter-amqp",
        "org.springframework.amqp:*",
        "com.rabbitmq:*",
        "com.xuxueli:*",
        "com.paytech:paytech-compensation-event",
        "com.paytech:paytech-xxl-job-starter",
        "com.paytech:paytech-http-client-support",
        "com.paytech:paytech-vault-client",
        "com.paytech:paytech-mybatis-support",
        "com.paytech:paytech-snowflake-id",
        "org.springframework.boot:spring-boot-starter-security",
        "org.springframework.boot:spring-boot-starter-oauth2-*",
        "org.springframework.security:*",
        "org.springframework.cloud:spring-cloud-starter-gateway*",
        "org.springframework.cloud:spring-cloud-starter-openfeign",
        "org.apache.dubbo:*",
        "org.springframework:spring-webflux",
        "org.mockito:*"
    );

    @Test
    void test_reactorContainsOnlyApprovedFoundationModules() throws Exception {
        Assertions.assertEquals(
            List.of("paytech-commerce-web", "paytech-commerce-test"),
            values(readPom(""), "/project/modules/module")
        );
        Assertions.assertFalse(Files.exists(reactorRoot().resolve("paytech-commerce-api")),
            "没有真实内部 HTTP 契约时不得创建空 API 模块");
    }

    @Test
    void test_modulesContainOnlyApprovedDirectDependencies() throws Exception {
        Assertions.assertEquals(WEB_DEPENDENCIES,
            directDependencies(readPom("paytech-commerce-web")));
        Assertions.assertEquals(TEST_DEPENDENCIES,
            directDependencies(readPom("paytech-commerce-test")));
    }

    @Test
    void test_onlyWebIsExecutableAndTestIsNotPublished() throws Exception {
        Assertions.assertEquals(Set.of("exec"), new HashSet<>(values(
            readPom("paytech-commerce-web"),
            "/project/build/plugins/plugin[artifactId='spring-boot-maven-plugin']"
                + "/executions/execution/configuration/classifier"
        )));
        Assertions.assertFalse(values(readPom("paytech-commerce-test"),
            "/project/build/plugins/plugin/artifactId").contains("spring-boot-maven-plugin"));
        Assertions.assertEquals(List.of("true"), values(readPom("paytech-commerce-test"),
            "/project/properties/maven.install.skip"));
        Assertions.assertEquals(List.of("true"), values(readPom("paytech-commerce-test"),
            "/project/properties/maven.deploy.skip"));
    }

    @Test
    void test_foundationDependencyBansRemainEnforced() throws Exception {
        Set<String> bans = new HashSet<>(values(readPom(""),
            "/project/build/plugins/plugin[artifactId='maven-enforcer-plugin']"
                + "/executions/execution/configuration/rules/bannedDependencies/excludes/exclude"));
        Assertions.assertTrue(bans.containsAll(REQUIRED_BANS),
            "基础工程未完整禁止业务与外部基础设施依赖");
    }

    @Test
    void test_runtimeClasspathContainsNoUnapprovedInfrastructure() {
        for (String resource : Set.of(
            "org/apache/ibatis/session/SqlSession.class",
            "com/zaxxer/hikari/HikariDataSource.class",
            "com/alibaba/nacos/api/NacosFactory.class",
            "org/springframework/amqp/rabbit/core/RabbitTemplate.class",
            "org/redisson/Redisson.class",
            "org/springframework/security/core/Authentication.class",
            "org/mockito/Mockito.class",
            "org/testcontainers/containers/GenericContainer.class"
        )) {
            Assertions.assertNull(getClass().getClassLoader().getResource(resource), resource);
        }
    }

    @Test
    void test_onlyApprovedProductionSourceExists() throws Exception {
        Path sourceRoot = reactorRoot().resolve(
            "paytech-commerce-web/src/main/java");
        Set<String> sourceFiles;
        try (var files = Files.walk(sourceRoot)) {
            sourceFiles = files.filter(Files::isRegularFile)
                .map(sourceRoot::relativize)
                .map(Path::toString)
                .collect(Collectors.toUnmodifiableSet());
        }
        Assertions.assertEquals(
            Set.of("com/paytech/commerce/CommerceApplication.java"),
            sourceFiles
        );
    }

    private Path reactorRoot() {
        return Path.of(System.getProperty("commerce.reactor.root"))
            .toAbsolutePath().normalize();
    }

    private Document readPom(String module) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder()
            .parse(reactorRoot().resolve(module).resolve("pom.xml").toFile());
    }

    private List<String> values(Document document, String expression) throws Exception {
        NodeList nodes = (NodeList) XPathFactory.newInstance().newXPath().evaluate(
            expression, document, XPathConstants.NODESET);
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            values.add(nodes.item(index).getTextContent().trim());
        }
        return List.copyOf(values);
    }

    private Set<String> directDependencies(Document document) {
        Element project = document.getDocumentElement();
        Element dependencies = directChild(project, "dependencies");
        if (dependencies == null) {
            return Set.of();
        }
        Set<String> coordinates = new HashSet<>();
        NodeList children = dependencies.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node instanceof Element element && "dependency".equals(element.getTagName())) {
                coordinates.add(directChildText(element, "groupId") + ":"
                    + directChildText(element, "artifactId"));
            }
        }
        return Set.copyOf(coordinates);
    }

    private Element directChild(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node instanceof Element element && tagName.equals(element.getTagName())) {
                return element;
            }
        }
        return null;
    }

    private String directChildText(Element parent, String tagName) {
        Element child = directChild(parent, tagName);
        if (child == null) {
            throw new IllegalArgumentException("POM 元素不存在: " + tagName);
        }
        return child.getTextContent().trim();
    }
}
```

- [ ] **Step 2: Run the boundary test and verify the intended RED**

```bash
./mvnw -pl paytech-commerce-test -am \
  -Dtest=CommerceModuleBoundaryAutoTest test
```

Expected: `test_foundationDependencyBansRemainEnforced` fails because the initial root POM does not yet contain the Enforcer execution. The other boundary methods must pass; any other failure indicates drift in Tasks 1–3 and must be corrected first.

- [ ] **Step 3: Add the exact foundation dependency bans**

In `commerce-service/pom.xml`, keep the existing Surefire plugin and append this plugin inside `/project/build/plugins`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-enforcer-plugin</artifactId>
    <executions>
        <execution>
            <id>enforce-commerce-foundation-scope</id>
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
                            <exclude>com.alibaba.cloud:*</exclude>
                            <exclude>com.alibaba.nacos:*</exclude>
                            <exclude>org.springframework.boot:spring-boot-starter-data-redis*</exclude>
                            <exclude>org.springframework.data:spring-data-redis</exclude>
                            <exclude>io.lettuce:*</exclude>
                            <exclude>redis.clients:*</exclude>
                            <exclude>org.redisson:*</exclude>
                            <exclude>com.github.ben-manes.caffeine:*</exclude>
                            <exclude>com.paytech:paytech-caffeine-starter</exclude>
                            <exclude>com.paytech:paytech-redis-starter</exclude>
                            <exclude>org.springframework.boot:spring-boot-starter-amqp</exclude>
                            <exclude>org.springframework.amqp:*</exclude>
                            <exclude>com.rabbitmq:*</exclude>
                            <exclude>com.xuxueli:*</exclude>
                            <exclude>com.paytech:paytech-compensation-event</exclude>
                            <exclude>com.paytech:paytech-xxl-job-starter</exclude>
                            <exclude>com.paytech:paytech-http-client-support</exclude>
                            <exclude>com.paytech:paytech-vault-client</exclude>
                            <exclude>com.paytech:paytech-mybatis-support</exclude>
                            <exclude>com.paytech:paytech-snowflake-id</exclude>
                            <exclude>org.springframework.boot:spring-boot-starter-security</exclude>
                            <exclude>org.springframework.boot:spring-boot-starter-oauth2-*</exclude>
                            <exclude>org.springframework.security:*</exclude>
                            <exclude>org.springframework.cloud:spring-cloud-starter-gateway*</exclude>
                            <exclude>org.springframework.cloud:spring-cloud-starter-openfeign</exclude>
                            <exclude>org.apache.dubbo:*</exclude>
                            <exclude>org.springframework:spring-webflux</exclude>
                            <exclude>org.mockito:*</exclude>
                        </excludes>
                    </bannedDependencies>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 4: Run the same boundary test and verify GREEN**

```bash
./mvnw -pl paytech-commerce-test -am \
  -Dtest=CommerceModuleBoundaryAutoTest test
```

Expected: all six module-boundary methods pass. The Enforcer also runs during `validate` and finds no banned transitive dependency.

---

### Task 5: Document, verify, and record the implemented foundation accurately

**Files:**
- Create: `commerce-service/README.md`
- Modify after successful Commerce verification: `agent-docs/CURRENT_IMPLEMENTATION.md`
- Verify unchanged: `commerce-service/.gitignore`
- Verify unchanged: `commerce-service/docs/database/COMMERCE_PHYSICAL_DECISIONS.md`
- Verify unchanged: `commerce-service/docs/database/commerce.pdma.json`

**Interfaces:**
- Consumes: the green reactor from Tasks 1–4 and the protected-file evidence path from Task 1.
- Produces: a developer runbook, a truthful central status row, a verified executable JAR, and fresh build/model/site evidence.

- [ ] **Step 1: Create the service README**

Create `commerce-service/README.md`:

```markdown
# Commerce Service

commerce-service 是 Commerce 核心服务的独立 Maven Reactor。当前里程碑只提供可运行的 Spring MVC 应用基础、共享 MVC 日志与异常边界，以及同监听器的 Actuator 健康、信息和 Prometheus 端点；尚未暴露任何 Commerce 业务 API。

现有数据库设计资产仍以 [COMMERCE_PHYSICAL_DECISIONS.md](docs/database/COMMERCE_PHYSICAL_DECISIONS.md) 和 [commerce.pdma.json](docs/database/commerce.pdma.json) 为准。它们是受保护的设计输入；本基础工程不会生成、迁移或连接业务数据库，也不得在普通工程搭建中重新生成这两个文件。

## 模块

- `paytech-commerce-web`：唯一可部署的 Spring MVC 应用及运维端点。
- `paytech-commerce-test`：通过随机端口真实 Server 验证生产应用的 auto 测试；跳过 install 和 deploy。

依赖方向固定为 `test -> web`，生产模块不得依赖 Test。当前没有真实内部 HTTP 契约，因此没有创建 `paytech-commerce-api`；首个真实内部或 BFF 私有契约进入获批纵向切片时才创建非空 API 模块。

## 前置条件

- Java 25。
- 仓库内 Maven Wrapper；它下载并校验 Maven 3.9.16。
- 开发者 Maven Settings/Nexus 能解析 `com.paytech:paytech-parent:1.0.0-SNAPSHOT` 和 `com.paytech:paytech-mvc-web-starter:1.0.0-SNAPSHOT`。

本基础工程不需要 MySQL、Nacos、Redis、RabbitMQ、XXL-Job、Docker、Vault、其他 Java 服务、凭据或 Secret。

## 构建与验证

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw clean verify
```

Web 模块同时生成普通依赖 JAR 和可执行的 `paytech-commerce-web-1.0.0-SNAPSHOT-exec.jar`。Test 模块只保存测试证据，不生成可部署 JAR。

## 本地运行

```bash
java -jar paytech-commerce-web/target/paytech-commerce-web-1.0.0-SNAPSHOT-exec.jar
```

应用默认只监听 `127.0.0.1:18102`，Context Path 为 `/commerce`。启动后可检查：

```bash
curl --fail --silent http://127.0.0.1:18102/commerce/actuator/health
curl --fail --silent http://127.0.0.1:18102/commerce/actuator/health/liveness
curl --fail --silent http://127.0.0.1:18102/commerce/actuator/health/readiness
```

Actuator discovery 已关闭，健康详情不会对外披露，管理端点与应用共用监听器。当前没有 Gateway 公共路由；这些本地健康 URL 不代表 Gateway 路由或部署已经完成。

## 当前非目标

本基础工程不实现 COM-I01 至 COM-I13、Order、Checkout、Consumer Context、Network Evidence、Acceptance、Cancel、Reversal、Refund 或其他业务能力；也不包含 API JAR、SQL、持久化、Nacos、Redis、消息、补偿任务、调度、Security、跨系统客户端、BFF、前端或部署自动化。

后续每个 Commerce 业务能力必须通过单独批准的纵向切片设计，按真实消费者创建最小契约、代码、SQL、集成和测试。
```

- [ ] **Step 2: Run the full clean reactor verification**

```bash
cd commerce-service
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw clean verify
```

Expected: `BUILD SUCCESS`; 12/12 auto test methods pass—1 application, 1 configuration, 4 management and 6 module-boundary tests. The log must not show any connection attempt to MySQL, Nacos, Redis, RabbitMQ, Vault or another service.

- [ ] **Step 3: Verify artifacts and the effective dependency boundary**

```bash
find paytech-commerce-web/target -maxdepth 1 -type f -name '*.jar' -print | sort
find paytech-commerce-test/target -maxdepth 1 -type f -name '*.jar' -print | sort
./mvnw dependency:tree
```

Expected:

- Web contains the ordinary JAR and exactly one `-exec.jar`.
- Test contains no JAR.
- No `paytech-commerce-api`, JDBC/MyBatis/MySQL, Flyway/Liquibase, Nacos, Redis, RabbitMQ, Compensation Event, XXL-Job, Security, Gateway, WebFlux, OpenFeign, Dubbo, Mockito or Testcontainers artifact appears.

- [ ] **Step 4: Smoke-test the executable JAR on the fixed local address**

First ensure no unrelated process owns the port:

```bash
lsof -nP -iTCP:18102 -sTCP:LISTEN
```

Expected: no output. If another process is present, do not terminate it; report the occupied port instead.

Start only the newly built JAR, poll for at most 30 seconds, inspect all three health groups, then stop only the captured PID:

```bash
commerce_smoke_log="$(mktemp /tmp/commerce-service-smoke.XXXXXX.log)"
set -e
java -jar paytech-commerce-web/target/paytech-commerce-web-1.0.0-SNAPSHOT-exec.jar \
  >"$commerce_smoke_log" 2>&1 &
commerce_smoke_pid=$!
commerce_ready=false
trap 'kill "$commerce_smoke_pid" 2>/dev/null || true' EXIT
for attempt in {1..30}; do
  if curl --fail --silent \
    http://127.0.0.1:18102/commerce/actuator/health >/dev/null; then
    commerce_ready=true
    break
  fi
  sleep 1
done
test "$commerce_ready" = true
curl --fail --silent http://127.0.0.1:18102/commerce/actuator/health
curl --fail --silent http://127.0.0.1:18102/commerce/actuator/health/liveness
curl --fail --silent http://127.0.0.1:18102/commerce/actuator/health/readiness
kill "$commerce_smoke_pid"
wait "$commerce_smoke_pid" || true
trap - EXIT
```

Expected: each response contains `"status":"UP"` and contains neither `"components"` nor `"details"`. The aggregate health response may additionally contain the non-sensitive `"groups"` array; liveness and readiness remain status-only. If startup or polling fails, stop the captured PID if it still exists and inspect only the printed temporary log path.

- [ ] **Step 5: Re-run Commerce model and root requirements checks**

Run from the workspace root:

```bash
node scripts/pdmaner/generate-commerce-model.mjs --check
npm --prefix requirements-site test
```

Expected: the Commerce model is up to date and all requirements-site tests pass. These checks prove that the foundation did not mutate the model or requirements catalogs; they do not prove a physical Commerce database exists.

- [ ] **Step 6: Update only the Commerce row in the current implementation ledger**

Only after Steps 2–5 succeed, replace the Commerce row in `agent-docs/CURRENT_IMPLEMENTATION.md` with:

```markdown
| Commerce | 14 表模型已生成；`paytech-commerce-service` 的 parent + Web + Test Java 基础工程已存在，当前只有 Spring MVC 启动与运维端点 | 自动模型门禁与 Commerce `clean verify` 12/12 通过；可执行 JAR 的本地固定端口健康检查通过 | 未声明 | 无业务 API/API JAR、数据库/SQL、外部基础设施、完整服务或生产部署 |
```

Do not modify any other component row or migration item.

- [ ] **Step 7: Prove protected assets and worktree discipline**

Run from the workspace root, using the explicit evidence file created in Task 1:

```bash
shasum -a 256 \
  commerce-service/.gitignore \
  commerce-service/docs/database/COMMERCE_PHYSICAL_DECISIONS.md \
  commerce-service/docs/database/commerce.pdma.json \
  | diff - /private/tmp/code-mor-commerce-foundation-protected.before.sha256
git -C commerce-service diff --check
git diff --check
git -C commerce-service status --short
git status --short
```

Expected:

- `diff` has no output and exits 0.
- The pre-existing Commerce physical-decisions modification remains present and untouched.
- Only the intended new Commerce foundation files appear in the nested repository.
- Root changes for this work are limited to the Commerce design/spec, this plan and the single current-status row; all unrelated dirty files remain untouched.
- No branch, commit, merge, push or deployment was performed.

## Completion Boundary

This plan is complete only when all five tasks are checked, both intended RED failures were observed for their stated reasons, the boundary RED was observed for the missing Enforcer bans, the full clean build reports 12/12, the executable JAR answers all three fixed-port health URLs, model/site checks pass, and protected hashes are unchanged. The resulting state is an application foundation only; the next implementation plan must select one approved Commerce vertical business slice and must not infer authorization for business APIs, SQL, external integrations or deployment from this result.
