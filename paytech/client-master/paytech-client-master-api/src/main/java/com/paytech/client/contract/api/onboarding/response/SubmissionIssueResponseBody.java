package com.paytech.client.contract.api.onboarding.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 一项提交问题。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionIssueResponseBody {
    /**
     * 问题定位
     * <li>主主体为表单键，如 individual.givenName；关联人员为 people[partyId].表单键。</li>
     * <li>缺少基础必需材料为 materials[partyId].requirementCode；具体材料为 documents[documentId]；组织缺少人员为 people。</li>
     */
    private String field;
    /** 可直接展示的中文说明，不含敏感值。 */
    private String message;
}
