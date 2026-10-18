package com.paytech.card.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** card-service 的 Snowflake 坐标，未配置时拒绝启动；按实例清单分配，不得与其他实例冲突。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties("card.identifier")
public class CardIdentifierProperties {
    /** 部署分配的区域，范围 0..31。 */
    @NotNull @Min(0) @Max(31)
    private Long regionId;

    /** 部署分配的工作节点，范围 0..31。 */
    @NotNull @Min(0) @Max(31)
    private Long workerId;
}
