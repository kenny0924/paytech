package com.paytech.billing.auto.mq;

import static com.paytech.billing.test.builder.PaymentFactConstants.CLIENT_PRICING_VERSION_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.EUR;
import static com.paytech.billing.test.builder.PaymentFactConstants.MID_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.ORDER_VERSION;
import static com.paytech.billing.test.builder.PaymentFactConstants.PAYMENT_ATTEMPT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.billing.ext.mq.OrderPaymentSucceededBillingHandler;
import com.paytech.billing.test.base.AbstractOrderPaymentSucceededTest;
import com.paytech.billing.test.builder.ChannelCostResponseBuilder;
import com.paytech.billing.test.builder.ClearingAccountResponseBuilder;
import com.paytech.billing.test.builder.CommercialPriceComponentBuilder;
import com.paytech.billing.test.builder.CommercialPriceResponseBuilder;
import com.paytech.billing.test.builder.OrderPaymentSucceededEnvelopeBuilder;
import com.paytech.billing.test.mocker.external.CommercialPriceMocker;
import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.BandBasis;
import com.paytech.commercial.api.enums.CalculationBase;
import com.paytech.commercial.api.price.response.CommercialPriceComponentResponseBody;
import com.paytech.commercial.api.price.response.CommercialPriceResponseBody;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventRejectedException;

/**
 * 数据不一致、定价不可表达与信封非法：不落事实、不发事件，第一次投递即进入 DLQ（技术方案 4.8，S4、S9、S10）
 * <li>以上游最多被调用一次证明没有重投。</li>
 *
 * @see OrderPaymentSucceededBillingHandler#consume(DomainEvent)
 */
class OrderPaymentSucceededRejectTest extends AbstractOrderPaymentSucceededTest {

    @Autowired
    private OrderPaymentSucceededBillingHandler handler;

    /** 收取金额与订单金额不一致：在读取任何上游前拒绝。 */
    @Test
    void test_capturedAmountMismatch() {
        assertRejectedBeforeUpstream(OrderPaymentSucceededEnvelopeBuilder.template().set("capturedAmount", "99.00").build());
    }

    /** Local Partner 来源缺平台签约 Party。 */
    @Test
    void test_partnerTermsIncomplete() {
        assertRejectedBeforeUpstream(OrderPaymentSucceededEnvelopeBuilder.template().partner()
            .set("platformServicePartyId", null).build());
    }

    /** 渠道交易引用三项缺一（S4）。 */
    @Test
    void test_channelReferenceMissing() {
        assertRejectedBeforeUpstream(OrderPaymentSucceededEnvelopeBuilder.template().set("channelTransactionRefId", null)
            .build());
    }

    /** 订单未冻结消费者国家。 */
    @Test
    void test_consumerCountryMissing() {
        assertRejectedBeforeUpstream(OrderPaymentSucceededEnvelopeBuilder.template().set("consumerCountryCode", null)
            .build());
    }

    /** 清算账户币种与订单不一致。 */
    @Test
    void test_clearingAccountCurrencyMismatch() {
        mockClientPrice(price(CommercialPriceComponentBuilder.percentage(CLIENT_FEE_COMPONENT_ID, "0.029").build()));
        channelCosts.mock(Long.toString(MID_ID), ChannelCostResponseBuilder.template().build());
        clearingAccounts.mock(Long.toString(MID_ID), ClearingAccountResponseBuilder.template().currency(EUR).build());

        deadLettered(OrderPaymentSucceededEnvelopeBuilder.template().build());

        assertNoFact();
        assertEquals(1, clearingAccounts.calls(Long.toString(MID_ID)), "数据不一致不应重投");
    }

    /** BUY_RATE_DIFFERENCE 基数本期不支持（S10）。 */
    @Test
    void test_buyRateDifferenceRejected() {
        assertPricingRejected(CommercialPriceComponentBuilder.percentage(CLIENT_FEE_COMPONENT_ID, "0.01")
            .base(CalculationBase.BUY_RATE_DIFFERENCE).build());
    }

    /** PERIOD_VOLUME 阶梯本期不支持（S10）。 */
    @Test
    void test_periodVolumeRejected() {
        assertPricingRejected(CommercialPriceComponentBuilder.tiered(CLIENT_FEE_COMPONENT_ID,
            CommercialPriceComponentBuilder.WHOLE_BASE, CommercialPriceComponentBuilder.band(59501L, 1, "0", null, "0.02"))
            .bandBasis(BandBasis.PERIOD_VOLUME).build());
    }

