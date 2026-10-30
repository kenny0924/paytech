package com.paytech.gateway.auto.configuration;

import com.paytech.gateway.configuration.PlatformSnapshotProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;

    /** 缺省配置通过 Platform 服务发现获取完整路由快照。 */
class PlatformSnapshotDiscoveryPropertiesAutoTest {
    @Test
    void test_defaultUsesPlatformSupportServiceDiscovery() {
        PlatformSnapshotProperties properties = properties(null, null, false);

        assertEquals("platform-support-service", properties.serviceId());
    }

    /** 直连地址只有在显式本机模式下才可启用。 */
    @Test
    void test_directUriRequiresExplicitLocalMode() {
        assertThrows(IllegalArgumentException.class,
            () -> properties(URI.create("http://127.0.0.1:18106"), null, false));
    }

    /** 服务发现和直连地址不能同时成为控制面来源。 */
    @Test
    void test_discoveryAndDirectUriAreMutuallyExclusive() {
        assertThrows(IllegalArgumentException.class,
            () -> properties(URI.create("http://127.0.0.1:18106"), "platform-support-service", true));
    }

    /** 配置默认值和发现/本机直连模式属于可独立校验的启动合同。 */
    @Test
    void test_defaultsAndExplicitBoundaryConfiguration() {
        var defaults = new PlatformSnapshotProperties(null, " ", false, null, null, 0, null);
        assertEquals("platform-support-service", defaults.serviceId());
        assertEquals(Duration.ofSeconds(2), defaults.connectTimeout());
        assertEquals(Duration.ofSeconds(2), defaults.responseTimeout());
        assertEquals(Duration.ofSeconds(5), defaults.refreshInterval());
        assertEquals(4_194_304, defaults.maxResponseBytes());
        assertTrue(defaults.discoveryEnabled());
        var direct = new PlatformSnapshotProperties(URI.create("HTTP://localhost:12345"), null, true,
            Duration.ofSeconds(10), Duration.ofSeconds(10), 1, Duration.ofMillis(1));
        assertFalse(direct.discoveryEnabled());
        assertEquals("owner-service", properties(null, " owner-service ", false).serviceId());
    }

    /** 本机直连不能包含凭据、路径、查询、片段或改成 TLS/其他协议。 */
    @ParameterizedTest
    @ValueSource(strings = {"https://localhost", "http:opaque", "/relative", "http:///path", "http://localhost/",
        "http://localhost/path", "http://user@localhost", "http://localhost?query=1", "http://localhost#fragment"})
    void test_rejectsUnsafeControlPlaneBaseUri(String uri) {
        assertThrows(IllegalArgumentException.class, () -> properties(URI.create(uri), null, true));
    }

    /** 服务发现名称不能携带 URI、端口或非法标识。 */
    @ParameterizedTest
    @ValueSource(strings = {"OWNER", "owner.service", "owner:123", "-owner", "owner_service"})
    void test_rejectsMalformedDiscoveryName(String service) {
        assertThrows(IllegalArgumentException.class, () -> properties(null, service, false));
    }

    /** 控制面连接及响应等待必须有正数上限。 */
    @ParameterizedTest
    @MethodSource("invalidDurations")
    void test_controlTimeoutsMustBePositiveAndWithinTenSeconds(Duration duration) {
        assertThrows(IllegalArgumentException.class, () -> new PlatformSnapshotProperties(null, null, false,
            duration, Duration.ofSeconds(1), 1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new PlatformSnapshotProperties(null, null, false,
            Duration.ofSeconds(1), duration, 1, Duration.ofSeconds(1)));
    }

    /** 提供零、负数和超过绝对上限的时长。 */
    private static Stream<Duration> invalidDurations() {
        return Stream.of(Duration.ZERO, Duration.ofMillis(-1), Duration.ofMillis(10_001));
    }

    /** 刷新周期及响应大小配置不能关闭资源约束。 */
    @Test
    void test_refreshAndResponseSizeCannotDisableResourceBounds() {
        for (var interval : new Duration[] {Duration.ZERO, Duration.ofMillis(-1)}) {
            assertThrows(IllegalArgumentException.class, () -> new PlatformSnapshotProperties(null, null, false,
                null, null, 1, interval));
        }
        for (int bytes : new int[] {-1, 4_194_305}) {
            assertThrows(IllegalArgumentException.class, () -> new PlatformSnapshotProperties(null, null, false,
                null, null, bytes, null));
        }
        assertThrows(IllegalArgumentException.class, () -> properties(null, "s".repeat(64), false));
    }

    /** 构造符合本机场景的其余控制面连接参数。 */
    private PlatformSnapshotProperties properties(URI baseUri, String serviceId, boolean directUriEnabled) {
        return new PlatformSnapshotProperties(baseUri, serviceId, directUriEnabled, Duration.ofSeconds(1),
            Duration.ofSeconds(1), PlatformSnapshotProperties.MAX_RESPONSE_BYTES, Duration.ofSeconds(5));
    }
}
