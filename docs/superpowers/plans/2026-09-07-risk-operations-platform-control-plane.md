# Risk Operations Platform Control Plane Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the exact Operations Risk permissions, navigation resources and nine Gateway route definitions to Platform Support while raising its generic route-body ceiling to 65,536 bytes.

**Architecture:** Platform remains the authority for PLT-I02 permissions, Console Resources and the immutable Gateway Snapshot source. This track changes no schema or Risk business data: it extends canonical manual initialization data, validates it as an exact catalog, and keeps existing-environment rollout in the separate local-integration plan.

**Tech Stack:** Java 25, Maven Wrapper 3.9.16, Spring Boot 4.0.8, MyBatis, MySQL 8.4 manual SQL, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-06-risk-operations-rule-configuration-console-integration-design.md`

## Global Constraints

- Reuse target ID `900000000000000001`, code/discovery name `operations-console-bff`, channel `OPERATIONS`; do not add a Risk Target.
- Add exactly 5 permissions, 6 Console Resources, 6 role-permission grants, 9 routes, 34 route-header rows, 9 query-parameter rows and 54 guard rows.
- Canonical initialization rows are `ACTIVE`; the existing-environment incremental SQL is a separate artifact whose routes/resources start `DISABLED`.
- Keep every existing route’s body limit unchanged. Only `operations-risk-rule-set-save` is 65,536; three other new POST routes are 16,384 and five new GET routes are zero.
- Keep all new routes at `body_receive_timeout_millis=5000` and `response_timeout_millis=10000` with cookie modes `DROP/NONE`.
- Use `OPERATIONS_JWT`, `HUMAN_PERMISSION` and one explicit permission ID per route.
- Every route has exactly the six guard rows specified below; do not introduce a new algorithm or TENANT guard.
- Do not modify PDManer, schema DDL, DataObjects, Mappers or repositories. Java never applies the DML.
- Database-backed tests reuse only `local-environment` MySQL at `127.0.0.1:13306/mor_platform`, never the independently deployed `test-environment`/`mor-test` database at port `23306`. Tests never migrate either database. If the local auto-test database is not the exact clean canonical revision, use only Task 5's separately authorized backup/rebuild procedure or leave the database gates pending.
- Preserve the current clean Platform baseline. Never stage the root planning repository from this track; inside `platform-support-service`, stage only the final explicit allowlist in Task 5 and stop if any listed path acquired an unrelated concurrent edit.

---

### Task 1: Raise the Platform Compiler Ceiling With a Focused RED/GREEN Cycle

**Files:**
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/gatewayconfiguration/GatewayConfigurationCompilerAutoTest.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/gatewayconfiguration/GatewaySnapshotHttpAutoTest.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/PlatformSupportHealthAutoTest.java`
- Modify: `platform-support-service/platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/domain/gatewayconfiguration/GatewayConfigurationCompiler.java`

**Interfaces:**
- Consumes: `GatewayConfigurationProjection.RouteRow.bodyLimitBytes()`.
- Produces: the generic invariant `0 <= bodyLimitBytes <= 65_536`, independent of route path.

- [ ] **Step 1: Change the focused boundaries to define the new ceiling**

In `test_transportBoundariesAcceptExactCeilingsAndRejectEveryOutsideEdge`, keep the current 16,384 accepted route, add a second accepted route at 65,536, keep the existing negative case and add a 65,537 rejection. Use the method’s existing local `target` variable and the actual shared error text for both invalid bounds:

```java
RouteRow maximumBody = route(106L, 1L, "OPERATIONS", "body-maximum", "POST", "/body", "/body",
    "PUBLIC", "PUBLIC", null, null, "DROP", "NONE", "ACTIVE", 65_536L, 5_000, 10_000);
assertDoesNotThrow(() -> compiler.compile(configuration(target, maximumBody, List.of(), baseline(106L))));
assertRejected("Body Limit 超出 Gateway 绝对上限", configuration(target,
    route(107L, 1L, "OPERATIONS", "body-too-large", "POST", "/too-large", "/too-large", "PUBLIC",
        "PUBLIC", null, null, "DROP", "NONE", "ACTIVE", 65_537L, 5_000, 10_000),
    List.of(), baseline(107L)));
```

Also change the independent `test_inactiveRowsAndRemainingCatalogBoundariesFailClosed` rejection fixture from 16,385 to 65,537; no old-threshold assertion may survive.

In `GatewaySnapshotHttpAutoTest`, change the existing database corruption value from 16,385 to 65,537 and add a 65,536 success case. In `PlatformSupportHealthAutoTest.test_livenessRemainsProcessOnlyWhenRuntimeReadinessIsDown`, likewise change `fixture.bodyLimit(16385)` to `65537`; 16,385 becomes valid after the generic ceiling rises and must no longer be the readiness corruption fixture.

- [ ] **Step 2: Run the focused test and capture RED**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service
./mvnw -pl platform-support-test -am \
  -Dtest=GatewayConfigurationCompilerAutoTest,GatewaySnapshotHttpAutoTest,PlatformSupportHealthAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the compiler and HTTP 65,536 cases fail because the current compiler rejects that value above its 16,384 ceiling; the 65,537 compiler/readiness cases still reject or report DOWN.

- [ ] **Step 3: Make the minimum generic implementation change**

Use one constant and keep the existing validation flow:

```java
private static final long MAX_BODY_LIMIT_BYTES = 65_536L;
```

Do not branch on route code or path.

- [ ] **Step 4: Run GREEN**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service
./mvnw -pl platform-support-test -am \
  -Dtest=GatewayConfigurationCompilerAutoTest,GatewaySnapshotHttpAutoTest,PlatformSupportHealthAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all three focused classes pass when the prepared database prerequisite is available; otherwise the database-backed classes report only their existing setup prerequisite.

### Task 2: Create One Shared Canonical Catalog Oracle and Freeze Static SQL

**Files:**
- Create: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/test/operations/OperationsCanonicalCatalogOracle.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsPersistenceContractAutoTest.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsPagingCatalogContractAutoTest.java`

**Interfaces:**
- Consumes: the code-frozen complete canonical immutable tuples, `docs/database/sql/platform-support-initial-data.sql` as an independent projection now, and the prepared-database `JdbcTemplate` as a second independent projection in Task 4.
- Produces: one normalized immutable `Catalog` contract shared by static and database tests; IDs, codes, paths, permissions, limits, headers, queries, guards and role/resource bindings are never re-described in a second mapper-only oracle.

- [ ] **Step 1: Add the missing shared-oracle consumer before its implementation**

In `OperationsPersistenceContractAutoTest`, replace the private `initialRows(String table)`/local service-root parsing path with calls to the not-yet-existing `OperationsCanonicalCatalogOracle.canonical()` and `readInitialSql()`. Add `test_initialSqlExactlyMatchesFrozenOperationsCatalog`, and make all existing table-specific assertions consume the returned `Catalog` collections rather than reparsing SQL independently. Do not create the helper in this step.

- [ ] **Step 2: Run the oracle compile RED**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service
./mvnw -pl platform-support-test -am \
  -DskipTests test-compile
```

