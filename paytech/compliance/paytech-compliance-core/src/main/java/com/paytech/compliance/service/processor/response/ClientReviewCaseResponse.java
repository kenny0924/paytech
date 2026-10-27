package com.paytech.compliance.service.processor.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import lombok.Data;

/** Client 入驻审核案件的 Owner 结果；列表只填摘要，详情另含主体、材料、检查、筛查、评级、退回错误、时间线与门禁。 */
@Data
public class ClientReviewCaseResponse {
    /** 主案件 ID。 */
    private long caseId;
    /** 案件编号。 */
    private String caseNo;
    /** 案件类型。 */
    private String caseType;
    /** 申请 ID。 */
    private Long applicationId;
    /** Client ID。 */
    private long clientId;
    /** 来源提交编号。 */
    private String sourceSubmissionId;
    /** 资格类型。 */
    private String qualificationType;
    /** 辖区。 */
    private String jurisdictionCode;
    /** 程序版本 ID。 */
    private long programVersionId;
    /** 档次。 */
    private String tier;
    /** 轮次。 */
    private int roundNo;
    /** 上一轮主案件。 */
    private Long previousCaseId;
    /** 责任 PayFac。 */
    private Long responsiblePartnerId;
    /** 主案件状态。 */
    private String status;
    /** 主案件版本。 */
    private long version;
    /** 提交时间。 */
    private Instant submittedAt;
    /** 结束时间。 */
    private Instant decidedAt;
    /** 补件期限。 */
    private Instant reviewDueTime;
    /** 风险评级。 */
    private String riskRating;
    /** 推荐结论。 */
    private String recommendation;
    /** 客户可见说明。 */
    private String publicNote;
    /** 兼容退回分区。 */
    private String returnSection;
    /** 程序是否要求四眼（详情）。 */
    private Boolean fourEyesRequired;
    /** 评级说明（详情）。 */
    private String riskRatingNote;
    /** 评级人（详情）。 */
    private Long riskRatedBy;
    /** 评级时间（详情）。 */
    private Instant riskRatedAt;
    /** 风险因子（详情）。 */
    private List<RiskFactorItem> riskFactors = List.of();
    /** 推荐人（详情）。 */
    private Long recommendedBy;
    /** 推荐时间（详情）。 */
    private Instant recommendedAt;
    /** 推荐理由（详情）。 */
    private String recommendationNote;
    /** 操作方。 */
    private String operatedBy;
    /** 代操作 PayFac。 */
    private Long onBehalfOfPartnerId;
    /** 退回需求级错误（详情）。 */
    private List<ErrorItem> returnErrors = List.of();
    /** 主体（详情）。 */
    private List<Subject> subjects = List.of();
    /** 分类（详情）。 */
    private List<Classification> classifications = List.of();
    /** 时间线（详情）。 */
    private List<TimelineItem> timeline = List.of();
    /** 批准门禁未满足项（详情）。 */
    private List<String> approvalBlockers = List.of();

    /** 一个案件的主体及其材料、检查、筛查。 */
    @Data
    public static class Subject {
        /** 案件 ID。 */
        private long caseId;
        /** 案件状态。 */
        private String caseStatus;
        /** Party ID。 */
        private long partyId;
        /** 来源资料 ID。 */
        private long profileId;
        /** 角色类型。 */
        private String roleType;
        /** 资格类型。 */
        private String qualificationType;
        /** UBO 依据。 */
        private String uboBasis;
        /** 职务。 */
        private String jobTitle;
        /** 依赖类型。 */
        private List<String> dependencyTypes;
        /** 材料要求。 */
        private List<Requirement> requirements;
        /** 检查。 */
        private List<Check> checks;
        /** 筛查执行。 */
        private List<ScreeningRunItem> screenings;
    }

    /** 一项材料要求。 */
    @Data
    public static class Requirement {
        /** 要求 ID。 */
        private long requirementId;
        /** 要求代码。 */
        private String requirementCode;
        /** 允许类型。 */
        private List<String> documentTypes;
        /** 是否必需。 */
        private boolean required;
        /** 是否可豁免。 */
        private boolean waivable;
        /** 最长时效天数。 */
        private Integer maxAgeDays;
        /** 状态。 */
        private String status;
        /** 豁免人。 */
        private Long waivedBy;
        /** 豁免原因码。 */
        private String waivedReasonCode;
        /** 豁免时间。 */
        private Instant waivedAt;
        /** 复用来源案件。 */
        private Long reusedFromCaseId;
        /** 证据。 */
        private List<Evidence> evidence;
    }

