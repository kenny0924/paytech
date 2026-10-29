package com.paytech.risk.control.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.risk.control.core.enums.FulfillmentType;
import com.paytech.risk.control.core.enums.UnderwritingClientType;
import com.paytech.risk.control.core.enums.UnderwritingDecisionSource;
import com.paytech.risk.control.core.enums.UnderwritingDueDiligenceTier;
import com.paytech.risk.control.core.enums.UnderwritingRiskLevel;
import com.paytech.risk.control.core.enums.UnderwritingStatus;

import lombok.Data;

/** Client 承保版本（t_client_underwriting），每个版本一行。 */
@Data
public class ClientUnderwriting {
    /** 承保版本 ID。 */
    private long underwritingId;

    /** 被承保 Client ID（client-master 跨库值引用）。 */
    private long clientId;

    /** 同一 Client 内从 1 递增的版本号。 */
    private int versionNo;

    /** 版本状态。 */
    private UnderwritingStatus status;

    /**
     * 进行中占位
     * <li>DRAFT、SUBMITTED 为 true，其余为空；唯一索引保证同一 Client 至多一个进行中版本。</li>
     */
    private Boolean openSlot;

    /**
     * 有效结论占位
     * <li>当前有效的 APPROVED 为 true，其余为空；唯一索引保证同一 Client 至多一个有效结论。</li>
     */
    private Boolean effectiveSlot;

    /** 本版本批准后替代的上一有效版本 ID；未替代为空。 */
    private Long supersedesUnderwritingId;

    /** 结论来源；未决定为空。 */
    private UnderwritingDecisionSource decisionSource;

    /** AUTO_RULE 使用的规则版本 ID；人工为空。 */
    private Long ruleVersionId;

    /** 快照时 Client 名称。 */
    private String clientDisplayName;

    /** 快照时 Client 编号。 */
    private String clientNo;

    /** 快照时 Client 类型。 */
    private UnderwritingClientType clientType;

    /** 快照时辖区代码（开放目录）。 */
    private String jurisdictionCode;

    /** 快照时尽调 CRR；存量未评为空。 */
    private UnderwritingRiskLevel crr;

    /** 复核级别：CRR 非空时等于 CRR，为空时 MEDIUM。 */
    private UnderwritingRiskLevel reviewLevel;

    /** 快照时尽调档次；可空。 */
    private UnderwritingDueDiligenceTier ddTier;

    /** 快照时主 MCC；草稿可空。 */
    private String mccCode;

    /** 快照时主行业代码；可空。 */
    private String industryCode;

    /** 快照时 MCC 是否卡组织高风险。 */
    private boolean networkHighRiskMcc;

    /** 预计月交易金额；可空。 */
    private BigDecimal expectedMonthlyVolume;

    /** 预计月交易金额与客单价的币种；有金额时非空。 */
    private String volumeCurrency;

    /** 预计单笔金额；可空。 */
    private BigDecimal expectedTicket;

    /** 交付方式；存量未采集为空。 */
    private FulfillmentType fulfillmentType;

    /** 预售交付天数；仅 PRE_ORDER 有值。 */
    private Integer fulfillmentDays;

    /** 申报网站。 */
    private String websiteUrl;

    /** 申报退款政策链接。 */
    private String refundPolicyUrl;

    /** 期望支付方式规范化逗号串。 */
    private String expectedPaymentMethods;

    /** 来源尽调案件 ID；提交必填。 */
    private Long sourceCaseId;

    /** 来源尽调案件版本。 */
    private Long sourceCaseVersion;

    /** 来源入驻申请 ID。 */
    private Long sourceApplicationId;

    /** 来源申请资料版本。 */
    private Long sourceDraftRevision;

    /** 运营确认分类的 UTC 时间；为空表示未确认。 */
    private Instant sourceClassificationConfirmedTime;

    /** 观察期天数；提交必填。 */
    private Integer observationDays;

    /** 观察期笔数；提交必填。 */
    private Integer observationTransactions;

    /** 是否要求最低保证金。 */
    private boolean requireDeposit;

    /** 最低保证金金额；不要求时为空。 */
    private BigDecimal depositAmount;

    /** 最低保证金币种；不要求时为空。 */
    private String depositCurrency;

    /** 保证金到账前是否建议阻塞收款。 */
    private boolean depositBlocksCollection;

    /** 结算周期建议 T+N 的 N；未给出为空。 */
    private Integer suggestedSettlementDays;

    /** 是否需卡组织高风险登记。 */
    private boolean highRiskMcc;

    /** 高风险登记标记与默认不同时的理由。 */
    private String highRiskMccReason;

    /** 估算敞口金额（保留 2 位）；不可算为空。 */
    private BigDecimal estimatedExposure;

    /** 估算敞口使用的风险天数；不可算为空。 */
    private Integer exposureRiskDays;

    /** 额度说明。 */
    private String limitNote;

    /** 承保说明。 */
    private String underwritingNote;

    /** 交接备注。 */
    private String handoverNote;

    /** DECLINED 的拒绝原因。 */
    private String declineReason;

    /** 修订原因；非修订版本为空。 */
    private String revisionReason;

    /** HIGH 复核意见。 */
    private String reviewComment;

    /** 建草稿或触发自动承保的账号 ID。 */
    private long createdBy;

    /** 提交人账号 ID；自动承保为空。 */
    private Long submittedBy;

    /** 提交（冻结）时间；自动承保为生成时间。 */
    private Instant submittedTime;

    /** 决定人账号 ID；自动承保为空。 */
    private Long decidedBy;

    /** 决定时间。 */
    private Instant decidedTime;

    /** 被替代时间；未替代为空。 */
    private Instant supersededTime;

    /** 并发版本，从 1 开始。 */
    private int version;

    /** 创建 UTC 时间。 */
    private Instant createTime;

    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
