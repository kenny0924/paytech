package com.paytech.platform.support.accesscontrol.domain.configurationmanagement;

import java.util.*;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.Permission;
import com.paytech.platform.support.accesscontrol.domain.enums.PermissionStatus;
import com.paytech.platform.support.api.enums.OperationsConsoleResourceType;

import lombok.AllArgsConstructor;

/** 资源结构、部署能力与管理入口维护的纯规则；不查询数据库。 */
@Component
@AllArgsConstructor
public final class ConsoleResourcePolicy {
    private static final Set<String> MANAGEMENT_COMPONENTS = Set.of("OPERATIONS_ACCOUNTS", "OPERATIONS_ROLES",
        "OPERATIONS_AUDIT", "OPERATIONS_GATEWAY_CONFIGURATION", "OPERATIONS_CONSOLE_RESOURCES", "OPERATIONS_PERMISSIONS");
    private final ConsoleResourceCapabilities capabilities;

    /** 组件确定路径；目录没有路径，任意 URL 不能成为页面能力。 */
    public String route(String componentKey) {
        if (componentKey == null) {
            return null;
        }
        var component = capabilities.component(componentKey);
        require(component != null, "页面组件尚未部署或不再支持");
        return component.frontendRoute();
    }

    /** 历史未知组件/动作可读取并标注，不能假装已受支持。 */
    public boolean supported(ConsoleResourceDefinition resource, List<? extends ConsoleResourceDefinition> tree) {
        if (resource.getResourceType() == OperationsConsoleResourceType.MENU) {
            if (resource.getComponentKey() == null) {
                return resource.getFrontendRoute() == null;
            }
            var component = capabilities.component(resource.getComponentKey());
            return component != null && component.frontendRoute().equals(resource.getFrontendRoute());
        }
        if (resource.getComponentKey() != null || resource.getFrontendRoute() != null) {
            return false;
        }
        var parent = tree.stream().filter(r -> Objects.equals(r.getResourceId(), resource.getParentResourceId()))
            .findFirst().orElse(null);
        var component = parent == null ? null : capabilities.component(parent.getComponentKey());
        return component != null
            && component.actions().stream().anyMatch(a -> a.resourceCode().equals(resource.getResourceCode()));
    }

    /** 候选树校验包括所有祖先及移动后的后代深度，防止交叉挂载。 */
    public void validate(ConsoleResourceDefinition before, ConsoleResourceDefinition candidate,
        List<? extends ConsoleResourceDefinition> tree) {
        require(tree.size() <= ConsoleResourceCapabilities.MAX_RESOURCES, "资源数量超过当前完整树上限");
        require(supported(candidate, tree), "不支持的页面组件或按钮操作");
        var indexed = new HashMap<Long, ConsoleResourceDefinition>();
        tree.forEach(r -> indexed.put(r.getResourceId(), r));
        ConsoleResourceDefinition parent =
            candidate.getParentResourceId() == null ? null : indexed.get(candidate.getParentResourceId());
        require(candidate.getParentResourceId() == null || parent != null, "父资源不存在或不属于 Operations");
        require(parent == null || parent.getResourceType() == OperationsConsoleResourceType.MENU, "按钮不能作为父资源");
        boolean button = candidate.getResourceType() == OperationsConsoleResourceType.BUTTON;
        require(!button || parent != null && parent.getComponentKey() != null, "按钮必须属于页面菜单");
        require(parent == null || parent.getComponentKey() == null || button, "页面菜单只能包含按钮");
        var children =
            tree.stream().filter(r -> Objects.equals(r.getParentResourceId(), candidate.getResourceId())).toList();
        require(!button || children.isEmpty(), "按钮不能拥有子资源");
        if (before != null && !Objects.equals(before.getComponentKey(), candidate.getComponentKey())) {
            require(children.isEmpty(), "请先移动子项，再更换页面组件或改为目录");
        }
        for (ConsoleResourceDefinition resource : tree) {
            var ancestors = new HashSet<Long>();
            ConsoleResourceDefinition cursor = resource;
            while (cursor != null) {
                require(ancestors.add(cursor.getResourceId()), "资源树不能形成循环");
                require(ancestors.size() <= ConsoleResourceCapabilities.MAX_DEPTH, "资源树深度最多为 8 层");
                cursor = cursor.getParentResourceId() == null ? null : indexed.get(cursor.getParentResourceId());
            }
        }
    }

    /** 系统管理页面、按钮及其祖先组成当前保护闭包。 */
    public Set<Long> protectedIds(List<? extends ConsoleResourceDefinition> tree) {
        var indexed = new HashMap<Long, ConsoleResourceDefinition>();
        tree.forEach(r -> indexed.put(r.getResourceId(), r));
        var actions = new HashSet<String>();
        capabilities.current().components().stream().filter(c -> MANAGEMENT_COMPONENTS.contains(c.componentKey()))
            .forEach(c -> c.actions().forEach(a -> actions.add(a.resourceCode())));
        var result = new HashSet<Long>();
        for (ConsoleResourceDefinition resource : tree) {
            if (!MANAGEMENT_COMPONENTS.contains(Objects.toString(resource.getComponentKey(), ""))
                && !actions.contains(resource.getResourceCode())) {
                continue;
            }
            ConsoleResourceDefinition cursor = resource;
            while (cursor != null && result.add(cursor.getResourceId())) {
                cursor = cursor.getParentResourceId() == null ? null : indexed.get(cursor.getParentResourceId());
            }
        }
        return Set.copyOf(result);
    }

    /** UI 与直接写入接口共享同一结果。 */
    public ManagementProtection protection(ConsoleResourceDefinition resource,
        List<? extends ConsoleResourceDefinition> tree) {
        return protectedIds(tree).contains(resource.getResourceId())
            ? ManagementProtection.protectedFields("displayName", "sortOrder")
            : ManagementProtection.editable("displayName", "parentResourceId", "componentKey", "permissionId", "sortOrder");
    }

    /** 受保护资源仍能改名称和顺序，不能通过移动破坏管理链。 */
    public void requireEditable(ConsoleResourceDefinition before, ConsoleResourceDefinition after,
        List<? extends ConsoleResourceDefinition> tree) {
        if (before == null || !protectedIds(tree).contains(before.getResourceId())) {
            return;
        }
        require(
            Objects.equals(before.getParentResourceId(), after.getParentResourceId())
                && Objects.equals(before.getComponentKey(), after.getComponentKey())
                && before.getPermissionId() == after.getPermissionId() && before.getStatus() == after.getStatus(),
            "内置管理资源只允许修改名称和排序");
    }

    /** 新绑定权限必须启用，既有停用绑定允许原样保留。 */
    public void requireBinding(Permission permission, Long previousId) {
        require(permission != null && (permission.status() == PermissionStatus.ACTIVE
            || Objects.equals(previousId, permission.permissionId())), "新绑定的权限必须存在且启用");
    }

    /** 超出完整树上限时拒绝截断。 */
    public void requireTreeSize(int size) {
        require(size <= ConsoleResourceCapabilities.MAX_RESOURCES, "Operations 资源超过 2000 条");
    }

    /** 入口转换后仍保留原有类型及排序约束。 */
    public void requireValues(OperationsConsoleResourceType type, int sortOrder) {
        if (type == null || sortOrder < 0 || sortOrder > 999999) {
            throw new com.paytech.foundation.common.error.InvalidParameterException("资源类型或排序不合法");
        }
    }

    /** 业务结构失败使用统一业务错误。 */
    private void require(boolean valid, String message) {
        if (!valid) {
            throw new BizCheckFailException(message);
        }
    }
}
