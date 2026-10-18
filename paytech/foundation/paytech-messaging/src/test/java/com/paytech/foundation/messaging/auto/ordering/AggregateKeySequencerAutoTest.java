package com.paytech.foundation.messaging.auto.ordering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.messaging.ordering.AggregateKeySequencer;
import com.paytech.foundation.messaging.ordering.AggregateSerialExecutor;

/**
 * {@link AggregateKeySequencer}：事件键到聚合的解析、同聚合串行、队列满拒绝。
 */
class AggregateKeySequencerAutoTest {

    /** 四段式键按 aggregateType:aggregateId 归并；其他格式以整个键为聚合。 */
    @Test
    void test_normalFlow_aggregateOfEventKey() {
        assertEquals("PAYMENT_ATTEMPT:42", AggregateKeySequencer.aggregateOf("PAYMENT_ATTEMPT:42:PaymentSucceeded:7"));
        assertEquals("PAYMENT_ATTEMPT:42", AggregateKeySequencer.aggregateOf("PAYMENT_ATTEMPT:42:PaymentSucceeded:8"));
        assertEquals("42", AggregateKeySequencer.aggregateOf("42"));
        assertEquals("a:b", AggregateKeySequencer.aggregateOf("a:b"));
    }

    /** 同一聚合的不同版本键落在同一线程并按提交顺序执行。 */
    @Test
    void test_normalFlow_sameAggregateRunsInSubmissionOrder() throws InterruptedException {
        try (AggregateKeySequencer sequencer = new AggregateKeySequencer(new AggregateSerialExecutor(4, 16, "t-"))) {
            List<String> executed = new CopyOnWriteArrayList<>();
            CountDownLatch done = new CountDownLatch(5);
            for (int version = 1; version <= 5; version++) {
                String key = "TEST_FACT:agg-1:TestFactRecorded:" + version;
                sequencer.execute("TEST", key, () -> {
                    executed.add(key);
                    done.countDown();
                });
            }
            assertEquals(true, done.await(5, TimeUnit.SECONDS));
            assertEquals(sequencer.laneOf("TEST_FACT:agg-1:TestFactRecorded:1"),
                sequencer.laneOf("TEST_FACT:agg-1:TestFactRecorded:5"));
            assertEquals(List.of(1, 2, 3, 4, 5).stream().map(v -> "TEST_FACT:agg-1:TestFactRecorded:" + v).toList(),
                executed);
            assertEquals(0, sequencer.pendingTaskCount());
        }
    }

    /** 线程队列已满时拒绝，供 Dispatcher 按可重试失败释放任务。 */
    @Test
    void test_businessRuleViolation_fullLaneRejects() throws InterruptedException {
        try (AggregateKeySequencer sequencer = new AggregateKeySequencer(new AggregateSerialExecutor(1, 1, "t-"))) {
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch started = new CountDownLatch(1);
            sequencer.execute("TEST", "1", () -> {
                started.countDown();
                await(release);
            });
            started.await(5, TimeUnit.SECONDS);
            sequencer.execute("TEST", "2", () -> { });
            assertEquals(2, sequencer.pendingTaskCount());
            assertThrows(RejectedExecutionException.class, () -> sequencer.execute("TEST", "3", () -> { }));
            release.countDown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
