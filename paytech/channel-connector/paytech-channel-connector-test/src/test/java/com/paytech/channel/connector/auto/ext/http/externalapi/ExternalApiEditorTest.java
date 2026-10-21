package com.paytech.channel.connector.auto.ext.http.externalapi;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.channel.connector.api.enums.ApiFieldLocation;
import com.paytech.channel.connector.protocol.domain.RegisteredFunctionCatalog;
import com.paytech.channel.connector.api.externalapi.ExternalApiEditorApi;
import com.paytech.channel.connector.api.externalapi.response.*;
import com.paytech.channel.connector.test.base.ExternalApiTestBase;
import com.paytech.channel.connector.test.builder.ExternalApiSamples;

/**
 * 编辑器目录：流程编辑上下文返回标准 API 字段与本接入方全部原子 API 合同摘要；能力目录返回枚举、上限与注册函数
 * <li>两个目录均为查询，不带操作者请求头；原 Platform 的权限拒绝场景随权限复核移出本 Owner（Gateway / BFF 负责）。</li>
 *
 * @see ExternalApiEditorApi#getFlowEditorContext(String, String)
 * @see ExternalApiEditorApi#getEditorCapabilities()
 */
class ExternalApiEditorTest extends ExternalApiTestBase {

    /** 上下文包含标准 API 字段、原子 API 的请求字段（固定值标记）、调用参数与分支输出。 */
    @Test
    void test_flowEditorContext() {
        externalApi.standardApi("PAY");
        var demo = externalApi.integration("DEMO");
        externalApi.createOrderApi(demo);
        externalApi.integration("OTHER");

        var context = get(BASE + "/integrations/DEMO/flow-editor-context?standardApiCode=PAY").assertOk()
            .as(FlowEditorContextResponseBody.class);

        assertEquals(1, context.getApis().size());
        FlowApiContractDetail api = context.getApis().getFirst();
        assertAll(
            () -> assertEquals(
                List.of("paymentContext", "paymentContext.attemptId", "paymentContext.amount",
                    "paymentContext.vaultToken"),
                context.getStandardApi().getRequestFields().stream().map(ApiFieldDetail::getFieldPath).toList()),
            () -> assertEquals(2, context.getStandardApi().getResponseFields().size()),
            () -> assertEquals(ExternalApiSamples.CREATE_ORDER, api.getApiCode()),
            () -> assertEquals(List.of("outletRef:false", "X-Api-Version:true", "orderRef:false", "amount:false"),
                api.getRequestFields().stream().map(ExternalApiEditorTest::fixed).toList()),
            () -> assertEquals(List.of("signingKey:PROTECTED_HANDLE"),
                api.getParams().stream().map(parameter -> parameter.getName() + ":" + parameter.getDataType())
                    .toList()),
            () -> assertEquals(List.of("transactionRef", "state"),
                api.getOutputs().get("SUCCESS").stream().map(ScriptParameterDetail::getName).toList()),
            () -> assertEquals(List.of("errorCode"),
                api.getOutputs().get("FAIL").stream().map(ScriptParameterDetail::getName).toList()));
    }

    /** 标准 API 或接入方不存在时返回 404。 */
    @Test
    void test_flowEditorContextNotFound() {
        externalApi.integration("DEMO");

        get(BASE + "/integrations/DEMO/flow-editor-context?standardApiCode=PAY").assertProblem(404, CommonErrorCode.NOT_FOUND);
        get(BASE + "/integrations/NOPE/flow-editor-context?standardApiCode=PAY").assertProblem(404, CommonErrorCode.NOT_FOUND);
    }

    /** 能力目录的枚举与上限和合同一致，注册函数区分初始化、纯函数与只在公共脚本中使用的脚本函数。 */
    @Test
    void test_editorCapabilities() {
        var capabilities =
            get(BASE + "/editor-capabilities").assertOk().as(EditorCapabilitiesResponseBody.class);

        var functions = capabilities.getFunctions();
        assertAll(
            () -> assertEquals(Arrays.stream(ApiFieldLocation.values()).map(ApiFieldLocation::getCode)
                .collect(Collectors.toSet()), Set.copyOf(capabilities.getEnums().get("fieldLocations"))),
            () -> assertEquals(List.of("DELETE", "GET", "PATCH", "POST", "PUT"),
                capabilities.getEnums().get("methods")),
            () -> assertEquals(List.of("SUCCESS", "FAIL", "ERROR"), capabilities.getEnums().get("returnStatuses")),
            () -> assertEquals(List.of("NUMBER", "STRING"), capabilities.getEnums().get("decimalEncodings")),
            () -> assertEquals(65_536, capabilities.getLimits().get("jsonBytes")),
            () -> assertEquals(32_768, capabilities.getLimits().get("scriptSourceBytes")),
            () -> assertEquals(16, capabilities.getLimits().get("endpointsPerIntegration")),
            () -> assertEquals(60_000, capabilities.getLimits().get("flowTimeoutMs")),
            () -> assertEquals(16, capabilities.getLimits().get("flowNodes")),
            () -> assertEquals(
                List.of("CHANNEL_ACCOUNT_CONFIG:INITIALIZER:[FLOW_INITIALIZER]",
                    "MONEY_MAJOR_UNITS:PURE:[FLOW_NODE, FLOW_RETURN, API_OUTPUT]",
                    "MONEY_MINOR_UNITS:PURE:[FLOW_NODE, FLOW_RETURN, API_OUTPUT]",
                    "PAYMENT_CARD:INITIALIZER:[FLOW_INITIALIZER]"),
                functions.stream().filter(item -> !RegisteredFunctionCatalog.SCRIPT.equals(item.getKind()))
                    .map(ExternalApiEditorTest::function).sorted().toList()),
            () -> assertEquals(RegisteredFunctionCatalog.codes(RegisteredFunctionCatalog.SCRIPT),
                functions.stream().filter(item -> RegisteredFunctionCatalog.SCRIPT.equals(item.getKind()))
                    .peek(item -> assertEquals(List.of("INTEGRATION_SCRIPT"), item.getStages()))
                    .map(item -> item.getFunctionCode()).collect(Collectors.toSet())));
    }

    /** 原子 API 请求字段的“路径:是否固定值”。 */
    private static String fixed(FlowRequestFieldDetail field) {
        return field.getFieldPath() + ":" + field.isFixed();
    }

    /** 注册函数的“编码:类别:可用阶段”。 */
    private static String function(RegisteredFunctionDetail function) {
        return function.getFunctionCode() + ":" + function.getKind() + ":" + function.getStages();
    }
}
