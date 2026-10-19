package com.paytech.billing.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.billing.api.enums.LineLinkType;

import lombok.Getter;
import lombok.Setter;

/**
 * t_billing_fact_line_link：反向或调整行与原行之间的金额链接，用于封顶与追溯；不可变
 * <li>本期人工登记不产生链接，只读路径返回空列表。</li>
 */
@Getter
@Setter
public class BillingFactLineLink {
    /** Snowflake 主键 */
    private Long id;

    /** 被冲回或被调整的原明细行 */
    private Long sourceLineId;

    /** 产生冲回或调整的新明细行 */
    private Long targetLineId;

    /** 链接类型 */
    private LineLinkType linkType;

    /** 本链接承担的正金额 */
    private BigDecimal linkedAmount;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;
}
