package com.paytech.compliance.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 运营审核结论及其主案件、仍在进行的子案件的结束状态。 */
@Getter
@AllArgsConstructor
public enum ClientReviewAction {
    /** O07 退回修改：主案件和子案件都结束为 RETURNED（释放进行中占位）；客户重提时新建案件并串联上一轮（S-01）。 */
    RETURN("RETURNED", "RETURNED", null),

    /** O08 批准：门禁满足后主案件和子案件都结束为 APPROVED，每个案件一条最终决定。 */
    APPROVE("APPROVED", "APPROVED", "APPROVED"),

    /** O09 最终拒绝：主案件 REJECTED 并记录决定；未单独判断的子案件随之撤回。 */
    REJECT("REJECTED", "WITHDRAWN", "REJECTED");

    /** 主案件结束状态。 */
    private final String caseStatus;
    /** 仍在进行的子案件结束状态。 */
    private final String childStatus;
    /** 资格决定结论；退回不形成决定。 */
    private final String decisionOutcome;
}
