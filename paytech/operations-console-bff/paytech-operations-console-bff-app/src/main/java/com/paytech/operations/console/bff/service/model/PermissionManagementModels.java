package com.paytech.operations.console.bff.service.model;
import java.time.Instant;
import java.util.List;
import jakarta.validation.constraints.*;
import com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ManagementProtection;
/** 当前配置管理的公开传输模型。 */
public final class PermissionManagementModels {
    private PermissionManagementModels() {}

    /** 权限目录不是当前操作者可转授权限集。 */
    public record PermissionSummary(String permissionId, String permissionCode, String resourceType, String actionCode,
        String riskLevel, String status, int version) {}

    /** 权限详情包含当前配置引用计数，不包含人员身份。 */
    public record PermissionResponseBody(String permissionId, String permissionCode, String resourceType,
        String actionCode, String riskLevel, String status, int version, Instant createTime, Instant updateTime,
        ManagementProtection managementProtection, ReferenceCounts referenceCounts) {}

    /** 按配置关联计数；Role 仅包含未撤销关联。 */
    public record ReferenceCounts(long resource, long route, long role) {}

    /** 选择器仅返回全局 ACTIVE 身份，不自动授予创建者。 */
    public record PermissionOption(String permissionId, String permissionCode, String resourceType, String actionCode,
        String riskLevel) {}

    /** 引用种类由请求 kind 决定，每页保持单一有类型的结果。 */
    @com.fasterxml.jackson.annotation.JsonTypeInfo(use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.DEDUCTION)
    @com.fasterxml.jackson.annotation.JsonSubTypes({
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(ResourceReference.class),
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(RouteReference.class),
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(RoleReference.class)
    })
    public sealed interface PermissionReference permits ResourceReference, RouteReference, RoleReference {}

    /** 资源引用包含其他 Channel，不能跨 Channel 编辑。 */
    public record ResourceReference(String resourceId, String resourceCode, String displayName, String channel,
        String resourceType, String status) implements PermissionReference {}

    /** 当前 Route 引用，不代表已经被 Gateway 加载。 */
    public record RouteReference(String routeCode, String channel, String httpMethod, String externalPathTemplate,
        String targetServiceCode, String status) implements PermissionReference {}

    /** 不展开角色成员，不返回员工或凭据。 */
    public record RoleReference(String roleId, String roleCode, String roleName, String roleStatus, String grantStatus,
        String scopeKey) implements PermissionReference {}

    /** 新权限固定 INACTIVE，身份三元组创建后不可更改。 */
    public record CreatePermissionRequestBody(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,127}") String permissionCode,
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String resourceType,
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String actionCode,
        @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL") String riskLevel,
        @NotBlank @Size(max = 500) String reason) {}

    /** 本期只更新风险等级，不附加没有业务消费者的名称字段。 */
    public record UpdatePermissionRequestBody(@NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL") String riskLevel,
        @Positive int expectedVersion, @NotBlank @Size(max = 500) String reason) {}

    /** 停用保留原关联，重新启用恢复原有关联的授权作用。 */
    public record ChangePermissionStatusRequestBody(@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status,
        @Positive int expectedVersion, @NotBlank @Size(max = 500) String reason) {}
}
