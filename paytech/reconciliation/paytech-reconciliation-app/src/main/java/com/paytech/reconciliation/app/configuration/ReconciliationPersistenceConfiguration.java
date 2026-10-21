package com.paytech.reconciliation.app.configuration;

import org.apache.ibatis.type.TypeHandlerRegistry;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.mybatis.typehandler.CodeEnumTypeHandler;
import com.paytech.foundation.mybatis.typehandler.InstantTypeHandler;
import com.paytech.reconciliation.api.enums.AccountPurpose;
import com.paytech.reconciliation.api.enums.BankMatchStatus;
import com.paytech.reconciliation.api.enums.BankStatementStatus;
import com.paytech.reconciliation.api.enums.CashAccountPendingAction;
import com.paytech.reconciliation.api.enums.CashAccountStatus;
import com.paytech.reconciliation.api.enums.CashDirection;
import com.paytech.reconciliation.api.enums.ChannelLineType;
import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.api.enums.CheckerDecision;
import com.paytech.reconciliation.api.enums.ClearingAccountStatus;
import com.paytech.reconciliation.api.enums.ClearingDirection;
import com.paytech.reconciliation.api.enums.ExpectedMovementStatus;
import com.paytech.reconciliation.api.enums.ExpectedMovementType;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.MatchMethod;
import com.paytech.reconciliation.api.enums.PayoutComponentType;
import com.paytech.reconciliation.api.enums.PlatformTxnSource;
import com.paytech.reconciliation.api.enums.PlatformTxnSourceRefType;
import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.api.enums.ReconClassification;
import com.paytech.reconciliation.api.enums.ReconEntityType;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import com.paytech.reconciliation.api.enums.ReconExceptionStatus;
import com.paytech.reconciliation.api.enums.ReconExceptionType;
import com.paytech.reconciliation.api.enums.ReconLayer;
import com.paytech.reconciliation.api.enums.RematchTargetType;
import com.paytech.reconciliation.api.enums.ResolutionStatus;
import com.paytech.reconciliation.api.enums.ResolutionType;
import com.paytech.reconciliation.api.enums.SettlementDirection;

/**
 * 对账服务（reconciliation-service） MyBatis 装配
 * <li>单 DataSource（mor_reconciliation）/ 单事务管理器（Spring Boot 自动装配 spring.datasource），{@code @MapperScan} 只扫描本服务 core 的
 * Mapper 包；paytech-messaging 与 paytech-compensation-event 自带的 Mapper 由各自自动配置加载。</li>
 * <li>主键由应用分配；列名按下划线转驼峰映射到 DataObject。</li>
 * <li>DataObject 的每个闭合枚举列类型都显式注册 CodeEnumTypeHandler（按 javaType），未知 Code 读取失败；Instant 统一按 UTC 持久化。</li>
 */
@Configuration(proxyBeanMethods = false)
@MapperScan("com.paytech.reconciliation.core.dal.mapper")
public class ReconciliationPersistenceConfiguration {

    /** Reconciliation MyBatis 全局定制。 */
    @Bean
    ConfigurationCustomizer reconciliationMyBatisCustomizer() {
        return configuration -> {
            configuration.setUseGeneratedKeys(false);
            configuration.setMapUnderscoreToCamelCase(true);
            TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
            registry.register(InstantTypeHandler.class);
            register(registry, AccountPurpose.class);
            register(registry, BankMatchStatus.class);
            register(registry, BankStatementStatus.class);
            register(registry, CashAccountPendingAction.class);
            register(registry, CashAccountStatus.class);
            register(registry, CashDirection.class);
            register(registry, ChannelLineType.class);
            register(registry, ChannelStatementStatus.class);
            register(registry, CheckerDecision.class);
            register(registry, ClearingAccountStatus.class);
            register(registry, ClearingDirection.class);
            register(registry, ExpectedMovementStatus.class);
            register(registry, ExpectedMovementType.class);
            register(registry, LineMatchStatus.class);
            register(registry, MatchMethod.class);
            register(registry, PayoutComponentType.class);
            register(registry, PlatformTxnSource.class);
            register(registry, PlatformTxnSourceRefType.class);
            register(registry, PlatformTxnStatus.class);
            register(registry, ReconClassification.class);
            register(registry, ReconEntityType.class);
            register(registry, ReconExceptionSourceType.class);
            register(registry, ReconExceptionStatus.class);
            register(registry, ReconExceptionType.class);
            register(registry, ReconLayer.class);
            register(registry, RematchTargetType.class);
            register(registry, ResolutionStatus.class);
            register(registry, ResolutionType.class);
            register(registry, SettlementDirection.class);
        };
    }

    /** 为一个持久化枚举类型注册独立的 CodeEnumTypeHandler 实例。 */
    private static <E extends Enum<E> & CodeEnum> void register(TypeHandlerRegistry registry, Class<E> type) {
        registry.register(type, new CodeEnumTypeHandler<>(type));
    }
}
