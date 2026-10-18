package com.paytech.foundation.messaging.auto.sequenced;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestComponent;

/**
 * 观察测试发布 Handler：开始执行的顺序；可让指定事件键放慢。
 */
@TestComponent
public final class SequencedPublishProbe {

    private final List<String> started = new CopyOnWriteArrayList<>();
    private final Map<String, Duration> delays = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    void enter(String eventKey) {
        inFlight.add(eventKey);
        started.add(eventKey);
    }

    void exit(String eventKey) {
        inFlight.remove(eventKey);
    }

    Duration delayOf(String eventKey) {
        return delays.getOrDefault(eventKey, Duration.ZERO);
    }

    /**
     * 让该事件键的 Handler 在发布前等待。
     *
     * @param eventKey 事件键
     * @param delay 等待
     */
    public void slowDown(String eventKey, Duration delay) {
        delays.put(eventKey, delay);
    }

    /**
     * 已开始执行的事件键。
     *
     * @return 按开始顺序
     */
    public List<String> started() {
        return List.copyOf(started);
    }

    /**
     * 正在执行的 Handler 数。
     *
     * @return 数量
     */
    public int inFlight() {
        return inFlight.size();
    }

    /** 清空。 */
    public void clear() {
        started.clear();
        delays.clear();
    }
}
