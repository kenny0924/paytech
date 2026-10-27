package com.paytech.compliance.core.dal.dataobject;

import java.time.Instant;

import lombok.Data;

/** 案件材料要求（t_dd_requirement），按程序版本模板实例化。 */
@Data
public class CaseRequirement {
    /** 案件材料要求 ID。 */
    private long requirementId;
    /** 案件 ID。 */
    private long caseId;
    /** 程序材料模板 ID。 */
    private long programRequirementId;
    /** 要求状态：PENDING、RECEIVED、ACCEPTED、REJECTED、WAIVED。 */
    private String status;
    /** 豁免人；未豁免为空。 */
    private Long waivedBy;
    /** 豁免原因码。 */
    private String waivedReasonCode;
    /** 豁免说明。 */
    private String waivedNote;
    /** 豁免时间。 */
    private Instant waivedTime;
    /** 复用来源案件 ID；非复用为空。 */
    private Long reusedFromCaseId;
    /** 并发版本。 */
    private long version;
    /** 创建 UTC 时间。 */
    private Instant createTime;
    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
