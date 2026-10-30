package com.paytech.acquiring.auto.ext.http.customerapi.v1.checkout.tax;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import com.paytech.acquiring.api.checkout.v1.checkout.CustomerCheckoutApi;
import com.paytech.acquiring.api.checkout.v1.checkout.request.SaveConsumerRequestBody;
import com.paytech.acquiring.api.checkout.v1.checkout.response.GetCheckoutResponseBody;
import com.paytech.acquiring.api.checkout.v1.enums.CustomerCheckoutErrorCode;
import com.paytech.acquiring.api.checkout.v1.payment.CustomerPaymentApi;
import com.paytech.acquiring.api.checkout.v1.payment.request.SubmitPaymentRequestBody;
import com.paytech.acquiring.api.client.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.acquiring.api.client.v1.enums.OrderAmountStatus;
import com.paytech.acquiring.core.dal.dataobject.order.OrderTaxCalculation;
import com.paytech.acquiring.domain.checkout.CreateCheckoutContext;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.ProtectedConsumer;
import com.paytech.acquiring.test.base.MockTestBase;
import com.paytech.acquiring.test.builder.customer.PaymentResponseBuilder;
import com.paytech.acquiring.test.builder.customer.SaveConsumerRequestBuilder;
import com.paytech.acquiring.test.builder.customer.SubmitPaymentRequestBuilder;
import com.paytech.acquiring.test.builder.external.CheckoutContextResponseBuilder;
import com.paytech.acquiring.test.builder.external.TaxCalculationResponseBuilder;
import com.paytech.acquiring.test.builder.order.OrderTaxCalculationBuilder;
import com.paytech.acquiring.test.checker.customer.CheckoutFlowResponseChecker;
import com.paytech.acquiring.test.checker.customer.SaveConsumerResponseChecker;
import com.paytech.acquiring.test.checker.order.OrderTaxChecker;
import com.paytech.acquiring.test.mocker.external.CardInstrumentMocker;
import com.paytech.acquiring.test.mocker.external.CheckoutCardMocker;
import com.paytech.acquiring.test.mocker.external.CheckoutIntentMocker;
import com.paytech.acquiring.test.mocker.external.ConsumerProtectionMocker;
import com.paytech.acquiring.test.mocker.local.CheckoutPaymentDataMocker;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;
import com.paytech.tax.api.enums.TaxBlockReason;
import com.paytech.tax.api.enums.TaxCalculationStage;
import com.paytech.tax.api.enums.TaxCalculationStatus;
import com.paytech.tax.api.enums.TaxEvidenceType;
import com.paytech.tax.api.response.TaxCalculationResponseBody;
import com.paytech.commercial.api.enums.BusinessMode;

/**
 * 消费者提交资料后按账单国家重算与支付门禁（technique S-04、S-05、9.1；AC-01、AC-03、AC-04、AC-05、AC-12、AC-14）
 * <li>前置订单为税务服务上线后的形态：单行未税价 100.00，建单暂估 revision 1，合同绑定该计算，MOR 商业关系（TAX_RESPONSIBLE = OE
 * Party）。</li>
 * <li>资料提交在事务外调用 tax FINAL；金额变化时重新渲染合同，订单当前报价、计算记录与新合同一次提交；支付只能基于最新、FINAL、READY 或
 * NOT_MANAGED 且未过期、合同绑定同一计算的报价。</li>
 *
 * @see CustomerCheckoutApi#saveConsumer(String, SaveConsumerRequestBody)
 * @see CustomerCheckoutApi#getCheckout(String, boolean)
 * @see CustomerPaymentApi#submitPayment(String, SubmitPaymentRequestBody)
 */
class CheckoutTaxQuoteTest extends MockTestBase {
    private static final String REQUEST_ID = "Commerce.CheckoutTax.Test";
    private static final Instant NOW = Instant.parse("2026-09-08T00:00:00Z");
    private static final String NET = CheckoutPaymentDataMocker.TAXED_NET;
    private static final ProtectedConsumer PROTECTED = new ProtectedConsumer("tax-email-reference",
        "tax-profile-reference");

