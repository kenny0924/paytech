package com.paytech.account.app.configuration;

import org.apache.ibatis.type.TypeHandlerRegistry;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.core.enums.NormalSide;
import com.paytech.foundation.mybatis.typehandler.CodeEnumTypeHandler;
import com.paytech.foundation.mybatis.typehandler.InstantTypeHandler;

/**
 * 账户服务（account-service） MyBatis 装配
 * <li>单 DataSource / 单事务管理器（Spring Boot 自动装配 spring.datasource），{@code @MapperScan} 只扫描本服务 core 的 Mapper 包。</li>
 * <li>主键由应用分配；列名按下划线转驼峰映射到 DataObject。</li>
 * <li>每个持久化枚举显式注册 CodeEnumTypeHandler，未知 Code 读取失败；Instant 统一按 UTC 持久化。</li>
 */
@Configuration(proxyBeanMethods = false)
@MapperScan("com.paytech.account.core.dal.mapper")
public class AccountPersistenceConfiguration {

    /** Account MyBatis 全局定制。 */
    @Bean
    ConfigurationCustomizer accountMyBatisCustomizer() {
        return configuration -> {
            configuration.setUseGeneratedKeys(false);
            configuration.setMapUnderscoreToCamelCase(true);
            TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
            registry.register(InstantTypeHandler.class);
            registry.register(BalanceBucket.class, new CodeEnumTypeHandler<>(BalanceBucket.class));
            registry.register(OwnerType.class, new CodeEnumTypeHandler<>(OwnerType.class));
            registry.register(NormalSide.class, new CodeEnumTypeHandler<>(NormalSide.class));
        };
    }
}
