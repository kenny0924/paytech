package com.paytech.billing.app.configuration;

import org.apache.ibatis.type.TypeHandlerRegistry;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.billing.api.enums.EconomicsType;
import com.paytech.billing.core.enums.AmountRoundingMode;
import com.paytech.billing.core.enums.ChannelCostCalculationBase;
import com.paytech.billing.core.enums.ChannelCostComponentType;
import com.paytech.billing.core.enums.ChannelCostPlanStatus;
import com.paytech.billing.core.enums.ChannelCostVersionStatus;
import com.paytech.billing.core.enums.EconomicFactStatus;
import com.paytech.foundation.mybatis.typehandler.CodeEnumTypeHandler;
import com.paytech.foundation.mybatis.typehandler.InstantTypeHandler;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

/**
 * 计费服务（billing-service） MyBatis 装配
 * <li>单 DataSource / 单事务管理器（Spring Boot 自动装配 spring.datasource），{@code @MapperScan} 只扫描本服务 core 的 Mapper 包。</li>
 * <li>主键由应用分配；列名按下划线转驼峰映射到 DataObject。</li>
 * <li>每个持久化枚举显式注册 CodeEnumTypeHandler，未知 Code 读取失败；Instant 统一按 UTC 持久化。</li>
 */
@Configuration(proxyBeanMethods = false)
@MapperScan("com.paytech.billing.core.dal.mapper")
public class BillingPersistenceConfiguration {

    /** Billing MyBatis 全局定制。 */
    @Bean
    ConfigurationCustomizer billingMyBatisCustomizer() {
        return configuration -> {
            configuration.setUseGeneratedKeys(false);
            configuration.setMapUnderscoreToCamelCase(true);
            TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
            registry.register(InstantTypeHandler.class);
            registry.register(EconomicsType.class, new CodeEnumTypeHandler<>(EconomicsType.class));
            registry.register(EconomicFactStatus.class, new CodeEnumTypeHandler<>(EconomicFactStatus.class));
            registry.register(ChannelCostPlanStatus.class, new CodeEnumTypeHandler<>(ChannelCostPlanStatus.class));
            registry.register(ChannelCostVersionStatus.class, new CodeEnumTypeHandler<>(ChannelCostVersionStatus.class));
            registry.register(ChannelCostComponentType.class, new CodeEnumTypeHandler<>(ChannelCostComponentType.class));
            registry.register(ChannelCostCalculationBase.class, new CodeEnumTypeHandler<>(ChannelCostCalculationBase.class));
            registry.register(AmountRoundingMode.class, new CodeEnumTypeHandler<>(AmountRoundingMode.class));
            registry.register(CurrencySpecStatus.class, new CodeEnumTypeHandler<>(CurrencySpecStatus.class));
        };
    }
}
