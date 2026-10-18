package com.paytech.gateway.auto.security;

import java.net.InetAddress;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.paytech.gateway.security.GatewayHmacProperties;
import com.paytech.gateway.security.VersionedHmacDigester;

/** Access Log HMAC 地址编码和主体假名化的纯计算测试。 */
class VersionedHmacDigesterAutoTest {
    private static final String ACCESS_CURRENT = "YWNjZXNzLWxvZy1zZWNyZXQtbWF0ZXJpYWwtMzJieXRlcyE=";

    private static VersionedHmacDigester digester() {
        return new VersionedHmacDigester(
            new GatewayHmacProperties(new GatewayHmacProperties.Secret("access-current", ACCESS_CURRENT)));
    }

    @Test
    void test_accessLogHmacUsesFixedVectorsForCanonicalAddressesAndUtf8Subjects() throws Exception {
        VersionedHmacDigester digester = digester();

        Assertions.assertEquals(
            new VersionedHmacDigester.VersionedDigest("access-current", "KeuDFRMyCd7oD8sAjpZMFOMfO5jm8wcHgwQ-TaiJL6s"),
            digester.accessIpDigest(InetAddress.getByName("203.0.113.9")));
        Assertions.assertEquals(
            new VersionedHmacDigester.VersionedDigest("access-current", "_3ZMt2Lqgr-8vfgp7nuFo8lFMMvM1DAKU9xw-Muz_8c"),
            digester.accessSubjectDigest("运营-user-42"));
    }

    @Test
    void test_accessLogHmacNeverReturnsRawIdentity() throws Exception {
        VersionedHmacDigester digester = digester();
        String subject = "运营-user-42";
        String rawIp = "203.0.113.9";

        String accessIp = digester.accessIpDigest(InetAddress.getByName(rawIp)).digest();
        String accessSubject = digester.accessSubjectDigest(subject).digest();

        Assertions.assertNotEquals(accessIp, accessSubject);
        Assertions.assertFalse(accessIp.contains(rawIp));
        Assertions.assertFalse(accessSubject.contains(subject));
        Assertions.assertFalse(accessIp.contains("="));
        Assertions.assertTrue(accessIp.matches("[A-Za-z0-9_-]{43}"));
    }

    @Test
    void test_accessLogSecretRejectsMissingInvalidAndShortMaterial() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new GatewayHmacProperties(null));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new GatewayHmacProperties(new GatewayHmacProperties.Secret("access-current", "not base64")));
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new GatewayHmacProperties(new GatewayHmacProperties.Secret("access-current", "c2hvcnQ=")));
    }
}
