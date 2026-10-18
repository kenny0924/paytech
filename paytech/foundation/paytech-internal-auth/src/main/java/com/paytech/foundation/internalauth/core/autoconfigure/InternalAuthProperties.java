package com.paytech.foundation.internalauth.core.autoconfigure;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.paytech.foundation.internalauth.api.InternalAuthMode;

/**
 * {@code internal-auth.*} 配置；另读取 {@code spring.application.name}（本服务名）与 {@code paytech.instance-id}（实例 ID）。
 *
 * @author Claude
 */
@ConfigurationProperties(prefix = "internal-auth", ignoreUnknownFields = false)
public class InternalAuthProperties {

    /**
     * 入站校验模式。
     * <li>默认 OFF：不创建任何校验 Bean，行为与引入前一致。</li>
     */
    private InternalAuthMode mode = InternalAuthMode.OFF;

    /**
     * Platform JWKS 地址。
     * <li>mode 非 OFF 且未配置 static-key 时必填。</li>
     */
    private URI jwksUri;

    /**
     * JWKS 缓存有效期，默认 5 分钟。
     */
    private Duration jwksCacheTtl = Duration.ofMinutes(5);

    /**
     * JWKS 连接与读取超时，默认 2 秒。
     */
    private Duration jwksTimeout = Duration.ofSeconds(2);

    /**
     * kid 未命中触发强制刷新的最小间隔，默认 10 秒。
     */
    private Duration jwksForcedRefreshInterval = Duration.ofSeconds(10);

    /**
     * 不校验的路径前缀。
     * <li>默认 {@code /actuator}、{@code /public/}。</li>
     */
    private List<String> exemptPathPrefixes = new ArrayList<>(List.of("/actuator", "/public/"));

    /**
     * 仅限本地 / mock 的静态密钥对；prod Profile 下配置即启动失败。
     */
    private final StaticKey staticKey = new StaticKey();

    public InternalAuthMode getMode() {
        return mode;
    }

    public void setMode(InternalAuthMode mode) {
        this.mode = mode;
    }

    public URI getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(URI jwksUri) {
        this.jwksUri = jwksUri;
    }

    public Duration getJwksCacheTtl() {
        return jwksCacheTtl;
    }

    public void setJwksCacheTtl(Duration jwksCacheTtl) {
        this.jwksCacheTtl = jwksCacheTtl;
    }

    public Duration getJwksTimeout() {
        return jwksTimeout;
    }

    public void setJwksTimeout(Duration jwksTimeout) {
        this.jwksTimeout = jwksTimeout;
    }

    public Duration getJwksForcedRefreshInterval() {
        return jwksForcedRefreshInterval;
    }

    public void setJwksForcedRefreshInterval(Duration jwksForcedRefreshInterval) {
        this.jwksForcedRefreshInterval = jwksForcedRefreshInterval;
    }

    public List<String> getExemptPathPrefixes() {
        return exemptPathPrefixes;
    }

    public void setExemptPathPrefixes(List<String> exemptPathPrefixes) {
        this.exemptPathPrefixes = exemptPathPrefixes;
    }

    public StaticKey getStaticKey() {
        return staticKey;
    }

    /** 本地静态 ES256 密钥对。 */
    public static class StaticKey {

        /**
         * 含私钥的 P-256 JWK JSON，必须带 kid。
         */
        private String privateJwk;

        /**
         * 对应公钥 JWK JSON。
         */
        private String publicJwk;

        public String getPrivateJwk() {
            return privateJwk;
        }

        public void setPrivateJwk(String privateJwk) {
            this.privateJwk = privateJwk;
        }

        public String getPublicJwk() {
            return publicJwk;
        }

        public void setPublicJwk(String publicJwk) {
            this.publicJwk = publicJwk;
        }

        /**
         * 是否配置了任一静态密钥字段。
         *
         * @return 配置了返回 true
         */
        public boolean configured() {
            return privateJwk != null || publicJwk != null;
        }
    }
}
