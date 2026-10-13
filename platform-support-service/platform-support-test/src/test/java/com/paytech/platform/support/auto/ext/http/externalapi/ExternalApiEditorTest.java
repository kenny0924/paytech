package com.paytech.platform.support.auto.ext.http.externalapi;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.ApiFieldLocation;
import com.paytech.platform.support.integration.domain.RegisteredFunctionCatalog;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.api.externalapi.ExternalApiEditorApi;
import com.paytech.platform.support.api.externalapi.response.*;
import com.paytech.platform.support.test.base.ExternalApiTestBase;
import com.paytech.platform.support.test.builder.ExternalApiSamples;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;

/**
 * 编辑器目录：流程编辑上下文返回标准 API 字段与本接入方全部原子 API 合同摘要；能力目录返回枚举、上限与注册函数。
 *
 * @see ExternalApiEditorApi#getFlowEditorContext(String, String, String, String)
 * @see ExternalApiEditorApi#getEditorCapabilities(String, String)
 */
class ExternalApiEditorTest extends ExternalApiTestBase {

    private Operator reader;

    /** 本类公共：只读人员即可使用两个目录。 */
    @BeforeEach
    void setUpClassMocks() {
        reader = reader();
    }

    /** 上下文包含标准 API 字段、原子 API 的请求字段（固定值标记）、调用参数与分支输出。 */
    @Test
    void test_flowEditorContext() {
        externalApi.standardApi("PAY");
        var demo = externalApi.integration("DEMO");
        externalApi.createOrderApi(demo);
        externalApi.integration("OTHER");

        var context = get(BASE + "/integrations/DEMO/flow-editor-context?standardApiCode=PAY", reader).assertOk()
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

        get(BASE + "/integrations/DEMO/flow-editor-context?standardApiCode=PAY", reader).assertProblem(404, CommonErrorCode.NOT_FOUND);
        get(BASE + "/integrations/NOPE/flow-editor-context?standardApiCode=PAY", reader).assertProblem(404, CommonErrorCode.NOT_FOUND);
    }

    /** 能力目录的枚举与上限和合同一致，注册函数区分初始化、纯函数与只在公共脚本中使用的脚本函数。 */
    @Test
    void test_editorCapabilities() {
        var capabilities =
            get(BASE + "/editor-capabilities", reader).assertOk().as(EditorCapabilitiesResponseBody.class);

        var functions = capabilities.getFunctions();
        assertAll(
            () -> assertEquals(Arrays.stream(ApiFieldLocation.values()).map(ApiFieldLocation::getCode)
                .collect(Collectors.toSet()), Set.copyOf(capabilities.getEnums().get("fieldLocations"))),
            () -> assertEquals(List.of("DELETE", "GET", "PATCH", "POST", "PUT"),
                capabilities.getEnums().get("methods")),
            () -> assertEquals(List.of("SUCCESS", "FAIL", "ERROR"), capabilities.getEnums().get("returnStatuses")),
            () -> assertEquals(65_536, capabilities.getLimits().get("jsonBytes")),
            () -> assertEquals(32_768, capabilities.getLimits().get("scriptSourceBytes")),
            () -> assertEquals(16, capabilities.getLimits().get("endpointsPerIntegration")),
            () -> assertEquals(60_000, capabilities.getLimits().get("flowTimeoutMs")),
            () -> assertEquals(16, capabilities.getLimits().get("flowNodes")),
            () -> assertEquals(
                List.of("CHANNEL_ACCOUNT_CONFIG:INITIALIZER:[FLOW_INITIALIZER]",
                    "MONEY_MINOR_UNITS:PURE:[FLOW_NODE, FLOW_RETURN, API_OUTPUT]"),
                functions.stream().filter(item -> !RegisteredFunctionCatalog.SCRIPT.equals(item.getKind()))
                    .map(ExternalApiEditorTest::function).sorted().toList()),
            () -> assertEquals(RegisteredFunctionCatalog.codes(RegisteredFunctionCatalog.SCRIPT),
                functions.stream().filter(item -> RegisteredFunctionCatalog.SCRIPT.equals(item.getKind()))
                    .peek(item -> assertEquals(List.of("INTEGRATION_SCRIPT"), item.getStages()))
                    .map(item -> item.getFunctionCode()).collect(Collectors.toSet())));
    }

    /** 没有外部 API 权限的人员不能读取目录。 */
    @Test
    void test_requiresPermission() {
        long role = operationsMocker.customRole("NO_EXTERNAL_API", "OPERATIONS_ROLE_READ");
        Operator other = operationsMocker.operator(OperatorBuilder.administrator().roles(role));

        get(BASE + "/editor-capabilities", other).assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
    }

    private static String fixed(FlowRequestFieldDetail field) {
        return field.getFieldPath() + ":" + field.isFixed();
    }

    private static String function(RegisteredFunctionDetail function) {
        return function.getFunctionCode() + ":" + function.getKind() + ":" + function.getStages();
    }
}
