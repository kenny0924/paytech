package com.paytech.billing.auto.mq;

import static com.paytech.billing.test.builder.PaymentFactConstants.CLEARING_ACCOUNT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.CLIENT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.CLIENT_PRICE_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.CLIENT_PRICING_VERSION_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.ENTITY_NO;
import static com.paytech.billing.test.builder.PaymentFactConstants.PAYMENT_ATTEMPT_ID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.billing.api.enums.CalculationMethod;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.enums.SourceRuleType;
import com.paytech.billing.ext.mq.OrderPaymentSucceededBillingHandler;
import com.paytech.billing.test.base.AbstractOrderPaymentSucceededTest;
import com.paytech.billing.test.builder.CommercialPriceComponentBuilder;
import com.paytech.billing.test.builder.CommercialPriceResponseBuilder;
import com.paytech.billing.test.builder.OrderPaymentSucceededEnvelopeBuilder;
import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.foundation.messaging.api.DomainEvent;

/**
 * Client 定价组件计算：阶梯（整体 / 累进）、固定金额、上下限、舍入边界、零金额组件与非 SALE 组件
 *
 * @see OrderPaymentSucceededBillingHandler#consume(DomainEvent)
 */
class OrderPaymentSucceededPricingTest extends AbstractOrderPaymentSucceededTest {
    /** 阶梯档位 ID 起点。 */
    private static final long BAND_ID = 59501L;

    /** 本类全部用例共用清算账户与渠道成本。 */
    @BeforeEach
    void setUpClassMocks() {
        mockChannel();
    }

    /** 1500.00：整体阶梯落在第二档按 2% 计 30.00；累进阶梯 1000 × 3% + 500 × 2% = 40.00；本金 1430.00。 */
    @Test
    void test_tieredWholeAndProgressive() {
        mockClientPrice(CommercialPriceResponseBuilder.clientPricing().components(
            CommercialPriceComponentBuilder.tiered(59001L, CommercialPriceComponentBuilder.WHOLE_BASE,
                CommercialPriceComponentBuilder.band(BAND_ID, 1, "0", "1000", "0.03"),
                CommercialPriceComponentBuilder.band(BAND_ID + 1, 2, "1000", null, "0.02")).build(),
            CommercialPriceComponentBuilder.tiered(59002L, CommercialPriceComponentBuilder.PROGRESSIVE,
                CommercialPriceComponentBuilder.band(BAND_ID + 2, 1, "0", "1000", "0.03"),
                CommercialPriceComponentBuilder.band(BAND_ID + 3, 2, "1000", null, "0.02")).build()).build());

        consumed(OrderPaymentSucceededEnvelopeBuilder.template().amount("1500.00").build());

        String clearing = Long.toString(CLEARING_ACCOUNT_ID);
        fact(PAYMENT_ATTEMPT_ID).lineCount(4)
            .line(1, FactComponentType.CLIENT_FEE, FactPartyRole.CHANNEL, clearing, FactPartyRole.OPERATING_ENTITY,
                ENTITY_NO, "30.00")
            .evidence(1, "1500.00", "0.02", "30.00", SourceRuleType.COMMERCIAL_PRICE, CLIENT_PRICING_VERSION_ID,
                CLIENT_PRICE_ID, 59001L)
            .evidenceField(1, "calculationMethod", CalculationMethod.TIERED_WHOLE_BASE.getCode())
            .evidenceField(1, "bandId", Long.toString(BAND_ID + 1))
            .line(2, FactComponentType.CLIENT_FEE, FactPartyRole.CHANNEL, clearing, FactPartyRole.OPERATING_ENTITY,
                ENTITY_NO, "40.00")
            .evidence(2, "1500.00", null, "40.00", SourceRuleType.COMMERCIAL_PRICE, CLIENT_PRICING_VERSION_ID,
                CLIENT_PRICE_ID, 59002L)
            .evidenceField(2, "calculationMethod", CalculationMethod.TIERED_PROGRESSIVE.getCode())
            .line(3, FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.CHANNEL, clearing, FactPartyRole.CLIENT,
                Long.toString(CLIENT_ID), "1430.00")
            .line(4, FactComponentType.CHANNEL_COST, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CHANNEL,
                clearing, "7.50");
    }

    /**
     * 固定 0.30、1.235% 原始 1.235 按 HALF_UP 记 1.24、5% 受上限 2.00 约束；0% 组件记零不生成行、REFUND 触发的组件不计算；
     * 手续费合计 3.54，本金 96.46。
     */
    @Test
    void test_fixedMaxRoundingAndZero() {
        mockClientPrice(CommercialPriceResponseBuilder.clientPricing().components(
            CommercialPriceComponentBuilder.fixed(59001L, "0.30").build(),
            CommercialPriceComponentBuilder.percentage(59002L, "0.01235").build(),
            CommercialPriceComponentBuilder.percentage(59003L, "0.05").max("2.00").build(),
            CommercialPriceComponentBuilder.percentage(59004L, "0").build(),
            CommercialPriceComponentBuilder.percentage(59005L, "0.5").trigger(CommercialCodes.FeeEvent.REFUND).build())
            .build());

        consumed(OrderPaymentSucceededEnvelopeBuilder.template().build());

        String clearing = Long.toString(CLEARING_ACCOUNT_ID);
        fact(PAYMENT_ATTEMPT_ID).lineCount(5)
            .line(1, FactComponentType.CLIENT_FEE, FactPartyRole.CHANNEL, clearing, FactPartyRole.OPERATING_ENTITY,
                ENTITY_NO, "0.30")
            .evidence(1, "100.00", null, "0.30", SourceRuleType.COMMERCIAL_PRICE, CLIENT_PRICING_VERSION_ID,
                CLIENT_PRICE_ID, 59001L)
            .evidenceField(1, "calculationMethod", CalculationMethod.FIXED.getCode())
            .line(2, FactComponentType.CLIENT_FEE, FactPartyRole.CHANNEL, clearing, FactPartyRole.OPERATING_ENTITY,
                ENTITY_NO, "1.24")
            .evidence(2, "100.00", "0.01235", "1.235", SourceRuleType.COMMERCIAL_PRICE, CLIENT_PRICING_VERSION_ID,
                CLIENT_PRICE_ID, 59002L)
            .line(3, FactComponentType.CLIENT_FEE, FactPartyRole.CHANNEL, clearing, FactPartyRole.OPERATING_ENTITY,
                ENTITY_NO, "2.00")
            .evidence(3, "100.00", "0.05", "2.00", SourceRuleType.COMMERCIAL_PRICE, CLIENT_PRICING_VERSION_ID,
                CLIENT_PRICE_ID, 59003L)
            .evidenceField(3, "maxAmount", "2.00")
            .line(4, FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.CHANNEL, clearing, FactPartyRole.CLIENT,
                Long.toString(CLIENT_ID), "96.46")
            .line(5, FactComponentType.CHANNEL_COST, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CHANNEL,
                clearing, "0.50");
    }
}
