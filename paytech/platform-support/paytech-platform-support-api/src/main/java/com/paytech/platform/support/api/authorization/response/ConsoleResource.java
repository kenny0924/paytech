package com.paytech.platform.support.api.authorization.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 当前身份可见的控制台菜单或按钮。 */
public final class ConsoleResource {
    /** 资源主键，以十进制字符串传输。 */
    private final String resourceId;

    /** 稳定资源代码。 */
    private final String resourceCode;

    /** 资源类型代码，MENU 或 BUTTON。 */
    private final String resourceType;

    /** 父资源主键；根节点为空。 */
    private final String parentResourceId;

    /** 展示名称。 */
    private final String displayName;

    /** 前端固定路径；不跳转的目录或按钮可空。 */
    private final String frontendRoute;

    /** 前端预注册组件键，可空。 */
    private final String componentKey;

    /** 非负排序号，同序号按资源主键排序。 */
    private final int sortOrder;

    /** 绑定已有导航协议字段，保留可空父节点与页面信息。 */
    @JsonCreator
    public ConsoleResource(@JsonProperty("resourceId") String resourceId,
        @JsonProperty("resourceCode") String resourceCode,
        @JsonProperty("resourceType") String resourceType,
        @JsonProperty("parentResourceId") String parentResourceId,
        @JsonProperty("displayName") String displayName,
        @JsonProperty("frontendRoute") String frontendRoute,
        @JsonProperty("componentKey") String componentKey,
        @JsonProperty("sortOrder") int sortOrder) {
        this.resourceId = resourceId;
        this.resourceCode = resourceCode;
        this.resourceType = resourceType;
        this.parentResourceId = parentResourceId;
        this.displayName = displayName;
        this.frontendRoute = frontendRoute;
        this.componentKey = componentKey;
        this.sortOrder = sortOrder;
    }

    /** 返回资源主键。 */
    public String getResourceId() {
        return resourceId;
    }

    /** 返回稳定资源代码。 */
    public String getResourceCode() {
        return resourceCode;
    }

    /** 返回资源类型代码。 */
    public String getResourceType() {
        return resourceType;
    }

    /** 返回父资源主键，根节点为空。 */
    public String getParentResourceId() {
        return parentResourceId;
    }

    /** 返回展示名称。 */
    public String getDisplayName() {
        return displayName;
    }

    /** 返回前端固定路径，可空。 */
    public String getFrontendRoute() {
        return frontendRoute;
    }

    /** 返回前端组件键，可空。 */
    public String getComponentKey() {
        return componentKey;
    }

    /** 返回资源排序号。 */
    public int getSortOrder() {
        return sortOrder;
    }
}
