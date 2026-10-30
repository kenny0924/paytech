package com.paytech.risk.decision.api.control.response;

import lombok.Getter;
import lombok.Setter;

/** 档位列表的一行：当前档位、观察期进度与待审批申请；ID、时间为字符串。 */
@Getter
@Setter
public class ClientTierSummaryBody {

    /** Client ID。 */
    private String clientId;

    /** 建档时 Client 名称；无档位时为空。 */
    private String clientDisplayName;

    /** 建档时 Client 编号；无档位时为空。 */
    private String clientNo;

    /** 建档时入驻辖区代码；无档位时为空。 */
    private String jurisdictionCode;

    /** 建档时 Client 类型：INDIVIDUAL 或 ORGANIZATION；无档位时为空。 */
    private String clientType;

    /** 当前有效承保的 CRR：LOW、MEDIUM、HIGH；无有效承保或未评为空。 */
    private String crr;

    /** 当前档位：VERIFICATION、OBSERVATION、STANDARD、RESTRICTED；无档位为空（额度为零）。 */
    private String tier;

    /** 当前档位生效的 UTC 时间。 */
    private String effectiveTime;

    /** 当前档位来源：UNDERWRITING_INIT、MIGRATION、REQUEST。 */
    private String sourceType;

    /** 产生当前档位的变更记录理由。 */
    private String sourceReason;

    /** 观察期开始 UTC 时间；非 OBSERVATION 为空。 */
    private String observationStartedTime;

    /** 观察期已过整天数；非 OBSERVATION 为空。 */
    private Integer observationDaysElapsed;

    /** 观察期目标天数；非 OBSERVATION 为空。 */
    private Integer observationTargetDays;

    /**
     * 观察期内风控放行的支付尝试数（近似值）
     * <li>非 OBSERVATION 为空。</li>
     */
    private Long observationAllowedTransactions;

    /** 观察期目标放行笔数；非 OBSERVATION 为空。 */
    private Integer observationTargetTransactions;

    /** 观察期是否到期待确认：天数或笔数任一达到目标；非 OBSERVATION 为 false。 */
    private Boolean observationDue;

    /** 待审批申请；没有为空。 */
    private ClientTierPendingChangeBody pendingChange;

    /** 档位行最后更新的 UTC 时间。 */
    private String updatedAt;
}
