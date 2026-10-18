package com.paytech.platform.support.auto.ext.http.operations.gateway;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;
import com.paytech.platform.support.api.enums.GatewayAuthorizationMode;
import com.paytech.platform.support.api.enums.GatewayGuardDimension;
import com.paytech.platform.support.api.enums.GatewayGuardRuleType;
import com.paytech.platform.support.api.enums.GatewayHttpMethod;
import com.paytech.platform.support.api.enums.GatewayIdempotencyMode;
import com.paytech.platform.support.api.enums.GatewayRequestCookieMode;
import com.paytech.platform.support.api.enums.GatewayResponseCookieMode;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;
import com.paytech.platform.support.api.enums.GatewaySecurityProfileCode;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationManagementApi;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.ChangeGatewayRouteStatusRequestBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.ChangeGatewayTargetStatusRequestBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.CreateGatewayRouteRequestBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.CreateGatewayTargetRequestBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayGuardRuleRequestBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayRouteResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.GatewayTargetResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.UpdateGatewayRouteRequestBody;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.UpdateGatewayTargetRequestBody;
import com.paytech.platform.support.test.base.OperationsApiTestBase;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mocker.local.ConfigurationMocker;

/**
 * Gateway 配置写入口共用一把 Catalog 资源锁：Key 沿用原注解锁的 {@code gateway-configuration:catalog}，
 * 被占用时六个写入口在等待上限后返回并发冲突且不改配置；查询入口不加锁。
 *
 * @see GatewayConfigurationManagementApi
 */
class GatewayConfigurationLockTest extends OperationsApiTestBase {

    /** 原注解锁与模板锁共用的完整逻辑 Key。 */
    private static final String CATALOG_LOCK = "gateway-configuration:catalog";
    private static final String TARGETS = "/gateway-admin/v1/target-services";
    private static final String ROUTES = "/gateway-admin/v1/routes";
    private static final String TARGET = "commerce-client-api";
    private static final String SCOPE = "orders:write";
    private static final String ROUTE = "client-api-order-create";

    @Autowired
    private RedissonClient redisson;

    @Autowired
    private ConfigurationMocker configuration;

    private Operator admin;

    /** 一个 ACTIVE 的 Client API 目标、已登记的写 Scope、一位平台管理员及其创建的一条 DISABLED 路由。 */
    @BeforeEach
    void setUpCatalog() {
        configuration.target(TARGET, GatewayRouteChannel.CLIENT_API.getCode(), GatewayConfigurationStatus.ACTIVE.getCode());
        configuration.apiScope(SCOPE, "创建订单");
        admin = operationsMocker.operator(OperatorBuilder.administrator());
        post(ROUTES, admin, createRoute(ROUTE, "/api/client/v1/orders", "/client/v1/orders")).assertOk();
    }

    /** Catalog 锁被占用：Target / Route 的创建、更新与启停全部返回 409；释放后版本与状态均未变化。 */
    @Test
    void test_lockContention() {
        RLock held = redisson.getLock(CATALOG_LOCK);
        assertTrue(tryHold(held));
        try {
            for (PlatformResponseChecker result : List.of(
                post(TARGETS, admin, new CreateGatewayTargetRequestBody("ledger-client-api",
                    GatewayRouteChannel.CLIENT_API.getCode(), "ledger-client-api", "创建")),
                put(TARGETS + "/" + TARGET, admin, new UpdateGatewayTargetRequestBody("commerce-service", 1, "调整")),
                call(HttpMethod.PATCH, TARGETS + "/" + TARGET + "/status", headers(admin),
                    new ChangeGatewayTargetStatusRequestBody(GatewayConfigurationStatus.DISABLED.getCode(), 1, "停用")),
                post(ROUTES, admin, createRoute("client-api-order-capture", "/api/client/v1/captures", "/client/v1/captures")),
                put(ROUTES + "/" + ROUTE, admin, updateRoute()),
                call(HttpMethod.PATCH, ROUTES + "/" + ROUTE + "/status", headers(admin),
                    new ChangeGatewayRouteStatusRequestBody(GatewayConfigurationStatus.ACTIVE.getCode(), 1, "启用")))) {
                result.assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
            }
        } finally {
            held.unlock();
        }

        GatewayTargetResponseBody target = get(TARGETS + "/" + TARGET, admin).assertOk().as(GatewayTargetResponseBody.class);
        GatewayRouteResponseBody route = get(ROUTES + "/" + ROUTE, admin).assertOk().as(GatewayRouteResponseBody.class);
        assertAll(() -> assertEquals(1, target.version()), () -> assertEquals(TARGET, target.discoveryServiceName()),
            () -> assertEquals(GatewayConfigurationStatus.ACTIVE.getCode(), target.status()),
            () -> assertEquals(1, route.version()),
            () -> assertEquals(GatewayConfigurationStatus.DISABLED.getCode(), route.status()));
        assertEquals(0, get(TARGETS + "?targetServiceCode=ledger-client-api", admin).assertOk().json().path("total").asLong());
    }

