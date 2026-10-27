package com.paytech.client.master.auto.ext.eventhandler.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.client.master.service.handler.ClientClassificationHandler;
import com.paytech.client.master.test.base.onboarding.AbstractClientReviewTest;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;

/**
 * I02 分类交付补偿入口：Risk 拒绝、Risk 任务已结束、Risk 不可用与过期任务。
 *
 * @see ClientClassificationHandler#handle(CompensationEvent)
 */
class ClientClassificationHandlerTest extends AbstractClientReviewTest {
    @Autowired
    ClientClassificationHandler handler;

    /** Risk 明确拒绝（集合不一致）：任务终态失败，保留供人工处理。 */
    @Test
    void test_riskRejected() {
        var review = confirmedReview();
        owners.mockClassificationsRejected(409, "CLIENT_REVIEW_CLASSIFICATION_CONFLICT");

        confirm(review);

        waitUntil(() -> "FAILED".equals(status(review)), Duration.ofSeconds(10));
        assertEquals("CLIENT_CLASSIFICATION_REJECTED",
            mapper.events(ClientClassificationHandler.EVENT_TYPE, Long.toString(review)).getFirst().get("last_error_code"));
    }

    /** Risk 任务已结束：视为完成，补偿记录删除。 */
    @Test
    void test_riskCaseClosed() {
        var review = confirmedReview();
        owners.mockClassificationsRejected(409, "CLIENT_REVIEW_CLOSED");

        confirm(review);

        waitUntil(() -> owners.count("classifications:") == 1
            && mapper.events(ClientClassificationHandler.EVENT_TYPE, Long.toString(review)).isEmpty(), Duration.ofSeconds(10));
    }

    /** Risk 不可用：保留待重试，不视为成功。 */
    @Test
    void test_riskUnavailable() {
        var review = confirmedReview();
        owners.mockClassificationsRejected(503, "SERVICE_UNAVAILABLE");

        confirm(review);

        waitUntil(() -> "CLIENT_CLASSIFICATION_UNAVAILABLE".equals(errorCode(review)), Duration.ofSeconds(10));
        assertNotEquals("FAILED", status(review));
    }

    /** 已不是任何申请当前任务：无事可做，不调用 Risk。 */
    @Test
    void test_staleCaseIsNoop() {
        var result = handler.handle(new CompensationEvent(1L, Long.toString(applications.nextId()), ClientClassificationHandler.EVENT_TYPE,
            "-", null, 1));

        assertEquals(CompensationHandlerResult.success(), result);
        assertEquals(0, owners.count("classifications:"));
    }

    /** 已设置分类的审核中任务。 */
    long confirmedReview() {
        var review = applications.underReview(ACCOUNT_ID);
        assertEquals(200, replace(review.caseId(), List.of(item("RETAIL_GROCERY", "5411", true))).getStatus().value());
        return review.caseId();
    }

    /** 补偿任务状态。 */
    String status(long caseId) {
        var events = mapper.events(ClientClassificationHandler.EVENT_TYPE, Long.toString(caseId));
        return events.isEmpty() ? null : (String)events.getFirst().get("execute_status");
    }

    /** 补偿任务最近错误。 */
    String errorCode(long caseId) {
        var events = mapper.events(ClientClassificationHandler.EVENT_TYPE, Long.toString(caseId));
        return events.isEmpty() ? null : (String)events.getFirst().get("last_error_code");
    }
}
