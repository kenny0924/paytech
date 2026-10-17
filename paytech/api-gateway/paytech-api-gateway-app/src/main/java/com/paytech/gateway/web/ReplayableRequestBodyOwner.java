package com.paytech.gateway.web;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

/** 持有一份可重放的请求体，并在请求终止时尽力清零。 */
final class ReplayableRequestBodyOwner {
    private final byte[] body;
    private final AtomicBoolean cleanupClaimed = new AtomicBoolean();
    private final AtomicInteger clearCount = new AtomicInteger();
    private volatile SignalType terminalSignal;
    private volatile boolean zeroed;

    ReplayableRequestBodyOwner(byte[] body) {
        this.body = Objects.requireNonNull(body, "可重放请求体不可为空");
    }

    Flux<DataBuffer> replay(DataBufferFactory bufferFactory) {
        Objects.requireNonNull(bufferFactory, "DataBufferFactory 不可为空");
        return Flux.defer(() -> Flux.just(bufferFactory.wrap(body))).doOnDiscard(DataBuffer.class,
            DataBufferUtils::release);
    }

    void clear(SignalType signalType) {
        if (!cleanupClaimed.compareAndSet(false, true)) {
            return;
        }
        terminalSignal = signalType;
        boolean cleared = false;
        try {
            Arrays.fill(body, (byte)0);
            cleared = true;
        } catch (Throwable ignored) {
            // 清理不得改变请求终止信号。
        } finally {
            zeroed = cleared;
            clearCount.incrementAndGet();
        }
    }

    int length() {
        return body.length;
    }

    SignalType terminalSignal() {
        return terminalSignal;
    }

    boolean isZeroed() {
        return zeroed;
    }

    int clearCount() {
        return clearCount.get();
    }
}
