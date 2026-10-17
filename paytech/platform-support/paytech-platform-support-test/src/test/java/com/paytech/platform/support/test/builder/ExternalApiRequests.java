package com.paytech.platform.support.test.builder;

import java.util.List;

import com.paytech.platform.support.api.enums.ScriptExecutionStage;
import com.paytech.platform.support.api.externalapi.request.ApiFieldBody;
import com.paytech.platform.support.api.externalapi.request.CreateImplementationRequestBody;
import com.paytech.platform.support.api.externalapi.request.CreateIntegrationApiRequestBody;
import com.paytech.platform.support.api.externalapi.request.CreateIntegrationRequestBody;
import com.paytech.platform.support.api.externalapi.request.CreateIntegrationScriptRequestBody;
import com.paytech.platform.support.api.externalapi.request.CreateStandardApiRequestBody;
import com.paytech.platform.support.api.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.platform.support.api.externalapi.request.IntegrationEndpointBody;
import com.paytech.platform.support.api.externalapi.request.ScriptParameterBody;
import com.paytech.platform.support.api.externalapi.request.UpdateImplementationRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateIntegrationApiRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateIntegrationRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateIntegrationScriptRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateStandardApiRequestBody;
import com.paytech.platform.support.api.externalapi.request.ValidateImplementationRequestBody;
import com.paytech.platform.support.api.externalapi.request.ValidateIntegrationApiRequestBody;
import com.paytech.platform.support.api.externalapi.request.ValidateIntegrationScriptRequestBody;

import tools.jackson.databind.JsonNode;

/**
 * 外部 API 管理接口的请求体：默认取 {@link ExternalApiSamples} 的合法样例，用例只替换与场景相关的部分。
 *
 * <p>不修复负例输入；说明字段统一为 null，原因统一为固定文本。
 */
public final class ExternalApiRequests {

    /** 写请求统一的变更原因。 */
    public static final String REASON = "测试配置";

    private ExternalApiRequests() {
    }

    /** 样例字段的标准 API 创建请求。 */
    public static CreateStandardApiRequestBody createStandardApi(String code) {
        return createStandardApi(code, ExternalApiSamples.standardRequestFields(),
            ExternalApiSamples.standardResponseFields());
    }

    public static CreateStandardApiRequestBody createStandardApi(String code, List<ApiFieldBody> requestFields,
        List<ApiFieldBody> responseFields) {
        return new CreateStandardApiRequestBody(code, code + " 名称", null, requestFields, responseFields, REASON);
    }

    public static UpdateStandardApiRequestBody updateStandardApi(String name, List<ApiFieldBody> requestFields,
        List<ApiFieldBody> responseFields, int expectedVersion) {
        return new UpdateStandardApiRequestBody(name, null, requestFields, responseFields, expectedVersion, REASON);
    }

    /** 样例出站地址的接入方创建请求。 */
    public static CreateIntegrationRequestBody createIntegration(String code) {
        return createIntegration(code, ExternalApiSamples.endpoints());
    }

    public static CreateIntegrationRequestBody createIntegration(String code, List<IntegrationEndpointBody> endpoints) {
        return new CreateIntegrationRequestBody(code, code + " 渠道", null, endpoints, REASON);
    }

    public static UpdateIntegrationRequestBody updateIntegration(String name, List<IntegrationEndpointBody> endpoints,
        int expectedVersion) {
        return new UpdateIntegrationRequestBody(name, null, endpoints, expectedVersion, REASON);
    }

    /** 样例字段、指定定义的原子 API 创建请求。 */
    public static CreateIntegrationApiRequestBody createIntegrationApi(String code, JsonNode definition) {
        return createIntegrationApi(code, ExternalApiSamples.createOrderRequestFields(), definition);
    }

    public static CreateIntegrationApiRequestBody createIntegrationApi(String code, List<ApiFieldBody> requestFields,
        JsonNode definition) {
        return new CreateIntegrationApiRequestBody(code, code + " 名称", null, requestFields,
            ExternalApiSamples.createOrderResponseFields(), definition, REASON);
    }

    public static UpdateIntegrationApiRequestBody updateIntegrationApi(List<ApiFieldBody> requestFields,
        JsonNode definition, int expectedVersion) {
        return new UpdateIntegrationApiRequestBody("下单", null, requestFields,
            ExternalApiSamples.createOrderResponseFields(), definition, expectedVersion, REASON);
    }

    public static ValidateIntegrationApiRequestBody validateIntegrationApi(String code, JsonNode definition) {
        return new ValidateIntegrationApiRequestBody(code, code + " 名称", null,
            ExternalApiSamples.createOrderRequestFields(), ExternalApiSamples.createOrderResponseFields(), definition,
            null);
    }

    /** 样例参数与源码的脚本创建请求。 */
    public static CreateIntegrationScriptRequestBody createScript(String code, ScriptExecutionStage stage) {
        return createScript(code, stage, ExternalApiSamples.signParameters(), ExternalApiSamples.signSource());
    }

    public static CreateIntegrationScriptRequestBody createScript(String code, ScriptExecutionStage stage,
        List<ScriptParameterBody> parameters, String sourceCode) {
        return new CreateIntegrationScriptRequestBody(code, "请求签名", stage.getCode(), parameters, sourceCode, null,
            REASON);
    }

    public static UpdateIntegrationScriptRequestBody updateScript(ScriptExecutionStage stage,
        List<ScriptParameterBody> parameters, int expectedVersion) {
        return new UpdateIntegrationScriptRequestBody("请求签名", stage.getCode(), parameters,
            ExternalApiSamples.signSource(),
            null, expectedVersion, REASON);
    }

    public static ValidateIntegrationScriptRequestBody validateScript(String code, ScriptExecutionStage stage,
        String sourceCode) {
        return new ValidateIntegrationScriptRequestBody(code, "请求签名", stage.getCode(),
            ExternalApiSamples.signParameters(),
            sourceCode, null, null);
    }

    public static CreateImplementationRequestBody createImplementation(String standardApiCode, JsonNode flow) {
        return new CreateImplementationRequestBody(standardApiCode, flow, REASON);
    }

    public static UpdateImplementationRequestBody updateImplementation(JsonNode flow, int expectedVersion) {
        return new UpdateImplementationRequestBody(flow, expectedVersion, REASON);
    }

    public static ValidateImplementationRequestBody validateImplementation(String standardApiCode, JsonNode flow,
        Integer expectedVersion) {
        return new ValidateImplementationRequestBody(standardApiCode, flow, expectedVersion);
    }

    public static DeleteExternalApiConfigRequestBody delete(int expectedVersion) {
        return new DeleteExternalApiConfigRequestBody(expectedVersion, REASON);
    }
}
