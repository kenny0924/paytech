package com.paytech.migration.auto;

import com.paytech.migration.cli.MigrationRunnerCli;
import com.paytech.migration.test.CliRun;
import com.paytech.migration.test.Fixtures;
import com.paytech.migration.test.FoundationTestDatabase;
import com.paytech.migration.test.MigrationRecordChecker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.JsonNode;

/**
 * 在 local-environment 的 Foundation 测试库上经真实命令行入口执行迁移：成功、语句失败停止、校验和漂移、并发互斥、
 * 初始化数据一次性执行与环境错误。
 *
 * <p>
 * 每例前后删除 {@code t_mr_test_*} 与 {@code t_schema_migration}、{@code t_schema_seed}；不触碰该库其他表。
 *
 * @see MigrationRunnerCli#run(String...)
 * @author Claude
 */
class MigrationExecutorAutoTest {

    private static final String V1 = "V1__create-mr-test-entry.sql";

    private static final String V2 = "V2__add-mr-test-audit.sql";

    private static final String V3 = "V3__broken-statement.sql";

    private FoundationTestDatabase database;

    private MigrationRecordChecker checker;

    @TempDir
    Path workDirectory;

    @BeforeEach
    void prepareDatabase() {
        database = FoundationTestDatabase.connect();
        checker = new MigrationRecordChecker(database);
        Assertions.assertEquals(List.of(), database.dropOwnedTables(), "清理上次中断遗留的测试表失败");
    }

    @AfterEach
    void cleanDatabase() {
        if (database != null) {
            Assertions.assertEquals(List.of(), database.dropOwnedTables(), "测试表清理失败");
        }
    }

    @Test
    void test_normalFlow() {
        Path sqlRoot = Fixtures.copy("migrations/executor", workDirectory.resolve("sql"));

        CliRun plan = run("plan", sqlRoot, "ledger-3.yaml", "--json").exit(0);
        checker.tables();
        Assertions.assertEquals(0, plan.service().path("currentVersion").asInt());
        Assertions.assertEquals(3, plan.service().path("pending").size());

        CliRun migrate = run("migrate", sqlRoot, "ledger-3.yaml", "--service", "ledger", "--target", "2", "--json")
            .exit(0);
        Assertions.assertEquals(List.of(V1, V2), executedScripts(migrate.service()));
        checker.migrations("V1:" + V1 + ":1", "V2:" + V2 + ":1")
            .tables("t_mr_test_audit", "t_mr_test_entry", "t_schema_migration", "t_schema_seed");
        Assertions.assertEquals(List.of("1|a;b|v2", "2|it's; fine|null"),
            checker.strings("SELECT CONCAT_WS('|', entry_id, label, IFNULL(note, 'null')) FROM t_mr_test_entry "
                + "ORDER BY entry_id"));

        run("plan", sqlRoot, "ledger-3.yaml").exit(0)
            .contains("plan ledger (database paytech_foundation_test): current V2, target V3")
            .contains("pending   " + V3).notContains("13306").notContains(database.runnerEnvironment()
                .get("PAYTECH_DB_PASSWORD_LEDGER"));
        run("migrate", sqlRoot, "ledger-3.yaml", "--service", "ledger", "--target", "2").exit(0)
            .notContains("executed");
    }

