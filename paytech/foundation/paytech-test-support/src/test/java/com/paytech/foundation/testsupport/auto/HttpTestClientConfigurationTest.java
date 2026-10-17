package com.paytech.foundation.testsupport.auto;

import static org.junit.jupiter.api.Assertions.*;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.resttestclient.autoconfigure.RestTestClientBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.http.server.LocalTestWebServer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.annotation.DirtiesContext.HierarchyMode;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.testsupport.api.HttpTestInvoker;

/**
 * 通过 Boot 测试注解与真实随机端口验证公共 HTTP 客户端装配，不显式导入被测配置。
 *
 * @see HttpTestInvoker
 * @see AutoConfigureRestTestClient
 */
class HttpTestClientConfigurationTest {
    private TestContextManager manager;

    /** 每例销毁 Spring 上下文、服务与传输，不复用请求记录。 */
    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.getTestContext().markApplicationContextDirty(HierarchyMode.CURRENT_LEVEL);
        }
    }

    /** 注解自动提供调用器，保留随机地址、Context Path、宿主传输及有序定制。 */
    @ParameterizedTest
    @ValueSource(ints = {200, 302, 503})
    void test_annotationUsesHostTransport(int status) throws Exception {
        var context = start(RealServerFixture.class);
        var invoker = context.getBean(HttpTestInvoker.class);
        var result = invoker.get("/probe?status=" + status, new HttpHeaders());
        assertEquals(status, result.getStatus().value());
        assertEquals("second", result.getResponseHeaders().getFirst("X-Observed-Customizer"));
        assertEquals("host", result.getResponseHeaders().getFirst("X-Observed-Transport"));
        var endpoint = context.getBean(ProbeController.class);
        assertEquals(List.of(status), endpoint.statuses);
        assertEquals(1, context.getBeansOfType(RestTestClient.class).size());
        assertEquals(1, context.getBeansOfType(HttpTestInvoker.class).size());
    }

    /** 用户提供客户端时保留该实例，公共调用器复用它。 */
    @Test
    void test_customClientTakesPrecedence() throws Exception {
        var context = start(CustomClientFixture.class);
        assertSame(context.getBean("customClient"), context.getBean(RestTestClient.class));
        var result = context.getBean(HttpTestInvoker.class).get("/probe", new HttpHeaders());
        assertEquals("custom-client", result.getResponseHeaders().getFirst("X-Observed-Customizer"));
        assertEquals(1, context.getBeansOfType(RestTestClient.class).size());
    }

    /** 用户提供调用器时不重复注册。 */
    @Test
    void test_customInvokerTakesPrecedence() throws Exception {
        var context = start(CustomInvokerFixture.class);
        assertSame(context.getBean("customInvoker"), context.getBean(HttpTestInvoker.class));
        assertEquals(1, context.getBeansOfType(HttpTestInvoker.class).size());
        assertEquals(200, context.getBean(HttpTestInvoker.class).get("/probe", new HttpHeaders()).getStatus().value());
    }

    /** 仅引入测试 JAR 不应在普通应用启动时启用 HTTP 测试组件。 */
    @Test
    void test_withoutAnnotationDoesNotRegisterClients() throws Exception {
        var context = start(NoAnnotationFixture.class);
        assertTrue(context.getBeansOfType(HttpTestInvoker.class).isEmpty());
        assertTrue(context.getBeansOfType(RestTestClient.class).isEmpty());
    }

    /** 没有实际监听端口的测试继续由 Boot 创建 MVC 客户端。 */
    @Test
    void test_mockWebContextKeepsBootClient() throws Exception {
        var context = start(MockWebFixture.class);
        var result = context.getBean(HttpTestInvoker.class).get("/probe", new HttpHeaders());
        assertEquals(200, result.getStatus().value());
        assertEquals("second", result.getResponseHeaders().getFirst("X-Observed-Customizer"));
        assertNull(result.getResponseHeaders().getFirst("X-Observed-Transport"));
    }

    /** 使用 Spring Test 实际解析注解和资源导入，并交由后置生命周期关闭上下文。 */
    private ConfigurableApplicationContext start(Class<?> fixture) throws Exception {
        manager = new TestContextManager(fixture);
        manager.getTestContext().markApplicationContextDirty(HierarchyMode.CURRENT_LEVEL);
        return (ConfigurableApplicationContext) manager.getTestContext().getApplicationContext();
    }

    /** 不添加公共 HTTP 配置的基础应用测试。 */
    @SpringBootTest(classes = HostApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.main.banner-mode=off", "logging.level.root=WARN", "server.servlet.context-path=/test-host"})
    static class NoAnnotationFixture {
    }

    /** 仅使用 Boot 已有注解。 */
    @AutoConfigureRestTestClient
    static class RealServerFixture extends NoAnnotationFixture {
    }

    /** 验证客户端定义优先于自动配置。 */
    @Import(CustomClientConfiguration.class)
    static class CustomClientFixture extends RealServerFixture {
    }

    /** 验证调用器定义优先于自动配置。 */
    @Import(CustomInvokerConfiguration.class)
    static class CustomInvokerFixture extends RealServerFixture {
    }

    /** MOCK 不存在 LocalTestWebServer，不能绑定到一个虚构端口。 */
    @AutoConfigureRestTestClient
    @SpringBootTest(classes = HostApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"spring.main.banner-mode=off", "logging.level.root=WARN"})
    static class MockWebFixture {
    }

    /** 仅启动验证公共机制所需的 HTTP 应用，无业务数据库或外部服务。 */
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(ProbeController.class)
    static class HostApplication {
        /** 宿主拥有实际传输及关闭生命周期。 */
        @Bean
        HttpClient transport() {
            return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        }

        /** 添加传输标记以证明客户端确实复用宿主工厂，而不是 Boot 默认工厂。 */
        @Bean
        ClientHttpRequestFactory requestFactory(HttpClient transport) {
            var factory = new JdkClientHttpRequestFactory(transport);
            factory.setReadTimeout(Duration.ofSeconds(3));
            return (uri, method) -> {
                var request = factory.createRequest(uri, method);
                request.getHeaders().set("X-Transport", "host");
                return request;
            };
        }

        /** 第一个定制器写入初始值。 */
        @Bean
        @Order(0)
        RestTestClientBuilderCustomizer firstCustomizer() {
            return builder -> builder.defaultHeader("X-Customizer", "first");
        }

        /** 第二个定制器覆盖值，验证有序组合。 */
        @Bean
        @Order(1)
        RestTestClientBuilderCustomizer secondCustomizer() {
            return builder -> builder.defaultHeader("X-Customizer", "second");
        }
    }

    /** 返回实际入站证据；若错误跟随跳转，则路径不存在使验证失败。 */
    @RestController
    static class ProbeController {
        private final List<Integer> statuses = new CopyOnWriteArrayList<>();

        /** 记录收到的次数，返回调用者选择的 HTTP 状态。 */
        @GetMapping("/probe")
        ResponseEntity<Void> probe(@RequestParam(defaultValue = "200") int status,
            @RequestHeader(value = "X-Customizer", required = false) String customizer,
            @RequestHeader(value = "X-Transport", required = false) String transport) {
            statuses.add(status);
            return ResponseEntity.status(status).header("X-Observed-Customizer", customizer)
                .header("X-Observed-Transport", transport).header(HttpHeaders.LOCATION, "/unexpected").build();
        }
    }

    /** 显式工厂用于验证用户覆盖公共默认值。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class CustomClientConfiguration {
        /** 自定义客户端仍指向同一个真实随机端口。 */
        @Bean
        @Lazy
        RestTestClient customClient(ApplicationContext context, ClientHttpRequestFactory requestFactory) {
            return RestTestClient.bindToServer(requestFactory)
                .uriBuilderFactory(LocalTestWebServer.get(context).uriBuilderFactory())
                .defaultHeader("X-Customizer", "custom-client").build();
        }
    }

    /** 显式工厂只用于验证调用器的覆盖契约。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class CustomInvokerConfiguration {
        /** 用户的调用器定义优先。 */
        @Bean
        @Lazy
        HttpTestInvoker customInvoker(RestTestClient client) {
            return new HttpTestInvoker(client);
        }
    }
}
