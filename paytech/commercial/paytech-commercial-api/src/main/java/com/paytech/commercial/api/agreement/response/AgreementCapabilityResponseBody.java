package com.paytech.commercial.api.agreement.response;

import lombok.Getter;
import lombok.Setter;

/** 开通项写入结果。 */
@Getter
@Setter
public class AgreementCapabilityResponseBody {
    /** 合同 ID */
    private String agreementId;

    /** 开通项 ID */
    private String agreementCapabilityId;

    /** 开通项状态：ENABLED、SUSPENDED、DISABLED */
    private String status;

    /** 合同 BCR Root 当前版本 */
    private Long changeVersion;
}