    @Test
    void test_failedStatementStops() {
        Path sqlRoot = Fixtures.copy("migrations/executor", workDirectory.resolve("sql"));
        run("migrate", sqlRoot, "ledger-3.yaml", "--service", "ledger", "--target", "2").exit(0);

        CliRun failed = run("migrate", sqlRoot, "ledger-3.yaml", "--json").exit(2);
        JsonNode failure = failed.service().path("failure");
        Assertions.assertEquals(3, failure.path("version").asInt());
        Assertions.assertEquals(2, failure.path("statementIndex").asInt());
        Assertions.assertEquals(3, failure.path("line").asInt());
        Assertions.assertEquals("42S02", failure.path("sqlState").asString());
        checker.migrations("V1:" + V1 + ":1", "V2:" + V2 + ":1", "V3:" + V3 + ":0")
            .errorContains(3, "第 2 条语句（第 3 行）失败: [42S02/1146]");
        Assertions.assertEquals(List.of("1"), checker.strings("SELECT COUNT(*) FROM information_schema.COLUMNS "
            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_mr_test_entry' AND COLUMN_NAME = 'extra'"));

        run("validate", sqlRoot, "ledger-3.yaml").exit(1).contains("[FAILED_MIGRATION]")
            .contains("V3__broken-statement.sql 执行失败");
        run("migrate", sqlRoot, "ledger-3.yaml").exit(1).contains("[FAILED_MIGRATION]").notContains("executed");
        checker.migrations("V1:" + V1 + ":1", "V2:" + V2 + ":1", "V3:" + V3 + ":0");
    }

    @Test
    void test_checksumDrift() throws IOException {
        Path sqlRoot = Fixtures.copy("migrations/executor", workDirectory.resolve("sql"));
        run("migrate", sqlRoot, "ledger-3.yaml", "--service", "ledger", "--target", "2").exit(0);
        run("validate", sqlRoot, "ledger-3.yaml").exit(0);

        Path v1 = sqlRoot.resolve("ledger/db/migrations").resolve(V1);
        Files.writeString(v1, Files.readString(v1).replace("\n", "\r\n"));
        run("validate", sqlRoot, "ledger-3.yaml").exit(0);

        Files.writeString(v1, "-- edited after execution\n", StandardOpenOption.APPEND);
        run("validate", sqlRoot, "ledger-3.yaml").exit(1).contains("ledger V1 [CHECKSUM_MISMATCH]");
        run("migrate", sqlRoot, "ledger-3.yaml", "--service", "ledger", "--target", "2").exit(1);
    }

    @Test
    void test_concurrentRunnersExecuteOnce() throws Exception {
        Path sqlRoot = Fixtures.resource("migrations/concurrent");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<CliRun> first = pool.submit(() -> awaitAndRun(start, sqlRoot));
            Future<CliRun> second = pool.submit(() -> awaitAndRun(start, sqlRoot));
            start.countDown();
            List<Integer> executed = List.of(executedCount(first.get(60, TimeUnit.SECONDS)),
                executedCount(second.get(60, TimeUnit.SECONDS)));

            Assertions.assertTrue(executed.equals(List.of(2, 0)) || executed.equals(List.of(0, 2)), executed::toString);
        } finally {
            pool.shutdownNow();
            Assertions.assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "并发执行器未结束，测试库不可清理");
        }
        checker.migrations("V1:V1__create-mr-test-entry.sql:1", "V2:V2__insert-mr-test-entry.sql:1");
        Assertions.assertEquals(List.of("1"), checker.strings("SELECT COUNT(*) FROM t_mr_test_entry"));
    }

    @Test
    void test_seedAppliedOnce() throws IOException {
        Path sqlRoot = Fixtures.copy("migrations/seed", workDirectory.resolve("sql"));
        run("seed", sqlRoot, "ledger-2.yaml").exit(1).contains("[SEED_BEFORE_MIGRATION]");
        checker.seeds();

        run("migrate", sqlRoot, "ledger-2.yaml").exit(0);
        run("seed", sqlRoot, "ledger-2.yaml").exit(0).contains("seed      APPLIED");
        run("seed", sqlRoot, "ledger-2.yaml").exit(0).contains("seed      ALREADY_APPLIED");
        checker.seeds("ledger-initial-data.sql");
        Assertions.assertEquals(List.of("100|seed|initial; data"), checker.strings(
            "SELECT CONCAT_WS('|', entry_id, label, note) FROM t_mr_test_entry WHERE entry_id = 100"));
        Assertions.assertEquals(List.of("1"), checker.strings("SELECT COUNT(*) FROM t_mr_test_audit"));

        Path seedFile = sqlRoot.resolve("ledger/db/seed/ledger-initial-data.sql");
        Files.writeString(seedFile, "INSERT INTO `t_mr_test_audit` (`audit_id`, `message`) VALUES (101, 'x');\n",
            StandardOpenOption.APPEND);
        run("seed", sqlRoot, "ledger-2.yaml").exit(1).contains("[SEED_CHECKSUM_MISMATCH]");
        run("validate", sqlRoot, "ledger-2.yaml").exit(1).contains("[SEED_CHECKSUM_MISMATCH]");
        Assertions.assertEquals(List.of("1"), checker.strings("SELECT COUNT(*) FROM t_mr_test_audit"));
    }

