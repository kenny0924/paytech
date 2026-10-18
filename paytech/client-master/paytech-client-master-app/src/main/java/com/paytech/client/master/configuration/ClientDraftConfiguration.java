package com.paytech.client.master.configuration;
import com.paytech.client.master.core.configuration.ClientFormCatalogProperties;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequestFactory;
import java.time.Duration;
import java.net.http.HttpClient;
import com.paytech.commercial.api.agreement.AgreementPartyQueryApi;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import java.time.Clock;
import javax.sql.DataSource;
import jakarta.validation.Validator;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import com.paytech.client.master.core.dal.mapper.*;
import com.paytech.client.master.domainservice.repository.*;
import com.paytech.client.master.domainservice.repository.impl.*;
import com.paytech.client.master.domainservice.service.*;
import com.paytech.client.master.domainservice.service.impl.*;
import com.paytech.client.master.integration.*;
import com.paytech.client.master.integration.impl.*;
import com.paytech.client.master.service.processor.*;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.httpclient.api.*;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockProperties;
import com.paytech.platform.support.api.authentication.ClientAuthenticationApi;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
import com.paytech.vault.client.VaultClient;
import com.paytech.vault.client.autoconfigure.VaultClientAutoConfiguration;
/** 草稿写入需明确启用恢复、CCT 数据源、Redis 协调和 Vault；不自动初始化业务表。 */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="onboarding",name="draft-enabled",havingValue="true")
@Import(VaultClientAutoConfiguration.class)
@org.springframework.boot.context.properties.EnableConfigurationProperties(ClientFormCatalogProperties.class)
public class ClientDraftConfiguration {
    /** 受控目录只读取启动时校验过的 CCT 配置。 */
    @Bean public ClientFormCatalogService clientFormCatalogService(ClientFormCatalogProperties catalog) {
        return new ClientFormCatalogServiceImpl(catalog);
    }
    /** 目录查询复用真实 Platform 会话验证，不打开数据库事务。 */
    @Bean public GetClientFormOptionsProcessor getClientFormOptionsProcessor(ClientDraftAccountClient accounts,ClientFormCatalogService catalog) {
        return new GetClientFormOptionsProcessor(accounts,catalog);
    }
    /** 工作区摘要复用真实 Platform 会话验证，只读不开事务。 */
    @Bean public GetClientWorkspaceSummariesProcessor getClientWorkspaceSummariesProcessor(ClientDraftAccountClient accounts,
        ClientApplicationRepository applications) {
        return new GetClientWorkspaceSummariesProcessor(accounts,applications);
    }
    @Bean public ClientDraftRepository clientDraftRepository(SqlSessionFactory factory,IdGenerator ids,Environment environment) {
        if (!environment.getProperty("onboarding.recovery-enabled",Boolean.class,false)
            || !environment.getProperty("redis.coordination.enabled",Boolean.class,false))
            throw new IllegalArgumentException("草稿写入要求启用本地恢复与 Redis 资源锁");
        return new ClientDraftRepositoryImpl(new SqlSessionTemplate(factory).getMapper(ClientDraftMapper.class),ids);
    }
    @Bean public ClientPeopleRepository clientPeopleRepository(SqlSessionFactory factory) {
        var session=new SqlSessionTemplate(factory);
        return new ClientPeopleRepositoryImpl(session.getMapper(ClientPeopleMapper.class),session.getMapper(ClientDraftMapper.class));
    }
    @Bean public ClientPeopleService clientPeopleService(ClientDraftRepository drafts,ClientPeopleRepository people,IdGenerator ids,Clock clock) {
        return new ClientPeopleServiceImpl(drafts,people,ids,clock);
    }
    /** 文件授权必须同时观察原申请状态与人员关系，避免相邻提交产生混合授权事实。 */
    @Bean public ClientFileTargetService clientFileTargetService(ClientDraftRepository drafts,
        ClientPeopleRepository people,ClientDocumentRepository documents,PlatformTransactionManager transactions) {
        var reads=new TransactionTemplate(transactions);reads.setReadOnly(true);reads.setTimeout(5);
        reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return new ClientFileTargetServiceImpl(reads,drafts,people,documents);
    }
    /** C08 新增人员；锁参数沿用既有注解锁配置。 */
    @Bean public CreateClientPersonProcessor createClientPersonProcessor(RedisResourceLockProperties locks,ClientPeopleService service,
        ClientDraftAccountClient accounts,PlatformTransactionManager transactions,Validator validator) {
        return new CreateClientPersonProcessor(locks.getWaitTime(),locks.getLeaseTime(),service,accounts,writes(transactions),validator);
    }
    /** C08 保存人员；事务前查询 commercial 合同引用。 */
    @Bean public SaveClientPersonProcessor saveClientPersonProcessor(RedisResourceLockProperties locks,ClientPeopleService service,
        ClientDraftAccountClient accounts,PlatformTransactionManager transactions,Validator validator,ClientDraftRepository drafts,
        ClientPeopleRepository people,ClientAgreementPartyClient agreementParties) {
        return new SaveClientPersonProcessor(locks.getWaitTime(),locks.getLeaseTime(),service,accounts,writes(transactions),validator,
            drafts,people,agreementParties);
    }
    /** C09 新建关系。 */
    @Bean public CreateClientRelationProcessor createClientRelationProcessor(RedisResourceLockProperties locks,ClientPeopleService service,
        ClientDraftAccountClient accounts,PlatformTransactionManager transactions) {
        return new CreateClientRelationProcessor(locks.getWaitTime(),locks.getLeaseTime(),service,accounts,writes(transactions));
    }
    /** C10 修改关系。 */
    @Bean public SaveClientRelationProcessor saveClientRelationProcessor(RedisResourceLockProperties locks,ClientPeopleService service,
        ClientDraftAccountClient accounts,PlatformTransactionManager transactions) {
        return new SaveClientRelationProcessor(locks.getWaitTime(),locks.getLeaseTime(),service,accounts,writes(transactions));
    }
    /** C11 删除关系。 */
    @Bean public RemoveClientRelationProcessor removeClientRelationProcessor(RedisResourceLockProperties locks,ClientPeopleService service,
        ClientDraftAccountClient accounts,PlatformTransactionManager transactions) {
        return new RemoveClientRelationProcessor(locks.getWaitTime(),locks.getLeaseTime(),service,accounts,writes(transactions));
    }
    @Bean public ClientDraftService clientDraftService(ClientDraftRepository repository,ClientPeopleRepository people,ClientDocumentRepository documents,
        IdGenerator ids,Clock clock,CompensationEventRegistrar events) {
        return new ClientDraftServiceImpl(repository,people,documents,ids,clock,events);
    }
    @Bean public ClientDraftAccountClient clientDraftAccountClient(Environment environment,ObjectProvider<LoadBalancerClient> discovery,
        ObjectProvider<InternalTokenSupplier> tokens) {
        var api=HttpServiceClientFactory.create(ClientOnboardingRecoveryConfiguration.platformTransport(environment,discovery),
            InternalCallTokenOptions.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(8192),tokens,"platform-support-service"),
            ClientAuthenticationErrorCode.values())
            .createClient(ClientAuthenticationApi.class);
        return new ClientDraftAccountClientImpl(api);
    }
    @Bean public ClientDraftContactClient clientDraftContactClient(VaultClient vault) { return new ClientDraftContactClientImpl(vault); }
    /**
     * commercial 合同参与方引用查询（design/06 §2.2）：人员共享标记的唯一外部来源，失败关闭。
     * <li>服务发现名 commercial-service；超时与重定向策略同其他私网 Owner 客户端。</li>
     */
    @Bean public ClientAgreementPartyClient clientAgreementPartyClient(ObjectProvider<LoadBalancerClient> discovery,
        ObjectProvider<InternalTokenSupplier> tokens) {
        var api=HttpServiceClientFactory.create(discoveryTransport(discovery,"commercial-service",""),
            InternalCallTokenOptions.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(65536),tokens,"commercial-service"))
            .createClient(AgreementPartyQueryApi.class);
        return new ClientAgreementPartyClientImpl(api);
    }
    /**
     * 私网服务发现传输：每次请求按发现名选实例，禁止重定向，连接 2 秒、读取 3 秒。
     *
     * @param discovery 负载均衡客户端
     * @param service 服务发现名
     * @param contextPath 目标服务的上下文路径，没有时为空串
     * @return 未附加错误边界的 RestClient
     */
    static RestClient discoveryTransport(ObjectProvider<LoadBalancerClient> discovery,String service,String contextPath) {
        var transport=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build());
        transport.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().baseUrl("http://"+service+contextPath).requestFactory(transport)
            .requestInterceptor((request,body,execution) -> {
                LoadBalancerClient balancer=discovery.getObject();
                var instance=balancer.choose(service);
                if (instance==null) {
                    throw new ResourceAccessException(service+" discovery is unavailable");
                }
                return balancer.execute(service,instance,new LoadBalancerRequestFactory(balancer).createRequest(request,body,execution));
            }).build();
    }
    /** C02 开始新申请；锁参数沿用既有注解锁配置。 */
    @Bean public CreateClientApplicationProcessor createClientApplicationProcessor(RedisResourceLockProperties locks,
        ClientDraftService service,ClientDraftAccountClient accounts,PlatformTransactionManager transactions) {
        return new CreateClientApplicationProcessor(locks.getWaitTime(),locks.getLeaseTime(),service,accounts,writes(transactions));
    }
    /** C01 开始页导航，只读不开事务。 */
    @Bean public GetClientOnboardingStartProcessor getClientOnboardingStartProcessor(ClientDraftService service,
        ClientDraftAccountClient accounts) {
        return new GetClientOnboardingStartProcessor(service,accounts);
    }
    /** 读取申请资料，本库数据在 REPEATABLE READ 只读快照内读取。 */
    @Bean public GetClientDraftProcessor getClientDraftProcessor(ClientDraftService service,ClientDraftAccountClient accounts,
        ClientDraftContactClient contacts,ClientAgreementPartyClient agreementParties,PlatformTransactionManager transactions) {
        return new GetClientDraftProcessor(service,accounts,contacts,agreementParties,reads(transactions));
    }
    /** 保存草稿；只读准备与最终短写事务分开。 */
    @Bean public SaveClientDraftProcessor saveClientDraftProcessor(RedisResourceLockProperties locks,ClientDraftService service,
        ClientDraftAccountClient accounts,ClientDraftContactClient contacts,Validator validator,PlatformTransactionManager transactions) {
        return new SaveClientDraftProcessor(locks.getWaitTime(),locks.getLeaseTime(),service,accounts,contacts,validator,
            writes(transactions),reads(transactions));
    }
    /** READ COMMITTED、5 秒超时的写事务。 */
    static TransactionTemplate writes(PlatformTransactionManager transactions) {
        var writes=new TransactionTemplate(transactions);writes.setTimeout(5);
        writes.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return writes;
    }
    /** REPEATABLE READ、5 秒超时的只读事务。 */
    static TransactionTemplate reads(PlatformTransactionManager transactions) {
        var reads=new TransactionTemplate(transactions);reads.setReadOnly(true);reads.setTimeout(5);
        reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return reads;
    }
}
