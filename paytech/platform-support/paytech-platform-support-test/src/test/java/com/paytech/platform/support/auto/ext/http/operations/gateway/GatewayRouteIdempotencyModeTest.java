package com.paytech.platform.support.auto.ext.http.operations.gateway;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayRouteHeaderUse;
import com.paytech.platform.support.api.enums.GatewayAuthorizationMode;
import com.paytech.platform.support.api.enums.GatewayGuardDimension;
import com.paytech.platform.support.api.enums.GatewayGuardRuleType;
import com.paytech.platform.support.api.enums.GatewayHttpMethod;
import com.paytech.platform.support.api.enums.GatewayIdempotencyMode;
import com.paytech.platform.support.api.enums.GatewayRequestCookieMode;
import com.paytech.platform.support.api.enums.GatewayResponseCookieMode;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;
import com.paytech.platform.support.api.enums.GatewaySecurityProfileCode;
import com.paytech.platform.support.api.gatewayconfiguration.GatewaySnapshotQueryApi;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationManagementApi;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.ChangeGatewayRouteStatusRequestBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.CreateGatewayRouteRequestBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayConfigurationCapabilitiesResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayGuardRuleRequestBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayRouteHeaderRequestBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayRouteResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.ProfileCapability;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.UpdateGatewayRouteRequestBody;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayPublicSnapshotResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayRouteSnapshot;
import com.paytech.platform.support.test.base.OperationsApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.dal.AuditTestMapper;
import com.paytech.platform.support.test.dal.ConfigurationTestMapper;
import com.paytech.platform.support.test.mocker.local.ConfigurationMocker;

import tools.jackson.core.type.TypeReference;

/**
 * Gateway Route 请求幂等模式：创建缺省 NONE、更新缺省保持当前值、REQUIRED 的通道 / 方法 / Header 兼容规则、
 * 能力选项、快照下发与配置审计。
 *
 * @see GatewayConfigurationManagementApi#createRoute(String, String, CreateGatewayRouteRequestBody)
 * @see GatewayConfigurationManagementApi#updateRoute(String, String, String, UpdateGatewayRouteRequestBody)
 * @see GatewayConfigurationManagementApi#route(String, String, String)
 * @see GatewayConfigurationManagementApi#capabilities(String, String)
 * @see GatewaySnapshotQueryApi#queryPublicSnapshot()
 */
class GatewayRouteIdempotencyModeTest extends OperationsApiTestBase {

    private static final String ROUTES = "/gateway-admin/v1/routes";
    private static final String CAPABILITIES = "/gateway-admin/v1/capabilities";
    private static final String SNAPSHOT = "/gateway-config/v1/public-snapshot";
    private static final String TARGET = "commerce-client-api";
    private static final String SCOPE = "orders:write";
    private static final String ROUTE = "client-api-order-create";
    private static final String IDEMPOTENCY_KEY = "idempotency-key";
    private static final String NONE = GatewayIdempotencyMode.NONE.getCode();
    private static final String REQUIRED = GatewayIdempotencyMode.REQUIRED.getCode();

    @Autowired
    private ConfigurationMocker configuration;

    @Autowired
    private ConfigurationTestMapper configurationData;

    @Autowired
    private AuditTestMapper auditData;

    private Operator admin;

    /** 一个 ACTIVE 的 Client API 目标、已登记的写 Scope 与一位平台管理员。 */
    @BeforeEach
    void setUpClientApiCatalog() {
        configuration.target(TARGET, GatewayRouteChannel.CLIENT_API.getCode(), GatewayConfigurationStatus.ACTIVE.getCode());
        configuration.apiScope(SCOPE, "创建订单");
        admin = operationsMocker.operator(OperatorBuilder.administrator());
    }

    /** 创建不声明请求幂等模式时保存为 NONE，详情同样返回 NONE。 */
    @Test
    void test_createDefaultsToNone() {
        createRoute(GatewayHttpMethod.POST, null, List.of()).assertOk();

        assertEquals(NONE, configurationData.routeIdempotencyMode(ROUTE));
        assertEquals(NONE, route().idempotencyMode());
    }

