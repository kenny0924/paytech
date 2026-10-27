package com.paytech.compliance.api.event;

import lombok.Getter;
import lombok.Setter;

/** ClientReviewDecided v2 中的一项退回需求级错误。 */
@Getter
@Setter
public class ClientReviewDecidedErrorPayload {

    /** 需求键。 */
    private String requirementKey;

    /** 需求所属主体 Party ID；与主体无关为空。 */
    private String subjectPartyId;

    /** 错误码。 */
    private String code;

    /** 给 Client 的说明；可空。 */
    private String reason;

    /** 补救动作码。 */
    private String remediation;
}