Expected: compilation fails only because `OperationsCanonicalCatalogOracle` and its `Catalog` API do not exist.

- [ ] **Step 3: Implement the shared normalized projection**

Create the public, test-only `OperationsCanonicalCatalogOracle` with exactly these entry points:

```java
public static Catalog canonical()
public static Catalog readInitialSql()
public static Catalog readDatabase(JdbcTemplate jdbc)
```

`canonical()` is a complete code-frozen literal of every projected immutable tuple and must never read SQL, JDBC, generated output or another runtime source. Populate it from a reviewed copy of the current 15/27 legacy catalog plus the exact Task 2–4 additions; do not build it by calling either reader. `readInitialSql()` independently locates `docs/database/sql/platform-support-initial-data.sql` using the repository's existing Maven-working-directory rule, parses each relevant `INSERT`, resolves the file's scalar `SET @...` ID/actor variables, normalizes quoted values, numbers and `NULL`, excludes only `create_time`/`update_time`, and returns primary-ID-sorted immutable projections. `readDatabase` performs read-only `SELECT`s with the same column order and normalization; it must contain no DDL, DML, initializer call or transaction side effect.

Use one immutable `Catalog` record containing exactly: the one Operations Target; all Permission definitions; all grants for the two system roles; all `OPERATIONS` Console Resources; all `OPERATIONS` Routes; and every Header, Query and Guard child joined to those Routes. Freeze these columns:

```text
Target:      target_id, target_service_code, discovery_service_name, channel, status, version, created_by, updated_by
Permission:  permission_id, permission_code, resource_type, action_code, risk_level, status, version
Role grant:  role_permission_id, role_id, permission_id, status, version
Resource:    resource_id, resource_code, channel, resource_type, parent_resource_id, display_name,
             frontend_route, component_key, permission_id, sort_order, status, version
Route:       route_id, route_code, channel, http_method, external_path_template, downstream_path_template,
             target_id, security_profile_code, authorization_mode, permission_id, required_scope_code,
             request_cookie_mode, response_cookie_mode, body_limit_bytes, body_receive_timeout_millis,
             response_timeout_millis, status, version, created_by, updated_by
Header:      route_header_id, route_id, header_name, header_use
Query:       route_query_param_id, route_id, parameter_name
Guard:       guard_rule_id, route_id, rule_type, dimension, capacity, refill_tokens,
             refill_period_millis, concurrency_limit
```

Assert `canonical().equals(readInitialSql())` as one whole-catalog equality before the remaining focused assertions. The focused Task 2 tests preserve useful failure localization for complete counts, exact ID/code sets and every new Risk tuple, but `canonical()` is the sole expected source. Task 4 independently asserts `canonical().equals(readDatabase(jdbc))`; never compare SQL directly with the database. Thus synchronized drift in SQL and a prepared database still fails against the code-frozen catalog.

- [ ] **Step 4: Run the oracle compile GREEN**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service
./mvnw -pl platform-support-test -am \
  -DskipTests test-compile
