package com.paytech.platform.support.dataquery.service.processor.request;

import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 启用或停用模板的内部请求。 */
@Getter
@AllArgsConstructor
public class ChangeQueryTemplateStatusRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 模板 ID。 */
    @Positive
    private final long templateId;

    /** 调用方读取的模板原版本。 */
    @Positive
    private final int expectedVersion;

    /** 目标状态。 */
    @NotNull
    private final QueryTemplateStatus targetStatus;
}
