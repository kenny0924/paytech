package com.paytech.platform.support.delivery.integration.provider.ses;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.vault.client.VaultClient;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/** 通知与 SES 同时启用时注册 AMAZON_SES 适配器；任一关闭都不创建 AWS 客户端。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("'${notification.enabled:false}' == 'true' && '${notification.ses.enabled:false}' == 'true'")
@EnableConfigurationProperties(SesEmailProperties.class)
public class SesEmailConfiguration {

    /** 作为 EmailProvider 按类型注册；密钥一律来自 Vault，缺少 VaultClient 时拒绝启动。 */
    @Bean
    SesEmailProvider sesEmailProvider(SesClients sesClients, VaultClient vaultClient) {
        return new SesEmailProvider(sesClients, new SesCredentialVault(vaultClient));
    }

    /** 按区域复用的客户端，随容器关闭。 */
    @Bean(destroyMethod = "close")
    SesClients sesClients(SesEmailProperties properties) {
        return new SesClients(properties);
    }

    /**
     * 按环境配置与账号区域构造 SES v2 客户端。
     * <li>JDK HttpURLConnection 传输与有界超时，整次调用不超过连接加读超时</li>
     * <li>关闭 SDK 内置重试：外发是否再次执行只由投递状态机决定，适配器不得暗中重试</li>
     * <li>客户端级凭据直接拒绝，密钥只能由每次请求从 Vault 提供，杜绝回退到默认凭据链</li>
     */
    public static SesV2Client client(SesEmailProperties properties, String region) {
        var http = UrlConnectionHttpClient.builder().connectionTimeout(properties.getConnectTimeout())
            .socketTimeout(properties.getReadTimeout());
        var builder = SesV2Client.builder().region(Region.of(region)).credentialsProvider(rejectClientCredentials())
            .httpClient(http.build())
            .overrideConfiguration(override -> override.retryStrategy(AwsRetryStrategy.doNotRetry())
                .apiCallTimeout(properties.getConnectTimeout().plus(properties.getReadTimeout())));
        if (properties.getEndpoint() != null) {
            builder.endpointOverride(properties.getEndpoint());
        }
        return builder.build();
    }

    /** 客户端级凭据：任何未按请求提供密钥的调用都失败。 */
    static AwsCredentialsProvider rejectClientCredentials() {
        return () -> {
            throw SdkClientException.create("SES 凭据须在每次发送前由 Vault 提供");
        };
    }
}
