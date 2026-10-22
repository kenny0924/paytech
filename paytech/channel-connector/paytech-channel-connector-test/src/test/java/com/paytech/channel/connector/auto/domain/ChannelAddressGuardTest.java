package com.paytech.channel.connector.auto.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.paytech.channel.connector.protocol.core.configuration.ExternalApiExecutionProperties;
import com.paytech.channel.connector.protocol.ext.outbound.ChannelAddressGuard;
import com.paytech.channel.connector.protocol.ext.outbound.ChannelAddressGuard.Verdict;

/**
 * 出站地址防护的网段规则（technique 第 6.4 节）：保存时只判断 IP 字面量与 localhost，执行时判断解析后的全部地址；
 * 回环、私网、链路本地、保留与组播地址被拦截，部署放行网段优先；IPv4 映射的 IPv6 按 IPv4 判断。只用 IP 字面量，不触发 DNS。
 *
 * @see ChannelAddressGuard#savingProblem(String)
 * @see ChannelAddressGuard#check(String)
 */
class ChannelAddressGuardTest {

    private final ChannelAddressGuard guard = guard(List.of());

    /** 被拦截网段的 IP 字面量与 localhost 在保存时拒绝，执行时判为 BLOCKED。 */
    @ParameterizedTest
    @ValueSource(strings = {"10.0.0.1", "172.16.5.4", "192.168.1.1", "127.0.0.1", "169.254.169.254", "100.64.0.1",
        "0.0.0.0", "224.0.0.251", "240.0.0.1", "198.18.0.1", "[::1]", "[fe80::1]", "[fd00::1]", "[ff02::1]",
        "[::ffff:10.0.0.1]"})
    void test_blockedLiterals(String host) {
        assertAll(() -> assertNotNull(guard.savingProblem(host)), () -> assertEquals(Verdict.BLOCKED, guard.check(host)));
    }

    /** 公网 IP 字面量允许；普通主机名在保存时不做解析、不拒绝。 */
    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "203.0.113.10", "[2001:4860:4860::8888]", "gateway.example.test"})
    void test_allowedWhenSaving(String host) {
        assertNull(guard.savingProblem(host));
    }

    /** localhost 与 *.localhost 保存时一律拒绝，放行网段只按 IP 匹配。 */
    @Test
    void test_localhostAlwaysBlockedWhenSaving() {
        ChannelAddressGuard trusting = guard(List.of("127.0.0.0/8"));

        assertAll(() -> assertNotNull(trusting.savingProblem("localhost")),
            () -> assertNotNull(trusting.savingProblem("API.LOCALHOST")),
            () -> assertNull(trusting.savingProblem("127.0.0.1")));
    }

    /** 放行网段内的私网地址允许，网段外仍拦截。 */
    @Test
    void test_trustedNetworks() {
        ChannelAddressGuard trusting = guard(List.of("10.20.0.0/16", "127.0.0.1/32"));

        assertAll(() -> assertEquals(Verdict.ALLOWED, trusting.check("10.20.3.4")),
            () -> assertNull(trusting.savingProblem("10.20.3.4")),
            () -> assertEquals(Verdict.ALLOWED, trusting.check("127.0.0.1")),
            () -> assertEquals(Verdict.BLOCKED, trusting.check("10.21.0.1")),
            () -> assertEquals(Verdict.BLOCKED, trusting.check("127.0.0.2")),
            () -> assertEquals(Verdict.ALLOWED, trusting.check("8.8.8.8")));
    }

    /** 放行网段格式错误时拒绝启动。 */
    @Test
    void test_invalidTrustedNetwork() {
        assertAll(() -> assertThrows(IllegalArgumentException.class, () -> guard(List.of("10.0.0.0"))),
            () -> assertThrows(IllegalArgumentException.class, () -> guard(List.of("10.0.0.0/33"))));
    }

    /** 以指定放行网段构造防护。 */
    private static ChannelAddressGuard guard(List<String> trustedNetworks) {
        ExternalApiExecutionProperties properties = new ExternalApiExecutionProperties();
        properties.setTrustedNetworks(trustedNetworks);
        return new ChannelAddressGuard(properties);
    }
}
