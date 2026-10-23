package com.paytech.platform.support.api.localization.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 批量保存一个语言的翻译：全部成功或全部失败。 */
@Getter
@Setter
public class SaveTranslationsRequestBody {

    /** 目标语言代码；必须是 ENABLED 且不是 en。 */
    @NotBlank
    @Size(max = 35)
    private String locale;

    /**
     * 翻译
     * <li>1 至 200 条，同一 key 不能重复。ICU 校验失败时返回 422，errors 的 field 为 translations[i].text。</li>
     */
    @NotEmpty
    @Size(max = 200)
    @Valid
    private List<TranslationInput> translations;
}
