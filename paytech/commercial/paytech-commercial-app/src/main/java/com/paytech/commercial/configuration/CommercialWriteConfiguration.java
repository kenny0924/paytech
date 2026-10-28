package com.paytech.commercial.configuration;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.commercial.domainservice.repository.AgreementCapabilityLimitRepository;
import com.paytech.commercial.domainservice.repository.ProductCapabilityDimensionRepository;
import com.paytech.commercial.domainservice.repository.AgreementCapabilityRepository;
import com.paytech.commercial.domainservice.repository.AgreementDocumentRefRepository;
import com.paytech.commercial.domainservice.repository.AgreementOperatingEntityRepository;
import com.paytech.commercial.domainservice.repository.AgreementPartyRepository;
import com.paytech.commercial.domainservice.repository.AgreementProductRepository;
import com.paytech.commercial.domainservice.repository.AgreementRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeRecordRepository;
import com.paytech.commercial.domainservice.repository.BusinessChangeVersionRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermPlanRepository;
import com.paytech.commercial.domainservice.repository.CommercialTermVersionRepository;
import com.paytech.commercial.domainservice.repository.PricingProfileCondRepository;
import com.paytech.commercial.domainservice.repository.PricingProfileRepository;
import com.paytech.commercial.domainservice.repository.ProductCapabilityRepository;
import com.paytech.commercial.domainservice.repository.ProductRepository;
import com.paytech.commercial.domainservice.service.AgreementService;
import com.paytech.commercial.domainservice.service.PricingProfileService;
import com.paytech.commercial.domainservice.service.impl.AgreementServiceImpl;
import com.paytech.commercial.domainservice.service.impl.BusinessChangeHistoryWriter;
import com.paytech.commercial.domainservice.service.impl.PricingProfileServiceImpl;
import com.paytech.commercial.service.processor.ActivateAgreementProcessor;
import com.paytech.commercial.service.processor.ActivatePricingProfileProcessor;
import com.paytech.commercial.integration.CheckoutQualificationClient;
import com.paytech.commercial.service.processor.AddAgreementCapabilityProcessor;
import com.paytech.commercial.service.processor.CheckCapabilityPrerequisitesProcessor;
import com.paytech.commercial.service.processor.CommercialTermLoader;
import com.paytech.commercial.service.processor.CreateAgreementProcessor;
import com.paytech.commercial.service.processor.CreatePricingProfileProcessor;
import com.paytech.commercial.service.processor.RecordAgreementAcceptanceProcessor;
import com.paytech.commercial.service.processor.ReplaceAgreementProductsProcessor;
import com.paytech.commercial.service.processor.ResumeAgreementCapabilityProcessor;
import com.paytech.commercial.service.processor.RetirePricingProfileProcessor;
import com.paytech.commercial.service.processor.SubmitAgreementForSignatureProcessor;
import com.paytech.commercial.service.processor.SuspendAgreementCapabilityProcessor;
import com.paytech.commercial.service.processor.TerminateAgreementProcessor;
import com.paytech.commercial.service.processor.UpdateAgreementCapabilityProcessor;
import com.paytech.commercial.service.processor.UpdatePricingProfileProcessor;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.id.IdGenerator;

