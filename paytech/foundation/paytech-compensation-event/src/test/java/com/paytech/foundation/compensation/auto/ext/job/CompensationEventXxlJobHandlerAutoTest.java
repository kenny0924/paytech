package com.paytech.foundation.compensation.auto.ext.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.paytech.foundation.compensation.api.CompensationEventScanner;
import com.paytech.foundation.compensation.api.CompensationScanRequest;
import com.paytech.foundation.compensation.api.CompensationScanResult;
import com.paytech.foundation.compensation.ext.job.CompensationEventXxlJobHandler;
import com.xxl.job.core.context.XxlJobContext;

class CompensationEventXxlJobHandlerAutoTest {

    @AfterEach
    void clearJobContext() {
        XxlJobContext.setXxlJobContext(null);
    }

    @Test
    void mapsShardAndEventTypeWhitelistToScanner() {
        RecordingScanner scanner = new RecordingScanner(new CompensationScanResult(2, 2, 0));
        CompensationEventXxlJobHandler handler = new CompensationEventXxlJobHandler(scanner, 100);
        XxlJobContext context = context("PaymentSucceeded, RefundSucceeded", 1, 3);

        handler.scan();

        CompensationScanRequest request = scanner.request;
        assertEquals(100, request.batchSize());
        assertEquals(1, request.shardIndex());
        assertEquals(3, request.shardCount());
        assertEquals(2, request.eventTypes().size());
        assertTrue(request.eventTypes().contains("PaymentSucceeded"));
        assertTrue(request.eventTypes().contains("RefundSucceeded"));
        assertEquals(XxlJobContext.HANDLE_CODE_SUCCESS, context.getHandleCode());
    }

    @Test
    void reportsBackpressureAsFailedJobWithoutLosingCounts() {
        RecordingScanner scanner = new RecordingScanner(new CompensationScanResult(20, 18, 2));
        CompensationEventXxlJobHandler handler = new CompensationEventXxlJobHandler(scanner, 100);
        XxlJobContext context = context("", 0, 1);

        handler.scan();

        assertEquals(XxlJobContext.HANDLE_CODE_FAIL, context.getHandleCode());
        assertTrue(context.getHandleMsg().contains("rejected=2"));
    }

    @Test
    void neverReturnsExceptionMessageToAdmin() {
        CompensationEventScanner scanner = request -> {
            throw new IllegalStateException("password=must-not-leak");
        };
        CompensationEventXxlJobHandler handler = new CompensationEventXxlJobHandler(scanner, 100);
        XxlJobContext context = context("", 0, 1);

        handler.scan();

        assertEquals(XxlJobContext.HANDLE_CODE_FAIL, context.getHandleCode());
        assertTrue(context.getHandleMsg().contains("IllegalStateException"));
        assertFalse(context.getHandleMsg().contains("must-not-leak"));
    }

    @Test
    void rejectsMissingShardContextWithoutCallingScanner() {
        RecordingScanner scanner = new RecordingScanner(new CompensationScanResult(0, 0, 0));
        CompensationEventXxlJobHandler handler = new CompensationEventXxlJobHandler(scanner, 100);
        XxlJobContext context = context("", -1, -1);

        handler.scan();

        assertEquals(XxlJobContext.HANDLE_CODE_FAIL, context.getHandleCode());
        assertEquals("invalid XXL-Job shard context", context.getHandleMsg());
        assertNull(scanner.request);
    }

    private XxlJobContext context(String parameter, int shardIndex, int shardCount) {
        XxlJobContext context = new XxlJobContext(1L, parameter, 2L, System.currentTimeMillis(), null, shardIndex,
            shardCount);
        XxlJobContext.setXxlJobContext(context);
        return context;
    }

    private static final class RecordingScanner implements CompensationEventScanner {

        private final CompensationScanResult result;
        private CompensationScanRequest request;

        private RecordingScanner(CompensationScanResult result) {
            this.result = result;
        }

        @Override
        public CompensationScanResult scan(CompensationScanRequest request) {
            this.request = request;
            return result;
        }
    }
}
