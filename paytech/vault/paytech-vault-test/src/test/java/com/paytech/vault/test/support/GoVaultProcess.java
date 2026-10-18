package com.paytech.vault.test.support;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.paytech.vault.client.admin.VaultAdminClient;

import com.paytech.vault.client.ProtectResult;
import com.paytech.vault.client.VaultClient;
import com.paytech.vault.client.autoconfigure.VaultClientAutoConfiguration;

import tools.jackson.databind.json.JsonMapper;

/** 拥有真实 Go 子进程与私有临时目录；节点租约由外层数据库 Fixture 持有至清理结束。 */
public final class GoVaultProcess implements AutoCloseable {
    private final VaultDatabaseFixture database;
    private final Set<String> secrets = new HashSet<>();
    private Path directory;
    private Process process;
    private Thread shutdownHook;
    private int port;
    private boolean closed;

    private GoVaultProcess(VaultDatabaseFixture database) { this.database = database; }

    public static GoVaultProcess start(VaultDatabaseFixture database) { return start(database, Map.of()); }

    public static GoVaultProcess start(VaultDatabaseFixture database, Map<String, String> nacos) {
        var server = new GoVaultProcess(database);
        try {
            server.launch(nacos);
            server.awaitReady();
            return server;
        } catch (Throwable failure) {
            server.close();
            throw new AssertionError("Go Vault 启动失败；检查二进制、环境、私有端口及手工数据库准备");
        }
    }

