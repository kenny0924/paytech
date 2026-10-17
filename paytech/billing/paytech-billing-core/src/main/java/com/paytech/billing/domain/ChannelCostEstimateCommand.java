package com.paytech.billing.domain;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 预计渠道成本估算命令：Payment 已冻结的渠道路由元组、计费基数与业务发生时间。 */
@Getter
@AllArgsConstructor
public class ChannelCostEstimateCommand {
    /** Payment Channel 稳定业务 ID，用于定位唯一成本方案。 */
    private final long channelId;

    /** Payment Channel MID 稳定业务 ID。 */
    private final long midId;

    /** 交易冻结的 CCT Operating Entity 编号。 */
    private final String entityNo;

    /** ISO 3166-1 alpha-2 国家代码。 */
    private final String countryCode;

    /** ISO 4217 交易币种，也是成本金额的币种。 */
    private final String currency;

    /** Payment 支付方式目录代码。 */
    private final String paymentMethodCode;

    /** Payment 支付、退款或争议动作类别。 */
    private final String actionType;

    /** 非负计费基数，小数位不超过币种精度。 */
    private final BigDecimal basisAmount;

    /** 业务发生时间，用于命中当时生效的成本版本。 */
    private final Instant occurredAt;
}
