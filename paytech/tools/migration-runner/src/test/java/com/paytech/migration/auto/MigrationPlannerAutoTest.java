package com.paytech.migration.auto;

import com.paytech.migration.model.AppliedMigration;
import com.paytech.migration.model.MigrationPlan;
import com.paytech.migration.model.MigrationScript;
import com.paytech.migration.model.MigrationTarget;
import com.paytech.migration.model.ViolationCode;
import com.paytech.migration.service.ChecksumCalculator;
import com.paytech.migration.service.ChecksumCalculatorImpl;
import com.paytech.migration.service.MigrationPlanner;
import com.paytech.migration.service.MigrationPlannerImpl;
import com.paytech.migration.sql.SqlScriptSplitterImpl;
import com.paytech.migration.test.Fixtures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 目录校验（连续、重复、超前、内容）与记录校验（校验和漂移、失败记录、N-1 并存）。
 *
 * @see MigrationPlanner#plan(MigrationTarget, String, List)
 * @author Claude
 */
class MigrationPlannerAutoTest {

    private final ChecksumCalculator checksumCalculator = new ChecksumCalculatorImpl();

    private final MigrationPlanner planner = new MigrationPlannerImpl(new SqlScriptSplitterImpl(), checksumCalculator);

    @Test
    void test_normalFlow() {
        MigrationPlan plan = planner.plan(target("planner-valid", 3, 3), "mor_ledger",
            List.of(applied("planner-valid", "V1__create-table-1.sql")));

        Assertions.assertEquals(List.of(), plan.violations());
        Assertions.assertEquals(1, plan.currentVersion());
        Assertions.assertEquals(List.of("V2__create-table-2.sql", "V3__create-table-3.sql"), scripts(plan));
        Assertions.assertEquals(1, plan.pending().get(0).statements().size());
    }

    @Test
    void test_targetBelowManifest() {
        MigrationPlan plan = planner.plan(target("planner-valid", 3, 2), "mor_ledger", List.of());

        Assertions.assertEquals(List.of(), plan.violations());
        Assertions.assertEquals(List.of("V1__create-table-1.sql", "V2__create-table-2.sql"), scripts(plan));
    }

    @Test
    void test_versionGap() {
        MigrationPlan plan = planner.plan(target("planner-gap", 3, 3), "mor_ledger", List.of());

        Assertions.assertEquals(List.of("VERSION_GAP@2"), codes(plan));
    }

    @Test
    void test_duplicateVersion() {
        MigrationPlan plan = planner.plan(target("planner-duplicate", 2, 2), "mor_ledger", List.of());

        Assertions.assertEquals(List.of("DUPLICATE_VERSION@1"), codes(plan));
        Assertions.assertTrue(plan.violations().get(0).message().contains("V1__create-table-a.sql 与 V1__create-table-b.sql"));
    }

    @Test
    void test_scriptAheadOfTarget() {
        MigrationPlan plan = planner.plan(target("planner-valid", 2, 2), "mor_ledger", List.of());

        Assertions.assertEquals(List.of("SCRIPT_AHEAD_OF_TARGET@3"), codes(plan));
    }

    @Test
    void test_targetScriptMissing() {
        MigrationPlan plan = planner.plan(target("planner-valid", 4, 4), "mor_ledger", List.of());

        Assertions.assertEquals(List.of("TARGET_SCRIPT_MISSING@4"), codes(plan));
    }

    @Test
    void test_checksumDrift() {
        AppliedMigration drifted = new AppliedMigration(1, "create-table-1", "V1__create-table-1.sql", "0".repeat(64),
            "migration_test", Instant.now(), 3, true, null);
        AppliedMigration renamed = new AppliedMigration(2, "old-name", "V2__old-name.sql",
            checksum("planner-valid", "V2__create-table-2.sql"), "migration_test", Instant.now(), 3, true, null);

        MigrationPlan plan = planner.plan(target("planner-valid", 3, 3), "mor_ledger", List.of(drifted, renamed));

        Assertions.assertEquals(List.of("CHECKSUM_MISMATCH@1", "SCRIPT_NAME_MISMATCH@2"), codes(plan));
        Assertions.assertEquals(List.of("V3__create-table-3.sql"), scripts(plan));
    }