    /** Client API 写路由可设为 REQUIRED；启用后详情与快照都携带该模式。 */
    @Test
    void test_createRequiredOnClientApiWrite() {
        createRoute(GatewayHttpMethod.POST, REQUIRED, List.of()).assertOk();
        assertEquals(REQUIRED, route().idempotencyMode());

        patch(ROUTES + "/" + ROUTE + "/status", new ChangeGatewayRouteStatusRequestBody(GatewayConfigurationStatus.ACTIVE.getCode(), 1,
            "启用")).assertOk();

        GatewayRouteSnapshot snapshot = snapshotRoute(ROUTE);
        assertEquals(REQUIRED, snapshot.getIdempotencyMode());
    }

    /** 更新不传该字段时保持当前 REQUIRED，避免按固定字段 PUT 的调用方悄悄降级。 */
    @Test
    void test_updateWithoutModeKeepsCurrentValue() {
        createRoute(GatewayHttpMethod.POST, REQUIRED, List.of()).assertOk();

        updateRoute(null, 1, 65536).assertOk();

        GatewayRouteResponseBody updated = route();
        assertAll(() -> assertEquals(2, updated.version()), () -> assertEquals(65536, updated.bodyLimitBytes()),
            () -> assertEquals(REQUIRED, updated.idempotencyMode()));
    }

    /** 请求体显式 null 与不含该键等价：都保持当前 REQUIRED；创建显式 null 为 NONE。 */
    @Test
    void test_explicitNullKeepsCurrentValue() {
        createRoute(GatewayHttpMethod.POST, REQUIRED, List.of()).assertOk();

        Map<String, Object> explicitNull = updateBody(1, 32768);
        explicitNull.put("idempotencyMode", null);
        put(ROUTES + "/" + ROUTE, admin, explicitNull).assertOk();
        assertEquals(REQUIRED, configurationData.routeIdempotencyMode(ROUTE));

        Map<String, Object> absent = updateBody(2, 16384);
        absent.remove("idempotencyMode");
        put(ROUTES + "/" + ROUTE, admin, absent).assertOk();
        GatewayRouteResponseBody updated = route();
        assertAll(() -> assertEquals(3, updated.version()), () -> assertEquals(REQUIRED, updated.idempotencyMode()));

        Map<String, Object> create = objectMapper.convertValue(createBody("client-api-order-capture",
            "/api/client/v1/captures", "/client/v1/captures", null), new TypeReference<Map<String, Object>>() {});
        create.put("idempotencyMode", null);
        post(ROUTES, admin, create).assertOk();
        assertEquals(NONE, configurationData.routeIdempotencyMode("client-api-order-capture"));
    }

    /** 显式改为 NONE 后持久化 NONE，审计记录修改前后的模式。 */
    @Test
    void test_updateToNoneRecordsAudit() {
        createRoute(GatewayHttpMethod.POST, REQUIRED, List.of()).assertOk();

        updateRoute(NONE, 1, 16384).assertOk();

        assertEquals(NONE, configurationData.routeIdempotencyMode(ROUTE));
        Map<String, Object> event = auditChecker.assertEvent(requestId, "OPERATIONS_GATEWAY_ROUTE_UPDATE", "SUCCEEDED",
            admin.actor(), "GATEWAY_ROUTE", ROUTE);
        Map<String, Object> attributes = auditData.attributes(((Number) event.get("audit_event_id")).longValue())
            .stream().collect(Collectors.toMap(row -> (String) row.get("attr_key"), row -> row.get("value_text")));
        assertAll(() -> assertEquals(REQUIRED, attributes.get("before.IDEMPOTENCY_MODE")),
            () -> assertEquals(NONE, attributes.get("after.IDEMPOTENCY_MODE")));
    }