```

Expected: test compilation passes and no database is contacted.

- [ ] **Step 5: Add exact ID and count assertions**

Create `test_riskRuleConfigurationPermissionAndResourceCatalogIsExact` whose expected ranges are:

```java
List<Long> permissionIds = LongStream.rangeClosed(900000000000000536L, 900000000000000540L).boxed().toList();
List<Long> rolePermissionIds = LongStream.rangeClosed(900000000000000581L, 900000000000000586L).boxed().toList();
List<Long> resourceIds = LongStream.rangeClosed(900000000000000565L, 900000000000000570L).boxed().toList();
```

Require total canonical counts `19` permissions, `21` Console Resources and `13` button resources. Require Admin grants `536..540` and Viewer grant `536` only. Keep Route totals out of this test so Task 3 can turn the permission/resource slice GREEN before Task 4 adds routes.

- [ ] **Step 6: Add the exact route matrix assertion**

Create `test_riskRuleConfigurationRouteCatalogIsExact`; represent the nine rows with this data, then parse and compare every field rather than checking substrings:

```java
List<Long> routeIds = LongStream.rangeClosed(900000000000001024L, 900000000000001032L).boxed().toList();
```

Require the complete canonical Operations Route count to be `36` only in this Route test.

```text
1024 operations-risk-rule-authoring-context GET  /api/operations/decision-rule-sets/v1/authoring-context/{pointCode} /decision-rule-sets/v1/authoring-context/{pointCode} READ     0
1025 operations-risk-rule-set-list          GET  /api/operations/decision-rule-sets/v1/rule-sets                    /decision-rule-sets/v1/rule-sets                    READ     0
1026 operations-risk-rule-set-detail        GET  /api/operations/decision-rule-sets/v1/rule-sets/{ruleSetVersionId} /decision-rule-sets/v1/rule-sets/{ruleSetVersionId} READ     0
1027 operations-risk-rule-set-save          POST /api/operations/decision-rule-sets/v1/rule-sets                    /decision-rule-sets/v1/rule-sets                    SAVE 65536
1028 operations-risk-rule-set-freeze        POST /api/operations/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}/freeze /decision-rule-sets/v1/rule-sets/{ruleSetVersionId}/freeze FREEZE 16384
1029 operations-risk-test-candidate-list    GET  /api/operations/decision-releases/v1/candidates                    /decision-releases/v1/candidates                    READ     0
1030 operations-risk-test-candidate-detail  GET  /api/operations/decision-releases/v1/candidates/{releaseId}        /decision-releases/v1/candidates/{releaseId}        READ     0
1031 operations-risk-test-candidate-create  POST /api/operations/decision-releases/v1/candidates                    /decision-releases/v1/candidates                    CREATE 16384
1032 operations-risk-test-candidate-validate POST /api/operations/decision-releases/v1/candidates/{releaseId}/validate /decision-releases/v1/candidates/{releaseId}/validate VALIDATE 16384
```

The test must additionally assert target `900000000000000001`, `OPERATIONS`, `OPERATIONS_JWT`, `HUMAN_PERMISSION`, `DROP`, `NONE`, `5000`, `10000`, `ACTIVE`, version `1` and null `required_scope_code` for every row.

- [ ] **Step 7: Extend the paging whitelist test**

Remove `OperationsPagingCatalogContractAutoTest`'s private SQL reader/regex parser and consume `OperationsCanonicalCatalogOracle.readInitialSql().queries()` so this second static class cannot create a competing projection. Append these exact query rows and change the distinct list-route count from 5 to 7:

```java
new QueryParameter(900000000000004031L, 900000000000001025L, "page"),
new QueryParameter(900000000000004032L, 900000000000001025L, "size"),
new QueryParameter(900000000000004033L, 900000000000001025L, "pointCode"),
new QueryParameter(900000000000004034L, 900000000000001025L, "ruleSetCode"),
new QueryParameter(900000000000004035L, 900000000000001025L, "status"),
new QueryParameter(900000000000004036L, 900000000000001029L, "page"),
new QueryParameter(900000000000004037L, 900000000000001029L, "size"),
new QueryParameter(900000000000004038L, 900000000000001029L, "pointCode"),
new QueryParameter(900000000000004039L, 900000000000001029L, "status")
```

Also change the separate global Operations Route total asserted by `OperationsPagingCatalogContractAutoTest` from `27` to `36`; keep the distinct pageable-list-route assertion at `7` so the two counts cannot be conflated.

Assert no other new route has a query row.

- [ ] **Step 8: Add exact header and guard assertions**

Require header IDs and shapes:

```text
5100..5105  routes 1024..1026: authorization/CORS_REQUEST_ALLOWED, origin/FORWARD_REQUEST
5106..5111  route 1027: GET pair plus content-type and idempotency-key, each CORS_REQUEST_ALLOWED and FORWARD_REQUEST
5112..5117  route 1028: same six write headers
5118..5121  routes 1029..1030: the two GET headers per route
5122..5127  route 1031: the six write headers
5128..5133  route 1032: the six write headers
```

For each route ID `1024..1032`, require guard IDs `6110 + (routeId - 1024) * 6` through `+5` with exactly:

```text
RATE_LIMIT SHARED_ROUTE              300 300 60000 null
CONCURRENCY SHARED_ROUTE             null null null 50
RATE_LIMIT CLIENT_IP                 10 30 60000 null
CONCURRENCY CLIENT_IP                null null null 3
RATE_LIMIT AUTHENTICATED_PRINCIPAL   10 10 60000 null
CONCURRENCY AUTHENTICATED_PRINCIPAL  null null null 2
```

- [ ] **Step 9: Freeze every pre-existing Route body limit**

In `test_riskRouteBodyLimitsPreserveLegacyCatalog`, parse the canonical Route rows and require these 27 pre-existing codes, and only these codes before the new nine, to remain at 16,384 bytes:

```text
operations-auth-login, operations-auth-refresh, operations-auth-logout,
operations-auth-context, operations-auth-password, operations-bootstrap-administrator,
operations-navigation, operations-account-list, operations-account-create,
operations-account-disable, operations-account-roles, operations-role-list,
operations-audit-list, operations-role-permission-options, operations-role-detail,
operations-role-create, operations-role-update, operations-role-delete,
operations-account-delete, operations-account-enable, operations-audit-detail,
operations-gateway-target-list, operations-gateway-target-detail,
operations-gateway-target-status, operations-gateway-route-list,
operations-gateway-route-detail, operations-gateway-route-status
```

The final assertion compares the complete `routeCode -> bodyLimitBytes` map: those 27 map to 16,384, the five new GETs map to zero, Save maps to 65,536 and the remaining three new POSTs map to 16,384.

- [ ] **Step 10: Update every existing aggregate catalog assertion in the same RED edit**

In `OperationsPersistenceContractAutoTest`, change only these stale canonical totals: route-code set `27 -> 36`, Console Resources `15 -> 21`, permissions `14 -> 19`, and buttons `9 -> 13`. Expand the existing exact button-permission ID set by adding `900000000000000537..900000000000000540` alongside its current IDs, rather than weakening it to a count-only assertion. Change root Console Resources `2 -> 3` because Risk Management is a third root. Extend the existing Console Resource assertions with the exact Risk parent/page/button relationships from Task 3; do not weaken any existing Gateway Configuration or role-management assertion.

- [ ] **Step 11: Run the catalog tests and capture behavior RED**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service
./mvnw -pl platform-support-test -am \
  -Dtest=OperationsPersistenceContractAutoTest,OperationsPagingCatalogContractAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because none of IDs `536..540`, `565..570` or routes `1024..1032` exists and the old route count is 27.

### Task 3: Add the Canonical Permission and Navigation Data

**Files:**
- Modify: `platform-support-service/docs/database/sql/platform-support-initial-data.sql`

**Interfaces:**
- Consumes: Task 2 exact permission/resource/grant assertions.
- Produces: active canonical rows for clean manual initialization; no application executes this file.

- [ ] **Step 1: Add five exact permissions**

Add IDs `536..540` in this order:

```text
RISK_DECISION_CONFIG_READ    RISK_DECISION_CONFIGURATION READ                    LOW
RISK_RULE_SET_SAVE           RISK_DECISION_CONFIGURATION SAVE                    HIGH
RISK_RULE_SET_FREEZE         RISK_DECISION_CONFIGURATION FREEZE                  HIGH
RISK_TEST_CANDIDATE_CREATE   RISK_DECISION_CONFIGURATION CREATE_TEST_CANDIDATE   HIGH
RISK_TEST_CANDIDATE_VALIDATE RISK_DECISION_CONFIGURATION VALIDATE_TEST_CANDIDATE HIGH
```

Use `ACTIVE`, version `1` and `@platform_initial_time` for both timestamps.

- [ ] **Step 2: Add six exact role grants**

Use role-permission IDs `581..585` to grant permission IDs `536..540` to role `900000000000000510`. Use ID `586` to grant permission `536` to role `900000000000000511`. All rows are `ACTIVE`, version `1`.

- [ ] **Step 3: Add six exact Console Resources**

Add:

```text
565 OPERATIONS_RISK_MANAGEMENT              MENU   null 风控管理       null                       null                                536 sort=30
566 OPERATIONS_RISK_RULE_CONFIGURATION      MENU   565  规则配置       /risk/rule-configuration   OPERATIONS_RISK_RULE_CONFIGURATION 536 sort=10
567 OPERATIONS_RISK_RULE_SET_SAVE           BUTTON 566  保存为新版本   null                       null                                537 sort=10
568 OPERATIONS_RISK_RULE_SET_FREEZE         BUTTON 566  冻结版本       null                       null                                538 sort=20
569 OPERATIONS_RISK_TEST_CANDIDATE_CREATE   BUTTON 566  创建 TEST Candidate null                   null                                539 sort=30
570 OPERATIONS_RISK_TEST_CANDIDATE_VALIDATE BUTTON 566  执行静态校验   null                       null                                540 sort=40
```

All rows use channel `OPERATIONS`, status `ACTIVE`, version `1` and canonical timestamps.

- [ ] **Step 4: Run the canonical catalog GREEN tests**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service
./mvnw -pl platform-support-test -am \
  -Dtest=OperationsPersistenceContractAutoTest#test_riskRuleConfigurationPermissionAndResourceCatalogIsExact \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the five permissions, six grants and six resources pass; Route and child-row tests remain RED until Task 4.

### Task 4: Add the Canonical Nine-Route Transport Catalog

**Files:**
- Modify: `platform-support-service/docs/database/sql/platform-support-initial-data.sql`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/gatewayconfiguration/GatewaySnapshotHttpAutoTest.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/test/gatewayconfiguration/GatewayCatalogFixture.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/test/operations/OperationsTestMapper.java`

