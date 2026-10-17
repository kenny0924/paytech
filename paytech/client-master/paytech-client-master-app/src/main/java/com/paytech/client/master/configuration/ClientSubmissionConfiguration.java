package com.paytech.client.master.configuration;

import com.paytech.client.master.core.configuration.ClientFormCatalogProperties;
import com.paytech.client.master.integration.ClientAgreementPartyClient;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import java.time.Clock;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.master.core.dal.mapper.ClientApplicationMapper;
import com.paytech.client.master.core.dal.mapper.PartyIdentityKeyMapper;
import com.paytech.client.master.core.dal.mapper.PartyProfileIdentifierMapper;
import com.paytech.client.master.domainservice.repository.ClientApplicationRepository;
import com.paytech.client.master.domainservice.repository.ClientDocumentRepository;
import com.paytech.client.master.domainservice.repository.ClientDraftRepository;
import com.paytech.client.master.domainservice.repository.ClientPeopleRepository;
import com.paytech.client.master.domainservice.repository.PartyIdentityKeyRepository;
import com.paytech.client.master.domainservice.repository.PartyProfileIdentifierRepository;
import com.paytech.client.master.domainservice.repository.impl.ClientApplicationRepositoryImpl;
import com.paytech.client.master.domainservice.repository.impl.PartyIdentityKeyRepositoryImpl;
import com.paytech.client.master.domainservice.repository.impl.PartyProfileIdentifierRepositoryImpl;
import com.paytech.client.master.domainservice.service.ClientReviewRegistrationService;
import com.paytech.client.master.domainservice.service.ClientSubmissionService;
import com.paytech.client.master.domainservice.service.impl.ClientReviewRegistrationServiceImpl;
import com.paytech.client.master.domainservice.service.impl.ClientSubmissionServiceImpl;
import com.paytech.client.master.domainservice.validator.ClientSubmissionValidator;
import com.paytech.client.master.integration.ClientDraftAccountClient;
import com.paytech.client.master.integration.ClientMaterialAuthorityClient;
import com.paytech.client.master.integration.ClientReviewRegistrationClient;
import com.paytech.client.master.integration.impl.ClientReviewRegistrationClientImpl;
import com.paytech.client.master.service.handler.ClientReviewRegistrationHandler;
import com.paytech.client.master.service.processor.CheckClientSubmissionProcessor;
import com.paytech.client.master.service.processor.ClientSubmissionPreparer;
import com.paytech.client.master.service.processor.SubmitClientApplicationProcessor;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockProperties;
import com.paytech.risk.api.compliance.ClientReviewRegistrationApi;
import com.paytech.risk.api.enums.ClientReviewErrorCode;

