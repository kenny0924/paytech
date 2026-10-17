package com.paytech.gateway.idempotency;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.gateway.idempotency.IdempotencyRecordStore.StoredResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClientResponse;

/**
 * 包装首次请求的响应：下游确实返回时缓冲不超过上限的响应体，先记录再写出；
 * 下游未返回（Gateway 自身错误）或响应体超限时原样写出并释放占位。
 * 位于 Owner Problem 校验装饰器外层，只看到已通过校验的 Owner 响应。
 */
final class IdempotencyResponseCapture extends ServerHttpResponseDecorator {
    private static final Set<String> RECORDED_HEADERS = Set.of("content-type", "content-language",
        "content-disposition", "content-encoding", "etag", "last-modified", "retry-after");

    private final ServerWebExchange exchange;
    private final int maxResponseBytes;
    private final Settlement settlement;

    IdempotencyResponseCapture(ServerWebExchange exchange, int maxResponseBytes, Settlement settlement) {
        super(exchange.getResponse());
        this.exchange = exchange;
        this.maxResponseBytes = maxResponseBytes;
        this.settlement = settlement;
    }

    @Override
    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
        if (!downstreamResponded()) {
            return settlement.release(ReleaseReason.NO_DOWNSTREAM_RESPONSE).then(super.writeWith(body));
        }
        return super.writeWith(capture(Flux.from(body)));
    }

    @Override
    public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
        return writeWith(Flux.from(body).concatMap(Flux::from));
    }

    @Override
    public Mono<Void> setComplete() {
        if (!downstreamResponded()) {
            return settlement.release(ReleaseReason.NO_DOWNSTREAM_RESPONSE).then(super.setComplete());
        }
        return settlement.record(storedResponse(new byte[0])).then(super.setComplete());
    }

    private Flux<DataBuffer> capture(Flux<? extends DataBuffer> body) {
        AtomicLong capturedBytes = new AtomicLong();
        AtomicBoolean exceeded = new AtomicBoolean();
        // 超限后谓词恒为真，后续分片逐个透传；未超限时只在完成时产出一份完整响应体。
        return body.cast(DataBuffer.class)
            .bufferUntil(buffer -> exceeded.get()
                || capturedBytes.addAndGet(buffer.readableByteCount()) > maxResponseBytes && markExceeded(exceeded))
            .concatMap(chunk -> exceeded.get() ? passThrough(chunk) : recordThenWrite(chunk))
            .switchIfEmpty(Flux.defer(() -> recordThenWrite(List.of())))
            .doOnDiscard(DataBuffer.class, DataBufferUtils::release);
    }

    private boolean markExceeded(AtomicBoolean exceeded) {
        exceeded.set(true);
        return true;
    }

    private Flux<DataBuffer> passThrough(List<DataBuffer> chunk) {
        return settlement.release(ReleaseReason.RESPONSE_TOO_LARGE).thenMany(Flux.fromIterable(chunk));
    }

    private Flux<DataBuffer> recordThenWrite(List<DataBuffer> chunk) {
        byte[] bytes = copyAndRelease(chunk);
        return settlement.record(storedResponse(bytes))
            .thenMany(Mono.fromSupplier(() -> bufferFactory().wrap(bytes)));
    }

    private StoredResponse storedResponse(byte[] body) {
        HttpStatusCode status = getStatusCode();
        int statusValue = status == null ? 200 : status.value();
        return new StoredResponse(statusValue, recordedHeaders(getHeaders(), statusValue), body);
    }

    private static Map<String, List<String>> recordedHeaders(HttpHeaders headers, int status) {
        Map<String, List<String>> recorded = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            String normalized = name.toLowerCase(Locale.ROOT);
            if (RECORDED_HEADERS.contains(normalized) || status == 201 && "location".equals(normalized)) {
                recorded.put(name, List.copyOf(values));
            }
        });
        return recorded;
    }

    private boolean downstreamResponded() {
        return exchange.getAttribute(ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR) instanceof HttpClientResponse;
    }

    private static byte[] copyAndRelease(List<DataBuffer> chunk) {
        List<DataBuffer> buffers = new ArrayList<>(chunk);
        try {
            int length = buffers.stream().mapToInt(DataBuffer::readableByteCount).sum();
            byte[] bytes = new byte[length];
            int offset = 0;
            for (DataBuffer buffer : buffers) {
                int readable = buffer.readableByteCount();
                buffer.read(bytes, offset, readable);
                offset += readable;
            }
            return bytes;
        } finally {
            buffers.forEach(DataBufferUtils::release);
        }
    }

    /** 每次占位最多结算一次：记录完成或释放。 */
    interface Settlement {
        Mono<Void> record(StoredResponse response);

        Mono<Void> release(ReleaseReason reason);
    }

    enum ReleaseReason {
        NO_DOWNSTREAM_RESPONSE,

        RESPONSE_TOO_LARGE,

        REQUEST_FAILED,

        CLIENT_CANCELLED
    }
}
