package com.paytech.platform.support.auto.ext.http.operations.management;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.operations.OperationsManagementApi;
import com.paytech.platform.support.api.operations.request.CreateRoleRequestBody;
import com.paytech.platform.support.api.operations.request.DeleteRoleRequestBody;
import com.paytech.platform.support.api.operations.request.RoleEmployeeCountsRequestBody;
import com.paytech.platform.support.api.operations.request.UpdateRoleRequestBody;
import com.paytech.platform.support.api.operations.response.PermissionCatalogResponseBody;
import com.paytech.platform.support.api.operations.response.RoleEmployeeCountsResponseBody;
import com.paytech.platform.support.api.operations.response.RoleResourcesResponseBody;
import com.paytech.platform.support.api.operations.response.RoleResponseBody;
import com.paytech.platform.support.api.operations.response.RoleSummary;
import com.paytech.platform.support.test.base.OperationsApiTestBase;
import com.paytech.platform.support.test.builder.OperationsCatalog;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.mocker.local.ConfigurationMocker;

import tools.jackson.core.type.TypeReference;

/**
 * Operations 角色：列表、人数、详情、创建、修改、删除，以及可授予权限与资源目录；系统角色只读且授予不得超出操作者上限。
 *
 * @see OperationsManagementApi#roles(String, String, Integer, Integer)
 * @see OperationsManagementApi#roleEmployeeCounts(String, String, RoleEmployeeCountsRequestBody)
 * @see OperationsManagementApi#role(String, String, String)
 * @see OperationsManagementApi#createRole(String, String, CreateRoleRequestBody)
 * @see OperationsManagementApi#updateRole(String, String, String, UpdateRoleRequestBody)
 * @see OperationsManagementApi#deleteRole(String, String, String, DeleteRoleRequestBody)
 * @see OperationsManagementApi#permissions(String, String)
 * @see OperationsManagementApi#roleResources(String, String)
 */
class OperationsRoleManagementTest extends OperationsApiTestBase {

    private static final String ROLES = "/operations/v1/roles";

    @Autowired
    private ConfigurationMocker configuration;

    private Operator admin;

    /** 正式目录的权限引用与一位平台管理员。 */
    @BeforeEach
    void setUpClassMocks() {
        configuration.permissionReferences();
        admin = operationsMocker.operator(OperatorBuilder.administrator());
    }

    /** 列表包含两个内置平台角色与内部租户自定义角色，按编码排序。 */
    @Test
    void test_listRoles() {
        operationsMocker.customRole("AUDITOR", "OPERATIONS_AUDIT_READ");

        var page = get(ROLES + "?page=1&size=10", admin).assertOk()
            .as(new TypeReference<PagingResponseBody<RoleSummary>>() {});

        assertEquals(3, page.total());
        assertEquals(List.of("AUDITOR", "OPERATIONS_VIEWER", "PLATFORM_ADMIN"),
            page.items().stream().map(RoleSummary::getRoleCode).toList());
        assertTrue(page.items().get(2).getSystemRole());
    }

    /** 创建自定义角色：内部租户范围，保存规范化后的权限集合。 */
    @Test
    void test_createRole() {
        var role = createRole("SUPPORT_AGENT", " 客服 ", "OPERATIONS_ROLE_READ", "OPERATIONS_ACCOUNT_READ").assertOk()
            .as(RoleResponseBody.class);

        long roleId = Long.parseLong(role.getRoleId());
        Map<String, Object> row = operationsData.role(roleId);
        assertAll(() -> assertEquals("客服", role.getRoleName()), () -> assertFalse(role.getSystemRole()),
            () -> assertEquals(1, role.getVersion()),
            () -> assertEquals(List.of(id("OPERATIONS_ACCOUNT_READ"), id("OPERATIONS_ROLE_READ")), role.getPermissionIds()),
            () -> assertEquals("TENANT", row.get("tenant_scope")),
            () -> assertEquals(Long.toString(OperationsCatalog.INTERNAL_TENANT_ID), row.get("scope_key")),
            () -> assertEquals(2, operationsData.rolePermissions(roleId).size()));
        auditChecker.assertEvent(requestId, "OPERATIONS_ROLE_CREATE", "SUCCEEDED", admin.actor(), "ROLE",
            role.getRoleId());
    }

