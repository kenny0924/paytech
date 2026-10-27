package com.paytech.client.master.configuration;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
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
import org.springframework.web.client.*;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.httpclient.api.*;
import com.paytech.client.master.core.dal.mapper.ClientDocumentMapper;
import com.paytech.client.master.domainservice.repository.*;
import com.paytech.client.master.domainservice.repository.impl.ClientDocumentRepositoryImpl;
import com.paytech.client.master.domainservice.service.*;
import com.paytech.client.master.domainservice.service.impl.ClientDocumentServiceImpl;
import com.paytech.client.master.integration.*;
import com.paytech.client.master.service.processor.AssociateClientDocumentProcessor;
import com.paytech.client.master.service.processor.RemoveClientDocumentProcessor;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockProperties;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.api.files.InternalFileApi;
import com.paytech.compliance.api.ProgramResolutionApi;
import com.paytech.client.master.integration.impl.ComplianceProgramClientImpl;
/** 默认随已批准的草稿开关关闭；没有真实规则或文件服务时明确不可用。 */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="onboarding",name="draft-enabled",havingValue="true")
public class ClientDocumentConfiguration {
    /** Mapper 只在现有 CCT 单库 SQL Session 中装配。 */
    @Bean public ClientDocumentRepository clientDocumentRepository(SqlSessionFactory factory) { return new ClientDocumentRepositoryImpl(new SqlSessionTemplate(factory).getMapper(ClientDocumentMapper.class)); }
    /** 同库业务规则不依赖 Owner 网络客户端。 */
    @Bean public ClientDocumentService clientDocumentService(ClientDraftRepository drafts,ClientPeopleRepository people,ClientDocumentRepository documents,IdGenerator ids,Clock clock) { return new ClientDocumentServiceImpl(drafts,people,documents,ids,clock); }
    /** 显式私网 HTTP 合同，不由浏览器指定 URL；注册 Platform 文件错误码，使 FILE_NOT_FOUND 可映射为材料不存在。 */
    @Bean public ClientMaterialAuthorityClient clientMaterialAuthorityClient(Environment env,ObjectProvider<LoadBalancerClient> discovery,
        ObjectProvider<InternalTokenSupplier> tokens,ComplianceProgramClient programs) {
        var platform=HttpServiceClientFactory.create(ClientOnboardingRecoveryConfiguration.platformTransport(env,discovery),
            InternalCallTokenOptions.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(8192),tokens,"platform-support-service"),
            FileErrorCode.values()).createClient(InternalFileApi.class);
        return new ClientMaterialAuthorityClient(programs,platform);
    }
    /** compliance CP-1 程序解析；无可用程序时 compliance 返回 503，本服务失败关闭。 */
    @Bean public ComplianceProgramClient complianceProgramClient(Environment env,ObjectProvider<LoadBalancerClient> discovery,
        ObjectProvider<InternalTokenSupplier> tokens) {
        var api=HttpServiceClientFactory.create(riskTransport(env,discovery),
            InternalCallTokenOptions.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(262144),tokens,"compliance-service"))
            .createClient(ProgramResolutionApi.class);
        return new ComplianceProgramClientImpl(api);
    }
    /** 生产使用 Risk 服务发现，测试仅允许 auto 的随机 loopback 根路径。 */
    static RestClient riskTransport(Environment env,ObjectProvider<LoadBalancerClient> discovery) {
        String url=env.getProperty("onboarding.risk-test-base-url","");var builder=RestClient.builder();
        if (url.isBlank()) {
            builder.baseUrl("http://compliance-service").requestInterceptor((request,body,execution) -> {
                var balancer=discovery.getObject();var instance=balancer.choose("compliance-service");
                if (instance==null) throw new ResourceAccessException("Risk discovery is unavailable");
                return balancer.execute("compliance-service",instance,new LoadBalancerRequestFactory(balancer).createRequest(request,body,execution));
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
    /** C12 关联材料：只读准备与最终短写事务分开，外部查询由 Processor 在事务外完成。 */
    @Bean public AssociateClientDocumentProcessor associateClientDocumentProcessor(RedisResourceLockProperties locks,ClientDocumentService service,
        ClientDraftAccountClient accounts,ClientMaterialAuthorityClient authority,PlatformTransactionManager manager,ClientDraftRepository drafts,
        OnboardingPackService packs) {
        return new AssociateClientDocumentProcessor(locks.getWaitTime(),locks.getLeaseTime(),service,accounts,authority,
            ClientDraftConfiguration.reads(manager),ClientDraftConfiguration.writes(manager),drafts,packs);
    }
    /** C13 删除材料关联。 */
    @Bean public RemoveClientDocumentProcessor removeClientDocumentProcessor(RedisResourceLockProperties locks,ClientDocumentService service,
        ClientDraftAccountClient accounts,PlatformTransactionManager manager) {
        return new RemoveClientDocumentProcessor(locks.getWaitTime(),locks.getLeaseTime(),service,accounts,ClientDraftConfiguration.writes(manager));
    }
}