    @Autowired
    private CheckoutPaymentDataMocker data;
    @Autowired
    private ConsumerProtectionMocker protection;
    @Autowired
    private OrderTaxChecker taxChecker;
    @Autowired
    private CheckoutIntentMocker intents;
    @Autowired
    private CardInstrumentMocker instruments;
    @Autowired
    private CheckoutCardMocker cards;
    private CreateCheckoutContext fixture;

    /** 第二层 BeforeEach：Payment 的 Intent、卡工具与 pay 响应，供可支付场景使用；门禁拒绝场景断言零调用。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = data.taxedTemplate();
        intents.mockResult(PaymentResponseBuilder.builder().intentId(Long.toString(orderId())).build().intent());
        instruments.mockResponse(
            PaymentResponseBuilder.builder().expiresAt(adjustableUtcClock.instant().plusSeconds(60)).build().instrument());
        cards.mockResponse(CheckoutCardMocker.processing("123456"));
    }

    /** AC-01 / AC-03：声明国家暂估 5%，账单国家 SA 重算为 15%，两份计算都保留，合同按新计算重新渲染，按 115.00 付款。 */
    @Test
    void test_finalRecalculation() {
        long initial = mockEstimated("AE", "5.00");
        taxMocker.mock(TaxCalculationStage.FINAL,
            TaxCalculationResponseBuilder.ready("0.15").expiresAt(NOW.plus(Duration.ofHours(24))));

        save("SA").assertSaved(orderNo()).assertQuote(OrderAmountStatus.FINAL, "15.00", "115.00", null);

        long recalculated = taxChecker.calculationId(orderId(), 2);
        taxChecker.assertRevisions(orderId(), 2)
            .assertCalculation(orderId(), 1, TaxCalculationStage.PROVISIONAL, TaxCalculationStatus.READY, "AE", "5.00",
                "105.00", null)
            .assertCalculation(orderId(), 2, TaxCalculationStage.FINAL, TaxCalculationStatus.READY, "SA", "15.00",
                "115.00", null)
            .assertQuote(orderId(), OrderAmountStatus.FINAL, "15.00", "115.00")
            .assertContracts(orderId(), List.of(initial, recalculated));
        assertEquals(recalculated, renderedFileId(1), "金额变化时按新计算重新渲染合同");
        assertFinalRequest("SA");

        var view = getCheckout();
        assertAll(() -> assertEquals(OrderAmountStatus.FINAL.getCode(), view.getAmountStatus()),
            () -> assertEquals("115.00", view.getAmounts().getTotalAmount()),
            () -> assertEquals(Long.toString(currentContractId()), view.getLegal().getConsumerContractId()),
            () -> assertTrue(view.getAllowedActions().contains("PAY")));
        submit(currentContractId()).assertProcessing();
        assertEquals("115.00", intents.calls().getFirst().getAmount());
    }

    /** 金额不变时沿用当前合同文件，只追加绑定新计算的合同行。 */
    @Test
    void test_unchangedAmountsReuseContractFile() {
        mockEstimated("SA", "15.00");
        taxMocker.mock(TaxCalculationStage.FINAL,
            TaxCalculationResponseBuilder.ready("0.15").expiresAt(NOW.plus(Duration.ofHours(24))));

        save("SA").assertSaved(orderNo()).assertQuote(OrderAmountStatus.FINAL, "15.00", "115.00", null);

        var contracts = taxChecker.contracts(orderId());
        assertEquals(2, contracts.size());
        assertEquals(renderedFileId(0), renderedFileId(1), "报价不变不重新渲染合同");
        taxChecker.assertContracts(orderId(),
            List.of(taxChecker.calculationId(orderId(), 1), taxChecker.calculationId(orderId(), 2)));
    }

