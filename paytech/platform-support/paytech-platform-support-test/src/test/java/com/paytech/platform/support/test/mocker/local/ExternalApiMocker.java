package com.paytech.platform.support.test.mocker.local;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.platform.support.api.enums.ApiFieldDataType;
import com.paytech.platform.support.api.enums.ApiFieldLocation;
import com.paytech.platform.support.api.enums.ScriptExecutionStage;
import com.paytech.platform.support.api.externalapi.request.ApiFieldBody;
import com.paytech.platform.support.integration.core.dal.dataobject.*;
import com.paytech.platform.support.integration.core.enums.ApiFieldDirection;
import com.paytech.platform.support.integration.domain.EndpointDefinition;
import com.paytech.platform.support.integration.domain.ExternalApiConfigurationCodec;
import com.paytech.platform.support.integration.domain.ParameterDeclaration;
import com.paytech.platform.support.integration.domainservice.repository.*;
import com.paytech.platform.support.test.builder.ExternalApiSamples;
import com.paytech.platform.support.test.builder.OperatorBuilder;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * 外部 API 配置的本地前置数据：经生产 Repository 直接插入已保存形态的行，版本为 1。
 *
 * <p>只做 Arrange，不调用被测管理接口；六张表由 {@link PlatformDataCleaner} 整表回收，本组件不持有可变状态。
 */
@TestComponent
@RequiredArgsConstructor
public class ExternalApiMocker {

    private static final long CREATOR = 900_000_000_000_000_001L;

    private final OperationsMocker operations;
    private final ExternalApiConfigurationCodec codec;
    private final ApiFieldRepository fields;
    private final StandardApiRepository standardApis;
    private final IntegrationRepository integrations;
    private final IntegrationApiRepository integrationApis;
    private final IntegrationScriptRepository scripts;
    private final IntegrationImplementationRepository implementations;

    /** 使用样例字段的标准 API。 */
    public StandardApi standardApi(String code) {
        return standardApi(code, ExternalApiSamples.standardRequestFields(), ExternalApiSamples.standardResponseFields());
    }

    /** 指定字段的标准 API。 */
    public StandardApi standardApi(String code, List<ApiFieldBody> requestFields, List<ApiFieldBody> responseFields) {
        Instant now = operations.now();
        var row = new StandardApi(OperatorBuilder.nextId(), code, code + " 名称", null, 1, CREATOR, CREATOR, now, now);
        standardApis.insert(row);
        insertFields(row.getStandardApiId(), ApiFieldDirection.STANDARD_API_REQUEST, requestFields, now);
        insertFields(row.getStandardApiId(), ApiFieldDirection.STANDARD_API_RESPONSE, responseFields, now);
        return row;
    }

    /** 使用样例出站地址的接入方。 */
    public Integration integration(String code) {
        return integration(code, ExternalApiSamples.endpoints().getFirst().getBaseUrl());
    }

    /** 出站地址 DEMO_GATEWAY 指向指定基础地址的接入方，执行测试用它指向本机 Mock 渠道。 */
    public Integration integration(String code, String baseUrl) {
        return integration(code, baseUrl, false);
    }

    /** 出站地址 DEMO_GATEWAY 指向指定基础地址并标记是否沙箱的接入方，沙箱验证测试使用。 */
    public Integration integration(String code, String baseUrl, boolean sandbox) {
        Instant now = operations.now();
        var endpoints = List.of(new EndpointDefinition(ExternalApiSamples.ENDPOINT, baseUrl, sandbox));
        var row = new Integration(OperatorBuilder.nextId(), code, code + " 渠道", null, codec.endpointsJson(endpoints),
            1, CREATOR, CREATOR, now, now);
        integrations.insert(row);
        return row;
    }

    /** 使用样例字段与定义的下单原子 API。 */
    public IntegrationApi createOrderApi(Integration integration) {
        return integrationApi(integration, ExternalApiSamples.CREATE_ORDER, ExternalApiSamples.createOrderDefinition());
    }

    /** 使用样例字段、指定定义的原子 API。 */
    public IntegrationApi integrationApi(Integration integration, String code, JsonNode definition) {
        return integrationApi(integration, code, definition, ExternalApiSamples.createOrderRequestFields(),
            ExternalApiSamples.createOrderResponseFields());
    }

    /** 指定字段与定义的原子 API。 */
    public IntegrationApi integrationApi(Integration integration, String code, JsonNode definition,
        List<ApiFieldBody> requestFields, List<ApiFieldBody> responseFields) {
        Instant now = operations.now();
        var row = new IntegrationApi(OperatorBuilder.nextId(), integration.getIntegrationId(), code, code + " 名称",
            null, ExternalApiSamples.text(definition), 1, CREATOR, CREATOR, now, now);
        integrationApis.insert(row);
        insertFields(row.getIntegrationApiId(), ApiFieldDirection.INTEGRATION_API_REQUEST, requestFields, now);
        insertFields(row.getIntegrationApiId(), ApiFieldDirection.INTEGRATION_API_RESPONSE, responseFields, now);
        return row;
    }

    /** 使用样例参数与源码的签名脚本。 */
    public IntegrationScript signScript(Integration integration, ScriptExecutionStage stage) {
        var parameters = ExternalApiSamples.signParameters().stream()
            .map(parameter -> new ParameterDeclaration(parameter.getName(), parameter.getDataType(),
                parameter.getRequired())).toList();
        return script(integration, ExternalApiSamples.SIGN_SCRIPT, stage, parameters, ExternalApiSamples.signSource());
    }

    /** 指定编码、阶段、参数与源码的公共脚本。 */
    public IntegrationScript script(Integration integration, String code, ScriptExecutionStage stage,
        List<ParameterDeclaration> parameters, String sourceCode) {
        Instant now = operations.now();
        var row = new IntegrationScript(OperatorBuilder.nextId(), integration.getIntegrationId(), code, "请求签名", stage,
            codec.parametersJson(parameters), sourceCode, null, 1, CREATOR, CREATOR, now, now);
        scripts.insert(row);
        return row;
    }

    /** 指定流程的标准 API 实现。 */
    public IntegrationImplementation implementation(Integration integration, StandardApi standardApi, JsonNode flow) {
        Instant now = operations.now();
        var row = new IntegrationImplementation(OperatorBuilder.nextId(), integration.getIntegrationId(),
            standardApi.getStandardApiId(), ExternalApiSamples.text(flow), 1, CREATOR, CREATOR, now, now);
        implementations.insert(row);
        return row;
    }

    /** 按数组顺序写入字段，顺序号从 1 开始，与接口保存一致。 */
    private void insertFields(long apiId, ApiFieldDirection direction, List<ApiFieldBody> bodies, Instant now) {
        var rows = new ArrayList<ApiField>();
        for (int index = 0; index < bodies.size(); index++) {
            ApiFieldBody body = bodies.get(index);
            rows.add(new ApiField(OperatorBuilder.nextId(), apiId, direction,
                ApiFieldLocation.fromCode(body.getFieldLocation()), body.getFieldPath(), body.getFieldName(),
                ApiFieldDataType.fromCode(body.getDataType()), body.getRequired(), index + 1, body.getDescription(),
                now, now));
        }
        fields.insertAll(rows);
    }
}
