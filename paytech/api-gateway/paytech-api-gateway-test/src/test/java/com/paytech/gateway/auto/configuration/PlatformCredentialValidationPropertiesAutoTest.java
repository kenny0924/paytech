package com.paytech.gateway.auto.configuration;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.env.MockEnvironment;

import com.paytech.gateway.configuration.PlatformCredentialValidationProperties;

class PlatformCredentialValidationPropertiesAutoTest {
    @Test
    void test_defaultsUseDiscoveryAndBoundedBudgets() {
        var properties = new PlatformCredentialValidationProperties(null, null, false, null, null, 0);
        assertTrue(properties.discoveryEnabled());
        assertEquals("platform-support-service", properties.serviceId());
        assertEquals(Duration.ofSeconds(2), properties.responseTimeout());
        assertEquals(PlatformCredentialValidationProperties.MAX_RESPONSE_BYTES, properties.maxResponseBytes());
    }

    @Test
    void test_directUriRequiresOnlyAutoProfileAndExplicitSwitch() {
        URI uri = URI.create("http://127.0.0.1:18099");
        assertThrows(IllegalArgumentException.class, () -> new PlatformCredentialValidationProperties(uri, null, false, null, null, 0));
        var properties = new PlatformCredentialValidationProperties(uri, null, true, null, null, 0);
        MockEnvironment environment = new MockEnvironment();
        assertThrows(IllegalStateException.class, () -> properties.validateEnvironment(environment));
        environment.setActiveProfiles("auto");
        assertDoesNotThrow(() -> properties.validateEnvironment(environment));
        environment.setActiveProfiles("auto", "local");
        assertThrows(IllegalStateException.class, () -> properties.validateEnvironment(environment));
    }

    @ParameterizedTest
    @MethodSource("invalidUris")
    void test_directUriRejectsAnythingOutsideLoopbackOrigin(String value) {
        assertThrows(IllegalArgumentException.class, () -> new PlatformCredentialValidationProperties(URI.create(value), null, true, null, null, 0));
    }

    static Stream<String> invalidUris() {
        return Stream.of("https://127.0.0.1:1", "http://other.test:1", "http://127.0.0.1", "http://127.0.0.1:1/path",
            "http://user@127.0.0.1:1", "http://127.0.0.1:1?x=y", "http://127.0.0.1:1#x");
    }

    @Test
    void test_timeoutAndResponseLimitsCannotExceedSafetyCeilings() {
        for (Duration timeout : java.util.List.of(Duration.ZERO, Duration.ofNanos(1), Duration.ofSeconds(-1), Duration.ofSeconds(11))) {
            assertThrows(IllegalArgumentException.class, () -> new PlatformCredentialValidationProperties(null, null, false, timeout, null, 0));
            assertThrows(IllegalArgumentException.class, () -> new PlatformCredentialValidationProperties(null, null, false, null, timeout, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> new PlatformCredentialValidationProperties(null, null, false, null, null, -1));
        assertThrows(IllegalArgumentException.class, () -> new PlatformCredentialValidationProperties(null, null, false, null, null, PlatformCredentialValidationProperties.MAX_RESPONSE_BYTES + 1));
    }
}
