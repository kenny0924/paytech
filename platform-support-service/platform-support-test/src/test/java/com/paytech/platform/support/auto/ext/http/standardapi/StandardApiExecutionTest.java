package com.paytech.platform.support.auto.ext.http.standardapi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.StandardApiExecutionErrorCode;
import com.paytech.platform.support.api.standardapi.StandardApiCommandApi;
import com.paytech.platform.support.api.standardapi.request.ExecuteStandardApiRequestBody;
import com.paytech.platform.support.integration.core.dal.dataobject.Integration;
import com.paytech.platform.support.integration.core.dal.dataobject.StandardApi;
import com.paytech.platform.support.test.base.ExternalApiTestBase;
import com.paytech.platform.support.test.builder.ExternalApiSamples;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * 标准 API 内部执行在第一个节点开始前的 Problem（内部接口第 3.2 节）：标准 API 或实现不存在、 已保存配置未通过运行前校验、input 不满足标准 API 请求字段；均不读取 Vault、不发出渠道请求。
 * 初始化与节点执行见 StandardApiFlowExecutionTest、StandardApiExecutionScenarioTest。
 *
 * @see StandardApiCommandApi#executeStandardApi(String, ExecuteStandardApiRequestBody)
 */
class StandardApiExecutionTest extends ExternalApiTestBase {

    private static final String PAY = "PAY";
    private static final String DEMO = "DEMO";

    private StandardApi pay;
    private Integration demo;

    /** 本类公共：标准 API PAY、接入方 DEMO 及其下单原子 API，DEMO 以样例支付流程实现 PAY。 */
    @BeforeEach
    void setUpClassMocks() {
        pay = externalApi.standardApi(PAY);
        demo = externalApi.integration(DEMO);
        externalApi.createOrderApi(demo);
        externalApi.implementation(demo, pay, ExternalApiSamples.payFlow());
    }

    /** 标准 API 编码不存在：404 STANDARD_API_NOT_FOUND。 */
    @Test
    void test_standardApiNotFound() {
        execute("REFUND", DEMO, validInput()).assertProblem(404, StandardApiExecutionErrorCode.STANDARD_API_NOT_FOUND);
    }

    /** 接入方不存在，或接入方存在但没有实现该标准 API：404 INTEGRATION_IMPLEMENTATION_NOT_FOUND。 */
    @Test
    void test_implementationNotFound() {
        externalApi.integration("OTHER");

        execute(PAY, "UNKNOWN", validInput()).assertProblem(404,
            StandardApiExecutionErrorCode.INTEGRATION_IMPLEMENTATION_NOT_FOUND);
        execute(PAY, "OTHER", validInput()).assertProblem(404,
            StandardApiExecutionErrorCode.INTEGRATION_IMPLEMENTATION_NOT_FOUND);
    }

    /** 绕过管理接口写坏的流程或原子 API 定义，运行前校验拒绝：500 INTEGRATION_CONFIG_INVALID。 */
    @Test
    void test_configInvalid() {
        externalApi.implementation(externalApi.integration("BROKEN_FLOW"), pay,
            ExternalApiSamples.object("{\"timeoutMs\":0}"));
        Integration brokenApi = externalApi.integration("BROKEN_API");
        var definition = ExternalApiSamples.createOrderDefinition();
        definition.remove("transport");
        externalApi.integrationApi(brokenApi, ExternalApiSamples.CREATE_ORDER, definition);
        externalApi.implementation(brokenApi, pay, ExternalApiSamples.payFlow());

        execute(PAY, "BROKEN_FLOW", validInput()).assertProblem(500,
            StandardApiExecutionErrorCode.INTEGRATION_CONFIG_INVALID);
        execute(PAY, "BROKEN_API", validInput()).assertProblem(500,
            StandardApiExecutionErrorCode.INTEGRATION_CONFIG_INVALID);
        assertAll(() -> assertTrue(channel.requests().isEmpty()),
            () -> assertEquals(0, channelAccountVault.revealCount()));
    }

    /** input 不满足标准 API 请求字段：422 VALIDATION_ERROR，errors[] 指向 input 下的具体位置。 */
    @Test
    void test_invalidInput() {
        assertFields(List.of("input.paymentContext.unknown"), execute(PAY, DEMO, ExternalApiSamples.object("""
            {"paymentContext":{"attemptId":"1","amount":"10.00","vaultToken":"t","unknown":"x"}}""")));
        assertFields(List.of("input.paymentContext.amount"), execute(PAY, DEMO, ExternalApiSamples.object("""
            {"paymentContext":{"attemptId":"1","vaultToken":"t"}}""")));
        assertFields(List.of("input.paymentContext.amount"), execute(PAY, DEMO, ExternalApiSamples.object("""
            {"paymentContext":{"attemptId":"1","amount":10,"vaultToken":"t"}}""")));
        assertFields(List.of("input"), execute(PAY, DEMO, JsonNodeFactory.instance.arrayNode()));
        assertAll(() -> assertTrue(channel.requests().isEmpty()),
            () -> assertEquals(0, channelAccountVault.revealCount()));
    }

    /** 请求体缺少接入方编码或 input：422 VALIDATION_ERROR，不加载配置。 */
    @Test
    void test_invalidBody() {
        assertFields(List.of("integrationCode"), execute(PAY, null, validInput()));
        assertFields(List.of("input"), execute(PAY, DEMO, null));
    }

    private PlatformResponseChecker execute(String standardApiCode, String integrationCode, JsonNode input) {
        return anonymous(HttpMethod.POST, "/standard-apis/v1/" + standardApiCode + "/execute",
            new ExecuteStandardApiRequestBody(integrationCode, input));
    }

    /** 满足样例标准 API 请求字段的输入。 */
    private static JsonNode validInput() {
        return ExternalApiSamples.object("""
            {"paymentContext":{"attemptId":"301000000000000001","amount":"10.00","vaultToken":"vault-token"}}""");
    }

    /** 422 VALIDATION_ERROR 且字段错误位置与预期一致。 */
    private static void assertFields(List<String> fields, PlatformResponseChecker checker) {
        JsonNode errors = checker.assertProblem(422, CommonErrorCode.VALIDATION_ERROR).json().path("errors");
        assertEquals(fields,
            StreamSupport.stream(errors.spliterator(), false).map(error -> error.path("field").asString()).toList());
    }
}
