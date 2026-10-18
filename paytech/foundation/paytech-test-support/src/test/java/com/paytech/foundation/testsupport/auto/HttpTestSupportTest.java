package com.paytech.foundation.testsupport.auto;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.foundation.testsupport.api.HttpTestInvoker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 公共入口通过真实 Loopback HTTP 验证，响应和记录每例重建。 */
class HttpTestSupportTest {
    private static final String REQUEST_ID = "Test.Request-1";
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final List<ReceivedRequest> received = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private HttpClient transport;
    private HttpTestInvoker invoker;
    private volatile int responseStatus;
    private volatile String responseType;
    private volatile String responseBody;
    private volatile String responseRequestId;

    /** 每例独立端口、响应和客户端；不接触共享数据库或其他中间件。 */
    @BeforeEach
    void setUp() throws IOException {
        responseStatus = 201;
        responseType = "application/json";
        responseBody = "{\"reference\":\"resource-1\"}";
        responseRequestId = REQUEST_ID;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.start();
        transport = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(transport);
        factory.setReadTimeout(Duration.ofSeconds(3));
        var client = RestTestClient.bindToServer(factory)
            .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build();
        invoker = new HttpTestInvoker(client);
    }

    /** 先结束请求和服务，再清除本例记录。 */
    @AfterEach
    void tearDown() {
        if (transport != null) {
            transport.close();
        }
        if (server != null) {
            server.stop(0);
        }
        received.clear();
    }

    /** DTO 实际序列化发送，Header 允许覆盖且不会污染下一次请求。 */
    @Test
    void test_clientRequestAndCheckerInheritance() {
        var headers = TestRequestHeaders.client(REQUEST_ID, "101", "202");
        headers.set(ApiHeaderNames.GATEWAY_CLIENT_ID, "303");
        var checker = checker(invoker.post("/orders?source=test", headers, Map.of("amount", "10.00")));
        assertSame(checker, checker.assertStatus(201).assertContentType(MediaType.APPLICATION_JSON)
            .assertRequestId().assertHeader("X-Test-Result", "present").assertReference("resource-1"));
        assertEquals(201, checker.status());
        var request = received.getFirst();
        assertEquals("POST", request.method);
        assertEquals("/orders?source=test", request.uri);
        assertEquals("10.00", mapper.readTree(request.body).path("amount").asString());
        assertEquals("303", request.headers.getFirst(ApiHeaderNames.GATEWAY_CLIENT_ID));
        assertEquals("202", request.headers.getFirst(ApiHeaderNames.GATEWAY_CREDENTIAL_ID));
        assertEquals(REQUEST_ID, request.headers.getFirst(ApiHeaderNames.REQUEST_ID));
        assertEquals("101", TestRequestHeaders.client(REQUEST_ID, "101", "202")
            .getFirst(ApiHeaderNames.GATEWAY_CLIENT_ID));
        assertFalse(request.headers.containsHeader(ApiHeaderNames.IDEMPOTENCY_KEY));
    }

    /** 显式删除可信 Header、非法身份和畸形 JSON 都原样到达服务端。 */
    @Test
    void test_negativeInputIsNotRepaired() {
        var headers = TestRequestHeaders.client(REQUEST_ID, "invalid-id", null);
        headers.remove(ApiHeaderNames.GATEWAY_CLIENT_ID);
        headers.remove(ApiHeaderNames.REQUEST_ID);
        headers.setContentType(MediaType.TEXT_PLAIN);
        String malformed = "{\"amount\":";
        invoker.post("/raw", headers, malformed);
        var request = received.getFirst();
        assertEquals(malformed, request.body);
        assertFalse(request.headers.containsHeader(ApiHeaderNames.GATEWAY_CLIENT_ID));
        assertFalse(request.headers.containsHeader(ApiHeaderNames.GATEWAY_CREDENTIAL_ID));
        assertFalse(request.headers.containsHeader(ApiHeaderNames.REQUEST_ID));
        assertTrue(request.headers.getContentType().isCompatibleWith(MediaType.TEXT_PLAIN));
        assertEquals("invalid-id", TestRequestHeaders.client(null, "invalid-id", "0")
            .getFirst(ApiHeaderNames.GATEWAY_CLIENT_ID));
    }