    /** 角色编码重复（含系统保留编码）、权限重复、超出授予上限或停用权限时拒绝。 */
    @Test
    void test_createRoleRejected() {
        createRole("SUPPORT_AGENT", "客服", "OPERATIONS_ROLE_READ").assertOk();
        createRole("SUPPORT_AGENT", "客服二", "OPERATIONS_ROLE_READ").assertProblem(400, "BUSINESS_CHECK_FAILED");
        createRole("PLATFORM_ADMIN", "伪管理员", "OPERATIONS_ROLE_READ").assertProblem(400, "BUSINESS_CHECK_FAILED");
        post(ROLES, admin, new CreateRoleRequestBody("DUP", "重复", List.of(id("OPERATIONS_ROLE_READ"),
            id("OPERATIONS_ROLE_READ")), "r")).assertProblem(400, "INVALID_REQUEST");
        post(ROLES, admin, new CreateRoleRequestBody("lower", "格式", List.of(), "r"))
            .assertProblem(422, "VALIDATION_ERROR").assertFieldError("roleCode");
        operationsData.updatePermissionStatus(OperationsCatalog.permission("OPERATIONS_AUDIT_READ").id(), "INACTIVE");
        createRole("AUDITOR", "审计", "OPERATIONS_AUDIT_READ").assertProblem(400, "BUSINESS_CHECK_FAILED");

        long creatorRole = operationsMocker.customRole("ROLE_CREATOR", "OPERATIONS_ROLE_CREATE");
        Operator creator = operationsMocker.operator(OperatorBuilder.administrator().roles(creatorRole));
        post(ROLES, creator, new CreateRoleRequestBody("ESCALATE", "越权", List.of(id("GATEWAY_CONFIG_MANAGE")), "r"))
            .assertProblem(403, "OPERATIONS_PERMISSION_DENIED");
        post(ROLES, creator, new CreateRoleRequestBody("SELF_COPY", "同权", List.of(id("OPERATIONS_ROLE_CREATE")), "r"))
            .assertOk();
    }

    /** 修改名称与权限集合：移除的授权撤销、新增的授权插入，版本递增。 */
    @Test
    void test_updateRole() {
        long roleId = operationsMocker.customRole("SUPPORT_AGENT", "OPERATIONS_ROLE_READ", "OPERATIONS_ACCOUNT_READ");

        var role = put(ROLES + "/" + roleId, admin, new UpdateRoleRequestBody("新名称",
            List.of(id("OPERATIONS_ACCOUNT_READ"), id("OPERATIONS_AUDIT_READ")), 1, "调整")).assertOk()
            .as(RoleResponseBody.class);

        assertAll(() -> assertEquals(2, role.getVersion()), () -> assertEquals("新名称", role.getRoleName()),
            () -> assertEquals(List.of(id("OPERATIONS_ACCOUNT_READ"), id("OPERATIONS_AUDIT_READ")),
                role.getPermissionIds()));
        var grants = operationsData.rolePermissions(roleId);
        assertEquals("REVOKED", status(grants, "OPERATIONS_ROLE_READ"));
        assertEquals("ACTIVE", status(grants, "OPERATIONS_ACCOUNT_READ"));
        assertEquals("ACTIVE", status(grants, "OPERATIONS_AUDIT_READ"));
        auditChecker.assertEvent(requestId, "OPERATIONS_ROLE_UPDATE", "SUCCEEDED", admin.actor(), "ROLE",
            Long.toString(roleId));
    }

