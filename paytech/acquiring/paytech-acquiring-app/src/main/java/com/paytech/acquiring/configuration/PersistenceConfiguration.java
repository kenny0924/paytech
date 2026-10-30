package com.paytech.acquiring.configuration;

import com.paytech.acquiring.api.client.v1.enums.OrderAmountStatus;
import com.paytech.acquiring.api.client.v1.enums.OrderCreationMode;
import com.paytech.acquiring.domain.enums.CheckoutSessionStatus;
import com.paytech.acquiring.domain.enums.RefundStatus;
import com.paytech.acquiring.domain.enums.RefundRequestOrigin;
import com.paytech.acquiring.domain.enums.EntitySourceType;
import com.paytech.acquiring.domain.enums.FundsAvailabilityStatus;
import com.paytech.acquiring.domain.enums.OrderStatus;
import com.paytech.acquiring.domain.enums.PaymentAttemptStatus;
import com.paytech.acquiring.domain.enums.PaymentIntentStatus;
import com.paytech.foundation.mybatis.typehandler.CodeEnumTypeHandler;
import com.paytech.foundation.compensation.model.CompensationEventStatus;
import com.paytech.foundation.mybatis.typehandler.InstantTypeHandler;
import com.paytech.tax.api.enums.TaxCalculationStage;
import com.paytech.tax.api.enums.TaxCalculationStatus;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** acquiring 唯一持久化装配（mor_acquiring）：一个 DataSource / 事务管理器（Spring Boot 自动配置）与一个 @MapperScan。 */
@Configuration(proxyBeanMethods = false)
@MapperScan("com.paytech.acquiring.core.dal.mapper")
public class PersistenceConfiguration {

    /**
     * Keeps identifiers application-assigned and persists every {@code Instant} as UTC.
     *
     * @return the acquiring MyBatis customization
     */
    @Bean
    ConfigurationCustomizer commerceMyBatisCustomizer() {
        return configuration -> {
            configuration.setUseGeneratedKeys(false);
            configuration.getTypeHandlerRegistry().register(RefundStatus.class, new CodeEnumTypeHandler<>(RefundStatus.class));
            configuration.getTypeHandlerRegistry().register(RefundRequestOrigin.class, new CodeEnumTypeHandler<>(RefundRequestOrigin.class));
            configuration.getTypeHandlerRegistry().register(InstantTypeHandler.class);
            configuration.getTypeHandlerRegistry().register(OrderStatus.class,
                new CodeEnumTypeHandler<>(OrderStatus.class));
            configuration.getTypeHandlerRegistry().register(OrderCreationMode.class,
                new CodeEnumTypeHandler<>(OrderCreationMode.class));
            configuration.getTypeHandlerRegistry().register(OrderAmountStatus.class,
                new CodeEnumTypeHandler<>(OrderAmountStatus.class));
            configuration.getTypeHandlerRegistry().register(TaxCalculationStage.class,
                new CodeEnumTypeHandler<>(TaxCalculationStage.class));
            configuration.getTypeHandlerRegistry().register(TaxCalculationStatus.class,
                new CodeEnumTypeHandler<>(TaxCalculationStatus.class));
            configuration.getTypeHandlerRegistry().register(CheckoutSessionStatus.class,
                new CodeEnumTypeHandler<>(CheckoutSessionStatus.class));
            configuration.getTypeHandlerRegistry().register(PaymentIntentStatus.class,
                new CodeEnumTypeHandler<>(PaymentIntentStatus.class));
            configuration.getTypeHandlerRegistry().register(PaymentAttemptStatus.class,
                new CodeEnumTypeHandler<>(PaymentAttemptStatus.class));
            configuration.getTypeHandlerRegistry().register(FundsAvailabilityStatus.class,
                new CodeEnumTypeHandler<>(FundsAvailabilityStatus.class));
            configuration.getTypeHandlerRegistry().register(EntitySourceType.class,
                new CodeEnumTypeHandler<>(EntitySourceType.class));
            configuration.getTypeHandlerRegistry().register(com.paytech.acquiring.domain.enums.ConsumerAcceptanceType.class,
                new CodeEnumTypeHandler<>(com.paytech.acquiring.domain.enums.ConsumerAcceptanceType.class));
            configuration.getTypeHandlerRegistry().register(com.paytech.acquiring.domain.enums.CheckoutEvidenceScene.class,
                new CodeEnumTypeHandler<>(com.paytech.acquiring.domain.enums.CheckoutEvidenceScene.class));
            // Foundation 当前 XML 显式引用通用 Handler 类，MyBatis 按类缓存会复用最后注册的枚举实例。
            // 业务映射按 Java 类型取各自实例；此处为补偿 XML 保留准确类型，并由真实 Claim/扫描测试保护。
            // TODO(Foundation)：其 XML 按 Java 类型解析 Handler 后移除此装配顺序约束。
            configuration.getTypeHandlerRegistry().register(CompensationEventStatus.class,
                new CodeEnumTypeHandler<>(CompensationEventStatus.class));
        };
    }
}
