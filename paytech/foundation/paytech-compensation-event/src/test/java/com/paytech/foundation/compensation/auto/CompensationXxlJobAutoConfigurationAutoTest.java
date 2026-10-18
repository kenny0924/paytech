package com.paytech.foundation.compensation.auto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.compensation.api.CompensationEventScanner;
import com.paytech.foundation.compensation.api.CompensationScanResult;
import com.paytech.foundation.compensation.core.autoconfigure.CompensationXxlJobAutoConfiguration;
import com.paytech.foundation.compensation.ext.job.CompensationEventXxlJobHandler;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;

class CompensationXxlJobAutoConfigurationAutoTest {

    private final ApplicationContextRunner scannerContextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CompensationXxlJobAutoConfiguration.class))
        .withBean(CompensationEventScanner.class, () -> request -> new CompensationScanResult(0, 0, 0));

    private final ApplicationContextRunner contextRunner = scannerContextRunner.withBean(XxlJobSpringExecutor.class,
        XxlJobSpringExecutor::new, beanDefinition -> beanDefinition.setLazyInit(true));

    @Test
    void remainsDisabledByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(CompensationEventXxlJobHandler.class));
    }

    @Test
    void doesNotCreateHandlerWithoutXxlJobExecutor() {
        scannerContextRunner.withPropertyValues("xxl.job.executor.enabled=true")
            .run(context -> assertThat(context).doesNotHaveBean(CompensationEventXxlJobHandler.class));
    }

    @Test
    void createsCompensationOwnedHandlerWhenXxlJobExecutorIsEnabled() {
        contextRunner.withPropertyValues("xxl.job.executor.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(CompensationEventXxlJobHandler.class);
            assertThat(context).hasBean(CompensationXxlJobAutoConfiguration.HANDLER_BEAN);
        });
    }

    @Test
    void rejectsConfigurationThatExcludesCompensationHandlerPackage() {
        contextRunner.withPropertyValues("xxl.job.executor.enabled=true",
            "xxl.job.executor.excluded-packages=com.paytech.foundation.compensation.").run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(FailException.class);
            });
    }
}
