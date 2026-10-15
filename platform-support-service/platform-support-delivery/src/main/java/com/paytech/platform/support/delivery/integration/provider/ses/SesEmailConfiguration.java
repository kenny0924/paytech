package com.paytech.platform.support.delivery.integration.provider.ses;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.vault.client.VaultClient;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/** 通知与 SES 同时启用时装配唯一 SES 发送账号；任一关闭都不创建 AWS 客户端。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("'${notification.enabled:false}' == 'true' && '${notification.ses.enabled:false}' == 'true'")
@EnableConfigurationProperties(SesEmailProperties.class)
public class SesEmailConfiguration {

    /** 作为 EmailProvider 加入精确账号绑定；配置凭据 Token 时必须存在 VaultClient，否则拒绝启动。 */
    @Bean
    SesEmailProvider sesEmailProvider(SesV2Client sesV2Client, SesEmailProperties properties,
        ObjectProvider<VaultClient> vaultClient) {
        var credentials = properties.usesVaultCredentials()
            ? new SesCredentialVault(vaultClient.getObject(), properties.getCredentialToken()) : null;
        return new SesEmailProvider(sesV2Client, properties, credentials);
    }

    /** 客户端随容器关闭；构造只组装配置，不发起网络请求。 */
    @Bean(destroyMethod = "close")
    SesV2Client sesV2Client(SesEmailProperties properties) {
        return client(properties);
    }

    /**
     * 按配置构造 SES v2 客户端。
     * <li>JDK HttpURLConnection 传输与有界超时，整次调用不超过连接加读超时</li>
     * <li>关闭 SDK 内置重试：外发是否再次执行只由投递状态机决定，适配器不得暗中重试</li>
     */
    public static SesV2Client client(SesEmailProperties properties) {
        var http = UrlConnectionHttpClient.builder().connectionTimeout(properties.getConnectTimeout())
            .socketTimeout(properties.getReadTimeout());
        var builder = SesV2Client.builder().region(Region.of(properties.getRegion()))
            .credentialsProvider(credentials(properties)).httpClient(http.build())
            .overrideConfiguration(override -> override.retryStrategy(AwsRetryStrategy.doNotRetry())
                .apiCallTimeout(properties.getConnectTimeout().plus(properties.getReadTimeout())));
        if (properties.getEndpoint() != null) {
            builder.endpointOverride(properties.getEndpoint());
        }
        return builder.build();
    }

    /**
     * 客户端级凭据。
     * <li>使用 Vault 凭据时每次请求单独提供，客户端级凭据直接拒绝，杜绝静默回退到默认凭据链</li>
     * <li>未配置 Token 时走 AWS 默认凭据链</li>
     */
    static AwsCredentialsProvider credentials(SesEmailProperties properties) {
        if (properties.usesVaultCredentials()) {
            return () -> {
                throw SdkClientException.create("SES 凭据须在每次发送前由 Vault 提供");
            };
        }
        return DefaultCredentialsProvider.builder().build();
    }
}
