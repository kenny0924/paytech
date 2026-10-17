package com.paytech.platform.support.api.operations.response;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Operations 响应字段，保持既有 JSON 合同。 */
public final class RoleResource {
    /**
     * 资源 ID。
     * <li>审计筛选时可空。</li>
     * <li>非空时精确匹配。</li>
     */
    private final String resourceId;

    /** 稳定资源业务代码。 */
    private final String resourceCode;

    /** 资源类型 Code。 */
    private final String resourceType;

    /**
     * 父资源 ID。
     * <li>根资源时为空。</li>
     */
    private final String parentResourceId;

    /** 资源展示名称。 */
    private final String displayName;

    /**
     * 前端路由。
     * <li>无路由的目录或按钮可空。</li>
     */
    private final String frontendRoute;

    /**
     * 已部署组件键。
     * <li>目录或按钮可空。</li>
     */
    private final String componentKey;

    /**
     * 权限 ID。
     * <li>十进制字符串。</li>
     */
    private final String permissionId;

    /**
     * 同级排序值。
     * <li>升序展示。</li>
     */
    private final int sortOrder;

    /** 绑定协议字段，保留 JSON 名称与校验约束。 */
    @JsonCreator
    public RoleResource(@JsonProperty(value = "resourceId") String resourceId,
        @JsonProperty(value = "resourceCode") String resourceCode,
        @JsonProperty(value = "resourceType") String resourceType,
        @JsonProperty(value = "parentResourceId") String parentResourceId,
        @JsonProperty(value = "displayName") String displayName,
        @JsonProperty(value = "frontendRoute") String frontendRoute,
        @JsonProperty(value = "componentKey") String componentKey,
        @JsonProperty(value = "permissionId") String permissionId,
        @JsonProperty(value = "sortOrder", required = true) int sortOrder) {
        this.resourceId = resourceId;
        this.resourceCode = resourceCode;
        this.resourceType = resourceType;
        this.parentResourceId = parentResourceId;
        this.displayName = displayName;
        this.frontendRoute = frontendRoute;
        this.componentKey = componentKey;
        this.permissionId = permissionId;
        this.sortOrder = sortOrder;
    }

    /** 资源 ID；审计筛选时可空，非空时精确匹配。 */
    public String getResourceId() {
        return resourceId;
    }

    /** 稳定资源业务代码。 */
    public String getResourceCode() {
        return resourceCode;
    }

    /** 资源类型 Code。 */
    public String getResourceType() {
        return resourceType;
    }

    /** 父资源 ID，根资源时为空。 */
    public String getParentResourceId() {
        return parentResourceId;
    }

    /** 资源展示名称。 */
    public String getDisplayName() {
        return displayName;
    }

    /** 前端路由；无路由的目录或按钮可空。 */
    public String getFrontendRoute() {
        return frontendRoute;
    }

    /** 已部署组件键；目录或按钮可空。 */
    public String getComponentKey() {
        return componentKey;
    }

    /** 权限 ID，十进制字符串。 */
    public String getPermissionId() {
        return permissionId;
    }

    /** 同级排序值，升序展示。 */
    public int getSortOrder() {
        return sortOrder;
    }

    /** 按协议字段比较值。 */
    @Override
    public boolean equals(Object value) {
        return value instanceof RoleResource other && Objects.equals(resourceId, other.resourceId)
            && Objects.equals(resourceCode, other.resourceCode)
            && Objects.equals(resourceType, other.resourceType)
            && Objects.equals(parentResourceId, other.parentResourceId)
            && Objects.equals(displayName, other.displayName)
            && Objects.equals(frontendRoute, other.frontendRoute)
            && Objects.equals(componentKey, other.componentKey)
            && Objects.equals(permissionId, other.permissionId)
            && Objects.equals(sortOrder, other.sortOrder);
    }

    /** 与字段值相等语义保持一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(resourceId, resourceCode, resourceType, parentResourceId, displayName, frontendRoute,
            componentKey, permissionId, sortOrder);
    }
}
