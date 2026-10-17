package com.paytech.billing.test.builder;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.billing.domain.ChannelCostEstimateCommand;

/**
 * 渠道成本估算命令的模板：路由元组与 ChannelCostVersionRequestBuilder 标准范围逐值相同
 * <li>用例只覆盖与场景有关的维度、基数或发生时间；build 不修复刻意设置的非法值。</li>
 */
public final class ChannelCostEstimateCommandBuilder {
    private long channelId;

    private long midId = Long.parseLong(ChannelCostVersionRequestBuilder.MID_ID);

    private String entityNo = ChannelCostVersionRequestBuilder.ENTITY_NO;

    private String countryCode = ChannelCostVersionRequestBuilder.COUNTRY_CODE;

    private String currency = ChannelCostVersionRequestBuilder.CURRENCY;

    private String paymentMethodCode = ChannelCostVersionRequestBuilder.PAYMENT_METHOD;

    private String actionType = ChannelCostVersionRequestBuilder.ACTION_TYPE;

    private BigDecimal basisAmount;

    private Instant occurredAt = Instant.now();

    private ChannelCostEstimateCommandBuilder() {
    }

    /** 标准范围、发生时间为当前时间的模板。 */
    public static ChannelCostEstimateCommandBuilder template(long channelId, String basisAmount) {
        ChannelCostEstimateCommandBuilder builder = new ChannelCostEstimateCommandBuilder();
        builder.channelId = channelId;
        builder.basisAmount = new BigDecimal(basisAmount);
        return builder;
    }

    /** 覆盖渠道。 */
    public ChannelCostEstimateCommandBuilder channelId(long channelId) {
        this.channelId = channelId;
        return this;
    }

    /** 覆盖国家。 */
    public ChannelCostEstimateCommandBuilder countryCode(String countryCode) {
        this.countryCode = countryCode;
        return this;
    }

    /** 覆盖币种。 */
    public ChannelCostEstimateCommandBuilder currency(String currency) {
        this.currency = currency;
        return this;
    }

    /** 覆盖动作类别。 */
    public ChannelCostEstimateCommandBuilder actionType(String actionType) {
        this.actionType = actionType;
        return this;
    }

    /** 覆盖发生时间。 */
    public ChannelCostEstimateCommandBuilder occurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
        return this;
    }

    /** 返回命令。 */
    public ChannelCostEstimateCommand build() {
        return new ChannelCostEstimateCommand(channelId, midId, entityNo, countryCode, currency, paymentMethodCode,
            actionType, basisAmount, occurredAt);
    }
}
