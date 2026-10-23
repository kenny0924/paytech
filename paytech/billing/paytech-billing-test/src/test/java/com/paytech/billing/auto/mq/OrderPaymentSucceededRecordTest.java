package com.paytech.billing.auto.mq;

import static com.paytech.billing.test.builder.PaymentFactConstants.CARD_BRAND;
import static com.paytech.billing.test.builder.PaymentFactConstants.CARD_LEVEL;
import static com.paytech.billing.test.builder.PaymentFactConstants.CHANNEL_ACCOUNT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.CHANNEL_REFERENCE_TYPE;
import static com.paytech.billing.test.builder.PaymentFactConstants.CHANNEL_TRANSACTION_REF;
import static com.paytech.billing.test.builder.PaymentFactConstants.CHANNEL_TRANSACTION_REF_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.CLEARING_ACCOUNT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.CLIENT_AGREEMENT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.CLIENT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.CLIENT_PRICE_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.CLIENT_PRICING_VERSION_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.COMMERCIAL_CONTEXT_TIME;
import static com.paytech.billing.test.builder.PaymentFactConstants.CONSUMER_COUNTRY;
import static com.paytech.billing.test.builder.PaymentFactConstants.COST_COMPONENT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.COST_VER_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.DEFAULT_CALENDAR;
import static com.paytech.billing.test.builder.PaymentFactConstants.END_OF_DAY_CUTOFF;
import static com.paytech.billing.test.builder.PaymentFactConstants.ENTITY_NO;
import static com.paytech.billing.test.builder.PaymentFactConstants.FINANCE_ENTITY_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.FUNDING_TYPE;
import static com.paytech.billing.test.builder.PaymentFactConstants.LOCAL_PARTNER_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.MID_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.ORDER_CONTEXT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.ORDER_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.ORDER_VERSION;
import static com.paytech.billing.test.builder.PaymentFactConstants.PARTNER_AGREEMENT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.PARTNER_AGREEMENT_OE_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.PARTNER_SHARE_PRICE_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.PARTNER_SHARE_VERSION_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.PAYMENT_ATTEMPT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.PAYMENT_INTENT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.PAYMENT_METHOD;
import static com.paytech.billing.test.builder.PaymentFactConstants.PAYMENT_SUCCEEDED_TIME;
import static com.paytech.billing.test.builder.PaymentFactConstants.PLATFORM_CHARGE_VERSION_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.SERVICE_CHARGE_PRICE_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.TAX_SNAPSHOT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.USD;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.api.enums.CalculationMethod;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactOriginType;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.api.enums.SourceRuleType;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.billing.ext.mq.OrderPaymentSucceededBillingHandler;
import com.paytech.billing.test.base.AbstractOrderPaymentSucceededTest;
import com.paytech.billing.test.builder.ChannelCostResponseBuilder;
import com.paytech.billing.test.builder.CommercialPriceResponseBuilder;
import com.paytech.billing.test.builder.CommercialPriceComponentBuilder;
import com.paytech.billing.test.builder.OrderPaymentSucceededEnvelopeBuilder;
import com.paytech.billing.test.mocker.external.CommercialPriceMocker;
import com.paytech.billing.test.mocker.external.DtoResponseMocker;
import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.CalculationBase;
import com.paytech.commercial.api.enums.PricingDimension;
import com.paytech.commercial.api.price.request.ResolveCommercialPriceRequestBody;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.messaging.api.DomainEvent;

/**
 * OrderPaymentSucceeded 登记自动 PAYMENT 事实：无 Partner 与 Partner 来源的完整事实、上游命中参数、重投与并发幂等、上游恢复后重投成功
 *
 * @see OrderPaymentSucceededBillingHandler#consume(DomainEvent)
 */
class OrderPaymentSucceededRecordTest extends AbstractOrderPaymentSucceededTest {
    /** T+1：周三成功 → 周四日终（UTC）。 */
    private static final Instant NEXT_BUSINESS_DAY_END = Instant.parse("2026-10-22T23:59:59Z");

    @Autowired
    private OrderPaymentSucceededBillingHandler handler;

