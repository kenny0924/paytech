package com.paytech.risk.compliance.core.dal.dataobject;

import java.time.Instant;

import lombok.Data;

/** 主案件对关联人员子案件的依赖（t_dd_case_dependency）。 */
@Data
public class CaseDependency {
    /** 依赖 ID。 */
    private long dependencyId;

    /** 主案件 ID。 */
    private long parentCaseId;

    /** 子案件 ID。 */
    private long childCaseId;

    /** 依赖类型：UBO_KYC、DIRECTOR_KYC 或 SIGNATORY_KYC。 */
    private String dependencyType;

    /** 是否必需：1 或 0。 */
    private int requiredFlag;

    /** 并发版本。 */
    private long version;

    /** 创建 UTC 时间。 */
    private Instant createTime;

    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
