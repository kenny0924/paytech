package com.paytech.platform.support.filedocument.integration.storage.minio;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.platform.support.filedocument.integration.storage.StorageTrust;

import io.minio.MinioClient;
import okhttp3.OkHttpClient;

/** 入驻文件启用且提供方为 MINIO 时装配唯一 MinIO 适配器；其他提供方不创建任何 MinIO 对象。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("'${files.onboarding.enabled:false}' == 'true' && '${files.onboarding.storage-provider:}' == 'MINIO'")
@EnableConfigurationProperties(MinioStorageProperties.class)
public class MinioStorageConfiguration {
    /** 同一适配器承担上传、完成读取、下载与检查读取四个端口。 */
    @Bean
    MinioFileStorage minioFileStorage(Clock clock, MinioStorageProperties properties) {
        return new MinioFileStorage(clock, client(properties));
    }

    /** 有界超时、禁止重定向；配置私有证书时只信任该证书。 */
    static MinioClient client(MinioStorageProperties properties) {
        OkHttpClient.Builder http = new OkHttpClient.Builder().connectTimeout(properties.getConnectTimeout())
            .readTimeout(properties.getReadTimeout()).writeTimeout(properties.getReadTimeout())
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false);
        if (properties.getTrustedCertificate() != null) {
            StorageTrust trust = StorageTrust.fromPem(properties.getTrustedCertificate());
            http.sslSocketFactory(trust.context().getSocketFactory(), trust.trustManager());
        }
        return MinioClient.builder().endpoint(properties.getEndpoint().toString()).region(properties.getRegion())
            .credentials(properties.getAccessKey(), properties.getSecretKey()).httpClient(http.build()).build();
    }
}
