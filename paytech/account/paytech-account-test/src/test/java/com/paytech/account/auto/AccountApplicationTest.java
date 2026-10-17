package com.paytech.account.auto;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.account.AccountApplication;
import com.paytech.account.test.base.MockTestBase;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.money.Money;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 通过真实随机端口验证 account-service 启动骨架、管理端点暴露范围和 Money 编解码
 * <li>由 FinanceApplicationTest 迁入（第 07 步）；Actuator 改在独立管理端口（业务端口 + 1000，测试随机），业务端口不暴露 /actuator。</li>
 *
 * @see AccountApplication#main(String[])
 */
class AccountApplicationTest extends MockTestBase {

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
        EntityExchangeResult<byte[]> response = management(path);
        Assertions.assertEquals(200, response.getStatus().value());
        JsonNode body = jsonMapper.readTree(response.getResponseBody());
        Assertions.assertEquals("UP", body.path("status").asString());
        Assertions.assertFalse(body.has("components"));
        Assertions.assertFalse(body.has("details"));
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
        EntityExchangeResult<byte[]> response = management("/actuator/prometheus");
        Assertions.assertEquals(200, response.getStatus().value());
        Assertions.assertTrue(new String(response.getResponseBody(), StandardCharsets.UTF_8)
            .contains("jvm_memory_used_bytes"));
    }

    @Test
    void test_infoDoesNotExposeEnvironment() {
        EntityExchangeResult<byte[]> response = management("/actuator/info");
        Assertions.assertEquals(200, response.getStatus().value());
        Assertions.assertEquals("{}", new String(response.getResponseBody(), StandardCharsets.UTF_8));
    }
}
