# Platform Support Service

租户访问与成员权限查询的现行合同见 [需求 API](../requirements/20260914/platform-support-租户访问与成员权限查询/api.md)：8 项能力，复用既有 IAM 模型；当前研发、验证和部署状态见 [根台账](../agent-docs/CURRENT_IMPLEMENTATION.md#platform-tenant-access-20260914)。

Client 入驻文件的归属、幂等与扫描边界见[文件合同](../requirements/20260914/client-console-注册与客户入驻/file-upload-closure-proposal.md)；代码、人工 SQL 和本机验证状态统一见[Client 实现台账](../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。

`platform-support-service` is the Platform Support core-service reactor. The current vertical slice implements the
manually maintained MySQL projection, the public-route Snapshot query PLT-I20, Operations Realm JWKS query PLT-I22,
and an explicitly development-only Operations authentication/IAM slice. Authentication is disabled by default;
this checkout does not provide a working login until the manual database and local integration prerequisites below are
supplied.

The full database authority remains
[`docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md`](docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md) and
[`docs/database/platform-support.pdma.json`](docs/database/platform-support.pdma.json). This runtime slice does not
claim that the remaining Platform capabilities are live.

PLT-I03 的内部认证入口为 Access Control 的 `ApiCredentialValidationProcessor`，使用 `@Component` 和构造器注入，
UTC 时钟由应用级 `ApplicationConfiguration` 统一提供，不受 Operations/Client 认证开关影响。入口读取同一只读快照中的
Credential/Tenant 和有效 Scope，事务结束后校验 PHC，再向 Web 返回不含认证材料的 `ApiCredentialIdentityView`。
公开响应使用 `api.apicredential.response.ValidatedCredentialResponseBody` 独立 class；路径、Header、JSON 字段、
401/503 语义和 Gateway 所需的 `Mono` 合同保持不变。Java 消费者重建时同步更新 import 和 getter 调用。
本次重构的验证范围见[当前状态台账](../agent-docs/CURRENT_IMPLEMENTATION.md#platform-api-credential-refactor-20260917)。

Client 认证 A01—A09 的编排入口位于 Access Control `service/processor`，Web Controller/Converter 负责协议适配。
`ClientAuthenticationApi` 的请求和响应是 `api.authentication.request/response` 下的独立 class；Java 消费者须同步
调整 import/getter 并重建，HTTP 路径和 JSON 字段保持不变。本地 Service 接收具名 Context，保存账号、挑战和会话事实；
Vault、密码计算和令牌签名在写事务前完成，邮件在提交后发送。
八个写入口通过 `AbstractLockProcessor` 使用邮箱、账号或会话资源锁，读取 `client.authentication.lock-wait-time`
（默认 `500ms`）和 `client.authentication.lock-lease-time`（默认 `30s`）；配置经启动校验，变更需重启。
Tenant Projection 的双资源锁现由公共 Processor 模板处理，账号 Key 为 `lock:customer:account:{accountId}`。
旧版本没有 `lock:` 前缀，部署时须协调相关写入口版本，不能依赖混合版本间的 Redis 互斥；数据库行锁和条件更新仍保留。
本次实现及实际验证范围见[Client 认证重构台账](../agent-docs/CURRENT_IMPLEMENTATION.md#platform-client-authentication-refactor-20260917)。

Operations 认证的登录、刷新、退出、改密、首次管理员和当前上下文六个入口，同样由 Access Control
`service/processor` 下各自的 Processor 编排；Controller/Converter 只处理协议，公开 Body 为独立 class。
五个写入口通过具名 Context 交给本地 `OperationsAuthenticationService` 原子保存，密码、Vault 和签名准备在事务外完成。
刷新保留单次消费及正确旧代复用撤销会话的安全规则；首次管理员创建与 Bootstrap 关闭在同一事务中完成。
`AbstractLockProcessor` 读取 `operations.authentication.lock-wait-time`（默认 `500ms`）和
`operations.authentication.lock-lease-time`（默认 `30s`），配置启动校验、变更需重启。
账号与会话实际 Key 分别为 `lock:operations:account:{id}`、`lock:operations:session:{id}`；
既有账号管理的四个写入口同步账号前缀。旧版本不带 `lock:`，相关入口发布时需要协调版本，数据库条件更新和行锁仍保留。
Java 消费者须适配 Body import/getter 并重建，HTTP 合同保持。实际验证范围见
[Operations 认证重构台账](../agent-docs/CURRENT_IMPLEMENTATION.md#platform-operations-authentication-refactor-20260917)。

`TenantProjectionApi` 的开通、状态投影和恢复查询由 Access Control 的三个独立 Processor 编排，
Controller/Converter 只做协议适配；API Body 拆为 `tenantprojection.request/response` 下的 class，
CCT Java 消费方同步 import/getter，HTTP 路由与 JSON 字段不变。开通与首次 ACTIVE 角色切换各有具名 Context，
本地 Service 只调用四张表的 Repository；查询不泄露 DataObject。
两个写入口继承 `AbstractLockProcessor`，通过 `LockCommand.tryLocks` 一次协调
`tenant:source:client:{ownerRefId}` 与 `lock:customer:account:{requestedByAccountId}`，不叠加注解锁。
沿用 `redis.coordination.resource-lock.wait-time/lease-time` 并在启动期校验，完整锁键按 ASCII 顺序去重，
共享一次等待预算，取得全部资源后开启事务，失败或事务结束后逆序释放已取得的资源。
三个入口均复用 `AbstractProcessor` 的字段校验和入口事务检查；HTTP `@Valid` 保留协议错误合同。
来源版本和摘要比较属于 CCT 业务事实恢复合同，继续保留；独立安全状态、成员状态及已撤销授权不会被来源重放恢复。
实际验证及限制见 [Tenant Projection 重构台账](../agent-docs/CURRENT_IMPLEMENTATION.md#platform-tenant-projection-refactor-20260917)。

## Modules and dependency chain

- `platform-support-api` contains the dependency-light PLT-I20/PLT-I22 and Operations authentication, authorization and
  management `@HttpExchange` contracts, String-code DTO
  fields and the externally understood Gateway Code enums under `api/enums`.
- `platform-support-access-control` is a non-deployable capability JAR. It owns Operations authentication/IAM,
  Gateway configuration business rules, their DataObjects/Mapper/Repository code and the local signing/Vault ports.
- `platform-support-audit-security` is a non-deployable capability JAR. It owns immutable audit facts, audit queries,
  audit persistence and the independently committed audit writer.
- `platform-support-file-document` is a non-deployable capability JAR. It owns file persistence, upload policy rules
  and immutable-byte verification, scan rules and storage/scanner ports; it does not access IAM tables or depend on other capability implementations.
- `platform-support-web` is the only Spring Boot application and composition root. It owns Controllers,
  the remaining legacy Processors and their transaction/annotation-lock orchestration, Snapshot protocol conversion,
  request-scoped audit lifecycle, legacy command-idempotency infrastructure, the shared Foundation HTTP error boundary
  and health probe. Refactored authentication and API-management Processors belong to Access Control.
  The approved file consumer registers `FILE_SCAN`
  through Foundation compensation in the same transaction as File/Owner/completion receipt; its Handler owns recovery orchestration.
- `platform-support-test` exercises both contracts through the real API → Controller → Processor/CommandProcessor →
  Service → Repository → Mapper → local MySQL/Redis chain and depends directly only on Web.

The production dependency graph is one-way: Web depends on Access Control, Audit/Security and File/Document; Access Control depends on
Audit/Security only through the immutable audit-fact sink; Audit/Security never depends on Access Control. These
capability JARs run in the same process and use the same Platform database, primary DataSource and transaction manager.
The existing one-connection audit writer pool targets that same database and remains the only specialized pool.

Production Mapper XML follows its Owner:
`platform-support-access-control/src/main/resources/mapper/access-control`,
`platform-support-file-document/src/main/resources/mapper/file-document`,
`platform-support-audit-security/src/main/resources/mapper/audit`, and
`platform-support-web/src/main/resources/mapper/reliability`. Mapper interfaces retain method signatures and parameter
bindings only. Reusable SQL, dynamic clauses, result/parameter types and locking-read cache options belong to XML. SQL
annotations are allowed only in test helpers under `src/test/java`.

### Operations service boundaries

The former Directory implementation is split into account lifecycle, account provisioning, role management,
authorization/navigation, audit queries and shared management policy. Each capability publishes a Service interface;
its same-named `*Impl` lives under `domainservice/service/impl`. Legacy management retains its shared transaction executor
and Service audit collection. Authentication uses Owner Processors to observe transaction completion and collect trusted
identity, dynamic target and immutable audit facts; its Service only saves local facts through per-table repositories.
Controller annotations own the request scope and common outcome classification. Authentication Converters adapt HTTP
DTOs to Owner requests/results; JWKS and Snapshot use their own Converters and Owner Processors. Internal DataObjects stay inside their
owning capability JAR; Web exchanges only domain models and narrow ports with those JARs. DataObjects never become
HTTP responses or queued audit values.

Account pages use one bounded summary query and one batched role query, with a matching COUNT and stable order.
The summary reads only password-change metadata, never password verifiers. Last-administrator checks share the same
SQL eligibility predicate as the effective administrator count, so an already-disabled or initial-password-only
administrator does not incorrectly block reassignment. Login still uses one public failure code while internal audit
retains the precise bounded failure reason.

Pure assignment constructors use Lombok `@AllArgsConstructor`. Constructors that configure transactions, validate
input or derive cryptographic objects remain explicit. Current implementation and verification evidence is maintained
in [the status ledger](../agent-docs/CURRENT_IMPLEMENTATION.md), not inferred from the historical records below.

The service listens on port `18106` by default. Its Servlet Context Path is `/platform-support`, so PLT-I20 is:

```text
GET /platform-support/gateway-config/v1/public-snapshot
```

The response is global, complete and unpaged. It contains only current runtime-effective Target, Route, Header, Query
and Guard configuration. Platform Support exposes this contract over private HTTP. Network reachability is controlled
by deployment infrastructure; the application does not terminate TLS or authenticate callers with client certificates.

- Every successful query returns `200` with the complete typed JSON Snapshot.
- This internal contract deliberately has no `ETag`, `If-None-Match` or `304` behavior.
- `QueryGatewaySnapshotProcessor` captures the six configuration reads in the existing read-only, repeatable-read
  transaction (2-second timeout), then compiles the complete `GatewaySnapshotView` after the transaction closes.
  The Controller and readiness probe use this same Processor entry; the former query Service is removed.
- API responses are immutable classes under `api.gatewayconfiguration.response`, with the top-level
  `GatewayPublicSnapshotResponseBody`. JSON fields, `schemaVersion=1` and full-response behavior remain unchanged;
  Java consumers must update imports/getters and rebuild. See the
  [refactoring verification record](../agent-docs/CURRENT_IMPLEMENTATION.md#platform-gateway-snapshot-refactor-20260917).
- Missing, ambiguous, invalid or unavailable configuration fails closed through the shared Foundation
  `500 INTERNAL_ERROR`; no partial or last-known Snapshot is emitted by Platform.
- An unknown persisted enum Code fails at the Mapper boundary and cannot be converted to `null` or published.
- Platform does not persist a Snapshot, Release or Version copy.

PLT-I22 is a separate bounded internal query:

```text
GET /platform-support/authentication/v1/realms/OPERATIONS/jwks
```

`OperationsJwksController` uses `JwksConverter` to create a parameterless `QueryOperationsJwksRequest`, calls
`QueryOperationsJwksProcessor.process`, and converts the result to its HTTP response. The Processor applies the shared
entry checks and delegates to the local `OperationsJwksService`, which is also used by token preparation.
The Service reads all candidates in one SELECT with a two-second statement timeout, then validates them against one
instant from the shared UTC Clock. The query opens no explicit transaction and requires no write Context or lock.
The Repository returns its module-owned `JwtSigningKey` class directly; only validated `PublicVerificationKey` values
cross into Web. Shared HTTP responses are the ordinary classes `JsonWebKeySetResponseBody` and
`JsonWebKeyResponseBody` under `api.authentication.response`, preserving `keys` and `key_ops`.
Customer JWKS and Gateway consumers use the same response classes. Verification scope is recorded in the
[Operations JWKS refactoring ledger](../agent-docs/CURRENT_IMPLEMENTATION.md#platform-operations-jwks-refactor-20260917).

- It publishes exactly one current Operations `ACTIVE` key plus still-valid `RETIRING` verification keys; `CUSTOMER`,
  `PREPARED`, `RETIRED` and expired rows never enter the response.
- Every key is an RSA-2048-or-stronger public JWK with `use=sig`, `key_ops=[verify]`, `alg=RS256`, a stable valid `kid`
  and canonical unpadded Base64URLUInt `n`/`e`. Private or unknown JWK members fail closed.
- The complete set is non-empty, has at most 16 keys and serializes to at most 64 KiB. Platform does not cache it and
  does not implement ETag/304; Gateway owns its bounded JWKS cache.
- The table is `t_jwt_signing_key`, not the generic former name `t_signing_key_metadata`. A `kid` cannot be reused with
  different public material; future lifecycle commands must enforce that write invariant.
- Operations uses Realm `OPERATIONS`, audience `operations-console` and an Operations-only KMS Key Ring. The local
  issuer is `https://platform-support.local.test`; production uses an environment-specific HTTPS issuer. Client and
  Partner share Realm/KMS/JWKS `CUSTOMER`, while keeping distinct audiences `client-console`/`partner-console`
  and channels `CLIENT`/`PARTNER`. CUSTOMER publication follows the Client capability below.

### CUSTOMER JWKS query

```text
GET /platform-support/authentication/v1/realms/CUSTOMER/jwks
```

`CustomerJwksController` uses the shared `JwksConverter` to create a parameterless `QueryCustomerJwksRequest`, calls
`QueryCustomerJwksProcessor.process`, and converts the validated public keys to the existing HTTP response classes.
Controller, Processor and `CustomerJwksServiceImpl` retain `client.authentication.enabled=true` as their enablement
condition. `ClientTokenPreparation` also uses this local Service to check the published signing key.

The Service reads CUSTOMER candidates in one SELECT with a two-second statement timeout, then validates the full
set against one instant from the shared UTC Clock. It opens no explicit transaction, uses the shared system exception
for invalid material, and retains the existing lifecycle, public-field, RSA, unique-kid and response-size checks.
It requires no write Context, lock or external call. Verification scope is recorded in the
[Customer JWKS refactoring ledger](../agent-docs/CURRENT_IMPLEMENTATION.md#platform-customer-jwks-refactor-20260917).

## Manual database setup

Code-MoR does not use Flyway, Liquibase or Testcontainers. Start the repository-owned local MySQL and apply the two
reviewed SQL files manually, in this order:

```bash
mysql --default-character-set=utf8mb4 --protocol=TCP -h 127.0.0.1 -P 13306 -u platform_app -p mor_platform \
  < docs/database/sql/platform-support-schema.sql
mysql --default-character-set=utf8mb4 --protocol=TCP -h 127.0.0.1 -P 13306 -u platform_app -p mor_platform \
  < docs/database/sql/platform-support-initial-data.sql
```

There is exactly one current Schema DDL and one initialization DML. The current projection adds `t_console_resource`
to the earlier 21 tables. The DML provisions one Operations Target, 36 Routes, the fixed INTERNAL Tenant, two system
Roles, 19 ACTIVE Permissions and 21 Console Resources, with paired rate/concurrency rules for each required
dimension.
It creates no default Account, password, fake ACTIVE signing key or audit event. The final commented operator block
in that same DML shows the explicit Bootstrap credential and matching public-key inserts. Supply an independently
prepared Argon2id PHC and real local RSA public JWK; do not execute placeholders or store plaintext secrets in SQL.
Bootstrap is forced to change its password, re-login, create the first named administrator, and becomes DISABLED;
the named administrator also changes its initial password before entering the normal console.

The SQL files are reviewed manual inputs. The current Schema keeps PRIMARY KEY, UNIQUE and NOT NULL,
but creates no CHECK or FOREIGN KEY. Association and business invariants are checked by application queries,
domain rules and local transactions; complete table/column/index comments match PDManer. Existing exact-path
and case-insensitive-header collations are retained. Java applications and tests never create, migrate or seed schemas.

For the existing 22-table database, review
[`2026-09-04-code-owned-constraints.sql`](docs/database/sql/changes/2026-09-04-code-owned-constraints.sql)
and back up first; it removes 25 foreign keys and 47 checks and synchronizes comments without changing rows.
It is a one-time manual upgrade, not an idempotent startup migration. Do not replay the full Schema or initialization DML.
The upgrade had previously been applied to the independent `mor-test` environment. For the resource-lock integration,
local `mor_platform` was backed up and incrementally aligned from its 21-table projection while preserving its existing
Gateway catalog; that work did not access or modify `mor-test`.
Until a valid Operations ACTIVE key exists, PLT-I22 continues to fail closed.

An existing local database created before this rename must be adjusted once by a developer or deployment operator:

```sql
RENAME TABLE t_signing_key_metadata TO t_jwt_signing_key;
```

Index names should then be aligned with the current Schema DDL during the same reviewed maintenance window;
old CHECK/FK definitions must not be recreated.

## Environment properties

No sensitive property has a repository default.

| Concern         | Environment variable              | Notes                                                   |
|-----------------|-----------------------------------|---------------------------------------------------------|
| MySQL JDBC URL  | `PLATFORM_SUPPORT_DB_URL`         | MySQL 8.4-compatible JDBC URL                           |
| MySQL user      | `PLATFORM_SUPPORT_DB_USERNAME`    | Dedicated Platform database principal                   |
| MySQL password  | `PLATFORM_SUPPORT_DB_PASSWORD`    | Secret value                                            |
| Redis host      | `PLATFORM_SUPPORT_REDIS_HOST`     | Required when Operations authentication is enabled      |
| Redis port      | `PLATFORM_SUPPORT_REDIS_PORT`     | Required when Operations authentication is enabled      |
| Redis password  | `PLATFORM_SUPPORT_REDIS_PASSWORD` | Secret value; no repository default                     |
| Nacos address   | `NACOS_SERVER_ADDR`               | Internal discovery endpoint; default `127.0.0.1:8848`   |
| Nacos namespace | `NACOS_NAMESPACE`                 | Environment namespace; local default `mor-local`        |

The service registers as `platform-support-service`. Only Nacos Discovery is enabled; this slice does not import Nacos
Config. The `auto` profile disables discovery and never registers test instances.

Scope creation and update use `AbstractLockProcessor` with the shared logical key
`lock:api-scope:{scopeCode}`. `api-scope.management.lock-wait-time` defaults to `500ms` and
`api-scope.management.lock-lease-time` to `30s`; both use `Duration`, are validated at startup,
and require restart to change. They are independent of the legacy annotation-lock properties.
The unique Scope code and database row/version checks remain the final concurrency protection.

## Onboarding file storage

`files.onboarding.enabled=true` (requires CLIENT authentication, Redis coordination and compensation) also needs exactly
one storage adapter, chosen by `files.onboarding.storage-provider`:

| Provider | Properties | Use |
|---|---|---|
| `MINIO` | `files.onboarding.minio.endpoint` (HTTPS root), `region`, `access-key`, `secret-key`, optional `trusted-certificate` (PEM), `connect-timeout`, `read-timeout` | Local `storage` profile (`https://127.0.0.1:19400`) |
| `S3` | `files.onboarding.s3.region`, optional `endpoint` (HTTPS), `path-style-access`, optional paired `access-key`/`secret-key` (otherwise the AWS default credential chain), `trusted-certificate`, timeouts | Production AWS S3 |

`storage-container-ref` is the bucket; it must have versioning and default server-side encryption, and the browser
origin must be allowed by bucket CORS for `PUT` with `Content-Type` and `If-None-Match`. Uploads are create-once and
completion pins the single object version. `scan-policy-code` must name an ACTIVE `t_file_scan_policy`; the built-in
`PLATFORM_FORMAT` scanner only re-checks digest, size and file signature (PDF/JPEG/PNG) — it is not malware scanning.
Operations reviewers download through `POST /review-files/v1/files/{fileId}/download` (metadata-only logging).

## Health

The same private HTTP listener exposes:

```text
/platform-support/actuator/health
/platform-support/actuator/health/liveness
/platform-support/actuator/health/readiness
```

Readiness performs the same bounded Snapshot query/compile path used by PLT-I20. A MySQL or Catalog failure makes it
Down. Liveness remains process-only. Health details are hidden. Standard Spring Boot/Micrometer HTTP, JVM and data
source observations remain available; this slice does not add a parallel Platform-specific metrics subsystem.

## Build and verify

PLT-I03 `validate` 的当前入口测试、Mock 配置和运行命令见 [测试模块说明](platform-support-test/README.md)，
实际验证范围见[当前台账](../agent-docs/CURRENT_IMPLEMENTATION.md#platform-api-credential-validation-tests-20260917)。
`MockTestBase` 统一自动加载已授权的本地 MySQL/Redis 连接密码，IDEA 或 Maven 启动不要求另配密码环境变量。
以下旧测试与覆盖率记录保留为历史证据，不代表正在整理的测试模块默认执行集合。

Prerequisites are Java 25 and the repository `local-environment` MySQL/Redis, with the reviewed SQL already applied.
Tests do not start, stop, migrate or clear either shared service. The historical suites used the local database password through
`PLATFORM_SUPPORT_TEST_DB_PASSWORD` (or `MOR_PLATFORM_DB_PASSWORD`) and the Redis password through
`PLATFORM_SUPPORT_TEST_REDIS_PASSWORD` (or `MOR_REDIS_PASSWORD`) through the approved operator-injected process
environment. Their startup did not read `local-environment/.env.local` or start dependencies; the current Mock entry
uses the automatic connection loading documented above. The historical full verification command was:

```bash
./mvnw clean verify
```

Verification covers the manual SQL baseline, the real PLT-I20 and PLT-I22 HTTP/MySQL chains, fail-closed Catalog/JWKS
behavior, architecture/dependency boundaries and aggregate JaCoCo thresholds. The Web module produces a normal
dependency JAR and an executable `-exec.jar`; the Test module is not installed or deployed.

Spring database tests take a bounded, cross-process MySQL advisory lock on a dedicated connection before application
startup. Do not run a second shared-catalog suite concurrently; lock contention fails clearly after the wait limit.
Snapshot/Health failure injection uses owned temporary Route IDs and checks the complete catalog before/after.
Management and JWKS fixtures register exact owned IDs with per-run markers; setup does not delete legacy runs or
restore manual routes to hard-coded values. A crashed run requires explicit inspection and scoped recovery, not an
automatic broad cleanup on the next test run.

Before the capability split, the integrated main-workspace gate passed 283/283 tests across 29 suites. The enforced
Phase A JaCoCo report covers 97.856% of instructions, 90.074% of branches and 96.108% of lines; the same run's Maven
aggregate report covers 97.756% of instructions, 90.244% of branches and 95.901% of lines. After the suite, all temporary
account/session/member/command/audit/key rows were absent; the resource-lock test prefix is checked separately through
bounded Redis `SCAN`.

For the current capability split, 135 focused pure/structural tests, the full Reactor package build and isolated
application startup have passed. The authentication-enabled runtime context also starts with the repository local
MySQL/Redis configuration. Its audit runtime fixture cannot currently run against the shared `mor_platform` database
because that database contains deployed/manual data and does not satisfy the suite's clean-catalog precondition; the
database was preserved and no full `clean verify` success is claimed for this working tree.

### Risk Operations catalog source verification (2026-09-07)

The fresh-database initialization DML adds five permissions: `RISK_DECISION_CONFIG_READ`, `RISK_RULE_SET_SAVE`,
`RISK_RULE_SET_FREEZE`, `RISK_TEST_CANDIDATE_CREATE` and `RISK_TEST_CANDIDATE_VALIDATE` (IDs ending `536..540`).
Its six Console Resources are the Risk root, `/risk/rule-configuration` page and four distinct action buttons
(`565..570`). Root and page share READ; each button belongs to page `566` and has its own write permission.
The two complete system-role grant sets contain 25 rows: Admin 19 and Viewer 6. The navigation checker now requires
exactly 21 Admin resources and 8 Viewer resources, including the existing Gateway menus and Admin-only Manage button.
It checks each Account, Role, Gateway and Risk button's exact parent, code and public shape.

Nine Operations Risk routes (`1024..1032`) cover authoring context, Rule Set list/detail/save/freeze and TEST Candidate
list/detail/create/validate. All target the existing Operations BFF; five reads have a zero body limit, Save has
65,536 bytes and the other three commands have 16,384 bytes. The compiler's generic maximum is 65,536 bytes;
65,537 is rejected without a Risk-specific exception, and the 27 legacy route body limits remain 16,384.

The prepared-database gate now requires 21 ACTIVE Operations resources and 36 ACTIVE Operations routes. It compares
the complete database catalog directly with the code-frozen canonical catalog, including every Permission, both
system-role grant sets, Resource, Route, Header, Query and Guard tuple. It does not derive expected data from the SQL
file. Extra, missing, inactive, count-preserving replacements or immutable-field drift fail with the unchanged manual
setup message. Every Snapshot test calls this prerequisite before HTTP/fixture work; the login-route-only prerequisite
has been removed. Static tests protect these source contracts and the manual-only setup boundary.

This source checkpoint used no database, Redis or Docker. The focused TDD gate first failed on the old navigation
sets, missing system-role Risk assertions and incomplete Viewer grant fixtures. After alignment, the following
database-free gate passed **70/70 tests**: Compiler 20, Persistence 47 and Paging Catalog 3, with zero failures,
errors or skips. All test sources also compiled successfully:

```bash
./mvnw -q -pl platform-support-test -am \
  -Dtest=GatewayConfigurationCompilerAutoTest,OperationsPersistenceContractAutoTest,OperationsPagingCatalogContractAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -q -pl platform-support-test -am -DskipTests test-compile
```

The HTTP test source now queries both system-role details using an authenticated Admin and expects the exact Risk
permission subsets `536..540` and `536`; three existing grant-ceiling fixtures include the complete six-permission
Viewer baseline so their original success/failure causes remain meaningful. These HTTP methods and the actual database
canonical-equality gate were **not executed** at this checkpoint. The existing database-isolation Listener remains in
place; no credentials were requested or read and it was not bypassed. The focused database gate, full `clean verify`
and coverage gate remain pending the separately authorized local dependency/credential/catalog prerequisites.

The canonical DML is a reviewed fresh-database input, **not an applied migration**. This checkpoint did not apply SQL,
prepare/rebuild a database, create a backup, enable an existing environment's routes or deploy anything. Existing-environment
SQL, route enablement and deployment belong to the separate integration/rollout track. Historical runtime verification
elsewhere in this README does not establish that this new catalog or its HTTP projections have run.

## Console resource management

`ConsoleResourceManagementApi` uses six Access Control entry points: `ListConsoleResourcesProcessor`,
`GetConsoleResourceProcessor`, `QueryConsoleResourceCapabilitiesProcessor`, `CreateConsoleResourceProcessor`,
`UpdateConsoleResourceProcessor` and `ChangeConsoleResourceStatusProcessor`. Controller/Converter only adapt the
protocol. Queries return `ConsoleResourceView`; writes pass a named Context holding the table object to the local
Service, with version/CAS and successful-write object synchronization in `ConsoleResourceRepository`.

The three writes share the Operations tree lock through Foundation `AbstractLockProcessor`, followed by a
15-second SERIALIZABLE transaction. `ps.access-control.console-resource.lock-wait-time` defaults to `500ms` and
`lock-lease-time` to `30s`; these validated settings require restart. The template's logical Redis key is
`lock:console-resource:operations`, before the configured service namespace. The earlier annotation used
`console-resource:operations`, so mixed versions do not share this Redis lock; rollout must coordinate the three
write entries. Database range protection, unique code and version checks remain the final safeguards.

Resource request replay belongs to Gateway. These Platform and BFF handlers no longer bind or forward
`Idempotency-Key`, prepare command hashes or store local replay receipts. Unchanged edits still retain the original
version and suppress a misleading change audit. Query/write JSON fields and validation remain compatible; Java
consumers use the standalone `consolemanagement.request` / `response` classes and the shared
`configurationmanagement.response` classes. Current verification and pending runtime checks are recorded in the
[implementation ledger](../agent-docs/CURRENT_IMPLEMENTATION.md#platform-console-resource-refactor-20260917).

## Operations development slice

The browser reaches the Operations BFF through Gateway. These private Platform paths include the Servlet context
prefix `/platform-support`; only service-to-service callers may reach them:

| API                           | Internal path                            | Capability                                                                                                                                                     |
|-------------------------------|------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `OperationsAuthenticationApi` | `/authentication/v1/operations/*`        | login, refresh, logout, context, password change, Bootstrap handoff                                                                                            |
| `OperationsAuthorizationApi`  | `/authorization/v1/operations/authorize` | live Session/Account/Member/Role and current Route decision (PLT-I02)                                                                                          |
| `OperationsAuthorizationApi`  | `/authorization/v1/console-navigation`   | permission-filtered Console Resources (PLT-I21)                                                                                                                |
| `OperationsManagementApi`     | `/operations/v1/*`                       | people list/create/disable/enable/delete, role create/update/delete/query/assignment, batch role employee counts, grantable permissions/resources, audit query |

The three Operations APIs contain only HTTP contracts and import their models from the same lightweight API module.
Authentication, authorization and management use standalone classes under their respective `request` / `response`
packages. Management models live in `api.operations.request` and `api.operations.response`; consumers import models
directly instead of through an API interface.

`OperationsManagementController` delegates its 16 entries through `OperationsManagementConverter` to Access Control
Processors. Eight commands use Foundation `AbstractLockProcessor`; eight queries retain the existing read-only
Repeatable Read snapshot. `OperationsManagementTransactions` owns the 15-second Read Committed write policy,
transaction completion observation and shared database concurrency mapping. Validated Contexts carry the original
Account/Member/Password/Role objects into local-only Services; per-table repositories update those objects only
after successful conditional SQL. Audit Security retains the audit query and classification projection.

The eight management commands no longer bind or forward `Idempotency-Key` or invoke service-local command replay;
Gateway owns HTTP replay. Account mutations share `lock:operations:account:{id}` with authentication. Role mutation
and creation use `lock:operations:role:{id}` and `lock:operations:role-code:{code}`. Account creation prepares the
stable email Token outside the transaction and locks its controlled HMAC resource digest under
`lock:operations:create-account:{digest}`. Token/email originals never enter the Redis key. These templates reuse
`operations.authentication.lock-wait-time` / `lock-lease-time` (defaults 500ms / 30s); unique indexes, database role
locks, expected versions and last-administrator protection remain the final authority. Provider, BFF consumers
and changed role/create-account lock namespaces require coordinated activation; no rollout is implied here.

The Operations contracts use `*RequestBody` and `*ResponseBody` for their top-level HTTP DTOs, including
`LoginRequestBody`, `AuthenticationTokensResponseBody`, `OperationsContextResponseBody`,
`AuthorizationDecisionResponseBody` and `NavigationResponseBody`. `AccountResponseBody` is both the create-account
response and the account-page element type. Nested `RoleSummary`, `ConsoleResource` and `AuditEvent` retain their
business names. Authorization model extraction preserves JSON fields, validation, HTTP methods/paths and parameter
locations; callers must update their shared API type references and use the class getters. Operations BFF performs
that conversion at its existing Client Adapter boundary.

`OperationsAuthorizationController` delegates through `OperationsAuthorizationConverter` to Access Control's
`AuthorizeOperationsProcessor.process` and `QueryOperationsNavigationProcessor.process`. Both retain the current
identity checks and 15-second, read-only REPEATABLE READ snapshot, with audit transaction observation inside the
callback. Navigation queries map directly to `OperationsNavigationResourceView`; missing-parent descendants are
removed iteratively before protocol conversion. The same projection retains `permissionId` for the existing
grantable-resource query, without exposing it in navigation JSON. These queries have no business writes, resource
locks or external steps. Verification scope is recorded in the
[current implementation ledger](../agent-docs/CURRENT_IMPLEMENTATION.md#platform-operations-authorization-refactor-20260917).

Accounts, roles and audit lists reuse Foundation `PagingRequestBody` and `PagingResponseBody<T>`.
Missing parameters default to **page=1, size=10**; sizes are exactly **10, 20, 50, 100, 200**, with a maximum page of

100000.

The JSON response is `{items, page, size, total}`. Roles now return this paging object instead of a bare array;
Platform API, Operations BFF and frontend must be released together. Queries apply database `LIMIT/OFFSET` and a
count under the same scope; roles use `role_code, role_id` for deterministic ordering. IDs are JSON strings. Management
writes require `Idempotency-Key`, a reason and the applicable current Account/Member version. The last usable
administrator cannot be removed; self-disable is denied. Personnel create/disable/role assignment require
`OPERATIONS_ACCOUNT_CREATE`, `OPERATIONS_ACCOUNT_DISABLE`
and `OPERATIONS_ACCOUNT_ASSIGN_ROLES` respectively. Role create/update/delete require `OPERATIONS_ROLE_CREATE`,
`OPERATIONS_ROLE_UPDATE` and `OPERATIONS_ROLE_DELETE` respectively. The actor may grant only their current grantable
Operations
permissions, including when assigning an existing role to personnel. Read permissions are `OPERATIONS_SYSTEM_READ`,
`OPERATIONS_ACCOUNT_READ`, `OPERATIONS_ROLE_READ` and `OPERATIONS_AUDIT_READ`. Every protected BFF Route still calls
PLT-I02. Bootstrap and
forced-change Accounts can read their current context and logout; remaining actions follow their live restrictions.

The 2026-09-04 paging change passed 18 focused Platform tests and compiled all test sources. The separately deployed
`mor-test` HTTP smoke check verified all three lists' defaults, five page sizes, subsequent pages and invalid size
rejection against the real database. The local-environment database auto suite was not run because its credentials
were not supplied. The role Route additionally requires the reviewed
`test-environment/sql/2026-09-04-operations-paging.sql`
increment; this adds only `page`/`size` query allowlist rows and has been applied to `mor-test`, without
reinitialization.

Enable only after reviewing/applying the SQL and injecting the following values outside this repository:

```text
--spring.profiles.active=local
--operations.authentication.enabled=true
--operations.authentication.worker-id=<unique worker id 0..31>
OPERATIONS_LOCAL_SIGNING_PRIVATE_KEY_BASE64=<base64 PKCS8 RSA private key, at least 2048 bits>
OPERATIONS_LOCAL_HMAC_KEY_BASE64=<base64 of at least 32 random bytes>
OPERATIONS_LOCAL_HMAC_KEY_VERSION=<positive integer>
```

The isolated Docker test deployment uses the explicitly authorized `test` Spring profile with
`operations.authentication.enabled=true`; the same local Vault/signing adapters are permitted there. Every active
profile must be one of `local`, `auto`, `test`: `prod`, `production`, mixed production profiles and unknown profiles
remain rejected. `test` does not enable authentication by itself or supply any secret. Use container DNS names for
MySQL and Nacos, create a separate test Nacos Namespace, and inject Nacos Discovery credentials when authentication
is enabled. Do not deploy with the Maven-test `auto` configuration, which disables service discovery.

The development signing key reference in `t_jwt_signing_key` must equal `development:operations:<kid>` and its
public JWK must exactly match the injected private key. No key is generated or registered at startup. Local issuance
uses `https://platform-support.local.test`, audience `operations-console`, RS256 and 15-minute access tokens.
Refresh generations use independent 256-bit secrets with HMAC-only persistence; a verified consumed generation
marks its session COMPROMISED. Session lifetime is at most 8 hours with a 30-minute idle window. Passwords use NFC,
15–128 code points / 512 UTF-8 bytes and Argon2id (19456 KiB, two iterations, one lane). Five failed login attempts
lock the Account for 30 minutes. Password changes increment credential/auth versions and revoke all current sessions.

The Vault port has an explicit local Mock: its token is a deterministic SHA-256-based test token and protected email
values are kept only in bounded process memory for password-context checks. It is not a production Vault, privacy
implementation or distributed store. Restarting it requires fresh login before a password change. The local password
blocklist is a small development policy, not a complete compromised-password corpus. The signing/HMAC adapter uses
one fixed, injected local key version, not a KMS. Persistent command-idempotency compatibility across production HMAC
rotation is not yet designed: the current database lookup has no persisted key-version compatibility path. Before a
rotating KMS is enabled, approve and test a versioned lookup/migration or an independently stable idempotency key
derivation; the fixed-key local tests do not prove replay continuity across rotation. Explicit TODOs identify real
Vault Protect/Reveal, real KMS Sign/HMAC and the production password corpus. Enabling authentication with no profile,
any profile other than `local`/`auto`/`test`,
missing
secrets, invalid keys or an invalid worker id fails startup; there is no production fallback.

Authentication/IAM payload logging is suppressed by Foundation's `web.logging.metadata-only-path-prefixes` for
`/authentication`, `/authorization`, `/operations`; these routes retain only bounded metadata and request IDs.
Expected auth/IAM failures use the Foundation shared error body. Only authentication failure,
session invalidation and permission denial retain dedicated `OPERATIONS_AUTHENTICATION_FAILED`,
`OPERATIONS_SESSION_INVALID` and `OPERATIONS_PERMISSION_DENIED` codes because BFF/browser behavior depends on them.
Platform returns these as `FailException` errors and BFF maps them to 401/403.
Ordinary password-policy, Bootstrap, account-conflict and administrator-protection failures use
`BizCheckFailException` with `BUSINESS_CHECK_FAILED` and a specific safe Chinese message. Input structure/range
failures use `InvalidParameterException` (`INVALID_REQUEST`, 400); optimistic-version and idempotency-payload
conflicts use `ConcurrentRequestException` (`CONCURRENT_REQUEST`, 409). Missing signing authority or unavailable
mock protected email uses `ServiceUnavailableException` (`SERVICE_UNAVAILABLE`, 503). Unexpected runtime failures
remain 500. These exceptions reuse Foundation's response generation; Platform adds no local error handler.

The internal BFF client forwards `X-Request-Id` so command and security audit events correlate to the ingress.

Business transactions freeze only typed in-memory audit facts. After the actual business transaction outcome and
Controller outcome are both known, a bounded worker appends an accepted event in an independent audit transaction,
with per-UTC-day sequence/hash continuity and protected attributes. Failed login counters commit before the response
error; the corresponding denial event is best effort and never controls that commit.
This slice supports audit query and local hash chains; external integrity seals, archival/export and cross-partition
verification are not implemented. It does not claim tamper-proof external attestation.

At the earlier resource-lock checkpoint, the local catalog was aligned to the 22-table projection after a pre-change backup; its existing
Gateway rows were preserved and current fixed initialization rows were added incrementally. The full
login/Refresh/IAM/audit and resource-coordination suite ran against real local MySQL and Redis. Missing credentials,
an unprepared catalog or an unavailable dependency still fails fast; no skipped database test is treated as verification.

`OperationsAuthenticationHttpAutoTest` contains real HTTP/MyBatis scenarios covering Bootstrap/password and
people management, consumed Refresh reuse, idempotency/version/last-administrator protection, custom-role and
independent-action authorization, batch employee counts, role and employee deletion with retained history, account
enablement and complete Tenant tuple checks. `OperationsAuthenticationConcurrencyHttpAutoTest` adds 37 real
MySQL/Redis concurrency, Lease-expiry and slow-work boundary scenarios; focused failure and Gateway overlap suites cover
the remaining coordination failures. Their shared Builder/Mocker creates only temporary test identities, a test signing
key and an isolated `2001-02-03` audit partition,
then removes the exact IDs it owns even when a scenario changes an Account Realm. It requires the manually prepared
catalog with no existing Operations Account, ACTIVE key or that audit partition, and refuses to clear existing data.
These fixtures do not provision runtime Bootstrap credentials or keys.

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home \
  ./mvnw -pl platform-support-test -am -Dtest=OperationsAuthenticationHttpAutoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Inject both database and Redis test passwords through the local environment before running. The earlier focused HTTP
suite and full `clean verify` passed in the local auto-test environment; the new Risk catalog's runtime gates remain
pending as recorded above. Separate `mor-test` deployment checks do not replace this suite, and `mor-test` was not used
by that earlier verification.

## Operations management resource coordination

The current Redis-first resource-lock refactor is integrated in the main workspace and verified against real local
MySQL/Redis. It has not been committed, pushed or deployed.

- Assign/disable/enable/delete use one logical `operations:account:{accountId}` Redis key. This slice has exactly
  one INTERNAL Tenant and a unique member per account; future CUSTOMER membership commands must use their own
  `tenantId + memberId` boundary.
- Role update/delete use `operations:role:{roleId}`; role creation uses `operations:role-code:{roleCode}`.
  Account creation uses `operations:create-account:{actorId}:{hmacKeyVersion}:{keyedDigest}` because the target ID
  does not exist yet. The original Idempotency-Key, email and tokens never enter keys.
- Foundation supplies the public repeatable `@ResourceLock`, restricted `#param` / `#param.field` resolver, startup
  expression/proxy/Advisor/client validation, fixed execution chain, owner-safe release and the single namespaced
  Redisson client. Healthy-lock contention waits at most 500 ms; the explicit lease is 30 s and the SQL transaction
  timeout remains 15 s. Redis transport timeouts are separately bounded by the shared client. Acquisition is outside
  the transaction; release follows commit/rollback.
- Contention/interruption returns shared 409, with interrupt status restored. Redis acquisition failure returns
  shared 503 with no fallback lock domain. Owner-checked release failure is logged without masking a committed
  result or the original transaction error; expiration bounds the remaining lock lifetime.
- Different targets share only the actor Account/Member and relevant Role SQL protection, not an exclusive actor or
  Tenant lock. After locking the actor Account/Member, each management command reads one database `UTC_TIMESTAMP(6)`;
  actor roles, permissions, grantable permissions and administrator counts all reuse that `effectiveAt`. A future
  actor Role that becomes effective between queries therefore cannot authorize the command midway. Enable protects
  every ACTIVE target binding it may restore. Target updates use expected Account/Member Version and conditional DML
  instead of a preliminary Target `FOR UPDATE`; Role lock modes are combined first and acquired in ID order.
- Any command that will revoke a current or future ACTIVE PLATFORM_ADMIN binding additionally takes that Role row
  exclusively, unless the request retains the binding. Future bindings with no expiry or an expiry after the frozen
  `effectiveAt` are included even when their `effective_from` has not arrived; the command then recounts currently
  effective administrators at the same cutoff. Normal personnel commands do not all take this exclusive lock. CAS,
  unique command records and transaction rollback remain authoritative if Redis ownership expires. Redis is not a
  fencing token.
- Ordinary queries, including PLT-I02 authorization, use a read-only Repeatable Read snapshot. Known Login and
  password/bootstrap changes coordinate by Account; Refresh/Logout coordinate by Session; unknown Login/Refresh use
  purpose-separated versioned HMAC buckets without exposing credentials. Vault, Argon2id, HMAC and signing work runs
  while holding Redis but before the short write transaction; Account/Password CAS, Session X, Bootstrap Account X,
  unique indexes and conditional DML retain only the database protection required by each invariant. Daily audit-chain
  arbitration occurs only in the background audit transaction and never extends the business lock or transaction.

Enablement follows `operations.authentication.enabled`; the Snapshot/JWKS-only application does not enable
coordination or Redis health checks. Supply `PLATFORM_SUPPORT_REDIS_HOST`, `PLATFORM_SUPPORT_REDIS_PORT` and
`PLATFORM_SUPPORT_REDIS_PASSWORD` for enabled environments. Auto tests use only local Redis `127.0.0.1:16379`,
`PLATFORM_SUPPORT_TEST_REDIS_PASSWORD` (or `MOR_REDIS_PASSWORD`), and a per-context random
`redis.key-prefix`. No test stops shared Redis or clears its database. SQL remains manually maintained.

See the current [Redis-first decision](../agent-docs/decisions/2026-09-05-redis-first-resource-lock.md),
[Foundation design](../docs/superpowers/specs/2026-09-05-foundation-resource-lock-design.md),
[Platform design](../docs/superpowers/specs/2026-09-05-platform-support-resource-lock-optimization-design.md) and
[implementation plan](../docs/superpowers/plans/2026-09-05-platform-support-resource-lock-optimization-implementation.md).
The [2026-09-04 record](../agent-docs/plans/2026-09-04-platform-resource-lock-refactoring.md) remains predecessor
evidence, not the current target contract.

## Tenant access management

`TenantAccessManagementApi` uses eight Access Control Processors through Controller → Converter → `process`.
The seven queries share `OperationsManagementTransactions`' RR read-only snapshot, so authorization, integrity checks,
page content, counts and database `evaluatedAt` stay consistent within one request. Joined member, role, permission and
source results use dedicated Views; tenant summaries cross the Web boundary without exposing DataObjects.

`ChangeTenantSecurityStatusProcessor` uses `AbstractLockProcessor` for the target Tenant. It authorizes the current
operator, locks the tenant row and validates the expected version before building `ChangeTenantSecurityStatusContext`.
The local-only `TenantAccessManagementService` saves through `TenantAccessRepository`; CAS must affect one row before
updating the original object. Only security status, version and update time change. Matching-version same-state requests
return the current result without advancing the version or recording a change. Version exhaustion rejects real changes.
Audit facts capture before-values before mutation and retain the existing post-commit best-effort lifecycle.

API DTOs are classes under `api.tenantaccessmanagement.request/response` with Chinese contracts. The strict JSON reader
recognizes the new request package and preserves unknown/duplicate-field rejection and required primitive binding.
Platform and the BFF no longer receive or forward `Idempotency-Key`; request replay belongs to Gateway. Current business
permission checks, target isolation, database integrity checks, row locks and CAS remain in the Owner.

Component-scanned `TenantAccessManagementProperties` binds `ps.access-control.tenant-access.lock-wait-time` (500ms)
and `lock-lease-time` (30s), independently of legacy annotation settings. The template's logical Redis key is
`lock:access-tenant:{tenantId}`; the previous annotation used `access-tenant:{tenantId}`. Coordinate replacement of old
writers when deploying, since mixed versions do not share a Redis mutex. Source projection retains its existing separate
source/account locks and database protection. Verification and deployment status are recorded in
[the implementation ledger](../agent-docs/CURRENT_IMPLEMENTATION.md#platform-tenant-access-refactor-20260917).

## Permission catalog management

`PermissionManagementApi` keeps its seven `/permission-admin/v1` routes and JSON fields. The API models are ordinary
classes under `api.permissionmanagement.request/response`; all operations pass through
`PermissionManagementController` → `PermissionManagementConverter` → an Access Control Processor's `process` entry.
Internal request constraints use Foundation `AbstractProcessor.validateBasic`. Four query Processors retain the
existing transaction-free, bounded query contract, including the 100-item page limit and usage-specific option authorization.

`CreatePermissionProcessor`, `UpdatePermissionProcessor` and `ChangePermissionStatusProcessor` use
`AbstractLockProcessor` with the same permission-code resource. `PermissionManagementTransactions` preserves
SERIALIZABLE isolation and the 15-second local write timeout; update/status reads retain the exclusive permission row
lock that coordinates with reference binding and role-grant shared locks. Each command builds its own Context and calls
the local-only `PermissionManagementService`; `PermissionRepository` checks affected rows before updating the original
`Permission` DataObject. Creation stays INACTIVE, identities stay immutable, unchanged risk edits keep their version,
and protected management permissions cannot be disabled. Existing Controller audit declarations and typed before/after
facts remain; no role/member grant is added or removed by these commands.

The three commands and their BFF adapters no longer consume `Idempotency-Key` or prepare/claim/complete service-local
request idempotency. Gateway owns that transport concern; permission-code/resource-action uniqueness, current authority,
row locks and version CAS remain in Platform. This change does not claim Gateway idempotency has been deployed.

Lock settings are component-scanned `PermissionManagementProperties`: `ps.access-control.permission.lock-wait-time`
defaults to 500ms and `lock-lease-time` to 30s. They are independent of the legacy annotation settings. The template adds
`lock:` to the logical `permission:{permissionCode}` key, so old/new writers require a coordinated rollout; they do not
share the same Redis mutex during mixed-version operation. Current verification and rollout limits are recorded only in
[the implementation ledger](../agent-docs/CURRENT_IMPLEMENTATION.md#platform-permission-management-refactor-20260917).

## Gateway Target and Route management

`GatewayConfigurationManagementApi` now exposes Operations-only Target/Route list, detail and status endpoints below
`/gateway-admin/v1`. Lists use the shared page contract, exact optional filters and stable database ordering. Reads require
`GATEWAY_CONFIG_READ` or `GATEWAY_CONFIG_MANAGE`; status commands require `GATEWAY_CONFIG_MANAGE`.

The REST Controller delegates to `GatewayConfigurationManagementProcessor`, which maps HTTP DTOs to internal
Gateway management Query/Command/Result/Page models. The Processor owns read snapshots and the two public command
entries; each command entry declares the fixed Catalog resource lock outside its Serializable write transaction and
maps database concurrency failures. It has no audit lifecycle dependency. `GatewayConfigurationManagementService` and
`OperationsCommandIdempotencyService` are business interfaces with one `*Impl` each; Repositories and MyBatis XML own
the persistence boundary.

Every status command acquires only `gateway-configuration:catalog`, then starts the Serializable local transaction.
The catalog lock protects full-catalog candidate compilation across different roots; the former Target/Route Redis
lock and preliminary root `FOR UPDATE` are removed. Serializable isolation, root Version CAS and the unique
idempotency row remain the local final protection. Enabling compiles
the complete candidate with `GatewayConfigurationCompiler`. Spring database concurrency failures become the shared
409 response and are recorded as denied audit events. Successful audit records preserve normalized reason, opaque
idempotency reference, and before/after status and version. `/gateway-admin` is metadata-only in ordinary HTTP logs.

`GatewayConfigurationManagementHttpAutoTest` exercises the real Controller-to-MySQL/Redis chain for exact list/detail,
READ/MANAGE boundaries, Target and Route status changes, normalized replay, different-payload key conflicts, stale CAS,
conflicting full-catalog activation, different-resource catalog serialization, real MySQL lock-timeout conversion,
success/failure audit and owned-fixture cleanup. `GatewayConfigurationLeaseOverlapHttpAutoTest` verifies that an expired
short Redis Lease still leaves Serializable isolation and Version CAS as the final database arbiter. Persistence
contracts also verify dynamic index-eligible filters, plain root reads without explicit row locks, Version CAS and the
internal model boundary. This capability exists in the main workspace; it has not been deployed, and initialization
SQL remains manually applied.

This capability is included in the complete JDK 25 MySQL/Redis gate reported in Build and verify above. The Gateway
management cases also cover MANAGE-as-read
fallback, unprivileged denial, unknown resources, Target restoration, inactive/missing Permission fail-closed behavior,
corrupted idempotency replay rejection and exact Permission projection in Route details.

## Explicitly absent from this slice

- production Vault/KMS integration and production password-blocklist corpus;
- PLT-I19 publication workflow and Console Resource management commands;
- Redis runtime limiting state, cache, RabbitMQ, compensation events and XXL-Job scheduling;
- Partner/Client BFF behavior or any browser-facing endpoint;
- Gateway runtime code (owned by the independent `api-gateway` repository);
- Nacos Config, deployment manifests and deployment automation.

Each absent capability requires its own approved vertical slice.

## Operations custom roles

The Operations console can create custom roles, atomically update their name and complete permission set, and delete
unassigned custom roles.
Roles belong to the current fixed INTERNAL Tenant. `PLATFORM_ADMIN` and `OPERATIONS_VIEWER` remain read-only
system roles; Client/Partner roles and other Tenants are outside this API. Role codes are immutable after creation.
No schema changes, role description field, general role enable/disable workflow or permission-catalog editor were
introduced.

Public role creation/options require `OPERATIONS_ROLE_CREATE`; detail/update require `OPERATIONS_ROLE_UPDATE`.
Internally, `OperationsManagementApi`
provides role detail/create/update/delete, current grantable permissions and authorized Console Resources. Platform
returns
business facts; the BFF assembles the permission tree. A grantable permission must be ACTIVE, used by an ACTIVE
Operations route or resource, and currently granted to the actor. Role detail/edit checks the complete old permission
set too, so a limited administrator cannot unknowingly overwrite hidden permissions.

Writes use resource-level coordination described above; updates check `expectedVersion` and all
commands retain the existing idempotency contract. The business transaction writes Role, Role Permission and command
completion, while the Service freezes a typed success fact for best-effort persistence after commit. A replay rechecks current authority. Permission changes are read by PLT-I02 on the next
request, without embedding a stale permission list in the JWT. Personnel role assignment applies the same grant ceiling.

`RoleSummary` includes its current `version`. The internal
`POST /platform-support/operations/v1/roles/employee-counts` accepts `{roleIds}` and requires
`OPERATIONS_ROLE_READ`. It accepts 1–200 distinct, nonblank IDs within the current Operations role scope and returns
`{items: [{roleId, employeeCount}]}`, with exactly one result per requested role, including zero. A single grouped
query counts distinct HUMAN Operations accounts associated through the fixed INTERNAL Tenant: membership must be
ACTIVE or SUSPENDED, the role grant must be ACTIVE and scope-matched, and its end time must not have passed.
Future-effective grants and disabled accounts remain counted. The BFF combines this batch result with the current
role page; Platform does not issue per-role count queries.

`DELETE /platform-support/operations/v1/roles/{roleId}` requires the independent `OPERATIONS_ROLE_DELETE` permission,
`Idempotency-Key`, and `{expectedVersion, reason}`, and returns `204`. System roles, out-of-scope roles and roles whose
complete permission set exceeds the actor's grant ceiling cannot be deleted. The same employee-count rule blocks
deletion while any association remains; the command never automatically unbinds staff. A role resource lock and
local role-row read/write protection coordinate deletion with personnel assignment and other role writes. Success sets the role
to INACTIVE, increments its version and revokes its permissions, preserving the role row, prior associations and audit
history. Deleted roles disappear from current lists/details and cannot be assigned again.

Deletion rechecks current DELETE authority before command replay. The same key/body succeeds without requiring the
now-inactive role or appending another success audit; a changed body under that key returns `CONCURRENT_REQUEST`,
while a new command for an already deleted role returns a business failure. Its `OPERATIONS_ROLE_DELETE` / `ROLE`
fact freezes the reason and before/after name, version, sorted permission IDs and ACTIVE → INACTIVE status at the
business mutation point; accepted audit persistence runs later in its independent transaction.

Role audit records include the reason, role code, and before/after names, versions and sorted permission IDs. Each
permission is a bounded attribute within the existing audit schema; the deterministic attribute root includes every
attribute, without truncating a serialized snapshot. Failure audit retains the shared exception boundary.

Existing `mor-test` uses the reviewed one-time increment
`test-environment/sql/2026-09-04-operations-role-management.sql`, followed by the action-grant increment and
`test-environment/sql/2026-09-04-operations-role-delete.sql` for deletion; do not replay full initialization or
Bootstrap.

The earlier 2026-09-04 role create/update delivery passed its focused verification. The separate `mor-test`
deployment passed real role create/update,
idempotency/version, privilege-ceiling and live revocation checks. Audit before/after attributes and their SHA-256
roots were independently verified from the test database. This is not a full local auto/coverage gate claim.

2026-09-04 action-grant correction: the five mutation actions now have distinct Permission IDs (526–530).
The existing test MANAGE grants were expanded to exactly the previously allowed actions, and the two old Permission
codes retired as INACTIVE with their grants REVOKED; affected role versions advanced. The deletion slice adds
`OPERATIONS_ROLE_DELETE` (531), bringing fresh initialization to ten ACTIVE permissions. It inserts each Permission
before its referencing Route, then Route Headers/Guards, respecting
immediate foreign keys. Shared internal catalog queries allow ROLE_CREATE or ROLE_UPDATE; create-only replay uses
the same scoped role check without requiring update authority. No public route was changed to authenticated-only.

2026-09-04 deletion/count delivery: **48 pure/configuration/architecture/catalog tests passed**, all test sources
compiled, the API was installed locally, and the executable Web JAR packaged successfully. The eleven real
HTTP/MySQL cases were **compiled only**, because neither `PLATFORM_SUPPORT_TEST_DB_PASSWORD` nor
`MOR_PLATFORM_DB_PASSWORD` was supplied. The new cases cover zero/distinct counts, future grants, suspended members,
disabled accounts, invalid or foreign role IDs, delete authorization, associated/system role protection, version
conflicts, replay, retained history and audit attributes. No database schema or fixture was applied by these checks.

## Operations employee deletion

`DELETE /platform-support/operations/v1/accounts/{accountId}` requires the independent
`OPERATIONS_ACCOUNT_DELETE` permission (532), `Idempotency-Key`, and
`DeleteAccountRequestBody {expectedVersion, expectedMemberVersion, reason}`; success returns `204`.
The command accepts ACTIVE or DISABLED HUMAN Operations accounts with a member in the fixed INTERNAL Tenant.
Self-deletion, Bootstrap/CUSTOMER identities, CLOSED accounts and foreign Tenant membership are rejected. Deletion
uses the personnel-management authority boundary; it does not impose a new target-role grant ceiling.

The target account resource lock coordinates deletion with assignment, disabling and enabling; scoped database
protection preserves current role dependencies. After current authority is checked, successful command replay is resolved before reading the target's active
state. A repeated key/body returns `204`; a changed body under that key returns `CONCURRENT_REQUEST`, while a new key
cannot delete an already CLOSED account. Both Account and Member versions must match before any mutation.

One local transaction sets Account to CLOSED and advances `auth_epoch` and version; sets Member to REVOKED and
advances its version; revokes every ACTIVE role grant, including future grants; revokes the password and all remaining
sessions/issued Refresh tokens; and writes the idempotency record. It also freezes a typed success fact for independent
best-effort audit persistence after commit. The Account, protected email,
password history, associations and audit rows remain stored. The email remains unique and Vault data is untouched.
Current account pages and their total both exclude CLOSED accounts, while the Bootstrap human-account count keeps
all historical identities. CLOSED accounts cannot receive new role assignments or be disabled again; revoked members
no longer contribute to role employee counts.

Last-administrator protection applies only when the target currently contributes to the usable administrator count.
DISABLED accounts, SUSPENDED members and credentials requiring their initial password change do not contribute and
are not incorrectly protected as the last active administrator. The shared count is restricted to HUMAN Operations
accounts in INTERNAL `owner_ref_id=1`, with matching Member/Tenant and Role/Scope joins.

The `OPERATIONS_ACCOUNT_DELETE` / `USER_ACCOUNT` audit includes reason, member ID, before/after Account status and
version, Member status and version, and sorted role IDs/counts. It contains no email or credential material and uses
the existing bounded attributes and deterministic audit chain.

2026-09-04 employee-deletion delivery: **49 pure/configuration/architecture/catalog tests passed**, all sources and
**13 real HTTP/MySQL cases compiled**, and the API install and executable Web package succeeded. The two new HTTP
cases cover current/disabled deletion, future grants, session/Refresh/password revocation, double-version conflicts,
replay, retained email uniqueness, sibling-permission isolation, terminal-state protection and last-administrator
scope. They were not executed because local auto-test database credentials were not supplied. No test deployment,
database migration or full database/coverage-gate success is claimed by this build result.

## Operations employee enablement

`POST /platform-support/operations/v1/accounts/{accountId}/enable` requires the independent
`OPERATIONS_ACCOUNT_ENABLE` permission (533), `Idempotency-Key`, and
`EnableAccountRequestBody {expectedVersion, reason}`;
success returns `204`. Only a DISABLED HUMAN Operations account may be enabled. Its fixed INTERNAL Tenant member
must remain ACTIVE, and its ACTIVE password credential must match the Account credential version. Bootstrap,
CUSTOMER, foreign Tenant, ACTIVE/PENDING/CLOSED accounts, SUSPENDED/REVOKED members and unavailable credentials are
rejected. Enabling an Account does not restore a Member relationship.

Enabling restores the account's existing authority, so all of its ACTIVE role associations, including future-effective
associations, must fit within the actor's current grantable permission set. An enable-only operator therefore cannot
restore a higher-privilege administrator. The account resource lock and shared protection of the actor and referenced
roles protect this check against concurrent role or personnel changes, without a Tenant-wide exclusive lock.

The command checks current authority before successful replay, then applies an Account version CAS. It changes only
status to ACTIVE, increments `auth_epoch` and version, and records the update time. It preserves role associations,
Member state/version, password and credential version, forced initial-password change, consecutive failure count and
lock expiry. Revoked Sessions and Refresh generations remain revoked; the employee must log in again, and an unexpired
password lock still prevents login. An identical key/body returns `204` without a second mutation or audit; a changed
body under the same key returns `CONCURRENT_REQUEST`.

The same business transaction records command completion and freezes an `OPERATIONS_ACCOUNT_ENABLE` / `USER_ACCOUNT`
fact with reason, member ID and before/after Account status, Account version and auth epoch. Its later audit write uses
the independent audit transaction. No credentials are included.

Historical 2026-09-05 checkpoint: **115/115 tests passed** against the manually prepared local database and Redis,
including the 21-Route / 12-resource / 12-ACTIVE-permission initialization contracts, 31 authentication/personnel/
resource-lock HTTP scenarios and five audit-query HTTP scenarios. API install, executable Web packaging and the
aggregate 90% branch gate passed. This is historical main-workspace verification evidence; the current full result is
reported under Build and verify above, and neither result claims deployment.

### Operations audit filters and details

`GET /operations/v1/audit-events` keeps the public paging contract and accepts optional ISO Instant
`occurredFrom` (inclusive), `occurredTo` (exclusive), `actorId`, `actionCode`, `result` and `requestId`.
Blank strings mean no filter; supplied strings are trimmed and matched exactly, including case. Actor IDs may
contain values such as `UNKNOWN`. Actor/request IDs are limited to 128 characters and action codes to 64;
result is `SUCCEEDED`, `DENIED` or `FAILED`. When both times are supplied, the original start must precede the end.
Both bounds then round upward to microseconds before JDBC binding, preserving the half-open predicate over
`DATETIME(6)` values even for nanosecond inputs. A valid interval may become empty at database precision.
Invalid filters use the shared `INVALID_REQUEST` response. List/count share the same SQL filters and Operations
scope, with database pagination ordered by occurrence time and event ID descending.

`GET /operations/v1/audit-events/{auditEventId}` returns `AuditModels.AuditEventResponseBody(event, attributes)`.
Both queries require current `OPERATIONS_AUDIT_READ` authority and only expose Platform events with an Operations
action. Missing or out-of-scope details return `BUSINESS_CHECK_FAILED`. Attribute order and duplicate keys are
preserved. PUBLIC/INTERNAL attributes expose `value_text` only when no Vault token is stored; CONFIDENTIAL/RESTRICTED
attributes expose only `value_masked`, with null preserved when no safe value exists. Queries never return Vault
tokens or hashes, and unknown classifications fail closed through the registered CodeEnum handler. List reasons
use the first attribute explicitly named `reason`, sharing the same safe projection as details. Existing history
is neither rewritten nor backfilled; BFF owns before/after presentation.

The five audit-query HTTP scenarios passed in the integrated gate above. They cover filter composition, exact strings,
half-open time ranges, database page boundaries, ordered duplicate attributes, masked/null values, out-of-scope events,
permission denial and unknown classification. Query-corruption fixtures affect only their owned event IDs and are not
represented as valid hash-chain evidence; the normal role-change detail verifies the chain before those fixtures are
applied. Automated tests continue to use local infrastructure rather than `mor-test`.

## Client API administration

Operations reads non-secret Credential/Tenant/Grant information, manages the global Scope directory, and may revoke one credential with its separate permission. Client Console self-service issuance/rotation/grants remain outside this slice. The nine v1 contracts live in `api.apicredentialmanagement` and `api.apiscopemanagement`; PLT-I03 remains the authentication authority. Scope references reuse the Route/Credential query APIs; the BFF composes the selected reference page.

The approved API and incremental SQL are in [Client API delivery](../requirements/20260913/platform-support-ClientAPI接入管理/design.md). See the [current ledger](../agent-docs/CURRENT_IMPLEMENTATION.md#platform-client-api-access-20260913) for implementation, validation and rollout status. New local Schema requires separate manual application; starting the application or tests never creates it.


## Delivery / Notification 普通 Email 首期

模块公开入口为 `platform-support-delivery` 的 `SubmitNotificationProcessor.process`，请求和响应使用模块合同；本期不开放 HTTP。
支持一次 1..100 个独立邮箱、默认语言/参数及收件人覆盖、可靠发送和原发送查询；规范化后重复邮箱整单拒绝。
正式默认 `notification.enabled=false`，没有生产 Mock Provider；开启需显式配置补偿执行、Vault 专用用途授权和账号适配器。
普通邮件与认证邮件对接分期，Access Control 现有邮件入口未迁移。

[需求和配置合同](../requirements/20260920/platform-support-Delivery领域规划/technique.md)、
[建表脚本](../requirements/20260920/platform-support-Delivery领域规划/db/notification-create.sql)、
[测试说明](platform-support-test/README.md#notification-普通-email)及
[实际状态](../agent-docs/CURRENT_IMPLEMENTATION.md#platform-delivery-domain-plan-20260920)分别维护对应事实。
