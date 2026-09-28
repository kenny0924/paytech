package com.paytech.client.contract.configuration;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.client.contract.core.dal.mapper.*;
import com.paytech.client.contract.domainservice.repository.*;
import com.paytech.client.contract.domainservice.repository.impl.*;

/** CCT 单表仓储装配，复用本库 MyBatis 会话与事务。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class ClientContractRepositoryConfiguration {
    /** 装配 t_client 仓储。 */
    @Bean
    public ClientRepository clientRepository(SqlSessionFactory factory) {
        return new ClientRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(ClientMapper.class));
    }

    /** 装配 t_client_business_profile 仓储。 */
    @Bean
    public ClientBusinessProfileRepository clientBusinessProfileRepository(SqlSessionFactory factory) {
        return new ClientBusinessProfileRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(ClientBusinessProfileMapper.class));
    }

    /** 装配 t_client_business_classification 仓储。 */
    @Bean
    public ClientBusinessClassificationRepository clientBusinessClassificationRepository(SqlSessionFactory factory) {
        return new ClientBusinessClassificationRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(ClientBusinessClassificationMapper.class));
    }

    /** 装配 t_client_entity_affiliation 仓储。 */
    @Bean
    public ClientEntityAffiliationRepository clientEntityAffiliationRepository(SqlSessionFactory factory) {
        return new ClientEntityAffiliationRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(ClientEntityAffiliationMapper.class));
    }

    /** 装配 t_operating_entity 仓储。 */
    @Bean
    public OperatingEntityRepository operatingEntityRepository(SqlSessionFactory factory) {
        return new OperatingEntityRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(OperatingEntityMapper.class));
    }

    /** 装配 t_entity_source_relation 仓储。 */
    @Bean
    public EntitySourceRelationRepository entitySourceRelationRepository(SqlSessionFactory factory) {
        return new EntitySourceRelationRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(EntitySourceRelationMapper.class));
    }

    /** 装配 t_operating_entity_scope 仓储。 */
    @Bean
    public OperatingEntityScopeRepository operatingEntityScopeRepository(SqlSessionFactory factory) {
        return new OperatingEntityScopeRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(OperatingEntityScopeMapper.class));
    }

    /** 装配 t_operating_entity_application 仓储。 */
    @Bean
    public OperatingEntityApplicationRepository operatingEntityApplicationRepository(SqlSessionFactory factory) {
        return new OperatingEntityApplicationRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(OperatingEntityApplicationMapper.class));
    }

    /** 装配 t_local_partner 仓储。 */
    @Bean
    public LocalPartnerRepository localPartnerRepository(SqlSessionFactory factory) {
        return new LocalPartnerRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(LocalPartnerMapper.class));
    }

    /** 装配 t_agreement 仓储。 */
    @Bean
    public AgreementRepository agreementRepository(SqlSessionFactory factory) {
        return new AgreementRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(AgreementMapper.class));
    }

    /** 装配 t_agreement_operating_entity 仓储。 */
    @Bean
    public AgreementOperatingEntityRepository agreementOperatingEntityRepository(SqlSessionFactory factory) {
        return new AgreementOperatingEntityRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(AgreementOperatingEntityMapper.class));
    }

    /** 装配 t_agreement_party 仓储。 */
    @Bean
    public AgreementPartyRepository agreementPartyRepository(SqlSessionFactory factory) {
        return new AgreementPartyRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(AgreementPartyMapper.class));
    }

    /** 装配 t_agreement_document_ref 仓储。 */
    @Bean
    public AgreementDocumentRefRepository agreementDocumentRefRepository(SqlSessionFactory factory) {
        return new AgreementDocumentRefRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(AgreementDocumentRefMapper.class));
    }

    /** 装配 t_party_profile 仓储。 */
    @Bean
    public PartyProfileRepository partyProfileRepository(SqlSessionFactory factory) {
        return new PartyProfileRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(PartyProfileMapper.class));
    }

    /** 装配 t_commercial_term_plan 仓储。 */
    @Bean
    public CommercialTermPlanRepository commercialTermPlanRepository(SqlSessionFactory factory) {
        return new CommercialTermPlanRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(CommercialTermPlanMapper.class));
    }

    /** 装配 t_commercial_term_version 仓储。 */
    @Bean
    public CommercialTermVersionRepository commercialTermVersionRepository(SqlSessionFactory factory) {
        return new CommercialTermVersionRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(CommercialTermVersionMapper.class));
    }

    /** 装配 t_commercial_term_price 仓储。 */
    @Bean
    public CommercialTermPriceRepository commercialTermPriceRepository(SqlSessionFactory factory) {
        return new CommercialTermPriceRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(CommercialTermPriceMapper.class));
    }

    /** 装配 t_commercial_term_component 仓储。 */
    @Bean
    public CommercialTermComponentRepository commercialTermComponentRepository(SqlSessionFactory factory) {
        return new CommercialTermComponentRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(CommercialTermComponentMapper.class));
    }

    /** 装配 t_commercial_term_component_band 仓储。 */
    @Bean
    public CommercialTermComponentBandRepository commercialTermComponentBandRepository(SqlSessionFactory factory) {
        return new CommercialTermComponentBandRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(CommercialTermComponentBandMapper.class));
    }

    /** 装配 t_payment_method_profile 仓储。 */
    @Bean
    public PaymentMethodProfileRepository paymentMethodProfileRepository(SqlSessionFactory factory) {
        return new PaymentMethodProfileRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(PaymentMethodProfileMapper.class));
    }

    /** 装配 t_consumer_legal_document 仓储。 */
    @Bean
    public ConsumerLegalDocumentRepository consumerLegalDocumentRepository(SqlSessionFactory factory) {
        return new ConsumerLegalDocumentRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(ConsumerLegalDocumentMapper.class));
    }

    /** 装配 t_seller_disclosure 仓储。 */
    @Bean
    public SellerDisclosureRepository sellerDisclosureRepository(SqlSessionFactory factory) {
        return new SellerDisclosureRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(SellerDisclosureMapper.class));
    }

    /** 装配 t_business_change_record 仓储。 */
    @Bean
    public BusinessChangeRecordRepository businessChangeRecordRepository(SqlSessionFactory factory) {
        return new BusinessChangeRecordRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(BusinessChangeRecordMapper.class));
    }
}
