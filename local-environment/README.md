# Code-MoR Local Base Environment

This directory contains the isolated local `base` infrastructure profile and the optional `scheduler` profile. It does not start Java services, create business tables, or use existing containers from other projects.

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

Only `nacos_config` and `xxl_job` receive infrastructure-owned tables during environment initialization. `xxl_job`
contains eight upstream Admin tables plus one local schema-version marker. Business/Vault/Foundation application tables
are never created or rebuilt by Compose, Java startup, Maven or JUnit. Apply each reviewed test Schema/DML manually to
its scoped database before running the owning test suite; tests may clean only rows and namespaces they own. A missing
table must fail fast with the documented preparation command, not trigger `application-mock.yaml` or `spring.sql.init`.

After the first start, sign in to the loopback-only Nacos console and replace any bootstrap administrator password before keeping the environment running. Do not copy that password into `.env.example`, Nacos configuration data, or Git.
