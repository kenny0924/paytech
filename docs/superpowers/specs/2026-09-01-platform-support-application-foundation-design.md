# Platform Support Application Foundation Design

Date: 2026-09-01

Status: Approved and implemented foundation scope. The user explicitly authorized a foundation-only first milestone; the implementation remains uncommitted and does not expand the business authorization.

> Historical scope note (2026-09-02): this document records the original foundation milestone. The later authorized
> Phase A runtime now adds Flyway/MyBatis persistence, deterministic initialization, mTLS and the PLT-I20 full-Snapshot
> provider. Statements below that describe “no database/API” are acceptance facts for this earlier milestone, not the
> current service status; use [`platform-support-service/README.md`](../../../platform-support-service/README.md) and the
> [dynamic public-control-plane design](./2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md)
> for the current boundary.

## 1. Purpose

Initialize `platform-support-service` as a runnable, independently buildable Java service repository without prematurely implementing any Platform business capability. The result establishes the build, module, package, runtime-configuration and automated-test boundaries that later vertical slices can extend.

This authorization supersedes earlier “Platform Java modules are not authorized” status text only for the foundation described here. It does not authorize business APIs, database implementation, authentication, public control-plane behavior or Gateway runtime cutover.

## 2. Selected approach

Use the standard core-service Maven shape already approved by `agent-docs/JAVA_PROJECT_STRUCTURE.md`:

```text
platform-support-service/
├── pom.xml
├── mvnw
├── .mvn/wrapper/maven-wrapper.properties
├── platform-support-api/
│   ├── pom.xml
│   └── src/main/java/com/paytech/platform/support/api/package-info.java
├── platform-support-web/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/paytech/platform/support/PlatformSupportApplication.java
│       └── resources/application.yml
└── platform-support-test/
    ├── pom.xml
    └── src/test/
        ├── java/com/paytech/platform/support/auto/PlatformSupportApplicationAutoTest.java
        └── resources/application-auto.yml
```

The root artifact is `com.paytech:platform-support-service:1.0.0-SNAPSHOT`. Child artifacts are `platform-support-api`, `platform-support-web` and `platform-support-test`. The Java base package is `com.paytech.platform.support`.

The API module establishes the independent contract artifact and allowed package now, but contains no request, response, enum or `@HttpExchange` interface until a real Platform interface is authorized. `package-info.java` documents that boundary without inventing a placeholder endpoint. The Web module depends on the API module; the Test module depends on the Web module. Production modules never depend on Test.

### Alternatives considered

1. **Web + Test only:** smaller today, but diverges from the approved core-service module boundary and would require changing published Maven coordinates when the first internal contract arrives.
2. **Foundation plus authentication or database:** produces a larger vertical slice, but violates the user-selected foundation-only scope and would couple scaffolding to unresolved implementation details.
3. **Single-module Spring Boot application:** common for small open-source samples, but inappropriate for this system because internal contract consumers must eventually depend on a lightweight API JAR rather than the deployable Web module.

## 3. Build and dependency boundary

The project inherits `com.paytech:paytech-parent:1.0.0-SNAPSHOT` with an empty `relativePath`, so it resolves the already governed Java 25, Spring Boot 4.0.8, Maven plugin and test rules from the local Nexus/Maven configuration. The repository carries the approved Maven Wrapper 3.9.16 using the wrapper's `only-script` mode and pins the Maven distribution SHA-256 checksum.

The Web module has only these production dependencies:

- `platform-support-api`;
- `paytech-mvc-web-starter`;
- `spring-boot-starter-webmvc`;
- `spring-boot-starter-actuator`;
- `micrometer-registry-prometheus`.

The Test module uses `spring-boot-starter-webmvc-test` and excludes Mockito artifacts. It is skipped during install/deploy and does not produce a deployable artifact.

The reactor Enforcer boundary rejects dependencies that would expand this milestone into business implementation, including MyBatis/JDBC/MySQL, Flyway, Nacos, Redis/Redisson, RabbitMQ, Compensation Event/XXL-Job, Spring Security and Spring Cloud Gateway. Later vertical slices may relax only the exact bans required by their separately approved design and tests.

## 4. Runtime behavior

`PlatformSupportApplication` is the only production Java class in the Web module. It uses `@SpringBootApplication` and has no Controller, Processor, Domain Service, Repository, Mapper, integration client, listener or scheduled job.

Production configuration fixes:

- `spring.application.name: platform-support-service`;
- application port `18106`;
- JMX disabled;
- Actuator endpoint discovery disabled;
- endpoints disabled by default;
- only `health`, `info` and `prometheus` exposed;
- health details hidden;
- liveness and readiness probes enabled with `livenessState` and `readinessState` only.

Actuator remains on the application listener for this foundation. The approved public Route Catalog contains no Actuator route, so Gateway cannot expose it externally. A separate management listener, network policy and production security model require a deployment design and are not guessed here.

