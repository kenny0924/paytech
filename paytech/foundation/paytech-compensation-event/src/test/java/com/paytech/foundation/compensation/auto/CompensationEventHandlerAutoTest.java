package com.paytech.foundation.compensation.auto;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationEventScanner;
import com.paytech.foundation.compensation.api.CompensationScanRequest;
import com.paytech.foundation.compensation.test.base.MockTestBase;
import com.paytech.foundation.compensation.test.support.DatabaseLookupCompensationHandler;

/**
 * 验证处理器从数据库重新加载业务数据，而不是依赖事件负载。
 *
 * @author Codex
 */
class CompensationEventHandlerAutoTest extends MockTestBase {

    @Autowired
    CompensationEventScanner scanner;

    @Test
    void test_handlerLoadsBusinessDataFromDatabase() {
        createBusinessSource("source-2");

        inTransaction(() -> registrar.register(
            new CompensationEventRequest("source-2", DatabaseLookupCompensationHandler.EVENT_TYPE, "TEST_SOURCE")));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertEquals(1, processedCount("source-2"));
            assertEquals(0, compensationEventCount());
            assertEquals("source-2", handlerProbe.events().getFirst().eventKey());
            assertNull(handlerProbe.events().getFirst().payloadJson());
        });
    }

    @Test
    void test_manuallyInsertedSourceReferenceCanBeRetried() {
        createBusinessSource("manual-source");
        testMapper.insertCompensationEvent(90_001L, "manual-source", DatabaseLookupCompensationHandler.EVENT_TYPE,
            "TEST_SOURCE");

        scanner.scan(new CompensationScanRequest(100, 0, 1, Set.of()));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertEquals(1, processedCount("manual-source"));
            assertEquals(0, compensationEventCount());
        });
    }

    private int processedCount(String eventKey) {
        return testMapper.findProcessedCount(eventKey, "TEST_SOURCE");
    }

    private int compensationEventCount() {
        return testMapper.countCompensationEvents();
    }
}
