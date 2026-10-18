package com.paytech.platform.support.auto.ext.http.operations.authorization;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.platform.support.api.authorization.OperationsAuthorizationApi;
import com.paytech.platform.support.api.authorization.request.AuthorizationRequestBody;
import com.paytech.platform.support.api.authorization.response.AuthorizationDecisionResponseBody;
import com.paytech.platform.support.api.authorization.response.NavigationResponseBody;
import com.paytech.platform.support.test.base.OperationsApiTestBase;
import com.paytech.platform.support.test.builder.GatewayRouteBuilder;
import com.paytech.platform.support.test.builder.OperationsCatalog;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.dal.ConfigurationTestMapper;
import com.paytech.platform.support.test.mocker.local.ConfigurationMocker;

/**
 * PLT-I02 路由授权与 PLT-I21 控制台导航：按当前库中路由、权限、角色与会话事实实时决策。
 *
 * @see OperationsAuthorizationApi#authorize(String, String, AuthorizationRequestBody)
 * @see OperationsAuthorizationApi#navigation(String, String)
 */
class OperationsAuthorizationTest extends OperationsApiTestBase {

    private static final String AUTHORIZE = "/authorization/v1/operations/authorize";
    private static final String NAVIGATION = "/authorization/v1/console-navigation";

    @Autowired
    private ConfigurationMocker configuration;

    @Autowired
    private ConfigurationTestMapper configurationData;

    private long targetId;

    /** 本类共用的 Operations 目标、权限路由与免权限路由。 */
    @BeforeEach
    void setUpClassMocks() {
        targetId = configuration.operationsTarget();
        configuration.route(GatewayRouteBuilder.operations("operations-account-create", targetId, "/accounts")
            .permission(OperationsCatalog.permission("OPERATIONS_ACCOUNT_CREATE").id()));
        for (String code : List.of("operations-auth-context", "operations-auth-logout", "operations-auth-password",
            "operations-bootstrap-administrator", "operations-navigation",
            "operations-configuration-permission-options", "operations-other-authenticated")) {
            configuration.route(GatewayRouteBuilder.operations(code, targetId, "/" + code).authenticated());
        }
    }

    /** 持有路由绑定权限的普通会话允许访问，成功不写审计。 */
    @Test
    void test_permissionRouteAllowed() {
        Operator admin = operationsMocker.operator(OperatorBuilder.administrator());

        assertTrue(authorize(admin, "operations-account-create"));
        awaitAudit();
        auditChecker.assertNoEvent(requestId);
    }

    /** 缺少路由权限明确拒绝并审计原因。 */
    @Test
    void test_permissionRouteDenied() {
        Operator viewer = operationsMocker.operator(OperatorBuilder.viewer());

        assertFalse(authorize(viewer, "operations-account-create"));
        auditChecker.assertEvent(requestId, "OPERATIONS_AUTHORIZATION", "DENIED", viewer.actor(), "ROUTE",
            "operations-account-create");
    }

    /** 路由、目标或权限任一不可用，以及未知路由，均不放行。 */
    @Test
    void test_unavailableRouteDenied() {
        Operator admin = operationsMocker.operator(OperatorBuilder.administrator());
        assertFalse(authorize(admin, "operations-unknown-route"));

        var disabled = configuration.route(GatewayRouteBuilder.operations("operations-disabled", targetId, "/disabled")
            .permission(OperationsCatalog.permission("OPERATIONS_ACCOUNT_CREATE").id()).status("DISABLED"));
        assertFalse(authorize(admin, disabled.code()));

        operationsData.updatePermissionStatus(OperationsCatalog.permission("OPERATIONS_ACCOUNT_CREATE").id(),
            "INACTIVE");
        assertFalse(authorize(admin, "operations-account-create"));

        configurationData.updateTargetStatus(targetId, "DISABLED");
        assertFalse(authorize(admin, "operations-auth-context"));
    }

    /** 免权限路由按会话阶段限制：普通会话、待改密会话与初始化账号分别只能访问允许的路由。 */
    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("authenticatedRoutes")
    void test_authenticatedRouteByStage(String stage, String routeCode, boolean expected) {
        OperatorBuilder builder = switch (stage) {
            case "ORDINARY" -> OperatorBuilder.administrator();
            case "FORCE_CHANGE" -> OperatorBuilder.administrator().forceChange(true);
            default -> OperatorBuilder.bootstrap().forceChange(false);
        };
        Operator operator = operationsMocker.operator(builder);

        assertEquals(expected, authorize(operator, routeCode));
        if (!expected) {
            auditChecker.assertEvent(requestId, "OPERATIONS_AUTHORIZATION", "DENIED", operator.actor(), "ROUTE",
                routeCode);
        }
    }

    static Stream<Arguments> authenticatedRoutes() {
        return Stream.of(Arguments.of("ORDINARY", "operations-auth-context", true),
            Arguments.of("ORDINARY", "operations-auth-password", true),
            Arguments.of("ORDINARY", "operations-navigation", true),
            Arguments.of("ORDINARY", "operations-configuration-permission-options", true),
            Arguments.of("ORDINARY", "operations-bootstrap-administrator", false),
            Arguments.of("ORDINARY", "operations-other-authenticated", false),
            Arguments.of("FORCE_CHANGE", "operations-auth-logout", true),
            Arguments.of("FORCE_CHANGE", "operations-auth-password", true),
            Arguments.of("FORCE_CHANGE", "operations-navigation", false),
            Arguments.of("BOOTSTRAP", "operations-bootstrap-administrator", true),
            Arguments.of("BOOTSTRAP", "operations-auth-password", false));
    }

