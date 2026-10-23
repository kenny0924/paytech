package com.paytech.acquiring.core.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** acquiring 领域事件信封的发布身份（design/08 §2）；未配置时拒绝启动。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties("acquiring.event")
public class AcquiringEventProperties {
    /**
     * 信封 sourceService
     * <li>01 §3 的服务名，必须与 messaging.producer.service 一致；默认 acquiring。</li>
     */
    @NotNull
    @Pattern(regexp = "[a-z][a-z0-9-]{0,63}")
    private String sourceService = "acquiring";

    /**
     * 信封 instanceId
     * <li>实例清单 instance.id（01 §5），通常取 paytech.instance-id；必填，启动时校验。</li>
     */
    @NotNull
    @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}")
    private String instanceId;
}
