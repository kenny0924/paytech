package com.paytech.compliance.domain;

import com.paytech.compliance.core.dal.dataobject.EvidenceRef;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次材料核验：证据、结论、拒绝原因与所属要求的新状态。 */
@Getter
@AllArgsConstructor
public class EvidenceReviewContext {
    /** 命令公共事实。 */
    private final ReviewCommandContext command;
    /** 被核验的证据，读取时版本。 */
    private final EvidenceRef evidence;
    /** ACCEPTED 或 REJECTED。 */
    private final String outcome;
    /** 拒绝原因码；接受为空。 */
    private final String rejectReasonCode;
    /** 内部备注。 */
    private final String note;
    /** 所属材料要求。 */
    private final ReviewRequirementView requirement;
    /** 要求的新状态；不变为空。 */
    private final String requirementStatus;
}
