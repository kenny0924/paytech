package com.paytech.billing.api.fact;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 经济事实明细行（t_economic_fact_line）：费用类型、付方 / 收方、方向与已按币种精度归整的金额
 * <li>EconomicFactRecorded 事件中所有字段都以 JSON 字符串出现（金额为币种主单位十进制、按币种精度补齐），缺省值为 JSON null；
 * Jackson 默认把十进制字符串绑定到本类的 Integer / BigDecimal 字段。</li>
 */
@Getter
@Setter
public class EconomicFactLinePayload {
    /** 同一经济事实内稳定且唯一的行号（component_no），从 1 起连续。 */
    private Integer lineNo;

    /**
     * 费用类型代码（component_type）
     * <li>自动记账规则行与 account 余额映射都以 {@code AmountSource} 代码匹配；人工 / 成本调整登记时即请求的 feeTypeCode。</li>
     */
    private String componentType;

    /** 归属方角色代码（FactPartyRole）；人工 / 成本调整行等于收方角色，无归属方为空。 */
    private String partyType;

    /** 按 partyType 解释的稳定标识；OE 为 entityNo，其余为十进制字符串。 */
    private String partyId;

    /**
     * 付方角色代码（FactPartyRole）
     * <li>人工 / 成本调整行必填；规则计算产生的交易事实行可为空（第 11a 步前登记的行没有付方 / 收方）。</li>
     */
    private String payerRole;

    /** 按 payerRole 解释的付方标识；OE 为 entityNo，其余为十进制字符串。 */
    private String payerId;

    /** 收方角色代码（FactPartyRole），可空条件同 payerRole。 */
    private String payeeRole;

    /** 按 payeeRole 解释的收方标识。 */
    private String payeeId;

    /** 影响方向代码（FactLineDirection：CHARGE、REVERSAL、ADJUSTMENT）。 */
    private String direction;

    /** 按币种精度与固定舍入方式归整后的正金额。 */
    private BigDecimal postedAmount;
}