    /** 系统角色不可修改；旧版本拒绝；不存在的角色拒绝。 */
    @Test
    void test_updateRoleRejected() {
        long roleId = operationsMocker.customRole("SUPPORT_AGENT", "OPERATIONS_ROLE_READ");
        put(ROLES + "/" + OperationsCatalog.VIEWER_ROLE_ID, admin, new UpdateRoleRequestBody("改", List.of(), 1, "r"))
            .assertProblem(400, "BUSINESS_CHECK_FAILED");
        put(ROLES + "/" + roleId, admin, new UpdateRoleRequestBody("改", List.of(), 7, "r"))
            .assertProblem(409, "CONCURRENT_REQUEST");
        put(ROLES + "/123", admin, new UpdateRoleRequestBody("改", List.of(), 1, "r"))
            .assertProblem(400, "BUSINESS_CHECK_FAILED");

        assertEquals(1, ((Number) operationsData.role(roleId).get("version")).intValue());
    }

    /** 删除没有关联人员的自定义角色：角色停用、授权全部撤销。 */
    @Test
    void test_deleteRole() {
        long roleId = operationsMocker.customRole("SUPPORT_AGENT", "OPERATIONS_ROLE_READ");

        call(HttpMethod.DELETE, ROLES + "/" + roleId, headers(admin), new DeleteRoleRequestBody(1, "下线"))
            .assertNoContent(204);

        assertEquals("INACTIVE", operationsData.role(roleId).get("status"));
        assertEquals("REVOKED", operationsData.rolePermissions(roleId).getFirst().get("status"));
        auditChecker.assertEvent(requestId, "OPERATIONS_ROLE_DELETE", "SUCCEEDED", admin.actor(), "ROLE",
            Long.toString(roleId));
    }

    /** 仍有人员或为系统角色时不可删除。 */
    @Test
    void test_deleteRoleRejected() {
        long roleId = operationsMocker.customRole("SUPPORT_AGENT", "OPERATIONS_ROLE_READ");
        operationsMocker.operator(OperatorBuilder.administrator().roles(roleId));

        call(HttpMethod.DELETE, ROLES + "/" + roleId, headers(admin), new DeleteRoleRequestBody(1, "r"))
            .assertProblem(400, "BUSINESS_CHECK_FAILED");
        call(HttpMethod.DELETE, ROLES + "/" + OperationsCatalog.ADMIN_ROLE_ID, headers(admin),
            new DeleteRoleRequestBody(1, "r")).assertProblem(400, "BUSINESS_CHECK_FAILED");

        assertEquals("ACTIVE", operationsData.role(roleId).get("status"));
    }

    /** 角色详情需要修改权限，返回当前授权。 */
    @Test
    void test_roleDetail() {
        long roleId = operationsMocker.customRole("SUPPORT_AGENT", "OPERATIONS_ROLE_READ");
        Operator viewer = operationsMocker.operator(OperatorBuilder.viewer());

        var role = get(ROLES + "/" + roleId, admin).assertOk().as(RoleResponseBody.class);

        assertEquals(List.of(id("OPERATIONS_ROLE_READ")), role.getPermissionIds());
        get(ROLES + "/" + roleId, viewer).assertProblem(403, "OPERATIONS_PERMISSION_DENIED");
        get(ROLES + "/abc", admin).assertProblem(400, "INVALID_REQUEST");
    }

    /** 人数只统计内部租户有效授权，未知或重复角色拒绝。 */
    @Test
    void test_roleEmployeeCounts() {
        long roleId = operationsMocker.customRole("SUPPORT_AGENT", "OPERATIONS_ROLE_READ");
        operationsMocker.operator(OperatorBuilder.administrator().roles(roleId));
        operationsMocker.operator(OperatorBuilder.administrator().roles(roleId).memberStatus("REVOKED"));

        var counts = post(ROLES + "/employee-counts", admin, new RoleEmployeeCountsRequestBody(
            List.of(Long.toString(roleId), Long.toString(OperationsCatalog.ADMIN_ROLE_ID)))).assertOk()
            .as(RoleEmployeeCountsResponseBody.class);

        assertEquals(List.of(1L, 1L), counts.getItems().stream().map(count -> count.getEmployeeCount()).toList());
        post(ROLES + "/employee-counts", admin, new RoleEmployeeCountsRequestBody(List.of("123")))
            .assertProblem(400, "BUSINESS_CHECK_FAILED");
        post(ROLES + "/employee-counts", admin, new RoleEmployeeCountsRequestBody(
            List.of(Long.toString(roleId), Long.toString(roleId)))).assertProblem(400, "INVALID_REQUEST");
    }

