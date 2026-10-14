package com.paytech.client.contract.configuration;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.*;
import java.util.Arrays;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.cloud.client.loadbalancer.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.*;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.httpclient.api.*;
import com.paytech.client.contract.core.dal.mapper.ClientDocumentMapper;
import com.paytech.client.contract.domainservice.repository.*;
import com.paytech.client.contract.domainservice.repository.impl.ClientDocumentRepositoryImpl;
import com.paytech.client.contract.domainservice.service.*;
import com.paytech.client.contract.domainservice.service.impl.ClientDocumentServiceImpl;
import com.paytech.client.contract.integration.*;
import com.paytech.client.contract.service.processor.ClientDocumentProcessor;
import com.paytech.platform.support.api.onboardingfiles.OnboardingFileVerificationApi;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;
import com.paytech.risk.api.compliance.MaterialRequirementsApi;
/** 默认随已批准的草稿开关关闭；没有真实规则或文件服务时明确不可用。 */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="onboarding",name="draft-enabled",havingValue="true")
public class ClientDocumentConfiguration {
    /** Mapper 只在现有 CCT 单库 SQL Session 中装配。 */
    @Bean public ClientDocumentRepository clientDocumentRepository(SqlSessionFactory factory) { return new ClientDocumentRepositoryImpl(new SqlSessionTemplate(factory).getMapper(ClientDocumentMapper.class)); }
    /** 同库业务规则不依赖 Owner 网络客户端。 */
    @Bean public ClientDocumentService clientDocumentService(ClientDraftRepository drafts,ClientPeopleRepository people,ClientDocumentRepository documents,IdGenerator ids,Clock clock) { return new ClientDocumentServiceImpl(drafts,people,documents,ids,clock); }
    /** 显式私网 HTTP 合同，不由浏览器指定 URL。 */
    @Bean public ClientMaterialAuthorityClient clientMaterialAuthorityClient(Environment env,ObjectProvider<LoadBalancerClient> discovery,Clock clock) {
        var platform=HttpServiceClientFactory.create(ClientOnboardingRecoveryConfiguration.platformTransport(env,discovery),HttpServiceClientOptions.strict().withStrictJsonResponses(8192),OnboardingFileErrorCode.values()).createClient(OnboardingFileVerificationApi.class);
        var risk=HttpServiceClientFactory.create(riskTransport(env,discovery),HttpServiceClientOptions.strict().withStrictJsonResponses(262144)).createClient(MaterialRequirementsApi.class);
        return new ClientMaterialAuthorityClient(risk,platform,clock);
    }
    /** 生产使用 Risk 服务发现，测试仅允许 auto 的随机 loopback 根路径。 */
    static RestClient riskTransport(Environment env,ObjectProvider<LoadBalancerClient> discovery) {
        String url=env.getProperty("onboarding.risk-test-base-url","");var builder=RestClient.builder();
        if (url.isBlank()) {
            builder.baseUrl("http://risk-service").requestInterceptor((request,body,execution) -> {
                var balancer=discovery.getObject();var instance=balancer.choose("risk-service");
                if (instance==null) throw new ResourceAccessException("Risk discovery is unavailable");
                return balancer.execute("risk-service",instance,new LoadBalancerRequestFactory(balancer).createRequest(request,body,execution));
            });
        } else {
            URI uri=URI.create(url);
            if (!Arrays.asList(env.getActiveProfiles()).contains("auto") || !"http".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost())
                || uri.getPort()<1 || uri.getPort()>65535 || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || !uri.getPath().isEmpty())
                throw new IllegalArgumentException("Risk 测试直连仅允许 auto 的 loopback HTTP 根路径");
            builder.baseUrl(url);
        }
        var transport=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build());transport.setReadTimeout(Duration.ofSeconds(3));
        return builder.requestFactory(transport).build();
    }
    /** 只读准备与最终短写事务分开，外部查询由 Processor 在事务外完成。 */
    @Bean public ClientDocumentProcessor clientDocumentProcessor(ClientDocumentService service,ClientDraftAccountClient accounts,ClientMaterialAuthorityClient authority,PlatformTransactionManager manager) {
        var reads=new TransactionTemplate(manager);reads.setReadOnly(true);reads.setTimeout(5);reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var writes=new TransactionTemplate(manager);writes.setTimeout(5);writes.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return new ClientDocumentProcessor(service,accounts,authority,reads,writes);
    }
}
