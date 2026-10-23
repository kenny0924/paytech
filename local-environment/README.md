# Code-MoR Local Base Environment

This directory contains the isolated local `base` infrastructure profile and the optional `scheduler`, `storage`, `mail`, `vault` and `analytics` profiles. It does not start Java services, create business tables, or use existing containers from other projects.

## Prepare

Use JDK 25 for Java commands:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"
```

Create a private environment file and replace every example value:

```bash
cd local-environment
cp .env.example .env.local
chmod 600 .env.local
```

The committed `.env.example` contains local-only examples, not shared or production credentials. `.env.local` is ignored by Git.

## Validate and start

From the repository root, validate the resolved model without starting or pulling anything:

```bash
node scripts/verify-local-environment.mjs
```

Then start the base profile from this directory when you are ready:

```bash
docker compose --env-file .env.local --profile base config
docker compose --env-file .env.local --profile base up -d
docker compose --env-file .env.local --profile base ps
```

Base-profile endpoints:

| Component | Local endpoint |
|---|---|
| MySQL | `127.0.0.1:13306` |
| Redis | `127.0.0.1:16379` |
| RabbitMQ AMQP | `127.0.0.1:5672` |
| RabbitMQ Management | `http://127.0.0.1:15672` |
| Nacos Client HTTP | `http://127.0.0.1:8848` |
| Nacos Client gRPC | `127.0.0.1:9848` |
| Nacos 3 Console | `http://127.0.0.1:18088` |

## API Gateway Redis tests

The Operations Gateway test module deliberately reuses this project's Redis instead of Testcontainers. Before running Gateway tests, confirm `mor-local-redis-1` is healthy and export the same private password without printing it:

```bash
docker compose --env-file .env.local --profile base ps redis
set -a
source .env.local
set +a
```

Gateway tests must connect only to `127.0.0.1:16379`. Each Maven run uses a unique key namespace and deletes only that namespace with `SCAN`; tests must never run `KEYS *`, `FLUSHDB`, stop the Compose Redis, or use the unrelated host Redis on `6379`. Redis-unavailable tests point to an unused loopback port rather than disrupting this shared local container.

## MinIO storage profile

The optional `storage` profile runs MinIO for Platform file storage. Platform no longer reads storage settings from
configuration: register the instance (type `MINIO`, `https://127.0.0.1:19400`, region `us-east-1`, path-style, the
certificate below, STATIC credentials from `MOR_MINIO_PLATFORM_*`), the bucket `mor-platform-files` and the default
write route `*` through the Operations "存储管理" page (or `/platform-support/storage-admin/v1`); the credentials are
stored in Vault only. Auto tests insert their own instances pointing at `mor-platform-files-test` and use
`MOR_MINIO_ROOT_*` only to create and delete temporary buckets for bucket-check negative cases.
The community MinIO images were withdrawn from Docker Hub/quay.io, so the image is Chainguard's source build pinned by
digest. It serves HTTPS only, on loopback, with a local self-signed certificate.

```bash
sh minio/generate-certs.sh   # once; writes minio/certs/ (gitignored)
docker compose --env-file .env.local --profile storage up -d minio
docker compose --env-file .env.local --profile storage run --rm minio-init   # idempotent
```

- S3 API `https://127.0.0.1:19400` (region `us-east-1`), console `https://127.0.0.1:19401`.
- `minio-init` creates the versioned, SSE-S3 encrypted buckets `mor-platform-files` (local runs),
  `mor-platform-files-test` (auto tests) and `mor-platform-public` (public file bucket: anonymous `GetObject` only,
  no anonymous listing), and the `platform-files` user limited to those buckets. Registering a bucket in
  存储管理 is still done through the Operations API, not by this script.
- Credentials: `MOR_MINIO_*` in `.env.local` (see `.env.example`). Java clients trust
  `local-environment/minio/certs/public.crt`; a browser must trust it too before direct uploads work.
