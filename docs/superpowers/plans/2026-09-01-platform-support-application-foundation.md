# Platform Support Application Foundation Implementation Plan

> Historical implementation note (2026-09-02): this plan verifies the original foundation-only milestone. Platform
> Support has since added the separately authorized Phase A persistence/initialization and PLT-I20 full-Snapshot slice;
> “no database/API” assertions below are historical acceptance criteria, not the current service boundary.

> **Implementation mode:** The user explicitly requires direct, lightweight TDD for coding. Do not invoke Superpowers implementation/execution skills. Execute each RED → GREEN → REFACTOR cycle directly and leave all changes uncommitted.

**Goal:** Initialize a runnable, independently tested Platform Support Java foundation without implementing business APIs, persistence or middleware integrations.

**Architecture:** Create the standard `platform-support-api`, `platform-support-web` and `platform-support-test` Maven modules under the independent `platform-support-service` repository. The Web module contains only the Spring Boot entry point and operability configuration; the Test module starts the real MVC server on a random port and verifies health plus the absence of placeholder routes.

**Tech Stack:** Java 25 LTS, Maven Wrapper 3.9.16, `paytech-parent:1.0.0-SNAPSHOT`, Spring Boot 4.0.8, Spring MVC, Actuator, Micrometer Prometheus and JUnit/Spring Boot Test.

**Spec:** `docs/superpowers/specs/2026-09-01-platform-support-application-foundation-design.md`

**Execution status (2026-09-01):** Completed and left uncommitted. The first RED failed only because `PlatformSupportApplication` was absent; the health/configuration RED then failed because Actuator and production YAML were absent. The final main-workspace reactor passes `./mvnw clean verify` with 3/3 tests, produces both Web JARs, starts on `18106`, reports Health/Liveness/Readiness `UP`, exposes Prometheus with HTTP 200, passes dependency/source/secret scans, and preserves the two database-design SHA-256 values recorded below.

## Global Constraints

- Base package is exactly `com.paytech.platform.support`.
- The production application name is exactly `platform-support-service`; the local application port is exactly `18106`.
- Create no business Controller, Processor, Domain Service, Repository, Mapper, event handler, scheduler or integration client.
- Add no MyBatis/JDBC/MySQL, Flyway, Nacos, Redis/Redisson, RabbitMQ, Compensation Event/XXL-Job, Spring Security or Spring Cloud Gateway dependency.
- Add no Flyway migration, business table, datasource configuration, Nacos import, credential, secret or environment-specific external URL.
- Preserve `docs/database/platform-support.pdma.json` and `docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md` byte-for-byte.
- Default tests run only `auto`, use a real random-port server and contain no Mockito, `@MockBean`, method Stub, shared infrastructure or fixed sleep.
- Use `apply_patch` for authored text/code. Reusing the already approved Maven Wrapper and `.gitignore` byte-for-byte is a mechanical copy, not a rewrite.
- Do not commit, push, merge, deploy, start shared infrastructure or mutate any database.

---

### Task 1: Isolate the nested service and establish the Maven reactor

**Files:**

- Reuse unchanged: `platform-support-service/docs/database/platform-support.pdma.json`
- Reuse unchanged: `platform-support-service/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md`
- Create: `platform-support-service/.gitignore`
- Create: `platform-support-service/mvnw`
- Create: `platform-support-service/.mvn/wrapper/maven-wrapper.properties`
- Create: `platform-support-service/pom.xml`
- Create: `platform-support-service/platform-support-api/pom.xml`
- Create: `platform-support-service/platform-support-api/src/main/java/com/paytech/platform/support/api/package-info.java`
- Create: `platform-support-service/platform-support-web/pom.xml`
- Create: `platform-support-service/platform-support-test/pom.xml`

**Interfaces:**

- Produces Maven coordinates `com.paytech:platform-support-api`, `com.paytech:platform-support-web` and `com.paytech:platform-support-test`, all at `${project.version}`.
- Produces dependency direction `test -> web -> api`.
- Produces no HTTP or Java business contract.

- [ ] **Step 1: Recheck both repositories and create the service worktree**

Run:

```bash
git -C /Users/kenny/Desktop/Workspace/Code-MoR status --short
git -C /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service status --short --branch
git -C /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service worktree list --porcelain
mkdir -p /Users/kenny/.codex/worktrees/platform-support-app
git -C /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service worktree add \
  -b codex/platform-support-app-foundation \
  /Users/kenny/.codex/worktrees/platform-support-app/platform-support-service main
```

Expected: the new worktree starts from service `main`; the original nested repository remains dirty only in its two database design files.

