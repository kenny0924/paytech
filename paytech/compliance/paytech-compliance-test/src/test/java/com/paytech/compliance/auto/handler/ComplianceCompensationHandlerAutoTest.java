package com.paytech.compliance.auto.handler;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import com.paytech.compliance.service.handler.CaseScreeningHandler;
import com.paytech.compliance.service.handler.ClientReviewResultHandler;
import com.paytech.compliance.service.handler.QualificationChangedHandler;
import com.paytech.compliance.test.base.ClientReviewFlowTestBase;
import com.paytech.compliance.test.builder.ClientReviewRegistrationRequestBuilder;
import com.paytech.compliance.test.mocker.external.ScreeningMocker;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;

/**
 * 补偿 Handler 独立入口（UNIT_TEST_GUIDE 5.1）：CASE_SCREENING、CLIENT_REVIEW_RESULT、QUALIFICATION_CHANGED
 * <li>畸形事件与缺失业务数据为终态失败，不调用外部；筛查在主案件已结束时直接成功；名称读取失败可重试且不调用渠道；
 * 主体没有名称为终态失败（转人工检查兜底）。</li>
 */
class ComplianceCompensationHandlerAutoTest extends ClientReviewFlowTestBase {
    @Autowired
    private CaseScreeningHandler screeningHandler;

    @Autowired
    private ClientReviewResultHandler resultHandler;

    @Autowired
    private QualificationChangedHandler qualificationHandler;

    /** 三国程序。 */
    @BeforeEach
    void setUpHandlers() {
        programs.mockCountryPacks();
    }

    /** 畸形或指向不存在数据的事件：三个 Handler 都终态失败。 */
    @Test
    void test_invalidParameter_malformed() {
        assertAll(() -> assertTerminal(screeningHandler.handle(event("abc", "CASE_SCREENING", "-"))),
            () -> assertTerminal(screeningHandler.handle(event("123456789", "CASE_SCREENING", "-"))),
            () -> assertTerminal(resultHandler.handle(event("123456789", "CLIENT_REVIEW_RESULT", "-"))),
            () -> assertTerminal(qualificationHandler.handle(event("123456789", "QUALIFICATION_CHANGED", "x"))),
            () -> assertTerminal(qualificationHandler.handle(event("123456789", "QUALIFICATION_CHANGED", "1"))));
        assertTrue(screening.calls().isEmpty());
    }

    /** 主案件未结束时结果 Handler 终态失败（只发布已结束案件）；名称读取失败可重试且不调用渠道；主体无名称终态失败。 */
    @Test
    void test_businessRuleViolation_screeningDependencies() {
        var builder = ClientReviewRegistrationRequestBuilder.individual("SA");
        partyProfiles.mockFault(ScreeningMocker.problem(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE"));
        long caseId = register(builder);

        CompensationHandlerResult unavailable = screeningHandler.handle(event(Long.toString(caseId), "CASE_SCREENING", "-"));
        partyProfiles.clear();
        partyProfiles.mockOrganization(999L, "Other");
        CompensationHandlerResult missing = screeningHandler.handle(event(Long.toString(caseId), "CASE_SCREENING", "-"));

        assertAll(() -> assertEquals(CompensationHandlerResult.Outcome.RETRYABLE_FAILURE, unavailable.outcome()),
            () -> assertTerminal(missing), () -> assertTrue(screening.calls().isEmpty()),
            () -> assertTerminal(resultHandler.handle(event(Long.toString(caseId), "CLIENT_REVIEW_RESULT", "-"))));
    }

    /** 主案件已撤回：筛查任务直接成功，不读名称、不调用渠道。 */
    @Test
    void test_edgeCase_screeningClosedCase() {
        var builder = ClientReviewRegistrationRequestBuilder.individual("SA");
        mockSubjects(builder, "Closed Case");
        screening.mockNoHitsByDefault();
        long caseId = register(builder);
        withdraw(caseId, builder.body().getApplicationId()).ok();
        int profileCalls = partyProfiles.calls().size();
        int screeningCalls = screening.calls().size();

        CompensationHandlerResult result = screeningHandler.handle(event(Long.toString(caseId), "CASE_SCREENING", "R1"));

        assertAll(() -> assertEquals(CompensationHandlerResult.Outcome.SUCCESS, result.outcome()),
            () -> assertEquals(profileCalls, partyProfiles.calls().size()), () -> assertEquals(screeningCalls, screening.calls().size()),
            () -> assertTrue(reviews.runs(caseId).stream().noneMatch(run -> "R1".equals(run.get("trigger_ref")))));
    }

    /** 同一触发已成功的模板不再执行（重入幂等）。 */
    @Test
    void test_edgeCase_screeningReentrant() {
        var builder = ClientReviewRegistrationRequestBuilder.individual("SA");
        mockSubjects(builder, "Reentrant");
        screening.mockNoHitsByDefault();
        long caseId = register(builder);
        reviews.awaitScreeningCompleted(caseId);
        int calls = screening.calls().size();

        CompensationHandlerResult result = screeningHandler.handle(event(Long.toString(caseId), "CASE_SCREENING", "-"));

        assertAll(() -> assertEquals(CompensationHandlerResult.Outcome.SUCCESS, result.outcome()),
            () -> assertEquals(calls, screening.calls().size()));
    }

    /** 终态失败断言。 */
    static void assertTerminal(CompensationHandlerResult result) {
        assertEquals(CompensationHandlerResult.Outcome.TERMINAL_FAILURE, result.outcome(), String.valueOf(result));
    }

    /** 补偿事件。 */
    static CompensationEvent event(String key, String type, String subType) {
        return new CompensationEvent(1L, key, type, subType, null, 0);
    }
}
