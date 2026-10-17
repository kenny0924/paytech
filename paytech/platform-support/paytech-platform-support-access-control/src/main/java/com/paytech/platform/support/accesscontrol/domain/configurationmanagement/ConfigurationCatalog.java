package com.paytech.platform.support.accesscontrol.domain.configurationmanagement;

import java.util.List;

import com.paytech.platform.support.accesscontrol.domain.enums.OperationsPermissionRiskLevel;
import com.paytech.platform.support.accesscontrol.domain.enums.PermissionStatus;

/** 菜单与权限管理的 Owner 领域投影，不暴露本模块 DataObject。 */
public final class ConfigurationCatalog {
    private ConfigurationCatalog() {}

    /** 未撤销 Role 映射计数包含停用权限。 */
    public record ReferenceCounts(long resource, long route, long role) {}

    /** 空筛选为全部，keyword 按普通文字包含查询。 */
    public record PermissionFilter(String code, String resourceType, PermissionStatus status,
        OperationsPermissionRiskLevel riskLevel, String keyword) {}

    /** 引用分页只查询指定类别，不加载员工。 */
    public enum ReferenceKind {
        RESOURCE,

        ROUTE,

        ROLE
    }

    /** 当前引用的有限类型集合。 */
    public sealed interface Reference permits ResourceReference, RouteReference, RoleReference {}

    /** 资源引用不会返回任何人员信息。 */
    @lombok.Getter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.experimental.Accessors(fluent = true)
    public static final class ResourceReference implements Reference {
        /** 资源主键。 */
        private long resourceId;

        /** 稳定资源编码。 */
        private String resourceCode;

        /** 资源展示名称。 */
        private String displayName;

        /** 资源或路由所属 Channel。 */
        private String channel;

        /** 不可变资源类型编码。 */
        private String resourceType;

        /** 当前配置状态：ACTIVE 或 DISABLED。 */
        private String status;
    }

    /** Route 引用不声称已被所有 Gateway 加载。 */
    @lombok.Getter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.experimental.Accessors(fluent = true)
    public static final class RouteReference implements Reference {
        /** 稳定路由编码。 */
        private String routeCode;

        /** 资源或路由所属 Channel。 */
        private String channel;

        /** HTTP 请求方法。 */
        private String httpMethod;

        /** 外部路径模板。 */
        private String externalPathTemplate;

        /** 目标服务编码。 */
        private String targetServiceCode;

        /** 当前配置状态：ACTIVE 或 DISABLED。 */
        private String status;
    }

    /** 角色引用保留状态与 Scope，不展开员工。 */
    @lombok.Getter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.experimental.Accessors(fluent = true)
    public static final class RoleReference implements Reference {
        /** 角色主键。 */
        private long roleId;

        /** 稳定角色编码。 */
        private String roleCode;

        /** 角色名称。 */
        private String roleName;

        /** 当前角色状态。 */
        private String roleStatus;

        /** 角色与权限的关联状态。 */
        private String grantStatus;

        /** 角色所属授权范围。 */
        private String scopeKey;
    }

    /** 页面能力来自随制品交付的显式清单。 */
    public record Capabilities(List<Component> components, List<String> resourceTypes, int maxDepth, int maxResources) {}

    /** 已部署页面及其真实支持动作。 */
    public record Component(String componentKey, String displayName, String frontendRoute, List<Action> actions) {}

    /** 一个动作编码只可有一个资源实例。 */
    public record Action(String resourceCode, String displayName) {}

}
