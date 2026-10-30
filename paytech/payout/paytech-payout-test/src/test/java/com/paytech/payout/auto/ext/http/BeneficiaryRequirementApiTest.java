package com.paytech.payout.auto.ext.http;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.payout.api.enums.PayoutErrorCode;
import com.paytech.payout.test.base.MockTestBase;
import com.paytech.payout.test.checker.PayoutResponseChecker;

import tools.jackson.databind.JsonNode;

/**
 * 字段要求查询（R5、Q-1）：精确国家条目、SWIFT 通配国家条目与不支持组合
 *
 * @see com.paytech.payout.api.beneficiary.BeneficiaryRequirementApi#getBeneficiaryRequirement(String, String, String)
 */
class BeneficiaryRequirementApiTest extends MockTestBase {
    /** 字段要求路径。 */
    static final String PATH = "/payout/v1/beneficiary-requirements";

    /** AE 本地 USD：户名、IBAN（AE + 21 位）、银行名称。 */
    @Test
    void test_exactRoute() {
        PayoutResponseChecker response = get(PATH + "?railType=LOCAL&bankCountry=AE&currency=USD").assertSuccess(200);
        JsonNode iban = response.json().get("fields").get(1);
        assertAll(() -> assertEquals(List.of("holderName", "iban", "bankName"), codes(response)),
            () -> assertEquals("AE[0-9]{21}", iban.get("pattern").asString()),
            () -> assertTrue(iban.get("required").asBoolean()));
    }

    /** SWIFT 任一国家：回显请求国家，iban 与 accountNumber 二选一。 */
    @Test
    void test_swiftWildcard() {
        PayoutResponseChecker response = get(PATH + "?railType=SWIFT&bankCountry=DE&currency=EUR").assertSuccess(200)
            .assertText("bankCountry", "DE");
        JsonNode fields = response.json().get("fields");
        assertAll(() -> assertEquals("accountIdentifier", fields.get(1).get("oneOfGroup").asString()),
            () -> assertEquals("accountIdentifier", fields.get(2).get("oneOfGroup").asString()),
            () -> assertEquals(List.of("holderName", "iban", "accountNumber", "swiftBic", "bankName"), codes(response)));
    }

    /** 清单外组合 404；参数非法 400。 */
    @Test
    void test_unsupported() {
        get(PATH + "?railType=LOCAL&bankCountry=FR&currency=USD").assertError(404, PayoutErrorCode.BENEFICIARY_REQUIREMENT_NOT_FOUND);
        get(PATH + "?railType=SWIFT&bankCountry=DE&currency=GBP").assertError(404, PayoutErrorCode.BENEFICIARY_REQUIREMENT_NOT_FOUND);
        get(PATH + "?railType=ACH&bankCountry=US&currency=USD").assertInvalidRequest();
    }

    /** 字段代码列表。 */
    static List<String> codes(PayoutResponseChecker response) {
        List<String> codes = new ArrayList<>();
        response.json().get("fields").forEach(field -> codes.add(field.get("code").asString()));
        return codes;
    }
}
