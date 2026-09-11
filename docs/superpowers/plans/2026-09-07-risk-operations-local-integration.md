# Risk Operations Local Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Register the private Risk service for BFF discovery and prepare a safe local Nginx→Gateway→BFF→PLT-I02→Risk integration path without silently applying SQL or deploying containers.

**Architecture:** Risk gains only Nacos service registration, not configuration-center consumption or new business behavior. The repository-owned test environment gains a backend-only Risk container, exact disabled Platform incremental DML, a 128 KiB edge envelope and a synthetic-data smoke script; static validation is automatic, while SQL application/container replacement/live smoke are explicit authorization gates.

**Tech Stack:** Java 25, Maven Wrapper 3.9.16, Spring Boot 4.0.8, Nacos Discovery, MySQL 8.4 manual SQL, Docker Compose, Nginx 1.30.4, Python 3 `unittest` and HTTP clients, Bash.

**Spec:** `docs/superpowers/specs/2026-09-06-risk-operations-rule-configuration-console-integration-design.md`

## Global Constraints

- Do not change Risk API models, Controllers, AST/domain logic, state machine, static checks, schema or initialization data.
- Risk registers as `risk-service` only on the private backend network; it has no host port, Gateway Target or Nginx route.
- Add Nacos Discovery but continue banning Nacos Config, OpenFeign, Dubbo, Redis, MQ, Scheduler and Testcontainers in Risk.
- Disable service discovery and auto-registration in every Risk `auto` test; `./mvnw clean verify` must not contact Nacos.
- Reuse existing database/user `mor_risk/risk_app` and existing secret variable `MOR_RISK_DB_PASSWORD`; do not create a new infrastructure service or credential.
- Assign Risk container IP `172.30.73.25`, port `18104`, Snowflake region `0`, worker `25`.
- BFF depends on Risk process readiness in Compose. That readiness endpoint does not prove the 71-table/catalog precondition; the separate read-only `--verify-risk-catalog` gate is authoritative. Gateway and Nginx never depend on or address Risk directly.
- Keep `client_max_body_size 128k`, streaming request forwarding and Nginx read timeout 15 seconds; Gateway remains the 65,536/16,384/zero-byte policy authority.
- Canonical Platform DML belongs to Platform. Existing-environment DML in this plan inserts all nine routes and six resources as `DISABLED`, active permissions/grants, exact children, and never re-enables a manually changed row on replay.
- The incremental SQL is a manually invoked artifact exposed only through `initialize.py --apply-risk-operations-sql`. Compose, Java startup, tests, `deploy.sh` and ordinary smoke must not execute it.
- Full Risk Schema/DML may be applied only through an explicit `initialize.py --apply-risk-sql` action after proving the target local database is empty; partial databases fail closed.
- Live smoke uses unique synthetic Rule Set codes and preserves immutable Risk facts. It never prints credentials, tokens, rule bodies, full hashes or idempotency keys.
- The exact transaction-A/transaction-B recovery window remains covered by Risk’s existing real HTTP/MySQL test and BFF’s simulator test. Do not add a timing race or production failpoint to force it in deployment smoke.
- Running Maven/static tests and `docker compose config --quiet` is allowed. Applying either SQL file, building/replacing images, starting/restarting containers, enabling routes/resources or executing write smoke requires explicit authorization in the active turn.
- Root `test-environment/**`, `deploy.sh`, Vault assets and `CURRENT_IMPLEMENTATION.md` already contain unrelated changes. Read and merge the live files; never restore them from HEAD or stage unrelated hunks.
- The Risk API model file has an unrelated import-only worktree change. Do not edit or stage it in the discovery commit.

---

### Task 1: Add Risk Nacos Registration Without Test-Time External I/O

**Files:**
- Modify: `risk-compliance-service/pom.xml`
- Modify: `risk-compliance-service/risk-web/pom.xml`
- Modify: `risk-compliance-service/risk-web/src/main/resources/application.yml`
- Modify: `risk-compliance-service/risk-test/src/test/resources/application-auto.yml`
- Modify: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/RiskModuleBoundaryAutoTest.java`
- Modify: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/service/DecisionConfigurationWebOrchestrationAutoTest.java`
- Modify: `risk-compliance-service/README.md`

**Interfaces:**
- Consumes: Nacos server address/namespace/IP environment variables supplied by deployment.
- Produces: one `risk-service` registration and a test profile that cannot register or query discovery.

- [ ] **Step 1: Add boundary-test RED assertions**

Require `risk-web` runtime dependencies to contain `spring-cloud-starter-alibaba-nacos-discovery`; require the root Enforcer to ban `spring-cloud-starter-alibaba-nacos-config` but not the discovery starter or its Nacos client runtime. Update `test_runtimeHasOnlyApprovedInfrastructure` so `com/alibaba/nacos/api/NacosFactory.class` and `com/alibaba/cloud/nacos/NacosDiscoveryProperties.class` are required, while RabbitMQ, Redisson, Mockito and Testcontainers remain absent. Do not try to ban a Nacos Config API class that is packaged inside the shared client; instead, keep the direct-dependency ban and source/config assertions for no Config starter/import. Require application config to define discovery server/namespace and auto config to disable generic discovery, Nacos discovery, Nacos registration and service-registry auto-registration. Continue asserting no `bootstrap.yml` or `spring.config.import`. In `DecisionConfigurationWebOrchestrationAutoTest`, assert from the real Spring Environment that `auto` is active and all four discovery/registration switches are false; this outlier creates `SpringApplicationBuilder(RiskApplication.class)` directly and must not silently use production discovery defaults.

- [ ] **Step 2: Run RED**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/risk-compliance-service
./mvnw -pl risk-test -am \
  -Dtest=RiskModuleBoundaryAutoTest,DecisionConfigurationWebOrchestrationAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because discovery is currently banned and absent.

- [ ] **Step 3: Narrow the root dependency ban**

Replace the broad `com.alibaba.cloud:*` and `com.alibaba.nacos:*` banned patterns with the single prohibited dependency:

```xml
<exclude>com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-config</exclude>
```

Keep all other existing banned dependency patterns unchanged.

- [ ] **Step 4: Add discovery to Risk Web**

Add without a local version:

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>
```

Do not add LoadBalancer because Risk does not call another service in this slice.

- [ ] **Step 5: Add runtime and auto-test configuration**

Under `spring.cloud.nacos.discovery` add:

```yaml
server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
namespace: ${NACOS_NAMESPACE:mor-local}
ip: ${SPRING_CLOUD_NACOS_DISCOVERY_IP:${SERVER_ADDRESS:127.0.0.1}}
```

In `application-auto.yml`, keep existing disables and add:

```yaml
spring:
  cloud:
    service-registry:
      auto-registration:
        enabled: false
    nacos:
      discovery:
        register-enabled: false
```

Make `DecisionConfigurationWebOrchestrationAutoTest.realWebContext` start the real application with the `auto` profile explicitly. All other real Spring auto tests already inherit an `@ActiveProfiles("auto")` base; this direct builder is the only known outlier. Do not copy discovery-disable properties into Java or contact Nacos from a test.

- [ ] **Step 6: Run Risk full GREEN**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/risk-compliance-service
set -euo pipefail
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
./mvnw clean verify
git diff --check
```

Expected: all Risk tests, including existing real loopback MySQL tests when their repository-owned database is prepared, pass without contacting Nacos. If MySQL is unavailable, record the existing fail-fast setup message and run the DB-free/module gate separately.

- [ ] **Step 7: Document the private-discovery boundary**

Update Risk README to state that the app can register privately as `risk-service`, tests disable registration, and no public exposure or configuration-center dependency was added.

- [ ] **Step 8: Stage only the Risk discovery change**

The current dirty API model is unrelated. Stage exactly these seven files and no other Risk path:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/risk-compliance-service
git add -- \
  pom.xml \
  risk-web/pom.xml \
  risk-web/src/main/resources/application.yml \
  risk-test/src/test/resources/application-auto.yml \
  risk-test/src/test/java/com/paytech/risk/auto/RiskModuleBoundaryAutoTest.java \
  risk-test/src/test/java/com/paytech/risk/auto/service/DecisionConfigurationWebOrchestrationAutoTest.java \
  README.md
```

- [ ] **Step 9: Verify the staged Risk boundary**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/risk-compliance-service
set -euo pipefail
git diff --cached --check
git diff --cached --name-only
git diff --cached
```

Require the exact seven paths from Step 8 and no `risk-api` path. Reject any unrelated hunk before continuing.

- [ ] **Step 10: Commit only if authorization is active**

After showing the exact staged diff and confirming commit authorization is active, run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/risk-compliance-service
git commit -m "feat(risk): register private service discovery"
```

### Task 2: Raise the Nginx Envelope With Static Boundary Proof

**Files:**
- Modify: `test-environment/nginx/nginx.conf`
- Modify: `test-environment/scripts/test_nginx_gateway_problem_boundary.py`

**Interfaces:**
- Consumes: Gateway maximum configured route body 65,536 and response timeout 10 seconds.
- Produces: an outer edge envelope 131,072 bytes and read timeout 15 seconds that lets the Gateway own normal 413 responses.

- [ ] **Step 1: Change the static test first**

Require parsed Nginx values:

```python
self.assertEqual(128 * 1024, client_max_body_size_bytes)
self.assertGreater(client_max_body_size_bytes, 65_537)
self.assertEqual(15, proxy_read_timeout_seconds)
self.assertGreater(proxy_read_timeout_seconds, 10)
self.assertIn("proxy_request_buffering off;", nginx_configuration)
```

- [ ] **Step 2: Run RED**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/test_nginx_gateway_problem_boundary.py
```

Expected: FAIL because Nginx currently declares 32 KiB.

- [ ] **Step 3: Change only the envelope**

Use:

```nginx
client_max_body_size 128k;
```

Leave both `proxy_read_timeout 15s` and both `proxy_request_buffering off` directives unchanged.

- [ ] **Step 4: Run the non-mutating GREEN test**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/test_nginx_gateway_problem_boundary.py
```

Expected: the exact 128 KiB/15-second/streaming static contract passes without starting a container.

- [ ] **Step 5: Defer executable Nginx syntax validation to the authorization gate**

Do not run `docker run`, pull an image or restart Nginx in this track’s automatic GREEN step. Task 7 runs the pinned-image `nginx -t` check immediately before deployment only after explicit container-state authorization.

### Task 3: Add a Backend-Only Risk Container and Build/Deploy Contracts

**Files:**
- Modify: `test-environment/compose.applications.yaml`
- Modify: `test-environment/scripts/build.sh`
- Modify: `test-environment/deploy.sh`
- Modify: `test-environment/scripts/test_deploy_script.py`
- Create: `test-environment/scripts/risk_rule_configuration_stage.py`
- Create: `test-environment/scripts/test_smoke_stage_contract.py`
- Modify: `test-environment/README.md` (initial two-phase operator sequence only; Task 5 later owns the checkpoint paragraph and is the sole README writer during the reopened Task 3 fix)

**Interfaces:**
- Consumes: Risk executable JAR, existing MySQL/Nacos credentials and the exact legacy/prepared/staged Operations catalog states.
- Produces: image `mor-test/risk-service:development`, private service `risk-service`, two explicit ordered deployment phases, and a default public smoke that never mutates the Platform catalog or core business facts and remains strict and re-runnable at every legal rollout stage; it may authenticate and update ignored local access-evidence files.

- [ ] **Step 1: Extend deploy-script tests before implementation**

Require two closed deploy phases: `risk-bff-web` contains exactly `risk-service`, `operations-console-bff` and `operations-console-web` in that order; `platform-gateway-nginx` contains exactly `platform-support-service`, `nginx` and `api-gateway` in that order. Require `mor-test/risk-service:development` exactly once in the inspected image set. Reject an unknown phase. The fake Compose journal must prove that phase 1 first starts/waits for exactly `mysql redis rabbitmq nacos`, then force-recreates each phase-1 application with `--no-deps`; phase 2 force-recreates each phase-2 application with `--no-deps`, with Nginx overwriting the fixed Operations Channel before Gateway starts. Neither phase may let Compose dependency expansion mention the other phase. Phase 1 runs no public-chain smoke and proves only selected health checks; phase 2 runs the existing public smoke exactly once after all three selected services are healthy. That smoke must not mutate the Platform catalog or core business facts, while authentication/logout and the existing ignored local access-evidence write remain allowed. Require a persistent mode-`0600`, non-symlink, current-user `test-environment/private/risk-bff-web-phase.ready` checkpoint: an actual phase-1 run invalidates any old checkpoint before build/start, then atomically publishes the exact six local image IDs only after every phase-1 service is healthy; an actual phase-2 run rejects a missing, malformed, unsafe or image-stale checkpoint before replacing any service or running smoke. `--check-only` never creates/removes it. This durable gate supplements Task 7's stop-on-first-failure sequence. Preserve all assertions for independently added Vault services and files. Add a build-contract assertion that `risk-service.jar` is created/copied before the BFF build consumes `risk-api`. In `test_smoke_stage_contract.py`, write RED cases for exact `LEGACY`, `BASELINE`, each of the five read-prefix stages, each write-prefix stage, `ALL_ROUTES_ACTIVE` and `RESOURCES_EXPOSED`, plus partial resources, non-prefix routes, unknown/missing routes and wrong legacy resource sets.

- [ ] **Step 2: Run RED**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/test_deploy_script.py
python3 test-environment/scripts/test_smoke_stage_contract.py
```

Expected: FAIL because compose/build/deploy currently omit Risk and the closed stage classifier does not exist.

- [ ] **Step 3: Add the exact Compose service**

Merge this service into the current dirty Compose file:

```yaml
  risk-service:
    <<: *java
    image: mor-test/risk-service:development
    build:
      context: ./artifacts
      dockerfile: ../java/Dockerfile
      args: { JAR_FILE: risk-service.jar }
    environment:
      <<: *discovery
      SERVER_ADDRESS: 0.0.0.0
      SPRING_CLOUD_NACOS_DISCOVERY_IP: 172.30.73.25
      RISK_DB_URL: jdbc:mysql://mysql:3306/mor_risk?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true&useSSL=false
      RISK_DB_USERNAME: risk_app
      RISK_DB_PASSWORD: ${MOR_RISK_DB_PASSWORD:?prepare test secrets}
      RISK_SNOWFLAKE_REGION_ID: "0"
      RISK_SNOWFLAKE_WORKER_ID: "25"
    depends_on:
      mysql: { condition: service_healthy }
      nacos: { condition: service_healthy }
    healthcheck:
      test: [CMD, java, -cp, /app/health-probe, LocalHttpHealthProbe, http://127.0.0.1:18104/actuator/health/readiness]
      interval: 10s
      timeout: 5s
      retries: 18
      start_period: 40s
    networks:
      backend: { ipv4_address: 172.30.73.25 }
```

Add `risk-service: { condition: service_healthy }` to the BFF dependencies. Do not expose a port or attach Risk to `ingress`/`web`.