- [ ] **Step 2: Synchronize the current database design inputs without editing them**

Run the mechanical copies and verify the current baseline hashes:

```bash
cp -p /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service/docs/database/platform-support.pdma.json \
  /Users/kenny/.codex/worktrees/platform-support-app/platform-support-service/docs/database/platform-support.pdma.json
cp -p /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md \
  /Users/kenny/.codex/worktrees/platform-support-app/platform-support-service/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md
shasum -a 256 \
  /Users/kenny/.codex/worktrees/platform-support-app/platform-support-service/docs/database/platform-support.pdma.json \
  /Users/kenny/.codex/worktrees/platform-support-app/platform-support-service/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md
```

Expected hashes:

```text
04b9825f6763aec30dbc3ecf0ae398edb3051c835fa9bf1d3f5ef0ee9ac42262  platform-support.pdma.json
7b6a03557328ba8fc9d5ed02105124221265cc29f0c9176eea7f11391bf71d4b  PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md
```

- [ ] **Step 3: Reuse the approved wrapper and ignore policy**

Run:

```bash
cp -p /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway/mvnw \
  /Users/kenny/.codex/worktrees/platform-support-app/platform-support-service/mvnw
cp -p /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway/.gitignore \
  /Users/kenny/.codex/worktrees/platform-support-app/platform-support-service/.gitignore
mkdir -p /Users/kenny/.codex/worktrees/platform-support-app/platform-support-service/.mvn/wrapper
cp -p /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway/.mvn/wrapper/maven-wrapper.properties \
  /Users/kenny/.codex/worktrees/platform-support-app/platform-support-service/.mvn/wrapper/maven-wrapper.properties
```

Then add this exact checksum property to `maven-wrapper.properties` with `apply_patch`:

```properties
distributionSha256Sum=5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce
```

- [ ] **Step 4: Create the root reactor POM**

Create `pom.xml` with `paytech-parent` and empty `relativePath`, internal-module dependency management, the three modules, and a `foundation-scope` Enforcer execution. The dependency bans must contain these exact patterns:

```xml
<exclude>org.mybatis:*</exclude>
<exclude>org.mybatis.spring.boot:*</exclude>
<exclude>org.springframework.boot:spring-boot-starter-jdbc</exclude>
<exclude>org.springframework.boot:spring-boot-starter-data-jdbc</exclude>
<exclude>com.mysql:*</exclude>
<exclude>org.flywaydb:*</exclude>
<exclude>com.alibaba.nacos:*</exclude>
<exclude>com.alibaba.cloud:*</exclude>
<exclude>org.springframework.boot:spring-boot-starter-data-redis</exclude>
<exclude>org.springframework.boot:spring-boot-starter-data-redis-reactive</exclude>
<exclude>org.redisson:*</exclude>
<exclude>org.springframework.boot:spring-boot-starter-amqp</exclude>
<exclude>org.springframework.amqp:*</exclude>
<exclude>com.xuxueli:*</exclude>
<exclude>com.paytech:paytech-caffeine-starter</exclude>
<exclude>com.paytech:paytech-redis-starter</exclude>
<exclude>com.paytech:paytech-compensation-event</exclude>
<exclude>com.paytech:paytech-xxl-job-starter</exclude>
<exclude>org.springframework.boot:spring-boot-starter-security</exclude>
<exclude>org.springframework.security:*</exclude>
<exclude>org.springframework.cloud:spring-cloud-starter-gateway*</exclude>
<exclude>org.mockito:*</exclude>
```

Use `<searchTransitive>true</searchTransitive>` so exclusions and unexpected transitive scope expansion fail during `validate`.

- [ ] **Step 5: Create the three child POMs and API package boundary**

`platform-support-api/pom.xml` has no dependencies. Its only Java source is:

```java
/**
 * Platform Support internal HTTP contract boundary.
 *
 * <p>Business interfaces are added only with an approved vertical slice.</p>
 */
package com.paytech.platform.support.api;
```

`platform-support-web/pom.xml` initially depends on:

```xml
<dependency>
    <groupId>com.paytech</groupId>
    <artifactId>platform-support-api</artifactId>
</dependency>
<dependency>
    <groupId>com.paytech</groupId>
    <artifactId>paytech-mvc-web-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>
```

Configure `spring-boot-maven-plugin` to repackage with classifier `exec`.

`platform-support-test/pom.xml` depends on `platform-support-web` and test-scoped `spring-boot-starter-webmvc-test`. Exclude both `org.mockito:mockito-core` and `org.mockito:mockito-junit-jupiter`. Set `maven.install.skip` and `maven.deploy.skip` to `true`; configure `maven-jar-plugin` with `skipIfEmpty=true`.