    /** REQUIRED 只允许 Client API 的非 GET/HEAD 路由，且不得转发 Idempotency-Key；未知模式同样拒绝。 */
    @Test
    void test_requiredCompatibilityRejected() {
        createRoute(GatewayHttpMethod.GET, REQUIRED, List.of()).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        createRoute(GatewayHttpMethod.HEAD, REQUIRED, List.of()).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        createRoute(GatewayHttpMethod.POST, REQUIRED,
            List.of(new GatewayRouteHeaderRequestBody(IDEMPOTENCY_KEY, GatewayRouteHeaderUse.FORWARD_REQUEST.getCode())))
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        createRoute(GatewayHttpMethod.POST, "ALWAYS", List.of()).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        configuration.operationsTarget();
        createOperationsRoute(REQUIRED).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        createOperationsRoute(NONE).assertOk();
        assertNull(configurationData.routeIdempotencyMode(ROUTE));

        createRoute(GatewayHttpMethod.POST, REQUIRED, List.of(new GatewayRouteHeaderRequestBody(IDEMPOTENCY_KEY,
            GatewayRouteHeaderUse.CORS_REQUEST_ALLOWED.getCode()))).assertOk();
        updateRoute(REQUIRED, 1, 16384, GatewayHttpMethod.GET).assertProblem(400, CommonErrorCode.INVALID_REQUEST);
        assertEquals(REQUIRED, configurationData.routeIdempotencyMode(ROUTE));
    }

    /** 只有 Client API Credential 档案提供 REQUIRED，其他档案只有 NONE。 */
    @Test
    void test_capabilitiesListModesByProfile() {
        var capabilities = get(CAPABILITIES, admin).assertOk().as(GatewayConfigurationCapabilitiesResponseBody.class);

        List<ProfileCapability> profiles = capabilities.channels().stream()
            .flatMap(channel -> channel.profiles().stream()).toList();
        assertFalse(profiles.isEmpty());
        for (ProfileCapability profile : profiles) {
            List<String> expected = GatewaySecurityProfileCode.CLIENT_API_CREDENTIAL.getCode()
                .equals(profile.securityProfileCode()) ? List.of(NONE, REQUIRED) : List.of(NONE);
            assertEquals(expected, profile.idempotencyModes(), profile.securityProfileCode());
        }
    }

    /** 以管理员身份创建 Client API 路由；mode 为 null 表示请求不声明该字段。 */
    private PlatformResponseChecker createRoute(GatewayHttpMethod method, String mode,
        List<GatewayRouteHeaderRequestBody> headers) {
        return post(ROUTES, admin, new CreateGatewayRouteRequestBody(ROUTE, GatewayRouteChannel.CLIENT_API.getCode(),
            method.getCode(), "/api/client/v1/orders", "/client/v1/orders", TARGET,
            GatewaySecurityProfileCode.CLIENT_API_CREDENTIAL.getCode(), GatewayAuthorizationMode.API_SCOPE.getCode(),
            null, SCOPE, GatewayRequestCookieMode.DROP.getCode(), GatewayResponseCookieMode.NONE.getCode(), mode,
            16384, 5000, 10000, headers, List.of(), sharedRouteGuards(), "创建"));
    }

    /** Client API POST 路由的创建请求体。 */
    private CreateGatewayRouteRequestBody createBody(String code, String externalPath, String downstreamPath,
        String mode) {
        return new CreateGatewayRouteRequestBody(code, GatewayRouteChannel.CLIENT_API.getCode(),
            GatewayHttpMethod.POST.getCode(), externalPath, downstreamPath, TARGET,
            GatewaySecurityProfileCode.CLIENT_API_CREDENTIAL.getCode(), GatewayAuthorizationMode.API_SCOPE.getCode(),
            null, SCOPE, GatewayRequestCookieMode.DROP.getCode(), GatewayResponseCookieMode.NONE.getCode(), mode,
            16384, 5000, 10000, List.of(), List.of(), sharedRouteGuards(), "创建");
    }

    /** 未声明请求幂等模式的 POST 路由更新请求体，以可编辑 Map 形式控制该键是否出现。 */
    private Map<String, Object> updateBody(int expectedVersion, long bodyLimit) {
        return objectMapper.convertValue(new UpdateGatewayRouteRequestBody(GatewayHttpMethod.POST.getCode(),
            "/api/client/v1/orders", "/client/v1/orders", TARGET, GatewaySecurityProfileCode.CLIENT_API_CREDENTIAL.getCode(),
            GatewayAuthorizationMode.API_SCOPE.getCode(), null, SCOPE, GatewayRequestCookieMode.DROP.getCode(),
            GatewayResponseCookieMode.NONE.getCode(), NONE, bodyLimit, 5000, 10000, List.of(), List.of(),
            sharedRouteGuards(), expectedVersion, "调整"), new TypeReference<Map<String, Object>>() {});
    }

