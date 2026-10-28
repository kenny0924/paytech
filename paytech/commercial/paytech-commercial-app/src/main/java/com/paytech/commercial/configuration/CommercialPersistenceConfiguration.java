package com.paytech.commercial.configuration;

import java.time.Instant;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import com.paytech.commercial.api.enums.*;
import com.paytech.commercial.domain.enums.*;
import com.paytech.foundation.mybatis.typehandler.CodeEnumTypeHandler;
import com.paytech.foundation.mybatis.typehandler.InstantTypeHandler;
import com.zaxxer.hikari.HikariDataSource;

/** commercial 唯一持久化装配（mor_commercial）；连接参数复用 Spring 标准配置。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@EnableConfigurationProperties(DataSourceProperties.class)
public class CommercialPersistenceConfiguration {
    /** 数据库密码由部署环境注入。 */
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    /** 只加载本服务 core 的 Mapper XML，并显式注册本服务的封闭枚举与 UTC 时间 TypeHandler。 */
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setMapperLocations(
            new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*Mapper.xml"));
        org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
        config.setDefaultStatementTimeout(5);
        config.setCacheEnabled(false);
        config.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        var handlers = config.getTypeHandlerRegistry();
        handlers.register(Instant.class, InstantTypeHandler.class);
        handlers.register(AgreementAgreementType.class, CodeEnumTypeHandler.class);
        handlers.register(AgreementAgreementFormat.class, CodeEnumTypeHandler.class);
        handlers.register(AgreementStatus.class, CodeEnumTypeHandler.class);
        handlers.register(AgreementPartyPartyRole.class, CodeEnumTypeHandler.class);
        handlers.register(AgreementPartyAcceptanceMethod.class, CodeEnumTypeHandler.class);
        handlers.register(AgreementDocumentRefDocumentType.class, CodeEnumTypeHandler.class);
        handlers.register(CommercialTermPlanStatus.class, CodeEnumTypeHandler.class);
        handlers.register(CommercialTermVersionStatus.class, CodeEnumTypeHandler.class);
        handlers.register(CommercialTermRoundingMode.class, CodeEnumTypeHandler.class);
        handlers.register(CommercialTermComponentComponentType.class, CodeEnumTypeHandler.class);
        handlers.register(CommercialTermComponentBandMode.class, CodeEnumTypeHandler.class);
        handlers.register(PricingProfileStatus.class, CodeEnumTypeHandler.class);
        handlers.register(BusinessMode.class, CodeEnumTypeHandler.class);
        handlers.register(FundMode.class, CodeEnumTypeHandler.class);
        handlers.register(PricingAuthority.class, CodeEnumTypeHandler.class);
        handlers.register(ProductCode.class, CodeEnumTypeHandler.class);
        handlers.register(ProductBusinessModeScope.class, CodeEnumTypeHandler.class);
        handlers.register(ProductStatus.class, CodeEnumTypeHandler.class);
        handlers.register(CapabilityKind.class, CodeEnumTypeHandler.class);
        handlers.register(AgreementCapabilityStatus.class, CodeEnumTypeHandler.class);
        handlers.register(AgreementCapabilityExecutionStatus.class, CodeEnumTypeHandler.class);
        handlers.register(CommercialTermOwnerType.class, CodeEnumTypeHandler.class);
        handlers.register(PricingProfileKind.class, CodeEnumTypeHandler.class);
        handlers.register(PricingDimension.class, CodeEnumTypeHandler.class);
        handlers.register(PricingModel.class, CodeEnumTypeHandler.class);
        handlers.register(FeeTaxTreatment.class, CodeEnumTypeHandler.class);
        handlers.register(RefundBehavior.class, CodeEnumTypeHandler.class);
        handlers.register(BandBasis.class, CodeEnumTypeHandler.class);
        handlers.register(CalculationBase.class, CodeEnumTypeHandler.class);
        handlers.register(BusinessChangeRecordRecordType.class, CodeEnumTypeHandler.class);
        handlers.register(BusinessChangeRecordAggregateRootType.class, CodeEnumTypeHandler.class);
        handlers.register(BusinessChangeOperation.class, CodeEnumTypeHandler.class);
        handlers.register(BusinessChangeSnapshotFormat.class, CodeEnumTypeHandler.class);
        factory.setConfiguration(config);
        return factory.getObject();
    }

    /** 本服务唯一本地事务管理器。 */
    @Bean
    public DataSourceTransactionManager transactionManager(DataSource source) {
        return new DataSourceTransactionManager(source);
    }
}
