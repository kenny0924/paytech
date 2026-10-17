package com.paytech.operations.console.bff.integration;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.ApiCredentialManagementModels.*;
import com.paytech.operations.console.bff.service.model.OperatorContext;

/** Platform 非秘密管理查询与命令的本地端口。 */
public interface PlatformApiCredentialManagementClient {

    /** 读取已授权的当前数据。 */
    PagingResponseBody<ApiCredentialSummary> credentials(OperatorContext context, Integer page, Integer size, String clientId, String clientName, String credentialId, String keyPrefix, String status, String scopeCode);

    /** 读取已授权的当前数据。 */
    ApiCredentialDetailResponseBody credential(OperatorContext context, String credentialId);

    /** 读取已授权的当前数据。 */
    PagingResponseBody<ApiCredentialScopeGrant> scopes(OperatorContext context, String credentialId, Integer page, Integer size);

    /** 执行已授权的单对象命令。 */
    ApiCredentialRevokeResponseBody revoke(OperatorContext context, String credentialId, RevokeApiCredentialRequestBody request);
}
