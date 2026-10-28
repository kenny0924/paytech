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

    /** 合同成员资格：ACTIVE、DISABLED */
    private String status;

    /**
     * 执行状态：PENDING_PREREQUISITES、ENABLED、SUSPENDED_BY_PREREQUISITE、SUSPENDED_MANUALLY
     * <li>成员资格 DISABLED 时为空。</li>
     */
    private String executionStatus;

    /** 合同 BCR Root 当前版本 */
    private Long changeVersion;
}
