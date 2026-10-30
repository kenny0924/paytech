package com.paytech.acquiring.auto.ext.job;

import static com.paytech.acquiring.test.mocker.local.OrderContextDataMocker.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.acquiring.core.dal.dataobject.order.OrderTaxCalculation;
import com.paytech.acquiring.domain.checkout.CreateCheckoutContext;
import com.paytech.acquiring.domain.order.OrderPaymentSucceededPayload;
import com.paytech.acquiring.domain.enums.PaymentAttemptStatus;
import com.paytech.acquiring.domain.enums.PaymentIntentStatus;
import com.paytech.acquiring.domain.order.OrderPaymentFacts;
import com.paytech.acquiring.ext.job.OrderPaymentSucceededHandler;
import com.paytech.acquiring.test.base.MockTestBase;
import com.paytech.acquiring.test.builder.order.GetOrderDataBuilder;
import com.paytech.acquiring.test.builder.order.OrderTaxCalculationBuilder;
import com.paytech.acquiring.test.dal.OrderQueryTestMapper;
import com.paytech.acquiring.test.mocker.local.OrderContextDataMocker;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.tax.api.enums.TaxCalculationStage;
import com.paytech.tax.api.enums.TaxCalculationStatus;

/**
 * OrderPaymentSucceeded 发布入口：测试在本地事务登记补偿任务，提交后由 Foundation 唤醒真实 Handler，回读订单冻结上下文与支付投影，
 * 发布到真实本机 RabbitMQ 的 acquiring.events，由临时观察队列读取信封；本库事实不满足发布条件时为终态失败且不发布。
 *
 * @see OrderPaymentSucceededHandler#handle(CompensationEvent)
 */
class OrderPaymentSucceededHandlerTest extends MockTestBase {
    private static final long ATTEMPT_ID = 8_100_001L;
    private static final long ORDER_VERSION = 9L;
    private static final Instant SUCCEEDED_TIME = Instant.parse("2026-09-08T00:00:01.654321Z");

    @Autowired
    private OrderContextDataMocker contextData;
    @Autowired
    private OrderQueryTestMapper orders;
    @Autowired
    private CompensationEventRegistrar registrar;
    @Autowired
    private PlatformTransactionManager transactionManager;

    /** 平台来源：订单组来自三表冻结事实，Partner 字段为 JSON null；支付组来自登记的支付事实与投影 Intent。 */
    @Test
    void test_platformOrder() {
        var context = contextData.template();
        mockPaidOrder(context, ATTEMPT_ID);

        register(context, facts(ATTEMPT_ID));

        orderPaymentEvents.awaitEnvelope(orderId(context)).envelope(orderId(context), ORDER_VERSION, SUCCEEDED_TIME)
            .payload(expected(context, false, true)).assertBillingTaxShape();
    }

    /** Partner 来源：合作方、Partner 合同、OE 关联、两个条款版本与平台签约 Party 一并发布。 */
    @Test
    void test_partnerOrder() {
        var context = contextData.partnerTemplate();
        mockPaidOrder(context, ATTEMPT_ID);

        register(context, facts(ATTEMPT_ID));

        orderPaymentEvents.awaitEnvelope(orderId(context)).envelope(orderId(context), ORDER_VERSION, SUCCEEDED_TIME)
            .payload(expected(context, true, true));
    }

    /** 渠道交易引用与卡属性为空（老报文或非卡支付）时照常发布，对应字段为 JSON null。 */
    @Test
    void test_nullableFacts() {
        var context = contextData.template();
        mockPaidOrder(context, ATTEMPT_ID);

        register(context, new OrderPaymentFacts(ATTEMPT_ID, new BigDecimal("1.00"), SUCCEEDED_TIME, "CARD_TEST", "301",
            "302", "303", "ch-ref-1", null, null, null, null, null));

        orderPaymentEvents.awaitEnvelope(orderId(context)).envelope(orderId(context), ORDER_VERSION, SUCCEEDED_TIME)
            .payload(expected(context, false, false));
    }

