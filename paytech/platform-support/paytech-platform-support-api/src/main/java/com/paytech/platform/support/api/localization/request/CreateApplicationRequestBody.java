package com.paytech.platform.support.api.localization.request;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 登记一个语言包消费方应用；新建为 ENABLED，版本为 1，尚无发布。 */
@Getter
@Setter
public class CreateApplicationRequestBody {

    /**
     * 应用编码
     * <li>小写字母开头的小写字母、数字与连字符，2 至 64 字符，如 client-console。创建后不可修改。</li>
     */
    @NotBlank
    @Pattern(regexp = "[a-z][a-z0-9-]{1,63}", message = "applicationCode 只能使用小写字母、数字与连字符，且以字母开头")
    private String applicationCode;

    /** 应用名称，最长 128 字符。 */
    @NotBlank
    @Size(max = 128)
    private String applicationName;

    /** 默认语言，必须是 ENABLED 的语言代码。 */
    @NotBlank
    @Size(max = 35)
    private String defaultLocale;

    /**
     * 必须覆盖的语言
     * <li>发布时这些语言的翻译覆盖率须达到阈值；必须是 ENABLED 的语言，不能重复，最多 20 个；可为空数组。en 恒为 100%。</li>
     */
    @NotNull
    @Size(max = 20)
    private List<@NotBlank @Size(max = 35) String> requiredLocales;

    /** 覆盖率阈值百分比，0 至 100；默认要求 100。 */
    @NotNull
    @Min(0)
    @Max(100)
    private Integer coverageThresholdPercent;
}
