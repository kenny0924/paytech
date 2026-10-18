package com.paytech.foundation.messaging.auto.ordering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.paytech.foundation.messaging.ordering.AggregateSerialExecutor;

/**
 * 按聚合串行的执行器：同键同线程有序、异键并行、队列有界与关闭。
 *
 * @see AggregateSerialExecutor#execute(String, Runnable)
 */
class AggregateSerialExecutorAutoTest {

    @Test
    void test_normalFlow_sameAggregateRunsInSubmissionOrderOnOneThread() throws InterruptedException {
        try (AggregateSerialExecutor executor = new AggregateSerialExecutor(4, 100, "serial-test-")) {
            List<Integer> order = new CopyOnWriteArrayList<>();
            Set<String> threads = ConcurrentHashMap.newKeySet();
            CountDownLatch done = new CountDownLatch(50);

            for (int index = 0; index < 50; index++) {
                int value = index;
                executor.execute("BOOK-1", () -> {
                    order.add(value);
                    threads.add(Thread.currentThread().getName());
                    done.countDown();
                });
            }

            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals(IntStream.range(0, 50).boxed().toList(), order);
            assertEquals(1, threads.size());
        }
    }

    @Test
    void test_normalFlow_differentAggregatesRunInParallel() throws InterruptedException {
        try (AggregateSerialExecutor executor = new AggregateSerialExecutor(8, 10, "serial-test-")) {
            String first = "BOOK-1";
            String second = IntStream.range(2, 100).mapToObj(index -> "BOOK-" + index)
                .filter(candidate -> executor.laneOf(candidate) != executor.laneOf(first)).findFirst().orElseThrow();
            CountDownLatch bothRunning = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);

            executor.execute(first, () -> awaitTogether(bothRunning, release));
            executor.execute(second, () -> awaitTogether(bothRunning, release));

            assertTrue(bothRunning.await(5, TimeUnit.SECONDS), "两个聚合未能同时执行");
            release.countDown();
            assertNotEquals(executor.laneOf(first), executor.laneOf(second));
        }
    }

    @Test
    void test_edgeCase_boundedQueueRejects() throws InterruptedException {
        try (AggregateSerialExecutor executor = new AggregateSerialExecutor(1, 1, "serial-test-")) {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            executor.execute("BOOK-1", () -> {
                started.countDown();
                await(release);
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            executor.execute("BOOK-1", () -> { });

            assertThrows(RejectedExecutionException.class, () -> executor.execute("BOOK-1", () -> { }));
            release.countDown();
        }
    }

    @Test
    void test_edgeCase_shutdownDrainsQueuedTasksThenRejects() throws InterruptedException {
        AggregateSerialExecutor executor = new AggregateSerialExecutor(2, 10, "serial-test-");
        CountDownLatch done = new CountDownLatch(3);
        for (int index = 0; index < 3; index++) {
            executor.execute("BOOK-" + index, done::countDown);
        }

        assertTrue(executor.shutdownAndAwait(5_000));
        assertEquals(0, done.getCount());
        assertThrows(RejectedExecutionException.class, () -> executor.execute("BOOK-1", () -> { }));
    }

    @Test
    void test_invalidParameter() {
        assertThrows(IllegalArgumentException.class, () -> new AggregateSerialExecutor(0, 1, "x"));
        assertThrows(IllegalArgumentException.class, () -> new AggregateSerialExecutor(257, 1, "x"));
        assertThrows(IllegalArgumentException.class, () -> new AggregateSerialExecutor(1, 0, "x"));
    }

    private static void awaitTogether(CountDownLatch bothRunning, CountDownLatch release) {
        bothRunning.countDown();
        await(release);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
