package com.paytech.operations.console.bff.integration.impl;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.ApiCredentialManagementModels.*;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.integration.PlatformApiCredentialManagementClient;
import com.paytech.operations.console.bff.integration.ClientApiContractConverter;
import com.paytech.platform.support.api.apicredentialmanagement.ApiCredentialManagementApi;

/** 保留可信身份、字段错误及幂等键的 HTTP 转发。 */
@org.springframework.stereotype.Component
@lombok.AllArgsConstructor
public class PlatformApiCredentialManagementClientImpl implements PlatformApiCredentialManagementClient {
    private final ApiCredentialManagementApi management;

    /** 经私网 HTTP 转发当前 Operations 身份，并逐字段投影 Owner 的管理响应。 */
    @Override
    public PagingResponseBody<ApiCredentialSummary> credentials(OperatorContext context, Integer page, Integer size, String clientId, String clientName, String credentialId, String keyPrefix, String status, String scopeCode) {
        var result = management.credentials(context.accountId(), context.sessionId(), page, size, clientId, clientName, credentialId, keyPrefix, status, scopeCode);
        return ClientApiContractConverter.page(result, page, size, ClientApiContractConverter::toBff, ApiCredentialSummary::credentialId);
    }

    /** 经私网 HTTP 转发当前 Operations 身份，并逐字段投影 Owner 的管理响应。 */
    @Override
    public ApiCredentialDetailResponseBody credential(OperatorContext context, String credentialId) {
        var result = management.credential(context.accountId(), context.sessionId(), credentialId);
        var mapped = ClientApiContractConverter.toBff(result);
        ClientApiContractConverter.sameIdentity(credentialId, mapped.credentialId());
        return mapped;
    }

    /** 经私网 HTTP 转发当前 Operations 身份，并逐字段投影 Owner 的管理响应。 */
    @Override
    public PagingResponseBody<ApiCredentialScopeGrant> scopes(OperatorContext context, String credentialId, Integer page, Integer size) {
        var result = management.scopes(context.accountId(), context.sessionId(), credentialId, page, size);
        return ClientApiContractConverter.page(result, page, size, ClientApiContractConverter::toBff, ApiCredentialScopeGrant::scopeId);
    }

    /** 经私网 HTTP 转发当前 Operations 身份，并逐字段投影 Owner 的管理响应。 */
    @Override
    public ApiCredentialRevokeResponseBody revoke(OperatorContext context, String credentialId, RevokeApiCredentialRequestBody request) {
        try {
            var result = management.revoke(context.accountId(), context.sessionId(), credentialId, ClientApiContractConverter.toPlatform(request));
            var mapped = ClientApiContractConverter.toBff(result);
            ClientApiContractConverter.sameIdentity(credentialId, mapped.credentialId());
            return mapped;
        } catch (com.paytech.foundation.httpclient.api.HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }
}
