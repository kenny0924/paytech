package com.paytech.client.contract.api.onboarding.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 审核任务的分类集合。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassificationSetResponseBody {
    /** Risk 主审核任务 ID。 */
    private String caseId;
    /** CCT 申请 ID。 */
    private String applicationId;
    /** 是否已确认；确认后不可修改。 */
    private boolean confirmed;
    /** 确认人 Account ID；未确认为空。 */
    private String confirmedBy;
    /** 确认时间，UTC ISO-8601；未确认为空。 */
    private String confirmedAt;
    /** 分类；尚未设置为空集合。 */
    private List<ClassificationItemResponseBody> items;
}
