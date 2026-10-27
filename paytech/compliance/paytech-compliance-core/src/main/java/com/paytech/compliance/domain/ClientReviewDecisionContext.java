package com.paytech.compliance.domain;

import java.time.Instant;
import java.util.List;

import com.paytech.compliance.core.dal.dataobject.CaseStateChange;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.core.dal.dataobject.QualificationDecision;
import com.paytech.compliance.core.dal.dataobject.ReturnError;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次审核结论：要结束的全部案件、结束字段、最终决定、退回错误、状态变更与 Client 资格投影。 */
@Getter
@AllArgsConstructor
public class ClientReviewDecisionContext {
    /** 主案件，按读取时版本条件结束。 */
    private final DueDiligenceCase mainCase;
    /** 要一起结束的进行中子案件。 */
    private final List<DueDiligenceCase> childCases;
    /** 审核结论。 */
    private final ClientReviewAction action;
    /** 主案件结束字段。 */
    private final CaseClosing closing;
    /** 子案件结束字段（不含客户可见说明与退回错误）。 */
    private final CaseClosing childClosing;
    /** 最终决定；退回为空列表。 */
    private final List<QualificationDecision> decisions;
    /** 退回的需求级错误；非退回为空列表。 */
    private final List<ReturnError> returnErrors;
    /** 每个案件一条状态变更，聚合版本为结束后的版本。 */
    private final List<CaseStateChange> stateChanges;
    /**
     * 主案件结论对 Client 角色资格的投影
     * <li>批准时新建或切换为 APPROVED；拒绝且已有 APPROVED 资格时切换为 REJECTED；其他情况为空。</li>
     */
    private final QualificationChangeContext qualificationChange;
    /** 结束时间。 */
    private final Instant now;
}
