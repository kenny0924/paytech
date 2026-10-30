package com.paytech.acquiring.auto.ext.http.clientapi.v1.checkout.create;

import static com.paytech.acquiring.test.builder.checkout.CreateCheckoutRequestBuilder.item;
import static com.paytech.acquiring.test.builder.checkout.CreateCheckoutRequestBuilder.template;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import com.paytech.acquiring.api.client.v1.checkout.ClientCheckoutApi;
import com.paytech.acquiring.api.client.v1.checkout.request.CreateCheckoutRequestBody;
import com.paytech.acquiring.api.client.v1.enums.CommerceCheckoutErrorCode;
import com.paytech.acquiring.api.client.v1.enums.OrderAmountStatus;
import com.paytech.acquiring.test.base.checkout.AbstractCreateCheckoutApiTest;
import com.paytech.acquiring.test.builder.external.CheckoutContextResponseBuilder;
import com.paytech.acquiring.test.builder.external.TaxCalculationResponseBuilder;
import com.paytech.acquiring.test.checker.order.OrderTaxChecker;
import com.paytech.commercial.api.enums.BusinessMode;
import com.paytech.commercial.api.enums.RelationshipRole;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.tax.api.enums.TaxBlockReason;
import com.paytech.tax.api.enums.TaxCalculationStage;
import com.paytech.tax.api.enums.TaxCalculationStatus;
import com.paytech.tax.api.enums.TaxEvidenceType;
import com.paytech.tax.api.enums.TaxPriceMode;

/**
 * 建单暂估报价（technique 10.2、S-07，AC-03 / AC-04 / AC-05）：tax 按 Client 声明国家给出 PROVISIONAL 计算，订单当前报价、金额状态、
 * 计算记录（revision 1）、上下文建单计算与合同绑定在同一事务写入；BLOCKED 仍建单，tax 不可用时不建单。
 *
 * @see ClientCheckoutApi#createCheckout(long, long, String, CreateCheckoutRequestBody)
 */
class CreateCheckoutTaxTest extends AbstractCreateCheckoutApiTest {
    @Autowired
    private OrderTaxChecker taxChecker;

    /** 第二层 BeforeEach：commercial MOR 解析与 Risk 放行；tax 响应由各用例按阶段配置。 */
    @BeforeEach
    void setUpClassMocks() {
        mockDefaultPreparation();
    }

    /** 未税价 100、声明国家暂估 5%：ESTIMATED，税 5.00，总额 105.00；请求带声明国家、计税主体与未税行金额。 */
    @Test
    void test_estimated() {
        taxMocker.mock(TaxCalculationStage.PROVISIONAL, TaxCalculationResponseBuilder.ready("0.05"));

        var response = invoke(singleItem()).assertStatus(201).getResponseBody();

        assertAll(() -> assertEquals(OrderAmountStatus.ESTIMATED.getCode(), response.getAmountStatus()),
            () -> assertEquals("5.00", response.getAmounts().getTaxAmount()),
            () -> assertEquals("105.00", response.getAmounts().getTotalAmount()),
            () -> assertNull(response.getTaxBlockReason()));
        long orderId = checkoutChecker.orderId(clientId(), CLIENT_ORDER_ID);
        long calculationId = taxChecker.calculationId(orderId, 1);
        taxChecker.assertRevisions(orderId, 1)
            .assertCalculation(orderId, 1, TaxCalculationStage.PROVISIONAL, TaxCalculationStatus.READY, "US", "5.00",
                "105.00", null)
            .assertQuote(orderId, OrderAmountStatus.ESTIMATED, "5.00", "105.00").assertContext(orderId, calculationId)
            .assertContracts(orderId, List.of(calculationId));
        assertProvisionalRequest();
    }

    /** 多行订单（含行折扣）逐行计税：行税额之和等于头税额，逐行税额落在计算记录的 lines_json。 */
    @Test
    void test_multiLineQuote() {
        taxMocker.mock(TaxCalculationStage.PROVISIONAL, TaxCalculationResponseBuilder.ready("0.15"));

        var response = invoke(template().build()).assertStatus(201).getResponseBody();

        assertAll(() -> assertEquals("6.44", response.getAmounts().getTaxAmount()),
            () -> assertEquals("49.34", response.getAmounts().getTotalAmount()));
        long orderId = checkoutChecker.orderId(clientId(), CLIENT_ORDER_ID);
        var lines = objectMapper.readTree((String) taxChecker.calculations(orderId).getFirst().get("lines_json"));
        assertEquals(2, lines.size());
        var byItem = new HashMap<String, String>();
        lines.forEach(line -> byItem.put(line.get("clientItemId").asString(), line.get("taxAmount").asString()));
        assertEquals(Map.of("line-1", "5.69", "line-2", "0.75"), byItem);
        assertEquals(2, taxMocker.requests().getFirst().getLines().size());
    }

    /** 声明国家无法计税：仍建单（D-02），金额状态 BLOCKED、税额与总额为 null，返回原因码。 */
    @Test
    void test_blocked() {
        taxMocker.mock(TaxCalculationStage.PROVISIONAL,
            TaxCalculationResponseBuilder.blocked(TaxBlockReason.MARKET_NOT_ENABLED));

        var response = invoke(singleItem()).assertStatus(201).getResponseBody();

        assertAll(() -> assertEquals(OrderAmountStatus.BLOCKED.getCode(), response.getAmountStatus()),
            () -> assertNull(response.getAmounts().getTaxAmount()),
            () -> assertNull(response.getAmounts().getTotalAmount()),
            () -> assertEquals(TaxBlockReason.MARKET_NOT_ENABLED.getCode(), response.getTaxBlockReason()));
        long orderId = checkoutChecker.orderId(clientId(), CLIENT_ORDER_ID);
        taxChecker
            .assertCalculation(orderId, 1, TaxCalculationStage.PROVISIONAL, TaxCalculationStatus.BLOCKED, "US", null,
                null, TaxBlockReason.MARKET_NOT_ENABLED.getCode())
            .assertQuote(orderId, OrderAmountStatus.BLOCKED, null, null)
            .assertContracts(orderId, List.of(taxChecker.calculationId(orderId, 1)));
    }