    @Test
    void test_seedFailureRollsBack() throws IOException {
        Path sqlRoot = Fixtures.copy("migrations/seed", workDirectory.resolve("sql"));
        Path seedFile = sqlRoot.resolve("ledger/db/seed/ledger-initial-data.sql");
        Files.writeString(seedFile, "INSERT INTO `t_mr_test_missing` (`id`) VALUES (1);\n", StandardOpenOption.APPEND);
        run("migrate", sqlRoot, "ledger-2.yaml").exit(0);

        run("seed", sqlRoot, "ledger-2.yaml", "--json").exit(2).contains("\"statementIndex\" : 3");
        checker.seeds();
        Assertions.assertEquals(List.of("0"), checker.strings("SELECT COUNT(*) FROM t_mr_test_entry WHERE entry_id = 100"));

        Files.writeString(seedFile, "CREATE TABLE `t_mr_test_seeded` (`id` INT NOT NULL, PRIMARY KEY (`id`));\n");
        run("seed", sqlRoot, "ledger-2.yaml").exit(1).contains("[SEED_DDL_FORBIDDEN]");
        checker.seeds().tables("t_mr_test_audit", "t_mr_test_entry", "t_schema_migration", "t_schema_seed");
    }

    @Test
    void test_environmentErrors() {
        Path sqlRoot = Fixtures.resource("migrations/executor");
        Path manifest = Fixtures.resource("manifests/ledger-3.yaml");

        CliRun.invoke(Map.of(), "plan", "--manifest", manifest.toString(), "--sql-root", sqlRoot.toString()).exit(3)
            .contains("PAYTECH_DB_URL_LEDGER, PAYTECH_DB_USER_LEDGER, PAYTECH_DB_PASSWORD_LEDGER");
        Map<String, String> wrongSchema = new HashMap<>(database.runnerEnvironment());
        wrongSchema.remove("PAYTECH_DB_SCHEMA_LEDGER");
        CliRun.invoke(wrongSchema, "migrate", "--manifest", manifest.toString(), "--sql-root", sqlRoot.toString())
            .exit(3).contains("连接到的库为 paytech_foundation_test，预期 mor_ledger");
        checker.tables();

        run("migrate", sqlRoot, "ledger-3.yaml", "--service", "ledger", "--target", "4").exit(3)
            .contains("--target 4 高于实例清单 versions.schema.ledger=3");
        run("migrate", sqlRoot, "ledger-3.yaml", "--target", "2").exit(3).contains("必须同时指定 --service");
        run("plan", sqlRoot, "ledger-3.yaml", "--service", "account").exit(3).contains("未登记服务: account");
        run("drop", sqlRoot, "ledger-3.yaml", "--json").exit(3).contains("\"error\" : \"未知子命令: drop");
        CliRun.invoke(Map.of(), "--help").exit(0).contains("exit codes: 0 success");
        checker.tables();
    }

    /**
     * 以测试库环境调用命令行入口。
     *
     * @param command 子命令
     * @param sqlRoot SQL 根目录
     * @param manifest {@code manifests/} 下的清单文件名
     * @param options 其他参数
     * @return 结果
     */
    private CliRun run(String command, Path sqlRoot, String manifest, String... options) {
        List<String> args = new ArrayList<>(List.of(command, "--manifest",
            Fixtures.resource("manifests/" + manifest).toString(), "--sql-root", sqlRoot.toString()));
        args.addAll(List.of(options));
        return CliRun.invoke(database.runnerEnvironment(), args.toArray(String[]::new));
    }

    /**
     * 等待同时开始后执行一次 migrate。
     *
     * @param start 起跑信号
     * @param sqlRoot SQL 根目录
     * @return 结果
     * @throws InterruptedException 被中断
     */
    private CliRun awaitAndRun(CountDownLatch start, Path sqlRoot) throws InterruptedException {
        start.await();
        return run("migrate", sqlRoot, "ledger-2.yaml", "--json").exit(0);
    }

    /**
     * 本次执行的版本数。
     *
     * @param run 结果
     * @return 数量
     */
    private static int executedCount(CliRun run) {
        return run.service().path("executed").size();
    }

    /**
     * 本次执行的文件名。
     *
     * @param service 服务结果
     * @return 文件名
     */
    private static List<String> executedScripts(JsonNode service) {
        return service.path("executed").valueStream().map(node -> node.path("script").asString()).toList();
    }
}
