package com.paytech.platform.support.api.consolemanagement.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 更新保留资源编码、类型、Channel 及状态。 */
public final class UpdateConsoleResourceRequestBody {
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

    /** 调用方期望的当前版本。 */
    @Positive
    private final int expectedVersion;

    /** 操作原因或维护限制说明。 */
    @NotBlank @Size(max = 500)
    private final String reason;

    /** 绑定协议字段，保留原有 JSON 名称和校验约束。 */
    @JsonCreator
    public UpdateConsoleResourceRequestBody(@JsonProperty(value = "displayName") String displayName,
        @JsonProperty(value = "parentResourceId") String parentResourceId,
        @JsonProperty(value = "componentKey") String componentKey,
        @JsonProperty(value = "permissionId") String permissionId,
        @JsonProperty(value = "sortOrder", required = true) int sortOrder,
        @JsonProperty(value = "expectedVersion", required = true) int expectedVersion,
        @JsonProperty(value = "reason") String reason) {
        this.displayName = displayName;
        this.parentResourceId = parentResourceId;
        this.componentKey = componentKey;
        this.permissionId = permissionId;
        this.sortOrder = sortOrder;
        this.expectedVersion = expectedVersion;
        this.reason = reason;
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

    /** 调用方期望的当前版本。 */
    public int getExpectedVersion() {
        return expectedVersion;
    }

    /** 操作原因或维护限制说明。 */
    public String getReason() {
        return reason;
    }
}
