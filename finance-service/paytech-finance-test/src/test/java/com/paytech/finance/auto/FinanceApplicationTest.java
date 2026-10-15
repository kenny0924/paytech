package com.paytech.finance.auto;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.http.HttpHeaders;

import com.paytech.finance.FinanceApplication;
import com.paytech.finance.test.base.MockTestBase;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.money.Money;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 通过真实随机端口验证 Finance 启动骨架、管理端点暴露范围和 Money 编解码。
 *
 * @see FinanceApplication#main(String[])
 */
class FinanceApplicationTest extends MockTestBase {

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
    void test_healthReportsUpWithoutDetails(String path) {
        var response = get(path).assertStatus(200);
        Assertions.assertEquals("UP", response.text("status"));
        Assertions.assertNull(response.text("components"));
        Assertions.assertNull(response.text("details"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator", "/actuator/env", "/actuator/beans", "/actuator/configprops", "/payouts"})
    void test_unmappedPathsUseCanonicalNotFoundProblem(String path) {
        get(path).assertProblem(404, CommonErrorCode.NOT_FOUND.getCode());
    }

    @Test
    void test_onlyApprovedManagementEndpointsAreRegistered() {
        Set<String> ids = endpoints.getEndpoints().stream().map(endpoint -> endpoint.getEndpointId().toString())
            .collect(Collectors.toSet());
        Assertions.assertEquals(Set.of("health", "info", "prometheus"), ids);
    }

    @Test
    void test_prometheusIsAvailable() {
        var response = http.get("/actuator/prometheus", new HttpHeaders());
        Assertions.assertEquals(200, response.getStatus().value());
        Assertions.assertTrue(new String(response.getResponseBody()).contains("jvm_memory_used_bytes"));
    }

    @Test
    void test_infoDoesNotExposeEnvironment() {
        var response = http.get("/actuator/info", new HttpHeaders());
        Assertions.assertEquals(200, response.getStatus().value());
        Assertions.assertEquals("{}", new String(response.getResponseBody()));
    }
}
