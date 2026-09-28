package com.paytech.platform.support.configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import com.paytech.client.contract.api.onboarding.ClientFileTargetApi;
import com.paytech.client.contract.api.enums.ClientDraftErrorCode;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.platform.support.integration.files.CctFileTargetClient;

/** 文件入口默认关闭；启用仍须真实 CLIENT 认证和 Redis，未配置供应商时不返回成功。 */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="files.onboarding.enabled",havingValue="true")
public class OnboardingFileConfiguration {
    /** 文件上传不接受关闭 IAM 或协调保护的运行模式。 */
    public OnboardingFileConfiguration(Environment environment) {
        if (!environment.getProperty("client.authentication.enabled",Boolean.class,false)
            || !environment.getProperty("redis.coordination.enabled",Boolean.class,false)
            || !environment.getProperty("compensation.enabled",Boolean.class,true))
            throw new IllegalStateException("入驻文件需要 CLIENT 认证、Redis 资源协调与本地补偿执行器");
    }
    /** 生产通过私网服务发现；仅 auto 允许显式 loopback 协议 Simulator。 */
    @Bean CctFileTargetClient cctFileTargetClient(Environment environment, ObjectProvider<LoadBalancerClient> discovery) {
        var builder=RestClient.builder();
        String url=environment.getProperty("files.onboarding.cct-test-base-url", "");
        if (url.isBlank()) {
            builder.baseUrl("http://client-contract-service").requestInterceptor((request,body,execution) -> {
                var balancer=discovery.getIfAvailable();
                var instance=balancer==null ? null : balancer.choose("client-contract-service");
                if (instance==null) throw new ResourceAccessException("CCT discovery unavailable");
                return balancer.execute("client-contract-service",instance,new LoadBalancerRequestFactory(balancer).createRequest(request,body,execution));
            });
        } else {
            URI uri=URI.create(url);
            if (!Arrays.asList(environment.getActiveProfiles()).contains("auto") || !"http".equals(uri.getScheme())
                || !"127.0.0.1".equals(uri.getHost()) || uri.getPort()<1 || uri.getPort()>65535
                || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || !uri.getPath().isEmpty())
                throw new IllegalArgumentException("CCT 测试直连仅允许 auto 的 loopback HTTP 根路径");
            builder.baseUrl(url);
        }
        var transport=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build());
        transport.setReadTimeout(Duration.ofSeconds(3));
        var api=HttpServiceClientFactory.create(builder.requestFactory(transport).build(),
            HttpServiceClientOptions.strict().withStrictJsonResponses(8192),ClientDraftErrorCode.values()).createClient(ClientFileTargetApi.class);
        return new CctFileTargetClient(api);
    }
}