    /** 无 Partner：手续费 2.9% 低于最低 3.00 取下限，本金 97.00，渠道成本 0.50；九类引用（税务快照只记引用）、定价输入、T+1 与事件发布完整。 */
    @Test
    void test_platformPaymentRecorded() {
        mockPlatformDefaults();

        consumed(OrderPaymentSucceededEnvelopeBuilder.template().build());

        String clearing = Long.toString(CLEARING_ACCOUNT_ID);
        long factId = fact(PAYMENT_ATTEMPT_ID).autoApproved(ORDER_VERSION).header(CLIENT_ID, ENTITY_NO, USD, "100.00", null, null)
            .times(PAYMENT_SUCCEEDED_TIME, NEXT_BUSINESS_DAY_END).refs(paymentRefs(), CHANNEL_REFERENCE_TYPE,
                CHANNEL_TRANSACTION_REF)
            .lineCount(3)
            .line(1, FactComponentType.CLIENT_FEE, FactPartyRole.CHANNEL, clearing, FactPartyRole.OPERATING_ENTITY,
                ENTITY_NO, "3.00")
            .evidence(1, "100.00", "0.029", "3.00", SourceRuleType.COMMERCIAL_PRICE, CLIENT_PRICING_VERSION_ID,
                CLIENT_PRICE_ID, CLIENT_FEE_COMPONENT_ID)
            .evidenceField(1, "termType", CommercialCodes.TermType.CLIENT_PRICING).evidenceField(1, "minAmount", "3.00")
            .evidenceField(1, "calculationMethod", CalculationMethod.PERCENTAGE.getCode()).evidenceField(1, "bandId", null)
            .line(2, FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.CHANNEL, clearing, FactPartyRole.CLIENT,
                Long.toString(CLIENT_ID), "97.00")
            .evidence(2, "100.00", null, "97.00", SourceRuleType.COMMERCIAL_PRICE, CLIENT_PRICING_VERSION_ID,
                CLIENT_PRICE_ID, null)
            .line(3, FactComponentType.CHANNEL_COST, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CHANNEL,
                clearing, "0.50")
            .evidence(3, "100.00", ChannelCostResponseBuilder.DEFAULT_RATE, "0.50", SourceRuleType.CHANNEL_COST,
                COST_VER_ID, null, COST_COMPONENT_ID)
            .evidenceField(3, "contentHash", ChannelCostResponseBuilder.CONTENT_HASH)
            .pricing("client_agreement_id", CLIENT_AGREEMENT_ID).pricing("partner_agreement_oe_id", null)
            .pricing("commercial_context_time", COMMERCIAL_CONTEXT_TIME).pricing("consumer_country_code", CONSUMER_COUNTRY)
            .pricing("payment_method", PAYMENT_METHOD).pricing("card_network", CARD_BRAND)
            .pricing("funding_type", FUNDING_TYPE).pricing("card_level", CARD_LEVEL)
            .pricing("authentication_class", null).pricing("commercial_price_id", CLIENT_PRICE_ID)
            .pricing("settlement_business_days", CommercialPriceResponseBuilder.DEFAULT_SETTLEMENT_DAYS)
            .pricing("business_calendar_code", DEFAULT_CALENDAR)
            .pricing("settlement_timezone", CommercialPriceResponseBuilder.UTC)
            .pricing("cutoff_second_of_day", END_OF_DAY_CUTOFF).factId();

        BillingFactPayload published = events.awaitEnvelope(factId).envelope(factId, 1, "main").payloadShape().payload();
        assertAll("BillingFactRecorded", () -> assertEquals(FactOriginType.AUTO.getCode(), published.getOriginType()),
            () -> assertEquals(BillingFactStatus.APPROVED.getCode(), published.getStatus()),
            () -> assertEquals(3, published.getLines().size()),
            () -> assertEquals(NEXT_BUSINESS_DAY_END, published.getBalanceAvailableTime()));
        assertClientPricingRequest(commercialPrices.onlyRequest(CommercialCodes.TermType.CLIENT_PRICING));
        financeEntities.assertNoCalls();
    }

