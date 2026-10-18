package com.paytech.operations.console.bff.integration.impl;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.PermissionManagementModels.*;
import com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ConfigurationMutationResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.integration.PlatformPermissionManagementClient;
import com.paytech.operations.console.bff.integration.ConfigurationContractConverter;
import com.paytech.platform.support.api.permissionmanagement.PermissionManagementApi;
/** 单个 Platform API 的无状态转发适配。 */
@org.springframework.stereotype.Component
@lombok.AllArgsConstructor
public class PlatformPermissionManagementClientImpl implements PlatformPermissionManagementClient {
    private final PermissionManagementApi management;

    /** 保留参数与当前身份，请求幂等由 Gateway 负责。 */
    @Override
    public PagingResponseBody<PermissionSummary> permissions(OperatorContext context, Integer page, Integer size, String code, String resourceType, String status, String riskLevel) {
        var result = management.permissions(context.accountId(), context.sessionId(), page, size, code, resourceType, status, riskLevel);
        return result.map(ConfigurationContractConverter::toBff);
    }

    /** 保留参数与当前身份，请求幂等由 Gateway 负责。 */
    @Override
    public PermissionResponseBody permission(OperatorContext context, String permissionCode) {
        var result = management.permission(context.accountId(), context.sessionId(), permissionCode);
        return ConfigurationContractConverter.toBff(result);
    }

    /** 保留参数与当前身份，请求幂等由 Gateway 负责。 */
    @Override
    public ConfigurationMutationResponseBody createPermission(OperatorContext context, CreatePermissionRequestBody request) {
        try {
            var result = management.createPermission(context.accountId(), context.sessionId(), ConfigurationContractConverter.toPlatform(request));
            return ConfigurationContractConverter.toBff(result);
        } catch (com.paytech.foundation.httpclient.api.HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }

    /** 保留参数与当前身份，请求幂等由 Gateway 负责。 */
    @Override
    public ConfigurationMutationResponseBody updatePermission(OperatorContext context, String permissionCode, UpdatePermissionRequestBody request) {
        try {
            var result = management.updatePermission(context.accountId(), context.sessionId(), permissionCode, ConfigurationContractConverter.toPlatform(request));
            return ConfigurationContractConverter.toBff(result);
        } catch (com.paytech.foundation.httpclient.api.HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }

    /** 保留参数与当前身份，请求幂等由 Gateway 负责。 */
    @Override
    public ConfigurationMutationResponseBody changePermissionStatus(OperatorContext context, String permissionCode, ChangePermissionStatusRequestBody request) {
        try {
            var result = management.changePermissionStatus(context.accountId(), context.sessionId(), permissionCode, ConfigurationContractConverter.toPlatform(request));
            return ConfigurationContractConverter.toBff(result);
        } catch (com.paytech.foundation.httpclient.api.HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }

    /** 保留参数与当前身份，请求幂等由 Gateway 负责。 */
    @Override
    public PagingResponseBody<PermissionReference> references(OperatorContext context, String permissionCode, String kind, Integer page, Integer size) {
        var result = management.references(context.accountId(), context.sessionId(), permissionCode, kind, page, size);
        return result.map(ConfigurationContractConverter::toBff);
    }

    /** 保留参数与当前身份，请求幂等由 Gateway 负责。 */
    @Override
    public PagingResponseBody<PermissionOption> options(OperatorContext context, String usage, Integer page, Integer size, String keyword) {
        var result = management.options(context.accountId(), context.sessionId(), usage, page, size, keyword);
        return result.map(ConfigurationContractConverter::toBff);
    }
}
