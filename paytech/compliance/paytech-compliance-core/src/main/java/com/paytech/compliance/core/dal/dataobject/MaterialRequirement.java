package com.paytech.risk.compliance.core.dal.dataobject;

import lombok.Data;

/** 程序修订下的一条材料要求，来自 t_dd_program_requirement。 */
@Data
public class MaterialRequirement {
    /** 程序材料模板 ID。 */
    private long programRequirementId;

    /** 稳定材料要求代码。 */
    private String requirementCode;

    /** 受控材料类型代码。 */
    private String documentType;

    /**
     * 必需标记
     * <li>只接受 0 或 1。</li>
     */
    private int requiredFlag;

    /** 判断代码、类型和必需标记是否符合目录合同。 */
    public boolean isWellFormed() {
        return requirementCode != null && requirementCode.matches("[A-Z][A-Z0-9_]{0,63}") && documentType != null
            && documentType.matches("[A-Z][A-Z0-9_]{0,63}") && (requiredFlag == 0 || requiredFlag == 1);
    }

    /** 是否为必需材料。 */
    public boolean isRequired() {
        return requiredFlag == 1;
    }
}
