package com.paytech.client.master.configuration;

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
import com.paytech.client.master.integration.ClientDraftAccountClient;
import com.paytech.client.master.integration.ClientMaterialAuthorityClient;
import com.paytech.client.master.integration.ClientReviewRegistrationClient;
import com.paytech.client.master.integration.impl.ClientReviewRegistrationClientImpl;
import com.paytech.client.master.service.handler.ClientReviewRegistrationHandler;
import com.paytech.client.master.service.processor.CheckClientSubmissionProcessor;
import com.paytech.client.master.service.processor.ClientSubmissionPreparer;
import com.paytech.client.master.service.processor.SubmitClientApplicationProcessor;
import com.paytech.client.master.service.processor.GetApplicationRequirementsProcessor;
import com.paytech.client.master.service.processor.RecordApplicationDeclarationProcessor;
import com.paytech.client.master.service.processor.WithdrawClientApplicationProcessor;
import com.paytech.client.master.service.handler.ClientReviewWithdrawalHandler;
import com.paytech.client.master.domainservice.repository.ApplicationDeclarationRepository;
import com.paytech.client.master.domainservice.repository.PartyAttributeRepository;
import com.paytech.client.master.domainservice.service.ClientApplicationCommandService;
import com.paytech.client.master.domainservice.service.OnboardingPackService;
import com.paytech.client.master.domainservice.service.impl.ClientApplicationCommandServiceImpl;
import com.paytech.client.master.integration.ComplianceProgramClient;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.httpclient.api.HttpServiceClientFactory;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockProperties;
import com.paytech.compliance.api.ClientReviewRegistrationApi;
import com.paytech.compliance.api.ClientReviewResultApi;
import com.paytech.client.master.integration.ClientReviewResultClient;
import com.paytech.client.master.integration.impl.ClientReviewResultClientImpl;
import com.paytech.compliance.api.enums.ClientReviewErrorCode;

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
        ClientApplicationRepository applications, PartyProfileIdentifierRepository identifiers, PartyAttributeRepository attributes,
        ApplicationDeclarationRepository declarations) {
        return new ClientReviewRegistrationServiceImpl(clock, writes(manager), drafts, people, documents, applications, identifiers,
            attributes, declarations);
    }

    /** READ COMMITTED 短事务。 */
    static TransactionTemplate writes(PlatformTransactionManager manager) {
        var writes = new TransactionTemplate(manager);
        writes.setTimeout(5);
        writes.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return writes;
    }

    /** 提交、核对与需求清单共用的准备步骤。 */
    @Bean
    public ClientSubmissionPreparer clientSubmissionPreparer(Clock clock, OnboardingPackService packs, ClientDraftRepository drafts,
        ClientPeopleRepository people, ClientDocumentRepository documents, PartyAttributeRepository attributes,
        ComplianceProgramClient programs, PartyIdentityKeyRepository identityKeys, ClientMaterialAuthorityClient authority,
        ClientAgreementPartyClient agreementParties, PartyProfileIdentifierRepository identifiers,
        ApplicationDeclarationRepository declarations) {
        return new ClientSubmissionPreparer(clock, packs, drafts, people, documents, attributes, programs, identityKeys, authority,
            agreementParties, identifiers, declarations);
    }

    /** CM-7 需求清单。 */
    @Bean
    public GetApplicationRequirementsProcessor getApplicationRequirementsProcessor(ClientDraftRepository drafts,
        ClientDraftAccountClient accounts, ClientSubmissionPreparer preparer, ClientReviewResultClient reviewResults, Clock clock) {
        return new GetApplicationRequirementsProcessor(drafts, accounts, preparer, reviewResults, clock);
    }

    /** compliance CP-4 审核结果出站端口（只读退回错误）；注册审核错误码以区分从未登记。 */
    @Bean
    public ClientReviewResultClient clientReviewResultClient(Environment env, ObjectProvider<LoadBalancerClient> discovery,
        ObjectProvider<InternalTokenSupplier> tokens) {
        var api = HttpServiceClientFactory.create(ClientDocumentConfiguration.riskTransport(env, discovery),
            InternalCallTokenOptions.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(262144), tokens, "compliance-service"),
            ClientReviewErrorCode.values())
            .createClient(ClientReviewResultApi.class);
        return new ClientReviewResultClientImpl(api);
    }

    /** 声明追加与撤回的本地短事务。 */
    @Bean
    public ClientApplicationCommandService clientApplicationCommandService(PlatformTransactionManager manager,
        CompensationEventRegistrar events, ClientApplicationRepository applications, ApplicationDeclarationRepository declarations) {
        return new ClientApplicationCommandServiceImpl(writes(manager), events, applications, declarations);
    }

    /** CM-6 记录声明。 */
    @Bean
    public RecordApplicationDeclarationProcessor recordApplicationDeclarationProcessor(Clock clock, IdGenerator ids,
        RedisResourceLockProperties locks, OnboardingPackService packs, ClientDraftRepository drafts, ClientDraftAccountClient accounts,
        ApplicationDeclarationRepository declarations, ClientApplicationCommandService commands) {
        return new RecordApplicationDeclarationProcessor(clock, ids, locks.getWaitTime(), locks.getLeaseTime(), packs, drafts, accounts,
            declarations, commands);
    }

    /** CM-8 撤回申请。 */
    @Bean
    public WithdrawClientApplicationProcessor withdrawClientApplicationProcessor(Clock clock, RedisResourceLockProperties locks,
        ClientDraftAccountClient accounts, ClientApplicationRepository applications, ClientApplicationCommandService commands) {
        return new WithdrawClientApplicationProcessor(clock, locks.getWaitTime(), locks.getLeaseTime(), accounts, applications, commands);
    }

    /** 撤回后通知 compliance 的补偿入口。 */
    @Bean
    public ClientReviewWithdrawalHandler clientReviewWithdrawalHandler(ClientReviewRegistrationService applications,
        ClientReviewRegistrationClient compliance) {
        return new ClientReviewWithdrawalHandler(applications, compliance);
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
            InternalCallTokenOptions.apply(HttpServiceClientOptions.strict().withStrictJsonResponses(8192), tokens, "compliance-service"),
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