    /** 税务责任方不由平台计税：税额为自报值 0，金额状态 FINAL。 */
    @Test
    void test_notManaged() {
        taxMocker.mock(TaxCalculationStage.PROVISIONAL, TaxCalculationResponseBuilder.notManaged());

        var response = invoke(singleItem()).assertStatus(201).getResponseBody();

        assertAll(() -> assertEquals(OrderAmountStatus.FINAL.getCode(), response.getAmountStatus()),
            () -> assertEquals("0.00", response.getAmounts().getTaxAmount()),
            () -> assertEquals("100.00", response.getAmounts().getTotalAmount()));
        long orderId = checkoutChecker.orderId(clientId(), CLIENT_ORDER_ID);
        taxChecker.assertCalculation(orderId, 1, TaxCalculationStage.PROVISIONAL, TaxCalculationStatus.NOT_MANAGED,
            "US", "0.00", "100.00", null).assertQuote(orderId, OrderAmountStatus.FINAL, "0.00", "100.00");
    }

    /** tax 不可用：503 DEPENDENCY_UNAVAILABLE，不落订单。 */
    @Test
    void test_taxUnavailable() {
        var problem = new ApiProblemResponse("about:blank", HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
            "tax Mock 不可用", CommonErrorCode.SERVICE_UNAVAILABLE.getCode(), "tax-test", "2026-10-29T00:00:00.000000Z",
            null);
        taxMocker.mockFailure(MockHttpFault.json(HttpStatus.SERVICE_UNAVAILABLE.value(), problem));

        invoke(singleItem()).assertProblem(HttpStatus.SERVICE_UNAVAILABLE.value(),
            CommerceCheckoutErrorCode.DEPENDENCY_UNAVAILABLE.getCode());

        checkoutChecker.assertAbsent(clientId(), CLIENT_ORDER_ID);
        taxMocker.assertCalls(1);
    }

    /** 商业关系没有 TAX_RESPONSIBLE 法律 Party：失败关闭为上游协议错误，不调用 Risk 与 tax，不落订单。 */
    @Test
    void test_taxSubjectMissing() {
        preparationMocker.mockResolution(clientId(), "US", "USD", CheckoutContextResponseBuilder.template(clientId())
            .with(body -> body.getRelationshipSnapshot().getBindings()
                .removeIf(binding -> RelationshipRole.TAX_RESPONSIBLE.getCode().equals(binding.getRole())))
            .build());

        invoke(singleItem()).assertProblem(HttpStatus.BAD_GATEWAY.value(),
            CommerceCheckoutErrorCode.UPSTREAM_PROTOCOL_ERROR.getCode());

        checkoutChecker.assertAbsent(clientId(), CLIENT_ORDER_ID);
        riskMocker.assertCalls(0);
        taxMocker.assertCalls(0);
    }

    /** 单行未税价 100.00 的建单请求；商品名称 255 字符（上限），另有说明，传给 tax 的行说明取名称。 */
    private static CreateCheckoutRequestBody singleItem() {
        var line = item("line-1", "n".repeat(255), "1", "100", null);
        line.setDescription("d".repeat(300));
        return template().items(List.of(line)).build();
    }

    /** 建单请求：revision 1、PROVISIONAL、Client 声明国家、MOR 与 OE 纳税主体、未税价行金额。 */
    private void assertProvisionalRequest() {
        taxMocker.assertCalls(1);
        var request = taxMocker.requests().getFirst();
        var line = request.getLines().getFirst();
        assertAll(() -> assertEquals("acquiring", request.getSourceSystem()),
            () -> assertEquals(1, request.getSourceRevision()),
            () -> assertEquals(TaxCalculationStage.PROVISIONAL.getCode(), request.getStage()),
            () -> assertEquals(BusinessMode.MOR.getCode(), request.getBusinessMode()),
            () -> assertEquals(CheckoutContextResponseBuilder.OE_PARTY_ID, request.getTaxSubjectPartyId()),
            () -> assertEquals(CheckoutContextResponseBuilder.OE_PARTY_ID, request.getSellerPartyId()),
            () -> assertEquals(Long.toString(clientId()), request.getBuyerClientId()),
            () -> assertEquals(CheckoutContextResponseBuilder.ENTITY_NO, request.getEntityNo()),
            () -> assertEquals(TaxPriceMode.EXCLUSIVE.getCode(), request.getPriceMode()),
            () -> assertEquals("2026-09-08", request.getTaxPointDate()),
            () -> assertEquals(TaxEvidenceType.DECLARED_COUNTRY.getCode(), request.getEvidence().getFirst().getType()),
            () -> assertEquals("US", request.getEvidence().getFirst().getCountryCode()),
            () -> assertEquals("line-1", line.getLineRef()), () -> assertEquals("100", line.getLineAmount()),
            () -> assertEquals("STANDARD", line.getCategoryCode()),
            () -> assertEquals("n".repeat(255), line.getDescription()));
    }
}