- If compose interpolation stops on `PAYTECH_REDIS_PASSWORD`, export it from your existing Redis password first; the
  storage profile does not touch Redis.

## Vault profile

The optional `vault` profile runs the Go Vault Server as a container (`mor-local/vault-service:development`, scratch image,
non-root, read-only root filesystem, no capabilities). It replaces the host-process Vault started by
`run-service.sh vault`; run only one of the two at a time because both bind `127.0.0.1:18107`.

```bash
GO_BIN=~/sdk/go1.27.1/bin/go ./vault/vault.sh build   # cross-compile linux/arm64, write vault/private/vault.env, build image
./vault/vault.sh up                                    # start/recreate vault-service only (mysql and nacos must be running)
./vault/vault.sh status
./vault/vault.sh stop                                  # deregisters from Nacos on shutdown
```

- Uses `mor_vault` with `vault_app`, reads the access policy only from the database (initialized by the
  [local DML](../requirements/20261014/operations-console-Vault数据类型与手动加解密/db/vault/dml-local.sql);
  `vault/policy.local.json` is kept only as its transcription source and is no longer read), and the same LOCAL AES
  key as the local full-chain material, so existing tokens remain readable.
- Registers `vault-service` in Nacos `mor-local` as `127.0.0.1:18107` for host-run Java services.
- `vault/private/vault.env` (0600, gitignored) holds only the AES root key, key ID and Nacos service account; it is never printed.
- `vault.sh` fills `PAYTECH_REDIS_PASSWORD` from `MOR_REDIS_PASSWORD` for compose interpolation only; Redis is not touched.

## XXL-Job scheduler profile

The scheduler profile adds only XXL-Job Admin `3.4.2`; it reuses this project's isolated MySQL and never reads a
business database. Add strong, matching `MOR_XXL_JOB_ACCESS_TOKEN` and `MOR_XXL_JOB_LOGIN_TOKEN_KEY` values to
`.env.local` before starting it.

On a fresh MySQL volume, `mysql/init/03-xxl-job-schema.sql` and its version marker are applied automatically. For an
existing volume, the one-shot `xxl-job-schema-init` service imports an empty schema, accepts a complete set of eight
vendor tables, records the vetted `3.4.2` source commit, and fails closed on a partial or different schema. It never drops
or rewrites an existing table.

Then start and inspect the scheduler without restarting the already-running base containers:

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.scheduler.yaml \
  --profile base --profile scheduler config
docker compose --env-file .env.local -f compose.yaml -f compose.scheduler.yaml \
  --profile base --profile scheduler up -d xxl-job-admin
docker compose --env-file .env.local -f compose.yaml -f compose.scheduler.yaml \
  --profile base --profile scheduler ps
```

XXL-Job Admin is available only on `http://127.0.0.1:18090`. The upstream bootstrap login is `admin / 123456`; change
that password immediately in the loopback-only console. No sample Executor Group or Job is preloaded. Create one group
per service only after that service exists, and create compensation jobs as `BEAN` jobs with Handler
`compensationEventScan`, routing `SHARDING_BROADCAST`, blocking `SERIAL_EXECUTION`, and failure retry count `0`.

Stop only the scheduler when it is not needed so that MySQL, Redis, RabbitMQ and Nacos remain untouched:

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.scheduler.yaml \
  --profile base --profile scheduler stop xxl-job-admin
