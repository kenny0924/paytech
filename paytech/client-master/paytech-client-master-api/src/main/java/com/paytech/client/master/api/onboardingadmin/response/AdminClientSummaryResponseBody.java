package com.paytech.client.master.api.onboardingadmin.response;

import lombok.Getter;
import lombok.Setter;

/** CM-11 Client 列表项。 */
@Getter
@Setter
public class AdminClientSummaryResponseBody {
    /** Client ID。 */
    private String clientId;

    /** Client 编号。 */
    private String clientNo;

    /** INDIVIDUAL 或 ORGANIZATION。 */
    private String clientType;

    /** Client 状态 PENDING、ACTIVE、SUSPENDED、TERMINATED。 */
    private String status;

    /** 入驻辖区，可空。 */
    private String jurisdictionCode;

    /** 责任 PayFac Local Partner ID；平台自营为空。 */
    private String responsiblePartnerId;

    /** 法定名称（个人为姓名），可空。 */
    private String legalName;

    /** 最近一份申请 ID，可空。 */
    private String latestApplicationId;

    /** 最近一份申请状态，可空。 */
    private String latestApplicationStatus;

    /** Client 创建 UTC 时间。 */
    private String createdAt;
}