/** C14 提交与审核登记装配；随草稿开关关闭，不启用外部入口。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "onboarding", name = "draft-enabled", havingValue = "true")
public class ClientSubmissionConfiguration {
    /** 申请提交坐标仓储。 */
    @Bean
    public ClientApplicationRepository clientApplicationRepository(SqlSessionFactory factory) {
        return new ClientApplicationRepositoryImpl(new SqlSessionTemplate(factory).getMapper(ClientApplicationMapper.class));
    }

    /** 身份键仓储。 */
    @Bean
    public PartyIdentityKeyRepository partyIdentityKeyRepository(SqlSessionFactory factory) {
        return new PartyIdentityKeyRepositoryImpl(new SqlSessionTemplate(factory).getMapper(PartyIdentityKeyMapper.class));
    }

    /** 资料证件绑定仓储。 */
    @Bean
    public PartyProfileIdentifierRepository partyProfileIdentifierRepository(SqlSessionFactory factory) {
        return new PartyProfileIdentifierRepositoryImpl(new SqlSessionTemplate(factory).getMapper(PartyProfileIdentifierMapper.class));
    }

    /** 提交检查使用启动时冻结的通用目录。 */
    @Bean
    public ClientSubmissionValidator clientSubmissionValidator(ClientFormCatalogProperties catalog) {
        return new ClientSubmissionValidator(catalog);
    }

    /** 提交保存使用 READ COMMITTED 短事务。 */
    @Bean
    public ClientSubmissionService clientSubmissionService(IdGenerator ids, PlatformTransactionManager manager,
        CompensationEventRegistrar events, PartyIdentityKeyRepository identityKeys, ClientApplicationRepository applications,
        PartyProfileIdentifierRepository identifiers) {
        return new ClientSubmissionServiceImpl(ids, writes(manager), events, identityKeys, applications, identifiers);
    }

    /** 审核登记补偿入口的读取与结果保存；不依赖补偿登记器，避免与 Handler 注册表形成循环。 */
    @Bean
    public ClientReviewRegistrationService clientReviewRegistrationService(Clock clock, PlatformTransactionManager manager,
        ClientDraftRepository drafts, ClientPeopleRepository people, ClientDocumentRepository documents,
        ClientApplicationRepository applications, PartyProfileIdentifierRepository identifiers) {
        return new ClientReviewRegistrationServiceImpl(clock, writes(manager), drafts, people, documents, applications, identifiers);
    }

    /** READ COMMITTED 短事务。 */
    static TransactionTemplate writes(PlatformTransactionManager manager) {
        var writes = new TransactionTemplate(manager);
        writes.setTimeout(5);
        writes.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return writes;
    }

    /** 提交与核对共用的准备步骤。 */
    @Bean
    public ClientSubmissionPreparer clientSubmissionPreparer(Clock clock, ClientDraftRepository drafts, ClientPeopleRepository people,
        ClientDocumentRepository documents, ClientSubmissionValidator validator, PartyIdentityKeyRepository identityKeys,
        ClientMaterialAuthorityClient authority, ClientAgreementPartyClient agreementParties,
        PartyProfileIdentifierRepository identifiers) {
        return new ClientSubmissionPreparer(clock, drafts, people, documents, validator, identityKeys, authority, agreementParties,
            identifiers);
    }

    /** 提交用例；锁参数沿用既有注解锁配置。 */
    @Bean
    public SubmitClientApplicationProcessor submitClientApplicationProcessor(Clock clock, RedisResourceLockProperties locks,
        ClientDraftRepository drafts, ClientSubmissionService submissions, ClientDraftAccountClient accounts,
        ClientSubmissionPreparer preparer, ClientApplicationRepository applications) {
        return new SubmitClientApplicationProcessor(clock, locks.getWaitTime(), locks.getLeaseTime(), drafts, submissions, accounts,
            preparer, applications);
    }

    /** 提交前核对用例。 */
    @Bean
    public CheckClientSubmissionProcessor checkClientSubmissionProcessor(ClientDraftRepository drafts,
        ClientDraftAccountClient accounts, ClientSubmissionPreparer preparer) {
        return new CheckClientSubmissionProcessor(drafts, accounts, preparer);
    }

    /** Risk I01/I02 出站端口；沿用 CCT 的 Risk 服务发现传输，注册 Risk 审核错误码以区分任务已结束与内容冲突。 */
    @Bean
    public ClientReviewRegistrationClient clientReviewRegistrationClient(Environment env, ObjectProvider<LoadBalancerClient> discovery,
        ObjectProvider<InternalTokenSupplier> tokens) {
        var api = HttpServiceClientFactory.create(ClientDocumentConfiguration.riskTransport(env, discovery),
            InternalCallTokenOptions.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(8192), tokens, "risk-service"),
            ClientReviewErrorCode.values())
            .createClient(ClientReviewRegistrationApi.class);
        return new ClientReviewRegistrationClientImpl(api);
    }

    /** 审核登记补偿入口。 */
    @Bean
    public ClientReviewRegistrationHandler clientReviewRegistrationHandler(ClientReviewRegistrationService submissions,
        ClientReviewRegistrationClient risk) {
        return new ClientReviewRegistrationHandler(submissions, risk);
    }
}