- [ ] **Step 4: Build/install Risk before BFF**

In `build.sh`, after Foundation and before Platform/BFF, add:

```bash
(cd "$workspace_dir/risk-compliance-service" && ./mvnw -pl risk-web -am -DskipTests clean install)
```

Copy:

```bash
cp "$workspace_dir/risk-compliance-service/risk-web/target/risk-web-1.0.0-SNAPSHOT-exec.jar" \
  "$environment_dir/artifacts/risk-service.jar"
```

Add `risk-service` to the Compose build list. This also installs `risk-api` before the BFF build.

- [ ] **Step 5: Update the explicit two-phase deployment contract**

Add `mor-test/risk-service:development` to `operations_images` in the current untracked/dirty `deploy.sh`, replace the single application-replacement array with the two exact ordered phase arrays from Step 1, and add required `--phase risk-bff-web|platform-gateway-nginx`. Phase 1 may build all reviewed images once, then starts/waits for exactly the four infrastructure services before force-recreating Risk, BFF and Web one at a time with explicit `--no-deps`; it ends after their Compose health checks and must not call `smoke.py`. Before any phase-1 build/start, safely invalidate the old exact checkpoint; after success, generate its exact sorted image-name/image-ID content in a mode-`0600` temporary, publish atomically in the same directory, and verify the final regular non-symlink file, ownership, mode and content. Phase 2 is normally invoked with `--skip-build`; before any replacement it must verify that checkpoint and current IDs for all six reviewed images, then force-recreate Platform, Nginx and Gateway one at a time with explicit `--no-deps`, wait for health checks, and only then run the existing public smoke exactly once against the current legal catalog state. Nginx must overwrite one fixed `X-Entry-Channel: OPERATIONS` on both API locations before the Channel-aware Gateway starts. That smoke does not mutate the Platform catalog or core business facts; its existing login/refresh/logout behavior and ignored `private/access.md` update are not catalog mutations. Do not use a single Compose `up` whose dependency closure can cross waves. A nonzero first-phase command prevents the Task 7 caller from reaching the second, and a standalone/stale phase-2 invocation fails before mutation. Update `deploy.sh --help` and, in the original Task 3 pass, the README with the exact two-command sequence and stop-on-failure rule. Task 5 later owns the README checkpoint paragraph; the reopened Task 3 checkpoint fix changes only `deploy.sh` and its tests and must not edit README. Do not invoke initialization or pass `--apply-risk-sql`. Treat `/actuator/health/readiness` only as process/lifecycle evidence; Task 7 must obtain `PREPARED` from the separate read-only catalog verifier before the first phase may start Risk.

Create the shared pure stage module, which exports `classify_route_stage(routeCodeToStatus)` and `classify_stage(activeResourceCodeToLabel, routeCodeToStatus)`. The Resource argument is explicitly the persisted ACTIVE Resource authority: a database caller must first verify all persisted rows, then project only ACTIVE Resources. Navigation absence can never prove a Resource is disabled because it is also filtered by role/permission grants. Freeze all 15 legacy active Resource codes/labels and all 27 legacy Route codes as exact sets. Freeze this one activation order, which intentionally differs from numeric Route-ID order: Authoring Context `1024` → Rule Set List `1025` → Rule Set Detail `1026` → Candidate List `1029` → Candidate Detail `1030` → Save `1027` → Freeze `1028` → Create `1031` → Validate `1032`.

The full classifier accepts only: legacy `15/27`; or the exact complete `15/36` catalog with a zero-to-nine prefix active in that order. Name the zero-prefix `BASELINE`; prefixes one through five `AUTHORING_READ_ACTIVE`, `RULE_SET_LIST_READ_ACTIVE`, `RULE_SET_DETAIL_READ_ACTIVE`, `CANDIDATE_LIST_READ_ACTIVE`, `READ_ACTIVE`; prefixes six through eight `SAVE_ACTIVE`, `FREEZE_ACTIVE`, `CREATE_ACTIVE`; and prefix nine `ALL_ROUTES_ACTIVE`. `RESOURCES_EXPOSED` is the exact `21/36` active Resource projection with the same nine-route active prefix. All 27 legacy routes remain ACTIVE in every state. These read-prefix states are durable and externally observable, not an in-process pseudo-transaction. Any partial Resource set, non-prefix Route state, unknown row or changed legacy catalog fails. Do not wire `smoke.py` to this module yet: the authoritative persisted reader is implemented in Task 5, where the executable wiring can be tested without substituting Navigation as authority.

- [ ] **Step 6: Run static GREEN only**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
set -euo pipefail
python3 test-environment/scripts/test_deploy_script.py
python3 test-environment/scripts/test_smoke_stage_contract.py
for risk_script in \
  test-environment/scripts/build.sh \
  test-environment/deploy.sh \
  test-environment/compose.sh; do
  bash -n "$risk_script"
done
./test-environment/compose.sh config --quiet
```

Expected: all contract/syntax/config checks pass without building images or changing containers.

### Task 4: Add Explicit Empty-Database Risk Initialization

**Files:**
- Modify: `test-environment/scripts/initialize.py`
- Create: `test-environment/scripts/test_risk_database_initialization.py`
- Modify: `test-environment/README.md`

**Interfaces:**
- Consumes: Owner SQL `risk-compliance-service/docs/database/sql/risk-compliance-schema.sql` and `risk-compliance-initial-data.sql`.
- Produces: a read-only `--verify-risk-catalog` operation and an opt-in `--apply-risk-sql` operation that works only against an empty repository-owned `mor-test/mor_risk`.

- [ ] **Step 1: Write fail-closed initializer tests**

With a fake MySQL command runner, first import the module under patched `os.umask`, `subprocess.run` and `urllib.request.urlopen` sentinels and require zero import-time calls. Drive a new injectable `main(argv=None)` in memory; never launch the current pre-refactor CLI from a RED test, because its still-combinable Platform/Nacos flags can reach real local services. Verify: no flag is rejected and causes zero actions; each single flag dispatches exactly one fake action; every flag pair is rejected before file reads, MySQL, Nacos or writes. `--verify-risk-catalog` performs only read queries and returns the explicit state `EMPTY` for zero schema objects or `PREPARED` only for the complete expected catalog; any partial/drifted state fails. Every Risk information-schema query, Schema batch and DML batch must pass the explicit helper argument `database="mor_risk"`; a call that relies on the existing `mor_platform` default is RED. `--apply-risk-sql` accepts only `EMPTY`, runs Schema then DML in order, stops before DML when Schema fails, then requires `PREPARED`. Query every `information_schema.tables` row as exact `table_name + table_type`: only zero objects is `EMPTY`; prepared means exactly the 71 Owner names and every row is `BASE TABLE`, so an extra View is drift. Parse the Owner DML column lists and compare in Python every stored column of its sole Decision Point row and three Action Version rows byte-for-byte, including IDs, names, domain/category, versions, Handler codes/versions/hashes, terminal/shadow flags, decision/merge values, status and both timestamps; reject missing, extra, case-only or other drift. Command output never includes passwords or SQL bodies.

- [ ] **Step 2: Run RED**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/test_risk_database_initialization.py
```

Expected: FAIL because the flag and function do not exist.

- [ ] **Step 3: Implement the explicit apply and verify options**

Move CLI construction/dispatch into import-safe `main(argv=None)` and replace the current independent booleans with one `parser.add_mutually_exclusive_group(required=True)` containing existing `--apply-platform-sql`, existing `--nacos`, new `--apply-risk-sql` and new `--verify-risk-catalog`; one process may select exactly one action. Remove the module-scope `os.umask`; apply a restrictive umask only inside an explicit file-write scope and restore it afterward. Keep the implementation compatible with repository Python 3.9. Resolve both Risk SQL files from workspace root and fail closed for missing paths, directories, symlinks or a file whose device/inode/size/mtime changes while read. Parse the exact expected table names from the Owner Schema plus the complete Point/Action tuples from the Owner DML, and prove the parser accounts for every executable statement rather than silently ignoring unrecognized SQL. The read-only verifier explicitly calls the helper with `database="mor_risk"` for its `information_schema` and catalog queries, reads all schema object names/types and all Point/Action rows, then compares exact Python strings/tuples so database collation cannot hide case drift: zero objects returns `EMPTY`; otherwise the exact 71-base-table set and every parsed Point/Action column are required for `PREPARED`. Apply first requires `EMPTY`, executes both `mysql(schema, database="mor_risk")` and `mysql(dml, database="mor_risk")` through batch stdin without `--force`, then requires `PREPARED`. The Owner SQL contains no `USE`, so no Risk path may depend on the helper's Platform default. Extend `count` with an explicit database parameter or avoid it for Risk. Do not call either function unless its flag is present.

- [ ] **Step 4: Prove no automatic caller exists**

Add a source assertion that `compose*.yaml`, `build.sh`, `deploy.sh` and application startup files contain neither `--apply-risk-sql` nor the mutating initializer function name. Update the existing README first-time preparation example from the now-invalid combined invocation to two explicit commands in order:

```bash
python3 test-environment/scripts/initialize.py --apply-platform-sql
python3 test-environment/scripts/initialize.py --nacos
```

Add a documentation test that rejects any command line containing two known initialization actions. The README may document each flag separately; no automated caller may invoke an apply flag. Narrow the historical warning “do not rerun `initialize.py`” to the old Platform/Nacos first-time mutation actions so the new read-only verification command is not contradicted.

- [ ] **Step 5: Run GREEN**

Run the Step 2 test and the full Python discovery command:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 -m unittest discover -s test-environment/scripts -p 'test_*.py'
```

Expected: all test-environment unit/static tests pass.

### Task 5: Create the Disabled Platform Incremental SQL and Static Oracle

**Files:**
- Create: `test-environment/sql/2026-09-07-risk-operations-rule-configuration.sql`
- Create: `test-environment/sql/2026-09-07-risk-operations-rule-configuration-resources-enable.sql`
- Create: `test-environment/sql/2026-09-07-risk-operations-rule-configuration-resources-disable.sql`
- Create: `test-environment/scripts/test_risk_rule_configuration_catalog.py`
- Modify: `test-environment/scripts/initialize.py`
- Modify: `test-environment/scripts/test_risk_database_initialization.py`
- Modify: `test-environment/scripts/smoke.py`
- Modify: `test-environment/scripts/gateway_control_plane_smoke.py`
- Modify: `test-environment/scripts/test_smoke_stage_contract.py`
- Modify: `test-environment/README.md`

**Interfaces:**
- Consumes: canonical IDs/content from the Platform plan and existing target/Admin/Viewer identities.
- Produces: replay-safe manually applied DML for an existing Platform database, with routes/resources initially disabled, plus one explicit CLI action that cannot run from normal deploy/startup paths.

- [ ] **Step 1: Write the static SQL oracle first**

Parse the SQL and require exact ranges/counts: permissions `536..540`, role grants `581..586`, resources `565..570`, routes `1024..1032`, headers `5100..5133`, queries `4031..4039`, guards `6110..6163`. Compare every column of the five Permission and six Role-Permission rows with the Platform canonical data. Compare every Resource field, including root/page/button parents, READ/write permissions, sort order, page-only route/component key, explicit nulls and initial `DISABLED` status. Compare the complete Route/Header/Query/Guard matrix with Platform canonical rows except that all nine routes insert as `DISABLED`. Also write failing artifact/CLI contract cases for the not-yet-created Resource disable and enable SQL: exact six IDs, closed all-disabled/all-active states, version increments, Route preconditions and mutually exclusive explicit flags. Freeze a repository-private Python `fcntl.flock` path `test-environment/private/risk-operations-rollout.lock`; the production lock file is a persistent regular file with mode `0600`, acquisition is non-blocking/fail-closed, release never unlinks it, and tests inject a temporary path rather than touching `private`. Every Platform catalog/resource mutation—including existing `--apply-platform-sql`—and every Route mutation must hold the same file descriptor continuously from authoritative pre-read through SQL and postcondition. This includes the pre-existing legacy Route PATCH/restore flow in `gateway_control_plane_smoke.py`; add a fake-lock/journal test proving it cannot issue a PATCH before acquiring the shared lock and holds it through restoration/post-read.

- [ ] **Step 2: Add safety assertions**

Require `SET NAMES utf8mb4`, `USE mor_platform`, one `START TRANSACTION`/`COMMIT`, target/Admin/Viewer `FOR UPDATE`, no `--force`, no schema DDL, no startup hook, and exact-content `NOT EXISTS` clauses. Lock and verify the exact ACTIVE identity of Target `900000000000000001`, Administrator `900000000000000510` and Viewer `900000000000000511` by both ID and business key; zero rows may not pass silently. MySQL equality must be binary exact: SQL predicates use binary/`HEX()` comparisons, and Python readers request tagged typed sections plus `HEX()` text so case, accents, trailing spaces and `NULL` cannot be normalized away. Define replay masks per table: omit mutable status, version and timestamps for every family; Route additionally omits mutable `updated_by` but retains immutable `created_by`. First insertion still requires Permission/Grant `ACTIVE/v1` and Resource/Route `DISABLED/v1`; persisted readers validate table-specific legal states and positive versions: Permission exactly `ACTIVE|INACTIVE`, Role-Permission exactly `ACTIVE|REVOKED`, Resource/Route exactly `ACTIVE|DISABLED`, while the required Target/Admin/Viewer rows remain `ACTIVE`. Add both-direction mutations proving legal inactive/revoked values pass and illegal cross-table `DISABLED` values fail. Any immutable identity/content drift must produce a duplicate-key failure and rollback rather than a false no-op.

- [ ] **Step 3: Run RED**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/test_risk_rule_configuration_catalog.py
```

Expected: FAIL because the incremental SQL does not exist.

- [ ] **Step 4: Implement active permissions/grants**

Lock target `900000000000000001`, roles `900000000000000510/511`, then insert the five `ACTIVE` permission definitions and six `ACTIVE` grants. Increment a role’s version only when `ROW_COUNT()` proves at least one new grant for that role.

- [ ] **Step 5: Implement disabled resources/routes and exact children**

Insert resources `565..570` and routes `1024..1032` as `DISABLED`, version 1, with immutable content equal to canonical data. Insert 34 headers, 9 queries and 54 guards. For every `INSERT ... SELECT ... WHERE NOT EXISTS`, compare both ID and all immutable business columns; omit mutable status/version/timestamps from replay equivalence.

- [ ] **Step 6: Add the read-only stage verifier and explicit incremental-apply CLI gate**