**Interfaces:**
- Consumes: Task 2 route/header/query/guard matrix and Task 3 permission IDs.
- Produces: a complete active Snapshot image for clean installations, with no partial route children.

- [ ] **Step 1: Wire the database-side consumer of the shared oracle**

Change `OperationsTestMapper.requirePreparedDatabase()` from its exact current source precondition of `12` active resources/`21` active Operations routes to the target `21/36`. Inside that method, compare `OperationsCanonicalCatalogOracle.readDatabase(jdbc)` directly with `OperationsCanonicalCatalogOracle.canonical()` as complete `Catalog` equality before any HTTP test state is prepared; do not read SQL in this database gate. This comparison covers the one Target, all 19 Permission definitions, all 25 system-role grants (Admin 19 and Viewer 6), all 21 Resources, all 36 Routes and every Header/Query/Guard tuple—not merely counts plus new-row `EXISTS`. Convert any mismatch or read failure to the existing exact `OperationsTestMapper.SETUP` exception and include only a safe projection name/count summary, never database credentials or row values. This remains a fail-fast verifier and never applies DML.

Construct this mapper and call `requirePreparedDatabase()` at the start of `GatewaySnapshotHttpAutoTest.setUp()`; remove the weaker login-route-only check. The separately maintained canonical SQL/deployed catalog baseline is already `15/27`; do not confuse it with this older HTTP-test prerequisite.

- [ ] **Step 2: Add the whole-Snapshot HTTP RED assertions**

Extend `GatewaySnapshotHttpAutoTest` to parse one complete response and require all nine Risk route codes together under `schemaVersion=1`, each with exact public headers, queries and guards. The public contract intentionally exposes neither database IDs, revision nor fingerprint. Add `GatewayCatalogFixture.removeLastOwnedGuard()` that deletes exactly the highest-ID guard belonging to its owned temporary Route; in a `try/finally`-protected test, require this incomplete child set to make the entire HTTP read return RFC 9457 500 rather than a partial Snapshot. Existing `GatewayCatalogFixture.close()` remains the finally/after-each restoration oracle and must prove every pre-test catalog row is unchanged.

- [ ] **Step 3: Run the whole-Snapshot HTTP RED gate**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service
./mvnw -pl platform-support-test -am \
  -Dtest=GatewaySnapshotHttpAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: static catalog tests are already RED for the absent DML rows. This database-backed class now stops at the exact `OperationsTestMapper.SETUP` prerequisite until the separately authorized target catalog is active; once prepared, the incomplete-child case must fail closed and the nine-route response assertion becomes the executable GREEN gate.

- [ ] **Step 4: Add the nine route rows**

Encode the exact Task 2 matrix using full 19-digit IDs `900000000000001024..900000000000001032`. Permission IDs are respectively `536,536,536,537,538,536,536,539,540`. Use body limits `0,0,0,65536,16384,0,0,16384,16384`.

- [ ] **Step 5: Add 34 exact header rows**

Use IDs `900000000000005100..900000000000005133` and the Task 2 header layout. Preserve lower-case header names. `authorization` is CORS-only; `origin` is forward-only; write-route `content-type` and `idempotency-key` each have both uses.

- [ ] **Step 6: Add nine exact query rows and 54 guard rows**

Use query IDs `900000000000004031..900000000000004039`. Use guard IDs `900000000000006110..900000000000006163` with the deterministic formula and six-row profile from Task 2.

- [ ] **Step 7: Run Tasks 1–4 focused GREEN**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service
./mvnw -pl platform-support-test -am \
  -Dtest=GatewayConfigurationCompilerAutoTest,GatewaySnapshotHttpAutoTest,OperationsPersistenceContractAutoTest,OperationsPagingCatalogContractAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: static catalog/compiler tests pass. A database-backed Snapshot test may stop with the repository’s exact local-environment setup message until the canonical DML is manually applied under separate authorization; record that as an unmet environment prerequisite, not a code pass.

### Task 5: Align the Prepared-Database Contract and Verify the Repository

**Files:**
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/test/operations/OperationsTestMapper.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/test/operations/OperationsHttpChecker.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsAuthenticationHttpAutoTest.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsPersistenceContractAutoTest.java`
- Modify: `platform-support-service/platform-support-test/src/test/java/com/paytech/platform/support/auto/gatewayconfiguration/GatewaySnapshotHttpAutoTest.java`
- Modify: `platform-support-service/README.md`
- Inspect only at the explicit manual gate: `local-environment/compose.yaml`
- Create only as an ignored private recovery artifact when authorized: `test-environment/private/backups/mor-local-platform-before-risk-operations-20260907.sql`

**Interfaces:**
- Consumes: the new canonical catalog totals.
- Produces: fail-fast verification against one exact fresh canonical database and accurate implementation documentation.

- [ ] **Step 1: Verify the Task 4 database precondition remains exact**

Review the Task 4 change and require the `OperationsTestMapper` source transition to be exactly `12 -> 21` active Console Resources and `21 -> 36` active Operations Routes. Also require direct database-to-`canonical()` full set equality for all 19 Permission codes/immutable tuples, all 21 Resource codes, all 36 Route codes and exact immutable Resource/Route/Header/Query/Guard tuples. Compare the two complete system-role grant sets as Admin 19 and Viewer 6 (25 total); a count-preserving replacement, extra/missing legacy grant, legacy-field drift, or synchronized SQL/database drift is RED. Keep the manual-application setup message and add an assertion that the precondition contains no SQL mutation, SQL-reader call or initializer invocation. Static canonical SQL still moves independently from `15/27` to `21/36` in Tasks 2–4 and is checked separately against the same code-frozen catalog.

- [ ] **Step 2: Verify every Snapshot test uses the same fail-fast prerequisite**

Require a source/test assertion that `GatewaySnapshotHttpAutoTest.setUp()` calls `requirePreparedDatabase()` before any Snapshot HTTP query and contains no weaker login-route-only branch. An old, disabled or partially updated catalog must stop with the exact manual-setup message rather than fail later as a misleading missing-route assertion.

- [ ] **Step 3: Align exact Admin, Viewer and role-resource projections**

Update `OperationsHttpChecker.navigation` to require these additions:

```text
PLATFORM_ADMIN: current checker 12
                + existing Gateway 562 OPERATIONS_PLATFORM_CONFIGURATION,
                  563 OPERATIONS_GATEWAY_CONFIGURATION,
                  564 OPERATIONS_GATEWAY_CONFIGURATION_MANAGE
                + all six Risk resources = 21 visible resources.
OPERATIONS_VIEWER: current checker 4
                   + existing Gateway 562/563
                   + Risk root/page 565/566 only = 8 visible resources.
