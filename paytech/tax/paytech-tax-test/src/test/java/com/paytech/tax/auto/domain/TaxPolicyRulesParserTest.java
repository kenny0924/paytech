package com.paytech.tax.auto.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.tax.api.enums.TaxTreatment;
import com.paytech.tax.domain.policy.TaxPolicyInvalidException;
import com.paytech.tax.domain.policy.TaxPolicyRules;
import com.paytech.tax.domain.policy.TaxPolicyRulesParser;

/**
 * 地区包 Schema 与互斥校验（纯函数，technique 15.1）
 *
 * @see TaxPolicyRulesParser#parse(String)
 */
class TaxPolicyRulesParserTest {
    /** 与初始化 DML DEMO_SA_SAAS 同结构的规则包，规则以 %s 占位。 */
    static final String TEMPLATE = """
        {"schemaVersion":1,
         "applicability":{"supplyKinds":["SALE"],"notCollectedReasonCode":"SA_NO_COLLECTION_REQUIRED","remarks":"demo"},
         "placeOfSupply":{"algorithm":"BUYER_COUNTRY"},
         "taxPoint":{"basis":"PAYMENT","quoteValidityHours":24},
         "treatments":[%s],
         "unmatchedBehavior":"BLOCK",
         "calculation":{"method":"PERCENTAGE","roundingMethod":"HALF_UP","roundingLevel":"LINE"},
         "documents":{"receipt":{"seriesPrefix":"SA-RCPT","numberFormat":"{series}-{number:06}",
           "templateCode":"SA_SIMPLIFIED_TAX_INVOICE","templateRevision":1,"titleKey":"tax.receipt.title.SA",
           "legalTextKeys":["tax.receipt.legal.noBuyerData.SA"],"submission":"NOT_REQUIRED"}},
         "reporting":{"reportingRuleCode":"SA_VAT_OUTPUT_STANDARD","dueDateRule":"END_OF_NEXT_MONTH"}}
        """;

    /** SA 标准规则（IN 与 EQ 混用）。 */
    static final String SA_RULE = """
        {"ruleCode":"SA_REMOTE_B2C_STANDARD","when":{"supplyKind":["SALE"],"categoryCode":["SAAS_AUTOMATED"],
         "placeOfSupply":"SA","buyerTaxpayerStatus":["NON_REGISTERED"]},
         "result":{"componentCode":"VAT","treatment":"TAXABLE","liableRole":"SELLER","reasonCode":"SA_REMOTE_B2C_STANDARD",
         "rate":"0.15"}}
        """;

    /** 合法规则包：匹配、税率、取整、开票与申报规则。 */
    @Test
    void test_validPolicy() {
        TaxPolicyRules rules = TaxPolicyRulesParser.parse(TEMPLATE.formatted(SA_RULE));

        assertEquals(1, rules.match(Map.of("supplyKind", "SALE", "categoryCode", "SAAS_AUTOMATED", "placeOfSupply", "SA",
            "buyerTaxpayerStatus", "NON_REGISTERED")).size());
        assertEquals(0, rules.match(Map.of("supplyKind", "SALE", "categoryCode", "SAAS_AUTOMATED", "placeOfSupply", "AE",
            "buyerTaxpayerStatus", "NON_REGISTERED")).size());
        assertEquals(TaxTreatment.TAXABLE, rules.getTreatments().get(0).getTreatment());
        assertEquals(0, new BigDecimal("0.15").compareTo(rules.getTreatments().get(0).getRate()));
        assertEquals(RoundingMode.HALF_UP, rules.getRoundingMode());
        assertNull(rules.getRoundingScale());
        assertEquals("SA-RCPT-2026-000001", rules.getReceipt().formatNumber("SA-RCPT-2026", 1));
        assertEquals("END_OF_NEXT_MONTH", rules.getDueDateRule());
    }

    /** 两条规则在全部字段上可同时命中：不互斥，拒绝。 */
    @Test
    void test_overlappingRulesRejected() {
        String other = SA_RULE.replace("SA_REMOTE_B2C_STANDARD\",\"when", "SA_OTHER\",\"when")
            .replace("\"placeOfSupply\":\"SA\",", "");
        assertInvalid(TEMPLATE.formatted(SA_RULE + "," + other));
    }

    /** 两条规则税类不同：互斥，接受。 */
    @Test
    void test_disjointRulesAccepted() {
        String other = SA_RULE.replace("SA_REMOTE_B2C_STANDARD\",\"when", "SA_OTHER\",\"when")
            .replace("[\"SAAS_AUTOMATED\"]", "[\"DIGITAL_GOODS\"]");
        assertEquals(2, TaxPolicyRulesParser.parse(TEMPLATE.formatted(SA_RULE + "," + other)).getTreatments().size());
    }

    /** 未登记的区块、条件字段与取值一律拒绝。 */
    @Test
    void test_unknownFieldsRejected() {
        assertInvalid(TEMPLATE.formatted(SA_RULE).replace("\"unmatchedBehavior\"", "\"filing\":{},\"unmatchedBehavior\""));
        assertInvalid(TEMPLATE.formatted(SA_RULE.replace("\"supplyKind\"", "\"merchantCategory\"")));
        assertInvalid(TEMPLATE.formatted(SA_RULE).replace("BUYER_COUNTRY", "IP_COUNTRY"));
        assertInvalid(TEMPLATE.formatted(SA_RULE).replace("\"schemaVersion\":1", "\"schemaVersion\":2"));
        assertInvalid("not json");
    }

    /** TAXABLE 必须有 (0,1) 税率；免税等处理不得虚构税率。 */
    @Test
    void test_rateRules() {
        assertInvalid(TEMPLATE.formatted(SA_RULE.replace(",\n \"rate\":\"0.15\"", "").replace(",\"rate\":\"0.15\"", "")
            .replace("\"rate\":\"0.15\"", "\"rate\":null")));
        assertInvalid(TEMPLATE.formatted(SA_RULE.replace("\"TAXABLE\"", "\"EXEMPT\"")));
        assertInvalid(TEMPLATE.formatted(SA_RULE.replace("\"0.15\"", "\"1.5\"")));
        assertTrue(TaxPolicyRulesParser.parse(TEMPLATE.formatted(SA_RULE.replace("\"TAXABLE\"", "\"EXEMPT\"")
            .replace("\"0.15\"", "null"))).getTreatments().get(0).getRate() == null);
    }

    /** PERIOD_END_PLUS_DAYS 必须给出天数。 */
    @Test
    void test_dueDateRule() {
        assertInvalid(TEMPLATE.formatted(SA_RULE).replace("END_OF_NEXT_MONTH", "PERIOD_END_PLUS_DAYS"));
        assertEquals(28, TaxPolicyRulesParser.parse(TEMPLATE.formatted(SA_RULE)
            .replace("\"END_OF_NEXT_MONTH\"", "\"PERIOD_END_PLUS_DAYS\",\"dueDays\":28")).getDueDays());
    }

    /** 断言不合法。 */
    static void assertInvalid(String policyJson) {
        assertThrows(TaxPolicyInvalidException.class, () -> TaxPolicyRulesParser.parse(policyJson));
    }
}
