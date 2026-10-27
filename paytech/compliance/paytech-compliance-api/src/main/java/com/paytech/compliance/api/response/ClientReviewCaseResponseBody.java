package com.paytech.compliance.api.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Client 入驻审核主案件；列表只填摘要字段，详情另含主体、分类、风险评级、推荐、退回错误、时间线与批准门禁。 */
@Getter
@Setter
public class ClientReviewCaseResponseBody {

    /** 主审核案件 ID。 */
    private String caseId;

    /** 案件编号。 */
    private String caseNo;

    /** 案件类型：本期固定 ONBOARDING。 */
    private String caseType;

    /** Client 申请 ID。 */
    private String applicationId;

    /** Client ID。 */
    private String clientId;

    /** 来源提交编号。 */
    private String sourceSubmissionId;

    /** 资格类型。 */
    private String qualificationType;

    /** 冻结的辖区代码。 */
    private String jurisdictionCode;

    /** 冻结的程序版本 ID。 */
    private String programVersionId;

    /** 尽调档次：SIMPLIFIED、STANDARD 或 ENHANCED。 */
    private String tier;

    /** 提交轮次，首次 1。 */
    private Integer roundNo;

    /** 上一轮被退回的主案件 ID；首次提交为空。 */
    private String previousCaseId;

    /** 责任 PayFac 的 Local Partner ID；平台自营为空。 */
    private String responsiblePartnerId;

    /** 主案件状态。 */
    private String status;

    /** 主案件并发版本。 */
    private String version;

    /** 提交时间，UTC ISO-8601。 */
    private String submittedAt;

    /** 结束时间；进行中为空。 */
    private String decidedAt;

    /** 退回补件期限；未退回为空。 */
    private String reviewDueTime;

    /** 客户风险评级 LOW、MEDIUM、HIGH；未评为空。 */
    private String riskRating;

    /** 推荐结论 APPROVE 或 REJECT；未推荐为空。 */
    private String recommendation;

    /** 客户可见说明。 */
    private String publicNote;

    /** 兼容字段：退回分区。 */
    private String returnSection;

    /** 程序是否要求四眼；详情填写。 */
    private Boolean fourEyesRequired;

    /** 评级说明；详情填写。 */
    private String riskRatingNote;

    /** 评级人；详情填写。 */
    private String riskRatedBy;

    /** 评级时间；详情填写。 */
    private String riskRatedAt;

    /** 风险因子；列表为空。 */
    private List<ClientReviewRiskFactorResponseBody> riskFactors;

    /** 推荐人；详情填写。 */
    private String recommendedBy;

    /** 推荐时间；详情填写。 */
    private String recommendedAt;

    /** 推荐理由；详情填写。 */
    private String recommendationNote;

    /** 操作方：PLATFORM（平台运营）或 PARTNER；决定后填写。 */
    private String operatedBy;

    /** 决定时代操作的责任 PayFac；决定后填写。 */
    private String onBehalfOfPartnerId;

    /** 最近一次退回的需求级错误；列表为空。 */
    private List<ClientReviewRequirementErrorBody> returnErrors;

    /** 主体、材料、检查与筛查；列表为空。 */
    private List<ClientReviewSubjectResponseBody> subjects;

    /** 已接收分类；列表为空。 */
    private List<ClientReviewClassificationResponseBody> classifications;

    /** 主案件与子案件的状态时间线，按发生时间升序；列表为空。 */
    private List<ClientReviewTimelineItemBody> timeline;

    /**
     * 批准门禁未满足项；列表为空
     * <li>CASE_NOT_ACTIVE、CHILD_CASE_NOT_ACTIVE、EVIDENCE_PENDING、REQUIRED_MATERIAL_NOT_ACCEPTED、CLASSIFICATION_NOT_RECEIVED、
     * CHECK_NOT_PASSED、SCREENING_INCOMPLETE、SCREENING_HIT_OPEN、SCREENING_TRUE_MATCH、RISK_RATING_MISSING、RECOMMENDATION_MISSING、
     * RECOMMENDATION_NOT_APPROVE、FOUR_EYES_SAME_PERSON（当前操作人即推荐人时）。</li>
     */
    private List<String> approvalBlockers;
}
