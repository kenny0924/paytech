package com.paytech.tax.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.tax.api.TaxCalculationApi;
import com.paytech.tax.api.enums.TaxBlockReason;
import com.paytech.tax.api.enums.TaxCalculationStatus;
import com.paytech.tax.api.enums.TaxEvidenceType;
import com.paytech.tax.api.request.CalculateTaxRequestBody;
import com.paytech.tax.core.enums.FilingFrequency;
import com.paytech.tax.core.enums.MarketCollectionStatus;
import com.paytech.tax.core.enums.MarketObligation;
import com.paytech.tax.core.enums.TaxCategoryStatus;
import com.paytech.tax.test.base.MockTestBase;
import com.paytech.tax.test.builder.CalculateTaxRequestBuilder;
import com.paytech.tax.test.builder.TaxTestConstants;
import com.paytech.tax.test.checker.TaxResponseChecker;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * BLOCKED（AC-05）：主体是平台纳税主体但供应地未启用、税类未知、规则缺失、证据缺失或地区包不合法；返回 200 与原因，不以零税成功
 * <li>BLOCKED 的税额、尾差与总额为空，原因码与路径不含个人原值。</li>
 *
 * @see TaxCalculationApi#calculateTax(CalculateTaxRequestBody)
 */
class CalculateTaxBlockedTest extends MockTestBase {
    /** 测试主体在 SA 已启用（BLOCKED 用例在其他国家或其他条件下发生）。 */
    @BeforeEach
    void setUpClassMocks() {
        subjects.registered(1001L, "SA", TaxTestConstants.SEED_POLICY_SA, TaxTestConstants.USD, "Asia/Riyadh",
            FilingFrequency.QUARTERLY);
    }

    /** 供应地没有准入。 */
    @Test
    void test_marketMissingForCountry() {
        assertBlocked(post(CalculateTaxTest.PATH, CalculateTaxRequestBuilder.template().country("KW").build()),
            TaxBlockReason.MARKET_NOT_ENABLED, "market.KW");
    }

    /** 供应地准入 collection_status=BLOCKED。 */
    @Test
    void test_marketCollectionBlocked() {
        subjects.market(1041L, "EG", MarketObligation.REGISTRATION_REQUIRED, MarketCollectionStatus.BLOCKED,
            TaxTestConstants.SEED_POLICY_EG);

        assertBlocked(post(CalculateTaxTest.PATH, CalculateTaxRequestBuilder.template().country("EG").build()),
            TaxBlockReason.MARKET_NOT_ENABLED, "market.EG");
    }

    /** 税类不在目录：不默认。 */
    @Test
    void test_unknownTaxCategory() {
        assertBlocked(post(CalculateTaxTest.PATH, CalculateTaxRequestBuilder.template().clearLines()
            .line(1, "UNREGISTERED_CATEGORY", "1", "100.00", "0", "100.00").build()),
            TaxBlockReason.UNKNOWN_TAX_CATEGORY, "lines[1].categoryCode");
    }

    /** S-13：Client 自由填写的税类（含空格、中文）不是参数错误，按未知税类 BLOCKED，并逐行给出行号。 */
    @Test
    void test_freeTextCategoryBlocked() {
        assertBlocked(post(CalculateTaxTest.PATH, CalculateTaxRequestBuilder.template().clearLines()
            .line(1, "Online course (en)", "1", "60.00", "0", "60.00").line(2, "在线课程", "1", "40.00", "0", "40.00").build()),
            TaxBlockReason.UNKNOWN_TAX_CATEGORY, "lines[1].categoryCode", "lines[2].categoryCode");
    }

    /** 小写自由文本税类（例如 standard）不是参数错误：BLOCKED(UNKNOWN_TAX_CATEGORY)，不是 400。 */
    @Test
    void test_lowercaseFreeTextCategoryBlocked() {
        assertBlocked(post(CalculateTaxTest.PATH, CalculateTaxRequestBuilder.template().clearLines()
            .line(1, "standard", "1", "100.00", "0", "100.00").build()), TaxBlockReason.UNKNOWN_TAX_CATEGORY,
            "lines[1].categoryCode");
    }