```

The stopped one-shot schema container may remain visible as `Exited (0)`; it uses no memory. The Admin log volume is
`mor-local-xxl-job-admin-logs`. XXL-Job's service-side Executor listens on every network
interface even when its registered IP is configured. Native macOS services therefore require a local firewall; deployed
services require container/network-policy isolation and a strong shared Access Token. XXL-Job 3.4.2 also supports remote
GLUE code execution, so this project permits only trusted Admin users and `BEAN` jobs.

XXL-Job is distributed under GPL-3.0. Complete the company's open-source and legal review before embedding this Starter
in a production service or distributing a product that includes it.

The existing host Redis on `6379` and Nexus on `18081` are outside this Compose project. Redis is not reused; Nexus is reused only as the local Maven artifact repository through the developer-owned Settings file documented in `agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md`. This Compose project must not restart, replace, or remove that Nexus container or its volume.

## Analytics profile

The optional `analytics` profile is the Doris dialect manual test environment for Platform data-query. Since the
[2026-10-22 data-query decision](../agent-docs/decisions/2026-10-22-data-query-into-platform-support.md) the former
`paytech/data-service` and its CDC whitelist contract are removed; Doris is one data source type that data-query can
register, and the containers, `mor_data_service` and `data_service_app` stay as the queried database and read-only
account for those manual tests. It contains a single-node Apache Doris 3.0.8 (FE + BE), a Flink 1.20.5 session
cluster with Flink CDC 3.7.0 pipeline connectors, and two one-shot containers. Images, versions and SHA-256 values are
listed under [Versions and checksums](#versions-and-checksums) and in `flink/Dockerfile`.

| Service | Role | Host endpoint |
|---|---|---|
| `doris-fe` | Doris FE, FQDN mode, 768 MiB heap | MySQL protocol `127.0.0.1:19030`, HTTP `http://127.0.0.1:18030` |
| `doris-be` | Doris BE, `mem_limit = 1024M` | none (FE and Flink reach it on the `mor` network) |
| `doris-init` | one-shot: root password, BE registration, database `mor_data_service`, read-only `data_service_app` | none |
| `flink-jobmanager` | Flink JobManager, 10 s checkpoints | Web UI `http://127.0.0.1:18091` (not `18081`, which is the host Nexus) |
| `flink-taskmanager` | Flink TaskManager, 2 slots | none |
| `flink-cdc-submit` | one-shot: submits `flink/pipelines/demo-currency-spec.yaml`; skips when that pipeline is already RUNNING | none |

The demo pipeline only shows how data gets into Doris; it carries no contract. Pipeline files reference passwords only
as `${MOR_*_PASSWORD}`, which `node scripts/verify-local-environment.mjs` checks. Volumes `mor-local-doris-fe-meta` and `mor-local-doris-be-storage`
keep Doris metadata and data; Flink keeps no volume, so a stopped pipeline is re-submitted (and re-snapshotted) on the next
start.

### Prerequisites

1. `base` is running. Analytics services never depend on base services, so starting or stopping them does not recreate
   MySQL.
2. `.env.local` contains `MOR_CDC_DB_PASSWORD`, `MOR_DORIS_ROOT_PASSWORD` and `MOR_DATA_SERVICE_DORIS_PASSWORD`
   (see `.env.example`).
3. MySQL writes a row-based binlog. `compose.yaml` now passes `--binlog-format=ROW --binlog-row-image=FULL
   --gtid-mode=ON --enforce-gtid-consistency=ON --server-id=1`; these flags take effect only when the MySQL container is
   next recreated. Until then, confirm that the running instance already satisfies Flink CDC (MySQL 8.4 defaults to
   `log_bin=ON`, `binlog_format=ROW`, `binlog_row_image=FULL`, `server_id=1`; GTID is optional for Flink CDC):

   ```bash
   set -a; source .env.local; set +a
   docker exec -e MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD" mor-local-mysql-1 mysql -uroot -e \
     "SHOW VARIABLES WHERE Variable_name IN ('log_bin','binlog_format','binlog_row_image','gtid_mode','server_id')"
   ```

4. The CDC account exists. A fresh MySQL volume gets it from `mysql/init/01-create-databases-and-users.sh`. For an
   existing volume, create it once as root. The password is read from `.env.local` and passed over stdin, never echoed:

   ```bash
   set -a; source .env.local; set +a
   printf "CREATE USER IF NOT EXISTS 'cdc_reader'@'%%' IDENTIFIED BY '%s';\nGRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'cdc_reader'@'%%';\nGRANT SELECT ON mor_finance.* TO 'cdc_reader'@'%%';\n" "$MOR_CDC_DB_PASSWORD" \
     | docker exec -i -e MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD" mor-local-mysql-1 mysql -uroot
   ```

   `cdc_reader` gets only `REPLICATION SLAVE, REPLICATION CLIENT` globally and `SELECT` on the demo pipelines' source
   databases; add a `GRANT SELECT` when a demo pipeline needs another database.

