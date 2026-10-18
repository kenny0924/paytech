package com.paytech.consumer.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** consumer 实例的 Snowflake 节点（独立于 acquiring，由实例清单分配；design/01 §3）。 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "consumer.identifier")
public class ConsumerIdentifierProperties {
    /** Snowflake 区域，0～31。 */
    @NotNull
    @Min(0)
    @Max(31)
    private Integer regionId;

    /** 同一区域内每实例唯一的 Snowflake 节点，0～31。 */
    @NotNull
    @Min(0)
    @Max(31)
    private Integer workerId;
}
