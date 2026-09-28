package com.paytech.operations.console.bff.auto;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.paytech.operations.console.bff.OperationsConsoleBffApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import static com.paytech.operations.console.bff.test.OperationsProblemChecker.assertProblem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** 不配置测试直连且关闭 Discovery，以真实 LoadBalancer 空实例覆盖安全不可用边界。 */
@ActiveProfiles("auto")
@SpringBootTest(classes = OperationsConsoleBffApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "operations.platform-test-base-url=")
class OperationsPlatformDiscoveryUnavailableAutoTest {
    @LocalServerPort
    private int port;

    @Test
    void test_noDiscoveredPlatformInstanceReturnsSafe503() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/auth/v1/login"))
            .timeout(Duration.ofSeconds(8)).header("Origin", "https://operations-console.local.test")
            .header("Content-Type", "application/json").header("X-Request-Id", "discovery-unavailable-request")
            .POST(HttpRequest.BodyPublishers.ofString("{\"loginIdentifier\":\"test@example.test\",\"password\":\"synthetic-password\"}"))
            .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        var body = assertProblem(JsonMapper.builder().build(), response, 503, "DEPENDENCY_UNAVAILABLE",
            "discovery-unavailable-request");
        assertEquals("依赖服务暂时不可用，请稍后重试", body.path("detail").asString());
        assertEquals("discovery-unavailable-request", body.path("requestId").asString());
        assertFalse(response.body().contains("platform-support-service"));
        assertFalse(response.body().contains("synthetic-password"));
    }
}
