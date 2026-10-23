package com.paytech.platform.support.api.localization.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 批量保存中的一条翻译。 */
@Getter
@Setter
public class TranslationInput {

    /** 完整 key，如 team.invite.submit；必须是该应用 ACTIVE 的 key。 */
    @NotBlank
    @Size(max = 191)
    private String keyCode;

    /**
     * 翻译文案
     * <li>ICU MessageFormat；不能为空白，最长 8000 字符。参数名集合须与英文基准一致，plural/select/selectordinal 须有 other。</li>
     */
    @NotBlank
    @Size(max = 8000)
    private String text;

    /**
     * 调用方读取的翻译版本
     * <li>0 表示该语言尚无翻译、本次新建；否则为已有翻译的版本。</li>
     */
    @NotNull
    @Min(0)
    private Integer expectedVersion;
}
