package com.paytech.risk.configuration;

import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.id.SnowflakeIdGenerator;
import com.paytech.risk.control.domainservice.port.ControlIdentifierGenerator;
import com.paytech.risk.decision.domain.authoring.CheckoutPrepareAuthoringDescriptor;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizerImpl;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidatorImpl;
import com.paytech.risk.decision.domain.rule.RuleAstValidator;
import com.paytech.risk.decision.domain.rule.RuleAstValidatorImpl;
import com.paytech.risk.compliance.domainservice.port.ComplianceIdentifierGenerator;
import com.paytech.risk.decision.domainservice.port.DecisionIdentifierGenerator;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Risk 进程级基础能力装配。
 */
@Configuration(proxyBeanMethods = false)
public class RiskRuntimeConfiguration {

    @Bean
    CheckoutPrepareAuthoringDescriptor checkoutPrepareAuthoringDescriptor() {
        return CheckoutPrepareAuthoringDescriptor.v1();
    }

    @Bean
    DecisionActionRegistry decisionActionRegistry() {
        return DecisionActionRegistry.checkoutPrepareV1();
    }

    @Bean
    RuleAstValidator ruleAstValidator(
        CheckoutPrepareAuthoringDescriptor descriptor,
        DecisionActionRegistry registry
    ) {
        return new RuleAstValidatorImpl(descriptor, registry);
    }

    @Bean
    RiskDecisionCanonicalizer riskDecisionCanonicalizer() {
        return new RiskDecisionCanonicalizerImpl();
    }

    @Bean
    DecisionReleaseStaticValidator decisionReleaseStaticValidator() {
        return new DecisionReleaseStaticValidatorImpl();
    }

    /**
     * 为所有业务写入提供唯一的 UTC 时间源。
     *
     * @return 进程级 UTC Clock
     */
    @Bean
    Clock riskClock() {
        return Clock.systemUTC();
    }

    /**
     * 进程唯一的 Foundation Snowflake 生成器；补偿任务登记与各模块标识端口共用，避免同一 Worker 两个实例产生重复标识。
     *
     * @param regionId 部署分配的 Region 标识
     * @param workerId 部署分配的 Worker 标识
     * @return 进程级标识生成器
     */
    @Bean
    IdGenerator riskIdGenerator(
        @Value("${decision.identifier.region-id}") long regionId,
        @Value("${decision.identifier.worker-id}") long workerId
    ) {
        return new SnowflakeIdGenerator(regionId, workerId);
    }

    /**
     * 将 Foundation Snowflake 实现隔离在决策模块定义的窄端口之后。
     *
     * @param riskIdGenerator 进程唯一的 Snowflake 生成器
     * @return 决策标识生成端口
     */
    @Bean
    DecisionIdentifierGenerator decisionIdentifierGenerator(IdGenerator riskIdGenerator) {
        return riskIdGenerator::nextId;
    }

    /**
     * 合规模块标识端口，复用进程唯一的 Snowflake 生成器。
     *
     * @param riskIdGenerator 进程唯一的 Snowflake 生成器
     * @return 合规标识生成端口
     */
    @Bean
    ComplianceIdentifierGenerator complianceIdentifierGenerator(IdGenerator riskIdGenerator) {
        return riskIdGenerator::nextId;
    }

    /**
     * 控制模块标识端口，复用进程唯一的 Snowflake 生成器。
     *
     * @param riskIdGenerator 进程唯一的 Snowflake 生成器
     * @return 控制标识生成端口
     */
    @Bean
    ControlIdentifierGenerator controlIdentifierGenerator(IdGenerator riskIdGenerator) {
        return riskIdGenerator::nextId;
    }
}
