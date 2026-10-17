package com.paytech.gateway.auto.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.gateway.web.GatewayRequestIdPolicy;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

class GatewayRequestIdAutoTest {
    @Test
    void clientApiPreservesOnlyOneValidExternalValueIncludingThe64CharacterBoundary() {
        GatewayRequestIdPolicy policy = new GatewayRequestIdPolicy(() -> {
            throw new AssertionError("合法 Client API Request ID 不应生成替代值");
        });
        for (String value : List.of("A", "0", "Client-123._retry", "A".repeat(64))) {
            assertEquals(value, policy.resolve(GatewayRouteChannel.CLIENT_API, List.of(value)));
        }
    }

    @ParameterizedTest
    @MethodSource("rejectedValues")
    void missingDuplicateOrInvalidClientApiValueGeneratesExactlyOneFallback(List<String> values) {
        AtomicInteger calls = new AtomicInteger();
        GatewayRequestIdPolicy policy = new GatewayRequestIdPolicy(() -> "generated-" + calls.incrementAndGet());

        assertEquals("generated-1", policy.resolve(GatewayRouteChannel.CLIENT_API, values));
        assertEquals(1, calls.get());
    }

    @Test
    void everyOtherChannelIncludingOperationsAlwaysGeneratesANewValue() {
        GatewayRequestIdPolicy policy = new GatewayRequestIdPolicy(() -> "generated");
        for (GatewayRouteChannel channel : GatewayRouteChannel.values()) {
            if (channel != GatewayRouteChannel.CLIENT_API) {
                assertEquals("generated", policy.resolve(channel, List.of("valid-external")));
            }
        }
        assertEquals("generated", policy.resolve(null, List.of("valid-external")));
    }

    @ParameterizedTest
    @MethodSource("invalidGeneratedValues")
    void generatedValueMustAlsoMatchTheSharedProblemProfile(String generatedValue) {
        GatewayRequestIdPolicy policy = new GatewayRequestIdPolicy(() -> generatedValue);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> policy.resolve(GatewayRouteChannel.OPERATIONS, List.of("ignored-external")));

        assertEquals("Generated request ID does not match the Problem Profile", failure.getMessage());
    }

    private static Stream<List<String>> rejectedValues() {
        return Stream.of(null, List.of(), List.of("one", "two"), List.of("same", "same"), List.of(""),
            List.of("A".repeat(65)), List.of("-leading"), List.of("_leading"), List.of(".leading"),
            List.of("with space"), List.of(" trailing "), List.of("a,b"), List.of("a/b"), List.of("é"),
            List.of("a\n"), List.of("a\r"), List.of("a\t"));
    }

    private static Stream<String> invalidGeneratedValues() {
        return Stream.of(null, "", "bad/id", "A".repeat(65), "-leading", "with space");
    }
}
