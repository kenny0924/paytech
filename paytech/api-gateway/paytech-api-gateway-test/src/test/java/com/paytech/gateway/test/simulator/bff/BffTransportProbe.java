package com.paytech.gateway.test.simulator.bff;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** 只记录 BFF 传输启动和请求体信号数量，不保留请求内容。 */
public final class BffTransportProbe {
    private final AtomicInteger requestStarts = new AtomicInteger();
    private final AtomicInteger bodySignals = new AtomicInteger();
    private final AtomicLong bodyBytes = new AtomicLong();

    public int requestStartCount() {
        return requestStarts.get();
    }

    public int bodySignalCount() {
        return bodySignals.get();
    }

    public long bodyByteCount() {
        return bodyBytes.get();
    }

    void recordRequestStart() {
        requestStarts.incrementAndGet();
    }

    void recordBodySignal(int readableBytes) {
        bodySignals.incrementAndGet();
        bodyBytes.addAndGet(readableBytes);
    }

    void reset() {
        requestStarts.set(0);
        bodySignals.set(0);
        bodyBytes.set(0L);
    }
}
