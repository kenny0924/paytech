package com.paytech.platform.support.localization.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 发布的内部请求。 */
@Getter
@AllArgsConstructor
public class PublishReleaseCommand {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 应用编码。 */
    @NotBlank
    private final String applicationCode;

    /** 发布说明，可为 null。 */
    @Size(max = 512)
    private final String releaseNote;
}
