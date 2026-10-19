package com.paytech.commercial.service.processor.response;

import com.paytech.commercial.domain.enums.AgreementStatus;

import lombok.Getter;
import lombok.Setter;

/** 合同写入的内部结果。 */
@Getter
@Setter
public class AgreementResponse {
    /** 合同 ID */
    private long agreementId;

    /** 合同编号 */
    private String agreementNo;

    /** 合同当前状态 */
    private AgreementStatus status;

    /** 合同 BCR Root 当前版本 */
    private long changeVersion;
}
