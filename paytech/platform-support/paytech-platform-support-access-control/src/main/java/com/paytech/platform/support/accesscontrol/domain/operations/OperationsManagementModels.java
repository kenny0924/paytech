package com.paytech.platform.support.accesscontrol.domain.operations;

/** Operations 管理能力的业务命令与结果；HTTP 适配由 Web Converter 完成。 */
public final class OperationsManagementModels {
    /** 仅作为领域查询类型命名空间。 */
    private OperationsManagementModels() {}

    /** Operations PermissionSummary 查询投影，不跨越持久化边界。 */
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class PermissionSummary {
        /** 权限 ID，十进制字符串。 */
        private final String permissionId;

        /** 稳定权限业务代码。 */
        private final String permissionCode;

        /** 资源类型 Code。 */
        private final String resourceType;

        /** 操作代码，查询时可空；非空时精确匹配。 */
        private final String actionCode;

        /** 权限风险等级 Code。 */
        private final String riskLevel;

    }

    /** Operations RoleResource 查询投影，不跨越持久化边界。 */
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class RoleResource {
        /** 资源 ID；审计筛选时可空，非空时精确匹配。 */
        private final String resourceId;

        /** 稳定资源业务代码。 */
        private final String resourceCode;

        /** 资源类型 Code。 */
        private final String resourceType;

        /** 父资源 ID，根资源时为空。 */
        private final String parentResourceId;

        /** 资源展示名称。 */
        private final String displayName;

        /** 前端路由；无路由的目录或按钮可空。 */
        private final String frontendRoute;

        /** 已部署组件键；目录或按钮可空。 */
        private final String componentKey;

        /** 权限 ID，十进制字符串。 */
        private final String permissionId;

        /** 同级排序值，升序展示。 */
        private final int sortOrder;

    }

    /** Operations RoleSummary 查询投影，不跨越持久化边界。 */
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class RoleSummary {
        /** 角色 ID，十进制字符串。 */
        private final String roleId;

        /** 不可变角色代码，1 至 64 个大写字母、数字或下划线，首字符为字母。 */
        private final String roleCode;

        /** 角色名称，去除首尾空白后为 1 至 255 个字符。 */
        private final String roleName;

        /** 是否为禁止普通管理操作修改的系统角色。 */
        private final boolean systemRole;

        /** 当前账号或角色聚合版本。 */
        private final int version;

    }

    /** Operations RoleEmployeeCount 查询投影，不跨越持久化边界。 */
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class RoleEmployeeCount {
        /** 角色 ID，十进制字符串。 */
        private final String roleId;

        /** 关联的 Operations 人员数量，包含停用账号、暂停成员及未来生效的有效关联；最小为 0。 */
        private final long employeeCount;

    }

}
