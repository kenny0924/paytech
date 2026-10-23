package com.paytech.platform.support.api.localization.request;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 修改应用名称、默认语言、必须覆盖语言与阈值；全量提交。 */
@Getter
@Setter
public class UpdateApplicationRequestBody {

    /** 调用方读取的原版本。 */
    @NotNull
    @Min(1)
    private Integer expectedVersion;

    /** 应用名称，最长 128 字符。 */
    @NotBlank
    @Size(max = 128)
    private String applicationName;

    /** 默认语言，必须是 ENABLED 的语言代码。 */
    @NotBlank
    @Size(max = 35)
    private String defaultLocale;

    /** 必须覆盖的语言；规则同新建。 */
    @NotNull
    @Size(max = 20)
    private List<@NotBlank @Size(max = 35) String> requiredLocales;

    /** 覆盖率阈值百分比，0 至 100。 */
    @NotNull
    @Min(0)
    @Max(100)
    private Integer coverageThresholdPercent;
}