    /** 待改密会话访问权限路由时先要求改密。 */
    @Test
    void test_permissionRouteRequiresOrdinarySession() {
        Operator operator = operationsMocker.operator(OperatorBuilder.administrator().forceChange(true));

        post(AUTHORIZE, operator, new AuthorizationRequestBody("operations-account-create")).assertProblem(400,
            "BUSINESS_CHECK_FAILED");
    }

    /** 会话无效时返回统一失效错误，而不是拒绝决策。 */
    @Test
    void test_invalidSessionRejected() {
        Operator operator = operationsMocker.operator(OperatorBuilder.administrator().sessionStatus("EXPIRED"));

        post(AUTHORIZE, operator, new AuthorizationRequestBody("operations-account-create")).assertProblem(401,
            "OPERATIONS_SESSION_INVALID");
        post(AUTHORIZE, operator, new AuthorizationRequestBody(" ")).assertProblem(422, "VALIDATION_ERROR")
            .assertFieldError("routeCode");
    }

    /** 导航只返回有权限的 ACTIVE 资源，保持排序，并剔除失去可见父节点的后代。 */
    @Test
    void test_navigationFiltersByPermission() {
        long system = configuration.resource("OPERATIONS_SYSTEM", "MENU", null, "OPERATIONS_SYSTEM_READ", 10, "ACTIVE");
        configuration.resource("OPERATIONS_ACCOUNTS", "MENU", system, "OPERATIONS_ACCOUNT_READ", 11, "ACTIVE");
        long roles = configuration.resource("OPERATIONS_ROLES", "MENU", system, "OPERATIONS_ROLE_READ", 12, "ACTIVE");
        configuration.resource("OPERATIONS_ROLE_CREATE", "BUTTON", roles, "OPERATIONS_ROLE_CREATE", 13, "ACTIVE");
        configuration.resource("OPERATIONS_HIDDEN", "MENU", system, "OPERATIONS_AUDIT_READ", 14, "DISABLED");
        long gateway = configuration.resource("GATEWAY", "MENU", null, "GATEWAY_CONFIG_MANAGE", 20, "ACTIVE");
        configuration.resource("GATEWAY_ROUTES", "MENU", gateway, "GATEWAY_CONFIG_READ", 21, "ACTIVE");
        Operator viewer = operationsMocker.operator(OperatorBuilder.viewer());
        Operator admin = operationsMocker.operator(OperatorBuilder.administrator());

        assertEquals(List.of("OPERATIONS_SYSTEM", "OPERATIONS_ACCOUNTS", "OPERATIONS_ROLES"), navigation(viewer),
            "只读人员看不到按钮、停用资源以及父节点不可见的后代");
        assertEquals(List.of("OPERATIONS_SYSTEM", "OPERATIONS_ACCOUNTS", "OPERATIONS_ROLES", "OPERATIONS_ROLE_CREATE",
            "GATEWAY", "GATEWAY_ROUTES"), navigation(admin));
    }

    /** 导航返回完整资源字段，父节点使用字符串标识。 */
    @Test
    void test_navigationProjection() {
        long system = configuration.resource("OPERATIONS_SYSTEM", "MENU", null, "OPERATIONS_SYSTEM_READ", 10, "ACTIVE");
        configuration.resource("OPERATIONS_ACCOUNTS", "MENU", system, "OPERATIONS_ACCOUNT_READ", 11, "ACTIVE");
        Operator viewer = operationsMocker.operator(OperatorBuilder.viewer());

        var resources = post(NAVIGATION, viewer, null).assertOk().as(NavigationResponseBody.class).getResources();

        var child = resources.get(1);
        assertAll(() -> assertEquals(Long.toString(system), child.getParentResourceId()),
            () -> assertEquals("MENU", child.getResourceType()),
            () -> assertEquals("/operations_accounts", child.getFrontendRoute()),
            () -> assertEquals("OPERATIONS_ACCOUNTS", child.getComponentKey()),
            () -> assertEquals(11, child.getSortOrder()), () -> assertNull(resources.getFirst().getParentResourceId()));
    }

    /** 待改密会话不能读取导航。 */
    @Test
    void test_navigationRequiresOrdinarySession() {
        Operator operator = operationsMocker.operator(OperatorBuilder.administrator().forceChange(true));

        post(NAVIGATION, operator, null).assertProblem(400, "BUSINESS_CHECK_FAILED");
    }

    private boolean authorize(Operator operator, String routeCode) {
        PlatformResponseChecker checker = post(AUTHORIZE, operator, new AuthorizationRequestBody(routeCode)).assertOk();
        return checker.as(AuthorizationDecisionResponseBody.class).isAllowed();
    }

    private List<String> navigation(Operator operator) {
        return post(NAVIGATION, operator, null).assertOk().as(NavigationResponseBody.class).getResources().stream()
            .map(resource -> resource.getResourceCode()).toList();
    }

    private void awaitAudit() {
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5))
            .until(() -> auditDispatcher.pendingCount() == 0);
    }
}
