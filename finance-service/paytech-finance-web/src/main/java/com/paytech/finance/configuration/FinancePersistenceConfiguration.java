package com.paytech.finance.configuration;

import org.apache.ibatis.type.TypeHandlerRegistry;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.finance.accounting.core.enums.AccountClass;
import com.paytech.finance.accounting.core.enums.AccountStatus;
import com.paytech.finance.accounting.core.enums.AdjustmentStatus;
import com.paytech.finance.accounting.core.enums.ApprovalDecision;
import com.paytech.finance.accounting.core.enums.ApprovalRole;
import com.paytech.finance.accounting.core.enums.BalanceBucket;
import com.paytech.finance.accounting.core.enums.BookStatus;
import com.paytech.finance.accounting.core.enums.EntrySide;
import com.paytech.finance.accounting.core.enums.FinanceEntityStatus;
import com.paytech.finance.accounting.core.enums.InstructionOrigin;
import com.paytech.finance.accounting.core.enums.InstructionStatus;
import com.paytech.finance.accounting.core.enums.JournalEntryType;
import com.paytech.finance.accounting.core.enums.LedgerEntityType;
import com.paytech.finance.accounting.core.enums.NormalSide;
import com.paytech.finance.accounting.core.enums.OwnerType;
import com.paytech.finance.accounting.core.enums.PeriodStatus;
import com.paytech.finance.accounting.core.enums.ReferenceDataStatus;
import com.paytech.foundation.mybatis.typehandler.CodeEnumTypeHandler;
import com.paytech.foundation.mybatis.typehandler.InstantTypeHandler;

/**
 * Finance MyBatis 装配
 * <li>主键由应用分配；列名按下划线转驼峰映射到 DataObject。</li>
 * <li>每个持久化枚举显式注册 CodeEnumTypeHandler，未知 Code 读取失败；Instant 统一按 UTC 持久化。</li>
 */
@Configuration(proxyBeanMethods = false)
public class FinancePersistenceConfiguration {

    /** Finance MyBatis 全局定制。 */
    @Bean
    ConfigurationCustomizer financeMyBatisCustomizer() {
        return configuration -> {
            configuration.setUseGeneratedKeys(false);
            configuration.setMapUnderscoreToCamelCase(true);
            TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
            registry.register(InstantTypeHandler.class);
            registry.register(AccountClass.class, new CodeEnumTypeHandler<>(AccountClass.class));
            registry.register(AccountStatus.class, new CodeEnumTypeHandler<>(AccountStatus.class));
            registry.register(AdjustmentStatus.class, new CodeEnumTypeHandler<>(AdjustmentStatus.class));
            registry.register(ApprovalDecision.class, new CodeEnumTypeHandler<>(ApprovalDecision.class));
            registry.register(ApprovalRole.class, new CodeEnumTypeHandler<>(ApprovalRole.class));
            registry.register(BalanceBucket.class, new CodeEnumTypeHandler<>(BalanceBucket.class));
            registry.register(BookStatus.class, new CodeEnumTypeHandler<>(BookStatus.class));
            registry.register(EntrySide.class, new CodeEnumTypeHandler<>(EntrySide.class));
            registry.register(FinanceEntityStatus.class, new CodeEnumTypeHandler<>(FinanceEntityStatus.class));
            registry.register(InstructionOrigin.class, new CodeEnumTypeHandler<>(InstructionOrigin.class));
            registry.register(InstructionStatus.class, new CodeEnumTypeHandler<>(InstructionStatus.class));
            registry.register(JournalEntryType.class, new CodeEnumTypeHandler<>(JournalEntryType.class));
            registry.register(LedgerEntityType.class, new CodeEnumTypeHandler<>(LedgerEntityType.class));
            registry.register(NormalSide.class, new CodeEnumTypeHandler<>(NormalSide.class));
            registry.register(OwnerType.class, new CodeEnumTypeHandler<>(OwnerType.class));
            registry.register(PeriodStatus.class, new CodeEnumTypeHandler<>(PeriodStatus.class));
            registry.register(ReferenceDataStatus.class, new CodeEnumTypeHandler<>(ReferenceDataStatus.class));
        };
    }
}
