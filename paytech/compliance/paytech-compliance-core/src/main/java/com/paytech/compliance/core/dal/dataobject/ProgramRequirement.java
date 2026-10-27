package com.paytech.compliance.core.dal.dataobject;

import java.util.List;

import lombok.Data;

/** 程序材料槽（t_dd_program_requirement）及其允许类型（t_dd_program_requirement_doc_type）。 */
@Data
public class ProgramRequirement {
    /** 材料模板 ID。 */
    private long programRequirementId;
    /** 程序版本 ID。 */
    private long programVersionId;
    /** 材料要求代码。 */
    private String requirementCode;
    /** 归属主体角色。 */
    private String subjectRole;
    /** 是否必需：1 或 0。 */
    private int requiredFlag;
    /** 最长时效天数；不限为空。 */
    private Integer maxAgeDays;
    /** 是否可豁免：1 或 0。 */
    private int waivableFlag;
    /** 允许的材料类型，按代码排序。 */
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
