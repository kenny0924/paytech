package com.paytech.platform.support.configuration;

import javax.sql.DataSource;

import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.support.JdbcTransactionManager;

import com.paytech.platform.support.auditsecurity.configuration.OperationsAuditProperties;
import com.zaxxer.hikari.HikariDataSource;

/** 同库独立写池不参与主数据源的默认候选，避免主业务自动配置退让。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OperationsAuditProperties.class)
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("${operations.authentication.enabled:false} || ${client.authentication.enabled:false}")
public class OperationsAuditPersistenceConfiguration {
    @Bean(name = "auditWriteDataSource", defaultCandidate = false)
    HikariDataSource auditWriteDataSource(DataSourceProperties properties, Environment environment,
        OperationsAuditProperties budgets) {
        HikariDataSource pool = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        Binder.get(environment).bind("spring.datasource.hikari", Bindable.ofInstance(pool));
        pool.setPoolName("platform-operations-audit");
        pool.setMaximumPoolSize(budgets.getMaximumPoolSize());
        pool.setMinimumIdle(budgets.getMinimumIdle());
        pool.setInitializationFailTimeout(-1);
        pool.setConnectionTimeout(budgets.getConnectionTimeoutMillis());
        pool.setValidationTimeout(budgets.getValidationTimeoutMillis());
        pool.addDataSourceProperty("connectTimeout", Integer.toString(budgets.getConnectTimeoutMillis()));
        pool.addDataSourceProperty("socketTimeout", Integer.toString(budgets.getSocketTimeoutMillis()));
        // 保留宿主 UTC 初始化 SQL 和所有连接安全属性；Driver 在初始化 SQL 之前设置会话锁预算。
        String existing = pool.getDataSourceProperties().getProperty("sessionVariables");
        pool.addDataSourceProperty("sessionVariables", (existing == null || existing.isBlank() ? "" : existing + ",")
            + "innodb_lock_wait_timeout=" + budgets.getLockWaitTimeoutSeconds());
        return pool;
    }

    @Bean(name = "auditWriteTransactionManager", defaultCandidate = false)
    JdbcTransactionManager auditWriteTransactionManager(@Qualifier("auditWriteDataSource") DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }

    @Bean(name = "auditWriteSqlSessionFactory", defaultCandidate = false)
    SqlSessionFactory auditWriteSqlSessionFactory(@Qualifier("auditWriteDataSource") DataSource dataSource,
        @Qualifier("persistenceConfigurationCustomizer") ConfigurationCustomizer customizer,
        ObjectProvider<Interceptor> interceptors, OperationsAuditProperties budgets) throws Exception {
        var configuration = new org.apache.ibatis.session.Configuration();
        customizer.customize(configuration);
        configuration.setDefaultStatementTimeout(budgets.getStatementTimeoutSeconds());
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
            .getResources("classpath*:mapper/audit/OperationsAuditMapper.xml"));
        factory.setPlugins(interceptors.orderedStream().toArray(Interceptor[]::new));
        return factory.getObject();
    }

    @Bean(name = "auditWriteSqlSessionTemplate", defaultCandidate = false)
    SqlSessionTemplate auditWriteSqlSessionTemplate(@Qualifier("auditWriteSqlSessionFactory") SqlSessionFactory factory) {
        return new SqlSessionTemplate(factory);
    }
}
