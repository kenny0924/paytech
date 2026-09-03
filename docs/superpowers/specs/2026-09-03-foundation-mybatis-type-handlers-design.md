# Foundation MyBatis TypeHandler Design

Date: 2026-09-03

Status: Approved

## Goal

Move reusable JDBC/MyBatis value conversion out of Platform Support into one focused Foundation library, use typed `CodeEnum` values in internal DataObjects, and document every persisted field without changing database schema or external API contracts.

## Decisions

### Focused Foundation module

Add the plain JAR `paytech-mybatis-support`. It depends only on `paytech-common-core` and MyBatis and contains no Spring auto-configuration. This keeps database concerns out of `paytech-common-core` and web concerns out of the persistence boundary.

The module owns:

- `InstantTypeHandler`, preserving the explicit UTC conversion between MySQL `DATETIME(6)` and `Instant`;
- `CodeEnumTypeHandler<E extends Enum<E> & CodeEnum>`, mapping persisted string codes through `CodeEnum.getCode()`.

Consumers register handlers explicitly. The module does not scan packages, replace MyBatis defaults globally, or depend on business enums.

### Enum conversion contract

The generic enum handler builds one immutable code-to-value map for the supplied enum class. Null SQL values remain null. Null codes, duplicate codes, unsupported enum classes, and unknown persisted codes fail immediately; an unknown database code is surfaced as `SQLException` with the enum type and rejected value.

The existing compensation-event status mapping becomes a second real consumer of this shared handler and removes its duplicate specialized implementation.

### Platform persistence boundary

Platform Gateway DataObjects use typed enums for every closed persisted domain: route channel, configuration status, HTTP method, security profile, authorization mode, request/response cookie mode, header use, guard rule type, and guard dimension. Contract-visible Code definitions live in `platform-support-api` under `api/enums`; internal-only configuration status and header-use definitions live in `platform-support-web` under `domain/enums`. The Web module reuses API definitions instead of duplicating the same Code catalog. The existing Platform configuration explicitly registers each enum with `CodeEnumTypeHandler` and registers the shared `InstantTypeHandler`.

Repository projections convert typed values back through `getCode()`. The public `platform-support-api` request/response fields remain `String`-coded; the API enum package publishes stable Code definitions but is never used as a JSON DTO field type. Adding future enum values therefore does not create enum-deserialization coupling for consumers. Existing compiler validation remains as a defensive contract boundary.

### DataObject documentation

All five Gateway DataObjects retain their current records and receive Chinese JavaDoc on every record component. Comments explain business identity, nullable conditions, units, ownership, and enum meaning instead of repeating field names.

## Error and compatibility behavior

- No table, SQL column, seed row, route contract, or external DTO changes.
- A valid existing code maps identically to today.
- An unknown database enum code fails the complete Snapshot read closed rather than becoming null or leaking into a response.
- UTC timestamp behavior remains byte-for-byte equivalent at the JDBC boundary.
- No Flyway, Liquibase, Testcontainers, runtime schema initialization, or hidden Spring behavior is introduced.

## Verification

Use a lightweight TDD loop:

1. Foundation tests first prove UTC `Instant` reads/writes/nulls and `CodeEnum` reads/writes/nulls/invalid definitions/unknown codes.
2. Existing compensation-event database tests prove the shared handler works through its real mapper.
3. Platform local-MySQL auto tests prove typed enum mapping across Mapper, Repository and Snapshot HTTP flow, including fail-closed behavior for an invalid persisted code.
4. Run Foundation, build-governance, Platform, dependency-boundary, coverage and diff checks.

## Non-goals

- No external API enum fields.
- No generic enum catalog, reflection scanning, annotation processor or persistence starter.
- No changes to Gateway runtime behavior or Platform database design.
- No replacement of business validation by the TypeHandler.
