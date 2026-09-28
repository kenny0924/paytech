package com.paytech.gateway.routing.snapshot;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.paytech.platform.support.api.enums.*;

class GatewaySnapshotCodeParserAutoTest {

    @Test
    void test_parsesEveryPublishedPlatformGatewayCode() {
        Assertions.assertAll(
            () -> assertRoundTrip(GatewayRouteChannel.values(), GatewaySnapshotCodeParser::routeChannel),
            () -> assertRoundTrip(GatewayHttpMethod.values(), GatewaySnapshotCodeParser::httpMethod),
            () -> assertRoundTrip(GatewaySecurityProfileCode.values(), GatewaySnapshotCodeParser::securityProfile),
            () -> assertRoundTrip(GatewayAuthorizationMode.values(), GatewaySnapshotCodeParser::authorizationMode),
            () -> assertRoundTrip(GatewayRequestCookieMode.values(), GatewaySnapshotCodeParser::requestCookieMode),
            () -> assertRoundTrip(GatewayResponseCookieMode.values(), GatewaySnapshotCodeParser::responseCookieMode),
            () -> assertRoundTrip(GatewayGuardRuleType.values(), GatewaySnapshotCodeParser::guardRuleType),
            () -> assertRoundTrip(GatewayGuardDimension.values(), GatewaySnapshotCodeParser::guardDimension));
    }

    @Test
    void test_rejectsUnknownCodeForEveryPlatformGatewayCodeType() {
        Assertions.assertAll(() -> assertUnknown(GatewaySnapshotCodeParser::routeChannel),
            () -> assertUnknown(GatewaySnapshotCodeParser::httpMethod),
            () -> assertUnknown(GatewaySnapshotCodeParser::securityProfile),
            () -> assertUnknown(GatewaySnapshotCodeParser::authorizationMode),
            () -> assertUnknown(GatewaySnapshotCodeParser::requestCookieMode),
            () -> assertUnknown(GatewaySnapshotCodeParser::responseCookieMode),
            () -> assertUnknown(GatewaySnapshotCodeParser::guardRuleType),
            () -> assertUnknown(GatewaySnapshotCodeParser::guardDimension));
    }

    private <T extends Enum<T> & com.paytech.foundation.common.enums.CodeEnum> void assertRoundTrip(T[] values,
        java.util.function.Function<String, T> parser) {
        for (T value : values) {
            Assertions.assertSame(value, parser.apply(value.getCode()));
        }
    }

    private void assertUnknown(java.util.function.Function<String, ?> parser) {
        Assertions.assertThrows(InvalidGatewaySnapshotException.class, () -> parser.apply("UNKNOWN_CODE"));
    }
}
