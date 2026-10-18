package com.paytech.client.master.configuration;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.client.master.core.dal.mapper.*;
import com.paytech.client.master.domainservice.repository.*;
import com.paytech.client.master.domainservice.repository.impl.*;

/** client-master 单表仓储装配，复用本库 MyBatis 会话与事务。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class ClientMasterRepositoryConfiguration {
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

    /** 装配 t_party_profile 仓储。 */
    @Bean
    public PartyProfileRepository partyProfileRepository(SqlSessionFactory factory) {
        return new PartyProfileRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(PartyProfileMapper.class));
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

    /** 装配 t_client_stored_card_setting 仓储。 */
    @Bean
    public ClientStoredCardSettingRepository clientStoredCardSettingRepository(SqlSessionFactory factory) {
        return new ClientStoredCardSettingRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(ClientStoredCardSettingMapper.class));
    }

    /** 装配 t_business_change_record 仓储。 */
    @Bean
    public BusinessChangeRecordRepository businessChangeRecordRepository(SqlSessionFactory factory) {
        return new BusinessChangeRecordRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(BusinessChangeRecordMapper.class));
    }
}
