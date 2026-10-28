package com.paytech.commercial.service.processor.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/** 运营恢复手动暂停开通项的内部请求。 */
@Getter
@Setter
public class ResumeAgreementCapabilityRequest {
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
}
