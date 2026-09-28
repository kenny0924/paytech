package com.paytech.client.contract.configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequestFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.*;
import com.paytech.client.contract.core.dal.mapper.ClientProjectionMapper;
import com.paytech.client.contract.domainservice.repository.*;
import com.paytech.client.contract.domainservice.repository.impl.*;
import com.paytech.client.contract.domainservice.service.*;
import com.paytech.client.contract.domainservice.service.impl.*;
import com.paytech.client.contract.integration.*;
import com.paytech.client.contract.integration.impl.*;
import com.paytech.client.contract.service.handler.ClientTenantProjectionHandler;
import com.paytech.foundation.httpclient.api.*;
import com.paytech.foundation.id.*;
import com.paytech.platform.support.api.tenantprojection.TenantProjectionApi;
import com.paytech.platform.support.api.enums.TenantProjectionErrorCode;

/** 显式打开时接入同一 CCT 数据库和真实 Foundation；不启用外部路由或创建数据。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "onboarding", name = "recovery-enabled", havingValue = "true")
public class ClientOnboardingRecoveryConfiguration {
    @Bean public IdGenerator onboardingIdGenerator(Environment env, DataSource dataSource) {
        if (!env.getProperty("compensation.enabled", Boolean.class, false)) {
            throw new IllegalArgumentException("入驻恢复要求启用现有 CCT 数据库及 compensation.enabled");
        }
        return new SnowflakeIdGenerator(env.getRequiredProperty("onboarding.id.datacenter", Long.class),
            env.getRequiredProperty("onboarding.id.worker", Long.class));
    }
    @Bean public ClientProjectionRepository clientProjectionRepository(SqlSessionFactory factory) {
        return new ClientProjectionRepositoryImpl(new SqlSessionTemplate(factory).getMapper(ClientProjectionMapper.class));
    }
    @Bean public ClientProjectionSourceService clientProjectionSources(ClientProjectionRepository repository,
            PlatformTransactionManager transactions) {
        TransactionTemplate reads = new TransactionTemplate(transactions);
        reads.setReadOnly(true); reads.setTimeout(5);
        reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return new ClientProjectionSourceServiceImpl(repository, reads);
    }
    @Bean public ClientTenantProjectionClient clientTenantProjectionClient(Environment env,
            ObjectProvider<LoadBalancerClient> discovery) {
        TenantProjectionApi api = HttpServiceClientFactory.create(platformTransport(env,discovery),
            HttpServiceClientOptions.strict().withStrictJsonResponses(8192), TenantProjectionErrorCode.values())
            .createClient(TenantProjectionApi.class);
        return new ClientTenantProjectionClientImpl(api);
    }
    /** 同一 Platform 发现/测试传输边界供会话核对与 Tenant 恢复复用。 */
    static RestClient platformTransport(Environment env,ObjectProvider<LoadBalancerClient> discovery) {
        String url = env.getProperty("onboarding.platform-test-base-url", "");
        RestClient.Builder builder = RestClient.builder();
        if (url.isBlank()) {
            LoadBalancerClient balancer = discovery.getObject();
            builder.baseUrl("http://platform-support-service/platform-support").requestInterceptor((request, body, execution) -> {
                var instance = balancer.choose("platform-support-service");
                if (instance == null) { throw new ResourceAccessException("Platform discovery is unavailable"); }
                return balancer.execute("platform-support-service", instance,
                    new LoadBalancerRequestFactory(balancer).createRequest(request, body, execution));
            });
        } else {
            URI uri = URI.create(url);
            if (!Arrays.asList(env.getActiveProfiles()).contains("auto") || !"http".equals(uri.getScheme())
                || !"127.0.0.1".equals(uri.getHost()) || uri.getPort() < 1 || uri.getPort() > 65535
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !"/platform-support".equals(uri.getPath())) {
                throw new IllegalArgumentException("Platform 测试直连仅允许 auto 的 loopback HTTP /platform-support");
            }
            builder.baseUrl(url);
        }
        var transport = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build());
        transport.setReadTimeout(Duration.ofSeconds(3));
        return builder.requestFactory(transport).build();
    }
    @Bean public ClientTenantProjectionHandler clientTenantProjectionHandler(ClientProjectionSourceService source,
            ClientTenantProjectionClient platform) { return new ClientTenantProjectionHandler(source, platform); }
}
