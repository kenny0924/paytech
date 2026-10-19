package com.paytech.billing.api.fact;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 计费事实明细行（t_billing_fact_line）：组件类型、付方 / 收方、方向与已按币种精度归整的金额
 * <li>BillingFactRecorded 事件中所有字段都以 JSON 字符串出现（金额为币种主单位十进制、按币种精度补齐），缺省值为 JSON null；
 * Jackson 默认把十进制字符串绑定到本类的 Integer / BigDecimal 字段。</li>
 * <li>每行必有付方与收方；行的核算主体即收方，不再单列归属方。</li>
 */
@Getter
@Setter
public class BillingFactLinePayload {
    /** 同一计费事实内稳定且唯一的行号，从 1 起连续。 */
    private Integer lineNo;

    /** 金额组成类型代码（FactComponentType）；ledger 规则行金额来源按同名代码汇总。 */
    private String componentType;

    /** 付方角色代码（FactPartyRole）。 */
    private String payerRole;

    /** 按 payerRole 解释的付方标识；OE 为 entityNo，其余为十进制字符串。 */
    private String payerId;

    /** 收方角色代码（FactPartyRole），不得与付方相同。 */
    private String payeeRole;

    /** 按 payeeRole 解释的收方标识。 */
    private String payeeId;

    /** 影响方向代码（FactLineDirection：CHARGE、REVERSAL、ADJUSTMENT）。 */
    private String direction;

    /** 按币种精度与固定舍入方式归整后的正金额。 */
    private BigDecimal postedAmount;

    /**
     * 收方 Owner 账户的目标余额桶（PENDING、AVAILABLE）
     * <li>仅 MANUAL_ADJUSTMENT 且收方为 CLIENT / LOCAL_PARTNER 时可非空，为空按 AVAILABLE；其余为空。account 映射随后续切片启用。</li>
     */
    private String targetBucket;
}
