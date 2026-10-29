package com.paytech.operations.console.bff.service.model.riskunderwriting;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 工作台顶部的 Client 主档摘要（client-master）。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UnderwritingClientBody {
    /** Client ID。 */
    private String clientId;

    /** Client 编号。 */
    private String clientNo;

    /** 法定名称或个人姓名。 */
    private String displayName;

    /** Client 类型：INDIVIDUAL 或 ORGANIZATION。 */
    private String clientType;

    /** 入驻辖区代码。 */
    private String jurisdictionCode;
}