```

Assert the Risk root/page share READ, only the page has `/risk/rule-configuration` plus component key, and all four buttons have the page parent and their distinct write permission. Replace the checker's old Account-vs-Role Button shortcut with the exact family mapping: Account buttons → resource `550`, Role buttons → `556`, Gateway Manage `564` → `563`, and Risk buttons `567..570` → `566`; assert each code explicitly so Gateway/Risk buttons cannot inherit the Account parent. Navigation asserts Resource codes/shape only because it does not expose permission IDs. In `OperationsAuthenticationHttpAutoTest`, change the Admin-visible grantable role-resource catalog count from 12 to 21. Using the authenticated Admin, GET system-role details `/platform-support/operations/v1/roles/900000000000000510` and `/platform-support/operations/v1/roles/900000000000000511`; assert the new Risk permission subset is respectively exact `536..540` and exact `536`. Do not call the management catalog as Viewer, which correctly lacks role-management permission. Do not change the unrelated audit pagination totals of 12.

- [ ] **Step 4a: Verify the existing local dependency/credential precondition and run the focused database gate before catalog preparation**

Before running this step, the operator must inject non-empty `PLATFORM_SUPPORT_TEST_DB_PASSWORD` and
`PLATFORM_SUPPORT_TEST_REDIS_PASSWORD` into the current process environment through the approved local secret
workflow. The values must match the `mor-local` MySQL `platform_app` and Redis credentials. Do not source, parse,
print, persist or modify `local-environment/.env.local`; only Docker Compose may consume it through `--env-file`.

First run this read-only dependency preflight; it must not start, stop or recreate a container:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
set -euo pipefail
set +x
: "${PLATFORM_SUPPORT_TEST_DB_PASSWORD:?inject the local Platform test database password without echoing it}"
: "${PLATFORM_SUPPORT_TEST_REDIS_PASSWORD:?inject the local Platform test Redis password without echoing it}"
local_platform_compose=(docker compose --env-file local-environment/.env.local \
  -f local-environment/compose.yaml --profile base)
local_platform_mysql_id="$("${local_platform_compose[@]}" ps -a -q mysql)"
local_platform_redis_id="$("${local_platform_compose[@]}" ps -a -q redis)"
test -n "$local_platform_mysql_id"
test -n "$local_platform_redis_id"
test "$(docker inspect --format '{{.Config.Image}}' "$local_platform_mysql_id")" = 'mysql:8.4.11'
test "$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/var/lib/mysql"}}{{.Name}}{{end}}{{end}}' \
  "$local_platform_mysql_id")" = 'mor-local-mysql-data'
test "$(docker inspect --format '{{.Config.Image}}' "$local_platform_redis_id")" = 'redis:8.2.1-alpine'
test "$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/data"}}{{.Name}}{{end}}{{end}}' \
  "$local_platform_redis_id")" = 'mor-local-redis-data'
test "$(docker inspect --format '{{.State.Status}}' "$local_platform_mysql_id")" = 'running'
test "$(docker inspect --format '{{.State.Health.Status}}' "$local_platform_mysql_id")" = 'healthy'
test "$(docker inspect --format '{{.State.Status}}' "$local_platform_redis_id")" = 'running'
test "$(docker inspect --format '{{.State.Health.Status}}' "$local_platform_redis_id")" = 'healthy'
test "$("${local_platform_compose[@]}" port mysql 3306)" = '127.0.0.1:13306'
test "$("${local_platform_compose[@]}" port redis 6379)" = '127.0.0.1:16379'
```

If both exact containers exist with the asserted image tags and named-volume mounts, both named volumes exist, but one
or both containers are stopped, stop and obtain separate explicit authorization before running this narrowly scoped
developer-start command. `compose start` reuses the existing containers and volumes and does not run first-volume
initialization:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
docker compose --env-file local-environment/.env.local \
  -f local-environment/compose.yaml --profile base \
  start mysql redis
```

Before classifying this as a start-only path, verify both exact volumes exist with read-only
`docker volume inspect mor-local-mysql-data mor-local-redis-data`. If either container or volume is absent, do not use
the start authorization: report that `docker compose up` would create/reconcile the containers, network and named
volumes and that a fresh MySQL volume runs the repository base initialization for all configured databases, accounts
and technical schemas. Follow the root `local-environment/README.md` provisioning procedure only after a distinct
explicit base-provision authorization covering those effects. If an image tag or named-volume mount differs, or a
container is running but unhealthy, diagnose that state and request authority for the exact reconcile/recreate or
other corrective action; neither start nor base-provision authority permits an implicit recreate. After an authorized
start, provision or reconciliation, rerun the read-only preflight and continue only when it is green. Then run the
focused gate:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
set -euo pipefail
set +x
: "${PLATFORM_SUPPORT_TEST_DB_PASSWORD:?inject the local Platform test database password without echoing it}"
: "${PLATFORM_SUPPORT_TEST_REDIS_PASSWORD:?inject the local Platform test Redis password without echoing it}"
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
cd platform-support-service
./mvnw -pl platform-support-test -am \
  -Dtest=GatewaySnapshotHttpAutoTest,OperationsAuthenticationHttpAutoTest,OperationsPersistenceContractAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Test code never invokes Docker or reads the env file. A read-only preflight failure, a missing injected variable, a
credential rejection, a non-loopback endpoint or an unavailable dependency is an infrastructure/credential SETUP
failure: stop, repair that exact prerequisite under its own authorization and do not enter Step 4b. Expected: the Maven gate passes only when the separate
port-`13306` local auto-test database already contains the exact new active catalog and no Operations Account, ACTIVE
signing key or reserved audit chain. If it passes, record Steps 4b–4e as not required and continue at Step 5. If and
only if healthy dependencies plus valid injected credentials reach the exact `OperationsTestMapper.SETUP` catalog
failure, continue to the explicit preparation decision; any other test failure uses systematic debugging and never
triggers a database rebuild.

- [ ] **Step 4b: Obtain explicit authorization for the exact local auto-test database rebuild**

This is independent of the port-`23306` `mor-test` rollout. Present the exact targets: keep only the already selected
`mor-local` MySQL and Redis dependencies healthy; enter a bounded maintenance window in which no local Platform runtime
or other `platform_app` client may use `mor_platform`; acquire and continuously hold the existing
`platform-support:auto:global-catalog` MySQL advisory lock; create mode-`0600` backup
`/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/backups/mor-local-platform-before-risk-operations-20260907.sql`;
drop and recreate only database `mor_platform`; apply only the reviewed current Platform Schema and initialization DML;
then release the lock. Retain every other schema/container/volume and all Redis data. Continue only with explicit
authorization for every exact runtime/process stop needed for the maintenance window and for the backup/database
rebuild targets. Never kill a database session automatically. If the backup path already exists, stop and obtain
authorization for a newly specified absolute path; never overwrite or improvise it.

- [ ] **Step 4c: Enter the authorized maintenance window and prove the database is quiescent**

Rerun Step 4a's read-only dependency preflight first. If either dependency is no longer healthy, stop; the database
rebuild authorization does not by itself authorize a container start or recreation. Identify every running local
Platform process/container and every `platform_app` MySQL session. If any exists, stop and either wait for its owner or
use only the exact separately authorized stop action. Confirm with the operator that no such client will be started
until Step 4d releases the advisory lock. The zero-session assertion is repeated after lock acquisition; a one-time
`IS_FREE_LOCK` check is not an acceptable substitute because it has a race.

Use these read-only inventory checks; the MySQL query returns only a count and Docker Compose alone consumes the env
file:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
set -euo pipefail
docker ps --format '{{.ID}}\t{{.Names}}\t{{.Image}}' | rg -i 'platform-support|platform_support' || true
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
"$JAVA_HOME/bin/jps" -l | rg -i 'platform-support|PlatformSupportApplication' || true
local_platform_app_session_count=$(docker compose --env-file local-environment/.env.local \
  -f local-environment/compose.yaml --profile base exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --protocol=socket -uroot --batch --skip-column-names --skip-reconnect --execute="SELECT COUNT(*) FROM information_schema.processlist WHERE USER = '\''platform_app'\''"')
test "$local_platform_app_session_count" = '0'
```

