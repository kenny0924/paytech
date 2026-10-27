package com.paytech.client.console.bff.service.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 退回时的一项需求级错误；页面据此定位字段、人员、材料或声明。 */
@Getter
@AllArgsConstructor
public class ReviewRequirementError {

    /** 需求键，如 document.BANK、organization.legalName */
    private final String requirementKey;

    /** 需求所属主体 Party ID；与主体无关为空 */
    private final String subjectPartyId;

    /** 错误码，如 DOCUMENT_TOO_OLD */
    private final String code;

    /** 审核员给 Client 的说明，可空 */
    private final String reason;

    /** 补救动作码，如 UPLOAD_DOCUMENT */
    private final String remediation;

    /** 补件期限，UTC；未设期限为空 */
    private final String dueTime;
}
