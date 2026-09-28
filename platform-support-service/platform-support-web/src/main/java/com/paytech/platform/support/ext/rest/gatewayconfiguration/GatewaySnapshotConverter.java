package com.paytech.platform.support.ext.rest.gatewayconfiguration;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewaySnapshotView.GuardRule;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewaySnapshotView.Route;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewaySnapshotView.Target;
import com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration.GatewaySnapshotView;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayGuardRuleSnapshot;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayPublicSnapshotResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayRouteSnapshot;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayTargetSnapshot;

/**
 * 将已校验的 Gateway 配置转换为公开快照 DTO，不参与查询与业务校验。
 */
@Component
public final class GatewaySnapshotConverter {

    /** 转换完整快照，固定协议版本并保留编译器输出的稳定顺序。 */
    public GatewayPublicSnapshotResponseBody convertResponse(GatewaySnapshotView aggregate) {
        Objects.requireNonNull(aggregate, "aggregate");
        return new GatewayPublicSnapshotResponseBody(1, aggregate.getTargets().stream().map(this::targetSnapshot).toList(),
            aggregate.getRoutes().stream().map(this::routeSnapshot).toList());
    }

    /** 转换当前有效目标及服务发现身份。 */
    GatewayTargetSnapshot targetSnapshot(Target target) {
        return new GatewayTargetSnapshot(target.targetServiceCode(), target.channel().getCode(),
            target.discoveryServiceName());
    }

    /** 转换路由策略及其完整子配置。 */
    GatewayRouteSnapshot routeSnapshot(Route route) {
        return new GatewayRouteSnapshot(route.routeCode(), route.channel().getCode(), route.httpMethod(),
            route.externalPathTemplate(), route.downstreamPathTemplate(), route.targetServiceCode(),
            route.securityProfileCode().getCode(), route.authorizationMode().getCode(), route.permissionCode(),
            route.requiredScopeCode(), route.requestCookieMode().getCode(), route.responseCookieMode().getCode(),
            route.corsRequestHeaderNames(), route.forwardedRequestHeaderNames(), route.queryParameterNames(),
            route.bodyLimitBytes(), route.bodyReceiveTimeoutMillis(), route.responseTimeoutMillis(),
            route.guardRules().stream().map(this::guardSnapshot).toList());
    }

    /** 转换 Guard 枚举 Code 和已校验的数值。 */
    GatewayGuardRuleSnapshot guardSnapshot(GuardRule rule) {
        return new GatewayGuardRuleSnapshot(rule.ruleType().getCode(), rule.dimension().getCode(), rule.capacity(),
            rule.refillTokens(), rule.refillPeriodMillis(), rule.concurrencyLimit());
    }
}
