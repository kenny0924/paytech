package com.paytech.client.master.configuration;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.support.TransactionTemplate;
import com.paytech.client.master.domainservice.service.ClientDraftService;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.paytech.client.master.domainservice.repository.ClientApplicationRepository;
import com.paytech.client.master.domainservice.repository.ClientBusinessClassificationRepository;
import com.paytech.client.master.domainservice.repository.ClientBusinessProfileRepository;
import com.paytech.client.master.domainservice.repository.ClientProjectionRepository;
import com.paytech.client.master.domainservice.repository.ClientRepository;
import com.paytech.client.master.domainservice.service.ClientClassificationService;
import com.paytech.client.master.domainservice.service.ClientClassificationSourceService;
import com.paytech.client.master.domainservice.service.ClientReviewOutcomeService;
import com.paytech.client.master.domainservice.service.impl.ClientClassificationServiceImpl;
import com.paytech.client.master.domainservice.service.impl.ClientClassificationSourceServiceImpl;
import com.paytech.client.master.domainservice.repository.PartyProfileRepository;
import com.paytech.client.master.domainservice.service.impl.ClientReviewOutcomeServiceImpl;
import com.paytech.client.master.ext.mq.ClientReviewResultListener;
import com.paytech.client.master.ext.mq.QualificationChangedListener;
import com.paytech.client.master.domainservice.repository.ClientDraftRepository;
import com.paytech.client.master.domainservice.repository.PartyAttributeRepository;
import com.paytech.client.master.domainservice.service.ClientLifecycleService;
import com.paytech.client.master.domainservice.service.impl.ClientLifecycleServiceImpl;
import com.paytech.client.master.service.processor.ApplyQualificationChangeProcessor;
import com.paytech.client.master.integration.ClientReviewRegistrationClient;
import com.paytech.client.master.service.handler.ClientClassificationHandler;
import com.paytech.client.master.service.processor.ApplyClientReviewResultProcessor;
import com.paytech.client.master.service.processor.ConfirmClientClassificationProcessor;
import com.paytech.client.master.service.processor.GetClientClassificationProcessor;
import com.paytech.client.master.service.processor.GetClientReviewApplicationProcessor;
import com.paytech.client.master.service.processor.ReplaceClientClassificationProcessor;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockProperties;
import com.paytech.platform.support.referencedata.client.ReferenceData;

