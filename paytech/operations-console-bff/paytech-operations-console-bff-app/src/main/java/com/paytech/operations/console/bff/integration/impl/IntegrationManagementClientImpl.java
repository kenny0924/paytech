package com.paytech.operations.console.bff.integration.impl;

import org.springframework.stereotype.Component;

import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.ClientApiContractConverter;
import com.paytech.operations.console.bff.integration.ExternalApiContractConverter;
import com.paytech.operations.console.bff.integration.IntegrationManagementClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.externalapi.request.CreateIntegrationRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.UpdateIntegrationRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.IntegrationSummary;
import com.paytech.channel.connector.api.externalapi.IntegrationManagementApi;

import lombok.AllArgsConstructor;

/**
 * 接入方管理转发：逐字段转换请求与响应，检查返回的编码与请求一致。
 *
 * <p>422 只保留属于本次请求体的字段路径，说明统一为安全文案；具体问题由校验接口返回。
 */
@Component
@AllArgsConstructor
public class IntegrationManagementClientImpl implements IntegrationManagementClient {

    private final IntegrationManagementApi api;

    /** 分页查询接入方，按编码稳定排序。 */
    @Override
    public PagingResponseBody<IntegrationSummary> integrations(
        Integer page,
        Integer size,
        String keyword) {
        var result = api.integrations(page, size, keyword);
        return ClientApiContractConverter.page(result, page, size, ExternalApiContractConverter::toBff, IntegrationSummary::getIntegrationCode);
    }

    /** 读取接入方详情，含出站地址的引用方与下级对象数量。 */
    @Override
    public IntegrationResponseBody integration(
        String integrationCode) {
        var mapped = ExternalApiContractConverter.toBff(api.integration(integrationCode));
        ClientApiContractConverter.sameIdentity(integrationCode, mapped.getIntegrationCode());
        return mapped;
    }

    /** 创建接入方及其出站地址。 */
    @Override
    public ExternalApiMutationResponseBody createIntegration(
        OperatorContext context,
        CreateIntegrationRequestBody request) {
        try {
            var mapped = ExternalApiContractConverter.toBff(api.createIntegration(context.accountId(), ExternalApiContractConverter.toOwner(request)));
            ClientApiContractConverter.sameIdentity(request.getIntegrationCode(), mapped.getObjectCode());
            return mapped;
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }

    /** 按当前版本修改名称、说明与出站地址。 */
    @Override
    public ExternalApiMutationResponseBody updateIntegration(
        OperatorContext context,
        String integrationCode,
        UpdateIntegrationRequestBody request) {
        try {
            var mapped = ExternalApiContractConverter.toBff(api.updateIntegration(context.accountId(), integrationCode, ExternalApiContractConverter.toOwner(request)));
            ClientApiContractConverter.sameIdentity(integrationCode, mapped.getObjectCode());
            return mapped;
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }

    /** 按当前版本物理删除接入方。 */
    @Override
    public ExternalApiMutationResponseBody deleteIntegration(
        OperatorContext context,
        String integrationCode,
        DeleteExternalApiConfigRequestBody request) {
        try {
            var mapped = ExternalApiContractConverter.toBff(api.deleteIntegration(context.accountId(), integrationCode, ExternalApiContractConverter.toOwner(request)));
            ClientApiContractConverter.sameIdentity(integrationCode, mapped.getObjectCode());
            return mapped;
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }
}
