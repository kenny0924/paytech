package com.paytech.tax.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.tax.api.TaxCalculationApi;
import com.paytech.tax.api.enums.TaxAmountSource;
import com.paytech.tax.api.enums.TaxCalculationStage;
import com.paytech.tax.api.enums.TaxCalculationStatus;
import com.paytech.tax.api.enums.TaxErrorCode;
import com.paytech.tax.api.enums.TaxEvidenceType;
import com.paytech.tax.api.enums.TaxNotManagedReason;
import com.paytech.tax.api.enums.TaxPriceMode;
import com.paytech.tax.api.enums.TaxTreatment;
import com.paytech.tax.api.request.CalculateTaxRequestBody;
import com.paytech.tax.core.constant.TaxCodes;
import com.paytech.tax.core.enums.FilingFrequency;
import com.paytech.tax.core.enums.MarketCollectionStatus;
import com.paytech.tax.core.enums.MarketObligation;
import com.paytech.tax.test.base.MockTestBase;
import com.paytech.tax.test.builder.CalculateTaxRequestBuilder;
import com.paytech.tax.test.builder.TaxTestConstants;
import com.paytech.tax.test.checker.TaxResponseChecker;

import tools.jackson.databind.JsonNode;

/**
 * 计算成功路径：三国未税与含税（AC-01、AC-02）、多行折扣尾差（AC-06）、两阶段证据选择（AC-03 的 tax 侧）、NOT_MANAGED（AC-04）、
 * 获准不收、同身份幂等与冲突、参数校验
 * <li>测试主体在 SA / AE / EG 各有 USD 申报的注册与准入，地区包复用初始化 DML 的演示包（以真实规则内容计算）。</li>
 *
 * @see TaxCalculationApi#calculateTax(CalculateTaxRequestBody)
 */
class CalculateTaxTest extends MockTestBase {
    /** 计算接口路径。 */
    static final String PATH = "/tax/v1/calculations";

    /** SA 注册 ID。 */
    static final long SA_REGISTRATION = 1001L;

    /** 测试主体在三国的注册与准入。 */
    @BeforeEach
    void setUpClassMocks() {
        subjects.registered(SA_REGISTRATION, "SA", TaxTestConstants.SEED_POLICY_SA, TaxTestConstants.USD, "Asia/Riyadh",
            FilingFrequency.QUARTERLY);
        subjects.registered(1011L, "AE", TaxTestConstants.SEED_POLICY_AE, TaxTestConstants.USD, "Asia/Dubai",
            FilingFrequency.QUARTERLY);
        subjects.registered(1021L, "EG", TaxTestConstants.SEED_POLICY_EG, TaxTestConstants.USD, "Africa/Cairo",
            FilingFrequency.MONTHLY);
    }

    /** AC-01：SA 账单国家、未税价 100 → 税 15、总额 115；FINAL 报价有到期时间；汇总组件可作事件 taxComponents。 */
    @Test
    void test_saExclusiveFinalReady() {
        TaxResponseChecker response = post(PATH, CalculateTaxRequestBuilder.template().build()).assertSuccess(200)
            .assertText("status", TaxCalculationStatus.READY.getCode()).assertText("stage", TaxCalculationStage.FINAL.getCode())
            .assertText("taxAmountSource", TaxAmountSource.COMPUTED.getCode()).assertText("netAmount", "100.00")
            .assertText("taxAmount", "15.00").assertText("roundingAmount", "0.00").assertText("grossAmount", "115.00")
            .assertText("taxPointBasis", TaxCodes.TAX_POINT_PAYMENT);
        assertNotNull(response.text("expiresAt"), "FINAL 且 READY 的报价必须有到期时间");
        JsonNode component = response.json().path("lines").get(0).path("components").get(0);
        assertEquals("SA_REMOTE_B2C_STANDARD", component.path("ruleCode").asString());
        assertEquals(TaxTreatment.TAXABLE.getCode(), component.path("treatment").asString());
        assertEquals("0.15", component.path("rate").asString());
        assertEquals(Long.toString(SA_REGISTRATION), component.path("registrationId").asString());
        assertEquals("Item 1", response.json().path("lines").get(0).path("description").asString());
        assertNull(response.text("notManagedReason"));
        JsonNode summary = response.json().path("components");
        assertEquals(1, summary.size());
        assertEquals("SA", summary.get(0).path("jurisdictionCode").asString());
        assertEquals("15.00", summary.get(0).path("taxAmount").asString());
        assertEquals(Long.toString(TaxTestConstants.SUBJECT_PARTY_ID), summary.get(0).path("taxSubjectPartyId").asString());
        Map<String, Object> stored = testMapper.selectCalculation(response.id("calculationId"));
        assertEquals(0, new BigDecimal("15").compareTo((BigDecimal)stored.get("charged_tax_amount")));
        assertEquals(1, testMapper.countCalculations());
    }

