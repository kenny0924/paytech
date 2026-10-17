package com.paytech.risk.compliance.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.risk.compliance.core.dal.dataobject.CaseStateChange;
import com.paytech.risk.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.risk.compliance.core.dal.dataobject.QualificationDecision;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次审核结论：要结束的全部任务、最终决定、状态变更与主任务说明。 */
@Getter
@AllArgsConstructor
public class ClientReviewDecisionContext {
    /** 主任务，按读取时版本条件结束。 */
    private final DueDiligenceCase mainCase;
    /** 要一起结束的进行中子任务。 */
    private final List<DueDiligenceCase> childCases;
    /** 审核结论。 */
    private final ClientReviewAction action;
    /** 客户可见说明。 */
    private final String publicNote;
    /** 内部审核理由。 */
    private final String resolutionNote;
    /** 退回分区；非退回为空。 */
    private final String returnSection;
    /** 最终决定；退回为空列表。 */
    private final List<QualificationDecision> decisions;
    /** 每个任务一条状态变更，聚合版本为结束后的版本。 */
    private final List<CaseStateChange> stateChanges;
    /** 结束时间。 */
    private final Instant now;
}
