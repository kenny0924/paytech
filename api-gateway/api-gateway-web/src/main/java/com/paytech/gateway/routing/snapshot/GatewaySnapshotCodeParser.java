package com.paytech.gateway.routing.snapshot;

import java.util.Arrays;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.platform.support.api.enums.*;

/** 将 Snapshot 的 String Code 解析为 Platform API 发布的稳定枚举。 */
final class GatewaySnapshotCodeParser {

    private GatewaySnapshotCodeParser() {}

    static GatewayRouteChannel routeChannel(String code) {
        return require(code, GatewayRouteChannel.values(), "Route Channel");
    }

    static GatewayHttpMethod httpMethod(String code) {
        return require(code, GatewayHttpMethod.values(), "HTTP Method");
    }

    static GatewaySecurityProfileCode securityProfile(String code) {
        return require(code, GatewaySecurityProfileCode.values(), "Security Profile");
    }

    static GatewayAuthorizationMode authorizationMode(String code) {
        return require(code, GatewayAuthorizationMode.values(), "Authorization Mode");
    }

    static GatewayRequestCookieMode requestCookieMode(String code) {
        return require(code, GatewayRequestCookieMode.values(), "Request Cookie Mode");
    }

    static GatewayResponseCookieMode responseCookieMode(String code) {
        return require(code, GatewayResponseCookieMode.values(), "Response Cookie Mode");
    }

    /** 旧 Platform 未下发该字段时按 NONE 兼容；出现未知值仍拒绝整份快照。 */
    static GatewayIdempotencyMode idempotencyMode(String code) {
        if (code == null) {
            return GatewayIdempotencyMode.NONE;
        }
        return require(code, GatewayIdempotencyMode.values(), "Idempotency Mode");
    }

    static GatewayGuardRuleType guardRuleType(String code) {
        return require(code, GatewayGuardRuleType.values(), "Guard Rule Type");
    }

    static GatewayGuardDimension guardDimension(String code) {
        return require(code, GatewayGuardDimension.values(), "Guard Dimension");
    }

    private static <T extends Enum<T> & CodeEnum> T require(String code, T[] values, String field) {
        return Arrays.stream(values).filter(value -> value.getCode().equals(code)).findFirst()
            .orElseThrow(() -> new InvalidGatewaySnapshotException("Gateway " + field + " Code 非法"));
    }
}
