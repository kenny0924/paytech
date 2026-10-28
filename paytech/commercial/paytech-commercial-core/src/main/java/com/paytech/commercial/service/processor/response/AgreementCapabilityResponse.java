package com.paytech.commercial.service.processor.response;

import com.paytech.commercial.api.enums.AgreementCapabilityExecutionStatus;
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

    /** 合同成员资格 */
    private AgreementCapabilityStatus status;

    /** 执行状态；成员资格 DISABLED 时为空 */
    private AgreementCapabilityExecutionStatus executionStatus;

    /** 合同 BCR Root 当前版本 */
    private long changeVersion;
}
