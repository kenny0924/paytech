package com.paytech.client.contract.api.workspace.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 单个已批准客户的展示摘要；不含证件、地址或联系方式。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientSummaryResponseBody {
    /** Client ID，规范十进制字符串。 */
    private String clientId;
    /** Client 编号。 */
    private String clientNo;
    /** INDIVIDUAL 或 ORGANIZATION。 */
    private String clientType;
    /** CCT Client 生命周期状态：PENDING、ACTIVE、SUSPENDED、TERMINATED。 */
    private String clientStatus;
    /** 组织法定名称，或个人名与姓；资料缺失时为空。 */
    private String displayName;
    /** 组织注册国或个人居住国（ISO 3166-1 alpha-2）；资料缺失时为空。 */
    private String country;
    /** 经营模式代码；资料缺失时为空。 */
    private String businessModelCode;
    /** 最近一份 APPROVED 申请的审核结论时间，UTC 六位微秒。 */
    private String approvedAt;
}
