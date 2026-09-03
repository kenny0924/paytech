# Client & Contract Application Foundation Design

Date: 2026-09-04

Status: approved in chat; implementation not started

## 1. Purpose

Initialize `client-contract-service` as a runnable and independently buildable Java service repository without implementing a Client & Contract business capability. The foundation establishes stable Maven coordinates, module boundaries, the Java package, minimal Spring Boot runtime configuration and an automated-test boundary that later CCT vertical slices can extend.

This design is implementation authority only for the foundation described here. It does not authorize a business API, the 37-table database implementation, hand-maintained SQL, Nacos registration, cross-system integration, messaging, compensation execution or deployment infrastructure.

## 2. Existing state and protected inputs

The independent `client-contract-service` Git repository currently contains only database-design assets. It has no Maven reactor, Wrapper, Java source, tests, SQL or runtime configuration.

The following existing files contain uncommitted user changes and are protected inputs for this milestone:

- `client-contract-service/docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md`;
- `client-contract-service/docs/database/client-contract.pdma.json`.

The foundation must not edit, regenerate, format or stage either file. Their byte-level digests must be recorded before implementation and compared after verification.

The current CCT business authority remains the 37-table, 423-field model. In particular, this milestone must not accidentally introduce a persisted Eligibility aggregate, database migrations, placeholder CCT-Ixx contracts or implementations inferred from older CCT plans.

## 3. Selected approach

Use the standard three-module core-service shape:

```text
client-contract-service/
├── pom.xml
├── mvnw
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties
├── README.md
├── client-contract-api/
│   ├── pom.xml
│   └── src/main/java/com/paytech/client/contract/api/package-info.java
├── client-contract-web/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/paytech/client/contract/ClientContractApplication.java
│       └── resources/application.yml
└── client-contract-test/
    ├── pom.xml
    └── src/test/
        ├── java/com/paytech/client/contract/auto/
        │   ├── ClientContractApplicationAutoTest.java
        │   ├── ClientContractConfigurationAutoTest.java
        │   └── ClientContractHealthAutoTest.java
        └── resources/application-auto.yml
```

The root artifact is `com.paytech:client-contract-service:1.0.0-SNAPSHOT`. Child artifacts are `client-contract-api`, `client-contract-web` and `client-contract-test`. The Java base package is `com.paytech.client.contract`.

The API module establishes the stable lightweight contract artifact and allowed package. It contains only package documentation until a real CCT interface and consumer are approved. It must not contain a placeholder DTO, enum, `@HttpExchange` interface, Ping API or speculative shared utility.

The Web module depends on the API module. The Test module depends on the Web module. Production modules never depend on Test.

### 3.1 Alternatives not selected

1. **Web and Test only:** smaller initially, but it would add the lightweight API artifact and alter the reactor when the first internal CCT contract is implemented.
2. **Single-module Spring Boot application:** simpler for a sample application, but it would force future Java consumers to depend on the deployable implementation artifact.
3. **Foundation plus infrastructure or a business slice:** database, Nacos, messaging and CCT use cases would couple the foundation to behavior outside this approved scope.

## 4. Build and dependency boundary

The root project inherits `com.paytech:paytech-parent:1.0.0-SNAPSHOT` with an empty `relativePath`. Java 25, Spring Boot 4.0.8, Maven plugin versions, default `auto` test selection, the explicit `manual` profile and common build policy come from that parent.

The repository carries Maven Wrapper 3.9.16 in `only-script` mode and pins the Maven distribution SHA-256 in `maven-wrapper.properties`. Builds resolve the parent and dependencies through the developer's existing Maven Settings and local Nexus; repository POMs contain no credentials or hosted-repository URL.

The API module introduces no production dependency beyond what is required to compile its package documentation.

The Web module has exactly these production dependencies:

- `com.paytech:client-contract-api:${project.version}`;
- `com.paytech:paytech-mvc-web-starter`;
- `org.springframework.boot:spring-boot-starter-webmvc`;
- `org.springframework.boot:spring-boot-starter-actuator`;
- `io.micrometer:micrometer-registry-prometheus`.

