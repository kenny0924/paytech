package com.paytech.platform.support.localization.service.processor.request;

import com.paytech.platform.support.localization.core.enums.LocaleStatus;
import com.paytech.platform.support.localization.core.enums.TextDirection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新增或修改语言的内部请求；新增时 expectedVersion 与 status 为 null。 */
@Getter
@AllArgsConstructor
public class LocaleCommand {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 语言代码。 */
    @NotBlank
    @Size(max = 35)
    private final String localeCode;

    /** 调用方读取的原版本；新增时为 null。 */
    private final Integer expectedVersion;

    /** 本语言的自称。 */
    @NotBlank
    @Size(max = 64)
    private final String nativeName;

    /** 英文名称。 */
    @NotBlank
    @Size(max = 64)
    private final String englishName;

    /** 文字方向。 */
    @NotNull
    private final TextDirection textDirection;

    /** 目标状态；新增时为 null（固定 ENABLED）。 */
    private final LocaleStatus status;

    /** 排序值。 */
    private final int sortOrder;
}
