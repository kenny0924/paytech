# Vault — local/test Go server and Java client

This directory (`paytech/vault/`, moved from `vault-service/` in refactor step 02d) implements only generic Protect/Reveal, the Operations admin API (data types, access rules, manual Protect/Reveal), LOCAL AES, the reviewed six-table MySQL model, optional Nacos Discovery and the public `paytech-vault-client`. It is not production KMS, strong service authentication or a deployed business integration. Private HTTP, spoofable `X-Caller-Service` and a database-held allowlist are the accepted V1 trust boundary; the optional Platform internal token (D26, see *Internal caller token*) is off by default. No PAN/CVV/track/expiry/PIN, secrets or private keys are legitimate Values. Retention, destruction and production compliance remain unimplemented; PDManer in-app review remains pending.

## HTTP 契约

业务服务只使用以下两个接口，完整协议见 [OpenAPI](api/openapi/v1.yaml)：

```text
POST /vault/v1/records/protect
POST /vault/v1/records/reveal
```

`/vault/v1/admin/**` 只接受 `VAULT_ADMIN_CALLERS`（默认 `operations-console-bff`）并要求 `X-Operator-Id`：
维护数据类型与访问规则、查询策略变更与人工操作审计、人工加/解密（用途固定 `OPERATIONS_MANUAL`），
协议见 [管理接口 OpenAPI](api/openapi/admin-v1.yaml)。

访问策略（数据类型 + 访问规则）只存于数据库三张策略表，各环境用初始化 DML 写入，Vault 启动时必须读取成功，
之后按 `VAULT_POLICY_REFRESH_INTERVAL`（默认 10 秒）全量刷新；刷新失败保留上一份快照并计入指标。
审计中的策略版本记为 `db-<版本号>`。决定见 [2026-10-14 决定](../../agent-docs/decisions/2026-10-14-vault-policy-and-manual-access.md)。

`/vault` 表达系统边界，后续路径表达版本和能力；Java `@HttpExchange` 统一声明系统前缀，
各方法只声明能力路径。接口不使用 `/internal` 前缀或冒号动作后缀，仍仅供私网内部服务调用。
旧 URL 不保留别名或重定向，返回 `404`；Client 与 Server 必须同步更新。Java 公共方法、
请求/响应、幂等键、加密、三表数据及审计语义均不变，无需数据库迁移。

## 代码结构

