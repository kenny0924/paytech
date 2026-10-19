package com.paytech.commercial.api.agreement.response;

import lombok.Getter;
import lombok.Setter;

/** 合同参与方角色及其签署结果。 */
@Getter
@Setter
public class AgreementPartyDetail {
    /** Party 角色行 ID */
    private String agreementPartyId;

    /** 法律 Party ID */
    private String partyId;

    /** Party 资料版本 ID */
    private String partyProfileId;

    /** 角色 */
    private String partyRole;

    /** 签署方式；未签为空 */
    private String acceptanceMethod;

    /** 实际接受的外部 Actor；可空 */
    private String acceptedByActorId;

    /** 接受时刻，UTC ISO 8601；未签为空 */
    private String acceptedAt;

    /** 合同接受事件编号；未签为空 */
    private String acceptanceReference;

    /** 签署证据文件引用 ID；未签为空 */
    private String evidenceDocumentId;
}