    private void launch(Map<String, String> nacos) throws IOException {
        assertTrue(System.getenv("OTEL_RESOURCE_ATTRIBUTES") == null || System.getenv("OTEL_RESOURCE_ATTRIBUTES").isEmpty(),
                "OTEL_RESOURCE_ATTRIBUTES 必须为空");
        database.acquireNodeLease();
        directory = Files.createTempDirectory("vault-e2e-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        port = nacos.isEmpty() ? randomPort() : Integer.parseInt(nacos.get("VAULT_NACOS_INSTANCE_PORT"));
        var binary = Path.of(VaultDatabaseFixture.required("VAULT_TEST_SERVER_BINARY"));
        assertTrue(binary.isAbsolute() && Files.isExecutable(binary), "先构建实际 Go Vault Server 并提供绝对路径");
        var builder = new ProcessBuilder(binary.toString());
        configure(builder.environment(), nacos);
        builder.redirectOutput(privateFile("stdout.log").toFile());
        builder.redirectError(privateFile("stderr.log").toFile());
        process = builder.start();
        shutdownHook = new Thread(this::close, "vault-e2e-cleanup");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private void configure(Map<String, String> environment, Map<String, String> nacos) throws IOException {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String rootKey = Base64.getEncoder().encodeToString(key);
        Arrays.fill(key, (byte) 0);
        secrets.add(rootKey);
        environment.put("VAULT_LOCAL_AES_ROOT_KEY", rootKey);
        environment.put("VAULT_LOCAL_AES_KEY_ID", "e2e-local-only");
        environment.put("VAULT_DB_DSN", VaultDatabaseFixture.required("VAULT_TEST_DB_DSN"));
        environment.put("VAULT_RUNTIME_MODE", "test");
        environment.put("VAULT_HTTP_ADDRESS", "127.0.0.1:" + port);
        environment.put("VAULT_SNOWFLAKE_DATACENTER_ID", "31");
        environment.put("VAULT_SNOWFLAKE_WORKER_ID", "28");
        environment.put("VAULT_NACOS_ENABLED", Boolean.toString(!nacos.isEmpty()));
        // 调用方 b 兼作管理调用方，其审计与幂等记录仍在夹具的清理归属内；短刷新间隔让策略变更尽快生效。
        environment.put("VAULT_ADMIN_CALLERS", database.caller("b"));
        environment.put("VAULT_POLICY_REFRESH_INTERVAL", "200ms");
        environment.put("TMPDIR", directory.toString());
        environment.putAll(nacos);
        if (nacos.containsKey("VAULT_NACOS_PASSWORD")) secrets.add(nacos.get("VAULT_NACOS_PASSWORD"));
        database.seedPolicy();
    }

    private Path privateFile(String name) throws IOException {
        return Files.createFile(directory.resolve(name), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    }

    private static int randomPort() throws IOException {
        try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { return socket.getLocalPort(); }
    }

    public int port() { return port; }
    public String url() { return "http://127.0.0.1:" + port; }

    private void awaitReady() {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
            await().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(20)).until(() -> {
                assertTrue(process.isAlive(), "Go Vault 在就绪前退出");
                try {
                    var request = HttpRequest.newBuilder(URI.create(url() + "/health/readiness")).timeout(Duration.ofSeconds(1)).GET().build();
                    return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
                } catch (IOException exception) { return false; }
            });
        }
    }

    @FunctionalInterface public interface ClientScenario { void accept(VaultClient client) throws Exception; }

    public void withClient(String suffix, ClientScenario operation) {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class, VaultClientAutoConfiguration.class))
                .withPropertyValues("spring.application.name=" + database.caller(suffix), "spring.profiles.active=test",
                        "vault.client.base-url=" + url(), "vault.client.connect-timeout=1s", "vault.client.read-timeout=5s")
                .run(context -> {
                    assertNull(context.getStartupFailure(), "真实客户端自动配置失败");
                    operation.accept(context.getBean(VaultClient.class));
                });
    }

    @FunctionalInterface public interface AdminScenario { void accept(VaultAdminClient client) throws Exception; }

    /** 以管理调用方（调用方 b）开启 VaultAdminClient 并执行场景。 */
    public void withAdminClient(AdminScenario operation) {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class, VaultClientAutoConfiguration.class))
                .withPropertyValues("spring.application.name=" + database.caller("b"), "spring.profiles.active=test",
                        "vault.client.base-url=" + url(), "vault.client.connect-timeout=1s", "vault.client.read-timeout=5s",
                        "vault.client.admin-enabled=true")
                .run(context -> {
                    assertNull(context.getStartupFailure(), "真实管理客户端自动配置失败");
                    operation.accept(context.getBean(VaultAdminClient.class));
                });
    }

    public void assertRawBodyBoundary(VaultDatabaseFixture database) throws Exception {
        String json = "{\"dataType\":\"" + database.type("REFERENCE_VALUE") + "\",\"tokenMode\":\"REFERENCE\",\"value\":\"\",\"purpose\":\"TEST\"}";
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
            for (int size : new int[]{262144, 262145}) {
                var request = HttpRequest.newBuilder(URI.create(url() + "/vault/v1/records/protect"))
                        .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                        .header("X-Caller-Service", database.caller("a")).header("X-Request-Id", database.requestId())
                        .header("Idempotency-Key", database.key("raw-" + size))
                        .POST(HttpRequest.BodyPublishers.ofString(json + " ".repeat(size - json.length()))).build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertEquals(size == 262144 ? 200 : 400, response.statusCode(), "原始 Body 上限不匹配");
                if (size == 262144) rememberRawToken(response.body());
            }
        }
    }

    private void rememberRawToken(String body) {
        try {
            database.remember(new ProtectResult(JsonMapper.builder().build().readTree(body).get("token").asString()));
        } catch (RuntimeException exception) {
            throw new AssertionError("Go 原始 Body 边界测试返回非法成功响应；不输出 Payload 或解析 Cause");
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        boolean dead = stopProcess();
        if (!dead) {
            database.preserveRows();
            throw new AssertionError("Go Vault 未确认停止；保留本次数据、私有目录和节点互斥锁");
        }
        closed = true;
        try {
            if (shutdownHook != null && Thread.currentThread() != shutdownHook) Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } finally { scanAndDeleteDirectory(); }
    }

    private boolean stopProcess() {
        if (process == null) return true;
        boolean interrupted = false;
        try {
            process.destroy();
            try { if (process.waitFor(5, TimeUnit.SECONDS)) return true; }
            catch (InterruptedException exception) { interrupted = true; }
            process.destroyForcibly();
            try { return process.waitFor(5, TimeUnit.SECONDS); }
            catch (InterruptedException exception) { interrupted = true; return !process.isAlive(); }
        } finally {
            try { process.getInputStream().close(); process.getErrorStream().close(); process.getOutputStream().close(); }
            catch (IOException ignored) { /* 已停止进程的管道不包含诊断输出。 */ }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private void scanAndDeleteDirectory() {
        if (directory == null) return;
        boolean leaked = false;
        secrets.addAll(database.sensitiveValues());
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    String content = new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
                    leaked |= secrets.stream().filter(value -> !value.isEmpty()).anyMatch(content::contains);
                }
                Files.delete(path);
            }
        } catch (IOException exception) { throw new AssertionError("Vault 私有临时文件检查或清理失败"); }
        assertFalse(leaked, "Vault 私有进程输出发现敏感 Canary（不显示匹配内容）");
    }
}
