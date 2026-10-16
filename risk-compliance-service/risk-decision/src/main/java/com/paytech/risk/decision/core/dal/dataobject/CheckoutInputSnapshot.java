package com.paytech.risk.decision.core.dal.dataobject;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Data;

/** 结账风控点正式评估的不可变输入快照（t_checkout_input_snapshot）。 */
@Data
public class CheckoutInputSnapshot {
    /** 结账输入快照 ID。 */
    private long snapshotId;

    /** 正式评估 ID。 */
    private long evaluationId;

    /** Commerce 预分配的订单 ID，结账风控点的业务唯一身份。 */
    private long orderId;

    /** Client ID。 */
    private long clientId;

    /** Operating Entity 编号。 */
    private String entityNo;

    /** 经营主体来源，PLATFORM、CLIENT 或 PARTNER。 */
    private String entitySourceType;

    /**
     * Local Partner ID
     * <li>仅 PARTNER 来源非空。</li>
     */
    private Long localPartnerId;

    /** 订单消费者国家，ISO 3166-1 alpha-2。 */
    private String consumerCountryCode;

    /** 订单币种，ISO 4217。 */
    private String currency;

    /** 优惠前商品总额，币种主单位。 */
    private BigDecimal merchandiseAmount;

    /** 商品优惠总额，币种主单位。 */
    private BigDecimal discountAmount;

    /** 税前商品净额，币种主单位。 */
    private BigDecimal netAmount;

    /** 行业代码。 */
    private String industryCode;

    /** 四位 MCC。 */
    private String mccCode;

    /** 订单商品明细行数。 */
    private int lineCount;

    /** 订单 ID 与全部输入字段的稳定输入摘要，重放比较使用。 */
    private String stableInputHash;

    /** UTC 微秒创建时间。 */
    private Instant createTime;
}
