package com.paytech.compliance.core.dal.dataobject;

import lombok.Data;

/** 程序检查模板（t_dd_program_check）。 */
@Data
public class ProgramCheck {
    /** 检查模板 ID。 */
    private long programCheckId;
    /** 程序版本 ID。 */
    private long programVersionId;
    /** 检查代码。 */
    private String checkCode;
    /** 检查类型。 */
    private String checkType;
    /** 执行方式。 */
    private String checkMode;
    /** 检查对象主体角色。 */
    private String subjectRole;
    /** 自动检查的标准 API；人工为空。 */
    private String standardApiCode;
    /** 是否必需：1 或 0。 */
    private int requiredFlag;
    /** 非 PASS 是否阻断批准：1 或 0。 */
    private int blockingFlag;
    /** 结论有效天数；不设有效期为空。 */
    private Integer validityDays;

    /** 是否必需。 */
    public boolean isRequired() {
        return requiredFlag == 1;
    }

    /** 是否阻断批准。 */
    public boolean isBlocking() {
        return blockingFlag == 1;
    }
}