    /** 一份证据。 */
    @Data
    public static class Evidence {
        /** 证据 ID。 */
        private long evidenceId;
        /** 文件地址。 */
        private String fileAddress;
        /** 文件摘要。 */
        private String contentHash;
        /** 材料类型。 */
        private String documentType;
        /** 签发日期。 */
        private LocalDate issueDate;
        /** 到期日期。 */
        private LocalDate expiryDate;
        /** 状态。 */
        private String status;
        /** 拒绝原因码。 */
        private String rejectReasonCode;
        /** 核验人。 */
        private Long reviewerId;
        /** 核验时间。 */
        private Instant reviewedAt;
        /** 复用来源案件。 */
        private Long reusedFromCaseId;
    }

    /** 一项检查。 */
    @Data
    public static class Check {
        /** 检查 ID。 */
        private long checkId;
        /** 检查代码。 */
        private String checkCode;
        /** 检查类型。 */
        private String checkType;
        /** 执行方式。 */
        private String checkMode;
        /** 是否阻断。 */
        private boolean blocking;
        /** 是否必需。 */
        private boolean required;
        /** 状态。 */
        private String status;
        /** 结论。 */
        private String outcome;
        /** 原因码。 */
        private String reasonCode;
        /** 查询引用。 */
        private String evidenceRef;
        /** 备注。 */
        private String note;
        /** 录入人。 */
        private Long reviewerId;
        /** 录入时间。 */
        private Instant reviewedAt;
        /** 有效截止。 */
        private Instant validUntil;
        /** 复用来源案件。 */
        private Long reusedFromCaseId;
    }

    /** 一次筛查执行。 */
    @Data
    public static class ScreeningRunItem {
        /** 执行 ID。 */
        private long runId;
        /** 筛查类型。 */
        private String screeningType;
        /** 渠道编码。 */
        private String providerCode;
        /** 状态。 */
        private String status;
        /** 触发来源。 */
        private String triggerSource;
        /** 开始时间。 */
        private Instant startedAt;
        /** 完成时间。 */
        private Instant completedAt;
        /** 命中。 */
        private List<Hit> hits;
    }

    /** 一条命中。 */
    @Data
    public static class Hit {
        /** 命中 ID。 */
        private long hitId;
        /** 外部引用。 */
        private String externalHitRef;
        /** 实体类型。 */
        private String entityType;
        /** 匹配方式。 */
        private String matchType;
        /** 匹配分数。 */
        private BigDecimal matchScore;
        /** 风险等级。 */
        private String riskLevel;
        /** 名单类型。 */
        private List<String> listTypes;
        /** 状态。 */
        private String status;
        /** 处置记录。 */
        private List<Resolution> resolutions;
    }

    /** 一次处置。 */
    @Data
    public static class Resolution {
        /** 处置版本。 */
        private int resolutionVersion;
        /** 结论。 */
        private String resolution;
        /** 原因码。 */
        private String reasonCode;
        /** 备注。 */
        private String note;
        /** 处置人。 */
        private long reviewerId;
        /** 处置时间。 */
        private Instant resolvedAt;
        /** 代操作 PayFac。 */
        private Long onBehalfOfPartnerId;
    }

    /** 一项风险因子。 */
    @Data
    public static class RiskFactorItem {
        /** 因子代码。 */
        private String factorCode;
        /** 因子取值。 */
        private String factorValue;
        /** 来源。 */
        private String source;
        /** 记录人。 */
        private Long recordedBy;
        /** 记录时间。 */
        private Instant recordedAt;
    }

    /** 一项退回错误。 */
    @Data
    public static class ErrorItem {
        /** 需求键。 */
        private String requirementKey;
        /** 主体 Party ID。 */
        private Long subjectPartyId;
        /** 错误码。 */
        private String code;
        /** 说明。 */
        private String reason;
        /** 补救动作码。 */
        private String remediation;
        /** 补件期限。 */
        private Instant dueTime;
    }

    /** 一项已接收分类。 */
    @Data
    public static class Classification {
        /** 行业代码。 */
        private String industryCode;
        /** MCC。 */
        private String mccCode;
        /** 是否主要分类。 */
        private boolean primary;
    }

    /** 一项时间线。 */
    @Data
    public static class TimelineItem {
        /** 案件 ID。 */
        private long caseId;
        /** 原状态。 */
        private String fromStatus;
        /** 新状态。 */
        private String toStatus;
        /** 动作。 */
        private String actionType;
        /** 原因码。 */
        private String reasonCode;
        /** 操作人。 */
        private Long actorId;
        /** 操作方。 */
        private String operatedBy;
        /** 代操作 PayFac。 */
        private Long onBehalfOfPartnerId;
        /** 发生时间。 */
        private Instant occurredAt;
    }
}