/**
 * 合同、签署、订阅、开通项与定价画像运营写入的装配（technique.md §2.2）
 * <li>写事务：REQUIRED、5 秒，只包住本库写入；读事务：只读 REPEATABLE READ、5 秒，保证详情 / 列表与其 Root 版本来自同一快照。</li>
 * <li>Processor 由 Foundation 模板在入口拒绝调用方事务并获取资源锁（RedissonClient 与 Validator 由容器注入）。</li>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class CommercialWriteConfiguration {
    /** BCR 写入协作者。 */
    @Bean
    public BusinessChangeHistoryWriter businessChangeHistoryWriter(IdGenerator idGenerator,
        BusinessChangeRecordRepository recordRepository, BusinessChangeVersionRepository versionRepository) {
        return new BusinessChangeHistoryWriter(idGenerator, recordRepository, versionRepository);
    }

    /** 合同写入与详情服务。 */
    @Bean
    public AgreementService agreementService(DataSourceTransactionManager transactions,
        AgreementRepository agreementRepository, AgreementPartyRepository agreementPartyRepository,
        AgreementProductRepository agreementProductRepository, CompensationEventRegistrar compensationEventRegistrar,
        BusinessChangeHistoryWriter historyWriter, AgreementCapabilityRepository agreementCapabilityRepository,
        AgreementDocumentRefRepository agreementDocumentRefRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository,
        AgreementCapabilityLimitRepository agreementCapabilityLimitRepository,
        AgreementOperatingEntityRepository agreementOperatingEntityRepository) {
        return new AgreementServiceImpl(agreementRepository, writes(transactions), reads(transactions),
            agreementPartyRepository, agreementProductRepository, compensationEventRegistrar, historyWriter,
            agreementCapabilityRepository, agreementDocumentRefRepository, businessChangeRecordRepository,
            agreementCapabilityLimitRepository, agreementOperatingEntityRepository);
    }

    /** 定价画像写入与列表服务。 */
    @Bean
    public PricingProfileService pricingProfileService(DataSourceTransactionManager transactions,
        PricingProfileRepository pricingProfileRepository, BusinessChangeHistoryWriter historyWriter,
        PricingProfileCondRepository pricingProfileCondRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository) {
        return new PricingProfileServiceImpl(writes(transactions), reads(transactions), pricingProfileRepository,
            historyWriter, pricingProfileCondRepository, businessChangeRecordRepository);
    }

    /** POST /agreements/v1。 */
    @Bean
    public CreateAgreementProcessor createAgreementProcessor(Clock clock, IdGenerator idGenerator,
        AgreementService agreementService, AgreementRepository agreementRepository,
        AgreementPartyRepository agreementPartyRepository, BusinessChangeRecordRepository businessChangeRecordRepository,
        AgreementDocumentRefRepository agreementDocumentRefRepository,
        AgreementOperatingEntityRepository agreementOperatingEntityRepository) {
        return new CreateAgreementProcessor(clock, idGenerator, agreementService, agreementRepository,
            agreementPartyRepository, businessChangeRecordRepository, agreementDocumentRefRepository,
            agreementOperatingEntityRepository);
    }

    /** POST /agreements/v1/{id}/submit-for-signature。 */
    @Bean
    public SubmitAgreementForSignatureProcessor submitAgreementForSignatureProcessor(Clock clock,
        AgreementService agreementService, AgreementRepository agreementRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository,
        AgreementDocumentRefRepository agreementDocumentRefRepository,
        AgreementOperatingEntityRepository agreementOperatingEntityRepository) {
        return new SubmitAgreementForSignatureProcessor(clock, agreementService, agreementRepository,
            businessChangeRecordRepository, agreementDocumentRefRepository, agreementOperatingEntityRepository);
    }

    /** POST /agreements/v1/{id}/parties/{partyId}/acceptance。 */
    @Bean
    public RecordAgreementAcceptanceProcessor recordAgreementAcceptanceProcessor(Clock clock, IdGenerator idGenerator,
        AgreementService agreementService, AgreementRepository agreementRepository,
        AgreementPartyRepository agreementPartyRepository, BusinessChangeRecordRepository businessChangeRecordRepository,
        AgreementDocumentRefRepository agreementDocumentRefRepository) {
        return new RecordAgreementAcceptanceProcessor(clock, idGenerator, agreementService, agreementRepository,
            agreementPartyRepository, businessChangeRecordRepository, agreementDocumentRefRepository);
    }

    /** POST /agreements/v1/{id}/activate。 */
    @Bean
    public ActivateAgreementProcessor activateAgreementProcessor(Clock clock, AgreementService agreementService,
        AgreementRepository agreementRepository, BusinessChangeRecordRepository businessChangeRecordRepository,
        AgreementPartyRepository agreementPartyRepository, CommercialTermPlanRepository commercialTermPlanRepository,
        AgreementCapabilityRepository agreementCapabilityRepository,
        AgreementOperatingEntityRepository agreementOperatingEntityRepository) {
        return new ActivateAgreementProcessor(clock, agreementService, agreementRepository, businessChangeRecordRepository,
            agreementPartyRepository, commercialTermPlanRepository, agreementCapabilityRepository,
            agreementOperatingEntityRepository);
    }

    /** POST /agreements/v1/{id}/terminate。 */
    @Bean
    public TerminateAgreementProcessor terminateAgreementProcessor(Clock clock, IdGenerator idGenerator,
        AgreementService agreementService, AgreementRepository agreementRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository) {
        return new TerminateAgreementProcessor(clock, idGenerator, agreementService, agreementRepository,
            businessChangeRecordRepository);
    }

    /** PUT /agreements/v1/{id}/products。 */
    @Bean
    public ReplaceAgreementProductsProcessor replaceAgreementProductsProcessor(Clock clock, IdGenerator idGenerator,
        AgreementService agreementService, ProductRepository productRepository, AgreementRepository agreementRepository,
        AgreementProductRepository agreementProductRepository, ProductCapabilityRepository productCapabilityRepository,
        AgreementCapabilityRepository agreementCapabilityRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository) {
        return new ReplaceAgreementProductsProcessor(clock, idGenerator, agreementService, productRepository,
            agreementRepository, agreementProductRepository, productCapabilityRepository, agreementCapabilityRepository,
            businessChangeRecordRepository);
    }

    /** POST /agreements/v1/{id}/capabilities。 */
    @Bean
    public AddAgreementCapabilityProcessor addAgreementCapabilityProcessor(Clock clock, IdGenerator idGenerator,
        AgreementService agreementService, ProductRepository productRepository, AgreementRepository agreementRepository,
        AgreementProductRepository agreementProductRepository, ProductCapabilityRepository productCapabilityRepository,
        AgreementCapabilityRepository agreementCapabilityRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository,
        AgreementOperatingEntityRepository agreementOperatingEntityRepository,
        ProductCapabilityDimensionRepository productCapabilityDimensionRepository) {
        return new AddAgreementCapabilityProcessor(clock, idGenerator, agreementService, productRepository,
            agreementRepository, agreementProductRepository, productCapabilityRepository, agreementCapabilityRepository,
            businessChangeRecordRepository, agreementOperatingEntityRepository, productCapabilityDimensionRepository);
    }

    /** PUT /agreements/v1/{id}/capabilities/{capabilityId}。 */
    @Bean
    public UpdateAgreementCapabilityProcessor updateAgreementCapabilityProcessor(Clock clock, IdGenerator idGenerator,
        AgreementService agreementService, AgreementRepository agreementRepository,
        AgreementCapabilityRepository agreementCapabilityRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository,
        AgreementCapabilityLimitRepository agreementCapabilityLimitRepository,
        ProductCapabilityDimensionRepository productCapabilityDimensionRepository) {
        return new UpdateAgreementCapabilityProcessor(clock, idGenerator, agreementService, agreementRepository,
            agreementCapabilityRepository, businessChangeRecordRepository, agreementCapabilityLimitRepository,
            productCapabilityDimensionRepository);
    }

    /** POST /agreements/v1/{id}/capabilities/{capabilityId}/suspension。 */
    @Bean
    public SuspendAgreementCapabilityProcessor suspendAgreementCapabilityProcessor(Clock clock,
        AgreementService agreementService, AgreementRepository agreementRepository,
        AgreementCapabilityRepository agreementCapabilityRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository,
        AgreementCapabilityLimitRepository agreementCapabilityLimitRepository) {
        return new SuspendAgreementCapabilityProcessor(clock, agreementService, agreementRepository,
            agreementCapabilityRepository, businessChangeRecordRepository, agreementCapabilityLimitRepository);
    }

    /** POST /agreements/v1/{id}/capabilities/{capabilityId}/resumption。 */
    @Bean
    public ResumeAgreementCapabilityProcessor resumeAgreementCapabilityProcessor(Clock clock,
        AgreementService agreementService, AgreementRepository agreementRepository,
        AgreementCapabilityRepository agreementCapabilityRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository,
        AgreementCapabilityLimitRepository agreementCapabilityLimitRepository) {
        return new ResumeAgreementCapabilityProcessor(clock, agreementService, agreementRepository,
            agreementCapabilityRepository, businessChangeRecordRepository, agreementCapabilityLimitRepository);
    }

    /** 开通项前置门禁唯一入口：POST /agreements/v1/{id}/prerequisite-checks、前置检查任务、资格事件与重查 Job 共用。 */
    @Bean
    public CheckCapabilityPrerequisitesProcessor checkCapabilityPrerequisitesProcessor(Clock clock,
        CommercialTermLoader termLoader, AgreementService agreementService, AgreementRepository agreementRepository,
        CheckoutQualificationClient qualificationClient, ProductCapabilityRepository productCapabilityRepository,
        CommercialTermPlanRepository commercialTermPlanRepository,
        AgreementCapabilityRepository agreementCapabilityRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository,
        CommercialTermVersionRepository commercialTermVersionRepository,
        AgreementCapabilityLimitRepository agreementCapabilityLimitRepository) {
        return new CheckCapabilityPrerequisitesProcessor(clock, termLoader, agreementService, agreementRepository,
            qualificationClient, productCapabilityRepository, commercialTermPlanRepository, agreementCapabilityRepository,
            businessChangeRecordRepository, commercialTermVersionRepository, agreementCapabilityLimitRepository);
    }

    /** POST /pricing-profiles/v1。 */
    @Bean
    public CreatePricingProfileProcessor createPricingProfileProcessor(Clock clock, IdGenerator idGenerator,
        PricingProfileService pricingProfileService, PricingProfileRepository pricingProfileRepository,
        PricingProfileCondRepository pricingProfileCondRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository) {
        return new CreatePricingProfileProcessor(clock, idGenerator, pricingProfileService, pricingProfileRepository,
            pricingProfileCondRepository, businessChangeRecordRepository);
    }

    /** PUT /pricing-profiles/v1/{id}。 */
    @Bean
    public UpdatePricingProfileProcessor updatePricingProfileProcessor(Clock clock, IdGenerator idGenerator,
        PricingProfileService pricingProfileService, PricingProfileRepository pricingProfileRepository,
        PricingProfileCondRepository pricingProfileCondRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository) {
        return new UpdatePricingProfileProcessor(clock, idGenerator, pricingProfileService, pricingProfileRepository,
            pricingProfileCondRepository, businessChangeRecordRepository);
    }

    /** POST /pricing-profiles/v1/{id}/activate。 */
    @Bean
    public ActivatePricingProfileProcessor activatePricingProfileProcessor(Clock clock, IdGenerator idGenerator,
        PricingProfileService pricingProfileService, PricingProfileRepository pricingProfileRepository,
        PricingProfileCondRepository pricingProfileCondRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository) {
        return new ActivatePricingProfileProcessor(clock, idGenerator, pricingProfileService, pricingProfileRepository,
            pricingProfileCondRepository, businessChangeRecordRepository);
    }

    /** POST /pricing-profiles/v1/{id}/retire。 */
    @Bean
    public RetirePricingProfileProcessor retirePricingProfileProcessor(Clock clock, IdGenerator idGenerator,
        PricingProfileService pricingProfileService, PricingProfileRepository pricingProfileRepository,
        PricingProfileCondRepository pricingProfileCondRepository,
        BusinessChangeRecordRepository businessChangeRecordRepository) {
        return new RetirePricingProfileProcessor(clock, idGenerator, pricingProfileService, pricingProfileRepository,
            pricingProfileCondRepository, businessChangeRecordRepository);
    }

    /** 本库写事务：REQUIRED、5 秒。 */
    static TransactionTemplate writes(DataSourceTransactionManager transactions) {
        TransactionTemplate writes = new TransactionTemplate(transactions);
        writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        writes.setTimeout(5);
        return writes;
    }

    /** 本库只读快照：REPEATABLE READ、5 秒。 */
    static TransactionTemplate reads(DataSourceTransactionManager transactions) {
        TransactionTemplate reads = new TransactionTemplate(transactions);
        reads.setReadOnly(true);
        reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        reads.setTimeout(5);
        return reads;
    }
}
