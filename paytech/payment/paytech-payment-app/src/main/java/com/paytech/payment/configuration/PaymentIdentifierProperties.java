package com.paytech.payment.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Payment 已有 Snowflake 配置的强类型绑定，未配置时拒绝启动。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties("payment.identifier")
public class PaymentIdentifierProperties {
    /** 部署分配的区域，范围 0..31。 */
    @NotNull @Min(0) @Max(31)
    private Long regionId;

    /** 部署分配的工作节点，范围 0..31，不同实例不得冲突。 */
    @NotNull @Min(0) @Max(31)
    private Long workerId;
}
