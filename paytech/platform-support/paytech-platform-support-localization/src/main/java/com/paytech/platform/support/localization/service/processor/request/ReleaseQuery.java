package com.paytech.platform.support.localization.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 单个发布的详情或差异查询请求；详情不使用基准发布号。 */
@Getter
@AllArgsConstructor
public class ReleaseQuery {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 应用编码。 */
    @NotBlank
    private final String applicationCode;

    /** 发布号。 */
    @Positive
    private final int releaseNo;

    /** 基准发布号；为 null 时差异取上一发布，详情不使用。 */
    private final Integer baseReleaseNo;
}
