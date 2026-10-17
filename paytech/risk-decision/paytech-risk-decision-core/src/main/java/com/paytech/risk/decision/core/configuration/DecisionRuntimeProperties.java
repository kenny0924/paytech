package com.paytech.risk.decision.core.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import com.paytech.risk.decision.domain.enums.DecisionEnvironment;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 决策运行期配置。 */
@Data
@Validated
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "decision.runtime")
public class DecisionRuntimeProperties {
    /**
     * 本实例所属的发布环境
     * <li>TEST 或 PRODUCTION；评估读取该环境的生效发布，执行发布只允许在 TEST 激活。</li>
     * <li>必填，缺失时启动失败；启动后不刷新。</li>
     */
    @NotNull
    private DecisionEnvironment environment;
}