### Start, submit, inspect

If interpolation stops on `PAYTECH_REDIS_PASSWORD`, export it first (Redis is not touched). List the analytics services
explicitly so nothing else is started:

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.analytics.yaml --profile analytics config --quiet
docker compose --env-file .env.local -f compose.yaml -f compose.analytics.yaml --profile analytics build flink-jobmanager
docker compose --env-file .env.local -f compose.yaml -f compose.analytics.yaml --profile analytics \
  up -d doris-fe doris-be doris-init flink-jobmanager flink-taskmanager
docker compose --env-file .env.local -f compose.yaml -f compose.analytics.yaml --profile analytics up flink-cdc-submit
docker compose --env-file .env.local -f compose.yaml -f compose.analytics.yaml --profile analytics ps -a
```

`flink-cdc-submit` prints `Pipeline has been submitted to cluster.` or `... already RUNNING; nothing submitted`.
The demo pipeline needs `mor_finance.t_currency_spec` with rows in the running MySQL; if it is missing, apply only that
table's DDL and initial rows from its owner's reviewed SQL by hand (never from an automated script). To watch a change
arrive in Doris, update only the technical column and, after one checkpoint (about 10 s), read it with the read-only
account:

```bash
set -a; source .env.local; set +a
docker exec -e MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD" mor-local-mysql-1 mysql -uroot mor_finance -e \
  "UPDATE t_currency_spec SET update_time = UTC_TIMESTAMP(6) WHERE currency = 'USD'"
docker exec -e MYSQL_PWD="$MOR_DATA_SERVICE_DORIS_PASSWORD" mor-local-doris-fe-1 \
  mysql -h127.0.0.1 -P9030 -udata_service_app -e "SELECT currency, update_time FROM mor_data_service.t_currency_spec"
```

Compose warns about orphan `xxl-job-*` containers when the scheduler file is not included; do not add
`--remove-orphans`.

### Resources

Measured on 2026-10-17 on the Apple M4 Pro / Docker Desktop 15.84 GiB machine, with the pipeline RUNNING and idle:

| Container | Memory used / limit |
|---|---|
| `doris-fe` | 796 MiB / 1.25 GiB |
| `doris-be` | 1.06 GiB / 1.5 GiB |
| `flink-jobmanager` | 318 MiB / 768 MiB |
| `flink-taskmanager` | 504 MiB / 1.25 GiB |
| Total | about 2.7 GiB (limits 4.75 GiB) |

Disk: the images are 5.9 GB compressed (Doris BE 3.2 GB, FE 1.5 GB, Flink 0.58 GB, local Flink CDC layer 0.08 GB),
but Docker Desktop's image store keeps compressed and unpacked layers, so `docker image ls` reports about 17 GB and
`docker system df` grew by about 14 GB on this machine. Check `df -h /` first; `LOCAL_DEVELOPMENT_ENVIRONMENT.md`
asks for at least 15 GiB free after `base`. Do not run analytics alongside the `mor-test` environment and the full base
profile unless Docker has the memory above free. Removing the images afterwards (re-pulled on the next build/start):

```bash
docker image rm mor-local/flink-cdc:1.20.5-cdc3.7.0 flink:1.20.5-scala_2.12-java17 \
  apache/doris:be-3.0.8 apache/doris:fe-3.0.8
```

The BE runs with `SKIP_CHECK_ULIMIT=true` because Docker Desktop's VM keeps `vm.max_map_count` at 262144 (Doris
production requires 2000000). This is a local single-node setting, not a deployment baseline.

### Stop

Stop only the analytics services; base containers keep running and the shared network stays (Compose reports it is
still in use):

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.analytics.yaml --profile analytics down
```

