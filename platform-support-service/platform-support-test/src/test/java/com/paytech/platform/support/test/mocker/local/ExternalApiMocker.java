package com.paytech.platform.support.test.mocker.local;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.platform.support.api.enums.ApiFieldDataType;
import com.paytech.platform.support.api.enums.ApiFieldLocation;
import com.paytech.platform.support.api.enums.ScriptExecutionStage;
import com.paytech.platform.support.api.externalapi.request.ApiFieldBody;
import com.paytech.platform.support.integration.core.dal.dataobject.ApiField;
import com.paytech.platform.support.integration.core.dal.dataobject.Integration;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationApi;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationImplementation;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationScript;
import com.paytech.platform.support.integration.core.dal.dataobject.StandardApi;
import com.paytech.platform.support.integration.core.enums.ApiFieldDirection;
import com.paytech.platform.support.integration.domain.EndpointDefinition;
import com.paytech.platform.support.integration.domain.ExternalApiConfigurationCodec;
import com.paytech.platform.support.integration.domain.ParameterDeclaration;
import com.paytech.platform.support.integration.domainservice.repository.ApiFieldRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationApiRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationImplementationRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationRepository;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationScriptRepository;
import com.paytech.platform.support.integration.domainservice.repository.StandardApiRepository;
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
        Instant now = operations.now();
        var endpoints = ExternalApiSamples.endpoints().stream()
            .map(endpoint -> new EndpointDefinition(endpoint.getEndpointCode(), endpoint.getBaseUrl())).toList();
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
        Instant now = operations.now();
        var row = new IntegrationApi(OperatorBuilder.nextId(), integration.getIntegrationId(), code, code + " 名称",
            null, ExternalApiSamples.text(definition), 1, CREATOR, CREATOR, now, now);
        integrationApis.insert(row);
        insertFields(row.getIntegrationApiId(), ApiFieldDirection.INTEGRATION_API_REQUEST,
            ExternalApiSamples.createOrderRequestFields(), now);
        insertFields(row.getIntegrationApiId(), ApiFieldDirection.INTEGRATION_API_RESPONSE,
            ExternalApiSamples.createOrderResponseFields(), now);
        return row;
    }

    /** 使用样例参数与源码的签名脚本。 */
    public IntegrationScript signScript(Integration integration, ScriptExecutionStage stage) {
        Instant now = operations.now();
        var parameters = ExternalApiSamples.signParameters().stream()
            .map(parameter -> new ParameterDeclaration(parameter.getName(), parameter.getDataType(),
                parameter.getRequired())).toList();
        var row = new IntegrationScript(OperatorBuilder.nextId(), integration.getIntegrationId(),
            ExternalApiSamples.SIGN_SCRIPT, "请求签名", stage, codec.parametersJson(parameters),
            ExternalApiSamples.signSource(), null, 1, CREATOR, CREATOR, now, now);
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