    /** AC-05 / AC-14：账单国家无法计税时返回 BLOCKED 与原因，Customer API 不给出金额，支付被拒绝且不创建 Intent。 */
    @Test
    void test_blockedCannotPay() {
        mockEstimated("AE", "5.00");
        taxMocker.mock(TaxCalculationStage.FINAL, TaxCalculationResponseBuilder.blocked(TaxBlockReason.NO_RULE));

        save("KW").assertSaved(orderNo())
            .assertQuote(OrderAmountStatus.BLOCKED, null, null, TaxBlockReason.NO_RULE.getCode());

        taxChecker.assertCalculation(orderId(), 2, TaxCalculationStage.FINAL, TaxCalculationStatus.BLOCKED, "KW", null,
            null, TaxBlockReason.NO_RULE.getCode()).assertQuote(orderId(), OrderAmountStatus.BLOCKED, null, null);
        var view = getCheckout();
        assertAll(() -> assertEquals(OrderAmountStatus.BLOCKED.getCode(), view.getAmountStatus()),
            () -> assertEquals(TaxBlockReason.NO_RULE.getCode(), view.getTaxBlockReason()),
            () -> assertNull(view.getAmounts().getTotalAmount()),
            () -> assertFalse(view.getAllowedActions().contains("PAY")));
        submit(currentContractId()).assertProblem(HttpStatus.CONFLICT.value(),
            CustomerCheckoutErrorCode.CHECKOUT_NOT_READY.getCode());
        assertTrue(intents.calls().isEmpty());
    }

    /** AC-03：只有建单暂估（PROVISIONAL）时发起支付被拒绝。 */
    @Test
    void test_provisionalRejected() {
        long initial = data.nextId();
        data.mockTaxed(fixture, true, initial, List.of(provisional(initial, "AE", "5.00")));

        submit(currentContractId()).assertProblem(HttpStatus.CONFLICT.value(),
            CustomerCheckoutErrorCode.CHECKOUT_NOT_READY.getCode());

        assertTrue(intents.calls().isEmpty());
    }

    /** 最终报价已过有效期：支付被拒绝，需重新提交资料重算。 */
    @Test
    void test_expiredRejected() {
        long initial = data.nextId();
        long recalculated = data.nextId();
        data.mockTaxed(fixture, true, recalculated, List.of(provisional(initial, "AE", "5.00"),
            finalReady(recalculated, "SA", "15.00", NOW.plusSeconds(60))));
        adjustableUtcClock.advance(Duration.ofSeconds(120));

        submit(currentContractId()).assertProblem(HttpStatus.CONFLICT.value(),
            CustomerCheckoutErrorCode.CHECKOUT_NOT_READY.getCode());

        assertTrue(intents.calls().isEmpty());
    }

    /** 本次确认的合同没有绑定最新计算：LEGAL_DOCUMENT_CHANGED。 */
    @Test
    void test_contractNotBoundToLatest() {
        long initial = data.nextId();
        long recalculated = data.nextId();
        data.mockTaxed(fixture, true, initial, List.of(provisional(initial, "AE", "5.00"),
            finalReady(recalculated, "SA", "15.00", NOW.plus(Duration.ofHours(24)))));

        submit(currentContractId()).assertProblem(HttpStatus.CONFLICT.value(),
            CustomerCheckoutErrorCode.LEGAL_DOCUMENT_CHANGED.getCode());

        assertTrue(intents.calls().isEmpty());
    }

    /** AC-04：不由平台计税时税额为自报值 0，金额状态 FINAL，按净额正常付款。 */
    @Test
    void test_notManagedPays() {
        mockEstimated("AE", "5.00");
        taxMocker.mock(TaxCalculationStage.FINAL, TaxCalculationResponseBuilder.notManaged());

        save("SA").assertSaved(orderNo()).assertQuote(OrderAmountStatus.FINAL, "0.00", NET, null);

        taxChecker.assertCalculation(orderId(), 2, TaxCalculationStage.FINAL, TaxCalculationStatus.NOT_MANAGED, "SA",
            "0.00", NET, null);
        submit(currentContractId()).assertProcessing();
        assertEquals(NET, intents.calls().getFirst().getAmount());
    }

