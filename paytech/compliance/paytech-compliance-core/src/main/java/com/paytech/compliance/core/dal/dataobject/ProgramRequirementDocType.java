package com.paytech.compliance.core.dal.dataobject;

import lombok.Data;

/** 材料槽允许的一种材料类型（t_dd_program_requirement_doc_type）。 */
@Data
public class ProgramRequirementDocType {
    /** 程序材料模板 ID。 */
    private long programRequirementId;
    /** 材料类型代码。 */
    private String documentType;
}
