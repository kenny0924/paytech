package com.paytech.risk.configuration;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.mybatis.typehandler.CodeEnumTypeHandler;
import com.paytech.foundation.mybatis.typehandler.InstantTypeHandler;
import com.paytech.risk.control.core.enums.RiskListEntrySource;
import com.paytech.risk.control.core.enums.RiskListEntryStatus;
import com.paytech.risk.control.core.enums.RiskListPurpose;
import com.paytech.risk.control.core.enums.RiskListStatus;
import com.paytech.risk.control.domain.list.RiskSubjectType;
import com.paytech.risk.decision.domain.enums.ActionCategory;
import com.paytech.risk.decision.domain.enums.AuthenticationRequirement;
import com.paytech.risk.decision.domain.enums.ComparisonOperator;
import com.paytech.risk.decision.domain.enums.DataClassification;
import com.paytech.risk.decision.domain.enums.DecisionBusinessDomain;
import com.paytech.risk.decision.domain.enums.DecisionEnvironment;
import com.paytech.risk.decision.domain.enums.DecisionObligationFailurePolicy;
import com.paytech.risk.decision.domain.enums.DecisionObligationType;
import com.paytech.risk.decision.domain.enums.DecisionPointStatus;
import com.paytech.risk.decision.domain.enums.DecisionPointVersionStatus;
import com.paytech.risk.decision.domain.enums.DecisionReleaseStatus;
import com.paytech.risk.decision.domain.enums.DecisionValue;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.DictionaryStatus;
import com.paytech.risk.decision.domain.enums.EvaluationMode;
import com.paytech.risk.decision.domain.enums.EvaluationStrategy;
import com.paytech.risk.decision.domain.enums.ExecutionVersionStatus;
import com.paytech.risk.decision.domain.enums.FailureStrategy;
import com.paytech.risk.decision.domain.enums.FlowEdgeType;
import com.paytech.risk.decision.domain.enums.FlowNodeType;
import com.paytech.risk.decision.domain.enums.FlowStatus;
import com.paytech.risk.decision.domain.enums.GroupOperator;
import com.paytech.risk.decision.domain.enums.MergePolicy;
import com.paytech.risk.decision.domain.enums.NodeType;
import com.paytech.risk.decision.domain.enums.OperandRole;
import com.paytech.risk.decision.domain.enums.OperandSource;
import com.paytech.risk.decision.domain.enums.ReleaseApprovalStatus;
import com.paytech.risk.decision.domain.enums.ReleaseAuditEventType;
import com.paytech.risk.decision.domain.enums.RuleSetManagementStatus;
import com.paytech.risk.decision.domain.enums.RuleSetVersionStatus;
import com.paytech.risk.decision.domain.enums.RuleTraceType;
import com.paytech.risk.decision.domain.enums.ShadowRunStatus;
import com.paytech.risk.decision.domain.enums.StaticCheckCode;
import com.paytech.risk.decision.domain.enums.StaticCheckSeverity;
import com.paytech.risk.decision.domain.enums.StaticCheckStatus;
import com.paytech.risk.decision.domain.enums.TechnicalStatus;
import com.paytech.risk.decision.domain.enums.VelocityEventCode;
import com.paytech.risk.decision.domain.enums.VelocityMetric;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Risk 单库持久化基础配置。
 */
@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = {
    "com.paytech.risk.decision.core.dal.mapper",
    "com.paytech.risk.control.core.dal.mapper",
    "com.paytech.risk.compliance.core.dal.mapper"
}, sqlSessionTemplateRef = "sqlSessionTemplate")
public class PersistenceConfiguration {

    /**
     * 禁止数据库生成主键，并显式注册 UTC 时间转换。
     *
     * @return MyBatis 配置定制器
     */
    @Bean
    ConfigurationCustomizer persistenceConfigurationCustomizer() {
        return configuration -> {
            configuration.setUseGeneratedKeys(false);
            TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
            registry.register(InstantTypeHandler.class);
            registerCodeEnumHandler(registry, ActionCategory.class);
            registerCodeEnumHandler(registry, DictionaryStatus.class);
            registerCodeEnumHandler(registry, ComparisonOperator.class);
            registerCodeEnumHandler(registry, DataClassification.class);
            registerCodeEnumHandler(registry, DecisionBusinessDomain.class);
            registerCodeEnumHandler(registry, DecisionEnvironment.class);
            registerCodeEnumHandler(registry, DecisionObligationFailurePolicy.class);
            registerCodeEnumHandler(registry, DecisionObligationType.class);
            registerCodeEnumHandler(registry, DecisionPointStatus.class);
            registerCodeEnumHandler(registry, DecisionPointVersionStatus.class);
            registerCodeEnumHandler(registry, DecisionReleaseStatus.class);
            registerCodeEnumHandler(registry, DecisionValue.class);
            registerCodeEnumHandler(registry, DecisionValueType.class);
            registerCodeEnumHandler(registry, EvaluationStrategy.class);
            registerCodeEnumHandler(registry, ExecutionVersionStatus.class);
            registerCodeEnumHandler(registry, FailureStrategy.class);
            registerCodeEnumHandler(registry, GroupOperator.class);
            registerCodeEnumHandler(registry, MergePolicy.class);
            registerCodeEnumHandler(registry, NodeType.class);
            registerCodeEnumHandler(registry, OperandRole.class);
            registerCodeEnumHandler(registry, OperandSource.class);
            registerCodeEnumHandler(registry, ReleaseApprovalStatus.class);
            registerCodeEnumHandler(registry, ReleaseAuditEventType.class);
            registerCodeEnumHandler(registry, RuleSetVersionStatus.class);
            registerCodeEnumHandler(registry, RuleSetManagementStatus.class);
            registerCodeEnumHandler(registry, FlowStatus.class);
            registerCodeEnumHandler(registry, FlowNodeType.class);
            registerCodeEnumHandler(registry, FlowEdgeType.class);
            registerCodeEnumHandler(registry, StaticCheckCode.class);
            registerCodeEnumHandler(registry, StaticCheckSeverity.class);
            registerCodeEnumHandler(registry, StaticCheckStatus.class);
            registerCodeEnumHandler(registry, VelocityEventCode.class);
            registerCodeEnumHandler(registry, VelocityMetric.class);
            registerCodeEnumHandler(registry, EvaluationMode.class);
            registerCodeEnumHandler(registry, TechnicalStatus.class);
            registerCodeEnumHandler(registry, RuleTraceType.class);
            registerCodeEnumHandler(registry, ShadowRunStatus.class);
            registerCodeEnumHandler(registry, AuthenticationRequirement.class);
            registerCodeEnumHandler(registry, RiskSubjectType.class);
            registerCodeEnumHandler(registry, RiskListPurpose.class);
            registerCodeEnumHandler(registry, RiskListStatus.class);
            registerCodeEnumHandler(registry, RiskListEntryStatus.class);
            registerCodeEnumHandler(registry, RiskListEntrySource.class);
        };
    }

    /**
     * 为一个具体的闭合业务枚举注册稳定 Code 转换器。
     *
     * <p>调用方必须逐个传入已确认的枚举类型；不得把 {@link CodeEnum} 注册为全局默认处理器。</p>
     *
     * @param registry MyBatis TypeHandler 注册表
     * @param enumType 具体业务枚举类型
     * @param <E> 同时实现 {@link CodeEnum} 的枚举
     */
    static <E extends Enum<E> & CodeEnum> void registerCodeEnumHandler(
        TypeHandlerRegistry registry,
        Class<E> enumType
    ) {
        registry.register(enumType, CodeEnumTypeHandler.class);
    }
}
