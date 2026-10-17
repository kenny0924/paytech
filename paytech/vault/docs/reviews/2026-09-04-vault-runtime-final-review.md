# Vault runtime implementation and final-review evidence

Date: 2026-09-04. Scope: the approved local/test Go server and Java Client, not production acceptance.

## Status and authority

Tasks 1–12 completed their independent task reviews at Vault `a7d1c09` and root governance `24d3e77`. The final whole-branch review found one Important and three Minor issues; the single correction wave and independent scoped re-review closed all four with no new breakage. **Scoped review and controller fresh final verification are PASS** at Vault `cd03141` and root `2097efc`. No code-review findings remain open for the local/test delivery. Real Nacos acceptance is NOT RUN and PDManer GUI review is PENDING; production remains excluded.

Runtime/API authority is the root repository's `docs/superpowers/specs/2026-09-04-vault-go-server-java-client-design.md`; the preceding `2026-09-02-vault-database-design.md` retains the three-table contract. See [runtime prerequisites and commands](../../README.md), [OpenAPI](../../api/openapi/v1.yaml) and [physical decisions](../database/VAULT_PHYSICAL_DECISIONS.md). Historical Java/MVC, Binding, mTLS, Platform-audit and broad lifecycle descriptions do not override these authorities.

Implemented: Go 1.27.1 server, Protect/Reveal, immutable Allowlist, LOCAL AES, transactional MySQL persistence and local audit, strict private HTTP, whitelist telemetry, optional registration-only Nacos adapter, publishable Java Client and nonpublished cross-language tests. LOCAL AES is single-instance `local/test` only; production cannot start without an approved Key Provider. Required `X-Caller-Service` is spoofable, not strong workload authentication.

Not implemented/authorized here: cloud KMS/Secrets integration, rotation/destruction/DR, business consumer adapters or Token fields, Gateway/BFF routes, MQ/Redis/compensation, Platform audit relay, production topology or deployment.

## Reviewed implementation checkpoints

Ranges use exclusive base and inclusive head; task numbering differs from execution order because Task 10 proceeded while schema approval was pending.

| Task | Vault commit range | Accepted scope |
|---|---|---|
| 1 | `30fd356..7a4ffa0` | Automatic model gate and truthful GUI PENDING record |
| 2 | `7a4ffa0..70f6a32` | OpenAPI, golden vectors, manual Schema contract |
| 3 | `70f6a32..2588c72` | Domain, canonical encoding, IDs and Token format |
| 4 | `2588c72..4fb32b5` | Strict config/policy and secret redaction |
| 5 | `4fb32b5..c5e4e14` | KeyService and LOCAL AES |
| 10 | `c5e4e14..1e7b646` | Java Client and 23 transport/config tests |
| 6 | `1e7b646..86a9906` | MySQL adapter, transaction termination, owned cleanup |
| 7 | `86a9906..91d906a` | Application use cases, audit and concurrency |
| 8 | `91d906a..428fec0` | HTTP, telemetry and bounded assembly cleanup |
| 9 | `428fec0..03ad380` | Nacos lifecycle and ambient-credential guard |
| 11 | `03ad380..181fb8f` | Cross-language acceptance and verifier prerequisites |
| 12 | `181fb8f..a7d1c09` | Documentation/evidence synchronization; root `61e62b2..24d3e77`; independent review clean |

## Final whole-branch review and consolidated corrections

The independent reviewer examined exact ranges **Vault `30fd356..a7d1c09`** and **root governance `c8d6812..24d3e77`**, with both heads and clean worktrees confirmed. It reviewed all 15 binding runtime-design sections in bounded specification and quality passes. This was a read-only source/document review, not a new test execution; its verification statements cited existing task/controller evidence.

Verdict: specification compliant subject to one Important correction, three Minor corrections and unavailable manual acceptance gates; code quality / merge readiness **With fixes**. No Critical or other Important findings. The earlier Task 2 strict-HTTP observation is closed by Task 8's exact case-sensitive fields, duplicate/escaped-duplicate rejection and wire regressions; it is not an open finding.

