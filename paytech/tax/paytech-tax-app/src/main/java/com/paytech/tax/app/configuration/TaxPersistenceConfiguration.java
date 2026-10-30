package com.paytech.tax.app.configuration;

import org.apache.ibatis.type.TypeHandlerRegistry;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.foundation.mybatis.typehandler.CodeEnumTypeHandler;
import com.paytech.foundation.mybatis.typehandler.InstantTypeHandler;
import com.paytech.tax.api.enums.IssuanceMode;
import com.paytech.tax.api.enums.ManagedBy;
import com.paytech.tax.api.enums.TaxAmountSource;
import com.paytech.tax.api.enums.TaxCalculationStage;
import com.paytech.tax.api.enums.TaxCalculationStatus;
import com.paytech.tax.api.enums.TaxDocumentIssueStatus;
import com.paytech.tax.api.enums.TaxDocumentKind;
import com.paytech.tax.api.enums.TaxEvidenceType;
import com.paytech.tax.api.enums.TaxPriceMode;
import com.paytech.tax.api.enums.TaxReportingStatus;
import com.paytech.tax.api.enums.TaxTreatment;
import com.paytech.tax.core.enums.DocumentSubmissionStatus;
import com.paytech.tax.core.enums.EvidenceValidationStatus;
import com.paytech.tax.core.enums.FilingFrequency;
import com.paytech.tax.core.enums.LiableRole;
import com.paytech.tax.core.enums.MarketCollectionStatus;
import com.paytech.tax.core.enums.MarketObligation;
import com.paytech.tax.core.enums.ReportingEntryRole;
import com.paytech.tax.core.enums.TaxCalculationMethod;
import com.paytech.tax.core.enums.TaxCategoryStatus;
import com.paytech.tax.core.enums.TaxPeriodStatus;
import com.paytech.tax.core.enums.TaxPolicyStatus;
import com.paytech.tax.core.enums.TaxRegistrationStatus;
import com.paytech.tax.core.enums.TaxTransactionKind;
import com.paytech.tax.core.enums.TaxTransactionOrigin;

/**
 * 税务服务（tax-service） MyBatis 装配
 * <li>单 DataSource / 单事务管理器（Spring Boot 自动装配 spring.datasource），{@code @MapperScan} 只扫描本服务 core 的 Mapper 包。</li>
 * <li>主键由应用分配；列名按下划线转驼峰映射到 DataObject。</li>
 * <li>每个持久化枚举显式注册 CodeEnumTypeHandler，未知 Code 读取失败；Instant 统一按 UTC 持久化；JSON 列按字符串读写。</li>
 */
@Configuration(proxyBeanMethods = false)
@MapperScan("com.paytech.tax.core.dal.mapper")
public class TaxPersistenceConfiguration {

    /** Tax MyBatis 全局定制。 */
    @Bean
    ConfigurationCustomizer taxMyBatisCustomizer() {
        return configuration -> {
            configuration.setUseGeneratedKeys(false);
            configuration.setMapUnderscoreToCamelCase(true);
            TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
            registry.register(InstantTypeHandler.class);
            register(registry, ManagedBy.class);
            register(registry, IssuanceMode.class);
            register(registry, TaxPriceMode.class);
            register(registry, TaxTreatment.class);
            register(registry, TaxAmountSource.class);
            register(registry, TaxDocumentKind.class);
            register(registry, TaxEvidenceType.class);
            register(registry, TaxReportingStatus.class);
            register(registry, TaxCalculationStage.class);
            register(registry, TaxCalculationStatus.class);
            register(registry, TaxDocumentIssueStatus.class);
            register(registry, LiableRole.class);
            register(registry, FilingFrequency.class);
            register(registry, TaxPeriodStatus.class);
            register(registry, TaxPolicyStatus.class);
            register(registry, MarketObligation.class);
            register(registry, TaxCategoryStatus.class);
            register(registry, ReportingEntryRole.class);
            register(registry, TaxTransactionKind.class);
            register(registry, TaxCalculationMethod.class);
            register(registry, TaxTransactionOrigin.class);
            register(registry, MarketCollectionStatus.class);
            register(registry, TaxRegistrationStatus.class);
            register(registry, DocumentSubmissionStatus.class);
            register(registry, EvidenceValidationStatus.class);
        };
    }

    /** 为一个持久化枚举注册按 Code 读写的 TypeHandler。 */
    static <E extends Enum<E> & com.paytech.foundation.common.enums.CodeEnum> void register(TypeHandlerRegistry registry,
        Class<E> type) {
        registry.register(type, new CodeEnumTypeHandler<>(type));
    }
}