Add read-only `--verify-risk-operations-stage` and mutating `--apply-risk-operations-sql` to the same single required mutually exclusive action group created in Task 4; later Steps 7–8 add both Resource actions so the final required group has exactly eight actions. Test all eight valid single-action dispatches, no-action rejection, and every one of the 28 two-action combinations rejected before file reads, locks, MySQL, Nacos, network or writes. Implement the underlying read as an import-safe `read_risk_operations_stage()` function that returns the verified persisted Resource/Route maps plus stage; importing `initialize.py` or `smoke.py` must execute no CLI action or alter process umask. Read every relevant table in one read-only consistent-snapshot MySQL transaction/session, emit unambiguous typed result sections, and decode text from `HEX()` in Python for exact byte comparison. The reader accepts either the exact immutable legacy `15-resource/27-route` catalog and returns `LEGACY`, or the exact complete new catalog and one of its closed stages. For the non-Risk Permission/Grant families, accept only one whole-catalog lineage: the current canonical initialization lineage, or the already-reviewed 2026-09-04 Operations action-permission migration lineage with Permission `521/525` fixed `INACTIVE`, Grant `531/539` fixed `REVOKED`, and canonical Grant IDs `570..574` atomically replaced by `7001..7005`; never accept a cross-lineage mix or an arbitrary extra historical row. Keep non-system-role Risk Grants visible in that same snapshot: reject them as orphans while the Risk catalog is absent or partial, but after the complete Risk catalog exists exclude only non-reserved-ID, non-system-role Grants from Owner-catalog equivalence so ordinary IAM assignments remain legal. The six system-role Risk business keys and reserved Grant IDs remain exact under every stage. The reader derives the ACTIVE Resource code/label projection plus every persisted Route status, then feeds those maps to shared `risk_rule_configuration_stage.classify_stage`; do not copy a second stage machine into the initializer. The verifier reports that closed stage and performs no update.

The apply action accepts exactly two states: (a) `LEGACY` only when all seven reserved row families are absent by both ID and business-unique key—Permission `536..540`, Grant `581..586`, Resource `565..570`, Route `1024..1032`, Header `5100..5133`, Query `4031..4039`, Guard `6110..6163`—including no orphan child rows and no same code/key under a different ID, in which case execute the reviewed incremental file through the UTF-8 batch `mysql` helper; or (b) the exact complete new catalog, in which case perform a read-only no-op verification. Tests must reject at least one orphan from each child family, same-code/wrong-ID rows, case-only drift, trailing-space drift and `NULL` drift. Reject every partial/drifted state. After either path, run the shared classifier and report its exact Operations rollout stage (`BASELINE` on a first application), not Risk catalog state `PREPARED`; require all new exact counts and content while preserving each current Route/Resource status. Hold the shared rollout lock on the same descriptor from pre-read through post-read. The first application must leave every new Route/Resource `DISABLED`. Add source assertions proving Compose, `build.sh`, `deploy.sh`, application startup and default initialization never select the mutating flag.

While updating the README, preserve Task 3's exact two-phase order and document its durable `private/risk-bff-web-phase.ready` rule: phase 1 invalidates stale evidence and publishes exact six-image IDs only after success; phase 2 rejects missing/unsafe/stale evidence before replacement. Name the set exactly as the six reviewed local images Nacos, Risk, Platform, Gateway, BFF and Web—not “six applications”; Nginx and Vault are excluded. Task 3 owns the deploy implementation/tests; Task 5 owns only the non-overlapping README hunk while it is the active README writer.

Now wire default `smoke.py` to the same import-safe persisted reader and classifier. Refactor `Browser` into an injected, Python-3.9-compatible HTTP adapter that honors one strict canonical loopback HTTP origin/port and exposes a raw-JSON-bytes request path; reject empty query/fragment delimiters, whitespace/control characters, userinfo, backslashes, paths and every non-canonical spelling before retaining the raw value as `Origin`, while preserving an explicit port `0`. That path must send the caller's exact bytes without `json.dumps` reconstruction while preserving the normal `/api/operations` prefix, authentication, Origin and error handling. Keep the ordinary object request API for existing callers. Fake-transport tests journal the final path, exact body bytes and `Content-Length`, including a one-byte trailing-space difference, without opening a socket. The default smoke strictly pages the complete HTTP Route catalog to exhaustion, requires a stable declared total, rejects duplicate codes, and requires exactly 27 rows for `LEGACY` or 36 for every prepared Risk stage before comparing every HTTP status to the persisted map. Compare the repository test Platform Administrator's Navigation with the persisted ACTIVE Resource projection only as a secondary consistency check: exact 15 legacy Resources for `LEGACY` and all hidden-Resource stages, exact 21 only for `RESOURCES_EXPOSED`. Remove the old generic BUTTON-parent fallback; keep every existing field/parent/label assertion and validate exact Risk fields only in `RESOURCES_EXPOSED`. Print the one closed stage. The default smoke must not mutate the Platform catalog or core business facts, while its existing authentication/logout and ignored local access-evidence write remain allowed. This lets the authorized `deploy.sh` health/smoke gate pass against a strict `LEGACY` Platform catalog before Task 7 applies the disabled increment. Extend `test_smoke_stage_contract.py` with injected persisted reader, Browser, credential material and output path, then execute the real `main()` wiring entirely against fake database/HTTP/file boundaries. Cover both 27-row legacy and 36-row prepared states, incomplete/duplicate/unstable paging, `DB=21/Navigation=15` and the reverse mismatch, prove the reader is called, and prove no fake deploy substitute can bypass this wiring or access real secrets/network/`private/access.md`.

- [ ] **Step 7: Add the Resource-first rollback artifact**

Implement the disable SQL first. It locks and verifies the exact six Resources `565..570` field by field, accepts only the all-active or all-disabled state, and atomically changes all active rows to `DISABLED` with one version increment per row. It deliberately does not require a Route state so it remains the first rollback action. Split exact persisted-catalog decoding/immutable/domain validation from the closed combined-stage classifier: the disable CLI uses the former for its locked pre-read, may disable visible Resources over any exact Route status map without first calling the combined classifier, then uses the same raw post-read to prove all six disabled; when that Route map is a legal prefix, report the resulting hidden-Resource stage. Include the eight-active-prefix plus six-active-Resources counterexample as a causal test. Use one transaction, exact IDs/codes and immutable columns, UTC update time, exact row-count/postcondition checks, no deletes and no schema DDL. Add `--disable-risk-operations-resources` to the one global initialization action group; no other script invokes this flag. Hold the shared rollout-lock descriptor from the persisted pre-read through SQL completion and the persisted post-read. All-disabled replay is a zero-update verified no-op.

- [ ] **Step 8: Add the guarded Resource activation artifact**

Implement the enable SQL separately. It locks IDs `565..570` and all nine Risk Route rows, verifies every immutable Resource/Route column field by field, requires all nine Routes `ACTIVE`, and accepts only all-six-`DISABLED` (atomically update to `ACTIVE`, increment each version once) or all-six-`ACTIVE` (zero-update verified no-op). Use the same transaction, UTC, exact row-count, postcondition and no-DDL constraints as Step 7, including the same rollout-lock descriptor across persisted pre-read, SQL and post-read. Add `--enable-risk-operations-resources` to that same global action group. Unit tests require exact preconditions, idempotent no-op states, partial-state rejection, lock contention failure, persistent mode-`0600` lock behavior without unlink, every pair among all eight initialization actions rejected before side effects, and proof that Compose, build, deploy and startup never select either Resource flag. The Route rollout script may import only the read/lock helpers; it must never select a Resource mutation flag.

- [ ] **Step 9: Add post-commit read-only verification queries**

Return five permissions, six resources, nine routes with header/query/guard counts and both role grant sets. These SELECTs do not change status or enable anything.

- [ ] **Step 10: Run GREEN**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
set -euo pipefail
python3 test-environment/scripts/test_risk_rule_configuration_catalog.py
python3 test-environment/scripts/test_risk_database_initialization.py
python3 test-environment/scripts/test_smoke_stage_contract.py
python3 -m unittest discover -s test-environment/scripts -p 'test_*.py'
git diff --check
```

Expected: exact catalog and replay-safety assertions pass. Do not execute the SQL against MySQL in this task.

### Task 6: Add a Synthetic Full-Chain Smoke Without a Timing Race

**Files:**
- Modify: `test-environment/scripts/build.sh`
- Create: `test-environment/java/LocalSnapshotEvidenceFetch.java`
- Modify: `test-environment/scripts/test_java_image_health_probe.py`
- Modify: `test-environment/scripts/initialize.py`
- Modify: `test-environment/scripts/test_risk_rule_configuration_catalog.py`
- Modify: `test-environment/scripts/smoke.py`
- Modify: `test-environment/scripts/test_smoke_stage_contract.py`
- Create: `test-environment/scripts/risk_rule_configuration_smoke.py`
- Create: `test-environment/scripts/test_risk_rule_configuration_smoke.py`
- Create: `test-environment/scripts/risk_rule_configuration_rollout.py`
- Create: `test-environment/scripts/test_risk_rule_configuration_rollout.py`
- Create: `test-environment/scripts/risk_rule_configuration_recovery.py`
- Create: `test-environment/scripts/test_risk_rule_configuration_recovery.py`
- Create: `test-environment/scripts/gateway_snapshot_metrics.py`
- Create: `test-environment/scripts/test_gateway_snapshot_metrics.py`
- Create: `test-environment/scripts/gateway_snapshot_wait.py`
- Create: `test-environment/scripts/test_gateway_snapshot_wait.py`
- Create: `test-environment/scripts/test_risk_rule_configuration_authorization_inventory.py`
- Modify: `test-environment/README.md`

**Interfaces:**
- Consumes: an already running, explicitly prepared and route-enabled local stack, Task 5's import-safe persisted-stage reader/shared lock, Task 5's loopback-configurable `Browser` with exact raw-JSON-byte transport, and Task 3's two explicit deployment phases.
- Produces: safe synthetic evidence and explicit rollout/recovery operator adapters for the full public chain; it documents and consumes the existing deployment phases but does not modify or recreate them.

All Task 6 Python production and tests remain compatible with Python 3.9: do not use `X | Y` type unions, `match`, `tomllib`, `zip(strict=...)`, `datetime.UTC` or another 3.10+ API. Every import is side-effect free.

- [ ] **Step 1: Write request-generation and smoke-CLI RED tests**

In `test_risk_rule_configuration_smoke.py`, require unique Rule Set codes matching `^SMOKE_[0-9]{8}T[0-9]{6}Z_[0-9A-F]{12}$`, valid `CHECKOUT_PREPARE`/`ALLOW_CHECKOUT` v1 field/action values, explicit JSON `null` for nullable Rule Set fields, and a semantically valid compact ASCII-padded JSON body generator that can reach exactly 65,536 UTF-8 bytes while respecting 200 rules, 2,000 nodes, 10,000 operands and 512-character literal limits. Parse the 65,536-byte body back into JSON and assert its semantic limits and exact outbound bytes. Derive the 65,537 transport-only variant by appending one raw JSON-legal trailing byte `0x20` to those exact bytes—never reconstruct it from a dictionary—so the boundary differs only by size and would still parse if forwarded. Journal exact raw bytes and `Content-Length` through Task 5's Browser raw-JSON-byte path. Freeze these mutually exclusive smoke actions: `--prepare-authorization-fixture`, `--grant-authorization-fixture`, `--probe-runtime-stage EXPECTED_RUNTIME_STAGE [--expected-persisted-stage EXPECTED_PERSISTED_STAGE]`, `--read-stage`, `--route-smoke ROUTE_CODE`, `--full`, and `--cleanup-authorization-fixture`. The persisted-stage option is valid only with the probe and defaults to the runtime stage; it exists solely to prove an adjacent rejected candidate versus Gateway Last Valid. No action is the default. Capture stdout/stderr and assert no password, access token, idempotency key, literal body or complete hash is printed.

- [ ] **Step 2: Run request-generation and smoke-CLI RED**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/test_risk_rule_configuration_smoke.py
```

Expected: FAIL because the smoke module and exact-size generator do not exist.

- [ ] **Step 3: Write Snapshot parser RED tests**

In `test_gateway_snapshot_metrics.py`, unit-test a stdin evidence parser in two modes. Prometheus mode requires the exact exported names `gateway_snapshot_valid`, one-hot `gateway_snapshot_last_refresh_result{result=...}`, one-hot `gateway_snapshot_last_failure_reason{reason=...}`, `gateway_snapshot_last_valid_in_use` and `gateway_snapshot_routes{channel="OPERATIONS"}`; each selected family/series appears exactly once with a finite value, and duplicate, missing, extra one-hot and non-finite series fail. Public Snapshot mode strictly decodes `schemaVersion=1`, requires the whole Route list to be unique and in Platform's canonical binary `routeCode` order, then compares the active Risk code set—not wire list order—to the exact set represented by the rollout prefix. Include real lexicographic fixtures where activation order differs, plus duplicate, unknown and wire-order counterexamples. `last_valid_in_use=true` proves only that some prior valid Snapshot remains after a failed refresh; it never proves that Snapshot's identity, which remains the separate behavior probe's responsibility.

- [ ] **Step 4: Run Snapshot parser RED**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/test_gateway_snapshot_metrics.py
```

Expected: FAIL because the strict metrics/Snapshot parser does not exist.

- [ ] **Step 5: Write bounded Snapshot waiter RED tests**

In `test_gateway_snapshot_wait.py`, use injected fetchers and clocks to cover its two mutually exclusive CLI modes: successful exact stage, or invalid-refresh fallback to an exact prior runtime stage. Cover exact stage-to-active-set/count mapping, stale-to-current convergence across the Gateway's five-second refresh cadence, `FAILURE/INVALID_SNAPSHOT/last-valid=true`, malformed response, a 60-second hard deadline, a two-second maximum polling interval and no subprocess output leakage. Every fetch/subprocess receives a timeout no greater than the remaining monotonic deadline, so one hung JRE evidence fetch cannot overrun the total bound. In `test_java_image_health_probe.py`, require a separately compiled `LocalSnapshotEvidenceFetch` that accepts only the exact Gateway metrics and Platform Public Snapshot URLs, uses only JRE APIs, follows no redirect, accepts only 2xx, limits the response in the JDK HTTP body subscriber to 1 MiB, emits exact bytes only after success and stays silent on every failure. Exercise its injected transport for both exact URLs, exact-limit success, overflow, non-2xx, I/O, interruption, output failure and no-call URL rejection. Separately require a library-only recovery observer that accepts one adjacent `prior_stage + next_stage` pair and, under one shared monotonic deadline, returns either an identity-verified exact next-stage success or prior-stage invalid-fallback metric coordinates with `verified_stage=null` and `requires_behavior_probe=true`. Prove that neither branch can starve the other, fallback does not fetch the rejected public Snapshot, a stale next Snapshot keeps polling, non-adjacent pairs fail before fetching, and slow/oversized/deep evidence retains the same deadline, size and redaction guarantees. Do not add a third CLI mode or restore `curl`/a package-install step to the shared Java image.

- [ ] **Step 6: Run bounded Snapshot waiter RED**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/test_gateway_snapshot_wait.py
python3 test-environment/scripts/test_java_image_health_probe.py
```

Expected: FAIL because the bounded waiter does not exist.

- [ ] **Step 7: Write versioned Route rollout RED tests**

