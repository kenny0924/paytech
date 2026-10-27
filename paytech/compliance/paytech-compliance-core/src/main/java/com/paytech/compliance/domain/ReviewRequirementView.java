package com.paytech.compliance.domain;

import java.time.Instant;
import java.util.List;

import lombok.Data;

/** 案件材料要求与其程序模板（t_dd_requirement + t_dd_program_requirement）的组合查询结果；允许类型由加载器补齐。 */
@Data
public class ReviewRequirementView {
    /** 案件材料要求 ID。 */
    private long requirementId;
    /** 案件 ID。 */
    private long caseId;
    /** 程序材料模板 ID。 */
    private long programRequirementId;
    /** 要求状态。 */
    private String status;
    /** 并发版本。 */
    private long version;
    /** 材料要求代码。 */
    private String requirementCode;
    /** 是否必需：1 或 0。 */
    private int requiredFlag;
    /** 最长时效天数；不限为空。 */
    private Integer maxAgeDays;
    /** 是否可豁免：1 或 0。 */
    private int waivableFlag;
    /** 豁免人。 */
    private Long waivedBy;
    /** 豁免原因码。 */
    private String waivedReasonCode;
    /** 豁免时间。 */
    private Instant waivedTime;
    /** 复用来源案件 ID。 */
    private Long reusedFromCaseId;
    /** 允许的材料类型。 */
    private List<String> documentTypes;

    /** 是否必需。 */
    public boolean isRequired() {
        return requiredFlag == 1;
    }

    /** 是否可豁免。 */
    public boolean isWaivable() {
        return waivableFlag == 1;
    }
}
