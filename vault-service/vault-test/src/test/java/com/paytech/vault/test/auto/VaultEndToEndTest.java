package com.paytech.vault.test.auto;

import com.paytech.vault.client.*;
import com.paytech.vault.test.support.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.net.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import static org.junit.jupiter.api.Assertions.*;

/** 真正的 Java → Go → 本地 MySQL 验收；每个场景拥有独立运行标识和进程。 */
class VaultEndToEndTest {
    @Test
    void test_missingCachedMavenNeverDownloadsOrCreatesWrapperArtifacts() throws Exception {
        Path root = Files.createTempDirectory("vault-wrapper-test-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        try {
            Path marker = root.resolve("network-attempt");
            for (String name : new String[]{"curl", "wget"}) {
                Path tool = root.resolve(name);
                Files.writeString(tool, "#!/bin/sh\ntouch \"$VAULT_PREFLIGHT_MARKER\"\nexit 97\n");
                Files.setPosixFilePermissions(tool, PosixFilePermissions.fromString("rwx------"));
            }
            Path mavenHome = Files.createDirectory(root.resolve("empty-maven-home"));
            var builder = new ProcessBuilder("../mvnw", "--version").redirectErrorStream(true).redirectOutput(root.resolve("output.log").toFile());
            var environment = builder.environment();
            environment.put("PATH", root + ":" + environment.get("PATH"));
            environment.put("MAVEN_USER_HOME", mavenHome.toString());
            environment.put("VAULT_VERIFY_CACHED_MAVEN_ONLY", "true");
            environment.put("VAULT_PREFLIGHT_MARKER", marker.toString());
            environment.put("MVNW_USERNAME", "");
            environment.put("MVNW_PASSWORD", "");
            environment.put("MVNW_REPOURL", "");
            var process = builder.start();
            try {
                assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Wrapper 缓存门禁未及时返回");
                assertEquals(1, process.exitValue(), "缺失缓存必须拒绝");
                assertFalse(Files.exists(marker), "验证模式不得尝试下载 Maven");
                try (var entries = Files.list(mavenHome)) { assertEquals(0, entries.count(), "验证模式不得创建 Wrapper 制品目录"); }
                assertTrue(Files.readString(root.resolve("output.log")).contains("PREPARE: prepare cached Maven 3.9.16 wrapper distribution"), "应提供缓存 Maven 准备说明");
            } finally {
                process.destroyForcibly();
                assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Wrapper 缓存门禁进程未停止");
            }
        } finally {
            try (var paths = Files.walk(root)) { for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
        }
    }

    @Test
    void test_nodeLeaseSpansStoppedChildAndDatabaseCleanup() throws Exception {
        try (var database = new VaultDatabaseFixture(); var server = GoVaultProcess.start(database)) {
            server.withClient("a", client -> database.remember(database.call(() -> client.protect(
                    database.command("lease", "REFERENCE_VALUE", TokenMode.REFERENCE, database.canary())))));
            server.close();
            database.assertCounts(1, 1, 1);
            assertThrows(BindException.class, () -> {
                try (var unexpected = new ServerSocket(19128, 1, InetAddress.getByName("127.0.0.1"))) {
                    fail("数据库清理前节点互斥已被释放");
                }
            });
            database.close();
            try (var released = new ServerSocket(19128, 1, InetAddress.getByName("127.0.0.1"))) {
                assertTrue(released.isBound(), "数据库清理后节点互斥未释放");
            }
        }
    }

    @Test
    void test_failedScenarioStillCompletesOwnedCleanupAndReleasesLease() throws Exception {
        assertThrows(IllegalStateException.class, () -> {
            try (var database = new VaultDatabaseFixture(); var server = GoVaultProcess.start(database)) {
                server.withClient("a", client -> database.remember(database.call(() -> client.protect(
                        database.command("failure-cleanup", "REFERENCE_VALUE", TokenMode.REFERENCE, database.canary())))));
                server.close();
                database.assertCounts(1, 1, 1);
                assertThrows(BindException.class, () -> {
                    try (var unexpected = new ServerSocket(19128, 1, InetAddress.getByName("127.0.0.1"))) {
                        fail("异常路径在数据库清理前释放了节点互斥");
                    }
                });
                throw new IllegalStateException("test-only scenario failure");
            }
        });
        try (var released = new ServerSocket(19128, 1, InetAddress.getByName("127.0.0.1"))) {
            assertTrue(released.isBound(), "异常路径完成清理后未释放节点互斥");
        }
    }

    @Test
    void test_missingFoundationFailsBeforeDatabaseGate() throws Exception {
        Path root = Files.createTempDirectory("vault-preflight-test-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        try {
            Path marker = root.resolve("database-gate-called");
            Path fakeGo = root.resolve("go");
            Files.writeString(fakeGo, "#!/bin/sh\nif [ \"$1\" = version ]; then echo 'go version go1.27.1 test'; exit 0; fi\ntouch \"$VAULT_PREFLIGHT_MARKER\"\nexit 97\n");
            Files.setPosixFilePermissions(fakeGo, PosixFilePermissions.fromString("rwx------"));
            var builder = new ProcessBuilder("../scripts/verify.sh").redirectErrorStream(true).redirectOutput(root.resolve("output.log").toFile());
            var environment = builder.environment();
            environment.put("GO_BIN", fakeGo.toString());
            environment.put("VAULT_PREFLIGHT_MARKER", marker.toString());
            environment.put("VAULT_MAVEN_REPOSITORY", Files.createDirectory(root.resolve("empty-repository")).toString());
            environment.put("VAULT_TEST_JDBC_URL", "jdbc:mysql://127.0.0.1:13306/mor_vault");
            environment.put("VAULT_TEST_JDBC_USER", "vault_app");
            environment.put("VAULT_TEST_JDBC_PASSWORD", "test-only-preflight-password");
            environment.put("VAULT_TEST_DB_DSN", "vault_app:test-only-preflight-password@tcp(127.0.0.1:13306)/mor_vault?parseTime=true&loc=UTC");
            var process = builder.start();
            try {
                assertTrue(process.waitFor(30, TimeUnit.SECONDS), "前置门禁未在时限内返回");
                assertEquals(1, process.exitValue(), "缺失 Foundation 必须失败");
                assertFalse(Files.exists(marker), "缺失 Foundation 时不应进入数据库门禁");
                assertTrue(Files.readString(root.resolve("output.log")).contains("PREPARE: prepare Foundation artifacts"), "应提供明确的 Foundation 准备说明");
            } finally {
                process.destroyForcibly();
                assertTrue(process.waitFor(5, TimeUnit.SECONDS), "前置门禁进程未停止");
            }
        } finally {
            try (var paths = Files.walk(root)) { for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
        }
    }

    @Test
    void test_referenceReplayConflictAndRevealAudit() throws Exception {
        try (var database = new VaultDatabaseFixture(); var server = GoVaultProcess.start(database)) {
            server.withClient("a", client -> {
                var command = database.command("reference", "REFERENCE_VALUE", TokenMode.REFERENCE, database.canary());
                var first = database.call(() -> client.protect(command));
                database.remember(first);
                VaultResponseChecker.sameToken(first, database.call(() -> client.protect(command)));
                VaultResponseChecker.failure("IDEMPOTENCY_KEY_CONFLICT", () -> database.call(() -> client.protect(
                        database.command("reference", "REFERENCE_VALUE", TokenMode.REFERENCE, database.canary() + "changed"))));
                VaultResponseChecker.value(database.canary(), database.call(() -> client.reveal(new RevealQuery(first.token(), "TEST"))));
                database.assertCounts(1, 1, 4);
                database.assertAudit("PROTECT", "SUCCESS", "SUCCESS", 2);
                database.assertAudit("PROTECT", "DENIED", "IDEMPOTENCY_KEY_CONFLICT", 1);
                database.assertAudit("REVEAL", "SUCCESS", "SUCCESS", 1);
            });
        }
    }

    @Test
    void test_stableAcrossCallersAndDataTypes() throws Exception {
        try (var database = new VaultDatabaseFixture(); var server = GoVaultProcess.start(database)) {
            var results = new ArrayList<ProtectResult>();
            for (String caller : new String[]{"a", "b"}) {
                server.withClient(caller, client -> {
                    var result = database.call(() -> client.protect(database.command("stable-" + caller,
                            "STABLE_VALUE", TokenMode.STABLE, database.canary())));
                    database.remember(result);
                    results.add(result);
                });
            }
            VaultResponseChecker.sameToken(results.getFirst(), results.getLast());
            server.withClient("a", client -> {
                var other = database.call(() -> client.protect(database.command("other-type", "OTHER_VALUE", TokenMode.STABLE, database.canary())));
                database.remember(other);
                assertFalse(results.getFirst().token().equals(other.token()), "不同 Data Type 必须隔离 Token");
                database.assertCounts(2, 3, 3);
            });
        }
    }

    @Test
    void test_unknownTokenAndDeniedPurpose() throws Exception {
        try (var database = new VaultDatabaseFixture(); var server = GoVaultProcess.start(database)) {
            server.withClient("a", client -> {
                VaultResponseChecker.failure("TOKEN_NOT_FOUND", () -> database.call(() -> client.reveal(new RevealQuery("A".repeat(43), "TEST"))));
                VaultResponseChecker.failure("ACCESS_DENIED", () -> database.call(() -> client.protect(
                        new ProtectCommand("REFERENCE_VALUE", TokenMode.REFERENCE, database.canary(), "DENIED", database.key("deny")))));
                database.assertCounts(0, 0, 2);
                database.assertAudit("REVEAL", "DENIED", "TOKEN_NOT_FOUND", 1);
                database.assertAudit("PROTECT", "DENIED", "ACCESS_DENIED", 1);
            });
        }
    }

    @Test
    void test_valueAndRawBodyBoundary() throws Exception {
        try (var database = new VaultDatabaseFixture(); var server = GoVaultProcess.start(database)) {
            server.withClient("a", client -> {
                String maximum = "é".repeat(16384);
                var result = database.call(() -> client.protect(database.command("maximum", "REFERENCE_VALUE", TokenMode.REFERENCE, maximum)));
                database.remember(result);
                VaultResponseChecker.value(maximum, database.call(() -> client.reveal(new RevealQuery(result.token(), "TEST"))));
            });
            server.assertRawBodyBoundary(database);
            database.assertCounts(2, 2, 4);
            database.assertAudit("PROTECT", "DENIED", "INVALID_REQUEST", 1);
        }
    }

    @Test
    void test_concurrentReferenceAndStableWinner() throws Exception {
        try (var database = new VaultDatabaseFixture(); var server = GoVaultProcess.start(database)) {
            server.withClient("a", client -> {
                for (TokenMode mode : TokenMode.values()) {
                    var futures = new ArrayList<Future<ProtectResult>>();
                    var gate = new CountDownLatch(1);
                    var executor = Executors.newFixedThreadPool(6);
                    try {
                        for (int index = 0; index < 6; index++) {
                            String key = mode == TokenMode.REFERENCE ? "race-reference" : "race-stable-" + index;
                            String type = mode == TokenMode.REFERENCE ? "REFERENCE_VALUE" : "STABLE_VALUE";
                            futures.add(executor.submit(() -> {
                                assertTrue(gate.await(5, TimeUnit.SECONDS), "并发请求未释放");
                                return database.call(() -> client.protect(database.command(key, type, mode, database.canary())));
                            }));
                        }
                        gate.countDown();
                        ProtectResult winner = null;
                        for (var future : futures) {
                            var result = future.get(15, TimeUnit.SECONDS);
                            database.remember(result);
                            if (winner != null) VaultResponseChecker.sameToken(winner, result);
                            winner = result;
                        }
                    } finally {
                        gate.countDown();
                        executor.shutdownNow();
                        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "并发请求未停止");
                    }
                }
                database.assertCounts(2, 7, 12);
            });
        }
    }
}
