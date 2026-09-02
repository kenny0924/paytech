# Manual Infrastructure and Platform Runtime Simplification

Status: approved by the user on 2026-09-02.

## Decision

Code-MoR does not use Flyway, Liquibase or Testcontainers. MySQL, Redis, RabbitMQ and other middleware are started and maintained through the repository-owned `local-environment`. Tests connect to those local services and fail fast when a dependency is unavailable.

Each implemented service keeps:

- one reviewed current-schema DDL file;
- one reviewed initialization DML file containing all required initial rows;
- explicit operator instructions for applying both files;
- no application-startup schema migration, repair or seed logic.

Historical plans may still describe Flyway/Testcontainers decisions that were valid when written. This decision supersedes those implementation-mechanism sections without changing business ownership, table structure or API contracts.

## Platform Support simplification

Platform Support keeps the PLT-I20 chain:

```text
GatewaySnapshotController
  -> QueryGatewaySnapshotProcessor
  -> GatewaySnapshotService
  -> GatewayConfigurationRepository
  -> GatewayConfigurationMapper
  -> local MySQL
```

The following runtime seed framework is removed because initialization is now operator-owned SQL:

- the aggregate seed object and full-graph validator;
- Java password/JWK seed construction and seed integrity hashing;
- runtime seed Service/Repository/Mapper and concurrent seed arbitration;
- seed readiness state and seed-specific metrics;
- the Platform-specific wrapper around the Foundation `IdGenerator`.

Spring Boot readiness is used for process lifecycle. A small Gateway Snapshot health indicator may query the same production Service and report unavailable when the manually initialized catalog is missing or invalid. The Snapshot HTTP endpoint continues to fail closed with its stable `503` contract.

## Test boundary

Platform tests use the local MySQL 8.4 service on `127.0.0.1:13306`. They do not apply DDL/DML automatically and do not create or destroy containers. The suite is organized around a small number of production-chain scenarios plus focused domain cases that materially protect Gateway route validation. Coverage is evidence, not a reason to add method-by-method tests or production abstractions.

The test suite may update rows it owns but must restore them. It must never drop the shared schema or remove unrelated local data.
