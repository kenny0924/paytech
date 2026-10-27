package com.paytech.client.master.core.dal.dataobject;

import com.paytech.client.master.domain.enums.OnboardingCodeStatus;
import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** PayFac 入驻码（t_partner_onboarding_code）。 */
@Getter
@Setter
public class PartnerOnboardingCode {
    /** 入驻码 ID。 */
    private Long codeId;
    /** 随机入驻码 XXXX-XXXX。 */
    private String code;
    /** 签发 Local Partner ID。 */
    private Long localPartnerId;
    /** 状态。 */
    private OnboardingCodeStatus status;
    /** 生效 UTC 时间。 */
    private Instant effectiveFrom;
    /** 失效 UTC 时间，可空。 */
    private Instant effectiveTo;
    /** 最大使用次数，可空。 */
    private Integer maxUses;
    /** 已使用次数。 */
    private Integer useCount;
    /** 签发说明，可空。 */
    private String note;
    /** 签发人。 */
    private Long issuedBy;
    /** 作废人，可空。 */
    private Long revokedBy;
    /** 作废 UTC 时间，可空。 */
    private Instant revokedTime;
    /** 创建 UTC 时间。 */
    private Instant createTime;
    /** 更新 UTC 时间。 */
    private Instant updateTime;

    /** 含受保护值的引用，不输出到日志。 */
    @Override
    public String toString() {
        return "PartnerOnboardingCode[redacted]";
    }
}
