package com.paytech.operations.console.bff.integration.impl;

import org.springframework.stereotype.Component;

import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.operations.console.bff.integration.ClientApiContractConverter;
import com.paytech.operations.console.bff.integration.ExternalApiContractConverter;
import com.paytech.operations.console.bff.integration.IntegrationImplementationManagementClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.externalapi.request.CreateImplementationRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.UpdateImplementationRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.ValidateImplementationRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ImplementationListResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ImplementationResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ValidationResponseBody;
import com.paytech.channel.connector.api.externalapi.IntegrationImplementationManagementApi;

import lombok.AllArgsConstructor;

/**
 * 渠道标准 API 实现管理转发：逐字段转换请求与响应，检查返回的编码与请求一致。
 *
 * <p>422 只保留属于本次请求体的字段路径，说明统一为安全文案；具体问题由校验接口返回。
 */
@Component
@AllArgsConstructor
public class IntegrationImplementationManagementClientImpl implements IntegrationImplementationManagementClient {

    private final IntegrationImplementationManagementApi api;

    /** 查询接入方的全部标准 API 实现，按标准 API 编码排序，不分页。 */
    @Override
    public ImplementationListResponseBody listImplementations(
        String integrationCode) {
        var mapped = ExternalApiContractConverter.toBff(api.listImplementations(integrationCode));
        return mapped;
    }

    /** 读取一个标准 API 实现的流程定义。 */
    @Override
    public ImplementationResponseBody getImplementation(
        String integrationCode,
        String standardApiCode) {
        var mapped = ExternalApiContractConverter.toBff(api.getImplementation(integrationCode, standardApiCode));
        ClientApiContractConverter.sameIdentity(standardApiCode, mapped.getStandardApiCode());
        return mapped;
    }

    /** 创建标准 API 实现。 */
    @Override
    public ExternalApiMutationResponseBody createImplementation(
        OperatorContext context,
        String integrationCode,
        CreateImplementationRequestBody request) {
        try {
            var mapped = ExternalApiContractConverter.toBff(api.createImplementation(context.accountId(), integrationCode, ExternalApiContractConverter.toOwner(request)));
            ClientApiContractConverter.sameIdentity(integrationCode + "/" + request.getStandardApiCode(), mapped.getObjectCode());
            return mapped;
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }

    /** 按当前版本整体替换流程。 */
    @Override
    public ExternalApiMutationResponseBody updateImplementation(
        OperatorContext context,
        String integrationCode,
        String standardApiCode,
        UpdateImplementationRequestBody request) {
        try {
            var mapped = ExternalApiContractConverter.toBff(api.updateImplementation(context.accountId(), integrationCode, standardApiCode, ExternalApiContractConverter.toOwner(request)));
            ClientApiContractConverter.sameIdentity(integrationCode + "/" + standardApiCode, mapped.getObjectCode());
            return mapped;
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }

    /** 按当前版本物理删除实现。 */
    @Override
    public ExternalApiMutationResponseBody deleteImplementation(
        OperatorContext context,
        String integrationCode,
        String standardApiCode,
        DeleteExternalApiConfigRequestBody request) {
        try {
            var mapped = ExternalApiContractConverter.toBff(api.deleteImplementation(context.accountId(), integrationCode, standardApiCode, ExternalApiContractConverter.toOwner(request)));
            ClientApiContractConverter.sameIdentity(integrationCode + "/" + standardApiCode, mapped.getObjectCode());
            return mapped;
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }

    /** 只做流程静态校验，不保存；问题通过 issues 返回而不是错误响应。 */
    @Override
    public ValidationResponseBody validateImplementation(
        String integrationCode,
        ValidateImplementationRequestBody request) {
        try {
            var mapped = ExternalApiContractConverter.toBff(api.validateImplementation(integrationCode, ExternalApiContractConverter.toOwner(request)));
            return mapped;
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }
}
