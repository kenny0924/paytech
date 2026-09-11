# Risk Operations Rule Configuration Console Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the approved Operations rule-configuration flow from the structured browser builder through Gateway, PLT-I02 and the existing Risk private management APIs, stopping at TEST Candidate static validation.

**Architecture:** This is a five-track plan suite because Platform control-plane data, Gateway transport enforcement, the Operations BFF adapter, the Operations Web builder, and local integration are independently reviewable subsystems. The tracks share the confirmed nine-route browser contract. Platform's database gate closes first against the existing exact `local-environment` auto-test database, or against the same database after a separately authorized scoped rebuild when its catalog is stale; the independent `mor-test` rollout and full-chain smoke run later only when separately authorized. Risk remains the sole owner of rule semantics and persistent state.

**Tech Stack:** Java 25, Maven Wrapper 3.9.16, Spring Boot 4.0.8, Spring MVC, Spring Cloud Gateway WebFlux, `RestClient` plus `@HttpExchange`, Nacos Discovery, MySQL 8.4, React 19.2.8, TypeScript 7.0.2, Vite 8.2.2, Ant Design 6.6.2, Nginx 1.30.4, Python `unittest`, Node `node:test`.

**Spec:** `docs/superpowers/specs/2026-09-06-risk-operations-rule-configuration-console-integration-design.md`

## Global Constraints

- Read the confirmed design and `agent-docs/CURRENT_IMPLEMENTATION.md` before every execution track; the design is target state and the ledger is implementation/deployment fact.
- Preserve the fixed browser chain `Operations Web -> API Gateway -> operations-console-bff -> Platform PLT-I02 -> Risk private API`; never add a public Risk Target or direct browser-to-Risk route.
- Keep Risk as the sole owner of AST validation, canonical hashes, immutable versions, state transitions, idempotency, transactions and eight static checks.
- Keep the UI structured: operators see rules, condition groups, fields, operators, values and actions, never raw JSON/AST, `nodeKey`, `parentNodeKey`, `sequenceNo` or `valueSequence`.
- Keep Rule Set Save at exactly 65,536 UTF-8 bytes; warn at 49,152 bytes; reject from 65,537 bytes. Keep Freeze, Candidate Create and Validate at 16,384 bytes and all five GET routes at zero bytes.
- Raise only the three generic configuration ceilings to 65,536: Platform compiler, Gateway snapshot validator and BFF Gateway-detail validator. Existing route rows retain their configured limits.
- Keep Nginx `client_max_body_size` at 128 KiB and `proxy_read_timeout` at 15 seconds; it is an outer connector envelope, not the business route limit.
- Use five exact Platform permissions, six exact Console Resources and nine exact Gateway routes from spec sections 8 and 9. `PLATFORM_ADMIN` receives all five permissions; `OPERATIONS_VIEWER` receives only `RISK_DECISION_CONFIG_READ`.
- Run PLT-I02 before parsing a Risk command body and before any Risk call. Authorization denial, strict-input failure and Validate-key mismatch must produce zero Risk calls.
- Validate uses exactly `risk-validate-v1-{releaseId}-{expectedVersion}`. The browser creates it and the BFF reconstructs it after PLT-I02; a byte mismatch is a BFF-owned RFC 9457 400.
- The BFF must validate the complete upstream Authoring Context, then remove `handlerCode`, `implementationVersion` and `implementationHash` from the browser projection.
- The Risk HTTP client uses service name `risk-service`, connect timeout 2 seconds, read timeout 4 seconds, no redirects, no write retry and strict RFC 9457 error decoding.
- Uncommitted rule content stays in page memory only and never enters Local Storage, Session Storage, IndexedDB, URL, console output, ordinary logs or telemetry.
- Use TDD for every behavior change. Java integration tests use real loopback HTTP and stateful simulators, never Mockito, `@MockBean`, spies or Testcontainers.
- Do not add Flyway, Liquibase, database `CHECK`, physical `FOREIGN KEY`, startup schema creation or startup seed logic.
- Do not modify Risk tables, Rule Set/Candidate state machines, Commerce, Payment, Client & Contract, Finance or Vault.
- Do not apply SQL, start/rebuild shared containers, deploy, enable routes or publish externally without a separate explicit authorization for those state-changing steps.
- Preserve all existing dirty work. In particular, do not reset or overwrite current edits in root `CURRENT_IMPLEMENTATION.md` and `test-environment/**`, Web page/style files, Gateway `README.md`, or the Risk API import-only change.
- Each nested application directory is an independent Git repository. Run status, tests and any authorized commit inside the owning repository; a root commit does not include nested repository changes.
- A passing focused test is not a passing full suite. Record baseline failures separately and never describe SQL-file presence or disabled routes as deployment.

