package com.paytech.tax.auto.mq;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.tax.api.enums.TaxCalculationStatus;
import com.paytech.tax.api.enums.TaxDocumentIssueStatus;
import com.paytech.tax.api.enums.TaxReportingStatus;
import com.paytech.tax.core.constant.TaxCodes;
import com.paytech.tax.core.enums.FilingFrequency;
import com.paytech.tax.core.enums.ReportingEntryRole;
import com.paytech.tax.core.enums.TaxPeriodStatus;
import com.paytech.tax.ext.mq.OrderPaymentSucceededTaxHandler;
import com.paytech.tax.test.base.MockTestBase;
import com.paytech.tax.test.builder.CalculateTaxRequestBuilder;
import com.paytech.tax.test.builder.OrderPaymentSucceededEnvelopeBuilder;
import com.paytech.tax.test.builder.TaxTestConstants;

/**
 * 消费 OrderPaymentSucceeded 登记正式交易与申报底稿（AC-07、AC-08、AC-12、AC-04 的 tax 侧）
 * <li>经真实 acquiring.events → tax.acquiring.p0..p3 → 幂等拦截器 → Handler；冻结计算经真实计算接口建立（acquiring 支付前已取得的计算）。</li>
 * <li>SA 注册以 USD 申报（底稿 READY，同币种汇率 1）；EG 注册以 EGP 申报（缺法定汇率 → PENDING）。READY 交易会登记开票任务，本类接受上传。</li>
 *
 * @see OrderPaymentSucceededTaxHandler#consume(DomainEvent)
 */
class OrderPaymentSucceededTaxTest extends MockTestBase {
    /** SA 注册 ID。 */
    static final long SA_REGISTRATION = 1101L;

    /** EG 注册 ID。 */
    static final long EG_REGISTRATION = 1111L;

    /** SA（USD 申报、按季）与 EG（EGP 申报、按月）注册；开票任务的上传全部接受。 */
    @BeforeEach
    void setUpClassMocks() {
        subjects.registered(SA_REGISTRATION, "SA", TaxTestConstants.SEED_POLICY_SA, TaxTestConstants.USD, "Asia/Riyadh",
            FilingFrequency.QUARTERLY);
        subjects.registered(EG_REGISTRATION, "EG", TaxTestConstants.SEED_POLICY_EG, "EGP", "Africa/Cairo",
            FilingFrequency.MONTHLY);
        files.acceptUploads(0);
    }

    /** AC-07：READY 计算 → 唯一正式交易、OUTPUT 底稿归属 SA 当地日期所在季度，截止日为次月末；随后开出税票。 */
    @Test
    void test_registersTransactionAndEntries() {
        String calculationId = calculate(TaxTestConstants.ORDER_ID, "SA");

        consumed(paid(TaxTestConstants.ORDER_ID, calculationId, "15.00", "SA", SA_REGISTRATION));

        Map<String, Object> transaction = transaction(TaxTestConstants.ORDER_ID);
        assertEquals(TaxReportingStatus.READY.getCode(), transaction.get("reporting_status"));
        assertNull(transaction.get("reporting_block_reason"));
        assertEquals(Date.valueOf("2026-10-30"), transaction.get("tax_point_date"), "税点日期取 Asia/Riyadh 的本地日期");
        List<Map<String, Object>> entries = testMapper.selectEntries(((Number)transaction.get("id")).longValue());
        assertEquals(1, entries.size());
        Map<String, Object> entry = entries.get(0);
        assertAll(() -> assertEquals(ReportingEntryRole.OUTPUT.getCode(), entry.get("entry_role")),
            () -> assertEquals(0, new BigDecimal("15").compareTo((BigDecimal)entry.get("source_tax_amount"))),
            () -> assertEquals(TaxTestConstants.USD, entry.get("reporting_currency")),
            () -> assertEquals(0, BigDecimal.ONE.compareTo((BigDecimal)entry.get("fx_rate"))),
            () -> assertEquals(Date.valueOf("2026-10-01"), entry.get("period_start")),
            () -> assertEquals(Date.valueOf("2026-12-31"), entry.get("period_end")),
            () -> assertEquals(Date.valueOf("2027-01-31"), entry.get("filing_due_date")),
            () -> assertEquals(TaxPeriodStatus.OPEN.getCode(), entry.get("period_status")));
        awaitIssued(((Number)transaction.get("id")).longValue());
    }

    /** 同一事件重复投递（同 eventId 由消费去重跳过）与同内容不同 eventId（业务幂等）都只有一条正式交易。 */
    @Test
    void test_duplicateDeliveryRegistersOnce() {
        String calculationId = calculate(TaxTestConstants.ORDER_ID, "SA");
        DomainEvent event = paid(TaxTestConstants.ORDER_ID, calculationId, "15.00", "SA", SA_REGISTRATION);
        consumed(event);

        acquiringEvents.publish(event);
        consumed(OrderPaymentSucceededEnvelopeBuilder.template().aggregateVersion(TaxTestConstants.ORDER_VERSION + 1)
            .taxCalculation(calculationId, TaxCalculationStatus.READY, "15.00",
                OrderPaymentSucceededEnvelopeBuilder.component("SA", Long.toString(SA_REGISTRATION), "15.00"))
            .build());

        assertEquals(1, testMapper.countTransactions());
        awaitIssued(((Number)transaction(TaxTestConstants.ORDER_ID).get("id")).longValue());
    }

