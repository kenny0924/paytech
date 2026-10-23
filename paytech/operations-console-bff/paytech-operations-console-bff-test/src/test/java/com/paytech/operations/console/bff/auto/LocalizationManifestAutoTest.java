package com.paytech.operations.console.bff.auto;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.ext.rest.LocalizationManifestController;
import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RecordedRequest;

import tools.jackson.databind.json.JsonMapper;

/**
 * 匿名 manifest 经真实 BFF 与 HttpExchange 读取 Platform（技术方案 3.7）
 * <li>不经 PLT-I02、不带操作人；应用编码固定 operations-console，页面传入的应用编码被忽略。</li>
 * <li>Cache-Control: no-cache；ETag 随发布号、语言与内容变化，If-None-Match 命中返回 304 无正文。</li>
 *
 * @see LocalizationManifestController#getManifest(String, Integer)
 * @author Claude
 */
class LocalizationManifestAutoTest extends OperationsBffHttpTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private static final String OWNER_PATH = "/internal/localization/v1/applications/operations-console/manifest";
    private static final String REQUEST_ID = "localization-manifest-request";
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private static final String MANIFEST = """
        {"application":"operations-console","releaseNo":7,"publishedAt":"2026-10-23T08:00:00Z",
         "locales":[{"code":"en","nativeName":"English","direction":"LTR"},{"code":"zh-CN","nativeName":"简体中文","direction":"LTR"}],
         "defaultLocale":"zh-CN",
         "bundles":{"zh-CN":{"localization":{"hash":"%s","url":"https://static.local.test/i18n/operations-console/7/zh-CN/localization.%s.json"}}}}
        """.formatted("d".repeat(64), "d".repeat(16));

    @Autowired
    private PlatformOperationsSimulator platform;

    @LocalServerPort
    private int port;

    /** 每例从空 Platform 配置开始。 */
    @BeforeEach
    void setUpManifestMocks() {
        platform.reset();
    }

    /** Platform 不得收到未配置的请求。 */
    @AfterEach
    void verifyManifestCalls() {
        assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString());
    }

    /** 匿名读取：固定应用编码、只转发 locale 与 releaseNo，不调用 PLT-I02，不带操作人；响应原样返回并带 no-cache 与 ETag。 */
    @Test
    void test_normalFlow() throws Exception {
        platform.rawResponse(OWNER_PATH, MANIFEST);

        var response = get("?locale=zh-CN&releaseNo=7&application=client-console", null);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(JSON.readTree(MANIFEST), JSON.readTree(response.body()));
        assertEquals("no-cache", response.headers().firstValue("Cache-Control").orElse(null));
        String etag = response.headers().firstValue("ETag").orElseThrow();
        assertTrue(etag.matches("\"r7-zh-CN-[0-9a-f]{16}\""), etag);
        assertEquals(List.of(OWNER_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
        var call = platform.requests().getFirst();
        assertAll(() -> assertEquals("GET", call.method()),
            () -> assertEquals(Map.of("locale", "zh-CN", "releaseNo", "7"), query(call.query())),
            () -> assertEquals(REQUEST_ID, call.header("X-Request-Id")),
            () -> assertNull(call.header("X-Gateway-Actor-Subject")), () -> assertNull(call.header("X-Gateway-Session-Id")));
    }

    /** If-None-Match 与当前 ETag 一致返回 304 无正文；内容变化（如默认语言）后 ETag 改变并返回 200。 */
    @Test
    void test_notModified() throws Exception {
        platform.rawResponse(OWNER_PATH, MANIFEST);
        String etag = get("?locale=zh-CN", null).headers().firstValue("ETag").orElseThrow();

        var unchanged = get("?locale=zh-CN", etag);

        assertEquals(304, unchanged.statusCode(), unchanged.body());
        assertTrue(unchanged.body().isEmpty());
        assertEquals(etag, unchanged.headers().firstValue("ETag").orElse(null));

        platform.rawResponse(OWNER_PATH, MANIFEST.replace("\"defaultLocale\":\"zh-CN\"", "\"defaultLocale\":\"en\""));
        var changed = get("?locale=zh-CN", etag);
        assertEquals(200, changed.statusCode(), changed.body());
        assertNotEquals(etag, changed.headers().firstValue("ETag").orElseThrow());
    }

    /** 应用还没有 READY 发布：releaseNo 为 null、bundles 为空对象，ETag 发布号记 0、语言记 default。 */
    @Test
    void test_noDataFound() throws Exception {
        platform.rawResponse(OWNER_PATH, """
            {"application":"operations-console","releaseNo":null,"publishedAt":null,
             "locales":[{"code":"en","nativeName":"English","direction":"LTR"}],"defaultLocale":"zh-CN","bundles":{}}
            """);

        var response = get("", null);

        assertEquals(200, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertTrue(body.path("releaseNo").isNull() || body.path("releaseNo").isMissingNode()),
            () -> assertEquals(0, body.path("bundles").size()),
            () -> assertTrue(response.headers().firstValue("ETag").orElseThrow().startsWith("\"r0-default-")));
    }

    /** 指定发布不存在 404 原样；Platform 不可用 503；返回其他应用的 manifest 按上游无效 502；非法 locale 与 releaseNo 本地拒绝。 */
    @Test
    void test_failures() throws Exception {
        platform.problem(OWNER_PATH, 404, CommonErrorCode.NOT_FOUND.getCode());
        assertEquals(404, get("?releaseNo=9", null).statusCode());

        platform.reset();
        platform.problem(OWNER_PATH, 503, "SERVICE_UNAVAILABLE");
        var unavailable = get("", null);
        assertEquals(503, unavailable.statusCode());
        assertEquals(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE.getCode(), JSON.readTree(unavailable.body()).path("code").asString());

        platform.reset();
        platform.rawResponse(OWNER_PATH, MANIFEST.replace("\"application\":\"operations-console\"", "\"application\":\"client-console\""));
        assertEquals(502, get("", null).statusCode());

        platform.reset();
        assertEquals(400, get("?locale=zh_cn", null).statusCode());
        assertEquals(400, get("?releaseNo=0", null).statusCode());
        assertTrue(platform.requests().isEmpty(), platform.requests().toString());
    }

    /** Gateway PUBLIC 路由的匿名请求：无操作人 Header、无 Origin。 */
    private HttpResponse<String> get(String query, String ifNoneMatch) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + LocalizationManifestController.PATH + query))
            .timeout(Duration.ofSeconds(15)).header("X-Request-Id", REQUEST_ID);
        if (ifNoneMatch != null) {
            builder.header("If-None-Match", ifNoneMatch);
        }
        return HTTP.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Map<String, String> query(String raw) {
        var result = new java.util.HashMap<String, String>();
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(parts[0], parts[1]);
        }
        return result;
    }
}
