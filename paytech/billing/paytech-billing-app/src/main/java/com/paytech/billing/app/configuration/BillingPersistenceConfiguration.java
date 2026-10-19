package com.paytech.billing.app.configuration;

import org.apache.ibatis.type.TypeHandlerRegistry;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactOriginType;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.api.enums.LineLinkType;
import com.paytech.billing.api.enums.SourceRuleType;
import com.paytech.billing.core.enums.CalendarDayType;
import com.paytech.billing.core.enums.CalendarStatus;
import com.paytech.billing.core.enums.FactTargetBucket;
import com.paytech.foundation.mybatis.typehandler.CodeEnumTypeHandler;
import com.paytech.foundation.mybatis.typehandler.InstantTypeHandler;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

/**
 * 计费服务（billing-service） MyBatis 装配
 * <li>单 DataSource / 单事务管理器（Spring Boot 自动装配 spring.datasource），{@code @MapperScan} 只扫描本服务 core 的 Mapper 包。</li>
 * <li>主键由应用分配；列名按下划线转驼峰映射到 DataObject。</li>
 * <li>每个持久化枚举显式注册 CodeEnumTypeHandler，未知 Code 读取失败；Instant 统一按 UTC 持久化；明细行 JSON 计算证据的 TypeHandler
 * 只在 Mapper XML 中按列引用。</li>
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
            registry.register(BillingFactType.class, new CodeEnumTypeHandler<>(BillingFactType.class));
            registry.register(BillingFactStatus.class, new CodeEnumTypeHandler<>(BillingFactStatus.class));
            registry.register(FactOriginType.class, new CodeEnumTypeHandler<>(FactOriginType.class));
            registry.register(FactRefType.class, new CodeEnumTypeHandler<>(FactRefType.class));
            registry.register(FactComponentType.class, new CodeEnumTypeHandler<>(FactComponentType.class));
            registry.register(FactPartyRole.class, new CodeEnumTypeHandler<>(FactPartyRole.class));
            registry.register(FactLineDirection.class, new CodeEnumTypeHandler<>(FactLineDirection.class));
            registry.register(FactTargetBucket.class, new CodeEnumTypeHandler<>(FactTargetBucket.class));
            registry.register(SourceRuleType.class, new CodeEnumTypeHandler<>(SourceRuleType.class));
            registry.register(LineLinkType.class, new CodeEnumTypeHandler<>(LineLinkType.class));
            registry.register(CalendarStatus.class, new CodeEnumTypeHandler<>(CalendarStatus.class));
            registry.register(CalendarDayType.class, new CodeEnumTypeHandler<>(CalendarDayType.class));
            registry.register(CurrencySpecStatus.class, new CodeEnumTypeHandler<>(CurrencySpecStatus.class));
        };
    }
}
