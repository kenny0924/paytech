package com.paytech.commercial.configuration;

import java.util.Objects;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.commercial.core.configuration.CommercialEventProperties;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeVersionRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPlanRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermVersionRepository;
import com.paytech.commercial.domainservice.repository.ProductCapabilityRepository;
import com.paytech.commercial.domainservice.service.CommercialEventPublicationService;
import com.paytech.commercial.domainservice.service.impl.CommercialEventPublicationServiceImpl;
import com.paytech.commercial.ext.mq.QualificationChangedListener;
import com.paytech.commercial.service.handler.AgreementCapabilityStatusChangedHandler;
import com.paytech.commercial.service.handler.AgreementStatusChangedHandler;
import com.paytech.commercial.service.handler.CapabilityPrerequisiteCheckHandler;
import com.paytech.commercial.service.handler.CommercialTermVersionActivatedHandler;
import com.paytech.commercial.service.processor.CheckCapabilityPrerequisitesProcessor;
import com.paytech.commercial.service.processor.RecheckForQualificationChangeProcessor;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.id.SnowflakeIdGenerator;
import com.paytech.foundation.messaging.api.DomainEventPublisher;

/**
 * commercial 可靠异步发布装配（technique.md §2.8）
 * <li>写入方在本地事务内以 Handler 的 buildEvent 经 CompensationEventRegistrar 登记任务；提交后 Foundation 唤醒对应 Handler，
 * 由它回读本库并经 DomainEventPublisher 发布到 commercial.events。</li>
 * <li>开通项前置门禁（2026-10-28）起 commercial 另消费 compliance.events 的 QualificationChanged（commercial.compliance 分区队列），
 * 触发门禁重查；前置检查任务提交后由 CapabilityPrerequisiteCheckHandler 调用门禁。</li>
 * <li>Snowflake 生成器同时供补偿登记与后续写入 Processor 生成主键。</li>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@EnableConfigurationProperties({CommercialEventProperties.class, CommercialIdentifierProperties.class})
public class CommercialEventConfiguration {
    /** 进程唯一的 Snowflake 生成器；缺少 Region/Worker 配置时启动失败。 */
    @Bean
    public IdGenerator commercialIdGenerator(CommercialIdentifierProperties properties) {
        return new SnowflakeIdGenerator(
            Objects.requireNonNull(properties.getRegionId(), "commercial.identifier.region-id"),
            Objects.requireNonNull(properties.getWorkerId(), "commercial.identifier.worker-id"));
    }

    /** 发布前回读：只读、REPEATABLE READ、5 秒，独立于调用方事务。 */
    @Bean
    public CommercialEventPublicationService commercialEventPublicationService(DataSourceTransactionManager transactions,
        BusinessChangeRecordRepository businessChangeRecordRepository, AgreementRepository agreementRepository,
        CommercialTermPlanRepository commercialTermPlanRepository,
        CommercialTermVersionRepository commercialTermVersionRepository,
        ProductCapabilityRepository productCapabilityRepository,
        BusinessChangeVersionRepository businessChangeVersionRepository) {
        TransactionTemplate reads = new TransactionTemplate(transactions);
        reads.setReadOnly(true);
        reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        reads.setTimeout(5);
        return new CommercialEventPublicationServiceImpl(reads, businessChangeRecordRepository, agreementRepository,
            commercialTermPlanRepository, commercialTermVersionRepository, productCapabilityRepository,
            businessChangeVersionRepository);
    }

    /** AGREEMENT_STATUS_CHANGED 发布 Handler。 */
    @Bean
    public AgreementStatusChangedHandler agreementStatusChangedHandler(DomainEventPublisher publisher,
        CommercialEventProperties properties, CommercialEventPublicationService publicationService) {
        return new AgreementStatusChangedHandler(publisher, properties, publicationService);
    }

    /** COMMERCIAL_TERM_VERSION_ACTIVATED 发布 Handler。 */
    @Bean
    public CommercialTermVersionActivatedHandler commercialTermVersionActivatedHandler(DomainEventPublisher publisher,
        CommercialEventProperties properties, CommercialEventPublicationService publicationService) {
        return new CommercialTermVersionActivatedHandler(publisher, properties, publicationService);
    }

    /** AGREEMENT_CAPABILITY_STATUS_CHANGED 发布 Handler。 */
    @Bean
    public AgreementCapabilityStatusChangedHandler agreementCapabilityStatusChangedHandler(DomainEventPublisher publisher,
        CommercialEventProperties properties, CommercialEventPublicationService publicationService) {
        return new AgreementCapabilityStatusChangedHandler(publisher, properties, publicationService);
    }

    /** AGREEMENT_CAPABILITY_PREREQUISITE_CHECK 门禁 Handler；门禁 Processor 延迟取得以避开补偿注册表的装配环。 */
    @Bean
    public CapabilityPrerequisiteCheckHandler capabilityPrerequisiteCheckHandler(
        ObjectProvider<CheckCapabilityPrerequisitesProcessor> processor) {
        return new CapabilityPrerequisiteCheckHandler(processor);
    }

    /** compliance QualificationChanged 消费者；只在配置 messaging.consumer.service 时注册（commercial 首个消费者）。 */
    @Bean
    @ConditionalOnProperty(prefix = "messaging.consumer", name = "service")
    public QualificationChangedListener qualificationChangedListener(RecheckForQualificationChangeProcessor processor) {
        return new QualificationChangedListener(processor);
    }
}
