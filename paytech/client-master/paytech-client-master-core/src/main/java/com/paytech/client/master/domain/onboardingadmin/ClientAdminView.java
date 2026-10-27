package com.paytech.client.master.domain.onboardingadmin;

import java.time.Instant;

import com.paytech.client.master.domain.enums.ClientApplicationStatus;
import com.paytech.client.master.domain.enums.ClientStatus;
import com.paytech.client.master.domain.enums.ClientType;

import lombok.Getter;
import lombok.Setter;

/** 运营 Client 列表行：t_client 与 Party 类型、当前资料名称、最近一份申请的组合查询结果。 */
@Getter
@Setter
public class ClientAdminView {
    /** Client ID。 */
    private Long clientId;
    /** Client 编号。 */
    private String clientNo;
    /** Party 类型。 */
    private ClientType clientType;
    /** Client 状态。 */
    private ClientStatus status;
    /** 入驻辖区，可空。 */
    private String jurisdictionCode;
    /** 责任 PayFac，可空。 */
    private Long responsiblePartnerId;
    /** 当前资料法定名称（个人为姓名），可空。 */
    private String legalName;
    /** 最近一份申请 ID，可空。 */
    private Long latestApplicationId;
    /** 最近一份申请状态，可空。 */
    private ClientApplicationStatus latestApplicationStatus;
    /** Client 创建 UTC 时间。 */
    private Instant createTime;
}
