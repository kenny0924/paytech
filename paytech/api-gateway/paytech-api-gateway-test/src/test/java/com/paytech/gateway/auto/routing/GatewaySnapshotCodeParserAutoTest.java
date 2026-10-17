package com.paytech.gateway.auto.routing;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Function;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.paytech.gateway.routing.snapshot.InvalidGatewaySnapshotException;
import com.paytech.platform.support.api.enums.*;

/**
 * Platform 发布的 Gateway Code 与 Snapshot 解析器逐值往返，未知 Code 失败关闭。
 * <li>解析器为包级可见类；测试位于 auto 包，经反射调用其静态方法，不为测试放宽生产可见性。</li>
 *
 * @see com.paytech.gateway.routing.snapshot.GatewaySnapshotCompiler
 */
class GatewaySnapshotCodeParserAutoTest {

    private static final String PARSER = "com.paytech.gateway.routing.snapshot.GatewaySnapshotCodeParser";

    @Test
    void test_parsesEveryPublishedPlatformGatewayCode() {
        Assertions.assertAll(
            () -> assertRoundTrip(GatewayRouteChannel.values(), parser("routeChannel")),
            () -> assertRoundTrip(GatewayHttpMethod.values(), parser("httpMethod")),
            () -> assertRoundTrip(GatewaySecurityProfileCode.values(), parser("securityProfile")),
            () -> assertRoundTrip(GatewayAuthorizationMode.values(), parser("authorizationMode")),
            () -> assertRoundTrip(GatewayRequestCookieMode.values(), parser("requestCookieMode")),
            () -> assertRoundTrip(GatewayResponseCookieMode.values(), parser("responseCookieMode")),
            () -> assertRoundTrip(GatewayGuardRuleType.values(), parser("guardRuleType")),
            () -> assertRoundTrip(GatewayGuardDimension.values(), parser("guardDimension")),
            () -> assertRoundTrip(GatewayIdempotencyMode.values(), parser("idempotencyMode")));
    }

    @Test
    void test_rejectsUnknownCodeForEveryPlatformGatewayCodeType() {
        Assertions.assertAll(() -> assertUnknown(parser("routeChannel")),
            () -> assertUnknown(parser("httpMethod")),
            () -> assertUnknown(parser("securityProfile")),
            () -> assertUnknown(parser("authorizationMode")),
            () -> assertUnknown(parser("requestCookieMode")),
            () -> assertUnknown(parser("responseCookieMode")),
            () -> assertUnknown(parser("guardRuleType")),
            () -> assertUnknown(parser("guardDimension")),
            () -> assertUnknown(parser("idempotencyMode")));
    }

    @Test
    void test_missingIdempotencyModeFromOlderPlatformMeansNone() {
        Assertions.assertSame(GatewayIdempotencyMode.NONE, parser("idempotencyMode").apply(null));
        Assertions.assertThrows(InvalidGatewaySnapshotException.class,
            () -> parser("idempotencyMode").apply(""));
    }

    private <T extends Enum<T> & com.paytech.foundation.common.enums.CodeEnum> void assertRoundTrip(T[] values,
        Function<String, ?> parser) {
        for (T value : values) {
            Assertions.assertSame(value, parser.apply(value.getCode()));
        }
    }

    private void assertUnknown(Function<String, ?> parser) {
        Assertions.assertThrows(InvalidGatewaySnapshotException.class, () -> parser.apply("UNKNOWN_CODE"));
    }

    /**
     * 以反射取解析器的一个静态解析方法，并还原其抛出的原始异常。
     *
     * @param method 解析方法名
     * @return 解析函数
     */
    private static Function<String, Object> parser(String method) {
        Method target;
        try {
            target = Class.forName(PARSER).getDeclaredMethod(method, String.class);
            target.setAccessible(true);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Snapshot Code 解析方法不存在: " + method, exception);
        }
        return code -> {
            try {
                return target.invoke(null, code);
            } catch (InvocationTargetException exception) {
                if (exception.getCause() instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new AssertionError(exception.getCause());
            } catch (IllegalAccessException exception) {
                throw new AssertionError(exception);
            }
        };
    }
}
