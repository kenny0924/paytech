import static com.paytech.platform.support.test.operations.OperationsFixtureBuilder.*;
import com.paytech.platform.support.PlatformSupportApplication;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsPasswordPolicy;
import com.paytech.platform.support.accesscontrol.integration.vault.LoginEmailVault;
import com.paytech.platform.support.test.operations.*;
import com.paytech.platform.support.core.audit.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** 手工本地验收启动器：真实 Platform，复用测试夹具，不发布为应用代码。 */
public class PlatformAcceptance {
    public static void main(String[] args) throws Exception {
        Path runtime = Path.of(args[0]);
        var keys = generateKeyMaterial();
        System.setProperty("OPERATIONS_LOCAL_SIGNING_PRIVATE_KEY_BASE64", keys.encodedPrivateKey());
        System.setProperty("OPERATIONS_LOCAL_HMAC_KEY_BASE64", keys.encodedHmac());
        System.setProperty("OPERATIONS_LOCAL_HMAC_KEY_VERSION", "1");
        String marker = "plt-config-stage5-" + UUID.randomUUID();
        var context = new SpringApplicationBuilder(PlatformSupportApplication.class).registerShutdownHook(false).run(
            "--spring.profiles.active=auto", "--server.address=127.0.0.1", "--server.port=0",
            "--operations.authentication.enabled=true", "--operations.authentication.worker-id=27",
            "--redis.key-prefix=" + marker, "--spring.cloud.service-registry.auto-registration.enabled=false");
        var jdbc = context.getBean(JdbcTemplate.class);
        String url;
        try (var connection = jdbc.getDataSource().getConnection()) { url = connection.getMetaData().getURL(); }
        if (!url.startsWith("jdbc:mysql://127.0.0.1:13306/mor_platform_config_validation?")) {
            context.close(); throw new IllegalStateException("只允许本期独立本机验收库");
        }
        var json = context.getBean(ObjectMapper.class);
        var fixtures = new OperationsFixtureMocker(new OperationsTestMapper(jdbc),
            context.getBean(OperationsPasswordPolicy.class), context.getBean(LoginEmailVault.class), marker);
        var probe = new OperationsAuditProbe(context.getBean(AuditContext.class), context.getBean(AuditDispatcher.class), context.getBean(MeterRegistry.class));
        var activated = new java.util.concurrent.atomic.AtomicBoolean();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                probe.awaitIdle(Duration.ofSeconds(10));
                cleanupHttpArtifacts(jdbc, json, runtime.resolveSibling("http-journal.jsonl"), marker);
                fixtures.close();
                if (activated.get()) {
                    jdbc.update("UPDATE t_gateway_route SET status='DISABLED' WHERE route_id BETWEEN 900000000000041001 AND 900000000000041018 AND status='ACTIVE' AND version=1");
                    jdbc.update("UPDATE t_console_resource SET status='DISABLED' WHERE resource_id BETWEEN 900000000000041301 AND 900000000000041304 AND status='ACTIVE' AND version=1");
                }
            } finally { context.close(); }
        }));
        fixtures.open(keys, json);
        Files.writeString(runtime.resolveSibling("http-journal.jsonl"), "");
        fixtures.additionalAuditChain(LocalDate.now(ZoneOffset.UTC));
        var admin = fixtures.mock(administrator());
        var reader = fixtures.mock(administrator().viewer());
        var denied = fixtures.mock(administrator().viewer());
        fixtures.grant(admin, fixtures.role(TENANT_ID, false, 900000000000041101L, 900000000000041102L,
            900000000000041103L, 900000000000041104L));
        fixtures.grant(reader, fixtures.role(TENANT_ID, false, 900000000000041101L, 900000000000041103L));
        // 本期 18 个路由和 4 个资源仅在独立验收库临时可用；不改变管理员原有 REVOKED 授权。
        activated.set(true);
        int routes = jdbc.update("UPDATE t_gateway_route SET status='ACTIVE' WHERE route_id BETWEEN 900000000000041001 AND 900000000000041018 AND status='DISABLED' AND version=1");
        int resources = jdbc.update("UPDATE t_console_resource SET status='ACTIVE' WHERE resource_id BETWEEN 900000000000041301 AND 900000000000041304 AND status='DISABLED' AND version=1");
        if (routes != 18 || resources != 4) throw new IllegalStateException("验收目录基线不匹配");
        var control = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        control.createContext("/fixture", exchange -> {
            String kind = exchange.getRequestURI().getQuery();
            String value = kind.equals("ROLE") ? fixtures.roleCode() : fixtures.configurationCode(kind);
            byte[] bytes = json.writeValueAsBytes(Map.of("code", value));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        control.start();
        Files.writeString(runtime, json.writeValueAsString(Map.of("port", ((WebServerApplicationContext)context).getWebServer().getPort(),
            "controlPort", control.getAddress().getPort(), "marker", marker,
            "admin", admin.email(), "reader", reader.email(), "denied", denied.email(), "password", INITIAL_PASSWORD)));
        Files.setPosixFilePermissions(runtime, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> control.stop(0)));
        Thread.currentThread().join();
    }

    private static void cleanupHttpArtifacts(JdbcTemplate jdbc, ObjectMapper json, Path journal, String marker) {
        try {
            for (String line : Files.readAllLines(journal)) {
                var row = json.readTree(line);
                if (!row.path("ingressRequestId").asText().startsWith(marker + "-")) {
                    throw new IllegalStateException("HTTP 记录不属于本次验收");
                }
                String requestId = row.path("requestId").asText();
                jdbc.update("DELETE s FROM t_security_event s JOIN t_audit_event e ON s.source_system='PLATFORM' AND s.source_event_key=CAST(e.audit_event_id AS CHAR) WHERE e.request_id=?", requestId);
                jdbc.update("DELETE a FROM t_audit_event_attr a JOIN t_audit_event e ON e.audit_event_id=a.audit_event_id WHERE e.request_id=?", requestId);
                jdbc.update("DELETE FROM t_audit_event WHERE request_id=?", requestId);
                if (!row.path("commandKey").isNull()) {
                    jdbc.update("DELETE FROM t_command_idempotency WHERE idempotency_key=?", row.path("commandKey").asText());
                }
            }
        } catch (java.io.IOException error) { throw new IllegalStateException("无法读取本次验收归属记录", error); }
    }
}
