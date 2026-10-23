package com.paytech.platform.support.localization.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 切换当前发布的内部请求。 */
@Getter
@AllArgsConstructor
public class ActivateReleaseCommand {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 应用编码。 */
    @NotBlank
    private final String applicationCode;

    /** 目标发布号。 */
    @Positive
    private final int releaseNo;

    /** 切换原因。 */
    @NotBlank
    @Size(max = 256)
    private final String reason;
}
