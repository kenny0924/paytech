package com.paytech.operations.console.bff.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.operations.console.bff.service.model.RoleModels.GrantablePermission;
import com.paytech.operations.console.bff.service.model.RoleModels.PermissionNode;
import com.paytech.operations.console.bff.service.model.RoleModels.PermissionResource;

/** 将已授权权限及资源绑定转成页面树，不推导或补授任何权限。 @author Codex */
public final class RolePermissionTree {
    private RolePermissionTree() {
    }

    public static List<PermissionNode> assemble(List<GrantablePermission> permissions, List<PermissionResource> resources) {
        Map<String, GrantablePermission> catalog = permissionCatalog(permissions);
        Map<String, PermissionResource> visible = visibleResources(resources, catalog.keySet());
        Map<String, List<PermissionResource>> children = childrenByParent(visible);
        List<PermissionNode> roots = new ArrayList<>(nodes(children.getOrDefault(null, List.of()), children));
        Set<String> boundPermissions = new HashSet<>();
        visible.values().forEach(resource -> boundPermissions.add(resource.permissionId()));
        List<PermissionNode> unbound = catalog.values().stream()
            .filter(permission -> !boundPermissions.contains(permission.permissionId()))
            .sorted(Comparator.comparing(GrantablePermission::permissionCode).thenComparing(GrantablePermission::permissionId))
            .map(permission -> new PermissionNode("permission:" + permission.permissionId(), permission.permissionCode(),
                permission.permissionId(), List.of()))
            .toList();
        if (!unbound.isEmpty()) roots.add(new PermissionNode("group:other", "其他权限", null, unbound));
        return List.copyOf(roots);
    }

    private static Map<String, GrantablePermission> permissionCatalog(List<GrantablePermission> permissions) {
        require(permissions != null);
        Map<String, GrantablePermission> result = new LinkedHashMap<>();
        for (GrantablePermission permission : permissions) {
            require(permission != null && hasText(permission.permissionId()) && hasText(permission.permissionCode()));
            require(result.putIfAbsent(permission.permissionId(), permission) == null);
        }
        return result;
    }

    private static Map<String, PermissionResource> visibleResources(List<PermissionResource> resources, Set<String> permissions) {
        require(resources != null);
        Map<String, PermissionResource> result = new LinkedHashMap<>();
        for (PermissionResource resource : resources) {
            require(resource != null && hasText(resource.resourceId()) && hasText(resource.permissionId()));
            if (!permissions.contains(resource.permissionId())) continue;
            require(hasText(resource.displayName()));
            require(result.putIfAbsent(resource.resourceId(), resource) == null);
        }
        return result;
    }

    private static Map<String, List<PermissionResource>> childrenByParent(Map<String, PermissionResource> resources) {
        Map<String, List<PermissionResource>> children = new HashMap<>();
        for (PermissionResource resource : resources.values()) {
            rejectCycle(resource, resources);
            // 只展示返回范围内的资源；缺失父节点时提升当前已授权节点，不补父级名称或权限。
            String parent = resources.containsKey(resource.parentResourceId()) ? resource.parentResourceId() : null;
            children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(resource);
        }
        Comparator<PermissionResource> order = Comparator.comparingInt(PermissionResource::sortOrder)
            .thenComparing(PermissionResource::resourceId);
        children.values().forEach(siblings -> siblings.sort(order));
        return children;
    }

    private static void rejectCycle(PermissionResource resource, Map<String, PermissionResource> resources) {
        Set<String> visited = new HashSet<>();
        PermissionResource current = resource;
        while (current != null) {
            require(visited.add(current.resourceId()));
            current = resources.get(current.parentResourceId());
        }
    }

    private static List<PermissionNode> nodes(List<PermissionResource> resources,
                                             Map<String, List<PermissionResource>> children) {
        return resources.stream().map(resource -> new PermissionNode("resource:" + resource.resourceId(),
            resource.displayName(), resource.permissionId(), nodes(children.getOrDefault(resource.resourceId(), List.of()), children)))
            .toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean valid) {
        if (!valid) throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
    }
}
