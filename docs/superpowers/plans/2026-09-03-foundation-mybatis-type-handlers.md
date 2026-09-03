# Foundation MyBatis TypeHandlers Implementation Plan

> Implement with a lightweight TDD loop. Superpowers execution skills are intentionally not required for the code phase.

**Goal:** Extract reusable UTC and `CodeEnum` MyBatis mappings into a focused Foundation JAR, consume them from compensation-event and Platform Support, and complete Gateway DataObject field documentation.

**Spec:** `docs/superpowers/specs/2026-09-03-foundation-mybatis-type-handlers-design.md`

## Task 1: Freeze the Foundation handler contracts

**Files:**

- Create `paytech-foundation/paytech-mybatis-support/pom.xml`
- Create `paytech-foundation/paytech-mybatis-support/src/test/java/com/paytech/foundation/mybatis/auto/typehandler/InstantTypeHandlerTest.java`
- Create `paytech-foundation/paytech-mybatis-support/src/test/java/com/paytech/foundation/mybatis/auto/typehandler/CodeEnumTypeHandlerTest.java`

Write behavior-level tests for all JDBC setter/getter overloads, SQL null, UTC conversion, code writes, valid reads, unknown persisted code, duplicate/null enum code and a non-`CodeEnum` enum. Run the module tests and confirm RED because the handlers do not exist.

## Task 2: Implement and govern the Foundation module

**Files:**

- Modify `paytech-foundation/pom.xml`
- Create `paytech-foundation/paytech-mybatis-support/src/main/java/com/paytech/foundation/mybatis/typehandler/InstantTypeHandler.java`
- Create `paytech-foundation/paytech-mybatis-support/src/main/java/com/paytech/foundation/mybatis/typehandler/CodeEnumTypeHandler.java`
- Modify `paytech-foundation/README.md`
- Modify `paytech-build/paytech-dependencies/pom.xml`
- Modify `paytech-build/README.md` when its managed-artifact catalog requires it

Implement only the tested behavior. Add the module to the Foundation reactor and shared BOM, then run:

```bash
./mvnw -pl paytech-mybatis-support -am test
./mvnw -pl paytech-mybatis-support,paytech-compensation-event -am verify
```

Install the updated build BOM and Foundation artifacts into the local Maven repository for downstream Platform verification; do not deploy them.

## Task 3: Reuse the generic enum handler in compensation-event

**Files:**

- Modify `paytech-foundation/paytech-compensation-event/pom.xml`
- Modify `paytech-foundation/paytech-compensation-event/src/main/resources/META-INF/mybatis/CompensationEventMapper.xml`
- Delete `paytech-foundation/paytech-compensation-event/src/main/java/com/paytech/foundation/compensation/persistence/mapper/CompensationEventStatusTypeHandler.java`
- Modify the closest existing compensation enum/database auto test if an explicit regression assertion is needed

Replace only the specialized status handler. Prove existing real-database compensation tests remain green and unknown status codes fail closed.

## Task 4: Type Platform Gateway DataObjects

**Files:**

- Modify `platform-support-service/platform-support-web/pom.xml`
- Modify `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/configuration/PersistenceConfiguration.java`
- Delete `platform-support-service/platform-support-web/src/main/java/com/paytech/platform/support/core/dal/typehandler/InstantTypeHandler.java`
- Modify all five files under `platform-support-web/src/main/java/com/paytech/platform/support/core/dal/dataobject/`
- Add the missing internal `CodeEnum` types under `domain/gatewayconfiguration/`
- Modify `platform-support-web/src/main/java/com/paytech/platform/support/domainservice/repository/impl/MyBatisGatewayConfigurationRepository.java`
- Modify `platform-support-test/src/test/java/com/paytech/platform/support/auto/PlatformSupportDependencyBoundaryAutoTest.java`
- Modify the closest Gateway Snapshot/database auto test

First add an auto-test assertion that reads seeded rows through the production Mapper/Repository and expects typed enum values; confirm RED. Register every enum explicitly, convert internal enum values to string codes at the repository projection boundary, and add Chinese JavaDoc to every record component. Add one transactionally isolated invalid-code case and assert that Snapshot loading fails closed.

Run focused Platform tests, then the full reactor:

```bash
./mvnw -pl platform-support-test -am -Dtest=GatewaySnapshotHttpAutoTest,PlatformSupportDependencyBoundaryAutoTest test
./mvnw verify
```

## Task 5: Synchronize project guidance and verify integration

**Files:**

- Modify `agent-docs/DEVELOPMENT_CONVENTIONS.md`
- Modify `agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md`
- Modify root navigation only where the Foundation module inventory or persistence convention is current authority

Record the stable rule: external API/MQ DTOs keep `String` enum codes; internal DataObjects use typed `CodeEnum` plus explicit common MyBatis handlers; unknown persisted codes fail closed; DataObject fields require meaningful Chinese JavaDoc. Update the Foundation module count.

Run fresh verification in every affected repository, inspect `git diff --check`, and integrate semantic deltas without overwriting the pre-existing dirty Platform test import.