    /** AC-02 未税：AE 5% 与 EG 14%。 */
    @ParameterizedTest
    @CsvSource({"AE, 5.00, 105.00", "EG, 14.00, 114.00"})
    void test_exclusiveByCountry(String country, String tax, String gross) {
        post(PATH, CalculateTaxRequestBuilder.template().country(country).build()).assertSuccess(200)
            .assertText("status", TaxCalculationStatus.READY.getCode()).assertText("taxAmount", tax)
            .assertText("grossAmount", gross);
    }

    /** AC-02 含税价 100：SA 86.96 / 13.04，AE 95.24 / 4.76，EG 87.72 / 12.28（cases.md 第 1 节）。 */
    @ParameterizedTest
    @CsvSource({"SA, 86.96, 13.04", "AE, 95.24, 4.76", "EG, 87.72, 12.28"})
    void test_inclusiveByCountry(String country, String net, String tax) {
        TaxResponseChecker response = post(PATH, CalculateTaxRequestBuilder.template().country(country)
            .priceMode(TaxPriceMode.INCLUSIVE).build()).assertSuccess(200)
            .assertText("status", TaxCalculationStatus.READY.getCode()).assertText("netAmount", net)
            .assertText("taxAmount", tax).assertText("grossAmount", "100.00");
        JsonNode component = response.json().path("lines").get(0).path("components").get(0);
        assertEquals("PERCENTAGE_INCLUSIVE", component.path("calculationMethod").asString());
        assertEquals(net, component.path("taxBase").asString());
    }