    /** Partner 来源：分润按 Client 手续费 80%、平台服务费按毛额 2%，事实头带 Local Partner 与 Finance Entity。 */
    @Test
    void test_partnerPaymentRecorded() {
        mockPartnerDefaults();

        consumed(OrderPaymentSucceededEnvelopeBuilder.template().partner().build());

        String clearing = Long.toString(CLEARING_ACCOUNT_ID);
        long factId = fact(PAYMENT_ATTEMPT_ID).autoApproved(ORDER_VERSION)
            .header(CLIENT_ID, ENTITY_NO, USD, "100.00", LOCAL_PARTNER_ID, FINANCE_ENTITY_ID).lineCount(5)
            .line(1, FactComponentType.CLIENT_FEE, FactPartyRole.CHANNEL, clearing, FactPartyRole.OPERATING_ENTITY,
                ENTITY_NO, "10.00")
            .line(2, FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.CHANNEL, clearing, FactPartyRole.CLIENT,
                Long.toString(CLIENT_ID), "90.00")
            .line(3, FactComponentType.PARTNER_SHARE, FactPartyRole.OPERATING_ENTITY, ENTITY_NO,
                FactPartyRole.LOCAL_PARTNER, Long.toString(LOCAL_PARTNER_ID), "8.00")
            .evidence(3, "10.00", "0.80", "8.00", SourceRuleType.PARTNER_SHARE, PARTNER_SHARE_VERSION_ID,
                PARTNER_SHARE_PRICE_ID, PARTNER_SHARE_COMPONENT_ID)
            .evidenceField(3, "calculationBase", CalculationBase.ACTUAL_CLIENT_FEE.getCode())
            .line(4, FactComponentType.PLATFORM_SERVICE_CHARGE, FactPartyRole.OPERATING_ENTITY, ENTITY_NO,
                FactPartyRole.FINANCE_ENTITY, Long.toString(FINANCE_ENTITY_ID), "2.00")
            .evidence(4, "100.00", "0.02", "2.00", SourceRuleType.PLATFORM_SERVICE_CHARGE, PLATFORM_CHARGE_VERSION_ID,
                SERVICE_CHARGE_PRICE_ID, SERVICE_CHARGE_COMPONENT_ID)
            .line(5, FactComponentType.CHANNEL_COST, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CHANNEL,
                clearing, "0.50")
            .pricing("partner_agreement_oe_id", PARTNER_AGREEMENT_OE_ID).factId();

        events.awaitEnvelope(factId).envelope(factId, 1, "main");
        for (String termType : List.of(CommercialCodes.TermType.PARTNER_SHARE,
            CommercialCodes.TermType.PLATFORM_SERVICE_CHARGE)) {
            ResolveCommercialPriceRequestBody request = commercialPrices.onlyRequest(termType);
            assertAll("Partner 条款命中参数 " + termType,
                () -> assertEquals(Long.toString(LOCAL_PARTNER_ID), request.getLocalPartnerId()),
                () -> assertNull(request.getClientId()),
                () -> assertEquals(Long.toString(PARTNER_AGREEMENT_ID), request.getAgreementId()),
                () -> assertEquals(Long.toString(PAYMENT_ATTEMPT_ID), request.getPaymentFactId()));
        }
        assertEquals(Long.toString(PARTNER_SHARE_VERSION_ID),
            commercialPrices.onlyRequest(CommercialCodes.TermType.PARTNER_SHARE).getTermVersionId());
    }

    /** 卡属性未知：命中只传币种与消费者国家两个维度（缺键表示事实未知），定价输入的卡属性为空。 */
    @Test
    void test_unknownCardAttributesNotSent() {
        mockPlatformDefaults();

        consumed(OrderPaymentSucceededEnvelopeBuilder.template().set("cardBrand", null).set("fundingType", null)
            .set("cardLevel", null).build());

        fact(PAYMENT_ATTEMPT_ID).pricing("card_network", null).pricing("funding_type", null).pricing("card_level", null);
        assertEquals(Map.of(PricingDimension.CURRENCY.getCode(), USD, PricingDimension.CONSUMER_COUNTRY.getCode(),
            CONSUMER_COUNTRY), commercialPrices.onlyRequest(CommercialCodes.TermType.CLIENT_PRICING)
                .getPaymentFacts().getDimensions());
    }

    /** 同一支付尝试以更高版本重投：命中原事实返回，不再读取上游、不重复登记与发布。 */
    @Test
    void test_redeliveredEventReturnsOriginal() {
        mockPlatformDefaults();
        consumed(OrderPaymentSucceededEnvelopeBuilder.template().build());

        consumed(OrderPaymentSucceededEnvelopeBuilder.template().aggregateVersion(4).build());

        long factId = fact(PAYMENT_ATTEMPT_ID).autoApproved(ORDER_VERSION).factId();
        events.awaitEnvelope(factId);
        assertAll("重投不读取上游", () -> assertEquals(1, commercialPrices.calls(clientPricingKey())),
            () -> assertEquals(1, testMapper.countFacts()));
    }

    /** 两个实例并发处理同一事件：只有一条事实、一次发布；落败者以并发冲突交由重投。 */
    @Test
    void test_concurrentDeliveriesRecordOnce() throws Exception {
        mockPlatformDefaults();
        DomainEvent event = OrderPaymentSucceededEnvelopeBuilder.template().build();
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                futures.add(executor.submit(() -> consumeAfter(start, event, failures)));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertTrue(failures.size() <= 1, "至多一个并发处理失败: " + failures);
        failures.forEach(failure -> assertInstanceOf(ConcurrentRequestException.class, failure));
        long factId = fact(PAYMENT_ATTEMPT_ID).factId();
        events.awaitEnvelope(factId);
    }

