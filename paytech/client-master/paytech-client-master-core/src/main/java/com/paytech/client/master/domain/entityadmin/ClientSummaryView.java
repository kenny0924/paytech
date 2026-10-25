package com.paytech.client.master.domain.entityadmin;

import com.paytech.client.master.domain.enums.ClientStatus;
import com.paytech.client.master.domain.enums.ClientType;

import lombok.Getter;
import lombok.Setter;

/** 资格登记前核对的 Client 主体：Client 主档、Party 类型与法定名称。 */
@Getter
@Setter
public class ClientSummaryView {
    /** Client ID。 */
    private Long clientId;
    /** Client 编号。 */
    private String clientNo;
    /** 法律主体 Party ID。 */
    private Long partyId;
    /** 由 Party 类型决定的 Client 类型。 */
    private ClientType clientType;
    /** Client 状态。 */
    private ClientStatus status;
    /** 法定名称；资料缺失时为空。 */
    private String legalName;
}
