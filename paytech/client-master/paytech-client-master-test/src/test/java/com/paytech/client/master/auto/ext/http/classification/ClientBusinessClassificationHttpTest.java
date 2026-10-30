package com.paytech.client.master.auto.ext.http.classification;

import org.junit.jupiter.api.Test;

import com.paytech.client.master.api.classification.ClientBusinessClassificationApi;
import com.paytech.client.master.api.enums.CheckoutCandidateErrorCode;
import com.paytech.client.master.test.base.checkout.AbstractCheckoutCandidateApiTest;
import com.paytech.client.master.test.checker.checkout.CheckoutCandidateResponseChecker;
import com.paytech.client.master.test.mocker.checkout.CheckoutCandidateFixture;
import com.paytech.foundation.common.error.CommonErrorCode;

/**
 * Client 主经营分类的服务间查询（REQ-02 AC-09 的 MCC 来源）：已确认返回主分类，未确认或无经营资料返回 confirmed=false 且两码为空
 * <li>夹具：Client 910000000000001 的经营资料已确认，主分类 SOFTWARE / 5734。</li>
 *
 * @see ClientBusinessClassificationApi#getBusinessClassification(String)
 */
class ClientBusinessClassificationHttpTest extends AbstractCheckoutCandidateApiTest {
    private static final String PATH = "/client-master/v1/clients/" + CheckoutCandidateFixture.CLIENT_ID
        + "/business-classification";

    /** 已确认：返回主分类的行业与 MCC；只读，不改主档、不登记补偿任务。 */
    @Test
    void test_confirmedPrimary() {
        fixture.assertReadOnly(() -> get(PATH, headers()).assertOk()
            .assertText("/clientId", Long.toString(CheckoutCandidateFixture.CLIENT_ID)).assertBoolean("/confirmed", true)
            .assertText("/industryCode", "SOFTWARE").assertText("/mccCode", "5734"));
    }

    /** 已确认但没有主分类（primary_slot 为空）：confirmed=true，两码为空。 */
    @Test
    void test_confirmedWithoutPrimary() {
        data.primaryClassification(null);

        assertNoCodes(get(PATH, headers()).assertOk().assertBoolean("/confirmed", true));
    }

    /** 分类未确认：confirmed=false，不返回分类码。 */
    @Test
    void test_notConfirmed() {
        data.classificationConfirmedTime(null);

        assertNoCodes(get(PATH, headers()).assertOk().assertBoolean("/confirmed", false));
    }

    /** 没有经营资料：200，confirmed=false，两码为空。 */
    @Test
    void test_noBusinessProfile() {
        data.removeBusinessProfile();

        assertNoCodes(get(PATH, headers()).assertOk().assertBoolean("/confirmed", false));
    }

    /** Client 不存在 404 CLIENT_NOT_FOUND；非规范 Client ID 在读取前 400 拒绝。 */
    @Test
    void test_clientNotFoundAndInvalidId() {
        get("/client-master/v1/clients/910000000000999/business-classification", headers())
            .assertProblem(404, CheckoutCandidateErrorCode.CLIENT_NOT_FOUND.getCode());
        get("/client-master/v1/clients/01/business-classification", headers())
            .assertProblem(400, CommonErrorCode.INVALID_REQUEST.getCode());
    }

    /** 两码均为 JSON null。 */
    static void assertNoCodes(CheckoutCandidateResponseChecker response) {
        response.assertNull("/industryCode").assertNull("/mccCode");
    }
}
