package com.paytech.foundation.testsupport.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.ServletException;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.coyote.ActionCode;
import org.awaitility.Awaitility;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.servlet.ServletWebServerFactory;
import org.springframework.boot.web.server.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import tools.jackson.databind.json.JsonMapper;

/**
 * 独立的本机 Servlet Mock 服务，只装配显式提供的测试 Controller 和 MVC。
 * Controller 可以实现外部 HttpExchange API，路径、参数和 JSON 绑定全部由 Spring MVC 负责。
 */
public final class MockHttpServer implements Clear, AutoCloseable {
    private static final String EXPECTED_FAULT = MockHttpServer.class.getName() + ".fault";
    private final AnnotationConfigServletWebServerApplicationContext context;
    private final List<String> unexpectedRequests = new CopyOnWriteArrayList<>();
    private final AtomicInteger activeRequests = new AtomicInteger();

    /** 使用独立上下文和随机端口，不扫描宿主业务 Bean，不启动自动配置或中间件。 */
    public MockHttpServer(Object... controllers) {
        context = new AnnotationConfigServletWebServerApplicationContext();
        context.register(MvcConfiguration.class);
        for (int index = 0; index < controllers.length; index++) {
            context.getBeanFactory().registerSingleton("mockController" + index, controllers[index]);
        }
        context.registerBean(ServletWebServerFactory.class, this::serverFactory);
        context.registerBean(DispatcherServlet.class, () -> new DispatcherServlet(context));
        try {
            context.refresh();
        } catch (RuntimeException | Error failure) {
            context.close();
            throw failure;
        }
    }

    /** 供测试服务发现注册运行时地址，API 客户端仍使用原服务名与负载均衡。 */
    public String baseUrl() {
        return "http://127.0.0.1:" + context.getWebServer().getPort();
    }

    /** 每例清理前等待正在处理的请求退出。 */
    public void awaitIdle() {
        awaitIdle(Duration.ofSeconds(5));
    }

    /** 按测试场景的有界等待确认请求结束，不固定休眠或吞掉超时。 */
    public void awaitIdle(Duration timeout) {
        Awaitility.await().pollDelay(Duration.ZERO).pollInterval(Duration.ofMillis(10)).atMost(timeout)
            .until(() -> activeRequests.get() == 0);
    }

    /** 包括未进入 Controller 的路径、方法和绑定错误，防止客户端捕获异常后误通过。 */
    public void assertNoUnexpectedRequests() {
        assertTrue(unexpectedRequests.isEmpty(), () -> "Mock HTTP 意外请求：" + unexpectedRequests);
    }

    /** 只清本服务的请求错误记录；业务 DTO 配置由对应 Mocker 自己清理。 */
    @Override
    public void clear() {
        assertEquals(0, activeRequests.get(), "请求尚未结束，禁止清理 Mock HTTP 记录");
        unexpectedRequests.clear();
    }

    /** 测试上下文结束时关闭独立 Servlet 容器和 MVC 上下文。 */
    @Override
    public void close() {
        context.close();
    }

    /** 仅监听本机，所有业务路径交给 DispatcherServlet。 */
    private ServletWebServerFactory serverFactory() {
        var factory = new TomcatServletWebServerFactory(0);
        factory.setAddress(InetAddress.ofLiteral("127.0.0.1"));
        factory.addContextValves(new RequestObservationValve());
        return factory;
    }

    /** 在 Servlet 容器边界计数并观察响应，连接故障在这里操作实际 Socket。 */
    private final class RequestObservationValve extends ValveBase {
        /** 跟踪完整请求生命周期，明确配置的故障不计入意外请求。 */
        @Override
        public void invoke(Request request, Response response) throws IOException, ServletException {
            activeRequests.incrementAndGet();
            try {
                getNext().invoke(request, response);
                var fault = (MockHttpFault) request.getAttribute(EXPECTED_FAULT);
                if (fault != null && fault.disconnect) {
                    request.getCoyoteRequest().action(ActionCode.CLOSE_NOW, new IOException("Mock 主动断开连接"));
                } else if (fault == null && response.getStatus() >= 400) {
                    unexpectedRequests.add(request.getMethod() + " " + request.getRequestURI() + " -> " + response.getStatus());
                }
            } catch (IOException | ServletException | RuntimeException | Error failure) {
                unexpectedRequests.add(request.getMethod() + " " + request.getRequestURI() + " -> " + failure.getClass().getSimpleName());
                throw failure;
            } finally {
                activeRequests.decrementAndGet();
            }
        }
    }

    /** 仅启用 MVC 与测试故障响应，不加载业务异常处理、扫描或自动配置。 */
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class MvcConfiguration implements WebMvcConfigurer {
        private final JsonMapper json = JsonMapper.builder().build();

        /** 正常返回值仍由 MVC 转换；故障响应只拦截明确的测试专用异常。 */
        @Override
        public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
            resolvers.addFirst((request, response, handler, exception) -> {
                if (!(exception instanceof MockHttpFault fault)) {
                    return null;
                }
                request.setAttribute(EXPECTED_FAULT, fault);
                if (!fault.disconnect) {
                    response.setStatus(fault.status);
                    response.setContentType(fault.status < 400 ? "application/json" : "application/problem+json");
                    var body = fault.rawBody == null ? json.writeValueAsString(fault.body) : fault.rawBody;
                    try {
                        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
                    } catch (IOException failure) {
                        throw new IllegalStateException("写入 Mock HTTP 响应失败", failure);
                    }
                }
                return new ModelAndView();
            });
        }
    }
}