| Finding | Severity | Single-wave correction |
|---|---|---|
| F1: policy struct decoding accepted case aliases and last-value overwrites | Important / P2 | Exact decoded-name allowlists at root, Data Type policy and rule objects before typed decoding; dynamic Data Type keys remain exact and duplicate checks remain. Alias-only, both orders, invalid-first/valid-last and escaped-duplicate regressions added. |
| F2: AAD fixture record ID was hardcoded | Minor / P3 | Parse the shared fixture's positive base-10 int64 ID and consume it in AAD. Temporary ID-only mutation now fails; malformed/overflow/nonpositive IDs fail safely. Fixture restored unchanged. Current published bytes were correct. |
| F3: application idempotency-key grammar was broader than HTTP/Java | Minor / P3 | Use `^[A-Za-z0-9._~-]{1,128}$`; reject printable disallowed characters before keys/business writes while retaining denial audit and valid boundary/replay behavior. HTTP request-ID fallback is separate and unchanged. No external bypass was found. |
| F4: local guide advertised configurable sampling | Minor / P3 | Document fixed `ParentBased(AlwaysSample())`, parent-decision inheritance and sampled roots; sampler environment variables are not applied. Exporter configuration remains standard; no runtime sampling feature added. |

The independent scoped reviewer examined exact correction ranges **Vault `a7d1c09..cd03141`** and **root `24d3e77..2097efc`**. F1, F2, F3 and F4 were each **ADDRESSED**; new breakage and out-of-scope observations were **None**. Specification and code-quality verdicts are clear, with no residual Critical/Important/Minor finding. The reviewer checked source and recorded RED/GREEN evidence without rerunning suites. No second broad review, production extension, business integration or manual-gate substitution is implied.

## Physical database evidence

On 2026-09-04, after separate explicit user approval, a read-only recheck confirmed MySQL 8.4.11 and zero tables in the existing `mor_vault`. Exactly [vault-schema.sql](../database/sql/vault-schema.sql) was applied manually using the scoped `vault_app` account. SHA-256: `bd18057813f213b08061d68cad188940217e928aee14583fdbeacbf839ad37f6`.

Post-application inspection: exactly `t_vault_record`, `t_vault_protect_request`, `t_vault_access_event`; 30 columns, 3 primary keys, 3 nonprimary unique indexes, 2 foreign keys and 4 enforced CHECKs; each table initially had 0 rows. No initial DML, startup migration, seed, other database or root-account operation occurred. Task 12 does not reapply DDL.

Unchanged model SHA-256: `c9803868eabf0ad357a88443eda2ae8d30f88e39bafeb75b0f80888139de55f9`. Model counts remain 3 tables / 30 fields / 3 nonprimary unique indexes / 4 access indexes / 2 local n:1 relations / 0 one-to-one / 1 diagram group.

## Automatic verification evidence

Task 11's final implementation `181fb8f` passed the full automatic gate after its three review corrections. Java totals: **32 tests, 0 failures, 0 errors, 0 skipped** (23 Client + 9 E2E/lifecycle/prerequisite tests). All three tables had 0 remaining rows afterward. Actual MySQL 1205 timeout/rollback is covered; 1213 deadlock and lost Commit responses use deterministic driver simulation, not a claim of induced real database failures.

The verifier uses Go 1.27.1 with `GOTOOLCHAIN=local`, JDK 25, Python 3, at least 15 GiB free disk, a separately prepared Foundation artifact repository and already-cached Maven 3.9.16 wrapper. No tool installation, private-settings parsing, cache deletion or snapshot refresh is performed. Secrets are supplied only through the process environment and are never printed; required names are `VAULT_TEST_DB_DSN`, `VAULT_TEST_JDBC_URL`, `VAULT_TEST_JDBC_USER`, `VAULT_TEST_JDBC_PASSWORD`.

```sh
GO_BIN=/tmp/vault-go-toolchain.9EPL02/go/bin/go \
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home \
VAULT_MAVEN_REPOSITORY=/Users/kenny/.m3/repository ./scripts/verify.sh
```

