package com.paytech.compliance.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一项退回需求级错误的跨模块结果（不暴露 DataObject）。 */
@Getter
@AllArgsConstructor
public class RequirementError {
    /** 需求键。 */
    private final String requirementKey;
    /** 主体 Party ID；与主体无关为空。 */
    private final Long subjectPartyId;
    /** 错误码。 */
    private final String code;
    /** 给 Client 的说明。 */
    private final String reason;
    /** 补救动作码。 */
    private final String remediation;
}
