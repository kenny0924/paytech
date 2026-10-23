package com.paytech.platform.support.localization.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 导入翻译文件的内部请求。 */
@Getter
@AllArgsConstructor
public class ImportTranslationsCommand {

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

    /** 已上传文件 ID。 */
    @Positive
    private final long fileId;
}