The application imports no Nacos configuration and requires no MySQL, Redis, RabbitMQ, Docker, external service, credential or secret to start. Local infrastructure integration begins only with a business slice that has a demonstrated consumer.

## 5. Automated test design

Tests follow the repository's lightweight TDD requirement and Spring Boot's real-server pattern:

1. establish the Maven/test harness;
2. write `PlatformSupportApplicationAutoTest` before the application class and confirm compilation fails because `PlatformSupportApplication` does not exist;
3. add the minimum application/configuration needed to start;
4. run the same test until it passes;
5. refactor configuration and rerun the complete reactor.

The auto tests use `@SpringBootTest(webEnvironment = RANDOM_PORT)` against the real embedded server. Health tests use the Spring MVC `RestTestClient`; the application-boundary test inspects the production MVC route table. They verify:

- the application starts without external infrastructure;
- `/actuator/health`, `/actuator/health/liveness` and `/actuator/health/readiness` return HTTP 200 and `UP`;
- the random application port is positive and no exact `/` MVC handler exists, proving the foundation did not create a placeholder business endpoint.

The route-table assertion is intentional. The shared MVC starter currently converts Spring's unmapped static-resource exception to its generic 500 contract; this foundation neither changes that shared behavior nor freezes it as a Platform API contract merely to assert “no route.”

No Mockito, `@MockBean`, method-level Stub, Testcontainers, shared runtime dependency, fixed test port or `Thread.sleep` is allowed. The default Maven lifecycle runs only `auto`; `manual` remains an explicit inherited profile.

## 6. Packaging and developer experience

The Web module produces both a normal dependency JAR and an executable `-exec.jar`, matching the existing Gateway packaging convention. The repository README documents:

- the module responsibilities;
- Java 25 and local Maven/Nexus prerequisites;
- `./mvnw clean verify`;
- `java -jar platform-support-web/target/platform-support-web-1.0.0-SNAPSHOT-exec.jar` after the verified build;
- the local port and health URLs;
- the explicit non-goals of this milestone.

The `.gitignore` covers Maven/Java output, IDE files, logs, local environment files and worktree metadata. No credential, generated database structure or environment-specific endpoint is committed.

## 7. Documentation synchronization

After the service reactor is green, update the root status documentation semantically rather than replacing files. The synchronized statement is:

> Platform Support now has an initialized Java foundation (`api`, `web`, `test`) that starts and passes isolated auto tests. It still has no business interfaces, authentication implementation, Flyway migration, business tables, Nacos/MQ/Redis integration, Console/BFF implementation, deployment configuration or dynamic Gateway Snapshot cutover.

Expected root updates are limited to `AGENTS.md`, `README.md`, `agent-docs/README.md`, `agent-docs/PROJECT_CONTEXT.md`, `agent-docs/TECHNOLOGY_STACK.md`, `agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md`, `agent-docs/APPLICATION_ARCHITECTURE.md` and `agent-docs/JAVA_PROJECT_STRUCTURE.md`. Requirements catalogs and database design assets do not change because no product behavior or schema decision changes.

## 8. Worktree and integration discipline

Implementation occurs in a dedicated `platform-support-service` Git worktree created from its current `main` HEAD. The two current uncommitted database design files are copied byte-for-byte into the worktree as integration inputs and are never edited by this milestone. Root status documentation remains in the existing isolated root worktree and is merged by small semantic hunks only after service verification.

No commit, push, merge, deployment or shared-environment mutation is authorized. Before copying new service files back to the main nested repository, recheck its status and coordinate with the other Code-MoR tasks. Never overwrite the current Platform PDManer model or physical decisions file.

## 9. Completion criteria

The foundation is complete only when all of the following are fresh and successful:

- Java and Maven runtime checks show Java 25 and Maven Wrapper 3.9.16;
- the expected three-module reactor is present with the fixed dependency direction;
- the recorded RED test failure is caused by the missing application class;
- `./mvnw clean verify` passes from a clean output directory;
- the executable `platform-support-web-1.0.0-SNAPSHOT-exec.jar` is produced;
- dependency analysis confirms no database, Flyway, Nacos, Redis, MQ, Security, Gateway or Mockito dependency entered the reactor;
- service-local `git diff --check` passes;
- root documentation `git diff --check` passes;
- existing Platform PDManer generator/model tests remain green and the two database assets remain byte-identical to their pre-task main-worktree copies.

## 10. Engineering references

- Spring Boot official testing guidance for `RANDOM_PORT` and a real test client: <https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html>
- Apache Maven Wrapper guidance for a repository-pinned Maven distribution: <https://maven.apache.org/tools/mavenwrapper.html>
- Spring's canonical Petclinic sample for a small, explicit Boot entry point, repository-owned Wrapper and documented local commands: <https://github.com/spring-projects/spring-petclinic>

These projects are quality references only. Code-MoR's module boundaries, versions, security rules and test constraints remain authoritative.