With a journaled fake HTTP transport and fake read-only database authority, require the CLI to accept only the nine exact Route codes and action forms `--inspect-stage`, `--activate-route CODE`, `--disable-route CODE` and `--reconcile-baseline`. `--inspect-stage` calls the import-safe persisted-stage reader, strictly pages the complete Operations Route list to obtain all 36 code/status pairs, separately GETs all nine Risk Route Details to verify their complete immutable matrix, and reads the repository test Platform Administrator's Operations Navigation projection only as a consistency check. Feed the persisted ACTIVE Resource projection and complete persisted Route map—not the authorization-filtered Navigation map—to shared `classify_stage`; require HTTP Route statuses to equal that database authority. It must never synthesize the 27 legacy statuses or feed only the nine Risk rows to that classifier. Every mutation holds the shared rollout lock, reruns that full authoritative observation, computes one allowed adjacent final stage, then calls the byte-exact `Browser.request_raw_response("PATCH", "/gateway-admin/v1/routes/{routeCode}/status", ...)`; the helper prepends `/api/operations`, retains the authoritative HTTP status and response headers, and the journal must assert the final wire path `/api/operations/gateway-admin/v1/routes/{routeCode}/status`. The body contains exactly `status` (`ACTIVE` or `DISABLED`), `expectedVersion` equal to the numeric version just observed under the lock, and reason `Risk 规则配置分阶段启用` or `Risk 规则配置分阶段回滚`, plus one fresh hidden idempotency key. Risk smoke likewise passes only `/decision-rule-sets/v1/**` or `/decision-releases/v1/**` suffixes to `Browser`; no caller supplies a second `/api/operations` prefix. Require a matching persisted/HTTP post-read before releasing the lock. An already-complete target stage is a read-only no-op; an individually matching Route inside an otherwise non-prefix or drifted catalog is not.

Freeze the only allowed external transitions as the exact adjacent chain `BASELINE -> AUTHORING_READ_ACTIVE -> RULE_SET_LIST_READ_ACTIVE -> RULE_SET_DETAIL_READ_ACTIVE -> CANDIDATE_LIST_READ_ACTIVE -> READ_ACTIVE -> SAVE_ACTIVE -> FREEZE_ACTIVE -> CREATE_ACTIVE -> ALL_ROUTES_ACTIVE`, plus the exact reverse edges. `--activate-route CODE` accepts only the next inactive member of the frozen nine-route order; `--disable-route CODE` accepts only its last active member. Thus every one-route commit, including the five read prefixes, is a legal durable stage that can be inspected and recovered after process death. Every Route disable requires the persisted authority to prove all six Risk Resources `DISABLED`; Navigation absence alone is never sufficient. `RESOURCES_EXPOSED` refuses all Route mutation with `RESOURCES_MUST_BE_DISABLED_FIRST`; the operator must invoke the separately authorized `initialize.py --disable-risk-operations-resources` action first. `--reconcile-baseline` accepts any legal persisted hidden-Resource prefix stage, repeatedly disables only its last active member, and never invokes a Resource SQL action itself. Unit tests cover every adjacent edge, all five read-prefix recovery points and reconciliation from each one; reject a non-prefix Route set, skipping a forward/reverse edge, incomplete pagination, legacy drift, database/HTTP disagreement, lock contention and any Route disable while persisted Resources are visible—even when the current actor's Navigation omits them.

After a PATCH timeout/disconnect, retain the exact serialized body bytes and key in memory and perform at most one explicit settlement replay with those identical bytes and key; never create a new key. A definitive replay response settles the command. If that replay is also unknown, stop immediately, do not mutate another route, and report only the route code plus `UNKNOWN_REQUIRES_RECONCILIATION`; a later `--inspect-stage` must settle to exactly the previous or next adjacent legal prefix before any action proceeds. On 409, accept the target only after the same complete persisted-stage plus paged HTTP catalog and exact Route-detail observation proves the exact target state; otherwise stop as concurrent drift. No route is guessed, skipped or rolled past.

Every observation GET—the four Route-list pages, nine Risk Route Details and Operations Navigation—uses one dedicated GET-only rate-limit recovery path. It may retry exactly once only after a strict RFC 9457 `429 GATEWAY_RATE_LIMITED` with one canonical delta-seconds `Retry-After` in the current gateway-admin bound `1..6`. Gateway evaluates ingress IP before authenticated-principal Guard, so that Header is the rejecting layer's minimum rather than evidence that the later layer has refilled; before the sole retry, wait `max(Retry-After, 6 seconds)`, where six seconds is the exact slowest current gateway-admin token-refill quantum. Concurrency limiting, another code/status, malformed/duplicate headers, an HTTP-date, zero, a leading zero, an out-of-range value or a second 429 fails closed without exposing headers, body, query or credentials. It never accepts a request body, idempotency key or caller-selected method, and it never handles PATCH/POST. Use one injected monotonic action deadline across pre-read, PATCH settlement and post-read—90 seconds for inspect, 180 seconds for one activate/disable and 720 seconds for full reconciliation. The deadline must admit the full selected wait. Pass the current remaining budget into every local persisted-authority `mysql` subprocess timeout, check the deadline again after that read, and set each HTTP connection timeout to at most the smaller of 20 seconds and the remaining deadline. Do not reset the deadline in a post-read or per Route. For mutations, any bounded wait remains inside the already-required shared rollout lock; expiry before PATCH performs no mutation, while expiry after a possibly committed PATCH stops for explicit reconciliation and never advances another Route. Tests must prove these boundaries with explicit exceptions that remain effective under Python optimization.

- [ ] **Step 8: Run Route rollout RED**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
PYTHONPATH=test-environment/scripts python3 -m unittest \
  test_risk_rule_configuration_catalog.PersistedCatalogTest.test_mysql_timeout_is_bounded_validated_and_redacted \
  test_risk_rule_configuration_catalog.PersistedCatalogTest.test_persisted_stage_reader_propagates_the_sql_timeout
python3 test-environment/scripts/test_smoke_stage_contract.py
python3 test-environment/scripts/test_risk_rule_configuration_rollout.py
```

Expected: FAIL because the Route rollout module does not exist.

- [ ] **Step 9: Write deterministic recovery RED tests**

Separately unit-test `risk_rule_configuration_recovery.py --base-url http://localhost:28080 --recover-route CODE --timeout-seconds 60`. It accepts only one of the nine exact Route codes and derives that Route's previous/next stages from the shared frozen transition table, so an operator never supplies or infers stage names. With injected rollout, Snapshot and behavior-probe adapters, cover: persisted previous stage as a read-only settled result; persisted next plus successful next runtime; persisted next plus exact `INVALID_SNAPSHOT` Last Valid prior runtime; unknown/malformed runtime; rollback PATCH unknown; and successful rollback followed by exact prior-stage Snapshot plus behavior evidence. It must never disable before an invalid-fallback behavior probe succeeds, never proceed from any non-adjacent stage, and never print secrets, bodies, keys or full hashes.

- [ ] **Step 10: Run deterministic recovery RED**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/test_risk_rule_configuration_recovery.py
```

Expected: FAIL because the adjacent-stage recovery coordinator does not exist.

- [ ] **Step 11: Implement the Route rollout CLI**

Freeze the nine-row method/path/permission/body/header/query/guard matrix inside `risk_rule_configuration_rollout.py`. Authenticate through Task 5's in-memory `Browser` helper, which must honor every validated loopback-only base URL/port and reject non-loopback hosts, strictly page the full Operations Route catalog, GET and strictly decode all nine current Risk Route Details plus the active Operations Navigation projection, import Task 5's persisted-stage reader/shared lock without executing the initializer CLI, and implement the four action forms exactly. Do not recreate that reader, lock or classifier. `--inspect-stage` and every mutation feed the persisted ACTIVE Resource projection and complete persisted 36-route map to shared `risk_rule_configuration_stage.classify_stage`, then require the HTTP route map and Admin Navigation projection to be consistent with it; `classify_route_stage` remains its pure Route-only subroutine for database composition, not the CLI’s final stage answer. Do not duplicate ordering. Enforce the exact prospective transition table from Step 7. `--reconcile-baseline` refuses `RESOURCES_EXPOSED`, then walks the frozen nine-route prefix backwards one adjacent route at a time under the shared lock, stopping on unknown result; it never invokes `initialize.py` as a command or any Resource DML. Refuse to activate any Route unless every earlier member is already active and every later member disabled. Refuse every non-loopback base URL. No action runs when no explicit CLI flag is supplied.

Implement the Step 7 GET-only bounded 429 recovery as part of that shared Browser/observer rather than weakening the complete observation. All list, detail and Navigation reads share the one action deadline; `_change_locked` and every reconciliation iteration reuse it. The current exact accepted `Retry-After` ceiling and layered-Guard refill floor are both six seconds, independent of the Gateway compiler's broader generic 60-second ceiling. The recovery coordinator's existing `--timeout-seconds 60` continues to bound each Snapshot observer/waiter call; it is not silently redefined as an overall coordinator deadline or as the RouteRollout mutation deadline.

- [ ] **Step 12: Run Route rollout GREEN**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
PYTHONPATH=test-environment/scripts python3 -m unittest \
  test_risk_rule_configuration_catalog.PersistedCatalogTest.test_mysql_timeout_is_bounded_validated_and_redacted \
  test_risk_rule_configuration_catalog.PersistedCatalogTest.test_persisted_stage_reader_propagates_the_sql_timeout
python3 test-environment/scripts/test_smoke_stage_contract.py
python3 test-environment/scripts/test_risk_rule_configuration_rollout.py
```

Expected: the exact forward/reverse stage machine, unknown-result settlement and Resource-first rollback constraints pass without network calls.

- [ ] **Step 13: Implement the Snapshot evidence parser**

Implement `gateway_snapshot_metrics.py` as a stdin-only verifier with mutually exclusive `--prometheus` and `--public-snapshot` modes. Prometheus mode accepts explicit expected refresh result, failure reason, last-valid flag and Operations route count and parses only the exact underscore-exported metric names from Step 3, with strict one-hot, uniqueness and finite-value checks. Public Snapshot mode accepts the comma-separated activation-prefix codes as an expected set, rejects unknown/missing/duplicate entries, and separately requires the complete wire Route list to use unique canonical binary `routeCode` order. Never compare Snapshot list position with activation order. It never starts Docker or performs HTTP.

- [ ] **Step 14: Run Snapshot parser GREEN**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/test_gateway_snapshot_metrics.py
```

Expected: strict valid/invalid Prometheus and whole-Public-Snapshot fixtures pass.

- [ ] **Step 15: Implement the bounded Snapshot waiter**

Implement `gateway_snapshot_wait.py` with exactly one of `--expected-stage STAGE` or `--expected-invalid-fallback-stage PRIOR_STAGE`, plus `--timeout-seconds 60`. It imports those pure decoders, maps the closed stage to its exact Risk active set/count, and uses fixed-argument `subprocess.run` calls to `compose.sh exec -T api-gateway java -cp /app/health-probe LocalSnapshotEvidenceFetch URL`. Compile that helper with JDK 25 into the existing locally copied `health-probe` directory before image builds. The helper and Python adapter each independently allow only Gateway metrics at `http://127.0.0.1:19080/actuator/prometheus` and Platform Snapshot at `http://platform-support-service:18106/platform-support/gateway-config/v1/public-snapshot`; the helper uses `HttpResponse.BodyHandlers.limiting(...ofByteArray(), 1_048_576)`, while Python captures bytes with stderr discarded, rechecks the same 1 MiB ceiling and decodes strict UTF-8. Success mode polls both endpoints and succeeds only when both match the exact active set plus `SUCCESS/NONE/last-valid=false/count`. Invalid-fallback mode polls Gateway metrics only and succeeds only on `FAILURE/INVALID_SNAPSHOT/last-valid=true` with the prior stage count; the separate behavior probe is the identity oracle because Platform's current candidate Snapshot may itself be the rejected one. Add a library-only `observe_recovery_transition(prior_stage, next_stage, ...)` that requires an adjacent Route-prefix pair and evaluates both of those exact outcomes in each polling loop under one deadline: it returns verified next identity only after matching the public Snapshot, or unverified prior fallback coordinates requiring the behavior probe without reading the rejected candidate Snapshot. Recovery must consume this observer instead of serially invoking the two single-mode waits. Poll no less frequently than every two seconds against a monotonic 60-second hard deadline. Pass every subprocess a timeout no greater than the remaining deadline; a hung fetch must not overrun that deadline. A malformed response, subprocess error or timeout fails with secrets/bodies omitted. Tests inject fetchers and clocks; production code contains no shell interpolation and the Java image gains no package-install or remote-copy step.

- [ ] **Step 16: Run bounded Snapshot waiter GREEN**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/test_gateway_snapshot_wait.py
python3 test-environment/scripts/test_java_image_health_probe.py
```

Expected: exact-stage and invalid-fallback polling fixtures pass without Docker or network calls.

- [ ] **Step 17: Implement request generation and the explicit smoke CLI skeleton**

Implement the exact Rule Set-code generator, 65,536/65,537-byte request builders, seven-way mutually exclusive argument parser and redacted failure/output boundary from Step 1. The builders return bytes and the later live path passes those bytes directly to Task 5's Browser raw-JSON method; neither boundary request may be reconstructed from a Python object. This step performs no HTTP, database, IAM, Compose or subprocess action when the module is imported or when no explicit action is selected.

- [ ] **Step 18: Run request-generation and smoke-CLI GREEN**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/test_risk_rule_configuration_smoke.py
```

Expected: the currently added request-generation, CLI and redaction cases pass.

- [ ] **Step 19: Write IAM fixture, read-route and runtime-probe RED tests**

`--prepare-authorization-fixture` creates or verifies two generation-scoped local identities. A random 12-uppercase-hex `fixtureGeneration` is persisted with the four independent random passwords in ignored `test-environment/private/risk-rule-configuration-smoke.json`; generation `ABCDEF123456`, for example, derives roles `RISK_RULE_SMOKE_AUTHORIZED_ABCDEF123456` and `RISK_RULE_SMOKE_DENIED_ABCDEF123456` plus accounts `risk-rule-authorized-abcdef123456@example.test` and `risk-rule-denied-abcdef123456@example.test`. This avoids collisions with Platform's soft-deleted Role and Account uniqueness history after a completed cleanup. The file is created exclusively with parent mode `0700` and file mode `0600`; an exact legacy four-password file is atomically upgraded only when no active legacy fixture Role exists, while an active legacy fixture remains resumable through its original names. It accepts every prepared closed 36-Route stage from `BASELINE` through `RESOURCES_EXPOSED`; the 27-Route `LEGACY` catalog is outside this action because Task 7 prepares the disabled increment before creating the fixture. Before `ALL_ROUTES_ACTIVE`, both roles must contain only the already-grantable legacy `OPERATIONS_SYSTEM_READ`. At `ALL_ROUTES_ACTIVE` or `RESOURCES_EXPOSED`, it accepts either that pre-grant state or the exact already-granted authorized set, never removes grants, and leaves the denied role legacy-only. The action never inserts Permission definitions or grants a Risk permission itself. Every later process reads the same private generation, exhaustively pages Roles to rediscover each role by its exact derived code, logs in with the derived fixture email, obtains the account ID from `/auth/v1/context`, then exhaustively pages Accounts only to verify that exact ID's role/member/version relationships. The public Account model's masked email is never treated as an exact-email lookup, no password may appear in output, and no in-memory or persisted ID manifest is allowed.

