package com.paytech.platform.support.localization.service.processor.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新增或修改应用的内部请求；新增时 expectedVersion 为 null。 */
@Getter
@AllArgsConstructor
public class ApplicationCommand {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 应用编码。 */
    @NotBlank
    @Size(max = 64)
    private final String applicationCode;

    /** 调用方读取的原版本；新增时为 null。 */
    private final Integer expectedVersion;

    /** 应用名称。 */
    @NotBlank
    @Size(max = 128)
    private final String applicationName;

    /** 默认语言。 */
    @NotBlank
    private final String defaultLocale;

    /** 必须覆盖语言，已去重检查前的原始顺序。 */
    @NotNull
    private final List<String> requiredLocales;

    /** 覆盖率阈值百分比。 */
    private final int coverageThresholdPercent;
}
