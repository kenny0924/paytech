package com.paytech.platform.support.dataquery.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 模板详情的内部请求。 */
@Getter
@AllArgsConstructor
public class GetQueryTemplateRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 模板 ID。 */
    @Positive
    private final long templateId;
}