Do not add `-v` unless removal of the two Doris volumes has been explicitly approved.

### Versions and checksums

Moved here from the removed `paytech/data-service/README.md`:

| Component | Version | Pinned as |
|---|---|---|
| Apache Doris FE | 3.0.8 | `apache/doris:fe-3.0.8@sha256:749afa2be75cd58d54a0bdb3edaa805d5974646f67500f094912f8dbc4a96b8f` (arm64 / amd64) |
| Apache Doris BE | 3.0.8 | `apache/doris:be-3.0.8@sha256:a337e2b17a65c86c22a1205467a825d46dc3380c94ba8024e53eea033641f12e` (arm64 / amd64) |
| Apache Flink | 1.20.5 (Java 17) | `flink:1.20.5-scala_2.12-java17@sha256:80401a8ee20e2246718c01303712c10ccaf13961e31316d9b8c4549f7b2cfc9b` |
| Flink CDC distribution | 3.7.0 (Flink 1.20 line) | `flink-cdc-3.7.0-1.20-bin.tar.gz` SHA-256 `c7c48f9011e2835ceebaf25d98074e44716a106248da285d0a723fa738385a78` |
| MySQL pipeline connector | 3.7.0-1.20 | SHA-256 `59ed5bbf92f6e8b7d2291d6365a2d0b12438fb011892d8898b756f1d718339f9` |
| Doris pipeline connector | 3.7.0-1.20 | SHA-256 `72537db0a9b832732f756b0c846dcdc1e83a24d23671e3a126181e4c6be6e626` |
| MySQL JDBC (Flink side) | mysql-connector-java 8.0.27 | SHA-256 `d670e85fa1075a80d91b8f17dbd620d76717bc20c74ab4aea8356e37a8545ffe` |

`flink/Dockerfile` verifies every download with `sha256sum -c` at build time; the values above were first compared with
the upstream `.sha512` (Apache dist) or `.sha1` (Maven Central). The built local image is
`mor-local/flink-cdc:1.20.5-cdc3.7.0`. These versions serve only this local manual-test profile.

## Data-query test fixture

Platform data-query's HTTP entry tests query a dedicated local database instead of any service database
(requirement `requirements/20261022/platform-support-数据查询服务`, Q12). It lives in the `base` MySQL, is local only and
never reaches `mor-test`:

| Object | Purpose |
|---|---|
| `mor_data_query_fixture` | `t_client_fixture` (3 rows), `t_refund_fixture` (10 rows), `t_number_fixture` (0–999, for CROSS JOIN timeout tests) |
| `data_query_fixture_reader` | `SELECT` on `mor_data_query_fixture.*` only; the data source account that may be enabled |
| `data_query_fixture_writer` | `SELECT, INSERT, UPDATE` on the same database; used only to prove that enabling is rejected |

Passwords are `MOR_DATA_QUERY_FIXTURE_READER_PASSWORD` and `MOR_DATA_QUERY_FIXTURE_WRITER_PASSWORD` in `.env.local`.
A new MySQL volume creates the fixture through `mysql/init/05-data-query-fixture.sh` only when both variables reach the
mysql container (like the V2 successor accounts, `compose.yaml` does not pass them yet). An existing MySQL applies the
same SQL once by hand, after review, with the command in the header of
[`mysql/manual/2026-10-22-data-query-fixture.sql`](mysql/manual/2026-10-22-data-query-fixture.sql); the file is
idempotent (accounts re-created, table rows reset) and its header lists the rollback. Tests never create or reset this
database; they fail fast with this preparation instruction when it is missing.

## Stop safely

```bash
docker compose --env-file .env.local --profile base down
```

Do not add `-v` unless removal of the three named MoR volumes has been explicitly approved. Never use a broad Docker prune as part of this project workflow.

## Database boundary

The first MySQL initialization creates empty schemas and one scoped account per owner:

- `mor_cct` / `cct_app` (legacy CCT schema; kept until refactor step 04 is merged and verified)
- `mor_client_master` / `client_master_app` (client-master; test DDL `paytech/client-master/paytech-client-master-test/src/test/resources/init/ddl.sql`)
- `mor_commercial` / `commercial_app` (commercial; test DDL `paytech/commercial/paytech-commercial-test/src/test/resources/init/ddl.sql`)
- `mor_commerce` / `commerce_app` (legacy Commerce schema; kept until refactor step 03 is merged and verified)
- `mor_acquiring` / `acquiring_app` (acquiring; `SELECT, INSERT, UPDATE, DELETE` only; test DDL `paytech/acquiring/paytech-acquiring-test/src/test/resources/init/ddl.sql`; created only when `MOR_ACQUIRING_DB_PASSWORD` reaches the mysql container)
- `mor_counterparty` / `counterparty_app` (counterparty; `SELECT, INSERT, UPDATE, DELETE` only; test DDL `paytech/counterparty/paytech-counterparty-test/src/test/resources/init/ddl.sql`; created only when `MOR_COUNTERPARTY_DB_PASSWORD` reaches the mysql container)
- `mor_payment` / `payment_app` (payment service; the four card tables moved to `mor_card` in refactor step 12 — an existing local `mor_payment` may still contain them until they are dropped manually)
- `mor_channel_connector` / `channel_connector_app` (channel connector; `SELECT, INSERT, UPDATE, DELETE` only)
- `mor_card` / `card_app` (card service; `SELECT, INSERT, UPDATE, DELETE` only; test DDL `paytech/card/paytech-card-test/src/test/resources/init/ddl.sql`; created only when `MOR_CARD_DB_PASSWORD` reaches the mysql container)
- `mor_risk` / `risk_app` (legacy Risk schema; kept until refactor step 06 is merged and verified)
- `mor_risk_decision` / `risk_decision_app` (risk-decision; `SELECT, INSERT, UPDATE, DELETE` only; test DDL `paytech/risk-decision/paytech-risk-decision-test/src/test/resources/init/ddl.sql`; created only when `MOR_RISK_DECISION_DB_PASSWORD` reaches the mysql container)
- `mor_compliance` / `compliance_app` (compliance; `SELECT, INSERT, UPDATE, DELETE` only; test DDL `paytech/compliance/paytech-compliance-test/src/test/resources/init/ddl.sql`; created only when `MOR_COMPLIANCE_DB_PASSWORD` reaches the mysql container)
- `mor_finance` / `finance_app`
- `mor_platform` / `platform_app`
- `mor_vault` / `vault_app`
- `nacos_config` / `nacos`
- `xxl_job` / `xxl_job`
- `paytech_foundation_test` / `foundation_test` (local Java test schema)
- `cdc_reader`: no schema of its own; binlog replication plus `SELECT` on the demo pipelines' source databases (analytics profile)
- `mor_data_query_fixture` / `data_query_fixture_reader` (`SELECT`) and `data_query_fixture_writer` (`SELECT, INSERT, UPDATE`): data-query test fixture, see [Data-query test fixture](#data-query-test-fixture); created only when both `MOR_DATA_QUERY_FIXTURE_*_PASSWORD` variables reach the mysql container

Only `nacos_config`, `xxl_job` and the test-only `mor_data_query_fixture` receive tables during environment initialization. `xxl_job`
contains eight upstream Admin tables plus one local schema-version marker. Business/Vault/Foundation application tables
are never created or rebuilt by Compose, Java startup, Maven or JUnit. Apply each reviewed test Schema/DML manually to
its scoped database before running the owning test suite; tests may clean only rows and namespaces they own. A missing
table must fail fast with the documented preparation command, not trigger `application-mock.yaml` or `spring.sql.init`.

After the first start, sign in to the loopback-only Nacos console and replace any bootstrap administrator password before keeping the environment running. Do not copy that password into `.env.example`, Nacos configuration data, or Git.