    /** Client 定价版本先 404 后补齐：首次投递失败重投，第二次成功登记。 */
    @Test
    void test_upstreamRecoversOnRedelivery() {
        mockChannel();
        commercialPrices.mockSequence(clientPricingKey(),
            DtoResponseMocker.problem(HttpStatus.NOT_FOUND, CommonErrorCode.NOT_FOUND.getCode()),
            CommercialPriceResponseBuilder.clientPricing().components(
                CommercialPriceComponentBuilder.percentage(CLIENT_FEE_COMPONENT_ID, "0.029").build()).build());

        consumed(OrderPaymentSucceededEnvelopeBuilder.template().build());

        long factId = fact(PAYMENT_ATTEMPT_ID).autoApproved(ORDER_VERSION).factId();
        assertEquals(2, commercialPrices.calls(clientPricingKey()));
        events.awaitEnvelope(factId);
    }

    /** 在起跑信号后直接调用真实 Handler，记录异常。 */
    private void consumeAfter(CountDownLatch start, DomainEvent event, List<Throwable> failures) {
        try {
            start.await();
            handler.consume(event);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            failures.add(interrupted);
        } catch (RuntimeException failure) {
            failures.add(failure);
        }
    }

    /** Client 定价命中参数：冻结版本、合同、OE、商业时点、Client Owner、支付事实与五个维度。 */
    private static void assertClientPricingRequest(ResolveCommercialPriceRequestBody request) {
        assertAll("Client 定价命中参数",
            () -> assertEquals(Long.toString(CLIENT_PRICING_VERSION_ID), request.getTermVersionId()),
            () -> assertEquals(Long.toString(CLIENT_AGREEMENT_ID), request.getAgreementId()),
            () -> assertEquals(ENTITY_NO, request.getEntityNo()), () -> assertEquals(USD, request.getCurrency()),
            () -> assertEquals(COMMERCIAL_CONTEXT_TIME.toString(), request.getContextAsOfTime()),
            () -> assertEquals(Long.toString(CLIENT_ID), request.getClientId()),
            () -> assertNull(request.getLocalPartnerId()),
            () -> assertEquals(Long.toString(PAYMENT_ATTEMPT_ID), request.getPaymentFactId()),
            () -> assertEquals(PAYMENT_METHOD, request.getPaymentFacts().getPaymentMethodCode()),
            () -> assertEquals(Map.of(PricingDimension.CURRENCY.getCode(), USD,
                PricingDimension.CONSUMER_COUNTRY.getCode(), CONSUMER_COUNTRY,
                PricingDimension.CARD_BRAND.getCode(), CARD_BRAND, PricingDimension.FUNDING_TYPE.getCode(), FUNDING_TYPE,
                PricingDimension.CARD_LEVEL.getCode(), CARD_LEVEL), request.getPaymentFacts().getDimensions()));
    }

    /** Client 定价的 Mock 匹配标识。 */
    private static String clientPricingKey() {
        return CommercialPriceMocker.key(CommercialCodes.TermType.CLIENT_PRICING, CLIENT_PRICING_VERSION_ID);
    }

    /** 九类引用的期望标识（含税务快照）。 */
    private static Map<FactRefType, String> paymentRefs() {
        return Map.ofEntries(Map.entry(FactRefType.TAX_SNAPSHOT, Long.toString(TAX_SNAPSHOT_ID)),
            Map.entry(FactRefType.ORDER, Long.toString(ORDER_ID)),
            Map.entry(FactRefType.ORDER_CONTEXT, Long.toString(ORDER_CONTEXT_ID)),
            Map.entry(FactRefType.PAYMENT_INTENT, Long.toString(PAYMENT_INTENT_ID)),
            Map.entry(FactRefType.PAYMENT_ATTEMPT, Long.toString(PAYMENT_ATTEMPT_ID)),
            Map.entry(FactRefType.PAYMENT_CHANNEL_ACCOUNT, Long.toString(CHANNEL_ACCOUNT_ID)),
            Map.entry(FactRefType.MID, Long.toString(MID_ID)),
            Map.entry(FactRefType.CLEARING_ACCOUNT, Long.toString(CLEARING_ACCOUNT_ID)),
            Map.entry(FactRefType.CHANNEL_TRANSACTION_REF, Long.toString(CHANNEL_TRANSACTION_REF_ID)));
    }
}
