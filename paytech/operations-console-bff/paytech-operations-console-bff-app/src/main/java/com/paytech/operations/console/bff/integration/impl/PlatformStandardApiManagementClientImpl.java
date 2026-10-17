package com.paytech.operations.console.bff.integration.impl;

import org.springframework.stereotype.Component;

import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.ClientApiContractConverter;
import com.paytech.operations.console.bff.integration.ExternalApiContractConverter;
import com.paytech.operations.console.bff.integration.PlatformStandardApiManagementClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.externalapi.request.CreateStandardApiRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.DeleteExternalApiConfigRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.request.UpdateStandardApiRequestBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.ExternalApiMutationResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.StandardApiResponseBody;
import com.paytech.operations.console.bff.service.model.externalapi.response.StandardApiSummary;
import com.paytech.platform.support.api.externalapi.StandardApiManagementApi;

import lombok.AllArgsConstructor;

/**
 * 标准 API管理转发：逐字段转换请求与响应，检查返回的编码与请求一致。
 *
 * <p>422 只保留属于本次请求体的字段路径，说明统一为安全文案；具体问题由校验接口返回。
 */
@Component
@AllArgsConstructor
public class PlatformStandardApiManagementClientImpl implements PlatformStandardApiManagementClient {

    private final StandardApiManagementApi api;

    /** 分页查询标准 API，按编码稳定排序。 */
    @Override
    public PagingResponseBody<StandardApiSummary> standardApis(
        OperatorContext context,
        Integer page,
        Integer size,
        String keyword) {
        var result = api.standardApis(context.accountId(), context.sessionId(), page, size, keyword);
        return ClientApiContractConverter.page(result, page, size, ExternalApiContractConverter::toBff, StandardApiSummary::getStandardApiCode);
    }

    /** 读取标准 API 详情，含字段与实现方。 */
    @Override
    public StandardApiResponseBody standardApi(
        OperatorContext context,
        String standardApiCode) {
        var mapped = ExternalApiContractConverter.toBff(api.standardApi(context.accountId(), context.sessionId(), standardApiCode));
        ClientApiContractConverter.sameIdentity(standardApiCode, mapped.getStandardApiCode());
        return mapped;
    }

    /** 创建标准 API 及其字段。 */
    @Override
    public ExternalApiMutationResponseBody createStandardApi(
        OperatorContext context,
        CreateStandardApiRequestBody request) {
        try {
            var mapped = ExternalApiContractConverter.toBff(api.createStandardApi(context.accountId(), context.sessionId(), ExternalApiContractConverter.toPlatform(request)));
            ClientApiContractConverter.sameIdentity(request.getStandardApiCode(), mapped.getObjectCode());
            return mapped;
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }

    /** 按当前版本整体修改名称、说明与字段。 */
    @Override
    public ExternalApiMutationResponseBody updateStandardApi(
        OperatorContext context,
        String standardApiCode,
        UpdateStandardApiRequestBody request) {
        try {
            var mapped = ExternalApiContractConverter.toBff(api.updateStandardApi(context.accountId(), context.sessionId(), standardApiCode, ExternalApiContractConverter.toPlatform(request)));
            ClientApiContractConverter.sameIdentity(standardApiCode, mapped.getObjectCode());
            return mapped;
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }

    /** 按当前版本物理删除标准 API 及其字段。 */
    @Override
    public ExternalApiMutationResponseBody deleteStandardApi(
        OperatorContext context,
        String standardApiCode,
        DeleteExternalApiConfigRequestBody request) {
        try {
            var mapped = ExternalApiContractConverter.toBff(api.deleteStandardApi(context.accountId(), context.sessionId(), standardApiCode, ExternalApiContractConverter.toPlatform(request)));
            ClientApiContractConverter.sameIdentity(standardApiCode, mapped.getObjectCode());
            return mapped;
        } catch (HttpRequestValidationException failure) {
            throw ExternalApiValidationConverter.local(request, failure);
        }
    }
}
