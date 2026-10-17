package com.paytech.platform.support.api.permissionmanagement.response;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 资源引用，保留所属 Channel。 */
public final class ResourceReference implements PermissionReference {
    /** 资源主键，使用十进制字符串。 */
    private final String resourceId;

    /** 稳定资源编码。 */
    private final String resourceCode;

    /** 资源展示名称。 */
    private final String displayName;

    /** 资源或路由所属 Channel。 */
    private final String channel;

    /** 资源类型：MENU 或 BUTTON。 */
    private final String resourceType;

    /** 当前配置状态：ACTIVE 或 DISABLED。 */
    private final String status;

    /** 按明确的字段合同创建对象。 */
    @JsonCreator
    public ResourceReference(@JsonProperty(value = "resourceId") String resourceId,
        @JsonProperty(value = "resourceCode") String resourceCode,
        @JsonProperty(value = "displayName") String displayName,
        @JsonProperty(value = "channel") String channel,
        @JsonProperty(value = "resourceType") String resourceType,
        @JsonProperty(value = "status") String status) {
        this.resourceId = resourceId;
        this.resourceCode = resourceCode;
        this.displayName = displayName;
        this.channel = channel;
        this.resourceType = resourceType;
        this.status = status;
    }

    /** 资源主键，使用十进制字符串。 */
    public String getResourceId() { return resourceId; }

    /** 稳定资源编码。 */
    public String getResourceCode() { return resourceCode; }

    /** 资源展示名称。 */
    public String getDisplayName() { return displayName; }

    /** 资源或路由所属 Channel。 */
    public String getChannel() { return channel; }

    /** 资源类型：MENU 或 BUTTON。 */
    public String getResourceType() { return resourceType; }

    /** 当前配置状态：ACTIVE 或 DISABLED。 */
    public String getStatus() { return status; }

    /** 按字段比较，保持原传输对象的值语义。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ResourceReference value)) return false;
        return Objects.equals(resourceId, value.resourceId)
            && Objects.equals(resourceCode, value.resourceCode)
            && Objects.equals(displayName, value.displayName)
            && Objects.equals(channel, value.channel)
            && Objects.equals(resourceType, value.resourceType)
            && Objects.equals(status, value.status);
    }

    @Override
    public int hashCode() { return Objects.hash(resourceId, resourceCode, displayName, channel, resourceType, status); }
}
