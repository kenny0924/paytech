package com.paytech.compliance.configuration;

import org.apache.ibatis.type.TypeHandlerRegistry;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.foundation.mybatis.typehandler.InstantTypeHandler;

/**
 * compliance 单库（mor_compliance）持久化基础配置：只扫描合规 Mapper；合规 DataObject 的业务代码均以字符串持久化，无闭合枚举 TypeHandler。
 */
@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = "com.paytech.compliance.core.dal.mapper", sqlSessionTemplateRef = "sqlSessionTemplate")
public class CompliancePersistenceConfiguration {

    /**
     * 禁止数据库生成主键，并显式注册 UTC 时间转换。
     *
     * @return MyBatis 配置定制器
     */
    @Bean
    ConfigurationCustomizer compliancePersistenceConfigurationCustomizer() {
        return configuration -> {
            configuration.setUseGeneratedKeys(false);
            TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
            registry.register(InstantTypeHandler.class);
        };
    }
}
