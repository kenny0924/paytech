package com.paytech.billing.api.fact;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/** 经济事实明细行（t_economic_fact_line）：组件类型、归属方与已按币种精度归整的金额。 */
@Getter
@Setter
public class EconomicFactLinePayload {
    /** 同一经济事实内稳定且唯一的行号（component_no）。 */
    private Integer lineNo;

    /** 组件类型代码（component_type）；自动记账规则行以 AmountSource 代码匹配。 */
    private String componentType;

    /** 归属方角色代码；无归属方为空。 */
    private String partyType;

    /** 按 partyType 解释的稳定标识；OE 为 entityNo，其余为十进制字符串。 */
    private String partyId;

    /** 按币种精度与固定舍入方式归整后的非负金额。 */
    private BigDecimal postedAmount;
}