All administrator and synthetic-account logins within one fixture CLI action share one 90-second monotonic login deadline. The authoritative observation between administrator login and fixture preparation does not reset that deadline, nor does a later account login. A logical login may replay its identical POST body once, and only after a strict RFC 9457 `429 GATEWAY_RATE_LIMITED` with one canonical delta-seconds `Retry-After` no greater than the Gateway compiler's 60-second ceiling; wait `max(Retry-After, 6 seconds)` without resetting the action deadline. Distinct logins may each encounter that bounded wait, which permits the current five-login-per-minute Client-IP guard to refill while a partially created fixture resumes. HTTP 401 remains the normal absent-account discovery result and is never retried. `GATEWAY_CONCURRENCY_LIMITED`, any other 429/status, malformed or duplicate headers, a second 429 for the same logical login, deadline exhaustion or an unknown transport result fails closed. Tests must prove the real administrator-then-Prepare call order, the sixth-login case, one shared deadline, partial-fixture reuse without duplicate Role/Account creation, and no secret output.

For every active hidden-Resource stage, require `--route-smoke CODE`, `--read-stage` and runtime probes to use the existing repository test Platform Administrator as the authorized actor and the current generation's `RISK_RULE_SMOKE_DENIED_<fixtureGeneration>` role as the 403 actor. The `BASELINE` runtime probe needs no synthetic identity because it proves only that the first inactive route is rejected by Gateway before BFF. `--grant-authorization-fixture` is idempotent only at `ALL_ROUTES_ACTIVE` or `RESOURCES_EXPOSED`; it updates the authorized synthetic role to exact `OPERATIONS_SYSTEM_READ + permissions 536..540`, leaves the denied role legacy-only, re-logs both accounts and proves the new split. `--route-smoke CODE` accepts all nine codes and tests only that newly active route: Authoring Context and both list routes require authorized 200; Rule Set Detail and Candidate Detail first exhaustively page their respective lists, choose a positive decimal ID from `1..10` proved absent, and require Owner RFC 9457 `404 RISK_DECISION_CONFIGURATION_NOT_FOUND`, distinct from a deliberately unmatched nearby path’s Gateway `404 GATEWAY_ROUTE_NOT_FOUND`; every GET also requires the denied actor to receive PLT-I02 403. `--read-stage` reruns those five exact checks as one aggregate only after `READ_ACTIVE`; it never assumes an empty database and does not require detail 200.

Require `--probe-runtime-stage EXPECTED_RUNTIME_STAGE [--expected-persisted-stage EXPECTED_PERSISTED_STAGE]` to be read-only. It requires the database to equal the explicit persisted stage, or the runtime stage by default, and the repository test Platform Administrator's Navigation to match that persisted Resource state. A persisted/runtime mismatch is accepted only when the optional persisted argument was explicit, the two names are exact adjacent route-prefix stages, persisted is the next stage and all Resources remain disabled. The caller must separately run the invalid-fallback waiter first. Probe every active route through Nginx: use the preceding GET probes; Save sends the exact-record `{}` shape error and requires BFF `400 INVALID_REQUEST`; Freeze uses a proved-absent Rule Set ID plus a syntactically valid 64-hex expected hash; Create uses that absent ID/hash pair; Validate uses a proved-absent Release ID, `expectedVersion=1` and an exact deterministic key. The latter three require Owner `404 RISK_DECISION_CONFIGURATION_NOT_FOUND`. When a next inactive route exists, require Gateway `404 GATEWAY_ROUTE_NOT_FOUND`; `ALL_ROUTES_ACTIVE` and `RESOURCES_EXPOSED` instead require all nine active-route probes.

- [ ] **Step 20: Run IAM fixture, read-route and runtime-probe RED**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/test_risk_rule_configuration_smoke.py
```

Expected: FAIL on the newly added IAM/read/probe cases; the Step 18 request-generation cases remain GREEN.

- [ ] **Step 21: Implement IAM fixture, read-route and runtime-probe modes**

Implement the Step 19 contracts through the existing `Browser` helper, including its strict bounded login-rate adapter shared by administrator and fixture logins. Keep fixture creation, Risk-permission grant, behavior probing and cleanup as separate explicit actions. The implementation must not create Risk facts in `--probe-runtime-stage`, must not insert Permission definitions, and must not treat Navigation as persisted Resource authority.

- [ ] **Step 22: Run IAM fixture, read-route and runtime-probe GREEN**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/test_risk_rule_configuration_smoke.py
```

Expected: request-generation, fixture, read-route and runtime-probe cases pass without network calls.

- [ ] **Step 23: Implement the one-command adjacent-stage recovery coordinator**

Implement `risk_rule_configuration_recovery.py` only after the rollout observer/mutator, bounded Snapshot observation and smoke behavior probe have their own GREEN gates. Import those three adapters as libraries; do not shell out to the other Python CLIs. Its only mutation action is `--recover-route CODE`. It derives the exact prior/next pair from the shared transition table, observes persisted state, and follows this closed algorithm. When persisted state is next, use the Snapshot library's one-deadline dual-outcome recovery observer; never call success-wait and fallback-wait serially or treat fallback metrics as runtime identity:

1. If persisted state is the prior stage, wait for and behavior-probe that same stage, then report `ALREADY_AT_PRIOR_STAGE` without PATCH.
2. If persisted state is the next stage and Gateway proves the next successful runtime, disable exactly the supplied Route.
3. If persisted state is the next stage and Gateway proves `FAILURE/INVALID_SNAPSHOT/last-valid=true` at the prior route count, first behavior-probe prior runtime with explicit persisted-next expectation, then disable exactly the supplied Route.
4. Any other persisted/runtime combination stops without mutation.
5. After the disable, require the prior successful Snapshot and behavior probe. An unknown disable result stops for a later rerun; the next run settles from authoritative state.

The coordinator rejects visible Risk Resources, non-loopback URLs, invalid timeouts and all non-adjacent/drifted states. It relies on the rollout mutation's shared lock and authoritative re-read; it never invokes Resource DML or an explicit Gateway Last Valid rollback.

- [ ] **Step 24: Run deterministic recovery GREEN**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/test_risk_rule_configuration_recovery.py
```

Expected: all nine exact route-to-stage mappings and both live-runtime branches pass without network calls.

- [ ] **Step 25: Write write-route, authorization and size-boundary RED tests**

For write-route `--route-smoke CODE`, require a cumulative synthetic prerequisite flow so no persisted manifest is needed: Save creates and reads one new DRAFT; Freeze creates another DRAFT then freezes/reads it; Create Candidate creates+freezes another Rule Set then creates/reads its Candidate; Validate performs the entire chain and requires the actual response `200 + VALIDATING/version=2 + allStaticChecksPassed=true` with eight PASSED checks. Then GET that exact Candidate Detail and require `waitingForShadow=true`; the Validate response itself has no such field. Replay Validate with the same deterministic key and require the same result, then re-read Detail. Each invocation uses a unique Rule Set code and first asserts exactly the required active-route prefix. Keep the BFF protocol test's separate `200 + REJECTED` normal-response case.

For `--full`, use the prepared authorized synthetic operator/role to prove READ revocation immediately produces 403 through PLT-I02, never revoke the repository test Platform Administrator, and restore the permission afterward. Every role update uses a hidden idempotency key; after timeout/disconnect it permits at most one identical-byte/same-key settlement replay, then requires an exact role post-read before continuing. Cleanup is never trusted to restore authorization after an unknown result. Require active Resources, all six Navigation Resources and the complete visible-path flow. For an exactly-65,536-byte Save sent through the raw-byte Browser path, require HTTP 200, `DRAFT/versionNo=1`, positive Rule Set/Point Version IDs, a 64-lowercase-hex content hash and the exact Rule Set code; immediately GET that ID and require the same coordinates/hash plus submitted semantic rule content. Prove the unique code has total zero before and total one after. For a second valid 65,537-byte request with another proved-unused code, require RFC 9457 413/code `GATEWAY_PAYLOAD_TOO_LARGE`, a canonical Request ID and a follow-up exact-code total of zero. Require exactly one healthy Nacos `risk-service` at `172.30.73.25:18104` in namespace `mor-test`, group `DEFAULT_GROUP`. Require `--cleanup-authorization-fixture` to remove only the isolated fixture and to be idempotent.

- [ ] **Step 26: Run write-route, authorization and size-boundary RED**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/test_risk_rule_configuration_smoke.py
```

Expected: FAIL on the newly added write/full/cleanup cases while all earlier smoke tests remain GREEN.

- [ ] **Step 27: Implement write-route, authorization and size-boundary modes**

Implement the Step 25 flows with one unique Rule Set code per invocation and no persistent ID manifest. Preserve immutable Risk facts, keep all tokens, passwords, bodies, full hashes and idempotency keys out of output, and keep every write or IAM action behind its explicit CLI flag.

- [ ] **Step 28: Run the complete smoke module GREEN**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/test_risk_rule_configuration_smoke.py
```

Expected: all offline request, IAM, Route, runtime, authorization, size and redaction cases pass without network calls.

- [ ] **Step 29: Assemble deterministic layered recovery evidence**

Do not manufacture an unreliable transport race in deployment smoke or modify a component repository from this local-integration task. Use the completed BFF track's exact test method `RiskDecisionConfigurationBrowserBoundaryAutoTest#validateTimeoutIsNotRetriedAndDetailPreservesRecoveryActorFacts`, which proves one timed-out Validate call, zero automatic retry and the same strict original-actor state-change facts for differently authenticated callers. Web—not the stateless BFF—uses those facts to distinguish the original actor's recovery action from another actor's read-only view. Run all three deterministic component proofs independently:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/risk-compliance-service
./mvnw -pl risk-test -am \
  -Dtest=DecisionReleaseValidationRecoveryAutoTest#test_exactPostTransactionAStateRecoversWithOriginalCommand \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-bff
./mvnw -pl paytech-operations-console-bff-test -am \
  -Dtest=RiskDecisionConfigurationBrowserBoundaryAutoTest#validateTimeoutIsNotRetriedAndDetailPreservesRecoveryActorFacts \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
node --experimental-strip-types --test src/risk/testCandidateState.test.ts
```

The Risk method proves the exact `VALIDATING/version=2 + zero checks` database recovery window and original caller scope; the named BFF method proves timeout/no-retry plus faithful actor-fact transport; `testCandidateState.test.ts` proves current-actor recovery versus other-actor read-only classification. The opt-in live smoke's same-key replay proves that the deployed chain preserves the key and reaches a final result. Record all four named pieces together as the recovery gate.

- [ ] **Step 30: Document the opt-in operator actions**

Document the seven direct smoke action forms above, the four Route rollout actions and the one `--recover-route CODE` action as standalone explicit commands. Include the exact prerequisite stage, mutation scope, success evidence, failure stop condition and rerun/reconciliation instruction for each family. Do not add any smoke/rollout flag or automatic invocation to `deploy.sh`; deployment, catalog rollout, IAM fixture changes and synthetic fact creation remain separate operator actions. The default deploy smoke must not prepare IAM state or create immutable Risk facts. Unit tests prove cross-process credential/identity rediscovery, no Permission-definition insert, every action is opt-in and cleanup is never automatic after a failed/unknown route command. Add `test_risk_rule_configuration_authorization_inventory.py` as a source-only contract gate: enumerate every Task 7 write-capable command/action family and map it to the exact authorized mutation category; inspect the operator/deploy/initializer/smoke/rollout/recovery contracts and require the exact persistent `test-environment/private/risk-operations-rollout.lock`, transient mode-`0700` directory `test-environment/private/deploy.lock`, persistent mode-`0600` checkpoint `test-environment/private/risk-bff-web-phase.ready`, mode-`0600` `test-environment/private/access.md` and mode-`0600` `test-environment/private/risk-rule-configuration-smoke.json`. Recursively inspect `build.sh`, `deploy.sh`, `compose.sh`, both Compose manifests, every referenced Dockerfile, the requirements-site package scripts/font import and all invoked Python adapters; require exact coverage of package/repository/cache/font/registry/image/build-cache, container/network/volume, Session/audit/Guard, SQL, credential and file effects, reject every `RUN`/`ADD` in the shared Java image plus every online package-manager or remote-`ADD` step in a referenced Dockerfile, and prove Task 7 Step 1 explicitly requires and directly executes this inventory file before discovery. The test itself must use only repository text and temporary paths and must not open network, Docker, SQL or real private files.

- [ ] **Step 31: Run the complete offline GREEN gate**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
set -euo pipefail
python3 test-environment/scripts/test_risk_rule_configuration_smoke.py
python3 test-environment/scripts/test_risk_rule_configuration_rollout.py
python3 test-environment/scripts/test_risk_rule_configuration_recovery.py
python3 test-environment/scripts/test_gateway_snapshot_metrics.py
python3 test-environment/scripts/test_gateway_snapshot_wait.py
python3 test-environment/scripts/test_java_image_health_probe.py
PYTHONPATH=test-environment/scripts python3 -m unittest \
  test_risk_rule_configuration_catalog.PersistedCatalogTest.test_mysql_timeout_is_bounded_validated_and_redacted \
  test_risk_rule_configuration_catalog.PersistedCatalogTest.test_persisted_stage_reader_propagates_the_sql_timeout
PYTHONPATH=test-environment/scripts python3 -O -m unittest \
  test_smoke_stage_contract.SmokeWiringTest.test_rate_limited_get_honors_one_strict_bounded_retry_after \
  test_smoke_stage_contract.SmokeWiringTest.test_rate_limited_get_rejects_bad_retry_after_and_a_second_429 \
  test_smoke_stage_contract.SmokeWiringTest.test_rate_limited_get_extends_short_retry_after_for_layered_guard_refill \
  test_smoke_stage_contract.SmokeWiringTest.test_rate_limited_get_caps_each_connection_and_checks_deadline_after_exchange \
  test_smoke_stage_contract.SmokeWiringTest.test_rate_limited_get_has_one_monotonic_deadline_and_never_accepts_other_429 \
  test_smoke_stage_contract.SmokeWiringTest.test_real_main_rejects_both_navigation_authority_mismatches \
  test_smoke_stage_contract.SmokeWiringTest.test_real_main_rejects_incomplete_duplicate_unstable_and_wrong_status_routes \
  test_risk_rule_configuration_rollout.RolloutTest.test_deadline_exhaustion_before_patch_does_not_mutate_and_after_patch_stops \
  test_risk_rule_configuration_rollout.RolloutTest.test_deadline_before_settlement_replay_keeps_the_unknown_route_diagnostic \
  test_risk_rule_configuration_rollout.RolloutTest.test_reconcile_deadline_after_first_committed_patch_never_touches_second_route \
  test_risk_rule_configuration_rollout.RolloutTest.test_409_requires_the_strict_current_problem_contract \
  test_risk_rule_configuration_rollout.RolloutTest.test_problem_shaped_200_is_never_misclassified_as_a_409
python3 -m unittest discover -s test-environment/scripts -p 'test_*.py'
```

