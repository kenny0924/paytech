package com.paytech.platform.support.filedocument.integration.storage.minio;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;

/** MinIO 存储连接配置；仅在 `files.onboarding.storage-provider=MINIO` 时绑定并校验。 */
@Getter
@ConfigurationProperties("files.onboarding.minio")
public class MinioStorageProperties {
    /**
     * MinIO S3 接口地址
     * <li>必须为 HTTPS 根地址；同时是浏览器直传与下载授权的地址</li>
     */
    private final URI endpoint;
    /**
     * 签名区域
     * <li>必填，预签名时不再向服务端查询存储桶区域</li>
     */
    private final String region;
    /** 访问账号，只授予入驻文件存储桶。 */
    private final String accessKey;
    /** 访问密钥，不进入日志。 */
    private final String secretKey;
    /**
     * 私有 CA 或本机自签名证书 PEM
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

    /** 构造时校验必填项和范围，缺失配置使启动失败。 */
    public MinioStorageProperties(URI endpoint, String region, String accessKey, String secretKey,
        Path trustedCertificate, Duration connectTimeout, Duration readTimeout) {
        if (endpoint == null || !"https".equals(endpoint.getScheme()) || endpoint.getHost() == null
            || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
            || !(endpoint.getPath() == null || endpoint.getPath().isEmpty() || "/".equals(endpoint.getPath()))) {
            throw new IllegalArgumentException("files.onboarding.minio.endpoint 必须为 HTTPS 根地址");
        }
        if (isBlank(region) || isBlank(accessKey) || isBlank(secretKey)) {
            throw new IllegalArgumentException("files.onboarding.minio 的 region、access-key、secret-key 不能为空");
        }
        this.endpoint = endpoint;
        this.region = region;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.trustedCertificate = trustedCertificate;
        this.connectTimeout = bounded(connectTimeout, Duration.ofSeconds(2), Duration.ofSeconds(10), "connect-timeout");
        this.readTimeout = bounded(readTimeout, Duration.ofSeconds(10), Duration.ofSeconds(60), "read-timeout");
    }

    /** 未配置时取默认值，超出范围拒绝启动。 */
    static Duration bounded(Duration value, Duration fallback, Duration max, String name) {
        Duration actual = value == null ? fallback : value;
        if (actual.compareTo(Duration.ofSeconds(1)) < 0 || actual.compareTo(max) > 0) {
            throw new IllegalArgumentException("files.onboarding.minio." + name + " 超出允许范围");
        }
        return actual;
    }

    /** 空值或空白均视为缺失。 */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 不输出密钥。 */
    @Override
    public String toString() {
        return "MinioStorageProperties[endpoint=" + endpoint + "]";
    }
}
