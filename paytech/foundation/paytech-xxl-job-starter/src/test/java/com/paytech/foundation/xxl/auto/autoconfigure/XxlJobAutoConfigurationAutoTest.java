package com.paytech.foundation.xxl.auto.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.paytech.foundation.xxl.core.autoconfigure.XxlJobAutoConfiguration;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;

class XxlJobAutoConfigurationAutoTest {

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(XxlJobAutoConfiguration.class));

    @Test
    void remainsDisabledByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(XxlJobSpringExecutor.class));
    }

    @Test
    void failsBeforeStartingExecutorWhenEnabledConfigurationIsIncomplete() {
        contextRunner.withPropertyValues("xxl.job.executor.enabled=true")
            .run(context -> assertThat(context).hasFailed());
    }
}
