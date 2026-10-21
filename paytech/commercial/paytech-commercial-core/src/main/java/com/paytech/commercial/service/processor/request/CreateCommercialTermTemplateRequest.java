package com.paytech.commercial.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 创建标准价目模板方案的内部请求；(termType, planCode) 是业务唯一身份。 */
@Getter
@AllArgsConstructor
public class CreateCommercialTermTemplateRequest {
    /** 方案编号 */
    @NotBlank
    private final String planCode;

    /** 模板显示名 */
    @NotBlank
    private final String templateName;

    /** 条款类型 */
    @NotBlank
    private final String termType;

    /** 模板适用的准确 OE */
    @NotBlank
    private final String entityNo;

    /** 操作人账号 ID */
    @Positive
    private final long operatorAccountId;
}
