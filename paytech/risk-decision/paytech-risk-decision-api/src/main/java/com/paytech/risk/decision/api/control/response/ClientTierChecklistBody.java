package com.paytech.risk.decision.api.control.response;

import lombok.Getter;
import lombok.Setter;

/** 申请时冻结的上线复核清单。 */
@Getter
@Setter
public class ClientTierChecklistBody {

    /** 系统项：资格状态。 */
    private String qualificationStatus;

    /** 系统项：资格有效期 UTC 时间。 */
    private String qualificationValidTo;

    /** 系统项：有效承保版本号。 */
    private Integer underwritingVersionNo;

    /** 系统项：CLIENT 合同 ID。 */
    private String agreementId;

    /** 系统项：合同状态。 */
    private String agreementStatus;

    /** 系统项：ENABLED 的支付方式开通项代码，逗号分隔。 */
    private String enabledPaymentCapabilities;

    /** 系统项：未 ENABLED 的支付方式开通项代码，逗号分隔。 */
    private String pendingPaymentCapabilities;

    /** 人工项：渠道报备已确认。 */
    private Boolean channelRegistrationConfirmed;

    /** 人工项：技术接入已确认。 */
    private Boolean integrationConfirmed;

    /** 人工项：生产交易验证已完成。 */
    private Boolean productionVerified;

    /** 人工项：未结事项已处理。 */
    private Boolean openIssuesCleared;
}