---

## Plan Suite and Dependency Order

| Order | Track | Plan | Independently testable result |
|---:|---|---|---|
| 1 | Platform control plane | `docs/superpowers/plans/2026-09-07-risk-operations-platform-control-plane.md` | Exact permissions/resources/routes in canonical DML and a 65,536-byte compiler ceiling |
| 2A | Gateway boundary | `docs/superpowers/plans/2026-09-07-risk-operations-gateway-boundary.md` | Two approved downstream roots, nine-route enforcement and exact 413 behavior |
| 2B | Operations BFF | `docs/superpowers/plans/2026-09-07-risk-operations-bff-adapter.md` | Nine browser endpoints with PLT-I02-first authorization and one strict Risk call each |
| 3 | Operations Web | `docs/superpowers/plans/2026-09-07-risk-operations-rule-builder-web.md` | Structured builder, version-head inference, TEST Candidate UI and safe command handling |
| 4 | Risk discovery and local integration | `docs/superpowers/plans/2026-09-07-risk-operations-local-integration.md` | Risk service discovery, disabled incremental SQL, 128 KiB edge envelope and an authorization-gated full local smoke |

Gateway and BFF tracks may run in parallel after the route contract and Platform IDs are frozen. Web may begin its pure builder/model work in parallel, but its live API wiring waits for the BFF browser models. Local integration runs last.

### Task 1: Freeze Baselines and Protect Concurrent Work

**Files:**
- Inspect: `/Users/kenny/Desktop/Workspace/Code-MoR`
- Inspect: `/Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service`
- Inspect: `/Users/kenny/Desktop/Workspace/Code-MoR/api-gateway`
- Inspect: `/Users/kenny/Desktop/Workspace/Code-MoR/operations-console-bff`
- Inspect: `/Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web`
- Inspect: `/Users/kenny/Desktop/Workspace/Code-MoR/risk-compliance-service`

**Interfaces:**
- Consumes: the confirmed design, current implementation ledger and six independent working trees.
- Produces: one recorded baseline containing repository HEADs, dirty paths and test results that later reviews use to distinguish introduced failures from pre-existing failures.

- [ ] **Step 1: Record exact repository state without changing it**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
git status --short && git rev-parse HEAD
for repository in platform-support-service api-gateway operations-console-bff operations-console-web risk-compliance-service; do
  git -C "$repository" status --short
  git -C "$repository" rev-parse HEAD
done
```

Expected: status reports may be dirty, but no command changes, stashes or cleans any path.

- [ ] **Step 2: Capture the Platform pre-edit Maven baseline**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
cd /Users/kenny/Desktop/Workspace/Code-MoR/platform-support-service
./mvnw clean verify
```

Record the exit status and every failing class/method. If the fail-fast database prerequisite reports that repository-owned loopback infrastructure is unavailable, record that exact pending prerequisite without weakening the test.

- [ ] **Step 3: Capture the Gateway pre-edit Maven baseline**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
cd /Users/kenny/Desktop/Workspace/Code-MoR/api-gateway
./mvnw clean verify
```

Record the exit status and every failing class/method; this is the only evidence later permitted for baseline attribution.

- [ ] **Step 4: Capture the BFF pre-edit Maven baseline**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-bff
./mvnw clean verify
```

