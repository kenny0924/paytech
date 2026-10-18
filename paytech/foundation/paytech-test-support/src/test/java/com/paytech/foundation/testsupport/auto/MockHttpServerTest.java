package com.paytech.foundation.testsupport.auto;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.foundation.testsupport.api.MockHttpServer;

/**
 * 独立 Servlet 服务通过真实 HTTP 验证 API 接口映射、绑定和故障，不使用 MockMvc。
 *
 * @see MockHttpServer#MockHttpServer(Object...)
 */
class MockHttpServerTest {
    private MockApi controller;
    private MockHttpServer server;
    private HttpClient client;

    /** 每例使用隔离端口和控制器配置。 */
    @BeforeEach
    void setUp() {
        controller = new MockApi();
        server = new MockHttpServer(controller);
        client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /** 释放阻塞请求后结束真实服务，测试异常不跳过资源回收。 */
    @AfterEach
    void tearDown() {
        controller.release.countDown();
        assertAll(() -> server.awaitIdle(), () -> client.close(), () -> server.close());
    }

    /** 实现类不重复任何路径或参数注解，MVC 继承 API 的完整映射。 */
    @Test
    void test_inheritedMappingAndParameterBinding() throws Exception {
        var response = send("GET", "/contract/value?value=hello%20world", null);
        assertEquals(200, response.statusCode());
        assertEquals("{\"value\":\"hello world\",\"caller\":\"test\"}", response.body());
        server.assertNoUnexpectedRequests();
    }

    /** POST Body 同样由 MVC 从共享接口声明中解析。 */
    @Test
    void test_inheritedBodyBinding() throws Exception {
        var response = send("POST", "/contract/body", "{\"value\":\"configured\"}");
        assertEquals(200, response.statusCode());
        assertEquals("{\"value\":\"configured\"}", response.body());
        server.assertNoUnexpectedRequests();
    }

    /** 业务错误响应是明确配置的故障，不被当作意外请求。 */
    @Test
    void test_configuredJsonFailure() throws Exception {
        controller.fault = MockHttpFault.json(503, Map.of("code", "OWNER_UNAVAILABLE"));
        var response = send("GET", "/contract/value?value=x", null);
        assertEquals(503, response.statusCode());
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("{\"code\":\"OWNER_UNAVAILABLE\"}", response.body());
        server.assertNoUnexpectedRequests();
    }

    /** 畸形原文必须原样经过网络交给调用方，不能被重新 JSON 编码。 */
    @Test
    void test_configuredMalformedBody() throws Exception {
        controller.fault = MockHttpFault.raw(200, "{bad json");
        assertEquals("{bad json", send("GET", "/contract/value?value=x", null).body());
        server.assertNoUnexpectedRequests();
    }

    /** 断开底层连接产生传输错误，不伪装为 HTTP 500。 */
    @Test
    void test_configuredDisconnect() {
        controller.fault = MockHttpFault.disconnect();
        assertThrows(IOException.class, () -> send("GET", "/contract/value?value=x", null));
        server.assertNoUnexpectedRequests();
    }

    /** 进入 Controller 之前的路径、方法和参数错误仍进入意外调用记录。 */
    @ParameterizedTest
    @CsvSource({"GET,/missing,404", "POST,/contract/value?value=x,405", "GET,/contract/value,400"})
    void test_unmatchedRequestIsReportedAndCleared(String method, String path, int expected) throws Exception {
        assertEquals(expected, send(method, path, null).statusCode());
        server.awaitIdle();
        assertThrows(AssertionError.class, server::assertNoUnexpectedRequests);
        server.clear();
        server.assertNoUnexpectedRequests();
    }

    /** 异步未结束时等待失败，释放之后才允许清理共享记录。 */
    @Test
    void test_waitsForActiveRequest() throws Exception {
        controller.block = true;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var request = executor.submit(() -> send("GET", "/contract/value?value=x", null));
            try {
                assertTrue(controller.entered.await(5, TimeUnit.SECONDS));
                assertThrows(ConditionTimeoutException.class, () -> server.awaitIdle(Duration.ofMillis(50)));
                assertThrows(AssertionError.class, server::clear);
            } finally {
                controller.release.countDown();
            }
            assertEquals(200, request.get(5, TimeUnit.SECONDS).statusCode());
        }
        server.awaitIdle();
        server.clear();
    }

    /** 从真实 Socket 发出请求，路径和内容完全由测试指定。 */
    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(server.baseUrl() + path)).timeout(Duration.ofSeconds(5))
            .header("X-Caller", "test").header("Content-Type", "application/json")
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 此测试专用接口代表外部 API JAR；实现类不能重复其映射。 */
    @HttpExchange("/contract")
    interface ContractApi {
        /** 同时验证查询参数及请求头的接口注解。 */
        @GetExchange("/value")
        Map<String, String> value(@RequestParam("value") String value, @RequestHeader("X-Caller") String caller);

        /** 验证 Body 的绑定由 API 接口负责。 */
        @PostExchange("/body")
        Map<String, String> body(@RequestBody Map<String, String> body);
    }

    /** 控制器只返回场景配置，框架提供路由和绑定。 */
    @RestController
    static class MockApi implements ContractApi {
        MockHttpFault fault;
        boolean block;
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        /** 只处理配置和响应，不手工解析 URI 或查询字符串。 */
        @Override
        public Map<String, String> value(String value, String caller) {
            if (fault != null) {
                throw fault;
            }
            if (block) {
                entered.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(failure);
                }
            }
            var body = new java.util.LinkedHashMap<String, String>();
            body.put("value", value);
            body.put("caller", caller);
            return body;
        }

        /** 返回已由 Spring MVC 绑定的 JSON 对象。 */
        @Override
        public Map<String, String> body(Map<String, String> body) {
            return body;
        }
    }
}