These task-specific paths are evidence, not a promise that temporary toolchains survive reboot. The Maven artifact repository is distinct from the wrapper distribution cache.

| Gate | Executed command or check inside verify.sh |
|---|---|
| Exact live Schema | `go test ./internal/persistence/mysql -run '^TestIntegrationSchemaExactContract$' -count=1 -timeout=30s` |
| OpenAPI/golden bytes | `go test ./internal/contract ./internal/domain -count=1 -timeout=60s` |
| Formatting | `gofmt -l` on all Go sources; empty result |
| Static analysis | `go vet ./...` |
| Unit + real MySQL integration | `go test ./... -count=1 -timeout=120s` |
| Race + real MySQL integration | `go test -race ./... -count=1 -timeout=120s` |
| Actual binary | `go build -o <owned-temp>/vault-server ./cmd/vault-server` |
| Java Client and actual Go/MySQL black box | `./mvnw -B -ntp --no-snapshot-updates -Dmaven.repo.local=<prepared-repository> clean verify` |
| Sensitive canaries | Captured process output and Surefire reports scanned; no matching contents printed |
| Whitespace | `git diff --check` |

### Task 12 fresh rerun (2026-09-04)

At unchanged runtime HEAD `181fb8f`, with only the two intended documentation files uncommitted, one full `./scripts/verify.sh` invocation exited 0. Safe output:

```text
PASS: mysql-schema-preflight
PASS: openapi-golden
PASS: gofmt
PASS: go-vet
PASS: go-test
PASS: go-race
PASS: go-build
PASS: maven-clean-verify
PASS: sensitive-canary-scan
PASS: git-diff-check
PASS: full automatic Vault gate (manual Nacos is separate).
```

Fresh Surefire XML summary: Client 23 + vault-test 9 = **32**, failures/errors/skips **0/0/0**. Post-run read-only scoped `SELECT COUNT(*)` on Record, Protect Request and Access Event returned **0 / 0 / 0**. No overlapping integration-test process, schema change or real Nacos request was used.

Root governance validation at `61e62b2` plus Task 12 documentation changes:

| Command | Safe result |
|---|---|
| `node --test scripts/pdmaner/vault-model.test.mjs` | 11 tests, 11 pass, 0 fail/skip |
| `node scripts/pdmaner/vault/generate-vault-model.mjs --check` | Model up to date; exact 3/30/3/4/2/0/1 counts |
| `npm --prefix requirements-site test` | Build completed; 123 tests, 123 pass, 0 fail/skip |
| `git diff --check` in both repositories | Exit 0, empty output |

The first requirements invocation exited 127 before build (`vinext: command not found`). After confirming the isolated target absent/ignored and both lockfiles SHA-256 `99d4e20c346007dc004833f71f9f9076ea8d9e049f132d3f90a2e3f6cae47d6b`, the already-prepared 464 MiB dependency directory was copied to the isolated worktree, not symlinked or installed. The successful retry used that copy, so build-cache writes did not touch main. The model gate used an ignored real `vault-service/` directory containing only a `docs` symlink to the isolated Vault docs. These worktree-only prerequisites remain untracked for controller verification; they are not repository/runtime changes.

Documentation self-review checked 30 newly added local Markdown link occurrences with independent Vault paths resolved to the isolated repository: 0 missing targets. The controller's precommit OpenAPI filename correction is included. Model and Schema SHA-256 values above remain unchanged. This historical evidence did not replace the subsequently completed broad and scoped reviews.

### Final correction worker verification (2026-09-04)

On Vault base `a7d1c09` plus exactly F1–F3 and this archive, the correction worker ran one complete verifier using the same explicit prepared toolchain/JDK/Maven repository and scoped process-environment credentials described above. All ten stage messages and the final `PASS: full automatic Vault gate (manual Nacos is separate).` were observed; exit 0. Fresh Surefire totals remain **32 tests (23 Client + 9 black-box/lifecycle/prerequisite), 0 failures/errors/skips**. Read-only scoped counts returned **0 / 0 / 0** after owned cleanup. Model and Schema hashes are unchanged.