    /** AC-06：三行含折扣，逐行取整，行税额之和等于头税额，尾差记在行上。 */
    @Test
    void test_threeLinesWithDiscountRounding() {
        TaxResponseChecker response = post(PATH, CalculateTaxRequestBuilder.template().clearLines()
            .line(1, "1", "33.33", "0").line(2, "3", "11.11", "0").line(3, "1", "40.00", "6.66").build())
            .assertSuccess(200).assertText("netAmount", "100.00").assertText("taxAmount", "15.00")
            .assertText("grossAmount", "115.00");
        List<Map<String, Object>> lines = testMapper.selectLines(response.id("calculationId"));
        assertEquals(3, lines.size());
        BigDecimal lineTax = lines.stream().map(line -> (BigDecimal)line.get("charged_tax_amount"))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("15.00").compareTo(lineTax));
        assertEquals(0, new BigDecimal("0.0005").compareTo((BigDecimal)lines.get(0).get("rounding_delta")));
        assertEquals(0, new BigDecimal("0.0005").compareTo((BigDecimal)lines.get(1).get("rounding_delta")));
        assertEquals(0, new BigDecimal("-0.001").compareTo((BigDecimal)lines.get(2).get("rounding_delta")));
    }

    /** PROVISIONAL 按 Client 声明国家（AE 5%）暂估，不设到期时间；供应地证据为 DECLARED_COUNTRY。 */
    @Test
    void test_provisionalUsesDeclaredCountry() {
        TaxResponseChecker response = post(PATH, CalculateTaxRequestBuilder.template().revision(1)
            .stage(TaxCalculationStage.PROVISIONAL).evidence(TaxEvidenceType.DECLARED_COUNTRY, "AE")
            .evidence(TaxEvidenceType.BILLING_COUNTRY, "SA").build()).assertSuccess(200)
            .assertText("stage", TaxCalculationStage.PROVISIONAL.getCode()).assertText("taxAmount", "5.00");
        assertNull(response.text("expiresAt"), "暂估报价不可支付，不设到期时间");
        for (JsonNode evidence : response.json().path("evidence")) {
            boolean declared = TaxEvidenceType.DECLARED_COUNTRY.getCode().equals(evidence.path("type").asString());
            assertEquals(declared, evidence.path("placeOfSupplyBasis").asBoolean());
        }
    }

    /** FINAL 按消费者账单国家（SA 15%），与 Client 声明国家无关。 */
    @Test
    void test_finalUsesBillingCountry() {
        post(PATH, CalculateTaxRequestBuilder.template().evidence(TaxEvidenceType.DECLARED_COUNTRY, "AE")
            .evidence(TaxEvidenceType.BILLING_COUNTRY, "SA").build()).assertSuccess(200).assertText("taxAmount", "15.00");
    }

    /** AC-04：税务责任方没有任何准入 → NOT_MANAGED，税额为自报值，没有组件。 */
    @Test
    void test_notManagedUsesDeclaredTax() {
        TaxResponseChecker response = post(PATH, CalculateTaxRequestBuilder.template()
            .taxSubject(TaxTestConstants.UNMANAGED_PARTY_ID).declaredTaxAmount("7").build()).assertSuccess(200)
            .assertText("status", TaxCalculationStatus.NOT_MANAGED.getCode())
            .assertText("taxAmountSource", TaxAmountSource.DECLARED.getCode()).assertText("taxAmount", "7.00")
            .assertText("grossAmount", "107.00");
        response.assertText("notManagedReason", TaxNotManagedReason.SUBJECT_NOT_REGISTERED.getCode());
        assertEquals(0, response.json().path("components").size());
        assertTrue(response.json().path("lines").get(0).path("taxAmount").isNull());
        assertNull(response.text("expiresAt"));
        assertEquals(List.of(), testMapper.selectComponents(response.id("calculationId")));
        assertEquals(0, testMapper.countEvidence(response.id("calculationId")), "NOT_MANAGED 不建证据");
    }

    /** CLIENT 来源 OE 未传税务责任方：NOT_MANAGED（TAX_RESPONSIBLE_UNRESOLVED），自报税额缺省 0，落库且责任方为空；同内容重放返回原计算。 */
    @Test
    void test_taxResponsibleUnresolved() {
        TaxResponseChecker response = post(PATH, CalculateTaxRequestBuilder.template().taxSubject(null).build())
            .assertSuccess(200).assertText("status", TaxCalculationStatus.NOT_MANAGED.getCode())
            .assertText("taxAmountSource", TaxAmountSource.DECLARED.getCode())
            .assertText("notManagedReason", TaxNotManagedReason.TAX_RESPONSIBLE_UNRESOLVED.getCode())
            .assertText("taxAmount", "0.00").assertText("grossAmount", "100.00");
        long calculationId = response.id("calculationId");
        assertNull(response.text("taxSubjectPartyId"));
        assertEquals(0, response.json().path("components").size());
        assertNull(testMapper.selectTaxSubject(calculationId));
        assertEquals(List.of(), testMapper.selectComponents(calculationId));
        assertEquals(0, testMapper.countEvidence(calculationId));

        post(PATH, CalculateTaxRequestBuilder.template().taxSubject(null).build()).assertSuccess(200)
            .assertText("calculationId", Long.toString(calculationId));
        post(PATH, CalculateTaxRequestBuilder.template().taxSubject(null).declaredTaxAmount("1.00").build())
            .assertProblem(409, TaxErrorCode.TAX_CALCULATION_CONFLICT.getCode());
    }

    /** 准入 NO_COLLECTION_REQUIRED：READY、税 0，组件 NOT_COLLECTED 且无税率，不进入汇总。 */
    @Test
    void test_noCollectionRequired() {
        subjects.market(1031L, "KW", MarketObligation.NO_COLLECTION_REQUIRED, MarketCollectionStatus.ENABLED, null);

        TaxResponseChecker response = post(PATH, CalculateTaxRequestBuilder.template().country("KW").build())
            .assertSuccess(200).assertText("status", TaxCalculationStatus.READY.getCode()).assertText("taxAmount", "0.00")
            .assertText("grossAmount", "100.00");
        JsonNode component = response.json().path("lines").get(0).path("components").get(0);
        assertEquals(TaxTreatment.NOT_COLLECTED.getCode(), component.path("treatment").asString());
        assertEquals(TaxCodes.DEFAULT_NOT_COLLECTED_REASON, component.path("reasonCode").asString());
        assertTrue(component.path("rate").isNull());
        assertEquals(0, response.json().path("components").size());
    }

    /** 同一身份同内容：返回原计算，不重算。 */
    @Test
    void test_sameIdentitySameContentReturnsOriginal() {
        String first = post(PATH, CalculateTaxRequestBuilder.template().build()).assertSuccess(200).text("calculationId");

        post(PATH, CalculateTaxRequestBuilder.template().build()).assertSuccess(200).assertText("calculationId", first);

        assertEquals(1, testMapper.countCalculations());
    }

    /** 同一身份不同内容：409 TAX_CALCULATION_CONFLICT，原计算保留。 */
    @Test
    void test_sameIdentityDifferentContentConflicts() {
        post(PATH, CalculateTaxRequestBuilder.template().build()).assertSuccess(200);

        post(PATH, CalculateTaxRequestBuilder.template().clearLines().line(1, "1", "120.00", "0").build())
            .assertProblem(409, TaxErrorCode.TAX_CALCULATION_CONFLICT.getCode());

        assertEquals(1, testMapper.countCalculations());
    }

    /** 行金额不等于数量 × 单价 − 折扣、金额超过币种精度、行号重复：400 INVALID_REQUEST；字段注解不符：422；均不落库。 */
    @Test
    void test_invalidParameter() {
        post(PATH, CalculateTaxRequestBuilder.template().clearLines()
            .line(1, TaxTestConstants.SAAS_AUTOMATED, "1", "100.00", "0", "99.00").build())
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST.getCode());
        post(PATH, CalculateTaxRequestBuilder.template().clearLines()
            .line(1, TaxTestConstants.SAAS_AUTOMATED, "1", "100.001", "0", "100.001").build())
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST.getCode());
        post(PATH, CalculateTaxRequestBuilder.template().line(1, "1", "5.00", "0").build())
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST.getCode());
        post(PATH, CalculateTaxRequestBuilder.template().revision(null).build())
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR.getCode());

        assertEquals(0, testMapper.countCalculations());
    }
}
