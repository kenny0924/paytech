package com.paytech.compliance.core.dal.dataobject;

import java.time.Instant;

import lombok.Data;

/** 尽调案件（t_due_diligence_case）。 */
@Data
public class DueDiligenceCase {
    /** 案件 ID。 */
    private long caseId;
    /** 案件编号。 */
    private String caseNo;
    /** 案件类型：本期 ONBOARDING。 */
    private String caseType;
    /** 角色类型：CLIENT 或 RELATED_PERSON。 */
    private String roleType;
    /** 角色 ID：Client ID 或关联人员 Party ID。 */
    private long roleId;
    /** 资格类型。 */
    private String qualificationType;
    /** 冻结的程序版本 ID。 */
    private long ddProgramVersionId;
    /** 冻结的辖区代码。 */
    private String jurisdictionCode;
    /** 冻结的尽调档次。 */
    private String tier;
    /** 案件来源：APPLICATION。 */
    private String caseOrigin;
    /** 来源命名空间：CCT_CLIENT_APPLICATION。 */
    private String sourceNamespace;
    /** 来源申请 ID。 */
    private Long sourceApplicationId;
    /** 来源提交编号。 */
    private String sourceSubmissionId;
    /** 提交轮次，首次 1。 */
    private int roundNo;
    /** 上一轮同级案件 ID；首次为空。 */
    private Long previousCaseId;
    /** 责任 PayFac Local Partner ID；平台自营为空。 */
    private Long responsiblePartnerId;
    /** 客户风险评级 LOW、MEDIUM、HIGH；未评为空。 */
    private String riskRating;
    /** 评级说明。 */
    private String riskRatingNote;
    /** 评级人。 */
    private Long riskRatedBy;
    /** 评级时间。 */
    private Instant riskRatedTime;
    /** 推荐结论 APPROVE 或 REJECT；未推荐为空。 */
    private String recommendation;
    /** 推荐理由。 */
    private String recommendationNote;
    /** 推荐人。 */
    private Long recommendedBy;
    /** 推荐时间。 */
    private Instant recommendedTime;
    /** 运营确认的客户可见说明；退回或决定时写入。 */
    private String publicNote;
    /** 内部审核理由，禁止展示给 Client。 */
    private String resolutionNote;
    /** 兼容字段：退回定位分区。 */
    private String returnSection;
    /** 退回补件期限；未退回为空。 */
    private Instant reviewDueTime;
    /** 案件状态。 */
    private String status;
    /** 进行中为 1，RETURNED 与终态为空。 */
    private Integer activeSlot;
    /** 决定时的操作方：PLATFORM；未决定为空。 */
    private String operatedBy;
    /** 决定时代操作的责任 PayFac；非代操作为空。 */
    private Long onBehalfOfPartnerId;
    /** 提交 UTC 时间。 */
    private Instant submittedTime;
    /** 结束（退回、决定、撤回、过期）UTC 时间。 */
    private Instant decisionTime;
    /** 并发版本。 */
    private long version;
    /** 创建 UTC 时间。 */
    private Instant createTime;
    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
