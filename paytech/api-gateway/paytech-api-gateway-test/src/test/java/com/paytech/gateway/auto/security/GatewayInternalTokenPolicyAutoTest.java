package com.paytech.gateway.auto.security;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.paytech.foundation.internalauth.api.InternalAuthMode;
import com.paytech.foundation.internalauth.api.InternalTokenClaims;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.gateway.configuration.PlatformCredentialValidationProperties;
import com.paytech.gateway.configuration.PlatformJwksProperties;
import com.paytech.gateway.configuration.PlatformSnapshotProperties;
import com.paytech.gateway.security.GatewayInternalTokenPolicy;

import reactor.test.StepVerifier;

/**
 * 出站 Token 策略的模式判定：OFF 不附加；非 OFF 必须有 Token 来源，否则启动失败。
 *
 * @see GatewayInternalTokenPolicy#GatewayInternalTokenPolicy(InternalAuthMode, InternalTokenSupplier)
 * @see GatewayInternalTokenPolicy#token(String)
 * @see PlatformSnapshotProperties#tokenAudience()
 * @see PlatformJwksProperties#tokenAudience()
 * @see PlatformCredentialValidationProperties#tokenAudience()
 */
class GatewayInternalTokenPolicyAutoTest {

    private static final InternalTokenSupplier SUPPLIER = (target, purpose) -> target + "|" + purpose;

    @Test
    void test_normalFlow_offIgnoresSupplier() {
        GatewayInternalTokenPolicy policy = new GatewayInternalTokenPolicy(InternalAuthMode.OFF, SUPPLIER);

        Assertions.assertFalse(policy.enabled());
        StepVerifier.create(policy.token("operations-console-bff")).verifyComplete();
    }

    @Test
    void test_normalFlow_requiredUsesGeneralPurposeForTarget() {
        GatewayInternalTokenPolicy policy = new GatewayInternalTokenPolicy(InternalAuthMode.REQUIRED, SUPPLIER);

        Assertions.assertTrue(policy.enabled());
        StepVerifier.create(policy.token("operations-console-bff"))
            .expectNext("operations-console-bff|" + InternalTokenClaims.GENERAL_PURPOSE).verifyComplete();
    }

    @ParameterizedTest
    @EnumSource(value = InternalAuthMode.class, names = {"DUAL", "REQUIRED"})
    void test_invalidParameter_enabledModeWithoutSupplierFailsStartup(InternalAuthMode mode) {
        IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class,
            () -> new GatewayInternalTokenPolicy(mode, null));

        Assertions.assertTrue(failure.getMessage().contains("internal-auth.mode=" + mode));
    }

    @Test
    void test_normalFlow_platformTokenAudienceFollowsServiceDiscoveryName() {
        URI local = URI.create("http://127.0.0.1:18106");
        Assertions.assertAll(
            () -> Assertions.assertEquals("platform-custom",
                new PlatformSnapshotProperties(null, "platform-custom", false, null, null, 0, null).tokenAudience()),
            () -> Assertions.assertEquals("platform-support-service",
                new PlatformSnapshotProperties(local, null, true, null, null, 0, null).tokenAudience()),
            () -> Assertions.assertEquals("platform-custom",
                new PlatformJwksProperties(null, "platform-custom", false).tokenAudience()),
            () -> Assertions.assertEquals("platform-support-service",
                new PlatformJwksProperties(local, null, true).tokenAudience()),
            () -> Assertions.assertEquals("platform-custom",
                new PlatformCredentialValidationProperties(null, "platform-custom", false, null, null, 0)
                    .tokenAudience()),
            () -> Assertions.assertEquals("platform-support-service",
                new PlatformCredentialValidationProperties(local, null, true, Duration.ofSeconds(1),
                    Duration.ofSeconds(1), 0).tokenAudience()));
    }
}
