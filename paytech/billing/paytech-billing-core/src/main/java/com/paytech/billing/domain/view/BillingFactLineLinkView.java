package com.paytech.billing.domain.view;

import java.math.BigDecimal;

import com.paytech.billing.api.enums.LineLinkType;

import lombok.Getter;
import lombok.Setter;

/** 以事实明细行为新行的行链接（t_billing_fact_line_link 的查询结果）；金额已按币种精度补齐。 */
@Getter
@Setter
public class BillingFactLineLinkView {
    /** 原明细行 ID。 */
    private Long sourceLineId;

    /** 新明细行 ID。 */
    private Long targetLineId;

    /** 链接类型。 */
    private LineLinkType linkType;

    /** 链接金额。 */
    private BigDecimal linkedAmount;
}
