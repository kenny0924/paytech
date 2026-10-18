package com.paytech.platform.support.api.consolemanagement.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 前端路由从组件派生；不接受客户端任意路径。 */
public final class CreateConsoleResourceRequestBody {
    /** 稳定资源编码。 */
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,127}")
    private final String resourceCode;

    /** 资源类型 MENU 或 BUTTON。 */
    @NotBlank @Pattern(regexp = "MENU|BUTTON")
    private final String resourceType;

    /** 展示名称。 */
    @NotBlank @Size(max = 255)
    private final String displayName;

    /** 父资源主键，根目录为空。 */
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private final String parentResourceId;

    /** 已部署组件键，目录和按钮可空。 */
    @Size(max = 128)
    private final String componentKey;

    /** 可见性权限主键。 */
    @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}")
    private final String permissionId;

    /** 同级排序，范围 0..999999。 */
    @Min(0) @Max(999999)
    private final int sortOrder;

    /** 操作原因或维护限制说明。 */
    @NotBlank @Size(max = 500)
    private final String reason;

    /** 绑定协议字段，保留原有 JSON 名称和校验约束。 */
    @JsonCreator
    public CreateConsoleResourceRequestBody(@JsonProperty(value = "resourceCode") String resourceCode,
        @JsonProperty(value = "resourceType") String resourceType,
        @JsonProperty(value = "displayName") String displayName,
        @JsonProperty(value = "parentResourceId") String parentResourceId,
        @JsonProperty(value = "componentKey") String componentKey,
        @JsonProperty(value = "permissionId") String permissionId,
        @JsonProperty(value = "sortOrder", required = true) int sortOrder,
        @JsonProperty(value = "reason") String reason) {
        this.resourceCode = resourceCode;
        this.resourceType = resourceType;
        this.displayName = displayName;
        this.parentResourceId = parentResourceId;
        this.componentKey = componentKey;
        this.permissionId = permissionId;
        this.sortOrder = sortOrder;
        this.reason = reason;
    }

    /** 稳定资源编码。 */
    public String getResourceCode() {
        return resourceCode;
    }

    /** 资源类型 MENU 或 BUTTON。 */
    public String getResourceType() {
        return resourceType;
    }

    /** 展示名称。 */
    public String getDisplayName() {
        return displayName;
    }

    /** 父资源主键，根目录为空。 */
    public String getParentResourceId() {
        return parentResourceId;
    }

    /** 已部署组件键，目录和按钮可空。 */
    public String getComponentKey() {
        return componentKey;
    }

    /** 可见性权限主键。 */
    public String getPermissionId() {
        return permissionId;
    }

    /** 同级排序，范围 0..999999。 */
    public int getSortOrder() {
        return sortOrder;
    }

    /** 操作原因或维护限制说明。 */
    public String getReason() {
        return reason;
    }
}
