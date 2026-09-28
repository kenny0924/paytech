package com.paytech.finance.auto;

import static com.paytech.finance.test.FinanceProblemChecker.assertProblem;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import com.paytech.finance.FinanceApplication;
import com.paytech.foundation.money.Money;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 通过真实随机端口验证无外部依赖的 Finance 启动骨架。
 */
@SpringBootTest(classes = FinanceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("auto")
class FinanceApplicationAutoTest {

    private static final String REQUEST_ID = "Finance.Application_01";

    @LocalServerPort
    private int port;

    @Autowired
    private WebEndpointsSupplier endpoints;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void test_hostJsonMapperUsesStrictMoneyCodecWithoutRegistration() {
        Money amount = Money.of("USD", "9007199254740993.01");
        String json = jsonMapper.writeValueAsString(amount);
        Assertions.assertEquals("{\"currency\":\"USD\",\"amount\":\"9007199254740993.01\"}", json);
        Assertions.assertEquals(amount, jsonMapper.readValue(json, Money.class));
        Assertions.assertThrows(JacksonException.class,
            () -> jsonMapper.readValue("{\"currency\":\"USD\",\"amount\":1.25}", Money.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    void test_healthReportsUpWithoutDetails(String path) throws Exception {
        HttpResponse<String> response = get(path);
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertTrue(response.body().contains("\"status\":\"UP\""));
        Assertions.assertFalse(response.body().contains("\"components\""));
        Assertions.assertFalse(response.body().contains("\"details\""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator", "/actuator/env", "/actuator/beans", "/actuator/configprops", "/payouts"})
    void test_unmappedPathsUseCanonicalNotFoundProblem(String path) throws Exception {
        HttpResponse<String> response = get(path);
        assertProblem(jsonMapper, response, 404, "NOT_FOUND", "资源不存在", REQUEST_ID);
    }

    @Test
    void test_onlyApprovedManagementEndpointsAreRegistered() {
        Set<String> ids = endpoints.getEndpoints().stream().map(endpoint -> endpoint.getEndpointId().toString())
            .collect(Collectors.toSet());
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
            .timeout(Duration.ofSeconds(10)).header("X-Request-Id", REQUEST_ID).GET().build();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
