package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.OperationsProblemChecker.assertProblem;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.ACCOUNT_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.AUTHORIZE_PATH;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.SESSION_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.paytech.operations.console.bff.OperationsConsoleBffApplication;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RecordedRequest;

import tools.jackson.databind.json.JsonMapper;

/**
 * account 固定地址无人监听（连接被拒）时，经一次 PLT-I02 后返回安全的 503 DEPENDENCY_UNAVAILABLE，不重试、不外露地址
 * <li>读取超时 504 由对应 Browser 测试经 Simulator 挂起响应覆盖。</li>
 *
 * @author Claude
 */
@ActiveProfiles("auto")
@SpringBootTest(classes = {OperationsConsoleBffApplication.class, OperationsAccountUnavailableAutoTest.Configuration.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OperationsAccountUnavailableAutoTest {
    private static final PlatformOperationsSimulator PLATFORM = new PlatformOperationsSimulator();
    private static final int CLOSED_PORT = closedPort();
    private static final String REQUEST_ID = "account-unavailable-request";

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void addresses(DynamicPropertyRegistry registry) {
        registry.add("operations.platform-test-base-url", PLATFORM::baseUrl);
        registry.add("operations.account-base-url", () -> "http://127.0.0.1:" + CLOSED_PORT);
        registry.add("operations.console-origin", () -> "https://operations-console.local.test");
    }

    @BeforeEach
    void setUpUnavailableMocks() {
        PLATFORM.reset();
    }

    @AfterEach
    void verifyPlatformCalls() {
        assertTrue(PLATFORM.unexpectedRequests().isEmpty(), PLATFORM.unexpectedRequests().toString());
    }

    @Test
    void test_connectionRefusedReturnsSafe503() throws Exception {
        var response = send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/finance/balances/v1/clients/880000000000071001?entityNo=AE001&currency=USD"))
            .header("X-Gateway-Route-Code", "operations-finance-client-balance").GET());

        assertUnavailable(response);
    }

    private void assertUnavailable(HttpResponse<String> response) {
        var body = assertProblem(JsonMapper.builder().build(), response, 503,
            OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE.getCode(), REQUEST_ID);
        assertEquals(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE.getMessage(), body.path("detail").asString());
        assertEquals(List.of(AUTHORIZE_PATH), PLATFORM.requests().stream().map(RecordedRequest::path).toList());
        assertFalse(response.body().contains(String.valueOf(CLOSED_PORT)));
        assertFalse(response.body().contains("account-service"));
    }

    /** 带 Gateway 可信身份与 Origin 发送浏览器请求。 */
    private static HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        builder.timeout(Duration.ofSeconds(10)).header("Origin", "https://operations-console.local.test")
            .header("X-Request-Id", REQUEST_ID).header("X-Gateway-Realm", "OPERATIONS")
            .header("X-Gateway-Channel", "OPERATIONS").header("X-Gateway-Actor-Type", "PLATFORM_USER")
            .header("X-Gateway-Actor-Subject", ACCOUNT_ID).header("X-Gateway-Session-Id", SESSION_ID)
            .header("X-Gateway-Context-Type", "TENANT").header("X-Gateway-Tenant-Type", "INTERNAL")
            .header("X-Gateway-Tenant-Id", "tenant_10001").header("X-Gateway-Tenant-Member-Id", "member_10001");
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 取得一个刚释放、无人监听的 loopback 端口。 */
    private static int closedPort() {
        try (var socket = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (IOException failure) {
            throw new IllegalStateException("无法分配本地端口", failure);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean(destroyMethod = "close")
        PlatformOperationsSimulator platformOperationsSimulator() {
            return PLATFORM;
        }
    }
}
