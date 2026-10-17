package com.paytech.compliance.domain;

import lombok.Data;

/** 案件材料要求与其程序模板（t_dd_requirement + t_dd_program_requirement）的组合查询结果。 */
@Data
public class ReviewRequirementView {
    /** 案件材料要求 ID。 */
    private long requirementId;

    /** 案件 ID。 */
    private long caseId;

    /** 要求状态。 */
    private String status;

    /** 并发版本。 */
    private long version;

    /** 材料要求代码。 */
    private String requirementCode;

    /** 材料类型代码。 */
    private String documentType;

    /** 是否基础必需：1 或 0。 */
    private int requiredFlag;
}
