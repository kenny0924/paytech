package com.paytech.billing.api.response;

import lombok.Getter;
import lombok.Setter;

/** 明细行链接：新行对原行的冲回或调整及其承担金额。 */
@Getter
@Setter
public class BillingFactLineLinkResponseBody {
    /** 被冲回或被调整的原明细行 ID（t_billing_fact_line.id） */
    private String sourceLineId;

    /** 产生冲回或调整的新明细行 ID（t_billing_fact_line.id） */
    private String targetLineId;

    /** 链接类型（LineLinkType：REVERSAL、ADJUSTMENT） */
    private String linkType;

    /** 本链接承担的正金额，按币种精度补齐 */
    private String linkedAmount;
}
