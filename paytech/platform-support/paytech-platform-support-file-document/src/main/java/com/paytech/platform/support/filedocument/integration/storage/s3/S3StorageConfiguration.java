package com.paytech.platform.support.filedocument.integration.storage.s3;

import java.time.Clock;

import javax.net.ssl.TrustManager;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.platform.support.filedocument.integration.storage.StorageTrust;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** 入驻文件启用且提供方为 S3 时装配唯一 S3 适配器；其他提供方不创建任何 AWS 客户端。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("'${files.onboarding.enabled:false}' == 'true' && '${files.onboarding.storage-provider:}' == 'S3'")
@EnableConfigurationProperties(S3StorageProperties.class)
public class S3StorageConfiguration {
    /** 同一适配器承担上传、完成读取、下载与检查读取四个端口；客户端随容器关闭。 */
    @Bean
    S3FileStorage s3FileStorage(Clock clock, S3Client s3Client, S3Presigner s3Presigner) {
        return new S3FileStorage(clock, s3Client, s3Presigner);
    }

    /** 使用 JDK 连接，有界超时；配置私有证书时只信任该证书。 */
    @Bean(destroyMethod = "close")
    S3Client s3Client(S3StorageProperties properties) {
        var builder = S3Client.builder().region(Region.of(properties.getRegion())).credentialsProvider(credentials(properties))
            .httpClient(http(properties)).forcePathStyle(properties.isPathStyleAccess());
        if (properties.getEndpoint() != null) {
            builder.endpointOverride(properties.getEndpoint());
        }
        return builder.build();
    }

    /** 预签名只在本地计算，不发起网络请求。 */
    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(S3StorageProperties properties) {
        var builder = S3Presigner.builder().region(Region.of(properties.getRegion())).credentialsProvider(credentials(properties))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(properties.isPathStyleAccess()).build());
        if (properties.getEndpoint() != null) {
            builder.endpointOverride(properties.getEndpoint());
        }
        return builder.build();
    }

    /** 静态凭据成对配置时使用，否则走默认凭据链。 */
    static AwsCredentialsProvider credentials(S3StorageProperties properties) {
        if (properties.hasStaticCredentials()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey()));
        }
        return DefaultCredentialsProvider.builder().build();
    }

    /** JDK HttpURLConnection 传输，避免引入额外连接池依赖。 */
    static SdkHttpClient http(S3StorageProperties properties) {
        var builder = UrlConnectionHttpClient.builder().connectionTimeout(properties.getConnectTimeout())
            .socketTimeout(properties.getReadTimeout());
        if (properties.getTrustedCertificate() != null) {
            StorageTrust trust = StorageTrust.fromPem(properties.getTrustedCertificate());
            builder.tlsTrustManagersProvider(() -> new TrustManager[] {trust.trustManager()});
        }
        return builder.build();
    }
}