    /** Operations 渠道的 REQUIRED 路由，用于验证通道限制。 */
    private PlatformResponseChecker createOperationsRoute(String mode) {
        return post(ROUTES, admin, new CreateGatewayRouteRequestBody("operations-order-create",
            GatewayRouteChannel.OPERATIONS.getCode(), GatewayHttpMethod.POST.getCode(), "/api/operations/orders/v1/orders",
            "/orders/v1/orders", "operations-console-bff", GatewaySecurityProfileCode.OPERATIONS_JWT.getCode(),
            GatewayAuthorizationMode.HUMAN_AUTHENTICATED.getCode(), null, null, GatewayRequestCookieMode.DROP.getCode(),
            GatewayResponseCookieMode.NONE.getCode(), mode, 16384, 5000, 10000, List.of(), List.of(),
            List.of(rate(GatewayGuardDimension.SHARED_ROUTE), concurrency(GatewayGuardDimension.SHARED_ROUTE),
                rate(GatewayGuardDimension.CLIENT_IP), concurrency(GatewayGuardDimension.CLIENT_IP),
                rate(GatewayGuardDimension.AUTHENTICATED_PRINCIPAL),
                concurrency(GatewayGuardDimension.AUTHENTICATED_PRINCIPAL)),
            "创建"));
    }

    /** 全量更新 POST 路由；mode 为 null 表示请求不声明该字段。 */
    private PlatformResponseChecker updateRoute(String mode, int expectedVersion, long bodyLimit) {
        return updateRoute(mode, expectedVersion, bodyLimit, GatewayHttpMethod.POST);
    }

    /** 全量更新路由。 */
    private PlatformResponseChecker updateRoute(String mode, int expectedVersion, long bodyLimit,
        GatewayHttpMethod method) {
        return put(ROUTES + "/" + ROUTE, admin, new UpdateGatewayRouteRequestBody(method.getCode(),
            "/api/client/v1/orders", "/client/v1/orders", TARGET, GatewaySecurityProfileCode.CLIENT_API_CREDENTIAL.getCode(),
            GatewayAuthorizationMode.API_SCOPE.getCode(), null, SCOPE, GatewayRequestCookieMode.DROP.getCode(),
            GatewayResponseCookieMode.NONE.getCode(), mode, bodyLimit, 5000, 10000, List.of(), List.of(),
            sharedRouteGuards(), expectedVersion, "调整"));
    }

    /** 以管理员身份 PATCH。 */
    private PlatformResponseChecker patch(String path, Object body) {
        return call(HttpMethod.PATCH, path, headers(admin), body);
    }

    /** 读取路由详情。 */
    private GatewayRouteResponseBody route() {
        return get(ROUTES + "/" + ROUTE, admin).assertOk().as(GatewayRouteResponseBody.class);
    }

    /** 从完整快照中取出指定路由。 */
    private GatewayRouteSnapshot snapshotRoute(String routeCode) {
        var snapshot = anonymous(HttpMethod.GET, SNAPSHOT, null).assertOk().as(GatewayPublicSnapshotResponseBody.class);
        return snapshot.getRoutes().stream().filter(route -> routeCode.equals(route.getRouteCode())).findFirst()
            .orElseThrow(() -> new AssertionError("快照缺少路由: " + routeCode));
    }

    /** Client API Credential 档案必需的共享路由限流与并发。 */
    private List<GatewayGuardRuleRequestBody> sharedRouteGuards() {
        return List.of(rate(GatewayGuardDimension.SHARED_ROUTE), concurrency(GatewayGuardDimension.SHARED_ROUTE));
    }

    private GatewayGuardRuleRequestBody rate(GatewayGuardDimension dimension) {
        return new GatewayGuardRuleRequestBody(GatewayGuardRuleType.RATE_LIMIT.getCode(), dimension.getCode(), 100L,
            100L, 60000L, null);
    }

    private GatewayGuardRuleRequestBody concurrency(GatewayGuardDimension dimension) {
        return new GatewayGuardRuleRequestBody(GatewayGuardRuleType.CONCURRENCY.getCode(), dimension.getCode(), null,
            null, null, 10L);
    }
}
