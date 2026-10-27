package com.paytech.compliance.api.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 一项材料要求。 */
@Getter
@Setter
public class ClientReviewRequirementResponseBody {

    /** 案件材料要求 ID。 */
    private String requirementId;

    /** 要求代码。 */
    private String requirementCode;

    /** 允许的材料类型代码。 */
    private List<String> documentTypes;

    /** 是否必需。 */
    private Boolean required;

    /** 是否可豁免。 */
    private Boolean waivable;

    /** 材料最长时效天数；不限为空。 */
    private Integer maxAgeDays;

    /** 要求状态：PENDING、RECEIVED、ACCEPTED、REJECTED、WAIVED、EXPIRED。 */
    private String status;

    /** 豁免人；未豁免为空。 */
    private String waivedBy;

    /** 豁免原因码；未豁免为空。 */
    private String waivedReasonCode;

    /** 豁免时间；未豁免为空。 */
    private String waivedAt;

    /** 从上一轮案件复用时的来源案件 ID；否则为空。 */
    private String reusedFromCaseId;

    /** 证据。 */
    private List<ClientReviewEvidenceResponseBody> evidence;
}
