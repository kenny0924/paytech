package com.paytech.commercial.service.processor.request;

import com.paytech.commercial.api.enums.FeeTaxTreatment;
import com.paytech.commercial.api.enums.PricingModel;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 创建 DRAFT 版本的内部请求。 */
@Getter
@AllArgsConstructor
public class CreateCommercialTermVersionRequest {
    /** 方案 ID */
    @Positive
    private final long planId;

    /** 方案根期望版本 */
    @Positive
    private final long expectedChangeVersion;

    /** 操作人账号 ID */
    @Positive
    private final long operatorAccountId;

    /** 复制来源版本 ID，可空 */
    private final Long copyFromVersionId;

    /** 版本冻结的合同 ID，可空（Partner 条款必填） */
    private final Long agreementId;

    /** 定价模型，可空（复制时沿用来源） */
    private final PricingModel pricingModel;

    /** 费用含税口径，可空（复制时沿用来源） */
    private final FeeTaxTreatment feeTaxTreatment;

    /** 基于模板生成的来源模板版本 ID，可空；与 copyFromVersionId 互斥 */
    private final Long sourceTemplateVersionId;
}
