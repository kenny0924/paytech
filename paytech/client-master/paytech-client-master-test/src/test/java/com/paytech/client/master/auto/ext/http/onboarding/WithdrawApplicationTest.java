package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.client.master.service.handler.ClientReviewWithdrawalHandler;
import com.paytech.client.master.test.base.onboarding.AbstractClientReviewTest;

/**
 * Client 撤回申请（CM-8）：DRAFT / RETURNED 可撤回并释放名额；已立案的退回申请由补偿任务调用 compliance I03 撤回案件，
 * 案件已结束视为完成；审核中不可撤回。
 *
 * @see com.paytech.client.master.api.onboarding.ClientOnboardingApplicationApi#withdraw(String, String, String, String)
 */
class WithdrawApplicationTest extends AbstractClientReviewTest {

    /** 未立案的草稿：撤回成功、释放名额、不调用 compliance；重复撤回返回同一结果。 */
    @Test
    void test_draftWithdrawn() {
        var app = applications.individual(ACCOUNT_ID);

        var result = withdraw(app.applicationId());

        assertEquals(200, result.getStatus().value(), new String(result.getResponseBody()));
        assertEquals("WITHDRAWN", body(result).path("status").asString());
        var row = mapper.application(app.applicationId());
        assertEquals("WITHDRAWN", row.get("status"));
        assertNull(row.get("active_slot"));
        var again = withdraw(app.applicationId());
        assertEquals(200, again.getStatus().value());
        assertEquals(body(result).path("withdrawnAt").asString(), body(again).path("withdrawnAt").asString());
        assertEquals(0, owners.count("withdraw:"));
    }

    /** 已退回的立案申请：撤回后补偿任务以 I03 通知 compliance，载荷只有申请 ID 与撤回人。 */
    @Test
    void test_returnedWithdrawnNotifiesCompliance() {
        var review = applications.underReview(ACCOUNT_ID);
        publishResult(review, review.caseId(), "RETURNED", null, null);
        waitUntil(() -> "RETURNED".equals(mapper.review(review.app().applicationId()).get("status")), Duration.ofSeconds(10));
        owners.mockWithdrawalAccepted();

        var result = withdraw(review.app().applicationId());

        assertEquals(200, result.getStatus().value(), new String(result.getResponseBody()));
        String key = Long.toString(review.app().applicationId());
        waitUntil(() -> owners.count("withdraw:") == 1 && mapper.events(ClientReviewWithdrawalHandler.EVENT_TYPE, key).isEmpty(),
            Duration.ofSeconds(10));
        assertEquals(1, owners.count("withdraw:" + review.caseId()));
        var request = owners.withdrawalRequests().getFirst();
        assertEquals(key, request.getApplicationId());
        assertEquals(Long.toString(ACCOUNT_ID), request.getRequestedByAccountId());
        assertEquals("WITHDRAWN", mapper.application(review.app().applicationId()).get("status"));
    }

    /** compliance 已结束案件（CLIENT_REVIEW_CLOSED）：撤回任务视为完成，不重试。 */
    @Test
    void test_caseAlreadyClosed() {
        var review = applications.underReview(ACCOUNT_ID);
        publishResult(review, review.caseId(), "RETURNED", null, null);
        waitUntil(() -> "RETURNED".equals(mapper.review(review.app().applicationId()).get("status")), Duration.ofSeconds(10));
        owners.mockWithdrawalRejected(409, "CLIENT_REVIEW_CLOSED");

        assertEquals(200, withdraw(review.app().applicationId()).getStatus().value());

        String key = Long.toString(review.app().applicationId());
        waitUntil(() -> owners.count("withdraw:") == 1 && mapper.events(ClientReviewWithdrawalHandler.EVENT_TYPE, key).isEmpty(),
            Duration.ofSeconds(10));
    }

    /** 审核中的申请不可撤回：409 APPLICATION_NOT_WITHDRAWABLE，状态不变。 */
    @Test
    void test_underReviewNotWithdrawable() {
        var review = applications.underReview(ACCOUNT_ID);

        var result = withdraw(review.app().applicationId());

        assertEquals(409, result.getStatus().value());
        assertEquals("APPLICATION_NOT_WITHDRAWABLE", body(result).path("code").asString());
        assertEquals("UNDER_REVIEW", mapper.application(review.app().applicationId()).get("status"));
        assertEquals(0, owners.count("withdraw:"));
    }

    /** 通过真实 HTTP 撤回。 */
    EntityExchangeResult<byte[]> withdraw(long applicationId) {
        return http.post().uri("/client-onboarding/v1/applications/{id}/withdrawal", applicationId).headers(headers -> {
            headers.set("X-Caller-Service", "client-console-bff");
            headers.set("X-Gateway-Actor-Subject", Long.toString(ACCOUNT_ID));
            headers.set("X-Gateway-Session-Id", Long.toString(SESSION_ID));
            headers.set("X-Request-Id", "cct-withdraw-test");
        }).exchange().expectBody(byte[].class).returnResult();
    }
}
