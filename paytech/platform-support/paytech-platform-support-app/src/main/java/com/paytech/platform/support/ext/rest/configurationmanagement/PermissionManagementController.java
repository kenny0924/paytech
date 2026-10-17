package com.paytech.platform.support.ext.rest.configurationmanagement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.service.processor.*;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.permissionmanagement.PermissionManagementApi;
import com.paytech.platform.support.api.permissionmanagement.request.ChangePermissionStatusRequestBody;
import com.paytech.platform.support.api.permissionmanagement.request.CreatePermissionRequestBody;
import com.paytech.platform.support.api.permissionmanagement.request.UpdatePermissionRequestBody;
import com.paytech.platform.support.api.permissionmanagement.response.PermissionOption;
import com.paytech.platform.support.api.permissionmanagement.response.PermissionReference;
import com.paytech.platform.support.api.permissionmanagement.response.PermissionResponseBody;
import com.paytech.platform.support.api.permissionmanagement.response.PermissionSummary;
import com.paytech.platform.support.ext.rest.audit.Audit;

import lombok.AllArgsConstructor;

/** 配置管理的协议适配器，不承载事务或授权规则。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class PermissionManagementController implements PermissionManagementApi {
    private final GetPermissionProcessor getPermissionProcessor;
    private final ListPermissionsProcessor listPermissionsProcessor;
    private final CreatePermissionProcessor createPermissionProcessor;
    private final UpdatePermissionProcessor updatePermissionProcessor;
    private final PermissionManagementConverter permissionManagementConverter;
    private final ListPermissionOptionsProcessor listPermissionOptionsProcessor;
    private final ChangePermissionStatusProcessor changePermissionStatusProcessor;
    private final ListPermissionReferencesProcessor listPermissionReferencesProcessor;

    /** 分页查询全局权限目录。审计沿用真实业务结果。 */
    @Audit(action = "OPERATIONS_PERMISSIONS", resourceType = "PERMISSION", resourceIdParameter = "code",
        fallbackResourceId = "PERMISSION_CATALOG", recordSuccess = false)
    @Override
    public PagingResponseBody<PermissionSummary> permissions(String actor, String session, Integer page, Integer size, String code, String resourceType, String status, String riskLevel) {
        return listPermissionsProcessor
            .process(
                permissionManagementConverter.list(actor, session, page, size, code, resourceType, status, riskLevel))
            .map(permissionManagementConverter::summary);
    }

    /** 查询权限详情、维护边界与引用计数。审计沿用真实业务结果。 */
    @Audit(action = "OPERATIONS_PERMISSION", resourceType = "PERMISSION", resourceIdParameter = "permissionCode",
        fallbackResourceId = "PERMISSION_CATALOG", recordSuccess = false)
    @Override
    public PermissionResponseBody permission(String actor, String session, String permissionCode) {
        return permissionManagementConverter
            .detail(getPermissionProcessor.process(permissionManagementConverter.get(actor, session, permissionCode)));
    }

    /** 创建初始状态为 INACTIVE 的权限。审计沿用真实业务结果。 */
    @Audit(action = "OPERATIONS_CREATE_PERMISSION", resourceType = "PERMISSION",
        fallbackResourceId = "PERMISSION_CATALOG", recordSuccess = true)
    @Override
    public ConfigurationMutationResponseBody createPermission(String actor, String session,
        CreatePermissionRequestBody request) {
        return permissionManagementConverter
            .mutation(createPermissionProcessor.process(permissionManagementConverter.create(actor, session, request)));
    }

    /** 按原版本更新权限风险等级。审计沿用真实业务结果。 */
    @Audit(action = "OPERATIONS_UPDATE_PERMISSION", resourceType = "PERMISSION", resourceIdParameter = "permissionCode",
        fallbackResourceId = "PERMISSION_CATALOG", recordSuccess = true)
    @Override
    public ConfigurationMutationResponseBody updatePermission(String actor, String session, String permissionCode,
        UpdatePermissionRequestBody request) {
        return permissionManagementConverter.mutation(updatePermissionProcessor
            .process(permissionManagementConverter.update(actor, session, permissionCode, request)));
    }

    /** 按原版本启用或停用权限。审计沿用真实业务结果。 */
    @Audit(action = "OPERATIONS_CHANGE_PERMISSION_STATUS", resourceType = "PERMISSION", resourceIdParameter = "permissionCode",
        fallbackResourceId = "PERMISSION_CATALOG", recordSuccess = true)
    @Override
    public ConfigurationMutationResponseBody changePermissionStatus(String actor, String session, String permissionCode,
        ChangePermissionStatusRequestBody request) {
        return permissionManagementConverter.mutation(changePermissionStatusProcessor
            .process(permissionManagementConverter.status(actor, session, permissionCode, request)));
    }

    /** 分页查询指定类别的权限引用。审计沿用真实业务结果。 */
    @Audit(action = "OPERATIONS_REFERENCES", resourceType = "PERMISSION", resourceIdParameter = "permissionCode",
        fallbackResourceId = "PERMISSION_CATALOG", recordSuccess = false)
    @Override
    public PagingResponseBody<PermissionReference> references(String actor, String session, String permissionCode, String kind, Integer page, Integer size) {
        return listPermissionReferencesProcessor
            .process(permissionManagementConverter.references(actor, session, permissionCode, kind, page, size))
            .map(permissionManagementConverter::reference);
    }

    /** 按使用场景查询全局 ACTIVE 权限选项。审计沿用真实业务结果。 */
    @Audit(action = "OPERATIONS_OPTIONS", resourceType = "PERMISSION",
        fallbackResourceId = "PERMISSION_CATALOG", recordSuccess = false)
    @Override
    public PagingResponseBody<PermissionOption> options(String actor, String session, String usage, Integer page, Integer size, String keyword) {
        return listPermissionOptionsProcessor
            .process(permissionManagementConverter.options(actor, session, usage, page, size, keyword))
            .map(permissionManagementConverter::option);
    }
}
