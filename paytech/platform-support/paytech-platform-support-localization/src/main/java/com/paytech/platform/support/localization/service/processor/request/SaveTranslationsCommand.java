package com.paytech.platform.support.localization.service.processor.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 批量保存翻译的内部请求。 */
@Getter
@AllArgsConstructor
public class SaveTranslationsCommand {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 应用编码。 */
    @NotBlank
    private final String applicationCode;

    /** 目标语言。 */
    @NotBlank
    private final String locale;

    /** 翻译，1 至 200 条，按请求顺序。 */
    @NotEmpty
    @Size(max = 200)
    @Valid
    private final List<TranslationItem> translations;
}
