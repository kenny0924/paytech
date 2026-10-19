package com.paytech.ledger.app.configuration;

import org.apache.ibatis.type.TypeHandlerRegistry;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.ledger.api.enums.AmountSource;
import com.paytech.ledger.api.enums.AutoInstructionType;
import com.paytech.foundation.mybatis.typehandler.CodeEnumTypeHandler;
import com.paytech.foundation.mybatis.typehandler.InstantTypeHandler;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.ledger.api.enums.RuleOwnerRole;
import com.paytech.ledger.core.enums.AccountClass;
import com.paytech.ledger.core.enums.AccountStatus;
import com.paytech.ledger.core.enums.AdjustmentStatus;
import com.paytech.ledger.core.enums.ApprovalDecision;
import com.paytech.ledger.core.enums.ApprovalRole;
import com.paytech.ledger.core.enums.BookStatus;
import com.paytech.ledger.core.enums.CloseBookStatus;
import com.paytech.ledger.core.enums.CloseStepStatus;
import com.paytech.ledger.core.enums.CloseStepType;
import com.paytech.ledger.core.enums.EntrySide;
import com.paytech.ledger.core.enums.FinanceEntityStatus;
import com.paytech.ledger.core.enums.InstructionOrigin;
import com.paytech.ledger.core.enums.InstructionStatus;
import com.paytech.ledger.core.enums.JournalEntryType;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.core.enums.NormalSide;
import com.paytech.ledger.core.enums.PeriodCloseStatus;
import com.paytech.ledger.core.enums.PeriodCloseType;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.core.enums.ReferenceDataStatus;
import com.paytech.ledger.core.enums.RuleVersionStatus;
import com.paytech.ledger.core.enums.TrialBalanceStatus;
import com.paytech.ledger.core.enums.TrialExceptionStatus;
import com.paytech.ledger.core.enums.TrialExceptionType;
import com.paytech.ledger.core.enums.TrialReferenceType;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

/**
 * 会计服务（ledger-service） MyBatis 装配
 * <li>单 DataSource / 单事务管理器（Spring Boot 自动装配 spring.datasource），{@code @MapperScan} 只扫描本服务 core 的 Mapper 包。</li>
 * <li>主键由应用分配；列名按下划线转驼峰映射到 DataObject。</li>
 * <li>每个持久化枚举显式注册 CodeEnumTypeHandler，未知 Code 读取失败；Instant 统一按 UTC 持久化。</li>
 */
@Configuration(proxyBeanMethods = false)
@MapperScan("com.paytech.ledger.core.dal.mapper")
public class LedgerPersistenceConfiguration {

    /** Ledger MyBatis 全局定制。 */
    @Bean
    ConfigurationCustomizer ledgerMyBatisCustomizer() {
        return configuration -> {
            configuration.setUseGeneratedKeys(false);
            configuration.setMapUnderscoreToCamelCase(true);
            TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
            registry.register(InstantTypeHandler.class);
            registry.register(PeriodCloseType.class, new CodeEnumTypeHandler<>(PeriodCloseType.class));
            registry.register(PeriodCloseStatus.class, new CodeEnumTypeHandler<>(PeriodCloseStatus.class));
            registry.register(CloseStepType.class, new CodeEnumTypeHandler<>(CloseStepType.class));
            registry.register(CloseStepStatus.class, new CodeEnumTypeHandler<>(CloseStepStatus.class));
            registry.register(CloseBookStatus.class, new CodeEnumTypeHandler<>(CloseBookStatus.class));
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
            registry.register(LedgerOwnerType.class, new CodeEnumTypeHandler<>(LedgerOwnerType.class));
            registry.register(PeriodStatus.class, new CodeEnumTypeHandler<>(PeriodStatus.class));
            registry.register(ReferenceDataStatus.class, new CodeEnumTypeHandler<>(ReferenceDataStatus.class));
            registry.register(TrialReferenceType.class, new CodeEnumTypeHandler<>(TrialReferenceType.class));
            registry.register(AmountSource.class, new CodeEnumTypeHandler<>(AmountSource.class));
            registry.register(AutoInstructionType.class, new CodeEnumTypeHandler<>(AutoInstructionType.class));
            registry.register(RuleOwnerRole.class, new CodeEnumTypeHandler<>(RuleOwnerRole.class));
            registry.register(RuleVersionStatus.class, new CodeEnumTypeHandler<>(RuleVersionStatus.class));
            registry.register(TrialExceptionType.class, new CodeEnumTypeHandler<>(TrialExceptionType.class));
            registry.register(TrialExceptionStatus.class, new CodeEnumTypeHandler<>(TrialExceptionStatus.class));
            registry.register(TrialBalanceStatus.class, new CodeEnumTypeHandler<>(TrialBalanceStatus.class));
            registry.register(CurrencySpecStatus.class, new CodeEnumTypeHandler<>(CurrencySpecStatus.class));
        };
    }
}