    /** 权限目录：管理员看到全部被引用的权限；受限操作者只看到自己持有的。 */
    @Test
    void test_grantablePermissions() {
        long creatorRole = operationsMocker.customRole("ROLE_CREATOR", "OPERATIONS_ROLE_CREATE", "OPERATIONS_ROLE_READ");
        Operator creator = operationsMocker.operator(OperatorBuilder.administrator().roles(creatorRole));

        var all = get("/operations/v1/role-permissions", admin).assertOk().as(PermissionCatalogResponseBody.class);
        var own = get("/operations/v1/role-permissions", creator).assertOk().as(PermissionCatalogResponseBody.class);

        assertEquals(OperationsCatalog.PERMISSIONS.size(), all.getPermissions().size());
        assertEquals(List.of("OPERATIONS_ROLE_READ", "OPERATIONS_ROLE_CREATE"),
            own.getPermissions().stream().map(permission -> permission.getPermissionCode()).toList());
        get("/operations/v1/role-permissions", operationsMocker.operator(OperatorBuilder.viewer()))
            .assertProblem(403, "OPERATIONS_PERMISSION_DENIED");
    }

    /** 资源目录：管理员看到所有绑定可授予权限的资源；受限操作者只看到自己可见的资源。 */
    @Test
    void test_grantableResources() {
        long system = configuration.resource("OPERATIONS_SYSTEM", "MENU", null, "OPERATIONS_SYSTEM_READ", 10, "ACTIVE");
        configuration.resource("OPERATIONS_ROLES", "MENU", system, "OPERATIONS_ROLE_READ", 20, "ACTIVE");
        configuration.resource("OPERATIONS_ROLE_CREATE", "BUTTON", system, "OPERATIONS_ROLE_CREATE", 30, "DISABLED");
        long creatorRole = operationsMocker.customRole("ROLE_CREATOR", "OPERATIONS_ROLE_CREATE", "OPERATIONS_ROLE_READ");
        Operator creator = operationsMocker.operator(OperatorBuilder.administrator().roles(creatorRole));

        var all = get("/operations/v1/role-resources", admin).assertOk().as(RoleResourcesResponseBody.class);
        var own = get("/operations/v1/role-resources", creator).assertOk().as(RoleResourcesResponseBody.class);

        assertEquals(java.util.Set.of("OPERATIONS_SYSTEM", "OPERATIONS_ROLES", "OPERATIONS_ROLE_CREATE"),
            all.getResources().stream().map(resource -> resource.getResourceCode()).collect(java.util.stream.Collectors.toSet()),
            "管理员看到全部资源（含停用资源），按资源标识排序");
        assertEquals(3, all.getResources().size());
        assertEquals(List.of("OPERATIONS_ROLES"),
            own.getResources().stream().map(resource -> resource.getResourceCode()).toList());
    }

    private com.paytech.platform.support.test.checker.PlatformResponseChecker createRole(String code, String name,
        String... permissions) {
        return post(ROLES, admin, new CreateRoleRequestBody(code, name,
            java.util.Arrays.stream(permissions).map(OperationsRoleManagementTest::id).toList(), "新增"));
    }

    private static String id(String permissionCode) {
        return Long.toString(OperationsCatalog.permission(permissionCode).id());
    }

    private static String status(List<Map<String, Object>> grants, String permissionCode) {
        long permissionId = OperationsCatalog.permission(permissionCode).id();
        return grants.stream().filter(row -> ((Number) row.get("permission_id")).longValue() == permissionId)
            .map(row -> String.valueOf(row.get("status"))).findFirst().orElseThrow();
    }
}
