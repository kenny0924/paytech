package com.paytech.foundation.compensation.auto;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.RegisteredCompensationEvent;
import com.paytech.foundation.compensation.test.base.MockTestBase;
import com.paytech.foundation.compensation.test.support.DatabaseLookupCompensationHandler;

/**
 * 通过共享测试应用验证补偿事件登记。
 *
 * @author Codex
 */
class CompensationEventRegistrationAutoTest extends MockTestBase {

    @Test
    void test_eventKeyIsEnoughForRegistration() {
        createBusinessSource("source-1");

        RegistrationSnapshot snapshot = inTransaction(() -> {
            CompensationEventRequest request =
                new CompensationEventRequest("source-1", DatabaseLookupCompensationHandler.EVENT_TYPE, "TEST_SOURCE");
            RegisteredCompensationEvent first = registrar.register(request);
            RegisteredCompensationEvent duplicate = registrar.register(request);
            int persistedRows = testMapper.countCompensationEventWithoutPayload(first.eventId());
            return new RegistrationSnapshot(first, duplicate, persistedRows);
        });

        assertFalse(snapshot.first().duplicate());
        assertTrue(snapshot.duplicate().duplicate());
        assertEquals(snapshot.first().eventId(), snapshot.duplicate().eventId());
        assertEquals(1, snapshot.persistedRows());
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertEquals(1, testMapper.findProcessedCount("source-1", "TEST_SOURCE"));
            assertEquals(0, testMapper.countCompensationEventById(snapshot.first().eventId()));
        });
    }

    @Test
    void test_eventIdentityIsCaseSensitive() {
        testMapper.insertCompensationEvent(91_001L, "Event-Key-A", "CaseSensitiveEvent", "TEST_SOURCE");
        testMapper.insertCompensationEvent(91_002L, "event-key-a", "casesensitiveevent", "test_source");

        assertEquals(2, testMapper.countCompensationEventsByIds(91_001L, 91_002L));
    }

    private record RegistrationSnapshot(RegisteredCompensationEvent first, RegisteredCompensationEvent duplicate,
        int persistedRows) {
    }
}
