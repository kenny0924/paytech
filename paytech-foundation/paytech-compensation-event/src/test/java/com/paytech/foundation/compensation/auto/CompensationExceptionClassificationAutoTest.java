package com.paytech.foundation.compensation.auto;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.RegisteredCompensationEvent;
import com.paytech.foundation.compensation.model.CompensationEventStatus;
import com.paytech.foundation.compensation.test.base.MockTestBase;
import com.paytech.foundation.compensation.test.support.ExceptionClassificationCompensationHandler;

class CompensationExceptionClassificationAutoTest extends MockTestBase {

    @Test
    void test_failExceptionDoesNotRetry() {
        RegisteredCompensationEvent event = registerScenario("fail-source", "FAIL");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertEquals(CompensationEventStatus.FAILED.getCode(), status(event));
            assertEquals(1, testMapper.findCompensationEventExecuteCount(event.eventId()));
            assertEquals(CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(), errorCode(event));
        });
    }

    @Test
    void test_errorExceptionCanRetry() {
        RegisteredCompensationEvent event = registerScenario("error-source", "ERROR");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertEquals(CompensationEventStatus.ERROR.getCode(), status(event));
            assertEquals(1, testMapper.findCompensationEventExecuteCount(event.eventId()));
            assertEquals(CommonErrorCode.INTERNAL_ERROR.getCode(), errorCode(event));
        });
    }

    @Test
    void test_wrappedFailExceptionDoesNotRetry() {
        RegisteredCompensationEvent event = registerScenario("wrapped-fail-source", "WRAPPED_FAIL");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertEquals(CompensationEventStatus.FAILED.getCode(), status(event));
            assertEquals(1, testMapper.findCompensationEventExecuteCount(event.eventId()));
            assertEquals(CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(), errorCode(event));
        });
    }

    @Test
    void test_unclassifiedExceptionCanRetry() {
        RegisteredCompensationEvent event = registerScenario("unknown-source", "UNKNOWN");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertEquals(CompensationEventStatus.ERROR.getCode(), status(event));
            assertEquals(1, testMapper.findCompensationEventExecuteCount(event.eventId()));
            assertEquals("UNEXPECTED_HANDLER_EXCEPTION", errorCode(event));
        });
    }

    private RegisteredCompensationEvent registerScenario(String eventKey, String scenario) {
        createBusinessSource(eventKey, scenario);
        return inTransaction(() -> registrar.register(new CompensationEventRequest(eventKey,
            ExceptionClassificationCompensationHandler.EVENT_TYPE, "TEST_SOURCE")));
    }

    private String status(RegisteredCompensationEvent event) {
        return testMapper.findCompensationEventStatus(event.eventId());
    }

    private String errorCode(RegisteredCompensationEvent event) {
        return testMapper.findCompensationEventErrorCode(event.eventId());
    }
}
