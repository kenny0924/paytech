package com.paytech.compliance.core.dal.dataobject;

import lombok.Data;

/** 程序筛查模板（t_dd_program_screening）。 */
@Data
public class ProgramScreening {
    /** 筛查模板 ID。 */
    private long programScreeningId;
    /** 程序版本 ID。 */
    private long programVersionId;
    /** 被筛查主体角色。 */
    private String subjectRole;
    /** 筛查类型。 */
    private String screeningType;
    /** 标准 API 编码。 */
    private String standardApiCode;
    /** 是否必需：1 或 0。 */
    private int requiredFlag;
    /** 是否持续监控：1 或 0。 */
    private int monitoringRequired;

    /** 是否必需。 */
    public boolean isRequired() {
        return requiredFlag == 1;
    }
}