Before that full gate, policy tests observed 24 accepted case-alias/order regressions and 6 accepted overwrite regressions fail at RED, then pass after the field-name correction. Application tests observed 8 disallowed printable keys wrongly succeed at RED, then pass with DENIED audit/no business writes; allowed 1/128-byte keys retain replay. A temporary fixture-only ID change `42` to `43` falsely passed the old AAD consumer, failed with different AAD bytes after the fix, then passed after restoration; malformed, overflow and zero IDs failed safely. Published vector bytes remain unchanged.

Focused policy/domain/application tests, race and vet passed; the focused application run excluded real-DB tests, while the full verifier ran them. Root `24d3e77` plus F4 passed 11 model tests, no model drift, requirements production build and 123 tests. The existing vinext static route-classification informational limitation remains. Both whitespace checks passed. Read-only Ruling comparison found **23 ledger / 23 archived / 0 missing**. This is implementation-worker evidence, separate from the independent review and controller run below.

### Controller final verification (2026-09-04)

After the correction commits, the controller independently ran the complete `./scripts/verify.sh` at Vault `cd03141`, using the same explicit Go/JDK/prepared Maven prerequisites and process-only scoped credentials documented above. The command exited **0**, with all ten named stage PASS messages and the final full-automatic-gate PASS. This included real MySQL tests, Go race/vet/build, actual Java-to-Go acceptance and sensitive-canary checks; no integration-test worker overlapped the run.

The controller inspected only Surefire testsuite counters: Client **23** plus acceptance **9**, total **32**, failures/errors/skips **0/0/0**. Post-run scoped read-only counts were **0/0/0** for Record/Protect Request/Access Event. Both physical hashes above were independently rechecked unchanged; no DDL was reapplied.

At root `2097efc`, the controller separately executed the exact model test, generator `--check`, `npm --prefix requirements-site test` and whitespace commands listed above: **11 model tests passed, no model drift, production build and 123 requirements tests passed, exit 0**. Vinext's existing static-route-classification informational limitation was observed, not hidden. The only later change is this evidence/status update; runtime, tests, model and schema stay at the verified revisions. Both implementation branches remain separate and unmerged/unpushed pending the user's integration choice.

## Manual gates and final-review follow-up

- Real Nacos 3.1.2 discovery smoke: **NOT RUN**, because explicit client credentials were not supplied. Manual test source compiles, but simulators and auto tests do not prove live discovery. No credentials guessed, namespace/account created or ambient credentials reused.
- PDManer 4.9.3 in-app review: **PENDING**. Automatic model checks and manually approved DDL do not establish GUI compatibility.
- Whole-branch spec then quality review: **COMPLETE**; its initial With fixes verdict is closed by F1–F4's single correction wave. **Independent scoped correction review: PASS. Controller fresh final verification: PASS.** Exact ranges and independent evidence are recorded above; these do not substitute for the unavailable manual gates.
- Nacos readiness is SDK-observed cached/eventually-consistent state. Admission/caller waits are bounded; SDK internals/Close do not prove goroutine, UDP or cache-write quiescence. Private-directory cleanup is best effort; process exit reclaims residual resources. This is not a production health guarantee.
- Enabled Nacos requires safe Namespace/Group grammar and empty Alibaba access-key environment variables before SDK construction. `OTEL_RESOURCE_ATTRIBUTES` must be empty before telemetry construction; panic text is not automatically recorded. Neither boundary silently rewrites environment.
- Java Client performs zero logical/application retries. JDK transport may reconnect or resend peer-reported unprocessed requests; no absolute zero-transport-attempt claim.
- Tests use owned cleanup only. The fixed black-box Snowflake node 31/28 is protected by loopback socket lease 19128 through child termination, private cleanup, SQL cleanup and connection close. It is not service port 18107 or the random HTTP test port; never kill another process to acquire it.

## Rulings I made

This durable archive preserves all 23 controller Ruling decisions and their stated costs from the implementation ledger through the final correction wave, verbatim. Historical circumstances are not new permissions: the early disk concern was later resolved (57 GiB observed), schema execution happened only after the later explicit approval above, and no live Nacos credentials have been supplied. The root test wiring is local-only and must not be staged.

