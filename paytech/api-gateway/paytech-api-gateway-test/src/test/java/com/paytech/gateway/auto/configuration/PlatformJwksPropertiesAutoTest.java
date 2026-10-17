package com.paytech.gateway.auto.configuration;

import java.net.URI;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.paytech.gateway.configuration.PlatformJwksProperties;

class PlatformJwksPropertiesAutoTest {

    /** 未配置直连时使用 Platform 服务发现读取签名公钥。 */
    @Test
    void test_defaultsToPlatformSupportDiscovery() {
        PlatformJwksProperties properties = new PlatformJwksProperties(null, null, false);

        Assertions.assertAll(() -> Assertions.assertEquals("platform-support-service", properties.serviceId()),
            () -> Assertions.assertTrue(properties.discoveryEnabled()),
            () -> Assertions.assertNull(properties.baseUri()));
    }

    /** JWKS 直连只有在显式本机模式下才可启用。 */
    @Test
    void test_directUriRequiresExplicitTestSwitch() {
        URI baseUri = URI.create("http://127.0.0.1:18082");

    /** JWKS 来源必须是无凭据、查询和路径的私有 HTTP 基址。 */
        Assertions.assertThrows(IllegalArgumentException.class, () -> new PlatformJwksProperties(baseUri, null, false));
        Assertions.assertDoesNotThrow(() -> new PlatformJwksProperties(baseUri, null, true));
    }
    @ParameterizedTest
    @ValueSource(strings = {"https://localhost", "http:opaque", "http:///path", "http://localhost/",
        "http://user@localhost", "http://localhost?query=1", "http://localhost#fragment"})
    void test_rejectsNonBaseOrNonPrivateUri(String uri) {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new PlatformJwksProperties(URI.create(uri), null, true));
    }

    /** JWKS 服务发现与直连配置互斥，并规范化发现名称。 */
    @Test
    void test_discoveryIdentityAndDirectUriRemainMutuallyExclusive() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new PlatformJwksProperties(URI.create("http://localhost:12345"), "owner", true));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new PlatformJwksProperties(null, "OWNER", false));
        Assertions.assertEquals("owner", new PlatformJwksProperties(null, " owner ", false).serviceId());
        Assertions.assertEquals("platform-support-service", new PlatformJwksProperties(null, " ", false).serviceId());
        Assertions.assertFalse(new PlatformJwksProperties(URI.create("http://localhost:12345"), null, true).discoveryEnabled());
    }
}
