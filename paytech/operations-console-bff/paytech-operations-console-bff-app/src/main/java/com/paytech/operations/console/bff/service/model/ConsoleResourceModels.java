package com.paytech.operations.console.bff.service.model;
import java.time.Instant;
import java.util.List;
import jakarta.validation.constraints.*;
import com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ManagementProtection;
/** 当前配置管理的公开传输模型。 */
public final class ConsoleResourceModels {
    private ConsoleResourceModels() {}

    /** 有界完整树，客户端筛选时保留祖先。 */
    public record ConsoleResourcesResponseBody(List<ConsoleResourceResponseBody> items) {}

    /** 资源身份、当前绑定与维护边界；不支持的旧组件仍可读取。 */
    public record ConsoleResourceResponseBody(String resourceId, String resourceCode, String channel,
        String resourceType, String parentResourceId, String displayName, String frontendRoute, String componentKey,
        String permissionId, String permissionCode, String permissionStatus, int sortOrder, String status, int version,
        ManagementProtection managementProtection, boolean supported, Instant createTime, Instant updateTime) {}

    /** 部署能力的身份由代码提供，资源实例由数据库管理。 */
    public record ConsoleResourceCapabilitiesResponseBody(List<ComponentCapability> components,
        List<String> resourceTypes, int maxDepth, int maxResources) {}

    /** 已部署页面和真实支持的按钮动作。 */
    public record ComponentCapability(String componentKey, String displayName, String frontendRoute,
        List<ActionCapability> actions) {}

    /** 动作代码必须与 Web 中的真实处理器一致。 */
    public record ActionCapability(String resourceCode, String displayName) {}

    /** 前端路由从组件派生；不接受客户端任意路径。 */
    public record CreateConsoleResourceRequestBody(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,127}") String resourceCode,
        @NotBlank @Pattern(regexp = "MENU|BUTTON") String resourceType,
        @NotBlank @Size(max = 255) String displayName,
        @Pattern(regexp = "[1-9][0-9]{0,18}") String parentResourceId,
        @Size(max = 128) String componentKey,
        @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String permissionId,
        @Min(0) @Max(999999) int sortOrder, @NotBlank @Size(max = 500) String reason) {}

    /** 更新保留资源编码、类型、Channel 及状态。 */
    public record UpdateConsoleResourceRequestBody(@NotBlank @Size(max = 255) String displayName,
        @Pattern(regexp = "[1-9][0-9]{0,18}") String parentResourceId,
        @Size(max = 128) String componentKey,
        @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String permissionId,
        @Min(0) @Max(999999) int sortOrder, @Positive int expectedVersion,
        @NotBlank @Size(max = 500) String reason) {}

    /** 停用父级保留子项本身状态与全部引用。 */
    public record ChangeConsoleResourceStatusRequestBody(@NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String status,
        @Positive int expectedVersion, @NotBlank @Size(max = 500) String reason) {}
}