- Ruling: Keep Java mapping of HTTP 500 `INTEGRITY_CHECK_FAILED` as `ErrorException` because the approved Java Client section explicitly maps every non-503 5xx that way; deterministic server semantics do not change the client exception hierarchy — if wrong, callers may handle integrity failure less specifically until the contract is revised.

- Ruling: Enforce test-only `vault.client.base-url` by rejecting it unless Spring profile `test` or `unittest` is active — if wrong, a nonstandard test profile will need explicit alignment.

- Ruling: Continue source implementation with 5.5 GiB free while preserving the specified 15 GiB hard threshold in `scripts/verify.sh`; do not clean user or Docker data — if wrong, dependency/tool downloads may exhaust disk before the final gate.

- Ruling: The user's 2026-09-04 “开始实现吧” after the screen-permission explanation authorizes source, contract, tests and manually applied DDL before the PDManer GUI review. Task 1 now records automatic PASS plus manual PENDING, never a fabricated PASS — if GUI compatibility differs, the model/DDL presentation may need rework before acceptance.

- Ruling: RequestMaterial takes caller separately from ProtectInput, supplied by Attempt.Caller; TxStore adds FindRecordByID for replay. These close concrete missing data paths without changing wire/DB contracts — if wrong, internal signatures need rework only.

- Ruling: Application owns a typed DuplicateError and retryable-contention sentinel; MySQL maps only named 1062 indexes and 1213/1205, and Application retries only after full rollback with a fixed bound. Safe domain error classification separates integrity/dependency failures — if contention policy is insufficient, callers may receive 503 under load; no new external API or database state is introduced.

- Ruling: All three canonical encoders return `([]byte,error)` instead of the plan's no-error signature, rejecting overflow/invalid UTF-8 safely rather than panicking or silently truncating. Record AAD also rejects nonpositive IDs. The wire bytes are unchanged — if wrong, internal callers need adjustment, but request-derived data cannot trigger an encoder panic.

- Ruling: Task8 uses explicit W3C-aware whitelist tracing middleware and removes unused otelhttp. Official0.71.0 defaults always capture URL/path/User-Agent/peer attributes and cannot remove them via a public option; post-processing is not an adequate capture boundary — cost is a small custom middleware and no default HTTP semantic attributes, while trace continuity/OTLP remain supported.

- Ruling: Execute Task10 next while local schema approval is pending; it depends only on the completed Task2 contract, so this changes ordering rather than scope — if later transport integration finds a mismatch, Client receives a normal follow-up fix.

- Ruling: Task6/7 preserve commit uncertainty explicitly: terminal safe sentinels, no retry/second audit/result release after uncertain commit, and connection quarantine after failed termination; duplicate/contention recovery requires successful full rollback. Installed Go/MySQL sources confirm Commit errors are not rollback evidence (see tx-preflight.md) — if too conservative, an available request may receive 503 and need caller retry, but no reconciliation table or public API is added.

- Ruling: “zero retry” is the Client's zero logical HTTP/application retry and no retry interceptor; the selected JDK transport may reconnect or retry a request explicitly reported unprocessed by its peer. It does not expose a supported per-client disable switch; we will document this limit and never claim zero TCP/transport recovery — if absolute transport-attempt control is required later, it needs an approved alternative HTTP transport, not global JVM mutations.

- Ruling: A standalone required audit uses context.WithoutCancel plus a fixed2s timeout, preserving request/trace projection, so caller cancellation does not itself prevent audit to a writable DB. This realizes the approved independent short audit transaction; unknown Commit still permits no second audit — cost if wrong is up to2s additional handler lifetime after cancellation, not another business attempt.

- Ruling: Keep the approved official Nacos Go SDK and interpret local/test readiness as SDK-observed cached/eventually-consistent state; bound adapter admission/caller waits, not uncancelable SDK internals. One client per process, serialized calls, late results cleanup-only, no reconstruction/custom HTTP/fork; ordered directory cleanup best effort and residual resources reclaimed by process exit. Controller verified cache-hit query, Close-before-cancel/blocking channel, idle UDP receive and discarded partial constructor against installed v2.3.5 source — cost if wrong is stale Ready until SDK reports loss and residual resources/cache files until teardown, not a production health guarantee. Spec/Task9 explicitly amended before implementation; user notified.

