package com.paytech.platform.support.filedocument.integration.storage.s3;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;

/** AWS S3 存储连接配置；仅在 `files.onboarding.storage-provider=S3` 时绑定并校验。 */
@Getter
@ConfigurationProperties("files.onboarding.s3")
public class S3StorageProperties {
    /** AWS 区域，例如 me-central-1；必填。 */
    private final String region;
    /**
     * 终端地址覆盖
     * <li>可空；为空时使用区域默认终端。用于 VPC 终端或兼容 S3 的存储，必须为 HTTPS 根地址</li>
     */
    private final URI endpoint;
    /**
     * 路径风格访问
     * <li>默认 false；兼容 S3 的存储通常需要 true</li>
     */
    private final boolean pathStyleAccess;
    /**
     * 静态访问账号
     * <li>可空；与 secret-key 同时配置时使用，否则走 AWS 默认凭据链（推荐生产使用 IAM 角色）</li>
     */
    private final String accessKey;
    /** 静态访问密钥，不进入日志。 */
    private final String secretKey;
    /**
     * 私有 CA 证书 PEM
     * <li>可空；为空时使用 JDK 默认信任库</li>
     */
    private final Path trustedCertificate;
    /**
     * 连接超时
     * <li>默认 2 秒，范围 1—10 秒</li>
     */
    private final Duration connectTimeout;
    /**
     * 读写超时
     * <li>默认 10 秒，范围 1—60 秒</li>
     */
    private final Duration readTimeout;

    /** 构造时校验必填项、终端格式与凭据成对配置。 */
    public S3StorageProperties(String region, URI endpoint, Boolean pathStyleAccess, String accessKey, String secretKey,
        Path trustedCertificate, Duration connectTimeout, Duration readTimeout) {
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException("files.onboarding.s3.region 不能为空");
        }
        if (endpoint != null && (!"https".equals(endpoint.getScheme()) || endpoint.getHost() == null
            || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
            || !(endpoint.getPath() == null || endpoint.getPath().isEmpty() || "/".equals(endpoint.getPath())))) {
            throw new IllegalArgumentException("files.onboarding.s3.endpoint 必须为 HTTPS 根地址");
        }
        if ((accessKey == null || accessKey.isBlank()) != (secretKey == null || secretKey.isBlank())) {
            throw new IllegalArgumentException("files.onboarding.s3.access-key 与 secret-key 必须同时配置或同时为空");
        }
        this.region = region;
        this.endpoint = endpoint;
        this.pathStyleAccess = Boolean.TRUE.equals(pathStyleAccess);
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.trustedCertificate = trustedCertificate;
        this.connectTimeout = bounded(connectTimeout, Duration.ofSeconds(2), Duration.ofSeconds(10), "connect-timeout");
        this.readTimeout = bounded(readTimeout, Duration.ofSeconds(10), Duration.ofSeconds(60), "read-timeout");
    }

    /** 是否使用静态凭据。 */
    public boolean hasStaticCredentials() {
        return accessKey != null && !accessKey.isBlank();
    }

    /** 未配置时取默认值，超出范围拒绝启动。 */
    private static Duration bounded(Duration value, Duration fallback, Duration max, String name) {
        Duration actual = value == null ? fallback : value;
        if (actual.compareTo(Duration.ofSeconds(1)) < 0 || actual.compareTo(max) > 0) {
            throw new IllegalArgumentException("files.onboarding.s3." + name + " 超出允许范围");
        }
        return actual;
    }

    /** 不输出密钥。 */
    @Override
    public String toString() {
        return "S3StorageProperties[region=" + region + ", endpoint=" + endpoint + "]";
    }
}
