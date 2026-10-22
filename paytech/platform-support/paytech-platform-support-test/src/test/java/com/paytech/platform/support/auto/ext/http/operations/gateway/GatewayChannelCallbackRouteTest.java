package com.paytech.platform.support.auto.ext.http.operations.gateway;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayRouteHeaderUse;
import com.paytech.platform.support.api.enums.GatewayAuthorizationMode;
import com.paytech.platform.support.api.enums.GatewayGuardDimension;
import com.paytech.platform.support.api.enums.GatewayGuardRuleType;
import com.paytech.platform.support.api.enums.GatewayIdempotencyMode;
import com.paytech.platform.support.api.enums.GatewayRequestCookieMode;
import com.paytech.platform.support.api.enums.GatewayResponseCookieMode;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;
import com.paytech.platform.support.api.enums.GatewaySecurityProfileCode;
import com.paytech.platform.support.api.gatewayconfiguration.GatewaySnapshotQueryApi;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationManagementApi;
import com.paytech.platform.support.api.gatewayconfiguration.management.GatewayConfigurationModels.ChangeGatewayRouteStatusRequestBody;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayGuardRuleSnapshot;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayPublicSnapshotResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayRouteSnapshot;
import com.paytech.platform.support.test.base.OperationsApiTestBase;
import com.paytech.platform.support.test.builder.GatewayRouteBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.dal.ConfigurationTestMapper.Route;
import com.paytech.platform.support.test.mocker.local.ConfigurationMocker;

/**
 * 渠道支付通知公开路由（初始化数据块 133）：停用状态即可通过完整目录编译，启用后以完整策略进入公开快照。
 *
 * @see GatewaySnapshotQueryApi#queryPublicSnapshot()
 * @see GatewayConfigurationManagementApi#changeRouteStatus(String, String, String, ChangeGatewayRouteStatusRequestBody)
 */
class GatewayChannelCallbackRouteTest extends OperationsApiTestBase {

    private static final String SNAPSHOT = "/gateway-config/v1/public-snapshot";
    private static final String ROUTES = "/gateway-admin/v1/routes";
    private static final String TARGET = "channel-connector-service";
    private static final String ROUTE = "channel-payment-callback";
    private static final String EXTERNAL_PATH = "/api/channel-callbacks/v1/{channelCode}/{channelAccountId}";
    private static final String DOWNSTREAM_PATH = "/channel-callbacks/v1/{channelCode}/{channelAccountId}";
    private static final String CHANNEL = GatewayRouteChannel.CHANNEL_CALLBACK.getCode();
    private static final long BODY_LIMIT_BYTES = 65_536;

    @Autowired
    private ConfigurationMocker configuration;

    private long targetId;

    /** 与块 133 一致的 ACTIVE 渠道回调目标。 */
    @BeforeEach
    void setUpChannelCallbackTarget() {
        targetId = configuration.target(TARGET, CHANNEL, GatewayConfigurationStatus.ACTIVE.getCode());
    }

    /** 块 133 原样插入（路由 DISABLED）：快照可编译，目标下发、路由不下发。 */
    @Test
    void test_disabledRouteCompiles() {
        insertCallbackRoute();

        GatewayPublicSnapshotResponseBody snapshot = snapshot().assertOk().as(GatewayPublicSnapshotResponseBody.class);

        assertTrue(snapshot.getTargets().stream().anyMatch(
            target -> TARGET.equals(target.getTargetServiceCode()) && CHANNEL.equals(target.getChannel())
                && TARGET.equals(target.getDiscoveryServiceName())));
        assertTrue(snapshot.getRoutes().stream().noneMatch(route -> ROUTE.equals(route.getRouteCode())));
    }

