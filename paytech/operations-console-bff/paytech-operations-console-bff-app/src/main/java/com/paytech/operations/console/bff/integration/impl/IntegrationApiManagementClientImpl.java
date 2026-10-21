package com.paytech.operations.console.bff.integration.impl;

import org.springframework.stereotype.Component;

import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.ClientApiContractConverter;
import com.paytech.operations.console.bff.integration.ExternalApiContractConverter;
import com.paytech.operations.console.bff.integration.IntegrationApiManagementClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.externalapi.request.CreateIntegrationApiRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.UpdateIntegrationApiRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.ValidateIntegrationApiRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationApiResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationApiSummary;
import com.paytech.operations.console.bff.service.model.externalapi.response.ValidationResponseBody;
import com.paytech.channel.connector.api.externalapi.IntegrationApiManagementApi;

import lombok.AllArgsConstructor;

/**
 * 原子 API管理转发：逐字段转换请求与响应，检查返回的编码与请求一致。
 *
 * <p>422 只保留属于本次请求体的字段路径，说明统一为安全文案；具体问题由校验接口返回。
 */
@Component
@AllArgsConstructor
public class IntegrationApiManagementClientImpl implements IntegrationApiManagementClient {

    private final IntegrationApiManagementApi api;

    /** 分页查询接入方下的原子 API，按编码稳定排序。 */
    @Override
    public PagingResponseBody<IntegrationApiSummary> listIntegrationApis(
        String integrationCode,
        Integer page,
        Integer size,
        String keyword) {
        var result = api.listIntegrationApis(integrationCode, page, size, keyword);
        return ClientApiContractConverter.page(result, page, size, ExternalApiContractConverter::toBff, IntegrationApiSummary::getApiCode);
    }

    /** 读取原子 API详情，含引用方。 */
    @Override
    public IntegrationApiResponseBody getIntegrationApi(
        String integrationCode,
        String apiCode) {
        var mapped = ExternalApiContractConverter.toBff(api.getIntegrationApi(integrationCode, apiCode));
        ClientApiContractConverter.sameIdentity(apiCode, mapped.getApiCode());
        return mapped;
    }

    /** 创建原子 API。 */
    @Override
    public ExternalApiMutationResponseBody createIntegrationApi(
        OperatorContext context,
        String integrationCode,
        CreateIntegrationApiRequestBody request) {
        try {
            var mapped = ExternalApiContractConverter.toBff(api.createIntegrationApi(context.accountId(), integrationCode, ExternalApiContractConverter.toOwner(request)));
            ClientApiContractConverter.sameIdentity(integrationCode + "/" + request.getApiCode(), mapped.getObjectCode());
            return mapped;
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }

    /** 按当前版本整体修改原子 API。 */
    @Override
    public ExternalApiMutationResponseBody updateIntegrationApi(
        OperatorContext context,
        String integrationCode,
        String apiCode,
        UpdateIntegrationApiRequestBody request) {
        try {
            var mapped = ExternalApiContractConverter.toBff(api.updateIntegrationApi(context.accountId(), integrationCode, apiCode, ExternalApiContractConverter.toOwner(request)));
            ClientApiContractConverter.sameIdentity(integrationCode + "/" + apiCode, mapped.getObjectCode());
            return mapped;
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }

    /** 按当前版本物理删除原子 API。 */
    @Override
    public ExternalApiMutationResponseBody deleteIntegrationApi(
        OperatorContext context,
        String integrationCode,
        String apiCode,
        DeleteExternalApiConfigRequestBody request) {
        try {
            var mapped = ExternalApiContractConverter.toBff(api.deleteIntegrationApi(context.accountId(), integrationCode, apiCode, ExternalApiContractConverter.toOwner(request)));
            ClientApiContractConverter.sameIdentity(integrationCode + "/" + apiCode, mapped.getObjectCode());
            return mapped;
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }

    /** 只做静态校验，不保存；问题通过 issues 返回而不是错误响应。 */
    @Override
    public ValidationResponseBody validateIntegrationApi(
        String integrationCode,
        ValidateIntegrationApiRequestBody request) {
        try {
            var mapped = ExternalApiContractConverter.toBff(api.validateIntegrationApi(integrationCode, ExternalApiContractConverter.toOwner(request)));
            return mapped;
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }
}
