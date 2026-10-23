package com.paytech.billing.auto.mq;

import static com.paytech.billing.test.builder.PaymentFactConstants.CLIENT_PRICING_VERSION_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.DEFAULT_CALENDAR;
import static com.paytech.billing.test.builder.PaymentFactConstants.END_OF_DAY_CUTOFF;
import static com.paytech.billing.test.builder.PaymentFactConstants.MID_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.PLATFORM_SERVICE_PARTY_ID;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.paytech.billing.ext.mq.OrderPaymentSucceededBillingHandler;
import com.paytech.billing.test.base.AbstractOrderPaymentSucceededTest;
import com.paytech.billing.test.builder.ChannelCostResponseBuilder;
import com.paytech.billing.test.builder.CommercialPriceComponentBuilder;
import com.paytech.billing.test.builder.CommercialPriceResponseBuilder;
import com.paytech.billing.test.builder.OrderPaymentSucceededEnvelopeBuilder;
import com.paytech.billing.test.mocker.external.CommercialPriceMocker;
import com.paytech.billing.test.mocker.external.DtoResponseMocker;
import com.paytech.channel.connector.api.enums.ChannelCostErrorCode;
import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.CommercialPriceErrorCode;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.reconciliation.api.enums.ReconciliationErrorCode;

/**
 * 上游未就绪或配置缺口：不落事实、不发事件，消息退避重投直到投递上限进入 DLQ（技术方案 4.8）
 * <li>以上游被调用不止一次证明发生了重投（直接拒绝只调用一次）。</li>
 *
 * @see OrderPaymentSucceededBillingHandler#consume(DomainEvent)
 */
class OrderPaymentSucceededRetryTest extends AbstractOrderPaymentSucceededTest {

    /** Client 定价版本不存在（404）。 */
    @Test
    void test_clientPriceVersionNotFound() {
        assertClientPriceRetried(DtoResponseMocker.problem(HttpStatus.NOT_FOUND, CommonErrorCode.NOT_FOUND.getCode()));
    }

    /** 定价事实不完整（422）。 */
    @Test
    void test_pricingFactsIncomplete() {
        assertClientPriceRetried(DtoResponseMocker.problem(HttpStatus.UNPROCESSABLE_CONTENT,
            CommercialPriceErrorCode.PRICING_FACTS_INCOMPLETE.getCode()));
    }

    /** 价格命中歧义（409）。 */
    @Test
    void test_priceAmbiguous() {
        assertClientPriceRetried(
            DtoResponseMocker.problem(HttpStatus.CONFLICT, CommercialPriceErrorCode.PRICE_AMBIGUOUS.getCode()));
    }

    /** commercial 不可用（503）。 */
    @Test
    void test_commercialUnavailable() {
        assertClientPriceRetried(
            DtoResponseMocker.problem(HttpStatus.SERVICE_UNAVAILABLE, CommonErrorCode.SERVICE_UNAVAILABLE.getCode()));
    }

    /** MID 未归属清算账户（400）。 */
    @Test
    void test_clearingAccountNotResolved() {
        mockClientPrice(standardPrice().build());
        channelCosts.mock(Long.toString(MID_ID), ChannelCostResponseBuilder.template().build());
        clearingAccounts.mockFault(Long.toString(MID_ID), DtoResponseMocker.problem(HttpStatus.BAD_REQUEST,
            ReconciliationErrorCode.CLEARING_ACCOUNT_NOT_RESOLVED.getCode()));

        deadLettered(OrderPaymentSucceededEnvelopeBuilder.template().build());

        assertNoFact();
        assertRetried(clearingAccounts.calls(Long.toString(MID_ID)));
    }

    /** 渠道成本未命中（400）。 */
    @Test
    void test_channelCostNotResolved() {
        mockClientPrice(standardPrice().build());
        channelCosts.mockFault(Long.toString(MID_ID), DtoResponseMocker.problem(HttpStatus.BAD_REQUEST,
            ChannelCostErrorCode.CHANNEL_COST_NOT_RESOLVED.getCode()));

        deadLettered(OrderPaymentSucceededEnvelopeBuilder.template().build());

        assertNoFact();
        assertRetried(channelCosts.calls(Long.toString(MID_ID)));
        clearingAccounts.assertNoCalls();
    }

    /** Partner 订单的平台签约 Party 没有 ACTIVE Finance Entity（404）。 */
    @Test
    void test_financeEntityNotFound() {
        mockPartnerDefaults();
        financeEntities.clear();
        financeEntities.mockFault(Long.toString(PLATFORM_SERVICE_PARTY_ID), DtoResponseMocker
            .problem(HttpStatus.NOT_FOUND, LedgerSetupErrorCode.FINANCE_ENTITY_NOT_FOUND.getCode()));

        deadLettered(OrderPaymentSucceededEnvelopeBuilder.template().partner().build());

        assertNoFact();
        assertRetried(financeEntities.calls(Long.toString(PLATFORM_SERVICE_PARTY_ID)));
        channelCosts.assertNoCalls();
    }

    /** 命中价格引用的日历不在本服务日历副本中。 */
    @Test
    void test_calendarMissing() {
        mockChannel();
        mockClientPrice(standardPrice().settlement(1, "NO_SUCH_CALENDAR", CommercialPriceResponseBuilder.UTC,
            END_OF_DAY_CUTOFF).build());

        deadLettered(OrderPaymentSucceededEnvelopeBuilder.template().build());

        assertNoFact();
        assertRetried(clearingAccounts.calls(Long.toString(MID_ID)));
    }

    /** 命中价格缺少结算字段（截止秒为空）。 */
    @Test
    void test_settlementFieldMissing() {
        mockChannel();
        mockClientPrice(standardPrice().settlement(1, DEFAULT_CALENDAR, CommercialPriceResponseBuilder.UTC, null).build());

        deadLettered(OrderPaymentSucceededEnvelopeBuilder.template().build());

        assertNoFact();
        assertRetried(clearingAccounts.calls(Long.toString(MID_ID)));
    }

    /** Client 定价以给定故障持续失败：重投直到进入 DLQ，之后的上游没有调用。 */
    private void assertClientPriceRetried(MockHttpFault fault) {
        String key = CommercialPriceMocker.key(CommercialCodes.TermType.CLIENT_PRICING, CLIENT_PRICING_VERSION_ID);
        commercialPrices.mockFault(key, fault);

        deadLettered(OrderPaymentSucceededEnvelopeBuilder.template().build());

        assertNoFact();
        assertRetried(commercialPrices.calls(key));
        channelCosts.assertNoCalls();
        clearingAccounts.assertNoCalls();
    }

    /** 默认 Client 价格：2.9%。 */
    private static CommercialPriceResponseBuilder standardPrice() {
        return CommercialPriceResponseBuilder.clientPricing()
            .components(CommercialPriceComponentBuilder.percentage(CLIENT_FEE_COMPONENT_ID, "0.029").build());
    }

    /** 上游被调用不止一次，说明消息经过了重投而不是直接拒绝。 */
    private static void assertRetried(int calls) {
        assertTrue(calls > 1, "应重投后才进入 DLQ，实际调用 " + calls + " 次");
    }
}
