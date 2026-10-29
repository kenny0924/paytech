package com.paytech.risk.decision.api.control.response;

import lombok.Getter;
import lombok.Setter;

/** 承保列表的一行，取版本行的快照列。 */
@Getter
@Setter
public class UnderwritingSummaryBody {

    /** 承保版本 ID。 */
    private String underwritingId;

    /** Client ID。 */
    private String clientId;

    /** 快照时 Client 名称。 */
    private String clientDisplayName;

    /** 快照时 Client 编号。 */
    private String clientNo;

    /** 快照时辖区代码。 */
    private String jurisdictionCode;

    /** 快照时 Client 类型。 */
    private String clientType;

    /** 快照时 CRR；未评为空。 */
    private String crr;

    /** 快照时 MCC；草稿可空。 */
    private String mccCode;

    /** 快照时 MCC 是否卡组织高风险。 */
    private Boolean networkHighRiskMcc;

    /** 版本号。 */
    private Integer versionNo;

    /** 状态。 */
    private String status;

    /** 复核级别。 */
    private String reviewLevel;

    /** 结论来源；未决定为空。 */
    private String decisionSource;

    /** 自动承保规则版本号；人工为空。 */
    private Integer ruleVersionNo;

    /** 估算敞口；无法估算为空。 */
    private UnderwritingExposureBody estimatedExposure;

    /** 快照时交付方式；未采集为空。 */
    private String fulfillmentType;

    /** 结算周期建议 T+N 的 N；未填为空。 */
    private Integer suggestedSettlementDays;

    /** 是否要求保证金。 */
    private Boolean requireDeposit;

    /** 提交人账号 ID。 */
    private String submittedBy;

    /** 决定人账号 ID。 */
    private String decidedBy;

    /** 最后更新时间，UTC ISO-8601。 */
    private String updatedAt;
}