    /**
     * CLIENT 来源（没有商业关系快照）：重算时按 ID 从 tax 取回建单冻结的业务模式，不传责任方，结果 NOT_MANAGED，按净额正常付款。
     */
    @Test
    void test_clientSourceRecalculation() {
        long initial = data.nextId();
        data.mockTaxed(fixture, false, false, initial, List.of(OrderTaxCalculationBuilder.notManaged(data.nextId(),
            orderId(), 1, initial, TaxCalculationStage.PROVISIONAL, "US", NET, NOW)));
        protection.mockResult(SaveConsumerRequestBuilder.template().country("SA").details(), PROTECTED);
        var frozen = new TaxCalculationResponseBody();
        frozen.setCalculationId(Long.toString(initial));
        frozen.setBusinessMode(BusinessMode.PSP.getCode());
        taxMocker.mockStored(initial, frozen);
        taxMocker.mock(TaxCalculationStage.FINAL, TaxCalculationResponseBuilder.notManaged());

        save("SA").assertSaved(orderNo()).assertQuote(OrderAmountStatus.FINAL, "0.00", NET, null);

        assertEquals(List.of(Long.toString(initial)), taxMocker.reads());
        var request = taxMocker.requests().getFirst();
        assertAll(() -> assertNull(request.getTaxSubjectPartyId()), () -> assertNull(request.getSellerPartyId()),
            () -> assertEquals(BusinessMode.PSP.getCode(), request.getBusinessMode()));
        taxChecker.assertCalculation(orderId(), 2, TaxCalculationStage.FINAL, TaxCalculationStatus.NOT_MANAGED, "SA",
            "0.00", NET, null);
        submit(currentContractId()).assertProcessing();
        assertEquals(NET, intents.calls().getFirst().getAmount());
    }

    /** AC-12：税务服务上线前的存量订单（旧零税快照、无计算记录）保存资料不重算，沿用原报价。 */
    @Test
    void test_legacyOrderNotRecalculated() {
        fixture = data.mockPrepared(false);
        protection.mockResult(SaveConsumerRequestBuilder.template().country("SA").details(), PROTECTED);

        save("SA").assertSaved(orderNo()).assertQuote(OrderAmountStatus.FINAL, "0.00", "1.00", null);

        taxMocker.assertCalls(0);
        taxChecker.assertRevisions(orderId(), 0);
    }

    /** tax 不可用：503 DEPENDENCY_UNAVAILABLE，资料、报价与合同都不写入。 */
    @Test
    void test_taxUnavailable() {
        mockEstimated("AE", "5.00");
        var problem = new ApiProblemResponse("about:blank", HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
            "tax Mock 不可用", CommonErrorCode.SERVICE_UNAVAILABLE.getCode(), "tax-test", "2026-10-29T00:00:00.000000Z",
            null);
        taxMocker.mockFailure(MockHttpFault.json(HttpStatus.SERVICE_UNAVAILABLE.value(), problem));

        save("SA").assertProblem(HttpStatus.SERVICE_UNAVAILABLE.value(),
            CommerceCheckoutErrorCode.DEPENDENCY_UNAVAILABLE.getCode());

        taxChecker.assertRevisions(orderId(), 1).assertQuote(orderId(), OrderAmountStatus.ESTIMATED, "5.00", "105.00");
        assertEquals(1, taxChecker.contracts(orderId()).size());
    }

