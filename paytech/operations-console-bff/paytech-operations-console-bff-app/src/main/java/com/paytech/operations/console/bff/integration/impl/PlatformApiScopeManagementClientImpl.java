package com.paytech.operations.console.bff.integration.impl;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.ApiScopeManagementModels.*;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.integration.PlatformApiScopeManagementClient;
import com.paytech.operations.console.bff.integration.ClientApiContractConverter;
import com.paytech.platform.support.api.apiscopemanagement.ApiScopeManagementApi;

/** 保留可信身份和字段错误的 HTTP 转发，请求幂等归 Gateway。 */
@org.springframework.stereotype.Component
@lombok.AllArgsConstructor
public class PlatformApiScopeManagementClientImpl implements PlatformApiScopeManagementClient {
    private final ApiScopeManagementApi management;

    /** 经私网 HTTP 转发当前 Operations 身份，并逐字段投影 Owner 的管理响应。 */
    @Override
    public PagingResponseBody<ApiScopeSummary> scopes(OperatorContext context, Integer page, Integer size, String keyword) {
        var result = management.scopes(context.accountId(), context.sessionId(), page, size, keyword);
        return ClientApiContractConverter.page(result, page, size, ClientApiContractConverter::toBff, ApiScopeSummary::scopeCode);
    }

    /** 经私网 HTTP 转发当前 Operations 身份，并逐字段投影 Owner 的管理响应。 */
    @Override
    public ApiScopeDetailResponseBody scope(OperatorContext context, String scopeCode) {
        var result = management.scope(context.accountId(), context.sessionId(), scopeCode);
        var mapped = ClientApiContractConverter.toBff(result);
        ClientApiContractConverter.sameIdentity(scopeCode, mapped.scopeCode());
        return mapped;
    }

    /** 经私网 HTTP 转发当前 Operations 身份，并逐字段投影 Owner 的管理响应。 */
    @Override
    public ApiScopeMutationResponseBody create(OperatorContext context, CreateApiScopeRequestBody request) {
        try {
            var result = management.create(context.accountId(), context.sessionId(), ClientApiContractConverter.toPlatform(request));
            var mapped = ClientApiContractConverter.toBff(result);
            ClientApiContractConverter.sameIdentity(request.scopeCode(), mapped.scopeCode());
            return mapped;
        } catch (com.paytech.foundation.httpclient.api.HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }

    /** 经私网 HTTP 转发当前 Operations 身份，并逐字段投影 Owner 的管理响应。 */
    @Override
    public ApiScopeMutationResponseBody update(OperatorContext context, String scopeCode, UpdateApiScopeRequestBody request) {
        try {
            var result = management.update(context.accountId(), context.sessionId(), scopeCode, ClientApiContractConverter.toPlatform(request));
            var mapped = ClientApiContractConverter.toBff(result);
            ClientApiContractConverter.sameIdentity(scopeCode, mapped.scopeCode());
            return mapped;
        } catch (com.paytech.foundation.httpclient.api.HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }

    /** 经私网 HTTP 转发当前 Operations 身份，并逐字段投影 Owner 的管理响应。 */
    @Override
    public PagingResponseBody<ApiScopeOption> options(OperatorContext context, Integer page, Integer size, String keyword) {
        var result = management.options(context.accountId(), context.sessionId(), page, size, keyword);
        return ClientApiContractConverter.page(result, page, size, ClientApiContractConverter::toBff, ApiScopeOption::scopeCode);
    }
}
