package com.paytech.client.master.configuration;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.client.master.domainservice.repository.*;
import com.paytech.client.master.domainservice.service.CheckoutCandidateQueryService;
import com.paytech.client.master.domainservice.service.ClientBusinessClassificationQueryService;
import com.paytech.client.master.domainservice.service.ClientStoredCardSettingQueryService;
import com.paytech.client.master.domainservice.service.impl.CheckoutCandidateQueryServiceImpl;
import com.paytech.client.master.domainservice.service.impl.ClientBusinessClassificationQueryServiceImpl;
import com.paytech.client.master.domainservice.service.impl.ClientStoredCardSettingQueryServiceImpl;

/** Checkout 候选、已存卡开关与主经营分类的服务间只读能力装配；复用本库单表仓储。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class CheckoutCandidateConfiguration {
    /**
     * 候选查询与复核各用一个 REPEATABLE READ 只读事务（10 秒）读取全部主档事实，仓储不自行创建事务。
     */
    @Bean
    public CheckoutCandidateQueryService checkoutCandidateQueryService(Clock clock, DataSourceTransactionManager transactions,
        ClientRepository clientRepository, PartnerRoleRepository partnerRoleRepository, LocalPartnerRepository localPartnerRepository,
        PartyProfileRepository partyProfileRepository, OperatingEntityRepository operatingEntityRepository,
        SellerDisclosureRepository sellerDisclosureRepository, BusinessChangeRecordRepository businessChangeRecordRepository,
        EntitySourceRelationRepository entitySourceRelationRepository,
        OperatingEntityScopeRepository operatingEntityScopeRepository,
        ClientBusinessProfileRepository clientBusinessProfileRepository,
        ConsumerLegalDocumentRepository consumerLegalDocumentRepository,
        ClientStoredCardSettingRepository clientStoredCardSettingRepository,
        ClientEntityAffiliationRepository clientEntityAffiliationRepository,
        OperatingEntityApplicationRepository operatingEntityApplicationRepository,
        ClientBusinessClassificationRepository clientBusinessClassificationRepository) {
        TransactionTemplate reads = new TransactionTemplate(transactions);
        reads.setReadOnly(true);
        reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        reads.setTimeout(10);
        return new CheckoutCandidateQueryServiceImpl(clock, reads, clientRepository, partnerRoleRepository, localPartnerRepository,
            partyProfileRepository, operatingEntityRepository, sellerDisclosureRepository, businessChangeRecordRepository,
            entitySourceRelationRepository, operatingEntityScopeRepository, clientBusinessProfileRepository,
            consumerLegalDocumentRepository, clientStoredCardSettingRepository, clientEntityAffiliationRepository,
            operatingEntityApplicationRepository, clientBusinessClassificationRepository);
    }

    /** 单表单行读取，不开事务。 */
    @Bean
    public ClientStoredCardSettingQueryService clientStoredCardSettingQueryService(
        ClientStoredCardSettingRepository repository) {
        return new ClientStoredCardSettingQueryServiceImpl(repository);
    }

    /** Client、经营资料与分类在一个 REPEATABLE READ 只读事务（5 秒）内读取，确认状态与分类行来自同一快照。 */
    @Bean
    public ClientBusinessClassificationQueryService clientBusinessClassificationQueryService(
        DataSourceTransactionManager transactions, ClientRepository clientRepository,
        ClientBusinessProfileRepository clientBusinessProfileRepository,
        ClientBusinessClassificationRepository clientBusinessClassificationRepository) {
        TransactionTemplate reads = new TransactionTemplate(transactions);
        reads.setReadOnly(true);
        reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        reads.setTimeout(5);
        return new ClientBusinessClassificationQueryServiceImpl(reads, clientRepository, clientBusinessProfileRepository,
            clientBusinessClassificationRepository);
    }
}
