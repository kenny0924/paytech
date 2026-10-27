package com.paytech.compliance.api.response;

import lombok.Getter;
import lombok.Setter;

/** 案件状态时间线的一项。 */
@Getter
@Setter
public class ClientReviewTimelineItemBody {

    /** 发生变化的案件 ID（主案件或子案件）。 */
    private String caseId;

    /** 原状态；立案为空。 */
    private String fromStatus;

    /** 新状态。 */
    private String toStatus;

    /** 动作代码，例如 REGISTER、START_REVIEW、RETURN、APPROVE、REJECT、WITHDRAW、EXPIRE。 */
    private String actionType;

    /** 原因码。 */
    private String reasonCode;

    /** 操作人；系统动作为空。 */
    private String actorId;

    /** 操作方 PLATFORM 或 PARTNER；系统动作为空。 */
    private String operatedBy;

    /** 代操作的责任 PayFac；非代操作为空。 */
    private String onBehalfOfPartnerId;

    /** 发生时间。 */
    private String occurredAt;
}
