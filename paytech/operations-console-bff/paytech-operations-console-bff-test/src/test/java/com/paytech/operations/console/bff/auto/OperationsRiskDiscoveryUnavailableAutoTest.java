package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.OperationsProblemChecker.assertProblem;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.AUTHORIZE_PATH;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RISK_ACCOUNT_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.SESSION_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.paytech.operations.console.bff.OperationsConsoleBffApplication;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.blocking.client.BlockingLoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.aop.framework.ProxyFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

/** 真实默认 LoadBalancer 在 auto 空 Discovery 中解析 Risk；仅透明观察 choose 次数。 */
@ActiveProfiles("auto")
@SpringBootTest(classes = {OperationsConsoleBffApplication.class, OperationsRiskDiscoveryUnavailableAutoTest.Configuration.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "operations.risk-test-base-url=")
class OperationsRiskDiscoveryUnavailableAutoTest {
    private static final PlatformOperationsSimulator PLATFORM = new PlatformOperationsSimulator();
    private static final String REQUEST_ID = "risk-discovery-browser-request";
    @LocalServerPort private int port;
    @Autowired private LoadBalancerClient loadBalancer;
    @Autowired private DiscoveryObservation observation;

    @DynamicPropertySource
    static void platformAddress(DynamicPropertyRegistry registry) {
        registry.add("operations.platform-test-base-url", PLATFORM::baseUrl);
        registry.add("operations.console-origin", () -> "https://operations-console.local.test");
    }

    @BeforeEach void setUp() { PLATFORM.reset(); PLATFORM.useRiskIdentity(); observation.choices.clear(); }
    @AfterEach void reset() {
        assertTrue(PLATFORM.unexpectedRequests().isEmpty());
        PLATFORM.reset();
        assertEquals(PlatformOperationsSimulator.ACCOUNT_ID, PLATFORM.activeAccountId());
    }

    @Test
    void missingRiskInstanceReturns503AfterOnePltWithoutRetryOrAddress() throws Exception {
        assertInstanceOf(BlockingLoadBalancerClient.class, loadBalancer);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/decision-rule-sets/v1/authoring-context/CHECKOUT_PREPARE"))
            .timeout(Duration.ofSeconds(8)).header("Origin", "https://operations-console.local.test")
            .header("X-Request-Id", REQUEST_ID).header("X-Gateway-Realm", "OPERATIONS")
            .header("X-Gateway-Channel", "OPERATIONS").header("X-Gateway-Actor-Type", "PLATFORM_USER")
            .header("X-Gateway-Actor-Subject", RISK_ACCOUNT_ID).header("X-Gateway-Session-Id", SESSION_ID)
            .header("X-Gateway-Route-Code", "operations-risk-rule-authoring-context")
            .header("X-Gateway-Context-Type", "TENANT").header("X-Gateway-Tenant-Type", "INTERNAL")
            .header("X-Gateway-Tenant-Id", "tenant_10001").header("X-Gateway-Tenant-Member-Id", "member_10001")
            .GET().build();
        HttpResponse<String> response = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
            .send(request, HttpResponse.BodyHandlers.ofString());
        var body = assertProblem(JsonMapper.builder().build(), response, 503, "DEPENDENCY_UNAVAILABLE", REQUEST_ID);
        assertEquals("依赖服务暂时不可用，请稍后重试", body.path("detail").asString());
        assertEquals(1, PLATFORM.requests().size());
        assertEquals(AUTHORIZE_PATH, PLATFORM.requests().getFirst().path());
        assertEquals(1, PLATFORM.completedAuthorizationCount());
        assertEquals(List.of("risk-decision-service"), observation.choices, "真实 LoadBalancer.choose 必须且只能执行一次");
        for (String address : java.util.List.of("risk-decision-service", "127.0.0.1", "http://", "localhost", "发现实例"))
            assertFalse(response.body().contains(address));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean(destroyMethod = "close") PlatformOperationsSimulator platformOperationsSimulator() { return PLATFORM; }
        @Bean static DiscoveryObservation discoveryObservation() { return new DiscoveryObservation(); }
    }

    /** 仅观察真实方法入口；无返回值替换、无异常替换、无发现实例替身。 */
    static final class DiscoveryObservation implements BeanPostProcessor {
        private final List<String> choices = new CopyOnWriteArrayList<>();
        @Override public Object postProcessAfterInitialization(Object bean, String name) {
            if (!(bean instanceof BlockingLoadBalancerClient)) return bean;
            ProxyFactory proxy = new ProxyFactory(bean);
            proxy.setProxyTargetClass(true);
            proxy.addAdvice((MethodInterceptor) invocation -> {
                if (invocation.getMethod().getName().equals("choose")) choices.add((String) invocation.getArguments()[0]);
                return invocation.proceed();
            });
            return proxy.getProxy();
        }
    }
}
