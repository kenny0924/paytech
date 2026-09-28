package com.paytech.client.contract.configuration;
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
import com.paytech.client.contract.core.dal.mapper.*;
import com.paytech.client.contract.domainservice.repository.*;
import com.paytech.client.contract.domainservice.repository.impl.*;
import com.paytech.client.contract.domainservice.service.*;
import com.paytech.client.contract.domainservice.service.impl.*;
import com.paytech.client.contract.integration.*;
import com.paytech.client.contract.integration.impl.*;
import com.paytech.client.contract.service.processor.*;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.httpclient.api.*;
import com.paytech.foundation.id.IdGenerator;
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
    @Bean public ClientFormOptionsProcessor clientFormOptionsProcessor(ClientDraftAccountClient accounts,ClientFormCatalogService catalog) {
        return new ClientFormOptionsProcessor(accounts,catalog);
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
    @Bean public ClientPeopleProcessor clientPeopleProcessor(ClientPeopleService service,ClientDraftAccountClient accounts,
        Validator validator,PlatformTransactionManager transactions) {
        var writes=new TransactionTemplate(transactions);writes.setTimeout(5);
        writes.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return new ClientPeopleProcessor(service,accounts,validator,writes);
    }
    @Bean public ClientDraftService clientDraftService(ClientDraftRepository repository,ClientPeopleRepository people,ClientDocumentRepository documents,
        IdGenerator ids,Clock clock,CompensationEventRegistrar events) {
        return new ClientDraftServiceImpl(repository,people,documents,ids,clock,events);
    }
    @Bean public ClientDraftAccountClient clientDraftAccountClient(Environment environment,ObjectProvider<LoadBalancerClient> discovery) {
        var api=HttpServiceClientFactory.create(ClientOnboardingRecoveryConfiguration.platformTransport(environment,discovery),
            HttpServiceClientOptions.strict().withStrictJsonResponses(8192),ClientAuthenticationErrorCode.values())
            .createClient(ClientAuthenticationApi.class);
        return new ClientDraftAccountClientImpl(api);
    }
    @Bean public ClientDraftContactClient clientDraftContactClient(VaultClient vault) { return new ClientDraftContactClientImpl(vault); }
    @Bean public ClientDraftProcessor clientDraftProcessor(ClientDraftService service,ClientDraftAccountClient accounts,
        ClientDraftContactClient contacts,Validator validator,PlatformTransactionManager transactions) {
        var writes=new TransactionTemplate(transactions);writes.setTimeout(5);
        writes.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        var reads=new TransactionTemplate(transactions);reads.setReadOnly(true);reads.setTimeout(5);
        reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return new ClientDraftProcessor(service,accounts,contacts,validator,writes,reads);
    }
}