    /** 查询不加锁：Catalog 锁被占用时列表、详情与能力查询仍返回 200。 */
    @Test
    void test_queryNotLocked() {
        RLock held = redisson.getLock(CATALOG_LOCK);
        assertTrue(tryHold(held));
        try {
            get(TARGETS, admin).assertOk();
            get(TARGETS + "/" + TARGET, admin).assertOk();
            get(ROUTES, admin).assertOk();
            get(ROUTES + "/" + ROUTE, admin).assertOk();
            get("/gateway-admin/v1/capabilities", admin).assertOk();
        } finally {
            held.unlock();
        }
    }

    /** 立即取得锁，租约覆盖整段调用。 */
    boolean tryHold(RLock lock) {
        try {
            return lock.tryLock(0, 30, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    /** Client API POST 路由的创建请求体。 */
    CreateGatewayRouteRequestBody createRoute(String code, String externalPath, String downstreamPath) {
        return new CreateGatewayRouteRequestBody(code, GatewayRouteChannel.CLIENT_API.getCode(),
            GatewayHttpMethod.POST.getCode(), externalPath, downstreamPath, TARGET,
            GatewaySecurityProfileCode.CLIENT_API_CREDENTIAL.getCode(), GatewayAuthorizationMode.API_SCOPE.getCode(),
            null, SCOPE, GatewayRequestCookieMode.DROP.getCode(), GatewayResponseCookieMode.NONE.getCode(),
            GatewayIdempotencyMode.NONE.getCode(), 16384, 5000, 10000, List.of(), List.of(), guards(), "创建");
    }

    /** 调整请求体上限的全量更新请求体。 */
    UpdateGatewayRouteRequestBody updateRoute() {
        return new UpdateGatewayRouteRequestBody(GatewayHttpMethod.POST.getCode(), "/api/client/v1/orders",
            "/client/v1/orders", TARGET, GatewaySecurityProfileCode.CLIENT_API_CREDENTIAL.getCode(),
            GatewayAuthorizationMode.API_SCOPE.getCode(), null, SCOPE, GatewayRequestCookieMode.DROP.getCode(),
            GatewayResponseCookieMode.NONE.getCode(), GatewayIdempotencyMode.NONE.getCode(), 32768, 5000, 10000,
            List.of(), List.of(), guards(), 1, "调整");
    }

    /** Client API Credential 档案必需的共享路由限流与并发。 */
    List<GatewayGuardRuleRequestBody> guards() {
        return List.of(new GatewayGuardRuleRequestBody(GatewayGuardRuleType.RATE_LIMIT.getCode(),
            GatewayGuardDimension.SHARED_ROUTE.getCode(), 100L, 100L, 60000L, null),
            new GatewayGuardRuleRequestBody(GatewayGuardRuleType.CONCURRENCY.getCode(),
                GatewayGuardDimension.SHARED_ROUTE.getCode(), null, null, null, 10L));
    }
}
