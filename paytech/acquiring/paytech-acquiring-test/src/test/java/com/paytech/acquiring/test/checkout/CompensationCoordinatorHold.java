package com.paytech.acquiring.test.checkout;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;

/**
 * 测试侧暂停补偿任务认领：用阻塞任务占满 Foundation 协调器的核心线程，提交后唤醒的认领任务只能进入队列等待
 * <li>只在测试中使用，不修改生产组件；队列未满不会触发拒绝，释放后排队的认领按原顺序执行。</li>
 * <li>用例结束前必须 release()；Clear 兜底释放，避免公共清理时协调器仍被占用。</li>
 */
@TestComponent
public class CompensationCoordinatorHold implements Clear {
    private final ThreadPoolExecutor coordinator;
    private CountDownLatch latch;

    /** 注入 Foundation 补偿协调器线程池。 */
    public CompensationCoordinatorHold(
        @Qualifier("compensationCoordinatorExecutor") ThreadPoolExecutor coordinator) {
        this.coordinator = coordinator;
    }

    /** 占满全部核心线程，并等待阻塞任务全部开始执行。 */
    public synchronized void hold() {
        if (latch != null) {
            throw new IllegalStateException("协调器已暂停");
        }
        await().atMost(Duration.ofSeconds(10)).until(() -> coordinator.getActiveCount() == 0
            && coordinator.getQueue().isEmpty());
        latch = new CountDownLatch(1);
        CountDownLatch current = latch;
        int threads = coordinator.getCorePoolSize();
        for (int i = 0; i < threads; i++) {
            coordinator.execute(() -> {
                try {
                    current.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        await().atMost(Duration.ofSeconds(10)).until(() -> coordinator.getActiveCount() == threads);
    }

    /** 释放阻塞任务，排队的认领继续执行。 */
    public synchronized void release() {
        if (latch != null) {
            latch.countDown();
            latch = null;
        }
    }

    /** 用例未释放时兜底释放。 */
    @Override
    public void clear() {
        release();
    }
}