    /** Customer 和内部入口不携带 Client Gateway 身份或请求幂等键。 */
    @Test
    void test_customerAndInternalHeadersStaySeparate() {
        var customer = TestRequestHeaders.customer(REQUEST_ID);
        customer.set("X-Consumer-Test", "consumer");
        invoker.get("/checkout/token", customer);
        var internal = TestRequestHeaders.internal(REQUEST_ID);
        internal.set("X-Caller-Service", "commerce-service");
        invoker.exchange(HttpMethod.DELETE, "/internal/resource", internal, null);
        assertEquals(List.of("GET", "DELETE"), received.stream().map(request -> request.method).toList());
        for (var request : received) {
            assertFalse(request.headers.containsHeader(ApiHeaderNames.GATEWAY_CLIENT_ID));
            assertFalse(request.headers.containsHeader(ApiHeaderNames.GATEWAY_CREDENTIAL_ID));
            assertFalse(request.headers.containsHeader(ApiHeaderNames.IDEMPOTENCY_KEY));
            assertEquals("", request.body);
        }
        assertFalse(received.getFirst().headers.containsHeader("X-Caller-Service"));
        assertFalse(received.getLast().headers.containsHeader("X-Consumer-Test"));
    }

    /** 传输层直接交回错误及重定向响应，不暗中断言成功、重试或跳转。 */
    @ParameterizedTest
    @ValueSource(ints = {302, 409, 503})
    void test_nonSuccessIsReturnedOnce(int status) {
        responseStatus = status;
        checker(invoker.get("/status", TestRequestHeaders.internal(REQUEST_ID))).assertStatus(status);
        assertEquals(1, received.size());
    }

    /** 无内容响应仍可检查状态及 Header，不强制解析 JSON。 */
    @Test
    void test_emptyResponse() {
        responseStatus = 204;
        responseBody = null;
        checker(invoker.exchange(HttpMethod.DELETE, "/resource", TestRequestHeaders.internal(REQUEST_ID), null))
            .assertStatus(204).assertRequestId().assertEmptyBody();
    }

    /** 通用 Problem 断言保留子类类型，并且字段必须实际存在。 */
    @Test
    void test_problemAndFieldErrors() {
        respondProblem();
        var checker = checker(invoker.post("/problem", TestRequestHeaders.client(REQUEST_ID, "1", "2"), Map.of()));
        assertSame(checker, checker.assertProblem(422, "VALIDATION_ERROR").assertFieldError("amount"));
        assertThrows(AssertionError.class, () -> checker.assertFieldError("missing"));
        assertThrows(AssertionError.class, () -> checker.assertProblem(409, "VALIDATION_ERROR"));
        assertThrows(AssertionError.class, () -> checker.assertProblem(422, "OTHER_CODE"));
    }

    /** 公共 Checker 必须发现协议缺陷，不能只检查 HTTP 状态。 */
    @ParameterizedTest
    @CsvSource({"type,wrong", "title,Wrong Title", "detail,''", "requestId,other", "timestamp,invalid",
        "timestamp,2026-09-17T00:00:00Z"})
    void test_invalidProblemIsRejected(String field, String value) {
        respondProblem();
        var json = (tools.jackson.databind.node.ObjectNode) mapper.readTree(responseBody);
        json.put(field, value);
        responseBody = json.toString();
        var checker = checker(invoker.get("/problem", TestRequestHeaders.internal(REQUEST_ID)));
        assertThrows(AssertionError.class, () -> checker.assertProblem(422, "VALIDATION_ERROR"));
    }

    /** 缺少必填字符串及禁止输出的 status 不能被当成合法 Problem。 */
    @ParameterizedTest
    @ValueSource(strings = {"detail", "title", "requestId", "timestamp", "code", "type", "status"})
    void test_missingOrForbiddenProblemField(String field) {
        respondProblem();
        var json = (tools.jackson.databind.node.ObjectNode) mapper.readTree(responseBody);
        if (field.equals("status")) {
            json.put("status", 422);
        } else {
            json.remove(field);
        }
        responseBody = json.toString();
        var checker = checker(invoker.get("/problem", TestRequestHeaders.internal(REQUEST_ID)));
        assertThrows(AssertionError.class, () -> checker.assertProblem(422, "VALIDATION_ERROR"));
    }

