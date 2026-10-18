package com.paytech.foundation.compensation.auto;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.compensation.api.CompensationEventRecoveryService;
import com.paytech.foundation.compensation.api.CompensationEventReopenRequest;
import com.paytech.foundation.compensation.api.ReopenedCompensationEvent;
import com.paytech.foundation.compensation.test.base.MockTestBase;
import com.paytech.foundation.compensation.test.support.DatabaseLookupCompensationHandler;

/**
 * 通过公共本地测试应用验证人工恢复。
 *
 * @author Codex
 */
class CompensationEventRecoveryAutoTest extends MockTestBase {

    @Autowired
    CompensationEventRecoveryService recoveryService;

    @Test
    void test_failedEventCanBeAuditedAndReopened() {
        createBusinessSource("recovery-source");
        testMapper.insertFailedCompensationEvent(90_002L, "recovery-source",
            DatabaseLookupCompensationHandler.EVENT_TYPE, "TEST_SOURCE", 3, "TEST_FAILURE", "test failure", 7L);

        ReopenedCompensationEvent reopened = recoveryService
            .reopen(new CompensationEventReopenRequest(90_002L, 7L, "operator-1", "approved retry", "INC-100"));

        assertEquals(90_002L, reopened.eventId());
        assertEquals(8L, reopened.lockVersion());
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertEquals(1, testMapper.countRecoveryAudits(90_002L, "recovery-source"));
            assertEquals(0, testMapper.countCompensationEventById(90_002L));
        });
    }
}
