package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.client.master.test.base.onboarding.AbstractClientReviewTest;
import com.paytech.client.master.test.mocker.onboarding.OnboardingFixtures;

/**
 * O02 申请资料部分：真实 HTTP 进入，按审核任务读取提交后的申请资料；只读，不 Reveal 联系方式。
 *
 * @see com.paytech.client.contract.api.onboarding.ClientApplicationAdminApi
 */
class ClientApplicationAdminTest extends AbstractClientReviewTest {

    /** 审核中的申请：返回申请标识、状态、主体资料（含业务信息补采四项）与材料引用；联系方式分区为空，不可编辑。 */
    @Test
    void test_normalFlow() {
        var review = applications.underReview(ACCOUNT_ID);
        mapper.businessInfo(review.app().clientId(), "CARD,STC_PAY", null, OnboardingFixtures.INSTANT, null);
        long fileId = applications.nextId();
        long documentId = applications.document(review.app(), review.app().partyId(), "IDENTITY", "PASSPORT", fileId, "a".repeat(64));

        var body = body(application(review.caseId()));

        assertEquals(Long.toString(review.app().applicationId()), body.path("applicationId").asString());
        assertEquals(Long.toString(review.app().clientId()), body.path("clientId").asString());
        assertEquals("UNDER_REVIEW", body.path("status").asString());
        assertFalse(body.path("editable").asBoolean());
        assertTrue(body.path("values").path("individual").size() > 0);
        assertEquals(0, body.path("values").path("contact").size());
        assertEquals(Long.toString(documentId), body.path("documents").get(0).path("documentId").asString());
        assertEquals("/files/v1/" + fileId, body.path("documents").get(0).path("fileAddress").asString());
        var business = body.path("values").path("business");
        assertEquals("CARD,STC_PAY", business.path("expectedPaymentMethods").asString());
        assertEquals(OnboardingFixtures.INSTANT, business.path("fulfillmentType").asString());
        assertTrue(business.has("refundPolicyUrl") && business.path("refundPolicyUrl").isNull(), business.toString());
        assertTrue(business.has("fulfillmentDays") && business.path("fulfillmentDays").isNull(), business.toString());
    }

    /** 未知任务 404；非法任务 ID 按不存在处理；缺少会话 400。 */
    @Test
    void test_invalidParameter() {
        var review = applications.underReview(ACCOUNT_ID);

        assertEquals("REVIEW_CASE_NOT_FOUND", body(application(applications.nextId())).path("code").asString());
        assertEquals(404, application("012").getStatus().value());
        assertEquals(400, http.get().uri("/client-onboarding-admin/v1/cases/{caseId}/application", review.caseId())
            .header("X-Gateway-Actor-Subject", Long.toString(OPERATOR_ID)).header("X-Gateway-Session-Id", " ")
            .exchange().expectBody(byte[].class).returnResult().getStatus().value());
    }

    private EntityExchangeResult<byte[]> application(Object caseId) {
        return http.get().uri("/client-onboarding-admin/v1/cases/{caseId}/application", caseId)
            .header("X-Gateway-Actor-Subject", Long.toString(OPERATOR_ID)).header("X-Gateway-Session-Id", "ops-session-1")
            .header("X-Request-Id", "cct-review-test").exchange().expectBody(byte[].class).returnResult();
    }
}
