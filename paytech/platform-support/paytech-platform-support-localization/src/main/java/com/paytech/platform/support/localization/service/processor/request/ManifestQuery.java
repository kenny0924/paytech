package com.paytech.platform.support.localization.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 读取 manifest 的内部请求；不带操作人身份。 */
@Getter
@AllArgsConstructor
public class ManifestQuery {

    /** 应用编码。 */
    @NotBlank
    private final String applicationCode;

    /** 目标语言；为 null 时只返回 en 的语言包。 */
    private final String locale;

    /** 指定发布号；为 null 时读当前发布。 */
    private final Integer releaseNo;
}
