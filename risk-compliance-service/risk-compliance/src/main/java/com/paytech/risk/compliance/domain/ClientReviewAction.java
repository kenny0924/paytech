package com.paytech.risk.compliance.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 运营审核结论及其主任务、仍在进行的子任务的结束状态。 */
@Getter
@AllArgsConstructor
public enum ClientReviewAction {
    /** O07 退回修改：主任务和子任务都结束为 RETURNED，客户改完重提生成新任务。 */
    RETURN("RETURNED", "RETURNED", null),

    /** O08 通过：门禁满足后主任务和子任务都结束为 APPROVED，每个任务一条最终决定。 */
    APPROVE("APPROVED", "APPROVED", "APPROVED"),

    /** O09 最终拒绝：主任务 REJECTED 并记录决定；未单独判断的子任务随之撤销。 */
    REJECT("REJECTED", "WITHDRAWN", "REJECTED");

    /** 主任务结束状态。 */
    private final String caseStatus;
    /** 仍在进行的子任务结束状态。 */
    private final String childStatus;
    /** 资格决定结论；退回不形成决定。 */
    private final String decisionOutcome;
}
