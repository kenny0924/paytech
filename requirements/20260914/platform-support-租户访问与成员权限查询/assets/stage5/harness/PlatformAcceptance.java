import static com.paytech.platform.support.test.operations.OperationsFixtureBuilder.*;
import com.paytech.platform.support.PlatformSupportApplication;
import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsPasswordPolicy;
import com.paytech.platform.support.accesscontrol.integration.vault.LoginEmailVault;
import com.paytech.platform.support.test.operations.*;
import com.paytech.platform.support.test.apicredential.ApiCredentialFixture;
import com.paytech.platform.support.test.tenantaccess.*;
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

/** 手工本机整链路验收：真实 Platform，只准备并清理本次拥有的合成业务夹具。 */
public class PlatformAcceptance {
    public static void main(String[] args) throws Exception {
        Path runtime = Path.of(args[0]);
        var keys = generateKeyMaterial();
        System.setProperty("OPERATIONS_LOCAL_SIGNING_PRIVATE_KEY_BASE64", keys.encodedPrivateKey());
        System.setProperty("OPERATIONS_LOCAL_HMAC_KEY_BASE64", keys.encodedHmac());
        System.setProperty("OPERATIONS_LOCAL_HMAC_KEY_VERSION", "1");
        String marker = "tenant-stage5-" + UUID.randomUUID();
        var context = new SpringApplicationBuilder(PlatformSupportApplication.class).registerShutdownHook(false).run(
            "--spring.profiles.active=auto", "--server.address=127.0.0.1", "--server.port=0",
            "--operations.authentication.enabled=true", "--operations.authentication.worker-id=27",
            "--redis.key-prefix=" + marker, "--spring.cloud.service-registry.auto-registration.enabled=false");
        var jdbc = context.getBean(JdbcTemplate.class);
        try (var connection = jdbc.getDataSource().getConnection()) {
            if (!connection.getMetaData().getURL().startsWith("jdbc:mysql://127.0.0.1:13306/mor_platform_config_validation?")) {
                context.close(); throw new IllegalStateException("仅允许本期独立本机验收库");
            }
        }
        var json = context.getBean(ObjectMapper.class);
        var fixtures = new OperationsFixtureMocker(new OperationsTestMapper(jdbc),
            context.getBean(OperationsPasswordPolicy.class), context.getBean(LoginEmailVault.class), marker);
        var probe = new OperationsAuditProbe(context.getBean(AuditContext.class), context.getBean(AuditDispatcher.class), context.getBean(MeterRegistry.class));
        var credentials = new ArrayList<ApiCredentialFixture>();
        var tenants = new ArrayList<TenantAccessFixture>();
        var mapper = new TenantAccessTestMapper(jdbc);
        var control = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        Files.writeString(runtime.resolveSibling("http-journal.jsonl"), "");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                control.stop(0);
                probe.awaitIdle(Duration.ofSeconds(10));
                cleanupHttpArtifacts(jdbc, json, runtime.resolveSibling("http-journal.jsonl"), marker);
                tenants.forEach(TenantAccessFixture::close);
                credentials.forEach(ApiCredentialFixture::close);
                fixtures.close();
            } finally { context.close(); }
        }));
        // Oracle 在人工临时启用之前核对完整禁用目录；本启动器绝不应用登记/启用 SQL。
        fixtures.open(keys, json);
        fixtures.additionalAuditChain(LocalDate.now(ZoneOffset.UTC));
        var admin = fixtures.mock(administrator());
        var reader = fixtures.mock(administrator().viewer());
        var memberReader = fixtures.mock(administrator().viewer());
        var denied = fixtures.mock(administrator().viewer());
        long read = mapper.permission("TENANT_ACCESS_READ"), manage = mapper.permission("TENANT_ACCESS_MANAGE"), members = mapper.permission("TENANT_MEMBER_READ");
        fixtures.grant(admin, fixtures.role(TENANT_ID, false, read, manage, members));
        fixtures.grant(reader, fixtures.role(TENANT_ID, false, read));
        fixtures.grant(memberReader, fixtures.role(TENANT_ID, false, read, members));
        for (int i = 0; i < 7; i++) {
            var credential = new ApiCredentialFixture(jdbc); credentials.add(credential); credential.open();
            credential.credentialExpiresAt(Instant.now().plusSeconds(7200));
            jdbc.update("UPDATE t_access_tenant SET tenant_name=? WHERE tenant_id=?",
                i == 0 ? "验收客户_100% · 跨境业务与长名称展示" : "验收客户 " + (i + 1), credential.tenantId());
            if (i == 6) continue;
            var tenant = new TenantAccessFixture(mapper, credential.tenantId()); tenants.add(tenant); tenant.open();
            jdbc.update("UPDATE t_access_tenant SET tenant_name=? WHERE tenant_id=?", "验收合作伙伴 " + (i + 1), tenant.getPartner());
        }
        var first = tenants.getFirst();
        long role = first.role(null), revokedRole = first.role(first.getClient());
        first.activeGrant(role, true);
        first.grant(role, true, "ACTIVE", first.getNow().plusSeconds(3600), null);
        first.grant(role, true, "EXPIRED", first.getNow().minusSeconds(7200), first.getNow().minusSeconds(3600));
        first.grant(revokedRole, false, "REVOKED", first.getNow().minusSeconds(60), null);
        long permission = 0;
        for (int i = 0; i < 13; i++) {
            long id = first.permission(); first.bind(role, id);
            if (i == 0) { permission = id; first.bind(revokedRole, id); }
            if (i == 12) mapper.permissionStatus(id, "INACTIVE");
        }
        credentials.get(1).ownerStatus("SUSPENDED"); credentials.get(1).securityStatus("SUSPENDED");
        mapper.accountStatus(tenants.get(2).getAccount(), "DISABLED");
        mapper.memberStatus(tenants.get(2).getMember(), "SUSPENDED");
        control.createContext("/fixture-state", exchange -> {
            if (!exchange.getRequestMethod().equals("POST")) { exchange.sendResponseHeaders(405,-1); exchange.close(); return; }
            String mode = exchange.getRequestURI().getQuery();
            if ("foreign-role".equals(mode)) mapper.roleTenant(role, first.getPartner());
            else if ("restore-role".equals(mode)) mapper.roleTenant(role, null);
            else { exchange.sendResponseHeaders(400,-1); exchange.close(); return; }
            exchange.sendResponseHeaders(204,-1); exchange.close();
        });
        control.start();
        var data = new LinkedHashMap<String,Object>();
        data.put("port", ((WebServerApplicationContext)context).getWebServer().getPort());
        data.put("controlPort", control.getAddress().getPort()); data.put("marker", marker);
        data.put("admin", admin.email()); data.put("reader", reader.email()); data.put("memberReader", memberReader.email()); data.put("denied", denied.email());
        data.put("password", INITIAL_PASSWORD);
        data.put("tenantId", Long.toString(first.getClient())); data.put("partnerId", Long.toString(first.getPartner()));
        data.put("memberId", Long.toString(first.getMember())); data.put("otherMemberId", Long.toString(first.getOtherMember()));
        data.put("permissionId", Long.toString(permission)); data.put("blockedTenantId", Long.toString(tenants.get(1).getClient()));
        data.put("emptyTenantId", Long.toString(credentials.get(6).tenantId())); data.put("credential", credentials.getFirst().credential());
        Files.writeString(runtime, json.writeValueAsString(data));
        Files.setPosixFilePermissions(runtime, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        Thread.currentThread().join();
    }

    /** 按入口代理记录的本次 Request ID/命令键清理自身业务操作产生的审计和回执。 */
    private static void cleanupHttpArtifacts(JdbcTemplate jdbc, ObjectMapper json, Path journal, String marker) {
        try {
            for (String line : Files.readAllLines(journal)) {
                var row = json.readTree(line);
                if (!row.path("ingressRequestId").asText().startsWith(marker + "-")) throw new IllegalStateException("HTTP 记录不属于本次验收");
                String requestId = row.path("requestId").asText();
                jdbc.update("DELETE s FROM t_security_event s JOIN t_audit_event e ON s.source_system='PLATFORM' AND s.source_event_key=CAST(e.audit_event_id AS CHAR) WHERE e.request_id=?", requestId);
                jdbc.update("DELETE a FROM t_audit_event_attr a JOIN t_audit_event e ON e.audit_event_id=a.audit_event_id WHERE e.request_id=?", requestId);
                jdbc.update("DELETE FROM t_audit_event WHERE request_id=?", requestId);
                if (!row.path("commandKey").isNull()) jdbc.update("DELETE FROM t_command_idempotency WHERE idempotency_key=?", row.path("commandKey").asText());
            }
        } catch (java.io.IOException error) { throw new IllegalStateException("无法读取本次验收归属记录", error); }
    }
}
