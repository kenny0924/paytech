package com.paytech.foundation.compensation.core.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.compensation.api.CompensationEventScanner;
import com.paytech.foundation.compensation.core.error.CompensationErrorCode;
import com.paytech.foundation.compensation.ext.job.CompensationEventXxlJobHandler;
import com.paytech.foundation.xxl.core.autoconfigure.XxlJobAutoConfiguration;
import com.paytech.foundation.xxl.core.autoconfigure.XxlJobProperties;
import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import com.xxl.job.core.handler.annotation.XxlJob;

/**
 * 使用 XXL-Job 唤醒服务本地补偿扫描的自动配置。
 *
 * @author Codex
 */
@AutoConfiguration
@AutoConfigureAfter({CompensationEventAutoConfiguration.class, XxlJobAutoConfiguration.class})
@ConditionalOnClass({XxlJob.class, XxlJobSpringExecutor.class})
@ConditionalOnBean({CompensationEventScanner.class, XxlJobSpringExecutor.class})
@ConditionalOnProperty(prefix = "xxl.job.executor", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({CompensationEventProperties.class, XxlJobProperties.class})
public class CompensationXxlJobAutoConfiguration {

    public static final String HANDLER_BEAN = "compensationEventXxlJobHandler";

    @Bean(name = HANDLER_BEAN)
    CompensationEventXxlJobHandler compensationEventXxlJobHandler(CompensationEventScanner scanner,
        CompensationEventProperties properties, XxlJobProperties xxlJobProperties) {
        validateHandlerDiscovery(xxlJobProperties);
        return new CompensationEventXxlJobHandler(scanner, properties.getScannerBatchSize());
    }

    private void validateHandlerDiscovery(XxlJobProperties properties) {
        String handlerClassName = CompensationEventXxlJobHandler.class.getName();
        String excludedPackages = properties.getExecutor().getExcludedPackages();
        if (excludedPackages == null || excludedPackages.isBlank()) {
            throw new FailException(CompensationErrorCode.CONFIGURATION_INVALID,
                "xxl.job.executor.excluded-packages不能为空");
        }
        for (String excludedPackage : excludedPackages.split(",", -1)) {
            String normalizedPackage = excludedPackage.trim();
            if (normalizedPackage.isEmpty()) {
                throw new FailException(CompensationErrorCode.CONFIGURATION_INVALID,
                    "xxl.job.executor.excluded-packages不能包含空条目");
            }
            if (handlerClassName.startsWith(normalizedPackage)) {
                throw new FailException(CompensationErrorCode.CONFIGURATION_INVALID,
                    "xxl.job.executor.excluded-packages不能排除补偿扫描Handler: " + normalizedPackage);
            }
        }
    }
}
