package com.paytech.operations.console.bff.integration;

/** Platform 与浏览器 DTO 的显式转换，不通过任意 Map 或 JSON 反射复制合同。 */
public final class ConfigurationContractConverter {
    private ConfigurationContractConverter() {}

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ConfigurationMutationResponseBody toBff(com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        if (value.getObjectType() == null || !java.util.Set.of("GATEWAY_TARGET", "GATEWAY_ROUTE", "CONSOLE_RESOURCE", "PERMISSION").contains(value.getObjectType())
            || value.getObjectId() == null || !value.getObjectId().matches("[1-9][0-9]{0,18}") || value.getObjectCode() == null
            || !value.getObjectCode().matches("[A-Za-z0-9_-]{1,128}") || value.getStatus() == null
            || !java.util.Set.of("ACTIVE", "INACTIVE", "DISABLED").contains(value.getStatus()) || value.getVersion() <= 0)
            throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ConfigurationMutationResponseBody(
            value.getObjectType(),
            value.getObjectId(),
            value.getObjectCode(),
            value.getStatus(),
            value.getVersion());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.platform.support.api.configurationmanagement.response.ManagementProtection toPlatform(com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ManagementProtection value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.platform.support.api.configurationmanagement.response.ManagementProtection(
            java.util.List.copyOf(value.editableFields()),
            value.statusChangeAllowed(),
            value.reason());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ManagementProtection toBff(com.paytech.platform.support.api.configurationmanagement.response.ManagementProtection value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ManagementProtection(
            java.util.List.copyOf(value.getEditableFields()),
            value.isStatusChangeAllowed(),
            value.getReason());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.ConsoleResourceModels.ConsoleResourcesResponseBody toBff(com.paytech.platform.support.api.consolemanagement.response.ConsoleResourcesResponseBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.ConsoleResourceModels.ConsoleResourcesResponseBody(
            value.getItems().stream().map(ConfigurationContractConverter::toBff).toList());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.ConsoleResourceModels.ConsoleResourceResponseBody toBff(com.paytech.platform.support.api.consolemanagement.response.ConsoleResourceResponseBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.ConsoleResourceModels.ConsoleResourceResponseBody(
            value.getResourceId(),
            value.getResourceCode(),
            value.getChannel(),
            value.getResourceType(),
            value.getParentResourceId(),
            value.getDisplayName(),
            value.getFrontendRoute(),
            value.getComponentKey(),
            value.getPermissionId(),
            value.getPermissionCode(),
            value.getPermissionStatus(),
            value.getSortOrder(),
            value.getStatus(),
            value.getVersion(),
            toBff(value.getManagementProtection()),
            value.isSupported(),
            value.getCreateTime(),
            value.getUpdateTime());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.ConsoleResourceModels.ConsoleResourceCapabilitiesResponseBody toBff(com.paytech.platform.support.api.consolemanagement.response.ConsoleResourceCapabilitiesResponseBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.ConsoleResourceModels.ConsoleResourceCapabilitiesResponseBody(
            value.getComponents().stream().map(ConfigurationContractConverter::toBff).toList(),
            java.util.List.copyOf(value.getResourceTypes()),
            value.getMaxDepth(),
            value.getMaxResources());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.ConsoleResourceModels.ComponentCapability toBff(com.paytech.platform.support.api.consolemanagement.response.ComponentCapability value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.ConsoleResourceModels.ComponentCapability(
            value.getComponentKey(),
            value.getDisplayName(),
            value.getFrontendRoute(),
            value.getActions().stream().map(ConfigurationContractConverter::toBff).toList());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.ConsoleResourceModels.ActionCapability toBff(com.paytech.platform.support.api.consolemanagement.response.ActionCapability value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.ConsoleResourceModels.ActionCapability(
            value.getResourceCode(),
            value.getDisplayName());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.platform.support.api.consolemanagement.request.CreateConsoleResourceRequestBody toPlatform(com.paytech.operations.console.bff.service.model.ConsoleResourceModels.CreateConsoleResourceRequestBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.platform.support.api.consolemanagement.request.CreateConsoleResourceRequestBody(
            value.resourceCode(),
            value.resourceType(),
            value.displayName(),
            value.parentResourceId(),
            value.componentKey(),
            value.permissionId(),
            value.sortOrder(),
            value.reason());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.ConsoleResourceModels.CreateConsoleResourceRequestBody toBff(com.paytech.platform.support.api.consolemanagement.request.CreateConsoleResourceRequestBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.ConsoleResourceModels.CreateConsoleResourceRequestBody(
            value.getResourceCode(),
            value.getResourceType(),
            value.getDisplayName(),
            value.getParentResourceId(),
            value.getComponentKey(),
            value.getPermissionId(),
            value.getSortOrder(),
            value.getReason());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.platform.support.api.consolemanagement.request.UpdateConsoleResourceRequestBody toPlatform(com.paytech.operations.console.bff.service.model.ConsoleResourceModels.UpdateConsoleResourceRequestBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.platform.support.api.consolemanagement.request.UpdateConsoleResourceRequestBody(
            value.displayName(),
            value.parentResourceId(),
            value.componentKey(),
            value.permissionId(),
            value.sortOrder(),
            value.expectedVersion(),
            value.reason());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.ConsoleResourceModels.UpdateConsoleResourceRequestBody toBff(com.paytech.platform.support.api.consolemanagement.request.UpdateConsoleResourceRequestBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.ConsoleResourceModels.UpdateConsoleResourceRequestBody(
            value.getDisplayName(),
            value.getParentResourceId(),
            value.getComponentKey(),
            value.getPermissionId(),
            value.getSortOrder(),
            value.getExpectedVersion(),
            value.getReason());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.platform.support.api.consolemanagement.request.ChangeConsoleResourceStatusRequestBody toPlatform(com.paytech.operations.console.bff.service.model.ConsoleResourceModels.ChangeConsoleResourceStatusRequestBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.platform.support.api.consolemanagement.request.ChangeConsoleResourceStatusRequestBody(
            value.status(),
            value.expectedVersion(),
            value.reason());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.ConsoleResourceModels.ChangeConsoleResourceStatusRequestBody toBff(com.paytech.platform.support.api.consolemanagement.request.ChangeConsoleResourceStatusRequestBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.ConsoleResourceModels.ChangeConsoleResourceStatusRequestBody(
            value.getStatus(),
            value.getExpectedVersion(),
            value.getReason());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.PermissionManagementModels.PermissionSummary toBff(com.paytech.platform.support.api.permissionmanagement.response.PermissionSummary value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.PermissionManagementModels.PermissionSummary(
            value.getPermissionId(),
            value.getPermissionCode(),
            value.getResourceType(),
            value.getActionCode(),
            value.getRiskLevel(),
            value.getStatus(),
            value.getVersion());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.PermissionManagementModels.PermissionResponseBody toBff(com.paytech.platform.support.api.permissionmanagement.response.PermissionResponseBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.PermissionManagementModels.PermissionResponseBody(
            value.getPermissionId(),
            value.getPermissionCode(),
            value.getResourceType(),
            value.getActionCode(),
            value.getRiskLevel(),
            value.getStatus(),
            value.getVersion(),
            value.getCreateTime(),
            value.getUpdateTime(),
            toBff(value.getManagementProtection()),
            toBff(value.getReferenceCounts()));
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.PermissionManagementModels.ReferenceCounts toBff(com.paytech.platform.support.api.permissionmanagement.response.ReferenceCounts value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.PermissionManagementModels.ReferenceCounts(
            value.getResource(),
            value.getRoute(),
            value.getRole());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.PermissionManagementModels.PermissionOption toBff(com.paytech.platform.support.api.permissionmanagement.response.PermissionOption value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.PermissionManagementModels.PermissionOption(
            value.getPermissionId(),
            value.getPermissionCode(),
            value.getResourceType(),
            value.getActionCode(),
            value.getRiskLevel());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.PermissionManagementModels.ResourceReference toBff(com.paytech.platform.support.api.permissionmanagement.response.ResourceReference value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.PermissionManagementModels.ResourceReference(
            value.getResourceId(),
            value.getResourceCode(),
            value.getDisplayName(),
            value.getChannel(),
            value.getResourceType(),
            value.getStatus());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.PermissionManagementModels.RouteReference toBff(com.paytech.platform.support.api.permissionmanagement.response.RouteReference value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.PermissionManagementModels.RouteReference(
            value.getRouteCode(),
            value.getChannel(),
            value.getHttpMethod(),
            value.getExternalPathTemplate(),
            value.getTargetServiceCode(),
            value.getStatus());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.PermissionManagementModels.RoleReference toBff(com.paytech.platform.support.api.permissionmanagement.response.RoleReference value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.PermissionManagementModels.RoleReference(
            value.getRoleId(),
            value.getRoleCode(),
            value.getRoleName(),
            value.getRoleStatus(),
            value.getGrantStatus(),
            value.getScopeKey());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.platform.support.api.permissionmanagement.request.CreatePermissionRequestBody toPlatform(com.paytech.operations.console.bff.service.model.PermissionManagementModels.CreatePermissionRequestBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.platform.support.api.permissionmanagement.request.CreatePermissionRequestBody(
            value.permissionCode(),
            value.resourceType(),
            value.actionCode(),
            value.riskLevel(),
            value.reason());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.PermissionManagementModels.CreatePermissionRequestBody toBff(com.paytech.platform.support.api.permissionmanagement.request.CreatePermissionRequestBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.PermissionManagementModels.CreatePermissionRequestBody(
            value.getPermissionCode(),
            value.getResourceType(),
            value.getActionCode(),
            value.getRiskLevel(),
            value.getReason());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.platform.support.api.permissionmanagement.request.UpdatePermissionRequestBody toPlatform(com.paytech.operations.console.bff.service.model.PermissionManagementModels.UpdatePermissionRequestBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.platform.support.api.permissionmanagement.request.UpdatePermissionRequestBody(
            value.riskLevel(),
            value.expectedVersion(),
            value.reason());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.PermissionManagementModels.UpdatePermissionRequestBody toBff(com.paytech.platform.support.api.permissionmanagement.request.UpdatePermissionRequestBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.PermissionManagementModels.UpdatePermissionRequestBody(
            value.getRiskLevel(),
            value.getExpectedVersion(),
            value.getReason());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.platform.support.api.permissionmanagement.request.ChangePermissionStatusRequestBody toPlatform(com.paytech.operations.console.bff.service.model.PermissionManagementModels.ChangePermissionStatusRequestBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.platform.support.api.permissionmanagement.request.ChangePermissionStatusRequestBody(
            value.status(),
            value.expectedVersion(),
            value.reason());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.PermissionManagementModels.ChangePermissionStatusRequestBody toBff(com.paytech.platform.support.api.permissionmanagement.request.ChangePermissionStatusRequestBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.PermissionManagementModels.ChangePermissionStatusRequestBody(
            value.getStatus(),
            value.getExpectedVersion(),
            value.getReason());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.CreateGatewayTargetRequestBody toPlatform(com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.CreateGatewayTargetRequestBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.CreateGatewayTargetRequestBody(
            value.targetServiceCode(),
            value.channel(),
            value.discoveryServiceName(),
            value.reason());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.CreateGatewayTargetRequestBody toBff(com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.CreateGatewayTargetRequestBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.CreateGatewayTargetRequestBody(
            value.targetServiceCode(),
            value.channel(),
            value.discoveryServiceName(),
            value.reason());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.UpdateGatewayTargetRequestBody toPlatform(com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.UpdateGatewayTargetRequestBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.UpdateGatewayTargetRequestBody(
            value.discoveryServiceName(),
            value.expectedVersion(),
            value.reason());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.UpdateGatewayTargetRequestBody toBff(com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.UpdateGatewayTargetRequestBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.UpdateGatewayTargetRequestBody(
            value.discoveryServiceName(),
            value.expectedVersion(),
            value.reason());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.CreateGatewayRouteRequestBody toPlatform(com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.CreateGatewayRouteRequestBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.CreateGatewayRouteRequestBody(
            value.routeCode(),
            value.channel(),
            value.httpMethod(),
            value.externalPathTemplate(),
            value.downstreamPathTemplate(),
            value.targetServiceCode(),
            value.securityProfileCode(),
            value.authorizationMode(),
            value.permissionId(),
            value.requiredScopeCode(),
            value.requestCookieMode(),
            value.responseCookieMode(),
            value.idempotencyMode(),
            value.bodyLimitBytes(),
            value.bodyReceiveTimeoutMillis(),
            value.responseTimeoutMillis(),
            value.headers().stream().map(ConfigurationContractConverter::toPlatform).toList(),
            java.util.List.copyOf(value.queryParameterNames()),
            value.guardRules().stream().map(ConfigurationContractConverter::toPlatform).toList(),
            value.reason());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.CreateGatewayRouteRequestBody toBff(com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.CreateGatewayRouteRequestBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.CreateGatewayRouteRequestBody(
            value.routeCode(),
            value.channel(),
            value.httpMethod(),
            value.externalPathTemplate(),
            value.downstreamPathTemplate(),
            value.targetServiceCode(),
            value.securityProfileCode(),
            value.authorizationMode(),
            value.permissionId(),
            value.requiredScopeCode(),
            value.requestCookieMode(),
            value.responseCookieMode(),
            value.idempotencyMode(),
            value.bodyLimitBytes(),
            value.bodyReceiveTimeoutMillis(),
            value.responseTimeoutMillis(),
            value.headers().stream().map(ConfigurationContractConverter::toBff).toList(),
            java.util.List.copyOf(value.queryParameterNames()),
            value.guardRules().stream().map(ConfigurationContractConverter::toBff).toList(),
            value.reason());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.UpdateGatewayRouteRequestBody toPlatform(com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.UpdateGatewayRouteRequestBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.UpdateGatewayRouteRequestBody(
            value.httpMethod(),
            value.externalPathTemplate(),
            value.downstreamPathTemplate(),
            value.targetServiceCode(),
            value.securityProfileCode(),
            value.authorizationMode(),
            value.permissionId(),
            value.requiredScopeCode(),
            value.requestCookieMode(),
            value.responseCookieMode(),
            value.idempotencyMode(),
            value.bodyLimitBytes(),
            value.bodyReceiveTimeoutMillis(),
            value.responseTimeoutMillis(),
            value.headers().stream().map(ConfigurationContractConverter::toPlatform).toList(),
            java.util.List.copyOf(value.queryParameterNames()),
            value.guardRules().stream().map(ConfigurationContractConverter::toPlatform).toList(),
            value.expectedVersion(),
            value.reason());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.UpdateGatewayRouteRequestBody toBff(com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.UpdateGatewayRouteRequestBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.UpdateGatewayRouteRequestBody(
            value.httpMethod(),
            value.externalPathTemplate(),
            value.downstreamPathTemplate(),
            value.targetServiceCode(),
            value.securityProfileCode(),
            value.authorizationMode(),
            value.permissionId(),
            value.requiredScopeCode(),
            value.requestCookieMode(),
            value.responseCookieMode(),
            value.idempotencyMode(),
            value.bodyLimitBytes(),
            value.bodyReceiveTimeoutMillis(),
            value.responseTimeoutMillis(),
            value.headers().stream().map(ConfigurationContractConverter::toBff).toList(),
            java.util.List.copyOf(value.queryParameterNames()),
            value.guardRules().stream().map(ConfigurationContractConverter::toBff).toList(),
            value.expectedVersion(),
            value.reason());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayRouteHeaderRequestBody toPlatform(com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.GatewayRouteHeaderRequestBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayRouteHeaderRequestBody(
            value.headerName(),
            value.headerUse());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.GatewayRouteHeaderRequestBody toBff(com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayRouteHeaderRequestBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.GatewayRouteHeaderRequestBody(
            value.headerName(),
            value.headerUse());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayGuardRuleRequestBody toPlatform(com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.GatewayGuardRuleRequestBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayGuardRuleRequestBody(
            value.ruleType(),
            value.dimension(),
            value.capacity(),
            value.refillTokens(),
            value.refillPeriodMillis(),
            value.concurrencyLimit());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.GatewayGuardRuleRequestBody toBff(com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayGuardRuleRequestBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.GatewayGuardRuleRequestBody(
            value.ruleType(),
            value.dimension(),
            value.capacity(),
            value.refillTokens(),
            value.refillPeriodMillis(),
            value.concurrencyLimit());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.GatewayConfigurationCapabilitiesResponseBody toBff(com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayConfigurationCapabilitiesResponseBody value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.GatewayConfigurationCapabilitiesResponseBody(
            value.channels().stream().map(ConfigurationContractConverter::toBff).toList(),
            java.util.List.copyOf(value.httpMethods()),
            toBff(value.limits()),
            java.util.List.copyOf(value.forbiddenHeaderNames()),
            java.util.List.copyOf(value.headerUses()));
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.ChannelCapability toBff(com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.ChannelCapability value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.ChannelCapability(
            value.channel(),
            value.profiles().stream().map(ConfigurationContractConverter::toBff).toList());
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.ProfileCapability toBff(com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.ProfileCapability value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.ProfileCapability(
            value.securityProfileCode(),
            java.util.List.copyOf(value.authorizationModes()),
            java.util.List.copyOf(value.requestCookieModes()),
            java.util.List.copyOf(value.responseCookieModes()),
            java.util.List.copyOf(value.idempotencyModes()),
            java.util.List.copyOf(value.allowedGuardDimensions()),
            java.util.List.copyOf(value.requiredGuardDimensions()));
    }

    /** 显式投影允许字段，内部身份上下文不进入浏览器响应。 */
    public static com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.Limits toBff(com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.Limits value) {
        if (value == null) throw new com.paytech.foundation.common.error.ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        return new com.paytech.operations.console.bff.service.model.GatewayConfigurationModels.Limits(
            value.maxBodyLimitBytes(),
            value.maxBodyReceiveTimeoutMillis(),
            value.maxResponseTimeoutMillis(),
            value.maxRateCapacity(),
            value.maxRateRefillTokens(),
            value.maxRateRefillPeriodMillis(),
            value.maxConcurrencyLimit(),
            value.maxTargets(),
            value.maxRoutes(),
            value.maxCorsHeaders(),
            value.maxForwardedHeaders(),
            value.maxQueryParameters(),
            value.maxGuardRules());
    }

    /** 引用查询保持封闭类型，不能混入人员或凭据。 */
    public static com.paytech.operations.console.bff.service.model.PermissionManagementModels.PermissionReference toBff(
        com.paytech.platform.support.api.permissionmanagement.response.PermissionReference value) {
        return switch (value) {
            case com.paytech.platform.support.api.permissionmanagement.response.ResourceReference r -> toBff(r);
            case com.paytech.platform.support.api.permissionmanagement.response.RouteReference r -> toBff(r);
            case com.paytech.platform.support.api.permissionmanagement.response.RoleReference r -> toBff(r);
        };
    }
}
