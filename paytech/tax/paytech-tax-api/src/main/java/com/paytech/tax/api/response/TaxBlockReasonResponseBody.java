package com.paytech.tax.api.response;


import lombok.Getter;
import lombok.Setter;

/** BLOCKED 的一条原因 */
@Getter
@Setter
public class TaxBlockReasonResponseBody {
    /** 原因码（TaxBlockReason） */
    private String code;

    /**
     * 所缺字段或所在位置
     * <li>例如 lines[2].categoryCode、evidence.BILLING_COUNTRY、market.SA；不含个人原值。</li>
     * <li>lines[n] 中的 n 是该行的 lineNo（从 1 开始，与 lines[].lineNo 相同），不是数组下标。</li>
     */
    private String path;
}
