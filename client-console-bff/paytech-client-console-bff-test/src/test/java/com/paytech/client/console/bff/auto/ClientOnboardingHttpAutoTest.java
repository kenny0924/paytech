package com.paytech.client.console.bff.auto;

import static org.junit.jupiter.api.Assertions.*;

import com.paytech.client.console.bff.ClientConsoleBffApplication;
import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.*;
import org.springframework.test.context.*;

/** 真 BFF HTTP 链路消费三方协议模拟器；不冒充 Owner 持久化或生产联调。 */
@ActiveProfiles({ "auto", "onboarding-auto" })
@SpringBootTest(classes = ClientConsoleBffApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClientOnboardingHttpAutoTest {

    private static final String KEY = "78d8c036-c414-4311-8715-d7c601a0ce48";
    private static final String ORIGIN = "https://client-console.local.test";
    private static final List<Call> CALLS = new CopyOnWriteArrayList<>();
    private static final AtomicInteger CONTEXT = new AtomicInteger();
    private static final HttpServer OWNER = startOwner();
    private static volatile String mode = "ok";
    private static volatile boolean associated = true;

    private record Call(
        String method,
        String path,
        String query,
        String body,
        String key,
        String caller,
        String account,
        String session,
        String requestId
    ) {}

    @LocalServerPort
    int port;

    @LocalManagementPort
    int managementPort;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    /** 三 Owner 只替换传输地址，不替换 Controller、Processor 或适配逻辑。 */
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + OWNER.getAddress().getPort();
        registry.add("client.platform-test-base-url", () -> base + "/platform-support");
        registry.add("client.cct-test-base-url", () -> base);
        registry.add("client.risk-test-base-url", () -> base);
    }

    @BeforeEach
    void prepare() {
        CALLS.clear();
        CONTEXT.set(0);
        mode = "ok";
        associated = true;
    }

    @AfterAll
    static void stopOwner() {
        OWNER.stop(0);
    }

    @Test
    void missingIdentityIsRejectedBeforeOwners() throws Exception {
        var result = http.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/client-onboarding/v1/start")).build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(401, result.statusCode());
        assertTrue(CALLS.isEmpty());
    }

    @Test
    void startQueriesTwiceValidatedSessionWithoutCreating() throws Exception {
        var result = send("GET", "/client-onboarding/v1/start", null);
        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"activeApplicationId\":\"10\""));
        assertEquals(2, CONTEXT.get());
        assertTrue(CALLS.stream().allMatch(call -> call.method().equals("GET")));
        assertEquals("no-store", result.headers().firstValue("Cache-Control").orElseThrow());
    }

    @Test
    void aggregatesMainAndRelatedRequirementsAndDeduplicatesFileRead() throws Exception {
        var result = send("GET", "/client-onboarding/v1/applications/10", null);
        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"subjectPartyId\":\"30\""));
        assertTrue(result.body().contains("fixture.pdf"));
        assertFalse(result.body().contains("programVersionId"));
        assertFalse(result.body().contains("https://storage"));
        var rules = CALLS.stream()
            .filter(call -> call.path().contains("material-requirements"))
            .toList();
        assertEquals(2, rules.size());
        assertTrue(rules.stream().allMatch(call -> "client-console-bff".equals(call.caller())));
        assertTrue(
            rules
                .stream()
                .anyMatch(call -> call.query().contains("ORGANIZATION_KYB") && call.query().contains("countryCode=ZZ"))
        );
        assertTrue(
            rules
                .stream()
                .anyMatch(
                    call -> call.query().contains("RELATED_PERSON_KYC") && call.query().contains("countryCode=AA")
                )
        );
        assertEquals(
            1,
            CALLS.stream()
                .filter(call -> call.path().endsWith("/files/40"))
                .count()
        );
        for (var call : CALLS) {
            assertNotNull(call.requestId(), call.path());
        }
    }

    @Test
    void unavailableDependenciesKeepOriginalReferencesAndFailClosed() throws Exception {
        mode = "unavailable";
        var result = send("GET", "/client-onboarding/v1/applications/10", null);
        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"documentId\":\"50\""));
        assertTrue(result.body().contains("\"requirementsStatus\":\"UNAVAILABLE\""));
        assertTrue(result.body().contains("\"fileStatus\":\"UNAVAILABLE\""));
        assertFalse(result.body().contains("DOWNLOAD"));
    }

    @Test
    void missingCountryDoesNotInventDefaultRules() throws Exception {
        mode = "no-country";
        var result = send("GET", "/client-onboarding/v1/applications/10", null);
        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("COUNTRY_REQUIRED"));
        assertFalse(CALLS.stream().anyMatch(call -> call.path().contains("material-requirements")));
    }

    @Test
    void unknownFileStateCannotEnableDownload() throws Exception {
        mode = "unknown-file";
        var result = send("GET", "/client-onboarding/v1/applications/10", null);
        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"fileStatus\":\"UNAVAILABLE\""));
        assertFalse(result.body().contains("DOWNLOAD"));
    }

    @Test
    void sessionRevokedDuringFanoutDiscardsPage() throws Exception {
        mode = "revoke";
        var result = send("GET", "/client-onboarding/v1/applications/10", null);
        assertEquals(401, result.statusCode(), result.body());
        assertFalse(result.body().contains("fixture.pdf"));
    }

    @Test
    void filesSessionFailureIsNotDowngradedToDisplayOutage() throws Exception {
        mode = "file-session";
        var result = send("GET", "/client-onboarding/v1/applications/10", null);
        assertEquals(401, result.statusCode(), result.body());
    }

    @Test
    void commandsPreserveOriginalIdentityAndNeverCallOtherOwners() throws Exception {
        associated = false;
        String body =
            "{\"subjectPartyId\":\"30\",\"requirementCode\":\"REGISTRATION\",\"documentType\":\"REGISTRATION_CERTIFICATE\",\"fileId\":\"40\"}";
        for (int i = 0; i < 2; i++) {
            var result = send("POST", "/client-onboarding/v1/applications/10/documents", body);
            assertEquals(i == 0 ? 201 : 200, result.statusCode(), result.body());
        }
        assertEquals(2, CALLS.size());
        for (var call : CALLS) {
            assertEquals(KEY, call.key());
            assertEquals("1", call.account());
            assertEquals("2", call.session());
            assertEquals(body, call.body());
            assertEquals("client-console-bff", call.caller());
        }
        assertEquals(204, send("DELETE", "/client-onboarding/v1/applications/10/documents/50", null).statusCode());
        assertFalse(associated);
    }

    @Test
    void emptyFileCommandsStayEmptyAndDownloadNeedsNoIdempotencyKey() throws Exception {
        assertEquals(200, send("POST", "/onboarding-files/v1/uploads/60/completion", null).statusCode());
        assertEquals(200, send("POST", "/onboarding-files/v1/files/40/download", null).statusCode());
        assertTrue(CALLS.stream().allMatch(call -> call.body().isEmpty()));
        assertNull(CALLS.getLast().key());
    }

    @Test
    void cctSaveAndPeopleCommandsForwardOnlyApprovedBody() throws Exception {
        assertEquals(
            200,
            send(
                "PATCH",
                "/client-onboarding/v1/applications/10/draft",
                "{\"dirtyFields\":[\"organization.legalName\"],\"values\":{\"organization\":{\"legalName\":\"Fixture\"}}}"
            ).statusCode()
        );
        assertEquals(
            201,
            send(
                "POST",
                "/client-onboarding/v1/applications/10/people",
                "{\"person\":{\"dirtyFields\":[\"individual.givenName\"],\"values\":{\"individual\":{\"givenName\":\"Fixture\"}}},\"relation\":{\"relationType\":\"DIRECTOR\",\"ownershipPercentage\":null,\"effectiveFrom\":\"2026-01-01\",\"effectiveTo\":null}}"
            ).statusCode()
        );
        assertEquals(2, CALLS.size());
        assertTrue(CALLS.stream().allMatch(call -> call.path().startsWith("/client-onboarding/")));
    }

    @Test
    void malformedQueryIdentifiersAndEmptyCommandsNeverReachOwner() throws Exception {
        for (String path : List.of(
            "/client-onboarding/v1/start?create=true",
            "/client-onboarding/v1/applications/01",
            "/client-onboarding/v1/applications/9223372036854775808",
            "/client-onboarding/v1/form-options?clientType=INDIVIDUAL&clientType=ORGANIZATION"
        )) {
            assertEquals(400, send("GET", path, null).statusCode(), path);
        }
        assertEquals(400, send("POST", "/onboarding-files/v1/uploads/60/completion", "{}").statusCode());
        assertTrue(CALLS.isEmpty());
    }

    @Test
    void optionsOnlyAllowsCanonicalCountryAndFixedClientTypes() throws Exception {
        assertEquals(
            200,
            send("GET", "/client-onboarding/v1/form-options?clientType=INDIVIDUAL&countryCode=ZZ", null).statusCode()
        );
        var result = send("GET", "/client-onboarding/v1/form-options?clientType=INDIVIDUAL", null);
        assertTrue(result.body().contains("COUNTRY_REQUIRED"));
        assertEquals(
            400,
            send("GET", "/client-onboarding/v1/form-options?clientType=PARTNER&countryCode=ZZ", null).statusCode()
        );
    }

    /** CCT 目录与 Risk 要求分别聚合，单边失败不能吞掉另一边事实。 */
    @Test void optionsExposeControlledCatalogAndPreserveIndependentFailures() throws Exception {
        var available=send("GET","/client-onboarding/v1/form-options?clientType=INDIVIDUAL&countryCode=AE",null);
        assertEquals(200,available.statusCode());
        assertTrue(available.body().contains("SYNTHETIC_PERSON_ID"),available.body());
        assertTrue(available.body().contains("\"catalogStatus\":\"AVAILABLE\""));
        var catalogCall=CALLS.stream().filter(call -> call.path().endsWith("/form-options")).findFirst().orElseThrow();
        assertEquals("client-console-bff",catalogCall.caller());
        mode="catalog-unavailable";
        var missing=send("GET","/client-onboarding/v1/form-options?clientType=INDIVIDUAL&countryCode=AE",null);
        assertEquals(200,missing.statusCode());
        assertTrue(missing.body().contains("\"catalogStatus\":\"UNAVAILABLE\""));
        assertTrue(missing.body().contains("\"requirementsStatus\":\"AVAILABLE\""));
        mode="unavailable";
        var riskMissing=send("GET","/client-onboarding/v1/form-options?clientType=INDIVIDUAL&countryCode=AE",null);
        assertEquals(200,riskMissing.statusCode());
        assertTrue(riskMissing.body().contains("\"catalogStatus\":\"AVAILABLE\""));
        assertTrue(riskMissing.body().contains("\"requirementsStatus\":\"UNAVAILABLE\""));
    }
    /** 未识别的 Owner 状态不开放目录；聚合后撤销会话不能返回数据。 */
    @Test void optionsFailClosedForUnknownCatalogAndLateRevocation() throws Exception {
        mode="unknown-catalog";
        var result=send("GET","/client-onboarding/v1/form-options?clientType=INDIVIDUAL",null);
        assertEquals(200,result.statusCode());
        assertTrue(result.body().contains("\"catalogStatus\":\"UNAVAILABLE\""));
        CONTEXT.set(0);mode="revoke";
        assertEquals(401,send("GET","/client-onboarding/v1/form-options?clientType=INDIVIDUAL",null).statusCode());
    }

    @Test
    void managementListenerDoesNotExposeOnboarding() throws Exception {
        var result = http.send(
            HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + managementPort + "/client-onboarding/v1/start")
            ).build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(404, result.statusCode());
        assertTrue(CALLS.isEmpty());
    }

    /** 经 Gateway 唯一重建的测试身份；不使用真实凭据。 */
    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("X-Gateway-Realm", "CUSTOMER")
            .header("X-Gateway-Channel", "CLIENT")
            .header("X-Gateway-Actor-Type", "PLATFORM_USER")
            .header("X-Gateway-Context-Type", "ACCOUNT")
            .header("X-Gateway-Actor-Subject", "1")
            .header("X-Gateway-Session-Id", "2")
            .header("Origin", ORIGIN)
            .header("X-Request-Id", "browser-onboarding-fixture");
        if (!method.equals("GET") && !path.endsWith("/download")) {
            builder.header("Idempotency-Key", KEY);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        return http.send(
            builder
                .method(
                    method,
                    body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)
                )
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    /** 协议模拟器保留关联状态与调用记录，驱动重放及部分失败场景。 */
    private static HttpServer startOwner() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.createContext("/", ClientOnboardingHttpAutoTest::respond);
            server.start();
            return server;
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** 三 Owner 只返回各自合同事实，不预组装 BFF 页面。 */
    private static void respond(HttpExchange exchange) throws java.io.IOException {
        var h = exchange.getRequestHeaders();
        String path = exchange.getRequestURI().getPath(),
            method = exchange.getRequestMethod();
        CALLS.add(
            new Call(
                method,
                path,
                exchange.getRequestURI().getQuery(),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                h.getFirst("Idempotency-Key"),
                h.getFirst("X-Caller-Service"),
                h.getFirst("X-Gateway-Actor-Subject"),
                h.getFirst("X-Gateway-Session-Id"),
                h.getFirst("X-Request-Id")
            )
        );
        int status = 200;
        String body;
        if (path.endsWith("/context")) {
            if (CONTEXT.incrementAndGet() > 1 && mode.equals("revoke")) {
                failure(exchange, 401);
                return;
            }
            body =
                "{\"accountId\":\"1\",\"sessionId\":\"2\",\"emailMasked\":\"f***@example.test\",\"emailVerified\":true,\"contextType\":\"ACCOUNT\",\"tenantId\":null,\"memberId\":null,\"allowedActions\":[\"ONBOARDING\"]}";
        } else if (path.endsWith("/start")) {
            body =
                "{\"applications\":[{\"applicationId\":\"10\",\"applicationNo\":\"FIXTURE\",\"clientType\":\"ORGANIZATION\",\"status\":\"DRAFT\",\"active\":true,\"savedAt\":\"2026-09-16T00:00:00.000000Z\"}],\"hasMore\":false}";
        } else if (path.endsWith("/form-options")) {
            if(mode.equals("catalog-unavailable")) { failure(exchange,503);return; }
            var query=exchange.getRequestURI().getQuery();
            String country=query.contains("countryCode=") ? "\""+query.substring(query.indexOf("countryCode=")+12)+"\"" : "null";
            body="{\"clientType\":\"INDIVIDUAL\",\"countryCode\":"+country+",\"catalogStatus\":\""+(mode.equals("unknown-catalog")?"FUTURE":"AVAILABLE")+"\",\"organizationTypes\":[],\"businessModels\":[{\"code\":\"SYNTHETIC_SOLE\",\"label\":\"合成模式\"}],\"identifierTypes\":[{\"code\":\"SYNTHETIC_PERSON_ID\",\"label\":\"合成证件\",\"issuers\":[{\"scopeKey\":\"SYNTHETIC_GB_AUTHORITY\",\"label\":\"合成机构\",\"issuingCountry\":\"GB\"}]}]}";
        } else if (path.endsWith("/material-requirements")) {
            if (mode.equals("unavailable")) {
                failure(exchange, 503);
                return;
            }
            body =
                "{\"programId\":\"70\",\"programVersionId\":\"71\",\"items\":[{\"requirementCode\":\"REGISTRATION\",\"documentType\":\"REGISTRATION_CERTIFICATE\",\"requiredFlag\":true}]}";
        } else if (path.endsWith("/applications/10") && method.equals("GET")) {
            String organization = mode.equals("no-country") ? "{}" : "{\"incorporationCountry\":\"ZZ\"}";
            String individual = mode.equals("no-country") ? "{}" : "{\"residenceCountry\":\"AA\"}";
            body =
                "{\"applicationId\":\"10\",\"applicationNo\":\"FIXTURE\",\"clientId\":\"20\",\"clientType\":\"ORGANIZATION\",\"subjectPartyId\":\"30\",\"status\":\"DRAFT\",\"savedAt\":\"2026-09-16T00:00:00.000000Z\",\"editable\":true,\"values\":{\"organization\":" +
                organization +
                "},\"people\":[{\"partyId\":\"31\",\"editable\":true,\"values\":{\"individual\":" +
                individual +
                "}}],\"relations\":[{\"relationId\":\"32\",\"relatedPartyId\":\"31\",\"relationType\":\"DIRECTOR\",\"ownershipPercentage\":null,\"effectiveFrom\":\"2026-01-01\",\"effectiveTo\":null,\"status\":\"DRAFT\",\"editable\":true}],\"documents\":[{\"documentId\":\"50\",\"subjectPartyId\":\"30\",\"requirementCode\":\"REGISTRATION\",\"documentType\":\"REGISTRATION_CERTIFICATE\",\"fileId\":\"40\",\"subjectInScope\":true},{\"documentId\":\"51\",\"subjectPartyId\":\"30\",\"requirementCode\":\"ADDRESS\",\"documentType\":\"ADDRESS_PROOF\",\"fileId\":\"40\",\"subjectInScope\":true}]}";
        } else if (path.endsWith("/files/40")) {
            if (mode.equals("unavailable") || mode.equals("file-session")) {
                failure(exchange, mode.equals("file-session") ? 401 : 503);
                return;
            }
            body =
                "{\"fileId\":\"40\",\"displayName\":\"fixture.pdf\",\"sizeBytes\":\"4\",\"mimeType\":\"application/pdf\",\"scanStatus\":\"" +
                (mode.equals("unknown-file") ? "UNKNOWN" : "AVAILABLE") +
                "\",\"objectStatus\":\"ACTIVE\",\"allowedActions\":[\"DOWNLOAD\"]}";
        } else if (path.endsWith("/download")) {
            body = "{\"url\":\"https://storage.example.test/object\",\"expiresAt\":\"2099-01-01T00:00:00Z\"}";
        } else if (path.endsWith("/completion")) {
            body =
                "{\"commandId\":\"60\",\"uploadId\":\"60\",\"fileId\":\"40\",\"completedAt\":\"2026-09-16T00:00:00Z\"}";
        } else if (path.endsWith("/documents") && method.equals("POST")) {
            status = associated ? 200 : 201;
            associated = true;
            body =
                "{\"commandId\":\"80\",\"applicationId\":\"10\",\"documentId\":\"50\",\"subjectPartyId\":\"30\",\"fileId\":\"40\",\"operation\":\"ASSOCIATED\",\"completedAt\":\"2026-09-16T00:00:00.000000Z\"}";
        } else if (method.equals("DELETE")) {
            associated = false;
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        } else if (path.endsWith("/people")) {
            status = 201;
            body =
                "{\"commandId\":\"80\",\"applicationId\":\"10\",\"partyId\":\"31\",\"relationId\":\"32\",\"operation\":\"PERSON_CREATED\",\"completedAt\":\"2026-09-16T00:00:00.000000Z\"}";
        } else if (path.endsWith("/draft")) {
            body =
                "{\"commandId\":\"80\",\"applicationId\":\"10\",\"applicationNo\":\"FIXTURE\",\"clientId\":\"20\",\"clientType\":\"ORGANIZATION\",\"operation\":\"SAVED\",\"completedAt\":\"2026-09-16T00:00:00.000000Z\"}";
        } else {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        write(exchange, status, body, "application/json");
    }

    /** 严格 Problem 协议，测试业务失败与依赖失败的实际映射。 */
    private static void failure(HttpExchange exchange, int status) throws java.io.IOException {
        exchange.getResponseHeaders().set("X-Request-Id", "fixture-owner");
        write(
            exchange,
            status,
            "{\"type\":\"about:blank\",\"title\":\"" +
                (status == 401 ? "Unauthorized" : "Service Unavailable") +
                "\",\"detail\":\"Fixture unavailable\",\"code\":\"" +
                (status == 401 ? "CLIENT_SESSION_INVALID" : "SERVICE_UNAVAILABLE") +
                "\",\"requestId\":\"fixture-owner\",\"timestamp\":\"2026-09-16T00:00:00.000000Z\"}",
            "application/problem+json"
        );
    }

    /** 每次真实 HTTP 响应均按 JSON 媒体类型和实际长度发送。 */
    private static void write(HttpExchange exchange, int status, String body, String type) throws java.io.IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
