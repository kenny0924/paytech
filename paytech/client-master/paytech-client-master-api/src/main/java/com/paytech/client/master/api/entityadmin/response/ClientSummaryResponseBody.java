package com.paytech.client.master.api.entityadmin.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 资格登记前核对的 Client 主体摘要。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientSummaryResponseBody {
    /** Client ID，十进制字符串。 */
    private String clientId;

    /** Client 编号。 */
    private String clientNo;

    /** 法律主体 Party ID。 */
    private String partyId;

    /** Client 类型 Code：INDIVIDUAL 或 ORGANIZATION。 */
    private String clientType;

    /** Client 状态 Code：PENDING、ACTIVE、SUSPENDED 或 TERMINATED。 */
    private String status;

    /**
     * 法定名称
     * <li>机构取 legal_name，个人取名与姓；资料缺失时为 null。</li>
     */
    private String legalName;
}