    /** AC-08：事件税额与冻结计算不一致 → 死信，不建事实。 */
    @Test
    void test_amountMismatchDeadLetters() {
        String calculationId = calculate(TaxTestConstants.ORDER_ID, "SA");

        deadLettered(paid(TaxTestConstants.ORDER_ID, calculationId, "16.00", "SA", SA_REGISTRATION));

        assertEquals(0, testMapper.countTransactions());
        files.assertNoCalls();
    }

    /** 同一订单已登记后内容不同的事件 → 死信，原交易保留。 */
    @Test
    void test_sameOrderDifferentContentDeadLetters() {
        String calculationId = calculate(TaxTestConstants.ORDER_ID, "SA");
        consumed(paid(TaxTestConstants.ORDER_ID, calculationId, "15.00", "SA", SA_REGISTRATION));
        long transactionId = ((Number)transaction(TaxTestConstants.ORDER_ID).get("id")).longValue();

        deadLettered(OrderPaymentSucceededEnvelopeBuilder.template().aggregateVersion(TaxTestConstants.ORDER_VERSION + 1)
            .set("paymentAttemptId", "550000000099").taxCalculation(calculationId, TaxCalculationStatus.READY, "15.00",
                OrderPaymentSucceededEnvelopeBuilder.component("SA", Long.toString(SA_REGISTRATION), "15.00"))
            .build());

        assertEquals(1, testMapper.countTransactions());
        assertEquals(transactionId, ((Number)transaction(TaxTestConstants.ORDER_ID).get("id")).longValue());
        awaitIssued(transactionId);
    }

    /** AC-12：存量订单（无税务字段）正常消费，不建正式事实。 */
    @Test
    void test_legacyOrderSkipped() {
        consumed(OrderPaymentSucceededEnvelopeBuilder.template().build());

        assertEquals(0, testMapper.countTransactions());
        files.assertNoCalls();
    }

    /** AC-04：NOT_MANAGED 正常消费，不建正式事实与税票。 */
    @Test
    void test_notManagedSkipped() {
        consumed(OrderPaymentSucceededEnvelopeBuilder.template()
            .taxCalculation("440000000001", TaxCalculationStatus.NOT_MANAGED, "7.00").build());

        assertEquals(0, testMapper.countTransactions());
        files.assertNoCalls();
    }

    /** 申报币种（EGP）与交易币种（USD）不同且没有法定汇率：交易 PENDING(FX_RATE_MISSING)，不建底稿；期间已按月生成。 */
    @Test
    void test_crossCurrencyWithoutRatePending() {
        long orderId = TaxTestConstants.ORDER_ID + 1;
        String calculationId = calculate(orderId, "EG");

        consumed(paid(orderId, calculationId, "14.00", "EG", EG_REGISTRATION));

        Map<String, Object> transaction = transaction(orderId);
        assertEquals(TaxReportingStatus.PENDING.getCode(), transaction.get("reporting_status"));
        assertEquals(TaxCodes.REPORTING_FX_RATE_MISSING, transaction.get("reporting_block_reason"));
        assertEquals(List.of(), testMapper.selectEntries(((Number)transaction.get("id")).longValue()));
        assertEquals(1, testMapper.countPeriods());
        awaitIssued(((Number)transaction.get("id")).longValue());
    }

    /** 经真实计算接口建立该订单的 FINAL 计算，返回计算 ID。 */
    String calculate(long orderId, String country) {
        return post("/tax/v1/calculations", CalculateTaxRequestBuilder.template().order(orderId, "ORD-" + orderId)
            .country(country).build()).assertSuccess(200).assertText("status", TaxCalculationStatus.READY.getCode())
            .text("calculationId");
    }

    /** 带 READY 税务组的支付成功事件。 */
    static DomainEvent paid(long orderId, String calculationId, String taxAmount, String country, long registrationId) {
        return OrderPaymentSucceededEnvelopeBuilder.template().order(orderId)
            .taxCalculation(calculationId, TaxCalculationStatus.READY, taxAmount,
                OrderPaymentSucceededEnvelopeBuilder.component(country, Long.toString(registrationId), taxAmount))
            .build();
    }

    /** 发布并等到登记消费（Handler 成功）。 */
    void consumed(DomainEvent event) {
        String eventId = acquiringEvents.publish(event);
        await().atMost(ASYNC_WAIT).until(() -> testMapper.countConsumedEvent(eventId) == 1);
    }

    /** 发布并等到进入 DLQ；不登记消费。 */
    void deadLettered(DomainEvent event) {
        String eventId = acquiringEvents.publish(event);
        await().atMost(ASYNC_WAIT).until(() -> acquiringEvents.deadLetters() == 1);
        assertEquals(0, testMapper.countConsumedEvent(eventId), "进入 DLQ 的事件不应登记消费");
    }

    /** 订单的正式交易。 */
    Map<String, Object> transaction(long orderId) {
        return testMapper.selectTransaction(Long.toString(orderId));
    }

    /** 等到交易的税票开出（开票是登记后的异步补偿任务）。 */
    void awaitIssued(long transactionId) {
        await().atMost(ASYNC_WAIT).until(() -> {
            Map<String, Object> document = testMapper.selectDocument(transactionId);
            return document != null && TaxDocumentIssueStatus.ISSUED.getCode().equals(document.get("issue_status"));
        });
    }
}
