package com.paytech.client.contract.auto.ext.eventhandler.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.client.contract.service.handler.ClientReviewRegistrationHandler;
import com.paytech.client.contract.test.base.onboarding.AbstractSubmissionApiTest;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;

/**
 * 审核登记补偿入口：Risk 拒绝、Risk 不可用与过期提交编号。
 *
 * @see ClientReviewRegistrationHandler#handle(CompensationEvent)
 */
class ClientReviewRegistrationHandlerTest extends AbstractSubmissionApiTest {
    @Autowired
    ClientReviewRegistrationHandler handler;

    /** 本类公共配置：三类资格的通用材料要求。 */
    @BeforeEach
    void setUpClassMocks() {
        mockGenericRequirements();
    }

    /** Risk 明确拒绝：任务终态失败，申请保持 SUBMITTED 等待人工处理。 */
    @Test
    void test_riskRejected() {
        var app = applications.individual(ACCOUNT_ID);
        availableDocument(app, app.partyId(), "IDENTITY", "IDENTITY_DOCUMENT");
        owners.mockReviewRejected(409, "CONCURRENT_REQUEST");

        String submissionId = body(submit(app)).path("submissionId").asString();

        waitUntil(() -> !mapper.reviewEvents(submissionId).isEmpty()
            && "FAILED".equals(mapper.reviewEvents(submissionId).getFirst().get("execute_status")), Duration.ofSeconds(10));
        assertEquals("CLIENT_REVIEW_REJECTED", mapper.reviewEvents(submissionId).getFirst().get("last_error_code"));
        assertEquals("SUBMITTED", mapper.application(app.applicationId()).get("status"));
        assertEquals(1, owners.count("review:"));
    }

    /** Risk 不可用：任务保留待重试，申请保持 SUBMITTED，不生成新提交。 */
    @Test
    void test_riskUnavailable() {
        var app = applications.individual(ACCOUNT_ID);
        availableDocument(app, app.partyId(), "IDENTITY", "IDENTITY_DOCUMENT");
        owners.mockReviewUnavailable();

        String submissionId = body(submit(app)).path("submissionId").asString();

        waitUntil(() -> !mapper.reviewEvents(submissionId).isEmpty()
            && "CLIENT_REVIEW_UNAVAILABLE".equals(mapper.reviewEvents(submissionId).getFirst().get("last_error_code")),
            Duration.ofSeconds(10));
        assertNotEquals("SUCCEEDED", mapper.reviewEvents(submissionId).getFirst().get("execute_status"));
        assertEquals(submissionId, mapper.application(app.applicationId()).get("current_submission_id"));
        assertEquals("SUBMITTED", mapper.application(app.applicationId()).get("status"));
    }

    /** 已不是任何申请当前提交的编号：无事可做，不调用 Risk。 */
    @Test
    void test_staleSubmissionIsNoop() {
        var result = handler.handle(new CompensationEvent(1L, UUID.randomUUID().toString(),
            ClientReviewRegistrationHandler.EVENT_TYPE, "-", null, 1));

        assertEquals(CompensationHandlerResult.success(), result);
        assertEquals(0, owners.count("review:"));
    }
}
