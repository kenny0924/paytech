package com.paytech.compliance.api.response;

import lombok.Getter;
import lombok.Setter;

/** 退回时登记的一项需求级错误；Client 门户据此定位字段或材料并给出补救动作。 */
@Getter
@Setter
public class ClientReviewRequirementErrorBody {

    /** 需求键，例如 document.REGISTRATION、profile.organization.legal_name、relation.ubo.min_one。 */
    private String requirementKey;

    /** 需求所属主体 Party ID；与主体无关为空。 */
    private String subjectPartyId;

    /** 错误码，例如 DOCUMENT_EXPIRED、FIELD_FORMAT_INVALID。 */
    private String code;

    /** 审核员给 Client 的说明，可空。 */
    private String reason;

    /** 补救动作码，例如 UPLOAD_DOCUMENT、PROVIDE_FIELD。 */
    private String remediation;

    /** 补件期限，UTC ISO-8601；未设期限为空。 */
    private String dueTime;
}
