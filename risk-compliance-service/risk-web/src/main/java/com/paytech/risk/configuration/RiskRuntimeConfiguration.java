package com.paytech.risk.configuration;

import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.id.SnowflakeIdGenerator;
import com.paytech.risk.decision.domain.authoring.CheckoutPrepareAuthoringDescriptor;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizerImpl;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidatorImpl;
import com.paytech.risk.decision.domain.rule.RuleAstValidator;
import com.paytech.risk.decision.domain.rule.RuleAstValidatorImpl;
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
     * 将 Foundation Snowflake 实现隔离在决策模块定义的窄端口之后。
     *
     * @param regionId 部署分配的 Region 标识
     * @param workerId 部署分配的 Worker 标识
     * @return 决策标识生成端口
     */
    @Bean
    DecisionIdentifierGenerator decisionIdentifierGenerator(
        @Value("${decision.identifier.region-id}") long regionId,
        @Value("${decision.identifier.worker-id}") long workerId
    ) {
        IdGenerator foundationGenerator = new SnowflakeIdGenerator(regionId, workerId);
        return foundationGenerator::nextId;
    }
}
