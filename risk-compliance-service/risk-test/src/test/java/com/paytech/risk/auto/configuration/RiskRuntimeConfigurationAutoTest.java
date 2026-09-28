package com.paytech.risk.auto.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.paytech.risk.configuration.RiskRuntimeConfiguration;
import com.paytech.risk.decision.domain.authoring.CheckoutPrepareAuthoringDescriptor;
import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry;
import com.paytech.risk.decision.domain.hash.RiskDecisionCanonicalizer;
import com.paytech.risk.decision.domain.release.DecisionReleaseStaticValidator;
import com.paytech.risk.decision.domain.rule.RuleAstValidator;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Risk 进程级时间源装配测试。 */
class RiskRuntimeConfigurationAutoTest {

    @Test
    void test_singleClockUsesUtc() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of(
                "decision.identifier.region-id=1",
                "decision.identifier.worker-id=1"
            ).applyTo(context);
            context.register(RiskRuntimeConfiguration.class);
            context.refresh();

            assertEquals(1, context.getBeansOfType(Clock.class).size());
            assertEquals(ZoneOffset.UTC, context.getBean(Clock.class).getZone());
            assertEquals(1, context.getBeansOfType(CheckoutPrepareAuthoringDescriptor.class).size());
            assertEquals(1, context.getBeansOfType(DecisionActionRegistry.class).size());
            assertEquals(1, context.getBeansOfType(RuleAstValidator.class).size());
            assertEquals(1, context.getBeansOfType(RiskDecisionCanonicalizer.class).size());
            assertEquals(1, context.getBeansOfType(DecisionReleaseStaticValidator.class).size());
        }
    }
}
