package com.paytech.platform.support.delivery.integration.provider.ses;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;

/**
 * Amazon SES 适配器的环境级配置；仅在 notification.enabled 与 notification.ses.enabled 同时为 true 时绑定，变更需重启
 * <li>账号的区域、发件地址、Configuration Set 与密钥保存在 t_notification_provider，由运营后台维护</li>
 */
@Getter
@ConfigurationProperties("notification.ses")
public class SesEmailProperties {

    /** 本机测试允许的 HTTP 主机；其他终端只接受 HTTPS。 */
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    /**
     * 终端地址覆盖
     * <li>可空；为空时使用账号区域的默认终端。必须为 HTTPS 根地址，或本机 HTTP 地址（仅测试），允许路径前缀</li>
     * <li>对全部账号生效</li>
     */
    private final URI endpoint;

    /**
     * 连接超时
     * <li>默认 2 秒，范围 1—10 秒</li>
     */
    private final Duration connectTimeout;

    /**
     * 读超时
     * <li>默认 5 秒，范围 1—15 秒；须小于补偿单次执行时限 20 秒</li>
     */
    private final Duration readTimeout;

    /** 构造时校验终端与超时；任一失败拒绝启动。 */
    public SesEmailProperties(URI endpoint, Duration connectTimeout, Duration readTimeout) {
        this.endpoint = validEndpoint(endpoint);
        this.connectTimeout = bounded(connectTimeout, Duration.ofSeconds(2), Duration.ofSeconds(10), "connect-timeout");
        this.readTimeout = bounded(readTimeout, Duration.ofSeconds(5), Duration.ofSeconds(15), "read-timeout");
    }

    /** 终端为空时使用区域默认值；否则只接受 HTTPS 或本机 HTTP，不允许账号、查询串或片段。 */
    static URI validEndpoint(URI endpoint) {
        if (endpoint == null) {
            return null;
        }
        boolean https = "https".equals(endpoint.getScheme());
        boolean loopback = "http".equals(endpoint.getScheme()) && LOOPBACK_HOSTS.contains(endpoint.getHost());
        if (endpoint.getHost() == null || !(https || loopback) || endpoint.getRawUserInfo() != null
            || endpoint.getRawQuery() != null || endpoint.getRawFragment() != null) {
            throw new IllegalArgumentException("notification.ses.endpoint 必须为 HTTPS 地址或本机测试地址");
        }
        return endpoint;
    }

    /** 未配置时取默认值，超出范围拒绝启动。 */
    static Duration bounded(Duration value, Duration fallback, Duration max, String name) {
        Duration actual = value == null ? fallback : value;
        if (actual.compareTo(Duration.ofSeconds(1)) < 0 || actual.compareTo(max) > 0) {
            throw new IllegalArgumentException("notification.ses." + name + " 超出允许范围");
        }
        return actual;
    }
}