    /**
     * 税务服务上线后的订单（AC-07 / technique 11.1）：携带支付所依据的计算 ID、READY、SALE、冻结税额与按辖区汇总的组件（取自计算记录，
     * 不回查 tax）；新订单不写旧税务快照，financeTaxSnapshotId 为 null。
     */
    @Test
    void test_taxedOrder() {
        var context = contextData.template();
        context.getFrozen().financeTaxSnapshotId(null).financeTaxSnapshotHash(null);
        mockPaidOrder(context, ATTEMPT_ID);
        long calculationId = contextData.nextId();
        contextData.mockTaxCalculation(OrderTaxCalculationBuilder.finalReady(contextData.nextId(), orderId(context), 2,
            calculationId, "SA", "1.00", "0.15", SUCCEEDED_TIME.plusSeconds(3600), SUCCEEDED_TIME));

        register(context, facts(ATTEMPT_ID));

        var expected = expected(context, false, true);
        expected.put("taxCalculationId", Long.toString(calculationId));
        expected.put("taxStatus", TaxCalculationStatus.READY.getCode());
        expected.put("taxScopeKey", OrderPaymentSucceededPayload.TAX_SCOPE_KEY);
        expected.put("taxAmount", "0.15");
        expected.remove("taxComponents");
        orderPaymentEvents.awaitEnvelope(orderId(context)).envelope(orderId(context), ORDER_VERSION, SUCCEEDED_TIME)
            .fields(expected).taxComponents(List.of(Map.of("jurisdictionCode", "SA", "taxType", "VAT",
                "registrationId", "9200001", "taxSubjectPartyId", "601", "taxAmount", "0.15")))
            .assertBillingTaxShape();
    }

    /** 不由平台计税的订单（AC-04）：taxStatus=NOT_MANAGED、税额 0.00、taxComponents 为空数组。 */
    @Test
    void test_notManagedOrder() {
        var context = contextData.template();
        context.getFrozen().financeTaxSnapshotId(null).financeTaxSnapshotHash(null);
        mockPaidOrder(context, ATTEMPT_ID);
        long calculationId = contextData.nextId();
        contextData.mockTaxCalculation(OrderTaxCalculation.builder().orderTaxCalculationId(contextData.nextId())
            .orderId(orderId(context)).revision(2).taxCalculationId(calculationId)
            .calculationStage(TaxCalculationStage.FINAL).taxStatus(TaxCalculationStatus.NOT_MANAGED)
            .basisCountryCode("SA").taxAmount(BigDecimal.ZERO).roundingAmount(BigDecimal.ZERO)
            .totalAmount(new BigDecimal("1.00")).linesJson("[]").componentsJson("[]").createTime(SUCCEEDED_TIME).build());

        register(context, facts(ATTEMPT_ID));

        var expected = expected(context, false, true);
        expected.put("taxCalculationId", Long.toString(calculationId));
        expected.put("taxStatus", TaxCalculationStatus.NOT_MANAGED.getCode());
        expected.put("taxScopeKey", OrderPaymentSucceededPayload.TAX_SCOPE_KEY);
        expected.put("taxAmount", "0.00");
        expected.remove("taxComponents");
        orderPaymentEvents.awaitEnvelope(orderId(context)).envelope(orderId(context), ORDER_VERSION, SUCCEEDED_TIME)
            .fields(expected).taxComponents(List.of()).assertBillingTaxShape();
    }

    /** 未知订单：终态失败，不发布。 */
    @Test
    void test_noDataFound() {
        var context = contextData.template();

        register(context, facts(ATTEMPT_ID));

        assertNotPublishable(context);
    }

    /** 订单存在但尚未冻结上下文：终态失败，不发布。 */
    @Test
    void test_orderWithoutContext() {
        var context = contextData.template();
        contextData.mockOrderOnly(context);
        orders.insertPayment(orderId(context), succeededPayment(ATTEMPT_ID));

        register(context, facts(ATTEMPT_ID));

        assertNotPublishable(context);
    }

    /** 支付投影没有成功尝试：终态失败，不发布。 */
    @Test
    void test_projectionWithoutSuccess() {
        var context = contextData.template();
        contextData.mock(context);
        orders.insertPayment(orderId(context), GetOrderDataBuilder.Payment.builder().build());

        register(context, facts(ATTEMPT_ID));

        assertNotPublishable(context);
    }

    /** 投影的成功尝试与登记的尝试不同：终态失败，不发布。 */
    @Test
    void test_attemptMismatch() {
        var context = contextData.template();
        mockPaidOrder(context, ATTEMPT_ID + 1);

        register(context, facts(ATTEMPT_ID));

        assertNotPublishable(context);
    }

    /** 登记的支付事实缺少必填渠道字段：任务元数据不合法，终态失败，不读取订单也不发布。 */
    @Test
    void test_missingPaymentFacts() {
        var context = contextData.template();
        mockPaidOrder(context, ATTEMPT_ID);

        register(context, new OrderPaymentFacts(ATTEMPT_ID, new BigDecimal("1.00"), SUCCEEDED_TIME, "CARD_TEST", null,
            "302", "303", "ch-ref-1", null, null, null, null, null));

        assertEquals("ORDER_PAYMENT_EVENT_INVALID", orderPaymentEvents.awaitTerminalFailure(orderId(context)));
    }

    /** 插入订单冻结上下文与已成功的支付投影（Intent ID 同订单 ID）。 */
    void mockPaidOrder(CreateCheckoutContext context, long successfulAttemptId) {
        contextData.mock(context);
        orders.insertPayment(orderId(context), succeededPayment(successfulAttemptId));
    }