Expected: request construction, safety and orchestration tests pass without network calls.

### Task 7: Prepare the Authorized Local Rollout

**Files:**
- Inspect for the later umbrella-owned ledger write: `agent-docs/CURRENT_IMPLEMENTATION.md`
- Read the already-updated operator contract: `test-environment/README.md`

**Interfaces:**
- Consumes: the first four component tracks through their pre-staging source/static gates, Local-integration Tasks 1–6, prepared SQL and explicit user authorization; Platform's database-backed closeout remains downstream of this rollout.
- Produces: a backed-up, healthy local chain with a classified hidden-Resource rollout stage; never a production claim.

- [ ] **Step 1: Run the complete offline/source gate**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
set -euo pipefail
test -f test-environment/scripts/test_risk_rule_configuration_authorization_inventory.py
python3 test-environment/scripts/test_risk_rule_configuration_authorization_inventory.py
python3 -m unittest discover -s test-environment/scripts -p 'test_*.py'
for risk_script in \
  test-environment/scripts/build.sh \
  test-environment/deploy.sh \
  test-environment/compose.sh; do
  bash -n "$risk_script"
done
./test-environment/compose.sh config --quiet
node --test \
  requirements-site/tests/content-tabs.test.mjs \
  requirements-site/tests/domain-decisions.test.mjs \
  requirements-site/tests/global-use-case-sequences.test.mjs \
  requirements-site/tests/site-content.test.mjs
git diff --check
```

Expected: all static gates pass without SQL, live-service, container, network or private-evidence changes. Tests may create isolated temporary files. This pre-authorization gate deliberately runs the four source/content requirements suites directly and does not run the Vinext build, whose current remote-font cache miss can perform network I/O; the already-required full `npm --prefix requirements-site test` moves behind the exact authorization in Step 2b.

- [ ] **Step 2: Obtain one explicit authorization for the exact local mutations**

Present these targets verbatim: creation/verification of real non-symlink mode-`0700` directories `/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private` and `/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/backups`; backup `/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/backups/mor_platform-before-risk-operations-20260907.sql`; persistent mode-`0600` regular file `/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/risk-operations-rollout.lock`, transient mode-`0700` directory `/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/deploy.lock`, and persistent mode-`0600` regular-file phase checkpoint `/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/risk-bff-web-phase.ready`; required pre-existing `mor-test-mysql-data` volume plus the pinned temporary read-only/no-network initialized-volume inspection container `mysql:8.4.11@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb`, MySQL/Redis/RabbitMQ/Nacos health/start effects and Nacos image handling; local `mor-test/mor_risk` Owner SQL only if empty; local `mor-test/mor_platform` incremental/Resource-status DML; Maven `clean install`/`target` changes, configured Maven wrapper/plugin/dependency repository access and configured local Maven repository/cache writes; `npm ci`/`node_modules`, configured npm registry access and configured npm cache writes; the full requirements-site Vinext build, ignored `requirements-site/dist` and `.vinext/fonts` writes, and cache-miss reads from `fonts.googleapis.com` plus `fonts.gstatic.com`; Web `dist`, `test-environment/artifacts` recreation/JAR copies, Docker build-cache writes and rebuilding exactly `mor-test/nacos:3.1.2`, `mor-test/risk-service:development`, `mor-test/platform-support-service:development`, `mor-test/api-gateway:development`, `mor-test/operations-console-bff:development` and `mor-test/operations-console-web:development` once; only if absent locally, registry pulls of the three Dockerfile-pinned build bases `nacos/nacos-server:v3.1.2-slim@sha256:8e3a5eed20f3fa58f91d806bfad83b5742c73b13280a42c1c3e3c13e16e45f61`, `eclipse-temurin:25.0.4_7-jre-noble@sha256:b4c93a50fc67612798db73d68ca3b0ee4ebdd51736e59cca370e689b9797037e` and `nginx:1.30.4-alpine@sha256:dc5069ad14f19660b141b21236140b91656bf89bbc3e2417c70ae650cd66104c`; force-recreating Risk/BFF/Web in the first deployment phase and Platform/Nginx/Gateway only in the second; creation or reuse of exact `mor-test-backend`, `mor-test-ingress` and `mor-test-web` networks, exact `mor-test-redis-data` and `mor-test-rabbitmq-data` volumes, creation/start/reuse of Redis and RabbitMQ containers without recreation, configuration-driven replacement of Nacos, and the requirement that the already-verified MySQL container ID and `mor-test-mysql-data` mount never be replaced; the pinned temporary `nginx:1.30.4-alpine@sha256:dc5069ad14f19660b141b21236140b91656bf89bbc3e2417c70ae650cd66104c` syntax-check container; every rollout inspector/mutator login, Session, logout, security/audit and Guard effect; the default smoke's login/refresh/logout/audit/security effects and mode-`0600` ignored `/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/access.md` credential evidence; creation/verification of mode-`0600` `/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/risk-rule-configuration-smoke.json`, both synthetic Accounts/Roles/Tenant memberships/passwords/security/audit records, their permission updates and cleanup; adjacent Route/Resource status mutations; and uniquely named immutable synthetic Risk facts. Continue only after one explicit authorization covers that complete list. No new MySQL volume, unpinned image pull, remote Dockerfile `ADD`, Ubuntu APT/package-manager network access or first-volume initialization is authorized; Step 1's inventory must prove that the shared Java image has no `RUN`/`ADD` instruction and that every referenced Dockerfile has no online package-manager or remote-`ADD` step before this list may be presented as executable. The MySQL inspection image must already exist locally; the Nginx syntax check likewise uses `--pull never`, although its exact pinned image may have been obtained earlier as the explicitly authorized Web build base. Local Task 6 must already have added the offline inventory-contract test named in its file list; Step 1 requires and runs that exact file directly before discovery, and it must prove that every recursively invoked Task 7 command maps to this list and every lock/evidence path. If the exact backup target already exists, stop and obtain authorization for a newly specified absolute path before regenerating this command block; never improvise a filename while executing.

The Nginx syntax-check container runs directly with `--network none`, a read-only bind-mounted reviewed configuration and the service's user/read-only/tmpfs/capability/security/memory limits, without Compose service-network inheritance. This narrows the already authorized temporary-container effect and does not add a network or registry action.

This authorization also covers the Task 9 operator-facing browser acceptance, its synthetic IAM/Session/security/audit/Guard and immutable-fact effects, sanitized Request ID/screenshot evidence, and ignored Task 9 rollout evidence pack writes under `.superpowers/sdd/2026-09-07-risk-operations-local-integration/`. Its exact scope is Task 7 Steps 2a–13, Task 8 and Task 9 Steps 1–10. It does not authorize Task 9 Steps 11–16: Git index/object/ref changes and the `test-environment/private/risk-compose.XXXXXX.yaml` staged-Compose check remain behind separate later authorization, as does commit.

- [ ] **Step 2a: Run the read-only capacity gate after authorization**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
set -euo pipefail
df -h /
docker system df
available_kib="$(df -Pk / | awk 'NR == 2 { print $4 }')"
case "$available_kib" in
  ''|*[!0-9]*)
    echo "Unable to parse available KiB on /" >&2
    exit 1
    ;;
esac
(( available_kib >= 15728640 )) || {
  echo "At least 15728640 KiB (15 GiB) must be available on /; found $available_kib KiB" >&2
  exit 1
}
```

Require fresh `df -h /` and `docker system df` evidence. The `df -Pk /` parser accepts only a decimal KiB value and fails closed below 15 GiB. Do not run cleanup, prune, image/container/volume removal or cache removal; insufficient capacity stops this rollout before any requirements build, Docker, SQL or private mutation.

- [ ] **Step 2b: Run the full requirements-site gate after authorization**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
npm --prefix requirements-site test
```

Expected: the full build and all requirements suites pass. The build may reuse or populate its ignored remote-font cache and `dist` only under Step 2's explicit network/file authorization; failure stops before any Docker, SQL, private-evidence or service action.

- [ ] **Step 2c: Refresh canonicalization dependencies and prove frozen Risk vectors**

Use JDK 25.

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
set -euo pipefail
JAVA_HOME="$(/usr/libexec/java_home -v 25)"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
"$JAVA_HOME/bin/java" -version
(cd paytech-build && ./mvnw clean verify)
(cd paytech-build && ./mvnw clean install)
(cd paytech-foundation && ./mvnw -pl paytech-json-canonicalization -am clean verify)
(cd paytech-foundation && ./mvnw -pl paytech-json-canonicalization -am clean install)
node scripts/verify-java-foundation.mjs
(cd risk-compliance-service && ./mvnw -pl risk-test -am \
  -Dtest=RiskDecisionCanonicalizerAutoTest,DecisionReleaseStaticValidatorAutoTest,RiskModuleBoundaryAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test)
test "$(shasum -a 256 risk-compliance-service/risk-test/src/test/resources/fixtures/risk-decision-v1-golden-vectors.json | awk '{print $1}')" = \
  "9a5ad56d2edc300a1223afa1df22d88f0334f772a9ca2796500cfc22c30803e5"
node --test scripts/pdmaner/risk-decision-engine-contract.test.mjs
node --test scripts/pdmaner/risk-compliance-initial-data.test.mjs
```

Require both Build verify/install steps, targeted Foundation `paytech-json-canonicalization` verify/install and the Foundation structural/effective-Maven-model oracle before Risk runs. The focused Risk tests, frozen golden-vector SHA-256 and two independent JavaScript oracles must all pass. Do not run the full Foundation reactor in this rollout: its unrelated Redis integration tests require the repository-owned `mor-local` Redis and its compensation-event tests recreate tables in `paytech_foundation_test`, while neither Redis nor those SQL effects belongs to the Step 2c scope. The previously recorded full-Foundation verification remains implementation evidence only; any fresh full-reactor run requires a separately reviewed and explicitly authorized gate. Failure stops before Docker, `mor-test` SQL or repository-private mutation; this step performs no Git action.

- [ ] **Step 3: Start only the pre-existing repository-owned MySQL container**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
set -euo pipefail
mysql_probe_image='mysql:8.4.11@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb'
for reserved_variable in DOCKER_HOST DOCKER_CONTEXT DOCKER_CONFIG BUILDKIT_HOST; do
  test -z "${!reserved_variable:-}"
done
mysql_docker_context="$(docker context show)"
case "$(docker context inspect --format '{{.Endpoints.docker.Host}}' "$mysql_docker_context")" in
  unix://*) ;;
  *) exit 1 ;;
esac
test "$(./test-environment/compose.sh config --format json | python3 -c 'import json, sys; print(json.load(sys.stdin)["services"]["mysql"]["image"])')" = "$mysql_probe_image"
mysql_image_id="$(docker image inspect --format '{{.Id}}' "$mysql_probe_image")"
mysql_container_id="$(docker ps -aq --no-trunc --filter label=com.docker.compose.project=mor-test --filter label=com.docker.compose.service=mysql)"
case "$mysql_container_id" in
  ''|*$'\n'*) exit 1 ;;
