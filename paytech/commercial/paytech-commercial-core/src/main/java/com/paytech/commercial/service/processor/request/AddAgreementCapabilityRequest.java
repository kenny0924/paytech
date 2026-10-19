package com.paytech.commercial.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/** 新增开通项的内部请求。 */
@Getter
@Setter
public class AddAgreementCapabilityRequest extends AgreementCapabilityTermsRequest {
    /** 操作者 Account ID */
    @NotNull
    @Positive
    private Long actorId;

    /** 合同 ID */
    @NotNull
    @Positive
    private Long agreementId;

    /** 调用方期望的合同 BCR Root 版本 */
    @NotNull
    @Positive
    private Long expectedChangeVersion;

    /** 准确 OE */
    @NotBlank
    private String entityNo;

    /** 可开通项代码 */
    @NotBlank
    private String capabilityCode;
}
