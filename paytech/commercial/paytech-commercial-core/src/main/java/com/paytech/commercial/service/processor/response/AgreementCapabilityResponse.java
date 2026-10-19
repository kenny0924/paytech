package com.paytech.commercial.service.processor.response;

import com.paytech.commercial.api.enums.AgreementCapabilityStatus;

import lombok.Getter;
import lombok.Setter;

/** 开通项写入的内部结果。 */
@Getter
@Setter
public class AgreementCapabilityResponse {
    /** 合同 ID */
    private long agreementId;

    /** 开通项 ID */
    private long agreementCapabilityId;

    /** 开通项状态 */
    private AgreementCapabilityStatus status;

    /** 合同 BCR Root 当前版本 */
    private long changeVersion;
}