    /** 成功尝试为给定值的支付投影。 */
    static GetOrderDataBuilder.Payment succeededPayment(long successfulAttemptId) {
        return GetOrderDataBuilder.Payment.builder().intentStatus(PaymentIntentStatus.SUCCEEDED)
            .lastAttemptId(successfulAttemptId).lastAttemptStatus(PaymentAttemptStatus.SUCCEEDED)
            .successfulAttemptId(successfulAttemptId).capturedAmount(new BigDecimal("1.00")).build();
    }

    /** 全部字段齐全的支付事实。 */
    static OrderPaymentFacts facts(long attemptId) {
        return new OrderPaymentFacts(attemptId, new BigDecimal("1.00"), SUCCEEDED_TIME, "CARD_TEST", "301", "302", "303",
            "ch-ref-1", "401", "PAY", "VISA", "CREDIT", "CLASSIC");
    }

    /** 在本地事务登记任务；提交后由 Foundation 唤醒 Handler。 */
    void register(CreateCheckoutContext context, OrderPaymentFacts facts) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> registrar
            .register(OrderPaymentSucceededHandler.buildEvent(orderId(context), ORDER_VERSION, facts)));
    }

    /** 本库事实不满足发布条件。 */
    void assertNotPublishable(CreateCheckoutContext context) {
        assertEquals("ORDER_PAYMENT_NOT_PUBLISHABLE", orderPaymentEvents.awaitTerminalFailure(orderId(context)));
    }

    /** 期望 Payload：订单组取前置事实，支付组取登记事实；金额按 USD 精度。 */
    static Map<String, String> expected(CreateCheckoutContext context, boolean partner, boolean fullFacts) {
        var order = context.getOrder();
        var frozen = context.getFrozen();
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("orderId", Long.toString(order.getOrderId()));
        payload.put("orderContextId", Long.toString(frozen.orderContextId()));
        payload.put("clientId", Long.toString(order.getClientId()));
        payload.put("entityNo", order.getEntityNo());
        payload.put("entitySourceType", order.getEntitySourceType());
        payload.put("localPartnerId", partner ? Long.toString(LOCAL_PARTNER_ID) : null);
        payload.put("industryCode", order.getIndustryCode());
        payload.put("mccCode", order.getMccCode());
        payload.put("orderAmount", "1.00");
        payload.put("currency", order.getCurrency());
        payload.put("commercialContextTime", frozen.commercialContextTime().toString());
        payload.put("clientAgreementId", Long.toString(frozen.clientAgreementId()));
        payload.put("clientPricingVersionId", Long.toString(frozen.clientPricingVersionId()));
        payload.put("partnerAgreementId", partner ? Long.toString(PARTNER_AGREEMENT_ID) : null);
        payload.put("partnerAgreementOperatingEntityId", partner ? Long.toString(PARTNER_AGREEMENT_OE_ID) : null);
        payload.put("partnerShareVersionId", partner ? Long.toString(PARTNER_SHARE_VERSION_ID) : null);
        payload.put("platformChargeVersionId", partner ? Long.toString(PLATFORM_CHARGE_VERSION_ID) : null);
        payload.put("platformServicePartyId", partner ? Long.toString(PLATFORM_SERVICE_PARTY_ID) : null);
        payload.put("financeTaxSnapshotId",
            frozen.financeTaxSnapshotId() == null ? null : Long.toString(frozen.financeTaxSnapshotId()));
        payload.put("consumerCountryCode", frozen.consumerCountryCode());
        payload.put("paymentIntentId", Long.toString(order.getOrderId()));
        payload.put("paymentAttemptId", Long.toString(ATTEMPT_ID));
        payload.put("capturedAmount", "1.00");
        payload.put("paymentSucceededTime", SUCCEEDED_TIME.toString());
        payload.put("paymentMethodCode", "CARD_TEST");
        payload.put("channelId", "301");
        payload.put("channelAccountId", "302");
        payload.put("midId", "303");
        payload.put("channelTransactionRef", "ch-ref-1");
        payload.put("channelTransactionRefId", fullFacts ? "401" : null);
        payload.put("channelReferenceType", fullFacts ? "PAY" : null);
        payload.put("cardBrand", fullFacts ? "VISA" : null);
        payload.put("fundingType", fullFacts ? "CREDIT" : null);
        payload.put("cardLevel", fullFacts ? "CLASSIC" : null);
        // 存量订单（旧零税快照，无税务计算记录）：可空新增的税务字段全部为 null（AC-12）。
        payload.put("taxCalculationId", null);
        payload.put("taxStatus", null);
        payload.put("taxScopeKey", null);
        payload.put("taxAmount", null);
        payload.put("taxComponents", null);
        return payload;
    }

    /** 本例订单 ID。 */
    static long orderId(CreateCheckoutContext context) {
        return context.getOrder().getOrderId();
    }
}
