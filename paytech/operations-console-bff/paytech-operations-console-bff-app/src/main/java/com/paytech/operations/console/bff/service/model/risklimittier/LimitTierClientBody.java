package com.paytech.operations.console.bff.service.model.risklimittier;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 工作台顶部的 Client 摘要：有档位取建档快照，否则取承保快照；都没有时只有 clientId。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LimitTierClientBody {
    /** Client ID。 */
    private String clientId;

    /** Client 编号；未知为 null。 */
    private String clientNo;

    /** 法定名称或个人姓名；未知为 null。 */
    private String displayName;

    /** Client 类型：INDIVIDUAL 或 ORGANIZATION；未知为 null。 */
    private String clientType;

    /** 入驻辖区代码；未知为 null。 */
    private String jurisdictionCode;
}