The Test module uses the Spring Boot MVC test support needed for a real random-port server. Mockito artifacts must be excluded. The module is skipped during install and deploy and does not produce a deployable JAR.

The reactor Enforcer boundary rejects dependencies that would expand this milestone. The forbidden set includes:

- JDBC, MyBatis, MySQL and database migration libraries;
- Nacos Discovery and Nacos Config;
- Redis, Redisson and Caffeine;
- RabbitMQ and Spring AMQP;
- XXL-Job and Compensation Event;
- Spring Security and OAuth2;
- Spring Cloud Gateway;
- Flyway, Liquibase, Testcontainers and Mockito.

Later vertical slices may relax only the exact dependency bans required by their separately approved design and tests.

## 5. Runtime behavior and configuration

`ClientContractApplication` is the only production Java class. It uses `@SpringBootApplication` and contains only the standard `main` method.

The production configuration fixes:

- `spring.application.name: client-contract-service`;
- application port `18101`;
- JMX disabled;
- Actuator endpoint discovery disabled;
- Actuator endpoints disabled by default;
- only `health`, `info` and `prometheus` exposed over HTTP;
- health details hidden;
- liveness and readiness probes enabled;
- liveness contains only `livenessState`;
- readiness contains only `readinessState`.

Actuator uses the application listener. No approved Gateway route exposes it publicly. A separate management listener or production network policy belongs to a later deployment design.

The service must start without MySQL, Nacos, Redis, RabbitMQ, XXL-Job, another Java service, Docker, a credential or a secret. It imports no Nacos configuration and registers with no discovery service.

The foundation exposes no `/` handler, Ping endpoint, Controller, Processor, Domain Service, Repository, Mapper, integration client, listener, scheduled job or business health indicator.

## 6. HTTP logging and error boundary

`paytech-mvc-web-starter` is the only MVC request/response logging and exception-mapping implementation. The CCT foundation must not add a service-local Servlet Filter, `@RestControllerAdvice`, error-response type or duplicate Request ID implementation.

The Starter supplies the shared Request ID, full request/response/duration `INFO` event and `GlobalExceptionHandler`. Because this milestone exposes no CCT business endpoint, it defines no CCT-specific error code or failure semantics.

An unmapped route is not a CCT contract. Tests must inspect the MVC route table to prove that no placeholder business handler exists; they must not freeze the shared framework's current unmapped-route response as a CCT API behavior.

## 7. Automated test design

Implementation follows a recorded red-green-refactor sequence:

1. establish the Maven and test harness;
2. write the application-boundary test before `ClientContractApplication` exists;
3. run the targeted test and record that compilation fails because the application class is absent;
4. add the minimum application class and configuration;
5. run the targeted test until it passes;
6. add focused configuration, health and dependency-boundary checks;
7. run the complete reactor from a clean output directory.

The automated tests use `@SpringBootTest(webEnvironment = RANDOM_PORT)` and the real embedded MVC server. They do not use Mock MVC as a substitute for the server, Mockito, `@MockBean`, method-level stubs, Testcontainers, a fixed port or external infrastructure.

The tests verify:

- the application starts with no external dependency;
- the allocated application port is positive;
- `/actuator/health`, `/actuator/health/liveness` and `/actuator/health/readiness` return HTTP `200` and status `UP`;
- liveness and readiness contain only the approved state contributors;
- the production MVC route table has no exact `/` or placeholder business handler;
- production configuration contains the exact service name, port, exposure and health-detail boundary;
- prohibited dependency families are absent from the resolved reactor.

The exact application entry class may be excluded from JaCoCo because testing a static `main` method does not provide business confidence. No broader package exclusion is allowed. Later executable production code remains subject to the project target of at least 90% instruction and branch coverage.

## 8. Packaging and developer documentation

The Web module produces both a normal dependency JAR and an executable `client-contract-web-1.0.0-SNAPSHOT-exec.jar`. The executable JAR is the only deployable artifact in this repository.