Record the exit status and every failing class/method.

- [ ] **Step 5: Capture the Risk pre-edit Maven baseline**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
cd /Users/kenny/Desktop/Workspace/Code-MoR/risk-compliance-service
./mvnw clean verify
```

Record the exit status and every failing class/method; preserve the existing import-only API working-tree change.

- [ ] **Step 6: Capture the Web pre-edit test/build baseline**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web
npm test
npm run build
```

Record each command separately; a failure in one does not authorize skipping the other.

- [ ] **Step 7: Capture the root static baseline**

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
npm --prefix requirements-site test
python3 -m unittest discover -s test-environment/scripts -p 'test_*.py'
./test-environment/compose.sh config --quiet
git diff --check
```

Record each exit status without starting services or applying SQL.

- [ ] **Step 8: Establish single-writer ownership for overlapping paths**

During execution, assign only the local-integration track to root `test-environment/**`; reserve `agent-docs/CURRENT_IMPLEMENTATION.md` exclusively for this umbrella plan's Task 3 Step 8 after the local track has assembled its evidence pack. Assign only the Web track to `src/App.tsx` and `src/pages/Roles.tsx`; assign only the Gateway track to `api-gateway/README.md`. Treat the existing untracked Web `src/pages/pageHeading.test.ts` as out of scope: do not edit or stage it. Review the existing diff immediately before each patch and preserve all unrelated hunks.

### Task 2: Execute Component Tracks at Their Own Test Gates

**Files:**
- Read: the five plan files listed above

**Interfaces:**
- Consumes: Task 1 baseline and the shared route/permission/size constants in this plan.
- Produces: six working-tree diffs (root integration plus five nested application repositories) whose available focused gates pass independently and whose public contracts agree exactly.

This task is only a dependency and execution index. It intentionally has no checkboxes: execute and check off one bite-sized child-plan checkbox at a time, and never treat an umbrella entry below as an implementation unit or skip a child plan’s RED/GREEN checkpoints.

#### 2.1 Platform source/static work comes first

Complete Platform Tasks 1–4 and Task 5 Steps 1–3. Run the static/compiler gates; if a database-backed HTTP class reports the exact new-catalog setup prerequisite, record it as pending rather than weakening or calling it green. Freeze these ID ranges before another track writes fixtures:

```text
Permission       900000000000000536..900000000000000540
Role-Permission  900000000000000581..900000000000000586
Console Resource 900000000000000565..900000000000000570
Gateway Route    900000000000001024..900000000000001032
Route Header     900000000000005100..900000000000005133
Query Parameter  900000000000004031..900000000000004039
Guard Rule       900000000000006110..900000000000006163
```

#### 2.2 Gateway and BFF may then run independently

Complete Gateway Tasks 1–4 and Task 5 Steps 1–9, plus BFF Tasks 1–5 and Task 6 Steps 1–10. These are the two tracks' implementation, full verification, diff and README checkpoints; leave their inspect/stage/review/commit checkboxes pending for the Commit orchestration index below. Their shared external/downstream paths, route codes, permission codes, timeouts and body limits must match spec section 8 byte-for-byte; neither implementation may infer a wildcard route.

#### 2.3 Web runs against the frozen browser contract

Complete Web Tasks 1–4 and Task 5 Steps 1–10. Leave Task 5 Steps 11–12 pending for the Commit orchestration index. Keep Risk feature styles in `src/risk/riskRuleConfiguration.css`, merge only narrow additions into shared `App.tsx` and the already-dirty `Roles.tsx`, and leave the untracked page-heading test untouched.

#### 2.4 Risk discovery and local-integration preparation run last

Complete Local-integration Task 1 Steps 1–7, Tasks 2–6, and Task 7 Step 1. Leave Risk discovery staging/commit (Task 1 Steps 8–10) for the Commit orchestration index. Stop before Task 7 Step 2 and every SQL application, Compose update, route enablement or smoke that mutates running services unless the user explicitly authorizes those actions in the active execution turn.

#### 2.5 Close Platform against the separate clean auto-test database

Run Platform Task 5 Step 4a against only `local-environment` port `13306`; it is a different database from the port-`23306` `mor-test` rollout and can never be satisfied by synthetic deployment fixtures. Step 4a first performs a read-only check of the existing MySQL/Redis containers, volumes, images, health and Loopback ports. Existing stopped containers may use its exact `compose start` command only after a separate explicit local-dependency-start authorization; an absent container/volume requires the broader base-provision authorization and root `local-environment/README.md` procedure, while an image/mount drift or unhealthy running container requires diagnosis and authority for the exact corrective action. Rerun the preflight after any authorized preparation. If the focused gate passes, continue with Step 5. If it fails only with the exact catalog setup prerequisite, execute Steps 4b–4e solely after their separate destructive local-test-database authorization, including any exact Platform runtime stop and the uninterrupted advisory-lock maintenance window. Once the focused and full database gates pass, continue Platform Task 5 Steps 6–7. Leave staging/commit Steps 8–11 for the Commit orchestration index. If the clean auto-test database cannot be prepared, keep Platform closeout and every suite commit pending rather than weakening the precondition.

#### 2.6 Run the optional local rollout only after all component gates

After Platform Task 5 Step 7 and every other component's pre-staging verification are green, a separately authorized local rollout may complete Local-integration Task 7 Steps 2–13, Task 8 and Task 9 Steps 1–10. Without that authorization, leave those mutation/smoke checkpoints pending; this does not turn prepared SQL or component tests into deployment evidence.

### Task 3: Run the Cross-Repository Contract Gate

**Files:**
- Verify: all files changed by the five execution tracks
- Modify only after all code gates pass: `agent-docs/CURRENT_IMPLEMENTATION.md`

**Interfaces:**
- Consumes: component diffs and their focused/full test evidence.
- Produces: one evidence table that distinguishes component verification, SQL application, local rollout and shared/production deployment without conflation.

- [ ] **Step 1: Run the canonical requirements gate**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
npm --prefix requirements-site test
```

Expected: the canonical requirements and sequence catalog tests pass.

- [ ] **Step 2: Run the Nginx/Gateway Problem boundary gate**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 test-environment/scripts/test_nginx_gateway_problem_boundary.py
```

Expected: the static edge/Problem boundary test passes without starting services.

- [ ] **Step 3: Run the complete local-integration Python unit suite**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
python3 -m unittest discover -s test-environment/scripts -p 'test_*.py'
```

Expected: every repository-owned local-integration unit test passes without applying SQL.

- [ ] **Step 4: Validate the Compose model without starting containers**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
./test-environment/compose.sh config --quiet
```

Expected: Compose configuration is valid and no service starts.

- [ ] **Step 5: Check the root working-tree diff**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
git diff --check
```

Expected: the root working-tree diff has no whitespace errors.

- [ ] **Step 6: Check each nested repository diff**

Run:

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
for repository in platform-support-service api-gateway operations-console-bff operations-console-web risk-compliance-service; do
  git -C "$repository" diff --check
done
```

Expected: every nested working-tree diff has no whitespace errors.

- [ ] **Step 7: Compare all constants across repositories**

Run exact searches and verify only the Save route uses 65,536 while Freeze/Create/Validate use 16,384 and GET uses zero:

```bash
rg -n "operations-risk-|65536|65_536|49152|49_152|131072|128k|risk-validate-v1" \
  platform-support-service api-gateway operations-console-bff operations-console-web test-environment
```

Expected: no 1 MiB route recommendation, public Risk target, generic `/risk/` forwarding root or browser Handler fingerprint exists in implementation files.

- [ ] **Step 8: Update the implementation ledger once from the final evidence**

This is the sole ledger-writing checkpoint for the suite. First review the current dirty diff. When commit authorization is active and the Platform component-verification prerequisite in section 2.5 is green, complete rows 1–5 of the Commit orchestration index below before this ledger write and use their final hashes; when either condition is absent, leave all component commits pending and use each current HEAD plus diff identity. If the authorized local rollout reached Local-integration Task 9 Step 10, consume that fresh evidence pack and record the actual local SQL/deployment/smoke states; if rollout authorization was absent, record incremental SQL `prepared/not applied`, local smoke `not run` and local deployment `not deployed`. In both branches keep shared/production deployment, Shadow, approval, production activation, PRODUCTION Release and runtime evaluation explicitly unimplemented. Append one narrow entry and never perform a second ledger write from the local child plan. Only after this write may an eligible execution advance to row 6.

#### Commit orchestration index

This index introduces no additional commit checkbox. Use it only when commit authorization is active and section 2.5's Platform component-verification gate is green; local rollout authorization is not a commit prerequisite and its actual state is recorded separately. Otherwise every row remains pending. When eligible, complete rows 1–5 through each child plan’s own inspect/stage/review/commit checkboxes, return to Task 3 Step 8 for the sole ledger write, and only then complete row 6. Record each resulting commit hash before advancing; never replace a child plan’s explicit allowlist or interactive staging rules with a repository-wide `git add`.

| Order | Repository / owner | Child-plan commit checkpoint | Commit message |
|---:|---|---|---|
| 1 | `platform-support-service` | Platform Task 5 Steps 8–11 | `feat(platform-support): register Operations risk rule routes` |
| 2 | `api-gateway` | Gateway Task 5 Steps 10–14 | `feat(api-gateway): enforce Operations risk rule routes` |
| 3 | `operations-console-bff` | BFF Task 6 Steps 11–15 | `feat(operations-bff): proxy Risk rule configuration` |
| 4 | `operations-console-web` | Web Task 5 Steps 11–12 | `feat(operations-web): add structured Risk rule builder` |
| 5 | `risk-compliance-service` | Local-integration Task 1 Steps 8–10 | `feat(risk): register private service discovery` |
| 6 | root integration | Local-integration Task 9 Steps 11–16, after Umbrella Task 3 Step 8 has written the ledger | `test(environment): prepare Risk rule configuration chain` |

If either commit authorization or the section 2.5 Platform verification prerequisite is absent, leave every child commit checkpoint pending and record working-tree revisions only. Root is always last because it owns the plan suite, integration artifacts and evidence ledger.

## Spec Coverage Matrix

| Spec sections | Owning track/task |
|---|---|
| 1–5 authority, scope and trust boundaries | This plan Global Constraints; all track headers |
| 6 page, head inference and Candidate states | Web Tasks 3–5 |
| 7 Authoring Context, catalog and AST conversion | BFF Tasks 2–3; Web Tasks 1–2 |
| 8 nine routes and browser/BFF API | Platform Tasks 2 and 4; Gateway Tasks 2–4; BFF Tasks 3–5 |
| 9 permissions and Console Resources | Platform Tasks 2–4; Web Tasks 4–5 |
| 10 Risk client, strict JSON, errors and idempotency | BFF Tasks 1–5; Web Task 3 |
| 11 command/query flows | BFF Tasks 4–5; Web Tasks 3–5 |
| 12 concurrency, errors and security | BFF Task 5; Web Tasks 3 and 5; Integration Tasks 5–9 |
| 13 SQL and three generic ceilings | Platform Tasks 1–4; Gateway Task 1; BFF Task 6; Integration Tasks 2, 4 and 5 |
| 14 tests | Every track’s focused and full verification task |
| 15 documentation consistency | Every track’s README task; Local-integration Task 9 Step 10 evidence pack; Umbrella Task 3 Step 8 sole ledger write |
| 16 future enable order | Integration Tasks 7–9 |
| 17 Definition of Done | Task 3 evidence gate plus Integration Task 9 Steps 1–9 when separately authorized |
