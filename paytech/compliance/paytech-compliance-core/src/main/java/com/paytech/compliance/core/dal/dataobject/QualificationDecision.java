package com.paytech.risk.compliance.core.dal.dataobject;

import java.time.Instant;

import lombok.Data;

/** 案件最终决定（t_qualification_decision）；新 Client 每个案件最多一条，不填决定版本。 */
@Data
public class QualificationDecision {
    /** 决定 ID。 */
    private long decisionId;

    /** 案件 ID。 */
    private long caseId;

    /** 角色类型。 */
    private String roleType;

    /** 角色 ID。 */
    private long roleId;

    /** 资格类型。 */
    private String qualificationType;

    /** 决定来源：REVIEWER。 */
    private String decisionSource;

    /** 结论：APPROVED 或 REJECTED。 */
    private String outcome;

    /** 有效开始 UTC 时间。 */
    private Instant validFrom;

    /** 决定人 Operations 账号 ID。 */
    private Long decidedBy;

    /** 决定 UTC 时间。 */
    private Instant decidedTime;

    /** 创建 UTC 时间。 */
    private Instant createTime;
}
