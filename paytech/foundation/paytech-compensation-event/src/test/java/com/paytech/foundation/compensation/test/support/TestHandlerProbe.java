package com.paytech.foundation.compensation.test.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.paytech.foundation.compensation.api.CompensationEvent;

/**
 * 用于观察真实测试处理器的线程安全探针。
 *
 * @author Codex
 */
public final class TestHandlerProbe {

    private final CopyOnWriteArrayList<CompensationEvent> events = new CopyOnWriteArrayList<>();

    public void record(CompensationEvent event) {
        events.add(event);
    }

    public List<CompensationEvent> events() {
        return List.copyOf(events);
    }

    public void reset() {
        events.clear();
    }
}
