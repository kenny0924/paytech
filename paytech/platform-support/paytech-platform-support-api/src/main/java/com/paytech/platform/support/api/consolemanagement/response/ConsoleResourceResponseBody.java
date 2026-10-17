package com.paytech.platform.support.api.consolemanagement.response;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.paytech.platform.support.api.configurationmanagement.response.ManagementProtection;

/** 资源身份、当前绑定与维护边界；不支持的旧组件仍可读取。 */
public final class ConsoleResourceResponseBody {
    /** 资源主键，以十进制字符串传输。 */
    private final String resourceId;

    /** 稳定资源编码。 */
    private final String resourceCode;

    /** 资源所属 Channel。 */
    private final String channel;

    /** 资源类型 MENU 或 BUTTON。 */
    private final String resourceType;

    /** 父资源主键，根目录为空。 */
    private final String parentResourceId;

    /** 展示名称。 */
    private final String displayName;

    /** 由已部署组件派生的前端路径。 */
    private final String frontendRoute;

    /** 已部署组件键，目录和按钮可空。 */
    private final String componentKey;

    /** 可见性权限主键。 */
    private final String permissionId;

    /** 当前绑定权限编码。 */
    private final String permissionCode;

    /** 当前绑定权限状态。 */
    private final String permissionStatus;

    /** 同级排序，范围 0..999999。 */
    private final int sortOrder;

    /** 当前或请求的配置状态。 */
    private final String status;

    /** 当前版本。 */
    private final int version;

    /** 服务端计算的维护边界。 */
    private final ManagementProtection managementProtection;

    /** 当前制品是否支持该组件或动作。 */
    private final boolean supported;

    /** UTC 创建时间。 */
    private final Instant createTime;

    /** UTC 更新时间。 */
    private final Instant updateTime;

    /** 绑定协议字段，保留原有 JSON 名称和校验约束。 */
    @JsonCreator
    public ConsoleResourceResponseBody(@JsonProperty(value = "resourceId") String resourceId,
        @JsonProperty(value = "resourceCode") String resourceCode,
        @JsonProperty(value = "channel") String channel,
        @JsonProperty(value = "resourceType") String resourceType,
        @JsonProperty(value = "parentResourceId") String parentResourceId,
        @JsonProperty(value = "displayName") String displayName,
        @JsonProperty(value = "frontendRoute") String frontendRoute,
        @JsonProperty(value = "componentKey") String componentKey,
        @JsonProperty(value = "permissionId") String permissionId,
        @JsonProperty(value = "permissionCode") String permissionCode,
        @JsonProperty(value = "permissionStatus") String permissionStatus,
        @JsonProperty(value = "sortOrder") int sortOrder,
        @JsonProperty(value = "status") String status,
        @JsonProperty(value = "version") int version,
        @JsonProperty(value = "managementProtection") ManagementProtection managementProtection,
        @JsonProperty(value = "supported") boolean supported,
        @JsonProperty(value = "createTime") Instant createTime,
        @JsonProperty(value = "updateTime") Instant updateTime) {
        this.resourceId = resourceId;
        this.resourceCode = resourceCode;
        this.channel = channel;
        this.resourceType = resourceType;
        this.parentResourceId = parentResourceId;
        this.displayName = displayName;
        this.frontendRoute = frontendRoute;
        this.componentKey = componentKey;
        this.permissionId = permissionId;
        this.permissionCode = permissionCode;
        this.permissionStatus = permissionStatus;
        this.sortOrder = sortOrder;
        this.status = status;
        this.version = version;
        this.managementProtection = managementProtection;
        this.supported = supported;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /** 资源主键，以十进制字符串传输。 */
    public String getResourceId() {
        return resourceId;
    }

    /** 稳定资源编码。 */
    public String getResourceCode() {
        return resourceCode;
    }

    /** 资源所属 Channel。 */
    public String getChannel() {
        return channel;
    }

    /** 资源类型 MENU 或 BUTTON。 */
    public String getResourceType() {
        return resourceType;
    }

    /** 父资源主键，根目录为空。 */
    public String getParentResourceId() {
        return parentResourceId;
    }

    /** 展示名称。 */
    public String getDisplayName() {
        return displayName;
    }

    /** 由已部署组件派生的前端路径。 */
    public String getFrontendRoute() {
        return frontendRoute;
    }

    /** 已部署组件键，目录和按钮可空。 */
    public String getComponentKey() {
        return componentKey;
    }

    /** 可见性权限主键。 */
    public String getPermissionId() {
        return permissionId;
    }

    /** 当前绑定权限编码。 */
    public String getPermissionCode() {
        return permissionCode;
    }

    /** 当前绑定权限状态。 */
    public String getPermissionStatus() {
        return permissionStatus;
    }

    /** 同级排序，范围 0..999999。 */
    public int getSortOrder() {
        return sortOrder;
    }

    /** 当前或请求的配置状态。 */
    public String getStatus() {
        return status;
    }

    /** 当前版本。 */
    public int getVersion() {
        return version;
    }

    /** 服务端计算的维护边界。 */
    public ManagementProtection getManagementProtection() {
        return managementProtection;
    }

    /** 当前制品是否支持该组件或动作。 */
    public boolean isSupported() {
        return supported;
    }

    /** UTC 创建时间。 */
    public Instant getCreateTime() {
        return createTime;
    }

    /** UTC 更新时间。 */
    public Instant getUpdateTime() {
        return updateTime;
    }
}