- [ ] **Step 6: Validate the build model and toolchain**

Run with Java 25:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"
cd /Users/kenny/.codex/worktrees/platform-support-app/platform-support-service
./mvnw --version
./mvnw validate -DskipTests
```

Expected: Java 25, Maven 3.9.16, a successful three-module reactor and no business/middleware dependency.

---

### Task 2: RED/GREEN the real Spring Boot application

**Files:**

- Create test first: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/PlatformSupportApplicationAutoTest.java`
- Create test configuration: `platform-support-service/platform-support-test/src/test/resources/application-auto.yml`
- Create after RED: `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/PlatformSupportApplication.java`

**Interfaces:**

- Produces one deployable Spring Boot application class.
- Exposes no business route; `/` remains unmapped.

- [ ] **Step 1: Write the failing random-port application test**

Create:

```java
package com.paytech.platform.support.auto;

import com.paytech.platform.support.PlatformSupportApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Platform Support 应用基础启动测试。 */
@ActiveProfiles("auto")
@SpringBootTest(
    classes = PlatformSupportApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.port=0"
)
class PlatformSupportApplicationAutoTest {

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

- [ ] **Step 2: Run the test and capture the expected RED evidence**

Run:

```bash
./mvnw -pl platform-support-test -am -Dtest=PlatformSupportApplicationAutoTest test
```

Expected: compilation fails because `com.paytech.platform.support.PlatformSupportApplication` does not exist. Record that exact failure; a dependency-resolution or environment failure is not valid RED evidence.

- [ ] **Step 3: Add the minimum application implementation**

Create:

```java
package com.paytech.platform.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Platform Support 服务入口。 */
@SpringBootApplication
public class PlatformSupportApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformSupportApplication.class, args);
    }
}
```

- [ ] **Step 4: Run the focused test and confirm GREEN**

Run:

```bash
./mvnw -pl platform-support-test -am -Dtest=PlatformSupportApplicationAutoTest test
```

Expected: one passing auto test; the embedded MVC server starts on a positive random port and the production MVC route table has no exact `/` handler. Do not assert the shared MVC starter's current unmapped-resource error mapping as a Platform contract.

- [ ] **Step 5: Refactor check**

Inspect the compiled source set and confirm the Web module contains only `PlatformSupportApplication.java`. Rerun the focused test after any formatting or naming cleanup.

---

### Task 3: RED/GREEN production identity and health probes

**Files:**

- Create test first: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/PlatformSupportHealthAutoTest.java`
- Create test first: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/PlatformSupportConfigurationAutoTest.java`
- Modify after RED: `platform-support-service/platform-support-web/pom.xml`
- Create after RED: `platform-support-service/platform-support-web/src/main/resources/application.yml`

**Interfaces:**

- Produces application identity `platform-support-service` on local port `18106`.
- Produces Actuator health, liveness and readiness endpoints on the same application listener.
- Produces Prometheus registry support without an external monitoring dependency.

- [ ] **Step 1: Write the failing health test**

Create:

```java
package com.paytech.platform.support.auto;

import com.paytech.platform.support.PlatformSupportApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Platform Support 健康端点自动测试。 */
@ActiveProfiles("auto")
@AutoConfigureRestTestClient
@SpringBootTest(
    classes = PlatformSupportApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.port=0"
)
class PlatformSupportHealthAutoTest {

    @Autowired
    private RestTestClient restTestClient;

    @Test
    void test_healthEndpointsReportUp() {
        assertUp("/actuator/health");
        assertUp("/actuator/health/liveness");
        assertUp("/actuator/health/readiness");
    }

    private void assertUp(String path) {
        restTestClient.get().uri(path).exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .value(body -> Assertions.assertTrue(body.contains("\"status\":\"UP\""),
                "健康端点未返回 UP: " + path + ", body=" + body));
    }
}
```

- [ ] **Step 2: Write the failing production configuration contract test**

Create:

```java
package com.paytech.platform.support.auto;

import java.io.IOException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/** Platform Support 生产配置契约测试。 */
class PlatformSupportConfigurationAutoTest {