esac
test "$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}|{{ index .Config.Labels "com.docker.compose.service" }}|{{ .Image }}' "$mysql_container_id")" = "mor-test|mysql|$mysql_image_id"
mysql_volume_identity="$(docker volume inspect --format '{{ .Driver }}|{{ .Scope }}|{{ index .Labels "com.docker.compose.project" }}|{{ index .Labels "com.docker.compose.volume" }}|{{ .Mountpoint }}' mor-test-mysql-data)"
case "$mysql_volume_identity" in
  local\|local\|mor-test\|mysql-data\|/*) ;;
  *) exit 1 ;;
esac
mysql_volume_mountpoint="${mysql_volume_identity#local|local|mor-test|mysql-data|}"
test "$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/mysql"}}{{.Type}}|{{.Name}}|{{.Source}}|{{.RW}}{{end}}{{end}}' "$mysql_container_id")" = "volume|mor-test-mysql-data|$mysql_volume_mountpoint|true"
docker run --rm --pull=never --network none --read-only \
  --mount "type=bind,src=$mysql_volume_mountpoint,dst=/var/lib/mysql,readonly" \
  --entrypoint /bin/sh "$mysql_probe_image" -ceu \
  'test -s /var/lib/mysql/auto.cnf && test -s /var/lib/mysql/mysql.ibd && test -s /var/lib/mysql/ibdata1 && test -d /var/lib/mysql/mysql'
test "$(docker volume inspect --format '{{ .Driver }}|{{ .Scope }}|{{ index .Labels "com.docker.compose.project" }}|{{ index .Labels "com.docker.compose.volume" }}|{{ .Mountpoint }}' mor-test-mysql-data)" = "$mysql_volume_identity"
test "$(docker inspect --format '{{ index .Config.Labels "com.docker.compose.project" }}|{{ index .Config.Labels "com.docker.compose.service" }}|{{ .Image }}' "$mysql_container_id")" = "mor-test|mysql|$mysql_image_id"
docker start "$mysql_container_id" >/dev/null
mysql_deadline=$((SECONDS + 180))
while :; do
  mysql_state="$(docker inspect --format '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' "$mysql_container_id")"
  case "$mysql_state" in
    running\|healthy) break ;;
    exited\|*|dead\|*|removing\|*|*\|unhealthy|*\|missing) exit 1 ;;
  esac
  (( SECONDS < mysql_deadline )) || exit 1
  sleep 2
done
```

Require exactly one pre-existing Compose-owned MySQL container already referencing the exact pre-existing local-driver/local-scope `mor-test-mysql-data` volume; that container reference is the volume lease, so ordinary Docker removal/recreation cannot race the probe. Verify the current context is a local Unix socket before the first raw Docker command, bind the Compose `mysql` service to the exact pinned already-local image, and verify the container's exact image ID, labels, writable named-volume mount and unchanged daemon mountpoint. The pinned image then inspects the daemon mountpoint through a read-only bind mount with no network and rejects an empty or obviously uninitialized MySQL 8 data directory; `--pull=never` forbids an image pull. Start only that previously verified container ID and wait at most 180 seconds for its existing health check. A missing container/image/volume, multiple MySQL containers, non-local volume driver/scope, label/image/mount drift or an empty/obviously uninitialized data directory stops; do not let Compose create a container or volume in this step. This filesystem probe does not claim that Code-MoR schemas are complete: Step 4 must still dump the existing `mor_platform`, and the later Risk/Platform catalog verifiers remain authoritative for project initialization and drift. Then require the `mor-test` MySQL service healthy. Do not start Risk or another application yet.

- [ ] **Step 4: Create and verify the exact authorized Platform backup**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
set -euo pipefail
risk_platform_backup_path='/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/backups/mor_platform-before-risk-operations-20260907.sql'
risk_private_dir='/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private'
risk_backup_dir='/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/backups'
umask 077
require_owned_private_directory() {
  local candidate="$1"
  test ! -L "$candidate"
  if [[ ! -e "$candidate" ]]; then
    install -d -m 700 "$candidate"
  fi
  test -d "$candidate"
  test ! -L "$candidate"
  test "$(stat -f '%u' "$candidate")" = "$(id -u)"
  test "$(stat -f '%Lp' "$candidate")" = '700'
}
require_owned_private_directory "$risk_private_dir"
require_owned_private_directory "$risk_backup_dir"
test ! -e "$risk_platform_backup_path"
test ! -L "$risk_platform_backup_path"
risk_platform_backup_tmp=$(mktemp "${risk_platform_backup_path}.tmp.XXXXXX")
trap 'rm -f -- "$risk_platform_backup_tmp"' EXIT
chmod 600 "$risk_platform_backup_tmp"
./test-environment/compose.sh exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump --protocol=socket -uroot --single-transaction --quick --skip-lock-tables --no-tablespaces --default-character-set=utf8mb4 --hex-blob mor_platform' \
  > "$risk_platform_backup_tmp"
test -s "$risk_platform_backup_tmp"
risk_platform_backup_tmp_sha=$(shasum -a 256 "$risk_platform_backup_tmp" | awk '{print $1}')
ln "$risk_platform_backup_tmp" "$risk_platform_backup_path"
rm -f -- "$risk_platform_backup_tmp"
trap - EXIT
test -f "$risk_platform_backup_path"
test ! -L "$risk_platform_backup_path"
test -s "$risk_platform_backup_path"
test "$(stat -f '%Lp' "$risk_platform_backup_path")" = '600'
risk_platform_backup_sha=$(shasum -a 256 "$risk_platform_backup_path" | awk '{print $1}')
test "$risk_platform_backup_tmp_sha" = "$risk_platform_backup_sha"
shasum -a 256 "$risk_platform_backup_path"
shasum -a 256 test-environment/sql/2026-09-07-risk-operations-rule-configuration.sql
```

Both directory components must be real non-symlink mode-`0700` directories owned by the current user before any temporary is created. The same-directory temporary plus no-overwrite hard link must leave one non-empty, non-symlink mode-`0600` dump. Record only the two SHA-256 values in private evidence; never inspect or print dump contents.

- [ ] **Step 5: Classify the Risk catalog without mutation**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/initialize.py --verify-risk-catalog
```

Only `EMPTY` or `PREPARED` may continue; any partial/drifted state stops.

- [ ] **Step 6: Apply Risk Owner SQL only for the exact `EMPTY` result**

Skip this checkbox when Step 5 returned `PREPARED`. For `EMPTY`, run only:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/initialize.py --apply-risk-sql
```

- [ ] **Step 7: Require the Risk catalog `PREPARED` postcondition**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/initialize.py --verify-risk-catalog
```

- [ ] **Step 8: Validate the pinned Nginx configuration**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
set -euo pipefail
nginx_probe_image='nginx:1.30.4-alpine@sha256:dc5069ad14f19660b141b21236140b91656bf89bbc3e2417c70ae650cd66104c'
nginx_probe_config='/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/nginx/nginx.conf'
for reserved_variable in DOCKER_HOST DOCKER_CONTEXT DOCKER_CONFIG BUILDKIT_HOST; do
  test -z "${!reserved_variable:-}"
done
nginx_docker_context="$(docker context show)"
case "$(docker context inspect --format '{{.Endpoints.docker.Host}}' "$nginx_docker_context")" in
  unix://*) ;;
  *) exit 1 ;;
esac
test "$(./test-environment/compose.sh config --format json | python3 -c 'import json, sys; print(json.load(sys.stdin)["services"]["nginx"]["image"])')" = "$nginx_probe_image"
test -f "$nginx_probe_config"
test ! -L "$nginx_probe_config"
docker run --rm --pull=never --network none --read-only \
  --tmpfs /tmp:size=16m,mode=1777 \
  --cap-drop ALL \
  --security-opt no-new-privileges:true \
  --memory 64m \
  --user nginx \
  --mount "type=bind,src=$nginx_probe_config,dst=/etc/nginx/nginx.conf,readonly" \
  --entrypoint nginx "$nginx_probe_image" -t
```

Expected: after the local-Unix-context, exact Compose-service-image and real non-symlink configuration checks, the pinned local `nginx:1.30.4-alpine@sha256:dc5069ad14f19660b141b21236140b91656bf89bbc3e2417c70ae650cd66104c` reports valid syntax for that read-only bind-mounted configuration. The one-shot container retains the service's non-root user, read-only root filesystem, bounded writable `/tmp`, dropped capabilities, no-new-privileges and memory ceiling, but uses `--network none` rather than inheriting the service's fixed `172.30.72.2` Compose network allocation. A missing image fails and `--pull=never` forbids registry access.

- [ ] **Step 9a: Deploy and prove the Risk/BFF/Web phase**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
./test-environment/deploy.sh --phase risk-bff-web
```

Require exactly one pre-existing Compose-owned MySQL container ID before phase-1 infrastructure startup. Start MySQL, Redis and RabbitMQ together with `--no-recreate --no-build --pull never`, start Nacos separately with `--no-deps --no-build --pull never`, then require the exact same single MySQL container ID and all four infrastructure services healthy before any application replacement. Force-recreate Risk, Operations BFF and Operations Web in that order with `--no-deps` and require every selected health check to pass. No public-chain smoke runs in this phase. Only after success may the exact six-image checkpoint be atomically published. A missing/multiple/replaced MySQL container or any other failure leaves no valid checkpoint and stops here; do not run Step 9b.

- [ ] **Step 9b: Deploy and prove the Platform/Gateway/Nginx phase**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
./test-environment/deploy.sh --skip-build --phase platform-gateway-nginx
```

Require the phase-1 checkpoint to exist safely and match current IDs for all six images before any phase-2 replacement. Then require Platform, Nginx and Gateway to be force-recreated with `--no-deps` in that order, every selected health check to pass, and the default public smoke to run exactly once before any catalog mutation. Both Operations API locations in Nginx must overwrite a single `X-Entry-Channel: OPERATIONS` before the Channel-aware Gateway starts; an external same-name Header is never trusted or appended. That smoke must not mutate the Platform catalog or core business facts; its existing authentication/logout and ignored local access-evidence write remain allowed. This two-command checkpoint is the executable proof of design section 16's fixed deployment waves and the Nginx-before-Gateway trusted Channel prerequisite.

- [ ] **Step 10: Apply the disabled Platform increment**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/initialize.py --apply-risk-operations-sql
```

Require exactly five permissions, six resources, nine routes and the canonical child counts; a prepared catalog is a no-op that preserves statuses.

- [ ] **Step 11: Classify persisted rollout state**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/initialize.py --verify-risk-operations-stage
```

- [ ] **Step 12: Classify the complete HTTP catalog independently**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_rollout.py \
  --base-url http://localhost:28080 --inspect-stage
```

The two inspectors must name the same one of these states: `BASELINE`, `AUTHORING_READ_ACTIVE`, `RULE_SET_LIST_READ_ACTIVE`, `RULE_SET_DETAIL_READ_ACTIVE`, `CANDIDATE_LIST_READ_ACTIVE`, `READ_ACTIVE`, `SAVE_ACTIVE`, `FREEZE_ACTIVE`, `CREATE_ACTIVE`, `ALL_ROUTES_ACTIVE` or `RESOURCES_EXPOSED`. Any non-prefix Route set, partial Resource set, legacy drift, incomplete page or database/HTTP disagreement stops. Always complete Step 13 before branching, then use this exact resume map:

- `BASELINE`: run Task 8 Baseline Entry Gates 1–2, then Task 8 Step 1.
- `AUTHORING_READ_ACTIVE`: run Task 8 Steps 2–4; continue at Step 6 only after all three pass.
- `RULE_SET_LIST_READ_ACTIVE`: run Task 8 Steps 7–9; continue at Step 11 only after all three pass.
- `RULE_SET_DETAIL_READ_ACTIVE`: run Task 8 Steps 12–14; continue at Step 16 only after all three pass.
- `CANDIDATE_LIST_READ_ACTIVE`: run Task 8 Steps 17–19; continue at Step 21 only after all three pass.
- `READ_ACTIVE`: run Task 8 Steps 22–25; continue at Step 27 only after all four pass.
- `SAVE_ACTIVE`: run Task 8 Steps 28–30; continue at Step 32 only after all three pass.
- `FREEZE_ACTIVE`: run Task 8 Steps 33–35; continue at Step 37 only after all three pass.
- `CREATE_ACTIVE`: run Task 8 Steps 38–40; continue at Step 42 only after all three pass.
- `ALL_ROUTES_ACTIVE`: run Task 8 Steps 43–45; continue at Task 9 Step 1 only after all three pass. If one fails, run Task 8 Step 46 and stop.
- `RESOURCES_EXPOSED`: continue at Task 9 Step 1; Resource activation is then an exact verified no-op.

These branches apply equally to a first run and a resumed process. Never perform the next catalog mutation before its current-stage runtime proof succeeds.

- [ ] **Step 13: Create or verify the private synthetic IAM fixture**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py \
  --base-url http://localhost:28080 --prepare-authorization-fixture
```

Require two deterministic roles/accounts and an ignored credential file beneath `test-environment/private` with parent mode `0700` and file mode `0600`. Before `ALL_ROUTES_ACTIVE`, both roles are legacy `OPERATIONS_SYSTEM_READ` only. At `ALL_ROUTES_ACTIVE` or `RESOURCES_EXPOSED`, the authorized role may already have the exact five Risk grants and the denied role remains legacy-only; no other grant shape is accepted.

This single command may pause between login calls while the current five-login-per-minute Client-IP guard refills. It retries only a strict `429 GATEWAY_RATE_LIMITED` under the shared 90-second fixture deadline; do not interrupt it during a bounded wait or start Prepare/Cleanup concurrently.

### Task 8: Activate and Prove Each Route as an Independent Durable Stage

Each Route activation below is followed by two read-only gates and one route-specific smoke. GET-route smoke is read-only; Save, Freeze, Candidate Create and Candidate Validate smoke deliberately create uniquely named immutable synthetic Risk facts. If an activation result is unknown or any later gate fails, run that subsection's exact recovery checkbox and do not continue. The recovery coordinator proves either successful-next runtime or exact invalid-refresh Last Valid prior runtime before disabling, then proves the prior state after rollback. It never invokes Resource DML or deletes synthetic Risk facts.

#### Baseline entry gate before the first Route activation

- [ ] **Baseline Entry Gate 1: Wait for baseline Snapshot convergence**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/gateway_snapshot_wait.py --expected-stage BASELINE --timeout-seconds 60
```

Require 27 active Operations routes and no active Risk Route code.

- [ ] **Baseline Entry Gate 2: Probe baseline runtime identity**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --probe-runtime-stage BASELINE
```

Do not activate Authoring Context unless both baseline entry gates pass. This catches a resumed database baseline whose Gateway still serves a newer Last Valid Snapshot.

#### Authoring Context: `BASELINE -> AUTHORING_READ_ACTIVE`

- [ ] **Step 1: Activate Authoring Context**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_rollout.py --base-url http://localhost:28080 --activate-route operations-risk-rule-authoring-context
```

- [ ] **Step 2: Wait for Authoring Context Snapshot convergence**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/gateway_snapshot_wait.py --expected-stage AUTHORING_READ_ACTIVE --timeout-seconds 60
```

Require 28 active Operations routes and exact Risk prefix `1024`.

- [ ] **Step 3: Probe Authoring Context runtime identity**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --probe-runtime-stage AUTHORING_READ_ACTIVE
```

- [ ] **Step 4: Smoke Authoring Context**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --route-smoke operations-risk-rule-authoring-context
```

- [ ] **Step 5 (failure only): Recover Authoring Context**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_recovery.py --base-url http://localhost:28080 --recover-route operations-risk-rule-authoring-context --timeout-seconds 60
```

#### Rule Set List: `AUTHORING_READ_ACTIVE -> RULE_SET_LIST_READ_ACTIVE`

- [ ] **Step 6: Activate Rule Set List**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_rollout.py --base-url http://localhost:28080 --activate-route operations-risk-rule-set-list
```

- [ ] **Step 7: Wait for Rule Set List Snapshot convergence**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/gateway_snapshot_wait.py --expected-stage RULE_SET_LIST_READ_ACTIVE --timeout-seconds 60
```

Require 29 active Operations routes and exact Risk prefix `1024,1025`.

- [ ] **Step 8: Probe Rule Set List runtime identity**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --probe-runtime-stage RULE_SET_LIST_READ_ACTIVE
```

- [ ] **Step 9: Smoke Rule Set List**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --route-smoke operations-risk-rule-set-list
```

- [ ] **Step 10 (failure only): Recover Rule Set List**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_recovery.py --base-url http://localhost:28080 --recover-route operations-risk-rule-set-list --timeout-seconds 60
```

#### Rule Set Detail: `RULE_SET_LIST_READ_ACTIVE -> RULE_SET_DETAIL_READ_ACTIVE`

- [ ] **Step 11: Activate Rule Set Detail**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_rollout.py --base-url http://localhost:28080 --activate-route operations-risk-rule-set-detail
```

- [ ] **Step 12: Wait for Rule Set Detail Snapshot convergence**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/gateway_snapshot_wait.py --expected-stage RULE_SET_DETAIL_READ_ACTIVE --timeout-seconds 60
```

Require 30 active Operations routes and exact Risk prefix `1024,1025,1026`.

- [ ] **Step 13: Probe Rule Set Detail runtime identity**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --probe-runtime-stage RULE_SET_DETAIL_READ_ACTIVE
```

- [ ] **Step 14: Smoke Rule Set Detail**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --route-smoke operations-risk-rule-set-detail
```

Require the Owner-coded absent-ID `404 RISK_DECISION_CONFIGURATION_NOT_FOUND`, distinct from Gateway route absence.

- [ ] **Step 15 (failure only): Recover Rule Set Detail**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_recovery.py --base-url http://localhost:28080 --recover-route operations-risk-rule-set-detail --timeout-seconds 60
```

#### Candidate List: `RULE_SET_DETAIL_READ_ACTIVE -> CANDIDATE_LIST_READ_ACTIVE`

- [ ] **Step 16: Activate Candidate List**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_rollout.py --base-url http://localhost:28080 --activate-route operations-risk-test-candidate-list
```

- [ ] **Step 17: Wait for Candidate List Snapshot convergence**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/gateway_snapshot_wait.py --expected-stage CANDIDATE_LIST_READ_ACTIVE --timeout-seconds 60
```

Require 31 active Operations routes and exact Risk prefix `1024,1025,1026,1029`.

- [ ] **Step 18: Probe Candidate List runtime identity**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --probe-runtime-stage CANDIDATE_LIST_READ_ACTIVE
```

- [ ] **Step 19: Smoke Candidate List**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --route-smoke operations-risk-test-candidate-list
```

- [ ] **Step 20 (failure only): Recover Candidate List**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_recovery.py --base-url http://localhost:28080 --recover-route operations-risk-test-candidate-list --timeout-seconds 60
```

#### Candidate Detail: `CANDIDATE_LIST_READ_ACTIVE -> READ_ACTIVE`

- [ ] **Step 21: Activate Candidate Detail**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_rollout.py --base-url http://localhost:28080 --activate-route operations-risk-test-candidate-detail
```

- [ ] **Step 22: Wait for complete read-prefix Snapshot convergence**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/gateway_snapshot_wait.py --expected-stage READ_ACTIVE --timeout-seconds 60
```

Require 32 active Operations routes and exact Risk prefix `1024,1025,1026,1029,1030`.

- [ ] **Step 23: Probe complete read-prefix runtime identity**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --probe-runtime-stage READ_ACTIVE
```

- [ ] **Step 24: Smoke Candidate Detail**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --route-smoke operations-risk-test-candidate-detail
```

- [ ] **Step 25: Re-prove all five read routes together**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --read-stage
```

- [ ] **Step 26 (failure only): Recover Candidate Detail**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_recovery.py --base-url http://localhost:28080 --recover-route operations-risk-test-candidate-detail --timeout-seconds 60
```

#### Save: `READ_ACTIVE -> SAVE_ACTIVE`

- [ ] **Step 27: Activate Save**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_rollout.py --base-url http://localhost:28080 --activate-route operations-risk-rule-set-save
```

- [ ] **Step 28: Wait for Save Snapshot convergence**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/gateway_snapshot_wait.py --expected-stage SAVE_ACTIVE --timeout-seconds 60
```

Require 33 active Operations routes.

- [ ] **Step 29: Probe Save runtime identity**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --probe-runtime-stage SAVE_ACTIVE
```

- [ ] **Step 30: Smoke Save**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --route-smoke operations-risk-rule-set-save
```

Require a newly read-back DRAFT.

- [ ] **Step 31 (failure only): Recover Save**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_recovery.py --base-url http://localhost:28080 --recover-route operations-risk-rule-set-save --timeout-seconds 60
```

#### Freeze: `SAVE_ACTIVE -> FREEZE_ACTIVE`

- [ ] **Step 32: Activate Freeze**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_rollout.py --base-url http://localhost:28080 --activate-route operations-risk-rule-set-freeze
```

- [ ] **Step 33: Wait for Freeze Snapshot convergence**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/gateway_snapshot_wait.py --expected-stage FREEZE_ACTIVE --timeout-seconds 60
```

Require 34 active Operations routes.

- [ ] **Step 34: Probe Freeze runtime identity**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --probe-runtime-stage FREEZE_ACTIVE
```

- [ ] **Step 35: Smoke Freeze**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --route-smoke operations-risk-rule-set-freeze
```

Require a newly read-back FROZEN version.

- [ ] **Step 36 (failure only): Recover Freeze**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_recovery.py --base-url http://localhost:28080 --recover-route operations-risk-rule-set-freeze --timeout-seconds 60
```

#### Candidate Create: `FREEZE_ACTIVE -> CREATE_ACTIVE`

- [ ] **Step 37: Activate Candidate Create**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_rollout.py --base-url http://localhost:28080 --activate-route operations-risk-test-candidate-create
```

- [ ] **Step 38: Wait for Candidate Create Snapshot convergence**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/gateway_snapshot_wait.py --expected-stage CREATE_ACTIVE --timeout-seconds 60
```

Require 35 active Operations routes.

- [ ] **Step 39: Probe Candidate Create runtime identity**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --probe-runtime-stage CREATE_ACTIVE
```

- [ ] **Step 40: Smoke Candidate Create**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --route-smoke operations-risk-test-candidate-create
```

Require a newly read-back DRAFT Candidate.

- [ ] **Step 41 (failure only): Recover Candidate Create**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_recovery.py --base-url http://localhost:28080 --recover-route operations-risk-test-candidate-create --timeout-seconds 60
```

#### Candidate Validate: `CREATE_ACTIVE -> ALL_ROUTES_ACTIVE`

- [ ] **Step 42: Activate Candidate Validate**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_rollout.py --base-url http://localhost:28080 --activate-route operations-risk-test-candidate-validate
```

- [ ] **Step 43: Wait for all-route Snapshot convergence**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/gateway_snapshot_wait.py --expected-stage ALL_ROUTES_ACTIVE --timeout-seconds 60
```

Require 36 active Operations routes and all nine exact Risk codes.

- [ ] **Step 44: Probe all-route runtime identity**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --probe-runtime-stage ALL_ROUTES_ACTIVE
```

- [ ] **Step 45: Smoke Candidate Validate**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --route-smoke operations-risk-test-candidate-validate
```

Require `200 + VALIDATING/version=2`, eight PASSED checks, Detail `waitingForShadow=true` and same-key replay.

- [ ] **Step 46 (failure only): Recover Candidate Validate**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_recovery.py --base-url http://localhost:28080 --recover-route operations-risk-test-candidate-validate --timeout-seconds 60
```

### Task 9: Expose Resources, Accept the UI and Assemble Proven Facts

- [ ] **Step 1: Grant the synthetic authorized role the five Risk permissions**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --grant-authorization-fixture
```

Require either `ALL_ROUTES_ACTIVE` or `RESOURCES_EXPOSED`, exact grants `OPERATIONS_SYSTEM_READ + 536..540`, denied legacy-only role, fresh login for both accounts, authorized read and denied 403. When a resumed `RESOURCES_EXPOSED` run had previously cleaned the fixture, Task 7 Step 13 recreates the two identities and private credential file; this action only applies or verifies the five authorized-role grants and never creates an identity or changes Route/Resource state.

- [ ] **Step 2: Enable the six Console Resources atomically**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/initialize.py --enable-risk-operations-resources
```

- [ ] **Step 3: Verify persisted `RESOURCES_EXPOSED`**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/initialize.py --verify-risk-operations-stage
```

- [ ] **Step 4: Verify HTTP catalog `RESOURCES_EXPOSED`**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_rollout.py --base-url http://localhost:28080 --inspect-stage
```

- [ ] **Step 5: Reconfirm the unchanged all-route Snapshot**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/gateway_snapshot_wait.py --expected-stage RESOURCES_EXPOSED --timeout-seconds 60
```

- [ ] **Step 6: Probe visible Resources and both authorization outcomes**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --probe-runtime-stage RESOURCES_EXPOSED
```

Require six-resource Navigation for the authorized actor and authoritative PLT-I02 denial for the denied actor after fresh login.

- [ ] **Step 7: Run the opt-in full-chain smoke**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --full
```

Require full HTTP flow, 65,536-byte acceptance, 65,537-byte Gateway 413, Nacos identity and layered recovery evidence. Preserve immutable synthetic Risk facts.

- [ ] **Step 8: Perform the operator-facing browser acceptance**

Open `http://localhost:28080` as the synthetic authorized operator and verify 风控管理 → 规则配置, both tabs, structured ALL/ANY/NOT controls, field/operator/value widgets, Action selection, byte counter, save, immutable detail, freeze, Candidate creation and eight-check result. Confirm raw JSON/AST and node/sequence controls are absent, RETIRED is read-only, an early FROZEN can create a Candidate, and dirty-editor navigation prompts. Prove rule content is absent from URL/browser storage. Record Request IDs and screenshots only—never payloads, tokens, full hashes or keys.

- [ ] **Step 9: Clean up only the isolated IAM fixture**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --cleanup-authorization-fixture
```

- [ ] **Step 10: Assemble the fresh ledger evidence pack**

Collect this slice's exact component working-tree revisions, test results, SQL status, local deployment/smoke evidence and known gaps without editing `agent-docs/CURRENT_IMPLEMENTATION.md` yet. If component commits are authorized, the umbrella commit index records their final hashes before the sole ledger write; otherwise record the current HEAD plus diff identity. Keep shared/production deployment, Shadow, approval, production activation, PRODUCTION Release and runtime evaluation absent from any positive claim.

- [ ] **Step 11: Inspect root ownership before staging**

The root worktree already contains independent changes. Inspect both tracked and untracked ownership before touching the index:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
git status --short
git diff -- \
  agent-docs/CURRENT_IMPLEMENTATION.md \
  docs/superpowers/specs/2026-09-06-risk-operations-rule-configuration-console-integration-design.md \
  test-environment/README.md \
  test-environment/compose.applications.yaml \
  test-environment/scripts/build.sh \
  test-environment/scripts/smoke.py
```

Require the Risk-owned hunk boundaries and the single umbrella-owned Task 3 Step 8 ledger hunk to be identifiable.
The six new implementation plans and the current design-spec diff form this planning suite and may be staged whole only
while that spec diff contains no independent concurrent hunk. If an independent spec hunk appears, remove the spec from
Step 13 and add it to the Step 14 interactive list before running either staging command.
`test-environment/deploy.sh` and `test-environment/scripts/test_deploy_script.py` are currently untracked predecessor
artifacts, so a Risk-only commit is allowed only if their complete pre-Risk baseline has first been committed by its
owning track or the approved staged result remains a complete executable/test file. Never construct a partial invalid
file from selected additions against `/dev/null`.

- [ ] **Step 12: Mark untracked overlap files for interactive review**

Run `intent-to-add` before patch selection so Git can present their additions without staging their contents:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
git add -N -- \
  test-environment/deploy.sh \
  test-environment/scripts/test_deploy_script.py
```

- [ ] **Step 13: Stage only wholly owned new integration files**

Use this exact allowlist. Do not include Compose or deployment files in this whole-file action:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
git add -- \
  docs/superpowers/plans/2026-09-07-risk-operations-rule-configuration-console-integration.md \
  docs/superpowers/plans/2026-09-07-risk-operations-platform-control-plane.md \
  docs/superpowers/plans/2026-09-07-risk-operations-gateway-boundary.md \
  docs/superpowers/plans/2026-09-07-risk-operations-bff-adapter.md \
  docs/superpowers/plans/2026-09-07-risk-operations-rule-builder-web.md \
  docs/superpowers/plans/2026-09-07-risk-operations-local-integration.md \
  docs/superpowers/specs/2026-09-06-risk-operations-rule-configuration-console-integration-design.md \
  test-environment/nginx/nginx.conf \
  test-environment/scripts/test_nginx_gateway_problem_boundary.py \
  test-environment/scripts/risk_rule_configuration_stage.py \
  test-environment/scripts/test_smoke_stage_contract.py \
  test-environment/scripts/initialize.py \
  test-environment/scripts/test_risk_database_initialization.py \
  test-environment/sql/2026-09-07-risk-operations-rule-configuration.sql \
  test-environment/sql/2026-09-07-risk-operations-rule-configuration-resources-enable.sql \
  test-environment/sql/2026-09-07-risk-operations-rule-configuration-resources-disable.sql \
  test-environment/scripts/test_risk_rule_configuration_catalog.py \
  test-environment/scripts/risk_rule_configuration_smoke.py \
  test-environment/scripts/test_risk_rule_configuration_smoke.py \
  test-environment/scripts/risk_rule_configuration_rollout.py \
  test-environment/scripts/test_risk_rule_configuration_rollout.py \
  test-environment/scripts/test_risk_rule_configuration_authorization_inventory.py \
  test-environment/scripts/risk_rule_configuration_recovery.py \
  test-environment/scripts/test_risk_rule_configuration_recovery.py \
  test-environment/scripts/gateway_snapshot_metrics.py \
  test-environment/scripts/test_gateway_snapshot_metrics.py \
  test-environment/scripts/gateway_snapshot_wait.py \
  test-environment/scripts/test_gateway_snapshot_wait.py \
  test-environment/java/LocalSnapshotEvidenceFetch.java
```

- [ ] **Step 14: Interactively stage every overlap file**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
git add -p -- \
  agent-docs/CURRENT_IMPLEMENTATION.md \
  docs/superpowers/specs/2026-09-06-risk-operations-rule-configuration-console-integration-design.md \
  test-environment/README.md \
  test-environment/compose.applications.yaml \
  test-environment/deploy.sh \
  test-environment/scripts/build.sh \
  test-environment/scripts/smoke.py \
  test-environment/scripts/test_java_image_health_probe.py \
  test-environment/scripts/test_deploy_script.py
```

Accept only this track's Risk service, Risk rollout and validation-evidence hunks, plus the exact umbrella Task 3 Step 8
current-fact ledger hunk produced from Step 10's evidence pack. Reject every independent Vault,
deployment-foundation or pre-existing/unrelated ledger hunk. If either intent-to-add file cannot produce a complete
valid staged artifact without predecessor content, stop and have its owning track establish that baseline first.

- [ ] **Step 15: Verify the exact staged artifact**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
set -euo pipefail
git diff --cached --check
git diff --cached --name-only
git diff --cached
git show :test-environment/deploy.sh | bash -n
git show :test-environment/scripts/test_deploy_script.py | \
  python3 -c 'import sys; compile(sys.stdin.read(), "test_deploy_script.py", "exec")'
risk_staged_compose=$(mktemp test-environment/private/risk-compose.XXXXXX.yaml)
trap 'rm -f -- "$risk_staged_compose"' EXIT
git show :test-environment/compose.applications.yaml > "$risk_staged_compose"
docker compose --project-name mor-test-staged-check \
  --project-directory test-environment \
  --env-file test-environment/.env.test \
  -f test-environment/compose.yaml \
  -f "$risk_staged_compose" \
  --profile base --profile applications config --quiet
rm -f -- "$risk_staged_compose"
trap - EXIT
```

Require the staged deploy script to pass Bash syntax, the staged deploy test to compile, and the staged Compose overlay to render with the repository's base file and private test environment. Do not continue while an intent-to-add entry is incomplete or any unrelated Vault/deployment/ledger hunk is staged.

- [ ] **Step 16: Commit only after the staged diff is approved**

After showing the exact staged diff and receiving commit authorization, run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
git commit -m "test(environment): prepare Risk rule configuration chain"
```

#### Authorized rollback procedure

This is a separate terminal path, not part of successful acceptance. Use only with rollback authorization and in this exact order; each checkbox is one independently verified action.

- [ ] **Rollback Step 1: Hide Resources while every Route remains active**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/initialize.py --disable-risk-operations-resources
```

- [ ] **Rollback Step 2: Remove synthetic grants while all permissions remain grantable**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --cleanup-authorization-fixture
```

- [ ] **Rollback Step 3: Reverse only adjacent Routes to baseline**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_rollout.py --base-url http://localhost:28080 --reconcile-baseline
```

- [ ] **Rollback Step 4: Wait for baseline Snapshot convergence**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/gateway_snapshot_wait.py --expected-stage BASELINE --timeout-seconds 60
```

- [ ] **Rollback Step 5: Prove baseline runtime behavior**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/risk_rule_configuration_smoke.py --base-url http://localhost:28080 --probe-runtime-stage BASELINE
```
