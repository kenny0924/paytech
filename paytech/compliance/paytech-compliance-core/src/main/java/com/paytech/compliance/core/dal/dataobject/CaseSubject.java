package com.paytech.compliance.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Data;

/** 案件主体（t_dd_case_subject）；新 Client 使用稳定 Profile ID，不填资料版本。 */
@Data
public class CaseSubject {
    /** 案件主体 ID。 */
    private long caseSubjectId;
    /** 案件 ID。 */
    private long caseId;
    /** Party ID。 */
    private long partyId;
    /** 主体角色。 */
    private String subjectRole;
    /** PRIMARY 为 1，关联主体为空。 */
    private Integer primarySlot;
    /** 来源资料 ID。 */
    private long sourceProfileId;
    /** 来源关系 ID；PRIMARY 为空。 */
    private Long sourceRelationId;
    /** UBO 持股比例；其他为空。 */
    private BigDecimal ownershipPercentage;
    /** 关联人员 UBO 认定依据；主体本身为空。 */
    private String uboBasis;
    /** 关联人员职务；主体本身为空。 */
    private String jobTitle;
    /** 是否必需：1 或 0。 */
    private int requiredFlag;
    /** 创建 UTC 时间。 */
    private Instant createTime;
}
