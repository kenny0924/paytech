package com.paytech.operations.console.bff.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.operations.console.bff.service.RolePermissionTree;
import com.paytech.operations.console.bff.service.model.RoleModels.GrantablePermission;
import com.paytech.operations.console.bff.service.model.RoleModels.PermissionNode;
import com.paytech.operations.console.bff.service.model.RoleModels.PermissionResource;
import org.junit.jupiter.api.Test;

/** 纯页面组装：资源树不是权限继承关系，权限标识和节点标识不能混用。 */
class RolePermissionTreeAutoTest {
    @Test
    void test_directoryPermissionIsIndependentAndDuplicateBindingsKeepDistinctNodes() {
        var nodes = RolePermissionTree.assemble(List.of(permission("p1"), permission("p2")), List.of(
            resource("child-b", "root", "p2", 20), resource("root", null, "p1", 10),
            resource("child-a", "root", "p2", 10)));

        assertEquals(1, nodes.size());
        assertEquals("p1", nodes.getFirst().permissionId());
        assertEquals(List.of("resource:child-a", "resource:child-b"), nodes.getFirst().children().stream()
            .map(PermissionNode::nodeId).toList());
        assertTrue(nodes.getFirst().children().stream().allMatch(node -> node.permissionId().equals("p2")));
    }

    @Test
    void test_missingUnauthorizedParentIsNotRestoredAndUnboundPermissionsRemainSelectable() {
        var nodes = RolePermissionTree.assemble(List.of(permission("read"), permission("unbound")), List.of(
            resource("hidden", null, "not-grantable", 10), resource("read", "hidden", "read", 20)));

        assertEquals(List.of("resource:read", "group:other"), nodes.stream().map(PermissionNode::nodeId).toList());
        assertEquals(new PermissionNode("permission:unbound", "CODE_unbound", "unbound", List.of()),
            nodes.getLast().children().getFirst());
        assertEquals(null, nodes.getLast().permissionId());
        assertTrue(nodes.stream().noneMatch(node -> node.displayName().contains("hidden")));
    }

    @Test
    void test_emptyGrantableScopeProducesNoNodes() {
        assertEquals(List.of(), RolePermissionTree.assemble(List.of(), List.of(resource("hidden", null, "hidden", 10))));
    }

    @Test
    void test_duplicateCatalogOrResourceIdentitiesCannotProduceAmbiguousSelections() {
        assertThrows(ErrorException.class, () -> RolePermissionTree.assemble(
            List.of(permission("p"), permission("p")), List.of()));
        assertThrows(ErrorException.class, () -> RolePermissionTree.assemble(List.of(permission("p")),
            List.of(resource("same", null, "p", 10), resource("same", null, "p", 20))));
    }

    @Test
    void test_cyclesFailRatherThanPublishingAnIncompletePermissionTree() {
        assertThrows(ErrorException.class, () -> RolePermissionTree.assemble(List.of(permission("p")),
            List.of(resource("a", "b", "p", 10), resource("b", "a", "p", 20))));
        assertThrows(ErrorException.class, () -> RolePermissionTree.assemble(List.of(permission("p")),
            List.of(resource("self", "self", "p", 10))));
    }

    private GrantablePermission permission(String id) {
        return new GrantablePermission(id, "CODE_" + id);
    }

    private PermissionResource resource(String id, String parent, String permission, int order) {
        return new PermissionResource(id, parent, "资源 " + id, permission, order);
    }
}
