package com.paytech.gateway.test.probe;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.PooledDataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;

import com.paytech.gateway.web.GatewayGlobalFilterOrder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** 仅供浏览器边界自动测试观察真实 Gateway 链路的 Probe 配置。 */
@TestConfiguration(proxyBeanMethods = false)
public class GatewayBoundaryTestConfiguration {
    private static final String OWNER_ATTRIBUTE =
        "com.paytech.gateway.web.GatewayExchangeAttributes.replayableRequestBodyOwner";

    private static Mono<Integer> bodyLength(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody()).map(buffer -> {
            try {
                return buffer.readableByteCount();
            } finally {
                DataBufferUtils.release(buffer);
            }
        }).defaultIfEmpty(0);
    }

    @Bean
    GatewayBoundaryProbe gatewayBoundaryProbe() {
        return new GatewayBoundaryProbe();
    }

    @Bean
    WebFilter gatewayBoundaryOutcomeProbeWebFilter(GatewayBoundaryProbe probe) {
        return new OrderedWebFilter(Ordered.HIGHEST_PRECEDENCE + 15,
            (exchange, chain) -> chain.filter(exchange).doFinally(signal -> probe.record(exchange)));
    }

    @Bean
    org.springframework.cloud.gateway.filter.GlobalFilter inboundBodyProbeGlobalFilter(GatewayBoundaryProbe probe) {
        return new OrderedGlobalFilter(GatewayGlobalFilterOrder.REQUEST_BODY_LIMIT - 1, (exchange, chain) -> {
            ServerHttpRequest request = new ServerHttpRequestDecorator(exchange.getRequest()) {
                @Override
                public Flux<org.springframework.core.io.buffer.DataBuffer> getBody() {
                    return super.getBody().doOnNext(buffer -> {
                        probe.recordInboundBody();
                        probe.recordPooledBuffer(buffer);
                    });
                }
            };
            return chain.filter(exchange.mutate().request(request).build());
        });
    }

    @Bean
    org.springframework.cloud.gateway.filter.GlobalFilter parsedContentLengthFaultGlobalFilter() {
        return new OrderedGlobalFilter(GatewayGlobalFilterOrder.REQUEST_BODY_LIMIT - 2, (exchange, chain) -> {
            String requestedLength = exchange.getRequest().getHeaders().getFirst("X-Test-Parsed-Content-Length");
            if (requestedLength == null) {
                return chain.filter(exchange);
            }
            ServerHttpRequest request = new ServerHttpRequestDecorator(exchange.getRequest()) {
                @Override
                public HttpHeaders getHeaders() {
                    HttpHeaders headers = new HttpHeaders();
                    headers.putAll(super.getHeaders());
                    headers.setContentLength(Long.parseLong(requestedLength));
                    return headers;
                }
            };
            return chain.filter(exchange.mutate().request(request).build());
        });
    }

    @Bean
    org.springframework.cloud.gateway.filter.GlobalFilter
        replayabilityAndOwnerProbeGlobalFilter(GatewayBoundaryProbe probe) {
        return new OrderedGlobalFilter(GatewayGlobalFilterOrder.REQUEST_BODY_LIMIT + 1, (exchange, chain) -> {
            probe.recordOwner(exchange.getAttribute(OWNER_ATTRIBUTE));
            if (!"2".equals(exchange.getRequest().getHeaders().getFirst("X-Test-Replay-Subscriptions"))) {
                return chain.filter(exchange);
            }
            return bodyLength(exchange).flatMap(firstLength -> bodyLength(exchange).flatMap(secondLength -> {
                probe.recordReplaySubscriptions(firstLength, secondLength);
                return chain.filter(exchange);
            }));
        });
    }

    @Bean
    org.springframework.cloud.gateway.filter.GlobalFilter downstreamDataBufferLimitFaultGlobalFilter() {
        return new OrderedGlobalFilter(GatewayGlobalFilterOrder.UPSTREAM_REQUEST - 1, (exchange, chain) -> {
            if ("enabled".equals(exchange.getRequest().getHeaders().getFirst("X-Test-Downstream-Data-Buffer-Limit"))) {
                return reactor.core.publisher.Mono.error(new DataBufferLimitException("test downstream fault"));
            }
            if ("enabled".equals(exchange.getRequest().getHeaders().getFirst("X-Test-Downstream-Timeout"))) {
                return reactor.core.publisher.Mono
                    .error(new java.util.concurrent.TimeoutException("test downstream fault"));
            }
            if ("enabled".equals(exchange.getRequest().getHeaders().getFirst("X-Test-Synchronous-Downstream-Fault"))) {
                throw new IllegalStateException("test synchronous downstream fault");
            }
            return chain.filter(exchange);
        });
    }

    @Bean
    PostIdentityContinuationProbe postIdentityContinuationProbe() {
        return new PostIdentityContinuationProbe();
    }

    @Bean
    org.springframework.cloud.gateway.filter.GlobalFilter
        postIdentityContinuationProbeGlobalFilter(PostIdentityContinuationProbe probe) {
        return new OrderedGlobalFilter(GatewayGlobalFilterOrder.TRUSTED_DOWNSTREAM_IDENTITY + 1, (exchange, chain) -> {
            probe.record();
            return chain.filter(exchange);
        });
    }

    public static final class GatewayBoundaryProbe {
        private final ConcurrentLinkedQueue<Entry> entries = new ConcurrentLinkedQueue<>();
        private final CopyOnWriteArrayList<PooledDataBuffer> pooledBuffers = new CopyOnWriteArrayList<>();
        private final AtomicReference<Object> currentOwner = new AtomicReference<>();
        private final AtomicReference<OwnerLifecycleMetadata> terminalOwner = new AtomicReference<>();
        private final AtomicInteger replaySubscriptions = new AtomicInteger();
        private final AtomicReference<List<Integer>> replayLengths = new AtomicReference<>(List.of());
        private volatile CountDownLatch inboundBody = new CountDownLatch(1);
        private volatile CountDownLatch terminal = new CountDownLatch(1);

        public void reset() {
            entries.clear();
            pooledBuffers.clear();
            currentOwner.set(null);
            terminalOwner.set(null);
            replaySubscriptions.set(0);
            replayLengths.set(List.of());
            inboundBody = new CountDownLatch(1);
            terminal = new CountDownLatch(1);
        }

        public void record(ServerWebExchange exchange) {
            entries.add(new Entry(exchange.getAttribute("com.paytech.gateway.web.GatewayExchangeAttributes.routeCode"),
                exchange.getAttribute("com.paytech.gateway.web.GatewayExchangeAttributes.outcome"),
                copyHeaders(exchange.getRequest().getHeaders())));
            Object owner = currentOwner.get();
            if (owner != null) {
                terminalOwner.set(ownerMetadata(owner));
            }
            terminal.countDown();
        }

        private Map<String, List<String>> copyHeaders(org.springframework.http.HttpHeaders headers) {
            Map<String, List<String>> values = new LinkedHashMap<>();
            headers.forEach((name, entries) -> values.put(name, List.copyOf(entries)));
            return Map.copyOf(values);
        }

        public List<Entry> entries() {
            return List.copyOf(entries);
        }

        public void recordInboundBody() {
            inboundBody.countDown();
        }

        public void recordPooledBuffer(DataBuffer buffer) {
            if (buffer instanceof PooledDataBuffer pooledBuffer) {
                pooledBuffers.add(pooledBuffer);
            }
        }

        public int observedPooledBufferCount() {
            return pooledBuffers.size();
        }

        public boolean allObservedPooledBuffersReleased() {
            return !pooledBuffers.isEmpty() && pooledBuffers.stream().noneMatch(PooledDataBuffer::isAllocated);
        }

        public void recordOwner(Object owner) {
            if (owner != null) {
                currentOwner.set(owner);
            }
        }

        public OwnerLifecycleMetadata currentOwnerMetadata() {
            Object owner = currentOwner.get();
            return owner == null ? OwnerLifecycleMetadata.absent() : ownerMetadata(owner);
        }

        public OwnerLifecycleMetadata terminalOwnerMetadata() {
            return terminalOwner.get() == null ? OwnerLifecycleMetadata.absent() : terminalOwner.get();
        }

        public void recordReplaySubscriptions(int firstLength, int secondLength) {
            replaySubscriptions.addAndGet(2);
            replayLengths.set(List.of(firstLength, secondLength));
        }

        public int replaySubscriptionCount() {
            return replaySubscriptions.get();
        }

        public List<Integer> replayLengths() {
            return replayLengths.get();
        }

        public void awaitInboundBody(Duration timeout) {
            try {
                Assertions.assertTrue(inboundBody.await(timeout.toMillis(), TimeUnit.MILLISECONDS),
                    "Gateway 未在取消前读取入站请求体");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待入站请求体时被中断", exception);
            }
        }

        public void awaitTerminal(Duration timeout) {
            try {
                Assertions.assertTrue(terminal.await(timeout.toMillis(), TimeUnit.MILLISECONDS),
                    "Gateway 未在期限内完成或取消请求链");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待 Gateway 请求终止时被中断", exception);
            }
        }

        private OwnerLifecycleMetadata ownerMetadata(Object owner) {
            try {
                int length = (Integer)invokeMetadata(owner, "length");
                Object terminalSignal = invokeMetadata(owner, "terminalSignal");
                boolean zeroed = (Boolean)invokeMetadata(owner, "isZeroed");
                int clearCount = (Integer)invokeMetadata(owner, "clearCount");
                return new OwnerLifecycleMetadata(true, length,
                    terminalSignal == null ? null : terminalSignal.toString(), zeroed, clearCount);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("读取可重放请求体 Owner 生命周期元数据失败", exception);
            }
        }

        private Object invokeMetadata(Object owner, String methodName) throws ReflectiveOperationException {
            java.lang.reflect.Method method = owner.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method.invoke(owner);
        }
    }

    public record Entry(Object routeCode, Object outcome, Map<String, List<String>> headers) {
    }

    /** 请求体 Owner 的非内容生命周期快照。 */
    public record OwnerLifecycleMetadata(boolean present, int length, String terminalSignal, boolean zeroed,
        int clearCount) {
        private static OwnerLifecycleMetadata absent() {
            return new OwnerLifecycleMetadata(false, 0, null, false, 0);
        }
    }

    /** 只记录身份 Header 重建后的链路续接次数，避免 BFF 路由标记掩盖重复续接。 */
    public static final class PostIdentityContinuationProbe {
        private final AtomicInteger continuations = new AtomicInteger();

        public void reset() {
            continuations.set(0);
        }

        public int count() {
            return continuations.get();
        }

        private void record() {
            continuations.incrementAndGet();
        }
    }

    private record OrderedWebFilter(int order, WebFilter delegate) implements WebFilter, Ordered {
        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public reactor.core.publisher.Mono<Void> filter(ServerWebExchange exchange,
            org.springframework.web.server.WebFilterChain chain) {
            return delegate.filter(exchange, chain);
        }
    }

    private record OrderedGlobalFilter(int order, org.springframework.cloud.gateway.filter.GlobalFilter delegate)
        implements
            org.springframework.cloud.gateway.filter.GlobalFilter,
            Ordered {
        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public reactor.core.publisher.Mono<Void> filter(ServerWebExchange exchange,
            org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
            return delegate.filter(exchange, chain);
        }
    }
}