If the count is nonzero, do not print SQL text or kill sessions. Correlate the already inventoried Platform runtime with
its owner, leave the maintenance checkpoint pending and request authority for that exact stop target.

- [ ] **Step 4d: Hold one advisory-lock session across backup and the authorized `mor_platform` rebuild**

Run this whole block as one Bash process. The dedicated MySQL client remains connected through a private FIFO, so the
existing cross-process lock covers backup, `DROP`/`CREATE` and both imports. A failure to acquire the lock or either
nonzero `platform_app` session count stops before `DROP`; cleanup closes the dedicated connection and releases the lock.

```bash
/bin/bash <<'BASH'
set -euo pipefail
set +x
cd /Users/kenny/Desktop/Workspace/Code-MoR

local_platform_backup_path='/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/backups/mor-local-platform-before-risk-operations-20260907.sql'
local_platform_lock_name='platform-support:auto:global-catalog'
local_platform_lock_dir=''
local_platform_lock_fifo=''
local_platform_lock_output=''
local_platform_lock_pid=''
local_platform_lock_writer_open=0
local_platform_backup_tmp=''

local_platform_cleanup() {
  local_platform_exit_status=$?
  trap - EXIT HUP INT TERM
  set +e
  if [ "$local_platform_lock_writer_open" -eq 1 ]; then
    exec 9>&-
  fi
  if [ -n "$local_platform_lock_pid" ] && kill -0 "$local_platform_lock_pid" 2>/dev/null; then
    kill "$local_platform_lock_pid" 2>/dev/null
    wait "$local_platform_lock_pid" 2>/dev/null
  fi
  if [ -n "$local_platform_backup_tmp" ]; then
    rm -f -- "$local_platform_backup_tmp"
  fi
  if [ -n "$local_platform_lock_fifo" ]; then
    rm -f -- "$local_platform_lock_fifo"
  fi
  if [ -n "$local_platform_lock_output" ]; then
    rm -f -- "$local_platform_lock_output"
  fi
  if [ -n "$local_platform_lock_dir" ]; then
    rmdir "$local_platform_lock_dir" 2>/dev/null
  fi
  exit "$local_platform_exit_status"
}

trap local_platform_cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

umask 077
install -d -m 700 test-environment/private test-environment/private/backups
test ! -e "$local_platform_backup_path"
test ! -L "$local_platform_backup_path"
local_platform_lock_dir=$(mktemp -d 'test-environment/private/platform-rebuild-lock.XXXXXX')
local_platform_lock_fifo="$local_platform_lock_dir/mysql.stdin"
local_platform_lock_output="$local_platform_lock_dir/mysql.stdout"
mkfifo "$local_platform_lock_fifo"
: > "$local_platform_lock_output"
chmod 600 "$local_platform_lock_output"

docker compose --env-file local-environment/.env.local \
  -f local-environment/compose.yaml --profile base exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --protocol=socket -uroot --batch --skip-column-names --unbuffered --skip-reconnect' \
  < "$local_platform_lock_fifo" > "$local_platform_lock_output" &
local_platform_lock_pid=$!
exec 9>"$local_platform_lock_fifo"
local_platform_lock_writer_open=1

printf 'SELECT GET_LOCK(%s, 0);\n' "'${local_platform_lock_name}'" >&9
local_platform_wait_attempt=0
while [ ! -s "$local_platform_lock_output" ] && \
    kill -0 "$local_platform_lock_pid" 2>/dev/null && \
    [ "$local_platform_wait_attempt" -lt 100 ]; do
  sleep 0.1
  local_platform_wait_attempt=$((local_platform_wait_attempt + 1))
done
test "$(sed -n '1p' "$local_platform_lock_output" | tr -d '\r')" = '1'

printf '%s\n' "SELECT COUNT(*) FROM information_schema.processlist WHERE USER = 'platform_app';" >&9
local_platform_wait_attempt=0
while [ "$(wc -l < "$local_platform_lock_output" | tr -d '[:space:]')" -lt 2 ] && \
    kill -0 "$local_platform_lock_pid" 2>/dev/null && \
    [ "$local_platform_wait_attempt" -lt 100 ]; do
  sleep 0.1
  local_platform_wait_attempt=$((local_platform_wait_attempt + 1))
done
test "$(sed -n '2p' "$local_platform_lock_output" | tr -d '\r')" = '0'

local_platform_backup_tmp=$(mktemp "${local_platform_backup_path}.tmp.XXXXXX")
chmod 600 "$local_platform_backup_tmp"
docker compose --env-file local-environment/.env.local \
  -f local-environment/compose.yaml --profile base exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump --protocol=socket -uroot --single-transaction --quick --skip-lock-tables --no-tablespaces --default-character-set=utf8mb4 --hex-blob mor_platform' \
  > "$local_platform_backup_tmp"
test -s "$local_platform_backup_tmp"
local_platform_backup_sha256=$(shasum -a 256 "$local_platform_backup_tmp" | awk '{print $1}')
ln "$local_platform_backup_tmp" "$local_platform_backup_path"
rm -f -- "$local_platform_backup_tmp"
local_platform_backup_tmp=''
test -f "$local_platform_backup_path"
test ! -L "$local_platform_backup_path"
test "$(stat -f '%Lp' "$local_platform_backup_path")" = '600'
test "$(stat -f '%u' "$local_platform_backup_path")" = "$(id -u)"
test "$local_platform_backup_sha256" = \
  "$(shasum -a 256 "$local_platform_backup_path" | awk '{print $1}')"
printf 'Backup SHA-256 (record before DROP): %s\n' "$local_platform_backup_sha256"

printf '%s\n' "SELECT COUNT(*) FROM information_schema.processlist WHERE USER = 'platform_app';" >&9
local_platform_wait_attempt=0
while [ "$(wc -l < "$local_platform_lock_output" | tr -d '[:space:]')" -lt 3 ] && \
    kill -0 "$local_platform_lock_pid" 2>/dev/null && \
    [ "$local_platform_wait_attempt" -lt 100 ]; do
  sleep 0.1
  local_platform_wait_attempt=$((local_platform_wait_attempt + 1))
done
test "$(sed -n '3p' "$local_platform_lock_output" | tr -d '\r')" = '0'

printf 'SELECT IS_USED_LOCK(%s) = CONNECTION_ID();\n' "'${local_platform_lock_name}'" >&9
local_platform_wait_attempt=0
while [ "$(wc -l < "$local_platform_lock_output" | tr -d '[:space:]')" -lt 4 ] && \
    kill -0 "$local_platform_lock_pid" 2>/dev/null && \
    [ "$local_platform_wait_attempt" -lt 100 ]; do
  sleep 0.1
  local_platform_wait_attempt=$((local_platform_wait_attempt + 1))
done
test "$(sed -n '4p' "$local_platform_lock_output" | tr -d '\r')" = '1'

{
  printf '%s\n' \
    'DROP DATABASE `mor_platform`;' \
    'CREATE DATABASE `mor_platform` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;' \
    'USE `mor_platform`;'
  cat platform-support-service/docs/database/sql/platform-support-schema.sql
  printf '\n'
  cat platform-support-service/docs/database/sql/platform-support-initial-data.sql
  printf '\nSELECT RELEASE_LOCK(%s);\n' "'${local_platform_lock_name}'"
  printf '%s\n' 'quit'
} >&9
exec 9>&-
local_platform_lock_writer_open=0
wait "$local_platform_lock_pid"
local_platform_lock_pid=''
test "$(wc -l < "$local_platform_lock_output" | tr -d '[:space:]')" -eq 5
test "$(sed -n '5p' "$local_platform_lock_output" | tr -d '\r')" = '1'
printf '%s\n' 'Platform local auto-test database rebuild completed and advisory lock released.'
BASH
```