/**
 * Operations 审核在 CCT 侧的装配：O03/O04 分类、I02 交付、审核结果消费与 Client 激活
 * <li>随草稿与补偿开关同时开启；结果消费要求本机 RabbitMQ。</li>
 * <li>审核结果 Handler 为 paytech-messaging 的 DomainEventConsumer：订阅 compliance.events，分区拓扑（client-master.compliance.hash、
 * p0..p3、dlx / dlq）与监听容器由 messaging 按 messaging.consumer.* 声明（design/08 §3）。</li>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "onboarding", name = {"draft-enabled", "recovery-enabled"}, havingValue = "true")
public class ClientReviewConfiguration {
    /** 分类本地保存与交付读取。 */
    @Bean
    public ClientClassificationService clientClassificationService(PlatformTransactionManager manager, CompensationEventRegistrar events,
        ClientBusinessProfileRepository profiles, ClientBusinessClassificationRepository rows) {
        return new ClientClassificationServiceImpl(ClientSubmissionConfiguration.writes(manager), events, profiles, rows);
    }

    /** I02 交付补偿入口的本地读取；不依赖补偿登记器。 */
    @Bean
    public ClientClassificationSourceService clientClassificationSourceService(ClientBusinessProfileRepository profiles,
        ClientApplicationRepository applications, ClientBusinessClassificationRepository rows) {
        return new ClientClassificationSourceServiceImpl(profiles, applications, rows);
    }

    /** 审核结果本地落地。 */
    @Bean
    public ClientReviewOutcomeService clientReviewOutcomeService(ClientRepository clients, PlatformTransactionManager manager,
        CompensationEventRegistrar events, ClientProjectionRepository projections, ClientApplicationRepository applications,
        PartyProfileRepository profiles) {
        return new ClientReviewOutcomeServiceImpl(clients, ClientSubmissionConfiguration.writes(manager), events, projections, applications,
            profiles);
    }

    /** 分类读取。 */
    @Bean
    public GetClientClassificationProcessor getClientClassificationProcessor(ClientBusinessProfileRepository profiles,
        ClientApplicationRepository applications, ClientBusinessClassificationRepository rows) {
        return new GetClientClassificationProcessor(profiles, applications, rows);
    }

    /** O02 申请资料读取。 */
    @Bean
    public GetClientReviewApplicationProcessor getClientReviewApplicationProcessor(ClientApplicationRepository applications,
        ClientDraftService drafts, PlatformTransactionManager manager) {
        TransactionTemplate reads = new TransactionTemplate(manager);
        reads.setReadOnly(true);
        reads.setTimeout(5);
        return new GetClientReviewApplicationProcessor(applications, drafts, reads);
    }

    /** O03；行业与 MCC 向 Platform 参考数据校验。 */
    @Bean
    public ReplaceClientClassificationProcessor replaceClientClassificationProcessor(Clock clock, IdGenerator ids,
        RedisResourceLockProperties locks, ReferenceData referenceData, ClientBusinessProfileRepository profiles,
        ClientClassificationService classifications, ClientApplicationRepository applications, ClientBusinessClassificationRepository rows) {
        return new ReplaceClientClassificationProcessor(clock, ids, locks.getWaitTime(), locks.getLeaseTime(), referenceData, profiles,
            classifications, applications, rows);
    }

    /** O04。 */
    @Bean
    public ConfirmClientClassificationProcessor confirmClientClassificationProcessor(Clock clock, RedisResourceLockProperties locks,
        ClientBusinessProfileRepository profiles, ClientClassificationService classifications, ClientApplicationRepository applications,
        ClientBusinessClassificationRepository rows) {
        return new ConfirmClientClassificationProcessor(clock, locks.getWaitTime(), locks.getLeaseTime(), profiles, classifications,
            applications, rows);
    }

    /** I02 交付补偿入口。 */
    @Bean
    public ClientClassificationHandler clientClassificationHandler(ClientReviewRegistrationClient risk,
        ClientClassificationSourceService classifications) {
        return new ClientClassificationHandler(risk, classifications);
    }

    /** 审核结果落地用例。 */
    @Bean
    public ApplyClientReviewResultProcessor applyClientReviewResultProcessor(Clock clock, IdGenerator ids,
        ClientReviewOutcomeService outcomes, ClientProjectionRepository projections, ClientApplicationRepository applications,
        ClientDraftRepository drafts, PartyAttributeRepository attributes) {
        return new ApplyClientReviewResultProcessor(clock, ids, outcomes, projections, applications, drafts, attributes);
    }

    /** Client 生命周期（资格撤销挂起）本地落地。 */
    @Bean
    public ClientLifecycleService clientLifecycleService(ClientRepository clients, PlatformTransactionManager manager,
        CompensationEventRegistrar events, ClientProjectionRepository projections) {
        return new ClientLifecycleServiceImpl(clients, ClientSubmissionConfiguration.writes(manager), events, projections);
    }

    /** QualificationChanged 落地用例。 */
    @Bean
    public ApplyQualificationChangeProcessor applyQualificationChangeProcessor(Clock clock, IdGenerator ids, ClientRepository clients,
        ClientLifecycleService lifecycle, ClientProjectionRepository projections) {
        return new ApplyQualificationChangeProcessor(clock, ids, clients, lifecycle, projections);
    }

    /** 资格变化 Handler（QualificationChanged），与审核结果同一 compliance 分区队列。 */
    @Bean
    public QualificationChangedListener qualificationChangedListener(ApplyQualificationChangeProcessor processor) {
        return new QualificationChangedListener(processor);
    }

    /** 审核结果 Handler（ClientReviewDecided）。 */
    @Bean
    public ClientReviewResultListener clientReviewResultListener(ApplyClientReviewResultProcessor processor) {
        return new ClientReviewResultListener(processor);
    }
}
