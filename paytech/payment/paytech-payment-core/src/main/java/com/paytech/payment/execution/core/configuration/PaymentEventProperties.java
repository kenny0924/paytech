package com.paytech.payment.execution.core.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** Payment 领域事件信封的发布身份（design/08 §2）；未配置时拒绝启动。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties("payment.event")
public class PaymentEventProperties {
    /** 信封 sourceService：01 §3 的服务名，必须与 messaging.producer.service 一致。 */
    @NotNull
    @Pattern(regexp = "[a-z][a-z0-9-]{0,63}")
    private String sourceService = "payment";

    /** 信封 instanceId：实例清单 instance.id（01 §5），通常取 paytech.instance-id。 */
    @NotNull
    @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}")
    private String instanceId;
}
