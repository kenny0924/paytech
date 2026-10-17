package com.paytech.operations.console.bff.integration;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.PermissionManagementModels.*;
import com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ConfigurationMutationResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
/** 配置页面调用 Platform 的窄本地端口。 */
public interface PlatformPermissionManagementClient {

    /** 转发单个已授权配置操作。 */
    PagingResponseBody<PermissionSummary> permissions(OperatorContext context, Integer page, Integer size, String code, String resourceType, String status, String riskLevel);

    /** 转发单个已授权配置操作。 */
    PermissionResponseBody permission(OperatorContext context, String permissionCode);

    /** 转发单个已授权配置操作。 */
    ConfigurationMutationResponseBody createPermission(OperatorContext context, CreatePermissionRequestBody request);

    /** 转发单个已授权配置操作。 */
    ConfigurationMutationResponseBody updatePermission(OperatorContext context, String permissionCode, UpdatePermissionRequestBody request);

    /** 转发单个已授权配置操作。 */
    ConfigurationMutationResponseBody changePermissionStatus(OperatorContext context, String permissionCode, ChangePermissionStatusRequestBody request);

    /** 转发单个已授权配置操作。 */
    PagingResponseBody<PermissionReference> references(OperatorContext context, String permissionCode, String kind, Integer page, Integer size);

    /** 转发单个已授权配置操作。 */
    PagingResponseBody<PermissionOption> options(OperatorContext context, String usage, Integer page, Integer size, String keyword);
}