    /** 写入建单暂估订单（无消费者资料），并配置本例账单国家资料的保护结果；返回建单计算 ID。 */
    private long mockEstimated(String declaredCountry, String tax) {
        long initial = data.nextId();
        data.mockTaxed(fixture, false, initial, List.of(provisional(initial, declaredCountry, tax)));
        for (String country : List.of("SA", "KW")) {
            protection.mockResult(SaveConsumerRequestBuilder.template().country(country).details(), PROTECTED);
        }
        return initial;
    }

    /** 建单暂估计算行。 */
    private OrderTaxCalculation provisional(long calculationId, String country, String tax) {
        return OrderTaxCalculationBuilder.provisional(data.nextId(), orderId(), calculationId, country, NET, tax, NOW);
    }

    /** 资料提交后的最终计算行（revision 2）。 */
    private OrderTaxCalculation finalReady(long calculationId, String country, String tax, Instant expiresAt) {
        return OrderTaxCalculationBuilder.finalReady(data.nextId(), orderId(), 2, calculationId, country, NET, tax,
            expiresAt, NOW);
    }

    /** 最终计算请求：revision 2、FINAL、账单国家证据、OE 纳税主体。 */
    private void assertFinalRequest(String billingCountry) {
        taxMocker.assertCalls(1);
        var request = taxMocker.requests().getFirst();
        assertAll(() -> assertEquals(2, request.getSourceRevision()),
            () -> assertEquals(TaxCalculationStage.FINAL.getCode(), request.getStage()),
            () -> assertEquals(TaxEvidenceType.BILLING_COUNTRY.getCode(), request.getEvidence().getFirst().getType()),
            () -> assertEquals(billingCountry, request.getEvidence().getFirst().getCountryCode()),
            () -> assertEquals(CheckoutContextResponseBuilder.OE_PARTY_ID, request.getTaxSubjectPartyId()),
            () -> assertEquals("100", request.getLines().getFirst().getLineAmount()));
    }

    /** 第 index 份合同（按呈现先后）的文件 ID。 */
    private long renderedFileId(int index) {
        return ((Number) taxChecker.contracts(orderId()).get(index).get("rendered_file_id")).longValue();
    }

    /** 当前（最新呈现）合同 ID。 */
    private long currentContractId() {
        return ((Number) taxChecker.contracts(orderId()).getLast().get("consumer_contract_id")).longValue();
    }

    /** 经正式消费者入口保存资料。 */
    private SaveConsumerResponseChecker save(String country) {
        return new SaveConsumerResponseChecker(httpTestInvoker.exchange(HttpMethod.PUT, path() + "/consumer",
            TestRequestHeaders.customer(REQUEST_ID), SaveConsumerRequestBuilder.template().country(country).build()),
            objectMapper, REQUEST_ID);
    }

    /** 经正式消费者入口加载 Checkout。 */
    private GetCheckoutResponseBody getCheckout() {
        var result = httpTestInvoker.get(path(), headers());
        assertEquals(HttpStatus.OK.value(), result.getStatus().value());
        return objectMapper.readValue(result.getResponseBody(), GetCheckoutResponseBody.class);
    }

    /** 经正式消费者入口按给定合同提交合成卡支付。 */
    private CheckoutFlowResponseChecker submit(long contractId) {
        return new CheckoutFlowResponseChecker(httpTestInvoker.post(path() + "/payments", headers(),
            SubmitPaymentRequestBuilder.forContract(contractId)), objectMapper, REQUEST_ID);
    }

    /** 本例的公开 Checkout 路径。 */
    private String path() {
        return "/customer-gateway-api/v1/checkouts/" + fixture.getSession().getPublicToken();
    }

    /** 追踪头。 */
    private static HttpHeaders headers() {
        var headers = new HttpHeaders();
        headers.set(ApiHeaderNames.REQUEST_ID, REQUEST_ID);
        return headers;
    }

    /** 本例订单 ID。 */
    private long orderId() {
        return fixture.getOrder().getOrderId();
    }

    /** 本例公开订单号。 */
    private String orderNo() {
        return fixture.getOrder().getOrderNo();
    }
}
