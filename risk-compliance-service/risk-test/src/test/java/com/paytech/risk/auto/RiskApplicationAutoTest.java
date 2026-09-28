package com.paytech.risk.auto;

import com.paytech.risk.test.base.RiskServiceAutoTestBase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;

/**
 * 通过真实随机端口和已准备的本地数据库验证 Risk 启动骨架。
 */
class RiskApplicationAutoTest extends RiskServiceAutoTestBase {

    @Autowired
    private WebEndpointsSupplier endpoints;

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    void test_healthReportsUpWithoutDetails(String path) throws Exception {
        HttpResponse<String> response = get(path);
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertTrue(response.body().contains("\"status\":\"UP\""));
        Assertions.assertFalse(response.body().contains("\"components\""));
        Assertions.assertFalse(response.body().contains("\"details\""));
    }

    @Test
    void test_onlyApprovedManagementEndpointsAreRegistered() {
        Set<String> ids = endpoints.getEndpoints().stream()
            .map(endpoint -> endpoint.getEndpointId().toString()).collect(Collectors.toSet());
        Assertions.assertEquals(Set.of("health", "info", "prometheus"), ids);
    }

    @Test
    void test_prometheusIsAvailable() throws Exception {
        HttpResponse<String> response = get("/actuator/prometheus");
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertTrue(response.body().contains("jvm_memory_used_bytes"));
    }

    @Test
    void test_infoDoesNotExposeEnvironment() throws Exception {
        HttpResponse<String> response = get("/actuator/info");
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertEquals("{}", response.body());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(10)).GET().build();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