Capture the printed backup hash before the script reaches `DROP`; command output remains the recovery authority even if
a later statement fails. Record only that hash, never dump contents, credentials or session details. Do not continue if the lock cannot be
held or either import fails. Retain the backup. Any failure after `DROP` enters the following recovery terminal path as
a new destructive action requiring separate authorization; never auto-restore or touch another database.

Before recovery, re-enter the same separately authorized Platform maintenance window, rerun the complete Step 4a
read-only infrastructure preflight and complete Step 4c's quiescence confirmation. Export
`LOCAL_PLATFORM_BACKUP_SHA256` as the exact hash printed before `DROP` by the failed Step 4d invocation, then run this
entire self-contained block. It validates the backup before any destructive statement and holds the same advisory lock
through the complete restore:

```bash
/bin/bash <<'BASH'
set -euo pipefail
set +x
cd /Users/kenny/Desktop/Workspace/Code-MoR

local_platform_backup_path='/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/backups/mor-local-platform-before-risk-operations-20260907.sql'
local_platform_expected_backup_sha256="${LOCAL_PLATFORM_BACKUP_SHA256:?set this to the exact pre-DROP SHA-256 from Step 4d}"
local_platform_lock_name='platform-support:auto:global-catalog'
local_platform_restore_lock_dir=''
local_platform_restore_lock_fifo=''
local_platform_restore_lock_output=''
local_platform_restore_lock_pid=''
local_platform_restore_lock_writer_open=0

local_platform_restore_cleanup() {
  local_platform_restore_exit_status=$?
  trap - EXIT HUP INT TERM
  set +e
  if [ "$local_platform_restore_lock_writer_open" -eq 1 ]; then
    exec 9>&-
  fi
  if [ -n "$local_platform_restore_lock_pid" ] && \
      kill -0 "$local_platform_restore_lock_pid" 2>/dev/null; then
    kill "$local_platform_restore_lock_pid" 2>/dev/null
    wait "$local_platform_restore_lock_pid" 2>/dev/null
  fi
  if [ -n "$local_platform_restore_lock_fifo" ]; then
    rm -f -- "$local_platform_restore_lock_fifo"
  fi
  if [ -n "$local_platform_restore_lock_output" ]; then
    rm -f -- "$local_platform_restore_lock_output"
  fi
  if [ -n "$local_platform_restore_lock_dir" ]; then
    rmdir "$local_platform_restore_lock_dir" 2>/dev/null
  fi
  exit "$local_platform_restore_exit_status"
}

trap local_platform_restore_cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

test -f "$local_platform_backup_path"
test ! -L "$local_platform_backup_path"
test -s "$local_platform_backup_path"
test "$(stat -f '%Lp' "$local_platform_backup_path")" = '600'
test "$(stat -f '%u' "$local_platform_backup_path")" = "$(id -u)"
test "${#local_platform_expected_backup_sha256}" -eq 64
case "$local_platform_expected_backup_sha256" in
  *[!0-9a-f]*) exit 2 ;;
esac
test "$(shasum -a 256 "$local_platform_backup_path" | awk '{print $1}')" = \
  "$local_platform_expected_backup_sha256"

umask 077
install -d -m 700 test-environment/private
local_platform_restore_lock_dir=$(mktemp -d 'test-environment/private/platform-restore-lock.XXXXXX')
local_platform_restore_lock_fifo="$local_platform_restore_lock_dir/mysql.stdin"
local_platform_restore_lock_output="$local_platform_restore_lock_dir/mysql.stdout"
mkfifo "$local_platform_restore_lock_fifo"
: > "$local_platform_restore_lock_output"
chmod 600 "$local_platform_restore_lock_output"

docker compose --env-file local-environment/.env.local \
  -f local-environment/compose.yaml --profile base exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --protocol=socket -uroot --batch --skip-column-names --unbuffered --skip-reconnect' \
  < "$local_platform_restore_lock_fifo" > "$local_platform_restore_lock_output" &
local_platform_restore_lock_pid=$!
exec 9>"$local_platform_restore_lock_fifo"
local_platform_restore_lock_writer_open=1

printf 'SELECT GET_LOCK(%s, 0);\n' "'${local_platform_lock_name}'" >&9
local_platform_restore_wait_attempt=0
while [ ! -s "$local_platform_restore_lock_output" ] && \
    kill -0 "$local_platform_restore_lock_pid" 2>/dev/null && \
    [ "$local_platform_restore_wait_attempt" -lt 100 ]; do
  sleep 0.1
  local_platform_restore_wait_attempt=$((local_platform_restore_wait_attempt + 1))
done
test "$(sed -n '1p' "$local_platform_restore_lock_output" | tr -d '\r')" = '1'

printf '%s\n' "SELECT COUNT(*) FROM information_schema.processlist WHERE USER = 'platform_app';" >&9
local_platform_restore_wait_attempt=0
while [ "$(wc -l < "$local_platform_restore_lock_output" | tr -d '[:space:]')" -lt 2 ] && \
    kill -0 "$local_platform_restore_lock_pid" 2>/dev/null && \
    [ "$local_platform_restore_wait_attempt" -lt 100 ]; do
  sleep 0.1
  local_platform_restore_wait_attempt=$((local_platform_restore_wait_attempt + 1))
done
test "$(sed -n '2p' "$local_platform_restore_lock_output" | tr -d '\r')" = '0'
test "$(shasum -a 256 "$local_platform_backup_path" | awk '{print $1}')" = \
  "$local_platform_expected_backup_sha256"

printf '%s\n' "SELECT COUNT(*) FROM information_schema.processlist WHERE USER = 'platform_app';" >&9
local_platform_restore_wait_attempt=0
while [ "$(wc -l < "$local_platform_restore_lock_output" | tr -d '[:space:]')" -lt 3 ] && \
    kill -0 "$local_platform_restore_lock_pid" 2>/dev/null && \
    [ "$local_platform_restore_wait_attempt" -lt 100 ]; do
  sleep 0.1
  local_platform_restore_wait_attempt=$((local_platform_restore_wait_attempt + 1))
done
test "$(sed -n '3p' "$local_platform_restore_lock_output" | tr -d '\r')" = '0'

printf 'SELECT IS_USED_LOCK(%s) = CONNECTION_ID();\n' "'${local_platform_lock_name}'" >&9
local_platform_restore_wait_attempt=0
while [ "$(wc -l < "$local_platform_restore_lock_output" | tr -d '[:space:]')" -lt 4 ] && \
    kill -0 "$local_platform_restore_lock_pid" 2>/dev/null && \
    [ "$local_platform_restore_wait_attempt" -lt 100 ]; do
  sleep 0.1
  local_platform_restore_wait_attempt=$((local_platform_restore_wait_attempt + 1))
done
test "$(sed -n '4p' "$local_platform_restore_lock_output" | tr -d '\r')" = '1'

{
  printf '%s\n' \
    'DROP DATABASE `mor_platform`;' \
    'CREATE DATABASE `mor_platform` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;' \
    'USE `mor_platform`;'
  cat "$local_platform_backup_path"
  printf '\nSELECT RELEASE_LOCK(%s);\n' "'${local_platform_lock_name}'"
  printf '%s\n' 'quit'
} >&9
exec 9>&-
local_platform_restore_lock_writer_open=0
wait "$local_platform_restore_lock_pid"
local_platform_restore_lock_pid=''
test "$(wc -l < "$local_platform_restore_lock_output" | tr -d '[:space:]')" -eq 5
test "$(sed -n '5p' "$local_platform_restore_lock_output" | tr -d '\r')" = '1'
printf '%s\n' 'Platform local auto-test database restore completed and advisory lock released.'
BASH
```