    /** 税类超过 64 字符：字段校验 422。 */
    @Test
    void test_categoryTooLong() {
        post(CalculateTaxTest.PATH, CalculateTaxRequestBuilder.template().clearLines()
            .line(1, "X".repeat(65), "1", "100.00", "0", "100.00").build())
            .assertProblem(422, CommonErrorCode.VALIDATION_ERROR.getCode());
    }

    /** 税类有效但地区包没有该税类的规则。 */
    @Test
    void test_noRule() {
        subjects.category(1051L, "HUMAN_CONSULTING", TaxCategoryStatus.ACTIVE);

        assertBlocked(post(CalculateTaxTest.PATH, CalculateTaxRequestBuilder.template().clearLines()
            .line(1, "HUMAN_CONSULTING", "1", "100.00", "0", "100.00").build()), TaxBlockReason.NO_RULE, "lines[1]");
    }

    /** FINAL 缺账单国家证据。 */
    @Test
    void test_missingBillingCountry() {
        assertBlocked(post(CalculateTaxTest.PATH, CalculateTaxRequestBuilder.template().clearEvidence()
            .evidence(TaxEvidenceType.DECLARED_COUNTRY, "SA").build()), TaxBlockReason.MISSING_EVIDENCE,
            "evidence.BILLING_COUNTRY");
    }

    /** 准入指向的地区包规则重叠（不互斥）：POLICY_INVALID。 */
    @Test
    void test_policyInvalid() {
        ObjectNode policy = (ObjectNode)objectMapper.readTree(subjects.seedPolicyJson(TaxTestConstants.SEED_POLICY_SA));
        ArrayNode treatments = (ArrayNode)policy.path("treatments");
        ((ObjectNode)treatments.get(0).path("when")).put("placeOfSupply", "BH");
        ObjectNode duplicate = treatments.addObject();
        duplicate.put("ruleCode", "BH_DUPLICATE");
        duplicate.putObject("when").put("placeOfSupply", "BH");
        duplicate.putObject("result").put("componentCode", "VAT").put("treatment", "TAXABLE").put("liableRole", "SELLER")
            .put("reasonCode", "BH_DUPLICATE").put("rate", "0.10");
        String overlapping = objectMapper.writeValueAsString(policy);
        subjects.policy(1061L, "BH", overlapping);
        subjects.registered(1071L, "BH", 1061L, TaxTestConstants.USD, "Asia/Bahrain", FilingFrequency.QUARTERLY);

        assertBlocked(post(CalculateTaxTest.PATH, CalculateTaxRequestBuilder.template().country("BH").build()),
            TaxBlockReason.POLICY_INVALID, "market.BH.policy");
    }

    /** BLOCKED：200、原因与路径；未税价时净额保留，税额、尾差与总额为空，没有组件。 */
    void assertBlocked(TaxResponseChecker response, TaxBlockReason reason, String... paths) {
        response.assertSuccess(200).assertText("status", TaxCalculationStatus.BLOCKED.getCode())
            .assertText("netAmount", "100.00");
        assertTrue(response.json().path("taxAmount").isNull(), "BLOCKED 不能以零税表示成功");
        assertTrue(response.json().path("grossAmount").isNull());
        assertTrue(response.json().path("roundingAmount").isNull());
        assertEquals(paths.length, response.json().path("blockReasons").size());
        for (int index = 0; index < paths.length; index++) {
            assertEquals(reason.getCode(), response.json().path("blockReasons").get(index).path("code").asString());
            assertEquals(paths[index], response.json().path("blockReasons").get(index).path("path").asString());
        }
        assertEquals(0, response.json().path("components").size());
        assertEquals(0, response.json().path("lines").get(0).path("components").size());
    }
}
