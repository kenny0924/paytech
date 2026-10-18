package com.paytech.compliance.core.dal.dataobject;

import java.time.Instant;

import lombok.Data;

/** 案件材料要求（t_dd_requirement），按程序修订模板实例化。 */
@Data
public class CaseRequirement {
    /** 案件材料要求 ID。 */
    private long requirementId;

    /** 案件 ID。 */
    private long caseId;

    /** 程序材料模板 ID。 */
    private long programRequirementId;

    /** 要求状态：PENDING 或 RECEIVED。 */
    private String status;

    /** 并发版本。 */
    private long version;

    /** 创建 UTC 时间。 */
    private Instant createTime;

    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
