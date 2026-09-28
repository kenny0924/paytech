package com.paytech.platform.support.accesscontrol.domain.gatewayconfiguration;

import java.util.List;

import lombok.Getter;
import lombok.EqualsAndHashCode;

import com.paytech.platform.support.api.enums.*;

/**
 * 已完成全局语义校验的当前 Gateway 有效配置。
 */
@Getter
@EqualsAndHashCode
public final class GatewaySnapshotView {

    /** 已校验并按编码排序的当前有效目标。 */
    private final List<Target> targets;

    /** 已校验并按编码排序的当前有效路由及其子配置。 */
    private final List<Route> routes;

    /** 冻结多表组合后的完整查询结果，拒绝缺失集合或空元素。 */
    public GatewaySnapshotView(List<Target> targets, List<Route> routes) {
        this.targets = immutable("targets", targets);
        this.routes = immutable("routes", routes);
    }

    /** 冻结已校验的集合，防止查询结果被调用方修改。 */
    private static <T> List<T> immutable(String name, List<T> values) {
        if (values == null || values.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException(name + " 不得为空或包含空元素");
        }
        return List.copyOf(values);
    }

    /** 编译后的有效目标，不携带数据库管理字段。 */
    public record Target(String targetServiceCode, GatewayRouteChannel channel, String discoveryServiceName) {
    }

    /** 编译后的有效路由及当前请求策略。 */
    public record Route(String routeCode, GatewayRouteChannel channel, String httpMethod, String externalPathTemplate,
        String downstreamPathTemplate, String targetServiceCode, GatewaySecurityProfileCode securityProfileCode,
        GatewayAuthorizationMode authorizationMode, String permissionCode, String requiredScopeCode,
        GatewayRequestCookieMode requestCookieMode, GatewayResponseCookieMode responseCookieMode,
        List<String> corsRequestHeaderNames, List<String> forwardedRequestHeaderNames, List<String> queryParameterNames,
        long bodyLimitBytes, int bodyReceiveTimeoutMillis, int responseTimeoutMillis, List<GuardRule> guardRules) {

        /** 冻结路由拥有的完整子配置。 */
        public Route {
            corsRequestHeaderNames = immutable("corsRequestHeaderNames", corsRequestHeaderNames);
            forwardedRequestHeaderNames = immutable("forwardedRequestHeaderNames", forwardedRequestHeaderNames);
            queryParameterNames = immutable("queryParameterNames", queryParameterNames);
            guardRules = immutable("guardRules", guardRules);
        }
    }

    /** 通过安全档案校验的纯规则值。 */
    public record GuardRule(GatewayGuardRuleType ruleType, GatewayGuardDimension dimension, Long capacity,
        Long refillTokens, Long refillPeriodMillis, Long concurrencyLimit) {
    }
}