- Ruling: Reject any non-empty OTEL_RESOURCE_ATTRIBUTES before SDK/exporter construction and fix service.name=vault-service. Do not mutate process environment or filter captured data later; the pinned SDK has no Resource-environment opt-out — cost if wrong is that deployments with otherwise harmless shared Resource attributes must unset that variable for Vault. Also use the supported WithoutPanicRecording option: source span.go411–428 otherwise records panic text during deferred End, bypassing explicit no-RecordError discipline. Panic propagation is unchanged.

- Ruling: Restrict enabled Nacos Namespace/Group to `^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$` before SDK construction. Exact-source preflight and controller inspection confirm Namespace is concatenated into the naming cache path; keep process-owned private cache/log roots and reject rather than normalize configuration — cost if wrong is rejection of existing nonstandard or over64-character identifiers; this is not a malicious-Nacos response defense. Spec/Task9 updated with focused config tests.

- Ruling: When Nacos is enabled, reject either non-empty ALIBABA_CLOUD_ACCESS_KEY_ID or ALIBABA_CLOUD_ACCESS_KEY_SECRET before SDK construction; keep disabled mode unaffected. Public SDK has no provider-chain opt-out, so do not mutate environment or invent dummy credentials/fork — cost if wrong is that a process carrying unrelated Alibaba access keys cannot enable Nacos until those variables are absent. This preserves explicit Nacos-only credential use without activating cloud KMS.

- Ruling: Accept test-only loopback socket lease127.0.0.1:19128 to serialize the fixed31/28 black-box generator across processes; it is not the application/HTTP simulator port, which stays random. Existing port authority has no19128 assignment; no infrastructure service is installed — cost if wrong is a fail-fast test prerequisite when that port is occupied. README/local test authority must document it; never terminate another process to acquire it.

- Ruling: Accept explicit Python3 prerequisite for verify.sh's bounded process-group supervision on this Mac, which lacks GNUtimeout; Python is test tooling only, not a Vault runtime dependency. No tool installation or genericruntimeframework added — cost if wrong is one additional documented verifier prerequisite on developer machines.

- Ruling: Require explicit VAULT_MAVEN_REPOSITORY in verification so the checked prepared parent/BOM/common-core artifacts are the same effective repository passed to Maven, before Maven/DB/test gates; retain prepared snapshots without remote refresh. Avoid private-settings parsing/plugin resolution — cost if wrong is one required test environment variable for callers who previously relied on Maven defaults. Worker will document it and use owned missing-artifact regression directories.

- Ruling: Permit a small opt-in cached-distribution guard in Vault's existing mvnw, enabled onlybyverify.sh afterthewrapper's own cachelookup; failbefore download/install withoutcopyingcachehashlogic, retainnormalmanualwrapper behavior — cost if wrong is one small local wrapper extension to preserve when regenerating the wrapper. Task11filelist/brief expanded narrowly.

- Ruling: If absent, create only an ignored real vault-service directory in the root governance worktree with docs symlink to the isolated Vault worktree's docs; keep it untracked and do not modify ignore rules, generator APIs or main workspaces — existing directory-only ignore pattern and validators require independent docs at the conventional relative path; cost if wrong is a local worktree-only path coupling to remove when those worktrees are retired.

- Ruling: Copy the existing prepared requirements-site/node_modules into the absent ignored isolated-worktree location after matching lockfiles; do not install/download or symlink dependency caches — permits the prescribed root validation while protecting main from build-cache writes; cost if wrong is464MiB additional local disk usage and potentially stale prepared dependencies, exposed by the normal build/test gate.

- Ruling: Correct sampler documentation to the implemented fixed ParentBased(AlwaysSample) behavior rather than add environment-driven sampling — the current local/test use case requires accurate guidance, not a new configuration feature; cost if wrong is that operators needing tunable root sampling must request that capability separately.
