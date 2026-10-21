package com.paytech.channel.connector.auto.standardapi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.channel.connector.protocol.core.dal.dataobject.Integration;
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.core.enums.StandardApiNotSentCode;
import com.paytech.channel.connector.protocol.service.processor.ExecuteStandardApiProcessor;
import com.paytech.channel.connector.protocol.service.processor.request.ExecuteStandardApiRequest;
import com.paytech.channel.connector.test.base.ExternalApiTestBase;
import com.paytech.channel.connector.test.builder.ExternalApiSamples;
import com.paytech.foundation.common.error.InvalidParameterException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * 标准 API 进程内执行在第一个节点开始前的未发出失败（原 Platform 内部接口第 3.2 节的 NOT_SENT Problem 集合）
 * <li>标准 API 或实现不存在、已保存配置未通过运行前校验、input 不满足标准 API 请求字段；均不读取 Vault、不发出渠道请求。</li>
 * <li>原 HTTP 请求体缺字段的 422 改为 Processor 基础字段约束的参数异常。</li>
 * <li>初始化与节点执行见 StandardApiFlowExecutionTest、StandardApiExecutionScenarioTest。</li>
 *
 * @see ExecuteStandardApiProcessor#process(ExecuteStandardApiRequest)
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

    /** 标准 API 编码不存在：STANDARD_API_NOT_FOUND。 */
    @Test
    void test_standardApiNotFound() {
        assertNotSent(StandardApiNotSentCode.STANDARD_API_NOT_FOUND, () -> execute("REFUND", DEMO, validInput()));
    }

    /** 接入方不存在，或接入方存在但没有实现该标准 API：INTEGRATION_IMPLEMENTATION_NOT_FOUND。 */
    @Test
    void test_implementationNotFound() {
        externalApi.integration("OTHER");

        assertNotSent(StandardApiNotSentCode.INTEGRATION_IMPLEMENTATION_NOT_FOUND,
            () -> execute(PAY, "UNKNOWN", validInput()));
        assertNotSent(StandardApiNotSentCode.INTEGRATION_IMPLEMENTATION_NOT_FOUND,
            () -> execute(PAY, "OTHER", validInput()));
    }

    /** 绕过管理接口写坏的流程或原子 API 定义，运行前校验拒绝：INTEGRATION_CONFIG_INVALID。 */
    @Test
    void test_configInvalid() {
        externalApi.implementation(externalApi.integration("BROKEN_FLOW"), pay,
            ExternalApiSamples.object("{\"timeoutMs\":0}"));
        Integration brokenApi = externalApi.integration("BROKEN_API");
        var definition = ExternalApiSamples.createOrderDefinition();
        definition.remove("transport");
        externalApi.integrationApi(brokenApi, ExternalApiSamples.CREATE_ORDER, definition);
        externalApi.implementation(brokenApi, pay, ExternalApiSamples.payFlow());

        assertNotSent(StandardApiNotSentCode.INTEGRATION_CONFIG_INVALID,
            () -> execute(PAY, "BROKEN_FLOW", validInput()));
        assertNotSent(StandardApiNotSentCode.INTEGRATION_CONFIG_INVALID,
            () -> execute(PAY, "BROKEN_API", validInput()));
        assertAll(() -> assertTrue(channel.requests().isEmpty()), () -> assertEquals(0, vault.revealCount()));
    }

    /** input 不满足标准 API 请求字段：VALIDATION_ERROR，字段位置指向 input 下的具体位置。 */
    @Test
    void test_invalidInput() {
        assertFields(List.of("input.paymentContext.unknown"), ExternalApiSamples.object("""
            {"paymentContext":{"attemptId":"1","amount":"10.00","vaultToken":"t","unknown":"x"}}"""));
        assertFields(List.of("input.paymentContext.amount"), ExternalApiSamples.object("""
            {"paymentContext":{"attemptId":"1","vaultToken":"t"}}"""));
        assertFields(List.of("input.paymentContext.amount"), ExternalApiSamples.object("""
            {"paymentContext":{"attemptId":"1","amount":10,"vaultToken":"t"}}"""));
        assertFields(List.of("input"), JsonNodeFactory.instance.arrayNode());
        assertAll(() -> assertTrue(channel.requests().isEmpty()), () -> assertEquals(0, vault.revealCount()));
    }

    /** 请求缺少接入方编码或 input：Processor 基础字段约束拒绝，不加载配置。 */
    @Test
    void test_invalidRequest() {
        var missingIntegration = assertThrows(InvalidParameterException.class, () -> execute(PAY, null, validInput()));
        var missingInput = assertThrows(InvalidParameterException.class, () -> execute(PAY, DEMO, null));

        assertAll(() -> assertTrue(missingIntegration.getMessage().contains("integrationCode"),
                missingIntegration.getMessage()),
            () -> assertTrue(missingInput.getMessage().contains("input"), missingInput.getMessage()),
            () -> assertTrue(channel.requests().isEmpty()));
    }

    /** 满足样例标准 API 请求字段的输入。 */
    private static JsonNode validInput() {
        return ExternalApiSamples.object("""
            {"paymentContext":{"attemptId":"301000000000000001","amount":"10.00","vaultToken":"vault-token"}}""");
    }

    /** VALIDATION_ERROR 且字段位置与预期一致。 */
    private void assertFields(List<String> fields, JsonNode input) {
        var failure = assertNotSent(StandardApiNotSentCode.VALIDATION_ERROR, () -> execute(PAY, DEMO, input));
        assertEquals(fields, failure.getFields());
    }
}
