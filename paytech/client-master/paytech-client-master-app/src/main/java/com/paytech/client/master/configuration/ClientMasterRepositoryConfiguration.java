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

    /** 装配 t_partner_role 仓储。 */
    @Bean
    public PartnerRoleRepository partnerRoleRepository(SqlSessionFactory factory) {
        return new PartnerRoleRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(PartnerRoleMapper.class));
    }

    /** 装配 t_party_role 仓储。 */
    @Bean
    public PartyRoleRepository partyRoleRepository(SqlSessionFactory factory) {
        return new PartyRoleRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(PartyRoleMapper.class));
    }

    /** 装配 t_business_change_version 仓储。 */
    @Bean
    public BusinessChangeVersionRepository businessChangeVersionRepository(SqlSessionFactory factory) {
        return new BusinessChangeVersionRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(BusinessChangeVersionMapper.class));
    }

    /** 装配 t_business_change_record 仓储。 */
    @Bean
    public BusinessChangeRecordRepository businessChangeRecordRepository(SqlSessionFactory factory) {
        return new BusinessChangeRecordRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(BusinessChangeRecordMapper.class));
    }

    /** 装配 t_jurisdiction 仓储。 */
    @Bean
    public JurisdictionRepository jurisdictionRepository(SqlSessionFactory factory) {
        return new JurisdictionRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(JurisdictionMapper.class));
    }

    /** 装配 t_rule_setting 仓储。 */
    @Bean
    public RuleSettingRepository ruleSettingRepository(SqlSessionFactory factory) {
        return new RuleSettingRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(RuleSettingMapper.class));
    }

    /** 装配 t_organization_type 仓储。 */
    @Bean
    public OrganizationTypeRepository organizationTypeRepository(SqlSessionFactory factory) {
        return new OrganizationTypeRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(OrganizationTypeMapper.class));
    }

    /** 装配 t_identifier_type 仓储。 */
    @Bean
    public IdentifierTypeRepository identifierTypeRepository(SqlSessionFactory factory) {
        return new IdentifierTypeRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(IdentifierTypeMapper.class));
    }

    /** 装配 t_relation_type 仓储。 */
    @Bean
    public RelationTypeRepository relationTypeRepository(SqlSessionFactory factory) {
        return new RelationTypeRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(RelationTypeMapper.class));
    }

    /** 装配 t_address_format_component 仓储。 */
    @Bean
    public AddressFormatComponentRepository addressFormatComponentRepository(SqlSessionFactory factory) {
        return new AddressFormatComponentRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(AddressFormatComponentMapper.class));
    }

    /** 装配 t_catalog_item 仓储。 */
    @Bean
    public CatalogItemRepository catalogItemRepository(SqlSessionFactory factory) {
        return new CatalogItemRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(CatalogItemMapper.class));
    }

    /** 装配 t_attribute_definition 仓储。 */
    @Bean
    public AttributeDefinitionRepository attributeDefinitionRepository(SqlSessionFactory factory) {
        return new AttributeDefinitionRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(AttributeDefinitionMapper.class));
    }

    /** 装配 t_party_attribute 仓储。 */
    @Bean
    public PartyAttributeRepository partyAttributeRepository(SqlSessionFactory factory) {
        return new PartyAttributeRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(PartyAttributeMapper.class));
    }

    /** 装配 t_onboarding_schema 仓储。 */
    @Bean
    public OnboardingSchemaRepository onboardingSchemaRepository(SqlSessionFactory factory) {
        return new OnboardingSchemaRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(OnboardingSchemaMapper.class));
    }

    /** 装配 t_onboarding_schema_field 仓储。 */
    @Bean
    public OnboardingSchemaFieldRepository onboardingSchemaFieldRepository(SqlSessionFactory factory) {
        return new OnboardingSchemaFieldRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(OnboardingSchemaFieldMapper.class));
    }

    /** 装配 t_application_declaration 仓储。 */
    @Bean
    public ApplicationDeclarationRepository applicationDeclarationRepository(SqlSessionFactory factory) {
        return new ApplicationDeclarationRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(ApplicationDeclarationMapper.class));
    }

    /** 装配 t_partner_onboarding_code 仓储。 */
    @Bean
    public PartnerOnboardingCodeRepository partnerOnboardingCodeRepository(SqlSessionFactory factory) {
        return new PartnerOnboardingCodeRepositoryImpl(
                new SqlSessionTemplate(factory).getMapper(PartnerOnboardingCodeMapper.class));
    }
}
