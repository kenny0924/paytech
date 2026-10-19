package com.paytech.commercial.core.dal.dataobject;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * t_commercial_term_price：版本下准确 OE、币种与定价画像的价格绑定。
 * <li>CLIENT_PRICING 四个结算字段全部非空，Partner 类全部为空；版本 APPROVED 后不可修改。</li>
 */
@Getter
@Setter
public class CommercialTermPrice {
    /** 价格标识 */
    private Long termPriceId;

    /** 所属版本 */
    private Long termVersionId;

    /** 准确 OE */
    private String entityNo;

    /** ISO 4217 交易币种，即费用币种 */
    private String currency;

    /** 引用的 ACTIVE 定价画像代码 */
    private String pricingProfileCode;

    /** 同版本、OE 与币种下相同具体度候选的顺序，越小越优先 */
    private Integer priorityNo;

    /** T+N 工作日结算天数 */
    private Integer settlementBusinessDays;

    /** 结算业务日历代码 */
    private String businessCalendarCode;

    /** IANA 时区，本期仅 UTC */
    private String settlementTimezone;

    /** 日内截止秒数 0–86399 */
    private Integer cutoffSecondOfDay;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;

    /** 记录最近一次持久化更新的 UTC 微秒时间 */
    private Instant updateTime;
}