    @Test
    void test_appliedRecordViolations() {
        AppliedMigration failed = new AppliedMigration(2, "create-table-2", "V2__create-table-2.sql",
            checksum("planner-valid", "V2__create-table-2.sql"), "migration_test", Instant.now(), 3, false, "boom");
        AppliedMigration third = applied("planner-valid", "V3__create-table-3.sql");

        MigrationPlan plan = planner.plan(target("planner-valid", 3, 3), "mor_ledger", List.of(failed, third));

        Assertions.assertEquals(List.of("FAILED_MIGRATION@2", "APPLIED_VERSION_GAP@1"), codes(plan));
        Assertions.assertEquals(3, plan.currentVersion());
    }

    @Test
    void test_databaseAheadOfManifest() {
        AppliedMigration fromReleaseN = new AppliedMigration(3, "from-release-n", "V3__from-release-n.sql",
            "f".repeat(64), "migration_test", Instant.now(), 3, true, null);
        List<AppliedMigration> applied = List.of(applied("planner-previous", "V1__create-table-1.sql"),
            applied("planner-previous", "V2__create-table-2.sql"), fromReleaseN);

        MigrationPlan plan = planner.plan(target("planner-previous", 2, 2), "mor_ledger", applied);

        Assertions.assertEquals(List.of(), plan.violations());
        Assertions.assertEquals(3, plan.currentVersion());
        Assertions.assertEquals(List.of(), plan.pending());
    }

    @Test
    void test_invalidContent() {
        MigrationPlan plan = planner.plan(target("planner-invalid", 4, 4), "mor_ledger", List.of());

        Assertions.assertEquals(List.of("INVALID_SCRIPT_NAME@null", "FORBIDDEN_STATEMENT@1", "SQL_SYNTAX@2",
            "EMPTY_SCRIPT@3", "FORBIDDEN_STATEMENT@4"), codes(plan));
        Assertions.assertTrue(plan.violations().get(0).message().contains("v5_Bad_Name.sql"));
        Assertions.assertTrue(plan.violations().get(2).message().contains("第 1 行"));
    }

    @Test
    void test_missingDirectory() {
        MigrationPlan plan = planner.plan(new MigrationTarget("ledger", "mor_ledger", 1, 1, Path.of("does-not-exist")),
            "mor_ledger", List.of());

        Assertions.assertEquals(List.of("TARGET_SCRIPT_MISSING@1"), codes(plan));
        Assertions.assertEquals(List.of(), plan.pending());
    }

    /**
     * fixture 目录下 ledger 服务的迁移目标。
     *
     * @param fixture fixture 目录名
     * @param manifestVersion 清单版本
     * @param targetVersion 执行目标版本
     * @return 迁移目标
     */
    private static MigrationTarget target(String fixture, int manifestVersion, int targetVersion) {
        return new MigrationTarget("ledger", "mor_ledger", manifestVersion, targetVersion,
            Fixtures.resource("migrations/" + fixture).resolve("ledger"));
    }

    /**
     * 与 fixture 文件一致的成功记录。
     *
     * @param fixture fixture 目录名
     * @param script 文件名
     * @return 记录
     */
    private AppliedMigration applied(String fixture, String script) {
        int version = Integer.parseInt(script.substring(1, script.indexOf("__")));
        String description = script.substring(script.indexOf("__") + 2, script.length() - ".sql".length());
        return new AppliedMigration(version, description, script, checksum(fixture, script), "migration_test",
            Instant.now(), 3, true, null);
    }

    /**
     * fixture 文件的校验和。
     *
     * @param fixture fixture 目录名
     * @param script 文件名
     * @return 校验和
     */
    private String checksum(String fixture, String script) {
        try {
            return checksumCalculator.checksum(Files.readString(target(fixture, 1, 1).migrationsDirectory()
                .resolve(script)));
        } catch (IOException exception) {
            return Assertions.fail(exception);
        }
    }

    /**
     * 校验失败的 {@code CODE@version} 列表。
     *
     * @param plan 计划
     * @return 列表
     */
    private static List<String> codes(MigrationPlan plan) {
        return plan.violations().stream().map(violation -> violation.code() + "@" + violation.version()).toList();
    }

    /**
     * 待执行文件名列表。
     *
     * @param plan 计划
     * @return 文件名
     */
    private static List<String> scripts(MigrationPlan plan) {
        return plan.pending().stream().map(MigrationScript::script).toList();
    }
}
