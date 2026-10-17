package com.paytech.client.master.configuration;

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
import com.zaxxer.hikari.HikariDataSource;
import com.paytech.foundation.mybatis.typehandler.InstantTypeHandler;
import com.paytech.foundation.mybatis.typehandler.CodeEnumTypeHandler;
import com.paytech.client.master.domain.enums.*;

/** client-master 唯一持久化装配（mor_client_master）；连接参数复用 Spring 标准配置，不由某个业务接口拥有。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.datasource", name = "url")
@EnableConfigurationProperties(DataSourceProperties.class)
public class ClientMasterPersistenceConfiguration {
    /** 数据库密码由部署环境注入，不建立 Checkout 专属连接或配置别名。 */
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

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
        config.getTypeHandlerRegistry().register(Instant.class, InstantTypeHandler.class);
        config.getTypeHandlerRegistry().register(ClientStatus.class, CodeEnumTypeHandler.class);
        config.getTypeHandlerRegistry().register(ClientType.class, CodeEnumTypeHandler.class);
        config.getTypeHandlerRegistry().register(PartyRelationType.class, CodeEnumTypeHandler.class);
        config.getTypeHandlerRegistry().register(PartyRelationStatus.class, CodeEnumTypeHandler.class);
        config.getTypeHandlerRegistry().register(ClientApplicationStatus.class, CodeEnumTypeHandler.class);
        config.getTypeHandlerRegistry().register(ClientEntityAffiliationStatus.class, CodeEnumTypeHandler.class);
        config.getTypeHandlerRegistry().register(OperatingEntityStatus.class, CodeEnumTypeHandler.class);
        config.getTypeHandlerRegistry().register(EntitySourceRelationSourceType.class, CodeEnumTypeHandler.class);
        config.getTypeHandlerRegistry().register(EntitySourceRelationClientSourceBasis.class,
                CodeEnumTypeHandler.class);
        config.getTypeHandlerRegistry().register(EntitySourceRelationStatus.class, CodeEnumTypeHandler.class);
        config.getTypeHandlerRegistry().register(OperatingEntityScopeStatus.class, CodeEnumTypeHandler.class);
        config.getTypeHandlerRegistry().register(OperatingEntityApplicationStatus.class, CodeEnumTypeHandler.class);
        config.getTypeHandlerRegistry().register(LocalPartnerStatus.class, CodeEnumTypeHandler.class);
        config.getTypeHandlerRegistry().register(ConsumerLegalDocumentDocumentType.class, CodeEnumTypeHandler.class);
        config.getTypeHandlerRegistry().register(ConsumerLegalDocumentStatus.class, CodeEnumTypeHandler.class);
        config.getTypeHandlerRegistry().register(SellerDisclosureStatus.class, CodeEnumTypeHandler.class);
        config.getTypeHandlerRegistry().register(BusinessChangeRecordRecordType.class, CodeEnumTypeHandler.class);
        config.getTypeHandlerRegistry().register(BusinessChangeRecordAggregateRootType.class,
                CodeEnumTypeHandler.class);
        config.getTypeHandlerRegistry().register(BusinessChangeOperation.class, CodeEnumTypeHandler.class);
        config.getTypeHandlerRegistry().register(BusinessChangeSnapshotFormat.class, CodeEnumTypeHandler.class);
        factory.setConfiguration(config);
        return factory.getObject();
    }
    /** 同一 client-master 进程的所有数据库能力共享一个本地事务管理器。 */
    @Bean
    public DataSourceTransactionManager transactionManager(DataSource source) {
        return new DataSourceTransactionManager(source);
    }
}
