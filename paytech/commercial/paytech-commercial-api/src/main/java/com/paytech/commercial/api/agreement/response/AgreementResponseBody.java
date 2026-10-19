package com.paytech.commercial.api.agreement.response;

import lombok.Getter;
import lombok.Setter;

/** 合同写入结果。 */
@Getter
@Setter
public class AgreementResponseBody {
    /** 合同 ID */
    private String agreementId;

    /** 合同编号 */
    private String agreementNo;

    /**
     * 合同当前状态
     * <li>DRAFT、PENDING_SIGNATURE、SIGNED、ACTIVE、EXPIRED、TERMINATED、CANCELLED。</li>
     */
    private String status;

    /** 合同 BCR Root 当前版本，下一次写入的 expectedChangeVersion */
    private Long changeVersion;
}
