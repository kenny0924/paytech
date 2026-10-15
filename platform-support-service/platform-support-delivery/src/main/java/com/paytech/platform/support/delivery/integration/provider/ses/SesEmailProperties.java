package com.paytech.platform.support.delivery.integration.provider.ses;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.delivery.domain.NotificationInput;

import lombok.Getter;

/** Amazon SES 发送账号配置；仅在 notification.enabled 与 notification.ses.enabled 同时为 true 时绑定，变更需重启。 */
@Getter
@ConfigurationProperties("notification.ses")
public class SesEmailProperties {

    /** 本机测试允许的 HTTP 主机；其他终端只接受 HTTPS。 */
    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    /**
     * 发送账号编码
     * <li>必填，与 t_notification_provider.provider_code 一致，格式 [A-Z][A-Z0-9_]{0,63}</li>
     */
    private final String providerCode;

    /** AWS 区域，例如 me-central-1；必填。 */
    private final String region;

    /**
     * 发件地址
     * <li>必填，必须是该区域 SES 已验证的身份；按平台邮箱规则规范化，只接受纯地址</li>
     */
    private final String fromAddress;

    /**
     * Configuration Set 名称
     * <li>可空，空白视为未配置；为后续投递事件回调预留，格式 [A-Za-z0-9_-]{1,64}</li>
     */
    private final String configurationSet;

    /**
     * 终端地址覆盖
     * <li>可空；为空时使用区域默认终端。必须为 HTTPS 根地址，或本机 HTTP 地址（仅测试），允许路径前缀</li>
     */
    private final URI endpoint;

    /**
     * IAM 用户密钥对的 Vault Token
     * <li>可空，空白视为未配置；格式同 Vault Token（43 字符无填充 Base64URL）</li>
     * <li>配置时每次发送前以 NOTIFICATION_DELIVERY 用途 Reveal，不回退默认凭据链</li>
     * <li>为空时走 AWS 默认凭据链（本机或 manual 使用 AWS_PROFILE 等）；Token 不进入日志</li>
     */
    private final String credentialToken;

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

    /** 构造时校验必填项、地址、终端与 Token 格式；任一失败拒绝启动。 */
    public SesEmailProperties(String providerCode, String region, String fromAddress, String configurationSet,
        URI endpoint, String credentialToken, Duration connectTimeout, Duration readTimeout) {
        if (providerCode == null || !providerCode.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("notification.ses.provider-code 不合法");
        }
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException("notification.ses.region 不能为空");
        }
        String configured = configurationSet == null || configurationSet.isBlank() ? null : configurationSet;
        if (configured != null && !configured.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("notification.ses.configuration-set 不合法");
        }
        String token = credentialToken == null || credentialToken.isBlank() ? null : credentialToken;
        if (token != null && !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("notification.ses.credential-token 不合法");
        }
        this.providerCode = providerCode;
        this.region = region;
        this.fromAddress = canonicalFrom(fromAddress);
        this.configurationSet = configured;
        this.endpoint = validEndpoint(endpoint);
        this.credentialToken = token;
        this.connectTimeout = bounded(connectTimeout, Duration.ofSeconds(2), Duration.ofSeconds(10), "connect-timeout");
        this.readTimeout = bounded(readTimeout, Duration.ofSeconds(5), Duration.ofSeconds(15), "read-timeout");
    }

    /** 是否从 Vault 读取凭据。 */
    public boolean usesVaultCredentials() {
        return credentialToken != null;
    }

    /** 发件地址沿用平台邮箱规范化，拒绝显示名、换行等可注入 Header 的形式。 */
    static String canonicalFrom(String fromAddress) {
        if (fromAddress == null) {
            throw new IllegalArgumentException("notification.ses.from-address 不能为空");
        }
        try {
            return NotificationInput.canonicalEmail(fromAddress);
        } catch (InvalidParameterException invalid) {
            // 原异常只是通用的格式提示，启动错误直接指出配置键。
            throw new IllegalArgumentException("notification.ses.from-address 不是受支持的纯邮箱地址");
        }
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

    /** 不输出凭据 Token。 */
    @Override
    public String toString() {
        return "SesEmailProperties[providerCode=" + providerCode + ", region=" + region + ", endpoint=" + endpoint + "]";
    }
}