    /** 非字符串字段不能因 JSON 文本转换而意外通过协议检查。 */
    @ParameterizedTest
    @ValueSource(strings = {"type", "title", "detail", "code", "requestId", "timestamp"})
    void test_problemFieldsMustBeStrings(String field) {
        respondProblem();
        var json = (tools.jackson.databind.node.ObjectNode) mapper.readTree(responseBody);
        json.put(field, 123);
        responseBody = json.toString();
        var checker = checker(invoker.get("/problem", TestRequestHeaders.internal(REQUEST_ID)));
        assertThrows(AssertionError.class, () -> checker.assertProblem(422, "VALIDATION_ERROR"));
    }

    /** 状态、响应头、媒体类型和 JSON 错误分别有明确失败。 */
    @Test
    void test_responseChecksRejectMismatch() {
        var checker = checker(invoker.get("/resource", TestRequestHeaders.internal(REQUEST_ID)));
        assertThrows(AssertionError.class, () -> checker.assertHeader("X-Test-Result", "wrong"));
        assertThrows(AssertionError.class, () -> checker.assertContentType(MediaType.APPLICATION_PROBLEM_JSON));
        assertThrows(AssertionError.class, checker::assertEmptyBody);
        responseRequestId = "other";
        responseBody = "not-json";
        var malformed = checker(invoker.get("/resource", TestRequestHeaders.internal(REQUEST_ID)));
        assertThrows(AssertionError.class, malformed::assertRequestId);
        assertThrows(RuntimeException.class, malformed::readBody);
    }

    /** 响应构造使用固定字面值，不调用被测公共校验算法生成预期。 */
    private void respondProblem() {
        responseStatus = 422;
        responseType = "application/problem+json";
        responseBody = """
            {"type":"about:blank","title":"Unprocessable Content","detail":"Invalid amount",
             "code":"VALIDATION_ERROR","requestId":"Test.Request-1","timestamp":"2026-09-17T00:00:00.000000Z",
             "errors":[{"field":"amount","message":"Amount is required"}]}
            """;
    }

    /** 本例服务保存实际入站证据，然后输出当前配置的响应。 */
    private void respond(HttpExchange exchange) throws IOException {
        try (exchange) {
            var headers = new HttpHeaders();
            exchange.getRequestHeaders().forEach((key, values) -> headers.put(key, List.copyOf(values)));
            received.add(new ReceivedRequest(exchange.getRequestMethod(), exchange.getRequestURI().toString(), headers,
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, responseType);
            exchange.getResponseHeaders().set(ApiHeaderNames.REQUEST_ID, responseRequestId);
            exchange.getResponseHeaders().set("X-Test-Result", "present");
            exchange.getResponseHeaders().set(HttpHeaders.LOCATION, "/unexpected-redirect");
            byte[] content = responseBody == null ? new byte[0] : responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, content.length == 0 ? -1 : content.length);
            exchange.getResponseBody().write(content);
        }
    }

    /** 通过实际 HTTP 结果创建领域无关的测试子类。 */
    private SampleResponseChecker checker(EntityExchangeResult<byte[]> response) {
        return new SampleResponseChecker(response, mapper, REQUEST_ID);
    }

    /** 验证服务独立 Checker 能在公共断言后继续链式业务断言。 */
    private static final class SampleResponseChecker extends AbstractHttpResponseChecker<SampleResponseChecker> {
        /** 传入真实调用结果。 */
        private SampleResponseChecker(EntityExchangeResult<byte[]> result, JsonMapper mapper, String requestId) {
            super(result, mapper, requestId);
        }

        /** 返回具体 Checker，避免调用方转换类型。 */
        @Override
        protected SampleResponseChecker self() {
            return this;
        }

        /** 示例业务断言属于子类。 */
        private SampleResponseChecker assertReference(String expected) {
            assertEquals(expected, body().path("reference").asString());
            return this;
        }

        /** 暴露 JSON 读取用于畸形响应负例。 */
        private JsonNode readBody() {
            return body();
        }
    }

    /** 测试服务收到的一次请求，实例不跨测试方法共享。 */
    private static final class ReceivedRequest {
        private final String method;
        private final String uri;
        private final HttpHeaders headers;
        private final String body;

        /** 保存已经读取的请求内容。 */
        private ReceivedRequest(String method, String uri, HttpHeaders headers, String body) {
            this.method = method;
            this.uri = uri;
            this.headers = headers;
            this.body = body;
        }
    }
}
