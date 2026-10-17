package com.paytech.commercial.configuration;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.commercial.domainservice.repository.*;
import com.paytech.commercial.domainservice.service.CheckoutQueryService;
import com.paytech.commercial.domainservice.service.impl.CheckoutQueryServiceImpl;
import com.paytech.commercial.integration.CheckoutQualificationClient;
import com.paytech.commercial.integration.impl.UnavailableCheckoutQualificationClient;
import com.paytech.commercial.service.processor.CheckoutResolutionProcessor;

/** Checkout 解析复用 CCT 单表仓储，只装配自身只读 Service、Processor 和资格端口。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@Import(ClientContractRepositoryConfiguration.class)
public class CheckoutResolutionConfiguration {
    /** 多表加载共用同一个有界的只读事务，仓储不自行创建事务。 */
    @Bean
    public CheckoutQueryService checkoutService(Clock clock, DataSourceTransactionManager transactions,
            ClientRepository clientRepository,
            AgreementRepository agreementRepository,
            LocalPartnerRepository localPartnerRepository,
            PartyProfileRepository partyProfileRepository,
            AgreementPartyRepository agreementPartyRepository,
            OperatingEntityRepository operatingEntityRepository,
            SellerDisclosureRepository sellerDisclosureRepository,
            CommercialTermPlanRepository commercialTermPlanRepository,
            CommercialTermPriceRepository commercialTermPriceRepository,
            AgreementDocumentRefRepository agreementDocumentRefRepository,
            BusinessChangeRecordRepository businessChangeRecordRepository,
            EntitySourceRelationRepository entitySourceRelationRepository,
            OperatingEntityScopeRepository operatingEntityScopeRepository,
            PaymentMethodProfileRepository paymentMethodProfileRepository,
            ClientBusinessProfileRepository clientBusinessProfileRepository,
            CommercialTermVersionRepository commercialTermVersionRepository,
            ConsumerLegalDocumentRepository consumerLegalDocumentRepository,
            ClientEntityAffiliationRepository clientEntityAffiliationRepository,
            CommercialTermComponentRepository commercialTermComponentRepository,
            AgreementOperatingEntityRepository agreementOperatingEntityRepository,
            OperatingEntityApplicationRepository operatingEntityApplicationRepository,
            CommercialTermComponentBandRepository commercialTermComponentBandRepository,
            ClientBusinessClassificationRepository clientBusinessClassificationRepository) {
        TransactionTemplate reads = new TransactionTemplate(transactions);
        reads.setReadOnly(true);
        reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        reads.setTimeout(10);
        return new CheckoutQueryServiceImpl(clock, reads,
                clientRepository,
                agreementRepository,
                localPartnerRepository,
                partyProfileRepository,
                agreementPartyRepository,
                operatingEntityRepository,
                sellerDisclosureRepository,
                commercialTermPlanRepository,
                commercialTermPriceRepository,
                agreementDocumentRefRepository,
                businessChangeRecordRepository,
                entitySourceRelationRepository,
                operatingEntityScopeRepository,
                paymentMethodProfileRepository,
                clientBusinessProfileRepository,
                commercialTermVersionRepository,
                consumerLegalDocumentRepository,
                clientEntityAffiliationRepository,
                commercialTermComponentRepository,
                agreementOperatingEntityRepository,
                operatingEntityApplicationRepository,
                commercialTermComponentBandRepository,
                clientBusinessClassificationRepository);
    }

    /** 真实 Risk 合同尚未提供时保持不可用，不生成资格成功事实。 */
    @Bean
    public CheckoutQualificationClient checkoutQualification() {
        return new UnavailableCheckoutQualificationClient();
    }

    /** 装配先读、事务外资格检查、再复核的入口。 */
    @Bean
    public CheckoutResolutionProcessor checkoutResolution(CheckoutQueryService service,
            CheckoutQualificationClient qualification) {
        return new CheckoutResolutionProcessor(service, qualification);
    }
}