    @Test
    void test_productionConfigurationUsesApprovedFoundationBoundary() throws IOException {
        StandardEnvironment environment = loadProductionConfiguration();

        Assertions.assertEquals("platform-support-service",
            environment.getRequiredProperty("spring.application.name"));
        Assertions.assertEquals(18106,
            environment.getRequiredProperty("server.port", Integer.class));
        Assertions.assertFalse(environment.getRequiredProperty("spring.jmx.enabled", Boolean.class));
        Assertions.assertFalse(environment.getRequiredProperty(
            "management.endpoints.enabled-by-default", Boolean.class));
        Assertions.assertFalse(environment.getRequiredProperty(
            "management.endpoints.web.discovery.enabled", Boolean.class));
        Assertions.assertEquals("health,info,prometheus",
            environment.getRequiredProperty("management.endpoints.web.exposure.include"));
        Assertions.assertEquals("never",
            environment.getRequiredProperty("management.endpoint.health.show-details"));
        Assertions.assertEquals("livenessState",
            environment.getRequiredProperty("management.endpoint.health.group.liveness.include"));
        Assertions.assertEquals("readinessState",
            environment.getRequiredProperty("management.endpoint.health.group.readiness.include"));
        Assertions.assertNull(environment.getProperty("spring.config.import"));
        Assertions.assertNull(environment.getProperty("spring.datasource.url"));
        Assertions.assertNull(environment.getProperty("spring.data.redis.host"));
        Assertions.assertNull(environment.getProperty("spring.rabbitmq.host"));
    }

    private StandardEnvironment loadProductionConfiguration() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
            .load("platform-support-production", new ClassPathResource("application.yml"))
            .forEach(environment.getPropertySources()::addLast);
        return environment;
    }
}
```

- [ ] **Step 3: Run both tests and verify RED**

Run:

```bash
./mvnw -pl platform-support-test -am \
  -Dtest=PlatformSupportHealthAutoTest,PlatformSupportConfigurationAutoTest test
```

Expected: health paths are 404 and/or production `application.yml` is absent. The earlier application-start test remains green.

- [ ] **Step 4: Add the minimum operability dependencies**

Add to the Web module:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

- [ ] **Step 5: Add the approved production configuration**

Create `application.yml`:

```yaml
server:
  port: 18106
spring:
  application:
    name: platform-support-service
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

- [ ] **Step 6: Run the operability tests and complete GREEN/REFACTOR**

Run:

```bash
./mvnw -pl platform-support-test -am \
  -Dtest=PlatformSupportHealthAutoTest,PlatformSupportConfigurationAutoTest test
./mvnw -pl platform-support-test -am test
```

Expected: all application, health and configuration tests pass. Refactor duplicated test annotations into a small `PlatformSupportAutoTestBase` only if at least two classes need identical setup and the base remains stateless.

---

### Task 4: Package, document and prove the foundation boundary

**Files:**

- Create: `platform-support-service/README.md`
- Verify: all service POMs and generated artifacts

**Interfaces:**

- Produces a normal Web JAR and `platform-support-web-1.0.0-SNAPSHOT-exec.jar`.
- Produces a developer runbook with no secret-bearing command.

- [ ] **Step 1: Write the service README**

Document module responsibilities, prerequisites, `./mvnw clean verify`, the Web-module run command, `http://127.0.0.1:18106/actuator/health`, and the explicit non-goals. State that the API package has no business interface yet and that the two database design files remain design-only authority.

- [ ] **Step 2: Run the clean reactor**

Run:

```bash
./mvnw clean verify
```

Expected: all three modules succeed from clean output; only `auto` tests execute.

- [ ] **Step 3: Verify packaging and dependency scope**

Run:

```bash
test -f platform-support-web/target/platform-support-web-1.0.0-SNAPSHOT.jar
test -f platform-support-web/target/platform-support-web-1.0.0-SNAPSHOT-exec.jar
./mvnw dependency:tree -Dscope=test -DoutputFile=target/dependency-tree.txt
rg -n "mybatis|mysql|flyway|nacos|redis|redisson|rabbit|amqp|xxl-job|spring-security|gateway|mockito" \
  target/dependency-tree.txt
```

Expected: both JAR checks pass and the `rg` scan has no matches. If a test starter adds a banned transitive dependency, exclude it explicitly instead of weakening Enforcer.

- [ ] **Step 4: Verify source and configuration boundaries**

Run:

```bash
find platform-support-web/src/main/java -type f | sort
rg -n "@RestController|@Controller|@Service|@Repository|@Mapper|@Scheduled|@RabbitListener|@KafkaListener" \
  platform-support-api/src platform-support-web/src || true
rg -n "jdbc:|password|secret|token|spring\.config\.import|datasource|redis|rabbitmq|nacos" \
  platform-support-web/src/main/resources platform-support-test/src/test/resources || true
```

