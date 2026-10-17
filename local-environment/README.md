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

The optional `storage` profile runs MinIO for Platform onboarding file storage (`files.onboarding.storage-provider=MINIO`).
The community MinIO images were withdrawn from Docker Hub/quay.io, so the image is Chainguard's source build pinned by
digest. It serves HTTPS only, on loopback, with a local self-signed certificate.

```bash
sh minio/generate-certs.sh   # once; writes minio/certs/ (gitignored)
docker compose --env-file .env.local --profile storage up -d minio
docker compose --env-file .env.local --profile storage run --rm minio-init   # idempotent
```

- S3 API `https://127.0.0.1:19400` (region `us-east-1`), console `https://127.0.0.1:19401`.
- `minio-init` creates the versioned, SSE-S3 encrypted buckets `mor-platform-files` (local runs) and
  `mor-platform-files-test` (auto tests) and the `platform-files` user limited to those buckets.
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

The optional `analytics` profile backs the data service (`paytech/data-service`, decisions D25 / D35). It contains a
single-node Apache Doris 3.0.8 (FE + BE), a Flink 1.20.5 session cluster with Flink CDC 3.7.0 pipeline connectors,
and two one-shot containers. Images, versions and SHA-256 values are listed in
[`paytech/data-service/README.md`](../paytech/data-service/README.md#版本与校验值) and `flink/Dockerfile`.

| Service | Role | Host endpoint |
|---|---|---|
| `doris-fe` | Doris FE, FQDN mode, 768 MiB heap | MySQL protocol `127.0.0.1:19030`, HTTP `http://127.0.0.1:18030` |
| `doris-be` | Doris BE, `mem_limit = 1024M` | none (FE and Flink reach it on the `mor` network) |
| `doris-init` | one-shot: root password, BE registration, database `mor_data_service`, read-only `data_service_app` | none |
| `flink-jobmanager` | Flink JobManager, 10 s checkpoints | Web UI `http://127.0.0.1:18091` (not `18081`, which is the host Nexus) |
| `flink-taskmanager` | Flink TaskManager, 2 slots | none |
| `flink-cdc-submit` | one-shot: submits `flink/pipelines/demo-currency-spec.yaml`; skips when that pipeline is already RUNNING | none |

Every pipeline file may reference only tables in `paytech/data-service/cdc/whitelist.yaml`; `node
scripts/verify-local-environment.mjs` enforces it. Volumes `mor-local-doris-fe-meta` and `mor-local-doris-be-storage`
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

   `cdc_reader` gets only `REPLICATION SLAVE, REPLICATION CLIENT` globally and `SELECT` on whitelisted databases; add a
   `GRANT SELECT` when a new database enters the whitelist.

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
The demo update and Doris query are in [`paytech/data-service/README.md`](../paytech/data-service/README.md#本地演示人工执行).
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

Images add about 5.3 GB of disk (Doris FE 1.5 GB, BE 3.2 GB, Flink CDC image 0.66 GB). Do not run analytics alongside
the `mor-test` environment and the full base profile unless Docker has that much free memory.

The BE runs with `SKIP_CHECK_ULIMIT=true` because Docker Desktop's VM keeps `vm.max_map_count` at 262144 (Doris
production requires 2000000). This is a local single-node setting, not a deployment baseline.

### Stop

Stop only the analytics services; base containers keep running and the shared network stays (Compose reports it is
still in use):

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.analytics.yaml --profile analytics down
```

Do not add `-v` unless removal of the two Doris volumes has been explicitly approved.

## Stop safely

```bash
docker compose --env-file .env.local --profile base down
```

Do not add `-v` unless removal of the three named MoR volumes has been explicitly approved. Never use a broad Docker prune as part of this project workflow.

## Database boundary

The first MySQL initialization creates empty schemas and one scoped account per owner:

- `mor_cct` / `cct_app`
- `mor_commerce` / `commerce_app`
- `mor_payment` / `payment_app`
- `mor_risk` / `risk_app`
- `mor_finance` / `finance_app`
- `mor_platform` / `platform_app`
- `mor_vault` / `vault_app`
- `nacos_config` / `nacos`
- `xxl_job` / `xxl_job`
- `paytech_foundation_test` / `foundation_test` (local Java test schema)
- `cdc_reader`: no schema of its own; binlog replication plus `SELECT` on CDC-whitelisted databases (analytics profile)

Only `nacos_config` and `xxl_job` receive infrastructure-owned tables during environment initialization. `xxl_job`
contains eight upstream Admin tables plus one local schema-version marker. Business/Vault/Foundation application tables
are never created or rebuilt by Compose, Java startup, Maven or JUnit. Apply each reviewed test Schema/DML manually to
its scoped database before running the owning test suite; tests may clean only rows and namespaces they own. A missing
table must fail fast with the documented preparation command, not trigger `application-mock.yaml` or `spring.sql.init`.

After the first start, sign in to the loopback-only Nacos console and replace any bootstrap administrator password before keeping the environment running. Do not copy that password into `.env.example`, Nacos configuration data, or Git.
