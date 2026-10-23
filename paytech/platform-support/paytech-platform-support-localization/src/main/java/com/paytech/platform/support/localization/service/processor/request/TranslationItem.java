package com.paytech.platform.support.localization.service.processor.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 批量保存中的一条翻译。 */
@Getter
@AllArgsConstructor
public class TranslationItem {

    /** 完整 key。 */
    @NotBlank
    private final String keyCode;

    /** 翻译文案。 */
    @NotBlank
    private final String text;

    /** 调用方读取的翻译版本；0 表示新建。 */
    @Min(0)
    private final int expectedVersion;
}