    /** 按块 133 注释中的激活方式启用后，快照携带完整的回调路由策略。 */
    @Test
    void test_activatedRouteInSnapshot() {
        insertCallbackRoute();
        var admin = operationsMocker.operator(OperatorBuilder.administrator());

        call(HttpMethod.PATCH, ROUTES + "/" + ROUTE + "/status", headers(admin),
            new ChangeGatewayRouteStatusRequestBody(GatewayConfigurationStatus.ACTIVE.getCode(), 1, "启用渠道回调"))
            .assertOk();

        GatewayRouteSnapshot route = snapshot().assertOk().as(GatewayPublicSnapshotResponseBody.class).getRoutes()
            .stream().filter(item -> ROUTE.equals(item.getRouteCode())).findFirst()
            .orElseThrow(() -> new AssertionError("快照缺少渠道回调路由"));
        assertAll(() -> assertEquals(CHANNEL, route.getChannel()),
            () -> assertEquals(EXTERNAL_PATH, route.getExternalPathTemplate()),
            () -> assertEquals(DOWNSTREAM_PATH, route.getDownstreamPathTemplate()),
            () -> assertEquals(TARGET, route.getTargetServiceCode()),
            () -> assertEquals(GatewaySecurityProfileCode.CHANNEL_CALLBACK.getCode(), route.getSecurityProfileCode()),
            () -> assertEquals(GatewayAuthorizationMode.CHANNEL_CALLBACK.getCode(), route.getAuthorizationMode()),
            () -> assertNull(route.getPermissionCode()), () -> assertNull(route.getRequiredScopeCode()),
            () -> assertEquals(GatewayRequestCookieMode.DROP.getCode(), route.getRequestCookieMode()),
            () -> assertEquals(GatewayResponseCookieMode.NONE.getCode(), route.getResponseCookieMode()),
            () -> assertEquals(GatewayIdempotencyMode.NONE.getCode(), route.getIdempotencyMode()),
            () -> assertEquals(BODY_LIMIT_BYTES, route.getBodyLimitBytes()),
            () -> assertEquals(List.of(), route.getCorsRequestHeaderNames()),
            () -> assertEquals(List.of("content-type", "sign"), route.getForwardedRequestHeaderNames()),
            () -> assertEquals(List.of(
                guard(GatewayGuardRuleType.CONCURRENCY, GatewayGuardDimension.CLIENT_IP, null, 20L),
                guard(GatewayGuardRuleType.CONCURRENCY, GatewayGuardDimension.SHARED_ROUTE, null, 50L),
                guard(GatewayGuardRuleType.RATE_LIMIT, GatewayGuardDimension.CLIENT_IP, 120L, null),
                guard(GatewayGuardRuleType.RATE_LIMIT, GatewayGuardDimension.SHARED_ROUTE, 600L, null)),
                route.getGuardRules().stream().map(this::guardKey).toList()));
    }

    /** 插入块 133 的路由、Header 与 Guard。 */
    private void insertCallbackRoute() {
        Route route = configuration.route(GatewayRouteBuilder.operations(ROUTE, targetId, DOWNSTREAM_PATH)
            .externalPath(EXTERNAL_PATH).channel(CHANNEL, GatewaySecurityProfileCode.CHANNEL_CALLBACK.getCode(),
                GatewayAuthorizationMode.CHANNEL_CALLBACK.getCode())
            .bodyLimit(BODY_LIMIT_BYTES).status(GatewayConfigurationStatus.DISABLED.getCode()));
        configuration.header(route.id(), "sign", GatewayRouteHeaderUse.FORWARD_REQUEST.getCode());
        configuration.header(route.id(), "content-type", GatewayRouteHeaderUse.FORWARD_REQUEST.getCode());
        configuration.rateLimit(route.id(), GatewayGuardDimension.SHARED_ROUTE.getCode(), 600, 600, 60_000);
        configuration.concurrency(route.id(), GatewayGuardDimension.SHARED_ROUTE.getCode(), 50);
        configuration.rateLimit(route.id(), GatewayGuardDimension.CLIENT_IP.getCode(), 120, 120, 60_000);
        configuration.concurrency(route.id(), GatewayGuardDimension.CLIENT_IP.getCode(), 20);
    }

    /** 匿名读取 Gateway 公开快照。 */
    private PlatformResponseChecker snapshot() {
        return anonymous(HttpMethod.GET, SNAPSHOT, null);
    }

    /** Guard 的可比较摘要：类型、维度、容量与并发上限。 */
    private String guard(GatewayGuardRuleType type, GatewayGuardDimension dimension, Long capacity, Long concurrency) {
        return type.getCode() + ':' + dimension.getCode() + ':' + capacity + ':' + concurrency;
    }

    private String guardKey(GatewayGuardRuleSnapshot rule) {
        return rule.getRuleType() + ':' + rule.getDimension() + ':' + rule.getCapacity() + ':'
            + rule.getConcurrencyLimit();
    }
}