    /** FIXED 组件币种与事实不同（S9）。 */
    @Test
    void test_fixedCurrencyMismatch() {
        assertPricingRejected(CommercialPriceComponentBuilder.fixed(CLIENT_FEE_COMPONENT_ID, "0.30").currency(EUR).build());
    }

    /** Client 定价引用 Client 手续费基数为自引用（S10）。 */
    @Test
    void test_clientPricingSelfReference() {
        assertPricingRejected(CommercialPriceComponentBuilder.percentage(CLIENT_FEE_COMPONENT_ID, "0.10")
            .base(CalculationBase.ACTUAL_CLIENT_FEE).build());
    }

    /** Client 手续费合计为零（唯一 SALE 组件费率为 0，REFUND 组件不计）：ledger 要求手续费行，直接拒绝（S14）。 */
    @Test
    void test_zeroClientFeeRejected() {
        mockChannel();
        mockClientPrice(CommercialPriceResponseBuilder.clientPricing().components(
            CommercialPriceComponentBuilder.percentage(CLIENT_FEE_COMPONENT_ID, "0").build(),
            CommercialPriceComponentBuilder.percentage(CLIENT_FEE_COMPONENT_ID + 1, "0.10")
                .trigger(CommercialCodes.FeeEvent.REFUND).build()).build());

        deadLettered(OrderPaymentSucceededEnvelopeBuilder.template().build());

        assertNoFact();
        assertEquals(1, commercialPrices.calls(
            CommercialPriceMocker.key(CommercialCodes.TermType.CLIENT_PRICING, CLIENT_PRICING_VERSION_ID)), "不应重投");
    }

    /** 同一支付尝试已登记后，内容不同的事件（更高版本）被拒绝，原事实保留。 */
    @Test
    void test_sameAttemptDifferentContent() {
        mockChannel();
        mockClientPrice(price(CommercialPriceComponentBuilder.percentage(CLIENT_FEE_COMPONENT_ID, "0.029").build()));
        consumed(OrderPaymentSucceededEnvelopeBuilder.template().build());
        long factId = fact(PAYMENT_ATTEMPT_ID).factId();
        events.awaitEnvelope(factId);

        deadLettered(OrderPaymentSucceededEnvelopeBuilder.template().aggregateVersion(ORDER_VERSION + 1)
            .set("channelTransactionRef", "NI-TXN-OTHER").build());

        assertEquals(factId, fact(PAYMENT_ATTEMPT_ID).autoApproved(ORDER_VERSION).factId());
        assertEquals(1, testMapper.countFacts());
    }

    /** Schema 版本不是 1：信封非法。 */
    @Test
    void test_unsupportedSchemaVersion() {
        assertRejectedBeforeUpstream(OrderPaymentSucceededEnvelopeBuilder.template().schemaVersion(2).build());
    }

    /** 缺必填字段：信封非法。 */
    @Test
    void test_missingRequiredField() {
        assertRejectedBeforeUpstream(OrderPaymentSucceededEnvelopeBuilder.template().remove("midId").build());
    }

    /** 发布方不是 acquiring：Handler 直接拒绝（经 Broker 时 paytech-messaging 先按发布方不符拒绝）。 */
    @Test
    void test_unexpectedSourceService() {
        DomainEvent event = OrderPaymentSucceededEnvelopeBuilder.template().sourceService("payment").build();

        assertThrows(DomainEventRejectedException.class, () -> handler.consume(event));

        assertNoFact();
        commercialPrices.assertNoCalls();
    }

    /** 以给定 Client 价格组件登记：计算阶段拒绝，第一次投递即进入 DLQ，未读取渠道成本与清算账户之后的结果。 */
    private void assertPricingRejected(CommercialPriceComponentResponseBody component) {
        mockChannel();
        mockClientPrice(price(component));

        deadLettered(OrderPaymentSucceededEnvelopeBuilder.template().build());

        assertNoFact();
        assertEquals(1, commercialPrices.calls(
            CommercialPriceMocker.key(CommercialCodes.TermType.CLIENT_PRICING, CLIENT_PRICING_VERSION_ID)), "不应重投");
    }

    /** 事件本身不合法或内部不一致：直接进入 DLQ，不调用任何上游。 */
    private void assertRejectedBeforeUpstream(DomainEvent event) {
        deadLettered(event);

        assertNoFact();
        commercialPrices.assertNoCalls();
        channelCosts.assertNoCalls();
        clearingAccounts.assertNoCalls();
        financeEntities.assertNoCalls();
    }

    /** 只含给定组件的 Client 价格。 */
    private static CommercialPriceResponseBody price(CommercialPriceComponentResponseBody component) {
        return CommercialPriceResponseBuilder.clientPricing().components(component).build();
    }
}
