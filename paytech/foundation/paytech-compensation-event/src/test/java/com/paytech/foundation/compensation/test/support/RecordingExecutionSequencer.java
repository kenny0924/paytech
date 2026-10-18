package com.paytech.foundation.compensation.test.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import com.paytech.foundation.compensation.api.CompensationExecutionSequencer;

/**
 * 测试用执行编排器：在一条独立线程上按提交顺序执行，并记录每次编排的身份与执行线程；可切换为拒绝模式。
 * <li>只实现端口本身，不替换 Dispatcher 的 Claim、Handler 调度与状态迁移。</li>
 *
 * @author Claude
 */
public final class RecordingExecutionSequencer implements CompensationExecutionSequencer, AutoCloseable {

    /** 执行线程名。 */
    public static final String THREAD_NAME = "test-sequencer-lane";

    private final ExecutorService lane = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, THREAD_NAME);
        thread.setDaemon(true);
        return thread;
    });
    private final List<Sequenced> sequenced = new CopyOnWriteArrayList<>();
    private volatile boolean rejecting;

    @Override
    public void execute(String eventType, String eventKey, Runnable task) {
        if (rejecting) {
            throw new RejectedExecutionException("test sequencer is rejecting");
        }
        lane.execute(() -> {
            sequenced.add(new Sequenced(eventType, eventKey, Thread.currentThread().getName()));
            task.run();
        });
    }

    /**
     * 切换拒绝模式。
     *
     * @param rejecting true 时所有编排抛出 {@link RejectedExecutionException}
     */
    public void rejecting(boolean rejecting) {
        this.rejecting = rejecting;
    }

    /**
     * 已在编排线程上开始执行的任务。
     *
     * @return 按执行顺序
     */
    public List<Sequenced> sequenced() {
        return List.copyOf(sequenced);
    }

    /** 恢复接收并清空记录。 */
    public void reset() {
        rejecting = false;
        sequenced.clear();
    }

    @Override
    public void close() {
        lane.shutdownNow();
    }

    /**
     * 一次编排记录。
     *
     * @param eventType 事件类型
     * @param eventKey 业务键
     * @param threadName 执行线程
     */
    public record Sequenced(String eventType, String eventKey, String threadName) {
    }
}