技术栈见 [TECHNOLOGY_STACK.md §7.1](../../agent-docs/TECHNOLOGY_STACK.md#71-go-服务技术栈)，分层与编码规范见 [GO_DEVELOPMENT_CONVENTIONS.md](../../agent-docs/GO_DEVELOPMENT_CONVENTIONS.md)。

```text
cmd/vault-server/            进程入口
api/openapi/                 OpenAPI 合同
api/vault/v1/                请求/响应体（binding 校验标签）
internal/server/             Gin 路由、公共头、Trace/指标、Problem 错误出口
internal/service/            RecordService：Protect/Reveal；AdminService：策略维护、审计查询与人工加/解密
internal/biz/                ProtectUsecase、RevealUsecase、PolicyAdminUsecase、审计与加解密、领域对象、仓储与密钥端口
internal/data/               仓储实现与事务；data/sqlc 为查询（queries/*.sql）与生成代码（*.gen.go，不手改）
internal/conf/               配置读取与校验
internal/bootstrap/          组合根：cmd 与自动测试共用的装配
internal/pkg/                localaes、nacos、snowflake、observability、policy（数据库策略快照与定时刷新）、internalauth（内部调用 Token 与 JWKS）技术组件
test/auto/                   contract（OpenAPI 金样）、biz（用例）、conf、pkg/policy、pkg/internalauth、ext/http（HTTP 入口测试，Snowflake 31/27）、
                             ext/internalauth（Token 三种模式的入口测试，四套环境 Snowflake 31/22～25）
包内 *_test.go              仅保留必须访问未导出实现的白盒测试（事务故障、错误归类、严格 JSON 边界等）
test/support/                测试 base、Builder、本地 Mocker、外部 Mocker（Platform JWKS）、Checker、TestMapper、隔离标识
paytech-vault-client/        Java Client（Maven，属于 paytech/ 单 Reactor）
paytech-vault-test/          Java → Go → MySQL 黑盒验收（Maven）
```

Maven 模块使用 `paytech/` 唯一的 Wrapper：在本目录执行 `../mvnw …`，或在 `paytech/` 执行 `./mvnw -pl vault/<module> -am …`。

入口测试只需 `VAULT_TEST_DB_DSN`：`go test ./test/...`。每次运行使用独占的调用方/数据类型/请求 ID 前缀，结束后只删除本次运行的数据。

修改 `internal/data/sqlc/queries/*.sql` 或 Schema 后，在本目录执行 `sqlc generate`；`./scripts/verify.sh` 以 `sqlc diff` 校验生成代码无漂移。

## Docker test deployment

The current independent Docker test deployment is documented in
[the test runbook](../../test-environment/README.md#vault-test-部署). It uses only synthetic test data,
LOCAL AES and private HTTP at `http://vault-service:18107`; it does not replace Platform's Vault Mock.
Deployment scripts explicitly initialize only an empty test schema and preserve the test AES material.
The auto-test contract below remains local-environment-only; never point its fixture cleanup at test.

## Automatic verification

Prerequisites: Go **1.27.1** (`GO_BIN` or PATH), **sqlc v1.31.1** and **golangci-lint v2.14.0** (PATH or `SQLC_BIN` / `GOLANGCI_LINT_BIN`), **JDK 25** (`JAVA_HOME`), the `paytech/` Maven **3.9.16** wrapper (`../mvnw`), Python 3 (portable bounded subprocess supervision), git, POSIX private-file permissions, and **15 GiB** free disk. **`VAULT_MAVEN_REPOSITORY` is required** and must be an absolute path to the prepared Maven cache. Before invoking Maven or any database/test gate, the script checks nonempty local `paytech-parent`/`paytech-dependencies` POMs and `paytech-common-core`/`paytech-http-problem`/`paytech-internal-auth` POMs and JARs at `com/paytech/<artifact>/1.0.0-SNAPSHOT`. The parent and BOM resolve from `paytech/build` by relative path during the build, but the cache check still requires their installed copies. Missing artifacts require a separate operator-approved Foundation build/install (for example `../mvnw -B install -f ../foundation/pom.xml -DskipTests` from this directory); verification neither fetches nor substitutes them. Every Maven command uses that exact repository and `--no-snapshot-updates`; private/global settings are not parsed or changed. Nothing installs or publishes artifacts, changes global settings, starts containers or prepares schema automatically.

The operator must first start the repository `local-environment` MySQL and manually apply the reviewed [schema](docs/database/sql/vault-schema.sql) to existing `mor_vault`. Never replay DDL against an already prepared schema. Exactly three tables are required; no Flyway, Liquibase, Testcontainers, seed or truncate exists. Tests use only `vault_app` at `127.0.0.1:13306/mor_vault`.

The Maven wrapper distribution must also already be cached. Verification enables the repository-local `VAULT_VERIFY_CACHED_MAVEN_ONLY=true` guard: a wrapper cache miss returns a fixed preparation instruction before creating download directories or attempting any network download. Prepare the approved Maven 3.9.16 wrapper separately under operator control. Ordinary manual `./mvnw` behavior is unchanged when this guard is absent; verification does not duplicate the wrapper's URL/hash/cache-location rules.

Supply these environment variables through your secret-management shell (never command-line password arguments):

- `VAULT_TEST_JDBC_URL=jdbc:mysql://127.0.0.1:13306/mor_vault`
- `VAULT_TEST_JDBC_USER=vault_app`
- `VAULT_TEST_JDBC_PASSWORD`: the explicitly supplied scoped Vault password.
- `VAULT_TEST_DB_DSN`: `vault_app:<same password>@tcp(127.0.0.1:13306)/mor_vault?parseTime=true&loc=UTC`.
- `OTEL_RESOURCE_ATTRIBUTES` must be empty. The script rejects, never rewrites it. Runtime `service.name` is fixed as `vault-service`.

Tests and scripts do not read `.env.local`, Docker environment or credentials themselves. Missing MySQL/schema/credentials fail closed with a setup instruction, never skip. No two copies of a Go test package may run concurrently: persistence uses Snowflake `31/31`, application `31/30`, HTTP `31/29`, `ext/http` `31/27`, the local record Mocker `31/26` and `ext/internalauth` `31/22`–`31/25`. Test cleanup selects owned primary keys with a non-locking read and deletes by key, so parallel packages never lock each other's rows. `ext/internalauth` relies on JWKS forced refresh (at most once per 10 s per environment) and must not be run with `-count` greater than 1. Black-box fixtures use `31/28`, nonparallel JUnit and an exclusive loopback socket lease on **19128**; an occupied lease fails safely. Operators must not run another manually configured `31/28` server concurrently. Business test listeners use random loopback ports.

```sh
./scripts/verify.sh
```

The gate checks versions/disk/schema, OpenAPI and golden fixtures, gofmt, `sqlc diff`, golangci-lint, vet, Go tests, race tests, actual server build, Maven `clean verify`, a quiet sensitive-canary scan and `git diff --check`. All subprocess waits are bounded; no GNU `timeout` is needed. Captured output is private and not echoed on failure. The script removes only its exact temporary directory after owned processes stop; it preserves source, Maven reports and user data. Maven reports retain safe test diagnostics. The test module skips install/deploy and contains no production classes.

To rerun only acceptance after building an actual binary into an owned temporary directory, set `VAULT_TEST_SERVER_BINARY` to its absolute executable path and run:

```sh
../mvnw -pl paytech-vault-test -am test
```

Each fixture generates its own AES root key, policy, caller/request/key prefix and canary. Secrets enter the child only through environment variables; stdout/stderr and Go SDK temp files remain in a 0700 owned tree. Readiness is bounded to 20 seconds; shutdown requests graceful exit for 5 seconds then force-kills for at most 5 more. The outer database fixture owns the **19128 lease**: Go shutdown/private-file cleanup happens first, then owned SQL cleanup and connection close, then lease release. Stopping Go alone never releases it. SQL/file cleanup failures remain explicit; after failed SQL cleanup, rollback/connection close precedes lease release. If child death or database connection close cannot be confirmed, the lease is retained (and unconfirmed child death preserves rows). Cleanup follows confirmed process death, scanning logs without printing matches. SQL cleanup captures candidate Record **ID + immutable Token** through owned Protect requests before removing events/requests, deletes only owned caller/key/request rows, and preserves Records still referenced by another run. It never cleans by ID/timestamp range. Failed or interrupted tests may require operator investigation; they never delete unrelated rows to repair an ID collision.

The client performs **zero logical/application HTTP retries** and has no retry interceptor. JDK transport may reconnect or resend requests the peer declares unprocessed; there is no supported per-client switch to eliminate all low-level recovery. The library does not change JVM-global retry properties. Replays must retain the original idempotency key.

## Explicit manual Nacos smoke

This gate is excluded from default auto tests; no real Nacos credentials have been verified by this implementation. Supply all of `VAULT_MANUAL_NACOS_SERVER_ADDRESS` (loopback `127.0.0.1:<port>`), `NAMESPACE`, `GROUP`, `INSTANCE_IP` (exactly `127.0.0.1`), `INSTANCE_PORT`, `USERNAME`, `PASSWORD`, each with the `VAULT_MANUAL_NACOS_` prefix, plus the automatic MySQL variables and actual server binary. The Group must have no other healthy `vault-service` instances. No credentials are guessed and no accounts, namespaces or configuration are created.

Both `ALIBABA_CLOUD_ACCESS_KEY_ID` and `ALIBABA_CLOUD_ACCESS_KEY_SECRET` must be empty for enabled Go Nacos; the test rejects them without stripping the environment. Create a dedicated **0700** directory with `mktemp -d`, and supply its absolute path as `VAULT_MANUAL_NACOS_PRIVATE_DIR`. Surefire sets `JM.LOG.PATH` before the manual fork loads SDK classes and disables the SDK default log configuration. Test-only Logback turns Nacos logging off. The exact discovery property `spring.cloud.nacos.discovery.JM.SNAPSHOT.PATH` points to an owned child inside this directory; cache loading is disabled, but writes may still occur.

```sh
../mvnw -Pmanual-test -pl paytech-vault-test -am \
  -Dtest='**/manual/NacosDiscoverySmokeTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

`-am` uses the reactor client without installing it. The smoke starts the actual Go server with Nacos enabled, discovers its exact identity through the real Nacos DiscoveryClient, calls `/health/liveness` using a test-only LoadBalancer-backed RestClient, stops the Go child and polls up to 30 seconds for disappearance. `VaultClient` remains Protect/Reveal-only. Each discovery wait is bounded and Surefire bounds the fork to 180 seconds. Nacos is eventually consistent: health is the SDK view, not real-time confirmation. SDK close is **not** proof of background-work quiescence. Go cache/log files are removed only after child death; the manual Java SDK directory is deliberately retained. Inspect/scan and remove only your dedicated manual directory after confirming Maven and the test JVM have exited. Do not delete a shared user-home SDK cache/log tree or claim zero surviving SDK threads from this smoke.

## Server configuration

The server reads `VAULT_RUNTIME_MODE=local|test`, `VAULT_HTTP_ADDRESS`, `VAULT_DB_DSN`, optional `VAULT_ADMIN_CALLERS` (comma-separated, default `operations-console-bff`) and `VAULT_POLICY_REFRESH_INTERVAL` (100ms–5m, default 10s), `VAULT_LOCAL_AES_ROOT_KEY` (base64 32 random bytes), `VAULT_LOCAL_AES_KEY_ID`, explicit `VAULT_SNOWFLAKE_DATACENTER_ID` / `VAULT_SNOWFLAKE_WORKER_ID`, `VAULT_NACOS_ENABLED`, and optional `VAULT_INTERNAL_AUTH_MODE` (`OFF` default) with `VAULT_INTERNAL_AUTH_JWKS_URL` / `VAULT_INSTANCE_ID` (see *Internal caller token*). Enabled discovery additionally needs `VAULT_NACOS_SERVER_ADDRESS`, `NAMESPACE`, `GROUP`, `INSTANCE_IP`, `INSTANCE_PORT`, `USERNAME`, `PASSWORD` with the `VAULT_NACOS_` prefix. There is no policy file: the access policy is read only from the database. Production mode rejects LOCAL AES. Nacos registration happens once at startup and is deregistered at shutdown; the SDK keeps the ephemeral instance alive and re-registers after reconnects, so readiness covers only MySQL and keys. Standard OTLP exporter/sampling variables remain available; resource attributes must be empty. `/health/liveness` and `/health/readiness` are operational endpoints, not public business-client operations.

## Internal caller token (D26)

`VAULT_INTERNAL_AUTH_MODE` selects where the caller identity comes from; the access-rule `Caller` dimension is unchanged.

| Mode | Behaviour |
|---|---|
| `OFF` (default) | Only `X-Caller-Service` is read; `Authorization` is ignored and JWKS is never fetched. Identical to the behaviour before D26. |
| `DUAL` | A valid `Authorization: Bearer <token>` wins and its `sub` is the caller; a missing or invalid token, or unavailable JWKS, falls back to `X-Caller-Service`. |
| `REQUIRED` | Only a valid token is accepted; missing or invalid → `401 INTERNAL_TOKEN_INVALID`, unavailable JWKS → `503 DEPENDENCY_UNAVAILABLE`; `X-Caller-Service` is ignored. |

For `DUAL` and `REQUIRED`, `VAULT_INTERNAL_AUTH_JWKS_URL` (Platform JWKS, absolute http/https URL) and `VAULT_INSTANCE_ID` (this instance, the token's `inst`) are required; under `OFF` both are ignored. The token format matches Foundation `paytech-internal-auth`: ES256 JWS Compact, `iss=platform-support-service`, `aud=vault-service`, `inst`, `purpose`, `jti`, integer `iat`/`exp` with `exp - iat ≤ 300 s`; `exp` is strict and `iat`/`nbf` may lead by 60 s. The verifier uses only the Go standard library (`internal/pkg/internalauth`). JWKS keys are cached for 5 minutes; an unknown `kid` forces at most one refresh per 10 seconds; an expired cache whose refresh fails is dropped (fail closed). The first JWKS fetch happens on the first token-bearing request, so startup never depends on Platform. Health and metrics endpoints never require a token. Business-route rejections are audited like other header failures (caller `UNKNOWN`).

The Java client always sends `X-Caller-Service`. When the host includes `paytech-internal-auth` and has an `InternalTokenSupplier` bean, it also sends `Authorization: Bearer` with `aud` = the Vault service id and purpose `GENERAL`; a supplier failure fails closed as `DEPENDENCY_UNAVAILABLE` without sending the request. Compose and test deployments keep the mode `OFF`.
