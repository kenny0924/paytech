package com.paytech.card.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 孤立卡交易工具回收任务的配置（design/05 §2.2）。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties("card.instrument-purge")
public class CardInstrumentPurgeProperties {
    /** 工具过期后保留的宽限期，默认 1 天。 */
    @NotNull
    private Duration grace = Duration.ofDays(1);

    /** 单次执行最多回收条数，默认 200。 */
    @Min(1) @Max(1000)
    private int batchSize = 200;
}
