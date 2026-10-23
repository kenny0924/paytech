package com.paytech.client.console.bff.auto;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.paytech.client.console.bff.ClientConsoleBffApplication;
import com.paytech.client.console.bff.domain.enums.ClientBffErrorCode;
import com.paytech.client.console.bff.ext.rest.ClientLocalizationManifestController;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

/**
 * 匿名 manifest：真 BFF HTTP 链路消费 Platform 交付接口的协议模拟器（技术方案 3.7）
 * <li>不带会话、工作区与 Origin；应用编码固定 client-console，页面传入的应用编码被忽略。</li>
 * <li>Cache-Control: no-cache；ETag 随发布号、语言与内容变化，If-None-Match 命中返回 304 无正文。</li>
 *
 * @see ClientLocalizationManifestController
 * @see com.paytech.platform.support.api.localization.LocalizationDeliveryApi
 */
@ActiveProfiles("auto")
@SpringBootTest(classes = ClientConsoleBffApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClientLocalizationManifestHttpAutoTest {
    private static final String OWNER_PATH = "/platform-support/internal/localization/v1/applications/client-console/manifest";
    private static final String MANIFEST = """
        {"application":"client-console","releaseNo":7,"publishedAt":"2026-10-23T08:00:00Z",\
        "locales":[{"code":"en","nativeName":"English","direction":"LTR"},{"code":"zh-CN","nativeName":"简体中文","direction":"LTR"}],\
        "defaultLocale":"en",\
        "bundles":{"zh-CN":{"team":{"hash":"%s","url":"https://static.local.test/i18n/client-console/7/zh-CN/team.%s.json"}}}}"""
        .formatted("e".repeat(64), "e".repeat(16));
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<Call> CALLS = new CopyOnWriteArrayList<>();
    private static final HttpServer OWNER = startOwner();
    private static volatile int ownerStatus = 200;
    private static volatile String ownerBody = MANIFEST;

    private record Call(String method, String path, String query, String caller, String account, String session) {}

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    /** Platform 只替换传输地址。 */
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("client.platform-test-base-url",
            () -> "http://127.0.0.1:" + OWNER.getAddress().getPort() + "/platform-support");
    }

    /** 每例从当前发布 7 的 manifest 开始。 */
    @BeforeEach
    void prepareManifestOwner() {
        CALLS.clear();
        ownerStatus = 200;
        ownerBody = MANIFEST;
    }

    @AfterAll
    static void stopOwner() {
        OWNER.stop(0);
    }

    /** 匿名读取：固定应用编码、只转发 locale 与 releaseNo，不带身份；响应原样返回并带 no-cache 与 ETag。 */
    @Test
    void test_normalFlow() throws Exception {
        var result = get("?locale=zh-CN&releaseNo=7&application=operations-console", null);

        assertEquals(200, result.statusCode(), result.body());
        assertEquals(JSON.readTree(MANIFEST), JSON.readTree(result.body()));
        assertEquals("no-cache", result.headers().firstValue("Cache-Control").orElseThrow());
        String etag = result.headers().firstValue("ETag").orElseThrow();
        assertTrue(etag.matches("\"r7-zh-CN-[0-9a-f]{16}\""), etag);
        assertEquals(1, CALLS.size());
        var call = CALLS.getFirst();
        assertAll(() -> assertEquals("GET", call.method()), () -> assertEquals(OWNER_PATH, call.path()),
            () -> assertEquals("locale=zh-CN&releaseNo=7", call.query()),
            () -> assertEquals("client-console-bff", call.caller()),
            () -> assertNull(call.account()), () -> assertNull(call.session()));
    }

    /** If-None-Match 与当前 ETag 一致返回 304 无正文；内容变化后 ETag 改变并返回 200。 */
    @Test
    void test_notModified() throws Exception {
        String etag = get("?locale=zh-CN", null).headers().firstValue("ETag").orElseThrow();

        var unchanged = get("?locale=zh-CN", etag);

        assertEquals(304, unchanged.statusCode(), unchanged.body());
        assertTrue(unchanged.body().isEmpty());
        ownerBody = MANIFEST.replace("\"releaseNo\":7", "\"releaseNo\":8");
        var changed = get("?locale=zh-CN", etag);
        assertEquals(200, changed.statusCode(), changed.body());
        assertNotEquals(etag, changed.headers().firstValue("ETag").orElseThrow());
    }

    /** 应用还没有 READY 发布：releaseNo 为 null、bundles 为空对象，ETag 发布号记 0、语言记 default。 */
    @Test
    void test_noDataFound() throws Exception {
        ownerBody = "{\"application\":\"client-console\",\"releaseNo\":null,\"publishedAt\":null,"
            + "\"locales\":[{\"code\":\"en\",\"nativeName\":\"English\",\"direction\":\"LTR\"}],\"defaultLocale\":\"en\",\"bundles\":{}}";

        var result = get("", null);

        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"bundles\":{}"), result.body());
        assertTrue(result.headers().firstValue("ETag").orElseThrow().startsWith("\"r0-default-"));
    }

    /** 指定发布不存在 404；Platform 不可用 503；返回其他应用 502；非法 locale 与 releaseNo 不访问 Platform。 */
    @Test
    void test_failures() throws Exception {
        ownerStatus = 404;
        ownerBody = problem(404, CommonErrorCode.NOT_FOUND.getCode());
        assertEquals(404, get("?releaseNo=9", null).statusCode());

        ownerStatus = 503;
        ownerBody = problem(503, "SERVICE_UNAVAILABLE");
        var unavailable = get("", null);
        assertEquals(503, unavailable.statusCode());
        assertTrue(unavailable.body().contains(ClientBffErrorCode.DEPENDENCY_UNAVAILABLE.getCode()), unavailable.body());

        ownerStatus = 200;
        ownerBody = MANIFEST.replace("\"application\":\"client-console\"", "\"application\":\"operations-console\"");
        assertEquals(502, get("", null).statusCode());

        CALLS.clear();
        assertEquals(400, get("?locale=zh_cn", null).statusCode());
        assertEquals(400, get("?releaseNo=0", null).statusCode());
        assertTrue(CALLS.isEmpty());
    }

    /** Gateway PUBLIC 路由的匿名请求：无 Cookie、无 Origin、无身份 Header。 */
    private HttpResponse<String> get(String query, String ifNoneMatch) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + ClientLocalizationManifestController.PATH + query));
        if (ifNoneMatch != null) {
            builder.header("If-None-Match", ifNoneMatch);
        }
        return http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String problem(int status, String code) {
        return "{\"type\":\"about:blank\",\"title\":\"" + org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase()
            + "\",\"detail\":\"owner-detail\",\"code\":\"" + code + "\",\"requestId\":\"owner-request\","
            + "\"timestamp\":\"2026-10-23T00:00:00.123456Z\"}";
    }

    private static HttpServer startOwner() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.createContext("/", ClientLocalizationManifestHttpAutoTest::handle);
            server.start();
            return server;
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("无法启动 Platform 协议模拟器", failure);
        }
    }

    private static void handle(HttpExchange exchange) throws java.io.IOException {
        var headers = exchange.getRequestHeaders();
        CALLS.add(new Call(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
            exchange.getRequestURI().getRawQuery(), headers.getFirst("X-Caller-Service"),
            headers.getFirst("X-Gateway-Actor-Subject"), headers.getFirst("X-Gateway-Session-Id")));
        byte[] body = ownerBody.getBytes(StandardCharsets.UTF_8);
        boolean problem = ownerStatus >= 400;
        exchange.getResponseHeaders().add("Content-Type", problem ? "application/problem+json" : "application/json");
        if (problem) {
            exchange.getResponseHeaders().add("X-Request-Id", "owner-request");
        }
        exchange.sendResponseHeaders(ownerStatus, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