The repository README documents:

- the responsibility of each module;
- Java 25 and Maven/Nexus prerequisites;
- `./mvnw clean verify`;
- the command for running the verified executable JAR;
- port `18101` and the three health URLs;
- the absence of business APIs and external-infrastructure dependencies;
- the protected status of the existing database-design assets;
- the fact that future CCT vertical slices require their own approved design.

The repository `.gitignore` must cover Maven output, IDE metadata, logs, local environment files and worktree metadata without discarding the existing database backup assets.

## 9. Worktree and change discipline

Implementation must occur without overwriting the current uncommitted CCT database-design changes. Before any service edit, record:

- the Git status of `client-contract-service`;
- byte-level SHA-256 digests of both protected database files.

Only the new foundation files and a focused `.gitignore` update may be edited. Do not run the CCT PDManer generator. After the complete build, compare the protected files with their recorded digests and leave their existing modifications unstaged.

Root workspace documentation is not part of the implementation milestone unless a status statement is required after the service is verified. Any such update must be a separate, small semantic hunk and must preserve unrelated untracked or modified files.

## 10. Completion criteria

The foundation is complete only when all of the following are fresh and successful:

- `java -version` and Maven Wrapper output both use Java 25;
- Maven Wrapper reports version 3.9.16;
- the three-module reactor and dependency direction match this design;
- the recorded RED test fails for the expected missing application class;
- `./mvnw clean verify` passes from clean target directories;
- health, route-table and configuration auto tests pass against a random-port real server;
- dependency analysis confirms that no forbidden foundation dependency entered the reactor;
- the executable `client-contract-web-1.0.0-SNAPSHOT-exec.jar` exists;
- `git diff --check` passes in the service repository;
- both protected CCT database files retain their exact pre-implementation SHA-256 digests;
- no placeholder, `TODO by Codex`, business endpoint, SQL or external-infrastructure configuration was added.

## 11. Explicit non-goals

This milestone does not implement or create:

- CCT-I01 through CCT-I16 HTTP contracts or Controllers;
- Party, Profile, Client, Local Partner, Operating Entity, Application, Assignment, Agreement, Consumer Legal or Commercial Term behavior;
- DataObjects, Mapper XML, Repository or hand-maintained Schema/initial-data SQL;
- persisted Eligibility, generic Retention/Legal Hold or any database schema;
- Platform, Risk, Payment, Commerce, Finance or Vault HTTP clients;
- MQ publication or consumption, command idempotency, BCR persistence or Compensation Event execution;
- Nacos Discovery/Config, Redis, RabbitMQ, XXL-Job, Security or deployment automation;
- Gateway routes, BFFs, frontend projects or public APIs.

The first business capability must be designed and implemented later as a focused vertical slice through the established `api -> web -> test` boundary.

## 12. Authoritative references

- `agent-docs/DEVELOPMENT_CONVENTIONS.md`;
- `agent-docs/PROJECT_CONTEXT.md`;
- `agent-docs/TECHNOLOGY_STACK.md`;
- `agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md`;
- `agent-docs/APPLICATION_ARCHITECTURE.md`;
- `agent-docs/JAVA_PROJECT_STRUCTURE.md`;
- `agent-docs/UNIT_TEST_GUIDE.md`;
- `agent-docs/database/README.md`;
- `agent-docs/database/CLIENT_CONTRACT_DATABASE.md`;
- `agent-docs/database/scenarios/CLIENT_CONTRACT_REAL_WORLD_SCENARIOS.md`;
- `docs/superpowers/specs/2026-08-30-platform-unified-human-authentication-design.md`;
- `docs/superpowers/specs/2026-09-01-platform-support-application-foundation-design.md`;
- `docs/superpowers/specs/2026-09-02-manual-infrastructure-and-platform-runtime-simplification.md`;
- `docs/superpowers/specs/2026-09-02-mvc-web-logging-and-internal-http-simplification-design.md`;
- `client-contract-service/docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md`;
- `client-contract-service/docs/database/client-contract.pdma.json`.