Expected: the only Web production Java file is `PlatformSupportApplication.java`; no business stereotype or secret/external dependency configuration is found.

- [ ] **Step 5: Reconfirm immutable database inputs and formatting**

Run:

```bash
shasum -a 256 docs/database/platform-support.pdma.json \
  docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md
git diff --check
git status --short
```

Expected: the two hashes still match Task 1; all application-foundation files are visible and no unexpected database file is changed.

---

### Task 5: Synchronize root status documentation semantically

**Files:**

- Already modified: `AGENTS.md`
- Modify: `README.md`
- Modify: `agent-docs/README.md`
- Modify: `agent-docs/PROJECT_CONTEXT.md`
- Modify: `agent-docs/TECHNOLOGY_STACK.md`
- Modify: `agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md`
- Modify: `agent-docs/APPLICATION_ARCHITECTURE.md`
- Modify: `agent-docs/JAVA_PROJECT_STRUCTURE.md`

**Interfaces:**

- Produces one consistent status statement: Platform has a tested foundation, but no business or persistence implementation.
- Does not change requirements, interface IDs, database statistics or runtime Gateway behavior.

- [ ] **Step 1: Recheck single-writer coordination**

Confirm Gateway, Risk, Finance and architecture tasks still report no writes to these shared files. Re-read each target hunk from the latest main workspace immediately before patching.

- [ ] **Step 2: Replace only stale Platform implementation-status clauses**

Update clauses that currently say all six core Java services are uncreated or that only Gateway Java is authorized. Preserve every current database statistic and cross-system contract. Use this exact semantic statement wherever a consolidated status is needed:

```text
Platform Support now has an initialized and isolated Java foundation with api, web and test modules. It contains only the Spring Boot MVC entry point, Actuator configuration and random-port auto tests; business interfaces, authentication, Flyway migrations, business tables, Nacos/MQ/Redis integration, Console/BFF code, deployment configuration and dynamic Gateway Snapshot cutover remain unimplemented and unauthorized by this foundation milestone.
```

- [ ] **Step 3: Keep navigation and repository status accurate**

Add the new design/spec and implementation-plan links only where the document already maintains implementation navigation. Do not add these files to database-authority lists, because the foundation does not supersede the Platform database designs.

- [ ] **Step 4: Run root documentation and regression gates**

Run:

```bash
git diff --check
node scripts/verify-java-foundation.mjs
npm --prefix requirements-site test
npm --prefix requirements-site run lint
```

Expected: existing Foundation checks, requirements build/tests and lint remain green. Any unrelated environment or pre-existing failure must be reported separately rather than hidden.

---

### Task 6: Integrate only verified new service files into the main nested repository

**Files:**

- Add to main nested repository: every new application-foundation file from Tasks 1–4
- Do not copy over main nested repository: `docs/database/platform-support.pdma.json`
- Do not copy over main nested repository: `docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md`

**Interfaces:**

- Produces the same verified foundation in `/Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service`.
- Preserves the main nested repository's current database design files and uncommitted owner changes.

- [ ] **Step 1: Recheck the main nested repository immediately before integration**

Run:

```bash
git -C /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service status --short --branch
shasum -a 256 \
  /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service/docs/database/platform-support.pdma.json \
  /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md
```

Stop if any new non-database file appears or either database hash changed; coordinate the exact overlap before proceeding.

- [ ] **Step 2: Add only the new foundation files**

Use file-by-file patches or a reviewed new-file-only mechanical synchronization. Explicitly exclude `docs/database/**`, `.git/**` and `target/**`. Compare each destination before writing; never use a whole-directory overwrite.

- [ ] **Step 3: Run the clean verification in the integrated main nested repository**

Run:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"
cd /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service
./mvnw clean verify
git diff --check
```

Expected: the same clean reactor passes in the integrated location and the original two database hashes remain unchanged.

- [ ] **Step 4: Run final root regression gates and report exact state**

From `/Users/kenny/Desktop/Workspace/Code-MoR` run:

```bash
git diff --check
node scripts/pdmaner/platform-support/generate-platform-support-model.mjs --check
node --test scripts/pdmaner/platform-support-model.test.mjs \
  scripts/pdmaner/platform-support-scenario-doc.test.mjs
npm --prefix requirements-site test
npm --prefix requirements-site run lint
```

Report:

- RED and GREEN test commands with actual counts;
- service and root changed-file lists separately;
- final database-file hashes;
- Maven reactor/package evidence;
- any pre-existing dirty paths kept untouched;
- that no commit, push, merge, deployment or environment mutation occurred.
