package com.paytech.risk.test.checker.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.risk.compliance.domain.ClientReviewAction;
import com.paytech.risk.test.mocker.compliance.ClientReviewMocker.MockedReview;

import tools.jackson.databind.ObjectMapper;

/** 客户可见审核结果响应：字段集合固定，只含结果、退回分区与公开说明。 */
public final class ClientReviewResultResponseChecker extends AbstractHttpResponseChecker<ClientReviewResultResponseChecker> {
    /** 响应字段，不得出现内部理由或决定人。 */
    static final Set<String> FIELDS = Set.of("applicationId", "caseId", "status", "outcome", "returnSection", "publicNote", "decidedAt");

    public ClientReviewResultResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    @Override
    protected ClientReviewResultResponseChecker self() {
        return this;
    }

    /** 进行中的任务：只有状态，没有结果。 */
    public ClientReviewResultResponseChecker assertInProgress(MockedReview review) {
        assertCoordinates(review);
        assertEquals(review.main().status(), body().path("status").asString());
        for (String field : List.of("outcome", "returnSection", "publicNote", "decidedAt")) {
            assertTrue(body().path(field).isNull(), field);
        }
        return this;
    }

    /** 已结束的任务：结果等于主任务状态，退回分区与公开说明来自结论。 */
    public ClientReviewResultResponseChecker assertClosed(MockedReview review, ClientReviewAction action) {
        assertCoordinates(review);
        assertEquals(action.getCaseStatus(), body().path("status").asString());
        assertEquals(action.getCaseStatus(), body().path("outcome").asString());
        assertNullable(review.spec().returnSection(), "returnSection");
        assertNullable(review.spec().publicNote(), "publicNote");
        assertEquals("2026-10-13T10:00:00.123456Z", body().path("decidedAt").asString());
        return this;
    }

    /** 申请与主任务坐标，字段集合准确。 */
    private void assertCoordinates(MockedReview review) {
        assertStatus(200);
        assertEquals(FIELDS, Set.copyOf(body().propertyNames()));
        assertEquals(Long.toString(review.applicationId()), body().path("applicationId").asString());
        assertEquals(Long.toString(review.caseId()), body().path("caseId").asString());
    }

    private void assertNullable(String expected, String field) {
        if (expected == null) {
            assertTrue(body().path(field).isNull(), field);
            return;
        }
        assertEquals(expected, body().path(field).asString());
    }
}
