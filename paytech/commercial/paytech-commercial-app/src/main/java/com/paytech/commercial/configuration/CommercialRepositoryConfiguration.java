package com.paytech.commercial.configuration;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.commercial.core.dal.mapper.*;
import com.paytech.commercial.domainservice.repository.*;
import com.paytech.commercial.domainservice.repository.impl.*;

/** commercial 单表仓储装配，复用本库 MyBatis 会话与事务。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
public class CommercialRepositoryConfiguration {
    /** 装配 t_agreement 仓储。 */
    @Bean
    public AgreementRepository agreementRepository(SqlSessionFactory factory) {
        return new AgreementRepositoryImpl(new SqlSessionTemplate(factory).getMapper(AgreementMapper.class));
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
        return new AgreementPartyRepositoryImpl(new SqlSessionTemplate(factory).getMapper(AgreementPartyMapper.class));
    }

    /** 装配 t_agreement_document_ref 仓储。 */
    @Bean
    public AgreementDocumentRefRepository agreementDocumentRefRepository(SqlSessionFactory factory) {
        return new AgreementDocumentRefRepositoryImpl(
            new SqlSessionTemplate(factory).getMapper(AgreementDocumentRefMapper.class));
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

    /** 装配 t_business_change_record 仓储。 */
    @Bean
    public BusinessChangeRecordRepository businessChangeRecordRepository(SqlSessionFactory factory) {
        return new BusinessChangeRecordRepositoryImpl(
            new SqlSessionTemplate(factory).getMapper(BusinessChangeRecordMapper.class));
    }
}
