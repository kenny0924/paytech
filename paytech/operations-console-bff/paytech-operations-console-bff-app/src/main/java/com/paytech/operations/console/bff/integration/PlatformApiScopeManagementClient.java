package com.paytech.operations.console.bff.integration;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.ApiScopeManagementModels.*;
import com.paytech.operations.console.bff.service.model.OperatorContext;

/** Platform 非秘密管理查询与命令的本地端口。 */
public interface PlatformApiScopeManagementClient {

    /** 读取已授权的当前数据。 */
    PagingResponseBody<ApiScopeSummary> scopes(OperatorContext context, Integer page, Integer size, String keyword);

    /** 读取已授权的当前数据。 */
    ApiScopeDetailResponseBody scope(OperatorContext context, String scopeCode);

    /** 执行已授权的单对象命令。 */
    ApiScopeMutationResponseBody create(OperatorContext context, CreateApiScopeRequestBody request);

    /** 执行已授权的单对象命令。 */
    ApiScopeMutationResponseBody update(OperatorContext context, String scopeCode, UpdateApiScopeRequestBody request);

    /** 读取已授权的当前数据。 */
    PagingResponseBody<ApiScopeOption> options(OperatorContext context, Integer page, Integer size, String keyword);
}
