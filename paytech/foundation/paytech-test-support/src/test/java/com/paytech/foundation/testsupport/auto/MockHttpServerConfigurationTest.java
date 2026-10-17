package com.paytech.foundation.testsupport.auto;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.annotation.DirtiesContext.HierarchyMode;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.context.ConfigurableApplicationContext;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.EnableMockHttpServer;
import com.paytech.foundation.testsupport.api.MockHttpServer;
import com.paytech.foundation.testsupport.test.mockhttp.MockHttpFixtures.ExternalApi;
import com.paytech.foundation.testsupport.test.mockhttp.MockHttpFixtures.ResponseMocker;

/**
 * 通过实际 Spring Test 上下文验证扫描、隔离、动态端点与自动销毁。
 *
 * @see EnableMockHttpServer
 */
class MockHttpServerConfigurationTest {
    /** 包名配置错误在启动时失败，避免生成没有路由的空服务。 */
    @Test
    void test_emptyScanFailsAtStartup() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(EmptyConfiguration.class);
            var failure = assertThrows(IllegalArgumentException.class, context::refresh);
            assertTrue(failure.getMessage().contains("扫描范围中没有"));
        }
    }

    /** 测试与 HTTP Controller 使用同一 Mocker；Mock API 不暴露成宿主 Bean 或路由。 */
    @Test
    void test_scansMocksAndKeepsControllersIsolated() throws Exception {
        var fixture = startFixture();
        try (var context = fixture.context; var client = HttpClient.newHttpClient()) {
            fixture.mocker.value = "configured";
            var response = send(client, fixture.endpoint);
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"value\":\"configured\""));
            assertTrue(response.body().contains("\"id\":\"test\""));
            assertEquals(1, fixture.mocker.calls);
            assertEquals(1, fixture.mocker.initialized);
            assertSame(fixture.mocker, fixture.selectedSupplier);
            assertEquals(fixture.server.baseUrl(), fixture.endpoint);
            assertTrue(context.getBeansOfType(ExternalApi.class).isEmpty());
            assertTrue(context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods().isEmpty());
            assertTrue(fixture.clears.contains(fixture.mocker));
            assertTrue(fixture.clears.contains(fixture.server));
            fixture.server.awaitIdle();
            fixture.server.assertNoUnexpectedRequests();
            fixture.clears.forEach(Clear::clear);
            assertEquals(0, fixture.mocker.calls);
            assertNull(fixture.mocker.value);
        }
        assertEquals(1, fixture.mocker.destroyed);
    }

    /** 重建上下文创建新的服务和 Mocker，关闭后旧端口不再可用。 */
    @Test
    void test_contextOwnsServerLifecycle() throws Exception {
        var first = startFixture();
        try (var ignored = first.context) {
            first.mocker.value = "previous";
        }
        assertEquals(1, first.mocker.destroyed);
        try (var client = HttpClient.newHttpClient()) {
            assertThrows(IOException.class, () -> send(client, first.endpoint));
        }
        var second = startFixture();
        try (var ignored = second.context) {
            assertNotSame(first.server, second.server);
            assertNotSame(first.mocker, second.mocker);
            assertNull(second.mocker.value);
            assertEquals(1, second.mocker.initialized);
        }
        assertEquals(1, second.mocker.destroyed);
    }

    /** 使用真实 TestContext 注册动态属性，每次先移除前次已经关闭的缓存上下文。 */
    private Fixture startFixture() throws Exception {
        var manager = new TestContextManager(Fixture.class);
        manager.getTestContext().markApplicationContextDirty(HierarchyMode.CURRENT_LEVEL);
        var fixture = new Fixture();
        manager.prepareTestInstance(fixture);
        return fixture;
    }

    /** 请求真实监听端口，不使用 MockMvc 或替换服务器方法。 */
    private HttpResponse<String> send(HttpClient client, String endpoint) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(endpoint + "/managed-mock?id=test"))
            .timeout(Duration.ofSeconds(3)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** 同一配置可供多次独立测试上下文创建。 */
    @WebAppConfiguration
    @ContextConfiguration(classes = HostConfiguration.class)
    static class Fixture {
        @Autowired
        ConfigurableApplicationContext context;

        @Autowired
        MockHttpServer server;

        @Autowired
        ResponseMocker mocker;

        @Autowired
        List<Clear> clears;

        @Autowired
        String endpoint;

        @Autowired
        Supplier<String> selectedSupplier;
    }

    /** 显式选择测试包，服务器和 Mocker 没有手写 Bean 工厂。 */
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableMockHttpServer(basePackages = {"com.paytech.foundation.testsupport.test.mockhttp",
        "com.paytech.foundation.testsupport.test.mockhttp"})
    static class HostConfiguration {
        /** 宿主原有同类型 Bean 保留，Primary 必须让消费方选择测试组件。 */
        @Bean
        Supplier<String> originalSupplier() {
            return () -> "original";
        }

        /** 模拟在宿主客户端初始化时读取动态地址，验证注册时机。 */
        @Bean
        String endpoint(@Value("${test.mock-http.base-url}") String endpoint) {
            return endpoint;
        }
    }

    /** 错误扫描范围用于验证明确失败，无需启动 HTTP 服务。 */
    @Configuration(proxyBeanMethods = false)
    @EnableMockHttpServer(basePackages = "com.paytech.foundation.testsupport.test.missing")
    static class EmptyConfiguration {
    }
}