Require the restore client to exit zero and the release result to equal one. The cleanup trap closes the dedicated
connection on every failure so MySQL releases the named lock. Do not replace this with separate one-shot `mysql`
commands or a momentary `IS_FREE_LOCK` check.

- [ ] **Step 4e: Re-run the focused database GREEN gate with the same secure precondition**

Run the exact complete Step 4a preflight and Maven command again in a process that has the two required test password
variables securely injected. Require both dependencies to be healthy, all three classes to pass and
`OperationsTestMapper.requirePreparedDatabase()` to prove the complete canonical catalog plus the clean
Account/key/audit precondition. This is evidence for `local-environment` only and says nothing about `mor-test`
rollout or deployment.

- [ ] **Step 5: Run the full Maven gate**

First rerun Step 4a's read-only dependency/credential preflight. If it is not green, stop; this checkpoint never starts
or recreates a container. Then run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
set -euo pipefail
set +x
: "${PLATFORM_SUPPORT_TEST_DB_PASSWORD:?inject the local Platform test database password without echoing it}"
: "${PLATFORM_SUPPORT_TEST_REDIS_PASSWORD:?inject the local Platform test Redis password without echoing it}"
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
cd platform-support-service
./mvnw clean verify
```

Expected: all tests pass when the loopback dependencies are healthy, the securely injected credentials match them and
`mor_platform` has the exact manually prepared canonical data. Classify dependency/credential failures separately from
the canonical-catalog setup gate; do not point the suite at a shared environment or weaken the exact counts.

- [ ] **Step 6: Check the Platform diff**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service
git diff --check
```

Expected: no whitespace error.

- [ ] **Step 7: Update Platform implementation documentation**

In `README.md`, record the five permissions, six resources, nine routes, generic 65,536 ceiling and exact test
evidence. Preserve the secure operator-injected Platform/Redis test-password contract and state that Maven/JUnit never
reads `.env.local` or starts dependencies. State that the canonical DML is not an applied migration and that
existing-environment SQL, route enablement and deployment remain outside this repository track.

- [ ] **Step 8: Inspect the Platform worktree before staging**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service
git status --short
git diff --name-only
```

Expected: every changed or untracked Platform path belongs to the allowlist in Step 9. If another path or an unrelated hunk appears, stop and preserve it; never stage the root planning repository from this nested track.

- [ ] **Step 9: Stage the exact Platform track allowlist only when commit authorization is active**

Run exactly:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service
git add -- \
  docs/database/sql/platform-support-initial-data.sql \
  platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/domain/gatewayconfiguration/GatewayConfigurationCompiler.java \
  platform-support-test/src/test/java/com/paytech/platform/support/auto/PlatformSupportHealthAutoTest.java \
  platform-support-test/src/test/java/com/paytech/platform/support/auto/gatewayconfiguration/GatewayConfigurationCompilerAutoTest.java \
  platform-support-test/src/test/java/com/paytech/platform/support/auto/gatewayconfiguration/GatewaySnapshotHttpAutoTest.java \
  platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsAuthenticationHttpAutoTest.java \
  platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsPagingCatalogContractAutoTest.java \
  platform-support-test/src/test/java/com/paytech/platform/support/auto/operations/OperationsPersistenceContractAutoTest.java \
  platform-support-test/src/test/java/com/paytech/platform/support/test/gatewayconfiguration/GatewayCatalogFixture.java \
  platform-support-test/src/test/java/com/paytech/platform/support/test/operations/OperationsCanonicalCatalogOracle.java \
  platform-support-test/src/test/java/com/paytech/platform/support/test/operations/OperationsHttpChecker.java \
  platform-support-test/src/test/java/com/paytech/platform/support/test/operations/OperationsTestMapper.java \
  README.md
```

- [ ] **Step 10: Validate the exact staged Platform diff**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service
git diff --cached --check
git diff --cached --name-only
git diff --cached
```

Expected: `--name-only` is exactly the 13-path allowlist in Step 9 and the full staged diff contains only this track. Unstage and stop on any extra path or unrelated hunk.

- [ ] **Step 11: Commit the approved staged Platform diff**

Only while the active execution still has explicit commit authorization, run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service
git commit -m "feat(platform-support): register Operations risk rule routes"
```
