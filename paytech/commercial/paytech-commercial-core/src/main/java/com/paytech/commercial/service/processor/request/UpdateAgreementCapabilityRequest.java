package com.paytech.commercial.service.processor.request;

import com.paytech.commercial.api.enums.AgreementCapabilityStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/** 整体改写开通项的内部请求。 */
@Getter
@Setter
public class UpdateAgreementCapabilityRequest extends AgreementCapabilityTermsRequest {
    /** 操作者 Account ID */
    @NotNull
    @Positive
    private Long actorId;

    /** 合同 ID */
    @NotNull
    @Positive
    private Long agreementId;

    /** 开通项 ID */
    @NotNull
    @Positive
    private Long agreementCapabilityId;

    /** 调用方期望的合同 BCR Root 版本 */
    @NotNull
    @Positive
    private Long expectedChangeVersion;

    /** 目标状态 */
    @NotNull
    private AgreementCapabilityStatus status;
}
